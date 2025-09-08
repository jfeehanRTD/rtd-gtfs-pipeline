#!/bin/bash

# TIS Proxy Subscription Script
# Simple script to subscribe to SIRI, LRGPS, and Rail Comm feeds using IP address and port as arguments
# Usage: ./subscribe-to-tisproxy.sh <ip> <port> [feed_type]

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[TIS Subscribe]${NC} $1"
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
DEFAULT_TTL="${TIS_PROXY_TTL:-90000}"
DEFAULT_USERNAME="${TIS_PROXY_USERNAME:-}"
DEFAULT_PASSWORD="${TIS_PROXY_PASSWORD:-}"

# Feed type configurations
declare -A FEED_SERVICES
FEED_SERVICES[siri]="siri"
FEED_SERVICES[lrgps]="lrgps"
FEED_SERVICES[railcomm]="railcomm"
FEED_SERVICES[rail]="railcomm"  # alias

declare -A FEED_ENDPOINTS
FEED_ENDPOINTS[siri]="/bus-siri"
FEED_ENDPOINTS[lrgps]="/lrgps"
FEED_ENDPOINTS[railcomm]="/rail-comm"
FEED_ENDPOINTS[rail]="/rail-comm"  # alias

# Usage function
show_usage() {
    echo "TIS Proxy Subscription Script"
    echo ""
    echo "Usage: $0 <ip> <port> [feed_type]"
    echo ""
    echo "Arguments:"
    echo "  ip          IP address where TIS proxy should send data"
    echo "  port        Port number for the receiver"
    echo "  feed_type   Type of feed to subscribe to (default: all)"
    echo ""
    echo "Feed Types:"
    echo "  siri        Bus SIRI feed"
    echo "  lrgps       Light Rail GPS feed"
    echo "  railcomm    Rail Communication feed"
    echo "  rail        Alias for railcomm"
    echo "  all         Subscribe to all feeds (default)"
    echo ""
    echo "Examples:"
    echo "  $0 192.168.1.100 8082 siri      # Subscribe to SIRI feed only"
    echo "  $0 192.168.1.100 8083 lrgps     # Subscribe to LRGPS feed only"
    echo "  $0 192.168.1.100 8081 railcomm  # Subscribe to Rail Comm feed only"
    echo "  $0 192.168.1.100 8080 all       # Subscribe to all feeds"
    echo "  $0 192.168.1.100 8080           # Subscribe to all feeds (default)"
    echo ""
    echo "Environment Variables:"
    echo "  TIS_PROXY_HOST      TIS proxy host URL (default: http://tisproxy.rtd-denver.com)"
    echo "  TIS_PROXY_USERNAME  Username for authentication"
    echo "  TIS_PROXY_PASSWORD  Password for authentication"
    echo "  TIS_PROXY_TTL       Subscription TTL in milliseconds (default: 90000)"
    echo ""
    echo "Note: Credentials can also be loaded from .env file"
}

