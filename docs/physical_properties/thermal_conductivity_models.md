---
title: Thermal Conductivity Models
description: This guide documents the thermal conductivity calculation methods available in NeqSim for gas, liquid, and multiphase systems.
---

This guide documents the thermal conductivity calculation methods available in NeqSim for gas, liquid, and multiphase systems.

## Table of Contents

- [Overview](#overview)
- [Available Models](#available-models)
  - [PFCT (Pedersen)](#pfct-pedersen)
  - [Chung Method](#chung-method)
  - [Chung Dense Method](#chung-dense-method)
  - [Polynomial Correlation](#polynomial-correlation)
  - [CO₂ Reference](#co2-reference)
- [Model Selection Guide](#model-selection-guide)
- [Usage Examples](#usage-examples)

---

## Overview

Thermal conductivity ($\lambda$ or $k$) describes a material's ability to conduct heat. It is essential for:
- Heat exchanger design
- Pipeline heat loss calculations
- Thermal simulation of process equipment

**Units:**
- Default output: **W/(m·K)** (Watts per meter-Kelvin)

**Setting a conductivity model:**
```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");
fluid.getPhase("gas").initPhysicalProperties();
fluid.getPhase("oil").initPhysicalProperties();
```

Select the model after the flash and initial property calculation, then reinitialize each changed
phase as shown. Model keys are case-sensitive. Unsupported keys currently fall back to `PFCT`, so
use one of the documented keys and cover model selection in tests.

---

## Available Models

### PFCT (Pedersen)

The Pedersen Corresponding States method uses methane as a reference fluid with molecular weight corrections.

**Class:** `PFCTConductivityMethodMod86`

**Principle:**
Uses corresponding states with methane as reference:

$$\lambda_{mix} = \lambda_{ref}(T_0, P_0) \cdot \frac{\alpha_{mix}}{\alpha_0}$$

where:
- $\lambda_{ref}$ is methane thermal conductivity at corresponding conditions
- $T_0, P_0$ are the corresponding temperature and pressure
- $\alpha$ are molecular weight correction factors

**Corresponding state mapping:**
$$T_0 = T \cdot \frac{T_{c,ref}}{T_{c,mix}} \cdot \frac{\alpha_0}{\alpha_{mix}}$$

$$P_0 = P \cdot \frac{P_{c,ref}}{P_{c,mix}} \cdot \frac{\alpha_0}{\alpha_{mix}}$$

**Applicable phases:** Gas, Oil

**Best for:**
- Petroleum mixtures
- Wide pressure-temperature ranges
- Systems with characterized fractions

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");
```

---

### Chung Method

The Chung method (1988) is a corresponding states correlation based on kinetic theory.

**Class:** `ChungConductivityMethod`

**Equation (dilute gas):**
$$\lambda_0 = \frac{7.452 \eta_0 \Psi}{M}$$

where:
- $\eta_0$ is the dilute gas viscosity
- $\Psi$ is a correction factor
- $M$ is molar mass

The correction factor accounts for:
- Polyatomic structure
- Polar effects
- Association

**Dense fluid correction:**
$$\lambda = \lambda_0 \cdot G_2(T^*, \rho^*) + B_1 q B_2$$

where $G_2$ and $B$ terms account for density effects.

**Applicable phases:** Primarily gas phase

**Best for:**
- Gas-phase calculations
- Polar gases
- As baseline correlation

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
fluid.getPhase("gas").initPhysicalProperties();
```

---

### Chung Dense Method

`Chung-dense` implements the full Chung et al. correlation, including dilute-gas and
density-dependent contributions. Use it when pressure or liquid-like density makes the dilute
`Chung` model insufficient.

**Class:** `ChungDenseConductivityMethod`

**Applicable phases:** Gas and liquid

**Usage:**

```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung-dense");
fluid.getPhase("gas").initPhysicalProperties();
```

---

### Polynomial Correlation

Uses component-specific polynomial coefficients from the database.

**Class:** `Conductivity` (in liquid package)

**Equation:**
$$\lambda = A + BT + CT^2$$

where A, B, C are component-specific parameters.

**Database columns:** `LIQUIDCONDUCTIVITY1`, `LIQUIDCONDUCTIVITY2`, `LIQUIDCONDUCTIVITY3`

**Mixing rule:**
$$\lambda_{mix} = \sum_i x_i \lambda_i$$

**Applicable phases:** Liquid

**Best for:**
- Pure components with available parameters
- Simple liquid mixtures

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("polynom");
fluid.getPhase("oil").initPhysicalProperties();
```

---

### CO2 Reference

Pure-CO₂ thermal conductivity model implemented as a polynomial fit to reference data generated
with CoolProp. The implementation is checked against NIST values in
`CO2ConductivityMethodTest` and uses Span-Wagner properties if a usable density is unavailable.

**Class:** `CO2ConductivityMethod`

**Constraint:** The phase must contain pure CO₂. The implementation rejects mixtures; do not use
`CO2Model` for CO₂-rich multicomponent streams. NeqSim does not enforce a temperature-pressure
validity envelope for this polynomial, so validate extrapolated states independently.

**Best for:**
- Pure CO₂ systems
- CCS applications
- Reference calculations

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("CO2Model");
fluid.getPhase("gas").initPhysicalProperties();
```

---

## Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| Petroleum mixtures | PFCT | Corresponding states with MW correction |
| Low-density gas | Chung | Dilute-gas contribution |
| Dense gas or liquid | Chung-dense | Includes density-dependent contribution |
| Simple liquid mixtures | polynom | Uses database parameters |
| Pure CO₂ | CO2Model | Rejects mixtures; validate extrapolation |
| Wide P-T range | PFCT | Robust extrapolation |
| Polar systems | Chung | Includes polar corrections |

Additional implemented keys are `friction theory`, `Filippov`, `WaterModel`, and `H2Model`.
They are specialized models and should be selected only with a composition and phase appropriate
to the underlying correlation.

---

## Usage Examples

### Basic Conductivity Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();

// Get thermal conductivity
double gasConductivity = fluid.getPhase("gas").getThermalConductivity("W/mK");
if (!(gasConductivity > 0.0)) {
    throw new IllegalStateException("Expected positive gas thermal conductivity");
}
```

### Comparing Conductivity Models

```java
String[] models = {"PFCT", "Chung-dense"};
double[] conductivities = new double[models.length];

for (int i = 0; i < models.length; i++) {
    SystemInterface fluid = new SystemSrkEos(350.0, 50.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.10);
    fluid.addComponent("propane", 0.05);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initProperties();
    
    fluid.getPhase("gas").getPhysicalProperties().setConductivityModel(models[i]);
    fluid.getPhase("gas").initPhysicalProperties();
    conductivities[i] = fluid.getPhase("gas").getThermalConductivity("W/mK");
}
```

### Conductivity vs Pressure

```java
SystemInterface baseFluid = new SystemSrkEos(350.0, 10.0);
baseFluid.addComponent("methane", 1.0);
baseFluid.setMixingRule("classic");

double[] pressuresBara = {10, 50, 100, 150, 200};
double[] conductivities = new double[pressuresBara.length];

for (int i = 0; i < pressuresBara.length; i++) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setPressure(pressuresBara[i], "bara");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    
    conductivities[i] = fluid.getPhase(0).getThermalConductivity("W/mK");
}
```

### Two-Phase System

```java
SystemInterface fluid = new SystemSrkEos(280.0, 30.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("n-pentane", 0.5);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

double gasConductivity = fluid.hasPhaseType("gas")
    ? fluid.getPhase("gas").getThermalConductivity("W/mK") : Double.NaN;
double oilConductivity = fluid.hasPhaseType("oil")
    ? fluid.getPhase("oil").getThermalConductivity("W/mK") : Double.NaN;
```

---

## Physical Background

### Kinetic Theory (Dilute Gas)

For dilute gases, thermal conductivity is related to viscosity through:

$$\lambda = \frac{f \cdot \eta \cdot C_v}{M}$$

where:
- $f$ is the Eucken factor (typically 1.32 for monoatomic, 1.77 for polyatomic)
- $\eta$ is dynamic viscosity
- $C_v$ is heat capacity at constant volume
- $M$ is molar mass

### Mixing Rules

For mixtures, thermal conductivity is typically calculated using:

**Mass fraction weighting:**
$$\lambda_{mix} = \sum_i w_i \lambda_i$$

**Molar weighting with interaction:**
$$\lambda_{mix} = \sum_i \sum_j \frac{x_i x_j \lambda_{ij}}{\sum_k x_k \phi_{ik}}$$

where $\lambda_{ij}$ is a combining rule and $\phi_{ik}$ is an interaction factor.

### Pressure Effects

Thermal conductivity increases with pressure, particularly in dense fluids:

- At low pressures: $\lambda \approx \lambda_0(T)$
- At high pressures: $\lambda = \lambda_0 + \Delta\lambda(\rho)$

The PFCT method accounts for this through corresponding states mapping to reference fluid behavior.

---

## Temperature and Pressure Dependence

### Gases
- Conductivity increases with temperature (~$T^{0.8}$)
- Weak pressure dependence at low-moderate pressures
- Significant increase at high pressures (dense gas)

### Liquids
- Conductivity typically decreases with temperature
- Slight increase with pressure
- Water is an exception (increases with T up to ~130°C)

---

## References

1. Pedersen, K.S., et al. (1989). Thermal Conductivity of Crude Oils. Chem. Eng. Sci.
2. Chung, T.H., et al. (1988). Generalized Multiparameter Correlation. I&EC Res.
3. Huber, M.L., et al. (2016). New International Formulation for the Thermal Conductivity of CO₂. J. Phys. Chem. Ref. Data.
4. Scalabrin, G., et al. (2006). A Reference Multiparameter Thermal Conductivity Equation for Carbon Dioxide. J. Phys. Chem. Ref. Data.
5. Poling, B.E., et al. (2001). The Properties of Gases and Liquids, 5th Ed.
