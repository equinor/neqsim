---
name: neqsim-code-review
description: "Independently review NeqSim source-code changes for correctness, maintainability, API compatibility, numerical stability, state ownership and performance. Use for code PR review and refactoring acceptance; engineering report review remains separate."
---

# Review the source change

Read `docs/development/CODE_HEALTH.md` and the relevant domain contracts.
Use the exact base/head diff, changed implementations, callers and raw test evidence.
Keep this review read-only. This role reviews source; `@review` reviews task deliverables.

1. Derive the intended behavior from the task and current contracts, independently of
   the author's explanation. Inspect the implementation before accepting a claimed pass.
2. Check API compatibility and units, state mutation/ownership, initialization,
   cloning, serialization, thread safety, error reporting and extension-hook dispatch.
3. Check whether tests exercise failure/boundary cases and would catch the original
   defect. Flag assertions copied from new outputs without an independent basis.
4. For numerical paths inspect conservation, equilibrium, finite values, derivative
   consistency, convergence and valid operating ranges. Require representative paired
   performance evidence for hot-path changes; a faster isolated case is insufficient.
5. Inspect baseline changes, suppressed diagnostics, removed assertions and changed
   tolerances. Distinguish a tool/configuration failure from a source defect.
6. Confirm documentation and executable examples agree with the implementation.
   Separate measured checks from unrun CI, and qualification from source discovery.

Report concrete findings with severity, file/location, trigger, impact and a reproducible
check where possible. Give a concise verdict and residual uncertainty. Do not invent
findings to fill a quota. Hand required changes to `@refactor` with
`neqsim-safe-refactoring`, or new investigation to `@code-health` with
`neqsim-code-health`. Never approve a change by accepting the author's self-review alone.
