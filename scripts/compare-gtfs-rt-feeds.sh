#!/bin/bash

# RTD GTFS-RT Feed Comparison Script
# Compares the official RTD GTFS-RT feeds with locally generated feeds

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
RTD_VEHICLE_POSITIONS_URL="https://www.rtd-denver.com/files/gtfs-rt/VehiclePosition.pb"
RTD_TRIP_UPDATES_URL="https://www.rtd-denver.com/files/gtfs-rt/TripUpdate.pb"
LOCAL_GTFS_RT_DIR="data/gtfs-rt"
TEMP_DIR="temp/comparison"
TIMEOUT_SECONDS=15

# Function to print colored output
print_header() {
    echo -e "${PURPLE}========================================${NC}"
    echo -e "${PURPLE}$1${NC}"
    echo -e "${PURPLE}========================================${NC}"
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
    echo -e "${BLUE}ℹ️${NC} $1"
}

print_step() {
    echo -e "${CYAN}🔄${NC} $1"
}

# Function to check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if Maven is available
    if ! command -v mvn &> /dev/null; then
        print_error "Maven is required but not found. Please install Maven."
        exit 1
    fi
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        print_error "curl is required but not found. Please install curl."
        exit 1
    fi
    
    # Check if protoc is available (optional, for debugging)
    if command -v protoc &> /dev/null; then
        print_success "Protocol Buffers compiler found"
    else
        print_warning "protoc not found (optional for debugging)"
    fi
    
    # Create temp directory
    mkdir -p "$TEMP_DIR"
    print_success "Prerequisites check completed"
}

# Function to download RTD feeds
download_rtd_feeds() {
    print_header "Downloading Official RTD GTFS-RT Feeds"
    
    print_step "Downloading Vehicle Positions feed..."
    if curl -s --max-time $TIMEOUT_SECONDS -f -o "$TEMP_DIR/rtd_vehicle_positions.pb" "$RTD_VEHICLE_POSITIONS_URL"; then
        local size=$(ls -lh "$TEMP_DIR/rtd_vehicle_positions.pb" | awk '{print $5}')
        print_success "Downloaded Vehicle Positions feed (${size})"
    else
        print_error "Failed to download Vehicle Positions feed from $RTD_VEHICLE_POSITIONS_URL"
        return 1
    fi
    
    print_step "Downloading Trip Updates feed..."
    if curl -s --max-time $TIMEOUT_SECONDS -f -o "$TEMP_DIR/rtd_trip_updates.pb" "$RTD_TRIP_UPDATES_URL"; then
        local size=$(ls -lh "$TEMP_DIR/rtd_trip_updates.pb" | awk '{print $5}')
        print_success "Downloaded Trip Updates feed (${size})"
    else
        print_error "Failed to download Trip Updates feed from $RTD_TRIP_UPDATES_URL"
        return 1
    fi
}

# Function to check local feeds
check_local_feeds() {
    print_header "Checking Local GTFS-RT Feeds"
    
    if [ ! -d "$LOCAL_GTFS_RT_DIR" ]; then
        print_error "Local GTFS-RT directory not found: $LOCAL_GTFS_RT_DIR"
        print_info "Run the GTFS-RT pipeline first: ./rtd-control.sh gtfs-rt all"
        return 1
    fi
    
    # Check Vehicle Positions
    if [ -f "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" ]; then
        local size=$(ls -lh "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" | awk '{print $5}')
        local modified=$(ls -l "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" | awk '{print $6, $7, $8}')
        print_success "Local Vehicle Positions feed found (${size}, modified: ${modified})"
    else
        print_error "Local Vehicle Positions feed not found"
        return 1
    fi
    
    # Check Trip Updates
    if [ -f "$LOCAL_GTFS_RT_DIR/TripUpdate.pb" ]; then
        local size=$(ls -lh "$LOCAL_GTFS_RT_DIR/TripUpdate.pb" | awk '{print $5}')
        local modified=$(ls -l "$LOCAL_GTFS_RT_DIR/TripUpdate.pb" | awk '{print $6, $7, $8}')
        print_success "Local Trip Updates feed found (${size}, modified: ${modified})"
    else
        print_error "Local Trip Updates feed not found"
        return 1
    fi
}

# Function to run Java comparison test
run_comparison_test() {
    print_header "Running Comprehensive Feed Comparison"
    
    print_step "Executing Maven test for feed comparison..."
    
    # Copy downloaded files to expected locations for the test
    cp "$TEMP_DIR/rtd_vehicle_positions.pb" "$TEMP_DIR/rtd_VehiclePosition.pb" 2>/dev/null || true
    cp "$TEMP_DIR/rtd_trip_updates.pb" "$TEMP_DIR/rtd_TripUpdate.pb" 2>/dev/null || true
    
    # Run the comparison test
    if mvn test -Dtest="RTDFeedComparisonTest" -q 2>&1; then
        print_success "Feed comparison test completed successfully"
    else
        print_warning "Feed comparison test completed with some issues (see output above)"
    fi
}

