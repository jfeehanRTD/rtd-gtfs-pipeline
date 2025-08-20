#!/bin/bash

# RTD Pipeline Control Script
# Manages Java pipeline and React web app processes
# Supports both local and Docker containerized execution

set -e

# Configuration
JAVA_MAIN_CLASS="com.rtd.pipeline.RTDStaticDataPipeline"
KAFKA_MAIN_CLASS="com.rtd.pipeline.RTDApiApplication"  # Use Spring Boot API server
BUS_RECEIVER_CLASS="com.rtd.pipeline.BusCommHTTPReceiver"
RAIL_RECEIVER_CLASS="com.rtd.pipeline.RailCommHTTPReceiver"
REACT_APP_DIR="rtd-maps-app"
JAVA_LOG_FILE="rtd-api-server.log"
REACT_LOG_FILE="react-app.log"
BUS_RECEIVER_LOG_FILE="bus-receiver.log"
RAIL_RECEIVER_LOG_FILE="rail-receiver.log"
DOCKER_SETUP_SCRIPT="./scripts/docker-setup"

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
    
    if check_process "$JAVA_MAIN_CLASS"; then
        print_warning "API server is already running"
        return 0
    fi
    
    # Start in background with logging
    nohup mvn exec:java -Dexec.mainClass="$JAVA_MAIN_CLASS" -q > "$JAVA_LOG_FILE" 2>&1 &
    local java_pid=$!
    
    # Wait a moment to check if it started successfully
    sleep 5  # Spring Boot takes a bit longer to start
    
    if kill -0 $java_pid 2>/dev/null; then
        print_success "Spring Boot API server started (PID: $java_pid)"
        print_status "API: http://localhost:8080/api/health"
        print_status "Logs: tail -f $JAVA_LOG_FILE"
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
    
    local pids=$(get_pids "$JAVA_MAIN_CLASS")
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

# Function to start Bus SIRI HTTP Receiver
start_bus_receiver() {
    print_status "Starting Bus SIRI HTTP Receiver..."
    
    if check_process "$BUS_RECEIVER_CLASS"; then
        print_warning "Bus SIRI HTTP Receiver is already running"
        return 0
    fi
    
    # Start in background with logging
    nohup mvn exec:java -Dexec.mainClass="$BUS_RECEIVER_CLASS" -Dexec.cleanupDaemonThreads=false -q > "$BUS_RECEIVER_LOG_FILE" 2>&1 &
    local bus_pid=$!
    
    # Wait a moment to check if it started successfully
    sleep 5
    
    if kill -0 $bus_pid 2>/dev/null; then
        print_success "Bus SIRI HTTP Receiver started (PID: $bus_pid)"
        print_status "  ↳ Endpoint: http://localhost:8082/bus-siri"
        print_status "  ↳ Latest Data: http://localhost:8082/bus-siri/latest"
        print_status "  ↳ Health: http://localhost:8082/health"
        print_status "  ↳ Logs: tail -f $BUS_RECEIVER_LOG_FILE"
    else
        print_error "Failed to start Bus SIRI HTTP Receiver"
        return 1
    fi
}

# Function to start Rail Communication HTTP Receiver
start_rail_receiver() {
    print_status "Starting Rail Communication HTTP Receiver..."
    
    if check_process "$RAIL_RECEIVER_CLASS"; then
        print_warning "Rail Communication HTTP Receiver is already running"
        return 0
    fi
    
    # Start in background with logging
    nohup mvn exec:java -Dexec.mainClass="$RAIL_RECEIVER_CLASS" -Dexec.cleanupDaemonThreads=false -q > "$RAIL_RECEIVER_LOG_FILE" 2>&1 &
    local rail_pid=$!
    
    # Wait a moment to check if it started successfully
    sleep 5
    
    if kill -0 $rail_pid 2>/dev/null; then
        print_success "Rail Communication HTTP Receiver started (PID: $rail_pid)"
        print_status "  ↳ Endpoint: http://localhost:8081/rail-comm"
        print_status "  ↳ Latest Data: http://localhost:8081/rail-comm/latest"
        print_status "  ↳ Health: http://localhost:8081/health"
        print_status "  ↳ Logs: tail -f $RAIL_RECEIVER_LOG_FILE"
    else
        print_error "Failed to start Rail Communication HTTP Receiver"
        return 1
    fi
}

