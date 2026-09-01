---
name: neqsim-dynamic-simulation
description: "Dynamic simulation guidance for NeqSim. USE WHEN: running transient simulations, modeling startup/shutdown, tuning PID controllers, analyzing pressure/level dynamics, performing blowdown/depressurization, or setting up measurement devices and control loops. Covers runTransient, DynamicProcessHelper, controller tuning, and dynamic equipment configuration."
last_verified: "2026-08-31"
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

NeqSim dynamic simulation advances a `ProcessSystem` with `runTransient()`
(using the configured timestep) or `runTransient(double dt, UUID id)` (using an
explicit timestep and calculation identifier).
Pass a finite, positive timestep. Process and model entry points reject zero,
negative, NaN, and infinite values before any area or equipment state changes.
Adaptive stepping rejects invalid requests rather than clamping them.

Each timestep:
1. Flowsheet-wide settings are applied. Each setter unit receives exactly one
   transient call, applying its specification and advancing its own clock once.
2. Simulation time advances, then due events and field inputs are applied.
3. Equipment updates, including its thermodynamic calculations, run in insertion
   order or dependency-aware graph levels when parallel transient execution is
   enabled. Setter units are excluded from these explicit, semi-implicit, and
   parallel equipment passes.
   Steady-state algebraic equipment still evaluates on every requested pass,
   but its local clock advances once per non-null timestep calculation identifier.
   Reusing an identifier may refine a recycle without advancing physical time;
   use a new identifier for the next physical timestep.
4. Standalone controllers run; controllers actually executed inside equipment
   retain their equipment-specific timing for compatibility. Execution is
   coalesced by object identity and the timestep calculation identifier. Merely
   attaching a controller to equipment that does not execute it must not suppress
   the standalone update; repeated standalone registration runs once per step.
   `ControllerDeviceBaseClass` also makes repeated calls with one identifier
   idempotent, including semi-implicit equipment passes. Custom controllers
   integrated by equipment must implement the same identifier contract through
   `hasRunTransient(UUID)`.
5. Measurement devices are sampled, alarms are evaluated, and history is stored.

A non-null UUID therefore identifies **one physical timestep**, not an entire
outer time-marching simulation. Refinements of that physical step reuse the
same physical-step UUID; the next accepted physical step must use a different
UUID. For ordinary loops `runTransient()` is safe because it creates a fresh
UUID each call. Deterministic safety/OTS workflows can use
`TransientStepIdentifier.deterministicPhysicalStep(scope, stepIndex)`.

`ProcessSystem.runTransientAdaptive(...)` is **not yet a transactional adaptive
integrator**. The current implementation validates the request, advances trial
state directly, estimates a temperature-change heuristic, and chooses a
subsequent timestep. It does not yet provide rejected-step process/event/control
rollback or a full-step versus two-half-step error estimate. Do not use it as
evidence of qualified stiff/adaptive professional dynamics until the #2911
transactional-step work is complete.

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
// Level controller on liquid OUTLET valve — DIRECT acting (reverseActing = false):
// level up (error > 0) -> output up -> outlet valve opens -> level falls. Setting
// reverseActing(true) here inverts the sign and makes the loop unstable (runaway).
ControllerDeviceInterface LC100 = new ControllerDeviceBaseClass();
LC100.setControllerSetPoint(1.0);          // Target level = 1.0 m
LC100.setTransmitter(LT100);              // Controlled variable
LC100.setReverseActing(false);            // liquid-outlet level valve is direct acting
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

### Controller Deadband (SP-PV)

`ControllerDeviceBaseClass` supports a native SP-PV deadband via
`setDeadBand(double)` / `getDeadBand()`. While the absolute control error stays
inside the band the controller output is **frozen** (holds the last valve
position) and the integral term does **not** accumulate; default 0 disables it.
The deadband is in the controller error unit (percent in the default percent
mode, else the configured engineering unit). This is the standard DCS averaging-
level deadband used to stop valve cycling.

