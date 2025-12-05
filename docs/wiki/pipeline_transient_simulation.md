# Pipeline Transient Simulation in NeqSim

## Overview

NeqSim supports dynamic (transient) simulation of pipelines using the `PipeBeggsAndBrills` class. This allows modeling of:

- Flow rate step changes
- Pressure disturbances
- Startup and shutdown scenarios
- Valve opening/closing effects

## Steady-State vs Transient Modes

### Steady-State Mode (Default)

In steady-state mode, calling `run()` calculates the equilibrium pressure profile along the pipe assuming constant inlet conditions:

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipe", feed);
pipe.setLength(1000);
pipe.setDiameter(0.2);
pipe.run();  // Steady-state calculation

// Two calculation modes:
// 1. Forward (default): Given flow rate → calculate outlet pressure
// 2. Reverse: Given outlet pressure → calculate flow rate
pipe.setOutletPressure(45.0);  // Switch to reverse mode
pipe.run();
double requiredFlow = pipe.getInletStream().getFlowRate("kg/hr");
```

### Transient Mode

In transient mode, the pipe remembers its internal state between time steps, allowing simulation of dynamic behavior:

```java
pipe.setCalculateSteadyState(false);  // Enable transient mode
for (int step = 0; step < 100; step++) {
    pipe.runTransient(1.0, id);  // 1 second time step
}
```

## Physics Model

### Governing Equations

The transient model solves simplified forms of the mass and momentum conservation equations using a finite-difference approach:

**Mass Conservation:**
$$\frac{\partial \rho}{\partial t} + \frac{\partial (\rho v)}{\partial x} = 0$$

**Momentum (simplified):**
$$\frac{\partial P}{\partial x} = -\frac{dP}{dx}_{friction} - \rho g \sin\theta$$

### Numerical Method

The implementation uses a **relaxation-based advection scheme**:

1. Pipe is divided into segments (nodes)
2. Properties propagate from upstream to downstream
3. Friction and hydrostatic losses are applied at each segment
4. Relaxation factor based on transit time:

$$\alpha = \min\left(1, \frac{\Delta t}{\tau}\right)$$

Where $\tau = \Delta x / v$ is the segment transit time.

### Wave Propagation

For a property $\phi$ (pressure, temperature, flow):

$$\phi_{i+1}^{n+1} = \phi_{i+1}^{n} + \alpha \cdot (\phi_i^{n+1} - \phi_{i+1}^{n}) - \Delta P_{losses}$$

This gives physically realistic propagation delays.

## Usage

### Basic Transient Setup

```java
// Create and run steady-state first
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 50000, "kg/hr");
gas.setMixingRule(2);

Stream feed = new Stream("feed", gas);
feed.run();

PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipe", feed);
pipeline.setLength(1000);
pipeline.setDiameter(0.2);
pipeline.setPipeWallRoughness(4.6e-5);
pipeline.setNumberOfIncrements(20);
pipeline.run();  // Initialize with steady-state

// Switch to transient mode
pipeline.setCalculateSteadyState(false);

// Run transient simulation
UUID id = UUID.randomUUID();
double dt = 1.0;  // 1 second time step

for (int step = 0; step < 100; step++) {
    pipeline.runTransient(dt, id);
    
    // Monitor outlet
    double outletPressure = pipeline.getOutletPressure();
    double outletFlow = pipeline.getOutletStream().getFlowRate("kg/hr");
}
```

### Applying Disturbances

```java
// After running for some time, apply inlet pressure change
SystemInterface newGas = new SystemSrkEos(298.15, 55.0);  // +5 bar
newGas.addComponent("methane", 50000, "kg/hr");
newGas.setMixingRule(2);

feed.setThermoSystem(newGas);
feed.run();

// Continue transient - disturbance will propagate
for (int step = 0; step < 200; step++) {
    pipeline.runTransient(dt, id);
    // ... monitor response
}
```

### Integration with ProcessSystem

```java
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(pipeline);
process.add(separator);

// Initial steady-state
process.run();

// Configure for transient
valve.setCalculateSteadyState(false);
pipeline.setCalculateSteadyState(false);
process.setTimeStep(1.0);

