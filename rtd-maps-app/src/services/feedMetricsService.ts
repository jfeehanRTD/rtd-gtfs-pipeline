// Feed Metrics Service - Real-time metrics for SIRI, LRGPS, and RailComm feeds
import axios from 'axios';

export interface FeedIntervalMetrics {
  messages: number;
  connections: number;
  errors: number;
  interval_seconds: number;
}

export interface FeedTotalMetrics {
  messages: number;
  connections: number;
  errors: number;
  uptime_seconds: number;
  avg_messages_per_min: number;
  avg_connections_per_min: number;
}

export interface FeedRates {
  connection_rate_percent: number;
  error_rate_percent: number;
}

export interface FeedMetrics {
  feed_name: string;
  health_status: 'waiting' | 'healthy' | 'warning' | 'error';
  last_message: string | null;
  last_5s: FeedIntervalMetrics;
  totals: FeedTotalMetrics;
  rates?: FeedRates;
}

export interface AllFeedMetricsResponse {
  feeds: {
    siri: FeedMetrics;
    lrgps: FeedMetrics;
    railcomm: FeedMetrics;
  };
  timestamp: string;
  uptime_ms: number;
}

export class FeedMetricsService {
  private static instance: FeedMetricsService;
  private readonly API_BASE_URL = 'http://localhost:8080/api/metrics';
  private listeners: Array<(metrics: AllFeedMetricsResponse) => void> = [];
  private updateInterval: ReturnType<typeof setInterval> | null = null;
  private readonly UPDATE_INTERVAL_MS = 5000; // 5 seconds
  private isRunning = false;

  public static getInstance(): FeedMetricsService {
    if (!FeedMetricsService.instance) {
      FeedMetricsService.instance = new FeedMetricsService();
    }
    return FeedMetricsService.instance;
  }

  private constructor() {
    console.log('📊 Initializing Feed Metrics Service...');
  }

  public start(): void {
    if (this.isRunning) {
      return;
    }

    console.log('🚀 Starting feed metrics polling every 5 seconds...');
    this.isRunning = true;

    // Initial fetch
    this.fetchAllMetrics();

    // Set up interval
    this.updateInterval = setInterval(() => {
      this.fetchAllMetrics();
    }, this.UPDATE_INTERVAL_MS);
  }

  public stop(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
    this.isRunning = false;
    console.log('🛑 Feed metrics polling stopped');
  }

  private async fetchAllMetrics(): Promise<void> {
    try {
      const response = await axios.get<AllFeedMetricsResponse>(`${this.API_BASE_URL}/all`, {
        timeout: 3000
      });

      if (response.data) {
        this.notifyListeners(response.data);
        console.log(`📊 Fetched metrics for ${Object.keys(response.data.feeds).length} feeds`);
      }
    } catch (error) {
      console.error('❌ Failed to fetch feed metrics:', error);
      
      // Send mock data on error to keep UI responsive
      const mockData: AllFeedMetricsResponse = {
        feeds: {
          siri: this.createMockMetrics('SIRI Bus Feed', 'error'),
          lrgps: this.createMockMetrics('LRGPS Light Rail Feed', 'error'), 
          railcomm: this.createMockMetrics('RailComm SCADA Feed', 'error')
        },
        timestamp: new Date().toISOString(),
        uptime_ms: 0
      };
      
      this.notifyListeners(mockData);
    }
  }

  private createMockMetrics(feedName: string, status: 'waiting' | 'healthy' | 'warning' | 'error'): FeedMetrics {
    return {
      feed_name: feedName,
      health_status: status,
      last_message: null,
      last_5s: {
        messages: 0,
        connections: 0,
        errors: 0,
        interval_seconds: 5
      },
      totals: {
        messages: 0,
        connections: 0,
        errors: 0,
        uptime_seconds: 0,
        avg_messages_per_min: 0,
        avg_connections_per_min: 0
      }
    };
  }

  public async fetchSingleFeedMetrics(feedType: 'siri' | 'lrgps' | 'railcomm'): Promise<FeedMetrics | null> {
    try {
      const response = await axios.get<FeedMetrics>(`${this.API_BASE_URL}/${feedType}`, {
        timeout: 3000
      });
      return response.data;
    } catch (error) {
      console.error(`❌ Failed to fetch ${feedType} metrics:`, error);
      return null;
    }
  }

  public async resetFeedMetrics(feedType: 'siri' | 'lrgps' | 'railcomm'): Promise<boolean> {
    try {
      await axios.post(`${this.API_BASE_URL}/${feedType}/reset`, {}, {
        timeout: 3000
      });
      console.log(`✅ Reset metrics for ${feedType} feed`);
      return true;
    } catch (error) {
      console.error(`❌ Failed to reset ${feedType} metrics:`, error);
      return false;
    }
  }

  public subscribe(callback: (metrics: AllFeedMetricsResponse) => void): () => void {
    this.listeners.push(callback);
    
    // Start polling if this is the first subscriber
    if (this.listeners.length === 1) {
      this.start();
    }
    
    // Return unsubscribe function
    return () => {
      const index = this.listeners.indexOf(callback);
      if (index > -1) {
        this.listeners.splice(index, 1);
      }
      
      // Stop polling if no more subscribers
      if (this.listeners.length === 0) {
        this.stop();
      }
    };
  }

  private notifyListeners(metrics: AllFeedMetricsResponse): void {
    this.listeners.forEach(listener => {
      try {
        listener(metrics);
      } catch (error) {
        console.error('❌ Error notifying metrics listener:', error);
      }
    });
  }

  public getHealthIcon(status: string): string {
    switch (status) {
      case 'healthy': return '✅';
      case 'warning': return '⚠️';
      case 'error': return '❌';
      case 'waiting': return '⏳';
      default: return '❓';
    }
  }

  public getHealthColor(status: string): string {
    switch (status) {
      case 'healthy': return 'text-green-600';
      case 'warning': return 'text-yellow-600';
      case 'error': return 'text-red-600';
      case 'waiting': return 'text-gray-600';
      default: return 'text-gray-400';
    }
  }

  public formatUptime(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    return `${hours}h ${minutes}m`;
  }

  public destroy(): void {
    this.stop();
    this.listeners.length = 0;
    console.log('🛑 Feed Metrics Service destroyed');
  }
}