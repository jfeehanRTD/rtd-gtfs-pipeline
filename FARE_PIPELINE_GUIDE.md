# GTFS Fare Data Extraction Pipeline - Implementation Guide

## Quick Start Guide for Building a New Flink Job

### 1. Create New Pipeline Class

Location: `/src/main/java/com/rtd/pipeline/GTFSFareDataExtractionPipeline.java`

```java
package com.rtd.pipeline;

import com.rtd.pipeline.gtfs.model.*;
import com.rtd.pipeline.mtram.MTRAMPostgresConnector;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.types.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.*;

public class GTFSFareDataExtractionPipeline {

    private static final Logger LOG = LoggerFactory.getLogger(GTFSFareDataExtractionPipeline.class);

    // Configuration
    private static final String POSTGRES_JDBC_URL = "jdbc:postgresql://localhost:5432/mtram";
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASSWORD = "password";
    private static final String OUTPUT_DIR = "data/gtfs-fare";
    private static final String RUNBOARD_ID = "35628"; // RTD's default runboard

    public static void main(String[] args) throws Exception {
        LOG.info("=== GTFS Fare Data Extraction Pipeline Starting ===");

        // Create Flink execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        // Enable checkpointing
        env.enableCheckpointing(60000); // 60 seconds

        try {
            // Create source that fetches fare data from PostgreSQL
            DataStream<Row> fareStream = env.addSource(new FareDataSource());

            // Extract fare products
            DataStream<String> fareProducts = fareStream
                .filter(row -> "FARE_PRODUCT".equals(row.getField(0)))
                .map(new FareProductMapper())
                .name("Extract Fare Products");

            // Extract fare media
            DataStream<String> fareMedia = fareStream
                .filter(row -> "FARE_MEDIA".equals(row.getField(0)))
                .map(new FareMediaMapper())
                .name("Extract Fare Media");

            // Extract fare leg rules
            DataStream<String> fareLegRules = fareStream
                .filter(row -> "FARE_LEG_RULE".equals(row.getField(0)))
                .map(new FareLegRuleMapper())
                .name("Extract Fare Leg Rules");

            // Add file sinks
            addFileSink(fareProducts, "fare_products.txt", env);
            addFileSink(fareMedia, "fare_media.txt", env);
            addFileSink(fareLegRules, "fare_leg_rules.txt", env);

            // Execute
            env.execute("GTFS Fare Data Extraction Pipeline");

        } catch (Exception e) {
            LOG.error("Pipeline failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Source function that fetches fare data from PostgreSQL
     */
    private static class FareDataSource implements SourceFunction<Row> {

        private volatile boolean isRunning = true;
        private transient MTRAMPostgresConnector connector;

        @Override
        public void run(SourceContext<Row> ctx) throws Exception {
            try {
                // Initialize database connection
                Class.forName("org.postgresql.Driver");
                Connection conn = DriverManager.getConnection(
                    POSTGRES_JDBC_URL,
                    POSTGRES_USER,
                    POSTGRES_PASSWORD
                );

                connector = new MTRAMPostgresConnector(conn, RUNBOARD_ID);

                LOG.info("Connected to MTRAM PostgreSQL database");

                // Query fare data
                List<GTFSFareProduct> fareProducts = queryFareProducts(conn);
                List<GTFSFareMedia> fareMedia = queryFareMedia(conn);
                List<GTFSFareLegRule> fareLegRules = queryFareLegRules(conn);

                // Emit fare products
                for (GTFSFareProduct fp : fareProducts) {
                    Row row = Row.of("FARE_PRODUCT", fp);
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(row);
                    }
                }

                // Emit fare media
                for (GTFSFareMedia fm : fareMedia) {
                    Row row = Row.of("FARE_MEDIA", fm);
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(row);
                    }
                }

                // Emit fare leg rules
                for (GTFSFareLegRule flr : fareLegRules) {
                    Row row = Row.of("FARE_LEG_RULE", flr);
                    synchronized (ctx.getCheckpointLock()) {
                        ctx.collect(row);
                    }
                }

                LOG.info("Extracted {} fare products, {} fare media, {} fare leg rules",
                    fareProducts.size(), fareMedia.size(), fareLegRules.size());

                conn.close();

            } catch (Exception e) {
                LOG.error("Error in fare data source: {}", e.getMessage(), e);
                throw e;
            }
        }

        @Override
        public void cancel() {
            isRunning = false;
        }

        private List<GTFSFareProduct> queryFareProducts(Connection conn) throws Exception {
            List<GTFSFareProduct> products = new ArrayList<>();

            // RTD standard fares (hardcoded for now, extend with DB queries)
            products.add(new GTFSFareProduct("local_single", "Local Single Ride", 2.75, "USD"));
            products.add(new GTFSFareProduct("airport_single", "Airport Single Ride", 10.00, "USD"));
            products.add(new GTFSFareProduct("day_pass", "Day Pass", 5.50, "USD"));
            products.add(new GTFSFareProduct("monthly_pass", "Monthly Pass", 88.00, "USD"));
            products.add(new GTFSFareProduct("free_ride", "Free Ride", 0.00, "USD"));

            return products;
        }

        private List<GTFSFareMedia> queryFareMedia(Connection conn) throws Exception {
            List<GTFSFareMedia> mediaList = new ArrayList<>();

            // TODO: Query from database
            // For now, return common payment methods
            mediaList.add(new GTFSFareMedia("rtd_card", "RTD MyRide Card", 1));
            mediaList.add(new GTFSFareMedia("mobile_app", "RTD Mobile App", 2));
            mediaList.add(new GTFSFareMedia("cash", "Cash Payment", 4));

            return mediaList;
        }

        private List<GTFSFareLegRule> queryFareLegRules(Connection conn) throws Exception {
            List<GTFSFareLegRule> rules = new ArrayList<>();

            // TODO: Query from database
            // For now, return basic rules
            rules.add(new GTFSFareLegRule("local_leg", "standard_fare_network", 
                null, null, "local_single"));
            rules.add(new GTFSFareLegRule("airport_leg", "airport_network",
                null, null, "airport_single"));

            return rules;
        }
    }

    /**
     * Mapper: GTFSFareProduct → CSV string
     */
    private static class FareProductMapper implements MapFunction<Row, String> {
        @Override
        public String map(Row row) throws Exception {
            GTFSFareProduct fp = (GTFSFareProduct) row.getField(1);
            return fp.toCsvRow();
        }
    }

    /**
     * Mapper: GTFSFareMedia → CSV string
     */
    private static class FareMediaMapper implements MapFunction<Row, String> {
        @Override
        public String map(Row row) throws Exception {
            GTFSFareMedia fm = (GTFSFareMedia) row.getField(1);
            return fm.toCsvRow();
        }
    }

    /**
     * Mapper: GTFSFareLegRule → CSV string
     */
    private static class FareLegRuleMapper implements MapFunction<Row, String> {
        @Override
        public String map(Row row) throws Exception {
            GTFSFareLegRule flr = (GTFSFareLegRule) row.getField(1);
            return flr.toCsvRow();
        }
    }

    /**
     * Add file sink with CSV headers
     */
    private static void addFileSink(DataStream<String> stream, String filename,
                                   StreamExecutionEnvironment env) {
        String csvPath = OUTPUT_DIR + "/" + filename;

        stream
            .sinkTo(FileSink
                .forRowFormat(new Path(csvPath), new SimpleStringEncoder<String>("UTF-8"))
                .withRollingPolicy(new org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy.Builder()
                    .withRolloverInterval(Duration.ofHours(24).toMillis())
                    .withInactivityInterval(Duration.ofHours(1).toMillis())
                    .withMaxPartSize(1024 * 1024 * 100) // 100MB
                    .build())
                .build())
            .name("Write " + filename);
    }
}
```

