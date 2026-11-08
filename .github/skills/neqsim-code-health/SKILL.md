---
name: neqsim-code-health
description: "Assess NeqSim maintainability, technical debt, dependency boundaries, complexity hotspots, duplication and new static-analysis findings. Use for code-health triage, a maintenance backlog, or planning a bounded refactoring with evidence."
---

# Assess code health

Read `docs/development/CODE_HEALTH.md` for commands, baseline policy and limitations.
Use current source and the exact base/head revisions. Discover existing implementations
with `devtools/capability_search.py` before proposing new APIs.
In connector-only sessions, inspect current GitHub source and CI artifacts; mark
local checks unrun instead of assuming that a checkout or build tool is available.

1. Run `devtools/code_health.py --output <evidence.json>` with the selected Python
   executable. Inspect findings and measured file metrics, not just the exit code.
2. Generate and supply PMD and SpotBugs reports for a static-analysis assessment.
   Missing reports or analysis errors are failures. Distinguish unmeasured coverage
   and unavailable history from measured zero values.
   Use the exact reviewed analysis JDK; a runtime precondition failure is not a
   zero-findings pass. For clone warnings, inspect parent and owned-state copies,
   preserve normal copy behavior and propagate failures with their cause.
   For `CT_CONSTRUCTOR_THROW`, preserve constructor validation and prevent finalizer access to
   partially initialized objects. New implementations without an inheritance contract can be
   final; retain the interface as the extension point and document that choice. Do not suppress
   the detector or remove validation to pass the gate.
3. Rank candidates using branch counts, size, import dependencies, duplicate-window
   candidates, bounded history and relevant regression evidence. Size/branch counts
   are proxies; overlapping duplicate windows are not a percentage of duplicated code.
4. Check open work before selecting a change. Keep one coherent maintenance PR and
   coordinate overlapping files with existing campaigns.
5. State the problem, extension point, public contracts, numerical/performance risk,
   test commands and acceptance criteria. Prefer one responsibility with known callers.
6. Hand implementation to `@refactor` with `neqsim-safe-refactoring`, then request
   independent `@code-review` using `neqsim-code-review`. Reuse `@neqsim-test`
   and `@documentation` for distinct test and documentation work.

Return a ranked, source-linked backlog and a bounded first task. Record exact checks,
exit results and unresolved evidence. Never refresh the baseline to make a failure pass;
propose each baseline change explicitly with a reason. Do not call discovery coverage
or a maintenance metric numerical qualification.
