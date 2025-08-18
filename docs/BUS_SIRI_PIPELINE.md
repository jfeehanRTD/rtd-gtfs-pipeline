# RTD Bus Communication Pipeline (SIRI)

## Overview

The RTD Bus Communication Pipeline implements SIRI (Service Interface for Real-time Information) standard for real-time bus tracking and monitoring. This pipeline receives, processes, and stores bus location and status data using Apache Flink and Kafka.

## Architecture

### Components

1. **BusCommHTTPReceiver** - HTTP server that:
   - Receives SIRI XML/JSON payloads
   - Manages SIRI subscriptions
   - Forwards data to Kafka

2. **RTDBusCommPipeline** - Flink pipeline that:
   - Consumes SIRI data from Kafka
   - Parses both XML and JSON formats
   - Persists data to file system
   - Provides real-time monitoring

## Configuration

### SIRI Subscription Parameters
```json
{
  "host": "http://172.23.4.136:8080",
  "service": "siri",
  "ttl": 90000
}
```

### Endpoints
- **Data Receiver**: `http://localhost:8082/bus-siri`
- **Subscription**: `http://localhost:8082/subscribe`
- **Health Check**: `http://localhost:8082/health`
- **Status**: `http://localhost:8082/status`

### Kafka Configuration
- **Topic**: `rtd.bus.siri`
- **Bootstrap Servers**: `localhost:9092`
- **Consumer Group**: `rtd-bus-comm-consumer`

## Data Format

### JSON Format
```json
{
  "vehicle_id": "BUS_001",
  "route_id": "15",
  "direction": "EASTBOUND",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 25.5,
  "status": "IN_TRANSIT",
  "next_stop": "Union Station",
  "delay_seconds": 120,
  "occupancy": "MANY_SEATS_AVAILABLE",
  "block_id": "BLK_15_01",
  "trip_id": "TRIP_15_0800"
}
```

### SIRI XML Format
```xml
<Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
  <ServiceDelivery>
    <VehicleMonitoringDelivery>
      <VehicleActivity>
        <MonitoredVehicleJourney>
          <VehicleRef>BUS_001</VehicleRef>
          <LineRef>15</LineRef>
          <DirectionRef>EASTBOUND</DirectionRef>
          <VehicleLocation>
            <Latitude>39.7392</Latitude>
            <Longitude>-104.9903</Longitude>
          </VehicleLocation>
          ...
        </MonitoredVehicleJourney>
      </VehicleActivity>
    </VehicleMonitoringDelivery>
  </ServiceDelivery>
</Siri>
```

## Usage

### Starting the Pipeline

#### 1. Start Bus SIRI HTTP Receiver
```bash
./rtd-control.sh bus-comm receiver
```

#### 2. Start Bus Communication Pipeline
```bash
./rtd-control.sh bus-comm run
```

#### 3. Subscribe to SIRI Feed
```bash
./rtd-control.sh bus-comm subscribe
# Or with custom parameters:
./rtd-control.sh bus-comm subscribe "http://172.23.4.136:8080" "siri" 90000
```

### Testing

#### Send Test Data
```bash
./rtd-control.sh bus-comm test
```

#### Monitor Bus Data
```bash
./rtd-control.sh bus-comm monitor
```

#### Check Status
```bash
./rtd-control.sh bus-comm status
```

### Using the Subscription Script
```bash
# Subscribe with default settings
./scripts/bus-siri-subscribe.sh

# Subscribe with custom settings
./scripts/bus-siri-subscribe.sh "http://172.23.4.136:8080" "siri" 90000
```

## Occupancy Levels

The pipeline supports standard SIRI occupancy levels:
- `EMPTY` - No passengers
- `MANY_SEATS_AVAILABLE` - Less than 1/3 full
- `FEW_SEATS_AVAILABLE` - 1/3 to 2/3 full
- `STANDING_ROOM_ONLY` - All seats taken
- `CRUSHED_STANDING_ROOM_ONLY` - Very crowded
- `FULL` - At capacity
- `NOT_ACCEPTING_PASSENGERS` - No boarding allowed

## Bus Status Values

- `IN_TRANSIT` - Moving between stops
- `STOPPED_AT` - At a stop
- `IN_SERVICE` - Active on route
- `OUT_OF_SERVICE` - Not in service
- `DELAYED` - Behind schedule
- `ON_TIME` - On schedule
- `EARLY` - Ahead of schedule

## Data Persistence

Processed bus data is saved to:
- **Directory**: `./data/bus-comm/`
- **Format**: JSON files with rolling policy
- **Rollover**: Every 15 minutes or 128MB
- **Inactivity Timeout**: 5 minutes

## Monitoring

### Real-time Console Output
The pipeline provides two output formats:
1. **JSON Format** - Complete data structure
2. **Formatted Output** - Human-readable summary

Example formatted output:
```
BUS_SIRI: [2024-01-01 12:00:00] Vehicle=BUS_001 Route=15 Status=IN_TRANSIT Position=39.739200,-104.990300 Speed=25.5 mph Occupancy=MANY_SEATS_AVAILABLE
```

### Kafka Monitoring
```bash
# View messages in the bus SIRI topic
./scripts/kafka-console-consumer.sh --topic rtd.bus.siri
```

## Integration with RTD System

The Bus Communication Pipeline integrates with:
- RTD's SIRI-compliant bus tracking system
- Kafka message broker for data distribution
- Apache Flink for stream processing
- File system for data persistence

## Troubleshooting

### Check if Services are Running
```bash
# Check HTTP Receiver
curl http://localhost:8082/health

# Check Pipeline Status
./rtd-control.sh status
```

### View Logs
```bash
# Java pipeline logs
./rtd-control.sh logs java

# Or directly
tail -f rtd-pipeline.log
```

### Common Issues

1. **Port Already in Use**
   - HTTP Receiver uses port 8082
   - Ensure no other service is using this port

2. **Kafka Connection Failed**
   - Ensure Kafka is running: `./rtd-control.sh docker start`
   - Check Kafka is accessible at `localhost:9092`

3. **SIRI Subscription Failed**
   - Verify the SIRI endpoint is accessible
   - Check network connectivity to the SIRI host
   - Ensure correct subscription parameters

## Testing

Run the comprehensive test suite:
```bash
mvn test -Dtest=BusCommPipelineTest
```

The test suite covers:
- JSON/XML parsing
- HTTP endpoint connectivity
- SIRI subscription configuration
- Kafka producer functionality
- Multiple bus routes
- Occupancy levels
- Status values
- Location boundaries
- Subscription renewal logic