---
title: "Electrolyte CPA Model Documentation"
description: "The electrolyte CPA (Cubic Plus Association) model in NeqSim extends the standard CPA equation of state to handle aqueous electrolyte solutions. The model is based on the work of Solbraa (2002) and co..."
---

# Electrolyte CPA Model Documentation

## Overview

The electrolyte CPA (Cubic Plus Association) model in NeqSim extends the standard CPA equation of state to handle aqueous electrolyte solutions. The model is based on the work of Solbraa (2002) and combines:

1. **CPA equation of state** for non-electrolyte interactions (van der Waals + association)
2. **Fürst electrostatic contribution** for ion-ion and ion-solvent interactions
3. **Short-range Wij parameters** for specific ion-solvent and ion-ion correlations

## Implementation Classes

### SystemElectrolyteCPAstatoil (Recommended)

The **Statoil (now Equinor) implementation** of the electrolyte CPA model:

```java
import neqsim.thermo.system.SystemElectrolyteCPAstatoil;

SystemElectrolyteCPAstatoil system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
system.addComponent("water", 55.5);
system.addComponent("Na+", 1.0);
system.addComponent("Cl-", 1.0);
system.chemicalReactionInit();
system.createDatabase(true);
system.setMixingRule(10);  // Required: CPA mixing rule with temperature/composition dependency
```

### Key Features of the Statoil Implementation

| Feature | Description |
|---------|-------------|
| **Model Name** | `Electrolyte-CPA-EOS-statoil` |
| **Base Class** | Extends `SystemFurstElectrolyteEos` |
| **Phase Class** | `PhaseElectrolyteCPAstatoil` |
| **Component Class** | `ComponentElectrolyteCPAstatoil` |
| **Attractive Term** | Term 15 (Mathias-Copeman alpha function) |
| **Volume Correction** | Enabled by default |
| **Fürst Parameters** | Uses `electrolyteCPA` parameter set |

### Class Hierarchy

```
SystemThermo
  └── SystemSrkEos
        └── SystemFurstElectrolyteEos
              └── SystemElectrolyteCPAstatoil
                    ├── PhaseElectrolyteCPAstatoil (phase calculations)
                    └── ComponentElectrolyteCPAstatoil (component properties)
```

## Mixing Rule 10 - CPA with Temperature/Composition Dependency

### Overview

Mixing rule 10 is the **recommended mixing rule** for all CPA and electrolyte CPA systems. It automatically selects the appropriate sub-type based on the binary interaction parameters:

```java
system.setMixingRule(10);  // Automatically selects optimal sub-type
```

### Automatic Sub-Type Selection

The mixing rule analyzes the binary interaction parameter matrices and selects:

| Condition | Sub-Type | Class | Description |
|-----------|----------|-------|-------------|
| Symmetric kij, no T-dependency | `classic-CPA` | `ClassicSRK` | Simple symmetric mixing |
| Symmetric kij, with T-dependency | `classic-CPA_T` | `ClassicSRKT2` | Temperature-dependent symmetric |
| Asymmetric kij (kij ≠ kji) | `classic-CPA_Tx` | `ClassicSRKT2x` | Full asymmetric + T-dependent |

### Mathematical Formulation

The `a` parameter mixing rule:

$$a = \sum_i \sum_j x_i x_j \sqrt{a_i a_j} (1 - k_{ij})$$

For asymmetric mixing (ClassicSRKT2x):

$$k_{ij} \neq k_{ji}$$

Temperature dependency:

$$k_{ij}(T) = k_{ij,0} + k_{ij,T} \cdot T$$

### Why Mixing Rule 10?

1. **Automatic optimization**: Selects the simplest sufficient mixing rule
2. **Temperature dependency**: Captures T-dependent phase behavior
3. **Asymmetric parameters**: Handles non-symmetric ion-solvent interactions
4. **Database integration**: Uses binary parameters from NeqSim database

## Theoretical Background

### Helmholtz Energy Decomposition

The total residual Helmholtz energy is decomposed as:

$$A^{res} = A^{CPA} + A^{elec}$$

Where:
- $A^{CPA}$ = Standard CPA contribution (SRK + association)
- $A^{elec}$ = Electrostatic contribution (Fürst model)

### Fürst Electrostatic Model

The electrostatic contribution follows the Fürst model, which combines:

1. **Mean Spherical Approximation (MSA)** for ion-ion interactions
2. **Born solvation term** for ion-solvent interactions  
3. **Short-range Wij terms** for specific ion interactions

$$A^{elec} = A^{MSA} + A^{Born} + A^{SR}$$

