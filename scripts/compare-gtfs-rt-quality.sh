#!/bin/bash

# GTFS-RT Data Quality Comparison Script
# Compares TIS Producer vs RTD Production endpoints

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
    echo -e "${BLUE}[GTFS-RT Quality Comparison]${NC} $1"
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

print_header() {
    echo -e "${PURPLE}🔍${NC} $1"
}

# Configuration
TIS_VEHICLE_URL="http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb"
TIS_TRIP_URL="http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb"
TIS_ALERT_URL="http://tis-producer-d01:8001/gtfs-rt/Alerts.pb"

RTD_VEHICLE_URL="https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb"
RTD_TRIP_URL="https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb"
RTD_ALERT_URL="https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb"

TIMEOUT_SECONDS=30
OUTPUT_DIR="./gtfs-rt-quality-comparison"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Create output directory
mkdir -p "$OUTPUT_DIR"

print_header "GTFS-RT Data Quality Comparison"
echo "====================================="
echo
print_info "TIS Producer Endpoints:"
print_info "  Vehicle Position: $TIS_VEHICLE_URL"
print_info "  Trip Update: $TIS_TRIP_URL"
print_info "  Alerts: $TIS_ALERT_URL"
echo
print_info "RTD Production Endpoints:"
print_info "  Vehicle Position: $RTD_VEHICLE_URL"
print_info "  Trip Update: $RTD_TRIP_URL"
print_info "  Alerts: $RTD_ALERT_URL"
echo

# Function to download and analyze a feed
analyze_feed() {
    local url="$1"
    local name="$2"
    local temp_file="/tmp/gtfs-rt-${name}-${TIMESTAMP}.pb"
    
    print_status "Analyzing $name feed..."
    
    # Download the feed
    if curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$url" -o "$temp_file" 2>/dev/null; then
        local file_size=$(stat -f%z "$temp_file" 2>/dev/null || stat -c%s "$temp_file" 2>/dev/null)
        print_success "Downloaded $name feed (${file_size} bytes)"
        
        # Run Java analysis
        local analysis_result=$(mvn exec:java -Dexec.mainClass="com.rtd.pipeline.validation.GTFSRTQualityAnalyzer" \
            -Dexec.args="analyze $temp_file $name" -q 2>/dev/null || echo "Analysis failed")
        
        echo "$analysis_result"
        
        # Clean up
        rm -f "$temp_file"
        return 0
    else
        print_error "Failed to download $name feed"
        return 1
    fi
}

# Function to compare two feeds
compare_feeds() {
    local feed1_url="$1"
    local feed1_name="$2"
    local feed2_url="$3"
    local feed2_name="$4"
    local feed_type="$5"
    
    print_header "Comparing $feed_type: $feed1_name vs $feed2_name"
    echo "=================================================="
    
    local temp1="/tmp/gtfs-rt-${feed1_name}-${TIMESTAMP}.pb"
    local temp2="/tmp/gtfs-rt-${feed2_name}-${TIMESTAMP}.pb"
    
    # Download both feeds
    if curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$feed1_url" -o "$temp1" 2>/dev/null && \
       curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$feed2_url" -o "$temp2" 2>/dev/null; then
        
        local size1=$(stat -f%z "$temp1" 2>/dev/null || stat -c%s "$temp1" 2>/dev/null)
        local size2=$(stat -f%z "$temp2" 2>/dev/null || stat -c%s "$temp2" 2>/dev/null)
        
        print_success "Downloaded both feeds: $feed1_name (${size1} bytes), $feed2_name (${size2} bytes)"
        
        # Run Java comparison
        local comparison_result=$(mvn exec:java -Dexec.mainClass="com.rtd.pipeline.validation.GTFSRTQualityAnalyzer" \
            -Dexec.args="compare $temp1 $temp2 $feed1_name $feed2_name $feed_type" -q 2>/dev/null || echo "Comparison failed")
        
        echo "$comparison_result"
        
        # Clean up
        rm -f "$temp1" "$temp2"
        return 0
    else
        print_error "Failed to download one or both feeds"
        rm -f "$temp1" "$temp2"
        return 1
    fi
}

