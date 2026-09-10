---
title: Capacity Constraint Framework
description: "The Capacity Constraint Framework extends NeqSim's existing bottleneck analysis capability with multi-constraint support."
---

# Capacity Constraint Framework

## Overview

The Capacity Constraint Framework extends NeqSim's existing bottleneck analysis capability with multi-constraint support. It provides:

- **Universal constraint support**: ALL 144+ equipment types inheriting from `ProcessEquipmentBaseClass` can have capacity constraints — not just those implementing `CapacityConstrainedEquipment`
- **Multiple constraints per equipment**: Track speed, power, surge margin, temperature, etc. simultaneously
- **18 built-in capacity strategies**: Pre-configured constraints for compressors, separators, pipes, valves, heat exchangers, pumps, expanders, reactors, power generation, subsea equipment, filters, electrolyzers, wells, and more
- **Constraint types**: HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope)
- **Warning thresholds**: Early warning when approaching limits
- **Evidence metadata**: Record provenance, confidence, and the scalar operating range where a limit is applicable
- **Integration with ProductionOptimizer**: Works seamlessly with existing optimization tools
- **ProcessSystem-wide analysis**: `findBottleneck()`, `getCapacityUtilizationSummary()`, and related methods iterate over ALL equipment

> **Setting limits through mechanical design:** To derive capacity constraints directly from an
> equipment's design envelope (max design pressure drop, volume flow, power, etc.), see
> [Equipment Utilization via Mechanical Design](equipment_utilization_via_mechanical_design.md).

## Running the examples

The examples use the current Java API and Java 8 syntax. Put imports at the top of a
source file, statement snippets in a method, and class definitions in their own source
files (or as nested classes). Run each alternative example in a fresh scope. In addition
to imports shown locally, the equipment examples use:

```java
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.*;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.expander.Expander;
import neqsim.process.equipment.heatexchanger.*;
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.pipeline.*;
import neqsim.process.equipment.pump.Pump;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.*;
import neqsim.process.processmodel.*;
import neqsim.process.util.optimizer.*;
import neqsim.process.util.optimizer.PressureBoundaryOptimizer.LiftCurveTable;
import neqsim.thermo.system.SystemSrkEos;
```

Use this feed when a snippet expects `fluid` or `feed`. Downstream snippets must use
streams from their own solved process, not unrelated copies. Names such as `compressor`,
`separator`, `equipment`, `plant`, and `model` refer to that explicitly configured model.
The registry and immutable-evidence sections require the additional installed design data
and exact calculation identity described in those sections (`calculationId` is a `String`).

```java
Logger logger = LogManager.getLogger("CapacityExample");
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
feed.run();
```

The executable regression coverage is in `CapacityOptimizationDocumentationTest`; the
registry/snapshot API families also have their own dedicated tests. Missing installed
ratings must remain explicit evidence gaps. Auto-sizing and default strategy limits are
screening inputs, not vendor qualification.

## Plant-wide constraint registration

`PlantConstraintRegistry` adds deterministic plant identity and engineering-basis metadata without
replacing equipment-local `CapacityConstraint` calculations. It can register constraints on an
equipment item, stream, area, complete model, shared resource, or coupled group such as compressors
on a common shaft. Definitions are immutable, serializable, callback-free, and directly accessible
from Python through JPype.

The registry deliberately does **not** calculate utilization or declare a process candidate feasible.
It is the metadata layer consumed by the later complete-snapshot and solver layers.

| Registration field | Contract |
|---|---|
| Stable identity | Escaped model, area, subject, and caller-owned constraint ID |
| Aggregation | `DIRECT`, `SUM`, `MAXIMUM`, `MINIMUM`, `SHARED_BUDGET`, `COMMON_SETPOINT`, or explicit rate-basis conversion |
| Engineering basis | Target unit, measurement/rating basis, provenance, owner, reference, and calculation method |
| Evidence quality | Optional confidence and scalar validity interval |
| Status | Registered, disabled, incomplete basis, or disabled with incomplete basis |

Aggregated participants must use the target unit and basis. NeqSim never guesses a conversion; an
unlike source must declare an explicit finite affine conversion. Aggregates also require a non-empty
target unit and basis. This prevents, for example, silently adding shaft `kW` to electrical `MW`, or
combining standard and actual volumetric rates.

```java
PlantConstraintRegistry registry = new PlantConstraintRegistry();
PlantConstraintDefinition totalPower = PlantConstraintDefinition
    .builder("total-power", PlantConstraintScope.sharedResource("NorthPlant", "electrical-power"))
    .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
    .unit("MW")
    .basis("instantaneous electrical load")
    .provenance("site power study 2026")
    .participant(PlantConstraintParticipant.direct(
        "compression/K-101", "MW", "instantaneous electrical load"))
    .participant(PlantConstraintParticipant.converted(
        "compression/K-102", "kW", "instantaneous electrical load", 0.001, 0.0))
    .build();
registry.register(totalPower);
```

Use `registerEquipmentConstraint(...)` to copy identity-relevant metadata from an existing
`CapacityConstraint`. The adapter does not invoke or retain its live value supplier. Runtime values,
applicability, convergence, margins, and utilization belong in a complete immutable snapshot and
must be regenerated after every process solve.

## Complete immutable plant utilization snapshots

`PlantUtilizationSnapshot` is the post-solve evidence layer consumed by later plant-wide resource
and solver increments. It pairs every deterministic registry definition with a callback-free
`PlantConstraintSample` for one exact `calculationId`. The snapshot does not run equipment, evaluate
constraints, aggregate shared resources, or mutate process state.

Each row separates evidence coverage from operating status. Disabled definitions remain visible and
need no runtime sample. Enabled missing, stale, out-of-validity, non-finite, metadata-mismatched,
calculation-ID-mismatched, exception, and incomplete-convergence rows remain explicit and make the
snapshot incomplete. Finite available evidence retains:

| Quantity | Unit and sign contract |
|---|---|
| Sampled value and applicable limit | Registered physical unit and basis |
| Normalized utilization | Dimensionless; 1.0 is active |
| Normalized residual | Dimensionless `utilization - 1`; positive violates |
| Physical margin | Registered unit; positive is headroom and negative violates |
| Required relief | Registered unit; non-negative amount required to reach the limit |

`isComplete()` requires valid exact-calculation evidence for every enabled definition. Without an
explicit expected-coverage report, this means **registered constraints only**; it cannot detect
equipment or restrictions that were never registered. Use the coverage preflight below to connect
declared engineering scope to the registry before accepting a snapshot.
`isFeasible()` additionally rejects any hard or critical violation. Soft and advisory violations are
ranked and reported but do not silently become hard constraints. The immutable bottleneck ladder
contains all enabled available rows in descending utilization order with stable identity as the tie
breaker.

Use `PlantConstraintSample.fromInstalledEquipmentEvidence(...)` and
`fromProcessBoundaryEvidence(...)` to reuse existing immutable #2941 equipment evidence and process-
boundary evidence. These adapters validate identity, direction, physical unit, and evidence status;
they do not retain live suppliers or evaluator callbacks. A sample's registered basis is copied from
the matching plant definition, so an adapter cannot invent a rate- or reference-basis conversion.

### Shared total-power evidence

`PlantSharedResourceEvidence` is the immutable post-solve adapter for a maximum
`SHARED_BUDGET`. It requires one observation for every registered participant and an independently
calculated total in the same target unit and basis. Missing, unexpected, stale, non-finite,
out-of-validity, metadata-mismatched, exception, or unconverged evidence is incomplete; unavailable
values are Java `NaN` and JSON `null`, never zero load.

Use `fromProcessSystemShaftPower(...)` when participant IDs are top-level compressor or pump names,
or `fromProcessModelShaftPower(...)` when participant IDs are `ProcessModel` area names. Both
adapters cross-check the participant sum against the existing `getPower(unit)` total and retain the
explicit **compressor and pump shaft-power** basis. They do not infer motor efficiency or convert
shaft power to electrical demand.

```java
PlantConstraintDefinition shaftBudget = PlantConstraintDefinition
    .builder("total-shaft-power",
        PlantConstraintScope.sharedResource("NorthPlant", "compression shaft power"))
    .aggregationPolicy(PlantConstraintDefinition.AggregationPolicy.SHARED_BUDGET)
    .limitDirection(PlantConstraintDefinition.LimitDirection.MAXIMUM)
    .unit("MW")
    .basis("compressor and pump shaft power")
    .provenance("approved rotating-equipment study")
    .participant(PlantConstraintParticipant.converted(
        "Compression", "kW", "compressor and pump shaft power", 0.001, 0.0))
    .build();

PlantSharedResourceEvidence powerEvidence =
    PlantSharedResourceEvidence.fromProcessModelShaftPower(
        shaftBudget, calculationId, 12.0, model, model.isModelConverged(),
        "completed isolated ProcessModel");
PlantUtilizationSnapshot snapshot = PlantUtilizationSnapshot.builder(
        new PlantConstraintRegistry().register(shaftBudget), calculationId)
    .convergenceComplete(model.isModelConverged())
    .sample(powerEvidence.toPlantConstraintSample())
    .build();
```

For an electrical `EnergyBus`, `fromSolvedEnergyBusRequestedDemand(...)` uses the current solved
report's requested input demand, including unmet demand. It ignores producer output rows and rejects
unregistered inputs, external demand, bidirectional loads, and stale reports. An out-of-service
shaft participant is accepted only when explicitly named and observed at finite zero power.
Changing a limit or line-up requires new evidence with a new calculation identity; collection is
read-only and does not restore process mutations.

This layer is deliberately not operating authority. It does not infer electrical efficiency,
compute separator capacity or piping hydraulics, coordinate product quality, emissions or utility
allocation, restore mutations, cache process results, schedule dirty equipment, or accept solver
candidates. External optimizer proposals must still be replayed and accepted by the full NeqSim
model.

### Common-shaft compressor-train evidence

`PlantCommonShaftEvidence` freezes the already solved `MechanicalShaft` allocation together with
the declared `CompressorDriver`, `Gearbox`, and every casing `Compressor` operating point. The
adapter performs no equipment run and retains no mutable equipment. The caller supplies the exact
calculation ID, driver and casing participant IDs, speed and power-balance tolerances, and an
independently approved maximum torque.

The resulting common snapshot contains distinct constraints for casing-to-shaft speed agreement,
shaft maximum speed, unmet shaft power, driver speed range and available power, gearbox maximum
input power, shaft maximum torque, and each casing's minimum signed surge/stonewall map margin.
Power is in kW, speed in rpm, torque in N m, and map distances are dimensionless. Gearbox input
power uses the configured efficiency and idle loss; driver speed uses the configured output-to-input ratio;
torque uses only `power / angular speed`. No rating or map envelope is inferred.

Every active shaft input and the driver output must be declared. A stale bus report, unexpected
participant, mismatched calculation identity or casing power, chartless or non-finite map point,
unknown rating, trip, or incomplete convergence makes the evidence incomplete. A finite solved
point outside its map or beyond a declared speed, power, gearbox, or torque limit remains complete
but infeasible. Explicitly out-of-service casings qualify only with verified zero requested and
observed shaft load. Java getters, serialization, `toPlantUtilizationSnapshot()`, and `toJson()`
expose the same immutable evidence; unavailable JSON numbers are `null`, never zero.

### Strict separator evidence

`PlantSeparatorEvidence` adapts an already solved separator into the same immutable plant registry
and snapshot model. Select `GAS_SCRUBBER`, `TWO_PHASE_OIL`, `TWO_PHASE_WATER`, or `THREE_PHASE` so
the required phase observations are explicit. The adapter reads the existing Souders-Brown gas-load,
K-value, droplet-cut, inlet-momentum, oil/water residence, live liquid-level, and three-phase
interface-settling calculations. It does not run or retain the separator.

The strict path requires the exact completed calculation ID, full-candidate convergence, vessel
diameter and length, inlet-nozzle diameter, and every applicable HLL/NLL/NIL and effective-length
input. This intentionally disables the convenience geometry fallbacks used by interactive
separator calculations: missing design data becomes unavailable evidence, never zero utilization.
The liquid-level and three-phase interface-settling limits are caller-owned installed limits.

