---
title: ProcessSystem Class
description: ProcessSystem flowsheet execution, optimized scheduling, recycles, parallel and dataflow strategies, shared-stream safety, transient stepping, and diagnostics.
keywords: "ProcessSystem, flowsheet, process model, run, add equipment, recycle, adjuster, simulation, process train"
---

Build, execute, diagnose, and report NeqSim flowsheets with topology-aware steady-state and transient strategies.

## Table of Contents
- [Overview](#overview)
- [Creating a Process](#creating-a-process)
- [Adding Equipment](#adding-equipment)
- [Stream Introspection](#stream-introspection)
- [Explicit Connections](#explicit-connections)
- [Named Controllers](#named-controllers)
- [Unified Element Model](#unified-element-model)
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

## Stream Introspection

Every equipment class exposes its inlet and outlet streams through a uniform API. This allows tools, graph builders, and DEXPI exporters to discover the process topology without casting to specific equipment types.

### Querying Streams

```java
// Works on any ProcessEquipmentInterface — no casting needed
List<StreamInterface> inlets = equipment.getInletStreams();
List<StreamInterface> outlets = equipment.getOutletStreams();

System.out.println(equipment.getName() + " has "
    + inlets.size() + " inlet(s) and "
    + outlets.size() + " outlet(s)");
```

### Per-Equipment Behavior

| Equipment | Inlets | Outlets |
|-----------|--------|---------|
| `Stream` (feed) | 0 | 0 (feed streams are boundary conditions) |
| `TwoPortEquipment` (Heater, Compressor, Valve, Pipe, ...) | 1 | 1 |
| `Separator` | N (via internal mixer) | 2 (gas, liquid) |
| `ThreePhaseSeparator` | N (via internal mixer) | 3 (gas, oil, water) |
| `Mixer` | N | 1 |
| `Splitter` | 1 | N |

### Example: Walk the Flowsheet

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

for (ProcessEquipmentInterface unit : process.getUnitOperations()) {
    List<StreamInterface> ins = unit.getInletStreams();
    List<StreamInterface> outs = unit.getOutletStreams();
    System.out.printf("%-20s  in=%d  out=%d%n",
        unit.getName(), ins.size(), outs.size());
}
```

The returned lists are **unmodifiable** — they are read-only views of the equipment's current connections. To change connections, use the equipment's own setters (e.g., `addStream()`, `setInletStream()`).

---

## Explicit Connections

`ProcessSystem` can record explicit connection metadata between equipment. This is used by DEXPI import/export, diagram generation, and topology analysis.

### Declaring Connections

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);

// Record that feed connects to separator, via a specific stream
process.connect(feed, separator, feed.getOutletStream(),
    ProcessConnection.ConnectionType.MATERIAL, "Feed to HP Sep");

// Simple connection (defaults to MATERIAL type)
process.connect(separator, compressor);
```

### Connection Types

| Type | Description |
|------|-------------|
| `MATERIAL` | Process stream carrying fluid (default) |
| `ENERGY` | Energy stream (heat duty, shaft power) |
| `SIGNAL` | Instrument signal (controller, transmitter) |

### Querying Connections

```java
List<ProcessConnection> connections = process.getConnections();

for (ProcessConnection conn : connections) {
    System.out.printf("%s -> %s [%s] %s%n",
        conn.getSource().getName(),
        conn.getTarget().getName(),
        conn.getType(),
        conn.getLabel());
}
```

**Note:** Connections are metadata — they do not change how the simulation runs. The actual data flow is determined by stream references set on each equipment.

---

## Named Controllers

Equipment supports **named controllers** alongside the legacy single-controller API. This allows multiple controllers to be attached to the same equipment and retrieved by tag.

### Adding Multiple Controllers

```java
// Legacy API (still works, unchanged)
valve.setController(levelController);

// New named API — attach multiple controllers by tag
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);
```

### Retrieving Controllers

```java
// By tag
ControllerDeviceInterface lc = valve.getController("LC-100");
ControllerDeviceInterface pc = valve.getController("PC-200");

// All controllers on this equipment
Collection<ControllerDeviceInterface> all = valve.getControllers();
System.out.println("Controllers: " + all.size());
```

### Backward Compatibility

The legacy `setController()` method still works and also registers the controller in the named map (using the controller's name as the key). Existing code does not need any changes:

```java
// Old code — still works exactly as before
valve.setController(myController);

// The controller is now also accessible via the named map
valve.getController(myController.getName()); // returns myController
valve.getControllers();                      // returns [myController]
```

---

## Unified Element Model

All elements that can live inside a `ProcessSystem` — equipment, measurement devices, and controller devices — share a common marker interface: `ProcessElementInterface`.

### Type Hierarchy

```
ProcessElementInterface (extends NamedInterface, Serializable)
    ├── ProcessEquipmentInterface  — unit operations and streams
    ├── MeasurementDeviceInterface — transmitters and sensors
    └── ControllerDeviceInterface  — PID controllers
```

### Querying All Elements

```java
// Get everything in the process — equipment + measurements + controllers
List<ProcessElementInterface> all = process.getAllElements();

for (ProcessElementInterface elem : all) {
    System.out.println(elem.getName() + " : " + elem.getClass().getSimpleName());
}
```

This is useful for DEXPI export, diagram generation, and generic process analysis where you need a flat list of every element regardless of type.

### Adding Controller Devices to ProcessSystem

Controller devices can be registered directly on the ProcessSystem. During transient simulation, the system automatically scans and executes all registered controllers after the equipment loop:

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(valve);

// Register controller at system level
process.add(levelController);

// During runTransient(), controllers are executed automatically
// after all equipment has been stepped
process.runTransient(1.0, calcId);
```

---

## Running Simulations

### Execution methods

`run()` is the normal entry point. Optimized execution is enabled by default, so `run()`
delegates to `runOptimized(UUID)`. Call `setUseOptimizedExecution(false)` only when a
single-threaded insertion-order or graph-ordered diagnostic run is required.

| Entry point | Contract | Use |
|----------|----------|----------|
| `run()` / `run(UUID)` | Uses the optimized dispatcher by default | Normal steady-state execution |
| `runOptimized()` / `runOptimized(UUID)` | Selects sequential, hybrid, dataflow, or level-parallel execution | Preferred explicit dispatcher |
| `runSequential(UUID)` | Runs one unit at a time | Diagnostics or deliberate optimized-execution opt-out |
| `runParallel()` / `runParallel(UUID)` | Uses topological levels and waits at each level barrier | Feed-forward flowsheets |
| `runDataflow(UUID)` | Starts each task after its direct predecessors complete | Wide, asymmetric feed-forward flowsheets |
| `runHybrid(UUID)` | Runs the feed-forward prefix in parallel and iterates the recycle section | Recycle flowsheets |
| `runTransient()` / `runTransient(double, UUID)` | Advances one transient step | Dynamic simulation |

There are no zero-argument `runDataflow()`, `runHybrid()`, or `runSequential()` overloads.
The direct methods throw `InterruptedException`; application code must preserve the interrupted
status or otherwise apply its interruption policy.

### Optimized strategy selection

The dispatcher applies these source-owned rules:

1. An `Adjuster` or `MultiVariableAdjuster` selects `runSequential(UUID)` because its implicit
   feedback is not represented by stream dependencies.
2. A `Recycle` with no adjuster selects `runHybrid(UUID)`.
3. A feed-forward graph that is sufficiently large and has useful parallel tasks selects
   `runDataflow(UUID)`.
4. Other feed-forward graphs select `runParallel(UUID)`.

Multi-input equipment is supported in both feed-forward strategies. Predecessor ordering keeps a
mixer, manifold, or heat exchanger behind its producers. Shared mutable input streams are handled
as described below.

Use one calculation identifier for all units in a run:

```java
import java.util.UUID;

UUID calculationId = UUID.randomUUID();
process.runOptimized(calculationId);
```

The dispatcher catches an interruption from a direct hybrid, dataflow, or parallel attempt,
restores the thread's interrupted status, and falls back to sequential execution. Code that calls a
direct strategy remains responsible for handling `InterruptedException`.

### Explain the selected strategy

Inspect the current topology instead of relying on fixed performance claims:

```java
String strategy = process.getExecutionStrategyExplanation();
String partition = process.getExecutionPartitionInfo();

boolean parallelCandidate = process.isParallelExecutionBeneficial();
int levels = process.getParallelPartition().getLevelCount();
int maximumParallelism = process.getParallelPartition().getMaxParallelism();
```

`getExecutionStrategyExplanation()` names the selected strategy and controlling adjusters,
recycles, calculators, or multi-input equipment. `getExecutionPartitionInfo()` reports topology
levels and recycle sections. These diagnostics describe the current process structure; they are not
a throughput guarantee. Benchmark the actual flowsheet on its target JVM and hardware before
choosing a direct strategy for performance reasons.

### Shared-stream safety

Parallel and dataflow execution group consumers that share the same mutable
`StreamInterface` object. Units inside a shared-input group run sequentially; independent groups
can run concurrently. This protects thermodynamic cloning and initialization, which are not
read-only operations even for two single-input consumers.

A `Splitter` creates distinct output stream objects, so separate branches can remain parallel:

```java
Stream feed = new Stream("feed", fluid);
Splitter splitter = new Splitter("splitter", feed, 2);
Heater firstBranch = new Heater("first branch", splitter.getSplitStream(0));
Heater secondBranch = new Heater("second branch", splitter.getSplitStream(1));
```

By contrast, two units constructed with exactly the same stream object are placed in one sequential
task:

```java
Stream sharedInput = valve.getOutletStream();
Heater firstConsumer = new Heater("first consumer", sharedInput);
Heater secondConsumer = new Heater("second consumer", sharedInput);
```

This grouping applies to `runParallel(UUID)`, `runDataflow(UUID)`, and the feed-forward phase
of `runHybrid(UUID)`. Direct parallel/dataflow execution does not solve recycles or adjusters; use
the optimized dispatcher for those topologies.

### Graph-ordered sequential diagnostics

`setUseGraphBasedExecution(true)` changes the order used by the legacy sequential path. Because
optimized execution is enabled by default, opt out explicitly when graph-ordered single-threaded
execution is the intended diagnostic:

```java
process.setUseOptimizedExecution(false);
process.setUseGraphBasedExecution(true);
process.run();
```

This mode derives a topological order from stream references. It does not make explicit
`ProcessConnection` metadata drive material flow.

### Transient stepping

Transient equipment execution is sequential by default. Use either the configured timestep or the
explicit `double, UUID` overload:

```java
process.runTransient();

double timestepSeconds = 1.0;
UUID calculationId = UUID.randomUUID();
process.runTransient(timestepSeconds, calculationId);
```

The timestep must be finite and greater than zero. A `runTransient(double)` convenience overload
and a `runTransient(double, callback)` overload do not exist.

Parallel transient equipment stepping is opt-in:

```java
process.setParallelTransientEnabled(true);
process.setTransientThreadPoolSize(4);
process.runTransient(timestepSeconds, calculationId);
```

When enabled, process-graph level barriers and shared-input grouping protect independent transient
branches. Recycle and other iterative transient couplings still require their own convergence
semantics. Event-driven actions are configured through the process event scheduler; they are not a
callback argument to `runTransient`.

---

## Execution Strategy Analysis

Use `getExecutionStrategyExplanation()` for the dispatch decision and controlling units. Use
`getExecutionPartitionInfo()` or `getParallelPartition()` for detailed topology information.
The partition reports levels and possible concurrency before shared-input groups and runtime unit
costs are considered.

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

Prefer `run()` or `runOptimized()`. When diagnosing a specific feed-forward or recycle
topology, call the exact UUID-bearing direct methods documented in
[Running Simulations](#running-simulations). Direct dataflow and hybrid execution do not have
zero-argument overloads.

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

### Multiphase (Three-Phase) Flash Control

Every flash performed by the equipment in a process area either does or does not
run the extra phase-stability analysis that can split a second liquid out of the
mixture. On an area that is known to be two-phase only — a dry-gas recompression
train, an export-compression train, a fuel-gas header — that work is pure
overhead, and it is repeated on every unit of every recycle iteration.

`setMultiPhaseCheck(boolean)` switches the three-phase flash on or off for the
whole area in one call:

```java
// Turn the three-phase flash off on a dry-gas area
int fluidsUpdated = compressionTrain.setMultiPhaseCheck(false);

// ...and back on where a water phase can appear
separationTrain.setMultiPhaseCheck(true);

// Query the current setting (null = never configured)
Boolean setting = compressionTrain.getMultiPhaseCheck();
```

Behaviour:

- The setting is applied **immediately** to every fluid held by the unit
  operations and by their inlet and outlet streams, and is propagated into nested
  `ModuleInterface` sub-processes. The return value is the number of distinct
  fluids updated (a fluid shared by two units is counted once).
- It is **re-applied at the start of every run** — `run(UUID)`,
  `run_step(UUID)`, `runSequential(UUID)`, `runParallel(UUID)`, `runHybrid(UUID)`,
  `runDataflow(UUID)` and `runTransient(double, UUID)` — so equipment that
  temporarily enables the check (`ThreePhaseSeparator` does this for its own
  flash) cannot leak three-phase mode into the rest of the area across recycle
  iterations.
- The default is **unset** (`getMultiPhaseCheck()` returns `null`), which leaves
  the multiphase flag of each fluid exactly as the fluid was built. Existing
  models are unaffected until the method is called.

> **Warning:** turning the check off on an area where a second liquid phase
> really does form (free water, an aqueous phase from a glycol or MEG stream, a
> liquid CO2 phase) will silently produce a two-phase answer. Only disable it
> where the absence of a third phase is known from the process, not assumed.

Python:

```python
compression_train.setMultiPhaseCheck(False)
separation_train.setMultiPhaseCheck(True)
```

See [ProcessModel](process_model.md#multiphase-three-phase-flash-control) for the
per-area version on multi-area plants.

### Physical-Property Initialization Level

Every `Stream.run()` ends with `initProperties()`, which evaluates mass density,
viscosity, thermal conductivity and diffusivity. The transport-property
correlations dominate that cost, and a flowsheet that only needs mass and energy
balances never reads them.

`setPropertyInitLevel(Stream.PropertyInitLevel)` selects how much of that work is
done, for the whole area in one call:

```java
// Mass balances only: skip viscosity, thermal conductivity and diffusivity
int streamsUpdated = compressionTrain.setPropertyInitLevel(Stream.PropertyInitLevel.DENSITY_ONLY);

// ...and back to the full set before a flow-assurance or heat-exchanger study
compressionTrain.setPropertyInitLevel(Stream.PropertyInitLevel.FULL);

// Query the current setting (null = never configured, streams stay on FULL)
Stream.PropertyInitLevel level = compressionTrain.getPropertyInitLevel();
```

The API deliberately mirrors `setMultiPhaseCheck`:

- Applied **immediately** to every `Stream` held by the unit operations and by
  their inlet and outlet streams, propagated into nested `ModuleInterface`
  sub-processes, and applied to any unit added afterwards. The return value is
  the number of distinct streams updated.
- **Re-applied at the start of every run**, through the same seven entry points
  listed above.
- The default is **unset** (`getPropertyInitLevel()` returns `null`), which
  leaves each stream on `PropertyInitLevel.FULL` — the historical behaviour.
- A single stream can still be overridden with
  `Stream.setPropertyInitLevel(level)`.

> **Warning:** `DENSITY_ONLY` does **not** throw when a transport property is
> requested afterwards — `getViscosity()`, `getThermalConductivity()` and the
> diffusion coefficients return `0.0`. That silently corrupts pipeline pressure
> drop, heat-exchanger UA, mechanical design and every flow-assurance
> calculation. Switch back to `FULL` (or call `getFluid().initProperties()` on
> the stream) before reading transport properties.

Python:

```python
PropertyInitLevel = jneqsim.process.equipment.stream.Stream.PropertyInitLevel
compression_train.setPropertyInitLevel(PropertyInitLevel.DENSITY_ONLY)
```

See [ProcessModel](process_model.md#multiphase-three-phase-flash-control) for the
per-area version on multi-area plants.

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

`ProcessSystem` supports compressed full-object `.neqsim` archives and selective lifecycle JSON
state. These formats are not interchangeable.

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.processmodel.lifecycle.ProcessSystemState;

// Full object graph. Check the boolean result.
if (!process.saveToNeqsim("my_process.neqsim")) {
    throw new IllegalStateException("Could not save process");
}

// The convenience loader runs a restored process and returns null on failure.
ProcessSystem loaded = ProcessSystem.loadFromNeqsim("my_process.neqsim");
if (loaded == null) {
    throw new IllegalStateException("Could not load process");
}

// Selective JSON state for review, versioning, or a matching model definition.
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setVersion("1.0.0");
state.saveToFile("my_process_v1.0.0.json");
```

`ProcessSystem.saveAuto()` writes `.neqsim` archives, `.json` lifecycle state, or legacy binary
serialization for another extension. `ProcessSystem.loadAuto()` does not load lifecycle JSON: it
loads `.neqsim` through `loadFromNeqsim()` and treats every other extension as legacy binary. Load
JSON with `ProcessSystemState.loadFromFile()`, validate it, and apply it to a compatible pre-built
process.

Full-object XStream archives are a trusted-input format. A failed save can leave a partial file, so
save to a temporary path, check the return value, reopen and run the temporary archive, and only
then replace the last verified checkpoint. The embedded-host portability regression includes a
recycle-bearing process; report any new `No converter available` failure with a minimal equipment
graph instead of masking it with JVM `--add-opens` flags.

For complete format selection, Python behavior, compatibility limits, and recovery steps, see the
[Process Serialization Guide](../../simulation/process_serialization).

---


## Related Documentation

- [ProcessModel](process_model) - Multi-process container
- [ProcessModule](process_module) - Modular process units
- [Process Serialization](../../simulation/process_serialization) - Save/load processes
- [Graph Simulation](graph_simulation) - Graph-based execution
- [Equipment Overview](../equipment/) - Process equipment
- [Controllers](../controllers) - PID control and adjusters
- [Dynamic Simulation Guide](../../simulation/dynamic_simulation_guide) - Transient simulation
- [Extending Process Equipment](../../development/extending_process_equipment) - Custom equipment
