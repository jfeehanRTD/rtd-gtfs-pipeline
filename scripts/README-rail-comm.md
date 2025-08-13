# RTD Rail Communication Pipeline Setup

This guide explains how to set up and use the RTD Rail Communication Pipeline with proxy subscription.

## Overview

The rail communication system consists of:
1. **HTTP Receiver** - Receives JSON payloads from proxy and forwards to Kafka
2. **Kafka Topic** - `rtd.rail.comm` topic for rail communication data
3. **Flink Pipeline** - Processes JSON data and persists to file system
4. **Proxy Subscription** - Subscribes to external rail comm feed

## Quick Start

### 1. Start Kafka and Create Topics
```bash
./rtd-control.sh docker start
```

### 2. Start HTTP Receiver (receives proxy data)
```bash
./rtd-control.sh rail-comm receiver
```

### 3. Start Flink Pipeline (processes data)
```bash
# In another terminal
./rtd-control.sh rail-comm run
```

### 4. Subscribe to Proxy Feed
```bash
# In another terminal  
./rtd-control.sh rail-comm subscribe
```

### 5. Monitor Real-time Data
```bash
# In another terminal
./rtd-control.sh rail-comm monitor
```

## Components Details

### HTTP Receiver (`RailCommHTTPReceiver.java`)
- **Port**: 8081
- **Endpoint**: `/rail-comm`
- **Health Check**: `/health`
- **Function**: Receives JSON POST requests and forwards to Kafka topic

### Proxy Subscription (`proxy-subscribe.sh`)
- **Auto-detects local IP** for HOST_URL
- **Endpoint**: `http://[LOCAL_IP]:8081/rail-comm`
- **Subscription Target**: RTD rail communication proxy feed

### Flink Pipeline (`RTDRailCommPipeline.java`)
- **Consumes**: `rtd.rail.comm` Kafka topic
- **Processes**: JSON rail communication data
- **Outputs**: 
  - File sink: `./data/rail-comm/`
  - Console sink: Real-time monitoring

## Data Flow

```
External Proxy Feed → HTTP Receiver (8081) → Kafka Topic → Flink Pipeline → File Storage
                                    ↓
                            Real-time Console Output
```

## Testing

### Send Test Data
```bash
./rtd-control.sh rail-comm test
```

### Monitor Topic
```bash
./scripts/test-rail-comm.sh monitor
```

### Check HTTP Receiver Health
```bash
curl http://localhost:8081/health
```

## JSON Data Format

Expected rail communication JSON format:
```json
{
  "train_id": "LRV-001",
  "line_id": "A-Line", 
  "direction": "Northbound",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 35.2,
  "status": "ON_TIME",
  "next_station": "Union Station",
  "delay_seconds": 0,
  "operator_message": "Normal operation",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Troubleshooting

### Check Services Status
```bash
./rtd-control.sh status
./rtd-control.sh docker status
```

### View Logs
```bash
# HTTP Receiver logs
./rtd-control.sh logs java

# Kafka topic contents
./scripts/kafka-topics --list
```

### Test Connectivity
```bash
# Test proxy subscription
./scripts/proxy-subscribe.sh test

# Test HTTP receiver
curl -X POST http://localhost:8081/rail-comm \
  -H "Content-Type: application/json" \
  -d '{"train_id": "TEST-001", "status": "TESTING"}'
```

## File Outputs

- **Pipeline Logs**: `rtd-pipeline.log`
- **Rail Comm Data**: `./data/rail-comm/` directory
- **JSON Format**: Structured data files with processing timestamps

This setup enables real-time processing of RTD rail communication data from external proxy feeds through Kafka and Flink.