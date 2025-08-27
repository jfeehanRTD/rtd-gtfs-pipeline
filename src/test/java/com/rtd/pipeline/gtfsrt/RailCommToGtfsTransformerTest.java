package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RailComm to GTFS-RT Transformer Tests")
class RailCommToGtfsTransformerTest {

    private RailCommToGtfsTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new RailCommToGtfsTransformer();
    }

    @Test
    @DisplayName("Should handle valid RailComm JSON payload - trains array format")
    void shouldHandleValidRailCommJsonTrainsArray() throws Exception {
        String railCommJson = """
            {
              "trains": [
                {
                  "trainId": "A-123",
                  "routeId": "A",
                  "latitude": 39.7392,
                  "longitude": -104.9903,
                  "speed": 25.0,
                  "heading": 180.0,
                  "timestamp": "2025-01-01T10:00:00Z",
                  "nextStopId": "A01",
                  "arrivalTime": "2025-01-01T10:15:00Z",
                  "departureTime": "2025-01-01T10:16:00Z",
                  "delay": 120
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommJson);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(railCommJson);

        // Verify vehicle positions
        assertFalse(vehiclePositions.isEmpty(), "Should create at least one vehicle position");
        GtfsRealtime.VehiclePosition vehiclePosition = vehiclePositions.get(0);
        
        assertTrue(vehiclePosition.hasVehicle(), "Should have vehicle information");
        assertEquals("A-123", vehiclePosition.getVehicle().getId(), "Should have correct vehicle ID");
        
        assertTrue(vehiclePosition.hasPosition(), "Should have position information");
        assertEquals(-104.9903f, vehiclePosition.getPosition().getLongitude(), 0.01f, "Should have correct longitude");
        assertEquals(39.7392f, vehiclePosition.getPosition().getLatitude(), 0.01f, "Should have correct latitude");
        assertEquals(180.0f, vehiclePosition.getPosition().getBearing(), 0.01f, "Should have correct heading");
        assertEquals(25.0f, vehiclePosition.getPosition().getSpeed(), 0.01f, "Should have correct speed");

        assertTrue(vehiclePosition.hasTrip(), "Should have trip information");
        assertEquals("A", vehiclePosition.getTrip().getRouteId(), "Should have correct route ID");

        // Verify trip updates
        assertFalse(tripUpdates.isEmpty(), "Should create at least one trip update");
        GtfsRealtime.TripUpdate tripUpdate = tripUpdates.get(0);
        
        assertTrue(tripUpdate.hasVehicle(), "Should have vehicle information");
        assertEquals("A-123", tripUpdate.getVehicle().getId(), "Should have correct vehicle ID");
        
        assertTrue(tripUpdate.hasTrip(), "Should have trip information");
        assertEquals("A", tripUpdate.getTrip().getRouteId(), "Should have correct route ID");
        
        assertFalse(tripUpdate.getStopTimeUpdateList().isEmpty(), "Should have stop time updates");
        GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate = tripUpdate.getStopTimeUpdate(0);
        assertEquals("A01", stopTimeUpdate.getStopId(), "Should have correct stop ID");
        assertEquals(120, stopTimeUpdate.getArrival().getDelay(), "Should have correct delay");
    }

    @Test
    @DisplayName("Should handle valid RailComm JSON payload - vehicles array format")
    void shouldHandleValidRailCommJsonVehiclesArray() throws Exception {
        String railCommJson = """
            {
              "vehicles": [
                {
                  "id": "B-456",
                  "route": "B",
                  "lat": 39.6500,
                  "lon": -105.1000,
                  "speed": 30.0,
                  "bearing": 90.0,
                  "timestamp": "2025-01-01T10:30:00Z",
                  "stopId": "B02",
                  "arrivalTime": "2025-01-01T10:45:00Z",
                  "delay": -60
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommJson);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(railCommJson);

        // Verify vehicle positions
        assertFalse(vehiclePositions.isEmpty(), "Should create at least one vehicle position");
        GtfsRealtime.VehiclePosition vehiclePosition = vehiclePositions.get(0);
        
        assertEquals("B-456", vehiclePosition.getVehicle().getId(), "Should have correct vehicle ID");
        assertEquals(-105.1000f, vehiclePosition.getPosition().getLongitude(), 0.01f, "Should have correct longitude");
        assertEquals(39.6500f, vehiclePosition.getPosition().getLatitude(), 0.01f, "Should have correct latitude");
        assertEquals("B", vehiclePosition.getTrip().getRouteId(), "Should have correct route ID");

        // Verify trip updates
        assertFalse(tripUpdates.isEmpty(), "Should create at least one trip update");
        GtfsRealtime.TripUpdate tripUpdate = tripUpdates.get(0);
        
        assertEquals("B-456", tripUpdate.getVehicle().getId(), "Should have correct vehicle ID");
        assertEquals("B", tripUpdate.getTrip().getRouteId(), "Should have correct route ID");
        
        assertFalse(tripUpdate.getStopTimeUpdateList().isEmpty(), "Should have stop time updates");
        GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate = tripUpdate.getStopTimeUpdate(0);
        assertEquals("B02", stopTimeUpdate.getStopId(), "Should have correct stop ID");
        assertEquals(-60, stopTimeUpdate.getArrival().getDelay(), "Should handle negative delay (early arrival)");
    }

    @Test
    @DisplayName("Should handle single vehicle object format")
    void shouldHandleSingleVehicleObjectFormat() throws Exception {
        String railCommJson = """
            {
              "trainId": "W-789",
              "routeId": "W",
              "latitude": 39.8000,
              "longitude": -105.0500,
              "speed": 35.0,
              "heading": 270.0,
              "timestamp": "2025-01-01T11:00:00Z"
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommJson);

        assertFalse(vehiclePositions.isEmpty(), "Should create vehicle position for single object");
        GtfsRealtime.VehiclePosition vehiclePosition = vehiclePositions.get(0);
        
        assertEquals("W-789", vehiclePosition.getVehicle().getId(), "Should have correct vehicle ID");
        assertEquals("W", vehiclePosition.getTrip().getRouteId(), "Should have correct route ID");
        assertEquals(-105.0500f, vehiclePosition.getPosition().getLongitude(), 0.01f, "Should have correct longitude");
        assertEquals(39.8000f, vehiclePosition.getPosition().getLatitude(), 0.01f, "Should have correct latitude");
    }

    @Test
    @DisplayName("Should normalize light rail route IDs")
    void shouldNormalizeLightRailRouteIds() throws Exception {
        String railCommJson = """
            {
              "trains": [
                {
                  "trainId": "LR-A-001",
                  "routeId": "Light Rail A Line",
                  "latitude": 39.7500,
                  "longitude": -105.0000,
                  "timestamp": "2025-01-01T12:00:00Z"
                },
                {
                  "trainId": "LR-B-002", 
                  "routeId": "B-Line",
                  "latitude": 39.7600,
                  "longitude": -104.9900,
                  "timestamp": "2025-01-01T12:05:00Z"
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommJson);

        assertEquals(2, vehiclePositions.size(), "Should create positions for all trains");
        
        // Check first vehicle - should normalize to "A"
        GtfsRealtime.VehiclePosition firstVehicle = vehiclePositions.get(0);
        assertEquals("A", firstVehicle.getTrip().getRouteId(), "Should normalize 'Light Rail A Line' to 'A'");
        
        // Check second vehicle - should normalize to "B"
        GtfsRealtime.VehiclePosition secondVehicle = vehiclePositions.get(1);
        assertEquals("B", secondVehicle.getTrip().getRouteId(), "Should normalize 'B-Line' to 'B'");
    }

    @Test
    @DisplayName("Should handle coordinates outside Colorado bounds")
    void shouldHandleCoordinatesOutsideColoradoBounds() throws Exception {
        String railCommJson = """
            {
              "trains": [
                {
                  "trainId": "NYC-123",
                  "routeId": "A",
                  "latitude": 40.7128,
                  "longitude": -74.0060,
                  "timestamp": "2025-01-01T12:00:00Z"
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommJson);
        
        // Should filter out coordinates outside Colorado bounds
        assertTrue(vehiclePositions.isEmpty() || 
                  !vehiclePositions.get(0).hasPosition(), 
                  "Should filter out coordinates outside Colorado bounds");
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully")
    void shouldHandleInvalidJsonGracefully() throws Exception {
        String invalidJson = "{ invalid json }";

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(invalidJson);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(invalidJson);

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for invalid JSON");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for invalid JSON");
    }

    @Test
    @DisplayName("Should handle empty JSON object")
    void shouldHandleEmptyJsonObject() throws Exception {
        String emptyJson = "{}";

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(emptyJson);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(emptyJson);

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for empty JSON object");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for empty JSON object");
    }

    @Test
    @DisplayName("Should handle null and empty string inputs")
    void shouldHandleNullAndEmptyInputs() throws Exception {
        // Test null input
        List<GtfsRealtime.VehiclePosition> nullVehiclePositions = transformer.transformToVehiclePositions(null);
        List<GtfsRealtime.TripUpdate> nullTripUpdates = transformer.transformToTripUpdates(null);

        assertTrue(nullVehiclePositions.isEmpty(), "Should return empty list for null input");
        assertTrue(nullTripUpdates.isEmpty(), "Should return empty list for null input");

        // Test empty string input
        List<GtfsRealtime.VehiclePosition> emptyVehiclePositions = transformer.transformToVehiclePositions("");
        List<GtfsRealtime.TripUpdate> emptyTripUpdates = transformer.transformToTripUpdates("");

        assertTrue(emptyVehiclePositions.isEmpty(), "Should return empty list for empty string input");
        assertTrue(emptyTripUpdates.isEmpty(), "Should return empty list for empty string input");
    }

    @Test
    @DisplayName("Should handle missing required fields gracefully")
    void shouldHandleMissingRequiredFieldsGracefully() throws Exception {
        String jsonWithMissingFields = """
            {
              "trains": [
                {
                  "trainId": "PARTIAL-001"
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(jsonWithMissingFields);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(jsonWithMissingFields);

        // Should handle missing fields gracefully - might create partial records or skip
        assertNotNull(vehiclePositions, "Should return non-null list");
        assertNotNull(tripUpdates, "Should return non-null list");
    }

    @Test
    @DisplayName("Should validate speed and delay values")
    void shouldValidateSpeedAndDelayValues() throws Exception {
        String jsonWithExtremeValues = """
            {
              "trains": [
                {
                  "trainId": "EXTREME-001",
                  "routeId": "A",
                  "latitude": 39.7500,
                  "longitude": -105.0000,
                  "speed": 200.0,
                  "delay": 7200,
                  "timestamp": "2025-01-01T12:00:00Z"
                }
              ]
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(jsonWithExtremeValues);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(jsonWithExtremeValues);

        // Should handle extreme values according to validation rules
        assertNotNull(vehiclePositions, "Should return non-null list");
        assertNotNull(tripUpdates, "Should return non-null list");
        
        if (!vehiclePositions.isEmpty()) {
            GtfsRealtime.VehiclePosition position = vehiclePositions.get(0);
            if (position.hasPosition() && position.getPosition().hasSpeed()) {
                float speed = position.getPosition().getSpeed();
                assertTrue(speed >= 0 && speed <= 100, "Speed should be within reasonable bounds");
            }
        }
    }
}