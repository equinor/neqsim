---
title: TwoFluidPipe Model Documentation
description: The NeqSim `TwoFluidPipe` model implements a transient multiphase-flow solver with phase-resolved gas, hydrocarbon-liquid, and aqueous-liquid conservation equations.
---

# TwoFluidPipe Model Documentation

## Release scope and current validation

The canonical status vocabulary, row-by-row evidence, executable example, and
supported-use boundary are maintained in the
[TwoFluidPipe Evidence Matrix and Supported Envelope](twofluidpipe-evidence-matrix).
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
| Conservative Lagrangian tracking with implicit slug/film friction | 600 s completes, but amplitude, cycle and pressure-limiter gates remain open; substantial time-step sensitivity | Experimental; disabled by default |

The under-2% result requires all three settings before initialization:
`setSharedSlugForceBalanceEnabled(true)`,
`setEnableInterfacialPressure(true)`, and
`setEnableCoupledPressureMomentum(true)`.
Leaving shared slug forces disabled does not repair the earlier default-mode
5.757% inventory-drift result. The steady slug-unit closure and steady-consistent
transient initialization are now enabled by default; those changes do not qualify
the severe-slugging benchmark or replace its required opt-in settings.

The separate `setConservativeSlugForceIntegrationEnabled(true)` option remains
experimental and off by default. Its 65.163 kPa inlet-pressure amplitude is below
the unchanged 68.6 kPa lower bound; the unchanged liquid-trough detector finds no
completed cycle intervals, and pressure limits still activate. Do not use these
results as qualification of slug loads or extreme pressure transients.

The public Mohmmed slug-kinematics sweep remains experimentally unqualified:
its baseline passes 3/9 fixed gates and its mesh/time-step refinement is
non-monotone. The compact uphill gas/oil/water case is numerically verified on
30 and 60 cells, but the historical 73.8 km free-water input is unavailable and
is not covered. The coupled component/phase/thermal case is a closed, seeded
marker conservation test; it does not qualify spontaneous or sustained slugging.

Later sections preserve earlier measurements to explain the repair history.
Read those results with their stated configuration and revision; they are not
additional claims about the current defaults or released experimental accuracy.

### Opt-in static local geometry reference

A cell-average holdup-derived level is not necessarily its maximum local depth.
`TwoFluidSection.getHydrostaticCellGeometry()` reconstructs a horizontal plane
inside one straight finite-volume cell while preserving its existing total liquid
volume. The immutable result reports end/min/max normal depths, interface and
liquid-wall areas, minimum gas cross-section and volume residual in SI units.
The implementation is in the existing `GeometryCalculator`; no extra solver,
state owner, runtime switch or default closure update is introduced.

