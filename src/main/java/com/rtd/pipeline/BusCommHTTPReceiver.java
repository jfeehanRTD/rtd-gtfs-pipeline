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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rtd.pipeline.util.MetricsRecorder;

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
 * HTTP Receiver for Bus Communication Data (SIRI)
 * Receives SIRI XML/JSON payloads via HTTP POST and forwards them to Kafka bus topic
 * Also supports SIRI subscription management
 */
public class BusCommHTTPReceiver {
    
    private static final Logger LOG = LoggerFactory.getLogger(BusCommHTTPReceiver.class);
    
    // Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_COMM_TOPIC = "rtd.bus.siri";
    private static final int HTTP_PORT = 8082;
    private static final String HTTP_PATH = "/bus-siri";
    private static final String SUBSCRIPTION_PATH = "/subscribe";
    
    // SIRI Subscription Configuration
    private String siriHost = "http://172.23.4.136:8080";
    private String siriService = "siri";
    private long ttl = 90000; // 90 seconds default TTL
    private String authUsername = null;
    private String authPassword = null;
    
    private final KafkaProducer<String, String> kafkaProducer;
    private final HttpServer httpServer;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean subscriptionActive;
    private final ObjectMapper objectMapper;
    private final Queue<String> latestSiriData;
    
    public BusCommHTTPReceiver() throws IOException {
        // Initialize Kafka producer
        this.kafkaProducer = createKafkaProducer();
        
        // Initialize HTTP server
        this.httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);
        this.httpServer.createContext(HTTP_PATH, new BusSIRIHandler());
        this.httpServer.createContext(SUBSCRIPTION_PATH, new SubscriptionHandler());
        this.httpServer.createContext("/bus-siri/latest", new LatestDataHandler());
        this.httpServer.createContext("/health", new HealthHandler());
        this.httpServer.createContext("/status", new StatusHandler());
        this.httpServer.setExecutor(Executors.newFixedThreadPool(10));
        
        // Initialize scheduler for subscription management
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.subscriptionActive = new AtomicBoolean(false);
        this.objectMapper = new ObjectMapper();
        this.latestSiriData = new ConcurrentLinkedQueue<>();
        
        LOG.info("Bus SIRI HTTP Receiver initialized");
        LOG.info("HTTP Server: http://localhost:{}{}", HTTP_PORT, HTTP_PATH);
        LOG.info("Subscription Endpoint: http://localhost:{}{}", HTTP_PORT, SUBSCRIPTION_PATH);
        LOG.info("Kafka Topic: {}", BUS_COMM_TOPIC);
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
        LOG.info("✅ Bus SIRI HTTP Receiver started");
        LOG.info("📡 Listening for SIRI bus data at: http://localhost:{}{}", HTTP_PORT, HTTP_PATH);
        LOG.info("🔔 Subscription management at: http://localhost:{}{}", HTTP_PORT, SUBSCRIPTION_PATH);
        LOG.info("🏥 Health check available at: http://localhost:{}/health", HTTP_PORT);
        LOG.info("📊 Status available at: http://localhost:{}/status", HTTP_PORT);
        LOG.info("📨 SIRI payloads will be forwarded to Kafka topic: {}", BUS_COMM_TOPIC);
        
