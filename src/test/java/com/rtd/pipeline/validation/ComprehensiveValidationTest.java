package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;
import static com.rtd.pipeline.validation.TestDataBuilder.*;

/**
 * Comprehensive test suite for all GTFS-RT validation utilities.
 * Uses TestDataBuilder for consistent test data creation across all message types.
 */
class ComprehensiveValidationTest {

    @Nested
    @DisplayName("VehiclePosition Validation Tests")
    class VehiclePositionValidationTests {

        @Test
        @DisplayName("Valid vehicle position should pass all validations")
        void validVehiclePositionPassesAllValidations() {
            VehiclePosition position = validVehiclePosition().build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Invalid coordinates should fail validation")
        void invalidCoordinatesFailValidation() {
            VehiclePosition position = validVehiclePosition()
                .invalidCoordinates()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).hasSize(2);
            assertThat(result.getErrors()).anyMatch(error -> error.contains("Latitude must be between"));
            assertThat(result.getErrors()).anyMatch(error -> error.contains("Longitude must be between"));
        }

        @Test
        @DisplayName("High speed should generate warning but remain valid")
        void highSpeedGeneratesWarning() {
            VehiclePosition position = validVehiclePosition()
                .highSpeed()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("Speed over 200 km/h seems unusually high"));
        }

