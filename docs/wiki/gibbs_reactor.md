---
title: "Gibbs Reactor"
description: "Run and qualify Gibbs free-energy equilibrium calculations with NeqSim's GibbsReactor, explicit units, solver diagnostics, and engineering boundaries."
---

`GibbsReactor` calculates a thermodynamic-equilibrium outlet by minimizing Gibbs free energy while conserving the supported elements. Use it for equilibrium screening when the feed species, thermodynamic model, temperature/pressure basis, and allowed product species are explicitly defined. It is not a kinetic, residence-time, catalyst, emissions, or equipment-design model.

## Execution contract

The example below is one complete Java 8 program. It uses an isothermal methane/oxygen fixture already exercised by the reactor regression suite. Temperatures are in K, pressure is absolute bara, and reported compositions are dimensionless mole fractions.

```java
package examples;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class GibbsReactorGuideExample {
  private static final Logger logger = LogManager.getLogger(GibbsReactorGuideExample.class);

  private GibbsReactorGuideExample() {}

  public static void main(String[] args) {
    SystemInterface feedFluid = new SystemSrkEos(1300.0, 1.0);
    feedFluid.addComponent("methane", 0.1);
    feedFluid.addComponent("oxygen", 0.4);
    feedFluid.addComponent("CO2", 0.0);
    feedFluid.addComponent("water", 0.0);
    feedFluid.setMixingRule(2);

    Stream feed = new Stream("equilibrium feed", feedFluid);
    feed.setTemperature(1300.0, "K");
    feed.setPressure(1.0, "bara");
    feed.run();

    GibbsReactor reactor = new GibbsReactor("isothermal equilibrium reactor", feed);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setEnergyMode(GibbsReactor.EnergyMode.ISOTHERMAL);
    reactor.setUseAdaptiveStepSize(true);
    reactor.setMinIterations(3);
    reactor.setMaxIterations(10000);
    reactor.setConvergenceTolerance(1.0e-3);
    reactor.run();

    if (!reactor.hasConverged()) {
      throw new IllegalStateException(
          "Gibbs solver did not converge; final error=" + reactor.getFinalConvergenceError());
    }
    if (!reactor.getElementMassBalanceConverged()) {
      throw new IllegalStateException(
          "Element balance did not converge; error=" + reactor.getElementMassBalanceError()
              + " percent");
    }

    SystemInterface outlet = reactor.getOutletStream().getThermoSystem();
    double outletTemperatureK = outlet.getTemperature("K");
    double outletPressureBara = outlet.getPressure("bara");
    double methaneMoleFraction = outlet.getComponent("methane").getz();
    double carbonDioxideMoleFraction = outlet.getComponent("CO2").getz();
    double waterMoleFraction = outlet.getComponent("water").getz();

    requireFiniteFraction("methane", methaneMoleFraction);
    requireFiniteFraction("CO2", carbonDioxideMoleFraction);
    requireFiniteFraction("water", waterMoleFraction);
    if (Math.abs(outletTemperatureK - 1300.0) > 1.0e-6) {
      throw new IllegalStateException("Isothermal outlet temperature changed unexpectedly");
    }

    assert reactor.hasConverged();
    assert reactor.getElementMassBalanceConverged();
    assert carbonDioxideMoleFraction > 0.0;
    assert waterMoleFraction > 0.0;

    logger.info(
        "Equilibrium screen: T={} K, P={} bara, methane={}, CO2={}, water={}, iterations={}",
        outletTemperatureK, outletPressureBara, methaneMoleFraction,
        carbonDioxideMoleFraction, waterMoleFraction, reactor.getActualIterations());
    logger.info(
        "Qualification boundary: equilibrium composition is a screening result, not kinetic, "
            + "residence-time, catalyst, emissions, or equipment-design evidence.");
  }

  private static void requireFiniteFraction(String component, double value) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalStateException(component + " mole fraction must be finite and in [0, 1]");
    }
  }
}
```

The zero-amount `CO2` and `water` entries deliberately make those products available while `setUseAllDatabaseSpecies(false)` limits the candidate set to species already present in the thermodynamic system. A converged numerical flag is necessary but not sufficient: the example also checks supported-element closure, bounded outputs, and the isothermal temperature contract.

