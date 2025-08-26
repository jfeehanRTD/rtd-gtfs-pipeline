#!/bin/bash

echo "🌐 Starting ngrok tunnels for RTD feeds..."
echo "=========================================="

# Start both tunnels defined in ngrok.yml
ngrok start --all --log stdout &

echo "⏳ Waiting for tunnels to establish..."
sleep 5

echo ""
echo "📡 Getting tunnel URLs..."

# Get the tunnel URLs from ngrok API
BUS_SIRI_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[] | select(.name=="bus-siri") | .public_url')
RAIL_COMM_URL=$(curl -s http://localhost:4040/api/tunnels | jq -r '.tunnels[] | select(.name=="rail-comm") | .public_url')

echo "✅ Tunnel URLs:"
echo "   Bus SIRI:     $BUS_SIRI_URL"
echo "   Rail Comm:    $RAIL_COMM_URL"

echo ""
echo "🔧 Next Steps:"
echo "1. Test the tunnels:"
echo "   curl $BUS_SIRI_URL/status"
echo "   curl $RAIL_COMM_URL/health"
echo ""
echo "2. Update subscription endpoints:"
echo "   Use these URLs in your subscription scripts"

# Keep script running
wait