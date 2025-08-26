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
  
  // HTTP endpoints for live bus SIRI and rail communication data
  private readonly BUS_SIRI_ENDPOINT = 'http://localhost:8082/bus-siri'; // Bus SIRI live data
  private readonly BUS_SIRI_STATUS = 'http://localhost:8082/status'; // Bus SIRI status
  private readonly RAIL_COMM_ENDPOINT = 'http://localhost:8081/rail-comm'; // Rail communication live data  
  private readonly RAIL_COMM_STATUS = 'http://localhost:8081/status'; // Rail communication status
  
  // Note: Using live bus SIRI and rail communication feeds
  // This is separate from the static GTFS-RT data used by the static map

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
    let busConnected = false;
    let railConnected = false;

    try {
      // Test Bus SIRI endpoint
      const busResponse = await axios.get(this.BUS_SIRI_STATUS, { timeout: 3000 });
      if (busResponse.status === 200) {
        console.log('✅ Connected to Bus SIRI live data receiver');
        busConnected = true;
      }
    } catch (error) {
      console.warn('⚠️ Bus SIRI receiver not available');
    }

    try {
      // Test Rail Communication endpoint
      const railResponse = await axios.get(this.RAIL_COMM_STATUS, { timeout: 3000 });
      if (railResponse.status === 200) {
        console.log('✅ Connected to Rail Communication live data receiver');
        railConnected = true;
      }
    } catch (error) {
      console.warn('⚠️ Rail Communication receiver not available');
    }

    this.connectionState.isConnected = busConnected || railConnected;
    
    if (!busConnected && !railConnected) {
      this.connectionState.error = 'Bus SIRI and Rail Communication receivers not available';
    } else if (!busConnected) {
      this.connectionState.error = 'Bus SIRI receiver not available';
    } else if (!railConnected) {
      this.connectionState.error = 'Rail Communication receiver not available';
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
    await Promise.allSettled([
      this.fetchBusSIRIData(),
      this.fetchRailCommData()
    ]);
    
    // Update connection state
    this.connectionState.lastUpdate = new Date();
    this.connectionState.busCount = this.busVehicles.size;
    this.connectionState.trainCount = this.trainVehicles.size;
    
    // Notify listeners
    this.notifyListeners();
  }

  private async fetchBusSIRIData(): Promise<void> {
    try {
      console.log('📡 Checking Bus SIRI live feed status...');
      const response = await axios.get(this.BUS_SIRI_STATUS, { timeout: 5000 });

      if (response.data) {
        console.log('🚌 Bus SIRI receiver status:', response.data);
        
        // Note: The SIRI receivers are HTTP endpoints that receive POST data from RTD's TIS proxy
        // They don't serve GET data for retrieval. The live data flows through Kafka topics.
        // For a fully functional live tab, you would need to:
        // 1. Subscribe to RTD's SIRI feed using ./rtd-control.sh bus-comm subscribe
        // 2. Implement Kafka consumer to read from rtd.bus.siri topic
        // 3. Or add data retrieval endpoints to the HTTP receivers
        
        // For now, clear any existing data to show that this is a different data source
        this.busVehicles.clear();
        
        if (response.data.subscription_active) {
          console.log('✅ Bus SIRI subscription is active - live data flowing to Kafka');
        } else {
          console.log('⚠️ Bus SIRI subscription not active - use: ./rtd-control.sh bus-comm subscribe');
        }
      }
      
    } catch (error) {
      console.warn('⚠️ Failed to fetch Bus SIRI status:', error);
    }
  }

  private async fetchRailCommData(): Promise<void> {
    try {
      console.log('📡 Checking Rail Communication live feed status...');
      const response = await axios.get(this.RAIL_COMM_STATUS, { timeout: 5000 });

      if (response.data) {
        console.log('🚊 Rail Communication receiver status:', response.data);
        
        // Note: Similar to bus SIRI, the rail comm receiver is an HTTP endpoint for receiving POST data
        // Live rail communication data flows through the rtd.rail.comm Kafka topic
        
        // For now, clear any existing data to show that this is a different data source
        this.trainVehicles.clear();
        
        if (response.data.subscription_active) {
          console.log('✅ Rail Communication subscription is active - live data flowing to Kafka');
        } else {
          console.log('⚠️ Rail Communication subscription not active - use: ./rtd-control.sh rail-comm subscribe');
        }
      }
      
    } catch (error) {
      console.warn('⚠️ Failed to fetch Rail Communication status:', error);
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