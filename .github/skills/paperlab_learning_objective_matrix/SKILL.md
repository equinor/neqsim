---
name: paperlab_learning_objective_matrix
description: |
  Map PaperLab learning objectives to sections, figures, notebooks, examples,
  exercises, and assessments. Use when verifying that a chapter or whole book
  delivers on its stated student outcomes.
---

# PaperLab Learning Objective Matrix

## When to Use

USE WHEN: a book or chapter has learning objectives and needs proof that each
objective is supported by teaching and assessment assets.

Pair with:

- `paperlab_student_readability` for objective wording,
- `paperlab_exam_alignment` for assessment coverage,
- `paperlab_notebook_regression_baselines` for computational objectives.

## Objective Asset Types

Map objectives to these assets:

| Asset | Required When |
|-------|---------------|
| theory_section | every objective |
| worked_example | calculation, design, or diagnosis objectives |
| figure_or_table | visual or comparative objectives |
| notebook | computational or NeqSim-backed objectives |
| exercise | every objective |
| assessment | course-release objectives |
| summary_takeaway | every chapter objective |

## Status Values

- `complete`: objective has teaching and assessment evidence.
- `partial`: objective has teaching evidence but weak practice.
- `missing-practice`: objective is explained but not exercised.
- `missing-assessment`: objective has no assessment item.
- `unverified`: objective cannot be mapped from available files.

## Output Schema

```json
{
  "chapter": "ch10_acid_gas_removal_dehydration",
  "objectives": [
    {
      "id": "ch10-lo3",
      "text": "Calculate TEG circulation sensitivity for dehydration duty.",
      "verb": "Calculate",
      "status": "partial",
      "assets": {
        "theory_section": ["10.2"],
        "notebook": ["notebooks/ch10_s02_teg_contactor.ipynb"],
        "exercise": []
      },
      "recommended_edit": "Add one exercise that varies TEG circulation and interprets water dew point."
    }
  ]
}
```

## Pass Criteria

- Every objective has a section and an exercise or assessment item.
- Computational objectives point to a notebook, script, or verified example.
- Objective verbs are observable and match chapter difficulty.
- Summary takeaways reflect the objectives rather than restating headings.

## Safety Rules

- Do not mark a heading as evidence unless it contains teaching content.
- Do not require a notebook for purely conceptual objectives.
- Do not invent exercise solutions or assessment keys.