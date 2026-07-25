---
title: "Transient Multiphase Pipe Model"
description: "Configure and validate NeqSim's drift-flux transient pipeline model, including pressure boundaries, terrain effects, liquid accumulation, and profile results."
---

## Overview

The `TransientPipe` class provides a 1D transient multiphase (gas-liquid) flow simulator for pipelines. It uses the drift-flux formulation combined with mechanistic flow regime detection to model complex phenomena like terrain-induced slugging, liquid accumulation at low points, and transient pressure wave propagation.

The model supports:
- **Two-phase flow**: Gas + oil or gas + water
- **Three-phase flow**: Gas + oil + water with volume-weighted liquid property averaging

## Key Features

- **Drift-Flux Model**: Zuber-Findlay formulation with regime-dependent correlations
- **Flow Regime Detection**: Taitel-Dukler and Barnea mechanistic models
- **Terrain Effects**: Liquid accumulation at low points, terrain-induced slugging
- **Lagrangian Slug Tracking**: Individual slug units with growth/decay dynamics
- **Adaptive Time Stepping**: CFL-based stability control
- **NeqSim Integration**: Periodic thermodynamic flash updates using SRK, PR, or CPA equations of state

## Physical Models

### Drift-Flux Model

The core model uses the Zuber-Findlay drift-flux formulation:

$$
v_G = C_0 v_m + v_d
$$

Where:
- `v_G` = gas velocity (m/s)
- `C₀` = distribution coefficient (typically 1.0-1.2)
- `v_m` = mixture velocity (m/s)
- `v_d` = drift velocity (m/s)

The distribution coefficient `C₀` and drift velocity `v_d` depend on the flow regime:

| Flow Regime | C₀ | Drift Velocity Correlation |
|-------------|-----|---------------------------|
| Bubble | 1.2 | Harmathy (1960) |
| Slug | 1.05-1.2 (Fr dependent) | Bendiksen (1984) |
| Annular | 1.0 | Film drainage model |
| Stratified | Calculated from momentum balance | ~0 |

### Three-Phase Flow Handling

When both oil and aqueous (water) liquid phases are present, the model calculates
volume-weighted average liquid properties:

$$
\begin{aligned}
\rho_{L,\mathrm{avg}} &= \frac{V_{\mathrm{oil}}}{V_{\mathrm{total}}}\rho_{\mathrm{oil}}
+ \frac{V_{\mathrm{water}}}{V_{\mathrm{total}}}\rho_{\mathrm{water}}, \\
\mu_{L,\mathrm{avg}} &= \frac{V_{\mathrm{oil}}}{V_{\mathrm{total}}}\mu_{\mathrm{oil}}
+ \frac{V_{\mathrm{water}}}{V_{\mathrm{total}}}\mu_{\mathrm{water}}, \\
H_{L,\mathrm{avg}} &= \frac{V_{\mathrm{oil}}}{V_{\mathrm{total}}}H_{\mathrm{oil}}
+ \frac{V_{\mathrm{water}}}{V_{\mathrm{total}}}H_{\mathrm{water}}, \\
c_{L,\mathrm{avg}} &= \frac{V_{\mathrm{oil}}}{V_{\mathrm{total}}}c_{\mathrm{oil}}
+ \frac{V_{\mathrm{water}}}{V_{\mathrm{total}}}c_{\mathrm{water}}.
\end{aligned}
$$

Where:
- `V_oil`, `V_water` = volume of oil and water phases from thermodynamic flash
- `V_total = V_oil + V_water`
- `ρ`, `μ`, `H`, `c` = density, viscosity, enthalpy, and sound speed

This approach maintains the drift-flux framework while properly accounting for oil-water mixtures in the liquid phase. If only one liquid phase is present (oil OR water), that phase's properties are used directly.

### Flow Regime Detection

The model supports two methods for flow regime detection:

#### Mechanistic Approach (Default)

Uses mechanistic criteria to determine the local flow pattern:

