---
name: paperlab_curriculum_prerequisite_graph
description: |
  Build prerequisite graphs for PaperLab books. Use when checking chapter order,
  concept introduction, learning path coherence, glossary coverage, and whether
  students meet concepts before they are used or assessed.
---

# PaperLab Curriculum Prerequisite Graph

## When to Use

USE WHEN: reviewing a whole technical book for curriculum flow, especially when
chapters are written by multiple passes or imported from course material.

Pair with:

- `paperlab_student_readability` for chapter-level learner friction,
- `paperlab_book_knowledge_graph` for graph output and visualization,
- `paperlab_learning_objective_matrix` for objective-level coverage.

## Concept States

Track each concept in these states:

| State | Meaning |
|-------|---------|
| introduced | concept is explained for the first time |
| practiced | concept appears in worked examples or exercises |
| reinforced | concept is reused with reminder context |
| assessed | concept appears in exercises, exams, or projects |
| assumed | concept is used without explanation |

## Workflow

1. Extract concepts from headings, objectives, summaries, glossary entries,
   equations, figure captions, and exercises.
2. Normalize synonyms and acronyms.
3. Identify the first introduction, first use, first computation, and first
   assessment for each concept.
4. Create prerequisite edges from prerequisite concept to dependent concept.
5. Flag:
   - use before introduction,
   - assessment before worked example,
   - missing bridge paragraph,
   - duplicate first explanations,
   - glossary terms not used in chapters.

## Output Schema

```json
{
  "book": "book-slug",
  "concepts": [
    {
      "id": "hydrate_margin",
      "label": "hydrate margin",
      "introduced_in": "ch10#section",
      "first_used_in": "ch08#section",
      "state": "assumed",
      "issue": "used-before-introduction"
    }
  ],
  "edges": [
    {"from": "phase_envelope", "to": "hydrate_margin", "type": "prerequisite"}
  ]
}
```

## Pass Criteria

- Core concepts are introduced before calculation or assessment.
- Glossary and acronym entries match chapter usage.
- Capstone cases reference earlier theory instead of re-teaching it at length.
- Bridge paragraphs or cross-references exist for necessary jumps.

## Safety Rules

- Do not force a strict linear path when the book intentionally supports lookup.
- Do not flag every acronym as a concept; prioritize teaching-critical terms.
- Preserve confidential case anonymization in graph labels.