#!/bin/bash

# Direct Kafka Bridge Deployment Script
# Automates the deployment and testing of the Direct Kafka Bridge

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Direct Kafka Bridge Deployment]${NC} $1"
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
KAFKA_PORT=9092
HEALTH_ENDPOINT="http://localhost:${BRIDGE_PORT}/health"

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

# Function to wait for service to be ready
wait_for_service() {
    local service_name="$1"
    local check_command="$2"
    local max_attempts=30
    local attempt=1
    
    print_status "Waiting for $service_name to be ready..."
    
    while [[ $attempt -le $max_attempts ]]; do
        if eval "$check_command" >/dev/null 2>&1; then
            print_success "$service_name is ready"
            return 0
        fi
        
        print_info "Attempt $attempt/$max_attempts - waiting 2 seconds..."
        sleep 2
        attempt=$((attempt + 1))
    done
    
    print_error "$service_name failed to start within $((max_attempts * 2)) seconds"
    return 1
}

# Function to start Kafka if not running
start_kafka() {
    print_status "Starting Kafka cluster..."
    
    if check_service "Kafka" "./scripts/kafka-topics.sh --list >/dev/null 2>&1"; then
        print_info "Kafka is already running"
        return 0
    fi
    
    print_info "Starting Kafka with Docker..."
    ./rtd-control.sh docker start
    
    # Wait for Kafka to be ready
    wait_for_service "Kafka" "./scripts/kafka-topics.sh --list >/dev/null 2>&1"
}

# Function to create Kafka topics
create_topics() {
    print_status "Creating Kafka topics..."
    
    # Create rail comm topic if it doesn't exist
    if ! ./scripts/kafka-topics.sh --list | grep -q "rtd.rail.comm"; then
        print_info "Creating rtd.rail.comm topic..."
        ./scripts/kafka-topics.sh --create --topic rtd.rail.comm --partitions 2 --replication-factor 1
        print_success "Created rtd.rail.comm topic"
    else
        print_info "rtd.rail.comm topic already exists"
    fi
    
    # List all topics
    print_info "Available Kafka topics:"
    ./scripts/kafka-topics.sh --list
}

# Function to start Direct Kafka Bridge
start_bridge() {
    print_status "Starting Direct Kafka Bridge..."
    
    if check_service "Direct Kafka Bridge" "curl -s --connect-timeout 2 '$HEALTH_ENDPOINT' >/dev/null 2>&1"; then
        print_warning "Direct Kafka Bridge is already running"
        return 0
    fi
    
    print_info "Starting Direct Kafka Bridge in background..."
    ./rtd-control.sh rail-comm bridge &
    local bridge_pid=$!
    
    # Wait for bridge to be ready
    wait_for_service "Direct Kafka Bridge" "curl -s --connect-timeout 2 '$HEALTH_ENDPOINT' >/dev/null 2>&1"
    
    print_success "Direct Kafka Bridge started (PID: $bridge_pid)"
}

# Function to test the bridge
test_bridge() {
    print_status "Testing Direct Kafka Bridge..."
    
    # Run comprehensive tests
    if ./scripts/test-direct-kafka-bridge.sh; then
        print_success "All bridge tests passed"
        return 0
    else
        print_error "Bridge tests failed"
        return 1
    fi
}

# Function to show deployment status
show_status() {
    print_status "Deployment Status:"
    echo
    
    # Check Kafka
    if check_service "Kafka" "./scripts/kafka-topics.sh --list >/dev/null 2>&1"; then
        print_success "Kafka: Running"
        print_info "  Topics: $(./scripts/kafka-topics.sh --list | wc -l) topics available"
    else
        print_error "Kafka: Not running"
    fi
    
    # Check Direct Kafka Bridge
    if check_service "Direct Kafka Bridge" "curl -s --connect-timeout 2 '$HEALTH_ENDPOINT' >/dev/null 2>&1"; then
        print_success "Direct Kafka Bridge: Running"
        print_info "  Health: $(curl -s $HEALTH_ENDPOINT | jq -r '.status' 2>/dev/null || echo 'unknown')"
        print_info "  Endpoint: http://localhost:$BRIDGE_PORT"
    else
        print_error "Direct Kafka Bridge: Not running"
    fi
    
    echo
    print_info "Available endpoints:"
    print_info "  Health Check: http://localhost:$BRIDGE_PORT/health"
    print_info "  Metrics: http://localhost:$BRIDGE_PORT/metrics"
    print_info "  Rail Comm: http://localhost:$BRIDGE_PORT/rail-comm"
    print_info "  Direct Kafka: http://localhost:$BRIDGE_PORT/kafka/rtd.rail.comm"
}

# Function to stop the bridge
stop_bridge() {
    print_status "Stopping Direct Kafka Bridge..."
    
    local bridge_pids=$(pgrep -f "DirectKafkaBridge" 2>/dev/null || true)
    if [[ -n "$bridge_pids" ]]; then
        echo "$bridge_pids" | xargs kill -TERM
        print_success "Direct Kafka Bridge stopped"
    else
        print_warning "Direct Kafka Bridge is not running"
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  deploy      - Full deployment (Kafka + Bridge + Testing)"
    echo "  start       - Start Direct Kafka Bridge only"
    echo "  stop        - Stop Direct Kafka Bridge"
    echo "  test        - Test the bridge functionality"
    echo "  status      - Show deployment status"
    echo "  kafka       - Start Kafka cluster only"
    echo "  topics      - Create Kafka topics"
    echo "  help        - Show this help"
    echo
    echo "Examples:"
    echo "  $0 deploy   # Complete deployment"
    echo "  $0 start    # Start bridge only"
    echo "  $0 test     # Test bridge functionality"
    echo "  $0 status   # Check deployment status"
}

# Main deployment function
deploy() {
    print_status "Starting Direct Kafka Bridge deployment..."
    echo
    
    # Step 1: Start Kafka
    start_kafka
    
    # Step 2: Create topics
    create_topics
    
    # Step 3: Start bridge
    start_bridge
    
    # Step 4: Test bridge
    test_bridge
    
    echo
    print_success "Direct Kafka Bridge deployment completed successfully!"
    echo
    show_status
}

# Main script logic
case "${1:-deploy}" in
    "deploy")
        deploy
        ;;
    "start")
        start_bridge
        show_status
        ;;
    "stop")
        stop_bridge
        ;;
    "test")
        test_bridge
        ;;
    "status")
        show_status
        ;;
    "kafka")
        start_kafka
        ;;
    "topics")
        create_topics
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
