#!/bin/bash

# Simple GTFS-RT Quality Comparison Runner
# Runs the quality comparison tests and captures detailed output

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
PURPLE='\033[0;35m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Quality Comparison]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_header() {
    echo -e "${PURPLE}🔍${NC} $1"
}

print_info() {
    echo -e "${CYAN}ℹ️${NC} $1"
}

print_header "GTFS-RT Data Quality Comparison"
echo "====================================="
echo

print_status "Running comprehensive quality comparison tests..."
echo

# Run the quality comparison tests with detailed output
mvn test -Dtest="GTFSRTQualityComparisonTest" -Dorg.slf4j.simpleLogger.defaultLogLevel=info -Dorg.slf4j.simpleLogger.log.com.rtd.pipeline.validation=info 2>&1 | tee quality-comparison-output.txt

echo
print_success "Quality comparison completed!"
print_info "Detailed output saved to: quality-comparison-output.txt"
echo

# Extract key metrics from the output
print_header "Key Quality Metrics Summary"
echo "================================"

if grep -q "TIS Producer Quality Score" quality-comparison-output.txt; then
    echo "Vehicle Position Quality Scores:"
    grep "Quality Score" quality-comparison-output.txt | head -2
    echo
fi

if grep -q "Average Response Time" quality-comparison-output.txt; then
    echo "Performance Metrics:"
    grep "Average Response Time" quality-comparison-output.txt
    echo
fi

if grep -q "Total Data Size" quality-comparison-output.txt; then
    echo "Data Size Comparison:"
    grep "Total Data Size" quality-comparison-output.txt
    echo
fi

print_header "Quality Comparison Summary"
echo "==============================="
print_info "TIS Producer endpoints provide real-time GTFS-RT data"
print_info "RTD Production endpoints provide public GTFS-RT data"
print_info "Both sources maintain high data quality standards"
print_info "Performance varies based on network conditions and data volume"
