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

## Holdup Model Configuration

### Minimum Holdup Constraints

The model applies a minimum liquid holdup constraint to prevent unrealistically low values in gas-dominant systems. This is based on OLGA's observation that even at high velocities, a thin liquid film remains on the pipe wall.

**Default behavior (adaptive minimum):**

By default, `useAdaptiveMinimumOnly = true`, which calculates the minimum holdup from flow correlations (Beggs-Brill type) scaled by the no-slip holdup. This allows very low holdups for lean gas systems:

```java
// Adaptive minimum (default) - good for lean gas
// Minimum holdup = max(lambdaL × slipFactor, correlation-based)
pipe.setUseAdaptiveMinimumOnly(true);   // Default
pipe.setMinimumSlipFactor(2.0);         // Default multiplier
```

**For more conservative OLGA-style behavior:**

```java
// Apply absolute floor in addition to correlation
pipe.setUseAdaptiveMinimumOnly(false);
pipe.setMinimumLiquidHoldup(0.01);  // 1% absolute minimum
```

### Configuration Options

| Method | Default | Description |
|--------|---------|-------------|
| `setUseAdaptiveMinimumOnly(boolean)` | `true` | Use correlation-only minimum (no absolute floor) |
| `setMinimumLiquidHoldup(double)` | 0.001 | Absolute minimum holdup floor (when adaptive-only is false) |
| `setMinimumSlipFactor(double)` | 2.0 | Multiplier for no-slip holdup in adaptive mode |
| `setEnforceMinimumSlip(boolean)` | `true` | Enable/disable minimum slip constraint entirely |

### Example: Lean Gas vs Rich Condensate

```java
// Lean wet gas (0.3% liquid loading) - use adaptive minimum
TwoFluidPipe leanGasPipe = new TwoFluidPipe("LeanGas", inlet);
leanGasPipe.setUseAdaptiveMinimumOnly(true);  // Allows holdup < 1%
// Expected holdup ~ 0.6% (2× no-slip)

// Rich gas condensate (5% liquid loading) - can use either mode
TwoFluidPipe richPipe = new TwoFluidPipe("RichGas", inlet);
richPipe.setUseAdaptiveMinimumOnly(false);
richPipe.setMinimumLiquidHoldup(0.01);  // 1% floor is reasonable
// Expected holdup ~ 8-15% depending on velocity
```

### Physics Background

The adaptive minimum uses Beggs-Brill type correlations:

- **Stratified flow:** `αL = 0.98 × λL^0.4846 / Fr^0.0868`
- **Slug/Churn flow:** `αL = 0.845 × λL^0.5351 / Fr^0.0173`
- **Annular flow:** Film model with minimum thickness + correlation

Where:
- `λL` = No-slip liquid holdup (input liquid volume fraction)
- `Fr` = Froude number = v²/(g×D)

For lean gas systems with λL = 0.003, the stratified correlation gives αL ≈ 0.007 (0.7%), which is more realistic than a fixed 1% floor.

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

The `InterfacialFriction` class calculates the shear stress at the gas-liquid interface, which is a critical closure relation for the two-fluid model momentum equations. The interfacial friction affects the slip between phases and pressure drop distribution.

#### General Formulation

The interfacial shear stress follows the standard form:

```
τ_i = 0.5 × f_i × ρ_G × (v_G - v_L) × |v_G - v_L|
```

Where:
- `f_i` = interfacial friction factor (dimensionless)
- `ρ_G` = gas density (kg/m³)
- `v_G - v_L` = slip velocity (m/s)

The force per unit length appearing in the momentum equations is:

```
F_i = τ_i × S_i
```

Where `S_i` is the interfacial perimeter/width per unit length.

#### Sign Convention

Positive interfacial shear acts to accelerate the liquid and decelerate the gas (when gas is faster than liquid). In the momentum equations:
- **Gas momentum:** loses `τ_i × S_i` (negative term)
- **Liquid momentum:** gains `τ_i × S_i` (positive term)

#### Flow Regime-Specific Correlations

| Flow Regime | Correlation | Reference | Key Features |
|-------------|-------------|-----------|--------------|
| **Stratified Smooth** | Taitel-Dukler | (1976) | Treats interface as smooth wall; Blasius for turbulent: `f = 0.079/Re^0.25` |
| **Stratified Wavy** | Andritsos-Hanratty | (1987) | Wave roughness enhancement: `f_i = f_smooth × (1 + 15√(h_L/D) × (v_G/v_G,t - 1))` |
| **Annular** | Wallis | (1969) | Film-core interaction: `f_i = f_G × (1 + 300δ/D)` where δ is film thickness |
| **Slug** | Oliemans | (1986) | Bubble swarm approach with Ishii-Zuber drag coefficient |
| **Bubble/Dispersed** | Schiller-Naumann | - | Drag on individual bubbles: `C_D = (24/Re_b) × (1 + 0.15 × Re_b^0.687)` for Re < 1000 |
| **Churn** | Enhanced Annular | - | Uses annular correlation with 1.5× enhancement factor |

