# Graph-Based Process Simulation in NeqSim

## Overview

NeqSim now supports **graph-based process representation**, enabling topology-aware simulation execution, automatic parallelization, and advanced analysis of process flowsheets. This document explains the theory, demonstrates all functionality, and compares the new approach with traditional sequential execution.

## Table of Contents

1. [Theory and Motivation](#theory-and-motivation)
2. [Core Components](#core-components)
3. [Basic Usage](#basic-usage)
4. [Parallel Execution](#parallel-execution)
5. [Cycle and Recycle Detection](#cycle-and-recycle-detection)
6. [Sensitivity-Based Tear Stream Selection](#sensitivity-based-tear-stream-selection)
7. [Process Sensitivity Analysis](#process-sensitivity-analysis)
8. [Comparison: Old vs New Approach](#comparison-old-vs-new-approach)
9. [API Reference](#api-reference)
10. [Best Practices](#best-practices)

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

## Sensitivity-Based Tear Stream Selection

### Concept

In process simulation with recycle loops, the choice of **tear stream** (where to break the loop for iterative solving) significantly affects convergence speed. The graph-based approach enables automatic sensitivity analysis to select optimal tear streams.

### Sensitivity Metric

The sensitivity of a stream as a tear point is calculated based on:

1. **Path Length Factor** - Streams further from the recycle unit are less sensitive
2. **Equipment Type Weight** - Different equipment types affect sensitivity differently:
   - Separators: 1.5× (phase separation amplifies perturbations)
   - Heat exchangers: 1.3× (temperature changes propagate)
   - Compressors: 1.2× (pressure-flow coupling)
   - Standard equipment: 1.0×
3. **Branching Factor** - Streams feeding multiple downstream units have higher sensitivity

**Formula:**
$$\text{sensitivity} = \frac{\text{path length factor} \times \text{equipment weight}}{\text{branching factor}}$$

**Lower sensitivity = Better tear stream** (more stable convergence)

### Usage

```java
// Build process graph
ProcessGraph graph = process.buildGraph();

// Analyze sensitivity for all recycle loops
List<SensitivityAnalysisResult> results = graph.analyzeTearStreamSensitivity();

for (SensitivityAnalysisResult result : results) {
    System.out.println("Loop with " + result.getLoopNodes().size() + " nodes");
    System.out.println("Best tear stream: " + result.getRecommendedTearStream());
    System.out.println("Sensitivity: " + result.getSensitivity());
}

// Get formatted report
System.out.println(graph.getSensitivityAnalysisReport());
```

### Example Output

```
=== Tear Stream Sensitivity Analysis ===
Recycle Loop 1 (10 nodes):
  Nodes: Main Recycle -> JT Valve -> Gas Splitter -> HP Separator -> ...
  Tear stream candidates (ranked by sensitivity):
    1. Recycle Gas -> Feed Mixer [sensitivity=0.2711] (marked as recycle)
    2. JT Valve -> Recycle [sensitivity=0.3587] (marked as recycle)
    3. Gas Splitter -> JT Valve [sensitivity=0.5857]
    4. HP Separator -> Gas Splitter [sensitivity=0.7174]
    ...
  Recommended tear: Recycle Gas
```

### Automatic Selection

```java
// Automatically select optimal tear streams for all loops
Map<Integer, ProcessEdge> optimalTears = graph.selectTearStreamsWithSensitivity();

for (Map.Entry<Integer, ProcessEdge> entry : optimalTears.entrySet()) {
    System.out.println("Loop " + entry.getKey() + ": Tear at " + 
                       entry.getValue().getSource().getName() + " -> " +
                       entry.getValue().getTarget().getName());
}
```

### Benefits

- **Faster convergence**: Lower sensitivity tears require fewer iterations
- **Better stability**: Less sensitive streams are more tolerant of perturbations
- **Automatic**: No manual tear stream selection required
- **Diagnostic**: Helps understand process coupling

---

## Process Sensitivity Analysis

The `ProcessSensitivityAnalyzer` provides comprehensive sensitivity analysis for any process, computing how output properties change with respect to input properties.

### Fluent API

```java
ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

SensitivityMatrix result = analyzer
    .withInput("feed", "temperature", "C")
    .withInput("feed", "pressure", "bara")
    .withInput("feed", "flowRate", "kg/hr")
    .withOutput("separator", "temperature")
    .withOutput("compressor", "power", "kW")
    .compute();

// Query individual sensitivities
double dT_dP = result.getSensitivity("separator.temperature", "feed.pressure");
```

### Integration with Broyden Convergence

When a process has recycles using Broyden acceleration, the analyzer **automatically reuses the convergence Jacobian** for tear stream sensitivities - this is essentially free!

```java
// Run process with Broyden acceleration
recycle.setAccelerationMethod(AccelerationMethod.BROYDEN);
process.run();

// Analyzer checks for Broyden Jacobian first
SensitivityMatrix result = analyzer
    .withInput("recycle1", "temperature")
    .withOutput("recycle1", "pressure")
    .compute();  // Uses Broyden Jacobian if available, else FD
```

### Finite Difference Options

```java
// Forward differences (default): 1 extra simulation per input
analyzer.withCentralDifferences(false);

// Central differences: 2 extra simulations per input, more accurate
analyzer.withCentralDifferences(true);

// Custom perturbation size (default: 0.001 = 0.1%)
analyzer.withPerturbation(0.01);

// Force finite differences only (ignore Broyden)
SensitivityMatrix fdResult = analyzer.computeFiniteDifferencesOnly();
```

### Report Generation

```java
String report = analyzer.generateReport(result);
System.out.println(report);
```

**Output:**
```
=== Process Sensitivity Analysis Report ===

Inputs:
  - feed.temperature [C]
  - feed.pressure [bara]

Outputs:
  - separator.temperature
  - compressor.power [kW]

Sensitivity Matrix (d_output / d_input):

                                    temperature        pressure
        separator.temperature        1.0000e+00      -2.3400e-02
           compressor.power          4.5600e+01       1.2300e+02

Most Influential Inputs:
  separator.temperature: feed.temperature (sensitivity: 1.0000e+00)
  compressor.power: feed.pressure (sensitivity: 1.2300e+02)
```

### Use Cases

1. **Design Optimization**: Identify which inputs most affect key outputs
2. **Uncertainty Propagation**: Combine with input uncertainties for output bounds
3. **Control System Design**: Understand input-output relationships
4. **Model Validation**: Compare sensitivities against expected physics

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

// Sensitivity Analysis (NEW)
List<SensitivityAnalysisResult> analyzeTearStreamSensitivity()  // Analyze all loops
Map<Integer, ProcessEdge> selectTearStreamsWithSensitivity()    // Auto-select optimal tears
String getSensitivityAnalysisReport()                           // Get formatted report

// Validation
List<String> validate()                     // Check for issues
String getSummary()                         // Get text summary

// GNN/ML support
double[][] getNodeFeatureMatrix()
int[][] getEdgeIndexTensor()
double[][] getEdgeFeatureMatrix()
Map<Integer, List<Integer>> getAdjacencyList()
```

### SensitivityAnalysisResult Class (NEW)

```java
// Get the nodes in this recycle loop
List<ProcessNode> getLoopNodes()

// Get all candidate edges with their sensitivity scores
Map<ProcessEdge, Double> getEdgeSensitivities()

// Get the recommended tear stream (lowest sensitivity)
ProcessEdge getRecommendedTearStream()
double getSensitivity()  // Sensitivity score of recommended tear
```

### ProcessSensitivityAnalyzer Class (NEW)

A comprehensive analyzer for computing sensitivities of any output property with respect to any input property. It intelligently leverages Broyden Jacobians when available, falling back to finite differences only when necessary.

```java
// Create analyzer for a process
ProcessSensitivityAnalyzer analyzer = new ProcessSensitivityAnalyzer(process);

// Fluent API for defining inputs and outputs
analyzer
    .withInput("feed", "temperature", "C")      // equipment, property, unit
    .withInput("feed", "flowRate", "kg/hr")
    .withOutput("product", "temperature")
    .withOutput("product", "pressure", "bara")
    .withCentralDifferences(true)               // More accurate (2x cost)
    .withPerturbation(0.001);                   // Relative perturbation size

// Compute sensitivities (uses Broyden Jacobian if available)
SensitivityMatrix result = analyzer.compute();

// Query specific sensitivities
double dT_dFlow = result.getSensitivity("product.temperature", "feed.flowRate");

// Generate human-readable report
String report = analyzer.generateReport(result);

// Force finite differences only (ignores Broyden)
SensitivityMatrix fdResult = analyzer.computeFiniteDifferencesOnly();
```

**Key Features:**

| Feature | Description |
|---------|-------------|
| **Broyden Integration** | Automatically uses convergence Jacobian for tear streams (free!) |
| **Fluent API** | Easy specification of any equipment.property pair |
| **Unit Support** | Specify units for proper value access/setting |
| **Central/Forward FD** | Choose accuracy vs speed tradeoff |
| **Report Generation** | Formatted sensitivity report with most influential inputs |

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
---

## Jupyter Notebook Example

A complete interactive example is available in the notebooks directory:

📓 **[GraphBasedProcessSimulation.ipynb](../notebooks/GraphBasedProcessSimulation.ipynb)**

The notebook demonstrates:
- Graph construction and analysis
- Cycle detection and recycle block identification
- Sensitivity-based tear stream selection
- Acceleration method comparison (Direct Substitution vs Wegstein vs Broyden)
- Parallel execution for multi-train processes

---

*Last updated: December 2025*