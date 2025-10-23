# Context State

This file maintains state information across Claude Code subagents and conversations.

## Current Session State

**Last Updated**: 2025-10-22
**Current Branch**: main
**Dev Branch**: dev_GtfsRtGen

## Recent Actions Completed (2025-10-22)

### NextGenPipeline GTFS Extraction - COMPLETED
- ✅ Created NextGenPipelineGTFS.java - Complete GTFS generator from Oracle TIES database
- ✅ Renamed from TIES to NextGenPipeline branding throughout codebase
- ✅ Fixed all airport fare validation errors (1,929 → 0 errors)
- ✅ Generated RTD_NextGen_Pipeline.zip (6.4 MB) with 16 GTFS files
- ✅ Achieved perfect GTFS validation: 0 errors, 418 warnings
- ✅ Added GTFS v2 fares support (fare_media, fare_products, fare_leg_rules, fare_transfer_rules)
- ✅ Created comprehensive end-to-end documentation
- ✅ Pushed all changes to main branch

## Recently Completed Task (2025-10-22)

**NextGenPipeline GTFS Extraction System - PRODUCTION-READY**

**Objective**: Extract complete GTFS feeds from Oracle TIES database via PostgreSQL staging layer

**What Was Delivered**:

1. **NextGenPipelineGTFS.java** (581 lines)
   - Connects to PostgreSQL database with TIES views
   - Queries 16 TIES_GOOGLE_*_VW views
   - Generates all GTFS v2 files including fares
   - Creates RTD_NextGen_Pipeline.zip ready for Google Maps
   - 3-5 second execution time

2. **Key Features**:
   - GTFS v2 fares with network-based pricing
   - Airport fare filtering (airport_day_pass restricted to airport_network)
   - Large file handling with batching (811,206 stop_times)
   - Automatic ZIP creation
   - CSV escaping for special characters

3. **GTFS Files Generated** (16 files):
   - Core: agency.txt, routes.txt, trips.txt, stops.txt, stop_times.txt
   - Calendar: calendar.txt, calendar_dates.txt
   - Fares: fare_media.txt, fare_products.txt, fare_leg_rules.txt, fare_transfer_rules.txt
   - Areas: areas.txt, stop_areas.txt
   - Networks: networks.txt, route_networks.txt
   - Metadata: feed_info.txt

4. **Database Architecture**:
   - Source: Oracle TIES database (via VPN)
   - Staging: PostgreSQL (Docker container on port 5433)
   - Views: 16 TIES_GOOGLE_*_VW views transforming TIES → GTFS
   - Extraction: Java JDBC direct query

5. **Validation Results**:
   - ✅ 0 Errors - Perfect GTFS compliance
   - ⚠️  418 Warnings - Informational only (optional fields)
   - Airport fare errors fixed (1,929 → 0)
   - Ready for Google Transit Partner Dashboard

6. **Documentation Created**:
   - NEXTGEN_GTFS_PIPELINE_SUMMARY.md - Complete end-to-end guide
   - NEXTGEN_PIPELINE_RENAME_SUMMARY.md - Rename documentation
   - GTFS_VALIDATION_REPORT.md - Validation results
   - QUICK_START.md - Quick start guide
   - Multiple implementation and migration guides

7. **Gradle Tasks**:
   - `./gradlew runNextGenCorePipeline` - Run GTFS extraction
   - `./gradlew runTIESCorePipeline` - Backward compatibility alias (deprecated)
   - `./gradlew testPostgresConn` - Test PostgreSQL connection

**Status**: PRODUCTION-READY - Validated with 0 errors

## Data Flow Architecture

```
Oracle TIES Database (Source)
    ↓ (VPN Required)
PostgreSQL Staging (Docker port 5433)
    ├─ 16 TIES_GOOGLE_*_VW views
    └─ Transforms TIES schema → GTFS format
    ↓ (JDBC)
NextGenPipelineGTFS.java
    ├─ Queries each view
    ├─ Generates CSV files
    └─ Creates RTD_NextGen_Pipeline.zip
    ↓
Output: data/gtfs-nextgen/rtd/2025-10-22/
    └─ RTD_NextGen_Pipeline.zip (6.4 MB)
```

## Key Accomplishments

### Airport Fare Fix
- **Problem**: airport_day_pass ($10) showing in wrong networks
- **Root Cause**: 1,929 validation errors
- **Solution**: Filtered TIES_GOOGLE_FARE_LEG_RULES_VW to restrict airport fares
- **Result**: 0 errors ✅

### Rebranding: TIES → NextGenPipeline
- Renamed all TIES references to NextGenPipeline
- Preserved TIES references for Oracle database source
- Updated environment variables: NEXTGEN_OUTPUT_DIR
- Updated output path: data/gtfs-nextgen/
- Updated zip filename: RTD_NextGen_Pipeline.zip
- Backward compatible: runTIESCorePipeline → runNextGenCorePipeline

