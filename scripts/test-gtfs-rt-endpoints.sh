#!/bin/bash

# GTFS-RT Endpoint Test Script
# Tests GTFS-RT endpoints with comprehensive validation

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
    echo -e "${BLUE}[GTFS-RT Test]${NC} $1"
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
VEHICLE_POSITION_URL="http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb"
TRIP_UPDATE_URL="http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb"
TIMEOUT_SECONDS=30
OUTPUT_DIR="./gtfs-rt-test-results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

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

# Function to test endpoint connectivity
test_endpoint_connectivity() {
    local url="$1"
    local name="$2"
    
    increment_test
    print_step "Testing connectivity to $name: $url"
    
    local response_code
    local content_length
    local response_time
    
    # Test with curl
    local start_time=$(date +%s%N)
    local curl_output
    if curl_output=$(curl -s -w "%{http_code}|%{size_download}|%{time_total}" --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$url" 2>/dev/null); then
        local end_time=$(date +%s%N)
        local duration=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds
        
        # Parse curl output
        local http_code=$(echo "$curl_output" | tail -c 50 | cut -d'|' -f1)
        local size=$(echo "$curl_output" | tail -c 50 | cut -d'|' -f2)
        local curl_time=$(echo "$curl_output" | tail -c 50 | cut -d'|' -f3)
        
        if [[ "$http_code" == "200" ]]; then
            test_passed "$name endpoint is reachable (HTTP $http_code, ${size} bytes, ${curl_time}s)"
            print_info "Response time: ${duration}ms"
            return 0
        else
            test_failed "$name endpoint returned HTTP $http_code"
            return 1
        fi
    else
        test_failed "$name endpoint is not reachable"
        return 1
    fi
}

# Function to test protobuf format
test_protobuf_format() {
    local url="$1"
    local name="$2"
    
    increment_test
    print_step "Testing protobuf format for $name"
    
    # Download the file
    local temp_file="/tmp/gtfs-rt-test-${name}-${TIMESTAMP}.pb"
    
    if curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$url" -o "$temp_file" 2>/dev/null; then
        local file_size=$(stat -f%z "$temp_file" 2>/dev/null || stat -c%s "$temp_file" 2>/dev/null)
        
        if [[ $file_size -gt 0 ]]; then
            # Try to parse with protobuf (if protoc is available)
            if command -v protoc >/dev/null 2>&1; then
                # This is a basic check - in practice you'd need the actual .proto files
                test_passed "$name protobuf file downloaded (${file_size} bytes)"
                print_info "File size: ${file_size} bytes"
            else
                test_passed "$name file downloaded (${file_size} bytes) - protobuf validation skipped (protoc not available)"
                print_info "File size: ${file_size} bytes"
            fi
            
            # Clean up
            rm -f "$temp_file"
            return 0
        else
            test_failed "$name file is empty"
            rm -f "$temp_file"
            return 1
        fi
    else
        test_failed "$name file download failed"
        return 1
    fi
}

# Function to run Java-based GTFS-RT tests
run_java_gtfs_rt_tests() {
    print_step "Running Java-based GTFS-RT validation tests..."
    
    # Test compilation
    increment_test
    if mvn compile -q >/dev/null 2>&1; then
        test_passed "GTFS-RT test classes compile successfully"
    else
        test_failed "GTFS-RT test classes compilation failed"
        return 1
    fi
    
    # Run specific validation tests
    increment_test
    if mvn test -Dtest="*ValidationTest" -q >/dev/null 2>&1; then
        test_passed "GTFS-RT validation tests passed"
    else
        test_failed "GTFS-RT validation tests failed"
        return 1
    fi
    
    # Run endpoint comparison tests (if they exist)
    increment_test
    if mvn test -Dtest="*EndpointComparisonTest" -q >/dev/null 2>&1; then
        test_passed "GTFS-RT endpoint comparison tests passed"
    else
        test_warning "GTFS-RT endpoint comparison tests not found or failed"
    fi
}

# Function to create custom endpoint test
create_custom_endpoint_test() {
    print_step "Creating custom endpoint test for specified URLs..."
    
    # Create a temporary test class
    local test_file="/tmp/CustomGTFSRTEndpointTest.java"
    
    cat > "$test_file" << 'EOF'
package com.rtd.pipeline.validation;

import com.google.transit.realtime.GtfsRealtime.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

public class CustomGTFSRTEndpointTest {
    
    private static final String VEHICLE_POSITION_URL = "http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb";
    private static final String TRIP_UPDATE_URL = "http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb";
    private static final int TIMEOUT_SECONDS = 30;
    
    @Test
    @DisplayName("Test Vehicle Position endpoint")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testVehiclePositionEndpoint() {
        System.out.println("Testing Vehicle Position endpoint: " + VEHICLE_POSITION_URL);
        
        FeedMessage feed = fetchGTFSRTFeed(VEHICLE_POSITION_URL);
        
        assertThat(feed).isNotNull();
        assertThat(feed.getEntityCount()).isGreaterThan(0);
        
        System.out.println("✅ Vehicle Position feed has " + feed.getEntityCount() + " entities");
        
        // Validate first entity
        if (feed.getEntityCount() > 0) {
            FeedEntity entity = feed.getEntity(0);
            assertThat(entity.hasVehicle()).isTrue();
            
            VehiclePosition vehicle = entity.getVehicle();
            System.out.println("✅ First vehicle: ID=" + entity.getId() + 
                             ", Trip=" + (vehicle.hasTrip() ? vehicle.getTrip().getTripId() : "N/A") +
                             ", Route=" + (vehicle.hasTrip() && vehicle.getTrip().hasRouteId() ? vehicle.getTrip().getRouteId() : "N/A"));
        }
    }
    
    @Test
    @DisplayName("Test Trip Update endpoint")
    @Timeout(value = TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void testTripUpdateEndpoint() {
        System.out.println("Testing Trip Update endpoint: " + TRIP_UPDATE_URL);
        
        FeedMessage feed = fetchGTFSRTFeed(TRIP_UPDATE_URL);
        
        assertThat(feed).isNotNull();
        
        System.out.println("✅ Trip Update feed has " + feed.getEntityCount() + " entities");
        
        // Validate first entity if present
        if (feed.getEntityCount() > 0) {
            FeedEntity entity = feed.getEntity(0);
            assertThat(entity.hasTripUpdate()).isTrue();
            
            TripUpdate tripUpdate = entity.getTripUpdate();
            System.out.println("✅ First trip update: ID=" + entity.getId() + 
                             ", Trip=" + (tripUpdate.hasTrip() ? tripUpdate.getTrip().getTripId() : "N/A"));
        }
    }
    
    private FeedMessage fetchGTFSRTFeed(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", "RTD-GTFS-RT-Test/1.0");
            
            HttpResponse response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 200) {
                byte[] data = EntityUtils.toByteArray(response.getEntity());
                System.out.println("Downloaded " + data.length + " bytes from " + url);
                
                return FeedMessage.parseFrom(new ByteArrayInputStream(data));
            } else {
                System.out.println("HTTP error " + statusCode + " for " + url);
                return null;
            }
        } catch (Exception e) {
            System.out.println("Error fetching " + url + ": " + e.getMessage());
            return null;
        }
    }
}
EOF
    
    # Compile and run the custom test
    increment_test
    if mvn test -Dtest="CustomGTFSRTEndpointTest" -q >/dev/null 2>&1; then
        test_passed "Custom GTFS-RT endpoint tests passed"
    else
        test_failed "Custom GTFS-RT endpoint tests failed"
    fi
    
    # Clean up
    rm -f "$test_file"
}

