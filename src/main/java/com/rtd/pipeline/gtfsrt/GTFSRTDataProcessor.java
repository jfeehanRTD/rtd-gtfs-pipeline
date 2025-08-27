package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data processor for GTFS-RT feeds that handles quality control,
 * deduplication, validation, and data enrichment.
 */
public class GTFSRTDataProcessor {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRTDataProcessor.class);
    
    // Data quality thresholds
    private static final double MIN_LATITUDE = 39.0; // Southern Colorado boundary
    private static final double MAX_LATITUDE = 41.0; // Northern Colorado boundary  
    private static final double MIN_LONGITUDE = -106.0; // Western Colorado boundary
    private static final double MAX_LONGITUDE = -104.0; // Eastern Colorado boundary
    private static final float MAX_REASONABLE_SPEED = 100.0f; // 100 mph max
    private static final long MAX_TIMESTAMP_AGE_MS = 24 * 60 * 60 * 1000L; // 24 hours
    private static final int MAX_DELAY_SECONDS = 60 * 60; // 1 hour max delay
    
    /**
     * Process and validate vehicle positions
     */
    public List<GtfsRealtime.VehiclePosition> processVehiclePositions(List<GtfsRealtime.VehiclePosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<GtfsRealtime.VehiclePosition> processed = new ArrayList<>();
        Set<String> seenVehicleIds = new HashSet<>();
        int duplicateCount = 0;
        int invalidCount = 0;
        
        for (GtfsRealtime.VehiclePosition position : positions) {
            try {
                // Validate vehicle position
                if (!isValidVehiclePosition(position)) {
                    invalidCount++;
                    continue;
                }
                
                // Deduplicate by vehicle ID (keep most recent)
                String vehicleId = position.getVehicle().getId();
                if (seenVehicleIds.contains(vehicleId)) {
                    duplicateCount++;
                    // Skip duplicate - we already have a more recent entry
                    continue;
                }
                seenVehicleIds.add(vehicleId);
                
                // Add processed position
                GtfsRealtime.VehiclePosition enrichedPosition = enrichVehiclePosition(position);
                processed.add(enrichedPosition);
                
            } catch (Exception e) {
                LOG.warn("Error processing vehicle position: {}", e.getMessage());
                invalidCount++;
            }
        }
        
        if (duplicateCount > 0 || invalidCount > 0) {
            LOG.debug("Vehicle position processing: {} valid, {} duplicates removed, {} invalid",
                processed.size(), duplicateCount, invalidCount);
        }
        
        return processed;
    }
    
    /**
     * Process and validate trip updates
     */
    public List<GtfsRealtime.TripUpdate> processTripUpdates(List<GtfsRealtime.TripUpdate> tripUpdates) {
        if (tripUpdates == null || tripUpdates.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<GtfsRealtime.TripUpdate> processed = new ArrayList<>();
        Set<String> seenTripIds = new HashSet<>();
        int duplicateCount = 0;
        int invalidCount = 0;
        
        for (GtfsRealtime.TripUpdate tripUpdate : tripUpdates) {
            try {
                // Validate trip update
                if (!isValidTripUpdate(tripUpdate)) {
                    invalidCount++;
                    continue;
                }
                
                // Deduplicate by trip ID or route+vehicle if no trip ID
                String deduplicationKey;
                if (tripUpdate.getTrip().hasTripId() && !tripUpdate.getTrip().getTripId().isEmpty()) {
                    deduplicationKey = tripUpdate.getTrip().getTripId();
                } else {
                    // Use route + vehicle ID for deduplication when no trip ID
                    String routeId = tripUpdate.getTrip().getRouteId();
                    String vehicleId = tripUpdate.hasVehicle() ? tripUpdate.getVehicle().getId() : "unknown";
                    deduplicationKey = routeId + ":" + vehicleId;
                }
                
                if (seenTripIds.contains(deduplicationKey)) {
                    duplicateCount++;
                    // Remove previous entry and add new one
                    final String finalKey = deduplicationKey;
                    processed.removeIf(tu -> {
                        String existingKey;
                        if (tu.getTrip().hasTripId() && !tu.getTrip().getTripId().isEmpty()) {
                            existingKey = tu.getTrip().getTripId();
                        } else {
                            String existingRoute = tu.getTrip().getRouteId();
                            String existingVehicle = tu.hasVehicle() ? tu.getVehicle().getId() : "unknown";
                            existingKey = existingRoute + ":" + existingVehicle;
                        }
                        return existingKey.equals(finalKey);
                    });
                } else {
                    seenTripIds.add(deduplicationKey);
                }
                
                // Validate and normalize delays
                GtfsRealtime.TripUpdate normalizedTripUpdate = validateAndNormalizeDelays(tripUpdate);
                
                // Add processed trip update
                GtfsRealtime.TripUpdate enrichedTripUpdate = enrichTripUpdate(normalizedTripUpdate);
                processed.add(enrichedTripUpdate);
                
            } catch (Exception e) {
                LOG.warn("Error processing trip update: {}", e.getMessage());
                invalidCount++;
            }
        }
        
        if (duplicateCount > 0 || invalidCount > 0) {
            LOG.debug("Trip update processing: {} valid, {} duplicates removed, {} invalid",
                processed.size(), duplicateCount, invalidCount);
        }
        
        return processed;
    }
    
    /**
     * Process and validate alerts
     */
    public List<GtfsRealtime.Alert> processAlerts(List<GtfsRealtime.Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<GtfsRealtime.Alert> processed = new ArrayList<>();
        int invalidCount = 0;
        
        for (GtfsRealtime.Alert alert : alerts) {
            try {
                if (isValidAlert(alert)) {
                    GtfsRealtime.Alert enrichedAlert = enrichAlert(alert);
                    processed.add(enrichedAlert);
                } else {
                    invalidCount++;
                }
            } catch (Exception e) {
                LOG.warn("Error processing alert: {}", e.getMessage());
                invalidCount++;
            }
        }
        
        if (invalidCount > 0) {
            LOG.debug("Alert processing: {} valid, {} invalid", processed.size(), invalidCount);
        }
        
        return processed;
    }
    
    /**
     * Validate vehicle position data
     */
    private boolean isValidVehiclePosition(GtfsRealtime.VehiclePosition position) {
        // Must have vehicle ID
        if (!position.hasVehicle() || !position.getVehicle().hasId() || 
            position.getVehicle().getId().trim().isEmpty()) {
            LOG.debug("Vehicle position missing vehicle ID");
            return false;
        }
        
        // Must have position
        if (!position.hasPosition()) {
            LOG.debug("Vehicle position missing position data for vehicle {}", position.getVehicle().getId());
            return false;
        }
        
        GtfsRealtime.Position pos = position.getPosition();
        
        // Validate coordinates (Colorado bounding box)
        float lat = pos.getLatitude();
        float lon = pos.getLongitude();
        
        if (lat < MIN_LATITUDE || lat > MAX_LATITUDE || 
            lon < MIN_LONGITUDE || lon > MAX_LONGITUDE) {
            LOG.debug("Vehicle {} has coordinates outside Colorado: lat={}, lon={}", 
                position.getVehicle().getId(), lat, lon);
            return false;
        }
        
        // Validate speed if present
        if (pos.hasSpeed()) {
            float speed = pos.getSpeed();
            if (speed < 0.0f || speed > MAX_REASONABLE_SPEED) {
                LOG.debug("Vehicle {} has invalid speed: {} mph", 
                    position.getVehicle().getId(), speed);
                return false;
            }
        }
        
        // Validate timestamp if present
        if (position.hasTimestamp()) {
            long timestampMs = position.getTimestamp() * 1000;
            long age = System.currentTimeMillis() - timestampMs;
            
            if (age > MAX_TIMESTAMP_AGE_MS) {
                LOG.debug("Vehicle {} has stale timestamp: {} ms old", 
                    position.getVehicle().getId(), age);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Validate trip update data
     */
    private boolean isValidTripUpdate(GtfsRealtime.TripUpdate tripUpdate) {
        // Must have trip descriptor with route ID at minimum
        if (!tripUpdate.hasTrip() || !tripUpdate.getTrip().hasRouteId() ||
            tripUpdate.getTrip().getRouteId().trim().isEmpty()) {
            LOG.debug("Trip update missing route ID");
            return false;
        }
        
        // Must have at least one stop time update
        if (tripUpdate.getStopTimeUpdateCount() == 0) {
            String tripId = tripUpdate.getTrip().hasTripId() ? tripUpdate.getTrip().getTripId() : tripUpdate.getTrip().getRouteId();
            LOG.debug("Trip update {} has no stop time updates", tripId);
            return false;
        }
        
        // Delays will be validated and normalized in the processing step
        
        return true;
    }
    
    /**
     * Validate and normalize delays in trip updates
     */
    private GtfsRealtime.TripUpdate validateAndNormalizeDelays(GtfsRealtime.TripUpdate tripUpdate) {
        GtfsRealtime.TripUpdate.Builder builder = GtfsRealtime.TripUpdate.newBuilder(tripUpdate);
        List<GtfsRealtime.TripUpdate.StopTimeUpdate> validStopUpdates = new ArrayList<>();
        
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopUpdate : tripUpdate.getStopTimeUpdateList()) {
            GtfsRealtime.TripUpdate.StopTimeUpdate.Builder stopBuilder = 
                GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder(stopUpdate);
            
            // Validate and cap arrival delay
            if (stopUpdate.hasArrival() && stopUpdate.getArrival().hasDelay()) {
                int delay = stopUpdate.getArrival().getDelay();
                if (Math.abs(delay) > MAX_DELAY_SECONDS) {
                    LOG.debug("Trip {} arrival delay capped from {} to {} seconds", 
                        tripUpdate.getTrip().getTripId(), delay, 
                        delay > 0 ? MAX_DELAY_SECONDS : -MAX_DELAY_SECONDS);
                    
                    GtfsRealtime.TripUpdate.StopTimeEvent.Builder arrivalBuilder = 
                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder(stopUpdate.getArrival());
                    arrivalBuilder.setDelay(delay > 0 ? MAX_DELAY_SECONDS : -MAX_DELAY_SECONDS);
                    stopBuilder.setArrival(arrivalBuilder.build());
                }
            }
            
            // Validate and cap departure delay
            if (stopUpdate.hasDeparture() && stopUpdate.getDeparture().hasDelay()) {
                int delay = stopUpdate.getDeparture().getDelay();
                if (Math.abs(delay) > MAX_DELAY_SECONDS) {
                    LOG.debug("Trip {} departure delay capped from {} to {} seconds", 
                        tripUpdate.getTrip().getTripId(), delay, 
                        delay > 0 ? MAX_DELAY_SECONDS : -MAX_DELAY_SECONDS);
                    
                    GtfsRealtime.TripUpdate.StopTimeEvent.Builder departureBuilder = 
                        GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder(stopUpdate.getDeparture());
                    departureBuilder.setDelay(delay > 0 ? MAX_DELAY_SECONDS : -MAX_DELAY_SECONDS);
                    stopBuilder.setDeparture(departureBuilder.build());
                }
            }
            
            validStopUpdates.add(stopBuilder.build());
        }
        
        builder.clearStopTimeUpdate();
        builder.addAllStopTimeUpdate(validStopUpdates);
        return builder.build();
    }
    
    /**
     * Validate alert data
     */
    private boolean isValidAlert(GtfsRealtime.Alert alert) {
        // Must have header text or description
        if ((!alert.hasHeaderText() || alert.getHeaderText().getTranslationCount() == 0) &&
            (!alert.hasDescriptionText() || alert.getDescriptionText().getTranslationCount() == 0)) {
            LOG.debug("Alert has no header text or description");
            return false;
        }
        
        // Check active periods if present
        if (alert.getActivePeriodCount() > 0) {
            long currentTime = Instant.now().getEpochSecond();
            boolean hasActiveOrFuturePeriod = false;
            
            for (GtfsRealtime.TimeRange period : alert.getActivePeriodList()) {
                if (period.hasEnd() && period.getEnd() > currentTime) {
                    hasActiveOrFuturePeriod = true;
                    break;
                }
                if (!period.hasStart() || period.getStart() <= currentTime) {
                    hasActiveOrFuturePeriod = true;
                    break;
                }
            }
            
            if (!hasActiveOrFuturePeriod) {
                LOG.debug("Alert has only expired active periods");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Enrich vehicle position with additional data
     */
    private GtfsRealtime.VehiclePosition enrichVehiclePosition(GtfsRealtime.VehiclePosition position) {
        GtfsRealtime.VehiclePosition.Builder builder = GtfsRealtime.VehiclePosition.newBuilder(position);
        
        // Add timestamp if missing
        if (!builder.hasTimestamp()) {
            builder.setTimestamp(Instant.now().getEpochSecond());
        }
        
        // Normalize vehicle ID
        if (builder.hasVehicle()) {
            GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = 
                GtfsRealtime.VehicleDescriptor.newBuilder(builder.getVehicle());
            vehicleBuilder.setId(normalizeVehicleId(vehicleBuilder.getId()));
            builder.setVehicle(vehicleBuilder.build());
        }
        
        // Normalize route ID in trip descriptor
        if (builder.hasTrip() && builder.getTrip().hasRouteId()) {
            GtfsRealtime.TripDescriptor.Builder tripBuilder = 
                GtfsRealtime.TripDescriptor.newBuilder(builder.getTrip());
            tripBuilder.setRouteId(normalizeRouteId(tripBuilder.getRouteId()));
            builder.setTrip(tripBuilder.build());
        }
        
        return builder.build();
    }
    
    /**
     * Enrich trip update with additional data
     */
    private GtfsRealtime.TripUpdate enrichTripUpdate(GtfsRealtime.TripUpdate tripUpdate) {
        GtfsRealtime.TripUpdate.Builder builder = GtfsRealtime.TripUpdate.newBuilder(tripUpdate);
        
        // Add timestamp if missing
        if (!builder.hasTimestamp()) {
            builder.setTimestamp(Instant.now().getEpochSecond());
        }
        
        // Normalize vehicle ID
        if (builder.hasVehicle()) {
            GtfsRealtime.VehicleDescriptor.Builder vehicleBuilder = 
                GtfsRealtime.VehicleDescriptor.newBuilder(builder.getVehicle());
            vehicleBuilder.setId(normalizeVehicleId(vehicleBuilder.getId()));
            builder.setVehicle(vehicleBuilder.build());
        }
        
        // Normalize route ID in trip descriptor
        if (builder.hasTrip() && builder.getTrip().hasRouteId()) {
            GtfsRealtime.TripDescriptor.Builder tripBuilder = 
                GtfsRealtime.TripDescriptor.newBuilder(builder.getTrip());
            tripBuilder.setRouteId(normalizeRouteId(tripBuilder.getRouteId()));
            builder.setTrip(tripBuilder.build());
        }
        
        return builder.build();
    }
    
    /**
     * Enrich alert with additional data
     */
    private GtfsRealtime.Alert enrichAlert(GtfsRealtime.Alert alert) {
        // For now, just return the alert as-is
        // Future enhancements could include:
        // - Adding default active periods
        // - Normalizing affected route/stop IDs
        // - Adding severity levels
        return alert;
    }
    
    /**
     * Normalize vehicle ID to consistent format
     */
    private String normalizeVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            return vehicleId;
        }
        
        String normalized = vehicleId.trim();
        
        // Remove common prefixes
        if (normalized.startsWith("VEHICLE_") || normalized.startsWith("VEH_")) {
            normalized = normalized.substring(normalized.indexOf("_") + 1);
        }
        
        // Ensure numeric vehicle IDs are zero-padded to 4 digits
        if (normalized.matches("\\d+")) {
            int vehicleNumber = Integer.parseInt(normalized);
            if (vehicleNumber < 10000) {
                normalized = String.format("%04d", vehicleNumber);
            }
        }
        
        return normalized;
    }
    
    /**
     * Normalize route ID to RTD standard format
     */
    private String normalizeRouteId(String routeId) {
        if (routeId == null || routeId.trim().isEmpty()) {
            return routeId;
        }
        
        String normalized = routeId.trim().toUpperCase();
        
        // Remove common prefixes
        if (normalized.startsWith("ROUTE_") || normalized.startsWith("LINE_")) {
            normalized = normalized.substring(normalized.indexOf("_") + 1);
        }
        
        // Light rail routes should be single letters
        String[] lightRailRoutes = {"A", "B", "D", "E", "F", "G", "H", "L", "N", "R", "W"};
        for (String route : lightRailRoutes) {
            if (normalized.contains(route)) {
                return route;
            }
        }
        
        return normalized;
    }
    
    /**
     * Get processing statistics
     */
    public ProcessingStats getProcessingStats(List<GtfsRealtime.VehiclePosition> originalVehicles,
                                            List<GtfsRealtime.VehiclePosition> processedVehicles,
                                            List<GtfsRealtime.TripUpdate> originalTrips,
                                            List<GtfsRealtime.TripUpdate> processedTrips,
                                            List<GtfsRealtime.Alert> originalAlerts,
                                            List<GtfsRealtime.Alert> processedAlerts) {
        return new ProcessingStats(
            originalVehicles != null ? originalVehicles.size() : 0,
            processedVehicles != null ? processedVehicles.size() : 0,
            originalTrips != null ? originalTrips.size() : 0,
            processedTrips != null ? processedTrips.size() : 0,
            originalAlerts != null ? originalAlerts.size() : 0,
            processedAlerts != null ? processedAlerts.size() : 0
        );
    }
    
    /**
     * Statistics for data processing operations
     */
    public static class ProcessingStats {
        private final int originalVehicleCount;
        private final int processedVehicleCount;
        private final int originalTripCount;
        private final int processedTripCount;
        private final int originalAlertCount;
        private final int processedAlertCount;
        
        public ProcessingStats(int originalVehicleCount, int processedVehicleCount,
                              int originalTripCount, int processedTripCount,
                              int originalAlertCount, int processedAlertCount) {
            this.originalVehicleCount = originalVehicleCount;
            this.processedVehicleCount = processedVehicleCount;
            this.originalTripCount = originalTripCount;
            this.processedTripCount = processedTripCount;
            this.originalAlertCount = originalAlertCount;
            this.processedAlertCount = processedAlertCount;
        }
        
        public int getOriginalVehicleCount() { return originalVehicleCount; }
        public int getProcessedVehicleCount() { return processedVehicleCount; }
        public int getOriginalTripCount() { return originalTripCount; }
        public int getProcessedTripCount() { return processedTripCount; }
        public int getOriginalAlertCount() { return originalAlertCount; }
        public int getProcessedAlertCount() { return processedAlertCount; }
        
        public int getVehicleFilteredCount() { return originalVehicleCount - processedVehicleCount; }
        public int getTripFilteredCount() { return originalTripCount - processedTripCount; }
        public int getAlertFilteredCount() { return originalAlertCount - processedAlertCount; }
        
        @Override
        public String toString() {
            return String.format("ProcessingStats{vehicles: %d->%d, trips: %d->%d, alerts: %d->%d}", 
                originalVehicleCount, processedVehicleCount,
                originalTripCount, processedTripCount,
                originalAlertCount, processedAlertCount);
        }
    }
}