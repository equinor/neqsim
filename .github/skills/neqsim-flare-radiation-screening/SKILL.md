---
name: neqsim-flare-radiation-screening
version: "0.1.0"
description: "Educational flare thermal-radiation screening using the public API 521 / API 537 point-source equation. USE WHEN: a task needs a public, screening-level estimate of radiant heat flux at a distance from a flare and a check against allowable radiation limits before detailed flare and radiation design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Flare Radiation Screening

Use this skill for public, educational flare thermal-radiation screening. It estimates radiant heat flux at a distance from a flare using the open API 521 / API 537 point-source model and compares it to common allowable limits so an agent can decide whether a detailed flare-radiation study is needed.

## When to Use

- When a user asks how far personnel or equipment must be from a flare for a given relief rate.
- When an agent needs a quick radiation triage to scope a flare or relief study.
- When examples must run without confidential flare designs, vendor tip data, or company radiation criteria.

## Inputs

- `mass_flow`: flared mass flow in kg/s.
- `heat_of_combustion`: lower heating value of the flared gas in MJ/kg.
- `distance`: distance from the flame center to the receptor in m.
- `fraction_radiated`: fraction of heat radiated `F`, default 0.2.
- `transmissivity`: atmospheric transmissivity, default 1.0.
- `allowable_flux`: allowable radiant flux in kW/m2, default 6.31 (API 521 limit for a few seconds of exposure).

## Outputs

- `total_heat_release_kw`: total combustion heat release `Q`.
- `radiant_heat_flux_kw_m2`: radiant flux at the receptor.
- `allowable_flux_kw_m2`: the supplied allowable flux.
- `flux_ratio`: ratio of computed flux to allowable flux.
- `radiation_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `FlareRadiationModel` uses the open API 521 / API 537 point-source equation only:

- the total heat release uses `Q = mass_flow * heat_of_combustion`.
- the radiant flux uses `q = tau * F * Q / (4 pi r^2)`, treating the flame as a single point source.
- the warning compares the flux to the allowable limit using simple rule-based thresholds.

Common public allowable radiation limits from API 521 are about 4.73 kW/m2 (continuous exposure), 6.31 kW/m2 (a few seconds for escape), and 9.46 kW/m2 (equipment and structures). This is educational and screening-only logic. It does not include flame length and tilt, wind, multi-point flame models, view factors, solar radiation, or shielding. It is not a vendor flare method or a replacement for validated flare-radiation design and a qualified safety review.

## Python Usage Pattern

```python
from flare_radiation_screening import FlareRadiationModel

model = FlareRadiationModel()
result = model.evaluate(
    mass_flow=50.0,
    heat_of_combustion=46.0,
    distance=60.0,
)

print(result.radiation_warning)
print(result.radiant_heat_flux_kw_m2)
print(result.flux_ratio)
```

## Related NeqSim Functionality

For validated flare-radiation calculations, redirect to existing NeqSim classes:

- `neqsim.process.equipment.flare.Flare` — flare model with `estimateRadiationHeatFlux` for radiant flux at distance.
- `neqsim.process.equipment.flare.FlareStack` — flare stack sizing and tip selection.
- `neqsim.mcp.runners.FlareRadiationRunner` — runner that drives flare radiation calculations.

This skill is a public point-source triage layer that decides when to invoke those validated flare classes.

## Validation Checklist

- [ ] Mass flow, heating value, and distance are positive and in the stated units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover ok, watch, high, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real design is redirected to validated methods, API 521, API 537, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Flux far too low | Fraction radiated set too small for the gas | Use an `F` value appropriate to the gas composition |
| Distance criterion always passes | Allowable flux set too high | Use the API 521 limit matched to the exposure scenario |
| Heat release wrong order of magnitude | Higher heating value mixed with lower heating value | Use a consistent lower heating value basis |

## Limitations

- No proprietary flare designs, vendor tip data, or company radiation criteria are included.
- No flame length, tilt, wind, view factor, or solar radiation is modeled.
- No shielding, dispersion, or multi-flame interaction is considered.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
