# Hydrate Models in NeqSim

This document describes the gas hydrate thermodynamic models implemented in NeqSim for predicting hydrate formation, stability, and phase equilibrium.

## Table of Contents

- [Overview](#overview)
- [Hydrate Structures](#hydrate-structures)
- [Thermodynamic Framework](#thermodynamic-framework)
- [Available Hydrate Models](#available-hydrate-models)
  - [van der Waals-Platteeuw Model](#van-der-waals-platteeuw-model)
  - [CPA Hydrate Model (Statoil/Equinor)](#cpa-hydrate-model-statoilequinor)
  - [PVTsim Hydrate Model](#pvtsim-hydrate-model)
  - [Ballard Model](#ballard-model)
  - [Kluda Model](#kluda-model)
- [Component Parameters](#component-parameters)
- [Hydrate Inhibitors](#hydrate-inhibitors)
- [Usage Examples](#usage-examples)
- [References](#references)

---

## Overview

Gas hydrates (clathrate hydrates) are ice-like crystalline compounds formed when water molecules create cage structures that encapsulate gas molecules (guest molecules) under high pressure and low temperature conditions. NeqSim provides comprehensive models for:

- Hydrate phase equilibrium calculations
- Hydrate formation temperature and pressure prediction
- Multi-phase flash with hydrate phase
- Hydrate inhibitor dosing calculations
- Cavity occupancy calculations

---

## Hydrate Structures

NeqSim supports two common hydrate crystal structures:

### Structure I (sI)

| Property | Small Cavity (5¹²) | Large Cavity (5¹²6²) |
|----------|-------------------|---------------------|
| Coordination Number | 20 | 24 |
| Cavity Radius (Å) | 3.95 | 4.33 |
| Number per Unit Cell | 2 | 6 |
| Water per Cavity | 1/23 | 3/23 |

**Typical Guest Molecules:** Methane, ethane, CO₂, H₂S

**Unit Cell Formula:** 46 H₂O · 8 guest molecules (2 small + 6 large cavities)

### Structure II (sII)

| Property | Small Cavity (5¹²) | Large Cavity (5¹²6⁴) |
|----------|-------------------|---------------------|
| Coordination Number | 20 | 28 |
| Cavity Radius (Å) | 3.91 | 4.73 |
| Number per Unit Cell | 16 | 8 |
| Water per Cavity | 2/17 | 1/17 |

**Typical Guest Molecules:** Propane, isobutane, natural gas mixtures

**Unit Cell Formula:** 136 H₂O · 24 guest molecules (16 small + 8 large cavities)

### Structure Selection

The algorithm automatically selects the most stable structure based on Gibbs energy minimization. For mixed gases, the structure depends on composition:

```java
// Get the stable hydrate structure (1 = sI, 2 = sII)
int structure = fluid.getPhase(PhaseType.HYDRATE).getComponent("methane").getHydrateStructure();
```

---

## Thermodynamic Framework

### van der Waals-Platteeuw Theory

NeqSim implements the classical van der Waals-Platteeuw (vdWP) statistical thermodynamic model as the foundation for hydrate calculations.

The chemical potential difference between water in hydrate and empty hydrate lattice:

$$\Delta\mu_w^H = -RT \sum_{i=1}^{N_{cav}} \nu_i \ln\left(1 - \sum_{j=1}^{N_g} Y_{ij}\right)$$

where:
- $\nu_i$ = number of type $i$ cavities per water molecule
- $Y_{ij}$ = fractional occupancy of guest $j$ in cavity type $i$

### Cavity Occupancy (Langmuir Adsorption)

The cavity occupancy follows the Langmuir isotherm:

$$Y_{ij} = \frac{C_{ij} f_j}{1 + \sum_{k=1}^{N_g} C_{ik} f_k}$$

where:
- $C_{ij}$ = Langmuir constant for guest $j$ in cavity $i$
- $f_j$ = fugacity of guest component $j$

### Langmuir Constants

The Langmuir constants are calculated from the cell potential:

$$C_{ij}(T) = \frac{4\pi}{k_B T} \int_0^{R_{cell}} \exp\left(-\frac{w(r)}{k_B T}\right) r^2 dr$$

where $w(r)$ is the spherically averaged Kihara cell potential.

---

## Available Hydrate Models

### CPA Hydrate Model (Statoil/Equinor)

**Class:** `ComponentHydrateGF`, `ComponentHydrateStatoil`

The CPA (Cubic Plus Association) hydrate model is the recommended model for systems containing polar components like water, MEG, and methanol. It uses the CPA equation of state for fugacity calculations.

**Key Features:**
- Accurate for inhibitor systems (MEG, methanol, ethanol)
- Consistent with CPA mixing rules
- Validated for North Sea gas compositions

**Usage:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15, 100.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("water", 0.1);
fluid.setMixingRule(10);  // CPA mixing rule
fluid.setHydrateCheck(true);
```

### PVTsim Hydrate Model

**Class:** `ComponentHydratePVTsim`

The default hydrate model based on the PVTsim approach, suitable for hydrocarbon-water systems.

**Key Features:**
- Fast convergence
- Good accuracy for typical natural gas compositions
- Extensive validation against experimental data

**Usage:**
```java
SystemInterface fluid = new SystemSrkEos(273.15, 100.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("water", 0.1);
fluid.setMixingRule("classic");
fluid.setHydrateCheck(true);
```

### Ballard Model

**Class:** `ComponentHydrateBallard`

Based on the work of Ballard (2002), this model uses an improved approach for Langmuir constant calculation.

### Kluda Model

**Class:** `ComponentHydrateKluda`

Alternative hydrate model with different parameterization for specific applications.

---

## Component Parameters

Hydrate-specific parameters are stored in the component database:

| Parameter | Description | Unit |
|-----------|-------------|------|
| `LJdiameterHYDRATE` | Lennard-Jones diameter for hydrate cavity | Å |
| `LJepsHYDRATE` | Lennard-Jones energy parameter | K |
| `sphericalCoreRadius` | Kihara spherical core radius | Å |

### Hydrate Formers

Components that can occupy hydrate cavities (hydrate formers):

| Component | Structure | Small Cavity | Large Cavity |
|-----------|-----------|--------------|--------------|
| Methane | sI, sII | ✓ | ✓ |
| Ethane | sI | - | ✓ |
| Propane | sII | - | ✓ |
| i-Butane | sII | - | ✓ |
| CO₂ | sI | ✓ | ✓ |
| H₂S | sI, sII | ✓ | ✓ |
| Nitrogen | sII | ✓ | ✓ |

Check if a component is a hydrate former:
```java
boolean isFormer = fluid.getPhase(0).getComponent("methane").isHydrateFormer();
```

---

## Hydrate Inhibitors

NeqSim supports thermodynamic hydrate inhibitors that shift the hydrate equilibrium curve:

### Supported Inhibitors

| Inhibitor | Common Name | Effect |
|-----------|-------------|--------|
| MEG | Monoethylene glycol | Lowers hydrate temperature |
| TEG | Triethylene glycol | Lowers hydrate temperature |
| methanol | Methanol | Strong temperature depression |
| ethanol | Ethanol | Moderate temperature depression |
| NaCl | Salt | Salinity effect |

### Inhibitor Calculations

```java
// Calculate required MEG concentration for target temperature
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 80.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("water", 0.08);
fluid.addComponent("MEG", 0.02);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Calculate inhibitor concentration needed to prevent hydrate at 5°C
ops.hydrateInhibitorConcentration("MEG", 273.15 + 5.0);
double requiredMEGwt = fluid.getPhase("aqueous").getComponent("MEG").getwtfrac();
```

---

## Usage Examples

### Basic Hydrate Check

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create fluid at potential hydrate conditions
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("water", 0.03);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);  // Enable hydrate phase

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Calculate hydrate formation temperature
ops.hydrateFormationTemperature();
System.out.println("Hydrate formation T: " + fluid.getTemperature("C") + " °C");
```

### Hydrate Equilibrium Curve

```java
// Generate hydrate PT curve
double[] pressures = {10, 20, 50, 100, 150, 200}; // bar
System.out.println("P (bar)\tT_hydrate (°C)");

for (double P : pressures) {
    fluid.setPressure(P);
    fluid.setTemperature(280.0);  // Initial guess
    ops.hydrateFormationTemperature();
    System.out.println(P + "\t" + fluid.getTemperature("C"));
}
```

### Cavity Occupancy

```java
// Get cavity occupancy for each guest molecule
PhaseInterface hydratePhase = fluid.getPhase(PhaseType.HYDRATE);

for (int i = 0; i < hydratePhase.getNumberOfComponents(); i++) {
    if (hydratePhase.getComponent(i).isHydrateFormer()) {
        ComponentHydrate comp = (ComponentHydrate) hydratePhase.getComponent(i);
        double smallCavity = comp.calcYKI(0, 0, hydratePhase);  // Structure I, small cavity
        double largeCavity = comp.calcYKI(0, 1, hydratePhase);  // Structure I, large cavity
        System.out.println(comp.getName() + 
            " - Small: " + smallCavity + ", Large: " + largeCavity);
    }
}
```

---

## References

1. van der Waals, J.H., Platteeuw, J.C. (1959). "Clathrate Solutions." *Advances in Chemical Physics*, 2, 1-57.

2. Sloan, E.D., Koh, C.A. (2008). *Clathrate Hydrates of Natural Gases*, 3rd ed. CRC Press.

3. Ballard, A.L., Sloan, E.D. (2002). "The next generation of hydrate prediction: I. Hydrate standard states and incorporation of spectroscopy." *Fluid Phase Equilibria*, 194-197, 371-383.

4. Kontogeorgis, G.M., et al. (2006). "Ten Years with the CPA (Cubic-Plus-Association) Equation of State." *Industrial & Engineering Chemistry Research*, 45, 4855-4868.

5. Munck, J., Skjold-Jørgensen, S., Rasmussen, P. (1988). "Computations of the formation of gas hydrates." *Chemical Engineering Science*, 43, 2661-2672.

---

## Related Documentation

- [Hydrate Flash Operations](../thermodynamicoperations/hydrate_flash_operations.md) - Detailed flash calculations
- [Flash Calculations Guide](flash_calculations_guide.md) - General flash operations
- [CPA Equation of State](ElectrolyteCPAModel.md) - CPA model details
- [Component Database Guide](component_database_guide.md) - Component parameters
