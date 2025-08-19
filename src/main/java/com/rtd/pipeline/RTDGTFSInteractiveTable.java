package com.rtd.pipeline;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * RTD GTFS Interactive Table System
 * Provides an interactive SQL query interface for RTD static GTFS data
 * Includes pre-built analysis functions and custom query capabilities
 */
public class RTDGTFSInteractiveTable {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDGTFSInteractiveTable.class);
    
    private static final String RTD_GTFS_STATIC_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs/google_transit.zip";
    private static StreamTableEnvironment tableEnv;
    private static Scanner scanner;
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD GTFS Interactive Table System ===");
        LOG.info("Loading RTD transit network data for analysis...");
        
        // Initialize Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        tableEnv = StreamTableEnvironment.create(env);
        
        // Check if running in test mode
        boolean testMode = args.length > 0 && "test".equals(args[0]);
        
        try {
            // Load RTD GTFS data into tables
            loadRTDData();
            
            if (testMode) {
                // Test mode - just run a sample query and exit
                testTableCreation();
            } else {
                // Interactive mode
                scanner = new Scanner(System.in);
                runInteractiveSession();
            }
            
        } catch (Exception e) {
            LOG.error("❌ Error in RTD GTFS Interactive Table: {}", e.getMessage(), e);
            throw e;
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }
    
    /**
     * Tests that tables were created properly
     */
    private static void testTableCreation() {
        System.out.println("\n=== Testing Table Creation ===");
        
        // Test AGENCY table
        try {
            TableResult result = tableEnv.executeSql("SELECT * FROM AGENCY");
            System.out.println("✅ AGENCY table exists and queryable");
            
            var iterator = result.collect();
            int count = 0;
            while (iterator.hasNext() && count < 5) {
                Row row = iterator.next();
                System.out.println("Agency Record: " + row.toString());
                count++;
            }
            
        } catch (Exception e) {
            System.out.println("❌ AGENCY table error: " + e.getMessage());
        }
        
        // Test other tables
        String[] tables = {"ROUTES", "STOPS", "TRIPS", "CALENDAR"};
        for (String table : tables) {
            try {
                tableEnv.executeSql("SELECT COUNT(*) FROM " + table);
                System.out.println("✅ " + table + " table exists");
            } catch (Exception e) {
                System.out.println("❌ " + table + " table error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Loads all RTD GTFS data into Flink tables
     */
    private static void loadRTDData() throws Exception {
        LOG.info("Loading RTD GTFS data into tables...");
        
        // Download GTFS data
        Map<String, List<Row>> gtfsData = downloadAllGTFSFiles();
        
        // Create tables with proper schemas
        createAgencyTable(gtfsData.get("agency.txt"));
        createRoutesTable(gtfsData.get("routes.txt"));
        createStopsTable(gtfsData.get("stops.txt"));
        createTripsTable(gtfsData.get("trips.txt"));
        createStopTimesTable(gtfsData.get("stop_times.txt"));
        createCalendarTable(gtfsData.get("calendar.txt"));
        createCalendarDatesTable(gtfsData.get("calendar_dates.txt"));
        createShapesTable(gtfsData.get("shapes.txt"));
        
        LOG.info("✅ RTD GTFS data loaded successfully");
        
        // Display available tables
        displayAvailableTables();
    }
    
    /**
     * Downloads all GTFS files from RTD
     */
    private static Map<String, List<Row>> downloadAllGTFSFiles() throws IOException {
        LOG.info("Downloading RTD GTFS data...");
        
        Map<String, List<Row>> gtfsData = new HashMap<>();
        Set<String> targetFiles = Set.of(
            "agency.txt", "routes.txt", "stops.txt", "trips.txt", 
            "stop_times.txt", "calendar.txt", "calendar_dates.txt", "shapes.txt"
        );
        
        HttpURLConnection connection = (HttpURLConnection) new URL(RTD_GTFS_STATIC_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(180000);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                
                if (targetFiles.contains(fileName)) {
                    LOG.info("Processing: {}", fileName);
                    List<Row> fileData = parseCSVFromZip(zipStream);
                    gtfsData.put(fileName, fileData);
                    LOG.info("✅ Loaded {} with {} records", fileName, fileData.size());
                }
                
                zipStream.closeEntry();
            }
        }
        
        return gtfsData;
    }
    
    /**
     * Parses CSV data from ZIP stream
     */
    private static List<Row> parseCSVFromZip(ZipInputStream zipStream) throws IOException {
        List<Row> rows = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return rows;
        }
        
        String[] headers = headerLine.split(",");
        
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
    
    /**
     * Parses CSV line into Row
     */
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
        
        Row row = new Row(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String value = fields.get(i);
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                value = value.substring(1, value.length() - 1);
            }
            row.setField(i, value.isEmpty() ? null : value);
        }
        
        return row;
    }
    
    /**
     * Creates Agency table
     */
    private static void createAgencyTable(List<Row> data) {
        LOG.info("Creating AGENCY table...");
        if (data == null) {
            LOG.error("❌ Agency data is null - AGENCY table will not be created");
            return;
        }
        if (data.isEmpty()) {
            LOG.error("❌ Agency data is empty - AGENCY table will not be created");
            return;
        }
        
        LOG.info("Agency data contains {} records", data.size());
        
        Table agencyTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("agency_url", DataTypes.STRING()),
                DataTypes.FIELD("agency_name", DataTypes.STRING()),
                DataTypes.FIELD("agency_timezone", DataTypes.STRING()),
                DataTypes.FIELD("agency_id", DataTypes.STRING()),
                DataTypes.FIELD("agency_lang", DataTypes.STRING())
            ),
            data
        );
        
        tableEnv.createTemporaryView("AGENCY", agencyTable);
        LOG.info("✅ AGENCY table created successfully");
    }
    
    /**
     * Creates Routes table
     */
    private static void createRoutesTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        // Limit size for performance
        List<Row> limitedData = data.subList(0, Math.min(data.size(), 500));
        
        Table routesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("route_long_name", DataTypes.STRING()),
                DataTypes.FIELD("route_type", DataTypes.STRING()),
                DataTypes.FIELD("network_id", DataTypes.STRING()),
                DataTypes.FIELD("route_text_color", DataTypes.STRING()),
                DataTypes.FIELD("agency_id", DataTypes.STRING()),
                DataTypes.FIELD("route_id", DataTypes.STRING()),
                DataTypes.FIELD("route_color", DataTypes.STRING()),
                DataTypes.FIELD("route_desc", DataTypes.STRING()),
                DataTypes.FIELD("route_url", DataTypes.STRING()),
                DataTypes.FIELD("route_short_name", DataTypes.STRING())
            ),
            limitedData
        );
        
        tableEnv.createTemporaryView("ROUTES", routesTable);
    }
    
    /**
     * Creates Stops table
     */
    private static void createStopsTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        // Limit size for performance
        List<Row> limitedData = data.subList(0, Math.min(data.size(), 1000));
        
        Table stopsTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("stop_lat", DataTypes.STRING()),
                DataTypes.FIELD("wheelchair_boarding", DataTypes.STRING()),
                DataTypes.FIELD("stop_code", DataTypes.STRING()),
                DataTypes.FIELD("stop_lon", DataTypes.STRING()),
                DataTypes.FIELD("stop_timezone", DataTypes.STRING()),
                DataTypes.FIELD("stop_url", DataTypes.STRING()),
                DataTypes.FIELD("parent_station", DataTypes.STRING()),
                DataTypes.FIELD("stop_desc", DataTypes.STRING()),
                DataTypes.FIELD("stop_name", DataTypes.STRING()),
                DataTypes.FIELD("location_type", DataTypes.STRING()),
                DataTypes.FIELD("stop_id", DataTypes.STRING()),
                DataTypes.FIELD("zone_id", DataTypes.STRING())
            ),
            limitedData
        );
        
        tableEnv.createTemporaryView("STOPS", stopsTable);
    }
    
    /**
     * Creates Trips table
     */
    private static void createTripsTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        // Limit size for performance
        List<Row> limitedData = data.subList(0, Math.min(data.size(), 2000));
        
        Table tripsTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("block_id", DataTypes.STRING()),
                DataTypes.FIELD("route_id", DataTypes.STRING()),
                DataTypes.FIELD("direction_id", DataTypes.STRING()),
                DataTypes.FIELD("trip_headsign", DataTypes.STRING()),
                DataTypes.FIELD("shape_id", DataTypes.STRING()),
                DataTypes.FIELD("service_id", DataTypes.STRING()),
                DataTypes.FIELD("trip_id", DataTypes.STRING())
            ),
            limitedData
        );
        
        tableEnv.createTemporaryView("TRIPS", tripsTable);
    }
    
    /**
     * Creates Stop Times table (sample)
     */
    private static void createStopTimesTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        // Use small sample due to size
        List<Row> sampleData = data.subList(0, Math.min(data.size(), 5000));
        
        Table stopTimesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("trip_id", DataTypes.STRING()),
                DataTypes.FIELD("arrival_time", DataTypes.STRING()),
                DataTypes.FIELD("departure_time", DataTypes.STRING()),
                DataTypes.FIELD("stop_id", DataTypes.STRING()),
                DataTypes.FIELD("stop_sequence", DataTypes.STRING()),
                DataTypes.FIELD("stop_headsign", DataTypes.STRING()),
                DataTypes.FIELD("pickup_type", DataTypes.STRING()),
                DataTypes.FIELD("drop_off_type", DataTypes.STRING()),
                DataTypes.FIELD("shape_dist_traveled", DataTypes.STRING()),
                DataTypes.FIELD("timepoint", DataTypes.STRING())
            ),
            sampleData
        );
        
        tableEnv.createTemporaryView("STOP_TIMES", stopTimesTable);
    }
    
    /**
     * Creates Calendar table
     */
    private static void createCalendarTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        Table calendarTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("service_id", DataTypes.STRING()),
                DataTypes.FIELD("start_date", DataTypes.STRING()),
                DataTypes.FIELD("end_date", DataTypes.STRING()),
                DataTypes.FIELD("monday", DataTypes.STRING()),
                DataTypes.FIELD("tuesday", DataTypes.STRING()),
                DataTypes.FIELD("wednesday", DataTypes.STRING()),
                DataTypes.FIELD("thursday", DataTypes.STRING()),
                DataTypes.FIELD("friday", DataTypes.STRING()),
                DataTypes.FIELD("saturday", DataTypes.STRING()),
                DataTypes.FIELD("sunday", DataTypes.STRING())
            ),
            data
        );
        
        tableEnv.createTemporaryView("CALENDAR", calendarTable);
    }
    
    /**
     * Creates Calendar Dates table
     */
    private static void createCalendarDatesTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        Table calendarDatesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("service_id", DataTypes.STRING()),
                DataTypes.FIELD("date", DataTypes.STRING()),
                DataTypes.FIELD("exception_type", DataTypes.STRING())
            ),
            data
        );
        
        tableEnv.createTemporaryView("CALENDAR_DATES", calendarDatesTable);
    }
    
    /**
     * Creates Shapes table (sample)
     */
    private static void createShapesTable(List<Row> data) {
        if (data == null || data.isEmpty()) return;
        
        // Use sample due to potentially large size
        List<Row> sampleData = data.subList(0, Math.min(data.size(), 3000));
        
        Table shapesTable = tableEnv.fromValues(
            DataTypes.ROW(
                DataTypes.FIELD("shape_id", DataTypes.STRING()),
                DataTypes.FIELD("shape_pt_lat", DataTypes.STRING()),
                DataTypes.FIELD("shape_pt_lon", DataTypes.STRING()),
                DataTypes.FIELD("shape_pt_sequence", DataTypes.STRING()),
                DataTypes.FIELD("shape_dist_traveled", DataTypes.STRING())
            ),
            sampleData
        );
        
        tableEnv.createTemporaryView("SHAPES", shapesTable);
    }
    
    /**
     * Displays available tables and their schemas
     */
    private static void displayAvailableTables() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("RTD GTFS Tables Available for Querying:");
        System.out.println("=".repeat(80));
        
        System.out.println("AGENCY        - RTD agency information");
        System.out.println("ROUTES        - Bus and rail routes (sample: 500 records)");
        System.out.println("STOPS         - Transit stops and stations (sample: 1000 records)");
        System.out.println("TRIPS         - Individual trips (sample: 2000 records)");
        System.out.println("STOP_TIMES    - Stop arrival/departure times (sample: 5000 records)");
        System.out.println("CALENDAR      - Service schedules by day of week");
        System.out.println("CALENDAR_DATES- Service exceptions and holidays");
        System.out.println("SHAPES        - Route path coordinates (sample: 3000 records)");
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Sample Queries:");
        System.out.println("=".repeat(80));
        System.out.println("SELECT * FROM AGENCY");
        System.out.println("SELECT route_short_name, route_long_name FROM ROUTES LIMIT 10");
        System.out.println("SELECT stop_name, stop_lat, stop_lon FROM STOPS WHERE location_type = '1'");
        System.out.println("SELECT route_id, COUNT(*) FROM TRIPS GROUP BY route_id");
        System.out.println("SELECT service_id, monday, friday FROM CALENDAR");
        System.out.println("=".repeat(80));
    }
    
    /**
     * Runs interactive SQL session
     */
    private static void runInteractiveSession() {
        System.out.println("\n🚌 RTD GTFS Interactive Query System");
        System.out.println("Enter SQL queries to explore RTD's transit network");
        System.out.println("Commands: 'help', 'tables', 'samples', 'quit'");
        System.out.println("-".repeat(60));
        
        while (true) {
            System.out.print("\nRTD-SQL> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            switch (input.toLowerCase()) {
                case "quit":
                case "exit":
                    System.out.println("Goodbye! 🚌");
                    return;
                    
                case "help":
                    showHelp();
                    break;
                    
                case "tables":
                    displayAvailableTables();
                    break;
                    
                case "samples":
                    runSampleQueries();
                    break;
                    
                default:
                    if (input.toLowerCase().startsWith("select") || 
                        input.toLowerCase().startsWith("show") ||
                        input.toLowerCase().startsWith("describe")) {
                        executeUserQuery(input);
                    } else {
                        System.out.println("❌ Invalid command. Use 'help' for available commands.");
                    }
                    break;
            }
        }
    }
    
    /**
     * Shows help information
     */
    private static void showHelp() {
        System.out.println("\n📖 RTD GTFS Query Help:");
        System.out.println("-".repeat(40));
        System.out.println("tables   - Show available tables");
        System.out.println("samples  - Run sample queries");
        System.out.println("help     - Show this help");
        System.out.println("quit     - Exit the system");
        System.out.println("\nSQL Examples:");
        System.out.println("SELECT * FROM AGENCY");
        System.out.println("SELECT route_short_name, route_type FROM ROUTES LIMIT 5");
        System.out.println("SELECT stop_name FROM STOPS WHERE stop_name LIKE '%Union%'");
    }
    
    /**
     * Runs pre-built sample queries
     */
    private static void runSampleQueries() {
        System.out.println("\n🔍 Running RTD Sample Queries...\n");
        
        String[] sampleQueries = {
            "SELECT agency_name, agency_timezone FROM AGENCY",
            "SELECT route_short_name, route_long_name, route_type FROM ROUTES LIMIT 8",
            "SELECT stop_name FROM STOPS WHERE stop_name LIKE '%Union%' LIMIT 5",
            "SELECT route_id, COUNT(*) as trip_count FROM TRIPS GROUP BY route_id ORDER BY trip_count DESC LIMIT 5",
            "SELECT service_id, monday, saturday, sunday FROM CALENDAR LIMIT 5"
        };
        
        for (String query : sampleQueries) {
            System.out.println("Query: " + query);
            executeUserQuery(query);
            System.out.println();
        }
    }
    
    /**
     * Executes user-provided SQL query
     */
    private static void executeUserQuery(String query) {
        try {
            TableResult result = tableEnv.executeSql(query);
            
            System.out.println("-".repeat(80));
            
            var iterator = result.collect();
            int count = 0;
            int maxRows = 20;
            
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
            
            if (count == 0) {
                System.out.println("No results found.");
            } else {
                System.out.println("-".repeat(80));
                System.out.println("Returned " + count + " rows" + (count >= maxRows ? " (limited)" : ""));
            }
            
        } catch (Exception e) {
            System.out.println("❌ Query Error: " + e.getMessage());
        }
    }
}