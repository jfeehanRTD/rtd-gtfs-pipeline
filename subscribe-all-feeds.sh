#!/bin/bash

echo "🚀 Subscribing to All RTD Live Feeds..."
echo "======================================"

echo ""
echo "📡 Subscribing to Bus SIRI feed..."
./rtd-control.sh bus-comm subscribe

echo ""
echo "🚊 Subscribing to Rail Communication feed..."
./rtd-control.sh rail-comm subscribe

echo ""
echo "🚈 Subscribing to LRGPS feed..."
./rtd-control.sh lrgps subscribe

echo ""
echo "✅ All feed subscriptions initiated!"
echo ""
echo "📊 To monitor the feeds:"
echo "   Bus SIRI:           ./rtd-control.sh bus-comm monitor"
echo "   Rail Communication: ./rtd-control.sh rail-comm monitor"
echo "   LRGPS:              ./rtd-control.sh lrgps monitor"
echo ""
echo "🌐 Live tab should now show vehicle data from all feeds"