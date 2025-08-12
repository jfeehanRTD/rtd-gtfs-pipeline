# RTD GTFS-RT Pipeline Architecture

## System Component Diagrams

This document provides detailed architectural diagrams of the RTD GTFS-RT data pipeline showing the integration between Apache Kafka and Apache Flink for real-time transit data processing.

## High-Level Architecture Overview

```mermaid
graph TB
    subgraph "External Systems"
        RTD[RTD GTFS-RT APIs]
        GTFS[Static GTFS Data]
    end
    
    subgraph "Data Ingestion"
        PRODUCER[GTFS-RT Producer<br/>Java Application]
        HTTP[HTTP Fetcher]
        PB[Protobuf Parser]
        KC[Kafka Producer Client]
    end
    
    subgraph "Apache Kafka 4.0.0"
        subgraph "Raw Topics"
            VP[rtd.vehicle.positions]
            TU[rtd.trip.updates]
            AL[rtd.alerts]
        end
        
        subgraph "Processed Topics"
            CR[rtd.comprehensive.routes]
            RS[rtd.route.summary]
            VT[rtd.vehicle.tracking]
        end
    end
    
    subgraph "Apache Flink 1.19.1"
        VPR[Vehicle Processor]
        TPR[Trip Processor]
        APR[Alert Processor]
        AGG[Route Aggregator]
        ANO[Anomaly Detector]
    end
    
    subgraph "API Layer"
        API[HTTP API Server<br/>Port 8080]
        JSON[JSON Converter]
    end
    
    subgraph "Web Application"
        REACT[React Web App<br/>Port 3002]
        MAP[OpenStreetMap]
        TRACK[Vehicle Tracker]
    end
    
    subgraph "Data Consumers"
        DASH[Analytics Dashboard]
        MOB[Mobile Apps]
        DW[Data Warehouse]
    end
    
    %% Data Flow
    RTD -->|HTTPS/PB| HTTP
    HTTP --> PB
    PB --> KC
    KC -->|Publish| VP
    KC -->|Publish| TU
    KC -->|Publish| AL
    
    VP -->|Consume| VPR
    TU -->|Consume| TPR
    AL -->|Consume| APR
    
    GTFS -->|Enrich| VPR
    
    VPR -->|Enriched Data| CR
    VPR -->|Tracking| VT
    TPR -->|Statistics| RS
    APR -->|Context| CR
    
    CR -->|Aggregate| AGG
    AGG -->|Metrics| RS
    
    VT -->|Patterns| ANO
    RS -->|Thresholds| ANO
    
    CR -->|Latest Data| API
    API -->|REST/JSON| REACT
    
    CR -->|Stream| DASH
    CR -->|Stream| MOB
    RS -->|Batch| DW
    
    style RTD fill:#e0e0e0
    style GTFS fill:#e0e0e0
    style PRODUCER fill:#28a745
    style VP fill:#ff6b6b
    style TU fill:#ff6b6b
    style AL fill:#ff6b6b
    style CR fill:#28a745
    style RS fill:#28a745
    style VT fill:#28a745
    style VPR fill:#007acc
    style TPR fill:#007acc
    style APR fill:#007acc
    style API fill:#ffa500
    style REACT fill:#61dafb
```

## Detailed Component Architecture

### 1. Data Ingestion Layer

```mermaid
graph LR
    subgraph "RTD APIs"
        API1[VehiclePosition.pb]
        API2[TripUpdate.pb]
        API3[Alert.pb]
    end
    
    subgraph "Producer Application"
        FETCH[HTTP Fetcher<br/>60 sec intervals]
        PARSE[Protobuf Deserializer]
        PROD[Kafka Producer]
        
        FETCH --> PARSE
        PARSE --> PROD
    end
    
    subgraph "Kafka Topics"
        T1[rtd.vehicle.positions<br/>Partitions: 3]
        T2[rtd.trip.updates<br/>Partitions: 2]
        T3[rtd.alerts<br/>Partitions: 1]
    end
    
    API1 -->|HTTPS| FETCH
    API2 -->|HTTPS| FETCH
    API3 -->|HTTPS| FETCH
    
    PROD -->|Publish| T1
    PROD -->|Publish| T2
    PROD -->|Publish| T3
    
    style API1 fill:#e0e0e0
    style API2 fill:#e0e0e0
    style API3 fill:#e0e0e0
    style FETCH fill:#28a745
    style PARSE fill:#28a745
    style PROD fill:#28a745
    style T1 fill:#ff6b6b
    style T2 fill:#ff6b6b
    style T3 fill:#ff6b6b
```

### 2. Stream Processing Layer (Flink)

