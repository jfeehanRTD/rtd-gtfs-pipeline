package com.rtd.pipeline;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * HTTP Receiver for Rail Communication Data
 * Receives JSON payloads via HTTP POST and forwards them to Kafka rail comm topic
 */
public class RailCommHTTPReceiver {
    
    private static final Logger LOG = LoggerFactory.getLogger(RailCommHTTPReceiver.class);
    
    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final int HTTP_PORT = 8081;
    private static final String HTTP_PATH = "/rail-comm";
    
    private final KafkaProducer<String, String> kafkaProducer;
    private final HttpServer httpServer;
    
    public RailCommHTTPReceiver() throws IOException {
        // Initialize Kafka producer
        this.kafkaProducer = createKafkaProducer();
        
        // Initialize HTTP server
        this.httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        this.httpServer.createContext(HTTP_PATH, new RailCommHandler());
        this.httpServer.createContext("/health", new HealthHandler());
        this.httpServer.setExecutor(Executors.newFixedThreadPool(10));
        
        LOG.info("Rail Comm HTTP Receiver initialized");
        LOG.info("HTTP Server: http://localhost:{}{}", HTTP_PORT, HTTP_PATH);
        LOG.info("Kafka Topic: {}", RAIL_COMM_TOPIC);
        LOG.info("Kafka Bootstrap: {}", KAFKA_BOOTSTRAP_SERVERS);
    }
    
    private KafkaProducer<String, String> createKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        
        return new KafkaProducer<>(props);
    }
    
    public void start() {
        httpServer.start();
        LOG.info("✅ Rail Comm HTTP Receiver started");
        LOG.info("📡 Listening for rail comm data at: http://localhost:{}{}", HTTP_PORT, HTTP_PATH);
        LOG.info("🏥 Health check available at: http://localhost:{}/health", HTTP_PORT);
        LOG.info("📨 JSON payloads will be forwarded to Kafka topic: {}", RAIL_COMM_TOPIC);
    }
    
    public void stop() {
        httpServer.stop(5);
        kafkaProducer.close();
        LOG.info("Rail Comm HTTP Receiver stopped");
    }
    
    /**
     * HTTP Handler for rail communication JSON payloads
     */
    private class RailCommHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handlePostRequest(exchange);
            } else if ("GET".equals(method)) {
                handleGetRequest(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }
        
        private void handlePostRequest(HttpExchange exchange) throws IOException {
            try {
                // Read JSON payload from request body
                InputStream requestBody = exchange.getRequestBody();
                String jsonPayload = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                if (jsonPayload.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Empty request body");
                    return;
                }
                
                LOG.debug("Received rail comm payload: {}", jsonPayload);
                
                // Send to Kafka topic
                ProducerRecord<String, String> record = new ProducerRecord<>(RAIL_COMM_TOPIC, jsonPayload);
                
                kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        LOG.error("Failed to send message to Kafka: {}", exception.getMessage());
                    } else {
                        LOG.info("Successfully sent rail comm data to topic {} partition {} offset {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                    }
                });
                
                // Send success response
                String response = "{\"status\": \"success\", \"message\": \"Rail comm data received and forwarded to Kafka\"}";
                sendJsonResponse(exchange, 200, response);
                
                LOG.info("✅ Rail comm data forwarded to Kafka successfully");
                
            } catch (Exception e) {
                LOG.error("Error processing rail comm data: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
        
        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"service\": \"RTD Rail Comm HTTP Receiver\", " +
                "\"endpoint\": \"%s\", " +
                "\"kafka_topic\": \"%s\", " +
                "\"status\": \"ready\", " +
                "\"usage\": \"POST JSON payloads to this endpoint\"}",
                HTTP_PATH, RAIL_COMM_TOPIC
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Health check handler
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "{\"status\": \"healthy\", \"service\": \"Rail Comm HTTP Receiver\"}";
            sendJsonResponse(exchange, 200, response);
        }
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
            RailCommHTTPReceiver receiver = new RailCommHTTPReceiver();
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down Rail Comm HTTP Receiver...");
                receiver.stop();
            }));
            
            receiver.start();
            
            // Keep the server running
            LOG.info("Press Ctrl+C to stop the server");
            Thread.currentThread().join();
            
        } catch (Exception e) {
            LOG.error("Failed to start Rail Comm HTTP Receiver: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}