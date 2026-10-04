---
title: Production Optimization Guide
description: This guide provides comprehensive examples for setting up and running production optimization simulations in NeqSim, covering both Java and Python implementations.
---

> **New to process optimization?** Start with the [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

This guide provides comprehensive examples for setting up and running production optimization simulations in NeqSim, covering both Java and Python implementations.

---

## What's New (January 2026)

### Behavior Changes
- **Constraints Disabled by Default**: Separator, valve, pipeline, pump, and manifold constraints are now disabled by default for backward compatibility. Use `enableAllConstraints()`, `useEquinorConstraints()`, or `useAPIConstraints()` to enable constraint-based capacity analysis. The optimizer automatically falls back to traditional capacity methods when no constraints are enabled.

### Bug Fixes
- **Golden Section Ratio**: Fixed inconsistent phi formula and comparison logic
- **Nelder-Mead Bounds**: Added clamping for reflected/contracted simplex points
- **Zero Flow Validation**: Added check for zero/invalid flow rates
- **Feasibility Scoring**: Fixed penalty calculation to use actual utilization limits

### New Features
- **Configuration Validation**: `config.validate()` checks bounds, tolerance, and iterations
- **Stagnation Detection**: `stagnationIterations(int)` for early termination (default: 5)
- **Warm Start**: `initialGuess(double[])` to start near known good solutions
- **LRU Cache Control**: `maxCacheSize(int)` to limit memory usage (default: 1000)
- **Infeasibility Diagnostics**: `result.getInfeasibilityDiagnosis()` for detailed violation reports
- **Batch Constraint Control**: `equipment.disableAllConstraints()` and `enableAllConstraints()` for what-if analysis
- **Process-Wide Constraint Control**: `processSystem.disableAllConstraints()` controls all equipment at once
- **Whole-Plant Capacity Analysis (ProcessModel)**: Multi-area plants expose the same capacity API as `ProcessSystem` — `getBottleneck()`, `getBottleneckUtilization()`, `findBottleneck()`, `getConstrainedEquipment()`, `isAnyEquipmentOverloaded()`, `isAnyHardLimitExceeded()`, `getCapacityUtilizationSummary()`, `getEquipmentNearCapacityLimit()`, `disableAllConstraints()`, `enableAllConstraints()` — aggregated across all areas
- **Multi-Area Optimization**: `ProductionOptimizer.optimize(...)`, `optimizePareto(...)`, and `ScenarioRequest` all accept a `ProcessModel` directly — single/multi-variable, multi-objective (Pareto), and scenario comparison run on whole multi-area plants and report the plant-wide bottleneck
- **Full Equipment Exclusion**: Equipment with `setCapacityAnalysisEnabled(false)` is now fully excluded from optimization feasibility checks

---

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW) | **START HERE**: When to use which optimizer |
| [Optimizer Plugin Architecture](../process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE) | ProcessOptimizationEngine and equipment strategies |
| [Multi-Objective Optimization](../process/optimization/multi-objective-optimization) | Pareto fronts and trade-offs |
| [Flow Rate Optimization](../process/optimization/flow-rate-optimization) | FlowRateOptimizer and lift curves |
| [Batch Studies](../process/optimization/batch-studies) | Parallel parameter sweeps |
| [Industrial Process Optimization Baseline](../process/optimization/industrial-process-optimization-baseline) | Capability coverage, restriction gaps, and frozen large-plant benchmark contract |
| [Industrial S/M Benchmark Evidence](../process/optimization/industrial-sm-benchmark) | Executed small guide and 27-unit multi-train recycle baseline with a checked-in five-fork runner, exact raw records, and validation |
| [External Optimizer Integration](../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | Python/SciPy integration |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](../process/CAPACITY_CONSTRAINT_FRAMEWORK) | Multi-constraint equipment and bottleneck detection |

---

## Register plant-wide restrictions before solving

Large plants have restrictions that do not belong to one equipment item: total power and utilities,
common-shaft compressor trains, gathering and export networks, area limits, product quality, emissions,
produced-water handling, flare capacity, availability, and nomination limits. Use
`PlantConstraintRegistry` to give each restriction a stable identity and explicit engineering basis.

The registry complements equipment constraints for compressor maps, surge/choke, speed, temperature
and power; separator gas/oil/water capacity, residence and settling; and piping pressure, velocity,
erosion, FIV, thermal and phase limits. It does not infer operating authority from advisory safety or
integrity calculations.

```java
PlantConstraintRegistry registry = new PlantConstraintRegistry();
registry.register(PlantConstraintDefinition
    .builder("export-pressure", PlantConstraintScope.stream("Plant", "Export", "Gas export"))
    .unit("bara")
    .basis("absolute pressure at export battery limit")
    .provenance("sales-gas agreement rev 3")
    .category(PlantConstraintDefinition.Category.COMMERCIAL)
    .build());
```

Treat this as registration, not solved evidence. After a successful full-process solve, create one
`PlantConstraintSample` per enabled definition and assemble them with
`PlantUtilizationSnapshot.builder(registry, calculationId)`. The immutable result retains every
registered row, including disabled and unavailable restrictions, and exposes a deterministic
`getBottleneckLadder()` for Java and JPype reporting. `isComplete()` is false for missing, stale,
out-of-validity, non-finite, calculation-ID-mismatched, metadata-mismatched, exception, or incomplete-
convergence evidence. `isFeasible()` additionally requires that no complete hard or critical row is
violated. Soft and advisory violations remain visible without being promoted to hard infeasibility.

