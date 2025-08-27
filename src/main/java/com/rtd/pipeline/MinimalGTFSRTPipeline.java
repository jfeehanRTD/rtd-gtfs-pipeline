package com.rtd.pipeline;

import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Absolutely minimal GTFS-RT Pipeline that only reads from Kafka and prints
 * No transformations, no custom functions - just testing basic Flink 2.1.0 functionality
 */
public class MinimalGTFSRTPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(MinimalGTFSRTPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_SIRI_TOPIC = "rtd.bus.siri";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String CONSUMER_GROUP = "gtfs-rt-generator";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== Minimal GTFS-RT Pipeline Testing Flink 2.1.0 ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Bus SIRI Topic: {}", BUS_SIRI_TOPIC);
        LOG.info("Rail Comm Topic: {}", RAIL_COMM_TOPIC);
        
        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        try {
            // Create Kafka sources
            KafkaSource<String> busSiriSource = createKafkaSource(BUS_SIRI_TOPIC, CONSUMER_GROUP + "-siri");
            KafkaSource<String> railCommSource = createKafkaSource(RAIL_COMM_TOPIC, CONSUMER_GROUP + "-rail");
            
            // Create data streams - NO TRANSFORMATIONS
            DataStream<String> siriStream = env.fromSource(
                busSiriSource,
                org.apache.flink.api.common.eventtime.WatermarkStrategy.<String>noWatermarks(),
                "SIRI Bus Data Source"
            );
            
            DataStream<String> railCommStream = env.fromSource(
                railCommSource,
                org.apache.flink.api.common.eventtime.WatermarkStrategy.<String>noWatermarks(),
                "RailComm Data Source"
            );
            
            // ONLY print - no transformations whatsoever
            siriStream.print("SIRI-RAW");
            railCommStream.print("RAILCOMM-RAW");
            
            LOG.info("🚀 Starting minimal GTFS-RT pipeline execution...");
            
            // Execute the pipeline
            env.execute("Minimal GTFS-RT Test Pipeline");
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private static KafkaSource<String> createKafkaSource(String topic, String consumerGroup) {
        return KafkaSource.<String>builder()
            .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
            .setTopics(topic)
            .setGroupId(consumerGroup)
            .setStartingOffsets(OffsetsInitializer.latest())
            .setValueOnlyDeserializer(new SimpleStringSchema())
            .build();
    }
}