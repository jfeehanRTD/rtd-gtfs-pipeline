#!/bin/bash

# Test script for Direct Kafka Bridge
# Tests connectivity, message publishing, and performance

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Direct Kafka Bridge Test]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_info() {
    echo -e "${CYAN}ℹ️${NC} $1"
}

# Configuration
BRIDGE_PORT=8083
BRIDGE_HOST="localhost"
BRIDGE_URL="http://${BRIDGE_HOST}:${BRIDGE_PORT}"
RAIL_COMM_ENDPOINT="${BRIDGE_URL}/rail-comm"
KAFKA_ENDPOINT="${BRIDGE_URL}/kafka/rtd.rail.comm"
HEALTH_ENDPOINT="${BRIDGE_URL}/health"
METRICS_ENDPOINT="${BRIDGE_URL}/metrics"

# Test data
TEST_JSON='{"test": true, "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'", "message": "Direct Kafka Bridge test"}'

# Function to check if service is running
check_service() {
    local service_name="$1"
    local check_command="$2"
    
    print_status "Checking if $service_name is running..."
    if eval "$check_command" >/dev/null 2>&1; then
        print_success "$service_name is running"
        return 0
    else
        print_error "$service_name is not running"
        return 1
    fi
}

# Function to test endpoint connectivity
test_endpoint() {
    local endpoint="$1"
    local name="$2"
    local method="${3:-GET}"
    local data="${4:-}"
    
    print_status "Testing $name endpoint: $endpoint"
    
    local curl_cmd="curl -s --connect-timeout 5"
    if [[ "$method" == "POST" ]]; then
        curl_cmd="$curl_cmd -X POST -H 'Content-Type: application/json'"
        if [[ -n "$data" ]]; then
            curl_cmd="$curl_cmd -d '$data'"
        fi
    fi
    
    local response
    if response=$(eval "$curl_cmd '$endpoint'" 2>/dev/null); then
        print_success "$name endpoint is reachable"
        print_info "Response: $response"
        return 0
    else
        print_error "$name endpoint is not reachable"
        return 1
    fi
}

# Function to test Kafka connectivity
test_kafka() {
    print_status "Testing Kafka connectivity..."
    
    if command -v kafka-topics >/dev/null 2>&1; then
        if kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1; then
            print_success "Kafka is accessible"
            return 0
        else
            print_error "Kafka is not accessible"
            return 1
        fi
    else
        # Try using the built-in script
        if ./scripts/kafka-topics --list >/dev/null 2>&1; then
            print_success "Kafka is accessible (via built-in script)"
            return 0
        else
            print_error "Kafka is not accessible"
            return 1
        fi
    fi
}

# Function to monitor Kafka topic
monitor_topic() {
    local topic="$1"
    local duration="${2:-10}"
    
    print_status "Monitoring topic '$topic' for $duration seconds..."
    
    # Start monitoring in background
    if command -v kafka-console-consumer >/dev/null 2>&1; then
        timeout $duration kafka-console-consumer \
            --bootstrap-server localhost:9092 \
            --topic "$topic" \
            --from-beginning \
            --max-messages 5 &
    else
        timeout $duration ./scripts/kafka-console-consumer \
            --topic "$topic" \
            --from-beginning \
            --max-messages 5 &
    fi
    
    local monitor_pid=$!
    
    # Wait a moment for consumer to start
    sleep 2
    
    # Send test message
    print_status "Sending test message to $topic..."
    curl -s -X POST -H "Content-Type: application/json" \
        -d "$TEST_JSON" "$KAFKA_ENDPOINT" >/dev/null
    
    # Wait for monitoring to complete
    wait $monitor_pid 2>/dev/null || true
    
    print_success "Topic monitoring completed"
}

