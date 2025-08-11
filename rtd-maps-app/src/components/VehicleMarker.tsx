// Vehicle Marker Component - Individual transit vehicle visualization

import React from 'react';
import { EnhancedVehicleData } from '@/types/rtd';

interface VehicleMarkerProps {
  vehicle: EnhancedVehicleData;
  isSelected: boolean;
  onClick: (vehicle: EnhancedVehicleData) => void;
  className?: string;
}

const VehicleMarker: React.FC<VehicleMarkerProps> = ({
  vehicle,
  isSelected,
  onClick,
  className = ''
}) => {
  const isRail = vehicle.route_info?.route_type !== 3;
  const routeColor = vehicle.route_info?.route_color || (isRail ? '#0066CC' : '#FF6600');
  const size = isSelected ? 28 : 20;
  const isDelayed = (vehicle.delay_seconds || 0) > 300; // 5+ minutes late

  const handleClick = () => {
    onClick(vehicle);
  };

  return (
    <div
      className={`absolute cursor-pointer transform -translate-x-1/2 -translate-y-1/2 transition-all duration-200 hover:scale-110 ${className}`}
      onClick={handleClick}
      style={{
        width: size,
        height: size,
        zIndex: isSelected ? 1000 : 1
      }}
    >
      {/* Vehicle Icon */}
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        className="drop-shadow-md"
      >
        {/* Outer circle */}
        <circle
          cx="12"
          cy="12"
          r="11"
          fill={routeColor}
          stroke="white"
          strokeWidth={isSelected ? "2" : "1"}
        />
        
        {/* Inner icon */}
        {isRail ? (
          // Rail icon
          <g fill="white">
            <rect x="7" y="7" width="10" height="10" rx="1" />
            <rect x="9" y="9" width="2" height="6" />
            <rect x="13" y="9" width="2" height="6" />
          </g>
        ) : (
          // Bus icon  
          <g fill="white">
            <rect x="6" y="8" width="12" height="8" rx="2" />
            <rect x="8" y="10" width="2" height="4" />
            <rect x="10.5" y="10" width="3" height="4" />
            <rect x="14" y="10" width="2" height="4" />
          </g>
        )}

        {/* Selection indicator */}
        {isSelected && (
          <circle
            cx="12"
            cy="12"
            r="6"
            fill="none"
            stroke="white"
            strokeWidth="2"
            opacity="0.8"
          />
        )}

        {/* Delay indicator */}
        {isDelayed && (
          <circle
            cx="18"
            cy="6"
            r="3"
            fill="#FF4444"
            stroke="white"
            strokeWidth="1"
          />
        )}
      </svg>

      {/* Route label (for selected vehicles) */}
      {isSelected && (
        <div className="absolute top-full left-1/2 transform -translate-x-1/2 mt-1">
          <div
            className="px-2 py-1 rounded text-xs font-bold text-white shadow-lg"
            style={{ backgroundColor: routeColor }}
          >
            {vehicle.route_info?.route_short_name || vehicle.route_id}
          </div>
        </div>
      )}

      {/* Bearing indicator (direction arrow) */}
      {vehicle.bearing && (
        <div
          className="absolute inset-0 pointer-events-none"
          style={{
            transform: `rotate(${vehicle.bearing}deg)`
          }}
        >
          <div className="absolute top-0 left-1/2 transform -translate-x-1/2 -translate-y-1">
            <div className="w-0 h-0 border-l-2 border-r-2 border-b-4 border-transparent border-b-white opacity-80" />
          </div>
        </div>
      )}
    </div>
  );
};

export default VehicleMarker;