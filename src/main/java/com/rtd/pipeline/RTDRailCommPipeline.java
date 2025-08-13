package com.rtd.pipeline;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.apache.flink.configuration.MemorySize;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.Schema;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * RTD Rail Communication Pipeline
 * Consumes JSON payloads from Kafka topic and processes them through Flink with data sinks
 */
public class RTDRailCommPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDRailCommPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String CONSUMER_GROUP = "rtd-rail-comm-consumer";
    
    // Data Sink Configuration
    private static final String OUTPUT_TABLE = "rail_comm_sink";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD Rail Communication Pipeline Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Source Topic: {}", RAIL_COMM_TOPIC);
        LOG.info("Consumer Group: {}", CONSUMER_GROUP);
        
        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every minute
        
        // Create Table Environment for SQL-like operations
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        
        try {
            // Create Kafka source for consuming JSON payloads
            KafkaSource<String> kafkaSource = createKafkaSource();
            
            // Create DataStream from Kafka source
            DataStream<String> jsonStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "Rail Comm Kafka Source"
            );
            
            // Parse JSON and convert to structured Row format
            DataStream<Row> railCommStream = jsonStream
                .map(jsonString -> parseRailCommJson(jsonString))
                .filter(row -> row != null)
                .name("Parse Rail Comm JSON");
            
            // Add file sink for data persistence
            railCommStream
                .map(row -> formatRowAsJson(row))
                .sinkTo(FileSink
                    .forRowFormat(new Path("./data/rail-comm/"), new SimpleStringEncoder<String>("UTF-8"))
                    .withRollingPolicy(DefaultRollingPolicy.builder()
                        .withRolloverInterval(Duration.ofMinutes(15))
                        .withInactivityInterval(Duration.ofMinutes(5))
                        .withMaxPartSize(MemorySize.ofMebiBytes(128))
                        .build())
                    .build())
                .name("Rail Comm File Sink");
            
            // Add console sink for real-time monitoring and data verification
            railCommStream
                .map(row -> formatRowAsJson(row))
                .print("RAIL_COMM_JSON");
            
            // Add formatted console output
            railCommStream
                .map(row -> formatRowForConsole(row))
                .print("RAIL_COMM_FORMATTED");
            
            LOG.info("=== Pipeline Configuration Complete ===");
            LOG.info("✅ Kafka source configured for topic: {}", RAIL_COMM_TOPIC);
            LOG.info("✅ JSON parsing and Row conversion enabled");
            LOG.info("✅ File sink configured for data persistence");
            LOG.info("✅ Console sink configured for real-time monitoring");
            LOG.info("");
            LOG.info("Pipeline is now consuming JSON payloads from Kafka...");
            LOG.info("Data will be persisted to: ./data/rail-comm/");
            LOG.info("Monitor console output for real-time rail communication data");
            
            // Execute the pipeline
            env.execute("RTD Rail Communication Pipeline");
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Creates Kafka source for consuming rail communication JSON payloads
     */
    private static KafkaSource<String> createKafkaSource() {
        return KafkaSource.<String>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(RAIL_COMM_TOPIC)
            .setGroupId(CONSUMER_GROUP)
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
    }
    
    /**
     * Parses rail communication JSON payload and converts to Flink Row
     */
    private static Row parseRailCommJson(String jsonString) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(jsonString);
            
            Row row = new Row(12);
            
            // Extract fields from JSON with defaults
            row.setField(0, System.currentTimeMillis()); // timestamp_ms
            row.setField(1, getStringValue(jsonNode, "train_id", "UNKNOWN"));
            row.setField(2, getStringValue(jsonNode, "line_id", "UNKNOWN"));
            row.setField(3, getStringValue(jsonNode, "direction", "UNKNOWN"));
            row.setField(4, getDoubleValue(jsonNode, "latitude", 0.0));
            row.setField(5, getDoubleValue(jsonNode, "longitude", 0.0));
            row.setField(6, getDoubleValue(jsonNode, "speed_mph", 0.0));
            row.setField(7, getStringValue(jsonNode, "status", "UNKNOWN"));
            row.setField(8, getStringValue(jsonNode, "next_station", ""));
            row.setField(9, getIntValue(jsonNode, "delay_seconds", 0));
            row.setField(10, getStringValue(jsonNode, "operator_message", ""));
            row.setField(11, jsonString); // Store original JSON for reference
            
            LOG.debug("Parsed rail comm data: train_id={}, line_id={}, status={}", 
                row.getField(1), row.getField(2), row.getField(7));
            
            return row;
            
        } catch (Exception e) {
            LOG.error("Failed to parse JSON: {}", jsonString, e);
            return null;
        }
    }
    
    /**
     * Helper method to extract string values from JSON
     */
    private static String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : defaultValue;
    }
    
    /**
     * Helper method to extract double values from JSON
     */
    private static Double getDoubleValue(JsonNode node, String fieldName, Double defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asDouble() : defaultValue;
    }
    
    /**
     * Helper method to extract integer values from JSON
     */
    private static Integer getIntValue(JsonNode node, String fieldName, Integer defaultValue) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asInt() : defaultValue;
    }
    
    /**
     * Formats a Row as JSON for file persistence
     */
    private static String formatRowAsJson(Row row) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"timestamp_ms\":").append(row.getField(0)).append(",");
            json.append("\"train_id\":\"").append(row.getField(1)).append("\",");
            json.append("\"line_id\":\"").append(row.getField(2)).append("\",");
            json.append("\"direction\":\"").append(row.getField(3)).append("\",");
            json.append("\"latitude\":").append(row.getField(4)).append(",");
            json.append("\"longitude\":").append(row.getField(5)).append(",");
            json.append("\"speed_mph\":").append(row.getField(6)).append(",");
            json.append("\"status\":\"").append(row.getField(7)).append("\",");
            json.append("\"next_station\":\"").append(row.getField(8)).append("\",");
            json.append("\"delay_seconds\":").append(row.getField(9)).append(",");
            json.append("\"operator_message\":\"").append(row.getField(10)).append("\",");
            json.append("\"raw_json\":").append(row.getField(11));
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            LOG.error("Error formatting row as JSON: {}", e.getMessage());
            return "{\"error\":\"Failed to format row\"}";
        }
    }
    
    /**
     * Formats a Row for console output
     */
    private static String formatRowForConsole(Row row) {
        try {
            return String.format("RAIL_COMM: [%s] Train=%s Line=%s Status=%s Position=%.6f,%.6f Speed=%.1f mph", 
                formatTimestamp((Long) row.getField(0)),
                row.getField(1), row.getField(2), row.getField(7),
                row.getField(4), row.getField(5), row.getField(6));
        } catch (Exception e) {
            LOG.error("Error formatting row for console: {}", e.getMessage());
            return "RAIL_COMM: [ERROR] Failed to format row";
        }
    }
    
    /**
     * Formats timestamp for human-readable output
     */
    private static String formatTimestamp(long timestampMs) {
        return Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}