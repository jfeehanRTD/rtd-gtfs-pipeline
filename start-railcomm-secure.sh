#!/bin/bash

# Secure RTD Rail Communication Pipeline Startup Script
# This script loads credentials from environment variables instead of command line arguments

set -euo pipefail  # Exit on error, undefined variables, and pipe failures

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔐 RTD Rail Communication Secure Startup"
echo "======================================="

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
RAILCOMM_SERVICE="${RAILCOMM_SERVICE:-railcomm}"
RAILCOMM_TTL="${RAILCOMM_TTL:-90000}"

echo "✅ Configuration validated"
echo "   Host: $TIS_PROXY_HOST"
echo "   Service: $RAILCOMM_SERVICE" 
echo "   TTL: $RAILCOMM_TTL ms"
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
    echo "🛑 Shutting down RTD Rail Communication pipeline..."
    # Kill all background jobs
    jobs -p | xargs -r kill 2>/dev/null || true
    wait
    echo "✅ Pipeline stopped"
}

# Set up signal handlers for graceful shutdown
trap cleanup EXIT INT TERM

echo "🚀 Starting RTD Rail Communication Pipeline with secure credentials..."
echo ""

# Start the Rail Communication HTTP Receiver (reads credentials from environment)
echo "📡 Starting Rail Communication HTTP Receiver..."
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
    com.rtd.pipeline.RailCommHTTPReceiver &

RECEIVER_PID=$!

# Wait a moment for receiver to start
sleep 3

# Check if receiver started successfully
if ! kill -0 "$RECEIVER_PID" 2>/dev/null; then
    echo "❌ Failed to start Rail Communication HTTP Receiver"
    exit 1
fi

echo "✅ Rail Communication HTTP Receiver started (PID: $RECEIVER_PID)"

# Start the Rail Communication Pipeline
echo "⚙️  Starting Rail Communication Pipeline..."
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
    com.rtd.pipeline.RTDRailCommPipeline &

PIPELINE_PID=$!

# Wait a moment for pipeline to start
sleep 3

# Check if pipeline started successfully
if ! kill -0 "$PIPELINE_PID" 2>/dev/null; then
    echo "❌ Failed to start Rail Communication Pipeline"
    exit 1
fi

echo "✅ Rail Communication Pipeline started (PID: $PIPELINE_PID)"
echo ""
echo "🎉 RTD Rail Communication Pipeline is running!"
echo ""
echo "📊 Monitoring:"
echo "   • Health check: curl http://localhost:8081/health"
echo "   • Status: curl http://localhost:8081/status"
echo "   • Send test data: curl -X POST -H 'Content-Type: application/json' -d '{\"train_id\":\"TEST\"}' http://localhost:8081/rail-comm"
echo ""
echo "🛑 To stop: Press Ctrl+C"
echo ""

# Wait for all background jobs
wait