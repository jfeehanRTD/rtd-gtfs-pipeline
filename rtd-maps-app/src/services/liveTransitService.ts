// Live Transit Data Service - Connects to SIRI and Rail Communication feeds
// This service consumes data from the Kafka topics through HTTP endpoints

import axios from 'axios';

export interface SIRIVehicleData {
  timestamp_ms: number;
  vehicle_id: string;
  vehicle_label: string;
  route_id: string;
  direction: string;
  latitude: number;
  longitude: number;
  speed_mph: number;
  status: string;
  next_stop: string;
  delay_seconds: number;
  occupancy: string;
  block_id: string;
  trip_id: string;
  last_updated: Date;
  is_real_time: boolean;
}

export interface RailCommData {
  timestamp_ms: number;
  train_id: string;
  line_id: string;
  direction: string;
  latitude: number;
  longitude: number;
  speed_mph: number;
  status: string;
  next_station: string;
  delay_seconds: number;
  operator_message: string;
  last_updated: Date;
  is_real_time: boolean;
}

export interface LiveTransitState {
  isConnected: boolean;
  lastUpdate: Date | null;
  busCount: number;
  trainCount: number;
  error: string | null;
}

export class LiveTransitService {
  private static instance: LiveTransitService;
  private busVehicles: Map<string, SIRIVehicleData> = new Map();
  private trainVehicles: Map<string, RailCommData> = new Map();
  private listeners: Array<(data: { buses: SIRIVehicleData[], trains: RailCommData[] }) => void> = [];
  private connectionState: LiveTransitState = {
    isConnected: false,
    lastUpdate: null,
    busCount: 0,
    trainCount: 0,
    error: null
  };

  private updateInterval: ReturnType<typeof setInterval> | null = null;
  private updateIntervalMs = 5000; // 5 second updates for live data
  
  // HTTP endpoints that serve live RTD data
  private readonly RTD_VEHICLES_ENDPOINT = 'http://localhost:8080/api/vehicles'; // Real RTD GTFS-RT vehicle data
  private readonly RTD_HEALTH_ENDPOINT = 'http://localhost:8080/api/health'; // Health check
  
  // Note: Using real RTD GTFS-RT data instead of test data
  // This connects to the RTDStaticDataPipeline which fetches from https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb

  public static getInstance(): LiveTransitService {
    if (!LiveTransitService.instance) {
      LiveTransitService.instance = new LiveTransitService();
    }
    return LiveTransitService.instance;
  }

  private constructor() {
    this.initializeService();
  }

  private async initializeService(): Promise<void> {
    console.log('🚍🚊 Initializing Live Transit Service - RTD GTFS-RT Live Data...');
    console.log('📡 Connecting to real RTD vehicle positions from rtd-denver.com');
    
    await this.testConnections();
    this.startDataPolling();
  }

  private async testConnections(): Promise<void> {
    let rtdConnected = false;

    try {
      // Test RTD live data endpoint
      const rtdResponse = await axios.get(this.RTD_HEALTH_ENDPOINT, { timeout: 3000 });
      if (rtdResponse.status === 200) {
        console.log('✅ Connected to RTD live data pipeline');
        rtdConnected = true;
      }
    } catch (error) {
      console.warn('⚠️ RTD live data pipeline not available');
    }

    this.connectionState.isConnected = rtdConnected;
    
    if (!this.connectionState.isConnected) {
      this.connectionState.error = 'RTD live data pipeline not available';
    } else {
      this.connectionState.error = null;
    }
  }

  private startDataPolling(): void {
    console.log('📡 Starting Live Transit polling every', this.updateIntervalMs / 1000, 'second(s)');
    
    // Initial fetch
    this.fetchLiveData();
    
    // Set up interval
    this.updateInterval = setInterval(async () => {
      await this.fetchLiveData();
    }, this.updateIntervalMs);
  }

  private async fetchLiveData(): Promise<void> {
    await this.fetchRTDVehicleData();
    
    // Update connection state
    this.connectionState.lastUpdate = new Date();
    this.connectionState.busCount = this.busVehicles.size;
    this.connectionState.trainCount = this.trainVehicles.size;
    
    // Notify listeners
    this.notifyListeners();
  }

