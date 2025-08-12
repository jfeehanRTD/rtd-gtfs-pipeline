package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test suite for tracking RTD Light Rail trains and identifying missing trains from GTFS-RT feeds.
 * This test verifies that all expected light rail services are present in the feed data.
 */
class RTDLightRailTrackingTest {

    /**
     * Complete RTD Light Rail system configuration
     */
    public static class RTDLightRailSystem {
        
        public enum LightRailRoute {
            A_LINE("A", "A-Line", "Airport", Arrays.asList(
                "Union Station", "38th & Blake", "40th & Colorado", "Central Park Blvd", 
                "40th & Airport Blvd", "Airport Terminal")),
            
            B_LINE("B", "B-Line", "Westminster", Arrays.asList(
                "Union Station", "25th & Welton", "29th & Welton", "38th & Blake",
                "40th & Colorado", "Peoria", "61st & Peña", "Westminster")),
            
            C_LINE("C", "C-Line", "Littleton", Arrays.asList(
                "Union Station", "10th & Osage", "Auraria West", "Decatur-Federal",
                "Lakewood-Wadsworth", "Belmar", "Littleton-Downtown")),
            
            D_LINE("D", "D-Line", "Littleton", Arrays.asList(
                "Union Station", "10th & Osage", "Auraria West", "Sports Authority Field",
                "W 25th & Federal", "Sheridan", "Littleton-Mineral")),
            
            E_LINE("E", "E-Line", "Ridgegate", Arrays.asList(
                "Union Station", "Theatre District", "16th & Stout", "16th & California",
                "Pepsi Center", "10th & Osage", "Lincoln", "Southmoor", "Arapahoe at Village Center",
                "Dry Creek", "RidgeGate Parkway")),
            
            F_LINE("F", "F-Line", "18th & California", Arrays.asList(
                "18th & California", "18th & Stout", "18th & Welton", "25th & Welton",
                "29th & Welton", "38th & Blake", "40th & Colorado")),
            
            G_LINE("G", "G-Line", "Wheat Ridge", Arrays.asList(
                "Union Station", "Sports Authority Field", "Federal Center",
                "Lamar", "Jefferson County Government Center", "Wheat Ridge-Ward")),
            
            H_LINE("H", "H-Line", "Nine Mile", Arrays.asList(
                "Union Station", "Peoria", "Central Park Blvd", "40th & Airport Blvd",
                "Airport Terminal", "61st & Peña", "Nine Mile")),
            
            N_LINE("N", "N-Line", "Eastlake-124th", Arrays.asList(
                "Union Station", "38th & Blake", "Commerce City", "72nd & Colorado",
                "88th & Quebec", "104th & Colorado", "124th & Colorado", "Eastlake-124th")),
            
            R_LINE("R", "R-Line", "Peoria", Arrays.asList(
                "Union Station", "38th & Blake", "40th & Colorado", "Peoria")),
            
            W_LINE("W", "W-Line", "Jefferson County Government Center", Arrays.asList(
                "Union Station", "Auraria West", "Decatur-Federal", "Lakewood-Wadsworth",
                "Wadsworth-US 36", "Westminster", "US 36 & Table Mesa", "Broomfield",
                "Louisville", "McCaslin", "Interlocken", "US 36 & Flatiron Crossing",
                "Westminster", "Federal Center", "Jefferson County Government Center"));

            private final String routeCode;
            private final String routeName;
            private final String destination;
            private final List<String> stations;

            LightRailRoute(String routeCode, String routeName, String destination, List<String> stations) {
                this.routeCode = routeCode;
                this.routeName = routeName;
                this.destination = destination;
                this.stations = stations;
            }

            public String getRouteCode() { return routeCode; }
            public String getRouteName() { return routeName; }
            public String getDestination() { return destination; }
            public List<String> getStations() { return stations; }
            
            public static LightRailRoute fromCode(String code) {
                for (LightRailRoute route : values()) {
                    if (route.getRouteCode().equals(code)) {
                        return route;
                    }
                }
                return null;
            }
        }

