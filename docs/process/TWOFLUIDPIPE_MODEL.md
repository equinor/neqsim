---
title: TwoFluidPipe Model Documentation
description: The NeqSim `TwoFluidPipe` model implements a transient multiphase-flow solver with phase-resolved gas, hydrocarbon-liquid, and aqueous-liquid conservation equations.
---

# TwoFluidPipe Model Documentation

## Overview

The NeqSim `TwoFluidPipe` model implements a transient two-fluid multiphase flow solver for pipeline and riser simulations. It solves phase-resolved gas, hydrocarbon-liquid, and aqueous-liquid conservation equations, enabling prediction of:

- Liquid holdup and accumulation
- Pressure drop along the pipeline
- Flow regime transitions
- Terrain-induced effects (slugging, liquid fallback)
- Heat transfer and temperature profiles

This document provides comprehensive documentation of the model's capabilities, governing equations, and usage.

The selectable closure sets are literature-inspired NeqSim implementations. Historical API names containing `OLGA` are retained for compatibility and do not claim numerical equivalence with OLGA, LedaFlow, or another commercial simulator.

## Conservation Equations

### Mass Conservation
Separate mass conservation equations are solved for gas, hydrocarbon liquid, and aqueous liquid:

| Equation | Mathematical Form | Description |
|----------|-------------------|-------------|
| Gas mass | ∂(A αG ρG)/∂t + ∂(A αG ρG vG)/∂x = ΓG | Gas phase continuity with mass transfer |
| Oil mass | ∂(A αO ρO)/∂t + ∂(A αO ρO vO)/∂x = ΓO | Hydrocarbon-liquid continuity |
| Water mass | ∂(A αW ρW)/∂t + ∂(A αW ρW vW)/∂x = ΓW | Aqueous-liquid continuity |
| Phase transfer | ΓG + ΓO + ΓW = 0 | Flash-based transfer with inventory limits |

Where:
- αG, αO, αW = gas, oil, and water volume fractions (holdup)
- A = pipe cross-sectional area [m²]
- ρG, ρO, ρW = phase densities [kg/m³]
- vG, vO, vW = phase velocities [m/s]
- ΓG, ΓO, ΓW = phase mass sources [kg/(m·s)] in the finite-volume implementation

#### Flash-driven phase identity

`ThermodynamicCoupling` identifies phases by `PhaseType`; it does not assume gas, oil, or aqueous
phases occupy fixed array positions. A gas + oil + aqueous PT flash aggregates both liquid phases
in the equilibrium-liquid target. For condensation, the new liquid is split using equilibrium oil
and aqueous **mass** contributions. The current hydrodynamic water cut is intentionally not used at
phase appearance because a gas-only cell contains no information about the identity of its first
liquid.

For evaporation, oil and water withdrawals are distributed from the actual conservative phase
inventories. Each withdrawal is bounded by `phase mass / relaxation time`, so an absent phase cannot
evaporate and no phase can be removed faster than the relaxation step permits. The immutable
`PhaseMassTransfer` result reports gas, oil, and water sources in kg/(m s), together with flash
convergence and applicability metadata.

Transferred momentum uses donor velocity: condensing gas gives each receiving liquid gas momentum,
while evaporating oil and water give the gas their respective liquid momenta. Transfer-only gas,
oil, and water momentum sources therefore sum to zero. This closure preserves phase and total mass
and mixture momentum, but the current hydrodynamic state still does not transport full component
compositions independently in every cell.

`FlashTable` stores the same aggregate liquid fraction, oil/aqueous liquid mass split, and gas/liquid
molar masses as the rigorous flash path. Interpolated liquid identity fractions are clamped and
renormalized; an identity that is absent at all surrounding grid points remains exactly zero.

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

The default **adaptive minimum** is a closure relation that scales with no-slip holdup and tends continuously to zero as liquid input vanishes. It is not a phase-presence threshold. Exact phase presence comes from the conservative gas, oil, and water masses: an absent phase has zero mass, holdup, and velocity.

#### Configuration Methods

| Method | Default | Description |
|--------|---------|-------------|
| `setUseAdaptiveMinimumOnly(boolean)` | `true` | Use correlation-based minimum only |
| `setMinimumLiquidHoldup(double)` | 0.001 | Optional absolute floor in fixed-floor mode; zero disables it |
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

