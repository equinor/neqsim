---
name: paperlab_equation_dimensional_audit
description: |
  Audit equations, dimensions, symbols, notation, and formula variants in
  PaperLab books. Use when technical chapters contain equations, units, and
  engineering correlations that must remain consistent across chapters.
---

# PaperLab Equation Dimensional Audit

## When to Use

USE WHEN: reviewing equations, formula notation, unit consistency, or repeated
technical correlations in an engineering textbook.

Pair with:

- `paperlab_scientific_traceability_audit` for equation provenance,
- `neqsim_in_writing` for implementation links,
- `paperlab_standards_clause_traceability` for standards-based formulas.

## Audit Dimensions

| Dimension | Check |
|-----------|-------|
| symbol | defined near first use or in nomenclature |
| unit | stated and dimensionally plausible |
| source | citation, standard, derivation, or implementation link |
| assumption | valid range or simplification stated |
| duplicate | repeated formula uses same notation or explains differences |
| implementation | NeqSim method/test link when used computationally |

## Workflow

1. Extract display equations delimited by `$$...$$` and inline formulas using
   `$...$` when they contain engineering symbols.
2. Normalize symbols and compare against `nomenclature.yaml`.
3. Check units and dimensions where possible.
4. Detect conflicting definitions such as `q` for heat duty and volumetric flow
   in the same chapter without clarification.
5. Detect formula variants that need an assumptions note.
6. Recommend nomenclature additions and local text fixes.

## Output Pattern

```markdown
## Equation Audit: <chapter>

- Status: ready | minor-revision | major-revision | blocked
- Undefined symbols:
  - <symbol and equation context>
- Unit issues:
  - <quantity and suspected issue>
- Formula variants:
  - <formula family and recommended distinction>
- Recommended next edit: <one concrete action>
```

## Safety Rules

- Do not declare an equation wrong from notation alone.
- Treat approximate teaching equations as acceptable when assumptions are clear.
- Never invent standards clauses or citations for equations.