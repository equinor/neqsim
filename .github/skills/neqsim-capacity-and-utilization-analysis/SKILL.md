---
name: neqsim-capacity-and-utilization-analysis
description: "Equipment capacity constraints, bottlenecks, utilization snapshots, KPI/response DTOs, validated automation writes and GOR/MPFM rate fitting (CapacityConstraint, BottleneckTracker, ProcessAutomation, ProductionRateFitter, BroydenAccelerator). USE WHEN: asked what limits a flowsheet, how to expose a capacity margin, compare KPI data, apply guarded setpoints, or reconcile a simulated stream to gas, water, GOR or MPFM data."
last_verified: "2026-10-03"
---

# Capacity and Utilization Analysis

Use this skill for capacity observations and safe input/data reconciliation. It complements `neqsim-agentic-process-optimization` (decision-space discovery and closed-loop objectives), `neqsim-optimization-and-doe` (optimizer algorithms), and `neqsim-controllability-operability` (operating envelopes and control performance). It does not replace equipment-specific engineering validation.

## When to use this

- Find the enabled capacity constraint with highest utilization in a `ProcessSystem` or `ProcessModel`, and track its identity over a sweep or field-life timeline.
- Inspect the per-unit, per-constraint JSON snapshot including source and data-source metadata.
- Add or calibrate a hard, soft, design or empirical capacity constraint, or decide whether a fallback design value is credible enough for a decision.
- Compare process scenarios with `KPIDashboard`/`ScenarioKPI` and serialize equipment response DTOs for a reporting boundary.
- Apply validated single writes or transactional batches through `ProcessAutomation`.
- Fit a stream's produced gas rate, water rate, GOR or GVF to field/MPFM measurements; use Broyden only as an acceleration method inside a solver loop.

Use the existing optimizer skills for variable bounds, objective construction and optimizer selection. A high utilization number is a model observation, not by itself an approved operating limit.

## Class map

