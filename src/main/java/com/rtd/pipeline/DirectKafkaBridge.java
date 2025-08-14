package com.rtd.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Direct Kafka Bridge - Lightweight HTTP-to-Kafka Bridge
 * 
 * Optimized replacement for RailCommHTTPReceiver with:
 * - Async Kafka publishing for better performance
 * - Minimal HTTP processing overhead
 * - Built-in health checks and monitoring
 * - Direct topic routing without intermediate processing
 */
public class DirectKafkaBridge {
    
    private static final Logger LOG = LoggerFactory.getLogger(DirectKafkaBridge.class);
    
    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String DEFAULT_TOPIC = "rtd.rail.comm";
    private static final int HTTP_PORT = 8083; // Different port to avoid conflicts
    private static final String BRIDGE_PATH = "/kafka";
    private static final String RAIL_COMM_PATH = "/rail-comm"; // Maintain compatibility
    
    private final KafkaProducer<String, String> kafkaProducer;
    private final HttpServer httpServer;
    private final ExecutorService executorService;
    
    public DirectKafkaBridge() throws IOException {
        // Initialize optimized Kafka producer
        this.kafkaProducer = createOptimizedKafkaProducer();
        
        // Initialize HTTP server with custom thread pool
        this.executorService = Executors.newFixedThreadPool(20);
        this.httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        
        // Set up endpoints
        this.httpServer.createContext(BRIDGE_PATH, new KafkaBridgeHandler());
        this.httpServer.createContext(RAIL_COMM_PATH, new RailCommCompatHandler());
        this.httpServer.createContext("/health", new HealthHandler());
        this.httpServer.createContext("/metrics", new MetricsHandler());
        
        this.httpServer.setExecutor(executorService);
        
        LOG.info("Direct Kafka Bridge initialized");
        LOG.info("HTTP Server: http://localhost:{}", HTTP_PORT);
        LOG.info("Bridge Endpoint: {}/{{topic}}", BRIDGE_PATH);
        LOG.info("Rail Comm Endpoint: {}", RAIL_COMM_PATH);
        LOG.info("Kafka Bootstrap: {}", KAFKA_BOOTSTRAP_SERVERS);
    }
    
