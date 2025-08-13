# RTD GTFS-RT Pipeline Data Sinks

## Overview
The RTD Historical Tracking & Service Disruption Detection System uses multiple data sinks to store, distribute, and serve processed transit data from GTFS-RT feeds. The architecture supports both batch and real-time streaming use cases.

## Primary Data Sink: Apache Kafka

### Configuration
- **Bootstrap Server**: `localhost:9092`
- **Data Format**: JSON
- **Group ID**: `rtd-query-client` (for consumer applications)
- **Startup Mode**: `latest-offset` (configurable)

### Kafka Topics

| Topic Name | Description | Key Fields |
|------------|-------------|------------|
| `rtd.comprehensive.routes` | Complete vehicle and route data with real-time positions | route_id, vehicle_id, latitude, longitude, delay_seconds, occupancy_status |
| `rtd.route.summary` | Aggregated statistics and on-time performance per route | route_id, total_vehicles, on_time_performance, average_delay_seconds |
| `rtd.vehicle.tracking` | Detailed individual vehicle monitoring with enhanced metrics | vehicle_id, route_id, speed_kmh, delay_status, passenger_load, tracking_quality |
| `rtd.vehicle.positions` | Raw vehicle position data from GTFS-RT feeds | vehicle_id, latitude, longitude, bearing, speed, current_status |
| `rtd.trip.updates` | Trip delay and schedule relationship updates | trip_id, route_id, delay_seconds, schedule_relationship |
| `rtd.alerts` | Service alerts and disruption notifications | alert_id, cause, effect, header_text, active_period_start/end |

## Secondary Data Sinks

### 1. HTTP REST API
**Implementation**: `RTDStaticDataPipeline.java`
- **Port**: 8080
- **Purpose**: Real-time data serving for web applications

#### Endpoints
- `GET /api/vehicles` - Current vehicle positions in JSON format
- `GET /api/health` - System health status and metrics
- `POST /api/config/interval` - Dynamic configuration updates

#### Sample Response (Vehicle Data)
```json
{
  "vehicles": [
    {
      "vehicle_id": "RTD_BUS_FF2_001",
      "vehicle_label": "1234",
      "route_id": "FF2",
      "latitude": 39.7392,
      "longitude": -104.9903,
      "bearing": 180.0,
      "speed": 25.5,
      "current_status": "IN_TRANSIT_TO",
      "occupancy_status": "FEW_SEATS_AVAILABLE",
      "timestamp_ms": 1702934400000,
      "is_real_time": true
    }
  ],
  "metadata": {
    "total_count": 125,
    "last_update": "2024-12-18T12:00:00Z",
    "source": "RTD GTFS-RT Live Feed"
  }
}
```

### 2. Print Sink (Development/Testing)
**Implementation**: `SimpleRTDPipeline.java`
- **Purpose**: Console output for development and debugging
- **Output Prefix**: `RTD-LIVE-DATA`
- **Usage**: Development and testing environments only

## Data Flow Architecture

```
┌─────────────────────┐
│  GTFS-RT Sources    │
│  - Vehicle Position │
│  - Trip Updates     │
│  - Service Alerts   │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  Apache Flink       │
│  Stream Processing  │
│  - Watermarking     │
│  - Transformation   │
│  - Aggregation      │
└──────────┬──────────┘
           │
           ├──────────────────────┐
           ▼                      ▼
┌─────────────────────┐  ┌─────────────────┐
│   Kafka Topics      │  │   HTTP API      │
│  (Primary Sink)     │  │  (Port 8080)    │
└──────────┬──────────┘  └────────┬─────────┘
           │                       │
           ▼                       ▼
┌─────────────────────────────────────────┐
│         Consumer Applications           │
│  - RTDDataSinkQueryClient (CLI)         │
│  - React Transit Map (Web UI)           │
│  - Analytics Dashboards                 │
│  - Third-party Integrations             │
└─────────────────────────────────────────┘
```

## Query Client Interface

### RTDDataSinkQueryClient
A command-line tool for querying Kafka data sinks.

#### Available Commands
```bash
# List all available tables/topics
java -jar rtd-query-client.jar list

# Query comprehensive routes data
java -jar rtd-query-client.jar routes [limit]

# Query route summary statistics
java -jar rtd-query-client.jar summary [limit]

# Query vehicle tracking data
java -jar rtd-query-client.jar tracking [limit]

# Query specific route
java -jar rtd-query-client.jar route <route_id> [limit]

# Query specific vehicle
java -jar rtd-query-client.jar vehicle <vehicle_id> [limit]

# Query on-time performance
java -jar rtd-query-client.jar ontime [limit]

# Query delayed services
java -jar rtd-query-client.jar delays [limit]

# Live monitoring mode
java -jar rtd-query-client.jar live [limit]
```

## Table Schema

### Comprehensive Table Structure
The Kafka topics use a flexible JSON schema that supports all RTD data types:

