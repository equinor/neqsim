---
title: "INTER Table: Binary Interaction Coefficients Database"
description: "This guide documents the INTER table in NeqSim, which contains binary interaction parameters (BIPs) for thermodynamic models including equations of state (EoS), activity coefficient models, and CPA as..."
---

# INTER Table: Binary Interaction Coefficients Database

This guide documents the INTER table in NeqSim, which contains binary interaction parameters (BIPs) for thermodynamic models including equations of state (EoS), activity coefficient models, and CPA association parameters.

## Table of Contents
- [Overview](#overview)
- [Table Structure](#table-structure)
- [Column Reference](#column-reference)
  - [Component Pair Identification](#component-pair-identification)
  - [EoS Interaction Parameters](#eos-interaction-parameters)
  - [Huron-Vidal Parameters](#huron-vidal-parameters)
  - [Wong-Sandler Parameters](#wong-sandler-parameters)
  - [NRTL Parameters](#nrtl-parameters)
  - [CPA Association Parameters](#cpa-association-parameters)
- [Relation to Mixing Rules](#relation-to-mixing-rules)
- [Usage Examples](#usage-examples)
- [Setting Custom Parameters](#setting-custom-parameters)
- [TBP Fractions and Default Values](#tbp-fractions-and-default-values)

---

## Overview

The INTER table is the central repository for all binary interaction parameters in NeqSim. It stores parameters for:

1. **Cubic EoS** - Classical $k_{ij}$ values for SRK and PR
2. **CPA EoS** - Association and $k_{ij}$ for CPA model
3. **Huron-Vidal** - $\alpha$, $g_{ij}$, $g_{ji}$ parameters
4. **Wong-Sandler** - Integration with UNIFAC
5. **NRTL** - $\alpha$, $g_{ij}$, $g_{ji}$ for activity coefficients
6. **Soreide-Whitson** - Oil-water systems
7. **Desmukh-Mather** - Amine systems

**Location:** `src/main/resources/data/INTER.csv`

**Database table name:** `INTER` (or `INTERTEMP` for temporary tables)

---

## Table Structure

The INTER table contains one row per component pair. Each row has parameters for multiple thermodynamic models, allowing the same fluid definition to be used with different mixing rules.

**Example entry:**
```csv
ID,COMP1,COMP2,HVTYPE,KIJSRK,KIJTSRK,KIJTType,KIJPR,KIJTPR,KIJPCSAFT,...
6950,CO2,methane,Classic,0.0973,0,0,0.0973,0,...
```

---

## Column Reference

### Component Pair Identification

| Column | Type | Description |
|--------|------|-------------|
| `ID` | Integer | Unique row identifier |
| `COMP1` | String | First component name (must match COMP table) |
| `COMP2` | String | Second component name (must match COMP table) |

**Important:** Component pairs are symmetric. NeqSim searches for both `(COMP1=A, COMP2=B)` and `(COMP1=B, COMP2=A)`.

---

### EoS Interaction Parameters

These parameters are used in the classical van der Waals mixing rules for cubic equations of state.

| Column | Type | Description | Used By |
|--------|------|-------------|---------|
| `KIJSRK` | Double | Binary interaction parameter for SRK EoS | `SystemSrkEos` |
| `KIJTSRK` | Double | Temperature correction to $k_{ij}$ for SRK | SRK with T-dependent kij |
| `KIJTType` | Integer | Type of temperature dependence (0, 1, 2) | All EoS |
| `KIJPR` | Double | Binary interaction parameter for PR EoS | `SystemPrEos` |
| `KIJTPR` | Double | Temperature correction to $k_{ij}$ for PR | PR with T-dependent kij |
| `KIJPCSAFT` | Double | Binary interaction for PC-SAFT EoS | `SystemPCSAFT` |

**Temperature dependence types (`KIJTType`):**
- `0` - No temperature dependence: $k_{ij}(T) = k_{ij}$
- `1` - Inverse temperature: $k_{ij}(T) = k_{ij} + k_{ij}^T / T$
- `2` - Linear temperature: $k_{ij}(T) = k_{ij} + k_{ij}^T \cdot T$

**Mathematical role:**

The $k_{ij}$ parameter appears in the classical mixing rule for the EoS attractive parameter:

$$a_{mix} = \sum_i \sum_j x_i x_j \sqrt{a_i a_j} (1 - k_{ij})$$

A positive $k_{ij}$ reduces the attractive interactions between unlike molecules.

---

### Huron-Vidal Parameters

Huron-Vidal mixing rules combine an EoS with an activity coefficient model.

| Column | Type | Description |
|--------|------|-------------|
| `HVTYPE` | String | Type of mixing rule (`"Classic"` or `"HV"`) |
| `HVALPHA` | Double | NRTL non-randomness parameter ($\alpha$) |
| `HVGIJ` | Double | NRTL interaction parameter $g_{ij}$ (J/mol) |
| `HVGJI` | Double | NRTL interaction parameter $g_{ji}$ (J/mol) |
| `HVGIJT` | Double | Temperature derivative of $g_{ij}$ |
| `HVGJIT` | Double | Temperature derivative of $g_{ji}$ |

**Note:** When `HVTYPE = "Classic"`, the Huron-Vidal parameters are not used.

**Mathematical formulation:**

$$\tau_{ij} = \frac{g_{ij} - g_{jj}}{RT} = \frac{\Delta g_{ij}}{RT}$$

$$G_{ij} = \exp(-\alpha_{ij} \tau_{ij})$$

---

### Wong-Sandler Parameters

Wong-Sandler mixing rules provide thermodynamic consistency between high and low pressure limits.

| Column | Type | Description |
|--------|------|-------------|
| `WSTYPE` | String | Type (`"Classic"` or `"WS"`) |
| `KIJWS` | Double | Wong-Sandler interaction parameter |
| `KIJWSunifac` | Double | WS parameter for UNIFAC integration |
| `CalcWij` | Integer | Flag for calculated vs fitted W parameters |
| `W1`, `W2`, `W3` | Double | Second virial coefficient parameters |
| `WSGIJT` | Double | Temperature derivative for WS $g_{ij}$ |
| `WSGJIT` | Double | Temperature derivative for WS $g_{ji}$ |

---

### NRTL Parameters

Non-Random Two-Liquid parameters for activity coefficient calculations.

| Column | Type | Description |
|--------|------|-------------|
| `NRTLALPHA` | Double | Non-randomness parameter $\alpha_{ij}$ (typically 0.2-0.47) |
| `NRTLGIJ` | Double | Interaction parameter $g_{ij}$ (J/mol) |
| `NRTLGJI` | Double | Interaction parameter $g_{ji}$ (J/mol) |

**NRTL Activity Coefficient:**

$$\ln \gamma_i = \frac{\sum_j x_j \tau_{ji} G_{ji}}{\sum_k x_k G_{ki}} + \sum_j \frac{x_j G_{ij}}{\sum_k x_k G_{kj}} \left( \tau_{ij} - \frac{\sum_m x_m \tau_{mj} G_{mj}}{\sum_k x_k G_{kj}} \right)$$

---

### CPA Association Parameters

These parameters control cross-association in the CPA (Cubic Plus Association) equation of state.

| Column | Type | Description |
|--------|------|-------------|
| `cpakij_SRK` | Double | SRK-CPA binary interaction parameter |
| `cpakijT_SRK` | Double | Temperature correction for CPA $k_{ij}$ |
| `cpakijx_SRK` | Double | Composition-dependent $k_{ij}$ (asymmetric) |
| `cpakjix_SRK` | Double | Composition-dependent $k_{ji}$ (asymmetric) |
| `cpakij_PR` | Double | PR-CPA binary interaction parameter |
| `cpaAssosiationType` | Integer | Association scheme type (0, 1, 2, ...) |
| `cpaBetaCross` | Double | Cross-association volume parameter $\beta^{AB}$ |
| `cpaEpsCross` | Double | Cross-association energy parameter $\epsilon^{AB}$ (K) |

**Association scheme types:**
- `0` - No induced association
- `1` - CR1 combining rule (Elliott rule)
- `2` - CR2 combining rule (geometric mean)
- Other values - Custom fitted parameters

**Cross-association combining rules:**

When `cpaAssosiationType = 1` (CR1/Elliott rule):
$$\epsilon^{AB}_{ij} = \frac{\epsilon^{AA}_{ii} + \epsilon^{BB}_{jj}}{2}$$
$$\beta^{AB}_{ij} = \sqrt{\beta^{AA}_{ii} \beta^{BB}_{jj}}$$

When explicit values are provided in `cpaBetaCross` and `cpaEpsCross`, they override the combining rules.

---

### Physical Property Parameters

| Column | Type | Description |
|--------|------|-------------|
| `GIJVISC` | Double | Viscosity mixing parameter |
| `KIJWhitsonSoriede` | Double | Soreide-Whitson correlation parameter |

---

### Desmukh-Mather Parameters

For amine-gas systems with the Desmukh-Mather model.

| Column | Type | Description |
|--------|------|-------------|
| `aijDesMath` | Double | Desmukh-Mather $a_{ij}$ parameter |
| `bijDesMath` | Double | Desmukh-Mather $b_{ij}$ parameter |

---

## Relation to Mixing Rules

The INTER table parameters are loaded when you call `setMixingRule()`. The mixing rule number determines which columns are read:

| Mixing Rule | Number | Parameters Used |
|-------------|--------|-----------------|
| Classic (kij=0) | 1 | None (all kij set to 0) |
| Classic (from database) | 2 | `KIJSRK`/`KIJPR` |
| Classic + T-dependent | 3 | `KIJSRK`, `KIJTSRK`, `KIJTType` |
| Huron-Vidal | 4 | `HVALPHA`, `HVGIJ`, `HVGJI` |
| Wong-Sandler | 5 | `KIJWS`, `W1-W3`, NRTL params |
| CPA | 7 | `cpakij_SRK`, `cpaBetaCross`, `cpaEpsCross` |
| CPA + T-dependent | 9 | + `cpakijT_SRK` |
| CPA + composition-dependent | 10 | + `cpakijx_SRK`, `cpakjix_SRK` |

**Example:**
```java
// Classic with database kij
fluid.setMixingRule(2);  // or fluid.setMixingRule("classic");

// Uses KIJSRK/KIJPR columns from INTER table
```

---

## Usage Examples

### Accessing Binary Interaction Parameters

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.phase.PhaseEos;

SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("CO2", 0.2);
fluid.setMixingRule("classic");  // Loads from INTER table

// Get kij value
PhaseEos phase = (PhaseEos) fluid.getPhase(0);
double kij = phase.getMixingRule().getBinaryInteractionParameter(0, 1);
System.out.println("kij(methane-CO2) = " + kij);  // 0.0973
```

### Setting Custom Binary Parameters

```java
// Method 1: Using component names (recommended)
fluid.setBinaryInteractionParameter("methane", "CO2", 0.10);

// Method 2: Using component indices
((PhaseEos) fluid.getPhase(0)).getMixingRule()
    .setBinaryInteractionParameter(0, 1, 0.10);
((PhaseEos) fluid.getPhase(1)).getMixingRule()
    .setBinaryInteractionParameter(0, 1, 0.10);
```

### Setting Temperature-Dependent Parameters

```java
// Set temperature coefficient
((PhaseEos) fluid.getPhase(0)).getMixingRule()
    .setBinaryInteractionParameterT1(0, 1, -0.001);

// kij(T) = kij + kijT * T  (when KIJTType=2)
```

### Getting All Binary Parameters

```java
double[][] kijMatrix = ((PhaseEos) fluid.getPhase(0))
    .getMixingRule().getBinaryInteractionParameters();

// Print matrix
for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
        System.out.print(kijMatrix[i][j] + " ");
    }
    System.out.println();
}
```

### CPA Cross-Association Example

```java
SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("water", 0.2);
fluid.addComponent("MEG", 0.1);
fluid.setMixingRule(10);  // CPA with composition-dependent kij

// Cross-association parameters are loaded automatically:
// - cpaBetaCross for methane-water
// - cpaEpsCross for water-MEG association
```

---

## Setting Custom Parameters

### Adding New Component Pairs

To add interaction parameters for a new component pair:

1. **Add to INTER.csv:**
```csv
99999,newcomp1,newcomp2,Classic,0.05,0,0,0.05,0,0,0,0,0,0,0,0,0,Classic,0.5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0.05
```

2. **Or set programmatically:**
```java
fluid.setBinaryInteractionParameter("newcomp1", "newcomp2", 0.05);
```

### Asymmetric Parameters

Some mixing rules support asymmetric $k_{ij} \neq k_{ji}$:

```java
// Set kij (component i with j)
((PhaseEos) fluid.getPhase(0)).getMixingRule()
    .setBinaryInteractionParameterij(i, j, 0.05);

// Set kji (component j with i)
((PhaseEos) fluid.getPhase(0)).getMixingRule()
    .setBinaryInteractionParameterji(i, j, 0.03);
```

---

## TBP Fractions and Default Values

For undefined pseudo-components (TBP fractions), NeqSim uses correlations or default values:

### Default kij Values for TBP Fractions

| Component | TBP Fraction | Default $k_{ij}$ |
|-----------|--------------|------------------|
| CO2 | All | 0.10 |
| N2 | All | 0.08 |
| H2O | All | 0.20 |
| MEG | All | 0.20 |

### Calculated Parameters

When `setCalcEOSInteractionParameters(true)` is called, NeqSim calculates $k_{ij}$ from critical volumes:

$$k_{ij} = 1 - \left( \frac{2 \sqrt[3]{V_{c,i} V_{c,j}}}{V_{c,i}^{1/3} + V_{c,j}^{1/3}} \right)^n$$

where $n$ is configurable (default = 6).

---

## Column Summary Table

| Category | Columns |
|----------|---------|
| **Identification** | ID, COMP1, COMP2 |
| **SRK EoS** | KIJSRK, KIJTSRK |
| **PR EoS** | KIJPR, KIJTPR |
| **PC-SAFT** | KIJPCSAFT |
| **Temperature Type** | KIJTType |
| **Huron-Vidal** | HVTYPE, HVALPHA, HVGIJ, HVGJI, HVGIJT, HVGJIT |
| **Wong-Sandler** | WSTYPE, KIJWS, KIJWSunifac, CalcWij, W1, W2, W3, WSGIJT, WSGJIT |
| **NRTL** | NRTLALPHA, NRTLGIJ, NRTLGJI |
| **CPA** | cpakij_SRK, cpakijT_SRK, cpakijx_SRK, cpakjix_SRK, cpakij_PR, cpaAssosiationType, cpaBetaCross, cpaEpsCross |
| **Physical Properties** | GIJVISC |
| **Soreide-Whitson** | KIJWhitsonSoriede |
| **Desmukh-Mather** | aijDesMath, bijDesMath |

---

## Typical Parameter Values

### Common $k_{ij}$ Values (SRK/PR)

| Pair | $k_{ij}$ | Notes |
|------|----------|-------|
| CH4 - C2H6 | 0.003 | Very similar molecules |
| CH4 - CO2 | 0.097 | Significant non-ideality |
| CH4 - H2S | 0.08 | Acid gas |
| CH4 - N2 | 0.032 | Typical |
| CH4 - H2O | 0.45-0.65 | Polar-nonpolar (CPA: ~-0.08) |
| CO2 - H2S | 0.10-0.12 | Acid gas pair |
| CO2 - C3H8 | 0.12-0.14 | |
| H2O - MEG | 0.13 | CPA model |

### CPA Association Parameters

| Pair | $\beta^{AB}$ | $\epsilon^{AB}$ (K) |
|------|--------------|---------------------|
| H2O - MEG | 0.055 | 2000-2500 |
| H2O - methanol | 0.039 | 2000-2500 |
| CO2 - H2O | 0.085 | 0 (solvation) |

---

## References

1. Soave, G. (1972). Equilibrium Constants from a Modified Redlich-Kwong Equation of State. Chem. Eng. Sci.
2. Peng, D.Y., Robinson, D.B. (1976). A New Two-Constant Equation of State. Ind. Eng. Chem. Fundam.
3. Huron, M.J., Vidal, J. (1979). New Mixing Rules in Simple Equations of State. Fluid Phase Equilib.
4. Wong, D.S.H., Sandler, S.I. (1992). A Theoretically Correct Mixing Rule. AIChE J.
5. Kontogeorgis, G.M., et al. (2006). Multicomponent Phase Equilibrium Calculations for Water-Methanol-Alkane Mixtures. Fluid Phase Equilib.
6. Soreide, I., Whitson, C.H. (1992). Peng-Robinson Predictions for Hydrocarbons, CO2, N2, and H2S with Pure Water and NaCl Brine. Fluid Phase Equilib.
