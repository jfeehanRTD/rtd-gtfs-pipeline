// Live Transit Map - Real-time buses and trains from SIRI and Rail Communication feeds
// This component shows live vehicle positions similar to RTD's official live map

import React, { useEffect, useState, useCallback } from 'react';
import { MapContainer, TileLayer, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import { 
  Bus, 
  Train, 
  MapPin, 
  Wifi, 
  WifiOff, 
  RefreshCw, 
  Clock,
  Users,
  AlertCircle,
  Navigation
} from 'lucide-react';
import { LiveTransitService, SIRIVehicleData, RailCommData } from '../services/liveTransitService';

// Fix Leaflet default marker icons
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
});

// Custom icons for vehicles
const createBusIcon = (routeId: string, occupancy: string) => {
  const color = getOccupancyColor(occupancy);
  return new L.DivIcon({
    html: `
      <div style="
        background: ${color};
        border: 2px solid white;
        border-radius: 50%;
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 10px;
        font-weight: bold;
        color: white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.3);
      ">
        ${routeId.length > 3 ? 'BUS' : routeId}
      </div>
    `,
    className: 'bus-marker',
    iconSize: [24, 24],
    iconAnchor: [12, 12]
  });
};

const createTrainIcon = (lineId: string) => {
  const color = getLineColor(lineId);
  return new L.DivIcon({
    html: `
      <div style="
        background: ${color};
        border: 2px solid white;
        border-radius: 4px;
        width: 28px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 12px;
        font-weight: bold;
        color: white;
        box-shadow: 0 2px 4px rgba(0,0,0,0.3);
      ">
        ${lineId}
      </div>
    `,
    className: 'train-marker',
    iconSize: [28, 20],
    iconAnchor: [14, 10]
  });
};

const getOccupancyColor = (occupancy: string): string => {
  switch (occupancy?.toUpperCase()) {
    case 'EMPTY': return '#10B981'; // Green
    case 'MANY_SEATS_AVAILABLE': return '#3B82F6'; // Blue
    case 'FEW_SEATS_AVAILABLE': return '#F59E0B'; // Yellow
    case 'STANDING_ROOM_ONLY': return '#EF4444'; // Red
    case 'CRUSHED_STANDING_ROOM_ONLY': return '#DC2626'; // Dark Red
    case 'FULL': return '#7F1D1D'; // Very Dark Red
    default: return '#6B7280'; // Gray
  }
};

const getLineColor = (lineId: string): string => {
  const lineColors: { [key: string]: string } = {
    'A': '#0066CC',
    'B': '#00AA44',
    'C': '#FF6600',
    'D': '#FFD700',
    'E': '#800080',
    'F': '#FF69B4',
    'G': '#008080',
    'H': '#DC143C',
    'N': '#32CD32',
    'R': '#FF4500',
    'W': '#4B0082'
  };
  return lineColors[lineId.toUpperCase()] || '#666666';
};

const formatTimestamp = (timestamp: number): string => {
  const date = new Date(timestamp);
  return date.toLocaleTimeString();
};

const formatDelay = (delaySeconds: number): string => {
  if (delaySeconds === 0) return 'On Time';
  const minutes = Math.floor(Math.abs(delaySeconds) / 60);
  const seconds = Math.abs(delaySeconds) % 60;
  const sign = delaySeconds > 0 ? '+' : '-';
  return `${sign}${minutes}:${seconds.toString().padStart(2, '0')}`;
};

// Component to handle map updates
const MapController: React.FC<{ buses: SIRIVehicleData[], trains: RailCommData[] }> = ({ buses, trains }) => {
  const map = useMap();

  useEffect(() => {
    if (buses.length === 0 && trains.length === 0) return;

    // Calculate bounds to fit all vehicles
    const bounds = L.latLngBounds([]);
    
    buses.forEach(bus => {
      if (bus.latitude && bus.longitude) {
        bounds.extend([bus.latitude, bus.longitude]);
      }
    });
    
    trains.forEach(train => {
      if (train.latitude && train.longitude) {
        bounds.extend([train.latitude, train.longitude]);
      }
    });

    if (bounds.isValid()) {
      map.fitBounds(bounds, { padding: [20, 20] });
    }
  }, [map, buses, trains]);

  return null;
};

