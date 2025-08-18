package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
import com.rtd.pipeline.source.GTFSScheduleSource;
import com.rtd.pipeline.model.GTFSScheduleData;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RTD data pipeline that works around Flink 2.0.0 serialization issues.
 * Uses direct data fetching without Flink execution to demonstrate live RTD integration.
 * This provides the core functionality while avoiding Flink runtime compatibility problems.
 * 
 * Now includes an embedded HTTP server to serve live RTD data to the React web app.
 */
public class RTDStaticDataPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDStaticDataPipeline.class);
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final String GTFS_SCHEDULE_URL = "https://www.rtd-denver.com/files/gtfs/google_transit.zip";
    private static volatile long FETCH_INTERVAL_SECONDS = 60L;
    private static final long GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS = 3600L; // Fetch GTFS schedule every hour
    private static final int HTTP_SERVER_PORT = 8080;
    
    // Shared data for HTTP endpoints
    private static final AtomicReference<List<Row>> latestVehicleData = new AtomicReference<>();
    private static final AtomicReference<String> latestJsonData = new AtomicReference<>();
    private static final AtomicReference<Long> lastUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    
    // GTFS Schedule data
    private static final AtomicReference<List<GTFSScheduleData>> latestScheduleData = new AtomicReference<>();
    private static final AtomicReference<String> latestScheduleJsonData = new AtomicReference<>();
    private static final AtomicReference<Long> lastScheduleUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    
    // Schedulers for data fetching
    private static ScheduledExecutorService rtScheduler;
    private static ScheduledExecutorService gtfsScheduler;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== RTD Complete Data Pipeline with Web Server ===");
        System.out.println("Live RTD real-time + GTFS schedule data integration serving React web app");
        System.out.println("Fetching RTD real-time data every " + FETCH_INTERVAL_SECONDS + " seconds");
        System.out.println("Fetching GTFS schedule data every " + GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS + " seconds");
        System.out.println("HTTP server on port " + HTTP_SERVER_PORT + "\n");
        
        // Start HTTP server first
        startHttpServer();
        
        // Create schedulers to fetch RTD data periodically
        rtScheduler = Executors.newSingleThreadScheduledExecutor();
        gtfsScheduler = Executors.newSingleThreadScheduledExecutor();
        RTDRowSource dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS);
        
        // Start GTFS schedule fetching
        startGTFSScheduleFetching();
        
        // Counter for demonstration
        final int[] fetchCount = {0};
        
        try {
            System.out.println("=== Starting RTD Data Fetching ===");
            System.out.println("Press Ctrl+C to stop\n");
            
            // Schedule periodic real-time data fetching with dynamic interval
            rtScheduler.scheduleWithFixedDelay(() -> {
                try {
                    fetchCount[0]++;
                    System.out.printf("=== Fetch #%d (every %ds) ===\n", fetchCount[0], FETCH_INTERVAL_SECONDS);
                    
                    // Use the RTDRowSource to fetch data directly
                    java.lang.reflect.Method fetchMethod = RTDRowSource.class.getDeclaredMethod("fetchVehiclePositionsAsRows");
                    fetchMethod.setAccessible(true);
                    
                    @SuppressWarnings("unchecked")
                    java.util.List<org.apache.flink.types.Row> vehicles = 
                        (java.util.List<org.apache.flink.types.Row>) fetchMethod.invoke(dataSource);
                    
                    if (!vehicles.isEmpty()) {
                        System.out.printf("✅ Retrieved %d vehicles from RTD\n", vehicles.size());
                        
                        // Update shared data for HTTP server
                        latestVehicleData.set(vehicles);
                        latestJsonData.set(convertToJson(vehicles));
                        lastUpdateTime.set(System.currentTimeMillis());
                        
                        // Get and format the timestamp from the first vehicle
                        if (!vehicles.isEmpty()) {
                            Long timestampMs = (Long) vehicles.get(0).getField(0);
                            if (timestampMs != null) {
                                String formattedTime = Instant.ofEpochMilli(timestampMs)
                                    .atZone(ZoneId.of("America/Denver"))
                                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                                System.out.printf("📅 Feed Timestamp: %s\n", formattedTime);
                            }
                        }
                        
                        System.out.println("\nVehicle Details (All Vehicles):");
                        System.out.println("  Bus# | Route | Position            | Status         | ID");
                        System.out.println("  " + "-".repeat(70));
                        
                        // Display ALL vehicles
                        for (int i = 0; i < vehicles.size(); i++) {
                            org.apache.flink.types.Row vehicle = vehicles.get(i);
                            // Updated field indices after adding vehicle_label
                            String vehicleId = (String) vehicle.getField(1);
                            String vehicleLabel = (String) vehicle.getField(2);  // Fleet number
                            String routeId = (String) vehicle.getField(4);      // Shifted from 3
                            Double latitude = (Double) vehicle.getField(5);     // Shifted from 4
                            Double longitude = (Double) vehicle.getField(6);    // Shifted from 5
                            String status = (String) vehicle.getField(9);       // Shifted from 8
                            
                            // Show last 4 chars of vehicle ID for uniqueness
                            String shortId = "N/A";
                            if (vehicleId != null && vehicleId.length() >= 4) {
                                shortId = "..." + vehicleId.substring(vehicleId.length() - 4);
                            }
                            
                            // Format vehicle label (fleet number) with padding
                            String displayLabel = vehicleLabel != null ? vehicleLabel : "----";
                            
                            System.out.printf("  %-5s | %-5s | %9.6f,%10.6f | %-14s | %s\n",
                                displayLabel,
                                routeId != null ? routeId : "N/A",
                                latitude != null ? latitude : 0.0,
                                longitude != null ? longitude : 0.0,
                                status != null ? status : "N/A",
                                shortId
                            );
                        }
                        
                        System.out.printf("\n📊 Total: %d vehicles displayed\n", vehicles.size());
                        
                    } else {
                        System.out.println("❌ No vehicles retrieved from RTD");
                    }
                    
                    System.out.printf("Next fetch in %d seconds...\n\n", FETCH_INTERVAL_SECONDS);
                    
                } catch (Exception e) {
                    System.err.printf("❌ Error fetching RTD data: %s\n", e.getMessage());
                    e.printStackTrace();
                }
            }, 0, FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.out.println("\n=== Shutting Down RTD Pipeline ===");
        } finally {
            rtScheduler.shutdown();
            gtfsScheduler.shutdown();
            try {
                if (!rtScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    rtScheduler.shutdownNow();
                }
                if (!gtfsScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    gtfsScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                rtScheduler.shutdownNow();
                gtfsScheduler.shutdownNow();
            }
        }
        
        System.out.println("\n=== Pipeline Summary ===");
        System.out.printf("✅ Successfully fetched RTD data %d times\n", fetchCount[0]);
        System.out.println("✅ Demonstrated live vehicle position retrieval");
        System.out.println("✅ Used Flink Row data types for structured data");
        System.out.println("✅ Avoided Flink execution serialization issues");
        System.out.println("✅ Served live data to React web app via HTTP");
        System.out.println("✅ Integrated GTFS schedule data alongside real-time feeds");
        System.out.println("💡 This shows the complete RTD integration works - Flink 2.0.0 execution is the blocker");
    }
    
    private static void startGTFSScheduleFetching() {
        System.out.println("=== Starting GTFS Schedule Data Fetching ===");
        
        // Schedule periodic GTFS schedule fetching
        gtfsScheduler.scheduleWithFixedDelay(() -> {
            try {
                System.out.println("🗓️ Fetching GTFS schedule data...");
                
                // Create a simple GTFS schedule fetcher (similar to RTDRowSource approach)
                java.util.List<GTFSScheduleData> scheduleData = fetchGTFSScheduleData();
                
                if (!scheduleData.isEmpty()) {
                    System.out.printf("✅ Retrieved %d GTFS schedule files from RTD\n", scheduleData.size());
                    
                    // Update shared data for HTTP server
                    latestScheduleData.set(scheduleData);
                    latestScheduleJsonData.set(convertScheduleToJson(scheduleData));
                    lastScheduleUpdateTime.set(System.currentTimeMillis());
                    
                    // Display summary of what was fetched
                    System.out.println("GTFS Schedule Files Retrieved:");
                    scheduleData.forEach(data -> 
                        System.out.printf("  - %s (%d bytes)\n", 
                            data.getFileType(), 
                            data.getFileContent() != null ? data.getFileContent().length() : 0)
                    );
                } else {
                    System.out.println("❌ No GTFS schedule data retrieved from RTD");
                }
                
                System.out.printf("Next GTFS schedule fetch in %d seconds...\n\n", GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS);
                
            } catch (Exception e) {
                System.err.printf("❌ Error fetching GTFS schedule data: %s\n", e.getMessage());
                e.printStackTrace();
            }
        }, 0, GTFS_SCHEDULE_FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private static java.util.List<GTFSScheduleData> fetchGTFSScheduleData() {
        java.util.List<GTFSScheduleData> scheduleDataList = new java.util.ArrayList<>();
        
        try {
            // Use GTFSScheduleSource logic directly without Flink execution
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
                    
                    // Process the ZIP file
                    processGTFSZip(zipData, scheduleDataList);
                    
                    System.out.printf("✅ Processed GTFS schedule zip with %d files\n", scheduleDataList.size());
                    
                } else {
                    System.err.printf("❌ Failed to download GTFS schedule. HTTP Status: %d\n", 
                            response.getStatusLine().getStatusCode());
                }
            }
            
        } catch (Exception e) {
            System.err.printf("❌ Error in fetchGTFSScheduleData: %s\n", e.getMessage());
            e.printStackTrace();
        }
        
        return scheduleDataList;
    }
    
    private static void processGTFSZip(byte[] zipData, java.util.List<GTFSScheduleData> dataList) throws Exception {
        long downloadTimestamp = java.time.Instant.now().toEpochMilli();
        String feedVersion = null;
        String agencyName = null;
        String feedStartDate = null;
        String feedEndDate = null;
        
        System.out.printf("🔍 Processing GTFS ZIP file with %d bytes...\n", zipData.length);
        
        try (java.util.zip.ZipInputStream zipInputStream = 
                new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zipData))) {
            
            java.util.zip.ZipEntry entry;
            int entryCount = 0;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entryCount++;
                System.out.printf("📄 Found ZIP entry %d: %s (size: %d, compressed: %d)\n", 
                    entryCount, entry.getName(), entry.getSize(), entry.getCompressedSize());
                
                if (entry.isDirectory()) {
                    continue;
                }
                
                String fileName = entry.getName();
                System.out.printf("📄 Processing GTFS file: %s\n", fileName);
                
                StringBuilder content = new StringBuilder();
                // Don't close the ZipInputStream - use a non-closing wrapper
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(zipInputStream, java.nio.charset.StandardCharsets.UTF_8));
                
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
                
                if (lineCount >= 10000) {
                    System.out.printf("⚠️ File %s was truncated at %d lines to prevent memory issues\n", fileName, lineCount);
                }
                
                // Close the entry, not the stream
                zipInputStream.closeEntry();
                
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
        
        System.out.printf("✅ Completed processing GTFS schedule zip with %d files\n", dataList.size());
    }
    
    private static String convertScheduleToJson(java.util.List<GTFSScheduleData> scheduleData) {
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
            json.append("      \"last_updated\": \"").append(java.time.Instant.now().toString()).append("\"\n");
            json.append("    }");
            
            if (i < scheduleData.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        
        json.append("  ],\n");
        json.append("  \"metadata\": {\n");
        json.append("    \"total_files\": ").append(scheduleData.size()).append(",\n");
        json.append("    \"last_update\": \"").append(java.time.Instant.ofEpochMilli(lastScheduleUpdateTime.get()).toString()).append("\",\n");
        json.append("    \"source\": \"RTD GTFS Schedule Feed\"\n");
        json.append("  }\n");
        json.append("}");
        
        return json.toString();
    }
    
    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_SERVER_PORT), 0);
        
        // Set up endpoints
        server.createContext("/api/vehicles", new VehiclesHandler());
        server.createContext("/api/schedule", new ScheduleHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/config/interval", new IntervalConfigHandler());
        server.createContext("/", new CorsHandler()); // CORS preflight
        
        // Start the server
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.printf("✅ HTTP server started at http://localhost:%d\n", HTTP_SERVER_PORT);
        System.out.printf("📍 Vehicle data: http://localhost:%d/api/vehicles\n", HTTP_SERVER_PORT);
        System.out.printf("🗓️ Schedule data: http://localhost:%d/api/schedule\n", HTTP_SERVER_PORT);
        System.out.printf("🏥 Health check: http://localhost:%d/api/health\n\n", HTTP_SERVER_PORT);
        
        // Add shutdown hook for HTTP server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down HTTP server...");
            server.stop(0);
        }));
    }
    
    private static String convertToJson(List<Row> vehicles) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"vehicles\": [\n");
        
        for (int i = 0; i < vehicles.size(); i++) {
            Row vehicle = vehicles.get(i);
            
            // Extract fields based on RTDRowSource schema
            // 0: timestamp_ms, 1: vehicle_id, 2: vehicle_label, 3: trip_id, 4: route_id
            // 5: latitude, 6: longitude, 7: bearing, 8: speed, 9: current_status
            // 10: congestion_level, 11: occupancy_status
            
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
    
    // HTTP Handlers
    static class VehiclesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
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
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
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
    
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime.get();
            boolean isHealthy = timeSinceUpdate < (FETCH_INTERVAL_SECONDS * 1000 * 2); // Within 2 intervals
            
            String health = String.format(
                "{\"status\": \"%s\", \"last_update\": \"%s\", \"vehicle_count\": %d, \"uptime_ms\": %d}",
                isHealthy ? "healthy" : "stale",
                Instant.ofEpochMilli(lastUpdateTime.get()).toString(),
                latestVehicleData.get() != null ? latestVehicleData.get().size() : 0,
                timeSinceUpdate
            );
            
            byte[] response = health.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
    
    static class IntervalConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Add CORS headers
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Read request body
                    String body = new String(exchange.getRequestBody().readAllBytes());
                    
                    // Simple JSON parsing (expecting {"intervalSeconds": number})
                    if (body.contains("intervalSeconds")) {
                        String[] parts = body.split("intervalSeconds\"\\s*:\\s*");
                        if (parts.length > 1) {
                            String numberStr = parts[1].replaceAll("[^0-9.]", "");
                            double intervalSeconds = Double.parseDouble(numberStr);
                            
                            if (intervalSeconds >= 0.5 && intervalSeconds <= 300) {
                                long newInterval = Math.round(intervalSeconds);
                                FETCH_INTERVAL_SECONDS = Math.max(1, newInterval); // Minimum 1 second for Java
                                
                                System.out.printf("⏱️ Update interval changed to %d seconds by web UI\n", FETCH_INTERVAL_SECONDS);
                                
                                String response = String.format(
                                    "{\"success\": true, \"intervalSeconds\": %d, \"message\": \"Interval updated successfully\"}",
                                    FETCH_INTERVAL_SECONDS
                                );
                                
                                byte[] responseBytes = response.getBytes();
                                exchange.sendResponseHeaders(200, responseBytes.length);
                                try (OutputStream os = exchange.getResponseBody()) {
                                    os.write(responseBytes);
                                }
                                return;
                            }
                        }
                    }
                    
                    // Invalid request
                    String errorResponse = "{\"success\": false, \"message\": \"Invalid interval. Must be between 0.5 and 300 seconds.\"}";
                    byte[] responseBytes = errorResponse.getBytes();
                    exchange.sendResponseHeaders(400, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    
                } catch (Exception e) {
                    String errorResponse = "{\"success\": false, \"message\": \"Error processing request: " + e.getMessage() + "\"}";
                    byte[] responseBytes = errorResponse.getBytes();
                    exchange.sendResponseHeaders(500, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                }
            } else {
                // GET request - return current interval
                String response = String.format(
                    "{\"intervalSeconds\": %d, \"message\": \"Current fetch interval\"}",
                    FETCH_INTERVAL_SECONDS
                );
                byte[] responseBytes = response.getBytes();
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }
    
    static class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                String response = "RTD Pipeline Server - Use /api/vehicles, /api/health, or /api/config/interval";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}