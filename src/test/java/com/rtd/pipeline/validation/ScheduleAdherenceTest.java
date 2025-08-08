package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.GTFSScheduleData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test cases for detecting missing vehicles and schedule adherence issues.
 * Compares real-time data (vehicle positions and trip updates) with GTFS schedule data
 * to identify buses and trains that are missing or more than 3 minutes late.
 */
@DisplayName("Schedule Adherence Tests")
public class ScheduleAdherenceTest {
    
    private static final int LATE_THRESHOLD_SECONDS = 180; // 3 minutes
    private static final int MISSING_THRESHOLD_MINUTES = 10; // Consider missing if no update in 10 minutes
    
    /**
     * Represents a scheduled trip from GTFS data
     */
    public static class ScheduledTrip {
        public final String tripId;
        public final String routeId;
        public final String serviceId;
        public final LocalTime scheduledTime;
        public final String stopId;
        public final int stopSequence;
        
        public ScheduledTrip(String tripId, String routeId, String serviceId, 
                           LocalTime scheduledTime, String stopId, int stopSequence) {
            this.tripId = tripId;
            this.routeId = routeId;
            this.serviceId = serviceId;
            this.scheduledTime = scheduledTime;
            this.stopId = stopId;
            this.stopSequence = stopSequence;
        }
    }
    
    /**
     * Represents the adherence status of a vehicle/trip
     */
    public static class AdherenceStatus {
        public final String tripId;
        public final String routeId;
        public final String vehicleId;
        public final AdherenceType type;
        public final int delaySeconds;
        public final String message;
        
        public enum AdherenceType {
            ON_TIME,
            SLIGHTLY_LATE, // < 3 minutes
            SIGNIFICANTLY_LATE, // >= 3 minutes
            MISSING,
            NO_SCHEDULE_DATA
        }
        
        public AdherenceStatus(String tripId, String routeId, String vehicleId, 
                             AdherenceType type, int delaySeconds, String message) {
            this.tripId = tripId;
            this.routeId = routeId;
            this.vehicleId = vehicleId;
            this.type = type;
            this.delaySeconds = delaySeconds;
            this.message = message;
        }
        
        public boolean isProblematic() {
            return type == AdherenceType.SIGNIFICANTLY_LATE || type == AdherenceType.MISSING;
        }
    }
    
    /**
     * Analyzes schedule adherence by comparing real-time data with schedule data
     */
    public static class ScheduleAdherenceAnalyzer {
        
        public List<AdherenceStatus> analyzeAdherence(
                List<VehiclePosition> vehiclePositions,
                List<TripUpdate> tripUpdates,
                List<ScheduledTrip> scheduledTrips) {
            
            List<AdherenceStatus> results = new ArrayList<>();
            Map<String, TripUpdate> tripUpdateMap = tripUpdates.stream()
                .collect(Collectors.toMap(TripUpdate::getTripId, tu -> tu, (a, b) -> a));
            
            Map<String, VehiclePosition> vehiclePositionMap = vehiclePositions.stream()
                .filter(vp -> vp.getTripId() != null)
                .collect(Collectors.toMap(VehiclePosition::getTripId, vp -> vp, (a, b) -> a));
            
            // Check each scheduled trip
            for (ScheduledTrip scheduled : scheduledTrips) {
                AdherenceStatus status = analyzeTrip(scheduled, tripUpdateMap, vehiclePositionMap);
                results.add(status);
            }
            
            // Check for real-time trips not in schedule
            for (TripUpdate tripUpdate : tripUpdates) {
                boolean hasSchedule = scheduledTrips.stream()
                    .anyMatch(st -> st.tripId.equals(tripUpdate.getTripId()));
                
                if (!hasSchedule) {
                    results.add(new AdherenceStatus(
                        tripUpdate.getTripId(),
                        tripUpdate.getRouteId(),
                        tripUpdate.getVehicleId(),
                        AdherenceStatus.AdherenceType.NO_SCHEDULE_DATA,
                        tripUpdate.getDelaySeconds() != null ? tripUpdate.getDelaySeconds() : 0,
                        "Trip found in real-time data but not in schedule"
                    ));
                }
            }
            
            return results;
        }
        
