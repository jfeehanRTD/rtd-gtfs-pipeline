package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GTFS-RT Feed Generator Tests")
class GTFSRTFeedGeneratorTest {

    @TempDir
    Path tempDir;
    
    private GTFSRTFeedGenerator feedGenerator;

    @BeforeEach
    void setUp() {
        feedGenerator = new GTFSRTFeedGenerator(tempDir.toString(), "RTD Test", "https://test.rtd-denver.com");
    }

    @Test
    @DisplayName("Should generate vehicle positions feed successfully")
    void shouldGenerateVehiclePositionsFeedSuccessfully() throws Exception {
        List<GtfsRealtime.VehiclePosition> vehiclePositions = createTestVehiclePositions();

        boolean result = feedGenerator.generateVehiclePositionsFeed(vehiclePositions);

        assertTrue(result, "Should generate vehicle positions feed successfully");

        Path vehiclePositionsFile = tempDir.resolve("VehiclePosition.pb");
        assertTrue(Files.exists(vehiclePositionsFile), "Vehicle positions file should exist");
        assertTrue(Files.size(vehiclePositionsFile) > 0, "Vehicle positions file should not be empty");
    }

    @Test
    @DisplayName("Should generate trip updates feed successfully")
    void shouldGenerateTripUpdatesFeedSuccessfully() throws Exception {
        List<GtfsRealtime.TripUpdate> tripUpdates = createTestTripUpdates();

        boolean result = feedGenerator.generateTripUpdatesFeed(tripUpdates);

        assertTrue(result, "Should generate trip updates feed successfully");

        Path tripUpdatesFile = tempDir.resolve("TripUpdate.pb");
        assertTrue(Files.exists(tripUpdatesFile), "Trip updates file should exist");
        assertTrue(Files.size(tripUpdatesFile) > 0, "Trip updates file should not be empty");
    }

    @Test
    @DisplayName("Should generate alerts feed successfully")
    void shouldGenerateAlertsFeedSuccessfully() throws Exception {
        List<GtfsRealtime.Alert> alerts = createTestAlerts();

        boolean result = feedGenerator.generateAlertsFeed(alerts);

        assertTrue(result, "Should generate alerts feed successfully");

        Path alertsFile = tempDir.resolve("Alerts.pb");
        assertTrue(Files.exists(alertsFile), "Alerts file should exist");
        assertTrue(Files.size(alertsFile) > 0, "Alerts file should not be empty");
    }

    @Test
    @DisplayName("Should generate empty alerts feed with empty list")
    void shouldGenerateEmptyAlertsFeedWithEmptyList() throws Exception {
        List<GtfsRealtime.Alert> emptyAlerts = new ArrayList<>();

        boolean result = feedGenerator.generateAlertsFeed(emptyAlerts);

        assertTrue(result, "Should generate empty alerts feed successfully");

        Path alertsFile = tempDir.resolve("Alerts.pb");
        assertTrue(Files.exists(alertsFile), "Empty alerts file should exist");
        assertTrue(Files.size(alertsFile) > 0, "Empty alerts file should contain header");
    }

    @Test
    @DisplayName("Should generate all feeds at once")
    void shouldGenerateAllFeedsAtOnce() throws Exception {
        List<GtfsRealtime.VehiclePosition> vehiclePositions = createTestVehiclePositions();
        List<GtfsRealtime.TripUpdate> tripUpdates = createTestTripUpdates();
        List<GtfsRealtime.Alert> alerts = createTestAlerts();

        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(vehiclePositions, tripUpdates, alerts);

        assertTrue(result.isAllSuccess(), "All feeds should be generated successfully");
        assertTrue(result.isVehiclePositionsSuccess(), "Vehicle positions feed should succeed");
        assertTrue(result.isTripUpdatesSuccess(), "Trip updates feed should succeed");
        assertTrue(result.isAlertsSuccess(), "Alerts feed should succeed");
        
        assertEquals(2, result.getVehicleCount(), "Should report correct vehicle count");
        assertEquals(2, result.getTripUpdateCount(), "Should report correct trip update count");
        assertEquals(1, result.getAlertCount(), "Should report correct alert count");
        
        assertTrue(result.getDurationMs() >= 0, "Duration should be non-negative");

        // Verify all files exist
        assertTrue(Files.exists(tempDir.resolve("VehiclePosition.pb")), "Vehicle positions file should exist");
        assertTrue(Files.exists(tempDir.resolve("TripUpdate.pb")), "Trip updates file should exist");
        assertTrue(Files.exists(tempDir.resolve("Alerts.pb")), "Alerts file should exist");
    }

    @Test
    @DisplayName("Should handle null input lists gracefully")
    void shouldHandleNullInputListsGracefully() throws Exception {
        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(null, null, null);

        assertFalse(result.isVehiclePositionsSuccess(), "Vehicle positions should fail with null input");
        assertFalse(result.isTripUpdatesSuccess(), "Trip updates should fail with null input");
        assertTrue(result.isAlertsSuccess(), "Alerts should succeed with null input (empty feed)");
        
        assertEquals(0, result.getVehicleCount(), "Should report zero vehicle count");
        assertEquals(0, result.getTripUpdateCount(), "Should report zero trip update count");
        assertEquals(0, result.getAlertCount(), "Should report zero alert count");
    }

