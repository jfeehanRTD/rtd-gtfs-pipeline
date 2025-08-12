// Enhanced Data Query Service - Connects to multiple data sinks
// Supports Kafka, Flink Table API, Direct RTD API, and Database queries

import axios from 'axios';
import { EnhancedVehicleData, VehiclePosition, TripUpdate, Alert, VehicleStatus, OccupancyStatus } from '@/types/rtd';

export interface DataSource {
  id: string;
  name: string;
  type: 'kafka' | 'flink' | 'database' | 'api' | 'websocket';
  endpoint: string;
  status: 'connected' | 'disconnected' | 'error';
  latency: number;
  lastUpdate: Date | null;
  vehicleCount: number;
  description: string;
  capabilities: string[];
}

export interface QueryOptions {
  sources: string[];
  vehicleIds?: string[];
  routeIds?: string[];
  boundingBox?: {
    north: number;
    south: number;
    east: number;
    west: number;
  };
  timeRange?: {
    start: Date;
    end: Date;
  };
  limit?: number;
  realTimeOnly?: boolean;
}

export interface VehicleHistory {
  vehicleId: string;
  positions: Array<{
    timestamp: Date;
    latitude: number;
    longitude: number;
    speed?: number;
    bearing?: number;
    status?: string;
  }>;
}

export class DataQueryService {
  private static instance: DataQueryService;
  private availableSources: Map<string, DataSource> = new Map();
  private activeQueries: Map<string, AbortController> = new Map();
  private websocketConnections: Map<string, WebSocket> = new Map();

  // Configuration for different data sinks
  private readonly DATA_SOURCES: DataSource[] = [
    {
      id: 'kafka-vehicles',
      name: 'Kafka Vehicle Positions',
      type: 'kafka',
      endpoint: 'http://localhost:8080/api/kafka/vehicle-positions',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Real-time vehicle positions from Kafka topic',
      capabilities: ['real-time', 'streaming', 'historical']
    },
    {
      id: 'kafka-trips',
      name: 'Kafka Trip Updates',
      type: 'kafka',
      endpoint: 'http://localhost:8080/api/kafka/trip-updates',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Trip updates and delays from Kafka',
      capabilities: ['real-time', 'streaming']
    },
    {
      id: 'flink-aggregated',
      name: 'Flink Aggregated Data',
      type: 'flink',
      endpoint: 'http://localhost:8081/api/query/aggregated-vehicles',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Processed and aggregated vehicle data from Flink',
      capabilities: ['aggregations', 'analytics', 'windowed-queries']
    },
    {
      id: 'rtd-direct',
      name: 'RTD Direct API',
      type: 'api',
      endpoint: 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Direct connection to RTD GTFS-RT feeds',
      capabilities: ['real-time', 'protobuf']
    },
    {
      id: 'postgres-historical',
      name: 'PostgreSQL Historical',
      type: 'database',
      endpoint: 'http://localhost:8080/api/db/historical',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Historical vehicle data from PostgreSQL',
      capabilities: ['historical', 'analytics', 'geospatial-queries']
    },
    {
      id: 'websocket-stream',
      name: 'WebSocket Stream',
      type: 'websocket',
      endpoint: 'ws://localhost:8080/ws/vehicles',
      status: 'disconnected',
      latency: 0,
      lastUpdate: null,
      vehicleCount: 0,
      description: 'Live WebSocket stream for real-time updates',
      capabilities: ['real-time', 'bi-directional', 'low-latency']
    }
  ];

  public static getInstance(): DataQueryService {
    if (!DataQueryService.instance) {
      DataQueryService.instance = new DataQueryService();
    }
    return DataQueryService.instance;
  }

  private constructor() {
    this.initializeSources();
  }

  private async initializeSources(): Promise<void> {
    console.log('🔍 Initializing data sources...');
    
    // Test connectivity to each source
    for (const source of this.DATA_SOURCES) {
      this.availableSources.set(source.id, { ...source });
      this.testSourceConnection(source.id);
    }
  }

