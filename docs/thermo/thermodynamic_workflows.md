---
title: "Thermodynamic Workflows"
description: "Build, characterize, flash, inspect, and clone NeqSim fluids with source-verified Java APIs and explicit units."
keywords: "thermodynamic workflow, TPflash, PHflash, PSflash, TBP fraction, mixing rule, phase envelope, fluid clone"
---

Use the workflow below when a calculation needs a characterized fluid, a defined equilibrium
specification, and reusable results. Temperature is in K and pressure is absolute bara unless an
API call supplies another unit explicitly.

## Build and flash a characterized fluid

This complete example adds ordinary database components and one TBP pseudo-component, loads the
mixture interaction parameters, performs a TP flash, and initializes properties:

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class ThermodynamicWorkflowExample {
  private ThermodynamicWorkflowExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemPrEos(313.15, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);

    // name, moles, molar mass [kg/mol], specific gravity [-]
    fluid.addTBPfraction("C10", 0.10, 0.134, 0.792);

    // Rebuild the temporary component/interaction tables after the component list is complete.
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    System.out.printf("T = %.2f K%n", fluid.getTemperature("K"));
    System.out.printf("P = %.2f bara%n", fluid.getPressure("bara"));
    System.out.printf("phases = %d%n", fluid.getNumberOfPhases());
    System.out.printf("density = %.3f kg/m3%n", fluid.getDensity("kg/m3"));
    System.out.printf("molar mass = %.3f g/mol%n", fluid.getMolarMass() * 1000.0);
  }
}
```

`addTBPfraction(name, moles, molarMass, density)` expects molar mass in kg/mol and
specific gravity (numerically equal to g/cm3) in its fourth argument. Values above 1.5 in the
density position are interpreted as kg/m3 and divided by 1000. Use `addPlusFraction(...)` for an
unresolved plus fraction; a TBP cut and a plus fraction do not have the same characterization
semantics.

`createDatabase(true)` rebuilds NeqSim's temporary component and interaction tables for the
current component list. It does not switch on a separate database service and it does not infer
the molar mass or density of a TBP fraction.

## Choose the thermodynamic model and mixing rule separately

The system class selects the thermodynamic model. The mixing rule controls how mixture parameters
are combined. In the example, `SystemPrEos` selects Peng-Robinson and
`setMixingRule("classic")` selects the database-backed classic rule
(`EosMixingRuleType.CLASSIC`, legacy value 2).

Prefer named mixing rules over raw legacy integers. The maintained
[fluid-creation guide](fluid_creation_guide.md#8-mixing-rules) lists the current names, numeric
compatibility values, and model-specific recommendations. In particular, legacy value 1 is the
no-interaction rule with all binary interaction parameters set to zero; it is not the
database-backed classic rule.

## Select an equilibrium specification

Create one `ThermodynamicOperations` instance for the fluid being calculated. Operations update
that fluid's state.

| Engineering specification | Source-verified operation | Important basis |
|---|---|---|
| Temperature and pressure | `operations.TPflash()` | Set T and absolute P on the fluid first |
| Pressure and enthalpy | `operations.PHflash(hSpec, "J/kg")` | Set absolute P first and state the enthalpy unit |
| Pressure and entropy | `operations.PSflash(sSpec, "J/kgK")` | Set absolute P first and state the entropy unit |
| Dew-point temperature | `operations.dewPointTemperatureFlash()` | Uses the current composition and pressure; updates T |
| Bubble-point pressure | `operations.bubblePointPressureFlash()` | Uses the current composition and temperature; updates P |
| PT phase envelope | `operations.calcPTphaseEnvelope()` | Use the dedicated result accessors and inspect convergence |

The one-argument PH and PS overloads use total extensive specifications. Prefer the unit-aware
overloads in user-facing workflows so a molar, mass, or total basis cannot be confused. Saturation
and phase-envelope calculations can fail or become supercritical for some compositions and
starting states; handle exceptions and validate the returned state. See the
[phase-envelope guide](../pvtsimulation/phase_envelope_guide.md) for envelope-specific setup and
result handling.

Chemical and phase equilibrium require their dedicated model setup and operations; there is no
general `ThermodynamicOperations.calcChemicalEquilibrium()` entry point. Start with the
[reactive-flash guide](reactive_flash.md) for supported reactive workflows.

## Clone independent states for sweeps

Clone a configured and flashed fluid before changing a sweep condition. The clone owns an
independent thermodynamic state; the original remains at its prior temperature and pressure.

```java
SystemInterface sweepCase = fluid.clone();
sweepCase.setTemperature(280.0, "K");
sweepCase.setPressure(10.0, "bara");

ThermodynamicOperations sweepOperations = new ThermodynamicOperations(sweepCase);
sweepOperations.TPflash();
sweepCase.initProperties();
```

Cloning is not JSON export. If a workflow needs persistent or transferable state, select and
validate a serialization format explicitly rather than treating a clone as a stored artifact.

## Read, diagnose, and validate results

- Call `initProperties()` after the flash before reading density or transport properties.
- Prefer phase names or `PhaseType` and check phase existence; phase indexes can change after a
  new equilibrium calculation.
- Use `prettyPrint()` for a human-readable state table. Do not assume it reports every binary
  interaction parameter.
- Keep pressure basis and every enthalpy, entropy, density, and flow unit explicit.
- Validate the quantities relevant to the experiment or process: density and compressibility,
  saturation pressure/temperature, CCE or differential-liberation volumes, phase amounts and
  compositions, and material/energy closure. Molar mass and Z-factor alone do not validate a
  characterized petroleum fluid.
- Compare changed-state sweeps against the untouched original to detect accidental state reuse.

For the complete property initialization and phase-access contract, continue with
[Reading Fluid Properties](reading_fluid_properties.md).
