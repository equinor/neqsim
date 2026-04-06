---
title: TwoFluidPipe Model Documentation
description: The NeqSim `TwoFluidPipe` model implements a transient two-fluid multiphase flow solver for pipeline and riser simulations. It solves separate conservation equations for gas and liquid phases, enablin...
---

# TwoFluidPipe Model Documentation

## Overview

The NeqSim `TwoFluidPipe` model implements a transient two-fluid multiphase flow solver for pipeline and riser simulations. It solves separate conservation equations for gas and liquid phases, enabling accurate prediction of:

- Liquid holdup and accumulation
- Pressure drop along the pipeline
- Flow regime transitions
- Terrain-induced effects (slugging, liquid fallback)
- Heat transfer and temperature profiles

This document provides comprehensive documentation of the model's capabilities, governing equations, and usage.

## Conservation Equations

### Mass Conservation
Separate mass conservation equations for gas and liquid phases:

| Equation | Mathematical Form | Description |
|----------|-------------------|-------------|
| Gas mass | ∂(αG ρG)/∂t + ∂(αG ρG vG)/∂x = ΓG | Gas phase continuity with mass transfer |
| Liquid mass | ∂(αL ρL)/∂t + ∂(αL ρL vL)/∂x = -ΓG | Liquid phase continuity with mass transfer |
| Mass transfer ΓG | Flash-based calculation | Evaporation/condensation with optional kinetic limits |

Where:
- αG, αL = Gas and liquid volume fractions (holdup)
- ρG, ρL = Phase densities [kg/m³]
- vG, vL = Phase velocities [m/s]
- ΓG = Mass transfer rate [kg/(m³·s)]

### Momentum Conservation
Separate momentum equations for each phase:

| Component | Implementation |
|-----------|----------------|
| Gas momentum | Full 1D momentum with wall shear, interfacial shear, pressure gradient |
| Liquid momentum | Full 1D momentum with wall shear, interfacial shear, pressure gradient |
| Wall friction | Pipe roughness-based (Colebrook/Blasius correlations) |
| Interfacial friction | Flow-regime dependent correlations |

### Energy Conservation

| Feature | Description |
|---------|-------------|
| Mixture energy equation | Full energy balance including kinetic and potential terms |
| Joule-Thomson effect | Enabled by default for accurate temperature prediction |
| Multi-layer heat transfer | RadialThermalLayer and MultilayerThermalCalculator classes |

## Flow Regime Detection

### Gas-Liquid Flow Regimes

The gas-liquid flow regime detector uses Taitel-Dukler transitions:

| Regime | Detection Criteria | Status |
|--------|-------------------|--------|
| STRATIFIED_SMOOTH | Low gas velocity, stable interface | ✅ |
| STRATIFIED_WAVY | Kelvin-Helmholtz instability criterion | ✅ |
| SLUG | Liquid bridging criterion | ✅ |
| ANNULAR | Weber number > 30 | ✅ |
| CHURN | Transition between slug and annular | ✅ |
| BUBBLE | High liquid fraction, low gas velocity | ✅ |

### Oil-Water Flow Regime Detection

For three-phase (gas-oil-water) simulations the `OilWaterFlowRegimeDetector` classifies the
liquid-phase configuration at every pipe section. This is critical for corrosion prediction
(water wetting), effective viscosity calculation, and water dropout risk assessment.

Based on Trallero (1995), Brauner (2003), and Angeli & Hewitt (2000):

| Regime | Condition | Description |
|--------|-----------|-------------|
| `STRATIFIED` | $v_m < 0.1\,v_{crit}$ | Separate oil and water layers |
| `STRATIFIED_WITH_MIXING` | $0.1\,v_{crit} < v_m < 0.5\,v_{crit}$ | Stratified with interfacial mixing zone |
| `DISPERSED_OIL_IN_WATER` | $v_m > v_{crit}$ and $w_c > w_{inv}$ | Oil droplets in continuous water |
| `DISPERSED_WATER_IN_OIL` | $v_m > v_{crit}$ and $w_c < w_{inv}$ | Water droplets in continuous oil |
| `DUAL_DISPERSION` | $v_m \approx v_{crit}$ and $w_c \approx w_{inv}$ | Both O/W and W/O regions coexist |
| `ANNULAR` | High velocity, large density difference | Oil core with water annulus or vice versa |
| `SINGLE_PHASE` | $w_c < 0.005$ or $w_c > 0.995$ | Only oil or only water present |

