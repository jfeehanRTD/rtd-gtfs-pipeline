package com.rtd.pipeline;

import com.rtd.pipeline.source.GTFSRealtimeSource;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Main Flink job for processing RTD GTFS-RT feeds.
 * Downloads vehicle positions, trip updates, and alerts from RTD's real-time feeds
 * and processes them using Flink's Table API.
 */
public class RTDGTFSPipeline {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDGTFSPipeline.class);
    
    // RTD GTFS-RT Feed URLs
    private static final String VEHICLE_POSITIONS_URL = "https://www.rtd-denver.com/google_sync/VehiclePosition.pb";
    private static final String TRIP_UPDATES_URL = "https://www.rtd-denver.com/google_sync/TripUpdate.pb";
    private static final String ALERTS_URL = "https://www.rtd-denver.com/google_sync/Alert.pb";
    
    // Fetch interval (1 hour = 3600 seconds)
    private static final long FETCH_INTERVAL_SECONDS = 3600L;
    
    public static void main(String[] args) throws Exception {
        
        LOG.info("Starting RTD GTFS-RT Data Pipeline");
        
        // Set up the execution environment
        Configuration config = new Configuration();
        config.setString("execution.checkpointing.interval", "60s");
        config.setString("execution.checkpointing.mode", "EXACTLY_ONCE");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.createLocalEnvironmentWithWebUI(config);
        env.setParallelism(1); // Single parallelism for this demo
        
        // Create Table Environment
        StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
        
        RTDGTFSPipeline pipeline = new RTDGTFSPipeline();
        pipeline.createPipeline(env, tableEnv);
        
        // Execute the job
        env.execute("RTD GTFS-RT Data Pipeline");
    }
    
    public void createPipeline(StreamExecutionEnvironment env, StreamTableEnvironment tableEnv) {
        
        // Create data streams for each GTFS-RT feed type
        DataStream<VehiclePosition> vehiclePositions = createVehiclePositionStream(env);
        DataStream<TripUpdate> tripUpdates = createTripUpdateStream(env);
        DataStream<Alert> alerts = createAlertStream(env);
        
        // Convert streams to tables and create sinks
        createVehiclePositionTable(tableEnv, vehiclePositions);
        createTripUpdateTable(tableEnv, tripUpdates);
        createAlertTable(tableEnv, alerts);
        
        LOG.info("Pipeline created successfully");
    }
    
    private DataStream<VehiclePosition> createVehiclePositionStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Vehicle Position stream from: {}", VEHICLE_POSITIONS_URL);
        
        return env.addSource(new GTFSRealtimeSource<>(
                VEHICLE_POSITIONS_URL,
                FETCH_INTERVAL_SECONDS,
                VehiclePosition.class
        ))
        .name("Vehicle Position Source")
        .assignTimestampsAndWatermarks(
            WatermarkStrategy.<VehiclePosition>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                .withTimestampAssigner((position, timestamp) -> position.getTimestamp())
        );
    }
    
    private DataStream<TripUpdate> createTripUpdateStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Trip Update stream from: {}", TRIP_UPDATES_URL);
        
        return env.addSource(new GTFSRealtimeSource<>(
                TRIP_UPDATES_URL,
                FETCH_INTERVAL_SECONDS,
                TripUpdate.class
        ))
        .name("Trip Update Source")
        .assignTimestampsAndWatermarks(
            WatermarkStrategy.<TripUpdate>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                .withTimestampAssigner((update, timestamp) -> update.getTimestamp())
        );
    }
    
    private DataStream<Alert> createAlertStream(StreamExecutionEnvironment env) {
        LOG.info("Creating Alert stream from: {}", ALERTS_URL);
        
        return env.addSource(new GTFSRealtimeSource<>(
                ALERTS_URL,
                FETCH_INTERVAL_SECONDS,
                Alert.class
        ))
        .name("Alert Source")
        .assignTimestampsAndWatermarks(
            WatermarkStrategy.<Alert>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                .withTimestampAssigner((alert, timestamp) -> alert.getTimestamp())
        );
    }
    
    private void createVehiclePositionTable(StreamTableEnvironment tableEnv, DataStream<VehiclePosition> stream) {
        Table vehicleTable = tableEnv.fromDataStream(stream);
        tableEnv.createTemporaryView("vehicle_positions", vehicleTable);
        
        // Create sink table for vehicle positions
        tableEnv.executeSql("""
            CREATE TABLE vehicle_positions_sink (
                vehicle_id STRING,
                trip_id STRING,
                route_id STRING,
                latitude DOUBLE,
                longitude DOUBLE,
                bearing FLOAT,
                speed FLOAT,
                timestamp_ms BIGINT,
                current_status STRING,
                congestion_level STRING,
                occupancy_status STRING,
                PRIMARY KEY (vehicle_id) NOT ENFORCED
            ) WITH (
                'connector' = 'print'
            )
        """);
        
        // Insert data into sink
        tableEnv.executeSql("""
            INSERT INTO vehicle_positions_sink
            SELECT 
                vehicleId,
                tripId,
                routeId,
                latitude,
                longitude,
                bearing,
                speed,
                timestamp_ms,
                currentStatus,
                congestionLevel,
                occupancyStatus
            FROM vehicle_positions
        """);
        
        LOG.info("Vehicle Position table and sink created");
    }
    
    private void createTripUpdateTable(StreamTableEnvironment tableEnv, DataStream<TripUpdate> stream) {
        Table tripTable = tableEnv.fromDataStream(stream);
        tableEnv.createTemporaryView("trip_updates", tripTable);
        
        // Create sink table for trip updates
        tableEnv.executeSql("""
            CREATE TABLE trip_updates_sink (
                trip_id STRING,
                route_id STRING,
                vehicle_id STRING,
                start_date STRING,
                start_time STRING,
                schedule_relationship STRING,
                delay_seconds INT,
                timestamp_ms BIGINT,
                PRIMARY KEY (trip_id) NOT ENFORCED
            ) WITH (
                'connector' = 'print'
            )
        """);
        
        // Insert data into sink
        tableEnv.executeSql("""
            INSERT INTO trip_updates_sink
            SELECT 
                tripId,
                routeId,
                vehicleId,
                startDate,
                startTime,
                scheduleRelationship,
                delaySeconds,
                timestamp_ms
            FROM trip_updates
        """);
        
        LOG.info("Trip Update table and sink created");
    }
    
    private void createAlertTable(StreamTableEnvironment tableEnv, DataStream<Alert> stream) {
        Table alertTable = tableEnv.fromDataStream(stream);
        tableEnv.createTemporaryView("alerts", alertTable);
        
        // Create sink table for alerts
        tableEnv.executeSql("""
            CREATE TABLE alerts_sink (
                alert_id STRING,
                cause STRING,
                effect STRING,
                header_text STRING,
                description_text STRING,
                url STRING,
                active_period_start BIGINT,
                active_period_end BIGINT,
                timestamp_ms BIGINT,
                PRIMARY KEY (alert_id) NOT ENFORCED
            ) WITH (
                'connector' = 'print'
            )
        """);
        
        // Insert data into sink
        tableEnv.executeSql("""
            INSERT INTO alerts_sink
            SELECT 
                alertId,
                cause,
                effect,
                headerText,
                descriptionText,
                url,
                activePeriodStart,
                activePeriodEnd,
                timestamp_ms
            FROM alerts
        """);
        
        LOG.info("Alert table and sink created");
    }
}