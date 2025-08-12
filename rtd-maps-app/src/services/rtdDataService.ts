// RTD Data Service - Connects to live Java RTD API server

import axios from 'axios';
import { 
  VehiclePosition, 
  TripUpdate, 
  Alert, 
  EnhancedVehicleData, 
  RouteInfo, 
  DataConnectionState,
  KafkaConfig,
  VehicleStatus,
  OccupancyStatus
} from '@/types/rtd';

interface RTDAPIResponse {
  vehicles: Array<{
    vehicle_id: string;
    vehicle_label: string;
    trip_id: string;
    route_id: string;
    latitude: number;
    longitude: number;
    bearing: number;
    speed: number;
    current_status: string;
    occupancy_status: string;
    timestamp_ms: number;
    last_updated: string;
    is_real_time: boolean;
  }>;
  metadata: {
    total_count: number;
    last_update: string;
    source: string;
  };
}

export class RTDDataService {
  private static instance: RTDDataService;
  private vehiclePositions: Map<string, EnhancedVehicleData> = new Map();
  private tripUpdates: Map<string, TripUpdate> = new Map();
  private alerts: Alert[] = [];
  private routes: Map<string, RouteInfo> = new Map();
  private listeners: Array<(vehicles: EnhancedVehicleData[]) => void> = [];
  private connectionState: DataConnectionState = {
    isConnected: false,
    lastUpdate: null,
    vehicleCount: 0,
    error: null
  };

  private updateInterval: ReturnType<typeof setInterval> | null = null;
  private readonly UPDATE_INTERVAL_MS = 30000; // 30 seconds
  private readonly API_BASE_URL = 'http://localhost:8080/api';

  public static getInstance(): RTDDataService {
    if (!RTDDataService.instance) {
      RTDDataService.instance = new RTDDataService();
    }
    return RTDDataService.instance;
  }

  private constructor() {
    this.initializeService();
  }

  private async initializeService(): Promise<void> {
    console.log('🚌 Initializing RTD Data Service - connecting to live Java API...');
    
    // Load route information
    await this.loadRouteInformation();
    
    // Try to connect to the Java API server
    await this.testAPIConnection();
    
    // Start polling for data
    this.startAPIPolling();
  }

