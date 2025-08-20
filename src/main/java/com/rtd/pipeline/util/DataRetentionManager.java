package com.rtd.pipeline.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Data Retention Manager for RTD Pipeline
 * Automatically cleans up files older than specified retention period
 * Ensures SIRI and Rail Communication data is only stored for 1 day
 */
public class DataRetentionManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(DataRetentionManager.class);
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final String[] dataPaths;
    private final long retentionDays;
    
    public DataRetentionManager(String[] dataPaths, long retentionDays) {
        this.dataPaths = dataPaths;
        this.retentionDays = retentionDays;
    }
    
    /**
     * Start the data retention cleanup scheduler
     * Runs cleanup every 6 hours
     */
    public void startCleanupScheduler() {
        LOG.info("Starting data retention manager with {}-day retention for paths: {}", 
                retentionDays, Arrays.toString(dataPaths));
        
        // Run cleanup immediately, then every 6 hours
        scheduler.scheduleAtFixedRate(this::cleanupOldFiles, 0, 6, TimeUnit.HOURS);
    }
    
    /**
     * Stop the cleanup scheduler
     */
    public void stopCleanupScheduler() {
        LOG.info("Stopping data retention manager");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Clean up files older than retention period
     */
    private void cleanupOldFiles() {
        LOG.info("Running data retention cleanup for {}-day retention", retentionDays);
        
        Instant cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        
        for (String dataPath : dataPaths) {
            try {
                Path path = Paths.get(dataPath);
                if (!Files.exists(path)) {
                    LOG.debug("Data path does not exist: {}", dataPath);
                    continue;
                }
                
                cleanupDirectory(path, cutoffTime);
                
            } catch (Exception e) {
                LOG.error("Error cleaning up data path {}: {}", dataPath, e.getMessage(), e);
            }
        }
        
        LOG.info("Data retention cleanup completed");
    }
    
    /**
     * Recursively clean up files in directory
     */
    private void cleanupDirectory(Path dir, Instant cutoffTime) {
        try {
            Files.walk(dir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        Instant fileTime = Files.getLastModifiedTime(file).toInstant();
                        if (fileTime.isBefore(cutoffTime)) {
                            Files.delete(file);
                            LOG.debug("Deleted old file: {}", file);
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to delete file {}: {}", file, e.getMessage());
                    }
                });
                
            // Clean up empty directories
            Files.walk(dir)
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(dir))
                .forEach(emptyDir -> {
                    try {
                        File[] files = emptyDir.toFile().listFiles();
                        if (files != null && files.length == 0) {
                            Files.delete(emptyDir);
                            LOG.debug("Deleted empty directory: {}", emptyDir);
                        }
                    } catch (Exception e) {
                        LOG.debug("Could not delete directory {}: {}", emptyDir, e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            LOG.error("Error walking directory {}: {}", dir, e.getMessage(), e);
        }
    }
    
    /**
     * Perform immediate cleanup
     */
    public void cleanupNow() {
        LOG.info("Performing immediate data cleanup");
        cleanupOldFiles();
    }
    
    /**
     * Create a data retention manager for RTD pipelines with 1-day retention
     */
    public static DataRetentionManager createForRTDPipelines() {
        String[] dataPaths = {
            "./data/bus-siri/",
            "./data/rail-comm/"
        };
        return new DataRetentionManager(dataPaths, 1L); // 1 day retention
    }
}