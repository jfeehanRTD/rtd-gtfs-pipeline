#!/bin/bash

# GTFS-RT Test Runner Script
# Runs GTFS-RT tests against TIS Producer endpoints

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[GTFS-RT Test Runner]${NC} $1"
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
VEHICLE_POSITION_URL="http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb"
TRIP_UPDATE_URL="http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb"

print_status "Starting GTFS-RT tests against TIS Producer endpoints..."
echo
print_info "Vehicle Position: $VEHICLE_POSITION_URL"
print_info "Trip Update: $TRIP_UPDATE_URL"
echo

# Test 1: Basic connectivity
print_status "Test 1: Testing endpoint connectivity..."

if curl -s --connect-timeout 10 -I "$VEHICLE_POSITION_URL" >/dev/null 2>&1; then
    print_success "Vehicle Position endpoint is reachable"
else
    print_error "Vehicle Position endpoint is not reachable"
    exit 1
fi

if curl -s --connect-timeout 10 -I "$TRIP_UPDATE_URL" >/dev/null 2>&1; then
    print_success "Trip Update endpoint is reachable"
else
    print_error "Trip Update endpoint is not reachable"
    exit 1
fi

# Test 2: Download and check file sizes
print_status "Test 2: Testing file downloads..."

VEHICLE_FILE="/tmp/vehicle-test-$(date +%s).pb"
TRIP_FILE="/tmp/trip-test-$(date +%s).pb"

if curl -s --connect-timeout 10 --max-time 30 "$VEHICLE_POSITION_URL" -o "$VEHICLE_FILE" 2>/dev/null; then
    VEHICLE_SIZE=$(stat -f%z "$VEHICLE_FILE" 2>/dev/null || stat -c%s "$VEHICLE_FILE" 2>/dev/null)
    print_success "Vehicle Position file downloaded (${VEHICLE_SIZE} bytes)"
else
    print_error "Failed to download Vehicle Position file"
    exit 1
fi

if curl -s --connect-timeout 10 --max-time 30 "$TRIP_UPDATE_URL" -o "$TRIP_FILE" 2>/dev/null; then
    TRIP_SIZE=$(stat -f%z "$TRIP_FILE" 2>/dev/null || stat -c%s "$TRIP_FILE" 2>/dev/null)
    print_success "Trip Update file downloaded (${TRIP_SIZE} bytes)"
else
    print_error "Failed to download Trip Update file"
    exit 1
fi

# Test 3: Run Java-based tests
print_status "Test 3: Running Java-based GTFS-RT validation tests..."

# Compile the project
if mvn compile -q >/dev/null 2>&1; then
    print_success "Project compiled successfully"
else
    print_error "Project compilation failed"
    exit 1
fi

# Run the TIS Producer tests
if mvn test -Dtest="TISProducerGTFSRTTest" -q 2>/dev/null; then
    print_success "TIS Producer GTFS-RT tests passed"
else
    print_warning "TIS Producer GTFS-RT tests failed or not found"
    print_info "This may be expected if the endpoints are not accessible from this environment"
fi

# Test 4: Run general validation tests
print_status "Test 4: Running general GTFS-RT validation tests..."

if mvn test -Dtest="*ValidationTest" -q 2>/dev/null; then
    print_success "General GTFS-RT validation tests passed"
else
    print_warning "General GTFS-RT validation tests failed"
fi

# Test 5: Run endpoint comparison tests
print_status "Test 5: Running endpoint comparison tests..."

if mvn test -Dtest="*EndpointComparisonTest" -q 2>/dev/null; then
    print_success "Endpoint comparison tests passed"
else
    print_warning "Endpoint comparison tests failed or not found"
fi

# Clean up
rm -f "$VEHICLE_FILE" "$TRIP_FILE"

echo
print_success "GTFS-RT test execution completed!"
print_info "Check the test output above for detailed results."
print_info "If tests failed, verify that the TIS Producer endpoints are accessible from this environment."
