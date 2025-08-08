package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;

/**
 * Comprehensive test suite for Alert validation according to GTFS-RT specification.
 * Tests cover required fields, enum values, URL validation, active periods, and text content.
 */
class AlertValidationTest {

    @Nested
    @DisplayName("Valid Alert Tests")
    class ValidAlertTests {

        @Test
        @DisplayName("Valid complete alert should pass validation")
        void validCompleteAlert() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .cause("CONSTRUCTION")
                .effect("DETOUR")
                .headerText("Service Alert")
                .descriptionText("Bus route 15 is temporarily detoured due to construction on Main St.")
                .url("https://www.rtd-denver.com/alerts/construction-main-st")
                .activePeriodStart(currentTime)
                .activePeriodEnd(currentTime + 7200) // 2 hours from now
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Minimal valid alert should pass validation")
        void minimalValidAlert() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Required Field Validation Tests")
    class RequiredFieldTests {

        @Test
        @DisplayName("Null alert should fail validation")
        void nullAlert() {
            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Alert cannot be null");
        }

        @Test
        @DisplayName("Missing alert ID should fail validation")
        void missingAlertId() {
            Alert alert = Alert.builder()
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Alert ID is required");
        }

        @Test
        @DisplayName("Empty alert ID should fail validation")
        void emptyAlertId() {
            Alert alert = Alert.builder()
                .alertId("   ")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Alert ID is required");
        }
    }

    @Nested
    @DisplayName("Enum Validation Tests")
    class EnumValidationTests {

        @Test
        @DisplayName("Invalid cause should fail validation")
        void invalidCause() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .cause("INVALID_CAUSE")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid cause: INVALID_CAUSE");
        }

        @Test
        @DisplayName("Invalid effect should fail validation")
        void invalidEffect() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .effect("INVALID_EFFECT")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid effect: INVALID_EFFECT");
        }

        @Test
        @DisplayName("Valid causes should pass validation")
        void validCauses() {
            String[] validCauses = {
                "UNKNOWN_CAUSE", "OTHER_CAUSE", "TECHNICAL_PROBLEM", "STRIKE",
                "DEMONSTRATION", "ACCIDENT", "HOLIDAY", "WEATHER", 
                "MAINTENANCE", "CONSTRUCTION", "POLICE_ACTIVITY", "MEDICAL_EMERGENCY"
            };

            for (String cause : validCauses) {
                Alert alert = Alert.builder()
                    .alertId("ALERT123")
                    .cause(cause)
                    .headerText("Service Alert")
                    .build();

                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

                assertThat(result.isValid()).isTrue();
                assertThat(result.getErrors()).isEmpty();
            }
        }

        @Test
        @DisplayName("Valid effects should pass validation")
        void validEffects() {
            String[] validEffects = {
                "NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS", "DETOUR",
                "ADDITIONAL_SERVICE", "MODIFIED_SERVICE", "OTHER_EFFECT",
                "UNKNOWN_EFFECT", "STOP_MOVED", "NO_EFFECT", "ACCESSIBILITY_ISSUE"
            };

            for (String effect : validEffects) {
                Alert alert = Alert.builder()
                    .alertId("ALERT123")
                    .effect(effect)
                    .headerText("Service Alert")
                    .build();

                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

                assertThat(result.isValid()).isTrue();
                assertThat(result.getErrors()).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Text Content Validation Tests")
    class TextContentValidationTests {

        @Test
        @DisplayName("Missing both header and description text should generate warning")
        void missingBothTexts() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Alert should have either header_text or description_text");
        }

        @Test
        @DisplayName("Empty header and description text should generate warning")
        void emptyBothTexts() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .headerText("   ")
                .descriptionText("")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Alert should have either header_text or description_text");
        }

        @Test
        @DisplayName("Having header text should not generate warning")
        void havingHeaderText() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Alert should have either header_text or description_text"));
        }