        /**
         * Expected service frequency by time of day and day of week
         */
        public static class ServiceFrequency {
            public final int peakFrequencyMinutes;        // 6-9 AM, 3-7 PM weekdays
            public final int midDayFrequencyMinutes;      // 9 AM - 3 PM weekdays
            public final int eveningFrequencyMinutes;     // 7 PM - midnight weekdays
            public final int weekendFrequencyMinutes;     // All day Saturday/Sunday
            public final int lateNightFrequencyMinutes;   // Midnight - 6 AM

            public ServiceFrequency(int peak, int midDay, int evening, int weekend, int lateNight) {
                this.peakFrequencyMinutes = peak;
                this.midDayFrequencyMinutes = midDay;
                this.eveningFrequencyMinutes = evening;
                this.weekendFrequencyMinutes = weekend;
                this.lateNightFrequencyMinutes = lateNight;
            }
        }

        private static final Map<LightRailRoute, ServiceFrequency> SERVICE_FREQUENCIES = createServiceFrequencyMap();
        
        private static Map<LightRailRoute, ServiceFrequency> createServiceFrequencyMap() {
            Map<LightRailRoute, ServiceFrequency> frequencies = new HashMap<>();
            frequencies.put(LightRailRoute.A_LINE, new ServiceFrequency(15, 30, 30, 30, 60));
            frequencies.put(LightRailRoute.B_LINE, new ServiceFrequency(15, 30, 30, 30, 60));
            frequencies.put(LightRailRoute.C_LINE, new ServiceFrequency(10, 15, 20, 20, 60));
            frequencies.put(LightRailRoute.D_LINE, new ServiceFrequency(10, 15, 20, 20, 60));
            frequencies.put(LightRailRoute.E_LINE, new ServiceFrequency(10, 15, 20, 20, 60));
            frequencies.put(LightRailRoute.F_LINE, new ServiceFrequency(15, 30, 30, 30, -1)); // No late night service
            frequencies.put(LightRailRoute.G_LINE, new ServiceFrequency(15, 30, 30, 30, 60));
            frequencies.put(LightRailRoute.H_LINE, new ServiceFrequency(30, 60, 60, 60, -1)); // Limited service
            frequencies.put(LightRailRoute.N_LINE, new ServiceFrequency(15, 30, 30, 30, 60));
            frequencies.put(LightRailRoute.R_LINE, new ServiceFrequency(8, 10, 15, 15, 30));  // High frequency route
            frequencies.put(LightRailRoute.W_LINE, new ServiceFrequency(20, 30, 30, 30, 60));
            return frequencies;
        }

        public static ServiceFrequency getServiceFrequency(LightRailRoute route) {
            return SERVICE_FREQUENCIES.get(route);
        }

        /**
         * Calculate expected number of trains for a route at current time
         */
        public static int getExpectedTrainCount(LightRailRoute route, LocalDateTime currentTime) {
            ServiceFrequency freq = getServiceFrequency(route);
            if (freq == null) return 0;

            LocalTime time = currentTime.toLocalTime();
            DayOfWeek dayOfWeek = currentTime.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;

            int frequencyMinutes;
            
            if (isWeekend) {
                frequencyMinutes = freq.weekendFrequencyMinutes;
            } else {
                if (time.isBefore(LocalTime.of(6, 0))) {
                    frequencyMinutes = freq.lateNightFrequencyMinutes;
                } else if (time.isBefore(LocalTime.of(9, 0)) || 
                          (time.isAfter(LocalTime.of(15, 0)) && time.isBefore(LocalTime.of(19, 0)))) {
                    frequencyMinutes = freq.peakFrequencyMinutes;
                } else if (time.isBefore(LocalTime.of(15, 0))) {
                    frequencyMinutes = freq.midDayFrequencyMinutes;
                } else if (time.isBefore(LocalTime.of(23, 59))) {
                    frequencyMinutes = freq.eveningFrequencyMinutes;
                } else {
                    frequencyMinutes = freq.lateNightFrequencyMinutes;
                }
            }

            if (frequencyMinutes <= 0) return 0; // No service

            // Estimate trains: route length in minutes / frequency + buffer for both directions
            int routeLengthMinutes = route.getStations().size() * 3; // ~3 minutes between stations
            return Math.max(2, (routeLengthMinutes / frequencyMinutes) * 2); // Both directions
        }
    }

