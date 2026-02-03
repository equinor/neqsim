---
title: Optimization and Constraints in NeqSim
description: This document provides an integrated view of NeqSim's optimization and constraint framework, covering the mathematical foundations, equipment capacity constraints, optimization algorithms, and practic...
---

# Optimization and Constraints in NeqSim

> **Comprehensive guide to process optimization, equipment constraints, and bottleneck analysis in NeqSim**

This document provides an integrated view of NeqSim's optimization and constraint framework, covering the mathematical foundations, equipment capacity constraints, optimization algorithms, and practical usage patterns.

---

## Table of Contents

- [Overview](#overview)
- [Constraint Framework Architecture](#constraint-framework-architecture)
  - [Core Constraint Classes](#core-constraint-classes)
  - [Equipment Capacity Strategies](#equipment-capacity-strategies)
  - [Constraint Types and Severity](#constraint-types-and-severity)
- [Optimization Framework](#optimization-framework)
  - [ProcessOptimizationEngine](#processoptimizationengine)
  - [ProductionOptimizer](#productionoptimizer)
  - [Search Algorithms](#search-algorithms)
  - [Multi-Objective Optimization](#multi-objective-optimization)
- [Constraint-Based Optimization](#constraint-based-optimization)
  - [Defining Equipment Constraints](#defining-equipment-constraints)
  - [Constraint Enablement](#constraint-enablement)
  - [Utilization Limits](#utilization-limits)
- [Practical Examples](#practical-examples)
  - [Maximum Throughput](#maximum-throughput)
  - [Bottleneck Analysis](#bottleneck-analysis)
  - [Multi-Variable Optimization](#multi-variable-optimization)
  - [Pareto Optimization](#pareto-optimization)
- [Integration with External Optimizers](#integration-with-external-optimizers)
- [References](#references)

---

## Overview

NeqSim provides a comprehensive optimization and constraint framework with three main layers:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    LEVEL 3: Application-Specific                             │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐             │
│  │ ProductionOpt.   │ │ BatchParameter   │ │ EclipseVFP       │             │
│  │ (max throughput) │ │ (model calibr.)  │ │ (lift curves)    │             │
│  └────────┬─────────┘ └────────┬─────────┘ └────────┬─────────┘             │
└───────────┼──────────────────────────────────────────┼───────────────────────┘
            │                    │                     │
┌───────────┼──────────────────────────────────────────┼───────────────────────┐
│           ▼        LEVEL 2: Unified Engine           ▼                       │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                   ProcessOptimizationEngine                              ││
│  │  • findMaximumThroughput()   • evaluateAllConstraints()                  ││
│  │  • analyzeSensitivity()      • generateLiftCurve()                       ││
│  │  • Search algorithms: Binary, Golden-Section, BFGS                       ││
│  └──────────────────────────────────┬──────────────────────────────────────┘│
└─────────────────────────────────────┼────────────────────────────────────────┘
                                      │
┌─────────────────────────────────────┼────────────────────────────────────────┐
│                                     ▼                                        │
│                   LEVEL 1: Equipment Constraint Layer                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │            EquipmentCapacityStrategyRegistry (Plugin System)             ││
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐            ││
│  │  │Compressor  │ │ Separator  │ │   Pump     │ │  Expander  │ + custom   ││
│  │  │ Strategy   │ │  Strategy  │ │ Strategy   │ │  Strategy  │            ││
│  │  └────────────┘ └────────────┘ └────────────┘ └────────────┘            ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                     │                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                    CapacityConstraint                                    ││
│  │  (utilization ratio, design vs operating values, severity levels)       ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────────────────┘
```

### Key Capabilities

| Capability | Description |
|------------|-------------|
| **Multi-Constraint Support** | Multiple constraints per equipment (speed, power, surge, etc.) |
| **Constraint Types** | HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope) |
| **Bottleneck Detection** | Automatic identification of limiting equipment and constraint |
| **Search Algorithms** | Binary, Golden-Section, Nelder-Mead, Particle Swarm, Gradient Descent |
| **Multi-Objective** | Pareto optimization via weighted-sum scalarization |
| **External Integration** | Python/SciPy via `ProcessSimulationEvaluator` |

---

## Constraint Framework Architecture

### Core Constraint Classes

The constraint framework is built on these key classes in `neqsim.process.equipment.capacity`:

| Class | Purpose |
|-------|---------|
| `CapacityConstraint` | Single constraint with design/max values and live value supplier |
| `CapacityConstrainedEquipment` | Interface for equipment with capacity limits |
| `StandardConstraintType` | Predefined constraint types (speed, power, K-factor, etc.) |
| `BottleneckResult` | Result of bottleneck analysis with equipment and constraint info |
| `EquipmentCapacityStrategy` | Strategy interface for equipment-specific constraint evaluation |
| `EquipmentCapacityStrategyRegistry` | Plugin registry for equipment strategies |

#### CapacityConstraint

The fundamental building block representing a single capacity limit:

```java
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintType;

// Create a constraint with fluent builder pattern
CapacityConstraint speedConstraint = new CapacityConstraint("speed", "RPM", ConstraintType.HARD)
    .setDesignValue(10000.0)           // Design operating point
    .setMaxValue(11000.0)              // Absolute maximum (trip point)
    .setMinValue(5000.0)               // Minimum stable operation
    .setWarningThreshold(0.9)          // 90% triggers warning
    .setValueSupplier(() -> compressor.getSpeed());  // Live value getter
```

#### Key Constraint Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getCurrentValue()` | `double` | Current value from the valueSupplier |
| `getUtilization()` | `double` | Current value / design value (1.0 = 100%) |
| `getUtilizationPercent()` | `double` | Utilization as percentage |
| `isViolated()` | `boolean` | True if utilization > 1.0 |
| `isHardLimitExceeded()` | `boolean` | True if HARD constraint exceeds max value |
| `isNearLimit()` | `boolean` | True if above warning threshold (default 90%) |
| `getMargin()` | `double` | Remaining headroom (1.0 - utilization) |
| `isEnabled()` | `boolean` | True if constraint participates in capacity analysis |

### Equipment Capacity Strategies

Each equipment type has a capacity strategy that knows how to:
1. Evaluate equipment-specific constraints
2. Calculate utilization ratios
3. Determine the bottleneck constraint

**Available Strategies:**

| Strategy | Equipment | Typical Constraints |
|----------|-----------|---------------------|
| `SeparatorCapacityStrategy` | Separator, ThreePhaseSeparator | gasLoadFactor, liquidResidenceTime, dropletCutSize |
| `CompressorCapacityStrategy` | Compressor | speed, power, surgeMargin, stonewallMargin |
| `PumpCapacityStrategy` | Pump | npshMargin, power, flowRate |
| `ValveCapacityStrategy` | ThrottlingValve | valveOpening, cvUtilization |
| `PipeCapacityStrategy` | Pipeline, AdiabaticPipe | velocity, pressureDrop, FIV_LOF, FIV_FRMS |
| `HeatExchangerCapacityStrategy` | Heater, Cooler | duty, outletTemperature |
| `ExpanderCapacityStrategy` | Expander | speed, power |
| `TankCapacityStrategy` | Tank | fillLevel, fillRate |
| `MixerCapacityStrategy` | Mixer | flowRate, pressureDiff |
| `SplitterCapacityStrategy` | Splitter | flowRate |
| `EjectorCapacityStrategy` | Ejector | compressionRatio, motiveFlow |
| `DistillationColumnCapacityStrategy` | DistillationColumn | floodingFactor, reboilerDuty |

### Constraint Types and Severity

#### Constraint Types

| Type | Description | Examples |
|------|-------------|----------|
| `HARD` | Absolute limit - equipment trip or damage if exceeded | Compressor max speed, surge limit |
| `SOFT` | Operational limit - reduced efficiency or accelerated wear | High discharge temperature |
| `DESIGN` | Normal operating limit - design basis | Separator gas load factor |

#### Constraint Severity Levels

| Severity | Impact | Optimizer Behavior |
|----------|--------|-------------------|
| `CRITICAL` | Safety hazard or equipment damage | Optimization must stop immediately |
| `HARD` | Exceeds design limits | Marks solution as infeasible |
| `SOFT` | Exceeds recommended limits | Applies penalty to objective |
| `ADVISORY` | Information only | No impact on optimization |

---

## Optimization Framework

### ProcessOptimizationEngine

**Purpose:** Find maximum throughput for given inlet/outlet pressure conditions while respecting equipment constraints.

**Best for:**
- Maximum throughput calculations
- Pressure-constrained optimization
- Lift curve generation (Eclipse VFP tables)
- Equipment bottleneck analysis

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.util.optimizer.ProcessOptimizationEngine.OptimizationResult;

// Create engine with process system
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Find max throughput at given pressures
OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure (bara)
    10.0,      // outlet pressure (bara)
    1000.0,    // min flow rate (kg/hr)
    100000.0   // max flow rate (kg/hr)
);

System.out.println("Max flow: " + result.getOptimalValue() + " kg/hr");
System.out.println("Bottleneck: " + result.getBottleneck());
```

### ProductionOptimizer

**Purpose:** General-purpose optimization with arbitrary objective functions, multiple decision variables, and user-defined constraints.

**Best for:**
- Custom objective functions (not just throughput)
- Multi-variable optimization
- Multi-objective Pareto optimization
- User-defined constraints

```java
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

// Create optimizer and config
ProductionOptimizer optimizer = new ProductionOptimizer();
OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)  // flow range
    .tolerance(100.0)
    .maxIterations(30)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .defaultUtilizationLimit(0.95)
    .stagnationIterations(5);  // Early termination if no improvement

// Run optimization
OptimizationResult result = optimizer.optimize(process, feed, config, null, null);

System.out.println("Optimal rate: " + result.getOptimalRate() + " " + result.getRateUnit());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.println("Feasible: " + result.isFeasible());
```

### Search Algorithms

| Algorithm | Code | Best For |
|-----------|------|----------|
| **Binary Feasibility** | `SearchMode.BINARY_FEASIBILITY` | Single-variable, monotonic problems |
| **Golden Section** | `SearchMode.GOLDEN_SECTION_SCORE` | Single-variable, non-monotonic |
| **Nelder-Mead** | `SearchMode.NELDER_MEAD_SCORE` | Multi-variable (2-10 vars), no gradients |
| **Particle Swarm** | `SearchMode.PARTICLE_SWARM_SCORE` | Global search, non-convex problems |
| **Gradient Descent** | `SearchMode.GRADIENT_DESCENT_SCORE` | Multi-variable (5-20+ vars), smooth problems |

#### Algorithm Selection Guide

| Scenario | Recommended Algorithm |
|----------|----------------------|
| "What's the max flow at P_in=50, P_out=10?" | BINARY_FEASIBILITY or GOLDEN_SECTION_SCORE |
| "Optimize flow rate only" | GOLDEN_SECTION_SCORE |
| "Optimize pressure AND flow rate" | NELDER_MEAD_SCORE |
| "Find global optimum with many local optima" | PARTICLE_SWARM_SCORE |
| "Smooth multi-variable optimization" | GRADIENT_DESCENT_SCORE |
| "Trade off throughput vs power" | `optimizePareto()` with any algorithm |

### Multi-Objective Optimization

Pareto optimization finds non-dominated solutions when objectives conflict:

```java
// Define multiple objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput", 
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"), 
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("powerConsumption", 
        proc -> proc.getUnit("compressor").getPower("kW"), 
        1.0, ObjectiveType.MINIMIZE)
);

// Run Pareto optimization
ParetoResult pareto = ProductionOptimizer.optimizePareto(
    process, feed, config, objectives, null, 10);  // 10 Pareto points

// Analyze Pareto front
for (OptimizationResult point : pareto.getParetoFront()) {
    System.out.printf("Throughput: %.0f kg/hr, Power: %.1f kW%n",
        point.getObjectiveValues().get("throughput"),
        point.getObjectiveValues().get("powerConsumption"));
}
```

---

## Constraint-Based Optimization

### Defining Equipment Constraints

Constraints can be defined at three levels:

#### 1. Auto-Sizing (Recommended)

```java
// Separator - creates gasLoadFactor constraint from K-factor sizing
Separator sep = new Separator("HP-Sep", feed);
sep.autoSize(1.2);  // 20% safety factor

// Compressor - creates speed, power, surge constraints + generates curves
Compressor comp = new Compressor("Export", gasStream);
comp.setOutletPressure(100.0);
comp.autoSize(1.2);  // Creates constraints AND compressor curves

// Pipeline - creates velocity, pressureDrop, FIV constraints
Pipeline pipe = new PipeBeggsAndBrills("Export", comp.getOutletStream());
pipe.setLength(30000.0);
pipe.setDiameter(0.3);
pipe.autoSize(1.2);
```

#### 2. Manual Constraint Definition

```java
// Create custom constraint
CapacityConstraint customPower = new CapacityConstraint("powerLimit", "kW", ConstraintType.HARD)
    .setDesignValue(5000.0)
    .setMaxValue(5500.0)
    .setWarningThreshold(0.85)
    .setValueSupplier(() -> compressor.getPower("kW"));

compressor.addCapacityConstraint(customPower);
```

#### 3. Equipment Setters

```java
// Set constraint limits directly
compressor.setMaximumSpeed(11000.0);   // Creates/updates speed constraint
compressor.setMaximumPower(500.0);     // Creates/updates power constraint

separator.setDesignGasLoadFactor(0.15); // Sets K-factor for constraint
```

### Constraint Enablement

> **⚠️ Important:** Constraints are disabled by default for backward compatibility.

#### Enabling Constraints

```java
// Method 1: Use pre-configured constraint sets
separator.useEquinorConstraints();  // K-value, droplet, momentum, retention
separator.useAPIConstraints();      // K-value, retention per API 12J
separator.useAllConstraints();      // All constraint types

// Method 2: Enable all constraints
separator.enableConstraints();

// Method 3: Enable specific constraint
separator.getConstraints().get(StandardConstraintType.SEPARATOR_K_VALUE).setEnabled(true);
```

#### Constraint Enablement by Equipment Type

| Equipment | Default State | Enablement Method |
|-----------|---------------|-------------------|
| **Separator** | All disabled | `useEquinorConstraints()`, `useAPIConstraints()`, `enableConstraints()` |
| **Compressor** | All enabled | Created by `autoSize()` - enabled by default |
| **ThrottlingValve** | All disabled | `enableConstraints()` |
| **Pipeline** | All disabled | `enableConstraints()` |
| **Pump** | All disabled | `enableConstraints()` |

### Utilization Limits

Configure how much of design capacity the optimizer can use:

```java
OptimizationConfig config = new OptimizationConfig(minRate, maxRate)
    // Global default
    .defaultUtilizationLimit(0.95)  // 95% max for all equipment
    
    // Equipment-type specific
    .utilizationLimitForType(Compressor.class, 0.90)  // 90% for compressors
    .utilizationLimitForType(Separator.class, 0.98)   // 98% for separators
    
    // Equipment-specific
    .utilizationLimitForEquipment("HP Compressor", 0.85);  // 85% for this specific unit
```

---

## Practical Examples

### Maximum Throughput

Find the maximum production rate respecting all equipment constraints:

```java
// Create process
ProcessSystem process = new ProcessSystem();

SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

Stream feed = new Stream("Well Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
process.add(feed);

Separator separator = new Separator("HP Separator", feed);
separator.autoSize(1.2);
separator.enableConstraints();
process.add(separator);

Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
compressor.autoSize(1.2);
process.add(compressor);

process.run();

// Optimize
OptimizationConfig config = new OptimizationConfig(1000.0, 50000.0)
    .rateUnit("kg/hr")
    .tolerance(10.0)
    .maxIterations(25)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE);

OptimizationResult result = ProductionOptimizer.optimize(process, feed, config);

System.out.printf("Maximum throughput: %.0f %s%n", 
    result.getOptimalRate(), result.getRateUnit());
System.out.println("Bottleneck: " + result.getBottleneck().getName());
System.out.printf("Utilization: %.1f%%%n", result.getBottleneckUtilization() * 100);
```

### Bottleneck Analysis

Analyze which equipment is limiting production and why:

```java
import neqsim.process.equipment.capacity.BottleneckResult;
import neqsim.process.equipment.capacity.CapacityConstraint;

// After running process
BottleneckResult bottleneck = process.findBottleneck();

if (!bottleneck.isEmpty()) {
    System.out.println("=== BOTTLENECK ANALYSIS ===");
    System.out.println("Equipment: " + bottleneck.getEquipmentName());
    System.out.println("Constraint: " + bottleneck.getConstraintName());
    System.out.printf("Utilization: %.1f%%%n", bottleneck.getUtilizationPercent());
    
    // Get constraint details
    CapacityConstraint constraint = bottleneck.getConstraint();
    System.out.printf("Current value: %.2f %s%n", 
        constraint.getCurrentValue(), constraint.getUnit());
    System.out.printf("Design limit: %.2f %s%n", 
        constraint.getDesignValue(), constraint.getUnit());
    System.out.printf("Type: %s%n", constraint.getConstraintType());
}

// List all equipment near capacity
System.out.println("\n=== EQUIPMENT NEAR CAPACITY (>80%) ===");
for (CapacityConstrainedEquipment equip : process.getEquipmentNearCapacityLimit(0.8)) {
    ProcessEquipmentInterface unit = (ProcessEquipmentInterface) equip;
    System.out.printf("%s: %.1f%% (constraint: %s)%n",
        unit.getName(),
        equip.getMaxUtilizationPercent(),
        equip.getBottleneckConstraint().getName());
}
```

### Multi-Variable Optimization

Optimize multiple decision variables simultaneously:

```java
// Define manipulated variables
List<ManipulatedVariable> variables = Arrays.asList(
    new ManipulatedVariable("flowRate", 50000, 200000, "kg/hr",
        (proc, val) -> {
            StreamInterface feed = (StreamInterface) proc.getUnit("feed");
            feed.setFlowRate(val, "kg/hr");
        }),
    new ManipulatedVariable("outletPressure", 80, 150, "bara",
        (proc, val) -> {
            Compressor comp = (Compressor) proc.getUnit("compressor");
            comp.setOutletPressure(val, "bara");
        })
);

// Define objective
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("profit",
        proc -> {
            double revenue = proc.getUnit("outlet").getFlowRate("kg/hr") * 0.5;  // $/kg
            double powerCost = ((Compressor)proc.getUnit("compressor")).getPower("kW") * 0.10;  // $/kWh
            return revenue - powerCost;
        },
        1.0, ObjectiveType.MAXIMIZE)
);

// Configure for multi-variable
OptimizationConfig config = new OptimizationConfig(0, 1)  // bounds ignored for multi-var
    .searchMode(SearchMode.NELDER_MEAD_SCORE)
    .maxIterations(50)
    .tolerance(0.01);

// Run optimization
OptimizationResult result = ProductionOptimizer.optimize(process, variables, config, objectives, null);

System.out.println("Optimal decision variables:");
for (Map.Entry<String, Double> entry : result.getDecisionVariables().entrySet()) {
    System.out.printf("  %s: %.2f%n", entry.getKey(), entry.getValue());
}
System.out.printf("Optimal profit: $%.2f/hr%n", result.getObjectiveValue());
```

### Pareto Optimization

Trade off competing objectives:

```java
// Define conflicting objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput", 
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"), 
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("specificPower", 
        proc -> {
            double power = ((Compressor)proc.getUnit("comp")).getPower("kW");
            double flow = proc.getUnit("outlet").getFlowRate("kg/hr");
            return power / flow * 1000;  // kWh/tonne
        }, 
        1.0, ObjectiveType.MINIMIZE)
);

// Run Pareto optimization with 15 weight combinations
ParetoResult pareto = ProductionOptimizer.optimizePareto(
    process, feed, config, objectives, null, 15);

// Output Pareto front
System.out.println("=== PARETO FRONT ===");
System.out.println("Throughput (kg/hr) | Specific Power (kWh/t)");
System.out.println("-------------------|-----------------------");
for (OptimizationResult point : pareto.getParetoFront()) {
    Map<String, Double> vals = point.getObjectiveValues();
    System.out.printf("%18.0f | %22.1f%n", 
        vals.get("throughput"), vals.get("specificPower"));
}
```

---

## Integration with External Optimizers

NeqSim can be used with external optimizers via `ProcessSimulationEvaluator`:

### Python/SciPy Example

```python
from neqsim.neqsimpython import jneqsim
from scipy.optimize import minimize, NonlinearConstraint
import numpy as np

# Get Java classes
ProcessSimulationEvaluator = jneqsim.process.util.optimizer.ProcessSimulationEvaluator

# Create evaluator wrapper
evaluator = ProcessSimulationEvaluator(process)

# Define objective function
def objective(x):
    flow_rate, pressure = x
    evaluator.setFlowRate(flow_rate)
    evaluator.setPressure(pressure)
    evaluator.run()
    return -evaluator.getProfit()  # Negative for maximization

# Define constraint (max utilization < 95%)
def constraint(x):
    flow_rate, pressure = x
    evaluator.setFlowRate(flow_rate)
    evaluator.setPressure(pressure)
    evaluator.run()
    return 0.95 - evaluator.getMaxUtilization()

nlc = NonlinearConstraint(constraint, 0, np.inf)

# Run SciPy optimization
result = minimize(
    objective,
    x0=[100000, 100],        # Initial guess
    bounds=[(50000, 200000), (80, 150)],  # Bounds
    constraints=nlc,
    method='SLSQP'
)

print(f"Optimal flow: {result.x[0]:.0f} kg/hr")
print(f"Optimal pressure: {result.x[1]:.1f} bara")
print(f"Maximum profit: ${-result.fun:.2f}/hr")
```

### YAML Configuration

Load optimization configuration from YAML files:

```yaml
# optimization_config.yaml
optimization:
  type: production
  algorithm: GOLDEN_SECTION_SCORE
  bounds:
    min_rate: 50000
    max_rate: 200000
    unit: kg/hr
  tolerance: 100.0
  max_iterations: 30
  
  utilization_limits:
    default: 0.95
    by_type:
      Compressor: 0.90
      Separator: 0.98
    by_name:
      "HP Compressor": 0.85

  objectives:
    - name: throughput
      direction: maximize
      weight: 1.0
    
  constraints:
    - name: max_power
      value: 5000
      unit: kW
      type: less_than
```

```java
// Load and run
ProductionOptimizationSpecLoader loader = new ProductionOptimizationSpecLoader();
OptimizationConfig config = loader.loadConfig("optimization_config.yaml");
OptimizationResult result = ProductionOptimizer.optimize(process, feed, config);
```

---

## Key Configuration Options

### OptimizationConfig Builder Methods

| Method | Description | Default |
|--------|-------------|---------|
| `tolerance(double)` | Convergence tolerance | 100.0 |
| `maxIterations(int)` | Maximum iterations | 20 |
| `rateUnit(String)` | Flow rate unit | "kg/hr" |
| `searchMode(SearchMode)` | Algorithm selection | BINARY_FEASIBILITY |
| `defaultUtilizationLimit(double)` | Max equipment utilization | 0.95 |
| `utilizationLimitForType(Class, double)` | Type-specific limits | - |
| `stagnationIterations(int)` | Early termination threshold | 5 |
| `initialGuess(double[])` | Warm start values | - |
| `maxCacheSize(int)` | LRU cache limit | 1000 |
| `validate()` | Validate configuration | - |

### OptimizationResult Properties

| Method | Description |
|--------|-------------|
| `getOptimalRate()` | Optimal production rate |
| `getRateUnit()` | Unit of the rate |
| `isFeasible()` | Whether solution satisfies all constraints |
| `getBottleneck()` | Limiting equipment |
| `getBottleneckUtilization()` | Utilization of bottleneck |
| `getObjectiveValue()` | Final objective value |
| `getObjectiveValues()` | Map of all objective values |
| `getDecisionVariables()` | Map of optimized variable values |
| `getIterationCount()` | Number of iterations used |
| `getInfeasibilityDiagnosis()` | Detailed constraint violation report |

---

## References

### Related Documentation

| Document | Path | Description |
|----------|------|-------------|
| Optimization Overview | [OPTIMIZATION_OVERVIEW.md](OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| Capacity Constraint Framework | [../CAPACITY_CONSTRAINT_FRAMEWORK.md](../CAPACITY_CONSTRAINT_FRAMEWORK.md) | Detailed constraint architecture |
| Production Optimization Guide | [../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | Complete Java/Python examples |
| External Integration | [../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) | SciPy/NLopt integration |
| Batch Studies | [batch-studies.md](batch-studies.md) | Parameter sensitivity analysis |
| Multi-Objective | [multi-objective-optimization.md](multi-objective-optimization.md) | Pareto optimization details |

### Source Code References

| Class | Package | Purpose |
|-------|---------|---------|
| `ProductionOptimizer` | `neqsim.process.util.optimizer` | Main optimization class |
| `ProcessOptimizationEngine` | `neqsim.process.util.optimizer` | Throughput-focused engine |
| `CapacityConstraint` | `neqsim.process.equipment.capacity` | Constraint definition |
| `CapacityConstrainedEquipment` | `neqsim.process.equipment.capacity` | Equipment interface |
| `EquipmentCapacityStrategyRegistry` | `neqsim.process.equipment.capacity` | Strategy plugin system |
| `ProcessConstraintEvaluator` | `neqsim.process.util.optimizer` | Constraint evaluation |

---

*Last updated: January 2026*
