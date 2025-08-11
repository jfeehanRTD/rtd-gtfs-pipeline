# RTD GTFS-RT Data Pipeline

A real-time transit data processing pipeline built with Apache Flink that downloads and processes GTFS-RT (General Transit Feed Specification - Real Time) feeds from RTD Denver's public transit system.

## Overview

This application processes three types of real-time transit data from RTD Denver:
- **Vehicle Positions**: Real-time location and status of transit vehicles
- **Trip Updates**: Schedule adherence and delay information
- **Service Alerts**: Disruptions, detours, and other service announcements

The pipeline fetches data from RTD's public endpoints every hour, processes it using Apache Flink's streaming capabilities, and outputs structured data that can be easily integrated with databases, message queues, or other downstream systems.

## Architecture

### Components
- **RTDGTFSPipeline**: Main orchestration class that sets up the Flink job
- **GTFSRealtimeSource**: Custom source function for downloading GTFS-RT protobuf feeds
- **Data Models**: 
  - `VehiclePosition`: Real-time vehicle location and status
  - `TripUpdate`: Trip schedule and delay information  
  - `Alert`: Service disruption alerts
- **Table API Integration**: Structured data processing and configurable output sinks

### Data Flow
1. **HTTP Source**: Downloads protobuf data from RTD endpoints every hour
2. **Deserialization**: Converts GTFS-RT protobuf messages to Java objects
3. **Stream Processing**: Applies watermarks and timestamp assignment
4. **Table API**: Converts streams to SQL-queryable tables
5. **Sinks**: Outputs to configurable destinations (currently print connector)

### Feed Endpoints
- Vehicle Positions: `https://www.rtd-denver.com/google_sync/VehiclePosition.pb`
- Trip Updates: `https://www.rtd-denver.com/google_sync/TripUpdate.pb`
- Service Alerts: `https://www.rtd-denver.com/google_sync/Alert.pb`

## Prerequisites

- **Java 24** or higher
- **Maven 3.6+**
- **Apache Flink 1.18.0** (for cluster deployment)
- **Apache Kafka** (for data output)

**Note:** The project includes built-in Kafka console tools (`./scripts/kafka-topics` and `./scripts/kafka-console-consumer`), so you don't need to install Kafka tools separately.

## Quick Start

### 1. Build the Application

```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application (includes dependencies)
mvn clean package
```

For faster builds without tests:
```bash
mvn clean package -DskipTests
```

### 2. Set Up Kafka

Start Kafka locally (using Docker):
```bash
# Start Kafka with Docker Compose
docker-compose up -d

# Or manually start Kafka
docker run -d --name kafka \
  -p 9092:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  confluentinc/cp-kafka:latest
```

Create the required topics using the built-in Kafka tools:
```bash
# Easy way: Create all RTD topics at once
./scripts/kafka-topics --create-rtd-topics

# Manual way: Create topics individually
./scripts/kafka-topics --create --topic rtd.comprehensive.routes --partitions 3
./scripts/kafka-topics --create --topic rtd.route.summary --partitions 1
./scripts/kafka-topics --create --topic rtd.vehicle.tracking --partitions 2
./scripts/kafka-topics --create --topic rtd.vehicle.positions --partitions 2
./scripts/kafka-topics --create --topic rtd.trip.updates --partitions 2
./scripts/kafka-topics --create --topic rtd.alerts --partitions 1
```

Or with standard Kafka tools (if you have them installed):
```bash
kafka-topics --create --bootstrap-server localhost:9092 --topic rtd.comprehensive.routes --partitions 3
kafka-topics --create --bootstrap-server localhost:9092 --topic rtd.route.summary --partitions 1
# ... etc
```

### 3. Run Locally (Development)

The application includes a Flink mini-cluster for local development and testing:

```bash
# Run with Maven (recommended for development)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSPipeline"
```

Or run the packaged JAR directly:
```bash
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDGTFSPipeline
```

### 4. Deploy to Flink Cluster

For production deployment on a Flink cluster:

```bash
# Submit job to running Flink cluster
flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

### 5. Query Data with RTD Query Client

Use the built-in command-line query client to easily access all Flink data sinks:

```bash
# First, run a health check to verify connectivity
./scripts/rtd-query health

