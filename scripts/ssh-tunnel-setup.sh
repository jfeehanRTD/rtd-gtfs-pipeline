#!/bin/bash

# SSH Tunnel Setup for RTD Proxy Access
# Creates SSH tunnel to access RTD proxy server through work VPN

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[SSH Tunnel]${NC} $1"
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

# Configuration - Update these with your work server details
WORK_SERVER="your-work-server.company.com"  # Replace with your work server
WORK_USER="your-username"                   # Replace with your work username
RTD_PROXY_IP="10.4.51.37"                  # RTD proxy server IP
RTD_PROXY_PORT="80"                         # RTD proxy server port
LOCAL_TUNNEL_PORT="8037"                    # Local port for tunnel (avoiding conflicts)

# SSH options for tunnel
SSH_OPTS="-N -L ${LOCAL_TUNNEL_PORT}:${RTD_PROXY_IP}:${RTD_PROXY_PORT}"

print_tunnel_info() {
    print_info "SSH Tunnel Configuration:"
    echo "  Work Server:    ${WORK_SERVER}"
    echo "  Work User:      ${WORK_USER}"  
    echo "  RTD Proxy:      ${RTD_PROXY_IP}:${RTD_PROXY_PORT}"
    echo "  Local Tunnel:   localhost:${LOCAL_TUNNEL_PORT}"
    echo "  Tunnel URL:     http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim"
    echo
}

# Function to check if tunnel is already running
check_tunnel_status() {
    if pgrep -f "ssh.*${LOCAL_TUNNEL_PORT}:${RTD_PROXY_IP}:${RTD_PROXY_PORT}" > /dev/null; then
        return 0  # Tunnel is running
    else
        return 1  # Tunnel is not running
    fi
}

# Function to start SSH tunnel
start_tunnel() {
    print_status "Starting SSH tunnel to RTD proxy..."
    print_tunnel_info
    
    if check_tunnel_status; then
        print_warning "SSH tunnel is already running"
        show_tunnel_usage
        return 0
    fi
    
    print_status "Establishing SSH tunnel..."
    print_info "Command: ssh ${SSH_OPTS} ${WORK_USER}@${WORK_SERVER}"
    
    # Start tunnel in background
    ssh ${SSH_OPTS} ${WORK_USER}@${WORK_SERVER} &
    local ssh_pid=$!
    
    # Wait a moment for connection to establish
    sleep 3
    
    # Check if tunnel is working
    if check_tunnel_status; then
        print_success "SSH tunnel established successfully!"
        print_success "Tunnel PID: $ssh_pid"
        echo
        show_tunnel_usage
        
        # Test tunnel connectivity
        print_status "Testing tunnel connectivity..."
        if curl -s --connect-timeout 5 -I "http://localhost:${LOCAL_TUNNEL_PORT}" > /dev/null 2>&1; then
            print_success "Tunnel is responding to HTTP requests"
        else
            print_warning "Tunnel established but HTTP connection test failed"
            print_info "This may be normal - the RTD server might not respond to GET requests"
        fi
    else
        print_error "Failed to establish SSH tunnel"
        print_error "Check your SSH configuration and network connectivity"
        return 1
    fi
}

# Function to stop SSH tunnel
stop_tunnel() {
    print_status "Stopping SSH tunnel..."
    
    local pids=$(pgrep -f "ssh.*${LOCAL_TUNNEL_PORT}:${RTD_PROXY_IP}:${RTD_PROXY_PORT}" || true)
    
    if [ -z "$pids" ]; then
        print_warning "No SSH tunnel found running"
        return 0
    fi
    
    for pid in $pids; do
        print_status "Killing SSH tunnel process $pid"
        kill -TERM $pid 2>/dev/null || true
        
        # Wait for graceful shutdown
        local count=0
        while kill -0 $pid 2>/dev/null && [ $count -lt 5 ]; do
            sleep 1
            count=$((count + 1))
        done
        
        # Force kill if still running
        if kill -0 $pid 2>/dev/null; then
            print_warning "Force killing SSH tunnel process $pid"
            kill -KILL $pid 2>/dev/null || true
        fi
    done
    
    print_success "SSH tunnel stopped"
}

# Function to show tunnel status
show_status() {
    print_status "SSH Tunnel Status"
    print_tunnel_info
    
    if check_tunnel_status; then
        local pids=$(pgrep -f "ssh.*${LOCAL_TUNNEL_PORT}:${RTD_PROXY_IP}:${RTD_PROXY_PORT}")
        print_success "SSH tunnel is RUNNING (PIDs: $pids)"
        
        # Show port status
        if lsof -ti:${LOCAL_TUNNEL_PORT} > /dev/null 2>&1; then
            print_info "Local port ${LOCAL_TUNNEL_PORT}: IN USE (tunnel active)"
        else
            print_warning "Local port ${LOCAL_TUNNEL_PORT}: NOT IN USE (tunnel may be broken)"
        fi
        
        show_tunnel_usage
    else
        print_warning "SSH tunnel is NOT RUNNING"
        print_info "Start tunnel with: $0 start"
    fi
}

# Function to show how to use the tunnel
show_tunnel_usage() {
    print_info "Using the SSH Tunnel:"
    echo
    echo "  Original RTD URL:  http://10.4.51.37:80/rtd/tli/consumer/tim"
    echo "  Tunneled URL:      http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim"
    echo
    echo "  Test tunnel:       curl -I http://localhost:${LOCAL_TUNNEL_PORT}"
    echo "  Test RTD endpoint: curl -I http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim"
    echo
    echo "  Update proxy subscription scripts to use: localhost:${LOCAL_TUNNEL_PORT}"
}

