package com.rtd.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

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
    private static final String SUBSCRIPTION_PATH = "/subscribe";
    
    // TIS Proxy Subscription Configuration
    private String tisProxyHost = "http://tisproxy.rtd-denver.com";
    private String railcommService = "railcomm";
    private long ttl = 90000; // 90 seconds default TTL
    private String authUsername = null;
    private String authPassword = null;
    
    private final KafkaProducer<String, String> kafkaProducer;
    private final HttpServer httpServer;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean subscriptionActive;
    private final ObjectMapper objectMapper;
    private final Queue<String> latestRailData;
    
    public RailCommHTTPReceiver() throws IOException {
        // Initialize components
        this.kafkaProducer = createKafkaProducer();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.subscriptionActive = new AtomicBoolean(false);
        this.objectMapper = new ObjectMapper();
        this.latestRailData = new ConcurrentLinkedQueue<>();
        
        // Initialize HTTP server
        this.httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        this.httpServer.createContext(HTTP_PATH, new RailCommHandler());
        this.httpServer.createContext("/health", new HealthHandler());
        this.httpServer.createContext(SUBSCRIPTION_PATH, new SubscriptionHandler());
        this.httpServer.createContext("/rail-comm/latest", new LatestDataHandler());
        this.httpServer.createContext("/status", new StatusHandler());
        this.httpServer.setExecutor(Executors.newFixedThreadPool(10));
        
        LOG.info("Rail Comm HTTP Receiver initialized");
        LOG.info("HTTP Server: http://localhost:{}{}", HTTP_PORT, HTTP_PATH);
        LOG.info("Subscription Endpoint: http://localhost:{}{}", HTTP_PORT, SUBSCRIPTION_PATH);
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
        LOG.info("🔔 Subscription management at: http://localhost:{}{}", HTTP_PORT, SUBSCRIPTION_PATH);
        LOG.info("🏥 Health check available at: http://localhost:{}/health", HTTP_PORT);
        LOG.info("📊 Status available at: http://localhost:{}/status", HTTP_PORT);
        LOG.info("📨 JSON payloads will be forwarded to Kafka topic: {}", RAIL_COMM_TOPIC);
        
        // Start TIS Proxy subscription if configured
        if (authUsername != null && authPassword != null) {
            startTISProxySubscription();
        }
    }
    
    public void stop() {
        httpServer.stop(5);
        scheduler.shutdown();
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
                
                // Store latest data for React app
                latestRailData.offer(jsonPayload);
                // Keep only the last 10 entries to prevent memory issues
                while (latestRailData.size() > 10) {
                    latestRailData.poll();
                }
                
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
    
    /**
     * Configure TIS Proxy subscription parameters
     */
    public void configureTISProxySubscription(String host, String service, long ttl) {
        this.tisProxyHost = host;
        this.railcommService = service;
        this.ttl = ttl;
        LOG.info("TIS Proxy subscription configured: host={}, service={}, ttl={}", host, service, ttl);
    }
    
    /**
     * Configure authentication for TIS Proxy subscription
     */
    public void configureAuthentication(String username, String password) {
        this.authUsername = username;
        this.authPassword = password;
        if (username != null && password != null) {
            LOG.info("Authentication configured for TIS Proxy subscription");
        }
    }
    
    /**
     * Send subscription request to TIS Proxy endpoint
     */
    private void sendSubscriptionRequest() {
        try {
            // Create subscription request payload
            ObjectNode subscriptionRequest = objectMapper.createObjectNode();
            subscriptionRequest.put("host", "http://localhost:" + HTTP_PORT);
            subscriptionRequest.put("service", railcommService);
            subscriptionRequest.put("ttl", ttl);
            
            String jsonPayload = objectMapper.writeValueAsString(subscriptionRequest);
            
            // Send subscription request
            URL url = new URL(tisProxyHost + "/subscribe");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            
            // Add Basic Authentication if credentials are provided
            if (authUsername != null && authPassword != null) {
                String auth = authUsername + ":" + authPassword;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }
            
            conn.setDoOutput(true);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                subscriptionActive.set(true);
                LOG.info("✅ Rail Comm TIS Proxy subscription request sent successfully");
            } else {
                LOG.warn("⚠️ Rail Comm TIS Proxy subscription request returned code: {}", responseCode);
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            LOG.error("Failed to send Rail Comm TIS Proxy subscription request: {}", e.getMessage());
            subscriptionActive.set(false);
        }
    }
    
    /**
     * Start TIS Proxy subscription with periodic renewal
     */
    public void startTISProxySubscription() {
        try {
            LOG.info("TIS Proxy subscription configured: host={}, service={}, ttl={}", tisProxyHost, railcommService, ttl);
            
            // Send initial subscription request
            sendSubscriptionRequest();
            
            // Schedule periodic renewal (every half TTL)
            long renewalInterval = ttl / 2;
            scheduler.scheduleAtFixedRate(
                this::sendSubscriptionRequest,
                renewalInterval,
                renewalInterval,
                TimeUnit.MILLISECONDS
            );
            
            LOG.info("✅ Rail Comm TIS Proxy subscription started with TTL: {} ms, renewal every: {} ms", ttl, renewalInterval);
        } catch (Exception e) {
            LOG.error("Failed to start Rail Comm TIS Proxy subscription: {}", e.getMessage(), e);
        }
    }
    
    /**
     * HTTP Handler for subscription management
     */
    private class SubscriptionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handleSubscriptionRequest(exchange);
            } else if ("GET".equals(method)) {
                handleSubscriptionStatus(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }
        
        private void handleSubscriptionRequest(HttpExchange exchange) throws IOException {
            try {
                InputStream requestBody = exchange.getRequestBody();
                String body = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                ObjectNode request = (ObjectNode) objectMapper.readTree(body);
                String host = request.get("host").asText();
                String service = request.get("service").asText();
                long requestTtl = request.get("ttl").asLong();
                
                configureTISProxySubscription(host, service, requestTtl);
                startTISProxySubscription();
                
                ObjectNode response = objectMapper.createObjectNode();
                response.put("status", "success");
                response.put("message", "Subscription updated");
                response.put("host", host);
                response.put("service", service);
                response.put("ttl", requestTtl);
                
                sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
                
            } catch (Exception e) {
                LOG.error("Error handling subscription request: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 400, "Invalid subscription request");
            }
        }
        
        private void handleSubscriptionStatus(HttpExchange exchange) throws IOException {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("subscription_active", subscriptionActive.get());
            response.put("host", tisProxyHost);
            response.put("service", railcommService);
            response.put("ttl", ttl);
            response.put("renewal_interval", ttl / 2);
            
            sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
        }
    }
    
    /**
     * HTTP Handler for status information
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("status", "running");
            response.put("service", "Rail Comm HTTP Receiver");
            response.put("kafka_topic", RAIL_COMM_TOPIC);
            response.put("http_port", HTTP_PORT);
            response.put("subscription_active", subscriptionActive.get());
            
            sendJsonResponse(exchange, 200, objectMapper.writeValueAsString(response));
        }
    }
    
    /**
     * Latest Data Handler - Serves the most recent Rail Communication data to React app
     */
    private class LatestDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                // Return the latest rail data as JSON array
                String response;
                if (latestRailData.isEmpty()) {
                    response = "[]";
                } else {
                    StringBuilder jsonArray = new StringBuilder("[");
                    boolean first = true;
                    for (String data : latestRailData) {
                        if (!first) {
                            jsonArray.append(",");
                        }
                        jsonArray.append(data);
                        first = false;
                    }
                    jsonArray.append("]");
                    response = jsonArray.toString();
                }
                sendJsonResponse(exchange, 200, response);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET to retrieve latest rail communication data.");
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            RailCommHTTPReceiver receiver = new RailCommHTTPReceiver();
            
            // Check for command line arguments for TIS Proxy configuration
            if (args.length >= 3) {
                String host = args[0];
                String service = args[1];
                long ttl = Long.parseLong(args[2]);
                receiver.configureTISProxySubscription(host, service, ttl);
                
                // First check environment variables for credentials (most secure)
                String envUsername = System.getenv("TIS_PROXY_USERNAME");
                String envPassword = System.getenv("TIS_PROXY_PASSWORD");
                
                if (envUsername != null && envPassword != null) {
                    receiver.configureAuthentication(envUsername, envPassword);
                    LOG.info("Using authentication credentials from environment variables");
                } 
                // Fall back to command line arguments if no env vars (less secure)
                else if (args.length >= 5) {
                    String username = args[3];
                    String password = args[4];
                    receiver.configureAuthentication(username, password);
                    LOG.warn("Using authentication credentials from command line arguments (consider using environment variables instead)");
                } else {
                    LOG.info("No authentication credentials provided (neither env vars nor command line)");
                }
            } else {
                // Check if we can use environment variables for everything
                String envHost = System.getenv("TIS_PROXY_HOST");
                String envService = System.getenv("RAILCOMM_SERVICE");
                String envTtl = System.getenv("RAILCOMM_TTL");
                String envUsername = System.getenv("TIS_PROXY_USERNAME");
                String envPassword = System.getenv("TIS_PROXY_PASSWORD");
                
                if (envHost != null) {
                    String service = envService != null ? envService : "railcomm";
                    long ttl = envTtl != null ? Long.parseLong(envTtl) : 90000;
                    receiver.configureTISProxySubscription(envHost, service, ttl);
                    
                    if (envUsername != null && envPassword != null) {
                        receiver.configureAuthentication(envUsername, envPassword);
                    }
                    LOG.info("Configuration loaded from environment variables");
                }
            }
            
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