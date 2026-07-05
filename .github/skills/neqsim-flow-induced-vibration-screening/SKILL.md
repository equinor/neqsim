---
name: neqsim-flow-induced-vibration-screening
version: "0.1.0"
description: "Educational flow-induced vibration (FIV) screening using a public fluid kinetic-energy (rho v^2) likelihood-of-failure index. USE WHEN: a task needs a public, screening-level check of whether a main-line flow velocity and density produce a kinetic-energy level that warrants a detailed Energy Institute style FIV assessment before piping vibration design."
last_verified: "2026-06-18"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Flow-Induced Vibration Screening

Use this skill for public, educational flow-induced vibration (FIV) screening. It computes a fluid kinetic-energy index `rho v^2` and compares it to a configurable kinetic-energy threshold so an agent can flag piping that may need a detailed Energy Institute style FIV likelihood-of-failure assessment before vibration design.

## When to Use

- When a user asks whether a line could be prone to flow-induced vibration.
- When an agent needs a quick kinetic-energy triage to scope a piping vibration study.
- When examples must run without confidential piping classes, project line lists, or company piping specs.

## Inputs

- `fluid_velocity`: actual flowing velocity in the line in m/s.
- `mixture_density`: flowing mixture density in kg/m3.
- `kinetic_energy_threshold`: screening kinetic-energy threshold in Pa, default 10000.0.
- `small_bore_present`: optional flag that a small-bore connection or thermowell is present, default False.

## Outputs

- `kinetic_energy_pa`: fluid kinetic-energy index `rho v^2` in Pa.
- `threshold_ratio`: ratio of the kinetic energy to the screening threshold.
- `likelihood_of_failure_band`: qualitative `low`, `medium`, or `high` band.
- `fiv_warning`: `ok`, `watch`, or `high`.
- `small_bore_flag`: True when a small-bore connection raises the screening sensitivity.
- `assumptions`: public assumptions used by the placeholder model.

## Engineering Method

The Python class `FlowInducedVibrationModel` uses an open, published screening concept only:

- the fluid kinetic energy uses the widely published index `FKE = rho v^2`, the same quantity used as the primary driver in public Energy Institute style FIV likelihood-of-failure screening.
- the threshold ratio compares the kinetic energy to a configurable screening threshold.
- a small-bore connection flag lowers the effective warning thresholds because small-bore and thermowell connections are a common FIV failure location.
- the likelihood-of-failure band is a simple rule-based label derived from the threshold ratio.

This is educational and screening-only logic. It does not reproduce the proprietary Energy Institute Guidelines, scoring tables, or correction factors. It is not a vibration standard, a fatigue method, a modal analysis, or a replacement for a qualified piping vibration assessment.

## Python Usage Pattern

```python
from flow_induced_vibration_screening import FlowInducedVibrationModel

model = FlowInducedVibrationModel()
result = model.evaluate(
    fluid_velocity=20.0,
    mixture_density=60.0,
    kinetic_energy_threshold=10000.0,
    small_bore_present=False,
)

print(result.fiv_warning)
print(result.kinetic_energy_pa)
print(result.likelihood_of_failure_band)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can recommend moving to validated NeqSim property models for mixture density and velocity, followed by a detailed FIV assessment. If it is not installed, the example still runs with public placeholder logic.

## Related NeqSim Functionality

NeqSim already implements a validated Energy Institute style FIV likelihood-of-failure model. Redirect real assessments to:

- `neqsim.process.measurementdevice.FlowInducedVibrationAnalyser` — likelihood-of-failure analyser attached to a pipe segment.
- `neqsim.process.mechanicaldesign.manifold.ManifoldMechanicalDesignCalculator` — acoustic-induced vibration (AIV) likelihood-of-failure for manifold piping.

This skill is a public `rho v^2` triage layer that decides when to invoke `FlowInducedVibrationAnalyser` for a full assessment.

## Validation Checklist

- [ ] Inputs are positive and densities and velocities are in SI units.
- [ ] Example inputs are public and synthetic.
- [ ] Tests cover low, warning, high, small-bore, and invalid-input cases.
- [ ] Results are described as educational screening indicators.
- [ ] Real FIV assessment is redirected to validated methods, Energy Institute guidelines, and qualified review.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Kinetic energy looks too low | Density taken at standard rather than flowing conditions | Evaluate density at line pressure and temperature |
| Threshold never triggers | Threshold set above realistic main-line limits | Use a service-appropriate threshold and consider the small-bore flag |
| Result treated as a fatigue life | Confusing screening with assessment | Move to a detailed FIV likelihood-of-failure assessment |

## Limitations

- No proprietary Energy Institute scoring tables, correction factors, or fatigue calculations are included.
- No mechanical, modal, acoustic, or support-stiffness analysis is performed.
- No transient, slug, or two-phase intermittency excitation is modelled.

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
