package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced test suite for detecting RTD service issues including:
 * - Ghost trains (appearing without being scheduled)
 * - Cascading delays and their impact
 * - Schedule recovery detection
 * - Partial service disruptions
 * - Historical pattern analysis
 */
@DisplayName("Enhanced Schedule Adherence Tests")
public class EnhancedScheduleAdherenceTest {
    
    private static final int LATE_THRESHOLD_SECONDS = 180; // 3 minutes
    private static final int SEVERELY_LATE_THRESHOLD_SECONDS = 600; // 10 minutes
    private static final int GHOST_TRAIN_DETECTION_WINDOW_MINUTES = 30;
    
    /**
     * Detects ghost trains - vehicles appearing in real-time data but not in schedule
     */
    public static class GhostTrainDetector {
        
        public static class GhostTrainReport {
            public final String vehicleId;
            public final String routeId;
            public final String tripId;
            public final LocalDateTime detectedTime;
            public final String suspectedReason;
            
            public GhostTrainReport(String vehicleId, String routeId, String tripId, 
                                  LocalDateTime detectedTime, String suspectedReason) {
                this.vehicleId = vehicleId;
                this.routeId = routeId;
                this.tripId = tripId;
                this.detectedTime = detectedTime;
                this.suspectedReason = suspectedReason;
            }
        }
        
        public List<GhostTrainReport> detectGhostTrains(
                List<VehiclePosition> vehicles,
                List<TripUpdate> tripUpdates,
                List<ScheduleAdherenceTest.ScheduledTrip> scheduledTrips) {
            
            List<GhostTrainReport> ghostTrains = new ArrayList<>();
            
            // Get all scheduled trip IDs
            Set<String> scheduledTripIds = scheduledTrips.stream()
                .map(st -> st.tripId)
                .collect(Collectors.toSet());
            
            // Check vehicle positions for unscheduled trips
            for (VehiclePosition vehicle : vehicles) {
                if (vehicle.getTripId() != null && !scheduledTripIds.contains(vehicle.getTripId())) {
                    String suspectedReason = analyzeGhostTrainReason(vehicle, tripUpdates);
                    ghostTrains.add(new GhostTrainReport(
                        vehicle.getVehicleId(),
                        vehicle.getRouteId(),
                        vehicle.getTripId(),
                        LocalDateTime.now(),
                        suspectedReason
                    ));
                }
            }
            
            // Check trip updates for unscheduled trips
            for (TripUpdate update : tripUpdates) {
                if (!scheduledTripIds.contains(update.getTripId())) {
                    boolean alreadyDetected = ghostTrains.stream()
                        .anyMatch(gt -> gt.tripId.equals(update.getTripId()));
                    
                    if (!alreadyDetected) {
                        ghostTrains.add(new GhostTrainReport(
                            update.getVehicleId(),
                            update.getRouteId(),
                            update.getTripId(),
                            LocalDateTime.now(),
                            "Unscheduled trip update"
                        ));
                    }
                }
            }
            
            return ghostTrains;
        }
        
        private String analyzeGhostTrainReason(VehiclePosition vehicle, List<TripUpdate> updates) {
            // Analyze potential reasons for ghost train
            
            // Check if it might be a short-turn or express service
            if (vehicle.getTripId().contains("EXPRESS") || vehicle.getTripId().contains("SHORT")) {
                return "Possible unscheduled express or short-turn service";
            }
            
            // Check if it's a replacement service
            if (vehicle.getTripId().contains("EXTRA") || vehicle.getTripId().contains("SPECIAL")) {
                return "Possible extra service added for crowding or delays";
            }
            
            // Check if there's a corresponding trip update with high delay
            Optional<TripUpdate> relatedUpdate = updates.stream()
                .filter(tu -> tu.getRouteId().equals(vehicle.getRouteId()))
                .filter(tu -> tu.getDelaySeconds() != null && tu.getDelaySeconds() > SEVERELY_LATE_THRESHOLD_SECONDS)
                .findFirst();
            
            if (relatedUpdate.isPresent()) {
                return "Possible replacement service for delayed trip";
            }
            
            return "Unknown - possible operational adjustment";
        }
    }
    
