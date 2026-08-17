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
and mixture momentum. Full named-component transport is available through the opt-in conservative
component mode described below.

`FlashTable` stores the same aggregate liquid fraction, oil/aqueous liquid mass split, and gas/liquid
molar masses as the rigorous flash path. Interpolated liquid identity fractions are clamped and
renormalized; an identity that is absent at all surrounding grid points remains exactly zero.

### Conservative named-component transport

Call `setComponentTransportEnabled(true)` before `run()` to initialize independent component
inventories in every physical cell and each gas, oil, and aqueous phase. The default remains `false`
for backward compatibility.

For cell $i$, phase $k$, and named component $c$, the conserved inventory is
$M_{i,k,c}$ [kg]. For an accepted hydrodynamic substep, the component face flux is

$$
F_{f,k,c} = \dot m_{f,k}Y_{upwind(f,k),k,c},
$$

where $\dot m_{f,k}$ is the integration-weighted phase mass flux already used by the phase
continuity equation. The upwind state follows the sign of the internal face flux. The inlet stream
supplies positive-flow inlet composition. Integrated boundary component masses use the same Euler,
Runge-Kutta, or IMEX stage weights as the accepted phase update.

Flash-driven phase transfer is mapped by component name. Evaporation withdraws the donor oil or
water composition. Condensation uses the receiving equilibrium phase composition. In every cell,

$$
\sum_{k\in\{G,O,W\}} \Delta M_{i,k,c}^{transfer}=0
$$

for every component. After transport, the component sums must match the accepted hydrodynamic phase
inventories within `componentConservationTolerance`; the implementation only removes floating-point
round-off and throws if a material mismatch would require a projection. Cell PT flashes are then
built from total conservative named-component inventories. These flashes update density, viscosity,
sound speed, phase identity, and phase enthalpy but never overwrite the conserved inventories.

The interphase energy closure evaluates phase-specific partial component enthalpies at the cell
pressure and temperature. For one accepted transfer,

$$
Q_{latent,i}=-\sum_k\sum_c \Delta M_{i,k,c}^{transfer}\bar h_{i,k,c}.
$$

Positive $Q_{latent}$ is heat released into the fluid sensible-energy equation; negative values
consume sensible energy. This term is exposed in both
`TwoFluidComponentConservationReport.getInterphaseLatentHeatEnergyJ()` and
`TwoFluidThermalEnergyBalanceReport.getLatentHeatEnergyJ()`. It is included exactly once in the
thermal residual.

```java
pipe.setComponentTransportEnabled(true);
pipe.setComponentConservationTolerance(1.0e-8);
pipe.setStoreComponentConservationHistory(true);
pipe.run();

pipe.runTransient(0.1, UUID.randomUUID());
TwoFluidComponentConservationReport components =
    pipe.getLastComponentConservationReport();
double[] co2Gas = pipe.getComponentMassFractionProfile(
    TwoFluidComponentConservationReport.Phase.GAS, "CO2");
double outletCo2 = pipe.getOutletComponentMassFraction(
    TwoFluidComponentConservationReport.Phase.GAS, "CO2");
String json = components.toJson();
```

`TwoFluidComponentConservationHistory` retains time-aligned immutable reports when history storage
is enabled. All array getters return defensive copies. The same APIs are directly accessible from
Python through JPype; `devtools/neqsim_dev_setup.py` imports the pipe, reports, history, and phase
enum into the standard notebook namespace. The public report constructor rejects missing or
duplicate component names, inconsistent phase/component/cell dimensions, and non-finite diagnostic
values before an invalid report can cross the Java or JPype boundary.

#### Validated scope and fail-loud boundaries

- At least two named components and an unchanged component slate are required after `run()`.
  Component order may differ because identity is canonicalized by name.
- Gas, hydrocarbon liquid, and aqueous phases are supported. A non-negligible unsupported flash
  phase or a phase transfer whose receiving equilibrium phase is absent throws.
- Positive inlet flow, positive outlet outflow, closed boundaries, and signed internal-face
  reversals are supported. Reverse inflow through the outlet is rejected because no validated
  outlet-boundary composition has been supplied.