Key calculations:

- **Phase inversion** (Decarre & Fabre, 1997): water fraction at which continuous phase switches
- **Critical dispersion velocity** (Brauner, 2003): minimum velocity for full turbulent dispersion
- **Maximum droplet diameter** (Hinze, 1955): $d_{max} = \text{We}_{crit}^{3/5} \sigma^{3/5} / (\rho_c^{3/5} \epsilon^{2/5})$
- **Effective emulsion viscosity**: Brinkman correlation for the dispersed/continuous mixture
- **Water dropout risk**: flags sections where water may separate and accumulate

#### Per-Section Access

Each `TwoFluidSection` exposes the oil-water results:

| Method | Returns | Description |
|--------|---------|-------------|
| `getOilWaterFlowRegime()` | `OilWaterFlowRegime` | Detected regime for this section |
| `getOilWaterResult()` | `OilWaterResult` | Full result (regime, viscosity, inversion, droplet size, etc.) |
| `isWaterWetting()` | `boolean` | True if water wets the pipe wall (corrosion risk) |
| `isWaterDropoutRisk()` | `boolean` | True if water may separate and accumulate |
| `getOilWaterInterfacialTension()` | `double` | Oil-water IFT (N/m) |
| `setOilWaterInterfacialTension(double)` | — | Override IFT (default: 0.03 N/m) |
| `getOilWaterDetector()` | `OilWaterFlowRegimeDetector` | Access the detector for tuning |
| `setOilWaterDetector(...)` | — | Set custom detector instance |

#### Tuning the Detector

```java
OilWaterFlowRegimeDetector detector = section.getOilWaterDetector();
detector.setCriticalWeber(1.17);   // Hinze criterion (default 1.17)
detector.setInversionConstant(0.5); // Decarre-Fabre constant (default 0.5)
```

## Holdup Correlations

### Minimum Holdup Configuration

The model enforces a minimum liquid holdup to prevent unrealistically low values in gas-dominant systems. By default, an **adaptive minimum** is used that scales with the no-slip holdup, making it suitable for both lean gas and rich condensate systems.

#### Configuration Methods

| Method | Default | Description |
|--------|---------|-------------|
| `setUseAdaptiveMinimumOnly(boolean)` | `true` | Use correlation-based minimum only |
| `setMinimumLiquidHoldup(double)` | 0.001 | Absolute floor (when adaptive-only = false) |
| `setMinimumSlipFactor(double)` | 2.0 | Multiplier for no-slip holdup |
| `setEnforceMinimumSlip(boolean)` | `true` | Enable/disable minimum constraint |

#### Lean Gas Systems

For lean wet gas (< 1% liquid loading), use adaptive-only mode:

```java
pipe.setUseAdaptiveMinimumOnly(true);  // Default
pipe.setMinimumSlipFactor(2.0);
// Minimum holdup = lambdaL × 2.0 = 0.6% for 0.3% liquid loading
```

#### Rich Condensate Systems

For rich gas condensate (> 5% liquid loading), either mode works:

```java
// Option 1: Adaptive (recommended)
pipe.setUseAdaptiveMinimumOnly(true);

// Option 2: Fixed floor (OLGA-style)
pipe.setUseAdaptiveMinimumOnly(false);
pipe.setMinimumLiquidHoldup(0.01);  // 1% floor
```

#### Minimum Holdup Correlations

The adaptive minimum uses Beggs-Brill type correlations:

| Flow Regime | Correlation | Exponents |
|-------------|-------------|-----------|
| Stratified | αL = 0.98 × λL^0.4846 / Fr^0.0868 | Segregated flow |
| Slug/Churn | αL = 0.845 × λL^0.5351 / Fr^0.0173 | Intermittent flow |
| Annular | Film model + 1.065 × λL^0.5824 / Fr^0.0609 | Distributed flow |

Where λL = no-slip liquid holdup, Fr = Froude number = v²/(g×D)

### Stratified Flow Holdup
The `calculateStratifiedHoldupMomentumBalance()` method calculates liquid holdup from momentum balance:

```
Holdup = f(τwG, τwL, τi, ∂P/∂x, geometry)
```