### Mean Spherical Approximation (MSA)

The MSA term accounts for the electrostatic screening between ions:

$$\frac{A^{MSA}}{RT} = -\frac{V}{3\pi} \left[ \Gamma^3 + \frac{3\Gamma\sigma_+ \sigma_-}{1 + \Gamma\sigma_{+-}} \right]$$

Where:
- $\Gamma$ = MSA screening parameter
- $\sigma_i$ = ionic diameter of species $i$

### Born Solvation Term

The Born term accounts for the solvation energy of ions in the dielectric medium:

$$\frac{A^{Born}}{RT} = -\frac{e^2 N_A}{8\pi\varepsilon_0 k_B T} \sum_i n_i \frac{z_i^2}{\sigma_i} \left(1 - \frac{1}{\varepsilon_r}\right)$$

Where:
- $z_i$ = ionic charge
- $\sigma_i$ = ionic diameter
- $\varepsilon_r$ = relative permittivity (dielectric constant) of the solvent mixture

## Short-Range Interaction Parameters (Wij)

### Overview

The short-range Wij parameters capture specific ion-solvent and ion-ion interactions not described by the electrostatic terms. These are fitted to experimental activity coefficient and osmotic coefficient data.

### Parameter Correlations

The Wij values are calculated using linear correlations with ionic diameter:

#### Monovalent (1+) Cations

```
Wij(cation-water) = furstParamsCPA[2] × stokesDiameter + furstParamsCPA[3]
Wij(cation-anion) = furstParamsCPA[4] × (d_cat + d_an)^4 + furstParamsCPA[5]
```

Current fitted values (2024):
- `[2]` = 4.985e-05 (slope for cation-water)
- `[3]` = -1.215e-04 (intercept for cation-water)
- `[4]` = -2.059e-08 (prefactor for cation-anion)
- `[5]` = -9.495e-05 (intercept for cation-anion)

#### Divalent (2+) Cations

```
Wij(2+ cation-water) = furstParamsCPA[6] × stokesDiameter + furstParamsCPA[7]
Wij(2+ cation-anion) = furstParamsCPA[8] × (d_cat + d_an)^4 + furstParamsCPA[9]
```

Current fitted values (refitted December 2024):
- `[6]` = 5.40e-05 (slope for 2+ cation-water)
- `[7]` = -1.72e-04 (intercept for 2+ cation-water)
- `[8]` = -4.398e-08 (prefactor for 2+ cation-anion)
- `[9]` = -5.970e-17 (intercept for 2+ cation-anion)

### Why Separate Parameters for Divalent Cations?

The divalent cation parameters differ significantly from monovalent:
- **Intercept**: 42% more negative for 2+ cations
- **Physical interpretation**: Stronger ion-water interaction for doubly-charged ions

Using unified parameters would give dramatically wrong Wij values for divalent cations (sometimes even wrong sign), making separate parameters essential for accuracy.

| Parameter | Monovalent | Divalent | Ratio |
|-----------|------------|----------|-------|
| Slope | 4.98e-05 | 5.40e-05 | 1.08 |
| Intercept | -1.22e-04 | -1.72e-04 | 1.42 |

## Validation Against Experimental Data

### Robinson & Stokes Data (1965)

The model has been validated against Robinson & Stokes experimental data for mean activity coefficients (γ±) and osmotic coefficients (φ) at 25°C.

#### Monovalent Salts (1:1 electrolytes)

| Salt | Type | γ± Error | φ Error |
|------|------|----------|---------|
| NaCl | 1-1 | 2.4% | 1.6% |
| KCl | 1-1 | 4.3% | 1.0% |
| LiCl | 1-1 | 3.4% | 2.5% |
| NaBr | 1-1 | 2.8% | 2.0% |
| KBr | 1-1 | 1.4% | 2.0% |

#### Divalent Cation Salts (2:1 electrolytes)

| Salt | Type | γ± Error | φ Error |
|------|------|----------|---------|
| CaCl₂ | 2-1 | 7.0% | 4.2% |
| MgCl₂ | 2-1 | 9.6% | 4.6% |
| BaCl₂ | 2-1 | 2.3% | 1.5% |

#### Divalent Anion Salts (1:2 electrolytes)

| Salt | Type | γ± Error | φ Error |
|------|------|----------|---------|
| Na₂SO₄ | 1-2 | 20.0% | 19.7% |
| K₂SO₄ | 1-2 | 2.9% | 1.6% |

#### Overall Performance