## Energy modes

| Mode | Calculation contract |
| --- | --- |
| `ISOTHERMAL` | Keeps the reactor temperature at the inlet value and reports the calculated heat effect through the reactor diagnostics. |
| `ADIABATIC` | Adjusts temperature to satisfy the implemented enthalpy balance. Validate the resulting temperature, phase state, and species range independently. |

Select the enum form when possible. The string setter accepts only `"isothermal"` and `"adiabatic"` case-insensitively and rejects other values.

## Species ownership

By default, `useAllDatabaseSpecies` is false. In that mode the caller owns the candidate-species list and should add allowed products at zero amount before running the reactor. Setting it true admits all species that the implementation can load from the packaged Gibbs database; that broader search can change the solution space and must be qualified for the intended chemistry.

Components absent from the Gibbs database are not automatically given reaction data. Treat database coverage, aliases, elemental definitions, thermodynamic-model selection, and phase validity as input responsibilities.

## Solver configuration

| Setting | Default | Meaning |
| --- | ---: | --- |
| Maximum iterations | 5000 | Upper iteration limit |
| Convergence tolerance | $10^{-3}$ | Implemented solver convergence criterion |
| Composition damping | 0.05 | Fixed composition step when adaptive sizing is disabled |
| Minimum iterations | 100 | Iterations required before convergence may be declared |
| Adaptive step sizing | false | Enables implementation-provided step limiting |
| Armijo line search | false | Enables backtracking of the trial step |
| Regularization | false | Enables conditioning support for the constrained Jacobian |

Do not tighten tolerances or increase iteration limits merely to force a nominal pass. Record the final error and iteration count, then investigate species coverage, feed state, phase behavior, damping, and initialization.

## Diagnostics and units

| Method | Reported quantity |
| --- | --- |
| `hasConverged()` | Solver convergence flag |
| `getFinalConvergenceError()` | Final implemented convergence metric |
| `getMassBalanceError()` | Relative stream-mass difference in percent |
| `getElementMassBalanceError()` | Supported-element-derived relative mass difference in percent |
| `getEnthalpyOfReactions()` | Cumulative implemented reaction-enthalpy diagnostic in kJ |
| `getTemperatureChange()` | Cumulative implemented temperature-change diagnostic in K |
| `getPower("W"|"kW"|"MW")` | Signed heat-effect diagnostic in the requested supported unit |
| `getGibbsEnergyHistory()` | Recorded iteration history for diagnostic review |
| `getConditionNumberHistory()` | Recorded constrained-system condition estimates |
| `getStepSizeHistory()` | Recorded solver step sizes |
| `getElementBalanceErrorHistory()` | Recorded supported-element balance error history |

The cumulative enthalpy and temperature-change values are solver diagnostics. They are not, by themselves, a rated reactor duty, a transient temperature rise, or a substitute for an energy-balance and equipment-design review.

## Troubleshooting

- Fail closed when `hasConverged()` is false or a balance/result is non-finite.
- Confirm every allowed reactant and product has valid Gibbs-database data.
- Keep pressure on an absolute basis and label temperature units explicitly.
- Check both stream-mass and supported-element closure for reacting systems.
- Compare fixed damping and adaptive sizing on a controlled fixture before changing defaults.
- Treat a broadened all-database species search as a model change, not a harmless convenience.
- Recheck phase stability and the chosen equation of state across the intended operating envelope.

## Qualification boundary

Gibbs minimization predicts equilibrium for the selected species and thermodynamic model. It does not establish reaction rates, residence time, catalyst activity or deactivation, mixing limits, flame stability, pollutant formation, heat-transfer area, metallurgy, relief adequacy, controls, or safe operating limits. Validate chemistry, thermodynamic data, operating envelope, and equipment design with accountable engineering evidence.

For algorithm details and the broader API, see the [maintained Gibbs reactor reference](../process/gibbs-reactor-documentation.md) and the [reactor equipment overview](../process/equipment/reactors.md).
