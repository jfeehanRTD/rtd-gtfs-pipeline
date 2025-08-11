// OpenStreetMap React Component with RTD Vehicle Visualization using Leaflet

import React, { useEffect, useMemo, useRef } from 'react';
import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import { EnhancedVehicleData, MapFilters, DEFAULT_MAP_CONFIG } from '@/types/rtd';
import L from 'leaflet';
import VehicleMarkers from './VehicleMarkers';
import MapControls from './MapControls';
import 'leaflet/dist/leaflet.css';

// Fix for default markers in react-leaflet
delete (L.Icon.Default.prototype as any)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

interface OpenStreetMapProps {
  vehicles: EnhancedVehicleData[];
  selectedVehicle: EnhancedVehicleData | null;
  filters: MapFilters;
  onVehicleSelect: (vehicle: EnhancedVehicleData | null) => void;
  onFiltersChange: (filters: MapFilters) => void;
  className?: string;
}

// Component to handle map updates and bounds
const MapController: React.FC<{
  selectedVehicle: EnhancedVehicleData | null;
  vehicles: EnhancedVehicleData[];
}> = ({ selectedVehicle, vehicles }) => {
  const map = useMap();
  const previousSelectedRef = useRef<string | null>(null);

  // Center on selected vehicle
  useEffect(() => {
    if (selectedVehicle && selectedVehicle.vehicle_id !== previousSelectedRef.current) {
      map.setView([selectedVehicle.latitude, selectedVehicle.longitude], Math.max(map.getZoom(), 15), {
        animate: true,
        duration: 0.5
      });
      previousSelectedRef.current = selectedVehicle.vehicle_id;
    } else if (!selectedVehicle) {
      previousSelectedRef.current = null;
    }
  }, [selectedVehicle, map]);

  // Auto-fit bounds when vehicles load initially
  useEffect(() => {
    if (vehicles.length > 0 && map.getZoom() === DEFAULT_MAP_CONFIG.zoom) {
      const group = new L.FeatureGroup(
        vehicles.slice(0, 50).map(vehicle => // Use first 50 vehicles for bounds
          L.marker([vehicle.latitude, vehicle.longitude])
        )
      );
      
      try {
        const bounds = group.getBounds();
        if (bounds.isValid()) {
          map.fitBounds(bounds, { padding: [20, 20], maxZoom: 13 });
        }
      } catch (error) {
        console.warn('Could not fit vehicle bounds:', error);
      }
    }
  }, [vehicles.length, map]);

  return null;
};

const OpenStreetMap: React.FC<OpenStreetMapProps> = ({
  vehicles,
  selectedVehicle,
  filters,
  onVehicleSelect,
  onFiltersChange,
  className = ''
}) => {
  // Filter vehicles based on current filters
  const filteredVehicles = useMemo(() => {
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
      
      return true;
    });
  }, [vehicles, filters]);

  // Denver center coordinates
  const center: [number, number] = [DEFAULT_MAP_CONFIG.center.lat, DEFAULT_MAP_CONFIG.center.lng];

  return (
    <div className={`relative w-full h-full ${className}`}>
      <MapContainer
        center={center}
        zoom={DEFAULT_MAP_CONFIG.zoom}
        className="w-full h-full z-0"
        zoomControl={true}
        scrollWheelZoom={true}
        doubleClickZoom={true}
        dragging={true}
        attributionControl={true}
      >
        {/* Base tile layer - OpenStreetMap */}
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          maxZoom={19}
          minZoom={3}
        />

        {/* Alternative tile layers for better transit visualization */}
        {/* Uncomment to use different tile styles */}
        {/*
        <TileLayer
          url="https://{s}.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, Tiles style by <a href="https://www.hotosm.org/" target="_blank">Humanitarian OpenStreetMap Team</a> hosted by <a href="https://openstreetmap.fr/" target="_blank">OpenStreetMap France</a>'
        />
        */}

        {/* Vehicle markers */}
        <VehicleMarkers
          vehicles={filteredVehicles}
          selectedVehicle={selectedVehicle}
          onVehicleSelect={onVehicleSelect}
        />

        {/* Map controller for interactions */}
        <MapController 
          selectedVehicle={selectedVehicle}
          vehicles={filteredVehicles}
        />
      </MapContainer>

      {/* Map controls overlay */}
      <MapControls
        filters={filters}
        onFiltersChange={onFiltersChange}
        vehicleCount={filteredVehicles.length}
        totalVehicles={vehicles.length}
      />
    </div>
  );
};

export default OpenStreetMap;