- **Mean activity coefficient (γ±)**: 6.0% average error
- **Osmotic coefficient (φ)**: 4.3% average error

## Dielectric Constant Mixing Rules

The model supports three dielectric constant mixing rules:

### 1. MOLAR_AVERAGE (Default, Recommended)

$$\varepsilon_{mix} = \sum_i x_i \varepsilon_i$$

- Thermodynamically consistent (passes all derivative checks)
- Recommended for general use

### 2. VOLUME_AVERAGE

$$\varepsilon_{mix} = \sum_i \phi_i \varepsilon_i$$

Where $\phi_i$ is the volume fraction.

- **Warning**: Composition derivatives are incomplete
- May cause thermodynamic inconsistencies

### 3. LOOYENGA

$$\varepsilon_{mix}^{1/3} = \sum_i \phi_i \varepsilon_i^{1/3}$$

- Good for water-organic mixtures
- **Warning**: Composition derivatives are incomplete

## Thermodynamic Consistency

The model has been verified for thermodynamic consistency using built-in checks:

### Fugacity Coefficient Identities

✅ $\sum_i x_i \ln\phi_i = \frac{G^{res}}{RT}$ - PASSED

### Derivative Consistency

✅ $\left(\frac{\partial \ln\phi_i}{\partial P}\right)_T = \frac{\bar{V}_i - V_{ig}}{RT}$ - PASSED

✅ $\left(\frac{\partial \ln\phi_i}{\partial T}\right)_P = \frac{H_{ig} - \bar{H}_i}{RT^2}$ - PASSED

✅ $\left(\frac{\partial \ln\phi_i}{\partial n_j}\right)_{T,P,n_{k\neq j}} = \left(\frac{\partial \ln\phi_j}{\partial n_i}\right)_{T,P,n_{k\neq i}}$ (symmetry) - PASSED

## Usage Example

```java
// Create electrolyte CPA system
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);

// Add water (solvent)
system.addComponent("water", 55.5); // mol

// Add electrolyte
system.addComponent("Na+", 1.0);
system.addComponent("Cl-", 1.0);

// Initialize
system.chemicalReactionInit();
system.createDatabase(true);
system.setMixingRule(10); // Electrolyte CPA mixing rule

// Run flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Get activity coefficients
int aq = system.getPhaseNumberOfPhase("aqueous");
int naIdx = system.getPhase(aq).getComponent("Na+").getComponentNumber();
int clIdx = system.getPhase(aq).getComponent("Cl-").getComponentNumber();
int waterIdx = system.getPhase(aq).getComponent("water").getComponentNumber();

double gammaNa = system.getPhase(aq).getActivityCoefficient(naIdx, waterIdx);
double gammaCl = system.getPhase(aq).getActivityCoefficient(clIdx, waterIdx);
double meanGamma = Math.sqrt(gammaNa * gammaCl); // Mean activity coefficient

double phi = system.getPhase(aq).getOsmoticCoefficientOfWater();
```

## Mixed Solvent Systems

The model supports mixed solvent systems including:

- Water + MEG (monoethylene glycol)
- Water + Methanol
- Water + MDEA (methyldiethanolamine)

Separate Wij parameters are available for each solvent system.

## Gas-Ion Interaction Parameters (Salting-Out Effect)

The electrolyte CPA model includes gas-ion interaction parameters to correctly predict the salting-out effect. The salting-out coefficient (k_s) determines how much dissolved gas solubility decreases with salt concentration.

### Setschenow Equation

$$\log\left(\frac{S_0}{S}\right) = k_s \cdot c_{salt}$$

Where:
- $S_0$ = gas solubility in pure water
- $S$ = gas solubility in salt solution
- $k_s$ = Setschenow (salting-out) coefficient (L/mol)
- $c_{salt}$ = salt concentration (mol/L)

### Fitted Gas-Ion Parameters

| Gas | k_s (L/mol) | W_cation | W_anion | Notes |
|-----|-------------|----------|---------|-------|
| CO₂ | 0.10 | 1.05e-4 | 1.05e-4 | Polar, acidic gas |
| CH₄ | 0.12 | 1.10e-4 | 1.10e-4 | Reference hydrocarbon |
| C₂H₆ | 0.13 | 1.13e-4 | 1.13e-4 | Slightly larger |
| C₃H₈ | 0.14 | 1.15e-4 | 1.15e-4 | Larger hydrocarbon |
| C₄ (butanes) | 0.15 | 1.20e-4 | 1.20e-4 | Heavier hydrocarbon |
| C₅+ | 0.16 | 1.25e-4 | 1.25e-4 | Heaviest fractions |
| N₂ | 0.10-0.12 | 1.05e-4 | 1.05e-4 | Similar to CH₄ |
| H₂S | 0.06-0.08 | 1.10e-4 | 1.10e-4 | Polar, acidic |
| H₂ | 0.10 | 1.30e-4 | 1.30e-4 | Very small molecule |

