package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.VehicleInfo;
import com.rtd.pipeline.occupancy.model.VehicleType;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing vehicle capacity information and bus_info data integration.
 * Provides vehicle capacity lookup functionality for occupancy analysis.
 */
public class VehicleCapacityService implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final Map<String, VehicleInfo> vehicleInfoCache = new HashMap<>();
    private final Map<String, VehicleInfo> vehicleCodeToInfo = new HashMap<>();
    
    /**
     * Registers vehicle capacity information in the service.
     * 
     * @param vehicleInfo Vehicle capacity information to register
     */
    public void registerVehicle(VehicleInfo vehicleInfo) {
        if (vehicleInfo != null && vehicleInfo.isValid()) {
            if (vehicleInfo.getVehicleId() != null) {
                vehicleInfoCache.put(vehicleInfo.getVehicleId(), vehicleInfo);
            }
            if (vehicleInfo.getVehicleCode() != null) {
                vehicleCodeToInfo.put(vehicleInfo.getVehicleCode(), vehicleInfo);
            }
        }
    }
    
    /**
     * Gets vehicle information by vehicle ID.
     * 
     * @param vehicleId Vehicle ID to lookup
     * @return Optional containing VehicleInfo if found
     */
    public Optional<VehicleInfo> getVehicleInfo(String vehicleId) {
        if (vehicleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(vehicleInfoCache.get(vehicleId));
    }
    
    /**
     * Gets vehicle information by vehicle code.
     * 
     * @param vehicleCode Vehicle code to lookup
     * @return Optional containing VehicleInfo if found
     */
    public Optional<VehicleInfo> getVehicleInfoByCode(String vehicleCode) {
        if (vehicleCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(vehicleCodeToInfo.get(vehicleCode));
    }
    
    /**
     * Creates default vehicle info based on vehicle type when specific info is unavailable.
     * 
     * @param vehicleId Vehicle ID
     * @param vehicleCode Vehicle code
     * @param vehicleType Vehicle type
     * @return VehicleInfo with default capacity values for the type
     */
    public VehicleInfo createDefaultVehicleInfo(String vehicleId, String vehicleCode, VehicleType vehicleType) {
        return new VehicleInfo(
            vehicleId,
            vehicleCode,
            vehicleType,
            vehicleType.getDefaultMaxSeats(),
            vehicleType.getDefaultMaxStands()
        );
    }
    
    /**
     * Creates default vehicle info when vehicle type is unknown.
     * Uses standard 40ft bus configuration as fallback.
     * 
     * @param vehicleId Vehicle ID
     * @param vehicleCode Vehicle code
     * @return VehicleInfo with standard bus capacity
     */
    public VehicleInfo createDefaultVehicleInfo(String vehicleId, String vehicleCode) {
        return createDefaultVehicleInfo(vehicleId, vehicleCode, VehicleType.STANDARD_40FT);
    }
    
    /**
     * Bulk loads vehicle capacity data from bus_info table format.
     * Expected format: vehicleId, vehicleCode, vehicleType, maxSeats, maxStands
     * 
     * @param busInfoRecords Map of vehicle records with capacity data
     */
    public void loadBusInfoData(Map<String, Map<String, Object>> busInfoRecords) {
        for (Map<String, Object> record : busInfoRecords.values()) {
            try {
                String vehicleId = (String) record.get("vehicle_id");
                String vehicleCode = (String) record.get("vehicle_code");
                String typeString = (String) record.get("vehicle_type");
                Object maxSeatsObj = record.get("max_seats");
                Object maxStandsObj = record.get("max_stands");
                
                if (vehicleId != null && maxSeatsObj != null && maxStandsObj != null) {
                    VehicleType vehicleType = VehicleType.fromString(typeString);
                    int maxSeats = convertToInt(maxSeatsObj);
                    int maxStands = convertToInt(maxStandsObj);
                    
                    VehicleInfo vehicleInfo = new VehicleInfo(vehicleId, vehicleCode, vehicleType, maxSeats, maxStands);
                    registerVehicle(vehicleInfo);
                }
            } catch (Exception e) {
                // Log error but continue processing other records
                System.err.println("Error processing bus_info record: " + e.getMessage());
            }
        }
    }
    
    /**
     * Gets the number of registered vehicles.
     * 
     * @return Count of registered vehicles
     */
    public int getRegisteredVehicleCount() {
        return vehicleInfoCache.size();
    }
    
    /**
     * Clears all cached vehicle information.
     */
    public void clearCache() {
        vehicleInfoCache.clear();
        vehicleCodeToInfo.clear();
    }
    
    /**
     * Utility method to safely convert Object to int.
     */
    private int convertToInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}