- Direct oil-water component transfer is not inferred. Oil/water transfer must be represented
  through the validated gas-mediated flash closure.
- First-order upwind component advection is bounded under the hydrodynamic CFL step. Any negative
  component inventory, phase/component synchronization error, or non-closing component ledger
  above tolerance throws instead of being hidden by renormalization.
- The energy report covers the implemented sensible, Joule-Thomson, ambient, and
  composition-dependent interphase latent terms. Pressure-work and kinetic-energy storage remain
  outside that post-step thermal diagnostic.

The one-phase gas limit is regression-tested against the validated `PipeFlowSystem` conservative
species pulse. On a compact 2 m case, coarse and jointly refined grids/timesteps must keep the outlet
nitrogen mass fraction within 0.08 absolute mass fraction of the reference; refinement may not move
away by more than 0.01. This stated margin accounts for different hydraulic grids and explicit
versus implicit first-order transport. A closed SRK-CPA water-dew-point transition separately checks
every component, cell-wise equal/opposite phase transfer, phase mass, boundedness, deterministic
history, and the latent-inclusive thermal residual. Engineering applications should repeat mesh and
time-step refinement at their own length, velocity, phase split, and event duration.

### Momentum Conservation
Separate momentum equations for each phase:

| Component | Implementation |
|-----------|----------------|
| Gas momentum | Full 1D momentum with wall shear, interfacial shear, pressure gradient |
| Liquid momentum | Full 1D momentum with wall shear, interfacial shear, pressure gradient |
| Wall friction | Pipe roughness-based (Colebrook/Blasius correlations) |
| Interfacial friction | Flow-regime dependent correlations |

#### Optional stiff dispersed-bubble drag

`setEnableStiffBubbleDrag(true)` opts into the dimensionally correct Schiller-Naumann
dispersed-bubble force and a conservative local implicit source solve. For a spherical bubble
population,

$$
F_i=\frac{3}{4} C_D \rho_L \alpha_G \frac{A}{d_b}
(v_G-v_L)|v_G-v_L|,
\qquad a_i=\frac{6\alpha_G}{d_b},
\qquad f_i=\frac{C_D}{4}.
$$

Bubble and dispersed-bubble regimes use the explicit algebraic diameter closure

`d_b = min(2 sqrt(0.725 sigma_b / (g |rho_L-rho_G|)), f_D D)`.

The defaults $\sigma_b=0.02$ N/m and $f_D=0.20$ preserve the historical calculation. Change the
fixed values, or explicitly use each section's thermodynamic phase-property surface tension, through
the public pipe API:

```java
pipe.setBubbleSurfaceTension(0.025);
pipe.setMaximumBubbleDiameterFraction(0.15);
pipe.setUseLocalBubbleSurfaceTension(true);
```

Local mode uses the surface tension already stored for each section by the thermodynamic coupling;
the default remains fixed for compatibility. This single algebraic scale does not model a
bubble-size distribution, deformation, coalescence, breakup, or turbulent-dissipation dependence.

The source operator solves the active gas and combined-liquid momenta by backward Euler and applies
one half-step on each side of the transport update. It conserves total active-phase momentum to
roundoff, decreases slip and kinetic energy, removes exactly absent phases instead of applying a
mass floor, and partitions the liquid impulse by active oil/water mass so existing oil-water slip is
preserved. The source evaluation is local and retains no stage history.

This mode is opt-in for migration compatibility. The legacy force scaling remains the default
because the corrected closure, although numerically stable, is not yet quantitatively validated by
the public Tengesdal severe-slugging benchmark. Without relaxing its acceptance bounds, the opt-in
model passes 3 of 6 cases: its smallest pressure swing is 167.1 kPa against 98 +/- 5 kPa, its slug
length ratio is 1.164, and the 16-section, 0.1 s case does not establish a repeated cycle. The
compatibility default continues to pass all 6 cases. These results indicate a remaining closure or
regime-transition limitation rather than a stiff-source instability.

#### Optional virtual-mass coupling

`setEnableVirtualMassForce(true)` enables a local added-inertia coupling between gas and combined
liquid momentum. After the complete uncoupled finite-volume right-hand side is assembled, the model
computes

