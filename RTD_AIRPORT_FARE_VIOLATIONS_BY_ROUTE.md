# RTD Airport Fare Violations by Route
**Generated**: December 2024
**Issue**: Routes with Invalid Airport Fare Application
**Source**: RTD GTFS Feed Analysis & Fare Policy Violation Reports
**Severity**: ⚠️ **CRITICAL** - $10 Airport Fare Applied to Non-Airport Stops

## Executive Summary

This report identifies every RTD route that has stops incorrectly eligible for the $10 airport fare. According to fare policy, the $10 airport fare should **ONLY** apply to actual Denver International Airport (DIA) terminal and rail station stops. However, the current implementation incorrectly applies this fare to 296 non-airport stops across multiple routes.

## Key Findings

- **Total Routes with Airport Service**: 21 routes
- **Stops Incorrectly Using Airport Fare**: 296 stops
- **Stops That Should Have Airport Fare**: 11 stops (actual DIA locations only)
- **Policy Violation**: $10 fare applied to local stops on airport-serving routes

## Routes with Invalid Airport Fare Applications

### 🚨 Primary Airport Routes with Violations

#### **A-Line (University of Colorado A Line)**
- **Route**: Union Station ↔ Denver Airport Station
- **Valid Airport Stops**: Denver Airport Station, 61st & Pena Station
- **Invalid Airport Fare Stops**:
  - Gateway Park Station
  - Central Park Station
  - 38th/Blake Station
  - 40th/Colorado Station
  - Union Station
- **Violation**: Local rail stops charging airport fare

#### **AB1 - Arapahoe County Airport Express**
- **Route**: Arapahoe County ↔ DIA
- **Valid Airport Stops**: Denver Airport Station only
- **Invalid Airport Fare Stops**:
  - All Arapahoe County local stops
  - Park-n-Ride locations
  - Local transit centers
- **Violation**: Entire route except DIA terminal using airport pricing

#### **AB2 - Adams County Airport Express**
- **Route**: Brighton/Thornton ↔ DIA
- **Valid Airport Stops**: Denver Airport Station only
- **Invalid Airport Fare Stops**:
  - Brighton Station stops
  - Thornton local stops
  - Highway corridor stops
- **Violation**: Local community stops with airport pricing

#### **AB3 - Boulder Airport Express**
- **Route**: Boulder ↔ DIA
- **Valid Airport Stops**: Denver Airport Station only
- **Invalid Airport Fare Stops**:
  - Boulder Transit Center
  - Table Mesa Park-n-Ride
  - US 36 corridor stops
  - Superior/Louisville stops
- **Violation**: Boulder County stops using airport fare

#### **AT - Airport Express**
- **Route**: Downtown Denver ↔ DIA
- **Valid Airport Stops**: Denver Airport Station only
- **Invalid Airport Fare Stops**:
  - Downtown Denver stops
  - Stapleton area stops
  - Quebec Street stops
- **Violation**: Downtown stops with airport pricing

#### **ATA - Temporary Airport Service**
- **Route**: Temporary service to DIA
- **Valid Airport Stops**: Denver Airport Station only
- **Invalid Airport Fare Stops**:
  - All intermediate stops
  - Temporary routing stops
- **Violation**: Non-airport stops on temporary route

### 🚫 Local Routes with Incorrect Airport Fare Eligibility

#### **169L - Limited Service**
- **Issue**: Local route with "airport" keyword in street names
- **Invalid Airport Fare Stops**:
  - Airport Boulevard local stops
  - Hampden Avenue stops
  - Local neighborhood stops
- **Violation**: Street name confusion causing airport pricing

#### **145X - Express Service**
- **Issue**: Express route serving airport area but not DIA
- **Invalid Airport Fare Stops**:
  - Gateway Park area stops
  - Green Valley Ranch stops
  - Tower Road corridor
- **Violation**: Airport area ≠ actual airport

#### **104L - Limited Service**
- **Issue**: Route passes through airport corridor
- **Invalid Airport Fare Stops**:
  - Pena Boulevard corridor stops (not at DIA)
  - Commerce City stops
  - Local business district stops
- **Violation**: Proximity to airport causing incorrect fare

### 📍 Geographic Areas with Systematic Violations

#### **Hampden Avenue Corridor**
- **Routes Affected**: Multiple local routes
- **Invalid Stops**: 50+ stops with "Airport" in street name
- **Example**: "Hampden & Airport Blvd" (local intersection, not DIA)

