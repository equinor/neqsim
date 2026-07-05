---
name: neqsim-resource-classification-screening
version: "0.1.0"
description: "Educational petroleum resource classification screening using the public SPE-PRMS framework and the open Norwegian Petroleum Directorate resource-class scheme. USE WHEN: a task needs a public, screening-level mapping of a project maturity stage to a reserves, contingent-resources, or prospective-resources category before a formal resource estimate."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Resource Classification Screening

Use this skill for public, educational petroleum resource classification screening. It maps a project maturity stage to a high-level resource category using the open SPE-PRMS framework and the public Norwegian Petroleum Directorate resource-class scheme so an agent can position a project before a formal resource estimate.

## When to Use

- When a user asks which resource category a project sits in.
- When an agent needs to label a project as reserves, contingent, or prospective at a high level.
- When examples must run without confidential volumes, reservoir data, or company estimates.

## Inputs

- `maturity_stage`: a project maturity descriptor, for example `on production`, `approved for development`, `justified for development`, `development pending`, `development on hold`, `development unclarified`, `prospect`, `lead`, or `play`.
- `commercial`: optional flag indicating whether the project has been judged commercial.

## Outputs

- `resource_class`: a normalized maturity-stage label.
- `resource_category`: `reserves`, `contingent-resources`, `prospective-resources`, or `unrecoverable`.
- `prms_class_range`: the public SPE-PRMS class range that matches the category.
- `maturity_warning`: `ok`, `watch`, or `unclassified`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `ResourceClassificationModel` uses the open SPE-PRMS framework only:

- discovered, commercial, recoverable volumes map to reserves (PRMS classes on production, approved, justified).
- discovered, sub-commercial recoverable volumes map to contingent resources (development pending, on hold, unclarified).
- undiscovered recoverable volumes map to prospective resources (prospect, lead, play).
- stages that cannot be matched return `unclassified`.

This is educational and screening-only logic. It is a rule-based mapping of maturity descriptors, not a volumetric estimate. It does not compute in-place or recoverable volumes, recovery factors, or uncertainty ranges, and it does not apply any company-specific maturity gate. It is not a replacement for a formal resource estimate under SPE-PRMS or the Norwegian Petroleum Directorate scheme and a qualified subsurface review.

## Python Usage Pattern

```python
from resource_classification_screening import ResourceClassificationModel

model = ResourceClassificationModel()
result = model.evaluate(
    maturity_stage="justified for development",
)

print(result.resource_category)
print(result.prms_class_range)
print(result.maturity_warning)
```

## Related NeqSim Functionality

For volumetric resource estimation that feeds a formal classification, redirect to NeqSim field-development functionality:

- `neqsim.process.fielddevelopment.ReservesClassification` — public SPE-PRMS maturity-to-category mapping in Java, mirroring this skill.
- `neqsim.process.util.fielddevelopment` production-profile and recovery utilities — recoverable-volume estimation that supports a classification.
- field-development economics utilities — commercial screening that distinguishes reserves from contingent resources.

This skill is a public maturity-mapping triage layer that decides when to invoke those validated estimation utilities.

## Validation Checklist

- [ ] The maturity stage is a public descriptor, not a confidential project name.
- [ ] No confidential volumes or reservoir data are included.
- [ ] Tests cover a reserves case, a contingent case, a prospective case, and an unclassified case.
- [ ] Results are described as educational screening indicators.
- [ ] Formal classification is redirected to SPE-PRMS, the NPD scheme, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Reserves labelled too early | Commercial maturity assumed | Confirm a final investment decision before reserves |
| Contingent treated as reserves | Sub-commercial volumes counted as reserves | Keep sub-commercial discovered volumes as contingent |
| Prospective over-counted | Undiscovered volumes added to reserves | Keep undiscovered volumes as prospective only |

## Limitations

- No confidential volumes, reservoir data, or company estimates are included.
- No volumetric, recovery-factor, or uncertainty calculation is performed.
- No company-specific maturity gate is applied.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
