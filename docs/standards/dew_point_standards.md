---
title: "Water and Hydrocarbon Dew-Point Methods"
description: "Use NeqSim water- and hydrocarbon-dew-point standards classes with explicit pressure, model, units, validation, and contract boundaries."
keywords: "water dew point, hydrocarbon dew point, ISO 18453, GERG-water, SRK, gas quality, sales gas, reference pressure, phase envelope"
---

Dew-point calculations locate a phase-appearance boundary for a defined gas
composition and pressure. They are sensitive to sampling, water or heavy-end
content, model selection, binary-interaction parameters, and the specified
pressure. Treat NeqSim results as calculation evidence, not as proof that a
sample, measurement procedure, or delivery point complies with a standard or
contract.

## Choose the maintained calculation path

| Engineering question | Current class | Current boundary |
| --- | --- | --- |
| Water dew point at one pressure | `Standard_ISO18453` | Converts a non-GERG-water input composition to an internal `SystemGERGwaterEos` and runs `waterDewPointTemperatureFlash()` |
| Legacy database-backed water-dew-point rows | `Draft_ISO18453` | Retained for compatibility in `BaseContract`; new direct calculations should use `Standard_ISO18453` |
| Hydrocarbon dew point at the class's fixed pressure | `BestPracticeHydrocarbonDewPoint` | Copies non-water components into an internal SRK system, applies mixing rule 2, and calculates at 50 bara |
| Dew-point curve, cricondentherm, or cricondenbar | Phase-envelope or saturation operations | The fixed-pressure standards classes do not calculate these envelope extrema |

A dew point at 50 bara is not the cricondentherm. The cricondentherm is the
maximum temperature on the complete phase envelope and generally occurs at a
pressure determined by the envelope calculation.

## Water dew point with `Standard_ISO18453`

The current class is documented in source as superseding
`Draft_ISO18453`. It accepts any `SystemInterface`; when the input is not
already a GERG-water system, the constructor copies the phase-0 component names
and mole amounts into a new `SystemGERGwaterEos`.

The calculation pressure is absolute pressure in bara. Use `setPressure(double)`
or `setReferencePressure(double)` before `calculate()`. The primary result key
is `dewPointTemperature`; the no-unit getter returns degrees Celsius, while the
two-argument getter recognizes `"K"` and `"F"` conversions.

### Executable Java 8 example

```java
import neqsim.standards.gasquality.Standard_ISO18453;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;

SystemInterface wetGas = new SystemGERGwaterEos(268.15, 20.0);
wetGas.addComponent("methane", 0.9);
wetGas.addComponent("water", 0.0000051);
wetGas.createDatabase(true);
wetGas.setMixingRule(8);
wetGas.init(0);

Standard_ISO18453 waterDewPoint = new Standard_ISO18453(wetGas);
waterDewPoint.setPressure(70.0);
waterDewPoint.calculate();

double waterDewPointC =
    waterDewPoint.getValue("dewPointTemperature", "C");
double calculationPressureBara = waterDewPoint.getValue("pressure");
```

For the repository fixture, the result is approximately -28.493 °C at
70 bara. That number validates the documented API and bundled model behavior; it
is not a generic natural-gas expectation.

### Water-dew-point contract decisions

Apply the governing limit explicitly in the project layer:

```java
double maximumWaterDewPointC = -8.0;
boolean withinWaterDewPointLimit =
    Double.isFinite(waterDewPointC)
        && waterDewPointC <= maximumWaterDewPointC;
```

`isOnSpec()` compares the calculated value with
`getSalesContract().getWaterDewPointTemperature()`. The default embedded
`BaseContract` limit is not evidence for a particular delivery point.
`setDewPointTemperatureSpec(double)` stores a separate class field that the
current `isOnSpec()` implementation does not read. Prefer the explicit
comparison above unless a verified `ContractInterface` has been attached.

