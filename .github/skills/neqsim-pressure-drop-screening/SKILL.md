---
name: neqsim-pressure-drop-screening
version: "0.1.0"
description: "Educational single-phase line pressure-drop screening against a recommended pressure-gradient guideline. USE WHEN: a task needs a public, screening-level Darcy-Weisbach pressure-drop estimate and a check against NORSOK P-002 / GPSA style line pressure-gradient guidelines before detailed hydraulic design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Pressure Drop Screening

Use this skill for public, educational line pressure-drop screening. It estimates a single-phase Darcy-Weisbach pressure gradient and compares it to a recommended pressure-gradient guideline so an agent can flag lines that may exceed standard hydraulic limits before moving to validated line sizing.

## When to Use

- When a user asks whether a line pressure drop sits within recommended limits.
- When an agent needs a quick pressure-gradient triage to scope a hydraulic or debottlenecking study.
- When examples must run without confidential piping classes, project line lists, or company piping specs.

## Inputs

- `fluid_velocity`: actual flowing velocity in the line in m/s.
- `mixture_density`: flowing mixture density in kg/m3.
- `viscosity`: flowing dynamic viscosity in Pa.s.
- `pipe_inner_diameter`: pipe inner diameter in m.
- `length`: line length used for the total pressure drop in m, default 100.0.
- `roughness`: absolute pipe roughness in m, default 4.6e-5 (commercial steel).
- `guideline_bar_per_100m`: recommended maximum pressure gradient in bar per 100 m, default 0.5.

## Outputs

- `reynolds_number`: flow Reynolds number.
- `friction_factor`: Darcy friction factor from laminar or Haaland turbulent form.
- `dp_per_100m_bar`: screening pressure gradient in bar per 100 m.
- `dp_total_bar`: screening total pressure drop over the line length in bar.
- `guideline_ratio`: ratio of the pressure gradient to the recommended guideline.
- `pressure_drop_warning`: `ok`, `watch`, or `high`.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `PressureDropModel` uses open, published correlations only:

- the Reynolds number uses the standard `Re = rho v D / mu` form.
- the friction factor uses `f = 64 / Re` in laminar flow and the public Haaland explicit approximation of the Colebrook equation in turbulent flow.
- the pressure gradient uses the Darcy-Weisbach form `dP/L = f (1/D) (rho v^2 / 2)`.
- the guideline ratio compares the pressure gradient to a configurable recommended gradient aligned with NORSOK P-002 and GPSA style line pressure-gradient guidelines.

This is educational and screening-only logic. It is not a piping standard, a line sizing method, a vendor method, a two-phase or transient hydraulic method, or a replacement for validated hydraulic design and a qualified process engineering review.

## Python Usage Pattern

```python
from pressure_drop_screening import PressureDropModel

model = PressureDropModel()
result = model.evaluate(
    fluid_velocity=12.0,
    mixture_density=50.0,
    viscosity=1.2e-5,
    pipe_inner_diameter=0.2,
    length=100.0,
)

print(result.pressure_drop_warning)
print(result.dp_per_100m_bar)
print(result.guideline_ratio)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim hydraulic models (for example `PipeBeggsAndBrills`) for real pressure-drop calculations. If it is not installed, the example still runs with public placeholder logic.

## Related NeqSim Functionality

For validated pressure-drop calculations, redirect to existing NeqSim classes instead of this screening placeholder:

- `neqsim.process.equipment.pipeline.PipeBeggsAndBrills` — Beggs and Brills multiphase pipe flow with elevation, holdup, and friction.
- `neqsim.process.equipment.pipeline.AdiabaticTwoPhasePipe` and `neqsim.fluidmechanics` flow solvers — rigorous single and two-phase hydraulics.
- `neqsim.process.mechanicaldesign.heatexchanger.ThermalDesignCalculator` — tube-side `f L/D (rho v^2 / 2)` pressure drop inside exchangers.

This skill is a public triage layer that decides when to invoke those validated models.

## Validation Checklist

- [ ] Inputs are positive and densities, velocities, and viscosities are in SI units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover laminar, turbulent, warning, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real hydraulic design is redirected to validated methods, NORSOK P-002, GPSA, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Pressure gradient looks too low | Density taken at standard rather than flowing conditions | Evaluate density at line pressure and temperature |
| Friction factor looks wrong near Re 2000 | Mixing laminar and turbulent forms at transition | Treat the transition band as approximate and validate with detailed hydraulics |
| Guideline never triggers | Guideline gradient set too high | Use a service-appropriate gradient per NORSOK P-002 / GPSA style limits |

## Limitations

- No proprietary piping classes, project line lists, or company piping specs are included.
- No two-phase, slug, flashing, or transient pressure-drop modelling is performed.
- No fittings, valves, elevation, or acceleration pressure losses are included.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
