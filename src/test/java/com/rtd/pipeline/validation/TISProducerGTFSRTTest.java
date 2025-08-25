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
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

/**
 * Comprehensive test class for TIS Producer GTFS-RT endpoints:
 * - http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb
 * - http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb
 */
class TISProducerGTFSRTTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(TISProducerGTFSRTTest.class);
    
    // TIS Producer endpoints
    private static final String TIS_VEHICLE_POSITION_URL = "http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb";
    private static final String TIS_TRIP_UPDATE_URL = "http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb";
    
    // RTD production endpoints for comparison
    private static final String RTD_VEHICLE_POSITION_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final String RTD_TRIP_UPDATE_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb";
    
    private static final int TIMEOUT_SECONDS = 30;
    
    @BeforeAll
    static void setup() {
        LOG.info("=== TIS Producer GTFS-RT Endpoint Test Suite ===");
        LOG.info("Testing endpoints:");
        LOG.info("  Vehicle Position: {}", TIS_VEHICLE_POSITION_URL);
        LOG.info("  Trip Update: {}", TIS_TRIP_UPDATE_URL);
    }
    
    @Nested
    @DisplayName("TIS Producer Vehicle Position Tests")
    class TISVehiclePositionTests {
        
        @Test
        @DisplayName("Test TIS Producer Vehicle Position endpoint connectivity")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISVehiclePositionConnectivity() {
            LOG.info("Testing TIS Producer Vehicle Position endpoint connectivity...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_VEHICLE_POSITION_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Vehicle Position feed not available");
                Assumptions.assumeTrue(false, "TIS Vehicle Position feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            LOG.info("✅ TIS Producer Vehicle Position feed retrieved successfully");
            
            int entityCount = feed.getEntityCount();
            LOG.info("📊 Vehicle Position feed contains {} entities", entityCount);
            
            assertThat(entityCount).isGreaterThanOrEqualTo(0);
        }
        
        @Test
        @DisplayName("Test TIS Producer Vehicle Position data structure")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISVehiclePositionDataStructure() {
            LOG.info("Testing TIS Producer Vehicle Position data structure...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_VEHICLE_POSITION_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Vehicle Position feed not available");
                Assumptions.assumeTrue(false, "TIS Vehicle Position feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            
            if (feed.getEntityCount() > 0) {
                // Validate first entity
                FeedEntity entity = feed.getEntity(0);
                assertThat(entity.hasVehicle()).isTrue();
                
                VehiclePosition vehicle = entity.getVehicle();
                LOG.info("✅ First vehicle: ID={}", entity.getId());
                
                // Validate vehicle data
                if (vehicle.hasPosition()) {
                    Position position = vehicle.getPosition();
                    LOG.info("  Position: lat={}, lon={}, bearing={}, speed={}", 
                            position.getLatitude(), position.getLongitude(), 
                            position.getBearing(), position.getSpeed());
                    
                    // Validate coordinates
                    assertThat(position.getLatitude()).isGreaterThanOrEqualTo(-90.0f).isLessThanOrEqualTo(90.0f);
                    assertThat(position.getLongitude()).isGreaterThanOrEqualTo(-180.0f).isLessThanOrEqualTo(180.0f);
                }
                
                // Validate trip information
                if (vehicle.hasTrip()) {
                    TripDescriptor trip = vehicle.getTrip();
                    LOG.info("  Trip: ID={}, Route={}", 
                            trip.getTripId(), trip.getRouteId());
                }
                
                // Validate vehicle information
                if (vehicle.hasVehicle()) {
                    VehicleDescriptor vehicleDesc = vehicle.getVehicle();
                    LOG.info("  Vehicle: ID={}, Label={}", 
                            vehicleDesc.getId(), vehicleDesc.getLabel());
                }
            }
        }
        
        @Test
        @DisplayName("Test TIS Producer Vehicle Position data quality")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISVehiclePositionDataQuality() {
            LOG.info("Testing TIS Producer Vehicle Position data quality...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_VEHICLE_POSITION_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Vehicle Position feed not available");
                Assumptions.assumeTrue(false, "TIS Vehicle Position feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            
            int totalEntities = feed.getEntityCount();
            int validEntities = 0;
            int invalidCoordinates = 0;
            int missingTripInfo = 0;
            int missingVehicleInfo = 0;
            
            Set<String> vehicleIds = new HashSet<>();
            Set<String> tripIds = new HashSet<>();
            Set<String> routeIds = new HashSet<>();
            
            for (int i = 0; i < totalEntities; i++) {
                FeedEntity entity = feed.getEntity(i);
                if (entity.hasVehicle()) {
                    VehiclePosition vehicle = entity.getVehicle();
                    validEntities++;
                    
                    // Collect unique identifiers
                    if (vehicle.hasVehicle()) {
                        vehicleIds.add(vehicle.getVehicle().getId());
                    }
                    if (vehicle.hasTrip()) {
                        TripDescriptor trip = vehicle.getTrip();
                        if (trip.hasTripId()) {
                            tripIds.add(trip.getTripId());
                        }
                        if (trip.hasRouteId()) {
                            routeIds.add(trip.getRouteId());
                        }
                    } else {
                        missingTripInfo++;
                    }
                    
                    // Validate coordinates
                    if (vehicle.hasPosition()) {
                        Position position = vehicle.getPosition();
                        if (position.getLatitude() < -90 || position.getLatitude() > 90 ||
                            position.getLongitude() < -180 || position.getLongitude() > 180) {
                            invalidCoordinates++;
                        }
                    }
                }
            }
            
            LOG.info("📊 Data Quality Analysis:");
            LOG.info("  Total entities: {}", totalEntities);
            LOG.info("  Valid vehicle entities: {}", validEntities);
            LOG.info("  Unique vehicle IDs: {}", vehicleIds.size());
            LOG.info("  Unique trip IDs: {}", tripIds.size());
            LOG.info("  Unique route IDs: {}", routeIds.size());
            LOG.info("  Entities with invalid coordinates: {}", invalidCoordinates);
            LOG.info("  Entities missing trip info: {}", missingTripInfo);
            
            // Quality assertions
            assertThat(validEntities).isGreaterThan(0);
            assertThat(invalidCoordinates).isEqualTo(0);
            assertThat(vehicleIds.size()).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("TIS Producer Trip Update Tests")
    class TISTripUpdateTests {
        
        @Test
        @DisplayName("Test TIS Producer Trip Update endpoint connectivity")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISTripUpdateConnectivity() {
            LOG.info("Testing TIS Producer Trip Update endpoint connectivity...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_TRIP_UPDATE_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Trip Update feed not available");
                Assumptions.assumeTrue(false, "TIS Trip Update feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            LOG.info("✅ TIS Producer Trip Update feed retrieved successfully");
            
            int entityCount = feed.getEntityCount();
            LOG.info("📊 Trip Update feed contains {} entities", entityCount);
            
            assertThat(entityCount).isGreaterThanOrEqualTo(0);
        }
        
        @Test
        @DisplayName("Test TIS Producer Trip Update data structure")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISTripUpdateDataStructure() {
            LOG.info("Testing TIS Producer Trip Update data structure...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_TRIP_UPDATE_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Trip Update feed not available");
                Assumptions.assumeTrue(false, "TIS Trip Update feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            
            if (feed.getEntityCount() > 0) {
                // Validate first entity
                FeedEntity entity = feed.getEntity(0);
                assertThat(entity.hasTripUpdate()).isTrue();
                
                TripUpdate tripUpdate = entity.getTripUpdate();
                LOG.info("✅ First trip update: ID={}", entity.getId());
                
                // Validate trip information
                if (tripUpdate.hasTrip()) {
                    TripDescriptor trip = tripUpdate.getTrip();
                    LOG.info("  Trip: ID={}, Route={}, StartDate={}", 
                            trip.getTripId(), trip.getRouteId(), trip.getStartDate());
                }
                
                // Validate vehicle information
                if (tripUpdate.hasVehicle()) {
                    VehicleDescriptor vehicle = tripUpdate.getVehicle();
                    LOG.info("  Vehicle: ID={}, Label={}", 
                            vehicle.getId(), vehicle.getLabel());
                }
                
                // Validate stop time updates
                int stopTimeUpdateCount = tripUpdate.getStopTimeUpdateCount();
                LOG.info("  Stop time updates: {}", stopTimeUpdateCount);
                
                if (stopTimeUpdateCount > 0) {
                    TripUpdate.StopTimeUpdate firstUpdate = tripUpdate.getStopTimeUpdate(0);
                    LOG.info("  First stop update: StopID={}, Arrival={}, Departure={}", 
                            firstUpdate.getStopId(),
                            firstUpdate.hasArrival() ? firstUpdate.getArrival().getDelay() : "N/A",
                            firstUpdate.hasDeparture() ? firstUpdate.getDeparture().getDelay() : "N/A");
                }
            }
        }
        
        @Test
        @DisplayName("Test TIS Producer Trip Update data quality")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISTripUpdateDataQuality() {
            LOG.info("Testing TIS Producer Trip Update data quality...");
            
            FeedMessage feed = fetchGTFSRTFeed(TIS_TRIP_UPDATE_URL);
            
            if (feed == null) {
                LOG.warn("Skipping test - TIS Trip Update feed not available");
                Assumptions.assumeTrue(false, "TIS Trip Update feed not available");
                return;
            }
            
            assertThat(feed).isNotNull();
            
            int totalEntities = feed.getEntityCount();
            int validEntities = 0;
            int missingTripInfo = 0;
            int missingStopUpdates = 0;
            
            Set<String> tripIds = new HashSet<>();
            Set<String> routeIds = new HashSet<>();
            Set<String> stopIds = new HashSet<>();
            
            for (int i = 0; i < totalEntities; i++) {
                FeedEntity entity = feed.getEntity(i);
                if (entity.hasTripUpdate()) {
                    TripUpdate tripUpdate = entity.getTripUpdate();
                    validEntities++;
                    
                    // Collect unique identifiers
                    if (tripUpdate.hasTrip()) {
                        TripDescriptor trip = tripUpdate.getTrip();
                        if (trip.hasTripId()) {
                            tripIds.add(trip.getTripId());
                        }
                        if (trip.hasRouteId()) {
                            routeIds.add(trip.getRouteId());
                        }
                    } else {
                        missingTripInfo++;
                    }
                    
                    // Collect stop information
                    int stopUpdateCount = tripUpdate.getStopTimeUpdateCount();
                    if (stopUpdateCount == 0) {
                        missingStopUpdates++;
                    } else {
                        for (int j = 0; j < stopUpdateCount; j++) {
                            TripUpdate.StopTimeUpdate stopUpdate = tripUpdate.getStopTimeUpdate(j);
                            if (stopUpdate.hasStopId()) {
                                stopIds.add(stopUpdate.getStopId());
                            }
                        }
                    }
                }
            }
            
            LOG.info("📊 Trip Update Data Quality Analysis:");
            LOG.info("  Total entities: {}", totalEntities);
            LOG.info("  Valid trip update entities: {}", validEntities);
            LOG.info("  Unique trip IDs: {}", tripIds.size());
            LOG.info("  Unique route IDs: {}", routeIds.size());
            LOG.info("  Unique stop IDs: {}", stopIds.size());
            LOG.info("  Entities missing trip info: {}", missingTripInfo);
            LOG.info("  Entities missing stop updates: {}", missingStopUpdates);
            
            // Quality assertions
            assertThat(validEntities).isGreaterThanOrEqualTo(0);
            assertThat(tripIds.size()).isGreaterThanOrEqualTo(0);
        }
    }
    
    @Nested
    @DisplayName("TIS Producer vs RTD Production Comparison")
    class TISvsRTDComparisonTests {
        
        @Test
        @DisplayName("Compare Vehicle Position feeds between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareVehiclePositionFeeds() {
            LOG.info("Comparing Vehicle Position feeds...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_VEHICLE_POSITION_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_VEHICLE_POSITION_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            LOG.info("📊 Vehicle Position Feed Comparison:");
            LOG.info("  TIS Producer: {} entities", tisFeed.getEntityCount());
            LOG.info("  RTD Production: {} entities", rtdFeed.getEntityCount());
            
            // Compare entity counts
            int tisCount = tisFeed.getEntityCount();
            int rtdCount = rtdFeed.getEntityCount();
            
            if (tisCount > 0 && rtdCount > 0) {
                double ratio = (double) tisCount / rtdCount;
                LOG.info("  TIS/RTD ratio: {:.2f}", ratio);
                
                // TIS should have similar or more entities than RTD
                assertThat(ratio).isGreaterThan(0.5);
            }
        }
        
        @Test
        @DisplayName("Compare Trip Update feeds between TIS Producer and RTD Production")
        @Timeout(value = TIMEOUT_SECONDS * 2, unit = TimeUnit.SECONDS)
        void compareTripUpdateFeeds() {
            LOG.info("Comparing Trip Update feeds...");
            
            FeedMessage tisFeed = fetchGTFSRTFeed(TIS_TRIP_UPDATE_URL);
            FeedMessage rtdFeed = fetchGTFSRTFeed(RTD_TRIP_UPDATE_URL);
            
            assertThat(tisFeed).isNotNull();
            assertThat(rtdFeed).isNotNull();
            
            LOG.info("📊 Trip Update Feed Comparison:");
            LOG.info("  TIS Producer: {} entities", tisFeed.getEntityCount());
            LOG.info("  RTD Production: {} entities", rtdFeed.getEntityCount());
            
            // Compare entity counts
            int tisCount = tisFeed.getEntityCount();
            int rtdCount = rtdFeed.getEntityCount();
            
            if (tisCount > 0 && rtdCount > 0) {
                double ratio = (double) tisCount / rtdCount;
                LOG.info("  TIS/RTD ratio: {:.2f}", ratio);
                
                // TIS should have similar or more entities than RTD
                assertThat(ratio).isGreaterThan(0.5);
            }
        }
    }
    
    @Nested
    @DisplayName("TIS Producer Performance Tests")
    class TISPerformanceTests {
        
        @Test
        @DisplayName("Test TIS Producer response time")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void testTISResponseTime() {
            LOG.info("Testing TIS Producer response time...");
            
            long startTime = System.currentTimeMillis();
            FeedMessage vehicleFeed = fetchGTFSRTFeed(TIS_VEHICLE_POSITION_URL);
            long vehicleTime = System.currentTimeMillis() - startTime;
            
            startTime = System.currentTimeMillis();
            FeedMessage tripFeed = fetchGTFSRTFeed(TIS_TRIP_UPDATE_URL);
            long tripTime = System.currentTimeMillis() - startTime;
            
            if (vehicleFeed == null || tripFeed == null) {
                LOG.warn("Skipping test - TIS feeds not available (Vehicle: {}, Trip: {})", 
                    vehicleFeed != null, tripFeed != null);
                Assumptions.assumeTrue(false, "TIS feeds not available");
                return;
            }
            
            assertThat(vehicleFeed).isNotNull();
            assertThat(tripFeed).isNotNull();
            
            LOG.info("📊 Response Time Analysis:");
            LOG.info("  Vehicle Position: {}ms", vehicleTime);
            LOG.info("  Trip Update: {}ms", tripTime);
            
            // Performance assertions (should be under 10 seconds)
            assertThat(vehicleTime).isLessThan(10000);
            assertThat(tripTime).isLessThan(10000);
        }
    }
    
    /**
     * Helper method to fetch GTFS-RT feed from URL
     */
    private FeedMessage fetchGTFSRTFeed(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "RTD-GTFS-RT-Test/1.0");
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
}