Implementation features:
- Taitel-Dukler geometric relationships for gas-liquid interface
- Wall shear stress from friction factors (Colebrook/Blasius)
- Interfacial shear from gas-liquid velocity difference
- Iterative solution for equilibrium liquid level

### Velocity-Dependent Slip Model
The model captures liquid accumulation at low velocities using Froude number correlation:

```java
// Slip ratio as function of mixture Froude number
double baseSlip = 3.0;
double maxSlip = 25.0;
double exponent = 0.85;
double slip = baseSlip + (maxSlip - baseSlip) * Math.exp(-exponent * Frm);
```

| Parameter | Value | Physical Meaning |
|-----------|-------|------------------|
| baseSlip | 3.0 | Minimum slip at high velocity |
| maxSlip | 25.0 | Maximum slip at near-zero velocity |
| exponent | 0.85 | Velocity sensitivity factor |

## Terrain Tracking

### Terrain Effects Model
The `applyTerrainAccumulation()` method implements terrain-induced multiphase flow effects:

#### 1. Low Point Liquid Accumulation
Uses Froude number criterion (Fr < 0.5 indicates accumulation):
```java
double Fr_liquid = vL / Math.sqrt(g * diameter * (rhoL - rhoG) / rhoL);
if (Fr_liquid < 0.5) {
    // Calculate accumulated volume based on velocity deficit
}
```

#### 2. Riser Base Severe Slugging
Detects severe slugging potential using Pots criterion:
```java
double pi_ss = (inletPressure - outletPressure) / (rhoL * g * riserHeight);
if (pi_ss > 1.0) {
    // Severe slugging potential flagged
}
```

#### 3. Uphill Liquid Fallback
Uses Turner droplet model for critical gas velocity:
```java
double vG_critical = 3.0 * Math.pow(sigma * g * (rhoL - rhoG) / (rhoG * rhoG), 0.25);
if (vG < vG_critical) {
    // Liquid fallback occurs
}
```

#### 4. Downhill Drainage
```java
double drainageRate = Math.sqrt(2 * g * dz * holdup);
```

## Multi-Layer Thermal Model

### New Classes
1. **RadialThermalLayer** - Represents a single thermal layer with material properties
2. **MultilayerThermalCalculator** - Calculates U-value and transient heat transfer

### Supported Layer Materials

| Material | k [W/(m·K)] | ρ [kg/m³] | Cp [J/(kg·K)] |
|----------|-------------|-----------|---------------|
| Carbon Steel | 50.0 | 7850 | 480 |
| FBE Coating | 0.3 | 1400 | 1000 |
| PU Foam | 0.035 | 80 | 1500 |
| Syntactic Foam | 0.15 | 650 | 1100 |
| Aerogel | 0.015 | 150 | 1000 |
| Concrete | 1.4 | 2400 | 880 |

### Usage Example
```java
TwoFluidPipe pipe = new TwoFluidPipe("subsea-export", inletStream);
pipe.setLength(20000.0); // 20 km
pipe.setDiameter(0.254); // 10 inch
pipe.setWallThickness(0.015);
pipe.setSurfaceTemperature(4.0, "C"); // Cold seabed

// Configure with 50mm PU foam + 40mm concrete
pipe.configureSubseaThermalModel(0.050, 0.040,
    RadialThermalLayer.MaterialType.PU_FOAM);

// Set hydrate formation temperature
pipe.setHydrateFormationTemperature(20.0, "C");

// Calculate cooldown time
double cooldownHours = pipe.calculateHydrateCooldownTime();
System.out.printf("Cooldown to hydrate: %.1f hours%n", cooldownHours);

// Run simulation
pipe.run();

// Get thermal summary
System.out.println(pipe.getThermalSummary());
```

### Thermal Calculations
- **Overall U-value**: Based on series thermal resistance through all layers
- **Transient response**: Explicit finite-difference with thermal mass in each layer
- **Cooldown time**: Lumped capacitance approximation for shutdown scenarios

## Model Capabilities Summary

