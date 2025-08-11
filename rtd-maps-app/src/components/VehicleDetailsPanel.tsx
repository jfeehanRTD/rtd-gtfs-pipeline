// Vehicle Details Panel - Shows detailed information about selected vehicle

import React from 'react';
import { EnhancedVehicleData, VehicleStatus, OccupancyStatus } from '@/types/rtd';
import { 
  X, 
  MapPin, 
  Clock, 
  Navigation, 
  Gauge, 
  Users, 
  Route,
  Info,
  AlertTriangle,
  CheckCircle,
  Bus,
  Train
} from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

interface VehicleDetailsPanelProps {
  vehicle: EnhancedVehicleData | null;
  onClose: () => void;
  className?: string;
}

const VehicleDetailsPanel: React.FC<VehicleDetailsPanelProps> = ({
  vehicle,
  onClose,
  className = ''
}) => {
  if (!vehicle) return null;

  const isRail = vehicle.route_info?.route_type !== 3;
  const routeColor = vehicle.route_info?.route_color || (isRail ? '#0066CC' : '#FF6600');
  const isDelayed = (vehicle.delay_seconds || 0) > 300; // 5+ minutes
  const isRealTime = vehicle.is_real_time;

  const formatSpeed = (speedMs?: number): string => {
    if (!speedMs) return 'Unknown';
    const speedKmh = speedMs * 3.6;
    const speedMph = speedMs * 2.237;
    return `${speedKmh.toFixed(1)} km/h (${speedMph.toFixed(1)} mph)`;
  };

  const formatDelay = (delaySeconds?: number): string => {
    if (!delaySeconds) return 'On time';
    const minutes = Math.floor(Math.abs(delaySeconds) / 60);
    const seconds = Math.abs(delaySeconds) % 60;
    const sign = delaySeconds > 0 ? '+' : '-';
    return `${sign}${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const getStatusColor = (status: string): string => {
    switch (status) {
      case VehicleStatus.STOPPED_AT:
        return 'text-red-600 bg-red-50';
      case VehicleStatus.IN_TRANSIT_TO:
        return 'text-green-600 bg-green-50';
      case VehicleStatus.INCOMING_AT:
        return 'text-yellow-600 bg-yellow-50';
      default:
        return 'text-gray-600 bg-gray-50';
    }
  };

  const getOccupancyColor = (status?: string): string => {
    switch (status) {
      case OccupancyStatus.EMPTY:
      case OccupancyStatus.MANY_SEATS_AVAILABLE:
        return 'text-green-600 bg-green-50';
      case OccupancyStatus.FEW_SEATS_AVAILABLE:
        return 'text-yellow-600 bg-yellow-50';
      case OccupancyStatus.STANDING_ROOM_ONLY:
      case OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY:
        return 'text-orange-600 bg-orange-50';
      case OccupancyStatus.FULL:
      case OccupancyStatus.NOT_ACCEPTING_PASSENGERS:
        return 'text-red-600 bg-red-50';
      default:
        return 'text-gray-600 bg-gray-50';
    }
  };

  const formatOccupancyStatus = (status?: string): string => {
    if (!status) return 'Unknown';
    return status.split('_').map(word => 
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  };

  const formatVehicleStatus = (status: string): string => {
    return status.split('_').map(word => 
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  };

  return (
    <div className={`absolute top-4 right-4 w-80 bg-white rounded-lg shadow-xl border border-gray-200 overflow-hidden z-50 ${className}`}>
      {/* Header */}
      <div 
        className="px-4 py-3 text-white relative"
        style={{ backgroundColor: routeColor }}
      >
        <button
          onClick={onClose}
          className="absolute top-3 right-3 p-1 hover:bg-black/20 rounded transition-colors"
        >
          <X className="w-4 h-4" />
        </button>
        
        <div className="flex items-center space-x-3 pr-8">
          {isRail ? (
            <Train className="w-6 h-6" />
          ) : (
            <Bus className="w-6 h-6" />
          )}
          <div>
            <h3 className="font-bold text-lg">
              {vehicle.route_info?.route_short_name || vehicle.route_id}
            </h3>
            <p className="text-sm opacity-90">
              {vehicle.route_info?.route_long_name || 'Route Information'}
            </p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
        {/* Vehicle Status */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Info className="w-4 h-4 text-gray-500" />
              <span className="text-sm font-medium text-gray-700">Status</span>
            </div>
            <span className={`px-2 py-1 rounded-full text-xs font-medium ${getStatusColor(vehicle.current_status)}`}>
              {formatVehicleStatus(vehicle.current_status)}
            </span>
          </div>

          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Clock className="w-4 h-4 text-gray-500" />
              <span className="text-sm font-medium text-gray-700">Timing</span>
            </div>
            <div className="flex items-center space-x-2">
              {isDelayed ? (
                <AlertTriangle className="w-3 h-3 text-red-500" />
              ) : (
                <CheckCircle className="w-3 h-3 text-green-500" />
              )}
              <span className={`text-sm font-medium ${isDelayed ? 'text-red-600' : 'text-green-600'}`}>
                {formatDelay(vehicle.delay_seconds)}
              </span>
            </div>
          </div>
        </div>

        {/* Location & Movement */}
        <div className="space-y-3">
          <h4 className="font-medium text-gray-800 flex items-center">
            <MapPin className="w-4 h-4 mr-2" />
            Location & Movement
          </h4>
          
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div>
              <span className="text-gray-600">Latitude:</span>
              <div className="font-mono">{vehicle.latitude.toFixed(6)}</div>
            </div>
            <div>
              <span className="text-gray-600">Longitude:</span>
              <div className="font-mono">{vehicle.longitude.toFixed(6)}</div>
            </div>
          </div>

          {vehicle.bearing && (
            <div className="flex items-center space-x-2">
              <Navigation className="w-4 h-4 text-gray-500" />
              <span className="text-sm text-gray-600">Bearing:</span>
              <span className="text-sm font-medium">{Math.round(vehicle.bearing)}°</span>
            </div>
          )}

          {vehicle.speed && (
            <div className="flex items-center space-x-2">
              <Gauge className="w-4 h-4 text-gray-500" />
              <span className="text-sm text-gray-600">Speed:</span>
              <span className="text-sm font-medium">{formatSpeed(vehicle.speed)}</span>
            </div>
          )}
        </div>

        {/* Occupancy */}
        {vehicle.occupancy_status && (
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Users className="w-4 h-4 text-gray-500" />
              <span className="text-sm font-medium text-gray-700">Passenger Load</span>
            </div>
            <span className={`inline-block px-2 py-1 rounded-full text-xs font-medium ${getOccupancyColor(vehicle.occupancy_status)}`}>
              {formatOccupancyStatus(vehicle.occupancy_status)}
            </span>
          </div>
        )}

        {/* Trip Information */}
        <div className="space-y-2">
          <h4 className="font-medium text-gray-800 flex items-center">
            <Route className="w-4 h-4 mr-2" />
            Trip Details
          </h4>
          
          <div className="space-y-1 text-sm">
            <div className="flex justify-between">
              <span className="text-gray-600">Vehicle ID:</span>
              <span className="font-mono text-xs">{vehicle.vehicle_id}</span>
            </div>
            {vehicle.trip_id && (
              <div className="flex justify-between">
                <span className="text-gray-600">Trip ID:</span>
                <span className="font-mono text-xs">{vehicle.trip_id}</span>
              </div>
            )}
          </div>
        </div>

        {/* Data Freshness */}
        <div className="pt-3 border-t border-gray-200">
          <div className="flex items-center justify-between text-xs text-gray-500">
            <div className="flex items-center space-x-1">
              <div className={`w-2 h-2 rounded-full ${isRealTime ? 'bg-green-500' : 'bg-yellow-500'}`} />
              <span>{isRealTime ? 'Real-time' : 'Cached'}</span>
            </div>
            <span>Updated {formatDistanceToNow(vehicle.last_updated)} ago</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VehicleDetailsPanel;