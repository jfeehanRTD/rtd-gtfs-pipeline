package com.rtd.pipeline.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
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
 * Transforms SIRI (Service Interface for Real-time Information) data
 * to GTFS-RT (General Transit Feed Specification - Realtime) format.
 * 
 * Handles both XML and JSON SIRI formats commonly used by RTD bus systems.
 */
public class SiriToGtfsTransformer {
    
    private static final Logger LOG = LoggerFactory.getLogger(SiriToGtfsTransformer.class);
    
    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;
    private final DateTimeFormatter siriDateFormatter;
    
    // RTD timezone for proper timestamp conversion
    private static final ZoneId RTD_TIMEZONE = ZoneId.of("America/Denver");
    
    public SiriToGtfsTransformer() {
        this.jsonMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.siriDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    }
    
    /**
     * Transform SIRI payload to GTFS-RT VehiclePosition messages
     */
    public List<GtfsRealtime.VehiclePosition> transformToVehiclePositions(String siriPayload) {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();
        
        try {
            JsonNode rootNode = parsePayload(siriPayload);
            if (rootNode == null) {
                LOG.warn("Failed to parse SIRI payload");
                return positions;
            }
            
            // Navigate through SIRI structure to find VehicleActivity nodes
            JsonNode serviceDelivery = findServiceDelivery(rootNode);
            if (serviceDelivery == null) {
                LOG.debug("No ServiceDelivery found in SIRI payload");
                return positions;
            }
            
            JsonNode vehicleMonitoring = serviceDelivery.path("VehicleMonitoringDelivery");
            if (vehicleMonitoring.isArray() && vehicleMonitoring.size() > 0) {
                JsonNode delivery = vehicleMonitoring.get(0);
                JsonNode vehicleActivities = delivery.path("VehicleActivity");
                
                if (vehicleActivities.isArray()) {
                    for (JsonNode activity : vehicleActivities) {
                        GtfsRealtime.VehiclePosition position = transformVehicleActivity(activity);
                        if (position != null) {
                            positions.add(position);
                        }
                    }
                }
            }
            
            LOG.debug("Transformed {} SIRI vehicle activities to GTFS-RT positions", positions.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming SIRI to VehiclePositions: {}", e.getMessage(), e);
        }
        
        return positions;
    }
    
    /**
     * Transform SIRI payload to GTFS-RT TripUpdate messages
     */
    public List<GtfsRealtime.TripUpdate> transformToTripUpdates(String siriPayload) {
        List<GtfsRealtime.TripUpdate> tripUpdates = new ArrayList<>();
        
        try {
            JsonNode rootNode = parsePayload(siriPayload);
            if (rootNode == null) {
                return tripUpdates;
            }
            
            JsonNode serviceDelivery = findServiceDelivery(rootNode);
            if (serviceDelivery == null) {
                return tripUpdates;
            }
            
            // Look for EstimatedTimetableDelivery or VehicleMonitoringDelivery with delay info
            JsonNode vehicleMonitoring = serviceDelivery.path("VehicleMonitoringDelivery");
            if (vehicleMonitoring.isArray() && vehicleMonitoring.size() > 0) {
                JsonNode delivery = vehicleMonitoring.get(0);
                JsonNode vehicleActivities = delivery.path("VehicleActivity");
                
                if (vehicleActivities.isArray()) {
                    for (JsonNode activity : vehicleActivities) {
                        GtfsRealtime.TripUpdate tripUpdate = transformToTripUpdate(activity);
                        if (tripUpdate != null) {
                            tripUpdates.add(tripUpdate);
                        }
                    }
                }
            }
            
            LOG.debug("Transformed {} SIRI activities to GTFS-RT trip updates", tripUpdates.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming SIRI to TripUpdates: {}", e.getMessage(), e);
        }
        
        return tripUpdates;
    }
    
    private JsonNode parsePayload(String payload) {
        try {
            // Try JSON first
            if (payload.trim().startsWith("{") || payload.trim().startsWith("[")) {
                return jsonMapper.readTree(payload);
            } else {
                // Try XML
                return xmlMapper.readTree(payload);
            }
        } catch (Exception e) {
            LOG.error("Failed to parse SIRI payload: {}", e.getMessage());
            return null;
        }
    }
    
    private JsonNode findServiceDelivery(JsonNode root) {
        // Common SIRI structures
        if (root.has("Siri")) {
            return root.path("Siri").path("ServiceDelivery");
        }
        if (root.has("ServiceDelivery")) {
            return root.path("ServiceDelivery");
        }
        if (root.path("siri").has("serviceDelivery")) {
            return root.path("siri").path("serviceDelivery");
        }
        return null;
    }
    
    private GtfsRealtime.VehiclePosition transformVehicleActivity(JsonNode activity) {
        try {
            JsonNode monitoredVehicleJourney = activity.path("MonitoredVehicleJourney");
            if (monitoredVehicleJourney.isMissingNode()) {
                return null;
            }
            
            // Extract vehicle information
            String vehicleRef = extractText(monitoredVehicleJourney.path("VehicleRef"));
            String lineRef = extractText(monitoredVehicleJourney.path("LineRef"));
            String directionRef = extractText(monitoredVehicleJourney.path("DirectionRef"));
            String tripRef = extractText(monitoredVehicleJourney.path("FramedVehicleJourneyRef").path("DatedVehicleJourneyRef"));
            
            // Extract location information
            JsonNode vehicleLocation = monitoredVehicleJourney.path("VehicleLocation");
            if (vehicleLocation.isMissingNode()) {
                return null;
            }
            
            double latitude = vehicleLocation.path("Latitude").asDouble();
            double longitude = vehicleLocation.path("Longitude").asDouble();
            
            if (latitude == 0.0 || longitude == 0.0) {
                LOG.debug("Invalid coordinates for vehicle {}: lat={}, lon={}", vehicleRef, latitude, longitude);
                return null;
            }
            
            // Extract additional data
            float bearing = (float) monitoredVehicleJourney.path("Bearing").asDouble(0.0);
            float speed = (float) monitoredVehicleJourney.path("Speed").asDouble(0.0);
            
            // Extract timestamp
            String recordedAtTime = extractText(activity.path("RecordedAtTime"));
            long timestampMs = parseTimestamp(recordedAtTime);
            
            // Build GTFS-RT VehiclePosition
            GtfsRealtime.VehiclePosition.Builder builder = GtfsRealtime.VehiclePosition.newBuilder();
            
            // Vehicle descriptor
            GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = GtfsRealtime.VehicleDescriptor.newBuilder();
            if (vehicleRef != null && !vehicleRef.isEmpty()) {
                vehicleBuilder.setId(vehicleRef);
            }
            builder.setVehicle(vehicleBuilder.build());
            
            // Trip descriptor
            if (tripRef != null && !tripRef.isEmpty() && lineRef != null && !lineRef.isEmpty()) {
                GtfsRealtime.TripDescriptor.Builder tripBuilder = GtfsRealtime.TripDescriptor.newBuilder();
                tripBuilder.setTripId(tripRef);
                tripBuilder.setRouteId(lineRef);
                if (directionRef != null && !directionRef.isEmpty()) {
                    try {
                        tripBuilder.setDirectionId(Integer.parseInt(directionRef));
                    } catch (NumberFormatException e) {
                        // Direction not numeric, skip
                    }
                }
                builder.setTrip(tripBuilder.build());
            }
            
            // Position
            GtfsRealtime.Position.Builder positionBuilder = GtfsRealtime.Position.newBuilder();
            positionBuilder.setLatitude((float) latitude);
            positionBuilder.setLongitude((float) longitude);
            if (bearing > 0) {
                positionBuilder.setBearing(bearing);
            }
            if (speed > 0) {
                positionBuilder.setSpeed(speed);
            }
            builder.setPosition(positionBuilder.build());
            
            // Timestamp
            if (timestampMs > 0) {
                builder.setTimestamp(timestampMs / 1000); // GTFS-RT uses seconds
            }
            
            // Current status (if available)
            String occupancyStatus = extractText(monitoredVehicleJourney.path("OccupancyStatus"));
            if (occupancyStatus != null) {
                builder.setOccupancyStatus(mapOccupancyStatus(occupancyStatus));
            }
            
            return builder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming vehicle activity: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private GtfsRealtime.TripUpdate transformToTripUpdate(JsonNode activity) {
        try {
            JsonNode monitoredVehicleJourney = activity.path("MonitoredVehicleJourney");
            if (monitoredVehicleJourney.isMissingNode()) {
                return null;
            }
            
            String vehicleRef = extractText(monitoredVehicleJourney.path("VehicleRef"));
            String lineRef = extractText(monitoredVehicleJourney.path("LineRef"));
            String tripRef = extractText(monitoredVehicleJourney.path("FramedVehicleJourneyRef").path("DatedVehicleJourneyRef"));
            
            // Extract delay information
            JsonNode delay = monitoredVehicleJourney.path("Delay");
            if (delay.isMissingNode()) {
                return null; // No delay information available
            }
            
            int delaySeconds = 0;
            if (delay.isTextual()) {
                // Parse ISO 8601 duration (PT30S = 30 seconds)
                delaySeconds = parseIsoDuration(delay.asText());
            } else if (delay.isNumber()) {
                delaySeconds = delay.asInt();
            }
            
            if (delaySeconds == 0) {
                return null; // No meaningful delay
            }
            
            // Build TripUpdate
            GtfsRealtime.TripUpdate.Builder builder = GtfsRealtime.TripUpdate.newBuilder();
            
            // Trip descriptor
            if (tripRef != null && !tripRef.isEmpty() && lineRef != null && !lineRef.isEmpty()) {
                GtfsRealtime.TripDescriptor.Builder tripBuilder = GtfsRealtime.TripDescriptor.newBuilder();
                tripBuilder.setTripId(tripRef);
                tripBuilder.setRouteId(lineRef);
                builder.setTrip(tripBuilder.build());
            } else {
                return null; // Cannot create trip update without trip info
            }
            
            // Vehicle descriptor
            if (vehicleRef != null && !vehicleRef.isEmpty()) {
                GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = GtfsRealtime.VehicleDescriptor.newBuilder();
                vehicleBuilder.setId(vehicleRef);
                builder.setVehicle(vehicleBuilder.build());
            }
            
            // Add stop time update with delay
            GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopTimeBuilder = 
                GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder();
            
            // Add delay to arrival/departure
            GtfsRealtime.TripUpdate.StopTimeEvent.Builder arrivalBuilder = 
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
            arrivalBuilder.setDelay(delaySeconds);
            stopTimeBuilder.setArrival(arrivalBuilder.build());
            
            GtfsRealtime.TripUpdate.StopTimeEvent.Builder departureBuilder = 
                GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder();
            departureBuilder.setDelay(delaySeconds);
            stopTimeBuilder.setDeparture(departureBuilder.build());
            
            builder.addStopTimeUpdate(stopTimeBuilder.build());
            
            // Timestamp
            String recordedAtTime = extractText(activity.path("RecordedAtTime"));
            long timestampMs = parseTimestamp(recordedAtTime);
            if (timestampMs > 0) {
                builder.setTimestamp(timestampMs / 1000);
            }
            
            return builder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming to trip update: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private String extractText(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asText().trim();
    }
    
    private long parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return System.currentTimeMillis();
        }
        
        try {
            // Parse SIRI timestamp format
            LocalDateTime dateTime = LocalDateTime.parse(timestamp, siriDateFormatter);
            return dateTime.atZone(RTD_TIMEZONE).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            try {
                // Try ISO instant format
                return Instant.parse(timestamp).toEpochMilli();
            } catch (DateTimeParseException e2) {
                LOG.debug("Could not parse timestamp: {}", timestamp);
                return System.currentTimeMillis();
            }
        }
    }
    
    private int parseIsoDuration(String duration) {
        if (duration == null || !duration.startsWith("PT")) {
            return 0;
        }
        
        try {
            // Simple parsing for PT30S format
            String timePart = duration.substring(2); // Remove "PT"
            if (timePart.endsWith("S")) {
                return Integer.parseInt(timePart.substring(0, timePart.length() - 1));
            } else if (timePart.endsWith("M")) {
                return Integer.parseInt(timePart.substring(0, timePart.length() - 1)) * 60;
            } else if (timePart.endsWith("H")) {
                return Integer.parseInt(timePart.substring(0, timePart.length() - 1)) * 3600;
            }
        } catch (NumberFormatException e) {
            LOG.debug("Could not parse ISO duration: {}", duration);
        }
        
        return 0;
    }
    
    private GtfsRealtime.VehiclePosition.OccupancyStatus mapOccupancyStatus(String siriStatus) {
        if (siriStatus == null) {
            return GtfsRealtime.VehiclePosition.OccupancyStatus.EMPTY;
        }
        
        switch (siriStatus.toUpperCase()) {
            case "EMPTY":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.EMPTY;
            case "MANY_SEATS_AVAILABLE":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE;
            case "FEW_SEATS_AVAILABLE":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE;
            case "STANDING_ROOM_ONLY":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.STANDING_ROOM_ONLY;
            case "CRUSHED_STANDING_ROOM_ONLY":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY;
            case "FULL":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.FULL;
            case "NOT_ACCEPTING_PASSENGERS":
                return GtfsRealtime.VehiclePosition.OccupancyStatus.NOT_ACCEPTING_PASSENGERS;
            default:
                return GtfsRealtime.VehiclePosition.OccupancyStatus.EMPTY;
        }
    }
}