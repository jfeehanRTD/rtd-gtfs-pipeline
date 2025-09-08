# Data Source Setup Guide

This guide explains how to subscribe to RTD's TIS proxy feeds using the `subscribe-to-tisproxy.sh` script to receive real-time transit data.

## Overview

The RTD GTFS pipeline can receive real-time transit data from three main sources via the TIS (Transit Information System) proxy:

- **SIRI** - Bus real-time data (Service Interface for Real Time Information)
- **LRGPS** - Light Rail GPS positioning data
- **Rail Comm** - Rail communication and scheduling data

## Prerequisites

### 1. Environment Setup

Create a `.env` file in the project root with your TIS proxy credentials:

```bash
# Copy the example file
cp .env.example .env
```

Edit `.env` with your credentials:
```bash
# RTD TIS Proxy Configuration
TIS_PROXY_USERNAME=your_actual_username
TIS_PROXY_PASSWORD=your_actual_password
TIS_PROXY_HOST=http://tisproxy.rtd-denver.com
TIS_PROXY_SERVICE=siri
TIS_PROXY_TTL=90000
```

### 2. Required Services

Before subscribing, you need to start the appropriate HTTP receivers:

```bash
# Start Bus SIRI receiver (port 8082)
./rtd-control.sh bus-comm receiver

# Start LRGPS receiver (port 8083) 
./rtd-control.sh lrgps receiver

# Start Rail Comm receiver (port 8081)
./rtd-control.sh rail-comm receiver
```

### 3. Network Configuration

Determine your local IP address that the TIS proxy can reach:

```bash
# Check your current IP
ifconfig | grep "inet " | grep -v "127.0.0.1"

# Or use the detect script
./scripts/detect-vpn-ip.sh
```

## Using the Subscription Script

### Basic Usage

```bash
./scripts/subscribe-to-tisproxy.sh <ip> <port> [feed_type]
```

**Arguments:**
- `ip` - Your machine's IP address where TIS proxy should send data
- `port` - Port number for the receiver service
- `feed_type` - Type of feed to subscribe to (optional, defaults to 'all')

### Feed Types

| Feed Type | Description | Default Port | Endpoint |
|-----------|-------------|--------------|----------|
| `siri` | Bus SIRI real-time data | 8082 | `/bus-siri` |
| `lrgps` | Light Rail GPS data | 8083 | `/lrgps` |
| `railcomm` | Rail communication data | 8081 | `/rail-comm` |
| `all` | All feeds (default) | - | All endpoints |

### Examples

#### Subscribe to Individual Feeds

```bash
# Subscribe to Bus SIRI feed only
./scripts/subscribe-to-tisproxy.sh 192.168.1.100 8082 siri

# Subscribe to Light Rail GPS feed only  
./scripts/subscribe-to-tisproxy.sh 192.168.1.100 8083 lrgps

# Subscribe to Rail Communication feed only
./scripts/subscribe-to-tisproxy.sh 192.168.1.100 8081 railcomm
```

#### Subscribe to All Feeds

```bash
# Subscribe to all feeds (explicit)
./scripts/subscribe-to-tisproxy.sh 192.168.1.100 8080 all

# Subscribe to all feeds (default behavior)
./scripts/subscribe-to-tisproxy.sh 192.168.1.100 8080
```

### Script Features

- **Automatic validation** of IP address and port format
- **Connectivity testing** to TIS proxy and local endpoints
- **Authentication handling** using credentials from `.env` or environment
- **Detailed status output** with color-coded messages
- **Error handling** with meaningful HTTP status explanations
- **Monitoring instructions** provided after successful subscription

## Complete Setup Workflow

### 1. Start the Pipeline Infrastructure

```bash
# Option A: Start with Docker/Kafka (recommended)
./rtd-control.sh docker start

# Option B: Start local services
./rtd-control.sh start all
```

### 2. Start HTTP Receivers

```bash
# Start all receivers
./rtd-control.sh bus-comm receiver &
./rtd-control.sh lrgps receiver &  
./rtd-control.sh rail-comm receiver &
```

### 3. Subscribe to Feeds

