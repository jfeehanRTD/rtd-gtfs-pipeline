package com.rtd.pipeline.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simplified RTD GTFS-RT Query Client using direct Kafka consumer.
 * This version doesn't require Flink runtime dependencies.
 */
public class SimpleRTDQueryClient {
    
    // Kafka configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final Map<String, String> TOPICS = Map.of(
        "routes", "rtd.comprehensive.routes",
        "summary", "rtd.route.summary", 
        "tracking", "rtd.vehicle.tracking",
        "positions", "rtd.vehicle.positions",
        "updates", "rtd.trip.updates",
        "alerts", "rtd.alerts"
    );
    
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter timeFormatter;
    
    public SimpleRTDQueryClient() {
        this.objectMapper = new ObjectMapper();
        this.timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        SimpleRTDQueryClient client = new SimpleRTDQueryClient();
        
        try {
            String command = args[0].toLowerCase();
            int limit = getLimit(args);
            
            switch (command) {
                case "list":
                    client.listTopics();
                    break;
                case "routes":
                    client.queryTopic("routes", limit, client::formatRouteData);
                    break;
                case "summary":
                    client.queryTopic("summary", limit, client::formatSummaryData);
                    break;
                case "tracking":
                    client.queryTopic("tracking", limit, client::formatTrackingData);
                    break;
                case "positions":
                    client.queryTopic("positions", limit, client::formatPositionData);
                    break;
                case "updates":
                    client.queryTopic("updates", limit, client::formatUpdateData);
                    break;
                case "alerts":
                    client.queryTopic("alerts", limit, client::formatAlertData);
                    break;
                case "live":
                    client.liveMonitor(limit);
                    break;
                case "test":
                    client.testKafkaConnection();
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("Simple RTD GTFS-RT Query Client");
        System.out.println("===============================");
        System.out.println();
        System.out.println("Usage: java SimpleRTDQueryClient <command> [limit]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list                    - List all available topics");
        System.out.println("  routes [limit]          - Query comprehensive routes data");
        System.out.println("  summary [limit]         - Query route summary statistics");
        System.out.println("  tracking [limit]        - Query vehicle tracking data");
        System.out.println("  positions [limit]       - Query vehicle positions");
        System.out.println("  updates [limit]         - Query trip updates");
        System.out.println("  alerts [limit]          - Query active alerts");
        System.out.println("  live [limit]            - Live monitoring mode");
        System.out.println("  test                    - Test Kafka connection");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java SimpleRTDQueryClient routes 20");
        System.out.println("  java SimpleRTDQueryClient summary");
        System.out.println("  java SimpleRTDQueryClient live 10");
    }
    
    private static int getLimit(String[] args) {
        for (int i = 1; i < args.length; i++) {
            try {
                return Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                // Not a number, continue
            }
        }
        return 10; // Default limit
    }
    
    public void listTopics() {
        System.out.println("Available RTD GTFS-RT Topics");
        System.out.println("============================");
        System.out.println();
        
        String[][] topicInfo = {
            {"routes", TOPICS.get("routes"), "Complete vehicle and route data"},
            {"summary", TOPICS.get("summary"), "Route performance statistics"}, 
            {"tracking", TOPICS.get("tracking"), "Individual vehicle tracking"},
            {"positions", TOPICS.get("positions"), "Raw vehicle positions"},
            {"updates", TOPICS.get("updates"), "Trip delay updates"},
            {"alerts", TOPICS.get("alerts"), "Service alerts"}
        };
        
        System.out.printf("%-12s %-35s %s%n", "Command", "Kafka Topic", "Description");
        System.out.println("-".repeat(80));
        
        for (String[] info : topicInfo) {
            System.out.printf("%-12s %-35s %s%n", info[0], info[1], info[2]);
        }
        System.out.println();
    }
    
    public void testKafkaConnection() {
        System.out.println("Testing Kafka Connection");
        System.out.println("=======================");
        
        Properties props = createConsumerProperties();
        props.put("group.id", "test-connection");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            // Try to get metadata for topics
            Set<String> topics = consumer.listTopics().keySet();
            
            System.out.println("✓ Successfully connected to Kafka");
            System.out.println("✓ Bootstrap servers: " + KAFKA_BOOTSTRAP_SERVERS);
            System.out.println("✓ Total topics available: " + topics.size());
            
            // Check which RTD topics exist
            System.out.println("\nRTD Topic Status:");
            for (Map.Entry<String, String> entry : TOPICS.entrySet()) {
                String topicName = entry.getValue();
                boolean exists = topics.contains(topicName);
                System.out.printf("  %-35s %s%n", topicName, exists ? "✓ EXISTS" : "✗ MISSING");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Failed to connect to Kafka: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("1. Ensure Kafka is running on " + KAFKA_BOOTSTRAP_SERVERS);
            System.err.println("2. Check if the RTD pipeline is running and producing data");
            System.err.println("3. Verify firewall/network connectivity");
        }
    }
    
    public void queryTopic(String topicKey, int limit, RecordFormatter formatter) {
        String topicName = TOPICS.get(topicKey);
        if (topicName == null) {
            System.err.println("Unknown topic key: " + topicKey);
            return;
        }
        
        System.out.println("Querying Topic: " + topicName);
        System.out.println("=".repeat(16 + topicName.length()));
        System.out.println();
        
        Properties props = createConsumerProperties();
        props.put("group.id", "rtd-query-" + topicKey);
        props.put("auto.offset.reset", "latest");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topicName));
            
            AtomicInteger count = new AtomicInteger(0);
            long startTime = System.currentTimeMillis();
            
            System.out.println("Waiting for messages (timeout: 10 seconds)...");
            
            while (count.get() < limit && System.currentTimeMillis() - startTime < 10000) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    if (count.get() >= limit) break;
                    
                    try {
                        JsonNode json = objectMapper.readTree(record.value());
                        formatter.format(json, count.incrementAndGet());
                        
                    } catch (Exception e) {
                        System.err.println("Error parsing record: " + e.getMessage());
                        System.err.println("Raw data: " + record.value());
                    }
                }
            }
            
            if (count.get() == 0) {
                System.out.println("No messages received. This could mean:");
                System.out.println("1. The RTD pipeline is not running");
                System.out.println("2. No recent data in the topic");
                System.out.println("3. Topic doesn't exist yet");
                System.out.println("\nTry running 'test' command to check connectivity");
            } else {
                System.out.println("\nTotal records retrieved: " + count.get());
            }
            
        } catch (Exception e) {
            System.err.println("Error querying topic: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void liveMonitor(int limit) {
        System.out.println("RTD Live Monitor");
        System.out.println("===============");
        System.out.println("Press Ctrl+C to exit");
        System.out.println();
        
        // Monitor route summary for live updates
        while (true) {
            try {
                System.out.println("\n" + "=".repeat(60));
                System.out.println("Live Update - " + LocalDateTime.now().format(timeFormatter));
                System.out.println("=".repeat(60));
                
                queryTopic("summary", Math.min(limit, 5), this::formatSummaryDataCompact);
                
                Thread.sleep(30000); // 30 second refresh
                
            } catch (InterruptedException e) {
                System.out.println("\nLive monitoring stopped.");
                break;
            } catch (Exception e) {
                System.err.println("Error in live monitoring: " + e.getMessage());
            }
        }
    }
    
    private Properties createConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        return props;
    }
    
    @FunctionalInterface
    private interface RecordFormatter {
        void format(JsonNode json, int recordNumber);
    }
    
    private void formatRouteData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] Route: %s (%s) | Vehicle: %s | Status: %s | Delay: %s min%n",
            recordNumber,
            json.path("route_short_name").asText("N/A"),
            json.path("route_id").asText("N/A"),
            json.path("vehicle_id").asText("N/A"),
            json.path("vehicle_status").asText("N/A"),
            json.path("delay_seconds").asInt(0) / 60
        );
    }
    
    private void formatSummaryData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] %s | Vehicles: %d/%d | On-Time: %.1f%% | Avg Delay: %.1f min | Status: %s%n",
            recordNumber,
            json.path("route_short_name").asText("N/A"),
            json.path("active_vehicles").asInt(0),
            json.path("total_vehicles").asInt(0),
            json.path("on_time_performance").asDouble(0.0),
            json.path("average_delay_seconds").asDouble(0.0) / 60,
            json.path("service_status").asText("N/A")
        );
    }
    
    private void formatSummaryDataCompact(JsonNode json, int recordNumber) {
        System.out.printf("%s: %d vehicles, %.1f%% on-time, %s%n",
            json.path("route_short_name").asText("N/A"),
            json.path("active_vehicles").asInt(0),
            json.path("on_time_performance").asDouble(0.0),
            json.path("service_status").asText("N/A")
        );
    }
    
    private void formatTrackingData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] Vehicle: %s | Route: %s | Speed: %.1f km/h | Load: %s | Quality: %s%n",
            recordNumber,
            json.path("vehicle_id").asText("N/A"),
            json.path("route_id").asText("N/A"),
            json.path("speed_kmh").asDouble(0.0),
            json.path("passenger_load").asText("N/A"),
            json.path("tracking_quality").asText("N/A")
        );
    }
    
    private void formatPositionData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] Vehicle: %s | Route: %s | Position: [%.6f, %.6f] | Status: %s%n",
            recordNumber,
            json.path("vehicle_id").asText("N/A"),
            json.path("route_id").asText("N/A"),
            json.path("latitude").asDouble(0.0),
            json.path("longitude").asDouble(0.0),
            json.path("current_status").asText("N/A")
        );
    }
    
    private void formatUpdateData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] Trip: %s | Route: %s | Vehicle: %s | Delay: %d sec | Relationship: %s%n",
            recordNumber,
            json.path("trip_id").asText("N/A"),
            json.path("route_id").asText("N/A"),
            json.path("vehicle_id").asText("N/A"),
            json.path("delay_seconds").asInt(0),
            json.path("schedule_relationship").asText("N/A")
        );
    }
    
    private void formatAlertData(JsonNode json, int recordNumber) {
        System.out.printf("[%d] Alert: %s | Cause: %s | Effect: %s%n%s%n%n",
            recordNumber,
            json.path("alert_id").asText("N/A"),
            json.path("cause").asText("N/A"),
            json.path("effect").asText("N/A"),
            json.path("header_text").asText("No description")
        );
    }
}