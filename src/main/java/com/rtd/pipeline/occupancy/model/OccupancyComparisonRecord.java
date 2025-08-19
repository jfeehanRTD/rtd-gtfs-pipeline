package com.rtd.pipeline.occupancy.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model representing a joined record between GTFS-RT Vehicle Position and APC data
 * for occupancy accuracy comparison analysis.
 */
public class OccupancyComparisonRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Common fields used for joining
    private String vehicleId;
    private String tripId;
    private String stopId;
    private String routeId;
    private LocalDateTime serviceDate;
    
    // GTFS-RT specific fields
    private OccupancyStatus gtfsrtOccupancyStatus;
    private double gtfsrtOccupancyPercentage;
    private LocalDateTime gtfsrtTimestamp;
    private String currentStatus;
    private String congestionLevel;
    
    // APC specific fields
    private int passengerLoad;
    private OccupancyStatus apcOccupancyStatus;
    private int maxSeats;
    private int maxStands;
    private LocalDateTime apcTimestamp;
    private boolean lagApplied;
    
    // Comparison results
    private boolean occupancyStatusMatch;
    private int occupancyStatusDifference;
    private double occupancyPercentageDifference;
    
    public OccupancyComparisonRecord() {}
    
    public OccupancyComparisonRecord(String vehicleId, String tripId, String stopId, 
                                   String routeId, LocalDateTime serviceDate) {
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.stopId = stopId;
        this.routeId = routeId;
        this.serviceDate = serviceDate;
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
    
    public String getStopId() {
        return stopId;
    }
    
    public void setStopId(String stopId) {
        this.stopId = stopId;
    }
    
    public String getRouteId() {
        return routeId;
    }
    
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
    
    public LocalDateTime getServiceDate() {
        return serviceDate;
    }
    
    public void setServiceDate(LocalDateTime serviceDate) {
        this.serviceDate = serviceDate;
    }
    
    public OccupancyStatus getGtfsrtOccupancyStatus() {
        return gtfsrtOccupancyStatus;
    }
    
    public void setGtfsrtOccupancyStatus(OccupancyStatus gtfsrtOccupancyStatus) {
        this.gtfsrtOccupancyStatus = gtfsrtOccupancyStatus;
    }
    
    public double getGtfsrtOccupancyPercentage() {
        return gtfsrtOccupancyPercentage;
    }
    
    public void setGtfsrtOccupancyPercentage(double gtfsrtOccupancyPercentage) {
        this.gtfsrtOccupancyPercentage = gtfsrtOccupancyPercentage;
    }
    
    public LocalDateTime getGtfsrtTimestamp() {
        return gtfsrtTimestamp;
    }
    
    public void setGtfsrtTimestamp(LocalDateTime gtfsrtTimestamp) {
        this.gtfsrtTimestamp = gtfsrtTimestamp;
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
    
    public int getPassengerLoad() {
        return passengerLoad;
    }
    
    public void setPassengerLoad(int passengerLoad) {
        this.passengerLoad = passengerLoad;
    }
    
    public OccupancyStatus getApcOccupancyStatus() {
        return apcOccupancyStatus;
    }
    
    public void setApcOccupancyStatus(OccupancyStatus apcOccupancyStatus) {
        this.apcOccupancyStatus = apcOccupancyStatus;
    }
    
    public int getMaxSeats() {
        return maxSeats;
    }
    
    public void setMaxSeats(int maxSeats) {
        this.maxSeats = maxSeats;
    }
    
    public int getMaxStands() {
        return maxStands;
    }
    
    public void setMaxStands(int maxStands) {
        this.maxStands = maxStands;
    }
    
    public LocalDateTime getApcTimestamp() {
        return apcTimestamp;
    }
    
    public void setApcTimestamp(LocalDateTime apcTimestamp) {
        this.apcTimestamp = apcTimestamp;
    }
    
    public boolean isLagApplied() {
        return lagApplied;
    }
    
    public void setLagApplied(boolean lagApplied) {
        this.lagApplied = lagApplied;
    }
    
    public boolean isOccupancyStatusMatch() {
        return occupancyStatusMatch;
    }
    
    public void setOccupancyStatusMatch(boolean occupancyStatusMatch) {
        this.occupancyStatusMatch = occupancyStatusMatch;
    }
    
    public int getOccupancyStatusDifference() {
        return occupancyStatusDifference;
    }
    
    public void setOccupancyStatusDifference(int occupancyStatusDifference) {
        this.occupancyStatusDifference = occupancyStatusDifference;
    }
    
    public double getOccupancyPercentageDifference() {
        return occupancyPercentageDifference;
    }
    
    public void setOccupancyPercentageDifference(double occupancyPercentageDifference) {
        this.occupancyPercentageDifference = occupancyPercentageDifference;
    }
    
    /**
     * Gets the total vehicle capacity.
     * @return Total capacity (seats + standing)
     */
    public int getTotalCapacity() {
        return maxSeats + maxStands;
    }
    
    /**
     * Calculates occupancy percentage based on passenger load and vehicle capacity.
     * @return Occupancy percentage
     */
    public double getCalculatedOccupancyPercentage() {
        if (getTotalCapacity() <= 0) {
            return 0.0;
        }
        return (double) passengerLoad / getTotalCapacity();
    }
    
    /**
     * Checks if this comparison record is valid for analysis.
     * @return true if all required fields are present
     */
    public boolean isValid() {
        return vehicleId != null && !vehicleId.isEmpty() &&
               tripId != null && !tripId.isEmpty() &&
               stopId != null && !stopId.isEmpty() &&
               routeId != null && !routeId.isEmpty() &&
               serviceDate != null &&
               gtfsrtOccupancyStatus != null &&
               apcOccupancyStatus != null &&
               gtfsrtOccupancyStatus != OccupancyStatus.NO_DATA_AVAILABLE &&
               apcOccupancyStatus != OccupancyStatus.NO_DATA_AVAILABLE;
    }
    
    /**
     * Creates a unique key for this comparison record.
     * @return Unique key string
     */
    public String getUniqueKey() {
        return String.join("|",
            vehicleId != null ? vehicleId : "",
            tripId != null ? tripId : "",
            stopId != null ? stopId : "",
            routeId != null ? routeId : "",
            serviceDate != null ? serviceDate.toLocalDate().toString() : ""
        );
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OccupancyComparisonRecord that = (OccupancyComparisonRecord) o;
        return passengerLoad == that.passengerLoad &&
               maxSeats == that.maxSeats &&
               maxStands == that.maxStands &&
               lagApplied == that.lagApplied &&
               occupancyStatusMatch == that.occupancyStatusMatch &&
               occupancyStatusDifference == that.occupancyStatusDifference &&
               Double.compare(that.gtfsrtOccupancyPercentage, gtfsrtOccupancyPercentage) == 0 &&
               Double.compare(that.occupancyPercentageDifference, occupancyPercentageDifference) == 0 &&
               Objects.equals(vehicleId, that.vehicleId) &&
               Objects.equals(tripId, that.tripId) &&
               Objects.equals(stopId, that.stopId) &&
               Objects.equals(routeId, that.routeId) &&
               Objects.equals(serviceDate, that.serviceDate) &&
               gtfsrtOccupancyStatus == that.gtfsrtOccupancyStatus &&
               Objects.equals(gtfsrtTimestamp, that.gtfsrtTimestamp) &&
               Objects.equals(currentStatus, that.currentStatus) &&
               Objects.equals(congestionLevel, that.congestionLevel) &&
               apcOccupancyStatus == that.apcOccupancyStatus &&
               Objects.equals(apcTimestamp, that.apcTimestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, tripId, stopId, routeId, serviceDate, gtfsrtOccupancyStatus, 
                          gtfsrtOccupancyPercentage, gtfsrtTimestamp, currentStatus, congestionLevel, 
                          passengerLoad, apcOccupancyStatus, maxSeats, maxStands, apcTimestamp, 
                          lagApplied, occupancyStatusMatch, occupancyStatusDifference, occupancyPercentageDifference);
    }
    
    @Override
    public String toString() {
        return "OccupancyComparisonRecord{" +
                "vehicleId='" + vehicleId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", gtfsrtOccupancyStatus=" + gtfsrtOccupancyStatus +
                ", apcOccupancyStatus=" + apcOccupancyStatus +
                ", passengerLoad=" + passengerLoad +
                ", occupancyStatusMatch=" + occupancyStatusMatch +
                '}';
    }
}