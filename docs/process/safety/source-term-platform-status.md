---
title: Source-term platform implementation status
description: Delivered capabilities, evidence boundaries and remaining acceptance criteria for the supplier-neutral live source-term platform tracked in issue 3860.
---

# Source-term platform implementation status

Tracking issue: [#3860](https://github.com/equinor/neqsim/issues/3860).
Implementation scope as of 2026-09-21; this does not confer qualification or replace domain review.

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
- [Coupled gas inventory depletion](coupled-release-inventory) through native process dynamics,
  with component/energy/volume closure, atomic unit updates, timestep-refinement evidence and
  schema-validated frames from both process containers. Gas-only applicability is explicit.
- Analytical gas limits, dense-fluid conservation and flashing, multicomponent, lifecycle
  and contract regressions in focused CI.

Legacy `LeakModel` behavior is preserved. The new interface now has homogeneous-equilibrium,
analytical ideal-gas and explicit legacy-screening implementations. Model selection remains
caller-owned; no phase-count rule silently changes the requested physics.

## Remaining work before the whole issue can close

| Work item | Current boundary | Completion evidence required |
|---|---|---|
| Broader transient inventory regimes | Rigid adiabatic gas inventory is coupled and balance/refinement tested. Ordinary live sources remain hypothetical. | Multiphase/phase-selective withdrawal, pressure-equilibration event handling and qualification beyond the gas-only boundary. |
| Full-bore/long-pipe and non-equilibrium regimes | Outside the short-opening model. | Separate physical models, applicability controls and validation data. |
| Independent qualification and dense-fluid accuracy | Analytical and conservation regressions; frames remain `UNQUALIFIED`. | Independent datasets, error/range analysis, model evidence records and domain review. |
| Solid-formation applicability | Conservative CO2 temperature and enabled-solid checks exist. | Mixture-specific solid-risk assessment and assessed solid-capable physics where supported. |
| Multicomponent flashing robustness | Documented 80/20 propane/butane case fails closed. | Resolved entropy root, phase/inventory closure and independent benchmarks. |
| Colab demonstration | Foundation APIs merged. | Executed notebook, retained outputs, visual inspection, catalog and validation ledger. |

Do not close #3860 merely because initial PRs merged or focused CI passes.
The [NRC](../../rfcs/3860-source-term-platform) retains design intent; current guidance is in
[physics](release-flow-models), [contract](source-term-contract),
[live sessions](live-source-term-sessions) and [uncertainty](source-term-uncertainty).