    @Test
    @DisplayName("Should handle empty input lists")
    void shouldHandleEmptyInputLists() throws Exception {
        List<GtfsRealtime.VehiclePosition> emptyVehiclePositions = new ArrayList<>();
        List<GtfsRealtime.TripUpdate> emptyTripUpdates = new ArrayList<>(); 
        List<GtfsRealtime.Alert> emptyAlerts = new ArrayList<>();

        GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(emptyVehiclePositions, emptyTripUpdates, emptyAlerts);

        assertFalse(result.isVehiclePositionsSuccess(), "Vehicle positions should fail with empty input");
        assertFalse(result.isTripUpdatesSuccess(), "Trip updates should fail with empty input");
        assertTrue(result.isAlertsSuccess(), "Alerts should succeed with empty input");
        
        assertEquals(0, result.getVehicleCount(), "Should report zero vehicle count");
        assertEquals(0, result.getTripUpdateCount(), "Should report zero trip update count");
        assertEquals(0, result.getAlertCount(), "Should report zero alert count");
    }

    @Test
    @DisplayName("Should provide feed statistics")
    void shouldProvideFeedStatistics() throws Exception {
        // Generate some feeds first
        List<GtfsRealtime.VehiclePosition> vehiclePositions = createTestVehiclePositions();
        List<GtfsRealtime.TripUpdate> tripUpdates = createTestTripUpdates();
        List<GtfsRealtime.Alert> alerts = createTestAlerts();
        
        feedGenerator.generateAllFeeds(vehiclePositions, tripUpdates, alerts);

        GTFSRTFeedGenerator.GTFSRTFeedStats stats = feedGenerator.getFeedStats();

        assertTrue(stats.getVehiclePositionsSize() > 0, "Vehicle positions file should have size");
        assertTrue(stats.getTripUpdatesSize() > 0, "Trip updates file should have size");
        assertTrue(stats.getAlertsSize() > 0, "Alerts file should have size");
        
        assertTrue(stats.getVehiclePositionsLastModified() > 0, "Vehicle positions should have last modified time");
        assertTrue(stats.getTripUpdatesLastModified() > 0, "Trip updates should have last modified time");
        assertTrue(stats.getAlertsLastModified() > 0, "Alerts should have last modified time");
    }

    @Test
    @DisplayName("Should provide valid output directory")
    void shouldProvideValidOutputDirectory() {
        String outputDirectory = feedGenerator.getOutputDirectory();
        
        assertNotNull(outputDirectory, "Output directory should not be null");
        assertEquals(tempDir.toString(), outputDirectory, "Output directory should match temp directory");
    }

    @Test
    @DisplayName("Should track feed sequence number")
    void shouldTrackFeedSequenceNumber() {
        long initialSequence = feedGenerator.getCurrentSequence();
        assertTrue(initialSequence > 0, "Initial sequence should be positive");
        
        // Sequence number is incremented internally, but we can test it exists
        long secondCheck = feedGenerator.getCurrentSequence();
        assertEquals(initialSequence, secondCheck, "Sequence should be consistent between calls");
    }

    @Test
    @DisplayName("Should create directory if it doesn't exist")
    void shouldCreateDirectoryIfNotExists() throws Exception {
        Path nonExistentDir = tempDir.resolve("new-directory");
        assertFalse(Files.exists(nonExistentDir), "Directory should not exist initially");

        GTFSRTFeedGenerator newGenerator = new GTFSRTFeedGenerator(nonExistentDir.toString(), "Test", "https://test.com");
        
        assertTrue(Files.exists(nonExistentDir), "Directory should be created");
        assertTrue(Files.isDirectory(nonExistentDir), "Should be a directory");
    }

    private List<GtfsRealtime.VehiclePosition> createTestVehiclePositions() {
        List<GtfsRealtime.VehiclePosition> positions = new ArrayList<>();

        // Create first vehicle position
        GtfsRealtime.VehiclePosition.Builder pos1 = GtfsRealtime.VehiclePosition.newBuilder();
        pos1.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-001")
            .setLabel("Bus 001")
            .build());
        pos1.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-001")
            .setRouteId("15")
            .build());
        pos1.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.7392f)
            .setLongitude(-104.9903f)
            .setBearing(45.0f)
            .setSpeed(15.5f)
            .build());
        pos1.setTimestamp(Instant.now().getEpochSecond());
        positions.add(pos1.build());

        // Create second vehicle position
        GtfsRealtime.VehiclePosition.Builder pos2 = GtfsRealtime.VehiclePosition.newBuilder();
        pos2.setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder()
            .setId("vehicle-002")
            .setLabel("Bus 002")
            .build());
        pos2.setTrip(GtfsRealtime.TripDescriptor.newBuilder()
            .setTripId("trip-002")
            .setRouteId("16")
            .build());
        pos2.setPosition(GtfsRealtime.Position.newBuilder()
            .setLatitude(39.6500f)
            .setLongitude(-105.1000f)
            .setBearing(90.0f)
            .setSpeed(20.0f)
            .build());
        pos2.setTimestamp(Instant.now().getEpochSecond());
        positions.add(pos2.build());

        return positions;
    }

    private List<GtfsRealtime.TripUpdate> createTestTripUpdates() {
        List<GtfsRealtime.TripUpdate> updates = new ArrayList<>();

        // Create first trip update
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
            .setDeparture(GtfsRealtime.TripUpdate.StopTimeEvent.newBuilder()
                .setTime(Instant.now().plusSeconds(630).getEpochSecond())
                .setDelay(60)
                .build())
            .build());
        updates.add(update1.build());

        // Create second trip update
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

    private List<GtfsRealtime.Alert> createTestAlerts() {
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
        alert.setDescriptionText(GtfsRealtime.TranslatedString.newBuilder()
            .addTranslation(GtfsRealtime.TranslatedString.Translation.newBuilder()
                .setText("Route 15 is experiencing delays due to construction.")
                .setLanguage("en")
                .build())
            .build());
        
        alerts.add(alert.build());
        return alerts;
    }
}