        @Test
        @DisplayName("Position outside Denver area should generate warning")
        void outsideDenverAreaGeneratesWarning() {
            VehiclePosition position = validVehiclePosition()
                .outsideDenverArea()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(position);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("outside Denver metro area"));
        }
    }

    @Nested
    @DisplayName("TripUpdate Validation Tests")
    class TripUpdateValidationTests {

        @Test
        @DisplayName("Valid trip update should pass all validations")
        void validTripUpdatePassesAllValidations() {
            TripUpdate tripUpdate = validTripUpdate().build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Missing trip ID should fail validation")
        void missingTripIdFailsValidation() {
            TripUpdate tripUpdate = validTripUpdate()
                .nullRequired()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Trip ID is required");
        }

        @Test
        @DisplayName("Invalid schedule relationship should fail validation")
        void invalidScheduleRelationshipFailsValidation() {
            TripUpdate tripUpdate = validTripUpdate()
                .invalidScheduleRelationship()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Invalid schedule_relationship"));
        }

        @Test
        @DisplayName("Invalid date format should fail validation")
        void invalidDateFormatFailsValidation() {
            TripUpdate tripUpdate = validTripUpdate()
                .invalidDateFormat()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Start date must be in YYYYMMDD format"));
        }

        @Test
        @DisplayName("Invalid time format should fail validation")
        void invalidTimeFormatFailsValidation() {
            TripUpdate tripUpdate = validTripUpdate()
                .invalidTimeFormat()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Start time must be in HH:MM:SS format"));
        }

        @Test
        @DisplayName("Late night time should pass validation")
        void lateNightTimePassesValidation() {
            TripUpdate tripUpdate = validTripUpdate()
                .lateNightTime()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Large delay should generate warning")
        void largeDelayGeneratesWarning() {
            TripUpdate tripUpdate = validTripUpdate()
                .largeDelay()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("seems unusually large"));
        }
    }

    @Nested
    @DisplayName("Alert Validation Tests")
    class AlertValidationTests {

        @Test
        @DisplayName("Valid alert should pass all validations")
        void validAlertPassesAllValidations() {
            Alert alert = validAlert().build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Missing alert ID should fail validation")
        void missingAlertIdFailsValidation() {
            Alert alert = validAlert()
                .nullRequired()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Alert ID is required");
        }

        @Test
        @DisplayName("Invalid cause should fail validation")
        void invalidCauseFailsValidation() {
            Alert alert = validAlert()
                .invalidCause()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Invalid cause"));
        }

        @Test
        @DisplayName("Invalid effect should fail validation")
        void invalidEffectFailsValidation() {
            Alert alert = validAlert()
                .invalidEffect()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Invalid effect"));
        }

        @Test
        @DisplayName("Missing text should generate warning")
        void missingTextGeneratesWarning() {
            Alert alert = validAlert()
                .noText()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("should have either header_text or description_text"));
        }

        @Test
        @DisplayName("Invalid URL should fail validation")
        void invalidUrlFailsValidation() {
            Alert alert = validAlert()
                .invalidUrl()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Invalid URL format"));
        }

        @Test
        @DisplayName("Invalid active period should fail validation")
        void invalidActivePeriodFailsValidation() {
            Alert alert = validAlert()
                .invalidActivePeriod()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(error -> 
                error.contains("Active period start must be before end"));
        }

        @Test
        @DisplayName("Expired alert should generate warning")
        void expiredAlertGeneratesWarning() {
            Alert alert = validAlert()
                .expiredAlert()
                .build();
            
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
            
            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).anyMatch(warning -> 
                warning.contains("has expired"));
        }
    }

    @Nested
    @DisplayName("Cross-Message Type Tests")
    class CrossMessageTypeTests {

        @Test
        @DisplayName("All valid messages should pass validation")
        void allValidMessagesPassValidation() {
            VehiclePosition position = validVehiclePosition().build();
            TripUpdate tripUpdate = validTripUpdate().build();
            Alert alert = validAlert().build();
            
            GTFSRTValidator.ValidationResult positionResult = GTFSRTValidator.validateVehiclePosition(position);
            GTFSRTValidator.ValidationResult tripUpdateResult = GTFSRTValidator.validateTripUpdate(tripUpdate);
            GTFSRTValidator.ValidationResult alertResult = GTFSRTValidator.validateAlert(alert);
            
            assertThat(positionResult.isValid()).isTrue();
            assertThat(tripUpdateResult.isValid()).isTrue();
            assertThat(alertResult.isValid()).isTrue();
            
            assertThat(positionResult.getErrors()).isEmpty();
            assertThat(tripUpdateResult.getErrors()).isEmpty();
            assertThat(alertResult.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Timestamp validation consistency across message types")
        void timestampValidationConsistencyAcrossMessageTypes() {
            long oldTimestamp = System.currentTimeMillis() - (25 * 60 * 60 * 1000); // 25 hours ago
            
            VehiclePosition position = validVehiclePosition().timestamp(oldTimestamp).build();
            TripUpdate tripUpdate = validTripUpdate().timestamp(oldTimestamp).build();
            Alert alert = validAlert().timestamp(oldTimestamp).build();
            
            GTFSRTValidator.ValidationResult positionResult = GTFSRTValidator.validateVehiclePosition(position);
            GTFSRTValidator.ValidationResult tripUpdateResult = GTFSRTValidator.validateTripUpdate(tripUpdate);
            GTFSRTValidator.ValidationResult alertResult = GTFSRTValidator.validateAlert(alert);
            
            // All should be valid but have timestamp warnings
            assertThat(positionResult.isValid()).isTrue();
            assertThat(tripUpdateResult.isValid()).isTrue();
            assertThat(alertResult.isValid()).isTrue();
            
            assertThat(positionResult.hasWarnings()).isTrue();
            assertThat(tripUpdateResult.hasWarnings()).isTrue();
            assertThat(alertResult.hasWarnings()).isTrue();
            
            // All should have similar timestamp warning messages
            String expectedWarning = "more than 24 hours old or in the future";
            assertThat(positionResult.getWarnings()).anyMatch(w -> w.contains(expectedWarning));
            assertThat(tripUpdateResult.getWarnings()).anyMatch(w -> w.contains(expectedWarning));
            assertThat(alertResult.getWarnings()).anyMatch(w -> w.contains(expectedWarning));
        }
    }

    @Nested
    @DisplayName("Fixture Tests")
    class FixtureTests {

        @Test
        @DisplayName("Pre-built fixtures should work correctly")
        void preBuiltFixturesWorkCorrectly() {
            // Test valid fixtures
            GTFSRTValidator.ValidationResult validPositionResult = 
                GTFSRTValidator.validateVehiclePosition(Fixtures.VALID_VEHICLE_POSITION);
            GTFSRTValidator.ValidationResult validTripUpdateResult = 
                GTFSRTValidator.validateTripUpdate(Fixtures.VALID_TRIP_UPDATE);
            GTFSRTValidator.ValidationResult validAlertResult = 
                GTFSRTValidator.validateAlert(Fixtures.VALID_ALERT);
            
            assertThat(validPositionResult.isValid()).isTrue();
            assertThat(validTripUpdateResult.isValid()).isTrue();
            assertThat(validAlertResult.isValid()).isTrue();
            
            // Test invalid fixtures
            GTFSRTValidator.ValidationResult invalidPositionResult = 
                GTFSRTValidator.validateVehiclePosition(Fixtures.VEHICLE_POSITION_INVALID_COORDS);
            GTFSRTValidator.ValidationResult invalidTripUpdateResult = 
                GTFSRTValidator.validateTripUpdate(Fixtures.TRIP_UPDATE_INVALID_TIME);
            GTFSRTValidator.ValidationResult invalidAlertResult = 
                GTFSRTValidator.validateAlert(Fixtures.ALERT_NO_TEXT);
            
            assertThat(invalidPositionResult.isValid()).isFalse();
            assertThat(invalidTripUpdateResult.isValid()).isFalse();
            assertThat(invalidAlertResult.isValid()).isTrue(); // Warning only
            assertThat(invalidAlertResult.hasWarnings()).isTrue();
        }
    }
}