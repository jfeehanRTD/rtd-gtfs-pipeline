# TIES GTFS Fare Data Extraction Pipeline

## Overview

This Flink-based pipeline replaces the Oracle PL/SQL `TIES_GTFS_VW_PKG` package with a pure Java solution for extracting GTFS v2 fare data from the TIES PostgreSQL database.

**Created:** October 22, 2025
**Status:** MVP - Ready for testing

## What It Does

Extracts 6 GTFS v2 fare files from TIES PostgreSQL database:

1. **fare_media.txt** - Payment methods (cards, mobile app, cash)
2. **fare_products.txt** - Fare products (single ride, day pass, monthly pass)
3. **fare_leg_rules.txt** - Fare rules for trip legs
4. **fare_transfer_rules.txt** - Transfer pricing rules
5. **areas.txt** - Fare zones/areas
6. **stop_areas.txt** - Stop to area mappings

### Supported Agencies

- **RTD** - Regional Transportation District (Denver metro)
- **CDOT** - Colorado Department of Transportation (Bustang)

## Architecture

```
┌────────────────────────────────────────────┐
│  TIESGTFSFareExtractionPipeline.java      │
│  (Main Flink Job)                          │
└────────────────┬───────────────────────────┘
                 │
┌────────────────▼───────────────────────────┐
│  TIESPostgresConnector.java                │
│  - extractFareMedia()                      │
│  - extractFareProducts()                   │
│  - extractFareLegRules()                   │
│  - extractFareTransferRules()              │
│  - extractAreas()                          │
│  - extractStopAreas()                      │
└────────────────┬───────────────────────────┘
                 │
┌────────────────▼───────────────────────────┐
│  TIES PostgreSQL Database                  │
│  - ties_gtfs_fare_media                    │
│  - ties_gtfs_fare_products                 │
│  - ties_gtfs_fare_leg_rules                │
│  - ties_gtfs_fare_transfer_rules           │
│  - ties_gtfs_areas                         │
│  - ties_stops (for stop_areas)             │
│  + 4 more tables for complex queries       │
└────────────────────────────────────────────┘
```

## Files Created

### Models (GTFS data structures)
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSArea.java` - ✅ Created
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSStopArea.java` - ✅ Created
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSFareProduct.java` - ✅ Existing
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSFareMedia.java` - ✅ Existing
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSFareLegRule.java` - ✅ Existing
- `/src/main/java/com/rtd/pipeline/gtfs/model/GTFSFareTransferRule.java` - ✅ Existing

### Connector (Database access)
- `/src/main/java/com/rtd/pipeline/ties/TIESPostgresConnector.java` - ✅ Created

### Pipeline (Main Flink job)
- `/src/main/java/com/rtd/pipeline/TIESGTFSFareExtractionPipeline.java` - ✅ Created

### Build Configuration
- `build.gradle` - ✅ Updated with new tasks

## Database Configuration

### Connection Details

The pipeline connects to the TIES PostgreSQL database running on localhost (converted from Oracle).

**Default Configuration:**
```properties
TIES_DB_URL=jdbc:postgresql://localhost:5432/ties
TIES_DB_USER=ties
TIES_DB_PASSWORD=TiesPassword123
TIES_OUTPUT_DIR=data/gtfs-ties
```

### Environment Variables

Override defaults by setting environment variables:

```bash
export TIES_DB_URL="jdbc:postgresql://your-host:5432/ties"
export TIES_DB_USER="your_user"
export TIES_DB_PASSWORD="your_password"
export TIES_OUTPUT_DIR="path/to/output"
```

### Database Tables Required

The pipeline queries these TIES PostgreSQL tables:

**Simple Queries (4 tables):**
1. `ties_gtfs_fare_media`
2. `ties_gtfs_fare_products`
3. `ties_gtfs_fare_leg_rules`
4. `ties_gtfs_fare_transfer_rules`

**Complex Queries (10 additional tables):**
5. `ties_google_runboard`
6. `ties_runboards`
7. `ties_stops`
8. `ties_trip_events`
9. `ties_rpt_trips`
10. `ties_route_type_categories`
11. `ties_route_type_category_link`
12. `ties_trip_event_label_rules`
13. `ties_pattern_stops`
14. `ties_gtfs_areas`

**Total: 14 tables**

## Quick Start

### Prerequisites

1. **Java 24** with preview features enabled
2. **Gradle 8.14+**
3. **TIES PostgreSQL Database** running on localhost:5432
4. **Docker** (if using docker-compose for PostgreSQL)

### 1. Start TIES PostgreSQL Database

```bash
cd ~/projects/mtram
docker-compose -f docker-compose-postgres.yml up -d
```

Verify database is running:
```bash
docker exec ties-postgres psql -U ties -d ties -c "SELECT count(*) FROM ties_gtfs_fare_products;"
```

### 2. Build the Project

```bash
cd ~/demo/rtd-gtfs-pipeline-refArch1
./gradlew clean build
```

### 3. Run the Pipeline

**Option A: Run with default agency (RTD)**
```bash
./gradlew runTIESFarePipeline
```

**Option B: Specify agency via parameter**
```bash
# For RTD
./gradlew runTIESFarePipeline -Pagency=RTD

