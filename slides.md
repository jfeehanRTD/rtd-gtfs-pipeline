---
theme: default
background: https://source.unsplash.com/1920x1080/?transit,data
class: text-center
highlighter: shiki
lineNumbers: false
info: |
  ## RTD GTFS-RT Data Pipeline
  Real-time transit data processing with Apache Flink
drawings:
  persist: false
transition: slide-left
title: RTD GTFS-RT Data Pipeline
mdc: true
---

# RTD GTFS-RT Data Pipeline

Real-time Transit Data Processing with Apache Flink

<div class="pt-12">
  <span @click="$slidev.nav.next" class="px-2 py-1 rounded cursor-pointer" hover="bg-white bg-opacity-10">
    Press Space for next page <carbon:arrow-right class="inline"/>
  </span>
</div>

---
transition: fade-out
---

# What is RTD GTFS-RT Pipeline?

A Java application built with Apache Flink that processes real-time transit feeds from RTD Denver

<v-clicks>

- **Real-time Processing** - Downloads and processes GTFS-RT feeds every hour
- **Apache Flink** - Distributed stream processing framework
- **Three Feed Types** - Vehicle Positions, Trip Updates, and Alerts
- **Scalable Architecture** - Can run locally or on Flink clusters

</v-clicks>

<br>
<br>

<v-click>

## Key Technologies
- Java 24
- Apache Flink 1.18.0
- Maven 3.6+
- Protocol Buffers (GTFS-RT)

</v-click>

---
layout: image-right
image: https://source.unsplash.com/800x600/?architecture,data
---

# Architecture Overview

<v-clicks>

## High-Level Components

- **RTDGTFSPipeline** - Main orchestration class
- **GTFSRealtimeSource** - Custom source for hourly downloads
- **Data Models** - VehiclePosition, TripUpdate, Alert
- **Table API Sinks** - Configurable output destinations

## Data Flow

1. **Source** - HTTP requests to RTD endpoints
2. **Parse** - Protobuf deserialization
3. **Transform** - Convert to structured models
4. **Sink** - Output via Table API

</v-clicks>

---
transition: slide-up
---

# Feed URLs

The pipeline processes three GTFS-RT feed types from RTD Denver:

<v-clicks>

## Vehicle Positions
```
https://www.rtd-denver.com/google_sync/VehiclePosition.pb
```
Real-time vehicle location data

## Trip Updates  
```
https://www.rtd-denver.com/google_sync/TripUpdate.pb
```
Schedule deviations and predictions

## Alerts
```
https://www.rtd-denver.com/google_sync/Alert.pb
```
Service disruptions and notifications

</v-clicks>

---
layout: two-cols
---

# Prerequisites

Before running the application, ensure you have:

<v-clicks>

- **Java 24** installed
- **Maven 3.6+** for build management
- **Apache Flink 1.18.0** (for cluster deployment)

</v-clicks>

::right::

<v-click>

## Verify Installation

```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Check Flink (if using cluster)
flink --version
```

</v-click>

---

# Build Commands

Essential Maven commands for building the project:

<v-clicks>

## Clean and Compile
```bash
mvn clean compile
```

## Package Application
```bash
# Full package with tests
mvn clean package

# Skip tests for faster build
mvn clean package -DskipTests
```

## Development Build
```bash
# Just compile for development
mvn compile
```

</v-clicks>

---
transition: slide-left
---

# Running Tests

Multiple ways to execute the test suite:

<v-clicks>

## Run All Tests
```bash
mvn test
```

## Run Specific Test Class
```bash
mvn test -Dtest=YourTestClassName
```

## Run Tests with Coverage
```bash
mvn clean test jacoco:report
```

## Skip Tests During Build
```bash
mvn clean package -DskipTests
```

</v-clicks>

<v-click>

<div class="bg-blue-500 bg-opacity-20 p-4 rounded-lg mt-4">
💡 <strong>Tip:</strong> Always run tests after making code changes to ensure functionality remains intact
</div>

</v-click>

---
layout: center
class: text-center
---

# Running the Application

Two deployment modes available

---

# Local Development Mode

Run with Flink Mini Cluster for development and testing:

<v-clicks>

## Using Maven (Recommended)
```bash
mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSPipeline"
```
*Includes all dependencies automatically*

## Using Packaged JAR
```bash
# First package the application
mvn clean package

# Then run the JAR
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.RTDGTFSPipeline
```

</v-clicks>

<v-click>

<div class="bg-green-500 bg-opacity-20 p-4 rounded-lg mt-4">
✅ <strong>Local Mode:</strong> Perfect for development, debugging, and testing
</div>

</v-click>

