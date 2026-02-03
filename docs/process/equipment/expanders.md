---
title: Expanders and Turbines
description: Documentation for expansion equipment in NeqSim.
---

# Expanders and Turbines

Documentation for expansion equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Expander Class](#expander-class)
- [Turboexpander](#turboexpander)
- [Power Recovery](#power-recovery)
- [Compander Systems](#compander-systems)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.expander`

**Classes:**
| Class | Description |
|-------|-------------|
| `Expander` | General gas expander |
| `TurboExpander` | Turboexpander with shaft coupling |
| `ExpanderCompressorModule` | Compander unit |

Gas expanders are used for:
- Power recovery from high-pressure gas
- Cryogenic cooling (JT effect + work extraction)
- NGL recovery processes
- LNG production

---

## Expander Class

### Basic Usage

```java
import neqsim.process.equipment.expander.Expander;

// Create expander
Expander expander = new Expander("EX-100", gasStream);
expander.setOutletPressure(10.0, "bara");
expander.setIsentropicEfficiency(0.85);
expander.run();

// Results
double power = expander.getPower("kW");
double outletTemp = expander.getOutletTemperature("C");
```

### Outlet Specification

```java
// By outlet pressure
expander.setOutletPressure(10.0, "bara");

// By pressure ratio
expander.setPressureRatio(5.0);

// By outlet temperature
expander.setOutletTemperature(-50.0, "C");
```

---

## Turboexpander

For direct shaft coupling to compressor.

### Basic Usage

```java
import neqsim.process.equipment.expander.TurboExpander;

TurboExpander turboExpander = new TurboExpander("TEX-100", gasStream);
turboExpander.setOutletPressure(10.0, "bara");
turboExpander.setIsentropicEfficiency(0.85);
turboExpander.run();
```

### Shaft Coupling

```java
// Couple expander to compressor
turboExpander.setCoupledCompressor(compressor);

// Power balance
turboExpander.run();
compressor.run();

double expanderPower = turboExpander.getPower("kW");
double compressorPower = compressor.getPower("kW");
double netPower = expanderPower - compressorPower;
```

---

## Power Recovery

### Isentropic Power

$$W_{isentropic} = \dot{m} \cdot (h_1 - h_{2s})$$

Where:
- $h_1$ = inlet enthalpy
- $h_{2s}$ = isentropic outlet enthalpy

### Actual Power

$$W_{actual} = \eta_{isentropic} \cdot W_{isentropic}$$

### Temperature Drop

```java
// Get temperatures
double T_in = expander.getInletTemperature("C");
double T_out = expander.getOutletTemperature("C");
double deltaT = T_in - T_out;

// Compare to JT expansion (throttling)
ThrottlingValve valve = new ThrottlingValve("JT", gasStream);
valve.setOutletPressure(10.0, "bara");
valve.run();

double T_out_JT = valve.getOutletTemperature("C");
double deltaT_JT = T_in - T_out_JT;

System.out.println("Expander cooling: " + deltaT + " C");
System.out.println("JT cooling: " + deltaT_JT + " C");
```

---

## Compander Systems

Combined expander-compressor on single shaft.

### Usage

```java
import neqsim.process.equipment.expander.ExpanderCompressorModule;

ExpanderCompressorModule compander = new ExpanderCompressorModule("Compander");
compander.setExpanderInletStream(hotGas);
compander.setCompressorInletStream(coldGas);
compander.setExpanderOutletPressure(10.0, "bara");
compander.setCompressorOutletPressure(40.0, "bara");
compander.setIsentropicEfficiency(0.85);
compander.run();

double netPower = compander.getNetPower("kW");  // Can be positive or negative
```

---

## Examples

### Example 1: Simple Expander

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.expander.Expander;

// High pressure gas
SystemSrkEos gas = new SystemSrkEos(320.0, 80.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream feed = new Stream("HP Gas", gas);
feed.setFlowRate(50000.0, "kg/hr");
feed.run();

// Expander
Expander expander = new Expander("EX-100", feed);
expander.setOutletPressure(20.0, "bara");
expander.setIsentropicEfficiency(0.85);
expander.run();

System.out.println("Inlet: " + feed.getTemperature("C") + " C, " + feed.getPressure("bara") + " bara");
System.out.println("Outlet: " + expander.getOutletTemperature("C") + " C, " + expander.getOutletPressure("bara") + " bara");
System.out.println("Power generated: " + expander.getPower("kW") + " kW");
```

### Example 2: NGL Recovery with Turboexpander

```java
// Rich gas feed
SystemSrkEos richGas = new SystemSrkEos(300.0, 70.0);
richGas.addComponent("nitrogen", 0.02);
richGas.addComponent("methane", 0.75);
richGas.addComponent("ethane", 0.10);
richGas.addComponent("propane", 0.08);
richGas.addComponent("n-butane", 0.05);
richGas.setMixingRule("classic");

Stream feed = new Stream("Rich Gas", richGas);
feed.setFlowRate(100000.0, "Sm3/day");
feed.run();

// Pre-cooling
Cooler precooler = new Cooler("Pre-cooler", feed);
precooler.setOutletTemperature(280.0, "K");
precooler.run();

// Turboexpander
TurboExpander expander = new TurboExpander("TEX-100", precooler.getOutletStream());
expander.setOutletPressure(25.0, "bara");
expander.setIsentropicEfficiency(0.82);
expander.run();

// Cold separator
Separator coldSep = new Separator("Cold Sep", expander.getOutletStream());
coldSep.run();

// Results
System.out.println("Expander outlet: " + expander.getOutletTemperature("C") + " C");
System.out.println("Power: " + expander.getPower("kW") + " kW");
System.out.println("NGL recovered: " + coldSep.getLiquidOutStream().getFlowRate("m3/hr") + " m³/hr");
```

### Example 3: Expander vs JT Valve Comparison

```java
// Same inlet conditions
SystemSrkEos gas = new SystemSrkEos(300.0, 60.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream feed1 = new Stream("Feed 1", gas);
feed1.setFlowRate(10000.0, "kg/hr");
feed1.run();

Stream feed2 = new Stream("Feed 2", gas.clone());
feed2.setFlowRate(10000.0, "kg/hr");
feed2.run();

// JT valve
ThrottlingValve valve = new ThrottlingValve("JT Valve", feed1);
valve.setOutletPressure(15.0, "bara");
valve.run();

// Expander
Expander expander = new Expander("Expander", feed2);
expander.setOutletPressure(15.0, "bara");
expander.setIsentropicEfficiency(0.85);
expander.run();

System.out.println("JT Valve outlet: " + valve.getOutletTemperature("C") + " C");
System.out.println("Expander outlet: " + expander.getOutletTemperature("C") + " C");
System.out.println("Extra cooling: " + (valve.getOutletTemperature("C") - expander.getOutletTemperature("C")) + " C");
System.out.println("Power recovered: " + expander.getPower("kW") + " kW");
```

---

## Related Documentation

- [Equipment Index](./\) - All equipment
- [Compressors](compressors) - Gas compression
- [Valves](valves) - JT valves
- [Heat Exchangers](heat_exchangers) - Heat integration
