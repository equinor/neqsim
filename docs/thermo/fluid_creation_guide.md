---
title: "Creating Fluids in NeqSim"
description: "This guide provides comprehensive documentation on how to create and configure thermodynamic fluids in NeqSim, including available equations of state, mixing rules, and best practices."
---

# Creating Fluids in NeqSim

This guide provides comprehensive documentation on how to create and configure thermodynamic fluids in NeqSim, including available equations of state, mixing rules, and best practices.

## Table of Contents
1. [Basic Fluid Creation](#1-basic-fluid-creation)
2. [Equations of State Overview](#2-equations-of-state-overview)
3. [Cubic Equations of State](#3-cubic-equations-of-state)
4. [Advanced Equations of State](#4-advanced-equations-of-state)
5. [Reference Equations (Helmholtz-Based)](#5-reference-equations-helmholtz-based)
6. [Activity Coefficient Models](#6-activity-coefficient-models)
7. [Electrolyte Models](#7-electrolyte-models)
8. [Mixing Rules](#8-mixing-rules)
9. [Adding Components](#9-adding-components)
10. [Heavy Fraction Characterization](#10-heavy-fraction-characterization)
11. [Complete Examples](#11-complete-examples)
12. [Model Selection Guidelines](#12-model-selection-guidelines)

---

## 1. Basic Fluid Creation

Creating a fluid in NeqSim follows a consistent pattern:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

// 1. Create the fluid with initial temperature (K) and pressure (bara)
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);

// 2. Add components
fluid.addComponent("methane", 0.90);    // name, moles
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.05);

// 3. Set up the mixing rule
fluid.setMixingRule("classic");

// 4. Initialize the fluid
fluid.init(0);
```

### Constructor Parameters

All fluid system classes accept these constructor signatures:

| Constructor | Description |
|-------------|-------------|
| `SystemXXX()` | Default: 298.15 K, 1.0 bara |
| `SystemXXX(T, P)` | Temperature (K), Pressure (bara) |
| `SystemXXX(T, P, checkForSolids)` | With solid phase checking enabled |

---

## 2. Equations of State Overview

NeqSim provides a wide range of thermodynamic models organized into categories:

| Category | Use Cases | Examples |
|----------|-----------|----------|
| **Cubic EoS** | General hydrocarbon processing | SRK, PR, PR-1978 |
| **CPA (Cubic Plus Association)** | Polar/associating fluids (water, glycols, alcohols) | SRK-CPA, PR-CPA |
| **Reference EoS** | High-accuracy natural gas, CCS | GERG-2008, EOS-CG |
| **SAFT-based** | Complex molecular interactions | PC-SAFT |
| **Activity Coefficient** | Non-ideal liquid mixtures | UNIFAC, NRTL |
| **Electrolyte** | Aqueous salt solutions | Electrolyte-CPA, Pitzer |
| **Specialized** | Specific applications | Soreide-Whitson (sour gas/brine) |

---

## 3. Cubic Equations of State

### 3.1 Soave-Redlich-Kwong (SRK) Family

#### SystemSrkEos
The standard SRK equation of state. Best for general gas and light hydrocarbon applications.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("CO2", 0.2);
fluid.setMixingRule("classic");
```

#### SystemSrkPenelouxEos
SRK with Peneloux volume correction for improved liquid density predictions.

```java
SystemInterface fluid = new SystemSrkPenelouxEos(300.0, 50.0);
```

#### SystemSrkMathiasCopeman
SRK with Mathias-Copeman alpha function for better vapor pressure predictions.

```java
SystemInterface fluid = new SystemSrkMathiasCopeman(300.0, 50.0);
```

#### SystemSrkTwuCoonEos
SRK with Twu-Coon alpha function.

```java
SystemInterface fluid = new SystemSrkTwuCoonEos(300.0, 50.0);
```

### 3.2 Peng-Robinson (PR) Family

#### SystemPrEos
Standard Peng-Robinson equation. Widely used for oil and gas applications.

```java
SystemInterface fluid = new SystemPrEos(300.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("n-heptane", 0.3);
fluid.setMixingRule("classic");
```

The PR equation is expressed as:
$$
P = \frac{RT}{v - b} - \frac{a \alpha}{v(v + b) + b(v - b)}
$$

#### SystemPrEos1978
Original 1978 Peng-Robinson formulation with modified alpha function.

```java
SystemInterface fluid = new SystemPrEos1978(300.0, 50.0);
```

#### SystemPrMathiasCopeman
PR with Mathias-Copeman alpha function for polar components.

```java
SystemInterface fluid = new SystemPrMathiasCopeman(300.0, 50.0);
```

### 3.3 Other Cubic EoS

#### SystemRKEos
Original Redlich-Kwong equation (historical interest, less accurate).

```java
SystemInterface fluid = new SystemRKEos(300.0, 50.0);
```

#### SystemTSTEos
Twu-Sim-Tassone equation of state.

```java
SystemInterface fluid = new SystemTSTEos(300.0, 50.0);
```

---

## 4. Advanced Equations of State

### 4.1 CPA (Cubic Plus Association)

CPA models add an association term to handle hydrogen bonding in polar molecules like water, alcohols, and glycols.

#### SystemSrkCPAstatoil
The Equinor (formerly Statoil) implementation of SRK-CPA. **Recommended for water-hydrocarbon systems**.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 50.0);
fluid.addComponent("water", 0.1);
fluid.addComponent("methane", 0.85);
fluid.addComponent("MEG", 0.05);  // Mono-ethylene glycol
fluid.setMixingRule(10);  // CPA mixing rule with temperature/composition dependency
```

#### SystemSrkCPA / SystemSrkCPAs
Alternative CPA implementations.

```java
SystemInterface fluid = new SystemSrkCPA(300.0, 50.0);
fluid.setMixingRule(7);  // CPA mixing rule
```

#### SystemPrCPA
Peng-Robinson with CPA association term.

```java
SystemInterface fluid = new SystemPrCPA(300.0, 50.0);
```

### 4.2 PC-SAFT

Perturbed Chain Statistical Associating Fluid Theory. Good for polymers and complex molecules.

```java
SystemInterface fluid = new SystemPCSAFT(300.0, 50.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("ethane", 0.5);
```

### 4.3 UMR-PRU (Universal Mixing Rule)

Peng-Robinson with UNIFAC-based mixing rules for improved predictions.

```java
SystemInterface fluid = new SystemUMRPRUEos(300.0, 50.0);
```

---

## 5. Reference Equations (Helmholtz-Based)

For high-accuracy applications, NeqSim provides reference equations of state based on the Helmholtz free energy:

$$
\alpha(\delta, \tau, \bar{x}) = \alpha^0(\delta, \tau, \bar{x}) + \alpha^r(\delta, \tau, \bar{x})
$$

### 5.1 GERG-2008

The ISO 20765-2 standard for natural gas. Highest accuracy for custody transfer and fiscal metering.

**Supported components (21):** Methane, Nitrogen, CO2, Ethane, Propane, n-Butane, i-Butane, n-Pentane, i-Pentane, n-Hexane, n-Heptane, n-Octane, n-Nonane, n-Decane, Hydrogen, Oxygen, CO, Water, Helium, Argon.

```java
import neqsim.thermo.system.SystemGERG2008Eos;

SystemInterface fluid = new SystemGERG2008Eos(288.15, 50.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("nitrogen", 0.02);
fluid.createDatabase(true);

// Access GERG-specific properties
double density = fluid.getPhase(0).getDensity_GERG2008();
```

### 5.2 EOS-CG

Extension of GERG-2008 for CCS (Carbon Capture and Storage) applications. Includes combustion gas components.

**Additional components:** SO2, NO, NO2, and others relevant to flue gas.

```java
import neqsim.thermo.system.SystemEOSCGEos;

SystemInterface fluid = new SystemEOSCGEos(300.0, 100.0);
fluid.addComponent("CO2", 0.95);
fluid.addComponent("nitrogen", 0.03);
fluid.addComponent("oxygen", 0.02);
```

### 5.3 Other Reference Equations

| Class | Description |
|-------|-------------|
| `SystemSpanWagnerEos` | Span-Wagner equation for CO2 |
| `SystemLeachmanEos` | Leachman equation for hydrogen |
| `SystemBWRSEos` | Benedict-Webb-Rubin-Starling |
| `SystemBnsEos` | BNS equation of state |

---

## 6. Activity Coefficient Models

For non-ideal liquid mixtures, especially polar and chemical systems:

### 6.1 UNIFAC

Group contribution method for activity coefficients.

```java
import neqsim.thermo.system.SystemUNIFAC;

SystemInterface fluid = new SystemUNIFAC(300.0, 1.0);
fluid.addComponent("methanol", 0.3);
fluid.addComponent("water", 0.7);
```

### 6.2 NRTL

Non-Random Two-Liquid model.

```java
import neqsim.thermo.system.SystemNRTL;

SystemInterface fluid = new SystemNRTL(300.0, 1.0);
fluid.addComponent("ethanol", 0.4);
fluid.addComponent("water", 0.6);
```

### 6.3 GE-Wilson

Wilson equation for activity coefficients.

```java
import neqsim.thermo.system.SystemGEWilson;

SystemInterface fluid = new SystemGEWilson(300.0, 1.0);
```

---

## 7. Electrolyte Models

For systems containing salts and ions in aqueous solutions:

### 7.1 Electrolyte-CPA (Equinor)

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 1.0);
fluid.addComponent("water", 1.0);
fluid.addComponent("Na+", 0.1);
fluid.addComponent("Cl-", 0.1);
```

### 7.2 Søreide-Whitson

Modified PR for sour gas systems and brine.

```java
import neqsim.thermo.system.SystemSoreideWhitson;

SystemSoreideWhitson fluid = new SystemSoreideWhitson(350.0, 200.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("CO2", 0.15);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("water", 0.1);
fluid.addSalinity(2.0, "mole/sec");  // Add salinity
fluid.setMixingRule(11);  // Soreide-Whitson mixing rule
```

### 7.3 Pitzer Model

For concentrated electrolyte solutions.

```java
import neqsim.thermo.system.SystemPitzer;

SystemInterface fluid = new SystemPitzer(298.15, 1.0);
```

---

## 8. Mixing Rules

Mixing rules determine how pure-component parameters are combined for mixtures. Set via `setMixingRule()`:

### 8.1 Available Mixing Rules

| Value | Name | Description |
|-------|------|-------------|
| 1 | `NO` | Classic with all kij = 0 (no interaction) |
| 2 | `CLASSIC` | Classic van der Waals with kij from database |
| 3 | `CLASSIC_HV` | Huron-Vidal with database parameters |
| 4 | `HV` | Huron-Vidal including temperature-dependent HVDijT |
| 5 | `WS` | Wong-Sandler (NRTL-based coupling) |
| 7 | `CPA_MIX` | Classic with CPA kij from database |
| 8 | `CLASSIC_T` | Classic with temperature-dependent kij |
| 9 | `CLASSIC_T_CPA` | Classic T-dependent kij for CPA |
| 10 | `CLASSIC_TX_CPA` | Classic T and composition dependent kij for CPA |
| 11 | `SOREIDE_WHITSON` | Søreide-Whitson mixing rule |
| 12 | `CLASSIC_T2` | Alternative temperature-dependent classic |

### 8.2 Setting Mixing Rules

```java
// By integer value
fluid.setMixingRule(2);

// By name (string)
fluid.setMixingRule("classic");
fluid.setMixingRule("HV");
fluid.setMixingRule("WS");
```

### 8.3 Mixing Rule Recommendations

| Application | Recommended Mixing Rule |
|-------------|------------------------|
| Light hydrocarbons | `classic` (2) |
| CO2-hydrocarbon | `classic` (2) with tuned kij |
| Polar mixtures | `HV` (4) or `WS` (5) |
| Water-hydrocarbon (CPA) | `CPA_MIX` (7) or `CLASSIC_TX_CPA` (10) |
| Sour gas with brine | `SOREIDE_WHITSON` (11) |

---

## 9. Adding Components

### 9.1 Basic Component Addition

```java
// Add by name and moles
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);

// Add with flow rate and unit
fluid.addComponent("methane", 100.0, "kg/hr");
fluid.addComponent("ethane", 50.0, "Sm3/day");

// Add multiple components at once
String[] names = {"methane", "ethane", "propane"};
double[] moles = {0.85, 0.10, 0.05};
fluid.addComponents(names, moles);
```

### 9.2 Supported Units

For `addComponent(name, value, unit)`:
- Molar: `mol/sec`, `mol/hr`
- Mass: `kg/sec`, `kg/hr`
- Volumetric: `Sm3/hr`, `Sm3/day`, `MSm3/day`, `Nlitre/min`

### 9.3 Common Component Names

NeqSim's database includes hundreds of components. Common names:

**Hydrocarbons:**
`methane`, `ethane`, `propane`, `i-butane`, `n-butane`, `i-pentane`, `n-pentane`, `n-hexane`, `n-heptane`, `n-octane`, `n-nonane`, `n-decane`

**Inorganics:**
`nitrogen`, `oxygen`, `CO2`, `H2S`, `water`, `hydrogen`, `helium`, `argon`

**Polar/Associating:**
`methanol`, `ethanol`, `MEG` (mono-ethylene glycol), `TEG` (tri-ethylene glycol), `DEG`

**Ions:**
`Na+`, `K+`, `Ca++`, `Mg++`, `Cl-`, `SO4--`, `HCO3-`

---

## 10. Heavy Fraction Characterization

For petroleum fluids, NeqSim supports TBP (True Boiling Point) and plus-fraction characterization.

### 10.1 TBP Fractions

```java
SystemInterface oil = new SystemSrkEos(350.0, 100.0);
oil.createDatabase(true);  // Required before adding TBP fractions

// addTBPfraction(name, moles, molarMass [g/mol], density [g/cm3])
oil.addTBPfraction("C7", 0.05, 96.0, 0.738);
oil.addTBPfraction("C8", 0.04, 107.0, 0.765);
oil.addTBPfraction("C9", 0.03, 121.0, 0.781);
oil.addTBPfraction("C10", 0.02, 134.0, 0.792);

oil.setMixingRule("classic");
```

### 10.2 Plus Fractions

```java
// addPlusFraction(name, moles, molarMass [g/mol], density [g/cm3])
oil.addPlusFraction("C20+", 0.10, 350.0, 0.88);
```

### 10.3 TBP Characterization Models

NeqSim provides several models for estimating critical properties from TBP data:

```java
// Set TBP model before adding fractions
fluid.getCharacterization().setTBPModel("PedersenSRK");  // Default for SRK
fluid.getCharacterization().setTBPModel("PedersenPR");   // Default for PR
fluid.getCharacterization().setTBPModel("Lee-Kesler");
fluid.getCharacterization().setTBPModel("Twu");
fluid.getCharacterization().setTBPModel("RiaziDaubert");
```

---

## 11. Complete Examples

### 11.1 Natural Gas Processing

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class NaturalGasExample {
    public static void main(String[] args) {
        // Create SRK fluid at pipeline conditions
        SystemInterface gas = new SystemSrkEos(283.15, 70.0);
        
        // Typical natural gas composition
        gas.addComponent("nitrogen", 0.02);
        gas.addComponent("CO2", 0.01);
        gas.addComponent("methane", 0.85);
        gas.addComponent("ethane", 0.06);
        gas.addComponent("propane", 0.03);
        gas.addComponent("i-butane", 0.01);
        gas.addComponent("n-butane", 0.01);
        gas.addComponent("i-pentane", 0.005);
        gas.addComponent("n-pentane", 0.005);
        
        gas.setMixingRule("classic");
        
        // Flash calculation
        ThermodynamicOperations ops = new ThermodynamicOperations(gas);
        ops.TPflash();
        gas.initProperties();
        
        // Display results
        System.out.println("Density: " + gas.getDensity("kg/m3") + " kg/m3");
        System.out.println("Z-factor: " + gas.getZ());
        System.out.println("Molecular weight: " + gas.getMolarMass() * 1000 + " g/mol");
    }
}
```

### 11.2 Water-Hydrocarbon System with CPA

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class WaterHydrocarbonExample {
    public static void main(String[] args) {
        // CPA for associating systems
        SystemInterface fluid = new SystemSrkCPAstatoil(323.15, 50.0);
        
        fluid.addComponent("methane", 0.70);
        fluid.addComponent("ethane", 0.10);
        fluid.addComponent("propane", 0.05);
        fluid.addComponent("water", 0.10);
        fluid.addComponent("MEG", 0.05);
        
        fluid.setMixingRule(10);  // Temperature and composition dependent CPA
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        ops.TPflash();
        
        System.out.println("Number of phases: " + fluid.getNumberOfPhases());
        fluid.prettyPrint();
    }
}
```

### 11.3 High-Accuracy Fiscal Metering (GERG-2008)

```java
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class FiscalMeteringExample {
    public static void main(String[] args) {
        // GERG-2008 for custody transfer accuracy
        SystemInterface gas = new SystemGERG2008Eos(288.15, 40.0);
        
        gas.addComponent("methane", 0.92);
        gas.addComponent("ethane", 0.04);
        gas.addComponent("propane", 0.02);
        gas.addComponent("nitrogen", 0.01);
        gas.addComponent("CO2", 0.01);
        
        gas.createDatabase(true);
        
        ThermodynamicOperations ops = new ThermodynamicOperations(gas);
        ops.TPflash();
        
        // GERG-specific high-accuracy density
        double gergDensity = gas.getPhase(0).getDensity_GERG2008();
        System.out.println("GERG-2008 Density: " + gergDensity + " kg/m3");
    }
}
```

### 11.4 Oil Characterization

```java
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class OilCharacterizationExample {
    public static void main(String[] args) {
        SystemInterface oil = new SystemPrEos(350.0, 150.0);
        oil.createDatabase(true);
        
        // Light ends
        oil.addComponent("nitrogen", 0.005);
        oil.addComponent("CO2", 0.02);
        oil.addComponent("methane", 0.35);
        oil.addComponent("ethane", 0.08);
        oil.addComponent("propane", 0.06);
        oil.addComponent("i-butane", 0.02);
        oil.addComponent("n-butane", 0.03);
        oil.addComponent("i-pentane", 0.02);
        oil.addComponent("n-pentane", 0.02);
        oil.addComponent("n-hexane", 0.03);
        
        // TBP fractions (moles, MW g/mol, density g/cm3)
        oil.addTBPfraction("C7", 0.05, 96.0, 0.738);
        oil.addTBPfraction("C8", 0.04, 107.0, 0.765);
        oil.addTBPfraction("C9", 0.03, 121.0, 0.781);
        oil.addTBPfraction("C10", 0.02, 134.0, 0.792);
        
        // Plus fraction
        oil.addPlusFraction("C11+", 0.18, 250.0, 0.85);
        
        oil.setMixingRule("classic");
        
        ThermodynamicOperations ops = new ThermodynamicOperations(oil);
        ops.TPflash();
        oil.initProperties();
        
        oil.prettyPrint();
    }
}
```

---

## 12. Model Selection Guidelines

### Quick Reference Table

| System Type | Recommended Model | Mixing Rule |
|-------------|-------------------|-------------|
| Dry natural gas | `SystemSrkEos` or `SystemPrEos` | `classic` (2) |
| Wet gas / condensate | `SystemPrEos` | `classic` (2) |
| Black oil | `SystemPrEos` with TBP | `classic` (2) |
| Water-hydrocarbon | `SystemSrkCPAstatoil` | `CLASSIC_TX_CPA` (10) |
| Glycol dehydration | `SystemSrkCPAstatoil` | `CPA_MIX` (7) |
| Sour gas / brine | `SystemSoreideWhitson` | `SOREIDE_WHITSON` (11) |
| Fiscal metering | `SystemGERG2008Eos` | N/A |
| CCS / CO2 transport | `SystemEOSCGEos` | N/A |
| Electrolyte solutions | `SystemElectrolyteCPAstatoil` | N/A |
| Polar organics | `SystemUNIFAC` or `SystemNRTL` | N/A |

### Decision Flow

1. **Is high accuracy required for custody transfer?** → Use GERG-2008
2. **Does the system contain water, glycols, or alcohols?** → Use CPA models
3. **Is it a sour gas system with brine?** → Use Søreide-Whitson
4. **Is it a standard hydrocarbon system?** → Use SRK or PR
5. **Does it contain electrolytes?** → Use Electrolyte-CPA or Pitzer
6. **Is it a non-ideal organic mixture?** → Use UNIFAC or NRTL

### Performance vs. Accuracy Trade-offs

| Model Type | Speed | Accuracy | Best For |
|------------|-------|----------|----------|
| Cubic (SRK/PR) | Fast | Good | General process simulation |
| CPA | Medium | Very Good | Polar/associating systems |
| GERG-2008 | Slow | Excellent | Fiscal metering, calibration |
| UNIFAC | Medium | Good | Chemical process design |

---

## See Also

- [Thermodynamic Workflows](thermodynamic_workflows) - Flash calculations and operations
- [PVT and Fluid Characterization](pvt_fluid_characterization) - Heavy fraction handling
- [Mathematical Models](mathematical_models) - Equation details
- [GERG-2008 and EOS-CG](gerg2008_eoscg) - Reference equation details
- [Physical Properties](physical_properties) - Transport property calculations
