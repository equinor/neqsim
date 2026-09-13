---
title: "Two-Fluid Transient Multiphase Flow Model"
description: "This document describes the two-fluid model implementation in NeqSim for transient multiphase pipeline simulation."
---

# Two-Fluid Transient Multiphase Flow Model

## Release scope and current validation

The canonical status vocabulary, row-by-row evidence, executable example, and
supported-use boundary are maintained in the
[TwoFluidPipe Evidence Matrix and Supported Envelope](../process/twofluidpipe-evidence-matrix).
Use that matrix before selecting a configuration; implementation, numerical
verification, and public experimental qualification are different claims.

The evidence chain in merged PRs #3514, #3541, #3543, and #3547 covers
steady/transient consistency, public benchmark reporting, three-phase steady
convergence, and coupled component/phase/thermal ledgers. It does not establish
general experimental accuracy for multiphase transients.

| Configuration | Current evidence | Release interpretation |
|---|---|---|
| Existing defaults | Selected steady-state, stratified-transient and phase-consistency regressions pass | No blanket severe-slugging or long-run inventory qualification |
| Shared slug force balance with interfacial pressure and coupled pressure/momentum enabled | 1800 s inventory drift: 1.323207% at 40 cells and 1.357668% at 80 cells | Meets the unchanged 2% target for these fixtures; requires explicit opt-in |
| Conservative Lagrangian tracking with implicit slug/film friction | Centered coupled pressure traction restores five-second progress; earlier 600 s characterization missed amplitude, cycle and pressure-limiter gates | Experimental; disabled by default |

The under-2% result requires all three settings before initialization:
`setSharedSlugForceBalanceEnabled(true)`,
`setEnableInterfacialPressure(true)`, and
`setEnableCoupledPressureMomentum(true)`.
Leaving shared slug forces disabled does not repair the earlier default-mode
5.757% inventory-drift result. This release preparation does not change defaults.

The separate `setConservativeSlugForceIntegrationEnabled(true)` option remains
experimental and off by default. Its 65.163 kPa inlet-pressure amplitude is below
the unchanged 68.6 kPa lower bound; the unchanged liquid-trough detector finds no
completed cycle intervals, and pressure limits still activate. Do not use these
results as qualification of slug loads or extreme pressure transients. This is a historical
characterization; it does not establish completion with the current corrected detector.

The public Mohmmed slug-kinematics sweep remains experimentally unqualified:
its baseline passes 3/9 fixed gates and its mesh/time-step refinement is
non-monotone. The compact uphill gas/oil/water case is numerically verified on
30 and 60 cells, but the historical 73.8 km free-water input is unavailable and
is not covered. The coupled component/phase/thermal case is a closed, seeded
marker conservation test; it does not qualify spontaneous or sustained slugging.

Later sections preserve earlier measurements to explain the repair history.
Read those results with their stated configuration and revision; they are not
additional claims about the current defaults or released experimental accuracy.

This document describes the two-fluid model implementation in NeqSim for transient multiphase pipeline simulation.

The selectable closure sets are literature-inspired NeqSim implementations. Historical API names containing `OLGA` are retained for compatibility and do not claim numerical equivalence with OLGA, LedaFlow, or another commercial simulator.

For practical result extraction, long-flowline reporting, and comparison with measured
field data, see [TwoFluidPipe Reporting and Validation](two_fluid_reporting_and_validation).

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

The default adaptive minimum is a closure relation, not a phase-presence threshold. It tends continuously to zero with the no-slip liquid fraction. Exact phase presence comes from conservative gas, oil, and water masses, so an absent phase has zero mass, holdup, and velocity.

**Default behavior (adaptive minimum):**

By default, `useAdaptiveMinimumOnly = true`, which calculates the minimum holdup from flow correlations (Beggs-Brill type) scaled by the no-slip holdup. This allows very low holdups for lean gas systems:

```java
// Adaptive minimum (default) - good for lean gas
// Minimum holdup = max(lambdaL × slipFactor, correlation-based)
pipe.setUseAdaptiveMinimumOnly(true);   // Default
pipe.setMinimumSlipFactor(2.0);         // Default multiplier
```

**For an explicit calibrated wetting-film floor:**

```java
// Apply absolute floor in addition to correlation
pipe.setUseAdaptiveMinimumOnly(false);
pipe.setMinimumLiquidHoldup(0.01);  // 1% absolute minimum
```

Fixed-floor mode is opt-in and should be supported by fluid, wall-wetting, and flow-regime data. It is never applied to an exactly absent phase, and `setMinimumLiquidHoldup(0.0)` disables the absolute floor.

### Configuration Options

| Method | Default | Description |
|--------|---------|-------------|
| `setUseAdaptiveMinimumOnly(boolean)` | `true` | Use correlation-only minimum (no absolute floor) |
| `setMinimumLiquidHoldup(double)` | 0.001 | Absolute minimum holdup floor (when adaptive-only is false) |
| `setMinimumSlipFactor(double)` | 2.0 | Minimum ratio of gas to liquid velocity in adaptive mode |
| `setEnforceMinimumSlip(boolean)` | `true` | Enable/disable minimum slip constraint entirely |
| `setUseEquilibriumLevelAnnularTransition(boolean)` | `true` | Branch on the equilibrium liquid level instead of the droplet-entrainment criterion |
| `setSeparatedFrictionModel(boolean)` | `true` | Charge each phase its own wall shear where the phases are separated |

The minimum slip constraint states that the gas outruns the liquid by at least the given factor, which is a property of
gas-driven transport, so it is applied only on level and uphill sections. On a downhill section gravity moves the
liquid and the slip ratio legitimately falls; applying the bound there overwrote the momentum balance with a constant,
and it was binding on 39 of 42 downhill sections of an undulating fixture while binding on none of the uphill ones.

The bound is inverted from the slip ratio itself, `alphaL >= X / (1 + X)` with `X = slipFactor * vsL / vsG`, which is
below one at every liquid loading. The earlier form `alphaL >= lambdaL * slipFactor` is the same statement only in the
lean-gas limit; past `lambdaL > 1 / slipFactor` it exceeds one as a hold-up and degenerates into the clamp it was
truncated to, which is how the Tengesdal severe-slugging facility came to be held liquid-full at a constant 0.9 in
every section.

The horizontal annular criterion follows the equilibrium liquid level of Taitel and Dukler (1976). Disabling it
restores the vertical droplet-entrainment threshold, which classified effectively any horizontal gas pipeline as
annular. The two differ only below the Kelvin-Helmholtz threshold, which on a 73.8 km export line means they agree at
10 MSm3/d and differ at 4 MSm3/d, where the equilibrium branch moves the maximum holdup error from -25.5 to -2.4
per cent.

For inclinations above 10 degrees in magnitude, the mechanistic detector retains its gas-lift
criterion for annular flow. It no longer overrides an annular result with churn merely because
upward liquid superficial velocity exceeds 0.1 m/s. That dimensional switch produced lower uphill
holdup in the 500 m, 100 mm validation pipe by selecting a different closure. The unchanged uphill
comparison now gives approximately 3.26% holdup in both orientations. This removes an inconsistent
switch; it does not qualify a churn/annular boundary. A film-stability model is still needed to
resolve churn within this region.

The friction gradient uses per-phase wall shear in stratified flow and the mixture correlation elsewhere. On the same
export line the pressure drop error across a threefold rate range is +1.4, +1.6, +0.1 and -2.7 per cent, against +5.7,
+5.6, +1.4 and -0.0 per cent for the earlier mixture-only default.

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
- **Annular flow:** Film model + correlation; a nonzero minimum film is used only in explicit fixed-floor mode

Where:
- `λL` = No-slip liquid holdup (input liquid volume fraction)
- `Fr` = Froude number = v²/(g×D)

For lean gas systems with λL = 0.003, the stratified correlation gives αL ≈ 0.007 (0.7%), which is more realistic than a fixed 1% floor.

Numerical closure protection is separate from state: `1e-14` is used only in denominators, the stratified closure switches continuously to its trace-liquid asymptote at λL `1e-6`, and the drift-flux correction is withdrawn with `λL / (λL + 1e-3)`. These constants do not create phase inventory. The closure sets are literature-inspired NeqSim implementations and do not claim numerical equivalence with OLGA, LedaFlow, or another commercial simulator.

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

### Continuous Transient Source Evaluation

