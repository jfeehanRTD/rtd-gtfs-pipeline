package com.rtd.pipeline.gtfsrt;

import com.google.transit.realtime.GtfsRealtime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RTD GTFS-RT Feed Comparison Tests")
class RTDFeedComparisonTest {

    private static final String RTD_VEHICLE_POSITIONS_URL = "https://www.rtd-denver.com/files/gtfs-rt/VehiclePosition.pb";
    private static final String RTD_TRIP_UPDATES_URL = "https://www.rtd-denver.com/files/gtfs-rt/TripUpdate.pb";
    
    private static final String LOCAL_GTFS_RT_DIR = "data/gtfs-rt";
    private static final String LOCAL_VEHICLE_POSITIONS_FILE = "VehiclePosition.pb";
    private static final String LOCAL_TRIP_UPDATES_FILE = "TripUpdate.pb";
    
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    private static final int READ_TIMEOUT = 15000; // 15 seconds

    @BeforeEach
    void setUp() {
        // Ensure local files directory exists
        Path gtfsRtDir = Paths.get(LOCAL_GTFS_RT_DIR);
        if (!Files.exists(gtfsRtDir)) {
            try {
                Files.createDirectories(gtfsRtDir);
            } catch (IOException e) {
                fail("Could not create GTFS-RT directory: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Should compare vehicle positions feeds")
    @EnabledIf("isNetworkAvailable")
    void shouldCompareVehiclePositionsFeeds() throws Exception {
        System.out.println("🚌 Comparing Vehicle Positions feeds...");
        
        // Download RTD's official feed
        GtfsRealtime.FeedMessage rtdFeed = downloadRtdFeed(RTD_VEHICLE_POSITIONS_URL);
        assertNotNull(rtdFeed, "Should successfully download RTD vehicle positions feed");
        
        // Load our generated feed
        GtfsRealtime.FeedMessage localFeed = loadLocalFeed(LOCAL_VEHICLE_POSITIONS_FILE);
        assertNotNull(localFeed, "Should successfully load local vehicle positions feed");
        
        // Perform detailed comparison
        VehiclePositionComparison comparison = compareVehiclePositions(rtdFeed, localFeed);
        
        // Print comparison results
        printVehiclePositionComparison(comparison);
        
        // Assertions
        assertTrue(comparison.rtdVehicleCount > 0, "RTD feed should contain vehicles");
        
        // Allow some tolerance for timing differences
        long timeDiffSeconds = Math.abs(comparison.rtdTimestamp - comparison.localTimestamp);
        assertTrue(timeDiffSeconds < 300, "Feed timestamps should be within 5 minutes of each other");
        
        // Data quality assertions
        assertTrue(comparison.validCoordinatesRtd >= comparison.rtdVehicleCount * 0.8, 
            "At least 80% of RTD vehicles should have valid coordinates");
        assertTrue(comparison.validCoordinatesLocal >= comparison.localVehicleCount * 0.8, 
            "At least 80% of local vehicles should have valid coordinates");
    }

    @Test
    @DisplayName("Should compare trip updates feeds")
    @EnabledIf("isNetworkAvailable") 
    void shouldCompareTripUpdatesFeeds() throws Exception {
        System.out.println("🚊 Comparing Trip Updates feeds...");
        
        // Download RTD's official feed
        GtfsRealtime.FeedMessage rtdFeed = downloadRtdFeed(RTD_TRIP_UPDATES_URL);
        assertNotNull(rtdFeed, "Should successfully download RTD trip updates feed");
        
        // Load our generated feed
        GtfsRealtime.FeedMessage localFeed = loadLocalFeed(LOCAL_TRIP_UPDATES_FILE);
        assertNotNull(localFeed, "Should successfully load local trip updates feed");
        
        // Perform detailed comparison
        TripUpdateComparison comparison = compareTripUpdates(rtdFeed, localFeed);
        
        // Print comparison results
        printTripUpdateComparison(comparison);
        
        // Assertions
        assertTrue(comparison.rtdTripCount > 0, "RTD feed should contain trip updates");
        
        // Allow some tolerance for timing differences
        long timeDiffSeconds = Math.abs(comparison.rtdTimestamp - comparison.localTimestamp);
        assertTrue(timeDiffSeconds < 300, "Feed timestamps should be within 5 minutes of each other");
    }

    @Test
    @DisplayName("Should analyze data coverage overlap")
    @EnabledIf("isNetworkAvailable")
    void shouldAnalyzeDataCoverageOverlap() throws Exception {
        System.out.println("🔍 Analyzing data coverage overlap...");
        
        // Download both RTD feeds
        GtfsRealtime.FeedMessage rtdVehiclePositions = downloadRtdFeed(RTD_VEHICLE_POSITIONS_URL);
        GtfsRealtime.FeedMessage rtdTripUpdates = downloadRtdFeed(RTD_TRIP_UPDATES_URL);
        
        // Load both local feeds
        GtfsRealtime.FeedMessage localVehiclePositions = loadLocalFeed(LOCAL_VEHICLE_POSITIONS_FILE);
        GtfsRealtime.FeedMessage localTripUpdates = loadLocalFeed(LOCAL_TRIP_UPDATES_FILE);
        
        // Analyze coverage
        CoverageAnalysis analysis = analyzeCoverage(rtdVehiclePositions, rtdTripUpdates, 
                                                   localVehiclePositions, localTripUpdates);
        
        printCoverageAnalysis(analysis);
        
        // Coverage assertions
        assertTrue(analysis.routeCoveragePercent >= 50.0, 
            "Should cover at least 50% of RTD routes");
        assertTrue(analysis.sharedVehicleIds.size() > 0, 
            "Should have some vehicle IDs in common with RTD feed");
    }

    @Test
    @DisplayName("Should validate feed structure compatibility")
    @EnabledIf("isNetworkAvailable")
    void shouldValidateFeedStructureCompatibility() throws Exception {
        System.out.println("🏗️ Validating feed structure compatibility...");
        
        // Download RTD feeds
        GtfsRealtime.FeedMessage rtdVehiclePositions = downloadRtdFeed(RTD_VEHICLE_POSITIONS_URL);
        GtfsRealtime.FeedMessage rtdTripUpdates = downloadRtdFeed(RTD_TRIP_UPDATES_URL);
        
        // Load local feeds
        GtfsRealtime.FeedMessage localVehiclePositions = loadLocalFeed(LOCAL_VEHICLE_POSITIONS_FILE);
        GtfsRealtime.FeedMessage localTripUpdates = loadLocalFeed(LOCAL_TRIP_UPDATES_FILE);
        
        // Validate structure compatibility
        StructureCompatibility compatibility = validateStructureCompatibility(
            rtdVehiclePositions, rtdTripUpdates, localVehiclePositions, localTripUpdates);
        
        printStructureCompatibility(compatibility);
        
        // Structure assertions
        assertTrue(compatibility.headerCompatible, "Feed headers should be compatible");
        assertTrue(compatibility.vehiclePositionStructureCompatible, 
            "Vehicle position structure should be compatible");
        assertTrue(compatibility.tripUpdateStructureCompatible, 
            "Trip update structure should be compatible");
    }

    private GtfsRealtime.FeedMessage downloadRtdFeed(String feedUrl) throws IOException {
        System.out.println("📥 Downloading feed from: " + feedUrl);
        
        URL url = new URL(feedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "RTD-Pipeline-Test/1.0");
        
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + " when downloading " + feedUrl);
            }
            
            try (InputStream inputStream = connection.getInputStream()) {
                GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(inputStream);
                System.out.println("✅ Successfully downloaded feed with " + feed.getEntityCount() + " entities");
                return feed;
            }
        } finally {
            connection.disconnect();
        }
    }

