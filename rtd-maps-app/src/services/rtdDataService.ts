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
  private updateIntervalMs = 1000; // Default 1 second for real-time updates
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
      const response = await axios.get(`${this.API_BASE_URL}/health`, { timeout: 3000 });
      
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
    console.log('📡 Starting RTD API polling every', this.updateIntervalMs / 1000, 'second(s)');
    
    // Initial fetch
    this.fetchFromAPI();
    
    // Set up interval
    this.updateInterval = setInterval(async () => {
      await this.fetchFromAPI();
    }, this.updateIntervalMs);
  }

  private async fetchFromAPI(): Promise<void> {
    try {
      const response = await axios.get<RTDAPIResponse>(`${this.API_BASE_URL}/vehicles`, { 
        timeout: 5000  // Reduced timeout for 1-second updates
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
      // Dynamically determine route info from the vehicle data itself
      const routeInfo = vehicle.route_id ? {
        route_id: vehicle.route_id,
        route_short_name: vehicle.route_id,
        route_long_name: `Route ${vehicle.route_id}`,
        // Infer route type from route ID pattern - single letters are typically light rail (0), numbers are buses (3)
        route_type: /^[A-Z]$/.test(vehicle.route_id) ? 0 : 3,
        route_color: /^[A-Z]$/.test(vehicle.route_id) ? '#0066CC' : '#666666'
      } : undefined;

      const enhanced: EnhancedVehicleData = {
        ...vehicle,
        route_info: routeInfo,
        delay_seconds: this.tripUpdates.get(vehicle.trip_id || '')?.delay_seconds,
        last_updated: now,
        is_real_time: (now.getTime() - vehicle.timestamp_ms) < 300000 // Real-time if < 5 minutes old
      };
      
      this.vehiclePositions.set(vehicle.vehicle_id, enhanced);
    });

    this.notifyListeners();
  }

  private async loadRouteInformation(): Promise<void> {
    // No hardcoded route data - use only live GTFS-RT feed data
    // Route information will be dynamically determined from vehicle data
    console.log('📋 Route information will be determined from live GTFS-RT data');
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

  public async setUpdateInterval(intervalSeconds: number): Promise<void> {
    const intervalMs = Math.max(500, intervalSeconds * 1000); // Minimum 0.5 seconds
    
    console.log(`⏱️ Changing update interval from ${this.updateIntervalMs/1000}s to ${intervalMs/1000}s`);
    
    // Update React polling interval
    this.updateIntervalMs = intervalMs;
    
    // Restart polling with new interval
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
    }
    this.startAPIPolling();

    // Update Java pipeline interval
    try {
      await axios.post(`${this.API_BASE_URL}/config/interval`, {
        intervalSeconds: intervalSeconds
      }, { timeout: 3000 });
      console.log(`✅ Java pipeline interval updated to ${intervalSeconds}s`);
    } catch (error) {
      console.warn('⚠️ Failed to update Java pipeline interval:', error);
    }
  }

  public getUpdateInterval(): number {
    return this.updateIntervalMs / 1000;
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