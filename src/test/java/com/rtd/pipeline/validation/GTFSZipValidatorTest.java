package com.rtd.pipeline.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for GTFSZipValidator
 * Tests the comprehensive GTFS validation functionality
 */
public class GTFSZipValidatorTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSZipValidatorTest.class);
    
    private GTFSZipValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new GTFSZipValidator();
    }
    
    @Test
    @DisplayName("Test validation of main RTD GTFS feed")
    void testMainGTFSFeedValidation() {
        LOG.info("Testing main RTD GTFS feed validation...");
        
        try {
            // Test the main google_transit feed
            String feedUrl = "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip";
            GTFSZipValidator.ValidationReport report = validator.validateSingleFeed("google_transit", feedUrl);
            
            assertNotNull(report, "Validation report should not be null");
            assertEquals("google_transit GTFS Feed", report.getName());
            
            // Log the results
            LOG.info("Validation completed for google_transit");
            LOG.info("Errors: {}", report.getErrorCount());
            LOG.info("Warnings: {}", report.getWarningCount());
            
            // Print summary
            System.out.println("\n=== RTD Main GTFS Feed Validation Results ===");
            System.out.println("Status: " + (report.isValid() ? "✅ VALID" : "❌ INVALID"));
            System.out.println("Errors: " + report.getErrorCount());
            System.out.println("Warnings: " + report.getWarningCount());
            
            // Print first few errors and warnings if any
            if (report.getErrorCount() > 0) {
                System.out.println("\nFirst 5 errors:");
                report.getErrors().stream().limit(5).forEach(error -> 
                    System.out.println("  ❌ " + error));
            }
            
            if (report.getWarningCount() > 0) {
                System.out.println("\nFirst 5 warnings:");
                report.getWarnings().stream().limit(5).forEach(warning -> 
                    System.out.println("  ⚠️ " + warning));
            }
            
            // The test passes if we can download and process the feed successfully
            // We don't require the feed to be 100% valid as RTD may have minor issues
            assertTrue(report.getErrorCount() < 100, 
                      "Error count should be reasonable (< 100), got: " + report.getErrorCount());
            
        } catch (Exception e) {
            LOG.error("Failed to validate main GTFS feed", e);
            fail("Should be able to download and validate main GTFS feed: " + e.getMessage());
        }
    }
    
    @Test
    @DisplayName("Test GTFS file structure validation")
    void testGTFSFileStructure() {
        LOG.info("Testing GTFS file structure validation...");
        
        // Create a mock GTFS file for testing
        String[] headers = {"stop_id", "stop_name", "stop_lat", "stop_lon"};
        java.util.List<java.util.Map<String, String>> rows = new java.util.ArrayList<>();
        
        // Add valid row
        java.util.Map<String, String> validRow = new java.util.HashMap<>();
        validRow.put("stop_id", "1001");
        validRow.put("stop_name", "Test Stop");
        validRow.put("stop_lat", "39.7392");
        validRow.put("stop_lon", "-104.9903");
        rows.add(validRow);
        
        // Add invalid coordinate row
        java.util.Map<String, String> invalidRow = new java.util.HashMap<>();
        invalidRow.put("stop_id", "1002");
        invalidRow.put("stop_name", "Invalid Stop");
        invalidRow.put("stop_lat", "200.0"); // Invalid latitude
        invalidRow.put("stop_lon", "-104.9903");
        rows.add(invalidRow);
        
        GTFSZipValidator.GTFSFile gtfsFile = new GTFSZipValidator.GTFSFile("stops.txt", headers, rows);
        
        // Test file structure
        assertEquals("stops.txt", gtfsFile.getFileName());
        assertEquals(4, gtfsFile.getHeaders().length);
        assertEquals(2, gtfsFile.getRowCount());
        assertTrue(gtfsFile.hasField("stop_id"));
        assertTrue(gtfsFile.hasField("stop_lat"));
        assertFalse(gtfsFile.hasField("nonexistent_field"));
        
        LOG.info("GTFS file structure validation test completed successfully");
    }
    
    @Test
    @DisplayName("Test validation report functionality")
    void testValidationReport() {
        LOG.info("Testing validation report functionality...");
        
        GTFSZipValidator.ValidationReport report = new GTFSZipValidator.ValidationReport("Test Report");
        
        // Test initial state
        assertEquals("Test Report", report.getName());
        assertEquals(0, report.getErrorCount());
        assertEquals(0, report.getWarningCount());
        assertTrue(report.isValid());
        
        // Add errors and warnings
        report.addError("Test error 1");
        report.addError("Test error 2");
        report.addWarning("Test warning 1");
        report.addInfo("Test info 1");
        
        assertEquals(2, report.getErrorCount());
        assertEquals(1, report.getWarningCount());
        assertFalse(report.isValid());
        
        // Test sub-reports
        GTFSZipValidator.ValidationReport subReport = new GTFSZipValidator.ValidationReport("Sub Report");
        subReport.addError("Sub error");
        subReport.addWarning("Sub warning");
        
        report.addSubReport("sub", subReport);
        
        assertEquals(3, report.getErrorCount()); // 2 + 1 from sub-report
        assertEquals(2, report.getWarningCount()); // 1 + 1 from sub-report
        
        // Test report generation
        String reportText = report.generateReport();
        assertNotNull(reportText);
        assertTrue(reportText.contains("Test Report"));
        assertTrue(reportText.contains("INVALID"));
        assertTrue(reportText.contains("Test error 1"));
        assertTrue(reportText.contains("Test warning 1"));
        
        LOG.info("Validation report test completed successfully");
    }
    
    @Test
    @DisplayName("Test URL parsing and redirect handling")
    void testURLHandling() {
        LOG.info("Testing URL handling and redirect following...");
        
        try {
            // Test that we can at least connect to RTD's website
            String testUrl = "https://www.rtd-denver.com/open-records/open-spatial-information/gtfs";
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) 
                new java.net.URL(testUrl).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            // We expect either 200 (success) or 3xx (redirect) - both are fine
            assertTrue(responseCode == 200 || (responseCode >= 300 && responseCode < 400),
                      "Should be able to connect to RTD website, got response: " + responseCode);
            
            connection.disconnect();
            
            LOG.info("URL handling test completed - response code: {}", responseCode);
            
        } catch (Exception e) {
            LOG.warn("Could not test URL handling due to network issues: {}", e.getMessage());
            // Don't fail the test for network issues during testing
        }
    }
    
    @Test
    @DisplayName("Test GTFS data validation utilities")
    void testDataValidationUtilities() {
        LOG.info("Testing GTFS data validation utilities...");
        
        // We'll test some validation logic indirectly by creating a validator
        // and checking that it can handle basic validation scenarios
        
        GTFSZipValidator.ValidationReport report = new GTFSZipValidator.ValidationReport("Utility Test");
        
        // Test that the report can handle various types of messages
        report.addError("Error with special characters: äöü & <script>");
        report.addWarning("Warning with numbers: 123.45");
        report.addInfo("Info with long text: " + "x".repeat(1000));
        
        String reportText = report.generateReport();
        assertNotNull(reportText);
        assertTrue(reportText.length() > 0);
        
        // Test that error/warning counts work correctly
        assertEquals(1, report.getErrorCount());
        assertEquals(1, report.getWarningCount());
        assertFalse(report.isValid());
        
        LOG.info("Data validation utilities test completed successfully");
    }
    
    @Test
    @DisplayName("Integration test - validate small feed if available")
    void testSmallFeedValidation() {
        LOG.info("Testing validation with a smaller RTD feed...");
        
        try {
            // Try to validate the Bustang feed as it's likely smaller
            String feedUrl = "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=bustang-co-us.zip";
            GTFSZipValidator.ValidationReport report = validator.validateSingleFeed("bustang-co-us", feedUrl);
            
            assertNotNull(report, "Validation report should not be null");
            
            LOG.info("Small feed validation completed");
            LOG.info("Feed: bustang-co-us");
            LOG.info("Errors: {}", report.getErrorCount());
            LOG.info("Warnings: {}", report.getWarningCount());
            LOG.info("Valid: {}", report.isValid());
            
            // Log a summary of the validation
            System.out.println("\n=== Bustang GTFS Feed Validation Results ===");
            System.out.println("Status: " + (report.isValid() ? "✅ VALID" : "❌ INVALID"));
            System.out.println("Errors: " + report.getErrorCount());
            System.out.println("Warnings: " + report.getWarningCount());
            
            // The test passes if we can process the feed
            assertTrue(report.getErrorCount() < 50, 
                      "Error count should be reasonable for small feed, got: " + report.getErrorCount());
            
        } catch (java.io.IOException e) {
            LOG.warn("Could not download Bustang feed for testing: {}", e.getMessage());
            // Don't fail the test if the feed is temporarily unavailable
        } catch (Exception e) {
            LOG.error("Unexpected error during small feed validation", e);
            fail("Unexpected error: " + e.getMessage());
        }
    }
}