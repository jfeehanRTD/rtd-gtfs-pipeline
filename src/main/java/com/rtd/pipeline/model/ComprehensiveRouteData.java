package com.rtd.pipeline.model;

import java.util.List;
import java.util.Objects;

/**
 * Comprehensive route data model that combines GTFS schedule information 
 * with real-time GTFS-RT data for all RTD vehicles and routes.
 */
public class ComprehensiveRouteData {
    
    // Route Information (from GTFS)
    private String routeId;
    private String routeShortName;
    private String routeLongName;
    private String routeType; // 1 = Light Rail, 3 = Bus
    private String routeColor;
    private String routeTextColor;
    private String agencyId;
    
    // Vehicle Information (from GTFS-RT Vehicle Positions)
    private String vehicleId;
    private String tripId;
    private Double vehicleLatitude;
    private Double vehicleLongitude;
    private Float vehicleBearing;
    private Float vehicleSpeed;
    private String vehicleStatus;
    private String occupancyStatus;
    private String congestionLevel;
    
    // Trip Information (from GTFS-RT Trip Updates)
    private Integer delaySeconds;
    private String scheduleRelationship;
    private String startDate;
    private String startTime;
    
    // Stop Information
    private String currentStopId;
    private String currentStopName;
    private Integer stopSequence;
    private Integer directionId;
    
    // Service Information
    private String serviceId;
    private String tripHeadsign;
    private String blockId;
    
    // Timing
    private Long lastUpdated;
    private Long feedTimestamp;
    
    // Alerts (from GTFS-RT Alerts)
    private List<String> activeAlertIds;
    private List<String> alertDescriptions;
    
    // Calculated/Enriched Data
    private String routeBrandName; // e.g., "Blue Line", "Green Line"
    private Double distanceFromSchedule; // meters
    private String serviceStatus; // "ON_TIME", "DELAYED", "EARLY", "CANCELLED"
    private Double averageSpeed; // km/h over last period
    private Integer passengersOnBoard; // estimated
    
    // Default constructor
    public ComprehensiveRouteData() {}
    
    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ComprehensiveRouteData data = new ComprehensiveRouteData();
        
        public Builder routeId(String routeId) {
            data.routeId = routeId;
            return this;
        }
        
        public Builder routeShortName(String routeShortName) {
            data.routeShortName = routeShortName;
            return this;
        }
        
        public Builder routeLongName(String routeLongName) {
            data.routeLongName = routeLongName;
            return this;
        }
        
        public Builder routeType(String routeType) {
            data.routeType = routeType;
            return this;
        }
        
        public Builder routeColor(String routeColor) {
            data.routeColor = routeColor;
            return this;
        }
        
        public Builder routeTextColor(String routeTextColor) {
            data.routeTextColor = routeTextColor;
            return this;
        }
        
        public Builder agencyId(String agencyId) {
            data.agencyId = agencyId;
            return this;
        }
        
        public Builder vehicleId(String vehicleId) {
            data.vehicleId = vehicleId;
            return this;
        }
        
        public Builder tripId(String tripId) {
            data.tripId = tripId;
            return this;
        }
        
        public Builder vehicleLatitude(Double vehicleLatitude) {
            data.vehicleLatitude = vehicleLatitude;
            return this;
        }
        
        public Builder vehicleLongitude(Double vehicleLongitude) {
            data.vehicleLongitude = vehicleLongitude;
            return this;
        }
        
        public Builder vehicleBearing(Float vehicleBearing) {
            data.vehicleBearing = vehicleBearing;
            return this;
        }
        
        public Builder vehicleSpeed(Float vehicleSpeed) {
            data.vehicleSpeed = vehicleSpeed;
            return this;
        }
        
        public Builder vehicleStatus(String vehicleStatus) {
            data.vehicleStatus = vehicleStatus;
            return this;
        }
        
        public Builder occupancyStatus(String occupancyStatus) {
            data.occupancyStatus = occupancyStatus;
            return this;
        }
        
        public Builder congestionLevel(String congestionLevel) {
            data.congestionLevel = congestionLevel;
            return this;
        }
        
        public Builder delaySeconds(Integer delaySeconds) {
            data.delaySeconds = delaySeconds;
            return this;
        }
        
        public Builder scheduleRelationship(String scheduleRelationship) {
            data.scheduleRelationship = scheduleRelationship;
            return this;
        }
        
        public Builder startDate(String startDate) {
            data.startDate = startDate;
            return this;
        }
        
        public Builder startTime(String startTime) {
            data.startTime = startTime;
            return this;
        }
        
        public Builder currentStopId(String currentStopId) {
            data.currentStopId = currentStopId;
            return this;
        }
        
        public Builder currentStopName(String currentStopName) {
            data.currentStopName = currentStopName;
            return this;
        }
        
        public Builder stopSequence(Integer stopSequence) {
            data.stopSequence = stopSequence;
            return this;
        }
        
        public Builder directionId(Integer directionId) {
            data.directionId = directionId;
            return this;
        }
        
        public Builder serviceId(String serviceId) {
            data.serviceId = serviceId;
            return this;
        }
        
