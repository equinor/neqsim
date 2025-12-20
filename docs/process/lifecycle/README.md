# Process Model Lifecycle Management

This module provides infrastructure for managing the lifecycle of process models (digital twins) from concept through operation to archival.

## Overview

As process simulation models evolve from early feasibility studies to live digital twins, they require:

- **State Management**: Save, restore, and version control simulation states
- **Lifecycle Tracking**: Track model evolution through design phases
- **Audit Trail**: Record modifications for compliance and knowledge preservation
- **Quality Metrics**: Track model accuracy and calibration status

## Classes

### ProcessSystemState

Serializable state snapshot of a `ProcessSystem` for checkpointing and version control.

#### Key Features

- **JSON Export/Import**: Human-readable state serialization compatible with Git
- **Equipment State Capture**: Captures all equipment settings and fluid states
- **Equipment-Specific Properties**: Detailed capture of equipment-type properties
- **Integrity Verification**: Checksum-based validation of state files
- **Version Tracking**: Support for semantic versioning of model states

#### Equipment-Specific Properties Captured

| Equipment Type | Properties Captured |
|---------------|--------------------|
| Compressor | outletPressure, polytropicEfficiency, isentropicEfficiency, power, speed |
| Pump | outletPressure, power |
| Valve | percentValveOpening, outletPressure, Cv |
| Heater | duty, outletTemperature |
| Cooler | duty, outletTemperature |
| Separator | pressure, temperature, liquidLevel |
| Stream | temperature, pressure, flowRate |

#### Usage Example

```java
// Create a process system
ProcessSystem process = new ProcessSystem();
process.setName("GasProcessingPlant");
// ... configure process ...
process.run();

// Export state to file
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("1.2.3");
state.setDescription("Post-commissioning tuned model");
state.setCreatedBy("john.doe@company.com");
state.saveToFile("model_v1.2.3.json");

// Later: restore state
ProcessSystemState loaded = ProcessSystemState.loadFromFile("model_v1.2.3.json");
if (loaded.validateIntegrity()) {
    ProcessSystem restored = loaded.toProcessSystem();
}
```

#### Convenience Methods on ProcessSystem

```java
// Quick state export
ProcessSystemState state = process.exportState();

// Save directly to file
process.exportStateToFile("checkpoint.json");

// Load and apply state
process.loadStateFromFile("checkpoint.json");
```

### ModelMetadata

Tracks lifecycle phases, validation history, and calibration status.

#### Lifecycle Phases

| Phase | Description |
|-------|-------------|
| `CONCEPT` | Early screening and feasibility studies |
| `DESIGN` | Detailed engineering (FEED, Detailed Design) |
| `COMMISSIONING` | Construction and commissioning |
| `OPERATION` | Live digital twin, actively used |
| `LATE_LIFE` | Decommissioning planning |
| `ARCHIVED` | No longer in active use |

#### Calibration Status

| Status | Description |
|--------|-------------|
| `UNCALIBRATED` | Never validated against real data |
| `CALIBRATED` | Validated but may be outdated |
| `IN_PROGRESS` | Calibration underway |
| `FRESHLY_CALIBRATED` | Recent validation, high confidence |
| `NEEDS_RECALIBRATION` | Model diverged from plant data |

#### Usage Example

```java
ModelMetadata metadata = new ModelMetadata();
metadata.setAssetId("PLATFORM-A-TRAIN-1");
metadata.setAssetName("Gas Processing Train 1");
metadata.setLifecyclePhase(LifecyclePhase.OPERATION);
metadata.setResponsibleEngineer("jane.doe@company.com");

// Record validation against field data
metadata.recordValidation(
    "Matched well test data within 2%", 
    "WELL-TEST-2024-01"
);

// Record model modifications
metadata.recordModification(
    "Tuned compressor curves based on vendor data",
    "john.smith@company.com"
);

// Update calibration status
metadata.updateCalibration(CalibrationStatus.FRESHLY_CALIBRATED, 0.02);

// Check if revalidation is needed (after 90 days)
if (metadata.needsRevalidation(90)) {
    System.out.println("Model needs revalidation");
}
```

## Integration with Git-Based Workflows

The JSON export format is designed for version control:

```bash
# Commit model checkpoints
git add model_v1.2.3.json
git commit -m "feat: Updated compressor curves after field test"

# Compare model versions
git diff model_v1.2.2.json model_v1.2.3.json

# Track model evolution over time
git log --oneline -- model_*.json
```

## Best Practices

1. **Regular Checkpoints**: Save state after major modifications
2. **Semantic Versioning**: Use `MAJOR.MINOR.PATCH` for model versions
3. **Validation Records**: Document every validation against field data
4. **Modification History**: Record all changes with author attribution
5. **Lifecycle Transitions**: Update phase when model role changes

## Related Documentation

- [Emissions Tracking](../sustainability/README.md)
- [Safety Scenarios](../safety/README.md)
- [Batch Studies](../optimization/README.md)
