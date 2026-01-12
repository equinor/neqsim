# Unit Conversion Guide

Documentation for unit conversion and unit systems in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Supported Units](#supported-units)
- [Unit Systems](#unit-systems)
- [Usage Examples](#usage-examples)
- [Unit Conversion Classes](#unit-conversion-classes)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.util.unit`

NeqSim provides comprehensive unit handling capabilities:

- Multiple unit systems (SI, Metric, Field/Imperial)
- Automatic unit conversion in property getters
- Configurable active unit system
- Type-safe unit classes

| Class | Description |
|-------|-------------|
| `Units` | Unit system management and switching |
| `PressureUnit` | Pressure unit conversion |
| `TemperatureUnit` | Temperature unit conversion |
| `LengthUnit` | Length unit conversion |
| `EnergyUnit` | Energy unit conversion |
| `PowerUnit` | Power unit conversion |
| `RateUnit` | Flow rate unit conversion |
| `TimeUnit` | Time unit conversion |
| `BaseUnit` | Abstract base for unit classes |
| `NeqSimUnitSet` | Complete unit set definition |

---

## Supported Units

### Pressure Units

| Unit | Symbol | Description |
|------|--------|-------------|
| Pascal | `Pa` | SI unit |
| Bar absolute | `bara` | Metric (default) |
| Bar gauge | `barg` | Bar relative to atmosphere |
| PSI absolute | `psia` | Field units |
| PSI gauge | `psig` | PSI relative to atmosphere |
| PSI | `psi` | Same as psia |
| Atmosphere | `atm` | Standard atmosphere |
| mmHg | `mmHg` | Millimeters of mercury |
| kPa | `kPa` | Kilopascals |
| MPa | `MPa` | Megapascals |

### Temperature Units

| Unit | Symbol | Description |
|------|--------|-------------|
| Kelvin | `K` | SI unit |
| Celsius | `C` | Metric (default) |
| Fahrenheit | `F` | Field units |
| Rankine | `R` | Absolute imperial |

### Flow Rate Units

| Unit | Symbol | Description |
|------|--------|-------------|
| kg/s | `kg/sec` | SI mass flow |
| kg/hr | `kg/hr` | Metric mass flow (default) |
| kg/day | `kg/day` | Daily mass flow |
| mol/s | `mol/sec` | SI molar flow |
| mol/hr | `mole/hr` | Metric molar flow |
| kmol/hr | `kmole/hr` | Kilomoles per hour |
| m³/hr | `m3/hr` | Volume flow |
| Sm³/hr | `Sm3/hr` | Standard volume flow |
| Sm³/day | `Sm3/day` | Standard daily flow |
| MSm³/day | `MSm3/day` | Million Sm³/day |
| bbl/day | `bbl/day` | Barrels per day |
| lb/hr | `lb/hr` | Pounds per hour |

### Energy Units

| Unit | Symbol | Description |
|------|--------|-------------|
| Joule | `J` | SI unit |
| kJ | `kJ` | Kilojoules |
| MJ | `MJ` | Megajoules |
| kWh | `kWh` | Kilowatt-hours |
| BTU | `BTU` | British thermal units |

### Power Units

| Unit | Symbol | Description |
|------|--------|-------------|
| Watt | `W` | SI unit (default) |
| kW | `kW` | Kilowatts |
| MW | `MW` | Megawatts |
| hp | `hp` | Horsepower |
| BTU/hr | `BTU/hr` | BTU per hour |

### Length Units

| Unit | Symbol | Description |
|------|--------|-------------|
| Meter | `m` | SI unit (default) |
| Kilometer | `km` | Kilometers |
| Centimeter | `cm` | Centimeters |
| Millimeter | `mm` | Millimeters |
| Inch | `in` | Inches |
| Foot | `ft` | Feet |
| Mile | `mile` | Miles |

---

## Unit Systems

NeqSim supports three predefined unit systems:

### SI Units

International System of Units (scientific standard).

```java
import neqsim.util.unit.Units;

Units.activateDefaultUnits(); // SI
```

| Property | Unit |
|----------|------|
| Temperature | K (Kelvin) |
| Pressure | Pa (Pascal) |
| Enthalpy | J/mol |
| Density | kg/m³ |
| JT coefficient | K/Pa |

### Metric Units (Default)

Engineering metric units - commonly used in European industry.

```java
Units.activateMetricUnits();
```

| Property | Unit |
|----------|------|
| Temperature | °C (Celsius) |
| Pressure | bara |
| Enthalpy | J/kg |
| Density | kg/m³ |
| Viscosity | Pa·s |

### Field Units

Imperial/oilfield units - commonly used in US oil & gas.

```java
Units.activateFieldUnits();
```

| Property | Unit |
|----------|------|
| Temperature | °F (Fahrenheit) |
| Pressure | psia |
| Enthalpy | BTU/lbmol |
| Density | lb/ft³ |
| Viscosity | cP |

---

## Usage Examples

### Getting Properties with Units

Most NeqSim methods accept a unit string parameter:

```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
gas.init(3);

// Pressure
double p_bara = gas.getPressure("bara");    // 50.0
double p_Pa = gas.getPressure("Pa");        // 5000000.0
double p_psia = gas.getPressure("psia");    // 725.19

// Temperature  
double T_K = gas.getTemperature("K");       // 298.15
double T_C = gas.getTemperature("C");       // 25.0
double T_F = gas.getTemperature("F");       // 77.0

// Density
double rho_kgm3 = gas.getDensity("kg/m3");  
double rho_lbft3 = gas.getDensity("lb/ft3");

// Flow rates (for streams)
stream.getFlowRate("kg/hr");
stream.getFlowRate("Sm3/day");
stream.getFlowRate("MSm3/day");
stream.getFlowRate("bbl/day");
```

### Setting Properties with Units

```java
// Set temperature
stream.setTemperature(25.0, "C");
stream.setTemperature(298.15, "K");
stream.setTemperature(77.0, "F");

// Set pressure
stream.setPressure(50.0, "bara");
stream.setPressure(5.0, "MPa");
stream.setPressure(725.0, "psia");

// Set flow rate
stream.setFlowRate(1000.0, "kg/hr");
stream.setFlowRate(5.0, "MSm3/day");
stream.setFlowRate(10000.0, "bbl/day");
```

### Direct Unit Conversion

```java
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

// Pressure conversion
PressureUnit pu = new PressureUnit(50.0, "bara");
double p_psia = pu.getValue("psia");  // Convert to psia

// Temperature conversion
TemperatureUnit tu = new TemperatureUnit(25.0, "C");
double t_K = tu.getValue("K");    // 298.15
double t_F = tu.getValue("F");    // 77.0
```

### Switching Unit Systems

```java
import neqsim.util.unit.Units;

// Switch to field units for display
Units.activateFieldUnits();

// All subsequent property output uses field units
System.out.println("Temperature: " + 
    Units.activeUnits.get("temperature").symbol);  // "F"
System.out.println("Pressure: " + 
    Units.activeUnits.get("pressure").symbol);     // "psia"

// Switch back to metric
Units.activateMetricUnits();
```

### Equipment with Unit Parameters

```java
// Compressor with different unit specifications
Compressor comp = new Compressor("K-100", stream);
comp.setOutletPressure(100.0, "bara");

// Get power in different units
double power_W = comp.getPower("W");
double power_kW = comp.getPower("kW");
double power_hp = comp.getPower("hp");

// Heat exchanger duty
HeatExchanger hex = new HeatExchanger("E-100");
hex.setDuty(1000.0, "kW");
double duty_BTU_hr = hex.getDuty("BTU/hr");
```

---

## Unit Conversion Classes

### PressureUnit

```java
import neqsim.util.unit.PressureUnit;

// Create from any unit
PressureUnit p1 = new PressureUnit(50.0, "bara");
PressureUnit p2 = new PressureUnit(5000000.0, "Pa");
PressureUnit p3 = new PressureUnit(725.0, "psia");

// Convert to any unit
double inPa = p1.getValue("Pa");
double inBara = p1.getValue("bara");
double inPsia = p1.getValue("psia");
double inBarg = p1.getValue("barg");
```

### TemperatureUnit

```java
import neqsim.util.unit.TemperatureUnit;

TemperatureUnit t = new TemperatureUnit(100.0, "C");
double inK = t.getValue("K");       // 373.15
double inF = t.getValue("F");       // 212.0
double inR = t.getValue("R");       // 671.67
```

### RateUnit

```java
import neqsim.util.unit.RateUnit;

// Mass flow conversion
RateUnit massFlow = new RateUnit(1000.0, "kg/hr");
double inKgSec = massFlow.getValue("kg/sec");
double inLbHr = massFlow.getValue("lb/hr");

// Standard volume flow
RateUnit volFlow = new RateUnit(1.0e6, "Sm3/day");
double inMSm3Day = volFlow.getValue("MSm3/day");  // 1.0
```

---

## Best Practices

### Always Specify Units Explicitly

```java
// Good - explicit units
stream.setTemperature(25.0, "C");
stream.setPressure(50.0, "bara");

// Avoid - ambiguous
stream.setTemperature(25.0);  // What unit is this?
```

### Use Consistent Unit System

```java
// Choose one system for a project
Units.activateMetricUnits();

// Document assumptions
// All temperatures in °C, pressures in bara
```

### Convert at Boundaries

```java
// Convert external inputs to internal units
double externalTemp_F = 77.0;
double internalTemp_C = new TemperatureUnit(externalTemp_F, "F").getValue("C");

// Convert internal results to external units for output
double internalPressure_bara = 50.0;
double outputPressure_psia = new PressureUnit(internalPressure_bara, "bara").getValue("psia");
```

---

## Related Documentation

- [Utilities Package](README.md) - Main utilities documentation
- [Process Equipment](../process/equipment/README.md) - Equipment with unit parameters
- [Thermodynamic Systems](../thermo/README.md) - Fluid property units
