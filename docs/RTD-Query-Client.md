# RTD GTFS-RT Data Sink Query Client

A command-line tool for querying and monitoring RTD GTFS-RT data from Flink Table API sinks in real-time.

## Overview

The RTD Query Client provides easy access to all the data sinks created by the RTD GTFS-RT pipeline:

- **Comprehensive Routes** - Complete vehicle and route data with real-time positions
- **Route Summary** - Aggregated statistics and on-time performance per route  
- **Vehicle Tracking** - Detailed individual vehicle monitoring with enhanced metrics
- **Vehicle Positions** - Raw vehicle position data from GTFS-RT feeds
- **Trip Updates** - Trip delay and schedule relationship updates
- **Alerts** - Service alerts and disruption notifications

## Prerequisites

1. **Java 24+** - Required for running Flink applications
2. **Apache Kafka** - Running on `localhost:9092` (default)
3. **RTD GTFS-RT Pipeline** - Must be running to generate data
4. **Built Project** - Run `mvn clean package` to build the JAR

## Quick Start

### 1. Build the Project
```bash
mvn clean package
```

### 2. Start Services
Make sure these are running:
- Apache Kafka (localhost:9092)
- RTD GTFS-RT Pipeline (producing to Kafka topics)

### 3. Run Health Check
```bash
./scripts/rtd-query health
```

### 4. List Available Tables
```bash
./scripts/rtd-query list
```

### 5. Query Data
```bash
# View comprehensive routes data
./scripts/rtd-query routes 20

# Check route performance
./scripts/rtd-query summary

# Track specific vehicle
./scripts/rtd-query vehicle RTD_LR_A_001

# Monitor live data
./scripts/rtd-query live
```

## Commands Reference

### General Commands

| Command | Description | Example |
|---------|-------------|---------|
| `list` | Show all available tables | `./scripts/rtd-query list` |
| `health` | Run connectivity health check | `./scripts/rtd-query health` |
| `topics` | Show available Kafka topics | `./scripts/rtd-query topics` |

### Data Query Commands

| Command | Description | Example |
|---------|-------------|---------|
| `routes [limit]` | Query comprehensive routes data | `./scripts/rtd-query routes 25` |
| `summary [limit]` | Query route summary statistics | `./scripts/rtd-query summary` |
| `tracking [limit]` | Query vehicle tracking data | `./scripts/rtd-query tracking 15` |
| `positions [limit]` | Query raw vehicle positions | `./scripts/rtd-query positions` |
| `updates [limit]` | Query trip updates | `./scripts/rtd-query updates` |
| `alerts [limit]` | Query active alerts | `./scripts/rtd-query alerts` |

### Specific Queries

| Command | Description | Example |
|---------|-------------|---------|
| `route <id> [limit]` | Query specific route | `./scripts/rtd-query route A 10` |
| `vehicle <id> [limit]` | Query specific vehicle | `./scripts/rtd-query vehicle RTD_LR_A_001` |
| `ontime [limit]` | Query on-time performance | `./scripts/rtd-query ontime` |
| `delays [limit]` | Query delayed services | `./scripts/rtd-query delays` |
| `live [limit]` | Start live monitoring mode | `./scripts/rtd-query live` |

## Example Usage Scenarios

### 1. Monitor System Health
```bash
# Check overall system health
./scripts/rtd-query health

# View route performance summary
./scripts/rtd-query summary

# Check for active alerts
./scripts/rtd-query alerts
```

### 2. Track Specific Route
```bash
# Monitor A-Line (Airport) service
./scripts/rtd-query route A 15

# Check A-Line on-time performance
./scripts/rtd-query summary | grep "A"
```

### 3. Vehicle Monitoring
```bash
# Track a specific vehicle
./scripts/rtd-query vehicle RTD_LR_A_001

# See all vehicle positions
./scripts/rtd-query positions 20

# Monitor vehicle tracking data
./scripts/rtd-query tracking
```

### 4. Service Analysis
```bash
# Find delayed services
./scripts/rtd-query delays

# Check on-time performance across routes
./scripts/rtd-query ontime

# View comprehensive route data
./scripts/rtd-query routes 50
```

### 5. Live Monitoring
```bash
# Start live monitoring (updates every 30 seconds)
./scripts/rtd-query live

# Press Ctrl+C to exit live mode
```

## Data Schema Overview

### Comprehensive Routes
- Complete vehicle and route information
- Real-time positions and status
- Delay information and occupancy
- Stop and service details

### Route Summary  
- Aggregated statistics per route
- On-time performance percentages
- Average delays and service status
- Active vehicle counts

### Vehicle Tracking
- Individual vehicle monitoring
- Speed, position, and bearing
- Passenger load estimation
- Tracking quality metrics

### Raw Data Tables
- **Vehicle Positions**: Direct GTFS-RT vehicle position feed
- **Trip Updates**: Trip delay and schedule updates  
- **Alerts**: Service alerts and disruption notifications

## Configuration

Edit `config/query-client.properties` to customize:

```properties
# Kafka settings
kafka.bootstrap.servers=localhost:9092

# Query defaults
query.default.limit=10
query.timeout.seconds=30

# Display options
display.timestamps.format=yyyy-MM-dd HH:mm:ss
```

## Troubleshooting

### Common Issues

1. **"No data returned"**
   - Ensure RTD pipeline is running
   - Check Kafka topic contains data: `./scripts/rtd-query topics`
   - Verify connectivity: `./scripts/rtd-query health`

2. **"Connection timeout"**
   - Check Kafka is running on localhost:9092
   - Verify firewall settings
   - Confirm topic names in configuration

3. **"Query execution error"**
   - Check Flink dependencies in classpath
   - Verify table schemas match data format
   - Review error logs for specific details

### Debug Mode
```bash
# Run with debug logging
JAVA_OPTS="-Dlog.level=DEBUG" ./scripts/rtd-query routes
```

### Manual Execution
```bash
# Run directly with Java
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
     com.rtd.pipeline.client.RTDDataSinkQueryClient routes 10
```

## Advanced Usage

### Custom SQL Queries
The client creates Flink SQL tables that can be queried programmatically:

```java
// Example: Custom query in your own code
StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env);
TableResult result = tableEnv.executeSql("""
    SELECT route_short_name, AVG(delay_seconds) as avg_delay
    FROM comprehensive_routes_source 
    WHERE vehicle_status = 'IN_TRANSIT_TO'
    GROUP BY route_short_name
    ORDER BY avg_delay DESC
    """);
```

### Integration with Other Tools
- Export data to CSV/JSON for analysis
- Connect to BI tools via Kafka Connect
- Stream data to monitoring dashboards
- Create custom alerting systems

## Performance Considerations

- **Query Limits**: Default limit is 10 records, adjust as needed
- **Live Monitoring**: Updates every 30 seconds to balance freshness vs performance  
- **Memory Usage**: Large result sets may require increased JVM heap
- **Network**: Ensure sufficient bandwidth for real-time data streams

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review RTD pipeline logs
3. Verify Kafka connectivity and topic contents
4. Ensure all prerequisites are met