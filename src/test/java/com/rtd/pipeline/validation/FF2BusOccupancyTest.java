package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;
import static com.rtd.pipeline.validation.TestDataBuilder.*;

import java.time.Instant;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Test suite specifically for monitoring FF2 bus occupancy levels and detecting when buses are full.
 * FF2 is a popular bus route in Denver and monitoring occupancy helps with capacity planning.
 * 
 * GTFS-RT occupancy_status values:
 * - EMPTY: Few or no passengers aboard
 * - MANY_SEATS_AVAILABLE: Large number of seats available
 * - FEW_SEATS_AVAILABLE: Small number of seats available  
 * - STANDING_ROOM_ONLY: Can accommodate only standing passengers
 * - CRUSHED_STANDING_ROOM_ONLY: Can accommodate only standing passengers and limited boarding
 * - FULL: Cannot accept any passengers
 * - NOT_ACCEPTING_PASSENGERS: Cannot accept passengers (out of service)
 */
class FF2BusOccupancyTest {

    private static final String FF2_ROUTE_ID = "FF2";
    private static final String FULL_OCCUPANCY = "FULL";
    private static final String STANDING_ROOM_ONLY = "STANDING_ROOM_ONLY";
    private static final String CRUSHED_STANDING_ROOM_ONLY = "CRUSHED_STANDING_ROOM_ONLY";
    private static final String NOT_ACCEPTING_PASSENGERS = "NOT_ACCEPTING_PASSENGERS";
    
    @Nested
    @DisplayName("FF2 Bus Full Occupancy Detection Tests")
    class FF2FullOccupancyTests {

        @Test
        @DisplayName("Should detect when FF2 bus is completely full")
        void shouldDetectFF2BusCompletelyFull() {
            // Given: FF2 bus with FULL occupancy status
            VehiclePosition ff2Bus = validVehiclePosition()
                .routeId(FF2_ROUTE_ID)
                .vehicleId("RTD_FF2_001")
                .occupancyStatus(FULL_OCCUPANCY)
                .currentStatus("IN_TRANSIT_TO")
                .latitude(39.7392)  // Downtown Denver
                .longitude(-104.9903)
                .timestamp(Instant.now().toEpochMilli())
                .build();

            // When: Checking if bus is full
            boolean isFull = isFF2BusFull(ff2Bus);

            // Then: Should be detected as full
            assertThat(isFull).isTrue();
            assertThat(ff2Bus.getRouteId()).isEqualTo(FF2_ROUTE_ID);
            assertThat(ff2Bus.getOccupancyStatus()).isEqualTo(FULL_OCCUPANCY);
        }

        @Test
        @DisplayName("Should detect when FF2 bus is at standing room only capacity")
        void shouldDetectFF2BusStandingRoomOnly() {
            // Given: FF2 bus with standing room only
            VehiclePosition ff2Bus = validVehiclePosition()
                .routeId(FF2_ROUTE_ID)
                .vehicleId("RTD_FF2_002")
                .occupancyStatus(STANDING_ROOM_ONLY)
                .currentStatus("STOPPED_AT")
                .stopId("UNION_STATION")
                .build();

            // When: Checking if bus is at high capacity
            boolean isAtHighCapacity = isFF2BusHighCapacity(ff2Bus);

            // Then: Should be detected as high capacity
            assertThat(isAtHighCapacity).isTrue();
            assertThat(ff2Bus.getOccupancyStatus()).isEqualTo(STANDING_ROOM_ONLY);
        }

        @Test
        @DisplayName("Should detect when FF2 bus is at crushed standing room capacity")
        void shouldDetectFF2BusCrushedStandingRoom() {
            // Given: FF2 bus with crushed standing room only
            VehiclePosition ff2Bus = validVehiclePosition()
                .routeId(FF2_ROUTE_ID)
                .vehicleId("RTD_FF2_003")
                .occupancyStatus(CRUSHED_STANDING_ROOM_ONLY)
                .currentStatus("IN_TRANSIT_TO")
                .build();

            // When: Checking if bus is at critical capacity
            boolean isAtCriticalCapacity = isFF2BusCriticalCapacity(ff2Bus);

            // Then: Should be detected as critical capacity
            assertThat(isAtCriticalCapacity).isTrue();
            assertThat(ff2Bus.getOccupancyStatus()).isEqualTo(CRUSHED_STANDING_ROOM_ONLY);
        }

