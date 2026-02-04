---
title: Stream Saturator Utility
description: Water saturation utility for simulating reservoir conditions, wet gas systems, and water content analysis in natural gas streams.
---

# Stream Saturator Utility

The `StreamSaturatorUtil` is a utility unit operation that saturates a gas or hydrocarbon stream with water at the stream's current temperature and pressure conditions. This is essential for simulating:

- **Reservoir conditions** where gas is in equilibrium with formation water
- **Wet gas systems** before dehydration
- **Water content analysis** for hydrate prediction
- **Produced water estimation** in gas processing

## Overview

| Property | Value |
|----------|-------|
| **Class** | `neqsim.process.equipment.util.StreamSaturatorUtil` |
| **Type** | Two-port equipment (inlet → outlet) |
| **Operation** | Adds water to saturation at inlet T, P |

## Basic Usage

### Java

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.util.StreamSaturatorUtil;
import neqsim.thermo.system.SystemSrkEos;

// Create a dry gas stream
SystemSrkEos fluid = new SystemSrkEos(273.15 + 30.0, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("CO2", 0.02);
fluid.setMixingRule("classic");

Stream dryGas = new Stream("Dry Gas", fluid);
dryGas.setFlowRate(100000.0, "Sm3/day");
dryGas.setTemperature(30.0, "C");
dryGas.setPressure(50.0, "bara");
dryGas.run();

// Saturate with water
StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", dryGas);
saturator.run();

// Get saturated stream
Stream wetGas = new Stream("Wet Gas", saturator.getOutletStream());
wetGas.run();

// Check water content
double waterContent = wetGas.getFluid().getComponent("water").getx() * 1e6; // ppm molar
System.out.println("Water content: " + waterContent + " ppm (molar)");
```

### Python (neqsim-python)

```python
from neqsim import jneqsim

# Create fluid
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
fluid = SystemSrkEos(273.15 + 30.0, 50.0)
fluid.addComponent("methane", 0.90)
fluid.addComponent("ethane", 0.05)
fluid.addComponent("propane", 0.03)
fluid.addComponent("CO2", 0.02)
fluid.setMixingRule("classic")

# Create stream and saturator
Stream = jneqsim.process.equipment.stream.Stream
StreamSaturatorUtil = jneqsim.process.equipment.util.StreamSaturatorUtil

dry_gas = Stream("Dry Gas", fluid)
dry_gas.setFlowRate(100000.0, "Sm3/day")
dry_gas.setTemperature(30.0, "C")
dry_gas.setPressure(50.0, "bara")
dry_gas.run()

saturator = StreamSaturatorUtil("Water Saturator", dry_gas)
saturator.run()

wet_gas = Stream("Wet Gas", saturator.getOutletStream())
wet_gas.run()

# Get water content
water_mole_frac = wet_gas.getFluid().getComponent("water").getx()
print(f"Water content: {water_mole_frac * 1e6:.1f} ppm (molar)")
```

## Configuration Options

### Multi-Phase Check

Enable multi-phase calculations to handle systems where liquid water may form:

```java
saturator.setMultiPhase(true);  // Default: true
```

When `multiPhase = true`, the saturator enables multi-phase thermodynamic calculations to properly handle:
- Gas + aqueous systems
- Three-phase (gas + oil + water) systems
- Condensate systems with water

### Approach to Saturation

Control the degree of saturation (useful for sensitivity studies):

```java
// 100% saturated (default)
saturator.setApprachToSaturation(1.0);

// 80% of saturation water content
saturator.setApprachToSaturation(0.8);

// 50% saturated (undersaturated)
saturator.setApprachToSaturation(0.5);
```

This is useful for:
- Modeling partially saturated reservoir gas
- Sensitivity analysis on water content
- Simulating gas that hasn't reached equilibrium

## Process System Integration

### In a Full Process Model

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.compressor.Compressor;

ProcessSystem process = new ProcessSystem();

// Feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(50000.0, "kg/hr");
feed.setTemperature(40.0, "C");
feed.setPressure(37.0, "bara");
process.add(feed);

// Saturate with water (simulate reservoir conditions)
StreamSaturatorUtil saturator = new StreamSaturatorUtil("Water Saturator", feed);
process.add(saturator);

// Saturated stream for downstream processing
Stream wetFeed = new Stream("Wet Feed", saturator.getOutletStream());
process.add(wetFeed);

// Inlet separator (will now show water phase)
Separator inletSep = new Separator("Inlet Separator", wetFeed);
process.add(inletSep);

// Run process
process.run();

// Check separator outputs
System.out.println("Gas rate: " + inletSep.getGasOutStream().getFlowRate("MSm3/day") + " MSm3/day");
System.out.println("Water rate: " + inletSep.getLiquidOutStream().getFlowRate("kg/hr") + " kg/hr");
```

## Use Cases

### 1. Reservoir Gas Saturation

Simulate well stream conditions where gas is saturated with formation water:

```java
// Reservoir conditions: 100°C, 200 bara
Stream reservoirGas = new Stream("Reservoir Gas", fluid);
reservoirGas.setTemperature(100.0, "C");
reservoirGas.setPressure(200.0, "bara");
reservoirGas.run();

StreamSaturatorUtil saturator = new StreamSaturatorUtil("Formation Water Saturation", reservoirGas);
saturator.run();
```

### 2. Hydrate Analysis Preparation

Ensure water is present for hydrate equilibrium calculations:

```java
StreamSaturatorUtil saturator = new StreamSaturatorUtil("Saturator", dryGas);
saturator.run();

// Now calculate hydrate temperature
ThermodynamicOperations ops = new ThermodynamicOperations(saturator.getOutletStream().getFluid());
ops.hydrateFormationTemperature();
double hydrateTemp = saturator.getOutletStream().getFluid().getTemperature() - 273.15;
System.out.println("Hydrate formation temperature: " + hydrateTemp + " °C");
```

### 3. Dehydration Unit Feed

Set up realistic feed conditions for TEG dehydration simulation:

```java
// Wet gas feed to dehydration
StreamSaturatorUtil saturator = new StreamSaturatorUtil("Inlet Saturator", inletGas);
saturator.run();

// Now feed to TEG contactor
// Water content in feed is at saturation
SimpleAbsorber tegContactor = new SimpleAbsorber("TEG Contactor");
tegContactor.setGasFeedStream(saturator.getOutletStream());
tegContactor.setLiquidFeedStream(leanTEG);
```

### 4. Produced Water Estimation

Estimate water production from saturated reservoir gas:

```java
// High-pressure wellhead gas
Stream wellheadGas = new Stream("Wellhead", fluid);
wellheadGas.setFlowRate(5.0, "MSm3/day");
wellheadGas.setTemperature(80.0, "C");
wellheadGas.setPressure(150.0, "bara");
wellheadGas.run();

// Saturate at reservoir conditions
StreamSaturatorUtil saturator = new StreamSaturatorUtil("Saturator", wellheadGas);
saturator.run();

// Cool and separate (simulates surface processing)
Heater cooler = new Heater("Cooler", saturator.getOutletStream());
cooler.setOutTemperature(30.0, "C");
cooler.run();

ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator", cooler.getOutletStream());
separator.run();

// Water production rate
double waterRate = separator.getWaterOutStream().getFlowRate("m3/day");
System.out.println("Produced water: " + waterRate + " m3/day");
```

## API Reference

### Constructor

```java
StreamSaturatorUtil(String name, StreamInterface inStream)
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `name` | String | Unit operation name |
| `inStream` | StreamInterface | Input stream to saturate |

### Methods

| Method | Description |
|--------|-------------|
| `run()` | Execute saturation calculation |
| `getOutletStream()` | Get saturated output stream |
| `setMultiPhase(boolean)` | Enable/disable multi-phase check (default: true) |
| `isMultiPhase()` | Check if multi-phase is enabled |
| `setApprachToSaturation(double)` | Set saturation fraction (0.0-1.0, default: 1.0) |

## Technical Notes

1. **Water Component**: The saturator uses the thermodynamic `saturateWithWater()` operation, which adds water to the system until phase equilibrium is reached.

2. **Temperature/Pressure**: Saturation is calculated at the inlet stream's temperature and pressure. The outlet stream has the same T and P.

3. **Flash Calculation**: After saturation, a TP flash is performed to establish phase equilibrium.

4. **Initialization Level**: The outlet stream is initialized to level 3 (full property calculation).

## Related Documentation

- [Streams](../streams.md) - Stream handling
- [Separators](../separators.md) - Phase separation
- [Hydrate Modeling](../../../thermo/hydrate_models.md) - Hydrate calculations
- [Recycles and Adjusters](adjusters.md) - Process convergence utilities
