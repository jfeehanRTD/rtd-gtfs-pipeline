// RTD GTFS-RT Data Types for Real-time Transit Visualization

export interface VehiclePosition {
  vehicle_id: string;
  trip_id?: string;
  route_id?: string;
  latitude: number;
  longitude: number;
  bearing?: number;
  speed?: number; // m/s
  current_status: VehicleStatus;
  congestion_level?: CongestionLevel;
  occupancy_status?: OccupancyStatus;
  timestamp_ms: number;
}

export interface TripUpdate {
  trip_id: string;
  route_id?: string;
  vehicle_id?: string;
  start_date?: string;
  start_time?: string;
  schedule_relationship: ScheduleRelationship;
  delay_seconds?: number;
  timestamp_ms: number;
}

export interface Alert {
  alert_id: string;
  cause?: string;
  effect?: string;
  header_text?: string;
  description_text?: string;
  url?: string;
  active_period_start?: number;
  active_period_end?: number;
  timestamp_ms: number;
}

export interface RouteInfo {
  route_id: string;
  route_short_name: string;
  route_long_name: string;
  route_type: RouteType;
  route_color?: string;
  route_text_color?: string;
}

export interface EnhancedVehicleData extends VehiclePosition {
  route_info?: RouteInfo;
  delay_seconds?: number;
  next_stop?: string;
  last_updated: Date;
  is_real_time: boolean;
}

// GTFS-RT Enums
export enum VehicleStatus {
  INCOMING_AT = 'INCOMING_AT',
  STOPPED_AT = 'STOPPED_AT', 
  IN_TRANSIT_TO = 'IN_TRANSIT_TO'
}

export enum CongestionLevel {
  UNKNOWN_CONGESTION_LEVEL = 'UNKNOWN_CONGESTION_LEVEL',
  RUNNING_SMOOTHLY = 'RUNNING_SMOOTHLY',
  STOP_AND_GO = 'STOP_AND_GO',
  CONGESTION = 'CONGESTION',
  SEVERE_CONGESTION = 'SEVERE_CONGESTION'
}

export enum OccupancyStatus {
  EMPTY = 'EMPTY',
  MANY_SEATS_AVAILABLE = 'MANY_SEATS_AVAILABLE',
  FEW_SEATS_AVAILABLE = 'FEW_SEATS_AVAILABLE',
  STANDING_ROOM_ONLY = 'STANDING_ROOM_ONLY',
  CRUSHED_STANDING_ROOM_ONLY = 'CRUSHED_STANDING_ROOM_ONLY',
  FULL = 'FULL',
  NOT_ACCEPTING_PASSENGERS = 'NOT_ACCEPTING_PASSENGERS'
}

export enum ScheduleRelationship {
  SCHEDULED = 'SCHEDULED',
  ADDED = 'ADDED',
  UNSCHEDULED = 'UNSCHEDULED',
  CANCELED = 'CANCELED'
}

export enum RouteType {
  LIGHT_RAIL = 0,
  SUBWAY = 1,
  RAIL = 2,
  BUS = 3,
  FERRY = 4,
  CABLE_TRAM = 5,
  AERIAL_LIFT = 6,
  FUNICULAR = 7
}

// UI State Types
export interface MapFilters {
  showBuses: boolean;
  showTrains: boolean;
  selectedRoutes: string[];
  showDelayedOnly: boolean;
  minDelaySeconds: number;
}

export interface MapBounds {
  north: number;
  south: number;
  east: number;
  west: number;
}

export interface VehicleMarkerProps {
  vehicle: EnhancedVehicleData;
  isSelected: boolean;
  onClick: (vehicle: EnhancedVehicleData) => void;
}

export interface DataConnectionState {
  isConnected: boolean;
  lastUpdate: Date | null;
  vehicleCount: number;
  error: string | null;
}

// Kafka Integration Types
export interface KafkaConfig {
  brokers: string[];
  clientId: string;
  topics: {
    vehiclePositions: string;
    tripUpdates: string;
    alerts: string;
    comprehensiveRoutes: string;
  };
}

// Map Configuration
export interface MapConfig {
  center: {
    lat: number;
    lng: number;
  };
  zoom: number;
  styles?: google.maps.MapTypeStyle[];
  disableDefaultUI?: boolean;
  zoomControl?: boolean;
  mapTypeControl?: boolean;
  streetViewControl?: boolean;
  fullscreenControl?: boolean;
}

// Default Denver/RTD area configuration
export const DEFAULT_MAP_CONFIG: MapConfig = {
  center: {
    lat: 39.7392, // Denver latitude
    lng: -104.9903 // Denver longitude
  },
  zoom: 11,
  disableDefaultUI: false,
  zoomControl: true,
  mapTypeControl: true,
  streetViewControl: false,
  fullscreenControl: true
};

// RTD Route Colors (approximated)
export const RTD_ROUTE_COLORS: Record<string, string> = {
  // Light Rail Lines
  'A': '#0066CC', // A Line - Blue
  'B': '#00AA44', // B Line - Green  
  'C': '#FF6600', // C Line - Orange
  'D': '#FFD700', // D Line - Gold
  'E': '#800080', // E Line - Purple
  'F': '#FF69B4', // F Line - Pink
  'G': '#008080', // G Line - Teal
  'H': '#DC143C', // H Line - Red
  'L': '#FF1493', // L Line - Deep Pink
  'N': '#32CD32', // N Line - Lime Green
  'R': '#FF4500', // R Line - Red Orange
  'W': '#4B0082', // W Line - Indigo
  
  // Bus routes (generic colors)
  'BUS': '#666666',
  'DEFAULT': '#999999'
};