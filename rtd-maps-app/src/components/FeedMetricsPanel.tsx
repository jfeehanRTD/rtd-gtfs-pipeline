import React, { useState, useEffect } from 'react';
import { 
  Activity, 
  RefreshCw, 
  Server, 
  Clock, 
  AlertTriangle,
  CheckCircle,
  XCircle,
  Loader,
  RotateCcw
} from 'lucide-react';
import { 
  FeedMetricsService, 
  AllFeedMetricsResponse, 
  FeedMetrics 
} from '../services/feedMetricsService';

interface FeedMetricsPanelProps {
  className?: string;
}

const FeedMetricsPanel: React.FC<FeedMetricsPanelProps> = ({ className = '' }) => {
  const [metrics, setMetrics] = useState<AllFeedMetricsResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [lastUpdate, setLastUpdate] = useState<Date>(new Date());
  const feedMetricsService = FeedMetricsService.getInstance();

  useEffect(() => {
    setIsLoading(true);
    
    // Subscribe to metrics updates
    const unsubscribe = feedMetricsService.subscribe((newMetrics) => {
      setMetrics(newMetrics);
      setLastUpdate(new Date());
      setIsLoading(false);
    });

    return unsubscribe;
  }, [feedMetricsService]);

  const handleResetFeed = async (feedType: 'siri' | 'lrgps' | 'railcomm') => {
    const success = await feedMetricsService.resetFeedMetrics(feedType);
    if (success) {
      console.log(`✅ Reset ${feedType} feed metrics`);
    }
  };

  const renderFeedCard = (feedKey: 'siri' | 'lrgps' | 'railcomm', feedData: FeedMetrics) => {
    const iconMap = {
      siri: '🚌',
      lrgps: '🚊', 
      railcomm: '🛤️'
    };

    const getHealthIcon = () => {
      switch (feedData.health_status) {
        case 'healthy': return <CheckCircle className="h-5 w-5 text-green-600" />;
        case 'warning': return <AlertTriangle className="h-5 w-5 text-yellow-600" />;
        case 'error': return <XCircle className="h-5 w-5 text-red-600" />;
        case 'waiting': return <Loader className="h-5 w-5 text-gray-600 animate-spin" />;
        default: return <Server className="h-5 w-5 text-gray-400" />;
      }
    };

    const getHealthBgColor = () => {
      switch (feedData.health_status) {
        case 'healthy': return 'bg-green-50 border-green-200';
        case 'warning': return 'bg-yellow-50 border-yellow-200';
        case 'error': return 'bg-red-50 border-red-200';
        case 'waiting': return 'bg-gray-50 border-gray-200';
        default: return 'bg-gray-50 border-gray-200';
      }
    };

    return (
      <div key={feedKey} className={`border rounded-lg p-4 ${getHealthBgColor()}`}>
        {/* Feed Header */}
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center space-x-2">
            <span className="text-2xl">{iconMap[feedKey]}</span>
            <div>
              <h3 className="font-semibold text-sm text-gray-900">{feedData.feed_name}</h3>
              <div className="flex items-center space-x-1">
                {getHealthIcon()}
                <span className={`text-xs font-medium ${feedMetricsService.getHealthColor(feedData.health_status)}`}>
                  {feedData.health_status.toUpperCase()}
                </span>
              </div>
            </div>
          </div>
          <button
            onClick={() => handleResetFeed(feedKey)}
            className="p-1 rounded hover:bg-gray-200 transition-colors"
            title="Reset metrics"
          >
            <RotateCcw className="h-4 w-4 text-gray-500" />
          </button>
        </div>

        {/* 5-Second Metrics */}
        <div className="bg-white rounded-md p-3 mb-3 border">
          <div className="flex items-center space-x-1 mb-2">
            <Activity className="h-4 w-4 text-blue-600" />
            <span className="text-xs font-semibold text-gray-700">Last 5 Seconds</span>
          </div>
          <div className="grid grid-cols-3 gap-2 text-center">
            <div>
              <div className="text-lg font-bold text-blue-600">{feedData.last_5s.messages}</div>
              <div className="text-xs text-gray-600">Messages</div>
            </div>
            <div>
              <div className="text-lg font-bold text-green-600">{feedData.last_5s.connections}</div>
              <div className="text-xs text-gray-600">Connections</div>
            </div>
            <div>
              <div className="text-lg font-bold text-red-600">{feedData.last_5s.errors}</div>
              <div className="text-xs text-gray-600">Errors</div>
            </div>
          </div>
        </div>

        {/* Total Metrics */}
        <div className="bg-white rounded-md p-3 border">
          <div className="flex items-center space-x-1 mb-2">
            <Server className="h-4 w-4 text-purple-600" />
            <span className="text-xs font-semibold text-gray-700">
              Running Totals ({feedMetricsService.formatUptime(feedData.totals.uptime_seconds)})
            </span>
          </div>
          
          <div className="space-y-2">
            <div className="flex justify-between text-xs">
              <span className="text-gray-600">Total Messages:</span>
              <span className="font-semibold">{feedData.totals.messages.toLocaleString()}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-600">Total Connections:</span>
              <span className="font-semibold text-green-600">{feedData.totals.connections.toLocaleString()}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-600">Total Errors:</span>
              <span className="font-semibold text-red-600">{feedData.totals.errors.toLocaleString()}</span>
            </div>
            
            <hr className="my-2" />
            
            <div className="flex justify-between text-xs">
              <span className="text-gray-600">Avg Messages/min:</span>
              <span className="font-semibold">{feedData.totals.avg_messages_per_min}</span>
            </div>
            <div className="flex justify-between text-xs">
              <span className="text-gray-600">Avg Connections/min:</span>
              <span className="font-semibold">{feedData.totals.avg_connections_per_min}</span>
            </div>
            
            {/* Rates if available */}
            {feedData.rates && (
              <>
                <hr className="my-2" />
                <div className="flex justify-between text-xs">
                  <span className="text-gray-600">Connection Rate:</span>
                  <span className="font-semibold text-green-600">{feedData.rates.connection_rate_percent}%</span>
                </div>
                <div className="flex justify-between text-xs">
                  <span className="text-gray-600">Error Rate:</span>
                  <span className="font-semibold text-red-600">{feedData.rates.error_rate_percent}%</span>
                </div>
              </>
            )}
          </div>
        </div>

        {/* Last Message Time */}
        {feedData.last_message && (
          <div className="mt-2 flex items-center space-x-1 text-xs text-gray-500">
            <Clock className="h-3 w-3" />
            <span>Last: {new Date(feedData.last_message).toLocaleTimeString()}</span>
          </div>
        )}
      </div>
    );
  };

  if (isLoading) {
    return (
      <div className={`${className}`}>
        <div className="flex items-center justify-center py-8">
          <RefreshCw className="h-6 w-6 animate-spin text-blue-600 mr-2" />
          <span className="text-gray-600">Loading feed metrics...</span>
        </div>
      </div>
    );
  }

  if (!metrics) {
    return (
      <div className={`${className}`}>
        <div className="text-center py-8">
          <XCircle className="h-12 w-12 text-red-500 mx-auto mb-2" />
          <p className="text-gray-600">Failed to load feed metrics</p>
          <p className="text-sm text-gray-500">Check if the Java API server is running on port 8080</p>
        </div>
      </div>
    );
  }

  return (
    <div className={`${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center space-x-2">
          <Activity className="h-5 w-5 text-blue-600" />
          <h2 className="text-lg font-semibold text-gray-900">Feed Metrics</h2>
          <span className="text-xs text-gray-500">(Updates every 5s)</span>
        </div>
        <div className="text-xs text-gray-500">
          Last Update: {lastUpdate.toLocaleTimeString()}
        </div>
      </div>

      {/* Feed Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {Object.entries(metrics.feeds).map(([feedKey, feedData]) => 
          renderFeedCard(feedKey as 'siri' | 'lrgps' | 'railcomm', feedData)
        )}
      </div>

      {/* System Info */}
      <div className="mt-4 p-3 bg-gray-50 rounded-lg">
        <div className="flex items-center justify-between text-sm text-gray-600">
          <span>System Uptime: {feedMetricsService.formatUptime(Math.floor(metrics.uptime_ms / 1000))}</span>
          <span>Last System Update: {new Date(metrics.timestamp).toLocaleTimeString()}</span>
        </div>
      </div>
    </div>
  );
};

export default FeedMetricsPanel;