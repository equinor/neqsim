---
layout: default
title: Reliability Data Guide
parent: Risk Framework
---

# Equipment Reliability Data Guide

## Overview

NeqSim's risk framework uses equipment reliability data to calculate failure probabilities, availability, and risk metrics. This guide explains:

1. Available built-in data sources
2. CSV format specification
3. How to import your own data (including OREDA)
4. Data source selection guidance

---

## Built-in Data Sources

NeqSim includes **three public domain data sources** that can be freely used:

### 1. IEEE 493 (Gold Book) - `ieee493_equipment.csv`

**Source**: IEEE Std 493-2007 "Recommended Practice for the Design of Reliable Industrial and Commercial Power Systems"

**Scope**: Primarily electrical and utility equipment
- Transformers, circuit breakers, switchgear
- Motors (induction, synchronous, DC)
- Generators, cables, bus ducts
- Relays, inverters, rectifiers
- Pumps, compressors, fans
- Instrumentation and process control

**~100 equipment records**

### 2. IOGP/OGP Data - `iogp_equipment.csv`

**Source**: IOGP Reports 434-series, UK HSE Offshore Statistics, SINTEF summaries

**Scope**: Oil & gas specific equipment and safety systems
- Offshore platforms and wellheads
- Blowout preventers (BOP)
- Hydrocarbon release frequencies
- Fire/explosion ignition probabilities
- Safety systems (ESD, F&G, PSD)
- Subsea equipment
- Pipelines and risers
- Drilling equipment

**~150 equipment records**

### 3. Generic Literature - `generic_literature.csv`

**Source**: Lees' Loss Prevention, CCPS Guidelines, MIL-HDBK-217F, DNV-RP-G101

**Scope**: Comprehensive process equipment coverage
- Process vessels and piping
- All valve types
- Heat exchangers and condensers
- Rotating equipment details
- Instrumentation and sensors
- Electronics and structural components
- HVAC and utilities

**~180 equipment records**

### 4. Representative OREDA Data - `oreda_equipment.csv`

**Source**: Representative values based on OREDA Handbook categories

**Scope**: Offshore equipment reliability
- Compressors, pumps, separators
- Heat exchangers, valves, turbines
- Subsea equipment
- Instrumentation and control

**~120 equipment records**

> **Note**: These are representative values for demonstration. For actual projects, obtain official OREDA data from www.oreda.com

---

## CSV Format Specification

### Required Columns

| Column | Type | Description |
|--------|------|-------------|
| `EquipmentType` | String | General equipment category (e.g., "Pump", "Valve") |
| `EquipmentClass` | String | Specific type/subclass (e.g., "Centrifugal", "Ball") |
| `FailureMode` | String | Failure mode description (e.g., "All modes", "Leak", "Fail to close") |
| `FailureRate` | Double | Failures per hour (e.g., 1.14e-5) |
| `MTBF_hours` | Double | Mean Time Between Failures in hours |
| `MTTR_hours` | Double | Mean Time To Repair in hours |
| `DataSource` | String | Data source identifier (e.g., "OREDA-2015", "IEEE493-2007") |
| `Confidence` | String | Data quality: "High", "Medium", or "Low" |

### Example Records

```csv
EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,MTTR_hours,DataSource,Confidence
Pump,Centrifugal,All modes,1.83e-4,5464,24,OREDA-2015,High
Pump,Centrifugal,Seal failure,5.71e-5,17513,8,CCPS-1989,High
Valve,Ball,Fail to close,2.85e-6,350880,4,OREDA-2015,High
Compressor,Reciprocating,Critical,5.71e-5,17513,120,IEEE493-2007,High
```

### Comments and Headers

- Lines starting with `#` are comments
- The first non-comment line should contain column headers (optional but recommended)

### Units

| Parameter | Unit |
|-----------|------|
| FailureRate | failures per hour |
| MTBF_hours | hours |
| MTTR_hours | hours |

### Relationship

The following relationship should hold:
```
FailureRate ≈ 1 / MTBF_hours
```

---

## Importing Your Own Data

### Method 1: Using OREDADataImporter (Recommended)

