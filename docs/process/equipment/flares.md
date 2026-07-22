---
title: Flare Systems
description: Documentation for flare equipment in NeqSim process simulation.
---

# Flare Systems

Documentation for flare equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Flare Class](#flare-class)
- [Combustion Calculations](#combustion-calculations)
- [Capacity Monitoring](#capacity-monitoring)
- [Dynamic Operation](#dynamic-operation)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.flare`

**Classes:**
| Class | Description |
|-------|-------------|
| `Flare` | Main flare combustion unit |
| `FlareStack` | Flare stack with dispersion |
| `FlareCapacityDTO` | Capacity check results |
| `FlarePerformanceDTO` | Performance metrics |

Flares are safety devices that combust hydrocarbon gases that cannot be recovered. They are used for:
- Emergency depressuring
- Process upsets
- Maintenance activities
- Start-up/shutdown operations

---

## Flare Class

### Basic Usage

```java
import neqsim.process.equipment.flare.Flare;

// Create flare with inlet stream
Flare flare = new Flare("HP Flare", inletStream);
flare.run();

// Get results
double heatRelease = flare.getHeatDuty();  // W
double co2Emission = flare.getCO2Emission();  // kg/s

System.out.println("Heat release: " + heatRelease/1e6 + " MW");
System.out.println("CO2 emission: " + co2Emission * 3600 + " kg/hr");
```

### Constructor Options

```java
// With name only
Flare flare = new Flare("Flare");
flare.setInletStream(flareGas);

// With name and inlet stream
Flare flare = new Flare("Flare", flareGas);
```

---

## Combustion Calculations

### Heat Release

The flare calculates heat release based on the Lower Calorific Value (LCV):

$$Q = LCV \times \dot{V}_{Sm3}$$

```java
// Get heat release rate
double heatDuty = flare.getHeatDuty();  // W

// Convert to MW
double heatMW = heatDuty / 1e6;
System.out.println("Heat release: " + heatMW + " MW");
```

### CO₂ Emissions

CO₂ emissions are calculated from the carbon content of the flared gas:

$$\dot{m}_{CO_2} = \sum_i (x_i \times \dot{n}_{total} \times n_{C,i} \times M_{CO_2})$$

Where:
- $x_i$ = mole fraction of component i
- $\dot{n}_{total}$ = total molar flow
- $n_{C,i}$ = number of carbon atoms in component i
- $M_{CO_2}$ = molecular weight of CO₂ (44.01 g/mol)

```java
// Get CO2 emission rate
double co2Rate = flare.getCO2Emission();  // kg/s

// Convert to tonnes per hour
double co2TonHr = co2Rate * 3.6;
System.out.println("CO2 emissions: " + co2TonHr + " t/hr");
```

---

## Flare Design Parameters

### Radiation

```java
// Set radiant fraction (fraction of heat released as radiation)
flare.setRadiantFraction(0.20);  // 20%

// Set flame height for radiation calculations
flare.setFlameHeight(40.0);  // m

// Get radiation at ground level
double radiation = flare.getRadiationAtDistance(100.0);  // W/m² at 100m
```

### Tip Diameter

```java
// Set tip diameter for velocity calculations
flare.setTipDiameter(0.5);  // m

// Get tip velocity
double tipVelocity = flare.getTipVelocity();  // m/s
```

---

## Capacity Monitoring

### Design Capacity

```java
// Set design capacity limits
flare.setDesignHeatDutyCapacity(100e6);  // 100 MW
flare.setDesignMassFlowCapacity(50.0);   // 50 kg/s
flare.setDesignMolarFlowCapacity(2000.0); // 2000 mol/s
```

### Capacity Check

```java
// Check if within capacity
flare.run();
CapacityCheckResult result = flare.getCapacityCheckResult();

if (result.isOverCapacity()) {
    System.out.println("WARNING: Flare over capacity!");
    System.out.println("Heat duty: " + result.getHeatDutyPercent() + "% of design");
    System.out.println("Mass flow: " + result.getMassFlowPercent() + "% of design");
}
```

---

## Dynamic Operation

### Transient Simulation

```java
// Track cumulative values during transient
double timeStep = 1.0;  // seconds
double totalTime = 900.0;  // 15 minutes

for (double t = 0; t < totalTime; t += timeStep) {
    // Update inlet conditions (e.g., from blowdown)
    updateFlareInlet(t);
    
    flare.run();
    flare.updateCumulative(timeStep);
}

// Get cumulative values
double totalHeatGJ = flare.getCumulativeHeatReleased();  // GJ
double totalGasBurned = flare.getCumulativeGasBurned();  // kg
double totalCO2 = flare.getCumulativeCO2Emission();  // kg

System.out.println("Total heat released: " + totalHeatGJ + " GJ");
System.out.println("Total gas burned: " + totalGasBurned + " kg");
System.out.println("Total CO2 emitted: " + totalCO2 + " kg");
```

### Reset Cumulative Counters

```java
// Reset for new transient event
flare.resetCumulative();
```

---

## FlareStack Class

The FlareStack class includes additional features for dispersion modeling:

```java
import neqsim.process.equipment.flare.FlareStack;

FlareStack flareStack = new FlareStack("Main Flare Stack", inletStream);
flareStack.setStackHeight(100.0);  // m
flareStack.setTipDiameter(0.6);    // m
flareStack.run();

// Get dispersion parameters
FlareDispersionSurrogateDTO dispersion = flareStack.getDispersionSurrogate();
double effectiveHeight = dispersion.getEffectiveStackHeight();
double plumeMomentum = dispersion.getPlumeMomentum();
```

---

## Usage Examples

### Emergency Blowdown Flare Load

```java
ProcessSystem process = new ProcessSystem();

// Vessel being depressured
Tank vessel = new Tank("HP Vessel", vesselFluid);
vessel.setVolume(100.0, "m3");
vessel.setPressure(100.0, "bara");
process.add(vessel);

// Blowdown valve
BlowdownValve bdv = new BlowdownValve("BDV-100", vessel);
bdv.setOrificeSize(100.0, "mm");
bdv.setDownstreamPressure(1.5, "barg");
process.add(bdv);

// Flare receiving blowdown
Flare flare = new Flare("HP Flare", bdv.getOutletStream());
flare.setDesignHeatDutyCapacity(200e6);  // 200 MW design
process.add(flare);

// Run transient blowdown
double dt = 1.0;
for (double t = 0; t < 900; t += dt) {
    vessel.runTransient(dt);
    bdv.run();
    flare.run();
    flare.updateCumulative(dt);
    
    System.out.println("t=" + t + "s, P=" + vessel.getPressure("barg") + 
        " barg, Q=" + flare.getHeatDuty()/1e6 + " MW");
}
```

### Flare Header System

```java
// Multiple sources to common flare header
Mixer flareHeader = new Mixer("Flare Header");
flareHeader.addStream(lpFlareSource1);
flareHeader.addStream(lpFlareSource2);
flareHeader.addStream(lpFlareSource3);
process.add(flareHeader);

// Flare KO drum
Separator flareKODrum = new Separator("Flare KO Drum", flareHeader.getOutletStream());
process.add(flareKODrum);

// Flare tip
Flare flareTip = new Flare("LP Flare", flareKODrum.getGasOutStream());
flareTip.setDesignHeatDutyCapacity(50e6);
process.add(flareTip);

process.run();

// Check overall flare load
double totalLoad = flareTip.getHeatDuty();
double percentCapacity = totalLoad / flareTip.getDesignHeatDutyCapacity() * 100;
System.out.println("Flare load: " + percentCapacity + "% of design");
```

### Relief Scenario Calculation

```java
// Calculate flare load for blocked outlet scenario
double reliefRate = calculateBlockedOutletRelief();  // kg/hr

// Create relief stream
SystemInterface reliefFluid = vesselFluid.clone();
Stream reliefStream = new Stream("Relief Flow", reliefFluid);
reliefStream.setFlowRate(reliefRate, "kg/hr");
reliefStream.run();

// Calculate flare load
Flare flare = new Flare("Scenario Flare", reliefStream);
flare.run();

System.out.println("Relief scenario:");
System.out.println("  Flow rate: " + reliefRate + " kg/hr");
System.out.println("  Heat release: " + flare.getHeatDuty()/1e6 + " MW");
System.out.println("  CO2 emission: " + flare.getCO2Emission()*3600 + " kg/hr");
```

---

## Performance Reporting

### Get Performance Summary

```java
FlarePerformanceDTO performance = flare.getPerformance();

System.out.println("=== Flare Performance ===");
System.out.println("Heat release: " + performance.getHeatDutyMW() + " MW");
System.out.println("Mass flow: " + performance.getMassFlowKgS() + " kg/s");
System.out.println("Tip velocity: " + performance.getTipVelocity() + " m/s");
System.out.println("CO2 emission: " + performance.getCO2EmissionKgS() + " kg/s");
System.out.println("Radiant heat: " + performance.getRadiantHeatMW() + " MW");
```

---

## Environmental Calculations

### Carbon Footprint

```java
// Calculate annual CO2 emissions from continuous flaring
double avgFlowRate = 1000.0;  // Sm3/hr average
double hoursPerYear = 8760.0;

flare.getInletStream().setFlowRate(avgFlowRate, "Sm3/hr");
flare.run();

double annualCO2 = flare.getCO2Emission() * 3600 * hoursPerYear / 1000;  // tonnes/year
System.out.println("Annual CO2 emissions: " + annualCO2 + " tonnes/year");
```

---

## Related Documentation

- [Safety Systems](../safety/) - PSV and blowdown
- [Valves](valves) - Blowdown valves
- [Tanks](tanks) - Vessel depressuring
