---
title: Compressor-Based Production Optimization Guide
description: This guide covers production optimization for facilities with compressors, including variable speed drives (VFD), multi-speed, and compressor maps.
---

# Compressor-Based Production Optimization Guide

This guide covers production optimization for facilities with compressors, including variable speed drives (VFD), multi-speed, and compressor maps.

## Table of Contents

- [Overview](#overview)
- [Compressor Configuration](#compressor-configuration)
- [Search Algorithm Selection](#search-algorithm-selection)
- [Controlling Restrictions and Constraints](#controlling-restrictions-and-constraints)
- [CompressorOptimizationHelper Class](#compressoroptimizationhelper-class)
- [Single-Variable Optimization](#single-variable-optimization)
- [Multi-Variable Optimization](#multi-variable-optimization)
- [Two-Stage Optimization (Recommended)](#two-stage-optimization-recommended)
- [Compressor Constraints](#compressor-constraints)
- [Driver Curve Configuration](#driver-curve-configuration)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

> **January 2026 Update:** ProductionOptimizer now includes `GRADIENT_DESCENT_SCORE` algorithm for smooth multi-variable problems, configuration validation with `config.validate()`, stagnation detection, warm start support, bounded LRU cache, and infeasibility diagnostics. See [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) for details.

---

## Overview

Production optimization for compression facilities requires careful handling of:

| Challenge | Solution in NeqSim |
|-----------|-------------------|
| Compressor operating envelope | Compressor charts with surge/stonewall limits |
| Variable speed drives | `setMaxPowerSpeedCurve()` for tabular driver curves |
| Multi-train balancing | `ManipulatedVariable` for split factors |
| Feasibility detection | `isSimulationValid()` validation |
| Multiple constraints | `CapacityConstrainedEquipment` framework |

### Key Classes

```text
ProductionOptimizer              // Main optimizer
OptimizationConfig               // Search configuration
ManipulatedVariable              // Decision variables (flow, splits, pressures)
OptimizationObjective            // Throughput, power, efficiency objectives
CompressorDriver                 // Driver power curves
CompressorChartGenerator         // Performance curve generation
```

---

## Compressor Configuration

### 1. Basic Setup with Performance Curves

```java
// Create compressor
Compressor compressor = new Compressor("Export Compressor", inletStream);
compressor.setOutletPressure(110.0, "bara");
compressor.setPolytropicEfficiency(0.78);
compressor.setUsePolytropicCalc(true);
process.add(compressor);
process.run();

// Generate compressor chart at design point
CompressorChartGenerator chartGen = new CompressorChartGenerator(compressor);
chartGen.setChartType("interpolate and extrapolate");
CompressorChartInterface chart = chartGen.generateCompressorChart("normal curves", 5);

// Apply chart and enable speed solving
compressor.setCompressorChart(chart);
compressor.getCompressorChart().setUseCompressorChart(true);
compressor.setSolveSpeed(true);

// Set speed limits (defines optimization headroom)
double designSpeed = compressor.getSpeed();
compressor.setMaximumSpeed(designSpeed * 1.15);  // Illustrative mechanical limit
compressor.reinitializeCapacityConstraints();
process.run();
```

### 2. Load Compressor Chart from JSON

```java
// Load from external JSON file
compressor.loadCompressorChartFromJson("path/to/compressor_curve.json");
compressor.setSolveSpeed(true);
```

### 3. Configure VFD Electric Motor Driver

For variable frequency drive motors with tabular power limits:

```java
CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 44400.0);  // 44.4 MW max
driver.setRatedSpeed(7383.0);  // RPM at rated power

// Set tabular max power vs speed curve
double[] speeds = {4922, 5500, 6000, 6500, 7000, 7383};  // RPM
double[] powers = {21.8, 27.5, 32.0, 37.0, 42.0, 44.4};  // MW
driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

compressor.setDriver(driver);
```

### 4. Configure Gas Turbine Driver

For gas turbines with polynomial power curve:

```java
CompressorDriver driver = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);  // kW
driver.setRatedSpeed(7383.0);

// P_max(N) = maxPower * (a + b*(N/N_rated) + c*(N/N_rated)²)
driver.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);  // 0.748 at 70% speed, 1.0 at 100%

compressor.setDriver(driver);
```

---

## Search Algorithm Selection

| Scenario | Recommended Algorithm | Why |
|----------|----------------------|-----|
| Single flow variable | `BINARY_FEASIBILITY` | Fast, deterministic |
| Flow + 1 split factor | `NELDER_MEAD_SCORE` | Supports two decision variables |
| Flow + 2-3 split factors | `NELDER_MEAD_SCORE` | Multi-dimensional simplex |
| Many variables (4-10) | `PARTICLE_SWARM_SCORE` | Global search |
| Many smooth variables (5-20+) | `GRADIENT_DESCENT_SCORE` | **New** - Fast convergence |
| Two-stage approach | `NELDER_MEAD_SCORE` then `BINARY_FEASIBILITY` | **Recommended** |

### Algorithm Configuration

```java
// For single-variable throughput maximization
OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.BINARY_FEASIBILITY)
    .tolerance(flowRate * 0.005)
    .maxIterations(20)
    .defaultUtilizationLimit(1.0);

// For multi-variable optimization (2-10 variables)
OptimizationConfig simplexConfig = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .tolerance(flowRate * 0.002)
    .maxIterations(60)
    .defaultUtilizationLimit(1.0)
    .rejectInvalidSimulations(true);  // Critical for compressors!

// NEW: For many-variable smooth problems (5-20+ variables)
// Uses finite-difference gradients with Armijo line search
OptimizationConfig gradientConfig = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.GRADIENT_DESCENT_SCORE)
    .tolerance(flowRate * 0.001)
    .maxIterations(100)
    .rejectInvalidSimulations(true);

// For global search with many local optima
OptimizationConfig swarmConfig = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.PARTICLE_SWARM_SCORE)
    .swarmSize(12)
    .inertiaWeight(0.6)
    .cognitiveWeight(1.2)
    .socialWeight(1.2)
    .maxIterations(50);
```

---

## Controlling Restrictions and Constraints

The optimizer provides several mechanisms to enable, disable, or adjust restrictions.

### Configuration Options Reference

| Option | Default | Purpose |
|--------|---------|---------|
| `rejectInvalidSimulations(bool)` | `true` | Reject physically invalid operating points |
| `defaultUtilizationLimit(double)` | `0.95` | Maximum utilization for all equipment |
| `utilizationLimitForName(name, limit)` | - | Override limit for specific equipment |
| `utilizationLimitForType(class, limit)` | - | Override limit for equipment type |

### Turning Off Simulation Validity Checking

```java
// CAUTION: Only disable for debugging or exploration
OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .rejectInvalidSimulations(false);  // Allows invalid compressor states
```

When disabled, the optimizer may accept operating points where:
- Compressor speed is outside chart range
- Head or efficiency calculations fail
- Compressor power is negative for a modeled compression duty

**Recommendation:** Keep enabled (`true`) for production use.

### Adjusting Utilization Limits

#### Relax All Equipment (Allow Temporary Overload)

```java
// Allow up to 110% utilization during search exploration
config.defaultUtilizationLimit(1.10);

// Or remove the finite aggregate utilization ceiling (other checks still apply)
config.defaultUtilizationLimit(Double.MAX_VALUE);
```

#### Per-Equipment Limits

```java
// Tight limit on critical compressor
config.utilizationLimitForName("Export Compressor", 0.90);

// Relaxed limit on separator (has margin)
config.utilizationLimitForName("HP Separator", 1.05);

// By equipment type
config.utilizationLimitForType(Compressor.class, 0.95);
config.utilizationLimitForType(Separator.class, 1.00);
```

### Disabling Capacity Tracking on Specific Equipment

```java
// Exclude equipment from bottleneck analysis
manifold.setCapacityAnalysisEnabled(false);
heater.setCapacityAnalysisEnabled(false);
```

This prevents the equipment from being considered as a capacity bottleneck, useful for:
- Utility equipment (heaters, coolers)
- Manifolds with artificial velocity constraints
- Equipment where utilization is not meaningful

### Constraint Severity: HARD vs SOFT

When creating custom constraints:

```java
// HARD constraint - must be satisfied (infeasible if violated)
OptimizationConstraint.greaterThan("minSurgeMargin", 
    proc -> ((Compressor) proc.getUnit("Export Compressor")).getDistanceToSurge(),
    0.10,                           // 10% minimum
    ConstraintSeverity.HARD,        // Never violate
    100.0, "Surge protection margin");

// SOFT constraint - penalized but allowed (optimization prefers feasible)
OptimizationConstraint.lessThan("totalPower",
    proc -> proc.getPower("kW"),
    40000.0,                        // 40 MW target
    ConstraintSeverity.SOFT,        // Can exceed with penalty
    10.0, "Power budget target");
```

### Python Configuration

```python
from neqsim import jneqsim

OptimizationConfig = jneqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig
SearchMode = jneqsim.process.util.optimizer.ProductionOptimizer.SearchMode

# Relaxed configuration (for exploration)
config = OptimizationConfig(50000.0, 200000.0) \
    .rejectInvalidSimulations(False) \
    .defaultUtilizationLimit(1.5) \
    .searchMode(SearchMode.PARTICLE_SWARM_SCORE)

# Strict configuration (for production)
config = OptimizationConfig(50000.0, 200000.0) \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(0.95) \
    .utilizationLimitForName("Critical Compressor", 0.90) \
    .searchMode(SearchMode.BINARY_FEASIBILITY)
```

### Common Scenarios

| Scenario | Settings |
|----------|----------|
| Production optimization | `rejectInvalidSimulations(true)`, `defaultUtilizationLimit(0.95)` |
| Capacity exploration | `rejectInvalidSimulations(true)`, `defaultUtilizationLimit(1.10)` |
| Debugging/troubleshooting | `rejectInvalidSimulations(false)`, `defaultUtilizationLimit(2.0)` |
| Load balancing (Stage 1) | `rejectInvalidSimulations(true)`, `defaultUtilizationLimit(2.0)` |
| Throughput max (Stage 2) | `rejectInvalidSimulations(true)`, `defaultUtilizationLimit(1.0)` |

---

## CompressorOptimizationHelper Class

The `CompressorOptimizationHelper` class provides convenience methods for compressor-specific optimization.

### Extract Bounds from Compressor Charts

```java
import neqsim.process.util.optimizer.CompressorOptimizationHelper;
import neqsim.process.util.optimizer.CompressorOptimizationHelper.CompressorBounds;

// Extract operating bounds from compressor chart
CompressorBounds bounds = CompressorOptimizationHelper.extractBounds(compressor);

logger.info("{}", "Speed range: " + bounds.getMinSpeed() + " - " + bounds.getMaxSpeed() + " RPM");
logger.info("{}", "Flow range: " + bounds.getMinFlow() + " - " + bounds.getMaxFlow());
logger.info("{}", "Surge flow: " + bounds.getSurgeFlow());
logger.info("{}", "Stone wall: " + bounds.getStoneWallFlow());

// Get recommended operating range with 10% safety margin
double[] recommended = bounds.getRecommendedRange(0.10);
logger.info("{}", "Recommended flow: " + recommended[0] + " - " + recommended[1]);
```

### Create Compressor Variables and Objectives

```java
// Create speed variable with chart-derived bounds
ManipulatedVariable speedVar = CompressorOptimizationHelper.createSpeedVariable(
    compressor, bounds.getMinSpeed(), bounds.getMaxSpeed());

// Create outlet pressure variable
ManipulatedVariable pressVar = CompressorOptimizationHelper.createOutletPressureVariable(
    compressor, 80.0, 120.0);

// Raw objective weights: power 0.4, surge margin 0.3, efficiency 0.3.
// These are coefficients in different units, not percentage priorities; normalize for your study.
List<Compressor> compressors = Arrays.asList(comp1, comp2, comp3);
List<OptimizationObjective> objectives = 
    CompressorOptimizationHelper.createStandardObjectives(compressors);

// Standard constraints (validity + 10% surge margin)
List<OptimizationConstraint> constraints = 
    CompressorOptimizationHelper.createStandardConstraints(compressors);
```

### Python Usage (via JPype)

```python
from neqsim import jneqsim

Helper = jneqsim.process.util.optimizer.CompressorOptimizationHelper

# Extract bounds
bounds = Helper.extractBounds(compressor)
print(f"Speed: {bounds.getMinSpeed():.0f} - {bounds.getMaxSpeed():.0f} RPM")

# Create speed variables for all compressors
import jpype
compressors = jpype.JClass("java.util.ArrayList")()
compressors.add(comp1)
compressors.add(comp2)
speed_vars = Helper.createSpeedVariables(compressors)
```

---

## Single-Variable Optimization

For simple throughput maximization with fixed split factors:

```java
ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(
    currentFlow * 0.9,   // Confirm a feasible lower bound on the chart
    currentFlow * 1.1    // Upper bound
)
    .rateUnit("kg/hr")
    .tolerance(currentFlow * 0.005)
    .maxIterations(25)
    .defaultUtilizationLimit(1.0)
    .searchMode(SearchMode.BINARY_FEASIBILITY)
    .rejectInvalidSimulations(true);

OptimizationObjective throughputObjective = new OptimizationObjective(
    "throughput",
    proc -> ((Stream) proc.getUnit("Inlet Stream")).getFlowRate("kg/hr"),
    1.0,
    ObjectiveType.MAXIMIZE
);

OptimizationResult result = optimizer.optimize(
    processSystem,
    inletStream,
    config,
    Collections.singletonList(throughputObjective),
    Collections.emptyList()
);

logger.info("{}", "Optimal flow: " + result.getOptimalRate() + " kg/hr");
logger.info("{}", "Bottleneck: " + (result.getBottleneck() == null ? "None" : result.getBottleneck().getName()));
logger.info("{}", "Utilization: " + result.getBottleneckUtilization() * 100 + "%");
```

---

## Multi-Variable Optimization

For optimizing both flow rate and compressor train split factors:

```java
// Define manipulated variables
ManipulatedVariable flowVar = new ManipulatedVariable(
    "totalFlow",
    originalFlow * 0.95,
    originalFlow * 1.05,
    "kg/hr",
    (proc, value) -> {
        Stream inlet = (Stream) proc.getUnit("Inlet Stream");
        inlet.setFlowRate(value, "kg/hr");
    }
);

ManipulatedVariable split1Var = new ManipulatedVariable(
    "split1",
    0.28, 0.40,  // Bounds for split factor
    "fraction",
    (proc, value) -> {
        Splitter splitter = (Splitter) proc.getUnit("Compressor Splitter");
        double[] splits = splitter.getSplitFactors();
        double split3 = 1.0 - value - splits[1];
        splitter.setSplitFactors(new double[] {value, splits[1], split3});
    }
);

ManipulatedVariable split2Var = new ManipulatedVariable(
    "split2",
    0.28, 0.40,
    "fraction",
    (proc, value) -> {
        Splitter splitter = (Splitter) proc.getUnit("Compressor Splitter");
        double[] splits = splitter.getSplitFactors();
        double split3 = 1.0 - splits[0] - value;
        splitter.setSplitFactors(new double[] {splits[0], value, split3});
    }
);

List<ManipulatedVariable> variables = Arrays.asList(flowVar, split1Var, split2Var);

OptimizationConfig config = new OptimizationConfig(originalFlow * 0.95, originalFlow * 1.05)
    .rateUnit("kg/hr")
    .tolerance(originalFlow * 0.002)
    .maxIterations(60)
    .defaultUtilizationLimit(1.0)
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .rejectInvalidSimulations(true);

OptimizationResult result = optimizer.optimize(
    processSystem,
    variables,
    config,
    Collections.singletonList(throughputObjective),
    Collections.emptyList()
);
```

---

## Two-Stage Optimization (Recommended)

**Why Two Stages?**

Single-pass multi-variable optimizers can get stuck in local optima or produce inconsistent results due to:
- Coupling between flow and split variables
- Stochastic initialization (PSO)
- Non-convex feasibility regions

**The Two-Stage Approach:**

1. **Stage 1 - Balance Load:** At current flow, optimize split factors to minimize max utilization
2. **Stage 2 - Maximize Flow:** With balanced splits, use binary search to find maximum feasible flow

```java
// ========== STAGE 1: Balance compressor loads ==========
ProductionOptimizer optimizer = new ProductionOptimizer();

// Only split factors as variables
List<ManipulatedVariable> splitVariables = Arrays.asList(split1Var, split2Var);

OptimizationConfig stage1Config = new OptimizationConfig(0.28, 0.40)
    .rateUnit("fraction")
    .tolerance(0.001)
    .maxIterations(50)
    .defaultUtilizationLimit(2.0)  // Relax capacity only; validity checks still apply
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .rejectInvalidSimulations(true);

// Objective: MINIMIZE max utilization (balance the load)
OptimizationObjective balanceObjective = new OptimizationObjective(
    "balanceLoad",
    proc -> -proc.getUnitOperations().stream()
        .filter(unit -> unit instanceof Compressor)
        .mapToDouble(unit -> unit.getMaxUtilization()).max().orElse(0.0),
    1.0,
    ObjectiveType.MAXIMIZE
);

OptimizationResult stage1Result = optimizer.optimize(
    processSystem,
    splitVariables,
    stage1Config,
    Collections.singletonList(balanceObjective),
    Collections.emptyList()
);

// Apply balanced splits
double optSplit1 = stage1Result.getDecisionVariables().get("split1");
double optSplit2 = stage1Result.getDecisionVariables().get("split2");
splitter.setSplitFactors(new double[] {optSplit1, optSplit2, 1.0 - optSplit1 - optSplit2});
processSystem.run();

// ========== STAGE 2: Maximize flow with balanced splits ==========
OptimizationConfig stage2Config = new OptimizationConfig(
    originalFlow * 0.9,
    originalFlow * 1.15
)
    .rateUnit("kg/hr")
    .tolerance(originalFlow * 0.001)
    .maxIterations(20)
    .defaultUtilizationLimit(1.0)  // Strict 100% limit
    .searchMode(SearchMode.BINARY_FEASIBILITY)
    .rejectInvalidSimulations(true);

OptimizationResult stage2Result = optimizer.optimize(
    processSystem,
    inletStream,
    stage2Config,
    Collections.singletonList(throughputObjective),
    Collections.emptyList()
);

logger.info("{}", "Optimal flow: " + stage2Result.getOptimalRate() + " kg/hr");
logger.info("{}", "Balanced splits: [" + optSplit1 + ", " + optSplit2 + ", " +
    (1.0 - optSplit1 - optSplit2) + "]");
```

### Two-Stage Helper Method (Simplified)

The `CompressorOptimizationHelper` provides a two-stage search with N-1 independent splits.
Each setter below updates the last train as the remainder so fractions always sum to one.
Use the three-train process from the multi-variable example. The helper builds its own stage
configurations; it forwards iteration count and stage-2 search mode, flow unit and tolerance,
but does not forward caller utilization overrides or custom constraints. Use the explicit
two-stage example when those settings must be retained:

```java
import neqsim.process.util.optimizer.CompressorOptimizationHelper;
import neqsim.process.util.optimizer.CompressorOptimizationHelper.TwoStageResult;

List<Compressor> compressors = Arrays.asList(comp1, comp2, comp3);

// Define how to set each train's flow fraction
List<BiConsumer<ProcessSystem, Double>> trainSetters = Arrays.asList(
    (proc, split) -> {
        Splitter trains = (Splitter) proc.getUnit("Compressor Splitter");
        double second = trains.getSplitFactors()[1];
        trains.setSplitFactors(new double[] {split, second, 1.0 - split - second});
    },
    (proc, split) -> {
        Splitter trains = (Splitter) proc.getUnit("Compressor Splitter");
        double first = trains.getSplitFactors()[0];
        trains.setSplitFactors(new double[] {first, split, 1.0 - first - split});
    },
    (proc, split) -> {
        Splitter trains = (Splitter) proc.getUnit("Compressor Splitter");
        double first = trains.getSplitFactors()[0];
        trains.setSplitFactors(new double[] {first, 1.0 - first - split, split});
    }
);

OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .rateUnit("kg/hr")
    .maxIterations(50)
    .searchMode(SearchMode.BINARY_FEASIBILITY);

// Run two-stage optimization
TwoStageResult result = CompressorOptimizationHelper.optimizeTwoStage(
    processSystem,
    feedStream,
    compressors,
    trainSetters,
    minFlow, maxFlow,
    config
);

// Inspect both stages before accepting their proposed operating point.
if (!result.getStage1Result().isFeasible() || !result.getStage2Result().isFeasible()) {
    throw new IllegalStateException("No feasible two-stage solution");
}

// Access results
logger.info("{}", "Total flow: " + result.getTotalFlow() + " " + result.getFlowUnit());
logger.info("{}", "Total power: " + result.getTotalPower() + " kW");
logger.info("{}", "Min surge margin: " + result.getMinSurgeMargin() * 100 + "%");

// Per-train data
for (String train : result.getTrainSplits().keySet()) {
    logger.info("{}", String.format("%s: split=%.1f%%, flow=%.0f, power=%.1f kW%n",
        train,
        result.getTrainSplits().get(train) * 100,
        result.getTrainFlows().get(train),
        result.getTrainPowers().get(train)));
}

// Full summary
logger.info("{}", result.toSummary());
```

### Python Usage

```python
from neqsim import jneqsim
import jpype

# Prerequisite: process has a three-outlet Compressor Splitter followed by comp1/2/3,
# active compressor charts and drivers. feed is the stream upstream of the splitter.
Helper = jneqsim.process.util.optimizer.CompressorOptimizationHelper
OptimizationConfig = jneqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig
SearchMode = jneqsim.process.util.optimizer.ProductionOptimizer.SearchMode
ArrayList = jpype.JClass("java.util.ArrayList")

def train_setter(index):
    def apply(proc, value):
        splitter = proc.getUnit("Compressor Splitter")
        splits = list(splitter.getSplitFactors())
        splits[index] = float(value)
        # The first two setters are independent. The third is supplied for list parity.
        if index < 2:
            splits[2] = 1.0 - splits[0] - splits[1]
        else:
            splits[1] = 1.0 - splits[0] - splits[2]
        splitter.setSplitFactors(jpype.JArray(jpype.JDouble)(splits))
    return jpype.JProxy("java.util.function.BiConsumer", dict(accept=apply))

compressors = ArrayList()
setters = ArrayList()
for index, compressor in enumerate([comp1, comp2, comp3]):
    compressors.add(compressor)
    setters.add(train_setter(index))

config = OptimizationConfig(50000.0, 150000.0).rateUnit("kg/hr").maxIterations(50)
result = Helper.optimizeTwoStage(process, feed, compressors, setters, 50000.0, 150000.0, config)
if not result.getStage1Result().isFeasible() or not result.getStage2Result().isFeasible():
    raise RuntimeError("Two-stage search did not find a feasible candidate")
print(result.toSummary())
```

---

## Compressor Constraints

NeqSim exposes these compressor constraints. Speed and surge/stonewall limits are enabled only when an active map supplies the operating envelope:

| Constraint | Type | Description |
|------------|------|-------------|
| `speed` | HARD | Current speed vs maximum speed |
| `minSpeed` | HARD | Current speed vs minimum speed (chart limit) |
| `power` | HARD | Current power vs driver max power at speed |
| `surgeMargin` | HARD | Distance to surge line |
| `stonewallMargin` | SOFT | Distance to stonewall (choke) line |

### Accessing Constraints

```java
Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();

for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
    CapacityConstraint c = entry.getValue();
    logger.info("{}", String.format("%s: %.1f%% (current=%.2f, limit=%.2f)%n",
        entry.getKey(),
        c.getUtilizationPercent(),
        c.getCurrentValue(),
        c.getDisplayDesignValue()
    ));
}
```

### Checking Simulation Validity

```java
if (!compressor.isSimulationValid()) {
    List<String> errors = compressor.getSimulationValidationErrors();
    for (String error : errors) {
        logger.info("{}", "ERROR: " + error);
    }
}
```

---

## Driver Curve Configuration

### Tabular Driver Curve (Recommended for VFD)

```java
// From actual motor data
double[] speeds = {4922, 5500, 6000, 6500, 7000, 7383};  // RPM
double[] powers = {21.8, 27.5, 32.0, 37.0, 42.0, 44.4};  // MW

CompressorDriver driver = new CompressorDriver(DriverType.VFD_MOTOR, 44400.0);
driver.setRatedSpeed(7383.0);
driver.setMaxPowerSpeedCurve(speeds, powers, "MW");

// Get max power at any speed (interpolated)
double maxPowerAt6500RPM = driver.getMaxAvailablePowerAtSpeed(6500.0);
```

### Polynomial Driver Curve (Gas Turbines)

```java
// P_max(N) = driver.getMaxPower() * (a + b*(N/N_rated) + c*(N/N_rated)²)
CompressorDriver driver = new CompressorDriver(DriverType.GAS_TURBINE, 40500.0);
driver.setRatedSpeed(7383.0);
driver.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);
```

---

## Best Practices

### 1. Always Enable Simulation Validation

```java
config.rejectInvalidSimulations(true);
```

This prevents the optimizer from accepting operating points where compressors are outside their valid envelope (zero head, speed outside chart range, etc.).

### 2. Initialize Pipe Mechanical Designs

```java
for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
    if (equipment instanceof PipeBeggsAndBrills) {
        PipeBeggsAndBrills pipe = (PipeBeggsAndBrills) equipment;
        pipe.initMechanicalDesign();
        pipe.getMechanicalDesign().setMaxDesignVelocity(20.0);  // m/s
    }
}
```

### 3. Use Realistic Search Bounds

```java
// Stay within compressor chart range
double chartMinSpeed = compressor.getCompressorChart().getMinSpeedCurve();
double chartMaxSpeed = compressor.getCompressorChart().getMaxSpeedCurve();

// Trial mass-flow bounds; validate them against the actual chart and suction state
double lowerFlow = currentFlow * 0.8;   // Conservative lower
double upperFlow = currentFlow * 1.15;  // Check stonewall after solving; this factor is not a guarantee
```

### 4. Disable Capacity Analysis for Non-Critical Equipment

```java
// Manifold with velocity constraints may dominate unfairly in some tests
manifold.setCapacityAnalysisEnabled(false);
```

### 5. Check Results Before Accepting

```java
OptimizationResult result = optimizer.optimize(
    processSystem, inletStream, config, null, null);

// Verify feasibility
if (!result.isFeasible()) {
    logger.info("{}", "WARNING: No feasible solution found");
}

// Verify utilization is bounded
double util = result.getBottleneckUtilization();
if (Double.isNaN(util) || Double.isInfinite(util) || util > 10.0) {
    logger.info("{}", "WARNING: Utilization value is unrealistic: " + util);
}
```

---

## Troubleshooting

### Problem: Optimizer returns NaN or infinite utilization

**Cause:** Compressor operating outside chart envelope
**Solution:** Enable `rejectInvalidSimulations(true)` and reduce search bounds

### Problem: Multi-variable optimization gives inconsistent results

**Cause:** Non-convex objective landscape or coupling between variables
**Solution:** Use two-stage optimization approach

### Problem: All iterations marked infeasible

**Cause:** Search bounds too wide or equipment undersized
**Solution:** Start with smaller bounds around known feasible point

### Problem: Speed shows 0 or unrealistic value

**Cause:** Compressor chart not enabled or `solveSpeed` not set
**Solution:**
```java
compressor.getCompressorChart().setUseCompressorChart(true);
compressor.setSolveSpeed(true);
```

### Problem: Power utilization exceeds 100% even at design point

**Cause:** Driver power limit not configured
**Solution:** Configure driver with appropriate power curve

---

## Python Example (via neqsim-python)

```python
from neqsim import jneqsim
from jpype import JImplements, JOverride
import jpype

# Import classes
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
OptimizationConfig = ProductionOptimizer.OptimizationConfig
SearchMode = ProductionOptimizer.SearchMode
ObjectiveType = ProductionOptimizer.ObjectiveType
ManipulatedVariable = ProductionOptimizer.ManipulatedVariable
Collections = jpype.JClass("java.util.Collections")
Arrays = jpype.JClass("java.util.Arrays")

# Define objective
@JImplements("java.util.function.ToDoubleFunction")
class ThroughputEvaluator:
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("Inlet Stream").getFlowRate("kg/hr")

throughput_obj = ProductionOptimizer.OptimizationObjective(
    "throughput",
    ThroughputEvaluator(),
    1.0,
    ObjectiveType.MAXIMIZE
)

# Configure optimization
config = OptimizationConfig(low_flow, high_flow) \
    .rateUnit("kg/hr") \
    .tolerance(current_flow * 0.005) \
    .maxIterations(25) \
    .defaultUtilizationLimit(1.0) \
    .searchMode(SearchMode.BINARY_FEASIBILITY) \
    .rejectInvalidSimulations(True)

# Run optimization
optimizer = ProductionOptimizer()
result = optimizer.optimize(
    process_system,
    inlet_stream,
    config,
    Collections.singletonList(throughput_obj),
    Collections.emptyList()
)

print(f"Optimal flow: {result.getOptimalRate():.0f} kg/hr")
print(f"Feasible: {result.isFeasible()}")
```

---

## Related Documentation

- [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) - General optimization guide
- [ProductionOptimizer Tutorial](../../examples/ProductionOptimizer_Tutorial) - Interactive Jupyter tutorial
- [Bottleneck Analysis](../../wiki/bottleneck_analysis) - Constraint framework
- [Optimization Overview](OPTIMIZATION_OVERVIEW) - When to use which optimizer
- [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) - SciPy/NLopt integration
