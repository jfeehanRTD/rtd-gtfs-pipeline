package com.rtd.pipeline;

import com.rtd.pipeline.model.HistoricalVehicleRecord;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.*;

/**
 * Test suite for the historical data model components that don't require Flink runtime.
 * Tests the core data transformations and business logic.
 */
class HistoricalDataModelTest {

    @Nested
    @DisplayName("Historical Vehicle Record Tests")
    class HistoricalVehicleRecordTests {

        @Test
        @DisplayName("Should create historical record from VehiclePosition")
        void shouldCreateFromVehiclePosition() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("RTD_BUS_001")
                .routeId("15")
                .tripId("TRIP_15_123")
                .latitude(39.7392)
                .longitude(-104.9903)
                .bearing(180.0f)
                .speed(25.0f)
                .timestamp_ms(Instant.now().toEpochMilli())
                .currentStatus("IN_TRANSIT_TO")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .congestionLevel("RUNNING_SMOOTHLY")
                .build();

            HistoricalVehicleRecord record = new HistoricalVehicleRecord(position);

            assertThat(record.getVehicleId()).isEqualTo("RTD_BUS_001");
            assertThat(record.getRouteId()).isEqualTo("15");
            assertThat(record.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
            assertThat(record.getLatitude()).isEqualTo(39.7392);
            assertThat(record.getLongitude()).isEqualTo(-104.9903);
            assertThat(record.getIsMissing()).isFalse();
            assertThat(record.getServiceDisruptionLevel()).isEqualTo(HistoricalVehicleRecord.DisruptionLevel.NORMAL);
            assertThat(record.getServiceDate()).isNotNull();
            assertThat(record.getServiceHour()).isNotNull();
            assertThat(record.getRecordId()).contains("RTD_BUS_001");
        }

        @Test
        @DisplayName("Should create historical record from TripUpdate")
        void shouldCreateFromTripUpdate() {
            TripUpdate tripUpdate = TripUpdate.builder()
                .vehicleId("RTD_LR_A001")
                .routeId("A")
                .tripId("TRIP_A_456")
                .delaySeconds(240) // 4 minutes late
                .scheduleRelationship("SCHEDULED")
                .timestamp_ms(Instant.now().toEpochMilli())
                .build();

            HistoricalVehicleRecord record = new HistoricalVehicleRecord(tripUpdate);

            assertThat(record.getVehicleId()).isEqualTo("RTD_LR_A001");
            assertThat(record.getRouteId()).isEqualTo("A");
            assertThat(record.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.LIGHT_RAIL);
            assertThat(record.getDelaySeconds()).isEqualTo(240);
            assertThat(record.getScheduleRelationship()).isEqualTo("SCHEDULED");
            assertThat(record.getIsMissing()).isFalse();
        }

        @Test
        @DisplayName("Should create missing vehicle record")
        void shouldCreateMissingVehicleRecord() {
            HistoricalVehicleRecord record = HistoricalVehicleRecord
                .createMissingVehicleRecord("RTD_BUS_002", "20", 30, 
                    HistoricalVehicleRecord.DisruptionLevel.MODERATE);

            assertThat(record.getVehicleId()).isEqualTo("RTD_BUS_002");
            assertThat(record.getRouteId()).isEqualTo("20");
            assertThat(record.getIsMissing()).isTrue();
            assertThat(record.getMissingDurationMinutes()).isEqualTo(30);
            assertThat(record.getServiceDisruptionLevel()).isEqualTo(HistoricalVehicleRecord.DisruptionLevel.MODERATE);
            assertThat(record.getRecordId()).isNotNull();
        }

        @Test
        @DisplayName("Should correctly identify vehicle types from route IDs")
        void shouldIdentifyVehicleTypes() {
            // Light rail routes (single letters)
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("A"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.LIGHT_RAIL);
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("C"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.LIGHT_RAIL);

            // BRT routes
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("FF1"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BRT);
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("BRT_WEST"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BRT);

            // Bus routes (numbers)
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("15"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("120X"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);

            // Shuttle routes
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("MALL_SHUTTLE"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.SHUTTLE);
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId("downtown_circulator"))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.SHUTTLE);
        }

