package com.rtd.pipeline.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.apache.flink.table.api.Table;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Command-line test client for querying RTD GTFS-RT Flink Table API data sinks.
 * This client connects to Kafka topics created by the pipeline and executes SQL queries
 * to retrieve and display data from all RTD data sinks.
 */
public class RTDDataSinkQueryClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDDataSinkQueryClient.class);
    
    // Kafka configuration
    private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String COMPREHENSIVE_ROUTES_TOPIC = "rtd.comprehensive.routes";
    private static final String ROUTE_SUMMARY_TOPIC = "rtd.route.summary";
    private static final String VEHICLE_TRACKING_TOPIC = "rtd.vehicle.tracking";
    private static final String VEHICLE_POSITIONS_TOPIC = "rtd.vehicle.positions";
    private static final String TRIP_UPDATES_TOPIC = "rtd.trip.updates";
    private static final String ALERTS_TOPIC = "rtd.alerts";
    
    private final StreamExecutionEnvironment env;
    private final StreamTableEnvironment tableEnv;
    private final ObjectMapper objectMapper;
    
    public RTDDataSinkQueryClient() {
        this.env = StreamExecutionEnvironment.getExecutionEnvironment();
        this.env.setParallelism(1);
        this.tableEnv = StreamTableEnvironment.create(env);
        this.objectMapper = new ObjectMapper();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        RTDDataSinkQueryClient client = new RTDDataSinkQueryClient();
        
        try {
            String command = args[0].toLowerCase();
            
            switch (command) {
                case "list":
                    client.listAllTables();
                    break;
                case "routes":
                    client.queryComprehensiveRoutes(getLimit(args));
                    break;
                case "summary":
                    client.queryRouteSummary(getLimit(args));
                    break;
                case "tracking":
                    client.queryVehicleTracking(getLimit(args));
                    break;
                case "positions":
                    client.queryVehiclePositions(getLimit(args));
                    break;
                case "updates":
                    client.queryTripUpdates(getLimit(args));
                    break;
                case "alerts":
                    client.queryAlerts(getLimit(args));
                    break;
                case "route":
                    if (args.length < 2) {
                        System.err.println("Route ID required. Usage: java -jar client.jar route <route_id>");
                        System.exit(1);
                    }
                    client.querySpecificRoute(args[1], getLimit(args));
                    break;
                case "vehicle":
                    if (args.length < 2) {
                        System.err.println("Vehicle ID required. Usage: java -jar client.jar vehicle <vehicle_id>");
                        System.exit(1);
                    }
                    client.querySpecificVehicle(args[1], getLimit(args));
                    break;
                case "ontime":
                    client.queryOnTimePerformance(getLimit(args));
                    break;
                case "delays":
                    client.queryDelayedServices(getLimit(args));
                    break;
                case "live":
                    client.liveMonitor(getLimit(args));
                    break;
                default:
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
            }
            
        } catch (Exception e) {
            LOG.error("Error executing query", e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.out.println("RTD GTFS-RT Data Sink Query Client");
        System.out.println("=====================================");
        System.out.println();
        System.out.println("Usage: java -jar rtd-query-client.jar <command> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list                    - List all available tables");
        System.out.println("  routes [limit]          - Query comprehensive routes data");
        System.out.println("  summary [limit]         - Query route summary statistics");
        System.out.println("  tracking [limit]        - Query vehicle tracking data");
        System.out.println("  positions [limit]       - Query vehicle positions");
        System.out.println("  updates [limit]         - Query trip updates");
        System.out.println("  alerts [limit]          - Query active alerts");
        System.out.println("  route <route_id> [limit] - Query specific route data");
        System.out.println("  vehicle <vehicle_id> [limit] - Query specific vehicle data");
        System.out.println("  ontime [limit]          - Query on-time performance");
        System.out.println("  delays [limit]          - Query delayed services");
        System.out.println("  live [limit]            - Live monitoring mode");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  limit                   - Maximum number of records to display (default: 10)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar rtd-query-client.jar routes 20");
        System.out.println("  java -jar rtd-query-client.jar route A 5");
        System.out.println("  java -jar rtd-query-client.jar vehicle RTD_LR_A_001");
        System.out.println("  java -jar rtd-query-client.jar ontime");
    }
    
    private static int getLimit(String[] args) {
        // Look for limit parameter (usually last argument if numeric)
        for (int i = args.length - 1; i >= 1; i--) {
            try {
                return Integer.parseInt(args[i]);
            } catch (NumberFormatException e) {
                // Not a number, continue looking
            }
        }
        return 10; // Default limit
    }
    
    /**
     * List all available tables/topics
     */
    public void listAllTables() {
        System.out.println("RTD GTFS-RT Data Sinks");
        System.out.println("======================");
        System.out.println();
        
        String[][] tables = {
            {"comprehensive_routes", COMPREHENSIVE_ROUTES_TOPIC, "Complete vehicle and route data with real-time positions"},
            {"route_summary", ROUTE_SUMMARY_TOPIC, "Aggregated statistics and on-time performance per route"},
            {"vehicle_tracking", VEHICLE_TRACKING_TOPIC, "Detailed individual vehicle monitoring with enhanced metrics"},
            {"vehicle_positions", VEHICLE_POSITIONS_TOPIC, "Raw vehicle position data from GTFS-RT feeds"},
            {"trip_updates", TRIP_UPDATES_TOPIC, "Trip delay and schedule relationship updates"},
            {"alerts", ALERTS_TOPIC, "Service alerts and disruption notifications"}
        };
        
        System.out.printf("%-20s %-35s %s%n", "Table Name", "Kafka Topic", "Description");
        System.out.println("-".repeat(100));
        
        for (String[] table : tables) {
            System.out.printf("%-20s %-35s %s%n", table[0], table[1], table[2]);
        }
        
        System.out.println();
        System.out.println("Use specific commands to query each table (e.g., 'routes', 'summary', 'tracking')");
    }
    
    /**
     * Query comprehensive routes data
     */
    public void queryComprehensiveRoutes(int limit) throws Exception {
        System.out.println("Comprehensive Routes Data");
        System.out.println("========================");
        
        setupKafkaSource(COMPREHENSIVE_ROUTES_TOPIC, "comprehensive_routes_source");
        
        String query = String.format("""
            SELECT 
                route_id,
                route_short_name,
                route_long_name,
                vehicle_id,
                vehicle_latitude,
                vehicle_longitude,
                vehicle_status,
                delay_seconds,
                occupancy_status,
                last_updated
            FROM comprehensive_routes_source
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query route summary statistics
     */
    public void queryRouteSummary(int limit) throws Exception {
        System.out.println("Route Summary Statistics");
        System.out.println("=======================");
        
        setupKafkaSource(ROUTE_SUMMARY_TOPIC, "route_summary_source");
        
        String query = String.format("""
            SELECT 
                route_id,
                route_short_name,
                total_vehicles,
                active_vehicles,
                average_delay_seconds,
                on_time_performance,
                service_status,
                total_alerts,
                last_updated
            FROM route_summary_source
            ORDER BY on_time_performance DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query vehicle tracking data
     */
    public void queryVehicleTracking(int limit) throws Exception {
        System.out.println("Vehicle Tracking Data");
        System.out.println("====================");
        
        setupKafkaSource(VEHICLE_TRACKING_TOPIC, "vehicle_tracking_source");
        
        String query = String.format("""
            SELECT 
                vehicle_id,
                route_id,
                current_latitude,
                current_longitude,
                speed_kmh,
                delay_status,
                passenger_load,
                tracking_quality,
                last_position_update
            FROM vehicle_tracking_source
            ORDER BY last_position_update DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query vehicle positions
     */
    public void queryVehiclePositions(int limit) throws Exception {
        System.out.println("Vehicle Positions");
        System.out.println("================");
        
        setupKafkaSource(VEHICLE_POSITIONS_TOPIC, "vehicle_positions_source");
        
        String query = String.format("""
            SELECT 
                vehicle_id,
                route_id,
                latitude,
                longitude,
                bearing,
                speed,
                current_status,
                occupancy_status,
                timestamp_ms
            FROM vehicle_positions_source
            ORDER BY timestamp_ms DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query trip updates
     */
    public void queryTripUpdates(int limit) throws Exception {
        System.out.println("Trip Updates");
        System.out.println("===========");
        
        setupKafkaSource(TRIP_UPDATES_TOPIC, "trip_updates_source");
        
        String query = String.format("""
            SELECT 
                trip_id,
                route_id,
                vehicle_id,
                delay_seconds,
                schedule_relationship,
                start_date,
                start_time,
                timestamp_ms
            FROM trip_updates_source
            ORDER BY timestamp_ms DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query alerts
     */
    public void queryAlerts(int limit) throws Exception {
        System.out.println("Active Alerts");
        System.out.println("=============");
        
        setupKafkaSource(ALERTS_TOPIC, "alerts_source");
        
        String query = String.format("""
            SELECT 
                alert_id,
                cause,
                effect,
                header_text,
                description_text,
                active_period_start,
                active_period_end,
                timestamp_ms
            FROM alerts_source
            ORDER BY timestamp_ms DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query specific route data
     */
    public void querySpecificRoute(String routeId, int limit) throws Exception {
        System.out.println("Route: " + routeId);
        System.out.println("=".repeat(8 + routeId.length()));
        
        setupKafkaSource(COMPREHENSIVE_ROUTES_TOPIC, "comprehensive_routes_source");
        
        String query = String.format("""
            SELECT 
                route_id,
                route_long_name,
                vehicle_id,
                vehicle_latitude,
                vehicle_longitude,
                vehicle_status,
                delay_seconds,
                occupancy_status,
                stop_name,
                last_updated
            FROM comprehensive_routes_source
            WHERE route_id = '%s'
            ORDER BY last_updated DESC
            LIMIT %d
            """, routeId, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query specific vehicle data
     */
    public void querySpecificVehicle(String vehicleId, int limit) throws Exception {
        System.out.println("Vehicle: " + vehicleId);
        System.out.println("=".repeat(9 + vehicleId.length()));
        
        setupKafkaSource(VEHICLE_TRACKING_TOPIC, "vehicle_tracking_source");
        
        String query = String.format("""
            SELECT 
                vehicle_id,
                route_id,
                current_latitude,
                current_longitude,
                speed_kmh,
                status,
                delay_seconds,
                delay_status,
                passenger_load,
                current_stop,
                last_position_update
            FROM vehicle_tracking_source
            WHERE vehicle_id = '%s'
            ORDER BY last_position_update DESC
            LIMIT %d
            """, vehicleId, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query on-time performance across all routes
     */
    public void queryOnTimePerformance(int limit) throws Exception {
        System.out.println("On-Time Performance");
        System.out.println("==================");
        
        setupKafkaSource(ROUTE_SUMMARY_TOPIC, "route_summary_source");
        
        String query = String.format("""
            SELECT 
                route_short_name,
                total_vehicles,
                on_time_performance,
                average_delay_seconds,
                service_status,
                last_updated
            FROM route_summary_source
            WHERE on_time_performance IS NOT NULL
            ORDER BY on_time_performance DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Query delayed services
     */
    public void queryDelayedServices(int limit) throws Exception {
        System.out.println("Delayed Services");
        System.out.println("===============");
        
        setupKafkaSource(VEHICLE_TRACKING_TOPIC, "vehicle_tracking_source");
        
        String query = String.format("""
            SELECT 
                vehicle_id,
                route_id,
                delay_seconds,
                delay_status,
                current_stop,
                passenger_load,
                last_position_update
            FROM vehicle_tracking_source
            WHERE delay_status IN ('DELAYED', 'SEVERELY_DELAYED')
            ORDER BY delay_seconds DESC
            LIMIT %d
            """, limit);
            
        executeQuery(query);
    }
    
    /**
     * Live monitoring mode - continuously displays updates
     */
    public void liveMonitor(int limit) throws Exception {
        System.out.println("Live RTD Transit Monitor");
        System.out.println("=======================");
        System.out.println("Press Ctrl+C to exit");
        System.out.println();
        
        // Setup all sources for live monitoring
        setupKafkaSource(COMPREHENSIVE_ROUTES_TOPIC, "comprehensive_routes_source");
        setupKafkaSource(ROUTE_SUMMARY_TOPIC, "route_summary_source");
        setupKafkaSource(ALERTS_TOPIC, "alerts_source");
        
        // Display current time and summary
        while (true) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("RTD Live Status - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            System.out.println("=".repeat(60));
            
            // Show route summary
            String summaryQuery = String.format("""
                SELECT 
                    route_short_name,
                    total_vehicles,
                    on_time_performance,
                    service_status
                FROM route_summary_source
                WHERE total_vehicles > 0
                ORDER BY route_short_name
                LIMIT %d
                """, limit);
            
            System.out.println("\nRoute Status:");
            executeQuery(summaryQuery, false);
            
            // Show any alerts
            String alertsQuery = """
                SELECT 
                    alert_id,
                    header_text,
                    effect
                FROM alerts_source
                WHERE active_period_end > UNIX_TIMESTAMP() * 1000
                LIMIT 5
                """;
            
            System.out.println("\nActive Alerts:");
            executeQuery(alertsQuery, false);
            
            // Wait 30 seconds before next update
            Thread.sleep(30000);
        }
    }
    
    /**
     * Setup Kafka source for a specific topic
     */
    private void setupKafkaSource(String topic, String tableName) {
        String createTableSql = String.format("""
            CREATE TABLE IF NOT EXISTS %s (
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
                -- Additional fields for different table types
                total_vehicles INT,
                active_vehicles INT,
                average_delay_seconds DOUBLE,
                on_time_performance DOUBLE,
                total_alerts INT,
                service_status STRING,
                current_latitude DOUBLE,
                current_longitude DOUBLE,
                bearing FLOAT,
                speed_kmh DOUBLE,
                status STRING,
                occupancy STRING,
                delay_status STRING,
                current_stop STRING,
                next_stop STRING,
                distance_to_next_stop DOUBLE,
                estimated_arrival_time BIGINT,
                passenger_load STRING,
                service_alerts ARRAY<STRING>,
                last_position_update BIGINT,
                tracking_quality STRING,
                -- Raw table fields
                latitude DOUBLE,
                longitude DOUBLE,
                speed FLOAT,
                timestamp_ms BIGINT,
                current_status STRING,
                congestion_level STRING,
                start_date STRING,
                start_time STRING,
                alert_id STRING,
                cause STRING,
                effect STRING,
                header_text STRING,
                description_text STRING,
                url STRING,
                active_period_start BIGINT,
                active_period_end BIGINT
            ) WITH (
                'connector' = 'kafka',
                'topic' = '%s',
                'properties.bootstrap.servers' = '%s',
                'properties.group.id' = 'rtd-query-client',
                'scan.startup.mode' = 'latest-offset',
                'format' = 'json',
                'json.fail-on-missing-field' = 'false',
                'json.ignore-parse-errors' = 'true'
            )
            """, tableName, topic, KAFKA_BOOTSTRAP_SERVERS);
        
        try {
            tableEnv.executeSql(createTableSql);
            LOG.info("Created table {} for topic {}", tableName, topic);
        } catch (Exception e) {
            LOG.warn("Could not create table {} (may already exist): {}", tableName, e.getMessage());
        }
    }
    
    /**
     * Execute a SQL query and display results
     */
    private void executeQuery(String query) throws Exception {
        executeQuery(query, true);
    }
    
    private void executeQuery(String query, boolean waitForResults) throws Exception {
        try {
            TableResult result = tableEnv.executeSql(query);
            
            if (waitForResults) {
                // Print results with timeout
                result.print();
            } else {
                // For live monitoring, just execute and return quickly
                result.getJobClient().ifPresent(client -> {
                    try {
                        Thread.sleep(1000); // Give it a moment to fetch some data
                        client.cancel();
                    } catch (Exception e) {
                        LOG.debug("Error canceling job: {}", e.getMessage());
                    }
                });
            }
            
        } catch (Exception e) {
            System.err.println("Query execution error: " + e.getMessage());
            LOG.error("Query failed: {}", query, e);
            
            // Provide helpful suggestions
            if (e.getMessage().contains("timeout") || e.getMessage().contains("empty")) {
                System.err.println("\nTroubleshooting:");
                System.err.println("1. Ensure the RTD GTFS-RT pipeline is running");
                System.err.println("2. Check that Kafka is running on " + KAFKA_BOOTSTRAP_SERVERS);
                System.err.println("3. Verify that data is being produced to the topics");
                System.err.println("4. Try increasing the query timeout or checking topic contents");
            }
        }
    }
}