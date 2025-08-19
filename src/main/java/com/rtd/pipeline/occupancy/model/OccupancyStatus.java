package com.rtd.pipeline.occupancy.model;

/**
 * Enumeration of vehicle occupancy status levels as defined by GTFS-RT specification
 * and used in RTD's real-time occupancy analysis.
 */
public enum OccupancyStatus {
    /**
     * Vehicle has very few passengers (≤ 6 passengers)
     */
    EMPTY,
    
    /**
     * Vehicle has available seating (7 to 75% of max seats)
     */
    MANY_SEATS_AVAILABLE,
    
    /**
     * Vehicle has limited seating available (75.1% to 95% of max seats)
     */
    FEW_SEATS_AVAILABLE,
    
    /**
     * Vehicle is at seating capacity with standing room (95.1% to 50% standing capacity)
     */
    STANDING_ROOM_ONLY,
    
    /**
     * Vehicle is crowded with limited standing room (51% to 90% standing capacity)
     */
    CRUSHED_STANDING_ROOM_ONLY,
    
    /**
     * Vehicle is at or near full capacity (≥ 91% of total capacity)
     */
    FULL,
    
    /**
     * Occupancy data is not available for this vehicle
     */
    NO_DATA_AVAILABLE;
    
    /**
     * Converts string representation to OccupancyStatus enum.
     * 
     * @param status String representation of occupancy status
     * @return Corresponding OccupancyStatus enum value
     */
    public static OccupancyStatus fromString(String status) {
        if (status == null || status.trim().isEmpty()) {
            return NO_DATA_AVAILABLE;
        }
        
        try {
            return OccupancyStatus.valueOf(status.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return NO_DATA_AVAILABLE;
        }
    }
    
    /**
     * Returns numeric representation of occupancy status for analysis.
     * 0=EMPTY, 1=MANY_SEATS_AVAILABLE, 2=FEW_SEATS_AVAILABLE, 
     * 3=STANDING_ROOM_ONLY, 4=CRUSHED_STANDING_ROOM_ONLY, 5=FULL
     * 
     * @return Numeric value representing occupancy level
     */
    public int getNumericValue() {
        switch (this) {
            case EMPTY: return 0;
            case MANY_SEATS_AVAILABLE: return 1;
            case FEW_SEATS_AVAILABLE: return 2;
            case STANDING_ROOM_ONLY: return 3;
            case CRUSHED_STANDING_ROOM_ONLY: return 4;
            case FULL: return 5;
            case NO_DATA_AVAILABLE: 
            default: return -1;
        }
    }
}