```java
PlantSeparatorEvidence separatorEvidence = PlantSeparatorEvidence
    .builder("Plant", "Separation", calculationId, separator,
        PlantSeparatorEvidence.Profile.THREE_PHASE,
        "approved vessel rating revision 4")
    .maximumLiquidLevelFraction(0.80)
    .minimumInterfaceSettlingMinutes(1.0)
    .convergenceComplete(model.isModelConverged())
    .build();

if (!separatorEvidence.isComplete()) {
  throw new IllegalStateException(separatorEvidence.getDiagnostics().toString());
}
PlantUtilizationSnapshot separatorSnapshot =
    separatorEvidence.toPlantUtilizationSnapshot();
```

Java serialization, JPype getters, `toPlantUtilizationSnapshot()`, and `toJson()` preserve the same
detached values, physical margins, units, bases, and diagnostics. A physical limit violation remains
complete and is interpreted according to the severity of the corresponding installed separator
constraint. Carry-over/carry-under and slug handling are not inferred: they remain separate required
coverage until a qualified provider, measured correlation, or installed slug-volume rating is
declared and validated.

### Strict piping evidence

`PlantPipelineEvidence` snapshots one completed `PipeBeggsAndBrills` calculation into six
deterministic hydraulic/thermal rows: maximum absolute pressure, total pressure drop, receiving
pressure, maximum mixture superficial velocity, and minimum/maximum bulk-fluid temperature. Each
row retains the governing solved-profile node and distance from the inlet.

The caller must explicitly declare every installed limit and confirm that length, diameter, and
roughness came from the stated line-list or design source. The adapter never treats the pipe's
constructor defaults, auto-sizing values, API RP 14E result, FIV/FRMS/AIV screening result, or a
missing rating as installed capacity.

```java
PlantPipelineEvidence pipelineEvidence = PlantPipelineEvidence
    .builder("Plant", "Gathering", calculationId, pipeline,
        "approved line list revision 7 and operating case")
    .geometryVerified(true)
    .maximumPressureBara(120.0)
    .maximumPressureDropBar(12.0)
    .minimumReceivingPressureBara(70.0)
    .maximumMixtureVelocityMetresPerSecond(14.0)
    .minimumTemperatureCelsius(-20.0)
    .maximumTemperatureCelsius(80.0)
    .convergenceComplete(model.isModelConverged())
    .build();

if (!pipelineEvidence.isComplete() || !pipelineEvidence.isFeasible()) {
  throw new IllegalStateException(pipelineEvidence.getDiagnostics().toString());
}
PlantUtilizationSnapshot pipelineSnapshot =
    pipelineEvidence.toPlantUtilizationSnapshot();
```

Exact pipe and candidate calculation identity, a complete solved profile, monotonic profile
distance, finite values, and complete convergence are mandatory. Isothermal calculations carry the
single authoritative temperature observation returned by the pipe; non-isothermal calculations
retain the actual temperature extrema. Java getters, serialization, JPype use, and `toJson()`
expose the same callback-free values and physical margins. Hydrate/wax/liquid-dropout envelopes,
noise/FIV qualification, erosion acceptance, slugging, transient integrity, and pipeline-solver
changes remain separately owned and must be registered only from qualified evidence.

## Expected equipment coverage before qualification

`UtilizationCoverageReport` captures evidence for explicitly declared equipment and constraint
identities. It preserves expected-but-missing equipment, expected-but-missing constraints, and
discovered constraints missing from the supplied `PlantConstraintRegistry`. A report with no
declared equipment is incomplete. Completion applies to `DECLARED_EQUIPMENT_AND_CONSTRAINTS`;
the caller remains responsible for choosing the engineering scope.

`EquipmentCapacityConstraintResolver` combines direct equipment definitions with the selected
capacity strategy. Direct definitions take precedence **per constraint name**; they do not suppress
unrelated strategy constraints. A disabled or incomplete direct override remains authoritative for
its name. Discovery is deterministic and does not invoke value suppliers; report capture samples
each selected enabled supplier once and retains only immutable values.

| Evidence gap | Coverage result |
|---|---|
| Missing equipment, constraint, or registration | Explicit incomplete row or diagnostic |
| Unset current value, missing rating, non-finite result, or supplier exception | Incomplete; unavailable numbers are Java `NaN` and JSON `null` |
| Missing unit, measurement/rating basis, or provenance | Incomplete; no inferred unit or basis |
| Default fallback or advisory/design-only constraint | `SCREENING_ONLY`; cannot qualify installed rated capacity |
| Registry unit, direction, severity, enablement, explicit basis, or supplied provenance differs | `METADATA_MISMATCH` |
| Sample outside the source or registry validity range | `OUTSIDE_VALIDITY_RANGE` |
| Intentionally disabled equipment or constraint | Retained as `DISABLED`; its supplier is not sampled |

For example, after obtaining the configured `equipment` object named `K-101`, register its installed
power evidence and declare the required identity:

```java
CapacityConstraint power = new CapacityConstraint("power", "kW", CapacityConstraint.ConstraintType.HARD)
    .setDesignValue(1000.0).setCurrentValue(750.0)
    .setDataSource("vendor datasheet revision 3");
equipment.addCapacityConstraint(power);
PlantConstraintRegistry registry = new PlantConstraintRegistry();
registry.registerEquipmentConstraint("NorthPlant", "Compression", "K-101", "power", "shaft power",
    PlantConstraintDefinition.Category.DESIGN, "rotating equipment", "K-101 datasheet", power);

UtilizationCoverageReport coverage = UtilizationCoverageReport.builder("NorthPlant")
    .expectConstraint("Compression", "K-101", "power")
    .equipment("Compression", equipment).registry(registry).build();
```

This example uses an explicitly supplied operating-point value. In a live process, use the
equipment's actual value supplier after the relevant process calculation. Inspect `isComplete()`,
`getDiagnostics()`, `getRequiredConstraintIds()`, and `getRows()` from Java or JPype; `toJson()` exposes
the same frozen evidence with null numeric gaps. With only the shown power constraint, the example
reports utilization `0.75`; any additional discovered constraint must also have complete registration
and evidence. The example API chain is exercised by `UtilizationCoverageReportTest`.

Bind this registry-qualified report using `PlantUtilizationSnapshot.Builder.expectedCoverage(coverage)`.
The report must carry the matching registry digest. Snapshot qualification then also requires
declared convergence and exact-calculation runtime evidence. Coverage preflight alone does not prove
convergence, feasibility, complete process balances, shared-resource capacity, or operating approval.
Rebuild the report after changing installed capacity inputs; it never updates itself from live state.

For preflight without a registry, use `basis(areaName, equipmentName, constraintName, basis)` to supply
an explicit measurement basis. Such reports describe declared equipment evidence but cannot be used
as registry-qualified snapshot coverage. Do not turn a default/advisory rating into installed
capacity by adding a label: record and use the actual equipment or vendor rating.

`CapacityConstraint.hasCurrentValue()` distinguishes an unset legacy zero default from an explicit
zero or available supplier, without sampling that supplier. `getCurrentValue()` retains its previous
zero-default behavior. The new assignment flag survives Java serialization after explicit assignment
or a successful sample. Old serialized constraints lack that flag and require reassignment or a
reattached supplier before their cached values qualify as current evidence.

## Important: Constraints Disabled by Default

> **Enablement is equipment-specific.** Separator, valve, base `Pipeline`, pump, and manifold
> defaults commonly start disabled. `PipeBeggsAndBrills` velocity/FIV/AIV constraints start
> enabled; chartless compressors disable map-only metrics. Inspect the actual objects and
> enable the limits required by the study. The optimizer can use fallback rules when no
> constraints are enabled.

### Why Constraints Are Disabled by Default

To maintain backward compatibility with existing simulations, constraints are created but **not enabled** when equipment is initialized. This ensures that:

1. **Existing code works unchanged** - Simulations that don't use capacity analysis continue to work
2. **Explicit opt-in for capacity analysis** - You must explicitly enable constraints to use them
3. **No unexpected optimization failures** - Optimizer falls back to traditional methods if no constraints are enabled

### How to Enable Constraints

```java
// Method 1: Use pre-configured constraint sets (Separator example)
Separator separator = new Separator("HP Separator", feed);
separator.useEquinorConstraints();  // Enables K-value, droplet, momentum, retention times
// OR
separator.useAPIConstraints();      // Enables K-value and retention times per API 12J
// OR
separator.useAllConstraints();      // Enables all 5 constraint types

// Method 2: Enable individual constraints
separator.getCapacityConstraints().get(StandardConstraintType.SEPARATOR_K_VALUE.getName())
    .setEnabled(true);

// Method 3: Enable all constraints at once
separator.enableAllConstraints();      // Enables all constraints on this equipment

// Method 4: Disable constraints (return to default)
separator.disableConstraints();     // Disables all constraints
```

### How to Disable Constraints for What-If Analysis

For what-if scenarios or focused analysis, you can disable constraints at multiple levels:

```java
// Method 1: Disable a specific constraint
Map<String, CapacityConstraint> constraints = separator.getCapacityConstraints();
constraints.get("gasLoadFactor").setEnabled(false);  // Disable one constraint
constraints.get("gasLoadFactor").setEnabled(true);   // Re-enable

// Method 2: Disable all constraints on a single equipment
int disabled = separator.disableAllConstraints();  // Returns count of disabled constraints
int enabled = separator.enableAllConstraints();    // Re-enable all

// Method 3: Disable all constraints across entire ProcessSystem
int total = processSystem.disableAllConstraints();  // All equipment, all constraints
processSystem.enableAllConstraints();               // Re-enable all

// Method 4: Disable all constraints across ProcessModule
for (ProcessSystem system : processModule.getAllProcessSystems()) {
    system.disableAllConstraints();
    system.enableAllConstraints();
}

// Method 5: FULLY exclude equipment from optimization (not just disable constraints)
separator.setCapacityAnalysisEnabled(false);  // Completely excluded from capacity analysis
separator.setCapacityAnalysisEnabled(true);   // Re-include
```

**Comparison of Disable Methods:**

| Method | Scope | Effect on Optimization |
|--------|-------|------------------------|
| `constraint.setEnabled(false)` | One constraint | Uses other constraints or fallback rules |
| `equipment.disableAllConstraints()` | All constraints on equipment | Falls back to type-specific capacity rules |
| `processSystem.disableAllConstraints()` | All equipment in system | All use fallback capacity rules |
| `equipment.setCapacityAnalysisEnabled(false)` | Equipment level | **Fully excluded** from optimization |

### How the Optimizer Uses Constraints

The `ProductionOptimizer` uses a multi-level decision process:

```java
// Inspect the same enablement inputs used by the optimizer.
boolean included = !(equipment instanceof neqsim.process.equipment.ProcessEquipmentBaseClass)
    || ((neqsim.process.equipment.ProcessEquipmentBaseClass) equipment).isCapacityAnalysisEnabled();
boolean hasEnabledConstraints = equipment.getCapacityConstraints().values().stream()
    .anyMatch(CapacityConstraint::isEnabled);
logger.info("{}: included={}, enabled constraints={}", equipment.getName(),
    included, hasEnabledConstraints);
```

**Key behavior:**
- `setCapacityAnalysisEnabled(false)` → Equipment completely skipped by optimizer
- `disableAllConstraints()` → Equipment still checked using fallback capacity rules
- Individual constraints enabled → Uses `getMaxUtilization()` from constraint framework

### Summary: Constraint Enablement by Equipment Type

| Equipment Type | Default State | How to Enable |
|---------------|---------------|---------------|
| **Separator** | All disabled | `useEquinorConstraints()`, `useAPIConstraints()`, `enableAllConstraints()` |
| **ThreePhaseSeparator** | All disabled | Same as Separator |
| **GasScrubber** | K-value only enabled | `useGasScrubberConstraints()` (automatic in constructor) |
| **Compressor** | All enabled | (constraints created by `autoSize()` are enabled by default) |
| **ThrottlingValve** | All disabled | `enableAllConstraints()` |
| **Pipeline** (base) | Disabled defaults | `enableAllConstraints()` |
| **PipeBeggsAndBrills** | Velocity/FIV/AIV enabled | Inspect/set relevant limits |
| **Pump** | All disabled | `enableAllConstraints()` |
| **Manifold** | All disabled | `enableAllConstraints()` |

---

