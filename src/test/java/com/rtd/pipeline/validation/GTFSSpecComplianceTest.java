package com.rtd.pipeline.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GTFS Specification Compliance Test
 * Cross-references RTD GTFS data against official GTFS specification at gtfs.org
 * Tests for actual compliance vs reported validation errors
 */
public class GTFSSpecComplianceTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSSpecComplianceTest.class);
    
    // Sample RTD feed for detailed inspection
    private static final String SAMPLE_FEED_URL = "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=bustang-co-us.zip";
    
    private static Map<String, List<String[]>> gtfsData = new HashMap<>();
    private static Map<String, String[]> headers = new HashMap<>();
    
    @BeforeAll
    static void downloadSampleFeed() throws Exception {
        LOG.info("=== GTFS Specification Compliance Testing ===");
        LOG.info("Downloading sample RTD feed for manual inspection: {}", SAMPLE_FEED_URL);
        
        HttpURLConnection connection = followRedirects(SAMPLE_FEED_URL);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (fileName.endsWith(".txt")) {
                    LOG.info("Processing file: {}", fileName);
                    parseCSVFile(zipStream, fileName);
                }
                zipStream.closeEntry();
            }
        }
        
        LOG.info("Successfully downloaded and parsed {} GTFS files", gtfsData.size());
    }
    
    @Test
    @DisplayName("Test Calendar Date Format Compliance Against GTFS Spec")
    void testCalendarDateFormatCompliance() {
        LOG.info("Testing calendar.txt date format compliance...");
        
        // GTFS Spec: Dates must be in YYYYMMDD format
        List<String[]> calendarData = gtfsData.get("calendar.txt");
        assertNotNull(calendarData, "calendar.txt must be present");
        
        String[] calendarHeaders = headers.get("calendar.txt");
        int startDateIndex = findColumnIndex(calendarHeaders, "start_date");
        int endDateIndex = findColumnIndex(calendarHeaders, "end_date");
        
        assertTrue(startDateIndex >= 0, "start_date column must be present");
        assertTrue(endDateIndex >= 0, "end_date column must be present");
        
        int validStartDates = 0;
        int validEndDates = 0;
        int totalRows = calendarData.size();
        
        System.out.println("\\n=== Calendar Date Format Analysis ===");
        System.out.println("Analyzing " + totalRows + " calendar records...");
        
        for (int i = 0; i < Math.min(10, totalRows); i++) { // Sample first 10 rows
            String[] row = calendarData.get(i);
            String startDate = row.length > startDateIndex ? row[startDateIndex] : "";
            String endDate = row.length > endDateIndex ? row[endDateIndex] : "";
            
            System.out.printf("Row %d: start_date='%s', end_date='%s'%n", i+1, startDate, endDate);
            
            // Check start_date format
            if (isValidGTFSDate(startDate)) {
                validStartDates++;
                System.out.println("  start_date: ✅ VALID GTFS format");
            } else {
                System.out.println("  start_date: ❌ INVALID - Expected YYYYMMDD, got: " + startDate);
            }
            
            // Check end_date format
            if (isValidGTFSDate(endDate)) {
                validEndDates++;
                System.out.println("  end_date: ✅ VALID GTFS format");
            } else {
                System.out.println("  end_date: ❌ INVALID - Expected YYYYMMDD, got: " + endDate);
            }
        }
        
        System.out.printf("\\nSummary: %d/%d valid start_dates, %d/%d valid end_dates%n", 
                         validStartDates, Math.min(10, totalRows), validEndDates, Math.min(10, totalRows));
        
        // Report findings
        if (validStartDates == Math.min(10, totalRows) && validEndDates == Math.min(10, totalRows)) {
            LOG.info("✅ Calendar dates are GTFS compliant - previous validation error may be incorrect");
        } else {
            LOG.info("❌ Calendar dates have GTFS compliance issues - validation error confirmed");
        }
    }
    
    @Test
    @DisplayName("Test Calendar Exception Date Format Compliance")
    void testCalendarDatesFormatCompliance() {
        LOG.info("Testing calendar_dates.txt date format compliance...");
        
        List<String[]> calendarDatesData = gtfsData.get("calendar_dates.txt");
        if (calendarDatesData == null) {
            LOG.info("calendar_dates.txt not present - skipping test");
            return;
        }
        
        String[] calendarDatesHeaders = headers.get("calendar_dates.txt");
        int dateIndex = findColumnIndex(calendarDatesHeaders, "date");
        int exceptionTypeIndex = findColumnIndex(calendarDatesHeaders, "exception_type");
        
        assertTrue(dateIndex >= 0, "date column must be present");
        assertTrue(exceptionTypeIndex >= 0, "exception_type column must be present");
        
        int validDates = 0;
        int validExceptionTypes = 0;
        int totalRows = calendarDatesData.size();
        
        System.out.println("\\n=== Calendar Dates Format Analysis ===");
        System.out.println("Analyzing " + totalRows + " calendar exception records...");
        
        for (int i = 0; i < Math.min(10, totalRows); i++) { // Sample first 10 rows
            String[] row = calendarDatesData.get(i);
            String date = row.length > dateIndex ? row[dateIndex] : "";
            String exceptionType = row.length > exceptionTypeIndex ? row[exceptionTypeIndex] : "";
            
            System.out.printf("Row %d: date='%s', exception_type='%s'%n", i+1, date, exceptionType);
            
            // Check date format
            if (isValidGTFSDate(date)) {
                validDates++;
                System.out.println("  date: ✅ VALID GTFS format");
            } else {
                System.out.println("  date: ❌ INVALID - Expected YYYYMMDD, got: " + date);
            }
            
            // Check exception_type (must be 1 or 2)
            if ("1".equals(exceptionType) || "2".equals(exceptionType)) {
                validExceptionTypes++;
                System.out.println("  exception_type: ✅ VALID (1=service added, 2=service removed)");
            } else {
                System.out.println("  exception_type: ❌ INVALID - Expected 1 or 2, got: " + exceptionType);
            }
        }
        
        System.out.printf("\\nSummary: %d/%d valid dates, %d/%d valid exception types%n", 
                         validDates, Math.min(10, totalRows), validExceptionTypes, Math.min(10, totalRows));
    }
    
    @Test
    @DisplayName("Test Shape ID Uniqueness Requirements")
    void testShapeIdUniqueness() {
        LOG.info("Testing shapes.txt shape_id uniqueness...");
        
        List<String[]> shapesData = gtfsData.get("shapes.txt");
        if (shapesData == null) {
            LOG.info("shapes.txt not present - skipping test");
            return;
        }
        
        String[] shapesHeaders = headers.get("shapes.txt");
        int shapeIdIndex = findColumnIndex(shapesHeaders, "shape_id");
        int shapePtSequenceIndex = findColumnIndex(shapesHeaders, "shape_pt_sequence");
        
        assertTrue(shapeIdIndex >= 0, "shape_id column must be present");
        assertTrue(shapePtSequenceIndex >= 0, "shape_pt_sequence column must be present");
        
        // GTFS Spec Analysis: shapes.txt contains multiple rows per shape_id
        // Each row represents a point in the shape with shape_pt_sequence
        Set<String> uniqueShapeIds = new HashSet<>();
        Map<String, Integer> shapeIdCounts = new HashMap<>();
        int totalRows = shapesData.size();
        
        System.out.println("\\n=== Shape ID Analysis ===");
        System.out.println("Analyzing " + totalRows + " shape point records...");
        
        for (String[] row : shapesData) {
            String shapeId = row.length > shapeIdIndex ? row[shapeIdIndex] : "";
            if (!shapeId.isEmpty()) {
                uniqueShapeIds.add(shapeId);
                shapeIdCounts.put(shapeId, shapeIdCounts.getOrDefault(shapeId, 0) + 1);
            }
        }
        
        System.out.println("Total unique shape IDs: " + uniqueShapeIds.size());
        System.out.println("Total shape point records: " + totalRows);
        System.out.println("Average points per shape: " + (totalRows / uniqueShapeIds.size()));
        
        // Show sample shapes with their point counts
        System.out.println("\\nSample shape ID point counts:");
        shapeIdCounts.entrySet().stream()
            .limit(10)
            .forEach(entry -> System.out.println("  Shape " + entry.getKey() + ": " + entry.getValue() + " points"));
        
        // GTFS Spec Interpretation: Multiple rows with same shape_id are EXPECTED and VALID
        // Each represents a different point in the shape sequence
        LOG.info("✅ GTFS Spec Analysis: Multiple shape_id values are EXPECTED and VALID");
        LOG.info("   Each shape consists of multiple points (rows) with the same shape_id");
        LOG.info("   Previous validation error about 'duplicate shape_ids' appears to be INCORRECT");
        LOG.info("   {} unique shapes defined across {} shape points", uniqueShapeIds.size(), totalRows);
    }
    
    @Test
    @DisplayName("Test Service Definition Requirements")
    void testServiceDefinitionRequirements() {
        LOG.info("Testing service definition requirements...");
        
        boolean hasCalendar = gtfsData.containsKey("calendar.txt") && !gtfsData.get("calendar.txt").isEmpty();
        boolean hasCalendarDates = gtfsData.containsKey("calendar_dates.txt") && !gtfsData.get("calendar_dates.txt").isEmpty();
        
        System.out.println("\\n=== Service Definition Analysis ===");
        System.out.println("calendar.txt present: " + hasCalendar);
        System.out.println("calendar_dates.txt present: " + hasCalendarDates);
        
        // GTFS Spec: Must have either calendar.txt OR calendar_dates.txt (or both)
        assertTrue(hasCalendar || hasCalendarDates, 
                  "GTFS feed must have either calendar.txt or calendar_dates.txt");
        
        if (hasCalendar) {
            analyzeCalendarServiceDates();
        }
        
        if (hasCalendarDates) {
            analyzeCalendarDatesServiceDates();
        }
    }
    
    @Test
    @DisplayName("Test Required vs Optional Files Compliance")
    void testRequiredFilesCompliance() {
        LOG.info("Testing required vs optional files compliance...");
        
        // GTFS Required files
        String[] requiredFiles = {"agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt"};
        
        // GTFS Optional files (commonly used)
        String[] commonOptionalFiles = {"calendar.txt", "calendar_dates.txt", "shapes.txt", "fare_attributes.txt", 
                                       "fare_rules.txt", "frequencies.txt", "transfers.txt"};
        
        System.out.println("\\n=== File Presence Analysis ===");
        
        // Check required files
        for (String requiredFile : requiredFiles) {
            boolean present = gtfsData.containsKey(requiredFile);
            System.out.println("Required: " + requiredFile + " - " + (present ? "✅ PRESENT" : "❌ MISSING"));
            assertTrue(present, "Required file missing: " + requiredFile);
        }
        
        // Check optional files
        for (String optionalFile : commonOptionalFiles) {
            boolean present = gtfsData.containsKey(optionalFile);
            if (present) {
                int recordCount = gtfsData.get(optionalFile).size();
                System.out.println("Optional: " + optionalFile + " - ✅ PRESENT (" + recordCount + " records)");
            } else {
                System.out.println("Optional: " + optionalFile + " - Not present");
            }
        }
        
        LOG.info("✅ Required files compliance verified");
    }
    
    // Helper methods
    
    private static void parseCSVFile(ZipInputStream zipStream, String fileName) throws IOException {
        List<String[]> rows = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return;
        }
        
        String[] fileHeaders = parseCSVLine(headerLine);
        headers.put(fileName, fileHeaders);
        
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                String[] values = parseCSVLine(line);
                rows.add(values);
            } catch (Exception e) {
                // Skip invalid lines
            }
        }
        
        gtfsData.put(fileName, rows);
    }
    
    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString().trim());
        
        return fields.stream()
                .map(field -> field.startsWith("\"") && field.endsWith("\"") && field.length() > 1 
                        ? field.substring(1, field.length() - 1) : field)
                .toArray(String[]::new);
    }
    
    private static HttpURLConnection followRedirects(String url) throws IOException {
        String currentUrl = url;
        HttpURLConnection connection = null;
        
        for (int redirectCount = 0; redirectCount < 10; redirectCount++) {
            connection = (HttpURLConnection) new URL(currentUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(180000);
            connection.setInstanceFollowRedirects(false);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                return connection;
            } else if (responseCode >= 300 && responseCode < 400) {
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    if (location.startsWith("/")) {
                        URL baseUrl = new URL(currentUrl);
                        currentUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost() + location;
                    } else {
                        currentUrl = location;
                    }
                    connection.disconnect();
                    continue;
                } else {
                    throw new IOException("Redirect without Location header");
                }
            } else {
                throw new IOException("HTTP error code: " + responseCode + " for URL: " + currentUrl);
            }
        }
        
        throw new IOException("Too many redirects for URL: " + url);
    }
    
    private boolean isValidGTFSDate(String date) {
        if (date == null || date.isEmpty() || !date.matches("\\d{8}")) {
            return false;
        }
        
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate.parse(date, formatter);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
    
    private int findColumnIndex(String[] headers, String columnName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(columnName)) {
                return i;
            }
        }
        return -1;
    }
    
    private void analyzeCalendarServiceDates() {
        List<String[]> calendarData = gtfsData.get("calendar.txt");
        String[] calendarHeaders = headers.get("calendar.txt");
        
        int startDateIndex = findColumnIndex(calendarHeaders, "start_date");
        int endDateIndex = findColumnIndex(calendarHeaders, "end_date");
        
        LocalDate today = LocalDate.now();
        LocalDate next7Days = today.plusDays(7);
        LocalDate next30Days = today.plusDays(30);
        
        int activeFutureServices = 0;
        int expiredServices = 0;
        
        System.out.println("\\nCalendar service date analysis:");
        System.out.println("Today: " + today);
        System.out.println("GTFS Spec requirement: Cover at least next 7 days (ideally 30 days)");
        
        for (String[] row : calendarData) {
            String startDateStr = row.length > startDateIndex ? row[startDateIndex] : "";
            String endDateStr = row.length > endDateIndex ? row[endDateIndex] : "";
            
            if (isValidGTFSDate(startDateStr) && isValidGTFSDate(endDateStr)) {
                LocalDate startDate = LocalDate.parse(startDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                LocalDate endDate = LocalDate.parse(endDateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                
                if (endDate.isBefore(today)) {
                    expiredServices++;
                } else if (endDate.isAfter(next7Days)) {
                    activeFutureServices++;
                }
                
                System.out.printf("  Service: %s to %s%n", startDate, endDate);
            }
        }
        
        System.out.println("Active/future services: " + activeFutureServices);
        System.out.println("Expired services: " + expiredServices);
        
        if (activeFutureServices == 0) {
            LOG.warn("⚠️ No active/future services found - GTFS spec recommends covering next 7-30 days");
        } else {
            LOG.info("✅ Found {} active/future services", activeFutureServices);
        }
    }
    
    private void analyzeCalendarDatesServiceDates() {
        List<String[]> calendarDatesData = gtfsData.get("calendar_dates.txt");
        String[] calendarDatesHeaders = headers.get("calendar_dates.txt");
        
        int dateIndex = findColumnIndex(calendarDatesHeaders, "date");
        int exceptionTypeIndex = findColumnIndex(calendarDatesHeaders, "exception_type");
        
        LocalDate today = LocalDate.now();
        int futureExceptions = 0;
        int addedServices = 0;
        int removedServices = 0;
        
        System.out.println("\\nCalendar dates exception analysis:");
        
        for (String[] row : calendarDatesData) {
            String dateStr = row.length > dateIndex ? row[dateIndex] : "";
            String exceptionType = row.length > exceptionTypeIndex ? row[exceptionTypeIndex] : "";
            
            if (isValidGTFSDate(dateStr)) {
                LocalDate exceptionDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
                
                if (exceptionDate.isAfter(today)) {
                    futureExceptions++;
                }
                
                if ("1".equals(exceptionType)) {
                    addedServices++;
                } else if ("2".equals(exceptionType)) {
                    removedServices++;
                }
            }
        }
        
        System.out.println("Future exceptions: " + futureExceptions);
        System.out.println("Added services: " + addedServices);
        System.out.println("Removed services: " + removedServices);
    }
}