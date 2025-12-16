# Streams

Documentation for process streams in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Stream Class](#stream-class)
- [Stream Properties](#stream-properties)
- [Virtual Streams](#virtual-streams)
- [Neqstream](#neqstream)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.stream`

**Classes:**
| Class | Description |
|-------|-------------|
| `Stream` | Basic process stream |
| `StreamInterface` | Stream interface |
| `VirtualStream` | Reference to another stream |
| `NeqStream` | Extended stream with additional features |
| `EnergyStream` | Energy/heat stream |

Streams are the fundamental connections between process equipment, carrying material and energy through the flowsheet.

---

## Stream Class

### Creating Streams

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;

// From fluid system
SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
```

### Setting Conditions

```java
// Temperature
feed.setTemperature(300.0, "K");
feed.setTemperature(25.0, "C");

// Pressure
feed.setPressure(50.0, "bara");
feed.setPressure(725.0, "psia");

// Flow rate
feed.setFlowRate(10000.0, "kg/hr");
feed.setFlowRate(500.0, "kmol/hr");
feed.setFlowRate(1000000.0, "Sm3/day");
feed.setFlowRate(100.0, "m3/hr");

// Run to calculate properties
feed.run();
```

### Component Flow Rates

```java
// Set by mole fractions (normalized automatically)
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);

// Set by absolute molar flows
fluid.addComponent("methane", 100.0);  // 100 moles
fluid.addComponent("ethane", 7.0);     // 7 moles
```

---

## Stream Properties

### Getting Properties

```java
// Thermodynamic properties
double T = stream.getTemperature("C");
double P = stream.getPressure("bara");
double H = stream.getEnthalpy("kJ/kg");
double S = stream.getEntropy("kJ/kgK");

// Flow properties
double massFlow = stream.getFlowRate("kg/hr");
double molarFlow = stream.getFlowRate("kmol/hr");
double volFlow = stream.getFlowRate("m3/hr");
double stdVolFlow = stream.getFlowRate("Sm3/day");

// Density
double density = stream.getDensity("kg/m3");

// Molecular weight
double MW = stream.getMolarMass("kg/kmol");
```

### Phase Properties

```java
// Get fluid object
SystemInterface fluid = stream.getFluid();

// Phase fractions
double gasPhase = fluid.getGasPhase().getBeta();
double liquidPhase = fluid.getLiquidPhase().getBeta();

// Component mole fractions in each phase
double methaneInGas = fluid.getGasPhase().getComponent("methane").getx();
double methaneInLiq = fluid.getLiquidPhase().getComponent("methane").getx();

// Phase properties
double gasDensity = fluid.getGasPhase().getDensity("kg/m3");
double gasViscosity = fluid.getGasPhase().getViscosity("cP");
```

### Composition

```java
// Overall mole fractions
double[] moleFractions = stream.getFluid().getMoleFractions();

// Component mole fraction
double methane = stream.getFluid().getMoleFraction("methane");

// Mass fractions
double[] massFractions = stream.getFluid().getMassFractions();
```

---

## Virtual Streams

Reference another stream without copying.

### Usage

```java
import neqsim.process.equipment.stream.VirtualStream;

// Original stream
Stream original = new Stream("Original", fluid);
original.setFlowRate(1000.0, "kg/hr");

// Virtual reference
VirtualStream virtual = new VirtualStream("Virtual", original);

// Changes to original propagate to virtual
original.setFlowRate(2000.0, "kg/hr");
original.run();

virtual.run();  // Uses updated flow
```

---

## Neqstream

Extended stream with additional features.

### Usage

```java
import neqsim.process.equipment.stream.NeqStream;

NeqStream stream = new NeqStream("Process Stream", fluid);
stream.setFlowRate(1000.0, "kg/hr");
stream.run();

// Extended methods
double wobbe = stream.getWobbeIndex();
double gcv = stream.getGCV("MJ/Sm3");
```

---

## Examples

### Example 1: Define Feed Stream

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;

// Natural gas composition
SystemSrkEos gas = new SystemSrkEos(298.15, 70.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.03);
gas.addComponent("i-butane", 0.01);
gas.addComponent("n-butane", 0.015);
gas.addComponent("i-pentane", 0.005);
gas.setMixingRule("classic");

Stream feed = new Stream("Natural Gas Feed", gas);
feed.setFlowRate(10.0, "MSm3/day");  // Million Sm3/day
feed.run();

System.out.println("Feed pressure: " + feed.getPressure("bara") + " bara");
System.out.println("Feed temperature: " + feed.getTemperature("C") + " C");
System.out.println("Feed rate: " + feed.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Density: " + feed.getDensity("kg/m3") + " kg/m³");
```

### Example 2: Stream Properties Report

```java
Stream stream = processEquipment.getOutletStream();
stream.run();

System.out.println("=== Stream Properties ===");
System.out.println("Temperature: " + String.format("%.2f", stream.getTemperature("C")) + " °C");
System.out.println("Pressure: " + String.format("%.2f", stream.getPressure("bara")) + " bara");
System.out.println("Mass flow: " + String.format("%.2f", stream.getFlowRate("kg/hr")) + " kg/hr");
System.out.println("Molar flow: " + String.format("%.2f", stream.getFlowRate("kmol/hr")) + " kmol/hr");
System.out.println("Std gas flow: " + String.format("%.2f", stream.getFlowRate("Sm3/hr")) + " Sm³/hr");
System.out.println("Density: " + String.format("%.2f", stream.getDensity("kg/m3")) + " kg/m³");
System.out.println("MW: " + String.format("%.2f", stream.getMolarMass("kg/kmol")) + " kg/kmol");

System.out.println("\n=== Composition (mol%) ===");
for (int i = 0; i < stream.getFluid().getNumberOfComponents(); i++) {
    String name = stream.getFluid().getComponent(i).getName();
    double frac = stream.getFluid().getComponent(i).getz() * 100;
    System.out.println(name + ": " + String.format("%.3f", frac) + "%");
}
```

### Example 3: Two-Phase Stream

```java
// Oil and gas mixture
SystemSrkEos mixture = new SystemSrkEos(350.0, 30.0);
mixture.addComponent("methane", 0.30);
mixture.addComponent("ethane", 0.10);
mixture.addComponent("propane", 0.15);
mixture.addComponent("n-heptane", 0.25);
mixture.addComponent("n-decane", 0.20);
mixture.setMixingRule("classic");

Stream twoPhase = new Stream("Two-Phase", mixture);
twoPhase.setFlowRate(5000.0, "kg/hr");
twoPhase.run();

// Phase split
SystemInterface fluid = twoPhase.getFluid();
double gasWeight = fluid.getGasPhase().getBeta() * fluid.getGasPhase().getMolarMass();
double liqWeight = fluid.getLiquidPhase().getBeta() * fluid.getLiquidPhase().getMolarMass();
double totalWeight = gasWeight + liqWeight;

double gasFrac = gasWeight / totalWeight;
double liqFrac = liqWeight / totalWeight;

System.out.println("Gas fraction: " + String.format("%.1f", gasFrac * 100) + " wt%");
System.out.println("Liquid fraction: " + String.format("%.1f", liqFrac * 100) + " wt%");
```

### Example 4: Clone Stream

```java
// Original stream
Stream original = new Stream("Original", fluid);
original.setFlowRate(1000.0, "kg/hr");
original.run();

// Clone for parallel processing
Stream cloned = original.clone();
cloned.setName("Cloned");

// Modify clone independently
cloned.setFlowRate(500.0, "kg/hr");
cloned.run();

System.out.println("Original: " + original.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Cloned: " + cloned.getFlowRate("kg/hr") + " kg/hr");
```

---

## Flow Rate Units

| Unit | Description |
|------|-------------|
| `"kg/hr"` | Kilograms per hour |
| `"kg/sec"` | Kilograms per second |
| `"kmol/hr"` | Kilomoles per hour |
| `"m3/hr"` | Actual cubic meters per hour |
| `"Sm3/hr"` | Standard cubic meters per hour (15°C, 1 atm) |
| `"Sm3/day"` | Standard cubic meters per day |
| `"MSm3/day"` | Million standard cubic meters per day |
| `"barrel/day"` | Barrels per day |

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Mixers and Splitters](mixers_splitters.md) - Stream handling
- [Separators](separators.md) - Phase separation
