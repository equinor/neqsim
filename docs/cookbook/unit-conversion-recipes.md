---
title: Unit Conversion Recipes
description: Working with units in NeqSim - all supported unit strings for input and output methods.
---

# Unit Conversion Recipes

NeqSim supports various units for input and output. This guide lists all supported unit strings.

## Quick Reference

### Temperature Units

| Unit String | Description |
|-------------|-------------|
| `"K"` | Kelvin (default internal) |
| `"C"` | Celsius |
| `"F"` | Fahrenheit |
| `"R"` | Rankine |

```python
# Setting temperature
stream.setTemperature(25.0, "C")      # Celsius
stream.setTemperature(298.15, "K")    # Kelvin
stream.setTemperature(77.0, "F")      # Fahrenheit

# Getting temperature (always returns Kelvin by default)
T_kelvin = fluid.getTemperature()
T_celsius = fluid.getTemperature() - 273.15  # Manual conversion
```

### Pressure Units

| Unit String | Description |
|-------------|-------------|
| `"bara"` | Bar absolute |
| `"barg"` | Bar gauge |
| `"Pa"` | Pascal |
| `"MPa"` | Megapascal |
| `"psia"` | PSI absolute |
| `"psig"` | PSI gauge |
| `"atm"` | Atmosphere |

```python
# Setting pressure
stream.setPressure(50.0, "bara")
stream.setPressure(725.0, "psia")
stream.setPressure(5.0, "MPa")

# Getting pressure (returns bara by default)
P_bara = fluid.getPressure()
P_psia = fluid.getPressure() * 14.504  # Manual conversion
```

### Flow Rate Units

| Unit String | Description |
|-------------|-------------|
| `"kg/hr"` | Kilogram per hour |
| `"kg/sec"` | Kilogram per second |
| `"kg/day"` | Kilogram per day |
| `"mol/hr"` | Mole per hour |
| `"mol/sec"` | Mole per second |
| `"Am3/hr"` | Actual m³/hr |
| `"Sm3/hr"` | Standard m³/hr |
| `"Sm3/day"` | Standard m³/day |
| `"MSm3/day"` | Million Sm³/day |
| `"idSm3/hr"` | Ideal Sm³/hr |

```python
# Setting flow
stream.setFlowRate(10000, "kg/hr")
stream.setFlowRate(5.0, "MSm3/day")  # 5 million Sm³/day

# Getting flow
mass_flow = stream.getFlowRate("kg/hr")
vol_flow = stream.getFlowRate("Sm3/day")
molar_flow = stream.getFlowRate("mol/hr")
```

### Density Units

| Unit String | Description |
|-------------|-------------|
| `"kg/m3"` | Kilogram per cubic meter |
| `"mol/m3"` | Mole per cubic meter |
| `"lb/ft3"` | Pound per cubic foot |

```python
# IMPORTANT: Always specify unit to get Peneloux-corrected density
density = fluid.getDensity("kg/m3")  # With volume correction
molar_density = fluid.getDensity("mol/m3")

# WITHOUT unit - returns EoS density (no Peneloux correction)
density_uncorrected = fluid.getDensity()  # Avoid this!
```

### Energy/Enthalpy Units

| Unit String | Description |
|-------------|-------------|
| `"J"` | Joule (total) |
| `"J/mol"` | Joule per mole |
| `"kJ/kg"` | Kilojoule per kilogram |
| `"kJ/kmol"` | Kilojoule per kmol |
| `"BTU/lb"` | BTU per pound |

```python
# Enthalpy
H_total = fluid.getEnthalpy("J")
H_molar = fluid.getEnthalpy("J/mol")
H_mass = fluid.getEnthalpy("kJ/kg")
```

### Entropy Units

| Unit String | Description |
|-------------|-------------|
| `"J/K"` | Joule per Kelvin (total) |
| `"J/molK"` | Joule per mole-Kelvin |
| `"J/kgK"` | Joule per kg-Kelvin |
| `"kJ/kgK"` | Kilojoule per kg-Kelvin |

```python
S_molar = fluid.getEntropy("J/molK")
S_mass = fluid.getEntropy("kJ/kgK")
```

### Heat Capacity Units

| Unit String | Description |
|-------------|-------------|
| `"J/molK"` | Joule per mole-Kelvin |
| `"J/kgK"` | Joule per kg-Kelvin |
| `"kJ/kgK"` | Kilojoule per kg-Kelvin |
| `"kJ/kmolK"` | Kilojoule per kmol-Kelvin |

```python
Cp = fluid.getCp("kJ/kgK")
Cv = fluid.getCv("kJ/kgK")
gamma = Cp / Cv  # Or fluid.getGamma()
```

### Viscosity Units

| Unit String | Description |
|-------------|-------------|
| `"kg/msec"` | Pa·s (default) |
| `"Pas"` | Pascal-second |
| `"cP"` | Centipoise |

```python
viscosity = fluid.getViscosity("cP")
# Or for phase
gas_visc = fluid.getPhase("gas").getViscosity("cP")
```

### Thermal Conductivity Units

| Unit String | Description |
|-------------|-------------|
| `"W/mK"` | Watt per meter-Kelvin |
| `"W/cmK"` | Watt per cm-Kelvin |

```python
k = fluid.getThermalConductivity("W/mK")
```

### Molar Mass Units

| Unit String | Description |
|-------------|-------------|
| `"kg/mol"` | Kilogram per mole (default) |
| `"gr/mol"` | Gram per mole |
| `"lbm/lbmol"` | Pound per lb-mole |

```python
MW = fluid.getMolarMass("gr/mol")  # Returns g/mol
# Note: getMolarMass("kg/mol") * 1000 = g/mol
```

### Power Units

| Unit String | Description |
|-------------|-------------|
| `"W"` | Watt |
| `"kW"` | Kilowatt |
| `"MW"` | Megawatt |
| `"HP"` | Horsepower |

```python
power = compressor.getPower("kW")
power_hp = compressor.getPower("HP")
```

### Length Units

| Unit String | Description |
|-------------|-------------|
| `"m"` | Meter |
| `"km"` | Kilometer |
| `"ft"` | Foot |
| `"inch"` | Inch |
| `"mm"` | Millimeter |

```python
pipe.setLength(10000)  # Default is meters
pipe.setDiameter(0.3)  # Default is meters
```

---

## Unit System Setting

NeqSim supports global unit system settings:

```python
from neqsim import jneqsim

Units = jneqsim.util.unit.Units

# Set system-wide units
Units.activateFieldUnits()   # Field units (psia, °F, etc.)
Units.activateSIUnits()      # SI units
Units.activateMetricUnits()  # Metric (default)

# After setting, display methods use these units
fluid.display()
```

---

## Common Conversions

| From | To | Multiply by |
|------|-----|-------------|
| bara | psia | 14.5038 |
| °C | K | +273.15 |
| °C | °F | ×1.8 + 32 |
| kg/m³ | lb/ft³ | 0.06243 |
| cP | Pa·s | 0.001 |
| kJ/kg | BTU/lb | 0.4299 |
| m | ft | 3.2808 |
| m³ | bbl | 6.2898 |

---

## See Also

- **[Reading Fluid Properties](../thermo/reading_fluid_properties)** - Complete property guide
- **[Unit Conversion Documentation](../util/unit_conversion)** - Detailed unit handling
- **[JavaDoc: Units](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/util/unit/Units.html)** - Unit API
