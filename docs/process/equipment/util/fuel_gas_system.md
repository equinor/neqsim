---
title: Fuel Gas System
description: Documentation for fuel gas conditioning systems in NeqSim for gas turbines, fired heaters, and flare pilots.
---

# Fuel Gas System

Models fuel gas conditioning for process facilities including gas turbines, fired heaters, and flare pilots.

## Table of Contents
- [Overview](#overview)
- [Design Standards](#design-standards)
- [Consumer Types](#consumer-types)
- [System Components](#system-components)
- [Usage Examples](#usage-examples)
- [Quality Calculations](#quality-calculations)
- [API Reference](#api-reference)

---

## Overview

**Location:** `neqsim.process.equipment.util.FuelGasSystem`

The `FuelGasSystem` class models complete fuel gas conditioning systems for:

- **Gas turbines** - Power generation and compressor drivers
- **Fired heaters** - Process heating
- **Flare pilots** - Continuous ignition source
- **Hot oil heaters** - Heat transfer fluid heating
- **Regeneration gas heaters** - TEG/mol sieve regeneration

### Key Features

- Automatic knockout drum for liquid removal
- Joule-Thomson effect calculation for pressure letdown
- Fuel gas heating for superheat above dew point
- Wobbe Index calculation for combustion control
- Multi-consumer demand management

---

## Design Standards

| Standard | Description |
|----------|-------------|
| API 618 | Reciprocating Compressors for Petroleum Industry |
| NORSOK P-002 | Process System Design |
| ISO 21789 | Gas Turbine Applications |

---

## Consumer Types

The system supports different consumer types with specific quality requirements:

| Consumer | Pressure (barg) | Min Superheat (°C) | Max H₂S (ppmv) |
|----------|-----------------|---------------------|----------------|
| `GAS_TURBINE` | 30 | 25 | 20 |
| `FIRED_HEATER` | 3 | 15 | 100 |
| `FLARE_PILOT` | 1.5 | 10 | 500 |
| `HOT_OIL_HEATER` | 3 | 15 | 100 |
| `REGEN_HEATER` | 2 | 10 | 200 |
| `INCINERATOR` | 1 | 5 | 1000 |

### Consumer Class Usage

```java
import neqsim.process.equipment.util.FuelGasSystem.ConsumerType;

// Get requirements for gas turbine
ConsumerType turbine = ConsumerType.GAS_TURBINE;
double pressure = turbine.getTypicalPressureBarg();     // 30 barg
double superheat = turbine.getMinSuperheatC();          // 25°C
double maxH2S = turbine.getMaxH2Sppmv();                // 20 ppmv
String desc = turbine.getDescription();                  // "High pressure, superheated"
```

---

## System Components

A typical fuel gas system includes:

```
                    ┌─────────────┐
HP Gas ──────────►  │  Pressure   │
(70 barg)           │   Letdown   │
                    └──────┬──────┘
                           │ JT cooling
                           ▼
                    ┌─────────────┐
                    │  Knockout   │───► Liquids
                    │    Drum     │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  Fuel Gas   │
                    │   Heater    │
                    └──────┬──────┘
                           │ Superheated gas
                           ▼
                    To Consumers
```

### Component Details

| Component | Purpose | Key Parameters |
|-----------|---------|----------------|
| Pressure Letdown | Reduce pressure from source | Outlet pressure, Cv |
| Knockout Drum | Remove condensed liquids | Residence time, efficiency |
| Fuel Gas Heater | Superheat above dew point | Duty, outlet temperature |

---

## Usage Examples

### Java Example - Basic Setup

```java
import neqsim.process.equipment.util.FuelGasSystem;
import neqsim.process.equipment.util.FuelGasSystem.ConsumerType;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create fuel gas source
SystemSrkEos gasFluid = new SystemSrkEos(273.15 + 30.0, 70.0);
gasFluid.addComponent("methane", 0.85);
gasFluid.addComponent("ethane", 0.08);
gasFluid.addComponent("propane", 0.04);
gasFluid.addComponent("CO2", 0.02);
gasFluid.addComponent("H2S", 0.00001);  // 10 ppmv
gasFluid.setMixingRule("classic");

Stream fuelGasSource = new Stream("HP Fuel Gas", gasFluid);
fuelGasSource.setFlowRate(2000.0, "kg/hr");
fuelGasSource.run();

// Create fuel gas system
FuelGasSystem fgSystem = new FuelGasSystem("Platform FG", fuelGasSource);
fgSystem.setOutletPressure(30.0);  // barg
fgSystem.setOutletTemperature(50.0);  // °C

// Add consumers
fgSystem.addConsumer("GT-101", ConsumerType.GAS_TURBINE, 800.0);  // kg/hr
fgSystem.addConsumer("H-101", ConsumerType.FIRED_HEATER, 150.0);
fgSystem.addConsumer("Pilot", ConsumerType.FLARE_PILOT, 50.0);

// Run and get results
fgSystem.run();

// Results
System.out.println("Fuel Gas System Results:");
System.out.println("  JT Cooling: " + fgSystem.getJTCooling() + " °C");
System.out.println("  Heater Duty: " + fgSystem.getHeaterDutyKW() + " kW");
System.out.println("  Dew Point: " + fgSystem.getDewPointC() + " °C");
System.out.println("  Superheat: " + fgSystem.getSuperheatC() + " °C");
System.out.println("  Wobbe Index: " + fgSystem.getWobbeIndex() + " MJ/Sm³");
System.out.println("  LHV: " + fgSystem.getLHV() + " MJ/kg");

// Get JSON report
String report = fgSystem.toJson();
```

### Python Example

```python
from neqsim import jneqsim

# Create fuel gas system
FuelGasSystem = jneqsim.process.equipment.util.FuelGasSystem
ConsumerType = jneqsim.process.equipment.util.FuelGasSystem.ConsumerType

# Create fluid and stream
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream

gas = SystemSrkEos(273.15 + 30.0, 70.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.08)
gas.addComponent("propane", 0.04)
gas.addComponent("CO2", 0.02)
gas.addComponent("H2S", 0.00001)
gas.setMixingRule("classic")

source = Stream("HP Fuel Gas", gas)
source.setFlowRate(2000.0, "kg/hr")
source.run()

# Create fuel gas system
fg_system = FuelGasSystem("Platform FG", source)
fg_system.setOutletPressure(30.0)
fg_system.setOutletTemperature(50.0)

# Add consumers
fg_system.addConsumer("GT-101", ConsumerType.GAS_TURBINE, 800.0)
fg_system.addConsumer("H-101", ConsumerType.FIRED_HEATER, 150.0)

fg_system.run()

print(f"Heater duty: {fg_system.getHeaterDutyKW():.1f} kW")
print(f"Wobbe Index: {fg_system.getWobbeIndex():.1f} MJ/Sm³")
print(f"Superheat: {fg_system.getSuperheatC():.1f} °C above dew point")
```

### Calculating Gas Quality

```java
// Calculate heating values and combustion indices
fgSystem.run();

// Lower Heating Value
double LHV = fgSystem.getLHV();  // MJ/kg
double LHV_vol = fgSystem.getLHV("MJ/Sm3");  // MJ/Sm³

// Wobbe Index (combustion interchangeability)
double WI = fgSystem.getWobbeIndex();  // MJ/Sm³

// Check if within turbine limits (typically ±5%)
double designWI = 50.0;  // MJ/Sm³
double deviation = Math.abs((WI - designWI) / designWI * 100);
if (deviation > 5.0) {
    System.out.println("WARNING: Wobbe Index outside turbine limits!");
}
```

---

## Quality Calculations

### Wobbe Index

The Wobbe Index indicates fuel gas interchangeability:

$$WI = \frac{HHV}{\sqrt{SG}}$$

Where:
- $HHV$ = Higher Heating Value (MJ/Sm³)
- $SG$ = Specific Gravity relative to air

### Dew Point and Superheat

The system calculates:
1. **Cricondentherm** - Maximum dew point temperature at any pressure
2. **Dew point at delivery pressure** - Actual condensation temperature
3. **Superheat** - Temperature margin above dew point

```java
// Get dew point at delivery pressure
double dewPoint = fgSystem.getDewPointAtDeliveryPressure();  // °C

// Get actual superheat
double superheat = fgSystem.getSuperheatC();  // °C

// Check gas turbine requirement (>25°C superheat)
if (superheat < 25.0) {
    System.out.println("Increase heater duty for gas turbine!");
}
```

### JT Cooling Effect

Pressure letdown causes Joule-Thomson cooling:

```java
// Get JT cooling across letdown valve
double jtCooling = fgSystem.getJTCooling();  // °C (negative = cooling)

System.out.println("JT cooling: " + jtCooling + " °C");
// Typical: -5 to -15°C depending on composition and pressure drop
```

---

## API Reference

### Constructors

| Constructor | Description |
|-------------|-------------|
| `FuelGasSystem(String name)` | Create system with name |
| `FuelGasSystem(String name, StreamInterface inlet)` | Create with inlet stream |

### Configuration Methods

| Method | Description |
|--------|-------------|
| `setInletStream(StreamInterface)` | Set fuel gas source |
| `setOutletPressure(double)` | Set delivery pressure (barg) |
| `setOutletTemperature(double)` | Set delivery temperature (°C) |
| `addConsumer(String, ConsumerType, double)` | Add consumer (name, type, flow kg/hr) |
| `setTotalDemand(double)` | Set total fuel gas demand (kg/hr) |

### Results Methods

| Method | Description |
|--------|-------------|
| `getJTCooling()` | Get JT temperature drop (°C) |
| `getHeaterDutyKW()` | Get required heater duty (kW) |
| `getDewPointC()` | Get dew point at delivery (°C) |
| `getSuperheatC()` | Get superheat above dew point (°C) |
| `getWobbeIndex()` | Get Wobbe Index (MJ/Sm³) |
| `getLHV()` | Get Lower Heating Value (MJ/kg) |
| `getLHV(String unit)` | Get LHV in specified unit |
| `getOutletStream()` | Get conditioned fuel gas stream |
| `toJson()` | Get full results as JSON |

### Consumer Management

| Method | Description |
|--------|-------------|
| `addConsumer(String, ConsumerType, double)` | Add fuel gas consumer |
| `getConsumers()` | Get list of consumers |
| `getTotalDemand()` | Get total demand (kg/hr) |
| `getConsumerDemand(String)` | Get specific consumer demand |

---

## Related Documentation

- [Flare Systems](flares) - Flare and relief systems
- [Gas Turbines](power_generation) - Power generation equipment
- [Measurement Devices](measurement_devices) - CombustionEmissionsCalculator
- [Emissions Reporting](../../emissions/OFFSHORE_EMISSION_REPORTING) - Emission calculations
