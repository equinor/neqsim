---
name: paperlab_book_to_paper_extraction
description: |
  Extract publishable paper candidates from PaperLab books, including case
  studies, benchmark suites, review syntheses, workflows, and teaching datasets.
---

# PaperLab Book to Paper Extraction

## When to Use

USE WHEN: a mature book contains chapters, examples, or notebooks that could be
turned into standalone scientific papers.

## Candidate Types

- benchmark characterization paper,
- engineering application paper,
- methods tutorial with validation,
- review or perspective article,
- dataset or software note,
- reproducible teaching-lab paper.

## Readiness Checks

For each candidate, check:

- original contribution,
- evidence and benchmark depth,
- literature gap,
- target journal fit,
- missing validation,
- required new experiments,
- overlap risk with the source book.

## Output Schema

```json
{
  "candidates": [
    {
      "chapter": "ch08",
      "paper_type": "application",
      "novelty_angle": "validated workflow for compressor map screening",
      "missing_evidence": ["independent reference case"],
      "recommended_next_agent": "paper-planner"
    }
  ]
}
```

## Safety Rules

- Avoid duplicate publication unless the paper adds new synthesis or validation.
- Do not extract confidential case material into public manuscripts.
- Keep pedagogical conclusions separate from scientific novelty claims.