```java
import neqsim.process.safety.risk.data.OREDADataImporter;

// Load from custom CSV file
OREDADataImporter importer = new OREDADataImporter();
importer.loadFromCSV("path/to/your/reliability_data.csv");

// Query failure data
double failureRate = importer.getFailureRate("Pump", "Centrifugal", "All modes");
double mtbf = importer.getMTBF("Compressor", "Reciprocating", "Critical");
double mttr = importer.getMTTR("Valve", "Safety/Relief", "Fail to open");

// Get full equipment record
EquipmentReliabilityData data = importer.getEquipmentData("Separator", "Three-phase");
```

### Method 2: Programmatic Data Entry

```java
import neqsim.process.safety.risk.data.OREDADataImporter;

OREDADataImporter importer = new OREDADataImporter();

// Add individual records
importer.addEquipmentData(
    "Pump",                    // EquipmentType
    "Centrifugal",            // EquipmentClass
    "Seal failure",           // FailureMode
    5.71e-5,                  // FailureRate (per hour)
    17513,                    // MTBF (hours)
    8,                        // MTTR (hours)
    "MyCompanyData",          // DataSource
    "High"                    // Confidence
);
```

### Method 3: Using ProcessEquipmentReliability

```java
import neqsim.process.safety.risk.ProcessEquipmentReliability;

// Create reliability data object
ProcessEquipmentReliability reliability = new ProcessEquipmentReliability("HP Pump");
reliability.setFailureRate(1.83e-4);  // failures per hour
reliability.setMTBF(5464);            // hours
reliability.setMTTR(24);              // hours
reliability.setDataSource("OREDA-2015");

// Attach to process equipment
pump.setReliabilityData(reliability);
```

---

## Importing Official OREDA Data

If your organization has access to the official OREDA Handbook, you can import that data:

### Step 1: Create CSV from OREDA Tables

Convert OREDA tables to CSV format:

```csv
# My Company OREDA Data Import
# Source: OREDA Handbook 6th Edition (2015)
# Converted by: [Your Name]
# Date: [Conversion Date]
EquipmentType,EquipmentClass,FailureMode,FailureRate,MTBF_hours,MTTR_hours,DataSource,Confidence
Pump,Centrifugal (single stage),All modes,1.92e-4,5208,26,OREDA-2015-Vol1-Ch4,High
Pump,Centrifugal (single stage),Critical,4.81e-5,20800,52,OREDA-2015-Vol1-Ch4,High
```

### Step 2: Place File in Appropriate Location

```
# For project-specific use
<project>/src/main/resources/reliabilitydata/my_oreda_data.csv

# For system-wide use
${user.home}/.neqsim/reliabilitydata/oreda_data.csv
```

### Step 3: Load Data

```java
// Load official OREDA data
OREDADataImporter importer = new OREDADataImporter();
importer.loadFromCSV("reliabilitydata/my_oreda_data.csv");

// Or load from multiple sources
importer.loadFromCSV("reliabilitydata/oreda_equipment.csv");      // Built-in representative
importer.loadFromCSV("reliabilitydata/my_oreda_data.csv");        // Your official OREDA
// Later loaded data takes precedence for matching equipment
```

### OREDA Data Structure Reference

The official OREDA Handbook organizes data into:

| Volume | Content |
|--------|---------|
| Volume 1 | Topside Equipment (pumps, compressors, valves, etc.) |
| Volume 2 | Subsea Equipment (trees, manifolds, umbilicals, etc.) |

Each equipment entry includes:
- Failure rate (mean, 5th percentile, 95th percentile)
- Active repair time
- Total repair time
- Failure mode breakdown
- Population and operational hours

---

## Data Source Selection Guidance

### Which data source to use?

| Scenario | Recommended Source |
|----------|-------------------|
| Electrical power systems | IEEE 493 |
| Oil & gas offshore topside | OREDA or IOGP |
| Subsea systems | OREDA or IOGP |
| Safety systems (ESD, F&G) | IOGP |
| Process piping and vessels | Generic Literature / CCPS |
| Generic industrial equipment | IEEE 493 + Generic Literature |
| Fire/explosion risk assessment | IOGP |

