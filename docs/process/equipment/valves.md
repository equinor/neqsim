# Valve Equipment

Documentation for valve equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Valve Types](#valve-types)
- [Sizing and Cv](#sizing-and-cv)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.valve`

**Classes:**
- `ThrottlingValve` - Joule-Thomson throttling valve
- `ValveInterface` - Valve interface
- `SafetyValve` - Pressure relief valve

---

## Throttling Valve

### Basic Usage

```java
import neqsim.process.equipment.valve.ThrottlingValve;

ThrottlingValve valve = new ThrottlingValve("FV-100", inletStream);
valve.setOutletPressure(30.0, "bara");
valve.run();

// Joule-Thomson cooling
double Tin = inletStream.getTemperature("C");
double Tout = valve.getOutletStream().getTemperature("C");
double deltaT = Tout - Tin;

System.out.println("Temperature change: " + deltaT + " °C");
```

### Isenthalpic Expansion

Throttling is isenthalpic (constant enthalpy):

```java
double H_in = inletStream.getEnthalpy("J/mol");
double H_out = valve.getOutletStream().getEnthalpy("J/mol");
// H_in ≈ H_out (within numerical precision)
```

---

## Valve Sizing

### Flow Coefficient (Cv)

```java
// Set Cv
valve.setCv(150.0, "US");  // US gallons/min at 1 psi ΔP
// Or
valve.setCv(150.0, "SI");  // m³/hr at 1 bar ΔP

// Get Cv at current conditions
double Cv = valve.getCv("US");
```

### Valve Opening

```java
// Set valve position
valve.setPercentValveOpening(50.0);  // 50% open

// Cv varies with opening (inherent characteristic)
valve.setValveCharacteristic("linear");
// or
valve.setValveCharacteristic("equal_percentage");
// or
valve.setValveCharacteristic("quick_opening");
```

### Calculate Pressure Drop

```java
// Given Cv and flow, calculate ΔP
valve.setCv(100.0, "US");
valve.setPercentValveOpening(75.0);
valve.run();

double Pin = inletStream.getPressure("bara");
double Pout = valve.getOutletStream().getPressure("bara");
double deltaP = Pin - Pout;
```

---

## Critical Flow

For high pressure drops, flow becomes critical (choked).

```java
// Check if flow is critical
boolean isCritical = valve.isCriticalFlow();

// Critical flow factor
double Cf = valve.getCriticalFlowFactor();
```

---

## Safety Valve (PSV)

### Basic Setup

```java
import neqsim.process.equipment.valve.SafetyValve;

SafetyValve psv = new SafetyValve("PSV-100", vessel);
psv.setSetPressure(100.0, "barg");  // Set pressure
psv.setBlowdownPressure(10.0, "%"); // 10% blowdown
psv.run();

// Check if valve is open
boolean isOpen = psv.isOpen();
double relievingFlow = psv.getRelievingFlow("kg/hr");
```

### Sizing

```java
// Required relieving capacity
psv.setRequiredCapacity(10000.0, "kg/hr");

// Get required orifice area
double area = psv.getRequiredOrificeArea("cm2");

// Select API orifice
String orifice = psv.selectAPIorifice();  // e.g., "J", "K", "L"
```

---

## Choke Valve

For wellhead and production applications:

```java
import neqsim.process.equipment.valve.ChokeValve;

ChokeValve choke = new ChokeValve("Wellhead Choke", wellStream);
choke.setOutletPressure(50.0, "bara");
choke.run();

// Or specify bean size
choke.setBeanSize(32, "64ths");  // 32/64" = 0.5"
choke.run();

double Pout = choke.getOutletStream().getPressure("bara");
```

---

## Dynamic Simulation

```java
// Valve dynamics
valve.setCalculateSteadyState(false);

// Valve stroke time
valve.setStrokeTime(10.0);  // seconds for 0-100%

// Step change
valve.setPercentValveOpening(80.0);

for (double t = 0; t < 30; t += 0.1) {
    valve.runTransient();
    double opening = valve.getActualOpening();  // Lags setpoint
}
```

---

## Example: Pressure Letdown Station

```java
ProcessSystem process = new ProcessSystem();

// HP gas inlet
Stream hpGas = new Stream("HP Gas", gasFluid);
hpGas.setFlowRate(50000.0, "kg/hr");
hpGas.setTemperature(50.0, "C");
hpGas.setPressure(100.0, "bara");
process.add(hpGas);

// Stage 1: 100 -> 50 bar
ThrottlingValve pv1 = new ThrottlingValve("PV-100", hpGas);
pv1.setOutletPressure(50.0, "bara");
pv1.setCv(200.0, "US");
process.add(pv1);

// Heater (compensate JT cooling)
Heater heater = new Heater("E-100", pv1.getOutletStream());
heater.setOutTemperature(40.0, "C");
process.add(heater);

// Stage 2: 50 -> 10 bar
ThrottlingValve pv2 = new ThrottlingValve("PV-101", heater.getOutletStream());
pv2.setOutletPressure(10.0, "bara");
pv2.setCv(300.0, "US");
process.add(pv2);

process.run();

// JT effects
System.out.println("After PV-100: " + pv1.getOutletStream().getTemperature("C") + " °C");
System.out.println("After E-100: " + heater.getOutletStream().getTemperature("C") + " °C");
System.out.println("After PV-101: " + pv2.getOutletStream().getTemperature("C") + " °C");
```

---

## Joule-Thomson Coefficient

For ideal gases, $\mu_{JT} = 0$. For real gases:

$$\mu_{JT} = \left(\frac{\partial T}{\partial P}\right)_H = \frac{1}{C_p}\left[T\left(\frac{\partial V}{\partial T}\right)_P - V\right]$$

```java
// Get JT coefficient
double muJT = inletStream.getJouleThomsonCoefficient();  // K/bar
```

---

## Related Documentation

- [Process Package](../README.md) - Package overview
- [Separators](separators.md) - Separation equipment
- [Safety Systems](../safety/) - Safety valve sizing
