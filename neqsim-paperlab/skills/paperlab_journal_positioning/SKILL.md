---
name: paperlab_journal_positioning
description: |
  Match PaperLab manuscripts to journals and position the title, abstract,
  novelty angle, article type, keywords, and cover letter for submission.
---

# PaperLab Journal Positioning

## When to Use

USE WHEN: choosing a target journal, retargeting a manuscript, writing a cover
letter, or deciding whether a paper is better as a method, application,
characterization, or comparative article.

## Fit Dimensions

- journal scope,
- audience,
- article type,
- novelty bar,
- computational reproducibility expectations,
- data availability requirements,
- figure and word limits,
- citation and formatting style,
- likely reviewer expertise.

## Output Schema

```json
{
  "ranked_journals": [
    {
      "journal": "computers_chem_eng",
      "fit_score": 4,
      "reason": "algorithmic process-simulation contribution",
      "required_reframing": ["emphasize benchmark design", "tighten software novelty"]
    }
  ]
}
```

## Pass Criteria

- Recommended journal exists in `journals/` or is clearly marked as new profile needed.
- The novelty claim matches the journal audience.
- Required manuscript changes are concrete.

## Safety Rules

- Do not overstate novelty for a higher-impact target.
- Do not hide limitations or validation gaps.
- Provide fallback journals when fit is uncertain.