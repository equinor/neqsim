---
title: "Dynamic Simulation with DynamicProcessHelper"
description: "Guide to converting steady-state NeqSim process simulations into dynamic simulations with auto-generated transmitters, PID controllers, and transient execution. Covers DynamicProcessHelper, PID tuning, flow and temperature control loops."
---

# Dynamic Simulation with DynamicProcessHelper

## Overview

NeqSim supports both steady-state and dynamic (transient) process simulation. The
`DynamicProcessHelper` utility bridges these two modes: given a sized steady-state
`ProcessSystem`, it automatically creates transmitters, wires PID controllers to
downstream valves, and switches all equipment to dynamic mode — ready to run transient
steps.

**Location:** `neqsim.process.util.DynamicProcessHelper`

## Quick Start

```java
import neqsim.process.util.DynamicProcessHelper;

// 1. Build and run the steady-state process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(inletValve);
process.add(separator);
process.add(gasValve);
process.add(liquidValve);
process.run();

// 2. Auto-instrument in one call
DynamicProcessHelper helper = new DynamicProcessHelper(process);
helper.setDefaultTimeStep(1.0); // seconds
helper.instrumentAndControl();

// 3. Run transient loop
for (int i = 0; i < 600; i++) {
    process.runTransient();
}
```

## What `instrumentAndControl()` Creates

The method scans all equipment in the process and adds instrumentation based on
equipment type:

| Equipment | Transmitters Created | Controllers Created |
|-----------|---------------------|---------------------|
| `Separator` | PT (gas outlet), LT (vessel), TT (gas outlet) | PC on downstream gas valve, LC on downstream liquid valve |
| `ThreePhaseSeparator` | Same as Separator + water level | PC, LC, WLC (water level controller on water valve) |
| `Compressor` | PT (discharge), TT (discharge) | — |
| `Heater` / `Cooler` | TT (outlet) | — |

### Separator-Valve Pairing

Controllers are automatically wired to downstream valves by matching stream identity.
For example, if a `ThrottlingValve` has its inlet stream set to `separator.getGasOutStream()`,
the pressure controller is assigned to that valve.

## PID Tuning Defaults

Each controller type has sensible default tuning parameters:

| Controller Type | Kp (default) | Ti (default, s) | Action |
|----------------|-------------|----------------|--------|
| Pressure (PC) | 0.5 | 50 | Reverse |
| Level (LC) | 2.0 | 200 | Direct |
| Flow (FC) | 0.2 | 100 | Reverse |
| Temperature (TC) | 1.0 | 120 | Direct |

### Customizing Tuning

```java
DynamicProcessHelper helper = new DynamicProcessHelper(process);
helper.setPressureTuning(0.8, 30.0);     // Kp, Ti
helper.setLevelTuning(3.0, 150.0);
helper.setFlowTuning(0.3, 80.0);
helper.setTemperatureTuning(1.5, 100.0);
helper.instrumentAndControl();
```

## Adding Extra Control Loops

### Flow Controller

```java
// Add flow control on any valve
ControllerDeviceInterface fc = helper.addFlowController(
    "FIC-101",          // ISA tag
    exportValve,        // valve to control
    exportStream,       // stream to measure
    50000.0,            // setpoint
    "kg/hr"             // unit
);
```

### Temperature Controller

```java
// Add temperature control on a heater or cooler
ControllerDeviceInterface tc = helper.addTemperatureController(
    "TIC-201",          // ISA tag
    cooler,             // heater or cooler equipment
    cooler.getOutletStream(),
    35.0                // setpoint in Celsius
);
```

## Accessing Generated Instruments

```java
// All transmitters as a map
Map<String, MeasurementDeviceInterface> transmitters = helper.getTransmitters();

// All controllers as a map
Map<String, ControllerDeviceInterface> controllers = helper.getControllers();

// Specific item by tag
MeasurementDeviceInterface pt = helper.getTransmitter("PT-HP sep");
ControllerDeviceInterface pc = helper.getController("PC-HP sep");
```

Tag naming follows ISA conventions: `PT-<equipment>`, `LT-<equipment>`,
`TT-<equipment>`, `PC-<equipment>`, `LC-<equipment>`, `WLC-<equipment>`.

## Complete Example

