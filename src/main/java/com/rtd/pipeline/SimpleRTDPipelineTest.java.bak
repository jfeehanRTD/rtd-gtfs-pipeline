package com.rtd.pipeline;

import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.source.GTFSRealtimeSource;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Simple test class to verify GTFS-RT data source functionality without Table API.
 */
public class SimpleRTDPipelineTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleRTDPipelineTest.class);
    
    // RTD GTFS-RT Feed URL
    private static final String VEHICLE_POSITIONS_URL = "https://www.rtd-denver.com/google_sync/VehiclePosition.pb";
    
    // Fetch interval (30 seconds for testing)
    private static final long FETCH_INTERVAL_SECONDS = 30L;
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("Starting Simple RTD GTFS-RT Pipeline Test");
        
        // Set up the execution environment
        Configuration config = new Configuration();
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(config);
        env.setParallelism(1); // Single parallelism for this demo
        
        // Create data stream for vehicle positions
        DataStream<VehiclePosition> vehiclePositions = env.fromSource(
                GTFSRealtimeSource.create(
                    VEHICLE_POSITIONS_URL,
                    FETCH_INTERVAL_SECONDS,
                    VehiclePosition.class
                ),
                WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((position, timestamp) -> position.getTimestamp()),
                "Vehicle Position Source"
        );
        
        // Print the results
        vehiclePositions.print().name("Print Vehicle Positions");
        
        LOG.info("Pipeline created successfully");
        
        // Execute the job
        env.execute("Simple RTD GTFS-RT Pipeline Test");
    }
}