    private KafkaProducer<String, String> createOptimizedKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        // Optimized settings for low latency
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Required for idempotence
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 50);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5); // Small batching for low latency
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Fast compression
        
        // Additional optimizations  
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        return new KafkaProducer<>(props);
    }
    
    public void start() {
        httpServer.start();
        LOG.info("🚀 Direct Kafka Bridge started");
        LOG.info("📡 Bridge endpoint: http://localhost:{}{}/{{topic}}", HTTP_PORT, BRIDGE_PATH);
        LOG.info("🔄 Rail comm compatibility: http://localhost:{}{}", HTTP_PORT, RAIL_COMM_PATH);
        LOG.info("🏥 Health check: http://localhost:{}/health", HTTP_PORT);
        LOG.info("📊 Metrics: http://localhost:{}/metrics", HTTP_PORT);
    }
    
    public void stop() {
        httpServer.stop(5);
        executorService.shutdown();
        kafkaProducer.close();
        LOG.info("Direct Kafka Bridge stopped");
    }
    
    /**
     * Generic Kafka Bridge Handler - supports any topic via URL path
     */
    private class KafkaBridgeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            // Extract topic from URL path: /kafka/{topic}
            String topic = DEFAULT_TOPIC;
            if (path.startsWith(BRIDGE_PATH + "/")) {
                String topicPart = path.substring(BRIDGE_PATH.length() + 1);
                if (!topicPart.isEmpty()) {
                    topic = topicPart;
                }
            }
            
            if ("POST".equals(method)) {
                handlePostRequest(exchange, topic);
            } else if ("GET".equals(method)) {
                handleGetRequest(exchange, topic);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }
        
        private void handlePostRequest(HttpExchange exchange, String topic) throws IOException {
            try {
                // Read payload with optimized buffering
                InputStream requestBody = exchange.getRequestBody();
                String jsonPayload = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                if (jsonPayload.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Empty request body");
                    return;
                }
                
                LOG.debug("Bridge received payload for topic {}: {}", topic, 
                    jsonPayload.length() > 100 ? jsonPayload.substring(0, 100) + "..." : jsonPayload);
                
                // Async publish to Kafka with minimal overhead
                publishToKafkaAsync(topic, jsonPayload)
                    .thenAccept(metadata -> {
                        LOG.info("✅ Published to topic {} partition {} offset {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                    })
                    .exceptionally(throwable -> {
                        LOG.error("❌ Failed to publish to topic {}: {}", topic, throwable.getMessage());
                        return null;
                    });
                
                // Send immediate response (fire-and-forget for performance)
                String response = String.format(
                    "{\"status\": \"accepted\", \"topic\": \"%s\", \"message\": \"Payload queued for Kafka\"}",
                    topic
                );
                sendJsonResponse(exchange, 202, response);
                
            } catch (Exception e) {
                LOG.error("Error in bridge handler: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 500, "Bridge error: " + e.getMessage());
            }
        }
        
        private void handleGetRequest(HttpExchange exchange, String topic) throws IOException {
            String response = String.format(
                "{\"service\": \"Direct Kafka Bridge\", " +
                "\"endpoint\": \"%s/%s\", " +
                "\"topic\": \"%s\", " +
                "\"status\": \"ready\", " +
                "\"usage\": \"POST JSON payloads to this endpoint\"}",
                BRIDGE_PATH, topic, topic
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Rail Comm Compatibility Handler - maintains existing API contract
     */
    private class RailCommCompatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handleRailCommPost(exchange);
            } else if ("GET".equals(method)) {
                handleRailCommGet(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }
        
        private void handleRailCommPost(HttpExchange exchange) throws IOException {
            try {
                InputStream requestBody = exchange.getRequestBody();
                String jsonPayload = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                if (jsonPayload.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Empty request body");
                    return;
                }
                
                // Direct publish to rail comm topic
                publishToKafkaAsync(DEFAULT_TOPIC, jsonPayload)
                    .thenAccept(metadata -> {
                        LOG.info("✅ Rail comm data published to {} partition {} offset {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                    })
                    .exceptionally(throwable -> {
                        LOG.error("❌ Rail comm publish failed: {}", throwable.getMessage());
                        return null;
                    });
                
                // Compatible response format
                String response = "{\"status\": \"success\", \"message\": \"Rail comm data received and forwarded to Kafka\"}";
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                LOG.error("Error processing rail comm data: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
        
        private void handleRailCommGet(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"service\": \"Direct Kafka Bridge - Rail Comm\", " +
                "\"endpoint\": \"%s\", " +
                "\"kafka_topic\": \"%s\", " +
                "\"status\": \"ready\", " +
                "\"usage\": \"POST JSON payloads to this endpoint\"}",
                RAIL_COMM_PATH, DEFAULT_TOPIC
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Enhanced Health Check Handler
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Test Kafka connectivity
            boolean kafkaHealthy = testKafkaConnectivity();
            
            String status = kafkaHealthy ? "healthy" : "degraded";
            int httpStatus = kafkaHealthy ? 200 : 503;
            
            String response = String.format(
                "{\"status\": \"%s\", \"service\": \"Direct Kafka Bridge\", " +
                "\"kafka_connected\": %s, \"timestamp\": %d}",
                status, kafkaHealthy, System.currentTimeMillis()
            );
            sendJsonResponse(exchange, httpStatus, response);
        }
        
        private boolean testKafkaConnectivity() {
            try {
                // Simple test - check if producer is not closed
                return !kafkaProducer.partitionsFor(DEFAULT_TOPIC).isEmpty();
            } catch (Exception e) {
                LOG.warn("Kafka health check failed: {}", e.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Basic Metrics Handler
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"service\": \"Direct Kafka Bridge\", " +
                "\"uptime_ms\": %d, " +
                "\"kafka_bootstrap\": \"%s\", " +
                "\"default_topic\": \"%s\", " +
                "\"thread_pool_size\": %d}",
                System.currentTimeMillis() - startTime,
                KAFKA_BOOTSTRAP_SERVERS,
                DEFAULT_TOPIC,
                20
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    private final long startTime = System.currentTimeMillis();
    
    /**
     * Async Kafka publishing for better performance
     */
    private CompletableFuture<RecordMetadata> publishToKafkaAsync(String topic, String payload) {
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, payload);
        
        kafkaProducer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(exception);
            } else {
                future.complete(metadata);
            }
        });
        
        return future;
    }
    
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(responseBytes);
        }
    }
    
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = String.format("{\"error\": \"%s\"}", message);
        sendJsonResponse(exchange, statusCode, response);
    }
    
    public static void main(String[] args) {
        try {
            DirectKafkaBridge bridge = new DirectKafkaBridge();
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down Direct Kafka Bridge...");
                bridge.stop();
            }));
            
            bridge.start();
            
            // Keep the server running
            LOG.info("Press Ctrl+C to stop the bridge");
            Thread.currentThread().join();
            
        } catch (Exception e) {
            LOG.error("Failed to start Direct Kafka Bridge: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}