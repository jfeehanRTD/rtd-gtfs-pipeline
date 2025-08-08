package com.rtd.pipeline.validation;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Integration tests for validating RTD GTFS-RT feeds.
 * Tests realistic scenarios with actual RTD-like data patterns and edge cases.
 */
class RTDFeedValidationIntegrationTest {

    @Nested
    @DisplayName("RTD Vehicle Position Feed Validation")
    class RTDVehiclePositionFeedTests {

        @Test
        @DisplayName("Typical RTD bus fleet should pass validation")
        void typicalRTDBusFleet() {
            List<VehiclePosition> vehicles = createTypicalRTDBusFleet();
            List<GTFSRTValidator.ValidationResult> results = new ArrayList<>();

            for (VehiclePosition vehicle : vehicles) {
                results.add(GTFSRTValidator.validateVehiclePosition(vehicle));
            }

            // All vehicles should be valid
            assertThat(results).allMatch(GTFSRTValidator.ValidationResult::isValid);
            
            // Count warnings for analysis
            long warningCount = results.stream()
                .mapToLong(result -> result.getWarnings().size())
                .sum();
            
            // Some warnings are acceptable for a real fleet
            assertThat(warningCount).isLessThan(vehicles.size() * 2); // Max 2 warnings per vehicle
        }

        @Test
        @DisplayName("Light rail vehicles should pass validation")
        void lightRailVehicles() {
            List<VehiclePosition> lightRail = createLightRailVehicles();

            for (VehiclePosition vehicle : lightRail) {
                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(vehicle);
                
                assertThat(result.isValid()).isTrue();
                // Light rail may have higher speeds, which should generate warnings but not errors
                if (!result.getWarnings().isEmpty()) {
                    assertThat(result.getWarnings()).anyMatch(warning -> 
                        warning.contains("Speed") || warning.contains("outside Denver metro area"));
                }
            }
        }

        @Test
        @DisplayName("Express bus routes should handle suburban coordinates")
        void expressBusRoutes() {
            VehiclePosition expressBus = VehiclePosition.builder()
                .vehicleId("RTD_EXPRESS_101")
                .tripId("EXP_AB1_123")
                .routeId("AB1")
                .latitude(39.8781) // Thornton area
                .longitude(-104.8722)
                .bearing(90.0f)
                .speed(85.0f) // Highway speed
                .timestamp_ms(Instant.now().toEpochMilli())
                .currentStatus("IN_TRANSIT_TO")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateVehiclePosition(expressBus);

            assertThat(result.isValid()).isTrue();
            // May have warnings for location or speed, but should be valid
        }

        private List<VehiclePosition> createTypicalRTDBusFleet() {
            long currentTime = Instant.now().toEpochMilli();
            
            return Arrays.asList(
                // Downtown bus
                VehiclePosition.builder()
                    .vehicleId("RTD_8001")
                    .tripId("TRIP_15_123")
                    .routeId("15")
                    .latitude(39.7392)
                    .longitude(-104.9903)
                    .bearing(180.0f)
                    .speed(25.0f)
                    .timestamp_ms(currentTime)
                    .currentStatus("STOPPED_AT")
                    .congestionLevel("RUNNING_SMOOTHLY")
                    .occupancyStatus("FEW_SEATS_AVAILABLE")
                    .build(),
                    
                // Airport route
                VehiclePosition.builder()
                    .vehicleId("RTD_AF001")
                    .tripId("TRIP_AF_456")
                    .routeId("AF")
                    .latitude(39.8617)
                    .longitude(-104.6731) // Near DEN
                    .bearing(45.0f)
                    .speed(55.0f)
                    .timestamp_ms(currentTime - 30000) // 30 seconds ago
                    .currentStatus("IN_TRANSIT_TO")
                    .congestionLevel("STOP_AND_GO")
                    .occupancyStatus("STANDING_ROOM_ONLY")
                    .build(),
                    
                // Local route
                VehiclePosition.builder()
                    .vehicleId("RTD_3012")
                    .tripId("TRIP_32_789")
                    .routeId("32")
                    .latitude(39.7117)
                    .longitude(-105.0178)
                    .bearing(270.0f)
                    .speed(15.0f)
                    .timestamp_ms(currentTime - 60000) // 1 minute ago
                    .currentStatus("IN_TRANSIT_TO")
                    .congestionLevel("CONGESTION")
                    .occupancyStatus("MANY_SEATS_AVAILABLE")
                    .build()
            );
        }

