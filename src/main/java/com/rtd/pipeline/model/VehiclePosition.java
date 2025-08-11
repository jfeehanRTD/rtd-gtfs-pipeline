package com.rtd.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model representing a GTFS-RT Vehicle Position.
 * Contains information about the real-time location and status of a transit vehicle.
 */
public class VehiclePosition {
    
    @JsonProperty("vehicle_id")
    private String vehicleId;
    
    @JsonProperty("trip_id")
    private String tripId;
    
    @JsonProperty("route_id")
    private String routeId;
    
    @JsonProperty("latitude")
    private Double latitude;
    
    @JsonProperty("longitude")
    private Double longitude;
    
    @JsonProperty("bearing")
    private Float bearing;
    
    @JsonProperty("speed")
    private Float speed;
    
    @JsonProperty("timestamp_ms")
    private Long timestamp_ms;
    
    @JsonProperty("current_status")
    private String currentStatus;
    
    @JsonProperty("congestion_level")
    private String congestionLevel;
    
    @JsonProperty("occupancy_status")
    private String occupancyStatus;
    
    @JsonProperty("stop_id")
    private String stopId;
    
    // Default constructor
    public VehiclePosition() {}
    
    // Builder constructor
    private VehiclePosition(Builder builder) {
        this.vehicleId = builder.vehicleId;
        this.tripId = builder.tripId;
        this.routeId = builder.routeId;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.bearing = builder.bearing;
        this.speed = builder.speed;
        this.timestamp_ms = builder.timestamp_ms;
        this.currentStatus = builder.currentStatus;
        this.congestionLevel = builder.congestionLevel;
        this.occupancyStatus = builder.occupancyStatus;
        this.stopId = builder.stopId;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public Float getBearing() { return bearing; }
    public void setBearing(Float bearing) { this.bearing = bearing; }
    
    public Float getSpeed() { return speed; }
    public void setSpeed(Float speed) { this.speed = speed; }
    
    public Long getTimestamp() { return timestamp_ms; }
    public void setTimestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; }
    
    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }
    
    public String getCongestionLevel() { return congestionLevel; }
    public void setCongestionLevel(String congestionLevel) { this.congestionLevel = congestionLevel; }
    
    public String getOccupancyStatus() { return occupancyStatus; }
    public void setOccupancyStatus(String occupancyStatus) { this.occupancyStatus = occupancyStatus; }
    
    public String getStopId() { return stopId; }
    public void setStopId(String stopId) { this.stopId = stopId; }
    
    @Override
    public String toString() {
        return "VehiclePosition{" +
                "vehicleId='" + vehicleId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", bearing=" + bearing +
                ", speed=" + speed +
                ", timestamp_ms=" + timestamp_ms +
                ", currentStatus='" + currentStatus + '\'' +
                ", congestionLevel='" + congestionLevel + '\'' +
                ", occupancyStatus='" + occupancyStatus + '\'' +
                ", stopId='" + stopId + '\'' +
                '}';
    }
    
    // Builder pattern
    public static class Builder {
        private String vehicleId;
        private String tripId;
        private String routeId;
        private Double latitude;
        private Double longitude;
        private Float bearing;
        private Float speed;
        private Long timestamp_ms;
        private String currentStatus;
        private String congestionLevel;
        private String occupancyStatus;
        private String stopId;
        
        public Builder vehicleId(String vehicleId) { this.vehicleId = vehicleId; return this; }
        public Builder tripId(String tripId) { this.tripId = tripId; return this; }
        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder latitude(Double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(Double longitude) { this.longitude = longitude; return this; }
        public Builder bearing(Float bearing) { this.bearing = bearing; return this; }
        public Builder speed(Float speed) { this.speed = speed; return this; }
        public Builder timestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; return this; }
        public Builder currentStatus(String currentStatus) { this.currentStatus = currentStatus; return this; }
        public Builder congestionLevel(String congestionLevel) { this.congestionLevel = congestionLevel; return this; }
        public Builder occupancyStatus(String occupancyStatus) { this.occupancyStatus = occupancyStatus; return this; }
        public Builder stopId(String stopId) { this.stopId = stopId; return this; }
        
        public VehiclePosition build() {
            return new VehiclePosition(this);
        }
    }
}