# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RTD GTFS-RT Data Pipeline - A Java application using Apache Flink to download and process real-time transit feeds from RTD Denver.

## Known Issues and Resolutions

### Flink Version Compatibility Issue
**Problem**: The pipeline was initially configured with Flink 2.1.0, which has breaking changes that cause `ClassNotFoundException` for `SimpleUdfStreamOperatorFactory` and other classes.

**Root Cause**: Flink 2.x introduced significant API changes and removed several classes that were present in Flink 1.x.

**Resolution**: Downgraded to Flink 1.19.1 for compatibility. The pom.xml has been updated with:
- `flink.version`: 1.19.1
- `flink-connector-kafka`: 3.2.0-1.19 (compatible with Flink 1.19.x)

**Error Symptoms**:
- `java.lang.ClassNotFoundException: org.apache.flink.streaming.api.operators.SimpleUdfStreamOperatorFactory`
- `Could not deserialize stream node X`
- Job submission failures

If you encounter serialization/deserialization errors with Flink, check version compatibility first.

## Build and Run Commands

### Prerequisites
- Java 24
- Maven 3.6+
- Apache Flink 1.19.1 (for cluster deployment)
- Apache Kafka 4.0.0 (for data output)

### Build Commands
```bash
# Clean and compile
mvn clean compile

# Run tests
mvn test

# Package application
mvn clean package

# Package without tests (faster)
mvn clean package -DskipTests
```

### Run Commands

#### Local Development (Flink Mini Cluster)
```bash
# Run with Maven (includes all dependencies)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSPipeline"

# Run packaged JAR
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDGTFSPipeline
```

#### Flink Cluster Deployment
```bash
# Submit to Flink cluster
flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

## Architecture Overview

### High-Level Components
- **RTDGTFSPipeline**: Main job class that orchestrates the entire pipeline
- **GTFSRealtimeSource**: Custom source function that downloads GTFS-RT feeds every hour
- **Data Models**: VehiclePosition, TripUpdate, Alert - representing the three GTFS-RT feed types
- **Table API Sinks**: Print connectors for processed data output

### Data Flow
1. **Source**: HTTP requests to RTD GTFS-RT endpoints every hour
2. **Parse**: Protobuf deserialization of GTFS-RT feeds
3. **Transform**: Conversion to structured data models
4. **Sink**: Table API output (currently print connector, easily configurable for databases)

### Feed URLs
- Vehicle Positions: `https://www.rtd-denver.com/google_sync/VehiclePosition.pb`
- Trip Updates: `https://www.rtd-denver.com/google_sync/TripUpdate.pb`
- Alerts: `https://www.rtd-denver.com/google_sync/Alert.pb`

## Development Guidelines

- Always run tests after making code changes to ensure functionality remains intact
- The pipeline processes three separate GTFS-RT feed types in parallel streams
- Each feed is fetched hourly and parsed using the Google GTFS-RT protobuf library
- Watermarks are configured for 1-minute out-of-order tolerance
- Table API sinks can be easily reconfigured for different output formats (Kafka, databases, etc.)