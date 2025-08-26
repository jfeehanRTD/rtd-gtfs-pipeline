import React from 'react';
import { Wifi, WifiOff, Bus, Train, Clock, RefreshCw } from 'lucide-react';

interface ConnectionState {
  isConnected: boolean;
  lastUpdate: Date | null;
  busCount: number;
  trainCount: number;
  error: string | null;
}

interface MobileStatusBarProps {
  connectionState: ConnectionState;
  onRefresh: () => void;
}

const MobileStatusBar: React.FC<MobileStatusBarProps> = ({
  connectionState,
  onRefresh
}) => {
  return (
    <div className="sm:hidden absolute bottom-4 left-4 right-4 z-[1000]">
      <div className="bg-white rounded-lg shadow-lg p-3">
        <div className="flex items-center justify-between">
          {/* Connection Status */}
          <div className="flex items-center space-x-2">
            {connectionState.isConnected ? (
              <>
                <Wifi className="w-4 h-4 text-green-500" aria-label="Connected" />
                <span className="text-xs font-medium text-green-600">Live</span>
              </>
            ) : (
              <>
                <WifiOff className="w-4 h-4 text-red-500" aria-label="Disconnected" />
                <span className="text-xs font-medium text-red-600">Offline</span>
              </>
            )}
          </div>
          
          {/* Vehicle Counts */}
          <div className="flex items-center space-x-3 text-xs text-gray-600">
            <div className="flex items-center space-x-1">
              <Bus className="w-3 h-3" aria-label="Buses" />
              <span>{connectionState.busCount}</span>
            </div>
            <div className="flex items-center space-x-1">
              <Train className="w-3 h-3" aria-label="Trains" />
              <span>{connectionState.trainCount}</span>
            </div>
          </div>
          
          {/* Last Update */}
          {connectionState.lastUpdate && (
            <div className="flex items-center space-x-1 text-xs text-gray-500">
              <Clock className="w-3 h-3" aria-label="Last updated" />
              <span>{connectionState.lastUpdate.toLocaleTimeString([], { 
                hour: '2-digit', 
                minute: '2-digit' 
              })}</span>
            </div>
          )}
          
          {/* Refresh Button */}
          <button
            onClick={onRefresh}
            className="p-1.5 hover:bg-gray-100 rounded transition-colors focus:ring-2 focus:ring-blue-500 focus:outline-none"
            title="Refresh live transit data"
            aria-label="Refresh live transit data"
          >
            <RefreshCw className="w-4 h-4 text-gray-600" />
          </button>
        </div>
      </div>
    </div>
  );
};

export default MobileStatusBar;