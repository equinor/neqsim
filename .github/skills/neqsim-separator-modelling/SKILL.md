---
name: neqsim-separator-modelling
version: "0.1.0"
description: "Educational separator screening indicators for gas load, residence time, and capacity warnings. USE WHEN: a task needs public separator capacity screening without proprietary design methods."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Separator Modelling Screening

Use this skill for public, educational gas/liquid separator screening examples. It provides simple indicators that help agents structure early questions before moving to validated process simulation or detailed design.

## When to Use

- When a user asks for a simple separator capacity screening example.
- When an agent needs a public placeholder model for gas load and liquid residence time.
- When examples must run without confidential separator geometry, project data, or company design rules.

## Inputs

- `gas_flow`: gas volumetric flow rate in a consistent public unit, default examples use m3/h.
- `liquid_flow`: liquid volumetric flow rate in the same time basis, default examples use m3/h.
- `pressure`: operating pressure in bar.
- `temperature`: operating temperature in C.
- `gas_density`: gas density in kg/m3.
- `liquid_density`: liquid density in kg/m3.

## Outputs

- `gas_load_indicator`: dimensionless screening load where values above 1.0 indicate high gas load for the placeholder basis.
- `residence_time_indicator`: dimensionless screening residence indicator where values below 1.0 indicate low liquid residence time for the placeholder basis.
- `capacity_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `SeparatorModel` uses open placeholder calculations only:

- gas load indicator increases with gas flow and gas-density square root
- liquid residence time is estimated from a configurable public holdup volume and liquid flow
- warnings are rule-based thresholds on the two indicators

This is educational and screening-only logic. It is not a design standard, separator sizing method, vendor method, or replacement for a validated NeqSim process model.

## Python Usage Pattern

```python
from separator_modelling import SeparatorModel

model = SeparatorModel()
result = model.evaluate(
    gas_flow=18_000.0,
    liquid_flow=120.0,
    pressure=55.0,
    temperature=35.0,
    gas_density=18.0,
    liquid_density=720.0,
)

print(result.capacity_warning)
print(result.gas_load_indicator)
print(result.residence_time_indicator)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim calculations. If it is not installed, the example still runs with fallback placeholder logic.

## Validation Checklist

- [ ] Inputs are non-negative where applicable and densities are positive.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover normal, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real design work is redirected to validated NeqSim methods and qualified engineering review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Treating `gas_load_indicator` as a design K-factor | Placeholder model is not a sizing method | Use validated separator sizing methods and NeqSim process models for design |
| Unrealistic residence time | Liquid flow unit does not match the assumed basis | Keep flow and holdup units consistent |
| Silent bad output | Negative flow or density was passed | Validate inputs and use the provided class checks |

## Limitations

- No proprietary separator internals, demister rules, vendor data, or project standards are included.
- No phase equilibrium calculation is performed.
- No droplet settling, foam, carryover, control, or slugging model is included.
- Not suitable for safety-critical, design, guarantee, or standards-compliance work.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.process.equipment.separator.Separator` and `neqsim.process.equipment.separator.ThreePhaseSeparator` — rigorous gas/liquid separation with phase equilibrium.
- `Separator.setEntrainment(val, specType, specifiedStream, phaseFrom, phaseTo)` — models imperfect separation / carry-over by transferring a fraction of one phase into another outlet (specType `mole`/`mass`/`volume`; basis `feed` or `product`). The base `Separator` supports `oil->gas`, `aqueous->gas`, `gas->liquid`; `ThreePhaseSeparator` supports all six phase-to-phase paths.
- `neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign` — Souders-Brown gas-load factor, retention-time sizing, and demister/inlet-device internals.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public process engineering textbooks for general separator concepts.