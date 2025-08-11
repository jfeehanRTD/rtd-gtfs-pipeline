package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builder class for creating test data for GTFS-RT validation tests.
 * Provides fluent API for constructing valid and invalid test objects using the existing model builders.
 */
public class TestDataBuilder {

    /**
     * Builder wrapper for VehiclePosition test data that uses the model's builder
     */
    public static class VehiclePositionBuilder {
        private VehiclePosition.Builder modelBuilder = VehiclePosition.builder();

        public VehiclePositionBuilder() {
            // Set up valid defaults
            modelBuilder
                .vehicleId("RTD_BUS_001")
                .latitude(39.7392) // Denver downtown
                .longitude(-104.9903)
                .bearing(180.0f)
                .speed(25.0f)
                .timestamp_ms(Instant.now().toEpochMilli())
                .currentStatus("IN_TRANSIT_TO")
                .congestionLevel("RUNNING_SMOOTHLY")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .tripId("TRIP_123")
                .routeId("ROUTE_15");
        }

        public VehiclePositionBuilder vehicleId(String vehicleId) {
            modelBuilder.vehicleId(vehicleId);
            return this;
        }

        public VehiclePositionBuilder latitude(Double latitude) {
            modelBuilder.latitude(latitude);
            return this;
        }

        public VehiclePositionBuilder longitude(Double longitude) {
            modelBuilder.longitude(longitude);
            return this;
        }

        public VehiclePositionBuilder bearing(Float bearing) {
            modelBuilder.bearing(bearing);
            return this;
        }

        public VehiclePositionBuilder speed(Float speed) {
            modelBuilder.speed(speed);
            return this;
        }

        public VehiclePositionBuilder timestamp(Long timestamp) {
            modelBuilder.timestamp_ms(timestamp);
            return this;
        }

        public VehiclePositionBuilder currentStatus(String currentStatus) {
            modelBuilder.currentStatus(currentStatus);
            return this;
        }

        public VehiclePositionBuilder congestionLevel(String congestionLevel) {
            modelBuilder.congestionLevel(congestionLevel);
            return this;
        }

        public VehiclePositionBuilder occupancyStatus(String occupancyStatus) {
            modelBuilder.occupancyStatus(occupancyStatus);
            return this;
        }

        public VehiclePositionBuilder tripId(String tripId) {
            modelBuilder.tripId(tripId);
            return this;
        }

        public VehiclePositionBuilder routeId(String routeId) {
            modelBuilder.routeId(routeId);
            return this;
        }

        public VehiclePositionBuilder stopId(String stopId) {
            modelBuilder.stopId(stopId);
            return this;
        }

        public VehiclePositionBuilder outsideDenverArea() {
            modelBuilder.latitude(40.7128).longitude(-74.0060); // New York
            return this;
        }

        public VehiclePositionBuilder invalidCoordinates() {
            modelBuilder.latitude(91.0).longitude(181.0); // Invalid coords
            return this;
        }

        public VehiclePositionBuilder invalidBearing() {
            modelBuilder.bearing(400.0f); // Invalid bearing
            return this;
        }

        public VehiclePositionBuilder negativeSpeed() {
            modelBuilder.speed(-10.0f);
            return this;
        }

        public VehiclePositionBuilder highSpeed() {
            modelBuilder.speed(250.0f); // Very high speed
            return this;
        }

        public VehiclePositionBuilder oldTimestamp() {
            modelBuilder.timestamp_ms(Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000)); // 25 hours ago
            return this;
        }

        public VehiclePositionBuilder futureTimestamp() {
            modelBuilder.timestamp_ms(Instant.now().toEpochMilli() + (25 * 60 * 60 * 1000)); // 25 hours in future
            return this;
        }

        public VehiclePositionBuilder invalidStatus() {
            modelBuilder.currentStatus("INVALID_STATUS");
            return this;
        }

        public VehiclePositionBuilder noTripOrRoute() {
            modelBuilder.tripId(null).routeId(null);
            return this;
        }

        public VehiclePositionBuilder nullRequired() {
            modelBuilder.vehicleId(null).latitude(null).longitude(null);
            return this;
        }

