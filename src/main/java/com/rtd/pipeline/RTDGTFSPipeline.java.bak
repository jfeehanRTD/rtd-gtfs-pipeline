package com.rtd.pipeline;

import com.rtd.pipeline.source.GTFSRealtimeSource;
import com.rtd.pipeline.source.GTFSScheduleSource;
import com.rtd.pipeline.model.VehiclePosition;
import com.rtd.pipeline.model.TripUpdate;
import com.rtd.pipeline.model.Alert;
import com.rtd.pipeline.model.GTFSScheduleData;
import com.rtd.pipeline.model.ComprehensiveRouteData;
import com.rtd.pipeline.util.RTDRouteManager;
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
    
    // RTD GTFS Schedule Feed URL
    private static final String GTFS_SCHEDULE_URL = "https://www.rtd-denver.com/open-records/open-spatial-information/gtfs";
    
    // Fetch interval (1 hour = 3600 seconds)
    private static final long FETCH_INTERVAL_SECONDS = 3600L;
    
    // Kafka configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String VEHICLE_POSITIONS_TOPIC = "rtd.vehicle.positions";
    private static final String TRIP_UPDATES_TOPIC = "rtd.trip.updates";
    private static final String ALERTS_TOPIC = "rtd.alerts";
    private static final String GTFS_SCHEDULE_TOPIC = "rtd.gtfs.schedule";
    private static final String COMPREHENSIVE_ROUTES_TOPIC = "rtd.comprehensive.routes";
    private static final String ROUTE_SUMMARY_TOPIC = "rtd.route.summary";
    private static final String VEHICLE_TRACKING_TOPIC = "rtd.vehicle.tracking";
    
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
        
        // Create data stream for GTFS schedule data
        DataStream<GTFSScheduleData> scheduleData = createGTFSScheduleStream(env);
        
        // Convert streams to tables and create sinks
        createVehiclePositionTable(tableEnv, vehiclePositions);
        createTripUpdateTable(tableEnv, tripUpdates);
        createAlertTable(tableEnv, alerts);
        createGTFSScheduleTable(tableEnv, scheduleData);
        
        // Create comprehensive routes sink combining GTFS and GTFS-RT data
        createComprehensiveRouteSink(tableEnv);
        
        // Create additional analytical sinks
        createRouteSummarySink(tableEnv);
        createVehicleTrackingSink(tableEnv);
        
        LOG.info("Pipeline created successfully");
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
                    .withTimestampAssigner((position, timestamp) -> position.getTimestamp()),
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
                    .withTimestampAssigner((update, timestamp) -> update.getTimestamp()),
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
                    .withTimestampAssigner((alert, timestamp) -> alert.getTimestamp()),
                "Alert Source"
        );
    }
    
    private DataStream<GTFSScheduleData> createGTFSScheduleStream(StreamExecutionEnvironment env) {
        LOG.info("Creating GTFS Schedule stream from: {}", GTFS_SCHEDULE_URL);
        
        return env.fromSource(
                GTFSScheduleSource.create(
                    GTFS_SCHEDULE_URL,
                    FETCH_INTERVAL_SECONDS
                ),
                WatermarkStrategy.<GTFSScheduleData>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                    .withTimestampAssigner((scheduleData, timestamp) -> scheduleData.getTimestamp()),
                "GTFS Schedule Source"
        );
    }
    
    private void createVehiclePositionTable(StreamTableEnvironment tableEnv, DataStream<VehiclePosition> stream) {
        Table vehicleTable = tableEnv.fromDataStream(stream);
        tableEnv.createTemporaryView("vehicle_positions", vehicleTable);
        
        // Create Kafka sink table for vehicle positions
        tableEnv.executeSql(String.format("""
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
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, VEHICLE_POSITIONS_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
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
        
        // Create Kafka sink table for trip updates
        tableEnv.executeSql(String.format("""
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
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, TRIP_UPDATES_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
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
        
        // Create Kafka sink table for alerts
        tableEnv.executeSql(String.format("""
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
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, ALERTS_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
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
    
    private void createGTFSScheduleTable(StreamTableEnvironment tableEnv, DataStream<GTFSScheduleData> stream) {
        Table scheduleTable = tableEnv.fromDataStream(stream);
        tableEnv.createTemporaryView("gtfs_schedule", scheduleTable);
        
        // Create Kafka sink table for GTFS schedule data
        tableEnv.executeSql(String.format("""
            CREATE TABLE gtfs_schedule_sink (
                file_type STRING,
                file_content STRING,
                download_timestamp BIGINT,
                feed_version STRING,
                agency_name STRING,
                feed_start_date STRING,
                feed_end_date STRING,
                PRIMARY KEY (file_type, download_timestamp) NOT ENFORCED
            ) WITH (
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, GTFS_SCHEDULE_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
        // Insert data into sink
        tableEnv.executeSql("""
            INSERT INTO gtfs_schedule_sink
            SELECT 
                fileType,
                fileContent,
                downloadTimestamp,
                feedVersion,
                agencyName,
                feedStartDate,
                feedEndDate
            FROM gtfs_schedule
        """);
        
        LOG.info("GTFS Schedule table and sink created");
    }
    
    /**
     * Creates a comprehensive route data sink that combines GTFS schedule data with real-time vehicle positions
     * to provide complete route information for all RTD vehicles across all routes.
     */
    private void createComprehensiveRouteSink(StreamTableEnvironment tableEnv) {
        LOG.info("Creating comprehensive RTD routes sink");
        
        // Create the comprehensive routes sink table
        tableEnv.executeSql(String.format("""
            CREATE TABLE comprehensive_routes_sink (
                route_id STRING,
                route_short_name STRING,
                route_long_name STRING,
                route_type STRING,
                route_color STRING,
                route_text_color STRING,
                vehicle_id STRING,
                trip_id STRING,
                vehicle_latitude DOUBLE,
                vehicle_longitude DOUBLE,
                vehicle_bearing FLOAT,
                vehicle_speed FLOAT,
                vehicle_status STRING,
                occupancy_status STRING,
                delay_seconds INT,
                schedule_relationship STRING,
                last_updated BIGINT,
                active_alerts ARRAY<STRING>,
                stop_id STRING,
                stop_name STRING,
                stop_sequence INT,
                direction_id INT,
                service_id STRING,
                trip_headsign STRING,
                block_id STRING,
                PRIMARY KEY (route_id, vehicle_id) NOT ENFORCED
            ) WITH (
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, COMPREHENSIVE_ROUTES_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
        // Create a comprehensive view that joins all the data sources
        tableEnv.executeSql("""
            CREATE TEMPORARY VIEW comprehensive_routes AS
            SELECT 
                COALESCE(vp.routeId, tu.routeId, 'UNKNOWN') as route_id,
                
                -- Extract route information from GTFS schedule data
                -- This is a simplified extraction - in reality you'd parse the GTFS files
                CASE 
                    WHEN vp.routeId LIKE 'A%' THEN 'A'
                    WHEN vp.routeId LIKE 'B%' THEN 'B'
                    WHEN vp.routeId LIKE 'C%' THEN 'C'
                    WHEN vp.routeId LIKE 'D%' THEN 'D'
                    WHEN vp.routeId LIKE 'E%' THEN 'E'
                    WHEN vp.routeId LIKE 'F%' THEN 'F'
                    WHEN vp.routeId LIKE 'G%' THEN 'G'
                    WHEN vp.routeId LIKE 'H%' THEN 'H'
                    WHEN vp.routeId LIKE 'N%' THEN 'N'
                    WHEN vp.routeId LIKE 'R%' THEN 'R'
                    WHEN vp.routeId LIKE 'W%' THEN 'W'
                    ELSE COALESCE(vp.routeId, tu.routeId)
                END as route_short_name,
                
                -- Route long names based on RTD system
                CASE 
                    WHEN vp.routeId LIKE 'A%' THEN 'A-Line to Denver International Airport'
                    WHEN vp.routeId LIKE 'B%' THEN 'B-Line to Westminster'
                    WHEN vp.routeId LIKE 'C%' THEN 'C-Line to Littleton'
                    WHEN vp.routeId LIKE 'D%' THEN 'D-Line to Littleton-Mineral'
                    WHEN vp.routeId LIKE 'E%' THEN 'E-Line to RidgeGate'
                    WHEN vp.routeId LIKE 'F%' THEN 'F-Line to 18th & California'
                    WHEN vp.routeId LIKE 'G%' THEN 'G-Line to Wheat Ridge'
                    WHEN vp.routeId LIKE 'H%' THEN 'H-Line to Nine Mile'
                    WHEN vp.routeId LIKE 'N%' THEN 'N-Line to Eastlake-124th'
                    WHEN vp.routeId LIKE 'R%' THEN 'R-Line to Peoria'
                    WHEN vp.routeId LIKE 'W%' THEN 'W-Line to Westminster'
                    ELSE CONCAT('Route ', COALESCE(vp.routeId, tu.routeId))
                END as route_long_name,
                
                -- Route type (1 = Light Rail, 3 = Bus)
                CASE 
                    WHEN vp.routeId IN ('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'N', 'R', 'W') THEN '1'
                    ELSE '3'
                END as route_type,
                
                -- Route colors based on RTD branding
                CASE 
                    WHEN vp.routeId LIKE 'A%' THEN '#0073E6'  -- Blue
                    WHEN vp.routeId LIKE 'B%' THEN '#00A84F'  -- Green
                    WHEN vp.routeId LIKE 'C%' THEN '#F7931E'  -- Orange
                    WHEN vp.routeId LIKE 'D%' THEN '#FFD700'  -- Gold
                    WHEN vp.routeId LIKE 'E%' THEN '#8B4513'  -- Brown
                    WHEN vp.routeId LIKE 'F%' THEN '#FF69B4'  -- Pink
                    WHEN vp.routeId LIKE 'G%' THEN '#32CD32'  -- Lime Green
                    WHEN vp.routeId LIKE 'H%' THEN '#FF4500'  -- Red Orange
                    WHEN vp.routeId LIKE 'N%' THEN '#9932CC'  -- Purple
                    WHEN vp.routeId LIKE 'R%' THEN '#DC143C'  -- Crimson
                    WHEN vp.routeId LIKE 'W%' THEN '#20B2AA'  -- Light Sea Green
                    ELSE '#666666'  -- Default gray for buses
                END as route_color,
                
                'FFFFFF' as route_text_color,  -- White text for all routes
                
                -- Vehicle information
                vp.vehicleId as vehicle_id,
                COALESCE(vp.tripId, tu.tripId) as trip_id,
                vp.latitude as vehicle_latitude,
                vp.longitude as vehicle_longitude,
                vp.bearing as vehicle_bearing,
                vp.speed as vehicle_speed,
                vp.currentStatus as vehicle_status,
                vp.occupancyStatus as occupancy_status,
                
                -- Trip update information
                tu.delaySeconds as delay_seconds,
                tu.scheduleRelationship as schedule_relationship,
                
                -- Timing information
                GREATEST(
                    COALESCE(vp.timestamp_ms, 0),
                    COALESCE(tu.timestamp_ms, 0)
                ) as last_updated,
                
                -- Active alerts (collect all alerts affecting this route)
                COLLECT(a.alertId) as active_alerts,
                
                -- Additional trip/stop information
                COALESCE(vp.stopId, tu.stopId) as stop_id,
                CASE 
                    WHEN COALESCE(vp.stopId, tu.stopId) IS NOT NULL 
                    THEN CONCAT('Stop ', COALESCE(vp.stopId, tu.stopId))
                    ELSE NULL 
                END as stop_name,
                
                -- Default values for fields that would come from GTFS data
                1 as stop_sequence,
                0 as direction_id,
                'WEEKDAY' as service_id,
                CASE 
                    WHEN vp.routeId LIKE 'A%' THEN 'To Airport'
                    WHEN vp.routeId LIKE 'B%' THEN 'To Westminster'
                    WHEN vp.routeId LIKE 'C%' THEN 'To Littleton'
                    WHEN vp.routeId LIKE 'D%' THEN 'To Littleton-Mineral'
                    WHEN vp.routeId LIKE 'E%' THEN 'To RidgeGate'
                    WHEN vp.routeId LIKE 'F%' THEN 'To 18th & California'
                    WHEN vp.routeId LIKE 'G%' THEN 'To Wheat Ridge'
                    WHEN vp.routeId LIKE 'H%' THEN 'To Nine Mile'
                    WHEN vp.routeId LIKE 'N%' THEN 'To Eastlake-124th'
                    WHEN vp.routeId LIKE 'R%' THEN 'To Peoria'
                    WHEN vp.routeId LIKE 'W%' THEN 'To Westminster'
                    ELSE 'Service'
                END as trip_headsign,
                
                COALESCE(vp.vehicleId, tu.vehicleId, 'UNKNOWN') as block_id
                
            FROM vehicle_positions vp
            FULL OUTER JOIN trip_updates tu 
                ON vp.vehicleId = tu.vehicleId 
                AND vp.routeId = tu.routeId
            LEFT JOIN alerts a 
                ON ARRAY_CONTAINS(a.routeIds, vp.routeId) 
                OR ARRAY_CONTAINS(a.routeIds, tu.routeId)
            WHERE vp.vehicleId IS NOT NULL 
                OR tu.vehicleId IS NOT NULL
            GROUP BY 
                COALESCE(vp.routeId, tu.routeId),
                COALESCE(vp.vehicleId, tu.vehicleId),
                vp.tripId, tu.tripId, vp.latitude, vp.longitude, 
                vp.bearing, vp.speed, vp.currentStatus, vp.occupancyStatus,
                tu.delaySeconds, tu.scheduleRelationship,
                vp.timestamp_ms, tu.timestamp_ms,
                vp.stopId, tu.stopId
        """);
        
        // Insert the comprehensive route data into the sink
        tableEnv.executeSql("""
            INSERT INTO comprehensive_routes_sink
            SELECT * FROM comprehensive_routes
        """);
        
        LOG.info("Comprehensive RTD routes sink created - combining GTFS and GTFS-RT data for all vehicles and routes");
    }
    
    /**
     * Creates a route summary sink that provides aggregated statistics for each route
     */
    private void createRouteSummarySink(StreamTableEnvironment tableEnv) {
        LOG.info("Creating route summary sink");
        
        // Create the route summary sink table
        tableEnv.executeSql(String.format("""
            CREATE TABLE route_summary_sink (
                route_id STRING,
                route_short_name STRING,
                route_long_name STRING,
                route_type STRING,
                total_vehicles INT,
                active_vehicles INT,
                average_delay_seconds DOUBLE,
                on_time_performance DOUBLE,
                total_alerts INT,
                service_status STRING,
                last_updated BIGINT,
                PRIMARY KEY (route_id) NOT ENFORCED
            ) WITH (
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, ROUTE_SUMMARY_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
        // Create route summary view with aggregations
        tableEnv.executeSql("""
            CREATE TEMPORARY VIEW route_summary AS
            SELECT 
                route_id,
                route_short_name,
                route_long_name,
                route_type,
                COUNT(*) as total_vehicles,
                COUNT(CASE WHEN vehicle_status = 'IN_TRANSIT_TO' THEN 1 END) as active_vehicles,
                AVG(CAST(delay_seconds as DOUBLE)) as average_delay_seconds,
                
                -- On-time performance (percentage within 5 minutes)
                (COUNT(CASE WHEN ABS(COALESCE(delay_seconds, 0)) <= 300 THEN 1 END) * 100.0) / COUNT(*) as on_time_performance,
                
                -- Count of active alerts
                SUM(CASE WHEN CARDINALITY(active_alerts) > 0 THEN CARDINALITY(active_alerts) ELSE 0 END) as total_alerts,
                
                -- Overall service status
                CASE 
                    WHEN AVG(CAST(delay_seconds as DOUBLE)) > 900 THEN 'SEVERELY_DELAYED'
                    WHEN AVG(CAST(delay_seconds as DOUBLE)) > 300 THEN 'DELAYED' 
                    WHEN AVG(CAST(delay_seconds as DOUBLE)) < -300 THEN 'RUNNING_EARLY'
                    ELSE 'NORMAL'
                END as service_status,
                
                MAX(last_updated) as last_updated
                
            FROM comprehensive_routes
            WHERE vehicle_id IS NOT NULL
            GROUP BY route_id, route_short_name, route_long_name, route_type
        """);
        
        // Insert route summary data
        tableEnv.executeSql("""
            INSERT INTO route_summary_sink
            SELECT * FROM route_summary
        """);
        
        LOG.info("Route summary sink created");
    }
    
    /**
     * Creates a vehicle tracking sink that provides detailed tracking information for each vehicle
     */
    private void createVehicleTrackingSink(StreamTableEnvironment tableEnv) {
        LOG.info("Creating vehicle tracking sink");
        
        // Create the vehicle tracking sink table
        tableEnv.executeSql(String.format("""
            CREATE TABLE vehicle_tracking_sink (
                vehicle_id STRING,
                route_id STRING,
                trip_id STRING,
                current_latitude DOUBLE,
                current_longitude DOUBLE,
                bearing FLOAT,
                speed_kmh DOUBLE,
                status STRING,
                occupancy STRING,
                delay_seconds INT,
                delay_status STRING,
                current_stop STRING,
                next_stop STRING,
                distance_to_next_stop DOUBLE,
                estimated_arrival_time BIGINT,
                passenger_load STRING,
                service_alerts ARRAY<STRING>,
                last_position_update BIGINT,
                tracking_quality STRING,
                PRIMARY KEY (vehicle_id) NOT ENFORCED
            ) WITH (
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'format' = 'json'
            )
        """, VEHICLE_TRACKING_TOPIC, KAFKA_BOOTSTRAP_SERVERS));
        
        // Create vehicle tracking view with enhanced calculations
        tableEnv.executeSql("""
            CREATE TEMPORARY VIEW vehicle_tracking AS
            SELECT 
                vehicle_id,
                route_id,
                trip_id,
                vehicle_latitude as current_latitude,
                vehicle_longitude as current_longitude,
                vehicle_bearing as bearing,
                
                -- Convert speed from m/s to km/h (if needed)
                CASE 
                    WHEN vehicle_speed IS NOT NULL THEN vehicle_speed * 3.6
                    ELSE 0.0
                END as speed_kmh,
                
                vehicle_status as status,
                occupancy_status as occupancy,
                delay_seconds,
                
                -- Delay status categorization
                CASE 
                    WHEN delay_seconds IS NULL THEN 'UNKNOWN'
                    WHEN delay_seconds > 900 THEN 'SEVERELY_DELAYED'  -- > 15 min
                    WHEN delay_seconds > 300 THEN 'DELAYED'           -- > 5 min
                    WHEN delay_seconds < -300 THEN 'EARLY'            -- < -5 min
                    ELSE 'ON_TIME'
                END as delay_status,
                
                stop_name as current_stop,
                'Next Stop TBD' as next_stop,  -- Would calculate from GTFS data
                0.0 as distance_to_next_stop,  -- Would calculate from position
                
                -- Estimated arrival time (simplified calculation)
                last_updated + (COALESCE(delay_seconds, 0) * 1000) as estimated_arrival_time,
                
                -- Passenger load interpretation
                CASE 
                    WHEN occupancy_status = 'STANDING_ROOM_ONLY' THEN 'HIGH'
                    WHEN occupancy_status = 'FEW_SEATS_AVAILABLE' THEN 'MEDIUM'
                    WHEN occupancy_status = 'MANY_SEATS_AVAILABLE' THEN 'LOW'
                    ELSE 'UNKNOWN'
                END as passenger_load,
                
                active_alerts as service_alerts,
                last_updated as last_position_update,
                
                -- Tracking quality based on data freshness
                CASE 
                    WHEN (UNIX_TIMESTAMP() * 1000) - last_updated < 60000 THEN 'EXCELLENT'   -- < 1 min
                    WHEN (UNIX_TIMESTAMP() * 1000) - last_updated < 300000 THEN 'GOOD'       -- < 5 min  
                    WHEN (UNIX_TIMESTAMP() * 1000) - last_updated < 900000 THEN 'FAIR'       -- < 15 min
                    ELSE 'POOR'
                END as tracking_quality
                
            FROM comprehensive_routes
            WHERE vehicle_id IS NOT NULL 
                AND vehicle_latitude IS NOT NULL 
                AND vehicle_longitude IS NOT NULL
        """);
        
        // Insert vehicle tracking data
        tableEnv.executeSql("""
            INSERT INTO vehicle_tracking_sink
            SELECT * FROM vehicle_tracking
        """);
        
        LOG.info("Vehicle tracking sink created");
    }
}