# Function to perform basic protobuf analysis
analyze_protobuf_structure() {
    print_header "Analyzing Protobuf Structure"
    
    if command -v xxd &> /dev/null; then
        print_step "Analyzing RTD Vehicle Positions feed structure..."
        echo "First 256 bytes of RTD Vehicle Positions feed:"
        xxd -l 256 "$TEMP_DIR/rtd_vehicle_positions.pb" | head -10
        
        echo
        print_step "Analyzing Local Vehicle Positions feed structure..."
        echo "First 256 bytes of Local Vehicle Positions feed:"
        xxd -l 256 "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" | head -10
    else
        print_warning "xxd not available for binary analysis"
    fi
}

# Function to generate summary report
generate_summary_report() {
    print_header "Feed Comparison Summary Report"
    
    local report_file="$TEMP_DIR/comparison_report.txt"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    
    {
        echo "RTD GTFS-RT Feed Comparison Report"
        echo "Generated: $timestamp"
        echo "=========================================="
        echo
        
        echo "RTD Feed Sources:"
        echo "- Vehicle Positions: $RTD_VEHICLE_POSITIONS_URL"
        echo "- Trip Updates: $RTD_TRIP_UPDATES_URL"
        echo
        
        echo "Local Feed Sources:"
        echo "- Vehicle Positions: $LOCAL_GTFS_RT_DIR/VehiclePosition.pb"
        echo "- Trip Updates: $LOCAL_GTFS_RT_DIR/TripUpdate.pb"
        echo
        
        echo "File Sizes:"
        if [ -f "$TEMP_DIR/rtd_vehicle_positions.pb" ]; then
            echo "- RTD Vehicle Positions: $(ls -lh $TEMP_DIR/rtd_vehicle_positions.pb | awk '{print $5}')"
        fi
        if [ -f "$TEMP_DIR/rtd_trip_updates.pb" ]; then
            echo "- RTD Trip Updates: $(ls -lh $TEMP_DIR/rtd_trip_updates.pb | awk '{print $5}')"
        fi
        if [ -f "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" ]; then
            echo "- Local Vehicle Positions: $(ls -lh $LOCAL_GTFS_RT_DIR/VehiclePosition.pb | awk '{print $5}')"
        fi
        if [ -f "$LOCAL_GTFS_RT_DIR/TripUpdate.pb" ]; then
            echo "- Local Trip Updates: $(ls -lh $LOCAL_GTFS_RT_DIR/TripUpdate.pb | awk '{print $5}')"
        fi
        echo
        
        echo "File Modification Times:"
        if [ -f "$LOCAL_GTFS_RT_DIR/VehiclePosition.pb" ]; then
            echo "- Local Vehicle Positions: $(ls -l $LOCAL_GTFS_RT_DIR/VehiclePosition.pb | awk '{print $6, $7, $8}')"
        fi
        if [ -f "$LOCAL_GTFS_RT_DIR/TripUpdate.pb" ]; then
            echo "- Local Trip Updates: $(ls -l $LOCAL_GTFS_RT_DIR/TripUpdate.pb | awk '{print $6, $7, $8}')"
        fi
        
    } > "$report_file"
    
    cat "$report_file"
    print_success "Full report saved to: $report_file"
}

# Function to cleanup temporary files
cleanup() {
    print_info "Cleaning up temporary files..."
    rm -rf "$TEMP_DIR"
}

# Function to show usage
show_usage() {
    echo "RTD GTFS-RT Feed Comparison Script"
    echo
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --skip-download    Skip downloading RTD feeds (use cached files)"
    echo "  --test-only        Run only the Java comparison test"
    echo "  --structure-only   Analyze only protobuf structure"
    echo "  --help            Show this help message"
    echo
    echo "Examples:"
    echo "  $0                          # Full comparison"
    echo "  $0 --skip-download          # Use cached RTD feeds"
    echo "  $0 --test-only              # Run only Maven test"
    echo
    echo "Prerequisites:"
    echo "  1. Local GTFS-RT feeds must be generated first:"
    echo "     ./rtd-control.sh gtfs-rt all"
    echo "  2. Internet connection for downloading RTD feeds"
    echo "  3. Maven for running comparison tests"
}

# Main execution function
main() {
    local skip_download=false
    local test_only=false
    local structure_only=false
    
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-download)
                skip_download=true
                shift
                ;;
            --test-only)
                test_only=true
                shift
                ;;
            --structure-only)
                structure_only=true
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done
    
    # Set up trap for cleanup
    trap cleanup EXIT
    
    print_header "RTD GTFS-RT Feed Comparison Tool"
    echo "Comparing official RTD feeds with locally generated feeds"
    echo
    
    # Execute based on options
    check_prerequisites
    
    if [ "$test_only" = true ]; then
        run_comparison_test
        return
    fi
    
    if [ "$structure_only" = true ]; then
        analyze_protobuf_structure
        return
    fi
    
    # Full comparison flow
    if [ "$skip_download" = false ]; then
        if ! download_rtd_feeds; then
            print_error "Failed to download RTD feeds. Check network connection."
            exit 1
        fi
    else
        print_info "Skipping download, using cached files"
    fi
    
    if ! check_local_feeds; then
        print_error "Local feeds not available. Please run the GTFS-RT pipeline first."
        exit 1
    fi
    
    run_comparison_test
    analyze_protobuf_structure
    generate_summary_report
    
    print_success "Feed comparison completed successfully!"
    print_info "Check the detailed Maven test output above for comprehensive analysis."
}

# Execute main function with all arguments
main "$@"