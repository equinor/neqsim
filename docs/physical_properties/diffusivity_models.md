---
title: Diffusivity Models
description: "Diffusion coefficient calculation methods in NeqSim: Chapman-Enskog, Wilke-Lee, Fuller-Schettler-Giddings for gases; Siddiqi-Lucas, Wilke-Chang, Tyn-Calus, Hayduk-Minhas for liquids. Includes validation against experimental data."
---

# Diffusivity Models

This guide documents the diffusion coefficient calculation methods available in NeqSim for gas and liquid systems.

## Table of Contents
- [Overview](#overview)
- [Types of Diffusion Coefficients](#types-of-diffusion-coefficients)
- [Gas-Phase Models](#gas-phase-models)
  - [Chapman-Enskog (Default)](#chapman-enskog-default)
  - [Wilke-Lee](#wilke-lee)
  - [Fuller-Schettler-Giddings](#fuller-schettler-giddings)
- [Liquid-Phase Models](#liquid-phase-models)
  - [Siddiqi-Lucas](#siddiqi-lucas)
  - [Wilke-Chang](#wilke-chang)
  - [Tyn-Calus](#tyn-calus)
  - [Hayduk-Minhas](#hayduk-minhas)
- [General Models](#general-models)
  - [Corresponding States (CSP)](#corresponding-states-csp)
  - [High Pressure](#high-pressure)
  - [Amine Diffusivity](#amine-diffusivity)
- [Lennard-Jones Parameters](#lennard-jones-parameters)
- [Validation Against Experimental Data](#validation-against-experimental-data)
- [Model Selection Guide](#model-selection-guide)
- [Usage Examples](#usage-examples)
- [Physical Background](#physical-background)
- [References](#references)

---

## Overview

Diffusion coefficients describe the rate of molecular transport due to concentration gradients. They are essential for:
- Mass transfer calculations
- Absorption/desorption modeling
- Reaction kinetics in multiphase systems
- Pipeline corrosion modeling

**Units:**
- Default output: **m²/s**

**Available model names** (for `setDiffusionCoefficientModel`):

| Model Name | Phase | Class |
|---|---|---|
| `"Chapman-Enskog"` | Gas | `Diffusivity` |
| `"Wilke Lee"` | Gas | `WilkeLeeDiffusivity` |
| `"Fuller-Schettler-Giddings"` | Gas | `FullerSchettlerGiddingsDiffusivity` |
| `"Siddiqi Lucas"` | Liquid | `SiddiqiLucasMethod` |
| `"Wilke-Chang"` | Liquid | `WilkeChangDiffusivity` |
| `"Tyn-Calus"` | Liquid | `TynCalusDiffusivity` |
| `"Hayduk-Minhas"` | Liquid | `HaydukMinhasDiffusivity` |
| `"CSP"` | Gas/Liquid | `CorrespondingStatesDiffusivity` |
| `"High Pressure"` | Liquid | `HighPressureDiffusivity` |
| `"Alkanol amine"` | Aqueous | `AmineDiffusivity` |

**Setting a diffusivity model:**
```java
fluid.initPhysicalProperties();
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
fluid.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
```

---

## Types of Diffusion Coefficients

### Binary Diffusion Coefficient ($D_{ij}$)

The diffusion coefficient for species $i$ moving through species $j$ at infinite dilution.

### Effective Diffusion Coefficient ($D_i^{eff}$)

The effective diffusivity of species $i$ in a multicomponent mixture (Wilke approximation):

$$D_i^{eff} = \frac{1 - x_i}{\sum_{j \neq i} \frac{x_j}{D_{ij}}}$$

### Maxwell-Stefan Diffusion Coefficients

The fundamental diffusion coefficients describing molecular interactions, related to Fick diffusion through thermodynamic factors.

---

## Gas-Phase Models

### Chapman-Enskog (Default)

The default gas-phase model based on rigorous Chapman-Enskog kinetic theory.

**Class:** `neqsim.physicalproperties.methods.gasphysicalproperties.diffusivity.Diffusivity`

**Equation:**

$$D_{ij} = \frac{0.00266 \, T^{3/2}}{P \sqrt{M_{ij}} \, \sigma_{ij}^2 \, \Omega_D}$$

where:
- $T$ is temperature (K)
- $P$ is pressure (bar)
- $M_{ij} = 2 \left(\frac{1}{M_i} + \frac{1}{M_j}\right)^{-1}$ is the reduced molar mass (g/mol)
- $\sigma_{ij} = (\sigma_i + \sigma_j)/2$ is the collision diameter (Å)
- $\Omega_D$ is the diffusion collision integral, a function of reduced temperature $T^* = k_B T / \epsilon_{ij}$

**Collision integral:**

$$\Omega_D = \frac{1.06036}{(T^*)^{0.15610}} + \frac{0.19300}{\exp(0.47635 \, T^*)} + \frac{1.03587}{\exp(1.52996 \, T^*)} + \frac{1.76474}{\exp(3.89411 \, T^*)}$$

**Key features:**
- Uses diffusion-specific Lennard-Jones parameters from Poling et al. (2001) for ~35 common components (see [Lennard-Jones Parameters](#lennard-jones-parameters))
- Falls back to database LJ parameters for components not in the diffusion lookup table
- Output in m²/s

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Chapman-Enskog");
```

---

### Wilke-Lee

Modified Chapman-Enskog with the Wilke-Lee empirical correction factor for improved accuracy.

**Class:** `WilkeLeeDiffusivity`

**Equation:**

$$D_{ij} = \frac{(1.084 - 0.249\sqrt{1/M_i + 1/M_j}) \times 10^{-4} \, T^{3/2} \sqrt{1/M_i + 1/M_j}}{P \, \sigma_{ij}^2 \, \Omega_D}$$

The prefactor $(1.084 - 0.249\sqrt{1/M_i + 1/M_j})$ is an empirical correction that slightly improves accuracy compared to standard Chapman-Enskog, particularly for asymmetric molecular pairs.

**Key features:**
- Same LJ parameter system as Chapman-Enskog (inherits the diffusion lookup table)
- Slightly better for polar/nonpolar pairs
- Typical accuracy: ±5-10%

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
```

---

### Fuller-Schettler-Giddings

A widely used empirical method that avoids Lennard-Jones parameters entirely, using atomic diffusion volumes instead.

**Class:** `FullerSchettlerGiddingsDiffusivity`

**Equation:**

$$D_{ij} = \frac{1.013 \times 10^{-3} \, T^{1.75} \sqrt{1/M_i + 1/M_j}}{P \left((\sum v_i)^{1/3} + (\sum v_j)^{1/3}\right)^2}$$

where:
- $T$ is temperature (K)
- $P$ is pressure (bar)
- $M_i, M_j$ are molar masses (g/mol)
- $\sum v_i$ is the sum of atomic diffusion volumes for species $i$ (cm³/mol)

**Diffusion volume table** (selected values):

| Species | $\sum v$ |
|---|---|
| H₂ | 7.07 |
| N₂ | 17.9 |
| O₂ | 16.6 |
| CO₂ | 26.9 |
| H₂O | 12.7 |
| CH₄ | 25.14 |
| C₂H₆ | 45.66 |
| C₃H₈ | 66.18 |

For components not in the table, the critical volume is used as fallback: $\sum v \approx 0.285 \, V_c$ where $V_c$ is in cm³/mol.

**Key features:**
- Does not need Lennard-Jones parameters
- Covers wide range of organic and inorganic species
- Often the most accurate gas model (typically ±2-5%)
- Temperature exponent is 1.75 (versus 1.5 for Chapman-Enskog)

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties()
    .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
```

---

## Liquid-Phase Models

All liquid-phase models calculate infinite-dilution binary diffusion coefficients. The Vignes mixing rule is applied for finite concentrations:

$$D_{ij}^{mix} = (D_{ij}^0)^{x_j} (D_{ji}^0)^{x_i}$$

### Siddiqi-Lucas

Correlates liquid diffusion with solvent viscosity and solute molar volume.

**Class:** `SiddiqiLucasMethod`

**Aqueous systems** (water as solvent):

$$D_{ij}^0 = 2.98 \times 10^{-7} \frac{T}{\eta_j^{1.026} \, V_i^{0.5473}}$$

**Non-aqueous systems:**

$$D_{ij}^0 = 9.89 \times 10^{-8} \frac{T}{\eta_j^{0.907} \, V_i^{0.45} \, V_j^{-0.265}}$$

where:
- $T$ is temperature (K)
- $\eta_j$ is solvent viscosity (cP)
- $V_i, V_j$ are molar volumes at normal boiling point (cm³/mol), estimated from $V = M / \rho_\text{liq}$ or the Le Bas method

**Key features:**
- Auto-detects aqueous vs non-aqueous systems
- Validated for water as solvent with typical accuracy ±30%

**Usage:**
```java
fluid.getPhase("aqueous").getPhysicalProperties()
    .setDiffusionCoefficientModel("Siddiqi Lucas");
```

---

### Wilke-Chang

Classic correlation relating diffusion to solvent association and viscosity.

**Class:** `WilkeChangDiffusivity`

**Equation:**

$$D_{ij}^0 = \frac{7.4 \times 10^{-8} (\phi_j M_j)^{0.5} T}{\eta_j \, V_i^{0.6}}$$

where:
- $\phi_j$ is the solvent association parameter (2.6 for water, 1.9 for methanol, 1.5 for ethanol, 1.0 for unassociated)
- $M_j$ is solvent molar mass (g/mol)
- $T$ is temperature (K)
- $\eta_j$ is solvent viscosity (cP)
- $V_i$ is solute molar volume at boiling point (cm³/mol), estimated as $0.285 \, V_c^{1.048}$ when not available directly

**Key features:**
- Association parameter automatically selected based on solvent type
- Widely validated for aqueous systems
- Typical accuracy ±10-15%

**Usage:**
```java
fluid.getPhase("aqueous").getPhysicalProperties()
    .setDiffusionCoefficientModel("Wilke-Chang");
```

---

### Tyn-Calus

Uses parachor-based molar volumes for improved accuracy.

**Class:** `TynCalusDiffusivity`

**Equation:**

$$D_{ij}^0 = 8.93 \times 10^{-8} \frac{V_j^{0.267}}{V_i^{0.433}} \frac{T}{\eta_j}$$

where:
- $V_i, V_j$ are molar volumes at boiling point (cm³/mol)
- $T$ is temperature (K)
- $\eta_j$ is solvent viscosity (cP)

**Key features:**
- No association parameter needed
- Good for organic solvent systems
- Typical accuracy ±10-20%

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties()
    .setDiffusionCoefficientModel("Tyn-Calus");
```

---

### Hayduk-Minhas

Specialized for paraffin solutions and aqueous systems with different correlations for each.

**Class:** `HaydukMinhasDiffusivity`

**Paraffin solutions:**

$$D_{ij}^0 = 13.3 \times 10^{-8} \frac{T^{1.47} \eta_j^{(\epsilon)}}{V_i^{0.71}}$$

where $\epsilon = 10.2/V_i - 0.791$.

**Aqueous solutions:**

$$D_{ij}^0 = 1.25 \times 10^{-8} (V_i^{-0.19} - 0.292) T^{1.52} \eta_j^{9.58/V_i - 1.12}$$

**Key features:**
- Separate correlations for paraffinic and aqueous systems
- Molar volume at boiling point estimated from critical volume: $V_b = 0.285 \, V_c^{1.048}$
- Good for hydrocarbon solvents

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties()
    .setDiffusionCoefficientModel("Hayduk-Minhas");
```

---

## General Models

### Corresponding States (CSP)

A generalized corresponding states method for both gas and liquid diffusion.

**Class:** `CorrespondingStatesDiffusivity`

Uses reduced temperature and density to correlate diffusion coefficients, consistent with the thermodynamic model.

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
```

---

### High Pressure

Pressure-corrected liquid diffusion model for conditions where the standard low-pressure correlations break down.

**Class:** `HighPressureDiffusivity`

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("High Pressure");
```

---

### Amine Diffusivity

Specialized correlations for amine solutions used in gas treating.

**Class:** `AmineDiffusivity`

**Includes:**
- CO₂ in amine solutions
- H₂S in amine solutions
- Amine in water

**Usage:**
```java
fluid.getPhase("aqueous").getPhysicalProperties()
    .setDiffusionCoefficientModel("Alkanol amine");
```

---

## Lennard-Jones Parameters

The Chapman-Enskog and Wilke-Lee models require Lennard-Jones parameters ($\sigma$, $\epsilon/k_B$) for each component. NeqSim uses a two-tier approach:

1. **Diffusion-specific parameters** — A built-in lookup table with standard values from Poling, Prausnitz and O'Connell (2001) and Bird, Stewart and Lightfoot (2002), covering ~35 common components. These are optimized for transport property calculations.

2. **Database fallback** — For components not in the diffusion lookup table, NeqSim uses the LJ parameters stored in the component database. These may be optimized for equation-of-state calculations and can give less accurate diffusion coefficients.

**Components with diffusion-specific LJ parameters:**

| Component | $\sigma$ (Å) | $\epsilon/k_B$ (K) |
|---|---|---|
| Helium | 2.551 | 10.22 |
| Hydrogen | 2.827 | 59.7 |
| Nitrogen | 3.798 | 71.4 |
| Oxygen | 3.467 | 106.7 |
| CO₂ | 3.941 | 195.2 |
| H₂S | 3.623 | 301.1 |
| Methane | 3.758 | 148.6 |
| Ethane | 4.443 | 215.7 |
| Propane | 5.118 | 237.1 |
| Water | 2.641 | 809.1 |
| Benzene | 5.349 | 412.3 |
| Methanol | 3.626 | 481.8 |

The full list includes noble gases, halogenated compounds, and hydrocarbons up to n-octane.

---

## Validation Against Experimental Data

All models have been validated against published experimental data. The table below shows results at 298.15 K and 1 atm.

### Gas-Phase Validation

| System | Experimental (cm²/s) | Chapman-Enskog | Fuller | Wilke-Lee | Source |
|---|---|---|---|---|---|
| CH₄-N₂ | 0.220 | 0.219 (0.7%) | 0.216 (2.0%) | 0.231 (5.0%) | Marrero and Mason (1972) |
| CO₂-N₂ | 0.167 | 0.155 (7.4%) | 0.162 (2.7%) | 0.166 (0.3%) | Poling (2001) |

**Gas model accuracy summary:**
- Chapman-Enskog: ±1-8%
- Fuller-Schettler-Giddings: ±2-3% (best overall for gas)
- Wilke-Lee: ±0.3-5%

### Liquid-Phase Validation (CO₂ in water at 25°C)

| Model | Calculated (10⁻⁹ m²/s) | Error vs 1.92 × 10⁻⁹ m²/s |
|---|---|---|
| Siddiqi-Lucas | 1.33 | -31% |
| Wilke-Chang | 1.73 | -10% |
| Hayduk-Minhas | 1.63 | -15% |

**Liquid model accuracy summary:**
- Wilke-Chang: ±10-15% (best for aqueous)
- Hayduk-Minhas: ±15-20%
- Siddiqi-Lucas: ±30% (conservative)

### Physical Consistency Tests

All models pass the following consistency checks:
- **Pressure scaling:** $D \propto 1/P$ (Fuller model verified to better than 1%)
- **Temperature scaling:** $D \propto T^{1.75}$ for Fuller (verified to better than 1%)
- **Inter-model agreement:** All gas models agree within a factor of 2; all liquid models agree within a factor of 5

---

## Model Selection Guide

| Application | Recommended Model | Model String | Typical Accuracy |
|---|---|---|---|
| Gas phase (general) | Fuller-Schettler-Giddings | `"Fuller-Schettler-Giddings"` | ±2-5% |
| Gas phase (light gases) | Chapman-Enskog or Wilke-Lee | `"Chapman-Enskog"` / `"Wilke Lee"` | ±1-8% |
| Aqueous liquids | Wilke-Chang | `"Wilke-Chang"` | ±10-15% |
| Organic liquids | Tyn-Calus or Hayduk-Minhas | `"Tyn-Calus"` / `"Hayduk-Minhas"` | ±10-20% |
| Wide P-T range | CSP | `"CSP"` | ±15-25% |
| Amine systems | Alkanol amine | `"Alkanol amine"` | Specialized |
| High pressure liquids | High Pressure | `"High Pressure"` | Pressure-corrected |

---

## Usage Examples

### Accessing Binary Diffusion Coefficients

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("CO2", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();

// Get binary diffusion coefficients
double[][] Dij = fluid.getPhase("gas").getPhysicalProperties()
    .getDiffusivityCalc().getBinaryDiffusionCoefficients();

// Print diffusion matrix
int n = fluid.getPhase("gas").getNumberOfComponents();
for (int i = 0; i < n; i++) {
    for (int j = 0; j < n; j++) {
        System.out.println("D[" + i + "][" + j + "] = " + Dij[i][j] + " m2/s");
    }
}
```

### Switching Between Gas Models

```java
SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
fluid.addComponent("methane", 0.5);
fluid.addComponent("nitrogen", 0.5);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Compare gas models
String[] models = {"Chapman-Enskog", "Wilke Lee", "Fuller-Schettler-Giddings"};
for (String model : models) {
    fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel(model);
    double D = fluid.getPhase("gas").getPhysicalProperties()
        .diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    System.out.println(model + ": D(CH4-N2) = " + D + " m2/s");
}
```

### Liquid-Phase Diffusion in Water

```java
SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
fluid.addComponent("CO2", 0.01);
fluid.addComponent("water", 0.99);
fluid.createDatabase(true);
fluid.setMixingRule(2);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Compare liquid models
String[] models = {"Siddiqi Lucas", "Wilke-Chang", "Hayduk-Minhas", "Tyn-Calus"};
for (String model : models) {
    fluid.getPhase("aqueous").getPhysicalProperties().setDiffusionCoefficientModel(model);
    double D = fluid.getPhase("aqueous").getPhysicalProperties()
        .diffusivityCalc.calcBinaryDiffusionCoefficient(0, 1, 0);
    System.out.println(model + ": D(CO2-water) = " + D + " m2/s");
}
```

### Effective Diffusivity

```java
// Get effective diffusion coefficient
double[] Deff = fluid.getPhase("gas").getPhysicalProperties()
    .getDiffusivityCalc().getEffectiveDiffusionCoefficient();

for (int i = 0; i < n; i++) {
    String name = fluid.getPhase("gas").getComponent(i).getName();
    System.out.println("D_eff[" + name + "] = " + Deff[i] + " m2/s");
}
```

### Diffusivity in Amine Solutions

```java
SystemInterface fluid = new SystemSrkCPAstatoil(313.15, 1.0);
fluid.addComponent("CO2", 0.1);
fluid.addComponent("water", 0.7);
fluid.addComponent("MDEA", 0.2);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Use amine-specific diffusivity model
fluid.initPhysicalProperties("AMINE");

double[][] D = fluid.getPhase("aqueous").getPhysicalProperties()
    .getDiffusivityCalc().getBinaryDiffusionCoefficients();

System.out.println("D_CO2 in amine: " + D[0][1] + " m2/s");
```

---

## Physical Background

### Pressure Dependence

**Gases:**
$$D \propto \frac{1}{P}$$

At constant temperature, gas diffusivity is inversely proportional to pressure.

**Liquids:**
Weak pressure dependence; can often be neglected at moderate pressures. Use the `"High Pressure"` model for elevated pressures.

### Temperature Dependence

**Gases:**
$$D \propto T^{1.5} \text{ (Chapman-Enskog/Wilke-Lee)} \quad \text{or} \quad D \propto T^{1.75} \text{ (Fuller)}$$

**Liquids:**
$$D \propto T / \eta$$

Since viscosity decreases with temperature, liquid diffusivity increases.

### Typical Values

| Phase | Diffusivity Range |
|---|---|
| Gas (1 bar) | $10^{-5}$ to $10^{-4}$ m²/s |
| Gas (100 bar) | $10^{-7}$ to $10^{-6}$ m²/s |
| Liquid | $10^{-10}$ to $10^{-9}$ m²/s |
| Supercritical | $10^{-8}$ to $10^{-7}$ m²/s |

---

## Multicomponent Diffusion

For multicomponent systems, the flux of species $i$ depends on gradients of all components:

$$J_i = -c_t \sum_{j=1}^{n} D_{ij} \nabla x_j$$

NeqSim calculates:
1. **Binary Maxwell-Stefan coefficients** from pure component properties
2. **Effective diffusivities** using the Wilke approximation
3. **Fick diffusion matrix** (optional) including thermodynamic factors

---

## References

1. Chapman, S., Cowling, T.G. (1970). The Mathematical Theory of Non-uniform Gases, 3rd Ed. Cambridge University Press.
2. Wilke, C.R., Lee, C.Y. (1955). Estimation of Diffusion Coefficients for Gases and Vapors. Ind. Eng. Chem., 47(6), 1253-1257.
3. Fuller, E.N., Schettler, P.D., Giddings, J.C. (1966). New Method for Prediction of Binary Gas-Phase Diffusion Coefficients. Ind. Eng. Chem., 58(5), 18-27.
4. Siddiqi, M.A., Lucas, K. (1986). Correlations for Prediction of Diffusion in Liquids. Can. J. Chem. Eng., 64, 839-843.
5. Wilke, C.R., Chang, P. (1955). Correlation of Diffusion Coefficients in Dilute Solutions. AIChE J., 1(2), 264-270.
6. Tyn, M.T., Calus, W.F. (1975). Diffusion Coefficients in Dilute Binary Liquid Mixtures. J. Chem. Eng. Data, 20(1), 106-109.
7. Hayduk, W., Minhas, B.S. (1982). Correlations for Prediction of Molecular Diffusivities in Liquids. Can. J. Chem. Eng., 60, 295-299.
8. Poling, B.E., Prausnitz, J.M., O'Connell, J.P. (2001). The Properties of Gases and Liquids, 5th Ed. McGraw-Hill.
9. Bird, R.B., Stewart, W.E., Lightfoot, E.N. (2002). Transport Phenomena, 2nd Ed. Wiley.
10. Marrero, T.R., Mason, E.A. (1972). Gaseous Diffusion Coefficients. J. Phys. Chem. Ref. Data, 1(1), 3-118.
