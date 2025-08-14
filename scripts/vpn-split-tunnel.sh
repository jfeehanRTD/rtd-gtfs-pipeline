#!/bin/bash

# VPN Split Tunneling Setup Script
# Routes work traffic through VPN, keeps Claude traffic direct

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[Split Tunnel]${NC} $1"
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

# Configuration - Update these for your work network
WORK_NETWORKS=(
    "10.0.0.0/8"        # RTD internal network
    "172.16.0.0/12"     # Private network range
    "192.168.0.0/16"    # Private network range
)

# Domains to exclude from VPN (keep on regular internet)
EXCLUDE_DOMAINS=(
    "api.anthropic.com"
    "claude.ai"
    "auth.anthropic.com"
    "*.anthropic.com"
)

# Get VPN interface name
get_vpn_interface() {
    # Common VPN interface names
    for iface in utun0 utun1 utun2 ppp0 tun0; do
        if ifconfig $iface >/dev/null 2>&1; then
            echo $iface
            return 0
        fi
    done
    return 1
}

# Show current routing table
show_routes() {
    print_status "Current routing table:"
    netstat -rn | head -20
    echo
}

# Set up split tunneling
setup_split_tunnel() {
    print_status "Setting up VPN split tunneling..."
    
    # Find VPN interface
    VPN_INTERFACE=$(get_vpn_interface)
    if [ $? -ne 0 ]; then
        print_error "No VPN interface found. Is VPN connected?"
        print_error "Common interfaces: utun0, utun1, ppp0"
        return 1
    fi
    
    print_success "Found VPN interface: $VPN_INTERFACE"
    
    # Get original default route
    ORIGINAL_GATEWAY=$(route -n get default | grep gateway | awk '{print $2}')
    print_status "Original gateway: $ORIGINAL_GATEWAY"
    
    # Remove VPN default route (keeps internet traffic direct)
    print_status "Removing VPN default route..."
    sudo route delete default -interface $VPN_INTERFACE 2>/dev/null || true
    
    # Add specific routes for work networks through VPN
    print_status "Adding work network routes through VPN..."
    for network in "${WORK_NETWORKS[@]}"; do
        print_status "  → Routing $network through $VPN_INTERFACE"
        sudo route add -net $network -interface $VPN_INTERFACE 2>/dev/null || true
    done
    
    # Restore direct internet route if needed
    if [ -n "$ORIGINAL_GATEWAY" ]; then
        print_status "Ensuring direct internet access..."
        sudo route add default $ORIGINAL_GATEWAY 2>/dev/null || true
    fi
    
    print_success "Split tunneling configured!"
    show_status
}

# Remove split tunneling (restore full VPN)
remove_split_tunnel() {
    print_status "Removing split tunneling configuration..."
    
    VPN_INTERFACE=$(get_vpn_interface)
    if [ $? -ne 0 ]; then
        print_warning "No VPN interface found"
        return 0
    fi
    
    # Remove work network routes
    for network in "${WORK_NETWORKS[@]}"; do
        print_status "  → Removing route for $network"
        sudo route delete -net $network 2>/dev/null || true
    done
    
    print_success "Split tunneling removed"
}

# Show current split tunneling status
show_status() {
    print_status "Split Tunneling Status"
    echo
    
    # Check VPN interface
    VPN_INTERFACE=$(get_vpn_interface)
    if [ $? -eq 0 ]; then
        print_success "VPN Interface: $VPN_INTERFACE"
        
        # Show VPN IP
        VPN_IP=$(ifconfig $VPN_INTERFACE | grep "inet " | awk '{print $2}')
        if [ -n "$VPN_IP" ]; then
            print_status "VPN IP: $VPN_IP"
        fi
    else
        print_warning "VPN Interface: Not found"
    fi
    
    # Check default route
    DEFAULT_ROUTE=$(route -n get default 2>/dev/null | grep interface | awk '{print $2}')
    if [ -n "$DEFAULT_ROUTE" ]; then
        print_status "Default Route Interface: $DEFAULT_ROUTE"
        if [ "$DEFAULT_ROUTE" = "$VPN_INTERFACE" ]; then
            print_warning "All traffic going through VPN (no split tunneling)"
        else
            print_success "Internet traffic bypassing VPN ✓"
        fi
    fi
    
    echo
    print_status "Work Network Routes:"
    for network in "${WORK_NETWORKS[@]}"; do
        route_info=$(route -n get $network 2>/dev/null | grep interface | awk '{print $2}')
        if [ "$route_info" = "$VPN_INTERFACE" ]; then
            print_success "  ✓ $network → $VPN_INTERFACE"
        else
            print_warning "  ✗ $network → $route_info"
        fi
    done
}

