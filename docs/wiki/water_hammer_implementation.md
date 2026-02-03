---
title: "Water Hammer Simulation in NeqSim"
description: "NeqSim provides water hammer (hydraulic transient) simulation through the `WaterHammerPipe` class. This model uses the **Method of Characteristics (MOC)** to simulate fast pressure transients caused b..."
---

# Water Hammer Simulation in NeqSim

## Overview

NeqSim provides water hammer (hydraulic transient) simulation through the `WaterHammerPipe` class. This model uses the **Method of Characteristics (MOC)** to simulate fast pressure transients caused by:

- Rapid valve closures (emergency shutdown)
- Pump trips
- Check valve slam
- Sudden flow changes

Unlike the advection-based transient model in `PipeBeggsAndBrills`, `WaterHammerPipe` propagates pressure waves at the **speed of sound**, enabling accurate simulation of pressure surges.

## Quick Start

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
| `setRoughness(double meters)` | Surface roughness |
| `setElevationChange(double meters)` | Outlet - inlet elevation |
| `setNumberOfNodes(int nodes)` | Computational grid size |

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
| `setValveOpening(double fraction)` | Valve opening 0-1 |
| `getValveOpening()` | Current valve opening |

### Simulation Control

| Method | Description |
|--------|-------------|
| `run(UUID id)` | Initialize steady state |
| `runTransient(double dt, UUID id)` | Run one time step |
| `getMaxStableTimeStep()` | Get Courant-limited time step |
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
| `getMinPressureEnvelope()` | Min pressure at each node |
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
- **WaterHammerPipe**: ESD events, pump trips, check valve slam, pipe stress analysis

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

- [Pipeline Transient Simulation](pipeline_transient_simulation) - Slow transients with PipeBeggsAndBrills
- [Pipeline Pressure Drop](pipeline_pressure_drop) - Steady-state pressure drop
- [Pipeline Index](pipeline_index) - All pipeline documentation
