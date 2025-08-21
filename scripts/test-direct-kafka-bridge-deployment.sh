#!/bin/bash

# Direct Kafka Bridge Deployment Test Script
# Comprehensive testing of the Direct Kafka Bridge deployment process

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Deployment Test]${NC} $1"
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

print_step() {
    echo -e "${PURPLE}🔧${NC} $1"
}

# Configuration
BRIDGE_PORT=8083
KAFKA_PORT=9092
HEALTH_ENDPOINT="http://localhost:${BRIDGE_PORT}/health"
RAIL_COMM_ENDPOINT="http://localhost:${BRIDGE_PORT}/rail-comm"
KAFKA_ENDPOINT="http://localhost:${BRIDGE_PORT}/kafka/rtd.rail.comm"
METRICS_ENDPOINT="http://localhost:${BRIDGE_PORT}/metrics"

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to increment test counters
increment_test() {
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
}

test_passed() {
    PASSED_TESTS=$((PASSED_TESTS + 1))
    print_success "$1"
}

test_failed() {
    FAILED_TESTS=$((FAILED_TESTS + 1))
    print_error "$1"
}

# Function to check if service is running
check_service() {
    local service_name="$1"
    local check_command="$2"
    
    increment_test
    if eval "$check_command" >/dev/null 2>&1; then
        test_passed "$service_name is running"
        return 0
    else
        test_failed "$service_name is not running"
        return 1
    fi
}

# Function to test endpoint connectivity
test_endpoint() {
    local endpoint="$1"
    local name="$2"
    local method="${3:-GET}"
    local data="${4:-}"
    local expected_status="${5:-200}"
    
    increment_test
    print_step "Testing $name endpoint: $endpoint"
    
    local curl_cmd="curl -s --connect-timeout 5 -w '%{http_code}'"
    if [[ "$method" == "POST" ]]; then
        curl_cmd="$curl_cmd -X POST -H 'Content-Type: application/json'"
        if [[ -n "$data" ]]; then
            curl_cmd="$curl_cmd -d '$data'"
        fi
    fi
    
    local response
    local http_code
    if response=$(eval "$curl_cmd '$endpoint'" 2>/dev/null); then
        http_code=$(echo "$response" | tail -n1)
        response_body=$(echo "$response" | head -n -1)
        
        if [[ "$http_code" == "$expected_status" ]]; then
            test_passed "$name endpoint is reachable (HTTP $http_code)"
            print_info "Response: $response_body"
            return 0
        else
            test_failed "$name endpoint returned HTTP $http_code (expected $expected_status)"
            return 1
        fi
    else
        test_failed "$name endpoint is not reachable"
        return 1
    fi
}

# Function to test Kafka connectivity
test_kafka_connectivity() {
    increment_test
    print_step "Testing Kafka connectivity..."
    
    if ./scripts/kafka-topics --list >/dev/null 2>&1; then
        test_passed "Kafka is accessible"
        return 0
    else
        test_failed "Kafka is not accessible"
        return 1
    fi
}

# Function to test Kafka topic creation
test_kafka_topic_creation() {
    increment_test
    print_step "Testing Kafka topic creation..."
    
    local test_topic="test.direct.kafka.bridge.$(date +%s)"
    
    if ./scripts/kafka-topics --create --topic "$test_topic" --partitions 1 --replication-factor 1 >/dev/null 2>&1; then
        test_passed "Kafka topic creation works"
        
        # Clean up test topic
        ./scripts/kafka-topics --delete --topic "$test_topic" >/dev/null 2>&1 || true
        return 0
    else
        test_failed "Kafka topic creation failed"
        return 1
    fi
}

# Function to test message publishing to Kafka
test_kafka_message_publishing() {
    increment_test
    print_step "Testing Kafka message publishing..."
    
    local test_topic="test.message.publishing.$(date +%s)"
    local test_message='{"test": true, "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    
    # Create test topic
    ./scripts/kafka-topics --create --topic "$test_topic" --partitions 1 --replication-factor 1 >/dev/null 2>&1
    
    # Publish test message
    if echo "$test_message" | ./scripts/kafka-console-producer --topic "$test_topic" >/dev/null 2>&1; then
        test_passed "Kafka message publishing works"
        
        # Clean up test topic
        ./scripts/kafka-topics --delete --topic "$test_topic" >/dev/null 2>&1 || true
        return 0
    else
        test_failed "Kafka message publishing failed"
        return 1
    fi
}

# Function to test Direct Kafka Bridge compilation
test_bridge_compilation() {
    increment_test
    print_step "Testing Direct Kafka Bridge compilation..."
    
    if mvn compile -q >/dev/null 2>&1; then
        test_passed "Direct Kafka Bridge compiles successfully"
        return 0
    else
        test_failed "Direct Kafka Bridge compilation failed"
        return 1
    fi
}

