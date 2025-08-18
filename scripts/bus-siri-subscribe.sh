#!/bin/bash

# Bus SIRI Subscription Script
# Sends subscription request to the Bus SIRI HTTP Receiver

# Default values
DEFAULT_HOST="http://172.23.4.136:8080"
DEFAULT_SERVICE="siri"
DEFAULT_TTL="90000"
DEFAULT_RECEIVER_PORT="8082"

# Parse command line arguments
HOST="${1:-$DEFAULT_HOST}"
SERVICE="${2:-$DEFAULT_SERVICE}"
TTL="${3:-$DEFAULT_TTL}"
RECEIVER_PORT="${4:-$DEFAULT_RECEIVER_PORT}"

echo "================================"
echo "Bus SIRI Subscription Manager"
echo "================================"
echo ""
echo "Configuration:"
echo "  SIRI Host: $HOST"
echo "  Service: $SERVICE"
echo "  TTL: $TTL ms"
echo "  Receiver Port: $RECEIVER_PORT"
echo ""

# Create subscription JSON payload
SUBSCRIPTION_JSON=$(cat <<EOF
{
  "host": "$HOST",
  "service": "$SERVICE",
  "ttl": $TTL
}
EOF
)

echo "Sending subscription request..."
echo "Payload:"
echo "$SUBSCRIPTION_JSON"
echo ""

# Send subscription request to the Bus SIRI HTTP Receiver
RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$SUBSCRIPTION_JSON" \
  "http://localhost:${RECEIVER_PORT}/subscribe")

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
    echo "Make sure the Bus SIRI HTTP Receiver is running on port $RECEIVER_PORT"
fi

echo ""
echo "================================"
echo "Testing Bus SIRI endpoint..."
echo "================================"

# Send a test SIRI payload (JSON format)
TEST_PAYLOAD=$(cat <<EOF
{
  "vehicle_id": "BUS_001",
  "route_id": "15",
  "direction": "EASTBOUND",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 25.5,
  "status": "IN_TRANSIT",
  "next_stop": "Union Station",
  "delay_seconds": 120,
  "occupancy": "MANY_SEATS_AVAILABLE",
  "block_id": "BLK_15_01",
  "trip_id": "TRIP_15_0800"
}
EOF
)

echo "Sending test bus data..."
TEST_RESPONSE=$(curl -s -X POST \
  -H "Content-Type: application/json" \
  -d "$TEST_PAYLOAD" \
  "http://localhost:${RECEIVER_PORT}/bus-siri")

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
echo "1. Check Kafka topic for bus data:"
echo "   ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri"
echo ""
echo "2. View Bus Communication Pipeline logs:"
echo "   ./rtd-control.sh logs bus"
echo ""
echo "3. Check receiver status:"
echo "   curl http://localhost:${RECEIVER_PORT}/status"
echo ""
echo "4. Stop the subscription:"
echo "   Press Ctrl+C in the receiver terminal"