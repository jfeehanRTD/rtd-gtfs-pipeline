#!/bin/bash

# RTD Pipeline Control Script
# Manages Java pipeline and React web app processes
# Supports both local and Docker containerized execution

set -e

# Configuration
RTD_PIPELINE_CLASS="com.rtd.pipeline.RTDStaticDataPipeline"
KAFKA_MAIN_CLASS="${KAFKA_MAIN_CLASS:-com.rtd.pipeline.DirectKafkaBridge}"
SPRING_BOOT_API_CLASS="com.rtd.pipeline.RTDApiApplication"  # Use Spring Boot API server
REACT_APP_DIR="rtd-maps-app"
RTD_PIPELINE_LOG_FILE="rtd-pipeline.log"
SPRING_BOOT_LOG_FILE="rtd-api-server.log"
REACT_LOG_FILE="react-app.log"
DOCKER_SETUP_SCRIPT="./scripts/docker-setup"

# Note: Removed test data receivers - now using only real RTD GTFS-RT data

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[RTD Control]${NC} $1"
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

# Function to check if a process is running by pattern
check_process() {
    local pattern="$1"
    pgrep -f "$pattern" > /dev/null 2>&1
}

# Function to get PIDs of processes matching pattern
get_pids() {
    local pattern="$1"
    pgrep -f "$pattern" 2>/dev/null || true
}

# Function to start the Spring Boot API server
start_java() {
    print_status "Starting RTD Spring Boot API Server..."
    
    if check_process "$RTD_PIPELINE_CLASS"; then
        print_warning "API server is already running"
        return 0
    fi
    
    # Start in background with logging
    nohup mvn exec:java -Dexec.mainClass="$RTD_PIPELINE_CLASS" -q > "$RTD_PIPELINE_LOG_FILE" 2>&1 &
    local java_pid=$!
    
    # Wait a moment to check if it started successfully
    sleep 5  # Spring Boot takes a bit longer to start
    
    if kill -0 $java_pid 2>/dev/null; then
        print_success "Spring Boot API server started (PID: $java_pid)"
        print_status "API: http://localhost:8080/api/health"
        print_status "Logs: tail -f $RTD_PIPELINE_LOG_FILE"
    else
        print_error "Failed to start Spring Boot API server"
        return 1
    fi
}

# Function to start the React app
start_react() {
    print_status "Starting React Web App..."
    
    # Check for npm start processes in the React directory or vite on port 3002/3003
    if check_process "npm start" && [ -f "$REACT_APP_DIR/package.json" ]; then
        print_warning "React app is already running"
        return 0
    fi
    
    if [ ! -d "$REACT_APP_DIR" ]; then
        print_error "React app directory not found: $REACT_APP_DIR"
        return 1
    fi
    
    # Start in background with logging
    cd "$REACT_APP_DIR"
    nohup npm start > "../$REACT_LOG_FILE" 2>&1 &
    local react_pid=$!
    cd ..
    
    # Wait a moment to check if it started successfully
    sleep 5
    
    if kill -0 $react_pid 2>/dev/null; then
        print_success "React app started (PID: $react_pid)"
        print_status "Logs: tail -f $REACT_LOG_FILE"
        print_status "URL: http://localhost:3000/"
    else
        print_error "Failed to start React app"
        return 1
    fi
}

# Function to stop Java pipeline
stop_java() {
    print_status "Stopping RTD Spring Boot API Server..."
    
    local pids=$(get_pids "$RTD_PIPELINE_CLASS")
    if [ -z "$pids" ]; then
        print_warning "No API server processes found"
        return 0
    fi
    
    for pid in $pids; do
        print_status "Killing API server process $pid"
        kill -TERM $pid 2>/dev/null || true
        
        # Wait up to 10 seconds for graceful shutdown
        local count=0
        while kill -0 $pid 2>/dev/null && [ $count -lt 10 ]; do
            sleep 1
            count=$((count + 1))
        done
        
        # Force kill if still running
        if kill -0 $pid 2>/dev/null; then
            print_warning "Force killing Java process $pid"
            kill -KILL $pid 2>/dev/null || true
        fi
    done
    
    print_success "Spring Boot API server stopped"
}

# Function to stop React app
stop_react() {
    print_status "Stopping React Web App..."
    
    # Get both npm and vite processes related to the React app
    local npm_pids=$(get_pids "npm start")
    local vite_pids=$(get_pids "vite.*3000")
    local esbuild_pids=$(get_pids "esbuild")
    
    # Combine all PIDs
    local all_pids="$npm_pids $vite_pids $esbuild_pids"
    
    if [ -z "$all_pids" ] || [ "$all_pids" = "   " ]; then
        print_warning "No React app processes found"
        return 0
    fi
    
    for pid in $all_pids; do
        if [ -n "$pid" ] && [ "$pid" != " " ]; then
            print_status "Killing React process $pid"
            kill -TERM $pid 2>/dev/null || true
            
            # Wait up to 5 seconds for graceful shutdown
            local count=0
            while kill -0 $pid 2>/dev/null && [ $count -lt 5 ]; do
                sleep 1
                count=$((count + 1))
            done
            
            # Force kill if still running
            if kill -0 $pid 2>/dev/null; then
                print_warning "Force killing React process $pid"
                kill -KILL $pid 2>/dev/null || true
            fi
        fi
    done
    
    print_success "React app stopped"
}

# Note: Removed Bus SIRI and Rail Communication HTTP Receivers
# Now using only real RTD GTFS-RT data from RTDStaticDataPipeline

# Function to show status of all processes
status() {
    print_status "RTD Pipeline Status"
    echo
    
    # Check RTD Live Data Pipeline
    if check_process "$RTD_PIPELINE_CLASS"; then
        local rtd_pids=$(get_pids "$RTD_PIPELINE_CLASS")
        print_success "RTD Live Data Pipeline: RUNNING (PIDs: $rtd_pids)"
        print_status "  ↳ Health: http://localhost:8080/api/health"
        print_status "  ↳ Vehicle Data: http://localhost:8080/api/vehicles"
        print_status "  ↳ Schedule Data: http://localhost:8080/api/schedule"
    else
        print_warning "RTD Live Data Pipeline: STOPPED"
    fi
    
    # Check React app
    if check_process "npm start" && [ -f "$REACT_APP_DIR/package.json" ]; then
        local npm_pids=$(get_pids "npm start")
        local vite_pids=$(get_pids "vite.*3000")
        local react_pids="$npm_pids $vite_pids"
        print_success "React Web App: RUNNING (PIDs: $react_pids)"
        print_status "  ↳ URL: http://localhost:3000/"
        print_status "  ↳ Live Transit Map: http://localhost:3000/live"
    else
        print_warning "React Web App: STOPPED"
    fi
    
    echo
    
    # Check port usage
    print_status "Port Status:"
    if lsof -ti:8080 > /dev/null 2>&1; then
        print_status "  ↳ Port 8080: IN USE (RTD Pipeline API)"
    else
        print_status "  ↳ Port 8080: AVAILABLE"
    fi
    
    if lsof -ti:3000 > /dev/null 2>&1; then
        print_status "  ↳ Port 3000: IN USE (React App)"
    else
        print_status "  ↳ Port 3000: AVAILABLE"
    fi
}

