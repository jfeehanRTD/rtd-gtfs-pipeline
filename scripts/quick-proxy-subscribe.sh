#!/bin/bash

# Quick Proxy Subscription Script
# Simple one-liner to send JSON POST with Basic Auth using auto-detected IP

# Auto-detect local IP
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
        for interface in en0 eth0 wlan0 en1 eth1; do
            ip=$(ifconfig $interface 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
            [[ -n "$ip" ]] && break
        done
    fi
    
    # Method 4: Use external service as fallback
    if [[ -z "$ip" ]] && command -v curl &> /dev/null; then
        ip=$(curl -s --connect-timeout 5 ifconfig.me 2>/dev/null || curl -s --connect-timeout 5 ipinfo.io/ip 2>/dev/null)
    fi
    
    echo "$ip"
}

LOCAL_IP=$(get_local_ip)
if [[ -z "$LOCAL_IP" ]]; then
    echo "❌ Could not determine local IP address"
    exit 1
fi

echo "📡 Using local IP: $LOCAL_IP"

curl -X POST \
  -H "Content-Type: application/json" \
  -u "rc-proxy-admin:aE@jeLAM76zLNqQLeXuu" \
  -d "{
    \"host\": \"http://${LOCAL_IP}:80\",
    \"ttl\": 90000
  }" \
  http://10.1.105.65/subscribe

echo ""