# For CDOT/Bustang
./gradlew runTIESFarePipeline -Pagency=CDOT
```

**Option C: Use dedicated tasks**
```bash
# For RTD
./gradlew runTIESFarePipelineRTD

# For CDOT
./gradlew runTIESFarePipelineCDOT
```

### 4. Check Output

Output files are written to:
```
data/gtfs-ties/rtd/
├── fare_media.txt/
├── fare_products.txt/
├── fare_leg_rules.txt/
├── fare_transfer_rules.txt/
├── areas.txt/
└── stop_areas.txt/

data/gtfs-ties/cdot/
├── (same structure for Bustang)
```

Each directory contains CSV part files (e.g., `part-xxxxx.csv`).

## Output Format

### fare_media.txt
```csv
fare_media_id,fare_media_name,fare_media_type
rtd_card,RTD MyRide Card,1
mobile_app,RTD Mobile App,2
cash,Cash Payment,4
```

### fare_products.txt
```csv
fare_product_id,fare_product_name,fare_media_id,amount,currency
local_single,Local Single Ride,,2.75,USD
airport_single,Airport Single Ride,,10.00,USD
day_pass,Day Pass,,5.50,USD
```

### fare_leg_rules.txt
```csv
leg_group_id,network_id,from_area_id,to_area_id,fare_product_id
local_leg,standard_network,,,local_single
airport_leg,airport_network,,,airport_single
```

### fare_transfer_rules.txt
```csv
from_leg_group_id,to_leg_group_id,transfer_count,duration_limit,duration_limit_type,fare_transfer_type,transfer_fare
local_leg,local_leg,1,5400,0,1,0.00
```

### areas.txt
```csv
area_id,area_name
local_fare_zone,Local Fare Zone
airport,Denver International Airport Zone
```

### stop_areas.txt
```csv
stop_id,area_id
UNION,local_fare_zone
AIRPT,airport
```

## Gradle Tasks

All tasks are in the `rtd-pipelines` group:

```bash
# View all available tasks
./gradlew tasks --group rtd-pipelines

# Available TIES tasks:
./gradlew runTIESFarePipeline          # Run with -Pagency=RTD or -Pagency=CDOT
./gradlew runTIESFarePipelineRTD       # Run for RTD only
./gradlew runTIESFarePipelineCDOT      # Run for CDOT/Bustang only
```

## Logging

The pipeline uses SLF4J with Log4j2. Logs are output to console.

**Key Log Messages:**
```
INFO: === TIES GTFS Fare Data Extraction Pipeline Starting ===
INFO: Agency: RTD
INFO: Database: jdbc:postgresql://localhost:5432/ties
INFO: Connecting to TIES PostgreSQL database...
INFO: Connected successfully to TIES database
INFO: Extracting fare media...
INFO: Extracted 3 fare media records for agency RTD
INFO: Extracting fare products...
INFO: Extracted 5 fare product records for agency RTD
...
INFO: === Extraction Summary ===
INFO: Total Records: 1234
INFO: === Pipeline Completed Successfully ===
```

## Error Handling

### Common Errors

**1. Connection Refused**
```
ERROR: Connection refused. Check that the hostname and port are correct
```
**Solution:** Ensure TIES PostgreSQL is running on localhost:5432

**2. Authentication Failed**
```
ERROR: FATAL: password authentication failed for user "ties"
```
**Solution:** Check TIES_DB_PASSWORD environment variable

**3. Table Not Found**
```
ERROR: relation "ties_gtfs_fare_products" does not exist
```
**Solution:** Verify TIES schema is imported into PostgreSQL

**4. Invalid Agency**
```
ERROR: Invalid agency: XYZ. Must be RTD or CDOT
```
**Solution:** Use only RTD or CDOT as agency parameter

## Testing

### Manual Testing

Test database connectivity:
```bash
docker exec ties-postgres psql -U ties -d ties <<EOF
-- Test simple queries
SELECT count(*) FROM ties_gtfs_fare_media;
SELECT count(*) FROM ties_gtfs_fare_products;
SELECT count(*) FROM ties_gtfs_fare_leg_rules;
SELECT count(*) FROM ties_gtfs_fare_transfer_rules;
SELECT count(*) FROM ties_gtfs_areas;
SELECT count(*) FROM ties_stops WHERE gtfs_area_id IS NOT NULL;
EOF
```

Test pipeline execution:
```bash
# Dry run - just check if it compiles
./gradlew build

# Run with minimal output
./gradlew runTIESFarePipeline -Pagency=RTD --quiet