# Function to test bridge startup
test_bridge_startup() {
    increment_test
    print_step "Testing Direct Kafka Bridge startup..."
    
    # Start bridge in background
    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectKafkaBridge" >/dev/null 2>&1 &
    local bridge_pid=$!
    
    # Wait for bridge to start
    local attempts=0
    local max_attempts=30
    
    while [[ $attempts -lt $max_attempts ]]; do
        if curl -s --connect-timeout 2 "$HEALTH_ENDPOINT" >/dev/null 2>&1; then
            test_passed "Direct Kafka Bridge started successfully (PID: $bridge_pid)"
            
            # Stop bridge
            kill $bridge_pid 2>/dev/null || true
            wait $bridge_pid 2>/dev/null || true
            
            return 0
        fi
        
        sleep 1
        attempts=$((attempts + 1))
    done
    
    # Kill bridge if it didn't start
    kill $bridge_pid 2>/dev/null || true
    wait $bridge_pid 2>/dev/null || true
    
    test_failed "Direct Kafka Bridge failed to start within $max_attempts seconds"
    return 1
}

# Function to test bridge endpoints
test_bridge_endpoints() {
    print_step "Testing Direct Kafka Bridge endpoints..."
    
    # Start bridge in background
    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectKafkaBridge" >/dev/null 2>&1 &
    local bridge_pid=$!
    
    # Wait for bridge to start
    sleep 5
    
    # Test health endpoint
    test_endpoint "$HEALTH_ENDPOINT" "Health Check"
    
    # Test metrics endpoint
    test_endpoint "$METRICS_ENDPOINT" "Metrics"
    
    # Test rail comm GET endpoint
    test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm GET"
    
    # Test rail comm POST endpoint
    local test_json='{"test": true, "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
    test_endpoint "$RAIL_COMM_ENDPOINT" "Rail Comm POST" "POST" "$test_json"
    
    # Test direct Kafka GET endpoint
    test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka GET"
    
    # Test direct Kafka POST endpoint
    test_endpoint "$KAFKA_ENDPOINT" "Direct Kafka POST" "POST" "$test_json"
    
    # Stop bridge
    kill $bridge_pid 2>/dev/null || true
    wait $bridge_pid 2>/dev/null || true
}

# Function to test deployment script
test_deployment_script() {
    increment_test
    print_step "Testing deployment script functionality..."
    
    if [[ -f "scripts/deploy-direct-kafka-bridge.sh" ]] && [[ -x "scripts/deploy-direct-kafka-bridge.sh" ]]; then
        test_passed "Deployment script exists and is executable"
        return 0
    else
        test_failed "Deployment script is missing or not executable"
        return 1
    fi
}

# Function to test control script commands
test_control_script_commands() {
    print_step "Testing control script commands..."
    
    # Test bridge command help
    increment_test
    if ./rtd-control.sh rail-comm 2>&1 | grep -q "bridge"; then
        test_passed "Control script has bridge command"
    else
        test_failed "Control script missing bridge command"
    fi
    
    # Test bridge-stop command help
    increment_test
    if ./rtd-control.sh rail-comm 2>&1 | grep -q "bridge-stop"; then
        test_passed "Control script has bridge-stop command"
    else
        test_failed "Control script missing bridge-stop command"
    fi
    
    # Test test-bridge command help
    increment_test
    if ./rtd-control.sh rail-comm 2>&1 | grep -q "test-bridge"; then
        test_passed "Control script has test-bridge command"
    else
        test_failed "Control script missing test-bridge command"
    fi
}

# Function to test test script
test_test_script() {
    increment_test
    print_step "Testing test script functionality..."
    
    if [[ -f "scripts/test-direct-kafka-bridge.sh" ]] && [[ -x "scripts/test-direct-kafka-bridge.sh" ]]; then
        test_passed "Test script exists and is executable"
        return 0
    else
        test_failed "Test script is missing or not executable"
        return 1
    fi
}

# Function to test documentation
test_documentation() {
    print_step "Testing documentation..."
    
    # Test deployment guide
    increment_test
    if [[ -f "docs/DIRECT_KAFKA_BRIDGE_DEPLOYMENT.md" ]]; then
        test_passed "Deployment guide exists"
    else
        test_failed "Deployment guide is missing"
    fi
    
    # Test integration guide
    increment_test
    if [[ -f "docs/DIRECT_KAFKA_INTEGRATION.md" ]]; then
        test_passed "Integration guide exists"
    else
        test_failed "Integration guide is missing"
    fi
}

# Function to test full deployment workflow
test_full_deployment_workflow() {
    print_step "Testing full deployment workflow..."
    
    # Test deployment script status command
    increment_test
    if ./scripts/deploy-direct-kafka-bridge.sh status >/dev/null 2>&1; then
        test_passed "Deployment script status command works"
    else
        test_failed "Deployment script status command failed"
    fi
    
    # Test deployment script help command
    increment_test
    if ./scripts/deploy-direct-kafka-bridge.sh help >/dev/null 2>&1; then
        test_passed "Deployment script help command works"
    else
        test_failed "Deployment script help command failed"
    fi
}

