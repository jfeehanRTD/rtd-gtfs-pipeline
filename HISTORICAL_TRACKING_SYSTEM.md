# RTD Historical Tracking & Service Disruption Detection System

A comprehensive Flink-based streaming analytics pipeline for RTD transit data that provides historical storage, real-time service disruption detection, and proactive monitoring across the entire transit system.

## Overview

This system extends the basic RTD GTFS-RT pipeline to provide:

- **Historical Data Storage**: Complete audit trail of all vehicle movements and service events
- **Service Disruption Detection**: Real-time identification of missing vehicles and service gaps  
- **Proactive Monitoring**: Automated alerting for service quality issues
- **Multi-Sink Architecture**: Flexible data output to databases, search engines, and analytics platforms
- **Scalable Processing**: Handles high-volume transit data with fault tolerance

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│   GTFS-RT       │    │   Historical     │    │   Service           │
│   Sources       │───▶│   Enrichment     │───▶│   Disruption        │
│                 │    │                  │    │   Detection         │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
                                                          │
                       ┌──────────────────────────────────┴────────────────────────────────┐
                       │                                                                   │
                       ▼                                                                   ▼
┌─────────────────────────────────────────┐                    ┌─────────────────────────────┐
│            Data Sinks                   │                    │         Alerting            │
│  ┌─────────────┐  ┌─────────────────┐   │                    │  ┌─────────────────────────┐│
│  │ PostgreSQL  │  │ Elasticsearch   │   │                    │  │ Missing Vehicle Alerts  ││
│  │ (Raw Data)  │  │ (Search/Viz)    │   │                    │  │ Service Disruption      ││
│  └─────────────┘  └─────────────────┘   │                    │  │ System Status           ││
│  ┌─────────────┐  ┌─────────────────┐   │                    │  └─────────────────────────┘│
│  │ Kafka       │  │ Parquet/S3      │   │                    └─────────────────────────────┘
│  │ (Streaming) │  │ (Data Lake)     │   │
│  └─────────────┘  └─────────────────┘   │
└─────────────────────────────────────────┘
```

## Core Components

### 1. HistoricalVehicleRecord

**Purpose**: Unified data model for historical transit data storage

```java
HistoricalVehicleRecord record = new HistoricalVehicleRecord(vehiclePosition);
// Automatically enriched with:
// - Vehicle type classification (Bus/Light Rail/BRT/Shuttle)  
// - Service date and hour (Mountain Time)
// - Ingestion and processing timestamps
// - Initial disruption level assessment
```

**Key Features**:
- **Multi-Source Support**: Converts VehiclePosition, TripUpdate, and Alert data
- **Automatic Classification**: Determines vehicle type from route ID patterns
- **Time Zone Handling**: Service times in Mountain Time for RTD operations
- **Missing Vehicle Detection**: Special records for vehicles not seen in feeds
- **Disruption Scoring**: 5-level severity scale (Normal → Critical)

### 2. ServiceDisruptionDetector  

**Purpose**: Real-time analysis of service levels and missing vehicle detection

```java
// Keyed by route ID, maintains state for each route
DataStream<HistoricalVehicleRecord> enriched = historicalData
    .keyBy(record -> record.getRouteId())
    .process(new ServiceDisruptionDetector());
```

**Detection Logic**:
- **Route Expectations**: Dynamic service level expectations based on time/day
- **Missing Vehicles**: Detects vehicles not seen for 15+ minutes
- **Service Level Windows**: 30-minute sliding windows for trend analysis
- **Disruption Classification**: Automatic severity assessment

**State Management**:
- **Vehicle Last Seen**: Tracks when each vehicle was last observed
- **Route Expectations**: Expected service levels by time of day
- **Service Windows**: Recent service level metrics

### 3. HistoricalDataSinkManager

**Purpose**: Multi-destination data output using Flink Table API

**Supported Sinks**:
- **PostgreSQL**: Raw historical data for operational queries
- **Elasticsearch**: Search and visualization capabilities  
- **Kafka**: Real-time streaming for downstream consumers
- **Parquet/S3**: Cost-effective long-term storage with partitioning

**Table API Benefits**:
- **SQL Interface**: Easy data transformations and aggregations
- **Schema Evolution**: Handles data model changes gracefully
- **Batch Processing**: Efficient bulk operations
- **Connector Ecosystem**: Wide range of destination options

## Data Flow

### 1. Data Ingestion
```java
// Multiple GTFS-RT sources
DataStream<VehiclePosition> vehicles = env.addSource(vehiclePositionSource);
DataStream<TripUpdate> trips = env.addSource(tripUpdateSource);  
DataStream<Alert> alerts = env.addSource(alertSource);
```

### 2. Historical Enrichment
```java
// Convert to unified historical records
DataStream<HistoricalVehicleRecord> historical = 
    vehicles.map(HistoricalVehicleRecord::new)
    .union(trips.map(HistoricalVehicleRecord::new))
    .union(alerts.map(this::convertAlertToRecord));
