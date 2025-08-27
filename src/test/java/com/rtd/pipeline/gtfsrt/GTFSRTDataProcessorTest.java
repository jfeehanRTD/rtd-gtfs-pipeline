package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GTFS-RT Data Processor Tests")
class GTFSRTDataProcessorTest {

    private GTFSRTDataProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GTFSRTDataProcessor();
    }

    @Test
    @DisplayName("Should process valid vehicle positions")
    void shouldProcessValidVehiclePositions() {
        List<GtfsRealtime.VehiclePosition> positions = createValidVehiclePositions();

        List<GtfsRealtime.VehiclePosition> processed = processor.processVehiclePositions(positions);

        assertFalse(processed.isEmpty(), "Should return processed vehicle positions");
        assertEquals(2, processed.size(), "Should process all valid vehicle positions");
        
        for (GtfsRealtime.VehiclePosition position : processed) {
            assertTrue(position.hasVehicle(), "Processed position should have vehicle info");
            assertTrue(position.hasPosition(), "Processed position should have location");
            assertTrue(position.hasTimestamp(), "Processed position should have timestamp");
        }
    }

    @Test
    @DisplayName("Should filter out invalid coordinates")
    void shouldFilterOutInvalidCoordinates() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // Add vehicle with coordinates outside Colorado
        GtfsRealtime.VehiclePosition.Builder invalidPos = GtfsRealtime.VehiclePosition.newBuilder();
        invalidPos.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("invalid-vehicle")
            .build());
        invalidPos.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(40.7128f)  // NYC latitude
            .setLongitude(-74.0060f) // NYC longitude
            .build());
        invalidPos.setTimestamp(Instant.now().getEpochSecond());
        positions.add(invalidPos.build());

        // Add vehicle with valid Colorado coordinates
        positions.addAll(createValidVehiclePositions());

        List<GtfsRealtime.VehiclePosition> processed = processor.processVehiclePositions(positions);

        assertEquals(2, processed.size(), "Should filter out vehicles outside Colorado");
        
        for (GtfsRealtime.VehiclePosition position : processed) {
            float lat = position.getPosition().getLatitude();
            float lon = position.getPosition().getLongitude();
            
            assertTrue(lat >= 39.0f && lat <= 41.0f, "Latitude should be within Colorado bounds");
            assertTrue(lon >= -106.0f && lon <= -104.0f, "Longitude should be within Colorado bounds");
        }
    }

    @Test
    @DisplayName("Should validate and normalize speed values")
    void shouldValidateAndNormalizeSpeedValues() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // Add vehicle with excessive speed
        GtfsRealtime.VehiclePosition.Builder highSpeedPos = GtfsRealtime.VehiclePosition.newBuilder();
        highSpeedPos.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("high-speed-vehicle")
            .build());
        highSpeedPos.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.7392f)
            .setLongitude(-104.9903f)
            .setSpeed(200.0f) // Excessive speed
            .build());
        highSpeedPos.setTimestamp(Instant.now().getEpochSecond());
        positions.add(highSpeedPos.build());

        // Add vehicle with negative speed
        GtfsRealtime.VehiclePosition.Builder negativeSpeedPos = GtfsRealtime.VehiclePosition.newBuilder();
        negativeSpeedPos.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("negative-speed-vehicle")
            .build());
        negativeSpeedPos.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.6500f)
            .setLongitude(-105.1000f)
            .setSpeed(-10.0f) // Negative speed
            .build());
        negativeSpeedPos.setTimestamp(Instant.now().getEpochSecond());
        positions.add(negativeSpeedPos.build());

        List<GtfsRealtime.VehiclePosition> processed = processor.processVehiclePositions(positions);

        for (GtfsRealtime.VehiclePosition position : processed) {
            if (position.getPosition().hasSpeed()) {
                float speed = position.getPosition().getSpeed();
                assertTrue(speed >= 0.0f, "Speed should not be negative");
                assertTrue(speed <= 80.0f, "Speed should be within reasonable bounds");
            }
        }
    }

    @Test
    @DisplayName("Should deduplicate vehicles by ID")
    void shouldDeduplicateVehiclesByID() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // Add duplicate vehicles with same ID but different timestamps
        for (int i = 0; i < 3; i++) {
            GtfsRealtime.VehiclePosition.Builder pos = GtfsRealtime.VehiclePosition.newBuilder();
            pos.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
                .setId("duplicate-vehicle")
                .build());
            pos.setPosition(GtfsRealtime.Position.newBuilder()
                .setLatitude(39.7392f)
                .setLongitude(-104.9903f)
                .build());
            pos.setTimestamp(Instant.now().minusSeconds(i * 10).getEpochSecond());
            positions.add(pos.build());
        }

        List<GtfsRealtime.VehiclePosition> processed = processor.processVehiclePositions(positions);

        assertEquals(1, processed.size(), "Should deduplicate vehicles with same ID");
        
        // Should keep the most recent one (highest timestamp)
        GtfsRealtime.VehiclePosition kept = processed.get(0);
        assertEquals("duplicate-vehicle", kept.getVehicle().getId(), "Should keep correct vehicle ID");
    }

    @Test
    @DisplayName("Should process valid trip updates")
    void shouldProcessValidTripUpdates() {
        List<GtfsRealtime.TripUpdate> tripUpdates = createValidTripUpdates();

        List<GtfsRealtime.TripUpdate> processed = processor.processTripUpdates(tripUpdates);

        assertFalse(processed.isEmpty(), "Should return processed trip updates");
        assertEquals(2, processed.size(), "Should process all valid trip updates");
        
        for (GtfsRealtime.TripUpdate tripUpdate : processed) {
            assertTrue(tripUpdate.hasVehicle() || tripUpdate.hasTrip(), "Should have vehicle or trip info");
            assertFalse(tripUpdate.getStopTimeUpdateList().isEmpty(), "Should have stop time updates");
        }
    }

    @Test
    @DisplayName("Should validate delay values in trip updates")
    void shouldValidateDelayValuesInTripUpdates() {
        List<GtfsRealtime.TripUpdate> tripUpdates = new ArrayList<>();

        // Create trip update with excessive delay
        GtfsRealtime.TripUpdate.Builder update = GtfsRealtime.TripUpdate.newBuilder();
        update.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("delayed-vehicle")
            .build());
        update.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("delayed-trip")
            .setRouteId("15")
            .build());
        update.addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopId("stop-001")
            .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                .setDelay(7200) // 2 hour delay - excessive
                .build())
            .build());
        tripUpdates.add(update.build());

        List<GtfsRealtime.TripUpdate> processed = processor.processTripUpdates(tripUpdates);

        for (GtfsRealtime.TripUpdate tripUpdate : processed) {
            for (GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate : tripUpdate.getStopTimeUpdateList()) {
                if (stopTimeUpdate.hasArrival() && stopTimeUpdate.getArrival().hasDelay()) {
                    int delay = stopTimeUpdate.getArrival().getDelay();
                    assertTrue(Math.abs(delay) <= 3600, "Delay should be within reasonable bounds (1 hour)");
                }
                if (stopTimeUpdate.hasDeparture() && stopTimeUpdate.getDeparture().hasDelay()) {
                    int delay = stopTimeUpdate.getDeparture().getDelay();
                    assertTrue(Math.abs(delay) <= 3600, "Delay should be within reasonable bounds (1 hour)");
                }
            }
        }
    }

    @Test
    @DisplayName("Should process alerts without modification")
    void shouldProcessAlertsWithoutModification() {
        List<GtfsRealtime.Alert> alerts = createValidAlerts();

        List<GtfsRealtime.Alert> processed = processor.processAlerts(alerts);

        assertEquals(alerts.size(), processed.size(), "Should return all alerts");
        
        for (int i = 0; i < alerts.size(); i++) {
            assertEquals(alerts.get(i), processed.get(i), "Alerts should be unchanged");
        }
    }

    @Test
    @DisplayName("Should handle empty input lists")
    void shouldHandleEmptyInputLists() {
        List<GtfsRealtime.VehiclePosition> emptyVehiclePositions = new ArrayList<>();
        List<GtfsRealtime.TripUpdate> emptyTripUpdates = new ArrayList<>();
        List<GtfsRealtime.Alert> emptyAlerts = new ArrayList<>();

        List<GtfsRealtime.VehiclePosition> processedPositions = processor.processVehiclePositions(emptyVehiclePositions);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = processor.processTripUpdates(emptyTripUpdates);
        List<GtfsRealtime.Alert> processedAlerts = processor.processAlerts(emptyAlerts);

        assertTrue(processedPositions.isEmpty(), "Should return empty list for empty vehicle positions");
        assertTrue(processedTripUpdates.isEmpty(), "Should return empty list for empty trip updates");
        assertTrue(processedAlerts.isEmpty(), "Should return empty list for empty alerts");
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputGracefully() {
        List<GtfsRealtime.VehiclePosition> processedPositions = processor.processVehiclePositions(null);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = processor.processTripUpdates(null);
        List<GtfsRealtime.Alert> processedAlerts = processor.processAlerts(null);

        assertTrue(processedPositions.isEmpty(), "Should return empty list for null vehicle positions");
        assertTrue(processedTripUpdates.isEmpty(), "Should return empty list for null trip updates");
        assertTrue(processedAlerts.isEmpty(), "Should return empty list for null alerts");
    }

    @Test
    @DisplayName("Should normalize vehicle IDs")
    void shouldNormalizeVehicleIDs() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // Add vehicle with whitespace in ID
        GtfsRealtime.VehiclePosition.Builder pos = GtfsRealtime.VehiclePosition.newBuilder();
        pos.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("  vehicle-001  ")  // ID with leading/trailing whitespace
            .build());
        pos.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.7392f)
            .setLongitude(-104.9903f)
            .build());
        pos.setTimestamp(Instant.now().getEpochSecond());
        positions.add(pos.build());

        List<GtfsRealtime.VehiclePosition> processed = processor.processVehiclePositions(positions);

        assertFalse(processed.isEmpty(), "Should process vehicle with whitespace in ID");
        assertEquals("vehicle-001", processed.get(0).getVehicle().getId(), "Should trim whitespace from vehicle ID");
    }

    private List<GtfsRealtime.VehiclePosition> createValidVehiclePositions() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // First vehicle
        GtfsRealtime.VehiclePosition.Builder pos1 = GtfsRealtime.VehiclePosition.newBuilder();
        pos1.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-001")
            .build());
        pos1.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.7392f)  // Denver coordinates
            .setLongitude(-104.9903f)
            .setSpeed(15.5f)
            .setBearing(45.0f)
            .build());
        pos1.setTimestamp(Instant.now().getEpochSecond());
        positions.add(pos1.build());

        // Second vehicle
        GtfsRealtime.VehiclePosition.Builder pos2 = GtfsRealtime.VehiclePosition.newBuilder();
        pos2.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-002")
            .build());
        pos2.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.6500f)  // Colorado coordinates
            .setLongitude(-105.1000f)
            .setSpeed(20.0f)
            .setBearing(90.0f)
            .build());
        pos2.setTimestamp(Instant.now().getEpochSecond());
        positions.add(pos2.build());

        return positions;
    }

    private List<GtfsRealtime.TripUpdate> createValidTripUpdates() {
        List<GtfsRealtime.TripUpdate> updates = new ArrayList<>();

        // First trip update
        GtfsRealtime.TripUpdate.Builder update1 = GtfsRealtime.TripUpdate.newBuilder();
        update1.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-001")
            .build());
        update1.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-001")
            .setRouteId("15")
            .build());
        update1.addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopId("stop-001")
            .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                .setTime(Instant.now().plusSeconds(600).getEpochSecond())
                .setDelay(60)
                .build())
            .build());
        updates.add(update1.build());

        // Second trip update
        GtfsRealtime.TripUpdate.Builder update2 = GtfsRealtime.TripUpdate.newBuilder();
        update2.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-002")
            .build());
        update2.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-002")
            .setRouteId("16")
            .build());
        update2.addStopTimeUpdate(GtfsRealtime.TripUpdate.StopTimeUpdate.newBuilder()
            .setStopId("stop-002")
            .setArrival(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                .setTime(Instant.now().plusSeconds(900).getEpochSecond())
                .setDelay(-30)
                .build())
            .build());
        updates.add(update2.build());

        return updates;
    }

    private List<GtfsRealtime.Alert> createValidAlerts() {
        List<GtfsRealtime.Alert> alerts = new ArrayList<>();

        GtfsRealtime.Alert.Builder alert = GtfsRealtime.Alert.newBuilder();
        alert.addActivePeriod(GtfsRealtime.TimeRange.newBuilder()
            .setStart(Instant.now().getEpochSecond())
            .setEnd(Instant.now().plusSeconds(3600).getEpochSecond())
            .build());
        alert.addInformedEntity(GtfsRealtime.EntitySelector.newBuilder()
            .setRouteId("15")
            .build());
        alert.setCause(GtfsRealtime.Alert.Cause.CONSTRUCTION);
        alert.setEffect(GtfsRealtime.Alert.Effect.DETOUR);
        alert.setHeaderText(GtfsRealtime.TranslatedString.newBuilder()
            .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder()
                .setText("Route 15 Detour")
                .setLanguage("en")
                .build())
            .build());

        alerts.add(alert.build());
        return alerts;
    }
}