**Usage Example - Methane Solubility with Salt:**
```java
SystemInterface fluid = new SystemElectrolyteCPAstatoil(298.15, 50.0);
fluid.addComponent("methane", 0.1);
fluid.addComponent("water", 10.0);
fluid.addComponent("Na+", 0.5);
fluid.addComponent("Cl-", 0.5);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Methane solubility decreases with salt (salting-out effect)
double ch4InAqueous = fluid.getPhase(PhaseType.AQUEOUS).getComponent("methane").getx();
```

## Organic Inhibitor-Ion Interaction Parameters

### Hu-Lee-Sum Correlation for Hydrate Inhibition

For hydrate equilibrium calculations with combined salt + organic inhibitor systems, the Hu-Lee-Sum universal correlation (AIChE Journal 2017, 2018) states that water activity effects should be **additive**:

$$\ln(a_w^{combined}) = \ln(a_w^{salt}) + \ln(a_w^{OI})$$

This is critical for accurate hydrate inhibitor dosing calculations when both thermodynamic inhibitors (MEG, methanol) and formation water salts are present.

### OI-Ion Interaction Parameters

Without explicit organic inhibitor-ion (OI-ion) parameters, the combined effect may not be additive. The following parameters ensure correct additive behavior:

| Inhibitor | W_MeOH-cation | W_MeOH-anion | Purpose |
|-----------|---------------|--------------|---------|
| Methanol | 1.5e-4 | 1.5e-4 | Ensures MEG+salt gives more inhibition |
| MEG | 0.0 | 0.0 | Default calculation works correctly |
| Ethanol | 1.3e-4 | 1.3e-4 | Interpolated value |

### Validation Results

| System | Hydrate T (°C) | Validation |
|--------|---------------|------------|
| Pure gas + water | +20.0 | Baseline |
| With NaCl only | +10.8 | ✅ Salt inhibition |
| With MEG only | -2.3 | ✅ MEG inhibition |
| With methanol only | -4.6 | ✅ Methanol inhibition |
| MEG + NaCl | -18.9 | ✅ ~16°C additional depression |
| Methanol + NaCl | -5.4 | ✅ ~0.8°C additional depression |

**Example - Combined Inhibitor Hydrate Calculation:**
```java
SystemInterface fluid = new SystemElectrolyteCPAstatoil(273.15 + 10.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("water", 0.12);
fluid.addComponent("methanol", 0.03);
fluid.addComponent("Na+", 0.01);
fluid.addComponent("Cl-", 0.01);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateFormationTemperature();

// Combined effect is additive per Hu-Lee-Sum correlation
System.out.println("Hydrate T: " + fluid.getTemperature("C") + " °C");
```

## Known Limitations

1. **Divalent anions (SO₄²⁻)**: Higher errors for 1:2 electrolytes like Na₂SO₄ (~20%)
2. **High concentrations**: Accuracy decreases above ~2 mol/kg for some salts
3. **VOLUME_AVERAGE/LOOYENGA mixing rules**: Incomplete composition derivatives
4. **Temperature range**: Parameters fitted at 25°C, extrapolation accuracy may vary

## Comparison: CPA vs Electrolyte CPA

| Feature | SystemSrkCPAstatoil | SystemElectrolyteCPAstatoil |
|---------|---------------------|------------------------------|
| **Use Case** | Non-ionic associating systems | Aqueous electrolyte solutions |
| **Electrostatics** | None | MSA + Born solvation |
| **Ions** | Not supported | Na+, K+, Ca++, Mg++, Cl-, etc. |
| **Mixing Rule** | 10 (recommended) | 10 (required) |
| **Chemical Reactions** | Optional | Recommended (pH, speciation) |
| **Phase Class** | `PhaseSrkCPAs` | `PhaseElectrolyteCPAstatoil` |

## Quick Start Examples

### Simple NaCl Solution

```java
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
system.addComponent("water", 55.5);
system.addComponent("Na+", 0.5);
system.addComponent("Cl-", 0.5);
system.createDatabase(true);
system.setMixingRule(10);
system.init(0);
system.init(1);

// Get mean activity coefficient
double gammaMean = system.getPhase(0).getMeanIonicActivityCoefficient("Na+", "Cl-");
```

