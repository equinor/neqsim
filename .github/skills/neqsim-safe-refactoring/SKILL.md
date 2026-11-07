---
name: neqsim-safe-refactoring
description: "Refactor NeqSim source while preserving public APIs, physical results, state ownership and speed. Use for extracting responsibilities, consolidating duplicate implementations, reducing coupling, or simplifying code behind stable facades."
---

# Refactor a bounded responsibility

Use `docs/development/CODE_HEALTH.md` and the existing `neqsim-code-hygiene`
and `neqsim-regression-baselines` guidance. Load domain guidance only for touched code.

1. Identify the responsibility and inspect every relevant caller and extension hook.
   Record units, initialization, side effects, mutation, clone ownership, thread safety,
   serialization, failure behavior and supported ranges where applicable.
2. Run the current behavior first. Add meaningful characterization or regression tests
   before editing. Label recorded behavior separately from independent accuracy evidence.
3. Reuse existing helpers and retain compatibility wrappers/facades. Preserve subclass
   dispatch, dimensions and numerical operation order where results are sensitive.
4. Extract or consolidate one responsibility. Keep changes to equations, parameters,
   convergence thresholds and physics defaults as separate, explicitly qualified work.
5. Run focused tests and adjacent integration checks, formatter and documentation gates.
   For numerical changes use `docs/development/model_spec_harness.md`; for hot paths
   use `docs/development/steady_state_performance_benchmark.md` with paired measurements.
   Check convergence, phase topology, balances, changed-state refresh and clone isolation
   as applicable. Do not replace failed evidence with weaker tolerances or skipped tests.
6. Compare code-health findings against the reviewed baseline. Record any new findings
   and justified compatibility changes; follow `docs/development/api-lifecycle.md`.
7. Update affected docs/examples and hand the actual diff plus raw evidence to
   `@code-review` with `neqsim-code-review`. Do not supply a prescribed review verdict.

Return the diff, contract assessment, commands/exit results, before/after behavior,
performance evidence when relevant, and remaining limitations. A blocked check is
pending evidence. For a coding evaluation use the catalog's pinned base and an evaluator
outside the candidate checkout; never edit acceptance tests or the grading baseline.