The direct operation solves equality of water fugacity between the feed gas and
a pure incipient condensed phase. It uses a bracketed temperature solve with a
relative fugacity criterion; an exhausted iteration limit is a failure, not a
dew point. Positive water content and positive finite absolute pressure are
required. The legacy direct pure-MEG calculation is also supported. The standard
classes log a failed calculation and return `NaN`, so `isOnSpec()` is false.

### Ice, liquid water, and applicability

[ISO 18453:2004](https://www.iso.org/standard/31799.html), clauses 3–5,
uses separate water attraction functions for vapour pressure over ice below
273.16 K and over liquid water above it. A subzero ISO result therefore must not
be compared with a supercooled-liquid dew point without identifying the
condensed phase. Hydrate equilibrium is another, separate boundary.

The validated working range is **−15 to +5 °C and 5 to 100 bara**, subject to
the standard's composition limits. The extended range is **−50 to +40 °C and
1 to 300 bara**, with unknown correlation uncertainty. Thus −26 °C is outside
the validated range but inside the extended range. Numerical convergence does
not extend the model's engineering validity; do not use extrapolation outside
the extended range as an ISO-qualified result.

A result differing from an ideal-gas estimate alone does not identify a coding
error. Use the same ice/liquid reference and check fugacity equilibrium, model
parameters and applicability first. The direct operation retains the existing
pure condensed-phase approximation; it does not model dissolved gas, mixed
water/glycol liquids, or hydrates. Use and qualify the multiphase/CPA or hydrate
operation for those questions. The ISO-model low-temperature deviations from
measurements are discussed by
[Rajzinger (2012)](https://doi.org/10.26552/com.C.2012.4A.29-35), Table 2;
matching that model is distinct from matching experimental data.

### Trace-water process analyser

`WaterDewPointAnalyser` distinguishes the following boundaries:

| Method | Condensed reference and use |
| --- | --- |
| `CPA` | Fixed feed gas against pure liquid water using the Statoil SRK-CPA model; metastable liquid below freezing |
| `multiphase` | Mixed aqueous-phase onset using the stream's model and full multiphase flashes; use when dissolved gas, glycol or hydrocarbon liquids must be included |
| `CPA hydrate` | Hydrate equilibrium, separate from either water dew point |
| GERG-water | ISO 18453 correlation, including its ice reference below 273.16 K |

The `CPA` analyser now uses direct fugacity equilibrium instead of detecting an
aqueous phase by repeatedly flashing progressively colder mixtures. Its pure
liquid reference is initialized separately, avoiding ill-conditioned association
equations for species with vanishing mole fractions in the trial condensate.
Failed CPA calculations return `NaN` rather than a trial temperature. The inlet
stream remains unchanged.

This is a **pure-water approximation**: dissolved gas and mixed aqueous liquids
are excluded. It replaces the analyser's previous mixed-liquid search; callers
requiring that search should select `multiphase` on a CPA stream. This change
does not alter `waterDewPointTemperatureMultiphaseFlash()` or hydrate operations.

For a synthetic gas with 95% methane before moisture substitution, 3% ethane,
1% nitrogen and 1% CO2, the CPA liquid-water dew point at 5 ppmv and 43 bara is
approximately −43.97 °C. GERG-water gives approximately −34.50 °C for that gas.
These values solve different condensed-reference models; forcing agreement
within 3 °C would not validate either model. A liquid-water ideal-gas bound must
not be applied indiscriminately to the ISO ice correlation. Numerical tests
cover 1–100 ppmv at 43 and 70 bara and check CPA gas/pure-liquid fugacity equality;
they do not qualify extrapolated model accuracy against field measurements.

Before the convergence correction, the fixed temperature step scaled with
absolute water mole fraction and could stop at 10,000 iterations far from
equilibrium, especially in dry gas. Earlier stored dew-point values from this
operation should be recalculated. Retain the input composition, water basis,
pressure, model, NeqSim version and convergence diagnostics with each result.

## Hydrocarbon dew point with `BestPracticeHydrocarbonDewPoint`

The constructor copies every phase-0 component except exact component name
`water` into a new `SystemSrkEos`, applies mixing rule 2, and initializes an
internal calculation system. It does not reuse the input fluid's EOS or fitted
binary-interaction parameters.

The current implementation has a fixed `specPressure` of 50.0 bara.
`calculate()` resets the internal system to that pressure.
The inherited `setReferencePressure(double)` method does not change
`specPressure`, and changing the pressure of the input fluid after construction
does not change the calculation pressure.

### Executable Java 8 example

```java
import neqsim.standards.gasquality.BestPracticeHydrocarbonDewPoint;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface richGas = new SystemSrkEos(293.15, 70.0);
richGas.addComponent("methane", 0.85);
richGas.addComponent("ethane", 0.05);
richGas.addComponent("propane", 0.03);
richGas.addComponent("i-butane", 0.01);
richGas.addComponent("n-butane", 0.015);
richGas.addComponent("i-pentane", 0.005);
richGas.addComponent("n-pentane", 0.005);
richGas.addComponent("n-hexane", 0.003);
richGas.addComponent("nitrogen", 0.02);
richGas.addComponent("CO2", 0.012);
richGas.setMixingRule("classic");

BestPracticeHydrocarbonDewPoint hydrocarbonDewPoint =
    new BestPracticeHydrocarbonDewPoint(richGas);
hydrocarbonDewPoint.calculate();

double hydrocarbonDewPointC =
    hydrocarbonDewPoint.getValue(
        "hydrocarbondewpointTemperature", "C");
double calculationPressureBara =
    hydrocarbonDewPoint.getValue("pressure");
```

The result should be finite and the reported calculation pressure is 50 bara.
The two-argument getter does not currently convert hydrocarbon-dew-point units;
use `"C"` and report degrees Celsius.

Do not use `BestPracticeHydrocarbonDewPoint.isOnSpec()` for a hydrocarbon limit.
The current method compares the hydrocarbon result with the attached contract's
*water*-dew-point temperature. Apply the verified hydrocarbon limit explicitly:

```java
double maximumHydrocarbonDewPointC = -2.0;
boolean withinHydrocarbonDewPointLimit =
    Double.isFinite(hydrocarbonDewPointC)
        && hydrocarbonDewPointC <= maximumHydrocarbonDewPointC;
```

The value above only illustrates caller-owned comparison logic. Replace it with
the controlled limit, pressure range, uncertainty, and rounding rules from the
governing agreement.

## Curves and envelope extrema

Do not build a pressure curve by changing the source fluid pressure and repeatedly
constructing `BestPracticeHydrocarbonDewPoint`; every instance still calculates
at 50 bara. For a curve or envelope extremum, use a thermodynamic phase-envelope
or saturation workflow that exposes pressure as a calculation input, then
validate the selected EOS and characterization against measured dew-point data.

For hydrocarbon-dew-point work, preserve and qualify:

- representative sampling and recombination;
- C6+ or plus-fraction characterization;
- selected EOS, volume translation, and binary-interaction parameters;
- water, glycol, methanol, and other excluded or separately modeled components;
- convergence branch and phase-appearance interpretation;
- pressure and temperature measurement uncertainty.

For water-dew-point work, also qualify water-content measurement, acid gases,
glycols or inhibitors, salinity where relevant, and the applicability range of
the chosen water model.

## Reporting and engineering boundary

Every reported result should include:

1. sample or case identity and composition basis;
2. NeqSim version and class name;
3. EOS, mixing rule, characterization, and parameter provenance;
4. absolute calculation pressure in bara;
5. dew-point temperature and unit;
6. convergence or failure diagnostics;
7. governing standard or contract edition from a controlled source;
8. project limit, uncertainty, rounding rule, and accountable reviewer.

Class names such as `Standard_ISO18453` do not establish that sampling,
instrumentation, calibration, data reduction, or the complete requirements of a
licensed standard have been satisfied.

## Related documentation

- [Standards package overview](README.md)
- [Sales-contract checks](sales_contracts.md)
- [Phase-envelope guide](../pvtsimulation/phase_envelope_guide.md)
- [Thermodynamic operations](../thermodynamicoperations/README.md)
