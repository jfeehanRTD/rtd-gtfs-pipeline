package com.rtd.pipeline.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data model representing a GTFS-RT Trip Update.
 * Contains information about delays and schedule changes for transit trips.
 */
public class TripUpdate {
    
    @JsonProperty("trip_id")
    private String tripId;
    
    @JsonProperty("route_id")
    private String routeId;
    
    @JsonProperty("vehicle_id")
    private String vehicleId;
    
    @JsonProperty("start_date")
    private String startDate;
    
    @JsonProperty("start_time")
    private String startTime;
    
    @JsonProperty("schedule_relationship")
    private String scheduleRelationship;
    
    @JsonProperty("delay_seconds")
    private Integer delaySeconds;
    
    @JsonProperty("timestamp_ms")
    private Long timestamp_ms;
    
    @JsonProperty("stop_id")
    private String stopId;
    
    // Default constructor
    public TripUpdate() {}
    
    // Builder constructor
    private TripUpdate(Builder builder) {
        this.tripId = builder.tripId;
        this.routeId = builder.routeId;
        this.vehicleId = builder.vehicleId;
        this.startDate = builder.startDate;
        this.startTime = builder.startTime;
        this.scheduleRelationship = builder.scheduleRelationship;
        this.delaySeconds = builder.delaySeconds;
        this.timestamp_ms = builder.timestamp_ms;
        this.stopId = builder.stopId;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters and Setters
    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    
    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }
    
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    public String getScheduleRelationship() { return scheduleRelationship; }
    public void setScheduleRelationship(String scheduleRelationship) { this.scheduleRelationship = scheduleRelationship; }
    
    public Integer getDelaySeconds() { return delaySeconds; }
    public void setDelaySeconds(Integer delaySeconds) { this.delaySeconds = delaySeconds; }
    
    public Long getTimestamp() { return timestamp_ms; }
    public void setTimestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; }
    
    public String getStopId() { return stopId; }
    public void setStopId(String stopId) { this.stopId = stopId; }
    
    @Override
    public String toString() {
        return "TripUpdate{" +
                "tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", vehicleId='" + vehicleId + '\'' +
                ", startDate='" + startDate + '\'' +
                ", startTime='" + startTime + '\'' +
                ", scheduleRelationship='" + scheduleRelationship + '\'' +
                ", delaySeconds=" + delaySeconds +
                ", timestamp_ms=" + timestamp_ms +
                ", stopId='" + stopId + '\'' +
                '}';
    }
    
    // Builder pattern
    public static class Builder {
        private String tripId;
        private String routeId;
        private String vehicleId;
        private String startDate;
        private String startTime;
        private String scheduleRelationship;
        private Integer delaySeconds;
        private Long timestamp_ms;
        private String stopId;
        
        public Builder tripId(String tripId) { this.tripId = tripId; return this; }
        public Builder routeId(String routeId) { this.routeId = routeId; return this; }
        public Builder vehicleId(String vehicleId) { this.vehicleId = vehicleId; return this; }
        public Builder startDate(String startDate) { this.startDate = startDate; return this; }
        public Builder startTime(String startTime) { this.startTime = startTime; return this; }
        public Builder scheduleRelationship(String scheduleRelationship) { this.scheduleRelationship = scheduleRelationship; return this; }
        public Builder delaySeconds(Integer delaySeconds) { this.delaySeconds = delaySeconds; return this; }
        public Builder timestamp_ms(Long timestamp_ms) { this.timestamp_ms = timestamp_ms; return this; }
        public Builder stopId(String stopId) { this.stopId = stopId; return this; }
        
        public TripUpdate build() {
            return new TripUpdate(this);
        }
    }
}