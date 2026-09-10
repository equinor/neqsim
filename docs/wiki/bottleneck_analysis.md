---
title: "Bottleneck Analysis and Capacity Utilization"
description: "NeqSim provides functionality to analyze capacity utilization and identify bottlenecks in a process simulation. This feature is useful for production optimization and debottlenecking studies."
---

# Bottleneck Analysis and Capacity Utilization

NeqSim provides functionality to analyze capacity utilization and identify bottlenecks in a process simulation. This feature is useful for production optimization and debottlenecking studies.

## Overview

The bottleneck analysis identifies which unit operation in a process system is operating closest to its maximum design capacity. The analysis is based on the "utilization ratio," defined as:

$$
\text{Utilization} = \frac{\text{Current Duty}}{\text{Maximum Capacity}}
$$

The unit operation with the highest utilization ratio is considered the bottleneck.

## Key Concepts

### 1. Capacity Duty (`getCapacityDuty`)
The `getCapacityDuty()` method returns the current operating load of a unit operation. The definition of "duty" varies by equipment type:
- **Compressor**: Total power consumption (Watts).
- **Separator**: Gas outlet flow rate ($m^3/hr$).
- **Other Equipment**: Default is 0.0 (needs implementation for specific units).

### 2. Maximum Capacity (`getCapacityMax`)
The `getCapacityMax()` method returns the maximum design capacity of the equipment. This value is typically set in the equipment's mechanical design.
- **Compressor**: the mechanical-design setter takes kW; `getCapacityMax()` reports W.
- **Separator**: `maxDesignGassVolumeFlow` ($m^3/hr$).

### 3. Rest Capacity (`getRestCapacity`)
The `getRestCapacity()` method calculates the remaining available capacity:
$$
\text{Rest Capacity} = \text{Maximum Capacity} - \text{Current Duty}
$$

Use `ProductionOptimizer.OptimizationConfig.capacityRangeForType` to supply P10/P50/P90
envelopes for equipment without deterministic limits and specify a percentile via
`capacityPercentile` (e.g., 0.1 for P10 or 0.9 for P90 stress tests).

## Implementation Details

### ProcessEquipmentInterface
The `ProcessEquipmentInterface` defines the methods for capacity analysis:
```text
public double getCapacityDuty();
public double getCapacityMax();
public double getRestCapacity();
```

### ProcessSystem
The `ProcessSystem` class includes a method to identify the bottleneck:
```text
public ProcessEquipmentInterface getBottleneck();
```
This method iterates through all unit operations in the system and returns the one with the highest utilization ratio.

## Supported Equipment

Currently, the following equipment types support capacity analysis:

| Equipment | Duty Metric | Capacity Metric | How to Set Capacity | Override After autoSize |
|-----------|-------------|-----------------|---------------------|------------------------|
| **Separator** | Gas flow (m³/hr) | Max allowable gas flow | `setDesignGasLoadFactor()`, `setInternalDiameter()` | `separator.setDesignGasLoadFactor(0.15)` |
| **Compressor** | Power (W) | Max design power | `initMechanicalDesign()` + `getMechanicalDesign().setMaxDesignPower()`, `setMaximumSpeed()` | `compressor.getMechanicalDesign().setMaxDesignPower(5000.0)` |
| **Pump** | Power (W) | Max design power | `getMechanicalDesign().setMaxDesignPower()` | `pump.getMechanicalDesign().setMaxDesignPower(100000)` |
| **Heater/Cooler** | Duty (W) | Max design duty | `getMechanicalDesign().setMaxDesignDuty()` | `heater.getMechanicalDesign().setMaxDesignDuty(1e6)` |
| **ThrottlingValve** | Volume flow (m³/hr) | Max volume flow | `setCv()`, mechanical `setMaxDesignVolumeFlow()` | `valve.setCv(200.0)` |
| **Pipeline/Pipe** | Volume flow (m³/hr) | Max design flow | `setMaxDesignVelocity()`, `setDiameter()` | `pipe.setMaxDesignVelocity(25.0)` |
| **DistillationColumn** | Fs hydraulic factor | Fs limit | `OptimizationConfig.columnFsFactorLimit()` | Configure in optimizer |
| **Custom types** | User-defined | User-defined | `addCapacityConstraint()` with a live supplier | N/A |

