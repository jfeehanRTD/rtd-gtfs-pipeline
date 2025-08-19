package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.VehicleInfo;
import com.rtd.pipeline.occupancy.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for VehicleCapacityService.
 * Tests vehicle capacity management and bus_info integration functionality.
 */
public class VehicleCapacityServiceTest {
    
    private VehicleCapacityService capacityService;
    private VehicleInfo standardBus;
    private VehicleInfo coachBus;
    private VehicleInfo articulated;
    
    @BeforeEach
    void setUp() {
        capacityService = new VehicleCapacityService();
        standardBus = new VehicleInfo("V001", "BUS001", VehicleType.STANDARD_40FT, 36, 8);
        coachBus = new VehicleInfo("V002", "BUS002", VehicleType.COACH, 37, 36);
        articulated = new VehicleInfo("V003", "BUS003", VehicleType.ARTICULATED, 57, 23);
    }
    
    @Test
    void testRegisterVehicle() {
        // Test successful registration
        capacityService.registerVehicle(standardBus);
        assertEquals(1, capacityService.getRegisteredVehicleCount());
        
        // Test retrieving registered vehicle by ID
        Optional<VehicleInfo> retrieved = capacityService.getVehicleInfo("V001");
        assertTrue(retrieved.isPresent());
        assertEquals(standardBus, retrieved.get());
        
        // Test retrieving registered vehicle by code
        Optional<VehicleInfo> retrievedByCode = capacityService.getVehicleInfoByCode("BUS001");
        assertTrue(retrievedByCode.isPresent());
        assertEquals(standardBus, retrievedByCode.get());
    }
    
    @Test
    void testRegisterMultipleVehicles() {
        capacityService.registerVehicle(standardBus);
        capacityService.registerVehicle(coachBus);
        capacityService.registerVehicle(articulated);
        
        assertEquals(3, capacityService.getRegisteredVehicleCount());
        
        // Verify all vehicles can be retrieved
        assertTrue(capacityService.getVehicleInfo("V001").isPresent());
        assertTrue(capacityService.getVehicleInfo("V002").isPresent());
        assertTrue(capacityService.getVehicleInfo("V003").isPresent());
        
        assertTrue(capacityService.getVehicleInfoByCode("BUS001").isPresent());
        assertTrue(capacityService.getVehicleInfoByCode("BUS002").isPresent());
        assertTrue(capacityService.getVehicleInfoByCode("BUS003").isPresent());
    }
    
    @Test
    void testRegisterInvalidVehicles() {
        int initialCount = capacityService.getRegisteredVehicleCount();
        
        // Test null vehicle
        capacityService.registerVehicle(null);
        assertEquals(initialCount, capacityService.getRegisteredVehicleCount());
        
        // Test invalid vehicle (zero seats)
        VehicleInfo invalidVehicle = new VehicleInfo("INVALID", "INVALID", VehicleType.UNKNOWN, 0, 0);
        capacityService.registerVehicle(invalidVehicle);
        assertEquals(initialCount, capacityService.getRegisteredVehicleCount());
        
        // Test vehicle with negative standing capacity
        VehicleInfo negativeStands = new VehicleInfo("NEG", "NEG", VehicleType.UNKNOWN, 30, -5);
        capacityService.registerVehicle(negativeStands);
        assertEquals(initialCount, capacityService.getRegisteredVehicleCount());
    }
    
    @Test
    void testGetVehicleInfoNotFound() {
        // Test getting non-existent vehicle by ID
        Optional<VehicleInfo> notFound = capacityService.getVehicleInfo("NONEXISTENT");
        assertFalse(notFound.isPresent());
        
        // Test getting non-existent vehicle by code
        Optional<VehicleInfo> notFoundByCode = capacityService.getVehicleInfoByCode("NONEXISTENT");
        assertFalse(notFoundByCode.isPresent());
        
        // Test with null parameters
        Optional<VehicleInfo> nullId = capacityService.getVehicleInfo(null);
        assertFalse(nullId.isPresent());
        
        Optional<VehicleInfo> nullCode = capacityService.getVehicleInfoByCode(null);
        assertFalse(nullCode.isPresent());
    }
    
    @Test
    void testCreateDefaultVehicleInfo() {
        // Test creating default info for specific vehicle types
        VehicleInfo defaultStandard = capacityService.createDefaultVehicleInfo("V100", "BUS100", VehicleType.STANDARD_40FT);
        assertEquals("V100", defaultStandard.getVehicleId());
        assertEquals("BUS100", defaultStandard.getVehicleCode());
        assertEquals(VehicleType.STANDARD_40FT, defaultStandard.getVehicleType());
        assertEquals(36, defaultStandard.getMaxSeats());
        assertEquals(8, defaultStandard.getMaxStands());
        
        VehicleInfo defaultCoach = capacityService.createDefaultVehicleInfo("V101", "BUS101", VehicleType.COACH);
        assertEquals(37, defaultCoach.getMaxSeats());
        assertEquals(36, defaultCoach.getMaxStands());
        
        VehicleInfo defaultArticulated = capacityService.createDefaultVehicleInfo("V102", "BUS102", VehicleType.ARTICULATED);
        assertEquals(57, defaultArticulated.getMaxSeats());
        assertEquals(23, defaultArticulated.getMaxStands());
        
        // Test creating default info without vehicle type (should use standard)
        VehicleInfo defaultUnknown = capacityService.createDefaultVehicleInfo("V103", "BUS103");
        assertEquals(VehicleType.STANDARD_40FT, defaultUnknown.getVehicleType());
        assertEquals(36, defaultUnknown.getMaxSeats());
        assertEquals(8, defaultUnknown.getMaxStands());
    }
    
