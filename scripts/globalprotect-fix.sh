#!/bin/bash

# GlobalProtect Split Tunneling Fix
# Specifically designed to work with GlobalProtect's routing behavior

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[GP Fix]${NC} $1"
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

# Get VPN interface and gateway info
get_vpn_info() {
    # Find GlobalProtect VPN interface
    VPN_INTERFACE=$(ifconfig | grep -E "utun[0-9]" | head -1 | cut -d: -f1)
    if [ -z "$VPN_INTERFACE" ]; then
        print_error "No VPN interface found. Is GlobalProtect connected?"
        return 1
    fi
    
    # Get VPN gateway from the interface
    VPN_GATEWAY=$(ifconfig $VPN_INTERFACE | grep "inet " | awk '{print $4}' | cut -d'>' -f2)
    if [ -z "$VPN_GATEWAY" ]; then
        VPN_GATEWAY=$(ifconfig $VPN_INTERFACE | grep "inet " | awk '{print $2}')
    fi
    
    # Get original default gateway (before VPN)
    ORIGINAL_GATEWAY=$(netstat -rn | grep "^default" | grep -v "$VPN_INTERFACE" | awk '{print $2}' | head -1)
    if [ -z "$ORIGINAL_GATEWAY" ]; then
        # Try to find it in routing table
        ORIGINAL_GATEWAY=$(route -n get default 2>/dev/null | grep gateway | awk '{print $2}')
    fi
    
    print_success "VPN Interface: $VPN_INTERFACE"
    print_status "VPN Gateway: $VPN_GATEWAY"
    print_status "Original Gateway: $ORIGINAL_GATEWAY"
    
    return 0
}

