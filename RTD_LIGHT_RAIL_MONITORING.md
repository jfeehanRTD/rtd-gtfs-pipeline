# RTD Light Rail Service Monitoring

This document explains the RTD Light Rail tracking test that identifies missing trains from GTFS-RT feeds.

## Overview

The `RTDLightRailTrackingTest` provides comprehensive monitoring of RTD's Light Rail system to ensure all expected trains are present in the GTFS-RT vehicle position feeds.

## Key Features

### Complete Route Coverage
- **11 Light Rail Routes**: A, B, C, D, E, F, G, H, N, R, W lines
- **Station Lists**: Complete station sequences for each route
- **Service Frequency**: Time-of-day and day-of-week specific expectations

### Service Analysis
- **Expected vs Actual**: Compares expected train count to actual GTFS-RT feed data
- **Missing Train Detection**: Identifies specific trains missing from feeds
- **Service Completeness**: Calculates percentage coverage by route and overall
- **Disruption Detection**: Automatically identifies service disruptions

### Time-Based Validation
- **Peak Hours** (6-9 AM, 3-7 PM weekdays): High frequency service
- **Mid-Day** (9 AM-3 PM weekdays): Regular service
- **Evenings** (7 PM-midnight): Reduced service  
- **Weekends**: Weekend schedule
- **Late Night** (midnight-6 AM): Limited or no service on some routes

## RTD Light Rail Routes

| Route | Name | Destination | Peak Frequency | Notes |
|-------|------|-------------|----------------|-------|
| A | A-Line | Airport Terminal | 15 min | Airport service |
| B | B-Line | Westminster | 15 min | North suburban |
| C | C-Line | Littleton | 10 min | High frequency downtown |
| D | D-Line | Littleton | 10 min | High frequency downtown |
| E | E-Line | RidgeGate | 10 min | High frequency southeast |
| F | F-Line | 18th & California | 15 min | Limited service hours |
| G | G-Line | Wheat Ridge | 15 min | West suburban |
| H | H-Line | Nine Mile | 30 min | Limited service |
| N | N-Line | Eastlake-124th | 15 min | North suburban |
| R | R-Line | Peoria | 8 min | Union Station shuttle |
| W | W-Line | Jefferson County | 20 min | West suburban |

## Service Expectations

### High Frequency Routes (C, D, E, R)
- **Peak**: 8-15 minute frequency
- **Expected**: 90%+ service completeness
- **Critical**: Downtown corridor backbone

### Airport Route (A)
- **Frequency**: 15-30 minutes depending on time
- **Expected**: 90%+ completeness
- **Critical**: Airport connectivity

### Suburban Routes (B, G, N, W)
- **Frequency**: 15-30 minutes
- **Expected**: 80%+ completeness
- **Service**: May vary by time of day

### Limited Service Routes (F, H)
- **F-Line**: No late night service
- **H-Line**: Very limited service, may not run weekends
- **Expected**: 75%+ when in service

## Test Cases

### 1. Overall Service Tracking
```java
@Test
void allRoutesHaveMinimumService()
```
- Validates 90%+ overall completeness
- Ensures 50%+ per-route minimum
- Generates detailed service report

### 2. Peak Hour Validation
```java
@Test 
void peakHourServiceCoverage()
```
- Expects 95%+ completeness during peak
- High-frequency routes must have 90%+ service
- Validates morning rush (8 AM) service levels

### 3. Weekend Service
```java
@Test
void weekendServiceReflectsSchedule()
```
- Validates 80%+ weekend completeness
- Accounts for reduced weekend schedules
- Identifies routes with no weekend service

### 4. Late Night Service
```java
@Test
void lateNightServiceMatches()
```
- Expects 60%+ completeness (many routes don't run)
- Validates no service on F-Line and H-Line
- Reduced frequency expectations

### 5. Service Disruption Detection
```java
@Test
void serviceDisruptionDetection()
```
- Simulates missing trains from specific routes
- Should detect <80% completeness as disruption  
- Identifies most affected routes

### 6. Individual Route Analysis
```java
@Test
void aLineAirportService()
void downtownCorridorService()
void rLineShuttleService()
```
- Route-specific validation
- 75%+ minimum service levels
- Vehicle position validation

## Sample Output

```
RTD Light Rail Service Analysis
==============================
Overall Completeness: 92.5%

A-Line (A): 4/4 trains (100.0%)
B-Line (B): 3/4 trains (75.0%)
  Missing: B_TRAIN_4
C-Line (C): 6/6 trains (100.0%)
D-Line (D): 6/6 trains (100.0%)
E-Line (E): 8/8 trains (100.0%)
...

Disrupted Service Analysis:
C-Line (C): 0/6 trains (0.0%)
  Missing: C_TRAIN_1, C_TRAIN_2, C_TRAIN_3, C_TRAIN_4, C_TRAIN_5, C_TRAIN_6
```

## Usage

### Run All Light Rail Tests
```bash
mvn test -Dtest=RTDLightRailTrackingTest
```

### Run Specific Test
```bash
mvn test -Dtest=RTDLightRailTrackingTest#allRoutesHaveMinimumService
```

### Integration with CI/CD
The test can be integrated into continuous monitoring:
- **Green**: >90% overall service completeness
- **Yellow**: 80-90% completeness (degraded service)
- **Red**: <80% completeness (service disruption)

## Real-World Application

In a production environment, this test would:

1. **Connect to Live GTFS-RT Feeds**: Pull real-time vehicle positions
2. **Compare to Schedule Data**: Use GTFS static data for expected service
3. **Generate Alerts**: Notify operations of missing trains
4. **Track Metrics**: Historical service reliability data
5. **Dashboard Integration**: Real-time service status displays

## Benefits

- **Proactive Monitoring**: Detect service issues before passenger complaints
- **Data Quality**: Ensure GTFS-RT feeds accurately reflect service
- **Service Reliability**: Track and improve on-time performance
- **Operational Insights**: Identify patterns in service disruptions
- **Customer Communication**: Provide accurate real-time information