# Function to test tunnel connectivity
test_tunnel() {
    print_status "Testing SSH tunnel connectivity..."
    print_tunnel_info
    
    if ! check_tunnel_status; then
        print_error "SSH tunnel is not running"
        print_info "Start tunnel with: $0 start"
        return 1
    fi
    
    print_status "Testing local tunnel port..."
    if lsof -ti:${LOCAL_TUNNEL_PORT} > /dev/null 2>&1; then
        print_success "Local port ${LOCAL_TUNNEL_PORT} is active"
    else
        print_error "Local port ${LOCAL_TUNNEL_PORT} is not active"
        return 1
    fi
    
    print_status "Testing HTTP connectivity through tunnel..."
    local response=$(curl -s -w "%{http_code}" --connect-timeout 10 --max-time 15 \
                    -o /dev/null "http://localhost:${LOCAL_TUNNEL_PORT}" 2>/dev/null || echo "000")
    
    case "$response" in
        200|301|302|403|404|405)
            print_success "Tunnel HTTP connectivity confirmed (HTTP $response)"
            ;;
        000)
            print_error "Tunnel HTTP connectivity failed (connection timeout/refused)"
            return 1
            ;;
        *)
            print_warning "Tunnel responding with HTTP $response (may be normal)"
            ;;
    esac
    
    print_status "Testing RTD proxy endpoint through tunnel..."
    local rtd_response=$(curl -s -w "%{http_code}" --connect-timeout 10 --max-time 15 \
                        -o /dev/null "http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim" 2>/dev/null || echo "000")
    
    case "$rtd_response" in
        200|201|405)
            print_success "RTD proxy endpoint accessible through tunnel (HTTP $rtd_response)"
            ;;
        401|403)
            print_success "RTD proxy endpoint found but requires authentication (HTTP $rtd_response)"
            ;;
        404)
            print_warning "RTD proxy endpoint not found (HTTP 404) - check URL path"
            ;;
        000)
            print_error "RTD proxy endpoint not accessible through tunnel"
            return 1
            ;;
        *)
            print_warning "RTD proxy endpoint responding with HTTP $rtd_response"
            ;;
    esac
    
    show_tunnel_usage
}

# Function to create a modified proxy subscription script that uses the tunnel
create_tunnel_proxy_script() {
    local script_name="proxy-subscribe-tunnel.sh"
    print_status "Creating tunnel-aware proxy subscription script: $script_name"
    
    # Copy the original script and modify the proxy URL
    if [ -f "scripts/proxy-subscribe-bridge.sh" ]; then
        cp scripts/proxy-subscribe-bridge.sh "scripts/$script_name"
        
        # Update the proxy URL to use the tunnel
        sed -i.bak "s|PROXY_SUBSCRIBE_URL=\"http://10.4.51.37:80/rtd/tli/consumer/tim\"|PROXY_SUBSCRIBE_URL=\"http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim\"|g" "scripts/$script_name"
        sed -i.bak "s|PROXY_UNSUBSCRIBE_URL=\"http://10.4.51.37:80/rtd/tli/consumer/tim\"|PROXY_UNSUBSCRIBE_URL=\"http://localhost:${LOCAL_TUNNEL_PORT}/rtd/tli/consumer/tim\"|g" "scripts/$script_name"
        
        # Make it executable
        chmod +x "scripts/$script_name"
        
        # Clean up backup file
        rm -f "scripts/${script_name}.bak"
        
        print_success "Created tunnel-aware script: scripts/$script_name"
        print_info "Usage: ./scripts/$script_name test"
        print_info "Usage: ./scripts/$script_name bridge"
    else
        print_error "Source script scripts/proxy-subscribe-bridge.sh not found"
        return 1
    fi
}

# Main execution
main() {
    case "${1:-help}" in
        "start")
            start_tunnel
            ;;
        "stop")
            stop_tunnel
            ;;
        "restart")
            stop_tunnel
            sleep 2
            start_tunnel
            ;;
        "status")
            show_status
            ;;
        "test")
            test_tunnel
            ;;
        "create-script")
            create_tunnel_proxy_script
            ;;
        "help"|"-h"|"--help")
            echo "SSH Tunnel Setup for RTD Proxy Access"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  start         Start SSH tunnel to RTD proxy server"
            echo "  stop          Stop SSH tunnel"
            echo "  restart       Restart SSH tunnel"
            echo "  status        Show tunnel status and usage"
            echo "  test          Test tunnel connectivity"
            echo "  create-script Create tunnel-aware proxy subscription script"
            echo "  help          Show this help message"
            echo
            echo "Setup Instructions:"
            echo "  1. Edit this script to update WORK_SERVER and WORK_USER"
            echo "  2. Ensure SSH key authentication is set up to work server"
            echo "  3. Verify work server can reach RTD proxy (${RTD_PROXY_IP})"
            echo "  4. Run: $0 start"
            echo "  5. Run: $0 create-script"
            echo "  6. Test with tunnel-aware script"
            echo
            echo "Configuration:"
            print_tunnel_info
            ;;
        *)
            print_error "Unknown command: $1"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Check dependencies
if ! command -v ssh &> /dev/null; then
    print_error "SSH client is required but not installed"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    print_error "curl is required but not installed"
    exit 1
fi

# Verify configuration
if [ "$WORK_SERVER" = "your-work-server.company.com" ] || [ "$WORK_USER" = "your-username" ]; then
    print_warning "Please update WORK_SERVER and WORK_USER in this script before use"
    print_info "Edit: $0"
    print_info "Set WORK_SERVER to your work server hostname"
    print_info "Set WORK_USER to your work username"
    echo
fi

# Run main function with all arguments
main "$@"