    /**
     * Analyzes cascading delays and their impact on the network
     */
    public static class CascadingDelayAnalyzer {
        
        public static class CascadingDelayReport {
            public final String initialRoute;
            public final int initialDelaySeconds;
            public final Map<String, Integer> impactedRoutes; // route -> average delay
            public final int totalImpactedTrips;
            public final String severity; // MINOR, MODERATE, SEVERE
            
            public CascadingDelayReport(String initialRoute, int initialDelaySeconds,
                                      Map<String, Integer> impactedRoutes, int totalImpactedTrips) {
                this.initialRoute = initialRoute;
                this.initialDelaySeconds = initialDelaySeconds;
                this.impactedRoutes = impactedRoutes;
                this.totalImpactedTrips = totalImpactedTrips;
                this.severity = calculateSeverity();
            }
            
            private String calculateSeverity() {
                if (totalImpactedTrips > 20 || impactedRoutes.size() > 5) {
                    return "SEVERE";
                } else if (totalImpactedTrips > 10 || impactedRoutes.size() > 3) {
                    return "MODERATE";
                } else {
                    return "MINOR";
                }
            }
        }
        
        public List<CascadingDelayReport> analyzeCascadingDelays(List<TripUpdate> tripUpdates) {
            List<CascadingDelayReport> reports = new ArrayList<>();
            
            // Group by route and find routes with significant delays
            Map<String, List<TripUpdate>> updatesByRoute = tripUpdates.stream()
                .collect(Collectors.groupingBy(TripUpdate::getRouteId));
            
            for (Map.Entry<String, List<TripUpdate>> entry : updatesByRoute.entrySet()) {
                String route = entry.getKey();
                List<TripUpdate> routeUpdates = entry.getValue();
                
                // Find the most delayed trip
                Optional<TripUpdate> mostDelayed = routeUpdates.stream()
                    .filter(tu -> tu.getDelaySeconds() != null)
                    .max(Comparator.comparing(TripUpdate::getDelaySeconds));
                
                if (mostDelayed.isPresent() && mostDelayed.get().getDelaySeconds() > SEVERELY_LATE_THRESHOLD_SECONDS) {
                    // Analyze impact on connecting routes
                    Map<String, Integer> impactedRoutes = analyzeConnectingRouteImpact(
                        route, mostDelayed.get().getDelaySeconds(), updatesByRoute);
                    
                    if (!impactedRoutes.isEmpty()) {
                        int totalImpacted = impactedRoutes.values().stream()
                            .mapToInt(delays -> delays)
                            .sum();
                        
                        reports.add(new CascadingDelayReport(
                            route,
                            mostDelayed.get().getDelaySeconds(),
                            impactedRoutes,
                            totalImpacted
                        ));
                    }
                }
            }
            
            return reports;
        }
        
        private Map<String, Integer> analyzeConnectingRouteImpact(
                String delayedRoute, int delaySeconds, Map<String, List<TripUpdate>> updatesByRoute) {
            
            Map<String, Integer> impactedRoutes = new HashMap<>();
            
            // Define route connections (simplified - real implementation would use GTFS transfers)
            Map<String, Set<String>> connections = getRouteConnections();
            
            Set<String> connectedRoutes = connections.getOrDefault(delayedRoute, Collections.emptySet());
            
            for (String connectedRoute : connectedRoutes) {
                List<TripUpdate> connectedUpdates = updatesByRoute.getOrDefault(connectedRoute, Collections.emptyList());
                
                // Calculate average delay for connected route
                double avgDelay = connectedUpdates.stream()
                    .filter(tu -> tu.getDelaySeconds() != null && tu.getDelaySeconds() > 0)
                    .mapToInt(TripUpdate::getDelaySeconds)
                    .average()
                    .orElse(0.0);
                
                // If connected route shows delays, likely impacted
                if (avgDelay > LATE_THRESHOLD_SECONDS) {
                    impactedRoutes.put(connectedRoute, (int) avgDelay);
                }
            }
            
            return impactedRoutes;
        }
        
