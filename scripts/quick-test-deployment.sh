#!/bin/bash

# Quick Test Script for Direct Kafka Bridge Deployment
# Fast validation of the deployment process

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Quick Test]${NC} $1"
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
HEALTH_ENDPOINT="http://localhost:${BRIDGE_PORT}/health"

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to run a test
run_test() {
    local test_name="$1"
    local test_command="$2"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    print_status "Testing: $test_name"
    
    if eval "$test_command" >/dev/null 2>&1; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        print_success "$test_name: PASSED"
        return 0
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        print_error "$test_name: FAILED"
        return 1
    fi
}

# Function to show test summary
show_summary() {
    echo
    print_status "Quick Test Summary:"
    echo "===================="
    print_info "Total Tests: $TOTAL_TESTS"
    print_success "Passed: $PASSED_TESTS"
    print_error "Failed: $FAILED_TESTS"
    
    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo
        print_success "🎉 All quick tests passed! Direct Kafka Bridge is ready for deployment."
    else
        echo
        print_error "❌ Some tests failed. Run the full test suite for detailed diagnostics."
        print_info "Run: ./scripts/test-direct-kafka-bridge-deployment.sh --full"
    fi
}

# Main test execution
print_status "Starting Quick Test for Direct Kafka Bridge Deployment"
echo

# Test 1: Check if deployment script exists
run_test "Deployment script exists" "[[ -f 'scripts/deploy-direct-kafka-bridge.sh' ]]"

# Test 2: Check if deployment script is executable
run_test "Deployment script is executable" "[[ -x 'scripts/deploy-direct-kafka-bridge.sh' ]]"

# Test 3: Check if test script exists
run_test "Test script exists" "[[ -f 'scripts/test-direct-kafka-bridge.sh' ]]"

# Test 4: Check if test script is executable
run_test "Test script is executable" "[[ -x 'scripts/test-direct-kafka-bridge.sh' ]]"

# Test 5: Check if DirectKafkaBridge.java compiles
run_test "DirectKafkaBridge compiles" "mvn compile -q"

# Test 6: Check if control script has bridge commands
run_test "Control script has bridge commands" "./rtd-control.sh rail-comm 2>&1 | grep -q 'bridge'"

# Test 7: Check if deployment guide exists
run_test "Deployment guide exists" "[[ -f 'docs/DIRECT_KAFKA_BRIDGE_DEPLOYMENT.md' ]]"

# Test 8: Check if integration guide exists
run_test "Integration guide exists" "[[ -f 'docs/DIRECT_KAFKA_INTEGRATION.md' ]]"

# Test 9: Check if deployment script help works
run_test "Deployment script help works" "./scripts/deploy-direct-kafka-bridge.sh help >/dev/null"

# Test 10: Check if test script help works
run_test "Test script help works" "./scripts/test-direct-kafka-bridge.sh help >/dev/null"

echo
print_status "Quick tests completed!"

# Show summary
show_summary

# Exit with appropriate code
if [[ $FAILED_TESTS -eq 0 ]]; then
    exit 0
else
    exit 1
fi
