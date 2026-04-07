---
title: Ejector Equipment
description: "Documentation for ejector equipment in NeqSim process simulation, including Transvac-style vendor parameters (entrainment ratio, compression ratio, critical back pressure, area ratio, Mach numbers)."
---

# Ejector Equipment

Documentation for ejector equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Ejector Class](#ejector-class)
- [Operating Principles](#operating-principles)
- [Design Parameters](#design-parameters)
- [Transvac-Style Vendor Parameters](#transvac-style-vendor-parameters)
- [Performance Curves](#performance-curves)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.ejector`

**Classes:**
| Class | Description |
|-------|-------------|
| `Ejector` | Steam/gas ejector for compression |
| `EjectorDesignResult` | Design calculation results (immutable) |

Ejectors use the kinetic energy of a high-pressure motive stream to entrain and compress a low-pressure suction stream. Common applications include:
- Vapor recovery systems
- Vacuum generation
- Flare gas recovery (Transvac, Croll Reynolds)
- LP gas compression without rotating equipment

The NeqSim ejector model supports both constant-pressure mixing (CPM) and constant-area
mixing (CAM) approaches, and calculates Transvac-style vendor parameters including
entrainment ratio, compression ratio, critical back pressure, area ratio, and Mach numbers.

---

## Ejector Class

### Basic Usage

```java
import neqsim.process.equipment.ejector.Ejector;

// Create ejector with motive and suction streams
Ejector ejector = new Ejector("Ejector-100", motiveStream, suctionStream);
ejector.setDischargePressure(5.0);  // bara
ejector.setEfficiencyIsentropic(0.75);  // Nozzle efficiency
ejector.setDiffuserEfficiency(0.80);    // Diffuser efficiency
ejector.run();

// Get mixed stream
StreamInterface mixedStream = ejector.getMixedStream();
double dischargeT = mixedStream.getTemperature("C");
double dischargeP = mixedStream.getPressure("bara");
```

### Stream Setup

```java
// High-pressure motive stream (e.g., HP steam or gas)
SystemInterface motiveFluid = new SystemSrkEos(250.0, 10.0);
motiveFluid.addComponent("water", 1.0);
motiveFluid.setMixingRule("classic");

Stream motiveStream = new Stream("Motive Steam", motiveFluid);
motiveStream.setFlowRate(1000.0, "kg/hr");
motiveStream.run();

// Low-pressure suction stream
SystemInterface suctionFluid = new SystemSrkEos(300.0, 1.5);
suctionFluid.addComponent("methane", 0.95);
suctionFluid.addComponent("ethane", 0.05);
suctionFluid.setMixingRule("classic");

Stream suctionStream = new Stream("Suction Gas", suctionFluid);
suctionStream.setFlowRate(500.0, "kg/hr");
suctionStream.run();
```

---

## Operating Principles

### Ejector Sections

An ejector consists of four main sections:

1. **Nozzle**: Converts motive stream pressure to velocity
2. **Suction Chamber**: Entrains low-pressure gas
3. **Mixing Chamber**: Momentum exchange between streams
4. **Diffuser**: Converts velocity back to pressure

### Energy Balance

The ejector performs isentropic expansion and compression:

$$\eta_{nozzle} = \frac{h_1 - h_2}{h_1 - h_{2s}}$$

$$\eta_{diffuser} = \frac{h_{4s} - h_3}{h_4 - h_3}$$

Where:
- $h_1$ = motive inlet enthalpy
- $h_2$ = nozzle outlet enthalpy (actual)
- $h_{2s}$ = nozzle outlet enthalpy (isentropic)
- $h_3$ = mixing section outlet enthalpy
- $h_4$ = diffuser outlet enthalpy (actual)
- $h_{4s}$ = diffuser outlet enthalpy (isentropic)

### Entrainment Ratio

The entrainment ratio (ER) is defined as:

$$ER = \frac{\dot{m}_{suction}}{\dot{m}_{motive}}$$

---

## Design Parameters

### Efficiency Settings

```java
// Nozzle isentropic efficiency (typically 0.7-0.9)
ejector.setEfficiencyIsentropic(0.75);

// Diffuser efficiency (typically 0.7-0.85)
ejector.setDiffuserEfficiency(0.80);
```

### Discharge Pressure

```java
// Set target discharge pressure
ejector.setDischargePressure(5.0);  // bara
```

### Design Velocities

```java
// Optional: Override default suction and diffuser velocities
ejector.setDesignSuctionVelocity(30.0);  // m/s
ejector.setDesignDiffuserOutletVelocity(20.0);  // m/s
```

### Connection Lengths

```java
// Optional: Set connection pipe lengths for pressure drop
ejector.setSuctionConnectionLength(2.0);  // m
ejector.setDischargeConnectionLength(3.0);  // m
```

---

## Mechanical Design

```java
// Get mechanical design parameters
EjectorMechanicalDesign mechDesign = ejector.getMechanicalDesign();

// Calculate sizing
mechDesign.calcDesign();

// Get geometry
double throatDiameter = mechDesign.getThroatDiameter();  // m
double nozzleLength = mechDesign.getNozzleLength();  // m
double mixingLength = mechDesign.getMixingLength();  // m
double diffuserLength = mechDesign.getDiffuserLength();  // m
```

---

## Usage Examples

### Flare Gas Recovery

```java
ProcessSystem process = new ProcessSystem();

// High-pressure motive gas from compressor discharge
Stream motiveGas = new Stream("HP Gas", hpGasFluid);
motiveGas.setFlowRate(2000.0, "kg/hr");
motiveGas.setTemperature(60.0, "C");
motiveGas.setPressure(40.0, "bara");
process.add(motiveGas);

// Low-pressure flare header gas
Stream flareGas = new Stream("Flare Gas", flareGasFluid);
flareGas.setFlowRate(500.0, "kg/hr");
flareGas.setTemperature(40.0, "C");
flareGas.setPressure(1.2, "bara");
process.add(flareGas);

// Ejector to recover flare gas
Ejector fgr = new Ejector("FGR Ejector", motiveGas, flareGas);
fgr.setDischargePressure(8.0);  // bara
fgr.setEfficiencyIsentropic(0.75);
fgr.setDiffuserEfficiency(0.80);
process.add(fgr);

// Run process
process.run();

// Results
double entrainmentRatio = flareGas.getFlowRate("kg/hr") / motiveGas.getFlowRate("kg/hr");
System.out.println("Entrainment ratio: " + entrainmentRatio);
System.out.println("Discharge pressure: " + fgr.getMixedStream().getPressure("bara") + " bara");
```

### Steam Ejector Vacuum System

```java
// HP steam as motive fluid
Stream hpSteam = new Stream("HP Steam", steamFluid);
hpSteam.setFlowRate(1500.0, "kg/hr");
hpSteam.setTemperature(200.0, "C");
hpSteam.setPressure(10.0, "bara");

// Vacuum overhead vapor
Stream vacuumVapor = new Stream("Vacuum Vapor", vaporFluid);
vacuumVapor.setFlowRate(300.0, "kg/hr");
vacuumVapor.setTemperature(50.0, "C");
vacuumVapor.setPressure(0.1, "bara");

// First stage ejector
Ejector ejector1 = new Ejector("1st Stage", hpSteam, vacuumVapor);
ejector1.setDischargePressure(0.5);  // bara
ejector1.run();

// Intercondenser
Cooler intercondenser = new Cooler("Intercondenser", ejector1.getMixedStream());
intercondenser.setOutTemperature(40.0, "C");

// Second stage ejector
Ejector ejector2 = new Ejector("2nd Stage", hpSteam2, intercondenser.getOutletStream());
ejector2.setDischargePressure(1.1);  // bara
ejector2.run();
```

---

## Transvac-Style Vendor Parameters

After calling `ejector.run()`, the following vendor-style parameters are available.
These correspond directly to the parameters used in Transvac, Croll Reynolds, and
Schutte & Koerting ejector datasheets and performance curves.

### Key Parameters

| Parameter | Method | Description |
|-----------|--------|-------------|
| Entrainment Ratio (ER) | `getEntrainmentRatio()` | Suction mass flow / motive mass flow |
| Compression Ratio (CR) | `getCompressionRatio()` | Discharge pressure / suction pressure |
| Expansion Ratio | `getExpansionRatio()` | Motive pressure / discharge pressure |
| Critical Back Pressure | `getCriticalBackPressure()` | Max discharge pressure before breakdown (bara) |
| Area Ratio | `getAreaRatio()` | Mixing area / motive nozzle throat area |
| Motive Nozzle Mach | `getMotiveNozzleMach()` | Mach number at motive nozzle exit |
| Suction Mach | `getSuctionMach()` | Mach number of suction flow at mixing entrance |
| Mixing Mach | `getMixingMach()` | Mach number of mixed flow in mixing section |
| Motive Choked | `isMotiveChoked()` | Whether motive nozzle flow is sonic |
| Suction Choked | `isSuctionChoked()` | Whether suction flow is sonic |
| In Breakdown | `isInBreakdown()` | Whether discharge exceeds critical back pressure |

### Efficiency Settings

Three independent efficiencies control the ejector model:

```java
ejector.setEfficiencyIsentropic(0.75);     // Motive nozzle isentropic efficiency (0.7-0.9)
ejector.setSuctionNozzleEfficiency(0.90);  // Suction nozzle efficiency (0.85-0.95)
ejector.setMixingEfficiency(0.85);         // Mixing section momentum transfer (0.80-0.95)
ejector.setDiffuserEfficiency(0.80);       // Diffuser pressure recovery (0.7-0.85)
```

### Critical Back Pressure

The critical back pressure (CBP) is the most important parameter in ejector specification.
It represents the maximum discharge pressure at which the ejector maintains stable
entrainment. The CBP is calculated from the stagnation (total) enthalpy of the mixed
flow and the diffuser efficiency using a rigorous thermodynamic flash.

```java
ejector.run();
double cbp = ejector.getCriticalBackPressure();  // bara
boolean stable = !ejector.isInBreakdown();

if (ejector.isInBreakdown()) {
    System.out.println("WARNING: Operating beyond critical back pressure!");
    System.out.println("Reduce discharge pressure below " + cbp + " bara");
}
```

### Example: Reading All Vendor Parameters

```java
ejector.run();

System.out.println("Entrainment Ratio: " + ejector.getEntrainmentRatio());
System.out.println("Compression Ratio: " + ejector.getCompressionRatio());
System.out.println("Expansion Ratio:   " + ejector.getExpansionRatio());
System.out.println("Critical Back P:   " + ejector.getCriticalBackPressure() + " bara");
System.out.println("Area Ratio:        " + ejector.getAreaRatio());
System.out.println("Motive Mach:       " + ejector.getMotiveNozzleMach());
System.out.println("Suction Mach:      " + ejector.getSuctionMach());
System.out.println("Mixing Mach:       " + ejector.getMixingMach());
System.out.println("Motive Choked:     " + ejector.isMotiveChoked());
System.out.println("Suction Choked:    " + ejector.isSuctionChoked());
System.out.println("In Breakdown:      " + ejector.isInBreakdown());
```

---

## Performance Curves

### Generate Performance Curve

The `generatePerformanceCurve()` method produces Transvac-style performance data showing
how entrainment ratio varies with discharge pressure at constant motive and suction conditions:

```java
// Generate a performance curve with 10 points from 1.5 to 5.0 bara discharge
List<double[]> curve = ejector.generatePerformanceCurve(1.5, 5.0, 10);

for (double[] point : curve) {
    double dischargePressure = point[0];
    double entrainmentRatio = point[1];
    double compressionRatio = point[2];
    System.out.printf("Pd=%.2f bara, ER=%.3f, CR=%.2f%n",
        dischargePressure, entrainmentRatio, compressionRatio);
}
```

### Entrainment vs Compression Ratio

For a given motive pressure and geometry, ejector performance follows characteristic curves:

```java
// Calculate performance at different suction pressures
double[] suctionPressures = {0.5, 1.0, 1.5, 2.0};  // bara
for (double Ps : suctionPressures) {
    suctionStream.setPressure(Ps, "bara");
    suctionStream.run();
    ejector.run();

    double compressionRatio = ejector.getMixedStream().getPressure("bara") / Ps;
    System.out.println("Suction P: " + Ps + " bara, CR: " + compressionRatio);
}
```

---

## Related Documentation

- [Compressors](compressors) - Gas compression alternatives
- [Streams](streams) - Stream handling
- [Process Package](../) - Package overview
