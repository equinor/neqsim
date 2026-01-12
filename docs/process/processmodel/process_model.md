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

For full documentation on serialization options, see [Process Serialization Guide](../../simulation/process_serialization.md).

---

## Related Documentation

- [ProcessSystem](process_system.md) - Individual process flowsheets
- [ProcessModule](process_module.md) - Modular process units
- [Process Serialization](../../simulation/process_serialization.md) - Save/load processes
- [AI Validation Framework](../../integration/ai_validation_framework.md) - Validation integration