        public Builder tripHeadsign(String tripHeadsign) {
            data.tripHeadsign = tripHeadsign;
            return this;
        }
        
        public Builder blockId(String blockId) {
            data.blockId = blockId;
            return this;
        }
        
        public Builder lastUpdated(Long lastUpdated) {
            data.lastUpdated = lastUpdated;
            return this;
        }
        
        public Builder feedTimestamp(Long feedTimestamp) {
            data.feedTimestamp = feedTimestamp;
            return this;
        }
        
        public Builder activeAlertIds(List<String> activeAlertIds) {
            data.activeAlertIds = activeAlertIds;
            return this;
        }
        
        public Builder alertDescriptions(List<String> alertDescriptions) {
            data.alertDescriptions = alertDescriptions;
            return this;
        }
        
        public Builder routeBrandName(String routeBrandName) {
            data.routeBrandName = routeBrandName;
            return this;
        }
        
        public Builder distanceFromSchedule(Double distanceFromSchedule) {
            data.distanceFromSchedule = distanceFromSchedule;
            return this;
        }
        
        public Builder serviceStatus(String serviceStatus) {
            data.serviceStatus = serviceStatus;
            return this;
        }
        
        public Builder averageSpeed(Double averageSpeed) {
            data.averageSpeed = averageSpeed;
            return this;
        }
        
        public Builder passengersOnBoard(Integer passengersOnBoard) {
            data.passengersOnBoard = passengersOnBoard;
            return this;
        }
        
        public ComprehensiveRouteData build() {
            return data;
        }
    }
    
    // Getters
    public String getRouteId() { return routeId; }
    public String getRouteShortName() { return routeShortName; }
    public String getRouteLongName() { return routeLongName; }
    public String getRouteType() { return routeType; }
    public String getRouteColor() { return routeColor; }
    public String getRouteTextColor() { return routeTextColor; }
    public String getAgencyId() { return agencyId; }
    public String getVehicleId() { return vehicleId; }
    public String getTripId() { return tripId; }
    public Double getVehicleLatitude() { return vehicleLatitude; }
    public Double getVehicleLongitude() { return vehicleLongitude; }
    public Float getVehicleBearing() { return vehicleBearing; }
    public Float getVehicleSpeed() { return vehicleSpeed; }
    public String getVehicleStatus() { return vehicleStatus; }
    public String getOccupancyStatus() { return occupancyStatus; }
    public String getCongestionLevel() { return congestionLevel; }
    public Integer getDelaySeconds() { return delaySeconds; }
    public String getScheduleRelationship() { return scheduleRelationship; }
    public String getStartDate() { return startDate; }
    public String getStartTime() { return startTime; }
    public String getCurrentStopId() { return currentStopId; }
    public String getCurrentStopName() { return currentStopName; }
    public Integer getStopSequence() { return stopSequence; }
    public Integer getDirectionId() { return directionId; }
    public String getServiceId() { return serviceId; }
    public String getTripHeadsign() { return tripHeadsign; }
    public String getBlockId() { return blockId; }
    public Long getLastUpdated() { return lastUpdated; }
    public Long getFeedTimestamp() { return feedTimestamp; }
    public List<String> getActiveAlertIds() { return activeAlertIds; }
    public List<String> getAlertDescriptions() { return alertDescriptions; }
    public String getRouteBrandName() { return routeBrandName; }
    public Double getDistanceFromSchedule() { return distanceFromSchedule; }
    public String getServiceStatus() { return serviceStatus; }
    public Double getAverageSpeed() { return averageSpeed; }
    public Integer getPassengersOnBoard() { return passengersOnBoard; }
    
    // Convenience methods
    public boolean isLightRail() {
        return "1".equals(routeType);
    }
    
    public boolean isBus() {
        return "3".equals(routeType);
    }
    
    public boolean isDelayed() {
        return delaySeconds != null && delaySeconds > 300; // More than 5 minutes
    }
    
    public boolean isOnTime() {
        return delaySeconds != null && Math.abs(delaySeconds) <= 60; // Within 1 minute
    }
    
    public String getDelayStatus() {
        if (delaySeconds == null) return "UNKNOWN";
        if (delaySeconds > 300) return "DELAYED";
        if (delaySeconds < -300) return "EARLY";
        return "ON_TIME";
    }
    
    public double getDelayMinutes() {
        return delaySeconds != null ? delaySeconds / 60.0 : 0.0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComprehensiveRouteData that = (ComprehensiveRouteData) o;
        return Objects.equals(routeId, that.routeId) &&
               Objects.equals(vehicleId, that.vehicleId) &&
               Objects.equals(tripId, that.tripId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(routeId, vehicleId, tripId);
    }
    
    @Override
    public String toString() {
        return String.format("ComprehensiveRouteData{routeId='%s', vehicleId='%s', tripId='%s', " +
                            "position=[%.6f,%.6f], delay=%d seconds, status='%s'}", 
                            routeId, vehicleId, tripId, vehicleLatitude, vehicleLongitude, 
                            delaySeconds, serviceStatus);
    }
}