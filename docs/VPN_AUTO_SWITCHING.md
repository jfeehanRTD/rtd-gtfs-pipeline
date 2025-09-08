# VPN Auto-Switching for SIRI Host

This feature automatically detects when you're connected to a VPN and uses the appropriate IP address for SIRI host subscriptions.

## Overview

When connecting to RTD's TIS Proxy for SIRI feeds, the system needs to provide a callback IP address where the proxy will send data. This IP must be reachable from the proxy server. When you're on VPN, this should be your VPN IP address; otherwise, it should be your regular network IP.

## Components

### 1. VPN Detection Script (`scripts/detect-vpn-ip.sh`)
- Automatically detects VPN connections (GlobalProtect, OpenVPN, Cisco AnyConnect)
- Identifies VPN interface and IP address
- Falls back to regular network IP when VPN is not connected
- Can monitor for VPN status changes

### 2. Auto VPN Subscribe Script (`scripts/auto-vpn-subscribe.sh`)
- Wrapper script that handles automatic IP switching
- Subscribes to SIRI feeds with the correct IP
- Can monitor VPN status and resubscribe automatically when VPN status changes

### 3. Updated Bus SIRI Subscribe (`scripts/bus-siri-subscribe.sh`)
- Now integrates with VPN detection
- Automatically uses VPN IP when available
- Falls back to regular IP when VPN is disconnected

## Usage

### Quick Start

1. **Check VPN Status:**
   ```bash
   ./scripts/detect-vpn-ip.sh
   ```

2. **Subscribe with Auto-Detection:**
   ```bash
   ./scripts/auto-vpn-subscribe.sh
   ```

3. **Monitor and Auto-Resubscribe:**
   ```bash
   ./scripts/auto-vpn-subscribe.sh monitor
   ```

### Commands

#### VPN Detection Script
```bash
# Detect current VPN status
./scripts/detect-vpn-ip.sh

# Monitor VPN status changes
./scripts/detect-vpn-ip.sh monitor

# Update .env file with detected IP
./scripts/detect-vpn-ip.sh update-env

# Export variables for sourcing
source <(./scripts/detect-vpn-ip.sh export)
```

#### Auto VPN Subscribe Script
```bash
# Subscribe with auto-detected IP (default)
./scripts/auto-vpn-subscribe.sh

# Monitor VPN changes and auto-resubscribe
./scripts/auto-vpn-subscribe.sh monitor

# Test VPN switching setup
./scripts/auto-vpn-subscribe.sh test

# Update .env file with current IP
./scripts/auto-vpn-subscribe.sh update-env

# Show current VPN/network status
./scripts/auto-vpn-subscribe.sh status
```

## How It Works

1. **VPN Detection:**
   - Checks for VPN interfaces (utun*, tun*, tap*, gpd0, cscotun0)
   - Identifies GlobalProtect by checking for running processes
   - Extracts IP address from VPN interface

2. **IP Selection Priority:**
   - If VPN is connected: Use VPN IP
   - If no VPN: Use regular network IP
   - Fallback: Use localhost

3. **Automatic Switching:**
   - Monitor mode checks VPN status every 10 seconds
   - When VPN status changes, automatically resubscribes with new IP
   - Ensures continuous data flow regardless of VPN state

## Environment Variables

The scripts use and set these environment variables:

- `SIRI_HOST_IP`: The IP address to use for SIRI host
- `USE_VPN_IP`: Boolean indicating if VPN IP is being used
- `VPN_IP`: The detected VPN IP address (if connected)
- `VPN_INTERFACE`: The VPN network interface (if connected)

## Examples

### Manual VPN Switching
```bash
# Connect to VPN
# ... (connect via GlobalProtect or other VPN client)

# Subscribe with VPN IP
./scripts/auto-vpn-subscribe.sh
# Output: 🔐 Using VPN IP: 172.23.4.10

# Disconnect from VPN
# ... (disconnect VPN)

# Subscribe with regular IP
./scripts/auto-vpn-subscribe.sh
# Output: 🌐 Using regular network IP: 192.168.0.45
```

### Automatic Monitoring
```bash
# Start monitor (runs continuously)
./scripts/auto-vpn-subscribe.sh monitor

# The script will:
# - Detect current VPN status
# - Subscribe with appropriate IP
# - Monitor for VPN changes
# - Automatically resubscribe when VPN connects/disconnects
```

### Integration with rtd-control.sh
```bash
# The bus-siri-subscribe.sh script now automatically uses VPN detection
./rtd-control.sh bus-comm subscribe

# This will:
# - Detect if VPN is connected
# - Use VPN IP if available
# - Use regular IP otherwise
```

## Troubleshooting

### VPN Not Detected
If your VPN is connected but not detected:
1. Check interface name: `ifconfig -l`
2. Look for interfaces starting with: utun, tun, tap, gpd, cscotun
3. Verify IP assignment: `ifconfig <interface_name>`

### Manual Override
You can manually specify the IP if needed:
```bash
# Set environment variable
export SIRI_HOST_IP="172.23.4.10"

# Then run subscribe
./scripts/bus-siri-subscribe.sh
```

### Debug Mode
To see detailed detection process:
```bash
# Run detection with verbose output
bash -x ./scripts/detect-vpn-ip.sh
```

## Security Notes

- VPN credentials are never stored in these scripts
- Only network interface information is detected
- No VPN configuration is modified
- Scripts only read network state, don't modify it

## Supported VPN Types

- **GlobalProtect** (PanGPS) - Primary support
- **OpenVPN** (tun/tap interfaces)
- **Cisco AnyConnect** (cscotun interface)
- Generic VPN clients using utun interfaces

## Future Enhancements

- Support for more VPN types
- Integration with systemd/launchd for automatic monitoring
- Web UI for VPN status display
- Notification system for VPN state changes