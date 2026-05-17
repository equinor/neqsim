---
layout: default
title: "GraphBasedProcessSimulation"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# GraphBasedProcessSimulation

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`GraphBasedProcessSimulation.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/GraphBasedProcessSimulation.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/GraphBasedProcessSimulation.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/GraphBasedProcessSimulation.ipynb).

---

## Setup

First, import NeqSim using jpype for direct Java access.

```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim

# Import Java classes through the jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
ProcessGraph = jneqsim.process.processmodel.graph.ProcessGraph
ProcessGraphBuilder = jneqsim.process.processmodel.graph.ProcessGraphBuilder
Stream = jneqsim.process.equipment.stream.Stream
Heater = jneqsim.process.equipment.heatexchanger.Heater
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
Separator = jneqsim.process.equipment.separator.Separator
Splitter = jneqsim.process.equipment.splitter.Splitter
Mixer = jneqsim.process.equipment.mixer.Mixer
Compressor = jneqsim.process.equipment.compressor.Compressor
ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve

print("NeqSim loaded successfully!")
```

## 1. Basic Graph Construction

Let's create a simple process and examine its graph structure.

```python
# Create a natural gas fluid
def create_natural_gas():
    fluid = SystemSrkEos(298.0, 50.0)
    fluid.addComponent("methane", 0.85)
    fluid.addComponent("ethane", 0.08)
    fluid.addComponent("propane", 0.04)
    fluid.addComponent("n-butane", 0.02)
    fluid.addComponent("nitrogen", 0.01)
    fluid.setMixingRule("classic")
    return fluid

# Build a simple process
process = ProcessSystem("Simple Gas Processing")

