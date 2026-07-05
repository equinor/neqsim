---
name: neqsim-hydrate-screening
version: "0.1.0"
description: "Educational hydrate risk screening placeholder with public assumptions. USE WHEN: a task needs a quick hydrate risk triage and should be directed to validated NeqSim methods for real calculations."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Hydrate Screening

Use this skill for a quick, public hydrate-risk screening example. It is intentionally simple and should guide users toward validated NeqSim hydrate calculations for real work.

## When to Use

- When a user asks for a first-pass hydrate risk triage example.
- When only pressure, temperature, and water presence are available.
- When an agent should explain that validated NeqSim methods are required for real hydrate prediction.

## Inputs

- `pressure`: operating pressure in bar.
- `temperature`: operating temperature in C.
- `water_present`: boolean indicating whether free or condensed water may be present.

## Outputs

- `risk_level`: `low`, `medium`, or `high`.
- `margin_indicator`: temperature margin in C above the placeholder hydrate boundary.
- `estimated_boundary`: placeholder hydrate boundary temperature in C.
- `assumptions`: public assumptions and required follow-up.

## Engineering Method

The placeholder method estimates a simple pressure-dependent boundary and compares operating temperature to that boundary. If water is absent, risk is reported as low with an assumption note. If water is present, low margin gives higher risk.

This is not a thermodynamic hydrate model. Real hydrate calculations should use validated NeqSim methods with a defined fluid composition, water content, inhibitor basis, and operating envelope.

## Python Usage Pattern

```python
from hydrate_screening import HydrateScreener

screener = HydrateScreener()
result = screener.screen(pressure=80.0, temperature=4.0, water_present=True)

print(result.risk_level)
print(result.margin_indicator)
print(result.assumptions)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim hydrate workflows. If not, the example still runs with fallback placeholder logic.

## Validation Checklist

- [ ] Pressure is positive and temperature is finite.
- [ ] Water-present and water-absent cases are tested.
- [ ] The placeholder boundary is documented as educational only.
- [ ] Results are not used as inhibitor dosage, design margin, or operating limit.
- [ ] Real hydrate work is redirected to validated NeqSim methods.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Low risk reported despite cold temperature | `water_present=False` was assumed | Confirm water phase, water dew point, and operating history |
| Treating margin as a design safety margin | Placeholder boundary is not thermodynamic | Use validated NeqSim hydrate calculations |
| Missing composition effects | Only pressure, temperature, and water flag were provided | Provide full composition and inhibitor basis to NeqSim |

## Limitations

- No hydrate phase equilibrium calculation is performed.
- No salinity, methanol, MEG, kinetics, composition, or transient effects are included.
- No proprietary hydrate curves or company operating envelopes are included.
- Not suitable for flow assurance design, inhibitor dosage, or operating-limit decisions.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.thermo.system.SystemSrkCPAstatoil` — CPA equation of state for water-bearing natural gas.
- `neqsim.thermodynamicoperations.ThermodynamicOperations#hydrateFormationTemperature()` — rigorous hydrate equilibrium temperature.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Sloan and Koh, Clathrate Hydrates of Natural Gases, for public hydrate background.