# Setup split tunneling for GlobalProtect
setup_globalprotect_split() {
    print_status "Setting up GlobalProtect split tunneling fix..."
    
    if ! get_vpn_info; then
        return 1
    fi
    
    # Method 1: Add specific routes for Claude domains
    print_status "Adding direct routes for Claude API..."
    
    # Get Claude API IPs
    CLAUDE_IPS=$(nslookup api.anthropic.com | grep "Address:" | grep -v "#" | awk '{print $2}')
    CLAUDE_AI_IPS=$(nslookup claude.ai | grep "Address:" | grep -v "#" | awk '{print $2}')
    
    # Add direct routes for Claude IPs
    for ip in $CLAUDE_IPS $CLAUDE_AI_IPS; do
        if [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            print_status "  → Adding direct route for $ip"
            sudo route add -host $ip -gateway $ORIGINAL_GATEWAY 2>/dev/null || true
        fi
    done
    
    # Method 2: Add routes for common CDN networks (Claude might use these)
    print_status "Adding direct routes for common CDN networks..."
    
    # Cloudflare CDN
    sudo route add -net 104.16.0.0/13 -gateway $ORIGINAL_GATEWAY 2>/dev/null || true
    sudo route add -net 172.64.0.0/13 -gateway $ORIGINAL_GATEWAY 2>/dev/null || true
    
    # AWS CloudFront (Anthropic uses AWS)
    sudo route add -net 54.230.0.0/16 -gateway $ORIGINAL_GATEWAY 2>/dev/null || true
    sudo route add -net 54.239.128.0/18 -gateway $ORIGINAL_GATEWAY 2>/dev/null || true
    
    # Method 3: Fix work network routing through VPN
    print_status "Ensuring work networks route through VPN..."
    
    # Add specific RTD network route
    if [ -n "$VPN_GATEWAY" ]; then
        sudo route add -net 10.4.51.0/24 -gateway $VPN_GATEWAY 2>/dev/null || \
        sudo route add -net 10.4.51.0/24 $VPN_INTERFACE 2>/dev/null || true
    fi
    
    print_success "GlobalProtect split tunneling configured!"
}

# Alternative method: Modify DNS resolution
setup_dns_bypass() {
    print_status "Setting up DNS bypass for Claude domains..."
    
    # Create resolver directory if it doesn't exist
    sudo mkdir -p /etc/resolver
    
    # Use public DNS for Anthropic domains
    cat << EOF | sudo tee /etc/resolver/anthropic.com
nameserver 8.8.8.8
nameserver 1.1.1.1
EOF
    
    # Flush DNS cache
    sudo dscacheutil -flushcache
    sudo killall -HUP mDNSResponder 2>/dev/null || true
    
    print_success "DNS bypass configured for anthropic.com"
}

# Test connectivity with detailed debugging
test_detailed() {
    print_status "Testing connectivity with detailed debugging..."
    echo
    
    # Test RTD proxy
    print_status "Testing RTD proxy (10.4.51.37):"
    if ping -c 1 -W 5000 10.4.51.37 >/dev/null 2>&1; then
        print_success "  ✓ RTD proxy reachable via ping"
    else
        print_warning "  ✗ RTD proxy not reachable via ping"
    fi
    
    if nc -z -w 5 10.4.51.37 80 2>/dev/null; then
        print_success "  ✓ RTD proxy port 80 open"
    else
        print_warning "  ✗ RTD proxy port 80 not reachable"
    fi
    
    # Test Claude API with different methods
    print_status "Testing Claude API access:"
    
    # Test DNS resolution
    if nslookup api.anthropic.com >/dev/null 2>&1; then
        print_success "  ✓ Claude API DNS resolution working"
        API_IP=$(nslookup api.anthropic.com | grep "Address:" | grep -v "#" | head -1 | awk '{print $2}')
        print_status "  → Claude API resolves to: $API_IP"
    else
        print_error "  ✗ Claude API DNS resolution failed"
        return 1
    fi
    
    # Test HTTPS connection
    if curl -s --connect-timeout 10 --max-time 15 https://api.anthropic.com >/dev/null 2>&1; then
        print_success "  ✓ Claude API HTTPS connection working"
    else
        print_warning "  ✗ Claude API HTTPS connection failed"
        
        # Try with verbose output for debugging
        print_status "  → Debugging HTTPS connection..."
        curl -v --connect-timeout 5 https://api.anthropic.com 2>&1 | head -10
    fi
    
    # Test external IP
    print_status "Checking external IP addresses:"
    EXTERNAL_IP=$(curl -s --connect-timeout 5 https://ipinfo.io/ip 2>/dev/null || echo "unknown")
    print_status "  → External IP: $EXTERNAL_IP"
}

# Show current routing status
show_routing() {
    print_status "Current routing table:"
    echo
    netstat -rn | head -20
    echo
    
    print_status "VPN interface status:"
    ifconfig | grep -A 5 utun
    echo
}

# Remove all custom routes
cleanup() {
    print_status "Cleaning up custom routes..."
    
    # Remove Claude-specific routes
    CLAUDE_IPS=$(nslookup api.anthropic.com 2>/dev/null | grep "Address:" | grep -v "#" | awk '{print $2}' || true)
    for ip in $CLAUDE_IPS; do
        if [[ "$ip" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            sudo route delete -host $ip 2>/dev/null || true
        fi
    done
    
    # Remove CDN routes
    sudo route delete -net 104.16.0.0/13 2>/dev/null || true
    sudo route delete -net 172.64.0.0/13 2>/dev/null || true
    sudo route delete -net 54.230.0.0/16 2>/dev/null || true
    sudo route delete -net 54.239.128.0/18 2>/dev/null || true
    
    # Remove DNS resolver
    sudo rm -f /etc/resolver/anthropic.com
    sudo dscacheutil -flushcache
    sudo killall -HUP mDNSResponder 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# Try alternative: Temporarily disable IPv6
disable_ipv6() {
    print_status "Disabling IPv6 (may help with routing issues)..."
    sudo networksetup -setv6off "Wi-Fi" 2>/dev/null || true
    sudo networksetup -setv6off "Ethernet" 2>/dev/null || true
    print_success "IPv6 disabled (re-enable manually if needed)"
}

# Re-enable IPv6  
enable_ipv6() {
    print_status "Re-enabling IPv6..."
    sudo networksetup -setv6automatic "Wi-Fi" 2>/dev/null || true
    sudo networksetup -setv6automatic "Ethernet" 2>/dev/null || true
    print_success "IPv6 re-enabled"
}

# Main execution
main() {
    case "${1:-help}" in
        "setup"|"fix")
            setup_globalprotect_split
            setup_dns_bypass
            ;;
        "test")
            test_detailed
            ;;
        "routes"|"routing")
            show_routing
            ;;
        "cleanup"|"remove")
            cleanup
            ;;
        "ipv6-off")
            disable_ipv6
            ;;
        "ipv6-on")
            enable_ipv6
            ;;
        "help"|"-h"|"--help")
            echo "GlobalProtect Split Tunneling Fix"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  setup     Set up GlobalProtect split tunneling fix"
            echo "  test      Test connectivity with detailed debugging"
            echo "  routes    Show current routing table"
            echo "  cleanup   Remove all custom routes"
            echo "  ipv6-off  Disable IPv6 (may help with routing)"
            echo "  ipv6-on   Re-enable IPv6"
            echo "  help      Show this help message"
            echo
            echo "Troubleshooting steps:"
            echo "  1. Connect GlobalProtect normally"
            echo "  2. Run: $0 setup"
            echo "  3. Test: $0 test"
            echo "  4. If still fails, try: $0 ipv6-off"
            echo "  5. Clean up when done: $0 cleanup"
            ;;
        *)
            print_error "Unknown command: $1"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Check dependencies
for cmd in route ifconfig netstat nslookup curl; do
    if ! command -v $cmd &> /dev/null; then
        print_error "$cmd command not found"
        exit 1
    fi
done

main "$@"