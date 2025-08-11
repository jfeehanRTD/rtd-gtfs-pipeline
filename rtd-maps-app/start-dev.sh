#!/bin/bash

# RTD Maps App Development Startup Script

echo "🚌 Starting RTD Live Transit Maps Development Server..."
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js 18+ first."
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node -v | cut -d'v' -f2)
REQUIRED_VERSION="18.0.0"

if [ "$(printf '%s\n' "$REQUIRED_VERSION" "$NODE_VERSION" | sort -V | head -n1)" != "$REQUIRED_VERSION" ]; then 
    echo "❌ Node.js version $NODE_VERSION is too old. Please upgrade to Node.js 18+."
    exit 1
fi

# Check if .env file exists (optional for OSM)
if [ ! -f ".env" ]; then
    echo "📄 Creating .env from template (optional for OpenStreetMap)..."
    cp .env.example .env
fi

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

echo "✅ Ready to start - No API keys required!"
echo "🌐 Starting development server on http://localhost:3000"
echo "🔄 Hot reload enabled - changes will auto-refresh"
echo ""
echo "Features available:"
echo "  • Real-time RTD vehicle tracking"
echo "  • Interactive OpenStreetMap with transit data"
echo "  • Vehicle filtering and details"
echo "  • Live data from Kafka topics (fallback to RTD APIs)"
echo "  • 100% free - no API costs or limits!"
echo ""
echo "Press Ctrl+C to stop the development server"
echo ""

# Start the development server
npm run dev