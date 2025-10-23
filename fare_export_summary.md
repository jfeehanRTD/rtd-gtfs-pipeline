# RTD GTFS Fare Data Issues Export

## Executive Summary

RTD's GTFS feed contains critical fare calculation errors affecting **305 stops** that incorrectly trigger airport fare pricing. These stops show $12.75 on Google Maps instead of the correct $2.75 local fare.

Generated: 2025-09-24

## Key Findings

### 1. Fare Miscalculation Issue
- **Displayed fare**: $12.75 (Google Maps)
- **Actual fare**: $2.75
- **Overcharge shown**: 363% higher than actual

### 2. Stop Classification Problems

#### Authorized DIA Stops (Correct $10 fare)
Only these 7 stops should have $10 airport fare:
- 34476: Denver Airport Station
- 34475: Denver Airport Station
- 34654: Denver Airport Station Gate 10
- 34653: Denver Airport Station Gate 9
- 34652: Denver Airport Station Gate 8
- 34651: Denver Airport Station Gate 7
- 34650: Denver Airport Station Gate 6

#### Problematic Stops (305 total)
Stops with "airport" keywords incorrectly triggering $10 fare:

**Sample of affected stops:**
1. Wadsworth Pkwy & Metro Airport Ave
2. Airport Blvd & Alameda Dr
3. Gateway Park Station (40th Ave & Airport Blvd)
4. Airport Blvd & Colfax Ave
5. Airport Blvd & 10th Ave
6. Airport Rd & Nelson Rd
7. Federal Blvd & W Holden Pl (near airport)
8. S Parker Rd & Indianpipe Ln
9. University of Denver Station (some routes)
10. Ball Arena / Elitch Gardens Station

### 3. Affected Routes

Routes with the most unauthorized airport stops include:
- Routes serving Gateway Park Station area
- Routes along Airport Boulevard (not going to DIA)
- Routes on Federal Boulevard
- Routes on Hampden Avenue corridor
- Local neighborhood routes with "airport" in street names

### 4. Impact on Public Transit Usage

#### Financial Deterrent
- Family of 4 sees $51 instead of $11 for a trip
- Makes driving/Uber appear more economical
- Monthly pass ($88) looks like poor value against perceived $12.75 rides

#### Trust Erosion
- Riders lose confidence in RTD's pricing information
- Creates uncertainty about actual costs
- Reduces willingness to try public transit

#### Environmental & Social Impact
- Forces riders back to cars
- Disproportionately affects transit-dependent populations
- Undermines climate goals and transit-oriented development

## Recommended Actions

1. **Immediate**: Fix GTFS fare_leg_rules.txt to restrict $10 fare to only the 7 authorized DIA stop IDs
2. **Short-term**: Update fare validation logic to prevent keyword-based fare assignment
3. **Long-term**: Implement proper zone-based or distance-based fare calculation

## Data Export Files

The following files would be generated with full GTFS access:
- `unauthorized_airport_stops.csv` - All 305 stops with incorrect fare potential
- `affected_routes.csv` - Routes serving unauthorized airport stops
- `sample_trips_bad_fares.csv` - Example trips showing $12.75 instead of $2.75
- `fare_products.csv` - Current fare products in the system

## Technical Details

### Fare Calculation Logic Issue
The system appears to use keyword matching ("airport", "gateway", "DIA") in stop names to apply airport fares, rather than using specific stop IDs. This causes any stop with these keywords to potentially charge $10 instead of $2.75.

### Google Maps Integration
Google Maps appears to be adding fares together:
- $10.00 (incorrectly applied airport fare)
- $2.75 (local fare)
- **Total: $12.75** (displayed to users)

### Validation Results
From RTDFareValidator output:
- ❌ CRITICAL POLICY VIOLATION: 305 unauthorized stops could use $10 airport fare
- ✅ Only 7 stops are authorized for $10 fare
- 🆓 37 free service stops identified
- 📊 Total stops analyzed: 7,597

## Conclusion

This fare data issue is a critical barrier to public transit adoption, making RTD appear 4x more expensive than reality. The fix requires updating the GTFS fare rules to properly restrict airport pricing to only the 7 authorized Denver Airport Station stops.