## Relationship to Existing Bottleneck Analysis

NeqSim already provides bottleneck analysis via `ProcessEquipmentInterface`:

| Existing Method | Description |
|-----------------|-------------|
| `getCapacityDuty()` | Current operating load (power, flow, etc.) |
| `getCapacityMax()` | Maximum design capacity |
| `getRestCapacity()` | Available headroom |
| `ProcessSystem.getBottleneck()` | Equipment with highest utilization |

The new `CapacityConstrainedEquipment` interface **extends** this by allowing:
- Multiple named constraints per equipment
- Constraint severity levels (HARD/SOFT/DESIGN)
- Live value suppliers for dynamic updates
- Detailed bottleneck information via `findBottleneck()`

**The systems are integrated**: `ProcessSystem.getBottleneck()` automatically uses multi-constraint data when available, falling back to single-capacity metrics for equipment that doesn't implement the new interface.

## Architecture

The framework integrates with existing bottleneck analysis in `neqsim.process.equipment.capacity`:

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ProcessModule                                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  getConstrainedEquipment() │ findBottleneck() │ ...            │ │
│  │  (recursively searches all nested modules and systems)         │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌──────────────────────┐       │
│  │    ProcessModule     │             │    ProcessSystem     │       │
│  │    (nested)          │             │                      │       │
│  └──────────────────────┘             └──────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                         ProcessSystem                                │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  getBottleneck()  │  findBottleneck()  │  getRestCapacity()   │  │
│  │  (unified: checks both single and multi-constraint equipment) │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌─────────────────────────────┐│
│  │  Traditional API     │             │  Multi-Constraint API       ││
│  │  getCapacityDuty()   │             │  CapacityConstrainedEquipment│
│  │  getCapacityMax()    │             │  ├─ getCapacityConstraints()││
│  │  getRestCapacity()   │             │  ├─ getBottleneckConstraint()│
│  └──────────────────────┘             │  └─ getMaxUtilization()     ││
│                                       └─────────────────────────────┘│
│                                                   │                  │
│                                                   ▼                  │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                    CapacityConstraint                            ││
│  │  ┌──────────────────────────────────────────────────────────┐   ││
│  │  │ name │ type │ designValue │ maxValue │ valueSupplier │...│   ││
│  │  └──────────────────────────────────────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

## Core Classes

### 1. CapacityConstraint

The fundamental building block representing a single capacity limit on equipment.

```java
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;

// Create a constraint with fluent builder pattern
CapacityConstraint speedConstraint = new CapacityConstraint("speed", "RPM", ConstraintType.HARD)
    .setDesignValue(10000.0)           // Design operating point (RPM)
    .setMaxValue(11000.0)              // Absolute maximum (trip point)
    .setMinValue(5000.0)               // Minimum stable operation
    .setValueSupplier(() -> compressor.getSpeed());  // Live value getter
```

#### Constraint Types

| Type | Description | Example |
|------|-------------|---------|
| `HARD` | Absolute limit - equipment trip or damage if exceeded | Compressor max speed, surge limit |
| `SOFT` | Operational limit - reduced efficiency or accelerated wear | High discharge temperature |
| `DESIGN` | Normal operating limit - design basis | Separator gas load factor |

#### Key Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getCurrentValue()` | `double` | Current value from the valueSupplier |
| `getUtilization()` | `double` | Current/design for maximum constraints; minimum/current for minimum constraints (1.0 = limit) |
| `getUtilizationPercent()` | `double` | Utilization as percentage |
| `isViolated()` | `boolean` | True if utilization > 1.0 |
| `isHardLimitExceeded()` | `boolean` | True if a HARD maximum is exceeded or a HARD minimum is undershot |
| `isNearLimit()` | `boolean` | True if above warning threshold (default 90%) |
| `getMargin()` | `double` | Remaining normalized headroom (1.0 - utilization) |
| `getConfidence()` | `double` | Evidence-quality score in [0, 1], or `NaN` when unset |
| `isCurrentValueWithinValidityRange()` | `boolean` | Whether the current value is inside the explicitly assigned range |

> **Minimum constraints:** Set `minValue` and leave `designValue` unset. This makes utilization
> `minimum/current`, so safe values above the minimum remain below 100%. Setting the same value as
> both design and minimum changes the direction to `current/design` and is incorrect for minimum
> NPSH headroom, minimum stable flow, residence time, or similar limits. Required NPSH margin is
> service- and pump-specific; use vendor or applicable Hydraulic Institute design data instead of
> treating a generic screening default as an approved installed limit.

`ProcessModelSimulationEvaluator.BottleneckStatus` and `ThroughputCaseRow` preserve this direction
with `isMinimumConstraint()`. Their reported design value is the applicable finite limit. The
engineering-unit capacity margin is positive on the feasible side: `limit - current` for maximum
constraints and `current - limit` for minimum constraints. This convention prevents a minimum-only
constraint's internal unset design sentinel from appearing in JSON or CSV throughput results.

The same throughput rows preserve `CapacityConstraint.getDataSource()` as `dataSource` in Java,
JSON, and CSV. Set a concise provenance tag such as `mechanicalDesign`, `installedDataSheet`, or
`operatingEnvelope` when defining the limit. Untagged and legacy rows report `not_set`; downstream
optimizers should retain this tag with recommendations rather than treating all limits as equally
authoritative.

#### Confidence and validity metadata

Use confidence and validity metadata to state how strongly the limit is supported and where its
basis applies:

```java
CapacityConstraint gasCapacity = new CapacityConstraint("gasFlow", "kg/h", ConstraintType.HARD)
    .setDesignValue(12000.0)
    .setCurrentValue(10000.0)
    .setDataSource("installedDataSheet")
    .setConfidence(0.95)
    .setValidityRange(8000.0, 12000.0);

if (!gasCapacity.hasValidityRange()
    || !gasCapacity.isCurrentValueWithinValidityRange()) {
  // Require engineering review before relying on this limit outside its evidence range.
}
```

`confidence` is an evidence-quality score from zero to one. It is not a probability of safe
operation, constraint satisfaction, or model accuracy. The validity bounds are inclusive, finite,
and use the constraint's own unit. An unset confidence or range remains explicitly distinguishable
through `hasConfidence()` and `hasValidityRange()`; the numeric getters return `NaN` when unset.
Invalid scores, non-finite bounds, and reversed ranges fail fast.

When the constraint becomes the active full-model bottleneck,
`ProcessModelSimulationEvaluator.BottleneckStatus` and `ThroughputCaseRow` preserve the same
confidence, validity bounds, and snapshotted in-range result. Java callers can use
`hasConfidence()`, `getConfidence()`, `hasValidityRange()`, `getValidityMinimum()`,
`getValidityMaximum()`, and `isCurrentValueWithinValidityRange()`. JSON rows include the
`hasConfidence` and `hasValidityRange` flags; unset numeric or applicability values are
`null`. CSV traces include the same flags and leave unset confidence, bounds, and applicability
cells blank. Public snapshot constructors also normalize inconsistent enabled metadata (non-finite
confidence or bounds, confidence outside [0, 1], or reversed bounds) to this unset state.
Applicability is derived from each snapshot's current value and retained bounds rather than accepted
as caller-provided state. Dynamic value suppliers are read once per bottleneck candidate, and that
same scalar is used for utilization and applicability. This prevents legacy, malformed, or unqualified limits from being mistaken
for zero-confidence or out-of-range evidence.

This first validity contract is deliberately scalar. Compressor maps and other models whose
applicability depends on several variables still require a separate multidimensional operating
envelope. Confidence and validity metadata do not change utilization, feasibility, or optimizer
behavior by themselves; downstream ranking must preserve and assess them explicitly.

For full-model diagnostics, `ProcessModelSimulationEvaluator.rankCapacityConstraints(model)`
returns an immutable list of every enabled capacity constraint in descending utilization order.
Equal-utilization constraints retain process-model registration order, including the declared order
of built-in strategy-generated constraints. Each
`BottleneckStatus.getEvidenceApplicability()` value is one of `WITHIN_VALIDITY_RANGE`,
`OUTSIDE_VALIDITY_RANGE`, or `NOT_ASSESSED`. This makes unsupported and extrapolated limits
visible beside the engineering ranking without allowing confidence or applicability to change
utilization, feasibility, or order. The method snapshots every dynamic value supplier exactly once;
enabled constraints with undefined (`NaN`) utilization remain visible at the end of the ranking;
`findActiveBottleneck(model)` remains the lower-allocation API when only the leading constraint is
needed.

After `ProcessModelSimulationEvaluator.evaluate(...)`, use
`EvaluationResult.getRankedCapacityConstraints()` instead of rescanning the live model. The result
owns an immutable snapshot tied to that exact operating point, and its legacy active bottleneck is
the first finite-utilization item in the same ranking. `ProcessModelThroughputOptimizer` copies the
snapshot into each `ThroughputCaseRow`; JSON case tables therefore preserve near-active limits and
bottleneck switching across the throughput search. The flat CSV remains a one-row-per-case summary
of the leading limit; use JSON or the Java/JPype list when the complete per-case ranking is needed.

### 2. CapacityConstrainedEquipment (Interface)

Interface that equipment classes implement to participate in capacity tracking.

```java
public interface CapacityConstrainedEquipment {
    // Get all constraints
    Map<String, CapacityConstraint> getCapacityConstraints();

    // Get the most limiting constraint
    CapacityConstraint getBottleneckConstraint();

    // Check constraint status
    boolean isCapacityExceeded();
    boolean isHardLimitExceeded();
    boolean isNearCapacityLimit();

    // Get utilization metrics
    double getMaxUtilization();
    double getMaxUtilizationPercent();
    double getAvailableMargin();

    // Modify constraints
    void addCapacityConstraint(CapacityConstraint constraint);
    boolean removeCapacityConstraint(String constraintName);
    void clearCapacityConstraints();
}
```

### 3. StandardConstraintType (Enum)

Predefined constraint types for common equipment with standardized names and units.

```java
import neqsim.process.equipment.capacity.StandardConstraintType;

CapacityConstraint speed = StandardConstraintType.COMPRESSOR_SPEED.createConstraint()
    .setDesignValue(10000.0)
    .setCurrentValue(9000.0);
CapacityConstraint npsh = StandardConstraintType.PUMP_NPSH_MARGIN.createConstraint()
    .setMinValue(1.0)
    .setCurrentValue(2.0);
logger.info("Speed utilization: {}%, NPSH utilization: {}%",
    speed.getUtilizationPercent(), npsh.getUtilizationPercent());
```

### 4. BottleneckResult

Result class returned by `ProcessSystem.findBottleneck()`.

```java
BottleneckResult result = process.findBottleneck();

if (result.hasBottleneck()) {
    logger.info("Bottleneck: " + result.getEquipmentName());
    logger.info("Constraint: " + result.getConstraint().getName());
    logger.info("Utilization: " + result.getUtilizationPercent() + "%");
}
```

## Adding & Configuring Custom Constraints

There are three ways to give an equipment new constraint functionality, ordered from the
quickest (runtime, no subclassing) to the most reusable (built into an equipment type). All
three produce ordinary `CapacityConstraint` objects, so they surface identically in
`getMaxUtilization()`, `getBottleneckConstraint()`, `findBottleneck()`, and the utilization
snapshot.

### The constraint anatomy (what you configure)

Every constraint is built with the fluent `CapacityConstraint` API. The two constructors are:

```java
new CapacityConstraint("name", "unit", ConstraintType.HARD);  // explicit unit + type
new CapacityConstraint("name");                                // defaults: unit "", type SOFT
```

Configurable properties (all are optional fluent setters returning `this`):

| Setter | Purpose | Default |
|--------|---------|---------|
| `setDesignValue(double)` | Limit used for utilization = current / design | required |
| `setValueSupplier(DoubleSupplier)` | **Live** current value, re-evaluated each query | none |
| `setCurrentValue(double)` | Static current value (use when there is no live source) | Legacy zero; `hasCurrentValue()` is false until assigned |
| `setMaxValue(double)` | Absolute trip point for `HARD` constraints | none |
| `setMinValue(double)` | Minimum stable operating point | none |
| `setWarningThreshold(double)` | Near-limit early warning (fraction, e.g. 0.9) | 0.9 |
| `setUnit(String)` | Unit label (when the 1-arg constructor was used) | "" |
| `setDescription(String)` | Human-readable description | "" |
| `setDataSource(String)` | Provenance tag (e.g. `"mechanicalDesign"`, `"user"`) | "not_set" |
| `setEnabled(boolean)` | Whether the optimizer/analysis counts it | `true` |

