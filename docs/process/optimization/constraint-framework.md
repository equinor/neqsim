---
title: "Unified Constraint Framework"
description: "Documentation for the ProcessConstraint interface and the unified constraint framework that bridges equipment capacity constraints, internal optimizer constraints, and external NLP solver constraints in NeqSim."
---

# Unified Constraint Framework

The NeqSim optimization module provides a unified constraint framework through the `ProcessConstraint` interface. This bridges three previously disconnected constraint layers into a single API:

1. **Equipment capacity constraints** — auto-discovered from process equipment via the strategy registry
2. **Internal optimizer constraints** — `OptimizationConstraint` in `ProductionOptimizer`
3. **External optimizer constraints** — `ConstraintDefinition` in `ProcessSimulationEvaluator`

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimizer Guide](../../util/optimizer_guide) | Main optimization module documentation |
| [Multi-Objective Optimization](multi-objective-optimization) | Pareto fronts with constraints |
| [Flow Rate Optimization](flow-rate-optimization) | FlowRateOptimizer with compressor constraints |

## Table of Contents

- [Overview](#overview)
- [ProcessConstraint Interface](#processconstraint-interface)
- [Severity Levels](#severity-levels)
- [Equipment Capacity Constraints](#equipment-capacity-constraints)
- [Internal Optimizer Constraints](#internal-optimizer-constraints)
- [External Optimizer Integration](#external-optimizer-integration)
- [ConstraintPenaltyCalculator](#constraintpenaltycalculator)
- [Constraint Conversion](#constraint-conversion)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)

---

## Overview

### The Problem

Before the unified framework, NeqSim had three separate constraint systems:

| Layer | Class | Used By |
|-------|-------|---------|
| Equipment | `CapacityConstraint` | `EquipmentCapacityStrategy` implementations |
| Internal optimizer | `OptimizationConstraint` | `ProductionOptimizer` |
| External optimizer | `ConstraintDefinition` | `ProcessSimulationEvaluator` |

Each had its own severity model, margin convention, and penalty logic. Moving constraints between optimizers required manual conversion.

### The Solution

The `ProcessConstraint` interface provides a single contract:

- **Margin convention:** `margin(process) >= 0` means satisfied, `< 0` means violated
- **Unified severity:** 4-level `ConstraintSeverityLevel` with bidirectional mappings
- **Reusable penalty:** `ConstraintPenaltyCalculator` works with any `ProcessConstraint`
- **Auto-discovery:** Equipment constraints are automatically wrapped as `ProcessConstraint`

All three original constraint classes now implement `ProcessConstraint`.

---

## ProcessConstraint Interface

```java
package neqsim.process.util.optimizer;

public interface ProcessConstraint {

    /** Constraint name, never null. */
    String getName();

    /**
     * Constraint margin. Positive or zero means satisfied; negative means violated.
     * For equipment capacity: margin = 1.0 - utilization.
     */
    double margin(ProcessSystem process);

    /** True when margin >= 0. */
    default boolean isSatisfied(ProcessSystem process) {
        return margin(process) >= 0.0;
    }

    /** Unified severity level. */
    ConstraintSeverityLevel getSeverityLevel();

    /** Penalty weight for optimization. Higher = stronger penalization. */
    double getPenaltyWeight();

    /** Penalty value: weight * margin^2 when violated, 0 when satisfied. */
    default double penalty(ProcessSystem process) {
        double m = margin(process);
        return m < 0.0 ? getPenaltyWeight() * m * m : 0.0;
    }

    /** True for CRITICAL or HARD severity. */
    default boolean isHard() {
        ConstraintSeverityLevel level = getSeverityLevel();
        return level == ConstraintSeverityLevel.CRITICAL
            || level == ConstraintSeverityLevel.HARD;
    }

    /** Human-readable description. */
    default String getDescription() {
        return "";
    }
}
```

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| `margin >= 0` convention | Matches standard NLP formulation $g(x) \geq 0$ |
| Default quadratic penalty | Smooth gradient for penalty-based solvers |
| `isHard()` from severity | Avoids redundant boolean flags |
| Process parameter in `margin()` | Allows stateless constraints evaluated on any process |

---

## Severity Levels

The `ConstraintSeverityLevel` enum provides four levels with bidirectional mappings to the equipment and optimizer severity systems:

| Level | Optimization Impact | Equipment Mapping | Optimizer Mapping |
|-------|-------------------|-------------------|-------------------|
| `CRITICAL` | Solution rejected; optimizer may abort | `CapacityConstraint.ConstraintSeverity.CRITICAL` | `ProductionOptimizer.ConstraintSeverity.HARD` |
| `HARD` | Solution marked infeasible | `CapacityConstraint.ConstraintSeverity.HARD` | `ProductionOptimizer.ConstraintSeverity.HARD` |
| `SOFT` | Penalty applied to objective function | `CapacityConstraint.ConstraintSeverity.SOFT` | `ProductionOptimizer.ConstraintSeverity.SOFT` |
| `ADVISORY` | Reporting only; no optimization impact | `CapacityConstraint.ConstraintSeverity.ADVISORY` | `ProductionOptimizer.ConstraintSeverity.SOFT` |

### Conversion Methods

```java
// Equipment -> Unified
ConstraintSeverityLevel level =
    ConstraintSeverityLevel.fromCapacitySeverity(
        CapacityConstraint.ConstraintSeverity.HARD);

// Optimizer -> Unified
ConstraintSeverityLevel level =
    ConstraintSeverityLevel.fromOptimizerSeverity(
        ProductionOptimizer.ConstraintSeverity.HARD);

// Boolean -> Unified
ConstraintSeverityLevel level =
    ConstraintSeverityLevel.fromIsHard(true);  // Returns HARD

// Unified -> Equipment
CapacityConstraint.ConstraintSeverity eqSev =
    level.toCapacitySeverity();

// Unified -> Optimizer
ProductionOptimizer.ConstraintSeverity optSev =
    level.toOptimizerSeverity();

// Unified -> Boolean
boolean isHard = level.toIsHard();  // true for CRITICAL and HARD
```

---

## Equipment Capacity Constraints

Equipment constraints are provided by `EquipmentCapacityStrategy` implementations and wrapped by `CapacityConstraintAdapter`:

```java
import neqsim.process.util.optimizer.CapacityConstraintAdapter;
import neqsim.process.equipment.capacity.CapacityConstraint;

// Wrap an equipment constraint
CapacityConstraint eqConstraint = new CapacityConstraint(
    "surge_margin",
    compressor.getSurgeMargin(), // utilization fraction
    CapacityConstraint.ConstraintSeverity.CRITICAL,
    "%",
    "Compressor surge margin"
);

CapacityConstraintAdapter adapted =
    new CapacityConstraintAdapter("K-100/surge_margin", eqConstraint);

// Now usable as ProcessConstraint
double margin = adapted.margin(process);  // 1.0 - utilization
boolean ok = adapted.isSatisfied(process);
ConstraintSeverityLevel level = adapted.getSeverityLevel();  // CRITICAL
```

### Margin Calculation

For equipment capacity constraints, the margin is defined as:

$$\text{margin} = 1.0 - \text{utilization}$$

where utilization is the ratio of current value to limit (e.g., `power / maxPower`). A utilization of 0.95 gives margin = 0.05 (satisfied); utilization of 1.10 gives margin = -0.10 (violated).

---

## Internal Optimizer Constraints

`ProductionOptimizer.OptimizationConstraint` implements `ProcessConstraint` and provides static factory methods:

```java
import neqsim.process.util.optimizer.ProductionOptimizer.*;

// Less-than constraint: value <= limit
OptimizationConstraint powerLimit = OptimizationConstraint.lessThan(
    "MaxPower",
    (process, rate) -> getTotalPower(process),
    5000.0,
    ConstraintSeverity.HARD,
    100.0  // penalty weight
);

// Greater-than constraint: value >= limit
OptimizationConstraint minExport = OptimizationConstraint.greaterThan(
    "MinExport",
    (process, rate) -> getGasExport(process),
    10.0e6,
    ConstraintSeverity.SOFT,
    50.0
);

// These are ProcessConstraint — can be used anywhere
double margin = powerLimit.margin(process);
ConstraintSeverityLevel level = powerLimit.getSeverityLevel();
```

---

## External Optimizer Integration

`ProcessSimulationEvaluator.ConstraintDefinition` implements `ProcessConstraint` and supports these types:

| Type | Convention | Example |
|------|-----------|---------|
| `LOWER_BOUND` | value >= bound | Minimum flow rate |
| `UPPER_BOUND` | value <= bound | Maximum pressure |
| `RANGE` | lower <= value <= upper | Temperature range |
| `EQUALITY` | value == target (within tolerance) | Pressure setpoint |

### Auto-Discovery from Equipment

```java
import neqsim.process.util.optimizer.ProcessSimulationEvaluator;

ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);

// Add decision variables
evaluator.addParameter("feedRate", feed, "flowRate",
    1000.0, 20000.0, "kg/hr");

// Auto-discover all equipment capacity constraints
evaluator.addEquipmentCapacityConstraints();

// Get as unified ProcessConstraint list
List<ProcessConstraint> allConstraints =
    evaluator.getAllProcessConstraints();

// Get NLP margin vector: g(x) >= 0
double[] margins = evaluator.getConstraintMarginVector(process);
```

### Evaluate at a Point

```java
double[] x = {10000.0};  // feed rate
ProcessSimulationEvaluator.EvaluationResult result =
    evaluator.evaluate(x);

// Check feasibility
boolean feasible = result.isFeasible();

// Get constraint values
double[] constraintValues = result.getConstraintValues();
double[] constraintMargins = result.getConstraintMargins();
```

---

## ConstraintPenaltyCalculator

The `ConstraintPenaltyCalculator` is a reusable penalty engine that works with any `ProcessConstraint`. It provides:

- Automatic equipment constraint discovery
- NLP margin vectors for gradient-based solvers
- Adaptive penalty scaling proportional to the objective magnitude
- Detailed per-constraint evaluation reports

### Basic Usage

```java
import neqsim.process.util.optimizer.ConstraintPenaltyCalculator;

ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

// Auto-discover equipment constraints
calc.addEquipmentCapacityConstraints(process);

// Add custom constraints (any ProcessConstraint)
calc.addConstraint(powerLimit);
calc.addConstraint(minExport);

System.out.println("Total constraints: " + calc.getConstraintCount());
```

### Feasibility Check

```java
boolean feasible = calc.isFeasible(process);
// Returns true only if no CRITICAL or HARD constraint is violated
```

### NLP Margin Vector

For gradient-based NLP solvers, get the constraint vector in standard form:

```java
double[] margins = calc.evaluateMargins(process);
// margins[i] >= 0 means constraint i is satisfied
// margins[i] < 0 means constraint i is violated by |margins[i]|
```

### Adaptive Penalty

The `penalize()` method applies adaptive penalty scaling:

```java
double rawObjective = computeObjective(process);
double penalized = calc.penalize(rawObjective, process);
// If feasible: penalized == rawObjective
// If violated: penalized += scale * (hard: linear penalty, soft: quadratic penalty)
// Scale adapts to |rawObjective| for balanced optimization
```

### Detailed Report

```java
List<ConstraintPenaltyCalculator.ConstraintEvaluation> report =
    calc.evaluate(process);

for (ConstraintPenaltyCalculator.ConstraintEvaluation eval : report) {
    System.out.printf("%-30s severity=%-8s margin=%+.4f satisfied=%b penalty=%.1f%n",
        eval.getName(),
        eval.getSeverity(),
        eval.getMargin(),
        eval.isSatisfied(),
        eval.getPenalty());
}
```

Example output:

```
K-100/power                    severity=HARD     margin=+0.0500 satisfied=true  penalty=0.0
K-100/surge_margin             severity=CRITICAL margin=-0.0200 satisfied=false penalty=4.0
V-100/liquid_level             severity=SOFT     margin=+0.1500 satisfied=true  penalty=0.0
```

---

## Constraint Conversion

Constraints can be converted bidirectionally between the internal and external optimizer representations:

### Internal to External

```java
ProductionOptimizer.OptimizationConstraint internal =
    OptimizationConstraint.lessThan("MaxPower", evaluator, 5000.0,
        ConstraintSeverity.HARD, 100.0);

// Convert to ConstraintDefinition for ProcessSimulationEvaluator
ProcessSimulationEvaluator.ConstraintDefinition external =
    internal.toConstraintDefinition();
```

### External to Internal

```java
ProcessSimulationEvaluator.ConstraintDefinition external = ...;

// Convert to OptimizationConstraint for ProductionOptimizer
ProductionOptimizer.OptimizationConstraint internal =
    external.toOptimizationConstraint();
```

### Severity Mapping in Conversions

When converting between layers, severity is mapped via `ConstraintSeverityLevel`:

- `CRITICAL` and `HARD` remain as `HARD` in the optimizer layer (which has only 2 levels)
- `SOFT` and `ADVISORY` remain as `SOFT` in the optimizer layer
- Equipment severity maps 1:1 with the unified 4-level enum

---

## Usage Examples

### Example 1: Throughput Optimization with Equipment Constraints

```java
ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .rateUnit("kg/hr")
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .defaultUtilizationLimit(0.95);

// Equipment constraints are automatically discovered and enforced
OptimizationResult result = optimizer.optimize(process, feed, config);
```

### Example 2: Standalone Constraint Checking

```java
// Check constraints independently of optimization
ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator()
    .addEquipmentCapacityConstraints(process);

process.run();
boolean feasible = calc.isFeasible(process);

if (!feasible) {
    for (ConstraintPenaltyCalculator.ConstraintEvaluation e : calc.evaluate(process)) {
        if (!e.isSatisfied()) {
            System.out.println("VIOLATED: " + e.getName()
                + " (margin=" + e.getMargin() + ")");
        }
    }
}
```

### Example 3: External NLP Solver Integration

```java
// Set up for SciPy or IPOPT
ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);
evaluator.addParameter("feedRate", feed, "flowRate", 1000.0, 20000.0, "kg/hr");
evaluator.addObjective("throughput",
    ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE,
    proc -> proc.getMeasuredValue("feed", "flowRate", "kg/hr"));
evaluator.addEquipmentCapacityConstraints();

// The external solver calls evaluate(x) and reads margins
for (double rate = 1000; rate <= 20000; rate += 1000) {
    ProcessSimulationEvaluator.EvaluationResult result =
        evaluator.evaluate(new double[]{rate});
    System.out.printf("Rate=%.0f feasible=%b%n",
        rate, result.isFeasible());
}
```

### Example 4: Mixed Constraint Sources

```java
ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

// Equipment constraints (auto-discovered)
calc.addEquipmentCapacityConstraints(process);

// Custom internal optimizer constraint
calc.addConstraint(
    OptimizationConstraint.lessThan("MaxPower",
        (proc, rate) -> getTotalPower(proc),
        5000.0, ConstraintSeverity.HARD, 100.0));

// All constraints are treated uniformly
double[] margins = calc.evaluateMargins(process);
boolean feasible = calc.isFeasible(process);
double total = calc.totalPenalty(process);
```

---

## API Reference

### ProcessConstraint

| Method | Returns | Description |
|--------|---------|-------------|
| `getName()` | `String` | Constraint name |
| `margin(ProcessSystem)` | `double` | >= 0 satisfied, < 0 violated |
| `isSatisfied(ProcessSystem)` | `boolean` | margin >= 0 |
| `getSeverityLevel()` | `ConstraintSeverityLevel` | Unified severity |
| `getPenaltyWeight()` | `double` | Penalty multiplier |
| `penalty(ProcessSystem)` | `double` | weight * margin^2 when violated |
| `isHard()` | `boolean` | True for CRITICAL/HARD |
| `getDescription()` | `String` | Human-readable description |

### ConstraintSeverityLevel

| Constant | `toIsHard()` | `toOptimizerSeverity()` | `toCapacitySeverity()` |
|----------|-------------|------------------------|----------------------|
| `CRITICAL` | `true` | `HARD` | `CRITICAL` |
| `HARD` | `true` | `HARD` | `HARD` |
| `SOFT` | `false` | `SOFT` | `SOFT` |
| `ADVISORY` | `false` | `SOFT` | `ADVISORY` |

### CapacityConstraintAdapter

| Method | Returns | Description |
|--------|---------|-------------|
| Constructor | - | `(String qualifiedName, CapacityConstraint delegate)` |
| `margin(ProcessSystem)` | `double` | 1.0 - utilization |
| `getDelegate()` | `CapacityConstraint` | Underlying equipment constraint |
| `getUtilization()` | `double` | Current utilization fraction |
| `getUnit()` | `String` | Constraint unit |

### ConstraintPenaltyCalculator

| Method | Returns | Description |
|--------|---------|-------------|
| `addConstraint(ProcessConstraint)` | `this` | Add single constraint (chainable) |
| `addConstraints(List)` | `this` | Add multiple constraints (chainable) |
| `addEquipmentCapacityConstraints(ProcessSystem)` | `this` | Auto-discover from process (chainable) |
| `getConstraints()` | `List` | Unmodifiable constraint list |
| `getConstraintCount()` | `int` | Number of constraints |
| `evaluateMargins(ProcessSystem)` | `double[]` | NLP margin vector |
| `isFeasible(ProcessSystem)` | `boolean` | No hard violations |
| `totalPenalty(ProcessSystem)` | `double` | Sum of all penalties |
| `penalize(double, ProcessSystem)` | `double` | Adaptive penalized objective |
| `evaluate(ProcessSystem)` | `List` | Per-constraint evaluation report |
| `clear()` | `void` | Remove all constraints |
