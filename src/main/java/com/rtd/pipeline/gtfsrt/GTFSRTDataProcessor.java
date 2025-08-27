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
    private static final int MAX_DELAY_SECONDS = 2 * 60 * 60; // 2 hours max delay
    
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
                    // Remove previous entry and add new one (assuming newer is better)
                    processed.removeIf(vp -> vp.getVehicle().getId().equals(vehicleId));
                } else {
                    seenVehicleIds.add(vehicleId);
                }
                
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
                
                // Deduplicate by trip ID
                String tripId = tripUpdate.getTrip().getTripId();
                if (seenTripIds.contains(tripId)) {
                    duplicateCount++;
                    // Remove previous entry and add new one
                    processed.removeIf(tu -> tu.getTrip().getTripId().equals(tripId));
                } else {
                    seenTripIds.add(tripId);
                }
                
                // Add processed trip update
                GtfsRealtime.TripUpdate enrichedTripUpdate = enrichTripUpdate(tripUpdate);
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
        if (pos.hasSpeed() && pos.getSpeed() > MAX_REASONABLE_SPEED) {
            LOG.debug("Vehicle {} has unreasonable speed: {} mph", 
                position.getVehicle().getId(), pos.getSpeed());
            return false;
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
        // Must have trip descriptor
        if (!tripUpdate.hasTrip() || !tripUpdate.getTrip().hasTripId() ||
            tripUpdate.getTrip().getTripId().trim().isEmpty()) {
            LOG.debug("Trip update missing trip ID");
            return false;
        }
        
        // Must have at least one stop time update
        if (tripUpdate.getStopTimeUpdateCount() == 0) {
            LOG.debug("Trip update {} has no stop time updates", tripUpdate.getTrip().getTripId());
            return false;
        }
        
        // Validate delays are reasonable
        for (GtfsRealtime.TripUpdate.StopTimeUpdate stopUpdate : tripUpdate.getStopTimeUpdateList()) {
            if (stopUpdate.hasArrival() && stopUpdate.getArrival().hasDelay()) {
                int delay = stopUpdate.getArrival().getDelay();
                if (Math.abs(delay) > MAX_DELAY_SECONDS) {
                    LOG.debug("Trip {} has unreasonable delay: {} seconds", 
                        tripUpdate.getTrip().getTripId(), delay);
                    return false;
                }
            }
            
            if (stopUpdate.hasDeparture() && stopUpdate.getDeparture().hasDelay()) {
                int delay = stopUpdate.getDeparture().getDelay();
                if (Math.abs(delay) > MAX_DELAY_SECONDS) {
                    LOG.debug("Trip {} has unreasonable delay: {} seconds", 
                        tripUpdate.getTrip().getTripId(), delay);
                    return false;
                }
            }
        }
        
        return true;
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
        
        String normalized = vehicleId.trim().toUpperCase();
        
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