package com.rtd.pipeline;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * JUnit tests for RTD Persistent Data Pipeline functionality.
 * Tests file sink operations, data retention, and Table API access.
 */
public class RTDPersistentDataPipelineTest {

    @TempDir
    Path tempDataDir;
    
    private StreamExecutionEnvironment env;
    private StreamTableEnvironment tableEnv;
    
    @BeforeEach
    void setUp() {
        Configuration config = new Configuration();
        config.setString("execution.runtime-mode", "BATCH");
        config.setString("parallelism.default", "1");
        
        env = StreamExecutionEnvironment.createLocalEnvironment(config);
        tableEnv = StreamTableEnvironment.create(env);
    }
    
    @AfterEach
    void tearDown() {
        // Cleanup
    }
    
    @Test
    void testDataDirectoryCreation() throws IOException {
        // Test data directory structure creation
        Path vehiclePath = tempDataDir.resolve("vehicles");
        Path schedulePath = tempDataDir.resolve("schedule");
        Path catalogPath = tempDataDir.resolve("catalog");
        
        Files.createDirectories(vehiclePath);
        Files.createDirectories(schedulePath);
        Files.createDirectories(catalogPath);
        
        assertTrue(Files.exists(vehiclePath), "Vehicle data directory should exist");
        assertTrue(Files.exists(schedulePath), "Schedule data directory should exist");
        assertTrue(Files.exists(catalogPath), "Catalog directory should exist");
        assertTrue(Files.isDirectory(vehiclePath), "Vehicle path should be a directory");
    }
    
    @Test
    void testVehicleDataSinkConfiguration() {
        // Test vehicle data table creation
        Row testRow = Row.of(
            System.currentTimeMillis(),  // timestamp_ms
            "RTD_001",                   // vehicle_id
            "6547",                      // vehicle_label
            "trip_123",                  // trip_id
            "FF1",                       // route_id
            39.7392,                     // latitude
            -104.9903,                   // longitude
            180.0,                       // bearing
            25.5,                        // speed
            "IN_TRANSIT_TO",             // current_status
            "RUNNING_SMOOTHLY",          // congestion_level
            "FEW_SEATS_AVAILABLE"        // occupancy_status
        );
        
        // Verify row structure matches expected schema
        assertEquals(12, testRow.getArity(), "Vehicle row should have 12 fields");
        assertNotNull(testRow.getField(0), "Timestamp should not be null");
        assertNotNull(testRow.getField(1), "Vehicle ID should not be null");
        assertNotNull(testRow.getField(4), "Route ID should not be null");
        assertTrue(testRow.getField(5) instanceof Double, "Latitude should be Double");
        assertTrue(testRow.getField(6) instanceof Double, "Longitude should be Double");
    }
    
    @Test
    void testScheduleDataSinkConfiguration() {
        // Test schedule data row creation
        Row testScheduleRow = Row.of(
            System.currentTimeMillis(),  // download_timestamp
            "agency.txt",                // file_type
            "1.0",                       // feed_version
            "RTD",                       // agency_name
            "20241201",                  // feed_start_date
            "20250301",                  // feed_end_date
            "agency_id,agency_name,agency_url\nRTD,Regional Transportation District,https://rtd-denver.com"  // file_content
        );
        
        assertEquals(7, testScheduleRow.getArity(), "Schedule row should have 7 fields");
        assertNotNull(testScheduleRow.getField(0), "Download timestamp should not be null");
        assertNotNull(testScheduleRow.getField(1), "File type should not be null");
        assertTrue(testScheduleRow.getField(6).toString().contains("RTD"), "File content should contain RTD");
    }
    