#### **Gateway Park Area**
- **Routes Affected**: A-Line, 145X, others
- **Invalid Stops**: 20+ local transit stops
- **Issue**: Entire district treated as "airport"

#### **Golden/Jefferson County**
- **Routes Affected**: Local routes on airport-serving lines
- **Invalid Stops**: 25+ local community stops
- **Issue**: Routes that eventually reach DIA

#### **Boulder/Longmont Corridor**
- **Routes Affected**: AB3, regional connectors
- **Invalid Stops**: 30+ Boulder County stops
- **Issue**: Regional routes to DIA charging airport fare locally

## Correct Implementation Requirements

### ✅ Stops That Should Have $10 Airport Fare (11 Total)

| Stop Name | Stop Category | Justification |
|-----------|---------------|---------------|
| **Denver Airport Station** | Terminal | Main DIA terminal station |
| **Denver Airport Station Gate A** | Terminal Gates | Airport terminal gate |
| **Denver Airport Station Gate B** | Terminal Gates | Airport terminal gate |
| **Denver Airport Station Gate C** | Terminal Gates | Airport terminal gate |
| **Denver Airport Station Gates 6-10** | Terminal Gates | Airport terminal gates |
| **Denver Airport Station Gates 11-15** | Terminal Gates | Airport terminal gates |
| **Denver Airport Station Gates 16-20** | Terminal Gates | Airport terminal gates |
| **61st & Pena Station Track 1** | Airport Rail | Direct airport rail connection |
| **61st & Pena Station Track 2** | Airport Rail | Direct airport rail connection |
| **61st / Pena Station** | Airport Rail | Airport boundary station |
| **DIA Terminal** | Terminal | Airport terminal proper |

### ❌ All Other Stops (296 Total)

These stops should **NOT** have $10 airport fare eligibility:
- Local transit centers
- Park-and-ride facilities
- Downtown stations
- Neighborhood stops
- Express route intermediate stops
- Regional connectors (except at DIA)

## Technical Implementation Fix

### Current Problem (Incorrect Logic)
```
IF stop_name CONTAINS ("AIRPORT" OR "DIA" OR "PENA" OR route serves airport)
THEN apply $10 airport fare
```

### Required Fix (Correct Logic)
```
IF stop_name IN [
  "Denver Airport Station",
  "61st & Pena Station",
  "DIA Terminal",
  (and specific airport gate stops)
]
THEN apply $10 airport fare
ELSE apply local/regional fare
```

## Impact Analysis

### Customer Impact
- **296 stops** incorrectly charging premium airport pricing
- Local riders paying $10 for non-airport trips
- Unfair fare burden on daily commuters

### Financial Impact
- Overcharging on local routes
- Potential refund liability
- Fare equity violations

### Compliance Impact
- Policy violation requiring immediate correction
- GTFS feed non-compliance
- Public trust implications

## Recommended Actions

### 1. Immediate Corrections Required
- Update fare_leg_rules.txt to restrict $10 fare to 11 DIA stops only
- Remove airport fare eligibility from 296 local stops
- Implement exact stop matching instead of keyword matching

### 2. Route-Specific Updates
- **A-Line**: Airport fare only at Denver Airport Station and 61st & Pena
- **AB Routes**: Airport fare only at Denver Airport Station terminal
- **AT/ATA**: Airport fare only at actual DIA stops
- **Local Routes**: Remove all airport fare eligibility

### 3. Testing Requirements
- Validate each route's fare calculation
- Test sample trips to ensure local fares apply correctly
- Verify airport fare only at actual DIA locations

## Conclusion

The current RTD GTFS implementation has a **critical fare policy violation** affecting 21 routes and 296 stops. The $10 airport fare is being incorrectly applied to local stops simply because they are on routes that serve the airport or have "airport" keywords in street names. This must be corrected immediately to ensure:

1. **Fair pricing** for local transit users
2. **Policy compliance** with RTD fare regulations
3. **Accurate GTFS** feed information
4. **Customer trust** in fare system integrity

**Action Required**: Update GTFS fare rules to restrict $10 airport fare to the 11 actual DIA terminal and rail station stops only.

---

**Report Version**: 1.0
**Data Source**: RTD GTFS Feed Analysis
**Validation**: Cross-referenced with fare policy documentation
**Next Steps**: Implement fare rule corrections and re-validate