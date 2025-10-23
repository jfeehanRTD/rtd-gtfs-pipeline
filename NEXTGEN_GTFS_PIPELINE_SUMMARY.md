# NextGenPipeline GTFS End-to-End Process Summary

## Overview

The NextGenPipeline GTFS extraction process transforms RTD's transit scheduling data from the Oracle TIES database into Google's GTFS (General Transit Feed Specification) format. The pipeline produces a complete, validated GTFS feed with GTFS v2 fare support.

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         SOURCE: Oracle TIES Database                     │
│  - Transit scheduling data                                               │
│  - Routes, trips, stops, calendars, fares                               │
│  - Hosted on Oracle with VPN access required                            │
└─────────────────────┬───────────────────────────────────────────────────┘
                      │
                      │ (Oracle JDBC Connection)
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                 STAGING: PostgreSQL Database (Docker)                    │
│  - PostgreSQL container: ties-postgres (port 5433)                      │
│  - Contains TIES_GOOGLE_*_VW views                                      │
│  - Views transform TIES data into GTFS schema                           │
│  - 16 views mapping to GTFS files                                       │
└─────────────────────┬───────────────────────────────────────────────────┘
                      │
                      │ (PostgreSQL JDBC Connection)
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│            EXTRACTION: NextGenPipelineGTFS Java Application              │
│  - Connects to PostgreSQL via JDBC                                      │
│  - Queries each TIES_GOOGLE_*_VW view                                   │
│  - Writes CSV files in GTFS format                                      │
│  - Creates RTD_NextGen_Pipeline.zip                                     │
└─────────────────────┬───────────────────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    OUTPUT: GTFS Files & Validation                       │
│  - 16 GTFS .txt files                                                   │
│  - RTD_NextGen_Pipeline.zip (6.4 MB)                                    │
│  - Validation: 0 errors, 418 warnings                                   │
│  - Location: data/gtfs-nextgen/rtd/<date>/                              │
└─────────────────────────────────────────────────────────────────────────┘
```

## Step-by-Step Process Flow

### Step 1: Oracle TIES Database (Source)

**Purpose**: Source of truth for RTD transit scheduling data

**Data Content**:
- Route patterns and schedules
- Individual trips and stop times
- Bus stops and stations
- Service calendars and exceptions
- Fare products and rules

**Access**:
- VPN connection required: Palo Alto GlobalProtect
- Oracle JDBC URL: `jdbc:oracle:thin:@//oracle-host:1521/SERVICE`
- Credentials stored in environment variables
- Data accessed via PostgreSQL views (not direct Oracle queries)

**Data Model Features**:
- Complex versioning system (CONTAINER, RUNBOARD_ID, SCENARIO_ID)
- History tables with date partitioning
- Flexible attributes system for custom fields

---

### Step 2: PostgreSQL Staging Layer

**Purpose**: Transform TIES data into GTFS-compatible views

**Setup**:
```bash
docker-compose up -d ties-postgres
# PostgreSQL runs on localhost:5433
# Database: ties
# User: <configured via environment> / Password: <password>
```

**Key Views** (16 total):

#### Core Transit Views
1. **TIES_GOOGLE_AGENCY_VW**
   - Agency information (RTD)
   - Maps to: `agency.txt`

2. **TIES_GOOGLE_ROUTES_VW**
   - Bus routes (1, 15, AB, etc.)
   - Includes route colors, types
   - Maps to: `routes.txt`

3. **TIES_GOOGLE_TRIPS_VW**
   - Individual trip instances
   - Links routes to calendars
   - Maps to: `trips.txt`

4. **TIES_GOOGLE_STOPS_VW**
   - Bus stops and stations
   - GPS coordinates, names
   - Maps to: `stops.txt`

5. **TIES_GOOGLE_STOP_TIMES_VW**
   - Stop times for each trip
   - Arrival/departure times
   - Maps to: `stop_times.txt` (811,206 rows)

6. **TIES_GOOGLE_CALENDAR_VW**
   - Service patterns (weekday, weekend)
   - Maps to: `calendar.txt`

7. **TIES_GOOGLE_CALENDAR_DATES_VW**
   - Service exceptions (holidays)
   - Maps to: `calendar_dates.txt`

8. **TIES_GOOGLE_FEED_INFO_VW**
   - Feed metadata and version
   - Maps to: `feed_info.txt`

#### GTFS v2 Fare Views
9. **TIES_GOOGLE_FARE_MEDIA_VW**
   - Fare payment methods (smart card, cash, app)
   - Maps to: `fare_media.txt` (40 fare media types)

