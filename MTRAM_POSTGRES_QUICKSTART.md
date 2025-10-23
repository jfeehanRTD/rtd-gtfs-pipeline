# MTRAM PostgreSQL GTFS Generator - Quick Start Guide

## Overview

This guide shows how to generate complete GTFS feeds directly from your MTRAM PostgreSQL database.

**Why PostgreSQL instead of Oracle?**
- **Cost Savings**: Eliminate $141,950/year in Oracle licensing, DBA, and infrastructure costs
- **Performance**: Direct PostgreSQL queries are faster and more efficient
- **Simplicity**: No need for complex API layers or Oracle PL/SQL
- **Real Data**: Uses your actual MTRAM scheduling database

## Prerequisites

1. **Java 24** installed
2. **Maven 3.6+** installed
3. **MTRAM PostgreSQL database** accessible
4. **Runboard ID** - The ID of the runboard you want to export (e.g., `35628`)

## Quick Start

### Step 1: Build the Project

```bash
# Navigate to project directory
cd /Users/jamesfeehan/demo/rtd-gtfs-pipeline-refArch1

# Clean and package
mvn clean package -DskipTests
```

### Step 2: Set Environment Variables (Recommended)

Instead of passing database credentials on the command line, use environment variables:

```bash
# Linux/Mac
export MTRAM_DB_URL="jdbc:postgresql://localhost:5432/mtram"
export MTRAM_DB_USER="your_username"
export MTRAM_DB_PASSWORD="your_password"
export MTRAM_RUNBOARD_ID="35628"
export GTFS_OUTPUT_DIR="/tmp/gtfs-output"
```

```powershell
# Windows PowerShell
$env:MTRAM_DB_URL = "jdbc:postgresql://localhost:5432/mtram"
$env:MTRAM_DB_USER = "your_username"
$env:MTRAM_DB_PASSWORD = "your_password"
$env:MTRAM_RUNBOARD_ID = "35628"
$env:GTFS_OUTPUT_DIR = "C:\temp\gtfs-output"
```

### Step 3: Run GTFS Generation

**Option A: Using Environment Variables (Secure)**

```bash
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  "$MTRAM_DB_URL" \
  "$MTRAM_DB_USER" \
  "$MTRAM_DB_PASSWORD" \
  "$MTRAM_RUNBOARD_ID" \
  "$GTFS_OUTPUT_DIR"
```

**Option B: Direct Command Line (Less Secure)**

```bash
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  jdbc:postgresql://localhost:5432/mtram \
  username \
  password \
  35628 \
  /tmp/gtfs-output
```

### Step 4: Verify Output

The connector will generate the following GTFS files:

```
/tmp/gtfs-output/
├── agency.txt              # RTD agency information
├── networks.txt            # Transit networks
├── routes.txt              # Bus/rail routes from expmtram_lines
├── calendar.txt            # Service calendars (Weekday, Saturday, Sunday)
├── stops.txt               # Stop locations with coordinates
├── trips.txt               # Trip definitions
├── stop_times.txt          # Stop times for each trip
├── shapes.txt              # Route geometries
├── fare_products.txt       # Fare products (Adult, Youth, Discount, etc.)
├── fare_media.txt          # Payment methods (Cash, MyRide card)
├── fare_leg_rules.txt      # Fare rules by network
└── google_transit.zip      # Complete GTFS ZIP file
```

## What Gets Generated

### Data Sources (MTRAM PostgreSQL Tables)

| GTFS File | MTRAM Table(s) |
|-----------|----------------|
| `routes.txt` | `expmtram_lines` |
| `trips.txt` | `expmtram_trips`, `expmtram_lines` |
| `stops.txt` | `expmtram_shape_stoppoint` |
| `stop_times.txt` | `expmtram_linktimes`, `expmtram_trips` |
| `calendar.txt` | `expmtram_daytype_calendar` |
| `shapes.txt` | `expmtram_shape_linkroute` |

### Calendar Conversion

The connector automatically converts MTRAM daytype names to GTFS calendar entries:

| MTRAM Daytype | GTFS Service | Days |
|---------------|--------------|------|
| `Weekday`, `W-*` | Weekday service | Mon-Fri |
| `Saturday`, `A-*` | Saturday service | Sat only |
| `Sunday`, `U-*` | Sunday service | Sun only |

## Example Output