# Container runtime functions (Docker/Podman)

# Detect available container runtime
detect_container_runtime() {
    # Check if user specified runtime
    if [ -n "$CONTAINER_RUNTIME" ]; then
        echo "$CONTAINER_RUNTIME"
        return
    fi
    
    # Auto-detect runtime
    if command -v podman &> /dev/null && podman info &> /dev/null; then
        echo "podman"
    elif command -v docker &> /dev/null && docker info &> /dev/null; then
        echo "docker"
    else
        echo "none"
    fi
}

# Check if container runtime is available
check_container_runtime() {
    local runtime=$(detect_container_runtime)
    
    case "$runtime" in
        "podman")
            if ! podman info &> /dev/null; then
                print_error "Podman is not running properly"
                print_status "Check Podman: podman info"
                return 1
            fi
            print_status "Using Podman as container runtime"
            return 0
            ;;
        "docker")
            if ! docker info &> /dev/null; then
                print_error "Docker daemon is not running"
                if command -v colima &> /dev/null; then
                    print_status "Start Colima: colima start"
                else
                    print_status "Start Docker Desktop or install Colima"
                fi
                return 1
            fi
            print_status "Using Docker as container runtime"
            return 0
            ;;
        *)
            print_error "No container runtime found"
            print_status "Install Docker: brew install docker colima"
            print_status "Or install Podman: brew install podman"
            return 1
            ;;
    esac
}

# Legacy function for compatibility
check_docker() {
    check_container_runtime
}

# Start container services (Kafka) and pipeline
start_docker() {
    local runtime=$(detect_container_runtime)
    print_status "Starting RTD Pipeline in container mode (${runtime})..."
    
    if ! check_container_runtime; then
        return 1
    fi
    
    # Start Kafka using docker-compose
    if [ -f "$DOCKER_SETUP_SCRIPT" ]; then
        print_status "Starting Kafka services..."
        $DOCKER_SETUP_SCRIPT start
        
        # Wait for Kafka to be ready
        print_status "Waiting for Kafka to be ready..."
        sleep 10
        
        # Create topics if needed
        print_status "Creating RTD topics..."
        if [ -f "./scripts/kafka-topics" ]; then
            ./scripts/kafka-topics --create-rtd-topics || true
        fi
        
        # Start the Kafka-enabled pipeline
        print_status "Starting RTD Kafka Pipeline..."
        nohup mvn exec:java -Dexec.mainClass="$KAFKA_MAIN_CLASS" -q > "$RTD_PIPELINE_LOG_FILE" 2>&1 &
        local pipeline_pid=$!
        
        sleep 5
        
        if kill -0 $pipeline_pid 2>/dev/null; then
            print_success "RTD Pipeline started (PID: $pipeline_pid)"
            print_status "RTD API is available at http://localhost:8080"
            print_status "Kafka is available at localhost:9092"
            print_status "Kafka UI is available at http://localhost:8090"
            print_status "Pipeline logs: tail -f $RTD_PIPELINE_LOG_FILE"
        else
            print_error "Failed to start Kafka pipeline"
            print_status "Check logs: tail -f $RTD_PIPELINE_LOG_FILE"
            return 1
        fi
    else
        print_error "Docker setup script not found: $DOCKER_SETUP_SCRIPT"
        return 1
    fi
}

# Stop Docker services
stop_docker() {
    print_status "Stopping RTD Pipeline in Docker mode..."
    
    # Stop pipeline
    local pids=$(get_pids "$KAFKA_MAIN_CLASS")
    if [ -n "$pids" ]; then
        for pid in $pids; do
            print_status "Stopping Kafka pipeline (PID: $pid)"
            kill -TERM $pid 2>/dev/null || true
            sleep 2
            if kill -0 $pid 2>/dev/null; then
                kill -KILL $pid 2>/dev/null || true
            fi
        done
    fi
    
    # Stop Kafka
    if [ -f "$DOCKER_SETUP_SCRIPT" ]; then
        print_status "Stopping Kafka services..."
        $DOCKER_SETUP_SCRIPT stop
    fi
    
    print_success "Docker services stopped"
}

# Status for container services
status_docker() {
    local runtime=$(detect_container_runtime)
    print_status "Container Services Status (${runtime})"
    echo
    
    # Check container runtime
    if check_container_runtime; then
        print_success "$(echo ${runtime} | tr '[:lower:]' '[:upper:]'): RUNNING"
    else
        print_warning "$(echo ${runtime} | tr '[:lower:]' '[:upper:]'): NOT RUNNING"
        return
    fi
    
    # Check Kafka (works with both docker and podman)
    local container_cmd="${runtime}"
    if ${container_cmd} ps | grep -q "rtd-kafka" > /dev/null 2>&1; then
        print_success "Kafka: RUNNING (localhost:9092)"
        print_status "  ↳ Kafka UI: http://localhost:8090"
    else
        print_warning "Kafka: STOPPED"
    fi
    
    # Check pipeline
    if check_process "$KAFKA_MAIN_CLASS"; then
        local pids=$(get_pids "$KAFKA_MAIN_CLASS")
        print_success "Kafka Pipeline: RUNNING (PIDs: $pids)"
    else
        print_warning "Kafka Pipeline: STOPPED"
    fi
    
    # Show Kafka topics if available
    if ${container_cmd} ps | grep -q "rtd-kafka" > /dev/null 2>&1; then
        echo
        print_status "Kafka Topics:"
        if [ -f "./scripts/kafka-topics" ]; then
            ./scripts/kafka-topics --list 2>/dev/null | head -10 || true
        fi
    fi
}

# Function to clean up logs and temp files
cleanup() {
    print_status "Cleaning up RTD Pipeline files..."
    
    # Remove log files
    [ -f "$RTD_PIPELINE_LOG_FILE" ] && rm -f "$RTD_PIPELINE_LOG_FILE" && print_success "Removed $RTD_PIPELINE_LOG_FILE"
    [ -f "$REACT_LOG_FILE" ] && rm -f "$REACT_LOG_FILE" && print_success "Removed $REACT_LOG_FILE"
    # Note: Bus and Rail receiver log files removed - now using only RTD Pipeline
    
    # Remove nohup.out if it exists
    [ -f "nohup.out" ] && rm -f "nohup.out" && print_success "Removed nohup.out"
    
    # Clean React build artifacts if they exist
    if [ -d "$REACT_APP_DIR/dist" ]; then
        rm -rf "$REACT_APP_DIR/dist" && print_success "Removed React build artifacts"
    fi
    
    # Clean Maven target if user confirms
    if [ -d "target" ]; then
        echo -n "Remove Maven target directory? [y/N]: "
        read -r response
        if [[ "$response" =~ ^[Yy]$ ]]; then
            rm -rf "target" && print_success "Removed Maven target directory"
        fi
    fi
    
    print_success "Cleanup completed"
}