### Combining Data Sources

```java
// Create combined importer
OREDADataImporter importer = new OREDADataImporter();

// Load in priority order (later files override earlier)
importer.loadFromCSV("reliabilitydata/generic_literature.csv");  // Generic base
importer.loadFromCSV("reliabilitydata/ieee493_equipment.csv");   // Electrical focus
importer.loadFromCSV("reliabilitydata/iogp_equipment.csv");      // O&G specific
importer.loadFromCSV("reliabilitydata/oreda_equipment.csv");     // OREDA data (highest priority)

// Query will return best available data
double pumpFailureRate = importer.getFailureRate("Pump", "Centrifugal", "All modes");
```

---

## Failure Rate Conversions

### Common Conversion Factors

```java
// Failures per year to failures per hour
double failuresPerHour = failuresPerYear / 8760.0;

// Failures per 10^6 hours to failures per hour
double failuresPerHour = failuresPer10e6hours / 1e6;

// MTBF (hours) to failure rate
double failureRate = 1.0 / mtbfHours;

// Availability calculation
double availability = mtbf / (mtbf + mttr);
```

### OREDA Rate Conversion

OREDA reports failure rates per 10^6 hours. To convert:

```java
// OREDA typically reports as "failures per 10^6 hours"
double oredaRate = 183.0;  // From OREDA table
double failuresPerHour = oredaRate * 1e-6;  // = 1.83e-4
```

---

## Data Quality and Confidence

### Confidence Levels

| Level | Description | Typical Use |
|-------|-------------|-------------|
| High | Well-established data from large populations | Final design, risk assessment |
| Medium | Reasonable data but limited population | Preliminary design, screening |
| Low | Expert judgment or sparse data | Conceptual studies only |

### Uncertainty Handling

```java
// OREDA provides uncertainty bounds
// Use mean for expected values
// Use 95th percentile for conservative estimates

double meanRate = importer.getFailureRate("Pump", "Centrifugal", "All modes");
double conservativeRate = meanRate * 3.0;  // Typical factor for 95th percentile
```

---

## API Reference

### OREDADataImporter Class

```java
public class OREDADataImporter {
    // Loading methods
    void loadFromCSV(String filepath);
    void loadFromResource(String resourcePath);
    void addEquipmentData(String type, String class, String mode, 
                         double rate, double mtbf, double mttr,
                         String source, String confidence);
    
    // Query methods
    double getFailureRate(String type, String equipClass, String mode);
    double getMTBF(String type, String equipClass, String mode);
    double getMTTR(String type, String equipClass, String mode);
    String getDataSource(String type, String equipClass, String mode);
    String getConfidence(String type, String equipClass, String mode);
    EquipmentReliabilityData getEquipmentData(String type, String equipClass);
    
    // Listing methods
    List<String> getEquipmentTypes();
    List<String> getEquipmentClasses(String type);
    List<String> getFailureModes(String type, String equipClass);
}
```

---

## Legal Disclaimer

- **IEEE 493**: Based on publicly available standard summaries. For official use, purchase IEEE Std 493-2007.
- **IOGP Data**: Based on publicly available IOGP reports from www.iogp.org.
- **OREDA**: Representative values only. For official OREDA data, membership or purchase is required from www.oreda.com.
- **Generic Literature**: Compiled from various public domain sources cited in the data files.

Users are responsible for ensuring they have appropriate licenses for any proprietary data used in their projects.

---

## References

1. IEEE Std 493-2007, "IEEE Recommended Practice for the Design of Reliable Industrial and Commercial Power Systems (Gold Book)"
2. OREDA Handbook 6th Edition (2015), SINTEF/DNV/OREDA Participants
3. IOGP Report 434-series, "Safety Performance Indicators"
4. CCPS, "Guidelines for Process Equipment Reliability Data" (1989)
5. Lees' Loss Prevention in the Process Industries, 4th Edition (2012)
6. MIL-HDBK-217F, "Reliability Prediction of Electronic Equipment"
7. DNV-RP-G101, "Risk Based Inspection of Offshore Topsides Static Mechanical Equipment"
