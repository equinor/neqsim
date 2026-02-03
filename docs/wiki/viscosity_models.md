---
title: "Viscosity Models in NeqSim"
description: "NeqSim provides a comprehensive suite of methods for calculating fluid viscosity, ranging from standard empirical correlations to advanced corresponding states models and specialized pure-component eq..."
---

# Viscosity Models in NeqSim

NeqSim provides a comprehensive suite of methods for calculating fluid viscosity, ranging from standard empirical correlations to advanced corresponding states models and specialized pure-component equations. This document details the available models, their applications, and how to use them in simulations.

## Overview

NeqSim viscosity models are organized into several categories:

| Category | Models | Applicability |
|----------|--------|---------------|
| **General Purpose** | LBC, PFCT, Friction Theory | Hydrocarbon mixtures, reservoir fluids |
| **Pure Component** | Muzny, MethaneModel, CO2Model, KTA | Specialized high-accuracy correlations |
| **Aqueous Systems** | Salt Water (Laliberté), polynom | Brine and water solutions |
| **Heavy Oils** | PFCT-Heavy-Oil | Viscous crude oils, bitumen |

---

## Available Models

### 1. Lohrenz-Bray-Clark (LBC)
The **LBC** method is the industry-standard correlation for calculating viscosity of reservoir fluids. It combines a low-pressure gas viscosity term with a dense-fluid contribution based on reduced density.

*   **Keyword**: `"LBC"`
*   **Best for**: General oil and gas systems, reservoir fluids, PVT matching.
*   **Features**:
    *   **Tunable Parameters**: Five dense-fluid polynomial coefficients ($a_0$ to $a_4$) can be adjusted to match laboratory data.
    *   **Whitson Consistency**: Implementation aligned with the standard "Whitson" interpretation.
    *   **Linear Mixing Rule**: Critical volume uses linear summation ($\sum x_i V_{ci}$) for better heavy component handling.
    *   **Unit Verified**: Internal pressure and viscosity unit conversions rigorously verified against literature.

### 2. Corresponding States Principle (CSP/PFCT)
The **CSP** method (referred to as PFCT in NeqSim) uses the Corresponding States Principle to relate mixture viscosity to a reference substance (typically Methane) at corresponding thermodynamic conditions.

*   **Keyword**: `"PFCT"`
*   **Best for**: Light to medium hydrocarbon mixtures, natural gas.
*   **Features**:
    *   Uses methane as reference fluid with well-characterized viscosity.
    *   Includes shape factor corrections for non-spherical molecules.

### 3. Corresponding States Principle for Heavy Oil
A variant of the CSP model specifically tuned for heavy oil systems with additional terms to represent the viscous behavior of heavy fractions.

*   **Keyword**: `"PFCT-Heavy-Oil"`
*   **Best for**: Heavy oils, systems with significant TBP (True Boiling Point) fractions, bitumen.

### 4. Friction Theory
The **Friction Theory** (f-theory) model links viscosity to the equation of state (EOS) by separating total viscosity into a dilute gas contribution and a residual friction contribution.

*   **Keyword**: `"friction theory"`
*   **Best for**: Wide range of fluids, consistent with EOS thermodynamics, high-pressure applications.
*   **Features**:
    *   Thermodynamically consistent with the EOS used for phase equilibrium.
    *   Robust near critical point and at high pressures.
    *   Uses Chung correlation for dilute-gas contribution.

### 5. Chung Method (Gas Phase)
The **Chung** method is a corresponding states correlation for gas-phase viscosity based on the Chapman-Enskog kinetic theory with empirical corrections.

*   **Keyword**: Used internally by gas phase physical properties.
*   **Best for**: Low-density gas mixtures.
*   **Features**:
    *   Wilke mixing rules for multicomponent systems.
    *   Correction factors for polar and associating molecules.

### 6. Lee-Gonzalez-Eakin Correlation
A simple empirical correlation for natural gas viscosity estimation.

*   **Keyword**: Used as reference in LBC calculations.
*   **Best for**: Quick first-order estimates for natural gas.
*   **Reference**: Lee, Gonzalez, and Eakin, SPE-1340-PA, 1966.

---

## Pure Component Models

### 7. Muzny Hydrogen Viscosity
High-accuracy correlation for **pure hydrogen** viscosity based on the work of Muzny et al. Includes dilute-gas, first-density, and higher-density contributions.

