// RTD Occupancy Analysis Service - Connects to live occupancy analysis pipeline
import axios from 'axios';

export interface OccupancyAccuracyMetric {
  id: string;
  category: 'overall' | 'by_date' | 'by_route';
  subcategory: string;
  totalVPRecords: number;
  totalJoinedRecords: number;
  matchedOccupancyRecords: number;
  joinedPercentage: number;
  accuracyPercentage: number;
  lastUpdated: Date;
}

export interface OccupancyDistribution {
  feedType: 'GTFS-RT' | 'APC';
  empty: number;
  manySeatsAvailable: number;
  fewSeatsAvailable: number;
  standingRoomOnly: number;
  crushedStandingRoomOnly: number;
  full: number;
  totalRecords: number;
}

export interface VehicleTypeAnalysis {
  vehicleType: string;
  maxSeats: number;
  maxStands: number;
  totalCapacity: number;
  recordCount: number;
  averageOccupancy: number;
}

export interface OccupancyAnalysisStatus {
  isRunning: boolean;
  lastUpdate: Date | null;
  totalRecordsProcessed: number;
  error: string | null;
}

export class OccupancyAnalysisService {
  private static instance: OccupancyAnalysisService;
  private readonly API_BASE_URL = 'http://localhost:8080/api/occupancy';

  public static getInstance(): OccupancyAnalysisService {
    if (!OccupancyAnalysisService.instance) {
      OccupancyAnalysisService.instance = new OccupancyAnalysisService();
    }
    return OccupancyAnalysisService.instance;
  }

  private constructor() {
    console.log('🎯 Initializing RTD Occupancy Analysis Service');
  }

  /**
   * Test connection to the occupancy analysis API
   */
  public async testConnection(): Promise<boolean> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/health`, { timeout: 3000 });
      return response.status === 200;
    } catch (error) {
      console.warn('⚠️ Occupancy Analysis API not available:', error);
      return false;
    }
  }

  /**
   * Get current occupancy analysis status
   */
  public async getAnalysisStatus(): Promise<OccupancyAnalysisStatus> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/status`, { timeout: 5000 });
      return {
        ...response.data,
        lastUpdate: response.data.lastUpdate ? new Date(response.data.lastUpdate) : null
      };
    } catch (error) {
      return {
        isRunning: false,
        lastUpdate: null,
        totalRecordsProcessed: 0,
        error: 'Unable to connect to occupancy analysis service'
      };
    }
  }

  /**
   * Get current occupancy accuracy metrics
   */
  public async getAccuracyMetrics(): Promise<OccupancyAccuracyMetric[]> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/accuracy-metrics`, { timeout: 10000 });
      return response.data.map((metric: any) => ({
        ...metric,
        lastUpdated: new Date(metric.lastUpdated)
      }));
    } catch (error) {
      console.error('Failed to fetch accuracy metrics:', error);
      // Return empty array for live data - UI will show "No data available"
      return [];
    }
  }

  /**
   * Get occupancy distribution comparison data
   */
  public async getOccupancyDistributions(): Promise<OccupancyDistribution[]> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/distributions`, { timeout: 10000 });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch occupancy distributions:', error);
      return [];
    }
  }

  /**
   * Get vehicle type analysis data
   */
  public async getVehicleTypeAnalysis(): Promise<VehicleTypeAnalysis[]> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/vehicle-types`, { timeout: 10000 });
      return response.data;
    } catch (error) {
      console.error('Failed to fetch vehicle type analysis:', error);
      return [];
    }
  }

  /**
   * Start the occupancy analysis pipeline
   */
  public async startAnalysis(): Promise<{ success: boolean; message: string }> {
    try {
      const response = await axios.post(`${this.API_BASE_URL}/start`, {}, { timeout: 10000 });
      return {
        success: true,
        message: response.data.message || 'Occupancy analysis started successfully'
      };
    } catch (error) {
      return {
        success: false,
        message: axios.isAxiosError(error) ? error.message : 'Failed to start occupancy analysis'
      };
    }
  }

  /**
   * Stop the occupancy analysis pipeline
   */
  public async stopAnalysis(): Promise<{ success: boolean; message: string }> {
    try {
      const response = await axios.post(`${this.API_BASE_URL}/stop`, {}, { timeout: 10000 });
      return {
        success: true,
        message: response.data.message || 'Occupancy analysis stopped successfully'
      };
    } catch (error) {
      return {
        success: false,
        message: axios.isAxiosError(error) ? error.message : 'Failed to stop occupancy analysis'
      };
    }
  }

  /**
   * Get live occupancy comparison data (sample of recent joined records)
   */
  public async getLiveOccupancyData(limit: number = 100): Promise<any[]> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/live-data?limit=${limit}`, { timeout: 10000 });
      return response.data.map((record: any) => ({
        ...record,
        timestamp: new Date(record.timestamp)
      }));
    } catch (error) {
      console.error('Failed to fetch live occupancy data:', error);
      return [];
    }
  }

  /**
   * Refresh all occupancy analysis data
   */
  public async refreshAnalysis(): Promise<{ success: boolean; message: string }> {
    try {
      const response = await axios.post(`${this.API_BASE_URL}/refresh`, {}, { timeout: 15000 });
      return {
        success: true,
        message: response.data.message || 'Occupancy analysis refreshed successfully'
      };
    } catch (error) {
      return {
        success: false,
        message: axios.isAxiosError(error) ? error.message : 'Failed to refresh occupancy analysis'
      };
    }
  }

  /**
   * Get historical accuracy trends (for charting)
   */
  public async getAccuracyTrends(days: number = 7): Promise<any[]> {
    try {
      const response = await axios.get(`${this.API_BASE_URL}/trends?days=${days}`, { timeout: 10000 });
      return response.data.map((point: any) => ({
        ...point,
        timestamp: new Date(point.timestamp)
      }));
    } catch (error) {
      console.error('Failed to fetch accuracy trends:', error);
      return [];
    }
  }
}