---
title: Process Design Guide for NeqSim
description: An executable process-to-mechanical-design example with explicit units, supported standards APIs, structured calculation status, and physical checks.
---

# Process Design Guide for NeqSim

## Introduction

This guide connects a steady-state flowsheet to preliminary mechanical sizing and reporting.
The complete example defines all inputs, runs a separator and compressor, applies project
standards, calculates mechanical results, and checks mass balance and output validity.
`MechanicalDesignGuideDocumentationTest` compiles and executes the example directly from this
page against the current repository classes.

The synthetic example uses SRK with the classic mixing rule. It demonstrates software behavior
and basic physical consistency. Material suitability, code compliance, relief design, layout,
and equipment procurement require additional engineering checks and qualified inputs.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Design Framework](DESIGN_FRAMEWORK) | AutoSizeable, process templates, and design optimization |
| [Production Optimization Guide](../examples/PRODUCTION_OPTIMIZATION_GUIDE) | Production optimization examples |
| [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK) | Equipment capacity constraints |

## Process Design Workflow Overview

1. Define fluid composition, thermodynamic model, units, feeds, and equipment.
2. Run the process and check balances and operating results.
3. Set mechanical inputs and standards, then calculate equipment designs.
4. Check calculation completeness and engineering bounds before reporting results.

## Step 1: Define the System

### 1.1 Create the Fluid System

The complete example uses mole fractions of 0.70 methane, 0.10 ethane, 0.10 propane,
0.05 n-butane, and 0.05 n-pentane. `SystemSrkEos` constructor inputs are K and bara;
303.15 K corresponds to 30 °C. Use `setMixingRule("classic")` after adding components.

### 1.2 Build the Process Flowsheet

Connect a 10,000 kg/hr feed to an HP separator and its gas outlet to a compressor. Set the
compressor outlet to 80 bara and its isentropic efficiency to 0.75. Add the feed and equipment
to the `ProcessSystem` in flow order. The example starts with a 1 m separator diameter before
calculating a preliminary size.

### 1.3 Load Project TORG

The example creates a programmatic `TechnicalRequirementsDocument` so it needs no external
project file. Use the real category names returned by `StandardType.getDesignStandardCategory()`.
For external requirements, see [TORG Integration](torg_integration): filesystem CSV input takes
a `Path`, and loading a document alone does not activate or apply it.

## Step 2: Process Simulation

### 2.1 Run Base Case Simulation

Call `process.run()` before mechanical calculations so phase flow and physical properties are
available. Retrieve outlet flows with explicit units and compressor duty through
`getPower("kW")`. The example verifies that gas plus liquid mass flow equals feed mass flow,
that compressor power is positive and finite, and that outlet pressure equals the target.

### 2.2 Run Multiple Design Cases

Import `DesignCase` from `neqsim.process.mechanicaldesign`. Its load factors are scenario
metadata; explicitly change feed/equipment conditions and rerun for each scenario. The
[Field Development Orchestration](field_development_orchestration) example executes normal,
maximum, and minimum throughput cases. Adding case labels to an orchestrator alone does not
run different operating conditions.

## Step 3: Mechanical Design

### 3.1 Apply Design Standards

Initialize the equipment's supporting default design inputs before assigning individual
standards. Use `equipment.getMechanicalDesign().setDesignStandard(StandardType)` or a
`TorgManager`; `StandardRegistry.applyStandardToEquipment(...)` does not exist.

The example chooses `ASME_VIII_DIV1`, `API_12J`, and `API_617`. A registered standard name is
not proof of a complete edition-specific calculation. See
[Mechanical Design Standards](mechanical_design_standards) for selection support and limitations.

### 3.2 Run Mechanical Design Calculations

Use `setMaxOperationPressure(value, "bara")` and
`setMaxOperationTemperature(value, "C")` to define the mechanical operating envelope.
`setPressureMarginFactor(0.10)` is a **fractional** 10% margin; a TORG pressure safety factor of
1.10 is a multiplier and must not be passed unchanged to this setter.

The current maximum design-pressure getter is `getMaxDesignPressure("bara")`, and operating
temperature is available through `getMaxOperationTemperature("C")`. There are no generic
`getDesignPressure()` or `getDesignTemperature()` getters in `MechanicalDesign`.
For separator pressure-vessel sizing, `getWallThickness()` returns metres, so multiply by 1000
when reporting mm. Total mechanical weight is kg.

The separator pressure-vessel calculation uses the equipment's current internal diameter.
The example first calculates a preliminary diameter, assigns it back to the separator, then
runs system design so thickness/weight use that diameter. The pressure margin getter is design
metadata; inspect the equipment-specific calculation before assuming every margin or material
requirement is automatically enforced.

### 3.3 Design All Equipment in System

