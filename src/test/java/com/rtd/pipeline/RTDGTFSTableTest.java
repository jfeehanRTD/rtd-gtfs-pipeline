package com.rtd.pipeline;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for RTD GTFS Table API functionality
 * Validates table creation, data processing, and query execution
 */
public class RTDGTFSTableTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDGTFSTableTest.class);
    
    private static StreamExecutionEnvironment env;
    private static StreamTableEnvironment tableEnv;
    
    @BeforeAll
    static void setup() {
        LOG.info("Setting up RTD GTFS Table test environment");
        
        env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        tableEnv = StreamTableEnvironment.create(env);
        
        LOG.info("✅ Test environment configured");
    }
    
    @Test
    void testRTDGTFSDataDownload() {
        LOG.info("Testing RTD GTFS data download and processing");
        
        try {
            // This test verifies that we can connect to RTD's GTFS endpoint
            // and download valid ZIP data
            
            String url = "https://www.rtd-denver.com/files/gtfs/google_transit.zip";
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) 
                new java.net.URL(url).openConnection();
            
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true); // Follow redirects automatically
            
            int responseCode = connection.getResponseCode();
            
            // Handle redirects manually if needed
            if (responseCode == 308 || responseCode == 301 || responseCode == 302) {
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null) {
                    LOG.info("Following redirect to: {}", redirectUrl);
                    connection.disconnect();
                    
                    // Handle relative redirects by making them absolute
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = "https://www.rtd-denver.com" + redirectUrl;
                    }
                    
                    connection = (java.net.HttpURLConnection) new java.net.URL(redirectUrl).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(60000);
                    responseCode = connection.getResponseCode();
                }
            }
            
            assertEquals(200, responseCode, "RTD GTFS endpoint should be accessible (after following redirects)");
            
            String contentType = connection.getContentType();
            // Content type might be null or not specific, so check content length as primary validation
            if (contentType != null) {
                assertTrue(contentType.contains("zip") || contentType.contains("application/octet-stream") || 
                          contentType.contains("application/x-zip-compressed"),
                    "Response should be ZIP format, got: " + contentType);
            }
            
            long contentLength = connection.getContentLengthLong();
            // Some servers don't provide Content-Length header, so -1 is acceptable
            if (contentLength > 0) {
                assertTrue(contentLength > 100000, "GTFS ZIP should be substantial size (>100KB), got: " + contentLength);
            } else {
                LOG.info("Content-Length not provided by server (got {}), skipping size check", contentLength);
            }
            
            LOG.info("✅ RTD GTFS endpoint test passed");
            LOG.info("   Response Code: {}", responseCode);
            LOG.info("   Content Type: {}", contentType);
            LOG.info("   Content Length: {} bytes", contentLength);
            
            connection.disconnect();
            
        } catch (Exception e) {
            fail("Failed to connect to RTD GTFS endpoint: " + e.getMessage());
        }
    }
    
    @Test
    void testGTFSTableCreation() {
        LOG.info("Testing GTFS table creation with mock data");
        
        try {
            // Create mock agency data
            java.util.List<Row> mockAgencyData = java.util.Arrays.asList(
                Row.of("RTD", "Regional Transportation District", 
                       "https://www.rtd-denver.com", "America/Denver", "en", "303-299-6000"),
                Row.of("RTD", "Regional Transportation District Test", 
                       "https://test.rtd-denver.com", "America/Denver", "en", "303-299-6001")
            );
            
            // Create table with proper schema
            org.apache.flink.table.api.Table agencyTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("agency_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_name", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_url", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_timezone", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_lang", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_phone", org.apache.flink.table.api.DataTypes.STRING())
                ),
                mockAgencyData
            );
            
            // Register table
            tableEnv.createTemporaryView("TEST_AGENCY", agencyTable);
            
            // Test query execution
            TableResult result = tableEnv.executeSql("SELECT agency_name, agency_phone FROM TEST_AGENCY");
            
            assertNotNull(result, "Query result should not be null");
            
            var iterator = result.collect();
            int count = 0;
            while (iterator.hasNext()) {
                Row row = iterator.next();
                assertNotNull(row, "Result row should not be null");
                assertTrue(row.getArity() >= 2, "Row should have at least 2 fields");
                count++;
            }
            
            assertEquals(2, count, "Should return 2 agency records");
            
            LOG.info("✅ GTFS table creation test passed");
            
        } catch (Exception e) {
            fail("GTFS table creation failed: " + e.getMessage());
        }
    }
    
    @Test
    void testGTFSRouteTableQueries() {
        LOG.info("Testing GTFS route table queries");
        
        try {
            // Create mock route data representing different RTD services
            java.util.List<Row> mockRouteData = java.util.Arrays.asList(
                Row.of("A", "RTD", "A", "Airport Line", "Rail service to DEN Airport", "1", "", "005DAA", "FFFFFF"),
                Row.of("FF1", "RTD", "FF1", "Flatiron Flyer", "BRT service to Boulder", "3", "", "00A651", "FFFFFF"),
                Row.of("15", "RTD", "15", "Colfax Avenue", "East-west bus service", "3", "", "FF6600", "FFFFFF"),
                Row.of("MALL", "RTD", "MALL", "16th Street Mall", "Downtown shuttle", "3", "", "800080", "FFFFFF")
            );
            
            org.apache.flink.table.api.Table routesTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("route_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_short_name", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_long_name", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_desc", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_type", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_url", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_color", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_text_color", org.apache.flink.table.api.DataTypes.STRING())
                ),
                mockRouteData
            );
            
            tableEnv.createTemporaryView("TEST_ROUTES", routesTable);
            
            // Test route type analysis
            TableResult typeResult = tableEnv.executeSql(
                "SELECT route_type, COUNT(*) as route_count FROM TEST_ROUTES GROUP BY route_type"
            );
            
            var iterator = typeResult.collect();
            int railCount = 0;
            int busCount = 0;
            
            while (iterator.hasNext()) {
                Row row = iterator.next();
                String routeType = (String) row.getField(0);
                Long count = (Long) row.getField(1);
                
                if ("1".equals(routeType)) {
                    railCount = count.intValue();
                } else if ("3".equals(routeType)) {
                    busCount = count.intValue();
                }
            }
            
            assertEquals(1, railCount, "Should have 1 rail route (A Line)");
            assertEquals(3, busCount, "Should have 3 bus routes");
            
            // Test route filtering
            TableResult railResult = tableEnv.executeSql(
                "SELECT route_short_name, route_long_name FROM TEST_ROUTES WHERE route_type = '1'"
            );
            
            var railIterator = railResult.collect();
            assertTrue(railIterator.hasNext(), "Should have rail routes");
            
            Row railRow = railIterator.next();
            assertEquals("A", railRow.getField(0), "Rail route should be A Line");
            
            LOG.info("✅ GTFS route table queries test passed");
            
        } catch (Exception e) {
            fail("GTFS route queries failed: " + e.getMessage());
        }
    }
    
    @Test
    void testGTFSStopTableQueries() {
        LOG.info("Testing GTFS stop table queries");
        
        try {
            // Create mock stop data representing RTD stations and stops
            java.util.List<Row> mockStopData = java.util.Arrays.asList(
                Row.of("UNION", "1", "Union Station", "Downtown Denver hub", "39.753576", "-104.999734", "", "", "1", "", "", "1"),
                Row.of("DEN", "2", "Denver International Airport", "Airport terminal", "39.849926", "-104.673737", "", "", "1", "", "", "1"),
                Row.of("16MALL_1", "3", "16th & California", "Mall bus stop", "39.747585", "-104.990739", "", "", "0", "UNION", "", "1"),
                Row.of("COLFAX_15", "4", "Colfax & 15th", "Regular bus stop", "39.739236", "-104.991065", "", "", "0", "", "", "0")
            );
            
            org.apache.flink.table.api.Table stopsTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("stop_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_code", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_name", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_desc", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_lat", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_lon", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("zone_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_url", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("location_type", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("parent_station", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("stop_timezone", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("wheelchair_boarding", org.apache.flink.table.api.DataTypes.STRING())
                ),
                mockStopData
            );
            
            tableEnv.createTemporaryView("TEST_STOPS", stopsTable);
            
            // Test station vs stop analysis
            TableResult typeResult = tableEnv.executeSql(
                "SELECT location_type, COUNT(stop_id) as stop_count FROM TEST_STOPS GROUP BY location_type"
            );
            
            var iterator = typeResult.collect();
            int stationCount = 0;
            int stopCount = 0;
            
            while (iterator.hasNext()) {
                Row row = iterator.next();
                String locationType = (String) row.getField(0);
                Long count = (Long) row.getField(1);
                
                if ("1".equals(locationType)) {
                    stationCount = count.intValue();
                } else if ("0".equals(locationType)) {
                    stopCount = count.intValue();
                }
            }
            
            assertEquals(2, stationCount, "Should have 2 stations (Union, Airport)");
            assertEquals(2, stopCount, "Should have 2 regular stops");
            
            // Test name search
            TableResult searchResult = tableEnv.executeSql(
                "SELECT stop_name FROM TEST_STOPS WHERE stop_name LIKE '%Union%'"
            );
            
            var searchIterator = searchResult.collect();
            assertTrue(searchIterator.hasNext(), "Should find Union Station");
            
            Row unionRow = searchIterator.next();
            assertEquals("Union Station", unionRow.getField(0));
            
            LOG.info("✅ GTFS stop table queries test passed");
            
        } catch (Exception e) {
            fail("GTFS stop queries failed: " + e.getMessage());
        }
    }
    
    @Test
    void testGTFSCalendarAnalysis() {
        LOG.info("Testing GTFS calendar analysis");
        
        try {
            // Create mock calendar data representing RTD service patterns
            java.util.List<Row> mockCalendarData = java.util.Arrays.asList(
                Row.of("WEEKDAY", "1", "1", "1", "1", "1", "0", "0", "20241201", "20241231"),
                Row.of("WEEKEND", "0", "0", "0", "0", "0", "1", "1", "20241201", "20241231"),
                Row.of("SPECIAL", "1", "1", "1", "1", "1", "1", "1", "20241215", "20241225")
            );
            
            org.apache.flink.table.api.Table calendarTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("service_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("monday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("tuesday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("wednesday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("thursday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("friday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("saturday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("sunday", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("start_date", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("end_date", org.apache.flink.table.api.DataTypes.STRING())
                ),
                mockCalendarData
            );
            
            tableEnv.createTemporaryView("TEST_CALENDAR", calendarTable);
            
            // Test service pattern analysis
            TableResult weekdayResult = tableEnv.executeSql(
                "SELECT service_id FROM TEST_CALENDAR WHERE `monday` = '1' AND `saturday` = '0'"
            );
            
            var weekdayIterator = weekdayResult.collect();
            assertTrue(weekdayIterator.hasNext(), "Should find weekday service");
            
            Row weekdayRow = weekdayIterator.next();
            assertEquals("WEEKDAY", weekdayRow.getField(0));
            
            // Test weekend service
            TableResult weekendResult = tableEnv.executeSql(
                "SELECT service_id FROM TEST_CALENDAR WHERE `saturday` = '1' AND `sunday` = '1'"
            );
            
            var weekendIterator = weekendResult.collect();
            int weekendServiceCount = 0;
            while (weekendIterator.hasNext()) {
                weekendIterator.next();
                weekendServiceCount++;
            }
            
            assertEquals(2, weekendServiceCount, "Should have 2 services running on weekends (WEEKEND, SPECIAL)");
            
            LOG.info("✅ GTFS calendar analysis test passed");
            
        } catch (Exception e) {
            fail("GTFS calendar analysis failed: " + e.getMessage());
        }
    }
    
    @Test
    void testComplexGTFSQueries() {
        LOG.info("Testing complex GTFS join queries");
        
        try {
            // Clean up any existing views to avoid conflicts
            try {
                tableEnv.executeSql("DROP VIEW IF EXISTS COMPLEX_ROUTES");
                tableEnv.executeSql("DROP VIEW IF EXISTS COMPLEX_TRIPS");
            } catch (Exception e) {
                // Ignore if views don't exist
            }
            // Create related mock data for testing joins
            
            // Routes
            java.util.List<Row> routeData = java.util.Arrays.asList(
                Row.of("A", "RTD", "A", "Airport Line"),
                Row.of("15", "RTD", "15", "Colfax")
            );
            
            org.apache.flink.table.api.Table routesTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("route_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("agency_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_short_name", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("route_long_name", org.apache.flink.table.api.DataTypes.STRING())
                ),
                routeData
            );
            
            // Trips
            java.util.List<Row> tripData = java.util.Arrays.asList(
                Row.of("A", "WEEKDAY", "A_001", "Airport"),
                Row.of("A", "WEEKDAY", "A_002", "Airport"),
                Row.of("15", "WEEKDAY", "15_001", "Colfax East"),
                Row.of("15", "WEEKEND", "15_002", "Colfax West")
            );
            
            org.apache.flink.table.api.Table tripsTable = tableEnv.fromValues(
                org.apache.flink.table.api.DataTypes.ROW(
                    org.apache.flink.table.api.DataTypes.FIELD("route_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("service_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("trip_id", org.apache.flink.table.api.DataTypes.STRING()),
                    org.apache.flink.table.api.DataTypes.FIELD("trip_headsign", org.apache.flink.table.api.DataTypes.STRING())
                ),
                tripData
            );
            
            tableEnv.createTemporaryView("COMPLEX_ROUTES", routesTable);
            tableEnv.createTemporaryView("COMPLEX_TRIPS", tripsTable);
            
            // Test route-trip join query using INNER JOIN to ensure matches
            TableResult joinResult = tableEnv.executeSql(
                "SELECT r.route_short_name, r.route_long_name, COUNT(t.trip_id) as trip_count " +
                "FROM COMPLEX_ROUTES r " +
                "JOIN COMPLEX_TRIPS t ON r.route_id = t.route_id " +
                "GROUP BY r.route_short_name, r.route_long_name"
            );
            
            var joinIterator = joinResult.collect();
            boolean foundAirportLine = false;
            boolean foundColfaxLine = false;
            
            while (joinIterator.hasNext()) {
                Row row = joinIterator.next();
                String routeName = (String) row.getField(0);
                String routeLongName = (String) row.getField(1);
                Long tripCount = (Long) row.getField(2);
                
                LOG.info("Query result: route={}, long_name={}, trip_count={}", routeName, routeLongName, tripCount);
                
                if ("A".equals(routeName)) {
                    assertTrue(tripCount >= 1L, "Airport line should have at least 1 trip (got " + tripCount + ")");
                    foundAirportLine = true;
                } else if ("15".equals(routeName)) {
                    assertTrue(tripCount >= 1L, "Colfax line should have at least 1 trip (got " + tripCount + ")");
                    foundColfaxLine = true;
                }
            }
            
            assertTrue(foundAirportLine, "Should find Airport line in results");
            assertTrue(foundColfaxLine, "Should find Colfax line in results");
            
            LOG.info("✅ Complex GTFS queries test passed");
            
        } catch (Exception e) {
            fail("Complex GTFS queries failed: " + e.getMessage());
        }
    }
}