---
name: paperlab_exercise_difficulty_ramp
description: |
  Review and design PaperLab exercise sets with intentional progression from
  conceptual checks to calculation, design, uncertainty, and capstone tasks.
---

# PaperLab Exercise Difficulty Ramp

## When to Use

USE WHEN: preparing a book for teaching, revising exercises, or checking that
learning objectives are assessed with an appropriate difficulty progression.

Pair with:

- `paperlab_learning_objective_matrix` for objective coverage,
- `paperlab_exam_alignment` for assessment fit,
- `paperlab_student_readability` for student-facing wording.

## Exercise Types

| Type | Student Action |
|------|----------------|
| concept-check | explain, define, compare |
| calculation | compute a value with stated inputs |
| interpretation | explain what a result means physically |
| sensitivity | vary one or more inputs and compare outputs |
| design-decision | choose between alternatives using constraints |
| uncertainty-risk | use P10/P50/P90, risk matrix, or scenario range |
| capstone | integrate multiple chapters or recurring cases |

## Difficulty Levels

- `L1`: recall or identify.
- `L2`: explain or classify.
- `L3`: calculate with provided method.
- `L4`: diagnose, compare, or justify.
- `L5`: design, optimize, or evaluate uncertainty.

## Pass Criteria

- Each chapter has at least one concept-check exercise.
- Quantitative chapters have at least one calculation or notebook-linked task.
- Design chapters include at least one decision exercise.
- Late-book chapters include integration or capstone tasks.
- Objectives with verbs such as calculate, design, evaluate, or diagnose are
  assessed by matching exercise types.

## Output Schema

```json
{
  "chapter": "ch23_co2_field_development",
  "exercise_mix": {"concept-check": 2, "calculation": 1, "design-decision": 2},
  "missing_types": ["uncertainty-risk"],
  "recommended_exercise": "Estimate dense-phase transport margin under low-temperature uncertainty."
}
```

## Safety Rules

- Do not require private data for public exercises.
- Keep solutions and grading rubrics separate from student-facing chapters.
- Match difficulty to prerequisite coverage.