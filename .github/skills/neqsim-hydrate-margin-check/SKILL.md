---
name: neqsim-hydrate-margin-check
version: "0.1.0"
description: "Educational hydrate operating-margin screening placeholder with public assumptions. USE WHEN: a task needs a quick check of whether an operating point keeps a safe temperature margin above a hydrate equilibrium temperature and should be directed to validated NeqSim methods for real calculations."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Hydrate Margin Check

Use this skill for a quick, public check of whether a process or pipeline operating point keeps a temperature margin above a known hydrate equilibrium temperature. It is intentionally simple and should guide users toward validated NeqSim hydrate calculations for real work.

## When to Use

- When a user asks whether an operating temperature stays clear of the hydrate region.
- When a validated NeqSim hydrate equilibrium temperature is already available.
- When an agent should explain that validated NeqSim methods are required for real hydrate prediction and inhibitor design.

## Inputs

- `operating_temperature`: operating temperature in C.
- `hydrate_equilibrium_temperature`: hydrate equilibrium (formation) temperature in C from a validated NeqSim calculation.
- `min_margin`: configurable minimum acceptable margin in C (constructor, default 3.0).

## Outputs

- `hydrate_margin_c`: operating temperature minus the hydrate equilibrium temperature in C.
- `subcooling_c`: subcooling into the hydrate region in C, zero when the operating point is outside the region.
- `margin_warning`: `ok`, `watch`, or `high`.
- `neqsim_available`: whether the optional NeqSim package is importable.
- `assumptions`: public assumptions and required follow-up.

## Engineering Method

The placeholder method computes the hydrate margin as `operating_temperature - hydrate_equilibrium_temperature`. A non-positive margin means the operating point is inside the hydrate region and is flagged `high`. A small positive margin below `min_margin` is flagged `watch`. A larger margin is flagged `ok`.

This is not a thermodynamic hydrate model. The hydrate equilibrium temperature must come from a validated NeqSim hydrate calculation with a defined fluid composition, water content, inhibitor basis, and operating envelope.

## Python Usage Pattern

```python
from hydrate_margin_check import HydrateMarginModel

model = HydrateMarginModel(min_margin=3.0)
result = model.evaluate(
    operating_temperature=10.0,
    hydrate_equilibrium_temperature=8.0,
)

print(result.hydrate_margin_c)
print(result.margin_warning)
print(result.assumptions)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim hydrate workflows. If not, the example still runs with fallback placeholder logic.

## Validation Checklist

- [ ] Operating and hydrate equilibrium temperatures are finite.
- [ ] The hydrate equilibrium temperature came from a validated NeqSim calculation.
- [ ] In-region and out-of-region cases are tested.
- [ ] The minimum margin is documented as a configurable public guideline only.
- [ ] Results are not used as inhibitor dosage, design margin, or operating limit.
- [ ] Real hydrate work is redirected to validated NeqSim methods and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Margin looks safe but hydrates still form | Hydrate equilibrium temperature was guessed | Use a validated NeqSim hydrate equilibrium temperature |
| Margin warning never triggers | `min_margin` set too low | Use a service-appropriate operating margin and confirm with flow assurance review |
| Missing inhibitor effect | Inhibitor was not included in the NeqSim basis | Provide the inhibitor basis to NeqSim and recompute the hydrate temperature |

## Limitations

- No hydrate phase equilibrium calculation is performed in this skill.
- No salinity, methanol, MEG, kinetics, composition, or transient effects are included.
- No proprietary hydrate curves or company operating envelopes are included.
- Not suitable for flow assurance design, inhibitor dosage, or operating-limit decisions.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.thermo.system.SystemSrkCPAstatoil` — CPA equation of state for water-bearing natural gas.
- `neqsim.thermodynamicoperations.ThermodynamicOperations#hydrateFormationTemperature()` — rigorous hydrate equilibrium temperature and margin to operating conditions.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Sloan and Koh, Clathrate Hydrates of Natural Gases, for public hydrate background.
