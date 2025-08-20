# Live Transit Map - SIRI & Rail Communication Integration

## Overview

The Live Transit Map is a new React component that displays real-time bus and train positions using data from the SIRI (Service Interface for Real-time Information) and Rail Communication feeds. This creates an RTD-style live transit map similar to the official RTD Live Map.

## Features

### 🚌 Bus Data (SIRI Feed)
- **Real-time positions** from SIRI XML/JSON feeds
- **Route information** with color-coded markers
- **Occupancy status** with color indicators
- **Delay information** and next stop data
- **Speed and direction** display

### 🚊 Train Data (Rail Communication Feed)
- **Real-time positions** from rail communication feeds
- **Line identification** with RTD line colors (A, B, C, D, E, etc.)
- **Next station** and operator messages
- **Speed and delay** information
- **Status updates** (On Time, Delayed, etc.)

### 🗺️ Map Features
- **Interactive OpenStreetMap** with vehicle markers
- **Auto-fitting** to show all vehicles
- **Toggle visibility** for buses and trains
- **Detailed popups** with comprehensive vehicle information
- **Live status indicators** showing connection state
- **Real-time updates** every 5 seconds

## Architecture

```
TIS Proxy → Bus SIRI HTTP Receiver → Kafka Topic → React App
         → Rail Comm HTTP Receiver → Kafka Topic → React App
```

### Data Flow

1. **TIS Proxy** forwards real-time data to HTTP receivers
2. **HTTP Receivers** store latest data and forward to Kafka
3. **React Service** polls HTTP endpoints for latest data
4. **Live Map Component** displays vehicles on interactive map

## Getting Started

### Prerequisites
- Java 24+
- Node.js 18+
- Maven 3.6+

### 1. Start All Services (Recommended)

**Single Command - Start Everything**
```bash
./rtd-control.sh start all
```

This starts Java pipeline, React app, Bus SIRI receiver, and Rail Communication receiver.

### 2. Start Individual Services

**Start Only HTTP Receivers**
```bash
./rtd-control.sh start receivers
```

**Start Individual Receivers**
```bash
./rtd-control.sh start bus    # Bus SIRI receiver only
./rtd-control.sh start rail   # Rail Communication receiver only
```

**Start React App Only**
```bash
./rtd-control.sh start react
```

### 3. Check Status and Open Live Map

**Check All Services Status**
```bash
./rtd-control.sh status
```

**Open Live Map**
Navigate to: **http://localhost:3000/live**

## API Endpoints

### Data Endpoints
- **Bus SIRI Data**: `http://localhost:8082/bus-siri/latest`
- **Rail Communication Data**: `http://localhost:8081/rail-comm/latest`

### Health Checks
- **Bus SIRI Health**: `http://localhost:8082/health`
- **Rail Communication Health**: `http://localhost:8081/health`

## Configuration

### Environment Variables
```bash
# TIS Proxy Configuration
TIS_PROXY_HOST=http://tisproxy.rtd-denver.com
TIS_PROXY_USERNAME=tis-proxy-admin
TIS_PROXY_PASSWORD=your-password

# Local IP for subscriptions
LOCAL_IP=172.16.23.5
BUS_SIRI_PORT=880
RAIL_COMM_PORT=881

# Service Settings
RAILCOMM_SERVICE=railcomm
RAILCOMM_TTL=90000
```

## Data Formats

### SIRI Bus Data
```json
{
  "timestamp_ms": 1692547200000,
  "vehicle_id": "BUS_001",
  "route_id": "15",
  "direction": "EASTBOUND",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 25.5,
  "status": "IN_TRANSIT",
  "next_stop": "Union Station",
  "delay_seconds": 120,
  "occupancy": "MANY_SEATS_AVAILABLE",
  "block_id": "BLK_15_01",
  "trip_id": "TRIP_15_0800"
}
```

### Rail Communication Data
```json
{
  "timestamp_ms": 1692547200000,
  "train_id": "LRV_A_001",
  "line_id": "A-Line",
  "direction": "NORTHBOUND",
  "latitude": 39.7392,
  "longitude": -104.9903,
  "speed_mph": 35.5,
  "status": "ON_TIME",
  "next_station": "Union Station",
  "delay_seconds": 0,
  "operator_message": "Normal operation"
}
```

## Visual Indicators

### Bus Occupancy Colors
- 🟢 **Green**: Empty
- 🔵 **Blue**: Many Seats Available  
- 🟡 **Yellow**: Few Seats Available
- 🔴 **Red**: Standing Room Only
- 🟤 **Dark Red**: Crushed Standing Room
- ⚫ **Black**: Full

### Train Line Colors
- 🔵 **A Line**: Blue (#0066CC)
- 🟢 **B Line**: Green (#00AA44)
- 🟠 **C Line**: Orange (#FF6600)
- 🟡 **D Line**: Gold (#FFD700)
- 🟣 **E Line**: Purple (#800080)
- 🩷 **F Line**: Pink (#FF69B4)
- 🔷 **G Line**: Teal (#008080)
- 🔴 **H Line**: Red (#DC143C)
- 🟢 **N Line**: Lime (#32CD32)
- 🟠 **R Line**: Orange Red (#FF4500)
- 🟣 **W Line**: Indigo (#4B0082)

## Navigation

The app now includes three main pages:

1. **Static Map** (`/`) - Original GTFS-RT vehicle map
2. **Live Transit** (`/live`) - New SIRI & Rail Communication live map
3. **Admin Dashboard** (`/admin`) - Developer tools and configuration

## Troubleshooting

### Common Issues

**1. No vehicles showing on map**
- Check that HTTP receivers are running
- Verify TIS proxy subscriptions are active
- Check browser console for connection errors

**2. Connection errors**
- Ensure receivers are running on correct ports (8082, 8081)
- Check that React app can reach the endpoints
- Verify CORS headers are properly set

**3. Data not updating**
- Check TIS proxy subscription status
- Verify TTL settings haven't expired
- Look for authentication issues in receiver logs

### Debugging Commands

```bash
# Check receiver status
curl http://localhost:8082/status
curl http://localhost:8081/status

# Test data endpoints
curl http://localhost:8082/bus-siri/latest
curl http://localhost:8081/rail-comm/latest

# View current subscriptions
./show-subscriptions.sh
```

## Development

### Adding New Features

1. **New Vehicle Types**: Extend the marker creation functions in `LiveTransitMap.tsx`
2. **Additional Data Fields**: Update the data interfaces in `liveTransitService.ts`
3. **Map Controls**: Add new controls to the map controls panel
4. **Data Processing**: Modify the HTTP receivers to add data processing

### Testing

Run the comprehensive test script:
```bash
./test-live-map.sh
```

## Performance Notes

- **Data Caching**: Latest 10 records kept in memory per feed
- **Update Frequency**: 5-second polling interval (configurable)
- **Memory Management**: Automatic cleanup of old data
- **Connection Handling**: Graceful fallback for failed connections

## Security

- **Environment Variables**: Credentials stored securely in `.env`
- **CORS**: Proper headers for cross-origin requests
- **Authentication**: Basic Auth for TIS proxy communication
- **Data Validation**: Input validation on all endpoints

## Future Enhancements

- **Historical Data**: Store and display historical vehicle positions
- **Route Overlays**: Show route paths on the map
- **Alerts Integration**: Display service alerts and disruptions
- **Performance Metrics**: Real-time performance monitoring
- **Mobile Optimization**: Responsive design for mobile devices

---

## Support

For issues or questions about the Live Transit Map:

1. Check the troubleshooting section above
2. Review the console logs for errors
3. Verify all services are running correctly
4. Check the API endpoints for data availability