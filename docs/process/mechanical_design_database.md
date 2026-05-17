---
title: Mechanical Design Database and Data Sources
description: NeqSim supports loading mechanical design parameters from various data sources including databases and CSV files. This allows organizations to maintain centralized repositories of design data, materia...
---

# Mechanical Design Database and Data Sources

## Overview

NeqSim supports loading mechanical design parameters from various data sources including databases and CSV files. This allows organizations to maintain centralized repositories of design data, material properties, and company-specific standards.

## Data Source Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    MechanicalDesignDataSource                    │
│                         (Interface)                              │
├─────────────────────────────────────────────────────────────────┤
│                              │                                   │
│         ┌────────────────────┼────────────────────┐             │
│         ▼                    ▼                    ▼             │
│ ┌───────────────┐  ┌─────────────────┐  ┌──────────────────┐   │
│ │ Database      │  │ CSV Data        │  │ Standard-Based   │   │
│ │ DataSource    │  │ Source          │  │ CSV DataSource   │   │
│ └───────────────┘  └─────────────────┘  └──────────────────┘   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## MechanicalDesignDataSource Interface

The core interface for all design data sources:

```java
public interface MechanicalDesignDataSource {
    
    /**
     * Get a design parameter value.
     * @param category Parameter category (e.g., "material", "safety_factor")
     * @param parameterName Parameter name (e.g., "tensile_strength")
     * @param equipmentType Equipment type filter
     * @return Parameter value as double
     */
    double getParameter(String category, String parameterName, String equipmentType);
    
    /**
     * Get a string property.
     */
    String getProperty(String category, String propertyName, String equipmentType);
    
    /**
     * Check if data source is available.
     */
    boolean isAvailable();
    
    /**
     * Get the standard type this data source provides data for.
     */
    StandardType getStandardType();
}
```

## Database Data Source

### Configuration

The `DatabaseMechanicalDesignDataSource` connects to the NeqSim database:

```java
import neqsim.process.mechanicaldesign.data.DatabaseMechanicalDesignDataSource;

// Create database source (uses default neqsim database)
DatabaseMechanicalDesignDataSource dbSource = new DatabaseMechanicalDesignDataSource();

// Or specify connection
DatabaseMechanicalDesignDataSource dbSource = new DatabaseMechanicalDesignDataSource(
    "jdbc:derby:neqsimthermodatabase",
    "TechnicalRequirements_Process"
);
```

### Database Schema

The primary table `TechnicalRequirements_Process` stores design parameters:

```sql
CREATE TABLE TechnicalRequirements_Process (
    ID              INTEGER PRIMARY KEY,
    COMPANY         VARCHAR(50),
    CATEGORY        VARCHAR(100),
    PARAMETER_NAME  VARCHAR(100),
    EQUIPMENT_TYPE  VARCHAR(50),
    VALUE_NUMERIC   DOUBLE,
    VALUE_TEXT      VARCHAR(255),
    UNIT            VARCHAR(20),
    STANDARD_CODE   VARCHAR(20),
    VERSION         VARCHAR(10),
    NOTES           VARCHAR(500)
);

-- Example data
INSERT INTO TechnicalRequirements_Process VALUES
(1, 'Equinor', 'safety_factor', 'pressure_margin', 'separator', 1.10, NULL, '-', 'NORSOK-P002', 'Rev3', NULL),
(2, 'Equinor', 'material', 'min_wall_thickness', 'pressure_vessel', 6.0, NULL, 'mm', 'ASME-VIII-1', '2023', NULL),
(3, 'Equinor', 'sizing', 'liquid_retention_time', 'separator', 180, NULL, 's', 'NORSOK-P002', 'Rev3', 'Minimum 3 minutes');
```

### Querying the Database

```java
// Get numeric parameter
double pressureMargin = dbSource.getParameter("safety_factor", "pressure_margin", "separator");

// Get text property
String material = dbSource.getProperty("material", "default_plate_grade", "pressure_vessel");

// Check availability
if (dbSource.isAvailable()) {
    // Use database source
}
```

## CSV Data Source

### Basic CSV Format

Create CSV files for design parameters:

**File: `designdata/company_standards.csv`**

```csv
category,parameter_name,equipment_type,value_numeric,value_text,unit,standard_code
safety_factor,pressure_margin,separator,1.10,,−,NORSOK-P002
safety_factor,temperature_margin,all,25.0,,C,NORSOK-P001
material,default_plate_grade,pressure_vessel,,SA-516-70,,ASTM-A516
sizing,liquid_retention_time,separator,180.0,,s,NORSOK-P002
sizing,gas_velocity_factor,scrubber,0.07,,-,API-12J
```

### Using CSV Data Source

```java
import neqsim.process.mechanicaldesign.data.StandardBasedCsvDataSource;
import neqsim.process.mechanicaldesign.designstandards.StandardType;

// Load from file path
StandardBasedCsvDataSource csvSource = new StandardBasedCsvDataSource(
    "path/to/company_standards.csv",
    StandardType.NORSOK_P002
);

// Load from classpath resource
StandardBasedCsvDataSource csvSource = new StandardBasedCsvDataSource(
    "designdata/equinor_standards.csv",
    StandardType.NORSOK_P002
);

// Get parameters
double retentionTime = csvSource.getParameter("sizing", "liquid_retention_time", "separator");
```

