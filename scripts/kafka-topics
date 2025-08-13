#!/bin/bash

# Kafka Topics Wrapper for RTD GTFS-RT Pipeline  
# This script provides kafka-topics functionality without requiring separate Kafka installation

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
    echo -e "${BLUE}RTD Kafka Topics Manager (v4.0)${NC}"
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
    echo "Usage: ./kafka-topics [OPTIONS]"
    echo
    echo "Common Commands:"
    echo "  --list                              - List all topics"
    echo "  --create --topic <name>             - Create a new topic"
    echo "  --delete --topic <name>             - Delete a topic"
    echo "  --describe --topic <name>           - Describe topic details"
    echo "  --alter --topic <name>              - Alter topic configuration"
    echo
    echo "Options:"
    echo "  --bootstrap-server <server>         - Kafka bootstrap servers (default: localhost:9092)"
    echo "  --topic <topic>                     - Topic name"
    echo "  --partitions <num>                  - Number of partitions (for create)"
    echo "  --replication-factor <num>          - Replication factor (for create)"
    echo "  --config <key=value>                - Topic configuration"
    echo
    echo "RTD Topic Creation Commands:"
    echo "  # Create all RTD topics at once"
    echo "  ./kafka-topics --create-rtd-topics"
    echo
    echo "  # Create individual topics"
    echo "  ./kafka-topics --create --topic rtd.comprehensive.routes --partitions 3"
    echo "  ./kafka-topics --create --topic rtd.route.summary --partitions 1"
    echo "  ./kafka-topics --create --topic rtd.vehicle.tracking --partitions 2"
    echo "  ./kafka-topics --create --topic rtd.vehicle.positions --partitions 2" 
    echo "  ./kafka-topics --create --topic rtd.trip.updates --partitions 2"
    echo "  ./kafka-topics --create --topic rtd.alerts --partitions 1"
    echo "  ./kafka-topics --create --topic rtd.rail.comm --partitions 2"
    echo
    echo "Examples:"
    echo "  # List all topics"
    echo "  ./kafka-topics --list"
    echo
    echo "  # Describe RTD topics"
    echo "  ./kafka-topics --describe --topic rtd.comprehensive.routes"
    echo
    echo "  # Create topic with custom settings"
    echo "  ./kafka-topics --create --topic rtd.test --partitions 1 --replication-factor 1"
}

# Create all RTD topics
create_rtd_topics() {
    print_status "Creating all RTD GTFS-RT topics..."
    
    java -cp "target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar:$CLASSPATH" \
         com.rtd.pipeline.tools.KafkaTopicsManager \
         --create-rtd-topics \
         --bootstrap-server localhost:9092
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
    
    # Handle special RTD topics creation command
    if [ "$1" = "--create-rtd-topics" ]; then
        print_header
        echo
        create_rtd_topics
        exit 0
    fi
    
    print_header
    echo
    print_status "Running Kafka topics command..."
    echo
    
    # Run Kafka topics command with all provided arguments
    # Default bootstrap server is handled in the Java class
    java -cp "target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar:$CLASSPATH" \
         com.rtd.pipeline.tools.KafkaTopicsManager \
         "$@"
}

# Handle Ctrl+C gracefully  
trap 'echo -e "\n${YELLOW}Command interrupted by user${NC}"; exit 130' INT

# Run main function with all arguments
main "$@"