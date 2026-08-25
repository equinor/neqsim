---
title: "Standards Package"
description: "Use NeqSim implementations of ISO gas-quality, LNG-density, dew-point, and ASTM oil-quality calculation methods with explicit reference conditions and engineering boundaries."
---

The `neqsim.standards` package calculates gas- and oil-quality properties from a
NeqSim fluid. These calculations support engineering screening and contract
workflows; they do not replace representative sampling, validated composition
analysis, a certified laboratory method, or the governing contract.

## Choose a calculation

| Need | NeqSim class | Guide |
| --- | --- | --- |
| Calorific value, relative density, and Wobbe index | `Standard_ISO6976` or `Standard_ISO6976_2016` | [ISO 6976](iso6976_calorific_values) |
| LNG density from composition | `Standard_ISO6578` | [ISO 6578](iso6578_lng_density) |
| Water or hydrocarbon dew point | `Draft_ISO18453` or `BestPracticeHydrocarbonDewPoint` | [Dew-point methods](dew_point_standards) |
| CNG methane number and motor octane number | `Standard_ISO15403` | [ISO 15403](iso15403_cng_quality) |
| Simulated crude-oil vapour pressure | `Standard_ASTM_D6377` | [ASTM D6377](astm_d6377_rvp) |
| Other simulated oil-quality properties | Classes in `neqsim.standards.oilquality` | [Oil-quality methods](oil_quality_standards) |
| Delivery-point specification checks | `BaseContract` and `ContractSpecification` | [Sales contracts](sales_contracts) |

Use the edition and reference conditions named by the applicable contract or
regulation. A class name identifies the implemented calculation route; it is not
by itself evidence that the complete measurement system is compliant.

## ISO 6976 gas-quality quick start

The example reports superior calorific value and superior Wobbe index on a real-gas
volumetric basis. Volume reference temperature is 0°C and combustion-energy
reference temperature is 15.55°C (60°F).

```java
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface gas = new SystemSrkEos(293.15, 1.0);
gas.addComponent("methane", 0.931819);
gas.addComponent("ethane", 0.025618);
gas.addComponent("nitrogen", 0.010335);
gas.addComponent("CO2", 0.015391);
gas.setMixingRule("classic");
new ThermodynamicOperations(gas).TPflash();

Standard_ISO6976 iso6976 =
    new Standard_ISO6976(gas, 0.0, 15.55, "volume");
iso6976.setReferenceState("real");
iso6976.calculate();

double gcvMJPerNm3 = iso6976.getValue("GCV") / 1000.0;
double wobbeMJPerNm3 =
    iso6976.getValue("SuperiorWobbeIndex") / 1000.0;
double relativeDensity = iso6976.getValue("RelativeDensity");
```

Expected values for this fixture are approximately 39.615 MJ/Nm³,
51.701 MJ/Nm³, and 0.5871. Report both reference temperatures, reference state,
and basis with every result. Supported combustion-energy reference temperatures
are 0, 15, 15.55, 20, and 25°C. Although `checkReferenceCondition()` currently
accepts 25°C as a volume reference temperature, volume-dependent corrections
are implemented only for 0, 15, 15.55, and 20°C; use one of those four values.

`GCV` and `LCV` are aliases for `SuperiorCalorificValue` and
`InferiorCalorificValue`. `WI` and `WobbeIndex` are aliases for
`SuperiorWobbeIndex`. `StandardInterface` declares the generic
`getValue(...)` methods, but each concrete standard defines which parameter
names and units it supports.

## ISO 6578 LNG-density quick start

ISO 6578 uses the liquid temperature and molar composition. The supported
component set and temperature range are method limits, so screen the input before
using the result.

