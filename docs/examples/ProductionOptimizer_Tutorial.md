---
layout: default
title: "ProductionOptimizer Tutorial"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# ProductionOptimizer Tutorial

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`ProductionOptimizer_Tutorial.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb).

---

# ProductionOptimizer - Comprehensive Tutorial

This notebook provides a complete guide to using the `ProductionOptimizer` class from NeqSim for process optimization.

## Table of Contents

1. [Introduction](#1-introduction)
2. [Setup and Imports](#2-setup-and-imports)
3. [Search Algorithms](#3-search-algorithms)
4. [Single-Variable Optimization](#4-single-variable-optimization)
5. [Multi-Variable Optimization](#5-multi-variable-optimization)
6. [Objectives and Constraints](#6-objectives-and-constraints)
7. [Pareto Multi-Objective Optimization](#7-pareto-multi-objective-optimization)
8. [Configuration Options](#8-configuration-options)
9. [Advanced Usage](#9-advanced-usage)
10. [Best Practices](#10-best-practices)

## 1. Introduction

The `ProductionOptimizer` is a general-purpose optimization utility for NeqSim process models. It supports:

| Feature | Description |
|---------|-------------|
| **Single-variable optimization** | Optimize flow rate with a single feed stream |
| **Multi-variable optimization** | Optimize multiple decision variables simultaneously |
| **Multiple search algorithms** | Binary, Golden-Section, Nelder-Mead, Particle Swarm |
| **Custom objectives** | Maximize throughput, minimize power, or any custom metric |
| **Hard and soft constraints** | Equipment limits with penalty functions |
| **Pareto optimization** | Multi-objective trade-off analysis |
| **Parallel evaluation** | Evaluate scenarios in parallel |

### When to Use ProductionOptimizer vs ProcessOptimizationEngine

| Scenario | Use |
|----------|-----|
| Find max throughput at fixed pressures | `ProcessOptimizationEngine` |
| Custom objective function | `ProductionOptimizer` |
| Multiple decision variables | `ProductionOptimizer` |
| Pareto multi-objective | `ProductionOptimizer` |
| Equipment bottleneck detection | Either (both support it) |

## 2. Setup and Imports

```python
# Import neqsim-python (ensure neqsim is installed: pip install neqsim)
from neqsim.neqsimpython import jneqsim
from jpype import JImplements, JOverride, JArray, JDouble
import jpype

# Import Java classes
ProductionOptimizer = jneqsim.process.util.optimizer.ProductionOptimizer
OptimizationConfig = ProductionOptimizer.OptimizationConfig
OptimizationObjective = ProductionOptimizer.OptimizationObjective
OptimizationConstraint = ProductionOptimizer.OptimizationConstraint
ManipulatedVariable = ProductionOptimizer.ManipulatedVariable
SearchMode = ProductionOptimizer.SearchMode
ObjectiveType = ProductionOptimizer.ObjectiveType
ConstraintDirection = ProductionOptimizer.ConstraintDirection
ConstraintSeverity = ProductionOptimizer.ConstraintSeverity

# Process equipment imports
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Separator = jneqsim.process.equipment.separator.Separator
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

# Thermo imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

print("NeqSim ProductionOptimizer loaded successfully!")
print(f"Available search modes: {[str(m) for m in SearchMode.values()]}")
```

## 3. Search Algorithms

ProductionOptimizer supports four search algorithms:

### 3.1 BINARY_FEASIBILITY
- **Best for**: Single-variable problems where feasibility is monotonic
- **How it works**: Binary search on flow rate, checking feasibility at each point
- **Convergence**: O(log n) - very fast
- **Limitations**: Assumes higher flow → more likely to violate constraints

### 3.2 GOLDEN_SECTION_SCORE
- **Best for**: Single-variable non-monotonic problems
- **How it works**: Golden-section search on composite score (feasibility + objective)
- **Convergence**: O(log n) iterations
- **Limitations**: Single variable only

### 3.3 NELDER_MEAD_SCORE
- **Best for**: Multi-variable optimization (2-10 variables)
- **How it works**: Simplex-based derivative-free optimization
- **Convergence**: Good for smooth objective landscapes
- **Limitations**: May get stuck in local optima

### 3.4 PARTICLE_SWARM_SCORE
- **Best for**: Global optimization with many local optima
- **How it works**: Swarm intelligence with particles exploring the search space
- **Convergence**: Slower but more global
- **Parameters**: swarmSize, inertiaWeight, cognitiveWeight, socialWeight

```python
# Display all search modes and their use cases
search_modes = {
    "BINARY_FEASIBILITY": {
        "variables": "1",
        "speed": "Fastest",
        "use_case": "Monotonic feasibility problems"
    },
    "GOLDEN_SECTION_SCORE": {
        "variables": "1",
        "speed": "Fast",
        "use_case": "Non-monotonic single variable"
    },
    "NELDER_MEAD_SCORE": {
        "variables": "2-10",
        "speed": "Medium",
        "use_case": "Multi-variable, smooth landscape"
    },
    "PARTICLE_SWARM_SCORE": {
        "variables": "Any",
        "speed": "Slow",
        "use_case": "Global search, many local optima"
    }
}