*   **Keyword**: `"Muzny"`
*   **Best for**: Pure hydrogen systems across wide temperature and pressure ranges.
*   **Limitations**: Only valid for pure hydrogen - throws error for mixtures.
*   **Features**:
    *   Uses Leachman equation of state for density.
    *   Valid from gas to dense fluid phases.

### 8. Muzny Modified Hydrogen Viscosity
Extended version of the Muzny correlation with additional correction terms for improved accuracy at specific conditions.

*   **Keyword**: `"Muzny_mod"`
*   **Best for**: Pure hydrogen with enhanced accuracy at certain T-P conditions.

### 9. Methane Viscosity Model
Specialized correlation for **pure methane** viscosity using LBC as base with empirical correction terms.

*   **Keyword**: `"MethaneModel"`
*   **Best for**: Pure methane systems, LNG applications.
*   **Limitations**: Only valid for pure methane - throws error for mixtures.

### 10. CO2 Viscosity Model
Reference-quality correlation for **pure carbon dioxide** based on Laesecke et al. (JPCRD 2017).

*   **Keyword**: `"CO2Model"`
*   **Best for**: Pure CO2 systems, CCS applications, supercritical CO2.
*   **Limitations**: Only valid for pure CO2 - throws error for mixtures.
*   **Features**:
    *   Dilute-gas and residual contributions from JPCRD 2017.
    *   Uses Span-Wagner EOS for density when needed.

### 11. KTA Helium Viscosity
Simple power-law correlation for **pure helium** viscosity.

*   **Keyword**: `"KTA"`
*   **Best for**: Pure helium systems, cryogenic applications.
*   **Limitations**: Only valid for pure helium.
*   **Reference**: Based on KTA (Kerntechnischer Ausschuss) standard.

### 12. KTA Modified Helium Viscosity
Extended KTA model with pressure-dependent corrections for improved high-pressure accuracy.

*   **Keyword**: `"KTA_mod"`
*   **Best for**: Pure helium at elevated pressures.

---

## Aqueous System Models

### 13. Salt Water (Laliberté)
Viscosity correlation for aqueous salt solutions using the Laliberté (2007) model with erratum corrections.

*   **Keyword**: `"Salt Water"`
*   **Best for**: Brine systems, produced water, salt solutions.
*   **Supported Salts**:
    *   NaCl (sodium chloride)
    *   KCl (potassium chloride)
    *   KCOOH (potassium formate)
    *   NaBr (sodium bromide)
    *   CaCl2 (calcium chloride)
    *   KBr (potassium bromide)
*   **Reference**: G. Laliberté, Ind. Eng. Chem. Res., 2007, 46, 8865-8872.

### 14. Polynomial Liquid Viscosity
General liquid viscosity calculation using the Grunberg-Nissan mixing rule with pure-component correlations.

*   **Keyword**: `"polynom"`
*   **Best for**: Liquid mixtures where component viscosity parameters are available in database.

## Usage in NeqSim

To use a specific viscosity model, you must set it on the `PhysicalProperties` object of a phase. This is typically done after creating the system but before performing calculations.

### Java Example

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class ViscosityExample {
    public static void main(String[] args) {
        // 1. Create a system
        SystemInterface system = new SystemSrkEos(298.15, 100.0); // 298.15 K, 100 bar
        system.addComponent("methane", 0.5);
        system.addComponent("n-heptane", 0.5);
        
        // 2. Set mixing rule and initialize
        system.setMixingRule("classic");
        ThermodynamicOperations ops = new ThermodynamicOperations(system);
        ops.TPflash();

        // 3. Set Viscosity Model for a specific phase (e.g., oil/liquid)
        // Available options: "LBC", "PFCT", "PFCT-Heavy-Oil", "friction theory"
        
        // Example: Using LBC
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("LBC");
        system.initProperties();
        double lbcViscosity = system.getPhase("oil").getViscosity();
        System.out.println("LBC Viscosity: " + lbcViscosity + " kg/(m*s)");

        // Example: Using PFCT
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT");
        system.initProperties();
        double pfctViscosity = system.getPhase("oil").getViscosity();
        System.out.println("PFCT Viscosity: " + pfctViscosity + " kg/(m*s)");
        
        // Example: Using PFCT for Heavy Oil
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("PFCT-Heavy-Oil");
        system.initProperties();
        double pfctHeavyViscosity = system.getPhase("oil").getViscosity();
        System.out.println("PFCT Heavy Oil Viscosity: " + pfctHeavyViscosity + " kg/(m*s)");

        // Example: Using Friction Theory
        system.getPhase("oil").getPhysicalProperties().setViscosityModel("friction theory");
        system.initProperties();
        double frictionViscosity = system.getPhase("oil").getViscosity();
        System.out.println("Friction Theory Viscosity: " + frictionViscosity + " kg/(m*s)");
    }
}
```

### Tuning LBC dense-fluid parameters

The LBC implementation exposes the dense-fluid polynomial coefficients ("Whitson/Bray-Clark"
$a_0 \dots a_4$ parameters) so you can tune the model against laboratory data. After selecting the
`"LBC"` viscosity model, update the coefficients via `setLbcParameters` or `setLbcParameter`, then
re-initialize properties to apply them:

```java
system.getPhase(1).getPhysicalProperties().setViscosityModel("LBC");
system.initProperties();
double baseViscosity = system.getPhase(1).getViscosity();