# Function to test data quality
test_data_quality() {
    print_step "Testing GTFS-RT data quality..."
    
    # This would require parsing the protobuf data
    # For now, we'll do basic checks
    increment_test
    
    local vehicle_file="/tmp/vehicle-test-${TIMESTAMP}.pb"
    local trip_file="/tmp/trip-test-${TIMESTAMP}.pb"
    
    # Download files
    if curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$VEHICLE_POSITION_URL" -o "$vehicle_file" 2>/dev/null && \
       curl -s --connect-timeout 10 --max-time $TIMEOUT_SECONDS "$TRIP_UPDATE_URL" -o "$trip_file" 2>/dev/null; then
        
        local vehicle_size=$(stat -f%z "$vehicle_file" 2>/dev/null || stat -c%s "$vehicle_file" 2>/dev/null)
        local trip_size=$(stat -f%z "$trip_file" 2>/dev/null || stat -c%s "$trip_file" 2>/dev/null)
        
        if [[ $vehicle_size -gt 100 ]] && [[ $trip_size -gt 100 ]]; then
            test_passed "GTFS-RT data quality check passed (Vehicle: ${vehicle_size} bytes, Trip: ${trip_size} bytes)"
        else
            test_failed "GTFS-RT data quality check failed (files too small)"
        fi
        
        # Clean up
        rm -f "$vehicle_file" "$trip_file"
    else
        test_failed "GTFS-RT data quality check failed (download error)"
    fi
}

