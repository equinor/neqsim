---
title: "Dynamic Process Simulation Enhancements"
description: "Reference guide for advanced dynamic simulation features in NeqSim: controller modes, SFC sequencing, valve nonlinearities, sensor faults, transmitter filtering, alarm shelving, equipment and recycle dynamics, adaptive time stepping, and parallel transient execution."
---

# Dynamic Process Simulation Enhancements

This document covers the dynamic simulation improvements added in PR #2064
(`dynprocess` branch). These features bring NeqSim's transient simulation
capabilities closer to commercial DCS/OTS simulators, covering controller
logic, instrumentation realism, equipment-level dynamics, and numerical
integration infrastructure.

For the base dynamic simulation setup and `DynamicProcessHelper`, see
[Dynamic Simulation with DynamicProcessHelper](dynamic-simulation.md).

## Table of Contents

- [1. Controller Enhancements](#1-controller-enhancements)
  - [1.1 Controller Modes (AUTO/MANUAL/CASCADE)](#11-controller-modes-automanualcascade)
  - [1.2 Two-Degree-of-Freedom PID](#12-two-degree-of-freedom-pid)
  - [1.3 Sequential Function Chart (SFC)](#13-sequential-function-chart-sfc)
  - [1.4 Override (Selector) Control](#14-override-selector-control)
  - [1.5 Split-Range Control](#15-split-range-control)
- [2. Instrumentation Realism](#2-instrumentation-realism)
  - [2.1 Sensor Fault Injection](#21-sensor-fault-injection)
  - [2.2 Transmitter First-Order Filter](#22-transmitter-first-order-filter)
  - [2.3 Alarm Shelving](#23-alarm-shelving)
- [3. Valve Nonlinearities](#3-valve-nonlinearities)
- [4. Equipment Dynamics](#4-equipment-dynamics)
  - [4.1 Separator Internals](#41-separator-internals)
  - [4.2 Heat Exchanger Thermal Model](#42-heat-exchanger-thermal-model)
  - [4.3 Distillation Column MESH Dynamics](#43-distillation-column-mesh-dynamics)
- [5. Numerical Infrastructure](#5-numerical-infrastructure)
  - [5.1 Adaptive Time Stepping](#51-adaptive-time-stepping)
  - [5.2 Parallel Transient Execution](#52-parallel-transient-execution)
  - [5.3 Steady-to-Dynamic Recycle Networks](#53-steady-to-dynamic-recycle-networks)
  - [5.4 Integration Methods](#54-integration-methods)
- [6. Test Coverage](#6-test-coverage)
- [7. Related Documentation](#7-related-documentation)

---

## 1. Controller Enhancements

### 1.1 Controller Modes (AUTO/MANUAL/CASCADE)

Controllers now support the three standard operating modes found in commercial DCS:

| Mode | Behavior |
|------|----------|
| `AUTO` | PID algorithm computes the output (default) |
| `MANUAL` | Operator-set output; PID is bypassed but error tracking continues |
| `CASCADE` | Setpoint received from an upstream (primary) controller |

Switching between modes includes **bumpless transfer**: when transitioning from
MANUAL to AUTO, the integral state is back-calculated to match the current
manual output so the controller output does not jump.

**Package:** `neqsim.process.controllerdevice`

```java
import neqsim.process.controllerdevice.ControllerDeviceInterface.ControllerMode;

// Switch to manual
controller.setMode(ControllerMode.MANUAL);
controller.setManualOutput(45.0); // Set output directly

// Switch back to AUTO with bumpless transfer
controller.setMode(ControllerMode.AUTO);
// Output continues smoothly from 45.0
```

**Key methods on `ControllerDeviceInterface`:**

| Method | Description |
|--------|-------------|
| `getMode()` | Returns current `ControllerMode` |
| `setMode(ControllerMode)` | Switches mode with bumpless transfer |
| `getManualOutput()` | Returns the manual output value |
| `setManualOutput(double)` | Sets the manual output value |

### 1.2 Two-Degree-of-Freedom PID

The PID controller now supports a **setpoint weight** parameter ($b$) for the
proportional term. This decouples the setpoint response from the disturbance
response:

$$
\text{proportional error} = \text{measurement} - b \cdot \text{setpoint}
$$

- $b = 1.0$ (default): standard PID — setpoint changes cause a full proportional kick
- $b = 0.0$: proportional term acts only on measurement changes (no setpoint kick)
- $0 < b < 1$: intermediate behavior

```java
controller.setSetpointWeight(0.5); // Reduce setpoint kick by 50%
double b = controller.getSetpointWeight();
```

### 1.3 Sequential Function Chart (SFC)

A new `SequentialFunctionChart` class implements IEC 61131-3 style sequence
logic for modeling startup/shutdown procedures, ESD logic, and batch operations.

**Package:** `neqsim.process.controllerdevice`

An SFC consists of **steps** (with entry/active/exit actions) connected by
**transitions** (with guard conditions). The chart advances when the active
step's outgoing transition evaluates to `true`.

```java
SequentialFunctionChart sfc = new SequentialFunctionChart("Startup Sequence");

// Define steps with entry/active/exit actions
SfcStep idle = new SfcStep("IDLE");
SfcStep pressurize = new SfcStep("PRESSURIZE");
pressurize.setEntryAction(() -> valve.setPercentValveOpening(10.0));
SfcStep running = new SfcStep("RUNNING");
running.setEntryAction(() -> valve.setPercentValveOpening(100.0));

sfc.addStep(idle);
sfc.addStep(pressurize);
sfc.addStep(running);

// Transitions with guards
sfc.addTransition("IDLE", "PRESSURIZE", () -> startButton);
sfc.addTransition("PRESSURIZE", "RUNNING",
    () -> separator.getPressure() > 50.0);

// Or timed transitions
sfc.addTimedTransition("RUNNING", "IDLE", 3600.0); // After 1 hour

sfc.start();

// During transient loop
for (int i = 0; i < steps; i++) {
    sfc.runTransient(dt);
    process.runTransient();
}

// Query state
String current = sfc.getActiveStepName(); // "PRESSURIZE"
boolean isRunning = sfc.isRunning();
List<String> history = sfc.getEventHistory();
```

**Key methods on `SequentialFunctionChart`:**

| Method | Description |
|--------|-------------|
| `addStep(SfcStep)` | Adds a step (first added becomes initial step) |
| `addTransition(from, to, guard)` | Adds a conditional transition |
| `addTimedTransition(from, to, seconds)` | Adds a time-based transition |
| `start()` | Activates the initial step |
| `stop()` | Deactivates the chart |
| `reset()` | Returns to initial step |
| `runTransient(double dt)` | Advances time and evaluates transitions |
| `getActiveStepName()` | Returns the currently active step |
| `getEventHistory()` | Returns the transition event log |

### 1.4 Override (Selector) Control

The `OverrideControllerStructure` implements HIGH-SELECT or LOW-SELECT logic
between two controllers. This is commonly used for safety overrides where a
temperature or pressure controller can take over from a flow controller.

**Package:** `neqsim.process.controllerdevice.structure`

```java
import neqsim.process.controllerdevice.structure.OverrideControllerStructure;
import neqsim.process.controllerdevice.structure.OverrideControllerStructure.SelectionType;

OverrideControllerStructure override = new OverrideControllerStructure(
    flowController,         // primary (normal operation)
    pressureController,     // override (safety)
    SelectionType.LOW_SELECT  // lowest output wins
);

// In transient loop
override.runTransient(dt);
double output = override.getOutput();
boolean overrideActive = override.isOverrideActive();
```

### 1.5 Split-Range Control

The `SplitRangeControllerStructure` maps a single controller output (0-100%)
to multiple final control elements, each assigned a sub-range.

**Package:** `neqsim.process.controllerdevice.structure`

```java
import neqsim.process.controllerdevice.structure.SplitRangeControllerStructure;

// Two valves: valve A operates 0-50%, valve B operates 50-100%
SplitRangeControllerStructure splitRange =
    new SplitRangeControllerStructure(controller, 2);

// Or with custom ranges
double[] lows = {0.0, 40.0, 70.0};
double[] highs = {40.0, 70.0, 100.0};
SplitRangeControllerStructure customSplit =
    new SplitRangeControllerStructure(controller, lows, highs);

// In transient loop
splitRange.runTransient(dt);
double valve1 = splitRange.getOutput(0); // 0-100% for element 0
double valve2 = splitRange.getOutput(1); // 0-100% for element 1
```

---

## 2. Instrumentation Realism

### 2.1 Sensor Fault Injection

Transmitters can now simulate common sensor failure modes for testing control
system robustness and operator training scenarios.

**Package:** `neqsim.process.measurementdevice`

| Fault Type | Behavior |
|-----------|----------|
| `NONE` | Normal operation (default) |
| `STUCK_AT_VALUE` | Output frozen at the fault parameter value |
| `LINEAR_DRIFT` | Output drifts at `faultParameter` units/second |
| `BIAS` | Constant offset added to the true value |
| `NOISE_BURST` | Gaussian noise burst (amplitude = fault parameter) |
| `SATURATION` | Output clamped at the fault parameter value |

```java
import neqsim.process.measurementdevice.SensorFaultType;

// Inject a stuck-at fault
pressureTransmitter.setFault(SensorFaultType.STUCK_AT_VALUE, 55.0);

// Inject a drift fault (0.1 bar/second)
pressureTransmitter.setFault(SensorFaultType.LINEAR_DRIFT, 0.1);

// Clear the fault
pressureTransmitter.clearFault();
```

### 2.2 Transmitter First-Order Filter

Transmitters can apply a first-order exponential filter (low-pass) to smooth
noisy readings. This models the signal damping typically configured on
industrial transmitters.

The filter equation per sample:

$$
y_k = \alpha \cdot x_k + (1 - \alpha) \cdot y_{k-1}
$$

where $\alpha = 1 - e^{-\Delta t / \tau}$ and $\tau$ is the time constant.

```java
// Set a 2-second filter time constant
pressureTransmitter.setFirstOrderTimeConstant(2.0);

// Disable filtering
pressureTransmitter.setFirstOrderTimeConstant(0.0);

// Query
double tau = pressureTransmitter.getFirstOrderTimeConstant();
```

Typical industrial transmitter time constants range from 0.5 to 10 seconds.
A value of 0 (the default) disables the filter entirely.

### 2.3 Alarm Shelving

Alarms can be temporarily **shelved** (suppressed) while the alarm state machine
continues to track the process value. This models the operator practice of
shelving nuisance alarms during maintenance or known upset conditions.

```java
// Shelve indefinitely with a reason
pressureTransmitter.shelveAlarm("Maintenance in progress");

// Shelve until a specific simulation time (e.g., 3600 seconds)
pressureTransmitter.shelveAlarm("Scheduled work", 3600.0);

// Check status
boolean shelved = pressureTransmitter.isAlarmShelved();

// Remove shelve manually
pressureTransmitter.unshelveAlarm();
```

While shelved:
- The alarm state machine continues evaluating thresholds internally
- No `AlarmEvent` objects are generated
- If a timed shelve expires, normal alarm behavior resumes automatically

The underlying `AlarmState` class provides the `shelve()`, `unshelve()`, and
`isShelved()` methods directly if you work with the alarm system outside of
transmitters.

---

## 3. Valve Nonlinearities

The `ThrottlingValve` now supports realistic valve dynamics that affect
closed-loop control performance:

| Parameter | Method | Description |
|-----------|--------|-------------|
| **Deadband** | `setValveDeadband(double)` | Minimum signal change required before the valve moves (%) |
| **Stiction** | `setValveStiction(double)` | Static friction — valve sticks until the signal exceeds the breakaway force (%) |
| **Hysteresis** | `setValveHysteresis(double)` | Difference in valve position between increasing and decreasing signal (%) |

```java
ThrottlingValve valve = new ThrottlingValve("CV-101", feed);
valve.setOutletPressure(40.0);

// Configure nonlinearities
valve.setValveDeadband(0.5);   // 0.5% deadband
valve.setValveStiction(2.0);    // 2% stiction
valve.setValveHysteresis(1.0);  // 1% hysteresis

// Query
double db = valve.getValveDeadband();
double st = valve.getValveStiction();
double hy = valve.getValveHysteresis();
```

These are modeled in the valve's `runTransient()` method. The effective valve
position may differ from the commanded position due to these nonlinearities,
which is critical for control loop performance analysis and valve diagnostics
studies.

---

## 4. Equipment Dynamics

### 4.1 Separator Internals

The `Separator` now models two internal components that affect dynamic behavior:

#### Weir-Controlled Liquid Outflow

A weir controls the liquid overflow rate using the Francis weir formula:

$$
Q_{\text{weir}} = C_d \cdot L_w \cdot (h - h_w)^{3/2}
$$

where $L_w$ is the weir length, $h$ is the liquid level, $h_w$ is the weir
height, and $C_d$ is the discharge coefficient.

```java
Separator sep = new Separator("HP Sep", feed);
sep.setWeirHeight(0.3);    // 0.3 m weir height
sep.setWeirLength(1.0);    // 1.0 m weir length

// Query the calculated overflow rate (after runTransient)
double qWeir = sep.getWeirOverflowRate(); // m³/s
```

When both `weirHeight` and `weirLength` are set to non-zero values, the weir
overflow rate is applied in `runTransient()` to modulate the liquid outlet flow.

#### Mist Eliminator Pressure Drop

A mist eliminator (demister pad) adds a velocity-dependent pressure drop to the
gas outlet:

$$
\Delta P_{\text{mist}} = K \cdot \rho_g \cdot v_g^2 \cdot t
$$

where $K$ is the pressure drop coefficient, $\rho_g$ is the gas density,
$v_g$ is the gas superficial velocity, and $t$ is the pad thickness.

```java
sep.setMistEliminatorDpCoeff(150.0);      // Coefficient K
sep.setMistEliminatorThickness(0.15);     // 0.15 m pad thickness

// Query the calculated pressure drop (after runTransient)
double dpMist = sep.getMistEliminatorPressureDrop(); // Pa
```

#### Boot Volume

For three-phase separators, a boot (sump) volume can be specified:

```java
sep.setBootVolume(0.5); // 0.5 m³ boot volume
```

### 4.2 Heat Exchanger Thermal Model

The `HeatExchanger` now has a full dynamic thermal model with wall energy
storage and optional fluid holdup volumes.

#### Wall Energy ODE

When `dynamicModelEnabled` is `true`, the heat exchanger tracks wall
temperature using a lumped-parameter energy balance:

$$
M_w C_{p,w} \frac{dT_w}{dt} = h_s A (T_{\text{shell}} - T_w) - h_t A (T_w - T_{\text{tube}})
$$

where $M_w$ is the wall mass, $C_{p,w}$ is the wall heat capacity,
$h_s$ and $h_t$ are the shell-side and tube-side heat transfer coefficients,
and $A$ is the heat transfer area.

```java
HeatExchanger hx = new HeatExchanger("E-101", hotStream, coldStream);
hx.setDynamicModelEnabled(true);

// Wall properties
hx.setWallMass(500.0);          // kg
hx.setWallCp(500.0);            // J/(kg·K)
hx.setHeatTransferArea(25.0);   // m²

// Heat transfer coefficients
hx.setShellSideHtc(400.0);     // W/(m²·K)
hx.setTubeSideHtc(800.0);      // W/(m²·K)
```

#### Fluid Holdup (CSTR Model)

When holdup volumes are non-zero, the shell and tube fluids are modeled as
well-mixed accumulation volumes (CSTR), adding thermal inertia:

$$
\rho V C_p \frac{dT_f}{dt} = \dot{m} C_p (T_{\text{in}} - T_f) + h A (T_w - T_f)
$$

```java
hx.setShellHoldupVolume(0.5);   // m³ shell-side fluid volume
hx.setTubeHoldupVolume(0.3);    // m³ tube-side fluid volume
```

With holdup volume = 0 (default), the model reduces to the wall-only ODE.

**Key methods:**

| Method | Description |
|--------|-------------|
| `setDynamicModelEnabled(boolean)` | Enable/disable the dynamic thermal model |
| `setWallMass(double)` | Wall metal mass in kg |
| `setWallCp(double)` | Wall heat capacity in J/(kg·K) |
| `setHeatTransferArea(double)` | Heat transfer area in m² |
| `setShellSideHtc(double)` | Shell-side HTC in W/(m²·K) |
| `setTubeSideHtc(double)` | Tube-side HTC in W/(m²·K) |
| `setShellHoldupVolume(double)` | Shell-side fluid volume in m³ |
| `setTubeHoldupVolume(double)` | Tube-side fluid volume in m³ |
| `getWallTemperature()` | Current wall temperature in K |

### 4.3 Distillation Column MESH Dynamics

The `DistillationColumn` now supports per-tray dynamic modeling with liquid
holdup, energy balance, and vapor hydraulics.

#### Tray Liquid Holdup

When `dynamicColumnEnabled` is `true`, each tray tracks its liquid holdup using
the Francis weir overflow formula:

```java
DistillationColumn col = new DistillationColumn("Deethanizer", 10, true, true);
col.setDynamicColumnEnabled(true);
col.setTrayWeirHeight(0.05);  // 5 cm weir height
col.setTrayWeirLength(1.2);   // 1.2 m weir length

// After transient steps, query holdup
double[] holdups = col.getTrayLiquidHoldup(); // m³ per tray
```

#### Energy Balance

When `dynamicEnergyEnabled` is `true`, per-tray enthalpy is tracked and
vapor flow is driven by the tray pressure drop:

```java
col.setDynamicEnergyEnabled(true);
col.setTrayDryPressureDrop(500.0); // 500 Pa dry tray pressure drop

// After transient steps, query per-tray enthalpy
double[] enthalpies = col.getTrayEnthalpy(); // J per tray
```

The vapor flow through each tray is calculated from:

$$
V = \sqrt{\frac{\Delta P_{\text{tray}}}{\rho_V}} \cdot A_{\text{tray}}
$$

All dynamic features default to `false`/`0`, so existing steady-state
column calculations are unaffected.

---

## 5. Numerical Infrastructure

### 5.1 Adaptive Time Stepping

The `ProcessSystem` now supports adaptive time stepping that automatically
adjusts $\Delta t$ based on solution stability:

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

process.setAdaptiveTimestepEnabled(true);
process.setMinTimestep(0.001);    // seconds
process.setMaxTimestep(10.0);     // seconds
process.setAdaptiveTimestepTolerance(0.01); // relative tolerance

UUID id = UUID.randomUUID();
double actualDt = process.runTransientAdaptive(1.0, id);
// actualDt may be smaller or larger than the requested 1.0
```

Every `ProcessSystem` and `ProcessModel` transient entry point requires a
finite, positive timestep. Zero, negative, NaN, and infinite values throw
`IllegalArgumentException` before snapshots, setters, events, process areas,
equipment, controllers, clocks, or identifiers can change. Adaptive stepping
rejects invalid requested values instead of silently clamping them.

A multi-area `ProcessModel` also requires every child `ProcessSystem` to start
the step on the same finite simulation clock. A material mismatch throws
`IllegalStateException` before the first area advances or a shared
`EventScheduler` fires. Reset or explicitly synchronize restored, copied, or
independently advanced areas before calling `ProcessModel.runTransient(...)`;
this prevents one scheduled event from affecting only the areas that happen to
run later in the insertion order.

The adaptive algorithm compares state changes between the full step and two
half-steps, reducing or increasing $\Delta t$ based on the relative error.

Setter units form a dedicated pre-step specification phase. Each setter receives
exactly one `runTransient(dt, id)` call per process timestep, so its specification
is applied once and its own clock advances by the same $\Delta t$ as the process.
Setters are excluded from the later explicit, semi-implicit, and parallel
equipment passes.

Controller execution is coalesced by object identity and timestep calculation
identifier. Equipment that actually integrates a controller passes the same
calculation identifier to the controller; registering that object separately with
`process.add(controller)` therefore keeps it discoverable without advancing its
time, integral, derivative, or event-log state twice. Merely attaching a controller
to equipment that does not execute it does not suppress the standalone update.
Repeated standalone registration is also coalesced to one update per timestep;
distinct controller objects remain independent even if they share a name.
`ControllerDeviceBaseClass` tracks the identifier automatically and makes
repeated calls with one identifier idempotent, including semi-implicit equipment
passes. Custom controller implementations that integrate inside equipment should
implement `hasRunTransient(UUID)` and the same one-update-per-identifier contract.

Steady-state equipment can be evaluated more than once while a solver refines one
physical timestep. The default `SimulationInterface.runTransient(dt, id)` still
executes `run(id)` on every evaluation, so algebraic recycles retain both
semi-implicit passes, but advances the equipment clock only on the first
successful evaluation of a non-null calculation identifier. A new identifier
advances time for the next physical step. This keeps algebraic solver iterations
separate from simulated time, including normal low-flow returns.

### 5.2 Parallel Transient Execution

For large flowsheets, equipment-level transient calculations can be run in
parallel threads:

```java
process.setParallelTransientEnabled(true);
process.setTransientThreadPoolSize(4); // Number of worker threads

// Transient steps now run equipment in parallel
process.runTransient();
```

The configured workers are reused across timesteps. NeqSim creates the executor
lazily for each `ProcessSystem`, keeps it out of serialized model state, and
allows idle daemon workers to time out after 60 seconds. Changing
`setTransientThreadPoolSize(...)` or disabling parallel transient execution
retires the existing pool. This avoids creating and destroying a complete
thread pool for every timestep in long dynamic studies.

Parallel transient execution uses the same cached process graph as steady-state
parallel execution. Equipment is partitioned into topological levels: all
upstream groups in one level finish before downstream equipment is submitted,
while independent groups in the same level can use separate workers. Groups
that must share mutable inlet state execute sequentially within one worker.
This prevents a downstream unit from observing a partially updated upstream
stream merely because both units were submitted in the same timestep.

If the thread calling `runTransient(...)` is interrupted while waiting for
parallel equipment, NeqSim restores the caller's interrupt status and stops
waiting. No downstream level is submitted. Later parallel passes in the same
timestep observe that status and do not submit additional equipment work.
Queued futures are cancelled without interrupting equipment already updating
state. Because those running updates are not transactionally interruptible,
NeqSim aborts the remaining controller, measurement, result-storage, and
event-publication phases for that timestep. Callers should still treat an
interrupted timestep as incomplete rather than as an atomic rollback.

**Note:** Parallel execution is beneficial for flowsheets with many independent
branches. Topological ordering covers feed-forward stream dependencies. When a
flowsheet contains `Recycle` or `RecycleFlowCoordinator`, `ProcessSystem`
automatically falls back to insertion-order sequential transient execution even
if parallel transient execution is enabled. This preserves the ordered recycle
dependency; it does not iterate the loop to steady convergence inside a physical
timestep. For small flowsheets, scheduling overhead may also outweigh the
benefit.

### 5.3 Steady-to-Dynamic Recycle Networks

A converged steady-state flowsheet can enter transient mode without replacing
its recycle objects. The standard `Recycle` uses the accepted outlet state as the
upstream state for the current evaluation, then publishes its current inlet for
the next evaluation. Wegstein or Broyden acceleration is disabled during that
transport update and restored for the next steady-state solve; convergence
acceleration is not applied between physical timesteps.

The transient network modes make ownership of flow and pressure explicit:

| Equipment | Mode | Use |
|-----------|------|-----|
| `Compressor` | `MAP_FLOW_SOLVER` (default) | The compressor map solves the upstream flow, preserving historical behavior. |
| `Compressor` | `QUASI_STEADY` | Upstream dynamic equipment owns flow; the map evaluates discharge pressure, head, and power algebraically. |
| `Splitter` | `DOWNSTREAM_DEMAND` (default) | Pressure-driven downstream branches calculate flow and the splitter aggregates their demand. |
| `Splitter` | `PRESCRIBED_SPLIT` | The current inlet flow is distributed from configured split factors or branch flow rates. |
| `Cooler` | dynamic temperature control disabled (default) | Historical specified-temperature behavior is retained. |
| `Cooler` | configured dynamic temperature control | A bounded utility-valve/NTU model applies actuator and process thermal lags. |

Example handoff after the steady-state recycle has converged:

```java
process.run();

compressor.setTransientCalculationMode(
    Compressor.TransientCalculationMode.QUASI_STEADY);
compressor.setControllerSpeedRateLimitEnabled(true);
compressor.setCalculateSteadyState(false);

splitter.setTransientSplitMode(Splitter.TransientSplitMode.PRESCRIBED_SPLIT);
splitter.setCalculateSteadyState(false);

cooler.setDynamicDesignValveOpening(50.0);
cooler.configureDynamicTemperatureControl(
    designMassFlowKgPerHour, 80.0, 35.0, 20.0, "C");
cooler.setDynamicTimeConstants(5.0, 20.0);
cooler.setCalculateSteadyState(false);

recycle.setCalculateSteadyState(false);
process.runTransient(0.25, UUID.randomUUID());
```

For a pressure-driven anti-surge branch, add a
`RecycleFlowCoordinator` after the recycle valve in flowsheet order. It bounds
the valve-requested recycle flow, writes a mass-conserving main/recycle split,
and seeds the next splitter evaluation. Its signal dependency is not visible in
stream topology, so its presence also selects sequential execution.

### 5.4 Integration Methods

The `ProcessSystem` supports selectable integration methods:

```java
import neqsim.process.processmodel.ProcessSystem.IntegrationMethod;

process.setIntegrationMethod(IntegrationMethod.EXPLICIT_EULER);   // Default
process.setIntegrationMethod(IntegrationMethod.RUNGE_KUTTA_4);    // Higher-order
```

| Method | Order | Stability | Cost |
|--------|-------|-----------|------|
| `EXPLICIT_EULER` | 1st | Conditional | 1 evaluation/step |
| `RUNGE_KUTTA_4` | 4th | Better | 4 evaluations/step |

---

## 6. Test Coverage

The features in this guide are covered by eight focused test classes with 85
total tests:

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `DynamicImprovementsTest` | 17 | Controller modes, 2-DOF PID, SFC, control structures, gain scheduling, event logging, performance metrics |
| `DynamicImprovementsPhase2Test` | 27 | Sensor faults, valve nonlinearities, adaptive timestep, parallel transient, integration methods, JSON process builder |
| `DynamicImprovementsPhase3Test` | 16 | Transmitter filter, alarm shelving, separator internals, HX thermal model, distillation MESH dynamics |
| `ProcessSystemParallelTransientTest` | 9 | Worker reuse, copy lifecycle, dependency ordering, independent-level concurrency, and interruption behavior at waits and level boundaries |
| `ProcessSystemTransientSetterTest` | 3 | Single setter application, repeated-step clock advancement, and explicit, semi-implicit, and parallel execution |
| `ProcessSystemTransientControllerTest` | 5 | Equipment execution, semi-implicit passes, attachment-only fallback, duplicate standalone registration, identity semantics, and repeated timesteps |
| `ProcessSystemTransientAlgebraicTimeTest` | 4 | Repeated algebraic evaluation, semi-implicit recycle iteration, low-flow completion identifiers, null-ID compatibility, and one clock advance per timestep |
| `ProcessSystemTransientTimestepValidationTest` | 4 | Finite-positive process, adaptive, and multi-area timestep preflight plus valid nearby steps |

Run all tests:

```bash
./mvnw test -Dtest=DynamicImprovementsTest,DynamicImprovementsPhase2Test,DynamicImprovementsPhase3Test,ProcessSystemParallelTransientTest,ProcessSystemTransientSetterTest,ProcessSystemTransientControllerTest,ProcessSystemTransientAlgebraicTimeTest,ProcessSystemTransientTimestepValidationTest
```

---

## 7. Related Documentation

- [Dynamic Simulation with DynamicProcessHelper](dynamic-simulation.md) — Base dynamic simulation setup
- [Dynamic Simulation Guide](../simulation/dynamic_simulation_guide.md) — Comprehensive transient simulation guide
- [Process Controllers and Logic](controllers.md) — PID controllers and adjusters
- [Separators](equipment/separators.md) — Separator configuration and sizing
- [Heat Exchangers](equipment/heat_exchangers.md) — Heat exchanger types and design
