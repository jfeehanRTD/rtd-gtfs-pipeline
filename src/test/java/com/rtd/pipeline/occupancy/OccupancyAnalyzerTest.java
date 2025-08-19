package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import com.rtd.pipeline.occupancy.model.VehicleInfo;
import com.rtd.pipeline.occupancy.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for OccupancyAnalyzer service.
 * Tests the APC occupancy calculation algorithm based on Arcadis IBI Group methodology.
 */
public class OccupancyAnalyzerTest {
    
    private OccupancyAnalyzer analyzer;
    private VehicleInfo standardBus; // 36 seats, 8 standing
    private VehicleInfo coachBus;    // 37 seats, 36 standing  
    private VehicleInfo articulated; // 57 seats, 23 standing
    
    @BeforeEach
    void setUp() {
        analyzer = new OccupancyAnalyzer();
        standardBus = new VehicleInfo("TEST001", "TEST001", VehicleType.STANDARD_40FT, 36, 8);
        coachBus = new VehicleInfo("TEST002", "TEST002", VehicleType.COACH, 37, 36);
        articulated = new VehicleInfo("TEST003", "TEST003", VehicleType.ARTICULATED, 57, 23);
    }
    
    @Test
    void testEmptyStatus() {
        // Test EMPTY threshold (≤ 6 passengers)
        assertEquals(OccupancyStatus.EMPTY, analyzer.calculateOccupancyStatus(0, standardBus));
        assertEquals(OccupancyStatus.EMPTY, analyzer.calculateOccupancyStatus(6, standardBus));
        assertEquals(OccupancyStatus.EMPTY, analyzer.calculateOccupancyStatus(3, coachBus));
        assertEquals(OccupancyStatus.EMPTY, analyzer.calculateOccupancyStatus(6, articulated));
    }
    
