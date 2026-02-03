---
title: Valve Equipment
description: Documentation for valve equipment in NeqSim process simulation.
---

# Valve Equipment

Documentation for valve equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Valve Types](#valve-types)
- [Sizing and Cv](#sizing-and-cv)
- [Valve Characteristics](#valve-characteristics)
- [Sizing Standards](#sizing-standards)
- [Mechanical Design](#mechanical-design)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.valve`

**Classes:**
- `ThrottlingValve` - Joule-Thomson throttling valve (control valve)
- `ValveInterface` - Valve interface
- `SafetyValve` - Pressure relief valve
- `SafetyReliefValve` - Full PSV with API sizing
- `BlowdownValve` - Emergency blowdown valve
- `ControlValve` - Specialized control valve

**Mechanical Design:** `neqsim.process.mechanicaldesign.valve`
- `ValveMechanicalDesign` - Body sizing, weight, actuator calculations
- `ControlValveSizing` - IEC 60534 Cv/Kv calculations
- `ControlValveSizing_IEC_60534` - Full IEC implementation
- `ControlValveSizing_simple` - Production choke sizing

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

## Valve Characteristics

Control valves have inherent flow characteristics that define how Cv varies with valve opening.

### Available Characteristics

| Characteristic | Description | Best Application |
|----------------|-------------|------------------|
| **Linear** | Flow proportional to opening | Constant ΔP systems, bypass valves |
| **Equal Percentage** | Equal opening increments = equal % flow change | Variable ΔP, most process control |
| **Quick Opening** | Large flow change at small openings | On/off service, safety applications |
| **Modified Parabolic** | Compromise between linear and equal % | General purpose |

### Setting Valve Characteristic

```java
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.setValveCharacterization("equal percentage");
```

### Characteristic Curves

```
         │
   100%  │                    ●─── Quick Opening
         │               ●───●
   Flow  │          ●───●    ●── Linear
   (Cv)  │     ●───●        ●
         │ ●───●        ●───── Equal Percentage
         │●        ●───●
    0%   └────────────────────
         0%    Opening    100%
```

---

## Sizing Standards

NeqSim supports multiple valve sizing standards for different applications.

### Available Standards

| Standard | Code | Description |
|----------|------|-------------|
| Default | `default` | Simplified IEC 60534 for gas, standard for liquid |
| IEC 60534 | `IEC 60534` | Full IEC 60534-2-1 implementation |
| Extended | `IEC 60534 full` | IEC 60534 with all correction factors |
| Prod Choke | `prod choke` | Production choke with discharge coefficient |
| **Sachdeva** | `Sachdeva` | **Mechanistic two-phase choke model (SPE 15657)** |
| **Gilbert** | `Gilbert` | **Empirical two-phase correlation (1954)** |
| **Baxendell** | `Baxendell` | **Empirical two-phase correlation (1958)** |
| **Ros** | `Ros` | **Empirical two-phase correlation (1960)** |
| **Achong** | `Achong` | **Empirical two-phase correlation (1961)** |

### Setting Sizing Standard

```java
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.setValveSizingStandard("IEC 60534");

// For multiphase production chokes:
mechDesign.setValveSizingStandard("Sachdeva");
mechDesign.setChokeDiameter(0.5, "in");
```

### Multiphase Choke Sizing

For production chokes handling two-phase (gas-liquid) flow, use the Sachdeva or Gilbert-type models:

```java
ThrottlingValve choke = new ThrottlingValve("Production Choke", wellStream);
choke.setOutletPressure(30.0, "bara");

ValveMechanicalDesign design = choke.getMechanicalDesign();
design.setValveSizingStandard("Sachdeva");  // Mechanistic model
design.setChokeDiameter(32, "64ths");        // 32/64" = 0.5"
design.setChokeDischargeCoefficient(0.84);

// Enable flow calculation in transient mode
choke.setCalculateSteadyState(false);
choke.runTransient(0.1);

double calculatedFlow = choke.getOutletStream().getFlowRate("kg/hr");
```

**See:** [Multiphase Choke Flow Models](../MultiphaseChokeFlow.md) for detailed documentation.

### IEC 60534 Gas Sizing

For compressible fluids (gas/vapor):

$$K_v = \frac{Q}{N_9 \cdot P_1 \cdot Y} \sqrt{\frac{M \cdot T \cdot Z}{x}}$$

Where:
- $Q$ = actual volumetric flow at inlet (m³/h)
- $N_9$ = 24.6 (SI constant)
- $P_1$ = inlet pressure (kPa abs)
- $Y$ = expansion factor
- $M$ = molecular weight (g/mol)
- $T$ = temperature (K)
- $Z$ = compressibility factor
- $x$ = $\Delta P / P_1$

### Choked Flow Detection

Flow becomes choked when:
$$x \geq F_\gamma \cdot x_T$$

Where:
- $F_\gamma = \gamma / 1.40$ (specific heat ratio factor)
- $x_T$ = pressure drop ratio at choking (valve-specific, typically 0.13-0.80)

---

## Mechanical Design

Complete mechanical design calculations are available for valve body sizing, weight estimation, and actuator requirements.

### Accessing Mechanical Design

```java
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.calcDesign();
```

### Design Results

| Property | Method | Unit |
|----------|--------|------|
| ANSI Pressure Class | `getAnsiPressureClass()` | - |
| Nominal Size | `getNominalSizeInches()` | inches |
| Face-to-Face | `getFaceToFace()` | mm |
| Body Wall Thickness | `getBodyWallThickness()` | mm |
| Design Pressure | `getDesignPressure()` | bara |
| Design Temperature | `getDesignTemperature()` | °C |
| Actuator Thrust | `getRequiredActuatorThrust()` | N |
| Actuator Weight | `getActuatorWeight()` | kg |
| Total Weight | `getWeightTotal()` | kg |

### Example: Full Mechanical Design

```java
// Create and run valve
ThrottlingValve valve = new ThrottlingValve("PCV-101", gasStream);
valve.setOutletPressure(60.0, "bara");
valve.run();

// Calculate mechanical design
ValveMechanicalDesign mechDesign = (ValveMechanicalDesign) valve.getMechanicalDesign();
mechDesign.calcDesign();

// Print results
System.out.println("=== VALVE MECHANICAL DESIGN ===");
System.out.println("Cv: " + valve.getCv());
System.out.println("ANSI Class: " + mechDesign.getAnsiPressureClass());
System.out.println("Size: " + mechDesign.getNominalSizeInches() + " inches");
System.out.println("Face-to-Face: " + mechDesign.getFaceToFace() + " mm");
System.out.println("Wall Thickness: " + mechDesign.getBodyWallThickness() + " mm");
System.out.println("Total Weight: " + mechDesign.getWeightTotal() + " kg");
System.out.println("Actuator Thrust: " + mechDesign.getRequiredActuatorThrust() + " N");
```

### Detailed Documentation

See [Valve Mechanical Design](../ValveMechanicalDesign.md) for complete documentation including:
- ANSI pressure class selection criteria
- Wall thickness calculations per ASME B16.34
- Actuator sizing methodology
- Weight estimation correlations

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

- [Valve Mechanical Design](../ValveMechanicalDesign.md) - Complete mechanical design calculations
- [Compressor Mechanical Design](../CompressorMechanicalDesign.md) - Similar approach for compressors
- [Process Package](../README.md) - Package overview
- [Separators](separators.md) - Separation equipment
- [Safety Systems](../safety/) - Safety valve sizing
