package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Main GTFS-RT Pipeline that consumes from both SIRI bus and RailComm topics,
 * transforms the data to GTFS-RT format, and generates protobuf files.
 */
public class GTFSRTPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSRTPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_SIRI_TOPIC = "rtd.bus.siri";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String CONSUMER_GROUP = "gtfs-rt-generator";
    
    // GTFS-RT Configuration
    private static final String GTFS_RT_OUTPUT_DIR = "data/gtfs-rt";
    private static final Duration FEED_GENERATION_WINDOW = Duration.ofSeconds(30);
    private static final String FEED_PUBLISHER_NAME = "RTD Denver";
    private static final String FEED_PUBLISHER_URL = "https://www.rtd-denver.com";
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== RTD GTFS-RT Generation Pipeline Starting ===");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Bus SIRI Topic: {}", BUS_SIRI_TOPIC);
        LOG.info("Rail Comm Topic: {}", RAIL_COMM_TOPIC);
        LOG.info("GTFS-RT Output Directory: {}", GTFS_RT_OUTPUT_DIR);
        LOG.info("Feed Generation Interval: {} seconds", FEED_GENERATION_WINDOW.getSeconds());
        
        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Single parallelism for consistent feed generation
        
        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // Checkpoint every minute
        
        try {
            // Create Kafka sources
            KafkaSource<String> busSiriSource = createKafkaSource(BUS_SIRI_TOPIC, CONSUMER_GROUP + "-siri");
            KafkaSource<String> railCommSource = createKafkaSource(RAIL_COMM_TOPIC, CONSUMER_GROUP + "-rail");
            
            // Create data streams
            DataStream<String> siriStream = env.fromSource(
                busSiriSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofMinutes(2))
                    .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "SIRI Bus Data Source"
            );
            
            DataStream<String> railCommStream = env.fromSource(
                railCommSource,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofMinutes(2))
                    .withTimestampAssigner((event, timestamp) -> System.currentTimeMillis()),
                "RailComm Data Source"
            );
            
            // Transform SIRI data to GTFS-RT
            DataStream<GTFSRTData> siriGtfsRtStream = siriStream
                .map(new SiriToGtfsRtMapper())
                .name("SIRI to GTFS-RT Transformation");
            
            // Transform RailComm data to GTFS-RT
            DataStream<GTFSRTData> railCommGtfsRtStream = railCommStream
                .map(new RailCommToGtfsRtMapper())
                .name("RailComm to GTFS-RT Transformation");
            
            // Union both streams
            DataStream<GTFSRTData> unifiedGtfsRtStream = siriGtfsRtStream.union(railCommGtfsRtStream);
            
            // Window the data and generate feeds
            unifiedGtfsRtStream
                .windowAll(TumblingProcessingTimeWindows.of(FEED_GENERATION_WINDOW))
                .process(new GTFSRTFeedProcessor())
                .name("GTFS-RT Feed Generation and File Output")
                .print("GTFS-RT Generation Result");
            
            LOG.info("🚀 Starting GTFS-RT pipeline execution...");
            
            // Execute the pipeline
            env.execute("RTD GTFS-RT Generation Pipeline");
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Create Kafka source for the specified topic
     */
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
     * Map SIRI payload to GTFS-RT data
     */
    public static class SiriToGtfsRtMapper implements MapFunction<String, GTFSRTData> {
        
        private transient SiriToGtfsTransformer transformer;
        
        @Override
        public GTFSRTData map(String siriPayload) throws Exception {
            if (transformer == null) {
                transformer = new SiriToGtfsTransformer();
            }
            
            try {
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(siriPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(siriPayload);
                
                return new GTFSRTData(vehiclePositions, tripUpdates, new ArrayList<>(), "SIRI");
                
            } catch (Exception e) {
                LOG.error("Error transforming SIRI payload: {}", e.getMessage(), e);
                return new GTFSRTData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "SIRI");
            }
        }
    }
    
    /**
     * Map RailComm payload to GTFS-RT data
     */
    public static class RailCommToGtfsRtMapper implements MapFunction<String, GTFSRTData> {
        
        private transient RailCommToGtfsTransformer transformer;
        
        @Override
        public GTFSRTData map(String railCommPayload) throws Exception {
            if (transformer == null) {
                transformer = new RailCommToGtfsTransformer();
            }
            
            try {
                List<GtfsRealtime.VehiclePosition> vehiclePositions = transformer.transformToVehiclePositions(railCommPayload);
                List<GtfsRealtime.TripUpdate> tripUpdates = transformer.transformToTripUpdates(railCommPayload);
                
                return new GTFSRTData(vehiclePositions, tripUpdates, new ArrayList<>(), "RAILCOMM");
                
            } catch (Exception e) {
                LOG.error("Error transforming RailComm payload: {}", e.getMessage(), e);
                return new GTFSRTData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), "RAILCOMM");
            }
        }
    }
    
    /**
     * Process windowed GTFS-RT data and generate protobuf files directly
     */
    public static class GTFSRTFeedProcessor extends ProcessAllWindowFunction<GTFSRTData, String, TimeWindow> {
        
        private transient GTFSRTFeedGenerator feedGenerator;
        private transient GTFSRTDataProcessor dataProcessor;
        private transient ScheduledExecutorService healthCheckExecutor;
        
        @Override
        public void open(OpenContext openContext) throws Exception {
            super.open(openContext);
            // Initialize feed generator and data processor
            feedGenerator = new GTFSRTFeedGenerator(GTFS_RT_OUTPUT_DIR, FEED_PUBLISHER_NAME, FEED_PUBLISHER_URL);
            dataProcessor = new GTFSRTDataProcessor();
            
            // Start health check reporting
            healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
            healthCheckExecutor.scheduleAtFixedRate(this::reportHealth, 60, 60, TimeUnit.SECONDS);
            
            LOG.info("GTFS-RT feed processor initialized");
        }
        
        @Override
        public void process(Context context, Iterable<GTFSRTData> elements, Collector<String> out) throws Exception {
            
            List<GtfsRealtime.VehiclePosition> allVehiclePositions = new ArrayList<>();
            List<GtfsRealtime.TripUpdate> allTripUpdates = new ArrayList<>();
            List<GtfsRealtime.Alert> allAlerts = new ArrayList<>();
            
            int siriCount = 0;
            int railCommCount = 0;
            
            // Aggregate all data from the window
            for (GTFSRTData data : elements) {
                allVehiclePositions.addAll(data.getVehiclePositions());
                allTripUpdates.addAll(data.getTripUpdates());
                allAlerts.addAll(data.getAlerts());
                
                if ("SIRI".equals(data.getSource())) {
                    siriCount++;
                } else if ("RAILCOMM".equals(data.getSource())) {
                    railCommCount++;
                }
            }
            
            LOG.info("Window processed: {} SIRI records, {} RailComm records, {} vehicle positions, {} trip updates",
                siriCount, railCommCount, allVehiclePositions.size(), allTripUpdates.size());
            
            // Process and validate data
            List<GtfsRealtime.VehiclePosition> processedVehiclePositions = dataProcessor.processVehiclePositions(allVehiclePositions);
            List<GtfsRealtime.TripUpdate> processedTripUpdates = dataProcessor.processTripUpdates(allTripUpdates);
            List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(allAlerts);
            
            // Generate protobuf feeds
            try {
                GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
                    processedVehiclePositions,
                    processedTripUpdates,
                    processedAlerts
                );
                
                if (result.isAllSuccess()) {
                    LOG.info("Successfully generated GTFS-RT feeds: {} vehicles, {} trip updates, {} alerts",
                        result.getVehicleCount(), result.getTripUpdateCount(), result.getAlertCount());
                    out.collect("SUCCESS: Generated feeds with " + result.getVehicleCount() + " vehicles, " + 
                              result.getTripUpdateCount() + " trip updates, " + result.getAlertCount() + " alerts");
                } else {
                    LOG.warn("Some GTFS-RT feeds failed: VP={}, TU={}, A={}", 
                        result.isVehiclePositionsSuccess(), result.isTripUpdatesSuccess(), result.isAlertsSuccess());
                    out.collect("PARTIAL: Some feeds failed - check logs");
                }
                
            } catch (Exception e) {
                LOG.error("Error generating GTFS-RT feeds: {}", e.getMessage(), e);
                out.collect("ERROR: Feed generation failed - " + e.getMessage());
            }
        }
        
        @Override
        public void close() throws Exception {
            if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
                healthCheckExecutor.shutdown();
            }
            super.close();
        }
        
        private void reportHealth() {
            try {
                if (feedGenerator != null) {
                    GTFSRTFeedGenerator.GTFSRTFeedStats stats = feedGenerator.getFeedStats();
                    LOG.info("GTFS-RT feed status - VP: {}KB ({}), TU: {}KB ({}), A: {}KB ({})",
                        stats.getVehiclePositionsSize() / 1024,
                        stats.getVehiclePositionsLastModified() > 0 ? "recent" : "none",
                        stats.getTripUpdatesSize() / 1024,
                        stats.getTripUpdatesLastModified() > 0 ? "recent" : "none",
                        stats.getAlertsSize() / 1024,
                        stats.getAlertsLastModified() > 0 ? "recent" : "none"
                    );
                }
            } catch (Exception e) {
                LOG.warn("Error reporting health: {}", e.getMessage());
            }
        }
    }
    
    
    /**
     * Container for GTFS-RT data from transformation
     */
    public static class GTFSRTData {
        private final List<GtfsRealtime.VehiclePosition> vehiclePositions;
        private final List<GtfsRealtime.TripUpdate> tripUpdates;
        private final List<GtfsRealtime.Alert> alerts;
        private final String source;
        
        public GTFSRTData(List<GtfsRealtime.VehiclePosition> vehiclePositions,
                         List<GtfsRealtime.TripUpdate> tripUpdates,
                         List<GtfsRealtime.Alert> alerts,
                         String source) {
            this.vehiclePositions = vehiclePositions != null ? vehiclePositions : new ArrayList<>();
            this.tripUpdates = tripUpdates != null ? tripUpdates : new ArrayList<>();
            this.alerts = alerts != null ? alerts : new ArrayList<>();
            this.source = source;
        }
        
        public List<GtfsRealtime.VehiclePosition> getVehiclePositions() { return vehiclePositions; }
        public List<GtfsRealtime.TripUpdate> getTripUpdates() { return tripUpdates; }
        public List<GtfsRealtime.Alert> getAlerts() { return alerts; }
        public String getSource() { return source; }
    }
    
}