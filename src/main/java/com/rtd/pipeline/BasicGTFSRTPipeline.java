package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ultra-simplified GTFS-RT Pipeline using only basic MapFunction to avoid Flink 2.x compatibility issues
 */
public class BasicGTFSRTPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(BasicGTFSRTPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_SIRI_TOPIC = "rtd.bus.siri";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String CONSUMER_GROUP = "gtfs-rt-generator";
    
    // GTFS-RT Configuration
    private static final String GTFS_RT_OUTPUT_DIR = "data/gtfs-rt";
    private static final String FEED_PUBLISHER_NAME = "RTD Denver";
    private static final String FEED_PUBLISHER_URL = "https://www.rtd-denver.com";
    
    // Global feed generator and processor for periodic output
    private static GTFSRTFeedGenerator feedGenerator;
    private static GTFSRTDataProcessor dataProcessor;
    private static ScheduledExecutorService scheduler;
    private static final List<GtfsRealtime.VehiclePosition> vehicleBuffer = new ArrayList<>();
    private static final List<GtfsRealtime.TripUpdate> tripBuffer = new ArrayList<>();
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD Basic GTFS-RT Generation Pipeline Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Bus SIRI Topic: {}", BUS_SIRI_TOPIC);
        LOG.info("Rail Comm Topic: {}", RAIL_COMM_TOPIC);
        LOG.info("GTFS-RT Output Directory: {}", GTFS_RT_OUTPUT_DIR);
        
        // Initialize global components
        feedGenerator = new GTFSRTFeedGenerator(GTFS_RT_OUTPUT_DIR, FEED_PUBLISHER_NAME, FEED_PUBLISHER_URL);
        dataProcessor = new GTFSRTDataProcessor();
        
        // Start periodic feed generation
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(BasicGTFSRTPipeline::generateFeeds, 30, 30, TimeUnit.SECONDS);
        
        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        try {
            // Create Kafka sources
            KafkaSource<String> busSiriSource = createKafkaSource(BUS_SIRI_TOPIC, CONSUMER_GROUP + "-siri");
            KafkaSource<String> railCommSource = createKafkaSource(RAIL_COMM_TOPIC, CONSUMER_GROUP + "-rail");
            
            // Create data streams
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
            
            // Process SIRI data with simple map function
            siriStream
                .map(new SiriMapFunction())
                .name("SIRI to Buffer Processor")
                .print("SIRI Result");
            
            // Process RailComm data with simple map function
            railCommStream
                .map(new RailCommMapFunction())
                .name("RailComm to Buffer Processor")
                .print("RailComm Result");
            
            LOG.info("🚀 Starting basic GTFS-RT pipeline execution...");
            
            // Add shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                }
            }));
            
            // Execute the pipeline
            env.execute("RTD Basic GTFS-RT Generation Pipeline");
            
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
    
    /**
     * Simple map function for SIRI data that adds to global buffer
     */
    public static class SiriMapFunction implements MapFunction<String, String> {
        
        private transient SiriToGtfsTransformer transformer;
        
        @Override
        public String map(String siriPayload) throws Exception {
            if (transformer == null) {
                transformer = new SiriToGtfsTransformer();
            }
            
            try {
                // Transform SIRI to GTFS-RT
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(siriPayload);
                
                // Add to global buffer (thread-safe)
                synchronized (vehicleBuffer) {
                    vehicleBuffer.addAll(vehiclePositions);
                    // Keep only recent data (last 5 minutes)
                    if (vehicleBuffer.size() > 1000) {
                        vehicleBuffer.subList(0, vehicleBuffer.size() - 1000).clear();
                    }
                }
                
                synchronized (tripBuffer) {
                    tripBuffer.addAll(tripUpdates);
                    // Keep only recent data (last 5 minutes)
                    if (tripBuffer.size() > 1000) {
                        tripBuffer.subList(0, tripBuffer.size() - 1000).clear();
                    }
                }
                
                return "SIRI processed: " + vehiclePositions.size() + " vehicles, " + tripUpdates.size() + " trips";
                
            } catch (Exception e) {
                LOG.error("Error processing SIRI payload: {}", e.getMessage(), e);
                return "SIRI error: " + e.getMessage();
            }
        }
    }
    
    /**
     * Simple map function for RailComm data that adds to global buffer
     */
    public static class RailCommMapFunction implements MapFunction<String, String> {
        
        private transient RailCommToGtfsTransformer transformer;
        
        @Override
        public String map(String railCommPayload) throws Exception {
            if (transformer == null) {
                transformer = new RailCommToGtfsTransformer();
            }
            
            try {
                // Transform RailComm to GTFS-RT
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(railCommPayload);
                
                // Add to global buffer (thread-safe)
                synchronized (vehicleBuffer) {
                    vehicleBuffer.addAll(vehiclePositions);
                    // Keep only recent data (last 5 minutes)
                    if (vehicleBuffer.size() > 1000) {
                        vehicleBuffer.subList(0, vehicleBuffer.size() - 1000).clear();
                    }
                }
                
                synchronized (tripBuffer) {
                    tripBuffer.addAll(tripUpdates);
                    // Keep only recent data (last 5 minutes)
                    if (tripBuffer.size() > 1000) {
                        tripBuffer.subList(0, tripBuffer.size() - 1000).clear();
                    }
                }
                
                return "RailComm processed: " + vehiclePositions.size() + " vehicles, " + tripUpdates.size() + " trips";
                
            } catch (Exception e) {
                LOG.error("Error processing RailComm payload: {}", e.getMessage(), e);
                return "RailComm error: " + e.getMessage();
            }
        }
    }
    
    /**
     * Generate feeds from buffered data (called periodically)
     */
    private static void generateFeeds() {
        try {
            List<GtfsRealtime.VehiclePosition> currentVehicles;
            List<GtfsRealtime.TripUpdate> currentTrips;
            
            // Copy current buffer contents
            synchronized (vehicleBuffer) {
                currentVehicles = new ArrayList<>(vehicleBuffer);
            }
            synchronized (tripBuffer) {
                currentTrips = new ArrayList<>(tripBuffer);
            }
            
            if (!currentVehicles.isEmpty() || !currentTrips.isEmpty()) {
                // Process and generate feeds
                List<GtfsRealtime.VehiclePosition> processedVehicles = dataProcessor.processVehiclePositions(currentVehicles);
                List<GtfsRealtime.TripUpdate> processedTrips = dataProcessor.processTripUpdates(currentTrips);
                List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());
                
                GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
                    processedVehicles, processedTrips, processedAlerts);
                
                LOG.info("Generated GTFS-RT feeds: {} vehicles, {} trips",
                    result.getVehicleCount(), result.getTripUpdateCount());
            }
        } catch (Exception e) {
            LOG.error("Error generating feeds: {}", e.getMessage(), e);
        }
    }
}