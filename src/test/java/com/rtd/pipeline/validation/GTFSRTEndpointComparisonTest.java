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
 * Comprehensive test class comparing RTD GTFS-RT URLs:
 * Current production URLs vs new open-data URLs
 */
class GTFSRTEndpointComparisonTest {

    // Current production URLs (all using nodejs-prod API endpoint)
    private static final String CURRENT_VEHICLE_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final String CURRENT_TRIP_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb";
    private static final String CURRENT_ALERT_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb";
    
    // New open-data URLs with /rtd/ path
    private static final String NEW_VEHICLE_URL = "https://open-data.rtd-denver.com/files/prod/gtfs-rt/rtd/VehiclePosition.pb";
    private static final String NEW_TRIP_URL = "https://open-data.rtd-denver.com/files/prod/gtfs-rt/rtd/TripUpdate.pb";
    private static final String NEW_ALERT_URL = "https://open-data.rtd-denver.com/files/prod/gtfs-rt/rtd/Alerts.pb";

    private static final int TIMEOUT_SECONDS = 30;

    @Nested
    @DisplayName("Vehicle Position Endpoint Comparison")
    class VehiclePositionComparison {

        @Test
        @DisplayName("Compare vehicle position endpoint availability")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareVehiclePositionAvailability() {
            EndpointComparison comparison = compareEndpoints(CURRENT_VEHICLE_URL, NEW_VEHICLE_URL);
            
            System.out.println("=== Vehicle Position Endpoint Comparison ===");
            System.out.println("Current URL: " + CURRENT_VEHICLE_URL);
            System.out.println("New URL: " + NEW_VEHICLE_URL);
            System.out.println();
            
            printComparisonResults(comparison);
            
            // Current endpoint should be available (required for operations)
            assertThat(comparison.currentEndpoint.isAvailable).isTrue()
                .as("Current vehicle position endpoint should be available");
            
            // New endpoint availability check (may not exist yet)
            if (!comparison.newEndpoint.isAvailable) {
                System.out.println("NOTE: New vehicle position endpoint is not available yet (returns 404)");
                System.out.println("This suggests the open-data URLs may not be deployed yet.");
            } else {
                System.out.println("SUCCESS: New vehicle position endpoint is available!");
            }
        }

        @Test
        @DisplayName("Compare vehicle position data structure")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareVehiclePositionDataStructure() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_VEHICLE_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_VEHICLE_URL);
            
            assertThat(currentFeed).isNotNull().as("Current vehicle feed should be parseable");
            
            if (newFeed == null) {
                System.out.println("NOTE: Cannot compare data structures - new vehicle feed is not available");
                System.out.println("Current feed has " + currentFeed.getEntityCount() + " vehicle positions");
                return; // Skip comparison but don't fail the test
            }
            
            DataStructureComparison comparison = compareDataStructures(currentFeed, newFeed, "VehiclePosition");
            printDataStructureComparison(comparison);
            
