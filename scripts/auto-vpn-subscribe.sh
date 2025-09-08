#!/bin/bash

# Auto VPN Subscribe Script
# Automatically detects VPN status and subscribes to SIRI feeds with appropriate IP

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Auto Subscribe]${NC} $1"
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

# Get script directory
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Check for required scripts
if [[ ! -f "$SCRIPT_DIR/detect-vpn-ip.sh" ]]; then
    print_error "detect-vpn-ip.sh not found in $SCRIPT_DIR"
    exit 1
fi

if [[ ! -f "$SCRIPT_DIR/bus-siri-subscribe.sh" ]]; then
    print_error "bus-siri-subscribe.sh not found in $SCRIPT_DIR"
    exit 1
fi

# Function to perform subscription with VPN detection
subscribe_with_vpn_detection() {
    print_status "Detecting VPN status..."
    
    # Run VPN detection
    "$SCRIPT_DIR/detect-vpn-ip.sh" detect
    
    # Source the environment variables
    source <("$SCRIPT_DIR/detect-vpn-ip.sh" export)
    
    echo ""
    print_status "Subscribing to Bus SIRI feed..."
    
    # Run bus-siri-subscribe which will now use the detected IP
    "$SCRIPT_DIR/bus-siri-subscribe.sh"
    
    return $?
}

# Function to monitor and auto-resubscribe on VPN changes
monitor_and_resubscribe() {
    print_status "Starting VPN monitor with auto-resubscription..."
    print_info "Will automatically resubscribe when VPN status changes"
    print_info "Press Ctrl+C to stop monitoring"
    echo ""
    
    local last_ip=""
    local current_ip=""
    
    while true; do
        # Get current IP
        source <("$SCRIPT_DIR/detect-vpn-ip.sh" export 2>/dev/null)
        current_ip="$SIRI_HOST_IP"
        
        # Check if IP changed
        if [[ "$current_ip" != "$last_ip" ]]; then
            if [[ -n "$last_ip" ]]; then
                echo ""
                print_warning "Network change detected!"
                echo "  Previous IP: $last_ip"
                echo "  Current IP:  $current_ip"
                echo ""
            fi
            
            # Resubscribe with new IP
            print_status "Subscribing with new IP: $current_ip"
            subscribe_with_vpn_detection
            
            last_ip="$current_ip"
        fi
        
        # Wait before next check
        sleep 10
    done
}

# Function to test VPN switching
test_vpn_switching() {
    print_status "Testing VPN IP switching..."
    echo ""
    
    # Test without VPN (assuming current state)
    print_info "Current network status:"
    "$SCRIPT_DIR/detect-vpn-ip.sh" detect
    
    echo ""
    print_info "To test VPN switching:"
    echo "  1. Connect to VPN if not connected"
    echo "  2. Run: $0 subscribe"
    echo "  3. Disconnect from VPN"
    echo "  4. Run: $0 subscribe"
    echo ""
    print_info "Or use monitor mode for automatic switching:"
    echo "  $0 monitor"
}

# Function to update .env with current IP
update_env_with_vpn() {
    print_status "Updating .env with current network IP..."
    "$SCRIPT_DIR/detect-vpn-ip.sh" update-env
}

# Main execution
case "${1:-subscribe}" in
    "subscribe")
        subscribe_with_vpn_detection
        ;;
    "monitor")
        monitor_and_resubscribe
        ;;
    "test")
        test_vpn_switching
        ;;
    "update-env")
        update_env_with_vpn
        ;;
    "status")
        "$SCRIPT_DIR/detect-vpn-ip.sh" detect
        ;;
    "help"|"-h"|"--help")
        echo "Auto VPN Subscribe Script"
        echo ""
        echo "Usage: $0 [COMMAND]"
        echo ""
        echo "Commands:"
        echo "  subscribe    Subscribe with auto-detected IP (default)"
        echo "  monitor      Monitor VPN changes and auto-resubscribe"
        echo "  test         Test VPN switching setup"
        echo "  update-env   Update .env file with current IP"
        echo "  status       Show current VPN/network status"
        echo "  help         Show this help message"
        echo ""
        echo "Features:"
        echo "  - Automatically detects VPN connection"
        echo "  - Uses VPN IP when connected"
        echo "  - Falls back to regular network IP when VPN is off"
        echo "  - Monitor mode auto-resubscribes on VPN changes"
        echo ""
        echo "Examples:"
        echo "  $0                  # Subscribe with auto-detected IP"
        echo "  $0 monitor          # Monitor and auto-resubscribe"
        echo "  $0 status           # Check current VPN status"
        ;;
    *)
        print_error "Unknown command: $1"
        print_info "Run '$0 help' for usage information"
        exit 1
        ;;
esac