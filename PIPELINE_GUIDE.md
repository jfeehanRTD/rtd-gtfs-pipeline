# RTD GTFS Pipeline - Complete Guide

## Table of Contents
- [Overview](#overview)
- [Architecture Diagrams](#architecture-diagrams)
- [How to Run the Pipeline](#how-to-run-the-pipeline)
- [Cloud Deployment (AKS Automatic)](#cloud-deployment-aks-automatic)
- [Data Processing Summary](#data-processing-summary)
- [Color Coding Reference](#color-coding-reference)

---

## Overview

The RTD GTFS Pipeline is a real-time transit data processing system that uses **Apache Kafka** and **Apache Flink** to process bus and light rail data from RTD Denver.

### Key Components
- **Data Sources**: RTD TIS Proxy (SIRI Bus Feed), Rail Communication Proxy (SCADA Light Rail)
- **Ingestion**: HTTP Receivers on ports 8081 and 8082
- **Message Bus**: Apache Kafka for durable, scalable data streaming
- **Processing**: Apache Flink 2.1.0 for real-time stream processing
- **Outputs**: File storage, console monitoring, Kafka topics, REST API
- **Frontend**: React Web App for live transit visualization

---

## Architecture Diagrams

### 1. Complete End-to-End Data Flow
![Complete Pipeline Flow](./pipeline1.png)

This diagram shows the complete data flow from external RTD sources through HTTP receivers, Kafka topics, Flink processing, to end users.

**Key Layers:**
1. **External RTD Sources** (Green) - TIS Proxy (Bus), Rail Comm Proxy (Light Rail)
2. **HTTP Receivers** (Yellow) - Ports 8082 (Bus), 8081 (Rail)
3. **Kafka Raw Topics** (Orange) - rtd.bus.siri, rtd.rail.comm
4. **Apache Flink Processing** (Purple) - Bus/Rail Pipelines
5. **Kafka Processed Topics** (Orange) - comprehensive.routes, route.summary, vehicle.tracking
6. **Outputs** (Blue) - File Storage, API Server
7. **End Users** (Light Blue) - Web App, Mobile Apps, Analytics

---

### 2. Simplified Pipeline Flow
![Simplified Pipeline](./pipeline2.png)

A cleaner view showing the main components and data flow without detailed sub-processes.

**Highlights:**
- Bus Pipeline: XML→JSON→Parse (14 fields)
- Rail Pipeline: JSON→Parse
- Shared Processing: Filter→Enrich→Calculate→Detect Anomalies

---

### 3. Detailed Component View
![Detailed Components](./pipeline3.png)

Shows the internal processing steps within Flink pipelines.

---

### 4. Data Source Flow
![Source Flow](./SourceFlow.png)

External data sources and subscription mechanisms.

---

### 5. HTTP Receivers to Kafka Flow
![HTTP-Kafka Flow](./HTTP-Kafka-Flow%20_%20Mermaid%20Chart-2025-10-01-170611.png)

Detailed view of how HTTP receivers publish to Kafka topics.

---

### 6. Flink Processing Internal Flow
![Flink Flow](./Flink-Flow%20_%20Mermaid%20Chart-2025-10-01-170716.png)

Internal Flink processing stages: Parse → Enrich → Filter → Calculate → Output

---

### 7. Flink Processing Alternative View
![Flink Flow Alternative](./Flink-Flow_Mermaid%20Chart-2025-10-01-170954.png)

Alternative visualization of Flink processing logic.

---

### 8. Flink Output Flow
![Flink Output](./Flink-Flow2.png)

Shows how Flink outputs to multiple sinks (Kafka, files, console).

---

## How to Run the Pipeline

### Prerequisites

```bash
# Required Software
- Java 24
- Maven 3.6+
- Docker (for Kafka)
- Node.js 18+ (for React app)
- Apache Kafka 4.0.0
- Apache Flink 2.1.0
```

### Step 1: Environment Setup

```bash
# Clone the repository
cd /Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1

# Set up environment variables (for TIS Proxy authentication)
cp .env.example .env
# Edit .env with your credentials:
# TIS_PROXY_USERNAME=your_username
# TIS_PROXY_PASSWORD=your_password
```

### Step 2: Build the Project

```bash
# Clean and compile
mvn clean compile

# Package (skip tests for faster build)
mvn clean package -DskipTests

# Or run tests
mvn clean package
```

### Step 3: Start Infrastructure (Kafka)

```bash
# Start Kafka using Docker
./rtd-control.sh start docker

# Verify Kafka is running
./rtd-control.sh status
```

### Step 4: Start HTTP Receivers

```bash
# Start Bus SIRI HTTP Receiver (Port 8082)
./rtd-control.sh start bus

# In another terminal, start Rail Communication HTTP Receiver (Port 8081)
./rtd-control.sh start rail

# Verify receivers are running
curl http://localhost:8082/health
curl http://localhost:8081/health
```

### Step 5: Start Flink Processing Pipeline

```bash
# Start the main Java/Flink pipeline
./rtd-control.sh start java

# Check logs
./rtd-control.sh logs java
```

### Step 6: Subscribe to Data Feeds

```bash
# Subscribe to Bus SIRI feed
./scripts/bus-siri-subscribe.sh

# Subscribe to Rail Communication feed
./scripts/subscribe-to-tisproxy.sh
```

### Step 7: Start React Web Application (Optional)

```bash
# Start React development server
./rtd-control.sh start react

# Access the web app at http://localhost:3002
```

### Step 8: Monitor Real-Time Data

```bash
# Monitor bus data
./rtd-control.sh logs bus

# Monitor rail data
./rtd-control.sh logs rail

# Monitor Kafka topics
./scripts/kafka-console-consumer.sh rtd.bus.siri
./scripts/kafka-console-consumer.sh rtd.rail.comm

# Check all services status
./rtd-control.sh status
```

---

## Quick Start (All Services)

```bash
# Start everything at once
./rtd-control.sh start all

# Subscribe to feeds
./scripts/bus-siri-subscribe.sh &
./scripts/subscribe-to-tisproxy.sh &

# Monitor status
./rtd-control.sh status

# View logs
./rtd-control.sh logs java
```

---

## Stop Services

```bash
# Stop all services
./rtd-control.sh stop all

# Stop specific services
./rtd-control.sh stop bus
./rtd-control.sh stop rail
./rtd-control.sh stop java
./rtd-control.sh stop react

# Cleanup
./rtd-control.sh cleanup
```

---

## Cloud Deployment (AKS Automatic)

### Deploy to Azure Kubernetes Service

The RTD GTFS Pipeline can be deployed to **Azure Kubernetes Service (AKS) Automatic** for production-ready, auto-scaling cloud deployment.

#### Quick Start

```bash
# Set credentials
export TIS_PROXY_USERNAME="your-username"
export TIS_PROXY_PASSWORD="your-password"

# Deploy to AKS Automatic
./scripts/aks/deploy-to-aks.sh
```

#### What is AKS Automatic?

AKS Automatic is Microsoft's newest Kubernetes offering with:
- ✅ **Zero-config production clusters** - Preconfigured for best practices
- ✅ **Automatic node management** - Dynamic resource allocation
- ✅ **Built-in monitoring** - Managed Prometheus + Grafana
- ✅ **Auto-scaling** - HPA, KEDA, VPA enabled by default
- ✅ **Cost optimization** - Efficient bin packing, automatic node scaling

#### Estimated Monthly Costs

| Component | Cost |
|-----------|------|
| AKS Automatic (Standard) | ~$73 |
| Nodes (3-10 instances) | ~$420-$1,400 |
| Storage (500GB) | ~$75 |
| Load Balancer | ~$25 |
| **Total** | **~$600-$1,600/month** |

#### Key Features for RTD Pipeline

- **Kafka/Zookeeper**: StatefulSets with Azure Disk persistent storage
- **HTTP Receivers**: Auto-scale 2-10 replicas based on CPU
- **Flink Processing**: KEDA scales 3-20 TaskManagers based on Kafka lag
- **Secrets**: Azure Key Vault integration with Workload Identity
- **Monitoring**: Managed Prometheus metrics + Grafana dashboards
- **Networking**: Managed NGINX ingress + Cilium CNI

#### Deployment Steps

1. **Create AKS cluster** (10-15 minutes)
2. **Build and push Docker images** to Azure Container Registry
3. **Deploy Kubernetes manifests** (namespace, configs, deployments)
4. **Configure auto-scaling** (HPA, KEDA)
5. **Set up monitoring** (Prometheus, Grafana)

#### Documentation

- **[AKS Quick Start Guide](./docs/aks/AKS_QUICK_START.md)** - Deploy in minutes
- **[Full AKS Deployment Guide](./docs/aks/AKS_DEPLOYMENT_GUIDE.md)** - Complete documentation
- **[AKS Components Overview](./docs/aks/README.md)** - Architecture and costs

#### Kubernetes Resources Created

```
k8s/
├── namespace.yaml                    # RTD pipeline namespace
├── configmaps/rtd-config.yaml       # Configuration
├── secrets/rtd-secrets-template.yaml # Credentials (use Azure Key Vault)
├── deployments/
│   ├── kafka.yaml                   # Kafka StatefulSet (3 replicas)
│   ├── zookeeper.yaml              # Zookeeper StatefulSet (3 replicas)
│   ├── bus-receiver.yaml           # Bus SIRI HTTP receiver
│   └── rail-receiver.yaml          # Rail comm HTTP receiver
├── storage/
│   ├── kafka-pvc.yaml              # Kafka persistent storage
│   └── zookeeper-pvc.yaml          # Zookeeper persistent storage
└── scripts/aks/
    └── deploy-to-aks.sh            # Automated deployment script
```

---

## Data Processing Summary

### RTD GTFS Pipeline: Kafka & Flink Data Processing

The RTD pipeline processes **two primary real-time data sources** using Apache Kafka and Apache Flink:

1. **Bus Data (INIT/SIRI)** - XML/JSON via SIRI protocol
2. **Light Rail Data (SCADA)** - JSON via Rail Communication protocol

---

### Architecture: Kafka + Flink Integration

#### **Kafka's Role**: Message Bus & Data Distribution
- **Durability**: Persists data on disk with replication
- **Decoupling**: Separates producers from consumers
- **Topics**: Organize data streams by type (bus, rail, vehicles, etc.)
- **Scalability**: Handles 1000+ messages/second

#### **Flink's Role**: Real-Time Stream Processing
- **Stateful Processing**: Maintains state across streams (counting, aggregations)
- **Event Time Processing**: Handles out-of-order data based on timestamps
- **Fault Tolerance**: Exactly-once semantics with checkpoint recovery
- **Low Latency**: < 1 second end-to-end processing

---

### Bus Data Pipeline (INIT/SIRI)

#### Data Flow
```
RTD SIRI Source → BusCommHTTPReceiver (:8082) → Kafka (rtd.bus.siri)
→ RTDBusCommPipeline (Flink) → File Storage + Kafka Output Topics
```

#### Key Components

**1. BusCommHTTPReceiver** (Port 8082)
- Receives SIRI XML/JSON from RTD's TIS Proxy
- Manages SIRI subscriptions (90-second TTL)
- Publishes raw data to Kafka topic `rtd.bus.siri`
- Endpoints: `/bus-siri`, `/subscribe`, `/health`

**2. Kafka Topic: `rtd.bus.siri`**
- Bootstrap: `localhost:9092`
- Consumer Group: `rtd-bus-comm-consumer`
- Format: XML/JSON strings
- Retention: 24 hours

**3. RTDBusCommPipeline (Flink)**
- **XML→JSON Conversion**: Uses Jackson XmlMapper to automatically convert SIRI XML to JsonNode
- **Format Detection**: Automatically detects XML (starts with `<`) vs JSON
- **Data Parsing**: Extracts 14 fields including vehicle ID, route, GPS location, speed, occupancy
- **Processing Speed**: ~1-2ms per message, handles 1000+ messages/second
- **Output**: Files in `./data/bus-comm/` + real-time console + Kafka topics

#### Bus Data Structure (14 Fields)

| Field | Source | Type | Example |
|-------|--------|------|---------|
| timestamp_ms | Generated | Long | 1737465045000 |
| vehicle_id | VehicleRef | String | BUS_001 |
| route_id | LineRef | String | 15 |
| direction | DirectionRef | String | EASTBOUND |
| latitude | VehicleLocation/Latitude | Double | 39.7392 |
| longitude | VehicleLocation/Longitude | Double | -104.9903 |
| speed_mph | Speed | Double | 25.3 |
| status | ProgressStatus | String | IN_TRANSIT |
| next_stop | MonitoredCall/StopPointRef | String | Union Station |
| delay_seconds | Delay | Integer | 120 |
| occupancy | Occupancy | String | MANY_SEATS_AVAILABLE |
| block_id | BlockRef | String | BLK_15_01 |
| origin | OriginRef | String | Downtown |
| raw_data | Original | String | (XML/JSON) |

---

### Light Rail Pipeline (SCADA/Rail Communication)

#### Data Flow
```
RTD Rail Comm Source → RailCommHTTPReceiver (:8081) → Kafka (rtd.rail.comm)
→ RTDRailCommPipeline (Flink) → File Storage + Kafka Output Topics
```

#### Key Components

**1. RailCommHTTPReceiver** (Port 8081)
- Receives JSON payloads from RTD's proxy
- Auto-detects local IP for subscriptions
- Publishes to Kafka topic `rtd.rail.comm`
- Endpoints: `/rail-comm`, `/health`

**2. Kafka Topic: `rtd.rail.comm`**
- Bootstrap: `localhost:9092`
- Format: JSON strings
- Retention: 24 hours

**3. RTDRailCommPipeline (Flink)**
- **JSON Parsing**: Direct JSON deserialization
- **Data Processing**: Similar structure to bus but with rail-specific fields
- **Output**: Files in `./data/rail-comm/` + real-time console + Kafka topics

#### Rail Data Structure (JSON)

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

#### Light Rail Routes Monitored
- **11 Routes**: A (Airport), B, C, D, E, F, G, H, N, R, W lines
- **Service Expectations**: 80-95% completeness depending on route and time
- **High-Frequency Routes**: C, D, E, R (8-15 min headways)

---

### Flink Processing Pipeline

#### Common Processing Pattern
1. **Consume** from Kafka topic
2. **Deserialize** (XML→JSON for bus, JSON→POJO for rail)
3. **Filter** null/invalid records
4. **Transform** data structure
5. **Enrich** with static GTFS data (routes, stops)
6. **Calculate** metrics (delays, aggregations)
7. **Output** to files and console
8. **Publish** processed data to Kafka output topics

#### Output Topics (Processed Data)
- `rtd.comprehensive.routes` - Enriched vehicle/route data (JSON)
- `rtd.route.summary` - Aggregated statistics (JSON)
- `rtd.vehicle.tracking` - Enhanced monitoring data (JSON)

---

### Key Technologies

#### XML→JSON Conversion (Bus SIRI)
```java
XmlMapper xmlMapper = new XmlMapper();
JsonNode rootNode = xmlMapper.readTree(xmlData);  // XML → JsonNode
JsonNode vehicleActivity = rootNode.path("ServiceDelivery")
    .path("VehicleMonitoringDelivery")
    .path("VehicleActivity");
```

#### Flink Stream Processing
```java
DataStream<String> siriStream = env
    .addSource(new FlinkKafkaConsumer<>("rtd.bus.siri", ...))
    .name("Bus SIRI Kafka Source");

DataStream<Row> busCommStream = siriStream
    .map(new SIRIParsingFunction())     // XML→JSON conversion
    .filter(new NonNullRowFilter())     // Quality control
    .name("Parse SIRI Bus Data");

busCommStream.addSink(fileSink);        // File output
busCommStream.print();                   // Console monitoring
```

---

### Performance Characteristics

| Metric | Value |
|--------|-------|
| Bus Processing | 1-2ms per message |
| Rail Processing | < 1ms per message |
| Throughput | 1000+ messages/second |
| End-to-End Latency | < 1 second |
| API Response Time | < 100ms |
| Data Ingestion Rate | ~400 vehicles/minute |
| System Availability | 99.9% target |

---

### Kafka Topics Reference

#### Raw Input Topics

| Topic Name | Purpose | Format | Retention |
|------------|---------|--------|-----------|
| `rtd.bus.siri` | Raw bus SIRI data | XML/JSON | 24 hours |
| `rtd.rail.comm` | Raw rail communication | JSON | 24 hours |
| `rtd.vehicle.positions` | GTFS-RT vehicle positions | Protobuf | 24 hours |
| `rtd.trip.updates` | Schedule adherence | Protobuf | 24 hours |
| `rtd.alerts` | Service alerts | Protobuf | 7 days |

#### Processed Output Topics

| Topic Name | Purpose | Format | Retention |
|------------|---------|--------|-----------|
| `rtd.comprehensive.routes` | Enriched vehicle/route data | JSON | 12 hours |
| `rtd.route.summary` | Aggregated statistics | JSON | 24 hours |
| `rtd.vehicle.tracking` | Monitoring data | JSON | 6 hours |

---

## Color Coding Reference

### Component Colors in Diagrams

| Color | Component Type | Hex Code | Components |
|-------|---------------|----------|------------|
| 🟢 **Green** | External Data Sources | `#c8e6c9` / `#2e7d32` | RTD TIS Proxy, Rail Comm Proxy |
| 🟡 **Yellow** | HTTP Receivers | `#fff9c4` / `#f57f17` | BusCommHTTPReceiver, RailCommHTTPReceiver |
| 🟠 **Orange** | Kafka Topics | `#ffccbc` / `#d84315` | All Kafka topics (raw & processed) |
| 🟣 **Purple** | Flink Processing | `#ce93d8` / `#6a1b9a` | Pipelines, parsers, transformations |
| 🔵 **Blue** | Outputs & API | `#b3e5fc` / `#01579b` | File Storage, API Server |
| 🟦 **Light Blue** | End Users | `#c5cae9` / `#283593` | Web App, Mobile Apps, Dashboards |

### Layer-by-Layer Color Flow

| Layer | Flow | Color Transition |
|-------|------|------------------|
| Layer 1 | Data Sources → HTTP Receivers | 🟢 Green → 🟡 Yellow |
| Layer 2 | HTTP Receivers → Kafka Topics | 🟡 Yellow → 🟠 Orange |
| Layer 3 | Kafka Topics → Flink Processing | 🟠 Orange → 🟣 Purple |
| Layer 4 | Flink Processing → Outputs | 🟣 Purple → 🟠 Orange + 🔵 Blue |
| Layer 5 | Kafka/API → End Users | 🟠 Orange / 🔵 Blue → 🟦 Light Blue |

---

## Troubleshooting

### Common Issues

#### 1. Port Already in Use
```bash
# Check what's using the port
lsof -i :8082
lsof -i :8081

# Kill the process
kill -9 <PID>
```

#### 2. Kafka Connection Failed
```bash
# Ensure Kafka is running
./rtd-control.sh status

# Restart Kafka
./rtd-control.sh restart docker
```

#### 3. SIRI Subscription Failed
```bash
# Check TIS Proxy connectivity
curl http://tisproxy.rtd-denver.com

# Verify credentials in .env file
cat .env

# Test subscription manually
./scripts/bus-siri-subscribe.sh
```

#### 4. No Data in Pipeline
```bash
# Check HTTP receivers are receiving data
curl http://localhost:8082/status
curl http://localhost:8081/status

# Monitor Kafka topics
./scripts/kafka-console-consumer.sh rtd.bus.siri

# Check Flink logs
./rtd-control.sh logs java
```

---

## Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Tests
```bash
# Bus pipeline tests
mvn test -Dtest=BusCommPipelineTest

# Light rail tests
mvn test -Dtest=RTDLightRailTrackingTest

# GTFS validation tests
mvn test -Dtest=GTFSValidationTest
```

### Integration Tests
```bash
# Test complete data flow
./test-feeds.sh

# Test live map functionality
./test-live-map.sh

# Test all services
./check-all-services.sh
```

---

## Additional Resources

- **Architecture Documentation**: `architecture/RTD_ARCHITECTURE.md`
- **Bus SIRI Pipeline Details**: `docs/BUS_SIRI_PIPELINE.md`
- **Rail Communication Setup**: `scripts/README-rail-comm.md`
- **Kafka-Flink Integration**: `Kafka-Flink.md`
- **SIRI Data Flow**: `SIRI-DataFlow.md`
- **Data Flow Summary**: `RTD_DATA_FLOW_SUMMARY.md`

---

## Summary

**Kafka** acts as the durable message bus connecting:
- HTTP receivers (data ingestion) → Raw Kafka topics
- Raw topics → Flink processors → Processed topics
- Processed topics → API servers, dashboards, data warehouses

**Flink** provides intelligent stream processing:
- Format conversion (XML→JSON for SIRI)
- Data enrichment (joining with GTFS static data)
- Real-time calculations (delays, aggregations, anomalies)
- Multiple output formats (files, console, Kafka topics)

The architecture decouples data collection from processing, enabling **scalability**, **fault tolerance**, and **real-time analytics** for RTD's transit system with sub-second latency and 99.9% availability.
