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

## Choke/Valve Closure Propagation

A common transient scenario is simulating the effect of closing a downstream valve or choke and observing how the pressure/flow disturbance propagates back through the pipeline.

### Example: Downstream Valve Closure

```java
// Setup: Source → Pipeline → Choke Valve → Separator
SystemInterface gas = new SystemSrkEos(298.15, 100.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
gas.setTotalFlowRate(50000, "kg/hr");

Stream source = new Stream("source", gas);
source.run();

// Pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("pipeline", source);
pipeline.setLength(5000);        // 5 km
pipeline.setDiameter(0.2);       // 200 mm
pipeline.setNumberOfIncrements(50);
pipeline.run();

// Downstream choke valve
ThrottlingValve choke = new ThrottlingValve("choke", pipeline.getOutletStream());
choke.setOutletPressure(50.0);   // 50 bara downstream
choke.run();

// Build process
ProcessSystem process = new ProcessSystem();
process.add(source);
process.add(pipeline);
process.add(choke);
process.run();

System.out.println("Initial steady-state:");
System.out.println("  Choke inlet P: " + pipeline.getOutletPressure() + " bara");
System.out.println("  Choke opening: " + choke.getPercentValveOpening() + "%");
System.out.println("  Flow rate: " + source.getFlowRate("kg/hr") + " kg/hr");

// Switch to transient
pipeline.setCalculateSteadyState(false);
choke.setCalculateSteadyState(false);
process.setTimeStep(1.0);

UUID id = UUID.randomUUID();

// Run transient with valve closure event
for (int step = 0; step < 300; step++) {
    double t = step * 1.0;  // seconds
    
    // Gradual valve closure from t=50s to t=100s
    if (t >= 50 && t <= 100) {
        double closureFraction = (t - 50) / 50.0;  // 0 to 1
        double opening = 100.0 - closureFraction * 50.0;  // 100% → 50%
        choke.setPercentValveOpening(opening);
    }
    
    process.runTransient();
    
    // Monitor pressure wave propagation
    if (step % 10 == 0) {
        System.out.printf("t=%3.0fs: P_pipe_out=%.2f bara, Choke=%.0f%%, Flow=%.0f kg/hr%n",
            t, pipeline.getOutletPressure(), 
            choke.getPercentValveOpening(),
            choke.getOutletStream().getFlowRate("kg/hr"));
    }
}
```

### Expected Behavior: Valve Closure

When a downstream valve closes:

1. **Immediate effect (t=0)**: Valve restriction increases, flow through valve decreases
2. **Pressure buildup**: Pressure upstream of valve increases as flow backs up
3. **Wave propagation**: Pressure increase travels upstream through pipeline
4. **New equilibrium**: System reaches new steady-state with lower flow and higher upstream pressure

| Time | Pipeline Outlet | Flow Rate | Notes |
|------|-----------------|-----------|-------|
| 0s | 70 bara | 50,000 kg/hr | Initial steady-state |
| 50s | 70 bara | 50,000 kg/hr | Valve starts closing |
| 75s | 75 bara | 40,000 kg/hr | Pressure building |
| 100s | 82 bara | 30,000 kg/hr | Valve at 50% open |
| 200s | 85 bara | 28,000 kg/hr | New equilibrium |

### Example: Emergency Shutdown (ESD)

Simulate rapid valve closure for ESD scenario:

```java
// Fast valve closure (slam shut in 5 seconds)
for (int step = 0; step < 500; step++) {
    double t = step * 0.1;  // 100 ms time step for fast transient
    
    // ESD triggered at t=10s
    if (t >= 10 && t <= 15) {
        double closureFraction = (t - 10) / 5.0;
        choke.setPercentValveOpening(100.0 * (1 - closureFraction));
    } else if (t > 15) {
        choke.setPercentValveOpening(0.0);  // Fully closed
    }
    
    process.runTransient();
    
    // Log high-frequency data for pressure surge analysis
    System.out.printf("%.1f, %.3f, %.1f%n", 
        t, pipeline.getOutletPressure(), 
        choke.getOutletStream().getFlowRate("kg/hr"));
}
```

### Pressure Surge Considerations

For rapid valve closure (water hammer / pressure surge):

| Closure Time | Pressure Rise | Model Accuracy |
|--------------|---------------|----------------|
| > 2×L/c | Gradual | Good |
| ~ L/c | Moderate surge | Approximate |
| << L/c | Severe surge | **Not supported** |

Where L = pipe length, c = speed of sound (~400 m/s for gas, ~1200 m/s for liquid).

**Note**: The current transient model uses advection-based propagation (fluid velocity), not acoustic waves. For severe water hammer analysis, specialized transient software may be needed.

## Water Hammer Limitations

### What is Water Hammer?

Water hammer (hydraulic shock) occurs when a valve closes rapidly, causing pressure waves to travel at the speed of sound through the fluid. The pressure surge can be calculated using the Joukowsky equation:

$$\Delta P = \rho \cdot c \cdot \Delta v$$

Where:
- $\rho$ = fluid density (kg/m³)
- $c$ = speed of sound in fluid (m/s)
- $\Delta v$ = velocity change (m/s)

### Example Pressure Surge Calculation

For water at 10 m/s suddenly stopped:
- ρ = 1000 kg/m³
- c = 1200 m/s (water)
- Δv = 10 m/s
- **ΔP = 1000 × 1200 × 10 = 12 MPa = 120 bar!**

### Current Model Behavior vs Water Hammer