  private async testAPIConnection(): Promise<void> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/health`, { timeout: 5000 });
      
      if (response.status === 200) {
        console.log('✅ Connected to RTD Java API server');
        this.connectionState.isConnected = true;
        this.connectionState.error = null;
      }
    } catch (error) {
      console.warn('⚠️ RTD Java API server not available, will retry...', error);
      this.connectionState.isConnected = false;
      this.connectionState.error = 'Java API server not available on port 8080';
    }
  }

  private startAPIPolling(): void {
    console.log('📡 Starting RTD API polling every', this.UPDATE_INTERVAL_MS / 1000, 'seconds');
    
    // Initial fetch
    this.fetchFromAPI();
    
    // Set up interval
    this.updateInterval = setInterval(async () => {
      await this.fetchFromAPI();
    }, this.UPDATE_INTERVAL_MS);
  }

  private async fetchFromAPI(): Promise<void> {
    try {
      const response = await axios.get<RTDAPIResponse>(`${this.API_BASE_URL}/vehicles`, { 
        timeout: 10000 
      });
      
      if (response.data && response.data.vehicles) {
        // Convert API response to our internal format
        const vehicles = response.data.vehicles.map(apiVehicle => ({
          vehicle_id: apiVehicle.vehicle_id,
          trip_id: apiVehicle.trip_id,
          route_id: apiVehicle.route_id,
          latitude: apiVehicle.latitude,
          longitude: apiVehicle.longitude,
          bearing: apiVehicle.bearing,
          speed: apiVehicle.speed,
          current_status: this.mapStatus(apiVehicle.current_status),
          occupancy_status: this.mapOccupancy(apiVehicle.occupancy_status),
          timestamp_ms: apiVehicle.timestamp_ms,
        }));

        this.processVehiclePositions(vehicles);
        
        this.connectionState = {
          isConnected: true,
          lastUpdate: new Date(),
          vehicleCount: response.data.vehicles.length,
          error: null
        };
        
        console.log(`📊 Fetched ${response.data.vehicles.length} live vehicles from RTD`);
      }
      
    } catch (error) {
      console.error('❌ Failed to fetch from RTD API:', error);
      
      if (axios.isAxiosError(error)) {
        if (error.code === 'ECONNREFUSED') {
          this.connectionState.error = 'RTD Java API server not running on port 8080';
        } else if (error.code === 'ETIMEDOUT') {
          this.connectionState.error = 'RTD API server timeout';
        } else {
          this.connectionState.error = `API Error: ${error.message}`;
        }
      } else {
        this.connectionState.error = 'Unknown error fetching RTD data';
      }
      
      this.connectionState.isConnected = false;
    }
  }

  private mapStatus(status: string): VehicleStatus {
    switch (status?.toUpperCase()) {
      case 'IN_TRANSIT_TO':
        return VehicleStatus.IN_TRANSIT_TO;
      case 'STOPPED_AT':
        return VehicleStatus.STOPPED_AT;
      case 'INCOMING_AT':
        return VehicleStatus.INCOMING_AT;
      default:
        return VehicleStatus.IN_TRANSIT_TO;
    }
  }

  private mapOccupancy(occupancy: string): OccupancyStatus {
    switch (occupancy?.toUpperCase()) {
      case 'EMPTY':
        return OccupancyStatus.EMPTY;
      case 'MANY_SEATS_AVAILABLE':
        return OccupancyStatus.MANY_SEATS_AVAILABLE;
      case 'FEW_SEATS_AVAILABLE':
        return OccupancyStatus.FEW_SEATS_AVAILABLE;
      case 'STANDING_ROOM_ONLY':
        return OccupancyStatus.STANDING_ROOM_ONLY;
      case 'CRUSHED_STANDING_ROOM_ONLY':
        return OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY;
      case 'FULL':
        return OccupancyStatus.FULL;
      default:
        return OccupancyStatus.MANY_SEATS_AVAILABLE;
    }
  }

  private processVehiclePositions(vehicles: VehiclePosition[]): void {
    const now = new Date();
    
    vehicles.forEach(vehicle => {
      const enhanced: EnhancedVehicleData = {
        ...vehicle,
        route_info: this.routes.get(vehicle.route_id || ''),
        delay_seconds: this.tripUpdates.get(vehicle.trip_id || '')?.delay_seconds,
        last_updated: now,
        is_real_time: (now.getTime() - vehicle.timestamp_ms) < 300000 // Real-time if < 5 minutes old
      };
      
      this.vehiclePositions.set(vehicle.vehicle_id, enhanced);
    });

    this.notifyListeners();
  }

  private async loadRouteInformation(): Promise<void> {
    // Static route information
    const rtdRoutes: RouteInfo[] = [
      { route_id: 'A', route_short_name: 'A', route_long_name: 'Union Station to Denver Airport', route_type: 0, route_color: '#0066CC' },
      { route_id: 'B', route_short_name: 'B', route_long_name: 'Union Station to Westminster', route_type: 0, route_color: '#00AA44' },
      { route_id: 'C', route_short_name: 'C', route_long_name: 'Union Station to Littleton', route_type: 0, route_color: '#FF6600' },
      { route_id: 'D', route_short_name: 'D', route_long_name: 'Union Station to Ridgegate', route_type: 0, route_color: '#FFD700' },
      { route_id: 'E', route_short_name: 'E', route_long_name: 'Union Station to Lincoln', route_type: 0, route_color: '#800080' },
      { route_id: 'F', route_short_name: 'F', route_long_name: '18th & California to Union Station', route_type: 0, route_color: '#FF69B4' },
      { route_id: 'G', route_short_name: 'G', route_long_name: 'Union Station to Wheat Ridge', route_type: 0, route_color: '#008080' },
      { route_id: 'H', route_short_name: 'H', route_long_name: 'Union Station to Nine Mile', route_type: 0, route_color: '#DC143C' },
      { route_id: 'N', route_short_name: 'N', route_long_name: 'Union Station to Eastlake', route_type: 0, route_color: '#32CD32' },
      { route_id: 'R', route_short_name: 'R', route_long_name: 'Union Station Shuttle', route_type: 0, route_color: '#FF4500' },
      { route_id: 'W', route_short_name: 'W', route_long_name: 'Union Station to Jefferson County', route_type: 0, route_color: '#4B0082' },
      // Common bus routes
      { route_id: '15', route_short_name: '15', route_long_name: 'East Colfax', route_type: 3, route_color: '#666666' },
      { route_id: '16', route_short_name: '16', route_long_name: '16th Street Mall', route_type: 3, route_color: '#666666' },
      { route_id: '20', route_short_name: '20', route_long_name: 'East 20th Avenue', route_type: 3, route_color: '#666666' },
      { route_id: '44', route_short_name: '44', route_long_name: 'West 44th Avenue', route_type: 3, route_color: '#666666' },
      { route_id: '83', route_short_name: '83', route_long_name: '83rd Avenue', route_type: 3, route_color: '#666666' }
    ];

    rtdRoutes.forEach(route => {
      this.routes.set(route.route_id, route);
    });
  }

  private notifyListeners(): void {
    const vehicles = Array.from(this.vehiclePositions.values());
    this.listeners.forEach(listener => {
      try {
        listener(vehicles);
      } catch (error) {
        console.error('❌ Error notifying listener:', error);
      }
    });
  }

  // Public API
  public subscribe(callback: (vehicles: EnhancedVehicleData[]) => void): () => void {
    this.listeners.push(callback);
    
    // Immediately send current data if available
    if (this.vehiclePositions.size > 0) {
      callback(Array.from(this.vehiclePositions.values()));
    }
    
    // Return unsubscribe function
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) {
        this.listeners.splice(index, 1);
      }
    };
  }

  public getConnectionState(): DataConnectionState {
    return { ...this.connectionState };
  }

  public getVehicles(): EnhancedVehicleData[] {
    return Array.from(this.vehiclePositions.values());
  }

  public getAlerts(): Alert[] {
    return [...this.alerts];
  }

  public getRoutes(): RouteInfo[] {
    return Array.from(this.routes.values());
  }

  public async refresh(): Promise<void> {
    await this.fetchFromAPI();
  }

  public destroy(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    this.listeners.length = 0;
    console.log('🛑 RTD Data Service destroyed');
  }
}