        @Test
        @DisplayName("Should detect when FF2 bus is not accepting passengers")
        void shouldDetectFF2BusNotAcceptingPassengers() {
            // Given: FF2 bus not accepting passengers (potentially out of service)
            VehiclePosition ff2Bus = validVehiclePosition()
                .routeId(FF2_ROUTE_ID)
                .vehicleId("RTD_FF2_004")
                .occupancyStatus(NOT_ACCEPTING_PASSENGERS)
                .currentStatus("STOPPED_AT")
                .build();

            // When: Checking if bus is unavailable for boarding
            boolean isUnavailable = isFF2BusUnavailableForBoarding(ff2Bus);

            // Then: Should be detected as unavailable
            assertThat(isUnavailable).isTrue();
            assertThat(ff2Bus.getOccupancyStatus()).isEqualTo(NOT_ACCEPTING_PASSENGERS);
        }

        @Test
        @DisplayName("Should not detect non-FF2 buses as full even when they are")
        void shouldNotDetectNonFF2BusesAsFull() {
            // Given: Non-FF2 bus that is full
            VehiclePosition otherBus = validVehiclePosition()
                .routeId("15L") // Different route
                .vehicleId("RTD_15L_001")
                .occupancyStatus(FULL_OCCUPANCY)
                .build();

            // When: Checking if it's a full FF2 bus
            boolean isFF2Full = isFF2BusFull(otherBus);

            // Then: Should not be detected as FF2 full
            assertThat(isFF2Full).isFalse();
            assertThat(otherBus.getRouteId()).isNotEqualTo(FF2_ROUTE_ID);
        }

        @Test
        @DisplayName("Should not detect FF2 bus as full when it has available seats")
        void shouldNotDetectFF2BusAsFullWhenSeatsAvailable() {
            // Given: FF2 bus with available seats
            VehiclePosition ff2Bus = validVehiclePosition()
                .routeId(FF2_ROUTE_ID)
                .vehicleId("RTD_FF2_005")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .build();

            // When: Checking if bus is full
            boolean isFull = isFF2BusFull(ff2Bus);

            // Then: Should not be detected as full
            assertThat(isFull).isFalse();
            assertThat(ff2Bus.getOccupancyStatus()).isNotEqualTo(FULL_OCCUPANCY);
        }
    }

    @Nested
    @DisplayName("FF2 Fleet Occupancy Analysis Tests")
    class FF2FleetAnalysisTests {

        @Test
        @DisplayName("Should analyze occupancy across entire FF2 fleet")
        void shouldAnalyzeFF2FleetOccupancy() {
            // Given: Fleet of FF2 buses with various occupancy levels
            List<VehiclePosition> ff2Fleet = Arrays.asList(
                createFF2Bus("RTD_FF2_001", FULL_OCCUPANCY),
                createFF2Bus("RTD_FF2_002", STANDING_ROOM_ONLY),
                createFF2Bus("RTD_FF2_003", "FEW_SEATS_AVAILABLE"),
                createFF2Bus("RTD_FF2_004", CRUSHED_STANDING_ROOM_ONLY),
                createFF2Bus("RTD_FF2_005", "MANY_SEATS_AVAILABLE"),
                createFF2Bus("RTD_FF2_006", FULL_OCCUPANCY),
                createFF2Bus("RTD_FF2_007", NOT_ACCEPTING_PASSENGERS)
            );

            // When: Analyzing fleet occupancy
            FF2FleetOccupancyAnalysis analysis = analyzeFF2FleetOccupancy(ff2Fleet);

            // Then: Should provide accurate analysis
            assertThat(analysis.getTotalBuses()).isEqualTo(7);
            assertThat(analysis.getFullBuses()).isEqualTo(2);
            assertThat(analysis.getHighCapacityBuses()).isEqualTo(4); // 2 FULL + 1 STANDING_ROOM_ONLY + 1 CRUSHED_STANDING_ROOM_ONLY
            assertThat(analysis.getAvailableBuses()).isEqualTo(2); // FEW_SEATS_AVAILABLE + MANY_SEATS_AVAILABLE (NOT_ACCEPTING_PASSENGERS excluded)
            assertThat(analysis.getFullOccupancyPercentage()).isEqualTo(28.57, offset(0.01));
            assertThat(analysis.getHighCapacityPercentage()).isEqualTo(57.14, offset(0.01));
        }

