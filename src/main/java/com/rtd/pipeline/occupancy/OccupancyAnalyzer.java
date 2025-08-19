package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import com.rtd.pipeline.occupancy.model.VehicleInfo;
import java.io.Serializable;

/**
 * Service for analyzing vehicle occupancy status based on APC (Automatic Passenger Counter) data.
 * Implements the algorithm used in the Arcadis IBI Group study for RTD occupancy accuracy analysis.
 */
public class OccupancyAnalyzer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private static final int EMPTY_THRESHOLD = 6;
    private static final double MANY_SEATS_THRESHOLD = 0.75;
    private static final double FEW_SEATS_THRESHOLD = 0.95;
    private static final double STANDING_ROOM_THRESHOLD = 0.5;
    private static final double CRUSHED_STANDING_THRESHOLD = 0.9;
    
    /**
     * Calculates the occupancy status based on passenger load and vehicle capacity.
     * 
     * @param passengerLoad Current number of passengers on vehicle
     * @param vehicleInfo Vehicle capacity information
     * @return OccupancyStatus enum value
     */
    public OccupancyStatus calculateOccupancyStatus(int passengerLoad, VehicleInfo vehicleInfo) {
        if (vehicleInfo == null) {
            return OccupancyStatus.NO_DATA_AVAILABLE;
        }
        
        int maxSeats = vehicleInfo.getMaxSeats();
        int maxStands = vehicleInfo.getMaxStands();
        
        if (maxSeats <= 0) {
            return OccupancyStatus.NO_DATA_AVAILABLE;
        }
        
        // EMPTY: <= 6 passengers
        if (passengerLoad <= EMPTY_THRESHOLD) {
            return OccupancyStatus.EMPTY;
        }
        
        // MANY_SEATS_AVAILABLE: 7 to 75% of max seats
        double manySeatsLimit = maxSeats * MANY_SEATS_THRESHOLD;
        if (passengerLoad <= manySeatsLimit) {
            return OccupancyStatus.MANY_SEATS_AVAILABLE;
        }
        
        // FEW_SEATS_AVAILABLE: 75.1% to 95% of max seats
        double fewSeatsLimit = maxSeats * FEW_SEATS_THRESHOLD;
        if (passengerLoad <= fewSeatsLimit) {
            return OccupancyStatus.FEW_SEATS_AVAILABLE;
        }
        
        // STANDING_ROOM_ONLY: 95.1% to 50% of standing capacity
        double standingRoomLimit = maxSeats + (maxStands * STANDING_ROOM_THRESHOLD);
        if (passengerLoad <= standingRoomLimit) {
            return OccupancyStatus.STANDING_ROOM_ONLY;
        }
        
        // CRUSHED_STANDING_ROOM_ONLY: 51% to 90% of standing capacity
        double crushedStandingLimit = maxSeats + (maxStands * CRUSHED_STANDING_THRESHOLD);
        if (passengerLoad <= crushedStandingLimit) {
            return OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY;
        }
        
        // FULL: >= 91% of total capacity
        return OccupancyStatus.FULL;
    }
    
    /**
     * Calculates occupancy percentage based on passenger load and vehicle capacity.
     * 
     * @param passengerLoad Current number of passengers
     * @param vehicleInfo Vehicle capacity information
     * @return Occupancy percentage (0.0 to 1.0+)
     */
    public double calculateOccupancyPercentage(int passengerLoad, VehicleInfo vehicleInfo) {
        if (vehicleInfo == null || vehicleInfo.getMaxSeats() <= 0) {
            return 0.0;
        }
        
        int totalCapacity = vehicleInfo.getMaxSeats() + vehicleInfo.getMaxStands();
        if (totalCapacity <= 0) {
            return 0.0;
        }
        
        return (double) passengerLoad / totalCapacity;
    }
    
    /**
     * Validates if occupancy status calculation inputs are valid.
     * 
     * @param passengerLoad Passenger load to validate
     * @param vehicleInfo Vehicle info to validate
     * @return true if inputs are valid for calculation
     */
    public boolean isValidForCalculation(int passengerLoad, VehicleInfo vehicleInfo) {
        return passengerLoad >= 0 && 
               vehicleInfo != null && 
               vehicleInfo.getMaxSeats() > 0 && 
               vehicleInfo.getMaxStands() >= 0;
    }
}