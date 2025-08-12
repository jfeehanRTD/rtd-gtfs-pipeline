// Vehicle Tracker Component - Real-time tracking with play/pause controls

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { 
  Play, 
  Pause, 
  SkipForward,
  RotateCcw,
  Settings,
  Zap,
  Clock,
  Route,
  TrendingUp,
  MapPin,
  Navigation
} from 'lucide-react';
import { EnhancedVehicleData } from '@/types/rtd';
import { DataQueryService, VehicleHistory } from '@/services/dataQueryService';
import { formatDistanceToNow } from 'date-fns';

interface VehicleTrackerProps {
  selectedVehicles: Set<string>;
  onVehicleUpdate: (vehicles: EnhancedVehicleData[]) => void;
  onHistoryUpdate: (vehicleId: string, history: VehicleHistory) => void;
  className?: string;
}

interface TrackingStats {
  totalUpdates: number;
  avgSpeed: number;
  totalDistance: number;
  activeVehicles: number;
}

export const VehicleTracker: React.FC<VehicleTrackerProps> = ({
  selectedVehicles,
  onVehicleUpdate,
  onHistoryUpdate,
  className = ''
}) => {
  const [isTracking, setIsTracking] = useState(false);
  const [refreshInterval, setRefreshInterval] = useState(5); // seconds
  const [showSettings, setShowSettings] = useState(false);
  const [stats, setStats] = useState<TrackingStats>({
    totalUpdates: 0,
    avgSpeed: 0,
    totalDistance: 0,
    activeVehicles: 0
  });
  const [lastUpdate, setLastUpdate] = useState<Date | null>(null);
  const [trackedVehicles, setTrackedVehicles] = useState<Map<string, EnhancedVehicleData>>(new Map());
  const [vehicleHistories, setVehicleHistories] = useState<Map<string, VehicleHistory>>(new Map());
  
  const intervalRef = useRef<number | null>(null);
  const queryServiceRef = useRef<DataQueryService | null>(null);
  const unsubscribeFunctions = useRef<Map<string, () => void>>(new Map());

  useEffect(() => {
    queryServiceRef.current = DataQueryService.getInstance();
    
    return () => {
      stopTracking();
      if (queryServiceRef.current) {
        queryServiceRef.current.destroy();
      }
    };
  }, []);

  useEffect(() => {
    if (isTracking) {
      startTracking();
    } else {
      stopTracking();
    }
  }, [isTracking, refreshInterval]); // Removed selectedVehicles from deps to prevent constant re-starts

  const startTracking = useCallback(() => {
    // console.log('🎯 Starting vehicle tracking...');
    
    // Clear any existing interval
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
    }

    // Subscribe to WebSocket updates for each selected vehicle
    selectedVehicles.forEach(vehicleId => {
      if (!unsubscribeFunctions.current.has(vehicleId) && queryServiceRef.current) {
        const unsubscribe = queryServiceRef.current.subscribeToVehicle(
          vehicleId,
          (vehicle) => handleVehicleWebSocketUpdate(vehicleId, vehicle)
        );
        unsubscribeFunctions.current.set(vehicleId, unsubscribe);
      }
    });

    // Set up polling interval for batch updates
    const fetchVehicleUpdates = async () => {
      if (!queryServiceRef.current || selectedVehicles.size === 0) return;

      try {
        const vehicles = await queryServiceRef.current.queryVehicles({
          sources: ['kafka-vehicles', 'rtd-direct', 'websocket-stream'],
          vehicleIds: Array.from(selectedVehicles),
          realTimeOnly: true
        });

        handleBatchUpdate(vehicles);
      } catch (error) {
        console.error('Failed to fetch vehicle updates:', error);
      }
    };

    // Initial fetch
    fetchVehicleUpdates();

    // Set up interval
    intervalRef.current = window.setInterval(fetchVehicleUpdates, refreshInterval * 1000);
  }, [selectedVehicles, refreshInterval]);

  const stopTracking = useCallback(() => {
    console.log('⏹ Stopping vehicle tracking');
    
    if (intervalRef.current) {
      window.clearInterval(intervalRef.current);
      intervalRef.current = null;
    }

    // Unsubscribe from WebSocket updates
    unsubscribeFunctions.current.forEach(unsubscribe => unsubscribe());
    unsubscribeFunctions.current.clear();
  }, []);

  const handleVehicleWebSocketUpdate = (vehicleId: string, vehicle: EnhancedVehicleData) => {
    setTrackedVehicles(prev => {
      const next = new Map(prev);
      next.set(vehicleId, vehicle);
      return next;
    });

    // Update history
    updateVehicleHistory(vehicleId, vehicle);
    
    // Notify parent
    onVehicleUpdate([vehicle]);
    
    // Update stats
    updateStats([vehicle]);
    setLastUpdate(new Date());
  };

  const handleBatchUpdate = (vehicles: EnhancedVehicleData[]) => {
    // Update tracked vehicles
    const newTracked = new Map(trackedVehicles);
    vehicles.forEach(vehicle => {
      newTracked.set(vehicle.vehicle_id, vehicle);
      updateVehicleHistory(vehicle.vehicle_id, vehicle);
    });
    setTrackedVehicles(newTracked);

    // Notify parent
    onVehicleUpdate(vehicles);
    
    // Update stats
    updateStats(vehicles);
    setLastUpdate(new Date());
  };

  const updateVehicleHistory = (vehicleId: string, vehicle: EnhancedVehicleData) => {
    setVehicleHistories(prev => {
      const next = new Map(prev);
      const existing = next.get(vehicleId);
      
      if (existing) {
        existing.positions.push({
          timestamp: new Date(vehicle.timestamp_ms),
          latitude: vehicle.latitude,
          longitude: vehicle.longitude,
          speed: vehicle.speed,
          bearing: vehicle.bearing,
          status: vehicle.current_status
        });
        
        // Keep only last 100 positions
        if (existing.positions.length > 100) {
          existing.positions.shift();
        }
      } else {
        next.set(vehicleId, {
          vehicleId,
          positions: [{
            timestamp: new Date(vehicle.timestamp_ms),
            latitude: vehicle.latitude,
            longitude: vehicle.longitude,
            speed: vehicle.speed,
            bearing: vehicle.bearing,
            status: vehicle.current_status
          }]
        });
      }
      
      const history = next.get(vehicleId)!;
      onHistoryUpdate(vehicleId, history);
      
      return next;
    });
  };

  const updateStats = (vehicles: EnhancedVehicleData[]) => {
    setStats(prev => {
      const speeds = vehicles
        .map(v => v.speed || 0)
        .filter(s => s > 0);
      
      const avgSpeed = speeds.length > 0 
        ? speeds.reduce((a, b) => a + b, 0) / speeds.length 
        : prev.avgSpeed;

      return {
        totalUpdates: prev.totalUpdates + vehicles.length,
        avgSpeed: Math.round(avgSpeed * 2.237), // Convert m/s to mph
        totalDistance: prev.totalDistance + vehicles.length * 0.1, // Rough estimate
        activeVehicles: trackedVehicles.size
      };
    });
  };

  const handleSkipUpdate = async () => {
    if (!queryServiceRef.current || selectedVehicles.size === 0) return;

    const vehicles = await queryServiceRef.current.queryVehicles({
      sources: ['kafka-vehicles', 'rtd-direct'],
      vehicleIds: Array.from(selectedVehicles),
      realTimeOnly: true
    });

    handleBatchUpdate(vehicles);
  };

  const handleReset = () => {
    setTrackedVehicles(new Map());
    setVehicleHistories(new Map());
    setStats({
      totalUpdates: 0,
      avgSpeed: 0,
      totalDistance: 0,
      activeVehicles: 0
    });
    setLastUpdate(null);
  };

  const formatInterval = (seconds: number): string => {
    if (seconds < 60) return `${seconds}s`;
    return `${Math.floor(seconds / 60)}m`;
  };

  return (
    <div className={`bg-white rounded-lg shadow-lg border border-gray-200 ${className}`}>
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <Navigation className="w-5 h-5 text-rtd-primary" />
            <h3 className="text-lg font-semibold text-gray-800">Vehicle Tracker</h3>
            {isTracking && (
              <div className="flex items-center space-x-1">
                <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse" />
                <span className="text-sm text-green-600">Live</span>
              </div>
            )}
          </div>
          
          <button
            onClick={() => setShowSettings(!showSettings)}
            className="p-2 hover:bg-gray-100 rounded-md transition-colors"
          >
            <Settings className="w-4 h-4 text-gray-600" />
          </button>
        </div>
      </div>

      {/* Settings Panel */}
      {showSettings && (
        <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
          <div className="space-y-3">
            <div>
              <label className="text-sm font-medium text-gray-700">
                Refresh Interval
              </label>
              <div className="mt-1 flex items-center space-x-2">
                <input
                  type="range"
                  min="1"
                  max="60"
                  value={refreshInterval}
                  onChange={(e) => setRefreshInterval(Number(e.target.value))}
                  className="flex-1"
                />
                <span className="text-sm text-gray-600 w-12 text-right">
                  {formatInterval(refreshInterval)}
                </span>
              </div>
            </div>
            
            <div className="flex items-center justify-between text-sm">
              <span className="text-gray-600">High frequency mode</span>
              <button
                onClick={() => setRefreshInterval(1)}
                className="px-2 py-1 bg-rtd-primary text-white rounded text-xs hover:bg-rtd-dark"
              >
                1s updates
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Controls */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            {/* Play/Pause */}
            <button
              onClick={() => setIsTracking(!isTracking)}
              disabled={selectedVehicles.size === 0}
              className={`p-2 rounded-md transition-colors flex items-center space-x-2 ${
                isTracking 
                  ? 'bg-red-500 hover:bg-red-600 text-white' 
                  : 'bg-green-500 hover:bg-green-600 text-white disabled:bg-gray-300'
              }`}
            >
              {isTracking ? (
                <>
                  <Pause className="w-4 h-4" />
                  <span className="text-sm font-medium">Pause</span>
                </>
              ) : (
                <>
                  <Play className="w-4 h-4" />
                  <span className="text-sm font-medium">Start</span>
                </>
              )}
            </button>

            {/* Skip */}
            <button
              onClick={handleSkipUpdate}
              disabled={!isTracking || selectedVehicles.size === 0}
              className="p-2 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors disabled:opacity-50"
              title="Force update"
            >
              <SkipForward className="w-4 h-4 text-gray-600" />
            </button>

            {/* Reset */}
            <button
              onClick={handleReset}
              className="p-2 bg-gray-100 hover:bg-gray-200 rounded-md transition-colors"
              title="Reset tracking"
            >
              <RotateCcw className="w-4 h-4 text-gray-600" />
            </button>
          </div>

          {/* Status */}
          <div className="flex items-center space-x-4 text-sm">
            <div className="flex items-center space-x-1 text-gray-600">
              <Clock className="w-3 h-3" />
              <span>
                {lastUpdate ? formatDistanceToNow(lastUpdate) + ' ago' : 'Never'}
              </span>
            </div>
            
            <div className="flex items-center space-x-1">
              <Zap className="w-3 h-3 text-yellow-500" />
              <span className="text-gray-600">
                Every {formatInterval(refreshInterval)}
              </span>
            </div>
          </div>
        </div>

        {/* Selected vehicles info */}
        {selectedVehicles.size > 0 && (
          <div className="mt-2 text-sm text-gray-600">
            Tracking {selectedVehicles.size} vehicle{selectedVehicles.size !== 1 ? 's' : ''}
          </div>
        )}
      </div>

      {/* Stats */}
      <div className="px-4 py-3">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="space-y-1">
            <div className="text-xs text-gray-500 uppercase tracking-wider">Updates</div>
            <div className="text-xl font-semibold text-gray-800">
              {stats.totalUpdates.toLocaleString()}
            </div>
          </div>
          
          <div className="space-y-1">
            <div className="text-xs text-gray-500 uppercase tracking-wider">Avg Speed</div>
            <div className="text-xl font-semibold text-gray-800">
              {stats.avgSpeed} mph
            </div>
          </div>
          
          <div className="space-y-1">
            <div className="text-xs text-gray-500 uppercase tracking-wider">Distance</div>
            <div className="text-xl font-semibold text-gray-800">
              {stats.totalDistance.toFixed(1)} mi
            </div>
          </div>
          
          <div className="space-y-1">
            <div className="text-xs text-gray-500 uppercase tracking-wider">Active</div>
            <div className="text-xl font-semibold text-gray-800">
              {stats.activeVehicles}
            </div>
          </div>
        </div>

        {/* Tracked vehicles list */}
        {trackedVehicles.size > 0 && (
          <div className="mt-4 pt-4 border-t border-gray-200">
            <div className="text-sm font-medium text-gray-700 mb-2">Tracked Vehicles</div>
            <div className="space-y-1 max-h-32 overflow-y-auto">
              {Array.from(trackedVehicles.values()).map(vehicle => (
                <div key={vehicle.vehicle_id} className="flex items-center justify-between text-xs">
                  <div className="flex items-center space-x-2">
                    <div className={`w-2 h-2 rounded-full ${
                      vehicle.is_real_time ? 'bg-green-500' : 'bg-gray-400'
                    }`} />
                    <span className="font-mono">{vehicle.vehicle_id.slice(-8)}</span>
                    <span className="text-gray-500">Route {vehicle.route_id}</span>
                  </div>
                  <div className="flex items-center space-x-2 text-gray-500">
                    <MapPin className="w-3 h-3" />
                    <span>{vehicle.speed ? `${Math.round(vehicle.speed * 2.237)} mph` : 'Stopped'}</span>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default VehicleTracker;