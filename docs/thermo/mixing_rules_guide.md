# Mixing Rules in NeqSim

This guide provides comprehensive documentation on mixing rules available in NeqSim, including mathematical formulations, usage patterns, and recommendations for different applications.

## Table of Contents
1. [Introduction to Mixing Rules](#1-introduction-to-mixing-rules)
2. [Setting Mixing Rules](#2-setting-mixing-rules)
3. [Classic Mixing Rules](#3-classic-mixing-rules)
4. [Huron-Vidal Mixing Rules](#4-huron-vidal-mixing-rules)
5. [Wong-Sandler Mixing Rule](#5-wong-sandler-mixing-rule)
6. [CPA Mixing Rules](#6-cpa-mixing-rules)
7. [Søreide-Whitson Mixing Rule](#7-søreide-whitson-mixing-rule)
8. [Binary Interaction Parameters (kij)](#8-binary-interaction-parameters-kij)
9. [Complete Reference Table](#9-complete-reference-table)
10. [Examples by Application](#10-examples-by-application)

---

## 1. Introduction to Mixing Rules

Mixing rules determine how pure-component parameters from an equation of state are combined to calculate mixture properties. For cubic equations of state like SRK and PR, the key parameters are:

- **a**: Energy parameter (attraction term)
- **b**: Co-volume parameter (repulsion term)

The general form of cubic EoS mixing rules is:

$$
a_{mix} = \sum_i \sum_j x_i x_j a_{ij}
$$

$$
b_{mix} = \sum_i x_i b_i
$$

Where the cross-parameter $a_{ij}$ is typically calculated using a combining rule:

$$
a_{ij} = \sqrt{a_i a_j}(1 - k_{ij})
$$

The binary interaction parameter $k_{ij}$ is crucial for accurate phase equilibrium predictions.

---

## 2. Setting Mixing Rules

NeqSim provides three ways to set mixing rules:

### 2.1 By Integer Value

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("CO2", 0.2);
fluid.setMixingRule(2);  // Classic mixing rule
```

### 2.2 By Name (String)

```java
fluid.setMixingRule("classic");
fluid.setMixingRule("HV");
fluid.setMixingRule("WS");
```

### 2.3 By Enum Type

```java
import neqsim.thermo.mixingrule.EosMixingRuleType;

fluid.setMixingRule(EosMixingRuleType.CLASSIC);
fluid.setMixingRule(EosMixingRuleType.byName("classic"));
fluid.setMixingRule(EosMixingRuleType.byValue(2));
```

### 2.4 With GE Model (for Huron-Vidal/Wong-Sandler)

```java
// Huron-Vidal with UNIFAC
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

// Wong-Sandler with NRTL
fluid.setMixingRule("WS", "NRTL");
```

---

## 3. Classic Mixing Rules

### 3.1 NO (kij = 0) - Type 1

The simplest mixing rule with no binary interactions. All $k_{ij}$ values are set to zero.

$$
a_{ij} = \sqrt{a_i a_j}
$$

**Use case:** Quick calculations, ideal mixture approximations, or when no interaction parameters are available.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("ethane", 0.3);
fluid.setMixingRule(1);  // or fluid.setMixingRule("NO");
```

### 3.2 CLASSIC - Type 2

The standard van der Waals one-fluid mixing rule with binary interaction parameters from the NeqSim database.

$$
a_{mix} = \sum_i \sum_j x_i x_j \sqrt{a_i a_j}(1 - k_{ij})
$$

$$
b_{mix} = \sum_i x_i b_i
$$

**Use case:** General hydrocarbon systems, natural gas processing, most industrial applications.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("nitrogen", 0.05);
fluid.setMixingRule(2);  // or fluid.setMixingRule("classic");
```

### 3.3 CLASSIC_T - Type 8

Classic mixing rule with temperature-dependent binary interaction parameters:

$$
k_{ij}(T) = k_{ij,0} + k_{ij,T} \cdot T
$$

Where $k_{ij,0}$ is the reference value and $k_{ij,T}$ is the temperature coefficient.

**Use case:** Systems where kij varies significantly with temperature.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("H2S", 0.2);
fluid.setMixingRule(8);  // Temperature-dependent classic
```

### 3.4 CLASSIC_T2 - Type 12

Alternative temperature-dependent formulation:

$$
k_{ij}(T) = k_{ij,0} + \frac{k_{ij,T}}{T}
$$

**Use case:** Systems requiring inverse temperature dependency.

```java
fluid.setMixingRule(12);
```

---

## 4. Huron-Vidal Mixing Rules

Huron-Vidal (HV) mixing rules combine cubic EoS with activity coefficient models (like NRTL or UNIFAC) for improved liquid-phase behavior.

### 4.1 Mathematical Framework

The excess Gibbs energy from an activity coefficient model is incorporated into the EoS:

$$
a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} - \frac{G^E}{\Lambda} \right)
$$

Where:
- $G^E$ is the excess Gibbs energy from the activity coefficient model
- $\Lambda$ is the EoS-specific constant (≈ 0.693 for SRK)

### 4.2 CLASSIC_HV - Type 3

Basic Huron-Vidal mixing rule with NRTL parameters from the database.

```java
SystemInterface fluid = new SystemSrkEos(350.0, 10.0);
fluid.addComponent("methanol", 0.3);
fluid.addComponent("water", 0.4);
fluid.addComponent("methane", 0.3);
fluid.setMixingRule(3);  // or fluid.setMixingRule("CLASSIC_HV");
```

### 4.3 HV - Type 4

Enhanced Huron-Vidal with temperature-dependent parameters (HVDijT):

$$
D_{ij}(T) = D_{ij,0} + D_{ij,T} \cdot T
$$

**Use case:** Polar/non-polar mixtures, alcohol-water-hydrocarbon systems.

```java
SystemInterface fluid = new SystemPrEos(320.0, 20.0);
fluid.addComponent("ethanol", 0.2);
fluid.addComponent("water", 0.5);
fluid.addComponent("propane", 0.3);
fluid.setMixingRule(4);  // or fluid.setMixingRule("HV");
```

### 4.4 Huron-Vidal with UNIFAC

For predictive calculations without fitted parameters, use UNIFAC:

```java
SystemInterface fluid = new SystemSrkEos(320.0, 15.0);
fluid.addComponent("ethanol", 0.3);
fluid.addComponent("n-hexane", 0.4);
fluid.addComponent("water", 0.3);

// HV with UNIFAC activity coefficients
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
```

Available GE models for HV:
- `"NRTL"` - Non-Random Two-Liquid (default)
- `"UNIFAC_UMRPRU"` - UNIFAC with UMR-PRU parameters
- `"UNIFAC"` - Standard UNIFAC
- `"UNIFAC_PSRK"` - UNIFAC with PSRK parameters

### 4.5 Accessing HV Parameters

```java
// Get the mixing rule interface
HVMixingRulesInterface hvRule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();

// Get/Set HV parameters
double dij = hvRule.getHVDijParameter(0, 1);
hvRule.setHVDijParameter(0, 1, 500.0);  // Set Dij in K

double alpha = hvRule.getHValphaParameter(0, 1);
hvRule.setHValphaParameter(0, 1, 0.3);  // Set alpha (non-randomness)
```

---

## 5. Wong-Sandler Mixing Rule

### 5.1 Overview

The Wong-Sandler (WS) mixing rule provides theoretically correct behavior at both low and high densities by matching:
- The second virial coefficient of the mixture
- The excess Gibbs energy at infinite pressure

### 5.2 Mathematical Formulation

$$
b_{mix} = \frac{\sum_i \sum_j x_i x_j \left( b - \frac{a}{RT} \right)_{ij}}{1 - \frac{A_\infty^E}{CRT} - \sum_i x_i \frac{a_i}{RTb_i}}
$$

$$
a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} + \frac{A_\infty^E}{C} \right)
$$

Where $C$ is an EoS-specific constant and $A_\infty^E$ is the excess Helmholtz energy at infinite pressure.

### 5.3 WS - Type 5

```java
SystemInterface fluid = new SystemPrEos(350.0, 30.0);
fluid.addComponent("methanol", 0.2);
fluid.addComponent("water", 0.5);
fluid.addComponent("CO2", 0.3);
fluid.setMixingRule(5);  // or fluid.setMixingRule("WS");
```

### 5.4 Wong-Sandler with NRTL

```java
SystemInterface fluid = new SystemSrkEos(320.0, 20.0);
fluid.addComponent("acetone", 0.3);
fluid.addComponent("water", 0.4);
fluid.addComponent("methane", 0.3);

// WS with NRTL activity coefficients
fluid.setMixingRule("WS", "NRTL");
```

### 5.5 Accessing WS Parameters

```java
HVMixingRulesInterface wsRule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();

// Get/Set Wong-Sandler kij parameter
double kijWS = wsRule.getKijWongSandler(0, 1);
wsRule.setKijWongSandler(0, 1, 0.1);
```

**Use case:** High-pressure VLE, polar/non-polar mixtures, CO2 capture systems.

---

## 6. CPA Mixing Rules

For Cubic-Plus-Association (CPA) equations of state, specialized mixing rules handle both the cubic EoS part and the association term.

### 6.1 CPA_MIX - Type 7

Classic mixing rule with CPA-specific binary interaction parameters from the database.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(320.0, 50.0);
fluid.addComponent("water", 0.1);
fluid.addComponent("methane", 0.8);
fluid.addComponent("MEG", 0.1);
fluid.setMixingRule(7);  // CPA classic mixing
```

### 6.2 CLASSIC_T_CPA - Type 9

Temperature-dependent classic mixing rule for CPA systems:

```java
fluid.setMixingRule(9);
```

### 6.3 CLASSIC_TX_CPA - Type 10

Temperature and composition dependent mixing rule for CPA. This is the **recommended mixing rule for CPA systems** as it:
- Uses asymmetric kij parameters (kij ≠ kji)
- Includes temperature dependency
- Automatically selects the appropriate sub-type based on parameter symmetry

```java
SystemInterface fluid = new SystemSrkCPAstatoil(330.0, 100.0);
fluid.addComponent("water", 0.15);
fluid.addComponent("methane", 0.70);
fluid.addComponent("MEG", 0.10);
fluid.addComponent("CO2", 0.05);
fluid.setMixingRule(10);  // Recommended for CPA
```

### 6.4 Cross-Association Parameters

CPA mixing rules also handle association site interactions between different molecules:

| Association Scheme | Description | Examples |
|-------------------|-------------|----------|
| CR-1 | Cross-association with single site | Water-MEG |
| ER | Elliott combining rule | General polar systems |
| 4C | Four-site model | Water, glycols |
| 2B | Two-site model | Alcohols |

---

## 7. Søreide-Whitson Mixing Rule

### 7.1 Overview

The Søreide-Whitson mixing rule is specifically designed for systems containing:
- Sour gases (CO2, H2S, N2)
- Hydrocarbons
- Aqueous brine solutions

It uses composition and salinity-dependent binary interaction parameters.

### 7.2 SOREIDE_WHITSON - Type 11

```java
import neqsim.thermo.system.SystemSoreideWhitson;

SystemSoreideWhitson fluid = new SystemSoreideWhitson(350.0, 200.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("CO2", 0.15);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("water", 0.10);

// Add salinity (important for sour gas solubility)
fluid.addSalinity(2.0, "mole/sec");

fluid.setMixingRule(11);  // Søreide-Whitson mixing rule
```

### 7.3 Salinity-Dependent kij

The mixing rule calculates kij for water-gas interactions based on salinity (S in mol/kg):

**For CO2-water:**
$$
k_{ij} = -0.31092(1 + 0.156 S^{0.75}) + 0.236(1 + 0.178 S^{0.98}) T_r - 21.26 e^{-6.72^{T_r} - S}
$$

**For N2-water:**
$$
k_{ij} = -1.702(1 + 0.026 S^{0.75}) + 0.443(1 + 0.081 S^{0.75}) T_r
$$

**For hydrocarbons-water:**
$$
k_{ij} = (1 + a_0 S) A_0 + (1 + a_1 S) A_1 T_r + (1 + a_2 S) A_2 T_r^2
$$

Where $T_r$ is the reduced temperature and the $A$ and $a$ coefficients depend on the acentric factor.

**Use case:** Reservoir fluids with aqueous phases, sour gas processing, CCS with brine.

---

## 8. Binary Interaction Parameters (kij)

### 8.1 Default Database Values

NeqSim loads kij values from its internal database when you call `setMixingRule()`. These are stored in the `inter` table.

### 8.2 Setting Custom kij Values

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("CO2", 0.1);
fluid.setMixingRule("classic");

// Get the mixing rule interface
EosMixingRulesInterface mixRule = fluid.getPhase(0).getMixingRule();

// Set custom kij (symmetric: kij = kji)
mixRule.setBinaryInteractionParameter(0, 1, 0.12);

// Get current kij value
double kij = mixRule.getBinaryInteractionParameter(0, 1);
System.out.println("kij(CH4-CO2) = " + kij);
```

### 8.3 Setting Asymmetric kij (for CPA)

```java
// For CPA systems with asymmetric parameters
mixRule.setBinaryInteractionParameterij(0, 1, 0.08);  // kij
mixRule.setBinaryInteractionParameterji(0, 1, 0.12);  // kji
```

### 8.4 Setting Temperature-Dependent kij

```java
// For mixing rules 8, 9, 12
// kij(T) = kij0 + kijT * f(T)
mixRule.setBinaryInteractionParameter(0, 1, 0.10);    // kij0
mixRule.setBinaryInteractionParameterT1(0, 1, 0.001); // kijT
```

### 8.5 Displaying kij Matrix

```java
double[][] kijMatrix = mixRule.getBinaryInteractionParameters();
for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
        System.out.printf("kij[%d][%d] = %.4f  ", i, j, kijMatrix[i][j]);
    }
    System.out.println();
}
```

---

## 9. Complete Reference Table

| Type | Name | String Key | Description | Best For |
|------|------|------------|-------------|----------|
| 1 | `NO` | `"NO"` | All kij = 0 | Quick estimates, ideal mixtures |
| 2 | `CLASSIC` | `"classic"` | Classic van der Waals with database kij | General hydrocarbons |
| 3 | `CLASSIC_HV` | `"CLASSIC_HV"` | Huron-Vidal with database parameters | Polar mixtures |
| 4 | `HV` | `"HV"` | Huron-Vidal with T-dependent parameters | Alcohol-water-HC |
| 5 | `WS` | `"WS"` | Wong-Sandler | High-pressure polar systems |
| 7 | `CPA_MIX` | `"CPA_MIX"` | Classic for CPA systems | Water-HC with CPA |
| 8 | `CLASSIC_T` | `"CLASSIC_T"` | T-dependent kij (linear) | Temperature-sensitive kij |
| 9 | `CLASSIC_T_CPA` | `"CLASSIC_T_CPA"` | T-dependent for CPA | CPA with T-dependency |
| 10 | `CLASSIC_TX_CPA` | `"CLASSIC_TX_CPA"` | T and x dependent for CPA | **Recommended for CPA** |
| 11 | `SOREIDE_WHITSON` | `"SOREIDE_WHITSON"` | Salinity-dependent | Sour gas, brine systems |
| 12 | `CLASSIC_T2` | `"CLASSIC_T2"` | T-dependent (inverse) | Alternative T-dependency |

---

## 10. Examples by Application

### 10.1 Natural Gas Processing

```java
SystemInterface gas = new SystemSrkEos(280.0, 70.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.03);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.04);
gas.setMixingRule("classic");  // Type 2

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();
```

### 10.2 Glycol Dehydration (CPA)

```java
SystemInterface fluid = new SystemSrkCPAstatoil(320.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("water", 0.05);
fluid.addComponent("TEG", 0.15);  // Triethylene glycol
fluid.setMixingRule(10);  // CLASSIC_TX_CPA - recommended for CPA

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.3 Amine Gas Treating

```java
SystemInterface fluid = new SystemSrkEos(323.0, 20.0);
fluid.addComponent("CO2", 0.15);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("methane", 0.60);
fluid.addComponent("MDEA", 0.10);  // Methyldiethanolamine
fluid.addComponent("water", 0.10);
fluid.setMixingRule("HV", "NRTL");  // Huron-Vidal with NRTL

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.4 CO2 Capture and Storage

```java
SystemInterface fluid = new SystemPrEos(350.0, 150.0);
fluid.addComponent("CO2", 0.90);
fluid.addComponent("nitrogen", 0.05);
fluid.addComponent("oxygen", 0.02);
fluid.addComponent("water", 0.03);
fluid.setMixingRule("WS");  // Wong-Sandler for high pressure

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.5 Sour Gas with Brine

```java
SystemSoreideWhitson fluid = new SystemSoreideWhitson(380.0, 250.0);
fluid.addComponent("methane", 0.65);
fluid.addComponent("CO2", 0.20);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("water", 0.10);
fluid.addSalinity("NaCl", 3.0, "mole/sec");  // Add NaCl salinity
fluid.setMixingRule(11);  // Søreide-Whitson

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());
fluid.prettyPrint();
```

### 10.6 Pharmaceutical/Chemical (UNIFAC Predictive)

```java
SystemInterface fluid = new SystemSrkEos(298.0, 1.0);
fluid.addComponent("ethanol", 0.3);
fluid.addComponent("acetone", 0.3);
fluid.addComponent("water", 0.4);
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");  // Predictive UNIFAC

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide.md) - Complete guide to creating fluids
- [Mathematical Models](mathematical_models.md) - EoS equations and derivations
- [Thermodynamic Workflows](thermodynamic_workflows.md) - Flash calculations and operations
- [PVT and Fluid Characterization](pvt_fluid_characterization.md) - Heavy fraction handling
