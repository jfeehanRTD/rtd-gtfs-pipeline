#!/bin/bash

# Colima Control Script for RTD Pipeline Project
# Optimized for Kafka, Flink, and multiple Java processes

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Colima configuration optimized for RTD Pipeline on Apple M4
COLIMA_PROFILE="rtd-pipeline"
COLIMA_CPU=8        # 8 CPU cores (M4 has excellent multi-core performance)
COLIMA_MEMORY=12    # 12GB RAM for Kafka, Flink, and Java processes
COLIMA_DISK=80      # 80GB disk space for data persistence
COLIMA_ARCH="aarch64" # Use ARM64 natively for M4 performance

# Function to print colored output
print_status() {
    echo -e "${BLUE}[Colima Control]${NC} $1"
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

# Function to detect Apple Silicon and get system information
get_system_info() {
    local total_memory_gb=$(sysctl -n hw.memsize | awk '{print int($1/1024/1024/1024)}')
    local cpu_cores=$(sysctl -n hw.ncpu)
    local cpu_brand=$(sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "Unknown")
    local chip_name=$(system_profiler SPHardwareDataType | grep "Chip:" | awk -F': ' '{print $2}' | xargs)
    
    echo "System Information:"
    echo "  Chip: ${chip_name:-$cpu_brand}"
    echo "  Total Memory: ${total_memory_gb}GB"
    echo "  CPU Cores: ${cpu_cores}"
    
    # Detect specific Apple Silicon variants
    if echo "$chip_name" | grep -qi "M4"; then
        if echo "$chip_name" | grep -qi "Max"; then
            echo "  M4 Variant: Max (High-performance optimizations enabled)"
        elif echo "$chip_name" | grep -qi "Pro"; then
            echo "  M4 Variant: Pro (Performance optimizations enabled)"
        else
            echo "  M4 Variant: Standard (Balanced optimizations enabled)"
        fi
    elif echo "$chip_name" | grep -qi "Apple"; then
        echo "  Apple Silicon: Detected (ARM64 optimizations enabled)"
    fi
    echo ""
    
    # Adjust configuration based on available resources for Apple M4
    if [ "$total_memory_gb" -lt 16 ]; then
        print_warning "Low system memory detected. Reducing Colima memory allocation."
        COLIMA_MEMORY=8
    elif [ "$total_memory_gb" -ge 32 ]; then
        print_success "Excellent system memory for M4. Optimizing allocation."
        COLIMA_MEMORY=16
    elif [ "$total_memory_gb" -ge 64 ]; then
        print_success "High-end system detected. Maximum performance allocation."
        COLIMA_MEMORY=20
    fi
    
    if [ "$cpu_cores" -ge 14 ]; then
        print_success "M4 Max detected. Maximum performance allocation."
        COLIMA_CPU=14
    elif [ "$cpu_cores" -ge 10 ]; then
        print_success "M4 Pro detected. High-performance allocation."
        COLIMA_CPU=10
    elif [ "$cpu_cores" -lt 8 ]; then
        print_warning "Lower CPU cores detected. Conservative allocation."
        COLIMA_CPU=6
    fi
}

# Function to set up Docker environment for the profile
setup_docker_env() {
    # Try to use the profile-specific socket first, fall back to system default
    if [ -S "${HOME}/.colima/${COLIMA_PROFILE}/docker.sock" ]; then
        export DOCKER_HOST="unix://${HOME}/.colima/${COLIMA_PROFILE}/docker.sock"
    elif [ -S "${HOME}/.colima/default/docker.sock" ]; then
        export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
    else
        # Let Docker use its default detection
        unset DOCKER_HOST
    fi
}

# Function to check if Colima is installed
check_colima_installed() {
    if ! command -v colima &> /dev/null; then
        print_error "Colima is not installed"
        echo "Install with: brew install colima"
        echo "Also install Docker CLI: brew install docker docker-compose"
        exit 1
    fi
}

# Function to start Colima with optimized settings
start_colima() {
    print_status "Starting Colima with RTD Pipeline optimized settings..."
    
    get_system_info
    
    echo "Colima Configuration:"
    echo "  Profile: ${COLIMA_PROFILE}"
    echo "  CPU Cores: ${COLIMA_CPU}"
    echo "  Memory: ${COLIMA_MEMORY}GB"
    echo "  Disk: ${COLIMA_DISK}GB"
    echo "  Architecture: ${COLIMA_ARCH}"
    echo ""
    
    # Check if profile already exists and is running
    if colima list | grep -q "^${COLIMA_PROFILE}.*Running"; then
        print_success "Colima profile '${COLIMA_PROFILE}' is already running"
        show_status
        return 0
    fi
    
    # Stop any existing profile to avoid conflicts
    if colima list | grep -q "^${COLIMA_PROFILE}"; then
        print_status "Stopping existing Colima profile..."
        colima stop "${COLIMA_PROFILE}" 2>/dev/null || true
    fi
    
    # Start Colima with M4-optimized settings
    print_status "Starting Colima with M4 optimizations (this may take a few minutes)..."
    
    # M4-specific optimizations
    colima start "${COLIMA_PROFILE}" \
        --cpu "${COLIMA_CPU}" \
        --memory "${COLIMA_MEMORY}" \
        --disk "${COLIMA_DISK}" \
        --arch "${COLIMA_ARCH}" \
        --vm-type=vz \
        --vz-rosetta \
        --mount-type=virtiofs \
        --mount "${HOME}:${HOME}:w" \
        --network-address \
        --dns=1.1.1.1,8.8.8.8
    
    if [ $? -eq 0 ]; then
        print_success "Colima started successfully"
        
        # Wait a moment for Docker to be ready
        print_status "Waiting for Docker to be ready..."
        sleep 5
        
        # Set up Docker environment for this profile
        setup_docker_env
        
        # Test Docker connectivity
        if docker info &> /dev/null; then
            print_success "Docker is ready"
            print_status "Docker Host: $DOCKER_HOST"
            show_status
        else
            print_warning "Docker may not be fully ready yet"
            print_status "Docker Host: $DOCKER_HOST"
        fi
    else
        print_error "Failed to start Colima"
        exit 1
    fi
}

# Function to stop Colima
stop_colima() {
    print_status "Stopping Colima profile '${COLIMA_PROFILE}'..."
    
    if colima list | grep -q "^${COLIMA_PROFILE}.*Running"; then
        colima stop "${COLIMA_PROFILE}"
        print_success "Colima stopped successfully"
    else
        print_warning "Colima profile '${COLIMA_PROFILE}' is not running"
    fi
}

# Function to restart Docker daemon in Colima
restart_docker() {
    print_status "Restarting Docker daemon in Colima..."
    
    if ! colima list | grep -q "^${COLIMA_PROFILE}.*Running"; then
        print_error "Colima profile '${COLIMA_PROFILE}' is not running"
        print_status "Start Colima first: ./scripts/colima-control.sh start"
        return 1
    fi
    
    # Restart docker service inside the VM
    print_status "Restarting Docker service..."
    colima ssh "${COLIMA_PROFILE}" -- sudo rc-service docker restart 2>/dev/null || \
    colima ssh "${COLIMA_PROFILE}" -- sudo systemctl restart docker 2>/dev/null || \
    print_warning "Could not restart Docker service - may need full restart"
    
    # Wait for Docker to be ready
    sleep 5
    
    setup_docker_env
    if docker info &> /dev/null; then
        print_success "Docker daemon restarted successfully"
    else
        print_warning "Docker daemon still not accessible - try full restart"
        print_status "Run: ./scripts/colima-control.sh restart"
    fi
}

# Function to restart Colima
restart_colima() {
    print_status "Restarting Colima..."
    stop_colima
    sleep 2
    start_colima
}

# Function to show Colima status
show_status() {
    print_status "Colima Status:"
    echo ""
    
    # Show all Colima instances
    echo "Colima Instances:"
    colima list
    echo ""
    
    # Check Docker connectivity 
    echo "Docker Environment:"
    echo "  Docker CLI: $(docker --version 2>/dev/null || echo 'Not installed')"
    
    # Test Docker connectivity with multiple approaches
    setup_docker_env
    
    # First try the profile-specific socket
    if [ -S "${HOME}/.colima/${COLIMA_PROFILE}/docker.sock" ]; then
        export DOCKER_HOST="unix://${HOME}/.colima/${COLIMA_PROFILE}/docker.sock"
        if docker info &> /dev/null; then
            echo "  ✅ Docker daemon accessible via profile socket"
            echo "  Host: $DOCKER_HOST"
            
            # Show running containers
            local running_containers=$(docker ps --format "{{.Names}}" 2>/dev/null | wc -l | tr -d ' ')
            echo "  Running Containers: ${running_containers}"
            if [ "$running_containers" -gt 0 ]; then
                echo ""
                docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null
            fi
        else
            echo "  ⚠️ Profile socket exists but Docker daemon not responding"
            echo "  Socket: ${HOME}/.colima/${COLIMA_PROFILE}/docker.sock"
            
            # Check if colima is actually running
            if colima list | grep -q "^${COLIMA_PROFILE}.*Running"; then
                echo "  Colima VM is running - Docker daemon may be starting up"
                echo "  Try restarting: ./scripts/colima-control.sh restart"
            else
                echo "  Colima VM is not running properly"
            fi
        fi
    else
        echo "  ❌ Profile socket not found: ${HOME}/.colima/${COLIMA_PROFILE}/docker.sock"
        
        # Try default socket
        if [ -S "${HOME}/.colima/default/docker.sock" ]; then
            export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
            if docker info &> /dev/null; then
                echo "  ✅ Docker daemon accessible via default socket"
                echo "  Host: $DOCKER_HOST"
                
                local running_containers=$(docker ps --format "{{.Names}}" 2>/dev/null | wc -l | tr -d ' ')
                echo "  Running Containers: ${running_containers}"
            else
                echo "  ⚠️ Default socket exists but Docker daemon not responding"
            fi
        else
            echo "  ❌ No Docker sockets found - Colima may not be started"
        fi
    fi
}

# Function to delete Colima profile
delete_profile() {
    print_warning "This will delete the Colima profile '${COLIMA_PROFILE}' and all its data"
    echo -n "Are you sure? [y/N]: "
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        print_status "Deleting Colima profile '${COLIMA_PROFILE}'..."
        
        # Stop if running
        colima stop "${COLIMA_PROFILE}" 2>/dev/null || true
        
        # Delete the profile
        colima delete "${COLIMA_PROFILE}" --force
        
        print_success "Colima profile deleted"
    else
        print_status "Operation cancelled"
    fi
}

# Function to optimize for RTD Pipeline
optimize_for_rtd() {
    print_status "Optimizing Docker settings for RTD Pipeline..."
    
    # Create Docker daemon configuration for better performance
    local docker_config_dir="${HOME}/.colima/${COLIMA_PROFILE}/docker"
    local daemon_config="${docker_config_dir}/daemon.json"
    
    if [ ! -f "$daemon_config" ]; then
        print_status "Creating Docker daemon configuration..."
        mkdir -p "$docker_config_dir"
        
        cat > "$daemon_config" << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  },
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 65536,
      "Soft": 65536
    }
  },
  "max-concurrent-downloads": 20,
  "max-concurrent-uploads": 10,
  "builder": {
    "gc": {
      "enabled": true,
      "defaultKeepStorage": "10GB"
    }
  },
  "features": {
    "buildkit": true
  },
  "experimental": true,
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOF
        print_success "Docker daemon configuration created"
    fi
    
    # Set Docker context to use Colima
    docker context use colima-"${COLIMA_PROFILE}" 2>/dev/null || docker context use colima 2>/dev/null || true
    
    print_success "Docker optimization completed"
}

