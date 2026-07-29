---
title: "Thermodynamic Operations"
description: "Run NeqSim flash calculations safely, initialize thermodynamic and transport properties, and select the dedicated workflow for phase envelopes, hydrates, solids, electrolytes, and reactive systems."
keywords: "NeqSim, ThermodynamicOperations, TPflash, PHflash, PSflash, phase equilibrium, flash calculation, thermodynamic properties"
---

`ThermodynamicOperations` solves equilibrium and state-function specifications for a configured
`SystemInterface`. A reliable workflow is:

1. define the fluid and mixing rule;
2. set the known state variables;
3. run the appropriate flash;
4. call `initProperties()` before reading density or transport properties; and
5. check that the resulting phases and engineering values are physical.

## Complete Java quick start

This Java 8 program performs a TP flash at 25 °C and 50 bara, initializes properties, and then
calculates the state after an isenthalpic pressure reduction to 30 bara.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class ThermodynamicOperationsQuickStart {
  private ThermodynamicOperationsQuickStart() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double vaporFraction = fluid.getPhaseFraction("gas", "mole");
    double inletDensity = fluid.getDensity("kg/m3");
    double inletEnthalpy = fluid.getEnthalpy();

    fluid.setPressure(30.0, "bara");
    operations.PHflash(inletEnthalpy);
    fluid.initProperties();

    System.out.printf("Gas fraction: %.6f mol/mol%n", vaporFraction);
    System.out.printf("Inlet density: %.3f kg/m3%n", inletDensity);
    System.out.printf("Outlet temperature: %.3f C%n", fluid.getTemperature("C"));
    System.out.printf(
        "Enthalpy residual: %.6g J%n",
        fluid.getEnthalpy() - inletEnthalpy);
  }
}
```

For this lean-gas case, the focused documentation regression requires a gas fraction above
0.999 mol/mol, an inlet density between 35 and 50 kg/m³, a finite outlet temperature, and
isenthalpic closure within 0.001 J. These are deliberately bounded engineering checks rather than
portable exact output values.

## Choose the flash from the specification

| Operation | Known state | Solved state | Typical use |
|---|---|---|---|
| `TPflash()` | temperature and pressure | phase amounts and compositions | separator or pipeline state |
| `PHflash(H)` | pressure and total enthalpy | temperature and phase equilibrium | valve, heater, or heat exchanger |
| `PSflash(S)` | pressure and total entropy | temperature and phase equilibrium | ideal compressor or expander reference |
| `TVflash(V)` | temperature and total volume | pressure and phase equilibrium | fixed-volume screening |
| `VUflash(V, U)` | total volume and internal energy | temperature, pressure, and phases | dynamic vessel calculations |

The no-unit `PHflash` and `PSflash` overloads use total system enthalpy and entropy in NeqSim's
internal SI representation. Prefer the overloads with explicit unit strings when values originate
outside NeqSim. Volume and energy specifications are extensive quantities, so preserve the
system's material amount when copying a specification between fluids.

## Property initialization boundary

A flash establishes the equilibrium state. It does not guarantee that all transport-property
models have been evaluated. Call `initProperties()` after the flash before retrieving density,
viscosity, thermal conductivity, or interfacial tension.

Use bulk getters such as `fluid.getDensity("kg/m3")` only when a bulk value is meaningful. For
multiphase systems, check phase existence and retrieve a named phase explicitly, for example
`fluid.getPhase("gas").getDensity("kg/m3")`.

Do not use `init(3)` as a replacement for a flash after changing temperature, pressure, or
composition. The flash operation establishes the new equilibrium; `initProperties()` then
initializes thermodynamic and physical properties for the accepted state.

## Specialized equilibrium workflows

The compact quick start above covers state flashes. Use the dedicated guides for operations that
need additional phase configuration, model selection, or result extraction:

- [Flash calculations](flash_calculations_guide.md) — saturation, phase-envelope, critical-point,
  hydrate, and solid operations;
- [Reactive flash](reactive_flash.md) — simultaneous chemical and phase equilibrium;
- [Electrolyte CPA](ElectrolyteCPAModel.md) — electrolyte-system construction and applicability;
- [Hydrate models](hydrate_models.md) — hydrate structures, inhibitors, and formation conditions;
- [Physical properties](physical_properties.md) — density and transport-property models.

There is no generic `electrolyteFlash()`, `calcChemicalEquilibrium()`, or
`calcSolidFormationTemperature()` method on `ThermodynamicOperations`. Select the documented
operation for the physical problem instead of relying on those legacy names.

## Reuse and independent calculations

Reuse one `ThermodynamicOperations` instance while changing the state of the same fluid. Clone the
fluid and construct a separate operations object when two calculations must remain independent.
Always validate phase existence, conservation, units, and convergence before using a result in an
engineering decision.

## Related documentation

- [Fluid creation](fluid_creation_guide.md)
- [Mixing rules](mixing_rules_guide.md)
- [Thermodynamic workflows](thermodynamic_workflows.md)
- [Thermodynamic models](thermodynamic_models.md)