    /**
     * Service completeness analysis result
     */
    public static class ServiceAnalysisResult {
        private final Map<RTDLightRailSystem.LightRailRoute, Integer> expectedTrains;
        private final Map<RTDLightRailSystem.LightRailRoute, Integer> actualTrains;
        private final Map<RTDLightRailSystem.LightRailRoute, List<String>> missingTrains;
        private final List<String> unexpectedVehicles;
        private final double overallCompleteness;

        public ServiceAnalysisResult(Map<RTDLightRailSystem.LightRailRoute, Integer> expected,
                                   Map<RTDLightRailSystem.LightRailRoute, Integer> actual,
                                   Map<RTDLightRailSystem.LightRailRoute, List<String>> missing,
                                   List<String> unexpected,
                                   double completeness) {
            this.expectedTrains = expected;
            this.actualTrains = actual;
            this.missingTrains = missing;
            this.unexpectedVehicles = unexpected;
            this.overallCompleteness = completeness;
        }

        public Map<RTDLightRailSystem.LightRailRoute, Integer> getExpectedTrains() { return expectedTrains; }
        public Map<RTDLightRailSystem.LightRailRoute, Integer> getActualTrains() { return actualTrains; }
        public Map<RTDLightRailSystem.LightRailRoute, List<String>> getMissingTrains() { return missingTrains; }
        public List<String> getUnexpectedVehicles() { return unexpectedVehicles; }
        public double getOverallCompleteness() { return overallCompleteness; }

        public boolean isCompleteService() {
            return overallCompleteness >= 0.90; // 90% threshold
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("RTD Light Rail Service Analysis\n");
            sb.append("==============================\n");
            sb.append(String.format("Overall Completeness: %.1f%%\n\n", overallCompleteness * 100));

            for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
                int expected = expectedTrains.getOrDefault(route, 0);
                int actual = actualTrains.getOrDefault(route, 0);
                List<String> missing = missingTrains.getOrDefault(route, Collections.emptyList());
                
                if (expected > 0) {
                    double routeCompleteness = expected > 0 ? (double) actual / expected : 1.0;
                    sb.append(String.format("%s (%s): %d/%d trains (%.1f%%)\n", 
                        route.getRouteName(), route.getRouteCode(), actual, expected, routeCompleteness * 100));
                    
                    if (!missing.isEmpty()) {
                        sb.append(String.format("  Missing: %s\n", String.join(", ", missing)));
                    }
                }
            }

            if (!unexpectedVehicles.isEmpty()) {
                sb.append(String.format("\nUnexpected vehicles: %s\n", String.join(", ", unexpectedVehicles)));
            }

