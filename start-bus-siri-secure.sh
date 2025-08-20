#!/bin/bash

# Secure RTD Bus SIRI Pipeline Startup Script
# This script loads credentials from environment variables instead of command line arguments

set -euo pipefail  # Exit on error, undefined variables, and pipe failures

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔐 RTD Bus SIRI Secure Startup"
echo "================================"

# Check if .env file exists and source it
if [[ -f ".env" ]]; then
    echo "📄 Loading environment variables from .env file"
    # Source the .env file securely (export variables)
    set -a  # automatically export all variables
    source .env
    set +a  # stop automatic export
elif [[ -f ".env.local" ]]; then
    echo "📄 Loading environment variables from .env.local file"
    set -a
    source .env.local
    set +a
else
    echo "⚠️  No .env file found. Create one from .env.example with your credentials."
    echo "   You can also set environment variables directly:"
    echo "   export TIS_PROXY_USERNAME='your_username'"
    echo "   export TIS_PROXY_PASSWORD='your_password'"
fi

# Validate required environment variables
if [[ -z "${TIS_PROXY_USERNAME:-}" ]] || [[ -z "${TIS_PROXY_PASSWORD:-}" ]]; then
    echo "❌ ERROR: Missing required environment variables:"
    echo "   TIS_PROXY_USERNAME and TIS_PROXY_PASSWORD must be set"
    echo ""
    echo "💡 Set them in your .env file or export them directly:"
    echo "   cp .env.example .env"
    echo "   # Edit .env with your credentials"
    exit 1
fi

# Set defaults for optional variables
TIS_PROXY_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
TIS_PROXY_SERVICE="${TIS_PROXY_SERVICE:-siri}"
TIS_PROXY_TTL="${TIS_PROXY_TTL:-90000}"

echo "✅ Configuration validated"
echo "   Host: $TIS_PROXY_HOST"
echo "   Service: $TIS_PROXY_SERVICE" 
echo "   TTL: $TIS_PROXY_TTL ms"
echo "   Username: ${TIS_PROXY_USERNAME:0:3}***"  # Show only first 3 chars
echo ""

# Build the project if needed
if [[ ! -f "target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar" ]]; then
    echo "🔨 Building project..."
    mvn clean package -DskipTests
fi

# Function to cleanup background processes
cleanup() {
    echo ""
    echo "🛑 Shutting down RTD Bus SIRI pipeline..."
    # Kill all background jobs
    jobs -p | xargs -r kill 2>/dev/null || true
    wait
    echo "✅ Pipeline stopped"
}

# Set up signal handlers for graceful shutdown
trap cleanup EXIT INT TERM

echo "🚀 Starting RTD Bus SIRI Pipeline with secure credentials..."
echo ""

# Start the Bus SIRI HTTP Receiver (reads credentials from environment)
echo "📡 Starting Bus SIRI HTTP Receiver..."
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
    com.rtd.pipeline.BusCommHTTPReceiver &

RECEIVER_PID=$!

# Wait a moment for receiver to start
sleep 3

# Check if receiver started successfully
if ! kill -0 "$RECEIVER_PID" 2>/dev/null; then
    echo "❌ Failed to start Bus SIRI HTTP Receiver"
    exit 1
fi

echo "✅ Bus SIRI HTTP Receiver started (PID: $RECEIVER_PID)"

# Start the Bus Communication Pipeline
echo "⚙️  Starting Bus Communication Pipeline..."
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
    com.rtd.pipeline.RTDBusCommSimplePipeline &

PIPELINE_PID=$!

# Wait a moment for pipeline to start
sleep 3

# Check if pipeline started successfully
if ! kill -0 "$PIPELINE_PID" 2>/dev/null; then
    echo "❌ Failed to start Bus Communication Pipeline"
    exit 1
fi

echo "✅ Bus Communication Pipeline started (PID: $PIPELINE_PID)"
echo ""
echo "🎉 RTD Bus SIRI Pipeline is running!"
echo ""
echo "📊 Monitoring:"
echo "   • Health check: curl http://localhost:8082/health"
echo "   • Status: curl http://localhost:8082/status"
echo "   • Send test data: curl -X POST -H 'Content-Type: application/json' -d '{\"vehicle_id\":\"TEST\"}' http://localhost:8082/bus-siri"
echo ""
echo "🛑 To stop: Press Ctrl+C"
echo ""

# Wait for all background jobs
wait