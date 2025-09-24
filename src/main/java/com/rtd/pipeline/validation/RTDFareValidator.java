package com.rtd.pipeline.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * RTD Fare Validation Tool
 * Validates fare structures to ensure compliance with RTD fare policies
 */
public class RTDFareValidator {
    
    private static final Logger LOG = LoggerFactory.getLogger(RTDFareValidator.class);
    
    // RTD Google feeds to test
    private static final Map<String, String> RTD_GOOGLE_FEEDS = Map.of(
        "google_transit", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip",
        "google_transit_flex", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit_flex.zip"
    );
    
    // Fare validation rules
    private static final double MAX_FARE_AMOUNT = 10.00;
    private static final double AIRPORT_FARE_AMOUNT = 10.00;
    
    // Exact DIA airport stop IDs that should allow $10 fare (provided by user)
    private static final Set<String> AUTHORIZED_DIA_STOP_IDS = Set.of(
        "34476", "34651", "34652", "34650", "34654", "34475", "34653"
    );
    
    // Free service stop IDs that should have $0.00 fare (comprehensive list from user)
    private static final Set<String> FREE_SERVICE_STOP_IDS = Set.of(
        "35039", "34303", "34304", "34305", "34306", "34307", "34308", "34321", "34330", "34310", 
        "34319", "34296", "34297", "34298", "34299", "34300", "33646", "35194", "25423", "25422", 
        "33570", "34380", "22351", "22352", "22353", "22354", "22355", "22356", "22357", "22358", 
        "22359", "22360", "22361", "22362", "26118", "23485", "22363", "22364", "22365", "22366", 
        "22367", "22368", "22369", "22370", "22371", "22372", "22373", "34381", "34379", "25424", 
        "25425", "35193", "33647", "10101"
    );
    
    // Actual DIA airport stop identifiers (only these should allow $10 fare)
    private static final Set<String> DIA_AIRPORT_KEYWORDS = Set.of(
        "DENVER AIRPORT STATION", "DEN AIRPORT", "DENVER INTERNATIONAL AIRPORT",
        "61ST & PENA STATION", "61ST / PENA STATION", "PENA STATION"
    );
    
    // General airport-related keywords (for reporting, not for $10 fare validation)
    private static final Set<String> AIRPORT_KEYWORDS = Set.of(
        "AIRPORT", "DIA", "DEN", "DENVER INTERNATIONAL", "PENA", "A-TRAIN", "A TRAIN"
    );
    
    // Subscription fare keywords (exempt from single-trip limits)
    private static final Set<String> SUBSCRIPTION_KEYWORDS = Set.of(
        "MONTHLY", "WEEKLY", "ANNUAL", "SUBSCRIPTION", "PASS", "UNLIMITED"
    );
    
    public static class FareValidationReport {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> info = new ArrayList<>();
        private final Map<String, FareProduct> fareProducts = new HashMap<>();
        private final Map<String, FareLegRule> fareLegRules = new HashMap<>();
        private final Map<String, Route> routes = new HashMap<>();
        private final Map<String, Stop> stops = new HashMap<>();
        
        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        public void addInfo(String info) { this.info.add(info); }
        
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public List<String> getInfo() { return info; }
        
        public void addFareProduct(String id, FareProduct product) { fareProducts.put(id, product); }
        public void addFareLegRule(String id, FareLegRule rule) { fareLegRules.put(id, rule); }
        public void addRoute(String id, Route route) { routes.put(id, route); }
        public void addStop(String id, Stop stop) { stops.put(id, stop); }
        
        public Map<String, FareProduct> getFareProducts() { return fareProducts; }
        public Map<String, FareLegRule> getFareLegRules() { return fareLegRules; }
        public Map<String, Route> getRoutes() { return routes; }
        public Map<String, Stop> getStops() { return stops; }
    }
    
    public static class FareProduct {
        public String fareProductId;
        public String fareProductName;
        public double amount;
        public String currency;
        
