---
title: "Unit Conversion Guide"
description: "Unit strategies, absolute values and differences, flow context, and API migration in NeqSim."
---

# Unit Conversion Guide

Use explicit unit strings at calculation boundaries. Unit names are case-sensitive.
The conversion classes are in `neqsim.util.unit`; their SI reference need not be
the same as the default units of a thermodynamic system. For example, `PressureUnit`
uses Pa, while the system's no-argument pressure getter returns bara.

## Choose the conversion strategy

| Quantity | Class | Strategy | SI reference |
|---|---|---|---|
| Pressure | `PressureUnit` | `BiasAdjustedUnit`: scale and gauge offset | Pa absolute |
| Temperature | `TemperatureUnit` | `BiasAdjustedUnit`: scale and temperature offset | K |
| Length | `LengthUnit` | `LinearScaleUnit` | m |
| Energy | `EnergyUnit` | `LinearScaleUnit` | J |
| Power | `PowerUnit` | `LinearScaleUnit` | W |
| Time | `TimeUnit` | `LinearScaleUnit` | s |
| Flow rate | `RateUnit` | `LinearScaleUnit`, with caller-supplied fluid context | mol/sec |

For scale-only units, `getConversionFactor(unit)` multiplies a value into SI.
For offset-aware units, `toSIvalue(value, unit)` and
`fromSIvalue(siValue, unit)` handle the offset as well as the scale.
`getValue(targetUnit)` converts the stored input without changing it, including
when called through a `Unit`, `LinearScaleUnit`, or `BiasAdjustedUnit` reference.

Null, blank and unsupported unit strings throw `IllegalArgumentException` from
these built-in converters. This is input validation, not a check of whether a
pressure, temperature or flow is physically admissible for an equipment model.
`getAllowedUnits()` returns a defensive copy; editing it cannot register a new unit.

## Supported scalar units

| Class | Accepted strings |
|---|---|
| `PressureUnit` | `Pa`, `kPa`, `MPa`, `bar`, `bara`, `barg`, `psi`, `psia`, `psig`, `atm` |
| `TemperatureUnit` | `K`, `C`, `F`, `R` |
| `LengthUnit` | `m`, `meter`, `metre`, `cm`, `mm`, `km`, `in`, `inch`, `ft`, `feet` |
| `EnergyUnit` | `J`, `kJ`, `MJ`, `Wh`, `kWh`, `MWh`, `BTU`, `kcal` |
| `PowerUnit` | `W`, `kW`, `MW`, `hp`, `BTU/hr` |
| `TimeUnit` | `s`, `sec`, `second`, `min`, `minute`, `h`, `hr`, `hour`, `d`, `day` |

`bar` and `psi` mean absolute pressure. Gauge conversions use the fixed reference
atmosphere, 1.01325 bar; they do not infer local atmospheric pressure.
`mmHg`, `torr`, and `mile` are not supported by these classes.

## Absolute values and differences

An absolute temperature and a temperature rise require different calculations.
Likewise, a pressure drop must not acquire a gauge-pressure offset.

```java
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

// Absolute quantities include their reference offsets.
double pressurePa = PressureUnit.convert(0.0, "barg", "Pa"); // 101325
double temperatureK = TemperatureUnit.convert(32.0, "F", "K"); // 273.15

// Differences use only the scale, including gauge aliases.
double dropBar = PressureUnit.convertDifference(100.0, "kPa", "barg"); // 1
double riseK = TemperatureUnit.convertDifference(18.0, "F", "K"); // 10
double coolingF = TemperatureUnit.convertDifference(-10.0, "C", "F"); // -18
```

Difference conversions retain the sign and preserve zero. They do not subtract
two large absolute values, so very small differences are not lost to cancellation.
Equipment remains responsible for its own constraints: for example,
`Filter.setDeltaP(value, unit)` uses pressure-difference conversion and clamps
negative pressure drops to zero.

## Scalar conversion examples

```java
import neqsim.util.unit.EnergyUnit;
import neqsim.util.unit.LengthUnit;
import neqsim.util.unit.PowerUnit;
import neqsim.util.unit.TimeUnit;
import neqsim.util.unit.Unit;

Unit distance = new LengthUnit(100.0, "cm");
double distanceM = distance.getSIvalue(); // 1
double distanceMm = distance.getValue("mm"); // 1000
double energyJ = EnergyUnit.convert(1.0, "kWh", "J"); // 3600000
double powerKw = PowerUnit.convert(1.0, "hp", "kW"); // 0.745699872
double durationS = TimeUnit.convert(2.0, "h", "s"); // 7200
```

## Flow rate units and fluid context

`RateUnit` requires five constructor arguments. Its static `convert` method
requires six arguments, including the source and target units. There is no
two-argument constructor or three-argument static flow conversion.

```java
import neqsim.util.unit.RateUnit;

// Molar mass is kg/mol. For this synthetic liquid, density is 800 kg/m3.
RateUnit liquid = new RateUnit(720.0, "kg/hr", 0.020, 800.0, 100.0);
double molPerSecond = liquid.getSIvalue(); // 10
double kgPerSecond = liquid.getValue("kg/sec"); // 0.2
double cubicMetresPerHour = liquid.getValue("m3/hr"); // 0.9

double heavierFluidMolPerSecond =
    RateUnit.convert(720.0, "kg/hr", "mol/sec", 0.040, 900.0, 100.0); // 5
```