```bash
$ java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  jdbc:postgresql://localhost:5432/mtram \
  postgres postgres 35628 /tmp/gtfs

2025-01-21 10:00:00 INFO  MTRAMPostgresConnector - Connecting to MTRAM PostgreSQL database...
2025-01-21 10:00:01 INFO  MTRAMPostgresConnector - Connected successfully
2025-01-21 10:00:01 INFO  MTRAMPostgresConnector - Using runboard ID: 35628
2025-01-21 10:00:01 INFO  MTRAMPostgresConnector - Generating GTFS files to: /tmp/gtfs
2025-01-21 10:00:01 INFO  MTRAMPostgresConnector - Generating agency.txt...
2025-01-21 10:00:01 INFO  MTRAMPostgresConnector - Generating networks.txt...
2025-01-21 10:00:02 INFO  MTRAMPostgresConnector - Generating routes.txt...
2025-01-21 10:00:02 INFO  MTRAMPostgresConnector - Generated 125 routes
2025-01-21 10:00:03 INFO  MTRAMPostgresConnector - Generating calendar.txt...
2025-01-21 10:00:03 INFO  MTRAMPostgresConnector - Generated 15 calendar entries
2025-01-21 10:00:04 INFO  MTRAMPostgresConnector - Generating stops.txt...
2025-01-21 10:00:05 INFO  MTRAMPostgresConnector - Generated 8,456 stops
2025-01-21 10:00:06 INFO  MTRAMPostgresConnector - Generating trips.txt...
2025-01-21 10:00:08 INFO  MTRAMPostgresConnector - Generated 12,345 trips
2025-01-21 10:00:09 INFO  MTRAMPostgresConnector - Generating stop_times.txt...
2025-01-21 10:00:15 INFO  MTRAMPostgresConnector - Generated 345,678 stop times
2025-01-21 10:00:16 INFO  MTRAMPostgresConnector - Generating shapes.txt...
2025-01-21 10:00:20 INFO  MTRAMPostgresConnector - Generated 125,000 shape points
2025-01-21 10:00:21 INFO  MTRAMPostgresConnector - Generating fare_products.txt...
2025-01-21 10:00:21 INFO  MTRAMPostgresConnector - Creating GTFS ZIP: /tmp/gtfs/google_transit.zip
2025-01-21 10:00:22 INFO  MTRAMPostgresConnector - ✅ GTFS generation complete!
2025-01-21 10:00:22 INFO  MTRAMPostgresConnector -
Statistics:
  Routes: 125
  Stops: 8,456
  Trips: 12,345
  Stop Times: 345,678
  Shapes: 125,000 points
  Output: /tmp/gtfs/google_transit.zip (12.5 MB)
```

## Finding Your Runboard ID

To find available runboard IDs in your MTRAM database:

```sql
-- Connect to MTRAM PostgreSQL
psql -h localhost -U username -d mtram

-- List all runboards
SELECT runboard_id, description, startdate, enddate
FROM expmtram_runboards
WHERE deleted IS NULL OR deleted = 0
ORDER BY startdate DESC;

-- Example output:
--  runboard_id |     description      | startdate  |  enddate
-- -------------+----------------------+------------+------------
--        35628 | Winter 2025          | 2025-01-01 | 2025-03-31
--        35627 | Fall 2024            | 2024-09-01 | 2024-12-31
--        35626 | Summer 2024          | 2024-06-01 | 2024-08-31
```

## Validation

After generating the GTFS feed, validate it with the GTFS validator:

```bash
# Download GTFS validator (if not already installed)
./gtfs-validator.sh /tmp/gtfs/google_transit.zip
```

Or use the online validator at: https://gtfs-validator.mobilitydata.org/

## Scheduling Automatic Generation

### Daily GTFS Generation (Cron)

```bash
# Add to crontab (crontab -e)
# Generate fresh GTFS every night at midnight
0 0 * * * cd /path/to/rtd-gtfs-pipeline && \
  java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  "$MTRAM_DB_URL" "$MTRAM_DB_USER" "$MTRAM_DB_PASSWORD" \
  "$MTRAM_RUNBOARD_ID" "/var/www/gtfs" && \
  cp /var/www/gtfs/google_transit.zip /var/www/html/api/download/
```

### Systemd Timer (Linux)

```ini
# /etc/systemd/system/gtfs-generator.service
[Unit]
Description=RTD GTFS Generator
After=postgresql.service

[Service]
Type=oneshot
User=gtfs
Environment=MTRAM_DB_URL=jdbc:postgresql://localhost:5432/mtram
Environment=MTRAM_DB_USER=gtfs_user
Environment=MTRAM_DB_PASSWORD=your_password
Environment=MTRAM_RUNBOARD_ID=35628
Environment=GTFS_OUTPUT_DIR=/var/www/gtfs
ExecStart=/usr/bin/java -cp /opt/rtd-gtfs-pipeline/target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  ${MTRAM_DB_URL} ${MTRAM_DB_USER} ${MTRAM_DB_PASSWORD} \
  ${MTRAM_RUNBOARD_ID} ${GTFS_OUTPUT_DIR}
ExecStartPost=/bin/cp /var/www/gtfs/google_transit.zip /var/www/html/api/download/

[Install]
WantedBy=multi-user.target
```

```ini
# /etc/systemd/system/gtfs-generator.timer
[Unit]
Description=Daily GTFS Generation Timer

[Timer]
OnCalendar=daily
Persistent=true

[Install]
WantedBy=timers.target
```

Enable and start:
```bash
sudo systemctl enable gtfs-generator.timer
sudo systemctl start gtfs-generator.timer
sudo systemctl status gtfs-generator.timer
```

