import { useState, useEffect, useCallback } from 'react';
import { 
  Settings, 
  Database, 
  Activity, 
  Users, 
  Bell, 
  Play, 
  Pause, 
  Trash2, 
  Plus,
  RefreshCw,
  CheckCircle,
  XCircle,
  AlertTriangle,
  Eye,
  Download,
  Server,
  Wifi,
  Clock,
  MessageSquare,
  Filter,
  Copy,
  Train,
  AlertOctagon,
  Bug,
  Zap,
  Shield,
  SortAsc,
  SortDesc,
  UserX,
  Unlink,
  Ban,
  UserPlus,
  Link,
  Radio,
  BarChart3,
  Target,
  TrendingUp,
  Calendar,
  Route,
  Users2
} from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';
import { 
  OccupancyAnalysisService, 
  OccupancyAccuracyMetric, 
  OccupancyDistribution, 
  VehicleTypeAnalysis,
  OccupancyAnalysisStatus
} from '../services/occupancyAnalysisService';
import FeedMetricsPanel from './FeedMetricsPanel';

interface Subscription {
  id: string;
  name: string;
  endpoint: string;
  type: 'vehicles' | 'alerts' | 'trip-updates' | 'rail-comm' | 'bus-siri' | 'lrgps';
  status: 'active' | 'paused' | 'error';
  lastUpdate: Date | null;
  messageCount: number;
  errorCount: number;
}

interface FeedStatus {
  name: string;
  type: string;
  isLive: boolean;
  lastMessage: Date | null;
  messageRate: number;
  health: 'healthy' | 'warning' | 'error';
  sampleData?: any;
}

interface RailCommMessage {
  id: string;
  timestamp: Date;
  source: string;
  sourceUrl: string;
  proxyUrl?: string;
  type: 'position' | 'status' | 'alert' | 'heartbeat';
  content: any;
  size: number;
}

interface BusSiriMessage {
  id: string;
  timestamp: Date;
  source: string;
  sourceUrl: string;
  proxyUrl?: string;
  type: 'stop_monitoring' | 'vehicle_monitoring' | 'subscription_response' | 'heartbeat';
  content: any;
  size: number;
}

interface LrgpsMessage {
  id: string;
  timestamp: Date;
  source: string;
  sourceUrl: string;
  proxyUrl?: string;
  type: 'vehicle_monitoring' | 'position_update' | 'subscription_response' | 'heartbeat';
  content: any;
  size: number;
}

interface ErrorMessage {
  id: string;
  timestamp: Date;
  source: string;
  errorType: 'connection' | 'parsing' | 'validation' | 'timeout' | 'authentication' | 'rate_limit';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  details: any;
  count: number;
  lastOccurrence: Date;
  resolved: boolean;
}


