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
 * RTD Data API Server - Provides REST API access to live RTD vehicle data
 * Serves JSON data for the React frontend
 */
public class RTDDataAPIServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDDataAPIServer.class);
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final long FETCH_INTERVAL_SECONDS = 30L;
    private static final int SERVER_PORT = 8080;
    
    private final RTDRowSource dataSource;
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<List<Row>> latestVehicleData;
    private final AtomicReference<String> latestJsonData;
    private final AtomicReference<Long> lastUpdateTime;
    
    public RTDDataAPIServer() {
        this.dataSource = new RTDRowSource(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.latestVehicleData = new AtomicReference<>();
        this.latestJsonData = new AtomicReference<>();
        this.lastUpdateTime = new AtomicReference<>(System.currentTimeMillis());
    }
    
    public static void main(String[] args) throws Exception {
        RTDDataAPIServer server = new RTDDataAPIServer();
        server.start();
    }
    
    public void start() throws IOException {
        System.out.println("=== RTD Data API Server ===");
        System.out.println("Starting HTTP server on port " + SERVER_PORT);
        System.out.println("Fetching RTD data every " + FETCH_INTERVAL_SECONDS + " seconds\n");
        
        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
        
        // Set up endpoints
        server.createContext("/api/vehicles", new VehiclesHandler());
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/", new CorsHandler()); // CORS preflight
        
        // Start the server
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        
        System.out.println("✅ Server started at http://localhost:" + SERVER_PORT);
        System.out.println("📍 Vehicle data: http://localhost:" + SERVER_PORT + "/api/vehicles");
        System.out.println("🏥 Health check: http://localhost:" + SERVER_PORT + "/api/health\n");
        
        // Start data fetching
        startDataFetching();
        
        // Keep the server running
        System.out.println("Press Ctrl+C to stop\n");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutting down RTD API Server...");
            server.stop(0);
            scheduler.shutdown();
            System.out.println("✅ Server stopped");
        }));
        
        // Keep main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("Main thread interrupted");
            Thread.currentThread().interrupt();
        }
    }
    
    private void startDataFetching() {
        // Initial fetch
        fetchLatestData();
        
        // Schedule periodic fetching
        scheduler.scheduleAtFixedRate(this::fetchLatestData, 
            FETCH_INTERVAL_SECONDS, FETCH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    private void fetchLatestData() {
        try {
            // Use reflection to access the private method
            java.lang.reflect.Method fetchMethod = RTDRowSource.class.getDeclaredMethod("fetchVehiclePositionsAsRows");
            fetchMethod.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            List<Row> vehicles = (List<Row>) fetchMethod.invoke(dataSource);
            
            if (vehicles != null && !vehicles.isEmpty()) {
                latestVehicleData.set(vehicles);
                latestJsonData.set(convertToJson(vehicles));
                lastUpdateTime.set(System.currentTimeMillis());
                
                System.out.printf("📊 Fetched %d vehicles at %s\n", 
                    vehicles.size(),
                    Instant.ofEpochMilli(lastUpdateTime.get())
                        .atZone(ZoneId.of("America/Denver"))
                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                );
            }
            
        } catch (Exception e) {
            LOG.error("Failed to fetch RTD data", e);
            System.err.println("❌ Error fetching data: " + e.getMessage());
        }
    }
    
    private String convertToJson(List<Row> vehicles) {
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
    
    private String getString(Row row, int index) {
        Object value = row.getField(index);
        return value != null ? value.toString() : "";
    }
    
    private double getDouble(Row row, int index) {
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
    
    private long getLong(Row row, int index) {
        Object value = row.getField(index);
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        return 0L;
    }
    
    // HTTP Handlers
    class VehiclesHandler implements HttpHandler {
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
    
    class HealthHandler implements HttpHandler {
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
    
    class CorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                String response = "RTD API Server - Use /api/vehicles or /api/health";
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}