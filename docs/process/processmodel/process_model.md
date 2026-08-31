---
title: ProcessModel Class
description: Documentation for the ProcessModel class in NeqSim.
---

Documentation for the ProcessModel class in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Creating a ProcessModel](#creating-a-processmodel)
- [Adding Processes](#adding-processes)
- [Running the Model](#running-the-model)
- [Convergence Tracking](#convergence-tracking)
- [Validation](#validation)
- [Mass Balance](#mass-balance)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.processmodel.ProcessModel`

The `ProcessModel` class manages a collection of `ProcessSystem` objects that can be run together. It provides:
- Multi-process coordination
- Convergence tracking across all processes
- Mass balance verification
- Centralized validation of all processes
- Step-by-step or continuous execution modes

Use `ProcessModel` when you need to simulate interconnected process systems or coordinate multiple flowsheets.

---

## Creating a ProcessModel

### Basic Constructor

```java
import neqsim.process.processmodel.ProcessModel;

// Create empty process model
ProcessModel model = new ProcessModel();
```

---

## Adding Processes

### Adding ProcessSystems

```java
// Create process systems
ProcessSystem gasProcessing = new ProcessSystem("Gas Processing");
gasProcessing.add(feedGas);
gasProcessing.add(separator);
gasProcessing.add(compressor);

ProcessSystem oilProcessing = new ProcessSystem("Oil Processing");
oilProcessing.add(oilFeed);
oilProcessing.add(heater);
oilProcessing.add(stabilizer);

// Add to model
model.add("Gas Processing", gasProcessing);
model.add("Oil Processing", oilProcessing);
```

### Accessing Processes

```java
// Get specific process
ProcessSystem gas = model.get("Gas Processing");

// Get all processes
Collection<ProcessSystem> allProcesses = model.getAllProcesses();
```

---

## Running the Model

### Continuous Mode (Default)

```java
// Run until convergence or max iterations
model.run();

// Check if converged
if (model.isModelConverged()) {
    System.out.println("Model converged in " + model.getLastIterationCount() + " iterations");
}
```

### Step Mode

```java
// Enable step mode
model.setRunStep(true);

// Run one step at a time
model.run();  // Runs one step for each process
```

#### Per-area control: fully solve selected areas in step mode

By default every `ProcessSystem` advances a single pass per model step when the
model is in step mode. You can override this for individual areas so that a
selected area is solved to full convergence on each model step while the others
still advance only one pass. This is useful for fast inner loops (for example an
anti-surge recycle) that should settle within each step.

```java
ProcessModel model = new ProcessModel();
model.add("Compression", compressionSystem); // contains an anti-surge recycle
model.add("Export", exportSystem);

// Fully solve the compression area on every model step; Export still single-steps
compressionSystem.setSolveFullyInModelStep(true);

model.setRunStep(true);
model.run();  // Compression converges fully, Export advances one pass
```

The flag is read by `ProcessModel` when it runs each child area: areas with
`isSolveFullyInModelStep() == true` are executed with `run()` (run to
convergence), all others with `run_step()` (single pass). The setting is
preserved across `ProcessSystem.copy()`.

### Optimized Execution

```java
// Enable optimized execution (default is true)
model.setUseOptimizedExecution(true);
model.run();

// Each ProcessSystem uses runOptimized() internally
```

### Asynchronous Execution

```java
// Run in background thread
Future<?> task = model.runAsTask();

// Do other work...

// Wait for completion
task.get();
```

### Multiphase (Three-Phase) Flash Control

The three-phase flash can be switched on or off per process area. On a large
multi-area plant this is usually the cheapest available speed-up: the separation
trains keep the check (free water, glycol, MEG), while the recompression,
export-compression and fuel-gas areas — which are known to be two-phase only —
skip the extra phase-stability analysis on every flash of every recycle
iteration.

```java
// All areas at once
int fluidsUpdated = model.setMultiPhaseCheck(true);

// ...then turn it off only where a third phase cannot form.
// Returns the number of fluids updated, or -1 if the area name is unknown.
model.setMultiPhaseCheck("Export train A", false);
model.setMultiPhaseCheck("Export train B", false);
model.setMultiPhaseCheck("TEX process A", false);
```

Python:

```python
plant.setMultiPhaseCheck(True)                       # baseline for all areas
plant.setMultiPhaseCheck("Export train A", False)    # dry gas only
plant.setMultiPhaseCheck("Export train B", False)

if plant.setMultiPhaseCheck("typo in area name", False) == -1:
    raise KeyError("no such process area")
```

The per-area setting is stored on the child `ProcessSystem` and re-applied at the
start of each of its runs, so it survives the repeated area passes of
`run()` / `runUntilConverged(...)` / step mode. See
[ProcessSystem](process_system.md#multiphase-three-phase-flash-control) for the
full semantics and the correctness warning.

### Physical-Property Initialization Level

`setPropertyInitLevel` follows exactly the same plant-wide / per-area pattern and
controls how much of `initProperties()` runs after each stream flash. Selecting
`DENSITY_ONLY` skips the viscosity, thermal-conductivity and diffusivity
correlations.

```java
// Mass balances only across the plant...
int streamsUpdated = model.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);

// ...but full properties where transport properties are actually read.
// Returns the number of streams updated, or -1 if the area name is unknown.
model.setPropertyInitLevel("Subsea", Stream.PropertyInitLevel.FULL);
model.setPropertyInitLevel("Cooling water", Stream.PropertyInitLevel.FULL);
```

Python:

```python
PropertyInitLevel = jneqsim.process.equipment.stream.Stream.PropertyInitLevel

plant.setPropertyInitLevel(PropertyInitLevel.DENSITY_ONLY)
plant.setPropertyInitLevel("Subsea", PropertyInitLevel.FULL)

if plant.setPropertyInitLevel("typo in area name", PropertyInitLevel.FULL) == -1:
    raise KeyError("no such process area")
```

> **Warning:** under `DENSITY_ONLY` the skipped properties read back as `0.0`
> rather than raising. See
> [ProcessSystem](process_system.md#physical-property-initialization-level).

---

## Convergence Tracking

### Setting Tolerances

```java
// Set individual tolerances
model.setFlowTolerance(1e-5);        // Relative flow error
model.setTemperatureTolerance(1e-5); // Relative temperature error
model.setPressureTolerance(1e-5);    // Relative pressure error

// Or set all at once
model.setTolerance(1e-5);

// Set maximum iterations
model.setMaxIterations(100);
```

### Checking Convergence

```java
model.run();

// Check overall convergence
boolean converged = model.isModelConverged();

// Get convergence errors
double flowErr = model.getLastMaxFlowError();
double tempErr = model.getLastMaxTemperatureError();
double pressErr = model.getLastMaxPressureError();
double maxErr = model.getError();

// Get detailed summary
System.out.println(model.getConvergenceSummary());
```

### Run Until Converged (Agent-Friendly)

For a large multi-area model, `runUntilConverged(maxIterations)` enables the
automatic convergence tuner. It measures total feed-boundary mass flow (not the
largest internal or recycle stream), derives consistent absolute and low-flow
thresholds, and requires a full validation sweep after applying them.

```java
boolean converged = model.runUntilConverged(100);
System.out.println(model.getAutoTuningSummary());
```

Caller settings always take precedence. Explicit
`setBoundaryFlowFloor(...)`, `setAbsoluteFlowTolerance(...)`, and per-equipment
`setMinimumFlow(...)` values are not overwritten. During an auto-tuned run,
recycles without a caller-owned setting may also enable adaptive Wegstein
acceleration if direct substitution stalls. Ordinary `ProcessSystem.run()`
retains legacy direct substitution; call `recycle.setAdaptiveAcceleration(true)`
to opt in there, or `setAdaptiveAcceleration(false)` to pin an opt-out. Automatic
ownership survives `ProcessSystem.copy()` and reset operations.

Use the two-argument overload when you also want to set the relative tolerance:

```java
boolean converged = model.runUntilConverged(100, 1e-5);
if (!converged) {
  System.out.println("Model did not converge: " + model.getConvergenceReportJson());
}
```

For physically significant small streams, set an explicit unit threshold or
disable automatic low-flow bypass with `setAutoLowFlowBypass(false)`. Disable
all automatic tuning with `setAutoConvergenceTuning(false)`. Invalid iteration
budgets or non-positive/non-finite relative tolerances throw
`IllegalArgumentException`.

### Structured Convergence Report (JSON)

`getConvergenceReportJson()` is the machine-readable counterpart to `getConvergenceSummary()`.
It is schema-versioned and includes the per-area solved status and the names of any unsolved
units, so an agent can pinpoint exactly where a large multi-area model failed to converge.

```java
String json = model.getConvergenceReportJson();
```

```json
{
  "schemaVersion": "1.0",
  "converged": false,
  "iterations": 100,
  "maxIterations": 100,
  "boundaryStreamCount": 2,
  "boundaryValuesConverged": false,
  "allProcessesSolved": false,
  "maxError": 0.0042,
  "errors": {
    "flow":        { "value": 0.0042, "tolerance": 1e-5, "converged": false },
    "temperature": { "value": 8.0e-6, "tolerance": 1e-5, "converged": true },
    "pressure":    { "value": 1.2e-6, "tolerance": 1e-5, "converged": true }
  },
  "areas": [
    { "name": "Separation",  "solved": true,  "unsolvedUnits": [] },
    { "name": "Compression", "solved": false, "unsolvedUnits": ["Recycle"] }
  ]
}
```

| Field | Description |
|-------|-------------|
| `schemaVersion` | JSON schema version (`"1.0"`) |
| `converged` | Whether the model converged on the last run |
| `iterations` / `maxIterations` | Outer iterations performed / the configured limit |
| `boundaryStreamCount` | Number of cross-area boundary (tear) streams |
| `boundaryValuesConverged` | Whether all boundary stream values converged |
| `allProcessesSolved` | Whether every contained process reported solved |
| `maxError` | Largest relative error across flow/temperature/pressure |
| `errors` | Per-variable `value` / `tolerance` / `converged` for flow, temperature, pressure |
| `areas` | One entry per area with `name`, `solved`, and `unsolvedUnits` |

---

## Validation

ProcessModel provides comprehensive validation to check that all contained ProcessSystems are properly configured before running.

### Quick Check: isReadyToRun()

```java
// Quick check - returns true if no CRITICAL errors
if (model.isReadyToRun()) {
    model.run();
} else {
    System.out.println("Model not ready to run");
    System.out.println(model.getValidationReport());
}
```

### Detailed Validation: validateSetup()

```java
ValidationResult result = model.validateSetup();

if (!result.isValid()) {
    System.out.println("Validation issues found:");
    System.out.println(result.getReport());
}
```

### Per-Process Validation: validateAll()

```java
Map<String, ValidationResult> allResults = model.validateAll();

for (Map.Entry<String, ValidationResult> entry : allResults.entrySet()) {
    String processName = entry.getKey();
    ValidationResult processResult = entry.getValue();

    if (!processResult.isValid()) {
        System.out.println(processName + " has issues:");
        processResult.getErrors().forEach(System.out::println);
    }
}
```

### Formatted Validation Report

```java
// Get a human-readable validation report
String report = model.getValidationReport();
System.out.println(report);
```

**Example output:**
```
=== ProcessModel Validation Report ===

--- EmptyProcess ---
  [CRITICAL] ProcessSystem is empty
    Fix: Add at least one process equipment using add()

Summary: 1 issue(s) found (1 critical, 0 major)
Ready to run: NO
```

**Validation Methods Summary:**

| Method | Returns | Description |
|--------|---------|-------------|
| `validateSetup()` | `ValidationResult` | Combined result for all processes |
| `validateAll()` | `Map<String, ValidationResult>` | Per-process results |
| `isReadyToRun()` | `boolean` | True if no CRITICAL errors |
| `getValidationReport()` | `String` | Formatted human-readable report |

---

## Mass Balance

### Checking Mass Balance

```java
// Get mass balance for all processes
Map<String, Map<String, ProcessSystem.MassBalanceResult>> results =
    model.checkMassBalance("kg/hr");

// Get failed mass balance checks
Map<String, Map<String, ProcessSystem.MassBalanceResult>> failed =
    model.getFailedMassBalance(0.1);  // 0.1% threshold

// Get formatted reports
System.out.println(model.getMassBalanceReport());
System.out.println(model.getFailedMassBalanceReport());
```

---

## Examples

### Multi-Process Simulation

```java
// Create gas processing system
ProcessSystem gasProcess = new ProcessSystem("Gas Train");
Stream gasIn = new Stream("Gas Feed", gasFluid);
Separator scrubber = new Separator("Inlet Scrubber", gasIn);
Compressor comp = new Compressor("Export Compressor", scrubber.getGasOutStream());
gasProcess.add(gasIn);
gasProcess.add(scrubber);
gasProcess.add(comp);

// Create oil processing system
ProcessSystem oilProcess = new ProcessSystem("Oil Train");
Stream oilIn = new Stream("Oil Feed", oilFluid);
Heater heater = new Heater("Oil Heater", oilIn);
Separator stabilizer = new Separator("Stabilizer", heater.getOutletStream());
oilProcess.add(oilIn);
oilProcess.add(heater);
oilProcess.add(stabilizer);

// Create model and add processes
ProcessModel model = new ProcessModel();
model.add("Gas Train", gasProcess);
model.add("Oil Train", oilProcess);

// Validate before running
if (model.isReadyToRun()) {
    model.run();

    if (model.isModelConverged()) {
        System.out.println("Model converged!");
        System.out.println(model.getConvergenceSummary());
    }
} else {
    System.out.println(model.getValidationReport());
}
```

---

## Saving and Loading

ProcessModel supports saving and loading to/from compressed `.neqsim` files and JSON state files for version control.

```java
// Save to compressed .neqsim file
model.saveToNeqsim("field_model.neqsim");

// Load (auto-runs after loading)
ProcessModel loaded = ProcessModel.loadFromNeqsim("field_model.neqsim");

// Auto-detect format by extension
model.saveAuto("field_model.neqsim");  // Compressed
model.saveAuto("field_model.json");    // JSON state

// JSON state export for version control
ProcessModelState state = model.exportState();
state.setVersion("1.0.0");
state.saveToFile("field_model_v1.0.0.json");
```

For full documentation on serialization options, see [Process Serialization Guide](../../simulation/process_serialization).

---

## Related Documentation

- [ProcessSystem](process_system) - Individual process flowsheets
- [ProcessModule](process_module) - Modular process units
- [Process Serialization](../../simulation/process_serialization) - Save/load processes
- [AI Validation Framework](../../integration/ai_validation_framework) - Validation integration
