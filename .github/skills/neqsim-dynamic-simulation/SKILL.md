---
name: neqsim-dynamic-simulation
description: "Dynamic simulation guidance for NeqSim. USE WHEN: running transient simulations, modeling startup/shutdown, tuning PID controllers, analyzing pressure/level dynamics, performing blowdown/depressurization, or setting up measurement devices and control loops. Covers runTransient, DynamicProcessHelper, controller tuning, and dynamic equipment configuration."
last_verified: "2026-07-04"
---

# Dynamic Simulation Guidance

Guide for transient/dynamic process simulation in NeqSim.

## When to Use Dynamic Simulation

- Startup and shutdown sequences
- Controller tuning and loop analysis
- Pressure relief / blowdown scenarios
- Level and pressure dynamics
- Compressor surge analysis
- Pipeline transients (slug flow)
- Emergency depressurization (EDP/ESD)
- P&ID-derived valve actions where pressure, level, controller response, or inventory release changes with time

For valve-action studies that start from P&ID symbols and plant data, also load
`neqsim-pid-process-operations` to define the process graph, valve semantics,
historian tag mapping, and event schedule before running `runTransient`.

## Dynamic Simulation Architecture

NeqSim dynamic simulation uses the `runTransient(double dt)` method on `ProcessSystem`.
Each timestep:
1. All measurement devices read current values
2. All controllers calculate new outputs
3. All equipment updates for the timestep
4. Flash calculations update thermodynamic state

## Basic Dynamic Setup

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.controllerdevice.ControllerDeviceInterface;
import neqsim.process.controllerdevice.ControllerDeviceBaseClass;
import neqsim.process.measurementdevice.LevelTransmitter;
import neqsim.process.measurementdevice.PressureTransmitter;

// Build steady-state process first
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.05);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");

Separator sep = new Separator("HP Sep", feed);
sep.setInternalDiameter(2.0);  // m — for dynamic simulation, set directly for level dynamics
sep.setSeparatorLength(6.0);   // m — for design purposes, use SeparatorMechanicalDesign instead

ThrottlingValve gasValve = new ThrottlingValve("gas valve", sep.getGasOutStream());
gasValve.setOutletPressure(20.0, "bara");

ThrottlingValve liqValve = new ThrottlingValve("liq valve", sep.getLiquidOutStream());
liqValve.setOutletPressure(10.0, "bara");

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(sep);
process.add(gasValve);
process.add(liqValve);

// Run steady state first
process.run();
```

## Adding Measurement Devices

```java
// Pressure transmitter
PressureTransmitter PT100 = new PressureTransmitter("PT-100", sep);
PT100.setUnit("bara");
PT100.setMaximumValue(100.0);
PT100.setMinimumValue(0.0);
process.add(PT100);

// Level transmitter
LevelTransmitter LT100 = new LevelTransmitter("LT-100", sep);
LT100.setUnit("m");
process.add(LT100);

// Temperature transmitter
TemperatureTransmitter TT100 = new TemperatureTransmitter("TT-100", sep);
TT100.setUnit("C");
process.add(TT100);

// Flow transmitter
VolumeFlowTransmitter FT100 = new VolumeFlowTransmitter("FT-100", feed);
FT100.setUnit("kg/hr");
process.add(FT100);
```

## Controller Configuration

### PID Controller

```java
// Level controller on liquid valve
ControllerDeviceInterface LC100 = new ControllerDeviceBaseClass();
LC100.setControllerSetPoint(1.0);          // Target level = 1.0 m
LC100.setTransmitter(LT100);              // Controlled variable
LC100.setReverseActing(true);             // Level up -> valve opens
LC100.setControllerParameters(0.5, 100.0, 10.0); // Kp, Ti (s), Td (s)

// Attach controller to valve
liqValve.addController("LC-100", LC100);

// Pressure controller on gas valve
ControllerDeviceInterface PC100 = new ControllerDeviceBaseClass();
PC100.setControllerSetPoint(50.0);         // Target pressure = 50 bara
PC100.setTransmitter(PT100);
PC100.setReverseActing(false);            // Pressure up -> valve opens more
PC100.setControllerParameters(1.0, 50.0, 0.0);