        @Test
        @DisplayName("Should build complete record using builder pattern")
        void shouldBuildCompleteRecord() {
            HistoricalVehicleRecord record = HistoricalVehicleRecord.builder()
                .vehicleId("TEST_VEHICLE")
                .routeId("TEST_ROUTE")
                .vehicleType(HistoricalVehicleRecord.VehicleType.BUS)
                .latitude(40.0)
                .longitude(-105.0)
                .speedKmh(35.0f)
                .delaySeconds(-60) // 1 minute early
                .serviceDisruptionLevel(HistoricalVehicleRecord.DisruptionLevel.MINOR)
                .build();

            assertThat(record.getVehicleId()).isEqualTo("TEST_VEHICLE");
            assertThat(record.getRouteId()).isEqualTo("TEST_ROUTE");
            assertThat(record.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
            assertThat(record.getLatitude()).isEqualTo(40.0);
            assertThat(record.getSpeedKmh()).isEqualTo(35.0f);
            assertThat(record.getDelaySeconds()).isEqualTo(-60);
            assertThat(record.getRecordId()).isNotNull();
        }

        @Test
        @DisplayName("Should handle null and empty values gracefully")
        void shouldHandleNullValues() {
            // Test with null vehicle position
            VehiclePosition nullPosition = VehiclePosition.builder().build();
            HistoricalVehicleRecord record = new HistoricalVehicleRecord(nullPosition);

            assertThat(record.getVehicleId()).isNull();
            assertThat(record.getRouteId()).isNull();
            assertThat(record.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.BUS); // Default
            assertThat(record.getIsMissing()).isFalse();
            assertThat(record.getServiceDisruptionLevel()).isEqualTo(HistoricalVehicleRecord.DisruptionLevel.NORMAL);

            // Test with empty route ID
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId(null))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
            assertThat(HistoricalVehicleRecord.VehicleType.fromRouteId(""))
                .isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
        }

