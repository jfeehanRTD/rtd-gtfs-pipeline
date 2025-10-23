#!/bin/bash

# RTD GTFS-RT Data Sink Query Client Runner
# Usage: ./rtd-query <command> [options]

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

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}RTD GTFS-RT Query Client${NC}"
    echo -e "${BLUE}================================${NC}"
}

# Check if project is built
check_build() {
    if [ ! -f "$PROJECT_DIR/target/transitstream-1.0-SNAPSHOT.jar" ]; then
        print_warning "Project not built. Building now..."
        cd "$PROJECT_DIR"
        mvn clean package -DskipTests > /dev/null 2>&1
        if [ $? -ne 0 ]; then
            print_error "Failed to build project. Please run 'mvn clean package' manually."
            exit 1
        fi
        print_status "Project built successfully"
    fi
}

# Check if Kafka is running
check_kafka() {
    print_status "Checking Kafka connectivity..."
    
    # Simple check - try to list topics (requires kafka tools to be installed)
    if command -v kafka-topics.sh > /dev/null; then
        timeout 5 kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1
        if [ $? -eq 0 ]; then
            print_status "Kafka is running and accessible"
        else
            print_warning "Kafka may not be running or accessible on localhost:9092"
            print_warning "Make sure Kafka is started before running queries"
        fi
    else
        print_warning "Kafka tools not found in PATH. Skipping connectivity check."
    fi
}

# Show available Kafka topics related to RTD
show_topics() {
    print_status "Checking RTD-related Kafka topics..."
    
    if command -v kafka-topics.sh > /dev/null; then
        topics=$(timeout 5 kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null | grep -E "rtd\.|vehicle|trip|alert" || true)
        if [ -n "$topics" ]; then
            echo "Available RTD topics:"
            echo "$topics" | while read topic; do
                echo "  - $topic"
            done
        else
            print_warning "No RTD-related topics found. Make sure the pipeline is running."
        fi
    fi
}

# Quick health check
health_check() {
    print_header
    print_status "Running RTD Pipeline Health Check..."
    echo
    
    check_build
    check_kafka
    show_topics
    
    echo
    print_status "Health check complete. Use './rtd-query list' to see available tables."
}

# Main execution
main() {
    # Set working directory to project root
    cd "$PROJECT_DIR"
    
    # Check for help or no arguments
    if [ $# -eq 0 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ] || [ "$1" = "help" ]; then
        CLASSPATH=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout 2>/dev/null)
        java -cp "target/transitstream-1.0-SNAPSHOT.jar:$CLASSPATH" com.rtd.pipeline.client.SimpleRTDQueryClient
        exit 0
    fi
    
    # Special commands
    case "$1" in
        "health")
            health_check
            exit 0
            ;;
        "build")
            print_status "Building project..."
            mvn clean package -DskipTests
            exit $?
            ;;
        "topics")
            show_topics
            exit 0
            ;;
    esac
    
    # Check if project is built
    check_build
    
    # Run the query client
    print_header
    echo
    
    # Get the Maven classpath
    CLASSPATH=$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout 2>/dev/null)
    
    # Use the simple client that doesn't require Flink runtime
    java -cp "target/transitstream-1.0-SNAPSHOT.jar:$CLASSPATH" \
         -Djava.util.logging.config.file=src/main/resources/logging.properties \
         com.rtd.pipeline.client.SimpleRTDQueryClient "$@"
}

# Handle Ctrl+C gracefully
trap 'echo -e "\n${YELLOW}Query interrupted by user${NC}"; exit 130' INT

# Run main function with all arguments
main "$@"