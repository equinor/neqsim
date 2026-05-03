---
title: Getting Started with Process Optimization
description: Step-by-step getting-started guide for optimizing ProcessSystem and ProcessModel in NeqSim.
---

# Getting Started with Process Optimization

This page is a practical starting point for process optimization in NeqSim.

## 1) Choose optimization API

| Use case | API |
|---|---|
| Maximize throughput at fixed inlet/outlet pressure | `ProcessOptimizationEngine` |
| Custom objective and/or multiple decision variables | `ProductionOptimizer` |
| Pareto optimization (throughput vs. power, etc.) | `MultiObjectiveOptimizer` |
| Constrained NLP (equality + inequality) | `SQPoptimizer` |
| Automate set/get variables by string addresses | `ProcessAutomation` |

## 2) Build and run a base process first

```java
ProcessSystem process = new ProcessSystem();
// ... add streams/equipment
process.run();
```

Always run the base case before optimization.

## 3) Throughput optimization with `ProcessOptimizationEngine`

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.util.optimizer.ProcessOptimizationEngine.OptimizationResult;

ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

OptimizationResult result = engine.findMaximumThroughput(
    50.0,    // inlet pressure [bara]
    10.0,    // outlet pressure [bara]
    1000.0,  // min flow [kg/hr]
    100000.0 // max flow [kg/hr]
);

System.out.println("Max flow: " + result.getOptimalValue() + " kg/hr");
```

## 4) Custom optimization with `ProductionOptimizer`

```java
import java.util.Arrays;
import java.util.List;
import neqsim.process.util.optimizer.ProductionOptimizer;
import neqsim.process.util.optimizer.ProductionOptimizer.ObjectiveType;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationConfig;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationObjective;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.process.util.optimizer.ProductionOptimizer.SearchMode;

ProductionOptimizer optimizer = new ProductionOptimizer();

OptimizationConfig config = new OptimizationConfig(50000.0, 200000.0)
    .tolerance(100.0)
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE)
    .maxIterations(30);

List<OptimizationObjective> objectives = Arrays.asList(
    new OptimizationObjective(
        "throughput",
        proc -> proc.getUnit("outlet").getFlowRate("kg/hr"),
        1.0,
        ObjectiveType.MAXIMIZE)
);

OptimizationResult result = optimizer.optimize(process, feed, config, objectives, null);
System.out.println("Optimal rate: " + result.getOptimalRate() + " kg/hr");
```

## 5) ProcessModel and automation loop

Use `ProcessAutomation` for robust, string-addressed optimization loops.

```java
import neqsim.process.automation.ProcessAutomation;

ProcessAutomation auto = process.getAutomation();
auto.setVariableValue("Compressor.outletPressure", 120.0, "bara");
process.run();
double powerMW = auto.getVariableValue("Compressor.power", "MW");
```

## 6) Common workflow (recommended)

1. Run: `process.run()`.
2. (Optional) auto-size equipment.
3. Run again after sizing.
4. Optimize.
5. Validate constraints and bottlenecks.

## 7) Next reading

- [Optimization Overview](OPTIMIZATION_OVERVIEW)
- [Optimization & Constraints Guide](OPTIMIZATION_AND_CONSTRAINTS)
- [Constraint Framework](constraint-framework)
- [Multi-Objective Optimization](multi-objective-optimization)
- [SQP Optimizer](sqp_optimizer)
