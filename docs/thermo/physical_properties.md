---
title: "Physical Property Calculations"
description: "Use NeqSim's phase-specific density and transport-property APIs without confusing thermodynamic, transport, diffusion, or interfacial models."
keywords: "physical properties, transport properties, viscosity, thermal conductivity, surface tension, diffusion coefficient, density, PhysicalPropertyModel"
---

NeqSim separates the thermodynamic state from phase-specific transport and interfacial
properties. Establish equilibrium first, then initialize the physical-property calculations that
depend on that state. The maintained [physical-properties package guide](../physical_properties/README.md)
contains complete, executable Java examples and the model tables.

## Establish the thermodynamic state first

A flash calculation determines the stable phases and their compositions at the requested
conditions. After the flash, `fluid.initPhysicalProperties()` calculates density and transport
properties for the phases that exist.

Use this order:

1. Define temperature, absolute pressure, composition, equation of state, and EOS mixing rule.
2. Run the applicable flash, such as `new ThermodynamicOperations(fluid).TPflash()`.
3. Select a `PhysicalPropertyModel` set or an individual phase transport model.
4. Call `fluid.initPhysicalProperties()`.
5. Read results from a named phase.

If temperature, pressure, composition, or phase-equilibrium settings change materially, run the
appropriate flash again before reinitializing physical properties. Calling
`initPhysicalProperties()` alone does not perform a new equilibrium calculation.

## Thermodynamic and physical-property APIs are different

Thermodynamic quantities such as compressibility factor, enthalpy, heat capacity, and phase
equilibrium come from the active thermodynamic model. Transport-property initialization does not
replace `init(...)` or a flash calculation for those quantities.

The common phase accessors after physical-property initialization are:

| Quantity | Supported phase accessor |
|---|---|
| Mass density | `fluid.getPhase("gas").getDensity("kg/m3")` |
| Dynamic viscosity | `fluid.getPhase("gas").getViscosity("kg/msec")` |
| Thermal conductivity | `fluid.getPhase("gas").getThermalConductivity("W/mK")` |
| Kinematic viscosity | `fluid.getPhase("gas").getPhysicalProperties().getKinematicViscosity()` |

Always identify the phase by type or name and guard for its existence. Phase indexes can change
when the equilibrium state changes.

## Select the transport-property model explicitly

`fluid.setPhysicalPropertyModel(PhysicalPropertyModel.DEFAULT)` selects a coordinated model set.
Other supported sets include `WATER`, `SALT_WATER`, `GLYCOL`, `AMINE`, `CO2WATER`, and
`BASIC`. Select the set before calling `initPhysicalProperties()`.

An EOS mixing rule, such as `fluid.setMixingRule("classic")`, configures thermodynamic mixture
behavior. It is not a viscosity or conductivity model selector. For a specialized transport
correlation, select the model on the phase's `PhysicalProperties` object and reinitialize that
phase. See the [package overview](../physical_properties/README.md),
[viscosity guide](../physical_properties/viscosity_models.md), and
[thermal-conductivity guide](../physical_properties/thermal_conductivity_models.md).

## Diffusion coefficients

Diffusion coefficients are exposed by the phase's `PhysicalProperties` object. After selecting a
diffusion model and reinitializing the phase, use the public accessors with component indexes or
names:

```java
double methaneEthaneDiffusivity =
    fluid.getPhase("gas").getPhysicalProperties()
        .getDiffusionCoefficient("methane", "ethane");
```

For effective multicomponent diffusivity, call `calcEffectiveDiffusionCoefficients()` before
`getEffectiveDiffusionCoefficient(...)`. The
[diffusivity guide](../physical_properties/diffusivity_models.md) documents model keys, equations,
units, and executable access patterns.

## Surface and interfacial tension

Interfacial calculations belong to `InterphasePropertiesInterface`, not directly to
`SystemInterface`. Confirm that both phases exist, initialize physical properties, and access the
interface through the system:

```java
double surfaceTensionNPerM =
    fluid.getInterphaseProperties().getSurfaceTension(0, 1);
```

Phase numbers must refer to the intended pair in the current equilibrium state. The
[interfacial-properties guide](../physical_properties/interfacial_properties.md) shows named model
selection, phase guards, units, and complete examples.

## Engineering use

Transport and interfacial correlations have component, phase, temperature, pressure, and
composition limits. Validate the selected model against measurements or traceable literature for
the intended range. Reinitializing a model makes a calculation current; it does not establish that
the correlation is accurate for a particular design case.

## Related guides

- [Reading fluid properties](reading_fluid_properties.md)
- [Physical-properties package guide](../physical_properties/README.md)
- [Flash calculations](flash_calculations_guide.md)
- [Fluid creation](fluid_creation_guide.md)