        private Map<String, Set<String>> getRouteConnections() {
            Map<String, Set<String>> connections = new HashMap<>();
            
            // Major transfer points in RTD system
            connections.put("A", new HashSet<>(Arrays.asList("B", "C", "D", "E", "F", "G", "N", "R", "W"))); // Union Station
            connections.put("B", new HashSet<>(Arrays.asList("A", "F")));
            connections.put("C", new HashSet<>(Arrays.asList("A", "D", "E", "W")));
            connections.put("D", new HashSet<>(Arrays.asList("A", "C", "E", "F")));
            connections.put("E", new HashSet<>(Arrays.asList("A", "C", "D", "F")));
            connections.put("F", new HashSet<>(Arrays.asList("A", "B", "D", "E")));
            connections.put("G", new HashSet<>(Arrays.asList("A", "W")));
            connections.put("N", new HashSet<>(Arrays.asList("A")));
            connections.put("R", new HashSet<>(Arrays.asList("A")));
            connections.put("W", new HashSet<>(Arrays.asList("A", "C", "G")));
            
            return connections;
        }
    }
    
    /**
     * Detects schedule recovery patterns
     */
    public static class ScheduleRecoveryDetector {
        
        public static class RecoveryEvent {
            public final String tripId;
            public final String routeId;
            public final int initialDelaySeconds;
            public final int currentDelaySeconds;
            public final int recoverySeconds;
            public final LocalDateTime recoveryStartTime;
            public final boolean isFullyRecovered;
            
            public RecoveryEvent(String tripId, String routeId, int initialDelay, int currentDelay,
                               LocalDateTime recoveryStartTime) {
                this.tripId = tripId;
                this.routeId = routeId;
                this.initialDelaySeconds = initialDelay;
                this.currentDelaySeconds = currentDelay;
                this.recoverySeconds = initialDelay - currentDelay;
                this.recoveryStartTime = recoveryStartTime;
                this.isFullyRecovered = currentDelay <= 60; // Within 1 minute is considered recovered
            }
        }
        
        public List<RecoveryEvent> detectRecoveryEvents(
                Map<String, List<TripUpdate>> historicalUpdates) {
            
            List<RecoveryEvent> recoveryEvents = new ArrayList<>();
            
            for (Map.Entry<String, List<TripUpdate>> entry : historicalUpdates.entrySet()) {
                String tripId = entry.getKey();
                List<TripUpdate> updates = entry.getValue();
                
                if (updates.size() < 2) continue;
                
                // Sort by timestamp
                updates.sort(Comparator.comparing(TripUpdate::getTimestamp));
                
                // Find peak delay
                int maxDelay = updates.stream()
                    .mapToInt(tu -> tu.getDelaySeconds() != null ? tu.getDelaySeconds() : 0)
                    .max()
                    .orElse(0);
                
                if (maxDelay > LATE_THRESHOLD_SECONDS) {
                    // Check if delay decreased
                    TripUpdate latest = updates.get(updates.size() - 1);
                    Integer currentDelay = latest.getDelaySeconds();
                    
                    if (currentDelay != null && currentDelay < maxDelay) {
                        // Find when recovery started
                        LocalDateTime recoveryStart = findRecoveryStartTime(updates, maxDelay);
                        
                        recoveryEvents.add(new RecoveryEvent(
                            tripId,
                            latest.getRouteId(),
                            maxDelay,
                            currentDelay,
                            recoveryStart
                        ));
                    }
                }
            }
            
            return recoveryEvents;
        }
        
