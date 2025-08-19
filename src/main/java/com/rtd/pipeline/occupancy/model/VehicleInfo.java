package com.rtd.pipeline.occupancy.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Vehicle capacity information model containing seating and standing capacity data.
 * Used for occupancy calculations based on vehicle type and configuration.
 */
public class VehicleInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String vehicleId;
    private String vehicleCode;
    private VehicleType vehicleType;
    private int maxSeats;
    private int maxStands;
    private String model;
    private String manufacturer;
    
    public VehicleInfo() {}
    
    public VehicleInfo(String vehicleId, String vehicleCode, VehicleType vehicleType, 
                      int maxSeats, int maxStands) {
        this.vehicleId = vehicleId;
        this.vehicleCode = vehicleCode;
        this.vehicleType = vehicleType;
        this.maxSeats = maxSeats;
        this.maxStands = maxStands;
    }
    
    public VehicleInfo(String vehicleId, String vehicleCode, VehicleType vehicleType, 
                      int maxSeats, int maxStands, String model, String manufacturer) {
        this(vehicleId, vehicleCode, vehicleType, maxSeats, maxStands);
        this.model = model;
        this.manufacturer = manufacturer;
    }
    
    // Getters and setters
    public String getVehicleId() {
        return vehicleId;
    }
    
    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }
    
    public String getVehicleCode() {
        return vehicleCode;
    }
    
    public void setVehicleCode(String vehicleCode) {
        this.vehicleCode = vehicleCode;
    }
    
    public VehicleType getVehicleType() {
        return vehicleType;
    }
    
    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
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
    
    public String getModel() {
        return model;
    }
    
    public void setModel(String model) {
        this.model = model;
    }
    
    public String getManufacturer() {
        return manufacturer;
    }
    
    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }
    
    /**
     * Gets total vehicle capacity (seats + standing)
     * @return Total passenger capacity
     */
    public int getTotalCapacity() {
        return maxSeats + maxStands;
    }
    
    /**
     * Checks if vehicle info is valid for occupancy calculations
     * @return true if vehicle has valid capacity data
     */
    public boolean isValid() {
        return maxSeats > 0 && maxStands >= 0;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VehicleInfo that = (VehicleInfo) o;
        return maxSeats == that.maxSeats &&
               maxStands == that.maxStands &&
               Objects.equals(vehicleId, that.vehicleId) &&
               Objects.equals(vehicleCode, that.vehicleCode) &&
               vehicleType == that.vehicleType &&
               Objects.equals(model, that.model) &&
               Objects.equals(manufacturer, that.manufacturer);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vehicleId, vehicleCode, vehicleType, maxSeats, maxStands, model, manufacturer);
    }
    
    @Override
    public String toString() {
        return "VehicleInfo{" +
                "vehicleId='" + vehicleId + '\'' +
                ", vehicleCode='" + vehicleCode + '\'' +
                ", vehicleType=" + vehicleType +
                ", maxSeats=" + maxSeats +
                ", maxStands=" + maxStands +
                ", model='" + model + '\'' +
                ", manufacturer='" + manufacturer + '\'' +
                '}';
    }
}