$$
K=C_{vm}\alpha_G\rho_L A,
\qquad
F_{vm,G}=\frac{-K(a_{G,0}-a_{L,0})}
{1+K(1/m_G+1/m_L)},
\qquad F_{vm,L}=-F_{vm,G}.
$$

Here $a_{k,0}=(d(m_kv_k)/dt-v_kdm_k/dt)/m_k$ is obtained from the current integration-stage
state and its complete uncoupled rate. The operator retains no velocity history, so repeated RHS
evaluations and rejected integrator stages cannot change a later evaluation. For gas-oil-water
flow, the combined liquid correction is partitioned between oil and water by conservative liquid
mass, preserving mixture momentum without creating oil-water transfer. Coupling tends continuously
to zero when either phase inventory is absent.

The default $C_{vm}=0.5$ is the spherical-bubble value. It is not a universal calibration for slug,
churn, or annular flow, and enabling it does not establish accuracy parity with a commercial
multiphase simulator. Validate the coefficient and transient response against applicable public or
project data before engineering use.

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

> **Where the minimum applies.** The bound `alphaL >= lambdaL * minimumSlipFactor` states that the gas outruns the
> liquid by at least that factor, which is a property of gas-driven transport. It is applied only on level and uphill
> sections. On a downhill section gravity moves the liquid, the slip ratio legitimately falls, and the bound has no
> basis; applying it there overwrote the momentum balance with a constant. On a 5 km, 200 mm fixture undulating by
> +/-30 m it was binding on 39 of 42 downhill sections and on none of the uphill or level ones.

### Horizontal Annular Criterion

| Method | Default | Description |
|--------|---------|-------------|
| `setUseEquilibriumLevelAnnularTransition(boolean)` | `true` | Branch on the equilibrium liquid level instead of the droplet-entrainment criterion |

The horizontal branch of the flow map decides annular flow from the equilibrium liquid level, following Taitel and
Dukler (1976). Disabling it restores the earlier path, which used the *vertical* droplet-entrainment criterion
`U_SG > 3.1 * (sigma * g * drho / rhoG^2)^0.25` ahead of the stratified/slug transition. That threshold is around
0.75 m/s for a 14-inch high-pressure export line, so it classified a horizontal gas pipeline as annular on gas velocity
alone and solved a shallow stratified layer with a thin-film closure.

The two paths differ only where the gas velocity clears the droplet threshold while the Kelvin-Helmholtz margin is
still below one. On a 73.8 km export line they are identical at 10 MSm3/d; at 4 MSm3/d the equilibrium-level branch
reclassifies 272 of 320 sections as stratified-wavy and moves the maximum holdup error from -25.5 to -2.4 per cent.

### Friction Model

| Method | Default | Description |
|--------|---------|-------------|
| `setSeparatedFrictionModel(boolean)` | `true` | Charge each phase its own wall shear where the phases are separated |

The friction gradient uses per-phase wall shear, `-dP/dx = (tau_wG*S_G + tau_wL*S_L)/A`, in stratified flow, and the
mixture correlation elsewhere. The mixture form charges the whole perimeter with a holdup-weighted density; on a
stratified line that over-predicts the pressure drop by a factor of about 2.3 at 41 per cent holdup, and because it
scales as `G^2 / rho_mix` it also makes extra liquid *reduce* the gradient, which inverts the terrain response.

The separated form is scoped to stratified flow because its wetted perimeters come from a circular-segment layer at the
bottom of the bore. Annular flow, whose film wets the whole perimeter, is not described by that geometry: including it
moved the export-line error from +1.4 to +14.7 per cent at 10 MSm3/d and pushed 12 MSm3/d into the pressure floor.

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

### Public severe-slugging benchmark

The diagnostic and transient solver are checked against Tengesdal's public 2002 air–mineral-oil
experiments in a 3-inch, -3-degree flowline and 14.94 m riser. The source data and the assumptions
needed to reproduce them are recorded with the tests instead of being treated as an undocumented
commercial-simulator comparison.