// Option 2: Explicit calibrated wetting-film floor
pipe.setUseAdaptiveMinimumOnly(false);
pipe.setMinimumLiquidHoldup(0.01);  // 1% floor
```

Fixed-floor mode is opt-in and should be used only when a nonzero wetting film is supported by the fluid, wall-wetting, and flow-regime data. Even in this mode, an exactly absent phase remains exactly absent. `setMinimumLiquidHoldup(0.0)` therefore produces no absolute floor.

#### Minimum Holdup Correlations

The adaptive minimum uses Beggs-Brill type correlations:

| Flow Regime | Correlation | Exponents |
|-------------|-------------|-----------|
| Stratified | αL = 0.98 × λL^0.4846 / Fr^0.0868 | Segregated flow |
| Slug/Churn | αL = 0.845 × λL^0.5351 / Fr^0.0173 | Intermittent flow |
| Annular | Film model + 1.065 × λL^0.5824 / Fr^0.0609 | Distributed flow |

Where λL = no-slip liquid holdup, Fr = Froude number = v²/(g×D)

### Phase Disappearance and Numerical Regularization

`TwoFluidPipe` keeps closure regularization separate from conserved state:

- `1e-14` protects closure denominators only; it is never written as holdup or mass.
- The stratified closure uses a continuous trace-liquid asymptote for λL at or below `1e-6` to avoid singular circular-segment geometry.
- The drift-flux correction is smoothly withdrawn with weight `λL / (λL + 1e-3)` near pure gas, so its two-phase distribution parameters cannot leave a finite liquid intercept.
- Adaptive-minimum and disabled-minimum modes impose no absolute holdup floor. Oil and water split fractions are allowed to reach exact endpoints of zero and one.

These values regularize local constitutive equations; they do not declare that a phase is present. The implementation is literature-inspired and does not claim numerical equivalence with OLGA, LedaFlow, or another commercial simulator.

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

#### 2. Flowline–Riser Severe-Slugging Stability

Severe slugging is a system instability, not a local pipe-section threshold. After solving a
flowline–riser case, call the explicit diagnostic with the index of the first continuously
rising section:

```java
SevereSluggingSystemDiagnostic.Result stability =
    pipe.evaluateSevereSluggingSystem(riserBaseSection);

boolean severeSluggingPossible =
    stability.isApplicable() && stability.isSevereSluggingPossible();