| Class | Package | What it does | Key methods verified |
|---|---|---|---|
| `CapacityConstraint`, `CapacityConstrainedEquipment`, `EquipmentDesignData` | `process.equipment.capacity` | Defines typed limits and attaches design-capacity inputs to supported equipment. | `setDesignValue`, `setValueSupplier`, `getUtilization`, `isViolated`, `setConfidence`, `setValidityRange`, `EquipmentDesignData.apply` |
| `EquipmentCapacityStrategy`, `EquipmentCapacityStrategyRegistry`, `*CapacityStrategy` | `process.equipment.capacity` | Selects equipment-specific utilization/constraint logic; defaults include compressor, separator, pipe, valve, pump, exchanger, column, reactor and others. | `findStrategy`, `evaluateCapacity`, `getConstraints`, `getAllStrategies`, `register` |
| `BottleneckResult`, `BottleneckTracker` | `process.equipment.capacity` | Represents the current binding limit and tracks changes/peak loading across caller-defined time points. | `getEquipmentName`, `getConstraintName`, `getUtilizationPercent`, `record`, `getMigrationEvents`, `getPeakSnapshot`, `toJson` |
| `EmpiricalCarryOverConstraint` | `process.equipment.capacity` | Adds piecewise-linear measured carry-over as a hard empirical constraint. | `fromObservations`, `getCalibrationX`, `getCalibrationY` |
| `ProcessSystem`, `ProcessModel` | `process.processmodel` | Runs flowsheets/areas and aggregates bottleneck and utilization observations. | `findBottleneck`, `getCapacityUtilizationSummary`, `getUtilizationSnapshotJson`, `isAnyHardLimitExceeded` |
| `KPIDashboard`, `ScenarioKPI` | `process.util.monitor` | Stores scenario KPI records, computes category/overall scores and prints comparisons. | `addScenario`, `getScenarioCount`, `calculateSafetyScore`, `calculateProcessScore`, `calculateEnvironmentalScore`, `calculateOverallScore` |
| `BaseResponse`, `CompressorResponse`, `SeparatorResponse`, `StreamResponse`, `ValveResponse`, `*Response` | `process.util.monitor` | Equipment/report DTOs with public fields populated from a live object; the response classes do not share a `toJson()` method. | Equipment constructors and `applyConfig(ReportConfig)` where defined |
| `ProcessAutomation`, `SimulationVariable`, `AdjustableParameter`, `AutomationDiagnostics` | `process.automation` | Lists/read/writes variables, validates safe writes and exposes model inputs/diagnostics. | `setVariableValueSafe`, `setVariableValueValidated`, `getAdjustableParameters`, `getUtilizationSnapshot`, `validateAddress` |
| `WriteValidatorRegistry`, `DefaultWriteValidators`, `WriteValidationResult`, `TransactionalBatchResult` | `process.automation` | Validates equipment writes and reports commit/rollback per address. | `createDefault`, `register`, `validate`, `isAllowed`, `setValuesTransactional`, `isCommitted`, `getRollbackCategory` |
| `ProductionRateFitter`, `GORfitter`, `MPFMfitter`, `FlowSetter`, `FlowRateAdjuster` | `process.equipment.util` | Reconciles a stream to production-rate measurements, GOR/GVF or component-flow targets. | `setGasRate`, `setWaterRate`, `setGOR`, `setGVF`, `getGOR`, `getGFV`, `setAdjustedFlowRates` |
| `BroydenAccelerator`, `MultiVariableAdjuster` | `process.equipment.util` | Accelerates fixed-point iterations and solves coupled adjustable-variable targets. | `accelerate`, `initialize`, `addAdjustedVariable`, `addTargetSpecification`, `setVariableBounds`, `isConverged` |
| `CalculatorLibrary`, `EmissionsCalculator` | `process.equipment.util` | Provides adjuster presets and emissions calculations for a stream or separator. | `byName`, `preset`, `energyBalance`, `dewPointTargeting`, `calculate`, `getCO2EmissionRate` |
| `ProductionOptimizer`, `SQPoptimizer`, `MultiObjectiveOptimizer`, `ProcessSimulationEvaluator`, `BatchStudy`, `MonteCarloSimulator` | `process.util.optimizer` | Algorithm families for single/multi-objective search, DoE, external bridges and uncertainty. | Use `neqsim-optimization-and-doe` for verified selection and recipes. |

## Build pattern

This example builds one compressor flow path, establishes an explicit rated-power basis, runs it, reads the bottleneck snapshot, and changes outlet pressure through a validated write. A design value supplied in code is illustrative; replace it with installed/vendor evidence before using the resulting utilization as an engineering limit.

```java
import neqsim.process.automation.ProcessAutomation;
import neqsim.process.automation.WriteValidationResult;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class CapacitySnapshotExample {
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 30.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(100000.0, "kg/hr");
    feed.setTemperature(25.0, "C");
    feed.setPressure(30.0, "bara");
    Compressor compressor = new Compressor("K-101", feed);
    compressor.setOutletPressure(60.0, "bara");
    compressor.getMechanicalDesign().setMaxDesignPower(500.0);

    ProcessSystem process = new ProcessSystem("compression");
    process.add(feed);
    process.add(compressor);
    process.run();
    System.out.println(process.getUtilizationSnapshotJson());
    System.out.println(process.findBottleneck().getConstraintName());

    ProcessAutomation automation = process.getAutomation();
    WriteValidationResult validation = automation.setVariableValueValidated(
        "K-101.outletPressure", 80.0, "bara");
    if (!validation.isAllowed()) {
      throw new IllegalArgumentException(validation.getMessage());
    }
    process.run();
  }
}
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim
import json

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
ProcessSystem = jneqsim.process.processmodel.ProcessSystem

fluid = SystemSrkEos(298.15, 30.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
feed = Stream("feed", fluid)
feed.setFlowRate(100000.0, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(30.0, "bara")
compressor = Compressor("K-101", feed)
compressor.setOutletPressure(60.0, "bara")
compressor.getMechanicalDesign().setMaxDesignPower(500.0)
process = ProcessSystem("compression")
process.add(feed)
process.add(compressor)
process.run()
snapshot = json.loads(str(process.getUtilizationSnapshotJson()))
print(snapshot["bottleneck"], snapshot["anyHardLimitExceeded"])

automation = process.getAutomation()
validation = automation.setVariableValueValidated("K-101.outletPressure", 80.0, "bara")
if not validation.isAllowed():
    raise ValueError(str(validation.getMessage()))
process.run()
```

