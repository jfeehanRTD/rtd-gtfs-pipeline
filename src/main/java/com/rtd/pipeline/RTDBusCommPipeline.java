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
import org.apache.flink.types.Row;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.FilterFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * RTD Bus Communication Pipeline
 * Consumes SIRI XML/JSON payloads from Kafka topic and processes them through Flink with data sinks
 * SIRI (Service Interface for Real-time Information) is the standard for real-time bus information
 */
public class RTDBusCommPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDBusCommPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_COMM_TOPIC = "rtd.bus.siri";
    private static final String CONSUMER_GROUP = "rtd-bus-comm-consumer";
    
    // Data Sink Configuration
    private static final String OUTPUT_TABLE = "bus_comm_sink";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD Bus Communication Pipeline (SIRI) Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Source Topic: {}", BUS_COMM_TOPIC);
        LOG.info("Consumer Group: {}", CONSUMER_GROUP);
        
        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every minute
        
        try {
            // Create Kafka source for consuming SIRI payloads
            KafkaSource<String> kafkaSource;
            try {
                kafkaSource = createKafkaSource();
            } catch (Exception e) {
                LOG.error("Failed to create Kafka source: {}", e.getMessage());
                LOG.info("Make sure Kafka is running on {} and topic {} exists", KAFKA_BOOTSTRAP_SERVERS, BUS_COMM_TOPIC);
                throw new RuntimeException("Kafka connection failed. Start Kafka with: ./rtd-control.sh docker start", e);
            }
            
            // Create DataStream from Kafka source
            DataStream<String> siriStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "Bus SIRI Kafka Source"
            );
            
            // Parse SIRI data and convert to structured Row format
            DataStream<Row> busCommStream = siriStream
                .map(new SIRIParsingFunction())
                .filter(new NonNullRowFilter())
                .name("Parse SIRI Bus Data");
            
            // Add file sink for data persistence
            busCommStream
                .map(new JSONFormattingFunction())
                .sinkTo(FileSink
                    .forRowFormat(new Path("./data/bus-comm/"), new SimpleStringEncoder<String>("UTF-8"))
                    .withRollingPolicy(DefaultRollingPolicy.builder()
                        .withRolloverInterval(Duration.ofMinutes(15))
                        .withInactivityInterval(Duration.ofMinutes(5))
                        .withMaxPartSize(MemorySize.ofMebiBytes(128))
                        .build())
                    .build())
                .name("Bus SIRI File Sink");
            
            // Add console sink for real-time monitoring and data verification
            busCommStream
                .map(new JSONFormattingFunction())
                .print("BUS_SIRI_JSON");
            
            // Add formatted console output
            busCommStream
                .map(new ConsoleFormattingFunction())
                .print("BUS_SIRI_FORMATTED");
            
            LOG.info("=== Pipeline Configuration Complete ===");
            LOG.info("✅ Kafka source configured for topic: {}", BUS_COMM_TOPIC);
            LOG.info("✅ SIRI parsing and Row conversion enabled");
            LOG.info("✅ File sink configured for data persistence");
            LOG.info("✅ Console sink configured for real-time monitoring");
            LOG.info("");
            LOG.info("Pipeline is now consuming SIRI payloads from Kafka...");
            LOG.info("Data will be persisted to: ./data/bus-comm/");
            LOG.info("Monitor console output for real-time bus communication data");
            
            // Execute the pipeline
            env.execute("RTD Bus Communication Pipeline (SIRI)");
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Creates Kafka source for consuming bus SIRI payloads
     */
    private static KafkaSource<String> createKafkaSource() {
        return KafkaSource.<String>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(BUS_COMM_TOPIC)
            .setGroupId(CONSUMER_GROUP)
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
    }
    
    /**
     * Parses SIRI XML/JSON payload and converts to Flink Row
     * SIRI can be either XML or JSON format - this handles both
     */
    private static Row parseSIRIData(String siriData) {
        try {
            Row row = new Row(14);
            
            // Check if it's XML or JSON
            if (siriData.trim().startsWith("<")) {
                // Parse as XML SIRI format
                parseSIRIXML(siriData, row);
            } else {
                // Parse as JSON (similar to rail but with SIRI structure)
                parseSIRIJSON(siriData, row);
            }
            
            return row;
            
        } catch (Exception e) {
            LOG.error("Failed to parse SIRI data: {}", siriData, e);
            return null;
        }
    }
    
    /**
     * Parse SIRI XML format
     */
    private static void parseSIRIXML(String xmlData, Row row) throws Exception {
        XmlMapper xmlMapper = new XmlMapper();
        JsonNode rootNode = xmlMapper.readTree(xmlData);
        
        // Extract SIRI VehicleMonitoring data
        JsonNode vehicleActivity = rootNode.path("ServiceDelivery")
            .path("VehicleMonitoringDelivery")
            .path("VehicleActivity");
        
        if (!vehicleActivity.isMissingNode()) {
            JsonNode monitoredVehicleJourney = vehicleActivity.path("MonitoredVehicleJourney");
            
            row.setField(0, System.currentTimeMillis()); // timestamp_ms
            row.setField(1, getStringValue(monitoredVehicleJourney, "VehicleRef", "UNKNOWN"));
            row.setField(2, getStringValue(monitoredVehicleJourney, "LineRef", "UNKNOWN"));
            row.setField(3, getStringValue(monitoredVehicleJourney, "DirectionRef", "UNKNOWN"));
            
            // Location from VehicleLocation
            JsonNode location = monitoredVehicleJourney.path("VehicleLocation");
            row.setField(4, getDoubleValue(location, "Latitude", 0.0));
            row.setField(5, getDoubleValue(location, "Longitude", 0.0));
            
            row.setField(6, getDoubleValue(monitoredVehicleJourney, "Speed", 0.0));
            row.setField(7, getStringValue(monitoredVehicleJourney, "ProgressStatus", "UNKNOWN"));
            row.setField(8, getStringValue(monitoredVehicleJourney, "MonitoredCall/StopPointRef", ""));
            row.setField(9, getIntValue(monitoredVehicleJourney, "Delay", 0));
            row.setField(10, getStringValue(monitoredVehicleJourney, "Occupancy", "UNKNOWN"));
            row.setField(11, getStringValue(monitoredVehicleJourney, "BlockRef", ""));
            row.setField(12, getStringValue(monitoredVehicleJourney, "OriginRef", ""));
            row.setField(13, xmlData); // Store original data
        }
    }
    
    /**
     * Parse SIRI JSON format
     */
    private static void parseSIRIJSON(String jsonData, Row row) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonData);
        
        row.setField(0, System.currentTimeMillis()); // timestamp_ms
        row.setField(1, getStringValue(jsonNode, "vehicle_id", "UNKNOWN"));
        row.setField(2, getStringValue(jsonNode, "route_id", "UNKNOWN"));
        row.setField(3, getStringValue(jsonNode, "direction", "UNKNOWN"));
        row.setField(4, getDoubleValue(jsonNode, "latitude", 0.0));
        row.setField(5, getDoubleValue(jsonNode, "longitude", 0.0));
        row.setField(6, getDoubleValue(jsonNode, "speed_mph", 0.0));
        row.setField(7, getStringValue(jsonNode, "status", "UNKNOWN"));
        row.setField(8, getStringValue(jsonNode, "next_stop", ""));
        row.setField(9, getIntValue(jsonNode, "delay_seconds", 0));
        row.setField(10, getStringValue(jsonNode, "occupancy", "UNKNOWN"));
        row.setField(11, getStringValue(jsonNode, "block_id", ""));
        row.setField(12, getStringValue(jsonNode, "trip_id", ""));
        row.setField(13, jsonData); // Store original data
        
        LOG.debug("Parsed bus SIRI data: vehicle_id={}, route_id={}, status={}", 
            row.getField(1), row.getField(2), row.getField(7));
    }
    
    /**
     * Helper method to extract string values from JSON
     */
    private static String getStringValue(JsonNode node, String fieldName, String defaultValue) {
        String[] parts = fieldName.split("/");
        JsonNode current = node;
        for (String part : parts) {
            current = current.path(part);
            if (current.isMissingNode()) {
                return defaultValue;
            }
        }
        return current.isNull() ? defaultValue : current.asText();
    }
    
    /**
     * Helper method to extract double values from JSON
     */
    private static Double getDoubleValue(JsonNode node, String fieldName, Double defaultValue) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? defaultValue : field.asDouble();
    }
    
    /**
     * Helper method to extract integer values from JSON
     */
    private static Integer getIntValue(JsonNode node, String fieldName, Integer defaultValue) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? defaultValue : field.asInt();
    }
    
    /**
     * Formats a Row as JSON for file persistence
     */
    private static String formatRowAsJson(Row row) {
        try {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"timestamp_ms\":").append(row.getField(0)).append(",");
            json.append("\"vehicle_id\":\"").append(row.getField(1)).append("\",");
            json.append("\"route_id\":\"").append(row.getField(2)).append("\",");
            json.append("\"direction\":\"").append(row.getField(3)).append("\",");
            json.append("\"latitude\":").append(row.getField(4)).append(",");
            json.append("\"longitude\":").append(row.getField(5)).append(",");
            json.append("\"speed_mph\":").append(row.getField(6)).append(",");
            json.append("\"status\":\"").append(row.getField(7)).append("\",");
            json.append("\"next_stop\":\"").append(row.getField(8)).append("\",");
            json.append("\"delay_seconds\":").append(row.getField(9)).append(",");
            json.append("\"occupancy\":\"").append(row.getField(10)).append("\",");
            json.append("\"block_id\":\"").append(row.getField(11)).append("\",");
            json.append("\"trip_id\":\"").append(row.getField(12)).append("\",");
            json.append("\"raw_data\":").append(row.getField(13));
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
            return String.format("BUS_SIRI: [%s] Vehicle=%s Route=%s Status=%s Position=%.6f,%.6f Speed=%.1f mph Occupancy=%s", 
                formatTimestamp((Long) row.getField(0)),
                row.getField(1), row.getField(2), row.getField(7),
                row.getField(4), row.getField(5), row.getField(6),
                row.getField(10));
        } catch (Exception e) {
            LOG.error("Error formatting row for console: {}", e.getMessage());
            return "BUS_SIRI: [ERROR] Failed to format row";
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
    
    // Function implementations for Flink 2.1.0 compatibility
    
    public static class SIRIParsingFunction implements MapFunction<String, Row> {
        @Override
        public Row map(String siriData) throws Exception {
            return parseSIRIData(siriData);
        }
    }
    
    public static class NonNullRowFilter implements FilterFunction<Row> {
        @Override
        public boolean filter(Row row) throws Exception {
            return row != null;
        }
    }
    
    public static class JSONFormattingFunction implements MapFunction<Row, String> {
        @Override
        public String map(Row row) throws Exception {
            return formatRowAsJson(row);
        }
    }
    
    public static class ConsoleFormattingFunction implements MapFunction<Row, String> {
        @Override
        public String map(Row row) throws Exception {
            return formatRowForConsole(row);
        }
    }
    
}