| Rate family | Common strings | Conversion basis |
|---|---|---|
| Molar | `mol/sec`, `mol/min`, `mol/hr`, `kmol/sec`, `kmol/min`, `kmol/hr`, `kmol/day` | Mole count and time |
| Mass | `kg/sec`, `kg/min`, `kg/hr`, `kg/day`, `lb/hr` | Molar mass in kg/mol |
| Actual volume | `m3/sec`, `m3/min`, `m3/hr`, `m3/day`, and `Am3` aliases | Density supplied by the caller in kg/m3 |
| Ideal liquid volume | `idSm3/sec`, `idSm3/min`, `idSm3/hr`, `idSm3/day` | Caller must supply ideal-liquid density |
| Standard gas volume | `Sm3/sec`, `Sm3/min`, `Sm3/hr`, `Sm3/day`, `MSm3/hr`, `MSm3/day` | Ideal gas at 288.15 K and 101325 Pa; `M` means million |

The supplied density has only one slot: `RateUnit` cannot infer both actual and
ideal-liquid densities for a conversion between those families. Prefer the fluid
or stream API when the required density must be calculated from a thermodynamic
state. A direct mass/molar conversion needs a finite positive molar mass; a direct
volume conversion also needs the correct finite positive density. The legacy
constructor does not validate these property values.

**Known limitations:** `gallons/min`, `barrel/day` / `bbl/day`, and `Nlitre/*` retain
legacy conversion/basis inconsistencies. `Nlitre/*` uses a boiling-point proxy
and must not be assumed to implement a universal 0 °C normal-gas basis.
Do not use these paths as validated engineering conversions. The coordinated
registry and density-basis correction is tracked in
[issue #2998](https://github.com/equinor/neqsim/issues/2998). A successful round trip
alone does not validate a conversion factor: an incorrect forward and inverse
pair can cancel. Use independent mass/volume reference values as well.

## Thermodynamic system and stream examples

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
gas.setTemperature(77.0, "F");
gas.setPressure(5.0, "MPa");
double temperatureC = gas.getTemperature("C"); // 25
double pressureBar = gas.getPressure("bara"); // 50

Stream stream = new Stream("unit conversion feed", gas);
stream.setFlowRate(3600.0, "kg/hr");
double massRate = stream.getFlowRate("kg/sec"); // 1
```

## Display unit systems

`Units` configures process-wide display defaults; it does not change the stored
thermodynamic state or register new conversion symbols. Explicit unit arguments
are preferable in concurrent applications. Preserve and restore the active map
when changing display settings temporarily. Do not mutate its descriptions.

```java
import java.util.Map;
import neqsim.util.unit.Units;

Map<String, Units.UnitDescription> previous = Units.activeUnits;
try {
  Units.activateSIUnits();
  String pressureSymbol = Units.getSymbol("pressure"); // Pa
  String temperatureSymbol = Units.getSymbol("temperature"); // K
} finally {
  Units.activeUnits = previous;
}
```

`activateDefaultUnits()` selects NeqSim's engineering defaults (C and bara),
not SI. `activateMetricUnits()` selects the metric profile, while
`activateFieldUnits()` selects F and psia. `getSymbol`, `getSymbolName`, and
`setUnit` take a **property key**, such as `pressure`, not a unit symbol such as
`Pa`. Unknown or blank property keys are rejected.

## Compatibility and migration

- Existing `PressureUnit.getConversionFactor(unit)` remains available, deprecated.
  Its result is a multiplier **to bar**, not to Pa; gauge offsets are excluded.
  Migrate absolute quantities to `PressureUnit.convert` and differences to
  `PressureUnit.convertDifference`.
- Existing `TemperatureUnit.getConversionFactor(unit)` remains available,
  deprecated, with its original scale-to-Kelvin meaning. Migrate to `convert`
  or `convertDifference` according to the quantity.
- `BaseUnit.SIvalue`, `BaseUnit.factor`, and the inherited `getSIvalue()` remain
  available for legacy subclasses and are deprecated. New subclasses should
  compute SI values from the stored input and avoid mutable conversion caches.
  `BaseUnit` does not implement `getValue`, so it cannot shadow strategy defaults.
- The previously removed three-argument `Unit.getValue` overload is not restored.
  Use the concrete static conversion method or construct an instance.
- New unit implementations must define `getAllowedUnits()` independently of
  instance-field initialization, because the base constructor validates the unit.
  Choose `LinearScaleUnit` only when there is no offset; use `BiasAdjustedUnit`
  for affine conversions. Keep application-specific density context outside
  context-free scalar converters.

The examples above are compiled and executed by `UnitConversionGuideTest`.
`UnitStrategyContractTest` covers independent reference values, invalid inputs,
small and signed differences, supported-unit isolation, and legacy subclasses.
The unit-conversion refactor does not by itself qualify every flow basis or
change process-wide display settings into per-model settings.

## Related documentation

- [Utilities Package](./index.md)
- [Unit Conversion Recipes](../cookbook/unit-conversion-recipes.md)
- [Filter pressure-drop models](../process/equipment/filters.md)
- [Public API lifecycle](../development/api-lifecycle.md)