The dynamic conservation equations consume the detector's existing dimensionless, normalized
transition weights. Wall shear, interfacial force and area, and entrainment are evaluated with
each active regime's authoritative closure and then combined by those weights. Stratified
geometry stays active whenever its weight is non-zero. This removes a second, hard
`STRATIFIED_WAVY`/ `SLUG` source switch while preserving the original closure at every
pure-regime endpoint.
Interfacial force uses the weighted sum of shear times area, with equal and opposite gas and
liquid reactions. Effective shear is recovered from that force and the blended area.

No flow-map threshold, transition-band width, hold-up closure, or regime-specific friction
correlation is changed. Treat this continuation as experimental until conservation, nonlinear
robustness, Tengesdal, liquid-rich 1,800 s inventory, nearby operating point, and mesh/time-step
refinement qualification has passed.

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
| **Stratified Wavy** | Hart et al. | (1989) | Oil-gas systems: `f_i = 0.0142 + 22 × (h_L/D)^1.5 × ((v_G - v_L)/v_G)^0.9` |
| **Stratified** | Andreussi-Persen | (1987) | OLGA-style with inclination effects and Froude number correction |
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
// Configurable algebraic bubble diameter
d_b = 2 × (0.725 × σ_b / (|ρ_L - ρ_G| × g))^0.5
d_b = min(d_b, f_D × D)

// Bubble Reynolds number
Re_b = ρ_L × |v_slip| × d_b / μ_L

// Drag coefficient
if (Re_b < 0.1):
    C_D = 240           // Stokes limit
else if (Re_b < 1000):
    C_D = 24/Re_b × (1 + 0.15 × Re_b^0.687)
else:
    C_D = 0.44          // Newton regime

// Corrected dispersed-bubble friction factor
f_i = C_D / 4
```

With interfacial area concentration `a_i = 6 × α_G / d_b`, the corresponding force per pipe length
is

$$
F_i=\frac{3}{4}C_D\rho_L\alpha_G\frac{A}{d_b}
(v_G-v_L)|v_G-v_L|.
$$

The corrected force uses liquid-continuum density and is selected together with a local implicit
source solve by calling `TwoFluidPipe.setEnableStiffBubbleDrag(true)`. The local backward-Euler
operator is split into half-steps around transport, conserves active gas-oil-water momentum to
roundoff, cannot increase kinetic energy, and introduces no phase-mass floor. Oil and water receive
the liquid impulse in proportion to active mass, preserving their relative velocity. Bubble and
dispersed-bubble classifications use the same source treatment; neighboring regimes retain their
existing closures.

The defaults `σ_b = 0.02 N/m` and `f_D = 0.20` preserve the historical calculation. The
configuration is exposed on the public pipe API:

```java
pipe.setBubbleSurfaceTension(0.025);
pipe.setMaximumBubbleDiameterFraction(0.15);
pipe.setUseLocalBubbleSurfaceTension(true);
```

The opt-in local mode uses the thermodynamic phase-property surface tension already stored for each
section; fixed mode remains the default. This is a single algebraic size scale. It does not represent
a bubble-size distribution, deformation, coalescence, breakup, or turbulent-dissipation dependence.

The stiff corrected mode is opt-in. Existing simulations retain the legacy `C_D × d_b/(4D)` scaling
unless enabled, because the corrected mode is not yet quantitatively validated by the public
Tengesdal severe-slugging benchmark. That comparison was made when the benchmark still asserted a
riser-head-scaled pressure swing, which has since been shown to come from a saturated minimum-slip
bound rather than the momentum balance, so the recorded pass counts predate the rebased acceptance
bounds and the comparison has to be repeated before it means anything. This is a documented
physical closure/regime-transition limitation, not evidence of numerical source instability.

#### Hart et al. (1989) Correlation

For stratified wavy flow in oil-gas systems with emphasis on holdup dependency:

```java
InterfacialFriction ifCalc = new InterfacialFriction();
InterfacialFrictionResult result = ifCalc.calcHartCorrelation(
    gasVelocity, liquidVelocity, gasDensity, liquidDensity,
    gasViscosity, liquidViscosity, liquidHoldup, diameter);
double fi = result.frictionFactor;
```

The correlation:

$$
f_i = 0.0142 + 22.0 \cdot \left(\frac{h_L}{D}\right)^{1.5} \cdot \left(\frac{v_G - v_L}{v_G}\right)^{0.9}
$$

Where:
- `h_L/D` = liquid level / diameter ratio (from holdup)
- `v_G - v_L` = slip velocity
- Valid for stratified wavy flow with significant liquid holdup

#### Andreussi-Persen (1987) OLGA-Style Correlation

Includes inclination effects and Froude number-based wave transition:

```java
InterfacialFriction ifCalc = new InterfacialFriction();
InterfacialFrictionResult result = ifCalc.calcAndreussiPersenCorrelation(
    gasVelocity, liquidVelocity, gasDensity, liquidDensity,
    gasViscosity, liquidHoldup, diameter, inclinationAngleRadians);