# List all available data sources
./scripts/rtd-query list

# Query comprehensive route data (combines GTFS + GTFS-RT)
./scripts/rtd-query routes 20

# Check route performance statistics
./scripts/rtd-query summary

# Monitor individual vehicle tracking
./scripts/rtd-query tracking 15

# View raw vehicle positions
./scripts/rtd-query positions

# Check trip delays and updates
./scripts/rtd-query updates

# View active service alerts
./scripts/rtd-query alerts

# Start live monitoring mode (updates every 30 seconds)
./scripts/rtd-query live

# Test Kafka connectivity
./scripts/rtd-query test
```

### 6. Monitor Raw Kafka Topics

The project includes built-in Kafka tools so you don't need to install Kafka separately:

```bash
# Create all RTD topics at once
./scripts/kafka-topics --create-rtd-topics

# List all available topics
./scripts/kafka-topics --list

# Monitor comprehensive routes data from beginning
./scripts/kafka-console-consumer --topic rtd.comprehensive.routes --from-beginning --max-messages 10

# Monitor vehicle positions
./scripts/kafka-console-consumer --topic rtd.vehicle.positions --from-beginning

# Monitor trip updates  
./scripts/kafka-console-consumer --topic rtd.trip.updates --from-beginning

# Monitor alerts
./scripts/kafka-console-consumer --topic rtd.alerts --from-beginning

