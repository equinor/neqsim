---
title: "Phase Models and Phase-Level Properties"
description: "Current NeqSim phase-model API boundaries, phase fractions, initialization, phase-type detection, and safe property access."
keywords: "NeqSim, phase model, PhaseInterface, PhaseType, phase fraction, volume fraction, physical properties, hydrate, asphaltene"
---

NeqSim represents each equilibrium phase with a `PhaseInterface`, while the containing
`SystemInterface` owns the phase list, phase-type lookup, and phase-fraction conversions.
Most users should create and flash a thermodynamic system, then inspect the returned interfaces.
Concrete classes such as `PhaseGasEos` are implementation details; casting to them makes an
example model-dependent.

## API ownership at a glance

| Task | Current API | Meaning |
|---|---|---|
| Enumerate phases | `fluid.getNumberOfPhases()`, `fluid.getPhase(index)` | Inspect every phase returned by the latest calculation |
| Detect a type | `fluid.hasPhaseType(PhaseType.GAS)` | Check before type-specific access |
| Retrieve a type | `fluid.getPhase(PhaseType.GAS)` | Obtain the phase without a concrete-class cast |
| Molar phase fraction | `phase.getBeta()` or `fluid.getBeta(index)` | Fraction of total system moles in that phase |
| Volume fraction | `fluid.getVolumeFraction(index)` | Fraction of total system volume |
| Mass fraction | `fluid.getWtFraction(index)` | Fraction of total system mass |
| Thermodynamic properties | `phase.getDensity(unit)`, `getEnthalpy(unit)`, `getCp(unit)`, `getZ()` | Phase-level values after thermodynamic initialization |
| Transport properties | `phase.getViscosity(unit)`, `getThermalConductivity(unit)` | Require physical-property initialization |

There is no `PhaseInterface.getBetaV()`. A molar phase fraction is not interchangeable with a
volume or mass fraction, especially when gas and liquid densities differ greatly.

## Complete phase-inspection example

