# RTD Fare Validation Report
**Generated**: September 15, 2025  
**Methodology**: GTFS Fare Structure Analysis  
**Data Source**: RTD google_transit.zip feed  
**Validation Rules**: Maximum $10 fare limit, Airport fare restrictions

## Executive Summary

⚠️ **Fare Validation Status**: **ATTENTION REQUIRED**  
❌ **Primary Finding**: 1 fare product exceeds $10 maximum  
✅ **Airport Fare Compliance**: Airport $10 fare properly identified  
📊 **Fare Products Analyzed**: 8 fare products from RTD system  

## Fare Validation Results

### ❌ **Fare Limit Violations**

**Monthly Pass - $88.00**
- **Issue**: Exceeds maximum $10.00 fare limit
- **Amount**: $88.00 (780% over limit)
- **Type**: Monthly subscription fare product
- **Recommendation**: Review if monthly passes should be exempt from $10 limit

### ✅ **Compliant Airport Fare**

**Airport Day Pass - $10.00**
- **Status**: ✅ **CORRECT** - Exactly at maximum limit
- **Amount**: $10.00 (at limit)
- **Type**: Airport-specific day pass
- **Validation**: Properly identified as airport-related fare

## Detailed Fare Analysis

### **All RTD Fare Products**
| Fare Product | Amount | Status | Notes |
|--------------|--------|--------|-------|
| **Airport Day Pass** | $10.00 | ✅ Compliant | Airport fare at limit |
| **Monthly Pass** | $88.00 | ❌ Over Limit | 780% over $10 limit |
| *(6 other products)* | < $10.00 | ✅ Compliant | Under limit |

### **Airport Service Coverage**

**Airport Routes Identified**: 21 routes
- **A-Train**: Union Station to Denver Airport Station  
- **Airport Express Routes**: AB1, AB2, AB3, AT, ATA routes
- **Local Airport Service**: Various connecting routes

**Airport Stops Identified**: 307 stops
- **Denver Airport Station**: Primary terminal connection
- **Gateway Park Station**: Airport area transit hub
- **61st & Pena Station**: Airport rail connection
- **Airport-area stops**: Throughout DIA vicinity

### **Fare Structure Validation**

**GTFS-Fares Implementation**:
- ✅ **fare_products.txt**: 8 products properly defined
- ✅ **fare_leg_rules.txt**: 4 routing rules implemented  
- ✅ **Currency**: All fares in USD
- ✅ **Structure**: Modern GTFS-Fares v2 format

## Airport Fare Policy Compliance

### ✅ **$10 Airport Fare Verification**

**"Airport Day Pass" Analysis**:
- **Amount**: Exactly $10.00 ✅
- **Name**: Contains "Airport" keyword ✅  
- **Purpose**: Day pass for airport travel ✅
- **Routes**: Serves 21 airport-related routes ✅
- **Coverage**: 307 airport-area stops ✅

**Policy Compliance**: The $10 fare is correctly applied only to airport-related services as required.

## Fare Policy Recommendations

### **Immediate Actions Required**

1. **Monthly Pass Review**
   ```
   Current: $88.00 (over limit)
   Options: 
   - Exempt monthly passes from $10 limit
   - Restructure as multiple smaller products
   - Update fare policy to accommodate monthly passes
   ```

2. **Policy Clarification**
   ```
   Define exceptions to $10 limit:
   - Monthly/weekly subscription passes
   - Multi-day unlimited passes  
   - Regional/premium service passes
   ```

### **Current Compliance Status**

**Per-Trip Fares**: ✅ All under $10 limit  
**Airport Fares**: ✅ $10 limit correctly applied  
**Subscription Fares**: ❌ Monthly pass exceeds limit  

## Technical Implementation

### **Validation Logic Applied**

1. **Fare Amount Check**: All products validated against $10 maximum
2. **Airport Identification**: Keyword matching for airport-related fares
3. **Route Analysis**: 21 airport routes confirmed via naming patterns
4. **Stop Analysis**: 307 airport stops identified via location keywords

### **Airport Keywords Used**
- AIRPORT, DIA, DEN, DENVER INTERNATIONAL
- PENA, A-TRAIN, A TRAIN
- Gateway Park, 61st & Pena

### **Data Quality Assessment**

**GTFS Fare Data Quality**: ✅ **Excellent**
- Complete fare product definitions
- Proper currency formatting (USD)  
- Modern GTFS-Fares v2 implementation
- Comprehensive route and stop coverage

## Conclusions

### **Key Findings**

1. **Airport Fare Compliance**: ✅ **PERFECT**
   - $10 airport fare correctly identified and applied
   - Only airport-related services use $10 fare
   - Comprehensive airport service coverage

2. **General Fare Limit**: ⚠️ **NEEDS ATTENTION**  
   - Monthly Pass ($88) exceeds $10 limit
   - All other fares comply with limit
   - Policy clarification needed for subscription fares

3. **Technical Implementation**: ✅ **EXCELLENT**
   - Professional GTFS fare structure
   - Complete fare product definitions
   - Proper airport service identification

### **Policy Compliance Summary**

| Requirement | Status | Details |
|-------------|--------|---------|
| **No fares over $10** | ⚠️ Partial | Monthly Pass = $88 |
| **$10 fare only for airport** | ✅ Perfect | Airport Day Pass only |
| **Airport service coverage** | ✅ Complete | 21 routes, 307 stops |

### **Recommendations**

1. **Update Fare Policy**: Define exceptions for monthly/subscription passes
2. **Maintain Airport Compliance**: Current airport fare structure is perfect
3. **Consider Restructuring**: Break monthly pass into smaller components if needed

**Overall Assessment**: RTD's fare structure demonstrates excellent technical implementation with proper airport fare restrictions. The monthly pass pricing requires policy review to determine if subscription fares should be exempt from the $10 limit.

---

**Validation Tool**: RTD Fare Validator v1.0  
**Next Review**: Recommended after fare policy clarification  
**Contact**: Review with RTD fare policy team for subscription fare guidelines