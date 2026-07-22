---
name: paperlab_student_readability
description: |
  Review and revise PaperLab book chapters so students can learn efficiently.
  Use for learning-objective alignment, prerequisite checks, worked examples,
  summaries, exercises, glossary consistency, and chapter-level pedagogy.
---

# PaperLab Student Readability

## When to Use

USE WHEN: improving a PaperLab textbook or course book for student learning,
especially after the scientific content is broadly complete. This skill checks
whether a chapter is easy to enter, easy to follow, and useful for exam and
exercise preparation.

DO NOT USE for: journal-paper conciseness, renderer debugging, or technical
validation of NeqSim calculations. Pair with `paperlab_scientific_traceability_audit`
for scientific claims and `paperlab_book_typesetting_release` for final formats.

## Reader Model

Assume the primary reader is a student who:

- knows basic petroleum engineering terminology but not every industrial acronym,
- learns best from worked examples and recurring case threads,
- needs to connect equations, figures, and decisions,
- will use the book for exercises, exams, and project work,
- may be reading in English as a second language.

## Chapter Checklist

For each chapter, check:

1. **Learning objectives**: 3 to 7 objectives, written as observable actions.
   Prefer verbs such as calculate, compare, explain, diagnose, design, and
   evaluate. Avoid vague objectives such as "understand processing".
2. **Prerequisites**: identify any concepts that must already be known. If a
   prerequisite is not covered earlier, add a short bridge paragraph or a cross-reference.
3. **Concept sequence**: introduce physical intuition before equations, and
   equations before computational examples.
4. **Worked examples**: include at least one compact worked example in core
   engineering chapters. The example should state inputs, method, result,
   interpretation, and common mistakes.
5. **Figures as teaching objects**: each non-cover figure should be followed by
   discussion using `figure-discussion`.
6. **Exercises**: include a mix of conceptual questions, calculation questions,
   and short design-decision questions.
7. **Summary**: close with decision-useful takeaways, not a table of contents in prose.
8. **Glossary and acronyms**: define important first-use terms and keep names
   consistent with the book glossary and acronym list.

## Readability Rubric

Grade each chapter as `ready`, `minor-revision`, or `major-revision`.

| Area | Ready Signal | Revision Signal |
|------|--------------|-----------------|
| Objectives | Objectives map to sections and exercises | Objectives are broad or missing |
| Flow | Reader can follow the chapter without lecture context | Chapter reads like stitched slides |
| Examples | Examples show inputs, method, result, meaning | Examples give numbers without interpretation |
| Language | Sentences are direct and jargon is introduced | Acronyms and compressed phrases dominate |
| Student Use | Chapter supports exercises and exam preparation | Chapter is mainly descriptive |

## Output Pattern

Write a short chapter review with:

```markdown
## Student Readability Review: <chapter>

- Status: ready | minor-revision | major-revision
- Best teaching asset: <figure/example/section>
- Main student friction: <one sentence>
- Required edits:
  - <edit 1>
  - <edit 2>
- Optional polish:
  - <edit 3>
```

## Common Fixes

- Replace long historical background with a short "why this matters" paragraph.
- Move dense slide coverage lists to an appendix or review artifact.
- Add a small numerical example after a new equation.
- Convert a generic summary into three to five engineering takeaways.
- Add a short "common mistake" note after unit-sensitive calculations.
