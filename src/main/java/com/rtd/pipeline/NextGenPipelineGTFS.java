package com.rtd.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;

/**
 * Extract core GTFS files from TIES Oracle source via PostgreSQL database using views.
 * Generates standard GTFS files from TIES_GOOGLE_*_VW views.
 *
 * Generated files:
 * - agency.txt
 * - feed_info.txt
 * - routes.txt
 * - trips.txt
 * - stops.txt
 * - stop_times.txt
 *
 * Usage:
 *   ./gradlew runNextGenCorePipeline
 */
public class NextGenPipelineGTFS {

    private static final Logger LOG = LoggerFactory.getLogger(NextGenPipelineGTFS.class);

    private static final String POSTGRES_JDBC_URL = System.getenv().getOrDefault(
        "POSTGRES_TIES_URL", "jdbc:postgresql://localhost:5433/ties");
    private static final String POSTGRES_USER = System.getenv().getOrDefault(
        "POSTGRES_TIES_USER", "ties");
    private static final String POSTGRES_PASSWORD = System.getenv().getOrDefault(
        "POSTGRES_TIES_PASSWORD", "TiesPassword123");
    private static final String OUTPUT_DIR = System.getenv().getOrDefault(
        "NEXTGEN_OUTPUT_DIR", "data/gtfs-nextgen/rtd/" + LocalDate.now());

    public static void main(String[] args) throws Exception {
        LOG.info("=== NextGenPipeline GTFS Core Data Extraction Pipeline Starting ===");
        LOG.info("Database: {}", POSTGRES_JDBC_URL);
        LOG.info("Output Directory: {}", OUTPUT_DIR);

        // Create output directory
        File outDir = new File(OUTPUT_DIR);
        if (!outDir.exists()) {
            outDir.mkdirs();
            LOG.info("Created output directory: {}", outDir.getAbsolutePath());
        }

        try (Connection conn = DriverManager.getConnection(POSTGRES_JDBC_URL, POSTGRES_USER, POSTGRES_PASSWORD)) {
            LOG.info("Connected to PostgreSQL TIES database");

            // Extract core GTFS files
            extractAgency(conn);
            extractFeedInfo(conn);
            extractCalendar(conn);
            extractCalendarDates(conn);
            extractRoutes(conn);
            extractTrips(conn);
            extractStops(conn);
            extractStopTimes(conn);

            // Extract fare files
            extractFareMedia(conn);
            extractFareProducts(conn);
            extractFareLegRules(conn);
            extractFareTransferRules(conn);
            extractAreas(conn);
            extractStopAreas(conn);

            // Extract network files
            extractNetworks(conn);
            extractRouteNetworks(conn);

            LOG.info("=== NextGenPipeline GTFS Core Data Extraction Complete ===");
            LOG.info("Files written to: {}", outDir.getAbsolutePath());

            // Create zip file
            createGTFSZip(outDir);

        } catch (SQLException e) {
            LOG.error("Database error: {}", e.getMessage(), e);
            throw e;
        }
    }

    private static void createGTFSZip(File outputDir) throws Exception {
        String zipFileName = "RTD_NextGen_Pipeline.zip";
        File zipFile = new File(outputDir, zipFileName);

        // Delete existing zip file if it exists
        if (zipFile.exists()) {
            LOG.info("Deleting existing zip file: {}", zipFileName);
            zipFile.delete();
        }

        LOG.info("Creating GTFS zip file: {}", zipFileName);

        // Get list of .txt files
        File[] txtFiles = outputDir.listFiles((dir, name) -> name.endsWith(".txt"));
        if (txtFiles == null || txtFiles.length == 0) {
            LOG.warn("No .txt files found in {}", outputDir);
            return;
        }

        // Build zip command with explicit file list
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("zip");
        command.add("-q");
        command.add(zipFileName);
        for (File txtFile : txtFiles) {
            // Only include GTFS files, exclude other files like VALIDATION_SUMMARY.txt
            if (txtFile.getName().matches("(agency|stops|routes|trips|stop_times|calendar|calendar_dates|feed_info|fare_.*|areas|stop_areas|networks|route_networks)\\.txt")) {
                command.add(txtFile.getName());
            }
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(outputDir);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            LOG.info("✅ Successfully created: {}", zipFile.getAbsolutePath());
            LOG.info("   Size: {} MB", String.format("%.2f", zipFile.length() / 1024.0 / 1024.0));
        } else {
            LOG.error("Failed to create zip file (exit code: {})", exitCode);
            throw new RuntimeException("Failed to create GTFS zip file");
        }
    }

