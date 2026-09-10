---
title: Mechanical Design Database and Data Sources
description: Load mechanical design limits from the process design database, company CSV files, and standard-based CSV files using the current NeqSim API.
---

## Overview

Mechanical design data sources supply `DesignLimitData`: maximum and minimum pressure and
temperature, corrosion allowance, and joint efficiency. They do not provide a generic material
property or arbitrary string-property interface. Equipment-specific design standards load
additional sizing and material data separately.

The complete example below and the CSV formats on this page are exercised by
`MechanicalDesignGuideDocumentationTest`. The values are synthetic application inputs, not
requirements taken from an engineering standard.

## Data Source Architecture

| Class | Input | Lookup |
|-------|-------|--------|
| `MechanicalDesignDataSource` | Interface | Equipment type and company identifier |
| `DatabaseMechanicalDesignDataSource` | NeqSim process design database | `TechnicalRequirements_Process` rows |
| `CsvMechanicalDesignDataSource` | Filesystem `Path` | Company-format CSV |
| `StandardBasedCsvDataSource` | Filesystem `Path` or classpath resource `String` | Company or standard-format CSV |

All four are in `neqsim.process.mechanicaldesign.data`.

## MechanicalDesignDataSource Interface

The required method is
`Optional<DesignLimitData> getDesignLimits(String equipmentTypeName, String companyIdentifier)`.
Use the equipment's Java class simple name, for example `Separator` or `ThreePhaseSeparator`.
A missing match returns `Optional.empty()`; an individual undefined limit is `Double.NaN`.

The interface also provides `getDesignLimitsByStandard(standardCode, version, equipmentTypeName)`,
`getAvailableStandards(equipmentTypeName)`, `getAvailableVersions(standardCode)`, and
`hasStandard(standardCode)`. The default standard lookup delegates to company lookup; only
implementations that override it can distinguish editions.

## Database Data Source

### Configuration

`new DatabaseMechanicalDesignDataSource()` uses `NeqSimProcessDesignDataBase`. There is no JDBC
URL or table-name constructor on this data source. Configure database access through the
process database API when needed.

### Database Schema

The reader queries `SPECIFICATION`, `MAXVALUE`, and `MINVALUE` in
`TechnicalRequirements_Process`, filtered by `EQUIPMENTTYPE` and `Company`. Recognized
specifications are `MaxPressure`, `MinPressure`, `MaxTemperature`, `MinTemperature`,
`CorrosionAllowance`, and `JointEfficiency`. When both numeric values are present, this
reader uses their average; use equal minimum and maximum values for a single fixed limit.

The bundled table is in `src/main/resources/designdata/TechnicalRequirements_Process.csv`.
The former generic `CATEGORY` / `PARAMETER_NAME` schema does not match this reader.

### Querying the Database

Use `getDesignLimits(equipmentType, company)`, as demonstrated in the fallback source list
below. An empty result can mean no matching supported specification or a database read failure;
inspect the application log before treating it as an intentional absence.

## CSV Data Source

### Basic CSV Format

Save this company-format example as `company_limits.csv` and pass its `Path` to
`CsvMechanicalDesignDataSource`. Pressures are bara, temperatures are K, corrosion allowance is
mm, and joint efficiency is dimensionless.

```csv
EQUIPMENTTYPE,COMPANY,MAXPRESSURE,MINPRESSURE,MAXTEMPERATURE,MINTEMPERATURE,CORROSIONALLOWANCE,JOINTEFFICIENCY
Separator,ExampleCo,150.0,1.01325,423.15,233.15,3.0,0.85
```

### Using CSV Data Source

Filesystem input requires `Paths.get("company_limits.csv")`. For `StandardBasedCsvDataSource`,
the `String` constructor means a **classpath resource**, not a filesystem path. Neither CSV
reader accepts a `StandardType` constructor argument.

### Standard-Based CSV Structure

Save this independent example as `standard_limits.csv`:

```csv
STANDARD_CODE,STANDARD_VERSION,EQUIPMENTTYPE,SPECIFICATION,MINVALUE,MAXVALUE,UNIT,DESCRIPTION
DOCS-DEMO,1,Separator,MaxPressure,150.0,150.0,bara,Synthetic maximum pressure
DOCS-DEMO,1,Separator,MinPressure,1.01325,1.01325,bara,Synthetic minimum pressure
DOCS-DEMO,1,Separator,MaxTemperature,423.15,423.15,K,Synthetic maximum temperature
DOCS-DEMO,1,Separator,MinTemperature,233.15,233.15,K,Synthetic minimum temperature
DOCS-DEMO,1,Separator,CorrosionAllowance,3.0,3.0,mm,Synthetic corrosion allowance
DOCS-DEMO,1,Separator,JointEfficiency,0.85,0.85,-,Synthetic joint efficiency
```

`UNIT` is descriptive: the current CSV reader does **not** convert units. Supply bara and K
for `DesignLimitData`, even if an external source uses barg or Celsius. Some bundled standard
files contain those external units and need explicit conversion before use as design limits.

Standard lookup uses the maximum column for maximum pressure/temperature and the minimum
column for minimum pressure/temperature. For corrosion and joint efficiency it prefers the
maximum column. Unrecognized specification names are available through
`getSpecificationValues(...)`, but are not automatically applied to `DesignLimitData`.

## Registering Data Sources

### With MechanicalDesign

Use `setDesignDataSource(source)`, `setDesignDataSources(orderedSources)`, or
`addDesignDataSource(source)` on the equipment's `MechanicalDesign`. Setting or adding a
source reloads the limits. The complete example demonstrates two ordered sources.

