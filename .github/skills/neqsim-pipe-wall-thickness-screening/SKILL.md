---
name: neqsim-pipe-wall-thickness-screening
version: "0.1.0"
description: "Educational process-pipe wall-thickness screening using the public ASME B31.3 hoop-stress (Barlow style) equation. USE WHEN: a task needs a public, screening-level minimum wall-thickness estimate and a check against a nominal schedule wall before detailed piping or pipeline mechanical design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Pipe Wall Thickness Screening

Use this skill for public, educational pressure-piping wall-thickness screening. It estimates the minimum pressure-design wall thickness using the open ASME B31.3 internal-pressure equation, adds a corrosion allowance, and compares the result to a nominal wall so an agent can flag piping that may be under-walled before detailed mechanical design.

## When to Use

- When a user asks whether a nominal pipe wall is adequate for a design pressure.
- When an agent needs a quick wall-thickness triage to scope a piping or pipeline study.
- When examples must run without confidential piping classes, project material certificates, or company piping specs.

## Inputs

- `design_pressure`: internal design pressure in bar gauge.
- `outer_diameter`: pipe outer diameter in mm.
- `allowable_stress`: allowable stress `S` of the material in MPa at design temperature.
- `weld_joint_factor`: longitudinal weld joint quality factor `E`, default 1.0 (seamless).
- `coefficient_y`: temperature coefficient `Y`, default 0.4 for ferritic steel below 482 C.
- `corrosion_allowance`: corrosion and mechanical allowance in mm, default 3.0.
- `nominal_thickness`: nominal wall thickness in mm for the comparison.

## Outputs

- `pressure_design_thickness_mm`: minimum pressure-design thickness `t`.
- `required_thickness_mm`: `t` plus the corrosion allowance.
- `nominal_thickness_mm`: the supplied nominal wall.
- `thickness_margin_ratio`: ratio of nominal to required thickness.
- `wall_thickness_warning`: `ok`, `watch`, or `inadequate`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `PipeWallThicknessModel` uses the open ASME B31.3 internal-pressure equation only:

- the pressure-design thickness uses `t = P D / (2 (S E W + P Y))`, where `W` is taken as 1.0 in this screening form.
- the required thickness adds a configurable corrosion and mechanical allowance.
- the margin ratio compares the supplied nominal wall to the required thickness.
- the warning is a simple rule-based label aligned with ASME B31.3 and ISO 13703 pressure-design intent.

This is educational and screening-only logic. It does not include mill tolerance, bend thinning, external pressure or collapse, longitudinal or combined stresses, supports, or fatigue. It is not a piping code calculation, a vendor method, or a replacement for validated mechanical design and a qualified piping engineering review.

## Python Usage Pattern

```python
from pipe_wall_thickness_screening import PipeWallThicknessModel

model = PipeWallThicknessModel()
result = model.evaluate(
    design_pressure=100.0,
    outer_diameter=323.9,
    allowable_stress=138.0,
    nominal_thickness=12.7,
)

print(result.wall_thickness_warning)
print(result.required_thickness_mm)
print(result.thickness_margin_ratio)
```

## Related NeqSim Functionality

For validated wall-thickness and pipeline mechanical design, redirect to existing NeqSim classes:

- `neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign` — pipeline wall thickness and mechanical design (DNV-OS-F101 style).
- `neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign` — vessel wall thickness for separators (ASME VIII Div.1 style).
- `neqsim.process.mechanicaldesign.MechanicalDesign` — base mechanical-design framework with material data sources.

This skill is a public ASME B31.3 triage layer that decides when to invoke those validated design classes.

## Validation Checklist

- [ ] Pressure, diameter, and allowable stress are positive and in the stated units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover adequate, watch, inadequate, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real design is redirected to validated methods, ASME B31.3, ISO 13703, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Thickness looks too thin | Allowable stress taken at ambient not design temperature | Use the code allowable stress at design temperature |
| Margin always passes | Corrosion allowance set to zero | Use a service-appropriate corrosion and mechanical allowance |
| Wrong `Y` coefficient | Using ferritic `Y` at high temperature | Use the ASME B31.3 `Y` table for the material and temperature |

## Limitations

- No proprietary piping classes, material certificates, or company piping specs are included.
- No mill tolerance, bend thinning, external pressure, or combined-stress checks are performed.
- No branch reinforcement, flange rating, or fatigue assessment is included.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