1. **Single-Phase Check**: If liquid holdup < 0.001 → Gas; if gas holdup < 0.001 → Liquid
2. **Taitel-Dukler (1976)** for horizontal/near-horizontal pipes:
   - Stratified-Slug transition based on Kelvin-Helmholtz stability
   - Uses Lockhart-Martinelli parameter and Froude numbers
3. **Barnea (1987)** for inclined pipes:
   - Unified model covering all inclinations
   - Bubble-slug transition at void fraction ≈ 0.25
   - Annular transition at high gas velocities

#### Minimum Slip Criterion

An alternative approach that selects the flow regime with the minimum slip ratio (closest to 1.0, i.e., homogeneous flow). This is based on the principle that the physical system naturally tends toward the flow pattern with minimum phase velocity difference.

```java
FlowRegimeDetector detector = new FlowRegimeDetector();

// Enable minimum slip criterion
detector.setUseMinimumSlipCriterion(true);

// Or use the detection method enum
detector.setDetectionMethod(FlowRegimeDetector.DetectionMethod.MINIMUM_SLIP);

// Detect flow regime
FlowRegime regime = detector.detectFlowRegime(section);
```

The minimum slip criterion evaluates orientation-appropriate candidate regimes:
- **Horizontal pipes**: Stratified (smooth/wavy), Slug, Annular, Dispersed Bubble
- **Upward inclined**: Bubble, Slug, Churn, Annular, Dispersed Bubble
- **Downward inclined**: Stratified (smooth/wavy), Slug, Annular

### Numerical Method

The model solves the conservation equations using:

- **Spatial Discretization**: Finite volume method with cell-centered values
- **Flux Calculation**: AUSM+ (Advection Upstream Splitting Method) scheme
- **Time Integration**: Explicit Euler with adaptive time stepping
- **Stability**: CFL condition with default CFL number = 0.5

Conservative variables:
```
U = [ρ_G·α_G, ρ_L·α_L, ρ_m·u, ρ_m·e]
```

## Quick Start

### Basic Horizontal Pipeline

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.pipeline.twophasepipe.LiquidAccumulationTracker;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Define this as a class field in the enclosing example class.
private static final Logger logger =
    LogManager.getLogger("TransientPipeExample");

// Create two-phase fluid
SystemInterface fluid = new SystemSrkEos(300, 50); // 300 K, 50 bar
fluid.addComponent("methane", 0.8);
fluid.addComponent("n-pentane", 0.2);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Create inlet stream
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(5, "kg/sec");
inlet.run();

// Create transient pipe
TransientPipe pipe = new TransientPipe("Pipeline", inlet);
pipe.setLength(1000);          // 1000 m
pipe.setDiameter(0.2);         // 200 mm
pipe.setRoughness(0.00005);    // 50 μm
pipe.setNumberOfSections(50);  // 50 cells
pipe.setMaxSimulationTime(60); // 60 seconds

// Run simulation
pipe.run();