double[] tunedCoefficients = new double[] {0.11, 0.030, 0.065, -0.045, 0.010};
system.getPhase(1).getPhysicalProperties().setLbcParameters(tunedCoefficients);
system.getPhase(1).getPhysicalProperties().setLbcParameter(2, 0.070); // tweak a single term
system.initProperties();
double tunedViscosity = system.getPhase(1).getViscosity();

System.out.println("Base viscosity:  " + baseViscosity);
System.out.println("Tuned viscosity: " + tunedViscosity);
```

### Python Example (via JPype)

```python
from neqsim.thermo import SystemSrkEos
from neqsim.thermodynamicoperations import ThermodynamicOperations

# 1. Create system
system = SystemSrkEos(298.15, 100.0)
system.addComponent("methane", 0.5)
system.addComponent("n-heptane", 0.5)
system.setMixingRule("classic")

# 2. Flash
ops = ThermodynamicOperations(system)
ops.TPflash()

# 3. Set Viscosity Model
# Note: Phase index 0 is usually gas, 1 is oil/liquid
system.getPhase(1).getPhysicalProperties().setViscosityModel("LBC")
system.initProperties()
print("LBC Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")

system.getPhase(1).getPhysicalProperties().setViscosityModel("PFCT")
system.initProperties()
print("PFCT Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")

