// RTD Live Transit Map - Enhanced Application with Data Query Tools

import { useState, useCallback } from 'react';
import OpenStreetMap from './components/OpenStreetMap';
import VehicleDetailsPanel from './components/VehicleDetailsPanel';
import VehicleSelector from './components/VehicleSelector';
import DataSourcePanel from './components/DataSourcePanel';
import VehicleTracker from './components/VehicleTracker';
import { useRTDData } from './hooks/useRTDData';
import { EnhancedVehicleData, MapFilters } from './types/rtd';
import { VehicleHistory } from './services/dataQueryService';
import { 
  Wifi, 
  WifiOff, 
  RefreshCw, 
  AlertCircle, 
  CheckCircle,
  Clock,
  MapPin,
  Layers,
  Target,
  Navigation,
  Menu,
  X
} from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

function App() {
  const [selectedVehicle, setSelectedVehicle] = useState<EnhancedVehicleData | null>(null);
  const [selectedVehicles, setSelectedVehicles] = useState<Set<string>>(new Set());
  const [selectedSources, setSelectedSources] = useState<Set<string>>(new Set(['kafka-vehicles', 'rtd-direct']));
  const [vehicleHistories, setVehicleHistories] = useState<Map<string, VehicleHistory>>(new Map());
  const [showVehicleSelector, setShowVehicleSelector] = useState(false);
  const [showDataSources, setShowDataSources] = useState(false);
  const [showTracker, setShowTracker] = useState(false);
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

  // Handle vehicle selection for details
  const handleVehicleSelect = useCallback((vehicle: EnhancedVehicleData | null) => {
    setSelectedVehicle(vehicle);
  }, []);

  // Handle vehicle selection for tracking
  const handleVehicleToggle = useCallback((vehicleId: string) => {
    setSelectedVehicles(prev => {
      const next = new Set(prev);
      if (next.has(vehicleId)) {
        next.delete(vehicleId);
      } else {
        next.add(vehicleId);
      }
      return next;
    });
  }, []);

  const handleSelectAllVehicles = useCallback(() => {
    setSelectedVehicles(new Set(filteredVehicles.map(v => v.vehicle_id)));
  }, [filteredVehicles]);

  const handleClearAllVehicles = useCallback(() => {
    setSelectedVehicles(new Set());
  }, []);

  // Handle data source selection
  const handleSourceToggle = useCallback((sourceId: string) => {
    setSelectedSources(prev => {
      const next = new Set(prev);
      if (next.has(sourceId)) {
        next.delete(sourceId);
      } else {
        next.add(sourceId);
      }
      return next;
    });
  }, []);

  // Handle vehicle tracking updates
  const handleVehicleUpdate = useCallback((vehicles: EnhancedVehicleData[]) => {
    // Update could trigger map refresh or other UI updates
    // console.log(`Received ${vehicles.length} vehicle updates`);
  }, []);

  const handleHistoryUpdate = useCallback((vehicleId: string, history: VehicleHistory) => {
    setVehicleHistories(prev => new Map(prev).set(vehicleId, history));
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

          {/* Right: Actions and Tools */}
          <div className="flex items-center space-x-4">
            <div className="text-sm text-gray-600">
              <span className="font-medium">{filteredVehicles.length}</span>
              <span className="text-gray-500"> / {vehicles.length} vehicles</span>
              {selectedVehicles.size > 0 && (
                <span className="ml-2 text-rtd-primary font-medium">
                  ({selectedVehicles.size} selected)
                </span>
              )}
            </div>

            {/* Tool Buttons */}
            <div className="flex items-center space-x-1">
              <button
                onClick={() => setShowDataSources(!showDataSources)}
                className={`p-2 rounded-md transition-colors ${
                  showDataSources ? 'bg-rtd-primary text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
                title="Data Sources"
              >
                <Layers className="w-4 h-4" />
              </button>
              
              <button
                onClick={() => setShowVehicleSelector(!showVehicleSelector)}
                className={`p-2 rounded-md transition-colors ${
                  showVehicleSelector ? 'bg-rtd-primary text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
                title="Vehicle Selector"
              >
                <Target className="w-4 h-4" />
              </button>
              
              <button
                onClick={() => setShowTracker(!showTracker)}
                className={`p-2 rounded-md transition-colors ${
                  showTracker ? 'bg-rtd-primary text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
                title="Vehicle Tracker"
              >
                <Navigation className="w-4 h-4" />
              </button>
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

        {/* Side Panels */}
        <div className="absolute top-0 left-0 bottom-0 flex z-40 pointer-events-none">
          <div className="flex space-x-4 p-4 pointer-events-auto">
            {/* Data Sources Panel */}
            {showDataSources && (
              <div className="w-80 h-fit max-h-full">
                <DataSourcePanel
                  selectedSources={selectedSources}
                  onSourceToggle={handleSourceToggle}
                  onRefresh={handleRefresh}
                />
              </div>
            )}
            
            {/* Vehicle Selector Panel */}
            {showVehicleSelector && (
              <div className="w-96 h-fit max-h-full">
                <VehicleSelector
                  vehicles={filteredVehicles}
                  selectedVehicles={selectedVehicles}
                  onVehicleToggle={handleVehicleToggle}
                  onSelectAll={handleSelectAllVehicles}
                  onClearAll={handleClearAllVehicles}
                  onClose={() => setShowVehicleSelector(false)}
                />
              </div>
            )}
            
            {/* Vehicle Tracker Panel */}
            {showTracker && (
              <div className="w-80 h-fit">
                <VehicleTracker
                  selectedVehicles={selectedVehicles}
                  onVehicleUpdate={handleVehicleUpdate}
                  onHistoryUpdate={handleHistoryUpdate}
                />
              </div>
            )}
          </div>
        </div>

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
            <span>Powered by OpenStreetMap & RTD GTFS-RT • Enhanced with Multi-Source Data Query</span>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;