For multiple coordinated writes, prefer `setValuesTransactional(updates, unit)` over repeated setters. Supply a `LinkedHashMap<String, Double>` in Java to preserve request order; use `null` unit when addresses use different default units. Inspect `isCommitted()`, `getRollbackCategory()`, and every `WriteOutcome`. In Python, use the Java `LinkedHashMap` exposed through the Java bridge if batching, or make separate `setVariableValueValidated` calls followed by one run; do not confuse `setValues(...)` with a transaction.

To record bottleneck migration, after each converged model state call `tracker.record(time, label, process.findBottleneck())`. The `time` coordinate is caller-defined; the tracker does not advance the process or establish chronological order for you.

## Result extraction

- `CapacityConstraint.getUtilization()` is a fraction: `1.0` means 100% of its design value; values above `1.0` exceed that design reference. Constraints can use maximum or minimum limits, so read `getUnit()`, `getCurrentValue()`, `getDesignValue()`, `getMinValue()`/`getMaxValue()`, `getType()`, `getSeverity()`, `getSource()`, `getSourceReference()` and `getDataSource()` together.
- `BottleneckResult.getUtilization()` is a fraction; `getUtilizationPercent()` is percent. `getMargin()` is remaining fraction (positive means headroom), and `isExceeded()`/`isNearLimit()` give explicit flags.
- `ProcessSystem.getCapacityUtilizationSummary()` maps equipment name to utilization **percent**. The snapshot JSON uses fractions in `maxUtilization`/constraint `utilization`, and percentages in corresponding `*Percent` fields; the result includes `schemaVersion`, `units`, `bottleneck`, `anyOverloaded` and `anyHardLimitExceeded`.
- `ProcessModel` snapshots add an `area` and use qualified bottleneck names where necessary. Tests confirm deterministic insertion order and distinguish equipment with duplicate names in separate areas.
- `BottleneckTracker.Snapshot.getUtilizationPercent()` is percent. `getMigrationEvents()`, `getMigrationCount()`, `getPeakSnapshot()` and `toJson()` summarize identity changes and peak loading. Time units remain caller-defined.
- `CompressorResponse` fields include pressure bara, temperature °C, power kW, massflow kg/hr, standard flow Sm3/hr, volume flow m3/hr, polytropic head/efficiency and speed. These are public data fields; serialize with your chosen JSON library after constructing a response.
- `ScenarioKPI` stores caller-supplied values with documented units (pressure bara, temperature °C, rates kg/hr, production loss kg, flare Nm3, CO2 kg, energy kWh, currency USD and time seconds). `KPIDashboard.addScenario` stores records; `printDashboard` prints rather than returning JSON.
- `ProductionRateFitter.setGasRate(rate, unit)` accepts standard-volume examples such as MSm3/day, Sm3/day, Sm3/hr or Sm3/sec. `setWaterRate(rate, unit)` accepts Sm3/day, Sm3/hr or Sm3/sec. Its output is the unit's outlet stream; it does not return a separate fit-result object.
- `GORfitter.getGOR()` reports GOR; its historical `getGFV()` spelling returns GVF. `MPFMfitter` exposes the same outputs and supports `setReferenceFluidPackage`; prefer its non-deprecated `(String, StreamInterface)` constructor.

