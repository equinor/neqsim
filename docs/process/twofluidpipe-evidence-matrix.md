---
title: TwoFluidPipe Evidence Matrix and Supported Envelope
description: Evidence levels, supported operating envelope, executable examples, and explicit limitations for steady and transient TwoFluidPipe use.
---

# TwoFluidPipe Evidence Matrix and Supported Envelope

This page is the qualification boundary for **TwoFluidPipe**. It separates available code from
numerical verification and public experimental qualification. A completed calculation, finite
profile, small pressure change, or stationary pressure trace is not by itself evidence that the
model converged or represents the requested physics.

## Evidence labels

| Label | Meaning |
|---|---|
| **Implemented** | The public configuration and runtime path exist. This says nothing about convergence or accuracy. |
| **Numerically verified** | An executable test checks relevant conservation, convergence, boundedness, sensitivity, or refinement contracts. |
| **Experimentally qualified** | A reproducible public measured-data comparison passes its declared error gates, with source provenance and uncertainty or acceptance definitions. |
| **Not qualified** | A required numerical or experimental gate fails, is unavailable, or has not been run. The result must not be promoted by loosening the gate. |
| **Unsupported** | The configuration is rejected because the governing boundary or state information is missing. |

## Current evidence matrix

The transaction/execution update at `1f65f683` passed 422 focused tests in 62 classes, including four
separately selected slow coupled/reference tests. Fifteen riser and one coarse-gas five-second gates failed separately
at that revision. The later outlet-consistency repair clears the coarse-gas gate and promotes it to ordinary CI;
the fifteen unsplit riser gates remain unsuccessful, with a countercurrent annular/slug force discontinuity
isolated in a failing replay. The subsequent coupled-predictor repair restores all five coupled-pressure progress regressions
without rejected substeps. These counts distinguish the verified runtime contracts below from
the incomplete flow qualification.

The matrix was refreshed on current master `1f6d1c12827d844e69cdf6f38d65d1b5276f831a` before the
directional-ledger work. All fifteen unchanged opt-in five-second riser cases still fail. Historical
midpoint 16/0.1, 16/0.05 and 24/0.05 stop at 0.82734375, 0.775 and 0.63125 s; historical
backward-Euler/interpolated-pressure cases stop at 1.3015625, 1.840625 and 0.95 s (the last reaches
the iteration limit; the rest fail line search). Explicit-face-terrain cases stop at 1.050390625,
1.153515625 and 0.572265625 s. The six film-constrained historical/face cases stop at 1.3015625,
1.275, 0.925, 1.250390625, 1.817578125 and 1.684375 s, all by line-search failure. No 180 s or
600 s run is justified until this unchanged five-second matrix passes.

