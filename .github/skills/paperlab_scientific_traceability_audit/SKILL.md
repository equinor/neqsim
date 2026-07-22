---
name: paperlab_scientific_traceability_audit
description: |
  Audit PaperLab books for claim, figure, equation, notebook, citation, and
  unit traceability. Use when a book must become scientifically defensible and
  reproducible, especially for NeqSim-backed quantitative chapters.
---

# PaperLab Scientific Traceability Audit

## When to Use

USE WHEN: a textbook chapter contains numbers, equations, figures, tables, or
engineering recommendations that depend on calculations, simulations, standards,
or source documents. This skill turns technical content into auditable content.

Pair with:

- `neqsim_in_writing` for NeqSim claim markers,
- `neqsim-notebook-patterns` for executable notebooks,
- `neqsim-regression-baselines` for stable examples,
- `neqsim_standard_requirement_extraction` for clause-level standards evidence.

## Traceability Objects

Track six object types:

| Object | Required Evidence |
|--------|-------------------|
| Numeric claim | notebook cell, test, baseline, source table, or citation |
| Figure | source file, notebook, generated script, or approved source document |
| Equation | symbol definitions, units, source citation, and implementation link if used by NeqSim |
| Table | data source, units, and update path |
| Standard statement | standard code, revision, clause or paraphrased requirement |
| Recommendation | preceding evidence that supports the action |

## Marker Rules

Use existing `neqsim_in_writing` markers for high-value NeqSim-backed content:

```markdown
<!-- @neqsim:claim
  test: src/test/java/neqsim/book/<book_slug>/<TestName>.java
  baseline: src/test/resources/baselines/book/<book_slug>/<baseline>.json
-->
```

```markdown
<!-- @neqsim:figure source=notebooks/<notebook>.ipynb cell=<cell-id-or-index> -->
```

```markdown
<!-- @neqsim:eq method=neqsim.package.ClassName#methodName -->
```

Do not mark every sentence. Prioritize values and equations that students will
reuse, cite, or compare in exercises.

## Audit Workflow

1. Run `book-skill-stack-plan` and inspect traceability dimensions.
2. Run `book-evidence-check` and `book-figure-dossier`.
3. For each core chapter, identify the top 3 to 5 numerical or equation-based
   claims that deserve markers.
4. Confirm every cited notebook exists and has executed output or generated figures.
5. Confirm units in prose, tables, and figures match `nomenclature.yaml`.
6. For standards-heavy chapters, add clause-level or paraphrased evidence rather
   than broad standard names.

## Pass Criteria

A chapter is traceability-ready when:

- every main figure has source provenance and a discussion block,
- the most important numerical claims have markers or explicit citations,
- all equations define symbols and cite origin or implementation,
- SI units are present in tables and figure axes,
- worked examples have notebooks or tests that can be rerun,
- recommendations are supported by nearby evidence.

## Output Pattern

```markdown
## Scientific Traceability Audit: <chapter>

- Status: ready | needs-markers | needs-validation | blocked
- High-value claims needing markers:
  - <claim and location>
- Figures needing stronger provenance:
  - <figure>
- Equations needing source or implementation link:
  - <equation>
- Unit inconsistencies:
  - <quantity>
- Recommended next edit: <one concrete action>
```

## Safety Rules

- Never invent a notebook, test, citation, or standards clause.
- If evidence is missing, mark the claim as unverified or lower confidence.
- Do not claim regulatory compliance from a candidate standards map alone.
- Keep private source filenames out of public outputs unless approved.
