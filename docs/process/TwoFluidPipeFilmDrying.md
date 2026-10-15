---
title: "Non-equilibrium MEG/water film transfer in TwoFluidPipe"
description: "Experimental isothermal component transfer, independent post-pigging liquid inventory, and terrain-dependent two-fluid motion."
---

# Non-equilibrium film transfer after pigging

`TwoFluidPipe` has an opt-in `NonEquilibriumFilmTransfer` closure for a gas and an aqueous
MEG/water liquid with independently transported compositions. The gas and liquid retain the
existing two-fluid momentum equations, finite gas holdup, pressure response, terrain forces,
and conservative phase and component transport. A liquid inventory can therefore move through
cells and be initialized as a larger pool at a low point; it is not pinned to the pipe wall.

This is an **experimental isothermal screening capability**. It does not qualify field drying
duration, spontaneous pool formation, severe slugging, or long-time disappearance of the film.
The complete acceptance criteria in [issue #4281](https://github.com/equinor/neqsim/issues/4281)
remain open until long-horizon numerical and experimental qualification is available.

## Distinguish the two questions

`PipelineEvaporationStudy` marches a flowing two-phase feed over **distance**. Its phase amounts
represent molar flow rates. Selecting its wall-film geometry does not create a stationary
post-pigging inventory or calculate elapsed drying time. See the
[axial evaporation guide](../fluidmechanics/PipelineLiquidEvaporation.md).

For the transient question, `setInitialAqueousFilm(liquid, holdup)` supplies a separate liquid
composition and initial aqueous volume fraction for each physical cell. The liquid starts at
rest; the initial gas velocity comes from the gas-only steady initialization. This seeded
state is a transient initial condition, not a converged two-phase steady solution. Inlet gas
composition does not replace the initial liquid composition.

A 99/1 wt% MEG/water film can initially **absorb water** from methane specified at a liquid-water
dew point of -18 C at 70 bara, while MEG evaporates. Water content must not be forced to decrease.
Gas dew-point compliance and removal of residual MEG are different completion criteria. The
regression uses the issue's approximately 35.54 ppm inlet water at 5 C and 70 bara; this is a
CPA model reference, not an independent experimental measurement or an ice frost-point value.

## Transfer closure and units

For each configured species, the gas-directed mass flux is

$$j_i=k_i c_g M_i\left(x_{i,l}\frac{\phi_{i,l}}{\phi_{i,g}}-y_{i,g}\right).$$

Here $j_i$ is in kg/(m2 s), $k_i$ is a user-supplied overall gas-side coefficient in m/s,
$c_g=\rho_g/M_g$ is the actual gas molar density in mol/m3, and $M_i$ is component molar mass
in kg/mol. The mole fractions and fugacity coefficients are evaluated from **separate homogeneous
EOS states at the transported gas and aqueous compositions** and the local common pressure and
temperature. A bulk TP flash is not used to repartition the cell inventory. Equal fugacities
give zero flux. This is a dilute, lumped-resistance closure; it does not implement the coupled
Maxwell-Stefan, finite-flux, or interfacial-temperature calculation in the axial evaporation API.

Only explicitly named species transfer. Configuring water and MEG holds methane in its existing
phase inventory: methane dissolution is not implied. Coefficients are inputs to be measured,
correlated, or varied in a sensitivity study; there is no calibrated default.

The source per metre is $j_i S_i$. A positive constructor wetted-perimeter fraction prescribes
that fraction of the full pipe circumference for $S_i$; zero uses the stratified interfacial
width calculated from the current holdup instead. The former can screen a residual wall film, the latter a stratified pool. Neither
option predicts wetting, dry patches, or the coexistence of a separate wall film and pool.
Transfer stops if either receiving gas contact or aqueous inventory is absent: this closure
does not nucleate new liquid in a dry cell.

Each source is capped at half of its donor component inventory per substep. The remaining half
is reserved for advection under the required Euler/CFL limit. Phase sources are the sums of
component sources. Each component carries donor-phase momentum, including when opposing
component fluxes cancel in the net phase mass source. Component and phase reports must both
close; unsupported or nonfinite states reject instead of reporting successful drying.

## Configure and inspect a calculation

The executable Java setup is `TwoFluidPipeFilmDryingTest.pipe`, under
`src/test/java/neqsim/process/equipment/pipeline`. All public methods are also callable through
the Java bridge from Python. In sequence:

1. Define a CPA gas and a separate 99/1 wt% MEG/water liquid, with mixing rule 10. Both must have
   the identical named-component slate; the gas may contain zero MEG. Specify pressure in bara
   and temperature in K. Prepare each homogeneous phase explicitly.
2. Set pipe length, diameter, cell count and, if needed, N+1 cell-face elevations. Provide one
   initial holdup per physical cell. A full-circumference film of thickness $\delta$ has
   $\alpha_l=1-(1-2\delta/D)^2$; a local pool needs its own geometry-based inventory.
3. Enable component transport and mass transfer, select Euler, and use CFL no greater than 0.5.
   Install `NonEquilibriumFilmTransfer` and `setInitialAqueousFilm` before `run`.
4. Disable energy equations, wall heating, and tracked slugs. The opt-in route holds each cell's
   temperature fixed. Pressure remains a hydrodynamic state; it is not prescribed uniformly.
5. Enable `setTransactionalTransientEnabled(true)` to isolate rejected full intervals.
   Advance with `runTransient`, recording the actual accepted elapsed time and each immutable
   component and phase balance report. `setStoreComponentConservationHistory(true)` retains the
   time-aligned reports. Sum their boundary ledgers for cumulative gas use and component removal.
6. Read component mass-fraction, phase-velocity, holdup, pressure and temperature profiles.
   `getEquivalentAqueousFilmThicknessProfile` reports the full-circumference thickness equivalent
   of the local aqueous holdup. For a pool this is **not** its physical liquid depth.
7. Declare a positive operational residual mass threshold and a maximum simulated horizon before
   running. `getFilmDryingStatus(residualMassKg, horizonSeconds)` returns `RUNNING`,
   `RESIDUAL_THRESHOLD_REACHED`, or `HORIZON_REACHED` without altering mass. Report **threshold
   reached**, **horizon reached with residual liquid**, or **solver rejected**. Never relabel the
   threshold as exact mathematical zero. Do not remove sub-threshold mass from the conservation ledger.

The source model currently requires single-stage Euler because the component inventory is updated
once per accepted substep. Energy coupling, multi-stage integration and tracked-slug combinations
reject explicitly. The legacy bulk-equilibrium relaxation path is unchanged when the closure is
not selected. Its supported combinations must not be inferred for this experimental route.

## Evidence and remaining qualification

The tests cover opposing water/MEG flux signs, a zero-coefficient control, independent liquid
initialization, closed and open component conservation, liquid momentum on a seeded low point,
constant temperature, serialization/repeatability, short-time grid/time refinement, a 10 km
short transient, equilibrium zero flux, donor limits, absent-film contact, counter-transfer
momentum and unsupported-configuration rejection.
Numerical tests are not experimental drying validation. The low-point test checks motion and
retention of a seeded pool; it does not establish an accurate terrain-trapping model.

Relevant independent VLE data exist in Folas et al., *Fluid Phase Equilibria* 251 (2007), 52-58,
[DOI 10.1016/j.fluid.2006.11.001](https://doi.org/10.1016/j.fluid.2006.11.001), with a public
[NIST ThermoML record](https://trc.nist.gov/ThermoML/10.1016/j.fluid.2006.11.001.html).
That source reports MEG/methane and MEG/water/methane phase equilibria. It has **not** been used
here to fit transfer coefficients or to establish an error bound for this implementation.

Before reporting a 10 km, 5 C drying duration, qualify the actual gas composition, flow,
diameter, film inventory, terrain, grid, internal time step, residual threshold and transfer
coefficients. Required further evidence includes long-horizon positivity and component closure,
spatial/time refinement of pool redistribution and disappearance, carrying-capacity limits,
experimental VLE comparisons, and independent drying/transport data. Use the broader
[TwoFluidPipe evidence matrix](twofluidpipe-evidence-matrix.md) as well.

### Extended-horizon probes

A 100 m, 0.20 m diameter synthetic low-point case at 0.5 kg/s gas flow and coefficients of
0.001 m/s accepted 30 intervals of 0.1 s with converged component ledgers. Initial aqueous
holdup was 0.002, except 0.02 in the seeded pool. Over 3 s, that pool's holdup decreased to
approximately 0.015834 as liquid remobilized. Net transfer into liquid was approximately
0.00034609 kg water and -0.000030632 kg MEG. This reproducible numerical case is retained in
`seededLowPointRemobilizesOverThreeSeconds`; it is not a drying-time measurement.

**The long-horizon 10 km gate is not passed.** An exploratory 8-cell run with 1 s outer steps
and the uncoupled pressure reconstruction accepted its first interval but then generated an
unphysical pressure excursion (about 1548 bara in a trial state). The forced-phase property
check rejected the trial when the gas EOS root was classified as dense liquid. Enabling the
existing coupled pressure/momentum and interfacial-pressure options instead encountered unsupported
aqueous inflow through the outlet during the first interval. No outlet liquid composition was
provided, so that path correctly rejected rather than inventing incoming fluid. Both probes
used full-interval transactions; rejected trials were not published as accepted states.

These failures concern sustained hydrodynamic/pressure/boundary coupling and are recorded against
#4281, not hidden by weakening donor, phase-identity or boundary guards. The 0.1 s 10 km regression
is an initialization/short-time conservation check only. Do not extrapolate it into a full-line
evaporation duration. Further pressure/boundary work, representative mesh refinement, and
experimental qualification are required.
