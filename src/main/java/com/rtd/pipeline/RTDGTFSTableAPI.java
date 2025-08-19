package com.rtd.pipeline;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * RTD GTFS Table API Implementation
 * Advanced Flink Table API processor for RTD static GTFS data
 * Provides comprehensive transit network analysis with proper schemas
 */
public class RTDGTFSTableAPI {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDGTFSTableAPI.class);
    
    private static final String RTD_GTFS_STATIC_URL = "https://www.rtd-denver.com/files/gtfs/google_transit.zip";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD GTFS Table API Processor Starting ===");
        LOG.info("Creating comprehensive Flink tables from RTD GTFS static data");
        LOG.info("Data source: {}", RTD_GTFS_STATIC_URL);
        
        // Create Flink environment with optimized settings
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        
        try {
            // Download and create structured tables
            createRTDGTFSTables(tableEnv);
            
            // Execute comprehensive analysis queries
            executeRTDAnalysisQueries(tableEnv);
            
            LOG.info("✅ RTD GTFS Table API processing completed successfully");
            
        } catch (Exception e) {
            LOG.error("❌ Error in RTD GTFS Table API processing: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Downloads RTD GTFS data and creates properly typed Flink tables
     */
    private static void createRTDGTFSTables(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating RTD GTFS tables with proper schemas...");
        
        // Create agency table
        createAgencyTable(tableEnv);
        
        // Create routes table
        createRoutesTable(tableEnv);
        
        // Create stops table
        createStopsTable(tableEnv);
        
        // Create trips table
        createTripsTable(tableEnv);
        
        // Create stop_times table (sample - this can be very large)
        createStopTimesTable(tableEnv);
        
        // Create calendar table
        createCalendarTable(tableEnv);
        
        // Create shapes table (sample)
        createShapesTable(tableEnv);
        
        LOG.info("✅ All RTD GTFS tables created successfully");
    }
    
    /**
     * Creates Agency table with RTD agency information
     */
    private static void createAgencyTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating AGENCY table...");
        
        List<Row> agencyData = downloadGTFSFile("agency.txt");
        
        if (agencyData.isEmpty()) {
            LOG.warn("No agency data found");
            return;
        }
        
        // Create table with proper schema
        Table agencyTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("agency_id", DataTypes.STRING()),
                DataTypes.FIELD("agency_name", DataTypes.STRING()),
                DataTypes.FIELD("agency_url", DataTypes.STRING()),
                DataTypes.FIELD("agency_timezone", DataTypes.STRING()),
                DataTypes.FIELD("agency_lang", DataTypes.STRING()),
                DataTypes.FIELD("agency_phone", DataTypes.STRING())
            ),
            agencyData
        );
        
        tableEnv.createTemporaryView("RTD_AGENCY", agencyTable);
        LOG.info("✅ Created RTD_AGENCY table with {} records", agencyData.size());
    }
    
    /**
     * Creates Routes table with RTD route information
     */
    private static void createRoutesTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating ROUTES table...");
        
        List<Row> routesData = downloadGTFSFile("routes.txt");
        
        if (routesData.isEmpty()) {
            LOG.warn("No routes data found");
            return;
        }
        
        Table routesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("route_id", DataTypes.STRING()),
                DataTypes.FIELD("agency_id", DataTypes.STRING()),
                DataTypes.FIELD("route_short_name", DataTypes.STRING()),
                DataTypes.FIELD("route_long_name", DataTypes.STRING()),
                DataTypes.FIELD("route_desc", DataTypes.STRING()),
                DataTypes.FIELD("route_type", DataTypes.INT()),
                DataTypes.FIELD("route_url", DataTypes.STRING()),
                DataTypes.FIELD("route_color", DataTypes.STRING()),
                DataTypes.FIELD("route_text_color", DataTypes.STRING()),
                DataTypes.FIELD("route_sort_order", DataTypes.INT())
            ),
            routesData.subList(0, Math.min(routesData.size(), 1000)) // Limit for demo
        );
        
        tableEnv.createTemporaryView("RTD_ROUTES", routesTable);
        LOG.info("✅ Created RTD_ROUTES table with {} records", Math.min(routesData.size(), 1000));
    }
    
    /**
     * Creates Stops table with RTD stop information
     */
    private static void createStopsTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating STOPS table...");
        
        List<Row> stopsData = downloadGTFSFile("stops.txt");
        
        if (stopsData.isEmpty()) {
            LOG.warn("No stops data found");
            return;
        }
        
        Table stopsTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("stop_id", DataTypes.STRING()),
                DataTypes.FIELD("stop_code", DataTypes.STRING()),
                DataTypes.FIELD("stop_name", DataTypes.STRING()),
                DataTypes.FIELD("stop_desc", DataTypes.STRING()),
                DataTypes.FIELD("stop_lat", DataTypes.DOUBLE()),
                DataTypes.FIELD("stop_lon", DataTypes.DOUBLE()),
                DataTypes.FIELD("zone_id", DataTypes.STRING()),
                DataTypes.FIELD("stop_url", DataTypes.STRING()),
                DataTypes.FIELD("location_type", DataTypes.INT()),
                DataTypes.FIELD("parent_station", DataTypes.STRING()),
                DataTypes.FIELD("stop_timezone", DataTypes.STRING()),
                DataTypes.FIELD("wheelchair_boarding", DataTypes.INT())
            ),
            stopsData.subList(0, Math.min(stopsData.size(), 2000)) // Limit for demo
        );
        
        tableEnv.createTemporaryView("RTD_STOPS", stopsTable);
        LOG.info("✅ Created RTD_STOPS table with {} records", Math.min(stopsData.size(), 2000));
    }
    
    /**
     * Creates Trips table with RTD trip information
     */
    private static void createTripsTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating TRIPS table...");
        
        List<Row> tripsData = downloadGTFSFile("trips.txt");
        
        if (tripsData.isEmpty()) {
            LOG.warn("No trips data found");
            return;
        }
        
        Table tripsTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("route_id", DataTypes.STRING()),
                DataTypes.FIELD("service_id", DataTypes.STRING()),
                DataTypes.FIELD("trip_id", DataTypes.STRING()),
                DataTypes.FIELD("trip_headsign", DataTypes.STRING()),
                DataTypes.FIELD("trip_short_name", DataTypes.STRING()),
                DataTypes.FIELD("direction_id", DataTypes.INT()),
                DataTypes.FIELD("block_id", DataTypes.STRING()),
                DataTypes.FIELD("shape_id", DataTypes.STRING()),
                DataTypes.FIELD("wheelchair_accessible", DataTypes.INT()),
                DataTypes.FIELD("bikes_allowed", DataTypes.INT())
            ),
            tripsData.subList(0, Math.min(tripsData.size(), 5000)) // Limit for demo
        );
        
        tableEnv.createTemporaryView("RTD_TRIPS", tripsTable);
        LOG.info("✅ Created RTD_TRIPS table with {} records", Math.min(tripsData.size(), 5000));
    }
    
    /**
     * Creates Stop Times table (sample due to size)
     */
    private static void createStopTimesTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating STOP_TIMES table (sample)...");
        
        List<Row> stopTimesData = downloadGTFSFile("stop_times.txt");
        
        if (stopTimesData.isEmpty()) {
            LOG.warn("No stop_times data found");
            return;
        }
        
        // Use only a sample due to potentially large size
        int sampleSize = Math.min(stopTimesData.size(), 10000);
        
        Table stopTimesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("trip_id", DataTypes.STRING()),
                DataTypes.FIELD("arrival_time", DataTypes.STRING()),
                DataTypes.FIELD("departure_time", DataTypes.STRING()),
                DataTypes.FIELD("stop_id", DataTypes.STRING()),
                DataTypes.FIELD("stop_sequence", DataTypes.INT()),
                DataTypes.FIELD("stop_headsign", DataTypes.STRING()),
                DataTypes.FIELD("pickup_type", DataTypes.INT()),
                DataTypes.FIELD("drop_off_type", DataTypes.INT())
            ),
            stopTimesData.subList(0, sampleSize)
        );
        
        tableEnv.createTemporaryView("RTD_STOP_TIMES", stopTimesTable);
        LOG.info("✅ Created RTD_STOP_TIMES table with {} sample records (total: {})", 
            sampleSize, stopTimesData.size());
    }
    
    /**
     * Creates Calendar table with service schedules
     */
    private static void createCalendarTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating CALENDAR table...");
        
        List<Row> calendarData = downloadGTFSFile("calendar.txt");
        
        if (calendarData.isEmpty()) {
            LOG.warn("No calendar data found");
            return;
        }
        
        Table calendarTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("service_id", DataTypes.STRING()),
                DataTypes.FIELD("monday", DataTypes.INT()),
                DataTypes.FIELD("tuesday", DataTypes.INT()),
                DataTypes.FIELD("wednesday", DataTypes.INT()),
                DataTypes.FIELD("thursday", DataTypes.INT()),
                DataTypes.FIELD("friday", DataTypes.INT()),
                DataTypes.FIELD("saturday", DataTypes.INT()),
                DataTypes.FIELD("sunday", DataTypes.INT()),
                DataTypes.FIELD("start_date", DataTypes.STRING()),
                DataTypes.FIELD("end_date", DataTypes.STRING())
            ),
            calendarData
        );
        
        tableEnv.createTemporaryView("RTD_CALENDAR", calendarTable);
        LOG.info("✅ Created RTD_CALENDAR table with {} records", calendarData.size());
    }
    
    /**
     * Creates Shapes table (sample due to size)
     */
    private static void createShapesTable(StreamTableEnvironment tableEnv) throws Exception {
        LOG.info("Creating SHAPES table (sample)...");
        
        List<Row> shapesData = downloadGTFSFile("shapes.txt");
        
        if (shapesData.isEmpty()) {
            LOG.warn("No shapes data found");
            return;
        }
        
        // Use sample due to potentially very large size
        int sampleSize = Math.min(shapesData.size(), 5000);
        
        Table shapesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("shape_id", DataTypes.STRING()),
                DataTypes.FIELD("shape_pt_lat", DataTypes.DOUBLE()),
                DataTypes.FIELD("shape_pt_lon", DataTypes.DOUBLE()),
                DataTypes.FIELD("shape_pt_sequence", DataTypes.INT()),
                DataTypes.FIELD("shape_dist_traveled", DataTypes.DOUBLE())
            ),
            shapesData.subList(0, sampleSize)
        );
        
        tableEnv.createTemporaryView("RTD_SHAPES", shapesTable);
        LOG.info("✅ Created RTD_SHAPES table with {} sample records (total: {})", 
            sampleSize, shapesData.size());
    }
    
    /**
     * Downloads specific GTFS file and returns parsed Row data
     */
    private static List<Row> downloadGTFSFile(String fileName) throws IOException {
        LOG.info("Downloading GTFS file: {}", fileName);
        
        List<Row> rows = new ArrayList<>();
        
        HttpURLConnection connection = (HttpURLConnection) new URL(RTD_GTFS_STATIC_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.getName().equals(fileName)) {
                    rows = parseGTFSFile(zipStream, fileName);
                    break;
                }
                zipStream.closeEntry();
            }
        }
        
        LOG.info("✅ Downloaded {} with {} records", fileName, rows.size());
        return rows;
    }
    
    /**
     * Parses GTFS CSV file into typed Row objects
     */
    private static List<Row> parseGTFSFile(ZipInputStream zipStream, String fileName) throws IOException {
        List<Row> rows = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return rows;
        }
        
        String[] headers = headerLine.split(",");
        LOG.debug("Headers for {}: {}", fileName, String.join(", ", headers));
        
        String line;
        int lineCount = 0;
        
        while ((line = reader.readLine()) != null) {
            lineCount++;
            
            try {
                Row row = parseCSVLineWithTypes(line, headers.length, fileName);
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception e) {
                LOG.debug("Skipping invalid line {} in {}: {}", lineCount, fileName, e.getMessage());
            }
            
            // Progress logging for large files
            if (lineCount % 50000 == 0) {
                LOG.info("Processed {} lines in {}", lineCount, fileName);
            }
        }
        
        return rows;
    }
    
    /**
     * Parses CSV line with proper type conversion
     */
    private static Row parseCSVLineWithTypes(String line, int expectedColumns, String fileName) {
        List<String> fields = parseCSVFields(line);
        
        // Pad with nulls if needed
        while (fields.size() < expectedColumns) {
            fields.add("");
        }
        
        Row row = new Row(expectedColumns);
        
        for (int i = 0; i < expectedColumns; i++) {
            String value = i < fields.size() ? fields.get(i) : "";
            value = value.trim();
            
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            
            // Convert types based on file and column
            Object typedValue = convertFieldType(value, fileName, i);
            row.setField(i, typedValue);
        }
        
        return row;
    }
    
    /**
     * Converts field values to appropriate types
     */
    private static Object convertFieldType(String value, String fileName, int columnIndex) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        try {
            // Type conversions based on GTFS specifications
            switch (fileName) {
                case "routes.txt":
                    if (columnIndex == 5) return Integer.parseInt(value); // route_type
                    if (columnIndex == 9) return Integer.parseInt(value); // route_sort_order
                    break;
                    
                case "stops.txt":
                    if (columnIndex == 4) return Double.parseDouble(value); // stop_lat
                    if (columnIndex == 5) return Double.parseDouble(value); // stop_lon
                    if (columnIndex == 8) return Integer.parseInt(value); // location_type
                    if (columnIndex == 11) return Integer.parseInt(value); // wheelchair_boarding
                    break;
                    
                case "trips.txt":
                    if (columnIndex == 5) return Integer.parseInt(value); // direction_id
                    if (columnIndex == 8) return Integer.parseInt(value); // wheelchair_accessible
                    if (columnIndex == 9) return Integer.parseInt(value); // bikes_allowed
                    break;
                    
                case "stop_times.txt":
                    if (columnIndex == 4) return Integer.parseInt(value); // stop_sequence
                    if (columnIndex == 6) return Integer.parseInt(value); // pickup_type
                    if (columnIndex == 7) return Integer.parseInt(value); // drop_off_type
                    break;
                    
                case "calendar.txt":
                    if (columnIndex >= 1 && columnIndex <= 7) return Integer.parseInt(value); // weekdays
                    break;
                    
                case "shapes.txt":
                    if (columnIndex == 1) return Double.parseDouble(value); // shape_pt_lat
                    if (columnIndex == 2) return Double.parseDouble(value); // shape_pt_lon
                    if (columnIndex == 3) return Integer.parseInt(value); // shape_pt_sequence
                    if (columnIndex == 4) return Double.parseDouble(value); // shape_dist_traveled
                    break;
            }
        } catch (NumberFormatException e) {
            // Return string if conversion fails
        }
        
        return value;
    }
    
    /**
     * Parses CSV fields handling quoted values
     */
    private static List<String> parseCSVFields(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                currentField.append(c);
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        fields.add(currentField.toString());
        return fields;
    }
    
    /**
     * Executes comprehensive RTD analysis queries
     */
    private static void executeRTDAnalysisQueries(StreamTableEnvironment tableEnv) {
        LOG.info("=== Executing RTD Transit Network Analysis ===");
        
        try {
            // RTD Agency Information
            executeQuery(tableEnv, "RTD Agency Information",
                "SELECT agency_name, agency_url, agency_timezone, agency_phone FROM RTD_AGENCY");
            
            // Route Analysis
            executeQuery(tableEnv, "RTD Routes by Type",
                "SELECT route_type, COUNT(*) as route_count " +
                "FROM RTD_ROUTES GROUP BY route_type ORDER BY route_count DESC");
            
            executeQuery(tableEnv, "Popular RTD Routes",
                "SELECT route_short_name, route_long_name, route_type " +
                "FROM RTD_ROUTES WHERE route_short_name IS NOT NULL " +
                "ORDER BY route_sort_order LIMIT 20");
            
            // Stop Analysis
            executeQuery(tableEnv, "RTD Stops by Type",
                "SELECT location_type, COUNT(*) as stop_count " +
                "FROM RTD_STOPS GROUP BY location_type");
            
            executeQuery(tableEnv, "RTD Station Locations",
                "SELECT stop_name, stop_lat, stop_lon " +
                "FROM RTD_STOPS WHERE location_type = 1 LIMIT 10");
            
            // Service Pattern Analysis
            executeQuery(tableEnv, "RTD Service Patterns",
                "SELECT monday, tuesday, wednesday, thursday, friday, saturday, sunday, COUNT(*) as pattern_count " +
                "FROM RTD_CALENDAR GROUP BY monday, tuesday, wednesday, thursday, friday, saturday, sunday " +
                "ORDER BY pattern_count DESC");
            
            // Trip Analysis
            executeQuery(tableEnv, "Trips by Route",
                "SELECT route_id, COUNT(*) as trip_count " +
                "FROM RTD_TRIPS GROUP BY route_id ORDER BY trip_count DESC LIMIT 15");
            
            // Stop Time Analysis (sample)
            executeQuery(tableEnv, "Popular Stops (by scheduled visits)",
                "SELECT stop_id, COUNT(*) as visit_count " +
                "FROM RTD_STOP_TIMES GROUP BY stop_id ORDER BY visit_count DESC LIMIT 10");
            
        } catch (Exception e) {
            LOG.error("Error executing RTD analysis queries: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Executes a query and prints formatted results
     */
    private static void executeQuery(StreamTableEnvironment tableEnv, String queryName, String sql) {
        LOG.info("=== {} ===", queryName);
        
        try {
            TableResult result = tableEnv.executeSql(sql);
            
            System.out.println("\n" + queryName + ":");
            System.out.println("-".repeat(80));
            
            var iterator = result.collect();
            int count = 0;
            int maxRows = 15;
            
            while (iterator.hasNext() && count < maxRows) {
                Row row = iterator.next();
                for (int i = 0; i < row.getArity(); i++) {
                    Object field = row.getField(i);
                    String value = field != null ? field.toString() : "NULL";
                    System.out.printf("%-25s", value.length() > 23 ? value.substring(0, 23) : value);
                }
                System.out.println();
                count++;
            }
            
            System.out.println("-".repeat(80));
            LOG.info("Query completed - showed {} rows", count);
            
        } catch (Exception e) {
            LOG.error("Failed to execute query '{}': {}", queryName, e.getMessage());
        }
    }
}