        public VehiclePosition build() {
            return modelBuilder.build();
        }
    }

    /**
     * Builder wrapper for TripUpdate test data that uses the model's builder
     */
    public static class TripUpdateBuilder {
        private TripUpdate.Builder modelBuilder = TripUpdate.builder();

        public TripUpdateBuilder() {
            // Set up valid defaults
            modelBuilder
                .tripId("TRIP_123")
                .scheduleRelationship("SCHEDULED")
                .startDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .startTime("08:30:00")
                .delaySeconds(120) // 2 minutes late
                .timestamp_ms(Instant.now().toEpochMilli())
                .vehicleId("RTD_BUS_001")
                .routeId("ROUTE_15");
        }

        public TripUpdateBuilder tripId(String tripId) {
            modelBuilder.tripId(tripId);
            return this;
        }

        public TripUpdateBuilder scheduleRelationship(String scheduleRelationship) {
            modelBuilder.scheduleRelationship(scheduleRelationship);
            return this;
        }

        public TripUpdateBuilder startDate(String startDate) {
            modelBuilder.startDate(startDate);
            return this;
        }

        public TripUpdateBuilder startTime(String startTime) {
            modelBuilder.startTime(startTime);
            return this;
        }

        public TripUpdateBuilder delaySeconds(Integer delaySeconds) {
            modelBuilder.delaySeconds(delaySeconds);
            return this;
        }

        public TripUpdateBuilder timestamp(Long timestamp) {
            modelBuilder.timestamp_ms(timestamp);
            return this;
        }

        public TripUpdateBuilder vehicleId(String vehicleId) {
            modelBuilder.vehicleId(vehicleId);
            return this;
        }

        public TripUpdateBuilder routeId(String routeId) {
            modelBuilder.routeId(routeId);
            return this;
        }

        public TripUpdateBuilder stopId(String stopId) {
            modelBuilder.stopId(stopId);
            return this;
        }

        public TripUpdateBuilder invalidScheduleRelationship() {
            modelBuilder.scheduleRelationship("INVALID_RELATIONSHIP");
            return this;
        }

        public TripUpdateBuilder invalidDateFormat() {
            modelBuilder.startDate("2023-12-25"); // Wrong format
            return this;
        }

        public TripUpdateBuilder invalidDate() {
            modelBuilder.startDate("20231301"); // Invalid month
            return this;
        }

        public TripUpdateBuilder invalidTimeFormat() {
            modelBuilder.startTime("8:30 AM"); // Wrong format
            return this;
        }

        public TripUpdateBuilder invalidTime() {
            modelBuilder.startTime("25:70:90"); // Invalid hours, minutes, seconds
            return this;
        }

        public TripUpdateBuilder lateNightTime() {
            modelBuilder.startTime("26:30:00"); // Valid late night time
            return this;
        }

        public TripUpdateBuilder largeDelay() {
            modelBuilder.delaySeconds(7800); // 2.17 hours late
            return this;
        }

        public TripUpdateBuilder oldTimestamp() {
            modelBuilder.timestamp_ms(Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000)); // 25 hours ago
            return this;
        }

        public TripUpdateBuilder noVehicleId() {
            modelBuilder.vehicleId(null);
            return this;
        }

        public TripUpdateBuilder nullRequired() {
            modelBuilder.tripId(null);
            return this;
        }

        public TripUpdate build() {
            return modelBuilder.build();
        }
    }

    /**
     * Builder wrapper for Alert test data that uses the model's builder
     * Note: Alert model doesn't have routeId field, but validator checks for it in some contexts
     */
    public static class AlertBuilder {
        private Alert.Builder modelBuilder = Alert.builder();

        public AlertBuilder() {
            // Set up valid defaults
            modelBuilder
                .alertId("ALERT_001")
                .cause("CONSTRUCTION")
                .effect("DETOUR")
                .headerText("Route 15 Detour")
                .descriptionText("Due to construction on Main St, Route 15 is detoured via Broadway.")
                .url("https://www.rtd-denver.com/alerts/route-15-detour")
                .activePeriodStart(Instant.now().getEpochSecond())
                .activePeriodEnd(Instant.now().getEpochSecond() + 3600) // 1 hour from now
                .timestamp_ms(Instant.now().toEpochMilli());
        }

        public AlertBuilder alertId(String alertId) {
            modelBuilder.alertId(alertId);
            return this;
        }

        public AlertBuilder cause(String cause) {
            modelBuilder.cause(cause);
            return this;
        }

        public AlertBuilder effect(String effect) {
            modelBuilder.effect(effect);
            return this;
        }

        public AlertBuilder headerText(String headerText) {
            modelBuilder.headerText(headerText);
            return this;
        }

        public AlertBuilder descriptionText(String descriptionText) {
            modelBuilder.descriptionText(descriptionText);
            return this;
        }

        public AlertBuilder url(String url) {
            modelBuilder.url(url);
            return this;
        }

        public AlertBuilder activePeriodStart(Long activePeriodStart) {
            modelBuilder.activePeriodStart(activePeriodStart);
            return this;
        }

        public AlertBuilder activePeriodEnd(Long activePeriodEnd) {
            modelBuilder.activePeriodEnd(activePeriodEnd);
            return this;
        }

        public AlertBuilder timestamp(Long timestamp) {
            modelBuilder.timestamp_ms(timestamp);
            return this;
        }

        public AlertBuilder routeIds(List<String> routeIds) {
            modelBuilder.routeIds(routeIds);
            return this;
        }

        public AlertBuilder invalidCause() {
            modelBuilder.cause("INVALID_CAUSE");
            return this;
        }

        public AlertBuilder invalidEffect() {
            modelBuilder.effect("INVALID_EFFECT");
            return this;
        }

        public AlertBuilder noText() {
            modelBuilder.headerText(null).descriptionText(null);
            return this;
        }

        public AlertBuilder emptyText() {
            modelBuilder.headerText("").descriptionText("   "); // Whitespace only
            return this;
        }

        public AlertBuilder invalidUrl() {
            modelBuilder.url("not-a-valid-url");
            return this;
        }

        public AlertBuilder invalidActivePeriod() {
            long now = Instant.now().getEpochSecond();
            modelBuilder.activePeriodStart(now + 3600).activePeriodEnd(now); // start > end
            return this;
        }

        public AlertBuilder expiredAlert() {
            long now = Instant.now().getEpochSecond();
            modelBuilder.activePeriodStart(now - 7200).activePeriodEnd(now - 3600); // 2-1 hours ago
            return this;
        }

        public AlertBuilder oldTimestamp() {
            modelBuilder.timestamp_ms(Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000)); // 25 hours ago
            return this;
        }

        public AlertBuilder nullRequired() {
            modelBuilder.alertId(null);
            return this;
        }

        public Alert build() {
            return modelBuilder.build();
        }
    }

    // Factory methods for easy access
    public static VehiclePositionBuilder validVehiclePosition() {
        return new VehiclePositionBuilder();
    }

    public static TripUpdateBuilder validTripUpdate() {
        return new TripUpdateBuilder();
    }

    public static AlertBuilder validAlert() {
        return new AlertBuilder();
    }

    // Pre-built test fixtures
    public static class Fixtures {
        public static final VehiclePosition VALID_VEHICLE_POSITION = validVehiclePosition().build();
        
        public static final VehiclePosition VEHICLE_POSITION_NO_LOCATION = validVehiclePosition()
            .nullRequired().build();
        
        public static final VehiclePosition VEHICLE_POSITION_INVALID_COORDS = validVehiclePosition()
            .invalidCoordinates().build();
        
        public static final TripUpdate VALID_TRIP_UPDATE = validTripUpdate().build();
        
        public static final TripUpdate TRIP_UPDATE_NO_ID = validTripUpdate()
            .nullRequired().build();
        
        public static final TripUpdate TRIP_UPDATE_INVALID_TIME = validTripUpdate()
            .invalidTimeFormat().build();
        
        public static final Alert VALID_ALERT = validAlert().build();
        
        public static final Alert ALERT_NO_ID = validAlert()
            .nullRequired().build();
        
        public static final Alert ALERT_NO_TEXT = validAlert()
            .noText().build();
    }
}