> **Live vs. static value:** prefer `setValueSupplier(...)` so the constraint tracks the
> simulation. A manually built constraint is **enabled by default**, so it counts as soon as
> you add it (equipment defaults vary; strategy constraints are not universally disabled).

### Level 1 — Add a custom constraint to one equipment instance (no subclassing)

The fastest path: build a constraint with a live supplier and register it on the equipment.
Use this for one-off or study-specific limits.

```java
// Example: cap a heater on its outlet temperature
Heater heater = new Heater("H-100", feed);
heater.setOutTemperature(100.0, "C");
heater.run();

CapacityConstraint tempLimit =
    new CapacityConstraint("outletTemperature", "C", ConstraintType.SOFT)
        .setDesignValue(120.0)                                  // design limit
        .setWarningThreshold(0.9)                               // warn at 90%
        .setDescription("Illustrative maximum process outlet temperature")
        .setValueSupplier(() -> heater.getOutletStream()
            .getTemperature("C"));                              // live value

heater.addCapacityConstraint(tempLimit);                        // active immediately
double util = heater.getMaxUtilization();                       // includes the new limit
```

Reconfigure or remove it at any time:

```java
heater.getCapacityConstraints().get("outletTemperature").setDesignValue(110.0);
heater.getCapacityConstraints().get("outletTemperature").setEnabled(false); // what-if
heater.removeCapacityConstraint("outletTemperature");                       // remove
```

### Level 2 — Give an equipment type built-in default constraints (subclass)

To make a constraint part of every instance of an equipment type, override the
`initializeDefaultConstraints()` hook on `ProcessEquipmentBaseClass`. It is called lazily the
first time constraints are accessed (and after deserialization), so it is the right place to
register type-specific constraints.

```java
public class RatedStream extends Stream {
  private static final long serialVersionUID = 1L;

  public RatedStream(String name, StreamInterface source) {
    super(name, source);
  }

  @Override
  protected void initializeDefaultConstraints() {
    addCapacityConstraint(new CapacityConstraint("installedMassFlow", "kg/hr",
        ConstraintType.HARD)
        .setDesignValue(12000.0)
        .setDataSource("illustrative installed throughput rating")
        .setValueSupplier(() -> getFlowRate("kg/hr")));
  }
}
```

Follow the framework convention: if your equipment should preserve backward compatibility,
create the constraints **disabled** (`setEnabled(false)`) and provide a `useXxxConstraints()` /
`enableAllConstraints()` method so users opt in (see how `Separator` exposes
`useEquinorConstraints()`).

### Level 3 — Derive constraints from the mechanical-design envelope

When the limit is a property of the equipment's design envelope (max design power, flow,
pressure drop, velocity, Cv, duty), configure it on the `MechanicalDesign` and let the bridge
build the constraint for you. No `CapacityConstraint` object is needed. To support a **new**
design-derived metric, override the matching protected `getOperating*` hook on a
`MechanicalDesign` subclass. This path is documented in full in
[Equipment Utilization via Mechanical Design](equipment_utilization_via_mechanical_design.md):

```java
equipment.getMechanicalDesign().setMaxDesignPower(6000.0);     // kW
equipment.applyMechanicalDesignCapacityConstraints();          // opt-in bridge
double util = equipment.getMaxUtilization();                   // now includes design power
```

#### Plant-wide one-call activation

After auto-sizing a whole flowsheet, you do not need to loop over every unit. `ProcessSystem`
and `ProcessModel` both expose a bulk helper that calls
`applyMechanicalDesignCapacityConstraints()` on every contained piece of equipment and returns
the number of units that registered at least one design-derived constraint. The call is
**idempotent** (constraints use stable names) and safe to re-run after re-sizing:

```java
plant.autoSizeEquipment(1.20);                 // size every unit with a 20% margin
int n = plant.applyMechanicalDesignCapacityConstraints();   // activate utilization plant-wide
// n == number of units that now expose design-envelope constraints
String snapshot = plant.getUtilizationSnapshotJson();        // side-effect-free read
```

For a multi-area `ProcessModel` the same method walks every area's `ProcessSystem`, so a single
call activates utilization tracking across the entire plant.

### Which level should I use?

| Need | Use |
|------|-----|
| A one-off limit for a single run/study | **Level 1** (instance `addCapacityConstraint`) |
| A limit that every instance of a new equipment type should have | **Level 2** (`initializeDefaultConstraints`) |
| A limit that is part of the design envelope (power, flow, dP, velocity, Cv, duty) | **Level 3** (mechanical design + bridge) |

## Integration with AutoSizing and Mechanical Design

### How AutoSizing Creates Constraints

Run the feed and each upstream unit before sizing downstream equipment. Auto-sizing
creates equipment-specific limits; enable the applicable constraints and inspect their
values before optimizing. It does not qualify missing installed data.

```java
// Auto-sizing creates constraints automatically
Separator sep = new Separator("HP-Sep", feed);
sep.run();
sep.autoSize(1.2);  // 20% safety factor

// This creates the following constraints:
// - gasLoadFactor: based on K-factor sizing calculation
// - oilRetentionTime/waterRetentionTime: minimum phase retention times

// For compressors, autoSize does even more:
Compressor comp = new Compressor("Export", sep.getGasOutStream());
comp.setOutletPressure(100.0);
comp.run();
comp.autoSize(1.2);

// This creates:
// - speed constraint (from mechanical design)
// - power constraint (from driver sizing)
// - surgeMargin constraint (soft limit)
// AND generates compressor curves, sets solveSpeed=true
```

### Mechanical Design as Source of Constraint Values

The `MechanicalDesign` class provides design values that become constraint limits:

```java
// Mechanical design values feed constraints
Separator sep = new Separator("V-100", feed);
sep.initMechanicalDesign();
SeparatorMechanicalDesign mechDesign = (SeparatorMechanicalDesign) sep.getMechanicalDesign();

// Set design limits that will become constraints
mechDesign.setMaxDesignVolumeFlow(5000.0);     // m³/hr
mechDesign.setMaxDesignPressureDrop(2.0);      // bara → pressureDrop constraint

// For pipelines
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("L-100", feed);
pipe.initMechanicalDesign();
PipelineMechanicalDesign pipeDesign = (PipelineMechanicalDesign) pipe.getMechanicalDesign();

// Design values → constraints
pipeDesign.setMaxDesignVelocity(15.0);           // → velocity constraint
pipeDesign.setMaxDesignPressureDrop(5.0);        // → pressureDrop constraint
pipeDesign.setMaxDesignVolumeFlow(10000.0);      // → volumeFlow constraint
```

### Complete Workflow: Design → Constraints → Optimization

```java
// 1. Create process; solve before auto-sizing
ProcessSystem process = new ProcessSystem();

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
process.add(feed);

Separator sep = new Separator("HP-Sep", feed);
process.add(sep);

Compressor comp = new Compressor("K-100", sep.getGasOutStream());
comp.setOutletPressure(100.0);
comp.run();
process.add(comp);

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Export", comp.getOutletStream());
pipe.setLength(30000.0);
pipe.setDiameter(0.3);
process.add(pipe);

// 2. Solve and size the vessel and compressor. Keep the installed pipe geometry:
// velocity-only auto-sizing does not enforce the hydraulic pressure budget of a long line.
process.run();
sep.autoSize(1.2);
comp.autoSize(1.2);
pipe.setMaxDesignVelocity(15.0);
pipe.initMechanicalDesign();
pipe.getMechanicalDesign().setMaxDesignPressureDrop(10.0); // bar, illustrative installed limit
sep.enableConstraints("gasLoadFactor");
pipe.enableAllConstraints();
process.applyMechanicalDesignCapacityConstraints();
process.run();

// 3. Constraints are now active and can be queried
logger.info("Equipment constraints after auto-sizing:");
for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    logger.info(((ProcessEquipmentInterface) equip).getName() + ":");
    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        logger.info(String.format("  %s: %.2f / %.2f %s%n",
            c.getName(), c.getCurrentValue(), c.getDesignValue(), c.getUnit()));
    }
}

// 4. Use in optimization - optimizer checks ALL constraints
ProductionOptimizer.OptimizationConfig config =
    new ProductionOptimizer.OptimizationConfig(1000.0, 50000.0);
ProductionOptimizer optimizer = new ProductionOptimizer();
ProductionOptimizer.OptimizationResult result =
    optimizer.optimize(process, feed, config, null, null);

// 5. The bottleneck could be separator, compressor, or pipeline
if (result.getBottleneck() != null) {
    logger.info("Bottleneck: {}", result.getBottleneck().getName());
}
```

### Equipment Currently Supporting CapacityConstrainedEquipment

| Equipment | Constraints | Set By |
|-----------|-------------|--------|
| **Separator** | gasLoadFactor, liquidResidenceTime | autoSize(), setDesignGasLoadFactor() |
| **Compressor** | speed, power, ratedPower, surgeMargin, stonewallMargin | autoSize(), setMaximumSpeed(), getMechanicalDesign().setMaxDesignPower() |
| **Pump** | npshMargin, power, flowRate | getMechanicalDesign().setMaxDesignPower(), mechanical design |
| **ThrottlingValve** | valveOpening, cvUtilization, AIV | autoSize(), setCv(), setMaxDesignAIV() |
| **Pipeline** | velocity, pressureDrop, volumeFlow, FIV_LOF, FIV_FRMS | autoSize(), getMechanicalDesign().setMaxDesignVelocity() |
| **PipeBeggsAndBrills** | velocity, LOF, FRMS, AIV | autoSize(), setMaxDesignVelocity(), setMaxDesignLOF(), setMaxDesignAIV() |
| **AdiabaticPipe** | velocity, LOF, FRMS, AIV, pressureDrop | autoSize(), setMaxDesignVelocity(), setMaxDesignLOF(), setMaxDesignAIV() |
| **Manifold** | headerVelocity, branchVelocity, headerLOF, headerFRMS, branchLOF, branchFRMS | autoSize(), setMaxDesignVelocity() |
| **Heater/Cooler** | duty, outletTemperature | autoSize(), setMaxDesignDuty() |

### How to Override autoSize Constraints

After `autoSize()` creates constraints, you can override them:

```java
// 1. Override BEFORE autoSize (parameter will be used in sizing)
separator.setDesignGasLoadFactor(0.15);  // Your K-factor
separator.autoSize(1.2);                  // Uses your K-factor

// 2. Override AFTER autoSize (keeps sizing, changes constraint limit)
compressor.autoSize(1.2);
compressor.getMechanicalDesign().setMaxDesignPower(6000.0);  // Override constraint limit (kW)
compressor.setMaximumSpeed(12000.0);      // Override speed limit (RPM)

// 3. Manually set constraint on existing equipment
CapacityConstraint customPower = new CapacityConstraint("powerLimit", "kW", ConstraintType.HARD)
    .setDesignValue(5000.0)
    .setValueSupplier(() -> compressor.getPower("kW"));
compressor.addCapacityConstraint(customPower);

// 4. Remove auto-generated constraint and add custom one
compressor.removeCapacityConstraint("power");  // Remove default
compressor.addCapacityConstraint(customPower); // Add custom
```

### Constraint Priority After Override

When you override a constraint parameter, the priority is:
1. **User-specified value** (highest) - via setter methods
2. **autoSize calculated value** - based on flow conditions
3. **Mechanical design default** - from design standards
4. **Hard-coded default** (lowest) - in equipment class

---

## Usage Examples

### Basic Usage: Process-Wide Bottleneck Detection

