#!/bin/bash

# Secure LRGPS HTTP Receiver Startup Script
# Loads credentials from environment variables and starts the LRGPS receiver

echo "================================"
echo "RTD LRGPS HTTP Receiver Startup"
echo "================================"

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    echo "📁 Loading environment from .env file..."
    set -a  # automatically export all variables
    source .env
    set +a  # stop automatic export
    echo "✅ Environment loaded from .env"
else
    echo "⚠️  No .env file found - using system environment variables only"
fi

# Check if required credentials are available
if [[ -n "$TIS_PROXY_USERNAME" && -n "$TIS_PROXY_PASSWORD" ]]; then
    echo "🔐 Authentication credentials found in environment"
else
    echo "❌ Missing required environment variables: TIS_PROXY_USERNAME and TIS_PROXY_PASSWORD"
    echo "   Please create a .env file with your credentials or export them as environment variables"
    echo "   Example .env file:"
    echo "   TIS_PROXY_USERNAME=your_username"
    echo "   TIS_PROXY_PASSWORD=your_password"
    echo "   TIS_PROXY_HOST=http://tisproxy.rtd-denver.com"
    echo "   LRGPS_SERVICE=lrgps"
    echo "   LRGPS_TTL=90000"
    exit 1
fi

# Display configuration
echo ""
echo "Configuration:"
echo "  TIS Proxy Host: ${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
echo "  LRGPS Service: ${LRGPS_SERVICE:-lrgps}"
echo "  TTL: ${LRGPS_TTL:-90000} ms"
echo "  Username: $TIS_PROXY_USERNAME"
echo "  Password: [HIDDEN]"
echo ""

# Ensure the project is compiled
echo "🔨 Compiling project..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi
echo "✅ Compilation successful"

# Start the LRGPS HTTP Receiver
echo ""
echo "🚀 Starting LRGPS HTTP Receiver..."
echo "   Listening on port 8083 at /lrgps"
echo "   Press Ctrl+C to stop"
echo ""

# Run with environment variables (secure approach)
java -cp target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout) \
    com.rtd.pipeline.LRGPSHTTPReceiver

echo ""
echo "👋 LRGPS HTTP Receiver stopped"