print("Search Algorithm Comparison:")
print("-" * 70)
print(f"{'Algorithm':<25} {'Variables':<10} {'Speed':<10} {'Use Case'}")
print("-" * 70)
for name, info in search_modes.items():
    print(f"{name:<25} {info['variables']:<10} {info['speed']:<10} {info['use_case']}")
```

## 4. Single-Variable Optimization

The simplest case: optimize flow rate of a single feed stream.

```python
# Create a simple gas compression process
def create_compression_process():
    """Create a simple gas compression system for optimization."""
    # Create gas fluid
    gas = SystemSrkEos(288.15, 50.0)  # 15°C, 50 bara
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.10)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")
    
    # Create process
    process = ProcessSystem()
    
    # Feed stream
    feed = Stream("feed", gas)
    feed.setFlowRate(50000.0, "kg/hr")
    feed.setPressure(50.0, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    # Compressor
    compressor = Compressor("compressor", feed)
    compressor.setOutletPressure(100.0)  # bara
    compressor.setPolytropicEfficiency(0.78)
    process.add(compressor)
    
    # Aftercooler
    cooler = Cooler("cooler", compressor.getOutletStream())
    cooler.setOutTemperature(313.15)  # 40°C
    process.add(cooler)
    
    process.run()
    return process, feed

# Create the process
process, feed = create_compression_process()
print(f"Initial flow rate: {feed.getFlowRate('kg/hr'):.0f} kg/hr")
print(f"Compressor power: {process.getUnit('compressor').getPower('kW'):.1f} kW")
```

```python
# Single-variable optimization: maximize flow rate

# Configure optimization
config = OptimizationConfig(10000.0, 200000.0)  # Flow bounds: 10,000 - 200,000 kg/hr
config = config.tolerance(100.0)                 # Convergence tolerance
config = config.maxIterations(30)                # Max iterations
config = config.searchMode(SearchMode.GOLDEN_SECTION_SCORE)
config = config.defaultUtilizationLimit(0.95)    # 95% max equipment utilization

# Create optimizer and run
optimizer = ProductionOptimizer()
result = optimizer.optimize(process, feed, config, None, None)

# Display results
print("\n=== Single-Variable Optimization Results ===")
print(f"Optimal flow rate: {result.getOptimalRate():.0f} kg/hr")
print(f"Feasible: {result.isFeasible()}")
print(f"Iterations: {result.getIterations()}")
if result.getBottleneck():
    print(f"Bottleneck: {result.getBottleneck().getName()}")
```

## 5. Multi-Variable Optimization

For complex processes, you often need to optimize multiple variables simultaneously. Use `ManipulatedVariable` to define each decision variable.

```python
# Create a more complex two-stage compression process
def create_two_stage_process():
    """Create a two-stage compression system with intercooling."""
    gas = SystemSrkEos(288.15, 30.0)
    gas.addComponent("methane", 0.85)
    gas.addComponent("ethane", 0.10)
    gas.addComponent("propane", 0.05)
    gas.setMixingRule("classic")
    
    process = ProcessSystem()
    
    # Feed
    feed = Stream("feed", gas)
    feed.setFlowRate(50000.0, "kg/hr")
    feed.setPressure(30.0, "bara")
    feed.setTemperature(288.15, "K")
    process.add(feed)
    
    # Stage 1 compressor
    stage1 = Compressor("stage1", feed)
    stage1.setOutletPressure(70.0)
    stage1.setPolytropicEfficiency(0.78)
    process.add(stage1)
    
    # Intercooler
    intercooler = Cooler("intercooler", stage1.getOutletStream())
    intercooler.setOutTemperature(308.15)  # 35°C
    process.add(intercooler)
    
    # Stage 2 compressor
    stage2 = Compressor("stage2", intercooler.getOutletStream())
    stage2.setOutletPressure(150.0)
    stage2.setPolytropicEfficiency(0.76)
    process.add(stage2)
    
    # Aftercooler
    aftercooler = Cooler("aftercooler", stage2.getOutletStream())
    aftercooler.setOutTemperature(313.15)  # 40°C
    process.add(aftercooler)
    
    process.run()
    return process

process2 = create_two_stage_process()
print("Two-stage process created")
print(f"Stage 1 power: {process2.getUnit('stage1').getPower('kW'):.1f} kW")
print(f"Stage 2 power: {process2.getUnit('stage2').getPower('kW'):.1f} kW")
```

```python
# Define setter classes for ManipulatedVariable (JPype interface implementation)

@JImplements("neqsim.process.util.optimizer.ProductionOptimizer$Setter")
class FlowRateSetter:
    """Sets the feed flow rate."""
    @JOverride
    def apply(self, proc, value):
        proc.getUnit("feed").setFlowRate(float(value), "kg/hr")

@JImplements("neqsim.process.util.optimizer.ProductionOptimizer$Setter")
class Stage1PressureSetter:
    """Sets stage 1 outlet pressure."""
    @JOverride
    def apply(self, proc, value):
        proc.getUnit("stage1").setOutletPressure(float(value))

@JImplements("neqsim.process.util.optimizer.ProductionOptimizer$Setter")
class IntercoolerTempSetter:
    """Sets intercooler outlet temperature."""
    @JOverride
    def apply(self, proc, value):
        proc.getUnit("intercooler").setOutTemperature(float(value) + 273.15)  # °C to K

print("Setter classes defined for multi-variable optimization")
```

```python
# Create ManipulatedVariable list
from java.util import ArrayList

variables = ArrayList()

# Variable 1: Flow rate (10,000 - 150,000 kg/hr)
var_flow = ManipulatedVariable("flowRate", 10000.0, 150000.0, "kg/hr", FlowRateSetter())
variables.add(var_flow)

# Variable 2: Stage 1 outlet pressure (50 - 90 bara)
var_p1 = ManipulatedVariable("stage1Pressure", 50.0, 90.0, "bara", Stage1PressureSetter())
variables.add(var_p1)

# Variable 3: Intercooler temperature (25 - 45 °C)
var_temp = ManipulatedVariable("intercoolerTemp", 25.0, 45.0, "C", IntercoolerTempSetter())
variables.add(var_temp)

print(f"Defined {variables.size()} manipulated variables:")
for i in range(variables.size()):
    v = variables.get(i)
    print(f"  {i+1}. {v.getName()}: [{v.getLowerBound()}, {v.getUpperBound()}] {v.getUnit()}")
```

```python
# Multi-variable optimization with Nelder-Mead

# Configure for multi-variable
config_multi = OptimizationConfig(10000.0, 150000.0)
config_multi = config_multi.searchMode(SearchMode.NELDER_MEAD_SCORE)  # Best for multi-variable
config_multi = config_multi.maxIterations(100)
config_multi = config_multi.tolerance(50.0)
config_multi = config_multi.defaultUtilizationLimit(0.95)

# Run optimization
optimizer2 = ProductionOptimizer()
result_multi = optimizer2.optimize(process2, variables, config_multi, None, None)

print("\n=== Multi-Variable Optimization Results ===")
print(f"Optimal flow rate: {result_multi.getOptimalRate():.0f} kg/hr")
print(f"Feasible: {result_multi.isFeasible()}")
print(f"Iterations: {result_multi.getIterations()}")

# Get optimal variable values
opt_vars = result_multi.getOptimalVariables()
if opt_vars:
    print("\nOptimal variable values:")
    for name in opt_vars.keySet():
        print(f"  {name}: {opt_vars.get(name):.2f}")
```

## 6. Objectives and Constraints

### 6.1 Optimization Objectives

Objectives define what to optimize. You can:
- **MAXIMIZE**: throughput, production, revenue
- **MINIMIZE**: power, cost, emissions

### 6.2 Optimization Constraints

Constraints define limits that must be respected:
- **HARD constraints**: Must be satisfied (infeasible if violated)
- **SOFT constraints**: Penalized if violated (allows trade-offs)

```python
# Define objective evaluators

@JImplements("java.util.function.ToDoubleFunction")
class ThroughputEvaluator:
    """Evaluates throughput (outlet flow rate)."""
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("aftercooler").getOutletStream().getFlowRate("kg/hr")

@JImplements("java.util.function.ToDoubleFunction")
class TotalPowerEvaluator:
    """Evaluates total compressor power."""
    @JOverride
    def applyAsDouble(self, proc):
        power1 = proc.getUnit("stage1").getPower("kW")
        power2 = proc.getUnit("stage2").getPower("kW")
        return power1 + power2

@JImplements("java.util.function.ToDoubleFunction")
class Stage1PowerEvaluator:
    """Evaluates stage 1 power for constraint."""
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("stage1").getPower("kW")

@JImplements("java.util.function.ToDoubleFunction")
class OutletTempEvaluator:
    """Evaluates outlet temperature for constraint."""
    @JOverride
    def applyAsDouble(self, proc):
        return proc.getUnit("aftercooler").getOutletStream().getTemperature("C")

print("Objective and constraint evaluators defined")
```

```python
# Create objectives list
objectives = ArrayList()

# Objective 1: Maximize throughput (weight = 1.0)
obj_throughput = OptimizationObjective(
    "throughput",           # Name
    ThroughputEvaluator(),  # Evaluator function
    1.0,                    # Weight
    ObjectiveType.MAXIMIZE  # Direction
)
objectives.add(obj_throughput)

print(f"Defined {objectives.size()} objective(s):")
for i in range(objectives.size()):
    obj = objectives.get(i)
    print(f"  {i+1}. {obj.getName()} ({obj.getType()})")
```

```python
# Create constraints list
constraints = ArrayList()

# Constraint 1: Stage 1 power < 3000 kW (HARD)
con_power = OptimizationConstraint(
    "maxStage1Power",           # Name
    Stage1PowerEvaluator(),     # Evaluator
    3000.0,                     # Limit
    ConstraintDirection.LESS_THAN,
    ConstraintSeverity.HARD,
    0.0,                        # Penalty weight (for SOFT)
    "Stage 1 compressor power limit"
)
constraints.add(con_power)

# Constraint 2: Outlet temperature < 45°C (SOFT with penalty)
con_temp = OptimizationConstraint(
    "maxOutletTemp",
    OutletTempEvaluator(),
    45.0,
    ConstraintDirection.LESS_THAN,
    ConstraintSeverity.SOFT,
    100.0,                      # Penalty weight
    "Outlet temperature specification"
)
constraints.add(con_temp)

print(f"Defined {constraints.size()} constraint(s):")
for i in range(constraints.size()):
    con = constraints.get(i)
    print(f"  {i+1}. {con.getName()}: {con.getDirection()} {con.getLimit()} ({con.getSeverity()})")
```

```python
# Optimization with objectives and constraints

config_constrained = OptimizationConfig(10000.0, 150000.0)
config_constrained = config_constrained.searchMode(SearchMode.NELDER_MEAD_SCORE)
config_constrained = config_constrained.maxIterations(100)
config_constrained = config_constrained.tolerance(50.0)

# Recreate process (reset state)
process3 = create_two_stage_process()

# Run with objectives and constraints
result_constrained = optimizer2.optimize(process3, variables, config_constrained, objectives, constraints)

print("\n=== Constrained Optimization Results ===")
print(f"Optimal flow rate: {result_constrained.getOptimalRate():.0f} kg/hr")
print(f"Feasible: {result_constrained.isFeasible()}")

# Check constraint violations
violations = result_constrained.getConstraintViolations()
if violations and violations.size() > 0:
    print("\nConstraint violations:")
    for i in range(violations.size()):
        print(f"  - {violations.get(i)}")
else:
    print("\nAll constraints satisfied!")
```

## 7. Pareto Multi-Objective Optimization

When you have conflicting objectives (e.g., maximize throughput vs minimize power), use Pareto optimization to find the trade-off frontier.

```python
# Define competing objectives for Pareto optimization
pareto_objectives = ArrayList()

# Objective 1: Maximize throughput
obj1 = OptimizationObjective(
    "throughput",
    ThroughputEvaluator(),
    1.0,
    ObjectiveType.MAXIMIZE
)
pareto_objectives.add(obj1)

# Objective 2: Minimize total power
obj2 = OptimizationObjective(
    "totalPower",
    TotalPowerEvaluator(),
    1.0,
    ObjectiveType.MINIMIZE
)
pareto_objectives.add(obj2)

print("Pareto objectives defined:")
print("  1. Maximize throughput")
print("  2. Minimize total power")
print("\nThese objectives conflict: higher throughput requires more power.")
```

```python
# Configure Pareto optimization
config_pareto = OptimizationConfig(10000.0, 150000.0)
config_pareto = config_pareto.searchMode(SearchMode.GOLDEN_SECTION_SCORE)
config_pareto = config_pareto.paretoGridSize(15)  # Number of weight combinations
config_pareto = config_pareto.maxIterations(30)
config_pareto = config_pareto.tolerance(100.0)

# Recreate simple process for single-variable Pareto
process_pareto, feed_pareto = create_compression_process()

# Run Pareto optimization
pareto_result = optimizer.optimizePareto(process_pareto, feed_pareto, config_pareto, pareto_objectives)

print("\n=== Pareto Optimization Results ===")
points = pareto_result.getPoints()
print(f"Found {points.size()} Pareto-optimal solutions")
```

```python
# Extract and display Pareto front
import matplotlib.pyplot as plt

throughputs = []
powers = []

print("\nPareto Front Points:")
print("-" * 50)
print(f"{'Flow (kg/hr)':<15} {'Throughput (kg/hr)':<20} {'Power (kW)'}")
print("-" * 50)

for i in range(points.size()):
    point = points.get(i)
    obj_values = point.getObjectives()
    flow = point.getFlowRate()
    throughput = obj_values.get("throughput")
    power = obj_values.get("totalPower")
    
    throughputs.append(throughput)
    powers.append(power)
    print(f"{flow:<15.0f} {throughput:<20.0f} {power:.1f}")

# Plot Pareto front
plt.figure(figsize=(10, 6))
plt.scatter(throughputs, powers, c='blue', s=100, label='Pareto optimal')
plt.plot(throughputs, powers, 'b--', alpha=0.5)
plt.xlabel('Throughput (kg/hr)', fontsize=12)
plt.ylabel('Total Power (kW)', fontsize=12)
plt.title('Pareto Front: Throughput vs Power', fontsize=14)
plt.grid(True, alpha=0.3)
plt.legend()
plt.tight_layout()
plt.show()
```

## 8. Configuration Options

The `OptimizationConfig` class provides many configuration options:

```python
# All OptimizationConfig options
config_options = {
    "Basic Options": {
        "tolerance(double)": "Convergence tolerance (default: 1e-3)",
        "maxIterations(int)": "Maximum iterations (default: 30)",
        "searchMode(SearchMode)": "Search algorithm to use",
        "rateUnit(String)": "Unit for flow rate (default: 'kg/hr')",
    },
    "Equipment Constraints": {
        "defaultUtilizationLimit(double)": "Default max utilization for all equipment (0-1)",
        "utilizationLimitForType(Class, double)": "Utilization limit for specific equipment type",
        "utilizationMarginFraction(double)": "Safety margin on equipment limits",
    },
    "Uncertainty & Robustness": {
        "capacityUncertaintyFraction(double)": "Uncertainty in capacity estimates",
        "capacityPercentile(double)": "Percentile for capacity (0.5 = P50)",
    },
    "Pareto Options": {
        "paretoGridSize(int)": "Number of weight combinations for Pareto (default: 10)",
    },
    "Parallel Execution": {
        "parallelEvaluations(boolean)": "Enable parallel scenario evaluation",
        "parallelThreads(int)": "Number of threads for parallel execution",
    },
    "PSO Parameters": {
        "swarmSize(int)": "Number of particles (default: 8)",
        "inertiaWeight(double)": "Inertia weight (default: 0.6)",
        "cognitiveWeight(double)": "Cognitive (personal best) weight (default: 1.2)",
        "socialWeight(double)": "Social (global best) weight (default: 1.2)",
    },
    "Caching": {
        "enableCaching(boolean)": "Cache constraint evaluations (default: true)",
    },
    "Special Equipment": {
        "columnFsFactorLimit(double)": "Fs factor limit for distillation columns",
    }
}

print("OptimizationConfig Options:")
print("=" * 70)
for category, options in config_options.items():
    print(f"\n{category}:")
    print("-" * 70)
    for method, description in options.items():
        print(f"  .{method}")
        print(f"      {description}")
```

```python
# Example: Comprehensive configuration

comprehensive_config = OptimizationConfig(10000.0, 200000.0) \
    .rateUnit("kg/hr") \
    .tolerance(50.0) \
    .maxIterations(50) \
    .searchMode(SearchMode.NELDER_MEAD_SCORE) \
    .defaultUtilizationLimit(0.95) \
    .utilizationMarginFraction(0.05) \
    .capacityUncertaintyFraction(0.10) \
    .capacityPercentile(0.5) \
    .enableCaching(True) \
    .paretoGridSize(20)

print("Comprehensive configuration created")
print(f"  Bounds: [{comprehensive_config.getLowerBound()}, {comprehensive_config.getUpperBound()}]")
print(f"  Search mode: {comprehensive_config.getSearchMode()}")
print(f"  Max iterations: {comprehensive_config.getMaxIterations()}")
```

## 9. Advanced Usage

### 9.1 Scenario Evaluation

Evaluate multiple scenarios with different conditions:

```python
# Create multiple scenarios with different inlet pressures
ScenarioRequest = ProductionOptimizer.ScenarioRequest

scenarios = ArrayList()

# Base config
base_config = OptimizationConfig(10000.0, 150000.0) \
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE) \
    .maxIterations(20)

