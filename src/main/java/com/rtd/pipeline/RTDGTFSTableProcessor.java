package com.rtd.pipeline;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * RTD GTFS Table Processor
 * Downloads and processes all RTD static GTFS files into Flink tables
 * Provides comprehensive transit network data for analysis and querying
 */
public class RTDGTFSTableProcessor {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDGTFSTableProcessor.class);
    
    // RTD GTFS Static Data URLs
    private static final String[] RTD_GTFS_URLS = {
        "https://www.rtd-denver.com/files/gtfs/google_transit.zip",  // Main RTD GTFS
        "https://www.rtd-denver.com/files/gtfs/google_transit_flex.zip",  // FlexRide services
        "https://www.rtd-denver.com/files/gtfs/bustang-co-us.zip"  // Bustang intercity service
    };
    
    // GTFS file specifications
    private static final Map<String, String[]> GTFS_FILE_SCHEMAS = new HashMap<>();
    
    static {
        // Core GTFS files with their expected columns
        GTFS_FILE_SCHEMAS.put("agency.txt", new String[]{
            "agency_id", "agency_name", "agency_url", "agency_timezone", "agency_lang", "agency_phone"
        });
        
        GTFS_FILE_SCHEMAS.put("routes.txt", new String[]{
            "route_id", "agency_id", "route_short_name", "route_long_name", "route_desc", 
            "route_type", "route_url", "route_color", "route_text_color", "route_sort_order"
        });
        
        GTFS_FILE_SCHEMAS.put("stops.txt", new String[]{
            "stop_id", "stop_code", "stop_name", "stop_desc", "stop_lat", "stop_lon", 
            "zone_id", "stop_url", "location_type", "parent_station", "stop_timezone", 
            "wheelchair_boarding", "level_id", "platform_code"
        });
        
        GTFS_FILE_SCHEMAS.put("trips.txt", new String[]{
            "route_id", "service_id", "trip_id", "trip_headsign", "trip_short_name", 
            "direction_id", "block_id", "shape_id", "wheelchair_accessible", "bikes_allowed"
        });
        
        GTFS_FILE_SCHEMAS.put("stop_times.txt", new String[]{
            "trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence", 
            "stop_headsign", "pickup_type", "drop_off_type", "continuous_pickup", 
            "continuous_drop_off", "shape_dist_traveled", "timepoint"
        });
        
        GTFS_FILE_SCHEMAS.put("calendar.txt", new String[]{
            "service_id", "monday", "tuesday", "wednesday", "thursday", "friday", 
            "saturday", "sunday", "start_date", "end_date"
        });
        
        GTFS_FILE_SCHEMAS.put("calendar_dates.txt", new String[]{
            "service_id", "date", "exception_type"
        });
        
        GTFS_FILE_SCHEMAS.put("shapes.txt", new String[]{
            "shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence", "shape_dist_traveled"
        });
        
        GTFS_FILE_SCHEMAS.put("fare_attributes.txt", new String[]{
            "fare_id", "price", "currency_type", "payment_method", "transfers", "transfer_duration"
        });
        
        GTFS_FILE_SCHEMAS.put("fare_rules.txt", new String[]{
            "fare_id", "route_id", "origin_id", "destination_id", "contains_id"
        });
    }
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD GTFS Table Processor Starting ===");
        LOG.info("Processing static GTFS data from {} RTD sources", RTD_GTFS_URLS.length);
        
        // Create Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        
        try {
            // Download and process GTFS data
            Map<String, List<Row>> gtfsData = downloadAndProcessGTFS();
            
            // Create Flink tables for each GTFS file
            Map<String, Table> tables = createFlinkTables(tableEnv, gtfsData);
            
            // Register tables in the catalog
            registerTables(tableEnv, tables);
            
            // Execute sample queries
            executeSampleQueries(tableEnv);
            
            LOG.info("✅ RTD GTFS Table processing completed successfully");
            
        } catch (Exception e) {
            LOG.error("❌ Error processing RTD GTFS data: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Downloads RTD GTFS ZIP files and processes all contained files
     */
    private static Map<String, List<Row>> downloadAndProcessGTFS() throws IOException {
        LOG.info("Downloading RTD GTFS static data from {} sources...", RTD_GTFS_URLS.length);
        
        Map<String, List<Row>> allGtfsData = new HashMap<>();
        
        // Process each GTFS feed
        for (int i = 0; i < RTD_GTFS_URLS.length; i++) {
            String gtfsUrl = RTD_GTFS_URLS[i];
            LOG.info("Processing GTFS feed {}/{}: {}", i + 1, RTD_GTFS_URLS.length, gtfsUrl);
            
            try {
                Map<String, List<Row>> gtfsData = downloadSingleGTFS(gtfsUrl);
                
                // Merge data from multiple feeds
                for (Map.Entry<String, List<Row>> entry : gtfsData.entrySet()) {
                    String fileName = entry.getKey();
                    List<Row> fileData = entry.getValue();
                    
                    if (allGtfsData.containsKey(fileName)) {
                        // Append data if file already exists from another feed
                        allGtfsData.get(fileName).addAll(fileData);
                        LOG.info("✅ Merged {} records from {} (total: {})", 
                            fileData.size(), fileName, allGtfsData.get(fileName).size());
                    } else {
                        // New file
                        allGtfsData.put(fileName, new ArrayList<>(fileData));
                        LOG.info("✅ Added {} with {} records", fileName, fileData.size());
                    }
                }
                
            } catch (Exception e) {
                LOG.warn("Failed to process GTFS feed {}: {}", gtfsUrl, e.getMessage());
            }
        }
        
        LOG.info("✅ Downloaded and processed {} GTFS files from all sources", allGtfsData.size());
        return allGtfsData;
    }
    
    /**
     * Downloads a single GTFS ZIP file and processes all contained files
     */
    private static Map<String, List<Row>> downloadSingleGTFS(String gtfsUrl) throws IOException {
        Map<String, List<Row>> gtfsData = new HashMap<>();
        
        HttpURLConnection connection = (HttpURLConnection) new URL(gtfsUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                
                if (fileName.endsWith(".txt") && GTFS_FILE_SCHEMAS.containsKey(fileName)) {
                    LOG.debug("Processing GTFS file: {} from {}", fileName, gtfsUrl);
                    
                    List<Row> fileData = processGTFSFile(zipStream, fileName);
                    gtfsData.put(fileName, fileData);
                }
                
                zipStream.closeEntry();
            }
        }
        
        return gtfsData;
    }
    
    /**
     * Processes individual GTFS file from ZIP stream
     */
    private static List<Row> processGTFSFile(ZipInputStream zipStream, String fileName) throws IOException {
        List<Row> rows = new ArrayList<>();
        String[] expectedColumns = GTFS_FILE_SCHEMAS.get(fileName);
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            LOG.warn("Empty file: {}", fileName);
            return rows;
        }
        
        String[] headers = headerLine.split(",");
        LOG.debug("File: {} - Headers: {}", fileName, String.join(", ", headers));
        
        String line;
        int lineNumber = 1;
        
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            
            try {
                Row row = parseCSVLine(line, headers.length);
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception e) {
                LOG.warn("Error parsing line {} in {}: {} - Line: {}", 
                    lineNumber, fileName, e.getMessage(), line);
            }
            
            // Log progress for large files
            if (lineNumber % 10000 == 0) {
                LOG.info("Processed {} lines in {}", lineNumber, fileName);
            }
        }
        
        return rows;
    }
    
    /**
     * Parses CSV line into Flink Row
     */
    private static Row parseCSVLine(String line, int expectedColumns) {
        // Handle CSV parsing with potential commas in quoted fields
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
        
        // Add the last field
        fields.add(currentField.toString().trim());
        
        // Pad with empty strings if needed
        while (fields.size() < expectedColumns) {
            fields.add("");
        }
        
        // Create Row with proper field count
        Row row = new Row(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String value = fields.get(i);
            // Remove surrounding quotes if present
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            row.setField(i, value.isEmpty() ? null : value);
        }
        
        return row;
    }
    
    /**
     * Creates Flink tables from processed GTFS data
     */
    private static Map<String, Table> createFlinkTables(
            StreamTableEnvironment tableEnv, 
            Map<String, List<Row>> gtfsData) {
        
        LOG.info("Creating Flink tables from GTFS data...");
        Map<String, Table> tables = new HashMap<>();
        
        for (Map.Entry<String, List<Row>> entry : gtfsData.entrySet()) {
            String fileName = entry.getKey();
            List<Row> data = entry.getValue();
            
            if (data.isEmpty()) {
                LOG.warn("Skipping empty file: {}", fileName);
                continue;
            }
            
            try {
                // Create table from Row data
                Table table = tableEnv.fromValues(data);
                String tableName = fileName.replace(".txt", "").toUpperCase();
                tables.put(tableName, table);
                
                LOG.info("✅ Created table {} with {} rows", tableName, data.size());
                
            } catch (Exception e) {
                LOG.error("Failed to create table for {}: {}", fileName, e.getMessage());
            }
        }
        
        return tables;
    }
    
    /**
     * Registers tables in Flink catalog for SQL querying
     */
    private static void registerTables(StreamTableEnvironment tableEnv, Map<String, Table> tables) {
        LOG.info("Registering tables in Flink catalog...");
        
        for (Map.Entry<String, Table> entry : tables.entrySet()) {
            String tableName = entry.getKey();
            Table table = entry.getValue();
            
            try {
                tableEnv.createTemporaryView(tableName, table);
                LOG.info("✅ Registered table: {}", tableName);
            } catch (Exception e) {
                LOG.error("Failed to register table {}: {}", tableName, e.getMessage());
            }
        }
        
        LOG.info("✅ Registered {} tables in catalog", tables.size());
    }
    
    /**
     * Executes sample queries to demonstrate table functionality
     */
    private static void executeSampleQueries(StreamTableEnvironment tableEnv) {
        LOG.info("=== Executing Sample GTFS Queries ===");
        
        try {
            // Query 1: Agency information
            LOG.info("Query 1: RTD Agency Information");
            TableResult agencyResult = tableEnv.executeSql(
                "SELECT f0 as agency_id, f1 as agency_name, f2 as agency_url, f3 as timezone " +
                "FROM AGENCY LIMIT 5"
            );
            printTableResult("AGENCY", agencyResult, 5);
            
            // Query 2: Route summary
            LOG.info("Query 2: RTD Route Summary");
            TableResult routeResult = tableEnv.executeSql(
                "SELECT f0 as route_id, f2 as route_short_name, f3 as route_long_name, " +
                "f5 as route_type FROM ROUTES LIMIT 10"
            );
            printTableResult("ROUTES", routeResult, 10);
            
            // Query 3: Stop count by location type
            LOG.info("Query 3: Stop Count Analysis");
            TableResult stopResult = tableEnv.executeSql(
                "SELECT f8 as location_type, COUNT(*) as stop_count " +
                "FROM STOPS GROUP BY f8 ORDER BY stop_count DESC"
            );
            printTableResult("STOP_ANALYSIS", stopResult, 5);
            
            // Query 4: Service calendar summary
            LOG.info("Query 4: Service Calendar Summary");
            TableResult calendarResult = tableEnv.executeSql(
                "SELECT f0 as service_id, f1 as monday, f2 as tuesday, f3 as wednesday, " +
                "f4 as thursday, f5 as friday, f6 as saturday, f7 as sunday " +
                "FROM CALENDAR LIMIT 5"
            );
            printTableResult("CALENDAR", calendarResult, 5);
            
            // Query 5: Trip count by route
            LOG.info("Query 5: Trip Count by Route");
            TableResult tripResult = tableEnv.executeSql(
                "SELECT f0 as route_id, COUNT(*) as trip_count " +
                "FROM TRIPS GROUP BY f0 ORDER BY trip_count DESC LIMIT 10"
            );
            printTableResult("TRIP_ANALYSIS", tripResult, 10);
            
        } catch (Exception e) {
            LOG.error("Error executing sample queries: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Prints formatted table results
     */
    private static void printTableResult(String queryName, TableResult result, int maxRows) {
        LOG.info("=== {} Results ===", queryName);
        
        try {
            result.getResolvedSchema().getColumns().forEach(column -> {
                System.out.printf("%-20s", column.getName());
            });
            System.out.println();
            System.out.println("-".repeat(80));
            
            var iterator = result.collect();
            int count = 0;
            
            while (iterator.hasNext() && count < maxRows) {
                Row row = iterator.next();
                for (int i = 0; i < row.getArity(); i++) {
                    Object field = row.getField(i);
                    String value = field != null ? field.toString() : "NULL";
                    System.out.printf("%-20s", value.length() > 18 ? value.substring(0, 18) : value);
                }
                System.out.println();
                count++;
            }
            
            System.out.println("-".repeat(80));
            LOG.info("Showed {} rows for {}", count, queryName);
            
        } catch (Exception e) {
            LOG.error("Error printing results for {}: {}", queryName, e.getMessage());
        }
    }
}