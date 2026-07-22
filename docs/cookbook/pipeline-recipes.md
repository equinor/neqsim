---
title: Pipeline Recipes
description: Quick recipes for pipeline simulation in NeqSim - pressure drop, multiphase flow, drift-flux, two-fluid, slug tracking, three-phase flow, transient dynamics, and terrain effects.
---

# Pipeline Recipes

Copy-paste solutions for pipeline calculations and multiphase flow.

## Table of Contents

- [Simple Pressure Drop](#simple-pressure-drop)
- [Multiphase Pipelines](#multiphase-pipelines)
- [Drift-Flux Model](#drift-flux-model)
- [Dynamic Flow Simulation](#dynamic-flow-simulation)
- [Three-Phase Flow and Slug Tracking](#three-phase-flow-and-slug-tracking)
- [Advanced Pipeline Profiles](#advanced-pipeline-profiles)
- [Boundary Conditions](#boundary-conditions)
- [Heat Transfer](#heat-transfer)
- [Terrain Effects](#terrain-effects)
- [Flow Regimes](#flow-regimes)

---

## Simple Pressure Drop

### Adiabatic Pipe (Single Phase or Simple Multiphase)

```python
from neqsim import jneqsim

AdiabaticPipe = jneqsim.process.equipment.pipeline.AdiabaticPipe

# Assume inlet_stream exists
pipe = AdiabaticPipe("Pipeline", inlet_stream)
pipe.setLength(10000)          # 10 km
pipe.setDiameter(0.3)          # 300 mm ID
pipe.setPipeWallRoughness(5e-5) # m (smooth steel)

process.add(pipe)
process.run()

# Results
inlet_P = inlet_stream.getPressure()
outlet_P = pipe.getOutletStream().getPressure()
dp = inlet_P - outlet_P

print(f"Inlet P: {inlet_P:.2f} bara")
print(f"Outlet P: {outlet_P:.2f} bara")
print(f"Pressure drop: {dp:.2f} bar")
print(f"ΔP/L: {dp / 10:.3f} bar/km")
```

### Set Elevation Change

```python
pipe = AdiabaticPipe("Riser", inlet_stream)
pipe.setLength(500)           # 500 m
pipe.setDiameter(0.2)
pipe.setInletElevation(0)     # Start at sea level
pipe.setOutletElevation(100)  # 100 m elevation gain
```

---

## Multiphase Pipelines

### Beggs and Brill Correlation

```python
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills

pipe = PipeBeggsAndBrills("Export Pipeline", inlet_stream)
pipe.setLength(50000)          # 50 km
pipe.setDiameter(0.4)          # 400 mm
pipe.setAngle(0)               # Horizontal (degrees from horizontal)
pipe.setPipeWallRoughness(4.5e-5)

process.add(pipe)
process.run()

# Multiphase results
print(f"Outlet pressure: {pipe.getOutletStream().getPressure():.2f} bara")
print(f"Liquid holdup: {pipe.getPressureDrop():.2f} bar")  # Total DP
```

### Two-Fluid Model (Detailed)

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

pipe = TwoFluidPipe("Flowline", inlet_stream)
pipe.setLength(20000)
pipe.setDiameter(0.25)
pipe.setNumberOfSections(100)   # Computational cells

# Heat transfer
pipe.setSurfaceTemperature(5.0, "C")        # 5°C ambient
pipe.setHeatTransferCoefficient(25.0)       # W/(m²·K)

# Flat elevation profile (horizontal pipe)
elevations = [0.0] * 100
pipe.setElevationProfile(elevations)

process.add(pipe)
process.run()

# Get detailed profiles
positions = pipe.getPositionProfile()       # Section positions (m)
pressures = pipe.getPressureProfile()       # Pa
holdups = pipe.getLiquidHoldupProfile()      # Liquid fraction
temps = pipe.getTemperatureProfile("C")     # °C
```

### Two-Fluid Model with Advanced Features

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

pipe = TwoFluidPipe("Flowline", inlet_stream)
pipe.setLength(20000)
pipe.setDiameter(0.25)
pipe.setNumberOfSections(100)

# Add local loss coefficients (fittings, bends)
# addLocalLoss(position_m, kFactor)
pipe.addLocalLoss(5000.0, 0.9)   # K=0.9 at 5000m (tee junction)
pipe.addLocalLoss(15000.0, 2.0)  # K=2.0 at 15000m (check valve)
pipe.setNumberOf90DegreeBends(4)   # K=0.3 each
pipe.setNumberOf45DegreeBends(2)   # K=0.16 each
pipe.setInletLossCoefficient(0.5)  # Sharp entrance
pipe.setOutletLossCoefficient(1.0) # Exit to tank

process.add(pipe)
process.run()

# Pressure drop breakdown
dp_friction = pipe.getPressureDrop()
dp_local = pipe.calculateLocalLossPressureDrop()
dp_total = pipe.getTotalPressureDrop()
print(pipe.getLocalLossSummary())
```

---

## Drift-Flux Model

The **drift-flux model** is a simplified multiphase flow approach that solves mixture equations with slip between phases. It's faster than the full two-fluid model while capturing key multiphase phenomena.

### Key Equations

The drift-flux model relates gas velocity to mixture velocity:

$$v_g = C_0 \cdot v_m + v_{drift}$$

Where:
- $C_0$ = distribution parameter (~1.0-1.2 for pipes)
- $v_m$ = mixture superficial velocity
- $v_{drift}$ = drift velocity (buoyancy-driven slip)

### TransientPipe with Drift-Flux

```python
from neqsim import jneqsim

# Note: TransientPipe uses drift-flux internally with AUSM+ scheme
TransientPipe = jneqsim.process.equipment.pipeline.twophasepipe.TransientPipe

# Create fluid and stream
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 50, 80.0)
fluid.addComponent("methane", 0.80)
fluid.addComponent("n-pentane", 0.20)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

stream = jneqsim.process.equipment.stream.Stream("Feed", fluid)
stream.setFlowRate(50.0, "kg/sec")
stream.run()

# Create drift-flux pipe
pipe = TransientPipe("Subsea Flowline", stream)
pipe.setLength(10000)       # 10 km
pipe.setDiameter(0.3)       # 300 mm
pipe.setNumberOfSections(200)
pipe.setRoughness(4.5e-5)   # Steel roughness

# Run steady-state initialization
pipe.run()

# Get drift-flux results
pressures = pipe.getPressureProfile()
holdups = pipe.getLiquidHoldupProfile()
```

### When to Use Drift-Flux

| Scenario | Recommended Model |
|----------|-------------------|
| Quick screening studies | Drift-flux (TransientPipe) |
| Steady-state pressure drop | PipeBeggsAndBrills |
| Detailed slug dynamics | Two-fluid (TwoFluidPipe) |
| Three-phase flow | Two-fluid (TwoFluidPipe) |
| Countercurrent flow | Two-fluid |
| Long transient simulations | Drift-flux (faster) |

> **Further Reading**: See [Multiphase Transient Model](../wiki/multiphase_transient_model) for detailed drift-flux equations and numerical methods.

---

## Dynamic Flow Simulation

### Beggs and Brill - Dynamic Mode

`PipeBeggsAndBrills` can perform pseudo-transient calculations by iterating with updated inlet conditions:

```python
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills

# Create process system
process = jneqsim.process.processmodel.ProcessSystem()

# Create dynamic inlet stream
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 60, 100.0)
fluid.addComponent("methane", 0.85)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("propane", 0.05)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

inlet = jneqsim.process.equipment.stream.Stream("Inlet", fluid)
inlet.setFlowRate(25.0, "kg/sec")
process.add(inlet)

pipe = PipeBeggsAndBrills("Export Line", inlet)
pipe.setLength(30000)         # 30 km
pipe.setDiameter(0.4)         # 400 mm
pipe.setAngle(0)              # Horizontal
pipe.setPipeWallRoughness(4.5e-5)

# Enable detailed output
pipe.setNumberOfIncrements(100)  # Discretization for profiles

process.add(pipe)

# Initial steady-state
process.run()
print(f"Initial outlet P: {pipe.getOutletStream().getPressure():.2f} bara")

# Simulate flow rate ramp-up
flow_rates = [25.0, 30.0, 35.0, 40.0, 45.0]  # kg/s

for rate in flow_rates:
    inlet.setFlowRate(rate, "kg/sec")
    process.run()
    outlet_P = pipe.getOutletStream().getPressure()
    outlet_T = pipe.getOutletStream().getTemperature() - 273.15
    print(f"Flow: {rate:.0f} kg/s -> Outlet P: {outlet_P:.2f} bara, T: {outlet_T:.1f}°C")
```

### Two-Fluid Model - Full Transient

`TwoFluidPipe` solves separate momentum equations for gas and liquid phases with transient time-stepping:

```python
import uuid

TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

# Create two-phase fluid
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 40, 60.0)
fluid.addComponent("methane", 0.75)
fluid.addComponent("n-pentane", 0.25)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

inlet = jneqsim.process.equipment.stream.Stream("Feed", fluid)
inlet.setFlowRate(15.0, "kg/sec")
inlet.run()

# Create two-fluid pipe with fine discretization
pipe = TwoFluidPipe("Slugging Flowline", inlet)
pipe.setLength(5000)             # 5 km
pipe.setDiameter(0.2)            # 200 mm
pipe.setNumberOfSections(200)    # 200 cells = 25m each
pipe.setRoughness(4.5e-5)

# Heat transfer
pipe.setHeatTransferCoefficient(25.0)  # W/(m²·K)
pipe.setSurfaceTemperature(4.0, "C")   # Ambient

# Initialize steady-state
pipe.run()
print(f"Steady-state liquid inventory: {pipe.getLiquidInventory('m3'):.2f} m³")

# Run transient simulation
run_id = str(uuid.uuid4())
dt = 0.5        # 0.5 second time step
t_end = 300.0   # 5 minutes total

time = 0.0
while time < t_end:
    pipe.runTransient(dt, run_id)
    time += dt

    # Ramp up flow rate after 60 seconds
    if time > 60.0:
        inlet.setFlowRate(25.0, "kg/sec")
        inlet.run()

# Final results
print(f"Final liquid inventory: {pipe.getLiquidInventory('m3'):.2f} m³")
print(f"Outlet pressure: {pipe.getOutletStream().getPressure():.2f} bara")
```

### Key Parameters for Dynamic Simulation

| Parameter | Method | Description |
|-----------|--------|-------------|
| Time step | `runTransient(dt, id)` | Transient step size (s) |
| Grid cells | `setNumberOfSections(n)` | Spatial discretization |
| Roughness | `setRoughness(ε)` | Wall roughness (m) |
| Heat transfer | `setHeatTransferCoefficient(U)` | Overall U-value (W/m²K) |
| Ambient temp | `setSurfaceTemperature(T, unit)` | External temperature |

### Cross-Validate TwoFluidPipe vs Beggs & Brill

Compare pressure drops between the two models to build confidence:

```python
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

# Run both models on the same feed
bb = PipeBeggsAndBrills("BB", inlet)
bb.setLength(5000); bb.setDiameter(0.3)
bb.setAngle(0); bb.setPipeWallRoughness(4.5e-5)
bb.run()

tf = TwoFluidPipe("TF", inlet)
tf.setLength(5000); tf.setDiameter(0.3)
tf.setNumberOfSections(50); tf.setRoughness(4.5e-5)
tf.run()

dp_bb = inlet.getPressure() - bb.getOutletStream().getPressure()
dp_tf = inlet.getPressure() - tf.getOutletStream().getPressure()
print(f"BB dP: {dp_bb:.3f} bar, TF dP: {dp_tf:.3f} bar, ratio: {dp_tf/dp_bb:.2f}")
# Expect ratio 0.8-1.3 for typical two-phase horizontal flow
```

> **Note:** For single-phase gas, the ratio should be ~0.98 (excellent agreement).
> For two-phase flow, agreement varies with gas fraction — ratios of 0.8–1.3 are
> within typical engineering accuracy for different multiphase correlations.

---

## Three-Phase Flow and Slug Tracking

`TwoFluidPipe` supports **three-phase flow** (gas-oil-water) with built-in **slug tracking** capabilities.

### Three-Phase Configuration

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

# Create three-phase fluid (gas + oil + water)
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(273.15 + 50, 50.0)
fluid.addComponent("methane", 0.60)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("n-heptane", 0.20)  # Oil phase
fluid.addComponent("water", 0.10)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)

inlet = jneqsim.process.equipment.stream.Stream("WellStream", fluid)
inlet.setFlowRate(30.0, "kg/sec")
inlet.run()

# Configure three-phase pipe
pipe = TwoFluidPipe("Subsea Tieback", inlet)
pipe.setLength(15000)            # 15 km
pipe.setDiameter(0.25)           # 250 mm
pipe.setNumberOfSections(300)    # 300 cells for slug resolution
pipe.setRoughness(4.5e-5)

# Run simulation
pipe.run()

# Get three-phase results
liquid_inventory = pipe.getLiquidInventory("m3")
water_holdup = pipe.getWaterHoldupProfile()  # Water-in-liquid fraction
oil_holdup = pipe.getOilHoldupProfile()      # Oil holdup
pressure_profile = pipe.getPressureProfile()

print(f"Total liquid inventory: {liquid_inventory:.2f} m³")
print(f"Average water holdup: {sum(water_holdup)/len(water_holdup):.4f}")
print(f"Average oil holdup: {sum(oil_holdup)/len(oil_holdup):.4f}")
```

### Slug Tracking with Lagrangian Method

For detailed slug dynamics, use the Lagrangian slug tracker:

```python
import uuid

# Configure pipe for slug tracking
pipe = TwoFluidPipe("Slugging Pipeline", inlet)
pipe.setLength(8000)             # 8 km
pipe.setDiameter(0.15)           # 150 mm (promotes slugging)
pipe.setNumberOfSections(400)    # Fine grid for slug resolution

# Enable Lagrangian slug tracking
pipe.setEnableSlugTracking(True)

# Set terrain to promote terrain-induced slugging
elevations = []
for i in range(400):
    x = i * 20.0  # 20m per section
    # Create V-shaped terrain (valley)
    elevation = -30.0 * (1 - abs(x/4000 - 1))
    elevations.append(elevation)

pipe.setElevationProfile(elevations)

# Initialize and run
pipe.run()

# Run transient to observe slug development
run_id = str(uuid.uuid4())
for step in range(600):  # 5 minutes @ 0.5s steps
    pipe.runTransient(0.5, run_id)

    # Monitor slugs every 30 seconds
    if step % 60 == 0:
        slug_count = pipe.getSlugTracker().getSlugCount()
        avg_slug_length = pipe.getSlugTracker().getAverageSlugLength()
        slug_frequency = pipe.getSlugTracker().getSlugFrequency()
        print(f"Time: {step*0.5:.0f}s - Slugs: {slug_count}, "
              f"Avg length: {avg_slug_length:.1f}m, "
              f"Frequency: {slug_frequency:.3f} Hz")
```

### Three-Phase Capabilities Summary

| Feature | TwoFluidPipe | PipeBeggsAndBrills |
|---------|--------------|-------------------|
| Gas-liquid flow | ✅ | ✅ |
| Three-phase (gas-oil-water) | ✅ | Limited |
| Water holdup profile | ✅ | No |
| Oil holdup profile | ✅ | No |
| Slug tracking | ✅ (Lagrangian) | No |
| Terrain-induced slugging | ✅ | Empirical |
| Transient dynamics | ✅ Full | Pseudo-steady |

> **Further Reading**: See [TwoFluidPipe Tutorial](../examples/TwoFluidPipe_Tutorial) for comprehensive examples including slug visualization and transient analysis.

---

## Advanced Pipeline Profiles

For complex pipelines with varying geometry, use multi-leg or segmented configurations.

### Multi-Leg Pipeline (OnePhasePipeLine)

```python
OnePhasePipeLine = jneqsim.process.equipment.pipeline.OnePhasePipeLine

# Create gas stream
gas = jneqsim.thermo.system.SystemSrkEos(273.15 + 25, 200.0)
gas.addComponent("methane", 0.95)
gas.addComponent("ethane", 0.05)
gas.setMixingRule("classic")

stream = jneqsim.process.equipment.stream.Stream("Gas Feed", gas)
stream.setFlowRate(50000, "kg/hr")
stream.run()

# Create multi-segment pipeline
pipeline = OnePhasePipeLine(stream)

# Define leg geometry arrays
diameters = [1.0, 1.0, 0.8]           # meters (reducing pipe)
positions = [0, 70000, 150000]         # cumulative length (m)
elevations = [0, -200, -350]           # elevation profile (m)
roughnesses = [1e-5, 1e-5, 1e-5]       # wall roughness (m)
ambient_temps = [277.0, 280.0, 283.0]  # K (warming seabed)
U_outer = [15.0, 15.0, 15.0]           # outer U-value (W/m²K)
U_wall = [15.0, 15.0, 15.0]            # wall U-value (W/m²K)

# Configure legs
pipeline.setNumberOfLegs(len(diameters) - 1)
pipeline.setPipeDiameters(diameters)
pipeline.setLegPositions(positions)
pipeline.setHeightProfile(elevations)
pipeline.setPipeWallRoughness(roughnesses)
pipeline.setOuterTemperatures(ambient_temps)
pipeline.setPipeOuterHeatTransferCoefficients(U_outer)
pipeline.setPipeWallHeatTransferCoefficients(U_wall)

# Run
process = jneqsim.process.processmodel.ProcessSystem()
process.add(stream)
process.add(pipeline)
process.run()

# Results
outlet_P = pipeline.getOutletStream().getPressure()
outlet_T = pipeline.getOutletStream().getTemperature() - 273.15
print(f"Outlet: {outlet_P:.2f} bara, {outlet_T:.1f}°C")
```

### Complex Elevation Profile (TwoFluidPipe)

```python
import math

TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe

# Subsea pipeline with realistic terrain
pipe = TwoFluidPipe("Subsea Tieback", inlet_stream)
pipe.setLength(25000)              # 25 km
pipe.setDiameter(0.3)              # 300 mm
pipe.setNumberOfSections(500)      # 50m per section

# Create complex elevation profile
num_sections = 500
dx = 25000 / num_sections

elevations = []
for i in range(num_sections):
    x = i * dx

    # Start at platform (-50m), descend to seabed, undulations, rise to FPSO

    # Riser down (0-500m)
    if x < 500:
        elev = -50 - (x / 500) * 300  # Descend 300m

    # Seabed section with undulations (500m - 24000m)
    elif x < 24000:
        base = -350  # Base seabed depth
        # Add hills and valleys
        undulation = 20 * math.sin(x / 2000 * 2 * math.pi)  # ±20m
        valley = -40 * math.exp(-((x - 12000) / 3000)**2)   # Deep valley mid-pipe
        elev = base + undulation + valley

    # Riser up (24000m - 25000m)
    else:
        elev = -350 + ((x - 24000) / 1000) * 340  # Rise to -10m

    elevations.append(elev)

pipe.setElevationProfile(elevations)

# Heat transfer zones
pipe.setHeatTransferCoefficient(25.0)   # W/(m²·K) for bare pipe
pipe.setSurfaceTemperature(4.0, "C")    # Seabed temperature

# Run simulation
pipe.run()

# Analyze liquid accumulation in valleys
liquid_inv = pipe.getLiquidInventory("m3")
print(f"Liquid inventory in pipeline: {liquid_inv:.2f} m³")
```

### Variable Diameter Pipeline

```python
# Create pipe sections with different diameters
# by chaining multiple TwoFluidPipe segments

process = jneqsim.process.processmodel.ProcessSystem()

# Add inlet stream
process.add(inlet_stream)

# Section 1: Large diameter trunk line
section1 = TwoFluidPipe("Trunk Line", inlet_stream)
section1.setLength(15000)          # 15 km
section1.setDiameter(0.4)          # 400 mm
section1.setNumberOfSections(150)
process.add(section1)

# Section 2: Reduced diameter spur
section2 = TwoFluidPipe("Spur Line", section1.getOutletStream())
section2.setLength(8000)           # 8 km
section2.setDiameter(0.25)         # 250 mm
section2.setNumberOfSections(80)
process.add(section2)

# Section 3: Riser
section3 = TwoFluidPipe("Riser", section2.getOutletStream())
section3.setLength(300)            # 300 m riser
section3.setDiameter(0.2)          # 200 mm
section3.setNumberOfSections(60)

# Vertical elevation for riser
riser_elevations = [-300 + i * 5 for i in range(60)]  # -300m to 0m
section3.setElevationProfile(riser_elevations)
process.add(section3)

# Run complete system
process.run()

# Get results at each section outlet
print(f"After trunk: {section1.getOutletStream().getPressure():.2f} bara")
print(f"After spur: {section2.getOutletStream().getPressure():.2f} bara")
print(f"At topside: {section3.getOutletStream().getPressure():.2f} bara")
```

### Pipeline Configuration Summary

| Configuration | Method | Use Case |
|---------------|--------|----------|
| Single pipe | `setLength()`, `setDiameter()` | Simple flowline |
| Elevation change | `setInletElevation()`, `setOutletElevation()` | Riser, inclined pipe |
| Detailed terrain | `setElevationProfile(array)` | Undulating seabed |
| Multi-leg | `setNumberOfLegs()`, `setLegPositions()` | Variable geometry |
| Pipe sections | Chain multiple pipes | Different diameters/materials |
| Grid resolution | `setNumberOfSections(n)` | Fine vs coarse simulation |
| Non-uniform mesh | `generateRefinedMesh(n, factor)` | Finer cells at risers/gradients |
| Manual mesh | `setSectionLengths(double[])` | Full control over each cell |

### Non-Uniform Mesh (TwoFluidPipe)

Commercial simulators (OLGA, LedaFlow) use **static non-uniform meshes** to place finer
cells where gradients are steepest — typically at risers, terrain undulations, and wellheads.
NeqSim supports this via two approaches:

#### Automatic Refinement (recommended)

`generateRefinedMesh(baseSections, refinementFactor)` analyses the elevation profile and
creates shorter cells where the gradient is steepest with longer cells where the pipe is flat.

```python
pipe = TwoFluidPipe("Flowline-Riser", feed)
pipe.setLength(5400.0)
pipe.setDiameter(0.2032)

# Set elevation profile FIRST
elevation = [...]  # must be set before generateRefinedMesh
pipe.setElevationProfile(elevation)

# Generate refined mesh: 100 base sections, 4x refinement at steep sections
pipe.generateRefinedMesh(100, 4.0)

# Inspect resulting mesh
lengths = list(pipe.getSectionLengths())
print(f"dx range: [{min(lengths):.1f}, {max(lengths):.1f}] m")
# e.g. Flowline cells ~68 m, Riser cells ~17 m (3.9x ratio)
```

The `refinementFactor` controls the ratio between coarsest and finest cells (clamped to 1.5–10).

#### Manual Section Lengths

For full control, provide exact lengths for every section:

```python
pipe = TwoFluidPipe("Custom Mesh", feed)
pipe.setLength(1000.0)
pipe.setDiameter(0.3)

# Fine at ends, coarse in middle — lengths must sum to total length
lengths = [50, 50, 100, 150, 200, 150, 100, 100, 50, 50]
pipe.setSectionLengths(lengths)
```

#### CFL Note

With explicit time integration (RK4), the global timestep is governed by the **smallest cell**:
$\Delta t = \text{CFL} \cdot dx_{\min} / c_{\max}$. Finer riser cells improve accuracy but
reduce the CFL timestep proportionally. For a 4x refinement ratio, expect ~4x more sub-steps
per macro timestep.

### Adaptive Timestepping (TwoFluidPipe)

OLGA-style adaptive timestepping provides robustness for challenging geometries (risers, S-bends):

```python
pipe = TwoFluidPipe("Subsea Line", feed)
pipe.setLength(5400.0)
pipe.setNumberOfSections(100)
pipe.setElevationProfile(elevation)

# Enable adaptive timestepping
pipe.setEnableAdaptiveTimestepping(True)
pipe.setAdaptiveMaxPressure(200.0)  # bar — reject step if exceeded

# Run transient
import java.util.UUID as UUID
run_id = UUID.randomUUID()
for step in range(n_steps):
    pipe.runTransient(dt, run_id)
    dt_factor = pipe.getAdaptiveDtFactor()  # 1.0 = full CFL, <1 = reduced
```

| Parameter | Method | Default | Description |
|-----------|--------|---------|-------------|
| Enable | `setEnableAdaptiveTimestepping(bool)` | `False` | Turn on/off |
| Pressure ceiling | `setAdaptiveMaxPressure(bar)` | 1000 | Reject step if any section exceeds |
| Monitor | `getAdaptiveDtFactor()` | — | Current dt multiplier (1.0 = full CFL) |
| Check | `isAdaptiveTimesteppingEnabled()` | — | Query state |

The algorithm: (1) per-step CFL recompute from current velocities, (2) NaN/negative mass
detection with state rollback, (3) pressure/velocity ceiling checks, (4) gradual dt recovery
(x1.02 growth per stable step back to 1.0).

---

## Boundary Conditions

For transient `TwoFluidPipe` simulations, you can configure inlet and outlet boundary conditions to control how the solver handles flow rate and pressure.

### Boundary Condition Types

| Type | Description |
|------|-------------|
| `STREAM_CONNECTED` | Use properties from connected inlet stream (default for inlet) |
| `CONSTANT_FLOW` | Fixed mass flow rate (set via `setInletMassFlow()`) |
| `CONSTANT_PRESSURE` | Fixed pressure (default for outlet) |
| `CLOSED` | Zero flow velocity (blocked/shut-in) — pressure floats |

### Default Behavior

By default, `TwoFluidPipe` uses:
- **Inlet**: `STREAM_CONNECTED` — flow rate, temperature, and composition from the inlet stream
- **Outlet**: `CONSTANT_PRESSURE` — fixed outlet pressure

During transient simulation, the inlet pressure is computed from momentum balance (backward marching from the outlet boundary), while the inlet flow rate is fixed.

### Setting Boundary Conditions

```python
TwoFluidPipe = jneqsim.process.equipment.pipeline.TwoFluidPipe
BoundaryCondition = TwoFluidPipe.BoundaryCondition

pipe = TwoFluidPipe("Pipeline", inlet_stream)
pipe.setLength(10000)
pipe.setDiameter(0.25)
pipe.setNumberOfSections(50)

# Set outlet pressure (required for transient)
pipe.setOutletPressure(30.0, "bara")

# Option 1: Default - use stream flow rate
pipe.setInletBoundaryCondition(BoundaryCondition.STREAM_CONNECTED)

# Option 2: Explicit mass flow (independent of stream)
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW)
pipe.setInletMassFlow(50.0)            # kg/s
pipe.setInletMassFlow(180000, "kg/hr") # or with unit

# Option 3: Fixed inlet pressure (flow computed from momentum)
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE)
pipe.setInletPressure(60.0, "bara")

# Query current BC types
inlet_bc = pipe.getInletBoundaryCondition()
outlet_bc = pipe.getOutletBoundaryCondition()
```

### Transient Simulation with Changing Flow Rate

```python
import uuid

# Configure pipe with explicit flow BC
pipe = TwoFluidPipe("Flowline", inlet_stream)
pipe.setLength(10000)
pipe.setDiameter(0.25)
pipe.setNumberOfSections(50)

# Use constant flow BC so we can change flow directly
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW)
pipe.setInletMassFlow(20.0)  # Initial 20 kg/s
pipe.setOutletPressure(30.0, "bara")

# Initialize
pipe.run()

# Transient loop with flow ramp-up
run_id = str(uuid.uuid4())
for t in range(120):  # 2 minutes
    # Ramp flow from 20 to 50 kg/s over first 60s
    if t < 60:
        new_flow = 20.0 + (t / 60.0) * 30.0
        pipe.setInletMassFlow(new_flow)

    pipe.runTransient(1.0, run_id)  # 1s timestep

    # Monitor inlet pressure (computed by solver)
    pressures = pipe.getPressureProfile()
    inlet_P = pressures[0] / 1e5  # Pa to bara
    print(f"t={t}s, flow={new_flow:.1f} kg/s, inlet P={inlet_P:.2f} bara")
```

### Fixed Pressure at Both Ends

For scenarios where both inlet and outlet pressures are known (e.g., connecting to a reservoir and topside), set both as constant pressure:

```python
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE)
pipe.setInletPressure(65.0, "bara")   # Wellhead pressure
pipe.setOutletPressure(30.0, "bara")  # Receiving pressure

# Flow rate is computed from the pressure differential
pipe.run()

# Check computed flow rate from outlet stream
outlet = pipe.getOutletStream()
print(f"Computed outlet mass flow: {outlet.getFlowRate('kg/sec'):.2f} kg/s")
```

### Boundary Condition Summary Table

| Configuration | Inlet BC | Outlet BC | Flow | Inlet P | Outlet P |
|--------------|----------|-----------|------|---------|----------|
| Default | STREAM_CONNECTED | CONSTANT_PRESSURE | From stream | Computed | Fixed |
| Explicit flow | CONSTANT_FLOW | CONSTANT_PRESSURE | Fixed | Computed | Fixed |
| Both P fixed | CONSTANT_PRESSURE | CONSTANT_PRESSURE | Computed | Fixed | Fixed |
| Shut-in outlet | STREAM_CONNECTED | CLOSED | From stream | Computed | Floats |
| Blowdown | CLOSED | CONSTANT_PRESSURE | Zero | Floats | Fixed |
| Blocked pipe | CLOSED | CLOSED | Zero | Floats | Floats |

> **Note:** The most common setup for production pipelines is `STREAM_CONNECTED` inlet with `CONSTANT_PRESSURE` outlet, where the inlet stream defines the flow rate and the outlet represents the receiving facility pressure.

### Shut-In and Surge Scenarios

Use the `CLOSED` boundary condition or convenience methods for shut-in and pressure surge analysis:

```python
import uuid

pipe = TwoFluidPipe("Pipeline", inlet_stream)
pipe.setLength(10000)
pipe.setDiameter(0.25)
pipe.setNumberOfSections(100)
pipe.setOutletPressure(30.0, "bara")

# Initialize with normal flow
pipe.run()
initial_P_inlet = pipe.getPressureProfile()[0] / 1e5
print(f"Initial inlet pressure: {initial_P_inlet:.2f} bara")

# --- Shut-in scenario: close outlet valve ---
pipe.closeOutlet()  # Convenience method (or: BoundaryCondition.CLOSED)

run_id = str(uuid.uuid4())
for t in range(60):  # 1 minute transient
    pipe.runTransient(1.0, run_id)

    if t % 10 == 0:
        pressures = pipe.getPressureProfile()
        P_inlet = pressures[0] / 1e5
        P_outlet = pressures[-1] / 1e5
        print(f"t={t}s: inlet P={P_inlet:.2f} bara, outlet P={P_outlet:.2f} bara")

# --- Reopen outlet ---
pipe.openOutlet(30.0, "bara")

# Continue transient to observe pressure recovery
for t in range(60, 120):
    pipe.runTransient(1.0, run_id)
```

### Blowdown / Depressurization

Close inlet, leave outlet open for blowdown simulation:

```python
pipe = TwoFluidPipe("Pipeline", inlet_stream)
pipe.setLength(5000)
pipe.setDiameter(0.3)
pipe.setNumberOfSections(50)

# Initialize packed pipe at high pressure
pipe.setInletPressure(100.0, "bara")
pipe.setOutletPressure(100.0, "bara")
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_PRESSURE)
pipe.run()

# Blowdown: close inlet, open outlet to atmosphere
pipe.closeInlet()
pipe.openOutlet(1.0, "bara")  # Vent to atmospheric

# Monitor depressurization
run_id = str(uuid.uuid4())
for t in range(300):  # 5 minutes
    pipe.runTransient(1.0, run_id)

    if t % 30 == 0:
        pressures = pipe.getPressureProfile()
        avg_P = sum(pressures) / len(pressures) / 1e5
        inventory = pipe.getLiquidInventory("m3")
        print(f"t={t}s: avg P={avg_P:.2f} bara, liquid={inventory:.3f} m³")
```

### Convenience Methods

| Method | Description |
|--------|-------------|
| `closeOutlet()` | Set outlet BC to CLOSED (zero velocity) |
| `openOutlet()` | Restore outlet to CONSTANT_PRESSURE (uses last set pressure) |
| `openOutlet(P, unit)` | Open outlet with specified pressure |
| `closeInlet()` | Set inlet BC to CLOSED (zero velocity) |
| `openInlet()` | Restore inlet to STREAM_CONNECTED |
| `isOutletClosed()` | Returns true if outlet BC is CLOSED |
| `isInletClosed()` | Returns true if inlet BC is CLOSED |

---

## Heat Transfer

### Pipeline with Heat Loss

```python
pipe = PipeBeggsAndBrills("Subsea Flowline", inlet_stream)
pipe.setLength(30000)
pipe.setDiameter(0.3)

# Heat transfer
pipe.setConstantSurfaceTemperature(4.0, "C")  # 4°C seawater
pipe.setHeatTransferCoefficient(10.0)  # W/m²K (overall U-value)

process.add(pipe)
process.run()

inlet_T = inlet_stream.getTemperature() - 273.15
outlet_T = pipe.getOutletStream().getTemperature() - 273.15
print(f"Temperature drop: {inlet_T - outlet_T:.1f} °C")
```

### Insulated Pipeline

```python
# Higher insulation = lower U-value
pipe.setHeatTransferCoefficient(2.0)  # W/m²K (well insulated)
```

### Buried Pipeline

```python
pipe.setConstantSurfaceTemperature(10.0, "C")  # 10°C soil temperature
pipe.setHeatTransferCoefficient(5.0)  # W/m²K (buried)
```

---

## Terrain Effects

### Pipeline with Elevation Profile

```python
# For more complex terrain, use TwoFluidPipe with segments
# or set overall elevation change

pipe = PipeBeggsAndBrills("Mountain Pipeline", inlet_stream)
pipe.setLength(10000)
pipe.setDiameter(0.3)

# Set inclination angle (degrees from horizontal)
# Positive = uphill, Negative = downhill
pipe.setAngle(5)  # 5° uphill

# Or set specific elevations
pipe.setInletElevation(0)
pipe.setOutletElevation(870)  # ~870 m rise for 5° over 10 km
```

### Riser (Vertical Section)

```python
riser = PipeBeggsAndBrills("Production Riser", flowline_outlet)
riser.setLength(200)       # 200 m water depth
riser.setDiameter(0.25)
riser.setAngle(90)         # Vertical

process.add(riser)
```

---

## Flow Regimes

### Detect Flow Regime

```python
# After running pipe calculation
pipe.run()

# Get flow pattern (Beggs & Brill)
flow_pattern = pipe.getFlowRegime()
print(f"Flow regime: {flow_pattern}")

# Common regimes:
# - "Segregated" (stratified)
# - "Intermittent" (slug/plug)
# - "Distributed" (bubble/mist)
# - "Transition"
```

### Slug Flow Parameters

```python
# For two-fluid model with slug tracking
pipe = TwoFluidPipe("Slugging Line", inlet_stream)
pipe.setLength(5000)
pipe.setDiameter(0.15)
pipe.setNumberOfSections(200)

# Enable slug tracking (Lagrangian OLGA-style)
pipe.setEnableSlugTracking(True)
pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.LAGRANGIAN)

# Configure Lagrangian tracker for terrain and hydrodynamic slugs
pipe.configureLagrangianSlugTracking(True, True, True)

process.add(pipe)
process.run()

# Check for slugging conditions
print(pipe.getFlowAnalysisSummary())

# Get slug statistics via JSON
print(pipe.getSlugTrackingStatisticsJson())

# Or use the slug summary
print(pipe.getSlugStatisticsSummary())
```

---

## TwoFluidPipe Complete API Reference

A comprehensive reference for the TwoFluidPipe model covering all flow types, boundary conditions, time integration, and advanced features.

### Enumerations

#### BoundaryCondition

| Value | Description |
|-------|-------------|
| `STREAM_CONNECTED` | Use properties from connected inlet stream (default inlet) |
| `CONSTANT_FLOW` | Fixed mass flow rate (set via `setInletMassFlow()`) |
| `CONSTANT_PRESSURE` | Fixed pressure (default outlet) |
| `CLOSED` | No flow — blocked/shut-in; pressure floats |
| `CHARACTERISTIC` | Riemann invariant-based, reduces spurious wave reflections |

#### SlugTrackingMode

| Value | Description |
|-------|-------------|
| `LAGRANGIAN` | Full Lagrangian tracking (OLGA-style) — **default** |
| `SIMPLIFIED` | Simplified slug unit model |
| `DISABLED` | No slug tracking |

#### TimeIntegrator.Method

| Value | Description |
|-------|-------------|
| `EULER` | First-order forward Euler |
| `RK2` | Second-order (Heun) |
| `RK4` | Classical 4th-order Runge-Kutta — **default** |
| `SSP_RK3` | Strong Stability Preserving RK3 |
| `IMEX_PRESSURE_CORRECTION` | Implicit-explicit; allows 10-100× larger dt |

#### InsulationType

| Value | U-Value (W/m²K) |
|-------|-----------------|
| `NONE` | 150.0 |
| `UNINSULATED_SUBSEA` | 25.0 |
| `PU_FOAM` | 10.0 |
| `MULTI_LAYER` | 5.0 |
| `PIPE_IN_PIPE` | 2.0 |
| `VIT` | 0.5 |
| `BURIED_ONSHORE` | 3.0 |
| `EXPOSED_ONSHORE` | 75.0 |

### Profile Getters

| Method | Unit | Description |
|--------|------|-------------|
| `getPressureProfile()` | Pa | Pressure at each section |
| `getTemperatureProfile("C")` | °C | Temperature, unit = "K", "C", or "F" |
| `getLiquidHoldupProfile()` | - | Total liquid holdup fraction |
| `getWaterHoldupProfile()` | - | Water holdup fraction |
| `getOilHoldupProfile()` | - | Oil holdup fraction |
| `getWaterCutProfile()` | - | Water in liquid ratio |
| `getGasVelocityProfile()` | m/s | Gas velocity |
| `getLiquidVelocityProfile()` | m/s | Liquid velocity |
| `getOilVelocityProfile()` | m/s | Oil velocity |
| `getWaterVelocityProfile()` | m/s | Water velocity |
| `getOilWaterSlipProfile()` | - | Oil-water slip ratio |
| `getFlowRegimeProfile()` | - | Flow regime per section |
| `getPositionProfile()` | m | Section positions |
| `getWallTemperatureProfile()` | K | Wall temperature |

### Mesh Configuration

| Method | Description |
|--------|-------------|
| `setNumberOfSections(int)` | Uniform mesh with N equal-length sections |
| `setSectionLengths(double[])` | Non-uniform mesh with explicit per-section lengths |
| `generateRefinedMesh(int baseSections, double refinementFactor)` | Auto-refine based on elevation gradient |
| `getSectionLengths()` | Returns per-section lengths (null if uniform) |

### Adaptive Timestepping

| Method | Description |
|--------|-------------|
| `setEnableAdaptiveTimestepping(boolean)` | Enable/disable OLGA-style adaptive dt |
| `setAdaptiveMaxPressure(double)` | Pressure ceiling (bar) — reject step if exceeded |
| `getAdaptiveDtFactor()` | Current dt multiplier (1.0 = full CFL) |
| `isAdaptiveTimesteppingEnabled()` | Query state |

### Time Integration Method

| Method | Description |
|--------|-------------|
| `setTimeIntegrationMethod(TimeIntegrator.Method)` | Select time integrator (RK4, SSP_RK3, RK2, EULER, IMEX_PRESSURE_CORRECTION) |
| `getTimeIntegrationMethod()` | Returns current time integration method |

### Oil-Water Flow Regime (Three-Phase)

Per-section oil-water classification is available via `TwoFluidSection` for three-phase
(gas-oil-water) simulations. The `OilWaterFlowRegimeDetector` runs automatically when
an aqueous phase is present.

| Method (on TwoFluidSection) | Returns | Description |
|-----------------------------|---------|-------------|
| `getOilWaterFlowRegime()` | `OilWaterFlowRegime` | Detected regime (STRATIFIED, DISPERSED_OIL_IN_WATER, etc.) |
| `getOilWaterResult()` | `OilWaterResult` | Full result: regime, viscosity, inversion point, droplet size |
| `isWaterWetting()` | `boolean` | True if water wets the pipe wall (corrosion indicator) |
| `isWaterDropoutRisk()` | `boolean` | True if water may separate and accumulate |
| `getOilWaterInterfacialTension()` | `double` | Oil-water IFT in N/m |
| `setOilWaterInterfacialTension(double)` | — | Override default IFT (0.03 N/m) |
| `getOilWaterDetector()` | `OilWaterFlowRegimeDetector` | Access detector for tuning |
| `setOilWaterDetector(...)` | — | Set custom detector instance |

**OilWaterFlowRegimeDetector tuning:**

| Method | Default | Description |
|--------|---------|-------------|
| `setCriticalWeber(double)` | 1.17 | Hinze critical Weber number for droplet breakup |
| `getCriticalWeber()` | — | Query current value |
| `setInversionConstant(double)` | 0.5 | Decarre-Fabre phase inversion constant |
| `getInversionConstant()` | — | Query current value |

**Standalone usage** (outside TwoFluidPipe):

```python
import jpype
OilWaterFlowRegimeDetector = jpype.JClass(
    "neqsim.process.equipment.pipeline.twophasepipe.closure.OilWaterFlowRegimeDetector")

detector = OilWaterFlowRegimeDetector()
result = detector.detect(
    0.30,     # waterCut (volume fraction)
    2.0,      # mixtureVelocity (m/s)
    800.0,    # rhoOil (kg/m3)
    1025.0,   # rhoWater (kg/m3)
    0.005,    # muOil (Pa.s)
    0.001,    # muWater (Pa.s)
    0.03,     # sigmaOW (N/m)
    0.25,     # pipeDiameter (m)
    0.0       # inclination (radians)
)

print(f"Regime: {result.regime}")
print(f"Water wetting: {result.waterWetting}")
print(f"Effective viscosity: {result.effectiveViscosity:.4f} Pa.s")
print(f"Inversion water fraction: {result.inversionWaterFraction:.3f}")
print(f"Water dropout risk: {result.waterDropoutRisk}")
```

### Steady-State Solver Tuning

| Parameter | Setter | Default | Description |
|-----------|--------|---------|-------------|
| Under-relaxation | `setSteadyStateUnderRelaxation(double)` | 0.5 | Update damping (0 to 1) |
| Flash interval | `setSteadyStateFlashInterval(int)` | 3 | Flash thermodynamics every N iterations |
| Max wall-clock time | `setSteadyStateMaxWallClockTime(double)` | 30 s | Timeout for SS solver |

### Flow Assurance

```python
# Set hydrate/wax appearance temperatures
pipe.setHydrateFormationTemperature(18.0, "C")
pipe.setWaxAppearanceTemperature(35.0, "C")

pipe.run()

# Check risk
print(f"Hydrate risk: {pipe.hasHydrateRisk()}")
print(f"Wax risk: {pipe.hasWaxRisk()}")
print(f"Distance to hydrate risk: {pipe.getDistanceToHydrateRisk():.0f} m")
print(f"Hydrate cooldown time: {pipe.calculateHydrateCooldownTime():.1f} hours")
print(pipe.getThermalSummary())
```

### Erosion Assessment (API 14E)

```python
pipe.run()

# Check erosional velocity
print(f"Erosional velocity: {pipe.getErosionalVelocity():.2f} m/s")
print(f"Above erosional limit: {pipe.isVelocityAboveErosionalLimit()}")
print(pipe.getErosionRiskAssessment(100.0))  # C-factor = 100
```

---

## Pipeline Network Example

```python
from neqsim import jneqsim

# Imports
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
PipeBeggsAndBrills = jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
Mixer = jneqsim.process.equipment.mixer.Mixer

# Create fluids for two wells
fluid1 = SystemSrkEos(273.15 + 80, 150.0)
fluid1.addComponent("methane", 0.85)
fluid1.addComponent("ethane", 0.10)
fluid1.addComponent("propane", 0.05)
fluid1.setMixingRule("classic")

fluid2 = fluid1.clone()

# Build network
process = ProcessSystem()

# Well 1 stream
well1 = Stream("Well-1", fluid1)
well1.setFlowRate(30000, "kg/hr")
process.add(well1)

# Well 1 flowline
flowline1 = PipeBeggsAndBrills("FL-1", well1)
flowline1.setLength(5000)
flowline1.setDiameter(0.2)
process.add(flowline1)

# Well 2 stream
well2 = Stream("Well-2", fluid2)
well2.setFlowRate(20000, "kg/hr")
process.add(well2)

# Well 2 flowline
flowline2 = PipeBeggsAndBrills("FL-2", well2)
flowline2.setLength(8000)
flowline2.setDiameter(0.2)
process.add(flowline2)

# Manifold
manifold = Mixer("Manifold")
manifold.addStream(flowline1.getOutletStream())
manifold.addStream(flowline2.getOutletStream())
process.add(manifold)

# Export line
export = PipeBeggsAndBrills("Export", manifold.getOutletStream())
export.setLength(25000)
export.setDiameter(0.4)
process.add(export)

# Run
process.run()

# Results
print(f"Manifold P: {manifold.getOutletStream().getPressure():.2f} bara")
print(f"Export outlet P: {export.getOutletStream().getPressure():.2f} bara")
print(f"Total flow: {export.getOutletStream().getFlowRate('kg/hr'):.0f} kg/hr")
```

---

## Pressure Drop Correlations

| Correlation | Best For | NeqSim Class |
|-------------|----------|--------------|
| **Beggs & Brill** | General multiphase, empirical | `PipeBeggsAndBrills` |
| **Two-Fluid** | Detailed transient, slugging, three-phase | `TwoFluidPipe` |
| **Drift-Flux** | Transient, mixture equations | `TransientPipe` |
| **Adiabatic** | Single phase, quick estimates | `AdiabaticPipe` |
| **One-Phase** | Gas or liquid, multi-leg | `OnePhasePipeLine` |

---

## See Also

- **[Pipeline Documentation](../process/equipment/pipelines)** - Detailed pipeline guide
- **[Multiphase Transient Model](../wiki/multiphase_transient_model)** - Drift-flux equations and numerics
- **[Beggs and Brill](../wiki/beggs_and_brill_correlation)** - Correlation details
- **[Two-Fluid Model](../wiki/two_fluid_model)** - Advanced two-fluid equations
- **[TwoFluidPipe Tutorial](../examples/TwoFluidPipe_Tutorial)** - Complete tutorial with slug tracking
- **[Pipeline Network Example](../examples/LoopedPipelineNetworkExample)** - Complex network modeling
- **[Dynamic Simulation Guide](../simulation/dynamic_simulation_guide)** - Transient simulation concepts
- **[JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)** - Complete reference