This read-only query is a static geometric reference, not a prediction of an
arbitrary moving interface, stable pool, oil/water layering, gas-flow capacity or
finite-rate evaporation. It does not alter holdup, conservative inventories,
friction or transfer area used by the transient solver. Neighboring cell planes
need not share one global level. The straight-cell arclength abstraction excludes
capillarity and curved-pipe volume corrections. Invalid/near-vertical or
unresolvable cases fail explicitly. Long inclined trace pools are solved from their small positive
leading-edge depth, rather than reconstructing that depth by subtracting nearly equal centre and
slope-range values. This preserves representable wedge volume and area without an inventory floor;
traces below floating-point resolution still fail explicitly. See the
[reporting guide](../wiki/two_fluid_reporting_and_validation#static-subcell-depth-and-area-diagnostic)
for units, supported limits and executable regression evidence.

### Optional two-donor Rusanov phase-flux contract

The default pipeline operator remains AUSM+. `AUSMPlusFluxCalculator.calcRusanovFlux` is also
available as a local query, and `TwoFluidPipe.setRusanovInternalFluxEnabled(true)` selects it on
internal independently resolved gas/oil/water faces. Phase inputs carry total specific
enthalpy including kinetic energy. Rusanov dissipates `alpha * (rho * H - p)`, while the physical
energy flux remains `alpha * rho * u * H`. It returns consistent pressure/area metadata and a
Rusanov holdup flux. Exactly absent phase properties are unused; positive traces use the same
equations. Invalid occupied states, unrepresentable interface holdup and nonfinite outputs reject explicitly.
The [executed local-flux record](../../src/test/resources/neqsim/process/equipment/pipeline/rusanov-total-energy/README.md)
includes independent ideal-gas controls, dry/trace limits, default-AUSM equivalence and 324 SRK
gas/oil local queries. SRK controls explicitly convert EOS internal energy to the existing
physical-density mechanical-energy convention; they do not validate translated-EOS thermodynamics
or elapsed pooling.
Explicit updates require an appropriate Courant bound and admissible thermodynamics; there is
no unconditional stability guarantee. The operator retains nonnegative left-to-right and
right-to-left mass streams separately, carries both through Runge-Kutta stage weights, pressure
work and transactional named-component transport, and verifies that their difference equals the
net hydrodynamic flux. Pressure-only numerical energy diffusion remains a separate energy term.

Enable water/oil slip before selecting the operator:

```java
pipe.setEnableWaterOilSlip(true);
pipe.setRusanovInternalFluxEnabled(true);
pipe.setComponentTransportEnabled(true); // when evolving composition is required
```

The combined-liquid one-upwind split, AUSM pressure interpolation, reconstructed slug faces and
the current one-direction unsplit complementarity block reject this option explicitly. These are
algorithmic ownership boundaries, not automatic fallbacks. This does not repair the remaining
dry/wet pressure-moment front or qualify pools, inlet refill, physical evaporation, shutdown/restart
or hydraulic pigging.

The separate experimental `IdealGasPiggingModel` is not a `TwoFluidPipe`
closure. It couples a rigid pig to two well-mixed calorically perfect gas
control volumes with conservative moving-volume work and ideal-gas orifice
bypass. Its launcher/receiver boundary search reuses the reversal-safe
`PigMotion.advanceToPosition` API and its analytical work ledger. Its analytical,
conservation and refinement evidence cannot qualify
distributed pressure waves, multiphase moving boundaries, swept liquid,
receiver liquid capacity, residual film, shutdown/restart or severe slugging.
See [pig motion and reduced-order gas coupling](pig-motion.md).

### Stratified hydrostatic pressure moments

The immutable pressure-moment result also exposes signed phase first area moments about the pipe
centre and `getGravityPotentialEnergyPerLength(gasDensity, liquidDensity, normalGravity)` in J/m.
Normal gravity is `g*cos(inclination)`. These explicit, read-only cross-sectional energy budgets
have independent analytical/80-digit reference qualification; they add no serialized fields and
do not enter the transient solver or thermodynamic recovery. Centreline-elevation, internal,
kinetic and wall energy remain separate. See the
[executed gravity-energy record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/gravity-energy/README.md).

The opt-in geometry query `GeometryCalculator.calculateStratifiedPressureMoments(depth, diameter)`
returns immutable signed first moments `getLiquidMoment()` and `getGasMoment()` in m3, plus
`getLiquidArea()` and `getGasArea()` in m2. Depth is normal to the circular pipe cross-section.
For interface depth $h$, height $y$ above the invert and chord width
$w(y)=2\sqrt{y(D-y)}$, the definitions are

$$J_l=\int_0^h(h-y)w(y)\,dy,\qquad J_g=\int_h^D(h-y)w(y)\,dy.$$

Thus $J_l\geq0$, $J_g\leq0$, and $dJ_k/dh=A_k$. The hydrostatic contribution to
phase pressure traction is $\rho_k g\cos(\theta)J_k$ in N; the separate interface-pressure
contribution is $p_I A_k$. These are signed pressure moments about the interface,
including the gas region above it, rather than moments about the pipe invert.
The method integrates the smaller segment with positive 16-point quadrature and recovers
its complement from the exact full-section moment. Empty phases have exactly zero moment;
unrepresentable geometry fails explicitly without changing the supplied depth.

`StratifiedPressureMomentsTest` verifies independent angular quadrature, analytical half/full
sections, trace limits, complement symmetry, area derivatives, serialization and signed
finite-volume gravity/traction identities. The [executed numerical record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/README.md)
retains every case, refinement and measured opt-in query cost. The geometry query remains
side-effect free.

The paired-fraction query `calculateStratifiedPressureMomentsFromHoldups(gas, liquid, diameter)`
inverts the minority area with a relative tolerance and algebraic reflection. The pressure operator
uses this query so a positive gas or liquid trace retains its own area even when the complementary
fraction rounds to one. Recovered occupied-volume weights are checked against the existing
1e-10 closure bound and converted to geometric fractions only; conserved variables are unchanged. The historical `calculateFromHoldup` depth floor and absolute area tolerance
are unsuitable for this pressure-work adapter and are not used by it. An independent 162-section,
80-digit series inversion covers both phase limits and positive fractions down to `1e-30`; see the
[coupling record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/coupling/README.md).

**Transient qualification blocker:** with the switch enabled, the historical 12/24-cell rich-gas
ramp currently rejects the requested 0.5/0.375 s intervals after 0.25 s accepted time on both meshes.
The next face-volume sum exceeds the unchanged 1e-10 closure bound; primary-state rollback passes.
See the coupling record and executable `qualify_ramp.py`. This option has instantaneous analytical
operator evidence and must not be treated as a qualified blowdown or pooling model.

`TwoFluidPipe.setStratifiedHydrostaticPressureMomentsEnabled(true)` now couples those moments to
the finite-volume phase momentum and total-energy operators. The solved thermodynamic pressure is
the interface pressure; each face receives $F_k=\rho_k g\cos(\theta)J_k$, and its energy flux receives
$Q_kF_k/A_k$ with the same signed mass donor used by phase advection. Internal forces telescope,
closed walls retain traction without mass or energy leakage, and exact all-gas/all-liquid endpoints
need no seeded phase. `getLastStratifiedHydrostaticPressureForces()` reports inlet, internal and
outlet forces in N. The option is off by default, branches before geometry evaluation when disabled,
and restores its diagnostic during transactional trials.

The qualified circular geometry owns one gas/liquid interface and one constant diameter per face.
Simultaneous positive oil and water or a diameter step therefore fail explicitly. This coupling is
analytical/numerical operator evidence, not elapsed pool retention, terrain drainage, cooldown,
restart, severe-slugging or pigging qualification. Capillarity, waves, entrainment, separate
oil/water interfaces and predictive hydrocarbon evaporation remain outside this API. See the
[coupling record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/coupling/README.md).


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
for backward compatibil