package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;
import static com.rtd.pipeline.validation.TestDataBuilder.*;
import java.time.Instant;

/**
 * Comprehensive test suite for VehiclePosition validation according to GTFS-RT specification.
 * Tests cover required fields, coordinate validation, enum values, and RTD-specific requirements.
 */
class VehiclePositionValidationTest {

    @Nested
    @DisplayName("Valid VehiclePosition Tests")
    class ValidVehiclePositionTests {

        @Test
        @DisplayName("Valid complete vehicle position should pass validation")
        void validCompleteVehiclePosition() {
            VehiclePosition position = validVehiclePosition().build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Minimal valid vehicle position should pass validation")
        void minimalValidVehiclePosition() {
            VehiclePosition position = validVehiclePosition()
                .noTripOrRoute() // Remove optional fields
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Required Field Validation Tests")
    class RequiredFieldTests {

        @Test
        @DisplayName("Null vehicle position should fail validation")
        void nullVehiclePosition() {
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("VehiclePosition cannot be null");
        }

        @Test
        @DisplayName("Missing vehicle ID should fail validation")
        void missingVehicleId() {
            VehiclePosition position = validVehiclePosition()
                .vehicleId(null)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Vehicle ID is required");
        }

        @Test
        @DisplayName("Empty vehicle ID should fail validation")
        void emptyVehicleId() {
            VehiclePosition position = validVehiclePosition()
                .vehicleId("   ")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Vehicle ID is required");
        }

        @Test
        @DisplayName("Missing coordinates should fail validation")
        void missingCoordinates() {
            VehiclePosition position = validVehiclePosition()
                .nullRequired()
                .vehicleId("RTD001") // Keep vehicle ID valid but nullify coordinates
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Both latitude and longitude are required");
        }
    }

    @Nested
    @DisplayName("Coordinate Validation Tests")
    class CoordinateValidationTests {

        @Test
        @DisplayName("Invalid latitude bounds should fail validation")
        void invalidLatitudeBounds() {
            VehiclePosition position1 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(-91.0) // Below minimum
                .longitude(-104.9903)
                .build();

            VehiclePosition position2 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(91.0) // Above maximum
                .longitude(-104.9903)
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateVehiclePosition(position1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateVehiclePosition(position2);

            assertThat(result1.isValid()).isFalse();
            assertThat(result1.getErrors()).contains("Latitude must be between -90 and 90 degrees");
            
            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrors()).contains("Latitude must be between -90 and 90 degrees");
        }

        @Test
        @DisplayName("Invalid longitude bounds should fail validation")
        void invalidLongitudeBounds() {
            VehiclePosition position1 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-181.0) // Below minimum
                .build();

            VehiclePosition position2 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(181.0) // Above maximum
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateVehiclePosition(position1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateVehiclePosition(position2);

            assertThat(result1.isValid()).isFalse();
            assertThat(result1.getErrors()).contains("Longitude must be between -180 and 180 degrees");
            
            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrors()).contains("Longitude must be between -180 and 180 degrees");
        }

        @Test
        @DisplayName("Coordinates outside Denver metro area should generate warning")
        void coordinatesOutsideDenverArea() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(40.7128) // New York coordinates
                .longitude(-74.0060)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("outside Denver metro area"));
        }
    }

    @Nested
    @DisplayName("Bearing and Speed Validation Tests")
    class BearingSpeedValidationTests {

        @Test
        @DisplayName("Invalid bearing bounds should fail validation")
        void invalidBearingBounds() {
            VehiclePosition position1 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .bearing(-1.0f)
                .build();

            VehiclePosition position2 = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .bearing(361.0f)
                .build();

            GTFSRTValidator.ValidationResult result1 = GTFSRTValidator.validateVehiclePosition(position1);
            GTFSRTValidator.ValidationResult result2 = GTFSRTValidator.validateVehiclePosition(position2);

            assertThat(result1.isValid()).isFalse();
            assertThat(result1.getErrors()).contains("Bearing must be between 0 and 360 degrees");
            
            assertThat(result2.isValid()).isFalse();
            assertThat(result2.getErrors()).contains("Bearing must be between 0 and 360 degrees");
        }

        @Test
        @DisplayName("Negative speed should fail validation")
        void negativeSpeed() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .speed(-5.0f)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Speed cannot be negative");
        }

        @Test
        @DisplayName("Extremely high speed should generate warning")
        void extremelyHighSpeed() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .speed(250.0f)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Speed over 200 km/h seems unusually high for transit vehicle");
        }
    }

    @Nested
    @DisplayName("Enum Validation Tests")
    class EnumValidationTests {

        @Test
        @DisplayName("Invalid current status should fail validation")
        void invalidCurrentStatus() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .currentStatus("INVALID_STATUS")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid current_status: INVALID_STATUS");
        }

        @Test
        @DisplayName("Invalid congestion level should fail validation")
        void invalidCongestionLevel() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .congestionLevel("INVALID_CONGESTION")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid congestion_level: INVALID_CONGESTION");
        }

        @Test
        @DisplayName("Invalid occupancy status should fail validation")
        void invalidOccupancyStatus() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .occupancyStatus("INVALID_OCCUPANCY")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid occupancy_status: INVALID_OCCUPANCY");
        }

        @Test
        @DisplayName("Valid enum values should pass validation")
        void validEnumValues() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .currentStatus("STOPPED_AT")
                .congestionLevel("CONGESTION")
                .occupancyStatus("STANDING_ROOM_ONLY")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Timestamp Validation Tests")
    class TimestampValidationTests {

        @Test
        @DisplayName("Missing timestamp should generate warning")
        void missingTimestamp() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is missing");
        }

        @Test
        @DisplayName("Very old timestamp should generate warning")
        void veryOldTimestamp() {
            long oldTimestamp = Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000); // 25 hours ago
            
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .timestamp_ms(oldTimestamp)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }

        @Test
        @DisplayName("Future timestamp should generate warning")
        void futureTimestamp() {
            long futureTimestamp = Instant.now().toEpochMilli() + (25 * 60 * 60 * 1000); // 25 hours from now
            
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .timestamp_ms(futureTimestamp)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }
    }

    @Nested
    @DisplayName("Trip and Route Validation Tests")
    class TripRouteValidationTests {

        @Test
        @DisplayName("Missing both trip_id and route_id should generate warning")
        void missingTripAndRouteIds() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .latitude(39.7392)
                .longitude(-104.9903)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("Both trip_id and route_id are missing"));
        }

        @Test
        @DisplayName("Having trip_id should not generate warning")
        void havingTripId() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .tripId("TRIP123")
                .latitude(39.7392)
                .longitude(-104.9903)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Both trip_id and route_id are missing"));
        }

        @Test
        @DisplayName("Having route_id should not generate warning")
        void havingRouteId() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD001")
                .routeId("ROUTE456")
                .latitude(39.7392)
                .longitude(-104.9903)
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Both trip_id and route_id are missing"));
        }
    }
}