```java
// Use an existing configured process containing rated equipment.
process.run();

// Find the process bottleneck
BottleneckResult bottleneck = process.findBottleneck();
logger.info("Process bottleneck: " + bottleneck.getEquipmentName());
logger.info("Limiting constraint: {}", bottleneck.getConstraintName());
logger.info("Utilization: " + bottleneck.getUtilizationPercent() + "%");

// Check if any equipment is overloaded
if (process.isAnyEquipmentOverloaded()) {
    logger.info("WARNING: Equipment operating above design capacity!");
}

// Check if any hard limits are exceeded (critical)
if (process.isAnyHardLimitExceeded()) {
    logger.info("CRITICAL: Hard equipment limits exceeded!");
}

// Get utilization summary for all equipment
Map<String, Double> utilization = process.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    logger.info(String.format("%s: %.1f%%\n", entry.getKey(), entry.getValue()));
}

// Get equipment near capacity limit (early warning)
List<String> nearLimit = process.getEquipmentNearCapacityLimit();
if (!nearLimit.isEmpty()) {
    logger.info("Equipment near capacity: " + nearLimit);
}
```

### ProcessModule Support

The capacity constraint framework also works with `ProcessModule`, which can contain multiple `ProcessSystem` instances and nested modules. All constraint methods work recursively across the entire module hierarchy.

```java
import neqsim.process.processmodel.ProcessModule;

// Create a complex module with multiple systems
ProcessModule productionModule = new ProcessModule("Production Platform");

// Add process systems
ProcessSystem separationSystem = new ProcessSystem();
separationSystem.add(inletManifold);
separationSystem.add(hpSeparator);
separationSystem.add(lpSeparator);

ProcessSystem compressionSystem = new ProcessSystem();
compressionSystem.add(lpCompressor);
compressionSystem.add(hpCompressor);
compressionSystem.add(exportPipeline);

productionModule.add(separationSystem);
productionModule.add(compressionSystem);
productionModule.run();

// Find bottleneck across ALL systems in the module
BottleneckResult bottleneck = productionModule.findBottleneck();
if (bottleneck.hasBottleneck()) {
    logger.info("Module bottleneck: " + bottleneck.getEquipmentName());
    logger.info("Constraint: " + bottleneck.getConstraint().getName());
    logger.info("Utilization: " + bottleneck.getUtilizationPercent() + "%");
}

// Check for overloaded equipment across all systems
if (productionModule.isAnyEquipmentOverloaded()) {
    logger.info("WARNING: Equipment overloaded in module!");
}

// Get all constrained equipment from the module
List<CapacityConstrainedEquipment> allConstrained =
    productionModule.getConstrainedEquipment();
logger.info("Found " + allConstrained.size() + " constrained equipment items");

// Get utilization summary across entire module
Map<String, Double> utilization = productionModule.getCapacityUtilizationSummary();
for (Map.Entry<String, Double> entry : utilization.entrySet()) {
    logger.info(String.format("%s: %.1f%%\n", entry.getKey(), entry.getValue()));
}
```

#### Nested Module Support

ProcessModule supports nesting, and constraint methods work recursively:

```java
// Create nested modules
ProcessModule topside = new ProcessModule("Topside");
ProcessModule subsea = new ProcessModule("Subsea");

// Each child must be a ProcessSystem or ProcessModule.
ProcessSystem subseaSystem = new ProcessSystem();
subseaSystem.add(subseaManifold);
subseaSystem.add(flowline);
subseaSystem.add(riser);
subsea.add(subseaSystem);

topside.add(separationSystem);
topside.add(compressionSystem);

// Create master module containing both
ProcessModule field = new ProcessModule("Field Development");
field.add(subsea);
field.add(topside);
field.run();

// findBottleneck() searches recursively through ALL nested modules
BottleneckResult fieldBottleneck = field.findBottleneck();

// All constraint methods work recursively
List<CapacityConstrainedEquipment> allEquipment = field.getConstrainedEquipment();
Map<String, Double> fieldUtilization = field.getCapacityUtilizationSummary();
boolean anyOverloaded = field.isAnyEquipmentOverloaded();
boolean hardLimitExceeded = field.isAnyHardLimitExceeded();
```

#### ProcessModule Constraint Methods

| Method | Description |
|--------|-------------|
| `getConstrainedEquipment()` | Returns all equipment implementing CapacityConstrainedEquipment from all systems and nested modules |
| `findBottleneck()` | Finds the equipment with highest utilization across entire module hierarchy |
| `isAnyEquipmentOverloaded()` | Checks if any equipment exceeds design capacity (utilization > 100%) |
| `isAnyHardLimitExceeded()` | Checks if any HARD constraint limits are exceeded |
| `getCapacityUtilizationSummary()` | Returns Map<String, Double> of equipment name to utilization percentage |
| `getEquipmentNearCapacityLimit()` | Returns list of equipment names near warning threshold |

### Individual Equipment Inspection

```java
// Get a specific compressor
Compressor compressor = (Compressor) process.getUnit("27-KA-01");

// Check overall capacity status
logger.info("Max utilization: " + compressor.getMaxUtilizationPercent() + "%");
logger.info("Available margin: " + compressor.getAvailableMarginPercent() + "%");

// Inspect individual constraints
Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();
for (CapacityConstraint c : constraints.values()) {
    logger.info(String.format("  %s: %.1f / %.1f %s (%.1f%% utilized)\n",
        c.getName(), c.getCurrentValue(), c.getDesignValue(),
        c.getUnit(), c.getUtilizationPercent()));
}

// Get the bottleneck constraint for this equipment
CapacityConstraint limiting = compressor.getBottleneckConstraint();
if (limiting != null) {
    logger.info("Limiting factor: {}", limiting.getName());
}
```

### Adding Custom Constraints at Runtime

```java
// Use a solved separator that actually contains oil, with configured vessel geometry.
CapacityConstraint residenceTime = StandardConstraintType.SEPARATOR_OIL_RETENTION_TIME
    .createConstraint()
    .setMinValue(3.0)  // minutes; leave designValue unset for a minimum constraint
    .setValueSupplier(() -> separator.calcOilRetentionTime());
separator.addCapacityConstraint(residenceTime);
logger.info("Oil retention utilization: {}%", residenceTime.getUtilizationPercent());

// Remove a specific optional constraint when the study scope calls for it.
separator.removeCapacityConstraint("gasLoadFactor");
```

---

## Activating and Deactivating Constraints

### Adding Constraints to Equipment WITH Existing Constraints

Equipment that already implements `CapacityConstrainedEquipment` (Separator, Compressor, Pipeline, Manifold, etc.) can have additional constraints added:

```java
// Equipment already has default constraints from autoSize() or initialization
Compressor compressor = new Compressor("Export Compressor", feed);
compressor.setOutletPressure(150.0);
compressor.autoSize(1.2);  // Creates speed, power, surgeMargin constraints
compressor.run();

// View existing constraints
logger.info("Current constraints:");
for (CapacityConstraint c : compressor.getCapacityConstraints().values()) {
    logger.info("  " + c.getName() + ": " + c.getDesignValue() + " " + c.getUnit());
}

// ADD a new custom constraint (discharge temperature)
CapacityConstraint tempLimit = new CapacityConstraint("dischargeTemp", "°C", ConstraintType.SOFT)
    .setDesignValue(150.0)  // °C design limit
    .setMaxValue(180.0)     // °C absolute max
    .setUnit("°C")
    .setWarningThreshold(0.9)
    .setValueSupplier(() -> compressor.getOutletStream().getTemperature("C"));

compressor.addCapacityConstraint(tempLimit);

// MODIFY an existing constraint's design value
CapacityConstraint speedConstraint = compressor.getCapacityConstraints().get("speed");
if (speedConstraint != null) {
    speedConstraint.setDesignValue(9500.0);  // Lower speed limit
    speedConstraint.setMaxValue(10000.0);
}
```

### Adding Constraints to Equipment WITHOUT Existing Constraints

All `ProcessEquipmentBaseClass` descendants already expose constraint methods. Start with
a direct constraint; no duplicate map or interface implementation is needed:

```java
Heater heater = new Heater("Process Heater", feed);
heater.setOutTemperature(350.0); // K
heater.run();
heater.addCapacityConstraint(new CapacityConstraint("installedDuty", "kW", ConstraintType.HARD)
    .setDesignValue(5000.0)
    .setValueSupplier(() -> Math.abs(heater.getDuty()) / 1000.0));
```

For a reusable subclass, retain the inherited constraint storage and simulation:

```java
public class ConstrainedHeater extends Heater {
  private static final long serialVersionUID = 1L;

  public ConstrainedHeater(String name, StreamInterface inletStream) {
    super(name, inletStream);
    addCapacityConstraint(new CapacityConstraint("installedDuty", "kW", ConstraintType.HARD)
        .setDesignValue(5000.0)
        .setValueSupplier(() -> Math.abs(getDuty()) / 1000.0));
  }
}
```

### Deactivating (Removing) Constraints

```java
// Remove a specific constraint by name
compressor.removeCapacityConstraint("surgeMargin");

// Remove multiple constraints
compressor.removeCapacityConstraint("stonewallMargin");
compressor.removeCapacityConstraint("dischargeTemp");

// Clear the current definitions; equipment may rebuild defaults on later access.
compressor.clearCapacityConstraints();

// Re-initialize default constraints after clearing
compressor.reinitializeCapacityConstraints();  // Public API
```

### Temporarily Disabling Constraints

For scenarios where you want to keep constraints defined but temporarily ignore them:

```java
CapacityConstraint speedConstraint = compressor.getCapacityConstraints().get("speed");
boolean wasEnabled = speedConstraint.isEnabled();
try {
    speedConstraint.setEnabled(false);
    // Run the explicitly scoped what-if calculation here.
    process.run();
} finally {
    speedConstraint.setEnabled(wasEnabled);
}
```

---

## Constraints in Eclipse VFP Table Generation

Capacity curves and `VFPPROD` tables are different data products. A capacity curve reports
maximum feasible **flow** for pressure boundaries. A `VFPPROD` table reports **bottom-hole
pressure** on flow, THP, water-fraction, gas-ratio, and artificial-lift axes. Do not put
maximum rates into BHP cells or interpret unavailable pressure cells as zero pressure.

### How Constraints Affect VFP Tables

Solve and qualify each hydraulic operating point first. Record constraints alongside the
calculated pressure table; the exporter itself neither solves the process nor enforces
constraints. `LiftCurveTable.toEclipseFormat()` is a commented capacity matrix, not a
complete `VFPPROD` keyword.

### VFP Generation with Constraint Checking

This minimal **serialization example** uses explicitly supplied illustrative BHP data;
it does not claim these numbers were calculated from the feed above. Replace the data
with a validated hydraulic study. The current `getVFPPRODString()` writer emits singleton
zero water/gas-ratio/lift axes, so this example uses only those axes.

```java
EclipseVFPExporter exporter = new EclipseVFPExporter(1);
exporter.setDatumDepth(1500.0); // m
exporter.setFlowRateType("GAS");
exporter.setUnitSystem("METRIC");
exporter.setFlowRates(new double[] {10000.0, 20000.0}); // standard m3/day
exporter.setTHPs(new double[] {30.0, 40.0}); // bara
exporter.setWaterCuts(new double[] {0.0});
exporter.setGORs(new double[] {0.0});
exporter.setALQs(new double[] {0.0});
// Index order: flow, THP, water fraction, gas ratio, lift quantity.
double[][][][][] bhp = new double[2][2][1][1][1];
bhp[0][0][0][0][0] = 35.0;
bhp[1][0][0][0][0] = 42.0;
bhp[0][1][0][0][0] = 45.0;
bhp[1][1][0][0][0] = 52.0;
exporter.setBHPTable(bhp);
String vfpTable = exporter.getVFPPRODString();
```

Validate exported deck syntax and units in the target simulator before using a table in
reservoir calculations; this Java test verifies the exporter API and data serialization.

### Understanding Constraint Impact on VFP

For a solved `process` with a configured compressor, compare bounded throughput searches
and restore the exact original constraint enablement. These are capacity results, not BHP:

```java
ProductionOptimizer optimizer = new ProductionOptimizer();
ProductionOptimizer.OptimizationConfig config =
    new ProductionOptimizer.OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr");
ProductionOptimizer.OptimizationResult baseline =
    optimizer.optimize(process, feed, config, null, null);
CapacityConstraint speedLimit = compressor.getCapacityConstraints().get("speed");
boolean enabled = speedLimit.isEnabled();
try {
    speedLimit.setEnabled(false);
    ProductionOptimizer.OptimizationResult relaxed =
        optimizer.optimize(process, feed, config, null, null);
    if (baseline.isFeasible() && relaxed.isFeasible()) {
        logger.info("Capacity change: {} kg/hr", relaxed.getOptimalRate() - baseline.getOptimalRate());
    }
} finally {
    speedLimit.setEnabled(enabled);
}
```

