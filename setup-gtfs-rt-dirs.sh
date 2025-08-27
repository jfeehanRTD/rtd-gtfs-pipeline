#!/bin/bash

# GTFS-RT Directory Setup Script
# Creates necessary directories for GTFS-RT file generation

set -e

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Setup]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_status "Setting up GTFS-RT directory structure..."

# Create main data directory if it doesn't exist
if [ ! -d "data" ]; then
    mkdir -p "data"
    print_success "Created data directory"
else
    print_status "Data directory already exists"
fi

# Create gtfs-rt subdirectory
if [ ! -d "data/gtfs-rt" ]; then
    mkdir -p "data/gtfs-rt"
    print_success "Created data/gtfs-rt directory"
else
    print_status "GTFS-RT directory already exists"
fi

# Set proper permissions
chmod 755 "data"
chmod 755 "data/gtfs-rt"

print_status "Directory structure:"
echo "  data/"
echo "  └── gtfs-rt/     # GTFS-RT protobuf files will be generated here"
echo

print_status "Expected files after pipeline runs:"
echo "  data/gtfs-rt/VehiclePosition.pb"
echo "  data/gtfs-rt/TripUpdate.pb"
echo "  data/gtfs-rt/Alerts.pb"
echo

print_success "GTFS-RT directory setup completed!"
print_status "You can now start the GTFS-RT pipeline with: ./rtd-control.sh gtfs-rt all"