### Standard-Based CSV Structure

For standards-specific data, use the enhanced format:

**File: `designdata/asme_viii_parameters.csv`**

```csv
standard_code,category,parameter_name,equipment_type,value,unit,version,notes
ASME-VIII-1,joint_efficiency,full_radiograph,all,1.0,-,2023,Category A and B joints
ASME-VIII-1,joint_efficiency,spot_radiograph,all,0.85,-,2023,Category A and B joints
ASME-VIII-1,joint_efficiency,no_radiograph,all,0.70,-,2023,Category A and B joints
ASME-VIII-1,material,min_tensile_strength,SA-516-70,485,MPa,2023,Grade 70
ASME-VIII-1,material,allowable_stress,SA-516-70,138,MPa,2023,At 100°C
```

## Registering Data Sources

### With MechanicalDesign

```java
import neqsim.process.mechanicaldesign.MechanicalDesign;

MechanicalDesign mechDesign = separator.getMechanicalDesign();

// Add database source
mechDesign.addDataSource(new DatabaseMechanicalDesignDataSource());

// Add CSV source
mechDesign.addDataSource(new StandardBasedCsvDataSource(
    "designdata/norsok_p002.csv",
    StandardType.NORSOK_P002
));

// Data sources are queried in order added (first match wins)
```

### With SystemMechanicalDesign

```java
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;

SystemMechanicalDesign sysMech = new SystemMechanicalDesign(process);

// Configure data sources for entire system
sysMech.addDataSource(new DatabaseMechanicalDesignDataSource());
sysMech.addDataSource(new StandardBasedCsvDataSource("company_stds.csv", StandardType.NORSOK_P001));
```

## Default Data Location

NeqSim looks for design data in these locations:

1. **Classpath resources**: `src/main/resources/designdata/`
2. **Working directory**: `./designdata/`
3. **User home**: `~/.neqsim/designdata/`

### Provided Default Files

| File | Description |
|------|-------------|
| `asme_viii_materials.csv` | ASME Section VIII material allowables |
| `norsok_p002_sizing.csv` | NORSOK P-002 sizing parameters |
| `api_617_compressors.csv` | API 617 compressor requirements |
| `dnv_os_f101_pipeline.csv` | DNV pipeline design factors |

## Creating Custom Data Sources

Implement the `MechanicalDesignDataSource` interface:

```java
public class MyCompanyDataSource implements MechanicalDesignDataSource {
    
    private Map<String, Double> parameters = new HashMap<>();
    
    @Override
    public double getParameter(String category, String parameterName, String equipmentType) {
        String key = category + ":" + parameterName + ":" + equipmentType;
        return parameters.getOrDefault(key, Double.NaN);
    }
    
    @Override
    public String getProperty(String category, String propertyName, String equipmentType) {
        // Implementation
        return null;
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public StandardType getStandardType() {
        return StandardType.NORSOK_P001;
    }
}
```

## Data Validation

NeqSim validates data source values:

```java
import neqsim.process.mechanicaldesign.data.DataSourceValidator;

// Validate a data source
DataSourceValidator validator = new DataSourceValidator();
List<String> errors = validator.validate(csvSource);

if (!errors.isEmpty()) {
    for (String error : errors) {
        System.err.println("Validation error: " + error);
    }
}
```

## Best Practices

### 1. Version Control Your Data

Keep CSV files in version control alongside your simulations:

```
project/
├── simulations/
│   └── hp_separator_sizing.java
├── designdata/
│   ├── project_standards.csv
│   └── material_data.csv
└── README.md
```

### 2. Use Standard Codes Consistently

Always reference standards by their `StandardType` code:

```csv
# Good
standard_code,category,parameter
NORSOK-P002,sizing,liquid_retention

# Avoid
standard_code,category,parameter  
NORSOK P-002,sizing,liquid_retention
Norsok-P002,sizing,liquid_retention
```

### 3. Document Units

Always include units in your data:

```csv
parameter_name,value,unit
min_wall_thickness,6.0,mm
design_pressure,50.0,barg
temperature_margin,25.0,C
```

### 4. Layer Data Sources

Use multiple sources with appropriate priority:

```java
// Priority order: company-specific → project-specific → defaults
mechDesign.addDataSource(new CsvDataSource("company_standards.csv"));  // 1st priority
mechDesign.addDataSource(new CsvDataSource("project_overrides.csv"));  // 2nd priority
mechDesign.addDataSource(new DatabaseMechanicalDesignDataSource());    // 3rd priority (fallback)
```

## See Also

- [Mechanical Design Standards](mechanical_design_standards) - Standard types and categories
- [TORG Document Integration](torg_integration) - Project-level requirements
- [Field Development Orchestration](field_development_orchestration) - Complete workflows
