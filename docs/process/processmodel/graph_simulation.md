---
title: Graph-Based Process Simulation
description: Documentation for graph-based execution in NeqSim.
---

# Graph-Based Process Simulation

Documentation for graph-based execution in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Execution Strategies](#execution-strategies)
- [ProcessGraph Class](#processgraph-class)
- [Graph Construction](#graph-construction)
- [Graph Analysis](#graph-analysis)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.processmodel.graph`

**Classes:**
| Class | Description |
|-------|-------------|
| `ProcessGraph` | Graph representation of process |
| `ProcessGraphBuilder` | Builder for constructing graphs |
| `ProcessNode` | Node representing equipment |
| `ProcessEdge` | Edge representing stream connection |

Graph-based simulation represents the process as a directed graph where:
- **Nodes** represent equipment
- **Edges** represent stream connections
- **Execution** follows topological order

Benefits:
- Automatic dependency resolution
- Parallel execution of independent units
- Optimal handling of recycle loops
- 28-40% speedup on complex processes

---

## Execution Strategies

### Quick Start - Use runOptimized()

The recommended approach is to use `runOptimized()` which automatically analyzes your process and selects the best strategy:

```java
// Auto-selects best execution strategy based on process topology
process.runOptimized();

// With calculation ID for tracking
UUID calcId = UUID.randomUUID();
process.runOptimized(calcId);
```

The method inspects the process for:
- **Adjuster units** → Sequential execution for implicit feedback
- **Recycle units** → Hybrid execution (parallel feed-forward + iterative recycle section)
- **Wide feed-forward topology** → Dependency-aware dataflow execution
- **Small or narrow feed-forward topology** → Level-based parallel execution

Mixers, manifolds, heat exchangers, and other multi-input equipment are supported by both feed-forward strategies. The
graph places each task after all direct predecessors, while equipment sharing a mutable input is grouped into one
sequential task.

### Execution Strategy Comparison

| Strategy | Method | Best For | When Used by runOptimized() |
|----------|--------|----------|----------------------------|
| Sequential | `runSequential()` | Adjusters and legacy insertion-order execution | Has Adjuster or MultiVariableAdjuster units |
| Graph-based | `setUseGraphBasedExecution(true)` | Complex ordering | Manual configuration only |
| Parallel | `runParallel()` | Small or narrow feed-forward graphs | Dataflow threshold or useful-width test is not met |
| Dataflow | `runDataflow()` | Wide or asymmetric feed-forward graphs | At least eight units and independent tasks |
| Hybrid | `runHybrid()` | Processes with recycles | Has recycle units and no adjusters |
| **Optimized** | `runOptimized()` | **All processes** | **Auto-selects from above** |

### Sequential Execution (Explicit Legacy Mode)

Explicit insertion-order execution:

```java
process.runSequential(UUID.randomUUID());

// Or retain sequential behavior for subsequent process.run() calls
process.setUseOptimizedExecution(false);
process.run();
```

### Graph-Based Execution

Uses topological ordering for optimal execution sequence:

```java
// Enable graph-based ordering
process.setUseGraphBasedExecution(true);
process.run();
```

### Parallel Execution

Executes independent units simultaneously using thread pool:

```java
// For feed-forward processes (no recycles)
try {
    process.runParallel();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**How it works:**
1. Builds dependency graph
2. Partitions into execution levels
3. Runs units at each level in parallel
4. Waits for level completion before next level

### Dataflow Execution

Executes each unit or shared-input group as soon as its direct predecessors finish:

```java
try {
    process.runDataflow();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

`runOptimized()` selects dataflow only for a feed-forward graph with at least eight units and useful independent tasks.
Unlike level-based execution, a slow unit on one branch does not hold unrelated downstream work behind a global level
barrier. Multi-input equipment retains deterministic predecessor ordering and shared-input grouping.

### Hybrid Execution

Combines parallel and iterative execution for processes with recycles:

```java
// For processes with recycles
try {
    process.runHybrid();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**How it works:**
1. **Phase 1 (Parallel)**: Run feed-forward units in parallel
2. **Phase 2 (Iterative)**: Run recycle section with convergence iteration

### Optimized Execution (Recommended)

Automatically selects the best strategy based on process topology:

```java
// Auto-selects best execution strategy
process.runOptimized();
```

**Decision logic (in order of priority):**

| Condition | Strategy | Reason |
|-----------|----------|--------|
| Has `Adjuster`/`MultiVariableAdjuster` units | `runSequential()` | Implicit feedback is not represented by stream dependencies |
| Has `Recycle` units | `runHybrid()` | Feed-forward levels can run in parallel; the recycle section iterates |
| Feed-forward, at least eight units, useful independent tasks | `runDataflow()` | Direct predecessor scheduling avoids unnecessary level barriers |
| Other feed-forward topology | `runParallel()` | Small or serial graphs do not amortize dataflow futures |

**Multi-input equipment includes:**
- `Mixer`, `Manifold`
- `TurboExpanderCompressor`, `Ejector`
- `HeatExchanger`, `MultiStreamHeatExchanger`
- `FurnaceBurner`, `FlareStack`

**Note:** `hasRecycles()` checks for explicit `Recycle` unit operations, not graph-based cycle detection.

---

## Analyzing Execution Strategy

### Check Process Topology

```java
// Check if process has Recycle units (requires iterative execution)
boolean hasRecycles = process.hasRecycles();

// Check if process has Adjuster units (requires iteration)
boolean hasAdjusters = process.hasAdjusters();

// Check if process has multi-input equipment (supported by parallel and dataflow execution)
// Includes: Mixer, Manifold, HeatExchanger, TurboExpanderCompressor, etc.
boolean hasMultiInput = process.hasMultiInputEquipment();

// Check if parallel execution would be beneficial (graph-based)
boolean beneficial = process.isParallelExecutionBeneficial();

// Get detailed partition analysis
System.out.println(process.getExecutionPartitionInfo());
```

### Understanding runOptimized() Selection

```java
// What will runOptimized() do for my process?
if (process.hasAdjusters()) {
    System.out.println("Will use: runSequential() - Adjuster feedback detected");
} else if (process.hasRecycles()) {
    System.out.println("Will use: runHybrid() - Recycle units detected");
} else {
    System.out.println(process.getExecutionStrategyExplanation());
}
```

### Example Partition Analysis Output

```
=== Execution Partition Analysis ===
Total units: 40
Has recycle loops: true
Parallel levels: 29
Max parallelism: 6
Units in recycle loops: 30
  - 1st stage compressor
  - 2nd stage separator
  ...

=== Hybrid Execution Strategy ===
Phase 1 (Parallel): 4 levels, 8 units
Phase 2 (Iterative): 25 levels, 32 units

Execution levels:
  Level 0 [PARALLEL]: feed TP setter, first stage oil reflux, export oil
  Level 1 [PARALLEL]: 1st stage separator
  Level 2 [PARALLEL]: oil depres valve
  Level 3 [PARALLEL]:
  --- Recycle Section Start (iterative) ---
  Level 4: oil heater second stage [RECYCLE]
  Level 5: 2nd stage separator [RECYCLE]
  ...
```

### Get Parallel Partition

```java
// Get detailed partition info
ProcessGraph.ParallelPartition partition = process.getParallelPartition();

System.out.println("Execution levels: " + partition.getLevelCount());
System.out.println("Max parallelism: " + partition.getMaxParallelism());

// Iterate through levels
for (List<ProcessNode> level : partition.getLevels()) {
    System.out.println("Level has " + level.size() + " units");
}
```

---

## ProcessGraph Class

### Basic Usage

```java
import neqsim.process.processmodel.graph.ProcessGraph;
import neqsim.process.processmodel.graph.ProcessGraphBuilder;

// Build graph from process
ProcessGraphBuilder builder = new ProcessGraphBuilder();
ProcessGraph graph = builder.build(processSystem);

// Execute in topological order
graph.execute();
```

### Graph Properties

```java
// Get number of nodes (equipment)
int nodeCount = graph.getNodeCount();

// Get number of edges (connections)
int edgeCount = graph.getEdgeCount();

// Check for cycles (recycles)
boolean hasCycles = graph.hasCycles();
```

---

## Graph Construction

### Automatic Construction

```java
// Build from existing process
ProcessGraphBuilder builder = new ProcessGraphBuilder();
ProcessGraph graph = builder.build(process);
```

### Manual Construction

```java
ProcessGraph graph = new ProcessGraph();

// Add nodes
graph.addNode(feed);
graph.addNode(heater);
graph.addNode(separator);

// Add edges (connections)
graph.addEdge(feed, heater);
graph.addEdge(heater, separator);
```

### With Metadata

```java
// Add node with properties
Map<String, Object> props = new HashMap<>();
props.put("criticality", "high");
props.put("maintainPriority", 1);
graph.addNode(compressor, props);
```

---

## Graph Analysis

### Topological Sort

```java
// Get execution order
List<ProcessEquipmentInterface> order = graph.topologicalSort();

for (int i = 0; i < order.size(); i++) {
    System.out.println((i+1) + ". " + order.get(i).getName());
}
```

### Find Cycles

```java
// Identify recycle loops
List<List<ProcessEquipmentInterface>> cycles = graph.findCycles();

for (List<ProcessEquipmentInterface> cycle : cycles) {
    System.out.println("Cycle found:");
    for (ProcessEquipmentInterface node : cycle) {
        System.out.println("  - " + node.getName());
    }
}
```

### Critical Path

```java
// Find longest path (critical path)
List<ProcessEquipmentInterface> criticalPath = graph.findCriticalPath();

System.out.println("Critical path:");
for (ProcessEquipmentInterface node : criticalPath) {
    System.out.println("  " + node.getName());
}
```

---

## Visualization

### Export to DOT Format

```java
// Export for Graphviz visualization
String dot = graph.toDOT();
Files.writeString(Path.of("process_graph.dot"), dot);

// Generate image with Graphviz:
// dot -Tpng process_graph.dot -o process_graph.png
```

### Export to JSON

```java
// Export graph structure to JSON
String json = graph.toJSON();
Files.writeString(Path.of("process_graph.json"), json);
```

---

## Usage Examples

### Parallel Compression Train

```java
ProcessSystem process = new ProcessSystem();

// Feed splitter
Splitter splitter = new Splitter("Feed Splitter", feedStream);
splitter.setSplitRatios(new double[]{0.5, 0.5});
process.add(splitter);

// Parallel compressor trains (can execute simultaneously)
Compressor comp1 = new Compressor("K-101", splitter.getOutletStream(0));
comp1.setOutletPressure(80.0, "bara");
process.add(comp1);

Compressor comp2 = new Compressor("K-102", splitter.getOutletStream(1));
comp2.setOutletPressure(80.0, "bara");
process.add(comp2);

// Merger
Mixer mixer = new Mixer("Discharge Mixer");
mixer.addStream(comp1.getOutletStream());
mixer.addStream(comp2.getOutletStream());
process.add(mixer);

// Build graph
ProcessGraph graph = new ProcessGraphBuilder().build(process);

// Parallel execution - K-101 and K-102 run simultaneously
graph.setExecutionStrategy(ExecutionStrategy.PARALLEL);
graph.execute();
```

### Complex Flowsheet Analysis

```java
// Build graph from complex process
ProcessGraph graph = new ProcessGraphBuilder().build(process);

// Analyze structure
System.out.println("Process structure:");
System.out.println("  Equipment count: " + graph.getNodeCount());
System.out.println("  Connections: " + graph.getEdgeCount());
System.out.println("  Has recycles: " + graph.hasCycles());

// Identify independent sections
List<Set<ProcessEquipmentInterface>> sections = graph.findConnectedComponents();
System.out.println("  Independent sections: " + sections.size());

// Find potential bottlenecks (high in-degree)
for (ProcessEquipmentInterface node : graph.getNodes()) {
    int inDegree = graph.getInDegree(node);
    if (inDegree > 2) {
        System.out.println("  Potential bottleneck: " + node.getName() +
            " (" + inDegree + " inputs)");
    }
}
```

### Recycle Identification

```java
ProcessGraph graph = new ProcessGraphBuilder().build(process);

// Find all recycle streams
List<List<ProcessEquipmentInterface>> cycles = graph.findCycles();

System.out.println("Recycle loops identified:");
for (int i = 0; i < cycles.size(); i++) {
    System.out.println("Recycle " + (i+1) + ":");
    List<ProcessEquipmentInterface> cycle = cycles.get(i);
    for (ProcessEquipmentInterface node : cycle) {
        System.out.println("  -> " + node.getName());
    }

    // Suggest tear stream (node with lowest "impact")
    ProcessEquipmentInterface tearStream = graph.suggestTearStream(cycle);
    System.out.println("  Suggested tear stream: " + tearStream.getName());
}
```

---

## Performance Optimization

### Identify Parallel Opportunities

```java
// Get execution levels
List<List<ProcessEquipmentInterface>> levels = graph.getExecutionLevels();

// Count parallel opportunities
int parallelOps = 0;
for (List<ProcessEquipmentInterface> level : levels) {
    if (level.size() > 1) {
        parallelOps += level.size() - 1;
    }
}

System.out.println("Parallel execution opportunities: " + parallelOps);
System.out.println("Potential speedup: " +
    (double)graph.getNodeCount() / levels.size() + "x");
```

### Subgraph Extraction

```java
// Extract subgraph for specific section
Set<ProcessEquipmentInterface> compressionUnits = process.getUnitsOfType(
    CompressorInterface.class).stream().collect(Collectors.toSet());

ProcessGraph compressionGraph = graph.extractSubgraph(compressionUnits);

// Analyze compression section separately
compressionGraph.execute();
```

---

## Related Documentation

- [ProcessSystem](process_system) - Process system management
- [ProcessModel](process_model) - Multi-process model management
- [ProcessModule](process_module) - Modular process units
- [Parallel Simulation](../../simulation/parallel_process_simulation) - Parallel execution guide

---

## ProcessModel Execution

When combining multiple `ProcessSystem` instances into a `ProcessModel`, execution follows a similar pattern:

### Running ProcessModel

```java
import neqsim.process.processmodel.ProcessModel;

// Create and populate model
ProcessModel model = new ProcessModel();
model.add("Upstream", upstreamProcess);
model.add("Compression", compressionProcess);
model.add("Export", exportProcess);

// Run until convergence (uses optimized execution internally)
model.run();

// Check convergence
if (model.isModelConverged()) {
    System.out.println("Converged in " + model.getLastIterationCount() + " iterations");
}
```

### Execution Options

```java
// Continuous mode (default) - iterates until convergence
model.setRunStep(false);
model.run();

// Step mode - run one iteration at a time
model.setRunStep(true);
model.run();  // One step for each ProcessSystem
model.run();  // Next step...

// Step mode with per-area override - fully solve one area each step
compressionSystem.setSolveFullyInModelStep(true);
model.setRunStep(true);
model.run();  // compressionSystem converges fully, other areas single-step

// Asynchronous execution
Future<?> task = model.runAsTask();
// ... do other work ...
task.get();  // Wait for completion
```

### Optimized Execution in ProcessModel

Each `ProcessSystem` within a `ProcessModel` uses `runOptimized()` by default:

```java
// Enable/disable optimized execution for contained ProcessSystems
model.setUseOptimizedExecution(true);  // Default
model.run();
```