// Get results
double[] pressures = pipe.getPressureProfile();
double[] holdups = pipe.getLiquidHoldupProfile();
double[] gasVel = pipe.getGasVelocityProfile();
```

### Terrain Pipeline with Low Points

```java
// Create pipe with terrain
TransientPipe pipe = new TransientPipe("TerrainPipe", inlet);
pipe.setLength(2000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(40);
pipe.setMaxSimulationTime(300);

// Define elevation profile with a low point
double[] elevations = new double[40];
for (int i = 0; i < 40; i++) {
    double x = i * 50.0; // Position along pipe
    if (x < 500) {
        elevations[i] = 0;
    } else if (x < 1000) {
        elevations[i] = -20 * (x - 500) / 500; // Downhill to -20m
    } else if (x < 1500) {
        elevations[i] = -20 + 20 * (x - 1000) / 500; // Uphill
    } else {
        elevations[i] = 0;
    }
}
pipe.setElevationProfile(elevations);

pipe.run();

// Check for liquid accumulation
LiquidAccumulationTracker accumulationTracker = pipe.getAccumulationTracker();
for (LiquidAccumulationTracker.AccumulationZone zone
        : accumulationTracker.getAccumulationZones()) {
    logger.info("Zone starts at: {} m", zone.startPosition);
    logger.info("Accumulated volume: {} m³", zone.liquidVolume);
}
```

### Vertical Riser

```java
TransientPipe riser = new TransientPipe("Riser", inlet);
riser.setLength(200);
riser.setDiameter(0.15);
riser.setNumberOfSections(40);

// Vertical profile
double[] elevations = new double[40];
for (int i = 0; i < 40; i++) {
    elevations[i] = i * 5; // 5m per section
}
riser.setElevationProfile(elevations);

riser.run();

// Significant pressure drop due to hydrostatic head
double[] P = riser.getPressureProfile();
```

### Three-Phase Gas-Oil-Water Flow

```java
// Create three-phase fluid
SystemInterface fluid = new SystemSrkEos(300, 50);
fluid.addComponent("methane", 0.40);
fluid.addComponent("propane", 0.10);
fluid.addComponent("n-heptane", 0.20);
fluid.addComponent("n-octane", 0.10);
fluid.addComponent("water", 0.20);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Create stream and pipe
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(15, "kg/sec");
inlet.run();

TransientPipe pipe = new TransientPipe("ThreePhasePipe", inlet);
pipe.setLength(1000);
pipe.setDiameter(0.25);
pipe.setNumberOfSections(50);
pipe.run();

// The model automatically uses volume-weighted averaging for liquid properties
// when both oil and aqueous phases are present.
double[] pressure = pipe.getPressureProfile(); // Pa
double deltaPBar = (pressure[0] - pressure[pressure.length - 1]) / 1.0e5;
logger.info("Pipeline pressure drop: {} bar", deltaPBar);
```

## Configuration Options

### Geometry

| Method | Description | Default |
|--------|-------------|---------|
| `setLength(double)` | Total pipe length (m) | - |
| `setDiameter(double)` | Inner diameter (m) | - |
| `setRoughness(double)` | Wall roughness (m) | 0.0001 |
| `setNumberOfSections(int)` | Discretization cells | 50 |
| `setElevationProfile(double[])` | Elevation at each node (m) | null (horizontal) |
| `setInclinationProfile(double[])` | Inclination angles (rad) | null |

### Simulation Control

| Method | Description | Default |
|--------|-------------|---------|
| `setMaxSimulationTime(double)` | Total simulation time (s) | 3600 |
| `setCflNumber(double)` | CFL number (0.1-1.0) | 0.5 |
| `setThermodynamicUpdateInterval(int)` | Flash update frequency | 10 |
| `setUpdateThermodynamics(boolean)` | Enable/disable thermo updates | true |

### Boundary Conditions

The default inlet boundary is `CONSTANT_FLOW`, and the default outlet boundary is
`CONSTANT_PRESSURE`. The connected inlet stream supplies the mass flow during
initialization and is re-read by each `runTransient()` call, so set the initial
flow with `inlet.setFlowRate(...)` as shown in the quick start. Set a receiving
pressure explicitly before the first `run()` or `runTransient()` call when the
downstream pressure is known:

```java
import java.util.UUID;
import neqsim.process.equipment.pipeline.twophasepipe.TransientPipe.BoundaryCondition;

pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);

pipe.setOutletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE);
double specifiedOutletPressure = 30.0; // bara
pipe.setOutletPressure(specifiedOutletPressure);

// A zero-duration first call initializes the profiles without advancing time.
pipe.runTransient(0.0, UUID.randomUUID());
double[] initialPressure = pipe.getPressureProfile(); // Pa
double initializedOutletPressure =
    initialPressure[initialPressure.length - 1] / 1.0e5;
