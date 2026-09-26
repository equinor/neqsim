---
title: Model-explicit release source terms
description: Explicit homogeneous, drift-flux and ideal/real-gas pipe release models with immutable thermodynamic stations and fail-closed diagnostics.
---

# Model-explicit release source terms

Use the [neutral source-term contract](source-term-contract) to export these results as
versioned JSON, newline-delimited frames or a reduced CSV time series.
Use [uncertainty ensembles](source-term-uncertainty) to propagate joint input cases without
discarding failures. The [implementation status](source-term-platform-status) records remaining work.

`ReleaseFlowModel` calculates an instantaneous short-opening source term from a NeqSim
fluid. It is independent of downstream safety software and process orchestration. Requests
defensively copy the fluid; results and station snapshots are immutable and serializable.
This is the first implementation layer of [NRC-3860](https://github.com/equinor/neqsim/pull/3861).

## Usage

The following example is executed by `ReleaseFlowModelTest.documentationExampleAndEnergyClosure`:

```java
SystemInterface gas = new SystemSrkEos(300.0, 50.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
ReleaseFlowModel model = new HomogeneousEquilibriumReleaseModel();
ReleaseFlowResult result = LeakModel.builder().fluid(gas).holeDiameter(0.01)
    .dischargeCoefficient(0.62).backPressure(101325.0).build()
    .calculateReleaseFlow(gas, model);
```

Import the types from `neqsim.process.safety.release` and `neqsim.thermo.system`.
Check `result.isUsable()` and inspect `getDiagnostics()` before accessing the release rate.
The rate getter throws on invalid or unsupported results; failure cannot look like zero flow.
For direct use, construct `ReleaseFlowRequest(fluid, diameterM, dischargeCoefficient,
backPressurePa)` and call the model's `calculate` method. Malformed request geometry throws
at construction; thermodynamic/model failures produce diagnostic results.

## Explicit model selection

Model selection is never inferred from phase count. Choose one implementation and retain its
identity, version and diagnostics with every frame:

| Model | Intended use | Required behavior |
|---|---|---|
| `HomogeneousEquilibriumReleaseModel` | Short-opening equilibrium gas, liquid and flashing calculations. | EOS/flash closure is strict; unsupported phase physics and failed required properties return no numeric payload. |
| `SlipCorrectedHomogeneousEquilibriumReleaseModel` | Short-opening gas/liquid flow with equilibrium thermodynamics and caller-declared velocity slip. | Requires exactly one gas and one liquid phase at the opening, exposes phase densities and velocities, and closes phase area and kinetic energy. It does not infer a slip or entrainment correlation. |
| `DriftFluxHomogeneousEquilibriumReleaseModel` | Vertical-upward, short-opening gas/liquid screening with equilibrium thermodynamics and predictive slip. | Solves Zuber-Findlay/Harmathy drift flux together with phase-area and kinetic-energy closure. Exactly one gas and one liquid phase, caller-declared positive interfacial tension and gas area fraction at most 0.80 are required. |
| `IdealGasReleaseModel` | Analytical gas checks and dilute-gas screening with constant $\gamma$. | Requires one gas phase plus finite NeqSim molar mass and $\gamma$; no property default or model fallback. |
| `IdealGasFannoPipeReleaseModel` | Quasi-steady one-sided full-bore gas release through a constant-area pipe. | Requires explicit pipe length and Darcy friction, one gas phase, and finite ideal-gas properties; no friction or phase fallback. |
| `RealGasFannoPipeReleaseModel` | Quasi-steady one-sided single-gas flow through a constant-area pipe using the selected EOS. | Requires explicit pipe length and Darcy friction and one equilibrium gas phase throughout; phase appearance, sonic-step failure and unresolved solid risk fail closed. |
| `LegacyScreeningReleaseModel` | Reproduce the historical `LeakModel.calculateMassFlowRate` rate during migration. | Always returns `VALID_WITH_WARNINGS` when usable and carries `SCREENING_ONLY`, unresolved-station and legacy-fallback diagnostics. |

Every implementation also returns an immutable `ReleaseModelEvidence` manifest. Stable
applicability and limitation codes state the modeled boundary, while evidence records distinguish
analytical, conservation, numerical and experimental comparisons. A custom model that does not
override `getEvidence()` fails closed to `NO_DECLARED_VALIDATION_EVIDENCE`. The current built-in
records are primarily repository-owned analytical and regression evidence. The ideal-gas Fanno
manifest additionally references the independently published NASA GFSSP nitrogen case described
below. All emitted frames remain `UNQUALIFIED`; an independent record does not self-promote a
model's evidence level.

For example:

```java
ReleaseFlowModel idealGas = new IdealGasReleaseModel();
ReleaseFlowModel legacyCompatibility = new LegacyScreeningReleaseModel();
ReleaseFlowResult analytical = idealGas.calculate(
    new ReleaseFlowRequest(gas, 0.01, 0.62, 101325.0));
```

The legacy adapter delegates the numeric rate to the historical scalar method, including its
documented density, molar-mass and heat-capacity-ratio fallback policy. It does not relabel that
behavior as strict physics. Because the legacy equation never solves throat or exit thermodynamics,
its opening snapshots alias the initialized upstream pressure and temperature. Consumers can detect
this limitation from `UNRESOLVED_STATIONS`; they must not interpret those snapshots as a nozzle
solution.

## Prescribed gas/liquid slip

`SlipCorrectedHomogeneousEquilibriumReleaseModel` separates thermodynamic equilibrium from
hydrodynamic equilibrium. The HEM calculation first resolves the isentropic opening state and
homogeneous velocity $u_h$. The caller supplies the gas-to-liquid velocity ratio
$S=u_g/u_l\ge 1$. With gas and liquid mass fractions $y_g$ and $y_l$, conservation of the HEM
specific kinetic energy gives

$$u_l=\frac{u_h}{\sqrt{y_gS^2+y_l}},\qquad u_g=Su_l.$$

The total mass flux $G$ then follows from phase-area closure using the EOS phase densities
$\rho_g$ and $\rho_l$:

$$G=\left(\frac{y_g}{\rho_gu_g}+\frac{y_l}{\rho_lu_l}\right)^{-1},\qquad \alpha_g+\alpha_l=1.$$

The station's scalar velocity is $G/\rho_{mix}$ so its existing `massFlux` field remains the total
mass flux. The optional `phaseDensities` and `phaseVelocities` maps retain the phase-resolved SI
basis. At $S=1$ the calculation reproduces HEM. Tests enforce that limit, phase-area closure,
phase kinetic-energy closure, immutable inputs, deterministic schema output, and steady/dynamic
frames from both process-container types.

This is a prescribed-slip sensitivity model, not a general non-equilibrium qualification. It
does not predict slip, entrainment, droplet size, finite-rate vaporization/condensation, wall
friction, heat transfer, solids, or pipe decompression. Select $S$ from an independently justified
engineering basis and retain it with scenario provenance. Results and evidence remain
`UNQUALIFIED` pending experimental validation and accountable domain review.

## Predictive vertical drift flux

`DriftFluxHomogeneousEquilibriumReleaseModel` removes the caller-declared slip ratio for a bounded
vertical-upward bubbly/dispersed screening case. It retains the HEM equilibrium thermodynamic
station and specific kinetic energy, then solves the Zuber-Findlay relation

$$u_g=C_0j+V_{gj},\qquad C_0=1.2,$$

where $j=\alpha_gu_g+\alpha_lu_l$ is total volumetric flux [m/s]. The bubble drift velocity uses
the Harmathy relation

$$V_{gj}=1.53\left[\frac{g\sigma(\rho_l-\rho_g)}{\rho_l^2}\right]^{1/4}.$$

Here $g$ is gravitational acceleration [m/s²], $\sigma$ is the caller-declared gas/liquid
interfacial tension [N/m], and $\rho_g$ and $\rho_l$ are native-phase densities [kg/m³]. The solver
couples this relation to the same phase-area and kinetic-energy equations documented for prescribed
slip. It returns explicit phase velocities and densities and checks the drift residual, phase-area
sum and specific kinetic-energy error.

```java
ReleaseFlowModel model = new DriftFluxHomogeneousEquilibriumReleaseModel(0.020); // N/m
```

The closure requires exactly one gas and one oil, generic-liquid or aqueous phase. The caller must
retain the source and applicability of the mixture-specific interfacial tension with scenario
provenance; missing, non-finite or non-positive values are rejected at construction. A predicted
gas area fraction above 0.80 is `UNSUPPORTED` rather than being silently extrapolated toward annular
flow. The opening is assumed vertical and upward because the current request contract carries no
orientation field.

The model basis is Zuber and Findlay, *Journal of Heat Transfer* 87 (1965), 453–468,
[doi:10.1115/1.3689137](https://doi.org/10.1115/1.3689137), and Harmathy, *AIChE Journal* 6
(1960), 281–288, [doi:10.1002/aic.690060222](https://doi.org/10.1002/aic.690060222). These
correlations are model provenance, not independent rate validation. The implementation excludes
finite-rate phase transfer, entrainment and droplet-size transport, annular/high-Weber jets, pipe
friction, heat transfer and solid-bearing flow. It remains `UNQUALIFIED` pending experimental
multiphase evidence and accountable domain review.

## Ideal-gas equations

The ideal-gas adapter uses the initialized mixture molar mass $M$ [kg/mol] and heat-capacity ratio
$\gamma$ [1] without replacement values. With $R=8.31446261815324$ J/(mol K), the critical pressure
ratio, isentropic station temperature, density and velocity are:

$$r_c=\left(\frac{2}{\gamma+1}\right)^{\gamma/(\gamma-1)},\quad \frac{T}{T_0}=\left(\frac{p}{p_0}\right)^{(\gamma-1)/\gamma}.$$

$$\rho=\frac{pM}{RT},\quad c_p=\frac{\gamma R}{(\gamma-1)M},\quad u=\sqrt{2c_p(T_0-T)},\quad \dot m=C_dA\rho u.$$

The accepted throat pressure is $p_0r_c$ when $p_b/p_0\le r_c$ and $p_b$ otherwise. For a choked
state, the analytical velocity equals $\sqrt{\gamma RT/M}$. Station enthalpy uses the upstream
NeqSim reference minus $u^2/2$ and station entropy retains the upstream reference. This preserves
relative energy/entropy consistency without presenting an arbitrary absolute ideal-gas reference
as new property data. The adapter supports one gas phase only and excludes reaction, forced phases,
solid/hydrate checks, real-gas departure, phase change, friction, heat transfer and depletion.

## Independent Fanno benchmark

`IdealGasFannoPipeReleaseModel` version 1.0.1 reproduces Case 1 from NASA NTRS
[20070036728](https://ntrs.nasa.gov/citations/20070036728). The external case specifies nitrogen at
50 psia, 80 °F and inlet Mach 0.5 in a 6-inch-diameter, 3207-inch-long adiabatic pipe with Darcy
friction factor 0.002; the published analytical boundary is Mach 1 at the exit.

The repository retains the converted SI inputs in
`src/test/resources/neqsim/process/safety/release/nasa-gfssp-fanno-2007.csv`. A test independently
converts the published inlet state to the reservoir stagnation boundary required by the API,
requires a choked exit, checks the exit Mach number, and limits inlet mass-flux error to 1%. It
writes the expected value, calculated value and relative error to
`target/source-term-benchmarks/nasa-gfssp-fanno-2007-receipt.csv`.

This is independently sourced analytical evidence for one calorically perfect single-gas case. It
is not experimental qualification and does not validate real-gas departure, transient
decompression waves, heat transfer, multiphase slip, phase change or solid-bearing transport.

## Ideal-gas Fanno pipe equations

`IdealGasFannoPipeReleaseModel` uses a separate finite-pipe request:

```java
ReleaseFlowModel pipeModel = new IdealGasFannoPipeReleaseModel();
ReleaseFlowRequest pipe = new ReleaseFlowRequest(
    gas, 0.10, 1.0, 101325.0, 100.0, 0.02);
ReleaseFlowResult pipeResult = pipeModel.calculate(pipe);
```

The final two values are pipe length [m] and specified Darcy friction factor [1]. Both
must be positive together. Existing short-opening models reject this geometry instead of
silently discarding it. `SourceTermSession.addLongPipeSource` carries the same geometry through
steady and dynamic `ProcessSystem` and `ProcessModel` paths. The corresponding
`ReleaseInventory` constructor preserves it while pressure, composition and energy evolve.

For exit Mach number $M_e$, inlet Mach number $M_i$, pipe length $L$, internal diameter $D$
and Darcy factor $f_D$, the model solves:

$$\frac{f_DL}{D}=F(M_i)-F(M_e),$$

$$F(M)=\frac{1-M^2}{\gamma M^2}+\frac{\gamma+1}{2\gamma}
\ln\left(\frac{(\gamma+1)M^2}{2+(\gamma-1)M^2}\right).$$

The stagnation-pressure relation is:

$$\frac{p_0}{p_0^*}=\frac{1}{M}
\left(\frac{2+(\gamma-1)M^2}{\gamma+1}\right)^{(\gamma+1)/(2(\gamma-1))}.$$

The pipe is choked when the receiving pressure is at or below the calculated static exit
pressure for $M_e=1$. Otherwise a bounded solve finds $0<M_e<1$ whose exit pressure equals the
receiving pressure. Mass flux is $G=\rho Ma$ and the requested effective full-bore area remains
$C_d\pi D^2/4$. Tests check Mach and energy closure, constant-area mass conservation,
friction-length and backpressure trends, no-flow behavior, process-container frames and coupled
inventory conservation.

This is a steady, adiabatic, calorically perfect, constant-area, one-sided pipe model. The
specified friction factor is not calculated from roughness or Reynolds number. The model does
not represent the opposite side of a rupture, transient decompression waves, line packing,
heat transfer, real-gas departure, multiphase flow, slip, delayed flashing, entrainment,
finite-rate phase transfer or solid-bearing transport. It is software-validated against its
analytical equations, not independently qualified for facility decisions.

## Real-gas Fanno pipe equations

`RealGasFannoPipeReleaseModel` uses the same finite-pipe request and process/inventory integration
as the ideal model, but obtains density, enthalpy, entropy and acoustic speed from the selected
NeqSim EOS at each numerical station. It preserves constant mass flux $G$ and stagnation enthalpy
$h_0$:

$$G=\rho u,\qquad h+\frac{u^2}{2}=h_0.$$

For specified Darcy factor $f_D$, diameter $D$ and axial coordinate $x$, the steady momentum
balance is:

$$\frac{dp}{dx}=-\frac{f_DG^2}{2D\rho\left(1+G\left.\frac{du}{dp}\right|_{h_0,G}\right)}.$$

The solver finds the subsonic isentropic inlet state connected to the reservoir, marches this
equation with EOS enthalpy flashes, and brackets the largest solution whose exit Mach number
remains below one. A receiving pressure above the critical exit pressure triggers a second
bounded mass-flux solve so the physical exit pressure matches the receiver. A lower receiving
pressure retains the critical pipe state and reports a separately resolved isentropic
ambient-expanded station. The requested effective area remains $C_d\pi D^2/4$.

This is a quasi-steady model, not a transient pipe solver. It represents neither pressure-wave
propagation nor line packing, finite pipe inventory, the second side of a rupture, heat transfer,
roughness/Reynolds friction correlations, non-equilibrium phase transfer, slip or solid-bearing
flow. Every EOS marching and ambient state must remain one equilibrium gas phase. Phase
appearance is not replaced with ideal-gas or homogeneous-equilibrium physics. The result remains
`UNQUALIFIED`; dense-gas regression and dilute analytical agreement are software validation, not
independent experimental qualification.

## EOS-backed transient pipe decompression

`RealGasPipeDecompression` is the state-owning transient counterpart to the quasi-steady model.
It stores cell-centred conservative density, momentum and total-energy values for a rigid,
constant-area pipe. A local Lax-Friedrichs finite-volume update advances

$$\frac{\partial}{\partial t}\begin{bmatrix}\rho\\ \rho u\\ E\end{bmatrix}
+\frac{\partial}{\partial x}\begin{bmatrix}\rho u\\ \rho u^2+p\\ u(E+p)\end{bmatrix}
=\begin{bmatrix}0\\ -f_D\rho u|u|/(2D)\\ 0\end{bmatrix}.$$

For every candidate cell, a NeqSim volume/internal-energy flash recovers pressure, temperature,
phase state and acoustic speed from the selected EOS. The CFL limit therefore follows the local
EOS signal speed rather than a constant heat-capacity ratio. The closed upstream boundary reflects
momentum. The downstream ghost state uses the declared constant receiver pressure and the local
exit temperature; the declared discharge coefficient multiplies the complete conservative
boundary flux. The same mass and total-enthalpy flux is accumulated in pipe accounting and
exported through `CoupledReleaseSource`, so `ProcessSystem` and `ProcessModel` frames do not
perform a second hypothetical withdrawal.

Construct the unit with the initial gas, pipe length and diameter, effective area coefficient,
receiver pressure, specified Darcy friction factor, cell count and CFL number. It must run in
dynamic mode. The input state must be one nonreacting equilibrium gas phase, and every VU-flashed
cell plus the receiver ghost state must remain one gas phase. Phase appearance, unresolved
properties, density closure failures, and detected or unresolved mixture-specific solid/hydrate
risk abort the whole caller step without committing partial state.

Tests retain exact pipe-plus-discharge mass and total-energy closure, fixed composition, atomic
isolation, both process-container paths, a dilute-gas comparison with the perfect-gas solver, and
an 8/12/16-cell refinement receipt. This is software evidence only. The model excludes heat
transfer, wall elasticity, a finite upstream vessel, two-sided rupture, finite-rate phase transfer,
multiphase slip/entrainment and solid-bearing transport, and it has no independent experimental
qualification.

## Homogeneous-equilibrium equations and station semantics

The upstream TP flash establishes stagnation enthalpy $h_0$ [J/kg] and entropy $s_0$
[J/(kg K)]. Pure-fluid pressure samples use PS flashes to retain the saturation-quality
degree of freedom. Mixtures use cold TP flashes in a bracketed temperature root at fixed
specific entropy and inventory (target residual `1e-7 J/(kg K)`). Numerical model version
`1.1.0` adds guarded continuation at an incipient vapour/liquid phase when the cold-TP entropy
bracket stalls or its candidate fails fugacity closure. The selected numerical route is
recorded in `ENTROPY_SOLVER`:

$$u(p)=\sqrt{2(h_0-h(p,s_0))},\quad G(p)=\rho(p,s_0)u(p),\quad \dot m=C_d A\max_{p_b\le p\le p_0}G(p).$$

Here $u$ is velocity [m/s], $G$ inviscid mass flux [kg/(m2 s)], $\rho$ EOS total density
[kg/m3], $C_d$ the supplied discharge coefficient, and $A=\pi d^2/4$ physical area [m2].
The coefficient multiplies effective flow area; it does not change the isentropic velocity.
Phase amounts are converted to mass fractions from phase mass, never from molar phase fraction.
EOS mass/volume density is used consistently with EOS enthalpy and entropy.

| Station | Definition |
|---|---|
| `UPSTREAM_STAGNATION` | Equilibrated reservoir state with zero velocity. |
| `THROAT_CRITICAL` | Maximum sampled/refined mass-flux state; receiving-pressure state when unchoked. |
| `ORIFICE_EXIT` | Explicit alias of the throat for the zero-length opening assumption. |
| `AMBIENT_EXPANDED` | Ideal isentropic state at receiving pressure; excludes shocks, entrainment and mixing. |

An interior maximum indicates choking. A 48-interval logarithmic pressure scan brackets
each resolved local maximum, then golden-section refinement reduces bracket width below
`1e-7 * upstreamPressure`. This finite search is not a proof that arbitrarily narrow maxima
cannot exist. Phase-boundary cases require resolution/sensitivity and benchmark evidence.

Every trial checks entropy closure (absolute `1e-5 J/(kg K)`), component conservation
(`1e-8` of total moles), phase composition normalization (`1e-8`), interphase log-fugacity
ratios (`1e-5`, for components above `1e-10` in both phases), and finite physical properties.
Negative enthalpy drops below `-1e-5 J/kg` fail; smaller roundoff is clipped to zero.

At a choked state the equilibrium acoustic API supplies an independent diagnostic. An unavailable
acoustic derivative or a Mach discrepancy greater than 5% produces `VALID_WITH_WARNINGS`,
with the reason retained. Acoustic speed is optional; required flash or closure failures
always invalidate the calculation. No ideal-gas property defaults or replacement model are used.

For upstream pressure at or below back pressure, the result is valid zero forward flow, with
`NO_FORWARD_FLOW`; there is no ambient-expanded station and no reverse-flow calculation.

## Applicability and evidence

| Case | Behavior |
|---|---|
| Nonreacting gas, liquid or equilibrium fluid mixture | Calculates the stated homogeneous-equilibrium model. |
| Flashing hydrocarbon liquid | Supports equilibrium phase redistribution with zero slip. |
| Forced phases, reactions, solid or hydrate checking enabled | `UNSUPPORTED`. |
| Mixture-specific equilibrium solid or hydrate risk at a resolved station | `UNSUPPORTED`; a separately assessed solid-capable model is required. |
| Required solid or hydrate assessment cannot resolve | `INVALID`; absence of risk is never inferred from a failed check. |
| Full-bore calorically perfect gas pipe with specified constant Darcy friction | Select `IdealGasFannoPipeReleaseModel`; outside HEM physics. |
| Full-bore single-equilibrium-gas pipe requiring EOS departure | Select `RealGasFannoPipeReleaseModel`; phase appearance fails closed. |
| Transient decompression waves, line packing, multiphase slip, delayed flashing or heat transfer | Outside the delivered models. |
| Failed flash, unclosed inventory/entropy/fugacity or invalid density | `INVALID`, without a numeric release payload. |

Version `1.2.0` assesses the upstream, accepted throat and ambient-expanded states on defensive
fluid copies. Components present above `1e-12` mole fraction are checked when the station is at
or below their database triple-point temperature. The selected-component solid flash uses the
same EOS and mixture; a stable solid mass fraction above `1e-12` returns `SOLID_RISK`. A
water-containing mixture with a recognized hydrate former is compared with its calculated
hydrate equilibrium temperature and returns `HYDRATE_RISK` at or below the boundary. Failed
required checks return `SOLID_RISK_ASSESSMENT_FAILED` and no numeric release payload. Clear
stations carry `SOLID_RISK_ASSESSED` diagnostics.

These are applicability controls, not solid-bearing release physics or experimental
qualification. Candidate screening is limited by the component data and hydrate model available
to the selected NeqSim thermodynamic system. Turning off solid checks on the caller does not
disable the defensive assessment or establish absence of solids outside the checked stations.

Evidence is software regression and analytical dilute-gas comparison, not independent
experimental qualification. The frame's `model.evidence` object retains the exact manifest ID,
applicability codes, limitation codes and evidence references used by the selected model; it does
not infer a higher evidence level from successful calculation status. Tests cover the ideal-gas choked limit (rate and pressure),
unchoked/no-flow limits, energy/entropy closure, flashing pure propane, a methane/ethane
gas mixture, input
immutability, serialization, invalid/unsupported inputs, and area/coefficient scaling.
No method is certified for a facility or safety decision by these tests.

### Incipient-phase numerical continuation

Cold TP phase selection can discard a trace vapour phase or assign its liquid cubic root near
phase appearance. This creates an entropy discontinuity even when an equilibrium entropy root
exists. Version `1.1.0` retains a vapour/liquid seed observed inside the same temperature bracket
and can refine its phase fractions with the existing `TPmultiflash.solveBeta` equations. Each
candidate uses the same EOS, pressure, components and inventory. It does not switch physical
models, use default properties, force phase types or weaken the final closure limits.

The refinement is bounded by 100 temperature bisections and 20 phase-split updates per
candidate. Both phase fractions must lie strictly between `1e-10` and `1-1e-10`; the active
set must still contain gas and liquid. Accepted candidates must pass composition, component
inventory, fugacity and finite-property checks. Their extensive Gibbs energy cannot exceed
the cold result by more than numerical noise (`1e-9 J/mol` times total moles plus 16 floating-point
units in the reference Gibbs energy). A rejected candidate leaves the cold reference intact;
an unresolved entropy, inventory or equilibrium residual still returns `INVALID` without a rate.

`INCIPIENT_PHASE_CONTINUATION` records resolved entropy roots, accepted/rejected candidate
counts and the last rejection reason. This diagnostic is distinct from acoustic warnings.
The original pressure-search bounds and closure tolerances are unchanged. The source-frame
schema remains v1, while model provenance identifies numerical version `1.2.0`.

The former failing regression (80 mol% propane, 20 mol% n-butane, SRK/classic, 300 K,
20 bara to 3 bara, 10 mm opening and discharge coefficient 0.62) now gives approximately
**1.617324 kg/s**, a critical pressure of **8.231285 bara**, and an ambient gas mass fraction
of **0.205319**. `ReleaseFlowFlashingTest` checks nine composition/temperature neighbours,
inventory scaling, back-pressure sensitivity, station conservation and a schema fixture.
It retains the matrix in `target/source-term-benchmarks/mixture-flashing.csv`.

A separate saturation-path calculation solves bubble pressure while matching saturated-liquid
entropy to upstream entropy. It shares SRK property data but does not reuse the HEM temperature
root or pressure maximizer. For the regression case, rate agreement is within `1e-5` relative
and critical pressure within 2 Pa; generated reference values are retained in
`mixture-saturation-reference.csv`. This is an independent numerical-algorithm check, not
independent experimental validation.

The maximum is at phase appearance and is nonsmooth. The equilibrium acoustic diagnostic
still reports `THROAT_MACH_MISMATCH` (Mach approximately 0.1215), so this example remains
`VALID_WITH_WARNINGS`; frames remain `UNQUALIFIED`. Full mixture-flashing qualification
requires independent release measurements and domain review.

## Transient ideal-gas pipe decompression

`IdealGasPipeDecompression` owns the line-pack state and advances it during native process
transient execution. It is deliberately not a stateless `ReleaseFlowModel`: direct calls through
its model identity fail closed with `TRANSIENT_STATE_REQUIRED`. Register the process unit through
`SourceTermSession.addInventorySource` so the session exports the committed boundary flux rather
than recalculating a hypothetical steady opening.

For conservative state $U=(\rho,\rho u,\rho E)$ and perfect-gas pressure
$p=(\gamma-1)[\rho E-(\rho u)^2/(2\rho)]$, each finite-volume cell advances as

$$U_i^{n+1}=U_i^n-\frac{\Delta t}{\Delta x}\left(F_{i+1/2}-F_{i-1/2}\right)+\Delta t S_i.$$

The local Lax--Friedrichs interface flux is

$$F_{i+1/2}=\frac{F(U_L)+F(U_R)}{2}-\frac{a_{\max}}{2}(U_R-U_L),$$

where $a_{\max}=\max(|u_L|+c_L,|u_R|+c_R)$. The timestep satisfies the configured
Courant bound. The closed-end ghost state reflects velocity; the receiver ghost state retains
the outlet temperature at the declared absolute backpressure. Specified Darcy friction contributes
$-f_D\rho u|u|/(2D)$ to momentum only. Internal interface fluxes cancel exactly, and tests close
remaining plus discharged mass and total energy while checking pressure-wave and discharge
refinement.

This is a first-order, one-sided, calorically perfect single-gas model. It excludes EOS-property
evolution, pipe elasticity, heat transfer, an upstream vessel, two-sided rupture, phase change,
non-equilibrium multiphase flow and solids. Its evidence is numerical/conservative and remains
`UNQUALIFIED`; it is not an experimental dense-gas validation.

## Compatibility and extension

Existing scalar `LeakModel` methods keep their screening rate equations. Its lumped gas
blowdown integration now conserves physical inventory and energy; see the
[gas blowdown compatibility note](release-dispersion-scenarios#conservative-gas-blowdown-and-compatibility)
for the correction of #3905, gas-only applicability, and changed numerical results. `LegacyScreeningReleaseModel` is an opt-in compatibility adapter that proves
rate equality while exposing screening limitations in machine-readable diagnostics. The
`calculateReleaseFlow` method still requires explicit model selection and does not change legacy
selected blowdown orifice law. Use `ReleaseInventory` and process dynamics for model-explicit
time-dependent state evolution.

Additional models implement the serializable `ReleaseFlowModel` interface and return checked
`ReleaseFlowResult.success` or `.failure` objects with stable identity/version and diagnostic
codes. Station snapshots expose overall component mole and mass fractions and native phase
mass fractions in deterministic name order. A new physical model needs its own applicability
documentation and independent validation evidence.
