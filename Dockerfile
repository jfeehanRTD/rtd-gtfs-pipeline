# Multi-stage Docker build for RTD GTFS Pipeline
# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy Maven dependencies file first (better caching)
COPY pom.xml .

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-alpine

# Install required packages
RUN apk add --no-cache \
    bash \
    curl \
    jq \
    tini

# Create application user
RUN addgroup -S rtdapp && adduser -S rtdapp -G rtdapp

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /app/target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar app.jar

# Copy scripts
COPY scripts/ /app/scripts/
RUN chmod +x /app/scripts/*.sh

# Create data directories
RUN mkdir -p /app/data /app/logs /app/output && \
    chown -R rtdapp:rtdapp /app

# Switch to non-root user
USER rtdapp

# Environment variables with defaults
ENV JAVA_OPTS="-Xms512m -Xmx2g" \
    TIS_PROXY_HOST="http://tisproxy.rtd-denver.com" \
    TIS_PROXY_SERVICE="siri" \
    TIS_PROXY_TTL="90000" \
    RAILCOMM_SERVICE="railcomm" \
    RAILCOMM_TTL="90000" \
    LRGPS_SERVICE="lrgps" \
    LRGPS_TTL="90000" \
    KAFKA_BOOTSTRAP_SERVERS="localhost:9092" \
    BUS_COMM_TOPIC="rtd.bus.siri" \
    RAIL_COMM_TOPIC="rtd.rail.comm" \
    LRGPS_TOPIC="rtd.lrgps"

# Expose ports
EXPOSE 8080 8081 8082 8083 9092

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8082/health || exit 1

# Use tini for proper signal handling
ENTRYPOINT ["/sbin/tini", "--"]

# Default command (can be overridden)
CMD ["java", "-jar", "app.jar", "com.rtd.pipeline.RTDStaticDataPipeline"]