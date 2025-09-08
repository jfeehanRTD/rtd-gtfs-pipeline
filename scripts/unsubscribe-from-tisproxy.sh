#!/bin/bash

# TIS Proxy Unsubscription Script
# Companion to subscribe-to-tisproxy.sh for unsubscribing from SIRI, LRGPS, and Rail Comm feeds
# Usage: ./unsubscribe-from-tisproxy.sh <ip> <port> [feed_type]

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[TIS Unsubscribe]${NC} $1"
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

# Load credentials from environment if available
if [[ -f .env ]]; then
    set -a  # automatically export all variables
    source .env
    set +a  # stop automatic export
fi

# Default values
DEFAULT_TIS_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
DEFAULT_USERNAME="${TIS_PROXY_USERNAME:-}"
DEFAULT_PASSWORD="${TIS_PROXY_PASSWORD:-}"

# Feed type configuration functions (compatible with older bash)
get_service_name() {
    case "$1" in
        "siri") echo "siri" ;;
        "lrgps") echo "lrgps" ;;
        "railcomm"|"rail") echo "railcomm" ;;
        *) echo "" ;;
    esac
}

get_endpoint() {
    case "$1" in
        "siri") echo "/bus-siri" ;;
        "lrgps") echo "/lrgps" ;;
        "railcomm"|"rail") echo "/rail-comm" ;;
        *) echo "" ;;
    esac
}

# Usage function
show_usage() {
    echo "TIS Proxy Unsubscription Script"
    echo ""
    echo "Usage: $0 <ip> <port> [feed_type]"
    echo ""
    echo "Arguments:"
    echo "  ip          IP address that was used for subscription"
    echo "  port        Port number that was used for subscription"
    echo "  feed_type   Type of feed to unsubscribe from (default: all)"
    echo ""
    echo "Feed Types:"
    echo "  siri        Bus SIRI feed"
    echo "  lrgps       Light Rail GPS feed"
    echo "  railcomm    Rail Communication feed"
    echo "  rail        Alias for railcomm"
    echo "  all         Unsubscribe from all feeds (default)"
    echo ""
    echo "Examples:"
    echo "  $0 192.168.1.100 8082 siri      # Unsubscribe from SIRI feed only"
    echo "  $0 192.168.1.100 8083 lrgps     # Unsubscribe from LRGPS feed only"
    echo "  $0 192.168.1.100 8081 railcomm  # Unsubscribe from Rail Comm feed only"
    echo "  $0 192.168.1.100 8080 all       # Unsubscribe from all feeds"
    echo "  $0 192.168.1.100 8080           # Unsubscribe from all feeds (default)"
    echo ""
    echo "Environment Variables:"
    echo "  TIS_PROXY_HOST      TIS proxy host URL (default: http://tisproxy.rtd-denver.com)"
    echo "  TIS_PROXY_USERNAME  Username for authentication"
    echo "  TIS_PROXY_PASSWORD  Password for authentication"
    echo ""
    echo "Note: Credentials can also be loaded from .env file"
}

# Function to unsubscribe from a specific feed
unsubscribe_from_feed() {
    local ip="$1"
    local port="$2" 
    local feed_type="$3"
    
    local service=$(get_service_name "$feed_type")
    local endpoint=$(get_endpoint "$feed_type")
    local host_url="http://${ip}:${port}${endpoint}"
    
    print_status "Unsubscribing from ${feed_type} feed..."
    
    # Create unsubscription JSON payload
    local unsubscription_json=$(cat <<EOF
{
  "host": "$host_url",
  "service": "$service"
}
EOF
)
    
    print_status "Configuration:"
    echo "  TIS Proxy: $DEFAULT_TIS_HOST"
    echo "  Feed Type: $feed_type"
    echo "  Service: $service"
    echo "  Host URL: $host_url"
    echo ""
    
    print_status "Sending unsubscription request..."
    echo "Payload:"
    echo "$unsubscription_json"
    echo ""
    
    # Prepare authentication
    local auth_header=""
    if [[ -n "$DEFAULT_USERNAME" && -n "$DEFAULT_PASSWORD" ]]; then
        auth_header="-u $DEFAULT_USERNAME:$DEFAULT_PASSWORD"
        print_status "Using authentication with username: $DEFAULT_USERNAME"
    else
        print_warning "No credentials provided - attempting without authentication"
    fi
    
    # Send unsubscription request (using TIS proxy endpoint format)
    local response
    local http_code
    local unsubscribe_url="$DEFAULT_TIS_HOST/unsubscribe"
    
    print_status "Unsubscribe URL: $unsubscribe_url"
    
    response=$(curl -s -w "\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        $auth_header \
        -d "$unsubscription_json" \
        "$unsubscribe_url" 2>&1)
    
    # Extract HTTP status code and response body
    http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    echo "HTTP Status Code: $http_code"
    echo "Response Body: $response_body"
    echo ""
    
    # Check response status
    case "$http_code" in
        200|201|202|204)
            print_success "Successfully unsubscribed from ${feed_type} feed!"
            print_success "TIS proxy will no longer send ${feed_type} data to: $host_url"
            return 0
            ;;
        400)
            print_error "Bad Request - Invalid JSON or parameters for ${feed_type}"
            print_error "Response: $response_body"
            return 1
            ;;
        401)
            print_error "Unauthorized - Check username/password for ${feed_type}"
            return 1
            ;;
        403)
            print_error "Forbidden - Access denied for ${feed_type}"
            return 1
            ;;
        404)
            print_warning "Subscription not found for ${feed_type} (may already be unsubscribed)"
            print_warning "Response: $response_body"
            return 0
            ;;
        409)
            print_warning "Subscription not found for ${feed_type} (already unsubscribed or never subscribed)"
            print_warning "Response: $response_body"
            return 0
            ;;
        500|502|503)
            print_error "Server Error - TIS proxy service issue for ${feed_type}"
            print_error "Response: $response_body"
            return 1
            ;;
        *)
            print_error "Unexpected HTTP status for ${feed_type}: $http_code"
            print_error "Response: $response_body"
            return 1
            ;;
    esac
}