### Capacity Calculation Details

Keep three different reporting paths separate:

| Path | Separator behavior | Compressor behavior |
|------|--------------------|---------------------|
| Legacy `getCapacityDuty()/getCapacityMax()` | Gas flow in m³/hr divided by the mechanical gas-flow limit; fallback capacity is `K × area × 3600` | Shaft power in W divided by the available driver/mechanical power in W |
| Enabled direct `CapacityConstraint` objects | Named K-factor, nozzle momentum, retention and other configured limits | Power/rated power plus chart-dependent speed, surge and stonewall limits |
| `ProductionOptimizer` without enabled direct constraints | Legacy type-specific liquid-level fraction relative to 1.0 | Capacity strategy or legacy duty/maximum fallback |

The legacy separator fallback is not the full Souders–Brown rating equation. Enable a named
gas-load constraint for K-factor analysis, and supply the required geometry and phase properties.
Similarly, a valve's legacy methods use volume flow, whereas the optimizer's fallback uses opening
only when a Cv/Kv and an opening ceiling below 100% are configured. Pipe capacity depends on the
particular pipe class and configured velocity limit; a screening default is not an erosional rating.

`ProcessSystem.getCapacityUtilizationSummary()` reports **percent**, while
`getBottleneckUtilization()` and `CapacityConstraint.getUtilization()` report **fractions**.
For minimum limits such as residence time, utilization is minimum/current; more available residence
time gives a lower utilization. `getDisplayDesignValue()` displays the appropriate physical limit.

## Example Usage