```java
levelController.setDeadBand(0.5);   // hold the valve while |PV - SP| <= 0.5 %
```

**Beware the deadband limit cycle.** On an integrating (level) process a
deadband delays correction until the level reaches the band edge; the delayed
correction then overshoots and the cycle repeats, giving a square-wave valve
trace. Removing (or shrinking) the deadband is the usual fix. If the installed
pip `neqsim` predates `setDeadBand`, emulate it by toggling controller mode
each step: `setMode(ControllerMode.MANUAL)` while `|PV%-SP%| <= deadband` (holds
output) and `setMode(ControllerMode.AUTO)` otherwise (bumpless resume) - this is
numerically identical to the native deadband.

### Dynamic level-loop recipe (get the sequence right)

A dynamic separator level loop only responds if the vessel is switched out of
steady-state mode **and** the liquid outlet valve is direct acting. The exact,
easy-to-get-wrong sequence is:

```java
// 1. Build and solve the steady state first (sets inventory, flows, holdup).
process.run();

// 2. Switch the vessel (and its outlet valve) to dynamic mode. If this is left
//    on, the separator recomputes steady state every step and the level is
//    pinned at its default (0.5 fraction) no matter what the controller does.
sep.setCalculateSteadyState(false);
liqValve.setCalculateSteadyState(false);

// 3. Set the physical geometry and the starting liquid level (as a 0..1 fraction
//    of the vessel). Do this AFTER run() so it is not overwritten by steady state.
sep.setInternalDiameter(2.0);   // m — drives holdup volume / level dynamics
sep.setSeparatorLength(6.0);    // m
sep.setLiquidLevel(0.30);       // start at 30 %

// 4. Direct-acting level controller on the liquid OUTLET valve (see above).
LevelTransmitter LT100 = new LevelTransmitter("LT-100", sep);
LT100.setUnit("m");
ControllerDeviceInterface LC100 = new ControllerDeviceBaseClass();
LC100.setTransmitter(LT100);
LC100.setControllerSetPoint(0.30 * sep.getInternalDiameter()); // SP in the LT unit
LC100.setReverseActing(false);                 // liquid-outlet level valve = direct acting
LC100.setControllerParameters(1.0, 300.0, 0.0); // averaging level: loose Kp, long Ti, no Td
liqValve.addController("LC-100", LC100);

// 5. Advance the transient with a fresh physical-step identity each step.
for (long step = 0; step < 600; step++) {
  java.util.UUID physicalStepId =
      neqsim.process.dynamics.TransientStepIdentifier.deterministicPhysicalStep("level-loop", step);
  process.runTransient(1.0, physicalStepId);   // dt = 1 s
}
```

**Gotchas:**
- **Level pinned at 0.5** — the vessel is still in steady-state mode; call
  `setCalculateSteadyState(false)` on the separator (and its outlet valve).
- **Level runs away** — the liquid-outlet level controller is reverse acting; it
  must be `setReverseActing(false)` (direct acting). A gas-outlet pressure valve
  is also direct acting (`false`); reverse acting is for cases where more output
  reduces the measured value (e.g. a controller manipulating an inlet/feed valve).
- **Set `setLiquidLevel` after `run()`** — a steady-state solve resets the level,
  so set the starting level and geometry after the first `run()`.
- **One UUID per physical step** — do not create one UUID before the outer time
  loop and reuse it. Built-in controllers/equipment intentionally treat repeated
  calls with one UUID as refinements of the same physical timestep.
- For an averaging level loop, use a loose `Kp` and long `Ti` (see the tuning
  table) and consider an SP-PV deadband only with care (see the limit-cycle note
  above).

After the run, use `ControllerPerformanceMetrics.fromEventLog(LC100.getEventLog())`
(or `LC100.getPerformanceMetrics()`) to score the tuning (IAE/ISE/ITAE, PV
variability, valve travel and reversals, settling time) — see the KPI section below.

### Loop-tuning KPIs (ControllerPerformanceMetrics)

