#!/bin/bash

# Enhanced Proxy Subscription Script with Direct Kafka Bridge Support
# Tests both original HTTP receiver and new Direct Kafka Bridge

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Proxy Bridge]${NC} $1"
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
PROXY_SUBSCRIBE_URL="http://10.4.51.37:80/rtd/tli/consumer/tim"
PROXY_UNSUBSCRIBE_URL="http://10.4.51.37:80/rtd/tli/consumer/tim"
PROXY_IP="10.4.51.37"
TTL_SECONDS=90000
USERNAME="rc-proxy-admin"
PASSWORD="aE@jeLAM76zLNqQLeXuu"

# Endpoint configurations
ORIGINAL_HTTP_PORT=8081
BRIDGE_HTTP_PORT=8083
ORIGINAL_ENDPOINT="/rail-comm"
BRIDGE_ENDPOINT="/rail-comm"  # Compatibility endpoint
BRIDGE_KAFKA_ENDPOINT="/kafka/rtd.rail.comm"  # Direct Kafka endpoint

# Auto-detect current IP address (same logic as original)
get_local_ip() {
    # Try multiple methods to get the local IP
    local ip=""
    
    # Method 1: Use route command (most reliable on macOS/Linux)
    if command -v route &> /dev/null; then
        candidate_ip=$(route get default 2>/dev/null | grep interface | awk '{print $2}' | xargs ifconfig 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
        # Skip if this IP is the same as the proxy server IP
        if [[ -n "$candidate_ip" ]] && [[ "$candidate_ip" != "$PROXY_IP" ]]; then
            ip="$candidate_ip"
        fi
    fi
    
    # Method 2: Use ip command (Linux)
    if [[ -z "$ip" ]] && command -v ip &> /dev/null; then
        candidate_ip=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7}' | head -1)
        # Skip if this IP is the same as the proxy server IP
        if [[ -n "$candidate_ip" ]] && [[ "$candidate_ip" != "$PROXY_IP" ]]; then
            ip="$candidate_ip"
        fi
    fi
    
    # Method 3: Use ifconfig and filter for common network interfaces
    if [[ -z "$ip" ]] && command -v ifconfig &> /dev/null; then
        # Look for common interface patterns (en0, eth0, wlan0)
        for interface in en0 eth0 wlan0 en1 eth1 en8; do
            candidate_ip=$(ifconfig $interface 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
            # Skip if this IP is the same as the proxy server IP
            if [[ -n "$candidate_ip" ]] && [[ "$candidate_ip" != "$PROXY_IP" ]]; then
                ip="$candidate_ip"
                break
            fi
        done
    fi
    
    # Method 4: Use hostname command
    if [[ -z "$ip" ]] && command -v hostname &> /dev/null; then
        ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi
    
    # Method 5: Get all IPs and exclude proxy IP (fallback)
    if [[ -z "$ip" ]] && command -v ifconfig &> /dev/null; then
        ip=$(ifconfig 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | grep -v "$PROXY_IP" | head -1)
    fi
    
    echo "$ip"
}

# Get the local IP and construct HOST_URLs
LOCAL_IP=$(get_local_ip)
if [[ -z "$LOCAL_IP" ]]; then
    print_error "Could not determine local IP address"
    print_error "Please set HOST_URL manually or check network connectivity"
    exit 1
fi

# Construct different endpoint URLs
ORIGINAL_HOST_URL="http://${LOCAL_IP}:${ORIGINAL_HTTP_PORT}${ORIGINAL_ENDPOINT}"
BRIDGE_HOST_URL="http://${LOCAL_IP}:${BRIDGE_HTTP_PORT}${BRIDGE_ENDPOINT}"
BRIDGE_KAFKA_URL="http://${LOCAL_IP}:${BRIDGE_HTTP_PORT}${BRIDGE_KAFKA_ENDPOINT}"

print_status "Auto-detected local IP: $LOCAL_IP"
print_info "Available endpoints:"
print_info "  Original HTTP Receiver: $ORIGINAL_HOST_URL"
print_info "  Bridge (compatible):    $BRIDGE_HOST_URL"
print_info "  Bridge (direct Kafka):  $BRIDGE_KAFKA_URL"

# Function to test endpoint connectivity
test_endpoint_connectivity() {
    local url="$1"
    local name="$2"
    
    print_status "Testing connectivity to $name..."
    
    if curl -s --connect-timeout 5 -I "$url" > /dev/null 2>&1; then
        print_success "$name is reachable"
        return 0
    else
        print_warning "$name is not reachable"
        return 1
    fi
}

# Function to send subscription request to specific endpoint
send_subscription_to_endpoint() {
    local host_url="$1"
    local endpoint_name="$2"
    
    print_status "Sending subscription request to $endpoint_name..."
    
    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "host": "$host_url",
  "ttl": $TTL_SECONDS
}
EOF
)
    
    print_status "Configuration:"
    echo "  Proxy URL:     $PROXY_SUBSCRIBE_URL"
    echo "  Host URL:      $host_url"
    echo "  Endpoint:      $endpoint_name"
    echo "  TTL:           $TTL_SECONDS seconds ($(($TTL_SECONDS / 60)) minutes)"
    echo "  Username:      $USERNAME"
    echo
    
    # Send POST request with Basic Auth
    local response
    local http_code
    
    print_status "Sending request..."
    
    response=$(curl -s -w "\\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -u "$USERNAME:$PASSWORD" \
        -d "$json_payload" \
        "$PROXY_SUBSCRIBE_URL" 2>&1)
    
    # Extract HTTP status code (last line) and response body (everything else)
    http_code=$(echo "$response" | tail -n1)
    response_body=$(echo "$response" | sed '$d')
    
    echo "HTTP Status Code: $http_code"
    echo "Response Body:"
    echo "$response_body"
    echo
    
    # Check response status
    case "$http_code" in
        200|201|202)
            print_success "Subscription to $endpoint_name successful!"
            print_success "Host $host_url is now subscribed to proxy"
            return 0
            ;;
        400)
            print_error "Bad Request - Invalid JSON or parameters"
            return 1
            ;;
        401)
            print_error "Unauthorized - Check username/password"
            return 1
            ;;
        403)
            print_error "Forbidden - Access denied"
            return 1
            ;;
        404)
            print_error "Not Found - Check proxy URL"
            return 1
            ;;
        500|502|503)
            print_error "Server Error - Proxy service issue"
            return 1
            ;;
        *)
            print_error "Unexpected HTTP status: $http_code"
            return 1
            ;;
    esac
}

