#!/bin/bash

# RTD Pipeline Control Script
# Manages Java pipeline and React web app processes

set -e

# Configuration
JAVA_MAIN_CLASS="com.rtd.pipeline.RTDStaticDataPipeline"
REACT_APP_DIR="rtd-maps-app"
JAVA_LOG_FILE="rtd-pipeline.log"
REACT_LOG_FILE="react-app.log"

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

# Function to start the Java pipeline
start_java() {
    print_status "Starting RTD Java Pipeline..."
    
    if check_process "$JAVA_MAIN_CLASS"; then
        print_warning "Java pipeline is already running"
        return 0
    fi
    
    # Start in background with logging
    nohup mvn exec:java -Dexec.mainClass="$JAVA_MAIN_CLASS" -q > "$JAVA_LOG_FILE" 2>&1 &
    local java_pid=$!
    
    # Wait a moment to check if it started successfully
    sleep 3
    
    if kill -0 $java_pid 2>/dev/null; then
        print_success "Java pipeline started (PID: $java_pid)"
        print_status "Logs: tail -f $JAVA_LOG_FILE"
    else
        print_error "Failed to start Java pipeline"
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
        print_status "URL: http://localhost:3002/"
    else
        print_error "Failed to start React app"
        return 1
    fi
}

# Function to stop Java pipeline
stop_java() {
    print_status "Stopping RTD Java Pipeline..."
    
    local pids=$(get_pids "$JAVA_MAIN_CLASS")
    if [ -z "$pids" ]; then
        print_warning "No Java pipeline processes found"
        return 0
    fi
    
    for pid in $pids; do
        print_status "Killing Java process $pid"
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
    
    print_success "Java pipeline stopped"
}

# Function to stop React app
stop_react() {
    print_status "Stopping React Web App..."
    
    # Get both npm and vite processes related to the React app
    local npm_pids=$(get_pids "npm start")
    local vite_pids=$(get_pids "vite.*300")
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

# Function to show status of all processes
status() {
    print_status "RTD Pipeline Status"
    echo
    
    # Check Java pipeline
    if check_process "$JAVA_MAIN_CLASS"; then
        local java_pids=$(get_pids "$JAVA_MAIN_CLASS")
        print_success "Java Pipeline: RUNNING (PIDs: $java_pids)"
        print_status "  ↳ API: http://localhost:8080/api/health"
    else
        print_warning "Java Pipeline: STOPPED"
    fi
    
    # Check React app
    if check_process "npm start" && [ -f "$REACT_APP_DIR/package.json" ]; then
        local npm_pids=$(get_pids "npm start")
        local vite_pids=$(get_pids "vite.*300")
        local react_pids="$npm_pids $vite_pids"
        print_success "React Web App: RUNNING (PIDs: $react_pids)"
        print_status "  ↳ URL: http://localhost:3002/"
    else
        print_warning "React Web App: STOPPED"
    fi
    
    echo
    
    # Check port usage
    print_status "Port Status:"
    if lsof -ti:8080 > /dev/null 2>&1; then
        print_status "  ↳ Port 8080: IN USE (Java API)"
    else
        print_status "  ↳ Port 8080: AVAILABLE"
    fi
    
    if lsof -ti:3002 > /dev/null 2>&1; then
        print_status "  ↳ Port 3002: IN USE (React App)"
    else
        print_status "  ↳ Port 3002: AVAILABLE"
    fi
}

# Function to clean up logs and temp files
cleanup() {
    print_status "Cleaning up RTD Pipeline files..."
    
    # Remove log files
    [ -f "$JAVA_LOG_FILE" ] && rm -f "$JAVA_LOG_FILE" && print_success "Removed $JAVA_LOG_FILE"
    [ -f "$REACT_LOG_FILE" ] && rm -f "$REACT_LOG_FILE" && print_success "Removed $REACT_LOG_FILE"
    
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
        *)
            print_error "Please specify 'java' or 'react' for logs"
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
        "help"|"-h"|"--help")
            echo "RTD Pipeline Control Script"
            echo
            echo "Usage: $0 COMMAND [OPTIONS]"
            echo
            echo "Commands:"
            echo "  start [java|react|all]    Start services (default: all)"
            echo "  stop [java|react|all]     Stop services (default: all)"
            echo "  restart [java|react|all]  Restart services (default: all)"
            echo "  status                    Show status of all services"
            echo "  logs [java|react]         Show real-time logs"
            echo "  cleanup                   Clean up log files and temp directories"
            echo "  help                      Show this help message"
            echo
            echo "Examples:"
            echo "  $0 start                  # Start both Java pipeline and React app"
            echo "  $0 start java             # Start only Java pipeline"
            echo "  $0 stop react             # Stop only React app"
            echo "  $0 restart all            # Restart both services"
            echo "  $0 status                 # Check status"
            echo "  $0 logs java              # Follow Java logs"
            echo "  $0 cleanup                # Clean up files"
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