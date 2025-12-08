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

Two detection methods are available:

```java
FlowRegimeDetector detector = new FlowRegimeDetector();

// Default: Mechanistic approach (Taitel-Dukler, Barnea)
detector.setDetectionMethod(FlowRegimeDetector.DetectionMethod.MECHANISTIC);

// Alternative: Minimum slip criterion
detector.setDetectionMethod(FlowRegimeDetector.DetectionMethod.MINIMUM_SLIP);
// or
detector.setUseMinimumSlipCriterion(true);
```

The **minimum slip criterion** selects the flow regime that gives the minimum slip ratio (closest to 1.0), based on the principle that the system tends toward the flow pattern with minimum phase velocity difference.

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

## Integrated System Example: Slug Pipeline to Separator

A complete example demonstrating a slugging pipeline connected to a choke valve and separator with level control is available in:

**Example file:** `examples/neqsim/process/pipeline/SlugPipelineToSeparatorExample.java`

**Test file:** `src/test/java/neqsim/process/equipment/pipeline/SlugPipelineToSeparatorTest.java`

### System Configuration

```
┌─────────────┐     ┌─────────────┐     ┌─────────┐     ┌───────────┐
│  Wellhead   │────▶│  Flowline   │────▶│  Choke  │────▶│ Separator │
│  (Const P)  │     │ TwoFluidPipe│     │  Valve  │     │ (Level    │
│  80 bara    │     │  3 km       │     │         │     │  Control) │
└─────────────┘     └─────────────┘     └─────────┘     └───────────┘
                          │                                    │
                     Low point                            Level
                     (Slugging)                          Controller
```

### Boundary Conditions

- **Pipeline inlet:** Stream-connected (constant pressure from wellhead at 80 bara)
- **Pipeline outlet:** Constant pressure boundary (55 bara, set by choke valve)
- **Separator outlet:** Controlled by PID level controller

### Transient Behavior

The TwoFluidPipe model produces realistic transient dynamics:

| Metric | Observed | Description |
|--------|----------|-------------|
| Outlet flow variation | 577% | Flow decreases from 8.0 to 0.46 kg/s during blowdown |
| Pressure range | 55-58 bara | Outlet pressure stabilizes to boundary value |
| Holdup variation | 0.3 → 0.006 | Pipeline drains liquid during transient |

### Key Code Snippet

```java
// Create TwoFluidPipe with stream-connected inlet
TwoFluidPipe pipeline = new TwoFluidPipe("SubseaFlowline", pipeInlet);
pipeline.setLength(3000.0);  // 3 km
pipeline.setDiameter(0.254); // 10 inch
pipeline.setNumberOfSections(30);

// Set outlet pressure boundary condition
pipeline.setOutletPressure(60.0, "bara");

// Terrain with low point for liquid accumulation
double[] elevations = new double[30];
for (int i = 0; i < 30; i++) {
    double x = (i + 1.0) / 30.0;
    if (x < 0.5) {
        elevations[i] = -35.0 * x / 0.5;  // Downhill to low point
    } else {
        elevations[i] = -35.0 + 85.0 * (x - 0.5) / 0.5;  // Riser to +50m
    }
}
pipeline.setElevationProfile(elevations);

// Choke valve between pipeline and separator
ThrottlingValve choke = new ThrottlingValve("Choke", pipeline.getOutletStream());
choke.setOutletPressure(55.0);  // bara

// Separator with level control
Separator separator = new Separator("InletSeparator");
separator.addStream(choke.getOutletStream());
separator.setInternalDiameter(2.5);
separator.setSeparatorLength(8.0);

// Level controller on liquid outlet valve
ThrottlingValve liquidValve = new ThrottlingValve("LiquidValve", 
    separator.getLiquidOutStream());
LevelTransmitter levelTT = new LevelTransmitter("LT-100", separator);
ControllerDeviceBaseClass levelController = new ControllerDeviceBaseClass("LIC-100");
levelController.setTransmitter(levelTT);
levelController.setControllerSetPoint(0.50);  // 50% level
levelController.setControllerParameters(1.5, 180.0, 15.0);  // Kp, Ti, Td
liquidValve.setController(levelController);

// Build process system and run transient
ProcessSystem process = new ProcessSystem();
process.add(pipeInlet);
process.add(pipeline);
process.add(choke);
process.add(separator);
process.add(liquidValve);

process.run();  // Initial steady-state

// Transient simulation
UUID simId = UUID.randomUUID();
for (int step = 0; step < 150; step++) {
    process.runTransient(2.0, simId);
    
    double pipeOutFlow = pipeline.getOutletStream().getFlowRate("kg/sec");
    double level = separator.getLiquidLevel();
    // Track slug arrivals, level variations, etc.
}
```

### Physical Scenario

The example simulates:
1. **Terrain-induced slugging:** Liquid accumulates in the pipeline low point and periodically releases as slugs
2. **Transient blowdown:** Pipeline drains from initial high holdup state to steady flow
3. **Choke valve operation:** Reduces pressure from ~60 bara (pipeline outlet) to 55 bara (separator)
4. **Level control:** PID controller adjusts liquid outlet valve to absorb flow variations
5. **Outlet flow dynamics:** Mass flow at pipeline outlet varies as the system reaches equilibrium

## Validation Against Published Data

The TwoFluidPipe model has been validated against established correlations and published experimental data to ensure physically correct pressure drop predictions.

### Validation Test Suite

