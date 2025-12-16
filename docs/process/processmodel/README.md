# Process System and Flowsheet Management

This folder contains documentation for process system and flowsheet management in NeqSim.

## Contents

| Document | Description |
|----------|-------------|
| [ProcessSystem](process_system.md) | Main process system class |
| [ProcessModule](process_module.md) | Modular process units |
| [Graph-Based Simulation](graph_simulation.md) | Graph-based execution |

---

## Overview

The `processmodel` package provides the framework for building and executing process simulations:

- **ProcessSystem**: Container for process equipment and execution engine
- **ProcessModule**: Encapsulated process modules for reuse
- **ProcessGraph**: Graph-based execution for complex flowsheets

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

// Run simulation
process.run();

// Get results
process.display();
```

---

## Related Documentation

- [Equipment Overview](../equipment/README.md) - Process equipment
- [Controllers](../controllers.md) - Control systems
- [Safety Systems](../safety/README.md) - Safety equipment
