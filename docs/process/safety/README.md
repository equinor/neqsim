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
- [Release and Gas Dispersion Scenarios](release-dispersion-scenarios.md)
- [CFD Source-Term Handoff](release-dispersion-scenarios.md#cfd-source-term-handoff)
- [Open Drain Review](../../safety/open_drain_review.md)
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
- NORSOK S-001 Clause 9 open-drain review from NeqSim-calculated liquid and hydraulic evidence
- Automatic release source terms and gas dispersion screening from process streams
- Formal CFD source-term JSON handoff cases for OpenFOAM, FLACS, KFX, PHAST, and Safeti workflows

The release, dispersion, and CFD handoff workflow is intended for screening, case generation,
and auditable source-term transfer. Final facility layout, regulatory QRA, and CFD conclusions
still require project-specific validation, site geometry, leak-frequency data, and approved
consequence-analysis methods.

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

### ESD Logic and Dynamic Evidence

```java
ESDValve inletValve = new ESDValve("ESD Inlet Isolation", feed);
inletValve.setStrokeTime(4.0);
inletValve.setCv(500.0);

ESDLogic esdLogic = new ESDLogic("ESD Level 1");
esdLogic.addAction(new TripValveAction(inletValve), 0.0);
```

Use `neqsim.process.safety.esd.EmergencyShutdownTestRunner` when the ESD sequence needs a
structured dynamic evidence report with monitored time series, tagreader comparisons, standards
references, and acceptance criteria.

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

## Example: ESD Dynamic Test Evidence

```java
OperationalTagMap tagMap = new OperationalTagMap()
    .addBinding(OperationalTagBinding.builder("xv_opening")
        .automationAddress("ESD Inlet Isolation.percentValveOpening")
        .unit("%")
        .role(InstrumentTagRole.BENCHMARK)
        .build());

EmergencyShutdownTestPlan plan = EmergencyShutdownTestPlan.builder("ESD1 isolation closure")
    .duration(8.0)
    .timeStep(1.0)
    .tagMap(tagMap)
    .enableLogic("ESD Level 1")
    .triggerLogic("ESD Level 1")
    .criterion(EmergencyShutdownTestCriterion.finalAtMost(
        "ESD-XV-CLOSED", "xv_opening", 5.0, "%"))
    .criterion(EmergencyShutdownTestCriterion.logicCompleted(
        "ESD-LOGIC-COMPLETE", "ESD Level 1"))
    .build();

EmergencyShutdownTestResult report = EmergencyShutdownTestRunner.run(process, plan, esdLogic);
```

---

## Related Documentation

- [Process Package](../) - Process simulation overview
- [Release and Gas Dispersion Scenarios](release-dispersion-scenarios.md) - Automatic source-term and cloud endpoint screening from ProcessSystem streams
- [Open Drain Review](../../safety/open_drain_review.md) - NORSOK S-001 Clause 9 review with NeqSim stream evidence and normalized STID/tagreader inputs
- [ESD Dynamic Testing Workflow](../../safety/esd_testing_workflow.md) - ESD transient testing with process logic, tagreader evidence, and criteria reports
- [ESD Blowdown System](../../safety/ESD_BLOWDOWN_SYSTEM) - Detailed ESD guide
- [HIPPS Summary](../../safety/HIPPS_SUMMARY) - HIPPS overview
- [PSV Dynamic Sizing](../../safety/psv_dynamic_sizing_example) - PSV sizing example