# Function to show logs
logs() {
    local service="$1"
    
    case "$service" in
        "java")
            if [ -f "$RTD_PIPELINE_LOG_FILE" ]; then
                print_status "Showing RTD pipeline logs (Ctrl+C to exit):"
                tail -f "$RTD_PIPELINE_LOG_FILE"
            else
                print_error "RTD pipeline log file not found: $RTD_PIPELINE_LOG_FILE"
            fi
            ;;
        "react")
            if [ -f "$REACT_LOG_FILE" ]; then
                print_status "Showing React app logs (Ctrl+C to exit):"
                tail -f "$REACT_LOG_FILE"
            else
                print_error "React log file not found: $REACT_LOG_FILE"
            fi
            ;;
        "bus")
            if [ -f "bus-siri-receiver.log" ]; then
                print_status "Showing Bus SIRI receiver logs (Ctrl+C to exit):"
                tail -f "bus-siri-receiver.log"
            else
                print_error "Bus SIRI receiver log file not found: bus-siri-receiver.log"
                print_status "Use './rtd-control.sh bus-comm receiver' to start the Bus SIRI receiver"
            fi
            ;;
        *)
            print_error "Please specify 'java', 'react', or 'bus' for logs"
            ;;
    esac
}

# Function to restart services
restart() {
    local service="$1"
    
    case "$service" in
        "java")
            stop_java
            sleep 2
            start_java
            ;;
        "react")
            stop_react
            sleep 2
            start_react
            ;;
        "all")
            stop_java
            stop_react
            sleep 3
            start_java
            sleep 3
            start_react
            ;;
        *)
            print_error "Please specify 'java', 'react', or 'all' for restart"
            ;;
    esac
}

