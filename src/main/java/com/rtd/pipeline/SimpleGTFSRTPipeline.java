package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Simplified GTFS-RT Pipeline that avoids Flink 2.x compatibility issues
 * by using basic process functions instead of complex windowing
 */
public class SimpleGTFSRTPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleGTFSRTPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_SIRI_TOPIC = "rtd.bus.siri";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String CONSUMER_GROUP = "gtfs-rt-generator";
    
    // GTFS-RT Configuration
    private static final String GTFS_RT_OUTPUT_DIR = "data/gtfs-rt";
    private static final String FEED_PUBLISHER_NAME = "RTD Denver";
    private static final String FEED_PUBLISHER_URL = "https://www.rtd-denver.com";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD Simple GTFS-RT Generation Pipeline Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Bus SIRI Topic: {}", BUS_SIRI_TOPIC);
        LOG.info("Rail Comm Topic: {}", RAIL_COMM_TOPIC);
        LOG.info("GTFS-RT Output Directory: {}", GTFS_RT_OUTPUT_DIR);
        
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
            
            // Process SIRI data
            siriStream
                .process(new SiriProcessor())
                .name("SIRI GTFS-RT Processor");
            
            // Process RailComm data
            railCommStream
                .process(new RailCommProcessor())
                .name("RailComm GTFS-RT Processor");
            
            LOG.info("🚀 Starting simple GTFS-RT pipeline execution...");
            
            // Execute the pipeline
            env.execute("RTD Simple GTFS-RT Generation Pipeline");
            
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
     * Process SIRI messages and generate GTFS-RT feeds
     */
    public static class SiriProcessor extends ProcessFunction<String, String> {
        
        private transient SiriToGtfsTransformer transformer;
        private transient GTFSRTDataProcessor dataProcessor;
        private transient GTFSRTFeedGenerator feedGenerator;
        private transient Map<String, List<GtfsRealtime.VehiclePosition>> vehicleBuffer;
        private transient Map<String, List<GtfsRealtime.TripUpdate>> tripBuffer;
        private transient ScheduledExecutorService scheduler;
        
        @Override
        public void open(OpenContext openContext) throws Exception {
            super.open(openContext);
            transformer = new SiriToGtfsTransformer();
            dataProcessor = new GTFSRTDataProcessor();
            feedGenerator = new GTFSRTFeedGenerator(GTFS_RT_OUTPUT_DIR, FEED_PUBLISHER_NAME, FEED_PUBLISHER_URL);
            vehicleBuffer = new HashMap<>();
            tripBuffer = new HashMap<>();
            
            // Schedule periodic feed generation
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::generateFeeds, 30, 30, TimeUnit.SECONDS);
            
            LOG.info("SIRI processor initialized");
        }
        
        @Override
        public void processElement(String siriPayload, Context context, Collector<String> collector) throws Exception {
            try {
                // Transform SIRI to GTFS-RT
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(siriPayload);
                
                // Buffer the data
                synchronized (this) {
                    vehicleBuffer.put("siri-" + System.currentTimeMillis(), vehiclePositions);
                    tripBuffer.put("siri-" + System.currentTimeMillis(), tripUpdates);
                    
                    // Keep only recent data (last 5 minutes)
                    long cutoff = System.currentTimeMillis() - 300000;
                    vehicleBuffer.entrySet().removeIf(entry -> 
                        Long.parseLong(entry.getKey().split("-")[1]) < cutoff);
                    tripBuffer.entrySet().removeIf(entry -> 
                        Long.parseLong(entry.getKey().split("-")[1]) < cutoff);
                }
                
                collector.collect("SIRI processed: " + vehiclePositions.size() + " vehicles, " + tripUpdates.size() + " trips");
                
            } catch (Exception e) {
                LOG.error("Error processing SIRI payload: {}", e.getMessage(), e);
                collector.collect("SIRI error: " + e.getMessage());
            }
        }
        
        private synchronized void generateFeeds() {
            try {
                // Aggregate all buffered data
                List<GtfsRealtime.VehiclePosition> allVehicles = new ArrayList<>();
                List<GtfsRealtime.TripUpdate> allTrips = new ArrayList<>();
                
                for (List<GtfsRealtime.VehiclePosition> vehicles : vehicleBuffer.values()) {
                    allVehicles.addAll(vehicles);
                }
                for (List<GtfsRealtime.TripUpdate> trips : tripBuffer.values()) {
                    allTrips.addAll(trips);
                }
                
                if (!allVehicles.isEmpty() || !allTrips.isEmpty()) {
                    // Process and generate feeds
                    List<GtfsRealtime.VehiclePosition> processedVehicles = dataProcessor.processVehiclePositions(allVehicles);
                    List<GtfsRealtime.TripUpdate> processedTrips = dataProcessor.processTripUpdates(allTrips);
                    List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());
                    
                    GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
                        processedVehicles, processedTrips, processedAlerts);
                    
                    LOG.info("Generated GTFS-RT feeds from SIRI: {} vehicles, {} trips",
                        result.getVehicleCount(), result.getTripUpdateCount());
                }
            } catch (Exception e) {
                LOG.error("Error generating feeds from SIRI data: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void close() throws Exception {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }
            super.close();
        }
    }
    
    /**
     * Process RailComm messages and generate GTFS-RT feeds
     */
    public static class RailCommProcessor extends ProcessFunction<String, String> {
        
        private transient RailCommToGtfsTransformer transformer;
        private transient GTFSRTDataProcessor dataProcessor;
        private transient GTFSRTFeedGenerator feedGenerator;
        private transient Map<String, List<GtfsRealtime.VehiclePosition>> vehicleBuffer;
        private transient Map<String, List<GtfsRealtime.TripUpdate>> tripBuffer;
        private transient ScheduledExecutorService scheduler;
        
        @Override
        public void open(OpenContext openContext) throws Exception {
            super.open(openContext);
            transformer = new RailCommToGtfsTransformer();
            dataProcessor = new GTFSRTDataProcessor();
            feedGenerator = new GTFSRTFeedGenerator(GTFS_RT_OUTPUT_DIR, FEED_PUBLISHER_NAME, FEED_PUBLISHER_URL);
            vehicleBuffer = new HashMap<>();
            tripBuffer = new HashMap<>();
            
            // Schedule periodic feed generation
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::generateFeeds, 30, 30, TimeUnit.SECONDS);
            
            LOG.info("RailComm processor initialized");
        }
        
        @Override
        public void processElement(String railCommPayload, Context context, Collector<String> collector) throws Exception {
            try {
                // Transform RailComm to GTFS-RT
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(railCommPayload);
                
                // Buffer the data
                synchronized (this) {
                    vehicleBuffer.put("rail-" + System.currentTimeMillis(), vehiclePositions);
                    tripBuffer.put("rail-" + System.currentTimeMillis(), tripUpdates);
                    
                    // Keep only recent data (last 5 minutes)
                    long cutoff = System.currentTimeMillis() - 300000;
                    vehicleBuffer.entrySet().removeIf(entry -> 
                        Long.parseLong(entry.getKey().split("-")[1]) < cutoff);
                    tripBuffer.entrySet().removeIf(entry -> 
                        Long.parseLong(entry.getKey().split("-")[1]) < cutoff);
                }
                
                collector.collect("RailComm processed: " + vehiclePositions.size() + " vehicles, " + tripUpdates.size() + " trips");
                
            } catch (Exception e) {
                LOG.error("Error processing RailComm payload: {}", e.getMessage(), e);
                collector.collect("RailComm error: " + e.getMessage());
            }
        }
        
        private synchronized void generateFeeds() {
            try {
                // Aggregate all buffered data
                List<GtfsRealtime.VehiclePosition> allVehicles = new ArrayList<>();
                List<GtfsRealtime.TripUpdate> allTrips = new ArrayList<>();
                
                for (List<GtfsRealtime.VehiclePosition> vehicles : vehicleBuffer.values()) {
                    allVehicles.addAll(vehicles);
                }
                for (List<GtfsRealtime.TripUpdate> trips : tripBuffer.values()) {
                    allTrips.addAll(trips);
                }
                
                if (!allVehicles.isEmpty() || !allTrips.isEmpty()) {
                    // Process and generate feeds
                    List<GtfsRealtime.VehiclePosition> processedVehicles = dataProcessor.processVehiclePositions(allVehicles);
                    List<GtfsRealtime.TripUpdate> processedTrips = dataProcessor.processTripUpdates(allTrips);
                    List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());
                    
                    GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
                        processedVehicles, processedTrips, processedAlerts);
                    
                    LOG.info("Generated GTFS-RT feeds from RailComm: {} vehicles, {} trips",
                        result.getVehicleCount(), result.getTripUpdateCount());
                }
            } catch (Exception e) {
                LOG.error("Error generating feeds from RailComm data: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void close() throws Exception {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
            }
            super.close();
        }
    }
}