double pressureMarginPa = stability.getPressureMarginPa();
```

The implementation uses the quasi-steady Taitel (1986) condition:

$$
P_{top,crit} = \phi\,\rho_L\,g\left(\frac{V_G}{A_r\,\alpha'} - H\right)
$$

where $P_{top}$ is **absolute** riser-outlet pressure [Pa], $\phi$ is average riser liquid
holdup [-], $\rho_L$ is average riser liquid density [kg/m³], $V_G$ is upstream compressible
gas volume [m³], $A_r$ is riser area [m²], $H$ is vertical riser height [m], and $\alpha'$ is
the gas-cap void fraction [-]. The system is classified stable when
$P_{top} + \Delta P_{choke} \ge P_{top,crit}$. A static choke pressure drop can be provided
to represent one operating point; dynamic choke response is not modelled.

The diagnostic is applicable only to a two-phase, low-rate, stratified flowline followed by
a continuously rising, constant-area riser. It assumes isothermal ideal-gas compression and
neglects wall and interfacial shear during incipient gas penetration. It deliberately returns
a not-applicable status for invalid topology, non-stratified feeders, single-phase states, and
unvalidated oil–water–gas cases. It predicts a stability boundary, not slug frequency, slug
length, or transient cycle amplitude.

The flowline and riser may have different diameters, and each flowline section contributes its
own solved gas volume. Only the rising sections must have constant area. The public
`SevereSluggingSystemDiagnostic.fromSections(...)` factory exposes the extracted descriptor for
unit checking, audit, and reuse outside `TwoFluidPipe`.

The old `getSevereSluggingNumberProfile()` method is retained as a deprecated serialization/API
alias. Its values are the local inclined-section gas-carryover screen now exposed accurately as
`getInclinedSectionGasCarryoverNumberProfile()`; they must not be used as a flowline–riser
stability criterion.
The associated local flag is available from
`getInclinedSectionLiquidFallbackPotentialProfile()`. `getSevereSlugPotentialProfile()` is
reserved for the explicit system result and is cleared by the next transient step.

Reference: Taitel, Y. (1986), *Stability of Severe Slugging*, International Journal of
Multiphase Flow 12(2), 203–217, [doi:10.1016/0301-9322(86)90026-1](https://doi.org/10.1016/0301-9322(86)90026-1).

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
| Gas mass | Full continuity equation | Phase-resolved flash transfer |
| Oil mass | Full continuity equation | Equilibrium mass split / donor inventory |
| Water mass | Full continuity equation | Equilibrium mass split / donor inventory |
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
| Riser-base liquid fallback | Local gas-carryover screen | Indicates possible local fallback only |
| Flowline-riser stability | Taitel (1986) quasi-steady criterion | Explicit topology-aware system diagnostic |
| Uphill fallback | Turner model | Critical gas velocity check |
| **Thermal Model** |
| Multi-layer heat transfer | Series resistance | RadialThermalLayer class |
| Cooldown calculation | Lumped capacitance | MultilayerThermalCalculator |
| Hydrate/wax risk | Temperature tracking | Section-by-section monitoring |
| **Numerical Methods** |
| Time stepping | CFL-based | RK4 (default), IMEX, adaptive dt |
| Spatial discretization | Finite volume | AUSM+ flux splitting, MUSCL reconstruction |
| Mesh | Uniform or non-uniform | `generateRefinedMesh()` or `setSectionLengths()` |

## Steady-State and Dynamic Simulation Workflow

`TwoFluidPipe` supports both steady-state initialization and transient simulation. In normal use,
call `run()` first to build a physically consistent pressure, holdup, temperature, and flow-regime
profile. Then change a boundary condition and advance time with `runTransient(dt, id)` until the
profiles stop changing or match a new steady-state reference.

### Steady-State Simulation

For a fixed inlet stream and a calculated outlet pressure, configure the pipe geometry and call
`run()`:

```java
SystemInterface fluid = new neqsim.thermo.system.SystemSrkEos(293.15, 70.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.06);
fluid.addComponent("propane", 0.04);
fluid.setMixingRule("classic");

Stream inlet = new Stream("inlet", fluid);
inlet.setFlowRate(4.0, "kg/sec");
inlet.setTemperature(20.0, "C");
inlet.setPressure(70.0, "bara");
inlet.run();

TwoFluidPipe pipe = new TwoFluidPipe("export line", inlet);
pipe.setLength(1000.0);
pipe.setDiameter(0.20);
pipe.setRoughness(1.0e-5);
pipe.setNumberOfSections(20);
pipe.run();
```

If the downstream pressure is known, set a constant outlet pressure before `run()`. The steady-state
solver calculates the pressure-gradient shape from flow, friction, gravity, and holdup, then aligns
the absolute profile to the specified pressure boundary:

```java
pipe.setOutletPressure(55.0, "bara");
pipe.run();
double outletPressure = pipe.getPressureProfile()[pipe.getPressureProfile().length - 1] / 1.0e5;
```

### Dynamic Simulation After a Boundary Change

Dynamic simulations should start from a steady state. After changing a boundary condition, run the
transient solver long enough for the new stationary solution to be reached:

```java
pipe.run();
pipe.setOutletPressure(52.0, "bara");