### Data Volume
- **Routes**: 149 bus routes
- **Trips**: 22,039 scheduled trips
- **Stops**: 136 bus stops
- **Stop Times**: 811,206 scheduled stop times
- **Fare Products**: 572 fare products
- **Fare Rules**: 782 total (600 leg rules + 182 transfer rules)

## Active Files and Components

### Core Pipeline
- src/main/java/com/rtd/pipeline/NextGenPipelineGTFS.java - Main GTFS extraction
- build.gradle - Gradle configuration with NextGenPipeline tasks
- docker-compose.yml - PostgreSQL database configuration

### PostgreSQL Views (16 views)
- oracle-views/TIES_GOOGLE_AGENCY_VW.sql
- oracle-views/TIES_GOOGLE_ROUTES_VW.sql
- oracle-views/TIES_GOOGLE_TRIPS_VW.sql
- oracle-views/TIES_GOOGLE_STOPS_VW.sql
- oracle-views/TIES_GOOGLE_STOP_TIMES_VW.sql
- oracle-views/TIES_GOOGLE_FEED_INFO_VW.sql
- + 10 more fare and network views

### Output
- data/gtfs-nextgen/rtd/2025-10-22/ - GTFS output directory
- RTD_NextGen_Pipeline.zip - Production GTFS feed

### Documentation
- NEXTGEN_GTFS_PIPELINE_SUMMARY.md - Complete process guide
- GTFS_VALIDATION_REPORT.md - Validation results
- QUICK_START.md - Quick start guide

## Oracle Database and VPN Requirements

**CRITICAL**: Oracle TIES database requires VPN connection, but Claude Code does NOT work when VPN is connected.

### Oracle Connection Details
- **Connection**: Requires VPN access (credentials stored in environment variables)
- **Configuration**: Loaded via `~/ties-oracle-config.sh`
- **JDBC URL**: Configured via environment variables

### PostgreSQL Staging Database
- **Host**: localhost:5433
- **Database**: ties
- **User**: ties
- **Password**: <password>
- **JDBC URL**: jdbc:postgresql://localhost:5433/ties

### VPN Workflow Constraint
- **User must connect to VPN** to access Oracle database
- **Claude Code does NOT work** when VPN is active
- **Workaround**: Provide manual steps for user to run while on VPN

### Migration Steps for User (with VPN)
When Oracle data migration is needed:

1. **Connect to VPN first**
2. **Load Oracle credentials**:
   ```bash
   source ~/ties-oracle-config.sh
   ```
3. **Run migration**:
   ```bash
   ./gradlew runTIESMigrationRTD
   ```
4. **Disconnect from VPN** and report results

## Git Status

**Current Branch**: main
**Last Commit**: 9d9978d4 - "Add NextGenPipeline GTFS extraction with rebranding from TIES"

**Recent Commits**:
- NextGenPipeline GTFS extraction (61 files changed, 850,355+ lines)
- All changes pushed to main and dev_GtfsRtGen branches
- Working directory clean
- Production-ready code deployed

## Project Configuration

### Build System
- **Gradle**: 8.14
- **Java**: 17+
- **Build Command**: `./gradlew compileJava`
- **Run Command**: `./gradlew runNextGenCorePipeline`

### Key Environment Variables
- `POSTGRES_TIES_URL` - PostgreSQL connection (default: jdbc:postgresql://localhost:5433/ties)
- `POSTGRES_TIES_USER` - PostgreSQL user (default: ties)
- `POSTGRES_TIES_PASSWORD` - PostgreSQL password (default: <password>)
- `NEXTGEN_OUTPUT_DIR` - Output directory (default: data/gtfs-nextgen/rtd/<date>)

### Docker Services
- ties-postgres (port 5433) - PostgreSQL database with TIES views

## Next Steps

### Enhancements (Optional)
1. Add GTFS-RT (real-time) feed generation
2. Automate validation in CI/CD pipeline
3. Add additional GTFS files (shapes.txt, transfers.txt)
4. Create feed health dashboard

### Maintenance
1. Run pipeline daily or when TIES data changes
2. Monitor validation results
3. Update views as TIES schema evolves
4. Upload to Google Transit Partner Dashboard

## Notes for Subagents

- **Data Source**: Oracle TIES database only (NO MTRAM)
- **Database**: PostgreSQL staging layer with TIES_GOOGLE_*_VW views
- **Pipeline**: NextGenPipelineGTFS.java generates complete GTFS feed
- **Output**: RTD_NextGen_Pipeline.zip (6.4 MB, 16 files)
- **Validation**: 0 errors, production-ready
- **VPN**: Required for Oracle access, blocks Claude Code access
- Always update this file after successful task completion
- Include relevant file paths, git status, and next steps
