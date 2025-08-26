#!/bin/bash

# Test script for RTD Rail Communication Pipeline
# Sends sample JSON payloads to the rtd.rail.comm Kafka topic

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[Rail Comm Test]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✅${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠️${NC} $1"
}

print_error() {
    echo -e "${RED}❌${NC} $1"
}

# Configuration
KAFKA_TOPIC="rtd.rail.comm"
KAFKA_SERVERS="localhost:9092"

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Sample rail communication JSON payloads
get_sample_payloads() {
    cat <<'EOF'
{
  "train_id": "LRV-001",
  "line_id": "A-Line",
  "direction": "Northbound",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 35.2,
  "status": "ON_TIME",
  "next_station": "Union Station",
  "delay_seconds": 0,
  "operator_message": "Normal operation",
  "timestamp": "2024-01-15T10:30:00Z"
}
EOF

    cat <<'EOF'
{
  "train_id": "LRV-002", 
  "line_id": "B-Line",
  "direction": "Southbound",
  "latitude": 39.7491,
  "longitude": -105.0000,
  "speed_mph": 28.5,
  "status": "DELAYED",
  "next_station": "Westminster Station",
  "delay_seconds": 120,
  "operator_message": "Signal delay ahead",
  "timestamp": "2024-01-15T10:32:00Z"
}
EOF

    cat <<'EOF'
{
  "train_id": "LRV-003",
  "line_id": "C-Line",
  "direction": "Eastbound", 
  "latitude": 39.7197,
  "longitude": -104.9800,
  "speed_mph": 0.0,
  "status": "STOPPED",
  "next_station": "Nine Mile Station",
  "delay_seconds": 300,
  "operator_message": "Passenger assistance in progress",
  "timestamp": "2024-01-15T10:35:00Z"
}
EOF

    cat <<'EOF'
{
  "train_id": "LRV-004",
  "line_id": "W-Line",
  "direction": "Westbound",
  "latitude": 39.7555,
  "longitude": -105.2211,
  "speed_mph": 42.0,
  "status": "ON_TIME",
  "next_station": "Wheat Ridge Station",
  "delay_seconds": -30,
  "operator_message": "Running slightly ahead of schedule",
  "timestamp": "2024-01-15T10:28:00Z"
}
EOF
}

# Function to send a single JSON payload to Kafka
send_payload() {
    local payload="$1"
    local payload_num="$2"
    
    print_status "Sending payload #$payload_num to topic: $KAFKA_TOPIC"
    
    # Use kafka-console-producer via our wrapper script
    echo "$payload" | ./scripts/kafka-console-producer --topic "$KAFKA_TOPIC" --bootstrap-server "$KAFKA_SERVERS" > /dev/null 2>&1
    
    if [ $? -eq 0 ]; then
        print_success "Payload #$payload_num sent successfully"
        echo "   Train: $(echo "$payload" | jq -r '.train_id // "Unknown"') | Line: $(echo "$payload" | jq -r '.line_id // "Unknown"') | Status: $(echo "$payload" | jq -r '.status // "Unknown"')"
    else
        print_error "Failed to send payload #$payload_num"
        return 1
    fi
}

# Function to check if Kafka is running
check_kafka() {
    print_status "Checking Kafka connectivity..."
    
    if ./scripts/kafka-topics.sh --list > /dev/null 2>&1; then
        print_success "Kafka is running and accessible"
        return 0
    else
        print_error "Cannot connect to Kafka at $KAFKA_SERVERS"
        print_error "Please ensure Kafka is running:"
        print_error "  ./rtd-control.sh docker start"
        print_error "  or"
        print_error "  ./scripts/docker-setup start"
        return 1
    fi
}

