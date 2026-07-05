---
name: neqsim-water-dewpoint-dehydration-screening
version: "0.1.0"
description: "Educational gas water-content and dehydration screening using the public GPSA Bukacek saturated-water-content correlation. USE WHEN: a task needs a public, screening-level estimate of saturated water content in natural gas and a check against a sales-gas water spec before detailed dehydration design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Water Dewpoint Dehydration Screening

Use this skill for public, educational gas water-content screening. It estimates the saturated water content of natural gas using the open GPSA Bukacek correlation and compares it to a sales-gas water specification so an agent can flag whether dehydration is likely required before detailed design.

## When to Use

- When a user asks whether a gas stream needs dehydration to meet a water spec.
- When an agent needs a quick saturated-water-content estimate to scope a dehydration study.
- When examples must run without confidential dehydration designs, glycol data, or company gas specs.

## Inputs

- `pressure`: gas pressure in bar absolute.
- `temperature`: gas temperature in kelvin.
- `water_spec`: sales-gas water-content specification in lb/MMscf, default 7.0 (common pipeline spec).

## Outputs

- `saturated_water_content_lb_mmscf`: saturated water content from the Bukacek correlation.
- `water_spec_lb_mmscf`: the supplied water specification.
- `spec_ratio`: ratio of saturated water content to the spec.
- `dehydration_required`: whether the saturated content exceeds the spec.
- `dehydration_warning`: `ok`, `watch`, or `dehydration-required`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `WaterDewpointModel` uses the open GPSA Bukacek correlation only:

- the saturated water content uses `W = A / P + B`, where `A` and `B` are public temperature-dependent terms and `P` is in psia.
- the spec ratio compares the saturated content to the supplied water spec.
- the warning is a simple rule-based label aligned with sales-gas dehydration intent.

This is educational and screening-only logic. The Bukacek correlation is for sweet natural gas in equilibrium with liquid water and is most accurate above 32 F. It does not include acid-gas corrections, salinity, hydrate suppression, or glycol contactor performance. It is not a replacement for validated water-content and dehydration design (for example GPSA and rigorous thermodynamics) and a qualified process review.

## Python Usage Pattern

```python
from water_dewpoint_dehydration_screening import WaterDewpointModel

model = WaterDewpointModel()
result = model.evaluate(
    pressure=70.0,
    temperature=305.15,
)

print(result.dehydration_warning)
print(result.saturated_water_content_lb_mmscf)
print(result.dehydration_required)
```

## Related NeqSim Functionality

For validated water-content and dehydration calculations, redirect to existing NeqSim functionality:

- `neqsim.thermo.system.SystemSrkCPAstatoil` (CPA equation of state) — rigorous water content and water dew point for natural gas.
- `neqsim.process.design.template.DehydrationTemplate` — TEG dehydration process template.
- `neqsim.process.equipment.absorber` glycol contactor models — detailed dehydration performance.

This skill is a public Bukacek triage layer that decides when to invoke validated NeqSim water-content and dehydration models.

## Validation Checklist

- [ ] Pressure and temperature are positive and in the stated units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover an in-spec case, a dehydration-required case, and invalid input.
- [ ] Results are described as educational screening indicators.
- [ ] Real design is redirected to validated methods, GPSA, NeqSim CPA, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Water content too low | Acid-gas content ignored | Add CO2 and H2S corrections with a validated method |
| Spec check wrong | Volume basis mismatch (MMscf vs MMsm3) | Keep the water spec on the same basis as the correlation |
| Out-of-range result | Temperature below the correlation range | Use a validated method below freezing or near hydrate conditions |

## Limitations

- No proprietary dehydration designs, glycol data, or company gas specs are included.
- No acid-gas, salinity, or hydrate-suppression corrections are performed.
- No glycol contactor or regeneration performance is modeled.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
