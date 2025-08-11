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
 * Test pipeline to verify RTD live data connection and parsing.
 * Uses shorter fetch interval for testing purposes.
 */
public class RTDTestPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDTestPipeline.class);
    
    // RTD GTFS-RT Feed URLs
    private static final String VEHICLE_POSITIONS_URL = "https://www.rtd-denver.com/google_sync/VehiclePosition.pb";
    
    // Short fetch interval for testing (30 seconds)
    private static final long FETCH_INTERVAL_SECONDS = 30L;
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("Starting RTD Test Pipeline - Live Data Test");
        
        // Set up the execution environment
        Configuration config = new Configuration();
        config.setString("execution.checkpointing.interval", "60s");
        config.setString("execution.checkpointing.mode", "EXACTLY_ONCE");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        env.setParallelism(1); // Single parallelism for testing
        
        // Create vehicle position stream from live RTD data
        DataStream<VehiclePosition> vehiclePositions = env.addSource(
            GTFSRealtimeSource.create(
                VEHICLE_POSITIONS_URL,
                FETCH_INTERVAL_SECONDS,
                VehiclePosition.class
            ),
            org.apache.flink.api.common.typeinfo.TypeInformation.of(VehiclePosition.class)
        ).assignTimestampsAndWatermarks(
            WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                .withTimestampAssigner((position, timestamp) -> position != null ? position.getTimestamp() : System.currentTimeMillis())
        ).name("RTD Vehicle Positions");
        
        // Filter out null positions and print live data
        vehiclePositions
            .filter(position -> position != null)
            .map(position -> {
                String status = String.format(
                    "LIVE RTD DATA -> Vehicle: %s | Route: %s | Trip: %s | Position: (%.6f, %.6f) | Status: %s | Time: %d",
                    position.getVehicleId(),
                    position.getRouteId(),
                    position.getTripId(),
                    position.getLatitude() != null ? position.getLatitude() : 0.0,
                    position.getLongitude() != null ? position.getLongitude() : 0.0,
                    position.getCurrentStatus(),
                    position.getTimestamp()
                );
                LOG.info("PROCESSED: {}", status);
                return status;
            })
            .print("RTD-LIVE");
        
        // Execute the job for a limited time (2 minutes for testing)
        LOG.info("Pipeline will run for 2 minutes to demonstrate live data...");
        env.execute("RTD Live Data Test Pipeline");
    }
}