        @Test
        @DisplayName("Should identify peak capacity periods for FF2 route")
        void shouldIdentifyFF2PeakCapacityPeriods() {
            // Given: FF2 buses during rush hour with high occupancy
            List<VehiclePosition> rushHourFF2Buses = Arrays.asList(
                createFF2BusWithTime("RTD_FF2_001", FULL_OCCUPANCY, getRushHourTimestamp()),
                createFF2BusWithTime("RTD_FF2_002", CRUSHED_STANDING_ROOM_ONLY, getRushHourTimestamp()),
                createFF2BusWithTime("RTD_FF2_003", STANDING_ROOM_ONLY, getRushHourTimestamp()),
                createFF2BusWithTime("RTD_FF2_004", FULL_OCCUPANCY, getRushHourTimestamp())
            );

            // When: Analyzing peak capacity
            boolean isPeakCapacityPeriod = isFF2PeakCapacityPeriod(rushHourFF2Buses);
            double averageCapacityScore = calculateAverageCapacityScore(rushHourFF2Buses);

            // Then: Should detect peak capacity period
            assertThat(isPeakCapacityPeriod).isTrue();
            assertThat(averageCapacityScore).isGreaterThan(3.5); // High capacity score
        }

        @Test
        @DisplayName("Should alert when FF2 fleet capacity exceeds threshold")
        void shouldAlertWhenFF2FleetCapacityExceedsThreshold() {
            // Given: FF2 fleet with mostly full buses
            List<VehiclePosition> overCapacityFleet = Arrays.asList(
                createFF2Bus("RTD_FF2_001", FULL_OCCUPANCY),
                createFF2Bus("RTD_FF2_002", FULL_OCCUPANCY),
                createFF2Bus("RTD_FF2_003", CRUSHED_STANDING_ROOM_ONLY),
                createFF2Bus("RTD_FF2_004", FULL_OCCUPANCY),
                createFF2Bus("RTD_FF2_005", STANDING_ROOM_ONLY)
            );

            // When: Checking if capacity alert should be triggered
            boolean shouldAlert = shouldTriggerFF2CapacityAlert(overCapacityFleet, 60.0); // 60% threshold

            // Then: Should trigger capacity alert
            assertThat(shouldAlert).isTrue();
            
            // Verify alert details
            FF2CapacityAlert alert = generateFF2CapacityAlert(overCapacityFleet);
            assertThat(alert.getMessage()).contains("FF2 route capacity critical");
            assertThat(alert.getFullBusCount()).isEqualTo(3);
            assertThat(alert.getTotalBusCount()).isEqualTo(5);
            assertThat(alert.getSeverity()).isEqualTo("HIGH");
        }
    }

    @Nested
    @DisplayName("Real-time FF2 Monitoring Tests")
    class FF2RealTimeMonitoringTests {

        @Test
        @DisplayName("Should monitor FF2 buses in real-time for occupancy changes")
        void shouldMonitorFF2BusesRealTime() {
            // Given: FF2 bus that changes from available to full
            VehiclePosition initialState = createFF2Bus("RTD_FF2_001", "FEW_SEATS_AVAILABLE");
            VehiclePosition updatedState = createFF2Bus("RTD_FF2_001", FULL_OCCUPANCY);

            // When: Monitoring occupancy change
            FF2OccupancyChange change = detectFF2OccupancyChange(initialState, updatedState);

            // Then: Should detect the change to full
            assertThat(change.hasOccupancyChanged()).isTrue();
            assertThat(change.getPreviousOccupancy()).isEqualTo("FEW_SEATS_AVAILABLE");
            assertThat(change.getCurrentOccupancy()).isEqualTo(FULL_OCCUPANCY);
            assertThat(change.isNowFull()).isTrue();
            assertThat(change.getVehicleId()).isEqualTo("RTD_FF2_001");
        }

        @Test
        @DisplayName("Should track FF2 bus locations when full for route optimization")
        void shouldTrackFF2FullBusLocations() {
            // Given: Full FF2 buses at different locations
            List<VehiclePosition> fullFF2Buses = Arrays.asList(
                createFF2BusAtLocation("RTD_FF2_001", FULL_OCCUPANCY, 39.7392, -104.9903, "UNION_STATION"),
                createFF2BusAtLocation("RTD_FF2_002", FULL_OCCUPANCY, 39.7555, -105.0014, "FEDERAL_CENTER"),
                createFF2BusAtLocation("RTD_FF2_003", CRUSHED_STANDING_ROOM_ONLY, 39.8617, -104.9943, "WESTMINSTER")
            );

            // When: Analyzing full bus locations
            List<FF2FullBusLocation> fullBusLocations = getFF2FullBusLocations(fullFF2Buses);

            // Then: Should provide location details for capacity planning
            assertThat(fullBusLocations).hasSize(3);
            assertThat(fullBusLocations).extracting("stopId")
                .containsExactlyInAnyOrder("UNION_STATION", "FEDERAL_CENTER", "WESTMINSTER");
            assertThat(fullBusLocations).allMatch(location -> location.isAtCapacity());
        }
    }

