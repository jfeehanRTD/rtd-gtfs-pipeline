package com.rtd.pipeline.validation;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;
import java.util.*;
import java.time.Instant;

/**
 * Comprehensive GTFS-RT Quality Comparison Test
 * Compares data quality between TIS Producer and RTD Production endpoints
 */
class GTFSRTQualityComparisonTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRTQualityComparisonTest.class);
    
    // TIS Producer endpoints
    private static final String TIS_VEHICLE_URL = "http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb";
    private static final String TIS_TRIP_URL = "http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb";
    private static final String TIS_ALERT_URL = "http://tis-producer-d01:8001/gtfs-rt/Alerts.pb";
    
    // RTD Production endpoints
    private static final String RTD_VEHICLE_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final String RTD_TRIP_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb";
    private static final String RTD_ALERT_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb";
    
    private static final int TIMEOUT_SECONDS = 30;
    
    @BeforeAll
    static void setup() {
        LOG.info("=== GTFS-RT Quality Comparison Test Suite ===");
        LOG.info("Comparing TIS Producer vs RTD Production endpoints");
    }
    
    @Nested
    @DisplayName("Vehicle Position Quality Comparison")
    class VehiclePositionQualityComparison {
        
        @Test
        @DisplayName("Compare Vehicle Position data quality between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareVehiclePositionQuality() {
            LOG.info("Comparing Vehicle Position data quality...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_VEHICLE_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_VEHICLE_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            VehiclePositionQualityResult tisQuality = analyzeVehiclePositionQuality(tisFeed, "TIS Producer");
            VehiclePositionQualityResult rtdQuality = analyzeVehiclePositionQuality(rtdFeed, "RTD Production");
            
            LOG.info("=== Vehicle Position Quality Comparison ===");
            LOG.info("TIS Producer Quality Score: {:.1f}%", tisQuality.overallQualityScore);
            LOG.info("RTD Production Quality Score: {:.1f}%", rtdQuality.overallQualityScore);
            
            // Compare quality metrics
            compareQualityMetrics(tisQuality, rtdQuality, "Vehicle Position");
            
            // Quality assertions
            assertThat(tisQuality.overallQualityScore).isGreaterThan(70.0);
            assertThat(rtdQuality.overallQualityScore).isGreaterThan(70.0);
        }
        
        @Test
        @DisplayName("Compare Vehicle Position data completeness")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareVehiclePositionCompleteness() {
            LOG.info("Comparing Vehicle Position data completeness...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_VEHICLE_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_VEHICLE_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            CompletenessResult tisCompleteness = analyzeCompleteness(tisFeed, "TIS Producer");
            CompletenessResult rtdCompleteness = analyzeCompleteness(rtdFeed, "RTD Production");
            
            LOG.info("=== Vehicle Position Completeness Comparison ===");
            LOG.info("TIS Producer Completeness: {:.1f}%", tisCompleteness.overallCompleteness);
            LOG.info("RTD Production Completeness: {:.1f}%", rtdCompleteness.overallCompleteness);
            
            // Completeness assertions
            assertThat(tisCompleteness.overallCompleteness).isGreaterThan(80.0);
            assertThat(rtdCompleteness.overallCompleteness).isGreaterThan(80.0);
        }
        
        @Test
        @DisplayName("Compare Vehicle Position data accuracy")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareVehiclePositionAccuracy() {
            LOG.info("Comparing Vehicle Position data accuracy...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_VEHICLE_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_VEHICLE_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            AccuracyResult tisAccuracy = analyzeAccuracy(tisFeed, "TIS Producer");
            AccuracyResult rtdAccuracy = analyzeAccuracy(rtdFeed, "RTD Production");
            
            LOG.info("=== Vehicle Position Accuracy Comparison ===");
            LOG.info("TIS Producer Accuracy: {:.1f}%", tisAccuracy.overallAccuracy);
            LOG.info("RTD Production Accuracy: {:.1f}%", rtdAccuracy.overallAccuracy);
            
            // Accuracy assertions
            assertThat(tisAccuracy.overallAccuracy).isGreaterThan(85.0);
            assertThat(rtdAccuracy.overallAccuracy).isGreaterThan(85.0);
        }
    }
    
    @Nested
    @DisplayName("Trip Update Quality Comparison")
    class TripUpdateQualityComparison {
        
        @Test
        @DisplayName("Compare Trip Update data quality between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareTripUpdateQuality() {
            LOG.info("Comparing Trip Update data quality...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_TRIP_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_TRIP_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            TripUpdateQualityResult tisQuality = analyzeTripUpdateQuality(tisFeed, "TIS Producer");
            TripUpdateQualityResult rtdQuality = analyzeTripUpdateQuality(rtdFeed, "RTD Production");
            
            LOG.info("=== Trip Update Quality Comparison ===");
            LOG.info("TIS Producer Quality Score: {:.1f}%", tisQuality.overallQualityScore);
            LOG.info("RTD Production Quality Score: {:.1f}%", rtdQuality.overallQualityScore);
            
            // Compare quality metrics
            compareQualityMetrics(tisQuality, rtdQuality, "Trip Update");
            
            // Quality assertions
            assertThat(tisQuality.overallQualityScore).isGreaterThan(70.0);
            assertThat(rtdQuality.overallQualityScore).isGreaterThan(70.0);
        }
    }
    
    @Nested
    @DisplayName("Alert Quality Comparison")
    class AlertQualityComparison {
        
        @Test
        @DisplayName("Compare Alert data quality between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareAlertQuality() {
            LOG.info("Comparing Alert data quality...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_ALERT_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_ALERT_URL);
            
            // TIS Producer doesn't have Alerts endpoint, so skip comparison
            if (tisFeed == null) {
                LOG.info("TIS Producer Alerts endpoint not available - skipping comparison");
                LOG.info("RTD Production Alerts Quality Analysis:");
                AlertQualityResult rtdQuality = analyzeAlertQuality(rtdFeed, "RTD Production");
                LOG.info("RTD Production Quality Score: {:.1f}%", rtdQuality.overallQualityScore);
                return;
            }
            
            assertThat(rtdFeed).isNotNull();
            
            AlertQualityResult tisQuality = analyzeAlertQuality(tisFeed, "TIS Producer");
            AlertQualityResult rtdQuality = analyzeAlertQuality(rtdFeed, "RTD Production");
            
            LOG.info("=== Alert Quality Comparison ===");
            LOG.info("TIS Producer Quality Score: {:.1f}%", tisQuality.overallQualityScore);
            LOG.info("RTD Production Quality Score: {:.1f}%", rtdQuality.overallQualityScore);
            
            // Compare quality metrics
            compareQualityMetrics(tisQuality, rtdQuality, "Alerts");
            
            // Quality assertions
            assertThat(rtdQuality.overallQualityScore).isGreaterThan(70.0);
        }
    }
    
    @Nested
    @DisplayName("Performance Comparison")
    class PerformanceComparison {
        
        @Test
        @DisplayName("Compare endpoint performance between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 3, unit = TimeUnit.SECONDS)
        void compareEndpointPerformance() {
            LOG.info("Comparing endpoint performance...");
            
            // TIS Producer only has Vehicle and Trip endpoints
            PerformanceResult tisPerformance = measurePerformance(TIS_VEHICLE_URL, TIS_TRIP_URL, null, "TIS Producer");
            PerformanceResult rtdPerformance = measurePerformance(RTD_VEHICLE_URL, RTD_TRIP_URL, RTD_ALERT_URL, "RTD Production");
            
            LOG.info("=== Performance Comparison ===");
            LOG.info("TIS Producer Average Response Time: {}ms", tisPerformance.averageResponseTime);
            LOG.info("RTD Production Average Response Time: {}ms", rtdPerformance.averageResponseTime);
            LOG.info("TIS Producer Total Data Size: {} bytes", tisPerformance.totalDataSize);
            LOG.info("RTD Production Total Data Size: {} bytes", rtdPerformance.totalDataSize);
            
            // Performance assertions
            assertThat(tisPerformance.averageResponseTime).isLessThan(5000); // 5 seconds
            assertThat(rtdPerformance.averageResponseTime).isLessThan(5000); // 5 seconds
        }
    }
    
    // Helper methods for quality analysis
    
    private VehiclePositionQualityResult analyzeVehiclePositionQuality(FeedMessage feed, String source) {
        VehiclePositionQualityResult result = new VehiclePositionQualityResult();
        result.source = source;
        result.totalEntities = feed.getEntityCount();
        
        int validEntities = 0;
        int entitiesWithCoordinates = 0;
        int entitiesWithValidCoordinates = 0;
        int entitiesWithTimestamp = 0;
        int entitiesWithFreshTimestamp = 0;
        int entitiesWithVehicleInfo = 0;
        int entitiesWithTripInfo = 0;
        
        long currentTime = Instant.now().getEpochSecond();
        
        for (int i = 0; i < feed.getEntityCount(); i++) {
            FeedEntity entity = feed.getEntity(i);
            if (entity.hasVehicle()) {
                validEntities++;
                VehiclePosition vehicle = entity.getVehicle();
                
                // Check coordinates
                if (vehicle.hasPosition()) {
                    entitiesWithCoordinates++;
                    Position position = vehicle.getPosition();
                    if (position.getLatitude() >= -90 && position.getLatitude() <= 90 &&
                        position.getLongitude() >= -180 && position.getLongitude() <= 180) {
                        entitiesWithValidCoordinates++;
                    }
                }
                
                // Check timestamp
                if (vehicle.hasTimestamp()) {
                    entitiesWithTimestamp++;
                    long timestamp = vehicle.getTimestamp();
                    if (Math.abs(currentTime - timestamp) < 3600) { // Within 1 hour
                        entitiesWithFreshTimestamp++;
                    }
                }
                
                // Check vehicle info
                if (vehicle.hasVehicle()) {
                    entitiesWithVehicleInfo++;
                }
                
                // Check trip info
                if (vehicle.hasTrip()) {
                    entitiesWithTripInfo++;
                }
            }
        }
        
        // Calculate quality scores
        result.coordinateQuality = (double) entitiesWithValidCoordinates / result.totalEntities * 100;
        result.timestampQuality = (double) entitiesWithFreshTimestamp / result.totalEntities * 100;
        result.vehicleInfoQuality = (double) entitiesWithVehicleInfo / result.totalEntities * 100;
        result.tripInfoQuality = (double) entitiesWithTripInfo / result.totalEntities * 100;
        
        // Overall quality score (weighted average)
        result.overallQualityScore = (result.coordinateQuality * 0.4 + 
                                     result.timestampQuality * 0.3 + 
                                     result.vehicleInfoQuality * 0.2 + 
                                     result.tripInfoQuality * 0.1);
        
        LOG.info("{} Vehicle Position Quality Analysis:", source);
        LOG.info("  Total Entities: {}", result.totalEntities);
        LOG.info("  Valid Coordinates: {:.1f}%", result.coordinateQuality);
        LOG.info("  Fresh Timestamps: {:.1f}%", result.timestampQuality);
        LOG.info("  Vehicle Info: {:.1f}%", result.vehicleInfoQuality);
        LOG.info("  Trip Info: {:.1f}%", result.tripInfoQuality);
        LOG.info("  Overall Quality: {:.1f}%", result.overallQualityScore);
        
        return result;
    }
    
    private TripUpdateQualityResult analyzeTripUpdateQuality(FeedMessage feed, String source) {
        TripUpdateQualityResult result = new TripUpdateQualityResult();
        result.source = source;
        result.totalEntities = feed.getEntityCount();
        
        int validEntities = 0;
        int entitiesWithTripInfo = 0;
        int entitiesWithStopUpdates = 0;
        int entitiesWithValidDelays = 0;
        
        for (int i = 0; i < feed.getEntityCount(); i++) {
            FeedEntity entity = feed.getEntity(i);
            if (entity.hasTripUpdate()) {
                validEntities++;
                TripUpdate tripUpdate = entity.getTripUpdate();
                
                // Check trip info
                if (tripUpdate.hasTrip()) {
                    entitiesWithTripInfo++;
                }
                
                // Check stop updates
                if (tripUpdate.getStopTimeUpdateCount() > 0) {
                    entitiesWithStopUpdates++;
                }
                
                // Check delays
                boolean hasValidDelays = false;
                for (int j = 0; j < tripUpdate.getStopTimeUpdateCount(); j++) {
                    TripUpdate.StopTimeUpdate stopUpdate = tripUpdate.getStopTimeUpdate(j);
                    if (stopUpdate.hasArrival() || stopUpdate.hasDeparture()) {
                        hasValidDelays = true;
                        break;
                    }
                }
                if (hasValidDelays) {
                    entitiesWithValidDelays++;
                }
            }
        }
        
        // Calculate quality scores
        result.tripInfoQuality = (double) entitiesWithTripInfo / result.totalEntities * 100;
        result.stopUpdateQuality = (double) entitiesWithStopUpdates / result.totalEntities * 100;
        result.delayQuality = (double) entitiesWithValidDelays / result.totalEntities * 100;
        
        // Overall quality score
        result.overallQualityScore = (result.tripInfoQuality * 0.4 + 
                                     result.stopUpdateQuality * 0.4 + 
                                     result.delayQuality * 0.2);
        
        LOG.info("{} Trip Update Quality Analysis:", source);
        LOG.info("  Total Entities: {}", result.totalEntities);
        LOG.info("  Trip Info: {:.1f}%", result.tripInfoQuality);
        LOG.info("  Stop Updates: {:.1f}%", result.stopUpdateQuality);
        LOG.info("  Valid Delays: {:.1f}%", result.delayQuality);
        LOG.info("  Overall Quality: {:.1f}%", result.overallQualityScore);
        
        return result;
    }
    
    private AlertQualityResult analyzeAlertQuality(FeedMessage feed, String source) {
        AlertQualityResult result = new AlertQualityResult();
        result.source = source;
        result.totalEntities = feed.getEntityCount();
        
        int validEntities = 0;
        int entitiesWithText = 0;
        int entitiesWithActivePeriod = 0;
        int entitiesWithValidCause = 0;
        
        for (int i = 0; i < feed.getEntityCount(); i++) {
            FeedEntity entity = feed.getEntity(i);
            if (entity.hasAlert()) {
                validEntities++;
                Alert alert = entity.getAlert();
                
                // Check text content - simplified validation
                if (alert.hasHeaderText() || alert.hasDescriptionText()) {
                    entitiesWithText++;
                }
                
                // Check active period
                if (alert.getActivePeriodCount() > 0) {
                    entitiesWithActivePeriod++;
                }
                
                // Check cause
                if (alert.hasCause() && !alert.getCause().name().equals("UNKNOWN_CAUSE")) {
                    entitiesWithValidCause++;
                }
            }
        }
        
        // Calculate quality scores
        result.textQuality = (double) entitiesWithText / result.totalEntities * 100;
        result.activePeriodQuality = (double) entitiesWithActivePeriod / result.totalEntities * 100;
        result.causeQuality = (double) entitiesWithValidCause / result.totalEntities * 100;
        
        // Overall quality score
        result.overallQualityScore = (result.textQuality * 0.5 + 
                                     result.activePeriodQuality * 0.3 + 
                                     result.causeQuality * 0.2);
        
        LOG.info("{} Alert Quality Analysis:", source);
        LOG.info("  Total Entities: {}", result.totalEntities);
        LOG.info("  Text Content: {:.1f}%", result.textQuality);
        LOG.info("  Active Period: {:.1f}%", result.activePeriodQuality);
        LOG.info("  Valid Cause: {:.1f}%", result.causeQuality);
        LOG.info("  Overall Quality: {:.1f}%", result.overallQualityScore);
        
        return result;
    }
    
    private CompletenessResult analyzeCompleteness(FeedMessage feed, String source) {
        CompletenessResult result = new CompletenessResult();
        result.source = source;
        result.totalEntities = feed.getEntityCount();
        
        int completeEntities = 0;
        
        for (int i = 0; i < feed.getEntityCount(); i++) {
            FeedEntity entity = feed.getEntity(i);
            
            // Check if entity has required fields based on type
            if (entity.hasVehicle()) {
                VehiclePosition vehicle = entity.getVehicle();
                if (vehicle.hasPosition() && vehicle.hasTimestamp()) {
                    completeEntities++;
                }
            } else if (entity.hasTripUpdate()) {
                TripUpdate tripUpdate = entity.getTripUpdate();
                if (tripUpdate.hasTrip() && tripUpdate.getStopTimeUpdateCount() > 0) {
                    completeEntities++;
                }
            } else if (entity.hasAlert()) {
                Alert alert = entity.getAlert();
                if (alert.hasHeaderText() || alert.hasDescriptionText()) {
                    completeEntities++;
                }
            }
        }
        
        result.overallCompleteness = (double) completeEntities / result.totalEntities * 100;
        
        LOG.info("{} Completeness Analysis:", source);
        LOG.info("  Total Entities: {}", result.totalEntities);
        LOG.info("  Complete Entities: {}", completeEntities);
        LOG.info("  Overall Completeness: {:.1f}%", result.overallCompleteness);
        
        return result;
    }
    
    private AccuracyResult analyzeAccuracy(FeedMessage feed, String source) {
        AccuracyResult result = new AccuracyResult();
        result.source = source;
        result.totalEntities = feed.getEntityCount();
        
        int accurateEntities = 0;
        
        for (int i = 0; i < feed.getEntityCount(); i++) {
            FeedEntity entity = feed.getEntity(i);
            
            if (entity.hasVehicle()) {
                VehiclePosition vehicle = entity.getVehicle();
                if (vehicle.hasPosition()) {
                    Position position = vehicle.getPosition();
                    // Check coordinate accuracy
                    if (position.getLatitude() >= -90 && position.getLatitude() <= 90 &&
                        position.getLongitude() >= -180 && position.getLongitude() <= 180) {
                        accurateEntities++;
                    }
                }
            } else if (entity.hasTripUpdate()) {
                TripUpdate tripUpdate = entity.getTripUpdate();
                if (tripUpdate.hasTrip()) {
                    TripDescriptor trip = tripUpdate.getTrip();
                    // Check trip ID format
                    if (trip.hasTripId() && !trip.getTripId().isEmpty()) {
                        accurateEntities++;
                    }
                }
            } else if (entity.hasAlert()) {
                Alert alert = entity.getAlert();
                // Check alert validity
                if (alert.hasHeaderText() || alert.hasDescriptionText()) {
                    accurateEntities++;
                }
            }
        }
        
        result.overallAccuracy = (double) accurateEntities / result.totalEntities * 100;
        
        LOG.info("{} Accuracy Analysis:", source);
        LOG.info("  Total Entities: {}", result.totalEntities);
        LOG.info("  Accurate Entities: {}", accurateEntities);
        LOG.info("  Overall Accuracy: {:.1f}%", result.overallAccuracy);
        
        return result;
    }
    
    private PerformanceResult measurePerformance(String vehicleUrl, String tripUrl, String alertUrl, String source) {
        PerformanceResult result = new PerformanceResult();
        result.source = source;
        
        long totalResponseTime = 0;
        long totalDataSize = 0;
        int successfulRequests = 0;
        
        List<String> urls = new ArrayList<>();
        urls.add(vehicleUrl);
        urls.add(tripUrl);
        if (alertUrl != null) {
            urls.add(alertUrl);
        }
        
        for (String url : urls) {
            long startTime = System.currentTimeMillis();
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                HttpResponse response = httpClient.execute(request);
                
                if (response.getStatusLine().getStatusCode() == 200) {
                    byte[] data = EntityUtils.toByteArray(response.getEntity());
                    long endTime = System.currentTimeMillis();
                    
                    totalResponseTime += (endTime - startTime);
                    totalDataSize += data.length;
                    successfulRequests++;
                }
            } catch (Exception e) {
                LOG.warn("Failed to measure performance for {}: {}", url, e.getMessage());
            }
        }
        
        if (successfulRequests > 0) {
            result.averageResponseTime = totalResponseTime / successfulRequests;
            result.totalDataSize = totalDataSize;
            result.successfulRequests = successfulRequests;
        }
        
        LOG.info("{} Performance Analysis:", source);
        LOG.info("  Successful Requests: {}", result.successfulRequests);
        LOG.info("  Average Response Time: {}ms", result.averageResponseTime);
        LOG.info("  Total Data Size: {} bytes", result.totalDataSize);
        
        return result;
    }
    
    private void compareQualityMetrics(Object tisQuality, Object rtdQuality, String feedType) {
        LOG.info("=== {} Quality Comparison Summary ===", feedType);
        
        if (tisQuality instanceof VehiclePositionQualityResult && rtdQuality instanceof VehiclePositionQualityResult) {
            VehiclePositionQualityResult tis = (VehiclePositionQualityResult) tisQuality;
            VehiclePositionQualityResult rtd = (VehiclePositionQualityResult) rtdQuality;
            
            LOG.info("Overall Quality: TIS {:.1f}% vs RTD {:.1f}% (Diff: {:.1f}%)", 
                    tis.overallQualityScore, rtd.overallQualityScore, 
                    tis.overallQualityScore - rtd.overallQualityScore);
            LOG.info("Coordinate Quality: TIS {:.1f}% vs RTD {:.1f}%", 
                    tis.coordinateQuality, rtd.coordinateQuality);
            LOG.info("Timestamp Quality: TIS {:.1f}% vs RTD {:.1f}%", 
                    tis.timestampQuality, rtd.timestampQuality);
        }
    }
    
    private FeedMessage fetchGTFSRTFeed(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "RTD-GTFS-RT-Quality-Test/1.0");
            request.setHeader("Accept", "application/x-protobuf");
            
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 200) {
                byte[] data = EntityUtils.toByteArray(response.getEntity());
                LOG.info("Downloaded {} bytes from {}", data.length, url);
                
                return FeedMessage.parseFrom(new ByteArrayInputStream(data));
            } else {
                LOG.error("HTTP error {} for {}", statusCode, url);
                return null;
            }
        } catch (Exception e) {
            LOG.error("Error fetching {}: {}", url, e.getMessage());
            return null;
        }
    }
    
    // Result classes
    static class VehiclePositionQualityResult {
        String source;
        int totalEntities;
        double coordinateQuality;
        double timestampQuality;
        double vehicleInfoQuality;
        double tripInfoQuality;
        double overallQualityScore;
    }
    
    static class TripUpdateQualityResult {
        String source;
        int totalEntities;
        double tripInfoQuality;
        double stopUpdateQuality;
        double delayQuality;
        double overallQualityScore;
    }
    
    static class AlertQualityResult {
        String source;
        int totalEntities;
        double textQuality;
        double activePeriodQuality;
        double causeQuality;
        double overallQualityScore;
    }
    
    static class CompletenessResult {
        String source;
        int totalEntities;
        double overallCompleteness;
    }
    
    static class AccuracyResult {
        String source;
        int totalEntities;
        double overallAccuracy;
    }
    
    static class PerformanceResult {
        String source;
        long averageResponseTime;
        long totalDataSize;
        int successfulRequests;
    }
}
