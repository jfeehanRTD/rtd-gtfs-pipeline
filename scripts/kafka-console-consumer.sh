#!/bin/bash

# Kafka Console Consumer Wrapper for RTD GTFS-RT Pipeline
# This script provides kafka-console-consumer functionality without requiring separate Kafka installation

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}RTD Kafka Console Consumer (v4.0)${NC}"
    echo -e "${BLUE}================================${NC}"
}

# Check if project is built
check_build() {
    if [ ! -f "$PROJECT_DIR/target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar" ]; then
        print_status "Project not built. Building now..."
        cd "$PROJECT_DIR"
        mvn clean package -DskipTests > /dev/null 2>&1
        if [ $? -ne 0 ]; then
            print_error "Failed to build project. Please run 'mvn clean package' manually."
            exit 1
        fi
        print_status "Project built successfully"
    fi
}

# Show usage information
show_usage() {
    print_header
    echo
    echo "Usage: ./kafka-console-consumer [OPTIONS]"
    echo
    echo "Common RTD Topics:"
    echo "  --topic rtd.comprehensive.routes    - Complete route and vehicle data"
    echo "  --topic rtd.route.summary           - Route performance statistics" 
    echo "  --topic rtd.vehicle.tracking        - Enhanced vehicle tracking"
    echo "  --topic rtd.vehicle.positions       - Raw vehicle positions"
    echo "  --topic rtd.trip.updates            - Trip delays and updates"
    echo "  --topic rtd.alerts                  - Service alerts"
    echo
    echo "Options:"
    echo "  --bootstrap-server <server>         - Kafka bootstrap servers (default: localhost:9092)"
    echo "  --topic <topic>                     - Topic to consume from (required)"
    echo "  --from-beginning                    - Read from beginning of topic"
    echo "  --max-messages <num>                - Maximum messages to consume"
    echo "  --timeout-ms <ms>                   - Consumer timeout in milliseconds"
    echo "  --group <group-id>                  - Consumer group ID"
    echo "  --property <key=value>              - Additional consumer properties"
    echo
    echo "Examples:"
    echo "  # Monitor comprehensive routes data from beginning"
    echo "  ./kafka-console-consumer --topic rtd.comprehensive.routes --from-beginning"
    echo
    echo "  # Monitor alerts with custom server"
    echo "  ./kafka-console-consumer --bootstrap-server kafka:9092 --topic rtd.alerts"
    echo
    echo "  # Limit to 100 messages"
    echo "  ./kafka-console-consumer --topic rtd.route.summary --max-messages 100"
    echo
    echo "Quick Start Commands:"
    echo "  ./kafka-console-consumer --topic rtd.comprehensive.routes --from-beginning --max-messages 10"
    echo "  ./kafka-console-consumer --topic rtd.route.summary --from-beginning"
    echo "  ./kafka-console-consumer --topic rtd.alerts --from-beginning"
}

# Main execution
main() {
    cd "$PROJECT_DIR"
    
    # Check for help or no arguments
    if [ $# -eq 0 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ] || [ "$1" = "help" ]; then
        show_usage
        exit 0
    fi
    
    # Check if project is built
    check_build
    
    # Get the Maven classpath
    CLASSPATH=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout 2>/dev/null)
    
    print_header
    echo
    print_status "Starting Kafka console consumer..."
    echo
    
    # Run Kafka console consumer with all provided arguments
    java -cp "target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar:$CLASSPATH" \
         com.rtd.pipeline.tools.KafkaConsumerManager "$@"
}

# Handle Ctrl+C gracefully
trap 'echo -e "\n${YELLOW}Consumer interrupted by user${NC}"; exit 130' INT

# Run main function with all arguments
main "$@"