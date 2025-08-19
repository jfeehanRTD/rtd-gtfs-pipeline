package com.rtd.pipeline.occupancy.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Model representing Automatic Passenger Counter (APC) data for occupancy analysis.
 * Contains passenger load data and calculated occupancy status for comparison with GTFS-RT.
 */
public class APCData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String vehicleCode;
    private String tripCode;
    private String stopCode;
    private LocalDateTime operatingDate;
    private int patternIndex;
    private int passengerLoad;
    private OccupancyStatus apcOccupancyStatus;
    private int maxSeats;
    private int maxStands;
    private String routeId;
    private LocalDateTime timestamp;
    private boolean lagApplied;
    
    public APCData() {}
    
    public APCData(String vehicleCode, String tripCode, String stopCode, 
                  LocalDateTime operatingDate, int passengerLoad) {
        this.vehicleCode = vehicleCode;
        this.tripCode = tripCode;
        this.stopCode = stopCode;
        this.operatingDate = operatingDate;
        this.passengerLoad = passengerLoad;
    }
    
    // Getters and setters
    public String getVehicleCode() {
        return vehicleCode;
    }
    
    public void setVehicleCode(String vehicleCode) {
        this.vehicleCode = vehicleCode;
    }
    
    public String getTripCode() {
        return tripCode;
    }
    
    public void setTripCode(String tripCode) {
        this.tripCode = tripCode;
    }
    
    public String getStopCode() {
        return stopCode;
    }
    
    public void setStopCode(String stopCode) {
        this.stopCode = stopCode;
    }
    
    public LocalDateTime getOperatingDate() {
        return operatingDate;
    }
    
    public void setOperatingDate(LocalDateTime operatingDate) {
        this.operatingDate = operatingDate;
    }
    
    public int getPatternIndex() {
        return patternIndex;
    }
    
    public void setPatternIndex(int patternIndex) {
        this.patternIndex = patternIndex;
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
    
    public String getRouteId() {
        return routeId;
    }
    
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isLagApplied() {
        return lagApplied;
    }
    
    public void setLagApplied(boolean lagApplied) {
        this.lagApplied = lagApplied;
    }
    
    /**
     * Checks if this APC data record is valid for analysis.
     * @return true if all required fields are present and passenger load is non-negative
     */
    public boolean isValid() {
        return vehicleCode != null && !vehicleCode.isEmpty() &&
               tripCode != null && !tripCode.isEmpty() &&
               stopCode != null && !stopCode.isEmpty() &&
               operatingDate != null &&
               passengerLoad >= 0;
    }
    
    /**
     * Checks if this record has valid occupancy status (not null or NO_DATA_AVAILABLE).
     * @return true if APC occupancy status is valid for comparison
     */
    public boolean hasValidOccupancyStatus() {
        return apcOccupancyStatus != null && apcOccupancyStatus != OccupancyStatus.NO_DATA_AVAILABLE;
    }
    
    /**
     * Creates a join key for matching with GTFS-RT data.
     * Format: vehicleCode|tripCode|stopCode|operatingDate
     * @return Join key string
     */
    public String getJoinKey() {
        return String.join("|",
            vehicleCode != null ? vehicleCode : "",
            tripCode != null ? tripCode : "",
            stopCode != null ? stopCode : "",
            operatingDate != null ? operatingDate.toLocalDate().toString() : ""
        );
    }
    
    /**
     * Gets the total vehicle capacity from APC data.
     * @return Total capacity (seats + standing)
     */
    public int getTotalCapacity() {
        return maxSeats + maxStands;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        APCData apcData = (APCData) o;
        return patternIndex == apcData.patternIndex &&
               passengerLoad == apcData.passengerLoad &&
               maxSeats == apcData.maxSeats &&
               maxStands == apcData.maxStands &&
               lagApplied == apcData.lagApplied &&
               Objects.equals(vehicleCode, apcData.vehicleCode) &&
               Objects.equals(tripCode, apcData.tripCode) &&
               Objects.equals(stopCode, apcData.stopCode) &&
               Objects.equals(operatingDate, apcData.operatingDate) &&
               apcOccupancyStatus == apcData.apcOccupancyStatus &&
               Objects.equals(routeId, apcData.routeId) &&
               Objects.equals(timestamp, apcData.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vehicleCode, tripCode, stopCode, operatingDate, patternIndex, 
                          passengerLoad, apcOccupancyStatus, maxSeats, maxStands, 
                          routeId, timestamp, lagApplied);
    }
    
    @Override
    public String toString() {
        return "APCData{" +
                "vehicleCode='" + vehicleCode + '\'' +
                ", tripCode='" + tripCode + '\'' +
                ", stopCode='" + stopCode + '\'' +
                ", operatingDate=" + operatingDate +
                ", passengerLoad=" + passengerLoad +
                ", apcOccupancyStatus=" + apcOccupancyStatus +
                ", maxSeats=" + maxSeats +
                ", maxStands=" + maxStands +
                '}';
    }
}