if (Math.abs(initializedOutletPressure - specifiedOutletPressure) > 1.0e-6) {
    throw new IllegalStateException("Outlet pressure boundary was not preserved");
}
```

`setOutletPressure(double)` accepts absolute pressure in bara. An explicit value
is retained while the pipe is initialized and is imposed at the final
computational section for a `CONSTANT_PRESSURE` outlet. If the setter is not
called, initialization estimates the outlet pressure from inlet pressure,
elevation, and friction and applies a lower bound of 1 bara. This estimate is an
initial condition, not a substitute for a known receiving-system pressure.

| Type | Description |
|------|-------------|
| `CONSTANT_PRESSURE` | Fixed pressure boundary |
| `CONSTANT_FLOW` | Fixed mass flow rate |
| `CONSTANT_VELOCITY` | Fixed velocity |
| `CLOSED` | No-flow wall |

## Output Results

### Profile Data

```java
// Spatial profiles at end of simulation
double[] pressure = pipe.getPressureProfile();      // Pa
double[] temperature = pipe.getTemperatureProfile(); // K
double[] liquidHoldup = pipe.getLiquidHoldupProfile(); // fraction
double[] gasVelocity = pipe.getGasVelocityProfile();   // m/s
double[] liquidVelocity = pipe.getLiquidVelocityProfile(); // m/s
```

### Time History

```java
// Pressure history at all locations
double[][] pressureHistory = pipe.getPressureHistory();
// pressureHistory[time_index][position_index]
```

### Slug Statistics

```java
SlugTracker slugTracker = pipe.getSlugTracker();

int activeSlugCount = slugTracker.getSlugCount();
int totalGenerated = slugTracker.getTotalSlugsGenerated();
double avgLength = slugTracker.getAverageSlugLength();
double frequency = slugTracker.getSlugFrequency();

// Detailed statistics
String stats = slugTracker.getStatisticsString();
logger.info("{}", stats);
```

**Note:** Both `TransientPipe` (drift-flux) and `TwoFluidPipe` (two-fluid) use the same `SlugTracker` and `LiquidAccumulationTracker` components, but may predict different slug frequencies due to their underlying holdup models. See the [Two-Fluid Model documentation](two_fluid_model.md#comparison-with-drift-flux-model-transientpipe) for a detailed comparison.

### Accumulation Zones

```java
LiquidAccumulationTracker tracker = pipe.getAccumulationTracker();

for (LiquidAccumulationTracker.AccumulationZone zone
        : tracker.getAccumulationZones()) {
    logger.info("Zone start: {} m", zone.startPosition);
    logger.info("Zone end: {} m", zone.endPosition);
    logger.info("Accumulated volume: {} m³", zone.liquidVolume);
    logger.info("Maximum volume: {} m³", zone.maxVolume);
    logger.info("Is overflowing: {}", zone.isOverflowing);
}
```

## Advanced Usage

### Custom Flow Regime Detection

```java
PipeSection[] sections = pipe.getSections();
FlowRegimeDetector detector = new FlowRegimeDetector();

for (PipeSection section : sections) {
    FlowRegime regime = detector.detectFlowRegime(section);
    logger.info("Position {}: {}", section.getPosition(), regime);
}
```

### Drift-Flux Analysis

```java
DriftFluxModel model = new DriftFluxModel();

for (PipeSection section : sections) {
    DriftFluxParameters params = model.calculateDriftFlux(section);
    
    logger.info("C0 = {}", params.C0);
    logger.info("Drift velocity = {} m/s", params.driftVelocity);
    logger.info("Void fraction = {}", params.voidFraction);
    logger.info("Slip ratio = {}", params.slipRatio);
}
```

### Accessing Individual Sections

```java
PipeSection[] sections = pipe.getSections();