        public FareProduct(String id, String name, double amount, String currency) {
            this.fareProductId = id;
            this.fareProductName = name;
            this.amount = amount;
            this.currency = currency;
        }
    }
    
    public static class FareLegRule {
        public String legGroupId;
        public String networkId;
        public String fromAreaId;
        public String toAreaId;
        public String fareProductId;
        
        public FareLegRule(String legGroupId, String networkId, String fromAreaId, String toAreaId, String fareProductId) {
            this.legGroupId = legGroupId;
            this.networkId = networkId;
            this.fromAreaId = fromAreaId;
            this.toAreaId = toAreaId;
            this.fareProductId = fareProductId;
        }
    }
    
    public static class Route {
        public String routeId;
        public String routeShortName;
        public String routeLongName;
        public int routeType;
        
        public Route(String id, String shortName, String longName, int type) {
            this.routeId = id;
            this.routeShortName = shortName;
            this.routeLongName = longName;
            this.routeType = type;
        }
        
        public boolean isAirportRoute() {
            String combinedName = (routeShortName + " " + routeLongName).toUpperCase();
            return AIRPORT_KEYWORDS.stream().anyMatch(combinedName::contains);
        }
    }
    
    public static class Stop {
        public String stopId;
        public String stopName;
        public double stopLat;
        public double stopLon;
        
        public Stop(String id, String name, double lat, double lon) {
            this.stopId = id;
            this.stopName = name;
            this.stopLat = lat;
            this.stopLon = lon;
        }
        
        public boolean isAirportStop() {
            return AIRPORT_KEYWORDS.stream().anyMatch(stopName.toUpperCase()::contains);
        }
        
        public boolean isDIAStop() {
            String upperName = stopName.toUpperCase();
            return DIA_AIRPORT_KEYWORDS.stream().anyMatch(upperName::contains);
        }
        
        public boolean isAuthorizedDIAStop() {
            return AUTHORIZED_DIA_STOP_IDS.contains(stopId);
        }
        
        public boolean isFreeServiceStop() {
            return FREE_SERVICE_STOP_IDS.contains(stopId);
        }
    }
    
    /**
     * Validates RTD fare data across multiple Google feeds
     */
    public FareValidationReport validateFares() {
        LOG.info("=== RTD Multi-Feed Fare Validation Report ===");
        
        FareValidationReport report = new FareValidationReport();
        Map<String, Map<String, GTFSFile>> allFeedData = new HashMap<>();
        
        try {
            // Download and parse all Google GTFS feeds
            for (Map.Entry<String, String> feed : RTD_GOOGLE_FEEDS.entrySet()) {
                String feedName = feed.getKey();
                String feedUrl = feed.getValue();
                
                LOG.info("Downloading {} feed from: {}", feedName, feedUrl);
                Map<String, GTFSFile> gtfsFiles = downloadAndParseGTFS(feedUrl);
                allFeedData.put(feedName, gtfsFiles);
                
                // Parse stops from each feed
                parseStops(gtfsFiles.get("stops.txt"), report);
            }
            
            // Use primary feed (google_transit) for fare analysis
            Map<String, GTFSFile> primaryFeed = allFeedData.get("google_transit");
            if (primaryFeed != null) {
                // Parse fare-related files from primary feed
                parseFareProducts(primaryFeed.get("fare_products.txt"), report);
                parseFareLegRules(primaryFeed.get("fare_leg_rules.txt"), report);
                parseRoutes(primaryFeed.get("routes.txt"), report);
            }
            
            // Validate fare rules and free stops across all feeds
            validateFareAmounts(report);
            validateAirportFares(report);
            validateFreeServiceStops(report, allFeedData);
            
            LOG.info("Multi-feed fare validation completed: {} errors, {} warnings", 
                    report.getErrors().size(), report.getWarnings().size());
            
        } catch (Exception e) {
            LOG.error("Failed to validate fares: {}", e.getMessage(), e);
            report.addError("Failed to validate fares: " + e.getMessage());
        }
        
        return report;
    }
    
