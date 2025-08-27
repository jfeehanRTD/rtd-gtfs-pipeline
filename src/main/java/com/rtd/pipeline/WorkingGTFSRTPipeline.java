package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime;
import com.rtd.pipeline.gtfsrt.GTFSRTFeedGenerator;
import com.rtd.pipeline.gtfsrt.GTFSRTDataProcessor;
import com.rtd.pipeline.transform.SiriToGtfsTransformer;
import com.rtd.pipeline.transform.RailCommToGtfsTransformer;
import com.rtd.pipeline.transform.LrgpsToGtfsTransformer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Working GTFS-RT Pipeline that avoids Flink's broken KafkaSource by using native Kafka consumers
 * This approach bypasses the SimpleUdfStreamOperatorFactory compatibility issue
 * 
 * Processes three data sources for RTD transit network:
 * - SIRI: Bus real-time data from RTD's bus fleet
 * - LRGPS: Light Rail GPS data from cellular network routers on each light rail car (PRIMARY)
 * - RailComm: Light Rail SCADA sensor data from track infrastructure (FAILOVER for light rail)
 * 
 * Data Source Priority for Light Rail GTFS-RT:
 * 1. LRGPS (more accurate, real-time GPS from vehicles)
 * 2. RailComm (SCADA sensors, used when LRGPS unavailable)
 * 
 * Combines all sources into unified GTFS-RT feeds with intelligent failover
 */
