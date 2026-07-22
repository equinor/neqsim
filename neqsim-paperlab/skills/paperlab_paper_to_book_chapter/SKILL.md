---
name: paperlab_paper_to_book_chapter
description: |
  Transform validated PaperLab papers into teachable book chapters, sections,
  worked examples, exercises, notebooks, and figure discussions.
---

# PaperLab Paper to Book Chapter

## When to Use

USE WHEN: reusing a finished paper inside a textbook, monograph, course book, or
professional handbook.

## Transformation Pattern

| Paper element | Book adaptation |
|---------------|-----------------|
| Abstract | chapter motivation and learning path |
| Methods | theory section plus implementation notes |
| Results | worked examples, figures, and exercises |
| Discussion | engineering interpretation and limitations |
| Supplementary data | notebooks and labs |

## Workflow

1. Extract validated claims, assumptions, figures, and citations.
2. Add prerequisite context and learning objectives.
3. Expand methods into stepwise explanations and worked examples.
4. Turn sensitivity or benchmark results into exercises.
5. Preserve provenance with a paper-to-chapter mapping.

## Pass Criteria

- The chapter teaches rather than merely reprints the paper.
- Paper claims remain traceable to results or citations.
- Figures have observation, mechanism, implication, and recommendation discussion.
- Exercises support learning objectives.

## Safety Rules

- Do not remove limitations or assumptions from paper results.
- Do not duplicate text in a way that creates self-plagiarism risk for submission contexts.
- Keep notebook outputs synchronized with chapter numbers and figure names.