The first present `DesignLimitData` wins as a whole; fields are not merged across sources.
Adding a custom source replaces the implicit database-only fallback, so include the database
explicitly if it is still wanted. Loaded pressure and temperature limits are available through
`getDesignLimitData()`; loading them does not set the operating envelope. Corrosion allowance
and joint efficiency are copied into the design object.

### With SystemMechanicalDesign

`SystemMechanicalDesign` has no `addDataSource` method. Configure each equipment design before
running system calculations. See [Process Design Guide](process_design_guide) for the calculation
workflow and structured completion checks.

## Default Data Location

CSV inputs are selected explicitly by path or classpath resource. These classes do not search
`~/.neqsim/designdata/` or the working directory automatically.

### Provided Default Files

The repository includes `designdata/TechnicalRequirements_Process.csv`,
`designdata/MaterialPlateProperties.csv`, `designdata/MaterialPipeProperties.csv`, and
`designdata/standards/{asme,api,norsok,astm,dnv_iso_en,subsea}_standards.csv` as classpath resources.
They have different schemas; do not interchange them without checking the consuming reader.

## Creating Custom Data Sources

Implement `getDesignLimits(...)` and return `Optional.empty()` for unhandled equipment or
companies. Build a result using `DesignLimitData.builder().maxPressure(...).build()` and add
other fields required by the application. Missing fields remain `NaN`; the builder does not
validate physical bounds. A lambda can implement this single required method.

## Data Validation

There is no `DataSourceValidator` class in this package. Validate required fields and units in
the application. The example checks a finite pressure limit and an ordered temperature range.
Project validation should also check joint efficiency, corrosion allowance, and coverage for
every equipment type used.

## Complete Example

The example logs summaries at INFO level; enable INFO output in your Log4j2 configuration to
see them. Its result checks run regardless of the logging level.

Compile this class against NeqSim and run it with the two CSV files above as arguments:
`MechanicalDesignDatabaseExample company_limits.csv standard_limits.csv`.

```java
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignLimitData;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.data.CsvMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.DatabaseMechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.MechanicalDesignDataSource;
import neqsim.process.mechanicaldesign.data.StandardBasedCsvDataSource;
import neqsim.thermo.system.SystemSrkEos;

public class MechanicalDesignDatabaseExample {
  private static final Logger logger = LogManager.getLogger(MechanicalDesignDatabaseExample.class);

  public static void main(String[] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Supply company_limits.csv and standard_limits.csv");
    }
    CsvMechanicalDesignDataSource companySource =
        new CsvMechanicalDesignDataSource(Paths.get(args[0]));
    StandardBasedCsvDataSource standardSource =
        new StandardBasedCsvDataSource(Paths.get(args[1]));
    DesignLimitData companyLimits = companySource.getDesignLimits("Separator", "ExampleCo")
        .orElseThrow(() -> new IllegalStateException("Company limits missing"));
    DesignLimitData standardLimits =
        standardSource.getDesignLimitsByStandard("DOCS-DEMO", "1", "Separator")
            .orElseThrow(() -> new IllegalStateException("Standard limits missing"));
    if (!companyLimits.equals(standardLimits)) {
      throw new IllegalStateException("The two demonstration files should give the same limits");
    }
    if (!Double.isFinite(companyLimits.getMaxPressure())
        || companyLimits.getMaxPressure() <= companyLimits.getMinPressure()
        || !Double.isFinite(companyLimits.getMinTemperature())
        || !Double.isFinite(companyLimits.getMaxTemperature())
        || companyLimits.getMinTemperature() <= 0.0
        || companyLimits.getMinTemperature() >= companyLimits.getMaxTemperature()) {
      throw new IllegalStateException("Invalid pressure or temperature limits");
    }

    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 1.0);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    Separator separator = new Separator("Separator", feed);
    MechanicalDesign design = separator.getMechanicalDesign();
    design.setCompanySpecificDesignStandards("ExampleCo");
    design.setDesignDataSources(Arrays.<MechanicalDesignDataSource>asList(
        companySource, new DatabaseMechanicalDesignDataSource()));
    if (!design.getDesignLimitData().equals(companyLimits)) {
      throw new IllegalStateException("Company limits were not applied");
    }
    logger.info("Maximum pressure limit: {} bara; temperature range: {} to {} K",
        design.getDesignLimitData().getMaxPressure(), companyLimits.getMinTemperature(),
        companyLimits.getMaxTemperature());
    logger.info("Available standards: {}; editions: {}",
        standardSource.getAvailableStandards("Separator"),
        standardSource.getAvailableVersions("DOCS-DEMO"));
  }
}
```

Expected results from the supplied inputs are a 150 bara maximum pressure limit, a
233.15–423.15 K temperature range, 3 mm corrosion allowance, and 0.85 joint efficiency.
This example loads limits; it does not calculate vessel thickness or weight.

## Best Practices

### 1. Version Control Your Data

Keep the CSV inputs with the simulation and record their revision and origin.

### 2. Use Standard Codes Consistently

Use the exact code and edition present in the data. For registered standard-format sources,
keep one edition per file: the equipment lookup does not select a version and can otherwise
combine rows from several editions.

### 3. Document Units

Normalize pressure to bara and temperature to K before loading generic design limits. Preserve
the original units and conversion in the data provenance.

### 4. Layer Data Sources

Order project-specific sources before company defaults and database fallback. Supply all
required fields in the first matching result because lower-priority sources do not fill gaps.

## See Also

- [Mechanical Design Standards](mechanical_design_standards)
- [TORG Document Integration](torg_integration)
- [Field Development Orchestration](field_development_orchestration)
