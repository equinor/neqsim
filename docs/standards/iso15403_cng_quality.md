---
title: "ISO 15403 - CNG Quality"
description: "Calculate the NeqSim ISO 15403 motor octane and methane-number correlation, with its supported composition and compliance boundaries."
---

NeqSim's `Standard_ISO15403` class calculates a motor octane number (MON) and a
methane-number result for compressed-natural-gas compositions. The class is a
calculation helper; it does not perform a complete conformity assessment against
[ISO 15403-1:2006](https://www.iso.org/standard/44211.html).

## Implemented correlation

The current implementation evaluates

$$MON=137.78x_{CH_4}+29.948x_{C_2H_6}-18.193x_{C_3H_8}-167.062(x_{nC_4}+x_{iC_4})+181.233x_{CO_2}+26.944x_{N_2}$$

and then

$$NM=1.445MON-103.42$$

where each $x_i$ is the overall mole fraction stored by the thermodynamic
system. Call `calculate()` before reading a result. The supported result keys
are `"MON"` and `"NM"`; the usual methane-number abbreviation is not an
accepted getter key.

The six terms above are the complete component coverage of the current class.
Hydrogen, C5+ hydrocarbons, and other unlisted components contribute zero to
the implemented sum, so mixtures containing material amounts of those
components require a method whose validity range covers them.

Pure methane is a useful implementation anchor, not a definition of the
methane-number scale: this correlation returns MON = 137.78 and NM = 95.6721.
A hydrogen-only system would produce NM = -103.42 because hydrogen has no term;
that extrapolation is outside the implemented component coverage and must not
be interpreted as a hydrogen-fuel rating.

## Complete Java example

This example keeps every case on the same one-mole composition basis. The
sensitivity cases replace two mole percentage points of methane with either
carbon dioxide or nitrogen instead of adding material to an existing system.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.gasquality.Standard_ISO15403;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class Iso15403Example {
  private static final Logger logger = LogManager.getLogger(Iso15403Example.class);

  private Iso15403Example() {}

  public static void main(String[] args) {
    Standard_ISO15403 base = new Standard_ISO15403(createCng(0.92, 0.01, 0.01));
    base.calculate();
    double baseMon = base.getValue("MON");
    double baseNm = base.getValue("NM");

    Standard_ISO15403 carbonDioxideCase =
        new Standard_ISO15403(createCng(0.90, 0.03, 0.01));
    carbonDioxideCase.calculate();
    double carbonDioxideNm = carbonDioxideCase.getValue("NM");

    Standard_ISO15403 nitrogenCase =
        new Standard_ISO15403(createCng(0.90, 0.01, 0.03));
    nitrogenCase.calculate();
    double nitrogenNm = nitrogenCase.getValue("NM");

    if (!Double.isFinite(baseMon) || !Double.isFinite(baseNm)) {
      throw new IllegalStateException("ISO 15403 correlation returned a non-finite result");
    }
    if (!(carbonDioxideNm > baseNm && nitrogenNm < baseNm)) {
      throw new IllegalStateException("Unexpected composition-sensitivity result");
    }

    logger.info("Base MON={}, base NM={}", baseMon, baseNm);
    logger.info("NM after replacing methane with CO2={}", carbonDioxideNm);
    logger.info("NM after replacing methane with N2={}", nitrogenNm);
  }

  private static SystemInterface createCng(
      double methane, double carbonDioxide, double nitrogen) {
    SystemInterface gas = new SystemSrkEos(288.15, 200.0);
    gas.addComponent("methane", methane);
    gas.addComponent("ethane", 0.04);
    gas.addComponent("propane", 0.01);
    gas.addComponent("n-butane", 0.005);
    gas.addComponent("i-butane", 0.005);
    gas.addComponent("CO2", carbonDioxide);
    gas.addComponent("nitrogen", nitrogen);
    gas.init(0);
    return gas;
  }
}
```

For these three normalized compositions, the current source correlation gives:

| Case | MON | NM | Engineering interpretation |
|---|---:|---:|---|
| Base composition | 128.18474 | 81.8069493 | Reference case |
| Replace 2 mol% methane with CO2 | 129.05380 | 83.0627410 | Increases this correlation result |
| Replace 2 mol% methane with N2 | 125.96802 | 78.6037889 | Decreases this correlation result |

These trends are properties of the implemented coefficients and the stated
replacement experiment. They are not universal claims about engine knock or
arbitrary dilution paths.

## API and engineering boundaries

| API | Current behavior |
|---|---|
| `new Standard_ISO15403(system)` | Uses the system's overall composition |
| `calculate()` | Updates the stored MON and NM results |
| `getValue("MON")` | Returns the dimensionless motor octane number |
| `getValue("NM")` | Returns the dimensionless methane-number result |
| `getUnit(...)` | Returns an empty string |
| `isOnSpec()` | Always returns `true`; no acceptance limits are evaluated |

Do not use `isOnSpec()` as evidence that a fuel complies with ISO 15403, a
national fuel specification, or an engine manufacturer's limits. A conformity
assessment also needs the applicable standard edition, sampling and analysis
requirements, validated composition range, contractual limits, and any other
required fuel properties.

## Related documentation

- [Standards overview](README.md)
- [ISO 6976 calorific values and Wobbe index](iso6976_calorific_values.md)
- [Dew-point methods](dew_point_standards.md)

