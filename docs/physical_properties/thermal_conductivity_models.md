# Thermal Conductivity Models

This guide documents the thermal conductivity calculation methods available in NeqSim for gas, liquid, and multiphase systems.

## Table of Contents
- [Overview](#overview)
- [Available Models](#available-models)
  - [PFCT (Pedersen)](#pfct-pedersen)
  - [Chung Method](#chung-method)
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
fluid.initPhysicalProperties();
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("Chung");
fluid.getPhase("oil").getPhysicalProperties().setConductivityModel("PFCT");
```

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
```

---

### CO₂ Reference

High-accuracy thermal conductivity for CO₂ based on the Vesovic et al. correlation.

**Class:** `CO2ConductivityMethod`

**Coverage:**
- Temperature: 200-1500 K
- Pressure: Up to 300 MPa
- All phases (gas, liquid, supercritical)

**Best for:**
- Pure CO₂ systems
- CCS applications
- Reference calculations

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setConductivityModel("CO2Model");
```

---

## Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| Petroleum mixtures | PFCT | Corresponding states with MW correction |
| Gas processing | Chung | Good for gases |
| Simple liquid mixtures | polynom | Uses database parameters |
| Pure CO₂ | CO2Model | High accuracy |
| Wide P-T range | PFCT | Robust extrapolation |
| Polar systems | Chung | Includes polar corrections |

---

## Usage Examples

### Basic Conductivity Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
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
System.out.println("Gas thermal conductivity: " + gasConductivity + " W/(m·K)");
```

### Comparing Conductivity Models

```java
String[] models = {"PFCT", "Chung"};

for (String model : models) {
    SystemInterface fluid = createFluid();
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    
    fluid.getPhase("gas").getPhysicalProperties().setConductivityModel(model);
    fluid.initPhysicalProperties();
    
    double k = fluid.getPhase("gas").getThermalConductivity("W/mK");
    System.out.println(model + ": " + k + " W/(m·K)");
}
```

### Conductivity vs Pressure

```java
SystemInterface baseFluid = new SystemSrkEos(350.0, 10.0);
baseFluid.addComponent("methane", 1.0);
baseFluid.setMixingRule("classic");

double[] pressures = {10, 50, 100, 150, 200};  // bar

for (double P : pressures) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setPressure(P, "bar");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    
    double k = fluid.getPhase(0).getThermalConductivity("W/mK");
    System.out.println("P=" + P + " bar: " + k + " W/(m·K)");
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

if (fluid.hasPhaseType("gas")) {
    System.out.println("Gas k: " + 
        fluid.getPhase("gas").getThermalConductivity("W/mK") + " W/(m·K)");
}
if (fluid.hasPhaseType("oil")) {
    System.out.println("Oil k: " + 
        fluid.getPhase("oil").getThermalConductivity("W/mK") + " W/(m·K)");
}
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
3. Vesovic, V., et al. (1990). The Transport Properties of Carbon Dioxide. J. Phys. Chem. Ref. Data.
4. Poling, B.E., et al. (2001). The Properties of Gases and Liquids, 5th Ed.