public class WorkingGTFSRTPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(WorkingGTFSRTPipeline.class);
    
    // Kafka Configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String BUS_SIRI_TOPIC = "rtd.bus.siri";
    private static final String RAIL_COMM_TOPIC = "rtd.rail.comm";
    private static final String LRGPS_TOPIC = "rtd.lrgps";
    private static final String CONSUMER_GROUP = "gtfs-rt-generator";
    
    // GTFS-RT Configuration
    private static final String GTFS_RT_OUTPUT_DIR = "data/gtfs-rt";
    private static final String FEED_PUBLISHER_NAME = "RTD Denver";
    private static final String FEED_PUBLISHER_URL = "https://www.rtd-denver.com";
    
    // Data processing components
    private static GTFSRTFeedGenerator feedGenerator;
    private static GTFSRTDataProcessor dataProcessor;
    private static SiriToGtfsTransformer siriTransformer;
    private static RailCommToGtfsTransformer railCommTransformer;
    private static LrgpsToGtfsTransformer lrgpsTransformer;
    private static ScheduledExecutorService scheduler;
    
    // Data buffers
    private static final List<GtfsRealtime.VehiclePosition> vehicleBuffer = new ArrayList<>();
    private static final List<GtfsRealtime.TripUpdate> tripBuffer = new ArrayList<>();
    
    // Light Rail data tracking for failover logic
    private static final List<GtfsRealtime.VehiclePosition> lrgpsVehicleBuffer = new ArrayList<>();
    private static final List<GtfsRealtime.VehiclePosition> railCommVehicleBuffer = new ArrayList<>();
    private static final List<GtfsRealtime.TripUpdate> lrgpsTripBuffer = new ArrayList<>();
    private static final List<GtfsRealtime.TripUpdate> railCommTripBuffer = new ArrayList<>();
    
    // Failover configuration
    private static final long LRGPS_TIMEOUT_MS = 60000; // 1 minute timeout for LRGPS data
    private static volatile long lastLRGPSDataTime = 0;
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("=== Working GTFS-RT Generation Pipeline Starting ===");
        LOG.info("Using native Kafka consumers to avoid KafkaSource compatibility issues");
        LOG.info("Kafka Bootstrap Servers: {}", KAFKA_BOOTSTRAP_SERVERS);
        LOG.info("Bus SIRI Topic: {}", BUS_SIRI_TOPIC);
        LOG.info("Light Rail LRGPS Topic (PRIMARY): {}", LRGPS_TOPIC);
        LOG.info("Light Rail RailComm Topic (FAILOVER): {}", RAIL_COMM_TOPIC);
        LOG.info("GTFS-RT Output Directory: {}", GTFS_RT_OUTPUT_DIR);
        LOG.info("Light Rail Failover Timeout: {}ms", LRGPS_TIMEOUT_MS);
        
        try {
            // Initialize components
            initializeComponents();
            
            // Start data processing threads
            startSiriConsumer();
            startRailCommConsumer();
            startLRGPSConsumer();
            
            // Start periodic feed generation
            startFeedGeneration();
            
            LOG.info("🚀 Working GTFS-RT pipeline started successfully!");
            LOG.info("✅ SIRI consumer thread running (Bus data)");
            LOG.info("✅ LRGPS consumer thread running (Light Rail PRIMARY)");
            LOG.info("✅ RailComm consumer thread running (Light Rail SCADA FAILOVER)");
            LOG.info("✅ Feed generation scheduled every 30 seconds");
            LOG.info("📋 Light Rail Priority: LRGPS (cellular GPS) → RailComm (SCADA sensors)");
            LOG.info("");
            LOG.info("Pipeline is processing real-time transit data...");
            LOG.info("Press Ctrl+C to stop");
            
            // Keep main thread alive
            Thread.currentThread().join();
            
        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            cleanup();
        }
    }
    
    private static void initializeComponents() {
        LOG.info("Initializing GTFS-RT processing components...");
        
        feedGenerator = new GTFSRTFeedGenerator(GTFS_RT_OUTPUT_DIR, FEED_PUBLISHER_NAME, FEED_PUBLISHER_URL);
        dataProcessor = new GTFSRTDataProcessor();
        siriTransformer = new SiriToGtfsTransformer();
        railCommTransformer = new RailCommToGtfsTransformer();
        lrgpsTransformer = new LrgpsToGtfsTransformer();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        
        LOG.info("✅ All components initialized successfully");
    }
    
    private static void startSiriConsumer() {
        Thread siriThread = new Thread(() -> {
            LOG.info("Starting SIRI consumer thread...");
            
            Properties props = createKafkaConsumerProperties(CONSUMER_GROUP + "-siri");
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(BUS_SIRI_TOPIC));
                LOG.info("✅ SIRI consumer subscribed to topic: {}", BUS_SIRI_TOPIC);
                
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        processSiriData(record.value());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("SIRI consumer error: {}", e.getMessage(), e);
            }
        });
        
        siriThread.setName("SIRI-Consumer");
        siriThread.setDaemon(true);
        siriThread.start();
    }
    
    private static void startRailCommConsumer() {
        Thread railCommThread = new Thread(() -> {
            LOG.info("Starting RailComm consumer thread...");
            
            Properties props = createKafkaConsumerProperties(CONSUMER_GROUP + "-rail");
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(RAIL_COMM_TOPIC));
                LOG.info("✅ RailComm SCADA failover subscribed to topic: {}", RAIL_COMM_TOPIC);
                
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        processRailCommData(record.value());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("RailComm consumer error: {}", e.getMessage(), e);
            }
        });
        
        railCommThread.setName("LightRail-SCADA-Failover");
        railCommThread.setDaemon(true);
        railCommThread.start();
    }
    
    private static void startLRGPSConsumer() {
        Thread lrgpsThread = new Thread(() -> {
            LOG.info("Starting LRGPS consumer thread...");
            
            Properties props = createKafkaConsumerProperties(CONSUMER_GROUP + "-lrgps");
            
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(LRGPS_TOPIC));
                LOG.info("✅ LRGPS primary consumer subscribed to topic: {}", LRGPS_TOPIC);
                
                while (!Thread.currentThread().isInterrupted()) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                    
                    for (ConsumerRecord<String, String> record : records) {
                        processLRGPSData(record.value());
                    }
                }
                
            } catch (Exception e) {
                LOG.error("LRGPS consumer error: {}", e.getMessage(), e);
            }
        });
        
        lrgpsThread.setName("LightRail-GPS-Primary");
        lrgpsThread.setDaemon(true);
        lrgpsThread.start();
    }
    
    private static Properties createKafkaConsumerProperties(String consumerGroup) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        return props;
    }
    
    private static void processSiriData(String siriPayload) {
        try {
            // Transform SIRI to GTFS-RT
            List<GtfsRealtime.VehiclePosition> vehiclePositions = siriTransformer.transformToVehiclePositions(siriPayload);
            List<GtfsRealtime.TripUpdate> tripUpdates = siriTransformer.transformToTripUpdates(siriPayload);
            
            // Add to global buffer (thread-safe)
            synchronized (vehicleBuffer) {
                vehicleBuffer.addAll(vehiclePositions);
                // Keep buffer size manageable
                if (vehicleBuffer.size() > 1000) {
                    vehicleBuffer.subList(0, vehicleBuffer.size() - 1000).clear();
                }
            }
            
            synchronized (tripBuffer) {
                tripBuffer.addAll(tripUpdates);
                // Keep buffer size manageable
                if (tripBuffer.size() > 1000) {
                    tripBuffer.subList(0, tripBuffer.size() - 1000).clear();
                }
            }
            
            LOG.debug("SIRI processed: {} vehicles, {} trips", vehiclePositions.size(), tripUpdates.size());
            
        } catch (Exception e) {
            LOG.error("Error processing SIRI payload: {}", e.getMessage(), e);
        }
    }
    
    private static void processRailCommData(String railCommPayload) {
        try {
            // Transform RailComm SCADA to GTFS-RT
            List<GtfsRealtime.VehiclePosition> vehiclePositions = railCommTransformer.transformToVehiclePositions(railCommPayload);
            List<GtfsRealtime.TripUpdate> tripUpdates = railCommTransformer.transformToTripUpdates(railCommPayload);
            
            if (!vehiclePositions.isEmpty() || !tripUpdates.isEmpty()) {
                // Store in RailComm-specific buffers for failover
                synchronized (railCommVehicleBuffer) {
                    railCommVehicleBuffer.clear(); // Keep only latest RailComm data
                    railCommVehicleBuffer.addAll(vehiclePositions);
                }
                
                synchronized (railCommTripBuffer) {
                    railCommTripBuffer.clear(); // Keep only latest RailComm data
                    railCommTripBuffer.addAll(tripUpdates);
                }
                
                LOG.debug("RailComm SCADA processed (FAILOVER): {} vehicles, {} trips", vehiclePositions.size(), tripUpdates.size());
            }
            
        } catch (Exception e) {
            LOG.error("Error processing RailComm payload: {}", e.getMessage(), e);
        }
    }
    
    private static void processLRGPSData(String lrgpsPayload) {
        try {
            // Transform LRGPS to GTFS-RT
            List<GtfsRealtime.VehiclePosition> vehiclePositions = lrgpsTransformer.transformToVehiclePositions(lrgpsPayload);
            List<GtfsRealtime.TripUpdate> tripUpdates = lrgpsTransformer.transformToTripUpdates(lrgpsPayload);
            
            if (!vehiclePositions.isEmpty() || !tripUpdates.isEmpty()) {
                // Update LRGPS data timestamp for failover logic
                lastLRGPSDataTime = System.currentTimeMillis();
                
                // Store in LRGPS-specific buffers
                synchronized (lrgpsVehicleBuffer) {
                    lrgpsVehicleBuffer.clear(); // Keep only latest LRGPS data
                    lrgpsVehicleBuffer.addAll(vehiclePositions);
                }
                
                synchronized (lrgpsTripBuffer) {
                    lrgpsTripBuffer.clear(); // Keep only latest LRGPS data  
                    lrgpsTripBuffer.addAll(tripUpdates);
                }
                
                LOG.debug("LRGPS processed (PRIMARY): {} vehicles, {} trips", vehiclePositions.size(), tripUpdates.size());
            }
            
        } catch (Exception e) {
            LOG.error("Error processing LRGPS payload: {}", e.getMessage(), e);
        }
    }
    
    private static void startFeedGeneration() {
        scheduler.scheduleAtFixedRate(WorkingGTFSRTPipeline::generateFeeds, 30, 30, TimeUnit.SECONDS);
        LOG.info("✅ Feed generation scheduled every 30 seconds");
    }
    
    private static void generateFeeds() {
        try {
            List<GtfsRealtime.VehiclePosition> currentVehicles = new ArrayList<>();
            List<GtfsRealtime.TripUpdate> currentTrips = new ArrayList<>();
            
            // Add SIRI bus data (always include)
            synchronized (vehicleBuffer) {
                currentVehicles.addAll(vehicleBuffer);
            }
            synchronized (tripBuffer) {
                currentTrips.addAll(tripBuffer);
            }
            
            // Light Rail Data Source Selection (LRGPS preferred, RailComm failover)
            boolean usingLRGPS = false;
            long currentTime = System.currentTimeMillis();
            
            // Check if LRGPS data is recent enough to use as primary source
            if (lastLRGPSDataTime > 0 && (currentTime - lastLRGPSDataTime) <= LRGPS_TIMEOUT_MS) {
                // Use LRGPS data (primary source)
                synchronized (lrgpsVehicleBuffer) {
                    if (!lrgpsVehicleBuffer.isEmpty()) {
                        currentVehicles.addAll(lrgpsVehicleBuffer);
                        usingLRGPS = true;
                    }
                }
                synchronized (lrgpsTripBuffer) {
                    if (!lrgpsTripBuffer.isEmpty()) {
                        currentTrips.addAll(lrgpsTripBuffer);
                    }
                }
                
                if (usingLRGPS) {
                    LOG.debug("Using LRGPS data for light rail (PRIMARY source)");
                }
            }
            
            // Failover to RailComm if LRGPS is not available
            if (!usingLRGPS) {
                synchronized (railCommVehicleBuffer) {
                    if (!railCommVehicleBuffer.isEmpty()) {
                        currentVehicles.addAll(railCommVehicleBuffer);
                        LOG.info("⚠️ Failing over to RailComm SCADA data (LRGPS unavailable for {}ms)", 
                            lastLRGPSDataTime > 0 ? currentTime - lastLRGPSDataTime : "unknown");
                    }
                }
                synchronized (railCommTripBuffer) {
                    if (!railCommTripBuffer.isEmpty()) {
                        currentTrips.addAll(railCommTripBuffer);
                    }
                }
            }
            
            if (!currentVehicles.isEmpty() || !currentTrips.isEmpty()) {
                // Process and generate feeds
                List<GtfsRealtime.VehiclePosition> processedVehicles = dataProcessor.processVehiclePositions(currentVehicles);
                List<GtfsRealtime.TripUpdate> processedTrips = dataProcessor.processTripUpdates(currentTrips);
                List<GtfsRealtime.Alert> processedAlerts = dataProcessor.processAlerts(new ArrayList<>());
                
                GTFSRTFeedGenerator.GTFSRTFeedResult result = feedGenerator.generateAllFeeds(
                    processedVehicles, processedTrips, processedAlerts);
                
                String lightRailSource = usingLRGPS ? "LRGPS" : "RailComm";
                LOG.info("🚀 Generated GTFS-RT feeds: {} vehicles, {} trips, {} alerts (Light Rail: {})",
                    result.getVehicleCount(), result.getTripUpdateCount(), result.getAlertCount(), lightRailSource);
            } else {
                LOG.debug("No data to generate feeds - all buffers are empty");
            }
        } catch (Exception e) {
            LOG.error("Error generating feeds: {}", e.getMessage(), e);
        }
    }
    
    private static void cleanup() {
        LOG.info("Shutting down GTFS-RT pipeline...");
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        LOG.info("✅ GTFS-RT pipeline shutdown complete");
    }
}