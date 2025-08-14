#!/bin/bash

# Simple Claude API Fix for GlobalProtect
# Just focus on getting Claude working while VPN is connected

set -e

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_status() {
    echo -e "${BLUE}[Claude Fix]${NC} $1"
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

# Get non-VPN gateway
get_original_gateway() {
    # Find the gateway that's not a VPN interface
    netstat -rn | grep "^default" | grep -v utun | awk '{print $2}' | head -1
}

# Fix Claude access
fix_claude_access() {
    print_status "Fixing Claude API access through GlobalProtect..."
    
    ORIGINAL_GW=$(get_original_gateway)
    if [ -z "$ORIGINAL_GW" ]; then
        print_error "Cannot find original gateway"
        return 1
    fi
    
    print_status "Using gateway: $ORIGINAL_GW"
    
    # Method 1: Direct IP routes for Claude API
    print_status "Adding direct routes for Claude API..."
    
    # Get Claude API IPs
    CLAUDE_IPS=$(dig +short api.anthropic.com 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$')
    CLAUDE_AI_IPS=$(dig +short claude.ai 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$')
    
    # Add routes for Claude IPs
    for ip in $CLAUDE_IPS $CLAUDE_AI_IPS; do
        print_status "  → Route $ip via $ORIGINAL_GW"
        sudo route add -host $ip $ORIGINAL_GW 2>/dev/null || true
    done
    
    # Method 2: Common Anthropic/AWS IP ranges
    print_status "Adding routes for AWS/CDN networks..."
    
    # AWS us-west-2 (where Anthropic likely hosts)
    sudo route add -net 54.0.0.0/8 $ORIGINAL_GW 2>/dev/null || true
    sudo route add -net 52.0.0.0/8 $ORIGINAL_GW 2>/dev/null || true
    
    # Cloudflare CDN (common for APIs)
    sudo route add -net 104.16.0.0/12 $ORIGINAL_GW 2>/dev/null || true
    sudo route add -net 172.64.0.0/13 $ORIGINAL_GW 2>/dev/null || true
    
    print_success "Routes added!"
}

# Test Claude connectivity
test_claude() {
    print_status "Testing Claude API connectivity..."
    
    # Test DNS resolution
    if dig +short api.anthropic.com >/dev/null 2>&1; then
        print_success "DNS resolution working"
        API_IP=$(dig +short api.anthropic.com | head -1)
        print_status "Claude API IP: $API_IP"
    else
        print_error "DNS resolution failed"
        return 1
    fi
    
    # Test HTTPS connectivity
    print_status "Testing HTTPS connection..."
    
    # Use gtimeout if available, otherwise use curl's built-in timeout
    if command -v gtimeout >/dev/null 2>&1; then
        TIMEOUT_CMD="gtimeout 10"
    else
        TIMEOUT_CMD=""
    fi
    
    # Test connection - Claude API returns 404 for GET requests, but that means it's reachable
    response=$(curl -s --connect-timeout 5 --max-time 10 -o /dev/null -w "%{http_code}" https://api.anthropic.com 2>/dev/null || echo "000")
    
    case "$response" in
        "404"|"405"|"200"|"403")
            print_success "Claude API connection successful! (HTTP $response)"
            return 0
            ;;
        "000")
            print_error "Claude API connection failed (timeout/unreachable)"
            ;;
        *)
            print_warning "Claude API responded with HTTP $response"
            print_success "Connection appears to be working"
            return 0
            ;;
    esac
    
    # Show debugging info if connection failed
    print_status "Debugging connection..."
    curl -v --connect-timeout 5 --max-time 5 https://api.anthropic.com 2>&1 | head -10
}

# Clean up routes
cleanup() {
    print_status "Cleaning up Claude routes..."
    
    # Remove specific IPs
    CLAUDE_IPS=$(dig +short api.anthropic.com 2>/dev/null | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$' || true)
    for ip in $CLAUDE_IPS; do
        sudo route delete -host $ip 2>/dev/null || true
    done
    
    # Remove network ranges
    sudo route delete -net 54.0.0.0/8 2>/dev/null || true
    sudo route delete -net 52.0.0.0/8 2>/dev/null || true
    sudo route delete -net 104.16.0.0/12 2>/dev/null || true
    sudo route delete -net 172.64.0.0/13 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# Show current routes
show_routes() {
    print_status "Current routing table:"
    echo
    netstat -rn | head -15
    echo
    
    print_status "Claude API routes:"
    CLAUDE_IPS=$(dig +short api.anthropic.com 2>/dev/null || true)
    for ip in $CLAUDE_IPS; do
        route_info=$(route -n get $ip 2>/dev/null | grep gateway | awk '{print $2}' || echo "default")
        echo "  $ip → $route_info"
    done
}

# Alternative: Use different DNS
try_dns_fix() {
    print_status "Trying DNS-based fix..."
    
    # Create custom DNS resolver for anthropic.com
    sudo mkdir -p /etc/resolver
    
    cat << EOF | sudo tee /etc/resolver/anthropic.com
nameserver 8.8.8.8
nameserver 1.1.1.1
search_order 1
EOF
    
    # Flush DNS cache
    sudo dscacheutil -flushcache
    sudo killall -HUP mDNSResponder 2>/dev/null || true
    
    print_success "Custom DNS resolver created for anthropic.com"
    
    # Test after DNS change
    sleep 2
    test_claude
}

# Main function
main() {
    case "${1:-fix}" in
        "fix"|"setup")
            fix_claude_access
            sleep 2
            test_claude
            ;;
        "test")
            test_claude
            ;;
        "routes")
            show_routes
            ;;
        "dns")
            try_dns_fix
            ;;
        "cleanup")
            cleanup
            # Clean up DNS resolver too
            sudo rm -f /etc/resolver/anthropic.com
            sudo dscacheutil -flushcache
            ;;
        "help"|"-h")
            echo "Simple Claude API Fix for GlobalProtect"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  fix      Fix Claude API access (default)"
            echo "  test     Test Claude API connectivity"
            echo "  routes   Show current routing table"
            echo "  dns      Try DNS-based fix"
            echo "  cleanup  Remove all fixes"
            echo "  help     Show this help"
            echo
            echo "Quick start:"
            echo "  1. Connect GlobalProtect"
            echo "  2. Run: $0 fix"
            echo "  3. Test Claude Code access"
            ;;
        *)
            print_error "Unknown command: $1"
            exit 1
            ;;
    esac
}

main "$@"