        @Test
        @DisplayName("Should set correct timestamps")
        void shouldSetCorrectTimestamps() {
            long testTime = Instant.now().toEpochMilli();
            
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("TIME_TEST")
                .timestamp_ms(testTime)
                .build();

            HistoricalVehicleRecord record = new HistoricalVehicleRecord(position);

            assertThat(record.getOriginalTimestampMs()).isEqualTo(testTime);
            assertThat(record.getIngestionTimestampMs()).isGreaterThanOrEqualTo(testTime);
            assertThat(record.getProcessingTimestampMs()).isGreaterThanOrEqualTo(testTime);
            assertThat(record.getServiceDate()).isNotNull();
            assertThat(record.getServiceHour()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Disruption Level Tests")
    class DisruptionLevelTests {

        @Test
        @DisplayName("Should have correct severity ordering")
        void shouldHaveCorrectSeverityOrdering() {
            assertThat(HistoricalVehicleRecord.DisruptionLevel.NORMAL.getSeverity()).isEqualTo(0);
            assertThat(HistoricalVehicleRecord.DisruptionLevel.MINOR.getSeverity()).isEqualTo(1);
            assertThat(HistoricalVehicleRecord.DisruptionLevel.MODERATE.getSeverity()).isEqualTo(2);
            assertThat(HistoricalVehicleRecord.DisruptionLevel.MAJOR.getSeverity()).isEqualTo(3);
            assertThat(HistoricalVehicleRecord.DisruptionLevel.CRITICAL.getSeverity()).isEqualTo(4);

            // Test severity comparison
            assertThat(HistoricalVehicleRecord.DisruptionLevel.CRITICAL.getSeverity())
                .isGreaterThan(HistoricalVehicleRecord.DisruptionLevel.MAJOR.getSeverity());
            assertThat(HistoricalVehicleRecord.DisruptionLevel.MODERATE.getSeverity())
                .isGreaterThan(HistoricalVehicleRecord.DisruptionLevel.MINOR.getSeverity());
        }

        @Test
        @DisplayName("Should have meaningful descriptions")
        void shouldHaveMeaningfulDescriptions() {
            assertThat(HistoricalVehicleRecord.DisruptionLevel.NORMAL.getDescription())
                .isEqualTo("Normal service");
            assertThat(HistoricalVehicleRecord.DisruptionLevel.CRITICAL.getDescription())
                .isEqualTo("Critical service failure");
            
            // All levels should have non-empty descriptions
            for (HistoricalVehicleRecord.DisruptionLevel level : HistoricalVehicleRecord.DisruptionLevel.values()) {
                assertThat(level.getDescription()).isNotEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Data Quality and Validation Tests")
    class DataQualityTests {

        @Test
        @DisplayName("Should generate unique record IDs")
        void shouldGenerateUniqueRecordIds() {
            Set<String> recordIds = new HashSet<>();
            
            // Create multiple records
            for (int i = 0; i < 100; i++) {
                VehiclePosition position = VehiclePosition.builder()
                    .vehicleId("TEST_" + i)
                    .timestamp_ms(System.currentTimeMillis() + i)
                    .build();
                
                HistoricalVehicleRecord record = new HistoricalVehicleRecord(position);
                recordIds.add(record.getRecordId());
                
                // Small delay to ensure different timestamps
                try { Thread.sleep(1); } catch (InterruptedException e) {}
            }
            
            assertThat(recordIds).hasSize(100); // All unique
        }

        @Test
        @DisplayName("Should handle concurrent record creation")
        void shouldHandleConcurrentRecordCreation() throws InterruptedException {
            List<Thread> threads = new ArrayList<>();
            Set<String> recordIds = Collections.synchronizedSet(new HashSet<>());
            
            // Create multiple threads creating records
            for (int t = 0; t < 5; t++) {
                final int threadId = t;
                Thread thread = new Thread(() -> {
                    for (int i = 0; i < 10; i++) {
                        VehiclePosition position = VehiclePosition.builder()
                            .vehicleId("THREAD_" + threadId + "_VEHICLE_" + i)
                            .timestamp_ms(System.currentTimeMillis())
                            .build();
                        
                        HistoricalVehicleRecord record = new HistoricalVehicleRecord(position);
                        recordIds.add(record.getRecordId());
                    }
                });
                threads.add(thread);
                thread.start();
            }
            
            // Wait for all threads
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Should have 50 unique records (5 threads * 10 records each)
            assertThat(recordIds).hasSize(50);
        }

        @Test
        @DisplayName("Should preserve all original data fields")
        void shouldPreserveOriginalDataFields() {
            VehiclePosition position = VehiclePosition.builder()
                .vehicleId("DATA_PRESERVATION_TEST")
                .routeId("99")
                .tripId("TRIP_99_TEST")
                .latitude(39.123456)
                .longitude(-104.987654)
                .bearing(123.45f)
                .speed(67.89f)
                .timestamp_ms(1234567890123L)
                .currentStatus("STOPPED_AT")
                .occupancyStatus("STANDING_ROOM_ONLY")
                .congestionLevel("SEVERE_CONGESTION")
                .build();

            HistoricalVehicleRecord record = new HistoricalVehicleRecord(position);

            // Verify all fields are preserved
            assertThat(record.getVehicleId()).isEqualTo("DATA_PRESERVATION_TEST");
            assertThat(record.getRouteId()).isEqualTo("99");
            assertThat(record.getTripId()).isEqualTo("TRIP_99_TEST");
            assertThat(record.getLatitude()).isEqualTo(39.123456);
            assertThat(record.getLongitude()).isEqualTo(-104.987654);
            assertThat(record.getBearing()).isEqualTo(123.45f);
            assertThat(record.getSpeedKmh()).isEqualTo(67.89f);
            assertThat(record.getOriginalTimestampMs()).isEqualTo(1234567890123L);
            assertThat(record.getCurrentStatus()).isEqualTo("STOPPED_AT");
            assertThat(record.getOccupancyStatus()).isEqualTo("STANDING_ROOM_ONLY");
            assertThat(record.getCongestionLevel()).isEqualTo("SEVERE_CONGESTION");
        }

        @Test
        @DisplayName("Should convert to string representation")
        void shouldConvertToString() {
            HistoricalVehicleRecord record = HistoricalVehicleRecord.builder()
                .vehicleId("STRING_TEST")
                .routeId("TEST_ROUTE")
                .vehicleType(HistoricalVehicleRecord.VehicleType.LIGHT_RAIL)
                .serviceDisruptionLevel(HistoricalVehicleRecord.DisruptionLevel.MINOR)
                .build();

            String stringRep = record.toString();
            
            assertThat(stringRep).contains("STRING_TEST");
            assertThat(stringRep).contains("TEST_ROUTE");
            assertThat(stringRep).contains("LIGHT_RAIL");
            assertThat(stringRep).contains("MINOR");
        }

        @Test
        @DisplayName("Should implement equals and hashCode correctly")
        void shouldImplementEqualsAndHashCode() {
            HistoricalVehicleRecord record1 = HistoricalVehicleRecord.builder()
                .recordId("TEST_RECORD_123")
                .vehicleId("VEHICLE_1")
                .build();

            HistoricalVehicleRecord record2 = HistoricalVehicleRecord.builder()
                .recordId("TEST_RECORD_123") // Same record ID
                .vehicleId("VEHICLE_2") // Different vehicle ID
                .build();

            HistoricalVehicleRecord record3 = HistoricalVehicleRecord.builder()
                .recordId("TEST_RECORD_456") // Different record ID
                .vehicleId("VEHICLE_1")
                .build();

            // Records with same record ID should be equal
            assertThat(record1).isEqualTo(record2);
            assertThat(record1.hashCode()).isEqualTo(record2.hashCode());

            // Records with different record IDs should not be equal
            assertThat(record1).isNotEqualTo(record3);
            assertThat(record1.hashCode()).isNotEqualTo(record3.hashCode());
        }
    }

    @Nested
    @DisplayName("Integration Scenario Tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("Should handle typical RTD data processing scenario")
        void shouldHandleTypicalRTDScenario() {
            // Simulate processing a batch of real RTD data
            List<HistoricalVehicleRecord> processedRecords = new ArrayList<>();
            
            // Bus vehicle position
            VehiclePosition busPosition = VehiclePosition.builder()
                .vehicleId("RTD_8001")
                .routeId("15")
                .tripId("TRIP_15_MORNING_123")
                .latitude(39.7392)
                .longitude(-104.9903)
                .speed(25.0f)
                .timestamp_ms(System.currentTimeMillis())
                .currentStatus("IN_TRANSIT_TO")
                .occupancyStatus("FEW_SEATS_AVAILABLE")
                .build();
            processedRecords.add(new HistoricalVehicleRecord(busPosition));
            
            // Light rail trip update with delay
            TripUpdate lightRailUpdate = TripUpdate.builder()
                .vehicleId("RTD_LR_C001")
                .routeId("C")
                .tripId("TRIP_C_PEAK_456")
                .delaySeconds(180) // 3 minutes late
                .scheduleRelationship("SCHEDULED")
                .timestamp_ms(System.currentTimeMillis())
                .build();
            processedRecords.add(new HistoricalVehicleRecord(lightRailUpdate));
            
            // Missing vehicle alert
            HistoricalVehicleRecord missingVehicle = HistoricalVehicleRecord
                .createMissingVehicleRecord("RTD_8002", "20", 20, 
                    HistoricalVehicleRecord.DisruptionLevel.MODERATE);
            processedRecords.add(missingVehicle);
            
            // Validate the processed batch
            assertThat(processedRecords).hasSize(3);
            
            // Check bus record
            HistoricalVehicleRecord busRecord = processedRecords.get(0);
            assertThat(busRecord.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.BUS);
            assertThat(busRecord.getRouteId()).isEqualTo("15");
            assertThat(busRecord.getIsMissing()).isFalse();
            
            // Check light rail record  
            HistoricalVehicleRecord lrRecord = processedRecords.get(1);
            assertThat(lrRecord.getVehicleType()).isEqualTo(HistoricalVehicleRecord.VehicleType.LIGHT_RAIL);
            assertThat(lrRecord.getDelaySeconds()).isEqualTo(180);
            
            // Check missing vehicle record
            HistoricalVehicleRecord missingRecord = processedRecords.get(2);
            assertThat(missingRecord.getIsMissing()).isTrue();
            assertThat(missingRecord.getMissingDurationMinutes()).isEqualTo(20);
            assertThat(missingRecord.getServiceDisruptionLevel()).isEqualTo(HistoricalVehicleRecord.DisruptionLevel.MODERATE);
        }

        @Test
        @DisplayName("Should support service disruption escalation scenario")
        void shouldSupportServiceDisruptionEscalation() {
            String routeId = "DISRUPTION_TEST_ROUTE";
            String vehicleId = "DISRUPTION_TEST_VEHICLE";
            
            // Start with normal service
            HistoricalVehicleRecord normalRecord = HistoricalVehicleRecord.builder()
                .vehicleId(vehicleId)
                .routeId(routeId)
                .serviceDisruptionLevel(HistoricalVehicleRecord.DisruptionLevel.NORMAL)
                .build();
            
            // Escalate to minor disruption (slight delay)
            HistoricalVehicleRecord minorRecord = HistoricalVehicleRecord.builder()
                .vehicleId(vehicleId)
                .routeId(routeId)
                .delaySeconds(300) // 5 minutes late
                .serviceDisruptionLevel(HistoricalVehicleRecord.DisruptionLevel.MINOR)
                .build();
            
            // Escalate to moderate disruption (vehicle becomes missing)
            HistoricalVehicleRecord moderateRecord = HistoricalVehicleRecord
                .createMissingVehicleRecord(vehicleId, routeId, 20, 
                    HistoricalVehicleRecord.DisruptionLevel.MODERATE);
            
            // Escalate to major disruption (long missing)
            HistoricalVehicleRecord majorRecord = HistoricalVehicleRecord
                .createMissingVehicleRecord(vehicleId, routeId, 75, 
                    HistoricalVehicleRecord.DisruptionLevel.MAJOR);
            
            // Validate escalation path
            List<HistoricalVehicleRecord> escalationSequence = Arrays.asList(
                normalRecord, minorRecord, moderateRecord, majorRecord
            );
            
            for (int i = 1; i < escalationSequence.size(); i++) {
                int prevSeverity = escalationSequence.get(i-1).getServiceDisruptionLevel().getSeverity();
                int currentSeverity = escalationSequence.get(i).getServiceDisruptionLevel().getSeverity();
                assertThat(currentSeverity).isGreaterThan(prevSeverity);
            }
        }
    }
}