Reuse `PlantConstraintSample.fromInstalledEquipmentEvidence(...)` and
`fromProcessBoundaryEvidence(...)` for existing #2941 installed-capacity and boundary evidence. The
adapters copy already sampled immutable values; they do not re-run equipment suppliers or the
process model. See the
[Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK#plant-wide-constraint-registration)
for aggregation and conversion rules.

### Common capacity inputs and expected coverage

Use `ProcessSystem.applyDesignCapacities(Map<String, Map<String, Object>>)` for direct equipment
names, or the same method on `ProcessModel` with `Area::Equipment` keys. These additive Java/JPype
methods validate the complete batch before applying the existing normalized `designCapacities`
properties. They reject unknown targets, ambiguous names, unsupported properties and non-finite or
non-positive ratings. Valid input uses the same `EquipmentDesignData` application as JSON process
construction. The existing JSON builder retains its advisory per-equipment error reports for
compatibility. See [Equipment Design Parameters](../process/EQUIPMENT_DESIGN_PARAMETERS) for the
supported inputs and units.

Configuration does not prove that every relevant plant restriction was specified. Declare the
expected equipment and constraints independently, then inspect `UtilizationCoverageReport`. The
report resolves direct constraints before same-name strategy defaults, retains unrelated strategy
constraints, and distinguishes a real observed zero from an absent value. Missing limits, units,
basis or provenance and screening-only defaults prevent complete rated coverage. Unavailable
numbers are `NaN` in Java and `null` in `toJson()`; they are never a false zero-percent utilization.

For a registry-bound report, supply its immutable preflight to the snapshot:

```java
UtilizationCoverageReport coverage = UtilizationCoverageReport.builder("Plant")
    .expectConstraint("Compression", "K-1", "power")
    .equipment("Compression", compressor)
    .registry(registry)
    .build();
PlantUtilizationSnapshot snapshot = PlantUtilizationSnapshot.builder(registry, calculationId)
    .expectedCoverage(coverage)
    .convergenceComplete(fullModelConverged)
    .sample(powerSample)
    .build();
```

The compressor and every required constraint must match the independently prepared registry;
`powerSample` must be exact-calculation evidence. An incomplete expected scope or changed registry
cannot become complete by omission. This path requires an explicit convergence declaration and
does not sample equipment again. `PlantUtilizationSnapshotTest` verifies these calls, omitted
constraints, registry changes, serialization and single-sample behavior. Snapshot schema 1.1 adds
the optional expected-coverage report. The legacy builder without that report still describes
registered rows only. Neither preflight nor the snapshot infers the unlisted engineering scope or
restores a mutated process model.

The `ProcessModelOptimizationView` used by `ProductionOptimizer` now throws
`IllegalStateException` when cross-area convergence fails, before any objective/capacity evaluation.
Callers must reject that trial and restore or discard their isolated candidate model. The exception
does not claim automatic rollback. Successful multi-area, Pareto and scenario workflows retain
their established path. A snapshot explicitly declaring failed convergence is incomplete even
when its registry is empty or every constraint is disabled.

### Qualify a shared total-power budget

After the complete isolated candidate has converged, use `PlantSharedResourceEvidence` to collect
exact participant coverage and create the common snapshot sample. Choose the physical basis first:

- `fromProcessModelShaftPower(...)` and `fromProcessSystemShaftPower(...)` report compressor and
  pump **shaft power** from the existing process APIs.
- `fromSolvedEnergyBusRequestedDemand(...)` reports **requested electrical load**, including unmet
  demand, from a current solved electrical `EnergyBus`.

These bases are deliberately not interchangeable. NeqSim does not infer driver or motor efficiency.
Declare any unit conversion explicitly in each `PlantConstraintParticipant`.

```java
PlantConstraintDefinition totalPower = PlantConstraintDefinition
    .builder("total-power",
        PlantConstraintScope.sharedResource("Plant", "compression shaft power"))
    .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
    .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
    .unit("kW")
    .basis("compressor and pump shaft power")
    .provenance("approved power budget")
    .participant(PlantConstraintParticipant.direct(
        "Compression", "kW", "compressor and pump shaft power"))
    .build();

PlantSharedResourceEvidence evidence =
    PlantSharedResourceEvidence.fromProcessModelShaftPower(
        totalPower, calculationId, 12000.0, model, fullModelConverged,
        "completed isolated ProcessModel");
if (!evidence.isComplete()) {
  throw new IllegalStateException(evidence.getDiagnostics().toString());
}
```

Java getters, `toJson()`, and JPype expose the same frozen calculation ID, participants, source and
converted values, conversion metadata, total cross-check, units, basis, provenance, utilization,
physical margin, required relief, and diagnostics. Missing observations remain unavailable rather
than becoming zero utilization. Rebuild evidence after any process, availability, bus, or limit
change; never reuse it for a different candidate.

### Qualify a common-shaft compressor train

After all casings and their shared `MechanicalShaft` have completed the same isolated candidate,
freeze the train's common speed, shaft power, driver, gearbox, torque, and casing-map evidence:

```java
PlantCommonShaftEvidence trainEvidence = PlantCommonShaftEvidence
    .builder("Plant", "Compression", "export train", calculationId, shaft,
        "completed isolated candidate")
    .casing(casingAPort.getParticipantId(), casingA)
    .casing(casingBPort.getParticipantId(), casingB)
    .driver(driverPort.getParticipantId(), driver)
    .gearbox("export-train-gearbox", gearbox)
    .speedToleranceRpm(1.0)
    .powerBalanceToleranceKw(1.0e-6)
    .maximumTorqueNm(8000.0)
    .convergenceComplete(fullModelConverged)
    .build();

if (!trainEvidence.isComplete() || !trainEvidence.isFeasible()) {
  throw new IllegalStateException(trainEvidence.getDiagnostics().toString());
}
PlantUtilizationSnapshot trainSnapshot =
    trainEvidence.toPlantUtilizationSnapshot();
```

`calculationId` is the UUID string stored on every completed casing. Casing power must equal that
casing's exact shaft-input request. Gearbox input includes its configured idle loss and efficiency.
The gearbox maximum input power is configured in W, while the driver model and evidence report use
kW. `maximumTorqueNm` is an independently approved limit, not
a value estimated by the adapter. A line-up or limit change invalidates the old evidence; solve the
isolated candidate again and build a new snapshot. Through JPype, use the same callback-free builder
and `toJson()` rather than supplying Python callbacks.

### Qualify separator observations without fallback values

After the complete process candidate has converged, use `PlantSeparatorEvidence` to freeze the
applicable separator calculations. Configure the vessel and mechanical-design elevations first;
the strict adapter rejects missing inlet-nozzle diameter, HLL/NLL/NIL, and effective gas/liquid
length instead of using the convenience estimates in `Separator`.

```java
PlantSeparatorEvidence separatorEvidence = PlantSeparatorEvidence
    .builder("Plant", "Separation", calculationId, separator,
        PlantSeparatorEvidence.Profile.TWO_PHASE_OIL,
        "approved separator rating revision 4")
    .maximumLiquidLevelFraction(0.80)
    .convergenceComplete(fullModelConverged)
    .build();

if (!separatorEvidence.isComplete() || !separatorEvidence.isFeasible()) {
  throw new IllegalStateException(separatorEvidence.getDiagnostics().toString());
}
String separatorJson = separatorEvidence.toJson();
```

For a three-phase vessel, select `THREE_PHASE` and also call
`minimumInterfaceSettlingMinutes(...)`. Two-phase oil and water profiles require only their
applicable residence observation; a gas scrubber does not create a zero-valued liquid-residence
row. Missing phases, stale calculation identity, non-finite results, or failed convergence are
unavailable and fail closed. Through JPype, call the same builder and inspect `toJson()`; unavailable
numbers are JSON `null`.

The adapter reuses the current public separator calculations. It does not validate or invent
carry-over/carry-under correlations, slug capacity, relief limits, or operating approval. Register
those limits only from qualified provider or measured evidence and retain their provenance.

### Qualify a solved pipeline against explicit installed limits

Use `PlantPipelineEvidence` only after the complete candidate and its
`PipeBeggsAndBrills` equipment have finished with the same calculation UUID. Supply the line-list
geometry provenance and each approved pressure, receiving-boundary, velocity, and temperature
limit; no convenience limit is substituted.

```java
PlantPipelineEvidence pipelineEvidence = PlantPipelineEvidence
    .builder("Plant", "Export", calculationId, exportPipeline,
        "line list revision 7 and export specification")
    .geometryVerified(true)
    .maximumPressureBara(160.0)
    .maximumPressureDropBar(15.0)
    .minimumReceivingPressureBara(125.0)
    .maximumMixtureVelocityMetresPerSecond(12.0)
    .minimumTemperatureCelsius(-10.0)
    .maximumTemperatureCelsius(60.0)
    .convergenceComplete(fullModelConverged)
    .build();

if (!pipelineEvidence.isComplete() || !pipelineEvidence.isFeasible()) {
  throw new IllegalStateException(pipelineEvidence.getDiagnostics().toString());
}
String pipelineJson = pipelineEvidence.toJson();
```

The JSON evidence rows include the exact sampled value, installed limit, unit, basis, normalized
utilization, physical margin, profile node, distance, and diagnostic. Missing geometry attestation,
profiles, ratings, convergence, or exact calculation identity fails closed with JSON `null`
numbers. API RP 14E, Rhone-Poulenc, FIV/FRMS/AIV, hydrate/wax, slug, and transient results are not
silently promoted to verified optimization constraints.

### Compile a fail-closed evaluation plan

Use `ProcessModelCompiledEvaluationPlan` around a configured
`ProcessModelOperatingActionSetEvaluator` before handing repeated candidates to an external solver.
Compilation evaluates the unchanged action vector and freezes model and area object identities,
the exact area order and
`ProcessSystem` structure versions, action and required-hydraulic definitions, evaluator
configuration, installed ratings, and expected installed-capacity and process-boundary identities.

```java
ProcessModelCompiledEvaluationPlan plan =
    ProcessModelCompiledEvaluationPlan.compile(
        "production-allocation-v1",
        "Compiled production allocation",
        "approved operating envelope revision 4",
        "NeqSim master and plant configuration revision 2026-09-11",
        transactionalEvaluator);

ProcessModelCompiledEvaluationPlan.EvaluationResult candidate =
    plan.evaluate(new double[] {11000.0});
if (!candidate.isAccepted()) {
  throw new IllegalStateException(candidate.getDiagnostics().toString());
}
String authoritativeJson = candidate.toJson();
```

`transactionalEvaluator` is a fully configured
`ProcessModelOperatingActionSetEvaluator`: it must contain at least one exact hydraulic binding,
and its underlying evaluator must already contain objectives and every required process-boundary
constraint. Do not add objectives, constraints, actions, equipment, or ratings after compilation.
`plan.isCurrent()` and `getStalenessDiagnostics()` are preflight views only; `evaluate(...)` repeats
the same check immediately before model mutation.

JPype uses the same Java authority and returns the same strict JSON. `plan.toJson()` includes the
qualified no-change compilation evidence; each evaluation result includes its complete delegated
candidate evidence. Java non-finite values are represented as JSON `null`, not invalid `NaN` or
`Infinity` tokens.

```python
CompiledPlan = jneqsim.process.util.optimizer.ProcessModelCompiledEvaluationPlan
candidate_values = jpype.JArray(jpype.JDouble)([11000.0])
plan = CompiledPlan.compile(
    "production-allocation-v1",
    "Compiled production allocation",
    "approved operating envelope revision 4",
    "NeqSim master and plant configuration revision 2026-09-11",
    transactional_evaluator,
)
candidate = plan.evaluate(candidate_values)
if not candidate.isAccepted():
    raise RuntimeError(str(candidate.getDiagnostics()))
authoritative_json = str(candidate.toJson())
```

A non-finite or incorrectly sized vector is rejected before a process run. A changed topology,
action, binding, evaluator definition, installed rating, or availability makes the compiled plan
stale before candidate mutation. A physically violated candidate retains its complete immutable
evidence but is not accepted. Any incomplete restoration overrides the candidate classification.
Replacing a model or removing and recreating an area requires recompilation even when names and
structure counters match, because callbacks and stream connections may still reference the original objects.
Shared mutable equipment is never evaluated concurrently; compile independent plans on independent
model instances for parallel candidate work.

The executable Java scale harness is
`CompiledProcessModelEvaluationPlanBenchmark`. Its default fixture contains 162 units across 20
areas and writes compilation/evaluation time, main-thread allocation, strict result size, exact
coverage, convergence, restoration, and mass closure without a CI wall-time assertion.

---

## Overview

NeqSim provides a powerful production optimization framework that combines:

| Component | Description |
|-----------|-------------|
| **ProductionOptimizer** | Core optimization engine with multiple search algorithms |
| **CapacityConstrainedEquipment** | Multi-constraint interface for equipment limits |
| **ProcessSystem.getBottleneck()** | Unified bottleneck detection (single & multi-constraint) |
| **ProcessSystem.findBottleneck()** | Detailed constraint analysis with remediation hints |
| **ProcessModel** (multi-area) | Same whole-flowsheet capacity API as `ProcessSystem`, aggregated across all process areas |

### Key Features

- **Multiple search algorithms**: Binary feasibility, Golden-section, Nelder-Mead, Particle-swarm, Gradient descent
- **Hard & soft constraints**: Enforce limits or penalize violations
- **Equipment-specific utilization limits**: Configure per equipment or type
- **Scenario comparison**: Run multiple what-if scenarios
- **JSON reporting**: Machine-readable optimization results
- **Early termination**: Stagnation detection for faster convergence
- **Warm start**: Start optimization near known good solutions
- **Bounded caching**: LRU cache with configurable size limit
- **Infeasibility diagnostics**: Detailed reports when optimization fails

---

## Quick Start (Java)

### Basic Production Rate Optimization

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// 1. Create fluid system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.02);
fluid.setMixingRule("classic");

// 2. Build process
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Well Feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
process.add(feed);

Separator separator = new Separator("HP Separator");
separator.setInletStream(feed);
separator.setDesignGasLoadFactor(0.107); // Souders-Brown K-factor gas load limit [m/s]
process.add(separator);

Compressor compressor = new Compressor("Gas Compressor");
compressor.setInletStream(separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
compressor.setMaximumSpeed(11000.0);  // RPM limit
compressor.initMechanicalDesign();
compressor.getMechanicalDesign().setMaxDesignPower(500.0);  // kW power limit
process.add(compressor);

process.run();

// 3. Configure optimization
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)  // kg/hr range
    .rateUnit("kg/hr")
    .tolerance(10.0)
    .maxIterations(20)
    .defaultUtilizationLimit(0.95)  // 95% max utilization
    .utilizationLimitForType(Compressor.class, 0.90)  // 90% for compressors
    .searchMode(SearchMode.BINARY_FEASIBILITY);

// 4. Run optimization (optimize() is an instance method; objectives/constraints may be null)
ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationResult result = optimizer.optimize(process, feed, config, null, null);
if (!result.isFeasible()) {
    throw new IllegalStateException(result.getInfeasibilityDiagnosis());
}
feed.setFlowRate(result.getOptimalRate(), result.getRateUnit());
process.run();

// 5. Report results
logger.info("{}", "Optimal production rate: " + result.getOptimalRate() + " " + result.getRateUnit());
logger.info("{}", "Bottleneck: " + (result.getBottleneck() == null ? "None" : result.getBottleneck().getName()));
logger.info("{}", "Bottleneck utilization: " + (result.getBottleneckUtilization() * 100) + "%");
logger.info("{}", "Feasible: " + result.isFeasible());
```

---

## Equipment Constraints and Active Bottlenecks

### Which Equipment Can Restrict Production?

**Any equipment implementing `CapacityConstrainedEquipment` can become the bottleneck**, not just compressors. The optimizer checks ALL equipment constraints and reports the one limiting production.

| Equipment | Implemented Constraints | Typical Bottleneck Scenarios |
|-----------|------------------------|------------------------------|
| **Separator** | `gasLoadFactor`, `liquidResidenceTime` | High gas rates, high liquid rates |
| **Compressor** | `speed`, `power`, `surgeMargin`, `stonewallMargin` | High compression duty, variable conditions |
| **Pump** | `npshMargin`, `power`, `flowRate` | High liquid rates, cavitation risk |
| **ThrottlingValve** | `valveOpening`, `cvUtilization` | Large pressure drops, high flows |
| **Pipeline** | `velocity`, `pressureDrop`, `FIV_LOF`, `FIV_FRMS` | Long pipelines, high velocities |
| **Heater/Cooler** | `duty`, `outletTemperature` | High thermal loads |

### What is an "Active Constraint"?

An **active constraint** is the specific equipment limit that prevents increasing production further. It's the "binding" constraint at the current operating point.

```java
// After optimization, identify the active constraint
ProcessEquipmentInterface bottleneck = result.getBottleneck();

if (bottleneck instanceof CapacityConstrainedEquipment) {
    CapacityConstrainedEquipment constrained = (CapacityConstrainedEquipment) bottleneck;
    CapacityConstraint active = constrained.getBottleneckConstraint();
    if (active == null) {
        throw new IllegalStateException("No enabled direct constraint on bottleneck");
    }

    logger.info("{}", "=== ACTIVE CONSTRAINT ===");
    logger.info("{}", "Equipment: " + bottleneck.getName());
    logger.info("{}", "Constraint: " + active.getName());
    logger.info("{}", "Current value: " + active.getCurrentValue() + " " + active.getUnit());
    logger.info("{}", "Design limit: " + active.getDisplayDesignValue() + " " + active.getUnit());
    logger.info("{}", "Utilization: " + active.getUtilizationPercent() + "%");
    logger.info("{}", "Type: " + active.getType());      // HARD, SOFT, or DESIGN
    logger.info("{}", "Severity: " + active.getSeverity());  // CRITICAL, HARD, SOFT, or ADVISORY
}
```

**Example scenarios where different equipment is the bottleneck:**

| Scenario | Bottleneck | Active Constraint | Reason |
|----------|------------|-------------------|--------|
| High GOR well | Separator | gasLoadFactor | K-factor limit exceeded |
| Low reservoir pressure | Compressor | power | Compressor at max driver power |
| Long export line | Pipeline | velocity | Erosional velocity limit |
| High water cut | Pump | npshMargin | Insufficient NPSH available |
| Restricted outlet | Valve | valveOpening | Valve fully open (90%) |
| Cold ambient | Heater | duty | Maximum heating capacity |

### How Constraints Are Established

Constraints come from three sources:

#### 1. Auto-Sizing (Recommended)

When you call `autoSize()`, constraints are automatically created based on design calculations:

```java
// Separator - sets gasLoadFactor constraint from K-factor sizing
Separator sep = new Separator("HP-Sep", feed);
sep.autoSize(1.2);  // Creates constraint: gasLoadFactor = design K-factor

// Compressor - sets speed, power, surge constraints + generates curves
Compressor comp = new Compressor("Export", gasStream);
comp.setOutletPressure(100.0);
comp.autoSize(1.2);  // Creates constraints: speed, power, surgeMargin
                     // Also generates compressor curves and sets solveSpeed=true

// Valve - sets valveOpening and Cv constraints
ThrottlingValve valve = new ThrottlingValve("HP-Valve", stream);
valve.setOutletPressure(30.0);
valve.autoSize(1.2);  // Creates constraints: valveOpening, cvUtilization
```

#### 2. Manual Configuration

Set limits directly on equipment:

```java
// Compressor limits
Compressor comp = new Compressor("K-100", stream);
comp.setMaximumSpeed(11000.0);    // Creates HARD speed constraint
comp.initMechanicalDesign();
comp.getMechanicalDesign().setMaxDesignPower(2000.0);  // Creates HARD power constraint (kW)
// Surge / stonewall margin constraints are created from the performance curve
// (call comp.autoSize(...) or comp.setCompressorChart(...) to populate them)

// Separator limits
Separator sep = new Separator("V-100", feed);
sep.setDesignGasLoadFactor(0.08);  // Creates DESIGN gasLoadFactor constraint

// Pipeline limits (velocity + FIV LOF/FRMS constraints are created by autoSize)
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("L-100", stream);
pipe.setLength(2000.0);
pipe.setDiameter(0.5);
pipe.autoSize(1.2);                // Creates velocity + FIV (LOF, FRMS) constraints
```

#### 3. Mechanical Design Integration

When `initMechanicalDesign()` is called, constraints use design values:

```java
Separator sep = new Separator("HP-Sep", feed);
sep.initMechanicalDesign();
sep.getMechanicalDesign().setMaxDesignGassVolumeFlow(5000.0);  // m³/hr
sep.getMechanicalDesign().setMaxOperationPressure(100.0); // bara
// The gas-flow limit feeds the legacy capacity getter.
// Pressure alone does not create an optimizer constraint; add an explicit one if required.
```

### Important: Constraints Are Disabled by Default

> **⚠️ Backward Compatibility**: Most equipment types have constraints **disabled by default** to maintain backward compatibility. The optimizer will automatically fall back to traditional capacity methods when no enabled constraints exist.

**Equipment with Disabled Constraints by Default:**
- Separator, ThreePhaseSeparator (except GasScrubber which enables K-value)
- ThrottlingValve
- Pipeline, PipeBeggsAndBrills, AdiabaticPipe
- Pump
- Manifold

**Compressor defaults:**
- Power/rated-power constraints are enabled; speed and surge/stonewall constraints require an active chart. Setting a speed ceiling alone does not define a compressor map.

**To enable constraints for capacity analysis:**

```java
// Separators - use pre-configured sets
separator.useEquinorConstraints();  // Equinor TR3500 standards
separator.useAPIConstraints();      // API 12J standards
separator.useAllConstraints();      // All constraint types

// Or enable all constraints on any equipment
separator.enableAllConstraints();
valve.enableAllConstraints();
pipeline.enableAllConstraints();

// Check if constraints are enabled
boolean hasEnabled = equipment.getCapacityConstraints().values().stream()
    .anyMatch(CapacityConstraint::isEnabled);
```

For detailed information, see [Capacity Constraint Framework - Constraints Disabled by Default](../process/CAPACITY_CONSTRAINT_FRAMEWORK#important-constraints-disabled-by-default).

### Constraint Types and Their Behavior

| Type | Meaning | Optimization Behavior | Example |
|------|---------|----------------------|---------|
| **HARD** | Physical or safety limit - cannot exceed | Optimization stops before exceeding | Compressor trip speed, vessel MAWP |
| **SOFT** | Operational capacity limit | Enabled capacity utilization still enters the optimizer ceiling | Efficiency degradation zone |
| **DESIGN** | Normal operating envelope | Target for optimal operation | Design K-factor, rated capacity |

### Disabling Constraints for What-If Analysis

You can disable constraints at three levels for what-if scenarios or focused analysis:

#### 1. Disable Individual Constraint

```java
// Get a specific constraint and disable it
Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();
constraints.get("surgeMargin").setEnabled(false);  // Disable just surge constraint

// Re-enable later
constraints.get("surgeMargin").setEnabled(true);
```

#### 2. Disable All Constraints on One Equipment

```java
// Disable all constraints on a single equipment
int disabled = compressor.disableAllConstraints();
logger.info("{}", "Disabled " + disabled + " constraints on compressor");

// Re-enable all constraints
int enabled = compressor.enableAllConstraints();
```

#### 3. Disable All Constraints in ProcessSystem or ProcessModel

```java
// Disable all constraints on ALL equipment in the process
int total = processSystem.disableAllConstraints();
logger.info("{}", "Disabled " + total + " constraints across the process");

// Re-enable all constraints
processSystem.enableAllConstraints();

// For multi-area plants, ProcessModel exposes the same API and aggregates
// the action across every process area it contains
int totalPlant = processModel.disableAllConstraints();
processModel.enableAllConstraints();
```

> **Multi-area plants:** `ProcessModel` mirrors the whole-flowsheet capacity API of
> `ProcessSystem`, delegating each call across all of its process areas. See
> [Whole-Plant Capacity Analysis (ProcessModel)](#whole-plant-capacity-analysis-processmodel).

#### 4. Exclude Equipment from Optimization Entirely

To **completely exclude** an equipment from optimization feasibility checks (not just disable its constraints), use `setCapacityAnalysisEnabled()`:

```java
// Completely exclude this compressor from optimization
compressor.setCapacityAnalysisEnabled(false);

// The optimizer will skip this equipment entirely
// It won't be included in utilization summaries or bottleneck detection

// Re-include in optimization
compressor.setCapacityAnalysisEnabled(true);
```

#### Comparison: Constraint Disable vs Capacity Analysis Disabled

| Method | Effect on Equipment | Effect on Optimization |
|--------|---------------------|------------------------|
| `constraint.setEnabled(false)` | Specific constraint disabled | Falls back to other constraints or type-specific rules |
| `equipment.disableAllConstraints()` | All constraints disabled | Falls back to type-specific capacity rules |
| `equipment.setCapacityAnalysisEnabled(false)` | Equipment excluded from analysis | **Fully excluded** - no capacity checks at all |

**Use cases:**
- `disableAllConstraints()` - What-if without constraint limits, still subject to basic capacity rules
- `setCapacityAnalysisEnabled(false)` - Exclude equipment from sizing analysis entirely (e.g., utilities)

```python
# Python example for disabling constraints in optimization
from neqsim import jneqsim

# Get the equipment
# Reuse the process and compressor from Production Optimization in Python.
process_system = process
compressor = process_system.getUnit("Gas Compressor")

# Option 1: Disable all constraints but keep in optimization (uses fallback rules)
compressor.disableAllConstraints()

# Option 2: Fully exclude from optimization
compressor.setCapacityAnalysisEnabled(False)

# Process-wide: disable all constraints on all equipment
process_system.disableAllConstraints()

# Re-enable all constraints
process_system.enableAllConstraints()
```

### Full Process Example: Finding Active Constraint

```java
// Build a realistic process
ProcessSystem process = new ProcessSystem();

// Synthetic rich fluid with gas, oil and water at the separator conditions.
SystemInterface reservoirFluid = new SystemSrkEos(313.15, 50.0);
reservoirFluid.addComponent("methane", 0.75);
reservoirFluid.addComponent("n-heptane", 0.15);
reservoirFluid.addComponent("water", 0.10);
reservoirFluid.setMixingRule("classic");
reservoirFluid.setMultiPhaseCheck(true);

// Feed from reservoir
Stream wellFeed = new Stream("Well Feed", reservoirFluid);
wellFeed.setFlowRate(20000.0, "kg/hr");
wellFeed.run();

// Three-phase separation
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellFeed);
hpSep.run();
hpSep.autoSize(1.2);
hpSep.run();