const LiveTransitMap: React.FC = () => {
  const [buses, setBuses] = useState<SIRIVehicleData[]>([]);
  const [trains, setTrains] = useState<RailCommData[]>([]);
  const [connectionState, setConnectionState] = useState({
    isConnected: false,
    lastUpdate: null as Date | null,
    busCount: 0,
    trainCount: 0,
    error: null as string | null
  });
  const [showBuses, setShowBuses] = useState(true);
  const [showTrains, setShowTrains] = useState(true);
  const [selectedVehicle, setSelectedVehicle] = useState<string | null>(null);

  useEffect(() => {
    console.log('🗺️ Initializing Live Transit Map...');
    
    const service = LiveTransitService.getInstance();
    
    const unsubscribe = service.subscribe((data) => {
      setBuses(data.buses);
      setTrains(data.trains);
      setConnectionState(service.getConnectionState());
    });

    // Initial state
    setConnectionState(service.getConnectionState());

    return () => {
      unsubscribe();
    };
  }, []);

  const handleRefresh = useCallback(async () => {
    const service = LiveTransitService.getInstance();
    await service.refresh();
  }, []);

  const visibleBuses = showBuses ? buses.filter(bus => bus.latitude && bus.longitude) : [];
  const visibleTrains = showTrains ? trains.filter(train => train.latitude && train.longitude) : [];

  return (
    <div className="relative w-full h-full">
      {/* Status Bar */}
      <div className="absolute top-4 left-4 right-4 z-[1000] flex justify-between items-center">
        <div className="bg-white rounded-lg shadow-md p-3 flex items-center space-x-3">
          <div className="flex items-center space-x-2">
            {connectionState.isConnected ? (
              <>
                <Wifi className="w-5 h-5 text-green-500" />
                <span className="text-sm font-medium text-green-600">Live</span>
              </>
            ) : (
              <>
                <WifiOff className="w-5 h-5 text-red-500" />
                <span className="text-sm font-medium text-red-600">Offline</span>
              </>
            )}
          </div>
          
          <div className="h-4 border-l border-gray-300"></div>
          
          <div className="flex items-center space-x-4 text-sm text-gray-600">
            <div className="flex items-center space-x-1">
              <Bus className="w-4 h-4" />
              <span>{connectionState.busCount}</span>
            </div>
            <div className="flex items-center space-x-1">
              <Train className="w-4 h-4" />
              <span>{connectionState.trainCount}</span>
            </div>
          </div>

          {connectionState.lastUpdate && (
            <>
              <div className="h-4 border-l border-gray-300"></div>
              <div className="flex items-center space-x-1 text-sm text-gray-500">
                <Clock className="w-4 h-4" />
                <span>{connectionState.lastUpdate.toLocaleTimeString()}</span>
              </div>
            </>
          )}
        </div>

        <div className="bg-white rounded-lg shadow-md p-2">
          <button
            onClick={handleRefresh}
            className="p-2 hover:bg-gray-100 rounded transition-colors"
            title="Refresh data"
          >
            <RefreshCw className="w-5 h-5 text-gray-600" />
          </button>
        </div>
      </div>

      {/* Controls */}
      <div className="absolute top-20 left-4 z-[1000]">
        <div className="bg-white rounded-lg shadow-md p-3 space-y-2">
          <h3 className="font-semibold text-sm text-gray-700 mb-2">Show Vehicles</h3>
          
          <label className="flex items-center space-x-2 cursor-pointer">
            <input
              type="checkbox"
              checked={showBuses}
              onChange={(e) => setShowBuses(e.target.checked)}
              className="rounded"
            />
            <Bus className="w-4 h-4 text-blue-600" />
            <span className="text-sm">Buses ({buses.length})</span>
          </label>
          
          <label className="flex items-center space-x-2 cursor-pointer">
            <input
              type="checkbox"
              checked={showTrains}
              onChange={(e) => setShowTrains(e.target.checked)}
              className="rounded"
            />
            <Train className="w-4 h-4 text-purple-600" />
            <span className="text-sm">Trains ({trains.length})</span>
          </label>
        </div>
      </div>

      {/* Error Message */}
      {connectionState.error && (
        <div className="absolute top-4 left-1/2 transform -translate-x-1/2 z-[1000]">
          <div className="bg-red-100 border border-red-300 rounded-lg p-3 flex items-center space-x-2">
            <AlertCircle className="w-5 h-5 text-red-500" />
            <span className="text-sm text-red-700">{connectionState.error}</span>
          </div>
        </div>
      )}

      {/* Map */}
      <MapContainer
        center={[39.7392, -104.9903]} // Denver center
        zoom={11}
        className="h-full w-full"
        zoomControl={false}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        
        <MapController buses={visibleBuses} trains={visibleTrains} />

        {/* Bus Markers */}
        {visibleBuses.map((bus) => (
          <Marker
            key={`bus-${bus.vehicle_id}`}
            position={[bus.latitude, bus.longitude]}
            icon={createBusIcon(bus.route_id, bus.occupancy)}
          >
            <Popup maxWidth={300}>
              <div className="space-y-2">
                <div className="flex items-center space-x-2 border-b pb-2">
                  <Bus className="w-5 h-5 text-blue-600" />
                  <div>
                    <h3 className="font-semibold">Route {bus.route_id}</h3>
                    <p className="text-sm text-gray-600">Bus {bus.vehicle_id}</p>
                  </div>
                </div>
                
                <div className="space-y-1 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Direction:</span>
                    <span>{bus.direction}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Speed:</span>
                    <span>{bus.speed_mph.toFixed(1)} mph</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Status:</span>
                    <span>{bus.status}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Next Stop:</span>
                    <span>{bus.next_stop || 'Unknown'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Delay:</span>
                    <span className={bus.delay_seconds > 0 ? 'text-red-600' : 'text-green-600'}>
                      {formatDelay(bus.delay_seconds)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Occupancy:</span>
                    <span style={{ color: getOccupancyColor(bus.occupancy) }}>
                      {bus.occupancy.replace(/_/g, ' ')}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Updated:</span>
                    <span className={bus.is_real_time ? 'text-green-600' : 'text-gray-500'}>
                      {formatTimestamp(bus.timestamp_ms)}
                    </span>
                  </div>
                </div>
              </div>
            </Popup>
          </Marker>
        ))}

        {/* Train Markers */}
        {visibleTrains.map((train) => (
          <Marker
            key={`train-${train.train_id}`}
            position={[train.latitude, train.longitude]}
            icon={createTrainIcon(train.line_id)}
          >
            <Popup maxWidth={300}>
              <div className="space-y-2">
                <div className="flex items-center space-x-2 border-b pb-2">
                  <Train className="w-5 h-5 text-purple-600" />
                  <div>
                    <h3 className="font-semibold">{train.line_id} Line</h3>
                    <p className="text-sm text-gray-600">Train {train.train_id}</p>
                  </div>
                </div>
                
                <div className="space-y-1 text-sm">
                  <div className="flex justify-between">
                    <span className="text-gray-600">Direction:</span>
                    <span>{train.direction}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Speed:</span>
                    <span>{train.speed_mph.toFixed(1)} mph</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Status:</span>
                    <span>{train.status}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Next Station:</span>
                    <span>{train.next_station || 'Unknown'}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Delay:</span>
                    <span className={train.delay_seconds > 0 ? 'text-red-600' : 'text-green-600'}>
                      {formatDelay(train.delay_seconds)}
                    </span>
                  </div>
                  {train.operator_message && (
                    <div className="mt-2 p-2 bg-blue-50 rounded text-sm">
                      <p className="text-blue-800">{train.operator_message}</p>
                    </div>
                  )}
                  <div className="flex justify-between">
                    <span className="text-gray-600">Updated:</span>
                    <span className={train.is_real_time ? 'text-green-600' : 'text-gray-500'}>
                      {formatTimestamp(train.timestamp_ms)}
                    </span>
                  </div>
                </div>
              </div>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
};

export default LiveTransitMap;