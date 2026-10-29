---
title: Diffusivity Models
description: "Diffusion coefficient calculation methods in NeqSim: Chapman-Enskog, Wilke-Lee, Fuller-Schettler-Giddings for gases; Siddiqi-Lucas, Wilke-Chang, Tyn-Calus, Hayduk-Minhas for liquids. Includes validation against experimental data."
---

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

Select the model on an existing phase, then reinitialize that phase before reading a coefficient:

```java
fluid.getPhase("gas").getPhysicalProperties()
    .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
fluid.getPhase("gas").initPhysicalProperties();

fluid.getPhase("oil").getPhysicalProperties()
    .setDiffusionCoefficientModel("Siddiqi Lucas");
fluid.getPhase("oil").initPhysicalProperties();
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
- $\sum v_i$ is the empirical Fuller diffusion-volume sum for species $i$

The displayed equation returns **cm²/s**; multiply by 1e-4 for the public m²/s
result. The retained rounded bar prefactor 1.013e-3 is not an atm prefactor.
Fuller diffusion volumes are empirical numbers, not EOS molar volumes. The
accessible parameter evidence is Gu, Cheng and Tang (2018), equation 2.3/Table 1,
and COMSOL 6.4, Tables 6-7/6-8; these do not establish sixth-edition conformance.

**Diffusion volume table** (selected implemented values):

| Species | $\sum v$ |
|---|---|
| H₂ | 6.12 |
| N₂ | 18.5 |
| O₂ | 16.3 |
| CO₂ | 26.9 |
| H₂O | 13.1 |
| CH₄ | 25.14 |
| C₂H₆ | 45.66 |
| C₃H₈ | 66.18 |
| Benzene, C₆H₆ | 90.96 |
| Cyclohexane, C₆H₁₂ | 123.12 |
| Methanol, CH₄O | 31.25 |
| MEG, C₂H₆O₂ | 57.88 |
| DEG, C₄H₁₀O₃ | 105.03 |
| TEG, C₆H₁₄O₄ | 152.18 |

Regressed simple-molecule values take precedence. Known organic values are
assembled once from C=15.9, H=2.31 and O=6.11, with -18.3 for an aromatic or
heterocyclic ring. Saturated cyclohexane receives no ring subtraction. This
repairs erroneous literal sums in the previous organic table; for example,
ethane changes 43.35→45.66 and propane 61.56→66.18. The canonical database
identity `nC10` now shares n-decane's 209.82 sum rather than taking the fallback.
This is a finite known-structure table, not a general molecular-formula parser.

For components not in the table, the critical volume is used as fallback: $\sum v \approx 0.285 \, V_c$ where $V_c$ is in cm³/mol.
If no positive critical volume is available, the retained fallback is
`max(10, 0.95*M)` with M in g/mol. These estimates lack a general uncertainty
contract and do not qualify TBP fractions or unknown structures. Nonfinite
positive critical volume produces an unavailable result rather than zero.
Known table entries do not require critical-volume data.

Nonpositive/nonfinite temperature, pressure or either molar mass, and
nonpositive/nonfinite calculated coefficients, return NaN through direct Fuller
evaluation. Stored coefficients require recalculation after any state/model
change. The 200–2000 K warning interval is an implementation warning, not an
experimentally established universal validity range. No dense-gas pressure
correction or mixture thermodynamic factor is added.

`FullerDiffusivityQualificationTest` freezes 27 independent 70-digit Decimal
equation evaluations with known diffusion volumes at 300 K and 1.01325 bara. These include all
maintained organic table identities and selected simple-gas controls; masses
are deliberately prescribed as 30/28 g/mol to isolate parameter lookup.
For example, synthetic ethane/N₂ gives 1.4688939362492457e-5 m²/s. Additional
checks cover pressure/temperature scaling, invalid inputs, approximate fallbacks,
amount/composition/insertion order, repeated evaluation and boundary neighbors.
These arithmetic checks are not experimental qualification of those substances.

A separate physical control compares CH₄/He at 200/250/273.15 K and 1.01325 bara
with Langenberg et al. (2020), Table 3: AF D₀=0.618 cm²/s, b=1.68; TT D₀=0.610
cm²/s, b=1.69, using D=D₀(T/273.15)^b. A fixed 15% model-comparison gate applies
only to these six comparisons. The reported fit errors do not include every
systematic contribution and are not a universal model uncertainty. The table's
C₂H₄ rows are ethene; the paper's isolated ethane wording is not used as ethane
evidence. No parameter was fitted to these controls.

Gas-diffusivity clones now independently own five mutable matrices and the
effective-coefficient array. This fixes clone evaluation overwriting the source's
stored results for Fuller, Chapman–Enskog and Wilke–Lee. Plain property-family
clones retain their existing phase-binding semantics: clone the fluid/phase for
independent thermodynamic state, or explicitly rebind a copied family.
Component-count rebinding and other property-family storage are outside this
increment. Default Chapman–Enskog equations and valid-input evaluation cost
are unchanged by the clone repair.

**Key features:**
- Does not need Lennard-Jones parameters
- Covers wide range of organic and inorganic species
- Low-pressure gas estimate; require independent evidence for the actual pair and domain
- Temperature exponent is 1.75 (versus 1.5 for Chapman-Enskog)

**Usage:**
```java
fluid.getPhase("gas").getPhysicalProperties()
    .setDiffusionCoefficientModel("Fuller-Schettler-Giddings");
