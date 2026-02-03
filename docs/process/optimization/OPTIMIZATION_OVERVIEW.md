---
title: Process Optimization in NeqSim - Overview
description: This document provides a high-level introduction to the process optimization capabilities in NeqSim, explaining how the different components relate to each other and when to use each one.
---

# Process Optimization in NeqSim - Overview

This document provides a high-level introduction to the process optimization capabilities in NeqSim, explaining how the different components relate to each other and when to use each one.

## Table of Contents

- [Quick Navigation](#quick-navigation)
- [Architecture Overview](#architecture-overview)
- [The Two Main Optimizers](#the-two-main-optimizers)
- [When to Use Which Optimizer](#when-to-use-which-optimizer)
- [Key Concepts](#key-concepts)
- [Search Algorithms](#search-algorithms)
- [Python Usage via JPype](#python-usage-via-jpype)
- [Complete Examples](#complete-examples)
- [Related Documentation](#related-documentation)

---

## Quick Navigation

| I want to... | Use this class | Documentation |
|--------------|----------------|---------------|
| Find maximum throughput for given pressures | `ProcessOptimizationEngine` | [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) |
| Optimize arbitrary objectives with constraints | `ProductionOptimizer` | [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) |
| Do multi-objective Pareto optimization | `ProductionOptimizer.optimizePareto()` | [Multi-Objective Optimization](multi-objective-optimization) |
| Run batch parameter studies | `BatchStudy` | [Batch Studies](batch-studies) |
| Calculate flow rates for pressure boundaries | `FlowRateOptimizer` | [Flow Rate Optimization](flow-rate-optimization) |
| Generate Eclipse lift curves (VFP tables) | `EclipseVFPExporter` | [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE#eclipsevfpexporter) |
| Evaluate equipment constraints | `ProcessConstraintEvaluator` | [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) |
| Integrate with external optimizers (SciPy, NLopt) | `ProcessSimulationEvaluator` | [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) |
| Calibrate model parameters to data | `BatchParameterEstimator` | [README.md](./ |
| Load optimization config from YAML/JSON | `ProductionOptimizationSpecLoader` | [YAML Spec Format](#yaml-specification-files) |

---

## All Documentation Files

| Document | Purpose |
|----------|---------|
| **This Document** | High-level overview and when to use which optimizer |
| **[Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS)** | **COMPREHENSIVE: Complete guide to algorithms, constraint types, bottleneck analysis, practical examples** |
| **[ProductionOptimizer Tutorial (Jupyter)](../../examples/ProductionOptimizer_Tutorial)** | **Interactive notebook: algorithms, single/multi-variable, Pareto, constraints** |
| **[Python Optimization Tutorial (Jupyter)](../../examples/NeqSim_Python_Optimization)** | **Using SciPy/Python optimizers with NeqSim: constraints, Pareto, global opt** |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) | Equipment capacity strategies, ProcessOptimizationEngine API, VFP export |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | Complete examples for ProductionOptimizer with Java/Python |
| [Practical Examples](PRACTICAL_EXAMPLES) | Code samples for common optimization tasks |
| [Multi-Objective Optimization](multi-objective-optimization) | Pareto fronts, weighted-sum, epsilon-constraint methods |
| [Batch Studies](batch-studies) | Parallel parameter sweeps and sensitivity analysis |
| [Flow Rate Optimization](flow-rate-optimization) | FlowRateOptimizer and lift curve tables |
| [External Optimizer Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) | ProcessSimulationEvaluator for Python/SciPy integration |
| [README.md](./ | BatchParameterEstimator for Levenberg-Marquardt calibration |
| [Optimizer Guide](../../util/optimizer_guide) | Detailed API reference for all optimizer classes |
| [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) | Equipment constraints and bottleneck detection |

---

## Architecture Overview

NeqSim provides three main levels of optimization capability:

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

---

## The Two Main Optimizers

### ProcessOptimizationEngine

**Purpose:** Find maximum throughput for given inlet/outlet pressure conditions while respecting equipment constraints.

**Best for:**
- Maximum throughput calculations
- Pressure-constrained optimization  
- Lift curve generation
- Equipment bottleneck analysis
- Integration with ProcessSystem/ProcessModule

**Key Features:**
- Works directly with `ProcessSystem` or `ProcessModule`
- Uses equipment capacity strategy plugins
- Supports multiple search algorithms (Binary, Golden-Section, BFGS)
- Auto-detects feed and outlet streams
- Generates sensitivity analysis

```java
// ProcessOptimizationEngine - throughput-focused
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Find max throughput at given pressures
OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure (bara)
    10.0,      // outlet pressure (bara)
    1000.0,    // min flow rate
    100000.0   // max flow rate
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
- Scenario evaluation and parallelization

**Key Features:**
- Arbitrary objective functions via lambdas/interfaces
- Multiple decision variables (`ManipulatedVariable`)
- Multiple search algorithms (Binary, Golden-Section, Nelder-Mead, PSO)
- Pareto multi-objective optimization
- Parallel scenario evaluation
- Works with any `ProcessSystem`

```java
// ProductionOptimizer - general-purpose
ProductionOptimizer optimizer = new ProductionOptimizer();

// Configure optimization
OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .tolerance(100.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .maxIterations(30);

// Define objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput", 
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"), 
        1.0, ObjectiveType.MAXIMIZE)
);

// Run optimization
OptimizationResult result = optimizer.optimize(process, feed, config, objectives, null);
System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
```

---

## When to Use Which Optimizer

| Scenario | Recommended | Why |
|----------|-------------|-----|
| "What's the max flow at P_in=50, P_out=10?" | `ProcessOptimizationEngine` | Designed exactly for this |
| "Find bottleneck equipment" | `ProcessOptimizationEngine` | Has constraint evaluation built-in |
| "Generate Eclipse VFP tables" | `ProcessOptimizationEngine` | Has `EclipseVFPExporter` integration |
| "Minimize operating cost" | `ProductionOptimizer` | Custom objective function support |
| "Optimize pressure AND flow rate together" | `ProductionOptimizer` | Multi-variable support |
| "Trade off throughput vs power consumption" | `ProductionOptimizer.optimizePareto()` | Pareto multi-objective |
| "Evaluate 100 scenarios in parallel" | `ProductionOptimizer` | Has parallel evaluation |
| "Calibrate model to match field data" | `BatchParameterEstimator` | Levenberg-Marquardt for data fitting |

---

## Relationship Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           USER CODE                                          │
└─────────┬─────────────────────┬────────────────────────┬─────────────────────┘
          │                     │                        │
          ▼                     ▼                        ▼
┌─────────────────────┐ ┌────────────────────┐ ┌─────────────────────────────┐
│ProcessOptimization  │ │ ProductionOptimizer│ │ BatchParameterEstimator     │
│Engine               │ │                    │ │ (model calibration)         │
│                     │ │• Custom objectives │ │                             │
│• findMaxThroughput()│ │• Multi-variable    │ │• Levenberg-Marquardt        │
│• evaluateConstraint │ │• Pareto multi-obj  │ │• Parameter fitting          │
│• generateLiftCurve()│ │• Parallel eval     │ │• Uncertainty quantification │
└──────────┬──────────┘ └─────────┬──────────┘ └──────────────────────────────┘
           │                      │
           │    ┌─────────────────┘
           │    │
           ▼    ▼
    ┌──────────────────────────────────────────┐
    │            ProcessSystem                  │
    │  (contains equipment, streams, recycles) │
    │                                          │
    │  process.run() → converged state         │
    │  process.getUnit("name") → equipment     │
    └────────────────┬─────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │   Equipment Capacity Strategy Registry   │
    │                                          │
    │  CompressorCapacityStrategy              │
    │  SeparatorCapacityStrategy               │
    │  PumpCapacityStrategy                    │
    │  ... (extensible plugin system)          │
    └────────────────┬─────────────────────────┘
                     │
                     ▼
    ┌──────────────────────────────────────────┐
    │         CapacityConstraint               │
    │                                          │
    │  • name, unit, type                      │
    │  • designValue, maxValue                 │
    │  • getUtilization() → 0.0 to 1.0+        │
    │  • severity (HARD/SOFT)                  │
    └──────────────────────────────────────────┘
```

---

## Key Concepts

### Equipment Capacity Constraints

Equipment constraints define operating limits. Each equipment type has a strategy that extracts constraints:

| Equipment | Typical Constraints |
|-----------|---------------------|
| Compressor | Surge margin, max power, operating envelope, speed limits |
| Separator | Liquid level, residence time, gas/liquid capacity |
| Pump | NPSH margin, max power, flow limits |
| Pipe | Erosional velocity, pressure drop |
| Valve | Cv capacity, choke conditions |

> **⚠️ Important**: Most equipment constraints are **disabled by default** for backward compatibility. The optimizer automatically falls back to traditional capacity methods (`getCapacityMax()`/`getCapacityDuty()`) when no enabled constraints exist. To use multi-constraint capacity analysis, you must explicitly enable constraints:
> 
> ```java
> separator.useEquinorConstraints();  // Enable Equinor TR3500 constraints
> // OR
> separator.enableConstraints();       // Enable all constraints
> ```
> 
> See [Capacity Constraint Framework - Constraints Disabled by Default](../CAPACITY_CONSTRAINT_FRAMEWORK#important-constraints-disabled-by-default) for details.

### Utilization Ratio

The **utilization ratio** is the key metric:

$$\text{utilization} = \frac{\text{actual value}}{\text{design limit}}$$

- `0.0` = not used
- `1.0` = at design limit
- `> 1.0` = exceeds limit (constraint violation)

### Bottleneck Detection

The **bottleneck** is the equipment with the highest utilization ratio:

```java
String bottleneck = engine.findBottleneckEquipment();
// Returns equipment name with highest utilization
```

---

## Search Algorithms

Both optimizers support multiple search algorithms:

| Algorithm | Best For | Convergence | Notes |
|-----------|----------|-------------|-------|
| **Binary Search** | Monotonic problems | Fast | Assumes feasibility is monotonic |
| **Golden Section** | Single variable, non-monotonic | Moderate | Robust, doesn't require derivatives |
| **Nelder-Mead** | Multi-variable (2-10 vars) | Moderate | No gradients needed |
| **PSO (Particle Swarm)** | Global search, many local optima | Slow | Good for non-convex problems |
| **Gradient Descent** | Smooth multi-variable (5-20+) | Fast | **New (Jan 2026)** - Finite-difference gradients |
| **BFGS** | Smooth functions | Fast | Requires gradient approximation |

### ProcessOptimizationEngine Algorithms

```java
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);
engine.setSearchAlgorithm(SearchAlgorithm.BFGS);
engine.setSearchAlgorithm(SearchAlgorithm.GRADIENT_ACCELERATED);
```

### ProductionOptimizer Algorithms

```java
config.searchMode(SearchMode.BINARY_FEASIBILITY);
config.searchMode(SearchMode.GOLDEN_SECTION_SCORE);
config.searchMode(SearchMode.NELDER_MEAD_SCORE);
config.searchMode(SearchMode.PARTICLE_SWARM_SCORE);
config.searchMode(SearchMode.GRADIENT_DESCENT_SCORE);  // New (Jan 2026)
```

> **January 2026 Update:** ProductionOptimizer now includes `GRADIENT_DESCENT_SCORE` algorithm, configuration validation, stagnation detection, warm start, bounded LRU cache, and infeasibility diagnostics. See [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) for details.

---

## Python Usage via JPype

Both optimizers work seamlessly from Python using neqsim-python:

### ProcessOptimizationEngine from Python

```python
from neqsim.neqsimpython import jneqsim

# Get classes
ProcessOptimizationEngine = jneqsim.process.util.optimizer.ProcessOptimizationEngine
SearchAlgorithm = ProcessOptimizationEngine.SearchAlgorithm

# Create and configure
engine = ProcessOptimizationEngine(process)
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION)

# Find max throughput
result = engine.findMaximumThroughput(50.0, 10.0, 1000.0, 100000.0)
print(f"Max flow: {result.getOptimalValue():.0f} kg/hr")
print(f"Bottleneck: {result.getBottleneck()}")
```

### ProductionOptimizer from Python

```python
from neqsim.neqsimpython import jneqsim
from jpype import JImplements, JOverride

# Get classes
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
OptimizationConfig = ProductionOptimizer.OptimizationConfig
OptimizationObjective = ProductionOptimizer.OptimizationObjective
SearchMode = ProductionOptimizer.SearchMode

# Define objective function as Java interface
@JImplements("java.util.function.ToDoubleFunction")
class ThroughputObjective:
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("outlet").getFlowRate("kg/hr")

# Configure and run
optimizer = ProductionOptimizer()
config = OptimizationConfig(50000.0, 200000.0) \
    .tolerance(100.0) \
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)

objectives = [
    OptimizationObjective("throughput", ThroughputObjective(), 1.0)
]

result = optimizer.optimize(process, feed, config, objectives, None)
print(f"Optimal rate: {result.getOptimalRate():.0f} kg/hr")
```

---

## Complete Examples

### Example 1: Find Maximum Compressor Throughput

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create gas system
SystemInterface gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

// Build process
Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000, "kg/hr");
feed.setPressure(50.0, "bara");

Compressor compressor = new Compressor("comp", feed);
compressor.setOutletPressure(100.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(compressor);
process.run();

// Find maximum throughput
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setFeedStreamName("feed");
engine.setSearchAlgorithm(SearchAlgorithm.GOLDEN_SECTION);

OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure
    100.0,     // outlet pressure  
    10000.0,   // min flow
    200000.0   // max flow
);

System.out.println("Maximum throughput: " + result.getOptimalValue() + " kg/hr");
System.out.println("Limited by: " + result.getBottleneck());
```

### Example 2: Multi-Objective Pareto Optimization

```java
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.*;

ProductionOptimizer optimizer = new ProductionOptimizer();

// Define competing objectives
List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective("throughput",
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"),
        1.0, ObjectiveType.MAXIMIZE),
    new OptimizationObjective("power",
        proc -> ((Compressor) proc.getUnit("comp")).getPower("kW"),
        1.0, ObjectiveType.MINIMIZE)
);

// Configure Pareto optimization
OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .paretoGridSize(20)  // 20 weight combinations
    .tolerance(100.0);

// Generate Pareto front
ParetoResult pareto = optimizer.optimizePareto(process, feed, config, objectives);

System.out.println("Pareto front has " + pareto.getPoints().size() + " solutions");
for (ParetoPoint point : pareto.getPoints()) {
    System.out.printf("Flow: %.0f kg/hr, Power: %.0f kW%n",
        point.getObjectives().get("throughput"),
        point.getObjectives().get("power"));
}
```

---

## YAML Specification Files

The `ProductionOptimizationSpecLoader` class allows loading optimization scenarios from YAML or JSON files, enabling configuration-driven optimization workflows.

### YAML Format

```yaml
scenarios:
  - name: "MaxThroughput"
    process: "myProcess"           # Key in processes map
    feedStream: "wellFeed"         # Key in feeds map
    lowerBound: 50000.0
    upperBound: 200000.0
    rateUnit: "kg/hr"
    tolerance: 100.0
    maxIterations: 30
    searchMode: "GOLDEN_SECTION_SCORE"
    utilizationMarginFraction: 0.05
    
    objectives:
      - name: "throughput"
        weight: 1.0
        type: "MAXIMIZE"
        metric: "throughputMetric"   # Key in metrics map
    
    constraints:
      - name: "maxPower"
        metric: "powerMetric"
        limit: 5000.0
        direction: "LESS_THAN"
        severity: "HARD"
        description: "Compressor power limit"
```

### Loading YAML Specs in Java

```java
import neqsim.process.util.optimizer.ProductionOptimizationSpecLoader;

// Create registries mapping spec keys to objects
Map<String, ProcessSystem> processes = new HashMap<>();
processes.put("myProcess", process);

Map<String, StreamInterface> feeds = new HashMap<>();
feeds.put("wellFeed", feed);

Map<String, ToDoubleFunction<ProcessSystem>> metrics = new HashMap<>();
metrics.put("throughputMetric", p -> p.getUnit("outlet").getFlowRate("kg/hr"));
metrics.put("powerMetric", p -> ((Compressor) p.getUnit("comp")).getPower("kW"));

// Load scenarios from YAML
List<ScenarioRequest> scenarios = ProductionOptimizationSpecLoader.load(
    Paths.get("optimization.yaml"), processes, feeds, metrics);

// Run each scenario
ProductionOptimizer optimizer = new ProductionOptimizer();
for (ScenarioRequest scenario : scenarios) {
    OptimizationResult result = optimizer.optimizeScenario(scenario);
    System.out.println(scenario.getName() + ": " + result.getOptimalRate());
}
```

---

## Class Summary

| Class | Purpose | Key Method | Documentation |
|-------|---------|------------|---------------|
| `ProcessOptimizationEngine` | Throughput-focused optimization | `findMaximumThroughput()` | [Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) |
| `ProductionOptimizer` | General-purpose optimization | `optimize()`, `optimizePareto()` | [Production Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) |
| `FlowRateOptimizer` | Flow rate for pressure boundaries | `findMaxFlowRate()` | [Flow Rate Optimization](flow-rate-optimization) |
| `MultiObjectiveOptimizer` | Pareto front generation | `optimize()` | [Multi-Objective](multi-objective-optimization) |
| `BatchStudy` | Parallel parameter sweeps | `run()` | [Batch Studies](batch-studies) |
| `ProcessConstraintEvaluator` | Constraint evaluation | `evaluate()` | [Capacity Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) |
| `ProcessSimulationEvaluator` | External optimizer interface | `evaluate()` | [External Integration](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) |
| `EclipseVFPExporter` | Eclipse VFP tables | `exportVFPPROD()` | [Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE#eclipsevfpexporter) |
| `LiftCurveGenerator` | Lift curve tables | `generateLiftCurve()` | [Flow Rate Optimization](flow-rate-optimization) |
| `BatchParameterEstimator` | Model calibration | `solve()` | [README.md](./ |
| `ProductionOptimizationSpecLoader` | YAML/JSON config loading | `load()` | [YAML Format](#yaml-specification-files) |

---

## Decision Guide

Choose based on your use case:
- **Max throughput at pressures** → `ProcessOptimizationEngine`
- **Custom objectives/multi-variable** → `ProductionOptimizer`
- **Model calibration** → `BatchParameterEstimator`