```

### 3. Service Analysis
```java
// Apply disruption detection with keyed state
SingleOutputStreamOperator<HistoricalVehicleRecord> analyzed = 
    historical.keyBy(HistoricalVehicleRecord::getRouteId)
             .process(new ServiceDisruptionDetector());
```

### 4. Multi-Sink Output
```java
// Configure multiple destinations
sinkManager.createHistoricalVehicleSinks(analyzed);
// - PostgreSQL for operations
// - Elasticsearch for search  
// - Kafka for real-time consumers
// - Parquet for analytics
```

## Service Disruption Detection

### Disruption Levels

| Level | Severity | Service % | Description | Example |
|-------|----------|-----------|-------------|---------|
| **NORMAL** | 0 | 95-100% | Full service | Normal operations |
| **MINOR** | 1 | 85-95% | Slight delays | Individual vehicle delays |
| **MODERATE** | 2 | 70-85% | Noticeable impact | Route partially affected |
| **MAJOR** | 3 | 50-70% | Significant disruption | Multiple vehicles missing |
| **CRITICAL** | 4 | <50% | Service failure | Route shutdown |

### Detection Scenarios

#### Missing Vehicle Detection
```java
// Vehicle not seen for 15+ minutes triggers missing vehicle alert
HistoricalVehicleRecord missingAlert = HistoricalVehicleRecord
    .createMissingVehicleRecord("BUS_123", "Route_15", 
        missingDurationMinutes, DisruptionLevel.MODERATE);
```

#### Service Level Analysis
- **Expected vs Actual**: Compares current vehicle count to time-based expectations
- **Route-Specific**: Different thresholds for high/low frequency routes
- **Time-Aware**: Peak vs off-peak service level expectations

#### Real-Time Alerting
```sql
-- System-wide disruption detection (Table API SQL)
SELECT 
  COUNT(CASE WHEN serviceDisruptionLevel = 'CRITICAL' THEN 1 END) as critical_issues,
  COUNT(DISTINCT routeId) as affected_routes,
  CASE 
    WHEN critical_issues > 0 THEN 'SYSTEM_CRITICAL'
    WHEN affected_routes > 5 THEN 'SYSTEM_MAJOR'  
    ELSE 'SYSTEM_NORMAL'
  END as system_status
FROM vehicle_positions_historical
WHERE processingTime > NOW() - INTERVAL '5' MINUTES;
```

## Table API Sink Configurations

### PostgreSQL - Operational Database
```java
TableDescriptor.forConnector("jdbc")
    .option("url", "jdbc:postgresql://localhost:5432/rtd_gtfs")
    .option("table-name", "vehicle_positions_historical")
    .option("sink.buffer-flush.max-rows", "1000")
    .option("sink.buffer-flush.interval", "10s")
```

### Elasticsearch - Search & Analytics  
```java
TableDescriptor.forConnector("elasticsearch-7")
    .option("hosts", "http://localhost:9200")
    .option("index", "rtd-vehicle-positions-{service_date}")
    .option("sink.bulk-flush.max-actions", "1000")
    .option("sink.bulk-flush.interval", "30s")
```

### Kafka - Real-time Streaming
```java
TableDescriptor.forConnector("kafka")
    .option("topic", "rtd-vehicle-positions-processed") 
    .option("format", "json")
    .option("sink.partitioner", "fixed")
```

### Parquet - Data Lake Storage
```java
TableDescriptor.forConnector("filesystem")
    .option("path", "s3://rtd-data-lake/vehicle-positions/year=$year/month=$month/day=$day/")
    .option("format", "parquet")
    .option("sink.partition-commit.policy.kind", "success-file")
```

## Real-Time Aggregations

### Route-Level Metrics (5-minute windows)
```sql
SELECT 
  routeId,
  TUMBLE_START(processingTime, INTERVAL '5' MINUTES) as window_start,
  COUNT(DISTINCT vehicleId) as active_vehicles,
  COUNT(CASE WHEN isMissing = true THEN 1 END) as missing_vehicles,  
  AVG(CAST(delaySeconds as DOUBLE)) as avg_delay_seconds,
  MAX(serviceDisruptionLevel) as max_disruption_level