### Modifying Constraints for What-If VFP Studies

Changing a gas-load-factor limit changes allowable loading; it does not enlarge vessel
geometry. A vessel upgrade requires new diameter/length, a new solve, and new rating
constraints. Archive validated BHP tables with Java 8 APIs:

```java
// In a method declaring throws java.io.IOException, after populating exporter:
java.nio.file.Files.write(java.nio.file.Paths.get("VFP_BASELINE.INC"),
    exporter.getVFPPRODString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
```

### Constraint-Aware Lift Curve Generation

Use `PressureBoundaryOptimizer` for a pressure-boundary capacity matrix. This example
requires `outlet` from a process whose outlet pressure is solved or controlled at 30 bara:

```java
PressureBoundaryOptimizer boundary = new PressureBoundaryOptimizer(process, feed, outlet);
boundary.setMinFlowRate(1000.0);
boundary.setMaxFlowRate(20000.0);
boundary.setAutoConfigureCompressors(false); // preserve the model's configured maps
LiftCurveTable table = boundary.generateLiftCurveTable(
    new double[] {40.0, 50.0}, new double[] {30.0}, "bara");
for (int i = 0; i < 2; i++) {
    logger.info("Inlet row {}: max flow={} kg/hr, power={} kW", i,
        table.getFlowRate(i, 0), table.getPower(i, 0));
}
```

The wrapper checks the requested outlet pressure; it does not retarget valve or compressor
setpoints. See [Pressure Boundary Optimization](pressure_boundary_optimization.md).

### Summary: Constraint Management for VFP Tables

| Action | API | Interpretation |
|---|---|---|
| Add or tighten a maximum | `addCapacityConstraint`, `setDesignValue` | May reduce feasible capacity |
| Tighten a minimum | `setMinValue` with design unset | May reduce feasible capacity |
| Disable one restriction | `setEnabled(false)` | Other equipment limits and fallback rules still apply |
| Exclude equipment analysis | `setCapacityAnalysisEnabled(false)` | Explicitly changes the study scope |
| Upgrade installed equipment | Change geometry/rating, solve, regenerate evidence | New physical candidate, then a new table |

### Integration with Optimization

```java
/** Finds a bounded feasible throughput; rates and tolerance are in kmol/hr. */
public double findMaxThroughput(ProcessSystem process, double minimumRate, double maximumRate) {
    StreamInterface feed = (StreamInterface) process.getUnit("well stream");
    ProductionOptimizer.OptimizationConfig config =
        new ProductionOptimizer.OptimizationConfig(minimumRate, maximumRate)
            .rateUnit("kmol/hr").tolerance(0.01).maxIterations(50);
    ProductionOptimizer.OptimizationResult result =
        new ProductionOptimizer().optimize(process, feed, config, null, null);
    if (!result.isFeasible()) {
        throw new IllegalStateException("No feasible throughput inside the configured bounds");
    }
    feed.setFlowRate(result.getOptimalRate(), "kmol/hr");
    process.run();
    return result.getOptimalRate();
}
```

## Extending to Other Equipment

### Step-by-Step Guide

Reuse inherited constraint storage and public equipment APIs. This avoids duplicate maps,
missing simulation implementations, and accidental loss of disabled-constraint handling.

#### Step 1: Implement the Interface

A `ProcessEquipmentBaseClass` subclass already provides capacity methods. Extend an
appropriate working equipment class; explicit `CapacityConstrainedEquipment` implementation
is only needed when a consumer requires that marker interface.

#### Step 2: Initialize Default Constraints in Constructor

```java
public class MyEquipment extends Stream implements CapacityConstrainedEquipment {
  private static final long serialVersionUID = 1L;

  public MyEquipment(String name, StreamInterface inlet) {
    super(name, inlet);
    addCapacityConstraint(new CapacityConstraint("flowRate", "m3/hr", ConstraintType.HARD)
        .setDesignValue(100.0)
        .setValueSupplier(() -> getFlowRate("m3/hr")));
  }

  public void setDesignFlowRate(double flowRate) {
    getCapacityConstraints().get("flowRate").setDesignValue(flowRate);
  }
}
```

#### Step 3: Implement Required Interface Methods

The example inherits the implementations, including enablement, bottleneck selection,
utilization, and mutation methods. Do not reimplement them with loops that ignore
`isEnabled()` or unavailable values.

#### Step 4: Update Constraints When Design Values Change

```java
MyEquipment equipment = new MyEquipment("Rated line", feed);
equipment.setDesignFlowRate(150.0); // actual m3/hr
equipment.run();
double utilization = equipment.getMaxUtilization();
```

### Example: Implementing for Pump

Use a solved liquid `pump` with an installed motor rating and vendor NPSH requirement:

```java
CapacityConstraint power = StandardConstraintType.PUMP_POWER.createConstraint()
    .setDesignValue(500.0) // illustrative motor shaft rating, kW
    .setValueSupplier(() -> pump.getPower("kW"));
pump.addCapacityConstraint(power);
CapacityConstraint npsh = StandardConstraintType.PUMP_NPSH_MARGIN.createConstraint()
    .setMinValue(1.0) // illustrative required NPSHa - NPSHr headroom, m
    .setValueSupplier(() -> pump.getNPSHAvailable() - pump.getNPSHRequired());
pump.addCapacityConstraint(npsh);
```

### Example: Implementing for Heat Exchanger

Use a solved two-stream `HeatExchanger exchanger`:

```java
CapacityConstraint duty = StandardConstraintType.HEAT_EXCHANGER_DUTY.createConstraint()
    .setDesignValue(1000.0) // kW
    .setValueSupplier(() -> Math.abs(exchanger.getDuty()) / 1000.0);
exchanger.addCapacityConstraint(duty);
CapacityConstraint approach = StandardConstraintType.HEAT_EXCHANGER_APPROACH_TEMP.createConstraint()
    .setMinValue(5.0) // temperature difference: K and degrees C have the same increment
    .setValueSupplier(() -> exchanger.getApproachTemperature());
exchanger.addCapacityConstraint(approach);
```

### Example: Implementing for Pipe/Pipeline

Use a solved `PipeBeggsAndBrills pipe`. The standard erosional constraint uses **percent**,
whereas `PIPE_PRESSURE_DROP` uses a gradient in **bar/km**, not total drop:

```java
CapacityConstraint velocity = StandardConstraintType.PIPE_VELOCITY.createConstraint()
    .setDesignValue(15.0)
    .setValueSupplier(() -> pipe.getMixtureVelocity());
pipe.addCapacityConstraint(velocity);
CapacityConstraint erosional = StandardConstraintType.PIPE_EROSIONAL_VELOCITY.createConstraint()
    .setDesignValue(100.0)
    .setValueSupplier(() -> 100.0 * pipe.getMixtureVelocity() / pipe.getErosionalVelocity());
pipe.addCapacityConstraint(erosional);
CapacityConstraint gradient = StandardConstraintType.PIPE_PRESSURE_DROP.createConstraint()
    .setDesignValue(2.0)
    .setValueSupplier(() -> pipe.getPressureDrop() / (pipe.getLength() / 1000.0));
pipe.addCapacityConstraint(gradient);
```

## StandardConstraintType Reference

| Category | Type | Name | Unit | Description |
|----------|------|------|------|-------------|
| **Separator** | `SEPARATOR_GAS_LOAD_FACTOR` | gasLoadFactor | m/s | Souders-Brown K-factor |
| | `SEPARATOR_RESIDENCE_TIME` | residenceTime | min | Liquid hold-up time |
| | (custom) | liquidLevel | % | Level as % of capacity |
| **Compressor** | `COMPRESSOR_SPEED` | speed | RPM | Maximum rotational speed |
| | `COMPRESSOR_MIN_SPEED` | minSpeed | RPM | Minimum stable speed (from curve) |
| | `COMPRESSOR_POWER` | power | kW | Standard shaft-power constraint; native compressor power may use % |
| | (custom) | ratedPower | % | Power utilization vs driver rated power |
| | `COMPRESSOR_SURGE_MARGIN` | surgeMargin | % | Distance to surge |
| | `COMPRESSOR_STONEWALL_MARGIN` | stonewallMargin | % | Distance to stonewall |
| | `COMPRESSOR_DISCHARGE_TEMP` | dischargeTemp | °C | Discharge temperature |
| | (custom) | pressureRatio | - | Compression ratio |
| **Pump** | `PUMP_FLOW_RATE` | flowRate | m³/hr | Volumetric flow |
| | (custom) | head | m | Developed head |
| | `PUMP_POWER` | power | kW | Shaft power |
| | `PUMP_NPSH_MARGIN` | npshMargin | m | NPSH available margin |
| **Heat Exchanger** | `HEAT_EXCHANGER_DUTY` | duty | kW | Heat transfer rate |
| | `HEAT_EXCHANGER_APPROACH_TEMP` | approachTemp | °C | Minimum ΔT |
| | `HEAT_EXCHANGER_PRESSURE_DROP` | pressureDrop | bar | Pressure loss |
| **Valve** | `VALVE_CV_UTILIZATION` | cvUtilization | % | Cv used / Cv available |
| | `VALVE_PRESSURE_DROP` | pressureDrop | bar | Pressure loss |
| | (custom) | AIV | kW | Acoustic-induced vibration power |
| **Pipe** | `PIPE_VELOCITY` | velocity | m/s | Fluid velocity |
| | `PIPE_EROSIONAL_VELOCITY` | erosionalVelocityRatio | % | 100 × v/v_erosional |
| | `PIPE_PRESSURE_DROP` | pressureDropPerLength | bar/km | Pressure gradient |
| | (custom) | AIV | kW | Acoustic-induced vibration power |

**Notes:**
- **COMPRESSOR_MIN_SPEED**: This is a "minimum constraint" - utilization is calculated as `minSpeed / currentSpeed`. Values < 1.0 mean operating safely above minimum; values > 1.0 mean operating below minimum (violation).
- **COMPRESSOR_POWER**: Utilization vs speed-dependent max power from driver curve. Shows actual operating margin at current speed. 100% means the driver is at its maximum power output at the current speed.
- **ratedPower**: Utilization vs driver's rated power (for capacity planning). Shows what fraction of the motor's full rating is being used, regardless of current speed.
- **COMPRESSOR_SURGE_MARGIN** and **COMPRESSOR_STONEWALL_MARGIN**: Minimum margin utilization is `requiredMargin / currentMargin`. Safe margins exceed the required minimum. Read the actual constraint direction and unit; legacy strategy constraints can use different definitions.

## Flow-Induced Vibration (FIV) Analysis

Pipeline equipment (`Pipeline`, `PipeBeggsAndBrills`, `AdiabaticPipe`, `Manifold`) includes built-in FIV analysis capabilities with constraints based on industry standards.

### FIV Metrics

| Metric | Description | Risk Threshold |
|--------|-------------|----------------|
| **LOF** (Likelihood of Failure) | Dimensionless indicator based on density, velocity, GVF, and support stiffness | > 0.6 = High risk |
| **FRMS** | RMS force per meter (N/m) - dynamic loading indicator | > 500 N/m = High risk |
| **Erosional Velocity** | Maximum velocity per API RP 14E: Ve = C/√ρ | > 100% = Erosion risk |

### Support Arrangement Coefficients

The LOF calculation uses support arrangement coefficients per industry practice:

| Support Type | Coefficient | Description |
|--------------|-------------|-------------|
| Stiff | 1.0 | Rigid supports, short spans |
| Medium stiff | 1.5 | Standard pipe racks |
| Medium | 2.0 | Longer spans, typical offshore |
| Flexible | 3.0 | Flexible supports, risers |

### Using FIV Analysis

