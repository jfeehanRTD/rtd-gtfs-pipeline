package com.rtd.pipeline;

import com.google.transit.realtime.GtfsRealtime.VehiclePosition;
import com.rtd.pipeline.serialization.ProtobufTypeInformation;
import com.rtd.pipeline.source.GTFSProtobufSource;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * RTD Pipeline using native Protocol Buffer messages to avoid Flink serialization issues.
 * This implementation uses protobuf's built-in serialization instead of custom classes,
 * which should resolve the SimpleUdfStreamOperatorFactory compatibility problems.
 */
public class ProtobufRTDPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(ProtobufRTDPipeline.class);
    
    private static final String VEHICLE_POSITIONS_URL = "https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb";
    private static final long FETCH_INTERVAL_SECONDS = 60L;
    
    public static void main(String[] args) throws Exception {
        
        System.out.println("=== Protocol Buffer RTD Pipeline - Flink 2.0.0 ===" );
        System.out.println("Using native protobuf messages for serialization compatibility");
        
        try {
            // Create Flink environment with minimal configuration
            Configuration config = new Configuration();
            StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironment(1, config);
            env.setParallelism(1);
            
            // Create source with custom protobuf type information
            DataStream<VehiclePosition> vehicleStream = env.addSource(
                GTFSProtobufSource.create(VEHICLE_POSITIONS_URL, FETCH_INTERVAL_SECONDS),
                ProtobufTypeInformation.create(VehiclePosition.class)
            );
            
            // Apply watermarks for event time processing
            DataStream<VehiclePosition> watermarkedStream = vehicleStream.assignTimestampsAndWatermarks(
                WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((vehiclePos, timestamp) -> {
                        // Use current time as event time for live data
                        return System.currentTimeMillis();
                    })
            );
            
            // Transform to readable format and print
            DataStream<String> vehicleInfo = watermarkedStream.map(new VehiclePositionFormatter());
            vehicleInfo.print("RTD-PROTOBUF");
            
            // Count vehicles by route
            DataStream<String> routeCounts = watermarkedStream
                .filter(vehiclePos -> vehiclePos.hasTrip() && vehiclePos.getTrip().hasRouteId())
                .map(vehiclePos -> vehiclePos.getTrip().getRouteId())
                .keyBy(routeId -> routeId)
                .reduce((route1, route2) -> route1); // Simple counting placeholder
            
            routeCounts.print("ROUTE-COUNTS");
            
            System.out.println("Starting protobuf-based Flink job...");
            
            // Execute the pipeline
            env.execute("Protobuf RTD Pipeline");
            
        } catch (Exception e) {
            System.err.println("Protobuf pipeline execution failed: " + e.getMessage());
            e.printStackTrace();
            
            // Log error and exit
            System.out.println("\n=== Protobuf Pipeline Execution Failed ===");
            System.out.println("Please check the logs above for detailed error information.");
        }
    }
    
    /**
     * MapFunction to convert protobuf VehiclePosition to readable string format.
     */
    public static class VehiclePositionFormatter implements MapFunction<VehiclePosition, String> {
        @Override
        public String map(VehiclePosition vehiclePos) throws Exception {
            StringBuilder sb = new StringBuilder();
            sb.append("Vehicle: ");
            
            if (vehiclePos.hasVehicle() && vehiclePos.getVehicle().hasId()) {
                sb.append(vehiclePos.getVehicle().getId());
            } else {
                sb.append("UNKNOWN");
            }
            
            if (vehiclePos.hasTrip() && vehiclePos.getTrip().hasRouteId()) {
                sb.append(" | Route: ").append(vehiclePos.getTrip().getRouteId());
            }
            
            if (vehiclePos.hasPosition()) {
                sb.append(" | Position: (")
                  .append(String.format("%.6f", vehiclePos.getPosition().getLatitude()))
                  .append(", ")
                  .append(String.format("%.6f", vehiclePos.getPosition().getLongitude()))
                  .append(")");
                
                if (vehiclePos.getPosition().hasBearing()) {
                    sb.append(" | Bearing: ").append(vehiclePos.getPosition().getBearing()).append("°");
                }
                
                if (vehiclePos.getPosition().hasSpeed()) {
                    sb.append(" | Speed: ").append(vehiclePos.getPosition().getSpeed()).append(" m/s");
                }
            }
            
            if (vehiclePos.hasCurrentStatus()) {
                sb.append(" | Status: ").append(vehiclePos.getCurrentStatus());
            }
            
            return sb.toString();
        }
    }
}