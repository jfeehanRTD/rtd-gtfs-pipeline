#!/bin/bash

echo "Testing RTD Feed Receivers..."
echo ""

# Test Rail Communication Feed
echo "🚂 Testing Rail Communication Feed..."
curl -X POST http://localhost:8081/rail-comm \
  -H "Content-Type: application/json" \
  -d '{
    "trainId": "301",
    "routeId": "W",
    "currentStation": "Union Station",
    "nextStation": "Pepsi Center",
    "occupancy": 75,
    "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'"
  }' \
  -w "\n   Response: %{http_code}\n"

echo ""

# Test Bus SIRI Feed
echo "🚌 Testing Bus SIRI Feed..."
curl -X POST http://localhost:8082/bus-siri \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<Siri xmlns="http://www.siri.org.uk/siri" version="2.0">
  <ServiceDelivery>
    <ResponseTimestamp>'$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)'</ResponseTimestamp>
    <VehicleMonitoringDelivery>
      <VehicleActivity>
        <MonitoredVehicleJourney>
          <VehicleRef>2847</VehicleRef>
          <LineRef>15</LineRef>
          <DirectionRef>East</DirectionRef>
          <VehicleLocation>
            <Latitude>39.7405</Latitude>
            <Longitude>-104.9928</Longitude>
          </VehicleLocation>
          <Occupancy>60</Occupancy>
        </MonitoredVehicleJourney>
      </VehicleActivity>
    </VehicleMonitoringDelivery>
  </ServiceDelivery>
</Siri>' \
  -w "\n   Response: %{http_code}\n"

echo ""
echo "✅ Feed testing complete!"