# Feed stream
feed = Stream("feed", create_natural_gas())
feed.setFlowRate(10000, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(50.0, "bara")
process.add(feed)

# Heater
heater = Heater("heater", feed)
heater.setOutTemperature(350.0)
process.add(heater)

# Separator
separator = Separator("separator", heater.getOutletStream())
process.add(separator)

# Build the graph
graph = process.buildGraph()

print("=" * 50)
print("PROCESS GRAPH ANALYSIS")
print("=" * 50)
print(f"Nodes (equipment): {graph.getNodeCount()}")
print(f"Edges (streams):   {graph.getEdgeCount()}")
print(f"Has cycles:        {graph.hasCycles()}")
print()
print("Calculation Order (topological sort):")
for i, unit in enumerate(graph.getCalculationOrder()):
    print(f"  {i+1}. {unit.getName()}")
```

## 2. Graph Structure Visualization

Let's examine the node and edge details.

```python
print("NODE DETAILS:")
print("-" * 60)
print(f"{'Name':<20} {'Type':<20} {'In':>5} {'Out':>5} {'Source':>7} {'Sink':>5}")
print("-" * 60)

for unit in process.getUnitOperations():
    node = graph.getNode(unit)
    print(f"{node.getName():<20} {node.getEquipmentType():<20} "
          f"{node.getIncomingEdges().size():>5} {node.getOutgoingEdges().size():>5} "
          f"{str(node.isSource()):>7} {str(node.isSink()):>5}")

print()
print("EDGE DETAILS:")
print("-" * 60)
print(f"{'From':<20} {'To':<20} {'Stream':<20}")
print("-" * 60)

for edge in graph.getEdges():
    print(f"{edge.getSource().getName():<20} {edge.getTarget().getName():<20} {edge.getName():<20}")
```

## 3. Parallel Execution Analysis

Let's create a process with multiple independent branches and analyze its parallelization potential.

```python
# Create a process with parallel branches
parallel_process = ProcessSystem("Parallel Processing Plant")

# Create 4 independent processing trains
for i in range(1, 5):
    fluid = create_natural_gas()
    
    # Feed
    feed = Stream(f"feed_{i}", fluid)
    feed.setFlowRate(5000, "kg/hr")
    feed.setTemperature(25.0, "C")
    feed.setPressure(50.0, "bara")
    parallel_process.add(feed)
    
    # Heater
    heater = Heater(f"heater_{i}", feed)
    heater.setOutTemperature(350.0)
    parallel_process.add(heater)
    
    # Separator
    sep = Separator(f"separator_{i}", heater.getOutletStream())
    parallel_process.add(sep)

# Analyze parallelization
print("=" * 50)
print("PARALLEL EXECUTION ANALYSIS")
print("=" * 50)
print(f"Total equipment: {parallel_process.getUnitOperations().size()}")
print(f"Parallel beneficial: {parallel_process.isParallelExecutionBeneficial()}")
print()

partition = parallel_process.getParallelPartition()
print(f"Parallel levels: {partition.getLevelCount()}")
print(f"Max parallelism: {partition.getMaxParallelism()}")
print()

print("Execution levels (units at same level run in parallel):")
for level_idx, level in enumerate(partition.getLevels()):
    units = [node.getName() for node in level]
    print(f"  Level {level_idx}: {', '.join(units)}")
```

## 4. Performance Comparison

Compare sequential vs parallel execution times.

```python
import time

# Warm up
parallel_process.run()

# Benchmark sequential execution
n_runs = 10
start = time.time()
for _ in range(n_runs):
    parallel_process.run()
seq_time = (time.time() - start) / n_runs * 1000  # ms

# Benchmark parallel execution
start = time.time()
for _ in range(n_runs):
    parallel_process.runParallel()
par_time = (time.time() - start) / n_runs * 1000  # ms

# Benchmark runOptimal (auto-select)
start = time.time()
for _ in range(n_runs):
    parallel_process.runOptimal()
opt_time = (time.time() - start) / n_runs * 1000  # ms

print("=" * 50)
print("PERFORMANCE COMPARISON")
print("=" * 50)
print(f"Sequential run():      {seq_time:.2f} ms")
print(f"Parallel runParallel(): {par_time:.2f} ms")
print(f"Auto runOptimal():      {opt_time:.2f} ms")
print()
print(f"Speedup (parallel vs sequential): {seq_time/par_time:.2f}x")
```

## 5. Complex Process with Splitter and Mixer

Demonstrate graph analysis for a process with branching and merging streams.

```python
# Create a more complex process
complex_process = ProcessSystem("Gas Processing with Split/Mix")

# Feed
feed = Stream("feed", create_natural_gas())
feed.setFlowRate(10000, "kg/hr")
feed.setTemperature(25.0, "C")
feed.setPressure(60.0, "bara")
complex_process.add(feed)

# Initial heating
preheater = Heater("preheater", feed)
preheater.setOutTemperature(320.0)
complex_process.add(preheater)

# Split into two branches
splitter = Splitter("splitter", preheater.getOutletStream())
splitter.setSplitFactors(JArray(JDouble)([0.6, 0.4]))
complex_process.add(splitter)

# Branch 1: Further heating
heater1 = Heater("heater_branch1", splitter.getSplitStream(0))
heater1.setOutTemperature(380.0)
complex_process.add(heater1)

# Branch 2: Cooling
cooler2 = Cooler("cooler_branch2", splitter.getSplitStream(1))
cooler2.setOutTemperature(280.0)
complex_process.add(cooler2)

# Merge branches
mixer = Mixer("mixer")
mixer.addStream(heater1.getOutletStream())
mixer.addStream(cooler2.getOutletStream())
complex_process.add(mixer)

# Final separation
final_sep = Separator("final_separator", mixer.getOutletStream())
complex_process.add(final_sep)

# Analyze
graph = complex_process.buildGraph()
partition = graph.partitionForParallelExecution()

print("=" * 50)
print("COMPLEX PROCESS ANALYSIS")
print("=" * 50)
print(f"Equipment: {graph.getNodeCount()}")
print(f"Streams:   {graph.getEdgeCount()}")
print(f"Has cycles: {graph.hasCycles()}")
print()

print("Parallel execution levels:")
for level_idx, level in enumerate(partition.getLevels()):
    units = [node.getName() for node in level]
    marker = " ← parallel!" if len(units) > 1 else ""
    print(f"  Level {level_idx}: {', '.join(units)}{marker}")

# Run
complex_process.runOptimal()
print()
print(f"Final separator gas out: {final_sep.getGasOutStream().getFlowRate('kg/hr'):.1f} kg/hr")
print(f"Final separator liq out: {final_sep.getLiquidOutStream().getFlowRate('kg/hr'):.1f} kg/hr")
```

## 6. Graph Summary and Validation

Use the built-in summary and validation features.

```python
# Get comprehensive summary
print("=" * 50)
print("GRAPH SUMMARY")
print("=" * 50)
print(graph.getSummary())

# Validate graph
print("=" * 50)
print("VALIDATION")
print("=" * 50)
issues = graph.validate()
if issues.isEmpty():
    print("✓ No issues found - graph is valid")
else:
    print("Issues found:")
    for issue in issues:
        print(f"  - {issue}")
```

## 7. Recommended Usage Pattern

Here's the recommended way to use graph-based execution in your simulations.

```python
def run_process_optimally(process):
    """
    Run a process using the optimal execution strategy.
    
    This function:
    1. Validates the process graph
    2. Reports parallelization potential
    3. Runs with the best strategy
    """
    # Build and validate graph
    graph = process.buildGraph()
    issues = graph.validate()
    
    if not issues.isEmpty():
        print("⚠️  Graph validation warnings:")
        for issue in issues:
            print(f"    - {issue}")
    
    # Report parallelization
    if process.isParallelExecutionBeneficial():
        partition = process.getParallelPartition()
        print(f"✓ Parallel execution enabled (max parallelism: {partition.getMaxParallelism()})")
    else:
        print("→ Using sequential execution")
    
    # Run optimally
    process.runOptimal()
    print("✓ Process simulation complete")

# Example usage
print("Running parallel process:")
run_process_optimally(parallel_process)
print()
print("Running complex process:")
run_process_optimally(complex_process)
```

## 8. Supported Equipment Types

The graph builder automatically handles various equipment types:

| Category | Equipment | Outlets Detected |
|----------|-----------|------------------|
| **Two-Port** | Stream, Heater, Cooler, Pump, Compressor, Valve | Single outlet |
| **Separators** | Separator | Gas + liquid outlets |
|  | ThreePhaseSeparator | Gas + oil + aqueous outlets |
| **Mixers/Splitters** | Mixer | Single outlet |
|  | Splitter | Multiple split streams |
|  | Manifold | Multiple outlets (N→M routing) |
| **Heat Exchange** | HeatExchanger | Both hot/cold side outlets |
|  | MultiStreamHeatExchanger | All stream outlets |
| **Turbomachinery** | TurboExpanderCompressor | Expander + compressor outlets |

```python
from neqsim.process.equipment.heatexchanger import HeatExchanger

# Create process with heat exchanger
hx_process = ProcessSystem("Heat Exchanger Process")

# Hot fluid
hot_fluid = create_natural_gas()
hot_stream = Stream("hot_stream", hot_fluid)
hot_stream.setFlowRate(5000, "kg/hr")
hot_stream.setTemperature(80.0, "C")
hot_stream.setPressure(50.0, "bara")
hx_process.add(hot_stream)

# Cold fluid
cold_fluid = create_natural_gas()
cold_stream = Stream("cold_stream", cold_fluid)
cold_stream.setFlowRate(4000, "kg/hr")
cold_stream.setTemperature(10.0, "C")
cold_stream.setPressure(40.0, "bara")
hx_process.add(cold_stream)

# Heat exchanger (2 inlets, 2 outlets)
hx = HeatExchanger("heat_exchanger", hot_stream, cold_stream)
hx.setUAvalue(5000)
hx_process.add(hx)

# Analyze
graph = hx_process.buildGraph()
print("=" * 50)
print("HEAT EXCHANGER GRAPH")
print("=" * 50)

hx_node = graph.getNode(hx)
print(f"Heat exchanger incoming edges: {hx_node.getIncomingEdges().size()}")
print(f"Heat exchanger outgoing edges: {hx_node.getOutgoingEdges().size()}")
print()
print("Connections:")
for edge in hx_node.getIncomingEdges():
    print(f"  {edge.getSource().getName()} → {hx_node.getName()}")
```

## Summary

### Key Methods

| Method | Description |
|--------|-------------|
| `process.buildGraph()` | Build process graph |
| `process.run()` | Sequential execution |
| `process.runParallel()` | Parallel execution |
| `process.runOptimal()` | Auto-select best strategy |
| `process.isParallelExecutionBeneficial()` | Check if parallel helps |
| `graph.getCalculationOrder()` | Get topological sort |
| `graph.partitionForParallelExecution()` | Get parallel levels |

### Best Practices

1. Use `runOptimal()` for most cases - it auto-selects the best strategy
2. Use `run()` for processes with recycles (requires convergence iteration)
3. Use `runParallel()` when you know you have independent branches
4. Call `validate()` during development to catch configuration errors
5. Use `getSummary()` to understand process structure

