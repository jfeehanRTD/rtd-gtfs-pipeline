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

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo "⚠️  .env file not found. Creating from template..."
    cp .env.example .env
    echo "📝 Please edit .env and add your Google Maps API key:"
    echo "   VITE_GOOGLE_MAPS_API_KEY=your_api_key_here"
    echo ""
    read -p "Press Enter when you've configured your API key..."
fi

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    npm install
fi

# Check if Google Maps API key is configured
if ! grep -q "^VITE_GOOGLE_MAPS_API_KEY=.*[^=]" .env; then
    echo "⚠️  Google Maps API key not configured in .env file"
    echo "Please add: VITE_GOOGLE_MAPS_API_KEY=your_api_key_here"
    echo ""
    echo "Get your API key from: https://console.cloud.google.com/google/maps-apis/credentials"
    exit 1
fi

echo "✅ Configuration validated"
echo "🌐 Starting development server on http://localhost:3000"
echo "🔄 Hot reload enabled - changes will auto-refresh"
echo ""
echo "Features available:"
echo "  • Real-time RTD vehicle tracking"
echo "  • Interactive Google Maps with transit data"
echo "  • Vehicle filtering and details"
echo "  • Live data from Kafka topics (fallback to RTD APIs)"
echo ""
echo "Press Ctrl+C to stop the development server"
echo ""

# Start the development server
npm run dev