---
title: "Thermodynamic Operations Package"
description: "Verified NeqSim Java examples for TP, PH, PS, reactive, saturation, solid-aware, and phase-envelope thermodynamic operations."
---

`ThermodynamicOperations` is the public facade for equilibrium calculations on a
NeqSim `SystemInterface`. It changes the attached fluid in place: set the known
state variables on the fluid, call an operation, and then read the solved state
from that same fluid.

## Units and initialization

- Constructors use kelvin for temperature and bara for pressure.
- Prefer unit-qualified setters such as `setPressure(30.0, "bara")`.
- Add all components and set the mixing rule before constructing the operations
  facade.
- Call `initProperties()` after an equilibrium calculation before reading
  transport properties such as viscosity or thermal conductivity.
- Extensive properties such as enthalpy and entropy depend on the amount of
  fluid in the system. Preserve the same system amount when using them as PH or
  PS flash targets.

## Verified state-flash example

The following Java 8 program exercises TP, PH, and PS flashes. The residual
checks demonstrate the constraints imposed by the PH and PS operations.

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class StateFlashExample {
  private StateFlashExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
    fluid.addComponent("methane", 0.90);
    fluid.addComponent("ethane", 0.07);
    fluid.addComponent("propane", 0.03);
    fluid.setMixingRule("classic");

    ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
    operations.TPflash();
    fluid.initProperties();

    double initialEnthalpy = fluid.getEnthalpy();
    fluid.setPressure(30.0, "bara");
    operations.PHflash(initialEnthalpy);
    if (Math.abs(fluid.getEnthalpy() - initialEnthalpy) > 1.0e-3) {
      throw new IllegalStateException("PH flash did not preserve enthalpy");
    }

    double initialEntropy = fluid.getEntropy();
    fluid.setPressure(70.0, "bara");
    operations.PSflash(initialEntropy);
    if (Math.abs(fluid.getEntropy() - initialEntropy) > 1.0e-6) {
      throw new IllegalStateException("PS flash did not preserve entropy");
    }
  }
}
```

Common state flashes exposed by the facade include:

| Method | Fixed variables | Typical use |
| --- | --- | --- |
| `TPflash()` | Temperature, pressure | Equilibrium state at specified conditions |
| `PHflash(H)` | Pressure, total enthalpy | Throttling and heat-duty calculations |
| `PSflash(S)` | Pressure, total entropy | Isentropic compression or expansion |
| `PUflash(U)` | Pressure, total internal energy | Pressure-constrained energy balance |
| `TVflash(V)` | Temperature, volume | Rigid-volume state calculation |
| `VUflash(V, U)` | Volume, total internal energy | Closed-vessel dynamic update |

`TVfractionFlash(fraction)` constrains the volume fraction of the first,
lightest phase. It is not a general molar vapour-fraction specification; inspect
the resulting phase types before interpreting the fraction.

## Saturation and phase-aware operations

The same facade provides the following public operations:

| Purpose | Method or setup |
| --- | --- |
| Bubble pressure at current temperature | `bubblePointPressureFlash(false)` |
| Bubble temperature at current pressure | `bubblePointTemperatureFlash()` |
| Dew pressure at current temperature | `dewPointPressureFlash()` |
| Dew temperature at current pressure | `dewPointTemperatureFlash()` |
| Water dew temperature | `waterDewPointTemperatureFlash()` |
| Hydrate formation temperature | `hydrateFormationTemperature()` |
| Hydrate formation pressure | `hydrateFormationPressure()` |
| Multiliquid equilibrium | Enable `setMultiPhaseCheck(true)`, then call `TPflash()` |
| Wax or other configured solid | Call `setSolidPhaseCheck(name)`, then `TPflash()` |

Solid checking is a fluid configuration used by `TPflash()`; there is no public
`TPsolidflash()` method on `ThermodynamicOperations`. Hydrate calculations also
require a fluid model and components suitable for hydrate equilibrium. See the
[thermodynamic model guide](../thermo/thermodynamic_models.md) before selecting an
equation of state.

## PT phase envelope

`calcPTphaseEnvelope()` calculates the pressure-temperature phase boundary. Use
the facade's named result accessor for the extrema; the internal operation
interface does not expose `getCricondenbar()` or `getCricondentherm()` methods.

```java
SystemInterface fluid = new SystemSrkEos(280.0, 10.0);
fluid.addComponent("methane", 0.75);
fluid.addComponent("ethane", 0.12);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-butane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
operations.calcPTphaseEnvelope();

double[] cricondenbar = operations.get("cricondenbar");
double[] cricondentherm = operations.get("cricondentherm");
double cricondenbarPressureBara = cricondenbar[1];
double cricondenthermTemperatureK = cricondentherm[0];
```

Each named extremum is returned as `[temperature K, pressure bara]`. Phase
envelopes are iterative and may be sensitive near critical conditions. Validate
the returned curve and extrema rather than relying only on successful method
completion. The facade does not currently expose a public PH-envelope method.

## Reactive TP flash

Use `reactiveTPflash()` for a system containing components registered in
NeqSim's chemical-reaction data. The example below uses a single vapour phase for
the water-gas-shift system.

```java
SystemInterface reactive = new SystemSrkEos(600.0, 1.0);
reactive.addComponent("CO", 0.25);
reactive.addComponent("water", 0.25);
reactive.addComponent("CO2", 0.25);
reactive.addComponent("hydrogen", 0.25);
reactive.setMixingRule("classic");
reactive.setMaxNumberOfPhases(1);
reactive.setNumberOfPhases(1);
reactive.init(0);
reactive.init(1);

ThermodynamicOperations operations = new ThermodynamicOperations(reactive);
operations.reactiveTPflash();

double compositionSum = 0.0;
for (int i = 0; i < reactive.getPhase(0).getNumberOfComponents(); i++) {
  compositionSum += reactive.getPhase(0).getComponent(i).getx();
}
if (Math.abs(compositionSum - 1.0) > 1.0e-10) {
  throw new IllegalStateException("Reactive composition does not close");
}
```

There are no `setChemicalReactions(true)` or `calcChemicalEquilibrium()` methods
on these public interfaces. For reaction selection, phase constraints, and
reactive PH/PS operations, see the [reactive flash guide](../thermo/reactive_flash.md).

## Convergence and result checks

`ThermodynamicOperations` does not provide generic `setMaxIterations(...)` or
`setTolerance(...)` methods. Solver controls are operation-specific and are not
interchangeable across all flash types. Prefer physical validation of each
result:

1. Check that temperature, pressure, phase count, and phase types are plausible.
2. Check that phase compositions and phase fractions close to one.
3. For PH, PS, PU, or VU flashes, compare the solved extensive-property residual
   with a tolerance appropriate to the system amount and application.
4. Test nearby states to detect branch switching or critical-region sensitivity.
5. Treat non-convergence as a model, initialization, or state-specification issue;
   do not silently accept the last iterate.

The legacy `OLGApropertyTableGenerator` is not a portable general-purpose export
API: it does not expose filename or water-cut setters and its current writer has
platform-specific behavior. Do not build new workflows around the obsolete
example previously shown on this page.

## Related documentation

- [Thermodynamics overview](../thermo/README.md)
- [Reading fluid properties](../thermo/reading_fluid_properties.md)
- [Thermodynamic model selection](../thermo/thermodynamic_models.md)
- [Reactive flash calculations](../thermo/reactive_flash.md)
- [Thermodynamics cookbook recipes](../cookbook/thermodynamics-recipes.md)
