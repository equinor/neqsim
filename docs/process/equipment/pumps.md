# Pumps

Documentation for liquid pumping equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Pump Class](#pump-class)
- [Pump Performance](#pump-performance)
- [Head and Efficiency Curves](#head-and-efficiency-curves)
- [NPSH Calculations](#npsh-calculations)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.pump`

**Classes:**
| Class | Description |
|-------|-------------|
| `Pump` | Centrifugal or positive displacement pump |
| `PumpInterface` | Pump interface |

---

## Pump Class

### Basic Usage

```java
import neqsim.process.equipment.pump.Pump;

// Create pump on liquid stream
Pump pump = new Pump("P-100", liquidStream);
pump.setOutletPressure(50.0, "bara");
pump.run();

// Results
double power = pump.getPower("kW");
double head = pump.getHead("m");
double efficiency = pump.getIsentropicEfficiency();
```

### Outlet Specification

```java
// By outlet pressure
pump.setOutletPressure(50.0, "bara");

// By pressure rise
pump.setPressureRise(30.0, "bara");

// By head
pump.setHead(300.0, "m");
```

---

## Pump Performance

### Isentropic Efficiency

```java
// Set pump efficiency
pump.setIsentropicEfficiency(0.75);  // 75%

// Calculate power
pump.run();
double power = pump.getPower("kW");
double isentropicPower = pump.getIsentropicPower("kW");
double actualPower = pump.getActualPower("kW");

// Efficiency = Isentropic Power / Actual Power
```

### Power Calculation

The pump power is calculated as:

$$P = \frac{\dot{m} \cdot \Delta h_{isentropic}}{\eta_{isentropic}}$$

Where:
- $\dot{m}$ = mass flow rate
- $\Delta h_{isentropic}$ = isentropic enthalpy rise
- $\eta_{isentropic}$ = isentropic efficiency

### Head Calculation

$$H = \frac{\Delta P}{\rho \cdot g}$$

Where:
- $\Delta P$ = pressure rise
- $\rho$ = liquid density
- $g$ = gravitational acceleration

---

## Head and Efficiency Curves

### Define Pump Curves

```java
// Define head vs flow curve points
double[] flowRates = {0, 50, 100, 150, 200};      // m³/hr
double[] heads = {350, 340, 310, 260, 180};       // m
double[] efficiencies = {0, 0.65, 0.80, 0.75, 0.60};

pump.setHeadCurve(flowRates, heads, "m3/hr", "m");
pump.setEfficiencyCurve(flowRates, efficiencies, "m3/hr");
```

### Operating Point

```java
pump.run();

// Get operating point
double flowRate = pump.getInletStream().getFlowRate("m3/hr");
double actualHead = pump.getHead("m");
double actualEff = pump.getIsentropicEfficiency();
```

---

## NPSH Calculations

### Net Positive Suction Head

```java
// NPSH available from process conditions
double npshAvailable = pump.getNPSHAvailable("m");

// NPSH required (from pump curve)
double[] flows = {50, 100, 150, 200};
double[] npshReq = {1.5, 2.0, 3.0, 5.0};
pump.setNPSHRequiredCurve(flows, npshReq, "m3/hr", "m");

double npshRequired = pump.getNPSHRequired("m");

// Check cavitation margin
double margin = npshAvailable - npshRequired;
if (margin < 1.0) {
    System.out.println("Warning: Low NPSH margin");
}
```

### NPSH Available Calculation

$$NPSH_A = \frac{P_{suction}}{\rho g} + \frac{v^2}{2g} - \frac{P_{vapor}}{\rho g}$$

---

## Examples

### Example 1: Simple Pump

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pump.Pump;

// Create liquid stream
SystemSrkEos fluid = new SystemSrkEos(298.15, 5.0);
fluid.addComponent("n-heptane", 1.0);
fluid.setMixingRule("classic");

Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(100.0, "m3/hr");
feed.run();

// Pump
Pump pump = new Pump("P-100", feed);
pump.setOutletPressure(30.0, "bara");
pump.setIsentropicEfficiency(0.75);
pump.run();

// Results
System.out.println("Flow rate: " + pump.getInletStream().getFlowRate("m3/hr") + " m³/hr");
System.out.println("Head: " + pump.getHead("m") + " m");
System.out.println("Power: " + pump.getPower("kW") + " kW");
System.out.println("Efficiency: " + pump.getIsentropicEfficiency() * 100 + " %");
```

### Example 2: Pump with Curves

```java
// Define pump curves
double[] flows = {0, 25, 50, 75, 100, 125, 150};
double[] heads = {400, 395, 380, 355, 320, 270, 200};
double[] effs = {0, 0.55, 0.70, 0.78, 0.80, 0.75, 0.65};

Pump pump = new Pump("P-100", liquidStream);
pump.setHeadCurve(flows, heads, "m3/hr", "m");
pump.setEfficiencyCurve(flows, effs, "m3/hr");
pump.run();

// Operating point found on curves
System.out.println("Operating flow: " + pump.getInletStream().getFlowRate("m3/hr"));
System.out.println("Operating head: " + pump.getHead("m"));
System.out.println("Operating efficiency: " + pump.getIsentropicEfficiency());
```

### Example 3: Booster Pump System

```java
// Inlet conditions
SystemSrkEos crude = new SystemSrkEos(340.0, 3.0);
crude.addComponent("methane", 0.01);
crude.addComponent("n-pentane", 0.20);
crude.addComponent("n-heptane", 0.50);
crude.addComponent("n-decane", 0.29);
crude.setMixingRule("classic");

Stream feed = new Stream("Crude Feed", crude);
feed.setFlowRate(500.0, "m3/hr");
feed.run();

// First stage pump
Pump pump1 = new Pump("P-100A", feed);
pump1.setOutletPressure(20.0, "bara");
pump1.setIsentropicEfficiency(0.78);
pump1.run();

// Second stage pump
Pump pump2 = new Pump("P-100B", pump1.getOutletStream());
pump2.setOutletPressure(50.0, "bara");
pump2.setIsentropicEfficiency(0.75);
pump2.run();

// Total power
double totalPower = pump1.getPower("kW") + pump2.getPower("kW");
System.out.println("Total pump power: " + totalPower + " kW");
```

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Compressors](compressors.md) - Gas compression
- [Separators](separators.md) - Phase separation
