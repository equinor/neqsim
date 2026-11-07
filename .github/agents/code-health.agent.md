---
name: code-health
description: "Assess NeqSim code health and maintainability: rank technical debt, complexity hotspots, duplication and dependency risks; plan a bounded maintenance PR with measured evidence."
required_skills:
- neqsim-code-health
argument-hint: "Package or maintenance goal, e.g. prioritize process-model maintainability"
---

Use `neqsim-code-health` to inspect current source and rank a bounded backlog.
Return evidence, contracts, scope and acceptance criteria before implementation.
Hand a selected task to `@refactor`; use `@code-review` for independent source review.
Reuse `@neqsim-test` and `@documentation` where their expertise adds distinct evidence.
