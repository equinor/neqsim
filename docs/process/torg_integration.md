---
title: TORG (Technical Requirements Document) Integration
description: A TORG (Technical Requirements Document, also known as TR or Technical Requirements Governing Document) defines the standards, methods, and requirements to be used in process design for a specific pro...
---

# TORG (Technical Requirements Document) Integration

## Overview

A TORG (Technical Requirements Document, also known as TR or Technical Requirements Governing Document) defines the standards, methods, and requirements to be used in process design for a specific project. NeqSim provides comprehensive support for loading, managing, and applying TORG requirements across process simulations.

## TORG Framework Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              TORG Framework                                   │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                    TechnicalRequirementsDocument                         │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │ │
│  │  │ Project Info │  │ Standards    │  │ Environmental│  │ Safety      │  │ │
│  │  │ - projectId  │  │ - pressure   │  │ Conditions   │  │ Factors     │  │ │
│  │  │ - company    │  │ - separator  │  │ - minTemp    │  │ - pressure  │  │ │
│  │  │ - revision   │  │ - pipeline   │  │ - maxTemp    │  │ - corrosion │  │ │
│  │  └──────────────┘  └──────────────┘  │ - seismic    │  └─────────────┘  │ │
│  │                                      └──────────────┘                    │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                     │                                         │
│                                     ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                           TorgManager                                    │ │
│  │  - load(projectId)           - apply(torg, processSystem)               │ │
│  │  - loadAndApply(id, system)  - applyToEquipment(torg, equipment)        │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                     │                                         │
│          ┌──────────────────────────┼──────────────────────────┐             │
│          ▼                          ▼                          ▼             │
│  ┌───────────────┐         ┌───────────────┐         ┌───────────────┐      │
│  │ CsvTorgData   │         │ DatabaseTorg  │         │ Custom        │      │
│  │ Source        │         │ DataSource    │         │ DataSource    │      │
│  └───────────────┘         └───────────────┘         └───────────────┘      │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

## TechnicalRequirementsDocument Class

The `TechnicalRequirementsDocument` class represents a complete TORG with all project-specific requirements.

### Core Properties

| Property | Description |
|----------|-------------|
| `projectId` | Unique project identifier (e.g., "JOHAN-SVERDRUP-01") |
| `projectName` | Human-readable project name |
| `companyIdentifier` | Company code (e.g., "EQUINOR") |
| `revision` | Document revision (e.g., "Rev 3") |
| `issueDate` | Document issue date |
| `designLifeYears` | Design life in years |

### Nested Classes

#### EnvironmentalConditions

```java
TechnicalRequirementsDocument.EnvironmentalConditions env = torg.getEnvironmentalConditions();

double minAmbient = env.getMinAmbientTemperature();  // °C
double maxAmbient = env.getMaxAmbientTemperature();  // °C
double minSeawater = env.getMinSeawaterTemperature(); // °C
double maxSeawater = env.getMaxSeawaterTemperature(); // °C
String seismicZone = env.getSeismicZone();
String location = env.getLocation();
```

#### SafetyFactors

```java
TechnicalRequirementsDocument.SafetyFactors safety = torg.getSafetyFactors();

double pressureSF = safety.getPressureSafetyFactor();      // e.g., 1.10
double tempMargin = safety.getTemperatureSafetyMargin();   // °C
double corrosion = safety.getCorrosionAllowance();         // mm
double wallTol = safety.getWallThicknessTolerance();       // fraction
double loadFactor = safety.getLoadFactor();                // multiplier
```

#### MaterialSpecifications

```java
TechnicalRequirementsDocument.MaterialSpecifications mats = torg.getMaterialSpecifications();

String plateMaterial = mats.getDefaultPlateMaterial();     // e.g., "SA-516-70"
String pipeMaterial = mats.getDefaultPipeMaterial();       // e.g., "API-5L-X65"
double minDesignTemp = mats.getMinDesignTemperature();     // °C
double maxDesignTemp = mats.getMaxDesignTemperature();     // °C
boolean impactTest = mats.isRequireImpactTesting();
String materialStd = mats.getMaterialStandard();           // e.g., "ASTM"
```

## Creating a TORG Programmatically

Use the Builder pattern for flexible TORG creation:

```java
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

TechnicalRequirementsDocument torg = new TechnicalRequirementsDocument.Builder()
    // Project identification
    .projectId("TROLL-WEST-2025")
    .projectName("Troll West Field Development")
    .companyIdentifier("EQUINOR")
    .revision("Rev 2")
    .issueDate("2025-01-15")
    .designLifeYears(25)
    
    // Add design standards
    .addStandard("pressure_vessel", StandardType.ASME_VIII_DIV1)
    .addStandard("separator_process", StandardType.NORSOK_P002)
    .addStandard("pipeline", StandardType.DNV_OS_F101)
    .addStandard("compressor", StandardType.API_617)
    
    // Environmental conditions
    .environmentalConditions(new TechnicalRequirementsDocument.EnvironmentalConditions(
        -30.0,    // minAmbientTemp °C
        35.0,     // maxAmbientTemp °C
        2.0,      // minSeawaterTemp °C
        20.0,     // maxSeawaterTemp °C
        "Zone 1", // seismicZone
        "Norwegian Sea" // location
    ))
    
    // Safety factors
    .safetyFactors(new TechnicalRequirementsDocument.SafetyFactors(
        1.10,    // pressureSafetyFactor
        25.0,    // temperatureSafetyMargin °C
        3.0,     // corrosionAllowance mm
        0.125,   // wallThicknessTolerance (12.5%)
        1.0      // loadFactor
    ))
    
    // Material specifications
    .materialSpecifications(new TechnicalRequirementsDocument.MaterialSpecifications(
        "SA-516-70",  // defaultPlateMaterial
        "API-5L-X65", // defaultPipeMaterial
        -46.0,        // minDesignTemp °C (for impact testing)
        150.0,        // maxDesignTemp °C
        true,         // requireImpactTesting
        "ASTM"        // materialStandard
    ))
    
    .build();
```

## Loading TORG from Data Sources

### CSV Data Source

Create a CSV file for TORG data:

**File: `torg_projects.csv`**

```csv
project_id,project_name,company,revision,issue_date,design_life_years
TROLL-WEST-2025,Troll West Development,EQUINOR,Rev 2,2025-01-15,25
SNORRE-EXPANSION,Snorre Expansion Project,EQUINOR,Rev 1,2024-06-01,30
```

**File: `torg_standards.csv`**

```csv
project_id,category,standard_code,version,notes
TROLL-WEST-2025,pressure_vessel,ASME-VIII-1,2023,Primary code
TROLL-WEST-2025,separator_process,NORSOK-P002,Rev 3,Process sizing
TROLL-WEST-2025,pipeline,DNV-OS-F101,2021,Subsea pipelines
```

**Loading from CSV:**

```java
import neqsim.process.mechanicaldesign.torg.CsvTorgDataSource;
import neqsim.process.mechanicaldesign.torg.TorgManager;

// Create CSV data source
CsvTorgDataSource csvSource = new CsvTorgDataSource("path/to/torg_projects.csv");

// Create manager and add source
TorgManager manager = new TorgManager();
manager.addDataSource(csvSource);

// Load TORG
Optional<TechnicalRequirementsDocument> optTorg = manager.load("TROLL-WEST-2025");
if (optTorg.isPresent()) {
    TechnicalRequirementsDocument torg = optTorg.get();
    System.out.println("Loaded: " + torg.getProjectName());
}
```

### Database Data Source

Load TORG from the NeqSim database:

```java
import neqsim.process.mechanicaldesign.torg.DatabaseTorgDataSource;

// Create database source
DatabaseTorgDataSource dbSource = new DatabaseTorgDataSource();

// Or with custom connection
DatabaseTorgDataSource dbSource = new DatabaseTorgDataSource(
    "jdbc:derby:neqsimthermodatabase"
);

// Add to manager
TorgManager manager = new TorgManager();
manager.addDataSource(dbSource);

// Load by company and project
Optional<TechnicalRequirementsDocument> torg = 
    manager.load("EQUINOR", "TROLL-WEST-2025");
```

### Database Schema for TORG

```sql
-- Main TORG projects table
CREATE TABLE TORG_Projects (
    PROJECT_ID      VARCHAR(50) PRIMARY KEY,
    PROJECT_NAME    VARCHAR(200),
    COMPANY         VARCHAR(50),
    REVISION        VARCHAR(20),
    ISSUE_DATE      DATE,
    DESIGN_LIFE     INTEGER,
    STATUS          VARCHAR(20)
);

-- Standards mapping table
CREATE TABLE TORG_Standards (
    ID              INTEGER PRIMARY KEY,
    PROJECT_ID      VARCHAR(50) REFERENCES TORG_Projects(PROJECT_ID),
    CATEGORY        VARCHAR(50),
    STANDARD_CODE   VARCHAR(20),
    VERSION         VARCHAR(20),
    NOTES           VARCHAR(500)
);

-- Environmental conditions
CREATE TABLE TORG_Environment (
    PROJECT_ID          VARCHAR(50) PRIMARY KEY REFERENCES TORG_Projects(PROJECT_ID),
    MIN_AMBIENT_TEMP    DOUBLE,
    MAX_AMBIENT_TEMP    DOUBLE,
    MIN_SEAWATER_TEMP   DOUBLE,
    MAX_SEAWATER_TEMP   DOUBLE,
    SEISMIC_ZONE        VARCHAR(20),
    LOCATION            VARCHAR(100)
);
```

## TorgManager

The `TorgManager` orchestrates TORG loading and application:

