---
title: ISO 6578 LNG Density Screening
description: Executable NeqSim ISO 6578 LNG-density calculation with explicit input limits, units, and custody-transfer boundaries.
---

`Standard_ISO6578` calculates LNG density from a molar composition and temperature by
combining tabulated pure-component molar volumes with the implementation's Klosek–McKinley
correction tables. It is useful for reproducible engineering screening. A calculated value is
not evidence that sampling, analysis, temperature measurement, quantity measurement, or a
custody-transfer system complies with ISO 6578 or a commercial contract.

## Implementation contract

Check every input before calling the class:

| Input | Current implementation contract |
| --- | --- |
| Temperature | 93.15–133.15 K (-180 to -140 °C), the ISO table grid |
| Mixture molar mass | 16–30 g/mol, the ISO correction-table grid |
| Composition | Normalized mole fractions |
| Supported names | `methane`, `ethane`, `propane`, `i-butane`, `n-butane`, `i-pentane`/`iC5`, `n-pentane`, `n-hexane`, and `nitrogen` |
| Phase | A representative single LNG liquid; the class reads phase 0 |
| Reported density | kg/m³, returned by `getValue("density")` |

The class does not reject an out-of-range temperature, unsupported component, non-normalized
composition, unsuitable molar mass, or wrong phase. It also does not perform a flash or prove
that the sample is a stable single liquid. Validate those conditions upstream. The pressure
used to construct the NeqSim system is in absolute bara; it is not an independent argument to
the tabulated density correlation.

For the default ISO table, the implemented volume and density calculation is

$$V_{\mathrm{mix}}=\sum_i x_iV_i-\left[K_1+(K_2-K_1)\frac{x_{N_2}}{0.0425}\right]\frac{x_{CH_4}}{1000}$$

$$\rho=\frac{1000M_{\mathrm{mix}}}{V_{\mathrm{mix}}}$$

where $x_i$ is mole fraction, $V_i$ is the tabulated component molar volume in dm³/mol,
$M_{\mathrm{mix}}$ is in kg/mol, and the result $\rho$ is in kg/m³. $K_1$ and $K_2$ are
interpolated on temperature and mixture molar mass. These equations describe the current
NeqSim implementation; use the governing standard edition for normative definitions and
rounding rules.

## Executable LNG-density screen

Compile and run this complete Java 8 program with assertions enabled, for example
`java -ea Iso6578LngDensityExample`.

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.standards.gasquality.Standard_ISO6578;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class Iso6578LngDensityExample {
  private static final Logger logger =
      LogManager.getLogger(Iso6578LngDensityExample.class);
  private static final double MINIMUM_TEMPERATURE_K = 93.15;
  private static final double MAXIMUM_TEMPERATURE_K = 133.15;

  private Iso6578LngDensityExample() {}

  private static SystemInterface createLng(double temperatureK) {
    if (temperatureK < MINIMUM_TEMPERATURE_K
        || temperatureK > MAXIMUM_TEMPERATURE_K) {
      throw new IllegalArgumentException("temperature outside ISO table grid");
    }

    SystemInterface lng = new SystemSrkEos(temperatureK, 1.0);
    lng.addComponent("nitrogen", 0.006538);
    lng.addComponent("methane", 0.918630);
    lng.addComponent("ethane", 0.058382);
    lng.addComponent("propane", 0.011993);
    lng.addComponent("n-butane", 0.003255);
    lng.addComponent("i-pentane", 0.000657);
    lng.addComponent("n-pentane", 0.000545);
    lng.setMixingRule("classic");
    lng.init(0);
    return lng;
  }

  private static double densityKgPerM3(double temperatureK) {
    Standard_ISO6578 standard =
        new Standard_ISO6578(createLng(temperatureK));
    standard.calculate();
    assert "kg/m^3".equals(standard.getUnit("density"));
    return standard.getValue("density");
  }

  public static void main(String[] args) {
    double coldDensityKgPerM3 = densityKgPerM3(108.15);
    double referenceDensityKgPerM3 = densityKgPerM3(113.15);
    double warmDensityKgPerM3 = densityKgPerM3(118.15);

    assert Double.isFinite(referenceDensityKgPerM3);
    assert referenceDensityKgPerM3 > 400.0;
    assert referenceDensityKgPerM3 < 500.0;
    assert coldDensityKgPerM3 > referenceDensityKgPerM3;
    assert referenceDensityKgPerM3 > warmDensityKgPerM3;

    logger.info(
        "LNG density at 113.15 K is {} kg/m^3; 108.15/118.15 K bounds are {}/{} kg/m^3",
        referenceDensityKgPerM3,
        coldDensityKgPerM3,
        warmDensityKgPerM3);
  }
}
```

The temperature comparison keeps composition fixed and checks the expected local trend; it is
not an uncertainty analysis. Record the composition basis, measurement uncertainty, temperature,
NeqSim version, class, table selection, and result unit with every reported value.

## API and reporting boundaries

- `getValue(String)` and `getValue(String, String)` currently return the same stored density.
  The requested parameter and unit strings are not validated or converted. Use
  `getValue("density")`, confirm `getUnit("density")`, and convert outside the class if needed.
- `isOnSpec()` returns `true` unconditionally. Compare the density with an explicit contractual
  limit only after applying the contract's sampling, reference-condition, uncertainty, and
  rounding requirements.
- `useISO6578VolumeCorrectionFacotrs(true)` (spelling preserved from the public API) selects the
  ISO table and is the default. Passing `false` selects a legacy alternative internal table;
  do not label that result ISO 6578 without separate provenance and validation.
- The tabulated component set and interpolation grids are implementation limits, not permission
  to extrapolate. Unsupported components require a documented, independently validated method.
- Qualify important results against traceable reference data or a certified calculation system
  before fiscal, inventory, or custody-transfer use.

## References

- ISO 6578:2017, *Refrigerated hydrocarbon liquids — Static measurement — Calculation
  procedure*.
- Klosek, J., and McKinley, C. (1968), *Densities of Liquefied Natural Gas and of Low Molecular
  Weight Hydrocarbons*.

