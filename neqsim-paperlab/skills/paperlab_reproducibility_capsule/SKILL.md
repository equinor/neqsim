---
name: paperlab_reproducibility_capsule
description: |
  Build PaperLab reproducibility capsules with raw data, scripts, notebooks,
  command logs, environment records, seeds, manifests, and rerun instructions.
---

# PaperLab Reproducibility Capsule

## When to Use

USE WHEN: preparing a paper submission, revision, supplementary material, or
book computational appendix that must be rerunnable.

## Capsule Structure

```text
replication_package/
  README.md
  manifest.json
  commands.ps1
  environment.txt
  raw_data/
  scripts/
  notebooks/
  expected_outputs/
```

## Manifest Fields

- NeqSim commit hash,
- PaperLab commit hash,
- Java version,
- Python version,
- dependency list,
- random seeds,
- input data files,
- output files and tolerances,
- commands required to rerun,
- known platform limitations.

## Pass Criteria

- A reviewer can rerun the core results without reading private notes.
- Figures and summary metrics have regeneration commands.
- Non-public data dependencies are clearly excluded or replaced with fixtures.
- Expected numerical tolerances are stated.

## Safety Rules

- Do not include internal documents, credentials, tokens, or browser profiles.
- Do not rely on local absolute paths in rerun commands.
- Keep large generated files out of the capsule unless required for review.