| Category | Feature | Method/Correlation |
|----------|---------|--------------------|
| **Conservation Equations** |
| Gas mass | Full continuity equation | Flash-based mass transfer |
| Liquid mass | Full continuity equation | Flash-based mass transfer |
| Gas momentum | 1D momentum balance | Wall and interfacial shear |
| Liquid momentum | 1D momentum balance | Wall and interfacial shear |
| Mixture energy | Full energy balance | Optional J-T effect |
| **Closure Models** |
| Stratified holdup | Momentum balance | Taitel-Dukler geometry |
| Annular holdup | Film model | Ishii-Mishima entrainment |
| Slug holdup | Empirical correlation | Dukler correlation |
| Interfacial friction | Flow-regime specific | Multiple correlations |
| **Oil-Water Models** |
| Oil-water flow regime | OilWaterFlowRegimeDetector | Trallero/Brauner/Angeli classification |
| Phase inversion | Decarre-Fabre (1997) | Viscosity/density-ratio model |
| Emulsion viscosity | Brinkman correlation | Continuous/dispersed mixture |
| Water wetting | Per-section detection | Corrosion risk indicator |
| Water dropout | Velocity/holdup criterion | Accumulation risk flag |
| **Terrain Effects** |
| Low point accumulation | Froude criterion | Fr < 0.5 triggers accumulation |
| Riser base slugging | Pots criterion | πSS > 1.0 indicates severe slugging |
| Uphill fallback | Turner model | Critical gas velocity check |
| **Thermal Model** |
| Multi-layer heat transfer | Series resistance | RadialThermalLayer class |
| Cooldown calculation | Lumped capacitance | MultilayerThermalCalculator |
| Hydrate/wax risk | Temperature tracking | Section-by-section monitoring |
| **Numerical Methods** |
| Time stepping | CFL-based | RK4 (default), IMEX, adaptive dt |
| Spatial discretization | Finite volume | AUSM+ flux splitting, MUSCL reconstruction |
| Mesh | Uniform or non-uniform | `generateRefinedMesh()` or `setSectionLengths()` |

## Spatial Discretization

### Uniform Mesh (default)

`setNumberOfSections(N)` creates N equal-length cells: $dx = L / N$.

### Non-Uniform Mesh

Two approaches for variable cell sizes along the pipe:

**Automatic refinement** — `generateRefinedMesh(baseSections, refinementFactor)` analyses
the elevation profile and creates shorter cells where the elevation gradient is steepest
(risers, S-bends) and longer cells where the pipe is flat (flowlines):

$$
\text{density}_i = 1 + (\text{factor} - 1) \cdot \frac{|\nabla z|_i}{\max |\nabla z|}
$$

Section lengths are inversely proportional to density, then normalized to sum to $L$.
The `refinementFactor` (clamped to 1.5–10) controls the coarsest/finest cell ratio.

**Manual** — `setSectionLengths(double[])` sets explicit per-section lengths (must sum to
total pipe length, minimum 2 sections).

All finite-volume calculations use per-section lengths:

| Component | Non-uniform treatment |
|-----------|-----------------------|
| AUSM+ flux assembly | $-\frac{1}{dx_i}(F_{i+1/2} - F_{i-1/2})$ |
| Pressure gradient | Non-uniform central difference: $dx_c = \frac{1}{2} dx_{i-1} + dx_i + \frac{1}{2} dx_{i+1}$ |
| CFL timestep | $\Delta t = \min_i \left( \text{CFL} \cdot dx_i / c_i \right)$ |
| Temperature updates | Per-section exponential decay and advection |
| Pressure reconstruction | Forward/backward march with per-section $dx$ |

## Time Integration

### Methods

Select the time integration method via `setTimeIntegrationMethod(TimeIntegrator.Method)`:

| Method | CFL constraint | Description |
|--------|---------------|-------------|
| `RK4` (default) | Acoustic ($c + v$) | Classical 4th-order Runge-Kutta. Stable for all geometries. |
| `SSP_RK3` | Acoustic | Strong Stability Preserving RK3 |
| `RK2` | Acoustic | Heun's method (2nd order) |
| `EULER` | Acoustic | Forward Euler (1st order) |
| `IMEX_PRESSURE_CORRECTION` | Convective only | Semi-implicit; ~10x larger dt. Not recommended for vertical risers. |

```java
pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);       // default
pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION); // semi-implicit
TimeIntegrator.Method current = pipe.getTimeIntegrationMethod(); // query
```

### Adaptive Timestepping

OLGA-style adaptive timestepping provides robustness for challenging geometries. Enable via
`setEnableAdaptiveTimestepping(true)`.

