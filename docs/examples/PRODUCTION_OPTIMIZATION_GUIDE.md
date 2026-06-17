---
title: Production Optimization Guide
description: This guide provides comprehensive examples for setting up and running production optimization simulations in NeqSim, covering both Java and Python implementations.
---

# Production Optimization Guide

> **New to process optimization?** Start with the [Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

This guide provides comprehensive examples for setting up and running production optimization simulations in NeqSim, covering both Java and Python implementations.

---

## What's New (January 2026)

### Behavior Changes
- **Constraints Disabled by Default**: Separator, valve, pipeline, pump, and manifold constraints are now disabled by default for backward compatibility. Use `enableConstraints()`, `useEquinorConstraints()`, or `useAPIConstraints()` to enable constraint-based capacity analysis. The optimizer automatically falls back to traditional capacity methods when no constraints are enabled.

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
- **Process-Wide Constraint Control**: `processSystem.disableAllConstraints()` and `processModule.disableAllConstraints()` to control all equipment at once
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
| [External Optimizer Integration](../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | Python/SciPy integration |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](../process/CAPACITY_CONSTRAINT_FRAMEWORK) | Multi-constraint equipment and bottleneck detection |

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

// 5. Report results
System.out.println("Optimal production rate: " + result.getOptimalRate() + " " + result.getRateUnit());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Bottleneck utilization: " + (result.getBottleneckUtilization() * 100) + "%");
System.out.println("Feasible: " + result.isFeasible());
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

    System.out.println("=== ACTIVE CONSTRAINT ===");
    System.out.println("Equipment: " + bottleneck.getName());
    System.out.println("Constraint: " + active.getName());
    System.out.println("Current value: " + active.getCurrentValue() + " " + active.getUnit());
    System.out.println("Design limit: " + active.getDesignValue() + " " + active.getUnit());
    System.out.println("Utilization: " + active.getUtilizationPercent() + "%");
    System.out.println("Type: " + active.getType());      // e.g. POWER, SPEED, SURGE_MARGIN
    System.out.println("Severity: " + active.getSeverity());  // HARD, SOFT, or DESIGN
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
sep.getMechanicalDesign().setMaxDesignVolumeFlow(5000.0);  // m³/hr
sep.getMechanicalDesign().setMaxOperationPressure(100.0); // bara
// These values feed into constraint limits
```

### Important: Constraints Are Disabled by Default

> **⚠️ Backward Compatibility**: Most equipment types have constraints **disabled by default** to maintain backward compatibility. The optimizer will automatically fall back to traditional capacity methods when no enabled constraints exist.

**Equipment with Disabled Constraints by Default:**
- Separator, ThreePhaseSeparator (except GasScrubber which enables K-value)
- ThrottlingValve
- Pipeline, PipeBeggsAndBrills, AdiabaticPipe
- Pump
- Manifold

**Equipment with Enabled Constraints by Default:**
- Compressor (when using `autoSize()` or setting max speed/power)

**To enable constraints for capacity analysis:**

```java
// Separators - use pre-configured sets
separator.useEquinorConstraints();  // Equinor TR3500 standards
separator.useAPIConstraints();      // API 12J standards
separator.useAllConstraints();      // All constraint types

// Or enable all constraints on any equipment
separator.enableConstraints();
valve.enableConstraints();
pipeline.enableConstraints();

// Check if constraints are enabled
boolean hasEnabled = equipment.getCapacityConstraints().values().stream()
    .anyMatch(CapacityConstraint::isEnabled);
```

For detailed information, see [Capacity Constraint Framework - Constraints Disabled by Default](../process/CAPACITY_CONSTRAINT_FRAMEWORK#important-constraints-disabled-by-default).

### Constraint Types and Their Behavior

| Type | Meaning | Optimization Behavior | Example |
|------|---------|----------------------|---------|
| **HARD** | Physical or safety limit - cannot exceed | Optimization stops before exceeding | Compressor trip speed, vessel MAWP |
| **SOFT** | Operational limit - penalty for exceeding | Can exceed with warning/penalty | Efficiency degradation zone |
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
System.out.println("Disabled " + disabled + " constraints on compressor");

// Re-enable all constraints
int enabled = compressor.enableAllConstraints();
```

#### 3. Disable All Constraints in ProcessSystem, ProcessModule, or ProcessModel

```java
// Disable all constraints on ALL equipment in the process
int total = processSystem.disableAllConstraints();
System.out.println("Disabled " + total + " constraints across the process");

// Re-enable all constraints
processSystem.enableAllConstraints();

// For process modules (same API)
processModule.disableAllConstraints();
processModule.enableAllConstraints();

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
compressor = process_system.getUnit("MyCompressor")

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

// Feed from reservoir
Stream wellFeed = new Stream("Well Feed", reservoirFluid);
wellFeed.setFlowRate(20000.0, "kg/hr");

// Three-phase separation
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", wellFeed);
hpSep.autoSize(1.2);  // Constraints: gasLoadFactor, liquidResidenceTime

// Gas compression
Compressor gasComp = new Compressor("Gas Compressor", hpSep.getGasOutStream());
gasComp.setOutletPressure(100.0);
gasComp.autoSize(1.2);  // Constraints: speed, power, surgeMargin + curves

// Export pipeline
PipeBeggsAndBrills exportPipe = new PipeBeggsAndBrills("Export Pipeline", gasComp.getOutletStream());
exportPipe.setLength(50000.0);  // 50 km
exportPipe.setDiameter(0.4);    // 16 inch
exportPipe.autoSize(1.2);       // Constraints: velocity, pressureDrop, FIV_LOF, FIV_FRMS

// Liquid pump
Pump oilPump = new Pump("Oil Pump", hpSep.getOilOutStream());
oilPump.setOutletPressure(15.0);
// Pump has: npshMargin, power, flowRate constraints

process.add(wellFeed);
process.add(hpSep);
process.add(gasComp);
process.add(exportPipe);
process.add(oilPump);
process.run();

// Run optimization
OptimizationConfig config = new OptimizationConfig(5000.0, 50000.0)
    .rateUnit("kg/hr")
    .defaultUtilizationLimit(0.95);

ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationResult result = optimizer.optimize(process, wellFeed, config, null, null);

// Report ALL equipment constraints and identify the active one
System.out.println("=== CONSTRAINT STATUS FOR ALL EQUIPMENT ===\n");

for (CapacityConstrainedEquipment equip : process.getConstrainedEquipment()) {
    ProcessEquipmentInterface unit = (ProcessEquipmentInterface) equip;
    boolean isBottleneck = unit.equals(result.getBottleneck());

    System.out.println(unit.getName() + (isBottleneck ? " ⭐ BOTTLENECK" : "") + ":");

    CapacityConstraint limitingConstraint = equip.getBottleneckConstraint();

    for (CapacityConstraint c : equip.getCapacityConstraints().values()) {
        boolean isActive = c.equals(limitingConstraint) && isBottleneck;
        String marker = isActive ? " ◀ ACTIVE" : "";
        String status = c.isViolated() ? "⚠️" : c.isNearLimit() ? "⚡" : "✓";

        System.out.printf("  %s %-18s: %7.2f / %7.2f %-6s (%5.1f%%)%s%n",
            status,
            c.getName(),
            c.getCurrentValue(),
            c.getDesignValue(),
            c.getUnit(),
            c.getUtilizationPercent(),
            marker);
    }
    System.out.println();
}

System.out.println("=== OPTIMIZATION RESULT ===");
System.out.println("Optimal production rate: " + result.getOptimalRate() + " kg/hr");
System.out.println("Bottleneck equipment: " + result.getBottleneck().getName());
System.out.println("Feasible: " + result.isFeasible());
```

**Example output:**

```
=== CONSTRAINT STATUS FOR ALL EQUIPMENT ===

HP Separator:
  ✓ gasLoadFactor     :    0.07 /    0.08 m/s    (87.5%)
  ✓ liquidResidence   :    2.80 /    3.00 min    (93.3%)

Gas Compressor ⭐ BOTTLENECK:
  ⚡ speed             : 9800.00 / 10000.00 RPM    (98.0%) ◀ ACTIVE
  ✓ power             : 1650.00 /  2000.00 kW     (82.5%)
  ✓ surgeMargin       :   12.00 /   10.00 %      (83.3%)

Export Pipeline:
  ✓ velocity          :   12.50 /   15.00 m/s    (83.3%)
  ✓ pressureDrop      :    3.80 /    5.00 bara   (76.0%)
  ✓ FIV_LOF           :    0.28 /    1.00 -      (28.0%)

Oil Pump:
  ✓ npshMargin        :    2.50 /    0.60 m      (24.0%)
  ✓ power             :   85.00 /  150.00 kW     (56.7%)

=== OPTIMIZATION RESULT ===
Optimal production rate: 28500 kg/hr
Bottleneck equipment: Gas Compressor
Feasible: true
```

In this example, the **Gas Compressor** is the bottleneck with **speed** as the active constraint at 98% utilization.

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
System.out.println("Plant bottleneck: " + bottleneck.getName()
    + " at " + (util * 100) + "%");

// Detailed bottleneck with the limiting constraint
BottleneckResult detail = plant.findBottleneck();
System.out.println("Limiting unit: " + detail.getEquipment().getName()
    + " (" + (detail.getUtilization() * 100) + "%)");

// Whole-plant overload / hard-limit checks
boolean overloaded = plant.isAnyEquipmentOverloaded();
boolean hardLimit = plant.isAnyHardLimitExceeded();

// All constrained equipment, flattened across areas
for (CapacityConstrainedEquipment equip : plant.getConstrainedEquipment()) {
    System.out.println(((ProcessEquipmentInterface) equip).getName()
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
Separator separator = new Separator("HP Separator");
separator.setDesignGasLoadFactor(0.15);  // K-factor limit

// Access constraints (getCapacityConstraints() returns a Map keyed by constraint name)
for (CapacityConstraint constraint : separator.getCapacityConstraints().values()) {
    System.out.println("Constraint: " + constraint.getName());
    System.out.println("  Type: " + constraint.getType());
    System.out.println("  Current: " + constraint.getCurrentValue());
    System.out.println("  Limit: " + constraint.getDesignValue());
    System.out.println("  Utilization: " + (constraint.getUtilization() * 100) + "%");
    System.out.println("  Is Violated: " + constraint.isViolated());
}

// Check overall utilization
double maxUtil = separator.getMaxUtilization();
CapacityConstraint limiting = separator.getBottleneckConstraint();
System.out.println("Limiting constraint: " + limiting.getName() + " at " + (maxUtil * 100) + "%");
```

### Compressor with Multiple Constraints

```java
Compressor compressor = new Compressor("Export Compressor");
compressor.setMaximumSpeed(11000.0);      // HARD constraint - RPM
compressor.initMechanicalDesign();
compressor.getMechanicalDesign().setMaxDesignPower(2000.0);  // HARD constraint - kW
// Surge / stonewall margin constraints are created from the performance curve
// (call compressor.autoSize(...) or compressor.setCompressorChart(...) to populate them)

// After running, check all constraints
process.run();

for (CapacityConstraint c : compressor.getCapacityConstraints().values()) {
    String status = c.isViolated() ? "⚠️ EXCEEDED" : "✓ OK";
    System.out.printf("%s: %.1f / %.1f (%.0f%%) %s%n",
        c.getName(), c.getCurrentValue(), c.getDesignValue(),
        c.getUtilization() * 100, status);
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
        ps -> ((Stream) ps.getUnit("Export Gas")).getPressure("bara"),
        105.0, ConstraintSeverity.HARD, 10.0, "Export pipeline limit"),
    OptimizationConstraint.greaterThan("Min Separator Temp",
        ps -> ((Separator) ps.getUnit("HP Separator")).getGasOutStream().getTemperature("C"),
        -10.0, ConstraintSeverity.SOFT, 5.0, "Hydrate prevention")
);

// Run with objectives and constraints
ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationResult result = optimizer.optimize(
    process, feed, config, objectives, constraints);

// Check constraint statuses
for (ConstraintStatus status : result.getConstraintStatuses()) {
    System.out.printf("%s: margin=%.2f, violated=%s%n",
        status.getName(), status.getMargin(), status.violated());
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

System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
System.out.println("Plant bottleneck: " + result.getBottleneck().getName());
System.out.println("Feasible: " + result.isFeasible());
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

System.out.println("Pareto front size: " + pareto.getParetoFrontSize());
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
    System.out.printf("%s: %.0f kg/hr (bottleneck: %s)%n",
        sr.getName(), sr.getResult().getOptimalRate(),
        sr.getResult().getBottleneck().getName());
}
```

### Scenario Comparison

```java
import java.util.Arrays;
import java.util.List;

// Define scenarios
List<ScenarioRequest> scenarios = Arrays.asList(
    new ScenarioRequest("Summer", process.copy(), feed,
        new OptimizationConfig(1000.0, 20000.0).rateUnit("kg/hr"),
        objectives, constraints),
    new ScenarioRequest("Winter", processWinter.copy(), feedWinter,
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
for (ScenarioResult sr : comparison.getResults()) {
    System.out.printf("Scenario '%s': %.0f kg/hr (bottleneck: %s)%n",
        sr.getName(), sr.getResult().getOptimalRate(),
        sr.getResult().getBottleneck().getName());
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
System.out.println("Bottleneck: " + bottleneck.getName());
System.out.printf("Utilization: %.1f%%%n", process.getBottleneckUtilization() * 100);
```

### Detailed Constraint Analysis

```java
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;

// Get detailed bottleneck result
BottleneckResult bottleneckResult = process.findBottleneck();

System.out.println("Bottleneck Equipment: " + bottleneckResult.getEquipmentName());
System.out.println("Limiting Constraint: " + bottleneckResult.getConstraintName());
System.out.printf("Utilization: %.1f%%%n", bottleneckResult.getUtilization() * 100);

// Inspect the limiting constraint on the bottleneck equipment
CapacityConstraint c = bottleneckResult.getConstraint();
if (c != null) {
    System.out.printf("  - %s: %.1f%% (%s)%n",
        c.getName(), c.getUtilization() * 100, c.getType());
}
```

### Capacity Utilization Summary

```java
import java.util.Map;

// Get all equipment utilizations
Map<String, Double> utilizations = process.getCapacityUtilizationSummary();

System.out.println("=== Capacity Utilization Summary ===");
for (Map.Entry<String, Double> entry : utilizations.entrySet()) {
    String bar = createProgressBar(entry.getValue());
    System.out.printf("%s: %s %.0f%%%n", entry.getKey(), bar, entry.getValue() * 100);
}

// Check for equipment near limits (returns Area::Unit / unit names above the warning threshold)
for (String equipName : process.getEquipmentNearCapacityLimit()) {
    System.out.println("⚠️ Near limit: " + equipName);
}

// Check for any overloaded equipment
if (process.isAnyEquipmentOverloaded()) {
    System.out.println("❌ Equipment overloaded!");
}
if (process.isAnyHardLimitExceeded()) {
    System.out.println("🛑 HARD limit exceeded - system unsafe!");
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
from neqsim.process import stream, separator, compressor, processSystem
```

### Production Optimization in Python

```python
import jpype
import jpype.imports
from jpype.types import *

# Ensure JVM is started (neqsim does this automatically)
import neqsim

# Import Java classes directly
from neqsim.thermo.system import SystemSrkEos
from neqsim.process.equipment.stream import Stream
from neqsim.process.equipment.separator import Separator
from neqsim.process.equipment.compressor import Compressor
from neqsim.process.processmodel import ProcessSystem
from neqsim.process.util.optimizer import ProductionOptimizer

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
print(f"Bottleneck: {result.getBottleneck().getName()}")
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
heater = process.getUnit("Gas Heater")
heater.setCapacityAnalysisEnabled(False)

manifold = process.getUnit("Production Manifold")
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
        return comp.getPower("kW") if comp else 0.0

# Create HARD constraint (must be satisfied)
power_constraint = OptimizationConstraint.lessThan(
    "Max Power",                    # Name
    PowerEvaluator(),               # Evaluator function
    450.0,                          # Limit (kW)
    ConstraintSeverity.HARD,        # Cannot be violated
    0.0,                            # Penalty weight (unused for HARD)
    "Compressor driver power limit" # Description
)

# Create SOFT constraint (penalty for violation)
@JImplements("java.util.function.ToDoubleFunction")
class SurgeMarginEvaluator:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("Gas Compressor")
        return comp.getSurgeMargin() if hasattr(comp, 'getSurgeMargin') else 0.2

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
from neqsim.process.equipment.capacity import BottleneckResult, CapacityConstraint

# Get detailed bottleneck
bottleneck_result = process.findBottleneck()

print(f"Bottleneck: {bottleneck_result.getEquipmentName()}")
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
    bar = "█" * int(util * 20) + "░" * (20 - int(util * 20))
    print(f"{name}: [{bar}] {util*100:.0f}%")

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
kpis.add(ScenarioKpi("Max Rate", "kg/hr", lambda r: r.getOptimalRate()))
kpis.add(ScenarioKpi("Bottleneck Util", "%", lambda r: r.getBottleneckUtilization() * 100))

# Compare (compareScenarios is an instance method)
optimizer = ProductionOptimizer()
comparison = optimizer.compareScenarios(scenarios, kpis)

for sr in comparison.getResults():
    print(f"{sr.getName()}: {sr.getResult().getOptimalRate():.0f} kg/hr")
```

---

## Integration with External Systems

### JSON Export for Dashboards

```java
// Get a lightweight optimization summary as structured data
OptimizationSummary summary = new ProductionOptimizer().quickOptimize(process, feed, config);

// Convert to JSON using Gson
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

Gson gson = new GsonBuilder().setPrettyPrinting().create();
String json = gson.toJson(summary);
System.out.println(json);
```

### Iteration History for Plotting

```java
// Get full result with history
OptimizationResult result = new ProductionOptimizer().optimize(process, feed, config, null, null);

// Export iteration history for plotting
List<IterationRecord> history = result.getIterationHistory();

System.out.println("Rate,Bottleneck,Utilization,Feasible,Score");
for (IterationRecord record : history) {
    System.out.printf("%.1f,%s,%.3f,%s,%.4f%n",
        record.getRate(),
        record.getBottleneckName(),
        record.getBottleneckUtilization(),
        record.isFeasible(),
        record.getScore());
}
```

### Real-Time Optimization Loop

```python
import time

# Continuous optimization loop
while True:
    # Update feed conditions from real-time data
    feed.setTemperature(get_realtime_temp(), "C")
    feed.setPressure(get_realtime_pressure(), "bara")

    # Re-run process
    process.run()

    # Check constraints
    if process.isAnyHardLimitExceeded():
        print("🛑 ALARM: Hard limit exceeded!")
        trigger_alarm()

    # Get current bottleneck
    bottleneck = process.getBottleneck()
    util = process.getBottleneckUtilization()

    # Log to historian
    log_to_historian({
        "bottleneck": bottleneck.getName(),
        "utilization": util,
        "timestamp": time.time()
    })

    # Run periodic optimization
    if should_optimize():
        result = ProductionOptimizer().optimize(process, feed, config, None, None)
        recommend_setpoint(result.getOptimalRate())

    time.sleep(60)  # 1-minute interval
```

---

## Equipment Support Matrix

| Equipment | getCapacityDuty() | getCapacityMax() | CapacityConstrainedEquipment |
|-----------|-------------------|------------------|------------------------------|
| Separator | ✅ Liquid level fraction | ✅ 1.0 (100% fill) | ✅ Gas load factor constraint |
| Compressor | ✅ Power (kW) | ✅ Max power | ✅ Speed, power, surge, stonewall margin |
| Pump | ✅ Power (kW) | ✅ Max power | ✅ Capacity constraints |
| Heater/Cooler | ✅ Duty (kW) | ✅ Max duty | ✅ Duty constraint |
| HeatExchanger | ✅ Duty (kW) | ✅ Max duty | ✅ Duty constraint |
| Valve | ✅ Opening (%) | ✅ Max opening | ✅ (only if max < 100% set) |
| Pipe | ✅ Superficial velocity | ✅ Max velocity | ✅ Velocity constraint |
| DistillationColumn | ✅ Fs factor | ✅ Max Fs factor | ❌ (planned) |
| Manifold | ✅ Velocity | ✅ Erosional velocity | ✅ FIV analysis |

**Notes:**
- **Separator**: Uses liquid level fraction for optimization (0.7 = 70% filled). Gas load factor (K-factor) available via `getGasLoadFactor()` for sizing.
- **Valve**: Only tracked if max opening < 100% is explicitly set. A fully open valve is normal, not overutilized.
- **Compressor**: Min speed constraint correctly handles utilization (below minimum speed = violation).

---

## Best Practices

### 1. Set Realistic Equipment Limits

```java
// Always set mechanical design limits
separator.setDesignGasLoadFactor(0.107);  // Souders-Brown K-factor [m/s]

// Or use the mechanical-design volume-flow limit
separator.initMechanicalDesign();
separator.getMechanicalDesign().setMaxDesignVolumeFlow(2000.0);  // m³/hr

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

### 4. Use Hard Constraints for Safety

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
    .capacityRuleForType(Compressor.class, new CapacityRule(
        unit -> ((CapacityConstrainedEquipment) unit).getMaxUtilization(),
        unit -> 1.0))
    .utilizationLimitForType(Compressor.class, 1.0);  // 100% since getMaxUtilization is a ratio

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
    ps -> ps.getUnit("Cooler").getOutStream().getTemperature("C"),
    35.0, ConstraintSeverity.SOFT, 5.0, "Target export temp");
```

### 5. Validate Before Optimization

```java
// Check that all equipment is properly configured
for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (unit.getCapacityMax() <= 0) {
        System.out.println("Warning: " + unit.getName() + " has no capacity limit set");
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
feed.setFlowRate(config.lowerBound, "kg/hr");
process.run();

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    double util = unit.getCapacityDuty() / unit.getCapacityMax();
    if (util > 1.0) {
        System.out.println("Already exceeded at min rate: " + unit.getName());
    }
}

// Use infeasibility diagnostics (New)
OptimizationResult result = optimizer.optimize(process, feed, config, null, null);
if (!result.isFeasible()) {
    System.out.println(result.getInfeasibilityDiagnosis());
}
```

### Problem: Bottleneck changes unexpectedly

**Solution:** Use iteration history to understand the search:
```java
for (IterationRecord r : result.getIterationHistory()) {
    System.out.printf("Rate=%.0f, Bottleneck=%s, Util=%.1f%%%n",
        r.getRate(), r.getBottleneckName(), r.getBottleneckUtilization() * 100);
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
    System.out.println("Configuration error: " + e.getMessage());
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
| `quickOptimize(ProcessSystem, StreamInterface[, OptimizationConfig])` | Returns lightweight `OptimizationSummary` |
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
| `getCapacityUtilizationSummary()` | Map of equipment name → utilization |
| `getEquipmentNearCapacityLimit()` | Equipment names above the warning threshold |

### CapacityConstrainedEquipment

| Method | Description |
|--------|-------------|
| `getCapacityConstraints()` | Map of constraint name → constraint |
| `getBottleneckConstraint()` | Get highest-utilization constraint |
| `getMaxUtilization()` | Get maximum utilization across constraints |
| `isOverloaded()` | Any constraint > 100% |
| `isHardLimitExceeded()` | Any HARD constraint violated |

