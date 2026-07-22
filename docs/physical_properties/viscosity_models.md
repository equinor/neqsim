---
title: Viscosity Models
description: This guide documents the viscosity calculation methods available in NeqSim for gas, liquid, and multiphase systems.
---

This guide documents the viscosity calculation methods available in NeqSim for gas, liquid, and multiphase systems.

## Table of Contents

- [Overview](#overview)
- [Available Models](#available-models)
  - [LBC (Lohrenz-Bray-Clark)](#lbc-lohrenz-bray-clark)
  - [Friction Theory](#friction-theory)
  - [PFCT (Pedersen)](#pfct-pedersen)
  - [Polynomial Correlation](#polynomial-correlation)
  - [Reference Fluid Methods](#reference-fluid-methods)
- [Model Selection Guide](#model-selection-guide)
- [Tuning Parameters](#tuning-parameters)
  - [CSP PFCT Parameter Tuning](#csp-pfct-parameter-tuning)
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
fluid.getPhase("gas").initPhysicalProperties();
fluid.getPhase("oil").initPhysicalProperties();
```

Select the model after the flash and initial property calculation, then reinitialize each changed
phase as shown. Model keys are case-sensitive. Unlike conductivity selection, an unsupported
viscosity key leaves the current model unchanged; verify the selected key in tests rather than
assuming a fallback.

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
fluid.getPhase("oil").initPhysicalProperties();
```

---

### Friction Theory

The Quiñones-Cisneros and Firoozabadi (2000) friction theory relates viscosity to the repulsive and attractive pressure contributions from the equation of state.

**Class:** `FrictionTheoryViscosityMethod`

**Equation:**
$$\eta = \eta_0 + \kappa_a P_a + \kappa_{aa} P_a^2 + \kappa_r P_r + \kappa_{rr} P_r^2$$

where:

- $\eta_0$ is the dilute gas viscosity (Chung low-density term + Wilke mixture rule)
- $P_a$ is the attractive pressure from EoS
- $P_r$ is the repulsive pressure from EoS
- $\kappa$ are friction coefficients (EoS-dependent)

**Applicable phases:** Gas, Oil (any EoS-based phase)

**Best for:**

- Wide pressure/temperature ranges
- Near-critical conditions
- When using SRK or PR EoS

**Automatic EoS detection:** The method automatically selects SRK or PR constants based on the phase type.

**Implementation notes in NeqSim (2026 update):**

- SRK and PR friction constants are selected automatically.
- The dilute-gas baseline now uses **Wilke mixing**, which is the standard state-of-practice for mixture dilute viscosities.
- The Chung correction factor $F_c$ includes acentric factor, dipole moment, and component viscosity correction term to better handle polar species.
- If friction theory is called for a non-EoS phase, NeqSim falls back to total pressure as repulsive contribution and zero attractive contribution, with a warning in logs.

**Usage:**
```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
fluid.getPhase("oil").initPhysicalProperties();
```

**Custom constants (advanced use):**
```java
FrictionTheoryViscosityMethod viscModel =
    (FrictionTheoryViscosityMethod) fluid.getPhase("oil")
        .getPhysicalProperties().getViscosityModel();

// Values must come from an independently validated parameter set.
viscModel.setFrictionTheoryConstants(kapac, kaprc, kaprrc, kapa, kapr, kaprr);
```

Here `kapac`, `kaprc`, `kaprrc`, and `kaprr` are scalar `double` values; `kapa` and
`kapr` are `double[][]` matrices. This API replaces the model's complete parameter set and is not
a general fitting shortcut.

---

### PFCT (Pedersen)

The Pedersen Friction Corresponding States Theory uses methane as a reference fluid with shape factors for mixture calculations.

The model can be selected with either `"PFCT"` or `"CSP"`. The `"CSP"` alias is intended for PVT workflows and external reports that label the Pedersen/PFCT viscosity model as CSP viscosity.

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
- PVT viscosity matching with four CSP correction factors

**Usage:**
```java
// Standard Pedersen
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT");
fluid.getPhase("oil").initPhysicalProperties();
```

Use `"CSP"` as an equivalent alias for `"PFCT"`, or select `"PFCT-Heavy-Oil"` for
characterized heavy fractions. Reinitialize the phase after any selection.

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
fluid.getPhase("oil").initPhysicalProperties();
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

The implemented case-sensitive keys and their constraints are:

| Key | Intended fluid |
|---|---|
| `MethaneModel` | Pure methane |
| `CO2Model` | Pure CO₂ |
| `Muzny`, `Muzny_mod` | Pure hydrogen |
| `KTA`, `KTA_mod` | Pure helium |
| `Salt Water` | Aqueous water/brine phase |

For example, select and recalculate a pure-hydrogen gas phase with:

```java
fluid.getPhase("gas").getPhysicalProperties().setViscosityModel("Muzny");
fluid.getPhase("gas").initPhysicalProperties();
```

---

## Model Selection Guide

| Application | Recommended Model | Notes |
|-------------|-------------------|-------|
| Reservoir simulation | LBC | Tunable, industry standard |
| Wide P-T range | Friction Theory | Good near critical |
| Heavy oils | PFCT-Heavy-Oil | Extended for high MW |
| Characterized crudes | PFCT | Works with pseudo-components |
| General gas processing | Friction theory or PFCT | Validate against representative data |
| Pure CO₂ | CO2Model | High accuracy |
| Pure H₂ | Muzny | Reference accuracy |
| Pure helium | KTA | Reference-fluid correlation |
| Aqueous systems | Salt Water | Water/brine correlation |

---

## Tuning Parameters

### LBC Parameter Tuning

The LBC method is commonly tuned to match laboratory viscosity data:

```java
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");

// Set all 5 parameters
double[] params = {0.1023, 0.023364, 0.058533, -0.040758, 0.0093324};
fluid.getPhase("oil").getPhysicalProperties().setLbcParameters(params);

// Or tune individual parameters
fluid.getPhase("oil").getPhysicalProperties().setLbcParameter(0, 0.105);
fluid.getPhase("oil").initPhysicalProperties();
```

**Tuning procedure:**

1. Measure viscosity at multiple P-T conditions
2. Run flash and calculate properties
3. Adjust parameters to minimize error
4. Validate at other conditions

### CSP PFCT Parameter Tuning

The PFCT/CSP viscosity model exposes four dimensionless CSP correction factors. All factors default to `1.0`, preserving the historical NeqSim calculation unless a user supplies tuned values. External PVT tools may report fitted vectors such as `0.6232`, `1.1507`, `1.0000`, `1.0000`; these can be supplied directly as the four-value CSP viscosity parameter vector.

| Index | Regression parameter | Corrected PFCT term |
|-------|----------------------|---------------------|
| 0 | `VISCOSITY_CSP_1` | Critical-temperature scaling exponent |
| 1 | `VISCOSITY_CSP_2` | Critical-pressure scaling exponent |
| 2 | `VISCOSITY_CSP_3` | Molar-mass scaling exponent |
| 3 | `VISCOSITY_CSP_4` | Alpha/molecular-weight correction exponent |

Use `PhysicalProperties.setCspViscosityParameters(double[])` to set all four parameters, `setCspViscosityParameter(int, double)` to tune one parameter, and `getCspViscosityParameters()` to read the active values. The longer `setCspViscosityCorrectionFactors` and `getCspViscosityCorrectionFactors` names are equivalent aliases.

For automated fitting, add viscosity measurements to `PVTRegression` with `addViscosityData(...)`, then register `RegressionParameter.VISCOSITY_CSP_1` through `VISCOSITY_CSP_4` individually or call `addCspViscosityRegressionParameters()` to fit all four together. Viscosity observations are expected in Pa s and can target gas, oil, liquid, aqueous, or water phases.

---

## Usage Examples

### Basic Viscosity Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create and flash fluid
SystemInterface fluid = new SystemSrkEos(298.15, 4.0);
fluid.addComponent("n-heptane", 0.5);
fluid.addComponent("nC10", 0.5);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initialize physical properties
fluid.initPhysicalProperties();
fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
fluid.getPhase("oil").initPhysicalProperties();

// Get viscosity
double oilVisc = fluid.getPhase("oil").getViscosity("cP");
if (!(oilVisc > 0.0) || !Double.isFinite(oilVisc)) {
    throw new IllegalStateException("Expected positive finite oil viscosity");
}
```

### Comparing Viscosity Models

```java
String[] models = {"LBC", "friction theory", "PFCT"};
double[] viscositiesCp = new double[models.length];

for (int i = 0; i < models.length; i++) {
    SystemInterface fluid = new SystemSrkEos(298.15, 4.0);
    fluid.addComponent("n-heptane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule("classic");
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();

    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel(models[i]);
    fluid.getPhase("oil").initPhysicalProperties();
    viscositiesCp[i] = fluid.getPhase("oil").getViscosity("cP");
}
```

### Viscosity vs Temperature

```java
SystemInterface baseFluid = new SystemSrkEos(300.0, 20.0);
baseFluid.addComponent("nC10", 1.0);
baseFluid.setMixingRule("classic");

double[] temps = {300, 320, 340, 360, 380, 400};  // K
double[] viscositiesCp = new double[temps.length];

for (int i = 0; i < temps.length; i++) {
    SystemInterface fluid = baseFluid.clone();
    fluid.setTemperature(temps[i], "K");

    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    fluid.initPhysicalProperties();
    fluid.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
    fluid.getPhase("oil").initPhysicalProperties();
    viscositiesCp[i] = fluid.getPhase("oil").getViscosity("cP");
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

Mixing behavior is model-specific. Common forms include logarithmic molar weighting:

$$\eta_{mix} = \exp\left(\sum_i x_i \ln \eta_i\right)$$

or the more rigorous:

$$\eta_{mix} = \frac{\sum_i x_i \sqrt{M_i} \eta_i}{\sum_i x_i \sqrt{M_i}}$$

---

## References

1. Lohrenz, J., Bray, B.G., Clark, C.R. (1964). Calculating Viscosities of Reservoir Fluids from Their Compositions. JPT.
2. Quiñones-Cisneros, S.E., Firoozabadi, A. (2000). One Parameter Friction Theory. AIChE J.
3. Pedersen, K.S., et al. (1987). Viscosity of Crude Oils. Chem. Eng. Sci.
4. Chung, T.H., et al. (1988). Generalized Multiparameter Correlation for Nonpolar and Polar Fluid Transport Properties. I&EC Res.
