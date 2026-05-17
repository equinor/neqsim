---
title: Produced Water Degassing System
description: Documentation for multi-stage produced water degassing with emission calculations per Norwegian regulations.
---

# Produced Water Degassing System

High-level model for produced water degassing with greenhouse gas emission calculations.

## Table of Contents
- [Overview](#overview)
- [Regulatory Context](#regulatory-context)
- [Thermodynamic Model](#thermodynamic-model)
- [Process Description](#process-description)
- [Usage Examples](#usage-examples)
- [Emission Calculations](#emission-calculations)
- [Calibration and Validation](#calibration-and-validation)
- [API Reference](#api-reference)

---

## Overview

**Location:** `neqsim.process.equipment.util.ProducedWaterDegassingSystem`

The `ProducedWaterDegassingSystem` class models multi-stage degassing for produced water treatment, automatically calculating greenhouse gas emissions at each stage. This is based on the methodology from the GFMW 2023 paper "Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator".

### Key Features

- Multi-stage degassing (Degasser → CFU → Caisson)
- Automatic GHG emission calculation at each stage
- Supports CPA equation of state for accurate water-gas equilibria
- Tunable binary interaction parameters from lab calibration
- Comparison with conventional handbook methods
- Norwegian regulatory compliance support

---

## Regulatory Context

This implementation supports compliance with:

| Regulation | Description |
|------------|-------------|
| **Aktivitetsforskriften §70** | Measurement and calculation requirements |
| **Norwegian Offshore Emission Handbook** | Replaces conventional solubility factor method |
| **Norwegian Environment Agency** | Annual emission reporting |

### Comparison: Virtual vs Conventional Methods

| Aspect | Conventional Method | Virtual Measurement |
|--------|---------------------|---------------------|
| Gas solubility | Fixed factors (ppmv/bar) | CPA-EoS calculation |
| Composition | Assumed (often 100% CH₄) | Component-by-component |
| Accuracy | ±30-50% typical | ±3.6% total gas |
| Calibration | None | Lab-tuned kij parameters |

---

## Thermodynamic Model

### CPA Equation of State

Uses SRK-CPA (Cubic Plus Association) with:
- Water self-association
- Water-hydrocarbon solvation
- Tuned binary interaction parameters

### Binary Interaction Parameters (Tuned)

| Pair | kij Base | kij Temp Coeff | Source |
|------|----------|----------------|--------|
| Water-CO₂ | -0.24 | 0.001121 | Kristiansen 2023 |
| Water-CH₄ | -0.72 | 0.002605 | Kristiansen 2023 |
| Water-C₂H₆ | 0.11 | - | Literature |
| Water-C₃H₈ | 0.205 | - | Literature |

### Model Validation

Validated against produced water samples from high-CO₂ reservoirs:
- Total gas: ±3.6% uncertainty
- CO₂/CH₄ composition: ±1% uncertainty

---

## Process Description

### Typical 3-Stage System

```
Water from ────►  Degasser  ────►    CFU    ────►  Caisson  ────► Sea
Separator        (3-5 barg)       (1 barg)        (atm)
                     │                │               │
                     ▼                ▼               ▼
               Gas to Flare    Gas to Vent    Gas to Atmosphere
```

### Stage Details

| Stage | Pressure | Temperature | Emissions Destination |
|-------|----------|-------------|----------------------|
| **Degasser** | 3-5 barg | 60-80°C | Flare/fuel gas |
| **CFU** | 1.0 barg | 40-60°C | Atmospheric vent |
| **Caisson** | 1.01 bara | Ambient | Direct to atmosphere |

---

## Usage Examples

### Java Example - Basic Setup

```java
import neqsim.process.equipment.util.ProducedWaterDegassingSystem;

// Create produced water degassing system
ProducedWaterDegassingSystem system = new ProducedWaterDegassingSystem("Platform PW");

// Set water flow conditions
system.setWaterFlowRate(100.0, "m3/hr");      // Produced water rate
system.setWaterTemperature(80.0, "C");        // From separator
system.setInletPressure(30.0, "bara");        // Upstream pressure

// Set stage pressures
system.setDegasserPressure(4.0, "bara");
system.setCFUPressure(1.0, "bara");
// Caisson is atmospheric by default

// Set dissolved gas composition (from PVT analysis)
system.setDissolvedGasComposition(
    new String[]{"CO2", "methane", "ethane", "propane"},
    new double[]{0.51, 0.44, 0.04, 0.01}  // Mole fractions
);

// Optional: Use tuned kij parameters from lab calibration
system.setTunedInteractionParameters(true);

// Set salinity if known
system.setSalinity(10.0, "wt%");  // 10 wt% NaCl

// Run calculation
system.run();

// Get emissions report
System.out.println(system.getEmissionsReport());
```

### Python Example

```python
from neqsim import jneqsim

ProducedWaterDegassingSystem = jneqsim.process.equipment.util.ProducedWaterDegassingSystem

# Create degassing system
system = ProducedWaterDegassingSystem("Platform PW")

# Configure
system.setWaterFlowRate(100.0, "m3/hr")
system.setWaterTemperature(80.0, "C")
system.setInletPressure(30.0, "bara")

# Set stage pressures
system.setDegasserPressure(4.0, "bara")
system.setCFUPressure(1.0, "bara")

# Set dissolved gas (typical Gudrun composition)
components = ["CO2", "methane", "ethane", "propane"]
fractions = [0.51, 0.44, 0.04, 0.01]
for comp, frac in zip(components, fractions):
    system.addDissolvedComponent(comp, frac)

# Use tuned parameters
system.setTunedInteractionParameters(True)

# Run
system.run()

# Get results
print(system.getEmissionsReport())
print(f"\nTotal emissions: {system.getTotalEmissionsTonnesCO2eq():.2f} tonnes CO2eq/year")
```

### Detailed Emission Results

```java
// Get emissions by stage
double degasserCH4 = system.getDegasserEmissions().getCH4("kg/hr");
double degasserCO2 = system.getDegasserEmissions().getCO2("kg/hr");

double cfuCH4 = system.getCFUEmissions().getCH4("kg/hr");
double cfuCO2 = system.getCFUEmissions().getCO2("kg/hr");

double caissonCH4 = system.getCaissonEmissions().getCH4("kg/hr");
double caissonCO2 = system.getCaissonEmissions().getCO2("kg/hr");

// Total GWP (Global Warming Potential)
double totalCO2eq = system.getTotalEmissionsTonnesCO2eq();  // tonnes/year

System.out.println("Stage Emissions (kg/hr):");
System.out.printf("  Degasser: CH4=%.2f, CO2=%.2f%n", degasserCH4, degasserCO2);
System.out.printf("  CFU:      CH4=%.2f, CO2=%.2f%n", cfuCH4, cfuCO2);
System.out.printf("  Caisson:  CH4=%.2f, CO2=%.2f%n", caissonCH4, caissonCO2);
System.out.printf("Total GHG: %.1f tonnes CO2eq/year%n", totalCO2eq);
```

---

## Emission Calculations

### Gas Water Ratio (GWR)

The model calculates the Gas Water Ratio at each stage:

$$GWR = \frac{V_{gas}}{V_{water}}$$

Where:
- $V_{gas}$ = Volume of liberated gas at standard conditions (Sm³)
- $V_{water}$ = Volume of produced water (m³)

### CO₂ Equivalent Emissions

Total emissions in CO₂ equivalent:

$$E_{CO_2eq} = E_{CO_2} + GWP_{CH_4} \times E_{CH_4} + GWP_{NMVOC} \times E_{NMVOC}$$

Where:
- $GWP_{CH_4}$ = 25 (IPCC AR4) or 28 (AR5)
- $GWP_{NMVOC}$ = 11 (weighted average for C2-C5)

### Comparison with Handbook Method

```java
// Get method comparison
String comparison = system.getMethodComparisonReport();
System.out.println(comparison);

// Typical output:
// Method Comparison (kg CH4/hr):
//   Virtual measurement: 12.5
//   Handbook method:     18.2
//   Difference:          -31%
```

---

## Calibration and Validation

### Lab Calibration

For improved accuracy, calibrate against lab measurements:

```java
// Set lab-measured Gas Water Ratio for calibration
system.setLabGWR(0.15, "Sm3/m3");  // Measured at lab conditions

// Set lab gas composition (mole fractions)
system.setLabGasComposition(new String[]{"CO2", "methane"}, 
                            new double[]{0.52, 0.45});

// Enable tuned parameters
system.setTunedInteractionParameters(true);

// Run and compare
system.run();

double modelGWR = system.getCalculatedGWR();
double labGWR = system.getLabGWR();
double deviation = Math.abs((modelGWR - labGWR) / labGWR * 100);

System.out.printf("Model vs Lab: %.1f%% deviation%n", deviation);
```

### Online Calibration

For real-time virtual metering:

```java
// Update with process measurements
system.setActualWaterFlowRate(measuredFlow);
system.setActualTemperature(measuredTemp);
system.setActualPressures(degasserP, cfuP);

// Recalculate
system.run();

// Get updated emissions
double currentEmissions = system.getCurrentEmissionsRate("kg/hr");
```

---

## API Reference

### Constructors

| Constructor | Description |
|-------------|-------------|
| `ProducedWaterDegassingSystem(String name)` | Create system with name |

### Configuration - Flow Conditions

| Method | Description |
|--------|-------------|
| `setWaterFlowRate(double, String)` | Set water flow (kg/hr, m3/hr, bbl/day) |
| `setWaterTemperature(double, String)` | Set temperature (C, K, F) |
| `setInletPressure(double, String)` | Set upstream pressure (bara, barg) |
| `setSalinity(double, String)` | Set water salinity (wt%) |

### Configuration - Stage Pressures

| Method | Description |
|--------|-------------|
| `setDegasserPressure(double, String)` | Set degasser pressure |
| `setCFUPressure(double, String)` | Set CFU pressure |
| `setCaissonPressure(double, String)` | Set caisson pressure (default atmospheric) |

### Configuration - Composition

| Method | Description |
|--------|-------------|
| `setDissolvedGasComposition(String[], double[])` | Set gas composition |
| `addDissolvedComponent(String, double)` | Add single component |
| `clearDissolvedComponents()` | Clear composition |

### Configuration - Model

| Method | Description |
|--------|-------------|
| `setTunedInteractionParameters(boolean)` | Use tuned kij values |
| `setLabGWR(double, String)` | Set lab-measured GWR |
| `setLabGasComposition(String[], double[])` | Set lab gas composition |

### Results - Emissions

| Method | Description |
|--------|-------------|
| `getDegasserEmissions()` | Get EmissionsCalculator for degasser |
| `getCFUEmissions()` | Get EmissionsCalculator for CFU |
| `getCaissonEmissions()` | Get EmissionsCalculator for caisson |
| `getTotalEmissionsTonnesCO2eq()` | Get total GHG (tonnes CO2eq/year) |
| `getEmissionsReport()` | Get formatted emissions report |

### Results - Gas Data

| Method | Description |
|--------|-------------|
| `getCalculatedGWR()` | Get calculated Gas Water Ratio |
| `getTotalGasLiberated()` | Get total gas volume (Sm³/hr) |
| `getMethodComparisonReport()` | Compare virtual vs handbook methods |

### Results - Process

| Method | Description |
|--------|-------------|
| `getDegasserOutlet()` | Get degasser water outlet stream |
| `getCFUOutlet()` | Get CFU water outlet stream |
| `toJson()` | Get full results as JSON |

---

## Related Documentation

- [Emissions Calculator](../measurement_devices#emissionscalculator) - Component emission calculations
- [Offshore Emission Reporting](../../emissions/OFFSHORE_EMISSION_REPORTING) - Norwegian regulations
- [Norwegian Emission Methods Comparison](../../examples/NorwegianEmissionMethods_Comparison) - Method comparison example
- [H2S Distribution Guide](../../thermo/H2S_distribution_guide) - Gas-liquid equilibria