The diagnostic benchmark uses all 55 operating points in Figure 4-8 and the superficial
velocities and uncertainties in Table A-3. Figure symbols were digitized as 26 severe-slug,
14 transition, and 15 stable observations. Transition points are reported separately and are not
scored as either binary class. With homogeneous inlet holdup, the published effective upstream
volume, 856 kg/m³ liquid density, atmospheric separator pressure, and a 0.89 gas-cap void
fraction, the current Taitel screen gives:

| Experimental class | Predicted severe | Predicted stable |
|--------------------|------------------|------------------|
| Severe slug | 22 | 4 |
| Stable | 8 | 7 |
| Transition (not scored) | 6 | 8 |

This is 70.7% binary accuracy, 84.6% severe-slug recall, and 46.7% stable recall. It is useful as
a conservative screen but is not a high-specificity classifier and must not be described as
quantitative dynamic validation.

The slow dynamic benchmark reproduces large-facility Test 3 ($v_{SL}=0.50$ m/s and standard
$v_{SG}=1.00$ m/s). Severe slugging in this configuration is a **deterministically chaotic** limit
cycle: a relative inlet-pressure perturbation of $10^{-12}$, twelve orders of magnitude below the
digitization uncertainty of the source figure, moves the peak-to-peak riser-base pressure by more
than a factor of two and the apparent cycle period by more than a factor of 1.5. Instantaneous
extremes taken from a single trajectory are therefore not reproducible across platforms, compilers
or JIT states, and the benchmark deliberately does not assert numerical agreement on them.

The benchmark instead evaluates a four-member ensemble — 12 sections at 0.1 s, the same case with
the $10^{-12}$ inlet perturbation, 16 sections at 0.1 s, and 12 sections at 0.2 s — and separates
trajectory-robust from trajectory-sensitive quantities:

| Quantity | Observed across the ensemble | How it is asserted |
|----------|------------------------------|--------------------|
| Phase-resolved and total mass closure | below $10^{-15}$ | below $10^{-10}$ |
| Time-averaged riser-base pressure | 171–176 kPa, spread below 4% | mesh, outer-step and perturbation agreement within 8% |
| Outlet-liquid blowout and fallback | present in every realization | above 1.25 and below 0.75 of the liquid feed rate |
| Peak-to-peak riser-base pressure | 42–300 kPa | inside 0.2–4.0 riser hydrostatic heads, and the ensemble range must bracket the digitized 98 ± 5 kPa |
| Apparent cycle period | 14–35 s | each realization above the riser filling time, and the ensemble mean asserted to stay below the experimental 38 ± 2 s |
| Maximum tracked outlet slug | 1.5–4.9 m, or 0.10–0.33 riser heights | positive and below one riser height |

The digitized experiment has about 98 ± 5 kPa inlet-pressure amplitude and a 38 ± 2 s cycle period.
The modelled pressure swing is of the same order and brackets the measured value, but the cycle
period is systematically too short and the tracked outlet slug stays well below the experimental
severe-slug definition. Neither metric supports a claim of quantitative severe-slugging validation;
only the regime signature, the mass closure and the time-averaged riser-base pressure are treated
as reproducible evidence.

The dynamic reproduction uses the physical 19.81 m flowline plus riser, 0.0762 m diameter,
atmospheric outlet, nitrogen as an air surrogate, and a single non-volatile TBP fraction fitted to
the reported Crystex density. The source does not give a case-specific temperature or a full oil
assay, so 25 °C and the TBP molecular weight are explicit modelling assumptions. The experimental
upstream tank/plenum is not represented dynamically; this missing compressible volume is a likely
contributor to the short period. Coarse grids are additionally sensitive to whether the
flowline–riser boundary lands on a cell face, which is one reason the instantaneous amplitude is
not mesh-converged even though the mean pressure is. The steady-state initialization runs with the
wall-clock guard disabled and each realization asserts that the guard did not fire, so the reported
results do not depend on the speed or load of the executing machine. The stochastic slug tracker
uses a fixed benchmark seed; ordinary simulations retain its non-deterministic default. Only the
explicit RK4 path is covered; no IMEX severe-slugging validation is claimed.

Run the public checks with:

```bash
./mvnw -Dtest=SevereSluggingBenchmarkHarnessTest test
./mvnw -DexcludedTestGroups= -Dtest=SevereSluggingExperimentalBenchmarkTest test
```

Source: S. Tengesdal, *Investigation of Self-Lifting Concept for Severe Slugging Elimination in
Deep-Water Pipeline/Riser Systems* (2002),
[BSEE Technical Assessment Program report](https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf).

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

// Explicit shutdown assumption; the documented default is also 50 W/(m2 K)
pipe.setStagnantInnerHeatTransferCoefficient(50.0);

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
- **Coefficient ownership**: `setHeatTransferCoefficient(...)` is the simple-model or configuration-level overall
  U-value. In the multi-layer transient model it enables heat transfer but is not reused as a fluid-side film
  coefficient, so calling it before or after radial-layer configuration does not change closed-flow heat flux.
- **Closed-flow inner HTC**: `setStagnantInnerHeatTransferCoefficient(...)` controls the zero-throughput fluid-to-wall
  film coefficient independently. The default is 50 W/(m²·K), a pragmatic gas-rich shutdown assumption; set a
  case-specific value for the fluid inventory and natural-convection regime being studied.
- **Flowing inner HTC**: Cells with local face throughput use the model's laminar constant-Nusselt or turbulent
  Dittus-Boelter correlation instead of the stagnant value.
- **Transient response**: Explicit finite-difference with thermal mass in each layer
- **Cooldown time**: Lumped capacitance approximation for shutdown scenarios
- **Transient advection**: Uses the gas, oil, and water mass flow retained from each conservative AUSM+ integration
  stage, combined with the time integrator's own stage weights. Temperature transport therefore uses the same face
  fluxes that advanced the accepted hydrodynamic state without a second flux sweep. A CLOSED inlet or outlet has
  exactly zero advective sensible-energy transport, while internal convection can continue. Every cell reads from
  one pre-update temperature snapshot, so explicit advection is independent of the cell loop order. The external outlet
  face is outflow-only; reverse-flow upwinding applies only at internal faces.
- **Closed cooldown**: Radial wall/ambient heat exchange is evaluated for every physical cell, including section zero.
  The local conservative phase inventory supplies fluid thermal inertia, so a disconnected inlet stream's stored
  nominal rate cannot change a closed-domain temperature history. Each cell also owns an independent radial-layer
  temperature history, preventing repeated advancement or cross-cell mixing of stateful multilayer wall calculations.
- **Energy ownership**: The post-step fluid/wall temperature model owns ambient heat exchange. The duplicate wall source
  in `TwoFluidConservationEquations` is disabled when this model is configured, avoiding two applications of the same
  heat loss.
- **Multilayer flux consistency**: The fluid and first radial layer use the same instantaneous pre-update
  fluid-to-wall heat rate. The outer-layer-to-ambient rate is retained from that same explicit step, so transient
  fluid-plus-layer energy closes against ambient heat loss rather than mixing transient wall storage with a steady
  overall-U estimate.
- **Thermal balance reporting**: After a thermal `runTransient(...)`,
  `getLastThermalEnergyBalanceReport()` returns fluid and wall energy changes, sensible advection, Joule-Thomson energy,
  composition-dependent interphase latent heat, ambient loss, and a signed residual in joules. With component transport
  enabled, the thermal step also runs for an adiabatic pipe so conservative sensible advection and latent effects remain
  coupled even when the external heat-transfer coefficient is zero. The report is `null` only when neither external
  heat transfer nor component transport is active. Pressure work and kinetic-energy storage are outside this post-step
  thermal diagnostic.

For validation, start from `run()`, close both boundaries, disable Joule–Thomson effects for an adiabatic invariant, and
check that a uniform state remains uniform. For cooldown, report absolute pressure, composition and mixing rule,
temperatures, heat-transfer coefficients, wall properties, mesh, time step, and units; verify that every cell approaches
ambient monotonically without undershoot. For deterministic compact regressions, require the thermal report to meet an
absolute residual of 1e-5 J or a relative residual of 1e-10, then demonstrate mesh and time-step refinement at nearby
conditions. This implementation does not claim OLGA or LedaFlow equivalence.

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