UUID id = UUID.randomUUID();
double elapsedTime = 0.0;
while (elapsedTime < 60.0) {
  pipe.runTransient(2.0, id);
  elapsedTime += 2.0;
}
```

For regression tests, compare the transient profile after the boundary change with a second
`TwoFluidPipe` solved directly at the new boundary condition. A practical pressure-profile metric is
root-mean-square pressure difference across all sections; for compact tests a limit of 1-2 bar is a
useful sanity check. Treat acoustic pressure settling and material-inventory settling as separate
checks. Pressure waves can settle quickly, while liquid, oil, and water holdup profiles may require
one or more residence times to approach a stationary distribution. Do not impose the pressure-test
horizon on holdup convergence or force agreement by reconstructing conservative phase masses from
the stationary closure.

Transient holdup is reconstructed from the phase masses advanced by the finite-volume equations.
There is no separate post-step projection toward the steady-state holdup correlation. Such a
projection changes phase inventory without a boundary flux or mass-transfer source and makes the
error scale with pipe length. The earlier unreferenced 4 s relaxation time has therefore been
removed; steady-state closures remain part of initialization and the local closure/source terms.
Auxiliary terrain and slug trackers may maintain primitive diagnostics, but they do not rebuild the
finite-volume phase masses. Conservative source or flux coupling for those trackers remains future
model-development work.

At the steady-to-transient handoff, `run()` converts the final pressure, phase-holdup, density, and
velocity profiles into conservative phase mass, momentum, and energy exactly once. This conversion
defines the initial condition and advances no simulation time. For three-phase flow, the oil and
water momenta retain the independent phase velocities from the steady slip closure rather than being
collapsed to the bulk-liquid velocity. After the transient solve starts, the conservative phase
masses own cell inventory. A stream-connected inlet may update boundary composition and velocity for
its inlet flux, but it must not replace the first finite-volume cell's oil or water mass. This prevents
an unchanged, near-zero-time handoff from producing an inventory or holdup jump that scales with pipe
volume.

For each phase $k$ and for the total domain, validate the discrete balance

$$
M_k(t + \Delta t) - M_k(t) =
\int_t^{t+\Delta t} \left(\dot m_{k,in} - \dot m_{k,out} + S_k\right)\,dt,
\qquad
M = \sum_i \left(m'_{g,i} + m'_{o,i} + m'_{w,i}\right)\Delta x_i .
$$

Use `getTotalMassInventory()` to read $M$ in kg directly from the conservative gas, oil, and water
masses. After each `runTransient(...)`, `getLastMassBalanceReport()` provides initial and final
inventory, integrated inlet and outlet fluxes, integrated sources, signed residual in kg, and
relative residual for `GAS`, `OIL`, `WATER`, `LIQUID`, and `TOTAL`:

```java
pipe.runTransient(0.1, UUID.randomUUID());
TwoFluidMassBalanceReport balance = pipe.getLastMassBalanceReport();
double totalResidualKg = balance.getResidualKg(TwoFluidMassBalanceReport.Phase.TOTAL);
double totalRelativeResidual = balance.getRelativeResidual(TwoFluidMassBalanceReport.Phase.TOTAL);
boolean closes = balance.isWithinTolerance(TwoFluidMassBalanceReport.Phase.TOTAL, 1.0e-7, 1.0e-10);
```

The boundary and source integrals use the accepted internal substeps and the same Euler,
Runge-Kutta, or IMEX stage weights as the conservative update. For deterministic regression cases,
an absolute tolerance of $10^{-7}$ kg or a relative tolerance of $10^{-10}$ is appropriate; choose a
larger engineering tolerance for long simulations after demonstrating time-step and mesh
sensitivity. Positivity limiting or any future non-conservative correction appears explicitly as a
non-zero residual rather than being hidden.

Flash-driven phase transfer is added to gas, oil, and water with
$\Gamma_G+\Gamma_O+\Gamma_W=0$, so it cancels from the total source without losing liquid identity.
Oil-water segregation is represented by separate phase momentum and face fluxes; it does not use a
local oil-to-water mass relaxation. The IMEX pressure correction likewise changes phase momenta,
not phase masses. Closed boundaries therefore have zero integrated boundary flux, while open
boundaries close against the actual phase-resolved inlet and outlet fluxes.

A steady-state solution remains a useful long-time comparison, but agreement must result from the
transient balances and closure forces rather than overwriting the conserved state.

### Boundary Conditions

Use `setInletBoundaryCondition(...)` and `setOutletBoundaryCondition(...)` for explicit boundary
types. Convenience methods such as `closeOutlet()` and `openOutlet(...)` update the type and value
together.

| Boundary condition | Typical side | Required value | How to set it | Use case |
|--------------------|--------------|----------------|---------------|----------|
| `STREAM_CONNECTED` | Inlet | Inlet stream flow, temperature, pressure, and composition | Default inlet; `pipe.openInlet()` | Pipe connected to upstream process equipment |
| `CONSTANT_FLOW` | Inlet | Mass flow | `pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW); pipe.setInletMassFlow(4.0, "kg/sec");` | Production-rate step or controlled inlet flow |
| `CONSTANT_PRESSURE` | Inlet or outlet | Pressure | `pipe.setInletPressure(70.0, "bara")` or `pipe.setOutletPressure(55.0, "bara")` | Known upstream pressure or downstream back-pressure |
| `CLOSED` | Inlet or outlet | None | `pipe.closeInlet()` or `pipe.closeOutlet()` | Shut-in, valve closure, blocked-in pipe |
| `CHARACTERISTIC` | Inlet or outlet | External pressure/flow state | `pipe.setOutletBoundaryCondition(BoundaryCondition.CHARACTERISTIC)` | Reduced wave reflection in fast transients |

For a `CLOSED` inlet, the first physical cell has zero upstream advective and Joule-Thomson
boundary contributions, but it remains part of the radial fluid-wall-ambient heat-transfer domain.
This distinction is required for blocked-in cooldown: closing a valve removes through-boundary
energy transport; it does not thermally insulate or remove the adjacent finite-volume cell. The
broader local-flux and integrated-energy closure work remains tracked in
[issue #2792](https://github.com/equinor/neqsim/issues/2792).

Example with explicit boundary settings:

```java
pipe.setInletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_FLOW);
pipe.setInletMassFlow(4.0, "kg/sec");
pipe.setOutletBoundaryCondition(TwoFluidPipe.BoundaryCondition.CONSTANT_PRESSURE);
pipe.setOutletPressure(55.0, "bara");
pipe.run();

