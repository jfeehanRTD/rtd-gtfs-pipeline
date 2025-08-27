package com.rtd.pipeline.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.transit.realtime.GtfsRealtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms RailComm SCADA sensor data to GTFS-RT format.
 * Handles JSON payloads from RTD's light rail track infrastructure sensors.
 * 
 * IMPORTANT: This is a FAILOVER data source for light rail.
 * Primary source should be LRGPS (cellular GPS from vehicles).
 * RailComm provides track-based sensor data when vehicle GPS is unavailable.
 */
public class RailCommToGtfsTransformer {
    
    private static final Logger LOG = LoggerFactory.getLogger(RailCommToGtfsTransformer.class);
    
    private final ObjectMapper jsonMapper;
    private static final ZoneId RTD_TIMEZONE = ZoneId.of("America/Denver");
    private static final DateTimeFormatter RAIL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    
    // RTD Light Rail Route Mapping
    private static final String[] LIGHT_RAIL_ROUTES = {
        "A", "B", "D", "E", "F", "G", "H", "L", "N", "R", "W"
    };
    
    public RailCommToGtfsTransformer() {
        this.jsonMapper = new ObjectMapper();
    }
    
    /**
     * Transform RailComm JSON to GTFS-RT VehiclePosition messages
     */
    public List<GtfsRealtime.VehiclePosition> transformToVehiclePositions(String railCommPayload) {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();
        
        try {
            JsonNode rootNode = jsonMapper.readTree(railCommPayload);
            
            // Handle different RailComm payload structures
            JsonNode trainsArray = findTrainsArray(rootNode);
            if (trainsArray == null || !trainsArray.isArray()) {
                LOG.debug("No trains array found in RailComm payload");
                return positions;
            }
            
            for (JsonNode trainNode : trainsArray) {
                GtfsRealtime.VehiclePosition position = transformTrainToVehiclePosition(trainNode);
                if (position != null) {
                    positions.add(position);
                }
            }
            
            LOG.debug("Transformed {} RailComm trains to GTFS-RT positions", positions.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming RailComm to VehiclePositions: {}", e.getMessage(), e);
        }
        
        return positions;
    }
    
    /**
     * Transform RailComm JSON to GTFS-RT TripUpdate messages
     */
    public List<GtfsRealtime.TripUpdate> transformToTripUpdates(String railCommPayload) {
        List<GtfsRealtime.TripUpdate> tripUpdates = new ArrayList<>();
        
        try {
            JsonNode rootNode = jsonMapper.readTree(railCommPayload);
            JsonNode trainsArray = findTrainsArray(rootNode);
            
            if (trainsArray == null || !trainsArray.isArray()) {
                return tripUpdates;
            }
            
            for (JsonNode trainNode : trainsArray) {
                GtfsRealtime.TripUpdate tripUpdate = transformTrainToTripUpdate(trainNode);
                if (tripUpdate != null) {
                    tripUpdates.add(tripUpdate);
                }
            }
            
            LOG.debug("Transformed {} RailComm trains to GTFS-RT trip updates", tripUpdates.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming RailComm to TripUpdates: {}", e.getMessage(), e);
        }
        
        return tripUpdates;
    }
    
    private JsonNode findTrainsArray(JsonNode root) {
        // Try different possible structures for RailComm data
        if (root.has("trains")) {
            return root.path("trains");
        }
        if (root.has("vehicles")) {
            return root.path("vehicles");
        }
        if (root.has("railComm") && root.path("railComm").has("trains")) {
            return root.path("railComm").path("trains");
        }
        if (root.has("data") && root.path("data").has("trains")) {
            return root.path("data").path("trains");
        }
        if (root.isArray()) {
            return root;
        }
        
        // Check if root contains train-like objects
        if (root.has("trainId") || root.has("vehicleId") || root.has("routeId")) {
            // Single train object, wrap in array
            ObjectMapper mapper = new ObjectMapper();
            return mapper.createArrayNode().add(root);
        }
        
        return null;
    }
    
    private GtfsRealtime.VehiclePosition transformTrainToVehiclePosition(JsonNode trainNode) {
        try {
            // Extract train identification
            String trainId = extractText(trainNode, "trainId", "vehicleId", "id");
            String routeId = extractText(trainNode, "routeId", "route", "lineId");
            String tripId = extractText(trainNode, "tripId", "trip", "serviceId");
            
            if (trainId == null || trainId.isEmpty()) {
                LOG.debug("Skipping train with no ID");
                return null;
            }
            
            // Extract position data
            JsonNode location = trainNode.path("location");
            if (location.isMissingNode()) {
                // Try alternative location field names
                location = findLocationNode(trainNode);
            }
            
            if (location == null || location.isMissingNode()) {
                LOG.debug("No location data for train {}", trainId);
                return null;
            }
            
            double latitude = extractDouble(location, "latitude", "lat", "y");
            double longitude = extractDouble(location, "longitude", "lon", "lng", "x");
            
            if (latitude == 0.0 || longitude == 0.0) {
                LOG.debug("Invalid coordinates for train {}: lat={}, lon={}", trainId, latitude, longitude);
                return null;
            }
            
            // Build VehiclePosition
            GtfsRealtime.VehiclePosition.Builder builder = GtfsRealtime.VehiclePosition.newBuilder();
            
            // Vehicle descriptor
            GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = GtfsRealtime.VehicleDescriptor.newBuilder();
            vehicleBuilder.setId(trainId);
            
            // Add vehicle label if available
            String vehicleLabel = extractText(trainNode, "label", "trainNumber", "vehicleLabel");
            if (vehicleLabel != null && !vehicleLabel.isEmpty()) {
                vehicleBuilder.setLabel(vehicleLabel);
            }
            
            builder.setVehicle(vehicleBuilder.build());
            
            // Trip descriptor
            if (tripId != null && !tripId.isEmpty()) {
                GtfsRealtime.TripDescriptor.Builder tripBuilder = GtfsRealtime.TripDescriptor.newBuilder();
                tripBuilder.setTripId(tripId);
                
                if (routeId != null && !routeId.isEmpty()) {
                    tripBuilder.setRouteId(normalizeRouteId(routeId));
                }
                
                // Extract direction if available
                String direction = extractText(trainNode, "direction", "directionId");
                if (direction != null) {
                    try {
                        int directionId = Integer.parseInt(direction);
                        tripBuilder.setDirectionId(directionId);
                    } catch (NumberFormatException e) {
                        // Direction might be text like "INBOUND"/"OUTBOUND"
                        if ("INBOUND".equalsIgnoreCase(direction) || "0".equals(direction)) {
                            tripBuilder.setDirectionId(0);
                        } else if ("OUTBOUND".equalsIgnoreCase(direction) || "1".equals(direction)) {
                            tripBuilder.setDirectionId(1);
                        }
                    }
                }
                
                builder.setTrip(tripBuilder.build());
            }
            
            // Position
            GtfsRealtime.Position.Builder positionBuilder = GtfsRealtime.Position.newBuilder();
            positionBuilder.setLatitude((float) latitude);
            positionBuilder.setLongitude((float) longitude);
            
            // Extract bearing/heading if available
            double bearing = extractDouble(trainNode, "bearing", "heading", "direction");
            if (bearing > 0) {
                positionBuilder.setBearing((float) bearing);
            }
            
            // Extract speed if available
            double speed = extractDouble(trainNode, "speed", "velocity");
            if (speed > 0) {
                positionBuilder.setSpeed((float) speed);
            }
            
            builder.setPosition(positionBuilder.build());
            
            // Current status
            String status = extractText(trainNode, "status", "currentStatus", "vehicleStatus");
            if (status != null) {
                builder.setCurrentStatus(mapCurrentStatus(status));
            }
            
            // Timestamp
            String timestamp = extractText(trainNode, "timestamp", "lastUpdate", "recordedAt");
            long timestampMs = parseTimestamp(timestamp);
            if (timestampMs > 0) {
                builder.setTimestamp(timestampMs / 1000);
            }
            
            // Current stop if available
            String currentStopId = extractText(trainNode, "currentStopId", "stopId", "stationId");
            if (currentStopId != null && !currentStopId.isEmpty()) {
                builder.setStopId(currentStopId);
            }
            
            return builder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming train to vehicle position: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private GtfsRealtime.TripUpdate transformTrainToTripUpdate(JsonNode trainNode) {
        try {
            String trainId = extractText(trainNode, "trainId", "vehicleId", "id");
            String routeId = extractText(trainNode, "routeId", "route", "lineId");
            String tripId = extractText(trainNode, "tripId", "trip", "serviceId");
            
            if (trainId == null || tripId == null) {
                return null; // Need both for trip update
            }
            
            // Look for delay information
            double delaySeconds = extractDouble(trainNode, "delay", "delaySeconds", "scheduleDeviation");
            String scheduleAdherence = extractText(trainNode, "scheduleAdherence", "adherence", "onTime");
            
            // Skip if no delay information
            if (delaySeconds == 0 && (scheduleAdherence == null || "ON_TIME".equalsIgnoreCase(scheduleAdherence))) {
                return null;
            }
            
            // Build TripUpdate
            GtfsRealtime.TripUpdate.Builder builder = GtfsRealtime.TripUpdate.newBuilder();
            
            // Trip descriptor
            GtfsRealtime.TripDescriptor.Builder tripBuilder = GtfsRealtime.TripDescriptor.newBuilder();
            tripBuilder.setTripId(tripId);
            if (routeId != null && !routeId.isEmpty()) {
                tripBuilder.setRouteId(normalizeRouteId(routeId));
            }
            builder.setTrip(tripBuilder.build());
            
            // Vehicle descriptor
            GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = GtfsRealtime.VehicleDescriptor.newBuilder();
            vehicleBuilder.setId(trainId);
            builder.setVehicle(vehicleBuilder.build());
            
            // Stop time updates
            JsonNode stopUpdates = trainNode.path("stopUpdates");
            if (stopUpdates.isArray() && stopUpdates.size() > 0) {
                // Use detailed stop updates if available
                for (JsonNode stopUpdate : stopUpdates) {
                    GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate = transformStopUpdate(stopUpdate);
                    if (stopTimeUpdate != null) {
                        builder.addStopTimeUpdate(stopTimeUpdate);
                    }
                }
            } else if (delaySeconds != 0) {
                // Create generic delay update
                GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeBuilder = 
                    GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
                
                String currentStopId = extractText(trainNode, "currentStopId", "nextStopId", "stopId");
                if (currentStopId != null && !currentStopId.isEmpty()) {
                    stopTimeBuilder.setStopId(currentStopId);
                }
                
                // Add delay to both arrival and departure
                if (delaySeconds != 0) {
                    GtfsRealtime.TripUpdate.StopTimeEvent.Builder arrivalBuilder = 
                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
                    arrivalBuilder.setDelay((int) delaySeconds);
                    stopTimeBuilder.setArrival(arrivalBuilder.build());
                    
                    GtfsRealtime.TripUpdate.StopTimeEvent.Builder departureBuilder = 
                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
                    departureBuilder.setDelay((int) delaySeconds);
                    stopTimeBuilder.setDeparture(departureBuilder.build());
                }
                
                builder.addStopTimeUpdate(stopTimeBuilder.build());
            }
            
            // Timestamp
            String timestamp = extractText(trainNode, "timestamp", "lastUpdate", "recordedAt");
            long timestampMs = parseTimestamp(timestamp);
            if (timestampMs > 0) {
                builder.setTimestamp(timestampMs / 1000);
            }
            
            return builder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming train to trip update: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private GtfsRealtime.TripUpdate.StopTimeUpdate transformStopUpdate(JsonNode stopUpdate) {
        try {
            String stopId = extractText(stopUpdate, "stopId", "stationId", "id");
            if (stopId == null || stopId.isEmpty()) {
                return null;
            }
            
            GtfsRealtime.TripUpdate.StopTimeUpdate.Builder builder = 
                GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
            builder.setStopId(stopId);
            
            // Arrival time/delay
            JsonNode arrival = stopUpdate.path("arrival");
            if (!arrival.isMissingNode()) {
                GtfsRealtime.TripUpdate.StopTimeEvent arrivalEvent = transformStopTimeEvent(arrival);
                if (arrivalEvent != null) {
                    builder.setArrival(arrivalEvent);
                }
            }
            
            // Departure time/delay
            JsonNode departure = stopUpdate.path("departure");
            if (!departure.isMissingNode()) {
                GtfsRealtime.TripUpdate.StopTimeEvent departureEvent = transformStopTimeEvent(departure);
                if (departureEvent != null) {
                    builder.setDeparture(departureEvent);
                }
            }
            
            // Schedule relationship
            String scheduleRelationship = extractText(stopUpdate, "scheduleRelationship", "relationship");
            if (scheduleRelationship != null) {
                builder.setScheduleRelationship(mapScheduleRelationship(scheduleRelationship));
            }
            
            return builder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming stop update: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private GtfsRealtime.TripUpdate.StopTimeEvent transformStopTimeEvent(JsonNode timeEvent) {
        GtfsRealtime.TripUpdate.StopTimeEvent.Builder builder = 
            GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
        
        // Delay in seconds
        double delay = extractDouble(timeEvent, "delay", "delaySeconds");
        if (delay != 0) {
            builder.setDelay((int) delay);
        }
        
        // Scheduled/estimated time
        String time = extractText(timeEvent, "time", "estimatedTime", "scheduledTime");
        if (time != null) {
            long timeSeconds = parseTimestamp(time) / 1000;
            if (timeSeconds > 0) {
                builder.setTime(timeSeconds);
            }
        }
        
        // Uncertainty
        double uncertainty = extractDouble(timeEvent, "uncertainty");
        if (uncertainty > 0) {
            builder.setUncertainty((int) uncertainty);
        }
        
        return builder.build();
    }
    
    private JsonNode findLocationNode(JsonNode trainNode) {
        // Try different location field patterns
        String[] locationFields = {"position", "coords", "coordinate", "gps", "latlon"};
        for (String field : locationFields) {
            JsonNode node = trainNode.path(field);
            if (!node.isMissingNode()) {
                return node;
            }
        }
        
        // Check if lat/lon are directly on the train node
        if (trainNode.has("latitude") || trainNode.has("lat")) {
            return trainNode;
        }
        
        return null;
    }
    
    private String extractText(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.path(field);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                String value = fieldNode.asText().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
    
    private double extractDouble(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode fieldNode = node.path(field);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                return fieldNode.asDouble(0.0);
            }
        }
        return 0.0;
    }
    
    private String normalizeRouteId(String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            return routeId;
        }
        
        // Normalize to standard RTD light rail route format
        String normalized = routeId.toUpperCase().trim();
        
        // Handle common variations
        if (normalized.startsWith("LINE_") || normalized.startsWith("ROUTE_")) {
            normalized = normalized.substring(normalized.indexOf("_") + 1);
        }
        
        // Ensure it's a valid light rail route
        for (String validRoute : LIGHT_RAIL_ROUTES) {
            if (normalized.equals(validRoute) || normalized.contains(validRoute)) {
                return validRoute;
            }
        }
        
        return normalized;
    }
    
    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return System.currentTimeMillis();
        }
        
        try {
            // Try different timestamp formats
            if (timestamp.matches("\\d{13}")) {
                // Unix timestamp in milliseconds
                return Long.parseLong(timestamp);
            } else if (timestamp.matches("\\d{10}")) {
                // Unix timestamp in seconds
                return Long.parseLong(timestamp) * 1000;
            } else {
                // Try parsing as date string
                LocalDateTime dateTime = LocalDateTime.parse(timestamp, RAIL_DATE_FORMATTER);
                return dateTime.atZone(RTD_TIMEZONE).toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            try {
                // Try ISO instant format
                return Instant.parse(timestamp).toEpochMilli();
            } catch (DateTimeParseException e2) {
                LOG.debug("Could not parse timestamp: {}", timestamp);
                return System.currentTimeMillis();
            }
        }
    }
    
    private GtfsRealtime.VehiclePosition.VehicleStopStatus mapCurrentStatus(String status) {
        if (status == null) {
            return GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO;
        }
        
        switch (status.toUpperCase()) {
            case "STOPPED_AT":
            case "AT_STATION":
            case "DWELLING":
                return GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT;
            case "INCOMING_AT":
            case "APPROACHING":
                return GtfsRealtime.VehiclePosition.VehicleStopStatus.INCOMING_AT;
            case "IN_TRANSIT_TO":
            case "MOVING":
            case "RUNNING":
                return GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO;
            default:
                return GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO;
        }
    }
    
    private GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship mapScheduleRelationship(String relationship) {
        if (relationship == null) {
            return GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;
        }
        
        switch (relationship.toUpperCase()) {
            case "SCHEDULED":
                return GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;
            case "SKIPPED":
                return GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SKIPPED;
            case "NO_DATA":
                return GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.NO_DATA;
            default:
                return GtfsRealtime.TripUpdate.StopTimeUpdate.ScheduleRelationship.SCHEDULED;
        }
    }
}