# GTFS-RT Testing Guide

## Overview

This guide explains how to test GTFS-RT endpoints using the comprehensive test suite developed for the RTD pipeline. The tests are designed to validate both TIS Producer endpoints and compare them with RTD production endpoints.

## Test Endpoints

### TIS Producer Endpoints (Primary)
- **Vehicle Position**: `http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb`
- **Trip Update**: `http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb`

### RTD Production Endpoints (Comparison)
- **Vehicle Position**: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb`
- **Trip Update**: `https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb`

## Test Scripts

### 1. Comprehensive Test Script
```bash
./scripts/test-gtfs-rt-endpoints.sh
```

**Features:**
- Endpoint connectivity testing
- Protobuf format validation
- Java-based validation tests
- Custom endpoint tests
- Data quality analysis
- Performance testing
- Detailed reporting

**Options:**
- `--connectivity`: Test endpoint connectivity only
- `--protobuf`: Test protobuf format only
- `--java`: Run Java-based validation tests only
- `--quality`: Test data quality only
- `--all`: Run all tests (default)

### 2. Simple Test Runner
```bash
./scripts/run-gtfs-rt-tests.sh
```

**Features:**
- Basic connectivity testing
- File download validation
- Java test execution
- Quick validation

## Java Test Classes

### 1. TISProducerGTFSRTTest
**Location**: `src/test/java/com/rtd/pipeline/validation/TISProducerGTFSRTTest.java`

**Test Categories:**
- **Connectivity Tests**: Verify endpoints are reachable
- **Data Structure Tests**: Validate protobuf message structure
- **Data Quality Tests**: Analyze data completeness and validity
- **Comparison Tests**: Compare TIS Producer vs RTD Production
- **Performance Tests**: Measure response times

**Key Validations:**
- Coordinate validation (latitude: -90 to 90, longitude: -180 to 180)
- Vehicle ID uniqueness
- Trip and route information completeness
- Stop time update validation
- Timestamp freshness

### 2. Existing Validation Tests
- `VehiclePositionValidationTest`: Validates vehicle position data
- `TripUpdateValidationTest`: Validates trip update data
- `AlertValidationTest`: Validates alert data
- `ComprehensiveValidationTest`: End-to-end validation scenarios
- `GTFSRTEndpointComparisonTest`: Compares different endpoints

## Running Tests

### Prerequisites
1. **Java 17+** installed
2. **Maven** installed
3. **Network access** to TIS Producer endpoints
4. **curl** for basic connectivity testing

### Basic Test Execution
```bash
# Run all tests
./scripts/run-gtfs-rt-tests.sh

# Run comprehensive tests
./scripts/test-gtfs-rt-endpoints.sh

# Run specific test categories
./scripts/test-gtfs-rt-endpoints.sh --connectivity
./scripts/test-gtfs-rt-endpoints.sh --java
./scripts/test-gtfs-rt-endpoints.sh --quality
```

### Maven Test Execution
```bash
# Run all GTFS-RT tests
mvn test -Dtest="*GTFSRT*"

# Run TIS Producer specific tests
mvn test -Dtest="TISProducerGTFSRTTest"

# Run validation tests
mvn test -Dtest="*ValidationTest"