Use `SystemMechanicalDesign.calculate(SystemDesignExecutionMode.FAIL_FAST)` for this example.
It returns a `SystemMechanicalDesignResult` and throws on a failed equipment calculation.
`runDesignCalculation()` uses best-effort mode; check `getLastCalculationResult().isPresent()`
and the contained result's `isComplete()` before accepting its aggregate totals.

See [Mechanical Design Database](mechanical_design_database) for loading generic design limits.
These limits do not automatically replace the explicitly configured operating envelope.

## Step 4: Validate and Report

### 4.1 Validate Design Compliance

`DesignValidationResult` is in `neqsim.process.mechanicaldesign`. Add an application check with
`addError(category, equipmentName, message, remediation)` and information with
`addInfo(equipmentName, message)`. `isValid()` only reports whether the collected messages
contain an error or critical issue; it is not an independent compliance assessment.

The example verifies mass balance, compressor pressure/power, positive finite diameter,
thickness and weight, and complete equipment calculations. It also compares the explicitly
assigned corrosion allowance with the requirement. These are software/consistency checks,
not a substitute for an independent sizing benchmark.

### 4.2 Generate Design Report

Use `SystemMechanicalDesign.generateSummaryReport()` for equipment totals and the validation
container's `getSummary()` for the checks performed by the application. Clearly distinguish
calculated dimensions, configured operating limits, and selected standards in reports.

## Using the Field Development Orchestrator

The constructor is `FieldDevelopmentDesignOrchestrator(process, projectId)`. Use
`getValidationResult()` after `runCompleteDesignWorkflow()`; `validateDesign()` is private.
The [orchestrator guide](field_development_orchestration) describes active TORG handling,
per-case execution, and the need to inspect mechanical calculation completeness separately.

## Design Phases and Accuracy

| Phase | Current planning range | Full-design validation flag |
|-------|------------------------|-----------------------------|
| `SCREENING` | ±40-50% | No |
| `CONCEPT_SELECT` | ±25-35% | No |
| `PRE_FEED` | ±20-30% | No |
| `FEED` | ±15-20% | Yes |
| `DETAIL_DESIGN` | ±10-15% | Yes |
| `AS_BUILT` | ±5% | No |

These are `DesignPhase` metadata, not verified uncertainty intervals. Selecting a phase does
not alter the thermodynamic model or automatically improve the mechanical correlations.

## Supported Design Standards

Use the [Mechanical Design Standards](mechanical_design_standards) guide to distinguish
standard metadata, mapped implementations, strict selection, and calculation support. A
standard appearing in `StandardType` does not establish that every equipment type or clause
is implemented.

## Data Sources

Configure data sources on each `MechanicalDesign` using `setDesignDataSource(...)` or
`setDesignDataSources(...)`. `StandardBasedCsvDataSource` takes a filesystem `Path` or a
classpath-resource `String`. `StandardRegistry.registerDataSource(...)` is not available.
See [Mechanical Design Database](mechanical_design_database) for executable CSV examples.

## Complete Example

