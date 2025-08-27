package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.*;
import static com.rtd.pipeline.validation.TestDataBuilder.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive data quality test suite for bus occupancy information.
 * Tests various aspects of bus ridership data including accuracy, consistency,
 * completeness, timeliness, and validity of occupancy status values.
 */
class BusOccupancyDataQualityTest {

    // GTFS-RT standard occupancy status values
    private static final Set<String> VALID_OCCUPANCY_STATUSES = Set.of(
        "EMPTY",
        "MANY_SEATS_AVAILABLE", 
        "FEW_SEATS_AVAILABLE",
        "STANDING_ROOM_ONLY",
        "CRUSHED_STANDING_ROOM_ONLY",
        "FULL",
        "NOT_ACCEPTING_PASSENGERS",
        "NO_DATA_AVAILABLE"
    );

    // Occupancy levels mapped to numeric capacity percentage ranges
    private static final Map<String, int[]> OCCUPANCY_CAPACITY_RANGES = Map.of(
        "EMPTY", new int[]{0, 10},
        "MANY_SEATS_AVAILABLE", new int[]{11, 40},
        "FEW_SEATS_AVAILABLE", new int[]{41, 70},
        "STANDING_ROOM_ONLY", new int[]{71, 85},
        "CRUSHED_STANDING_ROOM_ONLY", new int[]{86, 95},
        "FULL", new int[]{96, 100}
    );

    @Nested
    @DisplayName("Occupancy Data Completeness Tests")
    class OccupancyCompletenessTests {