  private async testSourceConnection(sourceId: string): Promise<void> {
    const source = this.availableSources.get(sourceId);
    if (!source) return;

    const startTime = Date.now();

    try {
      if (source.type === 'websocket') {
        await this.testWebSocketConnection(source);
      } else {
        // Test HTTP-based endpoints
        const healthEndpoint = this.getHealthEndpoint(source);
        const response = await axios.get(healthEndpoint, { 
          timeout: 5000,
          validateStatus: (status) => status < 500
        });
        
        source.latency = Date.now() - startTime;
        source.status = response.status === 200 ? 'connected' : 'error';
        source.lastUpdate = new Date();
        
        if (response.data?.vehicleCount !== undefined) {
          source.vehicleCount = response.data.vehicleCount;
        }
      }
    } catch (error) {
      source.status = 'disconnected';
      source.latency = 0;
      console.warn(`⚠️ Source ${source.name} is unavailable:`, error);
    }

    this.availableSources.set(sourceId, source);
  }

  private getHealthEndpoint(source: DataSource): string {
    switch (source.type) {
      case 'kafka':
        return source.endpoint.replace('/vehicle-positions', '/health');
      case 'flink':
        return source.endpoint.replace('/query/aggregated-vehicles', '/health');
      case 'database':
        return source.endpoint.replace('/historical', '/health');
      case 'api':
        // For RTD API, we'll just check if we can reach the endpoint
        return 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb';
      default:
        return source.endpoint;
    }
  }

  private async testWebSocketConnection(source: DataSource): Promise<void> {
    return new Promise((resolve) => {
      const ws = new WebSocket(source.endpoint);
      const timeout = setTimeout(() => {
        ws.close();
        source.status = 'disconnected';
        resolve();
      }, 5000);

      ws.onopen = () => {
        clearTimeout(timeout);
        source.status = 'connected';
        source.lastUpdate = new Date();
        ws.close();
        resolve();
      };

      ws.onerror = () => {
        clearTimeout(timeout);
        source.status = 'error';
        resolve();
      };
    });
  }

  // Query vehicles from selected data sources
  public async queryVehicles(options: QueryOptions): Promise<EnhancedVehicleData[]> {
    const results: EnhancedVehicleData[] = [];
    const queryId = `query-${Date.now()}`;
    const controller = new AbortController();
    this.activeQueries.set(queryId, controller);

    try {
      const queries = options.sources.map(sourceId => 
        this.querySource(sourceId, options, controller.signal)
      );

      const sourceResults = await Promise.allSettled(queries);
      
      sourceResults.forEach((result, index) => {
        if (result.status === 'fulfilled' && result.value) {
          results.push(...result.value);
        } else if (result.status === 'rejected') {
          console.error(`Failed to query source ${options.sources[index]}:`, result.reason);
        }
      });

      // Remove duplicates based on vehicle_id
      const uniqueVehicles = new Map<string, EnhancedVehicleData>();
      results.forEach(vehicle => {
        const existing = uniqueVehicles.get(vehicle.vehicle_id);
        if (!existing || vehicle.timestamp_ms > existing.timestamp_ms) {
          uniqueVehicles.set(vehicle.vehicle_id, vehicle);
        }
      });

      return Array.from(uniqueVehicles.values());
    } finally {
      this.activeQueries.delete(queryId);
    }
  }

  private async querySource(
    sourceId: string, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    const source = this.availableSources.get(sourceId);
    if (!source || source.status !== 'connected') {
      return [];
    }

    switch (source.type) {
      case 'kafka':
        return this.queryKafkaSource(source, options, signal);
      case 'flink':
        return this.queryFlinkSource(source, options, signal);
      case 'database':
        return this.queryDatabaseSource(source, options, signal);
      case 'api':
        return this.queryAPISource(source, options, signal);
      case 'websocket':
        return this.queryWebSocketSource(source, options, signal);
      default:
        return [];
    }
  }

  private async queryKafkaSource(
    source: DataSource, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    try {
      const response = await axios.post(
        source.endpoint,
        {
          vehicleIds: options.vehicleIds,
          routeIds: options.routeIds,
          boundingBox: options.boundingBox,
          limit: options.limit
        },
        { signal }
      );

      return response.data.vehicles || [];
    } catch (error) {
      console.error(`Kafka query failed for ${source.name}:`, error);
      return [];
    }
  }