        private AdherenceStatus analyzeTrip(ScheduledTrip scheduled, 
                                          Map<String, TripUpdate> tripUpdateMap,
                                          Map<String, VehiclePosition> vehiclePositionMap) {
            
            TripUpdate tripUpdate = tripUpdateMap.get(scheduled.tripId);
            VehiclePosition vehiclePosition = vehiclePositionMap.get(scheduled.tripId);
            
            // Check if trip is missing (no real-time data)
            if (tripUpdate == null && vehiclePosition == null) {
                return new AdherenceStatus(
                    scheduled.tripId,
                    scheduled.routeId,
                    null,
                    AdherenceStatus.AdherenceType.MISSING,
                    0,
                    "No real-time data found for scheduled trip"
                );
            }
            
            // Check if vehicle position is too old (indicates missing vehicle)
            if (vehiclePosition != null && isPositionTooOld(vehiclePosition)) {
                return new AdherenceStatus(
                    scheduled.tripId,
                    scheduled.routeId,
                    vehiclePosition.getVehicleId(),
                    AdherenceStatus.AdherenceType.MISSING,
                    0,
                    String.format("Vehicle position is %d minutes old", 
                        getPositionAgeMinutes(vehiclePosition))
                );
            }
            
            // Analyze delay if we have trip update data
            if (tripUpdate != null) {
                return analyzeDelay(scheduled, tripUpdate);
            }
            
            // If we only have vehicle position, assume on time
            return new AdherenceStatus(
                scheduled.tripId,
                scheduled.routeId,
                vehiclePosition != null ? vehiclePosition.getVehicleId() : null,
                AdherenceStatus.AdherenceType.ON_TIME,
                0,
                "Vehicle position available but no delay information"
            );
        }
        
        private AdherenceStatus analyzeDelay(ScheduledTrip scheduled, TripUpdate tripUpdate) {
            Integer delaySeconds = tripUpdate.getDelaySeconds();
            
            if (delaySeconds == null) {
                return new AdherenceStatus(
                    scheduled.tripId,
                    scheduled.routeId,
                    tripUpdate.getVehicleId(),
                    AdherenceStatus.AdherenceType.ON_TIME,
                    0,
                    "No delay information available"
                );
            }
            
            if (delaySeconds >= LATE_THRESHOLD_SECONDS) {
                return new AdherenceStatus(
                    scheduled.tripId,
                    scheduled.routeId,
                    tripUpdate.getVehicleId(),
                    AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE,
                    delaySeconds,
                    String.format("Vehicle is %d minutes %d seconds late", 
                        delaySeconds / 60, delaySeconds % 60)
                );
            } else if (delaySeconds > 60) {
                return new AdherenceStatus(
                    scheduled.tripId,
                    scheduled.routeId,
                    tripUpdate.getVehicleId(),
                    AdherenceStatus.AdherenceType.SLIGHTLY_LATE,
                    delaySeconds,
                    String.format("Vehicle is %d seconds late", delaySeconds)
                );
            }
            
            return new AdherenceStatus(
                scheduled.tripId,
                scheduled.routeId,
                tripUpdate.getVehicleId(),
                AdherenceStatus.AdherenceType.ON_TIME,
                delaySeconds,
                delaySeconds < 0 ? String.format("Vehicle is %d seconds early", Math.abs(delaySeconds)) : "On time"
            );
        }
        
        private boolean isPositionTooOld(VehiclePosition position) {
            return getPositionAgeMinutes(position) > MISSING_THRESHOLD_MINUTES;
        }
        
        private int getPositionAgeMinutes(VehiclePosition position) {
            long currentTime = Instant.now().toEpochMilli();
            long positionTime = position.getTimestamp();
            return (int) ((currentTime - positionTime) / (1000 * 60));
        }
    }
    
    @Nested
    @DisplayName("Missing Vehicle Detection")
    class MissingVehicleDetectionTests {
        
        @Test
        @DisplayName("Should detect completely missing vehicles")
        void shouldDetectCompletelyMissingVehicles() {
            // Arrange
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1),
                new ScheduledTrip("TRIP_002", "ROUTE_20", "SERVICE_1", 
                    LocalTime.of(8, 45), "STOP_002", 1)
            );
            
