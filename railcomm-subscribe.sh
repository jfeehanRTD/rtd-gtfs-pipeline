#!/bin/bash

# Rail Communication Subscription Script
# Sends subscription request to the Rail Communication HTTP Receiver via TIS Proxy

# Load credentials from environment if available
if [[ -f .env ]]; then
    set -a  # automatically export all variables
    source .env
    set +a  # stop automatic export
fi

# Function to get local host IP
get_local_host() {
    # Always dynamically determine IP address (ignore LOCAL_IP env var)
    local ip=""
    
    # Priority 1: Check for VPN interfaces (utun*)
    if command -v ifconfig &> /dev/null; then
        for vpn_interface in $(ifconfig -l | tr ' ' '\n' | grep 'utun'); do
            candidate_ip=$(ifconfig "$vpn_interface" 2>/dev/null | grep "inet " | grep -v "127.0.0.1" | awk '{print $2}' | head -1)
            if [[ -n "$candidate_ip" ]]; then
                ip="$candidate_ip"
                echo "🔐 Using VPN IP from $vpn_interface: $ip" >&2
                break
            fi
        done
    fi
    
    # Priority 2: Try route-based detection if no VPN IP found
    if [[ -z "$ip" ]]; then
        ip=$(route get default 2>/dev/null | grep interface | awk '{print $2}' | xargs ifconfig 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | awk '{print $2}' | head -1)
        echo "🌐 Using route-based IP: $ip" >&2
    fi
    
    # Priority 3: Fallback to first non-localhost IP
    if [[ -z "$ip" ]]; then
        ip=$(ifconfig 2>/dev/null | grep 'inet ' | grep -v '127.0.0.1' | head -1 | awk '{print $2}')
        echo "📡 Using fallback IP: $ip" >&2
    fi
    
    echo "http://${ip}:${RAIL_COMM_PORT:-881}"
}

# Default values (can be overridden by environment variables)
DEFAULT_TIS_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
DEFAULT_LOCAL_HOST="$(get_local_host)"
DEFAULT_SERVICE="${RAILCOMM_SERVICE:-railcomm}"
DEFAULT_TTL="${RAILCOMM_TTL:-90000}"
DEFAULT_RECEIVER_PORT="8081"

# Parse command line arguments (override env vars)
SUBSCRIPTION_HOST="${1:-$DEFAULT_LOCAL_HOST}"  # Host where TIS proxy will send data
SERVICE="${2:-$DEFAULT_SERVICE}"
TTL="${3:-$DEFAULT_TTL}"
RECEIVER_PORT="${4:-$DEFAULT_RECEIVER_PORT}"

echo "================================"
echo "Rail Communication Subscription Manager"
echo "================================"
echo ""
echo "Configuration:"
echo "  TIS Proxy: $DEFAULT_TIS_HOST"
echo "  Subscription Host: $SUBSCRIPTION_HOST"
echo "  Service: $SERVICE"
echo "  TTL: $TTL ms"
echo "  Local Receiver Port: $RECEIVER_PORT"
echo "  Current Hostname: $(hostname)"
# Get the IP that will be used
USED_IP=$(get_local_host | sed 's/http:\/\///; s/:.*$//')
echo "  Connection: Using IP address $USED_IP"
echo ""

# Create subscription JSON payload
SUBSCRIPTION_JSON=$(cat <<EOF
{
  "host": "$SUBSCRIPTION_HOST",
  "service": "$SERVICE",
  "ttl": $TTL
}
EOF
)

echo "Sending subscription request..."
echo "Payload:"
echo "$SUBSCRIPTION_JSON"
echo ""

# Send subscription request to the TIS Proxy
echo "Sending request to TIS Proxy: $DEFAULT_TIS_HOST/subscribe"

# Add authentication if available
AUTH_HEADER=""
if [[ -n "$TIS_PROXY_USERNAME" && -n "$TIS_PROXY_PASSWORD" ]]; then
    AUTH_HEADER="-u $TIS_PROXY_USERNAME:$TIS_PROXY_PASSWORD"
fi

RESPONSE=$(curl -s -X POST $AUTH_HEADER \
  -H "Content-Type: application/json" \
  -d "$SUBSCRIPTION_JSON" \
  "$DEFAULT_TIS_HOST/subscribe")

if [ $? -eq 0 ]; then
    echo "✅ Subscription request sent successfully"
    echo "Response: $RESPONSE"
    echo ""
    
    # Check subscription status
    echo "Checking subscription status..."
    STATUS=$(curl -s "http://localhost:${RECEIVER_PORT}/subscribe")
    echo "Status: $STATUS"
    echo ""
    
    # Check health
    echo "Checking receiver health..."
    HEALTH=$(curl -s "http://localhost:${RECEIVER_PORT}/health")
    echo "Health: $HEALTH"
else
    echo "❌ Failed to send subscription request"
    echo "Make sure the Rail Communication HTTP Receiver is running on port $RECEIVER_PORT"
fi

echo ""
echo "================================"
echo "Testing Rail Communication endpoint..."
echo "================================"

# Send a test Rail Communication payload (JSON format)
TEST_PAYLOAD=$(cat <<EOF
{
  "train_id": "LRV_TEST_001",
  "line_id": "A-Line",
  "direction": "NORTHBOUND", 
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 35.5,
  "status": "ON_TIME",
  "next_station": "Union Station",
  "delay_seconds": 0,
  "operator_message": "Normal operation - test message",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF
)

echo "Sending test rail communication data..."
TEST_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$TEST_PAYLOAD" \
  "http://localhost:${RECEIVER_PORT}/rail-comm")

if [ $? -eq 0 ]; then
    echo "✅ Test data sent successfully"
    echo "Response: $TEST_RESPONSE"
else
    echo "❌ Failed to send test data"
fi

echo ""
echo "================================"
echo "Monitoring Instructions:"
echo "================================"
echo "1. Check Kafka topic for rail communication data:"
echo "   ./scripts/kafka-console-consumer --topic rtd.rail.comm"
echo ""
echo "2. View Rail Communication Pipeline logs:"
echo "   ./rtd-control.sh logs rail-comm"
echo ""
echo "3. Check receiver status:"
echo "   curl http://localhost:${RECEIVER_PORT}/health"
echo ""
echo "4. Stop the subscription:"
echo "   Press Ctrl+C in the receiver terminal"