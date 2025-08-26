#!/bin/bash

echo "🛑 Unsubscribing from All RTD Live Feeds..."
echo "=========================================="

echo ""
echo "📡 Unsubscribing from Bus SIRI feed..."
./rtd-control.sh bus-comm unsubscribe

echo ""
echo "🚊 Unsubscribing from Rail Communication feed..."
./rtd-control.sh rail-comm unsubscribe

echo ""
echo "✅ All feed subscriptions stopped!"
echo ""
echo "📊 To check status:"
echo "   Bus SIRI status:    ./rtd-control.sh bus-comm status"
echo "   Rail Comm status:   ./rtd-control.sh rail-comm status"
echo ""
echo "🌐 Live tab will now show 0 vehicles (no active subscriptions)"