system.getPhase(1).getPhysicalProperties().setViscosityModel("friction theory")
system.initProperties()
print("Friction Theory Viscosity:", system.getPhase(1).getViscosity(), "kg/(m*s)")
```

## Mathematical Details

### 1. Lohrenz-Bray-Clark (LBC) Model
The LBC model calculates the viscosity of a fluid ($\eta$) as the sum of a low-pressure gas contribution ($\eta^*$) and a dense-fluid contribution ($\eta_{dense}$):

$$ \eta = \eta^* + \frac{\eta_{dense}}{\xi_m} $$

where $\xi_m$ is the mixture viscosity parameter:

$$ \xi_m = \frac{T_{cm}^{1/6}}{M_m^{1/2} P_{cm}^{2/3}} $$

The dense-fluid contribution is a function of the reduced density $\rho_r = \rho_m / \rho_{cm}$:

$$ [(\eta - \eta^*) \xi_m + 10^{-4}]^{1/4} = a_0 + a_1 \rho_r + a_2 \rho_r^2 + a_3 \rho_r^3 + a_4 \rho_r^4 $$

**Mixing Rules:**
*   $T_{cm} = \sum_i x_i T_{ci}$
*   $P_{cm} = \sum_i x_i P_{ci}$ (Note: LBC typically uses specific mixing rules for $T_c, P_c$ involving $V_c$, but NeqSim implementations may vary. The critical volume mixing rule is key.)
*   $M_m = \sum_i x_i M_i$
*   **Critical Volume ($V_{cm}$)**:
    $$ V_{cm} = \sum_{i} x_i V_{ci} $$
    *(Note: This linear mixing rule replaces the previous cubic root rule $\left(\sum x_i V_{ci}^{1/3}\right)^3$ for better heavy oil prediction.)*

### 2. Corresponding States Principle (CSP)
The CSP model uses the Corresponding States Principle to relate the viscosity of a mixture to that of a reference substance (typically Methane) at a corresponding state ($T_0, P_0$).

**Viscosity Mapping:**
$$ \eta_{mix}(T, P) = \eta_{ref}(T_0, P_0) \cdot F_{\eta} \cdot \frac{\alpha_{mix}}{\alpha_{ref}} $$

where the scaling factor $F_{\eta}$ is:
$$ F_{\eta} = \left(\frac{T_{cm}}{T_{c,ref}}\right)^{-1/6} \left(\frac{P_{cm}}{P_{c,ref}}\right)^{2/3} \left(\frac{M_{mix}}{M_{ref}}\right)^{1/2} $$

**Corresponding State ($T_0, P_0$):**
The reference substance is evaluated at:
$$ T_0 = T \cdot \frac{T_{c,ref}}{T_{cm}} \cdot \frac{\alpha_{ref}}{\alpha_{mix}} $$
$$ P_0 = P \cdot \frac{P_{c,ref}}{P_{cm}} \cdot \frac{\alpha_{ref}}{\alpha_{mix}} $$

The parameter $\alpha$ accounts for deviations from the simple CSP and is typically a function of reduced density and molecular weight.

### 3. Friction Theory (f-theory)
The Friction Theory model separates the total viscosity into a dilute gas term ($\eta_0$) and a friction term ($\eta_f$):

$$ \eta = \eta_0 + \eta_f $$

The friction term is derived from mechanical friction concepts applied to the van der Waals repulsive and attractive pressure terms of the Equation of State (EOS):

$$ \eta_f = \kappa_r P_r + \kappa_a P_a + \kappa_{rr} P_r^2 $$

where:
*   $P_r$: Repulsive pressure term from the EOS (e.g., $RT/(v-b)$ for SRK/PR).
*   $P_a$: Attractive pressure term from the EOS (e.g., $-a/(v(v+b))$ for SRK).
*   $\kappa_r, \kappa_a, \kappa_{rr}$: Friction coefficients, which are functions of temperature.

This approach ensures that the viscosity model is consistent with the thermodynamic behavior predicted by the EOS, making it robust across a wide range of conditions, including high pressure and near-critical regions.

### 4. Muzny Hydrogen Viscosity Model
The Muzny correlation for pure hydrogen viscosity follows a multi-term structure:

$$ \eta = \eta_0 + \eta_1 \rho + \Delta\eta(\rho_r, T_r) $$

where:
*   $\eta_0$: Dilute-gas viscosity from Chapman-Enskog theory
*   $\eta_1$: First-density coefficient
*   $\Delta\eta$: Higher-order density contribution

The dilute-gas term is:

$$ \eta_0 = \frac{0.021357 \sqrt{MT}}{\sigma^2 S^*} $$

where $S^*$ is the reduced collision integral and $\sigma = 0.297$ nm is the Lennard-Jones size parameter.

### 5. CO2 Viscosity Model (Laesecke JPCRD 2017)
The CO2 viscosity correlation consists of dilute-gas and residual terms:

$$ \eta = \eta_0(T) + \Delta\eta(\rho, T) $$

The dilute-gas term follows an empirical correlation, and the residual term is expressed as:

$$ \Delta\eta = \eta_{t,L} \left[ c_1 T_r \rho_r^3 + \frac{\rho_r^2 + \rho_r^\gamma}{T_r - c_2} \right] $$

where $T_t = 216.592$ K is the triple point temperature and $\rho_{t,L} = 1178.53$ kg/m³ is the triple point liquid density.

### 6. Salt Water (Laliberté Model)
The Laliberté mixture rule for aqueous salt solutions:

$$ \eta_m = \eta_w^{w_w} \prod_i \eta_i^{w_i} $$

where $\eta_w$ is pure-water viscosity and $\eta_i$ are solute viscosities:

$$ \eta_i = \frac{\exp\left[\frac{\nu_1(1-w_w)^{\nu_2} + \nu_3}{\nu_4 t + 1}\right]}{\nu_5(1-w_w)^{\nu_6} + 1} $$

with $w_w$ = water mass fraction and $t$ = temperature in °C.

---

## Quick Reference Table

| Model Keyword | Applicability | Phase | Multi-Component |
|---------------|---------------|-------|-----------------|
| `"LBC"` | Hydrocarbons, reservoir fluids | Gas/Liquid | Yes |
| `"PFCT"` | Light-medium hydrocarbons | Gas/Liquid | Yes |
| `"PFCT-Heavy-Oil"` | Heavy oils, bitumen | Liquid | Yes |
| `"friction theory"` | General fluids, EOS-consistent | Gas/Liquid | Yes |
| `"polynom"` | Liquids with database parameters | Liquid | Yes |
| `"Muzny"` | Pure hydrogen | Gas/Liquid | No |
| `"Muzny_mod"` | Pure hydrogen (extended) | Gas/Liquid | No |
| `"MethaneModel"` | Pure methane | Gas/Liquid | No |
| `"CO2Model"` | Pure CO2 | Gas/Liquid | No |
| `"KTA"` | Pure helium | Gas | No |
| `"KTA_mod"` | Pure helium (extended) | Gas | No |
| `"Salt Water"` | Brine, salt solutions | Liquid | Yes (aqueous) |

---

## Additional Examples

### Using Pure Component Models

```java
// Pure Hydrogen Viscosity
SystemInterface h2System = new SystemSrkEos(300.0, 50.0);
h2System.addComponent("hydrogen", 1.0);
h2System.setMixingRule("classic");
ThermodynamicOperations h2Ops = new ThermodynamicOperations(h2System);
h2Ops.TPflash();