            // Basic structure validation
            assertThat(comparison.entityCountDifference).isLessThan(50)
                .as("Entity count difference should be reasonable");
        }

        @Test
        @DisplayName("Compare vehicle position data quality")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareVehiclePositionDataQuality() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_VEHICLE_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_VEHICLE_URL);
            
            assertThat(currentFeed).isNotNull();
            
            DataQualityMetrics currentMetrics = analyzeVehiclePositionQuality(currentFeed);
            
            System.out.println("=== Vehicle Position Data Quality Analysis ===");
            System.out.println("Current Feed Quality:");
            printDataQualityMetrics(currentMetrics);
            
            if (newFeed == null) {
                System.out.println("\nNOTE: Cannot analyze new feed quality - endpoint not available");
            } else {
                DataQualityMetrics newMetrics = analyzeVehiclePositionQuality(newFeed);
                printDataQualityComparison(currentMetrics, newMetrics);
                // Quality assertions - both feeds should meet minimum standards
                assertThat(newMetrics.validEntityPercentage).isGreaterThan(0.8);
            }
            
            // Current feed should always meet quality standards
            assertThat(currentMetrics.validEntityPercentage).isGreaterThan(0.8);
        }
    }

    @Nested
    @DisplayName("Trip Update Endpoint Comparison")
    class TripUpdateComparison {

        @Test
        @DisplayName("Compare trip update endpoint availability")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareTripUpdateAvailability() {
            EndpointComparison comparison = compareEndpoints(CURRENT_TRIP_URL, NEW_TRIP_URL);
            
            System.out.println("=== Trip Update Endpoint Comparison ===");
            System.out.println("Current URL: " + CURRENT_TRIP_URL);
            System.out.println("New URL: " + NEW_TRIP_URL);
            System.out.println();
            
            printComparisonResults(comparison);
            
            assertThat(comparison.currentEndpoint.isAvailable).isTrue()
                .as("Current trip update endpoint should be available");
            
            if (!comparison.newEndpoint.isAvailable) {
                System.out.println("NOTE: New trip update endpoint is not available yet (returns 404)");
                System.out.println("This suggests the open-data URLs may not be deployed yet.");
            } else {
                System.out.println("SUCCESS: New trip update endpoint is available!");
            }
        }

        @Test
        @DisplayName("Compare trip update data structure")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareTripUpdateDataStructure() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_TRIP_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_TRIP_URL);
            
            assertThat(currentFeed).isNotNull().as("Current trip feed should be parseable");
            
            if (newFeed == null) {
                System.out.println("NOTE: Cannot compare data structures - new trip feed is not available");
                System.out.println("Current feed has " + currentFeed.getEntityCount() + " trip updates");
                return;
            }
            
            DataStructureComparison comparison = compareDataStructures(currentFeed, newFeed, "TripUpdate");
            printDataStructureComparison(comparison);
            
            assertThat(comparison.entityCountDifference).isLessThan(100)
                .as("Trip update entity count difference should be reasonable");
        }
    }

    @Nested
    @DisplayName("Alert Endpoint Comparison")
    class AlertComparison {

        @Test
        @DisplayName("Compare alert endpoint availability")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareAlertAvailability() {
            EndpointComparison comparison = compareEndpoints(CURRENT_ALERT_URL, NEW_ALERT_URL);
            
            System.out.println("=== Alert Endpoint Comparison ===");
            System.out.println("Current URL: " + CURRENT_ALERT_URL);
            System.out.println("New URL: " + NEW_ALERT_URL);
            System.out.println();
            
            printComparisonResults(comparison);
            
            // Note: Alert endpoint may not be currently available in production
            if (!comparison.currentEndpoint.isAvailable) {
                System.out.println("NOTE: Current alert endpoint is not available (returns 404)");
                System.out.println("This may indicate alerts are not currently active or endpoint is down.");
            } else {
                assertThat(comparison.currentEndpoint.isAvailable).isTrue()
                    .as("Current alert endpoint should be available");
            }
            
            if (!comparison.newEndpoint.isAvailable) {
                System.out.println("NOTE: New alert endpoint is not available yet (returns 404)");
                System.out.println("This suggests the open-data URLs may not be deployed yet.");
            } else {
                System.out.println("SUCCESS: New alert endpoint is available!");
            }
        }

        @Test
        @DisplayName("Compare alert data structure")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareAlertDataStructure() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_ALERT_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_ALERT_URL);
            
            if (currentFeed == null) {
                System.out.println("NOTE: Current alert feed is not available - cannot perform comparison");
                if (newFeed != null) {
                    System.out.println("New feed has " + newFeed.getEntityCount() + " alerts");
                }
                return;
            }
            
            if (newFeed == null) {
                System.out.println("NOTE: Cannot compare data structures - new alert feed is not available");
                System.out.println("Current feed has " + currentFeed.getEntityCount() + " alerts");
                return;
            }
            
            DataStructureComparison comparison = compareDataStructures(currentFeed, newFeed, "Alert");
            printDataStructureComparison(comparison);
            
            // Alerts can vary more than vehicle positions
            assertThat(comparison.entityCountDifference).isLessThan(20)
                .as("Alert entity count difference should be reasonable");
        }
    }

    @Test
    @DisplayName("Generate comprehensive endpoint comparison summary")
    void generateComprehensiveSummary() {
        System.out.println("=== COMPREHENSIVE GTFS-RT ENDPOINT COMPARISON SUMMARY ===");
        System.out.println();
        
        // Test all current endpoints
        System.out.println("CURRENT PRODUCTION ENDPOINTS:");
        EndpointMetrics vehicleMetrics = testEndpoint(CURRENT_VEHICLE_URL);
        EndpointMetrics tripMetrics = testEndpoint(CURRENT_TRIP_URL);
        EndpointMetrics alertMetrics = testEndpoint(CURRENT_ALERT_URL);
        
        System.out.printf("• Vehicle Positions: %s (%s)\n", 
            CURRENT_VEHICLE_URL, vehicleMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.printf("• Trip Updates: %s (%s)\n", 
            CURRENT_TRIP_URL, tripMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.printf("• Alerts: %s (%s)\n", 
            CURRENT_ALERT_URL, alertMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.println();
        
        // Test all new endpoints
        System.out.println("NEW OPEN-DATA ENDPOINTS:");
        EndpointMetrics newVehicleMetrics = testEndpoint(NEW_VEHICLE_URL);
        EndpointMetrics newTripMetrics = testEndpoint(NEW_TRIP_URL);
        EndpointMetrics newAlertMetrics = testEndpoint(NEW_ALERT_URL);
        
        System.out.printf("• Vehicle Positions: %s (%s)\n", 
            NEW_VEHICLE_URL, newVehicleMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.printf("• Trip Updates: %s (%s)\n", 
            NEW_TRIP_URL, newTripMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.printf("• Alerts: %s (%s)\n", 
            NEW_ALERT_URL, newAlertMetrics.isAvailable ? "✅ AVAILABLE" : "❌ UNAVAILABLE");
        System.out.println();
        
        // Summary
        int currentAvailable = (vehicleMetrics.isAvailable ? 1 : 0) + 
                              (tripMetrics.isAvailable ? 1 : 0) + 
                              (alertMetrics.isAvailable ? 1 : 0);
        int newAvailable = (newVehicleMetrics.isAvailable ? 1 : 0) + 
                          (newTripMetrics.isAvailable ? 1 : 0) + 
                          (newAlertMetrics.isAvailable ? 1 : 0);
        
        System.out.println("SUMMARY:");
        System.out.printf("• Current endpoints available: %d/3\n", currentAvailable);
        System.out.printf("• New endpoints available: %d/3\n", newAvailable);
        System.out.println();
        
        if (newAvailable == 0) {
            System.out.println("CONCLUSION: New open-data endpoints are not yet deployed.");
            System.out.println("Current production endpoints should be used for all operations.");
        } else if (newAvailable < 3) {
            System.out.println("CONCLUSION: New open-data endpoints are partially deployed.");
            System.out.println("Migration should wait until all endpoints are available.");
        } else {
            System.out.println("CONCLUSION: New open-data endpoints are fully available!");
            System.out.println("Consider planning migration from current production endpoints.");
        }
        
        // This test is informational only - don't fail if new endpoints aren't ready
        assertThat(currentAvailable).isGreaterThan(0)
            .as("At least some current production endpoints should be available");
    }

    @Nested
    @DisplayName("Content Comparison")
    class ContentComparison {

        @Test
        @DisplayName("Compare vehicle position content consistency")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareVehiclePositionContent() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_VEHICLE_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_VEHICLE_URL);
            
            assertThat(currentFeed).isNotNull().as("Current vehicle feed should be available");
            assertThat(newFeed).isNotNull().as("New vehicle feed should be available");
            
            ContentComparisonResult result = GTFSRTEndpointComparisonTest.this.compareVehiclePositionContent(currentFeed, newFeed);
            GTFSRTEndpointComparisonTest.this.printVehicleContentComparison(result);
            
            // Content consistency validations
            assertThat(result.matchingVehicleIds).isGreaterThan((int)(result.totalCurrentVehicles * 0.8))
                .as("At least 80% of vehicles should match between endpoints");
            assertThat(result.significantCoordinateDifferences).isLessThan((int)(result.matchingVehicleIds * 0.1))
                .as("Less than 10% should have significant coordinate differences");
        }

        @Test
        @DisplayName("Compare trip update content consistency")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareTripUpdateContent() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_TRIP_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_TRIP_URL);
            
            assertThat(currentFeed).isNotNull().as("Current trip feed should be available");
            assertThat(newFeed).isNotNull().as("New trip feed should be available");
            
            TripContentComparisonResult result = GTFSRTEndpointComparisonTest.this.compareTripUpdateContent(currentFeed, newFeed);
            GTFSRTEndpointComparisonTest.this.printTripContentComparison(result);
            
            // Content consistency validations
            assertThat(result.matchingTripIds).isGreaterThan((int)(result.totalCurrentTrips * 0.7))
                .as("At least 70% of trips should match between endpoints");
            assertThat(result.significantDelayDifferences).isLessThan((int)(result.matchingTripIds * 0.15))
                .as("Less than 15% should have significant delay differences");
        }

        @Test
        @DisplayName("Compare alert content consistency")
        @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
        void compareAlertContent() {
            FeedMessage currentFeed = fetchGTFSRTFeed(CURRENT_ALERT_URL);
            FeedMessage newFeed = fetchGTFSRTFeed(NEW_ALERT_URL);
            
            assertThat(currentFeed).isNotNull().as("Current alert feed should be available");
            assertThat(newFeed).isNotNull().as("New alert feed should be available");
            
            AlertContentComparisonResult result = GTFSRTEndpointComparisonTest.this.compareAlertContent(currentFeed, newFeed);
            GTFSRTEndpointComparisonTest.this.printAlertContentComparison(result);
            
            // Content consistency validations
            assertThat(result.identicalAlerts).isGreaterThan(0).as("Should have at least some identical alerts");
            assertThat(result.totalUniqueAlerts).isLessThan(result.totalCurrentAlerts + result.totalNewAlerts)
                .as("Should have some overlap in alerts");
        }

        @Test
        @DisplayName("Validate data semantic consistency across endpoints")
        void validateSemanticConsistency() {
            System.out.println("=== SEMANTIC CONSISTENCY VALIDATION ===");
            
            // Get all feeds
            FeedMessage currentVehicleFeed = fetchGTFSRTFeed(CURRENT_VEHICLE_URL);
            FeedMessage newVehicleFeed = fetchGTFSRTFeed(NEW_VEHICLE_URL);
            FeedMessage currentTripFeed = fetchGTFSRTFeed(CURRENT_TRIP_URL);
            FeedMessage newTripFeed = fetchGTFSRTFeed(NEW_TRIP_URL);
            
            assertThat(currentVehicleFeed).isNotNull();
            assertThat(newVehicleFeed).isNotNull();
            assertThat(currentTripFeed).isNotNull();
            assertThat(newTripFeed).isNotNull();
            
            // Cross-feed validation
            SemanticConsistencyResult result = GTFSRTEndpointComparisonTest.this.validateSemanticConsistency(
                currentVehicleFeed, newVehicleFeed, currentTripFeed, newTripFeed);
            
            GTFSRTEndpointComparisonTest.this.printSemanticConsistencyResults(result);
            
            // Semantic consistency validations
            assertThat(result.vehiclesTripConsistency).isGreaterThan(0.8)
                .as("Vehicle-trip consistency should be > 80%");
            assertThat(result.routeIdConsistency).isGreaterThan(0.9)
                .as("Route ID consistency should be > 90%");
        }
    }

    @Nested
    @DisplayName("Performance Comparison")
    class PerformanceComparison {

        @Test
        @DisplayName("Compare endpoint response times")
        void compareResponseTimes() {
            System.out.println("=== Endpoint Performance Comparison ===");
            
            // Test all endpoints multiple times for average
            Map<String, List<Long>> responseTimes = new HashMap<>();
            String[] endpoints = {
                CURRENT_VEHICLE_URL, NEW_VEHICLE_URL,
                CURRENT_TRIP_URL, NEW_TRIP_URL,
                CURRENT_ALERT_URL, NEW_ALERT_URL
            };
            
            for (String endpoint : endpoints) {
                responseTimes.put(endpoint, measureResponseTimes(endpoint, 3));
            }
            
            // Print results
            printPerformanceComparison(responseTimes);
            
            // Performance assertions - all endpoints should respond within reasonable time
            for (String endpoint : endpoints) {
                double avgTime = responseTimes.get(endpoint).stream()
                    .mapToLong(Long::longValue)
                    .average()
                    .orElse(Double.MAX_VALUE);
                
                assertThat(avgTime).isLessThan(10000)
                    .as("Endpoint %s should respond within 10 seconds on average", endpoint);
            }
        }
    }

    // Helper Classes and Methods

    private static class ContentComparisonResult {
        int totalCurrentVehicles;
        int totalNewVehicles;
        int matchingVehicleIds;
        int exactCoordinateMatches;
        int significantCoordinateDifferences;
        int timestampDifferences;
        Map<String, VehicleComparison> vehicleDetails = new HashMap<>();
        
        ContentComparisonResult(int totalCurrentVehicles, int totalNewVehicles, int matchingVehicleIds,
                               int exactCoordinateMatches, int significantCoordinateDifferences, int timestampDifferences) {
            this.totalCurrentVehicles = totalCurrentVehicles;
            this.totalNewVehicles = totalNewVehicles;
            this.matchingVehicleIds = matchingVehicleIds;
            this.exactCoordinateMatches = exactCoordinateMatches;
            this.significantCoordinateDifferences = significantCoordinateDifferences;
            this.timestampDifferences = timestampDifferences;
        }
    }

    private static class VehicleComparison {
        String vehicleId;
        boolean coordinatesMatch;
        double coordinateDifference;
        boolean timestampsMatch;
        long timestampDifference;
        String routeId;
        String status;
        
        VehicleComparison(String vehicleId, boolean coordinatesMatch, double coordinateDifference,
                         boolean timestampsMatch, long timestampDifference, String routeId, String status) {
            this.vehicleId = vehicleId;
            this.coordinatesMatch = coordinatesMatch;
            this.coordinateDifference = coordinateDifference;
            this.timestampsMatch = timestampsMatch;
            this.timestampDifference = timestampDifference;
            this.routeId = routeId;
            this.status = status;
        }
    }

    private static class TripContentComparisonResult {
        int totalCurrentTrips;
        int totalNewTrips;
        int matchingTripIds;
        int exactDelayMatches;
        int significantDelayDifferences;
        int scheduleRelationshipMatches;
        Map<String, TripComparison> tripDetails = new HashMap<>();
        
        TripContentComparisonResult(int totalCurrentTrips, int totalNewTrips, int matchingTripIds,
                                   int exactDelayMatches, int significantDelayDifferences, int scheduleRelationshipMatches) {
            this.totalCurrentTrips = totalCurrentTrips;
            this.totalNewTrips = totalNewTrips;
            this.matchingTripIds = matchingTripIds;
            this.exactDelayMatches = exactDelayMatches;
            this.significantDelayDifferences = significantDelayDifferences;
            this.scheduleRelationshipMatches = scheduleRelationshipMatches;
        }
    }

    private static class TripComparison {
        String tripId;
        boolean delayMatches;
        int delayDifference;
        boolean scheduleRelationshipMatches;
        String routeId;
        String vehicleId;
        
        TripComparison(String tripId, boolean delayMatches, int delayDifference,
                      boolean scheduleRelationshipMatches, String routeId, String vehicleId) {
            this.tripId = tripId;
            this.delayMatches = delayMatches;
            this.delayDifference = delayDifference;
            this.scheduleRelationshipMatches = scheduleRelationshipMatches;
            this.routeId = routeId;
            this.vehicleId = vehicleId;
        }
    }

    private static class AlertContentComparisonResult {
        int totalCurrentAlerts;
        int totalNewAlerts;
        int identicalAlerts;
        int similarAlerts;
        int totalUniqueAlerts;
        Map<String, AlertComparisonDetail> alertDetails = new HashMap<>();
        
        AlertContentComparisonResult(int totalCurrentAlerts, int totalNewAlerts, int identicalAlerts,
                                    int similarAlerts, int totalUniqueAlerts) {
            this.totalCurrentAlerts = totalCurrentAlerts;
            this.totalNewAlerts = totalNewAlerts;
            this.identicalAlerts = identicalAlerts;
            this.similarAlerts = similarAlerts;
            this.totalUniqueAlerts = totalUniqueAlerts;
        }
    }

    private static class AlertComparisonDetail {
        String alertId;
        boolean identical;
        boolean headerTextMatches;
        boolean descriptionMatches;
        boolean urlMatches;
        String cause;
        String effect;
        
        AlertComparisonDetail(String alertId, boolean identical, boolean headerTextMatches,
                       boolean descriptionMatches, boolean urlMatches, String cause, String effect) {
            this.alertId = alertId;
            this.identical = identical;
            this.headerTextMatches = headerTextMatches;
            this.descriptionMatches = descriptionMatches;
            this.urlMatches = urlMatches;
            this.cause = cause;
            this.effect = effect;
        }
    }

    private static class SemanticConsistencyResult {
        double vehiclesTripConsistency;
        double routeIdConsistency;
        int vehiclesWithTrips;
        int tripsWithVehicles;
        int totalVehicles;
        int totalTrips;
        Set<String> commonRoutes = new HashSet<>();
        Set<String> onlyInCurrent = new HashSet<>();
        Set<String> onlyInNew = new HashSet<>();
        
        SemanticConsistencyResult(double vehiclesTripConsistency, double routeIdConsistency,
                                 int vehiclesWithTrips, int tripsWithVehicles, int totalVehicles, int totalTrips) {
            this.vehiclesTripConsistency = vehiclesTripConsistency;
            this.routeIdConsistency = routeIdConsistency;
            this.vehiclesWithTrips = vehiclesWithTrips;
            this.tripsWithVehicles = tripsWithVehicles;
            this.totalVehicles = totalVehicles;
            this.totalTrips = totalTrips;
        }
    }

    private static class EndpointMetrics {
        boolean isAvailable;
        int statusCode;
        long responseTimeMs;
        long contentLength;
        String errorMessage;
        boolean isValidProtobuf;
        
        EndpointMetrics(boolean isAvailable, int statusCode, long responseTimeMs, 
                       long contentLength, String errorMessage, boolean isValidProtobuf) {
            this.isAvailable = isAvailable;
            this.statusCode = statusCode;
            this.responseTimeMs = responseTimeMs;
            this.contentLength = contentLength;
            this.errorMessage = errorMessage;
            this.isValidProtobuf = isValidProtobuf;
        }
    }

    private static class EndpointComparison {
        EndpointMetrics currentEndpoint;
        EndpointMetrics newEndpoint;
        
        EndpointComparison(EndpointMetrics currentEndpoint, EndpointMetrics newEndpoint) {
            this.currentEndpoint = currentEndpoint;
            this.newEndpoint = newEndpoint;
        }
    }

    private static class DataStructureComparison {
        String feedType;
        int currentEntityCount;
        int newEntityCount;
        int entityCountDifference;
        long currentTimestamp;
        long newTimestamp;
        boolean sameVersion;
        
        DataStructureComparison(String feedType, int currentEntityCount, int newEntityCount,
                               long currentTimestamp, long newTimestamp, boolean sameVersion) {
            this.feedType = feedType;
            this.currentEntityCount = currentEntityCount;
            this.newEntityCount = newEntityCount;
            this.entityCountDifference = Math.abs(currentEntityCount - newEntityCount);
            this.currentTimestamp = currentTimestamp;
            this.newTimestamp = newTimestamp;
            this.sameVersion = sameVersion;
        }
    }

    private static class DataQualityMetrics {
        int totalEntities;
        int validEntities;
        int entitiesWithCoordinates;
        int entitiesWithTimestamp;
        double validEntityPercentage;
        double coordinatePercentage;
        double timestampPercentage;
        
        DataQualityMetrics(int totalEntities, int validEntities, int entitiesWithCoordinates, 
                          int entitiesWithTimestamp) {
            this.totalEntities = totalEntities;
            this.validEntities = validEntities;
            this.entitiesWithCoordinates = entitiesWithCoordinates;
            this.entitiesWithTimestamp = entitiesWithTimestamp;
            this.validEntityPercentage = totalEntities > 0 ? (double) validEntities / totalEntities : 0.0;
            this.coordinatePercentage = totalEntities > 0 ? (double) entitiesWithCoordinates / totalEntities : 0.0;
            this.timestampPercentage = totalEntities > 0 ? (double) entitiesWithTimestamp / totalEntities : 0.0;
        }
    }

    private EndpointComparison compareEndpoints(String currentUrl, String newUrl) {
        EndpointMetrics currentMetrics = testEndpoint(currentUrl);
        EndpointMetrics newMetrics = testEndpoint(newUrl);
        return new EndpointComparison(currentMetrics, newMetrics);
    }

    private EndpointMetrics testEndpoint(String url) {
        long startTime = System.currentTimeMillis();
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline-Test/1.0");
            
            HttpResponse response = httpClient.execute(request);
            long responseTime = System.currentTimeMillis() - startTime;
            
            int statusCode = response.getStatusLine().getStatusCode();
            boolean isAvailable = (statusCode == 200);
            
            if (isAvailable) {
                byte[] content = EntityUtils.toByteArray(response.getEntity());
                long contentLength = content.length;
                boolean isValidProtobuf = false;
                
                try {
                    FeedMessage.parseFrom(new ByteArrayInputStream(content));
                    isValidProtobuf = true;
                } catch (Exception e) {
                    // Not valid protobuf
                }
                
                return new EndpointMetrics(true, statusCode, responseTime, contentLength, null, isValidProtobuf);
            } else {
                return new EndpointMetrics(false, statusCode, responseTime, 0, 
                    "HTTP " + statusCode, false);
            }
            
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            return new EndpointMetrics(false, 0, responseTime, 0, e.getMessage(), false);
        }
    }

    private FeedMessage fetchGTFSRTFeed(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "RTD-GTFS-Pipeline-Test/1.0");
            
            HttpResponse response = httpClient.execute(request);
            
            if (response.getStatusLine().getStatusCode() == 200) {
                byte[] content = EntityUtils.toByteArray(response.getEntity());
                return FeedMessage.parseFrom(new ByteArrayInputStream(content));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch feed from " + url + ": " + e.getMessage());
        }
        return null;
    }

    private DataStructureComparison compareDataStructures(FeedMessage currentFeed, FeedMessage newFeed, String feedType) {
        int currentCount = currentFeed.getEntityCount();
        int newCount = newFeed.getEntityCount();
        long currentTimestamp = currentFeed.getHeader().getTimestamp();
        long newTimestamp = newFeed.getHeader().getTimestamp();
        boolean sameVersion = currentFeed.getHeader().getGtfsRealtimeVersion()
            .equals(newFeed.getHeader().getGtfsRealtimeVersion());
        
        return new DataStructureComparison(feedType, currentCount, newCount, 
            currentTimestamp, newTimestamp, sameVersion);
    }

    private DataQualityMetrics analyzeVehiclePositionQuality(FeedMessage feed) {
        int totalEntities = feed.getEntityCount();
        int validEntities = 0;
        int entitiesWithCoordinates = 0;
        int entitiesWithTimestamp = 0;
        
        for (FeedEntity entity : feed.getEntityList()) {
            if (entity.hasVehicle()) {
                VehiclePosition vehicle = entity.getVehicle();
                boolean isValid = true;
                
                // Check coordinates
                if (vehicle.hasPosition()) {
                    Position pos = vehicle.getPosition();
                    if (pos.hasLatitude() && pos.hasLongitude()) {
                        entitiesWithCoordinates++;
                    } else {
                        isValid = false;
                    }
                } else {
                    isValid = false;
                }
                
                // Check vehicle ID
                if (!vehicle.hasVehicle() || !vehicle.getVehicle().hasId()) {
                    isValid = false;
                }
                
                // Check timestamp (use feed timestamp if entity doesn't have one)
                if (vehicle.hasTimestamp() || feed.getHeader().hasTimestamp()) {
                    entitiesWithTimestamp++;
                }
                
                if (isValid) {
                    validEntities++;
                }
            }
        }
        
        return new DataQualityMetrics(totalEntities, validEntities, entitiesWithCoordinates, entitiesWithTimestamp);
    }

    private List<Long> measureResponseTimes(String url, int iterations) {
        List<Long> times = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpGet request = new HttpGet(url);
                request.setHeader("User-Agent", "RTD-GTFS-Pipeline-Test/1.0");
                
                HttpResponse response = httpClient.execute(request);
                EntityUtils.consume(response.getEntity()); // Consume response
                
                long responseTime = System.currentTimeMillis() - startTime;
                times.add(responseTime);
                
            } catch (Exception e) {
                long responseTime = System.currentTimeMillis() - startTime;
                times.add(responseTime);
            }
            
            // Brief pause between requests
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return times;
    }

    // Output formatting methods

    private void printComparisonResults(EndpointComparison comparison) {
        System.out.println("Current Endpoint:");
        printEndpointMetrics(comparison.currentEndpoint);
        System.out.println();
        
        System.out.println("New Endpoint:");
        printEndpointMetrics(comparison.newEndpoint);
        System.out.println();
        
        // Performance comparison
        if (comparison.currentEndpoint.isAvailable && comparison.newEndpoint.isAvailable) {
            long timeDiff = comparison.newEndpoint.responseTimeMs - comparison.currentEndpoint.responseTimeMs;
            long sizeDiff = comparison.newEndpoint.contentLength - comparison.currentEndpoint.contentLength;
            
            System.out.println("Performance Difference:");
            System.out.printf("  Response Time: %+d ms (%s)\n", 
                timeDiff, timeDiff < 0 ? "new is faster" : "current is faster");
            System.out.printf("  Content Size: %+d bytes (%s)\n", 
                sizeDiff, sizeDiff < 0 ? "new is smaller" : "current is smaller");
        }
        System.out.println();
    }

    private void printEndpointMetrics(EndpointMetrics metrics) {
        System.out.printf("  Available: %s\n", metrics.isAvailable ? "✅ YES" : "❌ NO");
        System.out.printf("  Status Code: %d\n", metrics.statusCode);
        System.out.printf("  Response Time: %d ms\n", metrics.responseTimeMs);
        System.out.printf("  Content Length: %d bytes\n", metrics.contentLength);
        System.out.printf("  Valid Protobuf: %s\n", metrics.isValidProtobuf ? "✅ YES" : "❌ NO");
        
        if (metrics.errorMessage != null) {
            System.out.printf("  Error: %s\n", metrics.errorMessage);
        }
    }

    private void printDataStructureComparison(DataStructureComparison comparison) {
        System.out.printf("=== %s Data Structure Comparison ===\n", comparison.feedType);
        System.out.printf("Current Entity Count: %d\n", comparison.currentEntityCount);
        System.out.printf("New Entity Count: %d\n", comparison.newEntityCount);
        System.out.printf("Difference: %d entities\n", comparison.entityCountDifference);
        System.out.printf("Current Timestamp: %d\n", comparison.currentTimestamp);
        System.out.printf("New Timestamp: %d\n", comparison.newTimestamp);
        System.out.printf("Same GTFS-RT Version: %s\n", comparison.sameVersion ? "✅ YES" : "❌ NO");
        System.out.println();
    }

    private void printDataQualityComparison(DataQualityMetrics current, DataQualityMetrics newMetrics) {
        System.out.println("Current Feed Quality:");
        printDataQualityMetrics(current);
        System.out.println();
        
        System.out.println("New Feed Quality:");
        printDataQualityMetrics(newMetrics);
        System.out.println();
        
        System.out.println("Quality Comparison:");
        System.out.printf("  Valid Entity Rate: %.1f%% vs %.1f%% (%+.1f%%)\n",
            current.validEntityPercentage * 100,
            newMetrics.validEntityPercentage * 100,
            (newMetrics.validEntityPercentage - current.validEntityPercentage) * 100);
        System.out.printf("  Coordinate Coverage: %.1f%% vs %.1f%% (%+.1f%%)\n",
            current.coordinatePercentage * 100,
            newMetrics.coordinatePercentage * 100,
            (newMetrics.coordinatePercentage - current.coordinatePercentage) * 100);
        System.out.println();
    }

    private void printDataQualityMetrics(DataQualityMetrics metrics) {
        System.out.printf("  Total Entities: %d\n", metrics.totalEntities);
        System.out.printf("  Valid Entities: %d (%.1f%%)\n", 
            metrics.validEntities, metrics.validEntityPercentage * 100);
        System.out.printf("  With Coordinates: %d (%.1f%%)\n", 
            metrics.entitiesWithCoordinates, metrics.coordinatePercentage * 100);
        System.out.printf("  With Timestamp: %d (%.1f%%)\n", 
            metrics.entitiesWithTimestamp, metrics.timestampPercentage * 100);
    }

    private void printPerformanceComparison(Map<String, List<Long>> responseTimes) {
        String[] endpointNames = {
            "Current Vehicle", "New Vehicle",
            "Current Trip", "New Trip", 
            "Current Alert", "New Alert"
        };
        String[] endpoints = {
            CURRENT_VEHICLE_URL, NEW_VEHICLE_URL,
            CURRENT_TRIP_URL, NEW_TRIP_URL,
            CURRENT_ALERT_URL, NEW_ALERT_URL
        };
        
        for (int i = 0; i < endpoints.length; i++) {
            List<Long> times = responseTimes.get(endpoints[i]);
            double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long minTime = times.stream().mapToLong(Long::longValue).min().orElse(0L);
            long maxTime = times.stream().mapToLong(Long::longValue).max().orElse(0L);
            
            System.out.printf("%s: avg=%.0fms, min=%dms, max=%dms\n",
                endpointNames[i], avgTime, minTime, maxTime);
        }
    }

    // Content comparison implementation methods

    private ContentComparisonResult compareVehiclePositionContent(FeedMessage currentFeed, FeedMessage newFeed) {
        Map<String, VehiclePosition> currentVehicles = new HashMap<>();
        Map<String, VehiclePosition> newVehicles = new HashMap<>();
        
        // Index current vehicles by vehicle ID
        for (FeedEntity entity : currentFeed.getEntityList()) {
            if (entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId()) {
                String vehicleId = entity.getVehicle().getVehicle().getId();
                currentVehicles.put(vehicleId, entity.getVehicle());
            }
        }
        
        // Index new vehicles by vehicle ID
        for (FeedEntity entity : newFeed.getEntityList()) {
            if (entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId()) {
                String vehicleId = entity.getVehicle().getVehicle().getId();
                newVehicles.put(vehicleId, entity.getVehicle());
            }
        }
        
        int matchingVehicleIds = 0;
        int exactCoordinateMatches = 0;
        int significantCoordinateDifferences = 0;
        int timestampDifferences = 0;
        
        ContentComparisonResult result = new ContentComparisonResult(
            currentVehicles.size(), newVehicles.size(), 0, 0, 0, 0);
        
        // Compare matching vehicles
        for (String vehicleId : currentVehicles.keySet()) {
            if (newVehicles.containsKey(vehicleId)) {
                matchingVehicleIds++;
                VehiclePosition currentVehicle = currentVehicles.get(vehicleId);
                VehiclePosition newVehicle = newVehicles.get(vehicleId);
                
                boolean coordinatesMatch = true;
                double coordinateDifference = 0.0;
                
                if (currentVehicle.hasPosition() && newVehicle.hasPosition()) {
                    double currentLat = currentVehicle.getPosition().getLatitude();
                    double currentLon = currentVehicle.getPosition().getLongitude();
                    double newLat = newVehicle.getPosition().getLatitude();
                    double newLon = newVehicle.getPosition().getLongitude();
                    
                    // Calculate distance difference (simple Euclidean for comparison)
                    coordinateDifference = Math.sqrt(Math.pow(currentLat - newLat, 2) + Math.pow(currentLon - newLon, 2));
                    
                    if (coordinateDifference < 0.0001) { // Very close coordinates
                        exactCoordinateMatches++;
                    } else if (coordinateDifference > 0.01) { // Significant difference (roughly 1km)
                        significantCoordinateDifferences++;
                        coordinatesMatch = false;
                    }
                }
                
                // Compare timestamps
                boolean timestampsMatch = true;
                long timestampDifference = 0;
                if (currentVehicle.hasTimestamp() && newVehicle.hasTimestamp()) {
                    timestampDifference = Math.abs(currentVehicle.getTimestamp() - newVehicle.getTimestamp());
                    if (timestampDifference > 300) { // More than 5 minutes difference
                        timestampDifferences++;
                        timestampsMatch = false;
                    }
                }
                
                // Get additional info
                String routeId = currentVehicle.hasTrip() ? currentVehicle.getTrip().getRouteId() : "N/A";
                String status = currentVehicle.hasCurrentStatus() ? currentVehicle.getCurrentStatus().name() : "N/A";
                
                VehicleComparison vehicleComparison = new VehicleComparison(
                    vehicleId, coordinatesMatch, coordinateDifference, timestampsMatch, timestampDifference, routeId, status);
                result.vehicleDetails.put(vehicleId, vehicleComparison);
            }
        }
        
        result.matchingVehicleIds = matchingVehicleIds;
        result.exactCoordinateMatches = exactCoordinateMatches;
        result.significantCoordinateDifferences = significantCoordinateDifferences;
        result.timestampDifferences = timestampDifferences;
        
        return result;
    }

    private TripContentComparisonResult compareTripUpdateContent(FeedMessage currentFeed, FeedMessage newFeed) {
        Map<String, TripUpdate> currentTrips = new HashMap<>();
        Map<String, TripUpdate> newTrips = new HashMap<>();
        
        // Index trips by trip ID
        for (FeedEntity entity : currentFeed.getEntityList()) {
            if (entity.hasTripUpdate() && entity.getTripUpdate().hasTrip() && entity.getTripUpdate().getTrip().hasTripId()) {
                String tripId = entity.getTripUpdate().getTrip().getTripId();
                currentTrips.put(tripId, entity.getTripUpdate());
            }
        }
        
        for (FeedEntity entity : newFeed.getEntityList()) {
            if (entity.hasTripUpdate() && entity.getTripUpdate().hasTrip() && entity.getTripUpdate().getTrip().hasTripId()) {
                String tripId = entity.getTripUpdate().getTrip().getTripId();
                newTrips.put(tripId, entity.getTripUpdate());
            }
        }
        
        int matchingTripIds = 0;
        int exactDelayMatches = 0;
        int significantDelayDifferences = 0;
        int scheduleRelationshipMatches = 0;
        
        TripContentComparisonResult result = new TripContentComparisonResult(
            currentTrips.size(), newTrips.size(), 0, 0, 0, 0);
        
        // Compare matching trips
        for (String tripId : currentTrips.keySet()) {
            if (newTrips.containsKey(tripId)) {
                matchingTripIds++;
                TripUpdate currentTrip = currentTrips.get(tripId);
                TripUpdate newTrip = newTrips.get(tripId);
                
                // Compare delays
                boolean delayMatches = true;
                int delayDifference = 0;
                if (currentTrip.hasDelay() && newTrip.hasDelay()) {
                    delayDifference = Math.abs(currentTrip.getDelay() - newTrip.getDelay());
                    if (delayDifference == 0) {
                        exactDelayMatches++;
                    } else if (delayDifference > 180) { // More than 3 minutes difference
                        significantDelayDifferences++;
                        delayMatches = false;
                    }
                }
                
                // Compare schedule relationships
                boolean relationshipMatches = true;
                if (currentTrip.getTrip().hasScheduleRelationship() && newTrip.getTrip().hasScheduleRelationship()) {
                    if (currentTrip.getTrip().getScheduleRelationship().equals(newTrip.getTrip().getScheduleRelationship())) {
                        scheduleRelationshipMatches++;
                    } else {
                        relationshipMatches = false;
                    }
                }
                
                // Get additional info
                String routeId = currentTrip.getTrip().hasRouteId() ? currentTrip.getTrip().getRouteId() : "N/A";
                String vehicleId = currentTrip.hasVehicle() && currentTrip.getVehicle().hasId() ? 
                    currentTrip.getVehicle().getId() : "N/A";
                
                TripComparison tripComparison = new TripComparison(
                    tripId, delayMatches, delayDifference, relationshipMatches, routeId, vehicleId);
                result.tripDetails.put(tripId, tripComparison);
            }
        }
        
        result.matchingTripIds = matchingTripIds;
        result.exactDelayMatches = exactDelayMatches;
        result.significantDelayDifferences = significantDelayDifferences;
        result.scheduleRelationshipMatches = scheduleRelationshipMatches;
        
        return result;
    }

    private AlertContentComparisonResult compareAlertContent(FeedMessage currentFeed, FeedMessage newFeed) {
        Map<String, Alert> currentAlerts = new HashMap<>();
        Map<String, Alert> newAlerts = new HashMap<>();
        
        // Index alerts by ID or content hash
        for (FeedEntity entity : currentFeed.getEntityList()) {
            if (entity.hasAlert()) {
                String alertId = entity.getId();
                currentAlerts.put(alertId, entity.getAlert());
            }
        }
        
        for (FeedEntity entity : newFeed.getEntityList()) {
            if (entity.hasAlert()) {
                String alertId = entity.getId();
                newAlerts.put(alertId, entity.getAlert());
            }
        }
        
        int identicalAlerts = 0;
        int similarAlerts = 0;
        Set<String> allAlertIds = new HashSet<>();
        allAlertIds.addAll(currentAlerts.keySet());
        allAlertIds.addAll(newAlerts.keySet());
        
        AlertContentComparisonResult result = new AlertContentComparisonResult(
            currentAlerts.size(), newAlerts.size(), 0, 0, allAlertIds.size());
        
        // Compare alerts
        for (String alertId : allAlertIds) {
            if (currentAlerts.containsKey(alertId) && newAlerts.containsKey(alertId)) {
                Alert currentAlert = currentAlerts.get(alertId);
                Alert newAlert = newAlerts.get(alertId);
                
                boolean identical = true;
                boolean headerTextMatches = false;
                boolean descriptionMatches = false;
                boolean urlMatches = false;
                
                // Compare header text
                if (currentAlert.hasHeaderText() && newAlert.hasHeaderText()) {
                    headerTextMatches = currentAlert.getHeaderText().equals(newAlert.getHeaderText());
                    if (!headerTextMatches) identical = false;
                }
                
                // Compare description
                if (currentAlert.hasDescriptionText() && newAlert.hasDescriptionText()) {
                    descriptionMatches = currentAlert.getDescriptionText().equals(newAlert.getDescriptionText());
                    if (!descriptionMatches) identical = false;
                }
                
                // Compare URL
                if (currentAlert.hasUrl() && newAlert.hasUrl()) {
                    urlMatches = currentAlert.getUrl().equals(newAlert.getUrl());
                    if (!urlMatches) identical = false;
                }
                
                if (identical) {
                    identicalAlerts++;
                } else if (headerTextMatches || descriptionMatches) {
                    similarAlerts++;
                }
                
                String cause = currentAlert.hasCause() ? currentAlert.getCause().name() : "N/A";
                String effect = currentAlert.hasEffect() ? currentAlert.getEffect().name() : "N/A";
                
                AlertComparisonDetail alertComparison = new AlertComparisonDetail(
                    alertId, identical, headerTextMatches, descriptionMatches, urlMatches, cause, effect);
                result.alertDetails.put(alertId, alertComparison);
            }
        }
        
        result.identicalAlerts = identicalAlerts;
        result.similarAlerts = similarAlerts;
        
        return result;
    }

    private SemanticConsistencyResult validateSemanticConsistency(
            FeedMessage currentVehicleFeed, FeedMessage newVehicleFeed,
            FeedMessage currentTripFeed, FeedMessage newTripFeed) {
        
        Set<String> currentVehicleIds = new HashSet<>();
        Set<String> newVehicleIds = new HashSet<>();
        Set<String> currentTripIds = new HashSet<>();
        Set<String> newTripIds = new HashSet<>();
        Set<String> currentRoutes = new HashSet<>();
        Set<String> newRoutes = new HashSet<>();
        
        // Extract vehicle IDs and routes from vehicle feeds
        for (FeedEntity entity : currentVehicleFeed.getEntityList()) {
            if (entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId()) {
                currentVehicleIds.add(entity.getVehicle().getVehicle().getId());
                if (entity.getVehicle().hasTrip() && entity.getVehicle().getTrip().hasRouteId()) {
                    currentRoutes.add(entity.getVehicle().getTrip().getRouteId());
                }
            }
        }
        
        for (FeedEntity entity : newVehicleFeed.getEntityList()) {
            if (entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId()) {
                newVehicleIds.add(entity.getVehicle().getVehicle().getId());
                if (entity.getVehicle().hasTrip() && entity.getVehicle().getTrip().hasRouteId()) {
                    newRoutes.add(entity.getVehicle().getTrip().getRouteId());
                }
            }
        }
        
        // Extract trip IDs and routes from trip feeds
        for (FeedEntity entity : currentTripFeed.getEntityList()) {
            if (entity.hasTripUpdate() && entity.getTripUpdate().hasTrip() && entity.getTripUpdate().getTrip().hasTripId()) {
                currentTripIds.add(entity.getTripUpdate().getTrip().getTripId());
                if (entity.getTripUpdate().getTrip().hasRouteId()) {
                    currentRoutes.add(entity.getTripUpdate().getTrip().getRouteId());
                }
            }
        }
        
        for (FeedEntity entity : newTripFeed.getEntityList()) {
            if (entity.hasTripUpdate() && entity.getTripUpdate().hasTrip() && entity.getTripUpdate().getTrip().hasTripId()) {
                newTripIds.add(entity.getTripUpdate().getTrip().getTripId());
                if (entity.getTripUpdate().getTrip().hasRouteId()) {
                    newRoutes.add(entity.getTripUpdate().getTrip().getRouteId());
                }
            }
        }
        
        // Calculate consistency metrics
        Set<String> commonVehicles = new HashSet<>(currentVehicleIds);
        commonVehicles.retainAll(newVehicleIds);
        
        Set<String> commonTrips = new HashSet<>(currentTripIds);
        commonTrips.retainAll(newTripIds);
        
        Set<String> commonRoutes = new HashSet<>(currentRoutes);
        commonRoutes.retainAll(newRoutes);
        
        double vehiclesTripConsistency = currentVehicleIds.size() > 0 ? 
            (double) commonVehicles.size() / currentVehicleIds.size() : 0.0;
        double routeIdConsistency = currentRoutes.size() > 0 ? 
            (double) commonRoutes.size() / currentRoutes.size() : 0.0;
        
        SemanticConsistencyResult result = new SemanticConsistencyResult(
            vehiclesTripConsistency, routeIdConsistency, 
            commonVehicles.size(), commonTrips.size(), 
            currentVehicleIds.size(), currentTripIds.size());
        
        result.commonRoutes = commonRoutes;
        result.onlyInCurrent.addAll(currentRoutes);
        result.onlyInCurrent.removeAll(newRoutes);
        result.onlyInNew.addAll(newRoutes);
        result.onlyInNew.removeAll(currentRoutes);
        
        return result;
    }

    // Content comparison output methods
    
    private void printVehicleContentComparison(ContentComparisonResult result) {
        System.out.println("=== Vehicle Position Content Comparison ===");
        System.out.printf("Current vehicles: %d | New vehicles: %d\n", 
            result.totalCurrentVehicles, result.totalNewVehicles);
        System.out.printf("Matching vehicle IDs: %d (%.1f%%)\n", 
            result.matchingVehicleIds, (double) result.matchingVehicleIds / result.totalCurrentVehicles * 100);
        System.out.printf("Exact coordinate matches: %d (%.1f%%)\n", 
            result.exactCoordinateMatches, (double) result.exactCoordinateMatches / result.matchingVehicleIds * 100);
        System.out.printf("Significant coordinate differences: %d (%.1f%%)\n", 
            result.significantCoordinateDifferences, 
            result.matchingVehicleIds > 0 ? (double) result.significantCoordinateDifferences / result.matchingVehicleIds * 100 : 0);
        System.out.printf("Timestamp differences (>5min): %d (%.1f%%)\n", 
            result.timestampDifferences,
            result.matchingVehicleIds > 0 ? (double) result.timestampDifferences / result.matchingVehicleIds * 100 : 0);
        System.out.println();
    }
    
    private void printTripContentComparison(TripContentComparisonResult result) {
        System.out.println("=== Trip Update Content Comparison ===");
        System.out.printf("Current trips: %d | New trips: %d\n", 
            result.totalCurrentTrips, result.totalNewTrips);
        System.out.printf("Matching trip IDs: %d (%.1f%%)\n", 
            result.matchingTripIds, (double) result.matchingTripIds / result.totalCurrentTrips * 100);
        System.out.printf("Exact delay matches: %d (%.1f%%)\n", 
            result.exactDelayMatches, 
            result.matchingTripIds > 0 ? (double) result.exactDelayMatches / result.matchingTripIds * 100 : 0);
        System.out.printf("Significant delay differences (>3min): %d (%.1f%%)\n", 
            result.significantDelayDifferences,
            result.matchingTripIds > 0 ? (double) result.significantDelayDifferences / result.matchingTripIds * 100 : 0);
        System.out.printf("Schedule relationship matches: %d (%.1f%%)\n", 
            result.scheduleRelationshipMatches,
            result.matchingTripIds > 0 ? (double) result.scheduleRelationshipMatches / result.matchingTripIds * 100 : 0);
        System.out.println();
    }
    
    private void printAlertContentComparison(AlertContentComparisonResult result) {
        System.out.println("=== Alert Content Comparison ===");
        System.out.printf("Current alerts: %d | New alerts: %d\n", 
            result.totalCurrentAlerts, result.totalNewAlerts);
        System.out.printf("Identical alerts: %d\n", result.identicalAlerts);
        System.out.printf("Similar alerts: %d\n", result.similarAlerts);
        System.out.printf("Total unique alert IDs: %d\n", result.totalUniqueAlerts);
        System.out.printf("Alert overlap: %.1f%%\n", 
            result.totalUniqueAlerts > 0 ? 
            (double) (result.identicalAlerts + result.similarAlerts) / result.totalUniqueAlerts * 100 : 0);
        System.out.println();
    }
    
    private void printSemanticConsistencyResults(SemanticConsistencyResult result) {
        System.out.printf("Vehicle-Trip Consistency: %.1f%%\n", result.vehiclesTripConsistency * 100);
        System.out.printf("Route ID Consistency: %.1f%%\n", result.routeIdConsistency * 100);
        System.out.printf("Common vehicles: %d/%d\n", result.vehiclesWithTrips, result.totalVehicles);
        System.out.printf("Common trips: %d/%d\n", result.tripsWithVehicles, result.totalTrips);
        System.out.printf("Common routes: %d\n", result.commonRoutes.size());
        
        if (!result.onlyInCurrent.isEmpty()) {
            System.out.printf("Routes only in current: %s\n", 
                String.join(", ", result.onlyInCurrent.stream().limit(5).toArray(String[]::new)));
        }
        if (!result.onlyInNew.isEmpty()) {
            System.out.printf("Routes only in new: %s\n", 
                String.join(", ", result.onlyInNew.stream().limit(5).toArray(String[]::new)));
        }
        System.out.println();
    }
}