#!/bin/bash

# VPN IP Detection Script
# Automatically detects VPN IP address and uses it for SIRI host configuration

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[VPN Detect]${NC} $1"
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

# Function to detect VPN interfaces and IPs
detect_vpn_ip() {
    local vpn_ip=""
    local vpn_interface=""
    
    # Check for common VPN interfaces
    # GlobalProtect/PanGPS typically uses gpd0 or utun interfaces on macOS
    # OpenVPN uses tun0/tap0
    # Cisco AnyConnect uses cscotun0
    
    # Method 1: Check for GlobalProtect interfaces (utun)
    if [[ -z "$vpn_ip" ]]; then
        for interface in utun{0..9}; do
            if ifconfig "$interface" 2>/dev/null | grep -q "inet "; then
                candidate_ip=$(ifconfig "$interface" 2>/dev/null | grep "inet " | awk '{print $2}')
                if [[ -n "$candidate_ip" && "$candidate_ip" != "127.0.0.1" ]]; then
                    vpn_ip="$candidate_ip"
                    vpn_interface="$interface"
                    break
                fi
            fi
        done
    fi
    
    # Method 2: Check for gpd0 interface (GlobalProtect on some systems)
    if [[ -z "$vpn_ip" ]] && ifconfig gpd0 2>/dev/null | grep -q "inet "; then
        vpn_ip=$(ifconfig gpd0 2>/dev/null | grep "inet " | awk '{print $2}')
        vpn_interface="gpd0"
    fi
    
    # Method 3: Check for tun/tap interfaces (OpenVPN)
    if [[ -z "$vpn_ip" ]]; then
        for interface in tun{0..9} tap{0..9}; do
            if ifconfig "$interface" 2>/dev/null | grep -q "inet "; then
                candidate_ip=$(ifconfig "$interface" 2>/dev/null | grep "inet " | awk '{print $2}')
                if [[ -n "$candidate_ip" && "$candidate_ip" != "127.0.0.1" ]]; then
                    vpn_ip="$candidate_ip"
                    vpn_interface="$interface"
                    break
                fi
            fi
        done
    fi
    
    # Method 4: Check for Cisco AnyConnect
    if [[ -z "$vpn_ip" ]] && ifconfig cscotun0 2>/dev/null | grep -q "inet "; then
        vpn_ip=$(ifconfig cscotun0 2>/dev/null | grep "inet " | awk '{print $2}')
        vpn_interface="cscotun0"
    fi
    
    # Method 5: Check routing table for VPN routes (more generic)
    if [[ -z "$vpn_ip" ]]; then
        # Look for routes to 10.x.x.x or 172.x.x.x networks (common VPN ranges)
        local route_ip=$(netstat -rn | grep -E "^(10\.|172\.)" | grep -v "127.0.0.1" | awk '{print $1}' | head -1)
        if [[ -n "$route_ip" ]]; then
            # Get the interface for this route
            local route_interface=$(netstat -rn | grep "$route_ip" | awk '{print $NF}' | head -1)
            if [[ -n "$route_interface" ]]; then
                vpn_ip=$(ifconfig "$route_interface" 2>/dev/null | grep "inet " | awk '{print $2}')
                vpn_interface="$route_interface"
            fi
        fi
    fi
    
    if [[ -n "$vpn_ip" ]]; then
        echo "$vpn_ip:$vpn_interface"
    else
        echo ""
    fi
}

