---
name: paperlab_publication_opportunity_mining
description: |
  Mine NeqSim and PaperLab repositories for publishable scientific papers,
  book chapters, benchmark notes, and review articles. Use when looking for the
  next high-impact PaperLab publication opportunity.
---

# PaperLab Publication Opportunity Mining

## When to Use

USE WHEN: deciding what PaperLab should write next, scanning NeqSim for
publication-worthy changes, or converting code and task history into a ranked
paper/book roadmap.

## Signals to Inspect

| Signal | Why it matters |
|--------|----------------|
| Recent Java classes or tests | Fresh capability or unreported improvement |
| Existing notebooks and figures | Lowers effort to produce evidence |
| Changelog and task log entries | Indicates solved problems with reusable context |
| Unvalidated model claims | Creates validation-paper opportunities |
| Repeated book sections | May become a standalone review or tutorial paper |

## Scoring Dimensions

Score candidates from 0 to 5 for:

- novelty,
- NeqSim improvement impact,
- validation data availability,
- benchmark feasibility,
- figure and table richness,
- journal fit,
- book reuse potential.

## Output Schema

```json
{
  "opportunities": [
    {
      "slug": "flash_solver_convergence",
      "title": "Flash solver convergence across multiphase envelopes",
      "paper_type": "characterization",
      "readiness": "needs_validation",
      "target_journals": ["computers_chem_eng"],
      "required_evidence": ["benchmark matrix", "reference data", "failure taxonomy"],
      "neqsim_improvement": "adds regression benchmarks and solver diagnostics",
      "score": 22
    }
  ]
}
```

## Pass Criteria

- Each proposed topic has a concrete NeqSim improvement.
- Each topic has a validation or reproducibility path.
- At least one target output is named: paper, chapter, review, benchmark note, or lab.

## Safety Rules

- Do not claim novelty without checking prior PaperLab papers and obvious literature.
- Keep private task names and asset tags out of public roadmaps.
- Mark speculative ideas as speculative.