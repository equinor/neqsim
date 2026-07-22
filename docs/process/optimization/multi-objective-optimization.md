---
title: Multi-Objective Optimization for Process Systems
description: The `neqsim.process.util.optimizer` package provides a comprehensive **multi-objective optimization** framework for finding Pareto-optimal solutions when optimizing competing objectives in process sim...
---

# Multi-Objective Optimization for Process Systems

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

The `neqsim.process.util.optimizer` package provides a comprehensive **multi-objective optimization** framework for finding Pareto-optimal solutions when optimizing competing objectives in process simulations.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | When to use which optimizer |
| [Constraint Framework](constraint-framework) | Unified constraint system for all optimizers |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | ProductionOptimizer examples |
| [Batch Studies](batch-studies) | Parallel parameter sweeps |

## Table of Contents

- [Overview](#overview)
- [Key Concepts](#key-concepts)
  - [What is Multi-Objective Optimization?](#what-is-multi-objective-optimization)
  - [Pareto Dominance](#pareto-dominance)
  - [Pareto Front](#pareto-front)
  - [Knee Point](#knee-point)
- [Architecture](#architecture)
- [Standard Objectives](#standard-objectives)
- [Optimization Methods](#optimization-methods)
  - [Weighted Sum Method](#weighted-sum-method)
  - [Epsilon-Constraint Method](#epsilon-constraint-method)
  - [Sampling Method](#sampling-method)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Best Practices](#best-practices)
- [Python Usage (via JPype)](#python-usage-via-jpype)
  - [Basic Setup](#basic-setup)
  - [Using Standard Objectives](#using-standard-objectives)
  - [Configuring Restrictions in Python](#configuring-restrictions-in-python)
  - [Including Infeasible Solutions for Analysis](#including-infeasible-solutions-for-analysis)
  - [Custom Objectives in Python](#custom-objectives-in-python)
  - [Progress Monitoring](#progress-monitoring)
  - [Extracting Results for Pandas/NumPy](#extracting-results-for-pandasnumpy)
  - [Plotting Pareto Front (matplotlib)](#plotting-pareto-front-matplotlib)
  - [Integration with SciPy](#integration-with-scipy-for-custom-algorithms)

---

## Overview

Multi-objective optimization addresses real-world engineering problems where multiple, often conflicting, objectives must be optimized simultaneously. For example:

| Objective 1 | Objective 2 | Trade-off |
|-------------|-------------|-----------|
| Maximize throughput | Minimize power consumption | Higher throughput requires more power |
| Maximize production | Minimize emissions | Higher production may increase emissions |
| Minimize cost | Maximize reliability | Higher reliability typically costs more |

Instead of finding a single optimal solution, multi-objective optimization finds a **set of Pareto-optimal solutions** that represent the best trade-offs between objectives.

### Key Features

| Feature | Description |
|---------|-------------|
| **Pareto Front Generation** | Find non-dominated solutions across multiple objectives |
| **Multiple Methods** | Weighted-sum, epsilon-constraint, and sampling approaches |
| **Standard Objectives** | Pre-built objectives for throughput, power, heating/cooling duty |
| **Custom Objectives** | Define any objective using lambda functions |
| **Knee Point Detection** | Automatically find the best trade-off solution |
| **JSON Export** | Export results for visualization and analysis |
| **Progress Callbacks** | Monitor optimization progress in real-time |

---

## Key Concepts

### What is Multi-Objective Optimization?

Multi-objective optimization (MOO) solves problems of the form:

$$\min_{\vec{x}} \vec{f}(\vec{x}) = [f_1(\vec{x}), f_2(\vec{x}), \ldots, f_k(\vec{x})]$$

subject to constraints $g_i(\vec{x}) \leq 0$ and bounds $\vec{x}_{lb} \leq \vec{x} \leq \vec{x}_{ub}$

where:
- $\vec{x}$ = decision variables (e.g., feed flow rate)
- $\vec{f}$ = vector of objective functions
- $k$ = number of objectives

### Pareto Dominance

Solution **A dominates B** (written A ≻ B) if and only if:
1. A is **at least as good as** B on all objectives
2. A is **strictly better than** B on at least one objective

```
Example with 2 objectives (maximize throughput, minimize power):

Solution A: (10000 kg/hr, 250 kW)
Solution B: (9000 kg/hr, 280 kW)
Solution C: (11000 kg/hr, 320 kW)

A dominates B because:
- A has higher throughput (10000 > 9000) ✓
- A has lower power (250 < 280) ✓

A does NOT dominate C because:
- C has higher throughput (11000 > 10000)
- A has lower power (250 < 320)
→ Neither is better on all objectives
```

### Pareto Front

The **Pareto front** (or Pareto frontier) is the set of all non-dominated solutions. No solution in this set can be improved in one objective without degrading another.

```
        Power (kW)
           ▲
       500 │                    
       400 │          ★ C      (Not on front - dominated by B)
       300 │    ● A ──● B      (Pareto front)
       200 │  ●─────────●      
       100 │●                  
           └─────────────────► Throughput (kg/hr)
             5k   10k   15k   20k
             
● = Pareto-optimal solutions
★ = Dominated solution (not on front)
```

### Knee Point

The **knee point** is the solution on the Pareto front that represents the "best compromise" between objectives. It's found by maximizing the distance from the line connecting the extreme points (utopia line).

```
        Power (kW)
           ▲
       400 │         
       300 │    ●────●        Utopia line
       200 │  ●──★───●        ★ = Knee point (maximum distance)
       100 │●────────●        
           └─────────────────► Throughput (kg/hr)
```

The knee point is often the most desirable operating point because it provides significant improvement in all objectives without extreme trade-offs.

---

## Architecture

The multi-objective optimization framework consists of four main classes:

```
┌─────────────────────────────────────────────────────────────────┐
│                    MultiObjectiveOptimizer                       │
│  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────┐   │
│  │ optimizeWeight- │  │ optimizeEpsilon- │  │ samplePareto- │   │
│  │    edSum()      │  │   Constraint()   │  │    Front()    │   │
│  └────────┬────────┘  └────────┬─────────┘  └───────┬───────┘   │
│           │                    │                    │            │
│           ▼                    ▼                    ▼            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              ProductionOptimizer (single-objective)         ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        ParetoFront                               │
│  - add(solution)        - findKneePoint()                        │
│  - calculateSpacing()   - toJson()                               │
│  - getSolutionsSortedBy(objective, descending)                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       ParetoSolution                             │
│  - getRawValue(index)   - dominates(other)                       │
│  - isFeasible()         - getObjectiveName(index)                │
│  - getDecisionVariables()                                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Standard Objectives

The `StandardObjective` enum provides pre-built objectives for common optimization goals:

| Objective | Direction | Description | Unit |
|-----------|-----------|-------------|------|
| `MAXIMIZE_THROUGHPUT` | Maximize | Total feed stream flow rate | kg/hr |
| `MINIMIZE_POWER` | Minimize | Sum of compressor + pump power | kW |
| `MINIMIZE_HEATING_DUTY` | Minimize | Total heater duty | kW |
| `MINIMIZE_COOLING_DUTY` | Minimize | Total cooler duty | kW |
| `MINIMIZE_TOTAL_ENERGY` | Minimize | Power + heating + cooling | kW |
| `MAXIMIZE_SPECIFIC_PRODUCTION` | Maximize | Throughput per unit power | kg/kWh |
| `MAXIMIZE_LIQUID_RECOVERY` | Maximize | Liquid recovery fraction | - |

### Using Standard Objectives

```java
// Use directly
List<ObjectiveFunction> objectives = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER
);

// Create custom objective
ObjectiveFunction specificProduction = ObjectiveFunction.create(
    "Specific Production",
    proc -> {
        double throughput = StandardObjective.MAXIMIZE_THROUGHPUT.evaluate(proc);
        double power = StandardObjective.MINIMIZE_POWER.evaluate(proc);
        return power > 1.0 ? throughput / power : throughput;
    },
    ObjectiveFunction.Direction.MAXIMIZE,
    "kg/kWh"
);
```

---

## Optimization Methods

### Weighted Sum Method

Combines multiple objectives into a single weighted sum and solves using the underlying single-objective optimizer.

**Mathematical Formulation:**

$$\min_{\vec{x}} \sum_{i=1}^{k} w_i \cdot f_i(\vec{x})$$

where $\sum w_i = 1$ and $w_i \geq 0$

**Characteristics:**
- ✅ Simple and fast
- ✅ Works well for convex Pareto fronts
- ❌ Cannot find solutions on non-convex regions
- ❌ With linear objectives, may converge to same point for all weights

**When to Use:**
- Quick exploration of trade-offs
- Convex optimization problems
- When extreme points are most important

```java
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.optimizeWeightedSum(
    process,           // ProcessSystem
    feedStream,        // Stream to manipulate
    objectives,        // List<ObjectiveFunction>
    config,            // OptimizationConfig
    10                 // Number of weight combinations
);
```

### Epsilon-Constraint Method

Optimizes the primary objective while constraining other objectives to varying upper bounds (epsilons).

**Mathematical Formulation:**

$$\min_{\vec{x}} f_1(\vec{x})$$

subject to: $f_i(\vec{x}) \leq \epsilon_i$ for $i = 2, \ldots, k$

**Characteristics:**
- ✅ Can find solutions on non-convex fronts
- ✅ Provides more evenly distributed solutions
- ❌ More computationally expensive
- ❌ Requires careful selection of epsilon values

**When to Use:**
- Non-convex Pareto fronts
- Need well-distributed solutions
- Primary objective is clearly defined

```java
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.optimizeEpsilonConstraint(
    process,              // ProcessSystem
    feedStream,           // Stream to manipulate  
    primaryObjective,     // ObjectiveFunction to optimize
    constrainedObjectives,// List<ObjectiveFunction> to constrain
    config,               // OptimizationConfig
    8                     // Number of epsilon levels
);
```

### Sampling Method

Directly evaluates the process at fixed decision variable values to generate the Pareto front. **Best for linearly-related objectives.**

**Characteristics:**
- ✅ Guarantees diverse solutions across the decision space
- ✅ Works well when objectives are linearly related
- ✅ Simple and predictable
- ❌ May miss optimal points between samples
- ❌ Computational cost scales with number of samples

**When to Use:**
- Objectives are linearly proportional (e.g., power ∝ flow)
- Need guaranteed coverage of the decision space
- Weighted-sum converges to single point

```java
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.sampleParetoFront(
    process,        // ProcessSystem
    feedStream,     // Stream to manipulate
    objectives,     // List<ObjectiveFunction>
    config,         // OptimizationConfig (defines flow range)
    10              // Number of sample points
);
```

---

## Usage Examples

### Example 1: Basic Throughput vs Power Optimization

This example demonstrates finding the Pareto front for maximizing throughput while minimizing power consumption in a gas compression system.

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.*;
import neqsim.thermo.system.SystemSrkEos;
import java.util.Arrays;
import java.util.List;

// Step 1: Create the process
SystemSrkEos fluid = new SystemSrkEos(298.15, 30.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-butane", 0.02);
fluid.addComponent("CO2", 0.01);
fluid.setMixingRule("classic");

ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(30.0, "bara");
process.add(feed);

// Separator
Separator separator = new Separator("HP Separator", feed);
separator.initMechanicalDesign();
separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(50000.0);
process.add(separator);

// Compressor with capacity limit
Compressor compressor = new Compressor("Gas Compressor", separator.getGasOutStream());
compressor.setOutletPressure(50.0, "bara");
compressor.setIsentropicEfficiency(0.75);
compressor.getMechanicalDesign().setMaxDesignPower(500_000.0); // 500 kW in Watts
process.add(compressor);

// Cooler
Cooler cooler = new Cooler("After Cooler", compressor.getOutletStream());
cooler.setOutTemperature(40.0, "C");
process.add(cooler);

// Step 2: Define objectives
List<ObjectiveFunction> objectives = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER
);

// Step 3: Configure optimization
ProductionOptimizer.OptimizationConfig config = 
    new ProductionOptimizer.OptimizationConfig(1000.0, 20000.0)  // Flow range: 1000-20000 kg/hr
        .rateUnit("kg/hr")
        .tolerance(50.0)
        .defaultUtilizationLimit(0.95)
        .maxIterations(20);

// Step 4: Run sampling-based optimization
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer()
    .onProgress((iteration, total, solution) -> {
        if (solution != null) {
            System.out.printf("Sample %d/%d: Flow=%.0f kg/hr, Power=%.1f kW%n",
                iteration, total, solution.getRawValue(0), solution.getRawValue(1));
        }
    });

ParetoFront front = moo.sampleParetoFront(process, feed, objectives, config, 10);

// Step 5: Analyze results
System.out.println("\n=== Pareto Front Results ===");
System.out.println("Number of solutions: " + front.size());

// Print all solutions
for (ParetoSolution sol : front.getSolutionsSortedBy(0, true)) {
    System.out.printf("  Throughput: %.0f kg/hr, Power: %.1f kW%n",
        sol.getRawValue(0), sol.getRawValue(1));
}

// Find knee point (best trade-off)
ParetoSolution knee = front.findKneePoint();
System.out.printf("\nKnee Point (Best Trade-off):%n");
System.out.printf("  Throughput: %.0f kg/hr%n", knee.getRawValue(0));
System.out.printf("  Power: %.1f kW%n", knee.getRawValue(1));

// Export to JSON for visualization
String json = front.toJson();
System.out.println("\nJSON Export:\n" + json);
```

**Expected Output:**

```
Sample 1/10: Flow=1000 kg/hr, Power=23.6 kW
Sample 2/10: Flow=3111 kg/hr, Power=73.4 kW
Sample 3/10: Flow=5222 kg/hr, Power=123.2 kW
Sample 4/10: Flow=7333 kg/hr, Power=173.1 kW
Sample 5/10: Flow=9444 kg/hr, Power=222.9 kW
Sample 6/10: Flow=11556 kg/hr, Power=272.7 kW
Sample 7/10: Flow=13667 kg/hr, Power=322.5 kW
Sample 8/10: Flow=15778 kg/hr, Power=372.3 kW
Sample 9/10: Flow=17889 kg/hr, Power=422.2 kW
Sample 10/10: Flow=20000 kg/hr, Power=472.0 kW

=== Pareto Front Results ===
Number of solutions: 10
  Throughput: 1000 kg/hr, Power: 23.6 kW
  Throughput: 3111 kg/hr, Power: 73.4 kW
  Throughput: 5222 kg/hr, Power: 123.2 kW
  Throughput: 7333 kg/hr, Power: 173.1 kW
  Throughput: 9444 kg/hr, Power: 222.9 kW
  Throughput: 11556 kg/hr, Power: 272.7 kW
  Throughput: 13667 kg/hr, Power: 322.5 kW
  Throughput: 15778 kg/hr, Power: 372.3 kW
  Throughput: 17889 kg/hr, Power: 422.2 kW
  Throughput: 20000 kg/hr, Power: 472.0 kW

Knee Point (Best Trade-off):
  Throughput: 11556 kg/hr
  Power: 272.7 kW
```

### Example 2: Optimization with Explicit Constraints

Add explicit constraints (beyond equipment capacity limits):

```java
import neqsim.process.util.optimizer.ProductionOptimizer.*;

// Define a power constraint
OptimizationConstraint powerConstraint = OptimizationConstraint.lessThan(
    "Max Compressor Power",
    proc -> {
        Compressor comp = (Compressor) proc.getUnit("Gas Compressor");
        return comp != null ? comp.getPower("kW") : 0.0;
    },
    300.0,                    // Power limit: 300 kW
    ConstraintSeverity.HARD,  // Must be satisfied
    0.0,                      // No penalty weight (hard constraint)
    "Keep power below 300 kW for driver limitation"
);

// Run optimization with constraint
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.optimizeWeightedSum(
    process, feed, objectives, config, 10,
    Collections.singletonList(powerConstraint)  // Add constraint
);

// All feasible solutions will have power <= 300 kW
for (ParetoSolution sol : front) {
    if (sol.isFeasible()) {
        assert sol.getRawValue(1) <= 300.0 : "Power constraint violated";
    }
}
```

### Example 3: Three-Objective Optimization

Optimize throughput, power, AND specific production:

```java
// Custom objective: specific production (throughput per unit power)
ObjectiveFunction specificProduction = ObjectiveFunction.create(
    "Specific Production",
    proc -> {
        double throughput = StandardObjective.MAXIMIZE_THROUGHPUT.evaluate(proc);
        double power = StandardObjective.MINIMIZE_POWER.evaluate(proc);
        return power > 1.0 ? throughput / power : throughput;
    },
    ObjectiveFunction.Direction.MAXIMIZE,
    "kg/kWh"
);

// Three objectives
List<ObjectiveFunction> objectives = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER,
    specificProduction
);

// Run optimization
MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer();
ParetoFront front = moo.optimizeWeightedSum(process, feed, objectives, config, 15);

// Print results with 3 objectives
for (ParetoSolution sol : front) {
    System.out.printf("Flow: %.0f kg/hr, Power: %.1f kW, Specific: %.1f kg/kWh%n",
        sol.getRawValue(0), sol.getRawValue(1), sol.getRawValue(2));
}
```

### Example 4: Progress Monitoring and Callbacks

Track optimization progress in real-time:

```java
final int[] feasibleCount = {0};
final int[] infeasibleCount = {0};

MultiObjectiveOptimizer moo = new MultiObjectiveOptimizer()
    .includeInfeasible(true)  // Include infeasible solutions for analysis
    .onProgress((iteration, total, solution) -> {
        if (solution != null) {
            if (solution.isFeasible()) {
                feasibleCount[0]++;
            } else {
                infeasibleCount[0]++;
            }
            System.out.printf("  [%d/%d] Flow=%.0f kg/hr, Power=%.1f kW, Feasible=%s%n",
                iteration, total,
                solution.getRawValue(0),
                solution.getRawValue(1),
                solution.isFeasible());
        } else {
            System.out.printf("  [%d/%d] FAILED - Process did not converge%n",
                iteration, total);
        }
    });

ParetoFront front = moo.sampleParetoFront(process, feed, objectives, config, 20);

System.out.printf("%nSummary: %d feasible, %d infeasible solutions%n",
    feasibleCount[0], infeasibleCount[0]);
```

---

## API Reference

### MultiObjectiveOptimizer

The main optimizer class.

| Method | Description |
|--------|-------------|
| `includeInfeasible(boolean)` | Whether to include infeasible solutions in results |
| `onProgress(callback)` | Set progress callback for monitoring |
| `optimizeWeightedSum(...)` | Find Pareto front using weighted sum method |
| `optimizeEpsilonConstraint(...)` | Find Pareto front using epsilon-constraint method |
| `sampleParetoFront(...)` | Generate Pareto front by sampling at fixed flow rates |

### ParetoFront

Collection of non-dominated solutions.

| Method | Description |
|--------|-------------|
| `size()` | Number of solutions in front |
| `isEmpty()` | Check if front is empty |
| `add(solution)` | Add solution (automatically filters dominated) |
| `getSolutions()` | Get all solutions |
| `getSolutionsSortedBy(index, descending)` | Sort by objective |
| `findKneePoint()` | Find best trade-off solution |
| `findMaximum(index)` | Find max for objective |
| `findMinimum(index)` | Find min for objective |
| `calculateSpacing()` | Calculate distribution metric |
| `toJson()` | Export to JSON |

### ParetoSolution

Single Pareto-optimal solution.

| Method | Description |
|--------|-------------|
| `getNumObjectives()` | Number of objectives |
| `getRawValue(index)` | Get objective value by index |
| `getObjectiveName(index)` | Get objective name by index |
| `isFeasible()` | Check if solution satisfies all constraints |
| `dominates(other)` | Check if this solution dominates another |
| `getDecisionVariables()` | Get decision variable values |

### ObjectiveFunction

Interface for optimization objectives.

| Method | Description |
|--------|-------------|
| `getName()` | Objective name |
| `getDirection()` | MAXIMIZE or MINIMIZE |
| `evaluate(process)` | Calculate objective value |
| `getUnit()` | Unit of measurement |
| `create(name, evaluator, direction, unit)` | Static factory method |

---

## Best Practices

### 1. Choose the Right Method

| Scenario | Recommended Method |
|----------|-------------------|
| Linear objectives (power ∝ flow) | `sampleParetoFront()` |
| Convex Pareto front | `optimizeWeightedSum()` |
| Non-convex or well-distributed | `optimizeEpsilonConstraint()` |
| Quick exploration | `optimizeWeightedSum()` with few weights |
| Production decision support | `sampleParetoFront()` for predictable coverage |

### 2. Set Appropriate Bounds

```java
// Bounds should reflect realistic operating range
OptimizationConfig config = new OptimizationConfig(
    1000.0,   // Lower bound: minimum stable operation
    20000.0   // Upper bound: equipment design limit
).rateUnit("kg/hr");
```

### 3. Use Equipment Capacity Limits

```java
// Set mechanical design limits (in Watts for power)
compressor.getMechanicalDesign().setMaxDesignPower(500_000.0);  // 500 kW
separator.getMechanicalDesign().setMaxDesignGassVolumeFlow(50000.0);  // Sm3/hr
```

### 4. Handle Units Correctly

```java
// Power methods:
// - getPower() returns WATTS
// - getPower("kW") returns kilowatts
// - setMaxDesignPower() expects WATTS

// Correct:
compressor.getMechanicalDesign().setMaxDesignPower(500_000.0);  // 500 kW

// Incorrect:
compressor.getMechanicalDesign().setMaxDesignPower(500.0);  // Only 0.5 kW!
```

### 5. Interpret the Knee Point

The knee point represents the best trade-off, but consider:
- It may not be optimal for your specific priorities
- Use it as a starting point for decision-making
- Compare with extreme solutions to understand trade-offs

```java
ParetoSolution knee = front.findKneePoint();
ParetoSolution maxThroughput = front.findMaximum(0);
ParetoSolution minPower = front.findMinimum(1);

System.out.println("Decision options:");
System.out.println("  Max throughput: " + maxThroughput.getRawValue(0) + " kg/hr");
System.out.println("  Min power: " + minPower.getRawValue(1) + " kW");
System.out.println("  Best trade-off: " + knee.getRawValue(0) + " kg/hr at " 
    + knee.getRawValue(1) + " kW");
```

---

## Python Usage (via JPype)

All multi-objective optimization features are accessible from Python using neqsim-python.

### Basic Setup

```python
from neqsim.neqsimpython import jneqsim
import jpype
from jpype import JImplements, JOverride
import numpy as np

# Import optimizer classes
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Separator = jneqsim.process.equipment.separator.Separator
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

MultiObjectiveOptimizer = jneqsim.process.util.optimizer.MultiObjectiveOptimizer
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
OptimizationConfig = ProductionOptimizer.OptimizationConfig
StandardObjective = jneqsim.process.util.optimizer.StandardObjective
ObjectiveFunction = jneqsim.process.util.optimizer.ObjectiveFunction

# Java collections
Arrays = jpype.JClass("java.util.Arrays")
```

### Creating Process Model

```python
# Create fluid
fluid = SystemSrkEos(298.15, 30.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.08)
fluid.addComponent("propane", 0.05)
fluid.addComponent("n-butane", 0.02)
fluid.setMixingRule("classic")

# Build process
process = ProcessSystem()

feed = Stream("Feed", fluid)
feed.setFlowRate(5000.0, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(30.0, "bara")
process.add(feed)

separator = Separator("HP Separator", feed)
process.add(separator)

compressor = Compressor("Gas Compressor", separator.getGasOutStream())
compressor.setOutletPressure(50.0, "bara")
compressor.setIsentropicEfficiency(0.75)
process.add(compressor)

cooler = Cooler("After Cooler", compressor.getOutletStream())
cooler.setOutTemperature(40.0, "C")
process.add(cooler)

process.run()
```

### Using Standard Objectives

```python
# Use pre-defined standard objectives
objectives = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER
)

# Configure optimization bounds
config = OptimizationConfig(1000.0, 20000.0) \
    .rateUnit("kg/hr") \
    .tolerance(50.0) \
    .defaultUtilizationLimit(0.95) \
    .maxIterations(20)
```

### Configuring Restrictions in Python

Control how constraints and restrictions affect Pareto front generation:

```python
# Import constraint classes
OptimizationConstraint = ProductionOptimizer.OptimizationConstraint
ConstraintSeverity = ProductionOptimizer.ConstraintSeverity
Compressor = jneqsim.process.equipment.compressor.Compressor

# Relaxed config for exploring full trade-off space
config_explore = OptimizationConfig(1000.0, 30000.0) \
    .rateUnit("kg/hr") \
    .rejectInvalidSimulations(False) \
    .defaultUtilizationLimit(1.5)  # Allow temporary overload

# Strict config for feasible Pareto points only
config_strict = OptimizationConfig(1000.0, 20000.0) \
    .rateUnit("kg/hr") \
    .rejectInvalidSimulations(True) \
    .defaultUtilizationLimit(0.95) \
    .utilizationLimitForType(Compressor, 0.90)

# With explicit constraints
@JImplements("java.util.function.ToDoubleFunction")
class PowerEvaluator:
    @JOverride
    def applyAsDouble(self, proc):
        comp = proc.getUnit("Gas Compressor")
        return comp.getPower("kW") if comp else 0.0

power_constraint = OptimizationConstraint.lessThan(
    "Max Power",
    PowerEvaluator(),
    300.0,                          # 300 kW limit
    ConstraintSeverity.HARD,
    0.0,
    "Driver power limit"
)

# Pass constraints to optimization
from java.util import Collections
front = moo.optimizeWeightedSum(
    process, feed, objectives, config_strict, 10,
    Collections.singletonList(power_constraint)
)

# Check which solutions are feasible
for sol in front.getSolutions():
    status = "✓ Feasible" if sol.isFeasible() else "⚠️ Infeasible"
    print(f"  {sol.getRawValue(0):.0f} kg/hr, {sol.getRawValue(1):.1f} kW - {status}")
```

### Including Infeasible Solutions for Analysis

```python
# Include infeasible points to understand constraint boundaries
moo = MultiObjectiveOptimizer() \
    .includeInfeasible(True)

front = moo.sampleParetoFront(process, feed, objectives, config_strict, 20)

# Separate feasible and infeasible solutions
feasible = [s for s in front.getSolutions() if s.isFeasible()]
infeasible = [s for s in front.getSolutions() if not s.isFeasible()]

print(f"Feasible solutions: {len(feasible)}")
print(f"Infeasible solutions: {len(infeasible)}")

# Plot both for visualization
import matplotlib.pyplot as plt

fig, ax = plt.subplots()
if feasible:
    ax.scatter([s.getRawValue(0) for s in feasible],
               [s.getRawValue(1) for s in feasible],
               c='green', label='Feasible', s=100)
if infeasible:
    ax.scatter([s.getRawValue(0) for s in infeasible],
               [s.getRawValue(1) for s in infeasible],
               c='red', marker='x', label='Infeasible', s=80)
ax.legend()
ax.set_xlabel('Throughput (kg/hr)')
ax.set_ylabel('Power (kW)')
plt.show()
```

### Sampling-Based Pareto Front

```python
# Create optimizer
moo = MultiObjectiveOptimizer()

# Generate Pareto front by sampling
front = moo.sampleParetoFront(process, feed, objectives, config, 10)

# Analyze results
print(f"\n=== Pareto Front Results ===")
print(f"Number of solutions: {front.size()}")

# Iterate through solutions (sorted by throughput, descending)
for sol in front.getSolutionsSortedBy(0, True):  # index=0 is throughput
    throughput = sol.getRawValue(0)
    power = sol.getRawValue(1)
    print(f"  Throughput: {throughput:.0f} kg/hr, Power: {power:.1f} kW")

# Find knee point (best trade-off)
knee = front.findKneePoint()
print(f"\nKnee Point (Best Trade-off):")
print(f"  Throughput: {knee.getRawValue(0):.0f} kg/hr")
print(f"  Power: {knee.getRawValue(1):.1f} kW")
```

### Weighted-Sum Method

```python
# Weighted-sum optimization (good for convex Pareto fronts)
front = moo.optimizeWeightedSum(
    process,     # ProcessSystem
    feed,        # Stream to vary
    objectives,  # List of ObjectiveFunction
    config,      # OptimizationConfig
    10           # Number of weight combinations
)

print(f"Found {front.size()} Pareto-optimal solutions")
```

### Custom Objectives in Python

```python
# Define custom objective using Java interface implementation
@JImplements("java.util.function.ToDoubleFunction")
class SpecificProductionObjective:
    """Throughput per unit power (kg/kWh)"""
    @JOverride
    def applyAsDouble(self, proc):
        # Get throughput (first feed stream)
        throughput = 0.0
        for unit in proc.getUnitOperations():
            if hasattr(unit, 'getFlowRate'):
                throughput = unit.getFlowRate("kg/hr")
                break
        
        # Get total power
        power = 0.0
        for unit in proc.getUnitOperations():
            class_name = unit.getClass().getSimpleName()
            if class_name == "Compressor" or class_name == "Pump":
                power += unit.getPower("kW")
        
        return throughput / power if power > 1.0 else throughput

# Create ObjectiveFunction from Python callable
Direction = ObjectiveFunction.Direction

specific_obj = ObjectiveFunction.create(
    "Specific Production",
    SpecificProductionObjective(),
    Direction.MAXIMIZE,
    "kg/kWh"
)

# Use in optimization
objectives_3 = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER,
    specific_obj
)

front = moo.sampleParetoFront(process, feed, objectives_3, config, 15)
```

### Progress Monitoring

```python
# Define progress callback
@JImplements("neqsim.process.util.optimizer.MultiObjectiveOptimizer$ProgressCallback")
class ProgressMonitor:
    def __init__(self):
        self.feasible = 0
        self.infeasible = 0
    
    @JOverride
    def onProgress(self, iteration, total, solution):
        if solution is not None:
            if solution.isFeasible():
                self.feasible += 1
            else:
                self.infeasible += 1
            print(f"  [{iteration}/{total}] Flow={solution.getRawValue(0):.0f} kg/hr, "
                  f"Power={solution.getRawValue(1):.1f} kW, Feasible={solution.isFeasible()}")
        else:
            print(f"  [{iteration}/{total}] FAILED")

# Use progress monitor
monitor = ProgressMonitor()
moo = MultiObjectiveOptimizer() \
    .includeInfeasible(True) \
    .onProgress(monitor)

front = moo.sampleParetoFront(process, feed, objectives, config, 20)
print(f"\nSummary: {monitor.feasible} feasible, {monitor.infeasible} infeasible")
```

### Extracting Results for Pandas/NumPy

```python
import pandas as pd
import json

# Export to JSON and parse
json_str = front.toJson()
data = json.loads(json_str)

# Build DataFrame from Pareto solutions
results = []
for sol in front.getSolutions():
    row = {
        'throughput_kg_hr': sol.getRawValue(0),
        'power_kW': sol.getRawValue(1),
        'feasible': sol.isFeasible()
    }
    # Add decision variables if available
    dvars = sol.getDecisionVariables()
    if dvars:
        for name, val in dvars.items():
            row[f'var_{name}'] = val
    results.append(row)

df = pd.DataFrame(results)
print(df)

# Save to CSV
df.to_csv('pareto_front.csv', index=False)
```

### Plotting Pareto Front (matplotlib)

```python
import matplotlib.pyplot as plt
import numpy as np

# Extract data for plotting
throughputs = [sol.getRawValue(0) for sol in front.getSolutions()]
powers = [sol.getRawValue(1) for sol in front.getSolutions()]

# Get knee point
knee = front.findKneePoint()
knee_throughput = knee.getRawValue(0)
knee_power = knee.getRawValue(1)

# Plot
fig, ax = plt.subplots(figsize=(10, 6))

# Pareto front
ax.scatter(throughputs, powers, s=100, c='blue', label='Pareto Solutions', zorder=2)

# Connect points to show front
sorted_idx = np.argsort(throughputs)
ax.plot(np.array(throughputs)[sorted_idx], np.array(powers)[sorted_idx], 
        'b--', alpha=0.5, zorder=1)

# Highlight knee point
ax.scatter([knee_throughput], [knee_power], s=200, c='red', marker='*',
           label=f'Knee Point ({knee_throughput:.0f} kg/hr, {knee_power:.1f} kW)', zorder=3)

ax.set_xlabel('Throughput (kg/hr)', fontsize=12)
ax.set_ylabel('Power (kW)', fontsize=12)
ax.set_title('Pareto Front: Throughput vs Power Trade-off', fontsize=14)
ax.legend(loc='upper left')
ax.grid(True, alpha=0.3)

# Add annotations
ax.annotate('High throughput,\nhigh power', 
            xy=(max(throughputs), max(powers)),
            xytext=(max(throughputs)*0.9, max(powers)*1.1),
            fontsize=9, alpha=0.7)
ax.annotate('Low throughput,\nlow power', 
            xy=(min(throughputs), min(powers)),
            xytext=(min(throughputs)*0.8, min(powers)*0.7),
            fontsize=9, alpha=0.7)

plt.tight_layout()
plt.savefig('pareto_front.png', dpi=150)
plt.show()
```

### Integration with SciPy for Custom Algorithms

For advanced multi-objective optimization, combine NeqSim with Python's optimization libraries:

```python
from scipy.optimize import differential_evolution
import numpy as np

def evaluate_both_objectives(x):
    """Evaluate both objectives at flow rate x[0]"""
    flow_rate = x[0]
    
    # Clone process and set flow
    proc_copy = process.copy()
    feed_copy = proc_copy.getUnit("Feed")
    feed_copy.setFlowRate(flow_rate, "kg/hr")
    proc_copy.run()
    
    # Get objectives
    throughput = flow_rate
    power = proc_copy.getUnit("Gas Compressor").getPower("kW")
    
    return throughput, power

# Generate Pareto front using SciPy differential evolution
# with weighted sum scalarization
def weighted_objective(x, w1, w2):
    throughput, power = evaluate_both_objectives(x)
    # Minimize: -w1*throughput + w2*power (negate throughput to maximize)
    return -w1 * throughput + w2 * power

pareto_scipy = []
for w in np.linspace(0.1, 0.9, 9):
    result = differential_evolution(
        weighted_objective, 
        bounds=[(1000, 20000)],
        args=(w, 1-w),
        seed=42
    )
    throughput, power = evaluate_both_objectives(result.x)
    pareto_scipy.append({'throughput': throughput, 'power': power, 'weight': w})

print("SciPy Pareto front:")
for p in pareto_scipy:
    print(f"  w={p['weight']:.1f}: {p['throughput']:.0f} kg/hr, {p['power']:.1f} kW")
```

---

## Related Documentation

- [Optimizer Guide](../../util/optimizer_guide) - Main optimization module documentation
- [Constraint Framework](constraint-framework) - Unified constraint system (ProcessConstraint, ConstraintPenaltyCalculator)
- [Flow Rate Optimization](flow-rate-optimization) - FlowRateOptimizer and Eclipse VFP
- [Batch Parameter Estimation](batch-studies) - Parameter fitting

---

*Last updated: January 2026*

