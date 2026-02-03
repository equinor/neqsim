---
title: ProcessSystem Class
description: Documentation for the ProcessSystem class in NeqSim.
---

# ProcessSystem Class

Documentation for the ProcessSystem class in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Creating a Process](#creating-a-process)
- [Adding Equipment](#adding-equipment)
- [Running Simulations](#running-simulations)
  - [Thread Safety: Shared Stream Handling](#thread-safety-shared-stream-handling)
- [Results and Reporting](#results-and-reporting)
- [Advanced Features](#advanced-features)
- [Validation](#validation)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.processmodel.ProcessSystem`

The `ProcessSystem` class is the main container for building and running process flowsheets. It:
- Manages equipment registration
- Enforces unique naming
- Handles execution order
- Coordinates recycles and adjusters
- Provides reporting capabilities

---

## Creating a Process

### Basic Constructor

```java
import neqsim.process.processmodel.ProcessSystem;

// Create empty process system
ProcessSystem process = new ProcessSystem();

// Create with name
ProcessSystem process = new ProcessSystem("Gas Processing Plant");
```

---

## Adding Equipment

### Basic Addition

```java
// Add equipment in sequence
process.add(feedStream);
process.add(heater);
process.add(separator);
process.add(compressor);
```

### Equipment Order

Equipment is typically added in flow order, but the ProcessSystem handles dependencies automatically:

```java
// ProcessSystem resolves dependencies
process.add(stream);        // First
process.add(heater);        // Uses stream as input
process.add(separator);     // Uses heater output
process.add(compressor);    // Uses separator gas output
```

### Unique Names

All equipment must have unique names:

```java
Stream stream1 = new Stream("Feed", fluid1);
Stream stream2 = new Stream("Feed", fluid2);  // ERROR: Duplicate name!

// Use unique names
Stream stream1 = new Stream("Feed-1", fluid1);
Stream stream2 = new Stream("Feed-2", fluid2);
```

---

## Running Simulations

### Execution Methods Overview

| Method | Best For | Description |
|--------|----------|-------------|
| `run()` | General use | Sequential execution in insertion order |
| `runOptimized()` | **Recommended** | Auto-selects best strategy based on topology |
| `runParallel()` | Feed-forward processes | Maximum parallelism for no-recycle processes |
| `runHybrid()` | Complex processes | Parallel feed-forward + iterative recycle |

### Recommended: runOptimized()

The `runOptimized()` method automatically analyzes your process and selects the best execution strategy:

```java
// Recommended - auto-selects best strategy
process.runOptimized();

// With calculation ID for tracking
UUID calcId = UUID.randomUUID();
process.runOptimized(calcId);
```

**How it works:**
- **No recycles detected**: Uses `runParallel()` for maximum speed
- **Recycles detected**: Uses `runHybrid()` which runs feed-forward sections in parallel, then iterates on recycle sections

**Performance gains** (typical separation train with 40 units, 3 recycles):
| Mode | Time | Speedup |
|------|------|---------|
| Regular `run()` | 464 ms | baseline |
| Graph-based | 336 ms | 28% |
| `runOptimized()` | 286 ms | **38%** |

### Steady-State Simulation

```java
// Basic sequential execution
process.run();

// Run with calculation ID for tracking
UUID calcId = UUID.randomUUID();
process.run(calcId);
```

### Parallel Execution

For feed-forward processes (no recycles), parallel execution runs independent units simultaneously:

```java
// Run independent units in parallel
try {
    process.runParallel();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**Note:** `runParallel()` does not handle recycles or adjusters. Use `runOptimized()` for processes with recycles.

### Thread Safety: Shared Stream Handling

When multiple units share the same input stream (e.g., a splitter/manifold feeding parallel branches), NeqSim automatically groups them to prevent race conditions.

**How it works:**
1. Units at the same execution level are analyzed for shared input streams
2. Units sharing an input stream are grouped together using a Union-Find algorithm
3. Groups with shared streams run **sequentially** within the group
4. Independent groups (no shared streams) run **in parallel**

**Example - Parallel Pipelines:**
```java
// Three pipelines fed by the same manifold
Stream feedStream = new Stream("feed", fluid);
Splitter manifold = new Splitter("manifold", feedStream, 3);

AdiabaticPipe pipe1 = new AdiabaticPipe("pipe1", manifold.getSplitStream(0));
AdiabaticPipe pipe2 = new AdiabaticPipe("pipe2", manifold.getSplitStream(1));
AdiabaticPipe pipe3 = new AdiabaticPipe("pipe3", manifold.getSplitStream(2));

process.add(feedStream);
process.add(manifold);
process.add(pipe1);
process.add(pipe2);
process.add(pipe3);

// Safe: Each pipe has its own split stream (different objects)
// Pipes run in parallel without race conditions
process.runParallel();
```

**Example - Shared Input Stream (handled automatically):**
```java
// Two units explicitly sharing the same input stream object
Stream sharedInput = valve.getOutletStream();

Heater heater1 = new Heater("heater1", sharedInput);  // Same stream object
Heater heater2 = new Heater("heater2", sharedInput);  // Same stream object

// NeqSim detects shared input and runs heater1 and heater2 sequentially
// Other independent units at this level still run in parallel
process.runParallel();
```

**Applies to:**
- `runParallel()` - Groups by shared input streams
- `runHybrid()` - Groups in Phase 1 (feed-forward section)
- `runTransient()` - Groups at each time step

### Hybrid Execution

For processes with recycles, hybrid execution combines parallel and iterative strategies:

```java
// Hybrid: parallel feed-forward + iterative recycle
try {
    process.runHybrid();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

**How hybrid works:**
1. **Phase 1 (Parallel)**: Run feed-forward units (before recycles) in parallel
2. **Phase 2 (Iterative)**: Run recycle section with graph-based iteration until convergence

### Graph-Based Execution

Enable graph-based execution for optimized unit ordering:

```java
// Enable graph-based execution order
process.setUseGraphBasedExecution(true);
process.run();

// Or use runOptimized() which handles this automatically
process.runOptimized();
```

### Transient Simulation

Transient simulations use graph-based parallel execution for independent branches, applying the same shared-stream grouping as steady-state methods.

```java
// Set time step
double dt = 1.0;  // seconds

// Run single transient step
process.runTransient(dt);

// Run for specified duration
double totalTime = 3600.0;  // 1 hour
for (double t = 0; t < totalTime; t += dt) {
    process.runTransient(dt);
    logResults(t);
}
```

### Transient with Events

```java
// Run transient with event handling
process.runTransient(dt, (time) -> {
    if (time > 600.0) {
        // Open blowdown valve after 10 minutes
        bdv.setOpen(true);
    }
});
```

---

## Execution Strategy Analysis

### Check Process Topology

```java
// Check if process has recycles
boolean hasRecycles = process.hasRecycleLoops();

// Check if parallel execution would be beneficial
boolean useParallel = process.isParallelExecutionBeneficial();

// Get detailed partition analysis
String partitionInfo = process.getExecutionPartitionInfo();
System.out.println(partitionInfo);
```

**Example output:**
```
=== Execution Partition Analysis ===
Total units: 40
Has recycle loops: true
Parallel levels: 29
Max parallelism: 6
Units in recycle loops: 30

=== Hybrid Execution Strategy ===
Phase 1 (Parallel): 4 levels, 8 units
Phase 2 (Iterative): 25 levels, 32 units

Execution levels:
  Level 0 [PARALLEL]: feed TP setter, first stage oil reflux, LP stream temp controller
  Level 1 [PARALLEL]: 1st stage separator
  Level 2 [PARALLEL]: oil depres valve
  Level 3 [PARALLEL]: 
  --- Recycle Section Start (iterative) ---
  Level 4: oil heater second stage [RECYCLE]
  Level 5: 2nd stage separator [RECYCLE]
  ...
```

### Get Parallel Partition Details

```java
// Get parallel partition
ProcessGraph.ParallelPartition partition = process.getParallelPartition();

// Number of execution levels
int levels = partition.getLevelCount();

// Maximum units that can run simultaneously
int maxParallelism = partition.getMaxParallelism();

System.out.println("Execution levels: " + levels);
System.out.println("Max parallelism: " + maxParallelism);
```

---

## Retrieving Equipment

### By Name

```java
// Get specific equipment
Compressor comp = (Compressor) process.getUnit("K-100");
Separator sep = (Separator) process.getUnit("HP Separator");
Stream stream = (Stream) process.getUnit("Feed");
```

### By Type

```java
// Get all compressors
List<CompressorInterface> compressors = process.getUnitsOfType(CompressorInterface.class);

// Get all separators
List<SeparatorInterface> separators = process.getUnitsOfType(SeparatorInterface.class);
```

### All Equipment

```java
// Get all equipment
List<ProcessEquipmentInterface> allUnits = process.getUnitOperations();

for (ProcessEquipmentInterface unit : allUnits) {
    System.out.println(unit.getName() + ": " + unit.getClass().getSimpleName());
}
```

---

## Results and Reporting

### Console Display

```java
// Display summary to console
process.display();
```

### JSON Report

```java
// Get JSON report
String jsonReport = process.getReport_json();

// Save to file
Files.writeString(Path.of("process_report.json"), jsonReport);
```

### Tabular Report

```java
// Get as table
String[][] table = process.getUnitOperationsAsTable();

// Print table
for (String[] row : table) {
    System.out.println(String.join("\t", row));
}
```

### Mass Balance

```java
// Check overall mass balance
double totalIn = 0.0;
double totalOut = 0.0;

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    if (unit instanceof StreamInterface) {
        StreamInterface stream = (StreamInterface) unit;
        if (isInletStream(stream)) {
            totalIn += stream.getFlowRate("kg/hr");
        } else if (isOutletStream(stream)) {
            totalOut += stream.getFlowRate("kg/hr");
        }
    }
}

double balance = (totalIn - totalOut) / totalIn * 100;
System.out.println("Mass balance closure: " + balance + "%");
```

---

## Process Copying

### Clone Process

```java
// Create copy of process
ProcessSystem processCopy = process.copy();

// Modify copy without affecting original
Heater heater = (Heater) processCopy.getUnit("Heater");
heater.setOutTemperature(100.0, "C");
processCopy.run();
```

### Deep Copy

All equipment and streams are deep-copied:

```java
// Original
process.run();
double originalT = ((Stream) process.getUnit("Feed")).getTemperature("C");

// Copy and modify
ProcessSystem copy = process.copy();
((Stream) copy.getUnit("Feed")).setTemperature(50.0, "C");
copy.run();

// Original unchanged
assert originalT == ((Stream) process.getUnit("Feed")).getTemperature("C");
```

---

## Advanced Features

### Execution Strategy Selection

```java
// Use optimized execution (recommended)
process.runOptimized();  // Auto-selects best strategy

// Or manually choose strategy:

// 1. Sequential (default)
process.run();

// 2. Graph-based ordering
process.setUseGraphBasedExecution(true);
process.run();

// 3. Parallel execution (no recycles)
process.runParallel();

// 4. Hybrid execution (recycle processes)
process.runHybrid();
```

### Asynchronous Execution

```java
// Run in background thread
Future<?> task = process.runAsTask();

// Do other work...

// Wait for completion
task.get();

// Or check if done
if (task.isDone()) {
    System.out.println("Simulation complete");
}
```

### Convergence Settings

```java
// Set global convergence tolerance
process.setGlobalTolerance(1e-6);

// Set maximum iterations for recycles
process.setMaxRecycleIterations(50);
```

### Process Modules

```java
// Add pre-built module
ProcessModule compressorTrain = new CompressorTrainModule("HP Compression");
process.addModule(compressorTrain);

// Connect to process
compressorTrain.setInletStream(feedGas);
Stream compressed = compressorTrain.getOutletStream();
```

---

## Validation

ProcessSystem provides comprehensive validation to check that all equipment is properly configured before running a simulation. This helps catch configuration errors early and provides actionable error messages.

### Quick Check: isReadyToRun()

The simplest way to validate a process before execution:

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);

// Quick check - returns true if no CRITICAL errors
if (process.isReadyToRun()) {
    process.run();
} else {
    System.out.println("Process not ready to run");
    ValidationResult result = process.validateSetup();
    result.getErrors().forEach(System.out::println);
}
```

### Detailed Validation: validateSetup()

Get a combined `ValidationResult` for the entire process system:

```java
ValidationResult result = process.validateSetup();

if (!result.isValid()) {
    System.out.println("Validation issues found:");
    System.out.println(result.getReport());
    
    // Iterate through specific issues
    for (ValidationIssue issue : result.getIssues()) {
        System.out.println(issue.getSeverity() + ": " + issue.getMessage());
        System.out.println("  Fix: " + issue.getRemediation());
    }
}
```

**Severity Levels:**
| Level | Description |
|-------|-------------|
| `CRITICAL` | Blocks execution - must be fixed |
| `MAJOR` | Likely to cause errors during simulation |
| `MINOR` | May affect accuracy of results |
| `INFO` | Informational warnings |

### Per-Equipment Validation: validateAll()

Get individual validation results for each piece of equipment:

```java
Map<String, ValidationResult> allResults = process.validateAll();

for (Map.Entry<String, ValidationResult> entry : allResults.entrySet()) {
    String equipmentName = entry.getKey();
    ValidationResult equipResult = entry.getValue();
    
    if (!equipResult.isValid()) {
        System.out.println(equipmentName + " has issues:");
        equipResult.getErrors().forEach(e -> System.out.println("  - " + e));
    }
}
```

### Equipment-Level Validation

Each equipment class implements `validateSetup()` to check equipment-specific requirements:

| Equipment | Validates |
|-----------|-----------|
| Stream | Has fluid set, temperature > 0 K |
| Separator | Inlet stream connected |
| Mixer | At least one inlet stream |
| Splitter | Inlet stream connected, split fractions sum to 1.0 |
| Tank | Has fluid or input stream |
| DistillationColumn | Feed streams connected, condenser/reboiler configured |
| Recycle | Inlet and outlet streams connected, tolerance > 0 |
| Adjuster | Target and adjustment variables set, tolerance > 0 |
| TwoPortEquipment | Inlet stream connected |

**Example - Individual Equipment Validation:**

```java
Separator separator = new Separator("V-100");
// Forgot to set inlet stream

ValidationResult result = separator.validateSetup();
if (!result.isValid()) {
    // Will report: "Separator 'V-100' has no inlet stream connected"
    System.out.println(result.getReport());
}
```

### Validation in AI/ML Workflows

For AI agents and automated workflows, validation provides structured feedback:

```java
AIIntegrationHelper helper = AIIntegrationHelper.forProcess(process);

if (helper.isReady()) {
    ExecutionResult result = helper.safeRun();
} else {
    // Get issues as structured text for AI to parse
    String[] issues = helper.getIssuesAsText();
    for (String issue : issues) {
        // AI can parse and fix these issues
        System.out.println(issue);
    }
}
```

See [AI Validation Framework](../../integration/ai_validation_framework) for more details on AI integration.

---

## Examples

### Simple Separation Process

```java
ProcessSystem process = new ProcessSystem("Separator System");

// Create fluid
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("n-butane", 0.03);
fluid.setMixingRule("classic");

// Feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(100000.0, "kg/hr");
process.add(feed);

// Inlet valve
ThrottlingValve inletValve = new ThrottlingValve("Inlet Valve", feed);
inletValve.setOutletPressure(30.0, "bara");
process.add(inletValve);

// HP Separator
Separator hpSep = new Separator("HP Separator", inletValve.getOutletStream());
process.add(hpSep);

// LP Valve
ThrottlingValve lpValve = new ThrottlingValve("LP Valve", hpSep.getLiquidOutStream());
lpValve.setOutletPressure(5.0, "bara");
process.add(lpValve);

// LP Separator
Separator lpSep = new Separator("LP Separator", lpValve.getOutletStream());
process.add(lpSep);

// Run
process.run();

// Results
System.out.println("HP Gas: " + hpSep.getGasOutStream().getFlowRate("MSm3/day") + " MSm3/day");
System.out.println("LP Gas: " + lpSep.getGasOutStream().getFlowRate("MSm3/day") + " MSm3/day");
System.out.println("Liquid: " + lpSep.getLiquidOutStream().getFlowRate("m3/hr") + " m3/hr");
```

### Compression System

```java
ProcessSystem process = new ProcessSystem("Compression System");

// Gas feed
Stream gas = new Stream("Gas Feed", gasFluid);
gas.setFlowRate(50000.0, "Sm3/hr");
gas.setTemperature(40.0, "C");
gas.setPressure(5.0, "bara");
process.add(gas);

// First stage compressor
Compressor comp1 = new Compressor("K-101", gas);
comp1.setOutletPressure(15.0, "bara");
comp1.setPolytropicEfficiency(0.78);
process.add(comp1);

// Intercooler
Cooler cooler1 = new Cooler("E-101", comp1.getOutletStream());
cooler1.setOutTemperature(40.0, "C");
process.add(cooler1);

// Second stage compressor
Compressor comp2 = new Compressor("K-102", cooler1.getOutletStream());
comp2.setOutletPressure(45.0, "bara");
comp2.setPolytropicEfficiency(0.78);
process.add(comp2);

// Aftercooler
Cooler cooler2 = new Cooler("E-102", comp2.getOutletStream());
cooler2.setOutTemperature(40.0, "C");
process.add(cooler2);

// Run
process.run();

// Total power
double totalPower = comp1.getPower("kW") + comp2.getPower("kW");
System.out.println("Total compression power: " + totalPower + " kW");
```

### Process with Recycle

```java
ProcessSystem process = new ProcessSystem("Recycle Process");

// Fresh feed
Stream freshFeed = new Stream("Fresh Feed", freshFluid);
freshFeed.setFlowRate(1000.0, "kg/hr");
process.add(freshFeed);

// Mixer for fresh feed and recycle
Mixer feedMixer = new Mixer("Feed Mixer");
feedMixer.addStream(freshFeed);
process.add(feedMixer);

// Reactor
GibbsReactor reactor = new GibbsReactor("Reactor");
reactor.setInletStream(feedMixer.getOutletStream());
process.add(reactor);

// Product separator
Separator productSep = new Separator("Product Sep", reactor.getOutletStream());
process.add(productSep);

// Product stream
Stream product = productSep.getLiquidOutStream();

// Recycle unreacted gas
Recycle recycle = new Recycle("Gas Recycle");
recycle.addStream(productSep.getGasOutStream());
recycle.setOutletStream(feedMixer);
recycle.setTolerance(1e-5);
process.add(recycle);

// Complete the connection
feedMixer.addStream(recycle.getOutletStream());

// Run (will iterate until recycle converges)
process.run();

System.out.println("Recycle converged: " + recycle.isConverged());
System.out.println("Product rate: " + product.getFlowRate("kg/hr") + " kg/hr");
```

---

## Saving and Loading

ProcessSystem supports saving and loading to/from compressed `.neqsim` files and JSON state files for version control.

```java
// Save to compressed .neqsim file (recommended)
process.saveToNeqsim("my_process.neqsim");

// Load (auto-runs after loading)
ProcessSystem loaded = ProcessSystem.loadFromNeqsim("my_process.neqsim");

// Auto-detect format by extension
process.saveAuto("my_process.neqsim");  // Compressed XStream XML
process.saveAuto("my_process.json");    // JSON state export

// JSON state for version control
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("1.0.0");
state.saveToFile("my_process_v1.0.0.json");
```

For full documentation on serialization options, see [Process Serialization Guide](../../simulation/process_serialization).

---

## Related Documentation

- [ProcessModel](process_model) - Multi-process container
- [ProcessModule](process_module) - Modular process units
- [Process Serialization](../../simulation/process_serialization) - Save/load processes
- [Graph Simulation](graph_simulation) - Graph-based execution
- [Equipment Overview](../equipment/README) - Process equipment