`ControllerPerformanceMetrics`
(`neqsim.process.controllerdevice.ControllerPerformanceMetrics`) computes the
standard loop-tuning KPIs from a controller event log (or from raw time / PV / SP
/ output arrays) so tuning studies report consistent numbers without
re-implementing the definitions. It is the preferred way to compare two PID
tunings on the same disturbance.

Metrics: `getIntegralAbsoluteError()` (IAE), `getIntegralSquaredError()` (ISE),
`getIntegralTimeAbsoluteError()` (ITAE, time referenced to the first sample),
`getProcessValueStandardDeviation()` (PV variability), `getPeakAbsoluteError()`,
`getControllerOutputTravel()` (total valve travel), `getControllerOutputReversals()`
(valve direction reversals), and `getSettlingTime()` (time of the last sample
outside the settling band, default 2 % of max(|SP|, 1)).

```java
// After a runTransient loop with a logging controller:
ControllerPerformanceMetrics kpi = LC100.getPerformanceMetrics();          // from getEventLog()
// or, explicitly / with a custom settling band:
ControllerPerformanceMetrics kpi2 =
    ControllerPerformanceMetrics.fromEventLog(LC100.getEventLog(), 0.05);   // 5 % band

double iae = kpi.getIntegralAbsoluteError();
double valveTravel = kpi.getControllerOutputTravel();
int reversals = kpi.getControllerOutputReversals();
double settlingTime = kpi.getSettlingTime();
logger.info("IAE={} travel={} reversals={} settle={} s", iae, valveTravel, reversals, settlingTime);

// Or build directly from arrays (e.g. PV/OP pulled from a historian):
ControllerPerformanceMetrics kpi3 =
    ControllerPerformanceMetrics.fromArrays(time, pv, sp, op);
```

- Integral criteria use trapezoidal integration over the sample intervals, so
  irregular time steps are handled correctly.
- A **lower** IAE/ISE/ITAE means tighter regulation; **lower** valve travel and
  reversals means gentler actuator duty. Tuning trade-offs usually pit the two
  against each other (tighter control costs more valve movement).
- `resetEventLog()` on the controller before the disturbance so the KPIs cover
  only the window of interest.

### Canonical controls qualification suite

Use `ControlsBenchmarkSuite` when a request needs a deterministic regression
check across NeqSim's canonical level, pressure, cascade, split-range,
anti-surge, and compressor speed/recycle control structures:

```java
import neqsim.process.controllerdevice.ControlsBenchmarkSuite;

ControlsBenchmarkSuite.Report report = ControlsBenchmarkSuite.runCanonicalSuite();
boolean qualified = report.isPassed();
```

Inspect `report.getCases()` for the uniform time/PV/SP/output traces,
`ControllerPerformanceMetrics`, physical acceptance detail, and final errors.
The report also exposes the dedicated `AgentBenchmarkSuite` verdict through
`getAgentBenchmarkReport()`. See
[`docs/benchmarks/controls_benchmark.md`](../../../docs/benchmarks/controls_benchmark.md)
for equations, declared challenges, numerical gates, and current CI reference
values.

The suite qualifies deterministic control execution and KPI reporting against
transparent surrogate plants. It is not a substitute for field tuning, vendor
compressor data, severe-slugging qualification, a safety-instrumented function,
or commissioning validation.

### Anti-Surge Control (dynamic)

`AntiSurgeController` (`neqsim.process.controllerdevice.AntiSurgeController`) is a
purpose-built **reverse-acting PI** controller that reads the compressor
`getDistanceToSurge()` and drives a recycle (spill-back) `ThrottlingValve` open
when the margin falls below the set point, then closes it again on recovery.

```java
import neqsim.process.controllerdevice.AntiSurgeController;

// distance to surge ~ (operating flow / surge flow - 1); only meaningful once a
// compressor chart with an active surge curve exists.
AntiSurgeController asc = new AntiSurgeController("anti-surge", compressor, recycleValve);
asc.setSurgeMarginSetPoint(0.10);   // protect a 10% distance-to-surge margin
asc.setProportionalGain(400.0);     // percent opening per unit margin error
asc.setIntegralTime(20.0);          // s
asc.setOpeningRange(0.0, 100.0);    // valve opening clamp (%) with anti-windup
asc.setActive(true);
recycleValve.addController("anti-surge", asc);
```

Control law each transient step: `error = setPoint - distanceToSurge`,
`integral += Kp/Ti * error * dt`, `opening = clamp(Kp*error + integral)` with
anti-windup; the controller applies the opening directly to the recycle valve.

**Reproducible benchmark.** `AntiSurgeDynamicBenchmark`
(`neqsim.process.util.scenario.AntiSurgeDynamicBenchmark`) drives the real
controller against a transparent first-order gas-path surrogate
`m_{k+1} = m_k - d*dt + a*(u/100)*dt` (m = distance to surge, d = disturbance
rate, a = recycle authority, u = valve opening %). It is deterministic and
always converges, so it is the preferred way to verify or tune the control law:

```java
import neqsim.process.util.scenario.AntiSurgeDynamicBenchmark;

AntiSurgeDynamicBenchmark bench = new AntiSurgeDynamicBenchmark();
bench.setInitialMargin(0.30);
bench.setDisturbanceRate(0.020);   // flow loss erodes the margin (/s)
bench.setRecycleAuthority(0.060);  // fully open recycle restores margin (/s)
bench.setTimeStep(1.0);
bench.setNumberOfSteps(120);
bench.getController().setSurgeMarginSetPoint(0.10);
bench.run(false);                   // open loop  -> surges (margin < 0)
bench.run(true);                    // closed loop -> margin held at set point
boolean safe = bench.isSurgeAvoided();
```

**Critical gotchas when wiring a full dynamic recycle flowsheet:**

- A fixed-factor `Splitter` (`setSplitFactors([0.97, 0.03])`) **pins the recycle
  fraction** in dynamic mode, so the anti-surge valve has no authority over the
  actual recycle flow — the controller can hit 100% with no effect. Let the
  recycle flow be set by the valve (`setCv`/resistance), or use the steady-state
  anti-surge `Calculator` pattern instead.
- Once the operating point crosses left of the surge line, `getDistanceToSurge()`
  **clamps at -1.0 and the steady solver cannot climb back out**; a flowsheet
  driven into deep surge will not self-heal even after the inlet is reopened.
  Apply *gradual/ramped* disturbances and keep the machine off deep surge.
- Aggressive proportional gain can slam the recycle valve to its minimum opening,
  starve a stream, and trigger an SRK flash `NaN`
  (`PhaseSrkEos:molarVolume ... NaN`). Keep gains moderate and the valve off hard
  minimum.
- To demonstrate or tune the control *law* cleanly, prefer
  `AntiSurgeDynamicBenchmark` (or a transparent gas-path surrogate) over a full
  recycle flowsheet that can stick in deep surge.

For coordinated compressor-train studies, use
`CompressorAntiSurgeApplication` (`neqsim.process.equipment.compressor`) as the
supervisory scan layer. Each `StageApplication` can bind directly to real NeqSim
topology objects with `bindTopology(process, compressor, hotRecycleValve,
coldRecycleValve, recycleCooler, suctionMixer, hotRecycle, coldRecycle)`. A scan
then writes hot/cold recycle valve openings and optional compressor speed
runback to the real units, and `runDynamicStep(scanInput, dt)` advances the
bound `ProcessSystem` with `runTransient()`.

Use this application layer when the study needs stage coordination,
startup/shutdown or trip states, hot/cold recycle split, operator diagnostics,
commissioning checks, or speed runback in one executable dynamic model. Keep
`Recycle` blocks algebraic unless they have explicit transient inventory
support; valve, compressor, cooler, mixer, and volume-capable equipment should
carry the dynamic response. The application layer reports
`NOT_CERTIFIED_FOR_PROTECTION` and is for simulation/advisory studies, not a
certified machinery-protection package.

For production-readiness evidence, pass the actual compressor cases and transient response
limits to `CompressorProtectionQualificationCalculation`; it checks map margins,
extrapolation, driver/start-up/rundown, response time, rotor separation, settle-out and
vendor acceptance without certifying the protection system. For piping, pass ordered
`TwoFluidPipe`, water-hammer, or externally governed solver samples to
`TransientPipingQualificationCalculation`. That module checks acoustic resolution and
line-pack balance before pressure, slug, velocity and stress limits, and deliberately does
not treat a quasi-steady time series as distributed-transient evidence. Production mode
requires the controlled context attribute `distributedTransientModel=approved`.

## TwoFluidPipe Phase Appearance and Disappearance

When `TwoFluidPipe.setIncludeMassTransfer(true)` is enabled, flash-driven transfer is phase
resolved. Condensation must use equilibrium hydrocarbon-liquid and aqueous-liquid **mass**
contributions; never use the current cell water cut to identify a phase that is not yet present.
For evaporation, withdraw from the actual oil and water conservative inventories and bound each
withdrawal by `phase mass / relaxation time`. An absent phase must have exactly zero evaporation
source.

Transferred momentum follows donor velocity. During condensation, gas loses mass and momentum at
gas velocity and the receiving oil/water phases gain that momentum. During evaporation, each liquid
loses momentum at its own velocity and gas receives their sum. Validate both invariants:

```text
gas mass source + oil mass source + water mass source = 0
gas momentum source + oil momentum source + water momentum source = 0
```

For a phase-transition regression, cross a real SRK/CPA dew point in both directions without finite
oil or water seeding. Check gas, oil, water, liquid, and total closure with
`TwoFluidMassBalanceReport`; sweep nearby temperatures, refine time step and mesh, repeat the run,
and compare rigorous flash with `FlashTable`. The flash table must retain the oil/aqueous liquid mass
split. Record EOS, mixing rule, composition, absolute pressure, temperature, mass-transfer
relaxation time, and units. The current hydrodynamic state transports bulk phase inventories, not a
full component-composition vector per cell, and does not establish equivalence with any commercial
transient multiphase simulator.

## TwoFluidPipe Coupled Pressure-Momentum Gate

For a liquid-rich pressure outlet that physically permits phase fallback, the coupled path requires
all four options. Keep the nonlinear controls explicit in reproducible studies:

```java
pipe.setEnableInterfacialPressure(true);
pipe.setImplicitInterfacialPressureCoupling(true);
pipe.setEnableCoupledPressureMomentum(true);
pipe.setAllowOutletPhaseBackflow(true);
pipe.setCoupledPressureMomentumMaximumIterations(24); // default
pipe.setCoupledPressureMomentumRelativeVolumeTolerance(1.0e-7); // default
```

The previous budget of 12 stopped the public Tengesdal progress case near a `6e-7` relative
cell-volume residual, above the `1e-7` gate. The current 16-section Test 3 probe completes 50/50
calls of 0.1 s and a 24-section refinement completes 100/100 calls of 0.05 s. Neither rejects a
nonlinear substep, and phase and total discrete mass residuals remain below `1e-9`.

A coupled call that exhausts its adaptive retries throws with accepted/requested elapsed time,
residual/tolerance, iterations/cap, and limiter state. Do not catch that exception and advance the
flowsheet clock. Successful completion still requires the sticky diagnostics to be inspected after
the full window:

```java
if (pipe.isTransientOutletBackflowClamped()
    || pipe.isTransientCoupledPressureMomentumFailureDetected()
    || pipe.isTransientCoupledPressureMomentumCorrectionLimited()
    || pipe.getTransientCoupledPressureMomentumRejectedSubsteps() > 0) {
  throw new IllegalStateException("TwoFluidPipe transient is not qualified");
}
```

These flags reset on the next steady `run()`. A pressure-limiter event is not itself a rejected
substep because the volume residual can converge while the bounded correction is active, but it is
mandatory qualification evidence. Use
`isCoupledPressureMomentumPressureCorrectionLimited()` only for the latest correction; use the
`isTransient...` form for the complete window. The current Tengesdal progress run still activates that flag and
spans -18.55 to 6.88 kg/s liquid outlet versus the stored 0.375 to 4.03 kg/s comparison. It is
therefore numerical-progress evidence only, not sustained-severe-slugging or commercial-parity
evidence. Never tune a public closure to the commercial trace; validate the next boundary-coupling
increment against the public Tengesdal experiment, conservation, nearby points, and mesh/time-step
refinement.

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

For large flowsheets with independent branches, enable
`process.setParallelTransientEnabled(true)` and set the maximum worker count
with `process.setTransientThreadPoolSize(n)`. The per-process worker pool is
created lazily and reused across timesteps; changing the worker count or
disabling the option retires it. Execution follows cached process-graph levels,
so upstream groups complete before downstream equipment is submitted while
independent groups within a level remain parallel. A worker exception propagates to the caller with its runtime type and message,
cancels later queued groups, prevents downstream dependency levels from being
submitted, and skips controller, measurement/alarm/history, timestep-counter,
and calculation-identifier commit phases. The process clock, due-event effects,
and equipment state already mutated by a same-level sibling or earlier unit are
not rolled back; treat that timestep as incomplete. If the caller is interrupted
while waiting, NeqSim restores the interrupt status, cancels queued work without
interrupting equipment already updating state, does not submit downstream
levels, and likewise aborts the remaining timestep phases. Graph ordering does
not define transient recycle convergence or transactional rollback, so keep
parallel execution off for recycle loops and other implicit couplings until
their transient contract is explicitly supported.

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
3. **Liquid-outlet level-controller direction**: use `setReverseActing(false)` so level up drives the outlet valve further open. Reverse acting on this configuration drives the loop the wrong way.
4. **Controller windup**: Large setpoint changes can cause integral windup
5. **Separator dimensions**: Must set `setInternalDiameter()` and `setSeparatorLength()` for meaningful level dynamics. For dynamic simulation, set directly on the separator; for design purposes, configure via `SeparatorMechanicalDesign` (see neqsim-api-patterns skill)
6. **Measurement range**: Set min/max on transmitters to match process range
7. **Enable dynamic (inventory) mode for level loops**: after `process.run()` (steady), call `setCalculateSteadyState(false)` on the separator AND every valve, then `separator.setLiquidLevel(startFraction)`, before `runTransient`. If steady-state mode is left on, the separator liquid level stays pinned at its default (0.5) and the level controller never acts. The valve `Cv` is auto-derived from the steady solve. A **liquid-outlet** level valve is `setReverseActing(false)` (level up -> valve opens); put a pressure controller on the gas-outlet valve so the vessel pressure is held and the level loop is isolated.
8. **Physical-step UUIDs**: never reuse one non-null UUID across multiple outer timesteps. Reuse is reserved for refinement/evaluation of one physical step.

## Pluggable Integrator Strategies

`ProcessSystem` accepts a pluggable `IntegratorStrategy`, but this hook does not
by itself make the complete flowsheet a stiff vector ODE/DAE system. Implementations
live in `neqsim.process.dynamics`:

| Strategy | Class | Notes |
|----------|-------|-------|
| Explicit Euler | `ExplicitEulerIntegrator` | Default; fast, conditionally stable |
| BDF-1 (Implicit Euler) | `BDFIntegrator` | Experimental local strategy. Newton + FD Jacobian; can fall back to explicit Euler when Newton diverges. Inspect `lastStepFellBack()` and do not treat fallback runs as qualified stiff/DAE evidence. |

```java
import neqsim.process.dynamics.BDFIntegrator;
import neqsim.process.dynamics.ExplicitEulerIntegrator;
import neqsim.process.dynamics.IntegratorStrategy;

BDFIntegrator bdf = new BDFIntegrator();
process.setIntegratorStrategy(bdf);
// process.setIntegratorStrategy(new ExplicitEulerIntegrator()); // explicit default
// process.setIntegratorStrategy(null);  // reset to default ExplicitEulerIntegrator
IntegratorStrategy current = process.getIntegratorStrategy();
```

