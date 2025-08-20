// Live Transit Data Service - Connects to SIRI and Rail Communication feeds
// This service consumes data from the Kafka topics through HTTP endpoints

import axios from 'axios';

export interface SIRIVehicleData {
  timestamp_ms: number;
  vehicle_id: string;
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
  
  // HTTP endpoints that serve data from the Flink pipelines
  private readonly SIRI_ENDPOINT = 'http://localhost:8082/bus-siri/latest'; // Bus SIRI data
  private readonly RAIL_ENDPOINT = 'http://localhost:8081/rail-comm/latest'; // Rail Communication data
  
  // Fallback to reading from Kafka topics directly if HTTP endpoints not available
  private readonly KAFKA_PROXY_BASE = 'http://localhost:8090/api'; // Kafka consumer proxy

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
    console.log('🚍🚊 Initializing Live Transit Service - SIRI Bus + Rail Communication...');
    
    await this.testConnections();
    this.startDataPolling();
  }

  private async testConnections(): Promise<void> {
    let siriConnected = false;
    let railConnected = false;

    try {
      // Test SIRI Bus endpoint
      const siriResponse = await axios.get(`${this.SIRI_ENDPOINT}`, { timeout: 3000 });
      if (siriResponse.status === 200) {
        console.log('✅ Connected to SIRI Bus data endpoint');
        siriConnected = true;
      }
    } catch (error) {
      console.warn('⚠️ SIRI Bus endpoint not available, will try Kafka proxy...');
    }

    try {
      // Test Rail Communication endpoint
      const railResponse = await axios.get(`${this.RAIL_ENDPOINT}`, { timeout: 3000 });
      if (railResponse.status === 200) {
        console.log('✅ Connected to Rail Communication data endpoint');
        railConnected = true;
      }
    } catch (error) {
      console.warn('⚠️ Rail Communication endpoint not available, will try Kafka proxy...');
    }

    this.connectionState.isConnected = siriConnected || railConnected;
    
    if (!this.connectionState.isConnected) {
      this.connectionState.error = 'Neither SIRI nor Rail Communication endpoints available';
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
    const promises = [
      this.fetchSIRIData(),
      this.fetchRailData()
    ];

    await Promise.allSettled(promises);
    
    // Update connection state
    this.connectionState.lastUpdate = new Date();
    this.connectionState.busCount = this.busVehicles.size;
    this.connectionState.trainCount = this.trainVehicles.size;
    
    // Notify listeners
    this.notifyListeners();
  }

  private async fetchSIRIData(): Promise<void> {
    try {
      // Try primary endpoint first
      let response;
      try {
        response = await axios.get(this.SIRI_ENDPOINT, { timeout: 3000 });
      } catch (error) {
        // Fallback to Kafka proxy
        response = await axios.get(`${this.KAFKA_PROXY_BASE}/siri/latest`, { timeout: 3000 });
      }

      if (response.data) {
        // Handle both single object and array responses
        const busData = Array.isArray(response.data) ? response.data : [response.data];
        
        busData.forEach((bus: any) => {
          if (bus && bus.vehicle_id) {
            const siriVehicle: SIRIVehicleData = {
              timestamp_ms: bus.timestamp_ms || Date.now(),
              vehicle_id: bus.vehicle_id,
              route_id: bus.route_id || 'UNKNOWN',
              direction: bus.direction || 'UNKNOWN',
              latitude: parseFloat(bus.latitude) || 0,
              longitude: parseFloat(bus.longitude) || 0,
              speed_mph: parseFloat(bus.speed_mph) || 0,
              status: bus.status || 'UNKNOWN',
              next_stop: bus.next_stop || '',
              delay_seconds: parseInt(bus.delay_seconds) || 0,
              occupancy: bus.occupancy || 'UNKNOWN',
              block_id: bus.block_id || '',
              trip_id: bus.trip_id || '',
              last_updated: new Date(),
              is_real_time: (Date.now() - (bus.timestamp_ms || 0)) < 300000 // Real-time if < 5 minutes
            };

            this.busVehicles.set(bus.vehicle_id, siriVehicle);
          }
        });

        console.log(`🚌 Fetched ${busData.length} SIRI bus vehicles`);
      }
      
    } catch (error) {
      console.error('❌ Failed to fetch SIRI data:', error);
    }
  }

  private async fetchRailData(): Promise<void> {
    try {
      // Try primary endpoint first
      let response;
      try {
        response = await axios.get(this.RAIL_ENDPOINT, { timeout: 3000 });
      } catch (error) {
        // Fallback to Kafka proxy
        response = await axios.get(`${this.KAFKA_PROXY_BASE}/rail/latest`, { timeout: 3000 });
      }

      if (response.data) {
        // Handle both single object and array responses
        const railData = Array.isArray(response.data) ? response.data : [response.data];
        
        railData.forEach((train: any) => {
          if (train && train.train_id) {
            const railVehicle: RailCommData = {
              timestamp_ms: train.timestamp_ms || Date.now(),
              train_id: train.train_id,
              line_id: train.line_id || 'UNKNOWN',
              direction: train.direction || 'UNKNOWN',
              latitude: parseFloat(train.latitude) || 0,
              longitude: parseFloat(train.longitude) || 0,
              speed_mph: parseFloat(train.speed_mph) || 0,
              status: train.status || 'UNKNOWN',
              next_station: train.next_station || '',
              delay_seconds: parseInt(train.delay_seconds) || 0,
              operator_message: train.operator_message || '',
              last_updated: new Date(),
              is_real_time: (Date.now() - (train.timestamp_ms || 0)) < 300000 // Real-time if < 5 minutes
            };

            this.trainVehicles.set(train.train_id, railVehicle);
          }
        });

        console.log(`🚊 Fetched ${railData.length} rail communication vehicles`);
      }
      
    } catch (error) {
      console.error('❌ Failed to fetch Rail Communication data:', error);
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