```java
import neqsim.standards.gasquality.Standard_ISO6578;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface lng = new SystemSrkEos(113.15, 1.0);
lng.addComponent("nitrogen", 0.006538);
lng.addComponent("methane", 0.918630);
lng.addComponent("ethane", 0.058382);
lng.addComponent("propane", 0.011993);
lng.addComponent("n-butane", 0.003255);
lng.addComponent("i-pentane", 0.000657);
lng.addComponent("n-pentane", 0.000545);
lng.setMixingRule("classic");
lng.init(0);

Standard_ISO6578 iso6578 = new Standard_ISO6578(lng);
iso6578.calculate();
double densityKgPerM3 = iso6578.getValue("density");
```

The calculation is composition-based. Confirm that the sample is a single,
representative LNG liquid and disclose uncertainty from composition,
temperature, and sampling.

## ASTM D6377 simulation quick start

Use the type-safe `RvpMethod` enum so that the selected calculation route is
explicit. The result is a thermodynamic simulation of the NeqSim fluid, not a
claim that a laboratory apparatus or sampling procedure conforms to ASTM D6377.

```java
import neqsim.standards.oilquality.Standard_ASTM_D6377;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface oil = new SystemSrkEos(275.15, 1.0);
oil.addComponent("methane", 0.0006538);
oil.addComponent("ethane", 0.006538);
oil.addComponent("propane", 0.065380);
oil.addComponent("n-pentane", 0.154500);
oil.addComponent("nC10", 0.545000);
oil.setMixingRule(2);
oil.init(0);

Standard_ASTM_D6377 vapourPressure = new Standard_ASTM_D6377(oil);
vapourPressure.setReferenceTemperature(37.8, "C");
vapourPressure.setMethodRVP(
    Standard_ASTM_D6377.RvpMethod.RVP_ASTM_D6377);
vapourPressure.calculate();

double rvpBara = vapourPressure.getValue("RVP", "bara");
double tvpBara = vapourPressure.getValue("TVP", "bara");
```

For this fixture, `RVP_ASTM_D6377` gives approximately 0.965 bara simulated
RVP and 1.666 bara TVP. The alternative `VPCR4` route gives approximately
1.157 bara for the same fluid; always report the selected route with the result.
Preserve light ends when constructing the fluid; flashing or stabilizing the
sample before this calculation changes the vapour pressure.

## Contracts and `isOnSpec()`

`BaseContract(system, terminal, country)` loads specifications from the
`gascontractspecifications` data set. A terminal name is therefore data-dependent,
not a portable built-in guarantee. `BaseContract.display()` opens a Swing window
and should not be used in headless services; use `runCheck()` and
`getResultTable()` for programmatic reporting.

Do not treat `isOnSpec()` as a universal compliance engine. In the current
implementation, calculation-only classes such as `Standard_ISO6976` and
`Standard_ISO6578` return `true` unconditionally. Standards with implemented
contract logic, such as `Draft_ISO18453`, compare against their attached
contract. For auditable checks, evaluate each calculated value against an
explicit, version-controlled `ContractSpecification` and record its basis,
limits, units, reference conditions, and uncertainty.

## Input and reporting checks

Before calculation:

1. Confirm the governing standard edition and contractual reference conditions.
2. Use molar composition and preserve trace components relevant to the property.
3. Normalize or otherwise document the composition basis.
4. Check that every component and temperature lies within the method's coverage.
5. Characterize heavy ends before hydrocarbon-dew-point or oil-volatility work.

After calculation:

1. Record the NeqSim version, class, edition, method, and reference conditions.
2. State whether the result is molar, mass-based, ideal-volume, or real-volume.
3. Review `getComponentsNotDefinedByStandard()` for ISO 6976. The implementation
   substitutes generic component data for unsupported species, so the result is
   not equivalent to explicit coverage by the standard.
4. Compare important results with certified measurements or another validated
   method before fiscal, contractual, or design use.

## References

- ISO 6976:2016, *Natural gas — Calculation of calorific values, density,
  relative density and Wobbe indices from composition*.
- ISO 6578:2017, *Refrigerated hydrocarbon liquids — Static measurement —
  Calculation procedure*.
- ISO 18453:2004, *Natural gas — Correlation between water content and water
  dew point*.
- ASTM D6377, *Standard Test Method for Determination of Vapor Pressure of
  Crude Oil*.
