---
name: paperlab_standards_clause_traceability
description: |
  Map standards-heavy PaperLab statements to standard, revision, clause or
  paraphrased requirement, and evidence strength. Use for professional
  engineering chapters with API, DNV, ISO, IEC, ASME, NORSOK, or regulations.
---

# PaperLab Standards Clause Traceability

## When to Use

USE WHEN: book text makes recommendations or compliance-like statements based
on industry standards or regulations.

Pair with:

- `neqsim_standard_requirement_extraction` for extracting standard requirements,
- `paperlab_scientific_traceability_audit` for evidence markers,
- `paperlab_equation_dimensional_audit` for standard-based formulas.

## Evidence Strength

| Strength | Meaning |
|----------|---------|
| clause | statement maps to a known clause or table |
| paraphrased-requirement | statement maps to a summarized requirement |
| broad-standard | standard is relevant but clause is not identified |
| secondary-source | statement relies on a textbook or guidance note |
| unsupported | no source found |

## Workflow

1. Extract standards names, revisions, codes, local regulations, and compliance verbs.
2. Classify each statement as method, limit, recommendation, context, or claim.
3. Map high-impact statements to evidence strength and source path.
4. Flag unsupported phrases such as "compliant with" when only general guidance exists.
5. Suggest precise, conservative wording.

## Output Schema

```json
{
  "statement": "Pipeline design follows DNV-ST-F101 wall-thickness checks.",
  "standard": "DNV-ST-F101",
  "revision": "study-specific",
  "evidence_strength": "broad-standard",
  "recommendation": "Avoid formal compliance wording unless clause-level evidence is added."
}
```

## Safety Rules

- Do not quote restricted standards text unless licensing permits it.
- Do not invent clause numbers.
- Distinguish educational examples from certified engineering deliverables.