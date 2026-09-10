---
title: TORG (Technical Requirements Document) Integration
description: Create, load, apply, and inspect project technical requirements using the current NeqSim TORG API and its implementation limits.
---

# TORG (Technical Requirements Document) Integration

## Overview

A Technical Requirements Document (TORG) records project metadata, selected design standards,
environmental conditions, safety factors, and material specifications. NeqSim can load this
configuration and apply a subset to equipment. A populated TORG is not evidence that every
requirement has been enforced or that an equipment design complies with an entire standard.

The complete Java example and CSV on this page are executed by
`MechanicalDesignGuideDocumentationTest`. All project values are synthetic.

## TORG Framework Architecture

| Class | Responsibility |
|-------|----------------|
| `TechnicalRequirementsDocument` | Stores requirements and metadata |
| `CsvTorgDataSource` | Loads one CSV file from a `Path` or classpath resource |
| `DatabaseTorgDataSource` | Reads the configured NeqSim process design database |
| `TorgManager` | Loads documents, applies supported settings, and reports assigned standards |

These classes are in `neqsim.process.mechanicaldesign.torg`.

## TechnicalRequirementsDocument Class

### Core Properties

The builder supports `projectId`, `projectName`, `companyIdentifier`, `revision`, and
`issueDate`. Store additional metadata such as design life with
`customParameter("designLifeYears", 25)`, then retrieve it with
`getCustomParameter("designLifeYears", Integer.class)`. There is no dedicated
`designLifeYears(...)` builder method or `getDesignLifeYears()` getter.

Use `StandardType.getDesignStandardCategory()` when adding category requirements. For example,
the category for `ASME_VIII_DIV1` is `pressure vessel design code`, not `pressure_vessel`.
`getStandardsForCategory(...)` and `getAllApplicableStandards(equipmentType)` return lists.

### Nested Classes

#### EnvironmentalConditions

The constructor takes minimum ambient temperature, maximum ambient temperature, **one** design
seawater temperature (all °C), seismic zone, wind speed (m/s), wave height (m), and location.
Getters include `getMinAmbientTemperature()`, `getMaxAmbientTemperature()`,
`getDesignSeawaterTemperature()`, `getWindSpeed()`, `getWaveHeight()`, `getSeismicZone()`, and
`getLocation()`. The shorter builder overload `environmentalConditions(minC, maxC)` fills
default values for the remaining fields.

TORG environmental temperatures are in degrees Celsius. For equipment with an applicable
design standard, `TorgManager` applies the minimum ambient temperature through
`MechanicalDesign.setMinOperationTemperature(value, "C")`. Mechanical design stores the value
in Kelvin: a TORG minimum of -30 °C gives 243.15 K from `getMinOperationTemperature()` or
`getMinOperationTemperature("K")`, and -30 °C from `getMinOperationTemperature("C")`.

The conversion also applies to `apply(...)`, `loadAndApply(...)`, and the active TORG reapplied
internally by `FieldDevelopmentDesignOrchestrator.runCompleteDesignWorkflow()`. Repeated
application preserves the temperature. No manual temperature correction is required after
application; remove any earlier workaround that repeated the unit-aware setter call.

#### SafetyFactors

`SafetyFactors` takes pressure multiplier, temperature margin (°C), corrosion allowance (mm),
wall-thickness tolerance (fraction), and load multiplier. Getters have the corresponding names
`getPressureSafetyFactor()`, `getTemperatureSafetyMargin()`, `getCorrosionAllowance()`,
`getWallThicknessTolerance()`, and `getLoadFactor()`.

#### MaterialSpecifications

`MaterialSpecifications` takes plate material, pipe material, minimum and maximum design
temperatures (°C), an impact-testing flag, and material standard. The boolean getter is
`isImpactTestingRequired()`. These values are stored as requirements; TORG application does not
select the specified plate or pipe grade in the mechanical calculation.

## Creating a TORG Programmatically

The complete example below uses `TechnicalRequirementsDocument.builder()`. Build the document
with all required values and inspect coverage before applying it. The builder permits an empty
project ID and incomplete requirements, so application validation is necessary.

## Loading TORG from Data Sources

### CSV Data Source

Save this standards-format file as `project_torg.csv`:

```csv
PROJECT_ID,PROJECT_NAME,COMPANY,REVISION,ISSUE_DATE,DESIGN_CATEGORY,STANDARD_CODE
DOCS-001,Demonstration separator,ExampleCo,1,2026-09-10,pressure vessel design code,ASME-VIII-Div1
DOCS-001,Demonstration separator,ExampleCo,1,2026-09-10,separator process design,API-12J
```

