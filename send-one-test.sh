#!/bin/bash

# Send one valid test record to clear the queue and verify JSON format

echo "Sending one valid test record to each receiver..."

timestamp=$(( $(date +%s) * 1000 ))

# Send bus data
curl -X POST -H "Content-Type: application/json" -d "{
    \"timestamp_ms\": $timestamp,
    \"vehicle_id\": \"BUS_TEST_001\",
    \"route_id\": \"15\",
    \"direction\": \"EASTBOUND\",
    \"latitude\": 39.7392,
    \"longitude\": -104.9903,
    \"speed_mph\": 25,
    \"status\": \"IN_TRANSIT\",
    \"next_stop\": \"Union Station\",
    \"delay_seconds\": 30,
    \"occupancy\": \"MANY_SEATS_AVAILABLE\",
    \"block_id\": \"BLK_15_01\",
    \"trip_id\": \"TRIP_15_0900\",
    \"last_updated\": \"$(date -Iseconds)\",
    \"is_real_time\": true
}" http://localhost:8082/bus-siri

echo ""

# Send rail data
curl -X POST -H "Content-Type: application/json" -d "{
    \"timestamp_ms\": $timestamp,
    \"train_id\": \"LRV_TEST_001\",
    \"line_id\": \"A-Line\",
    \"direction\": \"NORTHBOUND\",
    \"latitude\": 39.7392,
    \"longitude\": -104.9903,
    \"speed_mph\": 35,
    \"status\": \"ON_TIME\",
    \"next_station\": \"Union Station\",
    \"delay_seconds\": 0,
    \"operator_message\": \"Normal operation\",
    \"last_updated\": \"$(date -Iseconds)\",
    \"is_real_time\": true
}" http://localhost:8081/rail-comm

echo ""
echo "Test data sent! Check endpoints:"
echo "Bus: curl -s http://localhost:8082/bus-siri/latest"
echo "Rail: curl -s http://localhost:8081/rail-comm/latest"