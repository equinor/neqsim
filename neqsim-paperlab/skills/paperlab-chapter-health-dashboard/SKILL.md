---
name: paperlab-chapter-health-dashboard
description: |
  Aggregate PaperLab audit outputs into chapter readiness scores and a release
  dashboard. Use when deciding which chapters are ready, need minor revision,
  need major revision, or block a release.
---

# PaperLab Chapter Health Dashboard

## When to Use

USE WHEN: a book has multiple audit outputs and authors need a prioritized fix
queue before release.

Pair with:

- `paperlab-book-typesetting-release` for render gates,
- `paperlab-scientific-traceability-audit` for evidence gates,
- `paperlab-learning-objective-matrix` for teaching gates.

## Score Dimensions

| Dimension | Inputs |
|-----------|--------|
| structure | book-check, chapter front matter |
| evidence | evidence report, figure dossier |
| learning | objective matrix, exercise audit |
| technical | equation, API, standards audits |
| computation | notebook verification and regression status |
| figures | style and accessibility audit |
| prose | readability and conciseness audits |

## Release Classes

- `ready`: no blockers, only optional polish.
- `minor-revision`: small edits needed, no release blocker.
- `major-revision`: meaningful content or evidence gaps.
- `blocked`: render, evidence, API, notebook, or standards issue prevents release.

## Output Pattern

```markdown
| Chapter | Status | Confidence | Main Blocker | Next Fix |
|---------|--------|------------|--------------|----------|
| Ch10 | minor-revision | high | weak exercise coverage | Add one TEG sensitivity exercise |
```

## Safety Rules

- Show missing audit data as confidence loss, not hidden failure.
- Do not average away blockers.
- Keep next fixes concrete and owned by a suitable agent.