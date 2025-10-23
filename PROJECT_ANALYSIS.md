# RTD GTFS Pipeline - Comprehensive Project Analysis

## Project Overview

**Project Name**: rtd-gtfs-pipeline-refArch1  
**Build System**: Gradle (Wrapper: 8.14) with Maven support  
**Java Version**: Java 24 (with --enable-preview support)  
**Apache Flink Version**: 2.1.0  
**Primary Use Case**: Real-time transit data pipeline processing (GTFS-RT generation and GTFS fare data extraction)

---

## 1. Project Structure & Directory Organization

### Top-Level Directory Tree
```
rtd-gtfs-pipeline-refArch1/
├── src/
│   ├── main/java/com/rtd/pipeline/
│   │   ├── api/                          # Spring Boot REST API endpoints
│   │   ├── client/                       # Data client implementations
│   │   ├── gtfs/                         # GTFS data processing
│   │   │   ├── model/                    # GTFS data models (15+ classes)
│   │   │   ├── GTFSDataProcessor.java    # Main GTFS file processor
│   │   │   ├── TIESDataExtractor.java    # Oracle extraction
│   │   │   └── RTDGTFSConverter.java     # Format conversion
│   │   ├── gtfsrt/                       # GTFS-RT (Real-time) processing
│   │   ├── mtram/                        # MTRAM database connectors
│   │   │   ├── MTRAMPostgresConnector.java  # PostgreSQL JDBC
│   │   │   └── MTRAMDataConnector.java      # API-based connector
│   │   ├── source/                       # Flink data sources (7 classes)
│   │   ├── occupancy/                    # Vehicle occupancy analysis
│   │   ├── serialization/                # Protobuf serialization
│   │   ├── transform/                    # Data transformation functions
│   │   ├── util/                         # Utility classes
│   │   ├── validation/                   # Data validation & verification
│   │   ├── tools/                        # CLI tools
│   │   ├── RTDStaticDataPipeline.java    # Main entry point (Direct fetching)
│   │   ├── WorkingGTFSRTPipeline.java    # Main Flink+Kafka pipeline
│   │   ├── RTDBusCommPipeline.java       # Bus SIRI processing
│   │   └── [20+ other pipeline classes]
│   └── test/                             # Comprehensive test suite
├── build.gradle                          # Gradle build configuration
├── config/
│   ├── gtfs-rt.properties               # GTFS-RT pipeline configuration
│   └── query-client.properties          # Query client settings
├── k8s/                                  # Kubernetes deployment files
├── fare_export/                          # Fare data export artifacts
└── [documentation & config files]
```

---

## 2. Build System & Dependencies

### Gradle Configuration (build.gradle)

**Key Properties:**
```gradle
flinkVersion = '2.1.0'
kafkaVersion = '4.0.0'
springBootVersion = '3.4.1'
slf4jVersion = '2.0.16'
jacksonVersion = '2.18.1'
log4jVersion = '2.24.3'
junitVersion = '5.11.0'
```

**Core Dependencies:**
- **Apache Flink**: Core, Streaming, Table API, Kafka Connector
- **Kafka**: Core client & broker libraries (4.0.0 version)
- **Protobuf**: GTFS-RT real-time bindings (0.0.4)
- **Spring Boot**: Web framework for API server
- **Jackson**: JSON/XML processing & data binding
- **Logging**: SLF4J + Log4j2 (2.24.3)
- **Testing**: JUnit 5, Mockito, TestContainers, OkHttp MockServer

**Build Tasks:**
```bash
./gradlew build              # Full build with tests
./gradlew fatJar             # Creates single JAR with all dependencies
./gradlew test               # Run test suite
./gradlew runRTDPipeline     # Run main RTD pipeline
./gradlew runGTFSRTPipeline  # Run GTFS-RT pipeline
./gradlew runStaticPipeline  # Run static data pipeline
```

---

## 3. Apache Flink Architecture & Usage

### Flink Version & Configuration
- **Current Version**: Apache Flink 2.1.0 (latest stable)
- **Execution Model**: Streaming + Batch (DataStream + Table API)
- **State Management**: Checkpointing enabled (60-second intervals)
- **Parallelism**: Default 1 (single task manager for development)

### Main Flink Pipeline Classes