// Gas compression
Compressor gasComp = new Compressor("Gas Compressor", hpSep.getGasOutStream());
gasComp.setOutletPressure(100.0);
gasComp.setUsePolytropicCalc(true);
gasComp.setPolytropicEfficiency(0.78);
gasComp.run();
// Illustrative fixed driver rating: 5% above this solved reference duty.
// This example screens power; add a validated map for speed/surge/choke studies.
gasComp.getMechanicalDesign().setMaxDesignPower(1.05 * gasComp.getPower("kW"));
gasComp.getCapacityConstraints().get("power").setMaxValue(100.0);

// Export pipeline
PipeBeggsAndBrills exportPipe = new PipeBeggsAndBrills("Export Pipeline", gasComp.getOutletStream());
exportPipe.setLength(10000.0);  // 10 km
exportPipe.setDiameter(0.4);    // 16 inch
exportPipe.setMaxDesignVelocity(20.0);
exportPipe.getCapacityConstraints().get("velocity").setEnabled(true);
exportPipe.run();

// Liquid pump
ThrottlingValve oilLetdown = new ThrottlingValve("Oil Letdown", hpSep.getOilOutStream());
oilLetdown.setOutletPressure(6.0, "bara");
oilLetdown.run();
Separator oilFlash = new Separator("Oil Flash", oilLetdown.getOutletStream());
oilFlash.run();
Pump oilPump = new Pump("Oil Pump", oilFlash.getLiquidOutStream());
oilPump.setOutletPressure(15.0, "bara");
// Pump has: npshMargin, power, flowRate constraints

