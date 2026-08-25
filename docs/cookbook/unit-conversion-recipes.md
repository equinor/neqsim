---
title: "Unit Conversion Recipes"
description: "Use NeqSim's unit-bearing setters, getters, and conversion classes without mixing physical-property, flow-basis, or report-unit semantics."
---

NeqSim unit strings belong to the API that consumes them. There is no single registry that
proves a string is valid for every setter, getter, property, or equipment model. Unit strings are
case-sensitive: for example, `PowerUnit` accepts `"hp"`, not `"HP"`.

## Prefer unit-bearing setters and getters

Thermodynamic systems and streams expose unit-bearing overloads for temperature and pressure.
Use them instead of maintaining conversion constants in application code:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

fluid = SystemSrkEos(298.15, 1.01325)
fluid.setTemperature(77.0, "F")
fluid.setPressure(5.0, "MPa")

temperature_c = fluid.getTemperature("C")
pressure_psia = fluid.getPressure("psia")

assert abs(temperature_c - 25.0) < 1.0e-10
assert abs(pressure_psia - 725.1887) < 1.0e-3
```

The no-argument system getters return the internal defaults: kelvin for temperature and absolute
bar for pressure. Gauge inputs (`"barg"` and `"psig"`) are converted using NeqSim's reference
pressure; keep the absolute/gauge basis explicit in stored results.

Supported `TemperatureUnit` strings are `"K"`, `"C"`, `"F"`, and `"R"`.
`PressureUnit` accepts `"bara"`, `"bar"`, `"barg"`, `"psi"`, `"psia"`, `"psig"`,
`"Pa"`, `"kPa"`, `"MPa"`, and `"atm"`.

## Keep flow basis explicit

Use stream or system flow methods when converting a real fluid. Mass and molar conversions use
the fluid molar mass. Actual-volume, standard-volume, and ideal-liquid-volume rates are different
bases and must not be relabelled as one another.

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream

fluid = SystemSrkEos(298.15, 10.0)
fluid.addComponent("methane", 1.0)

stream = Stream("unit conversion feed", fluid)
stream.setFlowRate(3_600.0, "kg/hr")

mass_flow_kg_s = stream.getFlowRate("kg/sec")
molar_flow_kmol_h = stream.getFlowRate("kmol/hr")

assert abs(mass_flow_kg_s - 1.0) < 1.0e-12
assert molar_flow_kmol_h > 0.0
```

Common supported families in `RateUnit` include:

- mass: `"kg/sec"`, `"kg/min"`, `"kg/hr"`, `"kg/day"`, and `"lb/hr"`;
- molar: `"mol/sec"`, `"mol/min"`, `"mol/hr"`, `"kmol/sec"`, `"kmol/min"`,
  `"kmol/hr"`, `"kmol/day"`, `"lbmol/hr"`, and their `mole`/`kmole` aliases;
- actual volume: `"m3/sec"`, `"m3/min"`, `"m3/hr"`, `"m3/day"` and `Am3` aliases;
- standard volume: `"Sm3/sec"`, `"Sm3/min"`, `"Sm3/hr"`, `"Sm3/day"`,
  `"MSm3/hr"`, and `"MSm3/day"`; and
- liquid/other bases: `idSm3` time-rate variants, `"Nlitre/sec"`, `"Nlitre/min"`,
  `"gallons/min"`, `"barrel/day"`, and `"bbl/day"`.

Standard-volume conversion uses NeqSim's standard-state temperature and pressure. Actual-volume
and ideal-liquid conversions additionally depend on fluid properties. Record the basis and the
fluid state or reference condition with every reported volumetric rate.

## Use focused converters for scalar values

The conversion classes make the owning unit family explicit:

```python
from neqsim import jneqsim

LengthUnit = jneqsim.util.unit.LengthUnit
PowerUnit = jneqsim.util.unit.PowerUnit

length_ft = LengthUnit(1.0, "m").getValue("ft")
power_kw = PowerUnit(1.0, "hp").getValue("kW")

assert abs(length_ft - 3.280839895) < 1.0e-9
assert abs(power_kw - 0.745699872) < 1.0e-12
```

`LengthUnit` accepts `"m"`, `"meter"`, `"metre"`, `"cm"`, `"mm"`, `"km"`,
`"in"`, `"inch"`, `"ft"`, and `"feet"`. `PowerUnit` accepts `"W"`, `"kW"`,
`"MW"`, `"hp"`, and `"BTU/hr"`. `EnergyUnit` is a scalar energy converter with its own
strings (`"J"`, `"kJ"`, `"MJ"`, `"Wh"`, `"kWh"`, `"MWh"`, `"BTU"`, and
`"kcal"`); it is not the unit contract for every enthalpy getter.

## Initialize and label physical properties

After a flash, initialize physical properties before reading unit-aware density, viscosity, or
thermal conductivity:

```python
operations = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
operations.TPflash()
fluid.initProperties()

density_kg_m3 = fluid.getDensity("kg/m3")
assert density_kg_m3 > 0.0
```

`fluid.getDensity()` and `fluid.getDensity("kg/m3")` follow different documented paths. The
unit-bearing getter uses initialized physical properties; it does not itself enable a particular
volume-translation model. Record the thermodynamic model, initialization sequence, requested unit,
and phase or bulk basis with each property result. See
[Reading fluid properties](../thermo/reading_fluid_properties.md) for property-specific units and
initialization requirements.

## Global report-unit profiles

`Units.activateSIUnits()`, `Units.activateMetricUnits()`, `Units.activateFieldUnits()`, and
`Units.activateDefaultUnits()` replace a process-wide static symbol map used by reporting code.
They do not change the internal thermodynamic state and do not make arbitrary getter unit strings
valid. Because the map is global mutable state, restore it after a bounded reporting operation:

```python
Units = jneqsim.util.unit.Units

try:
    Units.activateFieldUnits()
    assert str(Units.getSymbol("pressure")) == "psia"
    assert str(Units.getSymbol("power")) == "hp"
finally:
    Units.activateDefaultUnits()
```

Pass explicit units at calculation boundaries. Reserve global profiles for controlled report or
display formatting, especially in concurrent applications and reusable libraries.

## Diagnostic checklist

When a conversion fails or looks implausible:

1. Check the exact owner method or conversion class; do not infer support from another unit family.
2. Preserve spelling and case exactly (`"MPa"`, `"kW"`, and `"hp"`).
3. Distinguish absolute from gauge pressure and actual from standard volume.
4. Flash and initialize the fluid before reading physical properties.
5. Check whether a conversion depends on composition, molar mass, density, or standard conditions.
6. Store each value with its unit, basis, reference condition, model, and provenance.

For errors involving overload selection, stale properties, or phase availability, see the
[troubleshooting guide](../troubleshooting/index.md). For API signatures, use the
[current JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html).

## Engineering boundary

Unit conversion does not establish that an input basis is physically appropriate. Standard
conditions, gauge reference pressure, heating-value basis, petroleum volume basis, and contractual
reporting conventions must be agreed and recorded by the accountable engineering workflow.
