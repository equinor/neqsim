---
title: "Thermodynamic Models in NeqSim"
description: "This document provides a comprehensive overview of the thermodynamic models available in NeqSim, their theoretical foundations, and practical guidance on when and how to use each model. Models are cla..."
---

# Thermodynamic Models in NeqSim

This document provides a comprehensive overview of the thermodynamic models available in NeqSim, their theoretical foundations, and practical guidance on when and how to use each model. Models are classified into categories based on their mathematical formulation and application domain.

## Table of Contents

1. [Introduction](#1-introduction)
2. [Model Categories Overview](#2-model-categories-overview)
3. [Cubic Equations of State](#3-cubic-equations-of-state)
4. [CPA (Cubic Plus Association) Models](#4-cpa-cubic-plus-association-models)
5. [Reference Equations (Helmholtz-Based)](#5-reference-equations-helmholtz-based)
6. [Activity Coefficient (GE) Models](#6-activity-coefficient-ge-models)
7. [Electrolyte Models](#7-electrolyte-models)
8. [Mixing Rules](#8-mixing-rules)
9. [Auto-Select Model Feature](#9-auto-select-model-feature)
10. [Model Selection Guidelines](#10-model-selection-guidelines)
11. [Complete Reference Tables](#11-complete-reference-tables)

---

## 1. Introduction

NeqSim (Non-Equilibrium Simulator) provides a rich library of thermodynamic models for simulating phase equilibria, physical properties, and process operations. Choosing the appropriate thermodynamic model is crucial for obtaining accurate results in process simulation.

### General Workflow

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;

// 1. Choose and instantiate the thermodynamic model
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);  // T [K], P [bara]

// 2. Add components
fluid.addComponent("methane", 0.90);
fluid.addComponent("ethane", 0.10);

// 3. Set an appropriate mixing rule
fluid.setMixingRule("classic");

// 4. Initialize and perform calculations
fluid.init(0);
```

---

## 2. Model Categories Overview

| Category | Mathematical Basis | Key Use Cases | Examples |
|----------|-------------------|---------------|----------|
| **Cubic EoS** | $P = \frac{RT}{v-b} - \frac{a(T)}{(v+\epsilon b)(v+\sigma b)}$ | General hydrocarbons, gas processing | SRK, PR |
| **CPA** | Cubic EoS + Association term | Polar/associating fluids (water, glycols, alcohols) | SRK-CPA, PR-CPA |
| **Reference EoS** | Helmholtz free energy explicit | High-accuracy metering, CCS | GERG-2008, EOS-CG |
| **Activity Coefficient** | Excess Gibbs energy ($G^E$) | Non-ideal liquid mixtures | UNIFAC, NRTL |
| **Electrolyte** | CPA + Electrostatic contributions | Aqueous salt solutions | Electrolyte-CPA, Pitzer |
| **Specialized** | Modified equations | Specific applications | Søreide-Whitson |

---

## 3. Cubic Equations of State

### 3.1 Theoretical Foundation

Cubic equations of state express pressure as a function of temperature and molar volume in a cubic polynomial form:

$$
P = \frac{RT}{v - b} - \frac{a(T)}{(v + \epsilon b)(v + \sigma b)}
$$

Where:
- $P$ = pressure
- $T$ = temperature
- $v$ = molar volume
- $R$ = gas constant
- $a(T)$ = temperature-dependent energy parameter (attraction)
- $b$ = co-volume parameter (repulsion)
- $\epsilon, \sigma$ = EoS-specific constants

The energy parameter typically uses an alpha function:

$$
a(T) = a_c \cdot \alpha(T_r, \omega)
$$

Where $T_r = T/T_c$ is the reduced temperature and $\omega$ is the acentric factor.

### 3.2 Soave-Redlich-Kwong (SRK) Family

For SRK: $\epsilon = 0$, $\sigma = 1$

$$
P = \frac{RT}{v - b} - \frac{a(T)}{v(v + b)}
$$

| Class | Description | Best For |
|-------|-------------|----------|
| `SystemSrkEos` | Standard SRK | General gas/light hydrocarbon |
| `SystemSrkPenelouxEos` | SRK with Peneloux volume correction | Improved liquid density |
| `SystemSrkMathiasCopeman` | SRK with Mathias-Copeman alpha function | Better vapor pressure |
| `SystemSrkTwuCoonEos` | SRK with Twu-Coon alpha function | Polar components |
| `SystemSrkSchwartzentruberEos` | SRK-Schwartzentruber | Polar/associated fluids |

**Example: Standard SRK**

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("nitrogen", 0.05);
fluid.setMixingRule("classic");
```

### 3.3 Peng-Robinson (PR) Family

For PR: $\epsilon = 1 - \sqrt{2}$, $\sigma = 1 + \sqrt{2}$

$$
P = \frac{RT}{v - b} - \frac{a(T)}{v(v + b) + b(v - b)}
$$

| Class | Description | Best For |
|-------|-------------|----------|
| `SystemPrEos` | Standard PR | General oil & gas |
| `SystemPrEos1978` | Original 1978 formulation | Classic applications |
| `SystemPrMathiasCopeman` | PR with Mathias-Copeman alpha | Polar components |
| `SystemPrDanesh` | Modified PR | Heavy oil systems |
| `SystemPrEosvolcor` | PR with volume correction | Liquid density |
| `SystemPrEosDelft1998` | Delft 1998 modification | Gas condensates |

**Example: Peng-Robinson**

```java
SystemInterface fluid = new SystemPrEos(350.0, 150.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("n-heptane", 0.20);
fluid.addComponent("n-decane", 0.10);
fluid.setMixingRule("classic");
```

### 3.4 Other Cubic EoS

| Class | Description |
|-------|-------------|
| `SystemRKEos` | Original Redlich-Kwong (historical) |
| `SystemTSTEos` | Twu-Sim-Tassone equation |
| `SystemBWRSEos` | Benedict-Webb-Rubin-Starling (extended virial) |

---

## 4. CPA (Cubic Plus Association) Models

### 4.1 Theoretical Foundation

CPA (Cubic Plus Association) extends cubic EoS to handle hydrogen bonding in polar molecules like water, alcohols, and glycols. The pressure is expressed as:

$$
P = P_{\text{cubic}} + P_{\text{association}}
$$

The association term accounts for hydrogen bonding:

$$
P_{\text{association}} = -\frac{1}{2} RT \rho \sum_i x_i \sum_{A_i} \left( 1 - X_{A_i} \right) \frac{\partial \ln g}{\partial v}
$$

Where:
- $X_{A_i}$ = fraction of site A on molecule $i$ not bonded to other active sites
- $g$ = radial distribution function
- Sites can be of type 4C (water, glycols), 2B (alcohols), or other schemes

The non-bonded site fraction $X_{A_i}$ is determined by solving:

$$
X_{A_i} = \frac{1}{1 + \rho \sum_j x_j \sum_{B_j} X_{B_j} \Delta^{A_i B_j}}
$$

Where $\Delta^{A_i B_j}$ is the association strength between site A on molecule $i$ and site B on molecule $j$.

### 4.2 Available CPA Models

| Class | Description | Mixing Rule |
|-------|-------------|-------------|
| `SystemSrkCPAstatoil` | **Recommended** Equinor SRK-CPA implementation | 10 |
| `SystemSrkCPA` | Standard SRK-CPA | 7 |
| `SystemSrkCPAs` | Alternative SRK-CPA | 7 |
| `SystemPrCPA` | Peng-Robinson with CPA | 7 |
| `SystemUMRCPAEoS` | UMR-CPA with UNIFAC | - |

### 4.3 Association Schemes

| Scheme | Sites | Examples |
|--------|-------|----------|
| 4C | 2 donors + 2 acceptors | Water, glycols |
| 2B | 1 donor + 1 acceptor | Alcohols |
| CR-1 | Cross-association | Water-MEG, Water-methanol |

### 4.4 Example: Water-Hydrocarbon System

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;

SystemInterface fluid = new SystemSrkCPAstatoil(323.15, 50.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("water", 0.10);
fluid.addComponent("MEG", 0.05);  // Mono-ethylene glycol

// Use mixing rule 10 (recommended for CPA)
fluid.setMixingRule(10);  // CLASSIC_TX_CPA

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

---

## 5. Reference Equations (Helmholtz-Based)

### 5.1 Theoretical Foundation

Reference equations of state are explicit in the dimensionless Helmholtz free energy $\alpha$:

$$
\alpha(\delta, \tau, \bar{x}) = \frac{a(\rho, T, \bar{x})}{RT} = \alpha^0(\delta, \tau, \bar{x}) + \alpha^r(\delta, \tau, \bar{x})
$$

Where:
- $\delta = \rho / \rho_r$ = reduced density
- $\tau = T_r / T$ = inverse reduced temperature
- $\alpha^0$ = ideal gas contribution
- $\alpha^r$ = residual contribution

The residual contribution is fitted to high-accuracy experimental data:

$$
\alpha^r(\delta, \tau, \bar{x}) = \sum_{i=1}^{N} x_i \alpha_{0i}^r(\delta, \tau) + \sum_{i=1}^{N-1} \sum_{j=i+1}^{N} x_i x_j F_{ij} \alpha_{ij}^r(\delta, \tau)
$$

These models provide superior accuracy for density, speed of sound, and heat capacity compared to cubic EoS.

### 5.2 GERG-2008

**Standard:** ISO 20765-2  
**Application:** Natural gas custody transfer, fiscal metering  
**Accuracy:** ±0.1% in density for typical natural gas

**Supported Components (21):**
Methane, Nitrogen, CO2, Ethane, Propane, n-Butane, i-Butane, n-Pentane, i-Pentane, n-Hexane, n-Heptane, n-Octane, n-Nonane, n-Decane, Hydrogen, Oxygen, CO, Water, Helium, Argon

```java
import neqsim.thermo.system.SystemGERG2008Eos;

SystemInterface fluid = new SystemGERG2008Eos(288.15, 50.0);
fluid.addComponent("methane", 0.92);
fluid.addComponent("ethane", 0.04);
fluid.addComponent("propane", 0.02);
fluid.addComponent("nitrogen", 0.01);
fluid.addComponent("CO2", 0.01);
fluid.createDatabase(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Access GERG-specific high-accuracy density
double gergDensity = fluid.getPhase(0).getDensity_GERG2008();
```

### 5.3 GERG-2008-H2 (Hydrogen Enhanced)

Extension for hydrogen-rich blends with improved H2 binary parameters:

```java
SystemGERG2008Eos fluid = new SystemGERG2008Eos(300.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("hydrogen", 0.3);

// Enable hydrogen-enhanced model
fluid.useHydrogenEnhancedModel();

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 5.4 EOS-CG

**Application:** Carbon Capture and Storage (CCS), combustion gases  
**Extension:** Includes SO2, NO, NO2, HCl, Cl2, COS in addition to GERG-2008 components

```java
import neqsim.thermo.system.SystemEOSCGEos;

SystemInterface fluid = new SystemEOSCGEos(300.0, 100.0);
fluid.addComponent("CO2", 0.95);
fluid.addComponent("nitrogen", 0.03);
fluid.addComponent("SO2", 0.02);
fluid.createDatabase(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

double density = fluid.getPhase(0).getDensity_EOSCG();
```

### 5.5 Other Reference Equations

| Class | Description | Application |
|-------|-------------|-------------|
| `SystemSpanWagnerEos` | Span-Wagner equation | Pure CO2 |
| `SystemLeachmanEos` | Leachman equation | Pure hydrogen |
| `SystemVegaEos` | Vega equation | Specialized applications |
| `SystemAmmoniaEos` | Ammonia-specific | Ammonia systems |

---

## 6. Activity Coefficient (GE) Models

### 6.1 Theoretical Foundation

Activity coefficient models describe non-ideal liquid behavior through the excess Gibbs energy:

$$
G^E = RT \sum_i x_i \ln \gamma_i
$$

Where $\gamma_i$ is the activity coefficient of component $i$.

### 6.2 UNIFAC (Universal Functional Activity Coefficient)

Group contribution method that predicts activity coefficients from molecular group interactions:

$$
\ln \gamma_i = \ln \gamma_i^C + \ln \gamma_i^R
$$

Where the combinatorial ($C$) and residual ($R$) contributions are calculated from group properties.

```java
import neqsim.thermo.system.SystemUNIFAC;

SystemInterface fluid = new SystemUNIFAC(300.0, 1.0);
fluid.addComponent("methanol", 0.3);
fluid.addComponent("water", 0.7);
```

### 6.3 NRTL (Non-Random Two-Liquid)

Local composition model with binary interaction parameters:

$$
\ln \gamma_i = \frac{\sum_j x_j \tau_{ji} G_{ji}}{\sum_k x_k G_{ki}} + \sum_j \frac{x_j G_{ij}}{\sum_k x_k G_{kj}} \left( \tau_{ij} - \frac{\sum_m x_m \tau_{mj} G_{mj}}{\sum_k x_k G_{kj}} \right)
$$

```java
import neqsim.thermo.system.SystemNRTL;

SystemInterface fluid = new SystemNRTL(300.0, 1.0);
fluid.addComponent("ethanol", 0.4);
fluid.addComponent("water", 0.6);
```

### 6.4 Other GE Models

| Class | Description |
|-------|-------------|
| `SystemGEWilson` | Wilson equation |
| `SystemUNIFACpsrk` | UNIFAC with PSRK parameters |
| `SystemUMRPRUEos` | Peng-Robinson with UNIFAC mixing |
| `SystemUMRPRUMCEos` | UMR-PRU with Mathias-Copeman |

---

## 7. Electrolyte Models

### 7.1 Theoretical Foundation

Electrolyte models extend CPA with electrostatic contributions for aqueous salt solutions:

$$
A^{res} = A^{CPA} + A^{elec}
$$

The electrostatic contribution typically includes:

$$
A^{elec} = A^{MSA} + A^{Born} + A^{SR}
$$

Where:
- $A^{MSA}$ = Mean Spherical Approximation (ion-ion screening)
- $A^{Born}$ = Born solvation term (ion-solvent)
- $A^{SR}$ = Short-range interactions (specific ion effects)

**Born Solvation Term:**

$$
\frac{A^{Born}}{RT} = -\frac{e^2 N_A}{8\pi\varepsilon_0 k_B T} \sum_i n_i \frac{z_i^2}{\sigma_i} \left(1 - \frac{1}{\varepsilon_r}\right)
$$

### 7.2 Electrolyte-CPA (Equinor)

**Recommended for:** Aqueous electrolyte solutions with associating solvents

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
system.addComponent("water", 55.5);
system.addComponent("Na+", 1.0);
system.addComponent("Cl-", 1.0);
system.chemicalReactionInit();  // Enable pH and speciation
system.createDatabase(true);
system.setMixingRule(10);  // Required for electrolyte CPA

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Access activity coefficients
double meanGamma = system.getPhase(0).getMeanIonicActivityCoefficient("Na+", "Cl-");
```

### 7.3 Søreide-Whitson Model

**Application:** Gas solubility in brine, produced water systems, sour gas with formation water

The Søreide-Whitson model is a modified Peng-Robinson equation of state specifically designed for predicting gas solubility in aqueous systems containing dissolved salts. **This model is used in NeqSimLive for real-time emission calculations from produced water degassing on offshore platforms.**

The key innovation is a modified alpha function for water that incorporates salinity:

$$
\alpha = A^2
$$

where:

$$
A(T_r, c_s) = 1.0 + 0.453 \left[ 1.0 - T_r \left( 1.0 - 0.0103 \cdot c_s^{1.1} \right) \right] + 0.0034 \left( T_r^{-3} - 1.0 \right)
$$

- $T_r = T / T_c$ is the reduced temperature
- $c_s$ is the salinity expressed as equivalent NaCl molality (mol/kg H₂O)

```java
import neqsim.thermo.system.SystemSoreideWhitson;

// Create Søreide-Whitson system for produced water
SystemSoreideWhitson fluid = new SystemSoreideWhitson(353.15, 30.0);  // 80°C, 30 bara
fluid.addComponent("water", 0.92);
fluid.addComponent("methane", 0.05);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("ethane", 0.01);

// Add formation water salinity
fluid.addSalinity("NaCl", 1.2, "mole/sec");  // Dominant salt
fluid.addSalinity("CaCl2", 0.08, "mole/sec");

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

> **📖 Detailed Documentation:** For complete mathematical formulation, salt type coefficients, validation data, and literature references, see [Søreide-Whitson Model Documentation](SoreideWhitsonModel).
>
> **Reference:** Søreide, I. & Whitson, C.H. (1992). "Peng-Robinson predictions for hydrocarbons, CO₂, N₂, and H₂S with pure water and NaCl brine". *Fluid Phase Equilibria*, 77, 217-240.

### 7.4 Other Electrolyte Models

| Class | Description | Use Case |
|-------|-------------|----------|
| `SystemPitzer` | Pitzer model | Concentrated brines |
| `SystemDesmukhMather` | Desmukh-Mather | Amine systems |
| `SystemKentEisenberg` | Kent-Eisenberg | CO2/H2S in amines |
| `SystemDuanSun` | Duan-Sun | CO2 solubility in brine |
| `SystemFurstElectrolyteEos` | Fürst electrolyte EoS | General electrolytes |

---

## 8. Mixing Rules

### 8.1 Overview

Mixing rules determine how pure-component parameters are combined for mixtures. The choice of mixing rule is as important as the choice of equation of state.

### 8.2 Classic Mixing Rules

**van der Waals One-Fluid Mixing Rule:**

$$
a_{mix} = \sum_i \sum_j x_i x_j a_{ij}
$$

$$
b_{mix} = \sum_i x_i b_i
$$

With combining rule:

$$
a_{ij} = \sqrt{a_i a_j}(1 - k_{ij})
$$

| Type | Name | Description |
|------|------|-------------|
| 1 | `NO` | All $k_{ij} = 0$ (no interactions) |
| 2 | `CLASSIC` | Classic with database $k_{ij}$ |
| 8 | `CLASSIC_T` | Temperature-dependent: $k_{ij}(T) = k_{ij,0} + k_{ij,T} \cdot T$ |
| 12 | `CLASSIC_T2` | Inverse T-dependency: $k_{ij}(T) = k_{ij,0} + k_{ij,T}/T$ |

### 8.3 Huron-Vidal Mixing Rules

Combines cubic EoS with activity coefficient models at infinite pressure:

$$
a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} - \frac{G^E}{\Lambda} \right)
$$

Where $\Lambda \approx 0.693$ for SRK.

| Type | Name | Description |
|------|------|-------------|
| 3 | `CLASSIC_HV` | Huron-Vidal with database NRTL parameters |
| 4 | `HV` | HV with temperature-dependent parameters |

**Usage with GE model:**

```java
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
fluid.setMixingRule("HV", "NRTL");
```

### 8.4 Wong-Sandler Mixing Rule

Matches both second virial coefficient and excess Gibbs energy:

$$
b_{mix} = \frac{\sum_i \sum_j x_i x_j \left( b - \frac{a}{RT} \right)_{ij}}{1 - \frac{A_\infty^E}{CRT} - \sum_i x_i \frac{a_i}{RTb_i}}
$$

| Type | Name | Description |
|------|------|-------------|
| 5 | `WS` | Wong-Sandler (NRTL-based) |

```java
fluid.setMixingRule("WS", "NRTL");
```

### 8.5 CPA Mixing Rules

| Type | Name | Description |
|------|------|-------------|
| 7 | `CPA_MIX` | Classic for CPA systems |
| 9 | `CLASSIC_T_CPA` | Temperature-dependent CPA |
| 10 | `CLASSIC_TX_CPA` | **Recommended** T and x dependent for CPA |

**Mixing Rule 10 Auto-Selection:**

Mixing rule 10 automatically selects the appropriate sub-type:

| Condition | Sub-Type | Description |
|-----------|----------|-------------|
| Symmetric $k_{ij}$, no T-dependency | `classic-CPA` | Simple symmetric |
| Symmetric $k_{ij}$, with T-dependency | `classic-CPA_T` | Temperature-dependent |
| Asymmetric $k_{ij}$ ($k_{ij} \neq k_{ji}$) | `classic-CPA_Tx` | Full asymmetric |

### 8.6 Søreide-Whitson Mixing Rule

| Type | Name | Description |
|------|------|-------------|
| 11 | `SOREIDE_WHITSON` | Salinity-dependent for sour gas/brine |

### 8.7 Setting Mixing Rules

```java
// By integer value
fluid.setMixingRule(2);

// By name
fluid.setMixingRule("classic");
fluid.setMixingRule("HV");
fluid.setMixingRule("WS");

// By enum
fluid.setMixingRule(EosMixingRuleType.CLASSIC);

// With GE model (for HV/WS)
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
fluid.setMixingRule("WS", "NRTL");
```

### 8.8 Setting Custom kij Values

```java
EosMixingRulesInterface mixRule = fluid.getPhase(0).getMixingRule();

// Set symmetric kij
mixRule.setBinaryInteractionParameter(0, 1, 0.12);

// Set asymmetric kij (for CPA)
mixRule.setBinaryInteractionParameterij(0, 1, 0.08);  // kij
mixRule.setBinaryInteractionParameterji(0, 1, 0.12);  // kji

// Set temperature-dependent kij
mixRule.setBinaryInteractionParameter(0, 1, 0.10);      // kij0
mixRule.setBinaryInteractionParameterT1(0, 1, 0.001);   // kijT
```

---

## 9. Auto-Select Model Feature

### 9.1 Overview

NeqSim provides an `autoSelectModel()` method that automatically chooses an appropriate thermodynamic model based on the components in your fluid. This is useful for quick setups or when you're unsure which model to use.

### 9.2 Auto-Selection Logic

The `autoSelectModel()` method follows this decision tree:

```java
public SystemInterface autoSelectModel() {
    if (hasComponent("MDEA") && hasComponent("water") && hasComponent("CO2")) {
        return setModel("Electrolyte-ScRK-EOS");  // Amine systems
    } 
    else if (hasComponent("water") || hasComponent("methanol") || 
             hasComponent("MEG") || hasComponent("TEG") || 
             hasComponent("ethanol") || hasComponent("DEG")) {
        if (hasComponent("Na+") || hasComponent("K+") || 
            hasComponent("Br-") || hasComponent("Mg++") || 
            hasComponent("Cl-") || hasComponent("Ca++") || 
            hasComponent("Fe++") || hasComponent("SO4--")) {
            return setModel("Electrolyte-CPA-EOS-statoil");  // Electrolytes
        } else {
            return setModel("CPAs-SRK-EOS-statoil");  // Polar/associating
        }
    }
    else if (hasComponent("water")) {
        return setModel("ScRK-EOS");  // Water present
    }
    else if (hasComponent("mercury")) {
        return setModel("SRK-TwuCoon-Statoil-EOS");  // Mercury
    }
    else {
        return setModel("SRK-EOS");  // Default: standard SRK
    }
}
```

### 9.3 Model Selection Summary

| Components Present | Selected Model |
|-------------------|----------------|
| MDEA + water + CO2 | Electrolyte-ScRK-EOS |
| Water/glycols + ions | Electrolyte-CPA-EOS-statoil |
| Water/glycols (no ions) | CPAs-SRK-EOS-statoil |
| Only water (no glycols) | ScRK-EOS |
| Mercury | SRK-TwuCoon-Statoil-EOS |
| Default (hydrocarbons only) | SRK-EOS |

### 9.4 Usage

```java
import neqsim.thermo.Fluid;

// Method 1: Using Fluid builder with autoSelectModel
Fluid fluidBuilder = new Fluid();
fluidBuilder.setAutoSelectModel(true);
SystemInterface fluid = fluidBuilder.create("black oil with water");

// Method 2: Calling autoSelectModel() on existing fluid
SystemInterface gas = new SystemSrkEos(300.0, 50.0);
gas.addComponent("methane", 0.80);
gas.addComponent("water", 0.15);
gas.addComponent("MEG", 0.05);
gas.createDatabase(true);

// Auto-select will switch to CPA model
SystemInterface optimizedFluid = gas.autoSelectModel();
```

### 9.5 Auto-Select Mixing Rule

There is also an `autoSelectMixingRule()` method that selects an appropriate mixing rule based on the model type:

```java
fluid.autoSelectMixingRule();  // Automatically sets appropriate mixing rule
```

---

## 10. Model Selection Guidelines

### 10.1 Quick Reference Table

| System Type | Recommended Model | Mixing Rule | Notes |
|-------------|-------------------|-------------|-------|
| Dry natural gas | `SystemSrkEos` | `classic` (2) | Simple, fast |
| Wet gas / condensate | `SystemPrEos` | `classic` (2) | Better for C7+ |
| Black oil with TBP | `SystemPrEos` | `classic` (2) | With characterization |
| Water-hydrocarbon | `SystemSrkCPAstatoil` | `CLASSIC_TX_CPA` (10) | Handles association |
| Glycol dehydration | `SystemSrkCPAstatoil` | `CPA_MIX` (7) or (10) | MEG, TEG, DEG |
| Sour gas with brine | `SystemSoreideWhitson` | `SOREIDE_WHITSON` (11) | Salinity-dependent |
| Amine gas treating | `SystemSrkEos` | `HV` (4) | With NRTL |
| Fiscal metering | `SystemGERG2008Eos` | N/A | ISO 20765-2 |
| CCS / CO2 transport | `SystemEOSCGEos` | N/A | Flue gas components |
| Electrolyte solutions | `SystemElectrolyteCPAstatoil` | (10) | With chemicalReactionInit |
| Polar organics | `SystemUNIFAC` | N/A | Group contribution |
| High-pressure polar | `SystemPrEos` | `WS` (5) | Wong-Sandler |

### 10.2 Decision Flow

```
1. Is high accuracy required for custody transfer?
   └─ YES → Use GERG-2008 or EOS-CG

2. Does the system contain electrolytes (Na+, Cl-, etc.)?
   └─ YES → Use Electrolyte-CPA

3. Does the system contain water, glycols, or alcohols?
   └─ YES → Use CPA models (SystemSrkCPAstatoil)

4. Is it a sour gas system with brine?
   └─ YES → Use Søreide-Whitson

5. Is it a non-ideal organic mixture?
   └─ YES → Use UNIFAC or NRTL with HV/WS mixing

6. Is it a standard hydrocarbon system?
   └─ YES → Use SRK or PR with classic mixing
```

### 10.3 Performance vs. Accuracy Trade-offs

| Model Type | Speed | Accuracy | Memory |
|------------|-------|----------|--------|
| Cubic (SRK/PR) | ⚡⚡⚡ Fast | ★★★ Good | Low |
| CPA | ⚡⚡ Medium | ★★★★ Very Good | Medium |
| GERG-2008 | ⚡ Slow | ★★★★★ Excellent | High |
| UNIFAC | ⚡⚡ Medium | ★★★ Good | Medium |
| Electrolyte-CPA | ⚡ Slow | ★★★★ Very Good | High |

---

## 11. Complete Reference Tables

### 11.1 All System Classes

| Class | Category | Description |
|-------|----------|-------------|
| `SystemSrkEos` | Cubic | Standard SRK |
| `SystemSrkPenelouxEos` | Cubic | SRK with volume correction |
| `SystemSrkMathiasCopeman` | Cubic | SRK with MC alpha function |
| `SystemSrkTwuCoonEos` | Cubic | SRK with Twu-Coon alpha |
| `SystemSrkTwuCoonParamEos` | Cubic | SRK Twu-Coon parameterized |
| `SystemSrkTwuCoonStatoilEos` | Cubic | SRK Twu-Coon Equinor version |
| `SystemSrkSchwartzentruberEos` | Cubic | SRK-Schwartzentruber |
| `SystemSrkEosvolcor` | Cubic | SRK with volume correction |
| `SystemPrEos` | Cubic | Standard PR |
| `SystemPrEos1978` | Cubic | Original 1978 PR |
| `SystemPrMathiasCopeman` | Cubic | PR with MC alpha |
| `SystemPrDanesh` | Cubic | Modified PR |
| `SystemPrEosvolcor` | Cubic | PR with volume correction |
| `SystemPrEosDelft1998` | Cubic | Delft 1998 PR |
| `SystemPrGassemEos` | Cubic | Gassem PR |
| `SystemRKEos` | Cubic | Original RK |
| `SystemTSTEos` | Cubic | Twu-Sim-Tassone |
| `SystemBWRSEos` | Cubic | Benedict-Webb-Rubin-Starling |
| `SystemCSPsrkEos` | Cubic | CSP-SRK |
| `SystemPsrkEos` | Cubic | PSRK |
| `SystemSrkCPA` | CPA | Standard SRK-CPA |
| `SystemSrkCPAs` | CPA | Alternative SRK-CPA |
| `SystemSrkCPAstatoil` | CPA | **Recommended** Equinor SRK-CPA |
| `SystemPrCPA` | CPA | PR-CPA |
| `SystemUMRCPAEoS` | CPA | UMR-CPA |
| `SystemPCSAFT` | SAFT | PC-SAFT |
| `SystemPCSAFTa` | SAFT | PC-SAFT variant |
| `SystemGERG2008Eos` | Reference | GERG-2008 (ISO 20765-2) |
| `SystemGERG2004Eos` | Reference | GERG-2004 (older version) |
| `SystemGERGwaterEos` | Reference | GERG with water |
| `SystemEOSCGEos` | Reference | EOS-CG for CCS |
| `SystemSpanWagnerEos` | Reference | Span-Wagner for CO2 |
| `SystemLeachmanEos` | Reference | Leachman for H2 |
| `SystemVegaEos` | Reference | Vega equation |
| `SystemAmmoniaEos` | Reference | Ammonia-specific |
| `SystemBnsEos` | Reference | BNS equation |
| `SystemUNIFAC` | GE | UNIFAC |
| `SystemUNIFACpsrk` | GE | UNIFAC-PSRK |
| `SystemNRTL` | GE | NRTL |
| `SystemGEWilson` | GE | Wilson |
| `SystemUMRPRUEos` | GE-EoS | UMR-PRU |
| `SystemUMRPRUMCEos` | GE-EoS | UMR-PRU-MC |
| `SystemElectrolyteCPA` | Electrolyte | Electrolyte CPA |
| `SystemElectrolyteCPAstatoil` | Electrolyte | **Recommended** Equinor Electrolyte CPA |
| `SystemElectrolyteCPAMM` | Electrolyte | Electrolyte CPA-MM |
| `SystemFurstElectrolyteEos` | Electrolyte | Fürst electrolyte |
| `SystemFurstElectrolyteEosMod2004` | Electrolyte | Modified Fürst (2004) |
| `SystemSoreideWhitson` | Electrolyte | Søreide-Whitson for sour gas |
| `SystemPitzer` | Electrolyte | Pitzer model |
| `SystemDesmukhMather` | Electrolyte | Desmukh-Mather |
| `SystemKentEisenberg` | Electrolyte | Kent-Eisenberg |
| `SystemDuanSun` | Electrolyte | Duan-Sun |
| `SystemIdealGas` | Ideal | Ideal gas law |
| `SystemWaterIF97` | Water | IF-97 for steam |

### 11.2 All Mixing Rules

| Type | Name | String Key | Best For |
|------|------|------------|----------|
| 1 | NO | `"NO"` | Ideal mixtures |
| 2 | CLASSIC | `"classic"` | General hydrocarbons |
| 3 | CLASSIC_HV | `"CLASSIC_HV"` | Polar mixtures |
| 4 | HV | `"HV"` | Alcohol-water-HC |
| 5 | WS | `"WS"` | High-pressure polar |
| 7 | CPA_MIX | `"CPA_MIX"` | CPA systems |
| 8 | CLASSIC_T | `"CLASSIC_T"` | T-dependent kij |
| 9 | CLASSIC_T_CPA | `"CLASSIC_T_CPA"` | CPA with T-dependency |
| 10 | CLASSIC_TX_CPA | `"CLASSIC_TX_CPA"` | **Recommended for CPA** |
| 11 | SOREIDE_WHITSON | `"SOREIDE_WHITSON"` | Sour gas, brine |
| 12 | CLASSIC_T2 | `"CLASSIC_T2"` | Inverse T-dependency |

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide) - Complete guide to creating fluids
- [Mixing Rules Guide](mixing_rules_guide) - Detailed mixing rule documentation
- [GERG-2008 and EOS-CG](gerg2008_eoscg) - Reference equation details
- [Electrolyte CPA Model](ElectrolyteCPAModel) - Electrolyte model documentation
- [Søreide-Whitson Model](SoreideWhitsonModel) - Gas solubility in brine, produced water emissions
- [Flash Calculations Guide](flash_calculations_guide) - Thermodynamic operations
- [Mathematical Models](mathematical_models) - Equation derivations
- [Offshore Emission Reporting](../emissions/OFFSHORE_EMISSION_REPORTING) - Emission calculations using Søreide-Whitson

---

*Last updated: February 2026*
