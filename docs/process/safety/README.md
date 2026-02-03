---
title: Safety Systems Package
description: Documentation for safety systems modeling in NeqSim.
---

# Safety Systems Package

Documentation for safety systems modeling in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Safety Equipment](#safety-equipment)
- [Emergency Shutdown (ESD)](#emergency-shutdown-esd)
- [Blowdown Systems](#blowdown-systems)
- [Pressure Safety Valves](#pressure-safety-valves)
- [HIPPS](#hipps)

---

## Overview

**Location:** `neqsim.process.equipment.safety`, `neqsim.process.safety`

NeqSim provides equipment and logic for modeling process safety systems:
- Pressure Safety Valves (PSV)
- Relief valves
- Emergency Shutdown (ESD) systems
- Blowdown and depressuring systems
- High Integrity Pressure Protection Systems (HIPPS)

---

## Safety Equipment

### Pressure Safety Valve (PSV)

```java
import neqsim.process.equipment.valve.SafetyValve;

SafetyValve psv = new SafetyValve("PSV-100", vessel);
psv.setOpeningPressure(95.0, "barg");  // Set pressure
psv.setFullOpenPressure(100.0, "barg"); // Overpressure
psv.setBlowdownPressure(85.0, "barg");  // Reseating pressure
```

### Rupture Disk

```java
import neqsim.process.equipment.valve.RuptureDisk;

RuptureDisk disk = new RuptureDisk("RD-100", vessel);
disk.setBurstPressure(110.0, "barg");
disk.setDiameter(150.0, "mm");
```

---

## Emergency Shutdown (ESD)

### ESD Logic

```java
import neqsim.process.safety.ESDController;

ESDController esd = new ESDController("ESD-1");

// Add trip conditions
esd.addHighPressureTrip(separator, 100.0, "barg");
esd.addLowPressureTrip(separator, 5.0, "barg");
esd.addHighLevelTrip(separator, 0.9);
esd.addLowLevelTrip(separator, 0.1);

// Add shutdown actions
esd.addShutdownValve(inletValve);
esd.addShutdownValve(outletValve);
```

### ESD Levels

| Level | Description | Actions |
|-------|-------------|---------|
| ESD-0 | Total shutdown | Full plant shutdown |
| ESD-1 | Process shutdown | Process area isolation |
| ESD-2 | Unit shutdown | Single unit isolation |
| ESD-3 | Equipment shutdown | Single equipment stop |

---

## Blowdown Systems

### Depressuring Calculation

```java
import neqsim.process.equipment.valve.BlowdownValve;

BlowdownValve blowdown = new BlowdownValve("BDV-100", vessel);
blowdown.setDownstreamPressure(1.0, "barg");  // Flare pressure
blowdown.setOrificeSize(100.0, "mm");

// Run depressuring transient
for (double t = 0; t < 900; t += 1.0) {
    blowdown.runTransient();
    
    double P = vessel.getPressure("barg");
    double T = vessel.getTemperature("C");
    
    if (P < 7.0) {  // 15 minute rule target
        System.out.println("Reached target at " + t + " seconds");
        break;
    }
}
```

### Fire Case Calculation

```java
// Calculate heat input from fire
double wettedArea = 50.0;  // m²
double Q = 43200 * Math.pow(wettedArea, 0.82);  // API 521 formula

vessel.setHeatInput(Q, "W");
```

---

## Pressure Safety Valves

### PSV Sizing

```java
// Calculate required relief rate
double reliefRate = psv.getReliefRate("kg/hr");

// API 520 sizing
double area = psv.getRequiredOrificeArea("mm2");
String orifice = psv.getAPIOrificeLetter();

System.out.println("Required area: " + area + " mm²");
System.out.println("API orifice: " + orifice);
```

### Multiple Relief Scenarios

| Scenario | Description |
|----------|-------------|
| Blocked outlet | Outlet valve closed |
| Fire case | External fire exposure |
| Tube rupture | Heat exchanger tube failure |
| Power failure | Loss of cooling/control |
| Thermal relief | Liquid expansion |

---

## HIPPS

High Integrity Pressure Protection System.

```java
import neqsim.process.safety.HIPPS;

HIPPS hipps = new HIPPS("HIPPS-1");

// Add sensors (2oo3 voting)
hipps.addPressureSensor(pt1);
hipps.addPressureSensor(pt2);
hipps.addPressureSensor(pt3);

// Set trip point
hipps.setTripPressure(95.0, "barg");

// Add final elements
hipps.addIsolationValve(sdv1);
hipps.addIsolationValve(sdv2);

// Set voting logic
hipps.setVotingLogic("2oo3");  // 2 out of 3
```

---

## Example: Complete Safety System

```java
ProcessSystem process = new ProcessSystem();

// Process equipment
Stream feed = new Stream("Feed", feedFluid);
process.add(feed);

Separator separator = new Separator("V-100", feed);
separator.setVolume(10.0, "m3");
process.add(separator);

// PSV on separator
SafetyValve psv = new SafetyValve("PSV-100", separator);
psv.setOpeningPressure(100.0, "barg");
psv.setDischargeStream(flareStream);
process.add(psv);

// Blowdown valve
BlowdownValve bdv = new BlowdownValve("BDV-100", separator);
bdv.setDownstreamPressure(1.0, "barg");
process.add(bdv);

// ESD controller
ESDController esd = new ESDController("ESD");
esd.addHighPressureTrip(separator, 95.0, "barg");
esd.addShutdownAction(() -> {
    inletValve.close();
    bdv.open();
});
process.add(esd);

// Run simulation
process.run();

// Simulate fire scenario
separator.setHeatInput(500.0, "kW");

for (double t = 0; t < 3600; t += 1.0) {
    process.runTransient();
    
    // Check ESD status
    if (esd.isTripped()) {
        System.out.println("ESD activated at " + t + " s");
    }
}
```

---

## Related Documentation

- [Process Package](../) - Process simulation overview
- [ESD Blowdown System](../../safety/ESD_BLOWDOWN_SYSTEM) - Detailed ESD guide
- [HIPPS Summary](../../safety/HIPPS_SUMMARY) - HIPPS overview
- [PSV Dynamic Sizing](../../safety/psv_dynamic_sizing_example) - PSV sizing example