for (int i = 0; i < sections.length; i++) {
    PipeSection s = sections[i];
    
    logger.info("Section {} (x={} m)", i, s.getPosition());
    logger.info("Pressure: {} bar", s.getPressure() / 1.0e5);
    logger.info("Temperature: {} K", s.getTemperature());
    logger.info("Liquid holdup: {}", s.getLiquidHoldup());
    logger.info("Gas velocity: {} m/s", s.getGasVelocity());
    logger.info("Liquid velocity: {} m/s", s.getLiquidVelocity());
    logger.info("Flow regime: {}", s.getFlowRegime());
    logger.info("Is low point: {}", s.isLowPoint());
}
```

## Integration with ProcessSystem

```java
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem process = new ProcessSystem();

// Add inlet stream
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(10, "kg/sec");
process.add(inlet);

// Add transient pipe
TransientPipe pipeline = new TransientPipe("MainPipeline", inlet);
pipeline.setLength(5000);
pipeline.setDiameter(0.4);
pipeline.setNumberOfSections(100);
pipeline.setMaxSimulationTime(600);
process.add(pipeline);

// Add downstream equipment
Stream outlet = pipeline.getOutletStream();
// ... add separators, compressors, etc.

process.run();
```

## Performance Considerations

### Spatial Resolution

- **Guideline**: dx ≈ 10-50 pipe diameters
- More sections = better accuracy but slower
- For slug tracking, use at least 20 sections

### Time Step

- Controlled automatically via CFL condition
- Lower CFL number (e.g., 0.3) for more stability
- Higher CFL number (e.g., 0.8) for faster simulation

### Thermodynamic Updates

- Flash calculations are expensive
- `setThermodynamicUpdateInterval(20)` reduces frequency
- For isothermal flow, use `setUpdateThermodynamics(false)`

## Flow Regime Definitions

| Regime | Description | Typical Conditions |
|--------|-------------|-------------------|
| `SINGLE_PHASE_GAS` | Gas only | α_L < 0.001 |
| `SINGLE_PHASE_LIQUID` | Liquid only | α_G < 0.001 |
| `BUBBLE` | Discrete bubbles in liquid | Low gas velocity, vertical |
| `SLUG` | Alternating liquid slugs and gas bubbles | Moderate velocities |
| `STRATIFIED_SMOOTH` | Separated phases, smooth interface | Low velocities, horizontal |
| `STRATIFIED_WAVY` | Separated phases, wavy interface | Moderate velocities, horizontal |
| `ANNULAR` | Liquid film on wall, gas core | High gas velocity |
| `CHURN` | Chaotic, oscillating flow | High velocities, vertical |

## Troubleshooting

### Simulation Instability

**Symptoms**: NaN values, oscillations, crashes

**Solutions**:
1. Reduce CFL number: `pipe.setCflNumber(0.3)`
2. Increase number of sections
3. Check for unrealistic boundary conditions
4. Verify fluid properties are physical

### Slow Performance

**Solutions**:
1. Reduce number of sections
2. Increase thermodynamic update interval
3. Use simpler equation of state
4. Reduce simulation time

### No Slugs Detected

**Possible causes**:
1. Flow is single-phase (check inlet conditions)
2. Pipe too short for slug development
3. Velocities outside slug regime
4. No terrain variation (add elevation profile)

## References

1. Taitel, Y. and Dukler, A.E. (1976). "A Model for Predicting Flow Regime Transitions in Horizontal and Near Horizontal Gas-Liquid Flow." *AIChE Journal*, 22(1), 47-55.

2. Barnea, D. (1987). "A Unified Model for Predicting Flow-Pattern Transitions for the Whole Range of Pipe Inclinations." *Int. J. Multiphase Flow*, 13(1), 1-12.

3. Bendiksen, K.H. (1984). "An Experimental Investigation of the Motion of Long Bubbles in Inclined Tubes." *Int. J. Multiphase Flow*, 10(4), 467-483.

4. Zuber, N. and Findlay, J.A. (1965). "Average Volumetric Concentration in Two-Phase Flow Systems." *J. Heat Transfer*, 87(4), 453-468.

5. Harmathy, T.Z. (1960). "Velocity of Large Drops and Bubbles in Media of Infinite or Restricted Extent." *AIChE Journal*, 6(2), 281-288.

## Comparison with Beggs and Brill Correlation

### Overview

The `TransientPipe` model uses a different approach than empirical correlations like Beggs and Brill (1973). Understanding these differences helps in selecting the appropriate model for your application.

### Model Comparison

| Aspect | TransientPipe | Beggs and Brill |
|--------|---------------|-----------------|
| **Approach** | Mechanistic drift-flux with AUSM+ scheme | Empirical correlation from experiments |
| **Basis** | Conservation equations + closure relations | ~1500 experimental data points |
| **Flow Regimes** | Taitel-Dukler, Barnea criteria | Froude number based map |
| **Transient** | Full transient capability | Steady-state only |
| **Terrain** | Section-by-section integration | Overall correlation |

### Expected Differences

The models can differ materially because they use different conservation,
holdup, slip, and flow-regime treatments. Do not use a fixed percentage as an
acceptance tolerance: the difference depends on fluid, geometry, boundary
conditions, discretization, and whether the transient solution has reached the
intended comparison state. Compare like-for-like pressure, temperature, flow,
and elevation boundaries and report the complete configuration with each
benchmark result.

### When to Use Each Model

**Use TransientPipe when:**
- Transient phenomena are important (slugging, pressure waves, startup/shutdown)
- Terrain-induced slugging needs to be captured
- Liquid accumulation at low points is of interest
- Detailed spatial profiles are needed along the pipe
- Integration with NeqSim thermodynamics is beneficial

**Use Beggs and Brill when:**
- Quick steady-state pressure drop estimates are needed
- Conditions are within the empirical correlation's validated range
- Simple horizontal or inclined pipe calculations
- Historical validation against field data used this correlation

### Validation Approach

For critical applications, it is recommended to:

1. **Benchmark both models** against field data or detailed CFD simulations
2. **Understand model assumptions** - TransientPipe uses mechanistic closure relations that may need tuning for specific fluids
3. **Quantify uncertainty for the case** - derive uncertainty from relevant measurements, parameter sensitivity, and model-form comparisons rather than a generic percentage
4. **Use multiple models** - consensus from different approaches increases confidence

### Comparison Test Examples

NeqSim includes comparison tests in `TransientPipeVsBeggsAndBrillsComparisonTest.java`:

```java
// Example: Comparing models for horizontal multiphase flow
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("n-pentane", 0.2);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Setup Beggs and Brill
Stream bbStream = new Stream("BB_inlet", fluid);
bbStream.setFlowRate(2.0, "kg/sec");
bbStream.run();