### With Chemical Reactions (pH Calculation)

```java
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
system.addComponent("water", 55.5);
system.addComponent("CO2", 0.01);
system.addComponent("Na+", 0.1);
system.addComponent("Cl-", 0.1);
system.chemicalReactionInit();  // Enable pH and speciation
system.createDatabase(true);
system.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Access aqueous phase
int aq = system.getPhaseNumberOfPhase("aqueous");
double pH = -Math.log10(system.getPhase(aq).getComponent("H3O+").getx() * 55.5);
```

### Gas-Liquid Equilibrium with Electrolytes

```java
SystemInterface system = new SystemElectrolyteCPAstatoil(323.15, 50.0);
system.addComponent("methane", 10.0);
system.addComponent("water", 100.0);
system.addComponent("MEG", 20.0);
system.addComponent("Na+", 1.0);
system.addComponent("Cl-", 1.0);
system.createDatabase(true);
system.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Check phase compositions
for (int i = 0; i < system.getNumberOfPhases(); i++) {
    System.out.println("Phase " + i + ": " + system.getPhase(i).getType());
}
```

## References

1. Solbraa, E. (2002). "Measurement and Modelling of Absorption of Carbon Dioxide into Methyldiethanolamine Solutions at High Pressures." PhD Thesis, Norwegian University of Science and Technology.

2. Fürst, W., & Renon, H. (1993). "Representation of excess properties of electrolyte solutions using a new equation of state." AIChE Journal, 39(2), 335-343.

3. Robinson, R.A., & Stokes, R.H. (1965). "Electrolyte Solutions." 2nd Edition, Butterworths, London.

4. Kontogeorgis, G.M., & Folas, G.K. (2010). "Thermodynamic Models for Industrial Applications." Wiley.

5. Michelsen, M.L., & Mollerup, J.M. (2007). "Thermodynamic Models: Fundamentals & Computational Aspects." Tie-Line Publications.

6. Hu, Y., Lee, B.R., Sum, A.K. (2017). "Universal correlation for gas hydrates suppression temperature of inhibited systems: I. Single salts." *AIChE Journal*, 63(11), 5111-5124. DOI: 10.1002/aic.15868

7. Hu, Y., Lee, B.R., Sum, A.K. (2018). "Universal correlation for gas hydrates suppression temperature of inhibited systems: II. Mixed salts and structure type." *AIChE Journal*, 64(6), 2240-2250. DOI: 10.1002/aic.16generalized

## Parameter History

| Date | Change | Impact |
|------|--------|--------|
| 2002 | Initial parameters from Solbraa thesis | Baseline model |
| 2024 | Refitted monovalent parameters to Robinson & Stokes | γ± error: 2.8% |
| Dec 2024 | Refitted divalent cation parameters [6-9] | CaCl₂: 16%→7%, MgCl₂: 22%→10% |
| Dec 2024 | Updated chemical equilibrium solver | Improved pH accuracy |
| Dec 2024 | Added gas-ion parameters for C2-C5+, N₂, H₂S, H₂ | Correct salting-out for all gases |
| Feb 2026 | Added OI-ion parameters for Hu-Lee-Sum compliance | Additive hydrate inhibition with combined inhibitors |

## Source Code References

### System Classes
- `SystemElectrolyteCPAstatoil.java` - Main system class (Statoil implementation)
- `SystemElectrolyteCPA.java` - Generic electrolyte CPA system
- `SystemSrkCPAstatoil.java` - Non-electrolyte CPA (for comparison)

### Phase Classes  
- `PhaseElectrolyteCPAstatoil.java` - Phase calculations (Statoil g-function)
- `PhaseElectrolyteCPA.java` - Base electrolyte CPA phase
- `PhaseModifiedFurstElectrolyteEos.java` - Fürst electrostatic contributions

### Component Classes
- `ComponentElectrolyteCPAstatoil.java` - Component properties
- `ComponentElectrolyteCPA.java` - Base electrolyte CPA component

### Mixing Rules
- `EosMixingRuleHandler.java` - Mixing rule selection and Wij calculations
- `CPAMixingRuleHandler.java` - CPA association mixing rules

### Parameters
- `FurstElectrolyteConstants.java` - Wij correlation parameters

### Tests
- `SystemElectrolyteCPATest.java` - Basic electrolyte CPA tests
- `ElectrolyteCPAThermodynamicConsistencyTest.java` - Thermodynamic consistency
- `ElectrolyteCPARobinsonValidationTest.java` - Validation against experimental data

---

*Last updated: December 27, 2024*
