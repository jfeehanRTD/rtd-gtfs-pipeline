# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TransitStream - RTD's modern transit information system. A Java application using Apache Flink to download and process real-time transit feeds from RTD Denver.

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

## Security Configuration

### TIS Proxy Authentication
The Bus SIRI and Rail Communication pipelines require authentication to connect to RTD's TIS Proxy. For security, credentials are managed via environment variables.

#### Initial Setup (Required)
1. Copy the example environment file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` with your actual credentials:
   ```bash
   # RTD TIS Proxy Configuration
   TIS_PROXY_USERNAME=your_actual_username
   TIS_PROXY_PASSWORD=your_actual_password
   TIS_PROXY_HOST=http://tisproxy.rtd-denver.com
   TIS_PROXY_SERVICE=siri
   TIS_PROXY_TTL=90000
   ```

3. **IMPORTANT**: The `.env` file is automatically excluded from Git commits for security.

#### Alternative: Export Environment Variables
```bash
export TIS_PROXY_USERNAME="your_username"
export TIS_PROXY_PASSWORD="your_password"
export TIS_PROXY_HOST="http://tisproxy.rtd-denver.com"
```

### Run Commands

#### Secure Startup (Recommended)
Use the secure startup scripts that load credentials from environment variables:
```bash
# Bus SIRI pipeline
./start-bus-siri-secure.sh

# Rail Communication pipeline  
./start-railcomm-secure.sh
```

#### RTD Control Script (General Pipeline Management)
The easiest way to manage the TransitStream pipeline, React web app, and HTTP receivers:

```bash
# Start all services (Java, React, Bus & Rail receivers)
./rtd-control.sh start all

# Start specific services
./rtd-control.sh start java       # Java pipeline only
./rtd-control.sh start react      # React app only
./rtd-control.sh start bus        # Bus SIRI HTTP receiver only
./rtd-control.sh start rail       # Rail Communication HTTP receiver only
./rtd-control.sh start receivers  # Both HTTP receivers only

# Check status of all services
./rtd-control.sh status

# Stop services
./rtd-control.sh stop all         # Stop everything
./rtd-control.sh stop receivers   # Stop both HTTP receivers
./rtd-control.sh stop bus         # Stop Bus SIRI receiver only
./rtd-control.sh stop rail        # Stop Rail Communication receiver only

# Restart services
./rtd-control.sh restart all      # Restart everything
./rtd-control.sh restart receivers # Restart both HTTP receivers
./rtd-control.sh restart bus      # Restart Bus SIRI receiver only

# View real-time logs
./rtd-control.sh logs java        # Java pipeline logs
./rtd-control.sh logs react       # React app logs
./rtd-control.sh logs bus         # Bus SIRI receiver logs
./rtd-control.sh logs rail        # Rail Communication receiver logs

# Clean up log files and temp directories
./rtd-control.sh cleanup

# Show help
./rtd-control.sh help
```

#### Manual Commands (Alternative)

**Bus SIRI Pipeline (Secure)**
```bash
# Preferred: Use environment variables (requires .env file or exported variables)
java -cp target/transitstream-1.0-SNAPSHOT.jar com.rtd.pipeline.BusCommHTTPReceiver

# Alternative: Command line with credentials (less secure - avoid in production)
java -cp target/transitstream-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.BusCommHTTPReceiver \
  http://tisproxy.rtd-denver.com siri 90000 username password

# Bus data processing pipeline
java -cp target/transitstream-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDBusCommSimplePipeline
```

**Rail Communication Pipeline (Secure)**
```bash
# Preferred: Use environment variables (requires .env file or exported variables)
java -cp target/transitstream-1.0-SNAPSHOT.jar com.rtd.pipeline.RailCommHTTPReceiver

# Alternative: Command line with credentials (less secure - avoid in production)
java -cp target/transitstream-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.RailCommHTTPReceiver \
  http://tisproxy.rtd-denver.com railcomm 90000 username password

# Rail communication data processing pipeline
java -cp target/transitstream-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDRailCommPipeline
```

**Local Development (Flink Mini Cluster)**
```bash
# Run with Maven (includes all dependencies)
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDStaticDataPipeline"

# Run packaged JAR
java -cp target/transitstream-1.0-SNAPSHOT.jar com.rtd.pipeline.RTDStaticDataPipeline
```

**React Web App**
```bash
# Start React development server
cd rtd-maps-app && npm start
```

**Flink Cluster Deployment**
```bash
# Submit to Flink cluster
flink run target/transitstream-1.0-SNAPSHOT.jar
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

## Oracle Database and VPN Requirements

**CRITICAL**: Oracle TIES database requires VPN connection, but Claude Code does NOT work when VPN is connected.

### Oracle Connection Details
- **Connection**: Requires VPN access (credentials stored in environment variables)
- **User**: Configured via environment variables
- **JDBC URL**: Configured via `~/ties-oracle-config.sh`

### VPN Workflow Constraint
- **User must connect to VPN** to access Oracle database
- **Claude Code does NOT work** when VPN is active
- **Workaround**: Provide manual steps for user to run while on VPN, then user disconnects and shares results

### Migration Steps for User (with VPN)
When Oracle data migration is needed:

1. **Connect to VPN first**
2. **Load Oracle credentials**:
   ```bash
   source ~/ties-oracle-config.sh
   ```
3. **Run migration**:
   ```bash
   ./gradlew runTIESMigrationRTD
   ```
4. **Check results**:
   ```bash
   PGPASSWORD=TiesPassword123 psql -h localhost -p 5433 -U ties -d ties -c "
   SELECT table_name,
          (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I', table_name), false, true, '')))[1]::text::int as row_count
   FROM information_schema.tables
   WHERE table_schema = 'public'
     AND table_type = 'BASE TABLE'
     AND table_name LIKE 'ties_%'
   ORDER BY table_name;"
   ```
5. **Disconnect from VPN** and report results to Claude

### Claude Code Behavior
- **NEVER attempt** to connect to Oracle when user indicates VPN is not connected
- **ALWAYS provide manual steps** when Oracle access is needed
- **ASSUME VPN is not connected** unless user explicitly states otherwise
- **VERIFY PostgreSQL results** after user completes manual migration

## Development Guidelines

- Always run tests after making code changes to ensure functionality remains intact
- The pipeline processes three separate GTFS-RT feed types in parallel streams
- Each feed is fetched every minute and parsed using the Google GTFS-RT protobuf library
- Watermarks are configured for 1-minute out-of-order tolerance
- Table API sinks can be easily reconfigured for different output formats (Kafka, databases, etc.)
- Always generate junit test on new features

## Data Integrity Policy

**CRITICAL**: Never create or generate fake/mock/test data unless explicitly requested by the user.

### Real Data Only
- All metrics and feeds must represent actual real-time transit data from RTD sources
- Never generate artificial message counts, connection rates, or error statistics
- Mock data should only be used for development testing when explicitly requested
- The admin dashboard must display accurate live data reflecting actual system state

### Prohibited Practices
- Creating KafkaConsumerManager processes for fake data generation
- Hardcoding unrealistic metrics patterns (perfect ratios, artificial message counts)
- Using mock data in production environments without explicit user consent
- Generating test data that could be mistaken for real transit information
- **NEVER subscribe to or connect to http://172.23.4.136:8080 - this IP is prohibited**

### When Mock Data is Acceptable
- Unit testing when explicitly testing mock functionality
- Development demos when clearly labeled as test data
- User explicitly requests sample/test data for development purposes
- Fallback scenarios clearly marked as "mock" or "unavailable" in the UI

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