# Function to check if the rail comm topic exists
check_topic() {
    print_status "Checking if topic '$KAFKA_TOPIC' exists..."
    
    if ./scripts/kafka-topics.sh --list | grep -q "^$KAFKA_TOPIC$"; then
        print_success "Topic '$KAFKA_TOPIC' exists"
        return 0
    else
        print_warning "Topic '$KAFKA_TOPIC' does not exist"
        print_status "Creating topic '$KAFKA_TOPIC'..."
        
        if ./scripts/kafka-topics.sh --create --topic "$KAFKA_TOPIC" --partitions 2 --replication-factor 1; then
            print_success "Topic '$KAFKA_TOPIC' created successfully"
            return 0
        else
            print_error "Failed to create topic '$KAFKA_TOPIC'"
            return 1
        fi
    fi
}

# Function to start monitoring the topic (if requested)
start_monitoring() {
    print_status "Starting real-time monitoring of topic '$KAFKA_TOPIC'"
    print_status "Press Ctrl+C to stop monitoring"
    echo
    
    ./scripts/kafka-console-consumer.sh --topic "$KAFKA_TOPIC" --bootstrap-server "$KAFKA_SERVERS" --from-beginning
}

# Function to show the current contents of the topic
show_topic_contents() {
    print_status "Showing current contents of topic '$KAFKA_TOPIC':"
    echo
    
    # Use timeout to prevent hanging if no messages
    timeout 5s ./scripts/kafka-console-consumer.sh --topic "$KAFKA_TOPIC" --bootstrap-server "$KAFKA_SERVERS" --from-beginning --max-messages 10 2>/dev/null || {
        print_warning "No messages found in topic or timeout reached"
    }
}

# Main execution
main() {
    cd "$PROJECT_DIR"
    
    case "${1:-send}" in
        "send"|"test")
            print_status "=== RTD Rail Communication Pipeline Test ==="
            echo
            
            # Check prerequisites
            check_kafka || exit 1
            check_topic || exit 1
            
            echo
            print_status "Sending sample rail communication payloads..."
            echo
            
            # Send each sample payload
            local payload_num=1
            while IFS= read -r -d '' payload; do
                if [ -n "$payload" ]; then
                    send_payload "$payload" "$payload_num"
                    payload_num=$((payload_num + 1))
                    sleep 1
                fi
            done < <(get_sample_payloads | jq -c . | tr '\n' '\0')
            
            echo
            print_success "All test payloads sent successfully!"
            print_status "The RTD Rail Communication Pipeline should now be processing these messages"
            print_status "Check the pipeline output for processing results"
            ;;
        "monitor"|"watch")
            check_kafka || exit 1
            check_topic || exit 1
            start_monitoring
            ;;
        "show"|"contents")
            check_kafka || exit 1
            check_topic || exit 1
            show_topic_contents
            ;;
        "help"|"-h"|"--help")
            echo "RTD Rail Communication Pipeline Test Script"
            echo
            echo "Usage: $0 [COMMAND]"
            echo
            echo "Commands:"
            echo "  send        Send sample rail comm JSON payloads (default)"
            echo "  test        Alias for send"
            echo "  monitor     Monitor the rail comm topic in real-time"
            echo "  watch       Alias for monitor"
            echo "  show        Show current contents of the topic"
            echo "  contents    Alias for show"
            echo "  help        Show this help message"
            echo
            echo "Prerequisites:"
            echo "  - Kafka must be running (./rtd-control.sh docker start)"
            echo "  - RTD Rail Comm Pipeline should be running to process messages"
            echo
            echo "Examples:"
            echo "  $0                    # Send test payloads"
            echo "  $0 send               # Send test payloads"
            echo "  $0 monitor            # Monitor topic in real-time"
            echo "  $0 show               # Show current topic contents"
            ;;
        *)
            print_error "Unknown command: $1"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Check dependencies
if ! command -v jq &> /dev/null; then
    print_warning "jq is not installed - JSON formatting may not work correctly"
    print_status "Install with: brew install jq (macOS) or apt-get install jq (Linux)"
fi

# Run main function with all arguments
main "$@"