gasValve.addController("PC-100", PC100);
```

### Controller Tuning Guidelines

| Loop Type | Typical Kp | Typical Ti (s) | Typical Td (s) |
|-----------|-----------|----------------|-----------------|
| Level (averaging) | 0.5-2.0 | 60-300 | 0 |
| Level (tight) | 2.0-5.0 | 30-60 | 0-10 |
| Pressure (gas) | 0.5-2.0 | 20-100 | 0-5 |
| Flow | 0.3-1.0 | 5-30 | 0 |
| Temperature | 0.5-2.0 | 60-600 | 10-60 |

## Running Dynamic Simulation

```java
// Timestep loop
double dt = 1.0;  // seconds
int nSteps = 3600; // 1 hour

// Storage for time history
double[] time = new double[nSteps];
double[] pressure = new double[nSteps];
double[] level = new double[nSteps];

for (int i = 0; i < nSteps; i++) {
    time[i] = i * dt;

    // Introduce disturbance at t = 300 s
    if (i == 300) {
        feed.setFlowRate(15000.0, "kg/hr");  // Step change +50%
    }

    process.runTransient(dt);

    pressure[i] = PT100.getMeasuredValue();
    level[i] = LT100.getMeasuredValue();
}
```

## Python Dynamic Simulation

```python
from neqsim import jneqsim
import numpy as np
import matplotlib.pyplot as plt

# Build process (same pattern as Java)
# ... create fluid, equipment, controllers ...

process.run()  # Steady state

dt = 1.0
n_steps = 3600
times = np.zeros(n_steps)
pressures = np.zeros(n_steps)
levels = np.zeros(n_steps)

for i in range(n_steps):
    times[i] = i * dt
    if i == 300:
        feed.setFlowRate(15000.0, "kg/hr")

    process.runTransient(dt)
    pressures[i] = PT100.getMeasuredValue()
    levels[i] = LT100.getMeasuredValue()

fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(10, 8))
ax1.plot(times / 60, pressures)
ax1.set_ylabel("Pressure (bara)")
ax1.set_xlabel("Time (min)")
ax1.grid(True)

ax2.plot(times / 60, levels)
ax2.set_ylabel("Level (m)")
ax2.set_xlabel("Time (min)")
ax2.grid(True)
plt.tight_layout()
```

## P&ID Valve-Action Dynamic Studies

Use this pattern when evaluating actions such as closing an outlet valve,
opening a bypass, tripping a shutdown valve, or opening a drain/vent:

1. Run and validate the steady-state base case.
2. Define an event schedule with action type, affected valve, start time, and ramp duration.
3. Map each P&ID valve to the correct NeqSim role: control valve, boundary switch, check-valve direction constraint, or blowdown/flare path.
4. Run `process.runTransient(dt)` for controller and inventory dynamics, or use `neqsim-depressurization-mdmt` for dedicated blowdown/MDMT cases.
5. Save time series for pressure, level, temperature, valve position, flow, and any flare or vent stream.

Minimum result keys: `max_pressure_bara`, `max_level_m`, `min_temperature_C`,
`peak_flare_flow_kg_s`, `time_to_alarm_s`, and `time_to_new_steady_state_s`.

## Depressurization / Blowdown

```java
// For vessel depressurization, use the safety/depressuring agent
// Key pattern: open a blowdown valve at t=0 and track P, T vs time

ThrottlingValve bdv = new ThrottlingValve("BDV", sep.getGasOutStream());
bdv.setOutletPressure(1.0, "bara");  // Vent to atmosphere
bdv.setCv(500.0);  // Valve Cv

// Controller: fully open at t=0
// Or use step change in valve opening
```

## Transfer Function Blocks

For more advanced control logic:

```java
import neqsim.process.controllerdevice.TransferFunctionBlock;

TransferFunctionBlock leadLag = new TransferFunctionBlock();
// Configure lead-lag, deadtime, filters as needed
```

## Common Pitfalls

1. **Always run steady state first**: Call `process.run()` before `runTransient()`
2. **Timestep size**: Start with 1.0 s, reduce if oscillating (0.1-0.5 s)
3. **Reverse acting**: Level controllers are usually reverse-acting (level up = open valve)
4. **Controller windup**: Large setpoint changes can cause integral windup
5. **Separator dimensions**: Must set `setInternalDiameter()` and `setSeparatorLength()` for meaningful level dynamics. For dynamic simulation, set directly on the separator; for design purposes, configure via `SeparatorMechanicalDesign` (see neqsim-api-patterns skill)
6. **Measurement range**: Set min/max on transmitters to match process range