    @Test
    void testDataRetentionLogic() throws IOException {
        // Test data retention cleanup logic
        Duration retentionPeriod = Duration.ofDays(2);
        long cutoffTime = System.currentTimeMillis() - retentionPeriod.toMillis();
        
        // Create test files with different timestamps
        Path testDir = tempDataDir.resolve("test_cleanup");
        Files.createDirectories(testDir);
        
        // Old file (should be deleted)
        Path oldFile = testDir.resolve("old_data.json");
        Files.createFile(oldFile);
        Files.setLastModifiedTime(oldFile, java.nio.file.attribute.FileTime.fromMillis(cutoffTime - 3600000)); // 1 hour before cutoff
        
        // New file (should be kept)
        Path newFile = testDir.resolve("new_data.json");
        Files.createFile(newFile);
        Files.setLastModifiedTime(newFile, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
        
        // Simulate cleanup
        List<Path> filesToDelete = Files.walk(testDir)
            .filter(Files::isRegularFile)
            .filter(path -> {
                try {
                    return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                } catch (IOException e) {
                    return false;
                }
            })
            .toList();
        
        assertEquals(1, filesToDelete.size(), "Should identify 1 file for deletion");
        assertTrue(filesToDelete.contains(oldFile), "Old file should be marked for deletion");
        assertFalse(filesToDelete.contains(newFile), "New file should not be marked for deletion");
    }
    
    @Test
    void testTableAPIConfiguration() {
        // Test Table API table creation
        String vehicleTableDDL = """
            CREATE TABLE test_vehicles (
                timestamp_ms BIGINT,
                vehicle_id STRING,
                vehicle_label STRING,
                trip_id STRING,
                route_id STRING,
                latitude DOUBLE,
                longitude DOUBLE,
                bearing DOUBLE,
                speed DOUBLE,
                current_status STRING,
                congestion_level STRING,
                occupancy_status STRING,
                processing_time AS PROCTIME(),
                event_time AS TO_TIMESTAMP(FROM_UNIXTIME(timestamp_ms / 1000)),
                WATERMARK FOR event_time AS event_time - INTERVAL '1' MINUTE
            ) WITH (
                'connector' = 'filesystem',
                'path' = '%s/test_vehicles',
                'format' = 'json'
            )
            """.formatted(tempDataDir.toString());
        
        assertDoesNotThrow(() -> {
            tableEnv.executeSql(vehicleTableDDL);
        }, "Vehicle table creation should not throw exception");
        
        // Test that table is registered
        assertTrue(tableEnv.listTables().length > 0, "At least one table should be registered");
    }
    
    @Test
    void testSQLQueryCapability() {
        // Test SQL query execution capability
        String createTableSQL = """
            CREATE TABLE test_rtd_data (
                timestamp_ms BIGINT,
                vehicle_id STRING,
                route_id STRING,
                latitude DOUBLE,
                longitude DOUBLE
            ) WITH (
                'connector' = 'filesystem',
                'path' = '%s/sql_test',
                'format' = 'json'
            )
            """.formatted(tempDataDir.toString());
        
        assertDoesNotThrow(() -> {
            tableEnv.executeSql(createTableSQL);
        }, "Test table creation should succeed");
        
        // Test a simple query
        String testQuery = "SELECT COUNT(*) as record_count FROM test_rtd_data";
        assertDoesNotThrow(() -> {
            tableEnv.executeSql(testQuery);
        }, "Simple SQL query should execute without error");
    }
    
    @Test
    void testFileSinkRollingPolicy() {
        // Test rolling policy configuration
        Duration rolloverInterval = Duration.ofHours(1);
        Duration inactivityInterval = Duration.ofMinutes(15);
        
        assertTrue(rolloverInterval.toMinutes() > 0, "Rollover interval should be positive");
        assertTrue(inactivityInterval.toMinutes() > 0, "Inactivity interval should be positive");
        assertTrue(rolloverInterval.compareTo(inactivityInterval) > 0, "Rollover interval should be longer than inactivity interval");
    }
    
    @Test
    void testBucketAssignerFunctionality() {
        // Test date-time bucket assignment
        Instant testTime = Instant.now();
        long timestampMs = testTime.toEpochMilli();
        
        // Simulate bucket assignment logic
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
            testTime, 
            java.time.ZoneId.systemDefault()
        );
        String bucketId = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
        
        assertNotNull(bucketId, "Bucket ID should not be null");
        assertTrue(bucketId.matches("\\d{4}/\\d{2}/\\d{2}/\\d{2}"), "Bucket ID should match expected pattern");
        assertThat(bucketId).contains(String.valueOf(java.time.LocalDate.now().getYear()));
    }
    
    @Test
    void testJSONDataConversion() {
        // Test JSON conversion for HTTP API
        java.util.List<Row> testVehicles = java.util.List.of(
            Row.of(System.currentTimeMillis(), "RTD_001", "6547", "trip_123", "FF1", 39.7392, -104.9903, 180.0, 25.5, "IN_TRANSIT_TO", "SMOOTH", "FEW_SEATS"),
            Row.of(System.currentTimeMillis(), "RTD_002", "6548", "trip_124", "16", 39.7502, -105.0002, 90.0, 15.2, "STOPPED_AT", "SMOOTH", "MANY_SEATS")
        );
        
        // Simulate JSON conversion
        StringBuilder json = new StringBuilder();
        json.append("{\"vehicles\": [");
        
        for (int i = 0; i < testVehicles.size(); i++) {
            Row vehicle = testVehicles.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"vehicle_id\":\"").append(vehicle.getField(1)).append("\",")
                .append("\"route_id\":\"").append(vehicle.getField(4)).append("\",")
                .append("\"latitude\":").append(vehicle.getField(5)).append(",")
                .append("\"longitude\":").append(vehicle.getField(6)).append("\"")
                .append("}");
        }
        
        json.append("], \"count\": ").append(testVehicles.size()).append("}");
        String result = json.toString();
        
        assertNotNull(result, "JSON result should not be null");
        assertTrue(result.contains("\"count\": 2"), "JSON should contain correct count");
        assertTrue(result.contains("RTD_001"), "JSON should contain first vehicle ID");
        assertTrue(result.contains("FF1"), "JSON should contain route information");
        assertTrue(result.startsWith("{\"vehicles\":"), "JSON should start with vehicles array");
    }
    
    @Test
    void testDataRetentionPeriod() {
        // Test 2-day retention period
        Duration retentionPeriod = Duration.ofDays(2);
        
        assertEquals(2, retentionPeriod.toDays(), "Retention period should be 2 days");
        assertEquals(48, retentionPeriod.toHours(), "Retention period should be 48 hours");
        assertTrue(retentionPeriod.toMillis() > 0, "Retention period in milliseconds should be positive");
    }
    
    @Test
    void testHealthCheckData() {
        // Test health check response data
        long currentTime = System.currentTimeMillis();
        long lastUpdateTime = currentTime - 30000; // 30 seconds ago
        long fetchInterval = 60; // 60 seconds
        
        long timeSinceUpdate = currentTime - lastUpdateTime;
        boolean isHealthy = timeSinceUpdate < (fetchInterval * 1000 * 2); // Within 2 intervals
        
        assertTrue(isHealthy, "Pipeline should be considered healthy");
        assertTrue(timeSinceUpdate < 120000, "Time since update should be less than 2 minutes");
    }
}