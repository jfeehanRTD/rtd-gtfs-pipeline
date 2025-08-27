package com.rtd.pipeline.transform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.transit.realtime.GtfsRealtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms LRGPS (Light Rail GPS) data to GTFS-RT format.
 * Handles RTD's light rail vehicle position data.
 */
public class LrgpsToGtfsTransformer {
    
    private static final Logger LOG = LoggerFactory.getLogger(LrgpsToGtfsTransformer.class);
    
    private final ObjectMapper jsonMapper;
    private final XmlMapper xmlMapper;
    private final DateTimeFormatter dateFormatter;
    
    // RTD timezone for proper timestamp conversion
    private static final ZoneId RTD_TIMEZONE = ZoneId.of("America/Denver");
    
    public LrgpsToGtfsTransformer() {
        this.jsonMapper = new ObjectMapper();
        this.xmlMapper = new XmlMapper();
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    }
    
    /**
     * Transform LRGPS payload to GTFS-RT VehiclePosition messages
     */
    public List<GtfsRealtime.VehiclePosition> transformToVehiclePositions(String lrgpsPayload) {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();
        
        try {
            JsonNode rootNode = parsePayload(lrgpsPayload);
            if (rootNode == null) {
                LOG.warn("Failed to parse LRGPS payload");
                return positions;
            }
            
            // Handle different possible LRGPS structures
            JsonNode vehicles = findVehicleData(rootNode);
            
            if (vehicles != null && vehicles.isArray()) {
                for (JsonNode vehicle : vehicles) {
                    GtfsRealtime.VehiclePosition position = transformLRGPSVehicle(vehicle);
                    if (position != null) {
                        positions.add(position);
                    }
                }
            } else if (vehicles != null && !vehicles.isArray()) {
                // Single vehicle
                GtfsRealtime.VehiclePosition position = transformLRGPSVehicle(vehicles);
                if (position != null) {
                    positions.add(position);
                }
            }
            
            LOG.debug("Transformed {} LRGPS vehicles to GTFS-RT positions", positions.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming LRGPS to VehiclePositions: {}", e.getMessage(), e);
        }
        
        return positions;
    }
    
    /**
     * Transform LRGPS payload to GTFS-RT TripUpdate messages
     */
    public List<GtfsRealtime.TripUpdate> transformToTripUpdates(String lrgpsPayload) {
        List<GtfsRealtime.TripUpdate> tripUpdates = new ArrayList<>();
        
        try {
            JsonNode rootNode = parsePayload(lrgpsPayload);
            if (rootNode == null) {
                return tripUpdates;
            }
            
            JsonNode vehicles = findVehicleData(rootNode);
            
            if (vehicles != null && vehicles.isArray()) {
                for (JsonNode vehicle : vehicles) {
                    GtfsRealtime.TripUpdate tripUpdate = transformLRGPSTripUpdate(vehicle);
                    if (tripUpdate != null) {
                        tripUpdates.add(tripUpdate);
                    }
                }
            } else if (vehicles != null && !vehicles.isArray()) {
                GtfsRealtime.TripUpdate tripUpdate = transformLRGPSTripUpdate(vehicles);
                if (tripUpdate != null) {
                    tripUpdates.add(tripUpdate);
                }
            }
            
            LOG.debug("Transformed {} LRGPS vehicles to GTFS-RT trip updates", tripUpdates.size());
            
        } catch (Exception e) {
            LOG.error("Error transforming LRGPS to TripUpdates: {}", e.getMessage(), e);
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
            LOG.error("Failed to parse LRGPS payload: {}", e.getMessage());
            return null;
        }
    }
    
    private JsonNode findVehicleData(JsonNode root) {
        // Common LRGPS structures
        if (root.has("vehicles")) {
            return root.path("vehicles");
        }
        if (root.has("VehicleList")) {
            return root.path("VehicleList").path("Vehicle");
        }
        if (root.has("LRGPS")) {
            return root.path("LRGPS").path("Vehicle");
        }
        if (root.has("vehicle")) {
            return root.path("vehicle");
        }
        if (root.isArray()) {
            return root;
        }
        
        // If root is directly a vehicle object
        if (root.has("vehicleId") || root.has("VehicleId") || root.has("id")) {
            return root;
        }
        
        return null;
    }
    
    private GtfsRealtime.VehiclePosition transformLRGPSVehicle(JsonNode vehicle) {
        try {
            GtfsRealtime.VehiclePosition.Builder positionBuilder = 
                GtfsRealtime.VehiclePosition.newBuilder();
            
            // Extract vehicle ID
            String vehicleId = extractString(vehicle, "vehicleId", "VehicleId", "id", "vehicle_id");
            if (vehicleId == null || vehicleId.isEmpty()) {
                LOG.debug("No vehicle ID found in LRGPS data");
                return null;
            }
            
            // Build Vehicle descriptor
            GtfsRealtime.VehicleDescriptor.Builder vehicleDesc = 
                GtfsRealtime.VehicleDescriptor.newBuilder()
                    .setId(vehicleId);
            
            // Add label if available
            String label = extractString(vehicle, "label", "Label", "trainNumber", "train_number");
            if (label != null) {
                vehicleDesc.setLabel(label);
            }
            
            positionBuilder.setVehicle(vehicleDesc);
            
            // Extract position
            Double lat = extractDouble(vehicle, "latitude", "lat", "Latitude", "Lat");
            Double lon = extractDouble(vehicle, "longitude", "lon", "Longitude", "Lon", "lng");
            
            if (lat != null && lon != null) {
                GtfsRealtime.Position.Builder position = GtfsRealtime.Position.newBuilder()
                    .setLatitude(lat.floatValue())
                    .setLongitude(lon.floatValue());
                
                // Add bearing if available
                Double bearing = extractDouble(vehicle, "bearing", "heading", "direction");
                if (bearing != null) {
                    position.setBearing(bearing.floatValue());
                }
                
                // Add speed if available (convert from mph to m/s if needed)
                Double speed = extractDouble(vehicle, "speed", "Speed", "velocity");
                if (speed != null) {
                    // Assume speed is in mph, convert to m/s
                    float speedMs = (float)(speed * 0.44704); // mph to m/s conversion
                    position.setSpeed(speedMs);
                }
                
                positionBuilder.setPosition(position);
            } else {
                LOG.debug("No valid position found for vehicle {}", vehicleId);
                return null;
            }
            
            // Extract trip information if available
            String tripId = extractString(vehicle, "tripId", "trip_id", "TripId", "trip");
            String routeId = extractString(vehicle, "routeId", "route_id", "RouteId", "line", "Line");
            
            if (tripId != null || routeId != null) {
                GtfsRealtime.TripDescriptor.Builder tripDesc = 
                    GtfsRealtime.TripDescriptor.newBuilder();
                
                if (tripId != null) {
                    tripDesc.setTripId(tripId);
                }
                if (routeId != null) {
                    tripDesc.setRouteId(routeId);
                }
                
                positionBuilder.setTrip(tripDesc);
            }
            
            // Set timestamp
            Long timestamp = extractTimestamp(vehicle);
            if (timestamp != null) {
                positionBuilder.setTimestamp(timestamp);
            } else {
                positionBuilder.setTimestamp(System.currentTimeMillis() / 1000);
            }
            
            // Set current status if available
            String status = extractString(vehicle, "status", "Status", "state");
            if (status != null) {
                GtfsRealtime.VehiclePosition.VehicleStopStatus stopStatus = 
                    parseStopStatus(status);
                if (stopStatus != null) {
                    positionBuilder.setCurrentStopSequence(0);
                    positionBuilder.setCurrentStatus(stopStatus);
                }
            }
            
            // Set occupancy status if available
            String occupancy = extractString(vehicle, "occupancy", "Occupancy", "load");
            if (occupancy != null) {
                GtfsRealtime.VehiclePosition.OccupancyStatus occStatus = 
                    parseOccupancyStatus(occupancy);
                if (occStatus != null) {
                    positionBuilder.setOccupancyStatus(occStatus);
                }
            }
            
            return positionBuilder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming LRGPS vehicle: {}", e.getMessage());
            return null;
        }
    }
    
    private GtfsRealtime.TripUpdate transformLRGPSTripUpdate(JsonNode vehicle) {
        try {
            String tripId = extractString(vehicle, "tripId", "trip_id", "TripId", "trip");
            String routeId = extractString(vehicle, "routeId", "route_id", "RouteId", "line", "Line");
            
            if (tripId == null && routeId == null) {
                return null; // Need at least one identifier
            }
            
            GtfsRealtime.TripUpdate.Builder updateBuilder = 
                GtfsRealtime.TripUpdate.newBuilder();
            
            // Build trip descriptor
            GtfsRealtime.TripDescriptor.Builder tripDesc = 
                GtfsRealtime.TripDescriptor.newBuilder();
            
            if (tripId != null) {
                tripDesc.setTripId(tripId);
            }
            if (routeId != null) {
                tripDesc.setRouteId(routeId);
            }
            
            updateBuilder.setTrip(tripDesc);
            
            // Build vehicle descriptor
            String vehicleId = extractString(vehicle, "vehicleId", "VehicleId", "id", "vehicle_id");
            if (vehicleId != null) {
                GtfsRealtime.VehicleDescriptor.Builder vehicleDesc = 
                    GtfsRealtime.VehicleDescriptor.newBuilder()
                        .setId(vehicleId);
                updateBuilder.setVehicle(vehicleDesc);
            }
            
            // Set timestamp
            Long timestamp = extractTimestamp(vehicle);
            if (timestamp != null) {
                updateBuilder.setTimestamp(timestamp);
            } else {
                updateBuilder.setTimestamp(System.currentTimeMillis() / 1000);
            }
            
            // Add delay information if available
            Integer delay = extractInteger(vehicle, "delay", "Delay", "delaySeconds");
            if (delay != null) {
                updateBuilder.setDelay(delay);
            }
            
            // Note: Stop time updates would require more detailed schedule information
            // which may not be available in basic LRGPS data
            
            return updateBuilder.build();
            
        } catch (Exception e) {
            LOG.error("Error transforming LRGPS to TripUpdate: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractString(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.path(field).isNull()) {
                return node.path(field).asText();
            }
        }
        return null;
    }
    
    private Double extractDouble(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.path(field).isNull()) {
                try {
                    return node.path(field).asDouble();
                } catch (Exception e) {
                    LOG.debug("Failed to parse double from field {}: {}", field, e.getMessage());
                }
            }
        }
        return null;
    }
    
    private Integer extractInteger(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.has(field) && !node.path(field).isNull()) {
                try {
                    return node.path(field).asInt();
                } catch (Exception e) {
                    LOG.debug("Failed to parse integer from field {}: {}", field, e.getMessage());
                }
            }
        }
        return null;
    }
    
    private Long extractTimestamp(JsonNode node) {
        // Try various timestamp fields
        String[] timestampFields = {"timestamp", "Timestamp", "time", "Time", 
                                   "recordedTime", "RecordedTime", "lastUpdate"};
        
        for (String field : timestampFields) {
            if (node.has(field) && !node.path(field).isNull()) {
                String timeStr = node.path(field).asText();
                try {
                    // Try parsing as epoch milliseconds
                    if (timeStr.matches("\\d+")) {
                        long ts = Long.parseLong(timeStr);
                        // Convert to seconds if needed
                        if (ts > 10000000000L) {
                            return ts / 1000;
                        }
                        return ts;
                    }
                    
                    // Try parsing as ISO date
                    Instant instant = Instant.parse(timeStr);
                    return instant.getEpochSecond();
                } catch (Exception e) {
                    LOG.debug("Failed to parse timestamp: {}", e.getMessage());
                }
            }
        }
        return null;
    }
    
    private GtfsRealtime.VehiclePosition.VehicleStopStatus parseStopStatus(String status) {
        String upperStatus = status.toUpperCase();
        if (upperStatus.contains("STOP") || upperStatus.contains("DWELL")) {
            return GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT;
        } else if (upperStatus.contains("TRANSIT") || upperStatus.contains("MOVING")) {
            return GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO;
        }
        return null;
    }
    
    private GtfsRealtime.VehiclePosition.OccupancyStatus parseOccupancyStatus(String occupancy) {
        String upperOcc = occupancy.toUpperCase();
        if (upperOcc.contains("EMPTY") || upperOcc.contains("FEW")) {
            return GtfsRealtime.VehiclePosition.OccupancyStatus.EMPTY;
        } else if (upperOcc.contains("MANY") || upperOcc.contains("HALF")) {
            return GtfsRealtime.VehiclePosition.OccupancyStatus.MANY_SEATS_AVAILABLE;
        } else if (upperOcc.contains("STANDING") || upperOcc.contains("FEW_SEATS")) {
            return GtfsRealtime.VehiclePosition.OccupancyStatus.FEW_SEATS_AVAILABLE;
        } else if (upperOcc.contains("FULL") || upperOcc.contains("CRUSHED")) {
            return GtfsRealtime.VehiclePosition.OccupancyStatus.FULL;
        }
        return null;
    }
}