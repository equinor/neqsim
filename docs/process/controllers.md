---
title: Process Controllers and Logic
description: Documentation for controllers, adjusters, recycles, and process logic in NeqSim.
---

# Process Controllers and Logic

Documentation for controllers, adjusters, recycles, and process logic in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Named Controller Map](#named-controller-map)
- [Adjusters](#adjusters)
- [Recycles](#recycles)
- [Setters](#setters)
- [Calculators](#calculators)
- [PID Controllers](#pid-controllers)
- [Process Logic](#process-logic)

---

## Overview

**Location:** `neqsim.process.equipment.util`, `neqsim.process.controllerdevice`, `neqsim.process.logic`

**Classes:**
- `Adjuster` - Adjust variable to meet specification
- `Recycle` - Handle recycle streams
- `Setter` - Set variable values
- `Calculator` - Custom calculations
- `PIDController` - PID control
- `ProcessLogicController` - Conditional logic

---

## Named Controller Map

Equipment now supports **multiple named controllers** through a tag-based map, alongside the legacy single-controller API.

### Attaching Multiple Controllers

```java
// Attach a level controller and a pressure controller to the same valve
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);
```

### Retrieving by Tag

```java
ControllerDeviceInterface lc = valve.getController("LC-100");
ControllerDeviceInterface pc = valve.getController("PC-200");

// Get all controllers on this equipment
Collection<ControllerDeviceInterface> all = valve.getControllers();
```

### Backward Compatibility

The legacy `setController()` method still works. When called, it also registers the controller in the named map using the controller's name as the key:

```java
// Old code — unchanged behavior
valve.setController(myController);

// Controller is also available via the named map
valve.getController(myController.getName()); // returns myController
```

### System-Level Controller Registration

Controllers can also be registered on the `ProcessSystem` itself. During transient simulation, `runTransient()` automatically scans and executes all system-level controllers after the equipment loop:

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(valve);

// Register controller at system level
process.add(levelController);

// During runTransient(), the controller is executed automatically
process.runTransient(1.0, calcId);
```

This is in addition to controllers embedded on individual equipment, which continue to work as before.

---

## Adjusters

Adjusters modify one variable to achieve a target specification.

### Basic Usage

```java
import neqsim.process.equipment.util.Adjuster;

// Adjust heater duty to achieve target outlet temperature
Adjuster tempControl = new Adjuster("TC-100");
tempControl.setAdjustedVariable(heater, "outTemperature");
tempControl.setTargetVariable(stream, "temperature", 80.0, "C");
process.add(tempControl);
```

### Adjustable Variables

| Equipment     | Variable           | Description        |
| ------------- | ------------------ | ------------------ |
| Heater/Cooler | `"duty"`           | Heat duty          |
| Heater/Cooler | `"outTemperature"` | Outlet temperature |
| Compressor    | `"outletPressure"` | Discharge pressure |
| Valve         | `"outletPressure"` | Outlet pressure    |
| Splitter      | `"splitFactor"`    | Split ratio        |
| Stream        | `"flowRate"`       | Flow rate          |

### Target Variables

| Equipment | Variable         | Description             |
| --------- | ---------------- | ----------------------- |
| Stream    | `"temperature"`  | Temperature             |
| Stream    | `"pressure"`     | Pressure                |
| Stream    | `"flowRate"`     | Flow rate               |
| Stream    | `"moleFraction"` | Component mole fraction |
| Separator | `"liquidLevel"`  | Liquid level            |

### Example: Dew Point Control

```java
// Adjust cooler to achieve hydrocarbon dew point
Adjuster dewPointControl = new Adjuster("Dew Point Controller");
dewPointControl.setAdjustedVariable(cooler, "outTemperature");
dewPointControl.setTargetPhaseCondition(stream, "dewpoint", 50.0, "bara");
process.add(dewPointControl);
```

### Solver Settings

```java
adjuster.setMaximumIterations(50);
adjuster.setTolerance(1e-6);
adjuster.setMinimumValue(-1e6);  // Duty lower bound
adjuster.setMaximumValue(1e6);   // Duty upper bound
```

---

## Recycles

Handle recycle streams in process flowsheets.

### Basic Usage

```java
import neqsim.process.equipment.util.Recycle;

// Define recycle
Recycle recycle = new Recycle("Solvent Recycle");
recycle.addStream(recycleStream);
recycle.setOutletStream(inletMixer);
recycle.setTolerance(1e-6);
process.add(recycle);
```

### Recycle Placement

```java
ProcessSystem process = new ProcessSystem();

// Feed
process.add(feed);

// Mixer (combines feed and recycle)
Mixer mixer = new Mixer("M-100");
mixer.addStream(feed);
process.add(mixer);

// Process equipment
process.add(reactor);
process.add(separator);

// Splitter for recycle
Splitter splitter = new Splitter("Splitter", separator.getLiquidOutStream());
splitter.setSplitFactors(new double[]{0.9, 0.1});  // 10% recycle
process.add(splitter);

// Recycle stream
Recycle recycle = new Recycle("Recycle");
recycle.addStream(splitter.getSplitStream(1));
recycle.setOutletStream(mixer);
process.add(recycle);

// Connect mixer to recycle
mixer.addStream(recycle.getOutletStream());

process.run();
```

### Convergence Settings

```java
import neqsim.process.equipment.util.AccelerationMethod;

recycle.setTolerance(1e-6);
recycle.setMaximumIterations(100);

// Acceleration methods (default is DIRECT_SUBSTITUTION)
recycle.setAccelerationMethod(AccelerationMethod.WEGSTEIN);
```

### Choosing an Acceleration Method

| Method | When to use | Typical speedup | Risk |
|---|---|---|---|
| `DIRECT_SUBSTITUTION` *(default)* | Short loops (≤ 5 iterations), well-damped systems, debugging | baseline | none |
| `WEGSTEIN` | Single-variable recycle loops, slowly converging composition loops | 2–3× fewer outer iterations | bounded; may under-relax when slope ≈ 1 |
| `BROYDEN` | Tightly coupled multi-recycle systems, absorber/regenerator trains | 3–5× on hard cases | higher memory, needs stable first 2–3 iterations |

**Default behaviour.** Recycles use `DIRECT_SUBSTITUTION` out of the box — acceleration is opt-in. For existing models validated against a specific convergence trace, leave the default.

**What Wegstein accelerates.** The current implementation accelerates **composition only**. Temperature, pressure, and flow are handled by the normal mixing/flash logic. For recycles where T or P is the slowly-converging variable, Wegstein provides little benefit.

**Safety bounds.** Wegstein's q-factor is clamped to `[-5, 0]` and applied only after a 2-iteration warm-up (`wegsteinDelayIterations`). This prevents oscillation but means Wegstein has no effect on loops that converge in ≤ 2 iterations.

### Applying to a Whole Flowsheet

Instead of setting acceleration per `Recycle`, use the bulk setters:

```java
// All Recycle units in a single ProcessSystem
int updated = process.setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);

// All Recycle units across all areas of a ProcessModel
int total = plant.setRecycleAccelerationMethod(AccelerationMethod.WEGSTEIN);
```

Both methods return the count of `Recycle` units updated. Safe to call before or after `run()`; takes effect on the next iteration.

---

## Setters

Set variable values directly.

### Basic Usage

```java
import neqsim.process.equipment.util.Setter;

// Set flow rate
Setter flowSetter = new Setter("Flow Setter", stream);
flowSetter.setVariable("flowRate", 1000.0, "kg/hr");
process.add(flowSetter);
```

### Mole Fraction Setter

```java
import neqsim.process.equipment.util.MoleFractionSetter;

// Set component mole fraction
MoleFractionSetter compSetter = new MoleFractionSetter("CO2 Setter", stream);
compSetter.setMoleFraction("CO2", 0.02);
process.add(compSetter);
```

---

## Calculators

Perform custom calculations.

### Basic Usage

```java
import neqsim.process.equipment.util.Calculator;

Calculator calc = new Calculator("Energy Balance");
calc.addInputVariable(stream1);
calc.addInputVariable(stream2);
calc.setOutputVariable(heater, "duty");

// Custom calculation (override in subclass or use expression)
calc.setExpression("stream1.enthalpy - stream2.enthalpy");
process.add(calc);
```

---

## PID Controllers

For dynamic simulation with feedback control.

### Basic Usage

```java
import neqsim.process.controllerdevice.PIDController;

PIDController levelControl = new PIDController("LC-100");
levelControl.setMeasuredVariable(separator, "liquidLevel");
levelControl.setControlledVariable(valve, "opening");
levelControl.setSetPoint(0.5);  // 50% level

// Tuning parameters
levelControl.setKp(2.0);    // Proportional gain
levelControl.setKi(0.1);    // Integral gain (1/s)
levelControl.setKd(0.0);    // Derivative gain (s)

process.add(levelControl);
```

### Tuning

```java
// Action
levelControl.setReverseAction(true);  // Increase output decreases PV

// Output limits
levelControl.setOutputMin(0.0);
levelControl.setOutputMax(100.0);

// Anti-windup
levelControl.setAntiWindup(true);
```

### Dynamic Execution

```java
// Run transient with controllers
for (double t = 0; t < 3600; t += 1.0) {
    process.runTransient();

    double pv = levelControl.getProcessVariable();
    double sp = levelControl.getSetPoint();
    double out = levelControl.getOutput();

    System.out.printf("%.1f, %.3f, %.3f, %.1f%n", t, pv, sp, out);
}
```

---

## Process Logic

Conditional logic for process decisions.

### Basic Usage

```java
import neqsim.process.logic.ProcessLogicController;

ProcessLogicController logic = new ProcessLogicController("Emergency Logic");

// Define condition
logic.setCondition(pressure, ">", 100.0, "bara");

// Define action
logic.setAction(shutoffValve, "close");

process.add(logic);
```

### Complex Conditions

```java
// AND condition
logic.addCondition(pressure, ">", 100.0, "bara", "AND");
logic.addCondition(temperature, ">", 150.0, "C", "AND");

// OR condition
logic.addCondition(level, "<", 0.1, "ratio", "OR");
logic.addCondition(level, ">", 0.9, "ratio", "OR");
```

### Alarm Integration

```java
import neqsim.process.alarm.ProcessAlarmManager;

ProcessAlarmManager alarms = process.getAlarmManager();

// High pressure alarm
alarms.addAlarm(separator, "pressure", 95.0, "high", "bara");
alarms.addAlarm(separator, "pressure", 100.0, "highHigh", "bara");

// Low level alarm
alarms.addAlarm(separator, "liquidLevel", 0.2, "low", "ratio");
```

---

## Example: Complete Control System

```java
ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Feed", feedFluid);
feed.setFlowRate(1000.0, "kg/hr");
process.add(feed);

// Heater with temperature control
Heater heater = new Heater("E-100", feed);
process.add(heater);

Adjuster tempControl = new Adjuster("TC-100");
tempControl.setAdjustedVariable(heater, "duty");
tempControl.setTargetVariable(heater.getOutletStream(), "temperature", 80.0, "C");
process.add(tempControl);

// Separator with level control
Separator separator = new Separator("V-100", heater.getOutletStream());
process.add(separator);

ThrottlingValve liquidValve = new ThrottlingValve("LV-100", separator.getLiquidOutStream());
liquidValve.setOutletPressure(5.0, "bara");
process.add(liquidValve);

// Level controller (for dynamic)
PIDController levelControl = new PIDController("LC-100");
levelControl.setMeasuredVariable(separator, "liquidLevel");
levelControl.setControlledVariable(liquidValve, "opening");
levelControl.setSetPoint(0.5);
levelControl.setKp(5.0);
levelControl.setKi(0.5);
process.add(levelControl);

// Pressure control
ThrottlingValve gasValve = new ThrottlingValve("PV-100", separator.getGasOutStream());
process.add(gasValve);

Adjuster pressControl = new Adjuster("PC-100");
pressControl.setAdjustedVariable(gasValve, "outletPressure");
pressControl.setTargetVariable(separator, "pressure", 20.0, "bara");
process.add(pressControl);

// Run steady state
process.run();

// Run dynamic
for (double t = 0; t < 3600; t += 1.0) {
    // Disturbance at t=600
    if (Math.abs(t - 600) < 0.5) {
        feed.setFlowRate(1200.0, "kg/hr");
    }

    process.runTransient();
}
```

---

## Related Documentation

- [ProcessSystem](processmodel/process_system) - Process system with named controllers and connections
- [Dynamic Simulation Guide](../simulation/dynamic_simulation_guide) - Transient simulation with controller scan
- [Dynamic Simulation Helper](dynamic-simulation) - Auto-instrument a process for dynamic simulation
- [Process Package](index.md) - Package overview
- [Equipment](equipment/) - Process equipment
- [Alarm System](../safety/alarm_system_guide) - Alarms
- [Process Logic Framework](../simulation/process_logic_framework) - Advanced logic
