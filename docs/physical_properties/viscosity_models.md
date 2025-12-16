# Viscosity Models

This guide documents the viscosity calculation methods available in NeqSim for gas, liquid, and multiphase systems.

## Table of Contents
- [Overview](#overview)
- [Available Models](#available-models)
  - [LBC (Lohrenz-Bray-Clark)](#lbc-lohrenz-bray-clark)
  - [Friction Theory](#friction-theory)
  - [PFCT (Pedersen)](#pfct-pedersen)
  - [Chung Method](#chung-method)
  - [Polynomial Correlation](#polynomial-correlation)
  - [Reference Fluid Methods](#reference-fluid-methods)
- [Model Selection Guide](#model-selection-guide)
- [Tuning Parameters](#tuning-parameters)
- [Usage Examples](#usage-examples)

---

## Overview

Viscosity describes a fluid's resistance to flow. NeqSim provides several viscosity models suitable for different applications:

**Units:**
- Default output: **Pa·s** (Pascal-seconds)
- Alternative: **cP** (centipoise), where 1 cP = 0.001 Pa·s

**Setting a viscosity model:**
```java
fluid.initPhysicalProperties();
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("friction theory");
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
```

---

## Available Models

### LBC (Lohrenz-Bray-Clark)

The Lohrenz-Bray-Clark (1964) method is widely used in reservoir simulation. It combines a dilute gas correlation with a dense fluid polynomial correction.

**Class:** `LBCViscosityMethod`

**Equation:**
$$\eta = \eta^* + \frac{(\eta_r - 0.0001)^4}{\xi}$$

where:
- $\eta^*$ is the dilute gas viscosity
- $\eta_r$ is a function of reduced density
- $\xi$ is the inverse viscosity parameter

The dense fluid contribution uses a polynomial:
$$\eta_r = a_0 + a_1\rho_r + a_2\rho_r^2 + a_3\rho_r^3 + a_4\rho_r^4$$

**Default parameters:** `{0.10230, 0.023364, 0.058533, -0.040758, 0.0093324}`

**Applicable phases:** Gas, Oil

**Best for:**
- Reservoir simulation
- Light to medium oils
- Systems where tuning to lab data is available

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
```

---

### Friction Theory

The Quiñones-Cisneros and Firoozabadi (2000) friction theory relates viscosity to the repulsive and attractive pressure contributions from the equation of state.

**Class:** `FrictionTheoryViscosityMethod`

**Equation:**
$$\eta = \eta_0 + \kappa_a P_a + \kappa_{aa} P_a^2 + \kappa_r P_r + \kappa_{rr} P_r^2$$

where:
- $\eta_0$ is the dilute gas viscosity (Chung correlation)
- $P_a$ is the attractive pressure from EoS
- $P_r$ is the repulsive pressure from EoS
- $\kappa$ are friction coefficients (EoS-dependent)

**Applicable phases:** Gas, Oil (any EoS-based phase)

**Best for:**
- Wide pressure/temperature ranges
- Near-critical conditions
- When using SRK or PR EoS

**Automatic EoS detection:** The method automatically selects SRK or PR constants based on the phase type.

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
```

**Custom constants (for other EoS):**
```java
FrictionTheoryViscosityMethod viscModel = 
    (FrictionTheoryViscosityMethod) fluid.getPhase("oil")
        .getPhysicalProperties().getViscosityModel();

// Set custom friction theory constants
viscModel.setFrictionTheoryConstants(kapac, kaprc, kaprrc, kapa, kapr, kaprr);
```

---

### PFCT (Pedersen)

The Pedersen Friction Corresponding States Theory uses methane as a reference fluid with shape factors for mixture calculations.

**Classes:**
- `PFCTViscosityMethodMod86` - Standard Pedersen method (1987)
- `PFCTViscosityMethodHeavyOil` - Extended for heavy oils

**Equation:**
$$\eta_{mix} = \eta_{ref} \cdot \frac{f_\eta \cdot \alpha_{mix}}{\alpha_0}$$

where:
- $\eta_{ref}$ is the reference fluid (methane) viscosity
- $f_\eta$ is the shape factor ratio
- $\alpha$ are molecular weight correction factors

**Applicable phases:** Gas, Oil

**Best for:**
- Petroleum mixtures with characterized fractions
- Heavy oil systems (use `"PFCT-Heavy-Oil"`)
- Wide-range predictions

**Usage:**
```java
// Standard Pedersen
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT");

// Heavy oil variant
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT-Heavy-Oil");
```

---

### Chung Method

The Chung method (1984, 1988) is a corresponding states method for dilute gas and dense fluid viscosity.

**Class:** `ChungViscosityMethod`

**Equation (dilute gas):**
$$\eta_0 = \frac{40.785 F_c \sqrt{M T}}{\Omega_v V_c^{2/3}}$$

where:
- $F_c$ is the correction factor for polar/associating fluids
- $\Omega_v$ is the collision integral
- $V_c$ is critical volume
- $M$ is molar mass

**Applicable phases:** Primarily gas phase

**Best for:**
- Dilute gas mixtures
- Polar gases
- As baseline for other methods

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("Chung");
```

---

### Polynomial Correlation

Uses component-specific polynomial coefficients from the database.

**Class:** `Viscosity` (liquid), `GasViscosity` (gas)

**Equation:**
$$\ln(\eta) = A + \frac{B}{T} + C\ln(T) + DT$$

where A, B, C, D are component-specific parameters from COMP database.

**Database columns:** `LIQVISC1`, `LIQVISC2`, `LIQVISC3`, `LIQVISC4`

**Applicable phases:** Primarily liquid

**Best for:**
- Pure components with available parameters
- Simple mixtures with mixing rules

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("polynom");
```

---

### Reference Fluid Methods

Specialized high-accuracy methods for specific fluids:

#### Methane (Reference)
**Class:** `MethaneViscosityMethod`
- Uses Setzmann & Wagner reference equation
- High accuracy for pure methane
- Used as reference in PFCT calculations

#### CO₂
**Class:** `CO2ViscosityMethod`
- Fenghour et al. correlation
- Covers gas, liquid, and supercritical regions

#### Hydrogen
**Classes:** `MuznyViscosityMethod`, `MuznyModViscosityMethod`
- High-accuracy hydrogen viscosity
- Important for hydrogen energy applications

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("MethaneModel");
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("CO2Model");
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("Muzny");
```

---

## Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| Reservoir simulation | LBC | Tunable, industry standard |
| Wide P-T range | Friction Theory | Good near critical |
| Heavy oils | PFCT-Heavy-Oil | Extended for high MW |
| Characterized crudes | PFCT | Works with pseudo-components |
| Gas processing | Chung | Good for gases |
| Pure CO₂ | CO2Model | High accuracy |
| Pure H₂ | Muzny | Reference accuracy |
| Aqueous systems | Salt Water | Water correlation |

---

## Tuning Parameters

### LBC Parameter Tuning

The LBC method is commonly tuned to match laboratory viscosity data:

```java
// Get current parameters
LBCViscosityMethod lbc = (LBCViscosityMethod) 
    fluid.getPhase("oil").getPhysicalProperties().getViscosityModel();

// Set all 5 parameters
double[] params = {0.1023, 0.023364, 0.058533, -0.040758, 0.0093324};
fluid.getPhase("oil").getPhysicalProperties().setLbcParameters(params);

// Or tune individual parameters
fluid.getPhase("oil").getPhysicalProperties().setLbcParameter(0, 0.105);
```

**Tuning procedure:**
1. Measure viscosity at multiple P-T conditions
2. Run flash and calculate properties
3. Adjust parameters to minimize error
4. Validate at other conditions

---

## Usage Examples

### Basic Viscosity Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemInterface fluid = new SystemSrkEos(350.0, 150.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.10);
fluid.addComponent("n-decane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();

// Get viscosities
double gasVisc = fluid.getPhase("gas").getViscosity("cP");
double oilVisc = fluid.getPhase("oil").getViscosity("cP");

System.out.println("Gas viscosity: " + gasVisc + " cP");
System.out.println("Oil viscosity: " + oilVisc + " cP");
```

### Comparing Viscosity Models

```java
String[] models = {"LBC", "friction theory", "PFCT"};

for (String model : models) {
    SystemInterface fluid = createFluid();
    ops.TPflash();
    fluid.initPhysicalProperties();
    
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel(model);
    fluid.initPhysicalProperties();
    
    double visc = fluid.getPhase("oil").getViscosity("cP");
    System.out.println(model + ": " + visc + " cP");
}
```

### Viscosity vs Temperature

```java
SystemInterface baseFluid = createFluid();
baseFluid.initPhysicalProperties();

double[] temps = {300, 320, 340, 360, 380, 400};  // K

for (double T : temps) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setTemperature(T, "K");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    
    double visc = fluid.getPhase("oil").getViscosity("cP");
    System.out.println("T=" + (T-273.15) + "°C: " + visc + " cP");
}
```

---

## Mathematical Details

### Dilute Gas Viscosity (Chapman-Enskog)

For dilute gases, viscosity is calculated from kinetic theory:

$$\eta_0 = \frac{5}{16} \frac{\sqrt{\pi m k_B T}}{\pi \sigma^2 \Omega^{(2,2)*}}$$

where:
- $m$ is molecular mass
- $\sigma$ is Lennard-Jones diameter
- $\Omega^{(2,2)*}$ is the collision integral

### Mixing Rules

Most models use molar fraction-weighted mixing:

$$\eta_{mix} = \exp\left(\sum_i x_i \ln \eta_i\right)$$

or the more rigorous:

$$\eta_{mix} = \frac{\sum_i x_i \sqrt{M_i} \eta_i}{\sum_i x_i \sqrt{M_i}}$$

---

## References

1. Lohrenz, J., Bray, B.G., Clark, C.R. (1964). Calculating Viscosities of Reservoir Fluids from Their Compositions. JPT.
2. Quiñones-Cisneros, S.E., Firoozabadi, A. (2000). One Parameter Friction Theory. AIChE J.
3. Pedersen, K.S., et al. (1987). Viscosity of Crude Oils. Chem. Eng. Sci.
4. Chung, T.H., et al. (1988). Generalized Multiparameter Correlation for Nonpolar and Polar Fluid Transport Properties. I&EC Res.
