---
name: paperlab_notebook_regression_baselines
description: |
  Define notebook baseline, tolerance, and stale-figure rules for PaperLab
  computational books. Use when notebooks generate figures, tables, or numeric
  claims that must remain stable as NeqSim and dependencies evolve.
---

# PaperLab Notebook Regression Baselines

## When to Use

USE WHEN: a book has notebooks whose outputs support chapter figures, tables,
worked examples, or quantitative claims.

Pair with:

- `neqsim-notebook-patterns` for notebook execution structure,
- `neqsim-regression-baselines` for numerical drift principles,
- `paperlab_scientific_traceability_audit` for claim provenance.

## Baseline Types

| Type | Example | Tolerance Pattern |
|------|---------|-------------------|
| scalar | pressure drop, NPV, water dew point | absolute + relative tolerance |
| table | stream table, sensitivity output | per-column tolerance |
| figure | generated PNG path | existence + freshness + optional hash |
| environment | NeqSim version, Python package list | recorded metadata |
| claim | chapter number tied to notebook output | text-value match tolerance |

## Classification

- `pass`: outputs are within tolerance and figures are fresh.
- `stale`: notebook likely runs, but figure or claim output is outdated.
- `broken`: execution or import fails.
- `missing-baseline`: notebook has important outputs but no baseline yet.
- `expensive-skip`: execution is known expensive and requires manual review.

## Output Schema

```json
{
  "notebook": "chapters/ch10/notebooks/ch10_s02_teg_contactor.ipynb",
  "status": "pass",
  "engine": "neqsim_dev_setup",
  "baselines": [
    {
      "name": "dry_gas_water_ppm",
      "value": 32.4,
      "unit": "ppm",
      "abs_tol": 0.5,
      "rel_tol": 0.02
    }
  ],
  "figures": [
    {"path": "figures/ch10_teg_sensitivity.png", "status": "fresh"}
  ]
}
```

## Safety Rules

- Do not overwrite accepted baselines without explicit approval.
- Record environment metadata whenever execution occurs.
- Prefer stable engineering outputs over fragile full-notebook diffs.