# Run with full debug logging
./gradlew runTIESFarePipeline -Pagency=RTD --debug
```

### Verification

Compare output with Oracle results:
```bash
# If you have Oracle data for comparison
diff data/gtfs-ties/rtd/fare_products.txt oracle_output/fare_products.txt
```

## Performance

### Typical Execution Time

- **RTD:** ~5-10 seconds for small datasets
- **CDOT:** ~3-5 seconds (smaller dataset)

### Resource Usage

- **Memory:** ~512MB heap (configurable via JVM args)
- **CPU:** Single core (parallelism=1)
- **Disk:** Minimal (output files are small)

### Optimization Notes

- Pipeline uses parallelism=1 for data consistency
- For large datasets (>100k records), consider:
  - Increasing JVM heap size
  - Enabling Flink checkpointing interval adjustment
  - Using batch processing mode

## Integration with Existing Pipelines

This pipeline is standalone but can be integrated with existing RTD pipelines:

```bash
# Example: Run TIES extraction, then process with RTD pipeline
./gradlew runTIESFarePipelineRTD && ./gradlew runRTDPipeline
```

## Comparison with Oracle PL/SQL

### Oracle (Old)
```sql
-- Oracle PL/SQL approach
SELECT * FROM TABLE(TIES.ties_gtfs_vw_pkg.get_GTFS_fare_products('RTD'));
SELECT * FROM TABLE(TIES.ties_gtfs_vw_pkg.get_GTFS_fare_media('RTD'));
-- etc...
```

### Java + Flink (New)
```bash
# New approach - single command
./gradlew runTIESFarePipelineRTD
```

**Advantages:**
- ✅ Pure Java - no Oracle dependencies
- ✅ Native ARM64 support (Apple Silicon)
- ✅ PostgreSQL - open source, better performance on Mac
- ✅ Flink streaming - scalable for large datasets
- ✅ Integrated with existing pipeline
- ✅ Version controlled (Java vs PL/SQL in database)

## Troubleshooting

### Check Database Connection

```bash
# Test PostgreSQL connectivity
psql -h localhost -p 5432 -U ties -d ties -c "SELECT version();"

# Verify tables exist
psql -h localhost -p 5432 -U ties -d ties <<EOF
\dt ties_gtfs*
\dt ties_google*
EOF
```

### Check Environment

```bash
# View Java version
java -version  # Should be Java 24

# View Gradle version
./gradlew --version  # Should be 8.14+

# Check if PostgreSQL driver is available
./gradlew dependencies | grep postgresql
```

### Enable Debug Logging

Edit `src/main/resources/log4j2.xml` (if exists) or add JVM arg:
```bash
./gradlew runTIESFarePipeline -Pagency=RTD -Dlog4j.level=DEBUG
```

## Known Limitations

### 1. CSV Headers Not Included

Current implementation doesn't write CSV headers to output files. Headers are defined in model classes but not written to files automatically.

**Workaround:** Add headers manually or via post-processing:
```bash
# Example for fare_products.txt
echo "fare_product_id,fare_product_name,fare_media_id,amount,currency" > header.txt
cat header.txt data/gtfs-ties/rtd/fare_products.txt/part-*.csv > fare_products_final.txt
```

### 2. Simplified Area Queries

The `extractAreas()` and `extractStopAreas()` methods use simplified queries. The full Oracle view logic includes:
- Complex runboard resolution
- Multi-table joins with CTEs
- Route category filtering
- Special trip event exclusions

**Current:** Queries `ties_gtfs_areas` table directly
**Future:** Implement full view logic in SQL or Java

### 3. No Data Validation

Pipeline doesn't validate extracted data. Use existing `RTDFareValidator.java` for validation:
```bash
# TODO: Add validation task
./gradlew validateTIESFareData
```

## Future Enhancements

### Phase 2: Complex View Implementation

Implement full Oracle view logic for areas.txt:
- Create SQL files in `src/main/resources/sql/ties/`
- `ties_google_areas_rtd.sql` - Full RTD area query
- `ties_google_areas_cdot.sql` - Full CDOT area query
- `ties_google_stop_areas_rtd.sql` - Full RTD stop area query
- `ties_google_stop_areas_cdot.sql` - Full CDOT stop area query

### Phase 3: Advanced Features

- [ ] Add CSV header writing
- [ ] Implement data validation
- [ ] Add incremental extraction (track last run)
- [ ] Add error recovery and retry logic
- [ ] Support multiple output formats (JSON, Parquet)
- [ ] Add Kafka sink for streaming updates
- [ ] Create monitoring dashboard

## References

### Documentation
- [GTFS v2 Fare Specification](https://gtfs.org/schedule/reference/)
- [Apache Flink Documentation](https://flink.apache.org/docs/stable/)
- [PostgreSQL JDBC Documentation](https://jdbc.postgresql.org/documentation/)

### Related Files
- `/Users/jamesfeehan/projects/mtram/TIES_GTFS_VW_PKG.sql` - Original Oracle package
- `/Users/jamesfeehan/projects/mtram/POSTGRESQL-CONVERSION-SUMMARY.md` - Database conversion notes
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/PROJECT_ANALYSIS.md` - Project analysis
- `/Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1/FARE_PIPELINE_GUIDE.md` - Implementation guide

## Support

For issues or questions:
1. Check logs in console output
2. Verify database connectivity
3. Review this README
4. Check existing project documentation

---

**Last Updated:** October 22, 2025
**Version:** 1.0.0-MVP
**Status:** ✅ Ready for Testing
