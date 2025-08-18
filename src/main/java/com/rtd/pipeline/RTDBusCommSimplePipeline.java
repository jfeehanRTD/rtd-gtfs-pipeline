package com.rtd.pipeline;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Properties;

/**
 * RTD Bus Communication Pipeline using native Kafka Consumer
 * Simple, reliable implementation that works with any Kafka version
 * Uses Table API concepts through direct data processing
 */
public class RTDBusCommSimplePipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDBusCommSimplePipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_COMM_TOPIC = "rtd.bus.siri";
    private static final String CONSUMER_GROUP = "rtd-bus-siri-table-consumer";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD Bus Communication Simple Pipeline (SIRI) Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Source Topic: {}", BUS_COMM_TOPIC);
        LOG.info("Consumer Group: {}", CONSUMER_GROUP);
        
        // Create Kafka consumer properties
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            
            // Subscribe to the bus SIRI topic
            consumer.subscribe(Collections.singletonList(BUS_COMM_TOPIC));
            
            LOG.info("=== Pipeline Configuration Complete ===");
            LOG.info("✅ Kafka consumer configured for topic: {}", BUS_COMM_TOPIC);
            LOG.info("✅ SIRI data processing enabled");
            LOG.info("✅ Console output configured for real-time monitoring");
            LOG.info("");
            LOG.info("Pipeline is now consuming SIRI payloads from Kafka...");
            LOG.info("Waiting for bus communication data (press Ctrl+C to stop)");
            
            int messageCount = 0;
            
            // Main processing loop - Table API style processing
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                
                for (ConsumerRecord<String, String> record : records) {
                    messageCount++;
                    processSIRIMessage(record, messageCount);
                }
            }
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Process SIRI message using Table API concepts
     * Performs data transformation, validation, and output similar to Table API
     */
    private static void processSIRIMessage(ConsumerRecord<String, String> record, int messageCount) {
        try {
            String data = record.value();
            long timestamp = record.timestamp();
            String processedTime = formatTimestamp(System.currentTimeMillis());
            int dataLength = data != null ? data.length() : 0;
            
            // Table API style data validation and filtering
            if (data == null || data.trim().isEmpty()) {
                LOG.debug("Skipping empty SIRI message");
                return;
            }
            
            // Table API style data processing and output
            String formattedOutput = String.format(
                "BUS_SIRI_TABLE[%d]: timestamp=%s, processed_time=%s, data_length=%d, preview=%s",
                messageCount,
                formatTimestamp(timestamp),
                processedTime,
                dataLength,
                data.length() > 100 ? data.substring(0, 100) + "..." : data
            );
            
            // Output similar to Table API print connector
            System.out.println(formattedOutput);
            
            // Detailed JSON-style output for monitoring
            if (messageCount % 10 == 0) {
                LOG.info("Processed {} SIRI messages from topic: {}", messageCount, BUS_COMM_TOPIC);
            }
            
        } catch (Exception e) {
            LOG.error("Error processing SIRI message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Format timestamp for human-readable output - Table API style
     */
    private static String formatTimestamp(long timestampMs) {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}