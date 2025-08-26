#!/bin/bash

echo "Testing RTD Feed Receivers from TIS Proxy..."
echo ""

# Check if .env file exists and load it
if [ -f .env ]; then
    source .env
    echo "✅ Loaded environment variables from .env"
else
    echo "⚠️  Warning: .env file not found. Using environment variables if set."
fi

# Check required environment variables
if [ -z "$TIS_PROXY_USERNAME" ] || [ -z "$TIS_PROXY_PASSWORD" ] || [ -z "$TIS_PROXY_HOST" ]; then
    echo "❌ Error: Required environment variables not set:"
    echo "   TIS_PROXY_USERNAME, TIS_PROXY_PASSWORD, TIS_PROXY_HOST"
    echo "   Please copy .env.example to .env and configure your credentials."
    exit 1
fi

echo "Testing connection to TIS Proxy: $TIS_PROXY_HOST"
echo ""

# Test Rail Communication Feed from Proxy
echo "🚂 Testing Rail Communication Feed from TIS Proxy..."
echo "   Checking if Rail Communication receiver is running on port 8081..."
if curl -s --connect-timeout 5 http://localhost:8081/health > /dev/null 2>&1; then
    echo "   ✅ Rail Communication receiver is running"
    
    echo "   📡 Fetching real rail communication data from proxy..."
    # The receivers should automatically fetch from proxy if configured
    # Check if we're getting recent data
    curl -s -m 10 http://localhost:8081/status || echo "   ⚠️  No status endpoint available"
    echo ""
else
    echo "   ❌ Rail Communication receiver not running on port 8081"
    echo "   💡 Start with: ./rtd-control.sh start rail"
    echo ""
fi

echo ""

# Test Bus SIRI Feed from Proxy  
echo "🚌 Testing Bus SIRI Feed from TIS Proxy..."
echo "   Checking if Bus SIRI receiver is running on port 8082..."
if curl -s --connect-timeout 5 http://localhost:8082/health > /dev/null 2>&1; then
    echo "   ✅ Bus SIRI receiver is running"
    
    echo "   📡 Fetching real SIRI data from proxy..."
    # The receivers should automatically fetch from proxy if configured
    # Check if we're getting recent data
    curl -s -m 10 http://localhost:8082/status || echo "   ⚠️  No status endpoint available"
    echo ""
else
    echo "   ❌ Bus SIRI receiver not running on port 8082"
    echo "   💡 Start with: ./rtd-control.sh start bus"
    echo ""
fi

echo ""

# Check if receivers are actively processing data
echo "📊 Checking data processing status..."
echo "   Checking Java pipeline logs for recent activity..."

# Look for recent log activity (last 10 lines)
if [ -f "java-pipeline.log" ]; then
    echo "   📋 Recent Java pipeline activity:"
    tail -5 java-pipeline.log 2>/dev/null || echo "   ⚠️  No recent Java pipeline logs"
else
    echo "   ⚠️  Java pipeline log file not found"
fi

echo ""

# Check receiver logs
if [ -f "bus-receiver.log" ]; then
    echo "   📋 Recent Bus receiver activity:"
    tail -3 bus-receiver.log 2>/dev/null || echo "   ⚠️  No recent bus receiver logs"
else
    echo "   ⚠️  Bus receiver log file not found"
fi

if [ -f "rail-receiver.log" ]; then
    echo "   📋 Recent Rail receiver activity:"
    tail -3 rail-receiver.log 2>/dev/null || echo "   ⚠️  No recent rail receiver logs"  
else
    echo "   ⚠️  Rail receiver log file not found"
fi

echo ""
echo "✅ Feed testing complete!"
echo ""
echo "💡 Tips:"
echo "   - Start all services: ./rtd-control.sh start all"
echo "   - Check service status: ./rtd-control.sh status"
echo "   - View real-time logs: ./rtd-control.sh logs [service]"