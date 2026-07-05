---
name: neqsim-psv-orifice-screening
version: "0.1.0"
description: "Educational pressure-safety-valve orifice screening using the public API 520 Part I critical gas-flow equation. USE WHEN: a task needs a public, screening-level required PSV orifice area and a mapped API orifice letter before detailed relief-valve sizing and selection."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# PSV Orifice Screening

Use this skill for public, educational pressure-safety-valve (PSV) orifice screening. It estimates the required effective orifice area for critical gas flow using the open API 520 Part I equation and maps it to a standard API letter designation so an agent can scope a relief study before detailed sizing.

## When to Use

- When a user asks roughly what PSV orifice letter a gas relief case needs.
- When an agent needs a quick relief-area triage before detailed API 520 sizing.
- When examples must run without confidential valve data, vendor capacity charts, or company relief specs.

## Inputs

- `relief_rate`: required relief mass flow `W` in kg/h.
- `relieving_pressure`: upstream relieving pressure `P1` in bar absolute.
- `temperature`: relieving temperature `T` in kelvin.
- `compressibility`: gas compressibility factor `Z`, default 1.0.
- `molecular_weight`: gas molecular weight `M` in g/mol.
- `specific_heat_ratio`: ratio of specific heats `k`, default 1.3.
- `discharge_coefficient`: effective discharge coefficient `Kd`, default 0.975.
- `back_pressure_correction`: back-pressure correction `Kb`, default 1.0.
- `combination_correction`: rupture-disk combination correction `Kc`, default 1.0.

## Outputs

- `coefficient_c`: the `C` coefficient from `k`.
- `required_area_mm2`: required effective orifice area.
- `selected_orifice`: the smallest standard API orifice that meets the area, or `none`.
- `selected_orifice_area_mm2`: the area of the selected orifice.
- `area_margin_ratio`: ratio of selected area to required area.
- `psv_warning`: `ok`, `watch`, or `oversize-needed`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `PsvOrificeModel` uses the open API 520 Part I critical gas-flow equation only:

- the coefficient uses `C = 0.03948 * sqrt(k (2 / (k + 1))^((k + 1) / (k - 1)))` in SI-consistent screening form.
- the required area uses `A = (W / (C Kd P1 Kb Kc)) * sqrt(T Z / M)`.
- the area is mapped to the standard API 526 orifice letters (D through T) using their published effective areas.
- the warning is a simple rule-based label aligned with API 520 intent.

This is educational and screening-only logic. It assumes critical (choked) gas flow and does not check the choked-flow criterion, subcritical flow, liquid or two-phase relief, fire-case sizing, inlet or outlet pressure-drop limits, or vendor certified capacities. It is not a replacement for validated relief-valve sizing and a qualified safety review.

## Python Usage Pattern

```python
from psv_orifice_screening import PsvOrificeModel

model = PsvOrificeModel()
result = model.evaluate(
    relief_rate=50000.0,
    relieving_pressure=20.0,
    temperature=323.15,
    molecular_weight=19.0,
)

print(result.selected_orifice)
print(result.required_area_mm2)
print(result.psv_warning)
```

## Related NeqSim Functionality

For validated PSV sizing, redirect to existing NeqSim classes:

- `neqsim.process.util.fire.ReliefValveSizing` — API 520 gas, liquid, and two-phase relief sizing including fire case.
- `neqsim.process.equipment.valve.SafetyValve` — safety-valve equipment model for process simulations.

This skill is a public API 520 triage layer that decides when to invoke those validated relief classes.

## Validation Checklist

- [ ] Relief rate, pressure, temperature, and molecular weight are positive and in the stated units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover an orifice selection, an oversize-needed case, and invalid input.
- [ ] Results are described as educational screening indicators.
- [ ] Real sizing is redirected to validated methods, API 520, API 526, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Orifice too small | Choked-flow assumption invalid at low overpressure | Check the critical-flow criterion and use subcritical sizing if needed |
| Area off by orders of magnitude | Mixed mass and molar units | Keep `W` in kg/h and `M` in g/mol consistently |
| Fire case under-sized | Fire-case heat input not included | Use the API 521 fire-case relief rate as input |

## Limitations

- No proprietary valve data, vendor capacity charts, or company relief specs are included.
- No liquid, two-phase, or fire-case sizing logic is performed.
- No inlet or outlet pressure-drop or certified-capacity checks are included.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