# Scenario 1: Low pressure
process_low, feed_low = create_compression_process()
feed_low.setPressure(40.0, "bara")
scenarios.add(ScenarioRequest("LowPressure", process_low, feed_low, base_config, None, None))

# Scenario 2: Medium pressure
process_med, feed_med = create_compression_process()
feed_med.setPressure(50.0, "bara")
scenarios.add(ScenarioRequest("MediumPressure", process_med, feed_med, base_config, None, None))

# Scenario 3: High pressure
process_high, feed_high = create_compression_process()
feed_high.setPressure(60.0, "bara")
scenarios.add(ScenarioRequest("HighPressure", process_high, feed_high, base_config, None, None))

print(f"Created {scenarios.size()} scenarios for evaluation")
```

```python
# Run all scenarios
results = optimizer.optimizeScenarios(scenarios)

print("\n=== Scenario Comparison ===")
print("-" * 60)
print(f"{'Scenario':<20} {'Optimal Flow (kg/hr)':<25} {'Feasible'}")
print("-" * 60)

for i in range(results.size()):
    result = results.get(i)
    scenario = scenarios.get(i)
    print(f"{scenario.getName():<20} {result.getOptimalRate():<25.0f} {result.isFeasible()}")
```

### 9.2 Parallel Scenario Evaluation

For large numbers of scenarios, enable parallel execution:

```python
# Configure for parallel execution
parallel_config = OptimizationConfig(10000.0, 150000.0) \
    .searchMode(SearchMode.GOLDEN_SECTION_SCORE) \
    .parallelEvaluations(True) \
    .parallelThreads(4)  # Use 4 threads

