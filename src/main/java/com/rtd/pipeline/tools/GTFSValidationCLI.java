package com.rtd.pipeline.tools;

import com.rtd.pipeline.validation.GTFSZipValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Command-line interface for GTFS validation tool.
 * Provides easy access to validate RTD GTFS feeds from the command line.
 */
public class GTFSValidationCLI {
    
    private static final Logger LOG = LoggerFactory.getLogger(GTFSValidationCLI.class);
    
    private static final Map<String, String> FEED_DESCRIPTIONS = Map.of(
        "google_transit", "RTD Main Transit Feed (Combined)",
        "google_transit_flex", "RTD Flex/On-Demand Transit Feed",
        "bustang-co-us", "Colorado Bustang Intercity Service",
        "commuter_rail", "RTD Direct Operated Commuter Rail",
        "light_rail", "RTD Direct Operated Light Rail",
        "motorbus", "RTD Direct Operated Motor Bus",
        "purchased_motorbus", "RTD Purchased Transportation Motor Bus",
        "purchased_commuter", "RTD Purchased Transportation Commuter Rail"
    );
    
    public static void main(String[] args) {
        if (args.length == 0) {
            showHelp();
            return;
        }
        
        String command = args[0].toLowerCase();
        
        switch (command) {
            case "validate":
                handleValidateCommand(Arrays.copyOfRange(args, 1, args.length));
                break;
            case "list":
                handleListCommand();
                break;
            case "help":
            case "--help":
            case "-h":
                showHelp();
                break;
            default:
                System.err.println("Unknown command: " + command);
                System.err.println("Use 'help' to see available commands.");
                System.exit(1);
        }
    }
    
    private static void handleValidateCommand(String[] args) {
        GTFSZipValidator validator = new GTFSZipValidator();
        
        try {
            GTFSZipValidator.ValidationReport report;
            
            if (args.length == 0) {
                // Validate all feeds
                System.out.println("🔍 Validating all RTD GTFS feeds...");
                System.out.println("This may take several minutes as it downloads and validates all feeds.\n");
                
                report = validator.validateAllFeeds();
                
            } else if (args.length == 1) {
                // Validate specific feed
                String feedName = args[0].toLowerCase();
                
                if (!FEED_DESCRIPTIONS.containsKey(feedName)) {
                    System.err.println("❌ Unknown feed: " + feedName);
                    System.err.println("Available feeds:");
                    listFeeds();
                    System.exit(1);
                    return;
                }
                
                System.out.println("🔍 Validating RTD GTFS feed: " + feedName);
                System.out.println("📋 " + FEED_DESCRIPTIONS.get(feedName));
                System.out.println();
                
                // Get the feed URL for the specific feed
                String feedUrl = getFeedUrl(feedName);
                report = validator.validateSingleFeed(feedName, feedUrl);
                
            } else {
                System.err.println("❌ Too many arguments for validate command");
                System.err.println("Usage: validate [feed_name]");
                System.exit(1);
                return;
            }
            
            // Output results
            System.out.println(report.generateReport());
            
            // Summary
            System.out.println("=== VALIDATION SUMMARY ===");
            if (report.isValid()) {
                System.out.println("✅ VALIDATION PASSED");
                System.out.println("All GTFS feeds are valid and comply with GTFS specification.");
            } else {
                System.out.println("❌ VALIDATION FAILED");
                System.out.println("Found " + report.getErrorCount() + " error(s) and " + 
                                 report.getWarningCount() + " warning(s).");
                System.exit(1);
            }
            
        } catch (Exception e) {
            LOG.error("Validation failed", e);
            System.err.println("❌ Validation failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private static void handleListCommand() {
        System.out.println("📋 Available RTD GTFS Feeds:");
        System.out.println();
        
        listFeeds();
        
        System.out.println();
        System.out.println("💡 Usage:");
        System.out.println("  gtfs-validator validate                 # Validate all feeds");
        System.out.println("  gtfs-validator validate <feed_name>     # Validate specific feed");
    }
    
    private static void listFeeds() {
        for (Map.Entry<String, String> entry : FEED_DESCRIPTIONS.entrySet()) {
            System.out.printf("  %-20s - %s%n", entry.getKey(), entry.getValue());
        }
    }
    
    private static void showHelp() {
        System.out.println("RTD GTFS Validation Tool");
        System.out.println("========================");
        System.out.println();
        System.out.println("A comprehensive validation tool for all RTD GTFS feeds available at:");
        System.out.println("https://www.rtd-denver.com/open-records/open-spatial-information/gtfs");
        System.out.println();
        System.out.println("USAGE:");
        System.out.println("  gtfs-validator <command> [options]");
        System.out.println();
        System.out.println("COMMANDS:");
        System.out.println("  validate [feed_name]    Validate GTFS feed(s)");
        System.out.println("                          Without feed_name, validates all feeds");
        System.out.println("                          With feed_name, validates specific feed");
        System.out.println();
        System.out.println("  list                    List all available RTD GTFS feeds");
        System.out.println();
        System.out.println("  help                    Show this help message");
        System.out.println();
        System.out.println("EXAMPLES:");
        System.out.println("  gtfs-validator validate");
        System.out.println("  gtfs-validator validate google_transit");
        System.out.println("  gtfs-validator validate light_rail");
        System.out.println("  gtfs-validator list");
        System.out.println();
        System.out.println("VALIDATION CHECKS:");
        System.out.println("  ✓ File presence (required and optional GTFS files)");
        System.out.println("  ✓ File structure (headers, required fields)");
        System.out.println("  ✓ Data format validation (dates, times, coordinates, URLs, etc.)");
        System.out.println("  ✓ Data content validation (unique IDs, non-empty required values)");
        System.out.println("  ✓ Cross-reference validation (foreign keys between files)");
        System.out.println("  ✓ Geographic validation (RTD service area bounds checking)");
        System.out.println("  ✓ Service definition validation (calendar and exceptions)");
        System.out.println();
        System.out.println("OUTPUT:");
        System.out.println("  The tool generates a comprehensive validation report showing:");
        System.out.println("  • ❌ Errors: GTFS specification violations that must be fixed");
        System.out.println("  • ⚠️  Warnings: Potential issues or RTD-specific concerns");
        System.out.println("  • ℹ️  Info: Statistics and informational messages");
        System.out.println();
        System.out.println("EXIT CODES:");
        System.out.println("  0  - Validation passed (all feeds valid)");
        System.out.println("  1  - Validation failed (errors found) or tool error");
    }
    
    // Helper method to get feed URL (would need to access from GTFSZipValidator)
    private static String getFeedUrl(String feedName) {
        // This is a simplified version - in practice, we'd make the URLs accessible
        // from GTFSZipValidator or create a shared constants class
        Map<String, String> urls = Map.of(
            "google_transit", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip",
            "google_transit_flex", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit_flex.zip",
            "bustang-co-us", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=bustang-co-us.zip",
            "commuter_rail", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Commuter_Rail_GTFS.zip",
            "light_rail", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Light_Rail_GTFS.zip",
            "motorbus", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Operated_Motorbus_GTFS.zip",
            "purchased_motorbus", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Direct_Purchased_Transportation_Motorbus_GTFS.zip",
            "purchased_commuter", "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=RTD_Denver_Purchased_Transportation_Commuter_Rail_GTFS.zip"
        );
        
        return urls.get(feedName);
    }
}