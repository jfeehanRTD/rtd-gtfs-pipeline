package com.rtd.pipeline;

import com.rtd.pipeline.ties.OracleTIESSourceConnector;
import com.rtd.pipeline.ties.PostgreSQLTIESSinkConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data migration pipeline to extract GTFS fare data from Oracle TIES database
 * and load it into PostgreSQL TIES database.
 *
 * This pipeline migrates the 14 tables required for GTFS fare extraction:
 * - Simple fare tables (4): fare_media, fare_products, fare_leg_rules, fare_transfer_rules
 * - Complex area tables (10): areas, stops, runboards, trip_events, trips, etc.
 *
 * Usage:
 *   ./gradlew runTIESMigration -Pagency=RTD
 *   ./gradlew runTIESMigration -Pagency=CDOT
 *   ./gradlew runTIESMigration -Pagency=ALL
 */
public class TIESDataMigrationPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(TIESDataMigrationPipeline.class);

    // Oracle connection configuration
    private static final String ORACLE_JDBC_URL = System.getenv("ORACLE_TIES_URL");
    private static final String ORACLE_USER = System.getenv("ORACLE_TIES_USER");
    private static final String ORACLE_PASSWORD = System.getenv("ORACLE_TIES_PASSWORD");

    // PostgreSQL connection configuration
    private static final String POSTGRES_JDBC_URL = System.getenv("POSTGRES_TIES_URL");
    private static final String POSTGRES_USER = System.getenv("POSTGRES_TIES_USER");
    private static final String POSTGRES_PASSWORD = System.getenv("POSTGRES_TIES_PASSWORD");

    public static void main(String[] args) throws Exception {
        // Get agency from command line args or system property
        String agency = System.getProperty("agency", "ALL");
        if (args.length > 0) {
            agency = args[0].toUpperCase();
        }

        if (!agency.equals("RTD") && !agency.equals("CDOT") && !agency.equals("ALL")) {
            LOG.error("Invalid agency: {}. Must be RTD, CDOT, or ALL", agency);
            System.exit(1);
        }

        LOG.info("=== TIES Data Migration Pipeline Starting ===");
        LOG.info("Agency: {}", agency);
        LOG.info("Source: Oracle @ {}", ORACLE_JDBC_URL);
        LOG.info("Target: PostgreSQL @ {}", POSTGRES_JDBC_URL);

        Connection oracleConn = null;
        Connection postgresConn = null;

        try {
            // Connect to Oracle
            LOG.info("Connecting to Oracle TIES database...");
            Class.forName("oracle.jdbc.OracleDriver");
            oracleConn = DriverManager.getConnection(ORACLE_JDBC_URL, ORACLE_USER, ORACLE_PASSWORD);
            LOG.info("Connected to Oracle: {}", new OracleTIESSourceConnector(oracleConn).getDatabaseVersion());

            // Connect to PostgreSQL
            LOG.info("Connecting to PostgreSQL TIES database...");
            Class.forName("org.postgresql.Driver");
            postgresConn = DriverManager.getConnection(POSTGRES_JDBC_URL, POSTGRES_USER, POSTGRES_PASSWORD);
            LOG.info("Connected to PostgreSQL: {}", new PostgreSQLTIESSinkConnector(postgresConn).getDatabaseVersion());

            // Create connectors
            OracleTIESSourceConnector oracleSource = new OracleTIESSourceConnector(oracleConn);
            PostgreSQLTIESSinkConnector postgresSink = new PostgreSQLTIESSinkConnector(postgresConn);

            // Verify connections
            if (!oracleSource.testConnection() || !postgresSink.testConnection()) {
                throw new RuntimeException("Database connection test failed");
            }

            // Execute migration
            migrateTables(oracleSource, postgresSink, agency);

            LOG.info("=== Migration Completed Successfully ===");

        } catch (Exception e) {
            LOG.error("Migration failed: {}", e.getMessage(), e);
            throw e;
        } finally {
            if (oracleConn != null && !oracleConn.isClosed()) {
                oracleConn.close();
                LOG.info("Oracle connection closed");
            }
            if (postgresConn != null && !postgresConn.isClosed()) {
                postgresConn.close();
                LOG.info("PostgreSQL connection closed");
            }
        }
    }

    /**
     * Migrate all required tables
     */
    private static void migrateTables(OracleTIESSourceConnector source,
                                      PostgreSQLTIESSinkConnector sink,
                                      String agency) throws Exception {
        LOG.info("=== Starting Table Migration ===");

        int totalRows = 0;

        // === Phase 1: Simple Fare Tables (agency-specific) ===

        if (agency.equals("ALL") || agency.equals("RTD") || agency.equals("CDOT")) {
            LOG.info("--- Phase 1: Simple Fare Tables ---");

            String[] agenciesToMigrate = agency.equals("ALL")
                ? new String[]{"RTD", "CDOT"}
                : new String[]{agency};

            for (String ag : agenciesToMigrate) {
                LOG.info("Migrating fare data for agency: {}", ag);

                // 1. fare_media
                totalRows += migrateTable(source, sink,
                    "ties_gtfs_fare_media",
                    () -> source.extractFareMedia(ag),
                    false); // Don't truncate, append by agency

                // 2. fare_products
                totalRows += migrateTable(source, sink,
                    "ties_gtfs_fare_products",
                    () -> source.extractFareProducts(ag),
                    false);

                // 3. fare_leg_rules
                totalRows += migrateTable(source, sink,
                    "ties_gtfs_fare_leg_rules",
                    () -> source.extractFareLegRules(ag),
                    false);

                // 4. fare_transfer_rules
                totalRows += migrateTable(source, sink,
                    "ties_gtfs_fare_transfer_rules",
                    () -> source.extractFareTransferRules(ag),
                    false);

                // 5. areas
                totalRows += migrateTable(source, sink,
                    "ties_gtfs_areas",
                    () -> source.extractAreas(ag),
                    false);
            }
        }

        // === Phase 2: Core GTFS Reference Tables ===

        LOG.info("--- Phase 2: Core GTFS Reference Tables ---");

        // 6. stops (all stops)
        totalRows += migrateTable(source, sink,
            "ties_stops",
            () -> source.extractStops(),
            true);

        // 7. google_runboard (current runboard)
        totalRows += migrateTable(source, sink,
            "ties_google_runboard",
            () -> source.extractGoogleRunboard(),
            true);

        // Get the latest runboard ID
        List<Map<String, Object>> runboards = source.extractRunboards();
        List<Long> runboardIds = new ArrayList<>();
        for (Map<String, Object> runboard : runboards) {
            runboardIds.add(((Number) runboard.get("runboard_id")).longValue());
        }
        LOG.info("Found {} active runboards to migrate", runboardIds.size());

        // === Phase 3: Runboard-specific Tables ===

        LOG.info("--- Phase 3: Runboard-specific Tables ---");

        // 8. trip_events (stop times data)
        totalRows += migrateTable(source, sink,
            "ties_trip_events",
            () -> source.extractTripEvents(runboardIds),
            true);

        // 9. rpt_trips (trips data)
        totalRows += migrateTable(source, sink,
            "ties_rpt_trips",
            () -> source.extractTrips(runboardIds),
            true);

        // 10. patterns
        totalRows += migrateTable(source, sink,
            "ties_patterns",
            () -> source.extractPatterns(runboardIds),
            true);

        // 11. service_types
        totalRows += migrateTable(source, sink,
            "ties_service_types",
            () -> source.extractServiceTypes(runboardIds),
            true);

        // 12. comments
        totalRows += migrateTable(source, sink,
            "ties_comments",
            () -> source.extractComments(runboardIds),
            true);

        // === Phase 4: Reference Tables (no runboard_id) ===

        LOG.info("--- Phase 4: Route Destination Reference Tables ---");

        // 13. route_destinations
        totalRows += migrateTable(source, sink,
            "ties_route_destinations",
            () -> source.extractRouteDestinations(),
            true);

        // 14. route_destination1
        totalRows += migrateTable(source, sink,
            "ties_route_destination1",
            () -> source.extractRouteDestination1(),
            true);

        LOG.info("Migration includes all tables needed for complete GTFS feed");

        // === Summary ===

        LOG.info("=== Migration Summary ===");
        LOG.info("Total rows migrated: {}", totalRows);
        LOG.info("Agency: {}", agency);

        // Verify data in PostgreSQL
        LOG.info("--- Verification ---");
        verifyMigration(sink);
    }

    /**
     * Migrate a single table
     */
    private static int migrateTable(OracleTIESSourceConnector source,
                                    PostgreSQLTIESSinkConnector sink,
                                    String tableName,
                                    TableExtractor extractor,
                                    boolean truncate) throws Exception {
        LOG.info("Migrating table: {}", tableName);

        try {
            // Check if table exists in PostgreSQL
            if (!sink.tableExists(tableName)) {
                LOG.error("Table does not exist in PostgreSQL: {}", tableName);
                LOG.warn("Skipping table: {}", tableName);
                return 0;
            }

            // Truncate if requested
            if (truncate) {
                sink.truncateTable(tableName);
            }

            // Extract from Oracle
            List<Map<String, Object>> rows = extractor.extract();

            if (rows.isEmpty()) {
                LOG.warn("No data extracted from Oracle for table: {}", tableName);
                return 0;
            }

            // Load into PostgreSQL
            int inserted = sink.insertRows(tableName, rows);

            // Verify
            long pgCount = sink.getRowCount(tableName);
            LOG.info("Table {} migration complete: {} rows inserted, {} total rows in PostgreSQL",
                tableName, inserted, pgCount);

            return inserted;

        } catch (Exception e) {
            LOG.error("Error migrating table {}: {}", tableName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Verify migration results (all tables needed for complete GTFS feed)
     */
    private static void verifyMigration(PostgreSQLTIESSinkConnector sink) throws Exception {
        String[] tables = {
            // Fare tables
            "ties_gtfs_fare_media",
            "ties_gtfs_fare_products",
            "ties_gtfs_fare_leg_rules",
            "ties_gtfs_fare_transfer_rules",
            "ties_gtfs_areas",
            // Core GTFS tables
            "ties_stops",
            "ties_google_runboard",
            "ties_trip_events",
            "ties_rpt_trips",
            "ties_patterns",
            "ties_service_types",
            "ties_comments",
            "ties_route_destinations",
            "ties_route_destination1"
        };

        for (String table : tables) {
            try {
                if (sink.tableExists(table)) {
                    long count = sink.getRowCount(table);
                    LOG.info("✓ {}: {} rows", table, count);
                } else {
                    LOG.warn("✗ {}: Table does not exist", table);
                }
            } catch (Exception e) {
                LOG.error("✗ {}: Error - {}", table, e.getMessage());
            }
        }
    }

    /**
     * Functional interface for table extraction
     */
    @FunctionalInterface
    private interface TableExtractor {
        List<Map<String, Object>> extract() throws Exception;
    }
}
