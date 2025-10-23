package com.rtd.pipeline;

import com.rtd.pipeline.gtfs.model.*;
import com.rtd.pipeline.ties.TIESPostgresConnector;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Flink pipeline to extract GTFS fare data from TIES PostgreSQL database.
 * Replaces Oracle PL/SQL TIES_GTFS_VW_PKG package with pure Java solution.
 *
 * Generates the following GTFS v2 fare files:
 * - fare_media.txt
 * - fare_products.txt
 * - fare_leg_rules.txt
 * - fare_transfer_rules.txt
 * - areas.txt
 * - stop_areas.txt
 *
 * Usage:
 *   ./gradlew runTIESFarePipeline -Pagency=RTD
 *   ./gradlew runTIESFarePipeline -Pagency=CDOT
 */
public class TIESGTFSFareExtractionPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(TIESGTFSFareExtractionPipeline.class);

    // Configuration - can be overridden via environment variables or system properties
    private static final String POSTGRES_JDBC_URL = System.getenv("POSTGRES_TIES_URL");
    private static final String POSTGRES_USER = System.getenv("POSTGRES_TIES_USER");
    private static final String POSTGRES_PASSWORD = System.getenv("POSTGRES_TIES_PASSWORD");
    private static final String OUTPUT_DIR = System.getenv().getOrDefault(
        "TIES_OUTPUT_DIR", "data/gtfs-ties");

    public static void main(String[] args) throws Exception {
        // Get agency from command line args or system property
        String agency = System.getProperty("agency", "RTD");
        if (args.length > 0) {
            agency = args[0].toUpperCase();
        }

        if (!agency.equals("RTD") && !agency.equals("CDOT")) {
            LOG.error("Invalid agency: {}. Must be RTD or CDOT", agency);
            System.exit(1);
        }

        LOG.info("=== TIES GTFS Fare Data Extraction Pipeline Starting ===");
        LOG.info("Agency: {}", agency);
        LOG.info("Database: {}", POSTGRES_JDBC_URL);
        LOG.info("Output Directory: {}", OUTPUT_DIR);

        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1); // Single worker for data consistency

        // Enable checkpointing for fault tolerance
        env.enableCheckpointing(60000); // 60 seconds

        try {
            // Extract fare data from TIES PostgreSQL
            // Extract each type separately to avoid type erasure issues
            FareDataCollections fareData = extractAllFareData(agency);

            // === Create DataStreams for each GTFS file ===

            // 1. fare_media.txt
            DataStream<String> fareMediaStream = env
                .fromCollection(fareData.fareMedia)
                .map(new MapFunction<GTFSFareMedia, String>() {
                    @Override
                    public String map(GTFSFareMedia media) throws Exception {
                        return media.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Fare Media to CSV");

            // 2. fare_products.txt
            DataStream<String> fareProductsStream = env
                .fromCollection(fareData.fareProducts)
                .map(new MapFunction<GTFSFareProduct, String>() {
                    @Override
                    public String map(GTFSFareProduct product) throws Exception {
                        return product.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Fare Products to CSV");

            // 3. fare_leg_rules.txt
            DataStream<String> fareLegRulesStream = env
                .fromCollection(fareData.fareLegRules)
                .map(new MapFunction<GTFSFareLegRule, String>() {
                    @Override
                    public String map(GTFSFareLegRule rule) throws Exception {
                        return rule.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Fare Leg Rules to CSV");

            // 4. fare_transfer_rules.txt
            DataStream<String> fareTransferRulesStream = env
                .fromCollection(fareData.fareTransferRules)
                .map(new MapFunction<GTFSFareTransferRule, String>() {
                    @Override
                    public String map(GTFSFareTransferRule rule) throws Exception {
                        return rule.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Fare Transfer Rules to CSV");

            // 5. areas.txt
            DataStream<String> areasStream = env
                .fromCollection(fareData.areas)
                .map(new MapFunction<GTFSArea, String>() {
                    @Override
                    public String map(GTFSArea area) throws Exception {
                        return area.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Areas to CSV");

            // 6. stop_areas.txt
            DataStream<String> stopAreasStream = env
                .fromCollection(fareData.stopAreas)
                .map(new MapFunction<GTFSStopArea, String>() {
                    @Override
                    public String map(GTFSStopArea stopArea) throws Exception {
                        return stopArea.toCsvRow();
                    }
                })
                .returns(String.class)
                .name("Map Stop Areas to CSV");

            // === Write streams to CSV files ===

            String agencyDir = OUTPUT_DIR + "/" + agency.toLowerCase();

            writeToFile(fareMediaStream, agencyDir, "fare_media.txt", GTFSFareMedia.getCsvHeader());
            writeToFile(fareProductsStream, agencyDir, "fare_products.txt", GTFSFareProduct.getCsvHeader());
            writeToFile(fareLegRulesStream, agencyDir, "fare_leg_rules.txt", GTFSFareLegRule.getCsvHeader());
            writeToFile(fareTransferRulesStream, agencyDir, "fare_transfer_rules.txt", GTFSFareTransferRule.getCsvHeader());
            writeToFile(areasStream, agencyDir, "areas.txt", GTFSArea.getCsvHeader());
            writeToFile(stopAreasStream, agencyDir, "stop_areas.txt", GTFSStopArea.getCsvHeader());

            // Execute pipeline
            LOG.info("Starting pipeline execution...");
            env.execute("TIES GTFS Fare Data Extraction - " + agency);
            LOG.info("=== Pipeline Completed Successfully ===");

        } catch (Exception e) {
            LOG.error("Pipeline execution failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Write DataStream to CSV file with header
     */
    private static void writeToFile(DataStream<String> stream, String baseDir,
                                     String filename, String csvHeader) {
        String outputPath = baseDir + "/" + filename;

        // Note: To add CSV header, you would need to prepend it to the first row
        // For now, the header is documented in each model's getCsvHeader() method
        // and should be added manually or via post-processing

        FileSink<String> fileSink = FileSink
            .forRowFormat(new Path(outputPath), new SimpleStringEncoder<String>("UTF-8"))
            .withOutputFileConfig(
                OutputFileConfig.builder()
                    .withPartPrefix("part")
                    .withPartSuffix(".csv")
                    .build()
            )
            .withRollingPolicy(
                DefaultRollingPolicy.builder()
                    .withRolloverInterval(Duration.ofHours(24).toMillis())
                    .withInactivityInterval(Duration.ofHours(1).toMillis())
                    .withMaxPartSize(100 * 1024 * 1024) // 100MB
                    .build()
            )
            .build();

        stream.sinkTo(fileSink).name("Write " + filename);
        LOG.info("Configured sink for: {}", outputPath);
    }

    /**
     * Helper class to hold separate collections for each GTFS data type
     * This avoids type erasure issues with Flink's serialization
     */
    private static class FareDataCollections {
        final List<GTFSFareMedia> fareMedia;
        final List<GTFSFareProduct> fareProducts;
        final List<GTFSFareLegRule> fareLegRules;
        final List<GTFSFareTransferRule> fareTransferRules;
        final List<GTFSArea> areas;
        final List<GTFSStopArea> stopAreas;

        FareDataCollections(List<GTFSFareMedia> fareMedia,
                           List<GTFSFareProduct> fareProducts,
                           List<GTFSFareLegRule> fareLegRules,
                           List<GTFSFareTransferRule> fareTransferRules,
                           List<GTFSArea> areas,
                           List<GTFSStopArea> stopAreas) {
            this.fareMedia = fareMedia;
            this.fareProducts = fareProducts;
            this.fareLegRules = fareLegRules;
            this.fareTransferRules = fareTransferRules;
            this.areas = areas;
            this.stopAreas = stopAreas;
        }
    }

    /**
     * Extract all fare data from TIES PostgreSQL database
     * Returns separate typed collections to avoid Flink type erasure issues
     */
    private static FareDataCollections extractAllFareData(String agency) throws Exception {
        Connection conn = null;

        try {
            // Load PostgreSQL JDBC driver
            Class.forName("org.postgresql.Driver");

            // Connect to TIES database
            LOG.info("Connecting to TIES PostgreSQL database...");
            conn = DriverManager.getConnection(
                POSTGRES_JDBC_URL,
                POSTGRES_USER,
                POSTGRES_PASSWORD
            );

            LOG.info("Connected successfully to TIES database");

            // Create connector
            TIESPostgresConnector connector = new TIESPostgresConnector(conn, agency);

            // Test connection
            if (!connector.testConnection()) {
                throw new RuntimeException("Failed to verify database connection");
            }

            // === Extract data from each table ===

            // 1. Fare Media
            LOG.info("Extracting fare media...");
            List<GTFSFareMedia> fareMedia = connector.extractFareMedia();

            // 2. Fare Products
            LOG.info("Extracting fare products...");
            List<GTFSFareProduct> fareProducts = connector.extractFareProducts();

            // 3. Fare Leg Rules
            LOG.info("Extracting fare leg rules...");
            List<GTFSFareLegRule> fareLegRules = connector.extractFareLegRules();

            // 4. Fare Transfer Rules
            LOG.info("Extracting fare transfer rules...");
            List<GTFSFareTransferRule> fareTransferRules = connector.extractFareTransferRules();

            // 5. Areas
            LOG.info("Extracting areas...");
            List<GTFSArea> areas = connector.extractAreas();

            // 6. Stop Areas
            LOG.info("Extracting stop areas...");
            List<GTFSStopArea> stopAreas = connector.extractStopAreas();

            LOG.info("=== Extraction Summary ===");
            LOG.info("Fare Media: {} records", fareMedia.size());
            LOG.info("Fare Products: {} records", fareProducts.size());
            LOG.info("Fare Leg Rules: {} records", fareLegRules.size());
            LOG.info("Fare Transfer Rules: {} records", fareTransferRules.size());
            LOG.info("Areas: {} records", areas.size());
            LOG.info("Stop Areas: {} records", stopAreas.size());
            LOG.info("Total Records: {}",
                fareMedia.size() + fareProducts.size() + fareLegRules.size() +
                fareTransferRules.size() + areas.size() + stopAreas.size());

            conn.close();
            LOG.info("Database connection closed");

            return new FareDataCollections(fareMedia, fareProducts, fareLegRules,
                fareTransferRules, areas, stopAreas);

        } catch (Exception e) {
            LOG.error("Error extracting fare data: {}", e.getMessage(), e);
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
            throw e;
        }
    }
}
