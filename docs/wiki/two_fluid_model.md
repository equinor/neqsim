# Two-Fluid Transient Multiphase Flow Model

This document describes the two-fluid model implementation in NeqSim for transient multiphase pipeline simulation.

## Overview

The two-fluid model solves separate conservation equations for each phase (gas and liquid), providing more accurate predictions than drift-flux models for:

- **Countercurrent flow** - Phases moving in opposite directions
- **Slug flow dynamics** - Formation and propagation of liquid slugs
- **Terrain-induced liquid accumulation** - Holdup in low points
- **Transient pressure waves** - Fast pressure transients

## Package Structure

```
neqsim.process.equipment.pipeline.twophasepipe/
├── PipeSection.java              # Base section state container
├── TwoFluidSection.java          # Two-fluid section with conservative variables
├── TwoFluidConservationEquations.java  # PDE RHS calculation
├── ThreeFluidSection.java        # Extension for gas-oil-water systems
├── ThreeFluidConservationEquations.java  # Three-phase equations
├── ThermodynamicCoupling.java    # Flash calculation interface
├── FlashTable.java               # Pre-computed property interpolation
├── EntrainmentDeposition.java    # Droplet exchange model
├── FlowRegimeDetector.java       # Flow pattern determination
├── LiquidAccumulationTracker.java  # Low-point detection
├── SlugTracker.java              # Slug tracking and statistics
├── closure/
│   ├── GeometryCalculator.java   # Stratified geometry
│   ├── WallFriction.java         # Wall shear correlations
│   └── InterfacialFriction.java  # Interface shear correlations
└── numerics/
    ├── TimeIntegrator.java       # Runge-Kutta integration
    ├── AUSMPlusFluxCalculator.java  # Flux splitting scheme
    └── MUSCLReconstructor.java   # Higher-order reconstruction
```

## Conservation Equations

The two-fluid model solves the following 1D PDEs:

### Mass Conservation

**Gas phase:**
```
∂/∂t(αg·ρg·A) + ∂/∂x(αg·ρg·ug·A) = Γg
```

**Liquid phase:**
```
∂/∂t(αL·ρL·A) + ∂/∂x(αL·ρL·uL·A) = ΓL
```

Where:
- `αg`, `αL` = Gas and liquid holdups (volume fractions)
- `ρg`, `ρL` = Phase densities
- `ug`, `uL` = Phase velocities  
- `Γ` = Mass transfer rate (evaporation/condensation)
- `A` = Pipe cross-sectional area

### Momentum Conservation

**Gas phase:**
```
∂/∂t(αg·ρg·ug·A) + ∂/∂x(αg·ρg·ug²·A + αg·P·A) = 
    -τwg·Swg - τi·Si + αg·ρg·g·sin(θ)·A
```

**Liquid phase:**
```
∂/∂t(αL·ρL·uL·A) + ∂/∂x(αL·ρL·uL²·A + αL·P·A) = 
    -τwL·SwL + τi·Si + αL·ρL·g·sin(θ)·A
```

Where:
- `τwg`, `τwL` = Wall shear stresses
- `τi` = Interfacial shear stress
- `Swg`, `SwL`, `Si` = Wetted perimeters
- `θ` = Pipe inclination angle

### Energy Conservation (Optional)

```
∂/∂t(E·A) + ∂/∂x((E + P)·um·A) = Q - W
```

### Heat Transfer to Surroundings

The model supports configurable heat transfer from the pipe wall using Newton's law of cooling:

```
Q_wall = U × π × D × (T_surface - T_fluid)  [W/m]
```

Where:
- `U` = Overall heat transfer coefficient [W/(m²·K)]
- `D` = Pipe diameter [m]
- `T_surface` = Ambient/seabed temperature [K]
- `T_fluid` = Fluid mixture temperature [K]

**API:**
```java
pipe.setSurfaceTemperature(5.0, "C");      // Seabed at 5°C
pipe.setHeatTransferCoefficient(25.0);     // 25 W/(m²·K)
```

