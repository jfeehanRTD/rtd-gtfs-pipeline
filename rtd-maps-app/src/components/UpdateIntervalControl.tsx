import React, { useState, useEffect, useCallback } from 'react';
import { Clock, Settings, Zap, Timer, Gauge } from 'lucide-react';

interface UpdateIntervalControlProps {
  currentInterval: number;
  onIntervalChange: (intervalSeconds: number) => Promise<void>;
  className?: string;
}

const UPDATE_PRESETS = [
  { label: '0.5s', value: 0.5, description: 'Ultra Fast', icon: Zap, color: 'text-red-500' },
  { label: '1s', value: 1, description: 'Real-time', icon: Timer, color: 'text-orange-500' },
  { label: '5s', value: 5, description: 'Fast', icon: Clock, color: 'text-yellow-500' },
  { label: '10s', value: 10, description: 'Normal', icon: Gauge, color: 'text-green-500' },
  { label: '30s', value: 30, description: 'Slow', icon: Settings, color: 'text-blue-500' },
  { label: '60s', value: 60, description: 'Low Data', icon: Settings, color: 'text-gray-500' },
];

export const UpdateIntervalControl: React.FC<UpdateIntervalControlProps> = ({
  currentInterval,
  onIntervalChange,
  className = ''
}) => {
  const [selectedInterval, setSelectedInterval] = useState(currentInterval);
  const [isUpdating, setIsUpdating] = useState(false);
  const [customValue, setCustomValue] = useState('');
  const [showCustom, setShowCustom] = useState(false);

  useEffect(() => {
    setSelectedInterval(currentInterval);
  }, [currentInterval]);

  const handlePresetClick = useCallback(async (interval: number) => {
    if (interval === selectedInterval) return;
    
    setIsUpdating(true);
    try {
      await onIntervalChange(interval);
      setSelectedInterval(interval);
      setShowCustom(false);
    } catch (error) {
      console.error('Failed to update interval:', error);
    } finally {
      setIsUpdating(false);
    }
  }, [selectedInterval, onIntervalChange]);

  const handleCustomSubmit = useCallback(async (e: React.FormEvent) => {
    e.preventDefault();
    const value = parseFloat(customValue);
    if (value >= 0.5 && value <= 300) {
      setIsUpdating(true);
      try {
        await onIntervalChange(value);
        setSelectedInterval(value);
        setCustomValue('');
        setShowCustom(false);
      } catch (error) {
        console.error('Failed to update interval:', error);
      } finally {
        setIsUpdating(false);
      }
    }
  }, [customValue, onIntervalChange]);

  const getCurrentPreset = () => {
    return UPDATE_PRESETS.find(preset => preset.value === selectedInterval);
  };

  const currentPreset = getCurrentPreset();
  const isCustomInterval = !currentPreset;

  return (
    <div className={`bg-white rounded-lg shadow-lg border border-gray-200 p-4 ${className}`}>
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center space-x-2">
          <Clock className="w-5 h-5 text-rtd-primary" />
          <h3 className="text-lg font-semibold text-gray-800">Update Interval</h3>
        </div>
        {isUpdating && (
          <div className="flex items-center space-x-1 text-sm text-rtd-primary">
            <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-rtd-primary"></div>
            <span>Updating...</span>
          </div>
        )}
      </div>

      {/* Current Status */}
      <div className="mb-4 p-3 bg-gray-50 rounded-lg">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-2">
            {currentPreset ? (
              <>
                <currentPreset.icon className={`w-4 h-4 ${currentPreset.color}`} />
                <span className="font-medium text-gray-700">{currentPreset.description}</span>
              </>
            ) : (
              <>
                <Settings className="w-4 h-4 text-purple-500" />
                <span className="font-medium text-gray-700">Custom</span>
              </>
            )}
          </div>
          <div className="text-right">
            <div className="text-lg font-bold text-rtd-primary">{selectedInterval}s</div>
            <div className="text-xs text-gray-500">
              {selectedInterval < 1 ? 
                `${Math.round(60 / selectedInterval)}/min` : 
                `${Math.round(60 / selectedInterval)}/min`
              }
            </div>
          </div>
        </div>
      </div>

      {/* Preset Buttons */}
      <div className="grid grid-cols-3 gap-2 mb-4">
        {UPDATE_PRESETS.map((preset) => {
          const Icon = preset.icon;
          const isSelected = selectedInterval === preset.value;
          return (
            <button
              key={preset.value}
              onClick={() => handlePresetClick(preset.value)}
              disabled={isUpdating}
              className={`p-3 rounded-lg border-2 transition-all duration-200 ${
                isSelected
                  ? 'border-rtd-primary bg-rtd-primary text-white shadow-md'
                  : 'border-gray-200 bg-white text-gray-700 hover:border-rtd-primary hover:bg-rtd-light'
              } ${isUpdating ? 'opacity-50 cursor-not-allowed' : ''}`}
            >
              <div className="flex flex-col items-center space-y-1">
                <Icon className={`w-4 h-4 ${isSelected ? 'text-white' : preset.color}`} />
                <span className="text-sm font-medium">{preset.label}</span>
                <span className="text-xs opacity-75">{preset.description}</span>
              </div>
            </button>
          );
        })}
      </div>

      {/* Custom Interval */}
      <div className="border-t pt-4">
        <button
          onClick={() => setShowCustom(!showCustom)}
          className="w-full flex items-center justify-between p-2 text-sm text-gray-600 hover:text-rtd-primary transition-colors"
        >
          <span>Custom Interval</span>
          <Settings className="w-4 h-4" />
        </button>

        {showCustom && (
          <form onSubmit={handleCustomSubmit} className="mt-2">
            <div className="flex space-x-2">
              <input
                type="number"
                min="0.5"
                max="300"
                step="0.1"
                value={customValue}
                onChange={(e) => setCustomValue(e.target.value)}
                placeholder="0.5 - 300"
                className="flex-1 px-3 py-2 border border-gray-300 rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-rtd-primary focus:border-transparent"
              />
              <button
                type="submit"
                disabled={!customValue || isUpdating}
                className="px-4 py-2 bg-rtd-primary text-white text-sm rounded-md hover:bg-rtd-dark disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Set
              </button>
            </div>
            <p className="text-xs text-gray-500 mt-1">
              Enter interval in seconds (0.5 to 300)
            </p>
          </form>
        )}
      </div>

      {/* Performance Warning */}
      {selectedInterval < 1 && (
        <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <div className="flex items-start space-x-2">
            <Zap className="w-4 h-4 text-yellow-600 mt-0.5 flex-shrink-0" />
            <div className="text-sm">
              <p className="text-yellow-800 font-medium">High Frequency Mode</p>
              <p className="text-yellow-700 text-xs mt-1">
                Updates faster than 1 second may impact performance and increase data usage.
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Info */}
      <div className="mt-4 p-3 bg-blue-50 border border-blue-200 rounded-lg">
        <div className="text-sm text-blue-800">
          <p className="font-medium mb-1">📡 How it works:</p>
          <ul className="text-xs space-y-1 text-blue-700">
            <li>• Changes both React app polling & Java pipeline fetch intervals</li>
            <li>• Lower intervals = more real-time updates</li>
            <li>• RTD data refreshes from source every 60 seconds</li>
          </ul>
        </div>
      </div>
    </div>
  );
};

export default UpdateIntervalControl;