```java
// Pipeline with FIV constraints
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Export Line", feed);
pipe.setLength(5000.0);
pipe.setDiameter(0.2032);  // 8 inch
pipe.setThickness(0.008);   // 8mm wall
pipe.setSupportArrangement("Medium stiff");
pipe.run();

// Get FIV metrics
double lof = pipe.calculateLOF();
double frms = pipe.calculateFRMS();
double erosionalVel = pipe.getErosionalVelocity();
double actualVel = pipe.getMixtureVelocity();

logger.info(String.format("LOF: %.3f (Risk: %s)%n", lof, lof > 0.6 ? "HIGH" : "Low"));
logger.info(String.format("FRMS: %.1f N/m%n", frms));
logger.info(String.format("Velocity: %.2f / %.2f m/s (%.1f%% of erosional)%n",
    actualVel, erosionalVel, 100 * actualVel / erosionalVel));

// Get full FIV analysis as Map
Map<String, Object> fivAnalysis = pipe.getFIVAnalysis();

// Get FIV analysis as JSON
String fivJson = pipe.getFIVAnalysisJson();
```

### FIV Analysis Output (JSON)

```json
{
  "LOF": 0.234,
  "LOF_risk": "Low",
  "FRMS_N_per_m": 125.6,
  "FRMS_risk": "Low",
  "mixtureDensity_kg_m3": 85.2,
  "mixtureVelocity_m_s": 12.4,
  "erosionalVelocity_m_s": 18.5,
  "velocityRatio": 0.67,
  "gasVolumeFraction": 0.92,
  "supportArrangement": "Medium stiff",
  "supportCoefficient": 1.5,
  "innerDiameter_m": 0.1872
}
```

### Manifold FIV Analysis

Manifolds provide separate FIV analysis for header and branch lines:

```java
Manifold manifold = new Manifold("Production Manifold");
manifold.addStream(inlet1);
manifold.addStream(inlet2);
manifold.setSplitFactors(new double[] {0.4, 0.3, 0.3});
manifold.setMaxHeaderVelocityDesign(15.0);
manifold.setMaxBranchVelocityDesign(15.0);
manifold.setHeaderInnerDiameter(0.3);
manifold.setBranchInnerDiameter(0.15);
manifold.run();

// Header FIV
double headerLOF = manifold.calculateHeaderLOF();
double headerFRMS = manifold.calculateHeaderFRMS();

// Branch FIV (uses average branch flow)
double branchLOF = manifold.calculateBranchLOF();

// All constraints
Map<String, CapacityConstraint> constraints = manifold.getCapacityConstraints();
// Contains: headerVelocity, branchVelocity, headerLOF, headerFRMS, branchLOF
```

### FIV Design Limits

Set design limits for FIV constraints:

```java
// Set maximum allowable values
pipe.setMaxDesignVelocity(15.0);  // m/s
pipe.setMaxDesignLOF(0.5);        // dimensionless
pipe.setMaxDesignFRMS(400.0);     // N/m

// These become constraint design values
CapacityConstraint lofConstraint = pipe.getCapacityConstraints().get("LOF");
// lofConstraint.getDesignValue() returns 0.5
```

**Note:** The setter methods (`setMaxDesignVelocity`, `setMaxDesignLOF`, `setMaxDesignFRMS`, `setMaxDesignAIV`) clear the constraint map, so new values take effect when `getCapacityConstraints()` is called. Configure these limits before adding custom constraints, or reapply the custom constraints afterward. If you need to explicitly reinitialize constraints after other changes, call `pipe.reinitializeCapacityConstraints()`.

## Acoustic-Induced Vibration (AIV) Analysis

AIV is caused by high acoustic energy generated by pressure-reducing devices (valves, orifices) and is particularly relevant for high-pressure gas systems. Unlike FIV (which relates to liquid slugging), AIV is critical for dry gas systems.

### AIV Formula (Energy Institute Guidelines)

The acoustic power is calculated using the Energy Institute Guidelines formula:

$$W_{acoustic} = 3.2 \times 10^{-9} \cdot \dot{m} \cdot P_1 \cdot \left(\frac{\Delta P}{P_1}\right)^{3.6} \cdot \left(\frac{T}{273.15}\right)^{0.8}$$

Where:
- $W_{acoustic}$ = Acoustic power (kW)
- $\dot{m}$ = Mass flow rate (kg/s)
- $P_1$ = Upstream pressure (Pa)
- $\Delta P$ = Pressure drop (Pa)
- $T$ = Temperature (K)

### AIV Risk Levels

| Acoustic Power (kW) | Risk Level | Action Required |
|---------------------|------------|-----------------|
| < 1 | LOW | No action required |
| 1 - 10 | MEDIUM | Review piping layout |
| 10 - 25 | HIGH | Detailed analysis required |
| > 25 | VERY HIGH | Mitigation required |

### Using AIV Analysis in Pipes

```java
// Pipeline with AIV constraint
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("HP Gas Line", feed);
pipe.setLength(100.0);
pipe.setDiameter(0.2032);  // 8 inch
pipe.setThickness(0.008);   // 8mm wall
pipe.run();

// Get AIV metrics
double aivPower = pipe.calculateAIV();  // kW
double aivLOF = pipe.calculateAIVLikelihoodOfFailure();

logger.info(String.format("AIV Power: %.2f kW%n", aivPower));
logger.info(String.format("AIV LOF: %.2f%n", aivLOF));

// Set AIV design limit (default is 25 kW)
pipe.setMaxDesignAIV(10.0);  // kW

// Get FIV analysis (now includes AIV)
Map<String, Object> analysis = pipe.getFIVAnalysis();
// Contains: AIV_power_kW, AIV_risk, AIV_LOF
```

### Using AIV Analysis in Valves

Throttling valves are primary sources of AIV due to large pressure drops:

```java
// Control valve with significant pressure drop
ThrottlingValve valve = new ThrottlingValve("PCV-100", feed);
valve.setOutletPressure(30.0, "bara");  // Large ΔP
valve.run();

// Get AIV metrics
double aivPower = valve.calculateAIV();  // kW

// Calculate AIV LOF (requires downstream pipe geometry)
double downstreamDiameter = 0.2032;  // 8 inch
double downstreamThickness = 0.008;  // 8mm
double aivLOF = valve.calculateAIVLikelihoodOfFailure(
    downstreamDiameter, downstreamThickness);

logger.info(String.format("Valve AIV Power: %.2f kW%n", aivPower));
logger.info(String.format("Valve AIV LOF: %.3f%n", aivLOF));

// Set AIV design limit (default is 10 kW for valves)
valve.setMaxDesignAIV(5.0);  // kW - stricter limit

// Access AIV constraint
CapacityConstraint aivConstraint = valve.getCapacityConstraints().get("AIV");
double utilization = aivConstraint.getUtilization();
```

### AIV Analysis Output (JSON)

The `getFIVAnalysis()` method now includes AIV data:

```json
{
  "LOF": 0.05,
  "LOF_risk": "Low",
  "FRMS_N_per_m": 12.3,
  "FRMS_risk": "Low",
  "AIV_power_kW": 8.45,
  "AIV_risk": "MEDIUM",
  "AIV_LOF": 0.35,
  "mixtureDensity_kg_m3": 45.2,
  "mixtureVelocity_m_s": 18.4,
  "erosionalVelocity_m_s": 25.5,
  "velocityRatio": 0.72,
  "gasVolumeFraction": 0.98,
  "supportArrangement": "Medium stiff",
  "supportCoefficient": 1.5,
  "innerDiameter_m": 0.1872
}
```

### FIV vs AIV: When to Use Each

| Metric | Applicable Systems | Primary Concern |
|--------|-------------------|-----------------|
| **LOF/FRMS** (FIV) | Two-phase flow with liquid slugging | Liquid impacts causing pipe vibration |
| **AIV** | High-pressure gas with pressure drops | Acoustic energy from turbulent flow |

For dry gas systems, AIV is typically more relevant than FIV (LOF/FRMS will be near zero)

---

## Best Practices

### 1. Choose Appropriate Constraint Types

- Use `HARD` for absolute limits that cause trips or damage
- Use `SOFT` for operational limits affecting efficiency
- Use `DESIGN` for normal operating envelope limits

### 2. Set Meaningful Design Values

Design values should represent the intended operating point, not the maximum:
```java
// Good: Design at normal operation, max at limit
constraint.setDesignValue(10000.0);  // Normal speed
constraint.setMaxValue(11000.0);     // Trip speed

// Bad: Design equals maximum
constraint.setDesignValue(11000.0);  // No warning margin
```

### 3. Use Warning Thresholds

The default warning threshold is 90%. Adjust if needed:
```java
constraint.setWarningThreshold(0.85);  // Warn at 85% utilization
```

### 4. Handle Missing Data Gracefully

The valueSupplier should return `Double.NaN` for unavailable data:
```java
CapacityConstraint speed = new CapacityConstraint("speed", "rpm", ConstraintType.HARD)
    .setDesignValue(10000.0)
    .setValueSupplier(() -> compressor.getCompressorChart().isUseCompressorChart()
        ? compressor.getSpeed() : Double.NaN);
```

### 5. Update Constraints When Design Changes

Ensure constraints stay synchronized with design parameters:
```java
CapacityConstraint speed = compressor.getCapacityConstraints().get("speed");
if (speed != null) {
    speed.setDesignValue(10000.0);
}
```

## Equipment Capacity Strategy Registry

The Strategy Registry provides a plugin-based architecture for evaluating equipment capacity constraints without modifying equipment classes. The registry ships with **18 built-in strategies** covering all major equipment categories:

- Adding custom constraint evaluation logic
- Performing system-wide optimization
- Extending constraints for new/custom equipment types

### Built-in Strategies (18 total)

| Strategy | Equipment Types | Typical Constraints |
|----------|----------------|---------------------|
| `CompressorCapacityStrategy` | Compressor | speed, power, surgeMargin, stonewallMargin |
| `SeparatorCapacityStrategy` | Separator, ThreePhaseSeparator | gasLoadFactor, liquidResidenceTime, dropletCutSize |
| `PipeCapacityStrategy` | Pipeline, AdiabaticPipe | velocity, pressureDrop, FIV_LOF, FIV_FRMS |
| `ValveCapacityStrategy` | ThrottlingValve | valveOpening, cvUtilization |
| `HeatExchangerCapacityStrategy` | Heater, Cooler | duty, outletTemperature |
| `PumpCapacityStrategy` | Pump | npshMargin, power, flowRate |
| `ExpanderCapacityStrategy` | Expander | speed, power |
| `EjectorCapacityStrategy` | Ejector | compressionRatio, motiveFlow |
| `MixerCapacityStrategy` | Mixer | flowRate, pressureDiff |
| `SplitterCapacityStrategy` | Splitter | flowRate |
| `TankCapacityStrategy` | Tank | fillLevel, fillRate |
| `DistillationColumnCapacityStrategy` | DistillationColumn | floodingFactor, reboilerDuty |
| `ReactorCapacityStrategy` | GibbsReactor, PlugFlowReactor, StirredTankReactor | throughput, residenceTime, conversionRate |
| `PowerGenerationCapacityStrategy` | GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem | power, fuelFlow, exhaustTemp |
| `SubseaEquipmentCapacityStrategy` | SubseaWell, SubseaTree | flowRate, wellheadPressure, chokeOpening |
| `FilterAdsorberCapacityStrategy` | Filter, SulfurFilter, CharCoalFilter, SimpleAdsorber | pressureDrop, throughput |
| `ElectrolyzerCapacityStrategy` | Electrolyzer, CO2Electrolyzer | power, currentDensity, efficiency |
| `WellFlowCapacityStrategy` | WellFlow | flowRate, wellheadPressure, drawdown |

> **Expander note (equipment-level override).** The `ExpanderCapacityStrategy`
> above belongs to the older `CapacityAnalysisEngine` path. On the
> `getMaxUtilization()` / `getUtilizationSnapshotJson()` path, `Expander`
> overrides the inherited `Compressor` logic: it removes the inherited
> consumed-power constraints (`power`, `ratedPower`) — which are meaningless for a
> machine that *produces* shaft power — and, when a rating is set via
> `expander.setRatedRecoveredPower(kW)`, adds a `recoveredPower` HARD constraint
> sourced from `|getPower|`. It also overrides `isSimulationValid()` so an
> expander's normal state (negative shaft power, outlet colder than inlet,
> pressure ratio < 1) is treated as valid. This removes the previously spurious
> ~150 % expander utilization. See **Expanders (Turbo-Expanders)** below.

