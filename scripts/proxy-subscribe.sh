#!/bin/bash

# Proxy Subscription Script
# Sends a JSON POST request to subscribe to the RTD proxy service

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Proxy Subscribe]${NC} $1"
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

# Configuration
PROXY_SUBSCRIBE_URL="http://10.4.51.37:80/rtd/tli/consumer/tim"
PROXY_UNSUBSCRIBE_URL="http://10.4.51.37:80/rtd/tli/consumer/tim"
TTL_SECONDS=90000
USERNAME="rc-proxy-admin"
PASSWORD="aE@jeLAM76zLNqQLeXuu"

# Auto-detect current IP address
get_local_ip() {
    # Try multiple methods to get the local IP
    local ip=""
    
    # Method 1: Use route command (most reliable on macOS/Linux)
    if command -v route &> /dev/null; then
        ip=$(route get default 2>/dev/null | grep interface | awk '{print $2}' | xargs ifconfig 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
    fi
    
    # Method 2: Use ip command (Linux)
    if [[ -z "$ip" ]] && command -v ip &> /dev/null; then
        ip=$(ip route get 8.8.8.8 2>/dev/null | awk '{print $7}' | head -1)
    fi
    
    # Method 3: Use ifconfig and filter for common network interfaces
    if [[ -z "$ip" ]] && command -v ifconfig &> /dev/null; then
        # Look for common interface patterns (en0, eth0, wlan0)
        for interface in en0 eth0 wlan0 en1 eth1; do
            ip=$(ifconfig $interface 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
            [[ -n "$ip" ]] && break
        done
    fi
    
    # Method 4: Use hostname command
    if [[ -z "$ip" ]] && command -v hostname &> /dev/null; then
        ip=$(hostname -I 2>/dev/null | awk '{print $1}')
    fi
    
    # Method 5: Use external service as fallback
    if [[ -z "$ip" ]] && command -v curl &> /dev/null; then
        ip=$(curl -s --connect-timeout 5 ifconfig.me 2>/dev/null || curl -s --connect-timeout 5 ipinfo.io/ip 2>/dev/null)
    fi
    
    echo "$ip"
}

# Get the local IP and construct HOST_URL
LOCAL_IP=$(get_local_ip)
if [[ -z "$LOCAL_IP" ]]; then
    print_error "Could not determine local IP address"
    print_error "Please set HOST_URL manually or check network connectivity"
    exit 1
fi

HOST_URL="http://${LOCAL_IP}:8081/rail-comm"

print_status "Auto-detected local IP: $LOCAL_IP"
print_status "Host URL will be: $HOST_URL"

# Function to send subscription request
send_subscription() {
    print_status "Sending proxy subscription request..."
    
    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "host": "$HOST_URL",
  "ttl": $TTL_SECONDS
}
EOF
)
    
    print_status "Configuration:"
    echo "  Proxy URL: $PROXY_SUBSCRIBE_URL"
    echo "  Host URL:  $HOST_URL"
    echo "  TTL:       $TTL_SECONDS seconds ($(($TTL_SECONDS / 60)) minutes)"
    echo "  Username:  $USERNAME"
    echo
    
    # Send POST request with Basic Auth
    local response
    local http_code
    
    print_status "Sending request..."
    
    response=$(curl -s -w "\n%{http_code}" \
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
            print_success "Subscription request successful!"
            print_success "Host $HOST_URL is now subscribed to proxy $PROXY_SUBSCRIBE_URL"
            print_status "Subscription expires in $TTL_SECONDS seconds ($(($TTL_SECONDS / 60)) minutes)"
            ;;
        400)
            print_error "Bad Request - Invalid JSON or parameters"
            print_error "Response: $response_body"
            return 1
            ;;
        401)
            print_error "Unauthorized - Check username/password"
            print_error "Username: $USERNAME"
            return 1
            ;;
        403)
            print_error "Forbidden - Access denied"
            return 1
            ;;
        404)
            print_error "Not Found - Check proxy URL"
            print_error "URL: $PROXY_SUBSCRIBE_URL"
            return 1
            ;;
        500|502|503)
            print_error "Server Error - Proxy service issue"
            print_error "Response: $response_body"
            return 1
            ;;
        *)
            print_error "Unexpected HTTP status: $http_code"
            print_error "Response: $response_body"
            return 1
            ;;
    esac
}

