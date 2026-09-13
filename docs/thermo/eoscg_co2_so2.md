---
title: "EOS-CG CO2/SO2 mixture example"
description: "Executable CO2/SO2 TP-flash example with pressure closure, phase compositions, material balance, and a bounded numerical validation range."
---

# EOS-CG CO2/SO2 mixture example

This example uses 95 mol% CO2 and 5 mol% SO2 at 298.15 K and 50 bara. It requires
the initialization-order repair for [issue #3702](https://github.com/equinor/neqsim/issues/3702).
Earlier implementations formed binary reducing factors before loading the critical
properties of SO2 and the other added EOS-CG components. The resulting infinite
volume factor caused both density-root attempts to fail, including at 350 K and
10 bara. The repair loads all critical properties before forming those factors.
The interaction coefficients, density solver, and rejection of nonconverged
ideal-gas fallback results are unchanged.

## Executable mixture flash

The two phases have different compositions. Report density and mole fraction for
each phase, and reconstruct the feed from the phase mole inventories. A density
root evaluated at the overall feed composition alone is not the equilibrium
liquid density of a two-phase mixture.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemEOSCGEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.gerg.NeqSimEOSCG;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class EosCgCo2So2Example {
  private static final Logger logger = LogManager.getLogger(EosCgCo2So2Example.class);

  private EosCgCo2So2Example() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemEOSCGEos(298.15, 50.0); // K, bara
    fluid.addComponent("CO2", 0.95); // mol
    fluid.addComponent("SO2", 0.05); // mol
    fluid.createDatabase(true);
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);

    assert fluid.getNumberOfPhases() == 2 : "Expected gas and liquid";
    double recoveredCo2 = 0.0;
    double recoveredSo2 = 0.0;
    for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      double density = phase.getDensity("kg/m3");
      double molarDensity = density / (phase.getMolarMass() * 1000.0); // mol/L
      double pressureKPa = new NeqSimEOSCG(phase).getPressure(molarDensity);
      double co2Fraction = phase.getComponent("CO2").getx();
      double so2Fraction = phase.getComponent("SO2").getx();
      assert Double.isFinite(density) && density > 0.0;
      assert Math.abs(pressureKPa - 5000.0) <= 0.005 : "Density must reproduce pressure";
      recoveredCo2 += phase.getNumberOfMolesInPhase() * co2Fraction;
      recoveredSo2 += phase.getNumberOfMolesInPhase() * so2Fraction;
      logger.info("Phase {}; beta {}; x(CO2) {}; x(SO2) {}; density {} kg/m3; pressure {} kPa",
          phase.getType(), phase.getBeta(), co2Fraction, so2Fraction, density, pressureKPa);
    }
    assert Math.abs(recoveredCo2 - 0.95) < 1.0e-9 : "CO2 balance";
    assert Math.abs(recoveredSo2 - 0.05) < 1.0e-9 : "SO2 balance";
  }
}
```

Run with Java assertions enabled (`-ea`). `SystemEOSCGEosCO2SO2Test` also extracts,
compiles, and executes this exact Markdown program with assertions enabled.

## Numerical validation boundary

The following are calculated results from NeqSim 3.20.0 with the #3702 repair,
rounded for display. Pressure is absolute; beta is the molar phase fraction.

| Temperature (K) | Pressure (bara) | Phase | Beta | CO2 mole fraction | Density (kg/m3) |
| ---: | ---: | --- | ---: | ---: | ---: |
| 298.15 | 50.0 | Gas | 0.98747 | 0.95153 | 142.786 |
| 298.15 | 50.0 | Liquid | 0.01253 | 0.82958 | 897.995 |
| 300.00 | 50.0 | Gas | 1.00000 | 0.95000 | 139.220 |
| 298.15 | 48.0 | Gas | 1.00000 | 0.95000 | 132.571 |
| 350.00 | 10.0 | Gas | 1.00000 | 0.95000 | 15.972 |

Regression checks cover these four operating points, both requested density roots
at the original feed composition, pressure closure within the existing relative
tolerance of 1e-6, positive heat capacities, normalized phase compositions,
component balances, and two-phase fugacity agreement. The binary reducing
functions are independently checked against Eqs. (5)-(6) and the CO2/SO2
coefficients in Table 4 of
[Neumann et al. (2023)](https://doi.org/10.1007/s10765-023-03263-6).

These checks establish numerical consistency at the stated points. They are not
an experimental density or phase-equilibrium accuracy benchmark, and they do not
qualify a continuous temperature/pressure envelope or arbitrary impurity mixtures.
Other compositions and operating ranges require their own convergence and
reference-data validation. A failed density solve remains an error; an ideal-gas
fallback is not an accepted EOS-CG state.

See the [GERG-2008 and EOS-CG guide](gerg2008_eoscg) for model selection and the
other introductory examples.