```java
import neqsim.process.mechanicaldesign.torg.TorgManager;

TorgManager manager = new TorgManager();

// Add multiple data sources (checked in order)
manager.addDataSource(new CsvTorgDataSource("project_torg.csv"));
manager.addDataSource(new DatabaseTorgDataSource());

// Load TORG
Optional<TechnicalRequirementsDocument> optTorg = manager.load("PROJECT-001");

// Get active TORG (most recently loaded)
TechnicalRequirementsDocument activeTorg = manager.getActiveTorg();

// Load and apply in one step
boolean success = manager.loadAndApply("PROJECT-001", processSystem);

// Apply to specific equipment
manager.applyToEquipment(torg, separator);
```

## Applying TORG to Process Systems

### Automatic Application

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.mechanicaldesign.torg.TorgManager;

// Build process system
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.run();

// Load and apply TORG
TorgManager manager = new TorgManager();
manager.addDataSource(new CsvTorgDataSource("project_torg.csv"));

// This applies standards and parameters to all equipment
boolean applied = manager.loadAndApply("TROLL-WEST-2025", process);

if (applied) {
    System.out.println("TORG applied successfully");
}
```

### Manual Application

```java
// Load TORG first
Optional<TechnicalRequirementsDocument> optTorg = manager.load("TROLL-WEST-2025");

if (optTorg.isPresent()) {
    TechnicalRequirementsDocument torg = optTorg.get();
    
    // Apply to entire system
    manager.apply(torg, process);
    
    // Or apply to specific equipment
    manager.applyToEquipment(torg, separator);
    manager.applyToEquipment(torg, compressor);
}
```

## What Gets Applied

When a TORG is applied to equipment, the following are configured:

| Setting | Source | Applied To |
|---------|--------|------------|
| Design standards | `torg.getStandard(category)` | `mechDesign.setDesignStandard()` |
| Pressure safety factor | `safetyFactors.getPressureSafetyFactor()` | `mechDesign.setPressureMarginFactor()` |
| Temperature margin | `safetyFactors.getTemperatureSafetyMargin()` | Design temperature calculation |
| Corrosion allowance | `safetyFactors.getCorrosionAllowance()` | `mechDesign.setCorrosionAllowance()` |
| Material grade | `materialSpecs.getDefaultPlateMaterial()` | `mechDesign.setMaterialDesignStandard()` |
| Design life | `torg.getDesignLifeYears()` | Fatigue and corrosion calculations |

## Generating TORG Summary

```java
// Generate summary of applied TORG
String summary = manager.generateSummary(torg, process);
System.out.println(summary);
```

Output:
```
TORG Summary: TROLL-WEST-2025
=============================
Project: Troll West Development
Company: EQUINOR
Revision: Rev 2
Design Life: 25 years

Applied Standards:
  - pressure_vessel: ASME-VIII-1 (2023)
  - separator_process: NORSOK-P002 (Rev 3)
  - pipeline: DNV-OS-F101 (2021)

Equipment Coverage:
  - HP Separator: Standards applied
  - Export Compressor: Standards applied
  - Subsea Pipeline: Standards applied

Environmental Conditions:
  - Ambient Temperature: -30°C to 35°C
  - Seawater Temperature: 2°C to 20°C
  - Location: Norwegian Sea

Safety Factors:
  - Pressure: 1.10
  - Temperature Margin: 25°C
  - Corrosion Allowance: 3.0 mm
```

## TORG Validation

Validate TORG completeness before applying:

```java
import neqsim.process.mechanicaldesign.torg.TorgValidator;

TorgValidator validator = new TorgValidator();
List<String> issues = validator.validate(torg);

if (!issues.isEmpty()) {
    System.out.println("TORG validation issues:");
    for (String issue : issues) {
        System.out.println("  - " + issue);
    }
}
```

## Best Practices

### 1. One TORG Per Project

Each project should have exactly one TORG that governs all design:

```java
// Good - single source of truth
TorgManager manager = new TorgManager();
manager.loadAndApply("PROJECT-001", process);

// Avoid - multiple conflicting TORGs
// manager.loadAndApply("PROJECT-001-TOPSIDES", process);
// manager.loadAndApply("PROJECT-001-SUBSEA", process);
```

### 2. Version Control TORGs

Track TORG changes with revision numbers:

```java
TechnicalRequirementsDocument torg = new TechnicalRequirementsDocument.Builder()
    .projectId("PROJECT-001")
    .revision("Rev 3")  // Increment for changes
    .issueDate("2025-01-06")
    .build();
```

### 3. Validate Before Production

Always validate TORG against equipment:

```java
// Run validation before final design
boolean isComplete = manager.validateTorgCoverage(torg, process);
if (!isComplete) {
    throw new IllegalStateException("TORG does not cover all equipment types");
}
```

### 4. Document Deviations

Log any deviations from TORG requirements:

```java
// If equipment requires non-standard settings
mechDesign.setDesignStandard(StandardType.ASME_VIII_DIV2);
logger.warn("Deviation from TORG: Using ASME VIII Div 2 instead of Div 1 for {}", 
    equipment.getName());
```

## See Also

- [Mechanical Design Standards](mechanical_design_standards.md) - Standard types and categories
- [Mechanical Design Database](mechanical_design_database.md) - Data source configuration
- [Field Development Orchestration](field_development_orchestration.md) - Complete design workflows
