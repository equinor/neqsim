---
name: neqsim-fired-heater-duty-screening
version: "0.1.0"
description: "Educational fired-heater duty and radiant-flux screening using public energy-balance relations (API 560 style). USE WHEN: a task needs a public, screening-level estimate of process duty, fired duty, fuel rate, and average radiant flux for a fired heater before detailed thermal design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Fired Heater Duty Screening

Use this skill for public, educational fired-heater screening. It estimates the process duty, the fired duty, the fuel-gas rate, and the average radiant-section heat flux using open energy-balance relations so an agent can scope a fired-heater study and check the radiant flux against a public guideline before detailed thermal design.

## When to Use

- When a user asks roughly what fired duty and fuel rate a heater needs.
- When an agent needs a quick radiant-flux check to scope a fired-heater study.
- When examples must run without confidential heater designs, vendor data, or company specs.

## Inputs

- `mass_flow`: process mass flow in kg/s.
- `specific_heat`: process-fluid specific heat in kJ/(kg K).
- `inlet_temperature`: process inlet temperature in kelvin.
- `outlet_temperature`: process outlet temperature in kelvin.
- `thermal_efficiency`: heater thermal efficiency, default 0.85.
- `fuel_heating_value`: fuel lower heating value in MJ/kg, default 46.0.
- `radiant_area`: radiant-section tube surface area in m2.
- `allowable_radiant_flux`: allowable average radiant flux in kW/m2, default 37.0.

## Outputs

- `process_duty_kw`: process absorbed duty `Q`.
- `fired_duty_kw`: fired duty from the efficiency.
- `fuel_rate_kg_s`: fuel-gas mass rate.
- `average_radiant_flux_kw_m2`: process duty divided by radiant area.
- `flux_ratio`: ratio of average radiant flux to the allowable flux.
- `fired_heater_warning`: `ok`, `watch`, or `high-flux`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `FiredHeaterDutyModel` uses open energy-balance relations only:

- the process duty uses `Q = mass_flow * specific_heat * (T_out - T_in)`.
- the fired duty uses `Q_fired = Q / thermal_efficiency`.
- the fuel rate uses `fuel = Q_fired / fuel_heating_value`.
- the average radiant flux uses `Q / radiant_area` and is compared to a public guideline.

This is educational and screening-only logic. It uses a constant specific heat, treats all process duty as absorbed in the radiant section for the flux estimate, and does not model convection-section split, tube-wall temperature, flame and bridgewall temperature, draft, or emissions. Typical public average radiant fluxes are about 30 to 37 kW/m2. It is not a replacement for validated fired-heater design (for example API 560) and a qualified review.

## Python Usage Pattern

```python
from fired_heater_duty_screening import FiredHeaterDutyModel

model = FiredHeaterDutyModel()
result = model.evaluate(
    mass_flow=15.0,
    specific_heat=2.4,
    inlet_temperature=473.15,
    outlet_temperature=623.15,
    radiant_area=200.0,
)

print(result.fired_heater_warning)
print(result.fired_duty_kw)
print(result.average_radiant_flux_kw_m2)
```

## Related NeqSim Functionality

For validated fired-heater calculations, redirect to existing NeqSim classes:

- `neqsim.process.equipment.heatexchanger.FiredHeater` — fired-heater equipment model with duty and efficiency.
- `neqsim.process.equipment.heatexchanger.Heater` — generic process heater with duty calculation.
- `neqsim.process.equipment.powergeneration.HRSG` — heat-recovery steam generator for waste-heat duty.

This skill is a public energy-balance triage layer that decides when to invoke those validated heater classes.

## Validation Checklist

- [ ] Mass flow, specific heat, and temperatures are positive and `T_out > T_in`.
- [ ] Radiant area and efficiency are positive and in range.
- [ ] Tests cover a duty calculation, a high-flux case, and invalid input.
- [ ] Results are described as educational screening indicators.
- [ ] Real design is redirected to validated methods, API 560, NeqSim, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Duty too low | Specific heat at wrong temperature | Use an average specific heat over the duty range |
| Flux always high | All duty assigned to radiant section | Split convection and radiant duty in detailed design |
| Fuel rate wrong | Higher heating value used with efficiency on lower basis | Keep the heating value and efficiency on the same basis |

## Limitations

- No proprietary heater designs, vendor data, or company specs are included.
- No convection-section split, tube-wall, draft, or emissions modeling is performed.
- A constant specific heat is assumed over the full duty range.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