    @Test
    void testLoadBusInfoData() {
        // Prepare bus_info data
        Map<String, Map<String, Object>> busInfoRecords = new HashMap<>();
        
        // Valid record 1
        Map<String, Object> record1 = new HashMap<>();
        record1.put("vehicle_id", "V200");
        record1.put("vehicle_code", "BUS200");
        record1.put("vehicle_type", "Standard 40'");
        record1.put("max_seats", 36);
        record1.put("max_stands", 8);
        busInfoRecords.put("V200", record1);
        
        // Valid record 2 with string numbers
        Map<String, Object> record2 = new HashMap<>();
        record2.put("vehicle_id", "V201");
        record2.put("vehicle_code", "BUS201");
        record2.put("vehicle_type", "Coach");
        record2.put("max_seats", "37");
        record2.put("max_stands", "36");
        busInfoRecords.put("V201", record2);
        
        // Invalid record (missing data)
        Map<String, Object> record3 = new HashMap<>();
        record3.put("vehicle_id", "V202");
        record3.put("vehicle_code", "BUS202");
        // Missing capacity data
        busInfoRecords.put("V202", record3);
        
        // Load the data
        capacityService.loadBusInfoData(busInfoRecords);
        
        // Verify valid records were loaded
        assertEquals(2, capacityService.getRegisteredVehicleCount());
        
        Optional<VehicleInfo> vehicle1 = capacityService.getVehicleInfo("V200");
        assertTrue(vehicle1.isPresent());
        assertEquals(36, vehicle1.get().getMaxSeats());
        assertEquals(8, vehicle1.get().getMaxStands());
        assertEquals(VehicleType.STANDARD_40FT, vehicle1.get().getVehicleType());
        
        Optional<VehicleInfo> vehicle2 = capacityService.getVehicleInfo("V201");
        assertTrue(vehicle2.isPresent());
        assertEquals(37, vehicle2.get().getMaxSeats());
        assertEquals(36, vehicle2.get().getMaxStands());
        assertEquals(VehicleType.COACH, vehicle2.get().getVehicleType());
        
        // Verify invalid record was not loaded
        Optional<VehicleInfo> vehicle3 = capacityService.getVehicleInfo("V202");
        assertFalse(vehicle3.isPresent());
    }
    
    @Test
    void testLoadBusInfoDataWithVariousDataTypes() {
        Map<String, Map<String, Object>> busInfoRecords = new HashMap<>();
        
        // Record with Double values
        Map<String, Object> record1 = new HashMap<>();
        record1.put("vehicle_id", "V300");
        record1.put("vehicle_code", "BUS300");
        record1.put("vehicle_type", "Articulated");
        record1.put("max_seats", 57.0);
        record1.put("max_stands", 23.0);
        busInfoRecords.put("V300", record1);
        
        // Record with invalid string numbers
        Map<String, Object> record2 = new HashMap<>();
        record2.put("vehicle_id", "V301");
        record2.put("vehicle_code", "BUS301");
        record2.put("vehicle_type", "Standard");
        record2.put("max_seats", "invalid");
        record2.put("max_stands", "also_invalid");
        busInfoRecords.put("V301", record2);
        
        capacityService.loadBusInfoData(busInfoRecords);
        
        // Verify Double values were converted correctly
        Optional<VehicleInfo> vehicle1 = capacityService.getVehicleInfo("V300");
        assertTrue(vehicle1.isPresent());
        assertEquals(57, vehicle1.get().getMaxSeats());
        assertEquals(23, vehicle1.get().getMaxStands());
        
        // Verify invalid numbers were handled (should not be registered due to invalid capacity)
        Optional<VehicleInfo> vehicle2 = capacityService.getVehicleInfo("V301");
        assertFalse(vehicle2.isPresent()); // Invalid vehicle (0 seats) should not be registered
    }
    
    @Test
    void testClearCache() {
        // Add some vehicles
        capacityService.registerVehicle(standardBus);
        capacityService.registerVehicle(coachBus);
        assertEquals(2, capacityService.getRegisteredVehicleCount());
        
        // Clear cache
        capacityService.clearCache();
        assertEquals(0, capacityService.getRegisteredVehicleCount());
        
        // Verify vehicles are no longer retrievable
        assertFalse(capacityService.getVehicleInfo("V001").isPresent());
        assertFalse(capacityService.getVehicleInfoByCode("BUS001").isPresent());
    }
    
    @Test
    void testVehicleOverwrite() {
        // Register initial vehicle
        capacityService.registerVehicle(standardBus);
        assertEquals(1, capacityService.getRegisteredVehicleCount());
        
        // Register vehicle with same ID but different capacity
        VehicleInfo modifiedVehicle = new VehicleInfo("V001", "BUS001_MODIFIED", VehicleType.COACH, 40, 20);
        capacityService.registerVehicle(modifiedVehicle);
        
        // Should still be 1 vehicle (overwritten)
        assertEquals(1, capacityService.getRegisteredVehicleCount());
        
        // Verify the vehicle was updated
        Optional<VehicleInfo> retrieved = capacityService.getVehicleInfo("V001");
        assertTrue(retrieved.isPresent());
        assertEquals(40, retrieved.get().getMaxSeats());
        assertEquals(20, retrieved.get().getMaxStands());
        assertEquals(VehicleType.COACH, retrieved.get().getVehicleType());
    }
}