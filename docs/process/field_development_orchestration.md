---
title: Field Development Design Orchestration
description: Coordinate a base-case process simulation, mechanical design, TORG application, and reporting; explicitly run changed operating conditions for each design case.
---

# Field Development Design Orchestration

## Overview

`FieldDevelopmentDesignOrchestrator` coordinates a process simulation, TORG application,
mechanical design, validation messages, and a text report. It lives in
`neqsim.process.mechanicaldesign`, together with `DesignPhase`, `DesignCase`, and
`DesignValidationResult`.

**Current scope:** one workflow call runs the supplied process once. Adding multiple design
cases creates result labels containing the same base-case design totals; it does not change
flow, composition, pressure, or temperature and simulate each scenario. The complete example
below explicitly updates throughput and runs a separate workflow for each case.

`MechanicalDesignGuideDocumentationTest` compiles and runs that example, including mass-balance,
finite-result, and compressor-power trend checks. It is a synthetic API demonstration, not a
qualified field design or independent validation of equipment correlations.

## Orchestrator Architecture

| Object | Role |
|--------|------|
| `ProcessSystem` | User-defined fluid, feeds, equipment, and operating conditions |
| `DesignPhase` | Reporting metadata and validation requirements |
| `DesignCase` | Scenario label and indicative load factor |
| `TorgManager` | Active requirements and standards application |
| `SystemMechanicalDesign` | Per-equipment calculations and aggregate results |
| `DesignValidationResult` | Messages, metrics, and severity-based status |

## Design Phases

The table reflects the current enum values. Accuracy ranges are planning metadata, not measured
error bounds guaranteed by NeqSim.

| Phase | `getAccuracyRange()` | Detailed compliance flag | Full mechanical-design flag |
|-------|----------------------|--------------------------|-----------------------------|
| `SCREENING` | ±40-50% | No | No |
| `CONCEPT_SELECT` | ±25-35% | No | No |
| `PRE_FEED` | ±20-30% | No | No |
| `FEED` | ±15-20% | Yes | Yes |
| `DETAIL_DESIGN` | ±10-15% | Yes | Yes |
| `AS_BUILT` | ±5% | Yes | No |

### Using Design Phases

Use `setDesignPhase(...)` on the orchestrator. `DesignPhase` exposes
`getAccuracyRange()`, `requiresDetailedCompliance()`, and `requiresFullMechanicalDesign()`.
There are no `isLaterThan` or `isEarlierThan` methods. The workflow still calls system mechanical
design in every phase; these flags control validation, not selection of a different solver.

## Design Cases

These are the enum's current indicative values. Replace them with project operating cases
where known; a scalar throughput change does not model startup, shutdown, or relief physics.

| Case | Typical load factor | `isSizingCritical()` | `requiresReliefSizing()` |
|------|---------------------|----------------------|-------------------------|
| `NORMAL` | 1.0 | No | No |
| `MAXIMUM` | 1.15 | Yes | No |
| `MINIMUM` | 0.4 | No | No |
| `STARTUP` | 0.0 | No | No |
| `SHUTDOWN` | 0.0 | No | No |
| `UPSET` | 1.25 | Yes | Yes |
| `EMERGENCY` | 1.5 | No | Yes |
| `WINTER` | 1.0 | No | No |
| `SUMMER` | 1.0 | No | No |
| `EARLY_LIFE` | 1.2 | Yes | No |
| `LATE_LIFE` | 0.6 | No | No |

### Using Design Cases

Read the factor with `getTypicalLoadFactor()`. `isTurndownCase()` is true for `MINIMUM` and
`LATE_LIFE`. To select sizing or relief cases, iterate over `DesignCase.values()` and apply the
corresponding predicate; static methods such as `getSizingCriticalCases()` do not exist.

## Complete Workflow Example

### Step 1: Create Orchestrator

The constructor requires both `ProcessSystem` and project ID:
`new FieldDevelopmentDesignOrchestrator(process, "DOCS-FIELD")`.
Define the fluid, feed rate, and all equipment before creating it; see the complete class below.
The example treats the feed as an external boundary: run `feed.run()` explicitly, and include
only the separator and compressor in the orchestrated system. The current FEED compliance
check otherwise attempts to require design standards on the plain feed stream, whose base
mechanical-design accessor returns a new placeholder object on each call.

### Step 2: Configure Design Phase and Cases

The constructor initially selects `NORMAL` and `MAXIMUM`. Replace the list using
`setDesignCases(...)`; `addDesignCase(...)` adds a unique label. In the example, one label is
selected per actual simulation so each result has an unambiguous operating point.

### Step 3: Load and Apply TORG

For a programmatic document, use `orchestrator.getTorgManager().setActiveTorg(torg)` before
running. For file input, configure that manager with a `CsvTorgDataSource`, then call
`load(projectId)` and activate the returned document, or use `loadAndApply(projectId, process)`.