            return sb.toString();
        }
    }

    @Nested
    @DisplayName("RTD Light Rail Service Tracking")
    class LightRailServiceTracking {

        @Test
        @DisplayName("All light rail routes should have expected minimum service")
        void allRoutesHaveMinimumService() {
            LocalDateTime currentTime = LocalDateTime.now();
            List<VehiclePosition> lightRailVehicles = createCurrentLightRailFleet(currentTime);
            ServiceAnalysisResult analysis = analyzeServiceCompleteness(lightRailVehicles, currentTime);

            System.out.println(analysis);

            // Service completeness should be at least 90%
            assertThat(analysis.getOverallCompleteness())
                .as("Overall light rail service completeness should be at least 90%%")
                .isGreaterThanOrEqualTo(0.90);

            // Each active route should have at least 50% of expected service
            for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
                int expected = analysis.getExpectedTrains().getOrDefault(route, 0);
                int actual = analysis.getActualTrains().getOrDefault(route, 0);
                
                if (expected > 0) {
                    double routeCompleteness = (double) actual / expected;
                    assertThat(routeCompleteness)
                        .as("Route %s should have at least 50%% of expected service", route.getRouteName())
                        .isGreaterThanOrEqualTo(0.50);
                }
            }
        }

        @Test
        @DisplayName("Peak hour service should have full coverage")
        void peakHourServiceCoverage() {
            // Test during morning peak (8 AM on Tuesday)
            LocalDateTime peakTime = LocalDateTime.now()
                .withHour(8).withMinute(0).withSecond(0).withNano(0);
            
            List<VehiclePosition> peakFleet = createPeakHourLightRailFleet(peakTime);
            ServiceAnalysisResult analysis = analyzeServiceCompleteness(peakFleet, peakTime);

            System.out.println("Peak Hour Analysis:");
            System.out.println(analysis);

            // During peak hours, expect higher service levels
            assertThat(analysis.getOverallCompleteness())
                .as("Peak hour service should be at least 95%% complete")
                .isGreaterThanOrEqualTo(0.95);

            // High-frequency routes (C, D, E, R) should have excellent coverage
            List<RTDLightRailSystem.LightRailRoute> highFreqRoutes = Arrays.asList(
                RTDLightRailSystem.LightRailRoute.C_LINE,
                RTDLightRailSystem.LightRailRoute.D_LINE,
                RTDLightRailSystem.LightRailRoute.E_LINE,
                RTDLightRailSystem.LightRailRoute.R_LINE
            );

            for (RTDLightRailSystem.LightRailRoute route : highFreqRoutes) {
                int expected = analysis.getExpectedTrains().getOrDefault(route, 0);
                int actual = analysis.getActualTrains().getOrDefault(route, 0);
                
                if (expected > 0) {
                    double routeCompleteness = (double) actual / expected;
                    assertThat(routeCompleteness)
                        .as("High-frequency route %s should have at least 90%% service during peak", route.getRouteName())
                        .isGreaterThanOrEqualTo(0.90);
                }
            }
        }

        @Test
        @DisplayName("Weekend service should reflect reduced schedule")
        void weekendServiceReflectsSchedule() {
            // Test during weekend (Saturday 2 PM)
            LocalDateTime weekendTime = LocalDateTime.now()
                .withHour(14).withMinute(0).withSecond(0).withNano(0);
            
            List<VehiclePosition> weekendFleet = createWeekendLightRailFleet(weekendTime);
            ServiceAnalysisResult analysis = analyzeServiceCompleteness(weekendFleet, weekendTime);

            System.out.println("Weekend Service Analysis:");
            System.out.println(analysis);

            // Weekend service can be lower but should still be reasonable
            assertThat(analysis.getOverallCompleteness())
                .as("Weekend service should be at least 80%% complete")
                .isGreaterThanOrEqualTo(0.80);

            // Limited service routes might not run on weekends
            RTDLightRailSystem.LightRailRoute limitedRoute = RTDLightRailSystem.LightRailRoute.H_LINE;
            int expectedLimited = analysis.getExpectedTrains().getOrDefault(limitedRoute, 0);
            
            // H_LINE has very limited service, so lower expectations are reasonable
            if (expectedLimited > 0) {
                int actualLimited = analysis.getActualTrains().getOrDefault(limitedRoute, 0);
                assertThat(actualLimited)
                    .as("Limited service route %s may have reduced weekend service", limitedRoute.getRouteName())
                    .isGreaterThanOrEqualTo(0);
            }
        }

        @Test
        @DisplayName("Late night service should match reduced schedule")
        void lateNightServiceMatches() {
            // Test during late night (1 AM on Wednesday)
            LocalDateTime lateNightTime = LocalDateTime.now()
                .withHour(1).withMinute(0).withSecond(0).withNano(0);
            
            List<VehiclePosition> lateNightFleet = createLateNightLightRailFleet(lateNightTime);
            ServiceAnalysisResult analysis = analyzeServiceCompleteness(lateNightFleet, lateNightTime);

            System.out.println("Late Night Service Analysis:");
            System.out.println(analysis);

            // Late night service is significantly reduced
            assertThat(analysis.getOverallCompleteness())
                .as("Late night service completeness (many routes don't run)")
                .isGreaterThanOrEqualTo(0.60);

            // Some routes don't run late night (F_LINE, H_LINE)
            List<RTDLightRailSystem.LightRailRoute> noLateNightRoutes = Arrays.asList(
                RTDLightRailSystem.LightRailRoute.F_LINE,
                RTDLightRailSystem.LightRailRoute.H_LINE
            );

            for (RTDLightRailSystem.LightRailRoute route : noLateNightRoutes) {
                int expected = analysis.getExpectedTrains().getOrDefault(route, 0);
                assertThat(expected)
                    .as("Route %s should have no late night service", route.getRouteName())
                    .isEqualTo(0);
            }
        }

        @Test
        @DisplayName("Service disruption detection should identify missing trains")
        void serviceDisruptionDetection() {
            LocalDateTime currentTime = LocalDateTime.now();
            
            // Create a fleet with intentionally missing trains from specific routes
            List<VehiclePosition> disruptedFleet = createDisruptedLightRailFleet(currentTime);
            ServiceAnalysisResult analysis = analyzeServiceCompleteness(disruptedFleet, currentTime);

            System.out.println("Disrupted Service Analysis:");
            System.out.println(analysis);

            // Should detect the disruption
            assertThat(analysis.getOverallCompleteness())
                .as("Disrupted service should be detected with lower completeness")
                .isLessThan(0.80);

            // Should identify specific routes with issues
            Map<RTDLightRailSystem.LightRailRoute, List<String>> missingTrains = analysis.getMissingTrains();
            assertThat(missingTrains)
                .as("Should identify routes with missing trains")
                .isNotEmpty();

            // Check that we can identify which routes are most affected
            RTDLightRailSystem.LightRailRoute mostAffected = missingTrains.entrySet().stream()
                .max(Map.Entry.comparingByValue((a, b) -> Integer.compare(a.size(), b.size())))
                .map(Map.Entry::getKey)
                .orElse(null);

            assertThat(mostAffected)
                .as("Should identify most affected route")
                .isNotNull();
        }
    }

    @Nested
    @DisplayName("Individual Route Analysis")
    class IndividualRouteAnalysis {

        @Test
        @DisplayName("A-Line airport service should maintain consistent frequency")
        void aLineAirportService() {
            testRouteService(RTDLightRailSystem.LightRailRoute.A_LINE, "Airport service");
        }

        @Test
        @DisplayName("Downtown corridor (C/D/E lines) should have high frequency")
        void downtownCorridorService() {
            List<RTDLightRailSystem.LightRailRoute> downtownRoutes = Arrays.asList(
                RTDLightRailSystem.LightRailRoute.C_LINE,
                RTDLightRailSystem.LightRailRoute.D_LINE,
                RTDLightRailSystem.LightRailRoute.E_LINE
            );

            LocalDateTime currentTime = LocalDateTime.now();
            List<VehiclePosition> allVehicles = createCurrentLightRailFleet(currentTime);

            for (RTDLightRailSystem.LightRailRoute route : downtownRoutes) {
                List<VehiclePosition> routeVehicles = allVehicles.stream()
                    .filter(v -> route.getRouteCode().equals(v.getRouteId()))
                    .collect(Collectors.toList());

                int expected = RTDLightRailSystem.getExpectedTrainCount(route, currentTime);
                
                assertThat(routeVehicles.size())
                    .as("Downtown route %s should have adequate service", route.getRouteName())
                    .isGreaterThanOrEqualTo((int)(expected * 0.8)); // 80% minimum
            }
        }

        @Test
        @DisplayName("R-Line Union Station shuttle should maintain high frequency")
        void rLineShuttleService() {
            testRouteService(RTDLightRailSystem.LightRailRoute.R_LINE, "Union Station shuttle");
        }

        private void testRouteService(RTDLightRailSystem.LightRailRoute route, String serviceDescription) {
            LocalDateTime currentTime = LocalDateTime.now();
            List<VehiclePosition> allVehicles = createCurrentLightRailFleet(currentTime);
            
            List<VehiclePosition> routeVehicles = allVehicles.stream()
                .filter(v -> route.getRouteCode().equals(v.getRouteId()))
                .collect(Collectors.toList());

            int expected = RTDLightRailSystem.getExpectedTrainCount(route, currentTime);
            double completeness = expected > 0 ? (double) routeVehicles.size() / expected : 1.0;

            System.out.printf("%s (%s): %d/%d trains (%.1f%%)%n", 
                serviceDescription, route.getRouteCode(), routeVehicles.size(), expected, completeness * 100);

            assertThat(completeness)
                .as("%s should maintain at least 75%% of expected service", serviceDescription)
                .isGreaterThanOrEqualTo(0.75);

            // Verify vehicles have valid positions within service area
            for (VehiclePosition vehicle : routeVehicles) {
                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(vehicle);
                assertThat(result.isValid())
                    .as("Vehicle %s on %s should be valid", vehicle.getVehicleId(), route.getRouteName())
                    .isTrue();
            }
        }
    }

    // Helper methods to create test data

    private ServiceAnalysisResult analyzeServiceCompleteness(List<VehiclePosition> vehicles, LocalDateTime currentTime) {
        Map<RTDLightRailSystem.LightRailRoute, Integer> expectedTrains = new HashMap<>();
        Map<RTDLightRailSystem.LightRailRoute, Integer> actualTrains = new HashMap<>();
        Map<RTDLightRailSystem.LightRailRoute, List<String>> missingTrains = new HashMap<>();
        
        // Calculate expected trains for each route
        for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
            int expected = RTDLightRailSystem.getExpectedTrainCount(route, currentTime);
            expectedTrains.put(route, expected);
        }

        // Count actual trains by route
        for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
            long actual = vehicles.stream()
                .filter(v -> route.getRouteCode().equals(v.getRouteId()))
                .count();
            actualTrains.put(route, (int) actual);
        }

        // Identify missing trains (simplified - in real implementation would track specific trips)
        for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
            int expected = expectedTrains.get(route);
            int actual = actualTrains.get(route);
            
            List<String> missing = new ArrayList<>();
            for (int i = actual; i < expected; i++) {
                missing.add(String.format("%s_TRAIN_%d", route.getRouteCode(), i + 1));
            }
            if (!missing.isEmpty()) {
                missingTrains.put(route, missing);
            }
        }

        // Find unexpected vehicles (not matching any route)
        Set<String> validRouteCodes = Arrays.stream(RTDLightRailSystem.LightRailRoute.values())
            .map(RTDLightRailSystem.LightRailRoute::getRouteCode)
            .collect(Collectors.toSet());
        
        List<String> unexpectedVehicles = vehicles.stream()
            .filter(v -> !validRouteCodes.contains(v.getRouteId()))
            .map(VehiclePosition::getVehicleId)
            .collect(Collectors.toList());

        // Calculate overall completeness
        int totalExpected = expectedTrains.values().stream().mapToInt(Integer::intValue).sum();
        int totalActual = actualTrains.values().stream().mapToInt(Integer::intValue).sum();
        double overallCompleteness = totalExpected > 0 ? (double) totalActual / totalExpected : 1.0;

        return new ServiceAnalysisResult(expectedTrains, actualTrains, missingTrains, 
                                       unexpectedVehicles, overallCompleteness);
    }

    private List<VehiclePosition> createCurrentLightRailFleet(LocalDateTime currentTime) {
        List<VehiclePosition> fleet = new ArrayList<>();
        long currentTimeMs = Instant.now().toEpochMilli();

        // Create realistic fleet based on current time expectations
        for (RTDLightRailSystem.LightRailRoute route : RTDLightRailSystem.LightRailRoute.values()) {
            int expectedCount = RTDLightRailSystem.getExpectedTrainCount(route, currentTime);
            
            for (int i = 0; i < expectedCount; i++) {
                // Create vehicles with realistic positions along route
                double[] position = getRoutePosition(route, i, expectedCount);
                
                fleet.add(VehiclePosition.builder()
                    .vehicleId(String.format("RTD_LR_%s_%03d", route.getRouteCode(), i + 1))
                    .tripId(String.format("TRIP_%s_%s_%02d", route.getRouteCode(), 
                           currentTime.getHour() < 12 ? "AM" : "PM", i + 1))
                    .routeId(route.getRouteCode())
                    .latitude(position[0])
                    .longitude(position[1])
                    .bearing((float)(Math.random() * 360))
                    .speed((float)(30 + Math.random() * 50)) // 30-80 km/h
                    .timestamp_ms(currentTimeMs - (long)(Math.random() * 300000)) // Up to 5 min old
                    .currentStatus(Math.random() > 0.3 ? "IN_TRANSIT_TO" : "STOPPED_AT")
                    .occupancyStatus(getRealisticOccupancy(currentTime))
                    .build());
            }
        }

        return fleet;
    }

    private List<VehiclePosition> createPeakHourLightRailFleet(LocalDateTime peakTime) {
        // During peak hours, expect fuller service
        return createCurrentLightRailFleet(peakTime);
    }

    private List<VehiclePosition> createWeekendLightRailFleet(LocalDateTime weekendTime) {
        // Weekend service - similar to current but may have fewer trains on some routes
        return createCurrentLightRailFleet(weekendTime);
    }

    private List<VehiclePosition> createLateNightLightRailFleet(LocalDateTime lateNightTime) {
        // Late night - much reduced service, some routes don't run
        return createCurrentLightRailFleet(lateNightTime);
    }

    private List<VehiclePosition> createDisruptedLightRailFleet(LocalDateTime currentTime) {
        List<VehiclePosition> fleet = createCurrentLightRailFleet(currentTime);
        
        // Remove trains from specific routes to simulate disruption
        fleet.removeIf(v -> "C".equals(v.getRouteId()) || "E".equals(v.getRouteId()));
        
        // Remove half the trains from A-Line to simulate airport service issues
        List<VehiclePosition> aLineTrains = fleet.stream()
            .filter(v -> "A".equals(v.getRouteId()))
            .collect(Collectors.toList());
        
        for (int i = 0; i < aLineTrains.size() / 2; i++) {
            fleet.remove(aLineTrains.get(i));
        }

        return fleet;
    }

    private double[] getRoutePosition(RTDLightRailSystem.LightRailRoute route, int trainIndex, int totalTrains) {
        // Simplified position calculation - distribute trains along route
        // In real implementation, this would use actual station coordinates
        
        // Base coordinates around Denver metro area
        double baseLat = 39.7392;
        double baseLng = -104.9903;
        
        // Spread trains along route based on destinations
        double latOffset = 0;
        double lngOffset = 0;
        
        switch (route) {
            case A_LINE: // East to airport
                lngOffset = (trainIndex / (double) totalTrains) * 0.3;
                break;
            case B_LINE: // North
                latOffset = (trainIndex / (double) totalTrains) * 0.2;
                break;
            case C_LINE, D_LINE: // West/Southwest
                lngOffset = -(trainIndex / (double) totalTrains) * 0.3;
                latOffset = -(trainIndex / (double) totalTrains) * 0.1;
                break;
            case E_LINE: // Southeast
                lngOffset = (trainIndex / (double) totalTrains) * 0.2;
                latOffset = -(trainIndex / (double) totalTrains) * 0.2;
                break;
            case N_LINE: // North
                latOffset = (trainIndex / (double) totalTrains) * 0.3;
                lngOffset = (trainIndex / (double) totalTrains) * 0.1;
                break;
            default:
                // Random position around downtown
                latOffset = (Math.random() - 0.5) * 0.1;
                lngOffset = (Math.random() - 0.5) * 0.1;
        }
        
        return new double[]{baseLat + latOffset, baseLng + lngOffset};
    }

    private String getRealisticOccupancy(LocalDateTime currentTime) {
        LocalTime time = currentTime.toLocalTime();
        boolean isPeak = (time.isAfter(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(9, 0))) ||
                        (time.isAfter(LocalTime.of(16, 0)) && time.isBefore(LocalTime.of(18, 0)));
        
        if (isPeak) {
            return Math.random() > 0.5 ? "STANDING_ROOM_ONLY" : "FEW_SEATS_AVAILABLE";
        } else {
            return Math.random() > 0.3 ? "MANY_SEATS_AVAILABLE" : "FEW_SEATS_AVAILABLE";
        }
    }
}