# Function to benchmark performance
benchmark_performance() {
    local endpoint="$1"
    local name="$2"
    local iterations=10
    
    print_status "Benchmarking $name performance ($iterations requests)..."
    
    local total_time=0
    local success_count=0
    
    for i in $(seq 1 $iterations); do
        local start_time=$(date +%s%N)
        
        if curl -s -X POST -H "Content-Type: application/json" \
            -d "$TEST_JSON" "$endpoint" >/dev/null 2>&1; then
            local end_time=$(date +%s%N)
            local duration=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds
            total_time=$((total_time + duration))
            success_count=$((success_count + 1))
            print_info "Request $i: ${duration}ms"
        else
            print_warning "Request $i: failed"
        fi
        
        # Small delay between requests
        sleep 0.1
    done
    
    if [[ $success_count -gt 0 ]]; then
        local avg_time=$((total_time / success_count))
        print_success "$name average response time: ${avg_time}ms"
        print_success "$name success rate: ${success_count}/${iterations}"
    else
        print_error "$name: all requests failed"
    fi
}

# Main test function
run_tests() {
    print_status "Starting Direct Kafka Bridge tests..."
    echo
    
    # Check prerequisites
    print_status "Checking prerequisites..."
    check_service "Kafka" "test_kafka" || {
        print_error "Kafka is required but not running. Start with: ./rtd-control.sh docker start"
        exit 1
    }
    
    check_service "Direct Kafka Bridge" "curl -s --connect-timeout 2 '$HEALTH_ENDPOINT' >/dev/null" || {
        print_error "Direct Kafka Bridge is required but not running. Start with: ./rtd-control.sh rail-comm bridge"
        exit 1
    }
    echo
    
    # Test endpoints
    print_status "Testing endpoints..."
    test_endpoint "$HEALTH_ENDPOINT" "Health Check"
    test_endpoint "$METRICS_ENDPOINT" "Metrics"
    test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm Compatibility" "GET"
    test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka" "GET"
    echo
    
    # Test message publishing
    print_status "Testing message publishing..."
    test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm POST" "POST" "$TEST_JSON"
    test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka POST" "POST" "$TEST_JSON"
    echo
    
    # Monitor topic for messages
    print_status "Monitoring Kafka topic for test messages..."
    monitor_topic "rtd.rail.comm" 15
    echo
    
    # Benchmark performance
    print_status "Benchmarking performance..."
    benchmark_performance "$RAIL_COMM_ENDPOINT" "Rail Comm Compatibility"
    benchmark_performance "$KAFKA_ENDPOINT" "Direct Kafka"
    echo
    
    print_success "All Direct Kafka Bridge tests completed successfully!"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  test        - Run all tests (default)"
    echo "  health      - Test health endpoint only"
    echo "  endpoints   - Test all endpoints"
    echo "  publish     - Test message publishing"
    echo "  monitor     - Monitor Kafka topic"
    echo "  benchmark   - Benchmark performance"
    echo "  help        - Show this help"
    echo
    echo "Examples:"
    echo "  $0                    # Run all tests"
    echo "  $0 health            # Test health only"
    echo "  $0 benchmark         # Run performance benchmarks"
}

# Main script logic
case "${1:-test}" in
    "test")
        run_tests
        ;;
    "health")
        test_endpoint "$HEALTH_ENDPOINT" "Health Check"
        ;;
    "endpoints")
        test_endpoint "$HEALTH_ENDPOINT" "Health Check"
        test_endpoint "$METRICS_ENDPOINT" "Metrics"
        test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm Compatibility" "GET"
        test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka" "GET"
        ;;
    "publish")
        test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm POST" "POST" "$TEST_JSON"
        test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka POST" "POST" "$TEST_JSON"
        ;;
    "monitor")
        monitor_topic "rtd.rail.comm" 30
        ;;
    "benchmark")
        benchmark_performance "$RAIL_COMM_ENDPOINT" "Rail Comm Compatibility"
        benchmark_performance "$KAFKA_ENDPOINT" "Direct Kafka"
        ;;
    "help"|"-h"|"--help")
        show_usage
        ;;
    *)
        print_error "Unknown command: $1"
        show_usage
        exit 1
        ;;
esac
