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
- **Compressor**: `maxDesignPower` (Watts).
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
```java
public double getCapacityDuty();
public double getCapacityMax();
public double getRestCapacity();
```

### ProcessSystem
The `ProcessSystem` class includes a method to identify the bottleneck:
```java
public ProcessEquipmentInterface getBottleneck();
```
This method iterates through all unit operations in the system and returns the one with the highest utilization ratio.

## Supported Equipment

Currently, the following equipment types support capacity analysis:

| Equipment | Duty Metric | Capacity Parameter |
|-----------|-------------|--------------------|
| **Compressor** | Power (W) | `MechanicalDesign.maxDesignPower` with optional P10/P50/P90 overrides |
| **Separator** | Gas Flow ($m^3/hr$) | `MechanicalDesign.maxDesignGassVolumeFlow` |
| **Pump** | Power (W) | `MechanicalDesign.maxDesignPower` |
| **Heater** | Duty (W) | `MechanicalDesign.maxDesignDuty` |
| **Cooler** | Duty (W) | `MechanicalDesign.maxDesignDuty` |
| **ThrottlingValve** | Volume Flow ($m^3/hr$) | `MechanicalDesign.maxDesignVolumeFlow` |
| **Pipeline** | Volume Flow ($m^3/hr$) | `MechanicalDesign.maxDesignVolumeFlow` |
| **DistillationColumn** | Fs hydraulic factor | `OptimizationConfig.columnFsFactorLimit` (default 2.5) |
| **Custom types** | User-supplied duty/limit lambdas | Configure via `capacityRuleForType` |

## Example Usage

The following example demonstrates how to set up a simulation, define capacities, and identify the bottleneck.

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

public class BottleneckExample {
    public static void main(String[] args) {
        // 1. Create System
        SystemSrkEos testSystem = new SystemSrkEos(298.15, 10.0);
        testSystem.addComponent("methane", 100.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream inletStream = new Stream("inlet stream", testSystem);
        inletStream.setFlowRate(100.0, "MSm3/day");
        inletStream.setTemperature(20.0, "C");
        inletStream.setPressure(10.0, "bara");

        // 2. Create Equipment and Set Capacities
        Separator separator = new Separator("separator", inletStream);
        // Set Separator Capacity (e.g., 200 m3/hr)
        separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(200.0); 

        Compressor compressor = new Compressor("compressor", separator.getGasOutStream());
        compressor.setOutletPressure(50.0);
        // Set Compressor Capacity (e.g., 5 MW)
        compressor.getMechanicalDesign().maxDesignPower = 5000000.0; 

        // 3. Run Simulation
        ProcessSystem process = new ProcessSystem();
        process.add(inletStream);
        process.add(separator);
        process.add(compressor);
        process.run();

        // 4. Analyze Results
        System.out.println("Separator Duty: " + separator.getCapacityDuty());
        System.out.println("Separator Max: " + separator.getCapacityMax());
        System.out.println("Compressor Duty: " + compressor.getCapacityDuty());
        System.out.println("Compressor Max: " + compressor.getCapacityMax());

        if (process.getBottleneck() != null) {
            System.out.println("Bottleneck: " + process.getBottleneck().getName());
            double utilization = process.getBottleneck().getCapacityDuty() / process.getBottleneck().getCapacityMax();
            System.out.println("Utilization: " + (utilization * 100) + "%");
        } else {
            System.out.println("No bottleneck found (or capacity not set)");
        }
        
        System.out.println("Compressor Rest Capacity: " + compressor.getRestCapacity());
    }
}
```

## Extending to Other Equipment

To support capacity analysis for other equipment types (e.g., Pumps, Heat Exchangers), implement the `getCapacityDuty()` and `getCapacityMax()` methods in the respective classes. Ensure that the units for duty and capacity are consistent (e.g., both in Watts or both in kg/hr).

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
System.out.println("Bottleneck: " + bottleneck.getName() + " at " + (utilization * 100) + "%");

// Detailed constraint information (multi-constraint equipment only)
BottleneckResult result = process.findBottleneck();
if (!result.isEmpty()) {
    System.out.println("Equipment: " + result.getEquipmentName());
    System.out.println("Limiting constraint: " + result.getConstraint().getName());
    System.out.println("Utilization: " + result.getUtilizationPercent() + "%");
}

// Check specific equipment constraints
Compressor comp = (Compressor) process.getUnit("compressor");
for (CapacityConstraint c : comp.getCapacityConstraints().values()) {
    System.out.printf("  %s: %.1f / %.1f %s (%.1f%%)%n",
        c.getName(), c.getCurrentValue(), c.getDesignValue(), 
        c.getUnit(), c.getUtilizationPercent());
}

// Check for critical conditions
if (process.isAnyHardLimitExceeded()) {
    System.out.println("CRITICAL: Equipment hard limits exceeded!");
}
if (process.isAnyEquipmentOverloaded()) {
    System.out.println("WARNING: Equipment operating above design capacity");
}
```

### Supported Multi-Constraint Equipment

| Equipment | Constraints |
|-----------|-------------|
| **Separator** | Gas load factor (vs design K-factor) |
| **Compressor** | Speed, Power, Surge margin |

For detailed documentation on extending to other equipment, see [Capacity Constraint Framework](../process/CAPACITY_CONSTRAINT_FRAMEWORK.md).

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
import neqsim.process.util.optimization.ProductionOptimizer;
import neqsim.process.util.optimization.ProductionOptimizer.ConstraintSeverity;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationConstraint;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimization.ProductionOptimizer.OptimizationResult;

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
    proc -> process.getBottleneck().getCapacityDuty(), 1.0);

