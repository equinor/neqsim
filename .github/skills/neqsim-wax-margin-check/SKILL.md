---
name: neqsim-wax-margin-check
version: "0.1.0"
description: "Educational wax operating-margin screening placeholder with public assumptions. USE WHEN: a task needs a quick check of whether an operating point keeps a safe temperature margin above a wax appearance temperature and should be directed to validated NeqSim methods for real calculations."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Wax Margin Check

Use this skill for a quick, public check of whether a process or pipeline operating point keeps a temperature margin above a known wax appearance temperature (WAT). It is intentionally simple and should guide users toward validated NeqSim wax calculations for real work.

## When to Use

- When a user asks whether an operating temperature stays clear of the wax deposition region.
- When a validated NeqSim wax appearance temperature or a public lab WAT is already available.
- When an agent should explain that validated NeqSim methods are required for real wax prediction and deposition design.

## Inputs

- `operating_temperature`: operating temperature in C.
- `wax_appearance_temperature`: wax appearance temperature in C from a validated NeqSim calculation or public lab measurement.
- `min_margin`: configurable minimum acceptable margin in C (constructor, default 5.0).

## Outputs

- `wax_margin_c`: operating temperature minus the wax appearance temperature in C.
- `below_wax_appearance`: whether the operating point is at or below the wax appearance temperature.
- `margin_warning`: `ok`, `watch`, or `high`.
- `neqsim_available`: whether the optional NeqSim package is importable.
- `assumptions`: public assumptions and required follow-up.

## Engineering Method

The placeholder method computes the wax margin as `operating_temperature - wax_appearance_temperature`. A non-positive margin means the operating point is at or below the wax appearance temperature and is flagged `high`. A small positive margin below `min_margin` is flagged `watch`. A larger margin is flagged `ok`.

This is not a wax thermodynamic or deposition model. The wax appearance temperature must come from a validated NeqSim wax calculation or a public lab measurement with a defined fluid composition and operating envelope.

## Python Usage Pattern

```python
from wax_margin_check import WaxMarginModel

model = WaxMarginModel(min_margin=5.0)
result = model.evaluate(
    operating_temperature=33.0,
    wax_appearance_temperature=30.0,
)

print(result.wax_margin_c)
print(result.margin_warning)
print(result.assumptions)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim wax workflows. If not, the example still runs with fallback placeholder logic.

## Validation Checklist

- [ ] Operating and wax appearance temperatures are finite.
- [ ] The wax appearance temperature came from a validated NeqSim calculation or public lab measurement.
- [ ] Above-WAT and below-WAT cases are tested.
- [ ] The minimum margin is documented as a configurable public guideline only.
- [ ] Results are not used as a deposition rate, pigging interval, or operating limit.
- [ ] Real wax work is redirected to validated NeqSim methods and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Margin looks safe but wax still deposits | Wax appearance temperature was guessed | Use a validated NeqSim wax appearance temperature or public lab WAT |
| Margin warning never triggers | `min_margin` set too low | Use a service-appropriate operating margin and confirm with flow assurance review |
| Missing composition effect | Crude composition was not included in the NeqSim basis | Provide the composition to NeqSim and recompute the wax appearance temperature |

## Limitations

- No wax phase equilibrium or deposition calculation is performed in this skill.
- No composition, shear, ageing, pour-point, or transient effects are included.
- No proprietary wax models or company operating limits are included.
- Not suitable for flow assurance design, pigging strategy, or operating-limit decisions.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.thermo.system.SystemInterface#addWaxModel()` — solid wax-phase characterisation for the fluid.
- `neqsim.thermodynamicoperations.ThermodynamicOperations#calcWAT()` — rigorous wax appearance temperature.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public flow assurance literature on wax appearance temperature for background.