# Main script logic
main() {
    case "${1:-}" in
        "start")
            case "${2:-all}" in
                "java")
                    start_java
                    ;;
                "react")
                    start_react
                    ;;
                "all")
                    start_java
                    sleep 3
                    start_react
                    ;;
                *)
                    print_error "Usage: $0 start [java|react|all]"
                    exit 1
                    ;;
            esac
            ;;
        "stop")
            case "${2:-all}" in
                "java")
                    stop_java
                    ;;
                "react")
                    stop_react
                    ;;
                "all")
                    stop_java
                    stop_react
                    ;;
                *)
                    print_error "Usage: $0 stop [java|react|all]"
                    exit 1
                    ;;
            esac
            ;;
        "docker"|"podman")
            # Set runtime based on command
            if [ "$1" = "podman" ]; then
                export CONTAINER_RUNTIME="podman"
            fi
            case "${2:-}" in
                "start")
                    start_docker
                    sleep 3
                    start_react
                    ;;
                "stop")
                    stop_docker
                    stop_react
                    ;;
                "restart")
                    stop_docker
                    stop_react
                    sleep 3
                    start_docker
                    sleep 3
                    start_react
                    ;;
                "status")
                    status_docker
                    ;;
                *)
                    print_error "Usage: $0 ${1} [start|stop|restart|status]"
                    print_status "${1^} mode runs Kafka + Pipeline + React App"
                    exit 1
                    ;;
            esac
            ;;
        "restart")
            restart "${2:-all}"
            ;;
        "status")
            status
            ;;
        "logs")
            logs "$2"
            ;;
        "cleanup")
            cleanup
            ;;
        "rail-comm")
            case "${2:-run}" in
                "run")
                    print_status "Starting RTD Rail Communication Pipeline..."
                    if ! check_process "RTDRailCommPipeline"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDRailCommPipeline"
                    else
                        print_warning "Rail comm pipeline is already running"
                    fi
                    ;;
                "receiver")
                    print_status "Starting RTD Rail Comm HTTP Receiver..."
                    if ! check_process "RailCommHTTPReceiver"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RailCommHTTPReceiver"
                    else
                        print_warning "Rail comm HTTP receiver is already running"
                    fi
                    ;;
                "bridge")
                    print_status "Starting RTD Direct Kafka Bridge..."
                    if ! check_process "DirectKafkaBridge"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.DirectKafkaBridge"
                    else
                        print_warning "Direct Kafka Bridge is already running"
                    fi
                    ;;
                "bridge-stop")
                    print_status "Stopping RTD Direct Kafka Bridge..."
                    local bridge_pids=$(get_pids "DirectKafkaBridge")
                    if [[ -n "$bridge_pids" ]]; then
                        echo "$bridge_pids" | xargs kill -TERM
                        print_success "Direct Kafka Bridge stopped"
                    else
                        print_warning "Direct Kafka Bridge is not running"
                    fi
                    ;;
                "test")
                    print_status "Testing RTD Rail Communication Pipeline..."
                    ./scripts/test-rail-comm.sh send
                    ;;
                "monitor")
                    print_status "Monitoring rail comm topic with metrics..."
                    # Enhanced monitoring with connection counts and error tracking
                    {
                        echo -e "${BLUE}========================================${NC}"
                        echo -e "${BLUE}Rail Comm Enhanced Monitoring - $(date)${NC}"
                        echo -e "${BLUE}========================================${NC}"
                        echo ""
                        
                        # Initialize counters
                        local message_count=0
                        local connection_count=0
                        local error_count=0
                        local start_time=$(date +%s)
                        local last_interval=$(date +%s)
                        local messages_this_interval=0
                        local connections_this_interval=0
                        local errors_this_interval=0
                        
                        print_status "Starting Rail Comm monitoring with metrics..."
                        print_status "Press Ctrl+C to stop monitoring"
                        echo ""
                        
                        # Start Kafka consumer in background and capture output (simple mode for just values)
                        ./scripts/kafka-console-consumer.sh --topic rtd.railcomm --simple | while IFS= read -r line; do
                            current_time=$(date +%s)
                            message_count=$((message_count + 1))
                            messages_this_interval=$((messages_this_interval + 1))
                            
                            # Check if this looks like a Rail Comm connection message
                            if echo "$line" | grep -q "rail\|train\|track\|station\|departure\|arrival\|schedule" 2>/dev/null; then
                                connection_count=$((connection_count + 1))
                                connections_this_interval=$((connections_this_interval + 1))
                            fi
                            
                            # Check for error patterns
                            if echo "$line" | grep -qi "error\|failed\|exception\|timeout\|unavailable" 2>/dev/null; then
                                error_count=$((error_count + 1))
                                errors_this_interval=$((errors_this_interval + 1))
                                print_error "Rail Comm Error detected: $(echo "$line" | cut -c1-80)..."
                            fi
                            
                            # Print metrics summary every 5 seconds
                            if [ $((current_time - last_interval)) -ge 5 ]; then
                                elapsed_seconds=$(( current_time - start_time ))
                                elapsed_minutes=$(( elapsed_seconds / 60 ))
                                if [ $elapsed_minutes -eq 0 ]; then elapsed_minutes=1; fi
                                
                                avg_messages_per_min=$(( message_count / elapsed_minutes ))
                                avg_connections_per_min=$(( connection_count / elapsed_minutes ))
                                
                                echo ""
                                echo -e "${GREEN}📊 Rail Comm Metrics (${elapsed_seconds}s) - $(date '+%H:%M:%S')${NC}"
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo -e "${YELLOW}Messages (last 5s):${NC} $messages_this_interval"
                                echo -e "${YELLOW}Connections (last 5s):${NC} $connections_this_interval"
                                echo -e "${YELLOW}Errors (last 5s):${NC} $errors_this_interval"
                                echo ""
                                echo -e "${GREEN}📈 Running Totals (${elapsed_minutes}m):${NC}"
                                echo -e "${YELLOW}Total Messages:${NC} $message_count (avg: $avg_messages_per_min/min)"
                                echo -e "${YELLOW}Total Connections:${NC} $connection_count (avg: $avg_connections_per_min/min)"
                                echo -e "${YELLOW}Total Errors:${NC} $error_count"
                                
                                if [ $connection_count -gt 0 ]; then
                                    connection_rate=$(echo "scale=1; $connection_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${YELLOW}Connection Rate:${NC} ${connection_rate}%"
                                fi
                                
                                if [ $error_count -gt 0 ]; then
                                    error_rate=$(echo "scale=2; $error_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${RED}Error Rate:${NC} ${error_rate}%"
                                fi
                                
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo ""
                                
                                # Reset interval counters
                                messages_this_interval=0
                                connections_this_interval=0
                                errors_this_interval=0
                                last_interval=$current_time
                            fi
                            
                            # Show full message content formatted
                            timestamp=$(date '+%H:%M:%S')
                            echo -e "${BLUE}[$timestamp] MESSAGE:${NC}"
                            # Pretty print JSON if it's JSON, otherwise show raw
                            if echo "$line" | jq . 2>/dev/null; then
                                echo "$line" | jq -C '.' 2>/dev/null
                            else
                                echo "$line"
                            fi
                            echo -e "${BLUE}─────────────────────────────────────${NC}"
                        done
                    }
                    ;;
                "subscribe")
                    print_status "Subscribing to rail comm proxy feed..."
                    if [ -f "./railcomm-subscribe.sh" ]; then
                        ./railcomm-subscribe.sh "$3" "$4" "$5"
                    else
                        print_error "Rail communication subscription script not found"
                    fi
                    ;;
                "subscribe-bridge")
                    print_status "Subscribing to rail comm proxy feed (Direct Kafka Bridge)..."
                    ./scripts/proxy-subscribe-bridge.sh bridge
                    ;;
                "subscribe-kafka")
                    print_status "Subscribing to rail comm proxy feed (Direct Kafka endpoint)..."
                    ./scripts/proxy-subscribe-bridge.sh kafka
                    ;;
                "test-endpoints")
                    print_status "Testing all rail comm endpoints..."
                    ./scripts/proxy-subscribe-bridge.sh test
                    ;;
                "benchmark")
                    print_status "Benchmarking rail comm endpoints..."
                    ./scripts/proxy-subscribe-bridge.sh benchmark
                    ;;
                "test-bridge")
                    print_status "Testing Direct Kafka Bridge..."
                    ./scripts/test-direct-kafka-bridge.sh
                    ;;
                "unsubscribe")
                    print_status "Unsubscribing from rail comm proxy feed..."
                    ./scripts/proxy-subscribe.sh unsubscribe
                    ;;
                "unsubscribe-all")
                    print_status "Unsubscribing from all rail comm endpoints..."
                    ./scripts/proxy-subscribe-bridge.sh unsubscribe
                    ;;
                *)
                    print_error "Usage: $0 rail-comm [COMMAND]"
                    echo
                    print_status "Core Services:"
                    print_status "  run              - Start the rail comm pipeline"
                    print_status "  receiver         - Start HTTP receiver for proxy data (original)"
                    print_status "  bridge           - Start Direct Kafka Bridge (optimized)"
                    print_status "  bridge-stop      - Stop Direct Kafka Bridge"
                    echo
                    print_status "Testing & Monitoring:"
                    print_status "  test             - Send test JSON payloads"
                    print_status "  monitor          - Monitor rail comm topic with connection metrics and error tracking"
                    print_status "  test-endpoints   - Test connectivity to all endpoints"
                    print_status "  test-bridge      - Test Direct Kafka Bridge functionality"
                    print_status "  benchmark        - Benchmark performance of all endpoints"
                    echo
                    print_status "Proxy Subscription:"
                    print_status "  subscribe        - Subscribe using original HTTP receiver"
                    print_status "  subscribe-bridge - Subscribe using Direct Kafka Bridge"
                    print_status "  subscribe-kafka  - Subscribe using direct Kafka endpoint"
                    print_status "  unsubscribe      - Unsubscribe from original endpoint"
                    print_status "  unsubscribe-all  - Unsubscribe from all endpoints"
                    exit 1
                    ;;
            esac
            ;;
        "bus-comm")
            case "${2:-run}" in
                "run")
                    print_status "Starting RTD Bus Communication Pipeline (SIRI Simple Table API)..."
                    if ! check_process "RTDBusCommSimplePipeline"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDBusCommSimplePipeline"
                    else
                        print_warning "Bus comm pipeline is already running"
                    fi
                    ;;
                "receiver")
                    print_status "Starting RTD Bus SIRI HTTP Receiver..."
                    if ! check_process "BusCommHTTPReceiver"; then
                        # Detect VPN IP if available
                        VPN_IP=""
                        if [[ -f "./scripts/detect-vpn-ip.sh" ]]; then
                            source <(./scripts/detect-vpn-ip.sh export 2>/dev/null)
                            if [[ -n "$SIRI_HOST_IP" ]]; then
                                VPN_IP="$SIRI_HOST_IP"
                                if [[ "$USE_VPN_IP" == "true" ]]; then
                                    print_status "Using VPN IP for receiver: $VPN_IP"
                                else
                                    print_status "Using network IP for receiver: $VPN_IP"
                                fi
                            fi
                        fi
                        
                        if [[ -n "$VPN_IP" ]]; then
                            mvn exec:java -Dexec.mainClass="com.rtd.pipeline.BusCommHTTPReceiver" -Dexec.args="--bind-address $VPN_IP"
                        else
                            print_warning "Could not detect IP address, binding to all interfaces"
                            mvn exec:java -Dexec.mainClass="com.rtd.pipeline.BusCommHTTPReceiver"
                        fi
                    else
                        print_warning "Bus SIRI HTTP receiver is already running"
                    fi
                    ;;
                "subscribe")
                    print_status "Subscribing to SIRI bus feed..."
                    if [ -f "./scripts/bus-siri-subscribe.sh" ]; then
                        ./scripts/bus-siri-subscribe.sh "$3" "$4" "$5"
                    else
                        print_error "Bus SIRI subscription script not found"
                    fi
                    ;;
                "test")
                    print_status "Testing Bus SIRI Communication..."
                    if [ -f "./scripts/bus-siri-subscribe.sh" ]; then
                        ./scripts/bus-siri-subscribe.sh
                    else
                        print_error "Bus SIRI test script not found"
                    fi
                    ;;
                "monitor")
                    print_status "Monitoring bus SIRI topic with metrics..."
                    # Enhanced monitoring with connection counts and error tracking
                    {
                        echo -e "${BLUE}========================================${NC}"
                        echo -e "${BLUE}Bus SIRI Enhanced Monitoring - $(date)${NC}"
                        echo -e "${BLUE}========================================${NC}"
                        echo ""
                        
                        # Initialize counters
                        local message_count=0
                        local connection_count=0
                        local error_count=0
                        local start_time=$(date +%s)
                        local last_interval=$(date +%s)
                        local messages_this_interval=0
                        local connections_this_interval=0
                        local errors_this_interval=0
                        
                        print_status "Starting Bus SIRI monitoring with metrics..."
                        print_status "Press Ctrl+C to stop monitoring"
                        echo ""
                        
                        # Start Kafka consumer in background and capture output (simple mode for just values)
                        ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri --simple | while IFS= read -r line; do
                            current_time=$(date +%s)
                            message_count=$((message_count + 1))
                            messages_this_interval=$((messages_this_interval + 1))
                            
                            # Check if this looks like a Bus SIRI connection message
                            if echo "$line" | grep -q "bus\|vehicle\|route\|stop\|siri\|journey" 2>/dev/null; then
                                connection_count=$((connection_count + 1))
                                connections_this_interval=$((connections_this_interval + 1))
                            fi
                            
                            # Check for error patterns
                            if echo "$line" | grep -qi "error\|failed\|exception\|timeout\|unavailable" 2>/dev/null; then
                                error_count=$((error_count + 1))
                                errors_this_interval=$((errors_this_interval + 1))
                                print_error "Bus SIRI Error detected: $(echo "$line" | cut -c1-80)..."
                            fi
                            
                            # Print metrics summary every 5 seconds
                            if [ $((current_time - last_interval)) -ge 5 ]; then
                                elapsed_seconds=$(( current_time - start_time ))
                                elapsed_minutes=$(( elapsed_seconds / 60 ))
                                if [ $elapsed_minutes -eq 0 ]; then elapsed_minutes=1; fi
                                
                                avg_messages_per_min=$(( message_count / elapsed_minutes ))
                                avg_connections_per_min=$(( connection_count / elapsed_minutes ))
                                
                                echo ""
                                echo -e "${GREEN}📊 Bus SIRI Metrics (${elapsed_seconds}s) - $(date '+%H:%M:%S')${NC}"
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo -e "${YELLOW}Messages (last 5s):${NC} $messages_this_interval"
                                echo -e "${YELLOW}Connections (last 5s):${NC} $connections_this_interval"
                                echo -e "${YELLOW}Errors (last 5s):${NC} $errors_this_interval"
                                echo ""
                                echo -e "${GREEN}📈 Running Totals (${elapsed_minutes}m):${NC}"
                                echo -e "${YELLOW}Total Messages:${NC} $message_count (avg: $avg_messages_per_min/min)"
                                echo -e "${YELLOW}Total Connections:${NC} $connection_count (avg: $avg_connections_per_min/min)"
                                echo -e "${YELLOW}Total Errors:${NC} $error_count"
                                
                                if [ $connection_count -gt 0 ]; then
                                    connection_rate=$(echo "scale=1; $connection_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${YELLOW}Connection Rate:${NC} ${connection_rate}%"
                                fi
                                
                                if [ $error_count -gt 0 ]; then
                                    error_rate=$(echo "scale=2; $error_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${RED}Error Rate:${NC} ${error_rate}%"
                                fi
                                
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo ""
                                
                                # Reset interval counters
                                messages_this_interval=0
                                connections_this_interval=0
                                errors_this_interval=0
                                last_interval=$current_time
                            fi
                            
                            # Show full message content formatted
                            timestamp=$(date '+%H:%M:%S')
                            echo -e "${BLUE}[$timestamp] MESSAGE:${NC}"
                            # Pretty print JSON if it's JSON, otherwise show raw
                            if echo "$line" | jq . 2>/dev/null; then
                                echo "$line" | jq -C '.' 2>/dev/null
                            else
                                echo "$line"
                            fi
                            echo -e "${BLUE}─────────────────────────────────────${NC}"
                        done
                    }
                    ;;
                "status")
                    print_status "Checking Bus SIRI status..."
                    curl -s "http://localhost:8082/status" | jq . 2>/dev/null || curl -s "http://localhost:8082/status"
                    ;;
                *)
                    print_error "Usage: $0 bus-comm [COMMAND]"
                    echo
                    print_status "Core Services:"
                    print_status "  run              - Start the bus SIRI pipeline"
                    print_status "  receiver         - Start HTTP receiver for SIRI data"
                    echo
                    print_status "SIRI Subscription:"
                    print_status "  subscribe [host] [service] [ttl] - Subscribe to SIRI feed"
                    print_status "  test             - Test SIRI subscription with defaults"
                    echo
                    print_status "Monitoring:"
                    print_status "  monitor          - Monitor bus SIRI topic with connection metrics and error tracking"
                    print_status "  status           - Check receiver status"
                    exit 1
                    ;;
            esac
            ;;
        "lrgps")
            case "${2:-run}" in
                "run")
                    print_status "Starting RTD LRGPS Pipeline..."
                    if ! check_process "LRGPSPipeline"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.LRGPSPipeline"
                    else
                        print_warning "LRGPS pipeline is already running"
                    fi
                    ;;
                "receiver")
                    print_status "Starting RTD LRGPS HTTP Receiver..."
                    if ! check_process "LRGPSHTTPReceiver"; then
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.LRGPSHTTPReceiver"
                    else
                        print_warning "LRGPS HTTP receiver is already running"
                    fi
                    ;;
                "subscribe")
                    print_status "Subscribing to LRGPS feed..."
                    if [ -f "./scripts/lrgps-subscribe.sh" ]; then
                        ./scripts/lrgps-subscribe.sh "$3" "$4" "$5"
                    else
                        print_error "LRGPS subscription script not found"
                    fi
                    ;;
                "test")
                    print_status "Testing LRGPS Communication..."
                    if [ -f "./scripts/lrgps-subscribe.sh" ]; then
                        ./scripts/lrgps-subscribe.sh
                    else
                        print_error "LRGPS test script not found"
                    fi
                    ;;
                "monitor")
                    print_status "Monitoring LRGPS topic with metrics..."
                    # Enhanced monitoring with connection counts and error tracking
                    {
                        echo -e "${BLUE}========================================${NC}"
                        echo -e "${BLUE}LRGPS Enhanced Monitoring - $(date)${NC}"
                        echo -e "${BLUE}========================================${NC}"
                        echo ""
                        
                        # Initialize counters
                        local message_count=0
                        local connection_count=0
                        local error_count=0
                        local start_time=$(date +%s)
                        local last_interval=$(date +%s)
                        local messages_this_interval=0
                        local connections_this_interval=0
                        local errors_this_interval=0
                        
                        print_status "Starting LRGPS monitoring with metrics..."
                        print_status "Press Ctrl+C to stop monitoring"
                        echo ""
                        
                        # Start Kafka consumer in background and capture output (simple mode for just values)
                        ./scripts/kafka-console-consumer.sh --topic rtd.lrgps --simple | while IFS= read -r line; do
                            current_time=$(date +%s)
                            message_count=$((message_count + 1))
                            messages_this_interval=$((messages_this_interval + 1))
                            
                            # Check if this looks like an LRGPS connection message
                            if echo "$line" | grep -q "vehicle\|position\|gps\|coordinate\|location" 2>/dev/null; then
                                connection_count=$((connection_count + 1))
                                connections_this_interval=$((connections_this_interval + 1))
                            fi
                            
                            # Check for error patterns
                            if echo "$line" | grep -qi "error\|failed\|exception\|timeout\|unavailable" 2>/dev/null; then
                                error_count=$((error_count + 1))
                                errors_this_interval=$((errors_this_interval + 1))
                                print_error "LRGPS Error detected: $(echo "$line" | cut -c1-80)..."
                            fi
                            
                            # Print metrics summary every 5 seconds
                            if [ $((current_time - last_interval)) -ge 5 ]; then
                                elapsed_seconds=$(( current_time - start_time ))
                                elapsed_minutes=$(( elapsed_seconds / 60 ))
                                if [ $elapsed_minutes -eq 0 ]; then elapsed_minutes=1; fi
                                
                                avg_messages_per_min=$(( message_count / elapsed_minutes ))
                                avg_connections_per_min=$(( connection_count / elapsed_minutes ))
                                
                                echo ""
                                echo -e "${GREEN}📊 LRGPS Metrics (${elapsed_seconds}s) - $(date '+%H:%M:%S')${NC}"
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo -e "${YELLOW}Messages (last 5s):${NC} $messages_this_interval"
                                echo -e "${YELLOW}Connections (last 5s):${NC} $connections_this_interval"
                                echo -e "${YELLOW}Errors (last 5s):${NC} $errors_this_interval"
                                echo ""
                                echo -e "${GREEN}📈 Running Totals (${elapsed_minutes}m):${NC}"
                                echo -e "${YELLOW}Total Messages:${NC} $message_count (avg: $avg_messages_per_min/min)"
                                echo -e "${YELLOW}Total Connections:${NC} $connection_count (avg: $avg_connections_per_min/min)"
                                echo -e "${YELLOW}Total Errors:${NC} $error_count"
                                
                                if [ $connection_count -gt 0 ]; then
                                    connection_rate=$(echo "scale=1; $connection_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${YELLOW}Connection Rate:${NC} ${connection_rate}%"
                                fi
                                
                                if [ $error_count -gt 0 ]; then
                                    error_rate=$(echo "scale=2; $error_count * 100 / $message_count" | bc 2>/dev/null || echo "N/A")
                                    echo -e "${RED}Error Rate:${NC} ${error_rate}%"
                                fi
                                
                                echo -e "${BLUE}─────────────────────────────────────${NC}"
                                echo ""
                                
                                # Reset interval counters
                                messages_this_interval=0
                                connections_this_interval=0
                                errors_this_interval=0
                                last_interval=$current_time
                            fi
                            
                            # Show full message content formatted
                            timestamp=$(date '+%H:%M:%S')
                            echo -e "${BLUE}[$timestamp] MESSAGE:${NC}"
                            # Pretty print JSON if it's JSON, otherwise show raw
                            if echo "$line" | jq . 2>/dev/null; then
                                echo "$line" | jq -C '.' 2>/dev/null
                            else
                                echo "$line"
                            fi
                            echo -e "${BLUE}─────────────────────────────────────${NC}"
                        done
                    }
                    ;;
                "status")
                    print_status "Checking LRGPS status..."
                    curl -s "http://localhost:8083/status" | jq . 2>/dev/null || curl -s "http://localhost:8083/status"
                    ;;
                "unsubscribe")
                    print_status "Unsubscribing from LRGPS feed..."
                    # Send unsubscribe request to TIS proxy
                    AUTH_HEADER=""
                    if [[ -n "$TIS_PROXY_USERNAME" && -n "$TIS_PROXY_PASSWORD" ]]; then
                        AUTH_HEADER="-u $TIS_PROXY_USERNAME:$TIS_PROXY_PASSWORD"
                    fi
                    TIS_HOST="${TIS_PROXY_HOST:-http://tisproxy.rtd-denver.com}"
                    curl -s -X DELETE $AUTH_HEADER "$TIS_HOST/unsubscribe/lrgps" || print_warning "Failed to unsubscribe from TIS proxy"
                    print_success "LRGPS unsubscribe request sent"
                    ;;
                *)
                    print_error "Usage: $0 lrgps [COMMAND]"
                    echo
                    print_status "Core Services:"
                    print_status "  run              - Start the LRGPS pipeline"
                    print_status "  receiver         - Start HTTP receiver for LRGPS data"
                    echo
                    print_status "LRGPS Subscription:"
                    print_status "  subscribe [host] [service] [ttl] - Subscribe to LRGPS feed"
                    print_status "  test             - Test LRGPS subscription with defaults"
                    print_status "  unsubscribe      - Unsubscribe from LRGPS feed"
                    echo
                    print_status "Monitoring:"
                    print_status "  monitor          - Monitor LRGPS topic with connection metrics and error tracking"
                    print_status "  status           - Check receiver status"
                    exit 1
                    ;;
            esac
            ;;
        "gtfs-rt")
            case "${2:-pipeline}" in
                "pipeline")
                    print_status "Starting RTD GTFS-RT Generation Pipeline..."
                    if ! check_process "WorkingGTFSRTPipeline"; then
                        MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.misc=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.security=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -Xmx4g -Xms1g -XX:+UseG1GC -XX:+IgnoreUnrecognizedVMOptions" \
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.WorkingGTFSRTPipeline"
                    else
                        print_warning "GTFS-RT pipeline is already running"
                    fi
                    ;;
                "publisher")
                    print_status "Starting RTD GTFS-RT Publisher (HTTP Server)..."
                    if ! check_process "GTFSRTPublisher"; then
                        nohup mvn exec:java -Dexec.mainClass="com.rtd.pipeline.gtfsrt.GTFSRTPublisher" -q > "gtfs-rt-publisher.log" 2>&1 &
                        local pub_pid=$!
                        sleep 3
                        if kill -0 $pub_pid 2>/dev/null; then
                            print_success "GTFS-RT Publisher started (PID: $pub_pid)"
                            print_status "Vehicle Positions: http://localhost:8084/files/gtfs-rt/VehiclePosition.pb"
                            print_status "Trip Updates: http://localhost:8084/files/gtfs-rt/TripUpdate.pb"
                            print_status "Alerts: http://localhost:8084/files/gtfs-rt/Alerts.pb"
                            print_status "Status: http://localhost:8084/gtfs-rt/status"
                        else
                            print_error "Failed to start GTFS-RT Publisher"
                        fi
                    else
                        print_warning "GTFS-RT Publisher is already running"
                    fi
                    ;;
                "stop-publisher")
                    print_status "Stopping RTD GTFS-RT Publisher..."
                    local pids=$(get_pids "GTFSRTPublisher")
                    if [[ -n "$pids" ]]; then
                        echo "$pids" | xargs kill -TERM
                        sleep 2
                        # Check if still running and force kill if needed
                        local remaining_pids=$(get_pids "GTFSRTPublisher")
                        if [[ -n "$remaining_pids" ]]; then
                            echo "$remaining_pids" | xargs kill -KILL
                        fi
                        print_success "GTFS-RT Publisher stopped"
                    else
                        print_warning "GTFS-RT Publisher is not running"
                    fi
                    ;;
                "all")
                    print_status "Starting GTFS-RT Pipeline and Publisher..."
                    # Start publisher first
                    "$0" gtfs-rt publisher
                    sleep 3
                    # Start pipeline
                    "$0" gtfs-rt pipeline
                    ;;
                "stop-all")
                    print_status "Stopping all GTFS-RT services..."
                    "$0" gtfs-rt stop-publisher
                    local pipeline_pids=$(get_pids "WorkingGTFSRTPipeline")
                    if [[ -n "$pipeline_pids" ]]; then
                        echo "$pipeline_pids" | xargs kill -TERM
                        sleep 2
                        local remaining_pids=$(get_pids "WorkingGTFSRTPipeline")
                        if [[ -n "$remaining_pids" ]]; then
                            echo "$remaining_pids" | xargs kill -KILL
                        fi
                        print_success "GTFS-RT Pipeline stopped"
                    fi
                    ;;
                "status")
                    print_status "GTFS-RT Services Status:"
                    echo
                    
                    # Check GTFS-RT Pipeline
                    if check_process "WorkingGTFSRTPipeline"; then
                        local pipeline_pids=$(get_pids "WorkingGTFSRTPipeline")
                        print_success "GTFS-RT Pipeline: RUNNING (PIDs: $pipeline_pids)"
                    else
                        print_warning "GTFS-RT Pipeline: STOPPED"
                    fi
                    
                    # Check GTFS-RT Publisher
                    if check_process "GTFSRTPublisher"; then
                        local pub_pids=$(get_pids "GTFSRTPublisher")
                        print_success "GTFS-RT Publisher: RUNNING (PIDs: $pub_pids)"
                        print_status "  ↳ Vehicle Positions: http://localhost:8084/files/gtfs-rt/VehiclePosition.pb"
                        print_status "  ↳ Trip Updates: http://localhost:8084/files/gtfs-rt/TripUpdate.pb"
                        print_status "  ↳ Status: http://localhost:8084/gtfs-rt/status"
                    else
                        print_warning "GTFS-RT Publisher: STOPPED"
                    fi
                    
                    # Check for generated files
                    if [ -d "data/gtfs-rt" ]; then
                        echo
                        print_status "Generated GTFS-RT Files:"
                        for file in "data/gtfs-rt/VehiclePosition.pb" "data/gtfs-rt/TripUpdate.pb" "data/gtfs-rt/Alerts.pb"; do
                            if [ -f "$file" ]; then
                                local size=$(ls -lh "$file" | awk '{print $5}')
                                local modified=$(ls -l "$file" | awk '{print $6, $7, $8}')
                                print_status "  ↳ $(basename $file): ${size} (modified: ${modified})"
                            else
                                print_status "  ↳ $(basename $file): NOT FOUND"
                            fi
                        done
                    fi
                    ;;
                "test")
                    print_status "Testing GTFS-RT endpoints..."
                    local base_url="http://localhost:8084"
                    
                    # Test status endpoint
                    print_status "Testing status endpoint..."
                    curl -s "$base_url/gtfs-rt/status" | jq . 2>/dev/null || curl -s "$base_url/gtfs-rt/status"
                    
                    echo
                    print_status "Testing protobuf endpoints..."
                    
                    # Test protobuf endpoints
                    for endpoint in "VehiclePosition.pb" "TripUpdate.pb" "Alerts.pb"; do
                        local url="$base_url/files/gtfs-rt/$endpoint"
                        local status=$(curl -s -o /dev/null -w "%{http_code}" "$url")
                        if [ "$status" = "200" ]; then
                            local size=$(curl -s -I "$url" | grep -i content-length | awk '{print $2}' | tr -d '\r')
                            print_success "$endpoint: OK (${size:-unknown} bytes)"
                        else
                            print_warning "$endpoint: HTTP $status"
                        fi
                    done
                    ;;
                "compare")
                    print_status "Comparing generated feeds with official RTD feeds..."
                    if [ -f "./scripts/compare-gtfs-rt-feeds.sh" ]; then
                        ./scripts/compare-gtfs-rt-feeds.sh
                    else
                        print_error "Feed comparison script not found"
                        print_status "Expected: ./scripts/compare-gtfs-rt-feeds.sh"
                    fi
                    ;;
                "compare-quick")
                    print_status "Running quick feed comparison (test only)..."
                    if [ -f "./scripts/compare-gtfs-rt-feeds.sh" ]; then
                        ./scripts/compare-gtfs-rt-feeds.sh --test-only
                    else
                        print_error "Feed comparison script not found"
                    fi
                    ;;
                *)
                    print_error "Usage: $0 gtfs-rt [COMMAND]"
                    echo
                    print_status "Available Commands:"
                    print_status "  pipeline         - Start GTFS-RT generation pipeline (consumes from Kafka)"
                    print_status "  publisher        - Start GTFS-RT HTTP publisher server (port 8084)"
                    print_status "  all              - Start both pipeline and publisher"
                    print_status "  stop-publisher   - Stop GTFS-RT HTTP publisher"
                    print_status "  stop-all         - Stop all GTFS-RT services"
                    print_status "  status           - Show status of GTFS-RT services and files"
                    print_status "  test             - Test GTFS-RT endpoints and connectivity"
                    print_status "  compare          - Compare generated feeds with official RTD feeds"
                    print_status "  compare-quick    - Quick comparison (Java test only)"
                    echo
                    print_status "Endpoints (when publisher is running):"
                    print_status "  Vehicle Positions: http://localhost:8084/files/gtfs-rt/VehiclePosition.pb"
                    print_status "  Trip Updates: http://localhost:8084/files/gtfs-rt/TripUpdate.pb"
                    print_status "  Alerts: http://localhost:8084/files/gtfs-rt/Alerts.pb"
                    print_status "  Status: http://localhost:8084/gtfs-rt/status"
                    print_status "  Health: http://localhost:8084/gtfs-rt/health"
                    exit 1
                    ;;
            esac
            ;;
        "gtfs-table")
            case "${2:-interactive}" in
                "interactive")
                    print_status "Starting RTD GTFS Interactive Table System..."
                    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSInteractiveTable"
                    ;;
                "processor")
                    print_status "Running RTD GTFS Table Processor..."
                    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSTableProcessor"
                    ;;
                "api")
                    print_status "Running RTD GTFS Table API Analysis..."
                    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSTableAPI"
                    ;;
                "test")
                    print_status "Running GTFS Table tests..."
                    mvn test -Dtest="RTDGTFSTableTest"
                    ;;
                "validate")
                    print_status "Running comprehensive GTFS validation..."
                    mvn test -Dtest="GTFSValidationTest"
                    ;;
                "analyze")
                    print_status "Running RTD runboard change analysis..."
                    MAVEN_OPTS="-Xmx2G" mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDRunboardAnalyzer"
                    ;;
                "demo")
                    print_status "Running RTD GTFS Table API Demo..."
                    mvn exec:java -Dexec.mainClass="com.rtd.pipeline.RTDGTFSTableDemo"
                    ;;
                *)
                    print_error "Usage: $0 gtfs-table [COMMAND]"
                    echo
                    print_status "Available Commands:"
                    print_status "  demo             - Run RTD GTFS Table API demonstration"
                    print_status "  interactive      - Interactive SQL query system for RTD GTFS data"
                    print_status "  processor        - Process and create tables from RTD GTFS static files"
                    print_status "  api              - Run comprehensive GTFS Table API analysis"
                    print_status "  test             - Run GTFS table processing tests"
                    print_status "  validate         - Comprehensive GTFS file validation and integrity checks"
                    print_status "  analyze          - RTD runboard change analysis (Aug 25, 2025 focus)"
                    echo
                    print_status "Features:"
                    print_status "  - Downloads RTD GTFS static data (routes, stops, schedules)"
                    print_status "  - Creates Flink tables with proper schemas"
                    print_status "  - Provides SQL query interface for transit network analysis"
                    print_status "  - Supports complex joins and aggregations"
                    exit 1
                    ;;
            esac
            ;;
        "help"|"-h"|"--help")
            echo "RTD Pipeline Control Script"
            echo
            echo "Usage: $0 COMMAND [OPTIONS]"
            echo
            echo "Commands:"
            echo "  start [java|react|all]    Start services locally (default: all)"
            echo "  stop [java|react|all]     Stop services (default: all)"
            echo "  restart [java|react|all]  Restart services (default: all)"
            echo "  docker [start|stop|status] Run with Docker/Kafka (recommended)"
            echo "  podman [start|stop|status] Run with Podman/Kafka (Docker alternative)"
            echo "  status                    Show status of all services"
            echo "  logs [java|react] Show real-time logs"
            echo "  rail-comm [run|test|monitor] Rail communication pipeline commands"
            echo "  bus-comm [run|receiver|monitor] Bus communication pipeline (SIRI)"
            echo "  lrgps [run|receiver|monitor] LRGPS pipeline commands"
            echo "  gtfs-rt [pipeline|publisher|all] GTFS-RT generation and publishing"
            echo "  gtfs-table [interactive|processor|api] GTFS static data table system"
            echo "  cleanup                   Clean up log files and temp directories"
            echo "  help                      Show this help message"
            echo
            echo "Container Mode (Docker/Podman):"
            echo "  $0 docker start           # Start with Docker (auto-detected)"
            echo "  $0 podman start           # Start with Podman (force Podman)"
            echo "  $0 docker stop            # Stop all container services"
            echo "  $0 docker status          # Show container services status"
            echo "  $0 docker restart         # Restart container services"
            echo
            echo "Local Mode Examples:"
            echo "  $0 start                  # Start all services (RTD Pipeline & React)"
            echo "  $0 start java             # Start only RTD pipeline"
            echo "  $0 start react            # Start only React app"
            echo "  $0 restart all            # Restart all services"
            echo "  $0 status                 # Check status"
            echo "  $0 logs java              # Follow RTD pipeline logs"
            echo "  $0 logs react             # Follow React app logs"
            echo "  $0 cleanup                # Clean up files"
            echo
            echo "Rail Communication Pipeline:"
            echo "  $0 rail-comm run          # Start rail comm pipeline"
            echo "  $0 rail-comm receiver     # Start HTTP receiver for proxy data"
            echo "  $0 rail-comm test         # Send test JSON payloads"
            echo "  $0 rail-comm monitor      # Monitor rail comm topic with connection metrics"
            echo "  $0 rail-comm subscribe    # Subscribe to proxy feed"
            echo "  $0 rail-comm unsubscribe  # Unsubscribe from proxy feed"
            echo
            echo "Bus Communication Pipeline (SIRI):"
            echo "  $0 bus-comm run           # Start bus SIRI pipeline"
            echo "  $0 bus-comm receiver      # Start SIRI HTTP receiver"
            echo "  $0 bus-comm subscribe     # Subscribe to SIRI bus feed"
            echo "  $0 bus-comm test          # Test SIRI subscription"
            echo "  $0 bus-comm monitor       # Monitor bus SIRI topic with connection metrics"
            echo "  $0 bus-comm status        # Check receiver status"
            echo ""
            echo "LRGPS Pipeline:"
            echo "  $0 lrgps run              # Start LRGPS pipeline"
            echo "  $0 lrgps receiver         # Start LRGPS HTTP receiver"
            echo "  $0 lrgps subscribe        # Subscribe to LRGPS feed"
            echo "  $0 lrgps test             # Test LRGPS subscription"
            echo "  $0 lrgps unsubscribe      # Unsubscribe from LRGPS feed"
            echo "  $0 lrgps monitor          # Monitor LRGPS topic with connection metrics"
            echo "  $0 lrgps status           # Check receiver status"
            echo ""
            echo "GTFS-RT Generation Pipeline:"
            echo "  $0 gtfs-rt pipeline       # Start GTFS-RT generation pipeline (Kafka consumer)"
            echo "  $0 gtfs-rt publisher      # Start GTFS-RT HTTP publisher server"
            echo "  $0 gtfs-rt all            # Start both pipeline and publisher"
            echo "  $0 gtfs-rt stop-all       # Stop all GTFS-RT services"
            echo "  $0 gtfs-rt status         # Check GTFS-RT services status"
            echo "  $0 gtfs-rt test           # Test GTFS-RT endpoints"
            echo ""
            echo "GTFS Static Data Table System:"
            echo "  $0 gtfs-table interactive # Interactive SQL query system"
            echo "  $0 gtfs-table processor   # Process all RTD GTFS files into tables"
            echo "  $0 gtfs-table api         # Run comprehensive transit network analysis"
            echo "  $0 gtfs-table test        # Run GTFS table processing tests"
            echo
            echo "Container Mode Features (Docker/Podman):"
            echo "  - RTD API at http://localhost:8080"
            echo "  - Kafka at localhost:9092"
            echo "  - Kafka UI at http://localhost:8090"
            echo "  - RTD Pipeline provides real-time data"
            echo "  - Auto-detects Docker or Podman runtime"
            echo "  - Use CONTAINER_RUNTIME=podman to force Podman"
            echo "  - React app displays live vehicle positions"
            echo ""
            echo "Colima Management (Docker on macOS):"
            echo "  ./scripts/colima-control.sh start    # Start optimized Colima"
            echo "  ./scripts/colima-control.sh stop     # Stop Colima"
            echo "  ./scripts/colima-control.sh status   # Check status"
            echo "  ./scripts/colima-control.sh cleanup  # Clean up resources"
            ;;
        *)
            print_error "Unknown command: ${1:-}"
            print_status "Run '$0 help' for usage information"
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"