---
name: paperlab_book_release_orchestration
description: |
  Define the PaperLab whole-book release workflow: audit order, blocker rules,
  render commands, artifact freshness checks, and release gate reporting.
---

# PaperLab Book Release Orchestration

## When to Use

USE WHEN: preparing a PaperLab book release candidate or regenerating all final
artifacts after content, notebook, or figure changes.

Pair with:

- `paperlab_book_typesetting_release` for output formats,
- `paperlab_chapter_health_dashboard` for chapter readiness,
- `paperlab_notebook_regression_baselines` for computational gates.

## Release Gate Order

1. Source inventory and coverage check.
2. Learning objective and curriculum checks.
3. Scientific traceability, equation, API, and standards checks.
4. Notebook verification and regression checks.
5. Figure style and context checks.
6. Conciseness and adversarial review.
7. Chapter readiness dashboard.
8. Render outputs and artifact freshness check.
9. Final release gate report.

## Blockers

Block release for:

- failed `book-check`,
- unresolved TODOs in release chapters,
- missing main figures,
- failed evidence check,
- broken NeqSim API examples,
- broken notebooks that support published claims,
- unsupported formal compliance language,
- render failures,
- stale standalone HTML or PDF after source edits.

## Report Pattern

```markdown
# Release Gate Report

- Book: <book slug>
- Date: <date>
- Decision: release | hold
- Commands run:
  - <command>
- Blocking issues:
  - <issue, owner agent, next action>
- Artifacts refreshed:
  - `submission/book.html`
  - `submission/book_standalone.html`
  - `submission/book.pdf`
```

## Safety Rules

- Do not publish when blocker audits fail.
- Record exact commands and environment details.
- Respect book-specific instructions for ignored source directories and render paths.