# Function to get regular network IP (non-VPN)
get_regular_ip() {
    local ip=""
    
    # Method 1: Use route command (most reliable on macOS/Linux)
    if command -v route &> /dev/null; then
        ip=$(route get default 2>/dev/null | grep interface | awk '{print $2}' | xargs ifconfig 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
    fi
    
    # Method 2: Check common network interfaces
    if [[ -z "$ip" ]]; then
        for interface in en0 eth0 wlan0 en1 eth1; do
            candidate_ip=$(ifconfig $interface 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
            if [[ -n "$candidate_ip" ]]; then
                ip="$candidate_ip"
                break
            fi
        done
    fi
    
    echo "$ip"
}

# Function to check if GlobalProtect is connected
check_globalprotect_status() {
    # Check if GlobalProtect process is running
    if pgrep -x "GlobalProtect" > /dev/null 2>&1; then
        return 0
    fi
    
    # Alternative: Check for PanGPS process
    if pgrep -x "PanGPS" > /dev/null 2>&1; then
        return 0
    fi
    
    return 1
}

# Main detection logic
detect_network_status() {
    print_status "Detecting network configuration..."
    
    # Get VPN IP if available
    local vpn_result=$(detect_vpn_ip)
    local vpn_ip=""
    local vpn_interface=""
    
    if [[ -n "$vpn_result" ]]; then
        vpn_ip=$(echo "$vpn_result" | cut -d':' -f1)
        vpn_interface=$(echo "$vpn_result" | cut -d':' -f2)
    fi
    
    # Get regular network IP
    local regular_ip=$(get_regular_ip)
    
    # Display results
    echo ""
    print_info "Network Status:"
    echo "  Regular IP:     ${regular_ip:-Not found}"
    
    if [[ -n "$vpn_ip" ]]; then
        print_success "VPN Connected"
        echo "  VPN IP:         $vpn_ip"
        echo "  VPN Interface:  $vpn_interface"
        
        # Check if it's GlobalProtect
        if check_globalprotect_status; then
            echo "  VPN Type:       GlobalProtect"
        else
            echo "  VPN Type:       Unknown/Generic"
        fi
        
        # Export for use by other scripts
        export VPN_IP="$vpn_ip"
        export VPN_INTERFACE="$vpn_interface"
        export USE_VPN_IP=true
        export SIRI_HOST_IP="$vpn_ip"
        
        print_success "SIRI host will use VPN IP: $vpn_ip"
        
        # Optionally update .env file
        if [[ "$1" == "--update-env" ]]; then
            update_env_file "$vpn_ip"
        fi
    else
        print_warning "No VPN connection detected"
        echo "  Using regular IP: ${regular_ip:-localhost}"
        
        export VPN_IP=""
        export VPN_INTERFACE=""
        export USE_VPN_IP=false
        export SIRI_HOST_IP="${regular_ip:-localhost}"
        
        print_info "SIRI host will use regular IP: ${SIRI_HOST_IP}"
    fi
    
    echo ""
    return 0
}

# Function to update .env file with detected IP
update_env_file() {
    local ip="$1"
    
    if [[ ! -f ".env" ]]; then
        print_warning ".env file not found, skipping update"
        return
    fi
    
    # Backup existing .env
    cp .env .env.backup
    
    # Check if SIRI_HOST_IP exists in .env
    if grep -q "^SIRI_HOST_IP=" .env; then
        # Update existing entry
        sed -i.tmp "s/^SIRI_HOST_IP=.*/SIRI_HOST_IP=$ip/" .env
        rm .env.tmp
        print_success "Updated SIRI_HOST_IP in .env to: $ip"
    else
        # Add new entry
        echo "" >> .env
        echo "# Auto-detected IP for SIRI host" >> .env
        echo "SIRI_HOST_IP=$ip" >> .env
        print_success "Added SIRI_HOST_IP to .env: $ip"
    fi
}

# Function to monitor VPN status changes
monitor_vpn_status() {
    print_status "Monitoring VPN status changes..."
    print_info "Press Ctrl+C to stop monitoring"
    echo ""
    
    local last_status=""
    
    while true; do
        local vpn_result=$(detect_vpn_ip)
        local current_status=""
        
        if [[ -n "$vpn_result" ]]; then
            current_status="connected:$(echo "$vpn_result" | cut -d':' -f1)"
        else
            current_status="disconnected"
        fi
        
        if [[ "$current_status" != "$last_status" ]]; then
            echo "$(date '+%Y-%m-%d %H:%M:%S') - VPN status changed"
            detect_network_status
            last_status="$current_status"
            
            # Optionally restart services when VPN status changes
            if [[ "$2" == "--restart-services" ]]; then
                print_status "Restarting SIRI services with new IP..."
                # Call restart logic here
            fi
        fi
        
        sleep 5
    done
}

# Parse command line arguments
case "${1:-detect}" in
    "detect")
        detect_network_status "$2"
        ;;
    "monitor")
        monitor_vpn_status "$@"
        ;;
    "update-env")
        detect_network_status "--update-env"
        ;;
    "export")
        # Silent mode for sourcing in other scripts
        vpn_result=$(detect_vpn_ip)
        if [[ -n "$vpn_result" ]]; then
            vpn_ip=$(echo "$vpn_result" | cut -d':' -f1)
            echo "export SIRI_HOST_IP=$vpn_ip"
            echo "export USE_VPN_IP=true"
        else
            regular_ip=$(get_regular_ip)
            echo "export SIRI_HOST_IP=${regular_ip:-localhost}"
            echo "export USE_VPN_IP=false"
        fi
        ;;
    "help"|"-h"|"--help")
        echo "VPN IP Detection Script"
        echo ""
        echo "Usage: $0 [COMMAND] [OPTIONS]"
        echo ""
        echo "Commands:"
        echo "  detect       Detect current network/VPN status (default)"
        echo "  monitor      Monitor VPN status changes continuously"
        echo "  update-env   Detect and update .env file with IP"
        echo "  export       Output export commands for sourcing"
        echo "  help         Show this help message"
        echo ""
        echo "Options:"
        echo "  --update-env         Update .env file with detected IP"
        echo "  --restart-services   Restart services on VPN status change (monitor mode)"
        echo ""
        echo "Examples:"
        echo "  $0                    # Detect current VPN status"
        echo "  $0 detect --update-env # Detect and update .env"
        echo "  $0 monitor            # Monitor VPN status changes"
        echo "  source <($0 export)   # Source IP variables in script"
        ;;
    *)
        print_error "Unknown command: $1"
        print_info "Run '$0 help' for usage information"
        exit 1
        ;;
esac