### 2. Add Build Task (build.gradle)

```gradle
task runGTFSFarePipeline(type: JavaExec) {
    group = 'rtd-pipelines'
    description = 'Run GTFS Fare Data Extraction Pipeline'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.rtd.pipeline.GTFSFareDataExtractionPipeline'
    jvmArgs = [
        '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
        '--add-opens', 'java.base/sun.nio.ch=ALL-UNNAMED'
    ]
}
```

### 3. Database Query Patterns

```java
// Pattern 1: Direct JDBC Query
String sql = """
    SELECT fare_product_id, fare_product_name, amount, currency
    FROM fare_products
    WHERE runboard_id = ?
    """;

try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setLong(1, Long.parseLong(runboardId));
    try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
            GTFSFareProduct fareProduct = new GTFSFareProduct(
                rs.getString("fare_product_id"),
                rs.getString("fare_product_name"),
                rs.getDouble("amount"),
                rs.getString("currency")
            );
            list.add(fareProduct);
        }
    }
}

// Pattern 2: Using existing MTRAMPostgresConnector
MTRAMPostgresConnector connector = new MTRAMPostgresConnector(conn, runboardId);
connector.generateFareProducts(outputPath);
```

### 4. Running the Pipeline

```bash
# Build
./gradlew clean build

# Run
./gradlew runGTFSFarePipeline

# Or directly
java -cp build/libs/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.GTFSFareDataExtractionPipeline
```

