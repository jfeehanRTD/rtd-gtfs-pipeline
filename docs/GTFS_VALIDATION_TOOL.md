# RTD GTFS Validation Tool

A comprehensive validation tool for all RTD GTFS feeds available at [RTD's GTFS Data Portal](https://www.rtd-denver.com/open-records/open-spatial-information/gtfs).

## Overview

The RTD GTFS Validation Tool downloads and validates all GTFS ZIP files from RTD's website according to the official GTFS specification. It provides detailed validation reports highlighting errors, warnings, and informational messages for each feed.

## Features

### Comprehensive Validation
- **File Presence**: Checks for required and optional GTFS files
- **File Structure**: Validates headers and required fields
- **Data Format**: Validates dates, times, coordinates, URLs, colors, etc.
- **Data Content**: Checks for unique IDs and non-empty required values  
- **Cross-References**: Validates foreign keys between files
- **Geographic Bounds**: RTD-specific service area validation
- **Service Definitions**: Calendar and service exception validation

### Supported Feeds
The tool validates all 8 RTD GTFS feeds:

1. **google_transit** - RTD Main Transit Feed (Combined)
2. **google_transit_flex** - RTD Flex/On-Demand Transit Feed  
3. **bustang-co-us** - Colorado Bustang Intercity Service
4. **commuter_rail** - RTD Direct Operated Commuter Rail
5. **light_rail** - RTD Direct Operated Light Rail
6. **motorbus** - RTD Direct Operated Motor Bus
7. **purchased_motorbus** - RTD Purchased Transportation Motor Bus
8. **purchased_commuter** - RTD Purchased Transportation Commuter Rail

## Installation and Setup

### Prerequisites
- Java 21+ (recommended, minimum Java 11)
- Maven 3.6+ (for building)
- Internet connection (for downloading GTFS feeds)

### Build the Tool
```bash
# Clone and navigate to project
cd rtd-gtfs-pipeline-refArch1

# Build the project
mvn clean package -DskipTests

# Make the script executable
chmod +x gtfs-validator.sh
```

## Usage

### Command Line Interface

The tool provides a convenient shell script wrapper:

```bash
# Show help
./gtfs-validator.sh help

# List all available feeds
./gtfs-validator.sh list

# Validate all RTD GTFS feeds
./gtfs-validator.sh validate

# Validate a specific feed
./gtfs-validator.sh validate google_transit
./gtfs-validator.sh validate light_rail
./gtfs-validator.sh validate bustang-co-us

# Run test suite
./gtfs-validator.sh test

# Force rebuild
./gtfs-validator.sh build
```

### Java Direct Usage

You can also run the validator directly with Java:

```bash
# Using the CLI class
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.tools.GTFSValidationCLI validate google_transit

# Using the validator class directly  
java -cp target/rtd-gtfs-pipeline-1.0-SNAPSHOT.jar \
  com.rtd.pipeline.validation.GTFSZipValidator google_transit
```

### Programmatic Usage

```java
import com.rtd.pipeline.validation.GTFSZipValidator;

// Create validator instance
GTFSZipValidator validator = new GTFSZipValidator();

// Validate all feeds
GTFSZipValidator.ValidationReport overallReport = validator.validateAllFeeds();

// Validate specific feed
GTFSZipValidator.ValidationReport feedReport = validator.validateSingleFeed(
    "google_transit", 
    "https://www.rtd-denver.com/api/download?feedType=gtfs&filename=google_transit.zip"
);

// Check results
if (feedReport.isValid()) {
    System.out.println("✅ Feed is valid!");
} else {
    System.out.println("❌ Feed has " + feedReport.getErrorCount() + " errors");
    feedReport.getErrors().forEach(error -> System.out.println("  " + error));
}

// Generate detailed report
String reportText = feedReport.generateReport();
System.out.println(reportText);
```

## Validation Rules

### File Presence Validation
- Checks for all required GTFS files: `agency.txt`, `stops.txt`, `routes.txt`, `trips.txt`, `stop_times.txt`
- Reports optional files present: `calendar.txt`, `calendar_dates.txt`, `shapes.txt`, etc.
- Flags unknown files not in GTFS specification

### File Structure Validation
- Validates required headers in each file
- Checks data types and formats for each field
- Ensures proper CSV structure and encoding

### Data Format Validation
- **Times**: HH:MM:SS format, allows up to 47:59:59 for overnight service
- **Dates**: YYYYMMDD format with strict date validation  
- **Coordinates**: Latitude (-90 to 90), Longitude (-180 to 180)
- **Colors**: 6-character hex colors (RRGGBB)
- **URLs**: Valid HTTP/HTTPS URLs
- **Route Types**: Valid GTFS route type codes (0-12)
- **Boolean Fields**: 0 or 1 values only

### RTD-Specific Validation
- **Service Area**: Validates coordinates within expanded Denver metro bounds (38.5°-41.0°N, 106.0°-103.5°W)  
- **Route Types**: Validates against expected RTD service types (bus, light rail, commuter rail)
- **Service Coverage**: Checks for reasonable service area coverage percentage

### Cross-Reference Validation
- **Route References**: `trips.txt` route_id → `routes.txt` route_id
- **Stop References**: `stop_times.txt` stop_id → `stops.txt` stop_id  
- **Trip References**: `stop_times.txt` trip_id → `trips.txt` trip_id
- **Service References**: `trips.txt` service_id → `calendar.txt`/`calendar_dates.txt` service_id
- **Shape References**: `trips.txt` shape_id → `shapes.txt` shape_id (optional)

## Output Format

### Validation Report Structure
```
=== Feed Name ===
Status: ✅ VALID / ❌ INVALID
Errors: N
Warnings: N

ERRORS:
  ❌ Error description 1
  ❌ Error description 2

WARNINGS:  
  ⚠️ Warning description 1
  ⚠️ Warning description 2

INFO:
  ℹ️ Informational message 1
  ℹ️ Statistics and counts
```

### Exit Codes
- **0**: Validation passed (all feeds valid)
- **1**: Validation failed (errors found) or tool error

### Report Types
- **Errors (❌)**: GTFS specification violations that must be fixed
- **Warnings (⚠️)**: Potential issues or RTD-specific concerns  
- **Info (ℹ️)**: Statistics, counts, and informational messages

## Integration

### RTD Control Script Integration
The validator integrates with the existing RTD control script:

```bash
# Add to rtd-control.sh
./rtd-control.sh gtfs validate           # Validate all feeds
./rtd-control.sh gtfs validate light_rail # Validate specific feed
./rtd-control.sh gtfs list               # List feeds
```

### CI/CD Integration
The tool is designed for continuous integration:

```yaml
# Example GitHub Actions workflow
- name: Validate GTFS Feeds
  run: |
    ./gtfs-validator.sh validate
    if [ $? -ne 0 ]; then
      echo "GTFS validation failed"
      exit 1
    fi
```

### Maven Integration
Run validation tests as part of the build:

```bash
mvn test -Dtest=GTFSZipValidatorTest
```

## Performance

### Benchmarks (Approximate)
- **Single Feed Validation**: 10-60 seconds (depending on feed size)
- **All Feeds Validation**: 5-15 minutes total
- **Memory Usage**: ~2GB recommended (configurable via JAVA_OPTS)
- **Network**: Downloads 50-200MB total across all feeds

### Optimization Features
- Efficient ZIP stream processing (no temp files)
- Configurable memory settings
- Parallel validation potential for multiple feeds
- Sample-based validation for very large files (configurable)

## Troubleshooting

### Common Issues

**Java Version Error**
```bash
Error: Java version too old
Solution: Install Java 21+ or set JAVA_HOME appropriately
```

**Network Timeouts**
```bash
Error: Connection timeout
Solution: Check internet connection and RTD website availability
```

**Memory Issues**
```bash
Error: OutOfMemoryError
Solution: Increase heap size: export JAVA_OPTS="-Xmx4g"
```

**Build Failures**
```bash
Error: Maven build failed
Solution: Run ./gtfs-validator.sh build to force rebuild
```

### Debug Mode
Enable detailed logging:
```bash
export JAVA_OPTS="$JAVA_OPTS -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG"
./gtfs-validator.sh validate google_transit
```

## Architecture

### Core Classes
- **`GTFSZipValidator`**: Main validation engine
- **`GTFSValidationCLI`**: Command-line interface
- **`ValidationReport`**: Report data structure
- **`GTFSFile`**: Parsed GTFS file representation

### Validation Flow
1. **Download**: HTTP with redirect handling
2. **Parse**: ZIP stream processing, CSV parsing
3. **Validate**: Multi-stage validation (structure → content → references → geography)
4. **Report**: Hierarchical error/warning collection

### Error Handling
- Graceful degradation for network issues
- Detailed error messages with context
- Configurable validation strictness
- Comprehensive logging at all levels

## Contributing

### Adding New Validation Rules
1. Add validation method to `GTFSZipValidator`
2. Add corresponding test to `GTFSZipValidatorTest`  
3. Update documentation

### Adding New GTFS Files Support
1. Update `OPTIONAL_FILES` constant
2. Add structure validation method
3. Update cross-reference validation if needed

### Testing
```bash
# Run all validation tests
mvn test -Dtest=GTFSZipValidatorTest

# Run specific test
mvn test -Dtest=GTFSZipValidatorTest#testMainGTFSFeedValidation
```

## References

- [GTFS Static Specification](https://gtfs.org/schedule/)
- [RTD GTFS Data Portal](https://www.rtd-denver.com/open-records/open-spatial-information/gtfs)
- [Google GTFS Validation Tools](https://github.com/google/gtfs-validator)

---

**Generated**: December 2024  
**Version**: 1.0  
**Status**: ✅ Production Ready