**Typical U-values:**
| Condition | U [W/(m²·K)] |
|-----------|------------|
| Insulated subsea | 5-15 |
| Uninsulated subsea | 20-30 |
| Buried onshore | 2-5 |
| Exposed onshore | 50-100 |

### Insulation Type Presets

Convenience method for setting heat transfer coefficient based on insulation type:

```java
pipe.setInsulationType(TwoFluidPipe.InsulationType.PU_FOAM);  // 10 W/(m²·K)
```

**Available presets:**
| InsulationType | U [W/(m²·K)] | Description |
|----------------|-------------|-------------|
| `NONE` | 150 | Bare steel in seawater |
| `UNINSULATED_SUBSEA` | 25 | Typical bare subsea pipe |
| `PU_FOAM` | 10 | Standard PU foam insulation |
| `MULTI_LAYER` | 5 | Multi-layer insulation |
| `PIPE_IN_PIPE` | 2 | Pipe-in-pipe system |
| `VIT` | 0.5 | Vacuum insulated tubing |
| `BURIED_ONSHORE` | 3 | Buried onshore pipeline |
| `EXPOSED_ONSHORE` | 75 | Wind-cooled exposed pipe |

### Variable Heat Transfer Profile

Support for different U-values along the pipe (e.g., buried vs exposed sections):

```java
double[] htcProfile = new double[numSections];
for (int i = 0; i < numSections; i++) {
    htcProfile[i] = (i < 10) ? 5.0 : 50.0;  // First 1 km insulated, rest exposed
}
pipe.setHeatTransferProfile(htcProfile);
```

### Soil Thermal Resistance

For buried pipelines, add soil thermal resistance:

```java
pipe.setSoilThermalResistance(0.5);  // m²·K/W
// Effective U = 1 / (1/U + R_soil)
```

### Joule-Thomson Effect

Temperature change from pressure drop (enabled by default):

```java
pipe.setEnableJouleThomson(true);   // Enable J-T cooling
// dT = μ_JT × dP (typical: 0.4 K/bar for natural gas)
```

### Pipe Wall Thermal Mass

For transient simulations, configure pipe wall properties:

```java
pipe.setWallProperties(0.025, 7850.0, 500.0);  // 25mm steel wall
// Parameters: thickness [m], density [kg/m³], heat capacity [J/(kg·K)]
```

### Hydrate and Wax Risk Monitoring

Monitor for flow assurance issues:

```java
pipe.setHydrateFormationTemperature(10.0, "C");
pipe.setWaxAppearanceTemperature(25.0, "C");
pipe.run();

if (pipe.hasHydrateRisk()) {
    int section = pipe.getFirstHydrateRiskSection();
    double distance = pipe.getDistanceToHydrateRisk();
    System.out.println("Hydrate risk at " + distance + " m");
}
```

### Temperature Profile with Units

Get temperature profile in different units:

```java
double[] tempK = pipe.getTemperatureProfile("K");   // Kelvin
double[] tempC = pipe.getTemperatureProfile("C");   // Celsius
double[] tempF = pipe.getTemperatureProfile("F");   // Fahrenheit
```

## Closure Relations

### Flow Regime Detection

The `FlowRegimeDetector` uses Taitel-Dukler maps to identify:
- Stratified smooth/wavy
- Intermittent (slug/plug)
- Annular/mist
- Dispersed bubble
- Churn

### Wall Friction

`WallFriction` calculates wall shear using:
- **Laminar:** `f = 16/Re`
- **Turbulent:** Haaland approximation of Colebrook-White
- **Stratified:** Taitel-Dukler with hydraulic diameters

### Interfacial Friction

`InterfacialFriction` provides correlations for:
- **Stratified:** Shoham-Taitel wavy interface
- **Annular:** Wallis correlation
- **Slug:** Slug body + film

### Stratified Geometry

`GeometryCalculator` computes for stratified flow:
- Liquid level from holdup
- Wetted perimeters (gas, liquid, interface)
- Hydraulic diameters
- Cross-sectional areas

## Numerical Methods

### Spatial Discretization