            List<VehiclePosition> positions = Collections.emptyList();
            List<TripUpdate> updates = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> r.type == AdherenceStatus.AdherenceType.MISSING));
            
            results.forEach(result -> {
                assertTrue(result.isProblematic());
                assertEquals("No real-time data found for scheduled trip", result.message);
            });
        }
        
        @Test
        @DisplayName("Should detect vehicles with outdated positions")
        void shouldDetectVehiclesWithOutdatedPositions() {
            // Arrange
            long oldTimestamp = Instant.now().toEpochMilli() - (15 * 60 * 1000); // 15 minutes ago
            
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1)
            );
            
            List<VehiclePosition> positions = Arrays.asList(
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_001")
                    .timestamp(oldTimestamp)
                    .build()
            );
            
            List<TripUpdate> updates = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(1, results.size());
            AdherenceStatus result = results.get(0);
            
            assertEquals(AdherenceStatus.AdherenceType.MISSING, result.type);
            assertTrue(result.isProblematic());
            assertTrue(result.message.contains("minutes old"));
        }
        
        @Test
        @DisplayName("Should not flag vehicles with recent positions as missing")
        void shouldNotFlagVehiclesWithRecentPositions() {
            // Arrange
            long recentTimestamp = Instant.now().toEpochMilli() - (2 * 60 * 1000); // 2 minutes ago
            
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1)
            );
            
            List<VehiclePosition> positions = Arrays.asList(
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_001")
                    .timestamp(recentTimestamp)
                    .build()
            );
            
            List<TripUpdate> updates = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(1, results.size());
            AdherenceStatus result = results.get(0);
            
            assertNotEquals(AdherenceStatus.AdherenceType.MISSING, result.type);
            assertFalse(result.isProblematic());
        }
    }
    
    @Nested
    @DisplayName("Delay Detection Tests")
    class DelayDetectionTests {
        
        @Test
        @DisplayName("Should detect vehicles more than 3 minutes late")
        void shouldDetectSignificantlyLateVehicles() {
            // Arrange
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1),
                new ScheduledTrip("TRIP_002", "ROUTE_20", "SERVICE_1", 
                    LocalTime.of(8, 45), "STOP_002", 1)
            );
            
            List<TripUpdate> updates = Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_001")
                    .delaySeconds(240) // 4 minutes late
                    .build(),
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_002")
                    .delaySeconds(600) // 10 minutes late
                    .build()
            );
            
            List<VehiclePosition> positions = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(2, results.size());
            
            AdherenceStatus trip1Result = results.stream()
                .filter(r -> r.tripId.equals("TRIP_001"))
                .findFirst().orElseThrow();
            
            AdherenceStatus trip2Result = results.stream()
                .filter(r -> r.tripId.equals("TRIP_002"))
                .findFirst().orElseThrow();
            
            // Both should be significantly late
            assertEquals(AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE, trip1Result.type);
            assertEquals(AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE, trip2Result.type);
            
            assertTrue(trip1Result.isProblematic());
            assertTrue(trip2Result.isProblematic());
            
            assertEquals(240, trip1Result.delaySeconds);
            assertEquals(600, trip2Result.delaySeconds);
            
            assertTrue(trip1Result.message.contains("4 minutes"));
            assertTrue(trip2Result.message.contains("10 minutes"));
        }
        
        @Test
        @DisplayName("Should not flag vehicles less than 3 minutes late as problematic")
        void shouldNotFlagSlightlyLateVehiclesAsProblematic() {
            // Arrange
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1)
            );
            
            List<TripUpdate> updates = Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_001")
                    .delaySeconds(120) // 2 minutes late
                    .build()
            );
            
            List<VehiclePosition> positions = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(1, results.size());
            AdherenceStatus result = results.get(0);
            
            assertEquals(AdherenceStatus.AdherenceType.SLIGHTLY_LATE, result.type);
            assertFalse(result.isProblematic());
            assertEquals(120, result.delaySeconds);
        }
        
        @Test
        @DisplayName("Should handle early vehicles correctly")
        void shouldHandleEarlyVehicles() {
            // Arrange
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1)
            );
            
            List<TripUpdate> updates = Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_001")
                    .delaySeconds(-120) // 2 minutes early
                    .build()
            );
            
            List<VehiclePosition> positions = Collections.emptyList();
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(1, results.size());
            AdherenceStatus result = results.get(0);
            
            assertEquals(AdherenceStatus.AdherenceType.ON_TIME, result.type);
            assertFalse(result.isProblematic());
            assertEquals(-120, result.delaySeconds);
            assertTrue(result.message.contains("early"));
        }
    }
    
    @Nested
    @DisplayName("Integration Scenarios")
    class IntegrationScenariosTests {
        
        @Test
        @DisplayName("Should handle mixed scenario with missing, late, and on-time vehicles")
        void shouldHandleMixedScenario() {
            // Arrange
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_001", "ROUTE_15", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1), // Will be missing
                new ScheduledTrip("TRIP_002", "ROUTE_20", "SERVICE_1", 
                    LocalTime.of(8, 45), "STOP_002", 1), // Will be late
                new ScheduledTrip("TRIP_003", "ROUTE_25", "SERVICE_1", 
                    LocalTime.of(9, 00), "STOP_003", 1)  // Will be on time
            );
            
            List<TripUpdate> updates = Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_002")
                    .delaySeconds(300) // 5 minutes late
                    .build(),
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_003")
                    .delaySeconds(30) // 30 seconds late
                    .build()
            );
            
            List<VehiclePosition> positions = Arrays.asList(
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_002")
                    .build(),
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_003")
                    .build()
            );
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(3, results.size());
            
            // Check missing trip
            AdherenceStatus missing = results.stream()
                .filter(r -> r.tripId.equals("TRIP_001"))
                .findFirst().orElseThrow();
            assertEquals(AdherenceStatus.AdherenceType.MISSING, missing.type);
            assertTrue(missing.isProblematic());
            
            // Check late trip
            AdherenceStatus late = results.stream()
                .filter(r -> r.tripId.equals("TRIP_002"))
                .findFirst().orElseThrow();
            assertEquals(AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE, late.type);
            assertTrue(late.isProblematic());
            
            // Check on-time trip
            AdherenceStatus onTime = results.stream()
                .filter(r -> r.tripId.equals("TRIP_003"))
                .findFirst().orElseThrow();
            assertEquals(AdherenceStatus.AdherenceType.ON_TIME, onTime.type);
            assertFalse(onTime.isProblematic());
            
            // Count problematic vehicles
            long problematicCount = results.stream()
                .filter(AdherenceStatus::isProblematic)
                .count();
            assertEquals(2, problematicCount);
        }
        
        @Test
        @DisplayName("Should generate comprehensive adherence report")
        void shouldGenerateComprehensiveAdherenceReport() {
            // Arrange - Create a realistic scenario
            List<ScheduledTrip> scheduled = Arrays.asList(
                new ScheduledTrip("TRIP_A01", "A", "SERVICE_1", LocalTime.of(8, 15), "STOP_001", 1),
                new ScheduledTrip("TRIP_B01", "B", "SERVICE_1", LocalTime.of(8, 20), "STOP_002", 1),
                new ScheduledTrip("TRIP_C01", "C", "SERVICE_1", LocalTime.of(8, 25), "STOP_003", 1),
                new ScheduledTrip("TRIP_15_01", "15", "SERVICE_1", LocalTime.of(8, 30), "STOP_004", 1),
                new ScheduledTrip("TRIP_20_01", "20", "SERVICE_1", LocalTime.of(8, 35), "STOP_005", 1)
            );
            
            long currentTime = Instant.now().toEpochMilli();
            
            List<TripUpdate> updates = Arrays.asList(
                // A-Line: On time
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A01").routeId("A").delaySeconds(45).build(),
                // B-Line: Significantly late
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_B01").routeId("B").delaySeconds(420).build(), // 7 minutes
                // Route 15: Slightly late
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_15_01").routeId("15").delaySeconds(150).build() // 2.5 minutes
            );
            
            List<VehiclePosition> positions = Arrays.asList(
                // A-Line vehicle
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_A01").routeId("A").vehicleId("A_TRAIN_01").timestamp(currentTime).build(),
                // B-Line vehicle
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_B01").routeId("B").vehicleId("B_TRAIN_01").timestamp(currentTime).build(),
                // C-Line vehicle (old position - should be flagged as missing)
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_C01").routeId("C").vehicleId("C_TRAIN_01")
                    .timestamp(currentTime - (12 * 60 * 1000)).build(), // 12 minutes old
                // Route 15 vehicle
                TestDataBuilder.validVehiclePosition()
                    .tripId("TRIP_15_01").routeId("15").vehicleId("BUS_1501").timestamp(currentTime).build()
            );
            
            // Act
            ScheduleAdherenceAnalyzer analyzer = new ScheduleAdherenceAnalyzer();
            List<AdherenceStatus> results = analyzer.analyzeAdherence(positions, updates, scheduled);
            
            // Assert
            assertEquals(5, results.size());
            
            // Generate report
            Map<AdherenceStatus.AdherenceType, Long> summary = results.stream()
                .collect(Collectors.groupingBy(r -> r.type, Collectors.counting()));
            
            System.out.println("\n=== RTD Schedule Adherence Report ===");
            System.out.println("Total scheduled trips: " + scheduled.size());
            System.out.println("On-time: " + summary.getOrDefault(AdherenceStatus.AdherenceType.ON_TIME, 0L));
            System.out.println("Slightly late: " + summary.getOrDefault(AdherenceStatus.AdherenceType.SLIGHTLY_LATE, 0L));
            System.out.println("Significantly late (3+ min): " + summary.getOrDefault(AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE, 0L));
            System.out.println("Missing: " + summary.getOrDefault(AdherenceStatus.AdherenceType.MISSING, 0L));
            
            System.out.println("\n=== Problematic Vehicles ===");
            results.stream()
                .filter(AdherenceStatus::isProblematic)
                .forEach(r -> System.out.printf("Route %s, Trip %s: %s - %s%n", 
                    r.routeId, r.tripId, r.type, r.message));
            
            // Verify specific expectations
            long problematicCount = results.stream().filter(AdherenceStatus::isProblematic).count();
            assertTrue(problematicCount >= 2, "Should have at least 2 problematic vehicles");
            
            // Verify we have different types
            assertTrue(summary.containsKey(AdherenceStatus.AdherenceType.ON_TIME));
            assertTrue(summary.containsKey(AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE));
            assertTrue(summary.containsKey(AdherenceStatus.AdherenceType.MISSING));
        }
    }
}