10. **TIES_GOOGLE_FARE_PRODUCTS_VW**
    - Fare products (3-hour pass, day pass, etc.)
    - Prices and currency
    - Maps to: `fare_products.txt` (572 products)

11. **TIES_GOOGLE_FARE_LEG_RULES_VW**
    - Fare rules by network and area
    - **Critical Fix**: Filters airport_day_pass to airport_network only
    - Maps to: `fare_leg_rules.txt` (600 rules)

12. **TIES_GOOGLE_FARE_TRANSFER_RULES_VW**
    - Transfer pricing and time limits
    - Maps to: `fare_transfer_rules.txt` (182 rules)

13. **TIES_GOOGLE_AREAS_VW**
    - Fare zones/areas
    - Maps to: `areas.txt` (81 areas)

14. **TIES_GOOGLE_STOP_AREAS_VW**
    - Links stops to fare areas
    - Maps to: `stop_areas.txt` (184 mappings)

#### Network Views
15. **TIES_GOOGLE_NETWORKS_VW**
    - Route networks (airport_network, standard_fare_network, free_ride_service)
    - Maps to: `networks.txt` (3 networks)

16. **TIES_GOOGLE_ROUTE_NETWORKS_VW**
    - Links routes to networks
    - Maps to: `route_networks.txt` (149 mappings)

**View Logic**:
- Filters to latest version using `MODIFIED_IN_SCENARIO`
- Transforms TIES field names to GTFS field names
- Applies business rules (e.g., fare filtering)
- Handles NULL values and data validation

---

### Step 3: Java Extraction Pipeline

**Application**: `NextGenPipelineGTFS.java`

**Location**:
```
src/main/java/com/rtd/pipeline/NextGenPipelineGTFS.java
```

**Configuration**:

Environment variables:
```bash
POSTGRES_TIES_URL=jdbc:postgresql://localhost:5433/ties
POSTGRES_TIES_USER=ties
POSTGRES_TIES_PASSWORD=<password>
NEXTGEN_OUTPUT_DIR=data/gtfs-nextgen/rtd/2025-10-22
```

**Execution Flow**:

1. **Connect to PostgreSQL**
   ```java
   Connection conn = DriverManager.getConnection(
       POSTGRES_JDBC_URL, POSTGRES_USER, POSTGRES_PASSWORD);
   ```

2. **Extract Core Files** (in order):
   - `extractAgency()` → agency.txt
   - `extractFeedInfo()` → feed_info.txt
   - `extractCalendar()` → calendar.txt
   - `extractCalendarDates()` → calendar_dates.txt
   - `extractRoutes()` → routes.txt
   - `extractTrips()` → trips.txt
   - `extractStops()` → stops.txt
   - `extractStopTimes()` → stop_times.txt (large file, batched)

3. **Extract Fare Files**:
   - `extractFareMedia()` → fare_media.txt
   - `extractFareProducts()` → fare_products.txt
   - `extractFareLegRules()` → fare_leg_rules.txt
   - `extractFareTransferRules()` → fare_transfer_rules.txt
   - `extractAreas()` → areas.txt
   - `extractStopAreas()` → stop_areas.txt

4. **Extract Network Files**:
   - `extractNetworks()` → networks.txt
   - `extractRouteNetworks()` → route_networks.txt

5. **Create ZIP File**:
   ```java
   createGTFSZip(outputDir);
   // Creates: RTD_NextGen_Pipeline.zip
   ```

**Extraction Pattern** (per file):
```java
private static void extractAgency(Connection conn) throws Exception {
    String sql = "SELECT * FROM ties_google_agency_vw";
    String file = OUTPUT_DIR + "/agency.txt";

    try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql);
         PrintWriter writer = new PrintWriter(new FileWriter(file))) {

        // Write header
        writer.println("agency_id,agency_name,agency_url,...");

        // Write rows
        while (rs.next()) {
            writer.println(String.format("%s,%s,%s,...",
                csvEscape(rs.getString("agency_id")),
                csvEscape(rs.getString("agency_name")),
                ...));
        }
    }
}
```

**Key Features**:
- CSV escaping for special characters (commas, quotes, newlines)
- Large file handling with fetch batching (10,000 rows)
- Progress logging every 100,000 rows for stop_times
- Error handling with detailed logging

---

### Step 4: Generated GTFS Output

**Output Directory**:
```
data/gtfs-nextgen/rtd/2025-10-22/
```

**Generated Files** (16 files):