After `runTransient(...)`, `getOutletStream()` publishes the accepted
interval-average total outlet mass flux:

```java
double outletMassFlow = balance.getOutletMassKg(TwoFluidMassBalanceReport.Phase.TOTAL)
    / balance.getElapsedTimeSeconds();
```

This preserves finite transport delay and inventory release when the pipe is
coupled directly to downstream process equipment. Steady-state `run()` retains
the inlet-balanced outlet-flow convention. Use the report itself for signed,
phase-resolved flux integrals and conservation evidence.

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
until convergence. Four parameters control this:

| Parameter | Setter | Default | Description |
|-----------|--------|---------|-------------|
| Under-relaxation | `setSteadyStateUnderRelaxation(double)` | 0.5 | Update damping factor (0–1); lower = more damping, more stable |
| Flash interval | `setSteadyStateFlashInterval(int)` | 3 | Re-flash thermodynamics every N iterations; higher = faster but less accurate |
| Max iterations | `setSteadyStateMaxIterations(int)` | 0 = mesh-scaled | 0 uses `max(100, 20 x sections)`; the sweep moves information about one section per iteration, so a fixed budget silently truncates long, finely-discretised lines |
| Max wall-clock time | `setSteadyStateMaxWallClockTime(double)` | 300 s | Timeout for the SS solver; prevents runaway iterations |

```java
pipe.setSteadyStateUnderRelaxation(0.3);   // More conservative damping
pipe.setSteadyStateFlashInterval(5);       // Flash every 5th iteration
pipe.setSteadyStateMaxWallClockTime(60.0); // Allow 60 seconds
```

### Always check the steady-state outcome

`run()` does not throw when the steady state fails to settle, so the outcome has to be read back.
Three independent flags describe it, and a profile is only trustworthy when the first is true:

| Query | Meaning when true |
|-------|-------------------|
| `isSteadyStateConverged()` | The sweep met the tolerance and the profile is a solution |
| `isSteadyStateWallClockLimited()` | The wall-clock guard stopped the sweep early |
| `isSteadyStatePressureFloorLimited()` | One or more sections rest on the internal 1 bara pressure floor |

```java
pipe.run();
if (!pipe.isSteadyStateConverged()) {
  if (pipe.isSteadyStatePressureFloorLimited()) {
    throw new IllegalStateException(
        "The line cannot deliver this rate at this inlet pressure");
  }
  throw new IllegalStateException("Steady state did not converge");
}
```

**Why the pressure floor matters.** The marching solver clamps every section at 1 bara so it stays
numerically alive when a line has no deliverability. A profile resting on that clamp is a fixed
point of the *clamp*, not of the momentum balance: the per-section change falls below tolerance and
the sweep would otherwise report success on a line that cannot physically deliver the requested
rate. `isSteadyStatePressureFloorLimited()` makes that case visible, and `isSteadyStateConverged()`
is withheld. `PipeBeggsAndBrills` throws `Outlet pressure is negative` on the same condition, so
both codes agree that such a case has no solution.

### Direct electrical heating (DEH)

A uniform electrical heat input can be added to the energy equation, in steady state and in
transient runs, and it works with wall heat transfer switched off:

```java
pipe.setLength(73845.0);
pipe.setDirectElectricalHeatingPower(10.0e6);        // W, spread over the pipe length
// or, equivalently:
pipe.setDirectElectricalHeatingPowerPerMeter(135.4); // W/m
```

The power set here is what reaches the fluid, so cable and coating losses must already be deducted.
The same convention is used by `PipeBeggsAndBrills.setDirectElectricalHeatingPower(double)`, so the
two models can be compared like for like.

In steady state the segment solution decays toward the wall-loss/DEH **balance temperature**

$$T_\infty = T_{surf} + \frac{q}{U \pi D}$$

rather than toward the surface temperature. This is exact for a uniform source, so the profile
cannot overshoot the balance temperature — unlike explicit per-increment stepping, which does.


## Validation Status

### Evidence levels

Passing software tests establish numerical regressions, API behavior, and conservation. They do
not by themselves establish agreement with experiment. Current external evidence is:

