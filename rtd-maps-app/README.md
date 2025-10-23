# TransitStream Live Map

A real-time visualization of RTD Denver's buses and light rail trains using OpenStreetMap, built with React and TypeScript. This application is part of TransitStream, RTD's modern transit information system. It consumes live GTFS-RT data from the TransitStream pipeline and displays vehicle positions, routes, delays, and service information on an interactive map.

![TransitStream Live Map](https://via.placeholder.com/800x400/0066CC/FFFFFF?text=TransitStream+Live+Map)

## 🚌 Features

### Real-Time Transit Visualization
- **Live Vehicle Tracking**: Real-time positions of 200+ RTD buses and light rail trains
- **Route Information**: Complete route details with colors and service patterns
- **Delay Detection**: Visual indicators for delayed vehicles with timing information
- **Service Alerts**: Display active service disruptions and announcements

### Interactive Map Experience
- **OpenStreetMap Integration**: Free, open-source mapping with custom RTD styling
- **Vehicle Selection**: Click vehicles for detailed information panels  
- **Smart Filtering**: Filter by vehicle type, routes, delays, and map bounds
- **Responsive Design**: Works on desktop, tablet, and mobile devices
- **No API Keys Required**: 100% free to run and deploy

### Data Integration
- **Kafka Consumer**: Connects to RTD GTFS-RT pipeline topics
- **Direct API Fallback**: Falls back to RTD's public APIs when Kafka unavailable
- **Protocol Buffer Support**: Efficient parsing of GTFS-RT protobuf messages
- **Real-time Updates**: Data refreshes every 30 seconds automatically

## 🛠️ Technology Stack

- **Frontend**: React 18 + TypeScript + Vite
- **Maps**: Leaflet + OpenStreetMap with React-Leaflet wrapper
- **Styling**: Tailwind CSS with RTD brand colors
- **Data**: Kafka consumers + RTD GTFS-RT APIs
- **Icons**: Lucide React icons
- **Date Handling**: date-fns library

## 🚀 Quick Start

### Prerequisites

1. **Node.js 18+** and npm/yarn
2. **TransitStream Pipeline Running** (optional - will use mock data otherwise)

**No API keys required!** OpenStreetMap is completely free.

### Installation

```bash
# Clone the repository
git clone <repository-url>
cd rtd-maps-app

# Install dependencies
npm install

# Copy environment template (optional)
cp .env.example .env

# No API key needed! Ready to run.
```

### Development

```bash
# Start development server
npm run dev

# Open http://localhost:3000 in your browser
```

### Production Build

```bash
# Build for production
npm run build

# Preview production build
npm run preview

# Serve static files from dist/
```

## 📊 Data Sources

### Kafka Topics (Primary)
- `rtd.vehicle.positions` - Live vehicle GPS coordinates and status
- `rtd.trip.updates` - Schedule adherence and delay information
- `rtd.alerts` - Service disruptions and announcements
- `rtd.comprehensive.routes` - Enhanced route data with static GTFS

### RTD APIs (Fallback)
- Vehicle Positions: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb`
- Trip Updates: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb`
- Service Alerts: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb`

### Mock Data
When neither Kafka nor RTD APIs are available, the app generates realistic mock data with:
- 100-300 vehicles distributed across Denver metro area
- Common RTD routes (A/B/C/D/E lines, Routes 15/16/20)
- Simulated delays, speeds, and occupancy levels

## 🗺️ Map Features

### Vehicle Markers
- **Buses**: Orange circular markers with bus icon
- **Light Rail**: Blue circular markers with train icon
- **Selected**: Larger with white outline and route label
- **Delayed**: Red indicator dot for vehicles >5 minutes late
- **Direction**: Arrow indicating vehicle bearing/heading

### Map Controls
- **Vehicle Type Filter**: Toggle buses and light rail visibility
- **Route Selector**: Filter by specific routes (A Line, Route 15, etc.)
- **Delay Filter**: Show only delayed vehicles with configurable threshold
- **Auto-refresh**: Real-time data updates every 30 seconds
- **Connection Status**: Live indicator of data service connectivity

### Vehicle Details Panel
When clicking a vehicle, shows:
- Route information and destination
- Current location (lat/lng coordinates)
- Speed, bearing, and occupancy status
- Schedule adherence (on-time vs delayed)
- Real-time vs cached data indicator
- Trip and vehicle IDs for debugging

## 🔧 Admin Dashboard

TransitStream includes a comprehensive admin dashboard for managing data feeds, monitoring system health, and controlling subscriptions to RTD communication services.

### Access the Admin Dashboard

Navigate to `/admin` in the application to access the administrative interface with the following sections:

### 📊 Statistics Overview

Real-time metrics dashboard showing:
- **Active Subscriptions**: Count of currently active data feed subscriptions
- **Live Feeds**: Number of feeds actively receiving data
- **Total Messages**: Aggregate message count across all subscriptions  
- **Error Count**: Total system errors requiring attention

### 🔔 Subscription Management

Complete subscription lifecycle management with:

#### Quick Subscribe Actions
```bash
# Rail Communication Options
./rtd-control.sh rail-comm subscribe           # Original endpoint
./rtd-control.sh rail-comm subscribe-bridge    # Direct Kafka Bridge (recommended)
./rtd-control.sh rail-comm subscribe-kafka     # Direct Kafka endpoint

# Bus SIRI Options  
./scripts/bus-siri-subscribe.sh                # Default subscription
./scripts/bus-siri-subscribe.sh [host] [service] [ttl]  # Custom parameters
```

#### Individual Subscription Controls
- **🔗 Subscribe Button**: Blue link icon for inactive rail-comm and bus-siri feeds
- **🔓 Unsubscribe Button**: Orange unlink icon for active subscriptions
- **⏸️ Pause/Resume**: Yellow pause or green play buttons for temporary control
- **🗑️ Delete**: Red trash icon for permanent removal

#### Bulk Operations
- **Subscribe All Feeds**: Sequential subscription to both rail and bus feeds
- **Unsubscribe All Rail Comm**: Batch unsubscribe from all rail communication endpoints
- **Unsubscribe All Bus SIRI**: Batch unsubscribe from all bus SIRI services
- **Unsubscribe All Feeds**: Complete disconnection from all specialized feeds

### 📡 Live Feed Monitoring

Real-time monitoring of all data sources:

#### Health Status Indicators
- **🟢 Healthy**: Feed active with recent data
- **🟡 Warning**: Feed active but experiencing issues
- **🔴 Error**: Feed offline or encountering failures

#### Feed Types Monitored
1. **Kafka Vehicle Feed** - Real-time vehicle position data
2. **Direct RTD API** - Trip updates and schedule adherence  
3. **RTD Alerts Feed** - Service disruptions and announcements
4. **Rail Communication Bridge** - Train-to-infrastructure communication
5. **Bus SIRI Receiver** - SIRI standard bus monitoring data

#### Sample Data Viewer
- **Expandable Data Sections**: Click to view raw message content
- **JSON Formatting**: Syntax-highlighted message payloads
- **Copy to Clipboard**: One-click copy of message data for debugging
- **Message Metadata**: Timestamps, size, and source information

### 🚂 Rail Communication Bridge

Detailed message history and subscription management for RTD's rail communication system:

#### Message History (Last 20 Messages)
- **Message Types**: Position updates, status reports, alerts, and heartbeat messages
- **Train Information**: Train IDs, routes (A-Line, B-Line, etc.), speed, passenger counts
- **Location Data**: GPS coordinates, station information, direction
- **System Status**: Equipment health, communication strength, maintenance data

#### Message Type Breakdown
- **🔵 Position**: Real-time train location and movement data
- **🟢 Status**: Operational status, delays, door status, boarding information  
- **🔴 Alert**: Service disruptions, delays, equipment issues
- **⚪ Heartbeat**: System health checks and communication verification

#### Subscription Commands
```bash
# Subscribe to rail communication feeds
./rtd-control.sh rail-comm subscribe           # Original HTTP receiver
./rtd-control.sh rail-comm subscribe-bridge    # Direct Kafka Bridge (optimized)  
./rtd-control.sh rail-comm subscribe-kafka     # Direct Kafka endpoint

# Unsubscribe from feeds
./rtd-control.sh rail-comm unsubscribe         # Original endpoint
./rtd-control.sh rail-comm unsubscribe-all     # All rail communication endpoints

# Monitor and test
./rtd-control.sh rail-comm test                # Send test JSON payloads
./rtd-control.sh rail-comm monitor             # Monitor rail comm topic
```

### 🚌 Bus SIRI Feed Control

SIRI (Service Interface for Real Time Information) standard bus communication:

#### SIRI Service Types
- **StopMonitoring**: Real-time arrival predictions at bus stops
- **VehicleMonitoring**: Live bus positions and operational status
- **Default Subscription**: Automated subscription with optimal settings

#### Subscription Options
```bash
# Subscribe to SIRI services
./scripts/bus-siri-subscribe.sh                # Default: localhost StopMonitoring 3600
./scripts/bus-siri-subscribe.sh [host] [service] [ttl]  # Custom parameters

# Service-specific subscriptions
./scripts/bus-siri-subscribe.sh localhost StopMonitoring 7200
./scripts/bus-siri-subscribe.sh localhost VehicleMonitoring 3600

# Unsubscribe and status
./scripts/bus-siri-subscribe.sh unsubscribe    # Unsubscribe from SIRI feed
./rtd-control.sh bus-comm status               # Check receiver status
```

### ❌ Error Messages & System Health

Comprehensive error tracking and resolution:

#### Error Classification System
- **🔴 Connection**: Network connectivity issues (15 total errors)
- **🟠 Parsing**: Data format and structure problems (8 total errors)
- **🟡 Validation**: Data integrity and validation failures (23 total errors)  
- **🟣 Timeout**: Request/response timing issues (22 total errors)
- **🩷 Authentication**: Security and API key problems (1 total error)
- **🔵 Rate Limit**: API throttling and usage restrictions (12 total errors)

#### Error Management Features
- **Sortable Error List**: Sort by error type, count, severity, or timestamp
- **Error Details**: Full JSON error payloads with technical details
- **Resolution Status**: Track resolved vs. unresolved errors
- **Error Frequency**: Color-coded badges showing error occurrence count
- **Copy to Clipboard**: Easy sharing of error details for debugging

#### Severity Levels
- **🔴 Critical**: System-breaking errors requiring immediate attention
- **🟠 High**: Important errors affecting functionality
- **🟡 Medium**: Moderate errors with potential impact
- **🟢 Low**: Minor errors or warnings

### 🎛️ Feed Subscription Control Panel

Dedicated interface for managing rail communication and bus SIRI subscriptions:

#### Rail Communication Control
- **Three-Endpoint Support**: Original, Bridge, and Kafka connection options
- **Active Subscription Display**: Real-time list of active rail subscriptions
- **Quick Subscribe Buttons**: One-click subscription to different endpoints
- **Command Reference**: Complete command documentation with usage examples

#### Bus SIRI Control  
- **Service Selection**: Choose between StopMonitoring and VehicleMonitoring
- **Parameter Customization**: Configure host, service type, and TTL settings
- **Default Quick Start**: Instant subscription with optimized defaults
- **Status Monitoring**: Real-time connection and data flow status

#### Safety Features
- **Confirmation Dialogs**: Prevent accidental bulk unsubscribe operations
- **Non-Destructive Actions**: Subscriptions are paused rather than deleted
- **Easy Recovery**: Simple reactivation of paused subscriptions
- **Real-time Feedback**: Immediate visual confirmation of actions

### 📈 Usage Statistics

The admin dashboard tracks and displays:
- **Subscription Statistics**: Rail Comm (1), Bus SIRI (1), Active (2), Unsubscribed (0)
- **Message Throughput**: Real-time message rates and processing statistics
- **Error Frequency**: Historical error patterns and resolution tracking
- **System Health**: Overall platform health and performance metrics

## ⚙️ Configuration

### Environment Variables

```bash
# Optional (with defaults)
VITE_KAFKA_BROKERS=localhost:9092
VITE_RTD_API_BASE=https://nodejs-prod.rtd-denver.com/api
VITE_UPDATE_INTERVAL_MS=30000
VITE_DEBUG_MODE=false
VITE_MOCK_DATA=false

# Application URLs
# Main Map: http://localhost:3000/
# Admin Dashboard: http://localhost:3000/admin
```

### OpenStreetMap Benefits

- **100% Free**: No API keys, no usage limits, no billing
- **Open Source**: Community-driven mapping data
- **Privacy Friendly**: No tracking or data collection
- **Reliable**: Distributed infrastructure with high availability
- **Customizable**: Full control over map styling and features

### Kafka Integration

The app attempts to connect to Kafka topics first, then falls back to direct RTD APIs:

```typescript
// Default Kafka configuration
{
  brokers: ['localhost:9092'],
  clientId: 'rtd-maps-app',
  topics: {
    vehiclePositions: 'rtd.vehicle.positions',
    tripUpdates: 'rtd.trip.updates',
    alerts: 'rtd.alerts',
    comprehensiveRoutes: 'rtd.comprehensive.routes'
  }
}
```

## 🏗️ Architecture

### Component Structure
```
src/
├── components/
│   ├── OpenStreetMap.tsx       # Main map component (OpenStreetMap + Leaflet)
│   ├── MapView.tsx             # Map page container
│   ├── AdminDashboard.tsx      # Admin dashboard with subscription management
│   ├── MapControls.tsx         # Filter and control UI
│   ├── VehicleMarker.tsx       # Individual vehicle markers
│   ├── VehicleDetailsPanel.tsx # Vehicle info sidebar
│   ├── VehicleSelector.tsx     # Vehicle selection and tracking
│   ├── VehicleTracker.tsx      # Vehicle tracking and history
│   ├── DataSourcePanel.tsx     # Data source monitoring
│   └── UpdateIntervalControl.tsx # Update frequency control
├── services/
│   ├── rtdDataService.ts       # Data fetching and Kafka integration
│   └── dataQueryService.ts     # Query and analytics service
├── hooks/
│   └── useRTDData.ts          # React hook for RTD data management
├── types/
│   └── rtd.ts                 # TypeScript definitions
└── utils/
```

### Data Flow
```
RTD APIs / Kafka Topics
       ↓
RTDDataService (Singleton)
       ↓
useRTDData Hook
       ↓
App Component
       ↓
GoogleMap Component
       ↓
Vehicle Markers
```

### State Management
- **RTD Data Service**: Singleton service managing data connections
- **React Hooks**: Custom hooks for data consumption
- **Component State**: Local state for UI interactions
- **Real-time Updates**: WebSocket-style subscriptions to data changes

## 🎨 Customization

### RTD Brand Colors
```css
/* Tailwind custom colors in tailwind.config.js */
colors: {
  rtd: {
    primary: '#0066CC',    // RTD Blue
    secondary: '#FF6600',  // RTD Orange  
    light: '#E6F2FF',      // Light Blue
    dark: '#003366'        // Dark Blue
  },
  transport: {
    bus: '#FF6600',        // Bus Orange
    rail: '#0066CC',       // Rail Blue
    route: '#00AA44'       // Route Green
  }
}
```

### Route Colors
Light rail lines use specific brand colors defined in `src/types/rtd.ts`:
- A Line: Blue (#0066CC)
- B Line: Green (#00AA44)  
- C Line: Orange (#FF6600)
- D Line: Gold (#FFD700)
- E Line: Purple (#800080)

### Map Styling
Custom Google Maps styles enhance transit visibility:
- Simplified transit lines
- Reduced business POIs
- RTD brand color integration
- Enhanced road visibility

## 🧪 Testing

```bash
# Run type checking
npm run type-check

# Run linting
npm run lint

# Build test
npm run build
```

### Manual Testing Checklist
- [ ] Google Maps loads correctly
- [ ] Vehicle markers appear with correct colors/icons
- [ ] Click vehicle to open details panel
- [ ] Filter controls work (buses/trains, routes, delays)
- [ ] Data auto-refreshes every 30 seconds
- [ ] Connection status updates correctly
- [ ] Responsive design works on mobile
- [ ] Error states display appropriately

## 🚀 Deployment

### Static Hosting (Recommended)
```bash
# Build production bundle
npm run build

# Upload dist/ folder to:
# - Netlify, Vercel, GitHub Pages
# - AWS S3 + CloudFront
# - Any static file host
```

### Docker Deployment
```dockerfile
FROM node:18-alpine as build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

### Environment-Specific Builds
```bash
# Development
npm run dev

# Staging  
VITE_RTD_API_BASE=https://staging-api.rtd.com npm run build

# Production
VITE_RTD_API_BASE=https://api.rtd.com npm run build
```

## 📈 Performance

### Optimization Features
- **Lazy Loading**: Components load on demand
- **Virtual Markers**: Only render visible vehicles
- **Efficient Updates**: Minimal re-renders with React optimizations
- **Bundle Splitting**: Separate chunks for vendors and maps
- **Memory Management**: Proper cleanup of subscriptions and timers

### Monitoring
- Real-time connection status display
- Vehicle count and filter metrics
- Data freshness indicators
- Error boundary for graceful failure handling

## 🔧 Troubleshooting

### Common Issues

**Map doesn't load**
- Check internet connection to OpenStreetMap tiles
- Verify Leaflet CSS is loading correctly  
- Check browser console for JavaScript errors
- Try refreshing or clearing browser cache

**No vehicle data**
- Verify RTD API endpoints are accessible
- Check Kafka broker connectivity
- Enable debug mode: `VITE_DEBUG_MODE=true`

**Performance issues**
- Reduce update interval: `VITE_UPDATE_INTERVAL_MS=60000`
- Enable vehicle filtering to reduce marker count
- Check browser memory usage with DevTools

**Build errors**
- Update Node.js to version 18+
- Clear node_modules and reinstall
- Check TypeScript configuration

## 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit pull request

### Development Standards
- TypeScript strict mode enabled
- ESLint + Prettier for code formatting
- Responsive design required
- Accessibility considerations (WCAG 2.1)
- Performance testing for 500+ concurrent vehicles

## 📄 License

This project is part of TransitStream, RTD's modern transit information system, and processes publicly available transit data from RTD Denver. Please comply with RTD's terms of service when using their data feeds.

## 🙏 Acknowledgments

- **RTD Denver** for providing public GTFS-RT data
- **OpenStreetMap Community** for free, open mapping data  
- **React + TypeScript** community for excellent tooling
- **Tailwind CSS** for rapid UI development