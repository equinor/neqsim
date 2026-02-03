---
title: Dynamic Simulation Guide
description: Comprehensive guide to transient and dynamic simulation in NeqSim.
---

# Dynamic Simulation Guide

Comprehensive guide to transient and dynamic simulation in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Steady-State vs Transient Mode](#steady-state-vs-transient-mode)
- [Basic Transient Setup](#basic-transient-setup)
- [Time Stepping](#time-stepping)
- [Equipment with Transient Support](#equipment-with-transient-support)
- [Control Systems](#control-systems)
- [Pipeline Dynamics](#pipeline-dynamics)
- [Vessel Depressurization](#vessel-depressurization)
- [Calculation Identifiers](#calculation-identifiers)
- [Best Practices](#best-practices)
- [Examples](#examples)
- [Related Documentation](#related-documentation)

---

## Overview

NeqSim supports both **steady-state** and **transient (dynamic)** simulation modes. Dynamic simulation enables modeling of:

- **Process Dynamics**: Startup, shutdown, upset conditions
- **Control Systems**: PID controllers responding to disturbances
- **Flow Transients**: Slug flow, pressure waves, liquid accumulation
- **Depressurization**: Blowdown, emergency relief scenarios
- **Reservoir Depletion**: Time-dependent production

### When to Use Dynamic Simulation

| Scenario | Mode |
|----------|------|
| Design calculations | Steady-state |
| Equipment sizing | Steady-state |
| Controller tuning | Transient |
| Startup/shutdown analysis | Transient |
| Safety studies (blowdown) | Transient |
| Slug catcher sizing | Transient |
| Training simulators | Transient |

---

## Steady-State vs Transient Mode

### Steady-State Mode (Default)

In steady-state mode, each `run()` call calculates the equilibrium solution assuming constant boundary conditions:

```java
ProcessSystem process = new ProcessSystem();
process.add(stream);
process.add(separator);
process.add(compressor);

// Steady-state - each run() finds equilibrium
process.run();
```

### Transient Mode

In transient mode, equipment remembers state between time steps. Enable by setting `setCalculateSteadyState(false)`:

```java
// Enable transient mode on equipment
separator.setCalculateSteadyState(false);
pipeline.setCalculateSteadyState(false);

// Time step through simulation
UUID id = UUID.randomUUID();
for (int step = 0; step < 100; step++) {
    process.runTransient(1.0, id);  // 1 second time step
}
```

### Key Differences

| Aspect | Steady-State | Transient |
|--------|--------------|-----------|
| State memory | None | Equipment retains state |
| Method | `run()` | `runTransient(dt, id)` |
| Holdup | Ignored | Tracked (level, pressure) |
| Controllers | Converged | Time-stepping response |
| Initialization | Automatic | Requires steady-state first |

---

## Basic Transient Setup

### Step-by-Step Setup

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;
import java.util.UUID;

// 1. Create thermodynamic system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

// 2. Build process flowsheet
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");

Separator separator = new Separator("V-100", feed);
separator.setInternalDiameter(2.0);
separator.setSeparatorLength(5.0);

ThrottlingValve gasValve = new ThrottlingValve("Gas Valve", separator.getGasOutStream());
gasValve.setOutletPressure(20.0, "bara");

ThrottlingValve liquidValve = new ThrottlingValve("Liq Valve", separator.getLiquidOutStream());
liquidValve.setOutletPressure(20.0, "bara");

// 3. Create process system
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(gasValve);
process.add(liquidValve);

// 4. Initialize with steady-state
process.run();
System.out.println("Initial level: " + separator.getLiquidLevel());

// 5. Switch to transient mode
separator.setCalculateSteadyState(false);

// 6. Run transient simulation
UUID calcId = UUID.randomUUID();
double dt = 1.0;  // 1 second time step

for (int step = 0; step < 60; step++) {
    process.runTransient(dt, calcId);
    
    if (step % 10 == 0) {
        System.out.printf("t=%d s, Level=%.3f m, P=%.2f bara%n",
            step, separator.getLiquidLevel(), separator.getPressure());
    }
}
```

---

## Time Stepping

### Choosing Time Step Size

The time step should be small enough to capture dynamics but large enough for efficiency:

| Application | Typical Time Step |
|-------------|-------------------|
| Fast control loops | 0.1 - 1.0 s |
| Separator level control | 1.0 - 10.0 s |
| Pipeline transients | 0.5 - 5.0 s |
| Reservoir depletion | 3600 s (1 hour) to 86400 s (1 day) |
| Blowdown studies | 0.1 - 1.0 s |

### CFL Condition for Pipelines

For pipeline transients, the time step should satisfy the CFL (Courant-Friedrichs-Lewy) condition:

$$\Delta t \leq \frac{\Delta x}{v + c}$$

Where:
- Δx = segment length
- v = flow velocity
- c = speed of sound

### Variable Time Stepping

```java
double t = 0;
double tEnd = 3600;  // 1 hour
double dt = 1.0;

while (t < tEnd) {
    process.runTransient(dt, calcId);
    
    // Adjust time step based on rate of change
    double rateOfChange = Math.abs(separator.getLiquidLevel() - previousLevel) / dt;
    if (rateOfChange > 0.1) {
        dt = Math.max(0.1, dt / 2);  // Decrease time step
    } else if (rateOfChange < 0.01) {
        dt = Math.min(10.0, dt * 1.5);  // Increase time step
    }
    
    t += dt;
    previousLevel = separator.getLiquidLevel();
}
```

---

## Equipment with Transient Support

### Separators

```java
Separator separator = new Separator("V-100", feed);
separator.setInternalDiameter(2.0);
separator.setSeparatorLength(5.0);
separator.setLiquidLevel(0.5);  // Initial level (m)
separator.setCalculateSteadyState(false);

// During transient, level changes based on in/out flow imbalance
separator.runTransient(dt, id);
double newLevel = separator.getLiquidLevel();
```

### Pipelines (PipeBeggsAndBrills)

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipeline", feed);
pipe.setLength(10000);  // 10 km
pipe.setDiameter(0.3);  // 0.3 m
pipe.setNumberOfIncrements(50);
pipe.setCalculateSteadyState(false);

// Transient tracks pressure waves and liquid holdup
pipe.runTransient(dt, id);
```

### Tanks

```java
Tank tank = new Tank("T-100", feed);
tank.setVolume(100.0);  // m³
tank.setCalculateSteadyState(false);

// Tank pressure/composition evolves based on in/out flows
tank.runTransient(dt, id);
```

### Compressors

```java
Compressor comp = new Compressor("K-100", feed);
comp.setOutletPressure(100.0, "bara");

// Enable dynamic features (state machine, events)
comp.setDynamicSimulationEnabled(true);
comp.setRotorInertia(100.0);  // kg·m²

// Compressor responds to speed changes, surge, etc.
comp.runTransient(dt, id);
```

### Reservoirs

```java
SimpleReservoir reservoir = new SimpleReservoir("Field");
reservoir.setReservoirFluid(fluid);
reservoir.setGasVolume(1e9, "Sm3");

StreamInterface producer = reservoir.addGasProducer("GP-1");
producer.setFlowRate(5.0, "MSm3/day");

// Run for one day
reservoir.runTransient(86400, id);
double remainingGas = reservoir.getGasInPlace("Sm3");
```

---

## Control Systems

### Adding Controllers for Dynamic Simulation

Controllers require transmitters to measure process variables:

```java
// Level transmitter
LevelTransmitter levelTransmitter = new LevelTransmitter("LT-100", separator);
levelTransmitter.setMinimumValue(0.0);
levelTransmitter.setMaximumValue(2.0);

// Level controller
ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LC-100");
levelController.setTransmitter(levelTransmitter);
levelController.setControllerSetPoint(0.5);  // 0.5 m target level
levelController.setControllerParameters(0.5, 100.0, 0.0);  // Kp, Ti, Td
levelController.setReverseActing(true);  // Open valve to lower level

// Connect controller to valve
liquidValve.setController(levelController);
```

### Pressure Controller

```java
// Pressure transmitter
PressureTransmitter pressureTransmitter = new PressureTransmitter("PT-100", separator);
pressureTransmitter.setMinimumValue(0.0);
pressureTransmitter.setMaximumValue(100.0);

// Pressure controller
ControllerDeviceBaseClass pressureController = new ControllerDeviceBaseClass("PC-100");
pressureController.setTransmitter(pressureTransmitter);
pressureController.setControllerSetPoint(50.0);  // 50 bara target
pressureController.setControllerParameters(1.0, 50.0, 0.0);

gasValve.setController(pressureController);
```

### Flow Controller

```java
// Volume flow transmitter
VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter("FT-100", feed);
flowTransmitter.setMeasuredPhase("gas");
flowTransmitter.setMinimumValue(0.0);
flowTransmitter.setMaximumValue(10000.0);

ControllerDeviceBaseClass flowController = new ControllerDeviceBaseClass("FC-100");
flowController.setTransmitter(flowTransmitter);
flowController.setControllerSetPoint(5000.0);  // Target flow
flowController.setControllerParameters(0.1, 200.0, 0.0);

inletValve.setController(flowController);
```

### Controller Tuning

```java
// PID parameters
double Kp = 1.0;    // Proportional gain
double Ti = 100.0;  // Integral time (seconds)
double Td = 0.0;    // Derivative time (seconds)

controller.setControllerParameters(Kp, Ti, Td);

// Action direction
controller.setReverseActing(true);   // Increase output to decrease PV
controller.setReverseActing(false);  // Increase output to increase PV
```

---

## Pipeline Dynamics

### Transient Multiphase Flow

The `TransientPipe` class provides drift-flux based transient simulation:

```java
import neqsim.fluidmechanics.flowsystem.twophaseflowsystem.twophasepipeflowsystem.TwoPhasePipeFlowSystem;

// Create transient pipeline
TransientPipe pipeline = new TransientPipe("FL-100", feed);
pipeline.setLength(5000.0);  // 5 km
pipeline.setDiameter(0.25);
pipeline.setNumberOfNodes(50);
pipeline.setElevationProfile(elevations);  // Terrain effects

// Configure flow regime detection
pipeline.setFlowRegimeDetectionMethod("mechanistic");

// Run transient
pipeline.setCalculateSteadyState(false);
for (int step = 0; step < 600; step++) {
    pipeline.runTransient(1.0, id);
    
    // Track slugs
    SlugTracker slugs = pipeline.getSlugTracker();
    System.out.println("Active slugs: " + slugs.getSlugCount());
}
```

### Slug Tracking

```java
// Get slug statistics
SlugTracker tracker = pipeline.getSlugTracker();

int slugCount = tracker.getSlugCount();
double avgSlugLength = tracker.getAverageSlugLength();
double maxSlugVolume = tracker.getMaxSlugVolume();
double slugFrequency = tracker.getSlugFrequency();

System.out.printf("Slugs: %d, Avg length: %.1f m, Frequency: %.2f /min%n",
    slugCount, avgSlugLength, slugFrequency * 60);
```

---

## Vessel Depressurization

### Blowdown Simulation

```java
// Create vessel with initial conditions
SystemInterface vesselFluid = new SystemSrkEos(350.0, 100.0);
vesselFluid.addComponent("methane", 0.8);
vesselFluid.addComponent("ethane", 0.15);
vesselFluid.addComponent("propane", 0.05);
vesselFluid.setMixingRule("classic");

Tank vessel = new Tank("V-100", vesselFluid);
vessel.setVolume(50.0);  // 50 m³

// Blowdown valve to atmosphere
ThrottlingValve blowdownValve = new ThrottlingValve("BDV", vessel.getOutletStream());
blowdownValve.setOutletPressure(1.5, "bara");
blowdownValve.setCv(50.0);

ProcessSystem blowdown = new ProcessSystem();
blowdown.add(vessel);
blowdown.add(blowdownValve);

// Initialize
blowdown.run();
vessel.setCalculateSteadyState(false);

// Run blowdown for 15 minutes
UUID id = UUID.randomUUID();
double dt = 0.5;  // 0.5 second steps

ArrayList<double[]> results = new ArrayList<>();
for (double t = 0; t <= 900; t += dt) {
    blowdown.runTransient(dt, id);
    
    results.add(new double[] {
        t,
        vessel.getPressure(),
        vessel.getTemperature() - 273.15,  // °C
        blowdownValve.getFlowRate("kg/hr")
    });
}

// Report minimum temperature (for MDMT assessment)
double minTemp = results.stream()
    .mapToDouble(r -> r[2])
    .min()
    .orElse(Double.NaN);

System.out.println("Minimum temperature: " + minTemp + " °C");
```

### Fire Case Depressurization

```java
// Add heat input for fire case
vessel.setHeatInput(1000000.0);  // 1 MW fire load

for (double t = 0; t <= 900; t += dt) {
    blowdown.runTransient(dt, id);
    
    // Check for two-phase relief (wetted surface)
    double liquidFraction = vessel.getLiquidVolumeFraction();
    if (liquidFraction > 0) {
        System.out.println("Two-phase relief at t=" + t);
    }
}
```

---

## Calculation Identifiers

### Why IDs Matter

The calculation identifier (UUID) ensures all equipment in a process system is synchronized during transient runs:

```java
UUID calcId = UUID.randomUUID();

// All equipment should have same ID after runTransient
process.runTransient(dt, calcId);

// Verify synchronization
for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
    if (!eq.getCalculationIdentifier().equals(calcId)) {
        System.err.println("Equipment out of sync: " + eq.getName());
    }
}
```

### Detecting Stale States

```java
// After transient step, check all equipment updated
UUID expectedId = process.getCalculationIdentifier();

boolean allSynced = true;
for (ProcessEquipmentInterface eq : process.getUnitOperations()) {
    if (!eq.getCalculationIdentifier().equals(expectedId)) {
        allSynced = false;
        System.err.println("Stale: " + eq.getName());
    }
}

if (!allSynced) {
    // Reinitialize process
    process.run();
}
```

---

## Best Practices

### 1. Always Initialize with Steady-State

```java
// CORRECT: Initialize first
process.run();  // Steady-state initialization
separator.setCalculateSteadyState(false);
process.runTransient(dt, id);

// WRONG: Skip initialization
separator.setCalculateSteadyState(false);
process.runTransient(dt, id);  // May fail or give wrong results
```

### 2. Set Transient Mode on Dynamic Equipment Only

```java
// Equipment that needs transient mode:
separator.setCalculateSteadyState(false);  // Has holdup
tank.setCalculateSteadyState(false);       // Has volume
pipeline.setCalculateSteadyState(false);   // Has segments

// Equipment that typically stays steady-state:
// - Streams (boundary conditions)
// - Heat exchangers (fast thermal equilibrium)
// - Compressors (unless modeling inertia)
```

### 3. Match Controller Time Constants to Time Step

```java
double dt = 1.0;  // 1 second time step

// Controller integral time should be >> dt
double Ti = 100.0;  // 100 seconds >> 1 second

// Avoid Ti close to dt (causes oscillation)
// Ti = 1.0 would be problematic with dt = 1.0
```

### 4. Log Key Variables for Debugging

```java
try (PrintWriter log = new PrintWriter("transient.csv")) {
    log.println("time,pressure,temperature,level,flow");
    
    for (double t = 0; t < tEnd; t += dt) {
        process.runTransient(dt, id);
        
        log.printf("%.1f,%.2f,%.2f,%.3f,%.1f%n",
            t,
            separator.getPressure(),
            separator.getTemperature("C"),
            separator.getLiquidLevel(),
            gasValve.getFlowRate("kg/hr"));
    }
}
```

### 5. Use try-finally for Clean Shutdown

```java
try {
    process.run();
    separator.setCalculateSteadyState(false);
    
    for (int step = 0; step < 100; step++) {
        process.runTransient(dt, id);
    }
} finally {
    // Reset to steady-state for future runs
    separator.setCalculateSteadyState(true);
}
```

---

## Examples

### Example 1: Feed Rate Step Change

```java
// Initialize at 1000 kg/hr
feed.setFlowRate(1000.0, "kg/hr");
process.run();
separator.setCalculateSteadyState(false);

// Step change to 1500 kg/hr
feed.setFlowRate(1500.0, "kg/hr");

// Observe response
for (int step = 0; step < 300; step++) {
    process.runTransient(1.0, id);
    System.out.printf("t=%d s, Level=%.3f m%n", step, separator.getLiquidLevel());
}
```

### Example 2: Slug Arrival at Separator

See [transient_slug_separator_control_example.md](../examples/transient_slug_separator_control_example.md)

### Example 3: Compressor Startup

```java
Compressor comp = new Compressor("K-100", feed);
comp.setDynamicSimulationEnabled(true);
comp.setRotorInertia(150.0);

// Start from standby
comp.setState(CompressorState.STANDBY);
process.run();

// Initiate startup
comp.startStartupSequence();

for (int step = 0; step < 600; step++) {
    process.runTransient(0.5, id);
    
    System.out.printf("t=%.1f s, Speed=%.0f RPM, State=%s%n",
        step * 0.5,
        comp.getSpeed(),
        comp.getState());
        
    if (comp.getState() == CompressorState.RUNNING) {
        System.out.println("Startup complete at t=" + step * 0.5 + " s");
        break;
    }
}
```

---

## Related Documentation

### Core Transient Documentation
- [Process Transient Simulation Guide](../wiki/process_transient_simulation_guide.md) - Control loop patterns
- [Pipeline Transient Simulation](../wiki/pipeline_transient_simulation.md) - PipeBeggsAndBrills details
- [Transient Multiphase Pipe](../wiki/transient_multiphase_pipe.md) - Drift-flux model

### Equipment-Specific
- [Compressor Dynamic Features](../process/equipment/compressor_curves.md#dynamic-simulation-features) - State machines, events
- [Controllers](../process/controllers.md) - PID control for dynamics
- [Separators](../process/equipment/separators.md) - Level tracking

### Interactive Notebooks (Google Colab)

These Colab notebooks provide hands-on dynamic simulation examples:

| Notebook | Description |
|----------|-------------|
| [Dynamic Simulation Basics](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/process/dynamicsimul.ipynb) | Introduction to transient simulation |
| [Dynamic Compressor](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/process/dynamiccompressor.ipynb) | Compressor dynamics and control |
| [Single Component Dynamics](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/process/singlecomponent.ipynb) | Single component transient behavior |
| [Dynamic Separator](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/process/dynsep.ipynb) | Separator level dynamics and control |

### Local Examples
- [Transient Slug Separator Control](../examples/transient_slug_separator_control_example.md)
- [ESP Pump Tutorial](../examples/ESP_Pump_Tutorial.ipynb)

### Process Logic
- [Process Logic Framework](process_logic_framework.md) - runTransient integration
- [Integrated Workflow Guide](INTEGRATED_WORKFLOW_GUIDE.md) - Transient scenario analysis
