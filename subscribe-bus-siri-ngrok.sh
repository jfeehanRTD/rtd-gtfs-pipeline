#!/bin/bash

echo "📡 Bus SIRI Subscription via ngrok"
echo "=================================="

# Get ngrok tunnel URL
NGROK_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[] | select(.name=="bus-siri") | .public_url')

# Update script with current ngrok URL
NGROK_URL="https://3630ae39c204.ngrok-free.app"

if [[ -z "$NGROK_URL" || "$NGROK_URL" == "null" ]]; then
    echo "❌ ngrok tunnel not found. Start it with: ./start-ngrok-tunnels.sh"
    exit 1
fi

echo "✅ Found ngrok URL: $NGROK_URL"

# Read TIS Proxy credentials from .env
if [ -f .env ]; then
    source .env
fi

# TIS Proxy configuration
TIS_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
TIS_SERVICE="${TIS_PROXY_SERVICE:-siri}"
TIS_TTL="${TIS_PROXY_TTL:-90000}"

echo ""
echo "📋 Configuration:"
echo "  TIS Proxy: $TIS_HOST"
echo "  Service: $TIS_SERVICE"
echo "  TTL: $TIS_TTL ms"
echo "  Callback URL: $NGROK_URL/bus-siri"

# Create subscription request directly to TIS Proxy
PAYLOAD=$(cat <<EOF
{
  "RequestorRef": "RTD_LOCAL_TEST",
  "MessageIdentifier": "msg-$(date +%s)",
  "SubscriptionIdentifier": "sub-bus-$(date +%s)",
  "ValidUntilTime": "$(date -u -v+24H +%Y-%m-%dT%H:%M:%SZ)",
  "SubscriberRef": "RTD_BUS_RECEIVER",
  "ConsumerAddress": "$NGROK_URL/bus-siri",
  "SubscriptionContext": {
    "HeartbeatInterval": "PT30S"
  },
  "VehicleMonitoringSubscriptionRequest": {
    "VehicleMonitoringRequest": {
      "version": "2.0",
      "RequestTimestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
      "RequestorRef": "RTD_LOCAL_TEST"
    }
  }
}
EOF
)

echo ""
echo "📤 Sending subscription to TIS Proxy..."

# Send subscription request with auth if available
if [[ -n "$TIS_PROXY_USERNAME" && -n "$TIS_PROXY_PASSWORD" ]]; then
    AUTH_HEADER="Authorization: Basic $(echo -n "$TIS_PROXY_USERNAME:$TIS_PROXY_PASSWORD" | base64)"
    RESPONSE=$(curl -s -X POST "$TIS_HOST/subscribe" \
        -H "Content-Type: application/json" \
        -H "$AUTH_HEADER" \
        -d "$PAYLOAD" 2>&1)
else
    RESPONSE=$(curl -s -X POST "$TIS_HOST/subscribe" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD" 2>&1)
fi

if [[ "$RESPONSE" == *"error"* ]] || [[ "$RESPONSE" == *"Could not resolve"* ]]; then
    echo "❌ Subscription failed: $RESPONSE"
    echo ""
    echo "💡 Make sure:"
    echo "  1. You're connected to the RTD VPN"
    echo "  2. TIS Proxy credentials are correct in .env"
    echo "  3. ngrok tunnel is running"
else
    echo "✅ Subscription request sent!"
    echo "Response: $RESPONSE"
fi

echo ""
echo "📊 Check subscription status:"
echo "  curl http://localhost:8082/status | jq '.subscription_active'"
echo ""
echo "🔍 Monitor incoming data:"
echo "  ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri"