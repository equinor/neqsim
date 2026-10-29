---
title: "Water Hammer Simulation in NeqSim"
description: "Guide to NeqSim water hammer and liquid hammer screening with WaterHammerPipe, WaterHammerStudy, process JSON, automation variables, STID route data, tagreader event windows, and MCP runWaterHammer."
---

## Safety Shutdown kernel verification

The single-line acoustic kernel uses characteristic-foot interpolation at the actual internal
time interval. An outer `runTransient(dt, id)` call is split into equal internal intervals no
larger than `courantNumber * dx / a`; the entire requested duration is advanced. Courant number
must be finite and in `(0, 1]`. Smaller intervals introduce interpolation diffusion and require
mesh/time refinement; they no longer make waves propagate one full grid cell per call.

Geometry, grid, roughness, material modulus and wave-speed overrides are static configuration.
Changing any of these after initialization requires an explicit `run()` before advancing again;
otherwise `runTransient` rejects before changing the clock, arrays or histories. `run()` deliberately
starts a new initialized case and resets the hydraulic histories. Repeating an unchanged setter
does not invalidate the state. Valve opening, receiver pressure and a valid Courant number remain
live controls. A positive finite wave-speed override is retained across explicit reinitialization;
`setWaveSpeed(-1)` selects EOS/elastic calculation, recalculated for the current material/geometry.
Zero, other negative, nonfinite and NaN speed overrides reject immediately.

The initialized open-valve flow is calibrated against an independent receiver pressure.
`setValveBackPressure(double)` and `getValveBackPressure()` use **bara**, with a default of
**1 bara**. The study JSON accepts `valveBackPressure_bara` at the top level or in `fieldData`.
The hydraulic coefficient is internal, in cubic metres per second per square root metre of
head; it is **not** an IEC/ISA Cv or Kv. Valve opening scales this coefficient linearly.
The boundary solves the characteristic together with:

$$Q^2+B(C\tau)^2Q-(C\tau)^2(C_+-H_r)=0$$

Here $Q$ is volume flow in m³/s, $B=a/(gA)$ is characteristic impedance, $C$ is the
calibrated hydraulic coefficient, $\tau$ is opening fraction, $C_+$ is incoming head and
$H_r$ is receiver pressure head. The positive root is evaluated without subtractive cancellation.
An open boundary requiring reverse flow rejects explicitly; a closed valve remains closed even
when receiver pressure is higher. This is not a dynamic non-return-valve disc model.

Initialization requires positive finite geometry/material/fluid properties, one inlet phase,
an initially open forward-flow valve above its receiver pressure (when that boundary is used),
and an available EOS sound speed. There is no silent water-property fallback. Density,
viscosity, acoustic speed and Darcy friction coefficient are frozen at initialization. Pressure
head is evolved with the linear terrain gravity source. This is a small-disturbance acoustic
model, including when the single phase is gas, not a general large-decompression gas model.

`WaterHammerKernelTest` is the executable public synthetic verification example. It checks
unchanged-valve and inclined equilibrium, physical pre-arrival timing, Joukowsky first impulse
over mesh/Courant refinements, receiver pressure, complete-duration subcycling, invalid intervals
and deterministic Java restart. `WaterHammerPipeTest` retains adjacent API/regression checks.
These are analytical/numerical checks, not experimental or plant qualification. Existing results
can change because earlier sub-Courant timing and valve-boundary behavior were incorrect.

The study wrapper advances the exact requested duration, including a final partial interval.
It rejects non-valve events instead of silently skipping them. Pump trips, disc slam, network
junctions, phase-change/cavity collapse and structural response remain separate qualification
stages of the Safety Shutdown campaign. A single-line pressure envelope does not establish piping
stress, support impact, SIL or proof-test coverage. The inlet phase check is not a transient
cavitation detector; inspect the pressure envelope against the fluid's phase limits separately.

## Overview

NeqSim provides water hammer and liquid hammer screening through the `WaterHammerPipe`
class and the higher-level `WaterHammerStudy` workflow facade. The pipe model uses
the **Method of Characteristics (MOC)** to simulate fast pressure transients caused by:

- Rapid valve closures (emergency shutdown)
- Pump trips
- Check valve slam
- Sudden flow changes

Unlike the advection-based transient model in `PipeBeggsAndBrills`, `WaterHammerPipe`
propagates pressure waves at the **speed of sound**, enabling fast screening of
pressure surges. The workflow can start from a NeqSim process model, STID/P&ID/E3D
route data, line lists, valve schedules, and tagreader field-data snapshots.

Use this as a rapid engineering screening tool. Detailed surge, pipe-stress,
support-load, pump-curve, vapor-cavity, and branch-network assessments still need
specialist review when the screening margin is small or the route is complex.

## Full STID, Tagreader, and MCP Workflow

The full workflow is intended for fast industry studies where an engineer has a
NeqSim process model plus documentation and plant data:

1. Extract the liquid line route from STID/P&ID/E3D/line-list evidence: length,
     internal diameter, wall thickness, roughness or piping class, elevation change,
     fittings, valve tags, and design pressure.
2. Extract the initiating event: ESD valve closure time, check-valve slam, pump trip,
     controller output ramp, or operator action.
3. Read tagreader event-window values when available: inlet pressure, temperature,
     flow rate, valve opening, downstream pressure, pump speed, and pump status.
4. Run `WaterHammerStudy.run(...)` or MCP `runWaterHammer` with `pipe` or
     `stidRoute.segments`, `fieldData`, and `eventSchedule`.
5. Review `maxPressure_bara`, `minPressure_bara`, `pressureSurge_bar`,
     `joukowskySurgeEstimate_bar`, `waveRoundTripTime_s`, `maxStableTimeStep_s`,
     and any design-pressure validation flags.
6. Escalate to a detailed surge study if the peak pressure approaches MAOP/design
     pressure, the route contains important branches, or support loads and stress
     checks are required.

### MCP `runWaterHammer` Input

MCP clients can call `runWaterHammer` directly. `runPipeline` also dispatches to
the same runner when `mode`, `analysis`, or `studyType` is set to `waterHammer`,
`liquidHammer`, or `hydraulicTransient`.

```json
{
    "studyName": "Safety Shutdown valve closure screening",
    "model": "SRK",
    "temperature_C": 20.0,
    "pressure_bara": 45.0,
    "components": {"water": 1.0},
    "flowRate": {"value": 120000.0, "unit": "kg/hr"},
    "designPressure_bara": 95.0,
    "pipe": {
        "length_m": 1200.0,
        "diameter_m": 0.2032,
        "wallThickness_m": 0.0127,
        "roughness_m": 4.6e-5,
        "elevation_m": 8.0,
        "numberOfNodes": 80
    },
    "fieldData": {
        "inletPressure_bara": 46.0,
        "inletTemperature_C": 19.0,
        "flowRate_kg_hr": 118000.0,
        "valveOpening": 1.0
    },
    "eventSchedule": [
        {
            "type": "VALVE_CLOSURE",
            "startTime_s": 0.10,
            "duration_s": 0.15,
            "startOpening": 1.0,
            "endOpening": 0.0
        }
    ],
    "simulationTime_s": 4.0,
    "sourceReferences": [
        "generic STID line-list row",
        "generic tagreader event window"
    ]
}
```

For documentation-led studies, `stidRoute.segments` can replace the single `pipe`
object. The facade aggregates a serial route into an equivalent line and adds
minor-loss equivalent length when fittings or valves are supplied with K values.

### Process JSON Integration

`WaterHammerPipe` is registered in the process equipment factory and JSON builder.
Accepted type aliases include `WaterHammerPipe`, `waterHammer`, `liquidHammer`, and
`hydraulicTransientPipe`. Boundary fields can be provided as strings.

```json
{
    "name": "water hammer case",
    "equipment": [
        {
            "name": "Hammer Line",
            "type": "WaterHammerPipe",
            "inlet": "Feed",
            "properties": {
                "length": 1000.0,
                "diameter": 0.20,
                "wallThickness": 0.012,
                "pipeWallRoughness": 4.6e-5,
                "numberOfNodes": 80,
                "downstreamBoundary": "VALVE",
                "valveOpening": 1.0
            }
        }
    ]
}
```

### Automation and Operational Scenarios

Water-hammer variables are exposed through `ProcessAutomation` for process studies
and MCP automation tools:

| Address | Type | Meaning |
|---------|------|---------|
| `Hammer Line.valveOpening` | INPUT | Valve opening fraction from 0 to 1 |
| `Hammer Line.valveOpeningPercent` | INPUT | Valve opening in percent |
| `Hammer Line.waveSpeed` | INPUT | Override wave speed in m/s |
| `Hammer Line.numberOfNodes` | INPUT | MOC grid resolution |
| `Hammer Line.courantNumber` | INPUT | Courant stability factor |
| `Hammer Line.maxStableTimeStep` | OUTPUT | Courant-limited timestep in seconds |
| `Hammer Line.waveRoundTripTime` | OUTPUT | Acoustic round-trip time in seconds |
| `Hammer Line.maxPressure` | OUTPUT | Maximum pressure envelope value |
| `Hammer Line.minPressure` | OUTPUT | Minimum pressure envelope value |

`OperationalScenarioRunner` applies `SET_VALVE_OPENING` actions directly to a
`WaterHammerPipe`, so P&ID-driven operational scenarios can drive the initiating
valve position before the water-hammer runner performs the high-speed transient.

## Quick Start

### Safety Shutdown with prescribed actual valve travel

`AcousticValveBoundary` adds an opt-in physical liquid-capacity boundary. The legacy
`setValveOpening` mode still infers a square-root-head coefficient from steady flow;
that coefficient is **not** vendor Cv or Kv. In physical mode, supply absolute capacity
versus position and actual travel versus time. Commands and measured actual travel are
different inputs: this boundary replays actual travel after a command time and delay;
it does not integrate an actuator or execute safety logic.

The capacity table must span opening fractions 0 to 1, with finite, nonnegative,
nondecreasing coefficients and a positive fully open coefficient. The existing strict
`TabulatedValveCharacteristic` supplies monotone cubic capacity interpolation. No
noisy-data cleaning, isotonic fitting or hidden calibration is performed. Travel is
piecewise linear, may include reopening, begins at elapsed time zero, and holds the
first/last position outside its table. Zero capacity closes the boundary exactly;
positive capacity at zero position explicitly represents leakage.

Travel lookup and event localization use the same representable absolute knots:
`(commandTime_s + delay_s) + travelTime_s[i]`. At an exact knot the tabulated
position is returned exactly, including zero at full closure. Between knots the
position is interpolated on this absolute time axis; no epsilon closes a valve
early. This avoids residual capacity caused by subtracting a nonzero start time
before looking up a relative knot. Serialized inputs and field layout are unchanged.

For metric Kv, the independently checkable non-choked liquid relation is

$$Q=\frac{K_v(x)}{3600}\sqrt{\frac{\Delta p_{bar}}{\rho/1000}}$$

Here $Q$ is actual volume flow in m³/s, $x$ actual opening fraction, $\rho$ frozen
liquid density in kg/m³, and $\Delta p_{bar}$ the valve inlet minus external receiver
pressure in bar. Kv is reference-water m³/h capacity at one bar drop. `CV_US` uses
the existing NeqSim convention $C_{v,US}=1.156 K_v$; Imperial Cv is unsupported.
The [manufacturer capacity definitions](https://www.spiraxsarco.com/learn-about-steam/control-hardware-electric-pneumatic-actuation/control-valve-capacity)
give a more precise US conversion of 1.156099; retaining the library convention
introduces about 0.0086% conversion rounding. This is a simplified liquid law, not
an IEC 60534 sizing implementation, installed recovery-factor model or cavitation check.

An initialized feed/pipe and independent receiver pressure must agree with declared
initial capacity within $10^{-9}+10^{-6}|Q|$ m³/s. Inconsistent inputs reject; the
solver does not adjust the feed or fit a vendor coefficient. Supply a physically
consistent steady state first. Physical mode requires a single liquid phase and
downstream `VALVE`. Manual opening control rejects until the boundary is removed.
Changing/removing the prescribed boundary requires explicit `run()` reinitialization,
which resets the local clock and histories. `reset()` retains the immutable profile.
When returning to legacy mode, set the desired positive initial opening before `run()`.

Every acoustic internal endpoint evaluates actual position and effective Kv. Substeps
split at delay/travel knots, including a final partial interval. The boundary owns no
clock or mutable actuator state; Java serialization/restart reuses the pipe clock.
It does not provide model-wide transaction rollback or physical UUID idempotency.

The [public synthetic input and executed evidence](../../src/test/resources/neqsim/process/pipeline/safety-shutdown-valve/README.md)
are verified by `AcousticValveBoundaryTest.studyInputAndOutput` and the full boundary
qualification suite. `WaterHammerStudy.run` accepts a `valveBoundary` object with:

| Field | Units/meaning |
| --- | --- |
| `openingFraction`, `capacity` | matching capacity-position arrays; opening spans [0,1] |
| `capacityUnit` | required `KV` or `CV_US` |
| `travelTime_s`, `actualOpeningFraction` | matching actual-travel arrays; first time zero |
| `commandTime_s`, `delay_s` | nonnegative physical seconds, default zero |

Do not combine a profile with a nonempty `eventSchedule` or closure-time shortcuts.
The facade returns synchronized `time_s`, `valveOpening`, `effectiveValveKv` and
`outletFlow_m3_s`, plus the full boundary input and `frozenDensity_kg_m3` in
`inputSummary` so the liquid law can be checked independently. Output sampling can miss an internal
knot; the pipe's internal time/pressure histories retain it. There is no additional
scheduler, control loop or plant pressure-flow coupling in this interface.

**Known conservation failure:** with changing boundary flow below unit Courant,
the existing nodal MOC stored volume does not exactly match trapezoidally integrated
physical boundary flow. In the zero-friction discrete limit, the per-step defect is
$(\Delta x/a-\Delta t)\Delta(Q_{in}-Q_{out})/2$. This is numerical storage error,
not real fluid inventory and not an allowed mass correction. The public evidence
retains the physical $10^{-9}$ m³ target and records **FAIL**, separately from the
passing discrete-identity regression. The earlier instantaneous-closure balance test
does not qualify arbitrary travel. A conservative kernel increment is required;
this prescribed-boundary mode is experimental and does not qualify coupled inventory
or Safety Shutdown loads. `status=success` means the study executed.

**Evidence boundary:** analytical capacity/passivity, equilibrium, fast Joukowsky,
knot timing, refinement characterization and local restart/replay are software
verification. Pump torque/coastdown, check-valve disc/contact slam, acoustic
junctions, vapor-cavity/column separation, large thermodynamic excursions and vector
support/stress loads remain unqualified. Asset validation and safety approval are
NOT_EVALUATED. No SIL, SRS or proof-test approval follows from this calculation.

```java
import neqsim.process.equipment.pipeline.WaterHammerPipe;
import neqsim.process.equipment.pipeline.WaterHammerPipe.BoundaryType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid
SystemInterface water = new SystemSrkEos(298.15, 10.0);
water.addComponent("water", 1.0);
water.setMixingRule("classic");
water.setTotalFlowRate(100.0, "kg/hr");

Stream feed = new Stream("feed", water);
feed.run();

// Create water hammer pipe
WaterHammerPipe pipe = new WaterHammerPipe("pipeline", feed);
pipe.setLength(1000);              // 1 km
pipe.setDiameter(0.2);             // 200 mm
pipe.setNumberOfNodes(100);        // Grid resolution
pipe.setDownstreamBoundary(BoundaryType.VALVE);
pipe.run();                        // Initialize steady state

// Simulate valve closure
UUID id = UUID.randomUUID();
double dt = pipe.getMaxStableTimeStep();

for (int step = 0; step < 1000; step++) {
    double t = step * dt;

    // Close valve from t=0.1s to t=0.2s
    if (t >= 0.1 && t <= 0.2) {
        double tau = (t - 0.1) / 0.1;
        pipe.setValveOpening(1.0 - tau);  // 100% → 0%
    }

    pipe.runTransient(dt, id);
}

// Get maximum pressure surge
double maxPressure = pipe.getMaxPressure("bar");
System.out.println("Max surge pressure: " + maxPressure + " bar");
```

## Physics Model

### Method of Characteristics (MOC)

The MOC transforms the hyperbolic partial differential equations for 1D transient pipe flow into ordinary differential equations along characteristic lines.

**Governing Equations:**

Continuity:
$$\frac{\partial H}{\partial t} + \frac{c^2}{gA}\frac{\partial Q}{\partial x} = 0$$

Momentum:
$$\frac{\partial Q}{\partial t} + gA\frac{\partial H}{\partial x} + \frac{f}{2DA}Q|Q| = 0$$

**Characteristic Lines:**
- C⁺ line: dx/dt = +c (forward wave)
- C⁻ line: dx/dt = -c (backward wave)

**Compatibility Equations:**

Along C⁺: $H_P - H_A + B(Q_P - Q_A) + R \cdot Q_A|Q_A| = 0$

Along C⁻: $H_P - H_B - B(Q_P - Q_B) - R \cdot Q_B|Q_B| = 0$

Where:
- H = piezometric head (m)
- Q = volumetric flow rate (m³/s)
- c = wave speed (m/s)
- B = c/(gA) — characteristic impedance
- R = fΔx/(2gDA²) — friction term

### Wave Speed Calculation

The wave speed includes pipe elasticity using the **Korteweg-Joukowsky formula**:

$$c = \frac{c_{fluid}}{\sqrt{1 + \frac{K \cdot D}{E \cdot e}}}$$

Where:
- $c_{fluid}$ = speed of sound in fluid (from NeqSim thermodynamics)
- K = fluid bulk modulus = ρc²
- D = pipe diameter
- E = pipe elastic modulus (default: 200 GPa for steel)
- e = pipe wall thickness

```java
// Wave speed is automatically calculated, but can be overridden
pipe.setPipeElasticModulus(200e9);  // Steel
pipe.setWallThickness(0.01);        // 10 mm
double waveSpeed = pipe.getWaveSpeed();  // After run()
```

### Joukowsky Pressure Surge

The theoretical pressure surge for instantaneous velocity change:

$$\Delta P = \rho \cdot c \cdot \Delta v$$

```java
// Calculate theoretical surge
double surgePa = pipe.calcJoukowskyPressureSurge(velocityChange);
double surgeBar = pipe.calcJoukowskyPressureSurge(velocityChange, "bar");
```

## Boundary Conditions

### Available Types

| Type | Description | Use Case |
|------|-------------|----------|
| `RESERVOIR` | Constant pressure head | Upstream tank/reservoir |
| `VALVE` | Variable opening (0-1) | Downstream control valve |
| `CLOSED_END` | No flow (Q=0) | Dead end, closed valve |
| `CONSTANT_FLOW` | Fixed flow rate | Pump at constant speed |

### Setting Boundary Conditions

```java
// Upstream: constant pressure reservoir
pipe.setUpstreamBoundary(BoundaryType.RESERVOIR);

// Downstream: valve that can be opened/closed
pipe.setDownstreamBoundary(BoundaryType.VALVE);
pipe.setValveOpening(1.0);  // Initially fully open

// During simulation, close the valve
pipe.setValveOpening(0.5);  // 50% open
pipe.setValveOpening(0.0);  // Fully closed
```

## Time Step and Stability

### Courant Condition

For numerical stability, the time step must satisfy:

$$\Delta t \leq \frac{\Delta x}{c}$$

Where Δx = length / (numberOfNodes - 1).

```java
// Get maximum stable time step
double maxDt = pipe.getMaxStableTimeStep();

// Use smaller time step for safety
double dt = maxDt * 0.5;
```

### Wave Round-Trip Time

The time for a pressure wave to travel the pipe length and back:

$$T_{round-trip} = \frac{2L}{c}$$

```java
double roundTrip = pipe.getWaveRoundTripTime();
// For 1 km pipe with c=1000 m/s: roundTrip = 2 seconds
```

## Output and Results

### Pressure Profile

```java
// Pressure along pipe (Pa)
double[] pressures = pipe.getPressureProfile();

// Pressure in bar
double[] pressuresBar = pipe.getPressureProfile("bar");
```

### Velocity and Flow Profiles

```java
double[] velocities = pipe.getVelocityProfile();  // m/s
double[] flows = pipe.getFlowProfile();           // m³/s
double[] heads = pipe.getHeadProfile();           // m
```

### Pressure Envelopes

Track maximum and minimum pressures during simulation:

```java
double[] maxEnvelope = pipe.getMaxPressureEnvelope();
double[] minEnvelope = pipe.getMinPressureEnvelope();

double overallMax = pipe.getMaxPressure("bar");
double overallMin = pipe.getMinPressure("bar");

// Reset envelopes (e.g., after reaching steady state)
pipe.resetEnvelopes();
```

### Time History

```java
List<Double> pressureHistory = pipe.getPressureHistory();  // At outlet
List<Double> timeHistory = pipe.getTimeHistory();
double currentTime = pipe.getCurrentTime();
```

## Example: Emergency Shutdown

```java
// Setup: 5 km oil pipeline
SystemInterface oil = new SystemSrkEos(298.15, 50.0);
oil.addComponent("nC10", 1.0);
oil.setMixingRule("classic");
oil.setTotalFlowRate(500000, "kg/hr");

Stream feed = new Stream("feed", oil);
feed.run();

WaterHammerPipe pipeline = new WaterHammerPipe("export pipeline", feed);
pipeline.setLength(5, "km");
pipeline.setDiameter(300, "mm");
pipeline.setNumberOfNodes(200);
pipeline.setDownstreamBoundary(BoundaryType.VALVE);
pipeline.run();

System.out.println("Wave speed: " + pipeline.getWaveSpeed() + " m/s");
System.out.println("Round-trip time: " + pipeline.getWaveRoundTripTime() + " s");

// Initial conditions
double initialPressure = pipeline.getMaxPressure("bar");
double velocity = pipeline.getVelocityProfile()[0];

// Simulate ESD - valve closes in 5 seconds
UUID id = UUID.randomUUID();
double dt = 0.01;  // 10 ms time step
double closureTime = 5.0;

for (double t = 0; t < 30; t += dt) {
    // Linear valve closure from t=0 to t=closureTime
    if (t <= closureTime) {
        pipeline.setValveOpening(1.0 - t / closureTime);
    }

    pipeline.runTransient(dt, id);

    if (t % 1.0 < dt) {
        System.out.printf("t=%.1fs: P_max=%.1f bar, valve=%.0f%%%n",
            t, pipeline.getMaxPressure("bar"), pipeline.getValveOpening() * 100);
    }
}

// Results
double maxSurge = pipeline.getMaxPressure("bar");
double minPressure = pipeline.getMinPressure("bar");

System.out.println("Initial pressure: " + initialPressure + " bar");
System.out.println("Maximum surge: " + maxSurge + " bar");
System.out.println("Minimum pressure: " + minPressure + " bar");
System.out.println("Surge increase: " + (maxSurge - initialPressure) + " bar");

// Compare with Joukowsky theoretical value
double joukowskySurge = pipeline.calcJoukowskyPressureSurge(velocity, "bar");
System.out.println("Joukowsky theoretical: " + joukowskySurge + " bar");
```

## Example: Gas Pipeline

```java
// Natural gas pipeline
SystemInterface gas = new SystemSrkEos(298.15, 70.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");
gas.setTotalFlowRate(1000000, "Sm3/day");

Stream gasFeed = new Stream("gas feed", gas);
gasFeed.run();

WaterHammerPipe gasPipe = new WaterHammerPipe("gas pipeline", gasFeed);
gasPipe.setLength(100, "km");
gasPipe.setDiameter(0.5);           // 500 mm
gasPipe.setNumberOfNodes(500);
gasPipe.setDownstreamBoundary(BoundaryType.VALVE);
gasPipe.run();

// Gas has lower wave speed (~400 m/s) and lower density
// So pressure surges are typically smaller than for liquids
System.out.println("Gas wave speed: " + gasPipe.getWaveSpeed() + " m/s");
```

## API Reference

### Constructor

```java
WaterHammerPipe(String name)
WaterHammerPipe(String name, StreamInterface inStream)
```

### Geometry Methods

| Method | Description |
|--------|-------------|
| `setLength(double meters)` | Pipe length in meters |
| `setLength(double value, String unit)` | Length with unit ("m", "km", "ft") |
| `setDiameter(double meters)` | Inside diameter in meters |
| `setDiameter(double value, String unit)` | Diameter with unit ("m", "mm", "in") |
| `setWallThickness(double meters)` | Pipe wall thickness |
| `getWallThickness()` | Pipe wall thickness in meters |
| `setRoughness(double meters)` | Surface roughness |
| `setPipeWallRoughness(double meters)` | JSON-friendly roughness setter |
| `getPipeWallRoughness()` | Surface roughness in meters |
| `setElevationChange(double meters)` | Outlet - inlet elevation |
| `setElevation(double meters)` | JSON-friendly elevation setter |
| `getElevation()` | Elevation change in meters |
| `setNumberOfNodes(int nodes)` | Computational grid size |
| `setNumberOfIncrements(int increments)` | Compatibility setter mapping increments to nodes |
| `getNumberOfIncrements()` | Number of computational increments |

### Material Properties

| Method | Description |
|--------|-------------|
| `setPipeElasticModulus(double Pa)` | Pipe material modulus (default: 200 GPa) |
| `setWaveSpeed(double m_per_s)` | Override calculated wave speed |

### Boundary Conditions

| Method | Description |
|--------|-------------|
| `setUpstreamBoundary(BoundaryType)` | Set upstream BC type |
| `setDownstreamBoundary(BoundaryType)` | Set downstream BC type |
| `setUpstreamBoundary(String)` | JSON-friendly upstream boundary setter |
| `setDownstreamBoundary(String)` | JSON-friendly downstream boundary setter |
| `getUpstreamBoundaryName()` | Upstream boundary as a string |
| `getDownstreamBoundaryName()` | Downstream boundary as a string |
| `setValveOpening(double fraction)` | Valve opening 0-1 |
| `setValveOpeningPercent(double percent)` | Valve opening in percent |
| `getValveOpening()` | Current valve opening |
| `getValveOpeningPercent()` | Current valve opening in percent |

### Simulation Control

| Method | Description |
|--------|-------------|
| `run(UUID id)` | Initialize steady state |
| `runTransient(double dt, UUID id)` | Run one time step |
| `getMaxStableTimeStep()` | Get Courant-limited time step |
| `getCourantNumber()` | Current Courant number |
| `setCourantNumber(double cn)` | Set Courant number (default: 1.0) |
| `reset()` | Reset to initial state |
| `resetEnvelopes()` | Reset min/max tracking |

### Results

| Method | Description |
|--------|-------------|
| `getPressureProfile()` | Pressure array (Pa) |
| `getPressureProfile(String unit)` | Pressure in unit ("bar", "psi") |
| `getVelocityProfile()` | Velocity array (m/s) |
| `getFlowProfile()` | Flow rate array (m³/s) |
| `getHeadProfile()` | Piezometric head (m) |
| `getMaxPressureEnvelope()` | Max pressure at each node |
| `getMaxPressureEnvelope(String unit)` | Max pressure envelope in unit |
| `getMinPressureEnvelope()` | Min pressure at each node |
| `getMinPressureEnvelope(String unit)` | Min pressure envelope in unit |
| `getMaxPressure(String unit)` | Overall maximum pressure |
| `getMinPressure(String unit)` | Overall minimum pressure |
| `getPressureHistory()` | Outlet pressure vs time |
| `getTimeHistory()` | Time values |
| `getCurrentTime()` | Current simulation time |

### Calculations

| Method | Description |
|--------|-------------|
| `calcJoukowskyPressureSurge(double dv)` | Theoretical surge (Pa) |
| `calcJoukowskyPressureSurge(double dv, String unit)` | Surge in unit |
| `calcEffectiveWaveSpeed()` | Korteweg wave speed |
| `getWaveSpeed()` | Current wave speed (m/s) |
| `getWaveRoundTripTime()` | 2L/c in seconds |

## Comparison with PipeBeggsAndBrills

| Aspect | PipeBeggsAndBrills | WaterHammerPipe |
|--------|-------------------|-----------------|
| Wave speed | Fluid velocity (~10-20 m/s) | Speed of sound (400-1500 m/s) |
| Time scale | Minutes to hours | Milliseconds to seconds |
| Use case | Slow transients, process upsets | Fast transients, valve slam |
| Physics | Advection | Acoustic waves |
| Two-phase | Full correlation | Single-phase (liquid or gas) |
| Heat transfer | Included | Not included |

**When to use which:**

- **PipeBeggsAndBrills**: Production rate changes, separator upsets, slow valve operations
- **WaterHammerPipe**: bounded single-line Safety Shutdown valve-closure acoustics; pump/disc slam and coupled-inventory qualification remain open

## Limitations

Current implementation limitations:

1. **Single-phase only** - liquid or gas, no two-phase
2. **No heat transfer** - isothermal assumption
3. **No column separation** - vapor cavity modeling not included
4. **Simple friction** - quasi-steady friction model
5. **No pipe networks** - single pipe only

## References

1. Wylie, E.B. & Streeter, V.L. (1993). *Fluid Transients in Systems*. Prentice Hall.
2. Chaudhry, M.H. (2014). *Applied Hydraulic Transients*. Springer.
3. Ghidaoui, M.S. et al. (2005). "A Review of Water Hammer Theory and Practice". *Applied Mechanics Reviews*.

## See Also

- [Pipeline Transient Simulation](pipeline_transient_simulation.md) - Slow transients with PipeBeggsAndBrills
- [Pipeline Pressure Drop](pipeline_pressure_drop.md) - Steady-state pressure drop
- [Pipeline Index](pipeline_index.md) - All pipeline documentation
- [Piping Route Builder](../process/piping_route_builder.md) - STID/E3D route extraction for steady hydraulics and surge screening
- [MCP Server Guide](../integration/mcp_server_guide.md) - MCP tool usage including `runWaterHammer`
