---
title: "Thermodynamic Workflows"
description: "Build, characterize, flash, inspect, and clone NeqSim fluids with source-verified Java APIs and explicit units."
keywords: "thermodynamic workflow, TPflash, TBP fraction, mixing rule, fluid properties, fluid clone"
---

Use this workflow when a calculation needs a characterized fluid, a defined equilibrium
specification, and independently reusable states. Temperature is in K and pressure is absolute
bara unless an API call supplies another unit explicitly.

## Build, flash, and branch a characterized fluid

The complete Java 8 program below adds database components and one TBP pseudo-component, loads
mixture interaction parameters, performs a TP flash, initializes physical properties, and clones
the flashed fluid for a second operating point. Run it with assertions enabled (`java -ea ...`).

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class ThermodynamicWorkflowExample {
  private static final Logger logger =
      LogManager.getLogger(ThermodynamicWorkflowExample.class);

  private ThermodynamicWorkflowExample() {}

  public static void main(String[] args) {
    double temperatureK = 313.15;
    double pressureBara = 80.0;
    SystemInterface fluid = new SystemPrEos(temperatureK, pressureBara);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.05);

    // name, moles, molar mass [kg/mol], specific gravity [-]
    fluid.addTBPfraction("C10", 0.10, 0.134, 0.792);
    fluid.createDatabase(true);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double densityKgM3 = fluid.getDensity("kg/m3");
    double molarMassKgMol = fluid.getMolarMass();
    assert Math.abs(fluid.getTotalNumberOfMoles() - 1.0) < 1.0e-12;
    assert Math.abs(fluid.getTemperature("K") - temperatureK) < 1.0e-10;
    assert Math.abs(fluid.getPressure("bara") - pressureBara) < 1.0e-10;
    assert fluid.getNumberOfPhases() >= 1;
    assert Double.isFinite(densityKgM3) && densityKgM3 > 0.0;
    assert Double.isFinite(molarMassKgMol) && molarMassKgMol > 0.0;

    SystemInterface sweepCase = fluid.clone();
    sweepCase.setTemperature(280.0, "K");
    sweepCase.setPressure(10.0, "bara");
    ThermodynamicOperations sweepOperations = new ThermodynamicOperations(sweepCase);
    sweepOperations.TPflash();
    sweepCase.initProperties();

    assert sweepCase != fluid;
    assert Math.abs(fluid.getTemperature("K") - temperatureK) < 1.0e-10;
    assert Math.abs(fluid.getPressure("bara") - pressureBara) < 1.0e-10;
    assert Math.abs(sweepCase.getTemperature("K") - 280.0) < 1.0e-10;
    assert Math.abs(sweepCase.getPressure("bara") - 10.0) < 1.0e-10;
    assert Double.isFinite(sweepCase.getDensity("kg/m3"));
    assert sweepCase.getDensity("kg/m3") > 0.0;

    logger.info(
        "Base: {} K, {} bara, {} kg/m3, {} g/mol; sweep: {} K, {} bara, {} kg/m3",
        fluid.getTemperature("K"),
        fluid.getPressure("bara"),
        densityKgM3,
        molarMassKgMol * 1000.0,
        sweepCase.getTemperature("K"),
        sweepCase.getPressure("bara"),
        sweepCase.getDensity("kg/m3"));
  }
}
```

`addTBPfraction(name, moles, molarMass, specificGravity)` expects molar mass in kg/mol and a
dimensionless specific gravity in its fourth argument. Values above 1.5 in the fourth argument are
interpreted as kg/m3 and divided by 1000. Use `addPlusFraction(...)` for an unresolved plus
fraction; a TBP cut and a plus fraction do not have the same characterization semantics.

`createDatabase(true)` rebuilds NeqSim's temporary component and interaction tables for the
current component list. It does not infer the molar mass or density of a TBP fraction.

## Choose the thermodynamic model and mixing rule separately

The system class selects the thermodynamic model. The example uses `SystemPrEos` for
Peng-Robinson. `setMixingRule("classic")` separately selects the database-backed classic mixing
rule (`EosMixingRuleType.CLASSIC`, legacy value 2).

Prefer named mixing rules over raw legacy integers. The maintained
[fluid-creation guide](fluid_creation_guide.md#8-mixing-rules) lists current names, compatibility
values, and model-specific recommendations. The legacy value 1 is the no-interaction rule, with
all binary interaction parameters set to zero; it is not the database-backed classic rule.

## Select an equilibrium specification

Create a `ThermodynamicOperations` instance for the fluid being calculated. Operations update
that fluid's state.

| Engineering specification | Source-verified operation | Important basis |
|---|---|---|
| Temperature and pressure | `operations.TPflash()` | Set T and absolute P on the fluid first |
| Pressure and enthalpy | `operations.PHflash(hSpec, "J/kg")` | Set absolute P first and state the enthalpy unit |
| Pressure and entropy | `operations.PSflash(sSpec, "J/kgK")` | Set absolute P first and state the entropy unit |
| Dew-point temperature | `operations.dewPointTemperatureFlash()` | Uses the current composition and pressure; updates T |
| Bubble-point pressure | `operations.bubblePointPressureFlash()` | Uses the current composition and temperature; updates P |
| PT phase envelope | `operations.calcPTphaseEnvelope()` | Inspect convergence and dedicated result accessors |

The one-argument PH and PS overloads use total extensive specifications. Prefer unit-aware
overloads so a molar, mass, or total basis cannot be confused. Saturation and phase-envelope
calculations can fail or become supercritical for some compositions and starting states; handle
exceptions and validate the returned state. See the
[phase-envelope guide](../pvtsimulation/phase_envelope_guide.md) for envelope-specific setup.

Chemical and phase equilibrium require their dedicated model setup and operations; there is no
general `ThermodynamicOperations.calcChemicalEquilibrium()` entry point. Start with the
[reactive-flash guide](reactive_flash.md) for supported reactive workflows.

## Clone independent states for sweeps

Clone a configured and flashed fluid before changing a sweep condition. The clone owns an
independent thermodynamic state, while the original remains at its prior temperature and pressure,
as the executable assertions above demonstrate. Cloning is not JSON export. Select and validate a
serialization format explicitly when state must be persisted or transferred.

## Read, diagnose, and validate results

- Call `initProperties()` after a flash before reading density or transport properties.
- Prefer phase names or `PhaseType` and check phase existence; phase indexes can change after a
  new equilibrium calculation.
- Use `prettyPrint()` for a human-readable state table. Do not assume it reports every binary
  interaction parameter.
- Keep pressure basis and every enthalpy, entropy, density, and flow unit explicit.
- Validate against the quantities relevant to the experiment or process: density,
  compressibility, saturation pressure or temperature, CCE or differential-liberation volumes,
  phase amounts and compositions, and material or energy closure.

Molar mass and Z-factor alone do not validate a characterized petroleum fluid.

The source code does not benchmark a selected model for a particular reservoir fluid. Confirm
model choice, characterized pseudo-components, and calculated properties against representative
laboratory PVT data before using results for design or operating decisions.

For the complete property-initialization and phase-access contract, continue with
[Reading Fluid Properties](reading_fluid_properties.md).
