# GTFS-RT Feed Comparison Guide

This document explains how to compare the newly generated GTFS-RT feeds with the official RTD feeds to validate accuracy and compatibility.

## Overview

The comparison tools analyze the differences between:
- **Official RTD Feeds**: `https://www.rtd-denver.com/files/gtfs-rt/VehiclePosition.pb` and `TripUpdate.pb`
- **Generated Feeds**: Local protobuf files created by our SIRI/RailComm transformation pipeline

## Prerequisites

1. **Generate Local Feeds First**:
   ```bash
   # Start the complete GTFS-RT pipeline
   ./rtd-control.sh gtfs-rt all
   
   # Wait for feeds to be generated (check status)
   ./rtd-control.sh gtfs-rt status
   ```

2. **Network Access**: Internet connection to download official RTD feeds

3. **Required Tools**: Maven, curl (automatically checked by scripts)

## Comparison Methods

### 1. Quick Comparison (Recommended)

```bash
# Using rtd-control.sh (easiest)
./rtd-control.sh gtfs-rt compare

# Or directly with the script
./scripts/compare-gtfs-rt-feeds.sh
```

This performs a comprehensive comparison including:
- Feed structure validation
- Data coverage analysis
- Route and vehicle ID overlap
- Coordinate validation
- Timestamp synchronization check

### 2. Java Test Only

```bash
# Quick test without downloading feeds again
./rtd-control.sh gtfs-rt compare-quick

# Or with the script
./scripts/compare-gtfs-rt-feeds.sh --test-only
```

### 3. Maven Test Suite

```bash
# Run the comprehensive test class
mvn test -Dtest="RTDFeedComparisonTest"
```

### 4. Custom Script Options

```bash
# Skip downloading (use cached RTD feeds)
./scripts/compare-gtfs-rt-feeds.sh --skip-download

# Analyze only protobuf structure
./scripts/compare-gtfs-rt-feeds.sh --structure-only

# Show help
./scripts/compare-gtfs-rt-feeds.sh --help
```

## What Gets Compared

### Vehicle Positions Feed Analysis
- **Count Comparison**: Number of vehicles in each feed
- **Route Coverage**: Which routes are represented
- **Geographic Validation**: Coordinates within Colorado bounds
- **Timestamp Sync**: Feed generation timing
- **Vehicle ID Overlap**: Common vehicles between feeds
- **Data Quality**: Valid vs invalid position data

### Trip Updates Feed Analysis
- **Trip Count**: Number of active trips
- **Route Analysis**: Route coverage comparison
- **Stop Time Updates**: Number and quality of predictions
- **Delay Analysis**: Delay value validation
- **Structure Compatibility**: Feed format consistency

### Coverage Analysis
- **Route Coverage Percentage**: How many RTD routes are covered
- **Vehicle ID Overlap**: Shared vehicles between systems
- **Data Source Distribution**: SIRI vs RailComm coverage
- **Geographic Distribution**: Coverage across Colorado

## Understanding Results

### Success Indicators ✅
- **Route Coverage**: >50% of RTD routes covered
- **Valid Coordinates**: >80% of vehicles have valid Colorado coordinates
- **Timestamp Sync**: Feeds within 5 minutes of each other
- **Structure Compatibility**: Headers and entity structures match
- **Shared Vehicle IDs**: Some vehicles appear in both feeds

### Warning Signs ⚠️
- **Low Coverage**: <50% route coverage (may indicate data source issues)
- **Stale Feeds**: Timestamps more than 10 minutes apart
- **Invalid Coordinates**: High percentage of out-of-bounds coordinates
- **No Vehicle Overlap**: Zero shared vehicle IDs (system mismatch)

### Troubleshooting Common Issues

#### "Local feeds not found"
```bash
# Ensure the GTFS-RT pipeline is running
./rtd-control.sh gtfs-rt status
./rtd-control.sh gtfs-rt all  # If not running
```

#### "Failed to download RTD feeds"
- Check internet connection
- Verify RTD feeds are accessible: `curl -I https://www.rtd-denver.com/files/gtfs-rt/VehiclePosition.pb`
- Try using `--skip-download` with cached files

#### "Low route coverage"
- Check if SIRI and RailComm data sources are active
- Verify Kafka topics have recent data: `./rtd-control.sh bus-comm monitor`
- Review transformation logic for route ID mapping

#### "No vehicle overlap"
- This is normal if RTD's live system uses different vehicle IDs
- Focus on route coverage and geographic distribution instead

## Output Files

Comparison results are saved to:
- **Console Output**: Real-time analysis results
- **Maven Reports**: `target/surefire-reports/` (for test runs)
- **Comparison Report**: `temp/comparison/comparison_report.txt`
- **Downloaded Feeds**: `temp/comparison/` (RTD feeds cached for analysis)

## Integration with Monitoring

Add feed comparison to your monitoring workflow:

```bash
# Daily validation script example
#!/bin/bash
if ./rtd-control.sh gtfs-rt compare-quick; then
    echo "✅ GTFS-RT feeds validated successfully"
else
    echo "❌ GTFS-RT feed validation failed"
    # Send alert, check logs, etc.
fi
```

## Advanced Analysis

### Protobuf Structure Analysis
```bash
# Examine binary structure
xxd -l 256 data/gtfs-rt/VehiclePosition.pb
xxd -l 256 temp/comparison/rtd_vehicle_positions.pb

# Compare with protoc (if available)
protoc --decode=transit_realtime.FeedMessage gtfs-realtime.proto < data/gtfs-rt/VehiclePosition.pb
```

### Route-by-Route Analysis
The Java tests provide detailed route-by-route breakdowns:
- Which routes appear in RTD feeds only
- Which routes appear in local feeds only  
- Which routes appear in both (successful mapping)

### Performance Analysis
The comparison tests measure:
- Feed download time
- Parsing performance
- Data processing speed
- Overall pipeline latency

This helps identify performance bottlenecks and optimization opportunities.

## Best Practices

1. **Run Comparisons Regularly**: Daily or after pipeline changes
2. **Monitor Coverage Trends**: Track route coverage over time
3. **Validate After Changes**: Always compare after transformation logic updates
4. **Archive Results**: Keep comparison reports for trend analysis
5. **Alert on Significant Changes**: Set up monitoring for coverage drops

## Support

If comparison results show significant discrepancies:
1. Check the transformation logic in `SiriToGtfsTransformer.java` and `RailCommToGtfsTransformer.java`
2. Verify data source connectivity and quality
3. Review coordinate validation bounds in `GTFSRTDataProcessor.java`
4. Examine route ID normalization logic

The comparison tools help ensure our generated feeds maintain compatibility with RTD's official GTFS-RT structure while providing the coverage and accuracy needed for transit applications.