# Function to stop Bus SIRI HTTP Receiver
stop_bus_receiver() {
    print_status "Stopping Bus SIRI HTTP Receiver..."
    
    local pids=$(get_pids "$BUS_RECEIVER_CLASS")
    if [ -z "$pids" ]; then
        print_warning "No Bus SIRI HTTP Receiver processes found"
        return 0
    fi
    
    for pid in $pids; do
        print_status "Killing Bus receiver process $pid"
        kill -TERM $pid 2>/dev/null || true
        
        # Wait up to 10 seconds for graceful shutdown
        local count=0
        while kill -0 $pid 2>/dev/null && [ $count -lt 10 ]; do
            sleep 1
            count=$((count + 1))
        done
        
        # Force kill if still running
        if kill -0 $pid 2>/dev/null; then
            print_warning "Force killing Bus receiver process $pid"
            kill -KILL $pid 2>/dev/null || true
        fi
    done
    
    print_success "Bus SIRI HTTP Receiver stopped"
}

# Function to stop Rail Communication HTTP Receiver
stop_rail_receiver() {
    print_status "Stopping Rail Communication HTTP Receiver..."
    
    local pids=$(get_pids "$RAIL_RECEIVER_CLASS")
    if [ -z "$pids" ]; then
        print_warning "No Rail Communication HTTP Receiver processes found"
        return 0
    fi
    
    for pid in $pids; do
        print_status "Killing Rail receiver process $pid"
        kill -TERM $pid 2>/dev/null || true
        
        # Wait up to 10 seconds for graceful shutdown
        local count=0
        while kill -0 $pid 2>/dev/null && [ $count -lt 10 ]; do
            sleep 1
            count=$((count + 1))
        done
        
        # Force kill if still running
        if kill -0 $pid 2>/dev/null; then
            print_warning "Force killing Rail receiver process $pid"
            kill -KILL $pid 2>/dev/null || true
        fi
    done
    
    print_success "Rail Communication HTTP Receiver stopped"
}