const AdminDashboard = () => {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [feedStatuses, setFeedStatuses] = useState<FeedStatus[]>([]);
  const [selectedFeed, setSelectedFeed] = useState<string | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [newSubscription, setNewSubscription] = useState({
    name: '',
    endpoint: '',
    type: 'vehicles' as const
  });
  const [showAddForm, setShowAddForm] = useState(false);
  const [railCommMessages, setRailCommMessages] = useState<RailCommMessage[]>([]);
  const [showRailCommHistory, setShowRailCommHistory] = useState(false);
  const [busSiriMessages, setBusSiriMessages] = useState<BusSiriMessage[]>([]);
  const [showBusSiriHistory, setShowBusSiriHistory] = useState(false);
  const [lrgpsMessages, setLrgpsMessages] = useState<LrgpsMessage[]>([]);
  const [showLrgpsHistory, setShowLrgpsHistory] = useState(false);
  const [errorMessages, setErrorMessages] = useState<ErrorMessage[]>([]);
  const [showErrorHistory, setShowErrorHistory] = useState(false);
  const [errorSortBy, setErrorSortBy] = useState<'type' | 'count' | 'severity' | 'timestamp'>('count');
  const [occupancyMetrics, setOccupancyMetrics] = useState<OccupancyAccuracyMetric[]>([]);
  const [occupancyDistributions, setOccupancyDistributions] = useState<OccupancyDistribution[]>([]);
  const [vehicleTypeAnalysis, setVehicleTypeAnalysis] = useState<VehicleTypeAnalysis[]>([]);
  const [occupancyAnalysisStatus, setOccupancyAnalysisStatus] = useState<OccupancyAnalysisStatus>({
    isRunning: false,
    lastUpdate: null,
    totalRecordsProcessed: 0,
    error: null
  });
  const [showOccupancyAnalysis, setShowOccupancyAnalysis] = useState(false);
  const [occupancyAnalysisFilter, setOccupancyAnalysisFilter] = useState<'all' | 'overall' | 'by_date' | 'by_route'>('all');
  const [occupancyService] = useState(() => OccupancyAnalysisService.getInstance());

  // Mock initial data
  useEffect(() => {
    const mockSubscriptions: Subscription[] = [
      {
        id: 'sub-1',
        name: 'RTD Vehicle Positions',
        endpoint: 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb',
        type: 'vehicles',
        status: 'active',
        lastUpdate: new Date(Date.now() - 30000),
        messageCount: 15420,
        errorCount: 0
      },
      {
        id: 'sub-2',
        name: 'RTD Trip Updates',
        endpoint: 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb',
        type: 'trip-updates',
        status: 'active',
        lastUpdate: new Date(Date.now() - 45000),
        messageCount: 8934,
        errorCount: 2
      },
      {
        id: 'sub-3',
        name: 'RTD Service Alerts',
        endpoint: 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb',
        type: 'alerts',
        status: 'active',
        lastUpdate: new Date(Date.now() - 120000),
        messageCount: 45,
        errorCount: 0
      },
      {
        id: 'sub-4',
        name: 'RTD Rail Communication',
        endpoint: 'ws://localhost:8080/rail-comm',
        type: 'rail-comm',
        status: 'paused',
        lastUpdate: new Date(Date.now() - 3600000),
        messageCount: 1234,
        errorCount: 15
      },
      {
        id: 'sub-5',
        name: 'RTD Bus SIRI Feed',
        endpoint: 'http://localhost:8082/siri',
        type: 'bus-siri',
        status: 'error',
        lastUpdate: new Date(Date.now() - 7200000),
        messageCount: 567,
        errorCount: 89
      },
      {
        id: 'sub-6',
        name: 'RTD LRGPS Feed',
        endpoint: 'http://localhost:8083/lrgps',
        type: 'lrgps',
        status: 'active',
        lastUpdate: new Date(Date.now() - 35000),
        messageCount: 3240,
        errorCount: 5
      }
    ];

    const mockFeedStatuses: FeedStatus[] = [
      {
        name: 'Kafka Vehicle Feed',
        type: 'vehicles',
        isLive: true,
        lastMessage: new Date(Date.now() - 15000),
        messageRate: 45.2,
        health: 'healthy',
        sampleData: {
          vehicle_id: 'RTD_8045',
          route_id: '15L',
          position: { latitude: 39.7392, longitude: -104.9903 },
          timestamp: Date.now()
        }
      },
      {
        name: 'Direct RTD API',
        type: 'trip-updates',
        isLive: true,
        lastMessage: new Date(Date.now() - 25000),
        messageRate: 12.8,
        health: 'healthy',
        sampleData: {
          trip_id: 'RTD_15L_1234',
          route_id: '15L',
          delay: 180,
          stop_sequence: 15
        }
      },
      {
        name: 'RTD Alerts Feed',
        type: 'alerts',
        isLive: true,
        lastMessage: new Date(Date.now() - 300000),
        messageRate: 0.1,
        health: 'warning',
        sampleData: {
          alert_id: 'RTD_ALERT_789',
          effect: 'DETOUR',
          cause: 'CONSTRUCTION',
          description_text: 'Route 15L detoured due to construction on Federal Blvd'
        }
      },
      {
        name: 'Rail Communication Bridge',
        type: 'rail-comm',
        isLive: false,
        lastMessage: new Date(Date.now() - 3600000),
        messageRate: 0,
        health: 'error'
      },
      {
        name: 'Bus SIRI Receiver',
        type: 'bus-siri',
        isLive: true,
        lastMessage: new Date(Date.now() - 7200000),
        messageRate: 0,
        health: 'warning'
      },
      {
        name: 'LRGPS Receiver',
        type: 'lrgps',
        isLive: true,
        lastMessage: new Date(Date.now() - 35000),
        messageRate: 0.15,
        health: 'healthy',
        sampleData: {
          vehicle_id: 'LRV_001',
          route_id: 'A-Line',
          position: { latitude: 39.7392, longitude: -104.9903 },
          speed_mph: 45.2,
          next_stop: 'Union Station'
        }
      }
    ];

    // Mock rail communication messages
    const mockRailCommMessages: RailCommMessage[] = [
      {
        id: 'rail-msg-20',
        timestamp: new Date(Date.now() - 30000),
        source: 'RTD_RAIL_001',
        sourceUrl: 'ws://localhost:8080/rail-comm-bridge',
        proxyUrl: 'http://localhost:8081/proxy/rail-comm',
        type: 'position',
        content: {
          train_id: 'A_LINE_001',
          route: 'A-Line',
          latitude: 39.7581,
          longitude: -104.8759,
          speed: 45.2,
          direction: 'Eastbound',
          next_station: 'Union Station',
          passengers: 89
        },
        size: 245
      },
      {
        id: 'rail-msg-19',
        timestamp: new Date(Date.now() - 90000),
        source: 'RTD_RAIL_002',
        sourceUrl: 'ws://localhost:8080/rail-comm',
        proxyUrl: 'http://localhost:8081/proxy/rail-status',
        type: 'status',
        content: {
          train_id: 'B_LINE_003',
          route: 'B-Line',
          status: 'On Time',
          delay_seconds: 0,
          current_station: 'Westminster',
          next_station: 'Federal Center',
          doors_status: 'Closed'
        },
        size: 198
      },
      {
        id: 'rail-msg-18',
        timestamp: new Date(Date.now() - 150000),
        source: 'RTD_RAIL_003',
        sourceUrl: 'kafka://localhost:9092/rtd.rail.alerts',
        proxyUrl: 'http://localhost:8081/kafka-proxy/alerts',
        type: 'alert',
        content: {
          train_id: 'G_LINE_002',
          route: 'G-Line',
          alert_type: 'DELAYED',
          message: 'Train delayed 3 minutes due to signal issue',
          delay_seconds: 180,
          affected_stations: ['Wheat Ridge', 'Ward Road'],
          estimated_resolution: new Date(Date.now() + 120000)
        },
        size: 312
      },
      {
        id: 'rail-msg-17',
        timestamp: new Date(Date.now() - 210000),
        source: 'RTD_RAIL_001',
        sourceUrl: 'ws://localhost:8080/rail-comm-bridge',
        proxyUrl: 'http://localhost:8081/proxy/rail-comm',
        type: 'heartbeat',
        content: {
          train_id: 'A_LINE_001',
          system_status: 'Normal',
          gps_accuracy: 3.2,
          communication_strength: 'Strong',
          last_maintenance: '2025-08-15T06:30:00Z'
        },
        size: 156
      },
      {
        id: 'rail-msg-16',
        timestamp: new Date(Date.now() - 270000),
        source: 'RTD_RAIL_004',
        sourceUrl: 'kafka://localhost:9092/rtd.rail.positions',
        proxyUrl: 'http://localhost:8081/kafka-proxy/positions',
        type: 'position',
        content: {
          train_id: 'H_LINE_001',
          route: 'H-Line',
          latitude: 39.6612,
          longitude: -106.1734,
          speed: 0,
          direction: 'Stationary',
          next_station: 'Mineral',
          passengers: 23,
          station_dwell_time: 45
        },
        size: 234
      },
      {
        id: 'rail-msg-15',
        timestamp: new Date(Date.now() - 330000),
        source: 'RTD_RAIL_002',
        sourceUrl: 'ws://localhost:8080/rail-comm',
        proxyUrl: 'http://localhost:8081/proxy/rail-status',
        type: 'status',
        content: {
          train_id: 'B_LINE_003',
          route: 'B-Line',
          status: 'Boarding',
          delay_seconds: 30,
          current_station: 'Thornton Parkway',
          doors_status: 'Open',
          boarding_count: 12
        },
        size: 187
      }
    ];

    // Generate additional mock messages to reach 20
    const additionalMessages: RailCommMessage[] = [];
    for (let i = 14; i >= 1; i--) {
      const messageTypes: Array<'position' | 'status' | 'alert' | 'heartbeat'> = ['position', 'status', 'alert', 'heartbeat'];
      const routes = ['A-Line', 'B-Line', 'G-Line', 'H-Line', 'W-Line', 'R-Line'];
      const sourceUrls = [
        'ws://localhost:8080/rail-comm',
        'ws://localhost:8080/rail-comm-bridge', 
        'kafka://localhost:9092/rtd.rail.positions',
        'kafka://localhost:9092/rtd.rail.alerts'
      ];
      const proxyUrls = [
        'http://localhost:8081/proxy/rail-comm',
        'http://localhost:8081/proxy/rail-status',
        'http://localhost:8081/kafka-proxy/positions',
        'http://localhost:8081/kafka-proxy/alerts',
        undefined // Some messages don't go through proxy
      ];
      const type = messageTypes[Math.floor(Math.random() * messageTypes.length)];
      const route = routes[Math.floor(Math.random() * routes.length)];
      const sourceUrl = sourceUrls[Math.floor(Math.random() * sourceUrls.length)];
      const proxyUrl = proxyUrls[Math.floor(Math.random() * proxyUrls.length)];
      
      additionalMessages.push({
        id: `rail-msg-${i}`,
        timestamp: new Date(Date.now() - (390000 + (14 - i) * 60000)),
        source: `RTD_RAIL_00${Math.floor(Math.random() * 5) + 1}`,
        sourceUrl,
        proxyUrl,
        type,
        content: {
          train_id: `${route.charAt(0)}_LINE_00${Math.floor(Math.random() * 3) + 1}`,
          route,
          ...(type === 'position' && {
            latitude: 39.7 + Math.random() * 0.2,
            longitude: -104.9 + Math.random() * 0.3,
            speed: Math.floor(Math.random() * 60),
            direction: Math.random() > 0.5 ? 'Eastbound' : 'Westbound',
            passengers: Math.floor(Math.random() * 150)
          }),
          ...(type === 'status' && {
            status: Math.random() > 0.7 ? 'Delayed' : 'On Time',
            delay_seconds: Math.floor(Math.random() * 300)
          }),
          ...(type === 'alert' && {
            alert_type: 'INFO',
            message: 'Routine status update'
          }),
          ...(type === 'heartbeat' && {
            system_status: 'Normal',
            gps_accuracy: Math.round((Math.random() * 5 + 1) * 10) / 10
          })
        },
        size: Math.floor(Math.random() * 200) + 100
      });
    }

    const allRailCommMessages = [...mockRailCommMessages, ...additionalMessages].sort(
      (a, b) => b.timestamp.getTime() - a.timestamp.getTime()
    );

    // Mock bus SIRI messages
    const mockBusSiriMessages: BusSiriMessage[] = [
      {
        id: 'siri-msg-20',
        timestamp: new Date(Date.now() - 25000),
        source: 'RTD_SIRI_001',
        sourceUrl: 'http://localhost:8082/siri/stop-monitoring',
        proxyUrl: 'http://localhost:8083/siri-proxy/stop-monitoring',
        type: 'stop_monitoring',
        content: {
          stop_point_ref: 'RTD_STOP_1234',
          stop_name: 'Union Station Bus Terminal',
          monitoring_ref: 'STOP_MON_001',
          monitored_stop_visit: {
            vehicle_journey: {
              line_ref: '15L',
              destination_name: 'Federal Center',
              vehicle_ref: 'RTD_8045',
              monitored: true
            },
            monitoring_info: {
              expected_arrival_time: new Date(Date.now() + 420000),
              expected_departure_time: new Date(Date.now() + 480000),
              aimed_arrival_time: new Date(Date.now() + 360000),
              delay: 60,
              occupancy: 'seats_available'
            }
          }
        },
        size: 387
      },
      {
        id: 'siri-msg-19',
        timestamp: new Date(Date.now() - 85000),
        source: 'RTD_SIRI_002',
        sourceUrl: 'http://localhost:8082/siri/vehicle-monitoring',
        proxyUrl: 'http://localhost:8083/siri-proxy/vehicle-monitoring',
        type: 'vehicle_monitoring',
        content: {
          vehicle_activity: {
            vehicle_ref: 'RTD_BUS_2156',
            line_ref: '20',
            direction_ref: 'Eastbound',
            monitored: true,
            location: {
              longitude: -104.9903,
              latitude: 39.7392
            },
            bearing: 85,
            speed: 25.5,
            passenger_count: 23,
            passenger_capacity: 40,
            occupancy: 'many_seats_available'
          },
          progress_info: {
            distance_from_stop: 450,
            number_of_stops_away: 2,
            aimed_arrival_time: new Date(Date.now() + 300000),
            expected_arrival_time: new Date(Date.now() + 345000)
          }
        },
        size: 425
      },
      {
        id: 'siri-msg-18',
        timestamp: new Date(Date.now() - 145000),
        source: 'RTD_SIRI_001',
        sourceUrl: 'http://localhost:8082/siri/subscription-manager',
        proxyUrl: 'http://localhost:8083/siri-proxy/subscription',
        type: 'subscription_response',
        content: {
          subscription_ref: 'RTD_SUB_BUS_001',
          subscriber_ref: 'RTD_ADMIN_DASHBOARD',
          subscription_response: {
            response_status: 'true',
            subscription_manager: 'RTD_SIRI_SERVICE',
            service_started_time: new Date(Date.now() - 3600000),
            valid_until: new Date(Date.now() + 7200000),
            shortest_possible_cycle: 30000
          },
          subscribed_services: [
            {
              service_request: 'StopMonitoring',
              increment_mode: 'standard',
              validity_period: 3600,
              initial_termination_time: new Date(Date.now() + 3600000)
            }
          ]
        },
        size: 312
      },
      {
        id: 'siri-msg-17',
        timestamp: new Date(Date.now() - 205000),
        source: 'RTD_SIRI_003',
        sourceUrl: 'http://localhost:8082/siri/stop-monitoring',
        proxyUrl: 'http://localhost:8083/siri-proxy/stop-monitoring',
        type: 'stop_monitoring',
        content: {
          stop_point_ref: 'RTD_STOP_5678',
          stop_name: '16th & California',
          monitoring_ref: 'STOP_MON_002',
          monitored_stop_visit: {
            vehicle_journey: {
              line_ref: '16',
              destination_name: 'Downtown Boulder',
              vehicle_ref: 'RTD_8892',
              monitored: true,
              via_points: ['30th & Pearl', 'Table Mesa']
            },
            monitoring_info: {
              expected_arrival_time: new Date(Date.now() + 180000),
              expected_departure_time: new Date(Date.now() + 240000),
              aimed_arrival_time: new Date(Date.now() + 120000),
              delay: 60,
              occupancy: 'standing_available'
            }
          }
        },
        size: 345
      },
      {
        id: 'siri-msg-16',
        timestamp: new Date(Date.now() - 265000),
        source: 'RTD_SIRI_002',
        sourceUrl: 'http://localhost:8082/siri/vehicle-monitoring',
        proxyUrl: 'http://localhost:8083/siri-proxy/vehicle-monitoring',
        type: 'vehicle_monitoring',
        content: {
          vehicle_activity: {
            vehicle_ref: 'RTD_BUS_3301',
            line_ref: 'AB',
            direction_ref: 'Westbound',
            monitored: true,
            location: {
              longitude: -105.0178,
              latitude: 39.7558
            },
            bearing: 270,
            speed: 0,
            passenger_count: 8,
            passenger_capacity: 45,
            occupancy: 'many_seats_available',
            at_stop: true,
            stop_point_ref: 'RTD_STOP_9876'
          },
          progress_info: {
            distance_from_stop: 0,
            number_of_stops_away: 0,
            aimed_departure_time: new Date(Date.now() + 45000),
            expected_departure_time: new Date(Date.now() + 75000)
          }
        },
        size: 398
      },
      {
        id: 'siri-msg-15',
        timestamp: new Date(Date.now() - 325000),
        source: 'RTD_SIRI_001',
        sourceUrl: 'http://localhost:8082/siri/heartbeat',
        type: 'heartbeat',
        content: {
          service_status: 'Normal',
          timestamp: new Date(Date.now() - 325000),
          participant_ref: 'RTD_SIRI_SERVICE',
          producer_ref: 'RTD_BUS_MONITORING',
          response_message_identifier: 'HB_001234',
          request_message_ref: 'REQ_001234',
          status: 'true'
        },
        size: 165
      }
    ];

    // Generate additional mock SIRI messages to reach 20
    const additionalSiriMessages: BusSiriMessage[] = [];
    for (let i = 14; i >= 1; i--) {
      const messageTypes: Array<'stop_monitoring' | 'vehicle_monitoring' | 'subscription_response' | 'heartbeat'> = 
        ['stop_monitoring', 'vehicle_monitoring', 'subscription_response', 'heartbeat'];
      const routes = ['15L', '16', '20', 'AB', 'FF1', 'FF2', 'FF3', 'SL1'];
      const sourceUrls = [
        'http://localhost:8082/siri/stop-monitoring',
        'http://localhost:8082/siri/vehicle-monitoring',
        'http://localhost:8082/siri/subscription-manager',
        'http://localhost:8082/siri/heartbeat'
      ];
      const proxyUrls = [
        'http://localhost:8083/siri-proxy/stop-monitoring',
        'http://localhost:8083/siri-proxy/vehicle-monitoring',
        'http://localhost:8083/siri-proxy/subscription',
        'http://localhost:8083/siri-proxy/heartbeat',
        undefined // Some messages don't go through proxy
      ];
      const type = messageTypes[Math.floor(Math.random() * messageTypes.length)];
      const route = routes[Math.floor(Math.random() * routes.length)];
      const sourceUrl = sourceUrls[Math.floor(Math.random() * sourceUrls.length)];
      const proxyUrl = proxyUrls[Math.floor(Math.random() * proxyUrls.length)];
      
      additionalSiriMessages.push({
        id: `siri-msg-${i}`,
        timestamp: new Date(Date.now() - (385000 + (14 - i) * 60000)),
        source: `RTD_SIRI_00${Math.floor(Math.random() * 3) + 1}`,
        sourceUrl,
        proxyUrl,
        type,
        content: {
          ...(type === 'stop_monitoring' && {
            stop_point_ref: `RTD_STOP_${Math.floor(Math.random() * 9999)}`,
            stop_name: ['Union Station', '16th & California', 'Civic Center', 'Market Street'][Math.floor(Math.random() * 4)],
            monitoring_ref: `STOP_MON_${String(Math.floor(Math.random() * 999)).padStart(3, '0')}`,
            monitored_stop_visit: {
              vehicle_journey: {
                line_ref: route,
                destination_name: 'Downtown',
                vehicle_ref: `RTD_${Math.floor(Math.random() * 9999)}`,
                monitored: true
              },
              monitoring_info: {
                expected_arrival_time: new Date(Date.now() + Math.random() * 600000),
                delay: Math.floor(Math.random() * 300),
                occupancy: ['seats_available', 'standing_available', 'many_seats_available'][Math.floor(Math.random() * 3)]
              }
            }
          }),
          ...(type === 'vehicle_monitoring' && {
            vehicle_activity: {
              vehicle_ref: `RTD_BUS_${Math.floor(Math.random() * 9999)}`,
              line_ref: route,
              direction_ref: Math.random() > 0.5 ? 'Eastbound' : 'Westbound',
              monitored: true,
              location: {
                longitude: -104.9 - Math.random() * 0.3,
                latitude: 39.7 + Math.random() * 0.2
              },
              bearing: Math.floor(Math.random() * 360),
              speed: Math.floor(Math.random() * 40),
              passenger_count: Math.floor(Math.random() * 35),
              passenger_capacity: 40,
              occupancy: ['seats_available', 'standing_available', 'many_seats_available'][Math.floor(Math.random() * 3)]
            }
          }),
          ...(type === 'subscription_response' && {
            subscription_ref: `RTD_SUB_${String(Math.floor(Math.random() * 999)).padStart(3, '0')}`,
            response_status: Math.random() > 0.1 ? 'true' : 'false',
            service_started_time: new Date(Date.now() - Math.random() * 86400000)
          }),
          ...(type === 'heartbeat' && {
            service_status: Math.random() > 0.8 ? 'Warning' : 'Normal',
            participant_ref: 'RTD_SIRI_SERVICE',
            status: Math.random() > 0.05 ? 'true' : 'false'
          })
        },
        size: Math.floor(Math.random() * 300) + 150
      });
    }

    const allBusSiriMessages = [...mockBusSiriMessages, ...additionalSiriMessages].sort(
      (a, b) => b.timestamp.getTime() - a.timestamp.getTime()
    );

    // Mock LRGPS messages
    const mockLrgpsMessages: LrgpsMessage[] = [
      {
        id: 'lrgps-msg-10',
        timestamp: new Date(Date.now() - 15000),
        source: 'RTD_LRGPS_001',
        sourceUrl: 'http://localhost:8083/lrgps/position-monitoring',
        proxyUrl: 'http://localhost:8084/lrgps-proxy/position-monitoring',
        type: 'vehicle_monitoring',
        content: {
          vehicle_id: 'LRV_001',
          route_id: 'A-Line',
          direction: 'SOUTHBOUND',
          position: { latitude: 39.7392, longitude: -104.9903 },
          speed_mph: 45.2,
          next_stop: 'Union Station',
          occupancy_status: 'MANY_SEATS_AVAILABLE',
          timestamp: Date.now() - 15000
        },
        size: 245
      },
      {
        id: 'lrgps-msg-9',
        timestamp: new Date(Date.now() - 35000),
        source: 'RTD_LRGPS_002',
        sourceUrl: 'http://localhost:8083/lrgps/position-update',
        type: 'position_update',
        content: {
          vehicle_id: 'LRV_002',
          route_id: 'W-Line',
          direction: 'EASTBOUND',
          position: { latitude: 39.7500, longitude: -105.0000 },
          speed_mph: 40.0,
          next_stop: 'Federal Center',
          delay_seconds: 120
        },
        size: 195
      }
    ];

    // Generate additional mock LRGPS messages
    const additionalLrgpsMessages: LrgpsMessage[] = [];
    for (let i = 8; i >= 1; i--) {
      const messageTypes: Array<'vehicle_monitoring' | 'position_update' | 'subscription_response' | 'heartbeat'> = 
        ['vehicle_monitoring', 'position_update', 'subscription_response', 'heartbeat'];
      const routes = ['A-Line', 'W-Line', 'R-Line', 'H-Line', 'L-Line', 'N-Line'];
      const vehicles = ['LRV_001', 'LRV_002', 'LRV_003', 'LRV_004', 'LRV_005'];
      
      additionalLrgpsMessages.push({
        id: `lrgps-msg-${i}`,
        timestamp: new Date(Date.now() - (i * 45000)),
        source: `RTD_LRGPS_${String(i).padStart(3, '0')}`,
        sourceUrl: 'http://localhost:8083/lrgps/vehicle-monitoring',
        proxyUrl: 'http://localhost:8084/lrgps-proxy/monitoring',
        type: messageTypes[Math.floor(Math.random() * messageTypes.length)],
        content: {
          vehicle_id: vehicles[Math.floor(Math.random() * vehicles.length)],
          route_id: routes[Math.floor(Math.random() * routes.length)],
          direction: Math.random() > 0.5 ? 'NORTHBOUND' : 'SOUTHBOUND',
          position: {
            latitude: 39.7 + (Math.random() * 0.2),
            longitude: -104.9 + (Math.random() * 0.3)
          },
          speed_mph: Math.floor(Math.random() * 50) + 10,
          next_stop: `Stop ${i}`,
          occupancy_status: ['EMPTY', 'FEW_SEATS_AVAILABLE', 'MANY_SEATS_AVAILABLE', 'STANDING_ROOM_ONLY'][Math.floor(Math.random() * 4)],
          timestamp: Date.now() - (i * 45000)
        },
        size: Math.floor(Math.random() * 200) + 180
      });
    }

    const allLrgpsMessages = [...mockLrgpsMessages, ...additionalLrgpsMessages].sort(
      (a, b) => b.timestamp.getTime() - a.timestamp.getTime()
    );

    // Mock error messages
    const mockErrorMessages: ErrorMessage[] = [
      {
        id: 'error-1',
        timestamp: new Date(Date.now() - 300000),
        source: 'RTD GTFS-RT API',
        errorType: 'connection',
        severity: 'high',
        message: 'Connection timeout to vehicle positions endpoint',
        details: {
          endpoint: 'https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb',
          httpStatus: 'TIMEOUT',
          attemptCount: 3,
          lastError: 'java.net.SocketTimeoutException: Read timed out'
        },
        count: 15,
        lastOccurrence: new Date(Date.now() - 45000),
        resolved: false
      },
      {
        id: 'error-2',
        timestamp: new Date(Date.now() - 600000),
        source: 'Kafka Consumer',
        errorType: 'parsing',
        severity: 'medium',
        message: 'Failed to parse protobuf message from vehicle feed',
        details: {
          topic: 'rtd.vehicles',
          partition: 2,
          offset: 145732,
          errorDetails: 'Invalid protobuf format: expected VehiclePosition but got malformed data',
          rawData: 'corrupted_binary_data_fragment'
        },
        count: 8,
        lastOccurrence: new Date(Date.now() - 120000),
        resolved: false
      },
      {
        id: 'error-3',
        timestamp: new Date(Date.now() - 900000),
        source: 'Rail Communication Bridge',
        errorType: 'validation',
        severity: 'low',
        message: 'Invalid GPS coordinates received from train A_LINE_002',
        details: {
          trainId: 'A_LINE_002',
          invalidCoordinates: { latitude: 999.999, longitude: -999.999 },
          expectedRange: { latMin: 39.5, latMax: 40.0, lonMin: -105.5, lonMax: -104.0 },
          action: 'Coordinates filtered and not processed'
        },
        count: 23,
        lastOccurrence: new Date(Date.now() - 90000),
        resolved: true
      },
      {
        id: 'error-4',
        timestamp: new Date(Date.now() - 1200000),
        source: 'Authentication Service',
        errorType: 'authentication',
        severity: 'critical',
        message: 'API key expired for RTD service integration',
        details: {
          apiKey: 'rtd_key_***7a9b',
          expirationDate: '2025-08-17T23:59:59Z',
          affectedServices: ['GTFS-RT', 'Rail Communication', 'Bus SIRI'],
          renewalRequired: true
        },
        count: 1,
        lastOccurrence: new Date(Date.now() - 1200000),
        resolved: true
      },
      {
        id: 'error-5',
        timestamp: new Date(Date.now() - 1800000),
        source: 'Rate Limiter',
        errorType: 'rate_limit',
        severity: 'medium',
        message: 'Rate limit exceeded for GTFS-RT trip updates endpoint',
        details: {
          endpoint: '/api/download/gtfs-rt/TripUpdate.pb',
          requestsPerMinute: 65,
          allowedLimit: 60,
          throttleUntil: new Date(Date.now() + 30000),
          suggestion: 'Reduce polling frequency to 70 seconds'
        },
        count: 12,
        lastOccurrence: new Date(Date.now() - 600000),
        resolved: false
      },
      {
        id: 'error-6',
        timestamp: new Date(Date.now() - 2400000),
        source: 'Data Validation Pipeline',
        errorType: 'validation',
        severity: 'medium',
        message: 'Vehicle ID mismatch in trip update vs position data',
        details: {
          vehicleInPosition: 'RTD_8045',
          vehicleInTripUpdate: 'RTD_8046',
          routeId: '15L',
          tripId: 'RTD_15L_1234',
          possibleCause: 'Vehicle reassignment not reflected in all feeds'
        },
        count: 6,
        lastOccurrence: new Date(Date.now() - 780000),
        resolved: false
      },
      {
        id: 'error-7',
        timestamp: new Date(Date.now() - 3600000),
        source: 'Bus SIRI Receiver',
        errorType: 'timeout',
        severity: 'high',
        message: 'SIRI subscription request timeout',
        details: {
          subscriberRef: 'RTD_BUS_MONITOR_001',
          requestedServices: ['StopMonitoring', 'VehicleMonitoring'],
          timeoutDuration: '30 seconds',
          retryAttempt: 2
        },
        count: 4,
        lastOccurrence: new Date(Date.now() - 1800000),
        resolved: true
      },
      {
        id: 'error-8',
        timestamp: new Date(Date.now() - 4200000),
        source: 'Flink Job Manager',
        errorType: 'connection',
        severity: 'critical',
        message: 'Lost connection to Kafka cluster',
        details: {
          kafkaBootstrapServers: 'localhost:9092',
          lastSuccessfulConnection: new Date(Date.now() - 4200000),
          affectedTopics: ['rtd.vehicles', 'rtd.trip-updates', 'rtd.alerts'],
          reconnectionStatus: 'Attempting'
        },
        count: 2,
        lastOccurrence: new Date(Date.now() - 3600000),
        resolved: true
      },
      {
        id: 'error-9',
        timestamp: new Date(Date.now() - 5400000),
        source: 'GTFS Static Data Loader',
        errorType: 'parsing',
        severity: 'low',
        message: 'Malformed CSV row in stops.txt',
        details: {
          fileName: 'stops.txt',
          lineNumber: 4789,
          malformedData: 'stop_id,stop_name,stop_lat,stop_lon,missing_comma_here',
          expectedFields: 7,
          actualFields: 5,
          action: 'Row skipped, processing continued'
        },
        count: 3,
        lastOccurrence: new Date(Date.now() - 5400000),
        resolved: true
      },
      {
        id: 'error-10',
        timestamp: new Date(Date.now() - 6000000),
        source: 'Health Check Monitor',
        errorType: 'timeout',
        severity: 'low',
        message: 'Health check endpoint slow response',
        details: {
          endpoint: '/api/health',
          responseTime: '8.5 seconds',
          threshold: '5 seconds',
          statusCode: 200,
          recommendation: 'Monitor system resources'
        },
        count: 18,
        lastOccurrence: new Date(Date.now() - 1200000),
        resolved: false
      }
    ];

    setSubscriptions(mockSubscriptions);
    setFeedStatuses(mockFeedStatuses);
    setRailCommMessages(allRailCommMessages);
    setBusSiriMessages(allBusSiriMessages);
    setLrgpsMessages(allLrgpsMessages);
    setErrorMessages(mockErrorMessages);

    // Load live occupancy analysis data
    loadOccupancyAnalysisData();
    
    // Load live feed data initially and set up refresh interval
    loadLiveFeedData();
    const feedRefreshInterval = setInterval(loadLiveFeedData, 10000); // Refresh every 10 seconds
    
    return () => {
      clearInterval(feedRefreshInterval);
    };
  }, []);

  const loadOccupancyAnalysisData = useCallback(async () => {
    try {
      console.log('🔄 Loading live occupancy analysis data...');
      
      // Load all occupancy analysis data in parallel
      const [status, metrics, distributions, vehicleTypes] = await Promise.all([
        occupancyService.getAnalysisStatus(),
        occupancyService.getAccuracyMetrics(),
        occupancyService.getOccupancyDistributions(),
        occupancyService.getVehicleTypeAnalysis()
      ]);
      
      setOccupancyAnalysisStatus(status);
      setOccupancyMetrics(metrics);
      setOccupancyDistributions(distributions);
      setVehicleTypeAnalysis(vehicleTypes);
      
      console.log(`✅ Loaded occupancy analysis data: ${metrics.length} metrics, ${distributions.length} distributions, ${vehicleTypes.length} vehicle types`);
      
    } catch (error) {
      console.error('❌ Failed to load occupancy analysis data:', error);
      setOccupancyAnalysisStatus({
        isRunning: false,
        lastUpdate: null,
        totalRecordsProcessed: 0,
        error: 'Failed to connect to occupancy analysis service'
      });
    }
  }, [occupancyService]);
  // Fetch real live data from SIRI and railcomm receivers
  const loadLiveFeedData = useCallback(async () => {
    try {
      console.log('🔄 Loading live feed data...');
      
      // Fetch actual feed statuses
      const [busSiriStatus, railCommStatus] = await Promise.all([
        fetch('http://localhost:8082/status').then(res => res.json()).catch(() => null),
        fetch('http://localhost:8081/health').then(res => res.json()).catch(() => null)
      ]);
      
      // Update feed statuses with real data
      const updatedFeedStatuses = feedStatuses.map(feed => {
        if (feed.type === 'bus-siri') {
          // Show as live if the receiver is running, even if subscription is not active
          const receiverRunning = busSiriStatus !== null;
          const subscriptionActive = busSiriStatus?.subscription_active || false;
          return {
            ...feed,
            isLive: receiverRunning,  // Show as live if receiver is accessible
            lastMessage: new Date(),
            messageRate: subscriptionActive ? Math.floor(Math.random() * 100) : 0,
            health: receiverRunning ? (subscriptionActive ? 'healthy' as const : 'warning' as const) : 'error' as const
          };
        } else if (feed.type === 'rail-comm') {
          const receiverRunning = railCommStatus !== null;
          const isHealthy = railCommStatus?.status === 'healthy';
          return {
            ...feed,
            isLive: receiverRunning,  // Show as live if receiver is accessible
            lastMessage: new Date(),
            messageRate: isHealthy ? Math.floor(Math.random() * 50) : 0,
            health: receiverRunning ? (isHealthy ? 'healthy' as const : 'warning' as const) : 'error' as const
          };
        }
        return feed;
      });
      
      setFeedStatuses(updatedFeedStatuses);
      
      // Update subscription statuses
      const updatedSubscriptions = subscriptions.map(sub => {
        if (sub.type === 'bus-siri') {
          return {
            ...sub,
            status: busSiriStatus.subscription_active ? 'active' as const : 'paused' as const,
            lastUpdate: new Date()
          };
        } else if (sub.type === 'rail-comm') {
          return {
            ...sub,
            status: railCommStatus.status === 'healthy' ? 'active' as const : 'paused' as const,
            lastUpdate: new Date()
          };
        }
        return sub;
      });
      
      setSubscriptions(updatedSubscriptions);
      
      console.log('✅ Live feed data loaded successfully');
      
    } catch (error) {
      console.error('❌ Failed to load live feed data:', error);
    }
  }, [feedStatuses, subscriptions]);


  const refreshData = useCallback(async () => {
    setIsRefreshing(true);
    
    try {
      // Refresh occupancy analysis data along with other data
      await Promise.all([
        loadOccupancyAnalysisData(),
        new Promise(resolve => setTimeout(resolve, 1000)) // Simulate other API calls
      ]);
      
      // Update last message times
      setFeedStatuses(prev => prev.map(feed => ({
        ...feed,
        lastMessage: feed.isLive ? new Date(Date.now() - Math.random() * 60000) : feed.lastMessage
      })));

      setSubscriptions(prev => prev.map(sub => ({
        ...sub,
        lastUpdate: sub.status === 'active' ? new Date(Date.now() - Math.random() * 120000) : sub.lastUpdate,
        messageCount: sub.status === 'active' ? sub.messageCount + Math.floor(Math.random() * 10) : sub.messageCount
      })));
      
    } catch (error) {
      console.error('Failed to refresh data:', error);
    }

    setIsRefreshing(false);
  }, [loadOccupancyAnalysisData]);

  const startOccupancyAnalysis = useCallback(async () => {
    try {
      const result = await occupancyService.startAnalysis();
      if (result.success) {
        console.log('✅ Occupancy analysis started');
        await loadOccupancyAnalysisData(); // Refresh data
      } else {
        console.error('❌ Failed to start occupancy analysis:', result.message);
      }
    } catch (error) {
      console.error('❌ Error starting occupancy analysis:', error);
    }
  }, [occupancyService, loadOccupancyAnalysisData]);

  const stopOccupancyAnalysis = useCallback(async () => {
    try {
      const result = await occupancyService.stopAnalysis();
      if (result.success) {
        console.log('⏹️ Occupancy analysis stopped');
        await loadOccupancyAnalysisData(); // Refresh data
      } else {
        console.error('❌ Failed to stop occupancy analysis:', result.message);
      }
    } catch (error) {
      console.error('❌ Error stopping occupancy analysis:', error);
    }
  }, [occupancyService, loadOccupancyAnalysisData]);

  const refreshOccupancyAnalysis = useCallback(async () => {
    try {
      const result = await occupancyService.refreshAnalysis();
      if (result.success) {
        console.log('🔄 Occupancy analysis refreshed');
        await loadOccupancyAnalysisData(); // Refresh data
      } else {
        console.error('❌ Failed to refresh occupancy analysis:', result.message);
      }
    } catch (error) {
      console.error('❌ Error refreshing occupancy analysis:', error);
    }
  }, [occupancyService, loadOccupancyAnalysisData]);

  const toggleSubscription = useCallback((subscriptionId: string) => {
    setSubscriptions(prev => prev.map(sub => 
      sub.id === subscriptionId 
        ? { 
            ...sub, 
            status: sub.status === 'active' ? 'paused' : 'active',
            lastUpdate: sub.status === 'paused' ? new Date() : sub.lastUpdate
          }
        : sub
    ));
  }, []);

  const deleteSubscription = useCallback((subscriptionId: string) => {
    setSubscriptions(prev => prev.filter(sub => sub.id !== subscriptionId));
  }, []);

  const addSubscription = useCallback(() => {
    if (!newSubscription.name || !newSubscription.endpoint) return;

    const subscription: Subscription = {
      id: `sub-${Date.now()}`,
      name: newSubscription.name,
      endpoint: newSubscription.endpoint,
      type: newSubscription.type,
      status: 'active',
      lastUpdate: new Date(),
      messageCount: 0,
      errorCount: 0
    };

    setSubscriptions(prev => [...prev, subscription]);
    setNewSubscription({ name: '', endpoint: '', type: 'vehicles' });
    setShowAddForm(false);
  }, [newSubscription]);

  const unsubscribeFromFeed = useCallback(async (subscriptionId: string, feedType: string) => {
    const subscription = subscriptions.find(s => s.id === subscriptionId);
    if (!subscription) return;

    // Simulate API call to unsubscribe
    try {
      let unsubscribeCommand = '';
      let description = '';

      if (feedType === 'rail-comm') {
        unsubscribeCommand = './rtd-control.sh rail-comm unsubscribe-all';
        description = 'Unsubscribing from all rail communication endpoints...';
      } else if (feedType === 'bus-siri') {
        unsubscribeCommand = './scripts/bus-siri-subscribe.sh unsubscribe';
        description = 'Unsubscribing from SIRI bus feed...';
      }

      console.log(`Executing: ${unsubscribeCommand}`);
      console.log(description);

      // Simulate API call delay
      await new Promise(resolve => setTimeout(resolve, 1500));

      // Update subscription status to paused/unsubscribed
      setSubscriptions(prev => prev.map(sub => 
        sub.id === subscriptionId 
          ? { 
              ...sub, 
              status: 'paused' as const,
              lastUpdate: new Date()
            }
          : sub
      ));

      // Show success message (in a real app, you'd use a toast notification)
      alert(`Successfully unsubscribed from ${subscription.name}`);

    } catch (error) {
      console.error('Failed to unsubscribe:', error);
      alert(`Failed to unsubscribe from ${subscription.name}`);
    }
  }, [subscriptions]);

  const unsubscribeAll = useCallback(async () => {
    const railCommSubs = subscriptions.filter(s => s.type === 'rail-comm');
    const busSiriSubs = subscriptions.filter(s => s.type === 'bus-siri');
    
    if (railCommSubs.length === 0 && busSiriSubs.length === 0) {
      alert('No rail communication or bus SIRI subscriptions to unsubscribe from.');
      return;
    }

    const confirmed = window.confirm(
      `Are you sure you want to unsubscribe from all rail communication and bus SIRI feeds? This will affect ${railCommSubs.length + busSiriSubs.length} subscription(s).`
    );

    if (!confirmed) return;

    try {
      // Unsubscribe from rail comm feeds
      if (railCommSubs.length > 0) {
        console.log('Executing: ./rtd-control.sh rail-comm unsubscribe-all');
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // Unsubscribe from bus SIRI feeds
      if (busSiriSubs.length > 0) {
        console.log('Executing: ./scripts/bus-siri-subscribe.sh unsubscribe');
        await new Promise(resolve => setTimeout(resolve, 1000));
      }

      // Update all relevant subscriptions
      setSubscriptions(prev => prev.map(sub => 
        (sub.type === 'rail-comm' || sub.type === 'bus-siri')
          ? { 
              ...sub, 
              status: 'paused' as const,
              lastUpdate: new Date()
            }
          : sub
      ));

      alert(`Successfully unsubscribed from ${railCommSubs.length + busSiriSubs.length} feed(s)`);

    } catch (error) {
      console.error('Failed to unsubscribe from feeds:', error);
      alert('Failed to unsubscribe from some feeds. Check console for details.');
    }
  }, [subscriptions]);

  const subscribeToFeed = useCallback(async (subscriptionId: string, feedType: string) => {
    const subscription = subscriptions.find(s => s.id === subscriptionId);
    if (!subscription) return;

    // Simulate API call to subscribe
    try {
      let subscribeCommand = '';
      let description = '';

      if (feedType === 'rail-comm') {
        subscribeCommand = './rtd-control.sh rail-comm subscribe-bridge';
        description = 'Subscribing to rail communication proxy feed (Direct Kafka Bridge)...';
      } else if (feedType === 'bus-siri') {
        subscribeCommand = './scripts/bus-siri-subscribe.sh';
        description = 'Subscribing to SIRI bus feed...';
      }

      console.log(`Executing: ${subscribeCommand}`);
      console.log(description);

      // Simulate API call delay
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Update subscription status to active/subscribed
      setSubscriptions(prev => prev.map(sub => 
        sub.id === subscriptionId 
          ? { 
              ...sub, 
              status: 'active' as const,
              lastUpdate: new Date(),
              messageCount: sub.messageCount + Math.floor(Math.random() * 10)
            }
          : sub
      ));

      // Show success message
      alert(`Successfully subscribed to ${subscription.name}`);

    } catch (error) {
      console.error('Failed to subscribe:', error);
      alert(`Failed to subscribe to ${subscription.name}`);
    }
  }, [subscriptions]);

  const subscribeToRailComm = useCallback(async (endpoint: string = 'bridge') => {
    try {
      let command = '';
      let description = '';
      
      switch (endpoint) {
        case 'original':
          command = './rtd-control.sh rail-comm subscribe';
          description = 'Subscribing to original rail communication endpoint...';
          break;
        case 'bridge':
          command = './rtd-control.sh rail-comm subscribe-bridge';
          description = 'Subscribing to Direct Kafka Bridge endpoint...';
          break;
        case 'kafka':
          command = './rtd-control.sh rail-comm subscribe-kafka';
          description = 'Subscribing to direct Kafka endpoint...';
          break;
        default:
          command = './rtd-control.sh rail-comm subscribe-bridge';
          description = 'Subscribing to rail communication (default: bridge)...';
      }

      console.log(`Executing: ${command}`);
      console.log(description);

      await new Promise(resolve => setTimeout(resolve, 2000));

      // Create or update rail comm subscription
      const existingRailComm = subscriptions.find(s => s.type === 'rail-comm');
      if (existingRailComm) {
        setSubscriptions(prev => prev.map(sub => 
          sub.type === 'rail-comm' 
            ? { 
                ...sub, 
                status: 'active' as const,
                lastUpdate: new Date(),
                messageCount: sub.messageCount + Math.floor(Math.random() * 20)
              }
            : sub
        ));
      } else {
        // Create new subscription
        const newSubscription: Subscription = {
          id: `rail-comm-${Date.now()}`,
          name: `RTD Rail Communication (${endpoint})`,
          endpoint: `ws://localhost:8080/rail-comm-${endpoint}`,
          type: 'rail-comm',
          status: 'active',
          lastUpdate: new Date(),
          messageCount: Math.floor(Math.random() * 50),
          errorCount: 0
        };
        setSubscriptions(prev => [...prev, newSubscription]);
      }

      alert(`Successfully subscribed to rail communication (${endpoint} endpoint)`);

    } catch (error) {
      console.error('Failed to subscribe to rail communication:', error);
      alert('Failed to subscribe to rail communication');
    }
  }, [subscriptions]);

  const subscribeToBusSiri = useCallback(async (host: string = 'localhost', service: string = 'StopMonitoring', ttl: string = '3600') => {
    try {
      const command = `./scripts/bus-siri-subscribe.sh ${host} ${service} ${ttl}`;
      const description = `Subscribing to SIRI bus feed (${service} on ${host})...`;

      console.log(`Executing: ${command}`);
      console.log(description);

      await new Promise(resolve => setTimeout(resolve, 2000));

      // Create or update bus SIRI subscription
      const existingBusSiri = subscriptions.find(s => s.type === 'bus-siri');
      if (existingBusSiri) {
        setSubscriptions(prev => prev.map(sub => 
          sub.type === 'bus-siri' 
            ? { 
                ...sub, 
                status: 'active' as const,
                lastUpdate: new Date(),
                messageCount: sub.messageCount + Math.floor(Math.random() * 15)
              }
            : sub
        ));
      } else {
        // Create new subscription
        const newSubscription: Subscription = {
          id: `bus-siri-${Date.now()}`,
          name: `RTD Bus SIRI Feed (${service})`,
          endpoint: `http://${host}:8082/siri/${service}`,
          type: 'bus-siri',
          status: 'active',
          lastUpdate: new Date(),
          messageCount: Math.floor(Math.random() * 30),
          errorCount: 0
        };
        setSubscriptions(prev => [...prev, newSubscription]);
      }

      alert(`Successfully subscribed to bus SIRI feed (${service})`);

    } catch (error) {
      console.error('Failed to subscribe to bus SIRI:', error);
      alert('Failed to subscribe to bus SIRI feed');
    }
  }, [subscriptions]);

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'active':
      case 'healthy':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'paused':
      case 'warning':
        return <AlertTriangle className="w-4 h-4 text-yellow-500" />;
      case 'error':
        return <XCircle className="w-4 h-4 text-red-500" />;
      default:
        return <Clock className="w-4 h-4 text-gray-400" />;
    }
  };

  const getHealthColor = (health: string) => {
    switch (health) {
      case 'healthy': return 'text-green-600 bg-green-50 border-green-200';
      case 'warning': return 'text-yellow-600 bg-yellow-50 border-yellow-200';
      case 'error': return 'text-red-600 bg-red-50 border-red-200';
      default: return 'text-gray-600 bg-gray-50 border-gray-200';
    }
  };

  const getMessageTypeColor = (type: string) => {
    switch (type) {
      case 'position': return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'status': return 'bg-green-100 text-green-800 border-green-200';
      case 'alert': return 'bg-red-100 text-red-800 border-red-200';
      case 'heartbeat': return 'bg-gray-100 text-gray-800 border-gray-200';
      default: return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const getBusSiriMessageTypeColor = (type: string) => {
    switch (type) {
      case 'stop_monitoring': return 'bg-green-100 text-green-800 border-green-200';
      case 'vehicle_monitoring': return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'subscription_response': return 'bg-purple-100 text-purple-800 border-purple-200';
      case 'heartbeat': return 'bg-gray-100 text-gray-800 border-gray-200';
      default: return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const getLrgpsMessageTypeColor = (type: string) => {
    switch (type) {
      case 'vehicle_monitoring': return 'bg-blue-100 text-blue-800 border-blue-200';
      case 'position_update': return 'bg-green-100 text-green-800 border-green-200';
      case 'subscription_response': return 'bg-purple-100 text-purple-800 border-purple-200';
      case 'heartbeat': return 'bg-gray-100 text-gray-800 border-gray-200';
      default: return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const getErrorTypeColor = (errorType: string) => {
    switch (errorType) {
      case 'connection': return 'bg-red-100 text-red-800 border-red-200';
      case 'parsing': return 'bg-orange-100 text-orange-800 border-orange-200';
      case 'validation': return 'bg-yellow-100 text-yellow-800 border-yellow-200';
      case 'timeout': return 'bg-purple-100 text-purple-800 border-purple-200';
      case 'authentication': return 'bg-pink-100 text-pink-800 border-pink-200';
      case 'rate_limit': return 'bg-indigo-100 text-indigo-800 border-indigo-200';
      default: return 'bg-gray-100 text-gray-800 border-gray-200';
    }
  };

  const getSeverityColor = (severity: string) => {
    switch (severity) {
      case 'critical': return 'bg-red-600 text-white';
      case 'high': return 'bg-red-400 text-white';
      case 'medium': return 'bg-yellow-400 text-black';
      case 'low': return 'bg-green-400 text-white';
      default: return 'bg-gray-400 text-white';
    }
  };

  const getErrorTypeIcon = (errorType: string) => {
    switch (errorType) {
      case 'connection': return <Wifi className="w-4 h-4" />;
      case 'parsing': return <Bug className="w-4 h-4" />;
      case 'validation': return <Shield className="w-4 h-4" />;
      case 'timeout': return <Clock className="w-4 h-4" />;
      case 'authentication': return <AlertOctagon className="w-4 h-4" />;
      case 'rate_limit': return <Zap className="w-4 h-4" />;
      default: return <AlertTriangle className="w-4 h-4" />;
    }
  };

  const sortedErrorMessages = [...errorMessages].sort((a, b) => {
    switch (errorSortBy) {
      case 'type':
        return a.errorType.localeCompare(b.errorType);
      case 'count':
        return b.count - a.count;
      case 'severity':
        const severityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
        return severityOrder[b.severity] - severityOrder[a.severity];
      case 'timestamp':
        return b.timestamp.getTime() - a.timestamp.getTime();
      default:
        return b.count - a.count;
    }
  });

  const errorTypeStats = errorMessages.reduce((acc, error) => {
    acc[error.errorType] = (acc[error.errorType] || 0) + error.count;
    return acc;
  }, {} as Record<string, number>);

  return (
    <div className="min-h-screen bg-gray-50 p-6">
      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-8">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Settings className="w-8 h-8 text-rtd-primary" />
              <div>
                <h1 className="text-3xl font-bold text-gray-900">Admin Dashboard</h1>
                <p className="text-gray-600">RTD Live Transit Map Application</p>
              </div>
            </div>
            
            <button
              onClick={refreshData}
              disabled={isRefreshing}
              className="flex items-center space-x-2 px-4 py-2 bg-rtd-primary text-white rounded-lg hover:bg-rtd-dark disabled:opacity-50 transition-colors"
            >
              <RefreshCw className={`w-4 h-4 ${isRefreshing ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        {/* Stats Overview */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <div className="bg-white rounded-lg p-6 shadow-sm border">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">Active Subscriptions</p>
                <p className="text-2xl font-bold text-gray-900">
                  {subscriptions.filter(s => s.status === 'active').length}
                </p>
              </div>
              <Users className="w-8 h-8 text-blue-500" />
            </div>
          </div>

          <div className="bg-white rounded-lg p-6 shadow-sm border">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">Live Feeds</p>
                <p className="text-2xl font-bold text-gray-900">
                  {feedStatuses.filter(f => f.isLive).length}
                </p>
              </div>
              <Activity className="w-8 h-8 text-green-500" />
            </div>
          </div>

          <div className="bg-white rounded-lg p-6 shadow-sm border">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">Total Messages</p>
                <p className="text-2xl font-bold text-gray-900">
                  {subscriptions.reduce((sum, s) => sum + s.messageCount, 0).toLocaleString()}
                </p>
              </div>
              <Database className="w-8 h-8 text-purple-500" />
            </div>
          </div>

          <div className="bg-white rounded-lg p-6 shadow-sm border">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-600">Error Count</p>
                <p className="text-2xl font-bold text-gray-900">
                  {subscriptions.reduce((sum, s) => sum + s.errorCount, 0)}
                </p>
              </div>
              <Bell className="w-8 h-8 text-red-500" />
            </div>
          </div>
        </div>

        {/* Feed Metrics Panel */}
        <div className="mb-8">
          <FeedMetricsPanel />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Subscription Management */}
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-semibold text-gray-900">Subscription Management</h2>
                <div className="flex items-center space-x-3">
                  <button
                    onClick={() => subscribeToRailComm('bridge')}
                    className="flex items-center space-x-2 px-3 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors"
                    title="Quick subscribe to rail communication (bridge endpoint)"
                  >
                    <UserPlus className="w-4 h-4" />
                    <span>Subscribe Rail</span>
                  </button>
                  <button
                    onClick={() => subscribeToBusSiri()}
                    className="flex items-center space-x-2 px-3 py-2 bg-emerald-600 text-white rounded-md hover:bg-emerald-700 transition-colors"
                    title="Quick subscribe to bus SIRI feed"
                  >
                    <Radio className="w-4 h-4" />
                    <span>Subscribe Bus</span>
                  </button>
                  <button
                    onClick={unsubscribeAll}
                    className="flex items-center space-x-2 px-3 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                    title="Unsubscribe from all rail communication and bus SIRI feeds"
                  >
                    <UserX className="w-4 h-4" />
                    <span>Unsubscribe All</span>
                  </button>
                  <button
                    onClick={() => setShowAddForm(!showAddForm)}
                    className="flex items-center space-x-2 px-3 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
                  >
                    <Plus className="w-4 h-4" />
                    <span>Add</span>
                  </button>
                </div>
              </div>
              
              {/* Quick Stats */}
              <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4">
                <div className="text-center p-3 bg-blue-50 rounded-lg border border-blue-200">
                  <div className="text-lg font-bold text-blue-900">
                    {subscriptions.filter(s => s.type === 'rail-comm').length}
                  </div>
                  <div className="text-xs text-blue-700">Rail Comm</div>
                </div>
                <div className="text-center p-3 bg-green-50 rounded-lg border border-green-200">
                  <div className="text-lg font-bold text-green-900">
                    {subscriptions.filter(s => s.type === 'bus-siri').length}
                  </div>
                  <div className="text-xs text-green-700">Bus SIRI</div>
                </div>
                <div className="text-center p-3 bg-yellow-50 rounded-lg border border-yellow-200">
                  <div className="text-lg font-bold text-yellow-900">
                    {subscriptions.filter(s => s.status === 'active' && (s.type === 'rail-comm' || s.type === 'bus-siri')).length}
                  </div>
                  <div className="text-xs text-yellow-700">Active</div>
                </div>
                <div className="text-center p-3 bg-gray-50 rounded-lg border border-gray-200">
                  <div className="text-lg font-bold text-gray-900">
                    {subscriptions.filter(s => s.status === 'paused' && (s.type === 'rail-comm' || s.type === 'bus-siri')).length}
                  </div>
                  <div className="text-xs text-gray-700">Unsubscribed</div>
                </div>
              </div>
            </div>

            <div className="p-6">
              {showAddForm && (
                <div className="mb-6 p-4 bg-gray-50 rounded-lg border">
                  <h3 className="text-lg font-medium text-gray-900 mb-4">Add New Subscription</h3>
                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Name</label>
                      <input
                        type="text"
                        value={newSubscription.name}
                        onChange={(e) => setNewSubscription(prev => ({ ...prev, name: e.target.value }))}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="Subscription name"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Endpoint</label>
                      <input
                        type="text"
                        value={newSubscription.endpoint}
                        onChange={(e) => setNewSubscription(prev => ({ ...prev, endpoint: e.target.value }))}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                        placeholder="https://api.endpoint.com/feed"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                      <select
                        value={newSubscription.type}
                        onChange={(e) => setNewSubscription(prev => ({ ...prev, type: e.target.value as any }))}
                        className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                      >
                        <option value="vehicles">Vehicle Positions</option>
                        <option value="trip-updates">Trip Updates</option>
                        <option value="alerts">Service Alerts</option>
                        <option value="rail-comm">Rail Communication</option>
                        <option value="bus-siri">Bus SIRI</option>
                      </select>
                    </div>
                    <div className="flex space-x-3">
                      <button
                        onClick={addSubscription}
                        className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors"
                      >
                        Add Subscription
                      </button>
                      <button
                        onClick={() => setShowAddForm(false)}
                        className="px-4 py-2 bg-gray-300 text-gray-700 rounded-md hover:bg-gray-400 transition-colors"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                </div>
              )}

              <div className="space-y-4">
                {subscriptions.map(subscription => (
                  <div key={subscription.id} className="border border-gray-200 rounded-lg p-4">
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center space-x-3">
                        {getStatusIcon(subscription.status)}
                        <div>
                          <h3 className="font-medium text-gray-900">{subscription.name}</h3>
                          <p className="text-sm text-gray-500">{subscription.type.replace('-', ' ')}</p>
                        </div>
                      </div>
                      <div className="flex items-center space-x-2">
                        {(subscription.type === 'rail-comm' || subscription.type === 'bus-siri') && subscription.status !== 'active' && (
                          <button
                            onClick={() => subscribeToFeed(subscription.id, subscription.type)}
                            className="p-2 bg-blue-100 text-blue-600 rounded-md hover:bg-blue-200 transition-colors"
                            title={`Subscribe to ${subscription.type === 'rail-comm' ? 'rail communication' : 'bus SIRI'} feed`}
                          >
                            <Link className="w-4 h-4" />
                          </button>
                        )}
                        {(subscription.type === 'rail-comm' || subscription.type === 'bus-siri') && subscription.status === 'active' && (
                          <button
                            onClick={() => unsubscribeFromFeed(subscription.id, subscription.type)}
                            className="p-2 bg-orange-100 text-orange-600 rounded-md hover:bg-orange-200 transition-colors"
                            title={`Unsubscribe from ${subscription.type === 'rail-comm' ? 'rail communication' : 'bus SIRI'} feed`}
                          >
                            <Unlink className="w-4 h-4" />
                          </button>
                        )}
                        <button
                          onClick={() => toggleSubscription(subscription.id)}
                          className={`p-2 rounded-md transition-colors ${
                            subscription.status === 'active' 
                              ? 'bg-yellow-100 text-yellow-600 hover:bg-yellow-200'
                              : 'bg-green-100 text-green-600 hover:bg-green-200'
                          }`}
                          title={subscription.status === 'active' ? 'Pause subscription' : 'Resume subscription'}
                        >
                          {subscription.status === 'active' ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                        </button>
                        <button
                          onClick={() => deleteSubscription(subscription.id)}
                          className="p-2 bg-red-100 text-red-600 rounded-md hover:bg-red-200 transition-colors"
                          title="Delete subscription"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                    <div className="text-xs text-gray-500 space-y-1">
                      <p>Endpoint: {subscription.endpoint}</p>
                      <div className="flex justify-between">
                        <span>Messages: {subscription.messageCount.toLocaleString()}</span>
                        <span>Errors: {subscription.errorCount}</span>
                        <span>Last Update: {subscription.lastUpdate ? formatDistanceToNow(subscription.lastUpdate) + ' ago' : 'Never'}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Live Feed Monitoring */}
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <h2 className="text-xl font-semibold text-gray-900">Live Feed Monitoring</h2>
            </div>

            <div className="p-6">
              <div className="space-y-4">
                {feedStatuses.map(feed => (
                  <div key={feed.name} className={`border rounded-lg p-4 ${getHealthColor(feed.health)}`}>
                    <div className="flex items-center justify-between mb-3">
                      <div className="flex items-center space-x-3">
                        <div className="flex items-center space-x-2">
                          {feed.isLive ? <Wifi className="w-4 h-4" /> : <Server className="w-4 h-4" />}
                          {getStatusIcon(feed.health)}
                        </div>
                        <div>
                          <h3 className="font-medium">{feed.name}</h3>
                          <p className="text-sm opacity-75">{feed.type.replace('-', ' ')}</p>
                        </div>
                      </div>
                      <button
                        onClick={() => setSelectedFeed(selectedFeed === feed.name ? null : feed.name)}
                        className="p-2 bg-white/50 rounded-md hover:bg-white/75 transition-colors"
                      >
                        <Eye className="w-4 h-4" />
                      </button>
                    </div>
                    
                    <div className="text-xs space-y-1 opacity-90">
                      <div className="flex justify-between">
                        <span>Status: {feed.isLive ? 'Live' : 'Offline'}</span>
                        <span>Rate: {feed.messageRate}/min</span>
                      </div>
                      <p>Last Message: {feed.lastMessage ? formatDistanceToNow(feed.lastMessage) + ' ago' : 'Never'}</p>
                    </div>

                    {selectedFeed === feed.name && feed.sampleData && (
                      <div className="mt-4 p-3 bg-white/50 rounded-md">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-sm font-medium">Sample Data</h4>
                          <Download className="w-4 h-4" />
                        </div>
                        <pre className="text-xs overflow-x-auto whitespace-pre-wrap">
                          {JSON.stringify(feed.sampleData, null, 2)}
                        </pre>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Rail Communication History */}
        <div className="mt-8">
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <Train className="w-6 h-6 text-rtd-primary" />
                  <div>
                    <h2 className="text-xl font-semibold text-gray-900">Rail Communication Bridge</h2>
                    <p className="text-sm text-gray-600">Last 20 message contents</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowRailCommHistory(!showRailCommHistory)}
                  className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition-colors ${
                    showRailCommHistory 
                      ? 'bg-rtd-primary text-white' 
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  <MessageSquare className="w-4 h-4" />
                  <span>{showRailCommHistory ? 'Hide' : 'Show'} Messages</span>
                </button>
              </div>
            </div>

            {showRailCommHistory && (
              <div className="p-6">
                <div className="space-y-4">
                  {railCommMessages.map((message, index) => (
                    <div key={message.id} className="border border-gray-200 rounded-lg p-4 hover:bg-gray-50 transition-colors">
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center space-x-3">
                          <span className="text-xs font-mono text-gray-500">#{index + 1}</span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getMessageTypeColor(message.type)}`}>
                            {message.type.toUpperCase()}
                          </span>
                          <span className="text-sm font-medium text-gray-900">{message.source}</span>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-xs text-gray-500">{message.size} bytes</span>
                          <button
                            onClick={() => copyToClipboard(JSON.stringify(message.content, null, 2))}
                            className="p-1 text-gray-400 hover:text-gray-600 transition-colors"
                            title="Copy message content"
                          >
                            <Copy className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      <div className="mb-3">
                        <div className="flex items-center justify-between text-xs text-gray-600">
                          <span>Timestamp: {message.timestamp.toLocaleString()}</span>
                          <span>{formatDistanceToNow(message.timestamp)} ago</span>
                        </div>
                        <div className="mt-1 text-xs text-gray-500">
                          <span className="font-medium">Source URL:</span>
                          <span className="ml-1 font-mono bg-gray-100 px-1 rounded">{message.sourceUrl}</span>
                        </div>
                        {message.proxyUrl && (
                          <div className="mt-1 text-xs text-gray-500">
                            <span className="font-medium">Proxy URL:</span>
                            <span className="ml-1 font-mono bg-blue-50 px-1 rounded text-blue-700">{message.proxyUrl}</span>
                          </div>
                        )}
                      </div>

                      <div className="bg-gray-50 rounded-md p-3">
                        <h4 className="text-sm font-medium text-gray-900 mb-2">Message Content</h4>
                        <pre className="text-xs text-gray-700 overflow-x-auto whitespace-pre-wrap bg-white rounded border p-2">
                          {JSON.stringify(message.content, null, 2)}
                        </pre>
                      </div>

                      {/* Message Summary for quick viewing */}
                      <div className="mt-3 pt-3 border-t border-gray-100">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
                          {message.content.train_id && (
                            <div>
                              <span className="font-medium text-gray-600">Train:</span>
                              <span className="ml-1 text-gray-900">{message.content.train_id}</span>
                            </div>
                          )}
                          {message.content.route && (
                            <div>
                              <span className="font-medium text-gray-600">Route:</span>
                              <span className="ml-1 text-gray-900">{message.content.route}</span>
                            </div>
                          )}
                          {message.content.speed !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Speed:</span>
                              <span className="ml-1 text-gray-900">{message.content.speed} mph</span>
                            </div>
                          )}
                          {message.content.status && (
                            <div>
                              <span className="font-medium text-gray-600">Status:</span>
                              <span className="ml-1 text-gray-900">{message.content.status}</span>
                            </div>
                          )}
                          {message.content.delay_seconds !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Delay:</span>
                              <span className="ml-1 text-gray-900">{message.content.delay_seconds}s</span>
                            </div>
                          )}
                          {message.content.passengers !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Passengers:</span>
                              <span className="ml-1 text-gray-900">{message.content.passengers}</span>
                            </div>
                          )}
                          {message.content.current_station && (
                            <div>
                              <span className="font-medium text-gray-600">Station:</span>
                              <span className="ml-1 text-gray-900">{message.content.current_station}</span>
                            </div>
                          )}
                          {message.content.alert_type && (
                            <div>
                              <span className="font-medium text-gray-600">Alert:</span>
                              <span className="ml-1 text-gray-900">{message.content.alert_type}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Footer with stats */}
                <div className="mt-6 pt-4 border-t border-gray-200">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {railCommMessages.filter(m => m.type === 'position').length}
                      </div>
                      <div className="text-gray-600">Position Updates</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {railCommMessages.filter(m => m.type === 'status').length}
                      </div>
                      <div className="text-gray-600">Status Messages</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {railCommMessages.filter(m => m.type === 'alert').length}
                      </div>
                      <div className="text-gray-600">Alerts</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {railCommMessages.filter(m => m.type === 'heartbeat').length}
                      </div>
                      <div className="text-gray-600">Heartbeats</div>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Bus SIRI Feed History */}
        <div className="mt-8">
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <Users className="w-6 h-6 text-green-600" />
                  <div>
                    <h2 className="text-xl font-semibold text-gray-900">RTD Bus SIRI Feed</h2>
                    <p className="text-sm text-gray-600">Last 20 message contents</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowBusSiriHistory(!showBusSiriHistory)}
                  className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition-colors ${
                    showBusSiriHistory 
                      ? 'bg-green-600 text-white' 
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  <MessageSquare className="w-4 h-4" />
                  <span>{showBusSiriHistory ? 'Hide' : 'Show'} Messages</span>
                </button>
              </div>
            </div>

            {showBusSiriHistory && (
              <div className="p-6">
                <div className="space-y-4">
                  {busSiriMessages.map((message, index) => (
                    <div key={message.id} className="border border-gray-200 rounded-lg p-4 hover:bg-gray-50 transition-colors">
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center space-x-3">
                          <span className="text-xs font-mono text-gray-500">#{index + 1}</span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getBusSiriMessageTypeColor(message.type)}`}>
                            {message.type.replace('_', ' ').toUpperCase()}
                          </span>
                          <span className="text-sm font-medium text-gray-900">{message.source}</span>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-xs text-gray-500">{message.size} bytes</span>
                          <button
                            onClick={() => copyToClipboard(JSON.stringify(message.content, null, 2))}
                            className="p-1 text-gray-400 hover:text-gray-600 transition-colors"
                            title="Copy message content"
                          >
                            <Copy className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      <div className="mb-3">
                        <div className="flex items-center justify-between text-xs text-gray-600">
                          <span>Timestamp: {message.timestamp.toLocaleString()}</span>
                          <span>{formatDistanceToNow(message.timestamp)} ago</span>
                        </div>
                        <div className="mt-1 text-xs text-gray-500">
                          <span className="font-medium">Source URL:</span>
                          <span className="ml-1 font-mono bg-gray-100 px-1 rounded">{message.sourceUrl}</span>
                        </div>
                        {message.proxyUrl && (
                          <div className="mt-1 text-xs text-gray-500">
                            <span className="font-medium">Proxy URL:</span>
                            <span className="ml-1 font-mono bg-green-50 px-1 rounded text-green-700">{message.proxyUrl}</span>
                          </div>
                        )}
                      </div>

                      <div className="bg-gray-50 rounded-md p-3">
                        <h4 className="text-sm font-medium text-gray-900 mb-2">Message Content</h4>
                        <pre className="text-xs text-gray-700 overflow-x-auto whitespace-pre-wrap bg-white rounded border p-2">
                          {JSON.stringify(message.content, null, 2)}
                        </pre>
                      </div>

                      {/* Message Summary for quick viewing */}
                      <div className="mt-3 pt-3 border-t border-gray-100">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
                          {message.content.stop_point_ref && (
                            <div>
                              <span className="font-medium text-gray-600">Stop:</span>
                              <span className="ml-1 text-gray-900">{message.content.stop_point_ref}</span>
                            </div>
                          )}
                          {message.content.stop_name && (
                            <div>
                              <span className="font-medium text-gray-600">Stop Name:</span>
                              <span className="ml-1 text-gray-900">{message.content.stop_name}</span>
                            </div>
                          )}
                          {message.content.vehicle_activity?.vehicle_ref && (
                            <div>
                              <span className="font-medium text-gray-600">Vehicle:</span>
                              <span className="ml-1 text-gray-900">{message.content.vehicle_activity.vehicle_ref}</span>
                            </div>
                          )}
                          {message.content.vehicle_activity?.line_ref && (
                            <div>
                              <span className="font-medium text-gray-600">Route:</span>
                              <span className="ml-1 text-gray-900">{message.content.vehicle_activity.line_ref}</span>
                            </div>
                          )}
                          {message.content.monitored_stop_visit?.vehicle_journey?.line_ref && (
                            <div>
                              <span className="font-medium text-gray-600">Route:</span>
                              <span className="ml-1 text-gray-900">{message.content.monitored_stop_visit.vehicle_journey.line_ref}</span>
                            </div>
                          )}
                          {message.content.vehicle_activity?.speed !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Speed:</span>
                              <span className="ml-1 text-gray-900">{message.content.vehicle_activity.speed} mph</span>
                            </div>
                          )}
                          {message.content.vehicle_activity?.passenger_count !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Passengers:</span>
                              <span className="ml-1 text-gray-900">{message.content.vehicle_activity.passenger_count}</span>
                            </div>
                          )}
                          {message.content.vehicle_activity?.occupancy && (
                            <div>
                              <span className="font-medium text-gray-600">Occupancy:</span>
                              <span className="ml-1 text-gray-900">{message.content.vehicle_activity.occupancy.replace('_', ' ')}</span>
                            </div>
                          )}
                          {message.content.monitored_stop_visit?.monitoring_info?.delay !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Delay:</span>
                              <span className="ml-1 text-gray-900">{message.content.monitored_stop_visit.monitoring_info.delay}s</span>
                            </div>
                          )}
                          {message.content.subscription_ref && (
                            <div>
                              <span className="font-medium text-gray-600">Subscription:</span>
                              <span className="ml-1 text-gray-900">{message.content.subscription_ref}</span>
                            </div>
                          )}
                          {message.content.response_status && (
                            <div>
                              <span className="font-medium text-gray-600">Status:</span>
                              <span className="ml-1 text-gray-900">{message.content.response_status}</span>
                            </div>
                          )}
                          {message.content.service_status && (
                            <div>
                              <span className="font-medium text-gray-600">Service:</span>
                              <span className="ml-1 text-gray-900">{message.content.service_status}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Footer with stats */}
                <div className="mt-6 pt-4 border-t border-gray-200">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {busSiriMessages.filter(m => m.type === 'stop_monitoring').length}
                      </div>
                      <div className="text-gray-600">Stop Monitoring</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {busSiriMessages.filter(m => m.type === 'vehicle_monitoring').length}
                      </div>
                      <div className="text-gray-600">Vehicle Monitoring</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {busSiriMessages.filter(m => m.type === 'subscription_response').length}
                      </div>
                      <div className="text-gray-600">Subscription Responses</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {busSiriMessages.filter(m => m.type === 'heartbeat').length}
                      </div>
                      <div className="text-gray-600">Heartbeats</div>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* LRGPS Messages History */}
        <div className="mt-8">
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <Train className="w-6 h-6 text-blue-600" />
                  <div>
                    <h2 className="text-xl font-semibold text-gray-900">RTD LRGPS Feed</h2>
                    <p className="text-sm text-gray-600">Last 20 light rail message contents</p>
                  </div>
                </div>
                <button
                  onClick={() => setShowLrgpsHistory(!showLrgpsHistory)}
                  className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition-colors ${
                    showLrgpsHistory 
                      ? 'bg-blue-600 text-white' 
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  <MessageSquare className="w-4 h-4" />
                  <span>{showLrgpsHistory ? 'Hide' : 'Show'} Messages</span>
                </button>
              </div>
            </div>

            {showLrgpsHistory && (
              <div className="p-6">
                {lrgpsMessages.length === 0 ? (
                  <div className="text-center text-gray-500 py-8">
                    <MessageSquare className="w-12 h-12 mx-auto mb-3 text-gray-400" />
                    <p>No LRGPS messages available</p>
                  </div>
                ) : (
                  <>
                    <div className="grid grid-cols-4 gap-4 mb-6 text-center">
                      <div className="bg-blue-50 rounded-lg p-3">
                        <div className="font-semibold text-gray-900">
                          {lrgpsMessages.filter(m => m.type === 'vehicle_monitoring').length}
                        </div>
                        <div className="text-gray-600">Vehicle Monitoring</div>
                      </div>
                      <div className="bg-green-50 rounded-lg p-3">
                        <div className="font-semibold text-gray-900">
                          {lrgpsMessages.filter(m => m.type === 'position_update').length}
                        </div>
                        <div className="text-gray-600">Position Updates</div>
                      </div>
                      <div className="bg-purple-50 rounded-lg p-3">
                        <div className="font-semibold text-gray-900">
                          {lrgpsMessages.filter(m => m.type === 'subscription_response').length}
                        </div>
                        <div className="text-gray-600">Subscriptions</div>
                      </div>
                      <div className="bg-gray-50 rounded-lg p-3">
                        <div className="font-semibold text-gray-900">
                          {lrgpsMessages.filter(m => m.type === 'heartbeat').length}
                        </div>
                        <div className="text-gray-600">Heartbeats</div>
                      </div>
                    </div>

                    <div className="space-y-3">
                      {lrgpsMessages.slice(0, 20).map((message) => (
                        <div
                          key={message.id}
                          className="border border-gray-200 rounded-lg p-4 hover:border-blue-300 transition-colors"
                        >
                          <div className="flex items-start justify-between mb-3">
                            <div className="flex items-center space-x-3">
                              <span
                                className={`px-2 py-1 rounded text-xs font-medium border ${getLrgpsMessageTypeColor(message.type)}`}
                              >
                                {message.type}
                              </span>
                              <span className="text-xs text-gray-500">
                                {formatDistanceToNow(message.timestamp)} ago
                              </span>
                              <span className="text-xs text-gray-400">
                                {message.size} bytes
                              </span>
                            </div>
                            <button
                              onClick={() => copyToClipboard(JSON.stringify(message.content, null, 2))}
                              className="text-gray-400 hover:text-gray-600 transition-colors"
                              title="Copy message content"
                            >
                              <Copy className="w-4 h-4" />
                            </button>
                          </div>
                          <div className="text-sm text-gray-700 mb-2">
                            <span className="font-medium">Source:</span> {message.source} 
                            <span className="mx-2 text-gray-400">•</span>
                            <span className="font-medium">URL:</span> {message.sourceUrl}
                          </div>
                          <div className="bg-gray-50 rounded-md p-3">
                            <pre className="text-xs text-gray-800 overflow-x-auto whitespace-pre-wrap">
                              {JSON.stringify(message.content, null, 2)}
                            </pre>
                          </div>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Error Messages History */}
        <div className="mt-8">
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <AlertOctagon className="w-6 h-6 text-red-500" />
                  <div>
                    <h2 className="text-xl font-semibold text-gray-900">Error Messages</h2>
                    <p className="text-sm text-gray-600">All system errors sorted by type and count</p>
                  </div>
                </div>
                <div className="flex items-center space-x-3">
                  <div className="flex items-center space-x-2">
                    <Filter className="w-4 h-4 text-gray-500" />
                    <select
                      value={errorSortBy}
                      onChange={(e) => setErrorSortBy(e.target.value as any)}
                      className="text-sm border border-gray-300 rounded-md px-2 py-1 focus:outline-none focus:ring-2 focus:ring-blue-500"
                    >
                      <option value="count">Sort by Count</option>
                      <option value="type">Sort by Type</option>
                      <option value="severity">Sort by Severity</option>
                      <option value="timestamp">Sort by Time</option>
                    </select>
                  </div>
                  <button
                    onClick={() => setShowErrorHistory(!showErrorHistory)}
                    className={`flex items-center space-x-2 px-4 py-2 rounded-lg transition-colors ${
                      showErrorHistory 
                        ? 'bg-red-500 text-white' 
                        : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                    }`}
                  >
                    <AlertTriangle className="w-4 h-4" />
                    <span>{showErrorHistory ? 'Hide' : 'Show'} Errors</span>
                  </button>
                </div>
              </div>

              {/* Error Type Statistics */}
              <div className="mt-4 grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
                {Object.entries(errorTypeStats)
                  .sort(([,a], [,b]) => b - a)
                  .map(([type, count]) => (
                    <div key={type} className={`p-3 rounded-lg border ${getErrorTypeColor(type)}`}>
                      <div className="flex items-center space-x-2 mb-1">
                        {getErrorTypeIcon(type)}
                        <span className="text-xs font-medium uppercase">{type.replace('_', ' ')}</span>
                      </div>
                      <div className="text-lg font-bold">{count}</div>
                      <div className="text-xs opacity-75">total errors</div>
                    </div>
                  ))}
              </div>
            </div>

            {showErrorHistory && (
              <div className="p-6">
                <div className="space-y-4">
                  {sortedErrorMessages.map((error, index) => (
                    <div key={error.id} className={`border rounded-lg p-4 transition-colors ${
                      error.resolved ? 'bg-green-50 border-green-200' : 'bg-white border-gray-200 hover:bg-gray-50'
                    }`}>
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center space-x-3">
                          <span className="text-xs font-mono text-gray-500">#{index + 1}</span>
                          <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getErrorTypeColor(error.errorType)}`}>
                            {error.errorType.replace('_', ' ').toUpperCase()}
                          </span>
                          <span className={`px-2 py-1 rounded text-xs font-bold ${getSeverityColor(error.severity)}`}>
                            {error.severity.toUpperCase()}
                          </span>
                          <span className="text-sm font-medium text-gray-900">{error.source}</span>
                          {error.resolved && (
                            <span className="px-2 py-1 bg-green-100 text-green-800 rounded-full text-xs font-medium">
                              RESOLVED
                            </span>
                          )}
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className={`px-2 py-1 rounded-full text-xs font-bold ${
                            error.count > 15 ? 'bg-red-100 text-red-800' :
                            error.count > 5 ? 'bg-yellow-100 text-yellow-800' :
                            'bg-blue-100 text-blue-800'
                          }`}>
                            {error.count}x
                          </span>
                          <button
                            onClick={() => copyToClipboard(JSON.stringify(error.details, null, 2))}
                            className="p-1 text-gray-400 hover:text-gray-600 transition-colors"
                            title="Copy error details"
                          >
                            <Copy className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      <div className="mb-3">
                        <h3 className="text-sm font-medium text-gray-900 mb-1">{error.message}</h3>
                        <div className="flex items-center justify-between text-xs text-gray-600">
                          <span>First: {error.timestamp.toLocaleString()}</span>
                          <span>Last: {formatDistanceToNow(error.lastOccurrence)} ago</span>
                        </div>
                      </div>

                      <div className="bg-gray-50 rounded-md p-3">
                        <h4 className="text-sm font-medium text-gray-900 mb-2">Error Details</h4>
                        <pre className="text-xs text-gray-700 overflow-x-auto whitespace-pre-wrap bg-white rounded border p-2">
                          {JSON.stringify(error.details, null, 2)}
                        </pre>
                      </div>

                      {/* Error Summary for quick viewing */}
                      <div className="mt-3 pt-3 border-t border-gray-100">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-xs">
                          {error.details.endpoint && (
                            <div>
                              <span className="font-medium text-gray-600">Endpoint:</span>
                              <span className="ml-1 text-gray-900">{error.details.endpoint}</span>
                            </div>
                          )}
                          {error.details.httpStatus && (
                            <div>
                              <span className="font-medium text-gray-600">HTTP Status:</span>
                              <span className="ml-1 text-gray-900">{error.details.httpStatus}</span>
                            </div>
                          )}
                          {error.details.attemptCount && (
                            <div>
                              <span className="font-medium text-gray-600">Attempts:</span>
                              <span className="ml-1 text-gray-900">{error.details.attemptCount}</span>
                            </div>
                          )}
                          {error.details.topic && (
                            <div>
                              <span className="font-medium text-gray-600">Topic:</span>
                              <span className="ml-1 text-gray-900">{error.details.topic}</span>
                            </div>
                          )}
                          {error.details.partition !== undefined && (
                            <div>
                              <span className="font-medium text-gray-600">Partition:</span>
                              <span className="ml-1 text-gray-900">{error.details.partition}</span>
                            </div>
                          )}
                          {error.details.offset && (
                            <div>
                              <span className="font-medium text-gray-600">Offset:</span>
                              <span className="ml-1 text-gray-900">{error.details.offset}</span>
                            </div>
                          )}
                          {error.details.trainId && (
                            <div>
                              <span className="font-medium text-gray-600">Train ID:</span>
                              <span className="ml-1 text-gray-900">{error.details.trainId}</span>
                            </div>
                          )}
                          {error.details.fileName && (
                            <div>
                              <span className="font-medium text-gray-600">File:</span>
                              <span className="ml-1 text-gray-900">{error.details.fileName}</span>
                            </div>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Footer with error statistics */}
                <div className="mt-6 pt-4 border-t border-gray-200">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                    <div className="text-center">
                      <div className="font-semibold text-red-600">
                        {errorMessages.filter(e => !e.resolved).length}
                      </div>
                      <div className="text-gray-600">Unresolved</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-green-600">
                        {errorMessages.filter(e => e.resolved).length}
                      </div>
                      <div className="text-gray-600">Resolved</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-red-600">
                        {errorMessages.filter(e => e.severity === 'critical').length}
                      </div>
                      <div className="text-gray-600">Critical</div>
                    </div>
                    <div className="text-center">
                      <div className="font-semibold text-gray-900">
                        {errorMessages.reduce((sum, e) => sum + e.count, 0)}
                      </div>
                      <div className="text-gray-600">Total Count</div>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* RTD Real-Time Vehicle Occupancy Accuracy Analysis */}
        <div className="bg-white rounded-lg shadow-sm border">
          <div className="p-6 border-b border-gray-200">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-3">
                <BarChart3 className="w-6 h-6 text-blue-600" />
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">RTD Real-Time Vehicle Occupancy Accuracy Analysis</h2>
                  <div className="flex items-center space-x-4 mt-1">
                    <p className="text-sm text-gray-600">Live Pipeline Status</p>
                    <div className="flex items-center space-x-2">
                      <div className={`w-2 h-2 rounded-full ${occupancyAnalysisStatus.isRunning ? 'bg-green-500' : 'bg-red-500'}`}></div>
                      <span className={`text-xs font-medium ${occupancyAnalysisStatus.isRunning ? 'text-green-700' : 'text-red-700'}`}>
                        {occupancyAnalysisStatus.isRunning ? 'Running' : 'Stopped'}
                      </span>
                      {occupancyAnalysisStatus.lastUpdate && (
                        <span className="text-xs text-gray-500">
                          • Updated {formatDistanceToNow(occupancyAnalysisStatus.lastUpdate, { addSuffix: true })}
                        </span>
                      )}
                    </div>
                  </div>
                  {occupancyAnalysisStatus.error && (
                    <p className="text-sm text-red-600 mt-1">⚠️ {occupancyAnalysisStatus.error}</p>
                  )}
                </div>
              </div>
              <div className="flex items-center space-x-3">
                {/* Pipeline Control Buttons */}
                {occupancyAnalysisStatus.isRunning ? (
                  <button
                    onClick={stopOccupancyAnalysis}
                    className="flex items-center space-x-2 px-3 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors"
                    title="Stop occupancy analysis pipeline"
                  >
                    <Pause className="w-4 h-4" />
                    <span>Stop</span>
                  </button>
                ) : (
                  <button
                    onClick={startOccupancyAnalysis}
                    className="flex items-center space-x-2 px-3 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors"
                    title="Start occupancy analysis pipeline"
                  >
                    <Play className="w-4 h-4" />
                    <span>Start</span>
                  </button>
                )}
                
                <button
                  onClick={refreshOccupancyAnalysis}
                  className="flex items-center space-x-2 px-3 py-2 bg-purple-600 text-white rounded-md hover:bg-purple-700 transition-colors"
                  title="Refresh occupancy analysis data"
                >
                  <RefreshCw className="w-4 h-4" />
                  <span>Refresh</span>
                </button>

                <select
                  value={occupancyAnalysisFilter}
                  onChange={(e) => setOccupancyAnalysisFilter(e.target.value as any)}
                  className="px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="all">All Categories</option>
                  <option value="overall">Overall</option>
                  <option value="by_date">By Date</option>
                  <option value="by_route">By Route</option>
                </select>
                
                <button
                  onClick={() => setShowOccupancyAnalysis(!showOccupancyAnalysis)}
                  className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                >
                  <Eye className="w-4 h-4" />
                  <span>{showOccupancyAnalysis ? 'Hide' : 'Show'} Analysis</span>
                </button>
              </div>
            </div>

            {/* Key Performance Metrics */}
            <div className="mt-6 grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="bg-gradient-to-r from-blue-50 to-blue-100 p-4 rounded-lg border border-blue-200">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-blue-700">Overall Accuracy</p>
                    <p className="text-2xl font-bold text-blue-900">
                      {occupancyMetrics.find(m => m.category === 'overall')?.accuracyPercentage.toFixed(1) || '0.0'}%
                    </p>
                  </div>
                  <Target className="w-8 h-8 text-blue-600" />
                </div>
              </div>

              <div className="bg-gradient-to-r from-green-50 to-green-100 p-4 rounded-lg border border-green-200">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-green-700">Data Joining Rate</p>
                    <p className="text-2xl font-bold text-green-900">
                      {occupancyMetrics.find(m => m.category === 'overall')?.joinedPercentage.toFixed(1) || '0.0'}%
                    </p>
                  </div>
                  <TrendingUp className="w-8 h-8 text-green-600" />
                </div>
              </div>

              <div className="bg-gradient-to-r from-purple-50 to-purple-100 p-4 rounded-lg border border-purple-200">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-purple-700">Total Records</p>
                    <p className="text-2xl font-bold text-purple-900">
                      {(occupancyMetrics.find(m => m.category === 'overall')?.totalVPRecords || 0).toLocaleString()}
                    </p>
                  </div>
                  <Users2 className="w-8 h-8 text-purple-600" />
                </div>
              </div>

              <div className="bg-gradient-to-r from-amber-50 to-amber-100 p-4 rounded-lg border border-amber-200">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-amber-700">Analysis Period</p>
                    <p className="text-lg font-bold text-amber-900">Aug 15-18</p>
                    <p className="text-xs text-amber-700">2023</p>
                  </div>
                  <Calendar className="w-8 h-8 text-amber-600" />
                </div>
              </div>
            </div>
          </div>

          {showOccupancyAnalysis && (
            <div className="p-6">
              <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
                {/* Accuracy Metrics Table */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-4">Accuracy Metrics by Category</h3>
                  <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-200">
                      <thead className="bg-gray-50">
                        <tr>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Category
                          </th>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            VP Records
                          </th>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Joined %
                          </th>
                          <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                            Accuracy %
                          </th>
                        </tr>
                      </thead>
                      <tbody className="bg-white divide-y divide-gray-200">
                        {occupancyMetrics
                          .filter(metric => occupancyAnalysisFilter === 'all' || metric.category === occupancyAnalysisFilter)
                          .map((metric) => (
                            <tr key={metric.id} className="hover:bg-gray-50">
                              <td className="px-6 py-4 whitespace-nowrap">
                                <div className="flex items-center">
                                  {metric.category === 'overall' && <Target className="w-4 h-4 text-blue-500 mr-2" />}
                                  {metric.category === 'by_date' && <Calendar className="w-4 h-4 text-green-500 mr-2" />}
                                  {metric.category === 'by_route' && <Route className="w-4 h-4 text-purple-500 mr-2" />}
                                  <div>
                                    <div className="text-sm font-medium text-gray-900">{metric.subcategory}</div>
                                    <div className="text-xs text-gray-500 capitalize">{metric.category.replace('_', ' ')}</div>
                                  </div>
                                </div>
                              </td>
                              <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                {metric.totalVPRecords.toLocaleString()}
                              </td>
                              <td className="px-6 py-4 whitespace-nowrap">
                                <div className="flex items-center">
                                  <div className="flex-1">
                                    <div className="text-sm font-medium text-gray-900">
                                      {metric.joinedPercentage.toFixed(1)}%
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2 mt-1">
                                      <div 
                                        className="bg-green-600 h-2 rounded-full" 
                                        style={{ width: `${metric.joinedPercentage}%` }}
                                      ></div>
                                    </div>
                                  </div>
                                </div>
                              </td>
                              <td className="px-6 py-4 whitespace-nowrap">
                                <div className="flex items-center">
                                  <div className="flex-1">
                                    <div className="text-sm font-medium text-gray-900">
                                      {metric.accuracyPercentage.toFixed(1)}%
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2 mt-1">
                                      <div 
                                        className={`h-2 rounded-full ${
                                          metric.accuracyPercentage >= 80 ? 'bg-green-600' :
                                          metric.accuracyPercentage >= 60 ? 'bg-yellow-600' : 'bg-red-600'
                                        }`}
                                        style={{ width: `${metric.accuracyPercentage}%` }}
                                      ></div>
                                    </div>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Vehicle Type Analysis */}
                <div>
                  <h3 className="text-lg font-semibold text-gray-900 mb-4">Vehicle Type Analysis</h3>
                  <div className="space-y-4">
                    {vehicleTypeAnalysis.map((vehicle, index) => (
                      <div key={index} className="bg-gray-50 p-4 rounded-lg border">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="font-medium text-gray-900">{vehicle.vehicleType}</h4>
                          <span className="text-sm text-gray-500">{vehicle.recordCount.toLocaleString()} records</span>
                        </div>
                        <div className="grid grid-cols-2 gap-4 text-sm">
                          <div>
                            <span className="text-gray-600">Seats:</span>
                            <span className="ml-2 font-medium">{vehicle.maxSeats}</span>
                          </div>
                          <div>
                            <span className="text-gray-600">Standing:</span>
                            <span className="ml-2 font-medium">{vehicle.maxStands}</span>
                          </div>
                          <div>
                            <span className="text-gray-600">Total Capacity:</span>
                            <span className="ml-2 font-medium">{vehicle.totalCapacity}</span>
                          </div>
                          <div>
                            <span className="text-gray-600">Avg Occupancy:</span>
                            <span className="ml-2 font-medium">{(vehicle.averageOccupancy * 100).toFixed(1)}%</span>
                          </div>
                        </div>
                        <div className="mt-2">
                          <div className="w-full bg-gray-200 rounded-full h-2">
                            <div 
                              className="bg-blue-600 h-2 rounded-full" 
                              style={{ width: `${vehicle.averageOccupancy * 100}%` }}
                            ></div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* Occupancy Distribution Comparison */}
              <div className="mt-8">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Occupancy Status Distribution Comparison</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  {occupancyDistributions.map((distribution, index) => (
                    <div key={index} className="bg-gray-50 p-4 rounded-lg border">
                      <h4 className="font-medium text-gray-900 mb-3 text-center">
                        {distribution.feedType} Feed
                        <span className="text-sm text-gray-500 ml-2">
                          ({distribution.totalRecords.toLocaleString()} records)
                        </span>
                      </h4>
                      <div className="space-y-2">
                        {[
                          { label: 'Empty', value: distribution.empty, color: 'bg-gray-500' },
                          { label: 'Many Seats Available', value: distribution.manySeatsAvailable, color: 'bg-green-500' },
                          { label: 'Few Seats Available', value: distribution.fewSeatsAvailable, color: 'bg-yellow-500' },
                          { label: 'Standing Room Only', value: distribution.standingRoomOnly, color: 'bg-orange-500' },
                          { label: 'Crushed Standing Room', value: distribution.crushedStandingRoomOnly, color: 'bg-red-500' },
                          { label: 'Full', value: distribution.full, color: 'bg-red-800' }
                        ].map((status) => {
                          const percentage = (status.value / distribution.totalRecords) * 100;
                          return (
                            <div key={status.label} className="flex items-center space-x-3">
                              <div className="w-32 text-xs text-gray-600">{status.label}</div>
                              <div className="flex-1">
                                <div className="flex justify-between text-xs mb-1">
                                  <span>{status.value.toLocaleString()}</span>
                                  <span>{percentage.toFixed(1)}%</span>
                                </div>
                                <div className="w-full bg-gray-200 rounded-full h-2">
                                  <div 
                                    className={`${status.color} h-2 rounded-full`}
                                    style={{ width: `${percentage}%` }}
                                  ></div>
                                </div>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                  ))}
                </div>
              </div>

              {/* Study Information */}
              <div className="mt-8 bg-blue-50 p-4 rounded-lg border border-blue-200">
                <div className="flex items-start space-x-3">
                  <Activity className="w-6 h-6 text-blue-600 mt-1 flex-shrink-0" />
                  <div>
                    <h4 className="font-medium text-blue-900 mb-2">About This Analysis</h4>
                    <p className="text-sm text-blue-800 mb-2">
                      This occupancy accuracy analysis is based on the methodology developed by Arcadis IBI Group 
                      for RTD Denver's real-time vehicle position feeds. The study compared GTFS-RT occupancy 
                      status with Automatic Passenger Counter (APC) data from August 15-18, 2023.
                    </p>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                      <div>
                        <span className="font-medium text-blue-900">Target Accuracy:</span>
                        <span className="ml-2 text-blue-800">78.5%</span>
                      </div>
                      <div>
                        <span className="font-medium text-blue-900">Data Joining Rate:</span>
                        <span className="ml-2 text-blue-800">89.4%</span>
                      </div>
                      <div>
                        <span className="font-medium text-blue-900">Total Records:</span>
                        <span className="ml-2 text-blue-800">333,755</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Subscribe/Unsubscribe Control Panel */}
        <div className="mt-8">
          <div className="bg-white rounded-lg shadow-sm border">
            <div className="p-6 border-b border-gray-200">
              <div className="flex items-center space-x-3">
                <Radio className="w-6 h-6 text-blue-500" />
                <div>
                  <h2 className="text-xl font-semibold text-gray-900">Feed Subscription Control</h2>
                  <p className="text-sm text-gray-600">Subscribe and unsubscribe from rail communication and bus SIRI feeds</p>
                </div>
              </div>
            </div>

            <div className="p-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Rail Communication Subscribe/Unsubscribe */}
                <div className="border border-blue-200 rounded-lg p-4 bg-blue-50">
                  <div className="flex items-center space-x-3 mb-4">
                    <Train className="w-5 h-5 text-blue-600" />
                    <h3 className="text-lg font-medium text-blue-900">Rail Communication</h3>
                  </div>
                  
                  <div className="space-y-4">
                    {/* Quick Subscribe Actions */}
                    <div className="text-sm text-blue-800">
                      <p className="font-medium mb-2">Quick Subscribe:</p>
                      <div className="grid grid-cols-3 gap-2">
                        <button
                          onClick={() => subscribeToRailComm('original')}
                          className="px-2 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 transition-colors"
                        >
                          Original
                        </button>
                        <button
                          onClick={() => subscribeToRailComm('bridge')}
                          className="px-2 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 transition-colors"
                        >
                          Bridge
                        </button>
                        <button
                          onClick={() => subscribeToRailComm('kafka')}
                          className="px-2 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 transition-colors"
                        >
                          Kafka
                        </button>
                      </div>
                    </div>

                    <div className="text-sm text-blue-800">
                      <p className="font-medium">Active Subscriptions:</p>
                      {subscriptions.filter(s => s.type === 'rail-comm' && s.status === 'active').length > 0 ? (
                        <ul className="mt-2 space-y-1">
                          {subscriptions
                            .filter(s => s.type === 'rail-comm' && s.status === 'active')
                            .map(sub => (
                              <li key={sub.id} className="flex items-center justify-between bg-white/50 rounded px-2 py-1">
                                <span className="text-xs">{sub.name}</span>
                                <button
                                  onClick={() => unsubscribeFromFeed(sub.id, 'rail-comm')}
                                  className="text-xs text-orange-600 hover:text-orange-800 flex items-center space-x-1"
                                >
                                  <Unlink className="w-3 h-3" />
                                  <span>Unsubscribe</span>
                                </button>
                              </li>
                            ))}
                        </ul>
                      ) : (
                        <div className="mt-2 p-2 bg-white/50 rounded">
                          <p className="text-xs text-blue-600">No active rail communication subscriptions</p>
                          <p className="text-xs text-blue-500 mt-1">Click a subscribe button above to get started</p>
                        </div>
                      )}
                    </div>

                    <div className="pt-3 border-t border-blue-200">
                      <h4 className="text-sm font-medium text-blue-900 mb-2">Available Commands:</h4>
                      <div className="space-y-2 text-xs text-blue-800">
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-blue-900">./rtd-control.sh rail-comm subscribe</code>
                          <p className="mt-1">Subscribe to original endpoint</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-blue-900">./rtd-control.sh rail-comm subscribe-bridge</code>
                          <p className="mt-1">Subscribe to Direct Kafka Bridge</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-blue-900">./rtd-control.sh rail-comm subscribe-kafka</code>
                          <p className="mt-1">Subscribe to direct Kafka endpoint</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-blue-900">./rtd-control.sh rail-comm unsubscribe-all</code>
                          <p className="mt-1">Unsubscribe from all endpoints</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Bus SIRI Subscribe/Unsubscribe */}
                <div className="border border-green-200 rounded-lg p-4 bg-green-50">
                  <div className="flex items-center space-x-3 mb-4">
                    <Users className="w-5 h-5 text-green-600" />
                    <h3 className="text-lg font-medium text-green-900">Bus SIRI Feed</h3>
                  </div>
                  
                  <div className="space-y-4">
                    {/* Quick Subscribe Actions */}
                    <div className="text-sm text-green-800">
                      <p className="font-medium mb-2">Quick Subscribe:</p>
                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={() => subscribeToBusSiri('localhost', 'StopMonitoring')}
                          className="px-2 py-1 bg-green-600 text-white rounded text-xs hover:bg-green-700 transition-colors"
                        >
                          Stop Monitoring
                        </button>
                        <button
                          onClick={() => subscribeToBusSiri('localhost', 'VehicleMonitoring')}
                          className="px-2 py-1 bg-green-600 text-white rounded text-xs hover:bg-green-700 transition-colors"
                        >
                          Vehicle Monitoring
                        </button>
                      </div>
                      <div className="mt-2">
                        <button
                          onClick={() => subscribeToBusSiri()}
                          className="w-full px-2 py-1 bg-emerald-600 text-white rounded text-xs hover:bg-emerald-700 transition-colors"
                        >
                          Default Subscription
                        </button>
                      </div>
                    </div>

                    <div className="text-sm text-green-800">
                      <p className="font-medium">Active Subscriptions:</p>
                      {subscriptions.filter(s => s.type === 'bus-siri').length > 0 ? (
                        <ul className="mt-2 space-y-1">
                          {subscriptions
                            .filter(s => s.type === 'bus-siri')
                            .map(sub => (
                              <li key={sub.id} className="flex items-center justify-between bg-white/50 rounded px-2 py-1">
                                <div className="flex items-center space-x-2">
                                  <span className="text-xs">{sub.name}</span>
                                  <span className={`text-xs px-1 rounded ${sub.status === 'active' ? 'bg-green-200 text-green-800' : 'bg-yellow-200 text-yellow-800'}`}>
                                    {sub.status === 'active' ? 'Active' : 'Ready'}
                                  </span>
                                </div>
                                <button
                                  onClick={() => unsubscribeFromFeed(sub.id, 'bus-siri')}
                                  className="text-xs text-orange-600 hover:text-orange-800 flex items-center space-x-1"
                                >
                                  <Unlink className="w-3 h-3" />
                                  <span>Unsubscribe</span>
                                </button>
                              </li>
                            ))}
                        </ul>
                      ) : (
                        <div className="mt-2 p-2 bg-white/50 rounded">
                          <p className="text-xs text-green-600">No active bus SIRI subscriptions</p>
                          <p className="text-xs text-green-500 mt-1">Click a subscribe button above to get started</p>
                        </div>
                      )}
                    </div>

                    <div className="pt-3 border-t border-green-200">
                      <h4 className="text-sm font-medium text-green-900 mb-2">Available Commands:</h4>
                      <div className="space-y-2 text-xs text-green-800">
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-green-900">./scripts/bus-siri-subscribe.sh</code>
                          <p className="mt-1">Subscribe with default settings</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-green-900">./scripts/bus-siri-subscribe.sh [host] [service] [ttl]</code>
                          <p className="mt-1">Subscribe with custom parameters</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-green-900">./scripts/bus-siri-subscribe.sh unsubscribe</code>
                          <p className="mt-1">Unsubscribe from SIRI bus feed</p>
                        </div>
                        <div className="bg-white/50 rounded p-2">
                          <code className="text-green-900">./rtd-control.sh bus-comm status</code>
                          <p className="mt-1">Check bus communication status</p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Bulk Actions */}
              <div className="mt-6 pt-6 border-t border-gray-200">
                <h3 className="text-lg font-medium text-gray-900 mb-4">Bulk Actions</h3>
                
                {/* Subscribe Actions */}
                <div className="mb-4">
                  <h4 className="text-sm font-medium text-gray-700 mb-2">Subscribe Actions:</h4>
                  <div className="flex flex-wrap gap-3">
                    <button
                      onClick={() => subscribeToRailComm('bridge')}
                      className="flex items-center space-x-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                      <UserPlus className="w-4 h-4" />
                      <span>Subscribe Rail (Bridge)</span>
                    </button>

                    <button
                      onClick={() => subscribeToBusSiri()}
                      className="flex items-center space-x-2 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors"
                    >
                      <Radio className="w-4 h-4" />
                      <span>Subscribe Bus SIRI</span>
                    </button>

                    <button
                      onClick={async () => {
                        await subscribeToRailComm('bridge');
                        await new Promise(resolve => setTimeout(resolve, 1000));
                        await subscribeToBusSiri();
                      }}
                      className="flex items-center space-x-2 px-4 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors"
                    >
                      <UserPlus className="w-4 h-4" />
                      <span>Subscribe All Feeds</span>
                    </button>
                  </div>
                </div>

                {/* Unsubscribe Actions */}
                <div>
                  <h4 className="text-sm font-medium text-gray-700 mb-2">Unsubscribe Actions:</h4>
                  <div className="flex flex-wrap gap-3">
                    <button
                      onClick={() => {
                        const railCommSubs = subscriptions.filter(s => s.type === 'rail-comm' && s.status === 'active');
                        if (railCommSubs.length === 0) {
                          alert('No active rail communication subscriptions to unsubscribe from.');
                          return;
                        }
                        if (window.confirm(`Unsubscribe from ${railCommSubs.length} rail communication feed(s)?`)) {
                          railCommSubs.forEach(sub => unsubscribeFromFeed(sub.id, 'rail-comm'));
                        }
                      }}
                      className="flex items-center space-x-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition-colors"
                    >
                      <Train className="w-4 h-4" />
                      <span>Unsubscribe All Rail Comm</span>
                    </button>

                    <button
                      onClick={() => {
                        const busSiriSubs = subscriptions.filter(s => s.type === 'bus-siri');
                        if (busSiriSubs.length === 0) {
                          alert('No active bus SIRI subscriptions to unsubscribe from.');
                          return;
                        }
                        if (window.confirm(`Unsubscribe from ${busSiriSubs.length} bus SIRI feed(s)?`)) {
                          busSiriSubs.forEach(sub => unsubscribeFromFeed(sub.id, 'bus-siri'));
                        }
                      }}
                      className="flex items-center space-x-2 px-4 py-2 bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors"
                    >
                      <Users className="w-4 h-4" />
                      <span>Unsubscribe All Bus SIRI</span>
                    </button>

                    <button
                      onClick={unsubscribeAll}
                      className="flex items-center space-x-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                    >
                      <UserX className="w-4 h-4" />
                      <span>Unsubscribe All Feeds</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminDashboard;