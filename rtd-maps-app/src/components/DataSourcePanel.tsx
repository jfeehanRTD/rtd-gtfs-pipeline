// Data Source Panel - Shows available data sinks and their status

import React, { useState, useEffect } from 'react';
import { 
  Database, 
  Server, 
  Wifi, 
  WifiOff, 
  Activity,
  RefreshCw,
  CheckCircle,
  XCircle,
  AlertCircle,
  Clock,
  Zap,
  Settings,
  ChevronRight,
  Layers
} from 'lucide-react';
import { DataSource, DataQueryService } from '@/services/dataQueryService';
import { formatDistanceToNow } from 'date-fns';

interface DataSourcePanelProps {
  selectedSources: Set<string>;
  onSourceToggle: (sourceId: string) => void;
  onRefresh: () => void;
  className?: string;
}

export const DataSourcePanel: React.FC<DataSourcePanelProps> = ({
  selectedSources,
  onSourceToggle,
  onRefresh,
  className = ''
}) => {
  const [sources, setSources] = useState<DataSource[]>([]);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [expandedSource, setExpandedSource] = useState<string | null>(null);

  useEffect(() => {
    loadSources();
    const interval = setInterval(loadSources, 30000); // Refresh every 30 seconds
    return () => clearInterval(interval);
  }, []);

  const loadSources = async () => {
    const queryService = DataQueryService.getInstance();
    const availableSources = queryService.getAvailableSources();
    setSources(availableSources);
  };

  const handleRefreshSources = async () => {
    setIsRefreshing(true);
    const queryService = DataQueryService.getInstance();
    await queryService.refreshSourceStatus();
    await loadSources();
    setIsRefreshing(false);
    onRefresh();
  };

  const getSourceIcon = (type: DataSource['type']) => {
    switch (type) {
      case 'kafka':
        return Server;
      case 'flink':
        return Activity;
      case 'database':
        return Database;
      case 'websocket':
        return Zap;
      case 'api':
      default:
        return Wifi;
    }
  };

  const getStatusIcon = (status: DataSource['status']) => {
    switch (status) {
      case 'connected':
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case 'disconnected':
        return <XCircle className="w-4 h-4 text-gray-400" />;
      case 'error':
        return <AlertCircle className="w-4 h-4 text-red-500" />;
      default:
        return <AlertCircle className="w-4 h-4 text-yellow-500" />;
    }
  };

  const getStatusColor = (status: DataSource['status']) => {
    switch (status) {
      case 'connected':
        return 'text-green-600 bg-green-50';
      case 'disconnected':
        return 'text-gray-600 bg-gray-50';
      case 'error':
        return 'text-red-600 bg-red-50';
      default:
        return 'text-yellow-600 bg-yellow-50';
    }
  };

  const connectedCount = sources.filter(s => s.status === 'connected').length;
  const selectedCount = sources.filter(s => selectedSources.has(s.id)).length;

  return (
    <div className={`bg-white rounded-lg shadow-lg border border-gray-200 ${className}`}>
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200">
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center space-x-2">
            <Layers className="w-5 h-5 text-rtd-primary" />
            <h3 className="text-lg font-semibold text-gray-800">Data Sources</h3>
          </div>
          <button
            onClick={handleRefreshSources}
            disabled={isRefreshing}
            className="p-2 hover:bg-gray-100 rounded-md transition-colors disabled:opacity-50"
            title="Refresh sources"
          >
            <RefreshCw className={`w-4 h-4 text-gray-600 ${isRefreshing ? 'animate-spin' : ''}`} />
          </button>
        </div>
        
        {/* Stats */}
        <div className="flex items-center justify-between text-sm">
          <span className="text-gray-600">
            {connectedCount} of {sources.length} connected
          </span>
          <span className="text-rtd-primary font-medium">
            {selectedCount} selected
          </span>
        </div>
      </div>

      {/* Source List */}
      <div className="divide-y divide-gray-100 max-h-96 overflow-y-auto">
        {sources.map(source => {
          const Icon = getSourceIcon(source.type);
          const isSelected = selectedSources.has(source.id);
          const isExpanded = expandedSource === source.id;
          const isConnected = source.status === 'connected';
          
          return (
            <div key={source.id} className="relative">
              {/* Main Source Row */}
              <div
                className={`px-4 py-3 hover:bg-gray-50 cursor-pointer transition-colors ${
                  isSelected ? 'bg-rtd-light/20' : ''
                }`}
                onClick={() => isConnected && onSourceToggle(source.id)}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-3 flex-1">
                    {/* Selection indicator */}
                    <div className={`w-1 h-8 rounded-full ${
                      isSelected ? 'bg-rtd-primary' : 'bg-transparent'
                    }`} />
                    
                    {/* Source icon and status */}
                    <div className="relative">
                      <Icon className="w-5 h-5 text-gray-600" />
                      <div className="absolute -bottom-1 -right-1">
                        {getStatusIcon(source.status)}
                      </div>
                    </div>

                    {/* Source info */}
                    <div className="flex-1">
                      <div className="flex items-center space-x-2">
                        <span className="font-medium text-gray-800">
                          {source.name}
                        </span>
                        <span className={`px-2 py-0.5 text-xs font-medium rounded-full ${
                          getStatusColor(source.status)
                        }`}>
                          {source.status}
                        </span>
                      </div>
                      <div className="text-xs text-gray-500 mt-0.5">
                        {source.description}
                      </div>
                    </div>
                  </div>

                  {/* Metrics and expand button */}
                  <div className="flex items-center space-x-3">
                    {/* Latency */}
                    {source.status === 'connected' && source.latency > 0 && (
                      <div className="flex items-center space-x-1 text-xs text-gray-500">
                        <Zap className="w-3 h-3" />
                        <span>{source.latency}ms</span>
                      </div>
                    )}

                    {/* Vehicle count */}
                    {source.vehicleCount > 0 && (
                      <div className="flex items-center space-x-1 text-xs text-gray-600">
                        <span className="font-medium">{source.vehicleCount}</span>
                        <span>vehicles</span>
                      </div>
                    )}

                    {/* Last update */}
                    {source.lastUpdate && (
                      <div className="flex items-center space-x-1 text-xs text-gray-500">
                        <Clock className="w-3 h-3" />
                        <span>{formatDistanceToNow(source.lastUpdate)}</span>
                      </div>
                    )}

                    {/* Expand button */}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setExpandedSource(isExpanded ? null : source.id);
                      }}
                      className="p-1 hover:bg-gray-200 rounded transition-colors"
                    >
                      <ChevronRight className={`w-4 h-4 text-gray-400 transition-transform ${
                        isExpanded ? 'rotate-90' : ''
                      }`} />
                    </button>
                  </div>
                </div>

                {/* Selection checkbox for connected sources */}
                {isConnected && (
                  <div className="absolute left-2 top-1/2 -translate-y-1/2">
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => {}}
                      className="rounded border-gray-300 text-rtd-primary focus:ring-rtd-primary cursor-pointer"
                    />
                  </div>
                )}
              </div>

              {/* Expanded Details */}
              {isExpanded && (
                <div className="px-4 py-3 bg-gray-50 border-t border-gray-100">
                  <div className="space-y-2">
                    {/* Endpoint */}
                    <div className="flex items-start space-x-2">
                      <span className="text-xs font-medium text-gray-600">Endpoint:</span>
                      <span className="text-xs text-gray-500 font-mono break-all">
                        {source.endpoint}
                      </span>
                    </div>

                    {/* Capabilities */}
                    {source.capabilities.length > 0 && (
                      <div className="flex items-start space-x-2">
                        <span className="text-xs font-medium text-gray-600">Capabilities:</span>
                        <div className="flex flex-wrap gap-1">
                          {source.capabilities.map(cap => (
                            <span
                              key={cap}
                              className="px-2 py-0.5 text-xs bg-white border border-gray-200 rounded-full"
                            >
                              {cap}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}

                    {/* Connection settings */}
                    {source.status === 'connected' && (
                      <div className="flex items-center justify-between pt-2">
                        <button className="text-xs text-rtd-primary hover:text-rtd-dark flex items-center space-x-1">
                          <Settings className="w-3 h-3" />
                          <span>Configure</span>
                        </button>
                        <button 
                          onClick={(e) => {
                            e.stopPropagation();
                            // Test connection
                            handleRefreshSources();
                          }}
                          className="text-xs text-gray-600 hover:text-gray-800 flex items-center space-x-1"
                        >
                          <Activity className="w-3 h-3" />
                          <span>Test Connection</span>
                        </button>
                      </div>
                    )}

                    {/* Error message for disconnected sources */}
                    {source.status === 'error' && (
                      <div className="text-xs text-red-600 bg-red-50 p-2 rounded">
                        Connection failed. Check endpoint configuration.
                      </div>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}

        {sources.length === 0 && (
          <div className="p-8 text-center text-gray-500">
            <Database className="w-12 h-12 mx-auto mb-3 text-gray-300" />
            <p className="text-sm">No data sources configured</p>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="px-4 py-2 bg-gray-50 border-t border-gray-200">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2 text-xs text-gray-500">
            <WifiOff className="w-3 h-3" />
            <span>Select connected sources to query data</span>
          </div>
          <button className="text-xs text-rtd-primary hover:text-rtd-dark font-medium">
            Add Source
          </button>
        </div>
      </div>
    </div>
  );
};

export default DataSourcePanel;