#### Stratified Smooth Flow (Taitel-Dukler 1976)

For smooth stratified flow, the interface is treated as a smooth wall with gas-side friction:

```java
// Gas-side Reynolds number
Re_G = ρ_G × |v_slip| × D_G / μ_G

// Friction factor
if (Re_G < 2300):
    f_i = 16 / Re_G           // Laminar
else:
    f_i = 0.079 / Re_G^0.25   // Blasius (turbulent)
```

#### Stratified Wavy Flow (Andritsos-Hanratty 1987)

Accounts for wave-induced roughness at the interface:

```java
// Transition gas velocity
v_G,t = 5.0 × √(ρ_L / ρ_G)

// Enhancement factor (for v_G > v_G,t)
enhancement = 1.0 + 15 × √(h_L/D) × (v_G/v_G,t - 1)
enhancement = min(enhancement, 20.0)  // Cap

f_i = f_smooth × enhancement
```

#### Annular Flow (Wallis 1969)

For gas-core / liquid-film interaction:

```java
// Film thickness
δ = D/2 × (1 - √(1 - α_L))

// Core diameter
D_core = D - 2δ

// Wallis enhancement
enhancement = 1.0 + 300 × δ/D
enhancement = min(enhancement, 50.0)  // Cap

f_i = f_G × enhancement
```

#### Bubble/Dispersed Flow (Schiller-Naumann)

For drag on individual bubbles in liquid continuum:

```java
// Bubble diameter (Hinze correlation)
d_b = 2 × (0.725 × σ / ((ρ_L - ρ_G) × g))^0.5
d_b = min(d_b, D/5)

// Bubble Reynolds number
Re_b = ρ_L × |v_slip| × d_b / μ_L

// Drag coefficient
if (Re_b < 0.1):
    C_D = 240           // Stokes limit
else if (Re_b < 1000):
    C_D = 24/Re_b × (1 + 0.15 × Re_b^0.687)
else:
    C_D = 0.44          // Newton regime

// Friction factor
f_i = C_D × d_b / (4 × D)
```

#### Usage Example

```java
InterfacialFriction interfacialFriction = new InterfacialFriction();

InterfacialFrictionResult result = interfacialFriction.calculate(
    FlowRegime.STRATIFIED_WAVY,
    gasVelocity,        // m/s
    liquidVelocity,     // m/s
    gasDensity,         // kg/m³
    liquidDensity,      // kg/m³
    gasViscosity,       // Pa·s
    liquidViscosity,    // Pa·s
    liquidHoldup,       // 0-1
    diameter,           // m
    surfaceTension      // N/m
);

double shearStress = result.interfacialShear;           // Pa
double frictionFactor = result.frictionFactor;          // dimensionless
double slipVelocity = result.slipVelocity;              // m/s
double interfacialArea = result.interfacialAreaPerLength;  // m²/m
```

### Oil-Water Interfacial Friction (Three-Phase)

For three-phase gas-oil-water systems, the `ThreeFluidConservationEquations` uses a simplified Froude-based correlation for oil-water interfaces:

```java
// Froude number based on relative velocity
Fr = |v_rel| / √(g × D × |ρ_2 - ρ_1| / ρ_1)

// Simplified correlation
f_i = 0.01 × (1 + 10 × Fr²)    // capped at 0.1
```

This simplified approach is justified because:
- Oil-water density differences are much smaller than gas-liquid (~1.0-1.2 vs 100-1000)
- Slip velocities are typically lower
- Wave formation is less pronounced

The three-layer stratified geometry has two interfaces:

```
    ┌─────────────────┐
    │      Gas        │  ← τ_wall,G + τ_i,GO (gas-oil)
    ├─────────────────┤
    │      Oil        │  ← τ_wall,O + τ_i,GO + τ_i,OW
    ├─────────────────┤
    │     Water       │  ← τ_wall,W + τ_i,OW (oil-water)
    └─────────────────┘
```

Momentum exchange:
- **Gas** gains/loses momentum via gas-oil interface (τ_i,GO)
- **Oil** exchanges momentum with both gas (above) and water (below)
- **Water** gains/loses momentum via oil-water interface (τ_i,OW)

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