Use `new CsvTorgDataSource(Paths.get("project_torg.csv"))` for a file, or
`CsvTorgDataSource.fromResource("designdata/torg/torg_projects.csv")` for the bundled examples.
Rows with the same `PROJECT_ID` are combined. The `DESIGN_CATEGORY` column selects this format;
unknown standard codes are logged and skipped.

The alternate master format has one row per project without `DESIGN_CATEGORY`. It can load
metadata, environmental conditions, safety factors, and materials, but does not add standard
assignments. Separate CSV files are not automatically joined. The standards format does not
load safety factors or materials; construct a document programmatically when both are needed.
`VERSION` and `PRIORITY` columns do not select calculation editions in the CSV reader.

### Database Data Source

`new DatabaseTorgDataSource()` reads TORG tables through `NeqSimProcessDesignDataBase`.
`new DatabaseTorgDataSource(true)` uses the legacy `TechnicalRequirements_Process` mapping.
Neither constructor accepts a JDBC URL. The new TORG tables must exist in the configured database.

### Database Schema for TORG

`TORG_Projects` holds `PROJECT_ID`, `PROJECT_NAME`, `COMPANY`, `REVISION`, `ISSUE_DATE`,
`MIN_AMBIENT_TEMP`, `MAX_AMBIENT_TEMP`, `SEAWATER_TEMP`, `SEISMIC_ZONE`,
`CORROSION_ALLOWANCE`, `PRESSURE_SAFETY_FACTOR`, `DEFAULT_PLATE_MATERIAL`, and
`DEFAULT_PIPE_MATERIAL`.

`TORG_Standards` holds `PROJECT_ID`, `DESIGN_CATEGORY`, `STANDARD_CODE`, `VERSION`, and
`PRIORITY`. The database source's Javadoc contains the corresponding DDL. There is no separate
`TORG_Environment` table in this reader.

## TorgManager

Data sources are checked in order and the first matching document is returned. `load(projectId)`
returns `Optional<TechnicalRequirementsDocument>` without changing the active document.
`load(companyIdentifier, projectName)` uses the **project name**, not its ID.

`apply(torg, process)` both activates and applies a document. `loadAndApply(projectId, process)`
combines these operations and returns false if no document is found. `setActiveTorg(torg)` only
sets the active document; it does not apply settings. `getActiveTorg()` is null until activation.

## Applying TORG to Process Systems

### Automatic Application

Use `manager.loadAndApply(projectId, process)` after configuring sources. Its true result means
the document was found and application was attempted; individual failed standard assignments
are logged. Inspect `getAppliedStandards(equipmentName)` to verify the assignments required by
the application.

### Manual Application

Use `manager.apply(torg, process)` for the complete flowsheet or
`manager.applyToEquipment(torg, equipment)` for one item. Initialize the equipment's required
material, weld, and sizing defaults before assigning additional standards, as in the example.
A pressure-vessel standard alone does not supply all supporting separator design inputs.

## What Gets Applied

| Requirement | Current `TorgManager` behavior |
|-------------|--------------------------------|
| Applicable standards | Creates standards and assigns them by category; failures are logged |
| Corrosion allowance | Copies to `MechanicalDesign.setCorrosionAllowance(...)` |
| Minimum ambient temperature | Copies through the raw minimum operating-temperature setter; see unit correction below |
| Other environmental values | Stored in the document |
| Pressure/temperature margins, wall tolerance, load factor | Stored; not automatically enforced |
| Material specifications and custom design life | Stored; not automatically applied |

Equipment with no applicable standards returns before the environmental/safety settings are
applied. Multiple selected standards for one category are assigned successively to the same
map entry, so avoid conflicting assignments.