Algorithm per macro-step:
1. **CFL recompute** from current velocities (not fixed at initialization)
2. **Pre-check**: reject if NaN or negative mass detected; rollback state, halve `dtFactor`
3. **Post-check**: reject if pressure exceeds ceiling or velocities exceed 500 m/s
4. **Recovery**: after each stable step, `dtFactor` grows by x1.02 back toward 1.0
5. **Floor**: `dtFactor` cannot go below 0.001 to prevent stalling

### Steady-State Solver Tuning

The initial steady-state solve iterates between the transient solver and thermodynamic flashes
until convergence. Three parameters control this:

| Parameter | Setter | Default | Description |
|-----------|--------|---------|-------------|
| Under-relaxation | `setSteadyStateUnderRelaxation(double)` | 0.5 | Update damping factor (0–1); lower = more damping, more stable |
| Flash interval | `setSteadyStateFlashInterval(int)` | 3 | Re-flash thermodynamics every N iterations; higher = faster but less accurate |
| Max wall-clock time | `setSteadyStateMaxWallClockTime(double)` | 30 s | Timeout for the SS solver; prevents runaway iterations |

```java
pipe.setSteadyStateUnderRelaxation(0.3);   // More conservative damping
pipe.setSteadyStateFlashInterval(5);       // Flash every 5th iteration
pipe.setSteadyStateMaxWallClockTime(60.0); // Allow 60 seconds
```

## Validation Status

### Implemented Tests

#### Integration Tests (TwoFluidPipeIntegrationTest)
- `testVelocityDependentLiquidAccumulation` - Verifies holdup increases at low velocity
- `testMultilayerThermalModel` - U-value calculation and layer configuration
- `testCooldownTimeCalculation` - Hydrate cooldown time estimation
- `testBareVsInsulatedPipeThermal` - Comparison of thermal configurations

#### Validation Tests (TwoFluidPipeValidationTest)

**Beggs-Brill Correlation Comparison:**
- `testHorizontalPipeHoldupVsBeggsBrill` - Compares TwoFluidPipe with PipeBeggsAndBrills holdup
- `testUphillPipeHoldup` - Validates increased holdup due to gravity in uphill flow
- `testPressureDropComparison` - Compares pressure drop predictions between models

**Pipeline Scenario Validation:**
- `testOVIPCase1HorizontalGasCondensate` - 2km horizontal pipe, gas-condensate, 6-inch
- `testOVIPCase2UphillRiserAccumulation` - 500m vertical riser, riser base accumulation
- `testTerrainTrackingLowPointAccumulation` - V-shaped terrain with 30m dip
- `testVelocityEffectOnHoldup` - High vs low velocity holdup comparison

**Terrain-Induced Slugging Patterns:**
- `testSevereSlugConditions` - Flowline + 200m riser, severe slugging (Pots criterion)
- `testHillyTerrainMultipleLowPoints` - Sinusoidal terrain ±20m, 3 low points
- `testDownhillDrainage` - 50m downhill slope, liquid drainage validation

### Test Coverage Summary

| Test Category | Tests | Status |
|--------------|-------|--------|
| Integration Tests | 24 | ✅ All passing |
| Validation Tests | 13 | ✅ All passing |
| **Total** | **37** | **✅ All passing** |

## References

1. Bendiksen, K.H., Maines, D., Moe, R., & Nuland, S. (1991). "The Dynamic Two-Fluid Model OLGA: Theory and Application." SPE Production Engineering, 6(02), 171-180.

2. Taitel, Y., & Dukler, A.E. (1976). "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.

3. Pots, B.F.M., Bromilow, I.G., & Konijn, M.J.W.F. (1987). "Severe Slug Flow in Offshore Flowline/Riser Systems." SPE Production Engineering, 2(04), 319-324.

4. Turner, R.G., Hubbard, M.G., & Dukler, A.E. (1969). "Analysis and Prediction of Minimum Flow Rate for the Continuous Removal of Liquids from Gas Wells." Journal of Petroleum Technology, 21(11), 1475-1482.

5. Bai, Y., & Bai, Q. (2010). "Subsea Pipelines and Risers." Elsevier. Chapter on Thermal Design.

6. Beggs, H.D. & Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes." Journal of Petroleum Technology, SPE-4007-PA.