# Function to send unsubscription request
send_unsubscription() {
    print_status "Sending proxy unsubscription request..."
    
    # Create JSON payload
    local json_payload=$(cat <<EOF
{
  "host": "$HOST_URL"
}
EOF
)
    
    print_status "Configuration:"
    echo "  Proxy URL: $PROXY_UNSUBSCRIBE_URL"
    echo "  Host URL:  $HOST_URL"
    echo "  Username:  $USERNAME"
    echo
    
    # Send POST request with Basic Auth
    local response
    local http_code
    
    print_status "Sending unsubscribe request..."
    
    response=$(curl -s -w "\\n%{http_code}" \
        -X POST \
        -H "Content-Type: application/json" \
        -u "$USERNAME:$PASSWORD" \
        -d "$json_payload" \
        "$PROXY_UNSUBSCRIBE_URL" 2>&1)
    
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
            print_success "Unsubscription request successful!"
            print_success "Host $HOST_URL has been unsubscribed from proxy $PROXY_UNSUBSCRIBE_URL"
            ;;
        400)
            print_error "Bad Request - Invalid JSON or parameters"
            print_error "Response: $response_body"
            return 1
            ;;
        401)
            print_error "Unauthorized - Check username/password"
            print_error "Username: $USERNAME"
            return 1
            ;;
        403)
            print_error "Forbidden - Access denied"
            return 1
            ;;
        404)
            print_error "Not Found - Check proxy URL or subscription doesn't exist"
            print_error "URL: $PROXY_UNSUBSCRIBE_URL"
            return 1
            ;;
        500|502|503)
            print_error "Server Error - Proxy service issue"
            print_error "Response: $response_body"
            return 1
            ;;
        *)
            print_error "Unexpected HTTP status: $http_code"
            print_error "Response: $response_body"
            return 1
            ;;
    esac
}

# Function to test connectivity
test_connectivity() {
    print_status "Testing connectivity to proxy..."
    
    if curl -s --connect-timeout 10 -I "$PROXY_SUBSCRIBE_URL" > /dev/null 2>&1; then
        print_success "Proxy server is reachable"
    else
        print_error "Cannot reach proxy server at $PROXY_SUBSCRIBE_URL"
        print_error "Check network connectivity and proxy URL"
        return 1
    fi
    
    print_status "Testing connectivity to host..."
    
    if curl -s --connect-timeout 10 -I "$HOST_URL" > /dev/null 2>&1; then
        print_success "Host server is reachable"
    else
        print_warning "Cannot reach host server at $HOST_URL"
        print_warning "This may be normal if the host is behind a firewall"
    fi
}

# Function to show current configuration
show_config() {
    print_status "Current Configuration:"
    echo "  Proxy URL:    $PROXY_SUBSCRIBE_URL"
    echo "  Host URL:     $HOST_URL" 
    echo "  TTL:          $TTL_SECONDS seconds ($(($TTL_SECONDS / 60)) minutes)"
    echo "  Username:     $USERNAME"
    echo "  Password:     [HIDDEN]"
    echo
}

# Function to validate JSON payload
validate_json() {
    local json_payload=$(cat <<EOF
{
  "host": "$HOST_URL",
  "ttl": $TTL_SECONDS
}
EOF
)
    
    print_status "JSON Payload:"
    echo "$json_payload" | jq . 2>/dev/null || {
        print_warning "jq not available - cannot validate JSON format"
        echo "$json_payload"
    }
    echo
}

# Main execution
main() {
    case "${1:-send}" in
        "send"|"subscribe")
            show_config
            test_connectivity
            validate_json
            send_subscription
            ;;
        "unsubscribe")
            show_config
            test_connectivity
            send_unsubscription
            ;;
        "test"|"connectivity")
            test_connectivity
            ;;
        "config"|"show")
            show_config
            validate_json
            ;;
        "help"|"-h"|"--help")
            echo "Proxy Subscription Script"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  send        Send subscription request (default)"
            echo "  subscribe   Alias for send"
            echo "  unsubscribe Unsubscribe from proxy feed"
            echo "  test        Test connectivity only" 
            echo "  config      Show current configuration"
            echo "  help        Show this help message"
            echo
            echo "Configuration:"
            echo "  Proxy:      $PROXY_SUBSCRIBE_URL"
            echo "  Host:       http://[AUTO-DETECTED-IP]:8081/rail-comm"
            echo "  TTL:        $TTL_SECONDS seconds"
            echo "  Auto-IP:    Automatically detects your current local IP address"
            echo "  Endpoint:   Kafka producer for RTD rail communication data"
            echo
            echo "Examples:"
            echo "  $0                    # Send subscription request"
            echo "  $0 send               # Send subscription request"
            echo "  $0 unsubscribe        # Unsubscribe from proxy feed"
            echo "  $0 test               # Test connectivity"
            echo "  $0 config             # Show configuration"
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