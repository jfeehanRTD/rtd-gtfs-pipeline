package com.rtd.pipeline.occupancy.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model representing GTFS-Realtime Vehicle Position data for occupancy analysis.
 * Contains the essential fields needed for comparing with APC data.
 */
public class GTFSRTVehiclePosition implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String vehicleId;
    private String tripId;
    private String routeId;
    private String stopId;
    private String directionId;
    private int currentStopSequence;
    private String currentStatus;
    private String congestionLevel;
    private OccupancyStatus occupancyStatus;
    private double occupancyPercentage;
    private LocalDateTime vehicleTimestamp;
    private LocalDateTime serviceDate;
    private double latitude;
    private double longitude;
    
    public GTFSRTVehiclePosition() {}
    
    public GTFSRTVehiclePosition(String vehicleId, String tripId, String routeId, String stopId) {
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.stopId = stopId;
    }
    
    // Getters and setters
    public String getVehicleId() {
        return vehicleId;
    }
    
    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
    
    public String getTripId() {
        return tripId;
    }
    
    public void setTripId(String tripId) {
        this.tripId = tripId;
    }
    
    public String getRouteId() {
        return routeId;
    }
    
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
    
    public String getStopId() {
        return stopId;
    }
    
    public void setStopId(String stopId) {
        this.stopId = stopId;
    }
    
    public String getDirectionId() {
        return directionId;
    }
    
    public void setDirectionId(String directionId) {
        this.directionId = directionId;
    }
    
    public int getCurrentStopSequence() {
        return currentStopSequence;
    }
    
    public void setCurrentStopSequence(int currentStopSequence) {
        this.currentStopSequence = currentStopSequence;
    }
    
    public String getCurrentStatus() {
        return currentStatus;
    }
    
    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }
    
    public String getCongestionLevel() {
        return congestionLevel;
    }
    
    public void setCongestionLevel(String congestionLevel) {
        this.congestionLevel = congestionLevel;
    }
    
    public OccupancyStatus getOccupancyStatus() {
        return occupancyStatus;
    }
    
    public void setOccupancyStatus(OccupancyStatus occupancyStatus) {
        this.occupancyStatus = occupancyStatus;
    }
    
    public double getOccupancyPercentage() {
        return occupancyPercentage;
    }
    
    public void setOccupancyPercentage(double occupancyPercentage) {
        this.occupancyPercentage = occupancyPercentage;
    }
    
    public LocalDateTime getVehicleTimestamp() {
        return vehicleTimestamp;
    }
    
    public void setVehicleTimestamp(LocalDateTime vehicleTimestamp) {
        this.vehicleTimestamp = vehicleTimestamp;
    }
    
    public LocalDateTime getServiceDate() {
        return serviceDate;
    }
    
    public void setServiceDate(LocalDateTime serviceDate) {
        this.serviceDate = serviceDate;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }
    
    /**
     * Checks if this vehicle position record is valid for analysis.
     * @return true if all required fields are present
     */
    public boolean isValid() {
        return vehicleId != null && !vehicleId.isEmpty() &&
               tripId != null && !tripId.isEmpty() &&
               routeId != null && !routeId.isEmpty() &&
               stopId != null && !stopId.isEmpty() &&
               vehicleTimestamp != null;
    }
    
    /**
     * Checks if this record represents a vehicle "IN_TRANSIT_TO" a stop.
     * @return true if current status indicates in transit
     */
    public boolean isInTransitTo() {
        return "IN_TRANSIT_TO".equals(currentStatus);
    }
    
    /**
     * Creates a unique key for deduplication based on essential fields.
     * @return Unique string key for this record
     */
    public String getUniqueKey() {
        return String.join("|", 
            tripId != null ? tripId : "",
            routeId != null ? routeId : "",
            directionId != null ? directionId : "",
            vehicleId != null ? vehicleId : "",
            String.valueOf(currentStopSequence),
            currentStatus != null ? currentStatus : "",
            stopId != null ? stopId : "",
            congestionLevel != null ? congestionLevel : "",
            occupancyStatus != null ? occupancyStatus.toString() : "",
            String.valueOf(occupancyPercentage),
            serviceDate != null ? serviceDate.toString() : ""
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GTFSRTVehiclePosition that = (GTFSRTVehiclePosition) o;
        return currentStopSequence == that.currentStopSequence &&
               Double.compare(that.occupancyPercentage, occupancyPercentage) == 0 &&
               Double.compare(that.latitude, latitude) == 0 &&
               Double.compare(that.longitude, longitude) == 0 &&
               Objects.equals(vehicleId, that.vehicleId) &&
               Objects.equals(tripId, that.tripId) &&
               Objects.equals(routeId, that.routeId) &&
               Objects.equals(stopId, that.stopId) &&
               Objects.equals(directionId, that.directionId) &&
               Objects.equals(currentStatus, that.currentStatus) &&
               Objects.equals(congestionLevel, that.congestionLevel) &&
               occupancyStatus == that.occupancyStatus &&
               Objects.equals(vehicleTimestamp, that.vehicleTimestamp) &&
               Objects.equals(serviceDate, that.serviceDate);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, tripId, routeId, stopId, directionId, currentStopSequence, 
                          currentStatus, congestionLevel, occupancyStatus, occupancyPercentage, 
                          vehicleTimestamp, serviceDate, latitude, longitude);
    }
    
    @Override
    public String toString() {
        return "GTFSRTVehiclePosition{" +
                "vehicleId='" + vehicleId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", currentStatus='" + currentStatus + '\'' +
                ", occupancyStatus=" + occupancyStatus +
                ", vehicleTimestamp=" + vehicleTimestamp +
                '}';
    }
}