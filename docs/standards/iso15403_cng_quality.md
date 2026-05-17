---
title: "ISO 15403 - CNG Quality"
description: "ISO 15403 specifies requirements for natural gas used as compressed fuel for vehicles (CNG)."
---

# ISO 15403 - CNG Quality

ISO 15403 specifies requirements for natural gas used as compressed fuel for vehicles (CNG).

## Table of Contents
- [Overview](#overview)
- [Calculated Parameters](#calculated-parameters)
- [Implementation](#implementation)
- [Usage Examples](#usage-examples)
- [Methane Number Concepts](#methane-number-concepts)

---

## Overview

**Standard:** ISO 15403-1:2006 - Natural gas — Natural gas for use as a compressed fuel for vehicles

**Purpose:** Assess natural gas quality for use in vehicle engines by calculating:
- Motor Octane Number (MON)
- Methane Number (MN)

**Class:** `Standard_ISO15403`

---

## Calculated Parameters

### Motor Octane Number (MON)

MON indicates knock resistance. Calculated from gas composition:

$$MON = 137.78 \cdot x_{CH_4} + 29.948 \cdot x_{C_2H_6} - 18.193 \cdot x_{C_3H_8} - 167.062 \cdot (x_{nC_4} + x_{iC_4}) + 181.233 \cdot x_{CO_2} + 26.944 \cdot x_{N_2}$$

where $x_i$ is the mole fraction of component i.

### Methane Number (MN)

MN is derived from MON:

$$MN = 1.445 \cdot MON - 103.42$$

**Interpretation:**
- MN = 100: Pure methane
- MN = 0: Pure hydrogen
- Higher MN = better knock resistance

---

## Implementation

### Constructor

```java
import neqsim.standards.gasquality.Standard_ISO15403;

// Create from gas composition
Standard_ISO15403 iso15403 = new Standard_ISO15403(thermoSystem);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Calculate MON and MN |
| `getValue("MON")` | Get Motor Octane Number |
| `getValue("MN")` | Get Methane Number |

---

## Usage Examples

### Basic Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.gasquality.Standard_ISO15403;

// CNG composition
SystemInterface cng = new SystemSrkEos(273.15 + 15, 200.0);
cng.addComponent("methane", 0.92);
cng.addComponent("ethane", 0.04);
cng.addComponent("propane", 0.01);
cng.addComponent("n-butane", 0.002);
cng.addComponent("i-butane", 0.003);
cng.addComponent("CO2", 0.015);
cng.addComponent("nitrogen", 0.01);
cng.setMixingRule("classic");

// Calculate methane number
Standard_ISO15403 iso15403 = new Standard_ISO15403(cng);
iso15403.calculate();

double mon = iso15403.getValue("MON");
double mn = iso15403.getValue("MN");

System.out.printf("Motor Octane Number (MON) = %.1f%n", mon);
System.out.printf("Methane Number (MN) = %.1f%n", mn);
```

### Effect of Composition on Methane Number

```java
// Analyze MN sensitivity to C2+ content
double[] ethaneContents = {0.01, 0.03, 0.05, 0.08, 0.10};

System.out.println("Ethane (mol%) | MON   | MN");
System.out.println("--------------|-------|------");

for (double c2 : ethaneContents) {
    SystemInterface gas = new SystemSrkEos(273.15 + 15, 200.0);
    gas.addComponent("methane", 0.97 - c2);
    gas.addComponent("ethane", c2);
    gas.addComponent("CO2", 0.02);
    gas.addComponent("nitrogen", 0.01);
    gas.setMixingRule("classic");
    
    Standard_ISO15403 std = new Standard_ISO15403(gas);
    std.calculate();
    
    System.out.printf("%13.0f | %.1f | %.1f%n", 
        c2 * 100, 
        std.getValue("MON"), 
        std.getValue("MN"));
}
```

### CO2 and N2 Effects

```java
// CO2 and N2 improve methane number
SystemInterface leanGas = new SystemSrkEos(273.15 + 15, 200.0);
leanGas.addComponent("methane", 0.85);
leanGas.addComponent("ethane", 0.05);
leanGas.addComponent("propane", 0.02);

// Base case - no inerts
leanGas.addComponent("CO2", 0.0);
leanGas.addComponent("nitrogen", 0.0);
leanGas.setMixingRule("classic");

Standard_ISO15403 baseStd = new Standard_ISO15403(leanGas);
baseStd.calculate();
System.out.printf("Base case MN: %.1f%n", baseStd.getValue("MN"));

// With 5% CO2
SystemInterface withCO2 = leanGas.clone();
withCO2.addComponent("CO2", 0.05);
Standard_ISO15403 co2Std = new Standard_ISO15403(withCO2);
co2Std.calculate();
System.out.printf("With 5%% CO2 MN: %.1f%n", co2Std.getValue("MN"));

// With 5% N2
SystemInterface withN2 = leanGas.clone();
withN2.addComponent("nitrogen", 0.05);
Standard_ISO15403 n2Std = new Standard_ISO15403(withN2);
n2Std.calculate();
System.out.printf("With 5%% N2 MN: %.1f%n", n2Std.getValue("MN"));
```

---

## Methane Number Concepts

### Component Effects

| Component | Effect on MN |
|-----------|--------------|
| Methane | Increases MN |
| Ethane | Slight decrease |
| Propane | Moderate decrease |
| Butanes | Strong decrease |
| CO₂ | Increases MN |
| N₂ | Increases MN |
| H₂ | Decreases MN |

### Typical Values

| Gas Type | Typical MN |
|----------|------------|
| Pure methane | 100 |
| Lean natural gas | 85-95 |
| Associated gas | 70-85 |
| Biogas | 130-140 |
| LNG regasified | 75-90 |

### Specifications

| Region | Minimum MN |
|--------|------------|
| Europe (typical) | 65-70 |
| Germany (DIN 51624) | 70 |
| California | 80 |

---

## Technical Notes

### Correlation Validity

The correlation is valid for:
- Methane content > 70%
- Limited C4+ content
- Typical natural gas compositions

### Limitations

1. Does not account for C5+ hydrocarbons
2. Hydrogen effects not included in correlation
3. Best for pipeline-quality natural gas

### Alternative Methods

For more complex gases, consider:
- AVL Methane Number calculation
- Wärtsilä MN method
- GRI/MWM correlations

---

## References

1. ISO 15403-1:2006 - Natural gas — Natural gas for use as a compressed fuel for vehicles — Part 1: Designation of the quality
2. DIN 51624 - Automotive fuels - Compressed natural gas - Requirements and test methods
3. SAE J1616 - Recommended Practice for Compressed Natural Gas Vehicle Fuel
