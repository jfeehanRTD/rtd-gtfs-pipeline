# RTD GTFS-RT Data Pipeline

A real-time transit data processing pipeline built with Apache Flink that downloads and processes GTFS-RT (General Transit Feed Specification - Real Time) feeds from RTD Denver's public transit system.

## Overview

This application processes three types of real-time transit data from RTD Denver:
- **Vehicle Positions**: Real-time location and status of transit vehicles
- **Trip Updates**: Schedule adherence and delay information
- **Service Alerts**: Disruptions, detours, and other service announcements

The pipeline fetches data from RTD's public endpoints every minute, processes it using Apache Flink's streaming capabilities, and outputs structured data that can be easily integrated with databases, message queues, or other downstream systems.

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
1. **HTTP Source**: Downloads protobuf data from RTD endpoints every minute
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
- **Apache Flink 2.0.0** (for cluster deployment)
- **Docker Environment**: 
  - **macOS**: Colima (recommended) - `brew install colima docker docker-compose`
  - **Linux**: Docker Engine + Docker Compose
  - **Windows**: Docker Desktop or WSL2 with Docker
- **Apache Kafka 4.0.0** (provided via Docker)

**Note:** The project includes built-in Kafka console tools and Docker environment, so you don't need separate Kafka installation.

## Version 1 Updates

This version includes major improvements for live RTD data integration:

- ✅ **Flink 2.0.0 Compatibility**: Resolved ClassNotFoundException issues with SimpleUdfStreamOperatorFactory
- ✅ **Live Data Integration**: Successfully tested with 468+ active RTD vehicles
- ✅ **Multiple Data Sinks**: JSON file, CSV file, and console output formats
- ✅ **Enhanced Error Handling**: Robust null safety and connection recovery
- ✅ **Real-time Testing**: Live GPS coordinates from Denver metro transit system

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

### 2. Set Up Kafka 4.0.0 Environment

The project includes a complete Docker setup for Kafka 4.0.0 with KRaft (no ZooKeeper).

**Prerequisites (macOS):**
```bash
# Install Colima (lightweight Docker alternative)
brew install colima docker docker-compose

# Start Colima VM
./scripts/docker-setup colima start
# or manually: colima start --cpu 4 --memory 8
```

**Quick Setup:**
```bash
# Complete setup: Start Kafka and create all RTD topics
./scripts/docker-setup setup

# Or step by step:
./scripts/docker-setup start        # Start Kafka 4.0.0
./scripts/docker-setup status       # Check if running
./scripts/kafka-topics --create-rtd-topics  # Create topics

# Other useful commands:
./scripts/docker-setup stop         # Stop services
./scripts/docker-setup clean        # Stop and remove all data
./scripts/docker-setup logs         # View logs
./scripts/docker-setup ui           # Open Kafka UI (http://localhost:8080)
./scripts/docker-setup colima       # Manage Colima VM
```

Manual Docker Compose (if preferred):
```bash
docker-compose up -d                # Start services
docker-compose down                 # Stop services
docker-compose logs kafka           # View Kafka logs
```

**Topic Creation:**
The `./scripts/docker-setup setup` command automatically creates all RTD topics, or you can create them manually:

```bash
# Create all RTD topics at once (recommended)
./scripts/kafka-topics --create-rtd-topics

# Or create topics individually if needed
./scripts/kafka-topics --create --topic rtd.comprehensive.routes --partitions 3
./scripts/kafka-topics --create --topic rtd.route.summary --partitions 1
./scripts/kafka-topics --create --topic rtd.vehicle.tracking --partitions 2
./scripts/kafka-topics --create --topic rtd.vehicle.positions --partitions 2
./scripts/kafka-topics --create --topic rtd.trip.updates --partitions 2
./scripts/kafka-topics --create --topic rtd.alerts --partitions 1

# Verify topics were created
./scripts/kafka-topics --list
```

### 3. Test Live RTD Data Integration (New in Version 1)

**Quick Live Data Test (No Flink, Direct Connection)**
```bash
# Test live RTD connection and data parsing (recommended first test)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"

# Expected output: 468+ active vehicles with real GPS coordinates
# Sample: Vehicle: 3BEA612044CDF52FE063DC4D1FAC7665 | Route: 40 | Position: (39.696934, -104.940514)
```

**Flink Data Sink Testing**
```bash
# Test Flink 2.0.0 with multiple data sinks (JSON, CSV, Console)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.FlinkSinkTest"

# Creates output files in:
# - ./flink-output/rtd-data/ (JSON format)
# - ./flink-output/rtd-data-csv/ (CSV format) 
# - Console output with real-time vehicle positions
```

**Simple Pipeline Test (Minimal Flink)**
```bash
# Test basic Flink 2.0.0 pipeline execution
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.SimpleRTDPipeline"

# Falls back to DirectRTDTest if Flink execution fails
# Demonstrates both Flink and direct data access patterns
```

**Additional Test Pipelines**
```bash
# Test RTD pipeline with shorter fetch intervals (30 seconds)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDTestPipeline"

# Test data generation for development (when RTD unavailable)  
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DataGenTestPipeline"
```

### 4. Run Production Pipeline (Original)

The full production pipeline with Flink 2.0.0:

```bash
# Run with Maven (recommended for development)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSPipeline"
```

Or run the packaged JAR directly:
```bash
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDGTFSPipeline
```

### 5. Deploy to Flink Cluster

For production deployment on a Flink cluster:

```bash
# Submit job to running Flink cluster
flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

### 6. Query Data with RTD Query Client

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

### 7. Monitor Raw Kafka Topics

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
- **Fetch Interval**: 1 minute (60 seconds)
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
- **`./scripts/kafka-topics`**: Create, list, describe, and manage Kafka topics using modern Admin API
- **`./scripts/kafka-console-consumer`**: Monitor Kafka topics with real-time data using Consumer API
- **Kafka 4.0.0 Compatible**: Uses Java Admin/Consumer APIs instead of deprecated command-line tools
- Includes RTD-specific shortcuts and helpful usage examples

### Docker Environment Management
- **`./scripts/docker-setup`**: Complete Docker environment management for Kafka 4.0.0
- **KRaft Mode**: Modern Kafka without ZooKeeper dependency
- **Kafka UI**: Web interface at http://localhost:8080 for visual management
- **One-command setup**: `./scripts/docker-setup setup` starts everything

### Usage Examples
```bash
# Complete environment setup
./scripts/docker-setup setup           # Start Kafka + create topics
./scripts/rtd-query health             # Verify connectivity

# Query structured data
./scripts/rtd-query routes 20
./scripts/rtd-query live

# Monitor raw Kafka streams  
./scripts/kafka-console-consumer --topic rtd.alerts --from-beginning
./scripts/kafka-topics --list

# Docker management
./scripts/docker-setup status          # Check services
./scripts/docker-setup ui              # Open Kafka UI
./scripts/docker-setup clean           # Clean shutdown
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

## Troubleshooting (Version 1)

### Common Issues and Solutions

**Issue: ClassNotFoundException: SimpleUdfStreamOperatorFactory**
```bash
# Problem: Flink version compatibility issue
# Solution: Verify Flink 2.0.0 is being used
mvn dependency:tree | grep flink-core
# Should show: flink-core:jar:2.0.0

# Test with direct RTD connection first
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"
```

**Issue: No live RTD data or connection timeout**
```bash
# Test RTD endpoint connectivity
curl -I https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb
# Should return: HTTP/2 200

# Run connection diagnostic
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"
# Should show: "Downloaded XXXXX bytes of GTFS-RT data"
```

**Issue: Flink execution fails with serialization errors**
```bash
# This is a known issue with Flink 2.0.0 operator factories
# Workaround: Use direct data access pattern
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"

# Or test data sink configuration without execution
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.FlinkSinkTest"
```

**Issue: Missing output files from Flink sinks**
```bash
# Check if output directories were created
ls -la ./flink-output/
# Should show: rtd-data/ and rtd-data-csv/ directories

# Verify Flink has write permissions
mkdir -p ./flink-output/test && echo "test" > ./flink-output/test/write-test.txt
```

**Issue: SLF4J warnings or logging issues**
```bash
# These warnings are harmless but can be suppressed
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest" 2>/dev/null

# Or set log level to ERROR only
export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=error"
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"
```

### Pipeline Selection Guide

**For Testing Live RTD Connection:**
- Use `DirectRTDTest` - No Flink dependencies, pure HTTP/protobuf test
- Shows real vehicle count, GPS coordinates, route information
- Best for verifying RTD endpoint availability

**For Testing Flink Data Sinks:**
- Use `FlinkSinkTest` - Tests JSON/CSV file output configuration
- Creates local output files you can inspect
- Shows data sink setup without complex Flink execution

**For Production Pipeline:**
- Use `RTDGTFSPipeline` - Full pipeline with Kafka integration
- Requires running Kafka cluster
- Handles all three GTFS-RT feed types

### Expected Test Results

**DirectRTDTest Success:**
```
=== LIVE RTD DATA ===
Feed Timestamp: 2025-08-11 15:41:51 MDT
Total Entities: 468
✅ Successfully connected to RTD GTFS-RT feed
✅ Found 468 active vehicles
```

**FlinkSinkTest Configuration Success:**
```
✅ Created RTD data source
✅ Configured multiple Flink sinks:
   - Print/Console sink for real-time monitoring
   - JSON file sink for structured storage
   - CSV file sink for analytics
```

## Dependencies

- **Apache Flink 2.0.0**: Stream processing engine with legacy API support
- **Flink Kafka Connector 4.0.0-2.0**: Kafka integration compatible with Kafka 4.0.0
- **Apache Kafka 4.0.0**: Modern Kafka with KRaft (no ZooKeeper) and improved performance
- **GTFS-RT Bindings 0.0.4**: Google's protobuf library for GTFS-RT
- **Apache HttpComponents 4.5.14**: HTTP client for feed downloads
- **Jackson 2.18.1**: Latest JSON processing with enhanced performance
- **SLF4J 2.0.16 + Log4j2 2.24.3**: Modern logging framework

## License

This project processes publicly available GTFS-RT data from RTD Denver. Please comply with RTD's terms of service when using their data feeds.

## Quick Command Reference (Version 1)

### Essential Test Commands
```bash
# Quick live data test (recommended first step)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest"

# Test Flink data sinks with file output
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.FlinkSinkTest"

# Test basic Flink pipeline
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.SimpleRTDPipeline"

# Full production pipeline
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSPipeline"
```

### Build and Setup Commands
```bash
# Clean build and compile
mvn clean compile

# Run all unit tests
mvn test

# Package for deployment
mvn clean package -DskipTests

# Check Flink version
mvn dependency:tree | grep flink-core
```

### Output Verification Commands
```bash
# Check RTD endpoint availability
curl -I https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb

# Verify output files created by FlinkSinkTest
ls -la ./flink-output/rtd-data/
ls -la ./flink-output/rtd-data-csv/

# Check for active vehicles data
grep -i "vehicle" ./flink-output/rtd-data/*.txt 2>/dev/null | head -5
```

### Troubleshooting Commands
```bash
# Run tests without warnings
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectRTDTest" 2>/dev/null

# Check Java and Maven versions
java -version && mvn -version

# Clean all build artifacts and output
mvn clean && rm -rf ./flink-output/
```

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