#### 1. **RTDStaticDataPipeline.java** (Direct Fetching - No Flink Execution)
- Entry point: `public static void main(String[] args)`
- **Approach**: Bypasses Flink execution engine for serialization compatibility
- **Function**: 
  - Fetches RTD GTFS-RT vehicle positions via HTTP every 60 seconds
  - Fetches GTFS schedule data every 3600 seconds
  - Hosts embedded HTTP server on port 8080 serving live data
  - Uses scheduled executors instead of Flink's execution model
- **Output**: HTTP endpoints for React dashboard
- **Key Classes Used**:
  - `RTDRowSource` - Direct data fetching without Flink source functions
  - `GTFSScheduleSource` - GTFS schedule data fetching
  - HTTP Server with endpoints: `/api/vehicles`, `/api/schedule`, `/api/stats`

#### 2. **WorkingGTFSRTPipeline.java** (Kafka-based GTFS-RT Generation)
- **Approach**: Uses native Kafka consumers instead of Flink's KafkaSource
- **Function**: Processes three data sources:
  - **Bus SIRI**: SIRI XML real-time bus data (from `rtd.bus.siri` topic)
  - **Light Rail LRGPS**: GPS data from light rail vehicles (PRIMARY - `rtd.lrgps` topic)
  - **Light Rail RailComm**: SCADA sensor data (FAILOVER - `rtd.rail.comm` topic)
- **Output**: Generates GTFS-RT feeds (VehiclePosition, TripUpdate, Alerts) as protobuf files
- **Failover Logic**: Switches to RailComm if LRGPS data missing for >60 seconds
- **Transformation**: Converts SIRI/RailComm to GTFS-RT using transformers

#### 3. **RTDBusCommPipeline.java** (Flink-based SIRI Processing)
```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
    .setBootstrapServers("localhost:9092")
    .setTopics("rtd.bus.siri")
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();

env.fromSource(kafkaSource, WatermarkStrategy.forBoundedOutOfOrderness(...))
    .map(new SIRIParsingFunction())
    .sinkTo(FileSink.forRowFormat(...))
    .print();
```
- **Features**: Checkpointing, data retention, console monitoring
- **Data Flow**: Kafka → Parse → File Sink + Console Output

#### 4. **RTDRailCommPipeline.java** (Light Rail Processing)
- Similar to BusComm but processes rail communication data
- Handles SCADA sensor inputs from rail infrastructure

### Flink Data Sources (Implemented)

**Location**: `/src/main/java/com/rtd/pipeline/source/`

1. **GTFSProtobufSource.java**
   - Implements: `SourceFunction<VehiclePosition>`
   - Downloads GTFS-RT protobuf feeds from HTTP endpoints
   - Periodic fetching with configurable intervals
   - Native protobuf message handling

2. **GTFSRealtimeSource.java**
   - Generic GTFS-RT feed source
   - Handles FeedMessage parsing

3. **GTFSScheduleSource.java**
   - Downloads GTFS schedule ZIP files
   - Extracts and parses CSV files

