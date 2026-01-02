# Process System and Flowsheet Management

This folder contains documentation for process system and flowsheet management in NeqSim.

## Contents

| Document | Description |
|----------|-------------|
| [ProcessSystem](process_system.md) | Main process system class and execution strategies |
| [ProcessModule](process_module.md) | Modular process units |
| [Graph-Based Simulation](graph_simulation.md) | Graph-based execution and optimization |

---

## Overview

The `processmodel` package provides the framework for building and executing process simulations:

- **ProcessSystem**: Container for process equipment and execution engine
- **ProcessModule**: Encapsulated process modules for reuse
- **ProcessGraph**: Graph-based execution for complex flowsheets

---

## Execution Strategies

NeqSim provides multiple execution strategies optimized for different process types:

| Method | Best For | Description |
|--------|----------|-------------|
| `run()` | General use | Sequential execution (or optimized if enabled) |
| `runOptimized()` | **Recommended** | Auto-selects best strategy based on topology |
| `runParallel()` | Feed-forward processes | Maximum parallelism for no-recycle processes |
| `runHybrid()` | Complex processes | Parallel feed-forward + iterative recycle |

### Enabling Optimized Execution by Default

For best performance, enable optimized execution so `run()` automatically uses the best strategy:

```java
process.setUseOptimizedExecution(true);
process.run();  // Now uses runOptimized() internally
```

**Typical performance improvements:**
- Feed-forward processes: 40-57% speedup with parallel execution
- Processes with recycles: 28-38% speedup with hybrid execution

---

## Quick Start

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;

// Create process system
ProcessSystem process = new ProcessSystem();

// Add equipment
process.add(feedStream);
process.add(separator);
process.add(compressor);

// Run simulation (recommended - auto-optimized)
process.runOptimized();

// Get results
process.display();
```

---

## Analyzing Process Topology

```java
// Check if process has recycles
boolean hasRecycles = process.hasRecycleLoops();

// Get execution partition analysis
System.out.println(process.getExecutionPartitionInfo());
```

---

## Related Documentation

- [Equipment Overview](../equipment/README.md) - Process equipment
- [Controllers](../controllers.md) - Control systems
- [Safety Systems](../safety/README.md) - Safety equipment
