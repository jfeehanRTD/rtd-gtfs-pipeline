package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GTFS-RT Pipeline Integration Tests")
class GTFSRTPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    private SiriToGtfsTransformer siriTransformer;
    private RailCommToGtfsTransformer railCommTransformer;
    private GTFSRTDataProcessor dataProcessor;
    private GTFSRTFeedGenerator feedGenerator;

    @BeforeEach
    void setUp() {
        siriTransformer = new SiriToGtfsTransformer();
        railCommTransformer = new RailCommToGtfsTransformer();
        dataProcessor = new GTFSRTDataProcessor();
        feedGenerator = new GTFSRTFeedGenerator(tempDir.toString(), "RTD Test", "https://test.rtd-denver.com");
    }

    @Test
    @DisplayName("Should process complete SIRI to GTFS-RT pipeline")
    void shouldProcessCompleteSiriToGtfsRtPipeline() throws Exception {
        // Step 1: Transform SIRI data to GTFS-RT
        String siriPayload = createValidSiriPayload();
        List<GtfsRealtime.VehiclePosition> rawVehiclePositions = siriTransformer.transformToVehiclePositions(siriPayload);
        List<GtfsRealtime.TripUpdate> rawTripUpdates = siriTransformer.transformToTripUpdates(siriPayload);

        assertFalse(rawVehiclePositions.isEmpty(), "Should transform SIRI to vehicle positions");
        assertFalse(rawTripUpdates.isEmpty(), "Should transform SIRI to trip updates");

        // Step 2: Process and validate data
        List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(rawVehiclePositions);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(rawTripUpdates);
        List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());


        assertFalse(processedVehiclePositions.isEmpty(), "Should process vehicle positions");
        assertFalse(processedTripUpdates.isEmpty(), "Should process trip updates");

        // Step 3: Generate protobuf feeds
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
            processedVehiclePositions, processedTripUpdates, processedAlerts);

        assertTrue(result.isVehiclePositionsSuccess(), "Should generate vehicle positions feed");
        assertTrue(result.isTripUpdatesSuccess(), "Should generate trip updates feed");
        assertTrue(result.isAlertsSuccess(), "Should generate alerts feed");

        // Step 4: Verify files were created
        assertTrue(Files.exists(tempDir.resolve("VehiclePosition.pb")), "Vehicle positions file should exist");
        assertTrue(Files.exists(tempDir.resolve("TripUpdate.pb")), "Trip updates file should exist");
        assertTrue(Files.exists(tempDir.resolve("Alerts.pb")), "Alerts file should exist");

        // Step 5: Verify file contents
        assertTrue(Files.size(tempDir.resolve("VehiclePosition.pb")) > 0, "Vehicle positions file should not be empty");
        assertTrue(Files.size(tempDir.resolve("TripUpdate.pb")) > 0, "Trip updates file should not be empty");
        assertTrue(Files.size(tempDir.resolve("Alerts.pb")) > 0, "Alerts file should not be empty");
    }

    @Test
    @DisplayName("Should process complete RailComm to GTFS-RT pipeline")
    void shouldProcessCompleteRailCommToGtfsRtPipeline() throws Exception {
        // Step 1: Transform RailComm data to GTFS-RT
        String railCommPayload = createValidRailCommPayload();
        List<GtfsRealtime.VehiclePosition> rawVehiclePositions = railCommTransformer.transformToVehiclePositions(railCommPayload);
        List<GtfsRealtime.TripUpdate> rawTripUpdates = railCommTransformer.transformToTripUpdates(railCommPayload);

        assertFalse(rawVehiclePositions.isEmpty(), "Should transform RailComm to vehicle positions");
        assertFalse(rawTripUpdates.isEmpty(), "Should transform RailComm to trip updates");

        // Step 2: Process and validate data
        List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(rawVehiclePositions);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(rawTripUpdates);
        List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());

        assertFalse(processedVehiclePositions.isEmpty(), "Should process vehicle positions");
        assertFalse(processedTripUpdates.isEmpty(), "Should process trip updates");

        // Step 3: Generate protobuf feeds
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
            processedVehiclePositions, processedTripUpdates, processedAlerts);

        assertTrue(result.isVehiclePositionsSuccess(), "Should generate vehicle positions feed");
        assertTrue(result.isTripUpdatesSuccess(), "Should generate trip updates feed");
        assertTrue(result.isAlertsSuccess(), "Should generate alerts feed");

        // Verify light rail route normalization worked
        assertEquals(1, result.getVehicleCount(), "Should have correct vehicle count");
        assertEquals(1, result.getTripUpdateCount(), "Should have correct trip update count");
    }

    @Test
    @DisplayName("Should handle mixed SIRI and RailComm data")
    void shouldHandleMixedSiriAndRailCommData() throws Exception {
        // Transform both SIRI and RailComm data
        String siriPayload = createValidSiriPayload();
        String railCommPayload = createValidRailCommPayload();

        List<GtfsRealtime.VehiclePosition> siriVehiclePositions = siriTransformer.transformToVehiclePositions(siriPayload);
        List<GtfsRealtime.TripUpdate> siriTripUpdates = siriTransformer.transformToTripUpdates(siriPayload);

        List<GtfsRealtime.VehiclePosition> railCommVehiclePositions = railCommTransformer.transformToVehiclePositions(railCommPayload);
        List<GtfsRealtime.TripUpdate> railCommTripUpdates = railCommTransformer.transformToTripUpdates(railCommPayload);

        // Combine data from both sources
        List<GtfsRealtime.VehiclePosition> combinedVehiclePositions = new ArrayList<>();
        combinedVehiclePositions.addAll(siriVehiclePositions);
        combinedVehiclePositions.addAll(railCommVehiclePositions);

        List<GtfsRealtime.TripUpdate> combinedTripUpdates = new ArrayList<>();
        combinedTripUpdates.addAll(siriTripUpdates);
        combinedTripUpdates.addAll(railCommTripUpdates);

        // Process combined data
        List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(combinedVehiclePositions);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(combinedTripUpdates);
        List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());


        assertEquals(2, processedVehiclePositions.size(), "Should process both SIRI and RailComm vehicle positions");
        assertEquals(2, processedTripUpdates.size(), "Should process both SIRI and RailComm trip updates");

        // Generate combined feed
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
            processedVehiclePositions, processedTripUpdates, processedAlerts);

        assertTrue(result.isAllSuccess(), "Should generate all feeds successfully");
        assertEquals(2, result.getVehicleCount(), "Should have correct combined vehicle count");
        assertEquals(2, result.getTripUpdateCount(), "Should have correct combined trip update count");
    }

    @Test
    @DisplayName("Should handle invalid data gracefully in pipeline")
    void shouldHandleInvalidDataGracefullyInPipeline() throws Exception {
        // Test with invalid SIRI data
        String invalidSiriPayload = "{ invalid json }";
        List<GtfsRealtime.VehiclePosition> siriVehiclePositions = siriTransformer.transformToVehiclePositions(invalidSiriPayload);
        List<GtfsRealtime.TripUpdate> siriTripUpdates = siriTransformer.transformToTripUpdates(invalidSiriPayload);

        assertTrue(siriVehiclePositions.isEmpty(), "Should handle invalid SIRI gracefully");
        assertTrue(siriTripUpdates.isEmpty(), "Should handle invalid SIRI gracefully");

        // Test with invalid RailComm data
        String invalidRailCommPayload = "<xml>not json</xml>";
        List<GtfsRealtime.VehiclePosition> railCommVehiclePositions = railCommTransformer.transformToVehiclePositions(invalidRailCommPayload);
        List<GtfsRealtime.TripUpdate> railCommTripUpdates = railCommTransformer.transformToTripUpdates(invalidRailCommPayload);

        assertTrue(railCommVehiclePositions.isEmpty(), "Should handle invalid RailComm gracefully");
        assertTrue(railCommTripUpdates.isEmpty(), "Should handle invalid RailComm gracefully");

        // Process empty data through the pipeline
        List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(new ArrayList<>());
        List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(new ArrayList<>());
        List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());

        assertTrue(processedVehiclePositions.isEmpty(), "Should process empty vehicle positions");
        assertTrue(processedTripUpdates.isEmpty(), "Should process empty trip updates");
        assertTrue(processedAlerts.isEmpty(), "Should process empty alerts");

        // Generate feeds with no data
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
            processedVehiclePositions, processedTripUpdates, processedAlerts);

        assertFalse(result.isVehiclePositionsSuccess(), "Should fail to generate empty vehicle positions feed");
        assertFalse(result.isTripUpdatesSuccess(), "Should fail to generate empty trip updates feed");
        assertTrue(result.isAlertsSuccess(), "Should generate empty alerts feed");
    }

    @Test
    @DisplayName("Should measure pipeline performance")
    void shouldMeasurePipelinePerformance() throws Exception {
        long startTime = System.currentTimeMillis();

        // Transform data
        String siriPayload = createValidSiriPayload();
        List<GtfsRealtime.VehiclePosition> vehiclePositions = siriTransformer.transformToVehiclePositions(siriPayload);
        List<GtfsRealtime.TripUpdate> tripUpdates = siriTransformer.transformToTripUpdates(siriPayload);

        // Process data
        List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(vehiclePositions);
        List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(tripUpdates);
        List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());

        // Generate feeds
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
            processedVehiclePositions, processedTripUpdates, processedAlerts);

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Performance assertions
        assertTrue(totalDuration < 5000, "Complete pipeline should execute in under 5 seconds");
        assertTrue(result.getDurationMs() < 1000, "Feed generation should complete in under 1 second");
        assertTrue(result.isAllSuccess(), "Pipeline should complete successfully");
    }

    private String createValidSiriPayload() {
        // Use current time to avoid stale timestamp issues
        long currentTimeMs = System.currentTimeMillis();
        java.time.Instant now = java.time.Instant.ofEpochMilli(currentTimeMs);
        java.time.Instant future = now.plus(java.time.Duration.ofMinutes(15));
        java.time.Instant futureDeparture = now.plus(java.time.Duration.ofMinutes(16));
        
        return """
            <?xml version="1.0"?>
            <Siri version="2.0">
              <ServiceDelivery>
                <VehicleMonitoringDelivery>
                  <VehicleActivity>
                    <RecordedAtTime>""" + now.toString() + """
</RecordedAtTime>
                    <ValidUntilTime>""" + future.toString() + """
</ValidUntilTime>
                    <MonitoredVehicleJourney>
                      <LineRef>15</LineRef>
                      <VehicleRef>bus-1234</VehicleRef>
                      <VehicleLocation>
                        <Longitude>-104.9903</Longitude>
                        <Latitude>39.7392</Latitude>
                      </VehicleLocation>
                      <Bearing>45.0</Bearing>
                      <Speed>15.5</Speed>
                      <InPanic>false</InPanic>
                      <MonitoredCall>
                        <StopPointRef>stop-001</StopPointRef>
                        <ExpectedArrivalTime>""" + future.toString() + """
</ExpectedArrivalTime>
                        <ExpectedDepartureTime>""" + futureDeparture.toString() + """
</ExpectedDepartureTime>
                      </MonitoredCall>
                    </MonitoredVehicleJourney>
                  </VehicleActivity>
                </VehicleMonitoringDelivery>
              </ServiceDelivery>
            </Siri>
            """;
    }

    private String createValidRailCommPayload() {
        // Use current time to avoid stale timestamp issues  
        java.time.Instant now = java.time.Instant.ofEpochMilli(System.currentTimeMillis());
        java.time.Instant future = now.plus(java.time.Duration.ofMinutes(15));
        java.time.Instant futureDeparture = now.plus(java.time.Duration.ofMinutes(16));
        
        return """
            {
              "trains": [
                {
                  "trainId": "A-001",
                  "routeId": "A",
                  "latitude": 39.7500,
                  "longitude": -105.0000,
                  "speed": 25.0,
                  "heading": 180.0,
                  "timestamp": \"""" + now.toString() + """
",
                  "nextStopId": "A01",
                  "arrivalTime": \"""" + future.toString() + """
",
                  "departureTime": \"""" + futureDeparture.toString() + """
",
                  "delay": 120
                }
              ]
            }
            """;
    }
}