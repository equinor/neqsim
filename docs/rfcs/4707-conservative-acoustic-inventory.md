---
title: "NRC-4707: Safety Shutdown conservative acoustic inventory"
description: "Proposed cell-integrated characteristic, public diagnostic and checkpoint migration contracts for single-line Safety Shutdown acoustics."
---

# NRC-4707: Safety Shutdown conservative acoustic inventory

- Status: PROPOSED
- Owners: @EvenSol (process simulation and safety); independent domain/contract reviewers to be recorded
- Created: 2026-10-29
- Target release: UNPLANNED; compatibility classification pending owner review
- Tracking issue: [#4707](https://github.com/equinor/neqsim/issues/4707)
- Baseline: `cc35f058e6076ce634fa7321fdc12bfb55501a28`
- Tested implementation candidate: `23a94e08fc5ae0cff1901a2132c95858486f4552`

This proposal is **not accepted** and provides no merge approval or engineering
qualification. Publish it as a proposal-only draft under the
[NRC process](../development/rfc-process.md). Keep the separately authorized,
tested implementation in its sole draft PR. Neither draft may be described as
ready until the relevant governance, compatibility, CI and human-review gates pass.

## Context and problem

Merged #4708 repaired physical characteristic timing, valve equilibrium and the
independent receiver pressure. Merged #4714 added prescribed actual valve travel
and unit-aware Kv/US-Cv capacity; #4720 repaired exact absolute travel knots.
They retain a changing-flow nodal MOC storage defect below unit Courant. Fresh
unmodified baseline reproduces 50.37/25.19/12.59 mL errors on 51/101/201 nodes,
against an unchanged 1e-9 m3 acoustic-volume target. The independently derived
nodal defect identity passes, while physical conservation fails.

An endpoint node is a physical trace, not the spatial mean of an endpoint
half-cell. Correcting the final pressure sum or rescaling boundary flux merely
to fit nodal trapezoidal quadrature would hide this representation error.
Coupled pump/disc and process-inventory qualification must not rely on it.

## Decision

The following decision is proposed, not accepted:

1. Integrate the **existing linear acoustic characteristics** over N-1 physical
   cells bounded by the N configured nodes. Use one shared pressure/flow Riemann
   trace at each face. Replace the numerical update inside WaterHammerPipe;
   do not add a second solver, selection flag, pipeline architecture or scheduler.
2. Evolve cell mean pressure head by the conservative face-flow difference.
   Summing cells cancels every interior face exactly. Independently accumulate
   actual boundary exchange and compare it with cell pressure storage. No
   posthoc pressure correction, artificial inventory or flux rescaling.
3. Reconstruct the initialized linear pressure gradient exactly and use signed
   implicit Darcy resistance about that reference. Terrain and steady friction
   remain balanced; momentum change is checked against independent physical
   pressure, gravity and friction impulses.
4. Retain the existing event-localized pipe clock and complete requested
   interval. Boundary capacity uses right-endpoint hold over each internal
   interval, a declared first-order time quadrature preserving exact-knot
   semantics. Report endpoint traces separately from interval-integrated flux.
5. Preserve existing ESD API names. Call the engineering study **Safety Shutdown**.
   Add defensive cell diagnostics and explicit acoustic balance output without
   promoting execution success or an acoustic balance PASS into safety approval.
6. Preserve and label historical FAIL evidence. Require independent wave,
   impulse, energy, timing, equilibrium, volume, momentum and refinement gates
   before merging this candidate. Do not loosen a gate when a candidate fails.

## Public contracts affected

- WaterHammerPipe constructor, setters, run/runTransient and existing profile
  signatures remain. Existing N-entry profiles are physical face traces at
  x=i*L/(N-1); corrected numerical values can change. They are not exact cell
  inventory quadrature values.
- Add `getCellPressureProfile`, `getCellFlowProfile`, `getCellPositions`,
  `getAcousticStoredVolume`, integrated inlet/outlet volume and acoustic residual
  getters, plus momentum-change/pressure/gravity/friction impulse diagnostics.
- WaterHammerStudy adds optional `finalCellState` with initial/final mean
  pressure [Pa], mean actual flow [m3/s], centre/width [m], time [s] and frozen
  density [kg/m3]. Validation distinguishes acoustic balance from full physical
  and integrated Safety Shutdown qualification.
- The serialized state adds physical cell means, reference values and exchange/
  impulse ledgers. A pre-change checkpoint lacks those quantities; silently
  inventing its past accepted exchange or reconstructing a lost subcell history
  is not permitted. The candidate rejects transient continuation until explicit
  `run()` reinitializes a new case. See the release decision below.

## Engineering and safety boundary

The physical PDE remains frozen-property, single-line, small-disturbance linear
acoustics with a prescribed valve/receiver boundary. Density, viscosity, wave
speed and Darcy friction are frozen from the actual initialized fluid; no
water-property fallback. Effective wave speed includes wall elasticity.

An acoustic stored-volume identity is not a full EOS mass/energy ledger.
First-order transport is diffusive and endpoint-held forcing needs time
refinement. Inlet single-phase and positive finite pressure guards do not
detect all transient phase-boundary or cavity risks. No pressure-floor cavity
substitute, signed receiver thermodynamics or vapor-collapse model is supplied.

#2911 retains coordinator, physical identities, transactions and sampled-control
ownership; #2907/#3298 retain multiphase/network physics; #4149 retains compressor
physics; #3860 retains source terms. No shared-owner chain is changed here.
Pump coastdown/inertia, dynamic check-valve slam, junctions, coupled safety
execution, vector forces and structural screening remain separate dependencies.
No SIL/SRS/proof-test, asset or piping-support approval follows from acceptance.

## Compatibility and migration

Owners and independent reviewers must decide whether this is a compatible
numerical correctness repair with explicit checkpoint reinitialization, or an
incompatible persisted-method contract. **That classification is not decided
by the author.** The public API lifecycle policy applies either way.

If incompatible, require the approved major-contract/release boundary,
migration documentation and compatibility tests before the implementation
merges or ships. This proposal does not waive those requirements or claim that
retaining serialVersionUID alone preserves old checkpoint semantics.

Preserve original model inputs and old results with their exact source revision.
For a new conservative case, initialize explicitly, retain its initial cell
means, and advance the new state. Use cell means and accepted-substep exchange
for inventory checks. Refine grid and time before interpreting corrected
waveforms. New conservative checkpoints must replay all means and ledgers
deterministically. No silent checkpoint conversion is claimed.

## Validation and qualification evidence

The candidate is executed, not a source-only proposal. Its
[public evidence](https://github.com/equinor/neqsim/blob/23a94e08fc5ae0cff1901a2132c95858486f4552/src/test/resources/neqsim/process/pipeline/safety-shutdown-valve/conservative-kernel/README.md)
contains raw inputs/outputs, baseline FAIL, refinement, nearby cases, runtimes
and exact source/runtime provenance. The ledger records commands and final head.

- **238 local tests pass**, zero failures/errors/skips. Changed Java units compile
  with release8; execution is OpenJDK17.0.20, not actual local Java8 execution.
- Changing travel: independent cell storage versus accepted exchange residual
  around 2e-17 to4e-17 m3, below unchanged1e-9m3 gate.
- Nine variable-step closure/hold/reopening cases, 51/101/201 nodes,
  18000/36000/54000kg/hr, 12m rise: maximum volume4.08e-17m3 and momentum
  2.66e-10kg m/s, against1e-9m3 and1e-7kg m/s gates.
- Independent pre-arrival closure storage Q0*t; unchanged1% Joukowsky,
  L/a and2L/a arrival/reflection, exact duration/knots and equilibrium tests.
- Ideal d'Alembert standing wave at0.37s: combined pressure/characteristic-flow
  relative RMS errors1.279%,0.643%,0.322%; refinement ratios1.991/1.995.
  Closed ideal energy is nonincreasing at every evaluated step. These ideal
  initial-value fixtures do not substitute fluid properties.
- Signed implicit-friction limit, rejected local interval preservation,
  deterministic serialization/replay and explicit old-checkpoint rejection.
- API/Javadoc, guides, source skill/navigation, generated coverage, actual hooks,
  formatting and documentation search checked. Thirty complete-duration closure
  diagnostic runtimes1.641–795.689ms; no warmed speedup or throughput claim.

Exact-head hosted CI and independent accountable review remain separate gates.
Analytical verification is not asset validation or a converged slow-actuator
waveform. Calibration uses synthetic steady Kv selection only; wave/energy
oracles are independent of that selection.

## Alternatives considered

- **Keep nodal interpolation and correct final inventory/pressure:** rejected;
  hides a local conservation defect and changes stored state after the solve.
- **Rescale physical boundary flux to match nodal quadrature:** rejected;
  changes the physical boundary law to fit a reported budget.
- **Use full-Courant intervals only:** insufficient for arbitrary requested
  times and exact actuator knots unless a separately verified dense physical
  representation is supplied; remainder time cannot be discarded.
- **Add a second pressure-flow solver or mode:** rejected; repairs belong in
  the existing owned WaterHammerPipe update.
- **Losslessly convert old nodal checkpoints:** unavailable evidence; neither
  subcell inventory nor historical accepted flux ledger is recoverable from
  sparse endpoint samples alone. Explicit reinitialization is proposed instead.
- **Higher-order characteristic reconstruction now:** a distinct numerical
  increment; first establish and review conservative first-order limits.

## Rollback strategy

Keep this implementation draft until the governance and scientific gates pass.
The branch is one coherent reversible increment from the exact recorded base;
do not force-push, overwrite another owner or silently restore a known-invalid
prediction as qualified. Preserve baseline and rejected evidence.

If a defect is found after release, identify affected model/state ranges and
revert or repair the coherent numerical increment with its regressions. Retain
checkpoint provenance and do not claim interchangeability between methods.

## Decision record

- 2026-10-29: PROPOSED under #4707; separate proposal-only draft prepared after
  the validated implementation candidate and before implementation PR opening.
- Number follows the tracking-issue convention used by NRC-2937/2998/3144/3860.
- Formal sequence deviation: prototype implementation/validation preceded the
  proposal because governance review was completed late. It is disclosed here;
  it supplies no retrospective acceptance or independent approval.
- Owner acknowledgment, independent domain/contract reviewers, release/major
  version classification and acceptance decision: **NOT RECORDED**.
- Status remains PROPOSED until an explicit ACCEPTED, REJECTED or SUPERSEDED
  decision is recorded. Acceptance would record architecture, not plant qualification.
