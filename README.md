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

Create the required topics:
```bash
# Create topics for the three data streams
kafka-topics --create --bootstrap-server localhost:9092 --topic rtd.vehicle.positions
kafka-topics --create --bootstrap-server localhost:9092 --topic rtd.trip.updates  
kafka-topics --create --bootstrap-server localhost:9092 --topic rtd.alerts
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

### 5. Monitor Output

Monitor the Kafka topics to see real-time data:
```bash
# Monitor vehicle positions
kafka-console-consumer --bootstrap-server localhost:9092 --topic rtd.vehicle.positions --from-beginning

# Monitor trip updates
kafka-console-consumer --bootstrap-server localhost:9092 --topic rtd.trip.updates --from-beginning

# Monitor alerts
kafka-console-consumer --bootstrap-server localhost:9092 --topic rtd.alerts --from-beginning
```

## Configuration

### Pipeline Settings
- **Fetch Interval**: 1 hour (3600 seconds)
- **Parallelism**: 1 (configurable)
- **Checkpointing**: Every 60 seconds with EXACTLY_ONCE semantics
- **Watermarks**: 1-minute tolerance for out-of-order events

### Kafka Configuration
The pipeline outputs to three Kafka topics:
- **rtd.vehicle.positions**: Real-time vehicle location and status data
- **rtd.trip.updates**: Schedule adherence and delay information
- **rtd.alerts**: Service disruption alerts

Default Kafka settings:
- **Bootstrap Servers**: `localhost:9092`
- **Format**: JSON
- **Topics**: Auto-created if they don't exist

To modify Kafka settings, update the constants in `RTDGTFSPipeline.java`:
```java
private static final String KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
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

### Vehicle Positions
- `vehicle_id`: Unique vehicle identifier
- `trip_id`: Current trip identifier
- `route_id`: Route being served
- `latitude`, `longitude`: GPS coordinates
- `bearing`: Vehicle heading (0-360 degrees)
- `speed`: Current speed
- `current_status`: In transit, stopped, etc.
- `congestion_level`: Traffic conditions
- `occupancy_status`: Passenger load

### Trip Updates
- `trip_id`: Trip identifier
- `route_id`: Route identifier
- `vehicle_id`: Assigned vehicle
- `start_date`, `start_time`: Scheduled trip start
- `schedule_relationship`: On time, canceled, etc.
- `delay_seconds`: Schedule deviation

### Service Alerts
- `alert_id`: Unique alert identifier
- `cause`: Reason for alert
- `effect`: Impact description
- `header_text`: Brief summary
- `description_text`: Detailed information
- `url`: Additional information link
- `active_period_start`, `active_period_end`: Validity timeframe

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