        @Test
        @DisplayName("Having description text should not generate warning")
        void havingDescriptionText() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .descriptionText("Bus route 15 is temporarily detoured.")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Alert should have either header_text or description_text"));
        }
    }

    @Nested
    @DisplayName("URL Validation Tests")
    class URLValidationTests {

        @Test
        @DisplayName("Invalid URL format should fail validation")
        void invalidUrlFormat() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .url("not-a-valid-url")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Invalid URL format: not-a-valid-url");
        }

        @Test
        @DisplayName("Valid HTTP URL should pass validation")
        void validHttpUrl() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .url("http://www.rtd-denver.com/alerts")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Valid HTTPS URL should pass validation")
        void validHttpsUrl() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .url("https://www.rtd-denver.com/alerts/construction")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Empty URL should not cause validation error")
        void emptyUrl() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .url("")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Whitespace-only URL should not cause validation error")
        void whitespaceOnlyUrl() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .url("   ")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Active Period Validation Tests")
    class ActivePeriodValidationTests {

        @Test
        @DisplayName("Start time after end time should fail validation")
        void startAfterEnd() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodStart(currentTime + 3600) // 1 hour from now
                .activePeriodEnd(currentTime) // now
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Active period start must be before end");
        }

        @Test
        @DisplayName("Start time equal to end time should fail validation")
        void startEqualsEnd() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodStart(currentTime)
                .activePeriodEnd(currentTime)
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).contains("Active period start must be before end");
        }

        @Test
        @DisplayName("Valid active period should pass validation")
        void validActivePeriod() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodStart(currentTime)
                .activePeriodEnd(currentTime + 3600) // 1 hour from now
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Expired alert should generate warning")
        void expiredAlert() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodStart(currentTime - 7200) // 2 hours ago
                .activePeriodEnd(currentTime - 3600) // 1 hour ago
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Alert has expired (active period end is in the past)");
        }

        @Test
        @DisplayName("Only start time provided should not cause errors")
        void onlyStartTime() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodStart(currentTime)
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Only end time provided should not cause errors")
        void onlyEndTime() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .activePeriodEnd(currentTime + 3600)
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

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
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is missing");
        }

        @Test
        @DisplayName("Old timestamp should generate warning")
        void oldTimestamp() {
            long oldTimestamp = Instant.now().toEpochMilli() - (25 * 60 * 60 * 1000); // 25 hours ago
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .timestamp_ms(oldTimestamp)
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }

        @Test
        @DisplayName("Future timestamp should generate warning")
        void futureTimestamp() {
            long futureTimestamp = Instant.now().toEpochMilli() + (25 * 60 * 60 * 1000); // 25 hours from now
            
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .timestamp_ms(futureTimestamp)
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isTrue();
            assertThat(result.getWarnings()).contains("Timestamp is more than 24 hours old or in the future");
        }

        @Test
        @DisplayName("Current timestamp should not generate warnings")
        void currentTimestamp() {
            Alert alert = Alert.builder()
                .alertId("ALERT123")
                .timestamp_ms(Instant.now().toEpochMilli())
                .headerText("Service Alert")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getWarnings()).noneMatch(warning -> 
                warning.contains("Timestamp is more than 24 hours old"));
        }
    }

    @Nested
    @DisplayName("Complete Scenario Tests")
    class CompleteScenarioTests {

        @Test
        @DisplayName("Typical construction alert should pass validation")
        void typicalConstructionAlert() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("RTD_CONST_2024_001")
                .cause("CONSTRUCTION")
                .effect("DETOUR")
                .headerText("Route 15 Detour")
                .descriptionText("Route 15 buses will detour around Main Street construction through March 15th. " +
                               "Temporary stops have been established on 1st and 2nd Streets.")
                .url("https://www.rtd-denver.com/alerts/route-15-detour")
                .activePeriodStart(currentTime)
                .activePeriodEnd(currentTime + (30L * 24 * 60 * 60)) // 30 days
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.hasWarnings()).isFalse();
        }

        @Test
        @DisplayName("Emergency service alert should pass validation")
        void emergencyServiceAlert() {
            Alert alert = Alert.builder()
                .alertId("RTD_EMERG_2024_001")
                .cause("POLICE_ACTIVITY")
                .effect("NO_SERVICE")
                .headerText("Light Rail Service Suspended")
                .descriptionText("Light rail service is temporarily suspended between Union Station and " +
                               "38th & Blake due to police activity. Shuttle buses are being deployed.")
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("Weather-related service modification should pass validation")
        void weatherServiceModification() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert alert = Alert.builder()
                .alertId("RTD_WEATHER_2024_001")
                .cause("WEATHER")
                .effect("MODIFIED_SERVICE")
                .headerText("Snow Service in Effect")
                .descriptionText("Due to heavy snow, buses are operating on snow routes. " +
                               "Some stops may be temporarily relocated.")
                .activePeriodStart(currentTime - 3600) // Started 1 hour ago
                .activePeriodEnd(currentTime + (12 * 60 * 60)) // Ends in 12 hours
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.hasWarnings()).isFalse();
        }
    }
}