# Run endpoint comparison tests
mvn test -Dtest="*EndpointComparisonTest"
```

## Test Results Interpretation

### Success Indicators
- ✅ **Connectivity**: HTTP 200 responses
- ✅ **File Size**: Non-zero protobuf files
- ✅ **Data Structure**: Valid protobuf parsing
- ✅ **Data Quality**: Valid coordinates, complete information
- ✅ **Performance**: Response times under 10 seconds

### Warning Indicators
- ⚠️ **Empty Feeds**: Zero entities in feed
- ⚠️ **Missing Data**: Incomplete vehicle/trip information
- ⚠️ **Invalid Coordinates**: Out-of-range latitude/longitude
- ⚠️ **Stale Data**: Timestamps older than 24 hours

### Error Indicators
- ❌ **Connection Failed**: Endpoint unreachable
- ❌ **HTTP Errors**: Non-200 status codes
- ❌ **Parse Errors**: Invalid protobuf format
- ❌ **Validation Failures**: Data doesn't meet GTFS-RT spec

## Data Quality Metrics

### Vehicle Position Quality
- **Total Entities**: Number of vehicle positions in feed
- **Valid Coordinates**: Percentage with valid lat/lon
- **Unique Vehicle IDs**: Number of distinct vehicles
- **Unique Trip IDs**: Number of distinct trips
- **Unique Route IDs**: Number of distinct routes
- **Missing Trip Info**: Entities without trip data
- **Missing Vehicle Info**: Entities without vehicle data

### Trip Update Quality
- **Total Entities**: Number of trip updates in feed
- **Valid Trip Info**: Percentage with complete trip data
- **Stop Time Updates**: Number of stop time updates
- **Unique Stop IDs**: Number of distinct stops
- **Missing Stop Updates**: Entities without stop data

## Performance Benchmarks

### Response Time Targets
- **Connectivity Test**: < 5 seconds
- **File Download**: < 30 seconds
- **Java Test Execution**: < 60 seconds
- **Data Processing**: < 10 seconds per feed

### Data Volume Expectations
- **Vehicle Position**: 100-1000+ vehicles
- **Trip Update**: 50-500+ trip updates
- **File Size**: 1KB-1MB per feed

## Troubleshooting

### Common Issues

#### 1. Endpoint Not Reachable
```bash
# Check network connectivity
curl -v http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb

# Check DNS resolution
nslookup tis-producer-d01
```

#### 2. Compilation Errors
```bash
# Clean and recompile
mvn clean compile

# Check Java version
java -version
```

#### 3. Test Failures
```bash
# Run with verbose output
mvn test -Dtest="TISProducerGTFSRTTest" -X

# Check test logs
tail -f target/surefire-reports/TEST-*.txt
```

#### 4. Network Timeouts
```bash
# Increase timeout in test scripts
TIMEOUT_SECONDS=60

# Check firewall settings
telnet tis-producer-d01 8001
```

### Debug Mode
```bash
# Enable debug logging
export LOG_LEVEL=DEBUG

# Run tests with debug output
./scripts/test-gtfs-rt-endpoints.sh --java
```

## Integration with CI/CD

### GitHub Actions Example
```yaml
name: GTFS-RT Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - run: ./scripts/run-gtfs-rt-tests.sh
```

### Jenkins Pipeline Example
```groovy
pipeline {
    agent any
    stages {
        stage('GTFS-RT Tests') {
            steps {
                sh './scripts/run-gtfs-rt-tests.sh'
            }
        }
    }
}
```

## Reporting

### Test Reports
- **Location**: `./gtfs-rt-test-results/`
- **Format**: Text reports with timestamps
- **Content**: Test results, metrics, and recommendations

### Metrics Dashboard
Consider integrating with monitoring tools:
- **Grafana**: Real-time metrics visualization
- **Prometheus**: Time-series data collection
- **AlertManager**: Automated alerting

## Best Practices

### 1. Regular Testing
- Run tests every 15 minutes for production monitoring
- Run comprehensive tests daily
- Monitor for data quality degradation

### 2. Data Validation
- Always validate coordinates
- Check for data freshness
- Monitor entity counts for anomalies

### 3. Performance Monitoring
- Track response times over time
- Monitor file sizes for changes
- Alert on performance degradation

### 4. Documentation
- Keep test results for historical analysis
- Document any endpoint changes
- Maintain troubleshooting guides

## Support

For issues with GTFS-RT testing:
1. Check the troubleshooting section above
2. Review test logs in `target/surefire-reports/`
3. Verify endpoint accessibility
4. Contact the development team with specific error messages
