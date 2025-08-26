#!/bin/bash

echo "=========================================="
echo "RTD Transit System - Service Status Check"
echo "=========================================="
echo ""

# Check Java Pipeline
echo "📊 Main Pipeline API:"
if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
    echo "   ✅ RTD Pipeline API: RUNNING (port 8080)"
    echo "      • Health: http://localhost:8080/api/health"
    echo "      • Vehicles: http://localhost:8080/api/vehicles"
else
    echo "   ❌ RTD Pipeline API: NOT RUNNING"
fi
echo ""

# Check React App
echo "🌐 Web Application:"
if curl -s http://localhost:3000 > /dev/null 2>&1; then
    echo "   ✅ React Web App: RUNNING (port 3000)"
    echo "      • Static Map: http://localhost:3000/"
    echo "      • Live Map: http://localhost:3000/live"
    echo "      • Admin: http://localhost:3000/admin"
else
    echo "   ❌ React Web App: NOT RUNNING"
fi
echo ""

# Check Rail Receiver
echo "🚂 Rail Communication Feed:"
if curl -s http://localhost:8081/health > /dev/null 2>&1; then
    STATUS=$(curl -s http://localhost:8081/status | jq -r '.subscription_active')
    echo "   ✅ Rail Receiver: RUNNING (port 8081)"
    echo "      • Endpoint: http://localhost:8081/rail-comm"
    echo "      • Health: http://localhost:8081/health"
    if [ "$STATUS" = "true" ]; then
        echo "      • TIS Subscription: ACTIVE"
    else
        echo "      • TIS Subscription: INACTIVE (expected if not on RTD network)"
    fi
else
    echo "   ❌ Rail Receiver: NOT RUNNING"
fi
echo ""

# Check Bus Receiver
echo "🚌 Bus SIRI Feed:"
if curl -s http://localhost:8082/health > /dev/null 2>&1; then
    STATUS=$(curl -s http://localhost:8082/status | jq -r '.subscription_active')
    echo "   ✅ Bus SIRI Receiver: RUNNING (port 8082)"
    echo "      • Endpoint: http://localhost:8082/bus-siri"
    echo "      • Health: http://localhost:8082/health"
    if [ "$STATUS" = "true" ]; then
        echo "      • TIS Subscription: ACTIVE"
    else
        echo "      • TIS Subscription: INACTIVE (expected if not on RTD network)"
    fi
else
    echo "   ❌ Bus SIRI Receiver: NOT RUNNING"
fi
echo ""

# Check Kafka (optional)
echo "📨 Message Queue:"
if nc -z localhost 9092 2>/dev/null; then
    echo "   ✅ Kafka: RUNNING (port 9092)"
else
    echo "   ⚠️  Kafka: NOT RUNNING (optional for development)"
fi
echo ""

# Summary
echo "=========================================="
echo "Summary:"
RUNNING_COUNT=0
TOTAL_COUNT=4

curl -s http://localhost:8080/api/health > /dev/null 2>&1 && ((RUNNING_COUNT++))
curl -s http://localhost:3000 > /dev/null 2>&1 && ((RUNNING_COUNT++))
curl -s http://localhost:8081/health > /dev/null 2>&1 && ((RUNNING_COUNT++))
curl -s http://localhost:8082/health > /dev/null 2>&1 && ((RUNNING_COUNT++))

echo "Services Running: $RUNNING_COUNT/$TOTAL_COUNT"

if [ $RUNNING_COUNT -eq $TOTAL_COUNT ]; then
    echo "Status: ✅ All services operational"
else
    echo "Status: ⚠️  Some services need attention"
fi
echo "=========================================="