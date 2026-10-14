---
title: "NRC-2937: TP-flash state and process-consumer contracts"
description: "Proposed contracts for constrained fluid equilibrium, state ownership, compatibility adapters, hydrate consumers, and inactive process inventories."
---

# NRC-2937: TP-flash state and process-consumer contracts

- Status: PROPOSED
- Owners: @EvenSol (thermodynamics and process simulation); independent domain and public-contract reviewers to be recorded
- Created: 2026-10-14
- Target release: UNPLANNED; compatibility and release classification pending review
- Tracking issue: [#2937](https://github.com/equinor/neqsim/issues/2937)
- Related ownership: [#3144](https://github.com/equinor/neqsim/issues/3144), [#4310](https://github.com/equinor/neqsim/issues/4310), [#4311](https://github.com/equinor/neqsim/issues/4311)
- Existing implementations: [#4306](https://github.com/equinor/neqsim/pull/4306), [#4320](https://github.com/equinor/neqsim/pull/4320)
- Hydrate integration: [#4277](https://github.com/equinor/neqsim/pull/4277)
- Baseline master: `d28b180969e248837e68b5e982374db69e11a376`

This proposal is not accepted and supplies no merge approval or engineering qualification.
Publish it through a separate proposal-only draft PR under the [NRC process](../development/rfc-process.md).
Authorized implementation and validation can continue while the proposal is reviewed.
The final implementation must link the recorded decision and satisfy its own numerical and human-review gates.

## Context and problem

The existing TP-flash implementation combines RR, successive substitution, Newton refinement,
stability trials and endpoint recovery. Mutable state includes phase mappings, configured EOS
roots, component and phase amounts, K values, and derivative or association workspaces.
A numerically smaller residual does not justify adopting an inadmissible or unconserved state.

Two current PRs cross the thermodynamics/process ownership boundary:

- #4306 changes clone ownership, phase reuse, numerical trials and flash recovery. Its actual
  patch against the baseline master also changes `MultiStreamHeatExchanger.run(UUID)):
  inactive streams are excluded from heat balances, bypass their inlet state, and contribute
  zero duty; fewer than two active streams give zero duty; recursive calculation preserves
  the caller's calculation identity.
- #4320 adds strict acceptance and observable failure for repaired trace-water splits and
  corrects precision loss during large downward `SystemThermo.setTotalFlowRate` changes.
  It also removes unnecessary fluid-inventory rescaling from
  `WellSystem.calculateVLP_BHP_TwoFluid`; the requested rate continues to determine
  velocities while the property calculation retains a stable reference inventory.

[Governance](../../GOVERNANCE.md) requires an NRC for changes affecting two or more owned
subsystems. These are production behavior changes in both thermodynamics and process
simulation, even though individual corrective hunks are small.

The next draft increment also consolidates the existing CO2/brine helper paths into the
ordinary flash and shared aqueous-reaction coupling. Delegating existing public helpers and
changing their applicability or diagnostic semantics requires explicit architecture and
compatibility review. Retaining method signatures alone does not establish compatibility.

## Decision

The following decision is **proposed**, not accepted.

1. **Independent state ownership.** Public fluid, phase and component clones own mutable
   derivative and association state. Independent calculations, including concurrent ones,
   must not alter another fluid through shared scratch storage.
2. **Phase storage and roots.** Logical active phases map to distinct physical objects.
   Removal preserves a reusable inactive slot. Configured EOS root selection remains
   distinct from composition-dependent phase classification and follows its physical slot.
3. **Transactional trials.** Rejected trials restore mappings, configured roots, phase
   amounts, compositions and per-phase K values. Accepted transfer preserves the original
   inventory and the established initialization/replay boundary. Cancellation and failure
   must not publish a partial result.
4. **Existing constrained equations.** Retain the current RR/SSI/Newton/stability framework.
   Package-private participation constraints distinguish zero feed, freely partitioning
   neutral species and model-defined aqueous-confined ions. Do not introduce a separate
   named-mixture equation set or a new public solver-selection switch.
5. **Common physical acceptance.** Apply finite-state, normalization, nonnegative amounts,
   original-inventory, allowed-phase and molecular fugacity checks consistently to eligible
   endpoints. Check stored phase/component amounts as well as beta and composition.
   These checks are not a global stability certificate or validation against measurements.
6. **Shared aqueous reaction coupling.** Frozen-species phase calculations retain their
   original species inventory. Where chemical reactions apply, couple the existing reaction
   and phase calculations through isolated trials, conserve elements and charge, and accept
   only converged reaction and phase conditions. The proposed internal
   `AqueousReactionEquilibrium` path requires exactly one supported aqueous material phase;
   unsupported reactive topologies remain explicitly outside its qualified scope.
7. **Compatible public adapters.** Retain existing CO2/brine helper entry points as
   documented adapters to the shared calculation, subject to the compatibility decision
   below. Remove composition whitelists only when equivalent or stronger physical
   constraints and qualification exist; component names are not substitutes for model scope.
8. **Common hydrate fluid evaluation.** Hydrate iteration and independent verification use
   the same ordinary TP-flash fluid path. Retain the configured hydrate reference model,
   water-fugacity equation, conservation checks and physical applicability. Report molecular
   stability evidence only when assessed; unavailable evidence remains unavailable.
9. **Bounded performance workspace.** Compact stability copies remain restricted to the
   explicitly qualified exact SRK/PR, classic-mixing, fixed-component/two-slot scope, with
   normal independent-copy fallback elsewhere. Trial phases are not adopted into live
   fluids. This does not relax the public clone contract.
10. **Process consumers.** Preserve inactive exchanger inlet states and calculation identity.
    Use existing flow-floor semantics for thermal participation. Inventory scaling must
    retain valid composition and amounts within its stated accuracy contract; VLP intensive
    property evaluation must not destroy those amounts merely to represent a trial rate.

Retain physical model boundaries for solids, wax, hydrates, reactions, ions and forced or
locked phases. Water-rich trial seeds and qualified performance screens may remain useful;
they are not equivalent to routing final correctness by a named mixture. No parameter fit,
empirical offset, looser convergence tolerance or hidden successful fallback is authorized.

## Public contracts affected

Existing TP-flash, cloning, phase-removal and process entry points remain available.
Review the additive configured-root and compact-copy methods already in #4306:
`SystemInterface.getPhaseType(int)`, `SystemThermo.copyForStabilityTrial()`,
`PhaseEos.copyForStabilityTrial()` and `ComponentEos.copyForStabilityTrial(int)`.
The root accessor must not redefine `getPhase(i).getType()`; trial-copy methods must
state their detached fixed-component purpose and fallback limits.

The new draft keeps `CO2BrinePhaseEquilibrium` and
`ReactiveCO2BrinePhaseEquilibrium` signatures while deprecating them in favor of the
ordinary entry point. Review changed `isApplicable` results, failure behavior, output
topology, iteration reporting and stability diagnostics. Do not remove the adapters under
this proposal.

The proposed `HydrateEquilibriumDiagnostics.getMinimumFluidTrialDistance()` exposes
general molecular evidence. Retaining and deprecating `getMinimumCo2TrialDistance()`
does not by itself make a change from CO2-specific to general evidence semantically
compatible. Owners must explicitly decide and document the legacy accessor's meaning,
migration and release classification before implementation merges.

`FlashPhaseConstraints`, `FluidEquilibriumChecks` and the reaction coupling remain
package-private implementation details. Their introduction does not establish a new
public solver lifecycle, model identity or engineering-approval state.

## Engineering and safety boundary

This decision concerns software and numerical contracts. It does not qualify experimental
solubility, hydrate or WAT prediction, global phase discovery, asset use, industrial
deployment or universal performance parity. Finite-rate pipeline film transfer remains
a separate nonequilibrium model; it must not invoke a bulk flash that redistributes its
independently transported phase inventories.

#2937 owns generic phase search, RR/SSI/Newton, state lifecycle and performance.
#3144 owns electrolyte models, reaction chemistry, parameters and data.
Shared increments use coordinated implementation and both issue ledgers.
#4310 supplies root/phase-slot qualification; #4311 supplies recovery and clone-cost evidence.

A coherent derivative/model-correction PR may proceed separately from master with its
own tests and review. Dielectric/Born chain rules, stationary-shielding derivatives and
honest model-check diagnostics do not become qualified merely because this architecture
proposal is accepted. No parameter catalog, coefficient, reaction constant or hydrate
correlation is changed or approved here. NRC-3144 concerns Pitzer catalog defaults and
does not supply this decision.

## Compatibility and migration

Classify reproducible restoration of intended behavior only after owner and independent
review. Preserve Java 8, existing signatures, serialization identities, subclass fallback,
calculation identity and full-capacity normal cloning. Record observable corrected results
and stricter failure cases. Users should not need a new solver switch.

Applicability broadening, public helper delegation and diagnostic reinterpretation need
explicit review even where binaries still link. If a public or serialized contract is
incompatible, separate that increment until the required release boundary, migration,
compatibility tests and release notes are approved.

## Validation and qualification evidence

Freeze final cases and tolerances before changing expected outcomes. Required evidence includes:

- Original-feed component balance, stored phase/component inventory, normalized nonnegative
  compositions and phase fractions, finite properties, model-defined ion confinement and
  molecular fugacity closure. Retain the frozen case gates; ordinary neutral cases use
  `1e-10` component balance, `1e-12` normalization and `1e-8` log-fugacity residual,
  with tighter existing tests retained.
- Independent Gibbs/TPD evidence when topology changes; zero and trace components, reordered
  species, changed pseudo-component counts, poor initial guesses and inventory scaling.
- Cold, repeated warm, changed T/P/composition, disappearing/reappearing phases,
  removal/reuse, configured roots and complete trial rollback.
- Neutral SRK/PR/CPA and supported electrolyte cases, including non-CO2 molecular guests.
  Reactive cases require element, charge, reaction and phase-equilibrium evidence.
- Common hydrate iteration and independent-seed verification, original inventories and
  residuals; legacy helper and diagnostic compatibility tests, including #4277's MEG cases.
- Derivative/model corrections validated independently by finite differences of their
  declared potential, correct held variables and units, plus caloric and fugacity identities.
- Clone/serialization and independent-system concurrency including derivative properties.
- #4320 public reservoir-to-producer, pump PS closure, failed-repair/reuse, large downward
  flow scaling and VLP shut-in/near-zero-rate cases.
- Inactive, single-active and multiple-active exchanger cases, flow-floor crossings,
  repeated/changed-state operation, duty, balances and calculation identity.
- Final combined-tree checks with #4329 and #4320, then downstream compressor/network/evidence,
  characterization and finite-rate film consumers where shared behavior changes.
- Complete-operation allocation and timing with matched dependencies, interleaved JVM runs,
  numerical snapshots and explicit failed cases excluded from qualified throughput.
- Applicable Java 8/21/platform and slow-test CI, formatting, documentation search,
  generated coverage, hooks, Javadocs and public-contract review.

Historical baseline evidence must remain labeled by its exact revision. The earlier
#4306 head `6d05316627e116fd668a3d23e58963ddc8c995e9` reported 1,351/1,354 focused
passes and 386/389 combined passes, with three electrolyte failures. Those counts neither
describe a later working tree nor qualify a new implementation. The new general integration
is a draft under validation; its final exact tree, commands, exclusions, failures, residuals
and performance provenance must replace no baseline record and be reported separately.
Green CI alone cannot establish scientific acceptance.

Minimum accountable review remains subsystem-owner plus independent domain review for
methods, with independent maintainer review for public/serialized contracts. An author
cannot supply their own independent approval. Delegated agent reviews and user authorization
to publish or merge are not recorded independent human approval.

## Alternatives considered

- **Keep unrelated correctness work inside one expanding flash PR.** Prefer a coherent,
  independently validated model-derivative increment where its dependency boundary is clean;
  retain the general solver integration as draft until its own cases pass.
- **Split dependent Hessian corrections into competing PRs editing the same classes.**
  Avoid an incomplete analytic model or contradictory intermediate derivatives. Group the
  mathematically necessary terms and their regressions coherently.
- **Replace the public flash framework now.** Outside scope; repair and qualify the existing
  equations and consumers before proposing a broader replacement.
- **Preserve named-mixture special cases indefinitely.** Does not resolve generic admissibility;
  retain physical scope guards and useful seeds while converging on shared constraints.
- **Restore clamped states, weaken assertions, or hide failures.** Reject: these can conceal
  ion leakage, lost inventory, non-equilibrium states or false process evidence.
- **Skip the NRC because individual hunks are small.** Reject for the current cross-subsystem
  scope and public-helper delegation.

## Rollback strategy

Keep unqualified integration draft. Preserve reversible, reviewable commits and exact baseline
trees. An optimization can fall back to normal copying without changing physical equations.
On a failed gate, repair or remove the responsible unqualified increment; preserve its failure
evidence and tests. Do not describe a restored known-invalid endpoint as qualified.

If a released defect is found, identify the affected model/state range and revert or correct
the smallest coherent change with its regressions. Preserve compatibility adapters and
provenance. Derivative and solver failures must not be concealed by discarding diagnostics.

## Decision record

- 2026-10-14: PROPOSED for #2937, covering the actual #4306/#4320 thermodynamics/process
  contracts and the draft shared fluid/hydrate compatibility path.
- Numbering follows the existing tracking-issue convention (NRC-2998, NRC-3144, NRC-3860);
  the process defines the filename format but no separate number allocator.
- Proposal PR, owner acknowledgments, independent reviewers, final implementation tree,
  compatibility/release classification and acceptance date: NOT RECORDED.
- Status remains PROPOSED until material comments are resolved and an explicit ACCEPTED,
  REJECTED or SUPERSEDED decision is recorded.
- Acceptance would record architectural agreement, not method or engineering qualification.