    @Test
    void testManySeatsAvailable() {
        // Test MANY_SEATS_AVAILABLE (7 to 75% of max seats)
        // Standard bus: 7 to 27 passengers (36 * 0.75 = 27)
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(7, standardBus));
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(27, standardBus));
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(15, standardBus));
        
        // Coach bus: 7 to 27 passengers (37 * 0.75 = 27.75 -> 27)
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(7, coachBus));
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(27, coachBus));
        
        // Articulated: 7 to 42 passengers (57 * 0.75 = 42.75 -> 42)
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(7, articulated));
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(42, articulated));
    }
    
    @Test
    void testFewSeatsAvailable() {
        // Test FEW_SEATS_AVAILABLE (75.1% to 95% of max seats)
        // Standard bus: 28 to 34 passengers (36 * 0.95 = 34.2 -> 34)
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(28, standardBus));
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(34, standardBus));
        
        // Coach bus: 28 to 35 passengers (37 * 0.95 = 35.15 -> 35)
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(28, coachBus));
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(35, coachBus));
        
        // Articulated: 43 to 54 passengers (57 * 0.95 = 54.15 -> 54)
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(43, articulated));
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(54, articulated));
    }
    
    @Test
    void testStandingRoomOnly() {
        // Test STANDING_ROOM_ONLY (95.1% to 50% standing capacity)
        // Standard bus: 35 to 40 passengers (36 + 8*0.5 = 40)
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(35, standardBus));
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(40, standardBus));
        
        // Coach bus: 36 to 55 passengers (37 + 36*0.5 = 55)
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(36, coachBus));
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(55, coachBus));
        
        // Articulated: 55 to 68 passengers (57 + 23*0.5 = 68.5 -> 68)
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(55, articulated));
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(68, articulated));
    }
    
    @Test
    void testCrushedStandingRoomOnly() {
        // Test CRUSHED_STANDING_ROOM_ONLY (51% to 90% standing capacity)
        // Standard bus: 41 to 43 passengers (36 + 8*0.9 = 43.2 -> 43)
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(41, standardBus));
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(43, standardBus));
        
        // Coach bus: 56 to 69 passengers (37 + 36*0.9 = 69.4 -> 69)
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(56, coachBus));
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(69, coachBus));
        
        // Articulated: 69 to 77 passengers (57 + 23*0.9 = 77.7 -> 77)
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(69, articulated));
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(77, articulated));
    }
    
    @Test
    void testFullStatus() {
        // Test FULL (≥ 91% of total capacity)
        // Standard bus: ≥ 44 passengers
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(44, standardBus));
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(50, standardBus));
        
        // Coach bus: ≥ 70 passengers
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(70, coachBus));
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(80, coachBus));
        
        // Articulated: ≥ 78 passengers
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(78, articulated));
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(90, articulated));
    }
    
    @Test
    void testInvalidVehicleInfo() {
        // Test with null vehicle info
        assertEquals(OccupancyStatus.NO_DATA_AVAILABLE, analyzer.calculateOccupancyStatus(25, null));
        
        // Test with invalid vehicle info (zero seats)
        VehicleInfo invalidVehicle = new VehicleInfo("INVALID", "INVALID", VehicleType.UNKNOWN, 0, 0);
        assertEquals(OccupancyStatus.NO_DATA_AVAILABLE, analyzer.calculateOccupancyStatus(25, invalidVehicle));
    }
    
    @Test
    void testOccupancyPercentageCalculation() {
        // Test occupancy percentage calculation
        assertEquals(0.0, analyzer.calculateOccupancyPercentage(0, standardBus), 0.01);
        assertEquals(0.5, analyzer.calculateOccupancyPercentage(22, standardBus), 0.01); // 22/44 = 0.5
        assertEquals(1.0, analyzer.calculateOccupancyPercentage(44, standardBus), 0.01); // Full capacity
        assertEquals(1.25, analyzer.calculateOccupancyPercentage(55, standardBus), 0.01); // Over capacity
        
        // Test with invalid vehicle info
        assertEquals(0.0, analyzer.calculateOccupancyPercentage(25, null), 0.01);
        
        VehicleInfo invalidVehicle = new VehicleInfo("INVALID", "INVALID", VehicleType.UNKNOWN, 0, 0);
        assertEquals(0.0, analyzer.calculateOccupancyPercentage(25, invalidVehicle), 0.01);
    }
    
    @Test
    void testValidationLogic() {
        // Test valid cases
        assertTrue(analyzer.isValidForCalculation(25, standardBus));
        assertTrue(analyzer.isValidForCalculation(0, coachBus));
        assertTrue(analyzer.isValidForCalculation(100, articulated));
        
        // Test invalid cases
        assertFalse(analyzer.isValidForCalculation(-1, standardBus)); // Negative passenger load
        assertFalse(analyzer.isValidForCalculation(25, null)); // Null vehicle info
        
        VehicleInfo invalidVehicle = new VehicleInfo("INVALID", "INVALID", VehicleType.UNKNOWN, 0, 10);
        assertFalse(analyzer.isValidForCalculation(25, invalidVehicle)); // Zero seats
        
        VehicleInfo negativeStands = new VehicleInfo("INVALID2", "INVALID2", VehicleType.UNKNOWN, 30, -5);
        assertFalse(analyzer.isValidForCalculation(25, negativeStands)); // Negative standing capacity
    }
    
    @Test
    void testBoundaryConditions() {
        // Test exact boundary values for standard bus (36 seats, 8 standing)
        
        // Boundary between EMPTY and MANY_SEATS_AVAILABLE
        assertEquals(OccupancyStatus.EMPTY, analyzer.calculateOccupancyStatus(6, standardBus));
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(7, standardBus));
        
        // Boundary between MANY_SEATS_AVAILABLE and FEW_SEATS_AVAILABLE
        assertEquals(OccupancyStatus.MANY_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(27, standardBus));
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(28, standardBus));
        
        // Boundary between FEW_SEATS_AVAILABLE and STANDING_ROOM_ONLY
        assertEquals(OccupancyStatus.FEW_SEATS_AVAILABLE, analyzer.calculateOccupancyStatus(34, standardBus));
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(35, standardBus));
        
        // Boundary between STANDING_ROOM_ONLY and CRUSHED_STANDING_ROOM_ONLY
        assertEquals(OccupancyStatus.STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(40, standardBus));
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(41, standardBus));
        
        // Boundary between CRUSHED_STANDING_ROOM_ONLY and FULL
        assertEquals(OccupancyStatus.CRUSHED_STANDING_ROOM_ONLY, analyzer.calculateOccupancyStatus(43, standardBus));
        assertEquals(OccupancyStatus.FULL, analyzer.calculateOccupancyStatus(44, standardBus));
    }
}