---
name: neqsim-relief-load-screening
version: "0.1.0"
description: "Educational fire-case relief load screening indicators for pressure relief devices. USE WHEN: a task needs public, screening-level fire-case relief load estimates and capacity flags without proprietary relief design methods."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Relief Load Screening

Use this skill for public, educational pressure-relief screening examples. It provides a simple fire-case heat input and relief mass-rate indicator that helps agents structure early relief questions before moving to validated relief sizing and detailed process safety design.

## When to Use

- When a user asks for a quick, public fire-case relief load triage example.
- When an agent needs a placeholder relief mass-rate estimate to scope a relief or flare study.
- When examples must run without confidential vessel data, project relief bases, or company design rules.

## Inputs

- `wetted_area`: fire-exposed wetted surface area in m2.
- `latent_heat`: latent heat of vaporization of the relieving fluid in kJ/kg.
- `relief_pressure`: set or relieving pressure in bara (used for context and warnings).
- `environment_factor`: dimensionless credit factor for insulation or drainage, default 1.0 (no credit).

## Outputs

- `fire_heat_input_kW`: screening fire heat input from a public API 521 style correlation.
- `relief_mass_rate_kg_per_h`: screening vapor relief mass rate from heat input divided by latent heat.
- `relief_load_indicator`: dimensionless ratio of relief mass rate to a configurable public basis.
- `relief_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `ReliefLoadModel` uses open, published correlations only:

- fire heat input uses the public API 521 adequate-drainage form `Q = F * C * A^0.82` with a public coefficient expressed in kW.
- vapor relief mass rate is estimated as fire heat input divided by latent heat of vaporization.
- the relief load indicator scales the relief mass rate by a configurable public reference basis.
- warnings are rule-based thresholds on the relief load indicator.

This is educational and screening-only logic. It is not a relief sizing standard, an orifice sizing method, a vendor method, or a replacement for a validated relief design and process safety review.

## Python Usage Pattern

```python
from relief_load_screening import ReliefLoadModel

model = ReliefLoadModel()
result = model.evaluate(
    wetted_area=50.0,
    latent_heat=300.0,
    relief_pressure=20.0,
    environment_factor=1.0,
)

print(result.relief_warning)
print(result.fire_heat_input_kW)
print(result.relief_mass_rate_kg_per_h)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim property calculations for latent heat and relieving conditions. If it is not installed, the example still runs with public placeholder logic.

## Validation Checklist

- [ ] Inputs are positive where applicable and the environment factor is within a sensible public range.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover normal, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real relief sizing is redirected to validated methods, API 520/521, and qualified process safety review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Treating `relief_mass_rate_kg_per_h` as a sized relief rate | Placeholder model is not a sizing method | Use API 520/521 methods and validated NeqSim properties for design |
| Wrong heat input magnitude | Wetted area unit is not m2 | Keep area in m2 and review the fire-zone definition |
| Missing relief at low temperature | Latent heat taken at the wrong conditions | Evaluate latent heat at relieving pressure and temperature |

## Limitations

- No proprietary relief device data, vendor curves, or project relief bases are included.
- No two-phase, supercritical, or thermal-relief sizing is performed.
- No back-pressure, inlet/outlet line, or reaction-force checks are included.
- Not suitable for safety-critical, design, guarantee, or standards-compliance work.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.process.util.fire.ReliefValveSizing` — API 520 / API 521 relief-load and orifice sizing.
- `neqsim.process.equipment.valve.SafetyValve` — relief-device model inside a process flowsheet.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public pressure-relief engineering references such as API Standard 520 and API Standard 521 for general concepts.
