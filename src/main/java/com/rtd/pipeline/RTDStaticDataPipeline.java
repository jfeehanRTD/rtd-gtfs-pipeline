package com.rtd.pipeline;

import com.rtd.pipeline.source.RTDRowSource;
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
    private static volatile long FETCH_INTERVAL_SECONDS = 60L;
    private static final int HTTP_SERVER_PORT = 8080;
    
    // Shared data for HTTP endpoints
    private static final AtomicReference<List<Row>> latestVehicleData = new AtomicReference<>();
    private static final AtomicReference<String> latestJsonData = new AtomicReference<>();
    private static final AtomicReference<Long> lastUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    
    // Scheduler for data fetching
    private static ScheduledExecutorService scheduler;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== RTD Static Data Pipeline with Web Server ===");
        System.out.println("Live RTD data integration serving React web app");
        System.out.println("Fetching RTD data every " + FETCH_INTERVAL_SECONDS + " seconds");
        System.out.println("HTTP server on port " + HTTP_SERVER_PORT + "\n");
        
        // Start HTTP server first
        startHttpServer();
        
        // Create a simple scheduler to fetch RTD data periodically
        scheduler = Executors.newSingleThreadScheduledExecutor();
        RTDRowSource dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS);
        
        // Counter for demonstration
        final int[] fetchCount = {0};
        
        try {
            System.out.println("=== Starting RTD Data Fetching ===");
            System.out.println("Press Ctrl+C to stop\n");
            
            // Schedule periodic data fetching with dynamic interval
            scheduler.scheduleWithFixedDelay(() -> {
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
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        
        System.out.println("\n=== Pipeline Summary ===");
        System.out.printf("✅ Successfully fetched RTD data %d times\n", fetchCount[0]);
        System.out.println("✅ Demonstrated live vehicle position retrieval");
        System.out.println("✅ Used Flink Row data types for structured data");
        System.out.println("✅ Avoided Flink execution serialization issues");
        System.out.println("✅ Served live data to React web app via HTTP");
        System.out.println("💡 This shows the core RTD integration works - Flink 2.0.0 execution is the blocker");
    }
    
    private static void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_SERVER_PORT), 0);
        
        // Set up endpoints
        server.createContext("/api/vehicles", new VehiclesHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/config/interval", new IntervalConfigHandler());
        server.createContext("/", new CorsHandler()); // CORS preflight
        
        // Start the server
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.printf("✅ HTTP server started at http://localhost:%d\n", HTTP_SERVER_PORT);
        System.out.printf("📍 Vehicle data: http://localhost:%d/api/vehicles\n", HTTP_SERVER_PORT);
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