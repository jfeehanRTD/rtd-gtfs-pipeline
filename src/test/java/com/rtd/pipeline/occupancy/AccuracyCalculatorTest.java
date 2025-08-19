package com.rtd.pipeline.occupancy;

import com.rtd.pipeline.occupancy.AccuracyCalculator.AccuracyMetrics;
import com.rtd.pipeline.occupancy.model.OccupancyComparisonRecord;
import com.rtd.pipeline.occupancy.model.OccupancyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AccuracyCalculator service.
 * Tests accuracy calculation functionality based on Arcadis IBI Group methodology.
 */
public class AccuracyCalculatorTest {
    
    private AccuracyCalculator calculator;
    private OccupancyComparisonRecord matchingRecord;
    private OccupancyComparisonRecord nonMatchingRecord;
    private OccupancyComparisonRecord invalidRecord;
    
    @BeforeEach
    void setUp() {
        calculator = new AccuracyCalculator();
        
        LocalDateTime testDate = LocalDateTime.of(2023, 8, 15, 10, 30);
        
        // Create a matching record (both feeds show MANY_SEATS_AVAILABLE)
        matchingRecord = new OccupancyComparisonRecord("V001", "TRIP001", "STOP001", "15", testDate);
        matchingRecord.setGtfsrtOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        matchingRecord.setApcOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        matchingRecord.setPassengerLoad(20);
        matchingRecord.setMaxSeats(36);
        matchingRecord.setMaxStands(8);
        matchingRecord.setGtfsrtOccupancyPercentage(0.45);
        matchingRecord.setOccupancyStatusMatch(true); // Both are MANY_SEATS_AVAILABLE
        
        // Create a non-matching record (GTFS-RT shows FEW_SEATS_AVAILABLE, APC shows MANY_SEATS_AVAILABLE)
        nonMatchingRecord = new OccupancyComparisonRecord("V002", "TRIP002", "STOP002", "121", testDate);
        nonMatchingRecord.setGtfsrtOccupancyStatus(OccupancyStatus.FEW_SEATS_AVAILABLE);
        nonMatchingRecord.setApcOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        nonMatchingRecord.setPassengerLoad(25);
        nonMatchingRecord.setMaxSeats(37);
        nonMatchingRecord.setMaxStands(36);
        nonMatchingRecord.setGtfsrtOccupancyPercentage(0.68);
        nonMatchingRecord.setOccupancyStatusMatch(false); // FEW_SEATS_AVAILABLE != MANY_SEATS_AVAILABLE
        
        // Create an invalid record (has NO_DATA_AVAILABLE status)
        invalidRecord = new OccupancyComparisonRecord("V003", "TRIP003", "STOP003", "0", testDate);
        invalidRecord.setGtfsrtOccupancyStatus(OccupancyStatus.NO_DATA_AVAILABLE);
        invalidRecord.setApcOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        invalidRecord.setPassengerLoad(15);
        invalidRecord.setMaxSeats(36);
        invalidRecord.setMaxStands(8);
    }
    
    @Test
    void testAccuracyMetricsCreation() {
        AccuracyMetrics metrics = new AccuracyMetrics("Test Category", "Test Subcategory");
        assertEquals("Test Category", metrics.getCategory());
        assertEquals("Test Subcategory", metrics.getSubcategory());
        assertEquals(0, metrics.getTotalVPRecords());
        assertEquals(0, metrics.getTotalJoinedRecords());
        assertEquals(0, metrics.getMatchedOccupancyRecords());
        assertEquals(0.0, metrics.getJoinedPercentage());
        assertEquals(0.0, metrics.getAccuracyPercentage());
    }
    
    @Test
    void testAccuracyMetricsPercentageCalculation() {
        AccuracyMetrics metrics = new AccuracyMetrics("Test", "");
        metrics.setTotalVPRecords(1000);
        metrics.setTotalJoinedRecords(850);
        metrics.setMatchedOccupancyRecords(650);
        
        metrics.calculatePercentages();
        
        assertEquals(85.0, metrics.getJoinedPercentage(), 0.01); // 850/1000 * 100
        assertEquals(76.47, metrics.getAccuracyPercentage(), 0.01); // 650/850 * 100
    }
    