The orchestrator's `loadTorg(projectId)` and `loadTorg(projectId, dataSource)` currently only
report whether a document was found. They do not activate it. Check `getActiveTorg()` before
relying on TORG application. See [TORG Integration](torg_integration) for CSV formats and the
current environmental-temperature unit limitation. This example does not include environmental
metadata in the active TORG.

### Step 4: Run Complete Workflow

`runCompleteDesignWorkflow()` returns a boolean. The sequence initializes results, runs one
process calculation, applies the active TORG, runs mechanical design, optionally generates
engineering deliverables, validates, and records a summary.

Inspect both this boolean and `getSystemMechanicalDesign().getLastCalculationResult()`.
The system design uses best-effort execution, so a partial equipment calculation can be recorded
without causing the orchestrator itself to throw. A true workflow result is not sufficient
proof that all equipment calculations completed.

### Step 5: Get Results

Use `getValidationResult()`, `getCaseResults()`, `getWorkflowHistory()`, and
`generateDesignReport()`. `DesignCaseResult` and `WorkflowStep` are nested classes of the
orchestrator. `validateDesign()` is private; it is invoked by the workflow.

## Design Validation Results

### Severity Levels

| Severity | Meaning in the result container | Makes `isValid()` false |
|----------|---------------------------------|------------------------|
| `INFO` | Information | No |
| `WARNING` | Review required | No |
| `ERROR` | Reported error | Yes |
| `CRITICAL` | Reported critical issue | Yes |

### Using Validation Results

Retrieve all messages with `getMessages()`, filter with `getMessages(Severity.ERROR)`, and count
with `getCount(Severity.ERROR)`. `hasWarnings()` and `hasErrors()` are available;
`getMessagesBySeverity()`, `getErrorCount()`, and `hasCriticalIssues()` are not.

`getSummary()` provides a compact count summary. The workflow checks standard assignment and
some basic weight/pressure conditions. It reports TORG environmental ranges and safety factors
as information; it does not establish comprehensive TORG compliance. Also inspect positive,
finite calculated values and independent engineering acceptance criteria.

## Design Report Generation

`generateDesignReport()` returns a string with project/run identifiers, phase, case labels,
aggregate equipment results, validation messages, and workflow history. It does not contain
independently solved results for labels that were merely added to one workflow. Keep each
report with its actual input conditions and software revision.

## Workflow Customization

### Custom Workflow Steps

Call application prechecks before `runCompleteDesignWorkflow()` and export results afterward.
There are no `addPreProcessStep` or `addPostProcessStep` callback methods.

### Selective Case Execution

Use `setDesignCases(Collections.singletonList(designCase))` for an explicitly configured
operating point, as in the example. `clearDesignCases()` is not a public method.

### Phase-Specific Behavior

Choose a phase to select validation requirements and report metadata. There is no
`setDetailedCalculations(...)` switch on this orchestrator.

## Integration with Process Simulation

### Updating Process Conditions

Change the actual feed/equipment inputs before each workflow. The example scales mass flow
from the 10,000 kg/hr base rate while holding SRK composition, 50 bara inlet pressure, 30 °C
inlet temperature, 80 bara compressor outlet pressure, and 75% isentropic efficiency constant.
A corresponding change in compressor power is expected for this fixed operating state.

### Equipment Sizing Envelope

There is no orchestrator `getSizingEnvelope()` API. Store the results of the separately executed
cases in an application map and compare the quantities relevant to each item: gas volume flow,
liquid residence requirement, pressure, temperature, power, and mechanical dimensions. Do not
select every design variable from a single case merely because it has the highest total weight.

## Error Handling

Check the workflow return value and inspect validation/history even after failure. Check the
structured mechanical-design result's `isComplete()` before accepting totals. The previously
shown `TorgNotFoundException`, `DesignConvergenceException`, and
`StandardNotSupportedException` handlers are not part of this orchestrator's public workflow.

## Best Practices

### 1. Progressive Refinement

Replace screening assumptions with project data as the study matures. Changing the phase label
alone does not refine model physics or input quality.

### 2. Document All Assumptions

Keep a companion record of composition, flow basis, operating cases, design margins, material
choices, standards, and correlation limits. `addAssumption(...)` is not an orchestrator API.

### 3. Version Control Integration

Store the input model, software commit, TORG revision, and per-case reports together. The
orchestrator supplies a run UUID but has no `setRunMetadata(...)` method.

### 4. Reproducibility

Retain executable model construction code. There are no `saveConfiguration(...)` or
`loadConfiguration(...)` methods on this orchestrator.

## Complete Example

The example logs summaries at INFO level; enable INFO output in your Log4j2 configuration to
see them. Its result checks run regardless of the logging level.

