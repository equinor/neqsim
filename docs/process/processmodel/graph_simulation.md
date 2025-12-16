# Graph-Based Process Simulation

Documentation for graph-based execution in NeqSim.

## Table of Contents
- [Overview](#overview)
- [ProcessGraph Class](#processgraph-class)
- [Graph Construction](#graph-construction)
- [Execution Strategies](#execution-strategies)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.processmodel.graph`

**Classes:**
| Class | Description |
|-------|-------------|
| `ProcessGraph` | Graph representation of process |
| `ProcessGraphBuilder` | Builder for constructing graphs |

Graph-based simulation represents the process as a directed graph where:
- **Nodes** represent equipment
- **Edges** represent stream connections
- **Execution** follows topological order

Benefits:
- Automatic dependency resolution
- Parallel execution of independent units
- Better handling of complex flowsheets
- Visual representation of process structure

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
graph.addNode(compressor, Map.of(
    "criticality", "high",
    "maintainPriority", 1
));

// Add edge with properties
graph.addEdge(stream1, mixer, Map.of(
    "streamType", "recycle",
    "flowDirection", "forward"
));
```

---

## Execution Strategies

### Sequential Execution

Default topological ordering:

```java
graph.setExecutionStrategy(ExecutionStrategy.SEQUENTIAL);
graph.execute();
```

### Parallel Execution

Execute independent nodes in parallel:

```java
graph.setExecutionStrategy(ExecutionStrategy.PARALLEL);
graph.setNumberOfThreads(4);
graph.execute();
```

### Level-Based Execution

Execute by levels (all nodes at same depth together):

```java
graph.setExecutionStrategy(ExecutionStrategy.LEVEL_BASED);
graph.execute();

// Get execution levels
List<List<ProcessEquipmentInterface>> levels = graph.getExecutionLevels();
for (int i = 0; i < levels.size(); i++) {
    System.out.println("Level " + i + ": " + 
        levels.get(i).stream().map(e -> e.getName()).collect(Collectors.toList()));
}
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
