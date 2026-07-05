---
name: neqsim-line-velocity-check
version: "0.1.0"
description: "Educational process line velocity screening against erosional velocity and recommended velocity guidelines. USE WHEN: a task needs a public, screening-level check that a pipe or line operates within erosional and recommended velocity limits before detailed line sizing."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Line Velocity Check

Use this skill for public, educational process line velocity screening. It compares an operating fluid velocity against a public erosional velocity correlation and a recommended velocity guideline so an agent can flag lines that may exceed standard operating limits before moving to validated line sizing.

## When to Use

- When a user asks whether a line or pipe operates within velocity guidelines.
- When an agent needs a quick erosional-velocity triage to scope a piping or debottlenecking study.
- When examples must run without confidential piping classes, project line lists, or company piping specs.

## Inputs

- `fluid_velocity`: actual flowing velocity in the line in m/s.
- `mixture_density`: flowing mixture density in kg/m3.
- `c_factor`: dimensionless erosional velocity constant in SI form, default 122.0 (continuous service).
- `max_velocity_guideline`: recommended maximum velocity in m/s, default 20.0.

## Outputs

- `erosional_velocity_m_per_s`: screening erosional velocity from a public API RP 14E style correlation.
- `velocity_ratio`: ratio of fluid velocity to erosional velocity.
- `guideline_ratio`: ratio of fluid velocity to the recommended velocity guideline.
- `operating_indicator`: the larger of the two ratios, where higher means closer to a limit.
- `velocity_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `LineVelocityModel` uses open, published correlations only:

- erosional velocity uses a public API RP 14E metric form `Ve = C / sqrt(rho)` with the SI constant default of 122 corresponding to a continuous-service C of 100.
- the velocity ratio compares the operating velocity to this erosional velocity.
- the guideline ratio compares the operating velocity to a configurable recommended velocity guideline aligned with NORSOK P-001 style upper velocity limits.
- the operating indicator is the more limiting of the two ratios and drives rule-based warnings.

This is educational and screening-only logic. It is not a piping standard, a line sizing method, a vendor method, or a replacement for validated piping design and a qualified process engineering review.

## Python Usage Pattern

```python
from line_velocity_check import LineVelocityModel

model = LineVelocityModel()
result = model.evaluate(
    fluid_velocity=12.0,
    mixture_density=50.0,
    c_factor=122.0,
    max_velocity_guideline=20.0,
)

print(result.velocity_warning)
print(result.erosional_velocity_m_per_s)
print(result.operating_indicator)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim property calculations for mixture density and velocity. If it is not installed, the example still runs with public placeholder logic.

## Validation Checklist

- [ ] Inputs are positive and densities and velocities are in SI units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover normal, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real line sizing is redirected to validated methods, API RP 14E, NORSOK P-001, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Erosional velocity looks wrong | Using an imperial C-factor with SI density | Use the SI form `Ve = C / sqrt(rho)` with the SI constant near 122 |
| Velocity ratio too low | Density taken at the wrong conditions | Evaluate flowing density at line pressure and temperature |
| Guideline never triggers | Recommended velocity guideline set too high | Use a service-appropriate guideline per NORSOK P-001 style limits |

## Limitations

- No proprietary piping classes, project line lists, or company piping specs are included.
- No sand, corrosion, or service-specific C-factor selection is performed.
- No transient, slug, or acoustic velocity checks are included.
- Not suitable for safety-critical, design, guarantee, or standards-compliance work.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.process.equipment.pipeline.PipeBeggsAndBrills` — rigorous multiphase pressure drop and velocity profile.
- `neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe` — two-phase pipe hydraulics for line-velocity evaluation.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public piping engineering references such as API RP 14E and NORSOK P-001 for general velocity concepts.
