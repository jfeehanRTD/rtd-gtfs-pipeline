package com.rtd.pipeline;

import com.rtd.pipeline.source.GTFSRealtimeSource;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Simplified Flink job for processing RTD GTFS-RT feeds using DataStream API directly.
 * Sends data to Kafka topics without Table API complexity.
 */
public class SimpleRTDPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleRTDPipeline.class);
    
    // RTD GTFS-RT Feed URLs
    private static final String VEHICLE_POSITIONS_URL = "https://www.rtd-denver.com/google_sync/VehiclePosition.pb";
    private static final String TRIP_UPDATES_URL = "https://www.rtd-denver.com/google_sync/TripUpdate.pb";
    private static final String ALERTS_URL = "https://www.rtd-denver.com/google_sync/Alert.pb";
    
    // Fetch interval (1 hour = 3600 seconds)
    private static final long FETCH_INTERVAL_SECONDS = 3600L;
    
    // Kafka configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String VEHICLE_POSITIONS_TOPIC = "rtd.vehicle.positions";
    private static final String TRIP_UPDATES_TOPIC = "rtd.trip.updates";
    private static final String ALERTS_TOPIC = "rtd.alerts";
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("Starting Simple RTD GTFS-RT Data Pipeline");
        
        // Set up the execution environment
        Configuration config = new Configuration();
        config.setString("execution.checkpointing.interval", "60s");
        config.setString("execution.checkpointing.mode", "EXACTLY_ONCE");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        env.setParallelism(1); // Single parallelism for this demo
        
        SimpleRTDPipeline pipeline = new SimpleRTDPipeline();
        pipeline.createPipeline(env);
        
        // Execute the job
        env.execute("Simple RTD GTFS-RT Pipeline");
    }
    
    public void createPipeline(StreamExecutionEnvironment env) {
        
        // Create data streams for each GTFS-RT feed type
        DataStream<VehiclePosition> vehiclePositions = createVehiclePositionStream(env);
        DataStream<TripUpdate> tripUpdates = createTripUpdateStream(env);
        DataStream<Alert> alerts = createAlertStream(env);
        
        // Create Kafka sinks for each stream
        createVehiclePositionSink(vehiclePositions);
        createTripUpdateSink(tripUpdates);
        createAlertSink(alerts);
        
        LOG.info("Simple pipeline created successfully");
    }
    
    private DataStream<VehiclePosition> createVehiclePositionStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Vehicle Position stream from: {}", VEHICLE_POSITIONS_URL);
        
        return env.fromSource(
                GTFSRealtimeSource.create(
                    VEHICLE_POSITIONS_URL,
                    FETCH_INTERVAL_SECONDS,
                    VehiclePosition.class
                ),
                WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((position, timestamp) -> position != null ? position.getTimestamp() : System.currentTimeMillis()),
                "Vehicle Position Source"
        );
    }
    
    private DataStream<TripUpdate> createTripUpdateStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Trip Update stream from: {}", TRIP_UPDATES_URL);
        
        return env.fromSource(
                GTFSRealtimeSource.create(
                    TRIP_UPDATES_URL,
                    FETCH_INTERVAL_SECONDS,
                    TripUpdate.class
                ),
                WatermarkStrategy.<TripUpdate>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((update, timestamp) -> update != null ? update.getTimestamp() : System.currentTimeMillis()),
                "Trip Update Source"
        );
    }
    
    private DataStream<Alert> createAlertStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Alert stream from: {}", ALERTS_URL);
        
        return env.fromSource(
                GTFSRealtimeSource.create(
                    ALERTS_URL,
                    FETCH_INTERVAL_SECONDS,
                    Alert.class
                ),
                WatermarkStrategy.<Alert>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((alert, timestamp) -> alert != null ? alert.getTimestamp() : System.currentTimeMillis()),
                "Alert Source"
        );
    }
    
    private void createVehiclePositionSink(DataStream<VehiclePosition> stream) {
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(VEHICLE_POSITIONS_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setTransactionalIdPrefix("vehicle-positions-")
                .build();
        
        stream.filter(vehiclePosition -> vehiclePosition != null)
              .map(vehiclePosition -> {
                  try {
                      return objectMapper.writeValueAsString(vehiclePosition);
                  } catch (Exception e) {
                      LOG.error("Failed to serialize vehicle position", e);
                      return "{}";
                  }
              }).sinkTo(sink);
        
        LOG.info("Vehicle Position sink created for topic: {}", VEHICLE_POSITIONS_TOPIC);
    }
    
    private void createTripUpdateSink(DataStream<TripUpdate> stream) {
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(TRIP_UPDATES_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setTransactionalIdPrefix("trip-updates-")
                .build();
        
        stream.filter(tripUpdate -> tripUpdate != null)
              .map(tripUpdate -> {
                  try {
                      return objectMapper.writeValueAsString(tripUpdate);
                  } catch (Exception e) {
                      LOG.error("Failed to serialize trip update", e);
                      return "{}";
                  }
              }).sinkTo(sink);
        
        LOG.info("Trip Update sink created for topic: {}", TRIP_UPDATES_TOPIC);
    }
    
    private void createAlertSink(DataStream<Alert> stream) {
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP_SERVERS)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(ALERTS_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setTransactionalIdPrefix("alerts-")
                .build();
        
        stream.filter(alert -> alert != null)
              .map(alert -> {
                  try {
                      return objectMapper.writeValueAsString(alert);
                  } catch (Exception e) {
                      LOG.error("Failed to serialize alert", e);
                      return "{}";
                  }
              }).sinkTo(sink);
        
        LOG.info("Alert sink created for topic: {}", ALERTS_TOPIC);
    }
}