# Monitor with custom settings
./scripts/kafka-console-consumer --topic rtd.route.summary --max-messages 20 --timeout-ms 5000
```

If you have Kafka installed separately, you can also use the standard commands:
```bash
kafka-console-consumer --bootstrap-server localhost:9092 --topic rtd.comprehensive.routes --from-beginning
```

## Configuration

### Pipeline Settings
- **Fetch Interval**: 1 hour (3600 seconds)
- **Parallelism**: 1 (configurable)
- **Checkpointing**: Every 60 seconds with EXACTLY_ONCE semantics
- **Watermarks**: 1-minute tolerance for out-of-order events

### Kafka Configuration

The pipeline outputs to six Kafka topics organized in two categories:

**Comprehensive Data Sinks (Enhanced with GTFS data):**
- **rtd.comprehensive.routes**: Complete vehicle and route data with real-time positions, schedule information, and stop details
- **rtd.route.summary**: Aggregated statistics per route including on-time performance and active vehicle counts
- **rtd.vehicle.tracking**: Enhanced individual vehicle monitoring with speed, passenger load, and tracking quality metrics

**Raw GTFS-RT Data Streams:**
- **rtd.vehicle.positions**: Direct real-time vehicle location and status data
- **rtd.trip.updates**: Schedule adherence and delay information
- **rtd.alerts**: Service disruption alerts

Default Kafka settings:
- **Bootstrap Servers**: `localhost:9092`
- **Format**: JSON
- **Topics**: Auto-created if they don't exist

To modify Kafka settings, update the constants in `RTDGTFSPipeline.java`:
```java
private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
private static final String COMPREHENSIVE_ROUTES_TOPIC = "rtd.comprehensive.routes";
private static final String ROUTE_SUMMARY_TOPIC = "rtd.route.summary";
private static final String VEHICLE_TRACKING_TOPIC = "rtd.vehicle.tracking";
private static final String VEHICLE_POSITIONS_TOPIC = "rtd.vehicle.positions";
private static final String TRIP_UPDATES_TOPIC = "rtd.trip.updates";
private static final String ALERTS_TOPIC = "rtd.alerts";
```

### Alternative Output Configuration
The pipeline can be reconfigured for other sinks:
- **Databases**: PostgreSQL, MySQL, etc.
- **Elasticsearch**: Search and analytics
- **File Systems**: Parquet, JSON, CSV formats

To change output destinations, modify the sink table definitions in `RTDGTFSPipeline.java`.

## Data Schema

### Comprehensive Routes (Enhanced Sink)
Combines GTFS schedule data with real-time GTFS-RT information:
- `route_id`, `route_short_name`, `route_long_name`: Route identification
- `vehicle_id`, `trip_id`: Vehicle and trip identifiers
- `latitude`, `longitude`, `bearing`: Real-time GPS position and heading
- `speed_kmh`: Current speed in kilometers per hour
- `current_status`: Vehicle status (IN_TRANSIT_TO, STOPPED_AT, etc.)
- `stop_id`, `stop_name`: Current or next stop information
- `delay_seconds`: Schedule deviation in seconds
- `occupancy_status`: Passenger load (EMPTY, FEW_SEATS, STANDING_ROOM, etc.)
- `vehicle_status`: Operational status
- `schedule_relationship`: Relationship to schedule (SCHEDULED, ADDED, CANCELED)

### Route Summary (Aggregated Statistics)
Performance metrics grouped by route:
- `route_id`, `route_short_name`: Route identification
- `active_vehicles`, `total_vehicles`: Vehicle counts
- `on_time_performance`: Percentage of on-time vehicles
- `average_delay_seconds`: Mean delay across all vehicles
- `service_status`: Overall route status (NORMAL, DELAYED, DISRUPTED)

### Vehicle Tracking (Enhanced Individual Monitoring)
Enhanced vehicle-specific data:
- `vehicle_id`, `route_id`: Vehicle and route identifiers
- `latitude`, `longitude`, `speed_kmh`: Position and speed
- `passenger_load`: Estimated passenger count or load level
- `tracking_quality`: GPS signal quality and data freshness
- `last_updated`: Timestamp of last position update

### Raw GTFS-RT Data Streams

**Vehicle Positions:**
- `vehicle_id`: Unique vehicle identifier
- `trip_id`: Current trip identifier
- `route_id`: Route being served
- `latitude`, `longitude`: GPS coordinates
- `bearing`: Vehicle heading (0-360 degrees)
- `speed`: Current speed
- `current_status`: In transit, stopped, etc.
- `congestion_level`: Traffic conditions
- `occupancy_status`: Passenger load

**Trip Updates:**
- `trip_id`: Trip identifier
- `route_id`: Route identifier
- `vehicle_id`: Assigned vehicle
- `start_date`, `start_time`: Scheduled trip start
- `schedule_relationship`: On time, canceled, etc.
- `delay_seconds`: Schedule deviation

**Service Alerts:**
- `alert_id`: Unique alert identifier
- `cause`: Reason for alert
- `effect`: Impact description
- `header_text`: Brief summary
- `description_text`: Detailed information
- `url`: Additional information link
- `active_period_start`, `active_period_end`: Validity timeframe

## Built-in Tools

The project includes several convenient command-line tools:

### RTD Query Client
- **`./scripts/rtd-query`**: Query and monitor all Flink data sinks with formatted output
- Supports live monitoring, health checks, and connectivity testing
- No external dependencies required

### Kafka Tools (No Installation Required)
- **`./scripts/kafka-topics`**: Create, list, describe, and manage Kafka topics
- **`./scripts/kafka-console-consumer`**: Monitor Kafka topics with real-time data
- Includes RTD-specific shortcuts and helpful usage examples

### Usage Examples
```bash
# Health check and setup
./scripts/rtd-query health
./scripts/kafka-topics --create-rtd-topics

# Query structured data
./scripts/rtd-query routes 20
./scripts/rtd-query live

# Monitor raw Kafka streams  
./scripts/kafka-console-consumer --topic rtd.alerts --from-beginning
./scripts/kafka-topics --list
```

## Monitoring

The application includes:
- **Flink Web UI**: Available at http://localhost:8081 when running locally
- **Structured Logging**: SLF4J with Log4j2 backend
- **Checkpointing**: Automatic state recovery and fault tolerance

## Development

### Project Structure
```
src/
├── main/java/com/rtd/pipeline/
│   ├── RTDGTFSPipeline.java          # Main pipeline class
│   ├── model/                        # Data models
│   │   ├── VehiclePosition.java
│   │   ├── TripUpdate.java
│   │   └── Alert.java
│   └── source/
│       └── GTFSRealtimeSource.java   # Custom Flink source
└── test/java/                        # Test classes
```

### Adding New Features
1. Extend data models in the `model` package
2. Modify `GTFSRealtimeSource` for additional data processing
3. Update table schemas in `RTDGTFSPipeline`
4. Configure appropriate sinks for your use case

### Testing

The project includes comprehensive test suites for validating GTFS-RT data and detecting service issues:

```bash
# Run all tests
mvn test