# Function to test all endpoints
test_all_endpoints() {
    print_status "Testing all available endpoints..."
    echo
    
    # Test original HTTP receiver
    if test_endpoint_connectivity "$ORIGINAL_HOST_URL" "Original HTTP Receiver"; then
        print_info "✓ Original HTTP Receiver (port $ORIGINAL_HTTP_PORT) is available"
    else
        print_info "✗ Original HTTP Receiver (port $ORIGINAL_HTTP_PORT) is not running"
    fi
    
    # Test bridge compatibility endpoint
    if test_endpoint_connectivity "$BRIDGE_HOST_URL" "Bridge (compatible)"; then
        print_info "✓ Direct Kafka Bridge (port $BRIDGE_HTTP_PORT) is available"
    else
        print_info "✗ Direct Kafka Bridge (port $BRIDGE_HTTP_PORT) is not running"
    fi
    
    # Test bridge Kafka endpoint
    if test_endpoint_connectivity "$BRIDGE_KAFKA_URL" "Bridge (Kafka)"; then
        print_info "✓ Direct Kafka endpoint is available"
    else
        print_info "✗ Direct Kafka endpoint is not available"
    fi
}

# Function to benchmark endpoints
benchmark_endpoints() {
    print_status "Benchmarking available endpoints..."
    
    local test_payload='{"test": true, "timestamp": "'$(date -Iseconds)'", "message": "benchmark test"}'
    
    # Test original HTTP receiver
    if test_endpoint_connectivity "$ORIGINAL_HOST_URL" "Original Receiver"; then
        print_status "Benchmarking Original HTTP Receiver..."
        time curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$test_payload" \
            "$ORIGINAL_HOST_URL" > /dev/null
    fi
    
    # Test bridge compatibility endpoint
    if test_endpoint_connectivity "$BRIDGE_HOST_URL" "Bridge Compatible"; then
        print_status "Benchmarking Bridge (Compatible)..."
        time curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$test_payload" \
            "$BRIDGE_HOST_URL" > /dev/null
    fi
    
    # Test bridge Kafka endpoint
    if test_endpoint_connectivity "$BRIDGE_KAFKA_URL" "Bridge Kafka"; then
        print_status "Benchmarking Bridge (Direct Kafka)..."
        time curl -s -X POST \
            -H "Content-Type: application/json" \
            -d "$test_payload" \
            "$BRIDGE_KAFKA_URL" > /dev/null
    fi
}

