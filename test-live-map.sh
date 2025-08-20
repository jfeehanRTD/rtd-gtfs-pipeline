#!/bin/bash

# Test Live Transit Map Script
# Tests the new live transit map functionality with SIRI and Rail Communication feeds

set -euo pipefail

echo "======================================="
echo "RTD Live Transit Map Test"
echo "======================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_info() {
    echo -e "${BLUE}ℹ️${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

print_section() {
    echo -e "${CYAN}$1${NC}"
}

# Check dependencies
print_section "=== Dependency Check ==="

# Check if Node.js is installed
if command -v node &> /dev/null; then
    NODE_VERSION=$(node --version)
    print_success "Node.js is installed: $NODE_VERSION"
else
    print_error "Node.js is not installed. Please install Node.js first."
    exit 1
fi

# Check if npm is installed
if command -v npm &> /dev/null; then
    NPM_VERSION=$(npm --version)
    print_success "npm is installed: $NPM_VERSION"
else
    print_error "npm is not installed. Please install npm first."
    exit 1
fi

# Check if Java is installed
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java --version | head -n 1)
    print_success "Java is installed: $JAVA_VERSION"
else
    print_error "Java is not installed. Please install Java 24+ first."
    exit 1
fi

echo ""

# Check project structure
print_section "=== Project Structure Check ==="

if [[ -d "rtd-maps-app" ]]; then
    print_success "React app directory found"
else
    print_error "React app directory not found"
    exit 1
fi

if [[ -f "rtd-maps-app/src/components/LiveTransitMap.tsx" ]]; then
    print_success "Live Transit Map component found"
else
    print_error "Live Transit Map component not found"
    exit 1
fi

if [[ -f "rtd-maps-app/src/services/liveTransitService.ts" ]]; then
    print_success "Live Transit Service found"
else
    print_error "Live Transit Service not found"
    exit 1
fi

echo ""

# Check if React dependencies are installed
print_section "=== React App Dependencies ==="

cd rtd-maps-app

if [[ -d "node_modules" ]]; then
    print_success "Node modules found"
else
    print_info "Installing React dependencies..."
    npm install
    print_success "Dependencies installed"
fi

cd ..

echo ""

# Check Java compilation
print_section "=== Java Compilation Check ==="

if [[ -d "target/classes" ]]; then
    print_success "Java classes compiled"
else
    print_info "Compiling Java code..."
    mvn clean compile -q
    print_success "Java code compiled"
fi

echo ""

# Check endpoints
print_section "=== Endpoint Availability ==="

# Test SIRI endpoint
if curl -s --connect-timeout 2 "http://localhost:8082/bus-siri/latest" >/dev/null 2>&1; then
    print_success "Bus SIRI endpoint is available (http://localhost:8082/bus-siri/latest)"
else
    print_warning "Bus SIRI endpoint not available - start the Bus SIRI HTTP Receiver"
fi

# Test Rail Communication endpoint
if curl -s --connect-timeout 2 "http://localhost:8081/rail-comm/latest" >/dev/null 2>&1; then
    print_success "Rail Communication endpoint is available (http://localhost:8081/rail-comm/latest)"
else
    print_warning "Rail Communication endpoint not available - start the Rail Communication HTTP Receiver"
fi

echo ""

# Display instructions
print_section "=== Test Instructions ==="

echo "To test the Live Transit Map:"
echo ""
echo "1. Start all services with one command:"
echo "   ./rtd-control.sh start all"
echo ""
echo "2. Or start services individually:"
echo "   ./rtd-control.sh start receivers  # HTTP receivers only"
echo "   ./rtd-control.sh start react      # React app only"
echo ""
echo "3. Open your browser to:"
echo "   http://localhost:3000/live"
echo ""

print_section "=== Quick Start Commands ==="
echo "Start all services in one command:"
echo ""
echo "# Start everything (Java, React, Bus & Rail receivers)"
echo "./rtd-control.sh start all"
echo ""
echo "# Or start individual services:"
echo "./rtd-control.sh start receivers    # HTTP receivers only"
echo "./rtd-control.sh start react        # React app only"
echo "./rtd-control.sh start bus          # Bus SIRI receiver only"
echo "./rtd-control.sh start rail         # Rail Communication receiver only"
echo ""

print_section "=== Features to Test ==="
echo "✓ Live bus positions from SIRI feeds"
echo "✓ Live train positions from Rail Communication feeds"
echo "✓ Real-time updates every 5 seconds"
echo "✓ Vehicle details in popups (route, speed, occupancy, etc.)"
echo "✓ Toggle buses/trains visibility"
echo "✓ Connection status indicators"
echo "✓ Automatic map fitting to vehicle positions"
echo ""

print_section "=== API Endpoints ==="
echo "Bus SIRI Data:     http://localhost:8082/bus-siri/latest"
echo "Rail Comm Data:    http://localhost:8081/rail-comm/latest"
echo "Live Transit Map:  http://localhost:3000/live"
echo "Static Map:        http://localhost:3000/"
echo "Admin Dashboard:   http://localhost:3000/admin"

echo ""
print_success "Live Transit Map test setup complete!"

# Optional: Start React app automatically
read -p "Would you like to start the React app now? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    print_info "Starting React development server..."
    cd rtd-maps-app
    npm start
fi