The `AUSMPlusFluxCalculator` implements AUSM+ flux splitting for:
- Pressure-velocity coupling
- Shock capturing
- Low diffusion

### Temporal Integration

`TimeIntegrator` supports:
- Forward Euler
- RK2 (Heun's method)
- RK4 (Classical 4th order)
- SSPRK3 (Strong stability preserving)

### Higher-Order Reconstruction

`MUSCLReconstructor` provides:
- Piecewise linear reconstruction
- Minmod, van Leer, superbee limiters
- Second-order accuracy in smooth regions

## Thermodynamic Coupling

### Flash Calculations

`ThermodynamicCoupling` interfaces with NeqSim's flash routines:
```java
ThermodynamicCoupling coupling = new ThermodynamicCoupling(referenceFluid);
ThermoProperties props = coupling.flashPT(pressure, temperature);
```

### Flash Tables

`FlashTable` provides fast property lookup via bilinear interpolation:
```java
FlashTable table = new FlashTable();
table.build(fluid, pMin, pMax, nP, tMin, tMax, nT);
ThermoProperties props = table.interpolate(pressure, temperature);
```

## Three-Phase Extension

For gas-oil-water systems, `ThreeFluidSection` and `ThreeFluidConservationEquations` extend the model to 7 equations:
- 3 mass conservation (gas, oil, water)
- 3 momentum conservation
- 1 energy conservation

### Three-Layer Stratified Geometry

```
        ┌─────────────────┐
        │      Gas        │
        ├─────────────────┤  ← Gas-Oil Interface
        │      Oil        │
        ├─────────────────┤  ← Oil-Water Interface  
        │     Water       │
        └─────────────────┘
```

## Usage Example

```java
// Create two-phase fluid
SystemInterface fluid = new SystemSrkEos(300, 50);
fluid.addComponent("methane", 0.85);
fluid.addComponent("n-pentane", 0.15);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

// Create inlet stream
Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(10, "kg/sec");
inlet.run();

// Create two-fluid pipe
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inlet);
pipe.setLength(5000);        // 5 km
pipe.setDiameter(0.3);       // 300 mm
pipe.setNumberOfSections(100);

// Set terrain profile
double[] elevations = new double[100];
for (int i = 0; i < 100; i++) {
    elevations[i] = 50.0 * Math.sin(i * Math.PI / 50);
}
pipe.setElevationProfile(elevations);

// Optional: Configure heat transfer to surroundings
pipe.setSurfaceTemperature(5.0, "C");      // Seabed at 5°C
pipe.setHeatTransferCoefficient(25.0);     // 25 W/(m²·K)

// Run steady-state initialization
pipe.run();

// Transient simulation
UUID id = UUID.randomUUID();
for (int step = 0; step < 1000; step++) {
    pipe.runTransient(0.1, id);  // 0.1 second steps
}

// Get results
double[] pressures = pipe.getPressureProfile();
double[] holdups = pipe.getLiquidHoldupProfile();
double liquidInventory = pipe.getLiquidInventory("m3");
```

## References

1. Bendiksen, K.H. et al. (1991) - "The Dynamic Two-Fluid Model OLGA: Theory and Application", SPE Production Engineering
2. Taitel, Y. and Dukler, A.E. (1976) - "A Model for Predicting Flow Regime Transitions in Horizontal and Near Horizontal Gas-Liquid Flow", AIChE Journal
3. Issa, R.I. and Kempf, M.H.W. (2003) - "Simulation of Slug Flow in Horizontal and Nearly Horizontal Pipes with the Two-Fluid Model", Int. J. Multiphase Flow
4. Liou, M.S. (1996) - "A Sequel to AUSM: AUSM+", J. Computational Physics

## Test Coverage

The model includes comprehensive unit tests:
- Closure relations: 14 tests
- Numerical methods: 11 tests  
- Core solver: 14 tests
- Thermodynamic coupling: 40 tests
- Three-phase extension: 28 tests
- Integration tests: 19 tests
- Temperature/heat transfer: 16 tests

**Total: 142 tests**