- the public Tengesdal flow-map confusion matrix for the Taitel diagnostic;
- the public Tengesdal Test 3 pressure, production-cycle, period, and slug-length comparison;
- Beggs–Brill steady-profile comparisons, which are model-to-model checks rather than experiment.

Commercial transient multiphase simulators are **not** used as a reference. Their licence terms
generally prohibit publishing benchmark comparisons and prohibit using the software to develop the
science, technology or product content of similar software, so no NeqSim closure is tuned to such a
tool and no measured deviation against one is recorded here.

The public severe-slugging benchmark deliberately retains failed/limited metrics in its assertions
and documentation. In particular, the present cycle period and slug-length result prevent a claim
of fully quantitative severe-slugging validation.

### Steady-state behaviour on a long gas-condensate export line

Measured on a 73.8 km subsea gas-condensate export line (ID 0.355 m, U = 3 W/m2K, seabed 4 C,
200 bara inlet) as an internal consistency check on the solver. The rate exponent in
$\Delta P \sim \dot m^{\,n}$ rises from about 2.1 at low rate to about 3.1 at high rate, so the
density feedback along the line is reproduced rather than merely the level at one rate. Adding
10 MW of direct electrical heating raises the arrival temperature 17.4 K and the pressure drop
15.0 per cent. `PipeBeggsAndBrills` sits far above `TwoFluidPipe` on the same cases because its
two-phase friction multiplier is an extrapolation at this liquid loading. Results are
grid-converged, at default settings.

Remaining limitations:

- **Terrain response now comes from the momentum balance.** The annular film closure accepted an
  inclination argument but never used it - the film balance was `tau_i = tau_wL` with no gravity
  term - so in annular flow the holdup had no terrain dependence at all, and the terrain response
  was supplied instead by an empirical multiplier applied on top of the solved holdup. That
  multiplier compounded three proxies for one effect to a factor of order 100 and produced
  low-point holdup roughly five to nine times the solved value. The film balance now carries
  `tau_i = tau_wL + rhoL * g * sin(theta) * delta`, and the multiplier is gone. At 4 MSm3/d the
  maximum holdup moved from 0.222 to 0.022. The response
  scales with `sin(theta)` as it should: the same closure gives an 11-fold valley-to-crest holdup
  variation on a 5 km line undulating at 8.6 degrees.
- **The three-phase free-water case does not converge.** With 15 m3/hr of free water on the same
  line the solve is wall-clock limited after 4078 iterations at a 1200 s budget. The pressure drop
  is identical to the 300 s run to 0.01 bar, so the profile
  is stationary and the convergence criterion is stalling on the three-phase liquid split rather
  than the solution diverging - but `isSteadyStateConverged()` is correctly false and the number
  must not be quoted.
- All observations are model-internal on one line.

### Implemented regression tests

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

The exact suite size changes as the model evolves. Use the Maven/JUnit result for the tested commit
rather than a hard-coded historical count.

## References

1. Bendiksen, K.H., Maines, D., Moe, R., & Nuland, S. (1991). "The Dynamic Two-Fluid Model OLGA: Theory and Application." SPE Production Engineering, 6(02), 171-180.

2. Taitel, Y., & Dukler, A.E. (1976). "A model for predicting flow regime transitions in horizontal and near horizontal gas-liquid flow." AIChE Journal, 22(1), 47-55.

3. Pots, B.F.M., Bromilow, I.G., & Konijn, M.J.W.F. (1987). "Severe Slug Flow in Offshore Flowline/Riser Systems." SPE Production Engineering, 2(04), 319-324.

4. Turner, R.G., Hubbard, M.G., & Dukler, A.E. (1969). "Analysis and Prediction of Minimum Flow Rate for the Continuous Removal of Liquids from Gas Wells." Journal of Petroleum Technology, 21(11), 1475-1482.

5. Bai, Y., & Bai, Q. (2010). "Subsea Pipelines and Risers." Elsevier. Chapter on Thermal Design.

6. Beggs, H.D. & Brill, J.P. (1973). "A Study of Two-Phase Flow in Inclined Pipes." Journal of Petroleum Technology, SPE-4007-PA.