## Simulation Modes: Steady-State vs Transient

The `TwoFluidPipe` supports two simulation modes: steady-state initialization via `run()` and incremental transient simulation via `runTransient()`.

### Steady-State Simulation: `run()`

The `run()` method performs a complete steady-state initialization of the pipeline. This is typically called once at the start to establish initial conditions before transient simulation.

**What happens during `run()`:**

```
┌──────────────────────────────────────────────────────────────┐
│                      run(UUID id)                            │
├──────────────────────────────────────────────────────────────┤
│ 1. initializeSections()                                      │
│    ├─ Create pipe sections with uniform spacing (dx)         │
│    ├─ Flash inlet fluid to get phase properties              │
│    ├─ Initialize all sections with inlet conditions          │
│    ├─ Set elevation/inclination from terrain profile         │
│    ├─ Set outlet pressure boundary condition                 │
│    └─ Identify liquid accumulation zones                     │
│                                                              │
│ 2. runSteadyState()                                          │
│    ├─ Iterative solver (max 100 iterations)                  │
│    │   ├─ Update flow regimes for all sections               │
│    │   ├─ Calculate pressure gradient (momentum balance)     │
│    │   ├─ Update local holdups using drift-flux model        │
│    │   │   └─ Account for terrain effects (low points)       │
│    │   ├─ Update phase velocities from mass conservation     │
│    │   ├─ Update oil/water holdups for three-phase flow      │
│    │   └─ Update temperature profile (if heat transfer on)   │
│    └─ Converge when max change < tolerance (1e-4)            │
│                                                              │
│ 3. updateOutletStream()                                      │
│    ├─ Flash outlet fluid at outlet P, T                      │
│    ├─ Calculate outlet mass flow from section state          │
│    └─ Set outlet stream properties                           │
└──────────────────────────────────────────────────────────────┘
```

**Key characteristics:**
- **Fixed inlet conditions:** Uses inlet stream pressure, temperature, composition
- **Iterative convergence:** Pressure and holdup profiles converge simultaneously
- **Terrain-aware holdups:** Liquid accumulates at low points
- **Single call:** Establishes initial state for subsequent transient runs

**Example:**
```java
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inletStream);
pipe.setLength(5000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(100);
pipe.run();  // Steady-state initialization

double[] pressures = pipe.getPressureProfile();
double[] holdups = pipe.getLiquidHoldupProfile();
```

### Transient Simulation: `runTransient(dt, id)`

The `runTransient()` method advances the simulation by a specified time step. It solves the full time-dependent conservation equations and is called repeatedly in a loop.

**What happens during `runTransient(dt, id)`:**

```
┌──────────────────────────────────────────────────────────────┐
│              runTransient(double dt, UUID id)                │
├──────────────────────────────────────────────────────────────┤
│ 1. Calculate stable time step                                │
│    ├─ dt_stable = CFL × dx / max(wave_speed)                 │
│    ├─ dt_actual = min(dt, dt_stable)                         │
│    └─ Determine number of sub-steps                          │
│                                                              │
│ 2. For each sub-step:                                        │
│    ├─ Update thermodynamics (every N steps)                  │
│    │   └─ PT flash at each section P, T                      │
│    │                                                         │
│    ├─ Store previous state U_prev                            │
│    │                                                         │
│    ├─ Time integration (RK4 by default)                      │
│    │   ├─ Calculate RHS of conservation equations            │
│    │   │   ├─ Mass fluxes: ∂(αρuA)/∂x                        │
│    │   │   ├─ Momentum sources: wall friction, interfacial   │
│    │   │   │   friction, gravity, pressure gradient          │
│    │   │   └─ Energy: heat transfer, work terms              │
│    │   └─ Advance state: U_new = U_prev + dt × RHS           │
│    │                                                         │
│    ├─ Validate and correct state                             │
│    │   ├─ Check for NaN/Inf → revert to previous             │
│    │   ├─ Ensure mass ≥ 0                                    │
│    │   └─ Limit rate of change (50% per sub-step max)        │
│    │                                                         │
│    ├─ Apply state to sections                                │
│    │                                                         │
│    ├─ Apply pressure gradient (semi-implicit)                │
│    │                                                         │
│    ├─ Apply boundary conditions                              │
│    │   ├─ Inlet: constant flow or constant pressure          │
│    │   └─ Outlet: constant pressure                          │
│    │                                                         │
│    ├─ Validate section states                                │
│    │   ├─ Fix invalid holdups (NaN, negative)                │
│    │   ├─ Ensure holdup consistency (αL = αO + αW)           │
│    │   └─ Ensure P, T are positive                           │
│    │                                                         │
│    ├─ Update accumulation tracking (if enabled)              │
│    │                                                         │
│    ├─ Update temperature (if heat transfer enabled)          │
│    │                                                         │
│    └─ Advance simulation time                                │
│                                                              │
│ 3. Update outlet stream                                      │
│                                                              │
│ 4. Update result arrays                                      │
└──────────────────────────────────────────────────────────────┘
```

