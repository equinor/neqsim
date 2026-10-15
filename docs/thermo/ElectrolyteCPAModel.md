---
title: "Electrolyte CPA Model Documentation"
description: "Theory, supported interactions, implementation structure, and usage boundaries for NeqSim's electrolyte CPA model for aqueous electrolyte solutions."
---

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

### Ionic covolume selection and compatibility

CPA ionic components read covolume coefficients `[0]` and `[1]` directly from
`FurstElectrolyteConstants.furstParamsCPA`, including construction and
`initFurstParam()` reinitialization. This also applies to the Statoil and Advanced
subclasses. ScRK components retain their separate `furstParams` defaults.
Constructing an electrolyte CPA system no longer reassigns those shared ScRK
defaults. Interleaving systems, adding ions later, cloning and serialization must
therefore preserve each model's covolume identity.

With the default tables, Na+ covolume is approximately `2.58461732544` for ScRK
and `3.87214760778` for CPA in NeqSim internal units. Earlier versions could give
the CPA value to ScRK solely because a CPA system had been constructed first.
Removing that construction-order dependence can change affected ScRK results; it
is not a new parameter fit or an estimate of fluid-property error.

For deliberate customization, use `setFurstParamCPA(index, value)` for the CPA
table and `setFurstParam(index, value)` for the ScRK table, then reinitialize the
affected components/phases. These remain process-wide mutable customization APIs,
not per-system or thread-safe parameter stores. The legacy
`setFurstParams("electrolyteCPA")` explicitly aliases the ScRK table to CPA and
should not be used to select a system model. Code that relied on a CPA constructor
making `setFurstParam` target CPA must migrate to `setFurstParamCPA`.

The covolume correction is independent of the reference-interaction correction
described below. Neither changes fitted database pairs.

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

$$\frac{A^{Born}}{RT} = -\frac{e^2 N_A}{4\pi\varepsilon_0 RT} \sum_i n_i \frac{z_i^2}{\sigma_i} \left(1 - \frac{1}{\varepsilon_r}\right)$$

Where:
- $z_i$ = ionic charge
- $\sigma_i$ = ionic diameter
- $\varepsilon_r$ = relative permittivity (dielectric constant) of the solvent mixture

Here $n_i$ is in mol and $\sigma_i$ is the diameter in metres used by the
Furst-based implementation. The equivalent radius convention uses
$8\pi\varepsilon_0 RT$ and $r_i=\sigma_i/2$. Do not combine a diameter with the
radius prefactor, or use $N_A/k_B$ in place of $N_A/R$ for this molar expression.

The mole derivative includes both the ionic contribution and the derivative of
solvent permittivity. The latter must scale inversely with phase amount so that
the chemical potential stays intensive. See
[Born derivatives and phase-size invariance](ElectrolyteBornDerivatives.md) for
the chain rule, the legacy Mod2004 correction, the related EOS audit, and the
literature-based development recommendations.

The base Furst/electrolyte CPA derivative implementation includes the implicit
response of the stationary shielding parameter and the complete dielectric/Born
second-derivative chain rules. See the same guide for finite-difference
qualification. The n-butane and n-hexane temperature-only dielectric inputs use
qualified liquid reference data described in [hydrocarbon dielectric inputs](ElectrolyteHydrocarbonDielectric.md).
Nitrogen uses the
constant relative permittivity 2.0 specified in Solbraa (2002), Table 8-13, p. 176,
with zero temperature derivatives in equation (8.17). The table also uses this
approximation for methane and CO2; it supplies no temperature validity range for
these gases. This is an input to the electrolyte model, not a measured
density-independent pure-gas dielectric correlation. Both standard and extended
database loading obtain these neutral-component coefficients from canonical
`COMP.csv`; the existing extended loader replaces shared neutral properties
after reading `COMP_EXT.csv`. Restoring this documented input
does not by itself establish quantitative nitrogen salting-out accuracy. A converged
flash or consistent Jacobian does not by itself qualify the model's salting-out
or mixed-inhibitor accuracy.

