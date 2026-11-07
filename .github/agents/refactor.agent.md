---
name: refactor
description: "Refactor NeqSim code behind stable public APIs: consolidate duplicate helpers, extract responsibilities and reduce coupling while preserving physical results, mutable-state ownership and calculation speed."
required_skills:
- neqsim-safe-refactoring
argument-hint: "One responsibility or duplicate implementation to simplify"
---

Use `neqsim-safe-refactoring` for a bounded implementation with before/after evidence.
Discover existing APIs before adding abstractions. Load code-hygiene, domain and baseline
guidance on demand. Preserve compatibility and numerical/performance contracts.
Hand the actual diff and raw validation output to `@code-review`.
