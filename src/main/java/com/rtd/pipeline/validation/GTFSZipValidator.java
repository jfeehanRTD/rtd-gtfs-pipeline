package com.rtd.pipeline.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Comprehensive GTFS ZIP file validator for all RTD GTFS feeds.
 * Downloads and validates all GTFS ZIP files from RTD's website according to GTFS specification.
 */
public class GTFSZipValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSZipValidator.class);
    
    // RTD GTFS feed URLs
    // RTD-operated feeds only (excludes CDOT bustang)
    private static final Map<String, String> RTD_GTFS_URLS = Map.of(
        "google_transit", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip",
        "google_transit_flex", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit_flex.zip",
        "commuter_rail", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Commuter_Rail_GTFS.zip",
        "light_rail", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Light_Rail_GTFS.zip",
        "motorbus", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Motorbus_GTFS.zip",
        "purchased_motorbus", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Purchased_Transportation_Motorbus_GTFS.zip",
        "purchased_commuter", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Purchased_Transportation_Commuter_Rail_GTFS.zip"
    );
    
    // Non-RTD feeds available from RTD website (CDOT bustang)
    private static final Map<String, String> NON_RTD_GTFS_URLS = Map.of(
        "bustang-co-us", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=bustang-co-us.zip"
    );
    
    // Combined URL mapping for all feeds
    private static final Map<String, String> ALL_GTFS_URLS = new HashMap<String, String>() {{
        putAll(RTD_GTFS_URLS);
        putAll(NON_RTD_GTFS_URLS);
    }};
    
    // GTFS specification constants
    private static final Set<String> REQUIRED_FILES = Set.of(
        "agency.txt", "stops.txt", "routes.txt", "trips.txt", "stop_times.txt"
    );
    
    private static final Set<String> OPTIONAL_FILES = Set.of(
        "calendar.txt", "calendar_dates.txt", "fare_attributes.txt", "fare_rules.txt", 
        "shapes.txt", "frequencies.txt", "transfers.txt", "pathways.txt", "levels.txt",
        "feed_info.txt", "translations.txt", "attributions.txt", "fare_media.txt",
        "fare_products.txt", "fare_leg_rules.txt", "fare_transfer_rules.txt",
        "areas.txt", "stop_areas.txt", "networks.txt", "route_networks.txt"
    );
    
    // Field validation patterns
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^[0-9A-Fa-f]{6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[\\d\\-\\+\\(\\)\\s\\.]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");
    
    // RTD full service area bounds (8 counties: Boulder, Broomfield, Denver, Jefferson, Adams, Arapahoe, Douglas, Weld)
    // RTD serves 2,342 square miles covering metro Denver/Boulder region  
    private static final double RTD_MIN_LATITUDE = 39.0;   // Southern boundary (Douglas County)
    private static final double RTD_MAX_LATITUDE = 40.6;   // Northern boundary (Boulder/Weld Counties)
    private static final double RTD_MIN_LONGITUDE = -105.8; // Western boundary (Boulder/Jefferson Counties)
    private static final double RTD_MAX_LONGITUDE = -104.3; // Eastern boundary (Adams/Arapahoe Counties)
    
    // Route type enumeration (GTFS specification)
    private static final Map<String, String> ROUTE_TYPES = Map.of(
        "0", "Tram, Streetcar, Light rail",
        "1", "Subway, Metro",
        "2", "Rail",
        "3", "Bus",
        "4", "Ferry",
        "5", "Cable tram",
        "6", "Aerial lift, suspended cable car",
        "7", "Funicular",
        "11", "Trolleybus",
        "12", "Monorail"
    );
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Validates all RTD GTFS feeds
     */
    public ValidationReport validateAllFeeds() {
        LOG.info("=== RTD GTFS Comprehensive Validation Report ===");
        
        ValidationReport overallReport = new ValidationReport("RTD GTFS Feeds Validation");
        
        for (Map.Entry<String, String> entry : ALL_GTFS_URLS.entrySet()) {
            String feedName = entry.getKey();
            String feedUrl = entry.getValue();
            
            LOG.info("Validating feed: {}", feedName);
            
            try {
                ValidationReport feedReport = validateSingleFeed(feedName, feedUrl);
                overallReport.addSubReport(feedName, feedReport);
                
                LOG.info("Feed {} validation completed: {} errors, {} warnings", 
                        feedName, feedReport.getErrorCount(), feedReport.getWarningCount());
                
            } catch (Exception e) {
                LOG.error("Failed to validate feed {}: {}", feedName, e.getMessage(), e);
                overallReport.addError("Failed to validate feed " + feedName + ": " + e.getMessage());
            }
        }
        
        LOG.info("=== Validation Summary ===");
        LOG.info("Total feeds processed: {}", ALL_GTFS_URLS.size());
        LOG.info("Total errors: {}", overallReport.getErrorCount());
        LOG.info("Total warnings: {}", overallReport.getWarningCount());
        
        return overallReport;
    }
    
    /**
     * Validates a single GTFS feed
     */
    public ValidationReport validateSingleFeed(String feedName, String feedUrl) throws IOException {
        ValidationReport report = new ValidationReport(feedName + " GTFS Feed");
        
        // Download and parse the GTFS feed
        Map<String, GTFSFile> gtfsFiles = downloadAndParseGTFS(feedUrl, report);
        
        if (gtfsFiles.isEmpty()) {
            report.addError("No GTFS files found in feed");
            return report;
        }
        
        // 1. File presence validation
        validateFilePresence(gtfsFiles, report);
        
        // 2. File structure validation
        validateFileStructures(gtfsFiles, report);
        
        // 3. Data content validation
        validateDataContent(gtfsFiles, report);
        
        // 4. Cross-reference validation
        validateCrossReferences(gtfsFiles, report);
        
        // 5. Geographic validation (RTD-specific)
        validateGeographic(gtfsFiles, report);
        
        // 6. Service validation
        validateService(gtfsFiles, report);
        
        return report;
    }
    
    private Map<String, GTFSFile> downloadAndParseGTFS(String feedUrl, ValidationReport report) throws IOException {
        Map<String, GTFSFile> gtfsFiles = new HashMap<>();
        
        LOG.info("Downloading GTFS feed from: {}", feedUrl);
        
        HttpURLConnection connection = followRedirects(feedUrl);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            
            while ((entry = zipStream.getNextEntry()) != null) {
                String fileName = entry.getName();
                
                if (fileName.endsWith(".txt")) {
                    LOG.debug("Processing file: {}", fileName);
                    
                    GTFSFile gtfsFile = parseCSVFromZip(zipStream, fileName);
                    gtfsFiles.put(fileName, gtfsFile);
                    
                    LOG.debug("Loaded {} with {} records", fileName, gtfsFile.getRowCount());
                }
                zipStream.closeEntry();
            }
        }
        
        LOG.info("Successfully downloaded and parsed {} GTFS files", gtfsFiles.size());
        return gtfsFiles;
    }
    
    private HttpURLConnection followRedirects(String url) throws IOException {
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
    
    private GTFSFile parseCSVFromZip(ZipInputStream zipStream, String fileName) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(zipStream, StandardCharsets.UTF_8));
        
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return new GTFSFile(fileName, new String[0], rows);
        }
        
        String[] headers = parseCSVLine(headerLine);
        
        String line;
        int lineNumber = 2; // Header is line 1
        while ((line = reader.readLine()) != null) {
            try {
                String[] values = parseCSVLine(line);
                
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String value = i < values.length ? values[i] : "";
                    if (!value.isEmpty()) {
                        row.put(headers[i], value);
                    }
                }
                rows.add(row);
            } catch (Exception e) {
                LOG.debug("Error parsing line {} in {}: {}", lineNumber, fileName, e.getMessage());
            }
            lineNumber++;
        }
        
        return new GTFSFile(fileName, headers, rows);
    }
    
    private String[] parseCSVLine(String line) {
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
    
    private void validateFilePresence(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating file presence");
        
        // Check required files
        for (String requiredFile : REQUIRED_FILES) {
            if (!gtfsFiles.containsKey(requiredFile)) {
                report.addError("Required file missing: " + requiredFile);
            } else if (gtfsFiles.get(requiredFile).getRowCount() == 0) {
                report.addError("Required file is empty: " + requiredFile);
            }
        }
        
        // Log optional files
        for (String optionalFile : OPTIONAL_FILES) {
            if (gtfsFiles.containsKey(optionalFile)) {
                report.addInfo("Optional file present: " + optionalFile + 
                             " (" + gtfsFiles.get(optionalFile).getRowCount() + " records)");
            }
        }
        
        // Check for unknown files
        for (String fileName : gtfsFiles.keySet()) {
            if (!REQUIRED_FILES.contains(fileName) && !OPTIONAL_FILES.contains(fileName)) {
                report.addWarning("Unknown GTFS file: " + fileName);
            }
        }
    }
    
    private void validateFileStructures(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating file structures");
        
        validateAgencyStructure(gtfsFiles.get("agency.txt"), report);
        validateStopsStructure(gtfsFiles.get("stops.txt"), report);
        validateRoutesStructure(gtfsFiles.get("routes.txt"), report);
        validateTripsStructure(gtfsFiles.get("trips.txt"), report);
        validateStopTimesStructure(gtfsFiles.get("stop_times.txt"), report);
        validateCalendarStructure(gtfsFiles.get("calendar.txt"), report);
        validateCalendarDatesStructure(gtfsFiles.get("calendar_dates.txt"), report);
        validateShapesStructure(gtfsFiles.get("shapes.txt"), report);
    }
    
    private void validateAgencyStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("agency_name", "agency_url", "agency_timezone");
        Set<String> optionalFields = Set.of("agency_id", "agency_lang", "agency_phone", "agency_fare_url", "agency_email");
        
        validateRequiredFields(file, requiredFields, report);
        validateFieldFormats(file, Map.of(
            "agency_url", URL_PATTERN,
            "agency_fare_url", URL_PATTERN,
            "agency_phone", PHONE_PATTERN,
            "agency_email", EMAIL_PATTERN
        ), report);
    }
    
    private void validateStopsStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("stop_id", "stop_name", "stop_lat", "stop_lon");
        
        validateRequiredFields(file, requiredFields, report);
        validateCoordinateFields(file, "stop_lat", "stop_lon", report);
    }
    
    private void validateRoutesStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("route_id", "route_type");
        
        validateRequiredFields(file, requiredFields, report);
        validateRouteTypes(file, report);
        validateColorFields(file, report);
    }
    
    private void validateTripsStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("route_id", "service_id", "trip_id");
        
        validateRequiredFields(file, requiredFields, report);
    }
    
    private void validateStopTimesStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("trip_id", "stop_id", "stop_sequence");
        
        validateRequiredFields(file, requiredFields, report);
        validateTimeFields(file, report);
    }
    
    private void validateCalendarStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "start_date", "end_date");
        
        validateRequiredFields(file, requiredFields, report);
        validateDateFields(file, Set.of("start_date", "end_date"), report);
        validateBooleanFields(file, Set.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"), report);
    }
    
    private void validateCalendarDatesStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("service_id", "date", "exception_type");
        
        validateRequiredFields(file, requiredFields, report);
        validateDateFields(file, Set.of("date"), report);
        validateExceptionTypes(file, report);
    }
    
    private void validateShapesStructure(GTFSFile file, ValidationReport report) {
        if (file == null) return;
        
        Set<String> requiredFields = Set.of("shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence");
        
        validateRequiredFields(file, requiredFields, report);
        validateCoordinateFields(file, "shape_pt_lat", "shape_pt_lon", report);
    }
    
    private void validateRequiredFields(GTFSFile file, Set<String> requiredFields, ValidationReport report) {
        Set<String> fileHeaders = Set.of(file.getHeaders());
        
        for (String field : requiredFields) {
            if (!fileHeaders.contains(field)) {
                report.addError(file.getFileName() + " missing required field: " + field);
            }
        }
    }
    
    private void validateFieldFormats(GTFSFile file, Map<String, Pattern> fieldPatterns, ValidationReport report) {
        for (Map.Entry<String, Pattern> entry : fieldPatterns.entrySet()) {
            String fieldName = entry.getKey();
            Pattern pattern = entry.getValue();
            
            if (!file.hasField(fieldName)) continue;
            
            int errorCount = 0;
            for (Map<String, String> row : file.getRows()) {
                String value = row.get(fieldName);
                if (value != null && !value.isEmpty() && !pattern.matcher(value).matches()) {
                    errorCount++;
                    if (errorCount <= 5) { // Limit error reporting
                        report.addError(file.getFileName() + " invalid " + fieldName + " format: " + value);
                    }
                }
            }
            
            if (errorCount > 5) {
                report.addError(file.getFileName() + " has " + (errorCount - 5) + " more " + fieldName + " format errors");
            }
        }
    }
    
    private void validateCoordinateFields(GTFSFile file, String latField, String lonField, ValidationReport report) {
        if (!file.hasField(latField) || !file.hasField(lonField)) return;
        
        int invalidCoordinates = 0;
        int outsideRTD = 0;
        
        for (Map<String, String> row : file.getRows()) {
            String latStr = row.get(latField);
            String lonStr = row.get(lonField);
            
            if (latStr == null || lonStr == null || latStr.isEmpty() || lonStr.isEmpty()) continue;
            
            try {
                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);
                
                // Basic coordinate validation
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    invalidCoordinates++;
                    continue;
                }
                
                // RTD service area validation (8-county region)
                if (lat < RTD_MIN_LATITUDE || lat > RTD_MAX_LATITUDE ||
                    lon < RTD_MIN_LONGITUDE || lon > RTD_MAX_LONGITUDE) {
                    outsideRTD++;
                }
                
            } catch (NumberFormatException e) {
                invalidCoordinates++;
            }
        }
        
        if (invalidCoordinates > 0) {
            report.addError(file.getFileName() + " has " + invalidCoordinates + " invalid coordinates");
        }
        
        if (outsideRTD > 0) {
            report.addWarning(file.getFileName() + " has " + outsideRTD + " coordinates outside RTD service area");
        }
    }
    
    private void validateRouteTypes(GTFSFile file, ValidationReport report) {
        if (!file.hasField("route_type")) return;
        
        int invalidTypes = 0;
        Map<String, Integer> typeCounts = new HashMap<>();
        
        for (Map<String, String> row : file.getRows()) {
            String routeType = row.get("route_type");
            if (routeType != null && !routeType.isEmpty()) {
                if (!ROUTE_TYPES.containsKey(routeType)) {
                    invalidTypes++;
                } else {
                    typeCounts.put(routeType, typeCounts.getOrDefault(routeType, 0) + 1);
                }
            }
        }
        
        if (invalidTypes > 0) {
            report.addError(file.getFileName() + " has " + invalidTypes + " invalid route types");
        }
        
        // Report route type distribution
        for (Map.Entry<String, Integer> entry : typeCounts.entrySet()) {
            report.addInfo(file.getFileName() + " route type " + entry.getKey() + 
                          " (" + ROUTE_TYPES.get(entry.getKey()) + "): " + entry.getValue() + " routes");
        }
    }
    
    private void validateColorFields(GTFSFile file, ValidationReport report) {
        validateFieldFormats(file, Map.of(
            "route_color", COLOR_PATTERN,
            "route_text_color", COLOR_PATTERN
        ), report);
    }
    
    private void validateTimeFields(GTFSFile file, ValidationReport report) {
        Set<String> timeFields = Set.of("arrival_time", "departure_time");
        
        for (String field : timeFields) {
            if (!file.hasField(field)) continue;
            
            int invalidTimes = 0;
            for (Map<String, String> row : file.getRows()) {
                String timeValue = row.get(field);
                if (timeValue != null && !timeValue.isEmpty()) {
                    if (!isValidGTFSTime(timeValue)) {
                        invalidTimes++;
                    }
                }
            }
            
            if (invalidTimes > 0) {
                report.addError(file.getFileName() + " has " + invalidTimes + " invalid " + field + " values");
            }
        }
    }
    
    private void validateDateFields(GTFSFile file, Set<String> dateFields, ValidationReport report) {
        for (String field : dateFields) {
            if (!file.hasField(field)) continue;
            
            int invalidDates = 0;
            for (Map<String, String> row : file.getRows()) {
                String dateValue = row.get(field);
                if (dateValue != null && !dateValue.isEmpty()) {
                    if (!isValidGTFSDate(dateValue)) {
                        invalidDates++;
                    }
                }
            }
            
            if (invalidDates > 0) {
                report.addError(file.getFileName() + " has " + invalidDates + " invalid " + field + " values");
            }
        }
    }
    
    private void validateBooleanFields(GTFSFile file, Set<String> booleanFields, ValidationReport report) {
        for (String field : booleanFields) {
            if (!file.hasField(field)) continue;
            
            int invalidValues = 0;
            for (Map<String, String> row : file.getRows()) {
                String value = row.get(field);
                if (value != null && !value.isEmpty()) {
                    if (!"0".equals(value) && !"1".equals(value)) {
                        invalidValues++;
                    }
                }
            }
            
            if (invalidValues > 0) {
                report.addError(file.getFileName() + " has " + invalidValues + " invalid " + field + " values (must be 0 or 1)");
            }
        }
    }
    
    private void validateExceptionTypes(GTFSFile file, ValidationReport report) {
        if (!file.hasField("exception_type")) return;
        
        int invalidTypes = 0;
        for (Map<String, String> row : file.getRows()) {
            String exceptionType = row.get("exception_type");
            if (exceptionType != null && !exceptionType.isEmpty()) {
                if (!"1".equals(exceptionType) && !"2".equals(exceptionType)) {
                    invalidTypes++;
                }
            }
        }
        
        if (invalidTypes > 0) {
            report.addError(file.getFileName() + " has " + invalidTypes + " invalid exception_type values (must be 1 or 2)");
        }
    }
    
    private void validateDataContent(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating data content");
        
        // Validate unique IDs
        validateUniqueIds(gtfsFiles, report);
        
        // Validate required values
        validateRequiredValues(gtfsFiles, report);
    }
    
    private void validateUniqueIds(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        Map<String, String> idFields = Map.of(
            "agency.txt", "agency_id",
            "stops.txt", "stop_id",
            "routes.txt", "route_id",
            "trips.txt", "trip_id",
            "shapes.txt", "shape_id"
        );
        
        for (Map.Entry<String, String> entry : idFields.entrySet()) {
            String fileName = entry.getKey();
            String idField = entry.getValue();
            
            GTFSFile file = gtfsFiles.get(fileName);
            if (file == null || !file.hasField(idField)) continue;
            
            Set<String> ids = new HashSet<>();
            int duplicates = 0;
            
            for (Map<String, String> row : file.getRows()) {
                String id = row.get(idField);
                if (id != null && !id.isEmpty()) {
                    if (!ids.add(id)) {
                        duplicates++;
                    }
                }
            }
            
            if (duplicates > 0) {
                report.addError(fileName + " has " + duplicates + " duplicate " + idField + " values");
            }
        }
    }
    
    private void validateRequiredValues(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        // Check for required non-empty values in key fields
        Map<String, Set<String>> requiredNonEmpty = Map.of(
            "agency.txt", Set.of("agency_name", "agency_url", "agency_timezone"),
            "stops.txt", Set.of("stop_name"),
            "routes.txt", Set.of("route_type"),
            "trips.txt", Set.of("route_id", "service_id", "trip_id")
        );
        
        for (Map.Entry<String, Set<String>> entry : requiredNonEmpty.entrySet()) {
            String fileName = entry.getKey();
            Set<String> fields = entry.getValue();
            
            GTFSFile file = gtfsFiles.get(fileName);
            if (file == null) continue;
            
            for (String field : fields) {
                if (!file.hasField(field)) continue;
                
                int emptyCount = 0;
                for (Map<String, String> row : file.getRows()) {
                    String value = row.get(field);
                    if (value == null || value.trim().isEmpty()) {
                        emptyCount++;
                    }
                }
                
                if (emptyCount > 0) {
                    report.addError(fileName + " has " + emptyCount + " empty " + field + " values");
                }
            }
        }
    }
    
    private void validateCrossReferences(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating cross-references");
        
        // Collect all IDs
        Set<String> agencyIds = collectIds(gtfsFiles.get("agency.txt"), "agency_id");
        Set<String> routeIds = collectIds(gtfsFiles.get("routes.txt"), "route_id");
        Set<String> stopIds = collectIds(gtfsFiles.get("stops.txt"), "stop_id");
        Set<String> tripIds = collectIds(gtfsFiles.get("trips.txt"), "trip_id");
        Set<String> serviceIds = collectServiceIds(gtfsFiles);
        Set<String> shapeIds = collectIds(gtfsFiles.get("shapes.txt"), "shape_id");
        
        // Validate references
        validateReferences(gtfsFiles.get("routes.txt"), "agency_id", agencyIds, report);
        validateReferences(gtfsFiles.get("trips.txt"), "route_id", routeIds, report);
        validateReferences(gtfsFiles.get("trips.txt"), "service_id", serviceIds, report);
        validateReferences(gtfsFiles.get("trips.txt"), "shape_id", shapeIds, report);
        validateReferences(gtfsFiles.get("stop_times.txt"), "trip_id", tripIds, report);
        validateReferences(gtfsFiles.get("stop_times.txt"), "stop_id", stopIds, report);
    }
    
    private Set<String> collectIds(GTFSFile file, String idField) {
        Set<String> ids = new HashSet<>();
        if (file == null || !file.hasField(idField)) return ids;
        
        for (Map<String, String> row : file.getRows()) {
            String id = row.get(idField);
            if (id != null && !id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }
    
    private Set<String> collectServiceIds(Map<String, GTFSFile> gtfsFiles) {
        Set<String> serviceIds = new HashSet<>();
        
        // From calendar.txt
        GTFSFile calendar = gtfsFiles.get("calendar.txt");
        if (calendar != null && calendar.hasField("service_id")) {
            serviceIds.addAll(collectIds(calendar, "service_id"));
        }
        
        // From calendar_dates.txt
        GTFSFile calendarDates = gtfsFiles.get("calendar_dates.txt");
        if (calendarDates != null && calendarDates.hasField("service_id")) {
            serviceIds.addAll(collectIds(calendarDates, "service_id"));
        }
        
        return serviceIds;
    }
    
    private void validateReferences(GTFSFile file, String referenceField, Set<String> validIds, ValidationReport report) {
        if (file == null || !file.hasField(referenceField) || validIds.isEmpty()) return;
        
        int invalidReferences = 0;
        for (Map<String, String> row : file.getRows()) {
            String id = row.get(referenceField);
            if (id != null && !id.isEmpty() && !validIds.contains(id)) {
                invalidReferences++;
            }
        }
        
        if (invalidReferences > 0) {
            report.addError(file.getFileName() + " has " + invalidReferences + " invalid " + referenceField + " references");
        }
    }
    
    private void validateGeographic(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating geographic data (RTD-specific)");
        
        // Additional geographic validation for RTD service area
        GTFSFile stops = gtfsFiles.get("stops.txt");
        if (stops != null) {
            validateRTDServiceArea(stops, report);
        }
    }
    
    private void validateRTDServiceArea(GTFSFile stops, ValidationReport report) {
        if (!stops.hasField("stop_lat") || !stops.hasField("stop_lon")) return;
        
        int totalStops = 0;
        int validCoordinates = 0;
        int inServiceArea = 0;
        
        for (Map<String, String> row : stops.getRows()) {
            totalStops++;
            String latStr = row.get("stop_lat");
            String lonStr = row.get("stop_lon");
            
            if (latStr != null && lonStr != null && !latStr.isEmpty() && !lonStr.isEmpty()) {
                try {
                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);
                    
                    if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                        validCoordinates++;
                        
                        if (lat >= RTD_MIN_LATITUDE && lat <= RTD_MAX_LATITUDE &&
                            lon >= RTD_MIN_LONGITUDE && lon <= RTD_MAX_LONGITUDE) {
                            inServiceArea++;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Invalid coordinate already counted in structure validation
                }
            }
        }
        
        double serviceAreaPercent = totalStops > 0 ? (double) inServiceArea / totalStops * 100 : 0;
        
        report.addInfo(String.format("Geographic validation: %d/%d stops in RTD service area (%.1f%%)", 
                                   inServiceArea, totalStops, serviceAreaPercent));
        
        if (serviceAreaPercent < 80) {
            report.addWarning("Less than 80% of stops are in RTD service area - may indicate data issues");
        }
    }
    
    private void validateService(Map<String, GTFSFile> gtfsFiles, ValidationReport report) {
        LOG.debug("Validating service definitions");
        
        // Check that either calendar.txt or calendar_dates.txt exists
        boolean hasCalendar = gtfsFiles.containsKey("calendar.txt") && gtfsFiles.get("calendar.txt").getRowCount() > 0;
        boolean hasCalendarDates = gtfsFiles.containsKey("calendar_dates.txt") && gtfsFiles.get("calendar_dates.txt").getRowCount() > 0;
        
        if (!hasCalendar && !hasCalendarDates) {
            report.addError("At least one of calendar.txt or calendar_dates.txt must be present and non-empty");
            return;
        }
        
        if (!hasCalendar) {
            report.addWarning("calendar.txt is missing - using calendar_dates.txt only");
        }
        
        // Validate service date ranges
        if (hasCalendar) {
            validateServiceDateRanges(gtfsFiles.get("calendar.txt"), report);
        }
        
        if (hasCalendarDates) {
            validateServiceExceptions(gtfsFiles.get("calendar_dates.txt"), report);
        }
    }
    
    private void validateServiceDateRanges(GTFSFile calendar, ValidationReport report) {
        if (!calendar.hasField("start_date") || !calendar.hasField("end_date")) return;
        
        LocalDate currentDate = LocalDate.now();
        int activeFutureServices = 0;
        int expiredServices = 0;
        
        for (Map<String, String> row : calendar.getRows()) {
            String startDateStr = row.get("start_date");
            String endDateStr = row.get("end_date");
            
            if (startDateStr != null && endDateStr != null) {
                try {
                    LocalDate startDate = parseGTFSDate(startDateStr);
                    LocalDate endDate = parseGTFSDate(endDateStr);
                    
                    if (endDate.isBefore(currentDate)) {
                        expiredServices++;
                    } else if (startDate.isAfter(currentDate) || endDate.isAfter(currentDate)) {
                        activeFutureServices++;
                    }
                    
                    if (startDate.isAfter(endDate)) {
                        report.addError("Service has start_date after end_date: " + row.get("service_id"));
                    }
                    
                } catch (Exception e) {
                    // Date parsing errors already caught in structure validation
                }
            }
        }
        
        report.addInfo("Service analysis: " + activeFutureServices + " active/future services, " + 
                      expiredServices + " expired services");
        
        if (activeFutureServices == 0) {
            report.addWarning("No active or future services found in calendar.txt");
        }
    }
    
    private void validateServiceExceptions(GTFSFile calendarDates, ValidationReport report) {
        if (!calendarDates.hasField("date") || !calendarDates.hasField("exception_type")) return;
        
        int addedServices = 0;
        int removedServices = 0;
        LocalDate currentDate = LocalDate.now();
        int futureExceptions = 0;
        
        for (Map<String, String> row : calendarDates.getRows()) {
            String exceptionType = row.get("exception_type");
            String dateStr = row.get("date");
            
            if ("1".equals(exceptionType)) {
                addedServices++;
            } else if ("2".equals(exceptionType)) {
                removedServices++;
            }
            
            if (dateStr != null) {
                try {
                    LocalDate exceptionDate = parseGTFSDate(dateStr);
                    if (exceptionDate.isAfter(currentDate)) {
                        futureExceptions++;
                    }
                } catch (Exception e) {
                    // Date parsing errors already caught in structure validation
                }
            }
        }
        
        report.addInfo("Service exceptions: " + addedServices + " added, " + removedServices + " removed, " +
                      futureExceptions + " future exceptions");
    }
    
    // Utility methods
    
    private boolean isValidGTFSTime(String time) {
        if (!TIME_PATTERN.matcher(time).matches()) {
            return false;
        }
        
        String[] parts = time.split(":");
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            
            return hours >= 0 && hours < 48 && // GTFS allows up to 47:59:59
                   minutes >= 0 && minutes < 60 &&
                   seconds >= 0 && seconds < 60;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isValidGTFSDate(String date) {
        if (!DATE_PATTERN.matcher(date).matches()) {
            return false;
        }
        
        try {
            parseGTFSDate(date);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private LocalDate parseGTFSDate(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
        return LocalDate.parse(date, formatter);
    }
    
    // Data classes
    
    public static class GTFSFile {
        private final String fileName;
        private final String[] headers;
        private final List<Map<String, String>> rows;
        
        public GTFSFile(String fileName, String[] headers, List<Map<String, String>> rows) {
            this.fileName = fileName;
            this.headers = headers;
            this.rows = rows;
        }
        
        public String getFileName() { return fileName; }
        public String[] getHeaders() { return headers; }
        public List<Map<String, String>> getRows() { return rows; }
        public int getRowCount() { return rows.size(); }
        public boolean hasField(String fieldName) { 
            return Arrays.asList(headers).contains(fieldName); 
        }
    }
    
    public static class ValidationReport {
        private final String name;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        private final Map<String, ValidationReport> subReports = new HashMap<>();
        
        public ValidationReport(String name) {
            this.name = name;
        }
        
        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        public void addInfo(String info) { this.info.add(info); }
        
        public void addSubReport(String name, ValidationReport report) {
            subReports.put(name, report);
        }
        
        public String getName() { return name; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getInfo() { return info; }
        
        public int getErrorCount() {
            int count = errors.size();
            for (ValidationReport subReport : subReports.values()) {
                count += subReport.getErrorCount();
            }
            return count;
        }
        
        public int getWarningCount() {
            int count = warnings.size();
            for (ValidationReport subReport : subReports.values()) {
                count += subReport.getWarningCount();
            }
            return count;
        }
        
        public boolean isValid() {
            return getErrorCount() == 0;
        }
        
        public String generateReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(name).append(" ===\n");
            sb.append("Status: ").append(isValid() ? "✅ VALID" : "❌ INVALID").append("\n");
            sb.append("Errors: ").append(getErrorCount()).append("\n");
            sb.append("Warnings: ").append(getWarningCount()).append("\n");
            sb.append("\n");
            
            if (!errors.isEmpty()) {
                sb.append("ERRORS:\n");
                for (String error : errors) {
                    sb.append("  ❌ ").append(error).append("\n");
                }
                sb.append("\n");
            }
            
            if (!warnings.isEmpty()) {
                sb.append("WARNINGS:\n");
                for (String warning : warnings) {
                    sb.append("  ⚠️ ").append(warning).append("\n");
                }
                sb.append("\n");
            }
            
            if (!info.isEmpty()) {
                sb.append("INFO:\n");
                for (String infoItem : info) {
                    sb.append("  ℹ️ ").append(infoItem).append("\n");
                }
                sb.append("\n");
            }
            
            for (ValidationReport subReport : subReports.values()) {
                sb.append(subReport.generateReport()).append("\n");
            }
            
            return sb.toString();
        }
    }
    
    // Main method for standalone usage
    public static void main(String[] args) {
        GTFSZipValidator validator = new GTFSZipValidator();
        
        try {
            ValidationReport report;
            
            if (args.length > 0) {
                // Validate specific feed
                String feedName = args[0];
                String feedUrl = ALL_GTFS_URLS.get(feedName);
                
                if (feedUrl == null) {
                    System.err.println("Unknown feed: " + feedName);
                    System.err.println("Available feeds: " + String.join(", ", ALL_GTFS_URLS.keySet()));
                    System.exit(1);
                }
                
                report = validator.validateSingleFeed(feedName, feedUrl);
            } else {
                // Validate all feeds
                report = validator.validateAllFeeds();
            }
            
            System.out.println(report.generateReport());
            
            if (!report.isValid()) {
                System.exit(1);
            }
            
        } catch (Exception e) {
            LOG.error("Validation failed", e);
            System.err.println("Validation failed: " + e.getMessage());
            System.exit(1);
        }
    }
}