    @Test
    void testAccuracyMetricsPercentageCalculationWithZeros() {
        AccuracyMetrics metrics = new AccuracyMetrics("Test", "");
        metrics.setTotalVPRecords(0);
        metrics.setTotalJoinedRecords(0);
        metrics.setMatchedOccupancyRecords(0);
        
        metrics.calculatePercentages();
        
        assertEquals(0.0, metrics.getJoinedPercentage());
        assertEquals(0.0, metrics.getAccuracyPercentage());
    }
    
    @Test
    void testValidComparisonFilter() {
        AccuracyCalculator.ValidComparisonFilter filter = new AccuracyCalculator.ValidComparisonFilter();
        
        // Test valid record
        assertTrue(filter.filter(matchingRecord));
        assertTrue(filter.filter(nonMatchingRecord));
        
        // Test invalid record (has NO_DATA_AVAILABLE)
        assertFalse(filter.filter(invalidRecord));
        
        // Test null record
        assertFalse(filter.filter(null));
        
        // Test record with missing required fields
        OccupancyComparisonRecord incompleteRecord = new OccupancyComparisonRecord();
        incompleteRecord.setVehicleId("V004");
        // Missing other required fields
        assertFalse(filter.filter(incompleteRecord));
    }
    
    @Test
    void testOverallAccuracyMapper() {
        AccuracyCalculator.OverallAccuracyMapper mapper = new AccuracyCalculator.OverallAccuracyMapper();
        
        // Test with matching record
        AccuracyMetrics matchingMetrics = mapper.map(matchingRecord);
        assertEquals("Full Dataset", matchingMetrics.getCategory());
        assertEquals("", matchingMetrics.getSubcategory());
        assertEquals(1, matchingMetrics.getTotalJoinedRecords());
        assertEquals(1, matchingMetrics.getMatchedOccupancyRecords()); // Should be 1 for matching
        
        // Test with non-matching record
        AccuracyMetrics nonMatchingMetrics = mapper.map(nonMatchingRecord);
        assertEquals("Full Dataset", nonMatchingMetrics.getCategory());
        assertEquals(1, nonMatchingMetrics.getTotalJoinedRecords());
        assertEquals(0, nonMatchingMetrics.getMatchedOccupancyRecords()); // Should be 0 for non-matching
    }
    
    @Test
    void testDateAccuracyMapper() {
        AccuracyCalculator.DateAccuracyMapper mapper = new AccuracyCalculator.DateAccuracyMapper();
        
        AccuracyMetrics metrics = mapper.map(matchingRecord);
        assertEquals("By Date", metrics.getCategory());
        assertEquals("2023-08-15", metrics.getSubcategory());
        assertEquals(1, metrics.getTotalJoinedRecords());
        assertEquals(1, metrics.getMatchedOccupancyRecords());
    }
    
    @Test
    void testDateAccuracyMapperWithNullDate() {
        AccuracyCalculator.DateAccuracyMapper mapper = new AccuracyCalculator.DateAccuracyMapper();
        
        OccupancyComparisonRecord recordWithNullDate = new OccupancyComparisonRecord("V001", "TRIP001", "STOP001", "15", null);
        recordWithNullDate.setGtfsrtOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        recordWithNullDate.setApcOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        recordWithNullDate.setPassengerLoad(20);
        recordWithNullDate.setMaxSeats(36);
        recordWithNullDate.setMaxStands(8);
        
        AccuracyMetrics metrics = mapper.map(recordWithNullDate);
        assertEquals("By Date", metrics.getCategory());
        assertEquals("Unknown", metrics.getSubcategory());
    }
    
    @Test
    void testRouteAccuracyMapper() {
        AccuracyCalculator.RouteAccuracyMapper mapper = new AccuracyCalculator.RouteAccuracyMapper();
        
        AccuracyMetrics metrics = mapper.map(matchingRecord);
        assertEquals("By Route", metrics.getCategory());
        assertEquals("15", metrics.getSubcategory()); // Route ID from matching record
        assertEquals(1, metrics.getTotalJoinedRecords());
        assertEquals(1, metrics.getMatchedOccupancyRecords());
    }
    