    // Helper methods for FF2 occupancy detection
    private boolean isFF2BusFull(VehiclePosition vehicle) {
        return FF2_ROUTE_ID.equals(vehicle.getRouteId()) && 
               FULL_OCCUPANCY.equals(vehicle.getOccupancyStatus());
    }

    private boolean isFF2BusHighCapacity(VehiclePosition vehicle) {
        if (!FF2_ROUTE_ID.equals(vehicle.getRouteId())) {
            return false;
        }
        return STANDING_ROOM_ONLY.equals(vehicle.getOccupancyStatus()) ||
               CRUSHED_STANDING_ROOM_ONLY.equals(vehicle.getOccupancyStatus()) ||
               FULL_OCCUPANCY.equals(vehicle.getOccupancyStatus());
    }

    private boolean isFF2BusCriticalCapacity(VehiclePosition vehicle) {
        return FF2_ROUTE_ID.equals(vehicle.getRouteId()) && 
               CRUSHED_STANDING_ROOM_ONLY.equals(vehicle.getOccupancyStatus());
    }

    private boolean isFF2BusUnavailableForBoarding(VehiclePosition vehicle) {
        return FF2_ROUTE_ID.equals(vehicle.getRouteId()) && 
               NOT_ACCEPTING_PASSENGERS.equals(vehicle.getOccupancyStatus());
    }

    private VehiclePosition createFF2Bus(String vehicleId, String occupancyStatus) {
        return validVehiclePosition()
            .routeId(FF2_ROUTE_ID)
            .vehicleId(vehicleId)
            .occupancyStatus(occupancyStatus)
            .currentStatus("IN_TRANSIT_TO")
            .latitude(39.7392)
            .longitude(-104.9903)
            .timestamp(Instant.now().toEpochMilli())
            .build();
    }

    private VehiclePosition createFF2BusWithTime(String vehicleId, String occupancyStatus, long timestamp) {
        return validVehiclePosition()
            .routeId(FF2_ROUTE_ID)
            .vehicleId(vehicleId)
            .occupancyStatus(occupancyStatus)
            .timestamp(timestamp)
            .build();
    }

    private VehiclePosition createFF2BusAtLocation(String vehicleId, String occupancyStatus, 
                                                  double lat, double lon, String stopId) {
        return validVehiclePosition()
            .routeId(FF2_ROUTE_ID)
            .vehicleId(vehicleId)
            .occupancyStatus(occupancyStatus)
            .latitude(lat)
            .longitude(lon)
            .stopId(stopId)
            .currentStatus("STOPPED_AT")
            .timestamp(Instant.now().toEpochMilli())
            .build();
    }

    private long getRushHourTimestamp() {
        // 8:00 AM rush hour timestamp
        return Instant.now().toEpochMilli();
    }

    private FF2FleetOccupancyAnalysis analyzeFF2FleetOccupancy(List<VehiclePosition> fleet) {
        int total = fleet.size();
        int full = (int) fleet.stream().filter(this::isFF2BusFull).count();
        int highCapacity = (int) fleet.stream().filter(this::isFF2BusHighCapacity).count();
        // Available buses are those with seats (not high capacity and not out of service)
        int available = (int) fleet.stream()
            .filter(bus -> !isFF2BusHighCapacity(bus) && !NOT_ACCEPTING_PASSENGERS.equals(bus.getOccupancyStatus()))
            .count();
        
        return new FF2FleetOccupancyAnalysis(total, full, highCapacity, available);
    }

    private boolean isFF2PeakCapacityPeriod(List<VehiclePosition> buses) {
        double highCapacityRatio = buses.stream()
            .mapToDouble(bus -> isFF2BusHighCapacity(bus) ? 1.0 : 0.0)
            .average()
            .orElse(0.0);
        return highCapacityRatio > 0.75; // 75% threshold for peak period
    }

    private double calculateAverageCapacityScore(List<VehiclePosition> buses) {
        return buses.stream()
            .mapToDouble(this::getCapacityScore)
            .average()
            .orElse(0.0);
    }

    private double getCapacityScore(VehiclePosition bus) {
        switch (bus.getOccupancyStatus()) {
            case "EMPTY": return 0.0;
            case "MANY_SEATS_AVAILABLE": return 1.0;
            case "FEW_SEATS_AVAILABLE": return 2.0;
            case STANDING_ROOM_ONLY: return 3.0;
            case CRUSHED_STANDING_ROOM_ONLY: return 4.0;
            case FULL_OCCUPANCY: return 5.0;
            case NOT_ACCEPTING_PASSENGERS: return 5.0;
            default: return 0.0;
        }
    }

