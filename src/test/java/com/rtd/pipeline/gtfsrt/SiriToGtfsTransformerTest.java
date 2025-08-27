package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SIRI to GTFS-RT Transformer Tests")
class SiriToGtfsTransformerTest {

    private SiriToGtfsTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SiriToGtfsTransformer();
    }

    @Test
    @DisplayName("Should handle valid SIRI XML payload")
    void shouldHandleValidSiriXmlPayload() throws Exception {
        String siriXml = """
            <?xml version="1.0"?>
            <Siri version="2.0">
              <ServiceDelivery>
                <VehicleMonitoringDelivery>
                  <VehicleActivity>
                    <RecordedAtTime>2025-01-01T10:00:00Z</RecordedAtTime>
                    <ValidUntilTime>2025-01-01T10:05:00Z</ValidUntilTime>
                    <MonitoredVehicleJourney>
                      <LineRef>15</LineRef>
                      <VehicleRef>1234</VehicleRef>
                      <VehicleLocation>
                        <Longitude>-105.0</Longitude>
                        <Latitude>39.7</Latitude>
                      </VehicleLocation>
                      <Bearing>45.0</Bearing>
                      <Speed>15.5</Speed>
                      <InPanic>false</InPanic>
                      <MonitoredCall>
                        <StopPointRef>stop123</StopPointRef>
                        <ExpectedArrivalTime>2025-01-01T10:15:00Z</ExpectedArrivalTime>
                        <ExpectedDepartureTime>2025-01-01T10:16:00Z</ExpectedDepartureTime>
                      </MonitoredCall>
                    </MonitoredVehicleJourney>
                  </VehicleActivity>
                </VehicleMonitoringDelivery>
              </ServiceDelivery>
            </Siri>
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriXml);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(siriXml);

        // Verify vehicle positions
        assertFalse(vehiclePositions.isEmpty(), "Should create at least one vehicle position");
        GtfsRealtime.VehiclePosition vehiclePosition = vehiclePositions.get(0);
        
        assertTrue(vehiclePosition.hasVehicle(), "Should have vehicle information");
        assertEquals("1234", vehiclePosition.getVehicle().getId(), "Should have correct vehicle ID");
        
        assertTrue(vehiclePosition.hasPosition(), "Should have position information");
        assertEquals(-105.0f, vehiclePosition.getPosition().getLongitude(), 0.01f, "Should have correct longitude");
        assertEquals(39.7f, vehiclePosition.getPosition().getLatitude(), 0.01f, "Should have correct latitude");
        assertEquals(45.0f, vehiclePosition.getPosition().getBearing(), 0.01f, "Should have correct bearing");
        assertEquals(15.5f, vehiclePosition.getPosition().getSpeed(), 0.01f, "Should have correct speed");

        // Verify trip updates
        assertFalse(tripUpdates.isEmpty(), "Should create at least one trip update");
        GtfsRealtime.TripUpdate tripUpdate = tripUpdates.get(0);
        
        assertTrue(tripUpdate.hasVehicle(), "Should have vehicle information");
        assertEquals("1234", tripUpdate.getVehicle().getId(), "Should have correct vehicle ID");
        
        assertFalse(tripUpdate.getStopTimeUpdateList().isEmpty(), "Should have stop time updates");
        GtfsRealtime.TripUpdate.StopTimeUpdate stopTimeUpdate = tripUpdate.getStopTimeUpdate(0);
        assertEquals("stop123", stopTimeUpdate.getStopId(), "Should have correct stop ID");
    }

    @Test
    @DisplayName("Should handle valid SIRI JSON payload")
    void shouldHandleValidSiriJsonPayload() throws Exception {
        String siriJson = """
            {
              "Siri": {
                "ServiceDelivery": {
                  "VehicleMonitoringDelivery": {
                    "VehicleActivity": [
                      {
                        "RecordedAtTime": "2025-01-01T10:00:00Z",
                        "ValidUntilTime": "2025-01-01T10:05:00Z",
                        "MonitoredVehicleJourney": {
                          "LineRef": "15",
                          "VehicleRef": "5678",
                          "VehicleLocation": {
                            "Longitude": -104.5,
                            "Latitude": 39.5
                          },
                          "Bearing": 90.0,
                          "Speed": 20.0,
                          "InPanic": false,
                          "MonitoredCall": {
                            "StopPointRef": "stop456",
                            "ExpectedArrivalTime": "2025-01-01T10:20:00Z",
                            "ExpectedDepartureTime": "2025-01-01T10:21:00Z"
                          }
                        }
                      }
                    ]
                  }
                }
              }
            }
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriJson);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(siriJson);

        // Verify vehicle positions
        assertFalse(vehiclePositions.isEmpty(), "Should create at least one vehicle position");
        GtfsRealtime.VehiclePosition vehiclePosition = vehiclePositions.get(0);
        
        assertEquals("5678", vehiclePosition.getVehicle().getId(), "Should have correct vehicle ID");
        assertEquals(-104.5f, vehiclePosition.getPosition().getLongitude(), 0.01f, "Should have correct longitude");
        assertEquals(39.5f, vehiclePosition.getPosition().getLatitude(), 0.01f, "Should have correct latitude");

        // Verify trip updates
        assertFalse(tripUpdates.isEmpty(), "Should create at least one trip update");
        GtfsRealtime.TripUpdate tripUpdate = tripUpdates.get(0);
        
        assertEquals("5678", tripUpdate.getVehicle().getId(), "Should have correct vehicle ID");
        assertFalse(tripUpdate.getStopTimeUpdateList().isEmpty(), "Should have stop time updates");
    }

    @Test
    @DisplayName("Should handle invalid coordinates gracefully")
    void shouldHandleInvalidCoordinatesGracefully() throws Exception {
        String siriXml = """
            <?xml version="1.0"?>
            <Siri version="2.0">
              <ServiceDelivery>
                <VehicleMonitoringDelivery>
                  <VehicleActivity>
                    <RecordedAtTime>2025-01-01T10:00:00Z</RecordedAtTime>
                    <MonitoredVehicleJourney>
                      <LineRef>15</LineRef>
                      <VehicleRef>1234</VehicleRef>
                      <VehicleLocation>
                        <Longitude>-200.0</Longitude>
                        <Latitude>100.0</Latitude>
                      </VehicleLocation>
                    </MonitoredVehicleJourney>
                  </VehicleActivity>
                </VehicleMonitoringDelivery>
              </ServiceDelivery>
            </Siri>
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriXml);
        
        // Should either skip invalid coordinates or return empty list
        assertTrue(vehiclePositions.isEmpty() || 
                  !vehiclePositions.get(0).hasPosition(), 
                  "Should handle invalid coordinates gracefully");
    }

    @Test
    @DisplayName("Should handle empty SIRI payload")
    void shouldHandleEmptySiriPayload() throws Exception {
        String emptySiri = """
            <?xml version="1.0"?>
            <Siri version="2.0">
              <ServiceDelivery>
                <VehicleMonitoringDelivery>
                </VehicleMonitoringDelivery>
              </ServiceDelivery>
            </Siri>
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(emptySiri);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(emptySiri);

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for empty payload");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for empty payload");
    }

    @Test
    @DisplayName("Should handle malformed XML gracefully")
    void shouldHandleMalformedXmlGracefully() throws Exception {
        String malformedXml = "<invalid>xml<content>";

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(malformedXml);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(malformedXml);

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for malformed XML");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for malformed XML");
    }

    @Test
    @DisplayName("Should handle null input gracefully")
    void shouldHandleNullInputGracefully() throws Exception {
        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(null);
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(null);

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for null input");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for null input");
    }

    @Test
    @DisplayName("Should handle empty string input gracefully")
    void shouldHandleEmptyStringInputGracefully() throws Exception {
        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions("");
        List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates("");

        assertTrue(vehiclePositions.isEmpty(), "Should return empty list for empty string");
        assertTrue(tripUpdates.isEmpty(), "Should return empty list for empty string");
    }

    @Test
    @DisplayName("Should apply Colorado coordinate validation")
    void shouldApplyColoradoCoordinateValidation() throws Exception {
        String siriOutsideColorado = """
            <?xml version="1.0"?>
            <Siri version="2.0">
              <ServiceDelivery>
                <VehicleMonitoringDelivery>
                  <VehicleActivity>
                    <RecordedAtTime>2025-01-01T10:00:00Z</RecordedAtTime>
                    <MonitoredVehicleJourney>
                      <LineRef>15</LineRef>
                      <VehicleRef>1234</VehicleRef>
                      <VehicleLocation>
                        <Longitude>-74.0</Longitude>
                        <Latitude>40.7</Latitude>
                      </VehicleLocation>
                    </MonitoredVehicleJourney>
                  </VehicleActivity>
                </VehicleMonitoringDelivery>
              </ServiceDelivery>
            </Siri>
            """;

        List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriOutsideColorado);
        
        // Should filter out coordinates outside Colorado
        assertTrue(vehiclePositions.isEmpty() || 
                  !vehiclePositions.get(0).hasPosition(), 
                  "Should filter out coordinates outside Colorado bounds");
    }
}