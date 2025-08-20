#!/bin/bash

# RTD TIS Proxy Subscription Status Script
# Shows current subscriptions, POST messages sent, and localhost endpoints

set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Load environment variables if available
if [[ -f ".env" ]]; then
    set -a  # automatically export all variables
    source .env
    set +a  # stop automatic export
fi

# Configuration
TIS_PROXY_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
BUS_RECEIVER_PORT="8082"
RAIL_RECEIVER_PORT="8081"
LOCAL_IP="${LOCAL_IP:-172.16.23.5}"
BUS_SIRI_PORT="${BUS_SIRI_PORT:-880}"
RAIL_COMM_PORT="${RAIL_COMM_PORT:-881}"


print_header() {
    echo -e "${BOLD}${BLUE}================================================${NC}"
    echo -e "${BOLD}${BLUE}    RTD TIS Proxy Subscription Status${NC}"
    echo -e "${BOLD}${BLUE}================================================${NC}"
    echo ""
    echo -e "${CYAN}TIS Proxy Host:${NC} $TIS_PROXY_HOST"
    echo -e "${CYAN}Authentication:${NC} ${TIS_PROXY_USERNAME:+Configured}${TIS_PROXY_USERNAME:-Not configured}"
    echo ""
}

print_section() {
    local title="$1"
    echo -e "${BOLD}${YELLOW}--- $title ---${NC}"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

# Function to check if a service is running on a port
check_service() {
    local port="$1"
    local service_name="$2"
    
    if curl -s "http://localhost:$port/health" >/dev/null 2>&1; then
        print_success "$service_name is running on port $port"
        return 0
    else
        print_error "$service_name is not running on port $port"
        return 1
    fi
}

# Function to get service status
get_service_status() {
    local port="$1"
    local service_name="$2"
    
    if check_service "$port" "$service_name"; then
        echo ""
        echo -e "${CYAN}Health Check:${NC}"
        curl -s "http://localhost:$port/health" | jq '.' 2>/dev/null || curl -s "http://localhost:$port/health"
        
        echo ""
        echo -e "${CYAN}Subscription Status:${NC}"
        if curl -s "http://localhost:$port/subscribe" >/dev/null 2>&1; then
            curl -s "http://localhost:$port/subscribe" | jq '.' 2>/dev/null || curl -s "http://localhost:$port/subscribe"
        else
            print_warning "No subscription endpoint available"
        fi
        
        echo ""
        echo -e "${CYAN}Service Status:${NC}"
        if curl -s "http://localhost:$port/status" >/dev/null 2>&1; then
            curl -s "http://localhost:$port/status" | jq '.' 2>/dev/null || curl -s "http://localhost:$port/status"
        else
            print_warning "No status endpoint available"
        fi
    fi
}

# Function to show what POST message would be sent for subscription
show_subscription_message() {
    local service="$1"
    local port="$2"
    local host="${3:-$TIS_PROXY_HOST}"
    local ttl="${4:-90000}"
    
    echo -e "${CYAN}Subscription POST Message for $service:${NC}"
    echo -e "${YELLOW}URL:${NC} $host/subscribe"
    echo -e "${YELLOW}Method:${NC} POST"
    echo -e "${YELLOW}Headers:${NC}"
    echo "  Content-Type: application/json"
    if [[ -n "${TIS_PROXY_USERNAME:-}" ]]; then
        local auth_header=$(echo -n "$TIS_PROXY_USERNAME:$TIS_PROXY_PASSWORD" | base64)
        echo "  Authorization: Basic $auth_header"
    else
        echo "  (No authentication configured)"
    fi
    
    echo -e "${YELLOW}Body:${NC}"
    cat <<EOF | jq '.' 2>/dev/null || cat
{
  "host": "http://$LOCAL_IP:$port",
  "service": "$service",
  "ttl": $ttl
}
EOF
    
    echo -e "${CYAN}Data will be forwarded to:${NC} http://$LOCAL_IP:$port"
}

# Function to get TIS proxy subscriptions
get_tis_proxy_subscriptions() {
    print_section "TIS Proxy Active Subscriptions"
    
    local auth_args=""
    if [[ -n "${TIS_PROXY_USERNAME:-}" ]] && [[ -n "${TIS_PROXY_PASSWORD:-}" ]]; then
        auth_args="-u $TIS_PROXY_USERNAME:$TIS_PROXY_PASSWORD"
    fi
    
    if curl -s $auth_args "$TIS_PROXY_HOST/subscriptions" >/dev/null 2>&1; then
        echo -e "${CYAN}Active subscriptions from TIS Proxy:${NC}"
        curl -s $auth_args "$TIS_PROXY_HOST/subscriptions" | jq '.' 2>/dev/null || curl -s $auth_args "$TIS_PROXY_HOST/subscriptions"
    else
        print_error "Could not connect to TIS Proxy at $TIS_PROXY_HOST"
        print_info "Make sure TIS Proxy is running and credentials are correct"
    fi
}

# Function to test connectivity
test_connectivity() {
    print_section "Connectivity Test"
    
    # Test TIS Proxy
    if curl -s --connect-timeout 5 "$TIS_PROXY_HOST/health" >/dev/null 2>&1; then
        print_success "TIS Proxy is reachable at $TIS_PROXY_HOST"
    else
        print_error "Cannot reach TIS Proxy at $TIS_PROXY_HOST"
    fi
    
    # Test local services
    if curl -s --connect-timeout 2 "http://localhost:$BUS_RECEIVER_PORT/health" >/dev/null 2>&1; then
        print_success "Bus SIRI Receiver is reachable at localhost:$BUS_RECEIVER_PORT"
    else
        print_warning "Bus SIRI Receiver not reachable at localhost:$BUS_RECEIVER_PORT"
    fi
    
    if curl -s --connect-timeout 2 "http://localhost:$RAIL_RECEIVER_PORT/health" >/dev/null 2>&1; then
        print_success "Rail Communication Receiver is reachable at localhost:$RAIL_RECEIVER_PORT"
    else
        print_warning "Rail Communication Receiver not reachable at localhost:$RAIL_RECEIVER_PORT"
    fi
}

# Main execution
main() {
    print_header
    
    case "${1:-all}" in
        "tis"|"proxy")
            get_tis_proxy_subscriptions
            ;;
        "bus"|"siri")
            print_section "Bus SIRI Service (Port $BUS_RECEIVER_PORT)"
            get_service_status "$BUS_RECEIVER_PORT" "Bus SIRI Receiver"
            echo ""
            show_subscription_message "siri" "$BUS_SIRI_PORT"
            ;;
        "rail"|"railcomm")
            print_section "Rail Communication Service (Port $RAIL_RECEIVER_PORT)"
            get_service_status "$RAIL_RECEIVER_PORT" "Rail Communication Receiver"
            echo ""
            show_subscription_message "railcomm" "$RAIL_COMM_PORT"
            ;;
        "test"|"connectivity")
            test_connectivity
            ;;
        "messages"|"post")
            print_section "Subscription POST Messages"
            echo ""
            show_subscription_message "siri" "$BUS_SIRI_PORT"
            echo ""
            echo ""
            show_subscription_message "railcomm" "$RAIL_COMM_PORT"
            ;;
        "all"|*)
            test_connectivity
            echo ""
            
            print_section "Local Service Status"
            echo ""
            print_info "Bus SIRI Service (Port $BUS_RECEIVER_PORT):"
            get_service_status "$BUS_RECEIVER_PORT" "Bus SIRI Receiver"
            echo ""
            echo ""
            print_info "Rail Communication Service (Port $RAIL_RECEIVER_PORT):"
            get_service_status "$RAIL_RECEIVER_PORT" "Rail Communication Receiver"
            echo ""
            echo ""
            
            get_tis_proxy_subscriptions
            echo ""
            
            print_section "Subscription POST Messages"
            echo ""
            show_subscription_message "siri" "$BUS_SIRI_PORT"
            echo ""
            echo ""
            show_subscription_message "railcomm" "$RAIL_COMM_PORT"
            ;;
    esac
    
    echo ""
    print_section "Usage"
    echo "Commands:"
    echo "  $0                    # Show all information (default)"
    echo "  $0 all               # Show all information"
    echo "  $0 tis               # Show TIS proxy subscriptions only"
    echo "  $0 bus               # Show bus SIRI service status only"
    echo "  $0 rail              # Show rail communication service status only"
    echo "  $0 test              # Test connectivity only"
    echo "  $0 messages          # Show subscription POST messages only"
    echo ""
    echo "Environment Variables:"
    echo "  TIS_PROXY_HOST       # TIS Proxy URL (default: http://tisproxy.rtd-denver.com)"
    echo "  TIS_PROXY_USERNAME   # Authentication username"
    echo "  TIS_PROXY_PASSWORD   # Authentication password"
}

# Check dependencies
if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    print_warning "jq is not installed - JSON formatting may not work correctly"
    print_info "Install with: brew install jq (macOS) or apt-get install jq (Linux)"
fi

# Run main function with all arguments
main "$@"