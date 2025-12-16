# Separator Equipment

Documentation for separator equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Separator Types](#separator-types)
- [Usage Examples](#usage-examples)
- [Design Calculations](#design-calculations)

---

## Overview

**Location:** `neqsim.process.equipment.separator`

**Classes:**
- `Separator` - Two-phase gas-liquid separator
- `ThreePhaseSeparator` - Three-phase gas-oil-water separator
- `GasScrubber` - Gas scrubbing separator
- `GasScrubberSimple` - Simplified gas scrubber

---

## Separator Types

### Two-Phase Separator

```java
import neqsim.process.equipment.separator.Separator;

Separator separator = new Separator("V-100", inletStream);
separator.run();

// Get outlet streams
Stream gasOut = separator.getGasOutStream();
Stream liquidOut = separator.getLiquidOutStream();

// Properties
double gasRate = gasOut.getFlowRate("kg/hr");
double liquidRate = liquidOut.getFlowRate("kg/hr");
double liquidLevel = separator.getLiquidLevel();
```

### Three-Phase Separator

```java
import neqsim.process.equipment.separator.ThreePhaseSeparator;

ThreePhaseSeparator separator = new ThreePhaseSeparator("V-200", inletStream);
separator.run();

// Get outlet streams
Stream gasOut = separator.getGasOutStream();
Stream oilOut = separator.getOilOutStream();
Stream waterOut = separator.getWaterOutStream();

// Water cut
double waterCut = separator.getWaterCut();
```

### Gas Scrubber

```java
import neqsim.process.equipment.separator.GasScrubber;

GasScrubber scrubber = new GasScrubber("Inlet Scrubber", gasStream);
scrubber.run();

// Dry gas output
Stream dryGas = scrubber.getGasOutStream();

// Condensate removal
Stream condensate = scrubber.getLiquidOutStream();
```

---

## Separator Sizing

### Vertical Separator

```java
// Set dimensions
separator.setInternalDiameter(2.0, "m");
separator.setLiquidVolume(10.0, "m3");

// Or specify residence time
separator.setLiquidResidenceTime(120.0, "sec");
```

### Horizontal Separator

```java
separator.setSeparatorType("horizontal");
separator.setLength(10.0, "m");
separator.setInternalDiameter(2.5, "m");
```

---

## Dynamic Simulation

```java
// Enable dynamic mode
separator.setCalculateSteadyState(false);

// Set initial conditions
separator.setLiquidLevel(0.5);  // 50% level

// Run transient
for (int i = 0; i < 100; i++) {
    separator.runTransient();
    
    double level = separator.getLiquidLevel();
    double pressure = separator.getPressure();
}
```

---

## Separation Efficiency

```java
// Set droplet removal efficiency
separator.setGasCarryUnderFraction(0.001);  // 0.1% liquid in gas
separator.setLiquidCarryOverFraction(0.0001); // 0.01% gas in liquid
```

---

## Example: HP/LP Separation Train

```java
// HP Separator at 50 bar
Separator hpSep = new Separator("HP Sep", feedStream);
process.add(hpSep);

// Letdown valve
ThrottlingValve lpValve = new ThrottlingValve("LP Valve", hpSep.getLiquidOutStream());
lpValve.setOutletPressure(5.0, "bara");
process.add(lpValve);

// LP Separator at 5 bar
Separator lpSep = new Separator("LP Sep", lpValve.getOutletStream());
process.add(lpSep);

// Run process
process.run();

// Total gas production
double hpGas = hpSep.getGasOutStream().getFlowRate("MSm3/day");
double lpGas = lpSep.getGasOutStream().getFlowRate("MSm3/day");
double totalGas = hpGas + lpGas;
```

---

## Related Documentation

- [Process Package](../README.md) - Package overview
- [Streams](streams.md) - Stream handling