    private static void extractAgency(Connection conn) throws Exception {
        LOG.info("Extracting agency.txt...");
        String sql = "SELECT * FROM ties_google_agency_vw";
        String file = OUTPUT_DIR + "/agency.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("agency_id,agency_name,agency_url,agency_timezone,agency_lang,agency_phone");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("agency_id")),
                    csvEscape(rs.getString("agency_name")),
                    csvEscape(rs.getString("agency_url")),
                    csvEscape(rs.getString("agency_timezone")),
                    csvEscape(rs.getString("agency_lang")),
                    csvEscape(rs.getString("agency_phone"))));
                count++;
            }
            LOG.info("Wrote {} agencies to agency.txt", count);
        }
    }

    private static void extractFeedInfo(Connection conn) throws Exception {
        LOG.info("Extracting feed_info.txt...");
        String sql = "SELECT * FROM ties_google_feed_info_vw";
        String file = OUTPUT_DIR + "/feed_info.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("feed_publisher_name")),
                    csvEscape(rs.getString("feed_publisher_url")),
                    csvEscape(rs.getString("feed_lang")),
                    csvEscape(rs.getString("feed_start_date")),
                    csvEscape(rs.getString("feed_end_date")),
                    csvEscape(rs.getString("feed_version"))));
                count++;
            }
            LOG.info("Wrote {} feed info records to feed_info.txt", count);
        }
    }

    private static void extractRoutes(Connection conn) throws Exception {
        LOG.info("Extracting routes.txt...");
        String sql = "SELECT * FROM ties_google_routes_vw ORDER BY route_id";
        String file = OUTPUT_DIR + "/routes.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url,route_color,route_text_color,route_sort_order,network_id");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("route_id")),
                    csvEscape(rs.getString("agency_id")),
                    csvEscape(rs.getString("route_short_name")),
                    csvEscape(rs.getString("route_long_name")),
                    csvEscape(rs.getString("route_desc")),
                    csvEscape(rs.getString("route_type")),
                    csvEscape(rs.getString("route_url")),
                    csvEscape(rs.getString("route_color")),
                    csvEscape(rs.getString("route_text_color")),
                    csvEscape(rs.getString("route_sort_order")),
                    csvEscape(rs.getString("network_id"))));
                count++;
            }
            LOG.info("Wrote {} routes to routes.txt", count);
        }
    }

    private static void extractTrips(Connection conn) throws Exception {
        LOG.info("Extracting trips.txt...");
        String sql = "SELECT * FROM ties_google_trips_vw ORDER BY route_id, trip_id";
        String file = OUTPUT_DIR + "/trips.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("route_id,service_id,trip_id,trip_headsign,direction_id,block_id,shape_id,wheelchair_accessible,bikes_allowed");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("route_id")),
                    csvEscape(rs.getString("service_id")),
                    csvEscape(rs.getString("trip_id")),
                    csvEscape(rs.getString("trip_headsign")),
                    csvEscape(rs.getString("direction_id")),
                    csvEscape(rs.getString("block_id")),
                    csvEscape(rs.getString("shape_id")),
                    csvEscape(rs.getString("wheelchair_accessible")),
                    csvEscape(rs.getString("bikes_allowed"))));
                count++;
            }
            LOG.info("Wrote {} trips to trips.txt", count);
        }
    }

    private static void extractStops(Connection conn) throws Exception {
        LOG.info("Extracting stops.txt...");
        String sql = "SELECT * FROM ties_google_stops_vw ORDER BY stop_id";
        String file = OUTPUT_DIR + "/stops.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,stop_timezone,wheelchair_boarding");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("stop_id")),
                    csvEscape(rs.getString("stop_code")),
                    csvEscape(rs.getString("stop_name")),
                    csvEscape(rs.getString("stop_desc")),
                    csvEscape(rs.getString("stop_lat")),
                    csvEscape(rs.getString("stop_lon")),
                    csvEscape(rs.getString("zone_id")),
                    csvEscape(rs.getString("stop_url")),
                    csvEscape(rs.getString("location_type")),
                    csvEscape(rs.getString("parent_station")),
                    csvEscape(rs.getString("stop_timezone")),
                    csvEscape(rs.getString("wheelchair_boarding"))));
                count++;
            }
            LOG.info("Wrote {} stops to stops.txt", count);
        }
    }

    private static void extractStopTimes(Connection conn) throws Exception {
        LOG.info("Extracting stop_times.txt...");
        String sql = "SELECT * FROM ties_google_stop_times_vw ORDER BY trip_id, stop_sequence";
        String file = OUTPUT_DIR + "/stop_times.txt";

        Statement stmt = conn.createStatement();
        stmt.setFetchSize(10000);  // Fetch in batches for large result set

        try (ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            // Write header
            writer.println("trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled");

            // Write rows
            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("trip_id")),
                    csvEscape(rs.getString("arrival_time")),
                    csvEscape(rs.getString("departure_time")),
                    csvEscape(rs.getString("stop_id")),
                    csvEscape(rs.getString("stop_sequence")),
                    csvEscape(rs.getString("stop_headsign")),
                    csvEscape(rs.getString("pickup_type")),
                    csvEscape(rs.getString("drop_off_type")),
                    csvEscape(rs.getString("shape_dist_traveled"))));
                count++;
                if (count % 100000 == 0) {
                    LOG.info("Processed {} stop times...", count);
                }
            }
            LOG.info("Wrote {} stop times to stop_times.txt", count);
        }
    }

    private static void extractCalendar(Connection conn) throws Exception {
        LOG.info("Extracting calendar.txt...");
        String sql = "SELECT * FROM ties_google_calendar_vw";
        String file = OUTPUT_DIR + "/calendar.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("service_id")),
                    csvEscape(rs.getString("monday")),
                    csvEscape(rs.getString("tuesday")),
                    csvEscape(rs.getString("wednesday")),
                    csvEscape(rs.getString("thursday")),
                    csvEscape(rs.getString("friday")),
                    csvEscape(rs.getString("saturday")),
                    csvEscape(rs.getString("sunday")),
                    csvEscape(rs.getString("start_date")),
                    csvEscape(rs.getString("end_date"))));
                count++;
            }
            LOG.info("Wrote {} calendar records to calendar.txt", count);
        }
    }

    private static void extractCalendarDates(Connection conn) throws Exception {
        LOG.info("Extracting calendar_dates.txt...");
        String sql = "SELECT * FROM ties_google_calendar_dates_vw";
        String file = OUTPUT_DIR + "/calendar_dates.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("service_id,date,exception_type");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s",
                    csvEscape(rs.getString("service_id")),
                    csvEscape(rs.getString("date")),
                    csvEscape(rs.getString("exception_type"))));
                count++;
            }
            LOG.info("Wrote {} calendar date records to calendar_dates.txt", count);
        }
    }

    private static void extractFareMedia(Connection conn) throws Exception {
        LOG.info("Extracting fare_media.txt...");
        String sql = "SELECT * FROM ties_google_fare_media_vw";
        String file = OUTPUT_DIR + "/fare_media.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("fare_media_id,fare_media_name,fare_media_type");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s",
                    csvEscape(rs.getString("fare_media_id")),
                    csvEscape(rs.getString("fare_media_name")),
                    csvEscape(rs.getString("fare_media_type"))));
                count++;
            }
            LOG.info("Wrote {} fare media to fare_media.txt", count);
        }
    }

    private static void extractFareProducts(Connection conn) throws Exception {
        LOG.info("Extracting fare_products.txt...");
        String sql = "SELECT * FROM ties_google_fare_products_vw";
        String file = OUTPUT_DIR + "/fare_products.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("fare_product_id,fare_product_name,fare_media_id,amount,currency");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("fare_product_id")),
                    csvEscape(rs.getString("fare_product_name")),
                    csvEscape(rs.getString("fare_media_id")),
                    csvEscape(rs.getString("amount")),
                    csvEscape(rs.getString("currency"))));
                count++;
            }
            LOG.info("Wrote {} fare products to fare_products.txt", count);
        }
    }

    private static void extractFareLegRules(Connection conn) throws Exception {
        LOG.info("Extracting fare_leg_rules.txt...");
        String sql = "SELECT * FROM ties_google_fare_leg_rules_vw";
        String file = OUTPUT_DIR + "/fare_leg_rules.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("leg_group_id,network_id,from_area_id,to_area_id,fare_product_id");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("leg_group_id")),
                    csvEscape(rs.getString("network_id")),
                    csvEscape(rs.getString("from_area_id")),
                    csvEscape(rs.getString("to_area_id")),
                    csvEscape(rs.getString("fare_product_id"))));
                count++;
            }
            LOG.info("Wrote {} fare leg rules to fare_leg_rules.txt", count);
        }
    }

    private static void extractFareTransferRules(Connection conn) throws Exception {
        LOG.info("Extracting fare_transfer_rules.txt...");
        String sql = "SELECT * FROM ties_google_fare_transfer_rules_vw";
        String file = OUTPUT_DIR + "/fare_transfer_rules.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("from_leg_group_id,to_leg_group_id,fare_product_id,transfer_count,duration_limit");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s,%s,%s,%s",
                    csvEscape(rs.getString("from_leg_group_id")),
                    csvEscape(rs.getString("to_leg_group_id")),
                    csvEscape(rs.getString("fare_product_id")),
                    csvEscape(rs.getString("transfer_count")),
                    csvEscape(rs.getString("duration_limit"))));
                count++;
            }
            LOG.info("Wrote {} fare transfer rules to fare_transfer_rules.txt", count);
        }
    }

    private static void extractAreas(Connection conn) throws Exception {
        LOG.info("Extracting areas.txt...");
        String sql = "SELECT * FROM ties_google_areas_vw";
        String file = OUTPUT_DIR + "/areas.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("area_id,area_name");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s",
                    csvEscape(rs.getString("area_id")),
                    csvEscape(rs.getString("area_name"))));
                count++;
            }
            LOG.info("Wrote {} areas to areas.txt", count);
        }
    }

    private static void extractStopAreas(Connection conn) throws Exception {
        LOG.info("Extracting stop_areas.txt...");
        String sql = "SELECT * FROM ties_google_stop_areas_vw";
        String file = OUTPUT_DIR + "/stop_areas.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("area_id,stop_id");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s",
                    csvEscape(rs.getString("area_id")),
                    csvEscape(rs.getString("stop_id"))));
                count++;
                if (count % 10000 == 0) {
                    LOG.info("Processed {} stop areas...", count);
                }
            }
            LOG.info("Wrote {} stop areas to stop_areas.txt", count);
        }
    }

    private static void extractNetworks(Connection conn) throws Exception {
        LOG.info("Extracting networks.txt...");
        String sql = "SELECT * FROM ties_google_networks_vw";
        String file = OUTPUT_DIR + "/networks.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("network_id,network_name");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s",
                    csvEscape(rs.getString("column1")),
                    csvEscape(rs.getString("column2"))));
                count++;
            }
            LOG.info("Wrote {} networks to networks.txt", count);
        }
    }

    private static void extractRouteNetworks(Connection conn) throws Exception {
        LOG.info("Extracting route_networks.txt...");
        String sql = "SELECT * FROM ties_google_route_networks_vw";
        String file = OUTPUT_DIR + "/route_networks.txt";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql);
             PrintWriter writer = new PrintWriter(new FileWriter(file))) {

            writer.println("route_id,network_id");

            int count = 0;
            while (rs.next()) {
                writer.println(String.format("%s,%s",
                    csvEscape(rs.getString("route_id")),
                    csvEscape(rs.getString("network_id"))));
                count++;
            }
            LOG.info("Wrote {} route networks to route_networks.txt", count);
        }
    }

    /**
     * Escape CSV values - handle nulls and quote special characters
     */
    private static String csvEscape(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        // Quote if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
