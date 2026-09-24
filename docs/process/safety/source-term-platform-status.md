---
title: Source-term platform implementation status
description: Delivered capabilities, evidence boundaries and remaining acceptance criteria for the supplier-neutral live source-term platform tracked in issue 3860.
---

# Source-term platform implementation status

Tracking issue: [#3860](https://github.com/equinor/neqsim/issues/3860).
Implementation scope as of 2026-09-23; this does not confer qualification or replace domain review.

## Delivered foundations

Architecture proposal [#3861](https://github.com/equinor/neqsim/pull/3861) is merged.
Physics [#3867](https://github.com/equinor/neqsim/pull/3867), exchange contract
[#3868](https://github.com/equinor/neqsim/pull/3868), and live integration
[#3870](https://github.com/equinor/neqsim/pull/3870) reached `master` through merge commit
`b08d8bb59a95cf52e0565fab5c368c5548a9f27f`. The latter two merged into the physics branch first.

The current implementation includes:

- Opt-in homogeneous-equilibrium short-opening physics, immutable inputs/results,
  explicit thermodynamic stations and required closure checks.
- Versioned deterministic JSON, NDJSON and reduced CSV, bundled JSON Schema,
  source identity, provenance and machine-readable failure/lifecycle states.
- Steady runs, external capture and native dynamic stepping for `ProcessSystem` and
  `ProcessModel`, including real separator dynamics in tests.
- [Weighted uncertainty ensembles](source-term-uncertainty), retaining failures and refusing
  unconditional statistics for incomplete ensembles, with schema-compatible case frames.
- Explicit ideal-gas and legacy-screening adapters. The ideal-gas path has no silent property
  defaults; the compatibility path reports `SCREENING_ONLY` and unresolved station semantics.
- [Coupled inventory depletion](coupled-release-inventory) through native process dynamics,
  with component/energy/volume closure, atomic unit updates, receiving-pressure event location,
  timestep-refinement evidence and schema-validated frames from both process containers. The
  compatibility API remains single-gas; an explicit phase-selected API supports equilibrium
  gas, oil, generic-liquid and aqueous withdrawal without inferring entrainment or phase fallback.
- Analytical gas limits, dense-fluid conservation and flashing, multicomponent, lifecycle
  and contract regressions in focused CI.
- Guarded same-EOS continuation for the previously unresolved propane/butane entropy root,
  with nine nearby mixture cases, a separate saturation-path comparison, inventory scaling,
  back-pressure sensitivity and retained schema/benchmark evidence. Model version `1.2.0`
  preserves the acoustic warning and `UNQUALIFIED` evidence boundary.
- Mixture-specific, fail-closed equilibrium-solid and hydrate applicability assessment at the
  resolved upstream, throat and ambient-expanded stations. Detected risk is `UNSUPPORTED`;
  an unresolved required assessment is `INVALID`. This does not add solid-bearing flow physics.
- A separate ideal-gas Fanno model for quasi-steady, one-sided full-bore release through a
  constant-area pipe with explicit length and specified Darcy friction. The same immutable
  geometry reaches steady/live process frames and coupled inventory depletion; short-opening
  models reject it rather than ignoring it.
- Immutable per-model evidence manifests now retain stable applicability and limitation codes,
  typed analytical/conservation/numerical/experimental references, and an explicit independent-
  evidence flag in schema-validated frames. Current built-in manifests truthfully remain
  `UNQUALIFIED`; no successful calculation promotes its own evidence level.
- The executed NeqSim-Colab safety source-term demonstration is merged through
  [EvenSol/NeqSim-Colab #176](https://github.com/EvenSol/NeqSim-Colab/pull/176), with retained
  outputs, rendered-equation/figure inspection, catalog entry and repository validation.

Legacy `LeakModel` behavior is preserved. The new interface now has homogeneous-equilibrium,
analytical ideal-gas and explicit legacy-screening implementations. Model selection remains
caller-owned; no phase-count rule silently changes the requested physics.

## Remaining work before the whole issue can close

| Work item | Current boundary | Completion evidence required |
|---|---|---|
| Broader transient inventory regimes | Rigid adiabatic equilibrium inventory supports explicit phase-selected withdrawal, caller-declared ordered phase-exhaustion transitions, conservative receiving-pressure events, and balance/refinement tests. | Assessed phase-level/geometry, entrainment/slip and finite-rate interfacial transfer beyond the current well-mixed equilibrium boundary. |
| Full-bore/long-pipe and non-equilibrium regimes | A bounded ideal-gas Fanno model covers quasi-steady one-sided constant-area pipe flow with specified Darcy friction and analytical validation. | Real-gas/transient decompression and separate non-equilibrium multiphase models, applicability controls and independent validation data. |
| Independent qualification and dense-fluid accuracy | Machine-readable model evidence records now distinguish applicability, limitations, evidence type and independence; current records are analytical/conservation/numerical and frames remain `UNQUALIFIED`. | Add independently sourced datasets with error/range analysis, record them without self-promotion, and obtain domain review. |
| Solid-formation applicability | Mixture-specific solid/hydrate station assessment now fails closed and retains machine-readable diagnostics. | Assessed solid-bearing release physics where supported, plus independent mixture validation. |
| Multicomponent flashing qualification | The documented 80/20 propane/butane entropy root and nearby cases now close with guarded continuation; a separate same-EOS saturation path checks the maximum. Acoustic warnings remain explicit. | Independent experimental benchmarks and domain review; broader mixtures are not qualified by the regression matrix. |
| Colab demonstration | Executed, output-retaining demonstration merged in NeqSim-Colab #176 with rendering, catalog and validation-ledger checks. | No remaining implementation item; broader physical qualification remains governed by the rows above. |

Do not close #3860 merely because initial PRs merged or focused CI passes.
The [NRC](../../rfcs/3860-source-term-platform) retains design intent; current guidance is in
[physics](release-flow-models), [contract](source-term-contract),
[live sessions](live-source-term-sessions) and [uncertainty](source-term-uncertainty).
