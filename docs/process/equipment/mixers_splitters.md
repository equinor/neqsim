# Mixers and Splitters

Documentation for stream mixing and splitting equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Mixer](#mixer)
- [Splitter](#splitter)
- [Static Mixer](#static-mixer)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.mixer`, `neqsim.process.equipment.splitter`

**Classes:**
| Class | Description |
|-------|-------------|
| `Mixer` | Combine multiple streams |
| `MixerInterface` | Mixer interface |
| `Splitter` | Split stream into fractions |
| `SplitterInterface` | Splitter interface |
| `StaticMixer` | Static mixing element |

---

## Mixer

Combine multiple streams into one outlet stream.

### Basic Usage

```java
import neqsim.process.equipment.mixer.Mixer;

Mixer mixer = new Mixer("M-100");
mixer.addStream(stream1);
mixer.addStream(stream2);
mixer.addStream(stream3);
mixer.run();

Stream mixed = mixer.getOutletStream();
```

### Mixing Calculation

The mixer performs mass and energy balance:

$$\dot{m}_{out} = \sum_i \dot{m}_i$$

$$\dot{m}_{out} \cdot h_{out} = \sum_i \dot{m}_i \cdot h_i$$

$$x_{j,out} = \frac{\sum_i \dot{m}_i \cdot x_{j,i}}{\sum_i \dot{m}_i}$$

### Pressure Handling

```java
// Default: outlet pressure = minimum inlet pressure
mixer.run();

// Or specify outlet pressure
mixer.setOutletPressure(20.0, "bara");
mixer.run();
```

---

## Splitter

Split a stream into multiple fractions.

### Basic Usage

```java
import neqsim.process.equipment.splitter.Splitter;

// Split into 2 streams
Splitter splitter = new Splitter("SP-100", inletStream, 2);
splitter.setSplitFactors(new double[]{0.7, 0.3});  // 70% and 30%
splitter.run();

Stream split1 = splitter.getSplitStream(0);  // 70%
Stream split2 = splitter.getSplitStream(1);  // 30%
```

### Split Factor Specification

```java
// By mass fractions (must sum to 1.0)
splitter.setSplitFactors(new double[]{0.5, 0.3, 0.2});

// By flow rates
splitter.setFlowRates(new double[]{100.0, 60.0, 40.0}, "kg/hr");
```

### Properties

All split streams have identical:
- Temperature
- Pressure
- Composition (mole fractions)

Only flow rate differs.

---

## Static Mixer

For inline mixing with pressure drop.

### Usage

```java
import neqsim.process.equipment.mixer.StaticMixer;

StaticMixer staticMixer = new StaticMixer("Static Mixer");
staticMixer.addStream(stream1);
staticMixer.addStream(stream2);
staticMixer.setPressureDrop(0.5, "bara");
staticMixer.run();
```

---

## Examples

### Example 1: Simple Stream Mixing

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.mixer.Mixer;

// Stream 1: Rich gas
SystemSrkEos gas1 = new SystemSrkEos(300.0, 50.0);
gas1.addComponent("methane", 0.80);
gas1.addComponent("ethane", 0.15);
gas1.addComponent("propane", 0.05);
gas1.setMixingRule("classic");

Stream stream1 = new Stream("Rich Gas", gas1);
stream1.setFlowRate(5000.0, "kg/hr");
stream1.run();

// Stream 2: Lean gas
SystemSrkEos gas2 = new SystemSrkEos(310.0, 50.0);
gas2.addComponent("methane", 0.95);
gas2.addComponent("ethane", 0.04);
gas2.addComponent("propane", 0.01);
gas2.setMixingRule("classic");

Stream stream2 = new Stream("Lean Gas", gas2);
stream2.setFlowRate(3000.0, "kg/hr");
stream2.run();

// Mix streams
Mixer mixer = new Mixer("M-100");
mixer.addStream(stream1);
mixer.addStream(stream2);
mixer.run();

// Results
Stream mixed = mixer.getOutletStream();
System.out.println("Mixed flow: " + mixed.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Mixed temp: " + mixed.getTemperature("C") + " C");
System.out.println("Methane: " + mixed.getFluid().getMoleFraction("methane"));
```

### Example 2: Recycle Split

```java
// Main process stream
Stream processStream = new Stream("Process", processFluid);
processStream.setFlowRate(10000.0, "kg/hr");
processStream.run();

// Split: 90% product, 10% recycle
Splitter splitter = new Splitter("Recycle Splitter", processStream, 2);
splitter.setSplitFactors(new double[]{0.90, 0.10});
splitter.run();

Stream product = splitter.getSplitStream(0);
Stream recycle = splitter.getSplitStream(1);

System.out.println("Product: " + product.getFlowRate("kg/hr") + " kg/hr");
System.out.println("Recycle: " + recycle.getFlowRate("kg/hr") + " kg/hr");
```

### Example 3: Multiple Stream Manifold

```java
// Create manifold mixer for 4 wells
Mixer manifold = new Mixer("Production Manifold");

for (int i = 1; i <= 4; i++) {
    SystemSrkEos wellFluid = new SystemSrkEos(350.0, 100.0 - i * 5);
    wellFluid.addComponent("methane", 0.85);
    wellFluid.addComponent("ethane", 0.08);
    wellFluid.addComponent("propane", 0.05);
    wellFluid.addComponent("water", 0.02);
    wellFluid.setMixingRule("classic");
    
    Stream wellStream = new Stream("Well " + i, wellFluid);
    wellStream.setFlowRate(1000.0 + i * 200, "Sm3/day");
    wellStream.run();
    
    manifold.addStream(wellStream);
}

manifold.run();

System.out.println("Total production: " + manifold.getOutletStream().getFlowRate("Sm3/day") + " Sm3/day");
System.out.println("Manifold pressure: " + manifold.getOutletStream().getPressure("bara") + " bara");
```

### Example 4: Product Distribution

```java
// Gas from separator
Stream gasProduct = separator.getGasOutStream();

// Distribute to 3 customers
Splitter distributor = new Splitter("Gas Distribution", gasProduct, 3);

// Set by flow rates
double[] rates = {5000.0, 3000.0, 2000.0};  // Sm3/hr
distributor.setFlowRates(rates, "Sm3/hr");
distributor.run();

for (int i = 0; i < 3; i++) {
    Stream customerStream = distributor.getSplitStream(i);
    System.out.println("Customer " + (i+1) + ": " + customerStream.getFlowRate("Sm3/hr") + " Sm3/hr");
}
```

### Example 5: Bypass Configuration

```java
// Main stream
Stream mainStream = new Stream("Main", fluid);
mainStream.setFlowRate(1000.0, "kg/hr");
mainStream.run();

// Split: 80% through heater, 20% bypass
Splitter bypass = new Splitter("Bypass", mainStream, 2);
bypass.setSplitFactors(new double[]{0.80, 0.20});
bypass.run();

// Heat 80%
Heater heater = new Heater("E-100", bypass.getSplitStream(0));
heater.setOutletTemperature(400.0, "K");
heater.run();

// Remix
Mixer remix = new Mixer("M-100");
remix.addStream(heater.getOutletStream());
remix.addStream(bypass.getSplitStream(1));
remix.run();

System.out.println("Bypass temp control: " + remix.getOutletStream().getTemperature("K") + " K");
```

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Streams](streams.md) - Stream handling
- [Controllers](../controllers.md) - Adjusters and recycles