        private LocalDateTime findRecoveryStartTime(List<TripUpdate> updates, int maxDelay) {
            for (int i = 0; i < updates.size() - 1; i++) {
                Integer currentDelay = updates.get(i).getDelaySeconds();
                Integer nextDelay = updates.get(i + 1).getDelaySeconds();
                
                if (currentDelay != null && nextDelay != null && 
                    currentDelay >= maxDelay && nextDelay < currentDelay) {
                    return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(updates.get(i).getTimestamp()), 
                        java.time.ZoneOffset.UTC);
                }
            }
            
            return LocalDateTime.now();
        }
    }
    
    @Nested
    @DisplayName("Ghost Train Detection")
    class GhostTrainDetectionTests {
        
        @Test
        @DisplayName("Should detect vehicles operating without scheduled trips")
        void shouldDetectGhostTrains() {
            // Arrange
            List<ScheduleAdherenceTest.ScheduledTrip> scheduled = Arrays.asList(
                new ScheduleAdherenceTest.ScheduledTrip("TRIP_A_001", "A", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1),
                new ScheduleAdherenceTest.ScheduledTrip("TRIP_B_001", "B", "SERVICE_1", 
                    LocalTime.of(8, 45), "STOP_002", 1)
            );
            
            List<VehiclePosition> vehicles = Arrays.asList(
                // Scheduled vehicle
                TestDataBuilder.validVehiclePosition()
                    .vehicleId("TRAIN_A_01")
                    .tripId("TRIP_A_001")
                    .routeId("A")
                    .build(),
                // Ghost train - not in schedule
                TestDataBuilder.validVehiclePosition()
                    .vehicleId("TRAIN_C_GHOST")
                    .tripId("TRIP_C_EXTRA_001")
                    .routeId("C")
                    .build(),
                // Another ghost train
                TestDataBuilder.validVehiclePosition()
                    .vehicleId("TRAIN_E_SPECIAL")
                    .tripId("TRIP_E_EXPRESS_001")
                    .routeId("E")
                    .build()
            );
            
            List<TripUpdate> updates = Collections.emptyList();
            
            // Act
            GhostTrainDetector detector = new GhostTrainDetector();
            List<GhostTrainDetector.GhostTrainReport> ghostTrains = 
                detector.detectGhostTrains(vehicles, updates, scheduled);
            
            // Assert
            assertEquals(2, ghostTrains.size());
            
            // Verify ghost trains were detected
            assertTrue(ghostTrains.stream()
                .anyMatch(gt -> gt.vehicleId.equals("TRAIN_C_GHOST")));
            assertTrue(ghostTrains.stream()
                .anyMatch(gt -> gt.vehicleId.equals("TRAIN_E_SPECIAL")));
            
            // Check suspected reasons
            GhostTrainDetector.GhostTrainReport expressGhost = ghostTrains.stream()
                .filter(gt -> gt.tripId.contains("EXPRESS"))
                .findFirst()
                .orElseThrow();
            
            assertTrue(expressGhost.suspectedReason.contains("express"));
        }
        
        @Test
        @DisplayName("Should identify replacement services for severely delayed trips")
        void shouldIdentifyReplacementServices() {
            // Arrange
            List<ScheduleAdherenceTest.ScheduledTrip> scheduled = Arrays.asList(
                new ScheduleAdherenceTest.ScheduledTrip("TRIP_A_001", "A", "SERVICE_1", 
                    LocalTime.of(8, 30), "STOP_001", 1)
            );
            
            List<TripUpdate> updates = Arrays.asList(
                // Severely delayed trip
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A_001")
                    .routeId("A")
                    .delaySeconds(1200) // 20 minutes late
                    .build()
            );
            
            List<VehiclePosition> vehicles = Arrays.asList(
                // Ghost train on same route
                TestDataBuilder.validVehiclePosition()
                    .vehicleId("TRAIN_A_REPLACEMENT")
                    .tripId("TRIP_A_EXTRA_001")
                    .routeId("A")
                    .build()
            );
            
            // Act
            GhostTrainDetector detector = new GhostTrainDetector();
            List<GhostTrainDetector.GhostTrainReport> ghostTrains = 
                detector.detectGhostTrains(vehicles, updates, scheduled);
            
            // Assert
            assertEquals(1, ghostTrains.size());
            
            GhostTrainDetector.GhostTrainReport report = ghostTrains.get(0);
            assertTrue(report.suspectedReason.contains("replacement") || 
                      report.suspectedReason.contains("operational adjustment"));
        }
    }
    
    @Nested
    @DisplayName("Cascading Delay Analysis")
    class CascadingDelayTests {
        
        @Test
        @DisplayName("Should detect cascading delays across connected routes")
        void shouldDetectCascadingDelays() {
            // Arrange - simulate Union Station congestion affecting multiple lines
            List<TripUpdate> updates = Arrays.asList(
                // Initial severe delay on A-Line
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A_001").routeId("A").delaySeconds(900).build(), // 15 min
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A_002").routeId("A").delaySeconds(720).build(), // 12 min
                
                // Cascading delays on connecting routes
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_B_001").routeId("B").delaySeconds(480).build(), // 8 min
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_C_001").routeId("C").delaySeconds(360).build(), // 6 min
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_D_001").routeId("D").delaySeconds(420).build(), // 7 min
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_E_001").routeId("E").delaySeconds(300).build(), // 5 min
                
                // Unaffected route
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_N_001").routeId("N").delaySeconds(60).build()  // 1 min
            );
            
            // Act
            CascadingDelayAnalyzer analyzer = new CascadingDelayAnalyzer();
            List<CascadingDelayAnalyzer.CascadingDelayReport> reports = 
                analyzer.analyzeCascadingDelays(updates);
            
            // Assert
            assertFalse(reports.isEmpty());
            
            // Find A-Line cascade report
            CascadingDelayAnalyzer.CascadingDelayReport aLineReport = reports.stream()
                .filter(r -> r.initialRoute.equals("A"))
                .findFirst()
                .orElseThrow();
            
            assertEquals(900, aLineReport.initialDelaySeconds);
            assertTrue(aLineReport.impactedRoutes.size() >= 3);
            assertTrue(aLineReport.severity.equals("MODERATE") || aLineReport.severity.equals("SEVERE"), 
                "Should be MODERATE or SEVERE but was: " + aLineReport.severity);
            
            // Verify impacted routes include major connections
            assertTrue(aLineReport.impactedRoutes.containsKey("B"));
            assertTrue(aLineReport.impactedRoutes.containsKey("C"));
            assertTrue(aLineReport.impactedRoutes.containsKey("D"));
        }
        
        @Test
        @DisplayName("Should classify severe network-wide disruptions")
        void shouldClassifySevereDisruptions() {
            // Arrange - simulate major system-wide delays
            List<TripUpdate> updates = new ArrayList<>();
            
            // Add delays for multiple trips on each route
            String[] routes = {"A", "B", "C", "D", "E", "F", "G", "W"};
            for (String route : routes) {
                for (int i = 0; i < 5; i++) {
                    updates.add(TestDataBuilder.validTripUpdate()
                        .tripId(String.format("TRIP_%s_%03d", route, i))
                        .routeId(route)
                        .delaySeconds(600 + (int)(Math.random() * 600)) // 10-20 minutes
                        .build());
                }
            }
            
            // Act
            CascadingDelayAnalyzer analyzer = new CascadingDelayAnalyzer();
            List<CascadingDelayAnalyzer.CascadingDelayReport> reports = 
                analyzer.analyzeCascadingDelays(updates);
            
            // Assert
            assertFalse(reports.isEmpty());
            
            // Should have multiple severe cascades
            long severeCount = reports.stream()
                .filter(r -> "SEVERE".equals(r.severity))
                .count();
            
            assertTrue(severeCount > 0, "Should detect severe cascading delays");
        }
    }
    
    @Nested
    @DisplayName("Schedule Recovery Detection")
    class ScheduleRecoveryTests {
        
        @Test
        @DisplayName("Should detect when delayed trains recover schedule")
        void shouldDetectScheduleRecovery() {
            // Arrange - simulate a trip that was delayed but recovered
            String tripId = "TRIP_E_001";
            long baseTime = Instant.now().toEpochMilli();
            
            Map<String, List<TripUpdate>> historicalUpdates = new HashMap<>();
            historicalUpdates.put(tripId, Arrays.asList(
                // Initial on-time
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(0)
                    .timestamp(baseTime).build(),
                // Delay develops
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(300)
                    .timestamp(baseTime + 300000).build(), // 5 min later
                // Peak delay
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(600)
                    .timestamp(baseTime + 600000).build(), // 10 min later
                // Recovery begins
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(480)
                    .timestamp(baseTime + 900000).build(), // 15 min later
                // Further recovery
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(240)
                    .timestamp(baseTime + 1200000).build(), // 20 min later
                // Nearly recovered
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("E").delaySeconds(60)
                    .timestamp(baseTime + 1500000).build() // 25 min later
            ));
            
            // Act
            ScheduleRecoveryDetector detector = new ScheduleRecoveryDetector();
            List<ScheduleRecoveryDetector.RecoveryEvent> recoveries = 
                detector.detectRecoveryEvents(historicalUpdates);
            
            // Assert
            assertEquals(1, recoveries.size());
            
            ScheduleRecoveryDetector.RecoveryEvent recovery = recoveries.get(0);
            assertEquals(tripId, recovery.tripId);
            assertEquals(600, recovery.initialDelaySeconds);
            assertEquals(60, recovery.currentDelaySeconds);
            assertEquals(540, recovery.recoverySeconds); // Recovered 9 minutes
            assertTrue(recovery.isFullyRecovered);
        }
        
        @Test
        @DisplayName("Should track partial recovery progress")
        void shouldTrackPartialRecovery() {
            // Arrange - trip that partially recovers but remains delayed
            String tripId = "TRIP_C_001";
            long baseTime = Instant.now().toEpochMilli();
            
            Map<String, List<TripUpdate>> historicalUpdates = new HashMap<>();
            historicalUpdates.put(tripId, Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("C").delaySeconds(900) // 15 min delay
                    .timestamp(baseTime).build(),
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("C").delaySeconds(600) // Recovered to 10 min
                    .timestamp(baseTime + 600000).build(),
                TestDataBuilder.validTripUpdate()
                    .tripId(tripId).routeId("C").delaySeconds(480) // Still 8 min late
                    .timestamp(baseTime + 1200000).build()
            ));
            
            // Act
            ScheduleRecoveryDetector detector = new ScheduleRecoveryDetector();
            List<ScheduleRecoveryDetector.RecoveryEvent> recoveries = 
                detector.detectRecoveryEvents(historicalUpdates);
            
            // Assert
            assertEquals(1, recoveries.size());
            
            ScheduleRecoveryDetector.RecoveryEvent recovery = recoveries.get(0);
            assertEquals(900, recovery.initialDelaySeconds);
            assertEquals(480, recovery.currentDelaySeconds);
            assertEquals(420, recovery.recoverySeconds); // Recovered 7 minutes
            assertFalse(recovery.isFullyRecovered); // Still significantly late
        }
    }
    
    @Nested
    @DisplayName("Comprehensive Service Analysis")
    class ComprehensiveServiceTests {
        
        @Test
        @DisplayName("Should generate comprehensive disruption report")
        void shouldGenerateComprehensiveDisruptionReport() {
            // Arrange - create a complex scenario with multiple issues
            LocalDateTime currentTime = LocalDateTime.now();
            
            // Scheduled trips
            List<ScheduleAdherenceTest.ScheduledTrip> scheduled = createScheduledTrips();
            
            // Real-time data with various issues
            List<VehiclePosition> vehicles = createDisruptedVehiclePositions();
            List<TripUpdate> updates = createDisruptedTripUpdates();
            
            // Historical data for recovery analysis
            Map<String, List<TripUpdate>> historicalUpdates = createHistoricalUpdates();
            
            // Act - Run all detectors
            GhostTrainDetector ghostDetector = new GhostTrainDetector();
            CascadingDelayAnalyzer cascadeAnalyzer = new CascadingDelayAnalyzer();
            ScheduleRecoveryDetector recoveryDetector = new ScheduleRecoveryDetector();
            ScheduleAdherenceTest.ScheduleAdherenceAnalyzer adherenceAnalyzer = 
                new ScheduleAdherenceTest.ScheduleAdherenceAnalyzer();
            
            List<GhostTrainDetector.GhostTrainReport> ghostTrains = 
                ghostDetector.detectGhostTrains(vehicles, updates, scheduled);
            List<CascadingDelayAnalyzer.CascadingDelayReport> cascades = 
                cascadeAnalyzer.analyzeCascadingDelays(updates);
            List<ScheduleRecoveryDetector.RecoveryEvent> recoveries = 
                recoveryDetector.detectRecoveryEvents(historicalUpdates);
            List<ScheduleAdherenceTest.AdherenceStatus> adherence = 
                adherenceAnalyzer.analyzeAdherence(vehicles, updates, scheduled);
            
            // Generate report
            System.out.println("\n=== RTD COMPREHENSIVE SERVICE DISRUPTION REPORT ===");
            System.out.println("Report Time: " + currentTime);
            
            // Summary statistics
            long missingTrains = adherence.stream()
                .filter(a -> a.type == ScheduleAdherenceTest.AdherenceStatus.AdherenceType.MISSING)
                .count();
            long lateTrains = adherence.stream()
                .filter(a -> a.type == ScheduleAdherenceTest.AdherenceStatus.AdherenceType.SIGNIFICANTLY_LATE)
                .count();
            
            System.out.println("\n--- SUMMARY ---");
            System.out.println("Missing Trains: " + missingTrains);
            System.out.println("Significantly Late (3+ min): " + lateTrains);
            System.out.println("Ghost Trains Detected: " + ghostTrains.size());
            System.out.println("Cascading Delay Events: " + cascades.size());
            System.out.println("Recovery Events: " + recoveries.size());
            
            // Ghost trains
            if (!ghostTrains.isEmpty()) {
                System.out.println("\n--- GHOST TRAINS ---");
                ghostTrains.forEach(gt -> System.out.printf(
                    "Route %s: Vehicle %s (Trip %s) - %s%n",
                    gt.routeId, gt.vehicleId, gt.tripId, gt.suspectedReason));
            }
            
            // Cascading delays
            if (!cascades.isEmpty()) {
                System.out.println("\n--- CASCADING DELAYS ---");
                cascades.forEach(c -> System.out.printf(
                    "%s severity: Route %s (%d min delay) impacting %d routes%n",
                    c.severity, c.initialRoute, c.initialDelaySeconds / 60, c.impactedRoutes.size()));
            }
            
            // Recovery progress
            if (!recoveries.isEmpty()) {
                System.out.println("\n--- SCHEDULE RECOVERY ---");
                recoveries.forEach(r -> System.out.printf(
                    "Route %s Trip %s: Recovered %d minutes (%d min -> %d min delay) %s%n",
                    r.routeId, r.tripId, r.recoverySeconds / 60,
                    r.initialDelaySeconds / 60, r.currentDelaySeconds / 60,
                    r.isFullyRecovered ? "[FULLY RECOVERED]" : "[PARTIAL]"));
            }
            
            // Assert comprehensive detection worked
            assertFalse(ghostTrains.isEmpty(), "Should detect ghost trains");
            assertFalse(cascades.isEmpty(), "Should detect cascading delays");
            assertFalse(recoveries.isEmpty(), "Should detect recovery events");
            assertTrue(missingTrains > 0 || lateTrains > 0, "Should detect service issues");
        }
        
        private List<ScheduleAdherenceTest.ScheduledTrip> createScheduledTrips() {
            List<ScheduleAdherenceTest.ScheduledTrip> trips = new ArrayList<>();
            String[] routes = {"A", "B", "C", "D", "E", "F", "G", "N", "R", "W"};
            
            for (String route : routes) {
                for (int i = 0; i < 3; i++) {
                    trips.add(new ScheduleAdherenceTest.ScheduledTrip(
                        String.format("TRIP_%s_%03d", route, i),
                        route,
                        "SERVICE_1",
                        LocalTime.of(8 + i, 15 * i),
                        String.format("STOP_%s_%d", route, i),
                        i + 1
                    ));
                }
            }
            
            return trips;
        }
        
        private List<VehiclePosition> createDisruptedVehiclePositions() {
            List<VehiclePosition> positions = new ArrayList<>();
            long currentTime = Instant.now().toEpochMilli();
            
            // Some scheduled vehicles
            positions.add(TestDataBuilder.validVehiclePosition()
                .vehicleId("TRAIN_A_01").tripId("TRIP_A_001").routeId("A")
                .timestamp(currentTime).build());
            positions.add(TestDataBuilder.validVehiclePosition()
                .vehicleId("TRAIN_B_01").tripId("TRIP_B_001").routeId("B")
                .timestamp(currentTime).build());
            
            // Ghost trains
            positions.add(TestDataBuilder.validVehiclePosition()
                .vehicleId("TRAIN_C_EXTRA").tripId("TRIP_C_EXTRA_001").routeId("C")
                .timestamp(currentTime).build());
            positions.add(TestDataBuilder.validVehiclePosition()
                .vehicleId("TRAIN_E_EXPRESS").tripId("TRIP_E_EXPRESS_001").routeId("E")
                .timestamp(currentTime).build());
            
            // Old position (missing train)
            positions.add(TestDataBuilder.validVehiclePosition()
                .vehicleId("TRAIN_D_01").tripId("TRIP_D_001").routeId("D")
                .timestamp(currentTime - 20 * 60 * 1000).build()); // 20 minutes old
            
            return positions;
        }
        
        private List<TripUpdate> createDisruptedTripUpdates() {
            List<TripUpdate> updates = new ArrayList<>();
            
            // Severe delays on A-Line
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_A_001").routeId("A").delaySeconds(1200).build());
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_A_002").routeId("A").delaySeconds(900).build());
            
            // Cascading delays
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_B_001").routeId("B").delaySeconds(600).build());
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_C_001").routeId("C").delaySeconds(480).build());
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_E_001").routeId("E").delaySeconds(360).build());
            
            // Some on-time services
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_N_001").routeId("N").delaySeconds(30).build());
            updates.add(TestDataBuilder.validTripUpdate()
                .tripId("TRIP_R_001").routeId("R").delaySeconds(60).build());
            
            return updates;
        }
        
        private Map<String, List<TripUpdate>> createHistoricalUpdates() {
            Map<String, List<TripUpdate>> historical = new HashMap<>();
            long baseTime = Instant.now().toEpochMilli() - 30 * 60 * 1000; // 30 min ago
            
            // Trip that recovered
            historical.put("TRIP_E_001", Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_E_001").routeId("E").delaySeconds(600)
                    .timestamp(baseTime).build(),
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_E_001").routeId("E").delaySeconds(360)
                    .timestamp(baseTime + 10 * 60 * 1000).build()
            ));
            
            // Trip that got worse
            historical.put("TRIP_A_001", Arrays.asList(
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A_001").routeId("A").delaySeconds(300)
                    .timestamp(baseTime).build(),
                TestDataBuilder.validTripUpdate()
                    .tripId("TRIP_A_001").routeId("A").delaySeconds(1200)
                    .timestamp(baseTime + 20 * 60 * 1000).build()
            ));
            
            return historical;
        }
    }
}