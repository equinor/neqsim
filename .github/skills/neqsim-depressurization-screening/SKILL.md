---
name: neqsim-depressurization-screening
version: "0.1.0"
description: "Educational blowdown and depressurization screening with time-to-target and low-temperature flags. USE WHEN: a task needs a public, screening-level blowdown time indicator and auto-refrigeration low-temperature triage without proprietary blowdown design methods."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Depressurization Screening

Use this skill for public, educational emergency depressurization (blowdown) screening examples. It provides a simple time-to-target indicator and an auto-refrigeration low-temperature flag that help agents structure early blowdown questions before moving to a validated transient depressurization model and material low-temperature review.

## When to Use

- When a user asks for a quick, public blowdown time triage example.
- When an agent needs a placeholder estimate of cold-end temperature for a low-temperature flag.
- When examples must run without confidential vessel data, valve sizing, or company blowdown bases.

## Inputs

- `initial_pressure`: starting pressure in bara.
- `target_pressure`: target depressurization pressure in bara.
- `inventory`: vessel gas inventory in kg.
- `vent_mass_rate`: representative blowdown mass rate in kg/h.
- `relieving_temperature`: starting fluid temperature in C.
- `mdmt`: minimum design metal temperature in C, default -20.

## Outputs

- `blowdown_time_indicator`: dimensionless ratio of estimated blowdown time to a configurable public target time.
- `estimated_blowdown_time_min`: estimated time to reach the target pressure, in minutes.
- `estimated_min_temperature_C`: simplified isentropic cold-end temperature estimate.
- `low_temperature_flag`: `True` when the cold-end estimate is below the MDMT.
- `depressurization_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `DepressurizationModel` uses open placeholder calculations only:

- the vented mass to reach the target pressure is scaled from inventory using a linear pressure-fraction placeholder.
- blowdown time is the vented mass divided by the representative blowdown rate.
- the cold-end temperature uses a simplified isentropic expansion relation with a public isentropic exponent.
- warnings combine a too-slow blowdown indicator with the low-temperature flag.

This is educational and screening-only logic. It is not a transient depressurization standard, a valve sizing method, a vendor method, or a replacement for a validated NeqSim blowdown model and materials review.

## Python Usage Pattern

```python
from depressurization_screening import DepressurizationModel

model = DepressurizationModel()
result = model.evaluate(
    initial_pressure=100.0,
    target_pressure=7.0,
    inventory=2000.0,
    vent_mass_rate=12000.0,
    relieving_temperature=30.0,
    mdmt=-46.0,
)

print(result.depressurization_warning)
print(result.estimated_blowdown_time_min)
print(result.estimated_min_temperature_C)
print(result.low_temperature_flag)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to a validated NeqSim transient depressurization model for cold-end temperature and time profiles. If it is not installed, the example still runs with public placeholder logic.

## Validation Checklist

- [ ] Pressures are positive and the target pressure is below the initial pressure.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover normal, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real blowdown design is redirected to validated NeqSim transient models, API 521, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Treating `estimated_min_temperature_C` as a design cold-end | Placeholder isentropic relation ignores heat input and real fluid behavior | Use a validated NeqSim transient depressurization model for design |
| Optimistic blowdown time | Representative vent rate held constant instead of decaying with pressure | Use transient simulation with valve characteristics |
| Missing low-temperature risk | MDMT not provided at the correct case | Confirm MDMT against the controlling depressurization case |

## Limitations

- No proprietary valve data, vessel geometry, or project blowdown bases are included.
- No transient pressure or temperature profile, heat input, or two-phase behavior is modeled.
- No reaction force, flare back-pressure, or downstream network effects are included.
- Not suitable for safety-critical, design, guarantee, or standards-compliance work.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.process.safety.depressurization.DepressurizationSimulator` — transient blowdown inventory and time-to-target-pressure.
- `neqsim.process.equipment.valve.SafetyValve` — relief/blowdown valve model inside a process flowsheet.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public depressurization references such as API Standard 521 for general blowdown concepts.