# Test connectivity
test_connectivity() {
    print_status "Testing connectivity..."
    echo
    
    # Test work network (should go through VPN)
    print_status "Testing work network access (should use VPN):"
    if ping -c 1 -W 5000 10.4.51.37 >/dev/null 2>&1; then
        print_success "  ✓ RTD proxy (10.4.51.37) reachable"
    else
        print_warning "  ✗ RTD proxy (10.4.51.37) not reachable"
    fi
    
    # Test Claude API (should bypass VPN)
    print_status "Testing Claude API access (should bypass VPN):"
    if curl -s --connect-timeout 5 https://claude.ai >/dev/null 2>&1; then
        print_success "  ✓ Claude.ai reachable"
    else
        print_warning "  ✗ Claude.ai not reachable"
    fi
    
    if curl -s --connect-timeout 5 https://api.anthropic.com >/dev/null 2>&1; then
        print_success "  ✓ Anthropic API reachable"
    else
        print_warning "  ✗ Anthropic API not reachable"
    fi
    
    # Test general internet (should bypass VPN)
    print_status "Testing general internet access:"
    if curl -s --connect-timeout 5 https://google.com >/dev/null 2>&1; then
        print_success "  ✓ Google.com reachable"
    else
        print_warning "  ✗ Google.com not reachable"
    fi
}

# DNS configuration for split tunneling
setup_dns() {
    print_status "Configuring DNS for split tunneling..."
    
    # Create custom DNS configuration
    print_status "Setting up DNS resolution..."
    
    # Use work DNS for work domains, public DNS for everything else
    cat << EOF | sudo tee /etc/resolver/rtd-denver.com
nameserver 10.4.51.1
domain rtd-denver.com
search_order 1
EOF
    
    # Flush DNS cache
    sudo dscacheutil -flushcache
    sudo killall -HUP mDNSResponder
    
    print_success "DNS configuration updated"
}

# Main execution
main() {
    case "${1:-help}" in
        "setup"|"start")
            setup_split_tunnel
            ;;
        "remove"|"stop")
            remove_split_tunnel
            ;;
        "status")
            show_status
            ;;
        "test")
            test_connectivity
            ;;
        "dns")
            setup_dns
            ;;
        "routes")
            show_routes
            ;;
        "help"|"-h"|"--help")
            echo "VPN Split Tunneling Setup Script"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  setup     Set up split tunneling (work traffic via VPN, internet direct)"
            echo "  remove    Remove split tunneling (restore full VPN)"
            echo "  status    Show current split tunneling status"
            echo "  test      Test connectivity to work and internet"
            echo "  dns       Configure DNS for split tunneling"
            echo "  routes    Show current routing table"
            echo "  help      Show this help message"
            echo
            echo "Work Networks (routed through VPN):"
            for network in "${WORK_NETWORKS[@]}"; do
                echo "  - $network"
            done
            echo
            echo "Excluded Domains (bypass VPN):"
            for domain in "${EXCLUDE_DOMAINS[@]}"; do
                echo "  - $domain"
            done
            echo
            echo "Usage Flow:"
            echo "  1. Connect to your work VPN normally"
            echo "  2. Run: $0 setup"
            echo "  3. Test: $0 test"
            echo "  4. When done: $0 remove (optional)"
            ;;
        *)
            print_error "Unknown command: $1"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Check if running as root for some operations
check_sudo() {
    if ! sudo -n true 2>/dev/null; then
        print_warning "Some operations require sudo access"
        print_status "You may be prompted for your password"
    fi
}

# Check dependencies
if ! command -v route &> /dev/null; then
    print_error "route command not found"
    exit 1
fi

check_sudo
main "$@"