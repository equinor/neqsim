---
name: neqsim-safety-function-coverage-screening
version: "0.1.0"
description: "Educational process safety-function coverage screening using the public API RP 14C / ISO 10418 SAFE-chart concept. USE WHEN: a task needs a public, screening-level check of whether a process component has the typically required protective functions (PSH, PSL, PSV, LSH, LSL, etc.) before a formal SAFE-chart and safety analysis review."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Safety Function Coverage Screening

Use this skill for public, educational safety-function coverage screening. It checks whether a process component declares the protective functions that API RP 14C and ISO 10418 typically associate with that component type, reports the missing functions, and gives a coverage ratio so an agent can flag gaps before a formal Safety Analysis Function Evaluation (SAFE) chart review.

## When to Use

- When a user asks whether a vessel or component has the usual API RP 14C protective functions.
- When an agent needs a quick protective-function gap check to scope a safety review.
- When examples must run without confidential SAFE charts, cause-and-effect matrices, or company safety specs.

## Inputs

- `component_type`: one of `pressure_vessel`, `separator`, `gas_pipeline_segment`, `liquid_pipeline_segment`, `fired_heater`, `compressor`, `pump`, or `wellhead`.
- `provided_functions`: a collection of declared protective-function codes (for example `PSH`, `PSL`, `PSV`, `LSH`, `LSL`, `TSH`, `FSV`, `BSDV`).

## Outputs

- `component_type`: the normalized component type.
- `required_functions`: the typically required protective-function codes for that type.
- `provided_functions`: the normalized declared functions.
- `missing_functions`: required functions that were not declared.
- `coverage_ratio`: fraction of required functions that are declared.
- `coverage_warning`: `ok`, `watch`, or `gap`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `SafetyFunctionCoverageModel` uses the open API RP 14C / ISO 10418 concept only:

- each component type maps to a public set of typically required protective functions.
- the provided functions are compared to the required set to find missing functions.
- the coverage ratio is the count of provided required functions divided by the required count.
- the warning is a simple rule-based label that flags gaps for a formal review.

This is educational and screening-only logic. The required-function sets are simplified public defaults, not a project SAFE chart. It does not perform an undesirable-event analysis, evaluate detectable abnormal conditions, credit alternate protection, or assess independence and reliability. It is not a replacement for a formal API RP 14C / ISO 10418 SAFE-chart analysis and a qualified safety review.

## Python Usage Pattern

```python
from safety_function_coverage_screening import SafetyFunctionCoverageModel

model = SafetyFunctionCoverageModel()
result = model.evaluate(
    component_type="separator",
    provided_functions=["PSH", "PSL", "PSV", "LSH"],
)

print(result.coverage_warning)
print(result.missing_functions)
print(result.coverage_ratio)
```

## Related NeqSim Functionality

This screening logic is also implemented as a validated NeqSim Java class so it can run inside a process model:

- `neqsim.process.safety.SafetyAnalysisFunctionEvaluation` — API RP 14C / ISO 10418 SAFE-chart coverage evaluation: given a component type and provided protective functions, returns required vs provided functions, the missing list, and a coverage ratio.
- `neqsim.process.safety.barrier.BarrierRegister` and `SafetyCriticalElement` — barrier management for the credited protective functions.

This skill is a public triage layer aligned with the NeqSim `SafetyAnalysisFunctionEvaluation` class.

## Validation Checklist

- [ ] Component type is one of the supported public types.
- [ ] Provided functions use recognized protective-function codes.
- [ ] Tests cover full coverage, a gap case, and invalid input.
- [ ] Results are described as educational screening indicators.
- [ ] Real analysis is redirected to a formal SAFE chart, API RP 14C, ISO 10418, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Coverage looks complete but is not | Alternate or higher-level protection assumed | Use a formal SAFE chart to credit alternate protection |
| Wrong required set | Component type misclassified | Use the correct component type for the equipment |
| Missing function disputed | Local code or company practice differs | Defer to the applicable code and project safety basis |

## Limitations

- No proprietary SAFE charts, cause-and-effect matrices, or company safety specs are included.
- No undesirable-event analysis, independence, or reliability assessment is performed.
- The required-function sets are simplified public defaults for screening only.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