The following example demonstrates how to set up a simulation, define capacities, and identify the bottleneck.

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class BottleneckExample {
    private static final org.apache.logging.log4j.Logger logger =
        org.apache.logging.log4j.LogManager.getLogger(BottleneckExample.class);
    public static void main(String[] args) {
        // 1. Create System
        SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
        testSystem.addComponent("methane", 100.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream inletStream = new Stream("inlet stream", testSystem);
        inletStream.setFlowRate(10000.0, "kg/hr");
        inletStream.setTemperature(20.0, "C");
        inletStream.setPressure(10.0, "bara");

        // 2. Create Equipment and Set Capacities
        Separator separator = new Separator("separator", inletStream);
        // Explicit gas-flow design basis, at operating conditions (m3/hr)
        separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(2000.0);

        Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
        compressor.setOutletPressure(50.0);
        // Set Compressor Capacity (e.g., 5 MW)
        compressor.getMechanicalDesign().setMaxDesignPower(5000.0); // kW

        // 3. Run Simulation
        ProcessSystem process = new ProcessSystem();
        process.add(inletStream);
        process.add(separator);
        process.add(compressor);
        process.run();

        // 4. Analyze Results
        logger.info("{}", "Separator Duty: " + separator.getCapacityDuty());
        logger.info("{}", "Separator Max: " + separator.getCapacityMax());
        logger.info("{}", "Compressor Duty: " + compressor.getCapacityDuty());
        logger.info("{}", "Compressor Max: " + compressor.getCapacityMax());

        if (process.getBottleneck() != null) {
            logger.info("{}", "Bottleneck: " + process.getBottleneck().getName());
            double utilization = process.getBottleneckUtilization();
            logger.info("{}", "Utilization: " + (utilization * 100) + "%");
        } else {
            logger.info("{}", "No bottleneck found (or capacity not set)");
        }

        logger.info("{}", "Compressor Rest Capacity: " + compressor.getRestCapacity());
    }
}
```

## Extending to Other Equipment

For a new custom equipment type, implement the `getCapacityDuty()` and `getCapacityMax()` methods in the respective classes. Ensure that the units for duty and capacity are consistent (e.g., both in Watts or both in kg/hr).

## Multi-Constraint Capacity Analysis

For equipment with multiple capacity constraints (e.g., compressors limited by speed, power, and surge margin), NeqSim provides the `CapacityConstrainedEquipment` interface in `neqsim.process.equipment.capacity`.

### Key Features

- **Multiple constraints per equipment**: Track speed, power, surge margin, discharge temperature, etc.
- **Constraint types**: HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope)
- **Automatic integration**: `ProcessSystem.getBottleneck()` automatically uses multi-constraint data when available
- **Detailed analysis**: `ProcessSystem.findBottleneck()` returns specific constraint information

### Constraint Types

| Type | Description | Example |
|------|-------------|---------|
| `HARD` | Absolute limit - trip or damage if exceeded | Max compressor speed, surge limit |
| `SOFT` | Operational limit - reduced efficiency | High discharge temperature |
| `DESIGN` | Normal operating envelope | Separator gas load factor |

### Example: Multi-Constraint Analysis

```java
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;

// Run simulation
process.run();

// Simple bottleneck detection (works with both single and multi-constraint)
ProcessEquipmentInterface bottleneck = process.getBottleneck();
double utilization = process.getBottleneckUtilization();
logger.info("{}", "Bottleneck: " + bottleneck.getName() + " at " + (utilization * 100) + "%");

// Detailed constraint information (multi-constraint equipment only)
BottleneckResult result = process.findBottleneck();
if (result.hasBottleneck()) {
    logger.info("{}", "Equipment: " + result.getEquipmentName());
    logger.info("{}", "Limiting constraint: " + result.getConstraint().getName());
    logger.info("{}", "Utilization: " + result.getUtilizationPercent() + "%");
}

// Check specific equipment constraints
Compressor comp = (Compressor) process.getUnit("compressor");
for (CapacityConstraint c : comp.getCapacityConstraints().values()) {
    logger.info("{}", String.format("  %s: %.1f / %.1f %s (%.1f%%)%n",
        c.getName(), c.getCurrentValue(), c.getDisplayDesignValue(),
        c.getUnit(), c.getUtilizationPercent()));
}

// Check for critical conditions
if (process.isAnyHardLimitExceeded()) {
    logger.info("{}", "CRITICAL: Equipment hard limits exceeded!");
}
if (process.isAnyEquipmentOverloaded()) {
    logger.info("{}", "WARNING: Equipment operating above design capacity");
}
```

### Supported Multi-Constraint Equipment

| Equipment | Constraints |
|-----------|-------------|
| **Separator** | Gas load factor (vs design K-factor) |
| **Compressor** | Speed, Power, Surge margin |

For detailed documentation on extending to other equipment, see [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK).

## Production Optimization

The bottleneck analysis feature is a powerful tool for optimizing production. By identifying the limiting constraint in a process, you can maximize throughput or identify the most effective upgrades (debottlenecking).

### Optimization Workflow

1.  **Define Objective**: Configure one or more objectives (e.g., maximize throughput while penalizing power) using `OptimizationObjective` weights.
2.  **Identify Constraints**: Provide utilization limits per equipment name or type plus custom hard/soft constraints via `OptimizationConstraint`. Safety margins and capacity-uncertainty factors can be applied globally so bottleneck checks keep headroom.
3.  **Iterative Solver (selectable)**:
    *   `BINARY_FEASIBILITY` (default) targets monotonic systems and searches on feasibility margins.
    *   `GOLDEN_SECTION_SCORE` samples non-monotonic responses using weighted objectives and constraint penalties to guide the search.
    *   `NELDER_MEAD_SCORE` applies a simplex-based heuristic to handle noisy or coupled objectives without assuming monotonicity.
    *   `PARTICLE_SWARM_SCORE` explores the design space with a configurable swarm size/inertia/weights, useful when the objective landscape has multiple peaks.
    *   `GRADIENT_DESCENT_SCORE` **(New Jan 2026)** uses finite-difference gradients with Armijo line search for smooth multi-variable problems (5-20+ variables).
4.  **Diagnostics & reporting**:
    *   Each run keeps an `iterationHistory` with per-iteration utilization snapshots so you can plot trajectories of bottleneck movement and score versus candidate rate to understand convergence.
    *   Use `ProductionOptimizer.buildUtilizationSeries(result.getIterationHistory())` to feed plotting libraries or CSV exports and `formatUtilizationTimeline(...)` to highlight bottlenecks per iteration in Markdown.
    *   Use `ProductionOptimizer.formatUtilizationTable(result.getUtilizationRecords())` to render a quick Markdown table of duties, capacities, and limits for reports.
    *   Scenario helpers let you run a base case and multiple debottleneck cases in one call for side-by-side reporting, including KPI deltas and Markdown tables that highlight the gain relative to the baseline.
    *   Caching (enabled by default) reuses steady-state evaluations at similar rates to cut down on reruns during heuristic searches.

### Example: Using `ProductionOptimizer`

The `ProductionOptimizer` utility adds structured reporting and constraint handling on top of the existing bottleneck functions:

```java
import java.util.List;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;

ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(100.0, 5_000.0)
    .rateUnit("kg/hr")
    .tolerance(5.0)
    .defaultUtilizationLimit(0.95)
    .utilizationMarginFraction(0.1) // keep 10% headroom on every unit
    .capacityUncertaintyFraction(0.05) // down-rate capacities for uncertainty
    .capacityPercentile(0.1) // pick P10/P50/P90 from optional ranges
    .capacityRangeSpreadFraction(0.15) // auto-build P10/P90 around design capacity
    .columnFsFactorLimit(2.2) // set column hydraulic headroom
    .utilizationLimitForName("compressor", 0.9);

OptimizationObjective objective = new OptimizationObjective("maximize rate",
    proc -> ((StreamInterface) proc.getUnit("inlet stream")).getFlowRate("kg/hr"), 1.0);

OptimizationConstraint keepPowerLow = OptimizationConstraint.lessThan("compressor load",
    proc -> proc.getUnit("compressor").getMaxUtilization(), 0.9,
    ConstraintSeverity.SOFT, 5.0, "Prefer 10% safety margin on compressor");

// Enforce equipment-type constraints (e.g., pressure ratio below 10 for all compressors)
List<OptimizationConstraint> compressorConstraints = new java.util.ArrayList<>();
compressorConstraints.add(keepPowerLow);
for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (unit instanceof Compressor) {
        final String name = unit.getName();
        compressorConstraints.add(OptimizationConstraint.lessThan("pressure ratio " + name,
            proc -> {
                Compressor current = (Compressor) proc.getUnit(name);
                return current.getOutletStream().getPressure("bara")
                    / current.getInletStream().getPressure("bara");
            }, 10.0, ConstraintSeverity.HARD, 0.0, "Keep pressure ratio within design"));
    }
}

