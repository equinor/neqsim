# Graph-Based Process Simulation in NeqSim

## Overview

NeqSim now supports **graph-based process representation**, enabling topology-aware simulation execution, automatic parallelization, and advanced analysis of process flowsheets. This document explains the theory, demonstrates all functionality, and compares the new approach with traditional sequential execution.

## Table of Contents

1. [Theory and Motivation](#theory-and-motivation)
2. [Core Components](#core-components)
3. [Basic Usage](#basic-usage)
4. [Parallel Execution](#parallel-execution)
5. [Cycle and Recycle Detection](#cycle-and-recycle-detection)
6. [Comparison: Old vs New Approach](#comparison-old-vs-new-approach)
7. [API Reference](#api-reference)
8. [Best Practices](#best-practices)

---

## Theory and Motivation

### Process Flowsheets as Directed Graphs

A chemical process flowsheet is naturally represented as a **directed graph** (digraph) where:

- **Nodes** represent unit operations (streams, heaters, separators, compressors, etc.)
- **Edges** represent material/energy flow connections between units

```
        ┌─────────┐
        │  Feed   │
        │ Stream  │
        └────┬────┘
             │
             ▼
        ┌─────────┐
        │ Heater  │
        └────┬────┘
             │
             ▼
        ┌─────────┐      ┌─────────┐
        │Separator├─────►│Gas Out  │
        └────┬────┘      └─────────┘
             │
             ▼
        ┌─────────┐
        │Liquid   │
        │ Out     │
        └─────────┘
```

### Why Graph Representation?

Traditional process simulators execute equipment in **insertion order** - the order in which units were added to the flowsheet. This has limitations:

1. **No dependency awareness**: If equipment B depends on A's output, but A was added after B, simulation may fail or produce incorrect results
2. **No parallelization**: All units execute sequentially even when independent
3. **No structural analysis**: Cannot automatically detect recycle loops, independent branches, or optimal execution paths

Graph-based representation solves these problems by:

1. **Automatic dependency resolution** via topological sorting
2. **Parallel execution** of independent equipment
3. **Cycle detection** for recycle loop identification
4. **Structural validation** before simulation

### Mathematical Foundation

#### Topological Sorting (Kahn's Algorithm)

For a Directed Acyclic Graph (DAG), topological sort produces an ordering where for every edge $(u, v)$, node $u$ appears before $v$. This ensures all inputs are available before a unit executes.

**Algorithm complexity**: $O(V + E)$ where $V$ = nodes, $E$ = edges

#### Strongly Connected Components (Tarjan's Algorithm)

SCCs identify groups of nodes that form cycles (recycle loops). In process terms, an SCC with more than one node indicates a recycle that requires iterative convergence.

**Algorithm complexity**: $O(V + E)$

#### Parallel Partitioning (Longest Path)

Equipment is grouped into **levels** based on the longest path from any source node. Units at the same level have no dependencies on each other and can execute in parallel.

```
Level 0: [feed1, feed2, feed3]     ← All independent, run in parallel
Level 1: [heater1, heater2, heater3]  ← Depend only on Level 0
Level 2: [sep1, sep2, sep3]        ← Depend only on Level 1
```

---

## Core Components

### ProcessGraph

The main graph data structure containing nodes and edges.

```java
ProcessGraph graph = process.buildGraph();

// Get basic info
int nodeCount = graph.getNodeCount();
int edgeCount = graph.getEdgeCount();

// Get calculation order
List<ProcessEquipmentInterface> order = graph.getCalculationOrder();

// Check for cycles
boolean hasCycles = graph.hasCycles();

// Get summary
System.out.println(graph.getSummary());
```

### ProcessNode

Represents a unit operation in the graph.

```java
ProcessNode node = graph.getNode(heater);

// Check connectivity
boolean isSource = node.isSource();  // No incoming edges (e.g., feed stream)
boolean isSink = node.isSink();      // No outgoing edges (e.g., product)

// Get connections
List<ProcessEdge> incoming = node.getIncomingEdges();
List<ProcessEdge> outgoing = node.getOutgoingEdges();

// Get feature vector (for ML/GNN applications)
double[] features = node.getFeatureVector(typeMapping, numTypes);
```

### ProcessEdge

Represents a stream connection between units.

```java
ProcessEdge edge = graph.getEdge(stream);

ProcessNode source = edge.getSource();
ProcessNode target = edge.getTarget();
boolean isBackEdge = edge.isBackEdge();  // Part of a recycle loop
```

### ProcessGraphBuilder

Automatically constructs the graph by analyzing stream connections.

```java
// Automatic construction
ProcessGraph graph = ProcessGraphBuilder.buildGraph(processSystem);

// Or via ProcessSystem convenience method
ProcessGraph graph = process.buildGraph();
```

#### Supported Equipment Types

The `ProcessGraphBuilder` automatically detects stream connections for the following equipment:

| Category | Equipment | Outlets Detected |
|----------|-----------|------------------|
| **Two-Port** | Stream, Heater, Cooler, Pump, Compressor, Valve, etc. | Single outlet |
| **Separators** | Separator | Gas + liquid outlets |
| | ThreePhaseSeparator | Gas + oil + aqueous outlets |
| **Mixers/Splitters** | Mixer | Single outlet |
| | Splitter | Multiple split streams |
| | Manifold | Multiple outlets (N→M routing) |
| **Heat Exchange** | HeatExchanger | Both hot/cold side outlets |
| | MultiStreamHeatExchanger | All stream outlets |
| **Turbomachinery** | TurboExpanderCompressor | Expander + compressor outlets |
| **Columns** | DistillationColumn | Condenser + reboiler outlets |

---

## Basic Usage

### Example 1: Simple Linear Process

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// Build process
ProcessSystem process = new ProcessSystem("Simple Process");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(10000, "kg/hr");
feed.setTemperature(25.0, "C");
feed.setPressure(50.0, "bara");
process.add(feed);

Heater heater = new Heater("heater", feed);
heater.setOutTemperature(350.0);
process.add(heater);

Separator separator = new Separator("separator", heater.getOutletStream());
process.add(separator);

// Build and analyze graph
ProcessGraph graph = process.buildGraph();

System.out.println("=== Graph Analysis ===");
System.out.println("Nodes: " + graph.getNodeCount());
System.out.println("Edges: " + graph.getEdgeCount());
System.out.println("Has cycles: " + graph.hasCycles());
System.out.println();

// Get topological order
System.out.println("Calculation Order:");
for (ProcessEquipmentInterface unit : graph.getCalculationOrder()) {
    System.out.println("  " + unit.getName());
}

// Run with graph-based execution
process.setUseGraphBasedExecution(true);
process.run();
```

**Output:**
```
=== Graph Analysis ===
Nodes: 3
Edges: 2
Has cycles: false

Calculation Order:
  feed
  heater
  separator
```

### Example 2: Process with Splitter and Mixer

```java
ProcessSystem process = new ProcessSystem("Split-Mix Process");

// Feed
Stream feed = new Stream("feed", fluid.clone());
feed.setFlowRate(10000, "kg/hr");
process.add(feed);

// Split into two branches
Splitter splitter = new Splitter("splitter", feed);
splitter.setSplitFactors(new double[] {0.6, 0.4});
process.add(splitter);

// Branch 1: Heat
Heater heater = new Heater("heater", splitter.getSplitStream(0));
heater.setOutTemperature(350.0);
process.add(heater);

// Branch 2: Cool
Cooler cooler = new Cooler("cooler", splitter.getSplitStream(1));
cooler.setOutTemperature(280.0);
process.add(cooler);

// Merge branches
Mixer mixer = new Mixer("mixer");
mixer.addStream(heater.getOutletStream());
mixer.addStream(cooler.getOutletStream());
process.add(mixer);

// Final separation
Separator separator = new Separator("separator", mixer.getOutletStream());
process.add(separator);

// Analyze graph structure
ProcessGraph graph = process.buildGraph();
ProcessGraph.ParallelPartition partition = graph.partitionForParallelExecution();

System.out.println("Parallel Levels: " + partition.getLevelCount());
System.out.println("Max Parallelism: " + partition.getMaxParallelism());

for (int i = 0; i < partition.getLevelCount(); i++) {
    System.out.print("Level " + i + ": ");
    for (ProcessNode node : partition.getLevels().get(i)) {
        System.out.print(node.getName() + " ");
    }
    System.out.println();
}
```

**Output:**
```
Parallel Levels: 5
Max Parallelism: 2

Level 0: feed
Level 1: splitter
Level 2: heater cooler     ← These can run in parallel!
Level 3: mixer
Level 4: separator
```

---

## Parallel Execution

### Automatic Parallel Execution

For processes with independent branches, NeqSim can automatically execute units in parallel:

```java
// Create process with multiple independent branches
ProcessSystem process = new ProcessSystem("Parallel Process");

// Add 4 independent processing trains
for (int i = 1; i <= 4; i++) {
    SystemInterface fluid = new SystemSrkEos(298.0, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed" + i, fluid);
    feed.setFlowRate(5000, "kg/hr");
    process.add(feed);

    Heater heater = new Heater("heater" + i, feed);
    heater.setOutTemperature(350.0);
    process.add(heater);

    Separator sep = new Separator("separator" + i, heater.getOutletStream());
    process.add(sep);
}

// Check parallelization potential
System.out.println("Units: " + process.getUnitOperations().size());
System.out.println("Parallel beneficial: " + process.isParallelExecutionBeneficial());
System.out.println("Max parallelism: " + process.getParallelPartition().getMaxParallelism());

// Run with automatic parallel execution
process.runParallel();  // Explicit parallel
// or
process.runOptimal();   // Auto-selects best strategy
```

**Output:**
```
Units: 12
Parallel beneficial: true
Max parallelism: 4

Parallel Levels:
  Level 0: feed1 feed2 feed3 feed4        ← 4 parallel
  Level 1: heater1 heater2 heater3 heater4  ← 4 parallel
  Level 2: separator1 separator2 separator3 separator4  ← 4 parallel
```

### Execution Methods Comparison

| Method | Description | Use Case |
|--------|-------------|----------|
| `run()` | Sequential execution (insertion or topological order) | Standard simulation |
| `runParallel()` | Parallel execution using thread pool | Feed-forward processes with independent branches |
| `runOptimal()` | Auto-selects parallel or sequential | General use - best of both worlds |

### When Parallel Execution is Used

`runOptimal()` uses parallel execution when:

```java
process.isParallelExecutionBeneficial()
```

Returns `true` if:
- Process has **≥ 4 units** (to justify thread overhead)
- No **Recycle** units (require iterative convergence)
- No **Adjuster** units (require iterative convergence)
- **Max parallelism ≥ 2** (something to parallelize)

---

## Cycle and Recycle Detection

### Detecting Recycle Loops

Recycle loops appear as cycles in the process graph. NeqSim uses Tarjan's SCC algorithm to detect them:

```java
ProcessSystem process = new ProcessSystem("Recycle Process");

// ... add equipment with recycle ...

ProcessGraph graph = process.buildGraph();

// Check for cycles
ProcessGraph.CycleAnalysisResult cycles = graph.analyzeCycles();
System.out.println("Has cycles: " + cycles.hasCycles());
System.out.println("Cycle count: " + cycles.getCycleCount());
System.out.println("Back edges: " + cycles.getBackEdges().size());

// Find strongly connected components (recycle blocks)
ProcessGraph.SCCResult scc = graph.findStronglyConnectedComponents();
System.out.println("SCCs: " + scc.getComponentCount());

for (List<ProcessNode> component : scc.getRecycleLoops()) {
    System.out.print("Recycle loop: ");
    for (ProcessNode node : component) {
        System.out.print(node.getName() + " ");
    }
    System.out.println();
}
```

### Recycle Block Report

```java
String report = process.getRecycleBlockReport();
System.out.println(report);
```

**Example Output:**
```
=== Recycle Block Analysis ===
Total SCCs: 5
Recycle loops (SCCs with >1 node): 1

Recycle Block 1 (3 nodes):
  - mixer
  - heater
  - separator
  Back edges forming this loop:
    - recycle_stream (separator -> mixer)
==============================
```

---

## Comparison: Old vs New Approach

### Traditional Sequential Execution (Old)

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(heater);
process.add(separator);
process.run();  // Executes in insertion order
```

**Characteristics:**
- Units execute in the order they were added
- No awareness of dependencies
- No parallelization
- Simple implementation

### Graph-Based Execution (New)

```java
ProcessSystem process = new ProcessSystem();
process.add(heater);      // Added first, but depends on feed
process.add(separator);   // Added second
process.add(feed);        // Added last, but should run first!

process.setUseGraphBasedExecution(true);
process.run();  // Executes: feed → heater → separator (correct order!)
```

**Characteristics:**
- Units execute in topologically sorted order
- Dependency-aware
- Supports parallelization
- More complex implementation

### Performance Comparison

| Aspect | Sequential | Graph-Based | Graph + Parallel |
|--------|-----------|-------------|------------------|
| Dependency handling | Manual ordering required | Automatic | Automatic |
| Parallel execution | No | No | Yes |
| Recycle detection | Manual | Automatic | Automatic |
| Overhead | Minimal | Graph build ~0.5ms | Graph build + thread mgmt |
| Best for | Simple linear processes | Complex dependencies | Multi-train processes |

### Benchmark Results

```
=== Performance Benchmark (12 units, 4 parallel branches) ===
Sequential execution:    2.83 ms
Graph-based sequential:  2.76 ms  (3% faster due to optimal ordering)
Graph-based parallel:    2.06 ms  (27% faster due to parallelism)
Speedup from parallel:   1.38x
```

### Pros and Cons

#### Sequential Execution (Traditional)

**Pros:**
- ✅ Simple and predictable
- ✅ Minimal overhead
- ✅ Works for all process types including recycles
- ✅ Easy to debug (clear execution order)

**Cons:**
- ❌ Requires careful manual ordering of equipment
- ❌ No parallelization benefits
- ❌ No automatic structural validation
- ❌ Cannot detect configuration errors (wrong connections)

#### Graph-Based Execution

**Pros:**
- ✅ Automatic dependency resolution
- ✅ Order-independent equipment addition
- ✅ Structural validation (detect disconnected units)
- ✅ Enables parallel execution
- ✅ Automatic recycle loop detection
- ✅ Foundation for advanced analysis (optimization, ML)

**Cons:**
- ❌ Small overhead for graph construction
- ❌ More complex implementation
- ❌ Parallel execution not suitable for recycle processes
- ❌ Thread pool overhead for small processes

#### Recommendation

| Process Type | Recommended Method |
|-------------|-------------------|
| Simple linear process (<4 units) | `run()` |
| Complex dependencies | `run()` with `setUseGraphBasedExecution(true)` |
| Recycle loops | `run()` (sequential with convergence) |
| Multiple independent trains | `runParallel()` or `runOptimal()` |
| General/unknown | `runOptimal()` (auto-selects) |

---

## API Reference

### ProcessSystem Methods

```java
// Graph construction
ProcessGraph buildGraph()                    // Build/get cached graph
void invalidateGraph()                       // Clear cached graph

// Execution control
void setUseGraphBasedExecution(boolean use)  // Enable topological ordering
boolean isUseGraphBasedExecution()           // Check if enabled

// Execution methods
void run()                                   // Standard execution
void runParallel()                           // Parallel execution
void runOptimal()                            // Auto-select best strategy

// Analysis
List<ProcessEquipmentInterface> getTopologicalOrder()  // Get sorted order
ProcessGraph.ParallelPartition getParallelPartition()  // Get parallel levels
boolean isParallelExecutionBeneficial()     // Check if parallel helps
String getRecycleBlockReport()              // Get recycle analysis
```

### ProcessGraph Methods

```java
// Structure
int getNodeCount()
int getEdgeCount()
ProcessNode getNode(ProcessEquipmentInterface equipment)
ProcessEdge getEdge(StreamInterface stream)
List<ProcessNode> getSourceNodes()          // Nodes with no inputs
List<ProcessNode> getSinkNodes()            // Nodes with no outputs

// Analysis
List<ProcessEquipmentInterface> getCalculationOrder()  // Topological sort
boolean hasCycles()
CycleAnalysisResult analyzeCycles()
SCCResult findStronglyConnectedComponents()
ParallelPartition partitionForParallelExecution()

// Validation
List<String> validate()                     // Check for issues
String getSummary()                         // Get text summary

// GNN/ML support
double[][] getNodeFeatureMatrix()
int[][] getEdgeIndexTensor()
double[][] getEdgeFeatureMatrix()
Map<Integer, List<Integer>> getAdjacencyList()
```

---

## Best Practices

### 1. Use `runOptimal()` for New Code

```java
// Let NeqSim decide the best execution strategy
process.runOptimal();
```

### 2. Validate Graph Structure

```java
ProcessGraph graph = process.buildGraph();
List<String> issues = graph.validate();
if (!issues.isEmpty()) {
    System.out.println("Warning: " + issues);
}
```

### 3. Check Parallelization Potential

```java
if (process.isParallelExecutionBeneficial()) {
    System.out.println("This process can benefit from parallel execution");
    ProcessGraph.ParallelPartition p = process.getParallelPartition();
    System.out.println("Max speedup potential: " + p.getMaxParallelism() + "x");
}
```

### 4. Debug with Graph Summary

```java
System.out.println(process.buildGraph().getSummary());
```

**Output:**
```
ProcessGraph Summary:
  Nodes: 12
  Edges: 11
  Sources: 4
  Sinks: 4
  Has cycles: false
  SCCs: 12
  Recycle loops: 0
  Parallel levels: 3
  Max parallelism: 4
```

### 5. Handle Recycles Properly

```java
// Recycle processes should use sequential execution
if (processHasRecycles) {
    process.run();  // Uses convergence iteration
} else {
    process.runOptimal();  // May use parallel
}

// Or let runOptimal() decide automatically
process.runOptimal();  // Auto-detects recycles and uses sequential
```

---

## Advanced Topics

### Integration with Machine Learning

The graph representation provides feature matrices suitable for Graph Neural Networks (GNNs):

```java
ProcessGraph graph = process.buildGraph();

// Get tensors for GNN
double[][] nodeFeatures = graph.getNodeFeatureMatrix();  // [N, F]
int[][] edgeIndex = graph.getEdgeIndexTensor();          // [2, E]
double[][] edgeFeatures = graph.getEdgeFeatureMatrix();  // [E, F]

// Use with PyTorch Geometric, DGL, etc.
```

### Custom Graph Analysis

```java
ProcessGraph graph = process.buildGraph();

// Find all paths between two nodes
// Identify critical equipment (high betweenness centrality)
// Optimize equipment sizing based on flow patterns
// etc.
```

### ProcessModule Support

For hierarchical processes using `ProcessModule`:

```java
ProcessModule module = new ProcessModule("LNG Train");
module.add(inlet);
module.add(scrubber);
module.add(deethanizer);
// ...

// Build hierarchical graph
ProcessModelGraph modelGraph = new ProcessModelGraph(module);
ProcessGraph flatGraph = modelGraph.getFlattenedGraph();

// Get sub-system dependencies
Map<String, Set<String>> deps = modelGraph.getSubSystemDependencies();

// Check if parallel execution of sub-systems is beneficial
if (modelGraph.isParallelSubSystemExecutionBeneficial()) {
    // Get parallel partition of sub-systems
    ProcessModelGraph.ModuleParallelPartition partition = 
        modelGraph.partitionSubSystemsForParallelExecution();
    
    System.out.println("Parallel levels: " + partition.getLevelCount());
    System.out.println("Max parallelism: " + partition.getMaxParallelism());
    
    // Each level contains independent sub-systems that can run in parallel
    for (int i = 0; i < partition.getLevelCount(); i++) {
        List<String> systemsAtLevel = partition.getLevels().get(i);
        System.out.println("Level " + i + ": " + systemsAtLevel);
    }
}
```

---

## Conclusion

Graph-based process simulation in NeqSim provides:

1. **Robustness**: Automatic dependency handling prevents ordering errors
2. **Performance**: Parallel execution for suitable processes
3. **Insight**: Structural analysis reveals process topology
4. **Extensibility**: Foundation for optimization and ML applications

For most users, simply using `process.runOptimal()` provides the best of both worlds - automatic selection of the optimal execution strategy based on process structure.
