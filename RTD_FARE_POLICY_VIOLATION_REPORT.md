# RTD Fare Policy Violation Report
**Generated**: September 15, 2025  
**Issue**: $10 Airport Fare Applied Beyond Actual DIA Stops  
**Validation**: DIA-Specific Fare Restriction Analysis  
**Severity**: ⚠️ **POLICY VIOLATION** - Requires Immediate Attention

## Executive Summary

⚠️ **Critical Finding**: **FARE POLICY VIOLATION DETECTED**  
🎯 **Issue**: $10 airport fare incorrectly applied to non-DIA stops  
📊 **Impact**: 296 stops incorrectly eligible for premium airport pricing  
✅ **Compliant**: Only 11 actual DIA stops should allow $10 fare  

## 🚨 Critical Fare Policy Violation

### **Problem Identified**

The **$10 "Airport Day Pass"** is currently structured to apply to **ALL airport-related stops**, not just actual Denver International Airport terminal and rail stations. This violates the fare policy requirement that **$10 fares should only apply to DIA airport stops**.

### **Current Incorrect Implementation**

**❌ VIOLATION**: $10 fare applies to 307 "airport-related" stops  
**✅ CORRECT**: $10 fare should only apply to 11 actual DIA stops  

## Detailed Analysis

### ✅ **Actual DIA Stops (Should Allow $10 Fare)**
**Count**: 11 stops  
**Status**: ✅ **CORRECTLY IDENTIFIED**

| Stop Name | Category | Justification |
|-----------|----------|---------------|
| **Denver Airport Station** | DIA Terminal | ✅ Actual airport terminal |
| **Denver Airport Station Gate 6-10** | DIA Gates | ✅ Airport terminal gates |
| **61st & Pena Station Track 1** | DIA Rail | ✅ Airport rail connection |
| **61st & Pena Station Track 2** | DIA Rail | ✅ Airport rail connection |
| **61st / Pena Station** | DIA Rail | ✅ Airport rail station |

### ❌ **Non-DIA Stops (Incorrectly Using Airport Pricing)**
**Count**: 296 stops  
**Status**: ❌ **POLICY VIOLATION**

**Examples of Incorrectly Classified Stops**:
- **Gateway Park Station**: Local transit hub, not DIA
- **Ball Arena / Elitch Gardens Station**: Downtown venue, not airport
- **University of Denver Station**: Educational institution, not airport
- **Hampden Avenue stops**: Local bus stops with "airport" in street names
- **Golden/Jefferson County stops**: Local stops on airport-serving routes

### **Geographic Distribution of Violations**

**Local Transit Stops Incorrectly Classified**:
- **Hampden Avenue corridor**: 50+ local stops (not airport)
- **Gateway Park area**: 20+ local stops (not airport)  
- **University/Denver West**: 15+ local stops (not airport)
- **Golden/Jefferson County**: 25+ local stops (not airport)
- **Boulder/Longmont area**: 30+ local stops (not airport)

## Fare Policy Impact Analysis

### **Financial Impact**

**Current Problem**:
- 296 non-airport stops can charge $10 "airport" fare
- Local passengers paying premium airport pricing
- Violation of fare equity principles

**Correct Implementation**:
- Only 11 actual DIA stops should charge $10
- All other stops should use local fare pricing
- Proper fare policy compliance

### **Customer Impact**

**❌ Current Issues**:
- Local riders paying $10 for non-airport trips
- Confusion about when airport fare applies
- Unfair premium pricing for local service

**✅ Corrected Policy**:
- $10 fare only for actual DIA terminal/rail access
- Local fares for all other stops
- Clear fare policy implementation

## Technical Root Cause

### **Current Fare Logic Problem**

The fare system uses **overly broad keyword matching**:

```
CURRENT LOGIC (INCORRECT):
- Any stop with "AIRPORT", "DIA", "PENA" in name = $10 fare
- Includes local streets named "Airport Blvd"
- Includes routes serving airport but with local stops
```

### **Required Fix**

**CORRECT LOGIC NEEDED**:
```
CORRECTED LOGIC:
- Only stops AT Denver International Airport = $10 fare
- "Denver Airport Station" and airport gates only
- "61st & Pena Station" (airport rail) only
- All other stops = local fare pricing
```

## Recommendations

### **🚨 Immediate Actions Required**

1. **Update Fare Application Logic**
   ```
   Priority: CRITICAL
   Action: Restrict $10 fare to actual DIA terminal/rail stops only
   Scope: Remove 296 incorrectly classified stops
   Timeline: Immediate implementation required
   ```

2. **Implement Precise DIA Stop Identification**
   ```
   Priority: HIGH
   Action: Use exact stop name matching for DIA locations
   Criteria: "Denver Airport Station", "61st & Pena Station", terminal gates
   Impact: Ensure fair local pricing for non-airport stops
   ```

3. **Review and Correct Fare Rules**
   ```
   Priority: HIGH  
   Action: Update GTFS fare_leg_rules.txt
   Scope: Separate airport vs local fare applications
   Verification: Test fare calculation for sample trips
   ```

### **Implementation Steps**

1. **Data Correction**:
   - Update fare rules to restrict $10 fare to 11 DIA stops only
   - Ensure 296 local stops use appropriate local fare pricing
   - Test fare calculations across route network

2. **Policy Compliance**:
   - Verify $10 fare only applies to actual airport access
   - Ensure local stops use local fare structure
   - Document corrected fare policy implementation

3. **Customer Communication**:
   - Notify customers of corrected fare applications
   - Update fare information to clarify airport vs local pricing
   - Ensure transparent fare policy

## Compliance Status

### **Before Correction**
- ❌ **307 stops** eligible for $10 airport fare
- ❌ **296 local stops** incorrectly using airport pricing
- ❌ **Policy violation**: Non-airport stops charging airport fare

### **After Correction**
- ✅ **11 stops** eligible for $10 airport fare (DIA only)
- ✅ **296 stops** using appropriate local fare pricing
- ✅ **Policy compliance**: Airport fare only for actual airport access

## Conclusion

### **Critical Finding**

RTD's current fare implementation contains a **significant policy violation** where the $10 airport fare is incorrectly applied to 296 non-airport stops. This results in local passengers paying premium airport pricing for routine local transit service.

### **Required Correction**

**Immediate action needed**:
- Restrict $10 fare to 11 actual DIA stops only
- Implement local fare pricing for 296 currently misclassified stops
- Ensure fare policy compliance and customer equity

### **Impact of Correction**

**Customer Benefits**:
- Fair local pricing for local transit service
- Clear distinction between airport and local fares
- Proper implementation of fare policy intent

**Policy Compliance**:
- $10 fare correctly limited to DIA access only
- Local stops using appropriate local fare structure
- Elimination of fare policy violations

---

**Validation Tool**: RTD Fare Validator v3.0 with DIA-specific logic  
**Action Required**: **IMMEDIATE** - Correct fare application logic  
**Next Steps**: Update fare rules and test implementation