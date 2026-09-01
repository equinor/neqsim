---
title: Physical Properties Package
description: Calculate density, viscosity, thermal conductivity, diffusivity, and interfacial properties with NeqSim.
---

NeqSim calculates phase-specific physical properties after a thermodynamic state has been
established. This package provides model sets for common fluids, individual transport-property
models, and interfacial-property calculations.

## Contents

- **Overview** (this page) - Package architecture and basic usage
- [Viscosity Models](viscosity_models) - Dynamic viscosity calculation methods
- [Thermal Conductivity Models](thermal_conductivity_models) - Thermal conductivity methods
- [Diffusivity Models](diffusivity_models) - Binary and multicomponent diffusion coefficients
- [Interfacial Properties](interfacial_properties) - Surface tension and related calculations
- [Density Models](density_models) - Liquid density correlations

## Calculation workflow

Use the physical-property API in this order:

1. Define the fluid and thermodynamic model.
2. Establish the phase equilibrium with a flash calculation.
3. Select a physical-property model set or override an individual phase model.
4. Initialize the affected physical properties.
5. Read properties from a named phase.

The following complete example calculates gas density, dynamic viscosity, kinematic viscosity,
and thermal conductivity. Temperature is in kelvin and pressure is absolute in bara.

```java
import neqsim.physicalproperties.system.PhysicalPropertyModel;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhysicalPropertiesOverview {
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.10);
    fluid.setMixingRule("classic");

    new ThermodynamicOperations(fluid).TPflash();
    fluid.setPhysicalPropertyModel(PhysicalPropertyModel.DEFAULT);
    fluid.initPhysicalProperties();

    double viscosityPas = fluid.getPhase("gas").getViscosity("kg/msec");
    double conductivityWPerMeterK =
        fluid.getPhase("gas").getThermalConductivity("W/mK");
    double densityKgPerM3 = fluid.getPhase("gas").getDensity("kg/m3");
    double kinematicViscosityM2PerS =
        fluid.getPhase("gas").getPhysicalProperties().getKinematicViscosity();

    if (viscosityPas <= 0.0
        || conductivityWPerMeterK <= 0.0
        || densityKgPerM3 <= 0.0
        || kinematicViscosityM2PerS <= 0.0) {
      throw new IllegalStateException("Expected positive gas physical properties");
    }
  }
}
```

`initPhysicalProperties()` is separate from thermodynamic initialization. Re-run it after a
material change in temperature, pressure, composition, phase equilibrium, or selected
physical-property model.

## Preconfigured model sets

`PhysicalPropertyModel` selects a consistent set of phase-specific implementations:

| Model | Intended use |
|---|---|
| `DEFAULT` | General hydrocarbon systems |
| `WATER` | Aqueous systems |
| `SALT_WATER` | Brines |
| `GLYCOL` | Glycol dehydration systems |
| `AMINE` | Amine gas-treating systems |
| `CO2WATER` | CO₂-water systems |
| `BASIC` | Minimal property calculations |

Select the model set before initializing the properties. For example, call
`fluid.setPhysicalPropertyModel(PhysicalPropertyModel.GLYCOL)` and then
`fluid.initPhysicalProperties()`.

The string overload of `initPhysicalProperties` selects a
`PhysicalPropertyType` such as `DYNAMIC_VISCOSITY`; it does not select a
`PhysicalPropertyModel`. Therefore, `initPhysicalProperties("GLYCOL")` is not a model-set
selection call. For compatibility, the legacy keys `DENSITY`, `VISCOSITY`, and `CONDUCTIVITY`
map to `MASS_DENSITY`, `DYNAMIC_VISCOSITY`, and `THERMAL_CONDUCTIVITY`, respectively.

## Overriding individual phase models

