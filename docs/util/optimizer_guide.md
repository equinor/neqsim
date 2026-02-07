---
title: "Process Optimization Module"
description: "Complete guide to the neqsim.process.util.optimizer package covering ProductionOptimizer, ProcessOptimizationEngine, MultiObjectiveOptimizer, FlowRateOptimizer, the unified constraint framework, and all search algorithms."
---

# Process Optimization Module

The `neqsim.process.util.optimizer` package provides a comprehensive optimization framework for process simulation. It includes five search algorithms, multi-objective optimization with Pareto front generation, a unified constraint framework bridging equipment-level and optimizer-level restrictions, and external optimizer integration.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Multi-Objective Optimization](../process/optimization/multi-objective-optimization) | Pareto fronts and standard objectives |
| [Flow Rate Optimization](../process/optimization/flow-rate-optimization) | FlowRateOptimizer and Eclipse VFP tables |
| [Constraint Framework](../process/optimization/constraint-framework) | Unified constraint system for internal and external optimizers |
| [Pressure Boundary Optimization](../process/pressure_boundary_optimization) | PressureBoundaryOptimizer examples |

## Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [Architecture](#architecture)
- [Search Algorithms](#search-algorithms)
- [Production Optimization](#production-optimization)
- [ProcessOptimizationEngine](#processoptimizationengine)
- [Multi-Objective Optimization](#multi-objective-optimization)
- [Constraint Framework](#constraint-framework)
- [External Optimizer Integration](#external-optimizer-integration)
- [Algorithm Selection Guide](#algorithm-selection-guide)
- [Best Practices](#best-practices)
- [What's New](#whats-new)
- [API Reference](#api-reference)

---

## Overview

**Location:** `neqsim.process.util.optimizer`

**Purpose:**

- Maximize throughput, minimize power, or optimize custom objectives
- Five search algorithms: Binary Search, Golden Section, Nelder-Mead, PSO, Gradient Descent
- Multi-objective optimization with Pareto front generation
- Unified constraint framework (`ProcessConstraint`) bridging equipment capacity constraints, internal optimizer constraints, and external optimizer constraints
- Equipment capacity auto-discovery via the plugin registry
- Lift curve / VFP table generation for Eclipse reservoir coupling

---

## Quick Start

### ProductionOptimizer (Single-Variable Throughput)

```java
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .rateUnit("kg/hr")
    .tolerance(10.0)
    .maxIterations(30)
    .defaultUtilizationLimit(0.95)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);

OptimizationResult result = optimizer.optimize(process, feedStream, config);
System.out.println("Optimal rate: " + result.getOptimalRate());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Feasible: " + result.isFeasible());
```

### ProcessOptimizationEngine (Pressure-Boundary)

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setFeedStreamName("Feed");
engine.setOutletStreamName("Export");
engine.setSearchAlgorithm(
    ProcessOptimizationEngine.SearchAlgorithm.GOLDEN_SECTION);
engine.setMaxIterations(50);
engine.setTolerance(10.0);

ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 100.0, 1000.0, 50000.0);

System.out.println("Optimal flow: " + result.getOptimalValue());
System.out.println("Converged: " + result.isConverged());
System.out.println("Bottleneck: " + result.getBottleneck());
```

---

## Architecture

### Class Hierarchy

```
neqsim.process.util.optimizer/
│
│ ── Core Optimizers ──
├── ProductionOptimizer            # Throughput optimization with 5 search modes
├── ProcessOptimizationEngine      # Pressure-boundary optimization, lift curves
├── MultiObjectiveOptimizer        # Pareto front generation (weighted sum, epsilon-constraint)
├── FlowRateOptimizer              # Max flow at pressure boundaries, Eclipse VFP export
│
│ ── Unified Constraint Framework ──
├── ProcessConstraint              # Interface: margin(process) >= 0 = satisfied
├── ConstraintSeverityLevel        # Enum: CRITICAL, HARD, SOFT, ADVISORY
├── CapacityConstraintAdapter      # Wraps equipment CapacityConstraint as ProcessConstraint
├── ConstraintPenaltyCalculator    # Reusable adaptive penalty for any optimizer
│
│ ── External Optimizer Bridge ──
├── ProcessSimulationEvaluator     # Parameter/objective/constraint definitions for NLP
│
│ ── Multi-Objective ──
├── ObjectiveFunction              # Interface with Direction enum (MAXIMIZE/MINIMIZE)
├── StandardObjective              # Pre-built objectives (throughput, power, duty)
├── ParetoFront                    # Non-dominated solution set with knee point
├── ParetoSolution                 # Single Pareto point
│
│ ── Equipment Capacity (neqsim.process.equipment.capacity/) ──
├── EquipmentCapacityStrategy      # Strategy interface for capacity constraints
├── EquipmentCapacityStrategyRegistry  # Plugin registry for strategies
├── CompressorCapacityStrategy     # Compressor-specific constraints
├── SeparatorCapacityStrategy      # Separator constraints
├── PumpCapacityStrategy           # Pump constraints
├── ExpanderCapacityStrategy       # Expander constraints
└── EjectorCapacityStrategy        # Ejector constraints
```

### Plugin System

Equipment capacity strategies are auto-discovered via `EquipmentCapacityStrategyRegistry`. Register custom strategies:

```java
import neqsim.process.equipment.capacity.*;

public class CustomCapacityStrategy implements EquipmentCapacityStrategy {

    @Override
    public boolean appliesTo(ProcessEquipmentInterface equipment) {
        return equipment instanceof MyCustomEquipment;
    }

    @Override
    public List<CapacityConstraint> getConstraints(
            ProcessEquipmentInterface equipment) {
        MyCustomEquipment eq = (MyCustomEquipment) equipment;
        List<CapacityConstraint> constraints = new ArrayList<>();
        constraints.add(new CapacityConstraint(
            "maxPressure",
            eq.getPressure() / eq.getDesignPressure(), // utilization
            CapacityConstraint.ConstraintSeverity.HARD,
            "bara",
            "Pressure vs design limit"
        ));
        return constraints;
    }
}

// Register
EquipmentCapacityStrategyRegistry.getInstance()
    .register(new CustomCapacityStrategy());
```

---

## Search Algorithms

All search algorithms are available via `ProductionOptimizer.SearchMode` and `ProcessOptimizationEngine.SearchAlgorithm`.

### Binary Search (`BINARY_FEASIBILITY` / `BINARY_SEARCH`)

Traditional binary search on feasibility. Best for monotonic problems where increasing the decision variable monotonically decreases feasibility.

```java
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .searchMode(SearchMode.BINARY_FEASIBILITY);
```

### Golden Section (`GOLDEN_SECTION_SCORE` / `GOLDEN_SECTION`)

Golden-section search minimizing a score function. Requires a unimodal objective. Efficient for single-variable optimization.

```java
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);
```

### Nelder-Mead (`NELDER_MEAD_SCORE` / `NELDER_MEAD`)

Simplex method for derivative-free multi-dimensional optimization. Good for 2-10 decision variables with smooth landscapes.

```java
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .searchMode(SearchMode.NELDER_MEAD_SCORE);
```

### Particle Swarm (`PARTICLE_SWARM_SCORE` / `PARTICLE_SWARM`)

Population-based global optimizer. Handles non-convex, multi-modal landscapes. Supports configurable swarm parameters and reproducible runs.

```java
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .searchMode(SearchMode.PARTICLE_SWARM_SCORE)
    .swarmSize(30)
    .inertiaWeight(0.7)
    .cognitiveWeight(1.5)
    .socialWeight(1.5)
    .useFixedSeed(true)         // Reproducible results
    .randomSeed(42L);
```

### Gradient Descent (`GRADIENT_DESCENT_SCORE` / `GRADIENT_DESCENT`)

Steepest ascent with finite-difference gradients and Armijo backtracking line search. Best for smooth landscapes with 5+ variables.

```java
// Via ProcessOptimizationEngine for more control:
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setSearchAlgorithm(SearchAlgorithm.GRADIENT_DESCENT);
engine.setArmijoC1(1e-4);  // Armijo sufficient decrease constant
engine.setMaxLineSearchIterations(20);
```

The engine also offers `GRADIENT_DESCENT_ARMIJO_WOLFE` (with Wolfe curvature condition) and `BFGS` (scalar quasi-Newton for 1-D problems).

---

## Production Optimization

### OptimizationConfig (Fluent Builder)

```java
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    // Basic settings
    .rateUnit("kg/hr")
    .tolerance(10.0)
    .maxIterations(30)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)

    // Equipment constraints
    .defaultUtilizationLimit(0.95)
    .utilizationLimitForName("K-100", 0.90)    // Per-equipment override
    .utilizationLimitForType(Compressor.class, 0.92)

    // Advanced
    .stagnationIterations(10)    // Early stop after 10 no-improvement iterations
    .maxCacheSize(500)           // Bounded LRU cache
    .initialGuess(new double[]{7500.0})  // Warm start
    .parallelEvaluations(true)   // Parallel function evaluations
    .parallelThreads(4);

config.validate();  // Throws IllegalArgumentException if invalid
```

### Custom Constraints

```java
// Add hard constraint: compressor power must stay below 5000 kW
OptimizationConstraint powerLimit = OptimizationConstraint.lessThan(
    "MaxPower",
    (proc, rate) -> getTotalPower(proc),
    5000.0,
    ConstraintSeverity.HARD,
    100.0  // penalty weight
);

// Add soft constraint: prefer gas export above 10 MSm3/d
OptimizationConstraint exportTarget = OptimizationConstraint.greaterThan(
    "MinExport",
    (proc, rate) -> getGasExport(proc),
    10.0e6,
    ConstraintSeverity.SOFT,
    50.0
);
```

### Custom Objectives

```java
// Add secondary objective alongside throughput
OptimizationObjective minPower = new OptimizationObjective(
    "MinPower",
    (proc, rate) -> getTotalCompressorPower(proc),
    0.3,  // weight
    ObjectiveType.MINIMIZE
);

// Multi-objective Pareto via ProductionOptimizer
ParetoResult pareto = optimizer.optimizePareto(process, feed, config,
    Arrays.asList(throughputObj, minPower));
```

### Infeasibility Diagnostics

```java
OptimizationResult result = optimizer.optimize(process, feed, config);

if (!result.isFeasible()) {
    String diagnosis = result.getInfeasibilityDiagnosis();
    System.out.println(diagnosis);
    // Output example:
    // Infeasibility diagnosis for rate 15000.0 kg/hr:
    //   - Compressor 'K-100': 115.2% utilization (limit: 95.0%), exceeded by 20.2%
}

// Export iteration history for analysis
String csv = result.exportIterationHistoryAsCsv();
String json = result.exportIterationHistoryAsJson();
```

---

## ProcessOptimizationEngine

The `ProcessOptimizationEngine` provides pressure-boundary optimization, lift curve generation, sensitivity analysis, and constraint evaluation.

### Finding Maximum Throughput

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setFeedStreamName("Feed");
engine.setOutletStreamName("Export");
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);
engine.setEnforceConstraints(true);

ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 100.0, 1000.0, 50000.0);

System.out.println("Optimal: " + result.getOptimalValue());
System.out.println("Bottleneck: " + result.getBottleneck());
```

### Finding Required Inlet Pressure

```java
ProcessOptimizationEngine.OptimizationResult result =
    engine.findRequiredInletPressure(
        30000.0,  // target flow (kg/hr)
        100.0,    // outlet pressure (bara)
        30.0,     // min inlet pressure
        200.0     // max inlet pressure
    );
```

### Constraint Evaluation

```java
ProcessOptimizationEngine.ConstraintReport report =
    engine.evaluateAllConstraints();

String bottleneck = engine.findBottleneckEquipment();
```

### Sensitivity Analysis

```java
ProcessOptimizationEngine.SensitivityResult sensitivity =
    engine.analyzeSensitivity(optimalFlow, inletPressure, outletPressure);
```

### Lift Curve Generation

```java
ProcessOptimizationEngine.LiftCurveData liftCurve =
    engine.generateLiftCurve(
        new double[]{40, 50, 60, 70},     // pressures
        new double[]{15, 25, 35},          // temperatures (C)
        new double[]{0.0, 0.1, 0.2},      // water cuts
        new double[]{500, 1000, 2000}      // GORs
    );
```

---

## Multi-Objective Optimization

### Weighted Sum Method

```java
import neqsim.process.util.optimizer.MultiObjectiveOptimizer;
import neqsim.process.util.optimizer.StandardObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

MultiObjectiveOptimizer moOptimizer = new MultiObjectiveOptimizer();

List<ObjectiveFunction> objectives = Arrays.asList(
    StandardObjective.MAXIMIZE_THROUGHPUT,
    StandardObjective.MINIMIZE_POWER
);

OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .rateUnit("kg/hr")
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);

ParetoFront pareto = moOptimizer.optimizeWeightedSum(
    process, feed, objectives, config, 20);  // 20 weight combinations
```

> **Note:** The weighted-sum method can only find solutions on the convex hull of the Pareto front. For non-convex fronts, use epsilon-constraint or sampling.

### Epsilon-Constraint Method

```java
ParetoFront pareto = moOptimizer.optimizeEpsilonConstraint(
    process, feed,
    StandardObjective.MINIMIZE_POWER,       // primary objective
    Arrays.asList(StandardObjective.MAXIMIZE_THROUGHPUT), // constrained
    config, 15);  // 15 grid points
```

### Pareto Front Analysis

```java
ParetoFront pareto = ...;
ParetoSolution knee = pareto.findKneePoint();
double spacing = pareto.calculateSpacing();
String json = pareto.toJson();

List<ParetoSolution> sorted = pareto.getSolutionsSortedBy(0, true);
```

### Standard Objectives

| Enum Value | Direction | Unit |
|------------|-----------|------|
| `MAXIMIZE_THROUGHPUT` | Maximize | kg/hr |
| `MINIMIZE_POWER` | Minimize | kW |
| `MINIMIZE_HEATING_DUTY` | Minimize | kW |
| `MINIMIZE_COOLING_DUTY` | Minimize | kW |
| `MINIMIZE_TOTAL_ENERGY` | Minimize | kW |
| `MAXIMIZE_SPECIFIC_PRODUCTION` | Maximize | kg/kWh |
| `MAXIMIZE_LIQUID_RECOVERY` | Maximize | - |

---

## Constraint Framework

The unified constraint framework provides a single `ProcessConstraint` interface that bridges three constraint layers:

1. **Equipment capacity constraints** (auto-discovered from process equipment)
2. **Internal optimizer constraints** (`OptimizationConstraint` in `ProductionOptimizer`)
3. **External optimizer constraints** (`ConstraintDefinition` in `ProcessSimulationEvaluator`)

All three implement `ProcessConstraint`, enabling a single API for penalty calculation, feasibility checking, and constraint margin evaluation.

### ProcessConstraint Interface

```java
public interface ProcessConstraint {
    String getName();
    double margin(ProcessSystem process);     // >= 0 means satisfied
    boolean isSatisfied(ProcessSystem process); // margin >= 0
    ConstraintSeverityLevel getSeverityLevel();
    double getPenaltyWeight();
    double penalty(ProcessSystem process);     // weight * margin^2 when violated
    boolean isHard();
    String getDescription();
}
```

### Severity Levels

| Level | Optimization Impact | Example |
|-------|-------------------|---------|
| `CRITICAL` | Solution rejected; optimizer may abort | Compressor surge |
| `HARD` | Solution marked infeasible | Design capacity exceeded |
| `SOFT` | Penalty applied to objective | Recommended operating range |
| `ADVISORY` | Reporting only, no optimization impact | Turndown ratio |

### ConstraintPenaltyCalculator

Reusable calculator for any optimizer — auto-discovers equipment constraints and computes adaptive penalties:

```java
import neqsim.process.util.optimizer.ConstraintPenaltyCalculator;

ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator();

// Auto-discover equipment constraints from the process
calc.addEquipmentCapacityConstraints(process);

// Add custom constraints
calc.addConstraint(powerLimit);  // any ProcessConstraint

// Check feasibility
boolean feasible = calc.isFeasible(process);

// Get NLP constraint margin vector: g(x) >= 0
double[] margins = calc.evaluateMargins(process);

// Penalize an objective value (adaptive scaling)
double penalized = calc.penalize(rawObjective, process);

// Get detailed per-constraint report
List<ConstraintPenaltyCalculator.ConstraintEvaluation> evals =
    calc.evaluate(process);
for (ConstraintPenaltyCalculator.ConstraintEvaluation e : evals) {
    System.out.printf("%s: margin=%.3f, satisfied=%b, penalty=%.1f%n",
        e.getName(), e.getMargin(), e.isSatisfied(), e.getPenalty());
}
```

For the full constraint framework documentation, see [Constraint Framework](../process/optimization/constraint-framework).

---

## External Optimizer Integration

The `ProcessSimulationEvaluator` provides a parameter/objective/constraint bridge for external NLP solvers (SciPy, IPOPT, etc.).

### Defining Parameters and Objectives

```java
import neqsim.process.util.optimizer.ProcessSimulationEvaluator;

ProcessSimulationEvaluator evaluator = new ProcessSimulationEvaluator(process);

// Decision variables
evaluator.addParameter("feedRate", feed, "flowRate",
    1000.0, 20000.0, "kg/hr");
evaluator.addParameter("inletPressure", feed, "pressure",
    30.0, 100.0, "bara");

// Objectives
evaluator.addObjective("throughput",
    ProcessSimulationEvaluator.ObjectiveDefinition.Direction.MAXIMIZE,
    proc -> proc.getMeasuredValue("feed", "flowRate", "kg/hr"));

// Constraints from equipment capacity
evaluator.addEquipmentCapacityConstraints();

// Get all constraints as ProcessConstraint list
List<ProcessConstraint> allConstraints =
    evaluator.getAllProcessConstraints();

// Evaluate at a point
double[] x = {10000.0, 60.0};
ProcessSimulationEvaluator.EvaluationResult result = evaluator.evaluate(x);

// NLP constraint margin vector for gradient-based solvers
double[] constraintMargins =
    evaluator.getConstraintMarginVector(process);
```

### Constraint Conversion Between Layers

Constraints can be converted between the internal and external optimizer representations:

```java
// Internal -> External
OptimizationConstraint internal = OptimizationConstraint.lessThan(...);
ProcessSimulationEvaluator.ConstraintDefinition external =
    internal.toConstraintDefinition();

// External -> Internal
ProcessSimulationEvaluator.ConstraintDefinition def = ...;
OptimizationConstraint opt = def.toOptimizationConstraint();
```

---

## Algorithm Selection Guide

| Variables | Problem Type | Recommended | ProductionOptimizer | ProcessOptimizationEngine |
|-----------|-------------|-------------|--------------------|-----------------------------|
| 1 | Monotonic feasibility | Binary Search | `BINARY_FEASIBILITY` | `BINARY_SEARCH` |
| 1 | Unimodal objective | Golden Section | `GOLDEN_SECTION_SCORE` | `GOLDEN_SECTION` |
| 2-10 | Smooth, derivative-free | Nelder-Mead | `NELDER_MEAD_SCORE` | `NELDER_MEAD` |
| Any | Non-convex, multi-modal | PSO | `PARTICLE_SWARM_SCORE` | `PARTICLE_SWARM` |
| 5-20+ | Smooth, gradient-based | Gradient Descent | `GRADIENT_DESCENT_SCORE` | `GRADIENT_DESCENT` |
| 1 | Smooth, quasi-Newton | BFGS | - | `BFGS` |

---

## Best Practices

### 1. Run Process Before Optimizing

Always run the process once to establish a feasible baseline:

```java
process.run();

// Then optimize
OptimizationConfig config = new OptimizationConfig(1000.0, 20000.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);
OptimizationResult result = optimizer.optimize(process, feed, config);
```

### 2. Validate Configuration

```java
config.validate();  // Throws IllegalArgumentException if bounds, tolerance, or iterations invalid
```

### 3. Use Warm Starts

Start near a known good solution for faster convergence:

```java
config.initialGuess(new double[]{7500.0});
```

### 4. Use Stagnation Detection

Stop early when the optimizer is no longer improving:

```java
config.stagnationIterations(10);  // Stop after 10 iterations with no improvement
```

### 5. Control Memory with Bounded Cache

```java
config.maxCacheSize(500);  // Limit LRU cache to 500 entries
```

### 6. Use PSO Fixed Seed for Reproducibility

```java
config.useFixedSeed(true).randomSeed(42L);
```

### 7. Check Constraint Feasibility

Use `ConstraintPenaltyCalculator` to verify feasibility independently:

```java
ConstraintPenaltyCalculator calc = new ConstraintPenaltyCalculator()
    .addEquipmentCapacityConstraints(process);
boolean ok = calc.isFeasible(process);
```

---

## What's New

### Unified Constraint Framework (January 2026)
- **`ProcessConstraint` interface**: Single contract for all constraint types with `margin(process) >= 0` convention
- **`ConstraintSeverityLevel` enum**: 4-level severity (CRITICAL/HARD/SOFT/ADVISORY) with bidirectional mappings between equipment, internal, and external optimizer constraint systems
- **`CapacityConstraintAdapter`**: Wraps equipment `CapacityConstraint` as `ProcessConstraint` for direct use in any optimizer
- **`ConstraintPenaltyCalculator`**: Reusable adaptive penalty calculator with auto-discovery of equipment constraints
- **`ProcessSimulationEvaluator` bridge**: `addEquipmentCapacityConstraints()`, `getAllProcessConstraints()`, `getConstraintMarginVector()` for NLP solver integration
- **Constraint conversions**: `OptimizationConstraint.toConstraintDefinition()` and `ConstraintDefinition.toOptimizationConstraint()` for bidirectional conversion

### Algorithm and Engine Improvements (January 2026)
- **Configurable PSO seed**: `useFixedSeed(true).randomSeed(42L)` for reproducible results
- **Thread-safe PSO**: All shared state properly synchronized
- **Adaptive penalty scaling**: Penalty scales with `|rawObjective|` for balanced optimization
- **Shadow price calculation**: Finite-difference based in `ProcessOptimizationEngine`
- **Stagnation detection**: `stagnationIterations(int)` for early termination
- **Warm start**: `initialGuess(double[])` to start near known good solutions
- **Bounded LRU cache**: `maxCacheSize(int)` to control memory usage
- **Configuration validation**: `config.validate()` checks bounds, tolerance, iterations
- **Infeasibility diagnostics**: `result.getInfeasibilityDiagnosis()` for detailed violation reports

### Bug Fixes (January 2026)
- Golden Section ratio: Fixed inconsistent phi formula and comparison logic
- Nelder-Mead bounds: Added clamping for reflected/contracted simplex points
- Zero flow validation: Added check for zero/invalid flow rates
- Feasibility scoring: Fixed penalty calculation to use actual utilization limits

---

## API Reference

### ProductionOptimizer

| Method | Description |
|--------|-------------|
| `optimize(ProcessSystem, StreamInterface, OptimizationConfig)` | Run single-objective optimization |
| `optimizePareto(ProcessSystem, StreamInterface, OptimizationConfig, List)` | Multi-objective Pareto optimization |
| `optimizeScenarios(ProcessSystem, StreamInterface, OptimizationConfig, List)` | Scenario-based optimization |

### ProductionOptimizer.OptimizationResult

| Method | Description |
|--------|-------------|
| `getOptimalRate()` | Optimal flow rate |
| `getBottleneck()` | Limiting equipment utilization record |
| `getBottleneckUtilization()` | Utilization fraction at bottleneck |
| `isFeasible()` | Whether all hard constraints satisfied |
| `getScore()` | Objective score at optimum |
| `getIterations()` | Number of iterations performed |
| `getInfeasibilityDiagnosis()` | Detailed constraint violation report |
| `exportIterationHistoryAsCsv()` | CSV export of iteration history |
| `exportIterationHistoryAsJson()` | JSON export of iteration history |

### ProductionOptimizer.OptimizationConfig

| Method | Description |
|--------|-------------|
| `searchMode(SearchMode)` | Set search algorithm |
| `tolerance(double)` | Convergence tolerance |
| `maxIterations(int)` | Maximum iterations |
| `defaultUtilizationLimit(double)` | Default equipment utilization limit |
| `utilizationLimitForName(String, double)` | Per-equipment utilization override |
| `stagnationIterations(int)` | Early stop after N no-improvement iterations |
| `maxCacheSize(int)` | Maximum LRU cache entries |
| `initialGuess(double[])` | Warm start point |
| `validate()` | Validate configuration |

### ProcessOptimizationEngine

| Method | Description |
|--------|-------------|
| `findMaximumThroughput(inP, outP, minFlow, maxFlow)` | Find max flow at pressure boundaries |
| `findRequiredInletPressure(targetFlow, outP, minP, maxP)` | Find inlet pressure for target flow |
| `evaluateAllConstraints()` | Evaluate all equipment constraints |
| `findBottleneckEquipment()` | Identify bottleneck equipment |
| `generateLiftCurve(P[], T[], WC[], GOR[])` | Generate lift curve data |
| `analyzeSensitivity(flow, inP, outP)` | Sensitivity analysis |
| `setSearchAlgorithm(SearchAlgorithm)` | Set algorithm |
| `setArmijoC1(double)` | Armijo line search constant |
| `setWolfeC2(double)` | Wolfe curvature constant |
| `setBfgsGradientTolerance(double)` | BFGS gradient convergence tolerance |

### ConstraintPenaltyCalculator

| Method | Description |
|--------|-------------|
| `addConstraint(ProcessConstraint)` | Add single constraint |
| `addConstraints(List)` | Add multiple constraints |
| `addEquipmentCapacityConstraints(ProcessSystem)` | Auto-discover equipment constraints |
| `evaluateMargins(ProcessSystem)` | NLP margin vector (g(x) >= 0) |
| `isFeasible(ProcessSystem)` | Check all hard constraints |
| `totalPenalty(ProcessSystem)` | Sum of all penalties |
| `penalize(double, ProcessSystem)` | Adaptive penalty on raw objective |
| `evaluate(ProcessSystem)` | Per-constraint detailed report |