    private GtfsRealtime.FeedMessage loadLocalFeed(String filename) throws IOException {
        Path feedPath = Paths.get(LOCAL_GTFS_RT_DIR, filename);
        
        if (!Files.exists(feedPath)) {
            System.out.println("⚠️ Local feed file not found: " + feedPath);
            System.out.println("💡 Run the GTFS-RT pipeline first: ./rtd-control.sh gtfs-rt all");
            return null;
        }
        
        try (InputStream inputStream = Files.newInputStream(feedPath)) {
            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(inputStream);
            System.out.println("✅ Successfully loaded local feed with " + feed.getEntityCount() + " entities");
            return feed;
        }
    }

    private VehiclePositionComparison compareVehiclePositions(GtfsRealtime.FeedMessage rtdFeed, 
                                                              GtfsRealtime.FeedMessage localFeed) {
        VehiclePositionComparison comparison = new VehiclePositionComparison();
        
        // Basic counts
        comparison.rtdVehicleCount = (int) rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle())
            .count();
        comparison.localVehicleCount = (int) localFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle())
            .count();
        
        // Timestamps
        comparison.rtdTimestamp = rtdFeed.getHeader().getTimestamp();
        comparison.localTimestamp = localFeed.getHeader().getTimestamp();
        
        // Route analysis
        comparison.rtdRoutes = rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasTrip())
            .map(entity -> entity.getVehicle().getTrip().getRouteId())
            .collect(Collectors.toSet());
        comparison.localRoutes = localFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasTrip())
            .map(entity -> entity.getVehicle().getTrip().getRouteId())
            .collect(Collectors.toSet());
        comparison.commonRoutes = new HashSet<>(comparison.rtdRoutes);
        comparison.commonRoutes.retainAll(comparison.localRoutes);
        
        // Coordinate validation
        comparison.validCoordinatesRtd = (int) rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasPosition())
            .filter(entity -> isValidColoradoCoordinates(entity.getVehicle().getPosition()))
            .count();
        comparison.validCoordinatesLocal = (int) localFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasPosition())
            .filter(entity -> isValidColoradoCoordinates(entity.getVehicle().getPosition()))
            .count();
        
        // Vehicle ID overlap
        Set<String> rtdVehicleIds = rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId())
            .map(entity -> entity.getVehicle().getVehicle().getId())
            .collect(Collectors.toSet());
        Set<String> localVehicleIds = localFeed.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId())
            .map(entity -> entity.getVehicle().getVehicle().getId())
            .collect(Collectors.toSet());
        comparison.commonVehicleIds = new HashSet<>(rtdVehicleIds);
        comparison.commonVehicleIds.retainAll(localVehicleIds);
        
        return comparison;
    }

    private TripUpdateComparison compareTripUpdates(GtfsRealtime.FeedMessage rtdFeed,
                                                   GtfsRealtime.FeedMessage localFeed) {
        TripUpdateComparison comparison = new TripUpdateComparison();
        
        // Basic counts
        comparison.rtdTripCount = (int) rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate())
            .count();
        comparison.localTripCount = (int) localFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate())
            .count();
        
        // Timestamps
        comparison.rtdTimestamp = rtdFeed.getHeader().getTimestamp();
        comparison.localTimestamp = localFeed.getHeader().getTimestamp();
        
        // Route analysis
        comparison.rtdRoutes = rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate() && entity.getTripUpdate().hasTrip())
            .map(entity -> entity.getTripUpdate().getTrip().getRouteId())
            .collect(Collectors.toSet());
        comparison.localRoutes = localFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate() && entity.getTripUpdate().hasTrip())
            .map(entity -> entity.getTripUpdate().getTrip().getRouteId())
            .collect(Collectors.toSet());
        comparison.commonRoutes = new HashSet<>(comparison.rtdRoutes);
        comparison.commonRoutes.retainAll(comparison.localRoutes);
        
        // Stop time updates analysis
        comparison.rtdStopTimeUpdates = rtdFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate())
            .mapToInt(entity -> entity.getTripUpdate().getStopTimeUpdateCount())
            .sum();
        comparison.localStopTimeUpdates = localFeed.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate())
            .mapToInt(entity -> entity.getTripUpdate().getStopTimeUpdateCount())
            .sum();
        
        return comparison;
    }

    private CoverageAnalysis analyzeCoverage(GtfsRealtime.FeedMessage rtdVehiclePositions,
                                            GtfsRealtime.FeedMessage rtdTripUpdates,
                                            GtfsRealtime.FeedMessage localVehiclePositions,
                                            GtfsRealtime.FeedMessage localTripUpdates) {
        CoverageAnalysis analysis = new CoverageAnalysis();
        
        // Collect all routes from RTD feeds
        Set<String> rtdAllRoutes = new HashSet<>();
        rtdVehiclePositions.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasTrip())
            .forEach(entity -> rtdAllRoutes.add(entity.getVehicle().getTrip().getRouteId()));
        rtdTripUpdates.getEntityList().stream()
            .filter(entity -> entity.hasTripUpdate() && entity.getTripUpdate().hasTrip())
            .forEach(entity -> rtdAllRoutes.add(entity.getTripUpdate().getTrip().getRouteId()));
        
        // Collect all routes from local feeds
        Set<String> localAllRoutes = new HashSet<>();
        if (localVehiclePositions != null) {
            localVehiclePositions.getEntityList().stream()
                .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasTrip())
                .forEach(entity -> localAllRoutes.add(entity.getVehicle().getTrip().getRouteId()));
        }
        if (localTripUpdates != null) {
            localTripUpdates.getEntityList().stream()
                .filter(entity -> entity.hasTripUpdate() && entity.getTripUpdate().hasTrip())
                .forEach(entity -> localAllRoutes.add(entity.getTripUpdate().getTrip().getRouteId()));
        }
        
        analysis.rtdRoutes = rtdAllRoutes;
        analysis.localRoutes = localAllRoutes;
        analysis.sharedRoutes = new HashSet<>(rtdAllRoutes);
        analysis.sharedRoutes.retainAll(localAllRoutes);
        
        analysis.routeCoveragePercent = rtdAllRoutes.isEmpty() ? 0.0 :
            (analysis.sharedRoutes.size() * 100.0) / rtdAllRoutes.size();
        
        // Vehicle ID overlap
        Set<String> rtdVehicleIds = new HashSet<>();
        rtdVehiclePositions.getEntityList().stream()
            .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId())
            .forEach(entity -> rtdVehicleIds.add(entity.getVehicle().getVehicle().getId()));
        
        Set<String> localVehicleIds = new HashSet<>();
        if (localVehiclePositions != null) {
            localVehiclePositions.getEntityList().stream()
                .filter(entity -> entity.hasVehicle() && entity.getVehicle().hasVehicle() && entity.getVehicle().getVehicle().hasId())
                .forEach(entity -> localVehicleIds.add(entity.getVehicle().getVehicle().getId()));
        }
        
        analysis.sharedVehicleIds = new HashSet<>(rtdVehicleIds);
        analysis.sharedVehicleIds.retainAll(localVehicleIds);
        
        return analysis;
    }

    private StructureCompatibility validateStructureCompatibility(GtfsRealtime.FeedMessage rtdVehiclePositions,
                                                                  GtfsRealtime.FeedMessage rtdTripUpdates,
                                                                  GtfsRealtime.FeedMessage localVehiclePositions,
                                                                  GtfsRealtime.FeedMessage localTripUpdates) {
        StructureCompatibility compatibility = new StructureCompatibility();
        
        // Header compatibility
        compatibility.headerCompatible = 
            rtdVehiclePositions.getHeader().getGtfsRealtimeVersion().equals(
                localVehiclePositions != null ? localVehiclePositions.getHeader().getGtfsRealtimeVersion() : "2.0");
        
        // Vehicle position structure compatibility
        compatibility.vehiclePositionStructureCompatible = true;
        if (localVehiclePositions != null && localVehiclePositions.getEntityCount() > 0) {
            GtfsRealtime.FeedEntity localEntity = localVehiclePositions.getEntity(0);
            if (localEntity.hasVehicle()) {
                // Check if local feed has similar structure to RTD feed
                compatibility.vehiclePositionStructureCompatible = 
                    (localEntity.getVehicle().hasVehicle() && localEntity.getVehicle().getVehicle().hasId()) && // Must have vehicle ID
                    (localEntity.getVehicle().hasPosition() || localEntity.getVehicle().hasTrip()); // Position or trip info
            }
        }
        
        // Trip update structure compatibility
        compatibility.tripUpdateStructureCompatible = true;
        if (localTripUpdates != null && localTripUpdates.getEntityCount() > 0) {
            GtfsRealtime.FeedEntity localEntity = localTripUpdates.getEntity(0);
            if (localEntity.hasTripUpdate()) {
                compatibility.tripUpdateStructureCompatible = 
                    localEntity.getTripUpdate().hasTrip() && // Must have trip info
                    localEntity.getTripUpdate().getStopTimeUpdateCount() > 0; // Must have stop time updates
            }
        }
        
        return compatibility;
    }

    private boolean isValidColoradoCoordinates(GtfsRealtime.Position position) {
        float lat = position.getLatitude();
        float lon = position.getLongitude();
        return lat >= 39.0f && lat <= 41.0f && lon >= -106.0f && lon <= -104.0f;
    }

    private void printVehiclePositionComparison(VehiclePositionComparison comparison) {
        System.out.println("\n📊 Vehicle Position Feed Comparison Results:");
        System.out.println("=" .repeat(50));
        System.out.println("RTD Feed Vehicles: " + comparison.rtdVehicleCount);
        System.out.println("Local Feed Vehicles: " + comparison.localVehicleCount);
        System.out.println("RTD Timestamp: " + Instant.ofEpochSecond(comparison.rtdTimestamp));
        System.out.println("Local Timestamp: " + Instant.ofEpochSecond(comparison.localTimestamp));
        System.out.println("RTD Routes: " + comparison.rtdRoutes.size() + " " + comparison.rtdRoutes);
        System.out.println("Local Routes: " + comparison.localRoutes.size() + " " + comparison.localRoutes);
        System.out.println("Common Routes: " + comparison.commonRoutes.size() + " " + comparison.commonRoutes);
        System.out.println("RTD Valid Coordinates: " + comparison.validCoordinatesRtd + "/" + comparison.rtdVehicleCount);
        System.out.println("Local Valid Coordinates: " + comparison.validCoordinatesLocal + "/" + comparison.localVehicleCount);
        System.out.println("Common Vehicle IDs: " + comparison.commonVehicleIds.size());
    }

    private void printTripUpdateComparison(TripUpdateComparison comparison) {
        System.out.println("\n📊 Trip Update Feed Comparison Results:");
        System.out.println("=" .repeat(50));
        System.out.println("RTD Feed Trips: " + comparison.rtdTripCount);
        System.out.println("Local Feed Trips: " + comparison.localTripCount);
        System.out.println("RTD Timestamp: " + Instant.ofEpochSecond(comparison.rtdTimestamp));
        System.out.println("Local Timestamp: " + Instant.ofEpochSecond(comparison.localTimestamp));
        System.out.println("RTD Routes: " + comparison.rtdRoutes.size() + " " + comparison.rtdRoutes);
        System.out.println("Local Routes: " + comparison.localRoutes.size() + " " + comparison.localRoutes);
        System.out.println("Common Routes: " + comparison.commonRoutes.size() + " " + comparison.commonRoutes);
        System.out.println("RTD Stop Time Updates: " + comparison.rtdStopTimeUpdates);
        System.out.println("Local Stop Time Updates: " + comparison.localStopTimeUpdates);
    }

    private void printCoverageAnalysis(CoverageAnalysis analysis) {
        System.out.println("\n📈 Data Coverage Analysis:");
        System.out.println("=" .repeat(50));
        System.out.println("RTD Routes: " + analysis.rtdRoutes.size());
        System.out.println("Local Routes: " + analysis.localRoutes.size());
        System.out.println("Shared Routes: " + analysis.sharedRoutes.size() + " " + analysis.sharedRoutes);
        System.out.println("Route Coverage: " + String.format("%.1f%%", analysis.routeCoveragePercent));
        System.out.println("Shared Vehicle IDs: " + analysis.sharedVehicleIds.size());
    }

    private void printStructureCompatibility(StructureCompatibility compatibility) {
        System.out.println("\n🏗️ Structure Compatibility:");
        System.out.println("=" .repeat(50));
        System.out.println("Header Compatible: " + (compatibility.headerCompatible ? "✅" : "❌"));
        System.out.println("Vehicle Position Structure: " + (compatibility.vehiclePositionStructureCompatible ? "✅" : "❌"));
        System.out.println("Trip Update Structure: " + (compatibility.tripUpdateStructureCompatible ? "✅" : "❌"));
    }

    // Helper method to check if network is available for conditional test execution
    static boolean isNetworkAvailable() {
        try {
            URL url = new URL("https://www.rtd-denver.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            System.out.println("⚠️ Network not available, skipping online comparison tests: " + e.getMessage());
            return false;
        }
    }

    // Data structures for comparison results
    private static class VehiclePositionComparison {
        int rtdVehicleCount;
        int localVehicleCount;
        long rtdTimestamp;
        long localTimestamp;
        Set<String> rtdRoutes = new HashSet<>();
        Set<String> localRoutes = new HashSet<>();
        Set<String> commonRoutes = new HashSet<>();
        int validCoordinatesRtd;
        int validCoordinatesLocal;
        Set<String> commonVehicleIds = new HashSet<>();
    }

    private static class TripUpdateComparison {
        int rtdTripCount;
        int localTripCount;
        long rtdTimestamp;
        long localTimestamp;
        Set<String> rtdRoutes = new HashSet<>();
        Set<String> localRoutes = new HashSet<>();
        Set<String> commonRoutes = new HashSet<>();
        int rtdStopTimeUpdates;
        int localStopTimeUpdates;
    }

    private static class CoverageAnalysis {
        Set<String> rtdRoutes = new HashSet<>();
        Set<String> localRoutes = new HashSet<>();
        Set<String> sharedRoutes = new HashSet<>();
        double routeCoveragePercent;
        Set<String> sharedVehicleIds = new HashSet<>();
    }

    private static class StructureCompatibility {
        boolean headerCompatible;
        boolean vehiclePositionStructureCompatible;
        boolean tripUpdateStructureCompatible;
    }
}