| Aspect | NeqSim Transient Model | True Water Hammer |
|--------|------------------------|-------------------|
| Wave speed | Fluid velocity (1-20 m/s) | Speed of sound (400-1400 m/s) |
| Pressure peak | Gradual buildup | Sharp spike |
| Wave reflection | Not modeled | Multiple reflections |
| Timing | Minutes to equilibrate | Milliseconds for surge |

### When Current Model is Adequate

The advection-based model is suitable for:

✅ **Slow valve operations** (closure time > 2L/c)
- 1 km water pipe: closure > 1.7 seconds OK
- 10 km gas pipe: closure > 50 seconds OK

✅ **Production rate changes** - Gradual flow adjustments

✅ **Process upsets** - Separator level changes, compressor trips

✅ **Quasi-steady analysis** - New equilibrium after disturbance

### When Water Hammer Analysis is Needed

❌ **Emergency shutdowns** - Fast valve closure (<1 second)

❌ **Pump trips** - Sudden flow stoppage

❌ **Check valve slam** - Reverse flow closure

❌ **Pipe stress analysis** - Peak pressure for mechanical design

### Workaround: Estimate Surge Pressure

You can estimate the water hammer pressure surge separately:

```java
// Calculate theoretical water hammer pressure rise
public static double joukowskyPressureSurge(double density, double soundSpeed, 
                                             double velocityChange) {
    return density * soundSpeed * velocityChange;  // Pa
}

// Example usage
double rho = 800;    // kg/m³ (oil)
double c = 1100;     // m/s (speed of sound in oil)
double dv = 5;       // m/s (velocity before closure)

double surgePressure = joukowskyPressureSurge(rho, c, dv);
System.out.println("Max surge: " + surgePressure/1e5 + " bar");
// Output: Max surge: 44.0 bar

// Add to steady-state operating pressure for peak
double operatingPressure = 50.0;  // bara
double peakPressure = operatingPressure + surgePressure/1e5;
System.out.println("Peak pressure: " + peakPressure + " bara");
// Output: Peak pressure: 94.0 bar
```

### Speed of Sound Estimation

For gases, use NeqSim's thermodynamic properties:

```java
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
gas.init(3);
gas.initPhysicalProperties();

double soundSpeed = gas.getSoundSpeed();  // m/s
System.out.println("Speed of sound: " + soundSpeed + " m/s");
// Typical: 400-450 m/s for natural gas
```

### Recommended Tools for Water Hammer

For detailed water hammer analysis, consider:

1. **Method of Characteristics (MOC)** - Classical numerical method for transient pipe flow
2. **Specialized software**: OLGA, PIPESIM, AFT Impulse, Synergi Pipeline Simulator
3. **CFD** - For complex geometries or detailed surge vessel analysis

### Future Enhancement

A proper water hammer model would require:

1. Solving the full hyperbolic wave equations
2. Method of Characteristics or finite volume scheme
3. Acoustic wave speed (not fluid velocity)
4. Wave reflection at boundaries
5. Vapor cavity modeling for column separation

This is a potential future enhancement for NeqSim.

### Choke Opening (Flow Increase)

The reverse scenario - opening a choke to increase flow:

```java
// Start with choke partially closed
choke.setPercentValveOpening(30.0);
process.run();

// Switch to transient
pipeline.setCalculateSteadyState(false);
process.setTimeStep(1.0);

// Gradually open choke
for (int step = 0; step < 200; step++) {
    if (step >= 20 && step <= 70) {
        double opening = 30.0 + (step - 20) * 1.4;  // 30% → 100%
        choke.setPercentValveOpening(Math.min(opening, 100.0));
    }
    process.runTransient();
}
```

When opening a valve:
1. Flow increases immediately at the valve
2. Pressure drops upstream of valve (flow accelerating)
3. Pressure drop propagates upstream
4. System equilibrates at higher flow, lower upstream pressure

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

## Future: Water Hammer Implementation

Water hammer simulation is now available in NeqSim through the `WaterHammerPipe` class, which uses the Method of Characteristics (MOC) to simulate fast pressure transients. The recommended approach for water hammer analysis is:

1. **Use `WaterHammerPipe` for fast transients** - valve closures, pump trips, ESD events
2. **Leverage NeqSim thermodynamics** - wave speed calculated from EOS
3. **Design for extensibility** - supports reservoir, valve, and closed-end boundary conditions
4. **Validate against Joukowsky equation** - built-in surge pressure calculation

The existing `PipeBeggsAndBrills` advection model remains valuable for slow transients, while `WaterHammerPipe` handles fast acoustic phenomena.

### Quick Example

```java
// Create water hammer pipe
WaterHammerPipe pipe = new WaterHammerPipe("pipe", feed);
pipe.setLength(1000);
pipe.setDiameter(0.2);
pipe.setNumberOfNodes(100);
pipe.setDownstreamBoundary(BoundaryType.VALVE);
pipe.run();

// Transient with valve closure
for (int step = 0; step < 1000; step++) {
    if (step == 100) pipe.setValveOpening(0.0);  // Slam shut
    pipe.runTransient(0.001, id);
}

// Get maximum pressure surge
double maxP = pipe.getMaxPressure("bar");
```

For detailed implementation, see [Water Hammer Implementation Guide](water_hammer_implementation.md).

## See Also

- [Pipeline Pressure Drop Overview](pipeline_pressure_drop.md)
- [Beggs & Brill Correlation](beggs_and_brill_correlation.md)
- [Water Hammer Implementation Guide](water_hammer_implementation.md)
- [Process Simulation Basics](../simulation/process_calculator.md)