FROM vehicle_positions_historical
GROUP BY routeId, TUMBLE(processingTime, INTERVAL '5' MINUTES);
```

### System Status Dashboard
```sql
SELECT
  TUMBLE_START(processingTime, INTERVAL '1' MINUTES) as timestamp,
  COUNT(DISTINCT routeId) as total_routes,
  COUNT(DISTINCT vehicleId) as total_vehicles,
  COUNT(CASE WHEN serviceDisruptionLevel IN ('CRITICAL', 'MAJOR') THEN 1 END) as high_severity_issues,
  COUNT(CASE WHEN isMissing = true THEN 1 END) as missing_vehicles_total
FROM vehicle_positions_historical  
GROUP BY TUMBLE(processingTime, INTERVAL '1' MINUTES);
```

## Configuration & Deployment

### Environment Configurations

#### Development
```java
ParameterTool config = ParameterTool.fromMap(Map.of(
    "parallelism", "2",
    "sinks", "print,postgresql",
    "checkpoint.interval", "60000",
    "state.backend", "filesystem"
));
```

#### Production  
```java
ParameterTool config = ParameterTool.fromMap(Map.of(
    "parallelism", "8", 
    "sinks", "postgresql,elasticsearch,kafka,parquet",
    "checkpoint.interval", "30000",
    "state.backend", "rocksdb"
));
```

### Running the Pipeline

#### Local Development
```bash
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.HistoricalRTDGTFSPipeline" \
  -Dexec.args="--sinks print --parallelism 1"
```

#### Production Cluster
```bash  
flink run -p 8 target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  --sinks postgresql,elasticsearch,kafka,parquet \
  --checkpoint.interval 30000 \
  --state.backend rocksdb
```

## Sample Output Data

### Historical Vehicle Record
```json
{
  "recordId": "RTD_BUS_001_1691234567890",
  "vehicleId": "RTD_BUS_001", 
  "routeId": "15",
  "vehicleType": "BUS",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speedKmh": 25.0,
  "currentStatus": "IN_TRANSIT_TO",
  "delaySeconds": 120,
  "serviceDate": "2025-08-08",
  "serviceHour": 8,
  "isMissing": false,
  "serviceDisruptionLevel": "MINOR",
  "originalTimestampMs": 1691234567890,
  "ingestionTimestampMs": 1691234568000,
  "processingTimestampMs": 1691234568100
}
```

### Missing Vehicle Alert
```json
{
  "recordId": "RTD_BUS_002_1691234568000",
  "vehicleId": "RTD_BUS_002",
  "routeId": "20", 
  "vehicleType": "BUS",
  "isMissing": true,
  "missingDurationMinutes": 25,
  "serviceDisruptionLevel": "MODERATE",
  "serviceDate": "2025-08-08",
  "serviceHour": 8
}
```

### Service Disruption Summary
```
RTD Service Status - 2025-08-08 08:15:00
=========================================
Overall System Status: MODERATE

Route Disruptions:
- Route 15: MINOR (1 vehicle delayed 2+ minutes)
- Route 20: MODERATE (1 vehicle missing 25 minutes) 
- Route C: NORMAL (all vehicles on schedule)
- Route A: MAJOR (2 vehicles missing 45+ minutes)

Missing Vehicles: 3
High Severity Routes: 1  
Total Active Vehicles: 127/135 (94.1%)
```

## Benefits

### Operational Benefits
- **Proactive Monitoring**: Detect issues before passenger complaints
- **Historical Context**: Understand service patterns and improvement trends  
- **Real-Time Dashboards**: Live operational awareness
- **Automated Alerting**: Immediate notification of service disruptions

### Analytical Benefits
- **Performance Metrics**: Route-level and system-wide KPIs
- **Trend Analysis**: Historical service reliability data
- **Capacity Planning**: Usage patterns and service optimization
- **Data-Driven Decisions**: Evidence-based service improvements

### Technical Benefits  
- **Scalable Architecture**: Handles growing data volumes
- **Fault Tolerance**: Checkpointing and recovery capabilities
- **Flexible Outputs**: Multiple sink destinations for different use cases
- **Real-Time Processing**: Sub-minute latency for critical alerts

This comprehensive system transforms raw GTFS-RT feeds into actionable insights, enabling RTD to provide better service through proactive monitoring and data-driven operational decisions.