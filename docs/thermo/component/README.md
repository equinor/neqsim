---
title: "Component Package"
description: "Use NeqSim component names, aliases, pure-component data, phase compositions, and pseudo-components safely."
---

The `neqsim.thermo.component` package stores component identity, pure-component parameters,
composition, and equation-of-state state for a thermodynamic system. Most applications should
work through `SystemInterface`, `PhaseInterface`, and `ComponentInterface`; concrete component
classes are model-specific implementation details.

## Complete component-inspection example

This Java 8 example uses a database alias, performs a TP flash, and inspects both system-level
and phase-level component objects. Temperature is in K, pressure is absolute bara, molar mass is
read in kg/mol, and the log converts it to g/mol only for display.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class ComponentPropertiesExample {
  private static final Logger logger =
      LogManager.getLogger(ComponentPropertiesExample.class);

  private ComponentPropertiesExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 8.0);
    fluid.addComponent("ethane", 1.0);
    fluid.addComponent("2,2,4-trimethylpentane", 1.0);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    ComponentInterface canonical = fluid.getComponent("224-TM-C5");
    ComponentInterface alias = fluid.getComponent("isooctane");
    assert canonical != null;
    assert alias != null;
    assert fluid.hasComponent("ISOOCTANE");
    assert fluid.getPhase(0).getComponent("ISOOCTANE") != null;
    assert canonical.getComponentName().equals(alias.getComponentName());

    ComponentInterface methane = fluid.getComponent("methane");
    assert methane != null;
    assert Double.isFinite(methane.getTC()) && methane.getTC() > 0.0;
    assert Double.isFinite(methane.getPC()) && methane.getPC() > 0.0;
    assert Double.isFinite(methane.getMolarMass()) && methane.getMolarMass() > 0.0;

    double overallFractionSum = 0.0;
    for (int componentIndex = 0;
        componentIndex < fluid.getNumberOfComponents();
        componentIndex++) {
      ComponentInterface component = fluid.getComponent(componentIndex);
      assert Double.isFinite(component.getz()) && component.getz() >= 0.0;
      overallFractionSum += component.getz();
      logger.info(
          "component={} Tc={} K Pc={} bara molarMass={} g/mol z={}",
          component.getComponentName(),
          component.getTC(),
          component.getPC(),
          component.getMolarMass() * 1000.0,
          component.getz());
    }
    assert Math.abs(overallFractionSum - 1.0) < 1.0e-10;

    for (int phaseIndex = 0;
        phaseIndex < fluid.getNumberOfPhases();
        phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      ComponentInterface phaseMethane = phase.getComponent("methane");
      ComponentInterface phaseAlias = phase.getComponent("ISOOCTANE");
      assert phaseMethane != null;
      assert phaseAlias != null;
      assert Double.isFinite(phaseMethane.getx()) && phaseMethane.getx() >= 0.0;
      assert Double.isFinite(phaseMethane.getFugacityCoefficient());
      assert phaseMethane.getFugacityCoefficient() > 0.0;
      logger.info(
          "phase={} type={} methaneX={} methanePhi={}",
          phaseIndex,
          phase.getType(),
          phaseMethane.getx(),
          phaseMethane.getFugacityCoefficient());
    }
  }
}
```

The documentation regression compiles this exact fence with `-source 8 -target 8` and executes
it with assertions enabled. The calculated phase count and property values are intentionally not
fixed: they depend on the selected model, mixing rule, composition, temperature, and pressure.

## Names and aliases

`addComponent`, `hasComponent(String)`, and `getComponent` use the shared component-name resolver.
Recognized aliases, systematic names, and case variants resolve
to the same canonical database name. In the example, `2,2,4-trimethylpentane`, `isooctane`, and
`224-TM-C5` identify the same component.

Unknown, ambiguous, and near-miss inputs are not guessed. Check `hasComponent(name)` before dereferencing a
user-supplied name, and treat a `null` result from `getComponent(name)` as an input-validation
failure. The [component reference list](../component_list#component-name-resolution) documents
the supported name-resolution and mutation rules.

## System and phase component state

The system owns the overall composition while each equilibrium phase owns a phase composition.
After the relevant flash and initialization:

| Access | Typical value | Meaning |
|---|---|---|
| `fluid.getComponent(name)` | `ComponentInterface` | Component with overall mole fraction `getz()` |
| `fluid.getPhase(index).getComponent(name)` | `ComponentInterface` | Component with phase mole fraction `getx()` |
| `getTC()` | K | Critical temperature |
| `getPC()` | bara | Critical pressure |
| `getMolarMass()` | kg/mol | Molar mass |
| `getFugacityCoefficient()` | dimensionless | Phase fugacity coefficient after initialization |

Do not interchange `getz()` and `getx()`. Re-run the thermodynamic operation after changing
temperature, pressure, or composition, and call `initProperties()` before reading transport
properties.

## Database and pseudo-components

Database components carry stored pure-component and model parameters. A TBP fraction added with
`addTBPfraction(name, moles, molarMassKgMol, specificGravity)` is a characterized pseudo-component;
its molar-mass argument is in kg/mol and its specific gravity is dimensionless. A plus fraction
added with `addPlusFraction(name, moles, molarMassKgMol, specificGravity)` represents an unresolved
heavy end. These calls do not turn arbitrary text into a validated database component.

Use the characterization workflow and laboratory data appropriate to the fluid. Pseudo-component
critical properties, boiling points, density, and interaction parameters are model outputs or
inputs with uncertainty; they are not interchangeable with measured pure-component data.

## Model-specific component classes

Concrete classes such as EOS, CPA, PC-SAFT, electrolyte, hydrate, and TBP components expose
additional methods. A cast is valid only when the selected system actually creates that component
implementation. Prefer `ComponentInterface` for portable code, and verify the concrete type and
model assumptions before using implementation-specific parameters.

## Related documentation

- [Component reference list](../component_list)
- [Component database guide](../component_database_guide)
- [Fluid creation guide](../fluid_creation_guide)
- [System package](../system/)
- [Phase package](../phase/)
- [Mixing rules](../mixingrule/)
- [Thermodynamics overview](../)
