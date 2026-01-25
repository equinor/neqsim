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

The flow regime detector uses Taitel-Dukler transitions:

| Regime | Detection Criteria | Status |
|--------|-------------------|--------|
| STRATIFIED_SMOOTH | Low gas velocity, stable interface | ✅ |
| STRATIFIED_WAVY | Kelvin-Helmholtz instability criterion | ✅ |
| SLUG | Liquid bridging criterion | ✅ |
| ANNULAR | Weber number > 30 | ✅ |
| CHURN | Transition between slug and annular | ✅ |
| BUBBLE | High liquid fraction, low gas velocity | ✅ |

## Holdup Correlations

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
| **Terrain Effects** |
| Low point accumulation | Froude criterion | Fr < 0.5 triggers accumulation |
| Riser base slugging | Pots criterion | πSS > 1.0 indicates severe slugging |
| Uphill fallback | Turner model | Critical gas velocity check |
| **Thermal Model** |
| Multi-layer heat transfer | Series resistance | RadialThermalLayer class |
| Cooldown calculation | Lumped capacitance | MultilayerThermalCalculator |
| Hydrate/wax risk | Temperature tracking | Section-by-section monitoring |
| **Numerical Methods** |
| Time stepping | CFL-based | Fixed step with sub-cycling |
| Spatial discretization | Finite volume | Upwind scheme |

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