    private boolean shouldTriggerFF2CapacityAlert(List<VehiclePosition> fleet, double thresholdPercent) {
        double fullPercentage = (fleet.stream().filter(this::isFF2BusFull).count() * 100.0) / fleet.size();
        return fullPercentage >= thresholdPercent;
    }

    private FF2CapacityAlert generateFF2CapacityAlert(List<VehiclePosition> fleet) {
        int fullCount = (int) fleet.stream().filter(this::isFF2BusFull).count();
        return new FF2CapacityAlert(fullCount, fleet.size());
    }

    private FF2OccupancyChange detectFF2OccupancyChange(VehiclePosition previous, VehiclePosition current) {
        return new FF2OccupancyChange(previous, current);
    }

    private List<FF2FullBusLocation> getFF2FullBusLocations(List<VehiclePosition> buses) {
        return buses.stream()
            .filter(bus -> isFF2BusHighCapacity(bus))
            .map(FF2FullBusLocation::new)
            .collect(Collectors.toList());
    }

    // Supporting classes for analysis
    public static class FF2FleetOccupancyAnalysis {
        private final int totalBuses;
        private final int fullBuses;
        private final int highCapacityBuses;
        private final int availableBuses;

        public FF2FleetOccupancyAnalysis(int total, int full, int highCapacity, int available) {
            this.totalBuses = total;
            this.fullBuses = full;
            this.highCapacityBuses = highCapacity;
            this.availableBuses = available;
        }

        public int getTotalBuses() { return totalBuses; }
        public int getFullBuses() { return fullBuses; }
        public int getHighCapacityBuses() { return highCapacityBuses; }
        public int getAvailableBuses() { return availableBuses; }

        public double getFullOccupancyPercentage() {
            return (fullBuses * 100.0) / totalBuses;
        }

        public double getHighCapacityPercentage() {
            return (highCapacityBuses * 100.0) / totalBuses;
        }
    }

    public static class FF2CapacityAlert {
        private final int fullBusCount;
        private final int totalBusCount;
        private final String severity;
        private final String message;

        public FF2CapacityAlert(int fullBusCount, int totalBusCount) {
            this.fullBusCount = fullBusCount;
            this.totalBusCount = totalBusCount;
            double percentage = (fullBusCount * 100.0) / totalBusCount;
            this.severity = percentage >= 60 ? "HIGH" : percentage >= 40 ? "MEDIUM" : "LOW";
            this.message = String.format("FF2 route capacity critical: %d/%d buses full (%.1f%%)", 
                                        fullBusCount, totalBusCount, percentage);
        }

        public int getFullBusCount() { return fullBusCount; }
        public int getTotalBusCount() { return totalBusCount; }
        public String getSeverity() { return severity; }
        public String getMessage() { return message; }
    }

    public static class FF2OccupancyChange {
        private final String vehicleId;
        private final String previousOccupancy;
        private final String currentOccupancy;
        private final boolean hasChanged;

        public FF2OccupancyChange(VehiclePosition previous, VehiclePosition current) {
            this.vehicleId = current.getVehicleId();
            this.previousOccupancy = previous.getOccupancyStatus();
            this.currentOccupancy = current.getOccupancyStatus();
            this.hasChanged = !previousOccupancy.equals(currentOccupancy);
        }

        public String getVehicleId() { return vehicleId; }
        public String getPreviousOccupancy() { return previousOccupancy; }
        public String getCurrentOccupancy() { return currentOccupancy; }
        public boolean hasOccupancyChanged() { return hasChanged; }
        public boolean isNowFull() { return FULL_OCCUPANCY.equals(currentOccupancy); }
    }

    public static class FF2FullBusLocation {
        private final String vehicleId;
        private final double latitude;
        private final double longitude;
        private final String stopId;
        private final String occupancyStatus;

        public FF2FullBusLocation(VehiclePosition vehicle) {
            this.vehicleId = vehicle.getVehicleId();
            this.latitude = vehicle.getLatitude();
            this.longitude = vehicle.getLongitude();
            this.stopId = vehicle.getStopId();
            this.occupancyStatus = vehicle.getOccupancyStatus();
        }

        public String getVehicleId() { return vehicleId; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getStopId() { return stopId; }
        public String getOccupancyStatus() { return occupancyStatus; }
        public boolean isAtCapacity() { 
            return FULL_OCCUPANCY.equals(occupancyStatus) || 
                   CRUSHED_STANDING_ROOM_ONLY.equals(occupancyStatus) ||
                   STANDING_ROOM_ONLY.equals(occupancyStatus);
        }
    }
}