  private async queryFlinkSource(
    source: DataSource, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    try {
      const params = new URLSearchParams();
      if (options.routeIds?.length) {
        params.append('routes', options.routeIds.join(','));
      }
      if (options.limit) {
        params.append('limit', options.limit.toString());
      }

      const response = await axios.get(
        `${source.endpoint}?${params.toString()}`,
        { signal }
      );

      return response.data.vehicles || [];
    } catch (error) {
      console.error(`Flink query failed for ${source.name}:`, error);
      return [];
    }
  }

  private async queryDatabaseSource(
    source: DataSource, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    try {
      const query = {
        select: ['*'],
        from: 'vehicle_positions',
        where: [] as any[],
        orderBy: 'timestamp_ms DESC',
        limit: options.limit || 1000
      };

      if (options.vehicleIds?.length) {
        query.where.push({ vehicle_id: { in: options.vehicleIds } });
      }
      if (options.routeIds?.length) {
        query.where.push({ route_id: { in: options.routeIds } });
      }
      if (options.boundingBox) {
        query.where.push({
          latitude: { 
            between: [options.boundingBox.south, options.boundingBox.north] 
          },
          longitude: { 
            between: [options.boundingBox.west, options.boundingBox.east] 
          }
        });
      }
      if (options.timeRange) {
        query.where.push({
          timestamp_ms: {
            between: [options.timeRange.start.getTime(), options.timeRange.end.getTime()]
          }
        });
      }

      const response = await axios.post(
        source.endpoint,
        query,
        { signal }
      );

      return response.data.results || [];
    } catch (error) {
      console.error(`Database query failed for ${source.name}:`, error);
      return [];
    }
  }

  private async queryAPISource(
    source: DataSource, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    // For RTD Direct API, we need to parse protobuf
    // This is simplified - in production would use actual protobuf parsing
    try {
      const response = await axios.get(
        `${source.endpoint}/VehiclePosition.pb`,
        { 
          responseType: 'arraybuffer',
          signal 
        }
      );

      // Mock parsing - would use actual protobuf library
      return this.mockParseProtobuf(response.data, options);
    } catch (error) {
      console.error(`API query failed for ${source.name}:`, error);
      return [];
    }
  }

