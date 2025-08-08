package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.TripUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;

/**
 * Comprehensive test suite for TripUpdate validation according to GTFS-RT specification.
 * Tests cover required fields, date/time format validation, delay validation, and enum values.
 */
class TripUpdateValidationTest {

    @Nested
    @DisplayName("Valid TripUpdate Tests")
    class ValidTripUpdateTests {

        @Test
        @DisplayName("Valid complete trip update should pass validation")
        void validCompleteTripUpdate() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .routeId("ROUTE456")
                .vehicleId("RTD001")
                .startDate("20240101")
                .startTime("08:30:00")
                .scheduleRelationship("SCHEDULED")
                .delaySeconds(120) // 2 minutes late
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Minimal valid trip update should pass validation")
        void minimalValidTripUpdate() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Required Field Validation Tests")
    class RequiredFieldTests {

        @Test
        @DisplayName("Null trip update should fail validation")
        void nullTripUpdate() {
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("TripUpdate cannot be null");
        }

        @Test
        @DisplayName("Missing trip ID should fail validation")
        void missingTripId() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .routeId("ROUTE456")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Trip ID is required");
        }

        @Test
        @DisplayName("Empty trip ID should fail validation")
        void emptyTripId() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("   ")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Trip ID is required");
        }
    }

    @Nested
    @DisplayName("Schedule Relationship Validation Tests")
    class ScheduleRelationshipTests {

        @Test
        @DisplayName("Invalid schedule relationship should fail validation")
        void invalidScheduleRelationship() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .scheduleRelationship("INVALID_RELATIONSHIP")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid schedule_relationship: INVALID_RELATIONSHIP");
        }

        @Test
        @DisplayName("Valid schedule relationships should pass validation")
        void validScheduleRelationships() {
            String[] validRelationships = {"SCHEDULED", "ADDED", "UNSCHEDULED", "CANCELED"};

            for (String relationship : validRelationships) {
                TripUpdate tripUpdate = TripUpdate.builder()
                    .tripId("TRIP123")
                    .scheduleRelationship(relationship)
                    .build();

                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

                assertThat(result.isValid()).isTrue();
                assertThat(result.getErrors()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Date Format Validation Tests")
    class DateFormatValidationTests {

        @Test
        @DisplayName("Invalid date format should fail validation")
        void invalidDateFormat() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startDate("2024-01-01") // Wrong format, should be YYYYMMDD
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Start date must be in YYYYMMDD format");
        }

        @Test
        @DisplayName("Invalid date value should fail validation")
        void invalidDateValue() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startDate("20240230") // February 30th doesn't exist
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid start date: 20240230");
        }

        @Test
        @DisplayName("Short date format should fail validation")
        void shortDateFormat() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startDate("240101") // Too short
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Start date must be in YYYYMMDD format");
        }

        @Test
        @DisplayName("Valid date format should pass validation")
        void validDateFormat() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startDate("20240315")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Time Format Validation Tests")
    class TimeFormatValidationTests {

        @Test
        @DisplayName("Invalid time format should fail validation")
        void invalidTimeFormat() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("8:30") // Should be HH:MM:SS
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Start time must be in HH:MM:SS format");
        }

        @Test
        @DisplayName("Invalid hour value should fail validation")
        void invalidHourValue() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("48:30:00") // Hours > 47 not allowed
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Hours must be between 0 and 47");
        }

        @Test
        @DisplayName("Invalid minute value should fail validation")
        void invalidMinuteValue() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("08:60:00") // Minutes > 59 not allowed
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Minutes must be between 0 and 59");
        }

        @Test
        @DisplayName("Invalid second value should fail validation")
        void invalidSecondValue() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("08:30:60") // Seconds > 59 not allowed
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Seconds must be between 0 and 59");
        }

        @Test
        @DisplayName("Negative time values should fail validation")
        void negativeTimeValues() {
            TripUpdate tripUpdate1 = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("-1:30:00")
                .build();

            TripUpdate tripUpdate2 = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("08:-5:00")
                .build();

            TripUpdate tripUpdate3 = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("08:30:-10")
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateTripUpdate(tripUpdate1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateTripUpdate(tripUpdate2);
            GTFSRTValidator.ValidationResult result3 = GTFSRTValidator.validateTripUpdate(tripUpdate3);

            assertThat(result1.isValid()).isFalse();
            assertThat(result1.getErrors()).contains("Hours must be between 0 and 47");

            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrors()).contains("Minutes must be between 0 and 59");

            assertThat(result3.isValid()).isFalse();
            assertThat(result3.getErrors()).contains("Seconds must be between 0 and 59");
        }

        @Test
        @DisplayName("Non-numeric time values should fail validation")
        void nonNumericTimeValues() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("XX:30:00")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid time format: XX:30:00");
        }

        @Test
        @DisplayName("Valid time formats should pass validation")
        void validTimeFormats() {
            // Test normal times
            TripUpdate tripUpdate1 = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("08:30:00")
                .build();

            // Test midnight crossing times (GTFS allows hours > 24)
            TripUpdate tripUpdate2 = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("25:30:00") // 1:30 AM next day
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateTripUpdate(tripUpdate1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateTripUpdate(tripUpdate2);

            assertThat(result1.isValid()).isTrue();
            assertThat(result1.getErrors()).isEmpty();

            assertThat(result2.isValid()).isTrue();
            assertThat(result2.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delay Validation Tests")
    class DelayValidationTests {

        @Test
        @DisplayName("Reasonable delays should not generate warnings")
        void reasonableDelays() {
            TripUpdate tripUpdate1 = TripUpdate.builder()
                .tripId("TRIP123")
                .vehicleId("RTD001")
                .delaySeconds(300) // 5 minutes late
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            TripUpdate tripUpdate2 = TripUpdate.builder()
                .tripId("TRIP123")
                .vehicleId("RTD002")
                .delaySeconds(-180) // 3 minutes early
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateTripUpdate(tripUpdate1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateTripUpdate(tripUpdate2);

            assertThat(result1.isValid()).isTrue();
            assertThat(result1.hasWarnings()).isFalse();

            assertThat(result2.isValid()).isTrue();
            assertThat(result2.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("Extreme delays should generate warnings")
        void extremeDelays() {
            TripUpdate tripUpdate1 = TripUpdate.builder()
                .tripId("TRIP123")
                .delaySeconds(7500) // 125 minutes late
                .build();

            TripUpdate tripUpdate2 = TripUpdate.builder()
                .tripId("TRIP123")
                .delaySeconds(-8000) // 133 minutes early
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateTripUpdate(tripUpdate1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateTripUpdate(tripUpdate2);

            assertThat(result1.isValid()).isTrue();
            assertThat(result1.hasWarnings()).isTrue();
            assertThat(result1.getWarnings()).anyMatch(warning -> 
                warning.contains("seems unusually large"));

            assertThat(result2.isValid()).isTrue();
            assertThat(result2.hasWarnings()).isTrue();
            assertThat(result2.getWarnings()).anyMatch(warning -> 
                warning.contains("seems unusually large"));
        }
    }

    @Nested
    @DisplayName("Timestamp Validation Tests")
    class TimestampValidationTests {

        @Test
        @DisplayName("Missing timestamp should generate warning")
        void missingTimestamp() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is missing");
        }

        @Test
        @DisplayName("Old timestamp should generate warning")
        void oldTimestamp() {
            long oldTimestamp = Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000); // 25 hours ago
            
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .timestamp_ms(oldTimestamp)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }

        @Test
        @DisplayName("Future timestamp should generate warning")
        void futureTimestamp() {
            long futureTimestamp = Instant.now().toEpochMilli() + (25 * 60 * 60 * 1000); // 25 hours from now
            
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .timestamp_ms(futureTimestamp)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }
    }

    @Nested
    @DisplayName("Cross-Validation Tests")
    class CrossValidationTests {

        @Test
        @DisplayName("Trip update with trip_id but no vehicle_id should generate warning")
        void tripUpdateWithoutVehicleId() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .routeId("ROUTE456")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Trip update has trip_id but no vehicle_id");
        }

        @Test
        @DisplayName("Trip update with both trip_id and vehicle_id should not generate warning")
        void tripUpdateWithVehicleId() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .vehicleId("RTD001")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Trip update has trip_id but no vehicle_id"));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Zero delay should be valid")
        void zeroDelay() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .delaySeconds(0)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Current timestamp should be valid")
        void currentTimestamp() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Timestamp is more than 24 hours old"));
        }

        @Test
        @DisplayName("Midnight boundary time should be valid")
        void midnightBoundaryTime() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .tripId("TRIP123")
                .startTime("00:00:00")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }
}