Individual models are selected on a phase's `PhysicalProperties` object. Model keys are
case-sensitive. Reinitialize each changed phase after selecting a model.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhaseSpecificPhysicalProperties {
  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("n-pentane", 0.50);
    fluid.setMixingRule("classic");
    fluid.setMultiPhaseCheck(true);

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    if (!fluid.hasPhaseType("gas") || !fluid.hasPhaseType("oil")) {
      throw new IllegalStateException("Expected gas and oil phases");
    }

    fluid.getPhase("gas").getPhysicalProperties()
        .setViscosityModel("friction theory");
    fluid.getPhase("gas").getPhysicalProperties()
        .setConductivityModel("Chung");
    fluid.getPhase("oil").getPhysicalProperties()
        .setViscosityModel("LBC");
    fluid.getPhase("oil").getPhysicalProperties()
        .setConductivityModel("PFCT");

    fluid.getPhase("gas").initPhysicalProperties();
    fluid.getPhase("oil").initPhysicalProperties();

    double gasViscosityCp = fluid.getPhase("gas").getViscosity("cP");
    double oilViscosityCp = fluid.getPhase("oil").getViscosity("cP");
    if (gasViscosityCp <= 0.0 || oilViscosityCp <= 0.0) {
      throw new IllegalStateException("Expected positive phase viscosities");
    }
  }
}
```

The dedicated model pages list the implemented keys, applicability, equations, and tuning
interfaces. Unsupported keys do not have one uniform fallback policy across all property types,
so validate model selection and results in application tests.

## Accessing calculated properties

After initialization, the most common phase accessors are:

| Property | Accessor | Default unit |
|---|---|---|
| Dynamic viscosity | `getViscosity()` | Pa·s |
| Dynamic viscosity with unit | `getViscosity("cP")` | requested unit |
| Thermal conductivity | `getThermalConductivity()` | W/(m·K) |
| Thermal conductivity with unit | `getThermalConductivity("W/mK")` | requested unit |
| Mass density | `getDensity()` | kg/m³ |
| Mass density with unit | `getDensity("kg/m3")` | requested unit |
| Kinematic viscosity | `getPhysicalProperties().getKinematicViscosity()` | m²/s |

Diffusion coefficients are available from the phase's `PhysicalProperties` object. Use
`getDiffusionCoefficient(i, j)` or `getDiffusionCoefficient(component1, component2)` for a
Maxwell-Stefan binary coefficient. Call `calcEffectiveDiffusionCoefficients()` before reading an
effective coefficient with `getEffectiveDiffusionCoefficient(...)`. See the
[diffusivity guide](diffusivity_models) for model selection, Fick coefficients, and complete
access patterns.

Surface tension requires two existing phases and an initialized interfacial model. Use phase
guards and follow the complete examples in the
[interfacial-properties guide](interfacial_properties); do not assume that phase indexes 0 and
1 identify a valid interface.

## Model tuning

Viscosity tuning parameters are model-specific. The LBC, PFCT/CSP, and advanced friction-theory
interfaces are documented and tested in the [viscosity guide](viscosity_models). Use parameter
sets from independent measurements or validated literature, preserve their units and validity
range, and reinitialize the affected phase after changing them.

## Sensitivity calculations

Create a `ThermodynamicOperations` instance for the fluid that is actually being flashed. This is
especially important when cloning a base fluid:

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class PhysicalPropertyTemperatureSweep {
  public static void main(String[] args) {
    SystemInterface baseFluid = new SystemSrkEos(298.15, 50.0);
    baseFluid.addComponent("methane", 0.90);
    baseFluid.addComponent("ethane", 0.10);
    baseFluid.setMixingRule("classic");

    double[] temperaturesK = {280.0, 300.0, 320.0};
    for (double temperatureK : temperaturesK) {
      SystemInterface fluid = baseFluid.clone();
      fluid.setTemperature(temperatureK, "K");

      new ThermodynamicOperations(fluid).TPflash();
      fluid.initPhysicalProperties();

      double viscosityPas = fluid.getPhase("gas").getViscosity("kg/msec");
      if (viscosityPas <= 0.0 || !Double.isFinite(viscosityPas)) {
        throw new IllegalStateException("Invalid gas viscosity");
      }
    }
  }
}
```

## Extending the package

A new physical-property model should:

1. Extend the property-method base class for the correct phase family.
2. Document equations, units, parameter provenance, and validity limits.
3. Register a stable, case-sensitive key in the relevant setter.
4. Add focused tests for selection, calculation, bounds, and unsupported inputs.
5. Add an executable documentation example and update the applicable model guide.

Avoid placeholder implementations that return zero: they can appear numerically valid while
silently corrupting transport or equipment calculations.

## Package architecture

The package separates these responsibilities:

1. `PhysicalPropertyHandler` maps phase types and model sets to property containers.
2. `PhysicalProperties` stores the selected density, viscosity, conductivity, and diffusivity
   methods for one phase.
3. Method packages implement individual correlations and corresponding-states models.
4. Physical-property mixing rules combine component contributions.
5. Interfacial-property classes calculate surface tension and adsorption behavior.

## See also

- [Fluid Creation Guide](../thermo/fluid_creation_guide) - Creating thermodynamic systems
- [Flash Calculations Guide](../thermo/flash_calculations_guide) - Phase-equilibrium calculations
- [Thermodynamic Operations](../thermo/thermodynamic_operations) - Thermodynamic calculation workflow