The example logs summaries at INFO level; enable INFO output in your Log4j2 configuration to
see them. Its result checks run regardless of the logging level.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignValidationResult;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.SystemDesignExecutionMode;
import neqsim.process.mechanicaldesign.SystemMechanicalDesign;
import neqsim.process.mechanicaldesign.SystemMechanicalDesignResult;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;
import neqsim.process.mechanicaldesign.torg.TorgManager;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class ProcessDesignExample {
  private static final Logger logger = LogManager.getLogger(ProcessDesignExample.class);

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    feed.setFlowRate(10000.0, "kg/hr");
    Separator separator = new Separator("HP Separator", feed);
    separator.setInternalDiameter(1.0);
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    ProcessSystem process = new ProcessSystem();
    process.add(feed);
    process.add(separator);
    process.add(compressor);
    process.run();

    double gasKgPerHour = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidKgPerHour = separator.getLiquidOutStream().getFlowRate("kg/hr");
    double powerKw = compressor.getPower("kW");
    DesignValidationResult validation = new DesignValidationResult();
    if (!Double.isFinite(gasKgPerHour) || !Double.isFinite(liquidKgPerHour)
        || gasKgPerHour <= 0.0 || liquidKgPerHour < 0.0
        || Math.abs(gasKgPerHour + liquidKgPerHour - feed.getFlowRate("kg/hr")) > 1.0e-5
        || !Double.isFinite(powerKw) || powerKw <= 0.0
        || Math.abs(compressor.getOutletStream().getPressure("bara") - 80.0) > 1.0e-9) {
      validation.addError("Process", "Flowsheet", "Invalid mass balance, power, or outlet pressure",
          "Review feed conditions and equipment configuration");
    }

    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      equipment.getMechanicalDesign().setCompanySpecificDesignStandards("default");
    }
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("DOCS-001").projectName("Synthetic process design")
        .addStandard(StandardType.ASME_VIII_DIV1.getDesignStandardCategory(), StandardType.ASME_VIII_DIV1)
        .addStandard(StandardType.API_12J.getDesignStandardCategory(), StandardType.API_12J)
        .addStandard(StandardType.API_617.getDesignStandardCategory(), StandardType.API_617)
        .safetyFactors(new TechnicalRequirementsDocument.SafetyFactors(1.10, 25.0, 3.0, 0.125, 1.0))
        .build();
    TorgManager manager = new TorgManager();
    manager.apply(torg, process);
    MechanicalDesign separatorDesign = separator.getMechanicalDesign();
    separatorDesign.setMaxOperationPressure(50.0, "bara");
    separatorDesign.setMaxOperationTemperature(30.0, "C");
    separatorDesign.setPressureMarginFactor(torg.getSafetyFactors().getPressureSafetyFactor() - 1.0);
    compressor.getMechanicalDesign().setMaxOperationPressure(80.0, "bara");
    compressor.getMechanicalDesign().setMaxOperationTemperature(
        compressor.getOutletStream().getTemperature("C"), "C");
    separatorDesign.calcDesign();
    separator.setInternalDiameter(separatorDesign.getInnerDiameter());

    SystemMechanicalDesign systemDesign = new SystemMechanicalDesign(process);
    SystemMechanicalDesignResult calculation = systemDesign.calculate(SystemDesignExecutionMode.FAIL_FAST);
    double wallThicknessMm = separatorDesign.getWallThickness() * 1000.0;
    if (!calculation.isComplete() || !Double.isFinite(systemDesign.getTotalWeight())
        || systemDesign.getTotalWeight() <= 0.0 || !Double.isFinite(separatorDesign.getInnerDiameter())
        || separatorDesign.getInnerDiameter() <= 0.0 || !Double.isFinite(wallThicknessMm)
        || wallThicknessMm <= 0.0
        || separatorDesign.getCorrosionAllowance() < torg.getSafetyFactors().getCorrosionAllowance()) {
      validation.addError("Mechanical", separator.getName(), "Incomplete or invalid design results",
          "Inspect equipment outcomes, standards, dimensions, and material inputs");
    }
    if (!validation.isValid()) {
      throw new IllegalStateException(validation.getMessages().toString());
    }
    validation.addInfo("Flowsheet", "Documented execution and physical consistency checks passed");
    logger.info("Gas={} kg/hr; liquid={} kg/hr; compressor={} kW", gasKgPerHour, liquidKgPerHour, powerKw);
    logger.info("Separator diameter={} m; wall={} mm; maximum design-pressure metadata={} bara",
        separatorDesign.getInnerDiameter(), wallThicknessMm, separatorDesign.getMaxDesignPressure("bara"));
    logger.info("{}", validation.getSummary());
    logger.info("{}", systemDesign.generateSummaryReport());
  }
}
```

For these inputs, gas and liquid outlet mass flows must sum to 10,000 kg/hr and the compressor
outlet pressure must be 80 bara. The explicitly configured maximum separator operating pressure
is 50 bara; the 10% margin makes its maximum design-pressure metadata 55 bara. Mechanical
results are preliminary estimates from the selected implementations.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Mechanical Design Standards](mechanical_design_standards) | Standards selection and implementation coverage |
| [Mechanical Design Database](mechanical_design_database) | Supported data-source APIs, formats, and units |
| [TORG Integration](torg_integration) | Requirements, activation, application, and limitations |
| [Field Development Orchestration](field_development_orchestration) | Explicit operating cases and workflow reports |

## Quick Reference

### Key Classes

| Class | Purpose |
|-------|---------|
| `ProcessSystem` | Flowsheet and simulation |
| `MechanicalDesign` | Equipment design inputs and outputs |
| `SystemMechanicalDesign` | Equipment calculations and aggregate report |
| `SystemMechanicalDesignResult` | Completion and per-equipment outcomes |
| `StandardType`, `StandardRegistry` | Standards metadata and implementation selection |
| `TechnicalRequirementsDocument`, `TorgManager` | Project requirements and supported application |
| `FieldDevelopmentDesignOrchestrator` | Base-case workflow coordination |
| `DesignPhase`, `DesignCase`, `DesignValidationResult` | Phase/case metadata and validation messages |

### Key Packages

| Package | Contents |
|---------|----------|
| `neqsim.process.processmodel` | `ProcessSystem` |
| `neqsim.process.equipment` | Equipment interfaces and subpackages |
| `neqsim.process.mechanicaldesign` | Mechanical design, orchestration, phases/cases, validation |
| `neqsim.process.mechanicaldesign.designstandards` | Standards framework |
| `neqsim.process.mechanicaldesign.torg` | TORG framework |
| `neqsim.process.mechanicaldesign.data` | Design-limit data sources |