For multi-area plants the strategy is propagated to every child area:
`plant.setIntegratorStrategy(new BDFIntegrator())`.

A professional stiff-solver acceptance path must fail loudly or expose explicit
fallback diagnostics; silent fallback is not an acceptable qualification result.

## Event Scheduling (ESD, IOA, setpoint changes)

Time-stamped events (ESD trips, valve closures, setpoint ramps) are managed by
`EventScheduler` in `neqsim.process.dynamics`. `ProcessSystem.runTransient(dt, id)`
advances the process clock, then fires events with `time <= currentTime` before
equipment runs.

```java
import neqsim.process.dynamics.EventScheduler;

EventScheduler events = new EventScheduler();
events.scheduleEvent(120.0, "ESD trip", new Runnable() {
  public void run() { esdValve.setPercentOpen(0.0); }
});
events.scheduleEvent(300.0, "Setpoint ramp", new Runnable() {
  public void run() { pressureController.setControllerSetPoint(45.0); }
});
process.setEventScheduler(events);

for (int i = 0; i < nSteps; i++) {
  process.runTransient(dt);   // fresh physical-step ID; due events fire automatically
}

int fired = events.getFiredEvents().size();
int pending = events.getPendingEvents().size();
```

For event-aware/adaptive work, inspect event state without mutating it and
checkpoint scheduler bookkeeping explicitly:

```java
EventScheduler.Snapshot eventState = events.snapshot();
double nextEventTime = events.getNextEventTime();
java.util.List<EventScheduler.ScheduledEvent> due = events.getDueEvents(process.getTime());

// If a trial is rejected, restore pending/fired membership.
events.restore(eventState);
```

`restore(...)` restores only scheduler pending/fired membership. It cannot undo
an already executed `Runnable` side effect. A rejected trial must defer external
actions until acceptance or restore every object those actions mutate as part of
the same transaction. This is required before safety/OTS event replay can be
called transactional.

For multi-area plants install the scheduler once on the `ProcessModel`; it is
propagated to every child area, and `plant.runTransient(dt, id)` advances all
areas:

```java
plant.setEventScheduler(events);
plant.runTransient(dt, java.util.UUID.randomUUID());
```

**Note**: `EventScheduler` is declared `transient` on `ProcessSystem` because
event `Runnable` payloads (lambdas, anonymous classes) are usually not
serializable. Re-install the scheduler after deserialising a saved process.
`EventScheduler.Snapshot` itself can be serialized when its event actions are
serializable, which is useful for explicit checkpoint/restart coordination.

## New Measurement Devices (v3.11)

Three new measurement devices in `neqsim.process.measurementdevice` complement
the existing PT/TT/LT/FT family:

| Class | Reads | Unit |
|-------|-------|------|
| `DifferentialPressureTransmitter(name, high, low)` | `pHigh - pLow` across two streams | bar |
| `CompositionAnalyzer(name, stream, component, phase)` | Mole fraction; phase `OVERALL` / `GAS` / `LIQUID` | mole/mole |
| `FlowRatioMeter(name, num, den, basis)` | Flow ratio; basis `MASS` / `MOLE` / `VOLUME` | dimensionless |

```java
import neqsim.process.measurementdevice.DifferentialPressureTransmitter;
import neqsim.process.measurementdevice.CompositionAnalyzer;
import neqsim.process.measurementdevice.FlowRatioMeter;

DifferentialPressureTransmitter dpdt = new DifferentialPressureTransmitter("dPT-1", upstream, downstream);
CompositionAnalyzer ax = new CompositionAnalyzer("AX-1", sweetGas, "methane",
    CompositionAnalyzer.AnalyzerPhase.GAS);
FlowRatioMeter rxn = new FlowRatioMeter("FR-1", recycleStream, feedStream,
    FlowRatioMeter.FlowBasis.MASS);
```