        // Start SIRI subscription
        startSIRISubscription();
    }
    
    /**
     * Start SIRI subscription with the configured endpoint
     */
    private void startSIRISubscription() {
        try {
            // Configure SIRI subscription parameters
            configureSIRISubscription(siriHost, siriService, ttl);
            
            // Send initial subscription request
            sendSubscriptionRequest();
            
            // Schedule periodic subscription renewal based on TTL
            long renewalInterval = ttl / 2; // Renew at half the TTL
            scheduler.scheduleAtFixedRate(
                this::sendSubscriptionRequest, 
                renewalInterval, 
                renewalInterval, 
                TimeUnit.MILLISECONDS
            );
            
            LOG.info("✅ SIRI subscription started with TTL: {} ms, renewal every: {} ms", 
                ttl, renewalInterval);
            
        } catch (Exception e) {
            LOG.error("Failed to start SIRI subscription: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Configure SIRI subscription parameters
     */
    public void configureSIRISubscription(String host, String service, long ttl) {
        this.siriHost = host;
        this.siriService = service;
        this.ttl = ttl;
        LOG.info("SIRI subscription configured: host={}, service={}, ttl={}", host, service, ttl);
    }
    
    /**
     * Configure authentication for SIRI subscription
     */
    public void configureAuthentication(String username, String password) {
        this.authUsername = username;
        this.authPassword = password;
        if (username != null && password != null) {
            LOG.info("Authentication configured for SIRI subscription");
        }
    }
    
    /**
     * Send subscription request to SIRI endpoint
     */
    private void sendSubscriptionRequest() {
        try {
            // Create subscription request payload
            ObjectNode subscriptionRequest = objectMapper.createObjectNode();
            subscriptionRequest.put("host", siriHost);
            subscriptionRequest.put("service", siriService);
            subscriptionRequest.put("ttl", ttl);
            subscriptionRequest.put("callback_url", String.format("http://localhost:%d%s", HTTP_PORT, HTTP_PATH));
            subscriptionRequest.put("subscription_type", "VehicleMonitoring");
            subscriptionRequest.put("route_filter", "bus"); // Filter for bus routes only
            
            String jsonPayload = objectMapper.writeValueAsString(subscriptionRequest);
            
            // Send subscription request
            URL url = new URL(siriHost + "/subscribe");
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
                LOG.info("✅ SIRI subscription request sent successfully");
            } else {
                LOG.warn("⚠️ SIRI subscription request returned code: {}", responseCode);
            }
            
            conn.disconnect();
            
        } catch (Exception e) {
            LOG.error("Failed to send SIRI subscription request: {}", e.getMessage());
            subscriptionActive.set(false);
        }
    }
    
    public void stop() {
        httpServer.stop(5);
        scheduler.shutdown();
        kafkaProducer.close();
        LOG.info("Bus SIRI HTTP Receiver stopped");
    }
    
    /**
     * HTTP Handler for bus SIRI payloads
     */
    private class BusSIRIHandler implements HttpHandler {
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
                // Read SIRI payload from request body
                InputStream requestBody = exchange.getRequestBody();
                String siriPayload = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                if (siriPayload.trim().isEmpty()) {
                    MetricsRecorder.recordError("siri");
                    sendErrorResponse(exchange, 400, "Empty request body");
                    return;
                }
                
                LOG.debug("Received SIRI bus payload: {}", 
                    siriPayload.length() > 200 ? siriPayload.substring(0, 200) + "..." : siriPayload);
                
                // Determine if it's XML or JSON and process accordingly
                String processedPayload = processSIRIPayload(siriPayload);
                
                // Store latest data for React app
                latestSiriData.offer(processedPayload);
                // Keep only the last 10 entries to prevent memory issues
                while (latestSiriData.size() > 10) {
                    latestSiriData.poll();
                }
                
                // Send to Kafka topic
                ProducerRecord<String, String> record = new ProducerRecord<>(BUS_COMM_TOPIC, processedPayload);
                
                kafkaProducer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        LOG.error("Failed to send message to Kafka: {}", exception.getMessage());
                        MetricsRecorder.recordError("siri");
                    } else {
                        LOG.info("Successfully sent bus SIRI data to topic {} partition {} offset {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                        
                        // Record successful connection if this looks like valid SIRI data
                        if (MetricsRecorder.isValidConnectionData(processedPayload)) {
                            MetricsRecorder.recordConnection("siri");
                        } else {
                            MetricsRecorder.recordRegularMessage("siri");
                        }
                    }
                });
                
                // Send success response
                String response = "{\"status\": \"success\", \"message\": \"Bus SIRI data received and forwarded to Kafka\"}";
                sendJsonResponse(exchange, 200, response);
                
                LOG.info("✅ Bus SIRI data forwarded to Kafka successfully");
                
            } catch (Exception e) {
                LOG.error("Error processing bus SIRI data: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
        
        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"service\": \"RTD Bus SIRI HTTP Receiver\", " +
                "\"endpoint\": \"%s\", " +
                "\"kafka_topic\": \"%s\", " +
                "\"status\": \"ready\", " +
                "\"subscription_active\": %s, " +
                "\"usage\": \"POST SIRI XML/JSON payloads to this endpoint\"}",
                HTTP_PATH, BUS_COMM_TOPIC, subscriptionActive.get()
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Process SIRI payload - convert XML to JSON if needed
     */
    private String processSIRIPayload(String payload) {
        try {
            // If it's already JSON, return as-is
            if (payload.trim().startsWith("{") || payload.trim().startsWith("[")) {
                return payload;
            }
            
            // If it's XML, convert to a simplified JSON format
            if (payload.trim().startsWith("<")) {
                // For now, wrap XML in JSON envelope
                ObjectNode jsonWrapper = objectMapper.createObjectNode();
                jsonWrapper.put("format", "xml");
                jsonWrapper.put("timestamp", System.currentTimeMillis());
                jsonWrapper.put("siri_xml", payload);
                return objectMapper.writeValueAsString(jsonWrapper);
            }
            
            return payload;
            
        } catch (Exception e) {
            LOG.error("Error processing SIRI payload: {}", e.getMessage());
            return payload; // Return original if processing fails
        }
    }
    
    /**
     * Subscription management handler
     */
    private class SubscriptionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("POST".equals(method)) {
                handleSubscriptionUpdate(exchange);
            } else if ("GET".equals(method)) {
                handleSubscriptionStatus(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        }
        
        private void handleSubscriptionUpdate(HttpExchange exchange) throws IOException {
            try {
                // Read subscription configuration
                InputStream requestBody = exchange.getRequestBody();
                String jsonPayload = new String(requestBody.readAllBytes(), StandardCharsets.UTF_8);
                
                ObjectNode config = (ObjectNode) objectMapper.readTree(jsonPayload);
                
                String host = config.has("host") ? config.get("host").asText() : siriHost;
                String service = config.has("service") ? config.get("service").asText() : siriService;
                long newTtl = config.has("ttl") ? config.get("ttl").asLong() : ttl;
                
                // Update configuration
                configureSIRISubscription(host, service, newTtl);
                
                // Send new subscription request
                sendSubscriptionRequest();
                
                String response = String.format(
                    "{\"status\": \"success\", \"message\": \"Subscription updated\", " +
                    "\"host\": \"%s\", \"service\": \"%s\", \"ttl\": %d}",
                    host, service, newTtl
                );
                sendJsonResponse(exchange, 200, response);
                
            } catch (Exception e) {
                LOG.error("Error updating subscription: {}", e.getMessage(), e);
                sendErrorResponse(exchange, 500, "Failed to update subscription: " + e.getMessage());
            }
        }
        
        private void handleSubscriptionStatus(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"subscription_active\": %s, " +
                "\"host\": \"%s\", " +
                "\"service\": \"%s\", " +
                "\"ttl\": %d, " +
                "\"renewal_interval\": %d}",
                subscriptionActive.get(), siriHost, siriService, ttl, ttl/2
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
            String response = String.format(
                "{\"status\": \"healthy\", " +
                "\"service\": \"Bus SIRI HTTP Receiver\", " +
                "\"subscription_active\": %s}",
                subscriptionActive.get()
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Status handler with detailed information
     */
    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = String.format(
                "{\"service\": \"RTD Bus SIRI HTTP Receiver\", " +
                "\"port\": %d, " +
                "\"kafka_topic\": \"%s\", " +
                "\"kafka_bootstrap\": \"%s\", " +
                "\"siri_endpoint\": \"%s\", " +
                "\"siri_service\": \"%s\", " +
                "\"ttl_ms\": %d, " +
                "\"subscription_active\": %s, " +
                "\"endpoints\": {" +
                "  \"data\": \"%s\", " +
                "  \"subscription\": \"%s\", " +
                "  \"health\": \"/health\", " +
                "  \"status\": \"/status\"" +
                "}}",
                HTTP_PORT, BUS_COMM_TOPIC, KAFKA_BOOTSTRAP_SERVERS,
                siriHost, siriService, ttl, subscriptionActive.get(),
                HTTP_PATH, SUBSCRIPTION_PATH
            );
            sendJsonResponse(exchange, 200, response);
        }
    }
    
    /**
     * Latest Data Handler - Serves the most recent SIRI data to React app
     */
    private class LatestDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            
            if ("GET".equals(method)) {
                // Return the latest SIRI data as JSON array
                String response;
                if (latestSiriData.isEmpty()) {
                    response = "[]";
                } else {
                    StringBuilder jsonArray = new StringBuilder("[");
                    boolean first = true;
                    for (String data : latestSiriData) {
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
                sendErrorResponse(exchange, 405, "Method not allowed. Use GET to retrieve latest SIRI data.");
            }
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
            BusCommHTTPReceiver receiver = new BusCommHTTPReceiver();
            
            // Check for command line arguments for SIRI configuration
            if (args.length >= 3) {
                String host = args[0];
                String service = args[1];
                long ttl = Long.parseLong(args[2]);
                receiver.configureSIRISubscription(host, service, ttl);
                
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
                String envService = System.getenv("TIS_PROXY_SERVICE");
                String envTtl = System.getenv("TIS_PROXY_TTL");
                String envUsername = System.getenv("TIS_PROXY_USERNAME");
                String envPassword = System.getenv("TIS_PROXY_PASSWORD");
                
                if (envHost != null) {
                    String service = envService != null ? envService : "siri";
                    long ttl = envTtl != null ? Long.parseLong(envTtl) : 90000;
                    receiver.configureSIRISubscription(envHost, service, ttl);
                    
                    if (envUsername != null && envPassword != null) {
                        receiver.configureAuthentication(envUsername, envPassword);
                    }
                    LOG.info("Configuration loaded from environment variables");
                }
            }
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutting down Bus SIRI HTTP Receiver...");
                receiver.stop();
            }));
            
            receiver.start();
            
            // Keep the server running
            LOG.info("Press Ctrl+C to stop the server");
            Thread.currentThread().join();
            
        } catch (Exception e) {
            LOG.error("Failed to start Bus SIRI HTTP Receiver: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}