    @Test
    void testRouteAccuracyMapperWithNullRoute() {
        AccuracyCalculator.RouteAccuracyMapper mapper = new AccuracyCalculator.RouteAccuracyMapper();
        
        OccupancyComparisonRecord recordWithNullRoute = new OccupancyComparisonRecord("V001", "TRIP001", "STOP001", null, LocalDateTime.now());
        recordWithNullRoute.setGtfsrtOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        recordWithNullRoute.setApcOccupancyStatus(OccupancyStatus.MANY_SEATS_AVAILABLE);
        recordWithNullRoute.setPassengerLoad(20);
        recordWithNullRoute.setMaxSeats(36);
        recordWithNullRoute.setMaxStands(8);
        
        AccuracyMetrics metrics = mapper.map(recordWithNullRoute);
        assertEquals("By Route", metrics.getCategory());
        assertEquals("Unknown", metrics.getSubcategory());
    }
    
    @Test
    void testAccuracyMetricsKeySelector() {
        AccuracyCalculator.AccuracyMetricsKeySelector keySelector = new AccuracyCalculator.AccuracyMetricsKeySelector();
        
        AccuracyMetrics metrics1 = new AccuracyMetrics("Full Dataset", "");
        AccuracyMetrics metrics2 = new AccuracyMetrics("By Date", "2023-08-15");
        AccuracyMetrics metrics3 = new AccuracyMetrics("By Route", "15");
        
        assertEquals("Full Dataset|", keySelector.getKey(metrics1));
        assertEquals("By Date|2023-08-15", keySelector.getKey(metrics2));
        assertEquals("By Route|15", keySelector.getKey(metrics3));
    }
    
    @Test
    void testAccuracyMetricsKeySelectorWithNullSubcategory() {
        AccuracyCalculator.AccuracyMetricsKeySelector keySelector = new AccuracyCalculator.AccuracyMetricsKeySelector();
        
        AccuracyMetrics metrics = new AccuracyMetrics("Test Category", null);
        assertEquals("Test Category|", keySelector.getKey(metrics));
    }
    
    @Test
    void testAccuracyMetricsReducer() {
        AccuracyCalculator.AccuracyMetricsReducer reducer = new AccuracyCalculator.AccuracyMetricsReducer();
        
        AccuracyMetrics metrics1 = new AccuracyMetrics("Full Dataset", "");
        metrics1.setTotalVPRecords(500);
        metrics1.setTotalJoinedRecords(400);
        metrics1.setMatchedOccupancyRecords(300);
        
        AccuracyMetrics metrics2 = new AccuracyMetrics("Full Dataset", "");
        metrics2.setTotalVPRecords(300);
        metrics2.setTotalJoinedRecords(250);
        metrics2.setMatchedOccupancyRecords(200);
        
        AccuracyMetrics result = reducer.reduce(metrics1, metrics2);
        
        assertEquals("Full Dataset", result.getCategory());
        assertEquals("", result.getSubcategory());
        assertEquals(800, result.getTotalVPRecords()); // 500 + 300
        assertEquals(650, result.getTotalJoinedRecords()); // 400 + 250
        assertEquals(500, result.getMatchedOccupancyRecords()); // 300 + 200
        assertEquals(81.25, result.getJoinedPercentage(), 0.01); // 650/800 * 100
        assertEquals(76.92, result.getAccuracyPercentage(), 0.01); // 500/650 * 100
    }
    
    @Test
    void testAccuracyMetricsEquality() {
        AccuracyMetrics metrics1 = new AccuracyMetrics("Test", "Subcategory");
        AccuracyMetrics metrics2 = new AccuracyMetrics("Test", "Subcategory");
        AccuracyMetrics metrics3 = new AccuracyMetrics("Test", "Different");
        
        assertEquals(metrics1, metrics2);
        assertNotEquals(metrics1, metrics3);
        assertEquals(metrics1.hashCode(), metrics2.hashCode());
        assertNotEquals(metrics1.hashCode(), metrics3.hashCode());
    }
    
    @Test
    void testAccuracyMetricsToString() {
        AccuracyMetrics metrics = new AccuracyMetrics("Full Dataset", "");
        metrics.setTotalVPRecords(1000);
        metrics.setTotalJoinedRecords(894);
        metrics.setMatchedOccupancyRecords(785);
        metrics.calculatePercentages();
        
        String output = metrics.toString();
        assertTrue(output.contains("Full Dataset"));
        assertTrue(output.contains("1000"));
        assertTrue(output.contains("894"));
        assertTrue(output.contains("785"));
        assertTrue(output.contains("89.4%"));
        assertTrue(output.contains("87.8%"));
    }
}