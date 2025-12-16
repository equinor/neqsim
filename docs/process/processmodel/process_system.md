# ProcessSystem Class

Documentation for the ProcessSystem class in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Creating a Process](#creating-a-process)
- [Adding Equipment](#adding-equipment)
- [Running Simulations](#running-simulations)
- [Results and Reporting](#results-and-reporting)
- [Advanced Features](#advanced-features)
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

### Steady-State Simulation

```java
// Run until converged
process.run();

// Run with calculation ID for tracking
UUID calcId = UUID.randomUUID();
process.run(calcId);
```

### Transient Simulation

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

### Execution Order Control

```java
// Force specific execution order
process.setExecutionOrder(Arrays.asList(
    "Feed",
    "Heater",
    "Separator",
    "Gas Compressor",
    "Liquid Pump"
));
```

### Parallel Execution

```java
// Enable parallel execution for independent units
process.setParallelExecution(true);
process.setNumberOfThreads(4);
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

## Related Documentation

- [ProcessModule](process_module.md) - Modular process units
- [Graph Simulation](graph_simulation.md) - Graph-based execution
- [Equipment Overview](../equipment/README.md) - Process equipment
