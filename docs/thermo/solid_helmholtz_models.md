---
title: "Experimental Solid Helmholtz Models"
description: "Use NeqSim's opt-in pure solid argon and para-hydrogen Helmholtz equations, inspect SI molar properties, and obtain structured freezing-point diagnostics."
---

NeqSim provides experimental fundamental Helmholtz equations for pure solid argon and
phase-I para-hydrogen. Use these models when you need a reproducible solid-state property
calculation or a pure para-hydrogen freezing point. They are not general mixture solid
solutions, precipitation models, kinetic nucleation models, or design-certification tools.

The two supported workflows have different owners:

- `SystemArgonSolidHelmholtzEos` is a one-phase, pure-solid system for argon properties.
- `SystemLeachmanEos` couples the para-hydrogen fluid reference equation to the calibrated
  solid Helmholtz phase for solid-fluid equilibrium.
- `SystemSolidHelmholtzEos` is the extensible single-component base for a custom
  `SolidHelmholtzEquation`; it is not a mixture system.

## Validity and units

| Model | Temperature | Absolute pressure | Intended use |
| --- | --- | --- | --- |
| Solid argon | Above 0 K through 300 K | Above 0 through 160,000 bara (16 GPa) | Pure solid-state properties |
| Solid para-hydrogen | Above 0 K through 200 K | Above 0 through 100,000 bara (10 GPa) | Pure solid state and para-hydrogen freezing equilibrium |

Constructor and equation pressures use **bara**. `SolidHelmholtzState` returns molar volume
in m3/mol, energies in J/mol, entropy and molar heat capacities in J/(mol K), and the
natural logarithm of the fugacity coefficient. Phase-level extensive getters multiply
these molar properties by the phase mole inventory.

Both equations solve for a mechanically stable volume root and reject invalid or
out-of-range states. Treat a thrown exception or a non-converged freezing result as a
failed calculation; do not extrapolate silently.

## Pure solid-argon state

Use the calibrated system-owned equation when absolute Gibbs energy or entropy matters.
A raw `ArgonSolidHelmholtzEquation` does not contain the reference shifts recovered for
the published sample state.

```java
import neqsim.thermo.phase.PhaseSolidHelmholtzEos;
import neqsim.thermo.system.SystemArgonSolidHelmholtzEos;
import neqsim.thermo.util.solid.SolidHelmholtzState;

SystemArgonSolidHelmholtzEos solid =
    new SystemArgonSolidHelmholtzEos(70.0, 10.0);
solid.init(3);

PhaseSolidHelmholtzEos phase =
    (PhaseSolidHelmholtzEos) solid.getPhase(0);
SolidHelmholtzState state = phase.getSolidState();

double molarVolume = state.getMolarVolume();
double heatCapacityCp = state.getHeatCapacityCp();
```

At 70 K and 10 bara (1 MPa), the current regression obtains approximately
`2.39546e-5 m3/mol` and `30.2861 J/(mol K)`. These values reproduce the rounded
Table 8 state used by the solid-argon implementation; they do not validate another
substance or a mixed phase.

## Para-hydrogen freezing point

Construct the Leachman system for the explicit `"para-hydrogen"` component with solid
checking enabled. `freezingPointTemperatureFlashResult()` returns convergence,
temperature, iteration count, residual, controlling component, and a failure reason. The
call declares `IsNaNException`; catch it or declare it in the surrounding method.

```java
import neqsim.thermo.system.SystemLeachmanEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.saturationops.FreezingPointResult;

SystemLeachmanEos hydrogen =
    new SystemLeachmanEos(13.6, 0.07042, "para-hydrogen", true);
hydrogen.setSolidPhaseCheck("para-hydrogen");

ThermodynamicOperations operations =
    new ThermodynamicOperations(hydrogen);
FreezingPointResult result =
    operations.freezingPointTemperatureFlashResult();

if (!result.isConverged()) {
  throw new IllegalStateException(result.getFailureReason());
}
double freezingTemperatureK = result.getTemperature("K");
double equilibriumResidual = result.getResidual();
```

At the calibrated triple-point pressure, the regression converges to
`13.8033 K` with an absolute dimensionless Gibbs-equilibrium residual below
`1e-10`. The operation updates the system temperature to the converged result.

Do not substitute `"hydrogen"` or `"ortho-hydrogen"` when the Helmholtz solid model is
required. Those spin-isomer choices retain the established empirical pure-solid phase.
For an unsuccessful result, `getTemperature(...)` throws; inspect
`getFailureReason()` instead.

## API ownership

| API | Contract |
| --- | --- |
| `SystemArgonSolidHelmholtzEos(double, double)` | Calibrated one-phase solid-argon system; K and bara |
| `PhaseSolidHelmholtzEos.getSolidState()` | Last initialized immutable molar state |
| `SolidHelmholtzState` getters | SI molar properties and `ln(phi)` |
| `SystemLeachmanEos(double, double, String, boolean)` | Pure hydrogen spin-isomer fluid system; `true` configures a solid phase |
| `ThermodynamicOperations.freezingPointTemperatureFlashResult()` | Structured solid-fluid equilibrium outcome |
| `FreezingPointResult.getTemperature(String)` | Converged temperature in a supported unit; throws after failure |
| `SystemSolidHelmholtzEos` | Single-component extension point for a supplied solid equation |

## Model and engineering boundaries

- The solid systems accept one component only; adding a different component fails.
- Solid argon is currently a property system, not a documented argon melting-curve workflow.
- Para-hydrogen freezing uses the matching Leachman fluid reference and calibrated solid
  reference. Absolute reference energies from unrelated models are not interchangeable.
- The validity ceilings are implementation ranges, not statements of quantified
  uncertainty over the entire range.
- Neither model represents solid mixtures, defects, polymorph competition, nucleation,
  kinetics, deposition, or heat/mass-transfer limitations.
- Screened results require independent data and accountable engineering review before
  use in equipment or safety decisions.

## Related documentation

- [Thermodynamic models](thermodynamic_models.md)
- [Flash calculations](flash_calculations_guide.md)
- [Phase package](phase/README.md)
- [Reading fluid properties](reading_fluid_properties.md)
- [JavaDoc: SystemArgonSolidHelmholtzEos](https://equinor.github.io/neqsim/javadoc/neqsim/thermo/system/SystemArgonSolidHelmholtzEos.html)
- [JavaDoc: FreezingPointResult](https://equinor.github.io/neqsim/javadoc/neqsim/thermodynamicoperations/flashops/saturationops/FreezingPointResult.html)
- [Maltby, Hammer, and Wilhelmsen solid-argon EOS](https://doi.org/10.1063/5.0237497)