# Function to test endpoint connectivity (optional)
test_endpoint() {
    local ip="$1"
    local port="$2"
    local endpoint="$3"
    local feed_type="$4"
    
    local test_url="http://${ip}:${port}${endpoint}"
    
    print_status "Testing ${feed_type} endpoint: $test_url"
    
    if curl -s --connect-timeout 5 -I "$test_url" > /dev/null 2>&1; then
        print_success "${feed_type} endpoint is reachable"
        return 0
    else
        print_warning "${feed_type} endpoint is not reachable (receiver may be stopped)"
        return 1
    fi
}

# Main execution
main() {
    # Parse arguments
    local ip="$1"
    local port="$2"
    local feed_type="${3:-all}"
    
    # Validate arguments
    if [[ -z "$ip" || -z "$port" ]]; then
        print_error "Missing required arguments"
        echo ""
        show_usage
        exit 1
    fi
    
    # Validate IP address format (basic check)
    if ! [[ "$ip" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
        print_error "Invalid IP address format: $ip"
        exit 1
    fi
    
    # Validate port number
    if ! [[ "$port" =~ ^[0-9]+$ ]] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
        print_error "Invalid port number: $port"
        exit 1
    fi
    
    print_status "TIS Proxy Feed Unsubscription"
    echo "==============================="
    echo ""
    
    # Test TIS proxy connectivity
    print_status "Testing TIS proxy connectivity..."
    if curl -s --connect-timeout 10 -I "$DEFAULT_TIS_HOST" > /dev/null 2>&1; then
        print_success "TIS proxy is reachable"
    else
        print_error "Cannot reach TIS proxy at $DEFAULT_TIS_HOST"
        print_error "Check network connectivity and proxy URL"
        exit 1
    fi
    echo ""
    
    # Handle different feed types
    case "$feed_type" in
        "siri")
            test_endpoint "$ip" "$port" "$(get_endpoint "siri")" "SIRI"
            unsubscribe_from_feed "$ip" "$port" "siri"
            ;;
        "lrgps")
            test_endpoint "$ip" "$port" "$(get_endpoint "lrgps")" "LRGPS"
            unsubscribe_from_feed "$ip" "$port" "lrgps"
            ;;
        "railcomm"|"rail")
            test_endpoint "$ip" "$port" "$(get_endpoint "railcomm")" "Rail Comm"
            unsubscribe_from_feed "$ip" "$port" "railcomm"
            ;;
        "all")
            print_status "Unsubscribing from all feeds..."
            echo ""
            
            local success_count=0
            local total_feeds=3
            
            # Unsubscribe from each feed type
            test_endpoint "$ip" "$port" "$(get_endpoint "siri")" "SIRI"
            if unsubscribe_from_feed "$ip" "$port" "siri"; then
                ((success_count++))
            fi
            echo ""
            
            test_endpoint "$ip" "$port" "$(get_endpoint "lrgps")" "LRGPS"
            if unsubscribe_from_feed "$ip" "$port" "lrgps"; then
                ((success_count++))
            fi
            echo ""
            
            test_endpoint "$ip" "$port" "$(get_endpoint "railcomm")" "Rail Comm"
            if unsubscribe_from_feed "$ip" "$port" "railcomm"; then
                ((success_count++))
            fi
            echo ""
            
            # Summary
            print_status "Unsubscription Summary:"
            print_status "Successfully unsubscribed from $success_count out of $total_feeds feeds"
            
            if [ "$success_count" -eq "$total_feeds" ]; then
                print_success "All feeds unsubscribed successfully!"
            elif [ "$success_count" -gt 0 ]; then
                print_warning "Some feeds failed to unsubscribe (may already be inactive)"
            else
                print_error "All feed unsubscriptions failed"
                exit 1
            fi
            ;;
        *)
            print_error "Unknown feed type: $feed_type"
            print_status "Valid feed types: siri, lrgps, railcomm, rail, all"
            exit 1
            ;;
    esac
    
    echo ""
    print_status "Post-Unsubscription Status:"
    echo "================================"
    echo "1. Check receiver status:"
    echo "   curl http://${ip}:${port}/status"
    echo "   curl http://${ip}:${port}/health"
    echo ""
    echo "2. Verify no new data in Kafka topics:"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri --max-messages 1"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.lrgps --max-messages 1"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.railcomm --max-messages 1"
    echo ""
    echo "3. To re-subscribe, use:"
    echo "   ./scripts/subscribe-to-tisproxy.sh ${ip} ${port} [feed_type]"
    echo ""
    echo "4. Stop HTTP receivers if no longer needed:"
    echo "   ./rtd-control.sh stop receivers"
}

# Check dependencies
if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed"
    print_error "Install with: brew install curl (macOS) or apt-get install curl (Linux)"
    exit 1
fi

# Show help if requested
case "${1:-}" in
    "help"|"-h"|"--help")
        show_usage
        exit 0
        ;;
esac

# Run main function with all arguments
main "$@"