---

# Flink Cluster Deployment

Deploy to production Flink cluster:

<v-click>

## Submit Job to Cluster
```bash
# Package first
mvn clean package

# Submit to running Flink cluster
flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

</v-click>

<v-click>

## Cluster Management
```bash
# Check job status
flink list

# Cancel a job
flink cancel <job-id>

# Stop a job with savepoint
flink stop <job-id>
```

</v-click>

<v-click>

<div class="bg-yellow-500 bg-opacity-20 p-4 rounded-lg mt-4">
⚠️ <strong>Production:</strong> Requires a running Flink cluster (standalone or YARN/K8s)
</div>

</v-click>

---
layout: center
class: text-center
---

# Docker Setup

Containerizing the RTD GTFS-RT Pipeline

---

# Creating Dockerfile

First, let's create a Dockerfile for the application:

<v-click>

```dockerfile
# Multi-stage build for smaller final image
FROM maven:3.9-openjdk-24-slim AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:24-slim
WORKDIR /app
COPY --from=builder /app/target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar .
EXPOSE 8081
CMD ["java", "-cp", "rtd-gtfs-pipeline-1.0-SNAPSHOT.jar", \
     "com.rtd.pipeline.RTDGTFSPipeline"]
```

</v-click>

---

# Docker Commands

Building and running the containerized application:

<v-clicks>

## Build Docker Image
```bash
docker build -t rtd-gtfs-pipeline .
```

## Run Container (Local Mode)
```bash
docker run --rm -p 8081:8081 rtd-gtfs-pipeline
```

## Run with Environment Variables
```bash
docker run --rm -p 8081:8081 \
  -e FLINK_JOB_PARALLELISM=2 \
  rtd-gtfs-pipeline
```

## Run in Background
```bash
docker run -d --name rtd-pipeline \
  -p 8081:8081 rtd-gtfs-pipeline
```

</v-clicks>

---

# Docker Compose Setup

For more complex deployments with external dependencies:

<v-click>

```yaml
version: '3.8'
services:
  rtd-pipeline:
    build: .
    ports:
      - "8081:8081"
    environment:
      - FLINK_JOB_PARALLELISM=2
    depends_on:
      - kafka
      
  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
```

</v-click>

<v-click>

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f rtd-pipeline
```

</v-click>

---

# Docker Testing

Running tests within Docker containers:

<v-clicks>

## Test-Only Container
```bash
# Build test image
docker build --target builder -t rtd-pipeline-test .

# Run tests
docker run --rm rtd-pipeline-test mvn test
```

## Integration Testing
```bash
# Run with test profile
docker run --rm -e SPRING_PROFILES_ACTIVE=test \
  rtd-pipeline-test mvn test
```

## Volume Mount for Development
```bash
# Mount source code for live development
docker run --rm -v $(pwd):/app -w /app \
  maven:3.9-openjdk-24-slim mvn test
```

</v-clicks>

---
layout: two-cols
---

# Monitoring & Debugging

<v-clicks>

## Flink Web UI (Local)
- Access at `http://localhost:8081`
- Monitor job metrics
- View task managers

## Docker Logs
```bash
# View container logs
docker logs rtd-pipeline

# Follow logs in real-time  
docker logs -f rtd-pipeline
```

</v-clicks>

::right::

<v-click>

## Health Checks

```bash
# Check container status
docker ps

# Execute commands in container
docker exec -it rtd-pipeline bash

# Check Java processes
docker exec rtd-pipeline jps -l
```

</v-click>

<v-click>

## Troubleshooting

- Check memory allocation
- Verify network connectivity
- Monitor resource usage

</v-click>

---
layout: center
class: text-center
---

# Development Workflow

<v-click>

## Local Development
```bash
mvn clean compile → mvn test → mvn exec:java
```

</v-click>

<v-click>

## Docker Development  
```bash
docker build → docker run → docker logs
```

</v-click>

<v-click>

## Production Deployment
```bash
mvn clean package → flink run target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar
```

</v-click>

---
layout: center
class: text-center
---

# Questions & Next Steps

<v-clicks>

## Key Features Covered
- ✅ Project architecture and components
- ✅ Local development with Maven
- ✅ Testing strategies and commands
- ✅ Docker containerization
- ✅ Flink cluster deployment

## What's Next?
- Configure output sinks (Kafka, databases)
- Set up monitoring and alerting  
- Implement CI/CD pipelines
- Scale for production workloads

</v-clicks>

<div class="pt-12">
  <span class="px-2 py-1 rounded cursor-pointer" hover="bg-white bg-opacity-10">
    Thank you! 🚀
  </span>
</div>