```java
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.DesignCase;
import neqsim.process.mechanicaldesign.DesignPhase;
import neqsim.process.mechanicaldesign.DesignValidationResult;
import neqsim.process.mechanicaldesign.FieldDevelopmentDesignOrchestrator;
import neqsim.process.mechanicaldesign.SystemMechanicalDesignResult;
import neqsim.process.mechanicaldesign.designstandards.StandardType;
import neqsim.process.mechanicaldesign.torg.TechnicalRequirementsDocument;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class FieldDevelopmentDesignExample {
  private static final Logger logger = LogManager.getLogger(FieldDevelopmentDesignExample.class);

  public static void main(String[] args) {
    SystemSrkEos fluid = new SystemSrkEos(303.15, 50.0);
    fluid.addComponent("methane", 0.70);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.10);
    fluid.addComponent("n-butane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("Feed", fluid);
    Separator separator = new Separator("HP Separator", feed);
    separator.setInternalDiameter(1.0);
    Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
    compressor.setOutletPressure(80.0, "bara");
    compressor.setIsentropicEfficiency(0.75);
    ProcessSystem process = new ProcessSystem();
    process.add(separator);
    process.add(compressor);
    for (ProcessEquipmentInterface equipment : process.getUnitOperations()) {
      equipment.getMechanicalDesign().setCompanySpecificDesignStandards("default");
    }
    separator.getMechanicalDesign().setMaxOperationPressure(50.0, "bara");
    compressor.getMechanicalDesign().setMaxOperationPressure(80.0, "bara");
    TechnicalRequirementsDocument torg = TechnicalRequirementsDocument.builder()
        .projectId("DOCS-FIELD").projectName("Synthetic gas processing study")
        .addStandard(StandardType.ASME_VIII_DIV1.getDesignStandardCategory(), StandardType.ASME_VIII_DIV1)
        .addStandard(StandardType.API_12J.getDesignStandardCategory(), StandardType.API_12J)
        .addStandard(StandardType.API_617.getDesignStandardCategory(), StandardType.API_617).build();

    double baseFlowKgPerHour = 10000.0;
    Map<DesignCase, Double> powersKw = new EnumMap<>(DesignCase.class);
    for (DesignCase designCase : Arrays.asList(DesignCase.NORMAL, DesignCase.MAXIMUM, DesignCase.MINIMUM)) {
      feed.setFlowRate(baseFlowKgPerHour * designCase.getTypicalLoadFactor(), "kg/hr");
      feed.run();
      FieldDevelopmentDesignOrchestrator orchestrator =
          new FieldDevelopmentDesignOrchestrator(process, "DOCS-FIELD-" + designCase.name());
      orchestrator.setDesignPhase(DesignPhase.FEED);
      orchestrator.setDesignCases(Collections.singletonList(designCase));
      orchestrator.getTorgManager().setActiveTorg(torg);
      boolean workflowCompleted = orchestrator.runCompleteDesignWorkflow();
      DesignValidationResult validation = orchestrator.getValidationResult();
      if (!workflowCompleted || orchestrator.getSystemMechanicalDesign() == null) {
        throw new IllegalStateException(validation.getMessages().toString());
      }
      SystemMechanicalDesignResult calculation =
          orchestrator.getSystemMechanicalDesign().getLastCalculationResult()
              .orElseThrow(() -> new IllegalStateException("No mechanical calculation result"));
      if (!calculation.isComplete()) {
        throw new IllegalStateException("Some equipment designs did not complete");
      }
      double massOut = separator.getGasOutStream().getFlowRate("kg/hr")
          + separator.getLiquidOutStream().getFlowRate("kg/hr");
      double powerKw = compressor.getPower("kW");
      double weightKg = orchestrator.getCaseResults().get(designCase).getTotalWeight();
      if (!Double.isFinite(massOut)
          || Math.abs(massOut - feed.getFlowRate("kg/hr")) > 1.0e-5
          || !Double.isFinite(powerKw) || powerKw <= 0.0
          || !Double.isFinite(weightKg) || weightKg <= 0.0) {
        throw new IllegalStateException("Invalid mass balance, power, or mechanical weight");
      }
      powersKw.put(designCase, powerKw);
      logger.info("{}: feed={} kg/hr, compressor={} kW, estimated weight={} kg, warnings={}",
          designCase.name(), feed.getFlowRate("kg/hr"), powerKw, weightKg,
          validation.getCount(DesignValidationResult.Severity.WARNING));
      logger.info("{}", orchestrator.generateDesignReport());
    }
    if (!(powersKw.get(DesignCase.MINIMUM) < powersKw.get(DesignCase.NORMAL)
        && powersKw.get(DesignCase.NORMAL) < powersKw.get(DesignCase.MAXIMUM))) {
      throw new IllegalStateException("Expected compressor power to increase with throughput");
    }
  }
}
```

The executed feed rates are 10,000, 11,500, and 4,000 kg/hr. Each report corresponds to one
actual process run. Review any warnings even when the boolean result is true; the example's
checks establish software execution and basic physical consistency only.

## See Also

- [Mechanical Design Standards](mechanical_design_standards)
- [Mechanical Design Database](mechanical_design_database)
- [TORG Integration](torg_integration)
- [Process Design Guide](process_design_guide)
