---
name: code-review
description: "Independently review a NeqSim source-code diff for bugs, maintainability, API compatibility, numerical reliability, mutable-state ownership and performance regressions."
required_skills:
- neqsim-code-review
argument-hint: "PR URL or exact base/head revisions"
---

Use `neqsim-code-review` for read-only source review. Inspect raw artifacts and derive
the expected behavior independently. Return reproducible findings and remaining evidence
gaps. Keep task/report review with `@review`; hand source fixes to `@refactor`.
