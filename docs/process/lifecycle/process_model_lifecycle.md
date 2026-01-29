# Process Model Lifecycle Management

Documentation for process model state management, versioning, and lifecycle tracking.

## Table of Contents
- [Overview](#overview)
- [ProcessModelState](#processmodelstate)
- [ProcessSystemState](#processsystemstate)
- [Model Versioning](#model-versioning)
- [Checkpointing & Recovery](#checkpointing--recovery)
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
| `InterProcessConnection` | Connections between ProcessSystems |

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

// Get custom properties
String project = (String) state.getCustomProperty("project");
boolean approved = (boolean) state.getCustomProperty("approved");
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

```java
// Get equipment states
Map<String, EquipmentState> equipmentStates = state.getEquipmentStates();

for (Map.Entry<String, EquipmentState> entry : equipmentStates.entrySet()) {
    String name = entry.getKey();
    EquipmentState eqState = entry.getValue();
    
    System.out.println(name + ":");
    System.out.println("  Type: " + eqState.getEquipmentType());
    System.out.println("  Parameters: " + eqState.getParameters());
}
```

### Stream States

```java
// Get stream states
Map<String, StreamState> streamStates = state.getStreamStates();

for (Map.Entry<String, StreamState> entry : streamStates.entrySet()) {
    StreamState ss = entry.getValue();
    System.out.println(entry.getKey() + ":");
    System.out.println("  T: " + ss.getTemperature() + " K");
    System.out.println("  P: " + ss.getPressure() + " bara");
    System.out.println("  Flow: " + ss.getMolarFlowRate() + " mol/hr");
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

### Version Comparison

```java
// Load two versions
ProcessModelState v1 = ProcessModelState.loadFromFile("models/v1.json");
ProcessModelState v2 = ProcessModelState.loadFromFile("models/v2.json");

// Compare versions
ModelDiff diff = ProcessModelState.compare(v1, v2);

System.out.println("Added equipment: " + diff.getAddedEquipment());
System.out.println("Removed equipment: " + diff.getRemovedEquipment());
System.out.println("Modified parameters: " + diff.getModifiedParameters());
```

### Schema Migration

```java
// Handle older schema versions
ProcessModelState oldState = ProcessModelState.loadFromFile("legacy_model.json");

if (oldState.getSchemaVersion().compareTo("1.0") < 0) {
    // Migrate from old schema
    oldState = ProcessModelState.migrate(oldState, "1.0");
}
```

---

## Checkpointing & Recovery

### Automatic Checkpointing

```java
import neqsim.process.processmodel.ProcessModel;
import neqsim.process.processmodel.lifecycle.ProcessModelState;

// Enable automatic checkpointing
ProcessModel model = new ProcessModel();
model.setCheckpointEnabled(true);
model.setCheckpointInterval(100);  // Every 100 iterations
model.setCheckpointPath("checkpoints/");

// Run simulation
model.run();  // Automatically saves checkpoints
```

### Manual Checkpointing

```java
// Checkpoint during long simulation
for (int i = 0; i < 1000; i++) {
    model.runOneStep();
    
    if (i % 50 == 0) {
        ProcessModelState checkpoint = ProcessModelState.fromProcessModel(model);
        checkpoint.setVersion("iteration_" + i);
        checkpoint.saveToCompressedFile("checkpoints/step_" + i + ".json.gz");
    }
}
```

### Recovery from Checkpoint

```java
// Recover from last checkpoint
Path checkpointDir = Paths.get("checkpoints/");
Optional<Path> latestCheckpoint = Files.list(checkpointDir)
    .filter(p -> p.toString().endsWith(".json.gz"))
    .max(Comparator.comparing(p -> p.toFile().lastModified()));

if (latestCheckpoint.isPresent()) {
    ProcessModelState recovered = 
        ProcessModelState.loadFromCompressedFile(latestCheckpoint.get().toString());
    ProcessModel model = recovered.toProcessModel();
    
    System.out.println("Recovered from: " + recovered.getVersion());
    model.run();  // Continue simulation
}
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

### Cloud Storage Integration

```java
// Export for cloud storage
ProcessModelState state = ProcessModelState.fromProcessModel(model);
byte[] compressedData = state.toCompressedBytes();

// Upload to cloud storage (example with generic API)
cloudStorage.upload("models/" + state.getName() + "/" + state.getVersion() + ".json.gz", 
    compressedData);

// Download and restore
byte[] downloaded = cloudStorage.download("models/field_model/1.0.0.json.gz");
ProcessModelState restored = ProcessModelState.fromCompressedBytes(downloaded);
```

### REST API Integration

```java
// Expose model state via REST
@Path("/models")
public class ModelStateResource {
    
    @GET
    @Path("/{name}/state")
    @Produces(MediaType.APPLICATION_JSON)
    public String getModelState(@PathParam("name") String modelName) {
        ProcessModel model = modelRepository.get(modelName);
        ProcessModelState state = ProcessModelState.fromProcessModel(model);
        return state.toJson();
    }
    
    @POST
    @Path("/{name}/state")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setModelState(@PathParam("name") String modelName, String json) {
        ProcessModelState state = ProcessModelState.fromJson(json);
        ProcessModel model = state.toProcessModel();
        modelRepository.put(modelName, model);
    }
}
```

### Inter-Process Connections

```java
// Define connections between ProcessSystems
InterProcessConnection connection = new InterProcessConnection();
connection.setSourceProcess("upstream");
connection.setSourceStream("wellhead_manifold_out");
connection.setTargetProcess("pipeline");
connection.setTargetStream("inlet");
connection.setConnectionType(ConnectionType.MATERIAL);

state.addInterProcessConnection(connection);

// Query connections
List<InterProcessConnection> pipelineInputs = 
    state.getConnectionsTo("pipeline");
```

---

## Configuration

### Execution Configuration

```java
// Set execution configuration
ExecutionConfig config = new ExecutionConfig();
config.setSolverType("sequential");
config.setMaxIterations(100);
config.setTolerance(1e-6);
config.setParallelExecution(true);
config.setNumberOfThreads(4);

state.setExecutionConfig(config);
```

### Serialization Options

```java
// Configure JSON serialization
ProcessModelState.SerializationOptions options = 
    new ProcessModelState.SerializationOptions();
options.setPrettyPrint(true);
options.setIncludeTimestamps(true);
options.setCompressStreams(false);
options.setSchemaValidation(true);

String json = state.toJson(options);
```

---

## See Also

- [ProcessModel](../processmodel/ProcessModel.md) - Multi-system process modeling
- [ProcessSystem](../processmodel/ProcessSystem.md) - Single process system
- [Serialization Guide](../../util/serialization.md) - General serialization utilities
- [Digital Twin Integration](../../integration/digital_twins.md) - Digital twin concepts
