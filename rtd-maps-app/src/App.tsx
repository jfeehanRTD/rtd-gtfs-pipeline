// RTD Live Transit Map - Main Application Component

import React, { useState, useCallback } from 'react';
import OpenStreetMap from './components/OpenStreetMap';
import VehicleDetailsPanel from './components/VehicleDetailsPanel';
import { useRTDData } from './hooks/useRTDData';
import { EnhancedVehicleData, MapFilters } from './types/rtd';
import { 
  Wifi, 
  WifiOff, 
  RefreshCw, 
  AlertCircle, 
  CheckCircle,
  Clock,
  MapPin
} from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

function App() {
  const [selectedVehicle, setSelectedVehicle] = useState<EnhancedVehicleData | null>(null);
  const [filters, setFilters] = useState<MapFilters>({
    showBuses: true,
    showTrains: true,
    selectedRoutes: [],
    showDelayedOnly: false,
    minDelaySeconds: 300
  });

  const {
    vehicles,
    alerts,
    connectionState,
    isLoading,
    error,
    refresh,
    filteredVehicles,
    applyFilters
  } = useRTDData();

  // Handle vehicle selection
  const handleVehicleSelect = useCallback((vehicle: EnhancedVehicleData | null) => {
    setSelectedVehicle(vehicle);
  }, []);

  // Handle filter changes
  const handleFiltersChange = useCallback((newFilters: MapFilters) => {
    setFilters(newFilters);
    applyFilters(newFilters);
  }, [applyFilters]);

  // Handle refresh
  const handleRefresh = useCallback(async () => {
    await refresh();
  }, [refresh]);

  // No API key needed for OpenStreetMap!

  return (
    <div className="h-screen relative bg-gray-100 font-sans">
      {/* Status Bar */}
      <div className="absolute top-0 left-0 right-0 bg-white border-b border-gray-200 px-4 py-2 z-50">
        <div className="flex items-center justify-between">
          {/* Left: Connection Status */}
          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2">
              {connectionState.isConnected ? (
                <>
                  <Wifi className="w-4 h-4 text-green-500" />
                  <span className="text-sm text-green-600 font-medium">Connected</span>
                </>
              ) : (
                <>
                  <WifiOff className="w-4 h-4 text-red-500" />
                  <span className="text-sm text-red-600 font-medium">Disconnected</span>
                </>
              )}
            </div>
            
            {connectionState.lastUpdate && (
              <div className="flex items-center space-x-1 text-sm text-gray-600">
                <Clock className="w-3 h-3" />
                <span>Updated {formatDistanceToNow(connectionState.lastUpdate)} ago</span>
              </div>
            )}
          </div>

          {/* Center: Title */}
          <div className="flex items-center space-x-2">
            <MapPin className="w-5 h-5 text-rtd-primary" />
            <h1 className="text-lg font-bold text-gray-800">RTD Live Transit Map</h1>
          </div>

          {/* Right: Actions */}
          <div className="flex items-center space-x-4">
            <div className="text-sm text-gray-600">
              <span className="font-medium">{filteredVehicles.length}</span>
              <span className="text-gray-500"> / {vehicles.length} vehicles</span>
            </div>
            
            <button
              onClick={handleRefresh}
              disabled={isLoading}
              className="flex items-center space-x-1 px-3 py-1 bg-rtd-primary text-white rounded-md hover:bg-rtd-dark disabled:opacity-50 transition-colors"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
              <span className="text-sm">Refresh</span>
            </button>
          </div>
        </div>

        {/* Error Bar */}
        {error && (
          <div className="mt-2 p-2 bg-red-50 border border-red-200 rounded-md flex items-center space-x-2">
            <AlertCircle className="w-4 h-4 text-red-500 flex-shrink-0" />
            <span className="text-sm text-red-700">{error}</span>
            <button
              onClick={handleRefresh}
              className="ml-auto text-sm text-red-600 hover:text-red-800 underline"
            >
              Retry
            </button>
          </div>
        )}

        {/* Alerts Bar */}
        {alerts.length > 0 && (
          <div className="mt-2 p-2 bg-yellow-50 border border-yellow-200 rounded-md">
            <div className="flex items-center space-x-2">
              <AlertCircle className="w-4 h-4 text-yellow-600 flex-shrink-0" />
              <span className="text-sm text-yellow-800 font-medium">
                {alerts.length} active service alert{alerts.length !== 1 ? 's' : ''}
              </span>
            </div>
          </div>
        )}
      </div>

      {/* Main Map Container */}
      <div className="pt-16 h-full relative">
        {isLoading && vehicles.length === 0 ? (
          // Initial loading state
          <div className="h-full flex items-center justify-center bg-rtd-light">
            <div className="text-center">
              <div className="animate-spin rounded-full h-16 w-16 border-b-2 border-rtd-primary mx-auto mb-4"></div>
              <h2 className="text-xl font-semibold text-rtd-dark mb-2">Loading RTD Transit Data</h2>
              <p className="text-rtd-dark/70">
                Connecting to live vehicle feeds and map services...
              </p>
            </div>
          </div>
        ) : (
          // Map with data
          <OpenStreetMap
            vehicles={filteredVehicles}
            selectedVehicle={selectedVehicle}
            filters={filters}
            onVehicleSelect={handleVehicleSelect}
            onFiltersChange={handleFiltersChange}
          />
        )}

        {/* Vehicle Details Panel */}
        <VehicleDetailsPanel
          vehicle={selectedVehicle}
          onClose={() => setSelectedVehicle(null)}
        />
      </div>

      {/* Footer */}
      <div className="absolute bottom-0 right-0 p-2">
        <div className="bg-white/90 backdrop-blur-sm rounded-lg px-3 py-1 text-xs text-gray-600 shadow-sm">
          <div className="flex items-center space-x-2">
            <CheckCircle className="w-3 h-3 text-green-500" />
            <span>Powered by OpenStreetMap & RTD GTFS-RT • Live transit data</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;