This Java 8 example uses only public interfaces and logs through Log4j2.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class PhaseInspectionExample {
  private static final Logger logger =
      LogManager.getLogger(PhaseInspectionExample.class);

  private PhaseInspectionExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
    fluid.addComponent("methane", 0.80);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-pentane", 0.05);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    for (int phaseIndex = 0;
        phaseIndex < fluid.getNumberOfPhases();
        phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);

      logger.info(
          "phase={} type={} moleFraction={} volumeFraction={} massFraction={}",
          phaseIndex,
          phase.getType(),
          phase.getBeta(),
          fluid.getVolumeFraction(phaseIndex),
          fluid.getWtFraction(phaseIndex));
      logger.info(
          "density={} kg/m3, z={}, viscosity={} cP, conductivity={} W/mK",
          phase.getDensity("kg/m3"),
          phase.getZ(),
          phase.getViscosity("cP"),
          phase.getThermalConductivity("W/mK"));
    }
  }
}
```

The focused documentation regression test executes the same thermodynamic and property calls. The
example intentionally reports calculated values instead of promising a fixed phase count or fixed
numbers: phase appearance and properties depend on model, composition, temperature, pressure, and
enabled phase checks.

## Initialization sequence

Use the following sequence for phase-equilibrium and property work:

1. Define composition, temperature, pressure, thermodynamic model, and mixing rule.
2. Run the appropriate operation, such as `TPflash()`.
3. Call `fluid.initProperties()` when transport properties are needed.
4. Inspect `getNumberOfPhases()` and phase types before retrieving a specific phase.
5. Re-run the flash and initialization after changing state or composition.

`TPflash()` determines the equilibrium phase split. `initProperties()` initializes the
thermodynamic state and the selected physical-property models. Diffusion is not exposed as a
zero-argument method on `PhaseInterface`; use the initialized physical-properties API described in
the [physical-properties guide](../../physical_properties/README.md).

## Phase types and safe lookup

`PhaseType` currently defines these stable enum names and string descriptors:

| Enum | Descriptor |
|---|---|
| `LIQUID` | `liquid` |
| `GAS` | `gas` |
| `OIL` | `oil` |
| `AQUEOUS` | `aqueous` |
| `HYDRATE` | `gas hydrate` |
| `WAX` | `wax` |
| `SOLID` | `solid` |
| `SOLIDCOMPLEX` | `solidComplex` |
| `ASPHALTENE` | `asphaltene` |
| `LIQUID_ASPHALTENE` | `asphaltene liquid` |

Prefer enum lookup because it avoids descriptor spelling errors:

```java
if (fluid.hasPhaseType(PhaseType.GAS)) {
  PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
  double gasDensity = gas.getDensity("kg/m3");
}
```

Add this import when using the fragment:

```java
import neqsim.thermo.phase.PhaseType;
```

The legacy numeric values exposed by `PhaseType.getValue()` are deprecated and should not be used
as a public modeling contract.

## Fractions, composition, and components

For phase index `p`:

- `fluid.getBeta(p)` and `fluid.getPhase(p).getBeta()` are molar phase fractions.
- `fluid.getVolumeFraction(p)` is the system volume fraction.
- `fluid.getWtFraction(p)` is the system mass fraction.
- `fluid.getPhase(p).getMolarComposition()` returns component mole fractions within that phase.
- `fluid.getPhase(p).getComponent(name)` returns one component in that phase.

Fugacity is available from the phase or through a component's fugacity coefficient. For
activity-coefficient models, use the phase-level
`getActivityCoefficient(componentIndex)` method. `ComponentInterface.getActivity()` and a
generic `PhaseInterface.getIonicStrength()` are not current public APIs.

## Thermodynamic and physical properties

The common phase interface provides:

- state and phase identity: `getTemperature(unit)`, `getPressure(unit)`, `getType()`;
- equation-of-state results: `getZ()`, `getMolarVolume(unit)`, `getFugacity(name)`;
- energy properties: `getEnthalpy(unit)`, `getEntropy(unit)`,
  `getInternalEnergy(unit)`, `getGibbsEnergy()`, and `getHelmholtzEnergy()`;
- heat capacities and acoustic properties: `getCp(unit)`, `getCv(unit)`, and
  `getSoundSpeed(unit)`;
- initialized transport properties: `getDensity(unit)`, `getViscosity(unit)`, and
  `getThermalConductivity(unit)`.

Always state units in engineering-facing output. Methods without a unit argument may use NeqSim's
internal conventions and are less clear in examples.

Generic phase objects do not expose `getdZdT()`, `getdZdP()`, excess enthalpy/entropy/volume,
or component fugacity-derivative getters under the names previously shown on this page. Do not
build workflows around those names. For equilibrium and state-function calculations, use the
supported operations in the [thermodynamic operations guide](../thermodynamic_operations.md).

## Aqueous phases

Check for an aqueous phase before access:

```java
if (fluid.hasPhaseType(PhaseType.AQUEOUS)) {
  PhaseInterface aqueous = fluid.getPhase(PhaseType.AQUEOUS);
  double pH = aqueous.getpH();
  double waterActivityCoefficient =
      aqueous.getActivityCoefficient(aqueous.getComponent("water").getIndex());
}
```

The pH and activity result is model-dependent. Use an electrolyte-capable model and appropriate
chemical-reaction setup when ionic speciation matters; a cubic-EOS water phase alone does not imply
a validated electrolyte calculation.

## Hydrates, wax, solids, and asphaltenes

Additional phases appear only when the corresponding model and calculation are enabled.

- **Hydrate:** configure hydrate checking, run a hydrate-specific operation, then use
  `fluid.hasHydratePhase()`, `fluid.getHydratePhase()`, and
  `fluid.getHydrateFraction()`. See [hydrate flash operations](../../thermodynamicoperations/hydrate_flash_operations.md)
  and [hydrate models](../hydrate_models.md).
- **Wax and generic solids:** use the relevant solid/wax configuration and operation, then inspect
  `hasPhaseType(PhaseType.WAX)` or `hasPhaseType(PhaseType.SOLID)`. There is no generic
  `setWaxCheck()` or `hasWax()` pair on `SystemInterface`.
- **Asphaltene:** inspect `PhaseType.ASPHALTENE` or
  `PhaseType.LIQUID_ASPHALTENE` after the selected characterization/equilibrium workflow. See
  [asphaltene characterization](../characterization/asphaltene_characterization.md). Density,
  viscosity, and phase fraction are calculated results, not fixed constants.

Do not infer a formation temperature from a phase object. Formation-temperature and stability
calculations are separate operations with their own model assumptions and convergence behavior.

## Stability and model-specific APIs

`SystemInterface.checkStability()` is the generic system-level stability entry point. It does not
make nonexistent methods such as `isPhaseStable()` available on the system or phase interfaces.
Advanced derivatives and residual/excess properties vary by model and should be verified against
the exact interface or implementation used.

## Related documentation

- [Fluid creation guide](../fluid_creation_guide.md)
- [Thermodynamic models](../thermodynamic_models.md)
- [Thermodynamic operations](../thermodynamic_operations.md)
- [Physical-properties guide](../../physical_properties/README.md)
- [Hydrate models](../hydrate_models.md)
- [Asphaltene characterization](../characterization/asphaltene_characterization.md)