```mermaid
graph TB
    subgraph "Input Topics"
        VP[rtd.vehicle.positions]
        TU[rtd.trip.updates]
        AL[rtd.alerts]
    end
    
    subgraph "Flink Processing"
        subgraph "Vehicle Processor"
            DS[Stream Deserializer]
            DE[Data Enricher]
            DC[Delay Calculator]
            WM[Watermark Assigner<br/>1 min tolerance]
        end
        
        subgraph "Trip Processor"
            SA[Schedule Analyzer]
            CD[Cascade Detector]
            DA[Delay Aggregator]
        end
        
        subgraph "Alert Processor"
            AC[Alert Classifier]
            IA[Impact Analyzer]
            SS[Severity Scorer]
        end
        
        subgraph "Aggregation"
            PM[Performance Metrics<br/>5 min windows]
            VC[Vehicle Counter]
            SF[State Functions]
        end
        
        subgraph "Anomaly Detection"
            PT[Pattern Matcher]
            TH[Threshold Monitor]
            CEP[CEP Engine]
        end
    end
    
    subgraph "Output Topics"
        CR[rtd.comprehensive.routes]
        RS[rtd.route.summary]
        VT[rtd.vehicle.tracking]
    end
    
    VP --> DS
    DS --> DE
    DE --> DC
    DC --> WM
    WM --> CR
    WM --> VT
    
    TU --> SA
    SA --> CD
    CD --> DA
    DA --> RS
    
    AL --> AC
    AC --> IA
    IA --> SS
    SS --> CR
    
    CR --> PM
    PM --> VC
    VC --> SF
    SF --> RS
    
    VT --> PT
    RS --> TH
    PT --> CEP
    TH --> CEP
    
    style VP fill:#ff6b6b
    style TU fill:#ff6b6b
    style AL fill:#ff6b6b
    style CR fill:#28a745
    style RS fill:#28a745
    style VT fill:#28a745
```

### 3. API and Web Layer

```mermaid
graph LR
    subgraph "Kafka Topics"
        CR[rtd.comprehensive.routes]
    end
    
    subgraph "HTTP API Server"
        CON[Kafka Consumer]
        VE[/api/vehicles]
        HE[/api/health]
        JS[JSON Converter]
        CORS[CORS Handler]
        
        CON --> JS
        JS --> VE
        JS --> HE
        CORS --> VE
        CORS --> HE
    end
    
    subgraph "React Application"
        DS[RTD Data Service]
        MAP[OpenStreetMap View]
        SEL[Vehicle Selector]
        TRK[Vehicle Tracker]
        QRY[Query Tools]
        
        DS --> MAP
        DS --> SEL
        DS --> TRK
        DS --> QRY
    end
    
    CR -->|Consume| CON
    VE -->|REST| DS
    HE -->|Health Check| DS
    
    style CR fill:#28a745
    style CON fill:#ffa500
    style VE fill:#ffa500
    style HE fill:#ffa500
    style DS fill:#61dafb
    style MAP fill:#61dafb
```

## Data Flow Stages

### Stage 1: Data Ingestion (Kafka)
- **Frequency**: Every 60 seconds
- **Volume**: ~400+ vehicles per fetch
- **Format**: Protocol Buffer (binary)
- **Topics**: 
  - `rtd.vehicle.positions` - GPS and status data
  - `rtd.trip.updates` - Schedule adherence
  - `rtd.alerts` - Service disruptions

### Stage 2: Stream Processing (Flink)
- **Deserialization**: Protocol Buffers → Java POJOs
- **Enrichment**: Join with static GTFS data
- **Calculations**:
  - Schedule delay computation
  - Route aggregations
  - On-time performance metrics
- **Pattern Detection**:
  - Service anomalies
  - Cascading delays
  - Historical patterns

### Stage 3: Data Distribution (Kafka)
- **Format**: Enriched JSON
- **Topics**:
  - `rtd.comprehensive.routes` - Complete vehicle/route data
  - `rtd.route.summary` - Aggregated statistics
  - `rtd.vehicle.tracking` - Enhanced monitoring data
- **Consumers**:
  - Real-time dashboards
  - Mobile applications
  - Operations monitoring systems
  - Data warehouse (historical analysis)

## Production Kafka Topics

| Topic Name | Purpose | Partitions | Retention | Format |
|------------|---------|------------|-----------|---------|
| `rtd.vehicle.positions` | Raw vehicle GPS data | 3 | 24 hours | Protobuf |
| `rtd.trip.updates` | Schedule adherence info | 2 | 24 hours | Protobuf |
| `rtd.alerts` | Service disruption alerts | 1 | 7 days | Protobuf |
| `rtd.comprehensive.routes` | Enriched vehicle data | 3 | 12 hours | JSON |
| `rtd.route.summary` | Route-level metrics | 1 | 24 hours | JSON |
| `rtd.vehicle.tracking` | Vehicle monitoring data | 2 | 6 hours | JSON |

## Key Architecture Benefits