```

---

## Liquid-Phase Models

Models inheriting the shared liquid `Diffusivity` base calculate ordered infinite-dilution coefficients $D_{ij}^0$ (solute $i$ in solvent $j$), in m²/s. The base uses **Wesselingh–Krishna generalized Vignes interpolation** for finite concentrations:

$$D_{ij}^{mix}=(D_{ij}^0)^{(1+x_j-x_i)/2}(D_{ji}^0)^{(1+x_i-x_j)/2}$$

Here $x_i$ and $x_j$ are liquid mole fractions. The empirical assumption is that the limiting friction of pair $i,j$ in each third solvent $k$ is represented by the geometric-mean diffusivity $D_{ij}^{x_k\to1}=\sqrt{D_{ij}^0D_{ji}^0}$. The two combined exponents sum to one, so the result retains units m²/s. For a binary mixture, $x_i+x_j=1$ and this reduces to binary Vignes interpolation with exponents $x_j,x_i$. At infinite dilution in solvent $j$, it recovers $D_{ij}^0$.

Each unordered pair reads both original limiting directions and publishes the same mixed value in both matrix positions. Recalculation restarts from the limiting correlation, and cloned pair/effective arrays are independently owned. A nonpositive or nonfinite off-diagonal limiting value is rejected; no lower bound or substitute diffusivity is introduced. Diagonal correlation values are retained and excluded from the effective-diffusion sum.

**Qualification and migration.** Earlier shared liquid mixing omitted the third-solvent contribution and overwrote reverse pairs during interpolation. Multicomponent coefficients therefore lost dimensional scale covariance and depended on component insertion order; cloned matrices also aliased their original rows. Correcting these defects changes liquid transport and downstream film rates, sometimes substantially. Revisit previously fitted mass-transfer coefficients or film results; do not preserve them by fitting a compensating parameter. Gas diffusion correlations, the infinite-dilution correlation selected by the user, and the pipeline's approximate relaxation source are unchanged.

`LiquidPairMixingTest` checks independently specified constant coefficients, binary/infinite-dilution limits, a high-precision three-solvent reference product, unit-scale covariance, exact pair symmetry, cloning, recalculation and reversed insertion of the public six-component SRK oil fixture. These are analytical and numerical consistency checks. The Wesselingh–Krishna third-solvent approximation and each underlying infinite-dilution correlation still require application-specific physical data and validity ranges. They do not qualify stationary hydrocarbon pool area, heat supply, coupled multicomponent evaporation or predictive depletion times. The separate conservative pipeline root/phase-label limitation remains outside this property correction.

The executable [LiquidMixingBenchmark](../../src/test/java/neqsim/physicalproperties/methods/liquidphysicalproperties/diffusivity/LiquidMixingBenchmark.java) writes seven raw timing batches for two and six components, with 30,000 warm-up calls per operation and 100,000 measured calls per batch. It measures inherited pair mixing and matrix cloning separately, excluding EOS initialization. Compare repeated forks in reversed order; do not interpret these microbenchmarks as whole-process speed. Correct clone isolation requires separate row allocation, so its cost must be reported separately from mixing throughput. The underlying infinite-dilution correlation and its solvent-selection options are unchanged; this correction does not automatically select a hydrocarbon-specific correlation.

Provenance: Wesselingh and Krishna, *Mass Transfer* (1990); the independently documented formula is also in [COMSOL Liquid & Gas Properties Guide 6.2, equations 2-250–2-256, pp.108–109](https://doc.comsol.com/6.2/doc/com.comsol.help.lgp/LiquidAndGasPropertiesModuleUsersGuide.pdf). This is an implementation-equation cross-check, not experimental validation. For limitations of third-solvent limiting-friction estimates, see [Liu, Bardow and Vlugt (2011), DOI:10.1021/ie102515w](https://doi.org/10.1021/ie102515w).

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

The existing regression suite compares selected gas and liquid pairs against
published values. Its broad 25% gas and 50% liquid gates do not establish
universal model uncertainty. The following legacy illustrative table records
selected comparisons at 298.15 K and 1 atm; it is not a full-range qualification.
The independent Fuller parameter/lifecycle checks and CH₄/He controls above
have separate numerical and physical acceptance criteria.

### Gas-Phase Validation

| System | Experimental (cm²/s) | Chapman-Enskog | Fuller | Wilke-Lee | Source |
|---|---|---|---|---|---|
| CH₄-N₂ | 0.220 | 0.219 (0.7%) | 0.216 (2.0%) | 0.231 (5.0%) | Marrero and Mason (1972) |
| CO₂-N₂ | 0.167 | 0.155 (7.4%) | 0.162 (2.7%) | 0.166 (0.3%) | Poling (2001) |

**Gas model accuracy summary:**
- Chapman-Enskog: ±1-8%
- Fuller-Schettler-Giddings: pair- and state-dependent; see the bounded CH₄/He control above
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
| Low-pressure gas estimate | Fuller-Schettler-Giddings | `"Fuller-Schettler-Giddings"` | Qualify the actual pair and state; no universal percentage |
| Gas phase (light gases) | Chapman-Enskog or Wilke-Lee | `"Chapman-Enskog"` / `"Wilke Lee"` | ±1-8% |
| Aqueous liquids | Wilke-Chang | `"Wilke-Chang"` | ±10-15% |
| Organic liquids | Tyn-Calus or Hayduk-Minhas | `"Tyn-Calus"` / `"Hayduk-Minhas"` | ±10-20% |
| Wide P-T range | CSP | `"CSP"` | ±15-25% |
| Amine systems | Alkanol amine | `"Alkanol amine"` | Specialized |
| High pressure liquids | High Pressure | `"High Pressure"` | Pressure-corrected |

---

## Usage Examples

### Binary gas diffusion coefficient

This complete example uses the public `PhysicalProperties` API. The returned Maxwell-Stefan
coefficient is in m²/s.

```java
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public final class GasDiffusivityExample {
  private GasDiffusivityExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(298.15, 1.01325);
    fluid.addComponent("methane", 0.50);
    fluid.addComponent("nitrogen", 0.50);
    fluid.setMixingRule("classic");

    new ThermodynamicOperations(fluid).TPflash();
    fluid.initPhysicalProperties();

    PhysicalProperties properties =
        fluid.getPhase("gas").getPhysicalProperties();
    properties.setDiffusionCoefficientModel(
        "Fuller-Schettler-Giddings");
    fluid.getPhase("gas").initPhysicalProperties();

    double diffusivityM2PerS =
        properties.getDiffusionCoefficient("methane", "nitrogen");
    if (!(diffusivityM2PerS > 0.0)
        || !Double.isFinite(diffusivityM2PerS)) {
      throw new IllegalStateException("Invalid binary diffusivity");
    }
  }
}
```

Use component indexes with `getDiffusionCoefficient(i, j)` when the caller already owns a
validated component ordering. The name overload is clearer in user-facing workflows.

### Comparing models

A model setter changes the calculator but does not refresh the stored coefficients. Reinitialize
the phase after every model change:

```java
String[] models = {
  "Chapman-Enskog",
  "Wilke Lee",
  "Fuller-Schettler-Giddings"
};
for (String model : models) {
  properties.setDiffusionCoefficientModel(model);
  fluid.getPhase("gas").initPhysicalProperties();

  double diffusivityM2PerS =
      properties.getDiffusionCoefficient("methane", "nitrogen");
  if (!(diffusivityM2PerS > 0.0)
      || !Double.isFinite(diffusivityM2PerS)) {
    throw new IllegalStateException("Invalid diffusivity for " + model);
  }
}
```

Apply the same sequence to an aqueous or oil phase, using a model key implemented for that phase.
Do not read the public mutable `diffusivityCalc` field; use the `PhysicalProperties` accessors so
the example remains on the supported API boundary.

### Effective diffusivity

Effective coefficients are a separate multicomponent calculation. Calculate them before reading
a component result:

```java
properties.calcEffectiveDiffusionCoefficients();

