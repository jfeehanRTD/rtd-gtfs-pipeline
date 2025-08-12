// Vehicle Markers Component for Leaflet Map - RTD Transit Vehicles

import { useMemo } from 'react';
import { Marker, Popup } from 'react-leaflet';
import { EnhancedVehicleData } from '@/types/rtd';
import L from 'leaflet';
import { formatDistanceToNow } from 'date-fns';

interface VehicleMarkersProps {
  vehicles: EnhancedVehicleData[];
  selectedVehicle: EnhancedVehicleData | null;
  onVehicleSelect: (vehicle: EnhancedVehicleData | null) => void;
}

// Create custom vehicle icons
const createVehicleIcon = (vehicle: EnhancedVehicleData, isSelected: boolean = false): L.DivIcon => {
  const isRail = vehicle.route_info?.route_type !== 3;
  const routeColor = vehicle.route_info?.route_color || (isRail ? '#0066CC' : '#FF6600');
  const size = isSelected ? 32 : 24;
  const isDelayed = (vehicle.delay_seconds || 0) > 300; // 5+ minutes late
  
  // Create SVG icon
  const svgIcon = `
    <div style="position: relative; width: ${size}px; height: ${size}px;">
      <svg width="${size}" height="${size}" viewBox="0 0 32 32" style="filter: drop-shadow(2px 2px 4px rgba(0,0,0,0.3));">
        <!-- Outer circle -->
        <circle cx="16" cy="16" r="14" fill="${routeColor}" stroke="white" stroke-width="${isSelected ? 3 : 2}"/>
        
        <!-- Inner icon -->
        ${isRail ? `
          <!-- Rail icon -->
          <g fill="white">
            <rect x="10" y="10" width="12" height="12" rx="2"/>
            <rect x="12" y="12" width="2" height="8"/>
            <rect x="15" y="12" width="2" height="8"/>
            <rect x="18" y="12" width="2" height="8"/>
          </g>
        ` : `
          <!-- Bus icon -->
          <g fill="white">
            <rect x="9" y="11" width="14" height="10" rx="2"/>
            <rect x="11" y="13" width="2" height="6"/>
            <rect x="14" y="13" width="4" height="6"/>
            <rect x="19" y="13" width="2" height="6"/>
            <!-- Wheels -->
            <circle cx="12" cy="22" r="1.5"/>
            <circle cx="20" cy="22" r="1.5"/>
          </g>
        `}
        
        <!-- Selection indicator -->
        ${isSelected ? '<circle cx="16" cy="16" r="8" fill="none" stroke="white" stroke-width="2" opacity="0.8"/>' : ''}
        
        <!-- Delay indicator -->
        ${isDelayed ? '<circle cx="24" cy="8" r="4" fill="#FF4444" stroke="white" stroke-width="2"/>' : ''}
        
        <!-- Direction arrow -->
        ${vehicle.bearing ? `
          <g transform="rotate(${vehicle.bearing} 16 16)">
            <path d="M16 6 L18 10 L14 10 Z" fill="white" opacity="0.9"/>
          </g>
        ` : ''}
      </svg>
      
      <!-- Route label for selected vehicle -->
      ${isSelected && vehicle.route_info?.route_short_name ? `
        <div style="
          position: absolute;
          top: ${size + 2}px;
          left: 50%;
          transform: translateX(-50%);
          background: ${routeColor};
          color: white;
          padding: 2px 6px;
          border-radius: 4px;
          font-size: 10px;
          font-weight: bold;
          white-space: nowrap;
          box-shadow: 0 2px 4px rgba(0,0,0,0.2);
        ">
          ${vehicle.route_info.route_short_name}
        </div>
      ` : ''}
    </div>
  `;

  return L.divIcon({
    html: svgIcon,
    className: 'vehicle-marker',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2]
  });
};

