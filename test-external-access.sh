#!/bin/bash

echo "🧪 Testing External Access to RTD Feed Receivers"
echo "=================================================="

EXTERNAL_IP="65.114.238.246"

echo "Testing Bus SIRI receiver accessibility..."
curl -s --connect-timeout 10 "http://$EXTERNAL_IP:8082/status" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Bus SIRI receiver accessible externally"
else
    echo "❌ Bus SIRI receiver NOT accessible - need port forwarding"
fi

echo "Testing Rail Comm receiver accessibility..."
curl -s --connect-timeout 10 "http://$EXTERNAL_IP:8081/health" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Rail Comm receiver accessible externally"
else
    echo "❌ Rail Comm receiver NOT accessible - need port forwarding"
fi

echo ""
echo "📋 Router Port Forwarding Settings Needed:"
echo "   External 8082 → 192.168.0.45:8082 (Bus SIRI)"
echo "   External 8081 → 192.168.0.45:8081 (Rail Comm)"
echo ""
echo "🔄 After port forwarding, run: ./subscribe-all-feeds.sh"