```sql
CREATE TABLE rtd_comprehensive (
    -- Route Information
    route_id STRING,
    route_short_name STRING,
    route_long_name STRING,
    route_type STRING,
    
    -- Vehicle Information
    vehicle_id STRING,
    vehicle_latitude DOUBLE,
    vehicle_longitude DOUBLE,
    vehicle_bearing FLOAT,
    vehicle_speed FLOAT,
    vehicle_status STRING,
    
    -- Service Information
    trip_id STRING,
    delay_seconds INT,
    occupancy_status STRING,
    schedule_relationship STRING,
    
    -- Aggregated Metrics
    total_vehicles INT,
    active_vehicles INT,
    average_delay_seconds DOUBLE,
    on_time_performance DOUBLE,
    
    -- Metadata
    last_updated BIGINT,
    timestamp_ms BIGINT
)
```

## Data Retention and Performance

### Kafka Configuration
- **Retention Period**: Configurable (default: 7 days)
- **Partition Count**: Based on expected throughput
- **Replication Factor**: 1 (development), 3 (production)

### Performance Characteristics
- **Update Frequency**: 1-60 seconds (configurable)
- **Latency**: < 1 second end-to-end
- **Throughput**: Supports 1000+ vehicles updating per second

## Integration Points

### Producer Side (Data Input)
- Apache Flink jobs produce to Kafka topics
- HTTP endpoints accept configuration updates
- Direct GTFS-RT feed consumption

### Consumer Side (Data Output)
- **Real-time Applications**: React web UI, mobile apps
- **Analytics**: Batch processing, reporting dashboards
- **Monitoring**: Alerting systems, operations centers
- **External Systems**: Third-party transit applications

## Error Handling

### Kafka Error Handling
- **Dead Letter Queue**: Failed messages sent to error topics
- **Retry Logic**: Configurable retry attempts with backoff
- **Schema Evolution**: JSON format allows field additions

### HTTP API Error Handling
- **CORS Support**: Enabled for cross-origin requests
- **Rate Limiting**: Configurable per endpoint
- **Health Checks**: Automatic service monitoring

## Security Considerations

### Kafka Security
- **Authentication**: SASL/PLAIN or SASL/SCRAM (production)
- **Authorization**: ACLs for topic access control
- **Encryption**: SSL/TLS for data in transit

### HTTP API Security
- **HTTPS**: TLS encryption (production)
- **Authentication**: API key or OAuth2 (configurable)
- **Rate Limiting**: Per-client request limits

## Monitoring and Observability

### Metrics
- **Kafka Metrics**: Consumer lag, throughput, error rates
- **HTTP Metrics**: Request rates, response times, error codes
- **Data Quality**: Completeness, timeliness, accuracy

### Logging
- **Application Logs**: SLF4J with Log4j2
- **Audit Logs**: Data access and modifications
- **Performance Logs**: Query execution times

## Future Enhancements

### Planned Data Sinks
1. **PostgreSQL/TimescaleDB**: Historical data storage
2. **Elasticsearch**: Full-text search and analytics
3. **S3/Cloud Storage**: Long-term archival
4. **WebSocket**: Real-time push notifications
5. **GraphQL API**: Flexible query interface

### Scalability Improvements
- **Kafka Streams**: Stateful stream processing
- **Apache Pulsar**: Alternative messaging system
- **Redis Cache**: Low-latency data access
- **CDN Integration**: Global data distribution

## Usage Examples

### Consuming from Kafka (Java)
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "my-consumer-group");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("rtd.vehicle.positions"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        System.out.printf("Vehicle Data: %s%n", record.value());
    }
}
```

### Consuming from HTTP API (JavaScript)
```javascript
// Fetch current vehicle positions
async function getVehicles() {
    const response = await fetch('http://localhost:8080/api/vehicles');
    const data = await response.json();
    
    data.vehicles.forEach(vehicle => {
        console.log(`Vehicle ${vehicle.vehicle_id} on route ${vehicle.route_id} at ${vehicle.latitude}, ${vehicle.longitude}`);
    });
}

// Poll for updates every 5 seconds
setInterval(getVehicles, 5000);
```

## Troubleshooting

### Common Issues
1. **No data in Kafka topics**
   - Verify Flink jobs are running
   - Check GTFS-RT feed availability
   - Review producer logs for errors

2. **HTTP API not responding**
   - Confirm RTDStaticDataPipeline is running
   - Check port 8080 availability
   - Review CORS configuration

3. **Query client timeout**
   - Ensure Kafka is running
   - Verify topic exists with data
   - Check network connectivity

### Debug Commands
```bash
# List Kafka topics
kafka-topics --list --bootstrap-server localhost:9092

# View topic contents
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic rtd.vehicle.positions --from-beginning --max-messages 10

# Check consumer group status
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group rtd-query-client --describe

# Test HTTP API
curl http://localhost:8080/api/health
```

## Contact and Support

For questions or issues related to data sinks:
- **Documentation**: This file and CLAUDE.md
- **Issues**: GitHub repository issues
- **Monitoring**: Check system health at `/api/health`