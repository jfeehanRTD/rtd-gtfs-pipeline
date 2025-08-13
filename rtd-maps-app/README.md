# RTD Live Transit Map

A real-time visualization of RTD Denver's buses and light rail trains using OpenStreetMap, built with React and TypeScript. This application consumes live GTFS-RT data from the RTD pipeline and displays vehicle positions, routes, delays, and service information on an interactive map.

![RTD Maps Demo](https://via.placeholder.com/800x400/0066CC/FFFFFF?text=RTD+Live+Transit+Map)

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
2. **RTD Pipeline Running** (optional - will use mock data otherwise)

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

## ⚙️ Configuration

### Environment Variables

```bash
# Required
VITE_GOOGLE_MAPS_API_KEY=your_google_maps_api_key

# Optional (with defaults)
VITE_KAFKA_BROKERS=localhost:9092
VITE_RTD_API_BASE=https://nodejs-prod.rtd-denver.com/api
VITE_UPDATE_INTERVAL_MS=30000
VITE_DEBUG_MODE=false
VITE_MOCK_DATA=false
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
│   ├── GoogleMap.tsx           # Main map component
│   ├── MapControls.tsx         # Filter and control UI
│   ├── VehicleMarker.tsx       # Individual vehicle markers
│   └── VehicleDetailsPanel.tsx # Vehicle info sidebar
├── services/
│   └── rtdDataService.ts       # Data fetching and Kafka integration
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

This project is part of the RTD GTFS-RT Pipeline and processes publicly available transit data from RTD Denver. Please comply with RTD's terms of service when using their data feeds.

## 🙏 Acknowledgments

- **RTD Denver** for providing public GTFS-RT data
- **OpenStreetMap Community** for free, open mapping data  
- **React + TypeScript** community for excellent tooling
- **Tailwind CSS** for rapid UI development