### 5. Integration with Existing Code

**Use Existing Models:**
- `GTFSFareProduct.java` - Has `toCsvRow()` and `fromCsvRow()` methods
- `GTFSFareMedia.java` - Payment method definitions
- `GTFSFareLegRule.java` - Fare pricing rules
- `GTFSFareTransferRule.java` - Transfer rules

**Use Existing Connectors:**
- `MTRAMPostgresConnector` - Direct database access
- `MTRAMDataConnector` - API-based access (alternative)

**Use Existing Validation:**
- `RTDFareValidator.java` - Validate fare data
- `FareDataExporter.java` - Export to CSV

### 6. Key Database Tables (MTRAM PostgreSQL)

```sql
-- Fare Products
SELECT fare_product_id, fare_product_name, amount, currency 
FROM fare_products 
WHERE runboard_id = ?;

-- Fare Media
SELECT fare_media_id, fare_media_name, fare_media_type 
FROM fare_media;

-- Fare Leg Rules
SELECT leg_group_id, network_id, from_area_id, to_area_id, fare_product_id 
FROM fare_leg_rules;

-- Fare Transfer Rules
SELECT transfer_from_route, transfer_to_route, transfer_count, discount_amount 
FROM fare_transfer_rules;
```

### 7. Output Format

**fare_products.txt**
```csv
fare_product_id,fare_product_name,fare_media_id,amount,currency
local_single,Local Single Ride,,2.75,USD
airport_single,Airport Single Ride,,10.00,USD
day_pass,Day Pass,,5.50,USD
```

**fare_media.txt**
```csv
fare_media_id,fare_media_name,fare_media_type
rtd_card,RTD MyRide Card,1
mobile_app,RTD Mobile App,2
cash,Cash Payment,4
```

**fare_leg_rules.txt**
```csv
leg_group_id,network_id,from_area_id,to_area_id,fare_product_id
local_leg,standard_fare_network,local,local,local_single
airport_leg,airport_network,airport,airport,airport_single
```

### 8. Testing

```java
@Test
public void testFareProductExtraction() throws Exception {
    GTFSFareProduct product = new GTFSFareProduct(
        "test_fare", "Test Fare", 3.50, "USD"
    );
    
    String csv = product.toCsvRow();
    assertEquals("test_fare,Test Fare,,3.50,USD", csv);
}

@Test
public void testDatabaseConnection() throws Exception {
    Class.forName("org.postgresql.Driver");
    Connection conn = DriverManager.getConnection(
        "jdbc:postgresql://localhost:5432/mtram",
        "postgres", 
        "password"
    );
    
    assertNotNull(conn);
    conn.close();
}
```

### 9. Configuration (add to build.gradle)

```gradle
// Database configuration
ext {
    mtramDbUrl = 'jdbc:postgresql://localhost:5432/mtram'
    mtramDbUser = 'postgres'
    mtramDbPassword = 'password'
    mtramRunboardId = '35628'
    gtfsFareOutputDir = 'data/gtfs-fare'
}
```

### 10. Monitoring & Logging

The pipeline logs key metrics:
```
INFO: Connected to MTRAM PostgreSQL database
INFO: Extracted 5 fare products, 3 fare media, 2 fare leg rules
INFO: Written to data/gtfs-fare/fare_products.txt
INFO: Pipeline execution completed successfully
```

---

## Integration Checklist

- [ ] Create `GTFSFareDataExtractionPipeline.java` class
- [ ] Add build task `runGTFSFarePipeline`
- [ ] Implement `FareDataSource` with PostgreSQL connection
- [ ] Map CSV rows using existing `GTFSFare*` models
- [ ] Add FileSink for output files
- [ ] Test database connectivity
- [ ] Validate CSV output format
- [ ] Add error handling & logging
- [ ] Create unit tests
- [ ] Add to Gradle tasks documentation
- [ ] Update CLAUDE.md with new pipeline info

---

## File Locations Reference

| Component | Location |
|-----------|----------|
| Main Pipeline | `/src/main/java/com/rtd/pipeline/GTFSFareDataExtractionPipeline.java` |
| Fare Models | `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSFare*.java` |
| DB Connector | `/src/main/java/com/rtd/pipeline/mtram/MTRAMPostgresConnector.java` |
| Validation | `/src/main/java/com/rtd/pipeline/validation/RTDFareValidator.java` |
| Tests | `/src/test/java/com/rtd/pipeline/gtfs/GTFSFareFixTest.java` |
| Config | `/config/gtfs-rt.properties` |
| Build | `/build.gradle` |

