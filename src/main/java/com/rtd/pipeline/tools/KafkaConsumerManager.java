package com.rtd.pipeline.tools;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;

/**
 * Kafka Console Consumer using modern Consumer API
 * Replaces command-line tools for Kafka 4.0.0 compatibility
 */
public class KafkaConsumerManager {
    
    private final Properties consumerProps;
    
    public KafkaConsumerManager(String bootstrapServers) {
        this.consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        String bootstrapServers = "localhost:9092";
        String topicName = null;
        boolean fromBeginning = false;
        int maxMessages = -1;
        String groupId = "kafka-console-consumer-" + UUID.randomUUID().toString();
        boolean simpleOutput = false;  // Add simple output mode
        
        // Parse arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bootstrap-server":
                    if (i + 1 < args.length) bootstrapServers = args[++i];
                    break;
                case "--topic":
                    if (i + 1 < args.length) topicName = args[++i];
                    break;
                case "--from-beginning":
                    fromBeginning = true;
                    break;
                case "--max-messages":
                    if (i + 1 < args.length) maxMessages = Integer.parseInt(args[++i]);
                    break;
                case "--group":
                    if (i + 1 < args.length) groupId = args[++i];
                    break;
                case "--simple":
                case "--value-only":
                    simpleOutput = true;  // Enable simple output mode
                    break;
            }
        }
        
        if (topicName == null) {
            System.err.println("Error: --topic is required");
            printUsage();
            System.exit(1);
        }
        
        KafkaConsumerManager manager = new KafkaConsumerManager(bootstrapServers);
        
        try {
            manager.consumeMessages(topicName, fromBeginning, maxMessages, groupId, simpleOutput);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("RTD Kafka Console Consumer (using Consumer API)");
        System.out.println("===============================================");
        System.out.println();
        System.out.println("Usage: java KafkaConsumerManager [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --bootstrap-server <server>     - Kafka bootstrap servers (default: localhost:9092)");
        System.out.println("  --topic <topic>                 - Topic to consume from (required)");
        System.out.println("  --from-beginning                - Read from beginning of topic");
        System.out.println("  --max-messages <num>            - Maximum messages to consume");
        System.out.println("  --group <group-id>              - Consumer group ID");
        System.out.println();
        System.out.println("RTD Topic Examples:");
        System.out.println("  --topic rtd.comprehensive.routes   - Complete route and vehicle data");
        System.out.println("  --topic rtd.route.summary           - Route performance statistics");
        System.out.println("  --topic rtd.vehicle.tracking        - Enhanced vehicle tracking");
        System.out.println("  --topic rtd.vehicle.positions       - Raw vehicle positions");
        System.out.println("  --topic rtd.trip.updates            - Trip delays and updates");
        System.out.println("  --topic rtd.alerts                  - Service alerts");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java KafkaConsumerManager --topic rtd.alerts --from-beginning");
        System.out.println("  java KafkaConsumerManager --topic rtd.route.summary --max-messages 10");
    }
    
    public void consumeMessages(String topicName, boolean fromBeginning, int maxMessages, String groupId, boolean simpleOutput) {
        Properties props = new Properties();
        props.putAll(consumerProps);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        
        // Ensure deserializers are explicitly set
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        
        if (fromBeginning) {
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topicName));
            
            if (!simpleOutput) {
                System.out.printf("Consuming from topic: %s%n", topicName);
                System.out.printf("Bootstrap servers: %s%n", consumerProps.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
                System.out.printf("Group ID: %s%n", groupId);
                System.out.printf("From beginning: %s%n", fromBeginning);
                if (maxMessages > 0) {
                    System.out.printf("Max messages: %d%n", maxMessages);
                }
                System.out.println("Press Ctrl+C to exit");
                System.out.println("-".repeat(50));
            }
            
            int messageCount = 0;
            long startTime = System.currentTimeMillis();
            
            // Set up shutdown hook for graceful exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down consumer...");
                consumer.wakeup();
            }));
            
            try {
                while (maxMessages == -1 || messageCount < maxMessages) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    if (records.isEmpty()) {
                        // Show a progress indicator for long waits (not in simple mode)
                        if (!simpleOutput) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            if (elapsed > 5000 && messageCount == 0) {
                                System.out.printf("Waiting for messages... (%d seconds)%n", elapsed / 1000);
                                startTime = System.currentTimeMillis(); // Reset timer
                            }
                        }
                        continue;
                    }
                    
                    for (ConsumerRecord<String, String> record : records) {
                        if (simpleOutput) {
                            // Simple output mode - just print the value
                            System.out.println(record.value());
                        } else {
                            // Full output mode with metadata
                            System.out.printf("Partition: %d, Offset: %d, Key: %s, Value: %s%n",
                                record.partition(),
                                record.offset(),
                                record.key() == null ? "null" : record.key(),
                                record.value());
                        }
                        
                        messageCount++;
                        if (maxMessages > 0 && messageCount >= maxMessages) {
                            break;
                        }
                    }
                    
                    if (maxMessages > 0 && messageCount >= maxMessages) {
                        break;
                    }
                }
            } catch (org.apache.kafka.common.errors.WakeupException e) {
                // Ignore, this is expected during shutdown
            }
            
            if (!simpleOutput) {
                System.out.printf("%nTotal messages consumed: %d%n", messageCount);
            }
        }
    }
}