```java
// --- Steady-state setup ---
SystemInterface gas = new SystemSrkEos(273.15 + 30.0, 65.0);
gas.addComponent("methane", 0.80);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.addComponent("nC5", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(10000.0, "kg/hr");

ThrottlingValve inletValve = new ThrottlingValve("IV", feed);
inletValve.setOutletPressure(55.0);

Separator separator = new Separator("HP sep", inletValve.getOutletStream());
separator.setInternalDiameter(1.5);
separator.setLiquidLevel(0.5);

ThrottlingValve gasValve = new ThrottlingValve("GV", separator.getGasOutStream());
gasValve.setOutletPressure(40.0);

ThrottlingValve liqValve = new ThrottlingValve("LV", separator.getLiquidOutStream());
liqValve.setOutletPressure(10.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(inletValve);
process.add(separator);
process.add(gasValve);
process.add(liqValve);
process.run();

// --- Convert to dynamic ---
DynamicProcessHelper helper = new DynamicProcessHelper(process);
helper.setDefaultTimeStep(0.5);
helper.instrumentAndControl();

// --- Run 5 minutes of transient simulation ---
for (int i = 0; i < 600; i++) {
    process.runTransient();
}

// Read results
double pressure = separator.getGasOutStream().getPressure("bara");
double level = separator.getLiquidLevel();
```

## How It Works Internally

1. **Equipment scan** — iterates `process.getUnitOperations()` to find all separators,
   compressors, heaters, coolers, and valves.
2. **Stream identity matching** — for each separator, finds downstream valves by
   comparing `valve.getInletStream()` with `separator.getGasOutStream()` and
   `separator.getLiquidOutStream()` (object identity, not name matching).
3. **Transmitter creation** — creates `PressureTransmitter`, `LevelTransmitter`,
   `TemperatureTransmitter`, or `VolumeFlowTransmitter` and calls `process.add(device)`
   (which uses the `MeasurementDeviceInterface` overload).
4. **Controller wiring** — creates `ControllerDeviceBaseClass` with the transmitter
   as input, sets tuning parameters, and calls `valve.setController(controller)`.
5. **Dynamic mode switch** — calls `unit.setCalculateSteadyState(false)` on all
   equipment and sets `process.setTimeStep(dt)`.

## Related Documentation

- [Process Controllers and Logic](controllers.md) — Adjusters, recycles, PID controllers
- [Instrument Design Framework](instrument-design.md) — ISA instrument specifications and cost estimation
- [Separators](equipment/separators.md) — Separator configuration and sizing
- [Valves](equipment/valves.md) — Valve types and Cv calculation

## DEXPI Data Exchange for Instruments

The `DynamicProcessHelper` supports exporting and importing instrument data using the
[DEXPI](https://dexpi.org/) P&ID data exchange standard (ISO 15926-based XML).

### Exporting Instruments to DEXPI XML

After calling `instrumentAndControl()`, export all instruments to a DEXPI file:

```java
DynamicProcessHelper helper = new DynamicProcessHelper(process);
helper.instrumentAndControl();

// Export process + instruments to DEXPI XML
helper.exportDexpi(new File("process_pid.xml"));
```

The exported file contains:
- **ProcessInstrumentationFunction** elements for each transmitter (with ISA category, functions, and number)
- **ProcessSignalGeneratingFunction** elements for sensor elements
- **ActuatingFunction** elements for controller outputs
- **SignalConveyingFunction** elements linking instruments to actuators
- **InstrumentationLoopFunction** elements grouping each control loop

### Importing Instrument Metadata from DEXPI XML

Read instrument metadata from an existing DEXPI P&ID file:

```java
List<DexpiInstrumentInfo> instruments = helper.readDexpiInstruments(
    new File("existing_pid.xml"));

for (DexpiInstrumentInfo info : instruments) {
    System.out.println(info.getTagName()
        + " category=" + info.getCategory()
        + " functions=" + info.getFunctions()
        + " hasControl=" + info.hasControlFunction());
}
```

Note: Imported instruments are returned as `DexpiInstrumentInfo` metadata records rather
than live transmitter/controller objects, because creating live instruments requires
connected process streams. Use the info to identify which instruments exist in the P&ID
and wire them up to the appropriate equipment.

### Using the Writer/Reader Directly

For more control, use `DexpiXmlWriter` and `DexpiXmlReader` directly:

```java
// Export with explicit transmitter/controller maps
DexpiXmlWriter.write(process, file,
    helper.getTransmitters(), helper.getControllers());

// Import instrument metadata only
List<DexpiInstrumentInfo> instruments =
    DexpiXmlReader.readInstruments(new File("pid.xml"));
```