| File | Rows | Size | Description |
|------|------|------|-------------|
| agency.txt | 1 | 164B | Agency info (RTD) |
| feed_info.txt | 1 | 165B | Feed metadata |
| routes.txt | 149 | 16KB | Bus routes |
| trips.txt | 22,039 | 927KB | Trip instances |
| stops.txt | 136 | 9.5KB | Bus stops |
| stop_times.txt | 811,206 | 31MB | Stop times (largest file) |
| calendar.txt | 5 | 258B | Service patterns |
| calendar_dates.txt | 450 | 5.7KB | Service exceptions |
| fare_media.txt | 40 | 966B | Payment methods |
| fare_products.txt | 572 | 34KB | Fare products |
| fare_leg_rules.txt | 600 | 37KB | Fare rules by zone |
| fare_transfer_rules.txt | 182 | 7.4KB | Transfer pricing |
| areas.txt | 81 | 2.0KB | Fare zones |
| stop_areas.txt | 184 | 3.8KB | Stop-to-zone mapping |
| networks.txt | 3 | 107B | Route networks |
| route_networks.txt | 149 | 3.8KB | Route network mapping |

**ZIP File**:
- `RTD_NextGen_Pipeline.zip` - 6.4 MB compressed
- Contains all 16 .txt files
- Ready for upload to Google Transit Partner Dashboard

---

### Step 5: GTFS Validation

**Validation Tool**: GTFS-Tools (custom validator)

**Execution**:
```bash
cd ~/projects/GTFS-Tools
java -jar gtfs-validator.jar \
  --input /path/to/RTD_NextGen_Pipeline.zip \
  --output validation-report/
```

**Validation Results**:

✅ **0 Errors** - Feed is fully compliant!

⚠️ **418 Warnings** (informational):
- Missing route descriptions (acceptable)
- Optional fields not provided
- Recommended but not required fields

**Critical Fixes Applied**:
1. **Airport Fare Error** (1,929 → 0 errors)
   - Problem: airport_day_pass appeared in wrong networks
   - Fix: Filtered TIES_GOOGLE_FARE_LEG_RULES_VW to restrict airport_day_pass to airport_network only
   - Result: Perfect validation

2. **Stop Areas Duplication** (2,413 → 1,929 errors)
   - Problem: Duplicate stop-to-area mappings
   - Fix: Added DISTINCT to TIES_GOOGLE_STOP_AREAS_VW
   - Result: Eliminated duplicates

3. **Coordinate Format** (1,139,962 → 2,413 errors)
   - Problem: Coordinates in wrong format
   - Fix: Corrected lat/lon extraction in TIES_GOOGLE_STOPS_VW
   - Result: Valid GPS coordinates

---

## Running the Pipeline

### Prerequisites

1. **VPN Connection** (if accessing Oracle directly)
   ```bash
   # Connect to Palo Alto GlobalProtect VPN
   # Required for Oracle TIES database access
   ```

2. **Start PostgreSQL Container**
   ```bash
   docker-compose up -d ties-postgres

   # Verify connection
   ./gradlew testPostgresConn
   ```

3. **Environment Variables** (optional, uses defaults)
   ```bash
   export POSTGRES_TIES_URL="jdbc:postgresql://localhost:5433/ties"
   export POSTGRES_TIES_USER="ties"
   export POSTGRES_TIES_PASSWORD="<password>"
   export NEXTGEN_OUTPUT_DIR="data/gtfs-nextgen/rtd/$(date +%Y-%m-%d)"
   ```

### Execute Pipeline

**Run the extraction**:
```bash
./gradlew runNextGenCorePipeline
```

