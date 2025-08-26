package com.rtd.pipeline;

import com.rtd.pipeline.source.GTFSRealtimeSource;
import com.rtd.pipeline.model.VehiclePosition;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Simplified RTD pipeline to test Flink 2.0.0 compatibility with robust error handling.
 * Uses minimal operators and null-safe operations.
 */
public class SimpleRTDPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(SimpleRTDPipeline.class);
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final long FETCH_INTERVAL_SECONDS = 60L;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== Simple RTD Pipeline - Flink 2.0.0 Test ===");
        System.out.println("Testing basic Flink execution with live RTD data");
        
        try {
            // Create simple local environment without web UI
            Configuration config = new Configuration();
            StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1, config);
            env.setParallelism(1);
            
            // Create RTD data source
            DataStream<VehiclePosition> vehicleStream = env.addSource(
                GTFSRealtimeSource.create(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS, VehiclePosition.class),
                org.apache.flink.api.common.typeinfo.TypeInformation.of(VehiclePosition.class)
            );
            
            // Apply watermarks for event time processing
            DataStream<VehiclePosition> watermarkedStream = vehicleStream.assignTimestampsAndWatermarks(
                WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((position, timestamp) -> {
                        if (position != null && position.getTimestamp() != null) {
                            return position.getTimestamp();
                        }
                        return System.currentTimeMillis();
                    })
            );
            
            // Simple print sink - no transformation operators
            watermarkedStream.print("RTD-LIVE-DATA");
            
            System.out.println("Starting simple Flink job...");
            
            // Execute with timeout
            env.execute("Simple RTD Pipeline Test");
            
        } catch (Exception e) {
            System.err.println("Pipeline execution failed: " + e.getMessage());
            e.printStackTrace();
            
            // Log error and exit
            System.out.println("\n=== Pipeline Execution Failed ===");
            System.out.println("Please check the logs above for detailed error information.");
        }
    }
}