# Function to show resource usage
show_resources() {
    print_status "Resource Usage:"
    echo ""
    
    if colima list | grep -q "^${COLIMA_PROFILE}.*Running"; then
        # Get Colima VM stats
        echo "Colima VM Resources:"
        colima status "${COLIMA_PROFILE}" 2>/dev/null || echo "  Status information not available"
        echo ""
    fi
    
    setup_docker_env
    if docker info &> /dev/null; then
        echo "Docker Resources:"
        docker system df
        echo ""
        
        echo "Container Resource Usage:"
        docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}\t{{.BlockIO}}" 2>/dev/null || echo "  No containers running"
    fi
}

# Function to clean up Docker resources
cleanup_docker() {
    print_status "Cleaning up Docker resources..."
    
    setup_docker_env
    if ! docker info &> /dev/null; then
        print_error "Docker is not running"
        return 1
    fi
    
    echo "Before cleanup:"
    docker system df
    echo ""
    
    # Remove stopped containers
    print_status "Removing stopped containers..."
    docker container prune -f
    
    # Remove unused networks
    print_status "Removing unused networks..."
    docker network prune -f
    
    # Remove unused volumes
    print_status "Removing unused volumes..."
    docker volume prune -f
    
    # Remove unused images
    print_status "Removing unused images..."
    docker image prune -f
    
    echo "After cleanup:"
    docker system df
    
    print_success "Docker cleanup completed"
}