**Expected Output**:
```
> Task :runNextGenCorePipeline
=== NextGenPipeline GTFS Core Data Extraction Pipeline Starting ===
Database: jdbc:postgresql://localhost:5433/ties
Output Directory: data/gtfs-nextgen/rtd/2025-10-22
Connected to PostgreSQL TIES database
Extracting agency.txt...
Wrote 1 agencies to agency.txt
Extracting feed_info.txt...
Wrote 1 feed info records to feed_info.txt
Extracting calendar.txt...
Wrote 5 calendar records to calendar.txt
Extracting calendar_dates.txt...
Wrote 450 calendar date records to calendar_dates.txt
Extracting routes.txt...
Wrote 149 routes to routes.txt
Extracting trips.txt...
Wrote 22039 trips to trips.txt
Extracting stops.txt...
Wrote 136 stops to stops.txt
Extracting stop_times.txt...
Processed 100000 stop times...
Processed 200000 stop times...
Processed 300000 stop times...
Processed 400000 stop times...
Processed 500000 stop times...
Processed 600000 stop times...
Processed 700000 stop times...
Processed 800000 stop times...
Wrote 811206 stop times to stop_times.txt
Extracting fare_media.txt...
Wrote 40 fare media to fare_media.txt
Extracting fare_products.txt...
Wrote 572 fare products to fare_products.txt
Extracting fare_leg_rules.txt...
Wrote 600 fare leg rules to fare_leg_rules.txt
Extracting fare_transfer_rules.txt...
Wrote 182 fare transfer rules to fare_transfer_rules.txt
Extracting areas.txt...
Wrote 81 areas to areas.txt
Extracting stop_areas.txt...
Wrote 184 stop areas to stop_areas.txt
Extracting networks.txt...
Wrote 3 networks to networks.txt
Extracting route_networks.txt...
Wrote 149 route networks to route_networks.txt
=== NextGenPipeline GTFS Core Data Extraction Complete ===
Files written to: /path/to/data/gtfs-nextgen/rtd/2025-10-22
Creating GTFS zip file: RTD_NextGen_Pipeline.zip
✅ Successfully created: /path/to/RTD_NextGen_Pipeline.zip
   Size: 6.39 MB

BUILD SUCCESSFUL
```

**Execution Time**: ~3-5 seconds

### Verify Output

```bash
# List generated files
ls -lh data/gtfs-nextgen/rtd/2025-10-22/

# Check zip contents
unzip -l data/gtfs-nextgen/rtd/2025-10-22/RTD_NextGen_Pipeline.zip

# Validate GTFS (optional)
cd ~/projects/GTFS-Tools
java -jar gtfs-validator.jar \
  --input /path/to/RTD_NextGen_Pipeline.zip
```

---

## Data Flow Details

### 1. Oracle TIES → PostgreSQL Views

**Data Synchronization**:
- PostgreSQL views query Oracle TIES data via database link or external tables
- Alternative: Periodic ETL job to copy TIES data to PostgreSQL
- Current setup: Direct query to PostgreSQL staging tables

**View Transformation Example** (TIES_GOOGLE_ROUTES_VW):
```sql
CREATE OR REPLACE VIEW TIES_GOOGLE_ROUTES_VW AS
SELECT
    route_id,           -- TIES: PATTERN_ID
    'RTD' AS agency_id, -- Fixed value
    route_short_name,   -- TIES: PATTERN_NAME
    route_long_name,    -- TIES: PATTERN_DESCRIPTION
    route_type,         -- TIES: MODE (3=bus, 0=tram, 1=metro)
    route_color,        -- TIES: COLOR_CODE
    network_id          -- TIES: NETWORK_TYPE
FROM ties_gtfs_routes  -- Staging table populated from Oracle
ORDER BY route_id;
```

### 2. PostgreSQL Views → CSV Files

**JDBC Query Execution**:
```java
// Execute query
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM ties_google_routes_vw");

// Write header
writer.println("route_id,agency_id,route_short_name,...");

// Write data rows
while (rs.next()) {
    writer.println(String.format("%s,%s,%s,...",
        csvEscape(rs.getString("route_id")),
        csvEscape(rs.getString("agency_id")),
        csvEscape(rs.getString("route_short_name")),
        ...));
}
```

**CSV Escaping Rules**:
```java
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
```

### 3. CSV Files → ZIP Archive

**ZIP Creation**:
```java
ProcessBuilder pb = new ProcessBuilder(
    "zip", "-q", "RTD_NextGen_Pipeline.zip",
    "agency.txt", "routes.txt", "trips.txt", ...);
pb.directory(outputDir);
Process process = pb.start();
```

---

## Key Features & Improvements

### 1. GTFS v2 Fares Support

**What's New**:
- Complete fare system with fare_media, fare_products, fare_leg_rules
- Support for multiple payment methods (smart card, cash, mobile app)
- Zone-based pricing with transfer rules
- Network-based fare filtering

**Benefit**:
- Google Maps can show accurate fare information
- Riders can see trip costs before boarding
- Support for complex fare policies (transfers, day passes, etc.)

### 2. Airport Fare Fix

**Problem**:
- Airport day pass ($10) was showing in wrong networks
- Caused Google Maps to show incorrect $12.75 fares
- 1,929 validation errors