# Run all tests without warnings (clean output)
mvn test 2>/dev/null

# Run specific test
mvn test -Dtest=YourTestClass

# Run validation tests only
mvn test -Dtest="*ValidationTest"

# Run schedule adherence tests
mvn test -Dtest="*ScheduleAdherenceTest,EnhancedScheduleAdherenceTest"

# Run service disruption tests
mvn test -Dtest="ServiceDisruptionPatternTest"

# Clean test runner script (suppresses warnings)
./test-clean.sh                                    # Run all tests
./test-clean.sh -Dtest="*ValidationTest"          # Run specific tests
```

**Note:** You may see warnings about deprecated `sun.misc.Unsafe` methods when running tests. These are harmless warnings from Maven/Guice internal operations on Java 17+ and don't affect test functionality. For clean output, use `mvn test 2>/dev/null` or the provided `./test-clean.sh` script.

#### Test Coverage

**Data Validation Tests:**
- `VehiclePositionValidationTest`: Validates GPS coordinates, timestamps, and vehicle data
- `TripUpdateValidationTest`: Validates trip schedules and delay information
- `AlertValidationTest`: Validates service alert structure and content
- `ComprehensiveValidationTest`: End-to-end validation scenarios

**Service Quality Tests:**
- `ScheduleAdherenceTest`: Detects missing vehicles and trains more than 3 minutes late
- `EnhancedScheduleAdherenceTest`: Advanced detection including:
  - Ghost trains (unscheduled vehicles)
  - Cascading delays across routes
  - Schedule recovery tracking
- `ServiceDisruptionPatternTest`: Pattern analysis including:
  - Partial route disruptions
  - Historical delay patterns
  - Anomaly detection
  - Rush hour and day-of-week patterns

**Historical Analysis Tests:**
- `HistoricalDataModelTest`: Tests data structures for tracking service history
- `RTDLightRailTrackingTest`: Comprehensive light rail service monitoring

### Service Monitoring Capabilities

The enhanced test suite provides RTD with powerful monitoring capabilities:

1. **Missing Train Detection**
   - Identifies scheduled trips with no real-time data
   - Detects vehicles with outdated GPS positions (>10 minutes)
   - Tracks partial route coverage gaps

2. **Delay Analysis**
   - Categorizes delays: on-time (<1 min), slightly late (1-3 min), significantly late (>3 min)
   - Tracks cascading delays across connecting routes
   - Monitors schedule recovery progress

3. **Ghost Train Detection**
   - Identifies unscheduled vehicles operating on routes
   - Suggests reasons (replacement service, express runs, operational adjustments)

4. **Pattern Recognition**
   - Learns historical delay patterns by route, station, time, and day
   - Detects anomalies that deviate from expected patterns
   - Identifies chronic delay locations

5. **Disruption Classification**
   - Categorizes disruption types (signal problems, weather, incidents)
   - Estimates impact duration and affected segments
   - Provides severity scoring

## Dependencies

- **Apache Flink 1.18.0**: Stream processing engine
- **Flink Kafka Connector 3.2.0-1.18**: Kafka integration (latest for Flink 1.18)
- **GTFS-RT Bindings 0.0.4**: Google's protobuf library for GTFS-RT
- **Apache HttpComponents 4.5.14**: HTTP client for feed downloads
- **Jackson 2.15.2**: JSON processing
- **SLF4J + Log4j2**: Logging framework

## License

This project processes publicly available GTFS-RT data from RTD Denver. Please comply with RTD's terms of service when using their data feeds.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Run tests to ensure functionality
4. Submit a pull request

## Support

For issues with the pipeline, check:
1. Flink cluster status and logs
2. Network connectivity to RTD endpoints
3. Java and Maven versions
4. Application logs for error details