const VehicleMarkers: React.FC<VehicleMarkersProps> = ({
  vehicles,
  selectedVehicle,
  onVehicleSelect
}) => {

  // Create markers with memoization for performance
  const markers = useMemo(() => {
    return vehicles.map((vehicle) => {
      const isSelected = vehicle.vehicle_id === selectedVehicle?.vehicle_id;
      const icon = createVehicleIcon(vehicle, isSelected);
      
      return (
        <Marker
          key={vehicle.vehicle_id}
          position={[vehicle.latitude, vehicle.longitude]}
          icon={icon}
          zIndexOffset={isSelected ? 1000 : 0}
          eventHandlers={{
            click: () => {
              const newSelection = isSelected ? null : vehicle;
              onVehicleSelect(newSelection);
            }
          }}
        >
          <Popup className="vehicle-popup">
            <div className="p-2 min-w-64">
              {/* Header */}
              <div className="flex items-center space-x-2 mb-3 pb-2 border-b border-gray-200">
                {vehicle.route_info?.route_type !== 3 ? (
                  <div className="flex-shrink-0 w-6 h-6 rounded bg-blue-500 flex items-center justify-center">
                    <svg className="w-4 h-4 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
                    </svg>
                  </div>
                ) : (
                  <div className="flex-shrink-0 w-6 h-6 rounded bg-orange-500 flex items-center justify-center">
                    <svg className="w-4 h-4 text-white" viewBox="0 0 24 24" fill="currentColor">
                      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                    </svg>
                  </div>
                )}
                <div>
                  <h3 className="font-bold text-gray-800">
                    {vehicle.route_info?.route_short_name || vehicle.route_id}
                  </h3>
                  <p className="text-sm text-gray-600">
                    {vehicle.route_info?.route_long_name || 'RTD Vehicle'}
                  </p>
                </div>
              </div>

              {/* Vehicle Details */}
              <div className="space-y-2">
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <span className="text-gray-600">Vehicle ID:</span>
                    <div className="font-mono text-xs">{vehicle.vehicle_id}</div>
                  </div>
                  <div>
                    <span className="text-gray-600">Status:</span>
                    <div className="text-xs capitalize">{vehicle.current_status.toLowerCase().replace(/_/g, ' ')}</div>
                  </div>
                </div>

                {/* Position Info */}
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <span className="text-gray-600">Latitude:</span>
                    <div className="font-mono text-xs">{vehicle.latitude.toFixed(6)}</div>
                  </div>
                  <div>
                    <span className="text-gray-600">Longitude:</span>
                    <div className="font-mono text-xs">{vehicle.longitude.toFixed(6)}</div>
                  </div>
                </div>

                {/* Speed and Bearing */}
                {(vehicle.speed || vehicle.bearing) && (
                  <div className="grid grid-cols-2 gap-2 text-sm">
                    {vehicle.speed && (
                      <div>
                        <span className="text-gray-600">Speed:</span>
                        <div className="text-xs">{(vehicle.speed * 3.6).toFixed(1)} km/h</div>
                      </div>
                    )}
                    {vehicle.bearing && (
                      <div>
                        <span className="text-gray-600">Bearing:</span>
                        <div className="text-xs">{Math.round(vehicle.bearing)}°</div>
                      </div>
                    )}
                  </div>
                )}

                {/* Delay Info */}
                {vehicle.delay_seconds !== undefined && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Schedule:</span>
                    <span className={`text-xs px-2 py-1 rounded-full ${
                      vehicle.delay_seconds > 300 ? 'bg-red-100 text-red-700' :
                      vehicle.delay_seconds > 60 ? 'bg-yellow-100 text-yellow-700' :
                      'bg-green-100 text-green-700'
                    }`}>
                      {vehicle.delay_seconds > 0 ? `+${Math.floor(vehicle.delay_seconds / 60)}:${(vehicle.delay_seconds % 60).toString().padStart(2, '0')}` :
                       vehicle.delay_seconds < 0 ? `-${Math.floor(Math.abs(vehicle.delay_seconds) / 60)}:${(Math.abs(vehicle.delay_seconds) % 60).toString().padStart(2, '0')}` :
                       'On time'}
                    </span>
                  </div>
                )}

                {/* Occupancy */}
                {vehicle.occupancy_status && (
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-600">Occupancy:</span>
                    <span className="text-xs capitalize">
                      {vehicle.occupancy_status.toLowerCase().replace(/_/g, ' ')}
                    </span>
                  </div>
                )}

                {/* Data Freshness */}
                <div className="pt-2 border-t border-gray-200 flex items-center justify-between text-xs text-gray-500">
                  <span className="flex items-center">
                    <div className={`w-2 h-2 rounded-full mr-2 ${vehicle.is_real_time ? 'bg-green-500' : 'bg-yellow-500'}`}/>
                    {vehicle.is_real_time ? 'Live' : 'Cached'}
                  </span>
                  <span>Updated {formatDistanceToNow(vehicle.last_updated)} ago</span>
                </div>
              </div>
            </div>
          </Popup>
        </Marker>
      );
    });
  }, [vehicles, selectedVehicle, onVehicleSelect]);

  return <>{markers}</>;
};

export default VehicleMarkers;