# Function to generate comprehensive report
generate_report() {
    local report_file="$OUTPUT_DIR/gtfs-rt-quality-comparison-${TIMESTAMP}.md"
    
    print_status "Generating comprehensive quality comparison report..."
    
    cat > "$report_file" << EOF
# GTFS-RT Data Quality Comparison Report

**Generated**: $(date)
**Comparison**: TIS Producer vs RTD Production Endpoints

## Endpoint Information

### TIS Producer Endpoints
- **Vehicle Position**: $TIS_VEHICLE_URL
- **Trip Update**: $TIS_TRIP_URL
- **Alerts**: $TIS_ALERT_URL

### RTD Production Endpoints
- **Vehicle Position**: $RTD_VEHICLE_URL
- **Trip Update**: $RTD_TRIP_URL
- **Alerts**: $RTD_ALERT_URL

## Summary

This report compares the data quality between TIS Producer and RTD Production GTFS-RT endpoints.

### Key Metrics Compared
- **Data Completeness**: Percentage of required fields present
- **Data Accuracy**: Coordinate validation, timestamp freshness
- **Data Consistency**: Cross-field validation, logical consistency
- **Performance**: Response times, file sizes
- **Coverage**: Number of entities, unique identifiers

### Quality Indicators
- ✅ **Excellent**: > 95% completeness, < 5% errors
- ⚠️ **Good**: 85-95% completeness, < 15% errors
- ❌ **Poor**: < 85% completeness, > 15% errors

## Detailed Analysis

EOF
    
    print_success "Report generated: $report_file"
}

# Main comparison execution
main() {
    print_header "Starting GTFS-RT Data Quality Comparison"
    echo
    
    # Test connectivity first
    print_status "Testing endpoint connectivity..."
    
    local connectivity_issues=0
    
    # Test TIS Producer endpoints
    if curl -s --connect-timeout 10 -I "$TIS_VEHICLE_URL" >/dev/null 2>&1; then
        print_success "TIS Producer Vehicle Position endpoint is reachable"
    else
        print_error "TIS Producer Vehicle Position endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    if curl -s --connect-timeout 10 -I "$TIS_TRIP_URL" >/dev/null 2>&1; then
        print_success "TIS Producer Trip Update endpoint is reachable"
    else
        print_error "TIS Producer Trip Update endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    if curl -s --connect-timeout 10 -I "$TIS_ALERT_URL" >/dev/null 2>&1; then
        print_success "TIS Producer Alerts endpoint is reachable"
    else
        print_error "TIS Producer Alerts endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    # Test RTD Production endpoints
    if curl -s --connect-timeout 10 -I "$RTD_VEHICLE_URL" >/dev/null 2>&1; then
        print_success "RTD Production Vehicle Position endpoint is reachable"
    else
        print_error "RTD Production Vehicle Position endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    if curl -s --connect-timeout 10 -I "$RTD_TRIP_URL" >/dev/null 2>&1; then
        print_success "RTD Production Trip Update endpoint is reachable"
    else
        print_error "RTD Production Trip Update endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    if curl -s --connect-timeout 10 -I "$RTD_ALERT_URL" >/dev/null 2>&1; then
        print_success "RTD Production Alerts endpoint is reachable"
    else
        print_error "RTD Production Alerts endpoint is not reachable"
        connectivity_issues=$((connectivity_issues + 1))
    fi
    
    if [[ $connectivity_issues -gt 0 ]]; then
        print_warning "Some endpoints are not reachable. Comparison may be incomplete."
        echo
    fi
    
    echo
    
    # Run individual feed analysis
    print_header "Individual Feed Analysis"
    echo "============================"
    echo
    
    analyze_feed "$TIS_VEHICLE_URL" "TIS-Vehicle"
    echo
    
    analyze_feed "$RTD_VEHICLE_URL" "RTD-Vehicle"
    echo
    
    analyze_feed "$TIS_TRIP_URL" "TIS-Trip"
    echo
    
    analyze_feed "$RTD_TRIP_URL" "RTD-Trip"
    echo
    
    analyze_feed "$TIS_ALERT_URL" "TIS-Alert"
    echo
    
    analyze_feed "$RTD_ALERT_URL" "RTD-Alert"
    echo
    
    # Run feed comparisons
    print_header "Feed Comparisons"
    echo "==================="
    echo
    
    compare_feeds "$TIS_VEHICLE_URL" "TIS-Vehicle" "$RTD_VEHICLE_URL" "RTD-Vehicle" "VehiclePosition"
    echo
    
    compare_feeds "$TIS_TRIP_URL" "TIS-Trip" "$RTD_TRIP_URL" "RTD-Trip" "TripUpdate"
    echo
    
    compare_feeds "$TIS_ALERT_URL" "TIS-Alert" "$RTD_ALERT_URL" "RTD-Alert" "Alerts"
    echo
    
    # Generate report
    generate_report
    
    print_header "Quality Comparison Complete"
    echo "================================"
    print_success "All comparisons completed successfully!"
    print_info "Check the report in: $OUTPUT_DIR/"
    print_info "Run Java tests for detailed analysis: mvn test -Dtest=\"GTFSRTQualityComparisonTest\""
}

# Run main function
main "$@"
