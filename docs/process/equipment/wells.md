---
title: Well and Reservoir Equipment
description: Documentation for well and reservoir equipment in NeqSim process simulation.
---

# Well and Reservoir Equipment

Documentation for well and reservoir equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Well Types](#well-types)
- [IPR Curves](#ipr-curves)
- [Choke Modeling](#choke-modeling)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.well`

**Classes:**
- `SimpleWell` - Simple well model
- `WellFlow` - Well flow calculations
- `ChokeValve` - Wellhead choke

**Related:** `neqsim.process.equipment.reservoir`

---

## SimpleWell

### Basic Usage

```java
import neqsim.process.equipment.well.SimpleWell;

// Create reservoir fluid
SystemInterface reservoirFluid = new SystemPrEos(373.15, 250.0);
reservoirFluid.addComponent("methane", 0.70);
reservoirFluid.addComponent("ethane", 0.10);
reservoirFluid.addComponent("propane", 0.05);
reservoirFluid.addComponent("n-heptane", 0.10);
reservoirFluid.addComponent("water", 0.05);
reservoirFluid.setMixingRule("classic");

// Create well
SimpleWell well = new SimpleWell("Producer 1", reservoirFluid);
well.setWellheadPressure(50.0, "bara");
well.setFlowRate(10000.0, "Sm3/day");
well.run();

Stream wellheadStream = well.getOutletStream();
```

---

## IPR Curves

### Vogel IPR (Oil Wells)

$$q = q_{max} \left[1 - 0.2\frac{P_{wf}}{P_r} - 0.8\left(\frac{P_{wf}}{P_r}\right)^2\right]$$

```java
well.setIPRModel("vogel");
well.setReservoirPressure(300.0, "bara");
well.setMaxFlowRate(50000.0, "Sm3/day");
well.setWellheadPressure(50.0, "bara");
well.run();

double flowRate = well.getFlowRate("Sm3/day");
```

### Darcy IPR (Gas Wells)

$$q = \frac{k \cdot h \cdot (P_r^2 - P_{wf}^2)}{1422 \cdot T \cdot \mu \cdot Z \cdot \ln(r_e/r_w)}$$

```java
well.setIPRModel("darcy");
well.setPermeability(100.0, "mD");
well.setPayThickness(50.0, "m");
well.setDrainageRadius(500.0, "m");
well.setWellboreRadius(0.1, "m");
```

### Backpressure Equation

$$q = C (P_r^2 - P_{wf}^2)^n$$

```java
well.setIPRModel("backpressure");
well.setBackpressureCoefficient(0.001);
well.setBackpressureExponent(0.85);
```

---

## Wellbore Hydraulics

### Vertical Lift Performance

```java
well.setWellDepth(3000.0, "m");
well.setTubingDiameter(0.1, "m");
well.setWallRoughness(0.00005, "m");

// Correlation selection
well.setPressureDropCorrelation("beggs-brill");
// Options: "beggs-brill", "hagedorn-brown", "duns-ros", "gray"

well.run();

double bottomholePressure = well.getBottomholePressure("bara");
double pressureDrop = well.getTubingPressureDrop("bar");
```

### Temperature Profile

```java
well.setReservoirTemperature(120.0, "C");
well.setSurfaceTemperature(30.0, "C");
well.setGeothermalGradient(0.03, "C/m");

well.run();

double wellheadT = well.getWellheadTemperature("C");
```

---

## Choke Modeling

### Wellhead Choke

```java
import neqsim.process.equipment.valve.ChokeValve;

ChokeValve choke = new ChokeValve("Wellhead Choke", well.getOutletStream());
choke.setOutletPressure(30.0, "bara");
choke.run();

// Or specify bean size
choke.setBeanSize(24, "64ths");  // 24/64" choke
choke.run();

double chokeDP = choke.getPressureDrop("bar");
```

### Critical Flow

```java
boolean isCritical = choke.isCriticalFlow();
double criticalRatio = choke.getCriticalPressureRatio();
```

---

## Nodal Analysis

Find operating point by intersecting IPR and VLP curves.

```java
// IPR curve (reservoir deliverability)
double[] Pwf_ipr = new double[20];
double[] q_ipr = new double[20];

double Pr = 300.0;  // Reservoir pressure, bar
double qmax = 50000.0;  // Max rate, Sm3/day

for (int i = 0; i < 20; i++) {
    Pwf_ipr[i] = Pr * i / 20.0;
    // Vogel equation
    q_ipr[i] = qmax * (1 - 0.2 * (Pwf_ipr[i]/Pr) - 0.8 * Math.pow(Pwf_ipr[i]/Pr, 2));
}

// VLP curve (tubing performance)
double[] Pwf_vlp = new double[20];
double[] q_vlp = new double[20];

for (int i = 0; i < 20; i++) {
    q_vlp[i] = i * 3000.0;  // Flow rate
    well.setFlowRate(q_vlp[i], "Sm3/day");
    well.run();
    Pwf_vlp[i] = well.getBottomholePressure("bara");
}

// Find intersection (operating point)
```

---

## Gas Lift

```java
import neqsim.process.equipment.well.GasLiftWell;

GasLiftWell glWell = new GasLiftWell("GL Producer", reservoirFluid);
glWell.setGasLiftRate(1.0, "MMSm3/day");
glWell.setGasLiftDepth(2500.0, "m");
glWell.run();

double liftedRate = glWell.getOilRate("Sm3/day");
```

---

## ESP (Electrical Submersible Pump)

```java
import neqsim.process.equipment.well.ESPWell;

ESPWell espWell = new ESPWell("ESP Producer", reservoirFluid);
espWell.setPumpDepth(2800.0, "m");
espWell.setPumpDifferentialPressure(100.0, "bar");
espWell.setPumpEfficiency(0.6);
espWell.run();

double pumpPower = espWell.getPumpPower("kW");
double liftedRate = espWell.getOilRate("Sm3/day");
```

---

## Example: Production System

```java
ProcessSystem process = new ProcessSystem();

// Reservoir fluid
SystemInterface oil = new SystemPrEos(380.0, 280.0);
oil.addComponent("methane", 0.40);
oil.addComponent("ethane", 0.08);
oil.addComponent("propane", 0.06);
oil.addComponent("n-butane", 0.04);
oil.addComponent("n-pentane", 0.03);
oil.addComponent("n-heptane", 0.15);
oil.addTBPfraction("C10+", 0.20, 200.0/1000.0, 0.85);
oil.addComponent("water", 0.04);
oil.setMixingRule("classic");

// Well 1
SimpleWell well1 = new SimpleWell("P-1", oil.clone());
well1.setReservoirPressure(280.0, "bara");
well1.setWellheadPressure(50.0, "bara");
well1.setFlowRate(3000.0, "Sm3/day");
process.add(well1);

// Choke 1
ChokeValve choke1 = new ChokeValve("XV-1", well1.getOutletStream());
choke1.setOutletPressure(30.0, "bara");
process.add(choke1);

// Well 2
SimpleWell well2 = new SimpleWell("P-2", oil.clone());
well2.setReservoirPressure(260.0, "bara");
well2.setWellheadPressure(45.0, "bara");
well2.setFlowRate(2500.0, "Sm3/day");
process.add(well2);

// Choke 2
ChokeValve choke2 = new ChokeValve("XV-2", well2.getOutletStream());
choke2.setOutletPressure(30.0, "bara");
process.add(choke2);

// Manifold
Mixer manifold = new Mixer("Production Manifold");
manifold.addStream(choke1.getOutletStream());
manifold.addStream(choke2.getOutletStream());
process.add(manifold);

// First stage separator
ThreePhaseSeparator hpSep = new ThreePhaseSeparator("HP Separator", 
    manifold.getOutletStream());
process.add(hpSep);

process.run();

// Results
System.out.println("Total oil rate: " + hpSep.getOilOutStream().getFlowRate("Sm3/day") + " Sm3/day");
System.out.println("Total gas rate: " + hpSep.getGasOutStream().getFlowRate("MSm3/day") + " MSm3/day");
System.out.println("Total water rate: " + hpSep.getWaterOutStream().getFlowRate("m3/day") + " m3/day");
```

---

## GOR and Water Cut

```java
// Gas-oil ratio
double GOR = hpSep.getGasOutStream().getFlowRate("Sm3/day") / 
    hpSep.getOilOutStream().getFlowRate("Sm3/day");

// Water cut
double waterCut = hpSep.getWaterOutStream().getFlowRate("m3/day") / 
    (hpSep.getOilOutStream().getFlowRate("m3/day") + 
     hpSep.getWaterOutStream().getFlowRate("m3/day"));

System.out.println("GOR: " + GOR + " Sm3/Sm3");
System.out.println("Water cut: " + (waterCut * 100) + " %");
```

---

## Related Documentation

- [Process Package](../) - Package overview
- [Valves](valves) - Choke valve details
- [Separators](separators) - Production separators
- [Well Simulation Guide](../../simulation/well_simulation_guide) - Detailed well modeling