# Function to show status of all processes
status() {
    print_status "RTD Pipeline Status"
    echo
    
    # Check Spring Boot API server
    if check_process "$JAVA_MAIN_CLASS"; then
        local java_pids=$(get_pids "$JAVA_MAIN_CLASS")
        print_success "Spring Boot API Server: RUNNING (PIDs: $java_pids)"
        print_status "  ↳ Health: http://localhost:8080/api/health"
        print_status "  ↳ Occupancy: http://localhost:8080/api/occupancy/status"
    else
        print_warning "Spring Boot API Server: STOPPED"
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
    
    # Check Bus SIRI HTTP Receiver
    if check_process "$BUS_RECEIVER_CLASS"; then
        local bus_pids=$(get_pids "$BUS_RECEIVER_CLASS")
        print_success "Bus SIRI HTTP Receiver: RUNNING (PIDs: $bus_pids)"
        print_status "  ↳ Endpoint: http://localhost:8082/bus-siri"
        print_status "  ↳ Latest Data: http://localhost:8082/bus-siri/latest"
        print_status "  ↳ Health: http://localhost:8082/health"
    else
        print_warning "Bus SIRI HTTP Receiver: STOPPED"
    fi
    
    # Check Rail Communication HTTP Receiver
    if check_process "$RAIL_RECEIVER_CLASS"; then
        local rail_pids=$(get_pids "$RAIL_RECEIVER_CLASS")
        print_success "Rail Communication HTTP Receiver: RUNNING (PIDs: $rail_pids)"
        print_status "  ↳ Endpoint: http://localhost:8081/rail-comm"
        print_status "  ↳ Latest Data: http://localhost:8081/rail-comm/latest"
        print_status "  ↳ Health: http://localhost:8081/health"
    else
        print_warning "Rail Communication HTTP Receiver: STOPPED"
    fi
    
    echo
    
    # Check port usage
    print_status "Port Status:"
    if lsof -ti:8080 > /dev/null 2>&1; then
        print_status "  ↳ Port 8080: IN USE (Java API)"
    else
        print_status "  ↳ Port 8080: AVAILABLE"
    fi
    
    if lsof -ti:3000 > /dev/null 2>&1; then
        print_status "  ↳ Port 3000: IN USE (React App)"
    else
        print_status "  ↳ Port 3000: AVAILABLE"
    fi
    
    if lsof -ti:8082 > /dev/null 2>&1; then
        print_status "  ↳ Port 8082: IN USE (Bus SIRI Receiver)"
    else
        print_status "  ↳ Port 8082: AVAILABLE"
    fi
    
    if lsof -ti:8081 > /dev/null 2>&1; then
        print_status "  ↳ Port 8081: IN USE (Rail Comm Receiver)"
    else
        print_status "  ↳ Port 8081: AVAILABLE"
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
        nohup mvn exec:java -Dexec.mainClass="$KAFKA_MAIN_CLASS" -q > "$JAVA_LOG_FILE" 2>&1 &
        local pipeline_pid=$!
        
        sleep 5
        
        if kill -0 $pipeline_pid 2>/dev/null; then
            print_success "RTD Pipeline started (PID: $pipeline_pid)"
            print_status "RTD API is available at http://localhost:8080"
            print_status "Kafka is available at localhost:9092"
            print_status "Kafka UI is available at http://localhost:8090"
            print_status "Pipeline logs: tail -f $JAVA_LOG_FILE"
        else
            print_error "Failed to start Kafka pipeline"
            print_status "Check logs: tail -f $JAVA_LOG_FILE"
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
    [ -f "$JAVA_LOG_FILE" ] && rm -f "$JAVA_LOG_FILE" && print_success "Removed $JAVA_LOG_FILE"
    [ -f "$REACT_LOG_FILE" ] && rm -f "$REACT_LOG_FILE" && print_success "Removed $REACT_LOG_FILE"
    [ -f "$BUS_RECEIVER_LOG_FILE" ] && rm -f "$BUS_RECEIVER_LOG_FILE" && print_success "Removed $BUS_RECEIVER_LOG_FILE"
    [ -f "$RAIL_RECEIVER_LOG_FILE" ] && rm -f "$RAIL_RECEIVER_LOG_FILE" && print_success "Removed $RAIL_RECEIVER_LOG_FILE"
    
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
            if [ -f "$JAVA_LOG_FILE" ]; then
                print_status "Showing Java pipeline logs (Ctrl+C to exit):"
                tail -f "$JAVA_LOG_FILE"
            else
                print_error "Java log file not found: $JAVA_LOG_FILE"
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
            if [ -f "$BUS_RECEIVER_LOG_FILE" ]; then
                print_status "Showing Bus SIRI HTTP Receiver logs (Ctrl+C to exit):"
                tail -f "$BUS_RECEIVER_LOG_FILE"
            else
                print_error "Bus receiver log file not found: $BUS_RECEIVER_LOG_FILE"
            fi
            ;;
        "rail")
            if [ -f "$RAIL_RECEIVER_LOG_FILE" ]; then
                print_status "Showing Rail Communication HTTP Receiver logs (Ctrl+C to exit):"
                tail -f "$RAIL_RECEIVER_LOG_FILE"
            else
                print_error "Rail receiver log file not found: $RAIL_RECEIVER_LOG_FILE"
            fi
            ;;
        *)
            print_error "Please specify 'java', 'react', 'bus', or 'rail' for logs"
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
        "bus")
            stop_bus_receiver
            sleep 2
            start_bus_receiver
            ;;
        "rail")
            stop_rail_receiver
            sleep 2
            start_rail_receiver
            ;;
        "receivers")
            stop_bus_receiver
            stop_rail_receiver
            sleep 3
            start_bus_receiver
            sleep 3
            start_rail_receiver
            ;;
        "all")
            stop_java
            stop_react
            stop_bus_receiver
            stop_rail_receiver
            sleep 3
            start_java
            sleep 3
            start_react
            sleep 3
            start_bus_receiver
            sleep 3
            start_rail_receiver
            ;;
        *)
            print_error "Please specify 'java', 'react', 'bus', 'rail', 'receivers', or 'all' for restart"
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
                "bus")
                    start_bus_receiver
                    ;;
                "rail")
                    start_rail_receiver
                    ;;
                "receivers")
                    start_bus_receiver
                    sleep 3
                    start_rail_receiver
                    ;;
                "all")
                    start_java
                    sleep 3
                    start_react
                    sleep 3
                    start_bus_receiver
                    sleep 3
                    start_rail_receiver
                    ;;
                *)
                    print_error "Usage: $0 start [java|react|bus|rail|receivers|all]"
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
                "bus")
                    stop_bus_receiver
                    ;;
                "rail")
                    stop_rail_receiver
                    ;;
                "receivers")
                    stop_bus_receiver
                    stop_rail_receiver
                    ;;
                "all")
                    stop_java
                    stop_react
                    stop_bus_receiver
                    stop_rail_receiver
                    ;;
                *)
                    print_error "Usage: $0 stop [java|react|bus|rail|receivers|all]"
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
                "test")
                    print_status "Testing RTD Rail Communication Pipeline..."
                    ./scripts/test-rail-comm.sh send
                    ;;
                "monitor")
                    print_status "Monitoring rail comm topic..."
                    ./scripts/test-rail-comm.sh monitor
                    ;;
                "subscribe")
                    print_status "Subscribing to rail comm proxy feed (original)..."
                    ./scripts/proxy-subscribe.sh send
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
                    echo
                    print_status "Testing & Monitoring:"
                    print_status "  test             - Send test JSON payloads"
                    print_status "  monitor          - Monitor rail comm topic"
                    print_status "  test-endpoints   - Test connectivity to all endpoints"
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
                        mvn exec:java -Dexec.mainClass="com.rtd.pipeline.BusCommHTTPReceiver"
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
                    print_status "Monitoring bus SIRI topic..."
                    ./scripts/kafka-console-consumer.sh --topic rtd.bus.siri
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
                    print_status "  monitor          - Monitor bus SIRI topic"
                    print_status "  status           - Check receiver status"
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
            echo "  start [java|react|bus|rail|receivers|all]    Start services locally (default: all)"
            echo "  stop [java|react|bus|rail|receivers|all]     Stop services (default: all)"
            echo "  restart [java|react|bus|rail|receivers|all]  Restart services (default: all)"
            echo "  docker [start|stop|status] Run with Docker/Kafka (recommended)"
            echo "  podman [start|stop|status] Run with Podman/Kafka (Docker alternative)"
            echo "  status                    Show status of all services"
            echo "  logs [java|react|bus|rail] Show real-time logs"
            echo "  rail-comm [run|test|monitor] Rail communication pipeline commands"
            echo "  bus-comm [run|receiver|monitor] Bus communication pipeline (SIRI)"
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
            echo "  $0 start                  # Start all services (Java, React, Bus & Rail receivers)"
            echo "  $0 start java             # Start only Java pipeline"
            echo "  $0 start receivers        # Start only Bus & Rail HTTP receivers"
            echo "  $0 start bus              # Start only Bus SIRI HTTP receiver"
            echo "  $0 stop rail              # Stop only Rail Communication HTTP receiver"
            echo "  $0 restart all            # Restart all services"
            echo "  $0 status                 # Check status"
            echo "  $0 logs bus               # Follow Bus receiver logs"
            echo "  $0 logs rail              # Follow Rail receiver logs"
            echo "  $0 cleanup                # Clean up files"
            echo
            echo "Rail Communication Pipeline:"
            echo "  $0 rail-comm run          # Start rail comm pipeline"
            echo "  $0 rail-comm receiver     # Start HTTP receiver for proxy data"
            echo "  $0 rail-comm test         # Send test JSON payloads"
            echo "  $0 rail-comm monitor      # Monitor rail comm topic"
            echo "  $0 rail-comm subscribe    # Subscribe to proxy feed"
            echo "  $0 rail-comm unsubscribe  # Unsubscribe from proxy feed"
            echo
            echo "Bus Communication Pipeline (SIRI):"
            echo "  $0 bus-comm run           # Start bus SIRI pipeline"
            echo "  $0 bus-comm receiver      # Start SIRI HTTP receiver"
            echo "  $0 bus-comm subscribe     # Subscribe to SIRI bus feed"
            echo "  $0 bus-comm test          # Test SIRI subscription"
            echo "  $0 bus-comm monitor       # Monitor bus SIRI topic"
            echo "  $0 bus-comm status        # Check receiver status"
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