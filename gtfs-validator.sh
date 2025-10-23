#!/bin/bash

# RTD GTFS Validation Tool
# Validates all RTD GTFS feeds from https://www.rtd-denver.com/open-records/open-spatial-information/gtfs

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$PROJECT_DIR/target"
JAR_FILE="$TARGET_DIR/transitstream-1.0-SNAPSHOT.jar"

# Java options for the validator
JAVA_OPTS="-Xmx2g -Xms512m"
JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/java.io=ALL-UNNAMED"
JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/sun.misc=ALL-UNNAMED"
JAVA_OPTS="$JAVA_OPTS --add-opens=java.base/java.lang=ALL-UNNAMED"

# Function to print colored output
print_color() {
    color=$1
    shift
    echo -e "${color}$*${NC}"
}

# Function to print header
print_header() {
    echo
    print_color $BLUE "=================================================="
    print_color $BLUE "$1"
    print_color $BLUE "=================================================="
    echo
}

# Function to check if Java is available
check_java() {
    if ! command -v java &> /dev/null; then
        print_color $RED "❌ Java is not installed or not in PATH"
        print_color $YELLOW "Please install Java 21+ and ensure it's in your PATH"
        exit 1
    fi
    
    # Check Java version
    JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ]; then
        print_color $YELLOW "⚠️  Java version $JAVA_VERSION detected. Java 21+ recommended."
    fi
}

# Function to build the project if needed
build_if_needed() {
    if [ ! -f "$JAR_FILE" ]; then
        print_color $YELLOW "📦 JAR file not found. Building project..."
        
        if ! command -v mvn &> /dev/null; then
            print_color $RED "❌ Maven is not installed or not in PATH"
            print_color $YELLOW "Please install Maven and ensure it's in your PATH, or build the project manually"
            exit 1
        fi
        
        cd "$PROJECT_DIR"
        mvn clean package -DskipTests -q
        
        if [ $? -eq 0 ] && [ -f "$JAR_FILE" ]; then
            print_color $GREEN "✅ Project built successfully"
        else
            print_color $RED "❌ Failed to build project"
            exit 1
        fi
    fi
}

# Function to show usage
show_usage() {
    echo "RTD GTFS Validation Tool"
    echo "========================"
    echo
    echo "Validates all RTD GTFS feeds available at:"
    echo "https://www.rtd-denver.com/open-records/open-spatial-information/gtfs"
    echo
    echo "USAGE:"
    echo "  $0 [command] [options]"
    echo
    echo "COMMANDS:"
    echo "  validate [feed_name]    Validate GTFS feed(s)"
    echo "                          Without feed_name: validates all feeds"
    echo "                          With feed_name: validates specific feed"
    echo
    echo "  list                    List all available RTD GTFS feeds"
    echo "  test                    Run the validation test suite"
    echo "  build                   Force rebuild of the project"
    echo "  help                    Show this help message"
    echo
    echo "AVAILABLE FEEDS:"
    echo "  google_transit          RTD Main Transit Feed (Combined)"
    echo "  google_transit_flex     RTD Flex/On-Demand Transit Feed"
    echo "  bustang-co-us          Colorado Bustang Intercity Service"
    echo "  commuter_rail          RTD Direct Operated Commuter Rail"
    echo "  light_rail             RTD Direct Operated Light Rail"
    echo "  motorbus               RTD Direct Operated Motor Bus"
    echo "  purchased_motorbus     RTD Purchased Transportation Motor Bus"
    echo "  purchased_commuter     RTD Purchased Transportation Commuter Rail"
    echo
    echo "EXAMPLES:"
    echo "  $0 validate                    # Validate all feeds"
    echo "  $0 validate google_transit     # Validate main transit feed"
    echo "  $0 validate light_rail         # Validate light rail feed"
    echo "  $0 list                        # List all feeds"
    echo "  $0 test                        # Run test suite"
    echo
    echo "VALIDATION CHECKS:"
    echo "  ✓ File presence and structure"
    echo "  ✓ Data format validation"
    echo "  ✓ Cross-reference validation"
    echo "  ✓ Geographic bounds (RTD service area)"
    echo "  ✓ Service definition validation"
    echo
}

# Function to run the validator
run_validator() {
    cd "$PROJECT_DIR"
    
    # Run the CLI tool
    java $JAVA_OPTS -cp "$JAR_FILE" com.rtd.pipeline.tools.GTFSValidationCLI "$@"
}

# Function to run the validator directly (alternative method)
run_validator_direct() {
    cd "$PROJECT_DIR"
    
    # Run the main validator class directly
    java $JAVA_OPTS -cp "$JAR_FILE" com.rtd.pipeline.validation.GTFSZipValidator "$@"
}

# Function to run tests
run_tests() {
    print_header "Running GTFS Validation Test Suite"
    
    cd "$PROJECT_DIR"
    
    if ! command -v mvn &> /dev/null; then
        print_color $RED "❌ Maven is required to run tests"
        exit 1
    fi
    
    print_color $BLUE "🧪 Running validation tests..."
    mvn test -Dtest=GTFSZipValidatorTest
    
    if [ $? -eq 0 ]; then
        print_color $GREEN "✅ All tests passed!"
    else
        print_color $RED "❌ Some tests failed"
        exit 1
    fi
}

# Function to force rebuild
force_rebuild() {
    print_header "Force Rebuilding Project"
    
    cd "$PROJECT_DIR"
    
    if ! command -v mvn &> /dev/null; then
        print_color $RED "❌ Maven is required to build"
        exit 1
    fi
    
    print_color $BLUE "🔨 Cleaning and rebuilding..."
    rm -f "$JAR_FILE"
    mvn clean package -DskipTests
    
    if [ $? -eq 0 ] && [ -f "$JAR_FILE" ]; then
        print_color $GREEN "✅ Project rebuilt successfully"
    else
        print_color $RED "❌ Failed to rebuild project"
        exit 1
    fi
}

# Main script logic
main() {
    # Check prerequisites
    check_java
    
    # Handle commands
    case "${1:-help}" in
        "validate")
            build_if_needed
            print_header "RTD GTFS Validation Tool"
            shift
            run_validator validate "$@"
            ;;
        "list")
            build_if_needed
            run_validator list
            ;;
        "test")
            run_tests
            ;;
        "build")
            force_rebuild
            ;;
        "help"|"--help"|"-h")
            show_usage
            ;;
        *)
            print_color $RED "❌ Unknown command: $1"
            echo
            show_usage
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"