OptimizationConstraint keepPowerLow = OptimizationConstraint.lessThan("compressor load",
    proc -> compressor.getCapacityDuty() / compressor.getCapacityMax(), 0.9,
    ConstraintSeverity.SOFT, 5.0, "Prefer 10% safety margin on compressor");

// Enforce equipment-type constraints (e.g., pressure ratio below 10 for all compressors)
config.equipmentConstraintRule(new EquipmentConstraintRule(Compressor.class, "pressure ratio",
    unit -> ((Compressor) unit).getOutStream().getPressure() / ((Compressor) unit)
        .getInletStream().getPressure(), 10.0,
    ProductionOptimizer.ConstraintDirection.LESS_THAN, ConstraintSeverity.HARD, 0.0,
    "Keep pressure ratio within design"));

OptimizationResult result = optimizer.optimize(process, inletStream, config,
    List.of(objective), List.of(keepPowerLow));

System.out.println("Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
result.getUtilizationRecords().forEach(record ->
    System.out.println(record.getEquipmentName() + " utilization: " + record.getUtilization()));
// Optional: plot or log iteration history for transparency
result.getIterationHistory().forEach(iter -> System.out.println(
    "Iter " + iter.getRate() + " " + iter.getRateUnit() + " bottleneck="
        + iter.getBottleneckName() + " feasible=" + iter.isFeasible() + " score="
        + iter.getScore() + " utilizationCount=" + iter.getUtilizations().size()));

// Quick high-level summary without manual bounds/objective wiring
OptimizationSummary summary = optimizer.quickOptimize(process, inletStream);
System.out.println("Max rate: " + summary.getMaxRate() + " " + summary.getRateUnit());
System.out.println("Limiting equipment: " + summary.getLimitingEquipment()
    + " margin=" + summary.getUtilizationMargin());
System.out.println(ProductionOptimizer.formatUtilizationTimeline(result.getIterationHistory()));

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

OptimizationResult multiVar = optimizer.optimize(process, List.of(feedNorth, feedSouth,
    compressorSetPoint), config.searchMode(SearchMode.PARTICLE_SWARM_SCORE), List.of(objective),
    List.of(keepPowerLow));
```

### Comparing debottlenecking scenarios

Use `compareScenarios` to run a baseline plus multiple upgrades and compute KPI deltas in one
report-ready table:

```java
ScenarioRequest baseCase = new ScenarioRequest("base", baseProcess, baseFeed, baseConfig,
    List.of(objective), List.of(keepPowerLow));
ScenarioRequest upgradeCase = new ScenarioRequest("upgrade", upgradedProcess, upgradedFeed,
    baseConfig, List.of(objective), List.of(keepPowerLow));

List<ScenarioKpi> kpis = List.of(ScenarioKpi.optimalRate("kg/hr"), ScenarioKpi.score());
ScenarioComparisonResult comparison = optimizer.compareScenarios(
    List.of(baseCase, upgradeCase), kpis);

System.out.println(ProductionOptimizer.formatScenarioComparisonTable(comparison, kpis));
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

Model a three-stage compression train with interstage coolers and set the objective to minimize
total power while still honoring a required discharge pressure and anti-surge utilization headroom:

```yaml
scenarios:
  - name: energy_min_train
    process: c_train
    feedStream: feed_gas
    lowerBound: 40.0
    upperBound: 90.0
    rateUnit: bara # target discharge pressure instead of flow
    variables:
      - name: stage1_pressure
        unit: bara
        lowerBound: 30.0
        upperBound: 45.0
        stream: stage1_out
      - name: stage2_pressure
        unit: bara
        lowerBound: 50.0
        upperBound: 70.0
        stream: stage2_out
    objectives:
      - name: minimize_power
        metric: totalPowerMw
        weight: -1.0
        type: MAXIMIZE
    constraints:
      - name: discharge_pressure
        metric: dischargePressure
        limit: 90.0
        direction: GREATER_THAN
        severity: HARD
        description: Keep export pressure above spec
      - name: anti_surge_headroom
        metric: minSurgeMargin
        limit: 1.1
        direction: GREATER_THAN
        severity: HARD
        description: Maintain 10% margin to surge lines on all compressors
    searchMode: PARTICLE_SWARM_SCORE
    inertiaWeight: 0.8
    swarmSize: 24
```

Wire metrics via the spec loader to compute `totalPowerMw` from compressor duties (sum of
`getShaftWork()` per stage) and `minSurgeMargin` from a helper that returns the lowest ratio of
operating flow to surge flow across the train. Inspect `result.getIterationHistory()` to see where
power flattens out—large step sizes in the swarm can reveal solver-cost bottlenecks when each
iteration requires full thermodynamics and anti-surge calculations.

**2. Choke optimization under sand/erosion constraints**

Use a sand production limit and downstream separator capacity as hard constraints while maximizing
oil throughput in a well/test separator setup. The choke opening becomes the manipulated variable,
and penalty objectives can keep gas-lift rates reasonable:

```yaml
scenarios:
  - name: choke_max_oil
    process: wellpad
    feedStream: wellhead
    lowerBound: 10.0
    upperBound: 80.0
    rateUnit: percent_open
    variables:
      - name: choke_opening
        unit: percent
        lowerBound: 10.0
        upperBound: 80.0
        stream: choke_setting
    objectives:
      - name: oil_rate
        metric: stabilizedOilBpd
        weight: 1.0
        type: MAXIMIZE
      - name: gaslift_penalty
        metric: gasliftRate
        weight: -0.05
        type: MAXIMIZE
    constraints:
      - name: sand_limit
        metric: sandRate
        limit: 20.0
        direction: LESS_THAN
        severity: HARD
        description: Protect downstream erosion limit (kg/day)
      - name: separator_capacity
        metric: separatorUtil
        limit: 0.95
        direction: LESS_THAN
        severity: HARD
        description: Keep test separator within design envelope
    searchMode: BINARY_FEASIBILITY
```

For this case, metric functions can map to production tests: `sandRate` computed from empirical
correlations, `separatorUtil` derived from `getCapacityDuty()/getCapacityMax()`, and
`gasliftRate` pulled from a gas-lift valve set point. The feasibility-first search will quickly
highlight whether the sand constraint or separator capacity is the binding limitation, while the
iteration history logs identify performance hotspots (e.g., separator flash calculations dominating
runtime during tight binary searches).

### Debottlenecking Studies

Once the bottleneck is identified (e.g., a compressor), you can simulate a "debottlenecking" project:
1.  Increase the capacity of the bottleneck equipment (e.g., `compressor.getMechanicalDesign().maxDesignPower = newPower`).
2.  Re-run the optimization loop.
3.  Identify the *new* bottleneck and the new maximum production rate.
4.  Calculate the ROI of the upgrade based on the increased production.