print("Parallel configuration:")
print(f"  Parallel enabled: {parallel_config.isParallelEvaluations()}")
print(f"  Threads: {parallel_config.getParallelThreads()}")
print("\nNote: Parallel execution is most beneficial for many scenarios (10+)")
```

### 9.3 JSON Export

Export results to JSON for further analysis:

```python
# Get JSON representation of results
import json

# Run a simple optimization
process_json, feed_json = create_compression_process()
simple_config = OptimizationConfig(10000.0, 150000.0).searchMode(SearchMode.GOLDEN_SECTION_SCORE)
simple_result = optimizer.optimize(process_json, feed_json, simple_config, None, None)

# Convert to JSON
json_str = simple_result.toJson()
result_dict = json.loads(str(json_str))

print("Optimization Result (JSON):")
print(json.dumps(result_dict, indent=2))
```

## 10. Best Practices

### Algorithm Selection

| Variables | Problem Type | Recommended Algorithm |
|-----------|--------------|----------------------|
| 1 | Monotonic feasibility | `BINARY_FEASIBILITY` |
| 1 | Non-monotonic | `GOLDEN_SECTION_SCORE` |
| 2-10 | Smooth landscape | `NELDER_MEAD_SCORE` |
| Any | Many local optima | `PARTICLE_SWARM_SCORE` |

### Configuration Tips

1. **Start with coarse tolerance**, then refine
2. **Use `defaultUtilizationLimit(0.95)`** to leave 5% safety margin
3. **Enable caching** for repeated evaluations
4. **For Pareto**, use `paretoGridSize(15-25)` for good resolution

### Constraint Design

1. **Use HARD constraints** for safety-critical limits
2. **Use SOFT constraints** for operational preferences
3. **Set appropriate penalty weights** for soft constraints

### Debugging

1. Check `result.isFeasible()` first
2. Examine `result.getConstraintViolations()` for failures
3. Use `result.getIterations()` to check convergence
4. Enable verbose logging in Java if needed

```python
# Summary: Complete optimization workflow