**Solution**:
```sql
-- Filter airport_day_pass to airport_network only
SELECT * FROM ties_gtfs_fare_leg_rules
WHERE NOT (
    fare_product_id = 'airport_day_pass'
    AND network_id != 'airport_network'
)
```

**Result**:
- 0 validation errors
- Correct fares displayed in Google Maps
- Accurate airport service pricing

### 3. Performance Optimizations

**Large File Handling**:
- Batch fetching (10,000 rows at a time) for stop_times.txt
- Progress logging every 100,000 rows
- Stream processing to avoid memory issues

**Database Connection**:
- Single connection reused across all extractions
- Connection pooling not needed (short-lived process)
- Explicit resource cleanup with try-with-resources

### 4. Backward Compatibility

**Legacy Task Support**:
```gradle
// New task (recommended)
task runNextGenCorePipeline { ... }

// Legacy task (deprecated)
task runTIESCorePipeline {
    dependsOn runNextGenCorePipeline
}
```

**Preserved TIES References**:
- Database connection variables: `POSTGRES_TIES_URL`, `POSTGRES_TIES_USER`
- Database view names: `TIES_GOOGLE_*_VW`
- Log messages: "Connected to PostgreSQL TIES database"

---

## Troubleshooting

### Issue 1: Database Connection Failed

**Symptoms**:
```
Error: Could not connect to PostgreSQL database
```

**Solutions**:
```bash
# Check if PostgreSQL is running
docker ps | grep ties-postgres

# Start PostgreSQL if not running
docker-compose up -d ties-postgres

# Test connection
./gradlew testPostgresConn

# Check logs
docker logs ties-postgres
```

### Issue 2: Missing Data in GTFS Files

**Symptoms**:
- Empty or near-empty GTFS files
- Missing routes or trips

**Solutions**:
```bash
# Check PostgreSQL views
psql -h localhost -p 5433 -U ties -d ties
SELECT COUNT(*) FROM ties_google_routes_vw;
SELECT COUNT(*) FROM ties_google_trips_vw;

# Verify TIES data in staging tables
SELECT COUNT(*) FROM ties_gtfs_routes;
SELECT COUNT(*) FROM ties_gtfs_trips;
```

### Issue 3: Validation Errors

**Common Errors**:

1. **Foreign Key Violations**
   - Cause: Orphaned references (e.g., trip references non-existent route)
   - Fix: Update views to include JOIN constraints

2. **Duplicate IDs**
   - Cause: Missing DISTINCT in views
   - Fix: Add DISTINCT to view queries

3. **Invalid Coordinates**
   - Cause: Wrong lat/lon format or swapped coordinates
   - Fix: Verify CAST and column order in TIES_GOOGLE_STOPS_VW

**Validation Command**:
```bash
cd ~/projects/GTFS-Tools
java -jar gtfs-validator.jar \
  --input /path/to/RTD_NextGen_Pipeline.zip \
  --output validation-report/ \
  --verbose
```

### Issue 4: Build Failures

**Symptoms**:
```
Task :compileJava FAILED
```

**Solutions**:
```bash
# Clean build
./gradlew clean

# Rebuild
./gradlew compileJava

# Check Java version (requires Java 17+)
java -version
```

---

## Deployment & Publishing

### Local Testing

1. **Generate GTFS**:
   ```bash
   ./gradlew runNextGenCorePipeline
   ```

2. **Validate**:
   ```bash
   cd ~/projects/GTFS-Tools
   java -jar gtfs-validator.jar \
     --input /path/to/RTD_NextGen_Pipeline.zip
   ```

3. **Manual Testing**:
   - Upload to Google Transit Partner Dashboard (test environment)
   - Verify routes appear correctly in Google Maps
   - Test fare calculations
   - Check stop locations and times

### Production Deployment

1. **Schedule Pipeline**:
   ```bash
   # Cron job (daily at 2 AM)
   0 2 * * * cd /path/to/rtd-gtfs-pipeline-refArch1 && ./gradlew runNextGenCorePipeline
   ```

2. **Upload to Google**:
   - Go to: https://partnerdashboard.google.com/partnerdashboard/transit
   - Select RTD feed
   - Upload RTD_NextGen_Pipeline.zip
   - Wait for validation
   - Publish to production

3. **Monitor**:
   - Check validation reports
   - Monitor Google Maps updates (24-48 hours)
   - Verify rider feedback

---

## Configuration Reference

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| POSTGRES_TIES_URL | jdbc:postgresql://localhost:5433/ties | PostgreSQL connection URL |
| POSTGRES_TIES_USER | ties | PostgreSQL username |
| POSTGRES_TIES_PASSWORD | <password> | PostgreSQL password |
| NEXTGEN_OUTPUT_DIR | data/gtfs-nextgen/rtd/`<date>` | Output directory for GTFS files |