PipeBeggsAndBrills bb = new PipeBeggsAndBrills("BeggsAndBrill", bbStream);
bb.setDiameter(0.2);
bb.setLength(500);
bb.setAngle(0);
bb.run();
double dpBeggsBrill = bb.getPressureDrop();

// Setup TransientPipe
Stream tpStream = new Stream("TP_inlet", fluid.clone());
tpStream.setFlowRate(2.0, "kg/sec");
tpStream.run();

TransientPipe tp = new TransientPipe("TransientPipe", tpStream);
tp.setLength(500);
tp.setDiameter(0.2);
tp.setNumberOfSections(25);
tp.setMaxSimulationTime(60);
tp.run();
double[] pressures = tp.getPressureProfile();
double dpTransient = (pressures[0] - pressures[pressures.length - 1]) / 1e5;

logger.info("Beggs & Brill: {} bar", dpBeggsBrill);
logger.info("TransientPipe: {} bar", dpTransient);
```

### References for Model Comparison

1. Beggs, H.D. and Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes." *Journal of Petroleum Technology*, 25(5), 607-617.

2. Ishii, M. and Hibiki, T. (2011). *Thermo-Fluid Dynamics of Two-Phase Flow*. 2nd ed. Springer.

## See Also

- [Pipeline Flow Equations](pipeline_flow_equations.md)
- [Pipeline Model Recommendations](pipeline_model_recommendations.md)
- [Process Simulation](advanced_process_simulation.md)
