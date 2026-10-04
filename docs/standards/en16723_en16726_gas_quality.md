---
title: "EN 16726 and EN 16723 Gas-Quality Screening"
description: "Use NeqSim's EN 16726 natural-gas and EN 16723 biomethane composition screens with explicit result keys, embedded limits, and unimplemented measurement boundaries."
---

`Standard_EN16726` and `Standard_EN16723` provide deterministic screens over a
NeqSim gas composition. They are useful for checking the current library behavior and
for building a project-owned quality workflow. They do not reproduce sampling,
laboratory analysis, uncertainty, national annexes, or the complete requirements of a
licensed standard.

The two classes are coupled: EN 16723 Part 1 delegates its base-gas checks to an
internal `Standard_EN16726` instance. Use them only after validating the exact class
behavior below against the governing contract and standards editions.

## Complete Java 8 example

The fixture is a normalized dry biomethane composition. Assertions deliberately use
only result keys implemented by the current classes.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.gasquality.Standard_EN16723;
import neqsim.standards.gasquality.Standard_EN16726;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class EnGasQualityExample {
  private static final Logger logger = LogManager.getLogger(EnGasQualityExample.class);

  private EnGasQualityExample() {}

  public static void main(String[] args) {
    SystemInterface gas = new SystemSrkEos(288.15, 1.01325);
    gas.addComponent("methane", 0.964995);
    gas.addComponent("CO2", 0.020000);
    gas.addComponent("nitrogen", 0.015000);
    gas.addComponent("oxygen", 0.000005);
    gas.setMixingRule("classic");
    new ThermodynamicOperations(gas).TPflash();

    Standard_EN16726 networkGas = new Standard_EN16726(gas);
    networkGas.setNetworkType("transmission");
    networkGas.calculate();

    double wobbeIndex = networkGas.getValue("WobbeIndex");
    double carbonDioxide = networkGas.getValue("CO2");
    assert Double.isFinite(wobbeIndex);
    assert wobbeIndex >= networkGas.getWobbeIndexMin();
    assert wobbeIndex <= networkGas.getWobbeIndexMax();
    assert Math.abs(carbonDioxide - 2.0) < 1.0e-9;
    assert "MJ/m3".equals(networkGas.getUnit("WobbeIndex"));
    assert "mol%".equals(networkGas.getUnit("CO2"));
    assert networkGas.isOnSpec();

    Standard_EN16723 gridInjection = new Standard_EN16723(gas, 1);
    gridInjection.calculate();
    assert Math.abs(gridInjection.getValue("methane") - 96.4995) < 1.0e-9;
    assert Math.abs(gridInjection.getValue("totalInerts") - 3.5) < 1.0e-9;
    assert gridInjection.isOnSpec();

    Standard_EN16723 vehicleFuel = new Standard_EN16723(gas, 2);
    vehicleFuel.calculate();
    assert vehicleFuel.isOnSpec();

    double publishedSiloxaneLimit = gridInjection.getValue("siloxaneLimit");
    assert Math.abs(publishedSiloxaneLimit - 0.3) < 1.0e-12;

    logger.info("EN 16726 screen: WI={} MJ/m3, CO2={} mol%, onSpec={}", wobbeIndex,
        carbonDioxide, networkGas.isOnSpec());
    logger.info("EN 16723 screens: Part 1={}, Part 2={}, siloxane limit={} mg/m3",
        gridInjection.isOnSpec(), vehicleFuel.isOnSpec(), publishedSiloxaneLimit);
  }
}
```

The final siloxane value is an embedded limit, not a calculated contaminant
concentration. The example asserts that distinction in the regression test.

## `Standard_EN16726` implementation boundary

`calculate()` delegates calorific properties to `Standard_ISO6976` using a 0 °C
volume reference and a 25 °C combustion-energy reference. It then reads selected
overall mole fractions from phase 0.

| Result key | Reported unit | Current calculation |
| --- | --- | --- |
| `WobbeIndex` or `WI` | MJ/m3 | Superior Wobbe index from the internal ISO 6976 calculation |
| `GCV` or `grossCalorificValue` | MJ/m3 | Superior calorific value from the internal ISO 6976 calculation |
| `relativeDensity` | dimensionless | Relative density from the internal ISO 6976 calculation |
| `CO2`, `O2`, `H2` | mol% | Phase-0 overall mole fraction multiplied by 100 |
| `H2S` | mg/m3 | Approximate H2S compound concentration at 0 °C and 1 atm |
| `totalSulfur` | mg/m3 | Implementation proxy that adds approximate H2S and COS compound concentrations |
| `water` | mg/m3 | Approximate water concentration at 0 °C and 1 atm |

The two-argument getter ignores its unit argument. Use the fixed units returned by
`getUnit(...)`; it does not convert results. Unknown result keys currently fall back
to the Wobbe index, so validate keys before calling the generic getter.

`isOnSpec()` applies the class's embedded Wobbe-index, relative-density, CO2, H2S,
total-sulfur, O2, and H2 limits. It does **not** calculate or check water dew point,
hydrocarbon dew point, mercaptan sulfur, sampling, measurement uncertainty, or a
complete national specification. The calculated `water` concentration is not part of
the boolean result.

The exact string `"transmission"` selects the transmission O2 limit. Every other
string selects the distribution limit, so validate caller input before invoking
`setNetworkType(...)`. `setH2Limit(...)` changes only the embedded H2 screen and must
be tied to a controlled project requirement.

## `Standard_EN16723` implementation boundary

Part 1 checks CO2, O2, H2, total inerts, and the complete boolean returned by the
internal EN 16726 screen. Part 2 checks only CO2, O2, and methane content. Pass only
`1` or `2`: the current implementation treats every value other than `1` as Part 2.

Supported composition-result keys are `methane`, `methaneContent`, `CO2`, `O2`,
`H2`, `totalInerts`, `WobbeIndex`, and `WI`. The class also exposes
`siloxaneLimit`, `ammoniaLimit`, and `amineLimit` as constants. It does not calculate
those contaminants. Fluorine and chlorine limits exist internally but have no public
result key. None of these trace-contaminant limits participates in `isOnSpec()`.

As with EN 16726, the two-argument getter performs no conversion and an unknown key
falls back to the Wobbe index. Retrieve the coupled base screen with `getEN16726()`
when the individual EN 16726 results are required.

## Fail-closed project workflow

1. Normalize and validate the molar composition before constructing either class.
2. Check that required trace species are represented by qualified measurements; an
   absent component is treated as zero by these implementations.
3. Call `calculate()`, then reject non-finite or implausible values before using
   `isOnSpec()`. Calculation exceptions are logged internally and are not rethrown.
4. Compare every required property with a version-controlled project limit. Add
   measured contaminants, dew points, uncertainty, rounding, and exception handling
   outside these classes.
5. Record the NeqSim version, EOS, composition basis, result key, fixed unit,
   reference conditions, selected part or network type, governing edition, national
   requirements, and accountable reviewer.

Do not report a passing boolean as EN conformity. It is evidence that the properties
implemented by this exact NeqSim class satisfy its embedded screening limits.

## Related documentation

- [Standards package overview](README)
- [ISO 6976 calorific values and Wobbe index](iso6976_calorific_values)
- [Water and hydrocarbon dew-point methods](dew_point_standards)
- [Gas sales contract checks](sales_contracts)