h2System.getPhase(0).getPhysicalProperties().setViscosityModel("Muzny");
h2System.initProperties();
System.out.println("H2 Viscosity (Muzny): " + h2System.getPhase(0).getViscosity() + " Pa·s");

// Pure CO2 Viscosity
SystemInterface co2System = new SystemSrkEos(350.0, 100.0);
co2System.addComponent("CO2", 1.0);
co2System.setMixingRule("classic");
ThermodynamicOperations co2Ops = new ThermodynamicOperations(co2System);
co2Ops.TPflash();

co2System.getPhase(0).getPhysicalProperties().setViscosityModel("CO2Model");
co2System.initProperties();
System.out.println("CO2 Viscosity (Laesecke): " + co2System.getPhase(0).getViscosity() + " Pa·s");
```

### Salt Water Viscosity

```java
// Brine viscosity calculation
SystemInterface brine = new SystemSrkCPAstatoil(323.15, 10.0);
brine.addComponent("water", 0.95);
brine.addComponent("NaCl", 0.05);
brine.setMixingRule(10); // CPA mixing rule

ThermodynamicOperations brineOps = new ThermodynamicOperations(brine);
brineOps.TPflash();

// Set Laliberté salt water model
brine.getPhase("aqueous").getPhysicalProperties().setViscosityModel("Salt Water");
brine.initProperties();
System.out.println("Brine Viscosity: " + brine.getPhase("aqueous").getViscosity() + " Pa·s");
```

---

## Troubleshooting

### Common Issues

1. **"Model only supports PURE X" error**: Pure-component models (Muzny, CO2Model, MethaneModel, KTA) only work with single-component systems. Use LBC or PFCT for mixtures.

2. **Unexpected viscosity values**: Ensure `initProperties()` is called after setting the viscosity model and after any flash calculations.

3. **Phase selection**: Use `getPhase("oil")`, `getPhase("gas")`, or `getPhase("aqueous")` to select the correct phase, or use phase index (0, 1, 2).

4. **Heavy oil predictions too low**: Try `"PFCT-Heavy-Oil"` or tune LBC parameters using `setLbcParameters()`.

---

## References

1. Lohrenz, J., Bray, B.G., and Clark, C.R., "Calculating Viscosities of Reservoir Fluids from Their Compositions", JPT, 1964.
2. Pedersen, K.S., and Fredenslund, A., "An Improved Corresponding States Model for the Prediction of Oil and Gas Viscosities", Chemical Engineering Science, 1987.
3. Quiñones-Cisneros, S.E., and Deiters, U.K., "Generalization of the Friction Theory for Viscosity Modeling", J. Phys. Chem. B, 2006.
4. Muzny, C.D., et al., "Correlation for the Viscosity of Normal Hydrogen", J. Chem. Eng. Data, 2013.
5. Laesecke, A., and Muzny, C.D., "Reference Correlation for the Viscosity of Carbon Dioxide", JPCRD, 2017.
6. Laliberté, M., "Model for Calculating the Viscosity of Aqueous Solutions", Ind. Eng. Chem. Res., 2007.
7. Lee, A.L., Gonzalez, M.H., and Eakin, B.E., "The Viscosity of Natural Gases", SPE-1340-PA, 1966.
8. Chung, T.H., et al., "Generalized Multiparameter Correlation for Nonpolar and Polar Fluid Transport Properties", Ind. Eng. Chem. Res., 1988.
