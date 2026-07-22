---
name: paperlab_chapter_flow_editor
description: |
  Edit PaperLab book chapters for narrative flow, chapter arc, section order,
  transitions, recurring case threads, and controlled repetition. Use when a
  chapter is technically correct but still feels like assembled lecture notes.
---

# PaperLab Chapter Flow Editor

## When to Use

USE WHEN: improving chapter structure, transitions, case continuity, or the
overall reading path of a PaperLab book. This skill is useful after source
coverage, figure generation, and notebook execution have already produced enough
material, but before final typesetting.

DO NOT USE for: rewriting scientific claims without validation, changing
numerical results, or deleting pedagogical repetition without review.

## Chapter Arc

Each chapter should have a visible arc:

1. **Opening contract**: what engineering decision or student capability the
   chapter supports.
2. **Concept foundation**: the minimum theory, terminology, and physical logic.
3. **Engineering method**: equations, workflow, or design procedure.
4. **Computational or case example**: a worked use of the method.
5. **Decision consequence**: what the result changes for concept selection,
   operation, risk, or economics.
6. **Transfer forward**: how the output is reused in later chapters.

## Flow Checks

Review the chapter for:

- abrupt section starts that need a one-sentence bridge,
- equations introduced before variables or intuition,
- figures placed before the concept needed to read them,
- repeated background that should become a cross-reference,
- case-study material that conflicts with the canonical case thread,
- summaries that repeat headings instead of synthesizing decisions,
- generated source or lecture-checkpoint texture in the main reading path.

## Case Thread Pattern

When a recurring case is used across chapters, keep a small handoff pattern:

```markdown
**Case handoff.** This chapter uses <input> from Chapter N, calculates <result>,
and carries <output> forward to Chapter M where it becomes <later use>.
```

Use this sparingly. One handoff per case-heavy chapter is usually enough.

## Repetition Policy

Some repetition is useful for students; uncontrolled repetition is not. Keep:

- definitions that prevent a chapter from becoming inaccessible,
- summaries in review and exam-preparation chapters,
- short reminders before major calculations.

Compress or move:

- duplicate background paragraphs,
- repeated slide-topic inventories,
- repeated captions or figure explanations,
- repeated "NeqSim implementation" boilerplate that does not add chapter-specific value.

## Output Pattern

```markdown
## Chapter Flow Review: <chapter>

- Status: ready | minor-revision | major-revision
- Chapter arc: <one-sentence assessment>
- Strongest transition: <section or paragraph>
- Weakest transition: <section or paragraph>
- Repetition to keep: <why it helps students>
- Repetition to compress: <what to replace with cross-reference>
- Case-thread action: <none or specific handoff edit>
```

## Editing Rules

- Preserve citations, figure references, equation labels, and NeqSim traceability markers.
- Do not move figures away from their discussion blocks.
- When removing repeated text, keep one canonical explanation and replace the
  secondary copy with a short cross-reference.
- Prefer one clean bridge sentence over a long transitional paragraph.
