// Vehicle Selector Component - Search, filter and select individual vehicles

import React, { useState, useEffect, useMemo } from 'react';
import { 
  Search, 
  Filter, 
  Bus, 
  Train, 
  MapPin, 
  Clock, 
  Users,
  Activity,
  CheckCircle,
  Circle,
  X,
  ChevronDown,
  ChevronUp,
  Layers,
  Database
} from 'lucide-react';
import { EnhancedVehicleData } from '@/types/rtd';
import { formatDistanceToNow } from 'date-fns';

interface VehicleSelectorProps {
  vehicles: EnhancedVehicleData[];
  selectedVehicles: Set<string>;
  onVehicleToggle: (vehicleId: string) => void;
  onSelectAll: () => void;
  onClearAll: () => void;
  onClose?: () => void;
}

interface VehicleGroup {
  route: string;
  vehicles: EnhancedVehicleData[];
  expanded: boolean;
}

export const VehicleSelector: React.FC<VehicleSelectorProps> = ({
  vehicles,
  selectedVehicles,
  onVehicleToggle,
  onSelectAll,
  onClearAll,
  onClose
}) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [filterType, setFilterType] = useState<'all' | 'bus' | 'train'>('all');
  const [sortBy, setSortBy] = useState<'id' | 'route' | 'time' | 'speed'>('route');
  const [showRealTimeOnly, setShowRealTimeOnly] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  // Filter and sort vehicles
  const processedVehicles = useMemo(() => {
    let filtered = vehicles.filter(vehicle => {
      // Search filter
      if (searchTerm) {
        const search = searchTerm.toLowerCase();
        const matchesId = vehicle.vehicle_id.toLowerCase().includes(search);
        const matchesRoute = vehicle.route_id?.toLowerCase().includes(search);
        const matchesTrip = vehicle.trip_id?.toLowerCase().includes(search);
        if (!matchesId && !matchesRoute && !matchesTrip) {
          return false;
        }
      }

      // Type filter
      if (filterType !== 'all') {
        const isBus = vehicle.route_info?.route_type === 3;
        if (filterType === 'bus' && !isBus) return false;
        if (filterType === 'train' && isBus) return false;
      }

      // Real-time filter
      if (showRealTimeOnly && !vehicle.is_real_time) {
        return false;
      }

      return true;
    });

    // Sort vehicles
    filtered.sort((a, b) => {
      switch (sortBy) {
        case 'id':
          return a.vehicle_id.localeCompare(b.vehicle_id);
        case 'route':
          return (a.route_id || '').localeCompare(b.route_id || '');
        case 'time':
          return b.timestamp_ms - a.timestamp_ms;
        case 'speed':
          return (b.speed || 0) - (a.speed || 0);
        default:
          return 0;
      }
    });

    return filtered;
  }, [vehicles, searchTerm, filterType, sortBy, showRealTimeOnly]);

  // Group vehicles by route
  const vehicleGroups = useMemo(() => {
    const groups = new Map<string, EnhancedVehicleData[]>();
    
    processedVehicles.forEach(vehicle => {
      const route = vehicle.route_id || 'Unknown';
      if (!groups.has(route)) {
        groups.set(route, []);
      }
      groups.get(route)!.push(vehicle);
    });

    return Array.from(groups.entries())
      .map(([route, vehicles]) => ({
        route,
        vehicles,
        expanded: expandedGroups.has(route)
      }))
      .sort((a, b) => a.route.localeCompare(b.route));
  }, [processedVehicles, expandedGroups]);

  const toggleGroup = (route: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(route)) {
        next.delete(route);
      } else {
        next.add(route);
      }
      return next;
    });
  };

  const getVehicleIcon = (vehicle: EnhancedVehicleData) => {
    const isBus = vehicle.route_info?.route_type === 3;
    return isBus ? Bus : Train;
  };

  const getStatusColor = (vehicle: EnhancedVehicleData) => {
    if (!vehicle.is_real_time) return 'text-gray-400';
    if (vehicle.delay_seconds && vehicle.delay_seconds > 300) return 'text-red-500';
    if (vehicle.current_status === 'STOPPED_AT') return 'text-yellow-500';
    return 'text-green-500';
  };

  const getOccupancyIcon = (status?: string) => {
    switch (status) {
      case 'EMPTY':
        return '○○○';
      case 'MANY_SEATS_AVAILABLE':
        return '●○○';
      case 'FEW_SEATS_AVAILABLE':
        return '●●○';
      case 'STANDING_ROOM_ONLY':
        return '●●●';
      case 'CRUSHED_STANDING_ROOM_ONLY':
        return '●●●!';
      case 'FULL':
        return '●●●X';
      default:
        return '---';
    }
  };

  return (
    <div className="bg-white rounded-lg shadow-xl border border-gray-200 flex flex-col h-full max-h-[600px]">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2">
            <Layers className="w-5 h-5 text-rtd-primary" />
            <h3 className="text-lg font-semibold text-gray-800">Vehicle Selector</h3>
          </div>
          {onClose && (
            <button
              onClick={onClose}
              className="p-1 hover:bg-gray-100 rounded-md transition-colors"
            >
              <X className="w-5 h-5 text-gray-500" />
            </button>
          )}
        </div>

        {/* Search */}
        <div className="relative mb-3">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search by vehicle ID, route, or trip..."
            className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-rtd-primary focus:border-transparent"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>

        {/* Filters */}
        <div className="flex items-center justify-between space-x-2">
          <div className="flex items-center space-x-2">
            {/* Type filter */}
            <button
              onClick={() => setFilterType('all')}
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors ${
                filterType === 'all' 
                  ? 'bg-rtd-primary text-white' 
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              All
            </button>
            <button
              onClick={() => setFilterType('bus')}
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center space-x-1 ${
                filterType === 'bus' 
                  ? 'bg-rtd-primary text-white' 
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              <Bus className="w-3 h-3" />
              <span>Buses</span>
            </button>
            <button
              onClick={() => setFilterType('train')}
              className={`px-3 py-1 rounded-md text-sm font-medium transition-colors flex items-center space-x-1 ${
                filterType === 'train' 
                  ? 'bg-rtd-primary text-white' 
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              <Train className="w-3 h-3" />
              <span>Trains</span>
            </button>
          </div>

          {/* Real-time toggle */}
          <label className="flex items-center space-x-2 cursor-pointer">
            <input
              type="checkbox"
              checked={showRealTimeOnly}
              onChange={(e) => setShowRealTimeOnly(e.target.checked)}
              className="rounded border-gray-300 text-rtd-primary focus:ring-rtd-primary"
            />
            <span className="text-sm text-gray-600">Real-time only</span>
            <Activity className="w-3 h-3 text-green-500" />
          </label>
        </div>

        {/* Sort options */}
        <div className="flex items-center justify-between mt-3">
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value as any)}
            className="px-3 py-1 border border-gray-300 rounded-md text-sm focus:ring-2 focus:ring-rtd-primary focus:border-transparent"
          >
            <option value="route">Sort by Route</option>
            <option value="id">Sort by ID</option>
            <option value="time">Sort by Time</option>
            <option value="speed">Sort by Speed</option>
          </select>

          <div className="flex items-center space-x-2">
            <button
              onClick={onSelectAll}
              className="px-3 py-1 bg-green-500 text-white rounded-md text-sm hover:bg-green-600 transition-colors"
            >
              Select All
            </button>
            <button
              onClick={onClearAll}
              className="px-3 py-1 bg-gray-500 text-white rounded-md text-sm hover:bg-gray-600 transition-colors"
            >
              Clear All
            </button>
          </div>
        </div>
      </div>

      {/* Stats */}
      <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-600">
            {processedVehicles.length} vehicles found
          </span>
          <span className="text-rtd-primary font-medium">
            {selectedVehicles.size} selected
          </span>
        </div>
      </div>

      {/* Vehicle List */}
      <div className="flex-1 overflow-y-auto">
        {vehicleGroups.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <Database className="w-12 h-12 mx-auto mb-3 text-gray-300" />
            <p>No vehicles found matching criteria</p>
          </div>
        ) : (
          <div className="divide-y divide-gray-200">
            {vehicleGroups.map(group => (
              <div key={group.route} className="border-b border-gray-100">
                {/* Group Header */}
                <button
                  onClick={() => toggleGroup(group.route)}
                  className="w-full px-4 py-2 flex items-center justify-between hover:bg-gray-50 transition-colors"
                >
                  <div className="flex items-center space-x-2">
                    {group.expanded ? (
                      <ChevronUp className="w-4 h-4 text-gray-400" />
                    ) : (
                      <ChevronDown className="w-4 h-4 text-gray-400" />
                    )}
                    <span className="font-medium text-gray-700">
                      Route {group.route}
                    </span>
                    <span className="text-sm text-gray-500">
                      ({group.vehicles.length} vehicles)
                    </span>
                  </div>
                  <div className="text-sm text-gray-500">
                    {group.vehicles.filter(v => selectedVehicles.has(v.vehicle_id)).length} selected
                  </div>
                </button>

                {/* Expanded Vehicle List */}
                {group.expanded && (
                  <div className="bg-gray-50">
                    {group.vehicles.map(vehicle => {
                      const Icon = getVehicleIcon(vehicle);
                      const isSelected = selectedVehicles.has(vehicle.vehicle_id);
                      const statusColor = getStatusColor(vehicle);
                      
                      return (
                        <div
                          key={vehicle.vehicle_id}
                          className={`px-4 py-3 hover:bg-white cursor-pointer transition-colors border-l-4 ${
                            isSelected ? 'border-l-rtd-primary bg-rtd-light/10' : 'border-l-transparent'
                          }`}
                          onClick={() => onVehicleToggle(vehicle.vehicle_id)}
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center space-x-3">
                              {/* Selection checkbox */}
                              {isSelected ? (
                                <CheckCircle className="w-5 h-5 text-rtd-primary" />
                              ) : (
                                <Circle className="w-5 h-5 text-gray-400" />
                              )}

                              {/* Vehicle icon */}
                              <Icon className={`w-5 h-5 ${statusColor}`} />

                              {/* Vehicle info */}
                              <div>
                                <div className="font-medium text-gray-800">
                                  {vehicle.vehicle_id.slice(-8)}
                                </div>
                                <div className="text-xs text-gray-500">
                                  Trip: {vehicle.trip_id?.slice(-8) || 'N/A'}
                                </div>
                              </div>
                            </div>

                            {/* Vehicle details */}
                            <div className="flex items-center space-x-4 text-sm">
                              {/* Speed */}
                              <div className="flex items-center space-x-1">
                                <Activity className="w-3 h-3 text-gray-400" />
                                <span className="text-gray-600">
                                  {vehicle.speed ? `${Math.round(vehicle.speed * 2.237)} mph` : '--'}
                                </span>
                              </div>

                              {/* Occupancy */}
                              <div className="text-xs font-mono">
                                {getOccupancyIcon(vehicle.occupancy_status)}
                              </div>

                              {/* Status */}
                              <div className={`flex items-center space-x-1 ${statusColor}`}>
                                <MapPin className="w-3 h-3" />
                                <span className="text-xs">
                                  {vehicle.current_status?.replace(/_/g, ' ')}
                                </span>
                              </div>

                              {/* Time */}
                              <div className="flex items-center space-x-1 text-gray-500">
                                <Clock className="w-3 h-3" />
                                <span className="text-xs">
                                  {formatDistanceToNow(vehicle.timestamp_ms)} ago
                                </span>
                              </div>
                            </div>
                          </div>

                          {/* Delay indicator */}
                          {vehicle.delay_seconds && vehicle.delay_seconds > 60 && (
                            <div className="mt-1 text-xs text-red-600 pl-11">
                              Delayed by {Math.round(vehicle.delay_seconds / 60)} minutes
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default VehicleSelector;