### Scalability
- **Horizontal Scaling**: Kafka partitions and Flink parallelism
- **Load Distribution**: Multiple brokers and task managers
- **Elastic Processing**: Auto-scaling based on load

### Fault Tolerance
- **Message Durability**: Kafka replication factor of 3
- **Exactly-Once Semantics**: Flink checkpointing
- **Automatic Recovery**: State restoration from checkpoints

### Real-Time Processing
- **Low Latency**: < 1 second end-to-end processing
- **Event Time Processing**: Handles out-of-order data
- **Windowing**: Time-based aggregations and patterns

### Flexibility
- **Decoupled Architecture**: Producers and consumers operate independently
- **Multiple Data Formats**: Support for Protobuf, JSON, Avro
- **Extensible Processing**: Easy to add new stream processors

## Deployment Architecture

```mermaid
graph TB
    subgraph "RTD Data Center"
        subgraph "Kafka Cluster"
            KB1[Kafka Broker 1<br/>8GB RAM]
            KB2[Kafka Broker 2<br/>8GB RAM]
            KB3[Kafka Broker 3<br/>8GB RAM]
        end
        
        subgraph "Flink Cluster"
            JM[Job Manager<br/>4GB RAM]
            TM1[Task Manager 1<br/>8GB RAM]
            TM2[Task Manager 2<br/>8GB RAM]
        end
        
        subgraph "Application Tier"
            PROD[Producer Server<br/>2GB RAM]
            API[API Server<br/>4GB RAM]
            WEB[Web Server<br/>2GB RAM]
        end
        
        subgraph "Data Tier"
            DB[(PostgreSQL<br/>Static GTFS)]
        end
    end
    
    subgraph "External"
        RTD[RTD APIs]
        USERS[End Users]
    end
    
    RTD -->|HTTPS| PROD
    PROD --> KB1
    KB1 <--> KB2
    KB2 <--> KB3
    KB1 --> TM1
    KB2 --> TM2
    TM1 <--> JM
    TM2 <--> JM
    DB --> TM1
    DB --> TM2
    KB3 --> API
    API --> WEB
    WEB --> USERS
    
    style RTD fill:#e0e0e0
    style USERS fill:#e0e0e0
```

## Usage Instructions

### Viewing Structurizr Diagrams

The Structurizr DSL file (`rtd-pipeline-structurizr.dsl`) can be viewed using:

1. **Structurizr Lite** (Local):
   ```bash
   docker run -it --rm -p 8080:8080 \
     -v $(pwd)/architecture:/usr/local/structurizr \
     structurizr/lite
   ```
   Then navigate to http://localhost:8080

2. **Structurizr Online**:
   - Upload the DSL file to https://structurizr.com/

### Viewing PlantUML Diagrams

The PlantUML file (`rtd-pipeline-components.puml`) can be rendered using:

1. **PlantUML Server**:
   - Visit http://www.plantuml.com/plantuml/uml/
   - Paste the content

2. **VS Code Extension**:
   - Install PlantUML extension
   - Open the `.puml` file
   - Use `Alt+D` to preview

3. **Command Line**:
   ```bash
   java -jar plantuml.jar rtd-pipeline-components.puml
   ```

### Viewing Mermaid Diagrams

The Mermaid diagrams in this markdown file can be viewed:

1. **GitHub**: Automatically renders in README files
2. **VS Code**: Install Markdown Preview Mermaid Support
3. **Online**: Use https://mermaid.live/

## Architecture Decision Records

### ADR-001: Kafka as Message Bus
**Decision**: Use Apache Kafka 4.0.0 for message queuing
**Rationale**: 
- High throughput for real-time data
- Built-in partitioning for scalability
- Durable message storage
- Strong ecosystem support

### ADR-002: Flink for Stream Processing
**Decision**: Use Apache Flink 1.19.1 for stream processing
**Rationale**:
- True stream processing (not micro-batching)
- Exactly-once semantics
- Rich windowing functions
- Complex event processing capabilities

### ADR-003: Protocol Buffers for Data Format
**Decision**: Use Protocol Buffers for GTFS-RT data
**Rationale**:
- Industry standard for transit data
- Efficient binary serialization
- Strong typing and schema evolution
- Native support in RTD APIs

### ADR-004: React for Web Frontend
**Decision**: Use React with TypeScript for web application
**Rationale**:
- Component-based architecture
- Strong typing with TypeScript
- Rich ecosystem for mapping (Leaflet)
- Real-time data update capabilities

## Performance Metrics

- **Data Ingestion Rate**: ~400 vehicles/minute
- **Processing Latency**: < 1 second end-to-end
- **API Response Time**: < 100ms for vehicle queries
- **Dashboard Update Rate**: 30-second intervals
- **System Availability**: 99.9% uptime target

This architecture provides a robust, scalable foundation for real-time transit data processing, capable of handling the demands of modern public transportation systems.