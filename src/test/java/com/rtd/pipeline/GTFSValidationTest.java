package com.rtd.pipeline;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive GTFS Validation Test Suite
 * Validates all RTD GTFS files and tracks changes for Aug 25 runboard
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GTFSValidationTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSValidationTest.class);
    
    private static final String RTD_GTFS_URL = "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip";
    private static final String AUG_25_TARGET_DATE = "20250825"; // Aug 25, 2025 runboard
    
    private static Map<String, List<Row>> gtfsData = new HashMap<>();
    private static Map<String, String[]> fileHeaders = new HashMap<>();
    private static StreamExecutionEnvironment env;
    private static StreamTableEnvironment tableEnv;
    
    @BeforeAll
    static void setup() throws Exception {
        LOG.info("=== RTD GTFS Comprehensive Validation Test Suite ===");
        LOG.info("Target runboard date: August 25, 2025");
        
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        tableEnv = StreamTableEnvironment.create(env);
        
        // Download all GTFS files
        downloadAllGTFSFiles();
        
        LOG.info("✅ Test setup completed - {} GTFS files loaded", gtfsData.size());
    }
    
    @Test
    @Order(1)
    void testGTFSConnectivityAndDownload() {
        LOG.info("Testing RTD GTFS endpoint connectivity and download");
        
        try {
            // Follow redirects to get to the actual ZIP file
            String currentUrl = RTD_GTFS_URL;
            HttpURLConnection connection = null;
            
            for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(false);
                
                int responseCode = connection.getResponseCode();
                
                if (responseCode == 200) {
                    break;
                } else if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location != null) {
                        if (location.startsWith("/")) {
                            URL baseUrl = new URL(currentUrl);
                            currentUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + location;
                        } else {
                            currentUrl = location;
                        }
                        connection.disconnect();
                        continue;
                    } else {
                        fail("Redirect without Location header");
                    }
                } else {
                    fail("HTTP error code: " + responseCode);
                }
            }
            
            assertEquals(200, connection.getResponseCode(), "RTD GTFS endpoint should return 200 OK after redirects");
            
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0) {
                assertTrue(contentLength > 1000000, "GTFS ZIP should be > 1MB (actual: " + contentLength + " bytes)");
            } else {
                LOG.info("Content-Length header not provided by server (streaming response)");
            }
            
            String contentType = connection.getContentType();
            LOG.info("Content type received: {}", contentType);
            
            // More flexible content type checking
            if (contentType != null) {
                assertTrue(contentType.contains("zip") || 
                          contentType.contains("application/octet-stream") ||
                          contentType.contains("application/x-zip") ||
                          contentType.contains("binary"),
                          "Content should be ZIP/binary format, got: " + contentType);
            } else {
                LOG.warn("No content type specified - assuming binary content");
            }
            
            LOG.info("✅ GTFS endpoint validation passed");
            LOG.info("   Final URL: {}", currentUrl);
            LOG.info("   Response code: {}", connection.getResponseCode());
            LOG.info("   Content length: {} bytes", contentLength);
            LOG.info("   Content type: {}", contentType);
            
            connection.disconnect();
            
        } catch (Exception e) {
            fail("Failed to connect to RTD GTFS endpoint: " + e.getMessage());
        }
    }
    
    @Test
    @Order(2)
    void testRequiredGTFSFilesPresent() {
        LOG.info("Validating presence of required GTFS files");
        
        // Required GTFS files according to GTFS specification
        String[] requiredFiles = {
            "agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt"
        };
        
        // Optional but commonly used files
        String[] optionalFiles = {
            "calendar.txt", "calendar_dates.txt", "fare_attributes.txt", 
            "fare_rules.txt", "shapes.txt", "frequencies.txt", "transfers.txt"
        };
        
        for (String requiredFile : requiredFiles) {
            assertTrue(gtfsData.containsKey(requiredFile), 
                      "Required GTFS file missing: " + requiredFile);
            assertFalse(gtfsData.get(requiredFile).isEmpty(), 
                       "Required GTFS file is empty: " + requiredFile);
            LOG.info("✅ Required file present: {} ({} records)", 
                    requiredFile, gtfsData.get(requiredFile).size());
        }
        
        for (String optionalFile : optionalFiles) {
            if (gtfsData.containsKey(optionalFile)) {
                LOG.info("📋 Optional file present: {} ({} records)", 
                        optionalFile, gtfsData.get(optionalFile).size());
            } else {
                LOG.info("📋 Optional file missing: {}", optionalFile);
            }
        }
        
        LOG.info("✅ GTFS file presence validation completed");
    }
    
    @Test
    @Order(3)
    void testAgencyFileValidation() {
        LOG.info("Validating agency.txt file structure and content");
        
        assertTrue(gtfsData.containsKey("agency.txt"), "agency.txt must be present");
        List<Row> agencyData = gtfsData.get("agency.txt");
        assertFalse(agencyData.isEmpty(), "agency.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("agency.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("agency_id"), "agency.txt must have agency_id column");
        assertTrue(headerList.contains("agency_name"), "agency.txt must have agency_name column");
        assertTrue(headerList.contains("agency_url"), "agency.txt must have agency_url column");
        assertTrue(headerList.contains("agency_timezone"), "agency.txt must have agency_timezone column");
        
        // Validate RTD agency record
        Row rtdAgency = agencyData.get(0);
        assertNotNull(rtdAgency.getField(getColumnIndex("agency.txt", "agency_name")), 
                     "Agency name must not be null");
        
        String agencyName = (String) rtdAgency.getField(getColumnIndex("agency.txt", "agency_name"));
        assertTrue(agencyName.toLowerCase().contains("rtd") || 
                  agencyName.toLowerCase().contains("regional transportation"), 
                  "Agency should be RTD");
        
        LOG.info("✅ agency.txt validation passed");
        LOG.info("   Agency: {}", agencyName);
        LOG.info("   Records: {}", agencyData.size());
    }
    
    @Test
    @Order(4)
    void testRoutesFileValidation() {
        LOG.info("Validating routes.txt file structure and content");
        
        assertTrue(gtfsData.containsKey("routes.txt"), "routes.txt must be present");
        List<Row> routesData = gtfsData.get("routes.txt");
        assertFalse(routesData.isEmpty(), "routes.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("routes.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("route_id"), "routes.txt must have route_id column");
        assertTrue(headerList.contains("route_short_name") || headerList.contains("route_long_name"), 
                  "routes.txt must have route_short_name or route_long_name");
        assertTrue(headerList.contains("route_type"), "routes.txt must have route_type column");
        
        // Analyze route types
        Map<String, Integer> routeTypeCounts = new HashMap<>();
        Set<String> routeIds = new HashSet<>();
        
        for (Row route : routesData) {
            String routeId = (String) route.getField(getColumnIndex("routes.txt", "route_id"));
            String routeType = (String) route.getField(getColumnIndex("routes.txt", "route_type"));
            
            assertNotNull(routeId, "Route ID must not be null");
            assertNotNull(routeType, "Route type must not be null");
            
            routeIds.add(routeId);
            routeTypeCounts.put(routeType, routeTypeCounts.getOrDefault(routeType, 0) + 1);
        }
        
        // Validate route ID uniqueness
        assertEquals(routeIds.size(), routesData.size(), "Route IDs must be unique");
        
        LOG.info("✅ routes.txt validation passed");
        LOG.info("   Total routes: {}", routesData.size());
        LOG.info("   Unique route IDs: {}", routeIds.size());
        LOG.info("   Route types: {}", routeTypeCounts);
    }
    
    @Test
    @Order(5)
    void testStopsFileValidation() {
        LOG.info("Validating stops.txt file structure and content");
        
        assertTrue(gtfsData.containsKey("stops.txt"), "stops.txt must be present");
        List<Row> stopsData = gtfsData.get("stops.txt");
        assertFalse(stopsData.isEmpty(), "stops.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("stops.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("stop_id"), "stops.txt must have stop_id column");
        assertTrue(headerList.contains("stop_name"), "stops.txt must have stop_name column");
        assertTrue(headerList.contains("stop_lat"), "stops.txt must have stop_lat column");
        assertTrue(headerList.contains("stop_lon"), "stops.txt must have stop_lon column");
        
        // Analyze stops
        Set<String> stopIds = new HashSet<>();
        Map<String, Integer> locationTypes = new HashMap<>();
        int validCoordinates = 0;
        
        for (Row stop : stopsData) {
            String stopId = (String) stop.getField(getColumnIndex("stops.txt", "stop_id"));
            String stopLat = (String) stop.getField(getColumnIndex("stops.txt", "stop_lat"));
            String stopLon = (String) stop.getField(getColumnIndex("stops.txt", "stop_lon"));
            
            assertNotNull(stopId, "Stop ID must not be null");
            stopIds.add(stopId);
            
            // Validate coordinates if present
            if (stopLat != null && stopLon != null && !stopLat.isEmpty() && !stopLon.isEmpty()) {
                try {
                    double lat = Double.parseDouble(stopLat);
                    double lon = Double.parseDouble(stopLon);
                    
                    // Denver metro area coordinates validation (expanded for RTD service area)
                    assertTrue(lat >= 38.5 && lat <= 41.0, 
                              "Latitude should be in Denver metro area range: " + lat);
                    assertTrue(lon >= -106.0 && lon <= -103.5, 
                              "Longitude should be in Denver metro area range: " + lon);
                    validCoordinates++;
                } catch (NumberFormatException e) {
                    // Skip invalid coordinates but log
                    LOG.debug("Invalid coordinates for stop {}: {}, {}", stopId, stopLat, stopLon);
                }
            }
            
            // Count location types
            if (headerList.contains("location_type")) {
                String locationType = (String) stop.getField(getColumnIndex("stops.txt", "location_type"));
                locationTypes.put(locationType != null ? locationType : "0", 
                                locationTypes.getOrDefault(locationType != null ? locationType : "0", 0) + 1);
            }
        }
        
        // Validate stop ID uniqueness
        assertEquals(stopIds.size(), stopsData.size(), "Stop IDs must be unique");
        
        LOG.info("✅ stops.txt validation passed");
        LOG.info("   Total stops: {}", stopsData.size());
        LOG.info("   Unique stop IDs: {}", stopIds.size());
        LOG.info("   Valid coordinates: {}", validCoordinates);
        LOG.info("   Location types: {}", locationTypes);
    }
    
    @Test
    @Order(6)
    void testTripsFileValidation() {
        LOG.info("Validating trips.txt file structure and content");
        
        assertTrue(gtfsData.containsKey("trips.txt"), "trips.txt must be present");
        List<Row> tripsData = gtfsData.get("trips.txt");
        assertFalse(tripsData.isEmpty(), "trips.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("trips.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("route_id"), "trips.txt must have route_id column");
        assertTrue(headerList.contains("service_id"), "trips.txt must have service_id column");
        assertTrue(headerList.contains("trip_id"), "trips.txt must have trip_id column");
        
        // Analyze trips
        Set<String> tripIds = new HashSet<>();
        Set<String> routeIds = new HashSet<>();
        Set<String> serviceIds = new HashSet<>();
        
        for (Row trip : tripsData) {
            String tripId = (String) trip.getField(getColumnIndex("trips.txt", "trip_id"));
            String routeId = (String) trip.getField(getColumnIndex("trips.txt", "route_id"));
            String serviceId = (String) trip.getField(getColumnIndex("trips.txt", "service_id"));
            
            assertNotNull(tripId, "Trip ID must not be null");
            assertNotNull(routeId, "Route ID must not be null");
            assertNotNull(serviceId, "Service ID must not be null");
            
            tripIds.add(tripId);
            routeIds.add(routeId);
            serviceIds.add(serviceId);
        }
        
        // Validate trip ID uniqueness
        assertEquals(tripIds.size(), tripsData.size(), "Trip IDs must be unique");
        
        LOG.info("✅ trips.txt validation passed");
        LOG.info("   Total trips: {}", tripsData.size());
        LOG.info("   Unique trip IDs: {}", tripIds.size());
        LOG.info("   Referenced routes: {}", routeIds.size());
        LOG.info("   Service patterns: {}", serviceIds.size());
    }
    
    @Test
    @Order(7)
    void testStopTimesFileValidation() {
        LOG.info("Validating stop_times.txt file structure and content");
        
        assertTrue(gtfsData.containsKey("stop_times.txt"), "stop_times.txt must be present");
        List<Row> stopTimesData = gtfsData.get("stop_times.txt");
        assertFalse(stopTimesData.isEmpty(), "stop_times.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("stop_times.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("trip_id"), "stop_times.txt must have trip_id column");
        assertTrue(headerList.contains("arrival_time"), "stop_times.txt must have arrival_time column");
        assertTrue(headerList.contains("departure_time"), "stop_times.txt must have departure_time column");
        assertTrue(headerList.contains("stop_id"), "stop_times.txt must have stop_id column");
        assertTrue(headerList.contains("stop_sequence"), "stop_times.txt must have stop_sequence column");
        
        // Sample validation (first 1000 records to avoid performance issues)
        int sampleSize = Math.min(1000, stopTimesData.size());
        Set<String> referencedTrips = new HashSet<>();
        Set<String> referencedStops = new HashSet<>();
        int validTimes = 0;
        
        for (int i = 0; i < sampleSize; i++) {
            Row stopTime = stopTimesData.get(i);
            
            String tripId = (String) stopTime.getField(getColumnIndex("stop_times.txt", "trip_id"));
            String stopId = (String) stopTime.getField(getColumnIndex("stop_times.txt", "stop_id"));
            String arrivalTime = (String) stopTime.getField(getColumnIndex("stop_times.txt", "arrival_time"));
            String departureTime = (String) stopTime.getField(getColumnIndex("stop_times.txt", "departure_time"));
            
            assertNotNull(tripId, "Trip ID must not be null");
            assertNotNull(stopId, "Stop ID must not be null");
            
            referencedTrips.add(tripId);
            referencedStops.add(stopId);
            
            // Validate time format (HH:MM:SS)
            if (arrivalTime != null && !arrivalTime.isEmpty()) {
                if (arrivalTime.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
                    validTimes++;
                }
            }
        }
        
        LOG.info("✅ stop_times.txt validation passed (sample of {} records)", sampleSize);
        LOG.info("   Total stop times: {}", stopTimesData.size());
        LOG.info("   Referenced trips (sample): {}", referencedTrips.size());
        LOG.info("   Referenced stops (sample): {}", referencedStops.size());
        LOG.info("   Valid time formats (sample): {}", validTimes);
    }
    
    @Test
    @Order(8)
    void testCalendarFileValidation() {
        LOG.info("Validating calendar.txt file structure and content");
        
        if (!gtfsData.containsKey("calendar.txt")) {
            LOG.warn("calendar.txt not present - checking calendar_dates.txt");
            assertTrue(gtfsData.containsKey("calendar_dates.txt"), 
                      "Either calendar.txt or calendar_dates.txt must be present");
            return;
        }
        
        List<Row> calendarData = gtfsData.get("calendar.txt");
        assertFalse(calendarData.isEmpty(), "calendar.txt must not be empty");
        
        // Validate headers
        String[] headers = fileHeaders.get("calendar.txt");
        List<String> headerList = Arrays.asList(headers);
        
        assertTrue(headerList.contains("service_id"), "calendar.txt must have service_id column");
        assertTrue(headerList.contains("start_date"), "calendar.txt must have start_date column");
        assertTrue(headerList.contains("end_date"), "calendar.txt must have end_date column");
        
        // Check for Aug 25, 2025 service
        Set<String> serviceIds = new HashSet<>();
        int aug25Services = 0;
        
        for (Row calendar : calendarData) {
            String serviceId = (String) calendar.getField(getColumnIndex("calendar.txt", "service_id"));
            String startDate = (String) calendar.getField(getColumnIndex("calendar.txt", "start_date"));
            String endDate = (String) calendar.getField(getColumnIndex("calendar.txt", "end_date"));
            
            assertNotNull(serviceId, "Service ID must not be null");
            serviceIds.add(serviceId);
            
            // Check if service covers Aug 25, 2025
            if (startDate != null && endDate != null) {
                try {
                    int start = Integer.parseInt(startDate);
                    int end = Integer.parseInt(endDate);
                    int target = Integer.parseInt(AUG_25_TARGET_DATE);
                    
                    if (start <= target && target <= end) {
                        aug25Services++;
                    }
                } catch (NumberFormatException e) {
                    LOG.debug("Invalid date format for service {}: {} - {}", serviceId, startDate, endDate);
                }
            }
        }
        
        LOG.info("✅ calendar.txt validation passed");
        LOG.info("   Service patterns: {}", serviceIds.size());
        LOG.info("   Services covering Aug 25, 2025: {}", aug25Services);
        
        if (aug25Services == 0) {
            LOG.warn("⚠️ No services found covering Aug 25, 2025 - check calendar_dates.txt");
        }
    }
    
    @Test
    @Order(9)
    void testAug25RunboardChanges() {
        LOG.info("Analyzing changes for August 25, 2025 runboard");
        
        // Check calendar_dates.txt for Aug 25 specific changes
        if (gtfsData.containsKey("calendar_dates.txt")) {
            List<Row> calendarDatesData = gtfsData.get("calendar_dates.txt");
            String[] headers = fileHeaders.get("calendar_dates.txt");
            
            int aug25Additions = 0;
            int aug25Removals = 0;
            Set<String> affectedServices = new HashSet<>();
            
            for (Row calendarDate : calendarDatesData) {
                String date = (String) calendarDate.getField(getColumnIndex("calendar_dates.txt", "date"));
                String exceptionType = (String) calendarDate.getField(getColumnIndex("calendar_dates.txt", "exception_type"));
                String serviceId = (String) calendarDate.getField(getColumnIndex("calendar_dates.txt", "service_id"));
                
                if (AUG_25_TARGET_DATE.equals(date)) {
                    affectedServices.add(serviceId);
                    if ("1".equals(exceptionType)) {
                        aug25Additions++;
                    } else if ("2".equals(exceptionType)) {
                        aug25Removals++;
                    }
                }
            }
            
            LOG.info("📅 Aug 25, 2025 runboard analysis:");
            LOG.info("   Service additions: {}", aug25Additions);
            LOG.info("   Service removals: {}", aug25Removals);
            LOG.info("   Affected services: {}", affectedServices);
            
            if (!affectedServices.isEmpty()) {
                LOG.info("🔄 Runboard changes detected for Aug 25, 2025");
                for (String serviceId : affectedServices) {
                    LOG.info("   Modified service: {}", serviceId);
                }
            } else {
                LOG.info("📊 No specific runboard changes found for Aug 25, 2025");
            }
        }
        
        // Additional analysis - check for recent updates in feed_info
        if (gtfsData.containsKey("feed_info.txt")) {
            List<Row> feedInfoData = gtfsData.get("feed_info.txt");
            if (!feedInfoData.isEmpty()) {
                Row feedInfo = feedInfoData.get(0);
                String[] headers = fileHeaders.get("feed_info.txt");
                
                if (Arrays.asList(headers).contains("feed_version")) {
                    String feedVersion = (String) feedInfo.getField(getColumnIndex("feed_info.txt", "feed_version"));
                    LOG.info("📋 Feed version: {}", feedVersion);
                }
                
                if (Arrays.asList(headers).contains("feed_start_date")) {
                    String feedStartDate = (String) feedInfo.getField(getColumnIndex("feed_info.txt", "feed_start_date"));
                    LOG.info("📋 Feed start date: {}", feedStartDate);
                }
                
                if (Arrays.asList(headers).contains("feed_end_date")) {
                    String feedEndDate = (String) feedInfo.getField(getColumnIndex("feed_info.txt", "feed_end_date"));
                    LOG.info("📋 Feed end date: {}", feedEndDate);
                }
            }
        }
        
        LOG.info("✅ Aug 25 runboard analysis completed");
    }
    
    @Test
    @Order(10)
    void testGTFSDataIntegrity() {
        LOG.info("Testing GTFS data integrity and cross-references");
        
        // Collect all route IDs from routes.txt
        Set<String> routeIds = new HashSet<>();
        if (gtfsData.containsKey("routes.txt")) {
            for (Row route : gtfsData.get("routes.txt")) {
                String routeId = (String) route.getField(getColumnIndex("routes.txt", "route_id"));
                if (routeId != null) routeIds.add(routeId);
            }
        }
        
        // Collect all stop IDs from stops.txt
        Set<String> stopIds = new HashSet<>();
        if (gtfsData.containsKey("stops.txt")) {
            for (Row stop : gtfsData.get("stops.txt")) {
                String stopId = (String) stop.getField(getColumnIndex("stops.txt", "stop_id"));
                if (stopId != null) stopIds.add(stopId);
            }
        }
        
        // Check route references in trips.txt
        Set<String> referencedRoutes = new HashSet<>();
        Set<String> tripIds = new HashSet<>();
        if (gtfsData.containsKey("trips.txt")) {
            for (Row trip : gtfsData.get("trips.txt")) {
                String routeId = (String) trip.getField(getColumnIndex("trips.txt", "route_id"));
                String tripId = (String) trip.getField(getColumnIndex("trips.txt", "trip_id"));
                
                if (routeId != null) {
                    referencedRoutes.add(routeId);
                    assertTrue(routeIds.contains(routeId), 
                              "Trip references non-existent route: " + routeId);
                }
                if (tripId != null) tripIds.add(tripId);
            }
        }
        
        // Check stop and trip references in stop_times.txt (sample)
        Set<String> referencedStops = new HashSet<>();
        Set<String> referencedTrips = new HashSet<>();
        if (gtfsData.containsKey("stop_times.txt")) {
            List<Row> stopTimesData = gtfsData.get("stop_times.txt");
            int sampleSize = Math.min(1000, stopTimesData.size());
            
            for (int i = 0; i < sampleSize; i++) {
                Row stopTime = stopTimesData.get(i);
                String stopId = (String) stopTime.getField(getColumnIndex("stop_times.txt", "stop_id"));
                String tripId = (String) stopTime.getField(getColumnIndex("stop_times.txt", "trip_id"));
                
                if (stopId != null) {
                    referencedStops.add(stopId);
                    assertTrue(stopIds.contains(stopId), 
                              "Stop time references non-existent stop: " + stopId);
                }
                if (tripId != null) {
                    referencedTrips.add(tripId);
                    assertTrue(tripIds.contains(tripId), 
                              "Stop time references non-existent trip: " + tripId);
                }
            }
        }
        
        LOG.info("✅ GTFS data integrity validation passed");
        LOG.info("   Total routes: {}", routeIds.size());
        LOG.info("   Referenced routes: {}", referencedRoutes.size());
        LOG.info("   Total stops: {}", stopIds.size());
        LOG.info("   Referenced stops (sample): {}", referencedStops.size());
        LOG.info("   Total trips: {}", tripIds.size());
        LOG.info("   Referenced trips (sample): {}", referencedTrips.size());
    }
    
    // Helper methods
    
    private static void downloadAllGTFSFiles() throws IOException {
        LOG.info("Downloading all GTFS files from RTD...");
        
        String currentUrl = RTD_GTFS_URL;
        HttpURLConnection connection = null;
        
        // Handle redirects
        for (int redirectCount = 0; redirectCount < 5; redirectCount++) {
            connection = (HttpURLConnection) new URL(currentUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setInstanceFollowRedirects(false);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                break;
            } else if (responseCode == 301 || responseCode == 302 || responseCode == 307 || responseCode == 308) {
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    if (location.startsWith("/")) {
                        URL baseUrl = new URL(currentUrl);
                        currentUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + location;
                    } else {
                        currentUrl = location;
                    }
                    connection.disconnect();
                    continue;
                } else {
                    throw new IOException("Redirect without Location header");
                }
            } else {
                throw new IOException("HTTP error code: " + responseCode);
            }
        }
        
        if (connection == null) {
            throw new IOException("Too many redirects");
        }
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (fileName.endsWith(".txt")) {
                    LOG.info("Processing GTFS file: {}", fileName);
                    
                    List<Row> fileData = parseCSVFromZip(zipStream, fileName);
                    gtfsData.put(fileName, fileData);
                    
                    LOG.info("✅ Loaded {} with {} records", fileName, fileData.size());
                }
                zipStream.closeEntry();
            }
        }
    }
    
    private static List<Row> parseCSVFromZip(ZipInputStream zipStream, String fileName) throws IOException {
        List<Row> rows = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return rows;
        }
        
        String[] headers = headerLine.split(",");
        fileHeaders.put(fileName, headers);
        
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                Row row = parseCSVLine(line, headers.length);
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception e) {
                // Skip invalid lines
            }
        }
        
        return rows;
    }
    
    private static Row parseCSVLine(String line, int expectedColumns) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString().trim());
        
        // Pad with empty strings if needed
        while (fields.size() < expectedColumns) {
            fields.add("");
        }
        
        Row row = new Row(expectedColumns);
        for (int i = 0; i < expectedColumns; i++) {
            String value = i < fields.size() ? fields.get(i) : "";
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            row.setField(i, value.isEmpty() ? null : value);
        }
        
        return row;
    }
    
    private static int getColumnIndex(String fileName, String columnName) {
        String[] headers = fileHeaders.get(fileName);
        if (headers == null) return -1;
        
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(columnName)) {
                return i;
            }
        }
        return -1;
    }
}