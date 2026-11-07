---
title: Water Cooler and Reboiler
description: Documentation for WaterCooler and ReBoiler heat exchanger equipment in NeqSim.
---

Documentation for specialized heat exchangers: WaterCooler for seawater/cooling water systems and ReBoiler for distillation column reboilers.

## Table of Contents
- [Water Cooler](#water-cooler)
- [Reboiler](#reboiler)
- [API Reference](#api-reference)

---

## Water Cooler

`neqsim.process.equipment.heatexchanger.WaterCooler` calculates cooling utility
flow from the process duty and reference-water enthalpy rise. Fresh water is the
default; `setCoolingWaterSalinity(35.0)` selects 35 g/kg solution seawater using
IF97 water plus the IAPWS-08 saline Gibbs contribution. The process-side fluid
retains its own thermodynamic and physical-property models.

Use `setOutletTemperature(value, unit)` for the process target and configure
`setWaterInletTemperature`, `setWaterOutletTemperature` and `setWaterPressure`.
The return temperature must exceed the supply temperature. Unsupported states,
non-finite inputs and a heating duty are rejected. Mass-flow units and actual
inlet-volume units are supported; standard gas-volume units are rejected.

See [Reference water and seawater cooling](reference_cooling_water) for the
complete compiled/executed Java example, two-stream `HeatExchanger` setup,
plant-level `CoolingWaterSystem` sizing, equations, units and validity limits.
A `WATER` physical-property enum does not by itself replace EOS heat capacity.

---

## Reboiler

**Location:** `neqsim.process.equipment.heatexchanger.ReBoiler`

The `ReBoiler` class provides heat input to distillation column bottoms through enthalpy addition (PH flash calculation).

### Features

- Duty-based specification
- Uses PH flash for outlet conditions
- Suitable for column reboiler service
- Simple enthalpy addition model

### Java Example

```java
import neqsim.process.equipment.heatexchanger.ReBoiler;

// Get bottoms stream from column
Stream columnsBottoms = column.getBottomsStream();

// Create reboiler
ReBoiler reboiler = new ReBoiler("Reboiler", columnsBottoms);
reboiler.setReboilerDuty(500000.0);  // 500 kW in Watts

reboiler.run();

// Get outlet conditions
double outletTemp = reboiler.getOutletStream().getTemperature("C");
double vaporFraction = reboiler.getOutletStream().getFluid().getVaporFraction();

System.out.println("Reboiler outlet temperature: " + outletTemp + " °C");
System.out.println("Vapor fraction: " + vaporFraction);
```

### Python Example

```python
from neqsim import jneqsim

ReBoiler = jneqsim.process.equipment.heatexchanger.ReBoiler

# Create reboiler on column bottoms
reboiler = ReBoiler("Reboiler", column.getBottomsStream())
reboiler.setReboilerDuty(500000.0)  # Watts

reboiler.run()

outlet = reboiler.getOutletStream()
print(f"Outlet T: {outlet.getTemperature('C'):.1f} °C")
print(f"Vapor fraction: {outlet.getFluid().getVaporFraction():.3f}")
```

### Calculation Method

The reboiler performs a PH flash:
1. Calculate inlet enthalpy
2. Add reboiler duty to enthalpy
3. Flash at constant pressure to find outlet T and phase split

$$H_{out} = H_{in} + Q_{reboiler}$$

### Integration with Distillation Column

```java
// Create distillation column with reboiler
DistillationColumn column = new DistillationColumn("Debutanizer", 20, true, true);
column.addFeedStream(feedStream, 10);

// Access internal reboiler
Reboiler columnReboiler = column.getReboiler();
columnReboiler.setReboilerDuty(duty);

// Or use separate reboiler equipment
Stream bottoms = column.getLiquidOutStream();
ReBoiler externalReboiler = new ReBoiler("Thermosiphon", bottoms);
externalReboiler.setReboilerDuty(calculatedDuty);
```

---

## API Reference

### WaterCooler

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `WaterCooler(String name)` | Create cooler with name |
| `WaterCooler(String name, StreamInterface inStream)` | Create with inlet stream |

#### Configuration Methods

| Method | Description |
|--------|-------------|
| `setWaterInletTemperature(double, String)` | Set CW inlet temperature |
| `setWaterOutletTemperature(double, String)` | Set CW outlet temperature |
| `setWaterPressure(double, String)` | Set CW pressure |
| `setOutTemperature(double, String)` | Set process outlet temperature |

#### Results Methods

| Method | Description |
|--------|-------------|
| `getCoolingWaterFlowRate(String)` | Get CW flow rate (kg/hr, kg/sec, etc.) |
| `getDuty()` | Get heat duty (W, negative for cooling) |
| `getOutletStream()` | Get cooled process stream |

### ReBoiler

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `ReBoiler(String name, StreamInterface inStream)` | Create with inlet stream |

#### Configuration Methods

| Method | Description |
|--------|-------------|
| `setReboilerDuty(double)` | Set heat input (Watts) |

#### Results Methods

| Method | Description |
|--------|-------------|
| `getReboilerDuty()` | Get heat duty (Watts) |
| `getOutletStream()` | Get heated outlet stream |

---

## Related Documentation

- [Heat Exchangers](heat_exchangers) - General heat exchanger documentation
- [Distillation](distillation) - Distillation column equipment
- [Steam Heater](../../wiki/steam_heater) - Steam heating calculations
- [Air Cooler](../../wiki/air_cooler) - Air-cooled heat exchangers