# Function to test performance
test_performance() {
    print_step "Testing performance..."
    
    # Start bridge in background
    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectKafkaBridge" >/dev/null 2>&1 &
    local bridge_pid=$!
    
    # Wait for bridge to start
    sleep 5
    
    # Test response time
    increment_test
    local start_time=$(date +%s%N)
    if curl -s --connect-timeout 5 "$HEALTH_ENDPOINT" >/dev/null 2>&1; then
        local end_time=$(date +%s%N)
        local duration=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds
        
        if [[ $duration -lt 100 ]]; then
            test_passed "Health endpoint response time: ${duration}ms (acceptable)"
        else
            test_warning "Health endpoint response time: ${duration}ms (slow)"
        fi
    else
        test_failed "Health endpoint performance test failed"
    fi
    
    # Stop bridge
    kill $bridge_pid 2>/dev/null || true
    wait $bridge_pid 2>/dev/null || true
}

# Function to show test summary
show_test_summary() {
    echo
    print_status "Test Summary:"
    echo "=============="
    print_info "Total Tests: $TOTAL_TESTS"
    print_success "Passed: $PASSED_TESTS"
    print_error "Failed: $FAILED_TESTS"
    
    local success_rate=$(( (PASSED_TESTS * 100) / TOTAL_TESTS ))
    echo
    print_status "Success Rate: ${success_rate}%"
    
    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo
        print_success "🎉 All tests passed! Direct Kafka Bridge deployment is ready."
    else
        echo
        print_error "❌ Some tests failed. Please review the errors above."
        exit 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --full       - Run all tests (default)"
    echo "  --quick      - Run quick tests only"
    echo "  --kafka      - Test Kafka functionality only"
    echo "  --bridge     - Test bridge functionality only"
    echo "  --deployment - Test deployment scripts only"
    echo "  --help       - Show this help"
    echo
    echo "Examples:"
    echo "  $0              # Run all tests"
    echo "  $0 --quick      # Run quick tests"
    echo "  $0 --kafka      # Test Kafka only"
    echo "  $0 --bridge     # Test bridge only"
}

# Main test function
run_full_tests() {
    print_status "Starting Direct Kafka Bridge deployment tests..."
    echo
    
    # Prerequisites
    print_status "Testing Prerequisites:"
    echo "========================"
    test_kafka_connectivity
    test_kafka_topic_creation
    test_kafka_message_publishing
    test_bridge_compilation
    echo
    
    # Bridge functionality
    print_status "Testing Bridge Functionality:"
    echo "==============================="
    test_bridge_startup
    test_bridge_endpoints
    echo
    
    # Scripts and tools
    print_status "Testing Scripts and Tools:"
    echo "============================"
    test_deployment_script
    test_control_script_commands
    test_test_script
    echo
    
    # Documentation
    print_status "Testing Documentation:"
    echo "======================="
    test_documentation
    echo
    
    # Deployment workflow
    print_status "Testing Deployment Workflow:"
    echo "==============================="
    test_full_deployment_workflow
    echo
    
    # Performance
    print_status "Testing Performance:"
    echo "====================="
    test_performance
    echo
}

# Quick test function
run_quick_tests() {
    print_status "Running quick deployment tests..."
    echo
    
    test_kafka_connectivity
    test_bridge_compilation
    test_deployment_script
    test_test_script
    test_documentation
    echo
}

# Kafka-only test function
run_kafka_tests() {
    print_status "Running Kafka functionality tests..."
    echo
    
    test_kafka_connectivity
    test_kafka_topic_creation
    test_kafka_message_publishing
    echo
}

# Bridge-only test function
run_bridge_tests() {
    print_status "Running bridge functionality tests..."
    echo
    
    test_bridge_compilation
    test_bridge_startup
    test_bridge_endpoints
    test_performance
    echo
}

# Deployment-only test function
run_deployment_tests() {
    print_status "Running deployment script tests..."
    echo
    
    test_deployment_script
    test_control_script_commands
    test_test_script
    test_full_deployment_workflow
    echo
}

# Main script logic
case "${1:---full}" in
    "--full")
        run_full_tests
        ;;
    "--quick")
        run_quick_tests
        ;;
    "--kafka")
        run_kafka_tests
        ;;
    "--bridge")
        run_bridge_tests
        ;;
    "--deployment")
        run_deployment_tests
        ;;
    "--help"|"-h"|"help")
        show_usage
        exit 0
        ;;
    *)
        print_error "Unknown option: $1"
        show_usage
        exit 1
        ;;
esac

# Show test summary
show_test_summary
