package com.rtd.pipeline.occupancy.model;

/**
 * Enumeration of vehicle types used in RTD fleet with their typical capacity configurations.
 * Based on the Arcadis IBI Group analysis showing different vehicle configurations.
 */
public enum VehicleType {
    /**
     * Standard 40-foot bus (most common configuration: 36 seats, 8 standing)
     */
    STANDARD_40FT("Standard 40'", 36, 8),
    
    /**
     * Coach bus (typical configuration: 37 seats, 36 standing)
     */
    COACH("Coach", 37, 36),
    
    /**
     * Articulated bus (typical configuration: 57 seats, 23 standing)
     */
    ARTICULATED("Articulated", 57, 23),
    
    /**
     * Light rail vehicle
     */
    LIGHT_RAIL("Light Rail", 64, 100),
    
    /**
     * Small shuttle or cutaway bus
     */
    SHUTTLE("Shuttle", 20, 5),
    
    /**
     * Express bus with higher seating capacity
     */
    EXPRESS("Express", 45, 15),
    
    /**
     * Unknown or undefined vehicle type
     */
    UNKNOWN("Unknown", 0, 0);
    
    private final String displayName;
    private final int defaultMaxSeats;
    private final int defaultMaxStands;
    
    VehicleType(String displayName, int defaultMaxSeats, int defaultMaxStands) {
        this.displayName = displayName;
        this.defaultMaxSeats = defaultMaxSeats;
        this.defaultMaxStands = defaultMaxStands;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getDefaultMaxSeats() {
        return defaultMaxSeats;
    }
    
    public int getDefaultMaxStands() {
        return defaultMaxStands;
    }
    
    public int getDefaultTotalCapacity() {
        return defaultMaxSeats + defaultMaxStands;
    }
    
    /**
     * Attempts to determine vehicle type from string representation.
     * 
     * @param typeString String representation of vehicle type
     * @return VehicleType enum value, UNKNOWN if not recognized
     */
    public static VehicleType fromString(String typeString) {
        if (typeString == null || typeString.trim().isEmpty()) {
            return UNKNOWN;
        }
        
        String normalized = typeString.toUpperCase().trim();
        
        if (normalized.contains("40") || normalized.contains("STANDARD")) {
            return STANDARD_40FT;
        } else if (normalized.contains("COACH")) {
            return COACH;
        } else if (normalized.contains("ARTICULATED") || normalized.contains("ARTIC")) {
            return ARTICULATED;
        } else if (normalized.contains("RAIL") || normalized.contains("TRAIN")) {
            return LIGHT_RAIL;
        } else if (normalized.contains("SHUTTLE") || normalized.contains("CUTAWAY")) {
            return SHUTTLE;
        } else if (normalized.contains("EXPRESS")) {
            return EXPRESS;
        }
        
        return UNKNOWN;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}