4. **RTDRowSource.java** (Modern Flink Source API)
   - Implements new Flink 2.x Source API
   - Returns `Row` objects (Flink's generic record type)
   - HTTP-based vehicle position fetching

5. **RTDRowSourceReader.java** & **RTDRowSplitEnumerator.java**
   - Parallel split handling for horizontal scaling

### Kafka Integration

**Kafka Configuration:**
```properties
kafka.bootstrap.servers=localhost:9092
kafka.consumer.group.id=gtfs-rt-generator

# Topics
kafka.topic.bus.siri=rtd.bus.siri
kafka.topic.rail.comm=rtd.rail.comm
kafka.topic.lrgps=rtd.lrgps

# Feed Generation
gtfs.rt.feed.generation.window.seconds=30
gtfs.rt.output.directory=data/gtfs-rt
```

**Consumer Groups:**
- `gtfs-rt-generator` - Main GTFS-RT generator
- `rtd-bus-comm-consumer` - Bus communication processor
- `rtd-query-client` - Query client for data analysis

### Sink Implementations

**FileSink Configuration** (from RTDBusCommPipeline):
```java
FileSink.forRowFormat(new Path("./data/bus-siri/"), new SimpleStringEncoder<String>("UTF-8"))
    .withRollingPolicy(DefaultRollingPolicy.builder()
        .withRolloverInterval(Duration.ofDays(1))
        .withInactivityInterval(Duration.ofHours(1))
        .withMaxPartSize(MemorySize.ofMebiBytes(512))
        .build())
```

**Output Sinks Available:**
- File-based sinks (daily rolling, configurable retention)
- Console sinks (for real-time monitoring)
- Print sinks (debugging)
- Custom data sinks (extensible)

---

## 4. Database Connectivity Patterns

### PostgreSQL / MTRAM Database

**Primary Connector**: `MTRAMPostgresConnector.java`
```java
// Connection establishment
Class.forName("org.postgresql.Driver");
Connection connection = DriverManager.getConnection(
    "jdbc:postgresql://localhost:5432/mtram", 
    username, 
    password
);

// Prepared statements for GTFS generation
String sql = """
    SELECT unique_code, description FROM expmtram_lines
    WHERE runboard_id = ? AND (deleted IS NULL OR deleted = 0)
    """;
PreparedStatement stmt = connection.prepareStatement(sql);
stmt.setLong(1, Long.parseLong(runboardId));
```

**Database Schema** (MTRAM - Oracle migrated to PostgreSQL):
```
Core Tables:
- expmtram_lines           # Route definitions
- expmtram_trips           # Trip schedules
- expmtram_patternnodes    # Pattern nodes (stops)
- expmtram_shape_stoppoint # Stop locations (WKT geometry)
- expmtram_leavetimes      # Stop arrival/departure times
- expmtram_daytype         # Service day types
- expmtram_daytype_calendar # Calendar associations
```

**GTFS File Generation from PostgreSQL:**
```java
// Generates: agency.txt, routes.txt, stops.txt, trips.txt, 
//           stop_times.txt, calendar.txt, fare_products.txt
connector.generateGTFS(outputDir);
connector.createGTFSZip(outputPath);
```

### Oracle TIES Database (Legacy)

**Secondary Connector**: `TIESDataExtractor.java`
- One-time extraction tool from Oracle TIES schema
- Uses PL/SQL functions via `TIES_GTFS_VW_PKG`
- Connection: `jdbc:oracle:thin:@host:1521:sid`
- Usage: Migration path from Oracle to file-based GTFS

### MTRAM API Connector (HTTP-based)

**API Connector**: `MTRAMDataConnector.java`
```java
// HTTP-based API approach
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build();

// Fetch runboards from API
List<RunboardOverview> runboards = connector.getActiveRunboards();
// Endpoint: {mtramApiBaseUrl}/api/runboards/overview

// Convert to GTFS
List<GTFSCalendar> calendars = connector.convertRunboardsToCalendar(runboards);
```

**Advantages**: No JDBC driver needed, firewall-friendly, API-versioning support

---

## 5. GTFS Data Models

### GTFS v2 Standard Models

**Location**: `/src/main/java/com/rtd/pipeline/gtfs/model/`

**Core Models (Static Schedule):**
1. `GTFSAgency.java` - Transit agencies
2. `GTFSRoute.java` - Route definitions
3. `GTFSStop.java` - Stop locations & metadata
4. `GTFSTrip.java` - Individual trip instances
5. `GTFSStopTime.java` - Stop arrival/departure times
6. `GTFSCalendar.java` - Service calendars (weekday/weekend patterns)
7. `GTFSCalendarDate.java` - Service exceptions (holidays)
8. `GTFSShape.java` - Shape (geometry) definitions

**GTFS v2 Fare Models:**
1. `GTFSFareProduct.java` - Fare products (passes, single rides)
   - Fields: `fare_product_id`, `fare_product_name`, `amount`, `currency`
   - CSV Header: `fare_product_id,fare_product_name,fare_media_id,amount,currency`

2. `GTFSFareMedia.java` - Payment methods
   - Fields: `fare_media_id`, `fare_media_name`, `fare_media_type`
   - Types: Card, App, Cash, etc.

3. `GTFSFareLegRule.java` - Fare pricing rules
   - Maps: Origin zone → Destination zone → Fare product

4. `GTFSFareTransferRule.java` - Transfer discounts
   - Configurable transfer allowances between routes

5. `GTFSFareAttribute.java` - Legacy fare attributes (v1)
   - Backward compatibility support

6. `GTFSFareRule.java` - Legacy route-based fares (v1)

### GTFS-RT Models

**Real-time Feed Messages:**
- `VehiclePosition` - Current vehicle GPS coordinates
- `TripUpdate` - Predicted arrival/departure times
- `Alert` - Service disruption notices

**Implementation**: Uses Google's GTFS-RT protobuf library
```java
import com.google.transit.realtime.GtfsRealtime.*;
```

---

## 6. Existing GTFS Fare-Related Code

### Current Fare Implementation

**Static Fare Data Files Generated:**
- `fare_products.txt` - Hardcoded RTD fares:
  - `local_single`: $2.75
  - `airport_single`: $10.00
  - `day_pass`: $5.50
  - `monthly_pass`: $88.00

**From MTRAMPostgresConnector.java (lines 430-472):**
```java
private void generateFareProducts(Path outputPath) {
    writer.println("fare_product_id,fare_product_name,amount,currency");
    writer.println("local_single,Local Single Ride,2.75,USD");
    writer.println("airport_single,Airport Single Ride,10.00,USD");
    writer.println("free_ride,Free Ride,0.00,USD");
    writer.println("day_pass,Day Pass,5.50,USD");
    writer.println("monthly_pass,Monthly Pass,88.00,USD");
}
```

### Fare Validation & Issues

**Validation Tool**: `RTDFareValidator.java`
- Identifies unauthorized airport fare charging
- Issues found: 305 stops incorrectly marked for $10 airport fare
- Only 7 stops authorized for airport fare (Denver Airport Station gates)

**Data Export Tools**:
- `FareDataExporter.java` - Exports fare violations to CSV
- Output files in `fare_export/` directory
  - `unauthorized_airport_stops.csv` - 305 stops with fare issues
  - `affected_routes.csv` - Routes impacted

---

## 7. Configuration Files

### GTFS-RT Pipeline Config (`config/gtfs-rt.properties`)
```properties
# Kafka
kafka.bootstrap.servers=localhost:9092
kafka.topic.bus.siri=rtd.bus.siri
kafka.consumer.group.id=gtfs-rt-generator

# Output & Feed Config
gtfs.rt.output.directory=data/gtfs-rt
gtfs.rt.feed.generation.window.seconds=30
gtfs.rt.publisher.port=8084

# Validation Rules (Colorado bounds)
validation.coordinate.min.latitude=39.0
validation.coordinate.max.latitude=41.0
validation.coordinate.min.longitude=-106.0
validation.coordinate.max.longitude=-104.0

# Endpoints
endpoints.vehicle.positions=/files/gtfs-rt/VehiclePosition.pb
endpoints.trip.updates=/files/gtfs-rt/TripUpdate.pb
endpoints.alerts=/files/gtfs-rt/Alerts.pb
```

### Query Client Config (`config/query-client.properties`)
```properties
# Kafka
kafka.bootstrap.servers=localhost:9092
kafka.group.id=rtd-query-client

# Topics
topic.vehicle.positions=rtd.vehicle.positions
topic.trip.updates=rtd.trip.updates
topic.alerts=rtd.alerts

# Query Options
query.default.limit=10
query.timeout.seconds=30
```

### Environment Configuration (`.env.example`)
```bash
# TIS Proxy (Transit Information System)
TIS_PROXY_USERNAME=your_username
TIS_PROXY_PASSWORD=your_password
TIS_PROXY_HOST=http://tisproxy.rtd-denver.com
TIS_PROXY_SERVICE=siri
TIS_PROXY_TTL=90000

# Service Settings
RAILCOMM_SERVICE=railcomm
LRGPS_SERVICE=lrgps
LRGPS_PORT=881
BUS_SIRI_PORT=880
HTTP_PORT=8082
```

---

## 8. Package Structure & Naming Conventions

### Package Organization
```
com.rtd.pipeline
├── api.*                          # REST API endpoints
├── client.*                       # Data consumers
├── gtfs.*
│   ├── GTFSDataProcessor
│   ├── GTFSReplacementGenerator
│   ├── RTDGTFSConverter
│   ├── TIESDataExtractor
│   └── model.GTFS*               # Data classes
├── gtfsrt.*                       # Real-time feed generation
├── mtram.*
│   ├── MTRAMDataConnector        # API-based
│   └── MTRAMPostgresConnector    # JDBC-based
├── model.*                        # Domain models
├── occupancy.*                    # Vehicle occupancy analysis
├── serialization.*                # Protobuf serialization
├── source.*                       # Flink sources
├── transform.*                    # Data transformers (Siri→GTFS, etc.)
├── tools.*                        # CLI utilities
├── util.*                         # Helper utilities
└── validation.*                   # Data validation
```

### Naming Conventions

**Class Naming:**
- Flink sources: `*Source.java` (e.g., `GTFSProtobufSource`, `RTDRowSource`)
- Transformers: `*ToGtfsTransformer.java` (e.g., `SiriToGtfsTransformer`)
- Processors: `*Processor.java` (e.g., `GTFSDataProcessor`)
- Connectors: `*Connector.java` (e.g., `MTRAMPostgresConnector`)
- Models: `GTFS*.java` (e.g., `GTFSRoute`, `GTFSFareProduct`)
- Pipelines: `*Pipeline.java` (e.g., `RTDBusCommPipeline`)
- Validators: `*Validator.java` (e.g., `RTDFareValidator`)

**Method Naming:**
- Source factories: `public static create(...)`
- CSV conversion: `toCsvRow()`, `fromCsvRow()`
- Generators: `generate*(...)`
- Extractors: `extract*(...)`
- Converters: `convert*(...)`

---

## 9. Key Data Flow Patterns

### Pattern 1: Direct HTTP Fetching (RTDStaticDataPipeline)
```
HTTP endpoint → Fetch → Parse → In-memory → HTTP Server → React Dashboard
(No Flink execution)
```

### Pattern 2: Kafka-based Processing (WorkingGTFSRTPipeline)
```
Kafka (3 topics)
  ├─ Bus SIRI → SiriToGtfsTransformer → VehiclePosition Buffer
  ├─ LRGPS → LrgpsToGtfsTransformer → VehiclePosition Buffer (PRIMARY)
  └─ RailComm → RailCommToGtfsTransformer → VehiclePosition Buffer (FAILOVER)
       ↓
Merge with failover logic
       ↓
Feed generation (every 30 seconds)
       ↓
Output: VehiclePosition.pb, TripUpdate.pb, Alerts.pb
```

### Pattern 3: Database-driven GTFS Generation (MTRAMPostgresConnector)
```
PostgreSQL Connection
     ↓
Query 8+ tables (lines, trips, shapes, stops, etc.)
     ↓
Generate CSV files (agency.txt, routes.txt, stops.txt, etc.)
     ↓
Validate & ZIP
     ↓
Output: gtfs_mtram_{runboard_id}_{date}.zip
```

### Pattern 4: Flink Streaming Pipeline (RTDBusCommPipeline)
```
Kafka Topic (rtd.bus.siri)
     ↓
KafkaSource<String>
     ↓
map(SIRIParsingFunction)
     ↓
filter(NonNullRowFilter)
     ↓
├─ FileSink (daily rolling)
├─ print() for console monitoring
└─ Additional sinks
```

---

## 10. Java/JDBC Patterns Used

### Database Connection Pattern
```java
// Load driver
Class.forName("org.postgresql.Driver");

// Get connection
Connection conn = DriverManager.getConnection(
    jdbcUrl, 
    username, 
    password
);

// Prepared statement with parameterization
try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setLong(1, runboardId);
    try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
            // Process results
        }
    }
}
```

### Flink Type Information Pattern
```java
// For custom types
PojoTypeInfo<MyClass> typeInfo = TypeInformation.of(MyClass.class);

// For Protobuf
ProtobufTypeInformation<VehiclePosition> typeInfo = 
    ProtobufTypeInformation.of(VehiclePosition.class);
```

### HTTP Client Pattern (Java 11+)
```java
HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(endpoint))
    .header("Authorization", "Bearer " + token)
    .GET()
    .build();

HttpResponse<String> response = httpClient.send(request, 
    HttpResponse.BodyHandlers.ofString());
```

---

## 11. Testing & Validation Infrastructure

**Test Suite Location**: `/src/test/java/com/rtd/pipeline/`

**Key Test Classes:**
- `GTFSDataProcessorTest.java` - GTFS file processing
- `GTFSZipProcessingTest.java` - ZIP validation
- `GTFSValidationTest.java` - Data validation
- `GTFSRTFeedGeneratorTest.java` - Real-time feed generation
- `SiriToGtfsTransformerTest.java` - SIRI transformation
- `BusCommPipelineTest.java` - Pipeline integration
- `OccupancyAnalyzerTest.java` - Occupancy calculations

**Validation Tools:**
- Unit tests (JUnit 5)
- Integration tests (TestContainers for Kafka)
- Mock HTTP servers (OkHttp MockWebServer)
- Data validation (GTFS specification compliance)

---

## 12. Real-Time Data Sources

### Current RTD Sources

**Bus Real-time (SIRI):**
- Endpoint: RTD TIS Proxy (tisproxy.rtd-denver.com)
- Feed Type: SIRI XML
- Data: Vehicle positions, service alerts
- Update Frequency: Real-time streaming

**Light Rail GPS (LRGPS):**
- Endpoint: Cellular network routers on each light rail car
- Source: Direct GPS from vehicles
- Priority: PRIMARY for light rail (more accurate)

**Light Rail SCADA (RailComm):**
- Endpoint: Track infrastructure sensors
- Source: SCADA system data
- Priority: FAILOVER when LRGPS unavailable

**Static GTFS-RT:**
- Vehicle Positions: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb`
- Trip Updates: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb`
- Alerts: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb`

---

## 13. Recommended Architecture for New Fare Data Flink Job

### Proposed Job Structure

**Class**: `GTFSFareDataExtractionPipeline.java`

```java
public class GTFSFareDataExtractionPipeline {
    
    // Source: PostgreSQL MTRAM database
    // - Query fare-related tables
    // - Stream as Flink records
    
    // Transformations:
    // - Normalize fare structures
    // - Validate against GTFS spec
    // - Enrich with route/stop metadata
    
    // Sinks:
    // - File sinks: CSV files (fare_products.txt, etc.)
    // - Kafka sinks: For downstream consumers
    // - Database sinks: Back to TIES for validation
    
    // Monitoring:
    // - Fare validation metrics
    // - Data quality checks
    // - Completeness verification
}
```

### Integration Points
1. **Source**: `MTRAMPostgresConnector` for schema queries
2. **Models**: Existing `GTFSFare*.java` classes
3. **Validation**: `RTDFareValidator.java` patterns
4. **Output**: Same file structure as current GTFS export

---

## 14. Known Issues & Limitations

### Serialization Constraints
- Flink 2.0+ has serialization issues with custom classes
- **Workaround**: Use Protobuf messages (native support) or Flink Row objects
- Some pipelines bypass Flink execution engine entirely

### Database Connectivity
- Legacy Oracle TIES system requires ora2pg migration
- PostgreSQL MTRAM now primary database
- API-based connector available as alternative

### Fare Data Issues (from validation reports)
- 305 stops incorrectly marked for $10 airport fare
- Only 7 stops authorized for Denver Airport Station fares
- Google Maps shows $12.75 instead of $2.75 for many stops

---

## 15. Build & Run Quick Reference

### Build Commands
```bash
# Clean build
./gradlew clean build

# Fat JAR with all dependencies
./gradlew fatJar

# Run main pipeline
./gradlew runRTDPipeline

# Run GTFS-RT pipeline
./gradlew runGTFSRTPipeline

# Run tests
./gradlew test
```

### Database Connection Examples
```bash
# MTRAM PostgreSQL
MTRAMPostgresConnector \
  jdbc:postgresql://localhost:5432/mtram \
  postgres \
  password \
  35628 \
  /tmp/gtfs

# Oracle TIES (legacy)
TIESDataExtractor \
  jdbc:oracle:thin:@host:1521:sid \
  username \
  password \
  RTD \
  /tmp/gtfs
```

### Environment Setup
```bash
# Copy example config
cp .env.example .env

# Edit with real credentials
vim .env

# Export for pipeline
source .env

# Run pipeline
./gradlew runRTDPipeline
```

---

## Conclusion

This is a comprehensive, production-grade transit data pipeline with:
- **Multiple data sources** (HTTP, Kafka, PostgreSQL, Oracle)
- **Real-time processing** (GTFS-RT generation)
- **Static data export** (GTFS files)
- **Flexible connectors** (Flink, direct, API-based)
- **Fare data support** (GTFS v2 models)
- **Extensive validation** (data quality checks)

The architecture supports both batch GTFS generation and real-time GTFS-RT processing, with opportunities to extend fare data extraction using existing patterns.

