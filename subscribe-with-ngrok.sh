#!/bin/bash

echo "🌐 RTD Feed Subscription with ngrok Tunnels"
echo "==========================================="

# Check if ngrok is running
if ! curl -s http://localhost:4040/api/tunnels > /dev/null 2>&1; then
    echo "❌ ngrok not running. Start tunnels first:"
    echo "   ./start-ngrok-tunnels.sh"
    exit 1
fi

# Get tunnel URLs
echo "📡 Getting ngrok tunnel URLs..."
BUS_SIRI_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[] | select(.name=="bus-siri") | .public_url')
RAIL_COMM_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[] | select(.name=="rail-comm") | .public_url')

if [[ -z "$BUS_SIRI_URL" || "$BUS_SIRI_URL" == "null" ]]; then
    echo "❌ Bus SIRI tunnel not found. Check ngrok configuration."
    exit 1
fi

if [[ -z "$RAIL_COMM_URL" || "$RAIL_COMM_URL" == "null" ]]; then
    echo "❌ Rail Comm tunnel not found. Check ngrok configuration."
    exit 1
fi

echo "✅ Found tunnel URLs:"
echo "   Bus SIRI:  $BUS_SIRI_URL"
echo "   Rail Comm: $RAIL_COMM_URL"

echo ""
echo "🧪 Testing tunnel accessibility..."
curl -s "$BUS_SIRI_URL/status" > /dev/null && echo "✅ Bus SIRI tunnel working" || echo "❌ Bus SIRI tunnel failed"
curl -s "$RAIL_COMM_URL/health" > /dev/null && echo "✅ Rail Comm tunnel working" || echo "❌ Rail Comm tunnel failed"

echo ""
echo "📡 Subscribing to Bus SIRI feed..."
./scripts/bus-siri-subscribe.sh "$BUS_SIRI_URL"

echo ""
echo "🚊 Subscribing to Rail Comm feed..."  
./railcomm-subscribe.sh "$RAIL_COMM_URL"

echo ""
echo "✅ Subscription complete!"
echo "🌐 Live data should now flow through ngrok tunnels"