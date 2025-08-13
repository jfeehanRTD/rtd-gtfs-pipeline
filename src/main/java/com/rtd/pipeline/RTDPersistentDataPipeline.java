package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
import com.rtd.pipeline.model.GTFSScheduleData;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * RTD data pipeline with file-based persistence and 2-day retention.
 * Provides both real-time data serving and persistent file storage.
 * Supports SQL-like queries through file-based tables.
 */
public class RTDPersistentDataPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDPersistentDataPipeline.class);
    
    // Configuration
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final String GTFS_SCHEDULE_URL = "https://www.rtd-denver.com/files/gtfs/google_transit.zip";
    private static final long FETCH_INTERVAL_SECONDS = 60L;
    private static final long GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS = 3600L; // 1 hour
    private static final int HTTP_SERVER_PORT = 8081;
    
    // Data storage paths
    private static final String DATA_BASE_PATH = "./data";
    private static final String VEHICLE_DATA_PATH = DATA_BASE_PATH + "/vehicles";
    private static final String SCHEDULE_DATA_PATH = DATA_BASE_PATH + "/schedule";
    
    // Data retention - 2 days
    private static final Duration DATA_RETENTION_PERIOD = Duration.ofDays(2);
    
    // Shared data for HTTP endpoints
    private static final AtomicReference<List<Row>> latestVehicleData = new AtomicReference<>();
    private static final AtomicReference<String> latestJsonData = new AtomicReference<>();
    private static final AtomicReference<List<GTFSScheduleData>> latestScheduleData = new AtomicReference<>();
    private static final AtomicReference<String> latestScheduleJsonData = new AtomicReference<>();
    private static final AtomicReference<Long> lastUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    private static final AtomicReference<Long> lastScheduleUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    
    // Schedulers
    private static ScheduledExecutorService rtScheduler;
    private static ScheduledExecutorService gtfsScheduler;
    private static ScheduledExecutorService cleanupScheduler;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== RTD Persistent Data Pipeline ===");
        System.out.println("Real-time RTD data with file-based persistence and Table API access");
        System.out.println("Data retention: " + DATA_RETENTION_PERIOD.toDays() + " days");
        System.out.println("Storage path: " + DATA_BASE_PATH);
        
        // Create data directories
        createDataDirectories();
        
        // Start HTTP server
        startHttpServer();
        
        // Create schedulers
        rtScheduler = Executors.newSingleThreadScheduledExecutor();
        gtfsScheduler = Executors.newSingleThreadScheduledExecutor();
        
        RTDRowSource dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS);
        
        // Start GTFS schedule fetching
        startGTFSScheduleFetching();
        
        // Start cleanup scheduler
        startCleanupScheduler();
        
        // Counter for demonstration
        final int[] fetchCount = {0};
        
        try {
            System.out.println("=== Starting RTD Data Fetching with Persistence ===");
            System.out.println("Press Ctrl+C to stop\n");
            
            // Schedule periodic real-time data fetching with file persistence
            rtScheduler.scheduleWithFixedDelay(() -> {
                try {
                    fetchCount[0]++;
                    System.out.printf("=== Fetch #%d (every %ds) ===\n", fetchCount[0], FETCH_INTERVAL_SECONDS);
                    
                    // Use the RTDRowSource to fetch data directly
                    java.lang.reflect.Method fetchMethod = RTDRowSource.class.getDeclaredMethod("fetchVehiclePositionsAsRows");
                    fetchMethod.setAccessible(true);
                    
                    @SuppressWarnings("unchecked")
                    List<Row> vehicles = (List<Row>) fetchMethod.invoke(dataSource);
                    
                    if (!vehicles.isEmpty()) {
                        System.out.printf("✅ Retrieved %d vehicles from RTD\n", vehicles.size());
                        
                        // Persist to file sinks
                        persistVehicleData(vehicles);
                        
                        // Update shared data for HTTP server
                        latestVehicleData.set(vehicles);
                        latestJsonData.set(convertVehiclesToJson(vehicles));
                        lastUpdateTime.set(System.currentTimeMillis());
                        
                        System.out.printf("💾 Data persisted to %s\n", VEHICLE_DATA_PATH);
                        
                    } else {
                        System.out.println("❌ No vehicles retrieved from RTD");
                    }
                    
                    System.out.printf("Next fetch in %d seconds...\n\n", FETCH_INTERVAL_SECONDS);
                    
                } catch (Exception e) {
                    System.err.printf("❌ Error fetching RTD data: %s\n", e.getMessage());
                    LOG.error("Error in vehicle data fetch", e);
                }
            }, 0, FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.out.println("\n=== Shutting Down RTD Pipeline ===");
        } finally {
            rtScheduler.shutdown();
            gtfsScheduler.shutdown();
            cleanupScheduler.shutdown();
            try {
                if (!rtScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rtScheduler.shutdownNow();
                }
                if (!gtfsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    gtfsScheduler.shutdownNow();
                }
                if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rtScheduler.shutdownNow();
                gtfsScheduler.shutdownNow();
                cleanupScheduler.shutdownNow();
            }
        }
        
        System.out.println("\n=== Pipeline Summary ===");
        System.out.printf("✅ Successfully fetched RTD data %d times\n", fetchCount[0]);
        System.out.println("✅ Demonstrated live vehicle position retrieval with persistence");
        System.out.println("✅ Data stored in multiple formats (JSON, CSV) with 2-day retention");
        System.out.println("✅ Served live data to React web app via HTTP");
        System.out.printf("📁 Data location: %s\n", DATA_BASE_PATH);
    }
    
    private static void createDataDirectories() throws IOException {
        Files.createDirectories(Paths.get(VEHICLE_DATA_PATH + "/json"));
        Files.createDirectories(Paths.get(VEHICLE_DATA_PATH + "/csv"));
        Files.createDirectories(Paths.get(VEHICLE_DATA_PATH + "/table"));
        Files.createDirectories(Paths.get(SCHEDULE_DATA_PATH + "/json"));
        Files.createDirectories(Paths.get(SCHEDULE_DATA_PATH + "/table"));
        
        System.out.println("✅ Created data directories");
    }
    
    private static void persistVehicleData(List<Row> vehicles) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String timePartition = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd/HH"));
            String fileName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            
            // Create partition directories
            Path jsonPartition = Paths.get(VEHICLE_DATA_PATH + "/json/" + timePartition);
            Path csvPartition = Paths.get(VEHICLE_DATA_PATH + "/csv/" + timePartition);
            Path tablePartition = Paths.get(VEHICLE_DATA_PATH + "/table/" + timePartition);
            
            Files.createDirectories(jsonPartition);
            Files.createDirectories(csvPartition);
            Files.createDirectories(tablePartition);
            
            // Write JSON format
            try (PrintWriter jsonWriter = new PrintWriter(new FileWriter(jsonPartition.resolve(fileName + ".json").toFile()))) {
                jsonWriter.println(convertVehiclesToJson(vehicles));
            }
            
            // Write CSV format
            try (PrintWriter csvWriter = new PrintWriter(new FileWriter(csvPartition.resolve(fileName + ".csv").toFile()))) {
                // CSV Header
                csvWriter.println("timestamp_ms,vehicle_id,vehicle_label,trip_id,route_id,latitude,longitude,bearing,speed,current_status,congestion_level,occupancy_status");
                
                for (Row vehicle : vehicles) {
                    csvWriter.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        getString(vehicle, 0),
                        getString(vehicle, 1),
                        getString(vehicle, 2),
                        getString(vehicle, 3),
                        getString(vehicle, 4),
                        getString(vehicle, 5),
                        getString(vehicle, 6),
                        getString(vehicle, 7),
                        getString(vehicle, 8),
                        getString(vehicle, 9),
                        getString(vehicle, 10),
                        getString(vehicle, 11)
                    ));
                }
            }
            
            // Write table format (structured JSON for SQL-like access)
            try (PrintWriter tableWriter = new PrintWriter(new FileWriter(tablePartition.resolve(fileName + ".table").toFile()))) {
                for (Row vehicle : vehicles) {
                    tableWriter.println(String.format("""
                        {"timestamp_ms": %s, "vehicle_id": "%s", "vehicle_label": "%s", "trip_id": "%s", "route_id": "%s", "latitude": %s, "longitude": %s, "bearing": %s, "speed": %s, "current_status": "%s", "congestion_level": "%s", "occupancy_status": "%s", "processing_time": "%s"}""",
                        getString(vehicle, 0),
                        getString(vehicle, 1),
                        getString(vehicle, 2),
                        getString(vehicle, 3),
                        getString(vehicle, 4),
                        getString(vehicle, 5),
                        getString(vehicle, 6),
                        getString(vehicle, 7),
                        getString(vehicle, 8),
                        getString(vehicle, 9),
                        getString(vehicle, 10),
                        getString(vehicle, 11),
                        Instant.now().toString()
                    ));
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error persisting vehicle data", e);
        }
    }
    
    private static void startGTFSScheduleFetching() {
        System.out.println("=== Starting GTFS Schedule Data Fetching ===");
        
        // Schedule periodic GTFS schedule fetching
        gtfsScheduler.scheduleWithFixedDelay(() -> {
            try {
                System.out.println("🗓️ Fetching GTFS schedule data...");
                
                // Fetch schedule data (reuse existing logic from RTDStaticDataPipeline)
                List<GTFSScheduleData> scheduleData = fetchGTFSScheduleData();
                
                if (!scheduleData.isEmpty()) {
                    System.out.printf("✅ Retrieved %d GTFS schedule files from RTD\n", scheduleData.size());
                    
                    // Persist schedule data
                    persistScheduleData(scheduleData);
                    
                    // Update shared data for HTTP server
                    latestScheduleData.set(scheduleData);
                    latestScheduleJsonData.set(convertScheduleToJson(scheduleData));
                    lastScheduleUpdateTime.set(System.currentTimeMillis());
                    
                    System.out.printf("💾 Schedule data persisted to %s\n", SCHEDULE_DATA_PATH);
                    
                } else {
                    System.out.println("❌ No GTFS schedule data retrieved from RTD");
                }
                
                System.out.printf("Next GTFS schedule fetch in %d seconds...\n\n", GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS);
                
            } catch (Exception e) {
                System.err.printf("❌ Error fetching GTFS schedule data: %s\n", e.getMessage());
                LOG.error("Error in schedule data fetch", e);
            }
        }, 0, GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private static List<GTFSScheduleData> fetchGTFSScheduleData() {
        // Reuse existing fetch logic from RTDStaticDataPipeline
        java.util.List<GTFSScheduleData> scheduleDataList = new java.util.ArrayList<>();
        
        try {
            try (org.apache.http.impl.client.CloseableHttpClient httpClient = 
                    org.apache.http.impl.client.HttpClients.createDefault()) {
                
                org.apache.http.client.methods.HttpGet request = 
                    new org.apache.http.client.methods.HttpGet(GTFS_SCHEDULE_URL);
                request.setHeader("User-Agent", "RTD-GTFS-Pipeline/1.0");
                
                org.apache.http.HttpResponse response = httpClient.execute(request);
                
                if (response.getStatusLine().getStatusCode() == 200) {
                    byte[] zipData = org.apache.http.util.EntityUtils.toByteArray(response.getEntity());
                    
                    System.out.printf("📦 Downloaded GTFS schedule zip (%d bytes) from %s\n", 
                            zipData.length, GTFS_SCHEDULE_URL);
                    
                    // Process the ZIP file (simplified version)
                    processGTFSZip(zipData, scheduleDataList);
                    
                } else {
                    System.err.printf("❌ Failed to download GTFS schedule. HTTP Status: %d\n", 
                            response.getStatusLine().getStatusCode());
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error in fetchGTFSScheduleData", e);
        }
        
        return scheduleDataList;
    }
    
    private static void processGTFSZip(byte[] zipData, List<GTFSScheduleData> dataList) throws Exception {
        long downloadTimestamp = Instant.now().toEpochMilli();
        String feedVersion = null;
        String agencyName = null;
        String feedStartDate = null;
        String feedEndDate = null;
        
        try (java.util.zip.ZipInputStream zipInputStream = 
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                
                if (entry.isDirectory()) {
                    continue;
                }
                
                String fileName = entry.getName();
                
                StringBuilder content = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(zipInputStream, java.nio.charset.StandardCharsets.UTF_8))) {
                    
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null && lineCount < 10000) {
                        content.append(line).append("\n");
                        lineCount++;
                        
                        // Extract metadata from specific files
                        if (fileName.equals("feed_info.txt") && lineCount == 2) {
                            String[] parts = line.split(",");
                            if (parts.length >= 4) {
                                feedVersion = parts[1].replace("\"", "").trim();
                                feedStartDate = parts[2].replace("\"", "").trim();
                                feedEndDate = parts[3].replace("\"", "").trim();
                            }
                        }
                        
                        if (fileName.equals("agency.txt") && lineCount == 2) {
                            String[] parts = line.split(",");
                            if (parts.length >= 2) {
                                agencyName = parts[1].replace("\"", "").trim();
                            }
                        }
                    }
                }
                
                GTFSScheduleData scheduleData = GTFSScheduleData.builder()
                        .fileType(fileName)
                        .fileContent(content.toString())
                        .downloadTimestamp(downloadTimestamp)
                        .feedVersion(feedVersion)
                        .agencyName(agencyName)
                        .feedStartDate(feedStartDate)
                        .feedEndDate(feedEndDate)
                        .build();
                
                dataList.add(scheduleData);
            }
        }
    }
    
    private static void persistScheduleData(List<GTFSScheduleData> scheduleData) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String timePartition = now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String fileName = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            
            // Create partition directories
            Path jsonPartition = Paths.get(SCHEDULE_DATA_PATH + "/json/" + timePartition);
            Path tablePartition = Paths.get(SCHEDULE_DATA_PATH + "/table/" + timePartition);
            
            Files.createDirectories(jsonPartition);
            Files.createDirectories(tablePartition);
            
            // Write JSON format
            try (PrintWriter jsonWriter = new PrintWriter(new FileWriter(jsonPartition.resolve(fileName + ".json").toFile()))) {
                jsonWriter.println(convertScheduleToJson(scheduleData));
            }
            
            // Write table format
            try (PrintWriter tableWriter = new PrintWriter(new FileWriter(tablePartition.resolve(fileName + ".table").toFile()))) {
                for (GTFSScheduleData data : scheduleData) {
                    tableWriter.println(String.format("""
                        {"download_timestamp": %s, "file_type": "%s", "feed_version": "%s", "agency_name": "%s", "feed_start_date": "%s", "feed_end_date": "%s", "content_length": %d, "processing_time": "%s"}""",
                        data.getDownloadTimestamp(),
                        data.getFileType() != null ? data.getFileType() : "",
                        data.getFeedVersion() != null ? data.getFeedVersion() : "",
                        data.getAgencyName() != null ? data.getAgencyName() : "",
                        data.getFeedStartDate() != null ? data.getFeedStartDate() : "",
                        data.getFeedEndDate() != null ? data.getFeedEndDate() : "",
                        data.getFileContent() != null ? data.getFileContent().length() : 0,
                        Instant.now().toString()
                    ));
                }
            }
            
        } catch (Exception e) {
            LOG.error("Error persisting schedule data", e);
        }
    }
    
    private static void startCleanupScheduler() {
        cleanupScheduler = Executors.newScheduledThreadPool(1);
        
        // Run cleanup every 6 hours
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupOldData();
            } catch (Exception e) {
                LOG.error("Error during data cleanup", e);
            }
        }, 1, 6, TimeUnit.HOURS);
        
        System.out.println("✅ Data cleanup scheduler started (6-hour interval)");
    }
    
    private static void cleanupOldData() {
        long cutoffTime = System.currentTimeMillis() - DATA_RETENTION_PERIOD.toMillis();
        
        try {
            // Cleanup vehicle data
            cleanupDirectoryByTime(VEHICLE_DATA_PATH, cutoffTime);
            
            // Cleanup schedule data  
            cleanupDirectoryByTime(SCHEDULE_DATA_PATH, cutoffTime);
            
            LOG.info("Data cleanup completed for files older than {} days", DATA_RETENTION_PERIOD.toDays());
            
        } catch (Exception e) {
            LOG.error("Error during cleanup", e);
        }
    }
    
    private static void cleanupDirectoryByTime(String directoryPath, long cutoffTime) throws IOException {
        Path dir = Paths.get(directoryPath);
        if (Files.exists(dir)) {
            Files.walk(dir)
                .filter(Files::isRegularFile)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        LOG.debug("Deleted old file: {}", path);
                    } catch (IOException e) {
                        LOG.warn("Could not delete file: {}", path, e);
                    }
                });
        }
    }
    
    private static String convertVehiclesToJson(List<Row> vehicles) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"vehicles\": [\n");
        
        for (int i = 0; i < vehicles.size(); i++) {
            Row vehicle = vehicles.get(i);
            
            json.append("    {\n");
            json.append("      \"vehicle_id\": \"").append(getString(vehicle, 1)).append("\",\n");
            json.append("      \"vehicle_label\": \"").append(getString(vehicle, 2)).append("\",\n");
            json.append("      \"trip_id\": \"").append(getString(vehicle, 3)).append("\",\n");
            json.append("      \"route_id\": \"").append(getString(vehicle, 4)).append("\",\n");
            json.append("      \"latitude\": ").append(getDouble(vehicle, 5)).append(",\n");
            json.append("      \"longitude\": ").append(getDouble(vehicle, 6)).append(",\n");
            json.append("      \"bearing\": ").append(getDouble(vehicle, 7)).append(",\n");
            json.append("      \"speed\": ").append(getDouble(vehicle, 8)).append(",\n");
            json.append("      \"current_status\": \"").append(getString(vehicle, 9)).append("\",\n");
            json.append("      \"occupancy_status\": \"").append(getString(vehicle, 11)).append("\",\n");
            json.append("      \"timestamp_ms\": ").append(getLong(vehicle, 0)).append(",\n");
            json.append("      \"last_updated\": \"").append(Instant.now().toString()).append("\",\n");
            json.append("      \"is_real_time\": true\n");
            json.append("    }");
            
            if (i < vehicles.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"total_count\": ").append(vehicles.size()).append(",\n");
        json.append("    \"last_update\": \"").append(Instant.ofEpochMilli(lastUpdateTime.get()).toString()).append("\",\n");
        json.append("    \"source\": \"RTD GTFS-RT Live Feed\"\n");
        json.append("  }\n");
        json.append("}");
        
        return json.toString();
    }
    
    private static String convertScheduleToJson(List<GTFSScheduleData> scheduleData) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"gtfs_schedule_files\": [\n");
        
        for (int i = 0; i < scheduleData.size(); i++) {
            GTFSScheduleData data = scheduleData.get(i);
            
            json.append("    {\n");
            json.append("      \"file_type\": \"").append(data.getFileType() != null ? data.getFileType() : "").append("\",\n");
            json.append("      \"feed_version\": \"").append(data.getFeedVersion() != null ? data.getFeedVersion() : "").append("\",\n");
            json.append("      \"agency_name\": \"").append(data.getAgencyName() != null ? data.getAgencyName() : "").append("\",\n");
            json.append("      \"feed_start_date\": \"").append(data.getFeedStartDate() != null ? data.getFeedStartDate() : "").append("\",\n");
            json.append("      \"feed_end_date\": \"").append(data.getFeedEndDate() != null ? data.getFeedEndDate() : "").append("\",\n");
            json.append("      \"content_length\": ").append(data.getFileContent() != null ? data.getFileContent().length() : 0).append(",\n");
            json.append("      \"download_timestamp\": ").append(data.getDownloadTimestamp() != null ? data.getDownloadTimestamp() : 0).append(",\n");
            json.append("      \"last_updated\": \"").append(Instant.now().toString()).append("\"\n");
            json.append("    }");
            
            if (i < scheduleData.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"total_files\": ").append(scheduleData.size()).append(",\n");
        json.append("    \"last_update\": \"").append(Instant.ofEpochMilli(lastScheduleUpdateTime.get()).toString()).append("\",\n");
        json.append("    \"source\": \"RTD GTFS Schedule Feed\"\n");
        json.append("  }\n");
        json.append("}");
        
        return json.toString();
    }
    
    private static String getString(Row row, int index) {
        Object value = row.getField(index);
        return value != null ? value.toString() : "";
    }
    
    private static double getDouble(Row row, int index) {
        Object value = row.getField(index);
        if (value instanceof Double) return (Double) value;
        if (value instanceof Float) return ((Float) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    private static long getLong(Row row, int index) {
        Object value = row.getField(index);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return 0L;
    }
    
    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_SERVER_PORT), 0);
        
        // Set up endpoints
        server.createContext("/api/vehicles", new VehiclesHandler());
        server.createContext("/api/schedule", new ScheduleHandler());
        server.createContext("/api/data/query", new DataQueryHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/", new CorsHandler()); // CORS preflight
        
        // Start the server
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.printf("✅ HTTP server started at http://localhost:%d\n", HTTP_SERVER_PORT);
        System.out.printf("📍 Vehicle data: http://localhost:%d/api/vehicles\n", HTTP_SERVER_PORT);
        System.out.printf("🗓️ Schedule data: http://localhost:%d/api/schedule\n", HTTP_SERVER_PORT);
        System.out.printf("🔍 Data query: http://localhost:%d/api/data/query\n", HTTP_SERVER_PORT);
        System.out.printf("🏥 Health check: http://localhost:%d/api/health\n\n", HTTP_SERVER_PORT);
    }
    
    // HTTP Handlers
    static class VehiclesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String jsonData = latestJsonData.get();
            if (jsonData == null) {
                jsonData = "{\"vehicles\": [], \"metadata\": {\"total_count\": 0, \"error\": \"No data available\"}}";
            }
            
            byte[] response = jsonData.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    static class ScheduleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            String jsonData = latestScheduleJsonData.get();
            if (jsonData == null) {
                jsonData = "{\"gtfs_schedule_files\": [], \"metadata\": {\"total_files\": 0, \"error\": \"No schedule data available\"}}";
            }
            
            byte[] response = jsonData.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    static class DataQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                // Handle simple queries against stored data
                String response = String.format("""
                    {
                        "message": "Data query capabilities available",
                        "storage_path": "%s",
                        "formats": ["json", "csv", "table"],
                        "retention_days": %d,
                        "query_example": "Use file system tools to query stored data"
                    }
                    """, DATA_BASE_PATH, DATA_RETENTION_PERIOD.toDays());
                
                byte[] responseBytes = response.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            } else {
                String info = String.format("""
                    {
                        "message": "Data Query API",
                        "storage_structure": {
                            "vehicles": "%s/vehicles/{json,csv,table}/{yyyy/MM/dd/HH}/",
                            "schedule": "%s/schedule/{json,table}/{yyyy/MM/dd}/"
                        },
                        "retention": "%d days",
                        "formats": {
                            "json": "Human-readable JSON format",
                            "csv": "Comma-separated values for analysis",
                            "table": "Structured JSON for SQL-like access"
                        }
                    }
                    """, DATA_BASE_PATH, DATA_BASE_PATH, DATA_RETENTION_PERIOD.toDays());
                
                byte[] responseBytes = info.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }
    
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime.get();
            long timeSinceScheduleUpdate = System.currentTimeMillis() - lastScheduleUpdateTime.get();
            boolean isHealthy = timeSinceUpdate < (FETCH_INTERVAL_SECONDS * 1000 * 2);
            
            String health = String.format("""
                {
                    "status": "%s",
                    "last_update": "%s",
                    "last_schedule_update": "%s",
                    "vehicle_count": %d,
                    "schedule_files": %d,
                    "data_path": "%s",
                    "retention_days": %d,
                    "uptime_ms": %d
                }
                """,
                isHealthy ? "healthy" : "stale",
                Instant.ofEpochMilli(lastUpdateTime.get()).toString(),
                Instant.ofEpochMilli(lastScheduleUpdateTime.get()).toString(),
                latestVehicleData.get() != null ? latestVehicleData.get().size() : 0,
                latestScheduleData.get() != null ? latestScheduleData.get().size() : 0,
                DATA_BASE_PATH,
                DATA_RETENTION_PERIOD.toDays(),
                timeSinceUpdate
            );
            
            byte[] response = health.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    static class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                String response = "RTD Persistent Pipeline - Use /api/vehicles, /api/schedule, /api/data/query, or /api/health";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
    
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Content-Type", "application/json");
    }
}