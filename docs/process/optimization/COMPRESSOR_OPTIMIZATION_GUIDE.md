# Compressor-Based Production Optimization Guide

This guide covers production optimization for facilities with compressors, including variable speed drives (VFD), multi-speed, and compressor maps.

## Table of Contents

- [Overview](#overview)
- [Compressor Configuration](#compressor-configuration)
- [Search Algorithm Selection](#search-algorithm-selection)
- [Single-Variable Optimization](#single-variable-optimization)
- [Multi-Variable Optimization](#multi-variable-optimization)
- [Two-Stage Optimization (Recommended)](#two-stage-optimization-recommended)
- [Compressor Constraints](#compressor-constraints)
- [Driver Curve Configuration](#driver-curve-configuration)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

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

```java
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
compressor.setMaximumSpeed(designSpeed * 1.15);  // 15% margin above design
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
driver.setMaxPowerCurveCoefficients(0.3, 0.5, 0.2);  // ~0.86 at 70% speed, 1.0 at 100%

compressor.setDriver(driver);
```

---

## Search Algorithm Selection

| Scenario | Recommended Algorithm | Why |
|----------|----------------------|-----|
| Single flow variable | `BINARY_FEASIBILITY` | Fast, deterministic |
| Flow + 1 split factor | `GOLDEN_SECTION_SCORE` | Handles non-monotonic |
| Flow + 2-3 split factors | `NELDER_MEAD_SCORE` | Multi-dimensional simplex |
| Many variables (>4) | `PARTICLE_SWARM_SCORE` | Global search |
| Two-stage approach | `NELDER_MEAD` then `BINARY_FEASIBILITY` | **Recommended** |

### Algorithm Configuration

```java
// For single-variable throughput maximization
OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.BINARY_FEASIBILITY)
    .tolerance(flowRate * 0.005)
    .maxIterations(20)
    .defaultUtilizationLimit(1.0);

// For multi-variable optimization
OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .tolerance(flowRate * 0.002)
    .maxIterations(60)
    .defaultUtilizationLimit(1.0)
    .rejectInvalidSimulations(true);  // Critical for compressors!

// For global search with many local optima
OptimizationConfig config = new OptimizationConfig(minFlow, maxFlow)
    .searchMode(SearchMode.PARTICLE_SWARM_SCORE)
    .swarmSize(12)
    .inertiaWeight(0.6)
    .cognitiveWeight(1.2)
    .socialWeight(1.2)
    .maxIterations(50);
```

---

## Single-Variable Optimization

For simple throughput maximization with fixed split factors:

```java
ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(
    currentFlow * 0.8,   // Lower bound
    currentFlow * 1.2    // Upper bound
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

System.out.println("Optimal flow: " + result.getOptimalRate() + " kg/hr");
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Utilization: " + result.getBottleneckUtilization() * 100 + "%");
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
    .defaultUtilizationLimit(2.0)  // Allow infeasible during search
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .rejectInvalidSimulations(true);

// Objective: MINIMIZE max utilization (balance the load)
OptimizationObjective balanceObjective = new OptimizationObjective(
    "balanceLoad",
    proc -> -getMaxCompressorUtilization(proc),  // Negative for minimization
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

System.out.println("Optimal flow: " + stage2Result.getOptimalRate() + " kg/hr");
System.out.println("Balanced splits: [" + optSplit1 + ", " + optSplit2 + ", " + 
    (1.0 - optSplit1 - optSplit2) + "]");
```

---

## Compressor Constraints

NeqSim automatically tracks these compressor constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `speed` | HARD | Current speed vs maximum speed |
| `minSpeed` | HARD | Current speed vs minimum speed (chart limit) |
| `power` | HARD | Current power vs driver max power at speed |
| `surgeMargin` | SOFT | Distance to surge line |
| `stonewallMargin` | SOFT | Distance to stonewall (choke) line |

### Accessing Constraints

```java
Map<String, CapacityConstraint> constraints = compressor.getCapacityConstraints();

for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
    CapacityConstraint c = entry.getValue();
    System.out.printf("%s: %.1f%% (current=%.2f, limit=%.2f)%n",
        entry.getKey(),
        c.getUtilizationPercent(),
        c.getCurrentValue(),
        c.getDesignValue()
    );
}
```

### Checking Simulation Validity

```java
if (!compressor.isSimulationValid()) {
    List<String> errors = compressor.getSimulationValidationErrors();
    for (String error : errors) {
        System.out.println("ERROR: " + error);
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
// P_max(N) = P_rated * (a + b*(N/N_rated) + c*(N/N_rated)²)
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

// Calculate flow bounds that correspond to chart speed limits
double lowerFlow = currentFlow * 0.8;   // Conservative lower
double upperFlow = currentFlow * 1.15;  // Don't exceed stonewall
```

### 4. Disable Capacity Analysis for Non-Critical Equipment

```java
// Manifold with velocity constraints may dominate unfairly in some tests
manifold.setCapacityAnalysisEnabled(false);
```

### 5. Check Results Before Accepting

```java
OptimizationResult result = optimizer.optimize(...);

// Verify feasibility
if (!result.isFeasible()) {
    System.out.println("WARNING: No feasible solution found");
}

// Verify utilization is bounded
double util = result.getBottleneckUtilization();
if (Double.isNaN(util) || Double.isInfinite(util) || util > 10.0) {
    System.out.println("WARNING: Utilization value is unrealistic: " + util);
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
from neqsim.neqsimpython import jneqsim
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

- [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) - General optimization guide
- [ProductionOptimizer Tutorial](../../examples/ProductionOptimizer_Tutorial.md) - Interactive Jupyter tutorial
- [Bottleneck Analysis](../../wiki/bottleneck_analysis.md) - Constraint framework
- [Optimization Overview](OPTIMIZATION_OVERVIEW.md) - When to use which optimizer
- [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) - SciPy/NLopt integration
