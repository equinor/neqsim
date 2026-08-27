---
title: Engineering diagram performance evidence
description: Deterministic regression evidence and conservative CI budgets for representative DEXPI package, rendering, and revision-impact workflows.
---

# Engineering diagram performance evidence

NeqSim records a small, synthetic performance baseline for the representative multi-area
engineering-diagram delivery path. The benchmark is regression evidence: it detects unexpectedly
slow or nondeterministic changes in review, but it is not a capacity claim, standards-conformance
assessment, engineering approval, or fitness-for-construction determination.

## Covered operations

`EngineeringDiagramPerformanceBenchmarkTest` measures the median and maximum wall-clock time from
three recorded samples after one warm-up run for:

- assessed DEXPI 2.0 Process package export;
- immutable package intake;
- native SVG and PDF rendering from the controlled document model;
- package-to-package revision impact; and
- fail-closed projection of package changes into drawing, sheet, register, and review scope.

Every sample also produces a semantic output fingerprint. The validator rejects an operation when
the fingerprints differ within a run, when the operation set drifts from the controlled budget, or
when the recorded median exceeds its budget. The reference fixture includes multiple areas and
explicit material, energy, and information connections.

Package intake measures exact-content inspection and reassessment. It does not reconstruct a
`ProcessModel`. Rendering measures the repository's native SVG/PDF projection; it does not measure
Graphviz, external DEXPI viewers, commercial CAE tools, or accountable drawing review.

## Run locally

```bash
./mvnw -B -ntp \
  -Dtest=EngineeringDiagramPerformanceBenchmarkTest \
  -Dgroups=benchmark \
  -DexcludedTestGroups= \
  -Djacoco.skip=true \
  test

python devtools/validate_engineering_diagram_performance.py \
  --report target/engineering-diagram-performance.json \
  --budget devtools/baselines/engineering_diagram_performance.json

python -m unittest devtools.test_validate_engineering_diagram_performance
```

The benchmark writes `target/engineering-diagram-performance.json`. Its fixed boundary fields are
`engineeringStatus: PERFORMANCE_REGRESSION_EVIDENCE_ONLY`, `approvalStatus: REVIEW_REQUIRED`, and
`fitnessForConstruction: false`.

## Budget policy

Budgets in `devtools/baselines/engineering_diagram_performance.json` are intentionally conservative
hosted-runner medians. A single slow sample is retained in the artifact as `maximum` but does not
fail the gate. Change budgets only with repeated evidence from comparable runners and explain the
reason in review; never relax them to hide a deterministic functional regression.

This gate does not establish throughput, scalability, production sizing, or release completion on
its own. Final completion still requires the independent current-master audit and the applicable
validation, interoperability, licensing, and accountable-engineering decisions documented in the
[DEXPI and P&ID current-master audit](dexpi-pid-current-master-audit).

