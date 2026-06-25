---
name: paperlab_concept_spiral_learning
description: |
  Audit how concepts recur across PaperLab books so they are introduced,
  practiced, deepened, assessed, and reused without shallow repetition.
---

# PaperLab Concept Spiral Learning

## When to Use

USE WHEN: improving a long textbook or monograph where concepts recur across
many chapters and should deepen over time.

## Spiral Stages

| Stage | Meaning |
|-------|---------|
| introduce | first clear explanation |
| practice | worked example or basic exercise |
| deepen | broader case, limitation, or uncertainty |
| apply | engineering decision or design workflow |
| assess | exercise, lab, exam, or capstone |

## Output Schema

```json
{
  "concepts": [
    {
      "concept": "hydrate margin",
      "introduce": "ch04",
      "practice": "ch06",
      "deepen": "ch10",
      "apply": "ch15",
      "assess": "ch16_exercises",
      "status": "complete"
    }
  ]
}
```

## Pass Criteria

- Major concepts progress beyond definition.
- Exercises arrive after worked examples.
- Capstones integrate earlier concepts with cross-references.

## Safety Rules

- Do not remove intentional recap sections blindly.
- Do not force every concept through every stage.
- Keep reference-style chapters usable for lookup.