# Introduction to Process Simulation in NeqSim

NeqSim provides a powerful framework for modeling chemical and petroleum process plants. By connecting unit operations (separators, compressors, heat exchangers, valves) with streams, you can build complete process flowsheets for steady-state and dynamic simulation.

## Table of Contents
- [Why NeqSim for Process Simulation?](#why-neqsim-for-process-simulation)
- [Core Architecture](#core-architecture)
- [Quick Start Example](#quick-start-example)
- [Step-by-Step Guide](#step-by-step-guide)
- [Process Modules](#process-modules)
- [Advanced Features](#advanced-features)
- [Next Steps](#next-steps)

---

## Why NeqSim for Process Simulation?

NeqSim combines rigorous thermodynamic calculations with flexible process modeling:

| Feature | Description |
|---------|-------------|
| **Rigorous Thermodynamics** | Equations of state (SRK, PR, CPA, GERG-2008) with accurate phase equilibria |
| **Comprehensive Equipment** | 50+ unit operation types including specialized oil & gas equipment |
| **Dynamic Simulation** | Time-stepping for transient analysis, blowdown, and startup/shutdown |
| **Control Integration** | PID controllers, adjusters, and recycle solvers built-in |
| **Safety Systems** | PSV, ESD, HIPPS modeling for hazard analysis |
| **Extensibility** | Java API allows custom equipment and integration with external systems |

---

## Core Architecture

### Key Classes

| Class | Purpose |
|-------|---------|
| `ProcessSystem` | Container for all equipment; manages execution order and convergence |
| `Stream` | Fluid flow with thermodynamic state (T, P, composition, flow rate) |
| `ProcessEquipmentInterface` | Base interface for all unit operations |
| `Recycle` | Handles iterative convergence for recycle loops |
| `Adjuster` | Adjusts variables to meet specifications |
| `Calculator` | Custom calculations with lambda expressions |

### Equipment Hierarchy

```
ProcessEquipmentBaseClass
├── TwoPortEquipment (single inlet/outlet)
│   ├── ThrottlingValve
│   ├── Compressor
│   ├── Pump
│   ├── Heater / Cooler
│   └── AdiabaticPipe
├── Separator (multiple outlets)
│   ├── ThreePhaseSeparator
│   └── GasScrubber
├── Mixer / Splitter
├── DistillationColumn
└── Specialized Equipment
    ├── Ejector
    ├── MembraneSeparator
    └── Electrolyzer
```

### Execution Flow

```
1. Define fluid (thermodynamic system)
2. Create feed stream(s)
3. Instantiate equipment and connect streams
4. Add all units to ProcessSystem
5. Run simulation (process.run())
6. Retrieve results from streams/equipment
```

---

## Quick Start Example

A minimal working example - gas separation at reduced pressure:

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// 1. Define fluid
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-heptane", 0.02);
fluid.setMixingRule("classic");

// 2. Create feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");
feed.setTemperature(30.0, "C");
feed.setPressure(50.0, "bara");

// 3. Add equipment
ThrottlingValve valve = new ThrottlingValve("JT Valve", feed);
valve.setOutletPressure(10.0, "bara");

Separator separator = new Separator("HP Separator", valve.getOutletStream());

// 4. Build process system
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(separator);

// 5. Run simulation
process.run();

// 6. Get results
System.out.println("Gas rate: " + separator.getGasOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Liquid rate: " + separator.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
System.out.println("Separator temp: " + separator.getTemperature("C") + " °C");
```

---

## Step-by-Step Guide

### 1. Define the Fluid

Create a thermodynamic system using an equation of state:

```java
// SRK equation of state
SystemInterface fluid = new SystemSrkEos(298.15, 50.0); // T(K), P(bar)
fluid.addComponent("methane", 90.0);  // mole fraction or moles
fluid.addComponent("ethane", 5.0);
fluid.addComponent("propane", 3.0);
fluid.addComponent("n-heptane", 2.0);
fluid.setMixingRule("classic");

// Or use CPA for polar components (water, MEG, methanol)
SystemInterface cpaFluid = new SystemSrkCPAstatoil(298.15, 50.0);
cpaFluid.addComponent("methane", 0.9);
cpaFluid.addComponent("water", 0.05);
cpaFluid.addComponent("MEG", 0.05);
cpaFluid.setMixingRule(10);  // CPA mixing rule
```

### 2. Create the Feed Stream

```java
Stream feedStream = new Stream("Feed Stream", fluid);
feedStream.setFlowRate(1000.0, "kg/hr");    // or "kmol/hr", "MSm3/day"
feedStream.setTemperature(30.0, "C");        // or "K"
feedStream.setPressure(50.0, "bara");        // or "barg", "Pa"
```

### 3. Connect Equipment

Most equipment takes an inlet stream in the constructor:

```java
// Valve - reduces pressure
ThrottlingValve valve = new ThrottlingValve("Inlet Valve", feedStream);
valve.setOutletPressure(30.0, "bara");

// Separator - splits phases
Separator separator = new Separator("Test Separator", valve.getOutletStream());

// Compressor - increases pressure
Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(100.0, "bara");
compressor.setIsentropicEfficiency(0.75);

// Heater - adds heat
Heater heater = new Heater("Gas Heater", compressor.getOutletStream());
heater.setOutTemperature(50.0, "C");
```

### 4. Build the Process System

```java
ProcessSystem process = new ProcessSystem();
process.add(feedStream);
process.add(valve);
process.add(separator);
process.add(compressor);
process.add(heater);
```

> **Note:** Equipment names must be unique within a ProcessSystem.

### 5. Run the Simulation

```java
// Steady-state run
process.run();

// For debugging, run with UUID tracking
UUID id = UUID.randomUUID();
process.run(id);
```

### 6. Retrieve Results

```java
// Stream properties
double gasRate = separator.getGasOutStream().getFlowRate("kg/hr");
double gasTemp = separator.getGasOutStream().getTemperature("C");
double gasPressure = separator.getGasOutStream().getPressure("bara");

// Equipment performance
double compressorPower = compressor.getPower("kW");
double compressorHead = compressor.getPolytropicHead("kJ/kg");

// Composition
double methaneInGas = separator.getGasOutStream()
    .getFluid().getComponent("methane").getMoleFraction();
```

---

## Process Modules

Process modules are pre-configured collections of unit operations designed to perform standard processing tasks. They encapsulate complex logic for reuse.

### Built-in Modules

| Module | Purpose |
|--------|---------|
| `GlycolDehydrationlModule` | TEG/MEG dehydration systems |
| `SeparationTrainModule` | Multi-stage separation |
| `CompressionModule` | Multi-stage compression with intercooling |

### Using a Standard Module

```java
import neqsim.process.processmodel.processmodules.GlycolDehydrationlModule;

// Initialize the module
GlycolDehydrationlModule tegModule = new GlycolDehydrationlModule("TEG Plant");
tegModule.addInputStream("GasFeed", separator.getGasOutStream());
tegModule.addInputStream("TEGFeed", tegFeedStream);

// Configure module parameters
tegModule.setSpecification("water content", 50.0); // ppm target

// Add to process system
process.add(tegModule);
process.run();
```

### Creating Custom Modules

Extend `ProcessModuleBaseClass` to create reusable process blocks:

```java
public class MyCustomModule extends ProcessModuleBaseClass {
    public MyCustomModule(String name) {
        super(name);
    }

    @Override
    public void initializeModule() {
        // Define internal units and connections
        Separator sep = new Separator("Internal Sep", getInputStream("feed"));
        Compressor comp = new Compressor("Internal Comp", sep.getGasOutStream());
        
        getOperations().add(sep);
        getOperations().add(comp);
        
        // Set output streams
        setOutputStream("gas", comp.getOutletStream());
        setOutputStream("liquid", sep.getLiquidOutStream());
    }
}
```

---

## Advanced Features

### Recycle Loops

Handle closed loops with the `Recycle` class:

```java
Recycle recycle = new Recycle("Recycle Controller");
recycle.addStream(separator.getLiquidOutStream());
recycle.setTolerance(1e-6);
recycle.setMaximumIterations(50);
process.add(recycle);
```

See [Advanced Process Simulation](advanced_process_simulation.md) for complete examples.

### Controllers (PID)

Automate equipment operation with PID controllers:

```java
ControllerDeviceBaseClass flowController = new ControllerDeviceBaseClass();
flowController.setTransmitter(flowTransmitter);
flowController.setControllerSetPoint(50.0);
flowController.setControllerParameters(0.5, 100.0, 0.0); // Kp, Ti, Td
valve.setController(flowController);
```

### Adjusters

Adjust a variable to meet a target specification:

```java
Adjuster adj = new Adjuster("Pressure Adjuster");
adj.setAdjustedVariable(valve, "opening");
adj.setTargetVariable(separator, "pressure", 30.0, "bara");
process.add(adj);
```

### Dynamic Simulation

Run time-stepping simulations for transient analysis:

```java
process.setTimeStep(0.1);  // seconds
for (int i = 0; i < 1000; i++) {
    process.runTransient();
    // Log or record results
}
```

### Functional Interfaces

Use lambda expressions for flexible calculations:

```java
// Calculator with lambda
Calculator calc = new Calculator("Energy Balance");
calc.addInputVariable(inlet);
calc.setOutputVariable(outlet);
calc.setCalculationMethod((inputs, output) -> {
    double totalEnthalpy = inputs.stream()
        .mapToDouble(e -> ((Stream)e).getThermoSystem().getEnthalpy())
        .sum();
    // Apply to output...
});

// Adjuster with lambda
adjuster.setTargetValueCalculator(equipment -> {
    return ((Stream) equipment).getFlowRate("kg/hr") * 0.1;
});
```

See [Logical Unit Operations](logical_unit_operations.md) for complete functional interface documentation.

---

## Next Steps

| Topic | Documentation |
|-------|---------------|
| **Advanced Topics** | [Advanced Process Simulation](advanced_process_simulation.md) |
| **Control Logic** | [Logical Unit Operations](logical_unit_operations.md) |
| **Equipment Details** | [Process Equipment](../process/README.md) |
| **Dynamic Simulation** | [Process Transient Guide](process_transient_simulation_guide.md) |
| **Safety Systems** | [Integrated Safety Systems](../safety/INTEGRATED_SAFETY_SYSTEMS.md) |
| **Bottleneck Analysis** | [Bottleneck Analysis](bottleneck_analysis.md) |
| **Examples** | [Usage Examples](usage_examples.md) |

For equipment-specific documentation, see the [equipment documentation](../process/equipment/).