double fi = result.frictionFactor;
```

Key features:
- Critical gas velocity threshold for wave formation
- Froude number correction for wave height
- Inclination angle effects (0° = horizontal, positive = uphill)
- Asymptotic limits for smooth and wavy stratified flow
- Used as the default in OLGA for stratified flow regimes

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

## Virtual Mass Force

The virtual mass (added mass) force accounts for the inertia of displaced liquid during gas-liquid acceleration. It is an optional local momentum coupling for transient simulations.

### Physical Basis

When gas accelerates through liquid, it must also accelerate a portion of the surrounding liquid. After the complete uncoupled finite-volume right-hand side is assembled, NeqSim solves the local added-inertia relation algebraically:

$$
K=C_{vm}\alpha_G\rho_LA,
\qquad
F_{vm,G}=\frac{-K(a_{G,0}-a_{L,0})}{1+K(1/m_G+1/m_L)},
\qquad F_{vm,L}=-F_{vm,G}
$$

Where:
- `C_vm` = virtual mass coefficient (0.5 for spheres, default)
- `α_G` = gas holdup
- `ρ_L` = liquid density
- `A` = pipe cross-sectional area
- `m_G`, `m_L` = conservative gas and combined-liquid masses per length
- `a_k,0 = (d(m_k v_k)/dt - v_k dm_k/dt) / m_k` = uncoupled stage acceleration

The calculation uses only the supplied integration-stage state and its complete uncoupled rate; it does not retain velocities from previous RHS calls. In gas-oil-water flow, the liquid correction is divided between oil and water by their conservative masses. The gas and combined-liquid forces are equal and opposite, and coupling tends continuously to zero when either phase is absent.

### Enabling Virtual Mass Force

```java
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inlet);
pipe.setLength(5000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(100);

pipe.getEquations().setEnableVirtualMassForce(true);
pipe.getEquations().setVirtualMassCoefficient(0.5); // Spherical-bubble value

pipe.run();
```

### Impact on Momentum Equations

The virtual mass force appears as source terms in the phase momentum equations:

- **Gas momentum:** `+F_vm` (accelerates gas when liquid decelerates)
- **Liquid momentum:** `-F_vm` (decelerates liquid when gas accelerates)

The spherical-bubble coefficient is not a universal slug, churn, or annular-flow calibration. The implementation does not by itself establish improved field accuracy or parity with a commercial simulator. Validate the coefficient, discretization, and complete opt-in transient against data applicable to the intended operating envelope.

### Reference

Drew, D.A. and Lahey, R.T. (1987). "The Virtual Mass and Lift Force on a Sphere in Rotating and Straining Inviscid Flow", Int. J. Multiphase Flow, 13(1), 113-121.

## Junction and Bend Losses (Local Losses)

The TwoFluidPipe model supports local (minor) loss coefficients for fittings, bends, valves, and other flow obstructions. These are added to the friction pressure drop to give total pressure loss.

### Pressure Drop Calculation

Local losses follow the standard K-factor formulation:

$$
\Delta P_{local} = \sum K_i \cdot \frac{1}{2} \rho_{mix} v_{mix}^2
$$

Where:
- `K_i` = loss coefficient for fitting i
- `ρ_mix` = mixture density
- `v_mix` = mixture velocity

### Adding Local Losses

```java
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inlet);
pipe.setLength(5000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(100);

// Add local loss coefficients at specific positions along the pipe
// addLocalLoss(position_m, kFactor)
pipe.addLocalLoss(1000.0, 0.9);   // K=0.9 at 1000m (tee junction)
pipe.addLocalLoss(2000.0, 0.3);   // K=0.3 at 2000m (90° elbow)
pipe.addLocalLoss(3000.0, 0.3);   // K=0.3 at 3000m (90° elbow)
pipe.addLocalLoss(3500.0, 0.17);  // K=0.17 at 3500m (gate valve)
pipe.addLocalLoss(4000.0, 2.0);   // K=2.0 at 4000m (check valve)

// Or use convenience methods for standard bends
pipe.setNumberOf90DegreeBends(4);   // Each K=0.3
pipe.setNumberOf45DegreeBends(2);   // Each K=0.16
pipe.setInletLossCoefficient(0.5);  // Sharp entrance
pipe.setOutletLossCoefficient(1.0); // Exit to tank

pipe.run();

// Get pressure drop breakdown
double dpFriction = pipe.getPressureDrop();  // Friction only
double dpLocal = pipe.calculateLocalLossPressureDrop();  // Local losses
double dpTotal = pipe.getTotalPressureDrop();  // Combined

// Get summary of all losses
System.out.println(pipe.getLocalLossSummary());
```

### Standard K-Factors (Idelchik, 1986)

| Fitting Type | K-factor | Notes |
|--------------|----------|-------|
| **90° standard elbow** | 0.30 | Long radius |
| **90° short radius elbow** | 0.90 | Tight bend |
| **45° elbow** | 0.16 | Standard |
| **Tee (flow-through)** | 0.20 | Straight-through |
| **Tee (branch flow)** | 0.90 | Into/out of branch |
| **180° return bend** | 2.20 | U-turn |
| **Gate valve (full open)** | 0.17 | Fully open |
| **Gate valve (half open)** | 4.5 | Partially open |
| **Globe valve (full open)** | 6.0-10.0 | High resistance |
| **Ball valve (full open)** | 0.05 | Very low resistance |
| **Check valve (swing)** | 2.0 | Prevents backflow |
| **Sharp entrance** | 0.50 | Flush inlet |
| **Rounded entrance** | 0.04 | r/D > 0.15 |
| **Exit to tank** | 1.00 | All velocity head lost |
| **Sudden expansion** | (1 - A₁/A₂)² | Area ratio dependent |
| **Sudden contraction** | 0.5 × (1 - A₂/A₁) | Area ratio dependent |

### Reference

Idelchik, I.E. (1986). "Handbook of Hydraulic Resistance", 2nd Ed., Hemisphere Publishing.

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
- IMEX pressure correction

The IMEX timestep is limited by both convection and the explicit wall/interphase drag relaxation.
An implicit pressure update does not remove the source stability restriction. The estimate excludes
dispersed-bubble drag when its implicit source option is enabled. Euler and Runge-Kutta retain
their acoustic CFL; this does not bound all explicit drag timescales. A newly appearing laminar
film can have arbitrarily fast relaxation, so the IMEX source limit may also become impractically
small. General phase-appearance stability requires implicit drag, without artificial phase-mass
or timestep floors. Wall forces use
regime-specific wetted geometry and are blended after integration: slug/churn volume weights are
applied once, while annular films and dispersed-liquid continuums use the full wall perimeter.

### Coupled Pressure-Momentum Correction

The opt-in coupled route corrects phase masses, phase momenta, compressible densities, and pressure
inside the same accepted substep. For a liquid-rich pressure outlet that physically permits phase
fallback, configure all four coupled options and make the nonlinear gate explicit:

```java
pipe.setEnableInterfacialPressure(true);
pipe.setImplicitInterfacialPressureCoupling(true);
pipe.setEnableCoupledPressureMomentum(true);
pipe.setAllowOutletPhaseBackflow(true);
pipe.setCoupledPressureMomentumMaximumIterations(24); // default
pipe.setCoupledPressureMomentumRelativeVolumeTolerance(1.0e-7); // default
```

The former 12-iteration default stopped the public Tengesdal progress probe near a
$6\times10^{-7}$ relative cell-volume residual. With 24 iterations, the 16-section Test 3 setup
completes 50/50 calls of 0.1 s; a 24-section refinement completes 100/100 calls of 0.05 s. Neither
rejects a nonlinear substep, and both keep gas, oil, water, liquid, and total discrete mass
residuals below $10^{-9}$. A coupled call that cannot complete its requested interval now throws and
reports accepted/requested time, residual/tolerance, iterations/cap, and whether pressure correction
was limited; it never returns partial or zero progress silently.

Before time marching, a no-transfer steady case can be checked phase by phase with
`getGasMassFlowProfile()`, `getOilMassFlowProfile()`, and `getWaterMassFlowProfile()`. The active oil
or water velocity is synchronized with the final bulk-liquid velocity at exact single-liquid
endpoints, so a stale inactive-phase split cannot distort the handoff outlet flux. Matching the inlet
and outlet steady fluxes is necessary but not sufficient: it does not establish a transient
phase-momentum fixed point or qualify liquid-rich/severe-slugging behavior.

After every evaluated window, inspect the sticky diagnostics, which reset on the next steady
`run()`:

```java
boolean failed = pipe.isTransientCoupledPressureMomentumFailureDetected();
boolean limited = pipe.isTransientCoupledPressureMomentumCorrectionLimited();
int rejected = pipe.getTransientCoupledPressureMomentumRejectedSubsteps();
boolean latestLimited = pipe.isCoupledPressureMomentumPressureCorrectionLimited();
```

This progress result is not a severe-slugging qualification. The pressure limiter still fires and
the 50-step liquid-outlet range is -18.55 to 6.88 kg/s versus the stored 0.375 to 4.03 kg/s
comparison. Do not tune public closures to that commercial trace; use the public Tengesdal
experiment for subsequent amplitude, period, mesh, and long-horizon validation.

### Experimental Unsplit Solver Foundation

`UnsplitTransientSolver` defines an isothermal common-time-level system with seven unknowns per
cell: three phase masses, three phase momenta, and pressure. It evaluates conservation at the
implicit midpoint and retains pressure-dependent volume closure in every cell. A fixed outlet
pressure belongs to the boundary face supplied to the model callback; it does not replace the last
cell's closure equation or trigger proportional outlet-cell mass repair.

The kernel provides scaled residuals, a colored block-stencil finite-difference Jacobian, line
search, fraction-to-boundary mass/pressure limits, and hooks that freeze and refresh donor/regime
active sets. The adapter delegates those hooks to an explicit, attempt-local active-set owner using
defensive state copies. When a donor, regime, or complementarity choice changes, the solver
re-evaluates the residual before every subsequent refresh, bounded by the per-iterate active-set
budget (default 20). Both the nonlinear solve and diagnostic Jacobian use the same frozen choices
for their base and perturbed columns. Partial freeze failures are cleaned up, with cleanup exceptions
suppressed beneath the original error. Its model callback must remain
transactional so Jacobian probes cannot advance accepted diagnostics or state.

`TwoFluidUnsplitModelAdapter` connects this kernel to the finite-volume flux/source operator
transactionally. It reconstructs trial sections from accepted clones, evaluates phase densities at
both selected-time and closure pressure, restores retained equation diagnostics after each probe, and
uses a prescribed pressure only in the external outlet momentum traction. The last cell still owns
its volume-closure equation, while outlet phase mass and energy remain conservative advective
fluxes.

The serializable solver result now distinguishes convergence, iteration/update budgets, a singular
Jacobian, an inadmissible step and failed line search. Callback counts include actual frozen-base,
refresh and rejected-trial evaluations rather than estimates.

`evaluateTransactional` returns the trial's immutable RHS and exact phase-face/source/mass-balance
ledger while restoring previously published diagnostics, including on failure. The optional
mechanical-force diagnostic retains its documented six gas/liquid columns. Its backflow flag belongs
only to the current trial. Callers must supply trial section clones and synchronize configuration on
the equations instance.

`TwoFluidUnsplitModelAdapter.prepareStep` checks a converged candidate independently against the
current selected-time operator and endpoint volume closure, then returns defensive endpoint clones and
the exact phase transport ledger with inventories integrated over each cell length. It rejects
inconsistent candidates and unsupported thermal, phase-transfer and separately split source modes.
Density coefficients use the common evaluation time at both rate and end pressure. Strict
`setConservativeEndpoint` recovery preserves all conservative values, retains positive trace-phase
velocity, and never normalizes holdup or caps velocity. Endpoint closure diagnostics still require
an explicit refresh. Preparation does not commit state, reports, clocks or streams; the legacy
primitive-recovery path is unchanged.

The tests verify a closed three-phase five-second fixed point and a nonuniform flowing candidate.
The synthetic eight-case **flowing** five-second matrix now completes through
`TwoFluidUnsplitIntegrator.prepareInterval`, using maximum requested steps of 0.05/0.025 s on
4/8 cells with Bestion stabilization off/on. A new prescribed-phase-flow inlet lets pressure
traction follow the trial first cell, and an independent phase-pressure consistency option
preserves pressure balance even without Bestion stabilization. Production defaults are unchanged.
The original fixed-inlet-pressure matrix passed only one case; the corrected raw fixed-step
matrix passes six. Both 4-cell / 0.05 s raw cases still reject at oil-water inversion at 1.400 s.
Those failures remain documented; the passing eight-case result permits bounded step subdivision.

The interval preparer verifies each substep independently and accumulates only its accepted phase
face/source transfers. Retry exhaustion discards the entire local interval without publishing a
partial endpoint, clock or diagnostic. The full five-second duration and original conservation
tolerances remain; tests also bound phase speeds and pressure excursions. This is nonlinear
convergence control, not temporal-error control or a change to the discontinuous inversion law.
These 40 m tests use prescribed isothermal densities, not an EOS flash or experimental data.
See [the model guide](../process/TWOFLUIDPIPE_MODEL.md#five-second-flowing-gate-boundary-correction-and-bounded-retries)
for the historical negative evidence, revised boundary contract and reproduction command.

`TwoFluidPipe.prepareUnsplitTransient` now prepares an interval from actual initialized pipe cells
on deep copies of the operator and inlet fluid. `getSectionSnapshots` provides independent accepted
cells. `createUnsplitDensityModel` supplies a frozen-composition, fixed-temperature SRK/PR phase
density response, anchored by a constant specific-volume offset to reproduce accepted densities
exactly. No flash repartitions mass during Newton probes. Initial density and occupied-volume
consistency are checked, and unsupported phase appearance/inflow is rejected. The pipe exposes a
separate maximum nominal step and honors its configured accepted-substep budget.

Real methane and methane/decane/water steady handoffs are tested over `1e-5 s`, including unchanged
streams, profiles, reports, clocks and equation state on success and failure. Additional tests cover
independent SRK/PR derivatives, serialization and inlet physical-property cache isolation. These
short handoffs do not establish a steady fixed point over an engineering time horizon. Energy,
phase/component transfer, heat, upstream storage and tracked/split slug sources are unsupported.
Viscosity, sound speed and temperature remain frozen. See the
[EOS preparation contract](../process/TWOFLUIDPIPE_MODEL.md#pipe-preparation-with-frozen-phase-eos-densities)
for supported phases, boundaries, units and an executable API example.

The default temporal method remains implicit midpoint. Explicit
`UnsplitTransientSolver.TimeIntegrationMethod.BACKWARD_EULER` evaluates at the endpoint and damps
stiff decaying modes, with the expected first-order accuracy. Each result captures its temporal
weight so later solver configuration cannot change preparation or its flux ledger.

Co-current fallback now reverses the correlation coordinate and inclination together for both
regime detectors and horizontal closure blends, while preserving signed transport and oil/water
slip. This corrects a reproduced false bubble/slug switch; it does not qualify countercurrent flow.
The bulk gas/liquid drag reaction now follows conservative oil/water mass, giving continuous
disappearance limits and dissipative gas-drag work for consistent recovered phase velocities.
The nonlinear linear solve separately factors exactly homogeneous closed blocks, preserving
absent phases without projecting endpoints or changing tolerances.
The optional `setUnsplitPressureInterpolationEnabled(true)` also couples the stationary alternating
pressure mode inside the conservative face fluxes. It uses each attempted step's temporal weight,
preserves linear pressure profiles, and leaves external transport unchanged. Closed-domain tests
check independent phase conservation, damping, donor selection and full/colored Jacobians.
It remains off by default and has no general nonlinear hydrostatic or severe-slugging qualification.
See [the direction and pressure-interpolation contract](../process/TWOFLUIDPIPE_MODEL.md#flow-direction-and-conservative-pressure-interpolation).

The actual Tengesdal nitrogen/Crystex riser fixture also completes three short **0.1 s** preparation
cases on 16/24 cells. The preceding preparation baseline's **5 s** matrix failed line search at locally prepared times
0.691015625 s (16 cells) and 0.5234375 s (24 cells), even after increasing the initial 256-substep
cap to honor the pipe's configured budget. Every failed local prefix is discarded. The source
includes an explicit opt-in reproduction of this failing gate; it is not counted as a passing
regression. These results block the subsequent 180/600 s characterization and experimental gates.

After the direction, exact-zero-block and bulk-drag corrections, the backward-Euler/interpolation
option completes all three 0.1 s cases in **1/2/2 steps with zero retries** at the original
conservation/volume tolerances. The five-second gate at `62aabb7` still fails: midpoint/no-interpolation
prefixes stop at 0.7250/0.6951/0.5359 s, and backward-Euler/interpolation prefixes at
0.6000/0.6258/0.4254 s for the same 16/0.1, 16/0.05 and 24/0.05 matrix. The reduced retry burden
is numerical progress, not physical qualification. The final affected suite passes **327 tests
in 45 classes**, including maintained three-phase steady refinement; the six explicit long-gate
failures are recorded separately in the [actual riser evidence](../process/TWOFLUIDPIPE_MODEL.md#actual-tengesdal-handoff-evidence).

The subsequent countercurrent bubble-domain correction rejects negative inferred void fraction
across a bubble-transport denominator pole, preventing a reproduced 0.620-to-305.31 N/m artificial
drag jump. The unchanged historical five-second matrix advances farther but still fails: midpoint
prefixes end at 0.8066/0.8063/0.9000 s, and backward-Euler/interpolation at
0.6684/0.6883/0.6563 s. Both local branch-constrained solutions at the next annular/slug obstruction
violate their own regime criteria. This is evidence for a constitutive transition gap; solver
tolerances and retry budgets remain unchanged. See the
[branch diagnosis](../process/TWOFLUIDPIPE_MODEL.md#countercurrent-bubble-criterion-and-the-remaining-transition-obstruction).

The separate `setCellFaceElevationProfile` option accepts N+1 finite-volume face elevations.
It integrates each specified terrain rise once, uses midpoint steady pressure with external-face
boundary offsets, and preserves signed gravity/energy work under nonuniform mesh refinement.
Legacy elevation samples retain their convention. These geometry tests do not establish general
transient hydrostatic well-balancing or a steady/unsplit fixed point; see the
[explicit face contract](../process/TWOFLUIDPIPE_MODEL.md#explicit-cell-face-terrain).
The separately named corrected-face riser represents the same 13.9032247068273 m rise on both
meshes and passes three 0.1 s preparations. Its five-second attempts at `477964b5` rejected at
0.8875/0.8014/0.5801 s; those results are kept separate from historical geometry measurements.
The preceding component/domain/terrain update at `477964b5` passed 365 affected tests across 53 classes,
including three slow component/phase/thermal/reference tests and maintained three-phase steady
refinement. Nine explicit five-second qualification failures remain separate from passing regressions.

The transaction/execution/Jacobian update at `1f65f683` passed 422 focused tests across 62 classes,
including four slow component/phase/thermal/reference tests. At that revision, fifteen riser
and one coarse-gas five-second cases failed separately. The outlet-consistency repair below
clears the coarse-gas case; the fifteen unsplit riser cases still fail. The later coupled-predictor repair restores all five
coupled-pressure progress regressions: coupled Euler, RK2, RK4, SSP-RK3 and IMEX now consistently
use centered face pressure while retaining AUSM mass and energy advection. The same pressure
response must not also include the gas-velocity-dependent explicit AUSM pressure term. The
unchanged five-second shared-closure and subcell-force cases complete with zero rejected
substeps. The separate unsplit and experimental qualification limits remain in force; see the
[predictor diagnosis and evidence](../process/TWOFLUIDPIPE_MODEL.md#countercurrent-bubble-criterion-and-the-remaining-transition-obstruction).

The existing named-component route publishes accepted interval-average component outlet flows.
`setTransactionalTransientEnabled(true)` now also stages the complete legacy pipe interval,
including component/thermal/slug state, storage, reports, clocks and downstream publication.
Connected stream, upstream-volume, thermal-calculator and layer identities are retained; other
owned submodels must be reacquired after acceptance. Supported serializable phases are concrete
SRK, PR, SRK-CPA and SRK-CPAs; pipe subclasses require a separate commit contract.

`setUnsplitTransientSolver(solver, maximumTimeStep)` separately selects an experimental,
always-transactional `runTransient` route. It retains original SRK/PR density and phase-composition
references across calls. Outlet composition derives from exact phase transfers only when each
phase has uniform frozen component mass fractions across cells and inlet. Nonuniform/changing
composition and negative phase outlet transfer reject without publication. General component
transport, energy, phase change and tracked slugs remain unsupported in this route.
Four-cell/0.1 s and eight-/sixteen-cell/0.05 s gas configurations now complete consecutive
calls covering five seconds at the unchanged `1e-10` nonlinear and `1e-8` conservation gates.
The outlet now uses the independently recovered phase fractions consistently with internal
fluxes and pressure sources. This removes a single-phase clipping kink that gave the Newton
matrix a derivative of the wrong sign. Midpoint/backward-Euler directional regressions
verify the correction, and the coarse-gas case now runs in ordinary CI. This remains bounded
execution evidence rather than general single-phase or multiphase mesh qualification.

The opt-in inclined film-bridging constraint closes the captured high-holdup annular/slug branch
conflict. Phase-relative Jacobian probes and stable phase-volume differences also correct trace
derivatives without changing tolerances or nonlinear budgets. With both enabled, the corrected-face
five-second cases reach approximately 1.303/1.881/1.736 s before rejecting at other regime
transitions. All six added gates still fail; the 180/600 s sequence remains blocked. Original
fixture results above are historical evidence, not results of the revised Jacobian and outlet.
A historical 16-cell backward-Euler replay stops near 0.66328125 s at a countercurrent
annular/slug switch: a small change in superficial gas velocity changes the interfacial force
from about 27.02 to 10.81 N/m. None of its phase velocities reaches the legacy caps.
The separate coupled-predictor repair restores the legacy five-second pressure regressions;
it does not repair this unsplit transition discontinuity or qualify the 600 s experiment.
See the [transaction and execution contract](../process/TWOFLUIDPIPE_MODEL.md#complete-transient-transactions-and-experimental-unsplit-execution)
and [current transition evidence](../process/TWOFLUIDPIPE_MODEL.md#inclined-film-eligibility-and-trace-phase-derivatives).

### Public severe-slugging qualification

Tengesdal Test 3 pressure metrics use `getPressureProfile()[0]`, the upstream inlet cell.
They do not measure the physical flowline–riser bend; earlier riser-base labels for this
implementation were incorrect. The inlet sample and experimental amplitude/period gates
are retained unchanged. A bend-pressure comparison requires a separate probe and qualification.

The candidate with the wall-force and slip corrections was tested on 6 September 2026 using
all five existing 100 s characterization trajectories. All seven active checks passed with their
original fixtures and assertions, including conservation, repeatability, mesh and outer-step
checks. The resolved reference gave a 29.768 kPa pressure amplitude and a 30.55 s
liquid-production period. Across the ensemble, amplitudes were 23.640–29.768 kPa and periods
were 11.30–30.55 s. These short trajectories do not qualify the sustained experimental cycle.

The same candidate completed the exact disabled 600 s Tengesdal Test 3 method, with its
acceptance targets unchanged, but failed five requirements:

| Metric | Observed | Required |
|--------|----------|----------|
| Pressure amplitude | 36.301 kPa | 68.6–127.4 kPa |
| Liquid-production period | Not resolved | 26.6–49.4 s |
| Completed settled liquid-production cycles | 0 | At least 2 |
| Initial steady flowline holdup | 0.330546 | 0.33858–0.34542 |
| Sticky pressure-correction limit | Activated | Inactive |

No substeps were rejected and outlet backflow was not clamped. Captured phase and total
mass-closure diagnostics were below $1.6\times10^{-15}$, but the phase-conservation assertions
came after the failing assertion group and were not reached. The mean settled flowline holdup
was 0.95; a 0.531 s pressure oscillation is not the required liquid-production cycle. The earlier
pressure/EOS-only candidate gave a 40.909 kPa amplitude and 56.167 s liquid-production period;
the new corrections do not establish improved experimental severe-slugging accuracy. Retaining
the acoustic step for explicit integrators resolves the temporary source-CFL stall, while the
physical qualification remains open. The holdup target is a historical numerical regression
value; the pressure amplitude and period targets derive from the experimental pressure trace.

The [public source](https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf),
Table 4-1, reports Crystex oil viscosity of 18.9 cSt at 40 °C. The current density-based surrogate
gives 5.60685 cSt at 40 °C and atmospheric pressure, 70.3% lower; its value at the assumed 25 °C
fixture temperature is 8.02907 cSt. A measured 25 °C value or a justified temperature relationship
is needed before changing that input. No property or closure is tuned to the desired transient
metrics. See the [full benchmark scope](../process/TWOFLUIDPIPE_MODEL.md#public-severe-slugging-benchmark)
for the unchanged targets, source assumptions and remaining limitations.

### Higher-Order Reconstruction

`MUSCLReconstructor` provides:
- Piecewise linear reconstruction
- Minmod, van Leer, superbee limiters
- Second-order accuracy in smooth regions

## Thermodynamic Coupling

When `setComponentTransportEnabled(true)` and `setIncludeMassTransfer(true)` are both selected
before initialization, transfer uses equilibrium phase mass fractions from the conserved local
component inventory. This preserves phase equilibrium when hydraulic slip changes residence
inventories. Without component transport, the reference-composition/no-slip source remains an
approximation. The sustained regression covers 120 s of gas, gas/oil and gas/oil/water flow with
heat and EOS updates active, plus heating/cooling transfer signs and conservative component
ledgers. Positive-flow boundaries and a fixed named-component slate remain required.

With component transport enabled, the downstream outlet composition follows the accepted boundary
component ledger rather than the latest inlet composition or the final cell's instantaneous phase
split. The outlet TP flash preserves total component flow; closed outlets carry zero mass. Each
component substep stages boundary/source/latent-heat ledgers together with inventory and discards
all of them on failure. This is component-substep isolation, not whole-pipe transient rollback.
Positive trace-phase component inventories receive the same bounded synchronization as larger
inventories instead of being discarded below the `1e-10 kg` hydrodynamic mass allowance. Empty
component phases tolerate hydrodynamic round-off within that allowance without creating components.
Mismatches beyond the unchanged
synchronization allowance reject without changing the accepted component ledgers.
See the [component publication contract](../process/TWOFLUIDPIPE_MODEL.md#validated-scope-and-fail-loud-boundaries)
for the supported boundaries and remaining limits.

For the supported conservative-slug coupling, phase and component source allocations plus their
partial-enthalpy latent source are frozen at the same integration stage. A moving slug/film
interface can otherwise advect composition before a post-step flash and make an already accepted
phase appearance impossible to reconstruct. A disappearing phase uses its conserved donor
composition in a forced single-phase property state; receiving composition still comes from the
equilibrium flash. The closed wet-gas coupling regression exercises conservative slug/film
tracking, water condensation, wall cooling, bounded named-component transport, and phase/total/
component/thermal closure on three outer-step partitions: 0.05, 0.025 and 0.0125 s over 0.05 s.
Closed coupled boundaries block the external face flux while retaining physical-cell inertia.
Aqueous-water transfer is `2.6681e-9` to `2.8518e-9 kg`, latent heat is `0.0058212` to `0.0062209 J`,
and mean cooling is `0.0305404` to `0.0305461 K`. Adjacent-grid water/heat sensitivity is below 4%;
marker-displacement sensitivity decreases from 3.14% to 1.99%. Length and accepted age remain
unchanged by partitioning, and whole-pipe transactions reproduce ordinary execution on the same grid.
See the [coupled regression results](../process/TWOFLUIDPIPE_MODEL.md#coupled-slugcomponentphasethermal-contract).
The seeded marker is numerical coupling
evidence, not spontaneous or experimentally qualified severe slugging.

The four-way conservative slug/film combination is validated with the single-stage Euler
integrator. It rejects multi-stage integration before state mutation until intermediate phase
appearance has a stage-local component inventory; this limitation does not change the existing
stage-weighted paths for other named-component transport cases.

Signed outlet backflow cannot be combined with named-component transport because no external
outlet composition is configured. Either setter order now fails before state mutation instead of
waiting for reverse inflow during an accepted transient step.



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

`ThreeFluidSection` obtains the water and combined-liquid levels by inverting the
circular-segment area:

$$A(h)=\frac{r^2}{2}\left(\theta-\sin\theta\right),\qquad\theta=2\cos^{-1}\left(1-\frac{2h}{D}\right)$$

The Newton iteration uses the exact derivative
$$\frac{\mathrm{d}A}{\mathrm{d}h}=2\sqrt{h(D-h)}$$
so the recovered levels, wetted perimeters, and interfacial widths remain geometrically
similar across pipe diameters. The regression test checks both the water layer and the
combined oil-water layer from 0.05 m to 2.0 m diameter.

## Simulation Modes: Steady-State vs Transient

The `TwoFluidPipe` supports two simulation modes: steady-state initialization via `run()` and incremental transient simulation via `runTransient()`.

### Steady-State Simulation: `run()`

The `run()` method attempts a steady-state initialization of the pipeline. This is typically called
once at the start to establish initial conditions before transient simulation; always inspect its
convergence flags.

An explicit pressure boundary participates in the iterative momentum and EOS-property solve.
Section flashes use the boundary-aligned local pressure and temperature, and convergence requires
the thermodynamic properties as well as the hydraulic profile to settle. This replaces a final-only
pressure shift that could leave densities evaluated at the old pressure. With prescribed flow and
outlet pressure, the pipe inlet pressure is calculated without modifying the inlet stream. An
explicit inlet pressure is included in the iteration when it supplies the pressure boundary.
Friction, holdup, slip and terrain closure defaults are retained; previously inconsistent results
at an explicit pressure boundary can change and must be rechecked on the tested revision.

Periodic steady flashes refresh transported oil/water volume fractions while preserving the
hydraulic in-situ split. Only a newly appearing second liquid is seeded from the flash. If one
liquid disappears, the remaining liquid retains the total hydraulic holdup for the next closure
update. The slip calculation first recovers the prescribed liquid mass flux, splits its transported
volume flow into oil and water, and synchronizes bulk liquid velocity and momentum with the phase
momenta. This keeps the phase split consistent with the specified liquid throughput.

For positive flow, the connected outlet retains the feed's total and component flow rates and is
TP-flashed at the final section pressure and temperature **after** flow normalization. Thermodynamic
and transport properties are then initialized before publication. Callers can read heat capacity,
enthalpy and phase properties, or pass `getOutletStream()` directly to another pipe, without an
additional stream run or TP flash. The outlet's equilibrium phase fractions are distinct from the
hydraulic in-situ holdups. This fixes the stale phase state and invalid heat capacity tracked in
[#3685](https://github.com/equinor/neqsim/issues/3685); downstream heat-transfer results produced by
affected versions should be recalculated. It does not qualify pipeline thermal accuracy against
experimental data.

The shared transient publication path still uses the accepted interval-average total outlet flux.
A closed outlet or clamped nonpositive net flux publishes zero inventory, whose intensive
thermodynamic properties are undefined. Positive-flow outlet flash or property-initialization
failures throw an exception before replacing the connected outlet fluid.

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
│    ├─ Iterative solver (max(100, 20 x sections) iterations)  │
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
│    ├─ Normalize outlet flow to the steady inlet flow         │
│    ├─ TP-flash at final section pressure and temperature     │
│    └─ Initialize properties and publish the outlet fluid     │
└──────────────────────────────────────────────────────────────┘
```

**Key characteristics:**
- **Inlet specification:** Uses feed flow, temperature and composition; the configured pressure boundary determines the absolute pipe pressure
- **Iterative convergence:** Pressure, holdup and flashed phase properties must settle together
- **Terrain-aware holdups:** Liquid accumulates at low points
- **Single call:** Establishes initial state for subsequent transient runs
- **Convergence limits:** read the outcome report (see below); outlet thermodynamic initialization failures throw

**Example:**
```java
TwoFluidPipe pipe = new TwoFluidPipe("Pipeline", inletStream);
pipe.setLength(5000);
pipe.setDiameter(0.3);
pipe.setNumberOfSections(100);
pipe.run();  // Steady-state initialization

if (!pipe.isSteadyStateConverged()) {
  if (pipe.isSteadyStatePressureFloorLimited()) {
    throw new IllegalStateException(
        "Line cannot deliver this rate at this inlet pressure");
  }
  throw new IllegalStateException("Steady state did not converge");
}

double[] pressures = pipe.getPressureProfile();
double[] holdups = pipe.getLiquidHoldupProfile();
```

**Checking the outcome.** The legacy flags describe the common failure modes, while
`getSteadyStateConvergenceReport()` exposes the termination reason and the solved residuals. A
profile is only trustworthy when the report is converged:

| Query | Meaning when true |
|-------|-------------------|
| `isSteadyStateConverged()` | The sweep met the 1e-4 tolerance and the profile is a solution |
| `isSteadyStateWallClockLimited()` | The wall-clock guard (default 300 s) stopped it early |
| `isSteadyStatePressureFloorLimited()` | One or more sections rest on the internal 1 bara pressure floor |

`SteadyStateConvergenceReport` distinguishes `CONVERGED`, `ITERATION_LIMIT`,
`WALL_CLOCK_LIMIT`, `PRESSURE_FLOOR_LIMIT`, and the pre-run `NOT_RUN` state. It reports the
dimensionless pressure-momentum, pressure-update, total-liquid-holdup, oil/water-split,
thermodynamic-property, and total-pressure-drop residuals against `getTolerance()`. Convergence
includes the mandatory final flash and unrelaxed holdup/split resweep, so that pass cannot silently
change the state after the convergence flag has been set.

The report also checks source-free total mass transport. `getMassFluxResidual()` is the maximum
over all sections of `abs(gasMassFlow + oilMassFlow + waterMassFlow - inletMassFlow)` divided by
`max(abs(inletMassFlow), 1e-12 kg/s)`. It must be below `getMassFluxTolerance()` (1e-8), including
after the final conservative-state initialization. A nonfinite flux fails this check. Reports
created with the older constructor have no mass-flux measurement and return `NaN` for these two
getters; reports produced by a new pipe solve always include the check.

Steady phase velocities follow `massFlow / (density * holdup * area)` without the legacy 100 m/s
gas or 50 m/s liquid caps. Those caps could discard mass while the pressure and holdup iterations
appeared settled (#3686). The transient boundary velocity guards are unchanged. Phase changes may
redistribute gas, oil and water flows, but their sum must remain equal to the inlet. Removing a
numerical velocity cap does not add a critical-flow or choking model; pressure-floor and all
existing convergence checks still apply.

```java
SteadyStateConvergenceReport report = pipe.getSteadyStateConvergenceReport();
if (!report.isConverged()) {
  throw new IllegalStateException("Steady state stopped at "
      + report.getTerminationReason() + "; liquid-split residual="
      + report.getLiquidSplitResidual() + "; mass-flux residual="
      + report.getMassFluxResidual());
}
```

The marching solver clamps section pressure at 1 bara so it stays numerically alive on a line with
no deliverability. That clamp is a fixed point of itself: the per-section change falls below
tolerance and the sweep would otherwise report success on a case that has no physical solution.
When the floor is touched, `isSteadyStateConverged()` is withheld and
`isSteadyStatePressureFloorLimited()` is set. `PipeBeggsAndBrills` throws
`Outlet pressure is negative` on the same condition.

**Direct electrical heating.** A uniform electrical heat input can be added in both steady-state
and transient runs, and works with wall heat transfer switched off:

```java
pipe.setDirectElectricalHeatingPower(10.0e6);        // W over the whole length
pipe.setDirectElectricalHeatingPowerPerMeter(135.4); // or W/m directly
```

The value is the power delivered *to the fluid*, so cable and coating losses must already be
deducted. In steady state each segment decays toward the balance temperature
`T_surface + q / (U * pi * D)` rather than toward the surface temperature, which is exact for a
uniform source and cannot overshoot. `PipeBeggsAndBrills.setDirectElectricalHeatingPower(double)`
uses the same convention, so the two models can be compared like for like.

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
for (int step = 0; step < 1000; step++) {
    // Change boundary conditions if needed
    if (step == 100) {
        inletStream.setFlowRate(15.0, "kg/sec");  // Flow increase
        inletStream.run();
    }

    pipe.runTransient(0.1, UUID.randomUUID()); // one UUID per physical step

    // Monitor results
    double outletFlow = pipe.getOutletStream().getFlowRate("kg/sec");
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

## Pressure Gradient Model

The steady-state solver uses `estimatePressureGradient()` to compute the total pressure drop per unit length, combining friction and gravity:

$$
\frac{dP}{dx} = \frac{dP}{dx}\bigg|_{friction} + \frac{dP}{dx}\bigg|_{gravity}
$$

### Mixture Properties

- **Density:** holdup-weighted — $\rho_{mix} = \alpha_G \rho_G + \alpha_L \rho_L$
- **Viscosity:** McAdams quality-based harmonic averaging — $\frac{1}{\mu_{TP}} = \frac{x}{\mu_G} + \frac{1-x}{\mu_L}$ where $x$ is vapor mass fraction

The McAdams model weights viscosity by mass flux rather than volume fraction, which better represents the momentum transfer in separated two-phase flows.

### Friction Factor

The `calcDarcyFrictionFactor()` method uses the Haaland equation for turbulent flow:

$$
f = \frac{0.25}{\left[\log_{10}\left(\frac{\varepsilon/D}{3.7} + \frac{6.9}{Re}\right)\right]^2}
$$

With 64/Re for laminar flow (Re < 2300) and linear interpolation through the transitional regime (Re 2300–4000).

### Transient Boundary Conditions

During transient simulation (`runTransient()`), the inlet pressure is **not** overridden from the inlet stream. Instead, the pressure profile is reconstructed by backward-marching from the fixed outlet boundary condition using the local pressure gradient:

$$
P_{i-1} = P_i + \frac{dP}{dx}\bigg|_i \cdot \Delta x
$$

This allows the inlet pressure to evolve naturally in response to changing flow conditions.

## Benchmark Validation

### Phase-limit and steady-boundary regression requirements

`TwoFluidPipeSteadyBoundaryThermodynamicsTest` requires phase densities consistent with independent
flashes at the reported section pressure and temperature, unchanged feed state, and a fixed-outlet
gas solution independent of its feed-pressure initial guess. It also exercises a short gas
transient with thermodynamic refresh and unchanged boundaries.

`TwoFluidPipeDynamicPhaseEnvelopeRegressionTest` defines seven phase combinations:

| Phase count | Present phases | Tests require |
|-------------|----------------|---------------|
| One | Gas | Exactly absent oil and water |
| One | Oil | Exactly absent water and no gas void |
| One | Water | Exactly absent oil and no gas void |
| Two | Gas + oil | Positive active inventories and exactly absent water |
| Two | Gas + water | Positive active inventories and exactly absent oil |
| Two | Oil + water | Positive liquid inventories and no gas void |
| Three | Gas + oil + water | Positive, independently conserved phase inventories |

Each row requires a converged stationary reference, an infinitesimal handoff without a pressure or
holdup jump, and a separate short 10% inlet-flow perturbation with correct phase inlet masses and
closing phase balances. The final stationary state must also match independently flashed local
phase mass fluxes and the mass-weighted liquid specific enthalpy after oil/water slip. Both RK2
and IMEX pressure correction are exercised. These compact cases
disable phase transfer and thermal evolution and defer transient thermodynamic refresh to isolate
mechanical transport. They do not establish long-time settling or broad dynamic accuracy; report
execution results for the tested revision separately.

Oil/water specific enthalpy in J/kg uses mass weights, $h_L=(m_Oh_O+m_Wh_W)/(m_O+m_W)$,
with the corresponding phase mass-flow weights at initialization. It reduces to the active liquid
enthalpy in the pure-oil and pure-water limits. This preserves the enthalpy of the combined liquid;
volume weights are inappropriate for a mass-specific property.

The new phase matrix exercises a one-second 10% feed-rate step. An explicit rerun of the already
disabled 1800 s liquid-rich fixed-point case still gives **5.7570%** inventory drift, exceeding its
unchanged **5%** limit. Long-duration liquid-rich behaviour remains unqualified. Metastable trace
continuity is tested with frozen thermodynamic properties; separate public equilibrium tests
require dissolved trace liquid to disappear from every cell, including the inlet.

The public severe-slugging, long-horizon liquid-rich and unconverged free-water steady cases retain
their existing qualification limits. See the
[model validation status](../process/TWOFLUIDPIPE_MODEL.md#validation-status) for the full scope.

Transient inlet conditions preserve the first physical cell's EOS density at its solved
pressure. The coupled solver uses an external feed face; the uncoupled solver retains its inlet
momentum treatment. The unchanged near-zero-time handoff
and all boundary-condition regressions cover this behavior. Annular holdup now uses
$\alpha_L=S\lambda_L/[1+(S-1)\lambda_L]$ consistently with $S=v_G/v_L$ in both closure
paths; dedicated regressions disable minimum-slip enforcement to expose the algebra itself.

### Existing comparison benchmarks

The `TwoFluidPipeBenchmarkTest` provides 19 tests validating `TwoFluidPipe` against `PipeBeggsAndBrills` and analytical results. Key benchmark numbers:

| Test Case | TwoFluidPipe / Beggs&Brill Ratio | Notes |
|-----------|----------------------------------|-------|
| Single-phase gas (horizontal) | 0.98 | Excellent agreement |
| Two-phase GLR 0.50 | 0.81 | Liquid-dominated, acceptable |
| Two-phase GLR 0.75 | 1.16 | Intermediate GOR |
| Two-phase GLR 0.95 | 1.08 | Gas-dominated wet gas |
| Vertical riser gravity dP | 1.04 bar | Matches $\rho g H$ |
| Diameter scaling (6"/12") | 33.7× | Close to theoretical ~32× ($D^{-5}$) |
| Transient holdup evolution | 0.19 → 0.09 | Holdup decreases after 100% flow increase |

### Running Benchmarks

```bash
./mvnw test -Dtest=TwoFluidPipeBenchmarkTest
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

## Terrain-Induced Slug Tracking

The TwoFluidPipe model detects liquid accumulation at terrain low points and tracks slug
markers through the pipe. During transient tracking it uses
`LiquidAccumulationTracker.observeConservativeAccumulation(TwoFluidSection[], double)` in
all enabled tracking modes. Zone volume is measured from conserved oil and water mass and
their phase densities, allowing both filling and drainage. Observation and marker emission
leave cell holdup, velocity, mass and momentum unchanged; emitting a marker also leaves the
measured zone volume unchanged. The legacy empirical `TransientPipe` tracker path is separate.
See the [conservative observation contract](../process/TWOFLUIDPIPE_MODEL#conservative-terrain-accumulation-observation).

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

### Known limitations

Commercial transient multiphase simulators are not used as a reference: their licence terms
generally prohibit publishing benchmark comparisons and prohibit using the software to develop
similar software, so no closure here is tuned to one. The observations below are model-internal,
measured on a 73.8 km subsea gas-condensate export line at 200 bara inlet (see
[TwoFluidPipe detailed review](two_fluid_model_review)):

- **Pressure drop** reproduces the rate exponent across 4 to 12 MSm3/d, rising from about 2.1 at
  low rate to about 3.1 at high rate. Beggs–Brill sits far above `TwoFluidPipe` on the same cases,
  because its two-phase friction multiplier is an extrapolation at this liquid loading.
- **Arrival temperature** responds correctly to heating: 10 MW of DEH raises it 17.4 K while the
  pressure drop rises 15.0%.
- **Terrain response comes from the momentum balance, not a multiplier.** The annular film closure
  now carries the gravity term, so holdup responds to inclination as `sin(theta)`. At 4 MSm3/d the
  maximum holdup fell from 0.222 to 0.022 when the empirical multiplier was removed.
- **The historical three-phase free-water case remains unqualified.** With 15 m3/hr of free water,
  the earlier 73.8 km solve was wall-clock limited after 4078 iterations at a 1200 s budget.
  Its pressure drop was stationary between 300 s and 1200 s budgets while the oil/water split
  did not converge. This long case has not been rerun for the pressure-boundary and split
  corrections described here; the compact regressions do not establish that it is resolved.
  Always check `isSteadyStateConverged()` on a water-bearing line.
- The reproducible 3 km, 10-degree uphill gas/oil/water fixture converges on 30 and 60 cells with
  positive oil-over-water slip, closed phase volumes and closed phase mass flow. Refinement changes
  arrival pressure from 7.469114 bar to 7.429146 bar (0.538%) and mean liquid holdup from 0.274301
  to 0.271630 (0.983%). This is numerical evidence for the compact synthetic fixture, not
  experimental qualification or evidence for the unavailable 73.8 km input.
- **Pressure drop does not always respond to a temperature change.** In an earlier revision, adding
  10 MW of heating raised the arrival temperature 22 K but left the computed pressure drop
  unchanged; warmer gas at fixed mass rate is less dense and ΔP ~ G²/ρ must rise. Treat pressure
  drop from a case whose temperature field changes as indicative.
- **Legacy steady terrain/slug closures include holdup bounds.** These remain distinct from
  transient accumulation observation, which no longer adds holdup or damps velocity in
  `TwoFluidPipe`. The correction alone does not qualify physical valley inventory.
- The steady-state solve is an under-relaxed fixed-point sweep and can fail to settle on long
  transmission lines; always check `isSteadyStateConverged()`. The transient solve is a genuine
  conservative scheme (null-test drift 0.00 bar, closing mass balance).

## References

1. Bendiksen, K.H. et al. (1991) - "The Dynamic Two-Fluid Model OLGA: Theory and Application", SPE Production Engineering
2. Taitel, Y. and Dukler, A.E. (1976) - "A Model for Predicting Flow Regime Transitions in Horizontal and Near Horizontal Gas-Liquid Flow", AIChE Journal
3. Issa, R.I. and Kempf, M.H.W. (2003) - "Simulation of Slug Flow in Horizontal and Nearly Horizontal Pipes with the Two-Fluid Model", Int. J. Multiphase Flow
4. Liou, M.S. (1996) - "A Sequel to AUSM: AUSM+", J. Computational Physics
5. Drew, D.A. and Lahey, R.T. (1987) - "The Virtual Mass and Lift Force on a Sphere in Rotating and Straining Inviscid Flow", Int. J. Multiphase Flow, 13(1), 113-121
6. Hart, J., Hamersma, P.J., and Fortuin, J.M.H. (1989) - "Correlations Predicting Frictional Pressure Drop and Liquid Holdup during Horizontal Gas-Liquid Pipe Flow with a Small Liquid Holdup", Int. J. Multiphase Flow, 15(6), 947-964
7. Andreussi, P. and Persen, L.N. (1987) - "Stratified Gas-Liquid Flow in Downwardly Inclined Pipes", Int. J. Multiphase Flow, 13(4), 565-575
8. Idelchik, I.E. (1986) - "Handbook of Hydraulic Resistance", 2nd Ed., Hemisphere Publishing

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

- [Fluid Mechanics README](../fluidmechanics/) - Low-level pipe flow modeling
- [TwoPhasePipeFlowModel](../fluidmechanics/TwoPhasePipeFlowModel) - Non-equilibrium mass/heat transfer
- [TwoPhasePipeFlowSystem Development Plan](../fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan) - Implementation status
- [Pipeline Index](pipeline_index) - Overview of all pipeline models


### Shared mechanical slug option

An opt-in reduced liquid-wetted slug force balance is available through
`setSharedSlugForceBalanceEnabled(true)`, together with interfacial pressure and coupled
pressure-momentum enabled before `run()`. It shares the steady and transient mechanical
forces and bypasses incompatible slug minimum-slip/terrain holdup overrides. It changes
slug steady predictions and does not qualify the default correlation model, three-phase
liquid slip or experimental severe slugging. See the
[shared slug closure configuration and measured null result](../process/TWOFLUIDPIPE_MODEL#opt-in-shared-slug-force-balance).

### Experimental implicit slug/film friction and diagnostics

Conservative slug/film face reconstruction can now be paired with an optional local
implicit friction step. Previously its fluxes used separate body/film velocities but
its friction sources used only the mean cell state. The option evaluates the shared
slug wall/drag closure in the body and the annular wall/drag closure in the film,
then averages updated momenta with the reconstructed length fractions. Phase masses
and total energy remain unchanged by this friction step. Gravitational and pressure
terms remain in their existing conservative transport/source treatment.

```java
pipe.setSharedSlugForceBalanceEnabled(true);
pipe.setEnableInterfacialPressure(true);
pipe.setEnableCoupledPressureMomentum(true);
pipe.setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN);
pipe.setConservativeSlugForceIntegrationEnabled(true);
pipe.setMomentumForceDiagnosticsEnabled(true);
```

The option defaults to false and requires conservative tracking and shared slug forces;
it cannot be combined with the separate stiff-bubble-drag split. Wall velocity and
interphase slip are advanced through bounded scalar backward-Euler solves, so a
falling film has its own signed wall resistance without an explicit friction time-step
collapse. The explicit RHS omits these same friction forces in active reconstructed
cells to avoid applying them twice. The local wall/interface splitting is first-order;
calling it on both sides of transport does not by itself establish second-order accuracy.
The existing cell-mean steady initialization is retained. This is an experimental
extension, not a qualified steady body/film equilibrium or a complete churn/annular
transition model. Independent oil/water subcell dynamics still require qualification.

`getTransientPressureLimitCount()` counts every bounded nonlinear iteration, including
rejected attempted substeps, since `run()`. `getFirstTransientPressureLimitTime()` gives
the first attempted-substep start time, and `getMinimumTransientPressureDamping()` gives
the smallest actual Newton damping. With no events they return 0, NaN and 1 respectively.
The dedicated Log4j logger `neqsim.process.equipment.pipeline.TwoFluidPipe.pressureLimits`
at DEBUG records attempted time step, iteration, limiting cell, active bound, and proposed
and damped pressure correction. The solver's immutable `PressureLimitEvent` list covers
the latest nonlinear solve; cumulative counters preserve events between outer samples.

`getLastMomentumSourceForcesPerLength()` returns a defensive snapshot indexed by cell,
then gas wall, liquid wall, gas interface, liquid interface, gas gravity and liquid gravity,
in signed N/m. It samples the latest RHS state, which may precede the final accepted
pressure correction. It excludes pressure/advection fluxes, mass-transfer momentum and
separate oil-water exchange. When subcell friction is implicit it reports the mechanical
forces before their removal from the explicit RHS; it is not the time-integrated implicit
impulse. Sampling defaults to off and does not modify the trajectory.

The progress fixture checks convergence, conservation and consistency between limiter
counts and the sticky flag. Whether that fixture encounters a limit varies across
runtimes; neither mandatory presence nor mandatory absence is a portable progress
contract. The full experimental benchmark retains its separate no-limiter requirement.
A dedicated limited fixture verifies event recording and persistence after recovery.

The unchanged 600 s public-case diagnostic gives the following comparison. Only the
optional reconstruction/source configuration changes; experimental thresholds and
pressure sampling remain unchanged.

| Quantity | Shared mean-cell closure | Implicit body/film friction |
|---|---:|---:|
| Inlet peak-to-peak pressure | 51.315 kPa | 65.163 kPa |
| Inlet p10–p90 pressure width | 26.911 kPa | 25.314 kPa |
| Minimum mass-based riser liquid holdup | 0.935768 | 0.862846 |
| Mean mass-based riser liquid holdup | 0.982796 | 0.980280 |
| Completed liquid-trough intervals | 15, irregular | 0 under the unchanged algorithm |
| Maximum phase mass residual | 1.57e-15 | 1.27e-15 |
| Rejected coupled substeps | 0 | 0 |

The larger peak-to-peak excursion is not a demonstrated improvement in sustained
severe-slugging accuracy: the central pressure width decreases, a valid liquid-cycle
period is absent, and pressure limits still occur. Amplitude remains below the 68.6 kPa
lower acceptance bound. Initial holdup, experimental amplitude/period and limiter-free
operation remain unqualified. No experimental gate is enabled or relaxed by this option.
The diagnostic-only baseline reproduces all 6,000 prior TRACE samples exactly and
records 387 bounded nonlinear iterations, all limited by correction size; 269 are in
cell 7 before the bend. The prior outer-step latest flag exposed only five samples.

For ordinary three-phase calculations, flash updates now reuse
`TwoFluidSection.updateThreePhaseProperties()` for the in-situ mixture density and
viscosity, as the hydraulic split already does. A separate Brinkman calculation at
each flash previously forced a three-sweep viscosity/holdup cycle in the uphill
water/oil regression. Steady convergence now also checks liquid viscosity and
surface tension changes after a flash, in addition to phase densities and composition.

A supporting 100 s run with the outer reporting/advance interval reduced from 0.1 s to
0.05 s completes without rejected substeps, but changes peak-to-peak pressure from
43.369 to 58.071 kPa. This sensitivity is further evidence that the optional model is
not yet numerically or experimentally qualified for severe-slug predictions.
