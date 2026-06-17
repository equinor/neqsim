---
title: Process Model Lifecycle Management
description: Documentation for process model state management, versioning, and lifecycle tracking.
---

# Process Model Lifecycle Management

Documentation for process model state management, versioning, and lifecycle tracking.

## Table of Contents
- [Overview](#overview)
- [ProcessModelState](#processmodelstate)
- [ProcessSystemState](#processsystemstate)
- [Model Versioning](#model-versioning)
- [Version Comparison](#version-comparison)
- [Checkpointing](#checkpointing)
- [Compressed Bytes and Cloud Storage](#compressed-bytes-and-cloud-storage)
- [Serialization Options](#serialization-options)
- [Execution Configuration](#execution-configuration)
- [Integration Patterns](#integration-patterns)

---

## Overview

**Package:** `neqsim.process.processmodel.lifecycle`

The lifecycle package provides tools for managing the complete lifecycle of process models:
- State serialization and deserialization
- Version control integration
- Multi-process model management
- Digital twin lifecycle tracking (concept → design → operation)

### Key Classes

| Class | Description |
|-------|-------------|
| `ProcessModelState` | Serializable state of a complete ProcessModel |
| `ProcessSystemState` | State snapshot of a single ProcessSystem |
| `ModelMetadata` | Metadata for model tracking |
| `ProcessModelState.InterProcessConnection` | Inner class for connections between ProcessSystems |
| `ProcessModelState.ExecutionConfig` | Inner class for execution configuration |

---

## ProcessModelState

### Overview

`ProcessModelState` enables complete serialization of multi-system process models for:
- Checkpointing long-running simulations
- Version control with Git
- State transfer between systems
- Digital twin lifecycle management

### Creating a State Snapshot

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.lifecycle.ProcessModelState;

// Create and run a process model
ProcessModel model = new ProcessModel();
model.add("upstream", upstreamProcess);
model.add("midstream", pipelineProcess);
model.add("downstream", processingProcess);
model.run();

// Create state snapshot
ProcessModelState state = ProcessModelState.fromProcessModel(model);
state.setVersion("1.0.0");
state.setDescription("Initial field development model");
state.setCreatedBy("Engineering Team");
```

### Saving to File

```java
// Save as JSON (human-readable, Git-friendly)
state.saveToFile("models/field_model_v1.json");

// Save as compressed JSON (smaller file size)
state.saveToCompressedFile("models/field_model_v1.json.gz");
```

### Loading from File

```java
// Load from JSON
ProcessModelState loaded = ProcessModelState.loadFromFile("models/field_model_v1.json");

// Load from compressed file
ProcessModelState loadedCompressed =
    ProcessModelState.loadFromCompressedFile("models/field_model_v1.json.gz");

// Restore to ProcessModel
ProcessModel restoredModel = loaded.toProcessModel();
restoredModel.run();
```

### State Properties

```java
// Set metadata
state.setName("Troll Field Model");
state.setVersion("2.1.0");
state.setDescription("Updated with new wells A-5 and A-6");
state.setCreatedBy("Subsurface Team");

// Get metadata
System.out.println("Name: " + state.getName());
System.out.println("Version: " + state.getVersion());
System.out.println("Created: " + state.getCreatedAt());
System.out.println("Modified: " + state.getLastModifiedAt());
System.out.println("Created by: " + state.getCreatedBy());
System.out.println("Description: " + state.getDescription());
```

### Custom Properties

```java
// Add custom properties for extensibility
state.setCustomProperty("project", "Field Development Phase 2");
state.setCustomProperty("scenario", "High GOR Case");
state.setCustomProperty("approved", true);
state.setCustomProperty("reviewDate", "2024-03-15");

// Get a single custom property
String project = (String) state.getCustomProperty("project");

// Get all custom properties as a map
Map<String, Object> allProps = state.getCustomProperties();
```

---

## ProcessSystemState

### Overview

`ProcessSystemState` captures the complete state of a single `ProcessSystem`, including:
- All equipment and their configurations
- Stream states (composition, T, P, flow rates)
- Controller settings
- Solver configuration

### Creating a Process System State

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.lifecycle.ProcessSystemState;

// Create and configure process system
ProcessSystem process = new ProcessSystem();
process.add(inlet);
process.add(separator);
process.add(compressor);
process.run();

// Create state
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setName("Gas Processing Train A");
```

### Equipment States

Equipment states are returned as a list. Each `EquipmentState` captures the equipment
type, numeric properties (temperatures, pressures, flows), string properties (names,
modes), and the fluid state if applicable.

```java
// Get equipment states
List<ProcessSystemState.EquipmentState> equipmentStates = state.getEquipmentStates();

for (ProcessSystemState.EquipmentState eqState : equipmentStates) {
    System.out.println(eqState.getName() + ":");
    System.out.println("  Type: " + eqState.getEquipmentType());
    System.out.println("  All Parameters: " + eqState.getParameters());

    // Or access numeric and string properties separately
    System.out.println("  Numeric: " + eqState.getNumericProperties());
    System.out.println("  String: " + eqState.getStringProperties());

    // Fluid state (if equipment has an associated fluid)
    if (eqState.getFluidState() != null) {
        ProcessSystemState.FluidState fs = eqState.getFluidState();
        System.out.println("  T: " + fs.getTemperature() + " K");
        System.out.println("  P: " + fs.getPressure() + " Pa");
        System.out.println("  Phases: " + fs.getNumberOfPhases());
    }
}
```

### Connection Topology

ProcessSystemState captures stream connections between equipment for topology analysis:

```java
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

// View captured connections
for (ProcessSystemState.ConnectionState conn : state.getConnectionStates()) {
    System.out.println(conn.getSourceEquipmentName() + "." + conn.getSourcePortName()
        + " -> " + conn.getTargetEquipmentName() + "." + conn.getTargetPortName());
}
// Example output: separator.gasOutStream -> compressor.inlet
```

### Stream States

Stream equipment is also captured as `StreamState` objects with key thermodynamic properties:

```java
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);

Map<String, ProcessSystemState.StreamState> streams = state.getStreamStates();
for (Map.Entry<String, ProcessSystemState.StreamState> entry : streams.entrySet()) {
    ProcessSystemState.StreamState ss = entry.getValue();
    System.out.println(entry.getKey() + ":");
    System.out.println("  T: " + ss.getTemperature() + " K");
    System.out.println("  P: " + ss.getPressure() + " bara");
    System.out.println("  Flow: " + ss.getMolarFlowRate() + " mole/sec");
    System.out.println("  Composition: " + ss.getComposition());
}
```

---

## Model Versioning

### Git-Friendly JSON Export

The JSON format is designed for version control:

```java
// Export as formatted JSON for Git tracking
String json = state.toJson();
Files.write(Paths.get("models/field_model.json"), json.getBytes());

// The JSON is human-readable and diff-friendly:
// {
//   "schemaVersion": "1.0",
//   "name": "Field Model",
//   "version": "1.0.0",
//   "createdAt": "2024-01-15T10:30:00Z",
//   "processStates": {
//     "upstream": { ... },
//     "downstream": { ... }
//   },
//   "interProcessConnections": [ ... ]
// }
```

### Schema Version Tracking

ProcessModelState includes automatic schema version tracking. Older files
are automatically migrated when loaded:

```java
ProcessModelState state = ProcessModelState.loadFromFile("models/field_model.json");
System.out.println("Schema version: " + state.getSchemaVersion()); // e.g., "1.0"
```

---

## Version Comparison

Compare two model states to identify changes:

```java
ProcessModelState oldState = ProcessModelState.loadFromFile("models/v1.json");
ProcessModelState newState = ProcessModelState.loadFromFile("models/v2.json");

ProcessModelState.ModelDiff diff = ProcessModelState.compare(oldState, newState);
System.out.println("Added: " + diff.getAddedEquipment());
System.out.println("Removed: " + diff.getRemovedEquipment());
System.out.println("Modified: " + diff.getModifiedParameters());
System.out.println("Has changes: " + diff.hasChanges());
```

### Schema Migration

Migrate a state to the latest schema version:

```java
ProcessModelState state = ProcessModelState.loadFromFile("legacy_model.json");
ProcessModelState migrated = ProcessModelState.migrate(state, "1.0");
migrated.saveToFile("migrated_model.json");
```

---

## Checkpointing

### Manual Checkpointing

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.lifecycle.ProcessModelState;

// Checkpoint during long simulation loop
ProcessModel model = new ProcessModel();
model.add("upstream", upstreamProcess);
model.add("downstream", downstreamProcess);

for (int i = 0; i < 100; i++) {
    model.run();
    // ... adjust parameters ...

    if (i % 10 == 0) {
        ProcessModelState checkpoint = ProcessModelState.fromProcessModel(model);
        checkpoint.setVersion("iteration_" + i);
        checkpoint.saveToCompressedFile("checkpoints/step_" + i + ".json.gz");
    }
}
```

### Recovery from Checkpoint

```java
// Recover from a checkpoint
ProcessModelState recovered =
    ProcessModelState.loadFromCompressedFile("checkpoints/step_50.json.gz");
ProcessModel model = recovered.toProcessModel();
System.out.println("Recovered from: " + recovered.getVersion());
model.run();
```

### Automatic Checkpointing

Configure automatic checkpointing on the ProcessModel:

```java
ProcessModel model = new ProcessModel();
model.add("upstream", upstreamProcess);
model.add("downstream", downstreamProcess);

// Enable automatic checkpointing
model.setCheckpointEnabled(true);
model.setCheckpointInterval(5);  // every 5 iterations
model.setCheckpointPath("checkpoints/model_checkpoint.json.gz");

model.run();  // Checkpoints saved automatically during execution
```

---

## Compressed Bytes and Cloud Storage

For in-memory transfer or cloud storage without temporary files:

```java
// Serialize to byte array
ProcessModelState state = ProcessModelState.fromProcessModel(model);
byte[] compressed = state.toCompressedBytes();

// ... store in database, send over network, upload to cloud ...

// Deserialize from byte array
ProcessModelState restored = ProcessModelState.fromCompressedBytes(compressed);
```

---

## Serialization Options

Control JSON output format:

```java
ProcessModelState.SerializationOptions options = new ProcessModelState.SerializationOptions();
options.setPrettyPrint(false);          // Compact JSON
options.setIncludeTimestamps(true);      // Include creation/modification times
options.setCompressStreams(false);        // Standard stream output
options.setSchemaValidation(true);       // Validate schema on save

String json = state.toJson(options);
```

---

## Execution Configuration

Configure execution parameters on the ProcessModelState:

```java
ProcessModelState state = ProcessModelState.fromProcessModel(model);
ProcessModelState.ExecutionConfig config = state.getExecutionConfig();

config.setMaxIterations(100);
config.setFlowTolerance(1e-5);
config.setTemperatureTolerance(1e-5);
config.setPressureTolerance(1e-5);
config.setUseOptimizedExecution(true);
config.setSolverType("sequential");
config.setTolerance(1e-6);
config.setParallelExecution(false);
config.setNumberOfThreads(4);
```

---

## Integration Patterns

### Digital Twin Lifecycle

```java
// Track model through development phases
public enum LifecyclePhase {
    CONCEPT, FEED, DETAILED_DESIGN, CONSTRUCTION, COMMISSIONING, OPERATION, DECOMMISSIONING
}

// Update phase tracking
state.setCustomProperty("phase", LifecyclePhase.DETAILED_DESIGN.name());
state.setCustomProperty("phaseStartDate", "2024-01-01");
state.setCustomProperty("targetCompletion", "2024-06-30");

// Track design basis changes
List<String> designChanges = new ArrayList<>();
designChanges.add("2024-02-15: Updated reservoir pressure to 2850 psia");
designChanges.add("2024-03-01: Added third compressor train");
state.setCustomProperty("designChanges", designChanges);
```

### REST API Integration

```java
// Export model state to JSON string for REST endpoints
ProcessModelState state = ProcessModelState.fromProcessModel(model);
String json = state.toJson();
// ... send json in HTTP response ...

// Receive and restore from JSON string
ProcessModelState received = ProcessModelState.fromJson(requestBody);
ProcessModel model = received.toProcessModel();
model.run();
```

---

---

## See Also

- [Process Serialization](../../simulation/process_serialization) - Full serialization guide (JSON, XML, .neqsim)
- [Process Automation](../../simulation/process_automation) - String-addressable automation API
