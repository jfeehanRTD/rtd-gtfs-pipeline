# GTFS-RT Data Quality Comparison Report

**Generated**: Thu Aug 21 14:46:54 MDT 2025
**Comparison**: TIS Producer vs RTD Production Endpoints

## Endpoint Information

### TIS Producer Endpoints
- **Vehicle Position**: http://tis-producer-d01:8001/gtfs-rt/VehiclePosition.pb
- **Trip Update**: http://tis-producer-d01:8001/gtfs-rt/TripUpdate.pb
- **Alerts**: http://tis-producer-d01:8001/gtfs-rt/Alerts.pb

### RTD Production Endpoints
- **Vehicle Position**: https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/VehiclePosition.pb
- **Trip Update**: https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/TripUpdate.pb
- **Alerts**: https://nodejs-prod.rtd-denver.com/api/download/gtfs-rt/Alerts.pb

## Summary

This report compares the data quality between TIS Producer and RTD Production GTFS-RT endpoints.

### Key Metrics Compared
- **Data Completeness**: Percentage of required fields present
- **Data Accuracy**: Coordinate validation, timestamp freshness
- **Data Consistency**: Cross-field validation, logical consistency
- **Performance**: Response times, file sizes
- **Coverage**: Number of entities, unique identifiers

### Quality Indicators
- ✅ **Excellent**: > 95% completeness, < 5% errors
- ⚠️ **Good**: 85-95% completeness, < 15% errors
- ❌ **Poor**: < 85% completeness, > 15% errors

## Detailed Analysis