  private async queryWebSocketSource(
    source: DataSource, 
    options: QueryOptions, 
    signal: AbortSignal
  ): Promise<EnhancedVehicleData[]> {
    return new Promise((resolve, reject) => {
      const vehicles: EnhancedVehicleData[] = [];
      const ws = new WebSocket(source.endpoint);
      
      const timeout = setTimeout(() => {
        ws.close();
        resolve(vehicles);
      }, 5000);

      ws.onopen = () => {
        // Send query parameters
        ws.send(JSON.stringify({
          type: 'query',
          ...options
        }));
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          if (data.type === 'vehicles') {
            vehicles.push(...data.vehicles);
          } else if (data.type === 'end') {
            clearTimeout(timeout);
            ws.close();
            resolve(vehicles);
          }
        } catch (error) {
          console.error('WebSocket message parse error:', error);
        }
      };

      ws.onerror = (error) => {
        clearTimeout(timeout);
        reject(error);
      };

      signal.addEventListener('abort', () => {
        clearTimeout(timeout);
        ws.close();
        resolve(vehicles);
      });
    });
  }

  private mockParseProtobuf(data: ArrayBuffer, options: QueryOptions): EnhancedVehicleData[] {
    // This is a mock implementation
    // In production, would use actual protobuf parsing
    const vehicles: EnhancedVehicleData[] = [];
    const mockCount = 50;

    for (let i = 0; i < mockCount; i++) {
      const vehicle: EnhancedVehicleData = {
        vehicle_id: `RTD_${i.toString().padStart(4, '0')}`,
        trip_id: `TRIP_${i}`,
        route_id: ['A', 'B', 'C', 'D', 'E'][i % 5],
        latitude: 39.7392 + (Math.random() - 0.5) * 0.3,
        longitude: -104.9903 + (Math.random() - 0.5) * 0.3,
        bearing: Math.random() * 360,
        speed: Math.random() * 20 + 5,
        current_status: VehicleStatus.IN_TRANSIT_TO,
        occupancy_status: OccupancyStatus.MANY_SEATS_AVAILABLE,
        timestamp_ms: Date.now() - Math.random() * 30000,
        last_updated: new Date(),
        is_real_time: true
      };

      // Apply filters
      if (options.vehicleIds && !options.vehicleIds.includes(vehicle.vehicle_id)) {
        continue;
      }
      if (options.routeIds && !options.routeIds.includes(vehicle.route_id!)) {
        continue;
      }

      vehicles.push(vehicle);
    }

    return vehicles;
  }

  // Get vehicle history for tracking
  public async getVehicleHistory(
    vehicleId: string, 
    timeRange?: { start: Date; end: Date }
  ): Promise<VehicleHistory> {
    const source = this.availableSources.get('postgres-historical');
    if (!source || source.status !== 'connected') {
      // Return mock data if database not available
      return this.getMockVehicleHistory(vehicleId);
    }

    try {
      const response = await axios.get(
        `${source.endpoint}/vehicle/${vehicleId}/history`,
        {
          params: {
            start: timeRange?.start.toISOString(),
            end: timeRange?.end.toISOString()
          }
        }
      );

      return response.data;
    } catch (error) {
      console.error('Failed to get vehicle history:', error);
      return this.getMockVehicleHistory(vehicleId);
    }
  }

  private getMockVehicleHistory(vehicleId: string): VehicleHistory {
    const positions = [];
    const now = Date.now();
    const pointCount = 20;

    let lat = 39.7392;
    let lng = -104.9903;

    for (let i = 0; i < pointCount; i++) {
      lat += (Math.random() - 0.5) * 0.01;
      lng += (Math.random() - 0.5) * 0.01;
      
      positions.push({
        timestamp: new Date(now - (pointCount - i) * 60000),
        latitude: lat,
        longitude: lng,
        speed: Math.random() * 20 + 5,
        bearing: Math.random() * 360,
        status: i === pointCount - 1 ? 'IN_TRANSIT_TO' : 'STOPPED_AT'
      });
    }

    return { vehicleId, positions };
  }

  // Subscribe to real-time updates for specific vehicles
  public subscribeToVehicle(
    vehicleId: string, 
    callback: (vehicle: EnhancedVehicleData) => void
  ): () => void {
    const wsSource = this.availableSources.get('websocket-stream');
    if (!wsSource || wsSource.type !== 'websocket') {
      return () => {};
    }

    const ws = new WebSocket(wsSource.endpoint);
    this.websocketConnections.set(vehicleId, ws);

    ws.onopen = () => {
      ws.send(JSON.stringify({
        type: 'subscribe',
        vehicleId
      }));
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'vehicle-update' && data.vehicle) {
          callback(data.vehicle);
        }
      } catch (error) {
        console.error('WebSocket message error:', error);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    // Return unsubscribe function
    return () => {
      ws.close();
      this.websocketConnections.delete(vehicleId);
    };
  }

  // Get available data sources and their status
  public getAvailableSources(): DataSource[] {
    return Array.from(this.availableSources.values());
  }

  // Refresh source status
  public async refreshSourceStatus(): Promise<void> {
    const promises = Array.from(this.availableSources.keys()).map(sourceId => 
      this.testSourceConnection(sourceId)
    );
    await Promise.allSettled(promises);
  }

  // Cancel active queries
  public cancelActiveQueries(): void {
    this.activeQueries.forEach(controller => controller.abort());
    this.activeQueries.clear();
  }

  // Cleanup
  public destroy(): void {
    this.cancelActiveQueries();
    this.websocketConnections.forEach(ws => ws.close());
    this.websocketConnections.clear();
    console.log('🛑 Data Query Service destroyed');
  }
}

export default DataQueryService;