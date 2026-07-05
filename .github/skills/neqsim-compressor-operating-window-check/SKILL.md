---
name: neqsim-compressor-operating-window-check
version: "0.1.0"
description: "Educational compressor operating window screening against surge and stonewall margins. USE WHEN: a task needs a public, screening-level check that a centrifugal compressor operating point sits inside the surge and stonewall limits before detailed performance review."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Compressor Operating Window Check

Use this skill for public, educational compressor operating window screening. It compares an operating flow against surge and stonewall limits to flag whether a centrifugal compressor operates inside a safe operating window before moving to validated performance and anti-surge review.

## When to Use

- When a user asks whether a compressor operating point is too close to surge or stonewall.
- When an agent needs a quick operating-window triage to scope a compressor or debottlenecking study.
- When examples must run without confidential performance maps, vendor curves, or anti-surge control settings.

## Inputs

- `operating_flow`: actual or required volumetric flow at the operating point in m3/h.
- `surge_flow`: surge-limit flow at the same speed and conditions in m3/h.
- `stonewall_flow`: stonewall or choke-limit flow at the same speed and conditions in m3/h.
- `min_surge_margin`: minimum acceptable surge margin fraction, default 0.10.
- `min_stonewall_margin`: minimum acceptable stonewall margin fraction, default 0.05.

## Outputs

- `surge_margin_fraction`: fractional distance from the operating point to the surge limit.
- `stonewall_margin_fraction`: fractional distance from the operating point to the stonewall limit.
- `limiting_margin_fraction`: the smaller of the two margins, where lower means closer to a limit.
- `operating_window_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `CompressorOperatingWindowModel` uses open, published concepts only:

- the surge margin is `(operating_flow - surge_flow) / surge_flow`.
- the stonewall margin is `(stonewall_flow - operating_flow) / stonewall_flow`.
- the limiting margin is the smaller of the two and drives rule-based warnings.
- an operating point at or beyond either limit is flagged `high`; a margin below the minimum is flagged `watch`.

This is educational and screening-only logic. It is not a compressor performance standard, an anti-surge design method, a vendor method, or a replacement for validated performance review aligned with API 617 and a qualified process engineering review.

## Python Usage Pattern

```python
from compressor_operating_window import CompressorOperatingWindowModel

model = CompressorOperatingWindowModel()
result = model.evaluate(
    operating_flow=1300.0,
    surge_flow=1000.0,
    stonewall_flow=2000.0,
)

print(result.operating_window_warning)
print(result.surge_margin_fraction)
print(result.stonewall_margin_fraction)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim compressor calculations and performance curves. If it is not installed, the example still runs with public placeholder logic.

## Validation Checklist

- [ ] Inputs are positive and flows share the same speed and reference conditions.
- [ ] `surge_flow` is below `stonewall_flow`.
- [ ] Tests cover normal, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real performance review is redirected to validated methods, API 617, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Margins look inconsistent | Surge and stonewall flows taken at different speeds | Use surge and stonewall flows at the same speed and conditions |
| Operating point never flags surge | Surge flow taken too low | Use the surge flow from the relevant speed line on the map |
| Wrong window | `surge_flow` and `stonewall_flow` swapped | Keep `surge_flow` below `stonewall_flow` |

## Limitations

- No proprietary performance maps, vendor curves, or anti-surge control data are included.
- No head, polytropic efficiency, or power checks are performed.
- No transient, recycle, or control-margin dynamics are included.
- Not suitable for safety-critical, design, guarantee, or standards-compliance work.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.process.equipment.compressor.Compressor` — rigorous polytropic compression with real-gas properties.
- `neqsim.process.equipment.compressor.CompressorChart` — vendor performance maps with surge and stonewall limits.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public turbomachinery references such as API Standard 617 for general operating-window concepts.