process.add(wellFeed);
process.add(hpSep);
process.add(gasComp);
process.add(exportPipe);
process.add(oilLetdown);
process.add(oilFlash);
process.add(oilPump);
process.run();

// Run optimization
OptimizationConfig config = new OptimizationConfig(18000.0, 22000.0)
    .rateUnit("kg/hr")
    .tolerance(1.0)  // Resolve feed rate to 1 kg/hr.
    .defaultUtilizationLimit(1.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);

ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationObjective production = new OptimizationObjective("production",
    ps -> ((StreamInterface) ps.getUnit("Well Feed")).getFlowRate("kg/hr"),
    1.0, ObjectiveType.MAXIMIZE);
OptimizationResult result = optimizer.optimize(process, wellFeed, config,
    Collections.singletonList(production), null);
if (!result.isFeasible()) {
    throw new IllegalStateException(result.getInfeasibilityDiagnosis());
}
wellFeed.setFlowRate(result.getOptimalRate(), result.getRateUnit());
process.run();

// Report ALL equipment constraints and identify the active one
logger.info("{}", "=== CONSTRAINT STATUS FOR ALL EQUIPMENT ===\n");

for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    ProcessEquipmentInterface unit = (ProcessEquipmentInterface) equip;
    boolean isBottleneck = unit.equals(result.getBottleneck());

    logger.info("{}", unit.getName() + (isBottleneck ? " ⭐ BOTTLENECK" : "") + ":");

    CapacityConstraint limitingConstraint = equip.getBottleneckConstraint();

    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        boolean isActive = c.equals(limitingConstraint) && isBottleneck;
        String marker = isActive ? " ◀ ACTIVE" : "";
        if (!c.isEnabled()) {
            continue;
        }
        if (!c.isEnabled()) {
            continue;
        }
        String status = c.isViolated() ? "⚠️" : c.isNearLimit() ? "⚡" : "✓";

        logger.info("{}", String.format("  %s %-18s: %7.2f / %7.2f %-6s (%5.1f%%)%s%n",
            status,
            c.getName(),
            c.getCurrentValue(),
            c.getDisplayDesignValue(),
            c.getUnit(),
            c.getUtilizationPercent(),
            marker));
    }
    logger.info("");
}

logger.info("{}", "=== OPTIMIZATION RESULT ===");
logger.info("{}", "Optimal production rate: " + result.getOptimalRate() + " kg/hr");
logger.info("{}", "Bottleneck equipment: " + (result.getBottleneck() == null ? "None" : result.getBottleneck().getName()));
logger.info("{}", "Feasible: " + result.isFeasible());
```

The report is calculated when the example runs; no fixed production uplift or bottleneck is
assumed. Only enabled constraints are printed. `getDisplayDesignValue()` displays the minimum
for residence-time and surge-margin limits, so a larger available residence time correctly means
less utilization. Inspect `result.isFeasible()` before using the selected rate.

---

## Whole-Plant Capacity Analysis (ProcessModel)

Large plants are typically split into several `ProcessSystem` areas (separation, recompression,
export, etc.) and combined into a `ProcessModel`. `ProcessModel` now exposes the **same
whole-flowsheet capacity API as `ProcessSystem`**, aggregating each call across every process area
it contains. This lets you analyse capacity and bottlenecks for the entire plant without manually
looping over areas.

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;

ProcessModel plant = new ProcessModel();
plant.add("separation", separationArea);   // a ProcessSystem
plant.add("compression", compressionArea); // a ProcessSystem
plant.run();

// Plant-wide bottleneck (most-utilized unit across ALL areas)
ProcessEquipmentInterface bottleneck = plant.getBottleneck();
double util = plant.getBottleneckUtilization();
logger.info("{}", "Plant bottleneck: " + bottleneck.getName()
    + " at " + (util * 100) + "%");

// Detailed bottleneck with the limiting constraint
BottleneckResult detail = plant.findBottleneck();
logger.info("{}", "Limiting unit: " + detail.getEquipment().getName()
    + " (" + (detail.getUtilization() * 100) + "%)");

// Whole-plant overload / hard-limit checks
boolean overloaded = plant.isAnyEquipmentOverloaded();
boolean hardLimit = plant.isAnyHardLimitExceeded();

// All constrained equipment, flattened across areas
for (CapacityConstrainedEquipment equip : plant.getConstrainedEquipment()) {
    logger.info("{}", ((ProcessEquipmentInterface) equip).getName()
        + ": " + (equip.getMaxUtilization() * 100) + "%");
}

// Capacity summary and near-limit list use area-qualified "Area::Unit" keys
Map<String, Double> summary = plant.getCapacityUtilizationSummary();
// e.g. {"separation::inlet separator": 93.3, "compression::export compressor": 98.0}
List<String> nearLimit = plant.getEquipmentNearCapacityLimit();
// e.g. ["compression::export compressor"]

// What-if: disable / enable all constraints across the whole plant
int disabled = plant.disableAllConstraints();
plant.enableAllConstraints();
```

### ProcessModel capacity API