int componentCount = fluid.getPhase("gas").getNumberOfComponents();
for (int component = 0; component < componentCount; component++) {
  double effectiveDiffusivityM2PerS =
      properties.getEffectiveDiffusionCoefficient(component);
  if (!(effectiveDiffusivityM2PerS > 0.0)
      || !Double.isFinite(effectiveDiffusivityM2PerS)) {
    throw new IllegalStateException("Invalid effective diffusivity");
  }
}
```

### Amine model set

`initPhysicalProperties(String)` selects a `PhysicalPropertyType` such as `VISCOSITY`; it does
not select a model set. Configure amine service through `PhysicalPropertyModel.AMINE` before
initialization:

```java
import neqsim.physicalproperties.system.PhysicalPropertyModel;

fluid.setPhysicalPropertyModel(PhysicalPropertyModel.AMINE);
fluid.initPhysicalProperties();

PhysicalProperties aqueousProperties =
    fluid.getPhase("aqueous").getPhysicalProperties();
aqueousProperties.setDiffusionCoefficientModel("Alkanol amine");
fluid.getPhase("aqueous").initPhysicalProperties();

double co2AmineDiffusivityM2PerS =
    aqueousProperties.getDiffusionCoefficient(0, 1);
```

Confirm the component ordering before using integer indexes. For named components, prefer
`getDiffusionCoefficient(component1, component2)`.

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


### Accessible Fuller qualification sources

- Gu, W., Cheng, P. and Tang, M. (2018), *R. Soc. Open Sci.* 5:171936,
  [equation 2.3 and Table 1](https://pmc.ncbi.nlm.nih.gov/articles/PMC6083652/),
  [doi:10.1098/rsos.171936](https://doi.org/10.1098/rsos.171936), CC BY 4.0.
  Parameter/additive-rule evidence; later evaluation, not the original handbook.
- COMSOL 6.4, [Transport Properties, Tables 6-7/6-8](https://doc.comsol.com/6.4/doc/com.comsol.help.chem/chem_ug_thermo.11.27.html).
  Independent vendor implementation documentation corroborates the structural
  and simple-molecule distinction; it is not experimental validation.
- Langenberg et al. (2020), *Atmos. Chem. Phys.* 20, 3669–3682,
  [equation 5 and Table 3](https://acp.copernicus.org/articles/20/3669/2020/),
  [doi:10.5194/acp-20-3669-2020](https://doi.org/10.5194/acp-20-3669-2020),
  CC BY 4.0. Original AF/TT measured fits; selected CH₄/He controls only.