**Temperature unit limitation:** environmental temperatures are °C, but the current manager
calls a raw mechanical-design setter that expects K. After application, explicitly set the
minimum operating temperature with the `"C"` overload, as below. Reapply this correction after
every TORG application. The orchestrator reapplies its active TORG internally; use the manual
workflow when this correction is needed. This implementation defect is tracked in
[issue #3617](https://github.com/equinor/neqsim/issues/3617).

## Generating TORG Summary

`manager.generateSummary()` takes no arguments and reports the active project's identity and
standards recorded for each equipment item. It does not generate environmental, material,
fatigue, or corrosion-compliance results.

## TORG Validation

There is no `TorgValidator` or `TorgManager.validateTorgCoverage(...)` API. Validate identity,
required categories, finite values, and equipment coverage in application code. Check the
actual applied-standard list after application and assess the mechanical calculation outputs
separately. [Field Development Orchestration](field_development_orchestration) describes the
available structured workflow validation and its limits.

## Complete Example

The example logs summaries at INFO level; enable INFO output in your Log4j2 configuration to
see them. Its result checks run regardless of the logging level.

Compile and run `TorgIntegrationExample project_torg.csv` with the CSV above.
The example loads CSV requirements, constructs a richer programmatic document, applies it to a
separator, verifies the assignments and unit conversion, and logs a summary. It does not claim
to complete the separator's mechanical design.

```java
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.torg.CsvTorgDataSource;
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;
import neqsim.process.mechanicaldesign.torg.TorgManager;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class TorgIntegrationExample {
  private static final Logger logger = LogManager.getLogger(TorgIntegrationExample.class);

  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException("Supply project_torg.csv");
    }
    TorgManager manager = new TorgManager(new CsvTorgDataSource(Paths.get(args[0])));
    Optional<TechnicalRequirementsDocument> loaded = manager.load("DOCS-001");
    if (!loaded.isPresent()
        || !loaded.get().getAllApplicableStandards("Separator").contains(StandardType.API_12J)) {
      throw new IllegalStateException("CSV separator requirements missing");
    }
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("DOCS-001").projectName("Demonstration separator")
        .companyIdentifier("ExampleCo").revision("1").issueDate("2026-09-10")
        .addStandard(StandardType.ASME_VIII_DIV1.getDesignStandardCategory(),
            StandardType.ASME_VIII_DIV1)
        .addStandard(StandardType.API_12J.getDesignStandardCategory(), StandardType.API_12J)
        .environmentalConditions(new TechnicalRequirementsDocument.EnvironmentalConditions(
            -30.0, 35.0, 10.0, "0", 30.0, 10.0, "Synthetic offshore site"))
        .safetyFactors(new TechnicalRequirementsDocument.SafetyFactors(1.10, 25.0, 3.0, 0.125, 1.0))
        .materialSpecifications(new TechnicalRequirementsDocument.MaterialSpecifications(
            "SA-516-70", "API-5L-X65", -46.0, 150.0, true, "ASTM"))
        .customParameter("designLifeYears", 25).build();

    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    Separator separator = new Separator("HP Separator", feed);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    MechanicalDesign design = separator.getMechanicalDesign();
    design.setCompanySpecificDesignStandards("default");
    manager.apply(torg, process);
    // TORG environmental data are Celsius; use the unit-aware setter after application.
    design.setMinOperationTemperature(torg.getEnvironmentalConditions().getMinAmbientTemperature(), "C");

    if (!manager.getAppliedStandards(separator.getName()).contains(StandardType.ASME_VIII_DIV1)
        || !manager.getAppliedStandards(separator.getName()).contains(StandardType.API_12J)
        || Math.abs(design.getMinOperationTemperature("K") - 243.15) > 1.0e-9
        || Math.abs(design.getCorrosionAllowance() - 3.0) > 1.0e-9) {
      throw new IllegalStateException("Required settings were not applied correctly");
    }
    logger.info("{}", manager.generateSummary());
    logger.info("Design seawater temperature: {} C; impact testing required: {}; design life: {} years",
        torg.getEnvironmentalConditions().getDesignSeawaterTemperature(),
        torg.getMaterialSpecifications().isImpactTestingRequired(),
        torg.getCustomParameter("designLifeYears", Integer.class));
  }
}
```

## Best Practices

### 1. One TORG Per Project

Keep a clearly identified governing revision for each design scope. The manager's active TORG
is application state; loading another document alone does not change it.

### 2. Version Control TORGs

Store the source document, selected editions, revision, issue date, and local deviations with
the process model. A metadata version column alone does not ensure edition-specific calculations.

### 3. Validate Before Production

Require coverage checks, finite results, input/unit verification, and independent engineering
review of the applicable calculation methods. See [Mechanical Design Standards](mechanical_design_standards)
for selection and implementation support.

### 4. Document Deviations

Record any explicit equipment settings that differ from the governing requirements and verify
them again after reapplying a TORG or changing data sources.

## See Also

- [Mechanical Design Standards](mechanical_design_standards)
- [Mechanical Design Database](mechanical_design_database)
- [Field Development Orchestration](field_development_orchestration)