| Method | Returns | Aggregation across areas |
|--------|---------|--------------------------|
| `getBottleneck()` | `ProcessEquipmentInterface` | Most-utilized unit plant-wide |
| `getBottleneckUtilization()` | `double` | Highest area bottleneck utilization (fraction) |
| `findBottleneck()` | `BottleneckResult` | Detailed result with the highest utilization |
| `getConstrainedEquipment()` | `List<CapacityConstrainedEquipment>` | Flattened, area then unit order |
| `isAnyEquipmentOverloaded()` | `boolean` | OR across areas (>100%) |
| `isAnyHardLimitExceeded()` | `boolean` | OR across areas (HARD limits) |
| `getCapacityUtilizationSummary()` | `Map<String, Double>` | `Area::Unit` keys → utilization % |
| `getEquipmentNearCapacityLimit()` | `List<String>` | `Area::Unit` names above warning threshold |
| `disableAllConstraints()` | `int` | Summed disabled-constraint count |
| `enableAllConstraints()` | `int` | Summed enabled-constraint count |

> **Naming:** Summary and near-limit entries are prefixed with the area name using the
> `Area::Unit` convention so names stay unique across areas (the same convention used by
> `ProcessAutomation` for area-qualified variable addresses).

> **Optimizing a ProcessModel:** `ProductionOptimizer.optimize(...)` also accepts a `ProcessModel`
> directly — it adapts the multi-area plant to a single optimization view internally. See
> [Optimizing Multi-Area Plants](#optimizing-multi-area-plants-processmodel) below.

---

## Capacity Constraint Framework

### Setting Up Multi-Constraint Equipment

Equipment implementing `CapacityConstrainedEquipment` can have multiple constraints:

```java
// Separator with gas load factor constraint
Separator separator = new Separator("HP Separator", feed);
separator.run();
separator.enableConstraints("gasLoadFactor");
separator.setDesignGasLoadFactor(0.15);  // K-factor limit

// Access constraints (getCapacityConstraints() returns a Map keyed by constraint name)
for (CapacityConstraint constraint : separator.getCapacityConstraints().values()) {
    logger.info("{}", "Constraint: " + constraint.getName());
    logger.info("{}", "  Type: " + constraint.getType());
    logger.info("{}", "  Current: " + constraint.getCurrentValue());
    logger.info("{}", "  Limit: " + constraint.getDisplayDesignValue());
    logger.info("{}", "  Utilization: " + (constraint.getUtilization() * 100) + "%");
    logger.info("{}", "  Is Violated: " + constraint.isViolated());
}

// Check overall utilization
double maxUtil = separator.getMaxUtilization();
CapacityConstraint limiting = separator.getBottleneckConstraint();
logger.info("{}", "Limiting constraint: " + limiting.getName() + " at " + (maxUtil * 100) + "%");
```

### Compressor with Multiple Constraints

```java
Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
process.add(compressor);
compressor.setMaximumSpeed(11000.0);      // HARD constraint - RPM
compressor.initMechanicalDesign();
compressor.getMechanicalDesign().setMaxDesignPower(2000.0);  // HARD constraint - kW
// Surge / stonewall margin constraints are created from the performance curve
// (call compressor.autoSize(...) or compressor.setCompressorChart(...) to populate them)

// After running, check all constraints
process.run();

for (CapacityConstraint c : compressor.getCapacityConstraints().values()) {
    if (!c.isEnabled()) {
        continue;
    }
    String status = c.isViolated() ? "⚠️ EXCEEDED" : "✓ OK";
    logger.info("{}", String.format("%s: %.1f / %.1f (%.0f%%) %s%n",
        c.getName(), c.getCurrentValue(), c.getDisplayDesignValue(),
        c.getUtilization() * 100, status));
}
```

---

## Advanced Optimization Configurations

### With Custom Objectives and Constraints

```java
import java.util.Arrays;
import java.util.List;

// Define objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("Production",
        ps -> ((Stream) ps.getUnit("Well Feed")).getFlowRate("kg/hr"),
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("Efficiency",
        ps -> ((Compressor) ps.getUnit("Gas Compressor")).getPolytropicEfficiency(),
        0.5, ObjectiveType.MAXIMIZE)
);

// Define constraints
List<OptimizationConstraint> constraints = Arrays.asList(
    OptimizationConstraint.lessThan("Max Export Pressure",
        ps -> ((Compressor) ps.getUnit("Gas Compressor")).getOutletStream().getPressure("bara"),
        105.0, ConstraintSeverity.HARD, 10.0, "Export pipeline limit"),
    OptimizationConstraint.greaterThan("Min Separator Temp",
        ps -> ((Separator) ps.getUnit("HP Separator")).getGasOutStream().getTemperature("C"),
        -10.0, ConstraintSeverity.SOFT, 5.0, "Illustrative temperature target; hydrate limits require a separate calculation")
);

// Run with objectives and constraints
ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationResult result = optimizer.optimize(
    process, feed, config, objectives, constraints);

// Check constraint statuses
for (ConstraintStatus status : result.getConstraintStatuses()) {
    logger.info("{}", String.format("%s: margin=%.2f, violated=%s%n",
        status.getName(), status.getMargin(), status.violated()));
}
```

### Optimizing Multi-Area Plants (ProcessModel)

`ProductionOptimizer.optimize(...)` accepts a multi-area `ProcessModel` directly. The plant is
adapted to a single optimization view internally, so the existing search algorithms are reused
unchanged and no manual flattening of areas is required. Inside the manipulated-variable setters and
objectives, `proc.getUnit("Unit")` resolves units across **all** areas, and area-qualified
`"Area::Unit"` addresses are also supported.

```java
import neqsim.process.processmodel.ProcessModel;

ProcessModel plant = new ProcessModel();
plant.add("separation", separationArea);   // a ProcessSystem
plant.add("compression", compressionArea); // a ProcessSystem
plant.run();

// The feed stream lives in one of the areas
Stream feed = (Stream) plant.get("separation").getUnit("feed");

ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationConfig config = new OptimizationConfig(500.0, 12_000.0)
    .rateUnit("kg/hr")
    .defaultUtilizationLimit(0.95);

// Single feed-rate optimization across the whole plant
OptimizationResult result = optimizer.optimize(plant, feed, config, objectives, constraints);

logger.info("{}", "Optimal rate: " + result.getOptimalRate() + " kg/hr");
logger.info("{}", "Plant bottleneck: " + (result.getBottleneck() == null ? "None" : result.getBottleneck().getName()));
logger.info("{}", "Feasible: " + result.isFeasible());
```

The full optimizer surface provided for `ProcessSystem` is available for `ProcessModel` — single
and multi-variable optimization, multi-objective (Pareto) optimization, and scenario comparison:

| Overload | Use case |
|----------|----------|
| `optimize(ProcessModel, StreamInterface feed, OptimizationConfig, objectives, constraints)` | Optimize a single feed rate for the whole plant |
| `optimize(ProcessModel, List<ManipulatedVariable>, OptimizationConfig, objectives, constraints)` | Optimize multiple setpoints (pressures, temperatures, split fractions) across areas |
| `optimizePareto(ProcessModel, StreamInterface feed, OptimizationConfig, objectives, constraints)` | Multi-objective Pareto front by varying the feed rate |
| `optimizePareto(ProcessModel, List<ManipulatedVariable>, OptimizationConfig, objectives, constraints)` | Multi-objective Pareto front across multiple setpoints |
| `new ScenarioRequest(name, ProcessModel, StreamInterface feed, OptimizationConfig, objectives, constraints)` | Whole-plant scenario in a `optimizeScenarios(...)` comparison |
| `new ScenarioRequest(name, ProcessModel, List<ManipulatedVariable>, OptimizationConfig, objectives, constraints)` | Multi-variable whole-plant scenario in a comparison |

> The bottleneck reported in the result is the plant-wide bottleneck — the most-utilized unit
> across every area — consistent with `ProcessModel.getBottleneck()`.

#### Fail-closed external candidate evaluation

Use `ProcessModelSimulationEvaluator` when an external solver proposes a vector of plant setpoints.
The evaluator applies the complete vector, runs the full `ProcessModel`, and samples each objective
and constraint callback once. Candidate values must be finite before they are applied. After the
solve, every raw/scalarized objective value and every constraint value/margin must also be finite.

An invalid proposal is rejected before it can mutate the live process state. A non-finite callback
sample makes the result infeasible even when the affected constraint is configured as soft; the
sample remains visible for diagnosis, its margin is reported as negative infinity, and
`getErrorMessage()` identifies the affected objective or constraint. `isSimulationConverged()` is
kept separate so callers can distinguish a valid process solve from invalid optimization evidence.
Invalid evidence receives a deterministic terminal penalty and must not be accepted or cached by an
external optimizer.

```java
ProcessModelSimulationEvaluator evaluator = new ProcessModelSimulationEvaluator(plant);
evaluator.addParameter("separation::feed.flowRate", 500.0, 12000.0, "kg/hr");
evaluator.addObjective("export rate", model -> export.getFlowRate("kg/hr"),
    ProcessModelSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE);
evaluator.addConstraintUpperBound("total power", model -> model.getPower("MW"), 40.0);

ProcessModelSimulationEvaluator.EvaluationResult candidate =
    evaluator.evaluate(new double[] { proposedFeedRate });
if (!candidate.isSimulationConverged() || !candidate.isFeasible()
    || candidate.getErrorMessage() != null) {
  // Reject the external-solver proposal; do not use it as a plant operating point.
}
```

#### Multi-objective (Pareto) optimization of a plant

Provide **two or more** objectives (for example, maximize throughput while minimizing compression
power) and the optimizer returns a Pareto front of non-dominated trade-off points. The `ProcessModel`
is adapted to a single optimization view internally, exactly like the single-objective overloads.

```java
import java.util.Arrays;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;

OptimizationConfig paretoConfig = new OptimizationConfig(500.0, 12_000.0)
    .rateUnit("kg/hr")
    .defaultUtilizationLimit(0.95)
    .paretoGridSize(8); // weight granularity along the front

OptimizationObjective maxFeed = new OptimizationObjective(
    "feed throughput", proc -> feed.getFlowRate("kg/hr"), 1.0, ObjectiveType.MAXIMIZE);
OptimizationObjective minPower = new OptimizationObjective(
    "compression power", proc -> compressor.getPower(), 1.0, ObjectiveType.MINIMIZE);

ParetoResult pareto = optimizer.optimizePareto(plant, feed, paretoConfig,
    Arrays.asList(maxFeed, minPower), constraints);

logger.info("{}", "Pareto front size: " + pareto.getParetoFrontSize());
```

#### Whole-plant scenario comparison

Use the `ProcessModel` `ScenarioRequest` constructors to compare what-if cases (different limits,
fluids, or setpoints) where each scenario is its own multi-area plant:

```java
ScenarioRequest baseCase = new ScenarioRequest("base case", plantA, feedA, config,
    objectives, constraints);
ScenarioRequest highLimit = new ScenarioRequest("high limit", plantB, feedB, config,
    objectives, constraints);

List<ScenarioResult> results =
    optimizer.optimizeScenarios(Arrays.asList(baseCase, highLimit));

for (ScenarioResult sr : results) {
    logger.info("{}", String.format("%s: %.0f kg/hr (bottleneck: %s)%n",
        sr.getName(), sr.getResult().getOptimalRate(),
        (sr.getResult().getBottleneck() == null ? "None" : sr.getResult().getBottleneck().getName())));
}
```

### Scenario Comparison

For an operating-condition comparison, keep installed compressor maps, separator ratings, and
flowsheet topology fixed across cases. Generate synthetic maps once at a declared design point;
regenerating them from each cooled operating point compares redesigned equipment. Keep the cooler
and liquid knockout present in the zero-cooling case when they belong to the installed plant, and
apply the specified temperature difference and pressure loss at every candidate flow. Check
`OptimizationResult.isFeasible()` before interpreting a reported rate as achievable production.
Cooling does not guarantee a production gain when another installed constraint remains limiting.
The fixed-equipment sweep is exercised by
`CoolingDutyProductionAnalysisTest.test2026ScenarioCoolingAnalysisThreeCompressors`.

```java
import java.util.Arrays;
import java.util.List;

// Each feed must belong to its scenario copy.
ProcessSystem summerProcess = process.copy();
StreamInterface summerFeed = (StreamInterface) summerProcess.getUnit(feed.getName());
ProcessSystem winterProcess = processWinter.copy();
StreamInterface winterFeed = (StreamInterface) winterProcess.getUnit(feedWinter.getName());

// Define scenarios
List<ScenarioRequest> scenarios = Arrays.asList(
    new ScenarioRequest("Summer", summerProcess, summerFeed,
        new OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr"),
        objectives, constraints),
    new ScenarioRequest("Winter", winterProcess, winterFeed,
        new OptimizationConfig(1000.0, 25000.0).rateUnit("kg/hr"),
        objectives, constraints)
);

// Define KPIs for comparison
List<ScenarioKpi> kpis = Arrays.asList(
    new ScenarioKpi("Max Rate", "kg/hr", r -> r.getOptimalRate()),
    new ScenarioKpi("Bottleneck Util", "%", r -> r.getBottleneckUtilization() * 100)
);

// Run comparison (compareScenarios is an instance method)
ProductionOptimizer optimizer = new ProductionOptimizer();
ScenarioComparisonResult comparison = optimizer.compareScenarios(scenarios, kpis);

// Print results
for (ScenarioResult sr : comparison.getScenarioResults()) {
    logger.info("{}", String.format("Scenario '%s': %.0f kg/hr (bottleneck: %s)%n",
        sr.getName(), sr.getResult().getOptimalRate(),
        (sr.getResult().getBottleneck() == null ? "None" : sr.getResult().getBottleneck().getName())));
}
```

### Search Algorithms

```java
// Binary search (default) - fast for monotonic responses
config.searchMode(SearchMode.BINARY_FEASIBILITY);

// Golden-section - handles non-monotonic responses
config.searchMode(SearchMode.GOLDEN_SECTION_SCORE);

// Nelder-Mead simplex - multi-dimensional optimization
config.searchMode(SearchMode.NELDER_MEAD_SCORE);

// Particle-swarm - global optimization
config.searchMode(SearchMode.PARTICLE_SWARM_SCORE)
    .swarmSize(8)
    .inertiaWeight(0.6)
    .cognitiveWeight(1.2)
    .socialWeight(1.2);
```

---

## ProcessSystem Bottleneck Analysis

### Basic Bottleneck Detection

```java
// Run the process
process.run();

// Get bottleneck (unified - checks both single and multi-constraint)
ProcessEquipmentInterface bottleneck = process.getBottleneck();
logger.info("{}", "Bottleneck: " + (bottleneck == null ? "None" : bottleneck.getName()));
logger.info("{}", String.format("Utilization: %.1f%%%n", process.getBottleneckUtilization() * 100));
```

### Detailed Constraint Analysis

```java
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;

// Get detailed bottleneck result
BottleneckResult bottleneckResult = process.findBottleneck();

logger.info("{}", "Bottleneck Equipment: " + bottleneckResult.getEquipmentName());
logger.info("{}", "Limiting Constraint: " + bottleneckResult.getConstraintName());
logger.info("{}", String.format("Utilization: %.1f%%%n", bottleneckResult.getUtilization() * 100));

// Inspect the limiting constraint on the bottleneck equipment
CapacityConstraint c = bottleneckResult.getConstraint();
if (c != null) {
    logger.info("{}", String.format("  - %s: %.1f%% (%s)%n",
        c.getName(), c.getUtilization() * 100, c.getType()));
}
```

### Capacity Utilization Summary

```java
import java.util.Map;

// Get all equipment utilizations
Map<String, Double> utilizations = process.getCapacityUtilizationSummary();

logger.info("{}", "=== Capacity Utilization Summary ===");
for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
    logger.info("{}: {}%", entry.getKey(), entry.getValue());
}

// Check for equipment near limits (returns Area::Unit / unit names above the warning threshold)
for (String equipName : process.getEquipmentNearCapacityLimit()) {
    logger.info("{}", "⚠️ Near limit: " + equipName);
}

// Check for any overloaded equipment
if (process.isAnyEquipmentOverloaded()) {
    logger.info("{}", "❌ Equipment overloaded!");
}
if (process.isAnyHardLimitExceeded()) {
    logger.info("{}", "🛑 HARD limit exceeded - system unsafe!");
}
```

---

## Python Examples (neqsim-python)

NeqSim Python uses JPype for direct Java access. All Java classes are available through the `neqsim` package.

### Basic Setup

```python
# Install neqsim-python: pip install neqsim

import neqsim
from neqsim.thermo import fluid
from neqsim import jneqsim
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
```

### Production Optimization in Python

```python
import jpype
import jpype.imports
from jpype.types import *

# Ensure JVM is started (neqsim does this automatically)
import neqsim

# Import Java classes directly
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
Separator = jneqsim.process.equipment.separator.Separator
Compressor = jneqsim.process.equipment.compressor.Compressor
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer

# Create fluid
fluid = SystemSrkEos(298.15, 50.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.02)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

feed = Stream("Well Feed", fluid)
feed.setFlowRate(5000.0, "kg/hr")
process.add(feed)

sep = Separator("HP Separator")
sep.setInletStream(feed)
process.add(sep)

comp = Compressor("Gas Compressor")
comp.setInletStream(sep.getGasOutStream())
comp.setOutletPressure(100.0, "bara")
comp.setMaximumSpeed(11000.0)
comp.initMechanicalDesign()
comp.getMechanicalDesign().setMaxDesignPower(500.0)  # kW power limit
process.add(comp)

process.run()

# Configure optimization
OptConfig = ProductionOptimizer.OptimizationConfig
SearchMode = ProductionOptimizer.SearchMode

config = OptConfig(1000.0, 20000.0) \
    .rateUnit("kg/hr") \
    .tolerance(10.0) \
    .maxIterations(20) \
    .defaultUtilizationLimit(0.95) \
    .searchMode(SearchMode.BINARY_FEASIBILITY)

# Run optimization (optimize() is an instance method; objectives/constraints may be None)
optimizer = ProductionOptimizer()
result = optimizer.optimize(process, feed, config, None, None)

# Print results
print(f"Optimal rate: {result.getOptimalRate():.0f} {result.getRateUnit()}")
bottleneck = result.getBottleneck()
print(f"Bottleneck: {bottleneck.getName() if bottleneck is not None else 'None'}")
print(f"Utilization: {result.getBottleneckUtilization() * 100:.1f}%")
print(f"Feasible: {result.isFeasible()}")
```

### Configuring Restrictions and Constraints in Python

Python provides full access to all restriction configuration options through the `OptimizationConfig` builder pattern.

#### Controlling Simulation Validity

```python
# Strict mode (recommended for production)
config = OptConfig(1000.0, 20000.0) \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(0.95)

# Exploration mode (for debugging/investigation)
config = OptConfig(1000.0, 50000.0) \
    .rejectInvalidSimulations(False) \
    .defaultUtilizationLimit(2.0)  # Allow simulated overload
```

#### Per-Equipment Utilization Limits

```python
# Import equipment classes for type-based limits
from neqsim import jneqsim
Compressor = jneqsim.process.equipment.compressor.Compressor
Separator = jneqsim.process.equipment.separator.Separator
Pump = jneqsim.process.equipment.pump.Pump

# Configure per-equipment limits
config = OptConfig(1000.0, 20000.0) \
    .rateUnit("kg/hr") \
    .defaultUtilizationLimit(1.0) \
    .utilizationLimitForName("Export Compressor", 0.90) \
    .utilizationLimitForName("HP Separator", 1.05) \
    .utilizationLimitForType(Compressor, 0.95) \
    .utilizationLimitForType(Pump, 0.90)
```

#### Disabling Capacity Analysis on Equipment

```python
# Exclude specific equipment from bottleneck detection
heater = jneqsim.process.equipment.heatexchanger.Heater(
    "Gas Heater", comp.getOutletStream()
)
heater.setOutTemperature(313.15)
process.add(heater)
heater.setCapacityAnalysisEnabled(False)

manifold = jneqsim.process.equipment.mixer.Mixer("Production Manifold")
manifold.addStream(heater.getOutletStream())
process.add(manifold)
manifold.setCapacityAnalysisEnabled(False)

# Now these won't be considered as bottlenecks
optimizer = ProductionOptimizer()
result = optimizer.optimize(process, feed, config, None, None)
```

#### Adding Custom Constraints

```python
from jpype import JImplements, JOverride

# Import constraint classes
OptimizationConstraint = ProductionOptimizer.OptimizationConstraint
ConstraintSeverity = ProductionOptimizer.ConstraintSeverity

# Define a constraint evaluator
@JImplements("java.util.function.ToDoubleFunction")
class PowerEvaluator:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("Gas Compressor")
        if comp is None:
            raise ValueError("Gas Compressor is required")
        return comp.getPower("kW")

# Create HARD constraint (must be satisfied)
power_constraint = OptimizationConstraint.lessThan(
    "Max Power",                    # Name
    PowerEvaluator(),               # Evaluator function
    450.0,                          # Limit (kW)
    ConstraintSeverity.HARD,        # Cannot be violated
    0.0,                            # Penalty weight (unused for HARD)
    "Compressor driver power limit" # Description
)

# Create a synthetic chart for this tutorial before evaluating surge.
# Real studies require the installed compressor map.
comp.generateCompressorChart("normal curves", 5)
comp.setSolveSpeed(True)
process.run()

# Create SOFT constraint (penalty for violation)
@JImplements("java.util.function.ToDoubleFunction")
class SurgeMarginEvaluator:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("Gas Compressor")
        if comp is None or not comp.getCompressorChart().isUseCompressorChart():
            raise ValueError("Surge evidence requires an active compressor chart")
        return comp.getDistanceToSurge()

margin_constraint = OptimizationConstraint.greaterThan(
    "Surge Margin",
    SurgeMarginEvaluator(),
    0.10,                           # Minimum 10% surge margin
    ConstraintSeverity.SOFT,        # Can be violated with penalty
    100.0,                          # Penalty weight
    "Maintain adequate surge margin"
)

# Create constraint list
from java.util import Arrays
constraints = Arrays.asList(power_constraint, margin_constraint)

# Run optimization with constraints
optimizer = ProductionOptimizer()
result = optimizer.optimize(process, feed, config, None, constraints)
```

#### Common Restriction Configuration Patterns

```python
# Pattern 1: Safe Production Operation
config_safe = OptConfig(1000.0, 20000.0) \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(0.90) \
    .searchMode(SearchMode.BINARY_FEASIBILITY)

# Pattern 2: Maximum Capacity Search
config_max = OptConfig(1000.0, 50000.0) \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(1.0) \
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)

# Pattern 3: Equipment Sizing Study
config_sizing = OptConfig(1000.0, 100000.0) \
    .rejectInvalidSimulations(False) \
    .defaultUtilizationLimit(999.0) \
    .searchMode(SearchMode.PARTICLE_SWARM_SCORE)

# Pattern 4: Critical Equipment Protection
config_critical = OptConfig(1000.0, 20000.0) \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(1.0) \
    .utilizationLimitForName("Critical Compressor", 0.85) \
    .utilizationLimitForName("Aging Pump", 0.80) \
    .searchMode(SearchMode.BINARY_FEASIBILITY)
```

### Multi-Constraint Analysis in Python

```python
from neqsim import jneqsim
BottleneckResult = jneqsim.process.equipment.capacity.BottleneckResult
CapacityConstraint = jneqsim.process.equipment.capacity.CapacityConstraint

# Get detailed bottleneck
bottleneck_result = process.findBottleneck()

bottleneck = bottleneck_result.getEquipment()
print(f"Bottleneck: {bottleneck.getName() if bottleneck is not None else 'None'}")
print(f"Limiting: {bottleneck_result.getConstraintName()}")
print(f"Utilization: {bottleneck_result.getUtilization() * 100:.1f}%")

# Inspect the limiting constraint on the bottleneck equipment
constraint = bottleneck_result.getConstraint()
if constraint is not None:
    status = "⚠️ VIOLATED" if constraint.isViolated() else "✓ OK"
    print(f"  {constraint.getName()}: {constraint.getUtilization()*100:.0f}% {status}")
```

### Capacity Summary in Python

```python
# Get utilization summary
utilizations = process.getCapacityUtilizationSummary()

print("=== Capacity Utilization ===")
for name, util in utilizations.items():
    filled = max(0, min(20, int(float(util) / 5.0)))
    bar = "█" * filled + "░" * (20 - filled)
    print(f"{name}: [{bar}] {util:.0f}%")

# Check near-limit equipment (returns Area::Unit / unit names above the warning threshold)
near_limit = process.getEquipmentNearCapacityLimit()
for equip_name in near_limit:
    print(f"⚠️ Near limit: {equip_name}")
```

### Scenario Comparison in Python

```python
from java.util import Arrays, ArrayList

# Create scenarios
ScenarioRequest = ProductionOptimizer.ScenarioRequest
ScenarioKpi = ProductionOptimizer.ScenarioKpi

scenarios = ArrayList()

# Scenario 1: Base case
config1 = OptConfig(1000.0, 20000.0).rateUnit("kg/hr")
scenarios.add(ScenarioRequest("Base Case", process, feed, config1, None, None))

# Scenario 2: High pressure export
process2 = process.copy()
comp2 = process2.getUnit("Gas Compressor")
comp2.setOutletPressure(120.0, "bara")
config2 = OptConfig(1000.0, 18000.0).rateUnit("kg/hr")
feed2 = process2.getUnit("Well Feed")
scenarios.add(ScenarioRequest("High Pressure", process2, feed2, config2, None, None))

# Define KPIs
kpis = ArrayList()
kpis.add(ScenarioKpi.optimalRate("kg/hr"))
kpis.add(ScenarioKpi("Bottleneck Util", "%", jpype.JProxy(
    "java.util.function.ToDoubleFunction",
    dict(applyAsDouble=lambda r: r.getBottleneckUtilization() * 100),
)))

# Compare (compareScenarios is an instance method)
optimizer = ProductionOptimizer()
comparison = optimizer.compareScenarios(scenarios, kpis)

for sr in comparison.getScenarioResults():
    print(f"{sr.getName()}: {sr.getResult().getOptimalRate():.0f} kg/hr")
```

---

## Integration with External Systems

### JSON Export for Dashboards

```java
// Get a lightweight optimization summary as structured data
OptimizationSummary summary = new ProductionOptimizer().quickOptimize(process, feed, "kg/hr", Collections.emptyList());

// Convert to JSON using Gson
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

Gson gson = new GsonBuilder().setPrettyPrinting().create();
String json = gson.toJson(summary);
logger.info("{}", json);
```

### Iteration History for Plotting

```java
// Get full result with history
OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config, null, null);

// Export iteration history for plotting
List<IterationRecord> history = result.getIterationHistory();

logger.info("{}", "Rate,Bottleneck,Utilization,Feasible,Score");
for (IterationRecord record : history) {
    logger.info("{}", String.format("%.1f,%s,%.3f,%s,%.4f%n",
        record.getRate(),
        record.getBottleneckName(),
        record.getBottleneckUtilization(),
        record.isFeasible(),
        record.getScore()));
}
```

### Real-Time Optimization Loop

```python
import time

def evaluate_realtime_snapshot(temperature_c, pressure_bara):
    """Evaluate one measured snapshot on an isolated process copy."""
    candidate_process = process.copy()
    candidate_feed = candidate_process.getUnit("Well Feed")
    candidate_feed.setTemperature(temperature_c, "C")
    candidate_feed.setPressure(pressure_bara, "bara")
    candidate_process.run()
    candidate = ProductionOptimizer().optimize(
        candidate_process, candidate_feed, config, None, None
    )
    bottleneck = candidate.getBottleneck()
    return {
        "timestamp": time.time(),
        "feasible": candidate.isFeasible(),
        "recommended_rate_kg_hr": candidate.getOptimalRate() if candidate.isFeasible() else None,
        "bottleneck": None if bottleneck is None else str(bottleneck.getName()),
        "utilization_fraction": candidate.getBottleneckUtilization(),
    }

# A single synthetic measurement. A historian/scheduler can call this function repeatedly.
print(evaluate_realtime_snapshot(25.0, 50.0))
```

---

## Equipment Support Matrix

| Equipment | getCapacityDuty() | getCapacityMax() | CapacityConstrainedEquipment |
|-----------|-------------------|------------------|------------------------------|
| Separator | ✅ Gas flow (m³/hr) | ✅ Gas-flow design limit | ✅ Named gas/liquid constraints |
| Compressor | ✅ Power (W) | ✅ Max power | ✅ Speed, power, surge, stonewall margin |
| Pump | ✅ Power (W) | ✅ Max power | ✅ Capacity constraints |
| Heater/Cooler | ✅ Duty (W) | ✅ Max duty | ✅ Duty constraint |
| HeatExchanger | ✅ Duty (W) | ✅ Max duty | ✅ Duty constraint |
| Valve | ✅ Flow (m³/hr) | ✅ Design volume flow | ✅ Named valve constraints |
| Pipe | ✅ Superficial velocity | ✅ Max velocity | ✅ Velocity constraint |
| DistillationColumn | Optimizer uses Fs factor | Configured Fs limit | ✅ Inherited direct constraints and strategy |
| Manifold | ✅ Velocity | ✅ Erosional velocity | ✅ FIV analysis |

**Notes:**
- **Separator**: Enabled direct constraints take priority. Only the `ProductionOptimizer` fallback without enabled constraints uses liquid level fraction. Legacy capacity getters separately report gas volume flow.
- **Valve**: With no enabled direct constraints, `ProductionOptimizer` tracks opening only when Kv/Cv is configured and maximum opening is below 100%.
- **Compressor**: Min speed constraint correctly handles utilization (below minimum speed = violation).

---

## Best Practices

### 1. Set Realistic Equipment Limits

```java
// Always set mechanical design limits
separator.setDesignGasLoadFactor(0.107);  // Souders-Brown K-factor [m/s]

// Legacy gas-flow capacity getter uses the gas-specific mechanical-design limit.
separator.initMechanicalDesign();
separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(2000.0);  // m³/hr

// Set compressor limits
compressor.setMaximumSpeed(11000.0);
compressor.initMechanicalDesign();
compressor.getMechanicalDesign().setMaxDesignPower(500.0);  // kW
```

### 2. Use Appropriate Utilization Margins

```java
// Conservative (debottlenecking studies)
config.defaultUtilizationLimit(0.80);

// Normal operations
config.defaultUtilizationLimit(0.95);

// Stress testing
config.defaultUtilizationLimit(1.05);  // Allow some overage with penalties
```

### 3. Configure Equipment-Specific Limits

```java
config.utilizationLimitForName("Critical Compressor", 0.85)
      .utilizationLimitForType(Separator.class, 0.90)
      .utilizationLimitForType(Compressor.class, 0.88);
```

### 4. Define Explicit Operating Limits

A hard optimizer constraint rejects a simulated candidate. Operating approval still depends on
the supplied limit and the validity of the model and measurements.

### 5. Compressor Curves with Optimization

When using compressor performance curves with the optimizer, follow this setup sequence:

```java
// 1. Create and run compressor to establish design point
Compressor compressor = new Compressor("Export Compressor", gasScrubber.getGasOutStream());
compressor.setOutletPressure(80.0, "bara");
compressor.setPolytropicEfficiency(0.78);
compressor.setUsePolytropicCalc(true);
process.add(compressor);
process.run();

// 2. Generate compressor chart at design point
CompressorChartGenerator chartGen = new CompressorChartGenerator(compressor);
chartGen.setChartType("interpolate and extrapolate");
CompressorChartInterface chart = chartGen.generateCompressorChart("normal curves", 5);
compressor.setCompressorChart(chart);
compressor.getCompressorChart().setUseCompressorChart(true);
compressor.setSolveSpeed(true);

// 3. IMPORTANT: Set max speed higher than operating speed
// This defines the available headroom for optimization
double designSpeed = compressor.getSpeed();
compressor.setMaximumSpeed(designSpeed * 1.15);  // 15% speed margin

// 4. Re-run process and reinitialize constraints
process.run();
compressor.reinitializeCapacityConstraints();  // Updates constraints with curve limits

// 5. Now optimize - bounds must respect surge/stonewall
double lowerBound = currentRate * 0.96;  // Stay above surge
double upperBound = currentRate * 1.10;  // Stay below stonewall

OptimizationConfig config = new OptimizationConfig(lowerBound, upperBound)
    .rateUnit("kg/hr")
    .utilizationLimitForType(Compressor.class, 1.0);  // Enabled constraints are used automatically

OptimizationResult result = optimizer.optimize(process, feedStream, config,
    Collections.emptyList(), Collections.emptyList());
```

**Key Points:**
- Call `reinitializeCapacityConstraints()` after setting compressor charts to update speed/surge constraints
- Set `setMaximumSpeed()` to define available headroom (typically 10-15% above design)
- Use realistic search bounds that respect compressor surge/stonewall limits
- Compressor constraints include: speed, min speed, power (speed-dependent), ratedPower (vs motor rating), surge margin, stonewall margin
- For pipes, `setMaxDesignVelocity()` auto-invalidates cached constraints; use `reinitializeCapacityConstraints()` if needed after other changes

```java
// HARD constraints cannot be violated
OptimizationConstraint.lessThan("Max Pressure",
    ps -> ps.getUnit("Export").getPressure("bara"),
    150.0, ConstraintSeverity.HARD, 100.0, "Pipeline MAWP");

// SOFT constraints add penalties but allow operation
OptimizationConstraint.greaterThan("Target Temperature",
    ps -> ((Cooler) ps.getUnit("Cooler")).getOutletStream().getTemperature("C"),
    35.0, ConstraintSeverity.SOFT, 5.0, "Target export temp");
```

### 6. Validate Before Optimization

```java
// Check that all equipment is properly configured
for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (!Double.isFinite(unit.getCapacityMax()) || unit.getCapacityMax() <= 0
        || unit.getCapacityMax() == Double.MAX_VALUE) {
        logger.info("{}", "Warning: " + unit.getName() + " has no capacity limit set");
    }
}
```

---

## Troubleshooting

### Problem: Optimization finds no feasible solution

**Possible causes:**
1. Lower bound is too high
2. Equipment limits are too tight
3. Hard constraints cannot be satisfied

**Solution:**
```java
// Check constraints at minimum rate
feed.setFlowRate(config.getLowerBound(), "kg/hr");
process.run();

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    double util = unit.getCapacityDuty() / unit.getCapacityMax();
    if (util > 1.0) {
        logger.info("{}", "Already exceeded at min rate: " + unit.getName());
    }
}

// Use infeasibility diagnostics (New)
OptimizationResult result = optimizer.optimize(process, feed, config, null, null);
if (!result.isFeasible()) {
    logger.info("{}", result.getInfeasibilityDiagnosis());
}
```

### Problem: Bottleneck changes unexpectedly

**Solution:** Use iteration history to understand the search:
```java
for (IterationRecord r : result.getIterationHistory()) {
    logger.info("{}", String.format("Rate=%.0f, Bottleneck=%s, Util=%.1f%%%n",
        r.getRate(), r.getBottleneckName(), r.getBottleneckUtilization() * 100));
}
```

### Problem: Slow optimization

**Solutions:**
1. Reduce search range
2. Increase tolerance
3. Use binary search for monotonic problems
4. Enable caching with size limit
5. **Use stagnation detection** (New)
6. **Use warm start** when re-optimizing (New)

```java
config.tolerance(50.0)  // Coarser tolerance
      .maxIterations(15)
      .enableCaching(true)
      .maxCacheSize(500)               // Bounded cache (New)
      .stagnationIterations(5)         // Early termination (New)
      .searchMode(SearchMode.BINARY_FEASIBILITY);

// For re-optimization, use warm start (New)
double[] previousOptimal = new double[]{lastResult.getOptimalRate()};
config.initialGuess(previousOptimal);
```

### Problem: Invalid configuration causes runtime errors

**Solution:** Validate configuration before optimization (New):
```java
try {
    config.validate();  // Throws if invalid
} catch (IllegalArgumentException e) {
    logger.info("{}", "Configuration error: " + e.getMessage());
}
```

---

## Related Documentation

- [Bottleneck Analysis](../wiki/bottleneck_analysis) - Detailed bottleneck detection API
- [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK) - Multi-constraint architecture
- [Process Simulation Guide](../wiki/process_simulation) - Building process models
- [Advanced Process Simulation](../wiki/advanced_process_simulation) - Recycles and complex systems

---

## API Reference

### ProductionOptimizer

| Method | Description |
|--------|-------------|
| `optimize(ProcessSystem, StreamInterface, OptimizationConfig, List<Objective>, List<Constraint>)` | Find optimal feed rate (instance method; objectives/constraints may be `null`) |
| `quickOptimize(ProcessSystem, StreamInterface[, String rateUnit, List<OptimizationConstraint>])` | Returns lightweight `OptimizationSummary` |
| `compareScenarios(List<ScenarioRequest>, List<ScenarioKpi>)` | Compare multiple scenarios |

### OptimizationConfig (New Methods)

| Method | Description |
|--------|-------------|
| `validate()` | Validates configuration, throws if invalid |
| `stagnationIterations(int)` | Stop after N iterations with no improvement (default: 5) |
| `maxCacheSize(int)` | Maximum LRU cache entries (default: 1000) |
| `initialGuess(double[])` | Starting point for warm start optimization |

### OptimizationResult (New Methods)

| Method | Description |
|--------|-------------|
| `getInfeasibilityDiagnosis()` | Detailed report of constraint violations |

### ProcessSystem

| Method | Description |
|--------|-------------|
| `getBottleneck()` | Get equipment with highest utilization |
| `getBottleneckUtilization()` | Get utilization of bottleneck |
| `findBottleneck()` | Get detailed BottleneckResult |
| `getConstrainedEquipment()` | Get all CapacityConstrainedEquipment |
| `isAnyEquipmentOverloaded()` | Check if any utilization > 100% |
| `isAnyHardLimitExceeded()` | Check if any HARD constraint violated |
| `getCapacityUtilizationSummary()` | Map of equipment name → utilization percent |
| `getEquipmentNearCapacityLimit()` | Equipment names above the warning threshold |

### CapacityConstrainedEquipment

| Method | Description |
|--------|-------------|
| `getCapacityConstraints()` | Map of constraint name → constraint |
| `getBottleneckConstraint()` | Get highest-utilization constraint |
| `getMaxUtilization()` | Get maximum utilization across constraints |
| `isOverloaded()` | Any constraint > 100% |
| `isHardLimitExceeded()` | Any HARD constraint violated |
