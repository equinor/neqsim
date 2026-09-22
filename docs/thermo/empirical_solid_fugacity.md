---
title: "Empirical Solid Fugacity References"
description: "Select a liquid or sublimation-pressure reference for ComponentSolid, with explicit density units, domain checks, and solid-selection behavior."
---

# Empirical solid fugacity references

`ComponentSolid.fugcoef(PhaseInterface)` uses the existing liquid-reference
fusion model by default. An enabled component can explicitly select the
sublimation-pressure route with `setUseSolidVaporPressure(true)`.
The choice is per component; it does not enable solid checking by itself.
Methane retains its empirical-phase exclusion in the phase entry point.

```java
SystemSrkEos fluid = new SystemSrkEos(190.0, 2.0);
fluid.addComponent("CO2", 1.0);
fluid.setMixingRule(2);
fluid.setSolidPhaseCheck(true);
PhaseInterface solid = fluid.getPhases()[3];
solid.setTemperature(190.0);
solid.setPressure(2.0);
ComponentSolid carbonDioxide = (ComponentSolid) solid.getComponent("CO2");
carbonDioxide.setUseSolidVaporPressure(true);
double coefficient = carbonDioxide.fugcoef(solid);
```

Imports are `neqsim.thermo.system.SystemSrkEos`,
`neqsim.thermo.phase.PhaseInterface`, and
`neqsim.thermo.component.ComponentSolid`. The example's API path is executed by
`ComponentSolidFugacityTest`.

## Sublimation reference and units

For positive heat of sublimation and triple-point pressure, the route uses
Clausius–Clapeyron. Otherwise it uses the available solid Antoine correlation.
Missing or invalid data raises an exception; a coefficient from a previous
evaluation is never reused as substitute data.

The coefficient is evaluated from

$$\phi_s=\frac{P_{sub}}{P}\phi_v(T,P_{sub})\exp\left[\frac{v_s(P-P_{sub})10^5}{RT}\right]$$

where `P` and `Psub` are in bara, `vs = molarMass / density` is in m3/mol,
`R` is in J/(mol K), and `T` is in K. The pure fluid reference is evaluated
on its gas branch at the sublimation pressure.

The density polynomial returned by `getPureComponentSolidDensity` is in
**kg/m3**, as is the liquid-density polynomial. Valid tabulated solid density
is retained. Only an entirely absent density polynomial uses the legacy
**1000 kg/m3 screening assumption**; a present polynomial returning a
nonpositive or nonfinite density fails. This fallback is an assumption,
not substance-specific density validation.

The sublimation route accepts finite positive pressure and temperature no
higher than the component's triple point. It rejects higher temperatures
instead of silently substituting the triple-point temperature. The published
temperature derivative is a numerical derivative of `ln(phi)` at fixed pressure;
the pressure derivative includes the bar-to-Pa factor in the Poynting term.
The pure reference coefficient has zero composition derivatives. The public
derivative entry points use this selected reference during higher-level initialization.

## Compatibility and boundaries

- The default phase entry point retains the liquid-reference model. Explicitly
  selecting the vapor reference changes that component's equilibrium model;
  check its parameter provenance and validity range before use.
- A component with `doSolidCheck() == false` now publishes the finite exclusion
  coefficient `1e30`. Disabled checks no longer execute a solid correlation.
- The direct two-argument `fugcoef(T, P)` evaluates the sublimation correlation;
  like direct `fugcoef2(phase)`, it does not apply component-selection flags.
- Direct vapor-reference results change where the old implementation discarded
  density. Invalid inputs or missing data now fail explicitly.
- The liquid-reference coefficient is independent of the trial phase mole
  fraction, including a zero trial fraction. This removes a spurious `0/0`.
- Tests verify routing, density units, pressure correction, derivatives,
  parameter changes, and existing CO2 freezing/fluid-equilibrium regressions.
  They do not establish experimental accuracy for every component or solid phase.

See also [experimental solid Helmholtz models](solid_helmholtz_models.md) and
[thermodynamic models](thermodynamic_models.md).
