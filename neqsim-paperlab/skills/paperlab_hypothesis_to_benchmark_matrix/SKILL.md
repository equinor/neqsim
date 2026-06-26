---
name: paperlab_hypothesis_to_benchmark_matrix
description: |
  Convert PaperLab research questions into falsifiable hypotheses, benchmark
  matrices, statistical tests, and acceptance criteria for computational papers.
---

# PaperLab Hypothesis to Benchmark Matrix

## When to Use

USE WHEN: a paper idea needs to become an executable benchmark plan before
`benchmark-runner` starts work.

## Required Elements

Every hypothesis must define:

- claim wording,
- null expectation or baseline,
- controlled variables,
- varied variables,
- response metric,
- minimum case count,
- statistical test or deterministic acceptance criterion,
- expected failure or boundary modes,
- source of reference values when relevant.

## Benchmark Matrix Pattern

```json
{
  "hypotheses": [
    {
      "id": "H1",
      "claim": "candidate solver reduces failures for near-critical mixtures",
      "metric": "failure_rate_pct",
      "baseline": "existing solver",
      "test": "paired proportion test",
      "acceptance": "p < 0.05 and absolute reduction >= 5 percentage points",
      "case_categories": ["near-critical", "two-phase", "trace-water"]
    }
  ]
}
```

## Pass Criteria

- No claim exists without a metric.
- Boundary cases are explicit, not accidental.
- Benchmark cases can be regenerated from configuration.
- The matrix is small enough to rerun during revision.

## Safety Rules

- Do not retrofit metrics after seeing results unless clearly marked exploratory.
- Do not mix validation data and training/tuning data without disclosure.
- Do not use proprietary benchmark inputs in public capsules unless cleared.