        @Test
        @DisplayName("Should validate all required occupancy fields are present")
        void shouldValidateRequiredOccupancyFields() {
            // Given: Bus with complete occupancy data
            VehiclePosition bus = validVehiclePosition()
                .vehicleId("RTD_BUS_001")
                .routeId("FF2")
                .occupancyStatus("STANDING_ROOM_ONLY")
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            // When: Validating data completeness
            DataQualityResult result = validateOccupancyDataCompleteness(bus);

            // Then: Should pass all completeness checks
            assertThat(result.isComplete()).isTrue();
            assertThat(result.getMissingFields()).isEmpty();
            assertThat(bus.getOccupancyStatus()).isNotNull();
            assertThat(bus.getVehicleId()).isNotNull();
            assertThat(bus.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should detect missing occupancy status")
        void shouldDetectMissingOccupancyStatus() {
            // Given: Bus with missing occupancy status
            VehiclePosition bus = validVehiclePosition()
                .vehicleId("RTD_BUS_002")
                .routeId("15L")
                .occupancyStatus(null)
                .build();

            // When: Validating data completeness
            DataQualityResult result = validateOccupancyDataCompleteness(bus);

            // Then: Should detect missing occupancy field
            assertThat(result.isComplete()).isFalse();
            assertThat(result.getMissingFields()).contains("occupancyStatus");
            assertThat(result.getQualityScore()).isLessThan(1.0);
        }

        @Test
        @DisplayName("Should track occupancy data availability rate across fleet")
        void shouldTrackOccupancyDataAvailabilityRate() {
            // Given: Fleet with mixed data availability
            List<VehiclePosition> fleet = Arrays.asList(
                createBusWithOccupancy("BUS_1", "FULL"),
                createBusWithOccupancy("BUS_2", "STANDING_ROOM_ONLY"),
                createBusWithOccupancy("BUS_3", null),
                createBusWithOccupancy("BUS_4", "NO_DATA_AVAILABLE"),
                createBusWithOccupancy("BUS_5", "FEW_SEATS_AVAILABLE")
            );

            // When: Calculating data availability rate
            double availabilityRate = calculateOccupancyDataAvailabilityRate(fleet);

            // Then: Should correctly calculate availability rate
            assertThat(availabilityRate).isEqualTo(60.0); // 3 out of 5 have valid data
        }
    }

    @Nested
    @DisplayName("Occupancy Data Accuracy Tests")
    class OccupancyAccuracyTests {

        @Test
        @DisplayName("Should validate occupancy status values are within GTFS-RT standard")
        void shouldValidateOccupancyStatusValues() {
            // Given: Various occupancy status values
            String validStatus = "STANDING_ROOM_ONLY";
            String invalidStatus = "PACKED_LIKE_SARDINES";
            String nullStatus = null;

            // When & Then: Validate each status
            assertThat(isValidOccupancyStatus(validStatus)).isTrue();
            assertThat(isValidOccupancyStatus(invalidStatus)).isFalse();
            assertThat(isValidOccupancyStatus(nullStatus)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"EMPTY", "MANY_SEATS_AVAILABLE", "FEW_SEATS_AVAILABLE", 
                               "STANDING_ROOM_ONLY", "CRUSHED_STANDING_ROOM_ONLY", "FULL"})
        @DisplayName("Should accept all valid GTFS-RT occupancy statuses")
        void shouldAcceptValidOccupancyStatuses(String status) {
            // Given: Valid GTFS-RT occupancy status
            VehiclePosition bus = createBusWithOccupancy("BUS_TEST", status);

            // When: Validating occupancy status
            boolean isValid = isValidOccupancyStatus(bus.getOccupancyStatus());

            // Then: Should be valid
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("Should detect anomalous occupancy transitions")
        void shouldDetectAnomalousOccupancyTransitions() {
            // Given: Impossible occupancy transition (EMPTY to FULL in 30 seconds)
            VehiclePosition previousState = createBusWithOccupancyAndTime("BUS_1", "EMPTY", 
                Instant.now().minus(30, ChronoUnit.SECONDS).toEpochMilli());
            VehiclePosition currentState = createBusWithOccupancyAndTime("BUS_1", "FULL",
                Instant.now().toEpochMilli());

            // When: Checking for anomalous transition
            boolean isAnomalous = isAnomalousOccupancyTransition(previousState, currentState);

            // Then: Should detect as anomalous
            assertThat(isAnomalous).isTrue();
        }

        @Test
        @DisplayName("Should validate occupancy consistency across consecutive updates")
        void shouldValidateOccupancyConsistencyAcrossUpdates() {
            // Given: Series of occupancy updates for same bus
            List<VehiclePosition> updates = Arrays.asList(
                createBusWithOccupancyAndTime("BUS_1", "MANY_SEATS_AVAILABLE", 
                    Instant.now().minus(3, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "FEW_SEATS_AVAILABLE",
                    Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "STANDING_ROOM_ONLY",
                    Instant.now().minus(1, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "FULL",
                    Instant.now().toEpochMilli())
            );

            // When: Validating consistency
            ConsistencyResult result = validateOccupancyConsistency(updates);

            // Then: Should show consistent progression
            assertThat(result.isConsistent()).isTrue();
            assertThat(result.getAnomalousTransitions()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Occupancy Data Timeliness Tests")
    class OccupancyTimelinessTests {

        @Test
        @DisplayName("Should validate occupancy data freshness")
        void shouldValidateOccupancyDataFreshness() {
            // Given: Bus with recent occupancy update
            VehiclePosition freshBus = createBusWithOccupancyAndTime("BUS_1", "STANDING_ROOM_ONLY",
                Instant.now().minus(30, ChronoUnit.SECONDS).toEpochMilli());

            // Given: Bus with stale occupancy update
            VehiclePosition staleBus = createBusWithOccupancyAndTime("BUS_2", "FULL",
                Instant.now().minus(10, ChronoUnit.MINUTES).toEpochMilli());

            // When: Checking data freshness (threshold: 2 minutes)
            boolean isFreshDataFresh = isOccupancyDataFresh(freshBus, 120);
            boolean isStaleDataFresh = isOccupancyDataFresh(staleBus, 120);

            // Then: Should correctly identify fresh vs stale
            assertThat(isFreshDataFresh).isTrue();
            assertThat(isStaleDataFresh).isFalse();
        }

        @Test
        @DisplayName("Should calculate average occupancy update frequency")
        void shouldCalculateAverageOccupancyUpdateFrequency() {
            // Given: Series of occupancy updates
            List<VehiclePosition> updates = Arrays.asList(
                createBusWithOccupancyAndTime("BUS_1", "EMPTY",
                    Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "FEW_SEATS_AVAILABLE",
                    Instant.now().minus(4, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "STANDING_ROOM_ONLY",
                    Instant.now().minus(3, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancyAndTime("BUS_1", "FULL",
                    Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli())
            );

            // When: Calculating update frequency
            double avgFrequencySeconds = calculateAverageUpdateFrequency(updates);

            // Then: Should be approximately 60 seconds
            assertThat(avgFrequencySeconds).isCloseTo(60.0, within(5.0));
        }

        @Test
        @DisplayName("Should detect missing occupancy updates")
        void shouldDetectMissingOccupancyUpdates() {
            // Given: Expected update every 60 seconds, but 5 minute gap
            long lastUpdateTime = Instant.now().minus(5, ChronoUnit.MINUTES).toEpochMilli();
            long currentTime = Instant.now().toEpochMilli();

            // When: Checking for missing updates
            int missedUpdates = calculateMissedUpdates(lastUpdateTime, currentTime, 60);

            // Then: Should detect 4 missed updates
            assertThat(missedUpdates).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("Occupancy Capacity Distribution Tests")
    class OccupancyDistributionTests {

        @Test
        @DisplayName("Should analyze occupancy distribution across bus routes")
        void shouldAnalyzeOccupancyDistributionAcrossRoutes() {
            // Given: Buses from different routes with various occupancy levels
            Map<String, List<VehiclePosition>> routeBuses = Map.of(
                "FF2", Arrays.asList(
                    createBusWithOccupancy("FF2_1", "FULL"),
                    createBusWithOccupancy("FF2_2", "STANDING_ROOM_ONLY"),
                    createBusWithOccupancy("FF2_3", "FULL")
                ),
                "15L", Arrays.asList(
                    createBusWithOccupancy("15L_1", "MANY_SEATS_AVAILABLE"),
                    createBusWithOccupancy("15L_2", "FEW_SEATS_AVAILABLE")
                ),
                "0", Arrays.asList(
                    createBusWithOccupancy("0_1", "EMPTY"),
                    createBusWithOccupancy("0_2", "MANY_SEATS_AVAILABLE")
                )
            );

            // When: Analyzing distribution
            Map<String, OccupancyDistribution> distributions = analyzeOccupancyDistribution(routeBuses);

            // Then: Should provide accurate distribution analysis
            assertThat(distributions.get("FF2").getAverageOccupancyScore()).isGreaterThan(4.0);
            assertThat(distributions.get("15L").getAverageOccupancyScore()).isBetween(1.5, 2.5);
            assertThat(distributions.get("0").getAverageOccupancyScore()).isLessThan(1.0);
        }

        @ParameterizedTest
        @CsvSource({
            "EMPTY,0,10",
            "MANY_SEATS_AVAILABLE,11,40",
            "FEW_SEATS_AVAILABLE,41,70",
            "STANDING_ROOM_ONLY,71,85",
            "CRUSHED_STANDING_ROOM_ONLY,86,95",
            "FULL,96,100"
        })
        @DisplayName("Should validate occupancy maps to correct capacity percentage range")
        void shouldValidateOccupancyCapacityMapping(String occupancy, int minCapacity, int maxCapacity) {
            // Given: Occupancy status
            // When: Getting capacity range
            int[] range = OCCUPANCY_CAPACITY_RANGES.get(occupancy);

            // Then: Should match expected range
            assertThat(range[0]).isEqualTo(minCapacity);
            assertThat(range[1]).isEqualTo(maxCapacity);
        }

        @Test
        @DisplayName("Should calculate statistical metrics for occupancy data")
        void shouldCalculateOccupancyStatistics() {
            // Given: Fleet occupancy data
            List<VehiclePosition> fleet = Arrays.asList(
                createBusWithOccupancy("BUS_1", "EMPTY"),
                createBusWithOccupancy("BUS_2", "FEW_SEATS_AVAILABLE"),
                createBusWithOccupancy("BUS_3", "STANDING_ROOM_ONLY"),
                createBusWithOccupancy("BUS_4", "FULL"),
                createBusWithOccupancy("BUS_5", "FEW_SEATS_AVAILABLE")
            );

            // When: Calculating statistics
            OccupancyStatistics stats = calculateOccupancyStatistics(fleet);

            // Then: Should provide accurate statistics
            assertThat(stats.getMean()).isCloseTo(2.4, within(0.1));
            assertThat(stats.getMedian()).isEqualTo(2.0);
            assertThat(stats.getMode()).isEqualTo("FEW_SEATS_AVAILABLE");
            assertThat(stats.getStandardDeviation()).isGreaterThan(1.0);
        }
    }

    @Nested
    @DisplayName("Occupancy Anomaly Detection Tests")
    class OccupancyAnomalyTests {

        @Test
        @DisplayName("Should detect stuck occupancy values")
        void shouldDetectStuckOccupancyValues() {
            // Given: Bus reporting same occupancy for extended period
            List<VehiclePosition> updates = Stream.generate(() -> 
                createBusWithOccupancy("BUS_1", "STANDING_ROOM_ONLY"))
                .limit(20)
                .collect(Collectors.toList());

            // When: Checking for stuck values
            boolean isStuck = hasStuckOccupancyValue(updates, 10);

            // Then: Should detect as stuck
            assertThat(isStuck).isTrue();
        }

        @Test
        @DisplayName("Should detect occupancy outliers in route context")
        void shouldDetectOccupancyOutliers() {
            // Given: Most buses on route are full, but one reports empty
            List<VehiclePosition> routeBuses = Arrays.asList(
                createBusWithOccupancy("BUS_1", "FULL"),
                createBusWithOccupancy("BUS_2", "STANDING_ROOM_ONLY"),
                createBusWithOccupancy("BUS_3", "FULL"),
                createBusWithOccupancy("BUS_4", "EMPTY"), // Outlier
                createBusWithOccupancy("BUS_5", "CRUSHED_STANDING_ROOM_ONLY")
            );

            // When: Detecting outliers
            List<String> outliers = detectOccupancyOutliers(routeBuses);

            // Then: Should identify the empty bus as outlier
            assertThat(outliers).contains("BUS_4");
        }

        @Test
        @DisplayName("Should validate occupancy changes align with stop events")
        void shouldValidateOccupancyChangesAtStops() {
            // Given: Bus occupancy changes at stops
            VehiclePosition atStop = validVehiclePosition()
                .vehicleId("BUS_1")
                .currentStatus("STOPPED_AT")
                .stopId("STOP_123")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .build();

            VehiclePosition leavingStop = validVehiclePosition()
                .vehicleId("BUS_1")
                .currentStatus("IN_TRANSIT_TO")
                .occupancyStatus("STANDING_ROOM_ONLY")
                .build();

            // When: Validating occupancy change is reasonable
            boolean isReasonableChange = isReasonableOccupancyChangeAtStop(atStop, leavingStop);

            // Then: Should be reasonable (passengers boarded)
            assertThat(isReasonableChange).isTrue();
        }
    }

    @Nested
    @DisplayName("Occupancy Data Quality Reporting Tests")
    class OccupancyQualityReportingTests {

        @Test
        @DisplayName("Should generate comprehensive occupancy data quality report")
        void shouldGenerateOccupancyQualityReport() {
            // Given: Fleet with various data quality issues
            List<VehiclePosition> fleet = Arrays.asList(
                createBusWithOccupancy("BUS_1", "FULL"),
                createBusWithOccupancy("BUS_2", null),
                createBusWithOccupancy("BUS_3", "INVALID_STATUS"),
                createBusWithOccupancyAndTime("BUS_4", "STANDING_ROOM_ONLY",
                    Instant.now().minus(15, ChronoUnit.MINUTES).toEpochMilli()),
                createBusWithOccupancy("BUS_5", "FEW_SEATS_AVAILABLE")
            );

            // When: Generating quality report
            OccupancyQualityReport report = generateOccupancyQualityReport(fleet);

            // Then: Report should identify all issues
            assertThat(report.getTotalVehicles()).isEqualTo(5);
            assertThat(report.getValidOccupancyCount()).isEqualTo(3);
            assertThat(report.getMissingOccupancyCount()).isEqualTo(1);
            assertThat(report.getInvalidOccupancyCount()).isEqualTo(1);
            assertThat(report.getStaleDataCount()).isEqualTo(1);
            assertThat(report.getOverallQualityScore()).isLessThan(70.0);
        }

        @Test
        @DisplayName("Should track occupancy data quality trends over time")
        void shouldTrackOccupancyQualityTrends() {
            // Given: Historical quality scores
            Map<Long, Double> historicalScores = new TreeMap<>();
            historicalScores.put(Instant.now().minus(3, ChronoUnit.HOURS).toEpochMilli(), 85.0);
            historicalScores.put(Instant.now().minus(2, ChronoUnit.HOURS).toEpochMilli(), 82.0);
            historicalScores.put(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli(), 78.0);
            historicalScores.put(Instant.now().toEpochMilli(), 75.0);

            // When: Analyzing trend
            QualityTrend trend = analyzeQualityTrend(historicalScores);

            // Then: Should detect declining trend
            assertThat(trend.getDirection()).isEqualTo("DECLINING");
            assertThat(trend.getAverageChange()).isLessThan(0);
            assertThat(trend.requiresAttention()).isTrue();
        }
    }

    @Nested
    @DisplayName("Peak Hour Occupancy Analysis Tests")
    class PeakHourOccupancyTests {

        @Test
        @DisplayName("Should identify peak occupancy hours")
        void shouldIdentifyPeakOccupancyHours() {
            // Given: Occupancy data throughout the day
            Map<Integer, List<VehiclePosition>> hourlyData = createHourlyOccupancyData();

            // When: Identifying peak hours
            List<Integer> peakHours = identifyPeakOccupancyHours(hourlyData);

            // Then: Should identify morning and evening peaks
            assertThat(peakHours).contains(8, 9, 17, 18); // Rush hours
        }

        @Test
        @DisplayName("Should calculate occupancy variance by time of day")
        void shouldCalculateOccupancyVarianceByTimeOfDay() {
            // Given: Occupancy data for different times
            Map<Integer, List<String>> hourlyOccupancy = Map.of(
                8, Arrays.asList("FULL", "STANDING_ROOM_ONLY", "FULL"),
                12, Arrays.asList("FEW_SEATS_AVAILABLE", "MANY_SEATS_AVAILABLE"),
                15, Arrays.asList("MANY_SEATS_AVAILABLE", "FEW_SEATS_AVAILABLE"),
                18, Arrays.asList("FULL", "CRUSHED_STANDING_ROOM_ONLY", "STANDING_ROOM_ONLY")
            );

            // When: Calculating variance
            Map<Integer, Double> variance = calculateHourlyOccupancyVariance(hourlyOccupancy);

            // Then: Peak hours should have higher occupancy scores
            assertThat(variance.get(8)).isGreaterThan(variance.get(12));
            assertThat(variance.get(18)).isGreaterThan(variance.get(15));
        }
    }

    // Helper methods
    private VehiclePosition createBusWithOccupancy(String vehicleId, String occupancy) {
        return validVehiclePosition()
            .vehicleId(vehicleId)
            .occupancyStatus(occupancy)
            .timestamp_ms(Instant.now().toEpochMilli())
            .build();
    }

    private VehiclePosition createBusWithOccupancyAndTime(String vehicleId, String occupancy, long timestamp) {
        return validVehiclePosition()
            .vehicleId(vehicleId)
            .occupancyStatus(occupancy)
            .timestamp_ms(timestamp)
            .build();
    }

    private boolean isValidOccupancyStatus(String status) {
        return status != null && VALID_OCCUPANCY_STATUSES.contains(status);
    }

    private boolean isOccupancyDataFresh(VehiclePosition bus, int thresholdSeconds) {
        long currentTime = Instant.now().toEpochMilli();
        long dataAge = (currentTime - bus.getTimestamp()) / 1000;
        return dataAge <= thresholdSeconds;
    }

    private double calculateOccupancyDataAvailabilityRate(List<VehiclePosition> fleet) {
        long validCount = fleet.stream()
            .filter(bus -> bus.getOccupancyStatus() != null && 
                          !bus.getOccupancyStatus().equals("NO_DATA_AVAILABLE"))
            .count();
        return (validCount * 100.0) / fleet.size();
    }

    private boolean isAnomalousOccupancyTransition(VehiclePosition previous, VehiclePosition current) {
        if (previous.getOccupancyStatus() == null || current.getOccupancyStatus() == null) {
            return false;
        }
        
        long timeDiffSeconds = (current.getTimestamp() - previous.getTimestamp()) / 1000;
        
        // Check for impossible transitions
        if (timeDiffSeconds < 60) { // Less than 1 minute
            if (previous.getOccupancyStatus().equals("EMPTY") && 
                current.getOccupancyStatus().equals("FULL")) {
                return true;
            }
            if (previous.getOccupancyStatus().equals("FULL") && 
                current.getOccupancyStatus().equals("EMPTY")) {
                return true;
            }
        }
        return false;
    }

    private int calculateMissedUpdates(long lastUpdate, long currentTime, int expectedIntervalSeconds) {
        long timeDiffSeconds = (currentTime - lastUpdate) / 1000;
        return Math.max(0, (int)(timeDiffSeconds / expectedIntervalSeconds) - 1);
    }

    private double calculateAverageUpdateFrequency(List<VehiclePosition> updates) {
        if (updates.size() < 2) return 0;
        
        long totalDiff = 0;
        for (int i = 1; i < updates.size(); i++) {
            totalDiff += (updates.get(i).getTimestamp() - updates.get(i-1).getTimestamp());
        }
        return (totalDiff / 1000.0) / (updates.size() - 1);
    }

    private boolean hasStuckOccupancyValue(List<VehiclePosition> updates, int threshold) {
        if (updates.size() < threshold) return false;
        
        String firstOccupancy = updates.get(0).getOccupancyStatus();
        return updates.stream()
            .limit(threshold)
            .allMatch(bus -> Objects.equals(bus.getOccupancyStatus(), firstOccupancy));
    }

    private List<String> detectOccupancyOutliers(List<VehiclePosition> buses) {
        Map<String, Integer> occupancyScores = buses.stream()
            .collect(Collectors.toMap(
                VehiclePosition::getVehicleId,
                bus -> getOccupancyScore(bus.getOccupancyStatus())
            ));
        
        double avgScore = occupancyScores.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0);
        
        return occupancyScores.entrySet().stream()
            .filter(entry -> Math.abs(entry.getValue() - avgScore) > 2.5)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private int getOccupancyScore(String occupancy) {
        return switch (occupancy) {
            case "EMPTY" -> 0;
            case "MANY_SEATS_AVAILABLE" -> 1;
            case "FEW_SEATS_AVAILABLE" -> 2;
            case "STANDING_ROOM_ONLY" -> 3;
            case "CRUSHED_STANDING_ROOM_ONLY" -> 4;
            case "FULL" -> 5;
            default -> -1;
        };
    }

    private boolean isReasonableOccupancyChangeAtStop(VehiclePosition atStop, VehiclePosition leaving) {
        int scoreBefore = getOccupancyScore(atStop.getOccupancyStatus());
        int scoreAfter = getOccupancyScore(leaving.getOccupancyStatus());
        
        // Occupancy can increase (boarding) or decrease (alighting) at stops
        // But extreme changes (>3 levels) are suspicious
        return Math.abs(scoreAfter - scoreBefore) <= 3;
    }

    private DataQualityResult validateOccupancyDataCompleteness(VehiclePosition bus) {
        List<String> missingFields = new ArrayList<>();
        
        if (bus.getOccupancyStatus() == null) missingFields.add("occupancyStatus");
        if (bus.getVehicleId() == null) missingFields.add("vehicleId");
        if (bus.getTimestamp() == null) missingFields.add("timestamp");
        
        double qualityScore = 1.0 - (missingFields.size() / 3.0);
        return new DataQualityResult(missingFields.isEmpty(), missingFields, qualityScore);
    }

    private ConsistencyResult validateOccupancyConsistency(List<VehiclePosition> updates) {
        List<String> anomalies = new ArrayList<>();
        
        for (int i = 1; i < updates.size(); i++) {
            if (isAnomalousOccupancyTransition(updates.get(i-1), updates.get(i))) {
                anomalies.add(String.format("Anomalous transition at index %d", i));
            }
        }
        
        return new ConsistencyResult(anomalies.isEmpty(), anomalies);
    }

    private Map<String, OccupancyDistribution> analyzeOccupancyDistribution(
            Map<String, List<VehiclePosition>> routeBuses) {
        Map<String, OccupancyDistribution> distributions = new HashMap<>();
        
        for (Map.Entry<String, List<VehiclePosition>> entry : routeBuses.entrySet()) {
            double avgScore = entry.getValue().stream()
                .mapToInt(bus -> getOccupancyScore(bus.getOccupancyStatus()))
                .average()
                .orElse(0);
            
            distributions.put(entry.getKey(), new OccupancyDistribution(avgScore));
        }
        
        return distributions;
    }

    private OccupancyStatistics calculateOccupancyStatistics(List<VehiclePosition> fleet) {
        List<Integer> scores = fleet.stream()
            .map(bus -> getOccupancyScore(bus.getOccupancyStatus()))
            .sorted()
            .collect(Collectors.toList());
        
        double mean = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double median = scores.get(scores.size() / 2);
        
        Map<String, Long> frequency = fleet.stream()
            .collect(Collectors.groupingBy(
                VehiclePosition::getOccupancyStatus,
                Collectors.counting()
            ));
        
        String mode = frequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
        
        double variance = scores.stream()
            .mapToDouble(score -> Math.pow(score - mean, 2))
            .average()
            .orElse(0);
        
        double stdDev = Math.sqrt(variance);
        
        return new OccupancyStatistics(mean, median, mode, stdDev);
    }

    private OccupancyQualityReport generateOccupancyQualityReport(List<VehiclePosition> fleet) {
        int total = fleet.size();
        int valid = (int) fleet.stream()
            .filter(bus -> isValidOccupancyStatus(bus.getOccupancyStatus()))
            .count();
        int missing = (int) fleet.stream()
            .filter(bus -> bus.getOccupancyStatus() == null)
            .count();
        int invalid = total - valid - missing;
        int stale = (int) fleet.stream()
            .filter(bus -> !isOccupancyDataFresh(bus, 300))
            .count();
        
        double qualityScore = (valid * 100.0) / total;
        
        return new OccupancyQualityReport(total, valid, missing, invalid, stale, qualityScore);
    }

    private QualityTrend analyzeQualityTrend(Map<Long, Double> historicalScores) {
        List<Double> scores = new ArrayList<>(historicalScores.values());
        
        double totalChange = 0;
        for (int i = 1; i < scores.size(); i++) {
            totalChange += scores.get(i) - scores.get(i-1);
        }
        
        double avgChange = totalChange / (scores.size() - 1);
        String direction = avgChange < -2 ? "DECLINING" : avgChange > 2 ? "IMPROVING" : "STABLE";
        boolean needsAttention = direction.equals("DECLINING") && scores.get(scores.size()-1) < 80;
        
        return new QualityTrend(direction, avgChange, needsAttention);
    }

    private List<Integer> identifyPeakOccupancyHours(Map<Integer, List<VehiclePosition>> hourlyData) {
        Map<Integer, Double> hourlyScores = new HashMap<>();
        
        for (Map.Entry<Integer, List<VehiclePosition>> entry : hourlyData.entrySet()) {
            double avgScore = entry.getValue().stream()
                .mapToInt(bus -> getOccupancyScore(bus.getOccupancyStatus()))
                .average()
                .orElse(0);
            hourlyScores.put(entry.getKey(), avgScore);
        }
        
        double threshold = hourlyScores.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0) * 1.5;
        
        return hourlyScores.entrySet().stream()
            .filter(entry -> entry.getValue() > threshold)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private Map<Integer, List<VehiclePosition>> createHourlyOccupancyData() {
        Map<Integer, List<VehiclePosition>> hourlyData = new HashMap<>();
        
        // Morning rush
        hourlyData.put(8, Arrays.asList(
            createBusWithOccupancy("B1", "FULL"),
            createBusWithOccupancy("B2", "STANDING_ROOM_ONLY")
        ));
        hourlyData.put(9, Arrays.asList(
            createBusWithOccupancy("B3", "FULL"),
            createBusWithOccupancy("B4", "CRUSHED_STANDING_ROOM_ONLY")
        ));
        
        // Midday
        hourlyData.put(12, Arrays.asList(
            createBusWithOccupancy("B5", "FEW_SEATS_AVAILABLE"),
            createBusWithOccupancy("B6", "MANY_SEATS_AVAILABLE")
        ));
        
        // Evening rush
        hourlyData.put(17, Arrays.asList(
            createBusWithOccupancy("B7", "STANDING_ROOM_ONLY"),
            createBusWithOccupancy("B8", "FULL")
        ));
        hourlyData.put(18, Arrays.asList(
            createBusWithOccupancy("B9", "FULL"),
            createBusWithOccupancy("B10", "CRUSHED_STANDING_ROOM_ONLY")
        ));
        
        return hourlyData;
    }

    private Map<Integer, Double> calculateHourlyOccupancyVariance(Map<Integer, List<String>> hourlyOccupancy) {
        Map<Integer, Double> variance = new HashMap<>();
        
        for (Map.Entry<Integer, List<String>> entry : hourlyOccupancy.entrySet()) {
            double avgScore = entry.getValue().stream()
                .mapToInt(this::getOccupancyScore)
                .average()
                .orElse(0);
            variance.put(entry.getKey(), avgScore);
        }
        
        return variance;
    }

    // Supporting classes
    record DataQualityResult(boolean isComplete, List<String> missingFields, double qualityScore) {}
    record ConsistencyResult(boolean isConsistent, List<String> anomalousTransitions) {}
    record OccupancyDistribution(double averageOccupancyScore) {}
    record OccupancyStatistics(double mean, double median, String mode, double standardDeviation) {}
    record OccupancyQualityReport(int totalVehicles, int validOccupancyCount, int missingOccupancyCount,
                                  int invalidOccupancyCount, int staleDataCount, double overallQualityScore) {}
    record QualityTrend(String direction, double averageChange, boolean requiresAttention) {}
}