package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates GTFS-RT protobuf feeds from vehicle positions and trip updates.
 * Creates atomic file updates compatible with RTD's existing feed endpoints.
 */
public class GTFSRTFeedGenerator {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRTFeedGenerator.class);
    
    private final String outputDirectory;
    private final AtomicLong feedSequence;
    private final String feedPublisherName;
    private final String feedPublisherUrl;
    private final String feedLanguage;
    
    // Feed file names matching RTD's existing endpoints
    private static final String VEHICLE_POSITIONS_FILE = "VehiclePosition.pb";
    private static final String TRIP_UPDATES_FILE = "TripUpdate.pb";
    private static final String ALERTS_FILE = "Alerts.pb";
    
    // Temporary file suffix for atomic updates
    private static final String TEMP_SUFFIX = ".tmp";
    
    public GTFSRTFeedGenerator(String outputDirectory, String publisherName, String publisherUrl) {
        this.outputDirectory = outputDirectory;
        this.feedSequence = new AtomicLong(1);
        this.feedPublisherName = publisherName != null ? publisherName : "RTD Denver";
        this.feedPublisherUrl = publisherUrl != null ? publisherUrl : "https://www.rtd-denver.com";
        this.feedLanguage = "en";
        
        // Ensure output directory exists
        try {
            Files.createDirectories(Paths.get(outputDirectory));
            LOG.info("GTFS-RT feed generator initialized with output directory: {}", outputDirectory);
        } catch (IOException e) {
            LOG.error("Failed to create output directory: {}", outputDirectory, e);
            throw new RuntimeException("Cannot initialize feed generator", e);
        }
    }
    
    /**
     * Generate VehiclePosition.pb file from list of vehicle positions
     */
    public boolean generateVehiclePositionsFeed(List<GtfsRealtime.VehiclePosition> vehiclePositions) {
        if (vehiclePositions == null || vehiclePositions.isEmpty()) {
            LOG.debug("No vehicle positions to write");
            return false;
        }
        
        try {
            // Build FeedMessage
            GtfsRealtime.FeedMessage.Builder feedBuilder = GtfsRealtime.FeedMessage.newBuilder();
            
            // Feed header
            GtfsRealtime.FeedHeader.Builder headerBuilder = GtfsRealtime.FeedHeader.newBuilder();
            headerBuilder.setGtfsRealtimeVersion("2.0");
            headerBuilder.setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET);
            headerBuilder.setTimestamp(Instant.now().getEpochSecond());
            feedBuilder.setHeader(headerBuilder.build());
            
            // Add vehicle positions as feed entities
            int entityId = 1;
            for (GtfsRealtime.VehiclePosition vehiclePosition : vehiclePositions) {
                GtfsRealtime.FeedEntity.Builder entityBuilder = GtfsRealtime.FeedEntity.newBuilder();
                entityBuilder.setId(String.valueOf(entityId++));
                entityBuilder.setVehicle(vehiclePosition);
                feedBuilder.addEntity(entityBuilder.build());
            }
            
            // Write to file atomically
            Path outputPath = Paths.get(outputDirectory, VEHICLE_POSITIONS_FILE);
            boolean success = writeProtobufFile(feedBuilder.build(), outputPath);
            
            if (success) {
                LOG.debug("Generated VehiclePosition.pb with {} vehicles", vehiclePositions.size());
            }
            
            return success;
            
        } catch (Exception e) {
            LOG.error("Error generating vehicle positions feed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Generate TripUpdate.pb file from list of trip updates
     */
    public boolean generateTripUpdatesFeed(List<GtfsRealtime.TripUpdate> tripUpdates) {
        if (tripUpdates == null || tripUpdates.isEmpty()) {
            LOG.debug("No trip updates to write");
            return false;
        }
        
        try {
            // Build FeedMessage
            GtfsRealtime.FeedMessage.Builder feedBuilder = GtfsRealtime.FeedMessage.newBuilder();
            
            // Feed header
            GtfsRealtime.FeedHeader.Builder headerBuilder = GtfsRealtime.FeedHeader.newBuilder();
            headerBuilder.setGtfsRealtimeVersion("2.0");
            headerBuilder.setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET);
            headerBuilder.setTimestamp(Instant.now().getEpochSecond());
            feedBuilder.setHeader(headerBuilder.build());
            
            // Add trip updates as feed entities
            int entityId = 1;
            for (GtfsRealtime.TripUpdate tripUpdate : tripUpdates) {
                GtfsRealtime.FeedEntity.Builder entityBuilder = GtfsRealtime.FeedEntity.newBuilder();
                entityBuilder.setId(String.valueOf(entityId++));
                entityBuilder.setTripUpdate(tripUpdate);
                feedBuilder.addEntity(entityBuilder.build());
            }
            
            // Write to file atomically
            Path outputPath = Paths.get(outputDirectory, TRIP_UPDATES_FILE);
            boolean success = writeProtobufFile(feedBuilder.build(), outputPath);
            
            if (success) {
                LOG.debug("Generated TripUpdate.pb with {} trip updates", tripUpdates.size());
            }
            
            return success;
            
        } catch (Exception e) {
            LOG.error("Error generating trip updates feed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Generate Alerts.pb file from list of alerts
     */
    public boolean generateAlertsFeed(List<GtfsRealtime.Alert> alerts) {
        try {
            // Build FeedMessage
            GtfsRealtime.FeedMessage.Builder feedBuilder = GtfsRealtime.FeedMessage.newBuilder();
            
            // Feed header
            GtfsRealtime.FeedHeader.Builder headerBuilder = GtfsRealtime.FeedHeader.newBuilder();
            headerBuilder.setGtfsRealtimeVersion("2.0");
            headerBuilder.setIncrementality(GtfsRealtime.FeedHeader.Incrementality.FULL_DATASET);
            headerBuilder.setTimestamp(Instant.now().getEpochSecond());
            feedBuilder.setHeader(headerBuilder.build());
            
            // Add alerts as feed entities (if any)
            if (alerts != null && !alerts.isEmpty()) {
                int entityId = 1;
                for (GtfsRealtime.Alert alert : alerts) {
                    GtfsRealtime.FeedEntity.Builder entityBuilder = GtfsRealtime.FeedEntity.newBuilder();
                    entityBuilder.setId(String.valueOf(entityId++));
                    entityBuilder.setAlert(alert);
                    feedBuilder.addEntity(entityBuilder.build());
                }
            }
            
            // Write to file atomically
            Path outputPath = Paths.get(outputDirectory, ALERTS_FILE);
            boolean success = writeProtobufFile(feedBuilder.build(), outputPath);
            
            if (success) {
                LOG.debug("Generated Alerts.pb with {} alerts", alerts != null ? alerts.size() : 0);
            }
            
            return success;
            
        } catch (Exception e) {
            LOG.error("Error generating alerts feed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Generate all feeds at once
     */
    public GTFSRTFeedResult generateAllFeeds(List<GtfsRealtime.VehiclePosition> vehiclePositions,
                                           List<GtfsRealtime.TripUpdate> tripUpdates,
                                           List<GtfsRealtime.Alert> alerts) {
        
        long startTime = System.currentTimeMillis();
        
        boolean vehiclePosSuccess = generateVehiclePositionsFeed(vehiclePositions);
        boolean tripUpdatesSuccess = generateTripUpdatesFeed(tripUpdates);
        boolean alertsSuccess = generateAlertsFeed(alerts);
        
        long duration = System.currentTimeMillis() - startTime;
        
        GTFSRTFeedResult result = new GTFSRTFeedResult(
            vehiclePosSuccess,
            tripUpdatesSuccess,
            alertsSuccess,
            vehiclePositions != null ? vehiclePositions.size() : 0,
            tripUpdates != null ? tripUpdates.size() : 0,
            alerts != null ? alerts.size() : 0,
            duration
        );
        
        LOG.info("Generated GTFS-RT feeds: VP={} ({}), TU={} ({}) A={} ({}) in {}ms",
            vehiclePosSuccess, result.getVehicleCount(),
            tripUpdatesSuccess, result.getTripUpdateCount(),
            alertsSuccess, result.getAlertCount(),
            duration);
        
        return result;
    }
    
    /**
     * Write protobuf message to file atomically
     */
    private boolean writeProtobufFile(GtfsRealtime.FeedMessage feedMessage, Path outputPath) {
        Path tempPath = Paths.get(outputPath.toString() + TEMP_SUFFIX);
        
        try {
            // Write to temporary file first
            try (FileOutputStream fos = new FileOutputStream(tempPath.toFile())) {
                feedMessage.writeTo(fos);
                fos.flush();
                fos.getFD().sync(); // Force write to disk
            }
            
            // Atomically move temp file to final location
            Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            
            return true;
            
        } catch (IOException e) {
            LOG.error("Error writing protobuf file {}: {}", outputPath, e.getMessage(), e);
            
            // Clean up temp file if it exists
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException cleanupError) {
                LOG.warn("Failed to clean up temp file {}: {}", tempPath, cleanupError.getMessage());
            }
            
            return false;
        }
    }
    
    /**
     * Get the current feed sequence number
     */
    public long getCurrentSequence() {
        return feedSequence.get();
    }
    
    /**
     * Get the output directory
     */
    public String getOutputDirectory() {
        return outputDirectory;
    }
    
    /**
     * Get feed statistics
     */
    public GTFSRTFeedStats getFeedStats() {
        Path vehiclePositionsPath = Paths.get(outputDirectory, VEHICLE_POSITIONS_FILE);
        Path tripUpdatesPath = Paths.get(outputDirectory, TRIP_UPDATES_FILE);
        Path alertsPath = Paths.get(outputDirectory, ALERTS_FILE);
        
        return new GTFSRTFeedStats(
            Files.exists(vehiclePositionsPath) ? getFileSize(vehiclePositionsPath) : 0,
            Files.exists(tripUpdatesPath) ? getFileSize(tripUpdatesPath) : 0,
            Files.exists(alertsPath) ? getFileSize(alertsPath) : 0,
            Files.exists(vehiclePositionsPath) ? getFileLastModified(vehiclePositionsPath) : 0,
            Files.exists(tripUpdatesPath) ? getFileLastModified(tripUpdatesPath) : 0,
            Files.exists(alertsPath) ? getFileLastModified(alertsPath) : 0
        );
    }
    
    private long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }
    
    private long getFileLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
    
    /**
     * Result class for feed generation operations
     */
    public static class GTFSRTFeedResult {
        private final boolean vehiclePositionsSuccess;
        private final boolean tripUpdatesSuccess;
        private final boolean alertsSuccess;
        private final int vehicleCount;
        private final int tripUpdateCount;
        private final int alertCount;
        private final long durationMs;
        
        public GTFSRTFeedResult(boolean vehiclePositionsSuccess, boolean tripUpdatesSuccess, boolean alertsSuccess,
                               int vehicleCount, int tripUpdateCount, int alertCount, long durationMs) {
            this.vehiclePositionsSuccess = vehiclePositionsSuccess;
            this.tripUpdatesSuccess = tripUpdatesSuccess;
            this.alertsSuccess = alertsSuccess;
            this.vehicleCount = vehicleCount;
            this.tripUpdateCount = tripUpdateCount;
            this.alertCount = alertCount;
            this.durationMs = durationMs;
        }
        
        public boolean isVehiclePositionsSuccess() { return vehiclePositionsSuccess; }
        public boolean isTripUpdatesSuccess() { return tripUpdatesSuccess; }
        public boolean isAlertsSuccess() { return alertsSuccess; }
        public int getVehicleCount() { return vehicleCount; }
        public int getTripUpdateCount() { return tripUpdateCount; }
        public int getAlertCount() { return alertCount; }
        public long getDurationMs() { return durationMs; }
        
        public boolean isAllSuccess() {
            return vehiclePositionsSuccess && tripUpdatesSuccess && alertsSuccess;
        }
    }
    
    /**
     * Statistics class for existing feed files
     */
    public static class GTFSRTFeedStats {
        private final long vehiclePositionsSize;
        private final long tripUpdatesSize;
        private final long alertsSize;
        private final long vehiclePositionsLastModified;
        private final long tripUpdatesLastModified;
        private final long alertsLastModified;
        
        public GTFSRTFeedStats(long vehiclePositionsSize, long tripUpdatesSize, long alertsSize,
                              long vehiclePositionsLastModified, long tripUpdatesLastModified, long alertsLastModified) {
            this.vehiclePositionsSize = vehiclePositionsSize;
            this.tripUpdatesSize = tripUpdatesSize;
            this.alertsSize = alertsSize;
            this.vehiclePositionsLastModified = vehiclePositionsLastModified;
            this.tripUpdatesLastModified = tripUpdatesLastModified;
            this.alertsLastModified = alertsLastModified;
        }
        
        public long getVehiclePositionsSize() { return vehiclePositionsSize; }
        public long getTripUpdatesSize() { return tripUpdatesSize; }
        public long getAlertsSize() { return alertsSize; }
        public long getVehiclePositionsLastModified() { return vehiclePositionsLastModified; }
        public long getTripUpdatesLastModified() { return tripUpdatesLastModified; }
        public long getAlertsLastModified() { return alertsLastModified; }
    }
}