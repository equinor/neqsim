---
name: paperlab_instructor_resource_pack
description: |
  Build instructor resources from PaperLab books: lecture outlines, slide plans,
  question banks, solution manuals, rubrics, labs, and exam variants.
---

# PaperLab Instructor Resource Pack

## When to Use

USE WHEN: preparing a PaperLab textbook for teaching, course adoption, workshops,
or training delivery.

## Resource Types

- lecture outlines,
- slide plans,
- question banks,
- lab assignments,
- solution manual sections,
- grading rubrics,
- exam variants,
- project briefs.

## Question Bank Schema

```json
{
  "questions": [
    {
      "id": "ch04_q03",
      "chapter": "ch04",
      "learning_objective": "LO-4.2",
      "difficulty": "calculation",
      "prompt": "...",
      "solution_outline": "instructor-only",
      "rubric": ["units", "method", "interpretation"]
    }
  ]
}
```

## Pass Criteria

- Resources map to learning objectives.
- Labs have runnable notebooks or clear manual alternatives.
- Rubrics evaluate interpretation as well as calculation.

## Safety Rules

- Keep instructor-only answers out of public student exports.
- Do not include confidential industrial case details.
- Do not create exam questions that depend on unavailable software or data.