```bash
# Get your IP address
YOUR_IP=$(ifconfig | grep "inet " | grep -v "127.0.0.1" | head -1 | awk '{print $2}')

# Subscribe to all feeds
./scripts/subscribe-to-tisproxy.sh $YOUR_IP 8080 all
```

### 4. Monitor Data Flow

```bash
# Monitor Kafka topics
./scripts/kafka-console-consumer.sh --topic rtd.bus.siri     # SIRI data
./scripts/kafka-console-consumer.sh --topic rtd.lrgps       # LRGPS data  
./scripts/kafka-console-consumer.sh --topic rtd.railcomm    # Rail Comm data

# Or use enhanced monitoring with metrics
./rtd-control.sh bus-comm monitor
./rtd-control.sh lrgps monitor
./rtd-control.sh rail-comm monitor
```

### 5. Check Status

```bash
# Check receiver health
curl http://localhost:8082/health  # Bus SIRI
curl http://localhost:8083/health  # LRGPS
curl http://localhost:8081/health  # Rail Comm

# Check subscription status
curl http://localhost:8082/status
curl http://localhost:8083/status  
curl http://localhost:8081/status
```

## Troubleshooting

### Common Issues

#### 1. Connection Refused
```bash
curl: (7) Failed to connect to localhost port 8082: Connection refused
```
**Solution**: Start the HTTP receiver first:
```bash
./rtd-control.sh bus-comm receiver
```

#### 2. Authentication Failed
```
❌ Unauthorized - Check username/password
```
**Solution**: Verify credentials in `.env` file or environment variables.

#### 3. Network Unreachable
```bash
❌ Cannot reach TIS proxy at http://tisproxy.rtd-denver.com
```
**Solution**: Check network connectivity and VPN connection if required.

#### 4. Subscription Expired
Subscriptions expire after the TTL period (default: 90 seconds).
**Solution**: Re-run the subscription script or increase TTL:
```bash
export TIS_PROXY_TTL=300000  # 5 minutes
./scripts/subscribe-to-tisproxy.sh $YOUR_IP 8080
```

### VPN Considerations

If using VPN, the script will automatically detect VPN interfaces (utun*) and use the VPN IP address:

```bash
# Check VPN status
./scripts/detect-vpn-ip.sh

# Use detected VPN IP
source <(./scripts/detect-vpn-ip.sh export)
./scripts/subscribe-to-tisproxy.sh $SIRI_HOST_IP 8080
```

### Log Analysis

Check receiver logs for issues:
```bash
# View live logs
./rtd-control.sh logs bus    # Bus SIRI logs
./rtd-control.sh logs rail   # Rail Comm logs

# Check for errors
tail -f bus-siri-receiver.log | grep -i error
tail -f rail-comm-receiver.log | grep -i error
```

## Data Flow Architecture

```
TIS Proxy (RTD) → HTTP Receivers → Kafka Topics → Processing Pipeline
     ↓                 ↓               ↓              ↓
subscription      /bus-siri      rtd.bus.siri   GTFS-RT feeds
   request        /lrgps         rtd.lrgps      Vehicle positions
                  /rail-comm     rtd.railcomm   Trip updates
```

## Integration with rtd-control.sh

The subscription script integrates with the main control script:

```bash
# Use rtd-control.sh for easier management
./rtd-control.sh bus-comm subscribe $YOUR_IP 8082
./rtd-control.sh lrgps subscribe $YOUR_IP 8083  
./rtd-control.sh rail-comm subscribe $YOUR_IP 8081
```

## Security Best Practices

1. **Never commit credentials** - `.env` is automatically excluded from Git
2. **Use environment variables** in production environments
3. **Rotate credentials regularly** as required by RTD policies
4. **Monitor subscription status** to detect unauthorized access
5. **Use VPN** when required for accessing TIS proxy

## Next Steps

After successful data subscription:

1. **Start GTFS-RT generation**: `./rtd-control.sh gtfs-rt all`
2. **View live transit map**: Open `http://localhost:3000/live`
3. **Access REST API**: Use endpoints at `http://localhost:8080/api/`
4. **Monitor metrics**: Check the admin dashboard for feed health

For additional help, see:
- `./rtd-control.sh help` - Main control script usage
- `./scripts/subscribe-to-tisproxy.sh help` - Subscription script help
- `CLAUDE.md` - Project configuration and commands