### Gradle Tasks

| Task | Description |
|------|-------------|
| runNextGenCorePipeline | Run GTFS extraction pipeline (recommended) |
| runTIESCorePipeline | Legacy alias for runNextGenCorePipeline (deprecated) |
| compileJava | Compile Java source files |
| testPostgresConn | Test PostgreSQL connection |
| clean | Clean build artifacts |

### Docker Services

| Service | Port | Description |
|---------|------|-------------|
| ties-postgres | 5433 | PostgreSQL database with TIES views |

---

## File Reference

### Source Code

- **Main Pipeline**: `src/main/java/com/rtd/pipeline/NextGenPipelineGTFS.java`
- **Build Config**: `build.gradle`
- **Docker Config**: `docker-compose.yml`

### PostgreSQL Views

- **View Definitions**: `oracle-views/TIES_GOOGLE_*_VW.sql`
- **Location**: PostgreSQL container or database

### Output Files

- **GTFS Files**: `data/gtfs-nextgen/rtd/<date>/*.txt`
- **GTFS ZIP**: `data/gtfs-nextgen/rtd/<date>/RTD_NextGen_Pipeline.zip`

### Documentation

- **This Guide**: `NEXTGEN_GTFS_PIPELINE_SUMMARY.md`
- **Rename Summary**: `data/gtfs-nextgen/rtd/<date>/NEXTGEN_PIPELINE_RENAME_SUMMARY.md`
- **Validation Report**: `GTFS_VALIDATION_REPORT.md`
- **Quick Start**: `QUICK_START.md`

---

## Success Metrics

### Validation Results

✅ **0 Errors** - Perfect GTFS compliance
⚠️ **418 Warnings** - Informational only (optional fields)

### Data Volume

- **Total Files**: 16 GTFS files
- **Total Size**: 6.4 MB compressed
- **Largest File**: stop_times.txt (31 MB uncompressed, 811,206 rows)

### Performance

- **Extraction Time**: ~3-5 seconds
- **Build Time**: <1 second (incremental)
- **Upload Size**: 6.4 MB

### Coverage

- **Routes**: 149 bus routes
- **Trips**: 22,039 scheduled trips
- **Stops**: 136 bus stops
- **Stop Times**: 811,206 scheduled stop times
- **Fare Products**: 572 fare products
- **Fare Rules**: 782 fare rules (leg + transfer)

---

## Next Steps

### Enhancements

1. **Add Real-Time Data**
   - GTFS-RT (real-time) feed generation
   - Vehicle positions from GPS
   - Service alerts and delays

2. **Automate Validation**
   - CI/CD pipeline with automatic validation
   - GitHub Actions workflow
   - Automated Google upload

3. **Additional GTFS Files**
   - shapes.txt (route paths)
   - transfers.txt (walking transfers)
   - frequencies.txt (headway-based service)
   - attributions.txt (data sources)

4. **Dashboard & Monitoring**
   - Feed health dashboard
   - Historical validation tracking
   - Data quality metrics

### Maintenance

1. **Regular Updates**
   - Run pipeline daily or when TIES data changes
   - Monitor validation results
   - Update views as TIES schema evolves

2. **Schema Evolution**
   - Track TIES database changes
   - Update PostgreSQL views accordingly
   - Version control for view definitions

3. **Google Maps Integration**
   - Monitor Google Transit Partner Dashboard
   - Address any Google-reported issues
   - Update feed as GTFS spec evolves

---

## Summary

The NextGenPipeline GTFS extraction process successfully transforms RTD's complex Oracle TIES scheduling data into a clean, validated, Google-compliant GTFS feed. The pipeline:

✅ Extracts 16 GTFS files with complete transit data
✅ Includes GTFS v2 fares with network-based pricing
✅ Achieves 0 validation errors
✅ Generates 6.4 MB compressed feed
✅ Runs in under 5 seconds
✅ Supports backward compatibility
✅ Preserves TIES database references
✅ Provides comprehensive documentation

The feed is production-ready and can be uploaded to Google Transit Partner Dashboard for public consumption in Google Maps.

---

**Pipeline Version**: 1.0.0
**Last Updated**: 2025-10-22
**Validation Status**: ✅ 0 Errors, 418 Warnings
**Feed File**: RTD_NextGen_Pipeline.zip (6.4 MB)