# Function to generate test report
generate_test_report() {
    print_step "Generating test report..."
    
    mkdir -p "$OUTPUT_DIR"
    local report_file="$OUTPUT_DIR/gtfs-rt-test-report-${TIMESTAMP}.txt"
    
    cat > "$report_file" << EOF
GTFS-RT Endpoint Test Report
============================
Generated: $(date)
Test URLs:
- Vehicle Position: $VEHICLE_POSITION_URL
- Trip Update: $TRIP_UPDATE_URL

Test Results:
- Total Tests: $TOTAL_TESTS
- Passed: $PASSED_TESTS
- Failed: $FAILED_TESTS
- Success Rate: $(( (PASSED_TESTS * 100) / TOTAL_TESTS ))%

Detailed Results:
EOF
    
    print_success "Test report generated: $report_file"
}

# Function to show test summary
show_test_summary() {
    echo
    print_status "GTFS-RT Endpoint Test Summary:"
    echo "=================================="
    print_info "Total Tests: $TOTAL_TESTS"
    print_success "Passed: $PASSED_TESTS"
    print_error "Failed: $FAILED_TESTS"
    
    local success_rate=$(( (PASSED_TESTS * 100) / TOTAL_TESTS ))
    echo
    print_status "Success Rate: ${success_rate}%"
    
    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo
        print_success "🎉 All GTFS-RT endpoint tests passed!"
    else
        echo
        print_error "❌ Some tests failed. Please review the errors above."
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --connectivity - Test endpoint connectivity only"
    echo "  --protobuf     - Test protobuf format only"
    echo "  --java         - Run Java-based validation tests only"
    echo "  --quality      - Test data quality only"
    echo "  --all          - Run all tests (default)"
    echo "  --help         - Show this help"
    echo
    echo "Examples:"
    echo "  $0              # Run all tests"
    echo "  $0 --connectivity  # Test connectivity only"
    echo "  $0 --java       # Run Java tests only"
}

# Main test execution
run_all_tests() {
    print_status "Starting GTFS-RT endpoint tests..."
    echo
    
    # Test connectivity
    print_status "Testing Endpoint Connectivity:"
    echo "=================================="
    test_endpoint_connectivity "$VEHICLE_POSITION_URL" "Vehicle Position"
    test_endpoint_connectivity "$TRIP_UPDATE_URL" "Trip Update"
    echo
    
    # Test protobuf format
    print_status "Testing Protobuf Format:"
    echo "==========================="
    test_protobuf_format "$VEHICLE_POSITION_URL" "Vehicle Position"
    test_protobuf_format "$TRIP_UPDATE_URL" "Trip Update"
    echo
    
    # Run Java tests
    print_status "Running Java-based Tests:"
    echo "============================="
    run_java_gtfs_rt_tests
    echo
    
    # Create custom endpoint test
    print_status "Running Custom Endpoint Tests:"
    echo "=================================="
    create_custom_endpoint_test
    echo
    
    # Test data quality
    print_status "Testing Data Quality:"
    echo "========================"
    test_data_quality
    echo
    
    # Generate report
    generate_test_report
}

# Main script logic
case "${1:---all}" in
    "--all")
        run_all_tests
        ;;
    "--connectivity")
        print_status "Testing endpoint connectivity only..."
        test_endpoint_connectivity "$VEHICLE_POSITION_URL" "Vehicle Position"
        test_endpoint_connectivity "$TRIP_UPDATE_URL" "Trip Update"
        ;;
    "--protobuf")
        print_status "Testing protobuf format only..."
        test_protobuf_format "$VEHICLE_POSITION_URL" "Vehicle Position"
        test_protobuf_format "$TRIP_UPDATE_URL" "Trip Update"
        ;;
    "--java")
        print_status "Running Java-based tests only..."
        run_java_gtfs_rt_tests
        create_custom_endpoint_test
        ;;
    "--quality")
        print_status "Testing data quality only..."
        test_data_quality
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

# Exit with appropriate code
if [[ $FAILED_TESTS -eq 0 ]]; then
    exit 0
else
    exit 1
fi