The validation test suite is implemented in `TwoPhasePressureDropValidationTest.java` and includes:

1. **Beggs & Brill (1973) Comparison** - Validation against the widely-used empirical correlation
2. **Lockhart-Martinelli (1949) Consistency** - Cross-check with the classic two-phase multiplier method
3. **Industrial-Scale Pipeline Tests** - Verification for typical North Sea conditions
4. **Inclination Effects** - Validation of gravity effects for uphill/downhill flow

### Validation Results

Comparison of TwoFluidPipe against Beggs & Brill (1973) test cases:

| Test Case | D (mm) | L (m) | B&B ΔP (bar) | TFP ΔP (bar) | Ratio |
|-----------|--------|-------|--------------|--------------|-------|
| Horizontal Segregated | 100 | 500 | 0.844 | 0.587 | 0.70 |
| Horizontal Intermittent | 100 | 500 | 2.211 | 2.253 | 1.02 |
| Horizontal Distributed | 100 | 500 | 1.979 | 4.127 | 2.09 |
| Uphill 10° | 100 | 500 | 3.688 | 3.700 | 1.00 |
| Downhill 10° | 100 | 500 | -0.850 | -1.013 | 1.19 |

**Key Observations:**
- **Horizontal Intermittent & Uphill**: Excellent agreement (ratio ~1.0)
- **Downhill**: Good agreement (ratio 1.19) - both models correctly predict pressure gain
- **Horizontal Segregated**: TwoFluidPipe predicts 30% lower (ratio 0.70)
- **Horizontal Distributed**: TwoFluidPipe predicts ~2x higher (ratio 2.09)

The differences are expected because:
- **TwoFluidPipe** uses a mechanistic two-fluid model solving conservation equations
- **Beggs & Brill** uses empirical correlations fitted to 1" and 1.5" pipe experiments
- Differences up to 50% are typical between mechanistic and empirical approaches

### Physical Validation

The model correctly captures the following physical behaviors:

| Physical Effect | Expected Behavior | Model Result |
|-----------------|-------------------|--------------|
| Pressure drop vs GLR | Increases with gas-liquid ratio | ✓ Verified |
| Uphill flow | Higher ΔP (gravity opposes) | ✓ Verified |
| Downhill flow | Negative ΔP (pressure gain) | ✓ Verified |
| Hydrostatic head | Proportional to sin(θ) | ✓ Verified |
| Friction loss | Increases with velocity² | ✓ Verified |

### Running Validation Tests

To run the validation tests:

```bash
# Run all two-phase pressure drop validation tests
./mvnw test -Dtest=TwoPhasePressureDropValidationTest

# Run specific validation test
./mvnw test -Dtest=TwoPhasePressureDropValidationTest#testTwoFluidPipeValidation
```

### Comparison with PipeBeggsAndBrills

For applications where empirical accuracy is preferred over mechanistic modeling, NeqSim also provides `PipeBeggsAndBrills` which implements the original Beggs & Brill correlation with the Payne et al. (1979) corrections.

| Feature | TwoFluidPipe | PipeBeggsAndBrills |
|---------|--------------|-------------------|
| Approach | Mechanistic (conservation eqs) | Empirical (correlations) |
| Flow regimes | Computed from physics | Correlated flow map |
| Transient capability | Yes | Steady-state only |
| Heat transfer | Configurable | Built-in |
| Terrain effects | Elevation profile | Single angle |
| Best for | Transient, complex terrain | Quick steady-state |

## References

1. Bendiksen, K.H. et al. (1991) - "The Dynamic Two-Fluid Model OLGA: Theory and Application", SPE Production Engineering
2. Taitel, Y. and Dukler, A.E. (1976) - "A Model for Predicting Flow Regime Transitions in Horizontal and Near Horizontal Gas-Liquid Flow", AIChE Journal
3. Issa, R.I. and Kempf, M.H.W. (2003) - "Simulation of Slug Flow in Horizontal and Nearly Horizontal Pipes with the Two-Fluid Model", Int. J. Multiphase Flow
4. Liou, M.S. (1996) - "A Sequel to AUSM: AUSM+", J. Computational Physics

## Test Coverage

The model includes comprehensive unit tests:

### Core Tests
- Closure relations: 14 tests
- Numerical methods: 11 tests  
- Core solver: 14 tests
- Thermodynamic coupling: 40 tests
- Three-phase extension: 28 tests
- Integration tests: 19 tests
- Temperature/heat transfer: 16 tests

### Validation Tests
- Beggs & Brill (1973) validation: 5 test cases
- Lockhart-Martinelli (1949) consistency: 3 test cases
- TwoFluidPipe vs B&B comparison: 5 test cases
- Industrial-scale pipelines: 3 test cases
- Inclination effects: 9 test cases
- GLR sensitivity: 6 test cases
- Flow regime detection: 25 test cases

### Integration Tests (SlugPipelineToSeparatorTest)
- Slug pipeline to separator system: Full integration test
- Constant inlet pressure boundary: Boundary condition validation
- Pipeline outlet variations: Transient dynamics verification (577% flow variation)

### Temperature Comparison Tests (TemperatureDropComparisonTest)
- Basic temperature profile initialization
- Temperature monotonicity in cooling
- TwoFluidPipe vs PipeBeggsAndBrills comparison
- Uphill pipeline temperature behavior
- Reproducibility across runs
- Flow rate sensitivity
- Physical bounds validation

**Total: 160+ tests**