OptimizationResult result = optimizer.optimize(process, inletStream, config,
    Arrays.asList(objective), compressorConstraints);

logger.info("{}", "Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());
logger.info("{}", "Bottleneck: " + (result.getBottleneck() == null ? "None" : result.getBottleneck().getName()));
result.getUtilizationRecords().forEach(record ->
    logger.info("{}", record.getEquipmentName() + " utilization: " + record.getUtilization()));
// Optional: plot or log iteration history for transparency
result.getIterationHistory().forEach(iter -> logger.info("{}",
    "Iter " + iter.getRate() + " " + iter.getRateUnit() + " bottleneck="
        + iter.getBottleneckName() + " feasible=" + iter.isFeasible() + " score="
        + iter.getScore() + " utilizationCount=" + iter.getUtilizations().size()));

// Quick high-level summary without manual bounds/objective wiring
OptimizationSummary summary = optimizer.quickOptimize(process, inletStream);
logger.info("{}", "Max rate: " + summary.getMaxRate() + " " + summary.getRateUnit());
logger.info("{}", "Limiting equipment: " + summary.getLimitingEquipment()
    + " margin=" + summary.getUtilizationMargin());
logger.info("{}", ProductionOptimizer.formatUtilizationTimeline(result.getIterationHistory()));

// Built-in capacity coverage now includes separators (liquid level fraction) and
// MultiStream heat exchangers (duty vs design) in addition to compressors/pumps/columns.

// Swarm search example via YAML/JSON specs
// searchMode, swarmSize, inertiaWeight, and capacityPercentile can be provided per scenario
```

To vary multiple feeds or set points at once (e.g., two inlet streams plus a compressor pressure),
define `ManipulatedVariable` instances and call the multi-variable overload:

```java
ManipulatedVariable feedNorth = new ManipulatedVariable("north", 100.0, 800.0, "kg/hr",
    (proc, value) -> northStream.setFlowRate(value, "kg/hr"));
ManipulatedVariable feedSouth = new ManipulatedVariable("south", 100.0, 800.0, "kg/hr",
    (proc, value) -> southStream.setFlowRate(value, "kg/hr"));
ManipulatedVariable compressorSetPoint = new ManipulatedVariable("compressor pressure", 40.0,
    80.0, "bara", (proc, value) -> compressor.setOutletPressure(value));

OptimizationResult multiVar = optimizer.optimize(process, Arrays.asList(feedNorth, feedSouth,
    compressorSetPoint), config.searchMode(SearchMode.PARTICLE_SWARM_SCORE), Arrays.asList(objective),
    Arrays.asList(keepPowerLow));
```

### Multi-Variable Optimization with `ManipulatedVariable`

The `ManipulatedVariable` class enables optimization over arbitrary process parameters beyond
inlet flow rates. This is essential for complex systems where multiple degrees of freedom
affect the bottleneck—such as flow distribution between parallel trains, intermediate
pressures, or heat integration setpoints.

#### ManipulatedVariable API

```text
public class ManipulatedVariable {
    /**
     * Create a decision variable for optimization.
     *
     * @param name        Human-readable variable name (appears in logs/reports)
     * @param lowerBound  Minimum allowed value
     * @param upperBound  Maximum allowed value
     * @param unit        Engineering unit string (informational)
     * @param setter      BiConsumer that applies the value to the ProcessSystem
     */
    public ManipulatedVariable(String name, double lowerBound, double upperBound,
            String unit, BiConsumer<ProcessSystem, Double> setter);

    public String getName();
    public double getLowerBound();
    public double getUpperBound();
    public String getUnit();
    public void apply(ProcessSystem process, double value);
}
```

The `setter` parameter is a `BiConsumer<ProcessSystem, Double>` lambda that receives the
process and a candidate value from the optimizer. This allows you to manipulate any equipment
parameter—not just stream flow rates.

#### Common Use Cases

| Scenario | Variables | Setter Example |
|----------|-----------|----------------|
| **Parallel train balancing** | Split factors | `splitter.setSplitFactors(new double[]{val, 0.33, 0.67-val})` |
| **Dual-feed systems** | Two inlet flows | `feedA.setFlowRate(val, "kg/hr")` |
| **Pressure optimization** | Compressor setpoints | `comp.setOutletPressure(val)` |
| **Temperature control** | Heater/cooler setpoints | `heater.setOutletTemperature(val)` |
| **Recycle ratio** | Recycle stream split | `recycler.setFlowRate(val, "kg/hr")` |

#### Example: Split Factor Optimization

When a process has parallel compression trains served by a common inlet, the optimal split
depends on each train's capacity curve and driver limits. The optimizer can find the best
distribution:

```java
// Define system with splitter and three parallel trains
Splitter splitter = new Splitter("inlet_splitter", inletStream, 3);
// ... compressors ups1, ups2, ups3 downstream of splitter

// Create manipulated variables
ManipulatedVariable inletFlow = new ManipulatedVariable(
    "TotalInletFlow", 1_800_000.0, 2_200_000.0, "kg/hr",
    (proc, val) -> inletStream.setFlowRate(val, "kg/hr"));

// Balance factor: shifts flow from train 1 to train 3
// At bal=0: equal split (33.3% / 33.3% / 33.3%)
// At bal=+0.05: train 3 gets more (28.3% / 33.3% / 38.3%)
ManipulatedVariable balanceFactor = new ManipulatedVariable(
    "BalanceFactor", -0.10, 0.10, "factor",
    (proc, val) -> {
        double base = 1.0 / 3.0;
        splitter.setSplitFactors(new double[]{base - val, base, base + val});
    });

List<ManipulatedVariable> variables = Arrays.asList(inletFlow, balanceFactor);

OptimizationConfig config = new OptimizationConfig(1_800_000.0, 2_200_000.0)
    .rateUnit("kg/hr")
    .tolerance(1000.0)
    .defaultUtilizationLimit(0.99)
    .searchMode(SearchMode.NELDER_MEAD_SCORE);

OptimizationResult result = optimizer.optimize(process, variables, config,
    Collections.singletonList(new OptimizationObjective("throughput",
        proc -> inletStream.getFlowRate("kg/hr"), 1.0)),
    Collections.emptyList());

logger.info("{}", "Optimal flow: " + result.getOptimalRate() + " kg/hr");
logger.info("{}", "Optimal split: " + Arrays.toString(splitter.getSplitFactors()));
```

#### Choosing a Search Mode for Multi-Variable Problems

| Search Mode | Best For | Characteristics |
|-------------|----------|-----------------|
| `GOLDEN_SECTION_SCORE` | One variable only | Fast convergence on a unimodal score |
| `NELDER_MEAD_SCORE` | 2-4 variables, noisy responses | Robust simplex method, handles local noise |
| `PARTICLE_SWARM_SCORE` | 3+ variables, multimodal | Global search, configurable swarm size |

**Caution**: Gradient-free optimizers (Nelder-Mead, PSO) may explore infeasible regions where
equipment solvers (e.g., compressor chart interpolation) fail or return unrealistic values.
Strategies to handle this:

1. **Tighten bounds** to stay within solver-reliable operating ranges
2. **Add soft constraints** with high penalty weights in infeasible regions
3. **Use grid search** as a fallback for critical decisions:

```java
// Grid search for robustness when chart solvers are sensitive
double bestFlow = 0, bestBalance = 0, maxFeasibleFlow = 0;
for (double flow = 1_900_000; flow <= 2_150_000; flow += 10_000) {
    for (double bal = -0.10; bal <= 0.10; bal += 0.02) {
        inletStream.setFlowRate(flow, "kg/hr");
        splitter.setSplitFactors(new double[]{1.0 / 3.0 - bal, 1.0 / 3.0, 1.0 / 3.0 + bal});
        process.run();
        double util = process.getBottleneckUtilization();
        if (util < 1.0 && flow > maxFeasibleFlow) {
            maxFeasibleFlow = flow;
            bestFlow = flow;
            bestBalance = bal;
        }
    }
}
```

#### Example: Dual-Feed Optimization

For systems with multiple inlet streams feeding a common process:

```java
ManipulatedVariable feedNorth = new ManipulatedVariable(
    "NorthFeed", 100.0, 800.0, "kg/hr",
    (proc, val) -> northInlet.setFlowRate(val, "kg/hr"));

ManipulatedVariable feedSouth = new ManipulatedVariable(
    "SouthFeed", 100.0, 800.0, "kg/hr",
    (proc, val) -> southInlet.setFlowRate(val, "kg/hr"));

// Total throughput objective
OptimizationObjective totalThroughput = new OptimizationObjective(
    "totalThroughput",
    proc -> northInlet.getFlowRate("kg/hr") + southInlet.getFlowRate("kg/hr"),
    1.0);

OptimizationResult result = optimizer.optimize(process,
    Arrays.asList(feedNorth, feedSouth),
    config.searchMode(SearchMode.PARTICLE_SWARM_SCORE),
    Collections.singletonList(totalThroughput),
    Collections.emptyList());
```

#### Practical Considerations

1. **Equal-capacity trains**: For parallel trains with similar equipment specs, equal split is
   often near-optimal. Split optimization provides more value when trains have asymmetric
   capacities (e.g., different compressor sizes or driver ratings).

2. **Solver stability**: Compressor chart solvers may produce erroneous results (e.g., 99,000%
   utilization) when flows fall outside the interpolation envelope. Always validate results
   against physical bounds.

3. **Variable coupling**: Some variables are tightly coupled (e.g., split factors must sum to
   1.0). Encode these constraints in the setter lambda rather than relying on the optimizer.

4. **Iteration budget**: Multi-variable optimization requires more evaluations. Set appropriate
   `maxIterations` in the config (default is often too low for PSO with 3+ variables).

### Comparing debottlenecking scenarios

Use `compareScenarios` to run a baseline plus multiple upgrades and compute KPI deltas in one
report-ready table:

```java
ScenarioRequest baseCase = new ScenarioRequest("base", baseProcess, baseFeed, baseConfig,
    Arrays.asList(objective), Arrays.asList(keepPowerLow));
ScenarioRequest upgradeCase = new ScenarioRequest("upgrade", upgradedProcess, upgradedFeed,
    baseConfig, Arrays.asList(objective), Arrays.asList(keepPowerLow));

List<ScenarioKpi> kpis = Arrays.asList(ScenarioKpi.optimalRate("kg/hr"), ScenarioKpi.score());
ScenarioComparisonResult comparison = optimizer.compareScenarios(
    Arrays.asList(baseCase, upgradeCase), kpis);

logger.info("{}", ProductionOptimizer.formatScenarioComparisonTable(comparison, kpis));
```

The first scenario is treated as the baseline; each KPI cell shows `value (Δbaseline)` so uplift from
debottlenecking is immediately visible alongside bottleneck names and feasibility flags.

### Running from JSON/YAML specs

For reproducible CLI/CI runs, define scenarios in a YAML or JSON file (bounds, objectives,
constraints) and load them via `ProductionOptimizationSpecLoader.load(...)` while passing in a
registry of process models, feed streams, and metric functions keyed by name. This allows
side-by-side optimization of investment options without hard-coding Java configuration:

```yaml
scenarios:
  - name: base
    process: baseProcess
    feedStream: inlet
    lowerBound: 100.0
    upperBound: 2000.0
    rateUnit: kg/hr
    searchMode: BINARY_FEASIBILITY
    constraints:
      - name: column_pressure
        metric: columnPressureRatio
        limit: 1.8
        direction: LESS_THAN
        severity: HARD
  - name: upgrade
    process: upgradedProcess
    feedStream: inlet
    lowerBound: 100.0
    upperBound: 2500.0
    rateUnit: kg/hr
    searchMode: PARTICLE_SWARM_SCORE
```

After loading, call `optimizer.optimizeScenarios(...)` or `optimizer.compareScenarios(...)` to render
side-by-side KPIs automatically for the pipeline or report.

#### Advanced YAML with multi-objective scoring and variable feeds

To mirror the multi-objective/variable-driven test coverage, you can encode both throughput and
penalty objectives while letting a swarm search vary a feed stream directly:

```yaml
scenarios:
  - name: base
    process: base
    feedStream: feed1
    lowerBound: 100.0
    upperBound: 320.0
    rateUnit: kg/hr
    capacityPercentile: 0.9
    objectives:
      - name: rate
        metric: throughput
        weight: 1.0
        type: MAXIMIZE
      - name: compressorUtilPenalty
        metric: compressorUtil
        weight: -0.1
        type: MAXIMIZE
    constraints:
      - name: utilizationCap
        metric: compressorUtil
        limit: 0.95
        direction: LESS_THAN
        severity: HARD
        penaltyWeight: 0.0
        description: Keep compressor within design
  - name: upgrade
    process: upgrade
    lowerBound: 120.0
    upperBound: 340.0
    rateUnit: kg/hr
    searchMode: PARTICLE_SWARM_SCORE
    utilizationMarginFraction: 0.05
    capacityPercentile: 0.9
    variables:
      - name: feed2Variable
        stream: feed2
        lowerBound: 120.0
        upperBound: 340.0
        unit: kg/hr
    objectives:
      - name: rate
        metric: throughput
        weight: 1.0
        type: MAXIMIZE
    constraints:
      - name: utilizationCap
        metric: compressorUtil
        limit: 0.95
        direction: LESS_THAN
        severity: HARD
        penaltyWeight: 0.0
        description: Keep compressor within design
```

Hook this into `ProductionOptimizationSpecLoader.load(...)` with metric lambdas for `throughput` and
`compressorUtil`, then call `optimizer.optimizeScenarios(...)` to exercise the same workflow shown in
the regression test while generating Markdown comparison tables for reports.

#### Real-world spec-driven workflows

The same YAML/JSON specs can be extended to mirror common operational optimization tasks instead of
toy throughput maximization:

**1. Energy minimization across compressor trains**

The spec loader's `variables[].stream` field changes a **stream flow rate**. It does not set
pressure, temperature, or valve opening. Use Java manipulated-variable setters for those inputs.
For a solved three-stage `process` containing `stage1`, `stage2`, and `stage3` compressors with
interstage coolers and fixed feed flow, configure the final stage to deliver 90 bara:

```java
Compressor finalStage = (Compressor) process.getUnit("stage3");
finalStage.setOutletPressure(90.0, "bara");
List<ManipulatedVariable> pressures = Arrays.asList(
    new ManipulatedVariable("stage1 pressure", 30.0, 45.0, "bara",
        (proc, value) -> ((Compressor) proc.getUnit("stage1")).setOutletPressure(value, "bara")),
    new ManipulatedVariable("stage2 pressure", 50.0, 70.0, "bara",
        (proc, value) -> ((Compressor) proc.getUnit("stage2")).setOutletPressure(value, "bara")));
OptimizationObjective power = new OptimizationObjective("total power",
    proc -> proc.getPower("kW"), 1.0, ObjectiveType.MINIMIZE);
OptimizationConfig pressureConfig = new OptimizationConfig(30.0, 70.0)
    .searchMode(SearchMode.NELDER_MEAD_SCORE).maxIterations(50).tolerance(0.01);
OptimizationResult energyResult = optimizer.optimize(process, pressures, pressureConfig,
    Collections.singletonList(power), Collections.emptyList());
logger.info("Pressure settings: {}", energyResult.getDecisionVariables());
logger.info("Feasible: {}", energyResult.isFeasible());
```

**2. Choke capacity and downstream separation**

For a solved process with `inletStream`, a `choke` valve and a downstream separator, configure
the installed Cv and pressure drop. Optimize the inlet rate while the valve solver calculates
the required opening. An imposed feed rate is not independently determined by changing a valve
opening; reservoir deliverability requires a coupled well/network model.

```java
ThrottlingValve choke = (ThrottlingValve) process.getUnit("choke");
choke.setCv(20.0); // Synthetic installed rating; replace with approved valve data.
choke.setOutletPressure(30.0, "bara");
choke.setMaximumValveOpening(80.0);
OptimizationConstraint opening = OptimizationConstraint.lessThan("choke opening",
    proc -> ((ThrottlingValve) proc.getUnit("choke")).getPercentValveOpening(),
    80.0, ConstraintSeverity.HARD, 0.0, "Installed choke operating ceiling, percent");
OptimizationConfig chokeConfig = new OptimizationConfig(1000.0, 20000.0)
    .rateUnit("kg/hr").searchMode(SearchMode.BINARY_FEASIBILITY).tolerance(10.0);
OptimizationResult chokeResult = optimizer.optimize(process, inletStream, chokeConfig,
    Collections.emptyList(), Collections.singletonList(opening));
logger.info("Proposed rate: {} kg/hr; feasible: {}",
    chokeResult.getOptimalRate(), chokeResult.isFeasible());
```

Sand production and erosion limits require a qualified, separately supplied model or measured
evidence. Register those as additional hard constraints before interpreting this example as a
well operating envelope; this example does not invent a sand-production correlation.

### Debottlenecking Studies

Once the bottleneck is identified (e.g., a compressor), you can simulate a "debottlenecking" project:
1.  Increase the capacity of the bottleneck equipment (e.g., `compressor.getMechanicalDesign().setMaxDesignPower(newPowerKW)`).
2.  Re-run the optimization loop.
3.  Identify the *new* bottleneck and the new maximum production rate.
4.  Calculate the ROI of the upgrade based on the increased production.

