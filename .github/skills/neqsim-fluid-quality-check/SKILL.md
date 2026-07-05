---
name: neqsim-fluid-quality-check
version: "0.1.0"
description: "Public checks for fluid composition quality before NeqSim simulation. USE WHEN: a task needs mole fraction sum checks, negative fraction checks, required component checks, or water/CO2/H2S flags."
last_verified: "2026-05-31"
requires:
  python_packages: []
  java_packages: []
  env: []
  network: []
---

# Fluid Quality Check

Use this skill to check whether a public fluid composition is well-formed before it is used in examples, notebooks, or NeqSim simulations.

## When to Use

- When a task includes a gas, oil, condensate, or synthetic composition table.
- When mole fractions should sum close to 1.0 before simulation.
- When an agent should flag negative fractions, missing required components, water, CO2, or H2S.
- When composition validation must avoid confidential or proprietary data.

## Inputs

- `composition`: mapping of component name to mole fraction.
- `required_components`: optional sequence of components that must be present.
- `tolerance`: acceptable absolute deviation from a total mole fraction of 1.0.

## Outputs

- `total_fraction`: sum of all fractions.
- `total_within_tolerance`: whether the total is close to 1.0.
- `negative_components`: components with negative fractions.
- `missing_components`: required components absent from the composition.
- `flagged_components`: values for water, CO2, and H2S when present.
- `warnings`: human-readable quality messages.
- `is_usable`: true only when no blocking quality issue is found.

## Engineering Method

The checker performs transparent public data-quality checks only. It does not estimate missing components, tune an equation of state, characterize plus fractions, regress binary interaction parameters, or validate laboratory procedures.

## Python Usage Pattern

```python
from fluid_quality_check import FluidQualityChecker

composition = {
    "methane": 0.82,
    "ethane": 0.08,
    "propane": 0.04,
    "CO2": 0.03,
    "water": 0.03,
}

checker = FluidQualityChecker(required_components=("methane", "ethane", "propane"))
result = checker.check(composition)

print(result.is_usable)
print(result.warnings)
```

If the optional `neqsim` Python package is available, the result records that fact so an agent can proceed to NeqSim-specific setup checks. If not, the quality checks still run.

## Validation Checklist

- [ ] Composition values are finite numbers.
- [ ] Sum is within the documented tolerance or a warning is emitted.
- [ ] Negative fractions are rejected as blocking issues.
- [ ] Required components are present.
- [ ] Water, CO2, and H2S are flagged without implying proprietary gas quality rules.
- [ ] Examples use synthetic compositions.

## Common Mistakes

| Symptom | Cause | Fix |
| --- | --- | --- |
| Total fraction is 100 instead of 1 | Percent values were used as mole fractions | Divide by 100 before checking |
| H2S not flagged | Component name spelling differs | Use a recognizable alias such as `H2S` or `hydrogen sulfide` |
| Negative component accepted by spreadsheet | Manual copy/paste error | Run the checker before simulation |

## Limitations

- No proprietary fluid validation workflow is included.
- No EOS selection, plus-fraction characterization, or lab data reconciliation is performed.
- Water, CO2, and H2S flags are informational only and are not compliance checks.
- Not a replacement for validated PVT analysis or NeqSim thermodynamic calculations.

## Related NeqSim Functionality

This educational screening corresponds to validated, rigorous functionality in the NeqSim Java library that a qualified engineer should use for design-grade work:

- `neqsim.thermodynamicoperations.ThermodynamicOperations#TPflash()` — rigorous phase equilibrium for composition quality checks.
- `neqsim.standards.gasquality.Standard_ISO6976` — ISO 6976 calorific value, density, and Wobbe index.

In Python the same classes are reachable through the `neqsim` package (for example `from neqsim import jneqsim`).

## References

- NeqSim repository: https://github.com/equinor/neqsim
- NeqSim Skills Guide: https://github.com/equinor/neqsim/blob/master/docs/integration/skills_guide.md
- Public thermodynamics and PVT texts for general composition quality principles.