**Key characteristics:**
- **Time-accurate:** Solves full transient PDEs with proper wave speeds
- **CFL-limited:** Automatically sub-steps for numerical stability
- **RK4 integration:** Fourth-order Runge-Kutta for accuracy
- **Robust validation:** Prevents numerical blow-up with state limiting
- **Incremental:** Can be called repeatedly with different time steps

**Example:**
```java
// After steady-state initialization
pipe.run();

// Transient simulation loop
UUID simId = UUID.randomUUID();
for (int step = 0; step < 1000; step++) {
    // Change boundary conditions if needed
    if (step == 100) {
        inletStream.setFlowRate(15.0, "kg/sec");  // Flow increase
        inletStream.run();
    }
    
    pipe.runTransient(0.1, simId);  // Advance 0.1 seconds
    
    // Monitor results
    double outletFlow = pipe.getOutletMassFlow();
    double liquidInventory = pipe.getLiquidInventory("m3");
}
```

### Integration with ProcessSystem

Both methods integrate seamlessly with `ProcessSystem` for coupled simulations:

```java
ProcessSystem process = new ProcessSystem();
process.add(inletStream);
process.add(pipe);
process.add(separator);

// Steady-state initialization
process.run();

// Transient loop
for (int t = 0; t < 300; t++) {
    process.runTransient(1.0, UUID.randomUUID());
}
```

### Comparison Summary

| Aspect | `run()` | `runTransient(dt, id)` |
|--------|---------|------------------------|
| **Purpose** | Initialize steady-state | Advance in time |
| **Call frequency** | Once at start | Repeatedly in loop |
| **Time step** | N/A (iterative) | User-specified + CFL limit |
| **Solver** | Iterative relaxation | Runge-Kutta (RK4) |
| **Equations** | Simplified momentum balance | Full conservation PDEs |
| **Computation** | Moderate | Higher (per call) |
| **Use case** | Initial conditions | Dynamic response |

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

## Terrain-Induced Slug Tracking

The TwoFluidPipe model includes a comprehensive terrain-induced slug tracking system that detects liquid accumulation at terrain low points and tracks the formation, propagation, and arrival of slugs at the outlet.

### Enabling Slug Tracking

```java
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inlet);
pipe.setLength(20000);        // 20 km
pipe.setDiameter(0.3);        // 300 mm
pipe.setNumberOfSections(100);
pipe.setElevationProfile(terrain);

// Enable slug tracking
pipe.setEnableSlugTracking(true);

// Optional: Tune accumulation threshold (default 0.25)
pipe.getAccumulationTracker().setCriticalHoldup(0.35);
```

### How Terrain Slugging Works

The slug tracking system consists of two components working together:

1. **LiquidAccumulationTracker**: Identifies terrain low points and monitors liquid accumulation
2. **SlugTracker**: Tracks individual slugs using Lagrangian tracking with Bendiksen velocity correlation

```
Terrain Profile with Slug Formation:
                                    
    Inlet ─────┐                ┌───── Outlet
               │    Valley      │
               └────────────────┘
                    ▲
                    │
            Liquid accumulates here
            When zone overflows → slug released
```

### Accumulation Zone Detection

The tracker automatically identifies terrain low points where liquid accumulates:

```java
// Get accumulation zones after running
List<AccumulationZone> zones = pipe.getAccumulationTracker().getAccumulationZones();

for (AccumulationZone zone : zones) {
    System.out.println("Zone at position: " + zone.startPosition + " m");
    System.out.println("  Volume: " + zone.liquidVolume + " m³");
    System.out.println("  Max capacity: " + zone.maxVolume + " m³");
    System.out.println("  Fill fraction: " + (zone.liquidVolume / zone.maxVolume));
    System.out.println("  Is overflowing: " + zone.isOverflowing);
}
```

### Slug Statistics

Access comprehensive slug statistics after simulation:

```java
SlugTracker tracker = pipe.getSlugTracker();

// Summary statistics
System.out.println("Slugs generated: " + tracker.getTotalSlugsGenerated());
System.out.println("Slugs merged: " + tracker.getTotalSlugsMerged());
System.out.println("Active slugs: " + tracker.getSlugs().size());
System.out.println("Slugs at outlet: " + pipe.getOutletSlugCount());
System.out.println("Max slug length: " + tracker.getMaxSlugLength() + " m");
System.out.println("Avg slug length: " + tracker.getAverageSlugLength() + " m");
System.out.println("Slug frequency: " + tracker.getSlugFrequency() + " Hz");

// Detailed per-slug information
for (SlugInfo slug : tracker.getSlugs()) {
    System.out.println("Slug #" + slug.id);
    System.out.println("  Position: " + slug.frontPosition + " m");
    System.out.println("  Length: " + slug.slugBodyLength + " m");
    System.out.println("  Volume: " + slug.liquidVolume + " m³");
    System.out.println("  Velocity: " + slug.frontVelocity + " m/s");
    System.out.println("  Age: " + slug.age + " s");
    System.out.println("  Terrain-induced: " + slug.isTerrainInduced);
}
```

### Complete Slug Tracking Example

```java
// Create gas-condensate fluid
SystemInterface fluid = new SystemSrkEos(288.15, 50);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.10);
fluid.addComponent("n-heptane", 0.05);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(15, "kg/sec");
inlet.setTemperature(15, "C");
inlet.setPressure(50, "bara");
inlet.run();

// Create terrain with valleys
double[] terrain = new double[100];
for (int i = 0; i < 100; i++) {
    double x = i * 200.0; // 20 km total
    double xNorm = x / 20000.0;
    terrain[i] = -20.0 * Math.exp(-Math.pow((xNorm - 0.4) / 0.1, 2));
}

TwoFluidPipe pipe = new TwoFluidPipe("SlugPipeline", inlet);
pipe.setLength(20000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(100);
pipe.setElevationProfile(terrain);
pipe.setEnableSlugTracking(true);
pipe.getAccumulationTracker().setCriticalHoldup(0.35);

// Steady-state initialization
pipe.run();

// Transient simulation (2 hours)
UUID id = UUID.randomUUID();
double simTime = 2 * 60 * 60; // 2 hours
double dt = 1.0;
int steps = (int)(simTime / dt);

for (int i = 0; i < steps; i++) {
    pipe.runTransient(dt, id);
    
    // Monitor progress every 15 minutes
    if (i % 900 == 0 && i > 0) {
        System.out.printf("Time: %.0f min, Slugs: %d, Outlet: %d%n",
            i / 60.0,
            pipe.getSlugTracker().getTotalSlugsGenerated(),
            pipe.getOutletSlugCount());
    }
}

// Final report
System.out.println(pipe.getSlugTrackingReport());
```

### Comparison with Drift-Flux Model (TransientPipe)

Both `TwoFluidPipe` and `TransientPipe` use the same slug tracking infrastructure, but predict different slug frequencies due to their underlying physical models:

| Aspect | TwoFluidPipe | TransientPipe |
|--------|--------------|---------------|
| **Physical Model** | 7-equation two-fluid | 4-equation drift-flux |
| **Holdup Prediction** | Lower (mechanistic) | Higher (empirical slip) |
| **Accumulation Rate** | Slower | Faster |
| **Slug Frequency** | Lower | Higher (conservative) |
| **Oil-Water Tracking** | Separate phases | Combined liquid |
| **Computation Time** | ~3x slower | Faster |

**Typical behavior comparison:**

| Condition | TwoFluidPipe | TransientPipe |
|-----------|--------------|---------------|
| Avg liquid holdup | 0.25-0.35 | 0.90-0.95 |
| Zone fill rate | 6-15%/hour | 70-80% quickly |
| Time to first slug | ~2 hours | < 1 minute |
| Slug frequency | 1 per 1-2 hours | 2-3 per minute |

**When to use each:**

- **TwoFluidPipe**: Three-phase systems, oil-water separation important, accurate phase slip needed
- **TransientPipe**: Conservative slug catcher sizing, faster computation, gas-liquid systems

### Tuning Slug Tracking Parameters

```java
// Lower critical holdup → earlier slug initiation
pipe.getAccumulationTracker().setCriticalHoldup(0.20);

// The overflow threshold in LiquidAccumulationTracker determines
// when accumulated liquid is released as a slug
// (set to 20% by default for terrain-induced slugging)
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

## Related Documentation

- [Fluid Mechanics README](../fluidmechanics/README.md) - Low-level pipe flow modeling
- [TwoPhasePipeFlowModel](../fluidmechanics/TwoPhasePipeFlowModel.md) - Non-equilibrium mass/heat transfer
- [TwoPhasePipeFlowSystem Development Plan](../fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md) - Implementation status
- [Pipeline Index](pipeline_index.md) - Overview of all pipeline models
