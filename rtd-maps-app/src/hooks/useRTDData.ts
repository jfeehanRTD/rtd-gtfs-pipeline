// Custom React hook for managing RTD real-time transit data

import { useState, useEffect, useCallback, useRef } from 'react';
import { EnhancedVehicleData, Alert, DataConnectionState, MapFilters } from '@/types/rtd';
import { RTDDataService } from '@/services/rtdDataService';

interface UseRTDDataResult {
  vehicles: EnhancedVehicleData[];
  alerts: Alert[];
  connectionState: DataConnectionState;
  isLoading: boolean;
  error: string | null;
  refresh: () => Promise<void>;
  filteredVehicles: EnhancedVehicleData[];
  applyFilters: (filters: MapFilters) => void;
}

export const useRTDData = (initialFilters?: Partial<MapFilters>): UseRTDDataResult => {
  const [vehicles, setVehicles] = useState<EnhancedVehicleData[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [connectionState, setConnectionState] = useState<DataConnectionState>({
    isConnected: false,
    lastUpdate: null,
    vehicleCount: 0,
    error: null
  });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState<MapFilters>({
    showBuses: true,
    showTrains: true,
    selectedRoutes: [],
    showDelayedOnly: false,
    minDelaySeconds: 300,
    ...initialFilters
  });
  const [filteredVehicles, setFilteredVehicles] = useState<EnhancedVehicleData[]>([]);

  const dataServiceRef = useRef<RTDDataService | null>(null);
  const unsubscribeRef = useRef<(() => void) | null>(null);

  // Initialize data service
  useEffect(() => {
    console.log('🚌 Initializing RTD data hook...');
    
    dataServiceRef.current = RTDDataService.getInstance();
    
    // Subscribe to vehicle updates
    unsubscribeRef.current = dataServiceRef.current.subscribe((newVehicles) => {
      console.log(`📊 Received ${newVehicles.length} vehicles`);
      setVehicles(newVehicles);
      setIsLoading(false);
      setError(null);
    });

    // Set up connection state polling
    const connectionInterval = setInterval(() => {
      if (dataServiceRef.current) {
        const state = dataServiceRef.current.getConnectionState();
        setConnectionState(state);
        
        if (state.error) {
          setError(state.error);
        }
      }
    }, 1000);

    // Load initial alerts
    const loadAlerts = async () => {
      try {
        if (dataServiceRef.current) {
          const initialAlerts = dataServiceRef.current.getAlerts();
          setAlerts(initialAlerts);
        }
      } catch (err) {
        console.warn('Failed to load initial alerts:', err);
      }
    };
    loadAlerts();

    return () => {
      console.log('🛑 Cleaning up RTD data hook');
      if (unsubscribeRef.current) {
        unsubscribeRef.current();
      }
      clearInterval(connectionInterval);
    };
  }, []);

  // Apply filters to vehicles
  const applyFilters = useCallback((newFilters: MapFilters) => {
    setFilters(newFilters);
  }, []);

  // Filter vehicles based on current filters
  useEffect(() => {
    const filtered = vehicles.filter(vehicle => {
      // Vehicle type filter
      if (!filters.showBuses && vehicle.route_info?.route_type === 3) {
        return false;
      }
      if (!filters.showTrains && vehicle.route_info?.route_type !== 3) {
        return false;
      }

      // Selected routes filter
      if (filters.selectedRoutes.length > 0) {
        if (!filters.selectedRoutes.includes(vehicle.route_id || '')) {
          return false;
        }
      }

      // Delay filter
      if (filters.showDelayedOnly) {
        if (!vehicle.delay_seconds || vehicle.delay_seconds < filters.minDelaySeconds) {
          return false;
        }
      }

      return true;
    });

    setFilteredVehicles(filtered);
  }, [vehicles, filters]);

  // Refresh data manually
  const refresh = useCallback(async () => {
    console.log('🔄 Manual refresh requested');
    setIsLoading(true);
    setError(null);
    
    try {
      if (dataServiceRef.current) {
        await dataServiceRef.current.refresh();
        
        // Update alerts as well
        const updatedAlerts = dataServiceRef.current.getAlerts();
        setAlerts(updatedAlerts);
      }
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to refresh data';
      setError(errorMessage);
      console.error('❌ Refresh failed:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  return {
    vehicles,
    alerts,
    connectionState,
    isLoading,
    error,
    refresh,
    filteredVehicles,
    applyFilters
  };
};

export default useRTDData;