// Run transient steps
for (int i = 0; i < 500; i++) {
    if (i == 50) {
        valve.setPercentValveOpening(80);  // Change valve at t=50s
    }
    process.runTransient();
}
```

## Time Step Selection

### Guidelines

| Pipe Length | Velocity | Transit Time | Recommended Δt |
|-------------|----------|--------------|----------------|
| 100 m | 10 m/s | 10 s | 0.5-2 s |
| 1 km | 10 m/s | 100 s | 1-5 s |
| 10 km | 10 m/s | 1000 s | 5-20 s |

### Stability Criteria

The time step should satisfy:
$$\Delta t \leq \frac{\Delta x}{v}$$

Where $\Delta x = L / N_{increments}$

For fast transients (valve slam), use smaller time steps:
$$\Delta t \leq \frac{\Delta x}{c}$$

Where $c$ is the speed of sound (~350-450 m/s for natural gas).

## Propagation Timing

### Expected Behavior

Based on validation tests:

| Mechanism | Propagation Speed | Notes |
|-----------|------------------|-------|
| Mass flow | Fluid velocity | ~10-20 m/s (gas) |
| Pressure wave | ~0.4× transit time | Model uses advection |
| Temperature | Fluid velocity | Advective transport |

### Example: 1000m Pipeline

For a 1000m pipe with 12.5 m/s gas velocity:
- Fluid transit time: ~80 seconds
- Pressure detection: ~30 seconds (0.4× transit)
- Full equilibration: ~200 seconds

## Accessing Transient Results

### Profile Data

```java
// Pressure profile along pipe
List<Double> pressures = pipeline.getPressureProfile();

// Temperature profile
List<Double> temperatures = pipeline.getTemperatureProfile();

// Pressure drop per segment
List<Double> dpProfile = pipeline.getPressureDropProfile();

// Get segment-specific values
int segment = 10;
double p = pipeline.getSegmentPressure(segment);
double T = pipeline.getSegmentTemperature(segment);
double holdup = pipeline.getSegmentLiquidHoldup(segment);
```

### Outlet Stream

```java
Stream outlet = pipeline.getOutletStream();
double outP = outlet.getPressure("bara");
double outT = outlet.getTemperature("C");
double outFlow = outlet.getFlowRate("kg/hr");
```

## Physical Effects Included

### 1. Friction Losses
- Calculated each time step using Darcy-Weisbach
- Uses Haaland friction factor
- Accounts for current velocity and density

### 2. Hydrostatic Pressure
- Applied for inclined pipes
- $\Delta P = \rho \cdot g \cdot \Delta h$
- Uses local mixture density

### 3. Mass Conservation
- Mass flow propagates through segments
- Outlet flow responds to inlet changes

### 4. Density Updates
- Density propagates with flow
- Affects velocity and friction calculations

## Limitations

1. **No acoustic effects**: Pressure waves travel at fluid velocity, not speed of sound
2. **No liquid accumulation**: Holdup is quasi-steady
3. **Simplified heat transfer**: Optional, uses constant coefficient
4. **No phase change during transient**: Composition remains constant

## Best Practices

### 1. Initialize Properly
Always run steady-state first to establish baseline:
```java
pipeline.run();  // Steady-state
pipeline.setCalculateSteadyState(false);  // Then switch
```

### 2. Use Sufficient Segments
More segments = better resolution of waves:
```java
pipeline.setNumberOfIncrements(20);  // Minimum
pipeline.setNumberOfIncrements(50);  // Better for long pipes
```

### 3. Monitor Convergence
Check that outlet stabilizes after disturbances:
```java
double prevP = 0;
for (int step = 0; step < 500; step++) {
    pipeline.runTransient(dt, id);
    double p = pipeline.getOutletPressure();
    if (Math.abs(p - prevP) < 0.001) {
        System.out.println("Converged at step " + step);
        break;
    }
    prevP = p;
}
```

### 4. Validate with Steady-State
Transient should converge to same result as steady-state:
```java
double steadyDp = ...; // From steady-state run
double transientDp = ...; // After convergence
assertTrue(Math.abs(transientDp - steadyDp) / steadyDp < 0.15);
```

### 5. Transient Convergence Time

For a step change in inlet conditions, expect:
- **Initial response**: Starts within 1-2 time steps
- **50% of change**: ~0.4× fluid transit time
- **Full equilibration**: 2-3× fluid transit time

Example for 1000m pipe with 12.5 m/s velocity:
- Transit time = 80 seconds
- Pressure change detected at outlet: ~30 seconds
- Fully converged: ~200 seconds

## Troubleshooting

### Problem: Pressure goes negative
**Cause**: Time step too large or flow rate too high
**Solution**: Reduce time step or increase inlet pressure

### Problem: No response at outlet
**Cause**: Not enough transient steps
**Solution**: Run more steps (at least 2× transit time)

### Problem: Oscillations
**Cause**: Time step too small relative to physics
**Solution**: Increase time step or reduce number of segments

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop.md)
- [Beggs & Brill Correlation](beggs_and_brill_correlation.md)
- [Process Simulation Basics](../process_calculator.md)