## Troubleshooting

### Connection Refused

**Problem**: `Connection refused: connect`

**Solution**: Verify PostgreSQL is running and accessible
```bash
psql -h localhost -U username -d mtram -c "SELECT version();"
```

### Authentication Failed

**Problem**: `FATAL: password authentication failed`

**Solution**: Check credentials and pg_hba.conf
```bash
# Verify user exists
psql -h localhost -U postgres -c "SELECT usename FROM pg_user WHERE usename = 'your_username';"

# Check pg_hba.conf for authentication method
cat /etc/postgresql/*/main/pg_hba.conf | grep -v "^#"
```

### No Data Generated

**Problem**: `Generated 0 routes`

**Solution**: Verify runboard ID exists and has data
```sql
-- Check runboard exists
SELECT * FROM expmtram_runboards WHERE runboard_id = 35628;

-- Check routes for this runboard
SELECT COUNT(*) FROM expmtram_lines WHERE runboard_id = 35628;

-- Check trips for this runboard
SELECT COUNT(*) FROM expmtram_trips WHERE runboard_id = 35628;
```

### Invalid Coordinates

**Problem**: `Invalid WKT geometry`

**Solution**: Check for malformed POINT or LINESTRING data
```sql
-- Find invalid geometries
SELECT unique_code, geom
FROM expmtram_shape_stoppoint
WHERE runboard_id = 35628
  AND (geom IS NULL OR geom NOT LIKE 'POINT%');
```

## Performance Tuning

### Database Optimization

```sql
-- Add indexes for faster queries (if not already present)
CREATE INDEX IF NOT EXISTS idx_lines_runboard ON expmtram_lines(runboard_id);
CREATE INDEX IF NOT EXISTS idx_trips_runboard ON expmtram_trips(runboard_id);
CREATE INDEX IF NOT EXISTS idx_linktimes_runboard ON expmtram_linktimes(runboard_id);
CREATE INDEX IF NOT EXISTS idx_stoppoint_runboard ON expmtram_shape_stoppoint(runboard_id);
CREATE INDEX IF NOT EXISTS idx_linkroute_runboard ON expmtram_shape_linkroute(runboard_id);

-- Analyze tables for query optimization
ANALYZE expmtram_lines;
ANALYZE expmtram_trips;
ANALYZE expmtram_linktimes;
ANALYZE expmtram_shape_stoppoint;
ANALYZE expmtram_shape_linkroute;
```

### Java JVM Options

For large datasets, increase memory allocation:

```bash
java -Xmx4g -Xms2g \
  -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.mtram.MTRAMPostgresConnector \
  "$MTRAM_DB_URL" "$MTRAM_DB_USER" "$MTRAM_DB_PASSWORD" \
  "$MTRAM_RUNBOARD_ID" "$GTFS_OUTPUT_DIR"
```

## Integration with Existing Systems

### Publishing to Public API

After generating the GTFS ZIP, publish it to your public API:

```bash
# Copy to web server
cp /tmp/gtfs/google_transit.zip /var/www/html/api/download/

# Or upload to cloud storage (Azure Blob)
az storage blob upload \
  --account-name rtdgtfs \
  --container-name gtfs \
  --name google_transit.zip \
  --file /tmp/gtfs/google_transit.zip \
  --overwrite
```

### Notification System

Notify consumers when new GTFS is available:

```bash
# Send webhook notification
curl -X POST https://api.example.com/gtfs/updated \
  -H "Content-Type: application/json" \
  -d '{"timestamp":"'$(date -Iseconds)'","version":"'$MTRAM_RUNBOARD_ID'","url":"https://rtd-denver.com/api/download/google_transit.zip"}'
```

## Next Steps

1. **Validate Generated GTFS**: Use GTFS validator to check for errors
2. **Test with Transit Apps**: Import into Google Maps, Transit App, etc.
3. **Schedule Daily Generation**: Set up cron or systemd timer
4. **Monitor Performance**: Track generation time and file size
5. **Automate Publishing**: Deploy to production API endpoint

## Cost Savings Achieved

By using MTRAMPostgresConnector instead of Oracle:

| Cost Item | Oracle (Before) | PostgreSQL (After) | Savings |
|-----------|----------------|-------------------|---------|
| Oracle License | $47,500/year | $0 | $47,500 |
| Oracle Support | $10,450/year | $0 | $10,450 |
| DBA (50% FTE) | $60,000/year | $0 | $60,000 |
| DB Server | $24,000/year | $6,000/year | $18,000 |
| **TOTAL** | **$141,950/year** | **$6,000/year** | **$135,950/year** |

**96% cost reduction!**

## Support

For issues or questions:
- Review documentation in `/docs` directory
- Check MTRAM schema: `~/projects/mtram/migration/schema/01_tables.sql`
- Compare with SQL export: `~/projects/mtram/migration/export_gtfs_complete.sql`

---

**Ready to generate your GTFS feed from MTRAM PostgreSQL!** 🚀