| Capability and configuration | Implemented | Numerically verified | Experimentally qualified | Evidence and use boundary |
|---|---:|---:|---:|---|
| Positive-flow steady gas/liquid pressure, holdup, thermal and terrain profiles | Yes | Yes | No general claim | Require the complete steady convergence report to be converged, every residual below its recorded tolerance (including total phase mass flux below `getMassFluxTolerance()`), and no pressure-floor or wall-clock termination. Repeat mesh sensitivity for the actual geometry. |
| Steady gas/oil/water on the compact 3 km, 10-degree uphill fixture | Yes | Yes | No | The 30/60-cell results differ by 0.538% in arrival pressure and 0.983% in mean liquid holdup. All three phases remain present and the final thermodynamic/holdup reconciliation is inside the unchanged 1e-4 tolerance. The unavailable historical 73.8 km case is not covered. |
| Liquid-rich unchanged-boundary transient with shared slug force balance, interfacial pressure, and coupled pressure/momentum | Opt-in | Yes | Not applicable | Over 1,800 s, inventory drift is 1.323% at 40 cells and 1.358% at 80 cells, below the declared 2% fixture gate, with total-mass closure. This does not qualify slug loads or another operating envelope. |
| Default liquid-rich unchanged-boundary transient | Yes | No | No | The recorded 1,800 s inventory drift is 5.757%, above the unchanged 5% gate. Do not infer default-mode qualification from the opt-in shared-force result. |
| Mohmmed et al. public horizontal air/water slug kinematics | Harness and data implemented | Conservation only | **Failed** | At 40 cells and 0.05 s outer steps, 3/9 comparisons pass, MARE is 1.0402, and maximum absolute relative error is 3.2147. The 40/80-cell and 0.05/0.025 s sweep is non-monotone; steady starts are unconverged and pressure-floor limited and transients clamp outlet backflow. The fixed 20% speed and 30% length/frequency gates remain unchanged. |
| Conservative severe-slugging characterization against the public Tengesdal envelope | Opt-in | Five-second coupled progress restored | **Failed** | The earlier 600 s run gave 65.163 kPa, below the 68.6 kPa lower gate, without required repeated settled cycles. Centered pressure traction restores the five-second shared-closure and subcell-force regressions while retaining the valid bubble-domain guard. Short numerical progress does not qualify the experimental amplitude, period or sustained-cycle gates. |
| Conservative total-energy thermodynamic recovery | Experimental opt-in | Ideal-gas blowdown/filling, closed heating, fixed three-phase composition transport and closed hydrocarbon condensation/reheat refinement | No | `setUseConservativeEnergyRecovery(true)` requires coupled Euler and named components, with a single energy owner and complete-interval rollback. Check `getLastConservativeEnergyReport()` first-law, occupied-volume and EOS-state residuals. `TwoFluidPipeConservativeTransitionTest` retains positive relaxation tails; flowing phase appearance/disappearance, arbitrary blowdown/restart and asset evaporation-time qualification remain open under #4595/#4597. |
| Conservative stiff wall/interface friction through first oil appearance | Automatic within conservative-energy Euler | Native-force/matrix consistency, analytical source limits and bounded rich-gas regressions | No | `ConservativeWallFrictionTest` and `ConservativeInterfacialDragTest` check the frozen-resistance source, passive wall work, trace conditioning, oil/water slip, momentum and unchanged total energy. `TwoFluidRichGasDragQualificationTest` crosses the former 31.75–32.375 s rejections through 33 s on 12/24 cells. This is first-order numerical source qualification; complete blowdown, terrain pooling, restart and predictive evaporation rates remain unqualified. |
| Flash-driven phase appearance/disappearance with phase and energy ledgers | Yes | Yes | No | Check gas/oil/water and total mass, transfer closure, temperature sensitivity, and latent-inclusive energy balance. Record EOS, mixing rule, composition, pressure, temperature, relaxation time, mesh, and time step. |
| Named-component advection for supported positive-flow boundaries | Yes | Yes | No | Require every named-component ledger, bounded normalized phase fractions, phase/component synchronization, and component-sum closure. Every representable nonzero signed face transfer carries composition; mass tolerances scale audits rather than erase a transfer. |
| Same-step source/incoming-funded advection of a newly appearing phase | Yes | Reduced transport and bounded pipeline regression | No | `TwoFluidPhaseAppearanceAdvectionTest` checks shared donor budgets, old upwind composition, signed trace transfers, conservative relay through initially empty cells, outlet accounting and transactional rejection. `TwoFluidPhaseAppearancePipelineTest` advances a synthetic SRK 5 km/12-cell rich-gas depressurization through first oil appearance at 17.75 s with 0.25 s steps and 30 s transfer relaxation, preserving phase/component/source closure with exactly zero initial liquid. The public level-outlet conservative replay advances from 35.25 s to 94.25 s after the empty-phase repair, then rejects a material outlet-cell n-heptane deficit. Full blowdown thermal recovery, pooling and restart remain unqualified. |
| Positive trace-phase component inventory at synchronization | Yes | Yes | No | `TwoFluidComponentTraceInventoryTest` checks repeated closed substeps at 1e-15 to 2e-10 kg and nonzero condensation sources at 1e-15 to 1e-12 kg/(m s). Every positive hydrodynamic phase requires named components; a zero-component candidate rejects transactionally even below the audit tolerance. The synchronization allowance must not erase transported components. |
| Coupled closed-face momentum response | Yes | Short-time analytical limit | No | `TwoFluidClosedBoundaryMomentumTest` checks Euler and RK4: a finite pressure impulse over 1e-8 s retains adjacent physical-cell inertia, while both integrated external mass fluxes are exactly zero and total mass closes. Legacy uncoupled boundary handling is unchanged. |
| Component-resolved downstream outlet and rejected component-substep isolation | Yes | Yes | No | The published component flows equal accepted outlet transfers divided by interval duration, including after TP reflashing. Unequal gas/oil/water transport, delayed composition fronts, reordered names, zero flow and failure isolation are tested. General unsplit component advection remains open. |
| Complete legacy transient transaction | Opt-in | Bounded regression coverage | No | Failed intervals preserve accepted hydro/component/thermal/slug state, reports, histories and clocks. Connected streams, upstream storage, thermal calculators and layers retain identity. Other owned submodels are new snapshots after acceptance. Concrete SRK/PR/SRK-CPA/SRK-CPAs and ideal-gas phases are supported; unaudited EOSs and pipe subclasses reject. |
| Legacy IMEX compression/expansion response | Yes | Linear-acoustic restoring-force and mass checks | No | The scalar pressure correction opposes compression/expansion instead of amplifying it. Phase mass remains explicit and the scalar total-mass residual does not establish multiphase volume closure or unrestricted acoustic timesteps. |
| Countercurrent liquid-wall work and absent-phase mechanical terms | Yes | Force/power, conservative gravity, trace and boundary regressions | No | Unresolved wall force is partitioned by phase inertia, gravity uses each conservative phase mass, and exactly absent phases cannot create boundary NaNs or false backflow warnings. These checks do not qualify resolved phase wetting or arbitrary terrain well balancing. |
| Coupled fluid/multi-layer thermal exchange | Yes | Passive bounds, analytical refinement and energy ledgers | No | Backward Euler advances fluid and radial capacities together with consistent endpoint ambient flux. Adiabatic energy-enabled cases retain temperature advection, and both thermal routes retain elevation work. Remaining explicit thermal sources still require time/mesh sensitivity. |
| Frozen-phase unsplit execution through runTransient | Opt-in | Bounded regression coverage | No | Always stages complete intervals and verifies accepted phase ledgers. Original SRK/PR phase compositions and density branches persist across calls. Spatially nonuniform/changing phase composition, reverse phase outlet flow, energy/phase transfer and tracked slugs remain unsupported. Preparation-only diagnostics have a broader signed-flow scope. |
| Five-second gas execution and uniform three-phase fixed point | Opt-in | Selected configurations | No | Four-cell gas runs at 0.1 s and eight/sixteen-cell runs at 0.05 s pass consecutive accepted calls with unchanged nonlinear/interval tolerances of 1e-10/1e-8. Consistent outlet phase fractions repair the single-phase Jacobian kink; the coarse case now runs in ordinary CI. These are continuation/conservation checks, not spatial-accuracy qualification. A separate closed uniform three-phase fixed point passes. |
| Inclined annular film eligibility, transition blending and trace-phase Jacobian | Opt-in criteria; derivative correction enabled | Local branch and analytic derivatives | No | The captured 0.594-liquid-holdup conflict converges with the film constraint. A separate opt-in annular/slug force blend removes the gas-lift point switch and advances the historical backward-Euler 16-cell gates from 0.663/0.688 s to 1.451/1.671 s; the 24-cell gate reaches 0.95 s. All still fail before five seconds. Phase-relative probes retain trace drag/volume derivatives. Neither local criterion implements full film/droplet/reversal physics. |
| Directional phase-face complementarity | Experimental opt-in augmented solve | Unit, coupled adapter, exact-absent donor, conservation and 53-color layout coverage; six unchanged 0.1 s Tengesdal lanes pass | No | The nonlinear vector adds signed-direction mass, advective momentum and donor multiplier variables per internal phase face. Present donors use normalized conservative advection; exactly absent donors use exact donor-presence/zero-momentum rows. Solved transport replaces the free internal flux conservatively and prepared-step verification reuses it. No inventory is clipped and no donor smearing is used. The unchanged 15-case five-second matrix still fails at 0.1251953125–0.831640625 s, so phase-transition, restart and pigging qualification remain open. |
| Current unsplit Tengesdal five-second matrix with film constraint | Harness implemented | **Failed** | No | After the outlet repair, corrected-face 16/0.1, 16/0.05 and 24/0.05 cases stop near 1.303, 1.881 and 1.736 s. All six historical/face cases reject their prefixes and are unchanged by the inclined transition blend, showing that their blockers occur earlier. The transition-blended backward-Euler historical lane advances materially but all three cases still fail line search. Later closure/domain transitions, trial velocity guards and unsplit 180/600 s qualification remain open. |
| Explicit finite-volume face terrain | Opt-in | Yes | No | N+1 elevations, actual cell arc lengths and midpoint pressure with external-face offsets. Signed gravity/energy and constant-density hydrostatics are checked under nonuniform refinement. This is not general transient well-balancing. |
| Conservative slug/film + named components + phase transfer + thermal balance | Yes | Yes | No | A closed four-cell wet-gas cooling case over 0.05 s closes all ledgers on 0.05/0.025/0.0125 s outer partitions. Adjacent-grid water/heat sensitivity is below 4%; marker-displacement sensitivity decreases from 3.14% to 1.99%. Marker length/age are partition-invariant, and transactions reproduce the same-grid result. See the model guide for numerical values. This is coupled-ledger evidence, not spontaneous slug initiation. |
| Non-equilibrium post-pigging MEG/water transfer | Experimental opt-in | Short-time component/coupling tests | No | Separate initial aqueous inventory, independent signed species fluxes, Euler/isothermal restriction. Seeded low-point motion is tested; long-horizon drying, pool formation and experimental accuracy remain unqualified. See [film transfer](TwoFluidPipeFilmDrying.md). |
| Dry gas/oil component-target relaxation in conservative Euler | Experimental approximation | Frozen-inventory exponential/refinement and donor-momentum controls; complete source-only oil disappearance into single-gas targets at 50 bara/480–520 K; original 12/24-cell pressure-ramp regression | No | Each source uses its conserved component inventory difference from the local equilibrium target. Counter-transfers carry actual donor momentum with one total-energy owner. The hot disappearance test qualifies the frozen algebraic endpoint, not seabed heating or evaporation time. Aqueous grids retain the previous closure. The historical flat-outlet 12-cell/0.125 s case still rejects an inadmissible liquid velocity at 35.625 s; full pooling, inlet refill, heat/area/transport-rate and experimental qualification remain open. |
| Reverse outlet inflow with named-component transport and no external composition | **Unsupported** | Fail-closed | No | Configuration is rejected in either setter order. The last interior composition is not a physical external boundary condition. |
| Local gas/oil film simultaneous mass/heat solve | Separate default-off boundary option; not integrated into transient source | Binary/six-component zero-driving-force and nearby equation/refinement controls | No | `setCoupledMassHeatTransferEnabled(true)` uses the existing film/EOS equations with full residual acceptance. Requires positive finite transport and strictly positive nonreactive hydrocarbon interface composition. Stationary liquid with zero correlation coefficient rejects; no unit-conductance fallback. See `CoupledGasOilFilmTest` and the [film-method limits](../fluidmechanics/InterphaseHeatMassTransfer#residual-based-gasoil-option). This does not qualify stagnant-pool diffusion, interfacial area/heat supply, phase disappearance or predictive depletion time under #4675. |
| Reverse pressure-outlet inflow with an explicit external thermodynamic donor | Experimental opt-in | Signed component/mass/enthalpy unit tests and synthetic rich-gas pressure-ramp regression | No | `setOutletBoundaryFluid(...)` owns reversed phase composition, density and enthalpy; `setAllowOutletPhaseBackflow(true)` enables signed transport. The net conservation ledger stays signed while downstream publication contains only positive outbound material. The 40 s depressurization plus pressure reversal closes mass, named-component and full-energy reports. This is boundary-contract evidence, not complete blowdown, pooling, refill or predictive transfer-rate qualification. |
| Multi-stage conservative slug + component + phase-transfer coupling | **Unsupported** | Fail-closed | No | Phase appearance inside an intermediate stage needs stage-local component inventories. The coupled four-way path is currently restricted to single-stage Euler. |

The Mohmmed source provenance, exact spreadsheet cells, point selection, missing-data policy, and
error definitions are maintained in
[TwoFluidPipe Reporting and Validation](../wiki/two_fluid_reporting_and_validation). Those fixed
engineering gates are not claimed as measurement confidence intervals; the paper does not publish
pointwise uncertainty intervals for the selected observations.

## Supported-use envelope

Use **TwoFluidPipe** only when the case fits a row above and its runtime evidence is retained:

1. Run steady initialization and inspect the complete immutable convergence report. A stationary
   pressure profile is insufficient if liquid split, holdup, thermodynamics, or total pressure drop
   remains outside tolerance, or if total phase mass flux does not close against the inlet.
2. For transient work, require requested elapsed time, phase and total mass balances, positivity,
   and all sticky pressure/momentum, pressure-limiter, rejected-substep, outlet-backflow, component,
   and energy diagnostics applicable to the selected configuration.
3. Repeat mesh and time-step sensitivity at the actual length, diameter, inclination, composition,
   phase split, thermal boundary, rates, and event duration. Evidence from one fixture does not
   define a universal operating envelope.
4. Treat seeded slug markers as transport/coupling probes. They do not demonstrate spontaneous
   slug initiation, sustained cycles, arrival statistics, or load distributions.
5. Use **PipeBeggsAndBrills** for a correlation-based steady or quasi-steady screen, not for
   conservative distributed line-pack dynamics. Use a separately qualified transient model or a
   controlled experimental study when the requested TwoFluidPipe row is failed or unsupported.

## High-throughput steady mass conservation (#3686)

`TwoFluidPipeSteadyMassFluxTest` reproduces the 3,100 m well with a 0.23 m diameter, 2,380 m rise,
20 sections, 205 bara and 90 degrees Celsius inlet, and the issue's synthetic SRK fluid and
stock-tank rates. The original 166.113217788 kg/s case lost 3.473846596 kg/s at the outlet section
because gas velocity was capped at 100 m/s. At 5% higher flow the deficit was 7.007383459 kg/s;
the 5% lower-flow case did not reach the cap.

The regression checks all three rates against phase-summed section mass flux with relative
tolerance 1e-10 and absolute tolerance 1e-12 kg/s. It also requires the original hydraulic and
thermodynamic residuals to pass. Separate gas, oil and water fixtures exercise the former steady
gas/liquid caps, and injected 2% and nonfinite reporting errors must prevent convergence even
when the hydraulic residuals have settled. The existing 30/60-cell three-phase fixture remains
the free-water and mesh-refinement check. This is conservation and numerical-convergence evidence,
not experimental or critical-flow qualification.

## Executable steady and transient example

The executable **TwoFluidPipeEvidenceExampleTest** in
src/test/java/neqsim/process/equipment/pipeline runs a 5 km,
0.30 m liquid-rich gas-condensate line at 50 kg/s, 60 bara and 50 degrees Celsius. It enables the
opt-in shared slug force balance, interfacial pressure, and coupled pressure/momentum before steady
initialization. The same object then advances ten one-second outer steps with unchanged boundaries.

The example fails unless:

- the complete steady report is converged without pressure-floor or wall-clock termination;
- the transient accepts exactly 10 s;
- maximum relative total-mass residual is at most 1e-10;
- inventory and outlet-pressure drift are each at most 2%;
- no outlet clamp, nonlinear failure, pressure-correction limiter, or rejected substep occurs.

Execute it from the repository root:

~~~bash
./mvnw -q -Dtest=TwoFluidPipeEvidenceExampleTest test
~~~

The exact Stage 5 execution values and source/head commit are recorded in the Stage 5 pull request
and the issue #3298 series ledger. These values are reproducibility evidence for the example, not
experimental qualification.

The prepared local execution on master
`5bb230e1c2e2227074ce4d20ec19d6be91b53ec7` produced:

| Quantity | Result |
|---|---:|
| Steady termination / iterations / tolerance | CONVERGED / 16 / 1e-4 |
| Steady outlet pressure | 57.62005697 bara |
| Initial mean liquid holdup | 0.269435835 |
| Initial total inventory | 72,424.765816 kg |
| Accepted transient duration | 10.0 s |
| Maximum total-mass residual | 1.92344e-11 kg |
| Maximum relative total-mass residual | 2.65574e-16 |
| Inventory drift | 0.00977583% |
| Outlet-pressure drift | 0.0% |
| Final mean liquid holdup | 0.269473910 |

The Stage 5 pull request and issue ledger bind these values to the final evidence-only head.

## Decision record

The five-stage evidence chain is recorded in issue
[#3298](https://github.com/equinor/neqsim/issues/3298):

- #3514: steady-to-transient consistency and the opt-in liquid-rich drift result;
- #3541: public Mohmmed benchmark reporting and failed qualification;
- #3543: honest three-phase steady convergence residuals and compact refinement evidence;
- #3547: merged coupled slug/component/phase/thermal ledgers and fail-closed unsupported combinations;
- the Stage 5 PR: this evidence matrix, executable example, and agent/skill routing.

Open or draft work is not a released capability. Confirm the target release contains the referenced
implementation before using its row.

## Static subcell geometry diagnostic

`GeometryCalculator.calculateHydrostaticCell` and the read-only section accessor
are numerically verified static geometry references. `HydrostaticCellGeometryTest`
checks independent volume/area quadrature, exact empty/full cells, horizontal and
signed-slope limits, partition consistency, serialization and unchanged state.
They are not a flowing-interface closure, stable-gravity-pool validation or a
physical hydrocarbon evaporation-rate model. The existing cell-average closure
geometry and transient solver remain the numerical owners. See the
[reporting contract](../wiki/two_fluid_reporting_and_validation#static-subcell-depth-and-area-diagnostic).


### Closed-wall mechanical pressure compatibility

The explicitly impermeable external face reconstructs its pressure from the
adjacent conservative mixture inertia and half-cell gravity head. Mass and
enthalpy transport remain zero. Horizontal closed-wall pressure is unchanged;
nonphysical reconstructed pressure fails explicitly without a numerical floor.
`ClosedWallPressureCompatibilityTest` covers affine pure-phase hydrostatics,
horizontal acoustic pressure response and closed-flux limits. These are numerical
boundary controls, not qualification of a static two-phase pool or physical
hydrocarbon transfer rates.

The [closed-wall reproduction record](../../src/test/resources/neqsim/process/equipment/pipeline/closed-wall-pressure/README.md) includes exact source provenance, analytical acceptance, all 12–192-cell operator data, and measured RHS cost with shared-runner limitations.

### Interior hydrostatic pressure and signed work

Every internal face now reconstructs a common mechanical pressure from the two
adjacent conservative mixture inertias and signed half-cell gravity heads. The
same additive correction is applied to phase momentum traction, phase-pressure
diagnostics and total-energy pressure work. Energy uses the signed mass donor, so
the correction is exactly `Q * deltaP` for either flow direction. A horizontal
face retains the established AUSM pressure flux exactly, and rejected
transactional evaluations restore the accepted correction ledger.

`HydrostaticPressureReconstructionTest` passes 90 analytical stationary controls:
flat, affine and public curved terrain; constant, smooth and alternating cell
density; 12/24/48/96/192 cells; and pure gas or oil. The unchanged maximum
acceleration gate is `1e-10 m/s2`; every local mass and energy rate is exactly
zero. A separate 45-case pure-water extension passes the same terrain, density,
mesh and conservation gates without changing that predeclared basis. Signed
-2/+2 m/s controls verify pressure work, horizontal correction is
exactly zero, and rollback is exact. This is numerical well-balancing of a common
mixture pressure, not validation of a stationary two-density pool, a moving
interface, severe slugging, restart, phase-transfer rates, pigging or commercial
parity. See the [public analytical record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-interior-pressure/README.md).

`MultiphaseHydrostaticForceQualificationTest` adds 720 unequal-density controls
and 48 signed gas/oil/water work controls. At initially zero velocity, a common
mixture-hydrostatic pressure gives phase acceleration
`(rhoMix / rhoPhase - 1) * g * sin(theta)` while the summed mixture force is zero.
The fixed gates are `1e-10 m/s2` phase-acceleration error and `1e-8 N/m` mixture-force
residual; absent-phase mass and momentum rates and stationary energy rates are
exactly zero. The work controls retain the individual donor under countercurrent
flow. These are instantaneous force/flux identities, with refinement through 192
cells; no elapsed pool-retention interval, pool level, or depletion time is inferred.
The public record retains every case, inspected plots, and RHS-only timing data.


### Stratified hydrostatic pressure moments

The result's derived centre-referenced phase first area moments and normal-gravity energy [J/m]
are qualified by `StratifiedGravityEnergyTest`: closed circular-segment formula, 162 retained
80-digit references including positive phase traces down to 1e-30, equal-density/zero-gravity
limits, exact absence and invalid inputs, query purity and the unchanged four-field serialized
contract. No dynamic force, energy-recovery or phase-transfer call site is added. The
[executed energy budget](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/gravity-energy/README.md)
distinguishes a static potential-energy change from an elapsed drainage or evaporation result.

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
retains every case, refinement and measured opt-in query cost.

The default-off production switch
`TwoFluidPipe.setStratifiedHydrostaticPressureMomentsEnabled(true)` couples
$F_k=\rho_k g\cos(\theta)J_k$ into phase pressure traction at internal and external faces and adds
the conservative work $Q_kF_k/A_k$ with the mass-flux donor density. The retained force matrix is
ordered inlet/internal/outlet by gas/oil/water and is transaction-safe. The nine-test coupling matrix
covers both flow directions, exact phase endpoints, a vertical control, closed-domain force
telescoping, unsupported-interface and diameter rejection, rollback, public API state and bitwise
default-off restoration, trace forces and countercurrent unequal-density signed donors. Its paired-fraction
geometry adapter inverts the minority area without a depth floor or absolute area cutoff, including
gas traces whose complementary liquid fraction rounds to one. Recovered nonnegative volume
weights must satisfy the unchanged 1e-10 sum bound; only geometric fractions use weights divided
by their sum, with no mutation of the conserved state. An independent 80-digit series
inversion supplies 162 retained controls over six diameters and positive fractions down to `1e-30`.
The [executed coupling record](../../src/test/resources/neqsim/process/equipment/pipeline/hydrostatic-pressure-moments/coupling/README.md)
states the analytical references, units and commands.

The enabled historical rich-gas 120 s ramp **fails**: last accepted time is 0.25 s on both meshes;
the requested 12-cell 0.5 s and 24-cell 0.375 s intervals reject the existing face-volume closure
guard. Primary conservative state and elapsed time restore exactly. The default-off master ramp
passes, so that result cannot qualify enabled pressure moments. The retained executable probe,
accepted-interval data and rejection statuses make this dependency explicit; no closure threshold
was changed to produce a transient pass.

This qualifies instantaneous phase-traction/work identities only. The one-interface circular geometry
rejects simultaneous positive oil and water and diameter steps. No elapsed retained-pool, terrain
drainage, cooldown, restart, severe-slugging, pigging or predictive evaporation claim follows.
Capillarity, waves, entrainment and separate oil/water interfaces remain outside this API.