## Gotchas

- `getUtilizationSnapshotJson()` is side-effect-free and does not run the model. Run/converge first; the API reports stored/current constraint data, not a fresh simulation.
- Default equipment strategies may use incomplete/default design inputs. Inspect each limit's unit, enabled state, source, `sourceReference`, `dataSource`, confidence and validity range. A default/fallback is not vendor or installed capacity evidence.
- Utilization and summary scales differ: `CapacityConstraint.getUtilization()` and snapshot `utilization` are fractions; `ProcessSystem.getCapacityUtilizationSummary()` and `BottleneckResult.getUtilizationPercent()` are percentages. Avoid comparing them without conversion.
- A no-chart compressor has chart-dependent surge/speed constraints disabled; tests verify that power utilization can still be reported and becomes more loaded as flow rises. Do not interpret absence of a chart constraint as proof of surge margin.
- `setValues(...)` continues after bad addresses and can partially apply; it returns a successful-write count. `setValuesTransactional(...)` validates the full batch, applies, runs and rolls back on failure. Previously unreadable input values have no snapshot and are skipped during rollback.
- `setVariableValueSafe` is for address/bounds diagnostics; `setVariableValueValidated` invokes typed equipment checks. Neither should be mistaken for an engineering feasibility check or automatic process convergence.
- `EmpiricalCarryOverConstraint` treats units as labels: caller must align the driver, calibration x-values, carry-over y-values and limit. Values above the last x-point extrapolate linearly using the final segment; evidence must support that extrapolation.
- `ProductionRateFitter` matches gas rate by scaling total hydrocarbon flow and then adjusts water. If the feed has no water component, water matching is skipped; if no gas phase exists at standard conditions, gas-rate fitting is skipped. `GORfitter`/`MPFMfitter` retain total mass flow for their GOR/GVF adjustment; these are not equivalent reconciliation objectives.
- `BroydenAccelerator` returns direct fixed-point output during its initial delay iterations and does not test convergence itself. Handle damping, bounds, residual criteria and failure recovery in the owning solver.

## Validation / benchmarks

- `UtilizationSnapshotTest` verifies schema version, unit records, process-area labels, duplicate-name qualification, deterministic JSON and chartless-compressor constraint behavior.
- `CapacityConstraintMetadataTest` verifies unset confidence/validity metadata, inclusive validity boundaries and that metadata does not alter utilization math. `CapacityConstraintCurrentValueTest` and `CapacityConstraintMinimumLimitTest` cover sampled values and minimum-bound constraints.
- `BottleneckTrackerTest` verifies empty results, migration identity, peak percent and JSON keys. Record snapshots only after the corresponding process state is converged.
- `ProcessAutomationTransactionalTest` verifies successful commits, validation rollback and stable JSON fields. `WriteValidatorRegistryTest` covers registry dispatch; `AutomationDiagnosticsTest` covers address/value diagnostics.
- Fit production measurements against independently reconciled plant data. Check gas standard conditions, reference conditions, phase presence and water basis before comparing rates; internal regression tests are not a field-data benchmark.
- When a capacity result drives an operating/design decision, supply a documented basis and compare the limiting condition with vendor, installed, empirical or otherwise approved data. A utilization snapshot is not a safety assessment.

## Related skills

- `neqsim-agentic-process-optimization` — adjustable process inputs, convergence gates, trial feasibility and objectives.
- `neqsim-optimization-and-doe` — optimizer families, DoE, sweeps, Pareto and uncertainty.
- `neqsim-controllability-operability` — operating envelopes, turndown and loop response.
- `neqsim-process-modeling` — process construction and converged process results.
- `neqsim-model-calibration-and-data-reconciliation` — measured data quality, calibration and residual analysis.
- `neqsim-production-optimization` — production surveillance, rate allocation and profile fitting.
- `neqsim-operational-risk-and-safety-validation` — safety-system scenario interpretation and review boundaries.