// Google Maps React Component with RTD Vehicle Visualization

import React, { useEffect, useRef, useState, useCallback } from 'react';
import { Wrapper } from '@googlemaps/react-wrapper';
import { EnhancedVehicleData, MapBounds, MapFilters, DEFAULT_MAP_CONFIG } from '@/types/rtd';
import VehicleMarker from './VehicleMarker';
import MapControls from './MapControls';

interface GoogleMapProps {
  vehicles: EnhancedVehicleData[];
  selectedVehicle: EnhancedVehicleData | null;
  filters: MapFilters;
  onVehicleSelect: (vehicle: EnhancedVehicleData | null) => void;
  onFiltersChange: (filters: MapFilters) => void;
  className?: string;
}

interface MapComponentProps extends Omit<GoogleMapProps, 'className'> {
  apiKey: string;
}

const MapComponent: React.FC<GoogleMapProps> = ({
  vehicles,
  selectedVehicle,
  filters,
  onVehicleSelect,
  onFiltersChange
}) => {
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<google.maps.Map | null>(null);
  const markersRef = useRef<Map<string, google.maps.Marker>>(new Map());
  const [mapBounds, setMapBounds] = useState<MapBounds | null>(null);

  // Initialize Google Map
  useEffect(() => {
    if (!mapRef.current || mapInstanceRef.current) return;

    const mapOptions: google.maps.MapOptions = {
      ...DEFAULT_MAP_CONFIG,
      center: new google.maps.LatLng(DEFAULT_MAP_CONFIG.center.lat, DEFAULT_MAP_CONFIG.center.lng),
      styles: [
        {
          featureType: 'transit',
          elementType: 'all',
          stylers: [{ visibility: 'simplified' }]
        },
        {
          featureType: 'transit.line',
          elementType: 'all',
          stylers: [{ color: '#0066CC' }, { weight: 2 }]
        },
        {
          featureType: 'poi.business',
          stylers: [{ visibility: 'off' }]
        },
        {
          featureType: 'road.local',
          elementType: 'labels',
          stylers: [{ visibility: 'simplified' }]
        }
      ]
    };

    mapInstanceRef.current = new google.maps.Map(mapRef.current, mapOptions);

    // Add bounds change listener
    mapInstanceRef.current.addListener('bounds_changed', () => {
      if (mapInstanceRef.current) {
        const bounds = mapInstanceRef.current.getBounds();
        if (bounds) {
          const ne = bounds.getNorthEast();
          const sw = bounds.getSouthWest();
          setMapBounds({
            north: ne.lat(),
            south: sw.lat(),
            east: ne.lng(),
            west: sw.lng()
          });
        }
      }
    });

    console.log('🗺️  Google Map initialized');
  }, []);

  // Filter vehicles based on current filters
  const filteredVehicles = useCallback(() => {
    return vehicles.filter(vehicle => {
      // Route type filter (buses vs trains)
      if (!filters.showBuses && vehicle.route_info?.route_type === 3) return false;
      if (!filters.showTrains && vehicle.route_info?.route_type !== 3) return false;
      
      // Selected routes filter
      if (filters.selectedRoutes.length > 0 && !filters.selectedRoutes.includes(vehicle.route_id || '')) {
        return false;
      }
      
      // Delayed vehicles filter
      if (filters.showDelayedOnly && (!vehicle.delay_seconds || vehicle.delay_seconds < filters.minDelaySeconds)) {
        return false;
      }
      
      // Map bounds filter (only show vehicles in current view)
      if (mapBounds) {
        if (vehicle.latitude < mapBounds.south || vehicle.latitude > mapBounds.north ||
            vehicle.longitude < mapBounds.west || vehicle.longitude > mapBounds.east) {
          return false;
        }
      }
      
      return true;
    });
  }, [vehicles, filters, mapBounds]);

  // Update vehicle markers
  useEffect(() => {
    if (!mapInstanceRef.current) return;

    const filtered = filteredVehicles();
    const activeVehicleIds = new Set(filtered.map(v => v.vehicle_id));

    // Remove markers for vehicles no longer visible
    markersRef.current.forEach((marker, vehicleId) => {
      if (!activeVehicleIds.has(vehicleId)) {
        marker.setMap(null);
        markersRef.current.delete(vehicleId);
      }
    });

    // Add/update markers for visible vehicles
    filtered.forEach(vehicle => {
      let marker = markersRef.current.get(vehicle.vehicle_id);
      
      if (!marker) {
        // Create new marker
        const isRail = vehicle.route_info?.route_type !== 3;
        const icon = createVehicleIcon(vehicle, isRail, vehicle.vehicle_id === selectedVehicle?.vehicle_id);
        
        marker = new google.maps.Marker({
          position: { lat: vehicle.latitude, lng: vehicle.longitude },
          map: mapInstanceRef.current,
          icon,
          title: `${vehicle.route_info?.route_short_name || vehicle.route_id} - ${vehicle.vehicle_id}`,
          zIndex: vehicle.vehicle_id === selectedVehicle?.vehicle_id ? 1000 : 1
        });

        marker.addListener('click', () => {
          onVehicleSelect(vehicle.vehicle_id === selectedVehicle?.vehicle_id ? null : vehicle);
        });

        markersRef.current.set(vehicle.vehicle_id, marker);
      } else {
        // Update existing marker
        const isRail = vehicle.route_info?.route_type !== 3;
        const isSelected = vehicle.vehicle_id === selectedVehicle?.vehicle_id;
        const icon = createVehicleIcon(vehicle, isRail, isSelected);
        
        marker.setPosition({ lat: vehicle.latitude, lng: vehicle.longitude });
        marker.setIcon(icon);
        marker.setZIndex(isSelected ? 1000 : 1);
      }
    });

    console.log(`🚌 Updated ${filtered.length} vehicle markers`);
  }, [filteredVehicles, selectedVehicle, onVehicleSelect]);

  // Center map on selected vehicle
  useEffect(() => {
    if (selectedVehicle && mapInstanceRef.current) {
      mapInstanceRef.current.panTo({
        lat: selectedVehicle.latitude,
        lng: selectedVehicle.longitude
      });
      
      if (mapInstanceRef.current.getZoom() < 15) {
        mapInstanceRef.current.setZoom(15);
      }
    }
  }, [selectedVehicle]);

  const createVehicleIcon = (vehicle: EnhancedVehicleData, isRail: boolean, isSelected: boolean): google.maps.Icon => {
    const routeColor = vehicle.route_info?.route_color || (isRail ? '#0066CC' : '#FF6600');
    const size = isSelected ? 24 : 16;
    const strokeWidth = isSelected ? 3 : 2;
    
    // Create SVG icon
    const svg = `
      <svg width="${size}" height="${size}" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="10" fill="${routeColor}" stroke="white" stroke-width="${strokeWidth}"/>
        ${isRail ? 
          '<rect x="8" y="8" width="8" height="8" fill="white" rx="1"/>' : 
          '<path d="M8 6h8c1 0 2 1 2 2v8c0 1-1 2-2 2H8c-1 0-2-1-2-2V8c0-1 1-2 2-2z" fill="white"/>'
        }
        ${isSelected ? '<circle cx="12" cy="12" r="4" fill="white"/>' : ''}
      </svg>
    `;
    
    const blob = new Blob([svg], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);
    
    return {
      url,
      scaledSize: new google.maps.Size(size, size),
      anchor: new google.maps.Point(size / 2, size / 2)
    };
  };

  return (
    <div className="relative w-full h-full">
      <div ref={mapRef} className="w-full h-full" />
      <MapControls
        filters={filters}
        onFiltersChange={onFiltersChange}
        vehicleCount={filteredVehicles().length}
        totalVehicles={vehicles.length}
      />
    </div>
  );
};

const RTDGoogleMap: React.FC<MapComponentProps> = ({ apiKey, ...props }) => {
  const render = (status: string) => {
    switch (status) {
      case 'LOADING':
        return (
          <div className="flex items-center justify-center w-full h-full bg-rtd-light">
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-rtd-primary mx-auto mb-4"></div>
              <p className="text-rtd-dark font-medium">Loading Google Maps...</p>
            </div>
          </div>
        );
      case 'FAILURE':
        return (
          <div className="flex items-center justify-center w-full h-full bg-red-50">
            <div className="text-center text-red-600">
              <p className="font-medium">Failed to load Google Maps</p>
              <p className="text-sm mt-2">Please check your API key and internet connection</p>
            </div>
          </div>
        );
      default:
        return <MapComponent {...props} />;
    }
  };

  return (
    <Wrapper apiKey={apiKey} render={render} libraries={['geometry', 'places']}>
      <MapComponent {...props} />
    </Wrapper>
  );
};

export default RTDGoogleMap;