Call `system.init(3)` before inspecting composition derivatives or running their
consistency checks. A flash followed only by `initProperties()` may leave those
arrays uninitialized. Derivative initialization refreshes electrolyte and CPA
association caches at the final returned volume; repeated initialization and
detached copies are covered by the final-volume regression tests.
Component clones preserve fugacity composition-derivative caches in independently
owned arrays. Electrolyte CPA component clones also own their association-composition
derivative rows, so initializing one liquid phase cannot overwrite another phase's
component derivatives. This does not qualify concurrent access to all cloned phase
workspaces.

Ionic shielding requires finite positive phase permittivity. If an extrapolated
dielectric correlation violates that domain, the calculation raises an explicit
error; ion-free states retain exactly zero shielding. In particular, a 1000 K
endpoint for aqueous-phase disappearance in gas/oil/brine is invalid with the
current solvent correlation. These model corrections do not qualify the legacy
TP-flash boundary solver: brine boiling and gas/oil/brine pressure boundaries
require the [general equilibrium solver work](https://github.com/equinor/neqsim/pull/4306)
for strict conservation, ion-confinement and fugacity-closure checks.

## Short-Range Interaction Parameters (Wij)

### Overview

The short-range Wij parameters capture specific ion-solvent and ion-ion interactions not described by the electrostatic terms. These are fitted to experimental activity coefficient and osmotic coefficient data.

### Parameter Correlations

The reference coefficient `wij[0]` for calculated cation-water and ordinary
cation-anion pairs follows the phase's EOS family. `SystemFurstElectrolyteEos`
and `SystemFurstElectrolyteEosMod2004` use the existing ScRK `furstParams` table;
the three electrolyte CPA variants use `furstParamsCPA`. Selection follows the
phase type, including CPA subclasses, and is independent of construction order.
Database pairs marked fitted (`CalcWij != 0`) retain their reference and
temperature coefficients. The named-solvent tables, the Piperazine approximation,
the MDEA+ ion-ion override, and gas-ion/organic-inhibitor overrides are unchanged.

For ScRK, the existing six-coefficient table gives the same correlation for
monovalent and divalent cations, with diameters in angstroms:

```
Wij(cation-water, 298.15 K) = 6.99219e-5 * stokesDiameter + 4.3984e-6
Wij(cation-anion, 298.15 K) = -6.06e-8 * (d_cat + d_an)^4 - 2.1795e-5
```

These are the current ScRK table values, not a new fit. The separate ScRK/CPA
model identity follows the correlations discussed by Solbraa (2002), equations
8.14-8.15 and table 8-12; the mutable repository tables remain the exact numerical
source. The CPA-specific valence fits are described below and are unchanged.

**Numerical compatibility:** correcting the unconditional CPA dispatch introduced
in #1787 changes calculated ScRK pairs. With the default Na+ Stokes diameter
of 5.68 angstroms, `wij[0](Na+, water)` changes from `1.6297556353584585e-4`
to `4.01554792e-4`. ScRK phase equilibria and properties that depend on these
pairs can therefore change. This is a model-selection regression correction
(#3850), not evidence that the original or corrected calibration meets a given
experimental accuracy target. CPA reference interactions retain their values.

The current shared `furstParamsCPA_TDep` temperature correction is deliberately
retained for both families; the correction does **not** restore a complete
historical ScRK calibration. Regressions check the reference coefficients,
symmetric/reversed pairs, both valences, repeated initialization, CPA interleaving,
fitted precedence, and the retained temperature formula at 298.15 and 323.15 K.
Qualification of the combined ScRK correlation against experimental data,
including temperature dependence, remains part of [#3144](https://github.com/equinor/neqsim/issues/3144).

The CPA Wij values are calculated using the following ionic-diameter correlations:

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

- Validated by the targeted derivative checks described above
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
- Water + TEG (triethylene glycol; initial parameter estimates)
- Water + Methanol
- Water + MDEA (methyldiethanolamine)

Separate Wij parameters are available for each solvent system.

For calculated cation-glycol pairs, `TEG` and the mixing-rule alias
`triethylene glycol` use `furstParamsCPA_TEG`; `MEG` and `ethylene glycol`
use `furstParamsCPA_MEG`. Each set uses indices `[2]`/`[3]` for the
monovalent slope/intercept and `[6]`/`[7]` for divalent cations. The TEG
parameters are initial estimates based on water parameters and have **not
been fitted to experimental TEG-water-electrolyte data**.

The correction in [issue #3846](https://github.com/equinor/neqsim/issues/3846)
removes an earlier MEG fallback that shadowed the TEG-specific branch. With
the default parameters and the Na+ Stokes diameter of 5.68, the calculated
Na+-TEG reference parameter is now `0.000160864`, instead of the MEG value
`0.0003394`. TEG-containing calculations using the shared Furst short-range
mixing rule can therefore change. Explicitly fitted pair parameters retain
precedence, and the existing temperature-dependent coefficients are unchanged.
This corrects parameter selection; it does not validate the TEG estimates
against experimental data.

## Gas-Ion Interaction Parameters (Salting-Out Effect)

Gas-ion short-range interaction parameters affect gas solubility, but numerical convergence
alone does not establish their physical accuracy. The current default table is a legacy
NaCl-oriented calibration. Its transfer to other salts, mixed salts, and solvent mixtures
requires independent qualification; the same charge sign does not imply the same gas-ion
interaction.

### Setschenow Equation and Comparison Basis

For a molality basis at fixed temperature and gas fugacity,

$$\log_{10}\left(\frac{m_g^0/f_g^0}{m_g/f_g}\right)=k_{s,mm}m_s$$

Here $m_g$ is dissolved gas in mol/kg water, $f_g$ is gas fugacity in bar, and $m_s$ is
salt molality in mol/kg water. Thus $k_{s,mm}$ has units kg/mol. The superscript zero
identifies the salt-free reference. For unequal gas fugacities, the normalized comparison
uses the dilute-gas Henry-law approximation. A changed gas composition or phase split
can raise aqueous mole fraction even while molality per gas fugacity falls.

The concentration form uses mol/L and a coefficient in L/mol; it is a different
coefficient. Natural-log coefficients are $\ln(10)$ times their base-10 counterparts.
[NIST SRD 106, system 62_73](https://srdata.nist.gov/solubility/sol_detail.aspx?sysID=62_73)
defines the concentration bases and conversions. Do not mix molality, molarity, ionic
strength, or logarithm bases when importing data.

### Legacy Gas-Ion Parameters

These are the current source values in `FurstElectrolyteConstants.furstParamsGasIon`,
in m³/mol. They are model parameters, not measured Setschenow coefficients. The original
claimed coefficients and quantitative accuracy have not been established by a reproducible
independent dataset and must not be used as acceptance evidence.

| Gas | W_cation (m³/mol) | W_anion (m³/mol) |
|-----|-----------------|-----------------|
| CO₂ | 1.27e-4 | 1.27e-4 |
| CH₄ | 1.05e-4 | 1.05e-4 |
| C₂H₆ | 1.10e-4 | 1.10e-4 |
| C₃H₈ | 1.15e-4 | 1.15e-4 |
| C₄ (butanes) | 1.20e-4 | 1.20e-4 |
| C₅+ | 1.25e-4 | 1.25e-4 |
| N₂ | 1.05e-4 | 1.05e-4 |
| H₂S | 1.10e-4 | 1.10e-4 |
| H₂ | 1.30e-4 | 1.30e-4 |

For `SystemElectrolyteCPAstatoil`, the bounded nitrogen–NaCl correction and
unfitted divalent-chloride prediction override selected entries of this legacy
table. See [gas–salt parameter qualification](ElectrolyteNitrogenSalting.md)
for the temperature correlation, explicit/global override behavior, independent
holdouts and large remaining concentrated-brine errors.

The `SystemElectrolyteCPAstatoil` CO2/K+ pair has a separately qualified default
in the fixed CO2/Cl− gauge. See [CO2–KCl interaction qualification](CO2KClInteractionQualification.md)
for its units, calibration source, independent holdouts, remaining absolute-solubility
bias, and explicit-parameter priority. Other potassium-containing mixtures are not
implied to retain unchanged predictions or validated accuracy.

A defensible recalibration must first qualify dielectric data and the complete Helmholtz
energy derivatives, state the training data and reference basis, then validate held-out
temperatures, pressures, concentrations, and salt mixtures. Parameters from a different
e-CPA short-range model cannot be copied directly into the Fürst $W_{ij}$ expression.

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

// Read the calculated mole fraction; compare molality at matched fugacity for salting-out.
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

### Component conservation in hydrate-temperature calculations

Non-reactive brines retain the supplied molecular and ionic inventories. The
multiphase solver temporarily uses a normalized, ion-free molecular feed for
phase discovery. Stripped ions are excluded from its phase-fraction equations;
their very small fugacity coefficients must not enter the molecular Newton
matrix. Restoring the ions transforms both phase fractions and aqueous
composition back to the full feed basis. Charge balance alone does not establish
component conservation.

The final ionic gas/aqueous refinement compares Gibbs energies only when the
reference state conserves the feed. A converged, normalized, conservative
candidate must not be rejected because a state with a different inventory has
a lower extensive Gibbs energy.

At every fluid evaluation, the hydrate-temperature operation checks phase and
composition normalization, each component's recovered inventory against the
input, and aqueous ion confinement. An invalid non-reactive electrolyte state
raises `IllegalStateException` with the failed diagnostic instead of returning
a hydrate temperature. The caller's multiphase-check setting is restored on
success and failure. Reactive calculations retain their existing species and
element-balance handling.

Regression coverage includes CO2 with NaCl–CaCl2 and NaCl–KCl mixtures on a
1 kg water / 10 mol CO2 basis, nearby pressures and salt concentrations, and
equivalent salt-addition orders and repeated calculations. These are numerical
conservation checks, not experimental qualification of mixed-salt hydrate
temperatures.

For the CO2/water/explicit-ion subset, hydrate-temperature calculations also
compare independently initialized vapour and liquid CO2 trials with a conserved
aqueous feed. The constrained solver keeps ions in water, checks molecular
fugacity equality for both gas/aqueous and liquid-CO2/aqueous splits, and accepts
a single aqueous phase only after CO2 stability testing. This addresses the
phase-state failures in [issue #3584](https://github.com/equinor/neqsim/issues/3584).
It is a hydrate-temperature fluid-evaluation path; the generic `TPflash()` API
and mixed-inhibitor/chemical paths are unchanged.

Use the operation's `getDiagnostics()` snapshot to distinguish a numerically
converged finite-inventory result from a saturated CO2 boundary. The
[hydrate phase-state guide](../thermodynamicoperations/hydrate_flash_operations#co2brine-phase-state-diagnostics)
documents the public API and the separate saturated/undersaturated experimental
assessment. The selected public data give maximum absolute temperature errors
of 1.12 K for saturated systems and 4.10 K for undersaturated systems, so this
numerical correction does not establish full-range experimental accuracy or
high-pressure drilling-fluid applicability.

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
double pH = system.getPhase(aq).getpH("molality");
```

For reactive ionic feeds with freely selected fluid phases, the flash alternates
phase equilibrium and aqueous speciation while conserving the full feed's element
inventory. Slow gas-buffered coupling can activate a safeguarded correction in
reaction-extent coordinates. Each accepted correction retains nonnegative species
amounts, decreases the reaction residual, and rechecks phase equilibrium; final
acceptance still requires the reaction, electroneutrality, and element-balance
gates. A converged isolated aqueous calculation alone does not establish equilibrium
with a finite gas inventory.

The single-phase chemical Newton equations use the current conserved inventory
in the normalization derivative even when its difference from the target is
below the conservation-correction gate. Otherwise a nearly stationary solution
can acquire artificial relative steps in trace carbonate species. The correction
gate scales with the target inventory and a floating-point roundoff allowance;
it has no one-mole floor. Analytical stationary-potential and material-restoration
tests cover feed scaling, alongside the public iron-carbonate saturation case.

Activity normalization also requires consistent model settings in the mixture and
its reference states. Automatically constructed MM references inherit the active
term switches, dielectric mixing rule, and customized ion parameters before each
evaluation. Explicitly supplied references retain their own settings, including
references using the MM model. Both reference setters transfer configuration
ownership to the caller; replacing an entry through the exposed reference array
does the same. In-place edits to an automatic reference remain managed until it
is passed through a setter. This distinction survives cloning and serialization.
Phase clones own their mutable activity-reference phases while preserving custom
values and repeated or cyclic reference relationships. This complements ownership
of the CPA association state used during independent phase evaluation.

### In-situ pH — `getpH()` and the acid-gas fallback

Use `aqueousPhase.getpH("molality")` for IUPAC pH, measurements, and corrosion calculations.
The no-argument `getpH()` (including the `SystemInterface` delegate) retains its documented
legacy mole-fraction activity convention for compatibility. The available scales are:

| Method | Definition | Notes |
|--------|-----------|-------|
| `getpH()` / `getpH("activity")` | `-log10(γ_x · x_H3O+)` | legacy default; mole-fraction activity, not IUPAC pH |
| `getpH("molality")` | `-log10(γ_m · m_H3O+)` | IUPAC standard (mol H₃O⁺ / kg water) |
| `getpH("molarity")` | `-log10(γ_c · [H3O+])` | mol/L; dilute-aqueous approximation `γ_c ≈ γ_m` |
| `getpH("acidgas")` | carbonic/hydrosulfuric acid dissociation estimate | screening in-situ pH from dissolved CO₂/H₂S |

For EOS solute coefficients normalized at infinite dilution, `γ_m = γ_x · x_water`.
The standard chemical potential absorbs the water-molar-mass factor; dividing the
activity coefficient by 55.51 again is incorrect. Symmetric GE coefficients are first
normalized by their infinite-dilution value; Pitzer and Duan–Sun coefficients already use molality.
Deshmukh–Mather converts its native kg-of-all-solvents coefficient to kg-of-water while
preserving the native chemical activity. Kent–Eisenberg retains its ideal mole-fraction
activities and converts only the concentration scale.
For EOS solute coefficients with `γ_m = γ_x · x_water`,
`γ_m · m_H3O+ = γ_x · x_H3O+ / M_water`. The legacy mole-fraction activity result
therefore exceeds molal pH by `-log10(M_water [kg/mol]) ≈ 1.744`. Migrate physical pH consumers explicitly to
`getpH("molality")`; do not subtract a constant from all model outputs because native
activity conventions differ. The acid-gas fallback is a molarity-based dilute-water screening estimate.

Reaction constants and reporting scales are separate choices. For a reaction with
solute stoichiometric sum `Δν`, `K_x = K_m · M_water^Δν`, retaining solvent activity.
The default CO2 dissociation correlation requires one such conversion; the water
autodissociation constant already contains two and must not be converted again.
This follows the standard-state transformation derived by
[Smith, Rutherford, and Leal (2026), equations 34 and 36](https://doi.org/10.1002/cjce.70438).

The default mole-fraction reaction database uses the following provenance:

| Reaction | Source basis and implemented correction |
|----------|----------------------------------------|
| CO2 + 2 H2O ⇌ HCO3− + H3O+ | The existing correlation follows the molality constant of [Plummer and Busenberg (1982)](https://pubs.usgs.gov/publication/70011789). Its constant term changes from `253.235548` to `249.218997465218` by adding `ln(0.018015)`; temperature-dependent terms are retained. |
| HCO3− + H2O ⇌ CO3²− + H3O+ | The mole-fraction correlation in [Huttenhuis et al. (2008), Table 2](https://doi.org/10.1016/j.fluid.2007.10.020) gives the constant term `216.049`, correcting the previous `209.141`; other coefficients are retained. |
| 2 H2O ⇌ H3O+ + OH− | The existing mole-fraction correlation already includes the two-solute conversion and is unchanged. |

Pitzer uses its separate molality reaction database. Regression checks compare the
carbonate correlations with published values across temperature and independently
verify the concentration-scale identity and closed-feed conservation.

**Acid-gas fallback (no `chemicalReactionInit()` required).** The rigorous
`activity`/`molality`/`molarity` scales need an explicit `H3O+` inventory. Normally,
`chemicalReactionInit()` registers the reaction species and a subsequent reactive
flash solves their equilibrium amounts; species can also be added explicitly. If those reactions were **not** initialised (or the electrolyte
solver is numerically unstable and yields no/NaN `H3O+`, e.g. at low pressure),
`getpH()` now falls back to an acid-gas dissociation estimate whenever the
aqueous phase contains water together with dissolved CO₂ and/or H₂S — instead of
silently returning a flat, unphysical `7.0`.

The fallback uses the first-dissociation equilibria of carbonic and
hydrosulfuric acid and an electroneutrality balance dominated by the conjugate
bases (HCO₃⁻, HS⁻):

$$[\mathrm{H}^+] = \sqrt{K_1^{\mathrm{CO_2}}\,C_{\mathrm{CO_2}} + K_1^{\mathrm{H_2S}}\,C_{\mathrm{H_2S}} + K_w}, \qquad \mathrm{pH} = -\log_{10}[\mathrm{H}^+]$$

where $C_i$ are aqueous molar concentrations (mol/L) obtained from the aqueous
phase density and molar mass. The constants are temperature-dependent:
$K_1^{\mathrm{CO_2}}$ from Plummer &amp; Busenberg (1982), $K_1^{\mathrm{H_2S}}$
from a van't Hoff fit anchored at pK₁ = 7.05 (25 °C), and $K_w$ from a van't Hoff
fit anchored at 1.0×10⁻¹⁴ (25 °C). For CO₂-saturated water at ambient conditions
this reproduces the textbook value **pH ≈ 3.9**; acid-gas-free water returns
≈ 7.0.

```java
// Plain electrolyte fluid — NO chemicalReactionInit(), excess CO2 over water
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.01325);
system.addComponent("CO2", 5.0);
system.addComponent("water", 10.0);
system.createDatabase(true);
system.setMixingRule(10);
system.setMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();
system.initProperties();

double pH = system.getpH();   // ≈ 3.9 for CO2-saturated water (acidic)
```

> **Screening scope.** The acid-gas fallback ignores alkalinity from other ions
> (bicarbonate buffering, dissolved salts) and the second dissociation steps. It
> is intended for acid-gas corrosion screening (e.g. NORSOK M-506), not for
> buffered-brine speciation. For a fully rigorous speciated pH in a buffered
> brine, run `chemicalReactionInit()` so explicit `H3O+`/`HCO3-`/`OH-` species
> are solved. For corrosion work that must always return a finite, source-tagged
> value, see `neqsim.process.corrosion.RobustAqueousPH`.

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

1. Solbraa, E. (2002). "Measurement and Modelling of Absorption of Carbon Dioxide into Methyldiethanolamine Solutions at High Pressures." PhD Thesis, Norwegian University of Science and Technology. [Original thesis](http://hdl.handle.net/11250/231326), equation (8.17) and Table 8-13, p. 176.

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
| Dec 2024 | Added gas-ion parameters for C2-C5+, N₂, H₂S, H₂ | Legacy calibration; independent salt-specific qualification required |
| Feb 2026 | Added OI-ion parameters for Hu-Lee-Sum compliance | Additive hydrate inhibition with combined inhibitors |
| Jul 2026 | `getpH()` acid-gas dissociation fallback | Acidic in-situ pH for CO₂/H₂S water without `chemicalReactionInit()`; no more silent 7.0 |

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
