# Diffusivity Models

This guide documents the diffusion coefficient calculation methods available in NeqSim for gas and liquid systems.

## Table of Contents
- [Overview](#overview)
- [Types of Diffusion Coefficients](#types-of-diffusion-coefficients)
- [Available Models](#available-models)
  - [Wilke-Lee (Gases)](#wilke-lee-gases)
  - [Siddiqi-Lucas (Liquids)](#siddiqi-lucas-liquids)
  - [Corresponding States (CSP)](#corresponding-states-csp)
  - [Amine Diffusivity](#amine-diffusivity)
- [Model Selection Guide](#model-selection-guide)
- [Usage Examples](#usage-examples)

---

## Overview

Diffusion coefficients describe the rate of molecular transport due to concentration gradients. They are essential for:
- Mass transfer calculations
- Absorption/desorption modeling
- Reaction kinetics in multiphase systems
- Pipeline corrosion modeling

**Units:**
- Default output: **m²/s**

**Setting a diffusivity model:**
```java
fluid.initPhysicalProperties();
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
fluid.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
```

---

## Types of Diffusion Coefficients

### Binary Diffusion Coefficient ($D_{ij}$)

The diffusion coefficient for species $i$ moving through species $j$ at infinite dilution.

### Effective Diffusion Coefficient ($D_i^{eff}$)

The effective diffusivity of species $i$ in a multicomponent mixture:

$$D_i^{eff} = \frac{1 - x_i}{\sum_{j \neq i} \frac{x_j}{D_{ij}}}$$

### Maxwell-Stefan Diffusion Coefficients

The fundamental diffusion coefficients describing molecular interactions, related to Fick diffusion through thermodynamic factors.

---

## Available Models

### Wilke-Lee (Gases)

The Wilke-Lee method is based on Chapman-Enskog kinetic theory for gases.

**Class:** `WilkeLeeDiffusivity`

**Equation:**
$$D_{ij} = \frac{(1.084 - 0.249\sqrt{1/M_i + 1/M_j}) \times 10^{-4} T^{1.5} \sqrt{1/M_i + 1/M_j}}{P \sigma_{ij}^2 \Omega_D}$$

where:
- $T$ is temperature (K)
- $P$ is pressure (bar)
- $M_i, M_j$ are molar masses (g/mol)
- $\sigma_{ij}$ is the Lennard-Jones collision diameter (Å)
- $\Omega_D$ is the collision integral

**Collision diameter combining rule:**
$$\sigma_{ij} = \frac{\sigma_i + \sigma_j}{2}$$

**Collision integral approximation:**
$$\Omega_D = \frac{A}{(T^*)^B} + \frac{C}{\exp(DT^*)} + \frac{E}{\exp(FT^*)} + \frac{G}{\exp(HT^*)}$$

where $T^* = k_B T / \epsilon_{ij}$.

**Applicable phases:** Gas

**Best for:**
- Gas-phase binary diffusion
- Light gases (H₂, CO₂, CH₄, etc.)
- Moderate pressures

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("Wilke Lee");
```

---

### Siddiqi-Lucas (Liquids)

The Siddiqi-Lucas method is designed for liquid-phase binary diffusion.

**Class:** `SiddiqiLucasMethod`

**Aqueous systems:**
$$D_{ij} = 2.98 \times 10^{-7} \frac{T}{\eta_j^{1.026} V_i^{0.5473}}$$

**Non-aqueous systems:**
$$D_{ij} = 9.89 \times 10^{-8} \frac{T}{\eta_j^{0.907} V_i^{0.45} V_j^{-0.265}}$$

where:
- $T$ is temperature (K)
- $\eta_j$ is solvent viscosity (cP)
- $V_i, V_j$ are molar volumes at normal boiling point (cm³/mol)

**Applicable phases:** Liquid (aqueous and organic)

**Best for:**
- Liquid-phase diffusion
- Aqueous and organic solvents
- Dilute solutions

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setDiffusionCoefficientModel("Siddiqi Lucas");
```

---

### Corresponding States (CSP)

A generalized corresponding states method for both gas and liquid diffusion.

**Class:** `CorrespondingStatesDiffusivity`

**Principle:**
Uses reduced temperature and density to correlate diffusion:

$$D^* = D \cdot \frac{\sigma^2}{(M/N_A) \sqrt{k_B T / M}}$$

The reduced diffusivity is correlated against reduced temperature and density.

**Applicable phases:** Gas, Liquid

**Best for:**
- Wide temperature/pressure ranges
- When specific models aren't available
- Consistent with thermodynamic model

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setDiffusionCoefficientModel("CSP");
```

---

### Amine Diffusivity

Specialized correlations for amine solutions used in gas treating.

**Class:** `AmineDiffusivity`

**Includes:**
- CO₂ in amine solutions
- H₂S in amine solutions
- Amine in water

**Applicable phases:** Aqueous amine solutions

**Best for:**
- Gas sweetening processes
- Amine regeneration
- CO₂ capture

**Usage:**
```java
fluid.getPhase("aqueous").getPhysicalProperties()
    .setDiffusionCoefficientModel("Alkanol amine");
```

---

## Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| Gas phase | Wilke Lee | Based on kinetic theory |
| Aqueous liquids | Siddiqi Lucas | Validated for water |
| Organic liquids | Siddiqi Lucas | Use non-aqueous correlation |
| Wide P-T range | CSP | Corresponding states |
| Amine systems | Alkanol amine | Specialized for gas treating |
| CO₂ in water | CO2water model | Specific correlation |

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
        System.out.println("D[" + i + "][" + j + "] = " + Dij[i][j] + " m²/s");
    }
}
```

### Effective Diffusivity

```java
// Get effective diffusion coefficient
double[] Deff = fluid.getPhase("gas").getPhysicalProperties()
    .getDiffusivityCalc().getEffectiveDiffusionCoefficient();

for (int i = 0; i < n; i++) {
    String name = fluid.getPhase("gas").getComponent(i).getName();
    System.out.println("D_eff[" + name + "] = " + Deff[i] + " m²/s");
}
```

### Comparing Gas and Liquid Diffusivity

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("CO2", 0.1);
fluid.addComponent("n-octane", 0.9);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Compare gas and liquid diffusivities
if (fluid.hasPhaseType("gas")) {
    double[][] Dgas = fluid.getPhase("gas").getPhysicalProperties()
        .getDiffusivityCalc().getBinaryDiffusionCoefficients();
    System.out.println("Gas D_CO2-octane: " + Dgas[0][1] + " m²/s");
}

if (fluid.hasPhaseType("oil")) {
    double[][] Dliq = fluid.getPhase("oil").getPhysicalProperties()
        .getDiffusivityCalc().getBinaryDiffusionCoefficients();
    System.out.println("Liquid D_CO2-octane: " + Dliq[0][1] + " m²/s");
}
// Gas diffusivity is typically 10,000x larger than liquid
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

System.out.println("D_CO2 in amine: " + D[0][1] + " m²/s");
```

---

## Physical Background

### Pressure Dependence

**Gases:**
$$D \propto \frac{1}{P}$$

At constant temperature, gas diffusivity is inversely proportional to pressure.

**Liquids:**
Weak pressure dependence; can often be neglected.

### Temperature Dependence

**Gases:**
$$D \propto T^{1.5}$$

**Liquids:**
$$D \propto T / \eta$$

Since viscosity decreases with temperature, liquid diffusivity increases.

### Typical Values

| Phase | Diffusivity Range |
|-------|-------------------|
| Gas (1 bar) | 10⁻⁵ to 10⁻⁴ m²/s |
| Gas (100 bar) | 10⁻⁷ to 10⁻⁶ m²/s |
| Liquid | 10⁻¹⁰ to 10⁻⁹ m²/s |
| Supercritical | 10⁻⁸ to 10⁻⁷ m²/s |

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

1. Wilke, C.R., Lee, C.Y. (1955). Estimation of Diffusion Coefficients for Gases and Vapors. I&EC.
2. Siddiqi, M.A., Lucas, K. (1986). Correlations for Prediction of Diffusion in Liquids. Can. J. Chem. Eng.
3. Fuller, E.N., et al. (1966). New Method for Prediction of Binary Gas-Phase Diffusion Coefficients. I&EC.
4. Poling, B.E., et al. (2001). The Properties of Gases and Liquids, 5th Ed.