  private async fetchRTDVehicleData(): Promise<void> {
    try {
      console.log('📡 Fetching live RTD vehicle data...');
      const response = await axios.get(this.RTD_VEHICLES_ENDPOINT, { timeout: 10000 });

      if (response.data && response.data.vehicles) {
        const vehicles = response.data.vehicles;
        
        // Clear old data
        this.busVehicles.clear();
        this.trainVehicles.clear();
        
        vehicles.forEach((vehicle: any) => {
          if (vehicle && vehicle.vehicle_id && vehicle.latitude && vehicle.longitude) {
            // Determine if this is a bus or train based on route_id patterns
            const routeId = vehicle.route_id || 'UNKNOWN';
            const isLightRail = this.isLightRailRoute(routeId);
            
            if (isLightRail) {
              // Treat as train/light rail
              const railVehicle: RailCommData = {
                timestamp_ms: vehicle.timestamp_ms || Date.now(),
                train_id: vehicle.vehicle_id,
                line_id: routeId,
                direction: this.getDirectionFromBearing(vehicle.bearing) || 'UNKNOWN',
                latitude: parseFloat(vehicle.latitude) || 0,
                longitude: parseFloat(vehicle.longitude) || 0,
                speed_mph: parseFloat(vehicle.speed) || 0,
                status: this.mapVehicleStatus(vehicle.current_status) || 'UNKNOWN',
                next_station: '',
                delay_seconds: 0,
                operator_message: '',
                last_updated: new Date(),
                is_real_time: true
              };
              this.trainVehicles.set(vehicle.vehicle_id, railVehicle);
            } else {
              // Treat as bus
              const busVehicle: SIRIVehicleData = {
                timestamp_ms: vehicle.timestamp_ms || Date.now(),
                vehicle_id: vehicle.vehicle_id,
                vehicle_label: vehicle.vehicle_label || vehicle.vehicle_id,
                route_id: routeId,
                direction: this.getDirectionFromBearing(vehicle.bearing) || 'UNKNOWN',
                latitude: parseFloat(vehicle.latitude) || 0,
                longitude: parseFloat(vehicle.longitude) || 0,
                speed_mph: parseFloat(vehicle.speed) || 0,
                status: this.mapVehicleStatus(vehicle.current_status) || 'IN_TRANSIT',
                next_stop: '',
                delay_seconds: 0,
                occupancy: this.mapOccupancyStatus(vehicle.occupancy_status) || 'UNKNOWN',
                block_id: '',
                trip_id: vehicle.trip_id || '',
                last_updated: new Date(),
                is_real_time: true
              };
              this.busVehicles.set(vehicle.vehicle_id, busVehicle);
            }
          }
        });

        console.log(`🚌 Processed ${this.busVehicles.size} buses and 🚊 ${this.trainVehicles.size} light rail vehicles from RTD live data`);
      }
      
    } catch (error) {
      console.error('❌ Failed to fetch RTD vehicle data:', error);
      this.connectionState.error = 'Failed to fetch RTD vehicle data';
    }
  }

  private isLightRailRoute(routeId: string): boolean {
    // RTD Light Rail lines: A, B, C, D, E, F, G, H, N, R, W
    const lightRailLines = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'N', 'R', 'W'];
    return lightRailLines.includes(routeId.toUpperCase());
  }

  private getDirectionFromBearing(bearing: number): string {
    if (bearing == null) return 'UNKNOWN';
    
    // Convert bearing to cardinal directions
    if (bearing >= 315 || bearing < 45) return 'NORTHBOUND';
    if (bearing >= 45 && bearing < 135) return 'EASTBOUND';
    if (bearing >= 135 && bearing < 225) return 'SOUTHBOUND';
    if (bearing >= 225 && bearing < 315) return 'WESTBOUND';
    
    return 'UNKNOWN';
  }

  private mapVehicleStatus(status: string): string {
    if (!status) return 'IN_TRANSIT';
    
    // Map GTFS-RT vehicle status to display status
    switch (status.toUpperCase()) {
      case 'INCOMING_AT': return 'APPROACHING';
      case 'STOPPED_AT': return 'AT_STOP';
      case 'IN_TRANSIT_TO': return 'IN_TRANSIT';
      default: return 'IN_TRANSIT';
    }
  }

  private mapOccupancyStatus(occupancy: string): string {
    if (!occupancy) return 'UNKNOWN';
    
    // Map GTFS-RT occupancy status to display status
    switch (occupancy.toUpperCase()) {
      case 'EMPTY': return 'EMPTY';
      case 'MANY_SEATS_AVAILABLE': return 'MANY_SEATS_AVAILABLE';
      case 'FEW_SEATS_AVAILABLE': return 'FEW_SEATS_AVAILABLE';
      case 'STANDING_ROOM_ONLY': return 'STANDING_ROOM_ONLY';
      case 'CRUSHED_STANDING_ROOM_ONLY': return 'CRUSHED_STANDING_ROOM_ONLY';
      case 'FULL': return 'FULL';
      default: return 'UNKNOWN';
    }
  }

  private notifyListeners(): void {
    const data = {
      buses: Array.from(this.busVehicles.values()),
      trains: Array.from(this.trainVehicles.values())
    };

    this.listeners.forEach(listener => {
      try {
        listener(data);
      } catch (error) {
        console.error('❌ Error notifying listener:', error);
      }
    });
  }

  // Public API
  public subscribe(callback: (data: { buses: SIRIVehicleData[], trains: RailCommData[] }) => void): () => void {
    this.listeners.push(callback);
    
    // Immediately send current data if available
    if (this.busVehicles.size > 0 || this.trainVehicles.size > 0) {
      callback({
        buses: Array.from(this.busVehicles.values()),
        trains: Array.from(this.trainVehicles.values())
      });
    }
    
    // Return unsubscribe function
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) {
        this.listeners.splice(index, 1);
      }
    };
  }

  public getConnectionState(): LiveTransitState {
    return { ...this.connectionState };
  }

  public getBuses(): SIRIVehicleData[] {
    return Array.from(this.busVehicles.values());
  }

  public getTrains(): RailCommData[] {
    return Array.from(this.trainVehicles.values());
  }

  public async refresh(): Promise<void> {
    await this.fetchLiveData();
  }

  public setUpdateInterval(intervalSeconds: number): void {
    const intervalMs = Math.max(1000, intervalSeconds * 1000); // Minimum 1 second
    
    console.log(`⏱️ Changing live transit update interval from ${this.updateIntervalMs/1000}s to ${intervalMs/1000}s`);
    
    this.updateIntervalMs = intervalMs;
    
    // Restart polling with new interval
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
    }
    this.startDataPolling();
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
    console.log('🛑 Live Transit Service destroyed');
  }
}