        private List<VehiclePosition> createLightRailVehicles() {
            long currentTime = Instant.now().toEpochMilli();
            
            return Arrays.asList(
                // A-Line to Airport
                VehiclePosition.builder()
                    .vehicleId("RTD_LR_A001")
                    .tripId("TRIP_A_123")
                    .routeId("A")
                    .latitude(39.7686)
                    .longitude(-104.8769)
                    .bearing(65.0f)
                    .speed(75.0f) // Higher speed for light rail
                    .timestamp_ms(currentTime)
                    .currentStatus("IN_TRANSIT_TO")
                    .occupancyStatus("FEW_SEATS_AVAILABLE")
                    .build(),
                    
                // C-Line
                VehiclePosition.builder()
                    .vehicleId("RTD_LR_C002")
                    .tripId("TRIP_C_456")
                    .routeId("C")
                    .latitude(39.7547)
                    .longitude(-105.0053)
                    .bearing(315.0f)
                    .speed(45.0f)
                    .timestamp_ms(currentTime - 45000)
                    .currentStatus("STOPPED_AT")
                    .occupancyStatus("STANDING_ROOM_ONLY")
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("RTD Trip Update Feed Validation")
    class RTDTripUpdateFeedTests {

        @Test
        @DisplayName("Typical RTD trip updates should pass validation")
        void typicalRTDTripUpdates() {
            List<TripUpdate> tripUpdates = createTypicalRTDTripUpdates();

            for (TripUpdate tripUpdate : tripUpdates) {
                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(tripUpdate);
                
                assertThat(result.isValid()).isTrue();
            }
        }

        @Test
        @DisplayName("Peak hour delays should be realistic")
        void peakHourDelays() {
            TripUpdate peakHourTrip = TripUpdate.builder()
                .tripId("TRIP_15_PEAK_456")
                .routeId("15")
                .vehicleId("RTD_8001")
                .startDate("20240315")
                .startTime("08:30:00")
                .scheduleRelationship("SCHEDULED")
                .delaySeconds(480) // 8 minutes late - realistic for peak hour
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(peakHourTrip);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isFalse(); // 8 minutes should not generate warnings
        }

        @Test
        @DisplayName("Early morning service with cross-midnight times")
        void earlyMorningService() {
            TripUpdate earlyService = TripUpdate.builder()
                .tripId("TRIP_NIGHT_001")
                .routeId("NIGHT")
                .vehicleId("RTD_NIGHT_001")
                .startDate("20240315")
                .startTime("25:30:00") // 1:30 AM next day
                .scheduleRelationship("SCHEDULED")
                .delaySeconds(-60) // 1 minute early
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateTripUpdate(earlyService);

            assertThat(result.isValid()).isTrue();
        }

        private List<TripUpdate> createTypicalRTDTripUpdates() {
            long currentTime = Instant.now().toEpochMilli();
            
            return Arrays.asList(
                // On-time trip
                TripUpdate.builder()
                    .tripId("TRIP_15_123")
                    .routeId("15")
                    .vehicleId("RTD_8001")
                    .startDate("20240315")
                    .startTime("09:15:00")
                    .scheduleRelationship("SCHEDULED")
                    .delaySeconds(0)
                    .timestamp_ms(currentTime)
                    .build(),
                    
                // Slightly delayed trip
                TripUpdate.builder()
                    .tripId("TRIP_32_456")
                    .routeId("32")
                    .vehicleId("RTD_3012")
                    .startDate("20240315")
                    .startTime("14:22:00")
                    .scheduleRelationship("SCHEDULED")
                    .delaySeconds(180) // 3 minutes late
                    .timestamp_ms(currentTime - 30000)
                    .build(),
                    
                // Canceled trip
                TripUpdate.builder()
                    .tripId("TRIP_44_789")
                    .routeId("44")
                    .startDate("20240315")
                    .startTime("16:45:00")
                    .scheduleRelationship("CANCELED")
                    .timestamp_ms(currentTime - 120000) // 2 minutes ago
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("RTD Service Alert Feed Validation")
    class RTDServiceAlertFeedTests {

        @Test
        @DisplayName("Typical RTD service alerts should pass validation")
        void typicalRTDServiceAlerts() {
            List<Alert> alerts = createTypicalRTDServiceAlerts();

            for (Alert alert : alerts) {
                GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(alert);
                
                assertThat(result.isValid()).isTrue();
            }
        }

        @Test
        @DisplayName("Multi-day construction alert should be valid")
        void multiDayConstructionAlert() {
            long currentTime = Instant.now().getEpochSecond();
            
            Alert constructionAlert = Alert.builder()
                .alertId("RTD_CONST_I25_2024")
                .cause("CONSTRUCTION")
                .effect("DETOUR")
                .headerText("I-25 Construction Impacts")
                .descriptionText("Routes FF1, FF2, and FF5 will use alternate stops during I-25 construction. " +
                               "Construction is expected to last through summer 2024.")
                .url("https://www.rtd-denver.com/alerts/i25-construction")
                .activePeriodStart(currentTime - (7L * 24 * 60 * 60)) // Started 7 days ago
                .activePeriodEnd(currentTime + (90L * 24 * 60 * 60)) // Ends in 90 days
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            GTFSRTValidator.ValidationResult result = GTFSRTValidator.validateAlert(constructionAlert);

            assertThat(result.isValid()).isTrue();
            assertThat(result.hasWarnings()).isFalse();
        }

        private List<Alert> createTypicalRTDServiceAlerts() {
            long currentTime = Instant.now().getEpochSecond();
            long currentTimeMs = Instant.now().toEpochMilli();
            
            return Arrays.asList(
                // Weather delay
                Alert.builder()
                    .alertId("RTD_WEATHER_20240315")
                    .cause("WEATHER")
                    .effect("SIGNIFICANT_DELAYS")
                    .headerText("Snow Delays Expected")
                    .descriptionText("Heavy snow is causing delays of 15-30 minutes system-wide. " +
                                   "Allow extra travel time.")
                    .activePeriodStart(currentTime - 3600) // Started 1 hour ago
                    .activePeriodEnd(currentTime + (8 * 60 * 60)) // 8 more hours
                    .timestamp_ms(currentTimeMs)
                    .build(),
                    
                // Maintenance alert
                Alert.builder()
                    .alertId("RTD_MAINT_LR_20240315")
                    .cause("MAINTENANCE")
                    .effect("MODIFIED_SERVICE")
                    .headerText("Weekend Light Rail Maintenance")
                    .descriptionText("Light rail service will operate every 20 minutes instead of every 15 minutes " +
                                   "this weekend due to scheduled track maintenance.")
                    .url("https://www.rtd-denver.com/alerts/weekend-maintenance")
                    .activePeriodStart(currentTime + (2 * 24 * 60 * 60)) // Starts in 2 days
                    .activePeriodEnd(currentTime + (4 * 24 * 60 * 60)) // Ends in 4 days
                    .timestamp_ms(currentTimeMs)
                    .build(),
                    
                // Emergency alert
                Alert.builder()
                    .alertId("RTD_EMERG_20240315_001")
                    .cause("POLICE_ACTIVITY")
                    .effect("NO_SERVICE")
                    .headerText("Service Suspended")
                    .descriptionText("Bus service on Colfax Ave between Federal and Colorado Blvd is suspended " +
                                   "due to police activity. Passengers should use alternate routes.")
                    .activePeriodStart(currentTime - 1800) // Started 30 minutes ago
                    .timestamp_ms(currentTimeMs - 60000) // Alert created 1 minute ago
                    .build()
            );
        }
    }

    @Nested
    @DisplayName("Feed Data Quality Analysis")
    class FeedDataQualityTests {

        @Test
        @DisplayName("Vehicle positions should have reasonable data quality")
        void vehiclePositionDataQuality() {
            List<VehiclePosition> vehicles = createLargeVehicleFleet(100);
            List<GTFSRTValidator.ValidationResult> results = new ArrayList<>();

            for (VehiclePosition vehicle : vehicles) {
                results.add(GTFSRTValidator.validateVehiclePosition(vehicle));
            }

            // Quality metrics
            long validCount = results.stream()
                .mapToLong(result -> result.isValid() ? 1 : 0)
                .sum();
            
            long errorCount = results.stream()
                .mapToLong(result -> result.getErrors().size())
                .sum();

            long warningCount = results.stream()
                .mapToLong(result -> result.getWarnings().size())
                .sum();

            // Data quality assertions
            assertThat(validCount).isGreaterThan((long) (vehicles.size() * 0.95)); // 95%+ valid
            assertThat(errorCount).isLessThan((long) (vehicles.size() * 0.1)); // <10% error rate
            assertThat(warningCount).isLessThan((long) (vehicles.size() * 0.3)); // <30% warning rate
        }

        @Test
        @DisplayName("Feed should handle edge cases gracefully")
        void feedEdgeCases() {
            // Vehicle at exact coordinate boundaries
            VehiclePosition boundaryVehicle = VehiclePosition.builder()
                .vehicleId("RTD_BOUNDARY")
                .latitude(39.0) // Exactly at Denver boundary
                .longitude(-105.5)
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            // Trip with exact midnight time
            TripUpdate midnightTrip = TripUpdate.builder()
                .tripId("MIDNIGHT_TRIP")
                .startTime("24:00:00")
                .build();

            // Alert with no active period
            Alert openEndedAlert = Alert.builder()
                .alertId("OPEN_ENDED")
                .headerText("Ongoing Construction")
                .build();

            GTFSRTValidator.ValidationResult vehicleResult = GTFSRTValidator.validateVehiclePosition(boundaryVehicle);
            GTFSRTValidator.ValidationResult tripResult = GTFSRTValidator.validateTripUpdate(midnightTrip);
            GTFSRTValidator.ValidationResult alertResult = GTFSRTValidator.validateAlert(openEndedAlert);

            // Edge cases should be handled gracefully (valid or with appropriate warnings)
            assertThat(vehicleResult.isValid()).isTrue();
            assertThat(tripResult.isValid()).isTrue();
            assertThat(alertResult.isValid()).isTrue();
        }

        private List<VehiclePosition> createLargeVehicleFleet(int count) {
            List<VehiclePosition> vehicles = new ArrayList<>();
            long currentTime = Instant.now().toEpochMilli();
            
            for (int i = 0; i < count; i++) {
                // Simulate realistic RTD fleet distribution
                vehicles.add(VehiclePosition.builder()
                    .vehicleId("RTD_" + (8000 + i))
                    .tripId("TRIP_" + (i % 50) + "_" + (100 + i))
                    .routeId(String.valueOf((i % 20) + 1)) // Routes 1-20
                    .latitude(39.7392 + (Math.random() - 0.5) * 0.2) // Around Denver
                    .longitude(-104.9903 + (Math.random() - 0.5) * 0.2)
                    .bearing((float) (Math.random() * 360))
                    .speed((float) (Math.random() * 80)) // 0-80 km/h
                    .timestamp_ms(currentTime - (long) (Math.random() * 300000)) // Up to 5 minutes old
                    .currentStatus(Math.random() > 0.7 ? "STOPPED_AT" : "IN_TRANSIT_TO")
                    .build());
            }
            
            return vehicles;
        }
    }

    @Nested
    @DisplayName("Performance and Batch Validation")
    class PerformanceTests {

        @Test
        @DisplayName("Validation should handle large feed efficiently")
        void largeFleetValidationPerformance() {
            List<VehiclePosition> largeFleet = createLargeVehicleFleet(1000);
            
            long startTime = System.currentTimeMillis();
            
            List<GTFSRTValidator.ValidationResult> results = largeFleet.stream()
                .map(GTFSRTValidator::validateVehiclePosition)
                .toList();
                
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            // Should process 1000 vehicles in reasonable time (< 5 seconds)
            assertThat(duration).isLessThan(5000);
            assertThat(results).hasSize(1000);
            
            // Most should be valid
            long validCount = results.stream()
                .mapToLong(result -> result.isValid() ? 1 : 0)
                .sum();
            assertThat(validCount).isGreaterThan(900);
        }

        private List<VehiclePosition> createLargeVehicleFleet(int count) {
            // Reuse the method from FeedDataQualityTests
            List<VehiclePosition> vehicles = new ArrayList<>();
            long currentTime = Instant.now().toEpochMilli();
            
            for (int i = 0; i < count; i++) {
                vehicles.add(VehiclePosition.builder()
                    .vehicleId("PERF_TEST_" + i)
                    .latitude(39.7392 + (Math.random() - 0.5) * 0.1)
                    .longitude(-104.9903 + (Math.random() - 0.5) * 0.1)
                    .timestamp_ms(currentTime)
                    .build());
            }
            
            return vehicles;
        }
    }
}