---
title: Water Cooler and Reboiler
description: Documentation for WaterCooler and ReBoiler heat exchanger equipment in NeqSim.
---

# Water Cooler and Reboiler

Documentation for specialized heat exchangers: WaterCooler for seawater/cooling water systems and ReBoiler for distillation column reboilers.

## Table of Contents
- [Water Cooler](#water-cooler)
- [Reboiler](#reboiler)
- [API Reference](#api-reference)

---

## Water Cooler

**Location:** `neqsim.process.equipment.heatexchanger.WaterCooler`

The `WaterCooler` class models process coolers using seawater or cooling water, with automatic calculation of cooling water flow rate using IAPWS IF97 steam tables.

### Features

- Uses IAPWS IF97 for accurate water properties
- Automatic cooling water flow calculation
- Configurable water inlet/outlet temperatures
- Forces water property model on streams

### Java Example

```java
import neqsim.process.equipment.heatexchanger.WaterCooler;

// Create process stream to be cooled
Stream hotStream = new Stream("Hot Process Gas", processFluid);
hotStream.setTemperature(120.0, "C");
hotStream.setFlowRate(5000.0, "kg/hr");
hotStream.run();

// Create water cooler
WaterCooler cooler = new WaterCooler("E-201", hotStream);

// Set process side outlet temperature
cooler.setOutTemperature(40.0, "C");

// Configure cooling water conditions
cooler.setWaterInletTemperature(15.0, "C");    // Seawater inlet
cooler.setWaterOutletTemperature(25.0, "C");   // Seawater outlet
cooler.setWaterPressure(3.0, "bara");          // Cooling water pressure

cooler.run();

// Get results
double duty = -cooler.getDuty() / 1000.0;  // kW (negative for cooling)
double cwFlow = cooler.getCoolingWaterFlowRate("kg/hr");

System.out.println("Cooling duty: " + duty + " kW");
System.out.println("Cooling water flow: " + cwFlow + " kg/hr");
```

### Python Example

```python
from neqsim import jneqsim

WaterCooler = jneqsim.process.equipment.heatexchanger.WaterCooler

# Create water cooler
cooler = WaterCooler("E-201", hot_stream)
cooler.setOutTemperature(40.0, "C")

# Configure cooling water
cooler.setWaterInletTemperature(15.0, "C")
cooler.setWaterOutletTemperature(25.0, "C")
cooler.setWaterPressure(3.0, "bara")

cooler.run()

print(f"Duty: {-cooler.getDuty()/1000:.1f} kW")
print(f"CW flow: {cooler.getCoolingWaterFlowRate('kg/hr'):.0f} kg/hr")
```

### Calculation Basis

The cooling water flow rate is calculated using IAPWS IF97:

$$\dot{m}_{cw} = \frac{Q}{h_{out} - h_{in}}$$

Where:
- $Q$ = Heat duty from process side (W)
- $h_{out}$ = Enthalpy of cooling water at outlet (kJ/kg)
- $h_{in}$ = Enthalpy of cooling water at inlet (kJ/kg)

### Typical Applications

| Application | CW Inlet (°C) | CW Outlet (°C) | Notes |
|-------------|---------------|----------------|-------|
| Seawater (North Sea) | 4-12 | 20-30 | Seasonal variation |
| Seawater (Gulf) | 25-32 | 35-45 | Higher temperatures |
| Cooling tower | 25-30 | 35-40 | Closed loop |
| River water | 10-20 | 25-35 | Environmental limits |

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
