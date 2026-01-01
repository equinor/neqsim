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

The recommended approach is to use `runOptimized()` which automatically selects the best strategy:

```java
// Auto-selects best execution strategy
process.runOptimized();
```

### Execution Strategy Comparison

| Strategy | Method | Best For | Speedup |
|----------|--------|----------|---------|
| Sequential | `run()` | Simple processes | baseline |
| Graph-based | `setUseGraphBasedExecution(true)` | Complex ordering | 28% |
| Parallel | `runParallel()` | Feed-forward (no recycles) | 40-57% |
| Hybrid | `runHybrid()` | Processes with recycles | 38% |
| **Optimized** | `runOptimized()` | **All processes** | **best available** |

### Sequential Execution (Default)

Standard execution in insertion order:

```java
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
// Auto-selects: runParallel() or runHybrid()
process.runOptimized();
```

**Decision logic:**
- No recycles → `runParallel()` for maximum speed
- Has recycles → `runHybrid()` for parallel + iteration

---

## Analyzing Execution Strategy

### Check Process Topology

```java
// Check if process has recycle loops
boolean hasRecycles = process.hasRecycleLoops();

// Check if parallel execution would be beneficial
boolean beneficial = process.isParallelExecutionBeneficial();

// Get detailed partition analysis
System.out.println(process.getExecutionPartitionInfo());
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

- [ProcessSystem](process_system.md) - Process system management
- [ProcessModule](process_module.md) - Modular process units
- [Parallel Simulation](../../parallel_process_simulation.md) - Parallel execution guide
