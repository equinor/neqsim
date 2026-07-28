---
title: "ISO 6976 regression evidence"
description: "Trace the current ISO 6976 gas-quality regression coverage for calorific value, Wobbe index, reference conditions, aliases, and unsupported components."
---

This page explains the numerical evidence in
[`Standard_ISO6976Test`](https://github.com/equinor/neqsim/blob/master/src/test/java/neqsim/standards/gasquality/Standard_ISO6976Test.java).
Use it with the [ISO 6976 calculation guide](../standards/iso6976_calorific_values.md),
which documents the API, editions, units, and engineering boundaries.

## Reference fixture and expected values

The shared `setUpBeforeClass` fixture builds an SRK gas at 20 °C and 1.0 bara,
adds methane, ethane, nitrogen, and carbon dioxide, applies the classic mixing
rule, and performs a TP flash. `testCalculate` then configures
`Standard_ISO6976` with a 0 °C volume reference, a 15.55 °C combustion-energy
reference, a real-gas reference state, and a volume basis.

`getUnit(...)` labels energy properties as `KJ/Nm3`. In this API, the cubic metre
is evaluated at the configured volume-reference temperature; it is not an
unqualified geometric m³. The table converts the kJ/Nm³ assertions to MJ/Nm³ for
readability. `testCalculate` asserts GCV and WI. `testCalculate2` uses a
separately initialized SRK system with the same four component amounts and
asserts relative density.

| Property | Regression value | Unit |
| --- | ---: | --- |
| Superior calorific value (`GCV`) | 39.61457 | MJ/Nm³ |
| Superior Wobbe index (`WI`) | 51.70101 | MJ/Nm³ |
| Relative density | 0.5870995 | dimensionless |

The exact energy assertions before conversion are 39614.56783352743 kJ/Nm³ for
GCV and 51701.01275822569 kJ/Nm³ for WI.

The superior Wobbe index is related to the superior calorific value by

$$
W_s = \frac{H_s}{\sqrt{d}}
$$

where $H_s$ is the superior calorific value on the selected basis and $d$ is
the relative density on the same reference basis. Report both reference
temperatures, the real or ideal reference state, and the volume, mass, or molar
basis with every result.

## Aliases and composition sensitivity

`testCalculate` verifies that `WI` and `WobbeIndex` both resolve to
`SuperiorWobbeIndex`. The separate `testWIAliasVariesWithComposition` regression
prevents the alias from returning a composition-independent value: its lean
98 mol% methane / 2 mol% ethane test target is approximately 53860 kJ/Nm³
(53.86 MJ/Nm³), while its richer methane/ethane/propane target is approximately
58380 kJ/Nm³ (58.38 MJ/Nm³).

These values are software regression anchors for the specified fixtures and
reference conditions. They are not universal sales-gas limits.

## Invalid reference temperatures

`testCalculateWithWrongReferenceState` deliberately sets unsupported reference
temperatures. When a value is requested, `checkReferenceCondition()` changes an
unsupported combustion-energy reference to 25 °C and an unsupported volume
reference to 15 °C, and logs both corrections. The test asserts
37499.35392575905 kJ/Nm³ (37.49935 MJ/Nm³) for the resulting GCV.

This fallback keeps the calculation running, but it also changes the requested
basis. Validate reference temperatures before calculation instead of treating
the fallback as input validation. Although `checkReferenceCondition()` accepts 25 °C as a volume reference,
explicit volume-dependent corrections are implemented only for 0, 15, 15.55,
and 20 °C. Use one of those four values, as explained in the primary guide.

## Pseudo-components and unsupported species

`testCalculateWithPSeudo` adds a `C10` TBP fraction and asserts a resulting GCV
of 42377.76099372482 kJ/Nm³ (42.37776 MJ/Nm³). This proves that the current fallback
route remains numerically stable for that fixture; it does not prove explicit
ISO 6976 coverage for the pseudo-component.

For hydrocarbon, TBP, and plus fractions not found in the ISO data table,
`Standard_ISO6976` substitutes n-heptane data and records the original component
name in `getComponentsNotDefinedByStandard()`. Other unsupported component types
use different fallback mappings. Always inspect that list and disclose any
approximation before using the result for design, fiscal, or contractual work.

## Full-property and stream coverage

`testCalculate2` creates a separate SRK system, loads the component database,
uses mixing rule 2, and configures the standard at 0/15.55 °C. It checks
superior and inferior calorific values, superior and inferior Wobbe indices,
relative density, compression factor, and molar mass. `testCalculate3` uses a
different gas at 15/15 °C and verifies the same property family. It also confirms that
`Stream.getGCV(...)` and `Stream.getWI(...)` agree with the standard calculation
after the stream has run.

Initialize or flash the thermodynamic system so that its composition and state
are current before constructing the standard. Use the same preparation,
reference conditions, and basis when comparing a custom calculation with a
regression value.

## Interpretation boundary

The tests establish repeatable NeqSim results for defined inputs and catch
software regressions in aliases, reference handling, composition response, and
stream integration. They do not establish sampling quality, measurement
uncertainty, laboratory conformity, contractual compliance, or certification to
a particular ISO 6976 edition.

See the [standards package overview](../standards/README.md) for the broader
measurement and reporting checks.