def run_production_optimization(process, feed, objectives=None, constraints=None, 
                                 search_mode=SearchMode.GOLDEN_SECTION_SCORE,
                                 min_flow=10000, max_flow=200000):
    """
    Complete production optimization workflow.
    
    Parameters:
    - process: ProcessSystem to optimize
    - feed: Feed stream (or list of ManipulatedVariable for multi-var)
    - objectives: List of OptimizationObjective (optional)
    - constraints: List of OptimizationConstraint (optional)
    - search_mode: SearchMode enum
    - min_flow, max_flow: Flow rate bounds
    
    Returns:
    - OptimizationResult
    """
    # Configure
    config = OptimizationConfig(float(min_flow), float(max_flow)) \
        .searchMode(search_mode) \
        .tolerance(100.0) \
        .maxIterations(50) \
        .defaultUtilizationLimit(0.95) \
        .enableCaching(True)
    
    # Optimize
    optimizer = ProductionOptimizer()
    result = optimizer.optimize(process, feed, config, objectives, constraints)
    
    # Report
    print(f"Optimal flow: {result.getOptimalRate():.0f} kg/hr")
    print(f"Feasible: {result.isFeasible()}")
    print(f"Iterations: {result.getIterations()}")
    
    if result.getBottleneck():
        print(f"Bottleneck: {result.getBottleneck().getName()}")
    
    return result

# Example usage
print("=== Final Example ===")
final_process, final_feed = create_compression_process()
final_result = run_production_optimization(final_process, final_feed)
```

## Summary

The `ProductionOptimizer` provides:

✅ **Four search algorithms** for different problem types  
✅ **Single and multi-variable** optimization  
✅ **Custom objectives** (maximize/minimize anything)  
✅ **Hard and soft constraints** with penalties  
✅ **Pareto multi-objective** optimization  
✅ **Parallel scenario** evaluation  
✅ **Equipment utilization** tracking  
✅ **JSON export** for analysis  

For more details, see:
- [OPTIMIZATION_OVERVIEW.md](../process/optimization/OPTIMIZATION_OVERVIEW.md)
- [PRODUCTION_OPTIMIZATION_GUIDE.md](PRODUCTION_OPTIMIZATION_GUIDE.md)
- [multi-objective-optimization.md](../process/optimization/multi-objective-optimization.md)