# Function to subscribe to a specific feed
subscribe_to_feed() {
    local ip="$1"
    local port="$2" 
    local feed_type="$3"
    
    local service="${FEED_SERVICES[$feed_type]}"
    local endpoint="${FEED_ENDPOINTS[$feed_type]}"
    local host_url="http://${ip}:${port}${endpoint}"
    
    print_status "Subscribing to ${feed_type} feed..."
    
    # Create subscription JSON payload
    local subscription_json=$(cat <<EOF
{
  "host": "$host_url",
  "service": "$service",
  "ttl": $DEFAULT_TTL
}
EOF
)
    
    print_status "Configuration:"
    echo "  TIS Proxy: $DEFAULT_TIS_HOST"
    echo "  Feed Type: $feed_type"
    echo "  Service: $service"
    echo "  Host URL: $host_url"
    echo "  TTL: $DEFAULT_TTL ms"
    echo ""
    
    print_status "Sending subscription request..."
    echo "Payload:"
    echo "$subscription_json"
    echo ""
    
    # Prepare authentication
    local auth_header=""
    if [[ -n "$DEFAULT_USERNAME" && -n "$DEFAULT_PASSWORD" ]]; then
        auth_header="-u $DEFAULT_USERNAME:$DEFAULT_PASSWORD"
        print_status "Using authentication with username: $DEFAULT_USERNAME"
    else
        print_warning "No credentials provided - attempting without authentication"
    fi
    
    # Send subscription request
    local response
    local http_code
    
    response=$(curl -s -w "\n%{http_code}" \
        -X POST \
        $auth_header \
        -H "Content-Type: application/json" \
        -d "$subscription_json" \
        "$DEFAULT_TIS_HOST/subscribe" 2>&1)
    
    # Extract HTTP status code and response body
    http_code=$(echo "$response" | tail -n1)
    local response_body=$(echo "$response" | sed '$d')
    
    echo "HTTP Status Code: $http_code"
    echo "Response Body: $response_body"
    echo ""
    
    # Check response status
    case "$http_code" in
        200|201|202)
            print_success "Successfully subscribed to ${feed_type} feed!"
            print_success "TIS proxy will send ${feed_type} data to: $host_url"
            print_status "Subscription expires in $DEFAULT_TTL ms ($(($DEFAULT_TTL / 60000)) minutes)"
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
            print_error "Not Found - Check TIS proxy URL for ${feed_type}"
            return 1
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

# Function to test endpoint connectivity
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
        print_warning "${feed_type} endpoint is not reachable (this may be normal if service isn't running yet)"
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
    
    print_status "TIS Proxy Feed Subscription"
    echo "=============================="
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
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[siri]}" "SIRI"
            subscribe_to_feed "$ip" "$port" "siri"
            ;;
        "lrgps")
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[lrgps]}" "LRGPS"
            subscribe_to_feed "$ip" "$port" "lrgps"
            ;;
        "railcomm"|"rail")
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[railcomm]}" "Rail Comm"
            subscribe_to_feed "$ip" "$port" "railcomm"
            ;;
        "all")
            print_status "Subscribing to all feeds..."
            echo ""
            
            local success_count=0
            local total_feeds=3
            
            # Subscribe to each feed type
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[siri]}" "SIRI"
            if subscribe_to_feed "$ip" "$port" "siri"; then
                ((success_count++))
            fi
            echo ""
            
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[lrgps]}" "LRGPS"
            if subscribe_to_feed "$ip" "$port" "lrgps"; then
                ((success_count++))
            fi
            echo ""
            
            test_endpoint "$ip" "$port" "${FEED_ENDPOINTS[railcomm]}" "Rail Comm"
            if subscribe_to_feed "$ip" "$port" "railcomm"; then
                ((success_count++))
            fi
            echo ""
            
            # Summary
            print_status "Subscription Summary:"
            print_status "Successfully subscribed to $success_count out of $total_feeds feeds"
            
            if [ "$success_count" -eq "$total_feeds" ]; then
                print_success "All feeds subscribed successfully!"
            elif [ "$success_count" -gt 0 ]; then
                print_warning "Some feeds failed to subscribe"
            else
                print_error "All feed subscriptions failed"
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
    print_status "Monitoring Instructions:"
    echo "================================"
    echo "1. Start the appropriate HTTP receivers before subscribing:"
    echo "   ./rtd-control.sh bus-comm receiver    # For SIRI (port 8082)"
    echo "   ./rtd-control.sh lrgps receiver       # For LRGPS (port 8083)" 
    echo "   ./rtd-control.sh rail-comm receiver   # For Rail Comm (port 8081)"
    echo ""
    echo "2. Monitor Kafka topics for incoming data:"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri    # SIRI data"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.lrgps       # LRGPS data"
    echo "   ./scripts/kafka-console-consumer.sh --topic rtd.railcomm    # Rail Comm data"
    echo ""
    echo "3. Check receiver status:"
    echo "   curl http://${ip}:${port}/status"
    echo "   curl http://${ip}:${port}/health"
    echo ""
    echo "4. Subscription expires in $DEFAULT_TTL ms ($(($DEFAULT_TTL / 60000)) minutes)"
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