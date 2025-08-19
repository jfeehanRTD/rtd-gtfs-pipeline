# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RTD GTFS-RT Data Pipeline - A Java application using Apache Flink to download and process real-time transit feeds from RTD Denver.

## Current Flink Version

**Active Version**: Apache Flink 2.1.0
- `flink.version`: 2.1.0
- `flink-connector-kafka`: 4.0.0-2.0 (compatible with Flink 2.0+)

**Important Notes**:
- The pipeline has been successfully migrated to Flink 2.1.0
- All deprecated APIs have been updated to use modern Flink 2.x patterns
- Use DataStream API directly instead of legacy Table API when possible
- File sinks use the modern `FileSink` API with rolling policies

**Migration Highlights**:
- Replaced legacy source functions with modern Source API where applicable
- Updated file sinks to use `FileSink.forRowFormat()`
- Kafka connector uses version 4.0.0-2.0 for Flink 2.x compatibility
- All serialization uses proper TypeInformation or Protobuf serialization

## Build and Run Commands

### Prerequisites
- Java 24
- Maven 3.6+
- Apache Flink 2.1.0 (for cluster deployment)
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

#### RTD Control Script (Recommended)
The easiest way to manage the RTD pipeline and React web app:

```bash
# Start both Java pipeline and React app
./rtd-control.sh start

# Start only specific services
./rtd-control.sh start java    # Java pipeline only
./rtd-control.sh start react   # React app only

# Check status of all services
./rtd-control.sh status

# Stop services
./rtd-control.sh stop          # Stop both
./rtd-control.sh stop java     # Stop Java only
./rtd-control.sh stop react    # Stop React only

# Restart services
./rtd-control.sh restart       # Restart both
./rtd-control.sh restart java  # Restart Java only

# View real-time logs
./rtd-control.sh logs java     # Java pipeline logs
./rtd-control.sh logs react    # React app logs

# Clean up log files and temp directories
./rtd-control.sh cleanup

# Show help
./rtd-control.sh help
```

#### Manual Commands (Alternative)

**Local Development (Flink Mini Cluster)**
```bash
# Run with Maven (includes all dependencies)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDStaticDataPipeline"

# Run packaged JAR
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDStaticDataPipeline
```

**React Web App**
```bash
# Start React development server
cd rtd-maps-app && npm start
```

**Flink Cluster Deployment**
```bash
# Submit to Flink cluster
flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

## Architecture Overview

### High-Level Components
- **RTDGTFSPipeline**: Main job class that orchestrates the entire pipeline
- **GTFSRealtimeSource**: Custom source function that downloads GTFS-RT feeds every minute
- **Data Models**: VehiclePosition, TripUpdate, Alert - representing the three GTFS-RT feed types
- **Table API Sinks**: Print connectors for processed data output

### Data Flow
1. **Source**: HTTP requests to RTD GTFS-RT endpoints every minute
2. **Parse**: Protobuf deserialization of GTFS-RT feeds
3. **Transform**: Conversion to structured data models
4. **Sink**: Table API output (currently print connector, easily configurable for databases)

### Feed URLs
- Vehicle Positions: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb`
- Trip Updates: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb`
- Alerts: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb`

## Development Guidelines

- Always run tests after making code changes to ensure functionality remains intact
- The pipeline processes three separate GTFS-RT feed types in parallel streams
- Each feed is fetched every minute and parsed using the Google GTFS-RT protobuf library
- Watermarks are configured for 1-minute out-of-order tolerance
- Table API sinks can be easily reconfigured for different output formats (Kafka, databases, etc.)
- always generate junit test on new features

## State Management for Subagents

**CRITICAL**: After each successful task completion, update `context.md` to maintain state across subagents and conversations.

### Context Update Requirements
- Update `context.md` immediately after completing any task
- Include current git status, branch, and recent actions
- Document active files and components being worked on
- Note any important project state changes
- Provide context for future subagents or conversation resumption

### Context File Usage
- Read `context.md` at the start of complex tasks to understand current state
- Use context information to avoid duplicate work
- Maintain continuity across different agent invocations
- Include relevant file paths and next steps for handoff

**Example Update Pattern**:
```bash
# After completing a task, always update context
# Include: current branch, last action, files modified, next steps
```