pipe.openOutlet(52.0, "bara");
pipe.runTransient(2.0, UUID.randomUUID());
```

Avoid over-specifying the same side. For example, a `STREAM_CONNECTED` inlet already gets flow,
temperature, pressure, and composition from the inlet stream; switch to `CONSTANT_FLOW` only when
the flow should be independent of the stream flow rate.

### Choosing Time Step, Sections, and Pipeline Length

`runTransient(dt, id)` takes a macro time step in seconds. Internally, the solver sub-steps to
satisfy the CFL condition, so `dt` is the requested reporting/control interval, not necessarily the
single numerical step.

Guidelines:

| Choice | Practical guidance |
|--------|--------------------|
| Number of sections | Start with 10-20 sections for simple horizontal pipes, 30-100 for long pipelines, and refine around risers, low points, or sharp elevation changes. |
| Section length | Keep `dx = length / sections` small enough to resolve terrain and holdup changes. A low point or riser should span several sections, not one cell. |
| Time step | Start with 0.5-2 s for compact regression models and 5-60 s for long slow-flow pipelines when adaptive time stepping is enabled. Reduce it for valve closures, slug fronts, or fast pressure waves. |
| Adaptive stepping | `setEnableAdaptiveTimestepping(true)` is recommended for difficult multiphase transients. It recomputes the stable internal step as velocities and holdups change. |
| CFL number | `setCflNumber(0.3-0.8)` is typical. Lower values provide more stability margin and temporal resolution; higher values are faster. |
| Settling time | Run until pressure and holdup profiles stop changing or match a new stationary reference. The required physical time scales with pipeline length, flow velocity, compressibility, and liquid inventory. |

For a pressure-boundary step, a compact 300 m regression pipe may settle in a few seconds. A real
long subsea line can require minutes to hours of simulated time, especially when liquid inventory,
terrain accumulation, or thermal transients dominate. Always distinguish wall-clock runtime from
physical simulated time.

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
| `IMEX_PRESSURE_CORRECTION` | Convective only | Semi-implicit momentum pressure correction with conservative explicit phase-mass transport; ~10x larger dt. Not recommended for vertical risers. |

```java
pipe.setTimeIntegrationMethod(TimeIntegrator.Method.RK4);       // default
pipe.setTimeIntegrationMethod(TimeIntegrator.Method.IMEX_PRESSURE_CORRECTION); // semi-implicit
TimeIntegrator.Method current = pipe.getTimeIntegrationMethod(); // query
```

### Adaptive Timestepping

Adaptive timestepping provides robustness for challenging geometries. Enable via
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
- `testHorizontalGasCondensateScenario` - 2km horizontal pipe, gas-condensate, 6-inch
- `testUphillRiserAccumulationScenario` - 500m vertical riser, riser-base accumulation
- `testTerrainTrackingLowPointAccumulation` - V-shaped terrain with 30m dip
- `testVelocityEffectOnHoldup` - High vs low velocity holdup comparison

**Terrain-Induced Slugging Patterns:**
- `testSevereSlugConditions` - Flowline-riser topology extraction smoke test; not a published dynamic validation case
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