### Strategy Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│            EquipmentCapacityStrategyRegistry (Singleton)            │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  findStrategy(equipment)  │  getAllStrategies()                 ││
│  │  register(strategy)       │  getConstraints(equipment)         ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              │                                       │
│         ┌────────────────────┴────────────────────┐                  │
│         ▼                                         ▼                  │
│  ┌──────────────────────┐             ┌─────────────────────────────┐│
│  │ Built-in (18)        │             │  Custom Strategies          ││
│  │ Compressor, Separator│             │  MyEquipmentStrategy        ││
│  │ Pump, Valve, Pipe    │             │  VendorSpecificStrategy     ││
│  │ Reactor, PowerGen    │             │  ...                        ││
│  │ Subsea, Filter       │             │                             ││
│  │ Electrolyzer, Well   │             │                             ││
│  └──────────────────────┘             └─────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

> **Universal Constraint Support:** Since all equipment inherits constraint
> methods from `ProcessEquipmentBaseClass`, you can add constraints to ANY
> equipment — even types without a dedicated strategy. The strategy registry
> provides pre-configured constraints; manual `addCapacityConstraint()` works
> on all equipment.

### Using the Strategy Registry

```java
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;

// Get the singleton registry
EquipmentCapacityStrategyRegistry registry =
    EquipmentCapacityStrategyRegistry.getInstance();

// Find strategy for a specific equipment
Compressor compressor = (Compressor) process.getUnit("ExportCompressor");
EquipmentCapacityStrategy strategy = registry.findStrategy(compressor);

if (strategy != null) {
    // Evaluate capacity
    double utilization = strategy.evaluateCapacity(compressor);
    logger.info(String.format("Compressor utilization: %.1f%%\n", utilization * 100));

    // Get all constraints
    Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);
    for (CapacityConstraint c : constraints.values()) {
        logger.info(String.format("  %s: %.2f %s (%.1f%% of design)\n",
            c.getName(), c.getCurrentValue(), c.getUnit(),
            c.getUtilizationPercent()));
    }

    // Check for violations
    List<CapacityConstraint> violations = strategy.getViolations(compressor);
    if (!violations.isEmpty()) {
        logger.info("Constraint violations:");
        for (CapacityConstraint v : violations) {
            logger.info(String.format("  - %s: %.2f exceeds %.2f\n",
                v.getName(), v.getCurrentValue(), v.getDesignValue()));
        }
    }

    // Get bottleneck constraint
    CapacityConstraint bottleneck = strategy.getBottleneckConstraint(compressor);
    if (bottleneck != null) {
        logger.info("Bottleneck: {}", bottleneck.getName());
    }
}
```

### Creating Custom Strategies

Extend an existing strategy when its equipment support and evaluation methods are appropriate.
This implementation adds a rated duty limit while retaining the complete strategy contract:

```java
public class MyCustomStrategy extends HeatExchangerCapacityStrategy {
  @Override
  public boolean supports(ProcessEquipmentInterface equipment) {
    return equipment instanceof Heater && equipment.getName().equals("Process Heater");
  }

  @Override
  public int getPriority() {
    return 100;
  }

  @Override
  public String getName() {
    return "InstalledHeaterDutyStrategy";
  }

  @Override
  public double evaluateCapacity(ProcessEquipmentInterface equipment) {
    return getConstraints(equipment).get("installedDuty").getUtilization();
  }

  @Override
  public double evaluateMaxCapacity(ProcessEquipmentInterface equipment) {
    return 5000.0; // kW, matching installedDuty
  }

  @Override
  public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
    Heater heater = (Heater) equipment;
    Map<String, CapacityConstraint> constraints = new LinkedHashMap<>();
    constraints.put("installedDuty", new CapacityConstraint("installedDuty", "kW", ConstraintType.HARD)
        .setDesignValue(5000.0)
        .setValueSupplier(() -> Math.abs(heater.getDuty()) / 1000.0));
    return constraints;
  }
}
```

Register the class from a method after its definition:

```java
EquipmentCapacityStrategyRegistry.getInstance().register(new MyCustomStrategy());
```

### Built-in Strategies

| Strategy | Equipment Type | Constraints Evaluated |
|----------|---------------|----------------------|
| `CompressorCapacityStrategy` | Compressor | speed, power, surgeMargin, stonewallMargin, dischargeTemperature |
| `SeparatorCapacityStrategy` | Separator | liquidLevel, gasLoadFactor |
| `PumpCapacityStrategy` | Pump | power, npshMargin, flowRate |
| `ValveCapacityStrategy` | Valve | valveOpening, pressureDropRatio |
| `PipeCapacityStrategy` | Pipeline | velocity, pressureDrop |
| `HeatExchangerCapacityStrategy` | HeatExchanger | duty, outletTemperature |

For detailed usage and integration with the ProcessOptimizationEngine, see [Optimizer Plugin Architecture](optimization/OPTIMIZER_PLUGIN_ARCHITECTURE).

## Integration with OilGasProcessSimulationOptimization

The example simulation class demonstrates integration:

```java
// After running the process
neqsim.process.examples.OilGasProcessSimulationOptimization.ProcessOutputResults results =
    simulation.getOutput();

// Check separator capacity
if (results.isAnySeparatorOverloaded()) {
    logger.info("Separator capacity exceeded!");
    for (Map.Entry<String, Double> e : results.getSeparatorCapacityUtilization().entrySet()) {
        if (e.getValue() > 100.0) {
            logger.info(String.format("  %s at %.1f%%\n", e.getKey(), e.getValue()));
        }
    }
}

// Check compressor speed limits
if (results.isAnyCompressorOverspeed()) {
    logger.info("Compressor speed limit exceeded!");
}

// Use ProcessSystem methods for deeper analysis
ProcessSystem process = simulation.getOilProcess();
BottleneckResult bottleneck = process.findBottleneck();
```

## Capacity Utilization Snapshot (ML / RL Observation Vector)

Both `ProcessSystem` and `ProcessModel` expose `getUtilizationSnapshotJson()`, and the
`ProcessAutomation` facade exposes `getUtilizationSnapshot()` which delegates to whichever it wraps.
The snapshot is a **side-effect-free** JSON view of every unit's capacity utilization — it never
calls `run()`, it only reads the utilization already computed by each unit's constraints. This makes
it the canonical **observation vector** for closed-loop and reinforcement-learning optimization,
paired with `ProcessAutomation.evaluate(...)` (the action + reward step).

```java
ProcessAutomation auto = plant.getAutomation();
plant.run(); // or auto.evaluate(...) — the snapshot reflects the most recent solve
String json = auto.getUtilizationSnapshot();
```

The returned JSON (schema `"1.0"`) has the shape:

```json
{
  "schemaVersion": "1.0",
  "units": [
    {
      "area": "Export compression",
      "name": "30-KA-01",
      "type": "Compressor",
      "capacityAnalysisEnabled": true,
      "maxUtilization": 0.83,
      "maxUtilizationPercent": 83.0,
      "limitingConstraint": "power",
      "feasible": true,
      "hardLimitExceeded": false,
      "power_kW": 1240.5,
      "constraints": [
        {"name": "power", "utilization": 0.83, "utilizationPercent": 83.0,
         "current": 83.0, "design": 100.0, "unit": "%", "enabled": true, "violated": false,
         "dataSource": "design"}
      ]
    }
  ],
  "bottleneck": {"area": "Export compression", "name": "30-KA-01",
                 "qualifiedName": "Export compression::30-KA-01", "utilization": 0.83,
                 "utilizationPercent": 83.0, "limitingConstraint": "power"},
  "anyOverloaded": false,
  "anyHardLimitExceeded": false
}
```

<table>
<caption>Per-unit snapshot fields</caption>
<tr><th>Field</th><th>Meaning</th></tr>
<tr><td><code>area</code></td><td>Process-area name (only present in a <code>ProcessModel</code> snapshot)</td></tr>
<tr><td><code>name</code> / <code>type</code></td><td>Unit name and simple class name</td></tr>
<tr><td><code>maxUtilization</code></td><td>Highest enabled-constraint utilization (0–1; <code>NaN</code> reported as 0)</td></tr>
<tr><td><code>limitingConstraint</code></td><td>Name of the constraint driving <code>maxUtilization</code> (or <code>null</code>)</td></tr>
<tr><td><code>feasible</code></td><td><code>true</code> when no enabled constraint is exceeded</td></tr>
<tr><td><code>power_kW</code></td><td>Shaft power, present for compressors and pumps only</td></tr>
<tr><td><code>constraints[]</code></td><td>Per-constraint breakdown (utilization, current, design, unit, enabled, violated, and <code>dataSource</code> when set)</td></tr>
<tr><td><code>dataSource</code></td><td>Provenance tag on each constraint (e.g. <code>"equipment"</code>, <code>"design"</code>) — lets an agent distinguish a rated/measured limit from an estimate. Emitted only when set.</td></tr>
</table>

The plant-wide `bottleneck`, `anyOverloaded`, and `anyHardLimitExceeded` fields summarise the whole
flowsheet. In a `ProcessModel` snapshot, a non-null `bottleneck` includes both its owning `area` and
an unambiguous `qualifiedName` using the existing `area::unit` convention. Consumers should join or
archive on `qualifiedName`, because separate process areas may legitimately reuse the same unit
name. Because the snapshot reads constraints rather than re-solving, it is cheap to call on every
optimization step.

**Closed-loop RL pattern:**

- **observation** = `getUtilizationSnapshot()`
- **action** = setpoints passed to `ProcessAutomation.evaluate(...)`
- **reward** = an objective read-back from `evaluate(...)` (e.g. negative compression power),
  **penalized when** `anyOverloaded` is `true` or any unit's `maxUtilization > 1`.

### Compressors Without a Performance Chart

A compressor's surge, stonewall, and speed constraints are only physically meaningful when a
performance chart is active. Without a chart the compressor runs on a fixed
outlet-pressure/polytropic-efficiency model where distance-to-surge is undefined
(`getDistanceToSurge()` returns positive infinity), which would otherwise pin the surge utilization
at a degenerate flat 100% that never responds to feed rate.

NeqSim therefore creates those chart-dependent constraints **present but disabled** for chartless
compressors. The constraint objects still exist (so existence checks pass), but they are excluded
from `getMaxUtilization()` and `getBottleneckConstraint()`. A chartless compressor consequently
reports smooth, **power-driven** utilization. Give the `power` constraint a basis with:

```java
compressor.getMechanicalDesign().setMaxDesignPower(installedKw); // kW
```

When a performance chart is later attached, call `reinitializeCapacityConstraints()` to re-enable
the chart-dependent metrics.

### Expanders (Turbo-Expanders)

`Expander` extends `Compressor` but operates in reverse — it *produces* shaft power and *cools* the
gas. The inherited compressor capacity logic does not fit this: the consumed-power constraints
(`power`, `ratedPower`) have no meaning for a power-producing machine, and
`Compressor.isSimulationValid()` treats an expander's *normal* operating state (negative shaft
power, outlet colder than inlet, pressure ratio < 1) as invalid. Because
`Compressor.getMaxUtilization()` returns a sentinel `1.5` whenever the simulation is flagged
invalid with zero computed utilization, a healthy expander used to report a **spurious ~150 %
utilization**.

`Expander` now fixes this natively by overriding two methods:

- `isSimulationValid()` — expander-correct: negative shaft power and a cooler outlet are valid;
  only `NaN` or an outlet *hotter* than the inlet flags the run invalid.
- `initializeCapacityConstraints()` — removes the inherited `power` / `ratedPower` constraints and,
  when a rating is set, adds a `recoveredPower` HARD constraint sourced from `|getPower|` with
  `dataSource = "equipment"`.

Set the rated recovered shaft power to get a meaningful utilization; otherwise the expander simply
reports no spurious limit:

```java
Expander expander = new Expander("X-100", feed);
expander.setOutletPressure(20.0);
expander.run();
expander.setRatedRecoveredPower(5000.0);   // kW — rebuilds the recoveredPower constraint
double util = expander.getMaxUtilization(); // |getPower| / 5000 kW, no spurious 150%
```

## See Also

- [Process Equipment Documentation](./index.md)
- [Mechanical Design](mechanical_design)
- [Optimizer Plugin Architecture](optimization/OPTIMIZER_PLUGIN_ARCHITECTURE)
- [Optimization Examples](../examples/index)

