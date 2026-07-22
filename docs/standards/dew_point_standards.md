---
title: "Dew Point Standards"
description: "Standards for calculating water and hydrocarbon dew points of natural gas."
---

# Dew Point Standards

Standards for calculating water and hydrocarbon dew points of natural gas.

## Table of Contents
- [Overview](#overview)
- [Water Dew Point (ISO 18453)](#water-dew-point-iso-18453)
- [Hydrocarbon Dew Point](#hydrocarbon-dew-point)
- [Usage Examples](#usage-examples)
- [Comparison of Methods](#comparison-of-methods)

---

## Overview

Dew point specifications are critical for:
- Pipeline transport (preventing liquid dropout)
- Custody transfer specifications
- Processing plant design
- Sales contract compliance

**Available Implementations:**
- `Draft_ISO18453` - Water dew point using GERG-water equation
- `BestPracticeHydrocarbonDewPoint` - Hydrocarbon dew point using SRK-EoS

---

## Water Dew Point (ISO 18453)

### Standard

**ISO 18453:2004** - Natural gas — Correlation between water content and water dew point

### Purpose

Calculate the temperature at which water vapor in natural gas begins to condense at a given pressure.

### Implementation

**Class:** `Draft_ISO18453`

Uses the GERG-water equation of state which is specifically designed for water in natural gas systems.

### Constructor

```java
import neqsim.standards.gasquality.Draft_ISO18453;

// Create standard from any fluid
Draft_ISO18453 waterDewPoint = new Draft_ISO18453(thermoSystem);
```

### How It Works

1. Converts input fluid to GERG-water EoS if not already
2. Sets pressure to reference pressure (default 70 bar)
3. Performs water dew point temperature flash
4. Returns temperature where water just begins to condense

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform dew point calculation |
| `getValue("dewPointTemperature")` | Get dew point in °C |
| `getValue("pressure")` | Get reference pressure in bar |
| `setReferencePressure(P)` | Set reference pressure |
| `isOnSpec()` | Check against sales contract specification |

### Example

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.standards.gasquality.Draft_ISO18453;

// Natural gas with water
SystemInterface wetGas = new SystemSrkCPAstatoil(273.15 + 20, 50.0);
wetGas.addComponent("methane", 0.90);
wetGas.addComponent("ethane", 0.05);
wetGas.addComponent("propane", 0.02);
wetGas.addComponent("CO2", 0.02);
wetGas.addComponent("water", 100e-6);  // 100 ppm water
wetGas.setMixingRule("CPA_Statoil");

// Calculate water dew point
Draft_ISO18453 iso18453 = new Draft_ISO18453(wetGas);
iso18453.setReferencePressure(70.0);  // 70 bar reference
iso18453.calculate();

double wdp = iso18453.getValue("dewPointTemperature");
System.out.printf("Water Dew Point at 70 bar = %.1f °C%n", wdp);
```

### Specification Checking

```java
// Check against contract specification
iso18453.getSalesContract().setWaterDewPointTemperature(-8.0);  // Max -8°C

if (iso18453.isOnSpec()) {
    System.out.println("Gas meets water dew point specification");
} else {
    System.out.println("Gas FAILS water dew point specification");
}
```

---

## Hydrocarbon Dew Point

### Purpose

Calculate the temperature at which hydrocarbon liquids begin to condense from natural gas (cricondentherm).

### Implementation

**Class:** `BestPracticeHydrocarbonDewPoint`

Uses SRK equation of state with Peneloux volume correction (mixing rule 2) for hydrocarbon phase behavior.

### Constructor

```java
import neqsim.standards.gasquality.BestPracticeHydrocarbonDewPoint;

// Create from any fluid (water is automatically removed)
BestPracticeHydrocarbonDewPoint hcDewPoint = 
    new BestPracticeHydrocarbonDewPoint(thermoSystem);
```

### How It Works

1. Creates new SRK-EoS system from input (excludes water)
2. Sets reference pressure (default 50 bar)
3. Performs dew point temperature flash
4. Returns hydrocarbon dew point temperature

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform dew point calculation |
| `getValue("hydrocarbondewpointTemperature")` | Get dew point in °C |
| `getValue("pressure")` | Get reference pressure in bar |
| `isOnSpec()` | Check against specification |

### Example

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.gasquality.BestPracticeHydrocarbonDewPoint;

// Rich natural gas
SystemInterface richGas = new SystemSrkEos(273.15 + 20, 50.0);
richGas.addComponent("methane", 0.85);
richGas.addComponent("ethane", 0.06);
richGas.addComponent("propane", 0.03);
richGas.addComponent("n-butane", 0.02);
richGas.addComponent("n-pentane", 0.01);
richGas.addComponent("n-hexane", 0.005);
richGas.addComponent("n-heptane", 0.003);
richGas.addComponent("n-octane", 0.002);
richGas.addComponent("nitrogen", 0.02);
richGas.setMixingRule("classic");

// Calculate hydrocarbon dew point
BestPracticeHydrocarbonDewPoint hcDP = new BestPracticeHydrocarbonDewPoint(richGas);
hcDP.calculate();

double hcdp = hcDP.getValue("hydrocarbondewpointTemperature");
System.out.printf("Hydrocarbon Dew Point at 50 bar = %.1f °C%n", hcdp);
```

### Multiple Pressures

```java
// Calculate HCDP curve at multiple pressures
double[] pressures = {20, 30, 40, 50, 60, 70, 80};

System.out.println("Pressure (bar) | HC Dew Point (°C)");
System.out.println("---------------|-----------------");

for (double P : pressures) {
    richGas.setPressure(P);
    BestPracticeHydrocarbonDewPoint hcDP = new BestPracticeHydrocarbonDewPoint(richGas);
    hcDP.calculate();
    double hcdp = hcDP.getValue("hydrocarbondewpointTemperature");
    System.out.printf("%14.0f | %16.1f%n", P, hcdp);
}
```

---

## Usage Examples

### Combined Water and HC Dew Points

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.standards.gasquality.Draft_ISO18453;
import neqsim.standards.gasquality.BestPracticeHydrocarbonDewPoint;

// Create wet gas with heavy ends
SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 20, 70.0);
gas.addComponent("methane", 0.88);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("n-butane", 0.01);
gas.addComponent("n-pentane", 0.005);
gas.addComponent("n-hexane", 0.002);
gas.addComponent("CO2", 0.02);
gas.addComponent("water", 50e-6);
gas.setMixingRule("CPA_Statoil");

// Water dew point
Draft_ISO18453 waterDP = new Draft_ISO18453(gas);
waterDP.setReferencePressure(70.0);
waterDP.calculate();
double wdp = waterDP.getValue("dewPointTemperature");

// Hydrocarbon dew point
BestPracticeHydrocarbonDewPoint hcDP = new BestPracticeHydrocarbonDewPoint(gas);
hcDP.calculate();
double hcdp = hcDP.getValue("hydrocarbondewpointTemperature");

System.out.println("=== Dew Point Analysis ===");
System.out.printf("Water Dew Point (at 70 bar): %.1f °C%n", wdp);
System.out.printf("Hydrocarbon Dew Point (at 50 bar): %.1f °C%n", hcdp);
```

### Effect of Water Content on WDP

```java
// Analyze water dew point vs water content
double[] waterContents = {10, 20, 50, 100, 200, 500};  // ppm

System.out.println("Water Content (ppm) | Water Dew Point (°C)");
System.out.println("--------------------|--------------------");

for (double ppm : waterContents) {
    SystemInterface gas = new SystemSrkCPAstatoil(273.15 + 20, 70.0);
    gas.addComponent("methane", 0.95);
    gas.addComponent("ethane", 0.03);
    gas.addComponent("CO2", 0.01);
    gas.addComponent("water", ppm * 1e-6);
    gas.setMixingRule("CPA_Statoil");
    
    Draft_ISO18453 waterDP = new Draft_ISO18453(gas);
    waterDP.setReferencePressure(70.0);
    waterDP.calculate();
    double wdp = waterDP.getValue("dewPointTemperature");
    
    System.out.printf("%19.0f | %19.1f%n", ppm, wdp);
}
```

---

## Comparison of Methods

### Water Dew Point Approaches

| Aspect | ISO 18453 (GERG-water) | CPA-EoS |
|--------|------------------------|---------|
| Model | GERG-water specific | General CPA |
| Accuracy | Optimized for NG | Good general accuracy |
| Speed | Fast | Moderate |
| Water association | Empirical | Explicit |

### Hydrocarbon Dew Point Approaches

| Aspect | Best Practice (SRK) | PR-EoS | GERG-2004 |
|--------|---------------------|--------|-----------|
| Heavy ends | Good | Good | Limited |
| Accuracy | Typical ±2-3°C | Typical ±2-3°C | Best for lean gas |
| Speed | Fast | Fast | Moderate |

---

## Typical Specifications

### European Gas Specifications

| Parameter | Typical Limit |
|-----------|---------------|
| Water dew point | < -8°C at 70 bar |
| HC dew point | < -2°C at 1-70 bar |

### US Pipeline Specifications

| Parameter | Typical Limit |
|-----------|---------------|
| Water dew point | < -7°C (20°F) at max operating P |
| HC dew point | < -4°C (25°F) at cricondenbar |

---

## Accuracy Considerations

### Factors Affecting Water Dew Point

- Water content measurement uncertainty
- Pressure accuracy
- EoS model selection
- Presence of glycols or methanol

### Factors Affecting HC Dew Point

- Heavy end characterization (C6+ components)
- Retrograde behavior near cricondentherm
- EoS binary interaction parameters
- Pressure at measurement

### Recommendations

1. Use CPA or GERG-water for water dew point
2. Characterize C6+ fraction carefully for HC dew point
3. Report dew point with reference pressure
4. Consider measurement at multiple pressures for HC dew point curve

---

## References

1. ISO 18453:2004 - Natural gas — Correlation between water content and water dew point
2. Folas, G.K., et al. (2007). High-pressure vapor-liquid equilibria of systems containing ethylene glycol, water and methane. Fluid Phase Equilibria.
3. ISO 23874:2006 - Natural gas — Gas chromatographic requirements for hydrocarbon dewpoint calculation
4. GERG Technical Monograph TM14 (2007) - The GERG-2004 Wide-Range Equation of State for Natural Gases
