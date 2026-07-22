---
name: paperlab_derivation_symbolic_checking
description: |
  Check PaperLab mathematical derivations for assumptions, dimensional
  consistency, limiting cases, notation, and NeqSim implementation linkage.
---

# PaperLab Derivation Symbolic Checking

## When to Use

USE WHEN: a paper or book includes a new method, equation derivation, algorithm
description, Jacobian, residual formulation, or thermodynamic identity.

## Audit Dimensions

| Dimension | Questions |
|-----------|-----------|
| Assumptions | Are phase, EOS, ideality, and differentiability assumptions explicit? |
| Units | Are both sides dimensionally consistent? |
| Symbols | Are symbols defined once and reused consistently? |
| Limiting cases | Does the equation reduce correctly for simple or ideal cases? |
| Code linkage | Is there a Java method, notebook, or test implementing the equation? |

## Output Schema

```json
{
  "equations": [
    {
      "label": "residual_jacobian",
      "status": "needs_fix",
      "issues": ["symbol J reused for flux and Jacobian"],
      "limiting_cases_checked": ["ideal gas", "zero association"],
      "implementation_link": "neqsim.package.Class#method"
    }
  ]
}
```

## Pass Criteria

- Each derivation states assumptions.
- Units and symbols are consistent.
- At least one limiting case or reference comparison is described.
- Implementation or test linkage exists for computational methods.

## Safety Rules

- Do not present dimensional consistency as mathematical proof.
- If symbolic algebra cannot be run, state that the review is manual.
- Keep corrections minimal and tied to the manuscript text.