# Function to show help
show_help() {
    echo "Colima Control Script for RTD Pipeline Project"
    echo ""
    echo "Usage: $0 COMMAND"
    echo ""
    echo "Commands:"
    echo "  start      Start Colima with RTD Pipeline optimized settings"
    echo "  stop       Stop Colima"
    echo "  restart    Restart Colima"
    echo "  restart-docker Restart just the Docker daemon in Colima"
    echo "  status     Show Colima and Docker status"
    echo "  optimize   Optimize Docker settings for RTD Pipeline"
    echo "  resources  Show resource usage"
    echo "  cleanup    Clean up Docker resources (containers, images, volumes)"
    echo "  delete     Delete the Colima profile and all data"
    echo "  help       Show this help message"
    echo ""
    echo "Apple M4 Optimized Configuration:"
    echo "  Profile: ${COLIMA_PROFILE}"
    echo "  CPU: ${COLIMA_CPU} cores (ARM64 native)"
    echo "  Memory: ${COLIMA_MEMORY}GB"
    echo "  Disk: ${COLIMA_DISK}GB"
    echo "  Architecture: ${COLIMA_ARCH} (Apple Silicon optimized)"
    echo "  VM Type: VZ with Rosetta for x86 compatibility"
    echo "  File System: VirtioFS for maximum I/O performance"
    echo ""
    echo "This configuration is optimized for Apple M4:"
    echo "  - M4 Standard (8-10 cores): 8 cores, 12GB RAM"
    echo "  - M4 Pro (10-12 cores): 10 cores, 16GB RAM"
    echo "  - M4 Max (14+ cores): 14 cores, 20GB RAM"
    echo "  - Apache Kafka (4-6GB RAM, ARM64 native performance)"
    echo "  - Apache Flink (2-4GB RAM, optimized for M4 cores)"
    echo "  - Multiple Java processes (native ARM64 JVM)"
    echo "  - RTD real-time data processing (high-performance cores)"
    echo "  - React web application (fast Node.js ARM64)"
    echo "  - Docker BuildKit with ARM64 images preferred"
}

# Main script logic
main() {
    check_colima_installed
    
    case "${1:-}" in
        "start")
            start_colima
            optimize_for_rtd
            ;;
        "stop")
            stop_colima
            ;;
        "restart")
            restart_colima
            optimize_for_rtd
            ;;
        "restart-docker")
            restart_docker
            ;;
        "status")
            show_status
            ;;
        "optimize")
            optimize_for_rtd
            ;;
        "resources")
            show_resources
            ;;
        "cleanup")
            cleanup_docker
            ;;
        "delete")
            delete_profile
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            print_error "Unknown command: ${1:-}"
            echo ""
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"