# Function to send unsubscription request
send_unsubscription() {
    local host_url="$1"
    local endpoint_name="$2"
    
    print_status "Sending unsubscription request for $endpoint_name..."
    
    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "host": "$host_url"
}
EOF
)
    
    # Send POST request
    local response
    local http_code
    
    response=$(curl -s -w "\\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -u "$USERNAME:$PASSWORD" \
        -d "$json_payload" \
        "$PROXY_UNSUBSCRIBE_URL" 2>&1)
    
    http_code=$(echo "$response" | tail -n1)
    response_body=$(echo "$response" | sed '$d')
    
    echo "HTTP Status Code: $http_code"
    echo "Response Body: $response_body"
    
    case "$http_code" in
        200|201|202)
            print_success "Unsubscription from $endpoint_name successful!"
            return 0
            ;;
        *)
            print_error "Unsubscription failed with status: $http_code"
            return 1
            ;;
    esac
}

# Main execution
main() {
    case "${1:-test}" in
        "original"|"old")
            print_status "Using Original HTTP Receiver endpoint"
            send_subscription_to_endpoint "$ORIGINAL_HOST_URL" "Original HTTP Receiver"
            ;;
        "bridge"|"new")
            print_status "Using Direct Kafka Bridge (compatible endpoint)"
            send_subscription_to_endpoint "$BRIDGE_HOST_URL" "Direct Kafka Bridge"
            ;;
        "kafka"|"direct")
            print_status "Using Direct Kafka Bridge (direct Kafka endpoint)"
            send_subscription_to_endpoint "$BRIDGE_KAFKA_URL" "Direct Kafka Bridge (Kafka)"
            ;;
        "test"|"check")
            test_all_endpoints
            ;;
        "benchmark"|"perf")
            benchmark_endpoints
            ;;
        "unsubscribe")
            # Unsubscribe from all possible endpoints
            print_status "Unsubscribing from all endpoints..."
            send_unsubscription "$ORIGINAL_HOST_URL" "Original HTTP Receiver"
            send_unsubscription "$BRIDGE_HOST_URL" "Direct Kafka Bridge"
            send_unsubscription "$BRIDGE_KAFKA_URL" "Direct Kafka Bridge (Kafka)"
            ;;
        "help"|\"-h\"|\"--help\")
            echo "Enhanced Proxy Subscription Script with Direct Kafka Bridge Support"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  original    Subscribe using Original HTTP Receiver (port $ORIGINAL_HTTP_PORT)"
            echo "  bridge      Subscribe using Direct Kafka Bridge compatible endpoint (port $BRIDGE_HTTP_PORT)"
            echo "  kafka       Subscribe using Direct Kafka Bridge direct endpoint (port $BRIDGE_HTTP_PORT)"
            echo "  test        Test connectivity to all endpoints"
            echo "  benchmark   Benchmark performance of all available endpoints"
            echo "  unsubscribe Unsubscribe from all endpoints"
            echo "  help        Show this help message"
            echo
            echo "Endpoints:"
            echo "  Original:     $ORIGINAL_HOST_URL"
            echo "  Bridge:       $BRIDGE_HOST_URL"
            echo "  Direct Kafka: $BRIDGE_KAFKA_URL"
            echo
            echo "Examples:"
            echo "  $0 test         # Test all endpoints"
            echo "  $0 bridge       # Subscribe using Direct Kafka Bridge"
            echo "  $0 benchmark    # Compare performance"
            echo "  $0 unsubscribe  # Unsubscribe from all"
            ;;
        *)
            print_error "Unknown command: $1"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Check dependencies
if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed"
    print_error "Install with: brew install curl (macOS) or apt-get install curl (Linux)"
    exit 1
fi

# Run main function with all arguments
main "$@"