    private Map<String, GTFSFile> downloadAndParseGTFS(String url) throws IOException {
        Map<String, GTFSFile> files = new HashMap<>();
        
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        
        try (ZipInputStream zipStream = new ZipInputStream(connection.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    String fileName = entry.getName();
                    if (fileName.endsWith(".txt")) {
                        GTFSFile gtfsFile = parseCSV(zipStream, fileName);
                        files.put(fileName, gtfsFile);
                    }
                }
            }
        }
        
        return files;
    }
    
    private GTFSFile parseCSV(InputStream inputStream, String fileName) throws IOException {
        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        
        // Read the entry content into a byte array to avoid stream issues
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(buffer.toByteArray())))) {
            String line = reader.readLine();
            if (line != null) {
                headers.addAll(Arrays.asList(line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)")));
                
                while ((line = reader.readLine()) != null) {
                    String[] values = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
                    Map<String, String> row = new HashMap<>();
                    
                    for (int i = 0; i < headers.size() && i < values.length; i++) {
                        String value = values[i].trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        row.put(headers.get(i), value);
                    }
                    rows.add(row);
                }
            }
        }
        
        return new GTFSFile(fileName, headers, rows);
    }
    
    private void parseFareProducts(GTFSFile file, FareValidationReport report) {
        if (file == null) {
            report.addWarning("fare_products.txt not found");
            return;
        }
        
        for (Map<String, String> row : file.getRows()) {
            String id = row.get("fare_product_id");
            String name = row.get("fare_product_name");
            String amountStr = row.get("amount");
            String currency = row.get("currency");
            
            if (id != null && amountStr != null) {
                try {
                    double amount = Double.parseDouble(amountStr);
                    FareProduct product = new FareProduct(id, name, amount, currency);
                    report.addFareProduct(id, product);
                } catch (NumberFormatException e) {
                    report.addError("Invalid amount in fare_products.txt: " + amountStr);
                }
            }
        }
        
        report.addInfo("Parsed " + report.getFareProducts().size() + " fare products");
    }
    
    private void parseFareLegRules(GTFSFile file, FareValidationReport report) {
        if (file == null) {
            report.addWarning("fare_leg_rules.txt not found");
            return;
        }
        
        for (Map<String, String> row : file.getRows()) {
            String legGroupId = row.get("leg_group_id");
            String networkId = row.get("network_id");
            String fromAreaId = row.get("from_area_id");
            String toAreaId = row.get("to_area_id");
            String fareProductId = row.get("fare_product_id");
            
            if (legGroupId != null && fareProductId != null) {
                FareLegRule rule = new FareLegRule(legGroupId, networkId, fromAreaId, toAreaId, fareProductId);
                report.addFareLegRule(legGroupId + "_" + fareProductId, rule);
            }
        }
        
        report.addInfo("Parsed " + report.getFareLegRules().size() + " fare leg rules");
    }
    
    private void parseRoutes(GTFSFile file, FareValidationReport report) {
        if (file == null) {
            report.addError("routes.txt not found");
            return;
        }
        
        for (Map<String, String> row : file.getRows()) {
            String id = row.get("route_id");
            String shortName = row.getOrDefault("route_short_name", "");
            String longName = row.getOrDefault("route_long_name", "");
            String typeStr = row.get("route_type");
            
            if (id != null && typeStr != null) {
                try {
                    int type = Integer.parseInt(typeStr);
                    Route route = new Route(id, shortName, longName, type);
                    report.addRoute(id, route);
                } catch (NumberFormatException e) {
                    report.addError("Invalid route_type: " + typeStr);
                }
            }
        }
        
        report.addInfo("Parsed " + report.getRoutes().size() + " routes");
    }
    
    private void parseStops(GTFSFile file, FareValidationReport report) {
        if (file == null) {
            report.addError("stops.txt not found");
            return;
        }
        
        for (Map<String, String> row : file.getRows()) {
            String id = row.get("stop_id");
            String name = row.getOrDefault("stop_name", "");
            String latStr = row.get("stop_lat");
            String lonStr = row.get("stop_lon");
            
            if (id != null && latStr != null && lonStr != null) {
                try {
                    double lat = Double.parseDouble(latStr);
                    double lon = Double.parseDouble(lonStr);
                    Stop stop = new Stop(id, name, lat, lon);
                    report.addStop(id, stop);
                } catch (NumberFormatException e) {
                    report.addError("Invalid coordinates for stop " + id);
                }
            }
        }
        
        report.addInfo("Parsed " + report.getStops().size() + " stops");
    }
    
    private void validateFareAmounts(FareValidationReport report) {
        int overLimitCount = 0;
        int maxFareCount = 0;
        int subscriptionFareCount = 0;
        
        for (FareProduct product : report.getFareProducts().values()) {
            String productName = product.fareProductName != null ? product.fareProductName.toUpperCase() : "";
            boolean isSubscriptionFare = SUBSCRIPTION_KEYWORDS.stream().anyMatch(productName::contains);
            
            if (isSubscriptionFare) {
                subscriptionFareCount++;
                report.addInfo("Subscription fare (exempt from $" + MAX_FARE_AMOUNT + " limit): '" + 
                             product.fareProductName + "' ($" + product.amount + ")");
            } else if (product.amount > MAX_FARE_AMOUNT) {
                report.addError("Fare product '" + product.fareProductName + 
                              "' has amount $" + product.amount + " exceeding maximum of $" + MAX_FARE_AMOUNT);
                overLimitCount++;
            } else if (product.amount == AIRPORT_FARE_AMOUNT) {
                maxFareCount++;
                report.addInfo("Found $" + AIRPORT_FARE_AMOUNT + " fare: '" + product.fareProductName + "'");
            }
        }
        
        if (overLimitCount == 0) {
            report.addInfo("✅ All single-trip fare amounts are within $" + MAX_FARE_AMOUNT + " limit");
        }
        
        report.addInfo("Found " + maxFareCount + " fare products at maximum $" + AIRPORT_FARE_AMOUNT + " amount");
        report.addInfo("Found " + subscriptionFareCount + " subscription fares (exempt from single-trip limits)");
    }
    
    private void validateAirportFares(FareValidationReport report) {
        // Find airport routes
        List<Route> airportRoutes = report.getRoutes().values().stream()
            .filter(Route::isAirportRoute)
            .toList();
        
        // Find all airport-related stops 
        List<Stop> airportStops = report.getStops().values().stream()
            .filter(Stop::isAirportStop)
            .toList();
        
        // Find actual DIA stops (only these should allow $10 fare)
        List<Stop> diaStops = report.getStops().values().stream()
            .filter(Stop::isDIAStop)
            .toList();
        
        // Find authorized DIA stops by exact stop ID
        List<Stop> authorizedDiaStops = report.getStops().values().stream()
            .filter(Stop::isAuthorizedDIAStop)
            .toList();
        
        // Find free service stops by exact stop ID
        List<Stop> freeServiceStops = report.getStops().values().stream()
            .filter(Stop::isFreeServiceStop)
            .toList();
        
        report.addInfo("Found " + airportRoutes.size() + " airport-related routes");
        report.addInfo("Found " + airportStops.size() + " airport-related stops");
        report.addInfo("Found " + diaStops.size() + " DIA stops by name matching");
        report.addInfo("Found " + authorizedDiaStops.size() + " AUTHORIZED DIA stops by stop ID");
        report.addInfo("Found " + freeServiceStops.size() + " FREE SERVICE stops by stop ID");
        
        // List authorized DIA stops (these can use $10 fare)
        report.addInfo("=== AUTHORIZED DIA STOPS (eligible for $10 fare) ===");
        for (Stop stop : authorizedDiaStops) {
            report.addInfo("✅ AUTHORIZED DIA Stop ID " + stop.stopId + ": " + stop.stopName);
        }
        
        // Check if all authorized stop IDs were found
        Set<String> foundStopIds = authorizedDiaStops.stream().map(s -> s.stopId).collect(java.util.stream.Collectors.toSet());
        Set<String> missingStopIds = new java.util.HashSet<>(AUTHORIZED_DIA_STOP_IDS);
        missingStopIds.removeAll(foundStopIds);
        
        if (!missingStopIds.isEmpty()) {
            report.addError("Missing authorized DIA stop IDs in GTFS data: " + missingStopIds);
        } else {
            report.addInfo("✅ All 7 authorized DIA stop IDs found in GTFS data");
        }
        
        // List free service stops (these should have $0.00 fare)
        report.addInfo("=== FREE SERVICE STOPS (should have $0.00 fare) ===");
        for (Stop stop : freeServiceStops) {
            report.addInfo("🆓 FREE SERVICE Stop ID " + stop.stopId + ": " + stop.stopName);
        }
        
        // Check if all free service stop IDs were found
        Set<String> foundFreeStopIds = freeServiceStops.stream().map(s -> s.stopId).collect(java.util.stream.Collectors.toSet());
        Set<String> missingFreeStopIds = new java.util.HashSet<>(FREE_SERVICE_STOP_IDS);
        missingFreeStopIds.removeAll(foundFreeStopIds);
        
        if (!missingFreeStopIds.isEmpty()) {
            report.addError("Missing free service stop IDs in GTFS data: " + missingFreeStopIds);
        } else {
            report.addInfo("✅ All " + FREE_SERVICE_STOP_IDS.size() + " free service stop IDs found in GTFS data");
        }
        
        // List other airport-related stops (these should use local fares)
        List<Stop> nonDiaAirportStops = airportStops.stream()
            .filter(stop -> !stop.isDIAStop())
            .toList();
        
        if (!nonDiaAirportStops.isEmpty()) {
            report.addInfo("=== NON-DIA AIRPORT STOPS (should use local fares) ===");
            for (Stop stop : nonDiaAirportStops) {
                report.addInfo("⚠️ Local fare stop: " + stop.stopName);
            }
        }
        
        // Validate $10 fare restriction
        for (FareProduct product : report.getFareProducts().values()) {
            if (product.amount == AIRPORT_FARE_AMOUNT) {
                String productName = product.fareProductName != null ? product.fareProductName.toUpperCase() : "";
                boolean isAirportFare = AIRPORT_KEYWORDS.stream().anyMatch(productName::contains);
                
                if (!isAirportFare) {
                    report.addError("$" + AIRPORT_FARE_AMOUNT + " fare '" + product.fareProductName + 
                                  "' is not airport-related but costs $10 (should be local fare)");
                } else {
                    // Check if this fare should only apply to actual DIA stops
                    if (diaStops.isEmpty()) {
                        report.addError("$" + AIRPORT_FARE_AMOUNT + " airport fare '" + product.fareProductName + 
                                      "' exists but no actual DIA stops found");
                    } else {
                        report.addInfo("✅ Airport fare validation: '" + product.fareProductName + "' ($" + product.amount + 
                                     ") - should only apply to " + diaStops.size() + " DIA stops");
                    }
                }
            }
        }
        
        // Critical validation: Check for fare policy violations based on authorized stop IDs
        List<Stop> unauthorizedAirportStops = airportStops.stream()
            .filter(stop -> !stop.isAuthorizedDIAStop())
            .toList();
        
        if (unauthorizedAirportStops.size() > 0) {
            report.addError("CRITICAL POLICY VIOLATION: " + unauthorizedAirportStops.size() + 
                          " unauthorized stops could potentially use $10 airport fare");
            report.addError("Only stops with IDs " + AUTHORIZED_DIA_STOP_IDS + " should allow $10 airport fare");
            
            // List some examples of unauthorized stops
            int maxExamples = Math.min(10, unauthorizedAirportStops.size());
            report.addInfo("=== EXAMPLES OF UNAUTHORIZED STOPS (should use local fares) ===");
            for (int i = 0; i < maxExamples; i++) {
                Stop stop = unauthorizedAirportStops.get(i);
                report.addInfo("❌ UNAUTHORIZED Stop ID " + stop.stopId + ": " + stop.stopName);
            }
            
            if (unauthorizedAirportStops.size() > maxExamples) {
                report.addInfo("... and " + (unauthorizedAirportStops.size() - maxExamples) + " more unauthorized stops");
            }
        } else {
            report.addInfo("✅ No unauthorized airport-related stops found");
        }
        
        // Validate free service stops for incorrect charging
        List<Stop> freeStopsWithPotentialCharges = freeServiceStops.stream()
            .filter(stop -> !stop.stopName.toUpperCase().contains("FREE"))
            .toList();
        
        if (!freeStopsWithPotentialCharges.isEmpty()) {
            report.addWarning("Free service stops may need fare rule verification: " + freeStopsWithPotentialCharges.size() + " stops");
        }
        
        // Final validation summary
        report.addInfo("=== FARE POLICY COMPLIANCE SUMMARY ===");
        report.addInfo("✅ Authorized for $10 fare: " + authorizedDiaStops.size() + " stops (IDs: " + 
                      String.join(", ", AUTHORIZED_DIA_STOP_IDS) + ")");
        report.addInfo("🆓 Should have $0.00 fare: " + freeServiceStops.size() + " free service stops (IDs: " + 
                      String.join(", ", FREE_SERVICE_STOP_IDS.stream().limit(5).toArray(String[]::new)) + 
                      (FREE_SERVICE_STOP_IDS.size() > 5 ? "..." : "") + ")");
        report.addInfo("❌ Should use local fares: " + unauthorizedAirportStops.size() + " airport-related stops");
        report.addInfo("📊 Total stops analyzed: " + report.getStops().size() + " stops");
    }
    
    /**
     * Validates free service stops across multiple feeds
     */
    private void validateFreeServiceStops(FareValidationReport report, Map<String, Map<String, GTFSFile>> allFeedData) {
        report.addInfo("=== COMPREHENSIVE FREE SERVICE STOP VALIDATION ===");
        
        // Count free service stops found across all feeds
        Set<String> foundFreeServiceStops = new HashSet<>();
        Map<String, String> feedsWithStops = new HashMap<>();
        
        for (Map.Entry<String, Map<String, GTFSFile>> feedEntry : allFeedData.entrySet()) {
            String feedName = feedEntry.getKey();
            Map<String, GTFSFile> feedFiles = feedEntry.getValue();
            
            GTFSFile stopsFile = feedFiles.get("stops.txt");
            if (stopsFile != null) {
                int feedFreeStops = 0;
                for (Map<String, String> row : stopsFile.getRows()) {
                    String stopId = row.get("stop_id");
                    if (stopId != null && FREE_SERVICE_STOP_IDS.contains(stopId)) {
                        foundFreeServiceStops.add(stopId);
                        feedsWithStops.put(stopId, feedName);
                        feedFreeStops++;
                    }
                }
                report.addInfo("Feed '" + feedName + "': Found " + feedFreeStops + " free service stops");
            }
        }
        
        // Calculate missing stops
        Set<String> missingFreeServiceStops = new HashSet<>(FREE_SERVICE_STOP_IDS);
        missingFreeServiceStops.removeAll(foundFreeServiceStops);
        
        // Report results
        report.addInfo("=== FREE SERVICE STOP COMPARISON RESULTS ===");
        report.addInfo("✅ Expected free service stops: " + FREE_SERVICE_STOP_IDS.size());
        report.addInfo("✅ Found across all Google feeds: " + foundFreeServiceStops.size());
        
        if (missingFreeServiceStops.isEmpty()) {
            report.addInfo("🎉 PERFECT MATCH: All " + FREE_SERVICE_STOP_IDS.size() + " free service stops found in Google feeds!");
        } else {
            report.addError("❌ MISSING FREE SERVICE STOPS: " + missingFreeServiceStops.size() + " stops not found in any Google feed");
            report.addError("Missing stop IDs: " + String.join(", ", missingFreeServiceStops));
        }
        
        // List found stops by feed
        if (!foundFreeServiceStops.isEmpty()) {
            report.addInfo("=== FREE SERVICE STOPS FOUND BY FEED ===");
            Map<String, List<String>> stopsByFeed = new HashMap<>();
            
            for (String stopId : foundFreeServiceStops) {
                String feedName = feedsWithStops.get(stopId);
                stopsByFeed.computeIfAbsent(feedName, k -> new ArrayList<>()).add(stopId);
            }
            
            for (Map.Entry<String, List<String>> entry : stopsByFeed.entrySet()) {
                String feedName = entry.getKey();
                List<String> stops = entry.getValue();
                report.addInfo("📂 " + feedName + " feed: " + stops.size() + " stops (" + 
                              String.join(", ", stops.stream().limit(10).toArray(String[]::new)) + 
                              (stops.size() > 10 ? "..." : "") + ")");
            }
        }
        
        // Final summary for free service stops
        report.addInfo("=== FREE SERVICE VALIDATION SUMMARY ===");
        report.addInfo("🆓 Total free service stop IDs to validate: " + FREE_SERVICE_STOP_IDS.size());
        report.addInfo("✅ Found in Google feeds: " + foundFreeServiceStops.size() + " (" + 
                      String.format("%.1f%%", (foundFreeServiceStops.size() * 100.0) / FREE_SERVICE_STOP_IDS.size()) + ")");
        
        if (missingFreeServiceStops.size() > 0) {
            report.addInfo("❌ Missing from all feeds: " + missingFreeServiceStops.size() + " (" + 
                          String.format("%.1f%%", (missingFreeServiceStops.size() * 100.0) / FREE_SERVICE_STOP_IDS.size()) + ")");
        }
    }
    
    public String generateReport(FareValidationReport report) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== RTD Fare Validation Report ===\n");
        sb.append("Generated: ").append(new Date()).append("\n\n");
        
        // Summary
        if (report.getErrors().isEmpty()) {
            sb.append("Status: ✅ FARE VALIDATION PASSED\n");
        } else {
            sb.append("Status: ❌ FARE VALIDATION FAILED\n");
        }
        sb.append("Errors: ").append(report.getErrors().size()).append("\n");
        sb.append("Warnings: ").append(report.getWarnings().size()).append("\n\n");
        
        // Errors
        if (!report.getErrors().isEmpty()) {
            sb.append("ERRORS:\n");
            for (String error : report.getErrors()) {
                sb.append("  ❌ ").append(error).append("\n");
            }
            sb.append("\n");
        }
        
        // Warnings  
        if (!report.getWarnings().isEmpty()) {
            sb.append("WARNINGS:\n");
            for (String warning : report.getWarnings()) {
                sb.append("  ⚠️ ").append(warning).append("\n");
            }
            sb.append("\n");
        }
        
        // Info
        if (!report.getInfo().isEmpty()) {
            sb.append("INFO:\n");
            for (String info : report.getInfo()) {
                sb.append("  ℹ️ ").append(info).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    // GTFSFile helper class
    private static class GTFSFile {
        private final String fileName;
        private final List<String> headers;
        private final List<Map<String, String>> rows;
        
        public GTFSFile(String fileName, List<String> headers, List<Map<String, String>> rows) {
            this.fileName = fileName;
            this.headers = headers;
            this.rows = rows;
        }
        
        public List<Map<String, String>> getRows() { return rows; }
    }
    
    /**
     * Main method for command line usage
     */
    public static void main(String[] args) {
        RTDFareValidator validator = new RTDFareValidator();
        FareValidationReport report = validator.validateFares();
        System.out.println(validator.generateReport(report));
    }
}