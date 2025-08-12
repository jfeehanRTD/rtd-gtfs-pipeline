// Map Controls - Filters and settings for the RTD transit map

import { useState } from 'react';
import { MapFilters } from '@/types/rtd';
import { 
  Filter,
  Settings,
  Bus,
  Train,
  Clock,
  Eye,
  MapPin,
  Info
} from 'lucide-react';

interface MapControlsProps {
  filters: MapFilters;
  onFiltersChange: (filters: MapFilters) => void;
  vehicleCount: number;
  totalVehicles: number;
}

const MapControls: React.FC<MapControlsProps> = ({
  filters,
  onFiltersChange,
  vehicleCount,
  totalVehicles
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [showRouteSelector, setShowRouteSelector] = useState(false);

  // Common RTD routes for quick selection
  const commonRoutes = [
    { id: 'A', name: 'A Line', type: 'rail', color: '#0066CC' },
    { id: 'B', name: 'B Line', type: 'rail', color: '#00AA44' },
    { id: 'C', name: 'C Line', type: 'rail', color: '#FF6600' },
    { id: 'D', name: 'D Line', type: 'rail', color: '#FFD700' },
    { id: 'E', name: 'E Line', type: 'rail', color: '#800080' },
    { id: '15', name: 'Route 15', type: 'bus', color: '#666666' },
    { id: '16', name: '16th Mall', type: 'bus', color: '#666666' },
    { id: '20', name: 'Route 20', type: 'bus', color: '#666666' }
  ];

  const toggleRoute = (routeId: string) => {
    const newSelectedRoutes = filters.selectedRoutes.includes(routeId)
      ? filters.selectedRoutes.filter(id => id !== routeId)
      : [...filters.selectedRoutes, routeId];
    
    onFiltersChange({
      ...filters,
      selectedRoutes: newSelectedRoutes
    });
  };

  const clearAllRoutes = () => {
    onFiltersChange({
      ...filters,
      selectedRoutes: []
    });
  };

  const selectAllRail = () => {
    const railRoutes = commonRoutes.filter(route => route.type === 'rail').map(route => route.id);
    onFiltersChange({
      ...filters,
      selectedRoutes: [...new Set([...filters.selectedRoutes, ...railRoutes])]
    });
  };

  const selectAllBus = () => {
    const busRoutes = commonRoutes.filter(route => route.type === 'bus').map(route => route.id);
    onFiltersChange({
      ...filters,
      selectedRoutes: [...new Set([...filters.selectedRoutes, ...busRoutes])]
    });
  };

  return (
    <>
      {/* Main Control Panel */}
      <div className="absolute top-4 left-4 bg-white rounded-lg shadow-lg border border-gray-200 overflow-hidden max-w-sm">
        {/* Header */}
        <div className="px-4 py-3 bg-rtd-primary text-white flex items-center justify-between">
          <div className="flex items-center space-x-2">
            <MapPin className="w-5 h-5" />
            <h3 className="font-semibold">RTD Live Map</h3>
          </div>
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            className="p-1 hover:bg-rtd-dark rounded transition-colors"
          >
            <Settings className={`w-4 h-4 transition-transform ${isExpanded ? 'rotate-180' : ''}`} />
          </button>
        </div>

        {/* Vehicle Count */}
        <div className="px-4 py-2 bg-gray-50 border-b border-gray-200">
          <div className="flex items-center justify-between text-sm">
            <span className="text-gray-600">Showing vehicles:</span>
            <span className="font-semibold text-rtd-primary">
              {vehicleCount.toLocaleString()} / {totalVehicles.toLocaleString()}
            </span>
          </div>
        </div>

        {/* Expanded Controls */}
        {isExpanded && (
          <div className="p-4 space-y-4 max-h-96 overflow-y-auto">
            {/* Vehicle Type Filters */}
            <div>
              <h4 className="font-medium text-gray-800 mb-2 flex items-center">
                <Filter className="w-4 h-4 mr-2" />
                Vehicle Types
              </h4>
              <div className="space-y-2">
                <label className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    checked={filters.showBuses}
                    onChange={(e) => onFiltersChange({ ...filters, showBuses: e.target.checked })}
                    className="rounded border-gray-300 text-transport-bus focus:ring-transport-bus"
                  />
                  <Bus className="w-4 h-4 text-transport-bus" />
                  <span className="text-sm">Buses</span>
                </label>
                <label className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    checked={filters.showTrains}
                    onChange={(e) => onFiltersChange({ ...filters, showTrains: e.target.checked })}
                    className="rounded border-gray-300 text-transport-rail focus:ring-transport-rail"
                  />
                  <Train className="w-4 h-4 text-transport-rail" />
                  <span className="text-sm">Light Rail</span>
                </label>
              </div>
            </div>

            {/* Delay Filter */}
            <div>
              <h4 className="font-medium text-gray-800 mb-2 flex items-center">
                <Clock className="w-4 h-4 mr-2" />
                Delays
              </h4>
              <label className="flex items-center space-x-2 mb-2">
                <input
                  type="checkbox"
                  checked={filters.showDelayedOnly}
                  onChange={(e) => onFiltersChange({ ...filters, showDelayedOnly: e.target.checked })}
                  className="rounded border-gray-300 text-red-500 focus:ring-red-500"
                />
                <span className="text-sm">Show delayed vehicles only</span>
              </label>
              {filters.showDelayedOnly && (
                <div className="ml-6">
                  <label className="text-xs text-gray-600 block mb-1">
                    Min delay: {Math.floor(filters.minDelaySeconds / 60)} minutes
                  </label>
                  <input
                    type="range"
                    min="60"
                    max="1800"
                    step="60"
                    value={filters.minDelaySeconds}
                    onChange={(e) => onFiltersChange({ ...filters, minDelaySeconds: parseInt(e.target.value) })}
                    className="w-full h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                  />
                </div>
              )}
            </div>

            {/* Route Selection */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <h4 className="font-medium text-gray-800 flex items-center">
                  <Eye className="w-4 h-4 mr-2" />
                  Routes
                </h4>
                <button
                  onClick={() => setShowRouteSelector(!showRouteSelector)}
                  className="text-xs text-rtd-primary hover:text-rtd-dark transition-colors"
                >
                  {showRouteSelector ? 'Hide' : 'Select'}
                </button>
              </div>
              
              {filters.selectedRoutes.length > 0 && (
                <div className="flex flex-wrap gap-1 mb-2">
                  {filters.selectedRoutes.map(routeId => {
                    const route = commonRoutes.find(r => r.id === routeId);
                    return (
                      <span
                        key={routeId}
                        className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-rtd-light text-rtd-primary"
                      >
                        {route?.name || routeId}
                        <button
                          onClick={() => toggleRoute(routeId)}
                          className="ml-1 text-rtd-primary hover:text-red-500"
                        >
                          ×
                        </button>
                      </span>
                    );
                  })}
                  <button
                    onClick={clearAllRoutes}
                    className="text-xs text-red-500 hover:text-red-700 underline"
                  >
                    Clear all
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Route Selector Modal */}
      {showRouteSelector && (
        <div className="absolute top-4 left-80 bg-white rounded-lg shadow-lg border border-gray-200 p-4 max-w-xs z-50">
          <div className="flex items-center justify-between mb-3">
            <h4 className="font-medium text-gray-800">Select Routes</h4>
            <button
              onClick={() => setShowRouteSelector(false)}
              className="text-gray-400 hover:text-gray-600"
            >
              ×
            </button>
          </div>
          
          {/* Quick selections */}
          <div className="flex space-x-2 mb-3">
            <button
              onClick={selectAllRail}
              className="flex-1 px-2 py-1 text-xs bg-transport-rail text-white rounded hover:bg-blue-600 transition-colors"
            >
              All Rail
            </button>
            <button
              onClick={selectAllBus}
              className="flex-1 px-2 py-1 text-xs bg-transport-bus text-white rounded hover:bg-orange-600 transition-colors"
            >
              All Bus
            </button>
            <button
              onClick={clearAllRoutes}
              className="flex-1 px-2 py-1 text-xs bg-gray-500 text-white rounded hover:bg-gray-600 transition-colors"
            >
              Clear
            </button>
          </div>

          {/* Route list */}
          <div className="space-y-1 max-h-64 overflow-y-auto">
            {commonRoutes.map(route => (
              <label key={route.id} className="flex items-center space-x-2 p-2 hover:bg-gray-50 rounded cursor-pointer">
                <input
                  type="checkbox"
                  checked={filters.selectedRoutes.includes(route.id)}
                  onChange={() => toggleRoute(route.id)}
                  className="rounded border-gray-300"
                  style={{ color: route.color }}
                />
                <div 
                  className="w-3 h-3 rounded"
                  style={{ backgroundColor: route.color }}
                />
                <span className="text-sm flex-1">{route.name}</span>
                <span className="text-xs text-gray-500 capitalize">{route.type}</span>
              </label>
            ))}
          </div>
        </div>
      )}

      {/* Info Panel */}
      <div className="absolute bottom-4 left-4 bg-white rounded-lg shadow-lg border border-gray-200 p-3">
        <div className="flex items-center space-x-2 text-sm text-gray-600">
          <Info className="w-4 h-4" />
          <span>Live RTD transit data • Updated every 30s</span>
        </div>
      </div>
    </>
  );
};

export default MapControls;