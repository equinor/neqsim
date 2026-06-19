---
title: ProcessModel Class
description: Documentation for the ProcessModel class in NeqSim.
---

# ProcessModel Class

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

`runUntilConverged(maxIterations, tolerance)` is an explicit wrapper around `run()` that
guarantees iterating (multi-area) mode and applies the iteration limit and tolerance in one
call — so you do not need to manually configure `setRunStep(false)`, `setMaxIterations(...)`,
`setTolerance(...)` and write your own outer loop.

```java
boolean converged = model.runUntilConverged(100, 1e-5);
if (!converged) {
  System.out.println("Model did not converge: " + model.getConvergenceReportJson());
}
```

It throws `IllegalArgumentException` if `maxIterations < 1` or `tolerance` is not a finite
positive number.

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
