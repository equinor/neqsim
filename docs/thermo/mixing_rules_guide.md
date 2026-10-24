---
title: "Mixing Rules in NeqSim"
description: "This guide provides comprehensive documentation on mixing rules available in NeqSim, including mathematical formulations, usage patterns, and recommendations for different applications."
---

This guide provides comprehensive documentation on mixing rules available in NeqSim, including mathematical formulations, usage patterns, and recommendations for different applications.

## Table of Contents
1. [Introduction to Mixing Rules](#1-introduction-to-mixing-rules)
2. [Setting Mixing Rules](#2-setting-mixing-rules)
3. [Classic Mixing Rules](#3-classic-mixing-rules)
4. [Huron-Vidal Mixing Rules](#4-huron-vidal-mixing-rules)
5. [Wong-Sandler Mixing Rule](#5-wong-sandler-mixing-rule)
6. [CPA Mixing Rules](#6-cpa-mixing-rules)
7. [Søreide-Whitson Mixing Rule](#7-sreide-whitson-mixing-rule)
8. [Binary Interaction Parameters (kij)](#8-binary-interaction-parameters-kij)
9. [Complete Reference Table](#9-complete-reference-table)
10. [Examples by Application](#10-examples-by-application)

---

## 1. Introduction to Mixing Rules

Mixing rules determine how pure-component parameters from an equation of state are combined to calculate mixture properties. For cubic equations of state like SRK and PR, the key parameters are:

- **a**: Energy parameter (attraction term)
- **b**: Co-volume parameter (repulsion term)

The general form of cubic EoS mixing rules is:

$$
a_{mix} = \sum_i \sum_j x_i x_j a_{ij}
$$

$$
b_{mix} = \sum_i x_i b_i
$$

Where the cross-parameter $a_{ij}$ is typically calculated using a combining rule:

$$
a_{ij} = \sqrt{a_i a_j}(1 - k_{ij})
$$

The binary interaction parameter $k_{ij}$ is crucial for accurate phase equilibrium predictions.

---

## 2. Setting Mixing Rules

NeqSim provides three ways to set mixing rules:

### 2.1 By Integer Value

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("CO2", 0.2);
fluid.setMixingRule(2);  // Classic mixing rule
```

### 2.2 By Name (String)

```java
fluid.setMixingRule("classic");
fluid.setMixingRule("HV");
fluid.setMixingRule("WS");
```

### 2.3 By Enum Type

```java
import neqsim.thermo.mixingrule.EosMixingRuleType;

fluid.setMixingRule(EosMixingRuleType.CLASSIC);
fluid.setMixingRule(EosMixingRuleType.byName("classic"));
fluid.setMixingRule(EosMixingRuleType.byValue(2));
```

### 2.4 With GE Model (for Huron-Vidal/Wong-Sandler)

```java
// Huron-Vidal with UNIFAC
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");

// Wong-Sandler with NRTL
fluid.setMixingRule("WS", "NRTL");
```

### 2.5 Component changes after configuration

Configure interaction-based mixing rules after the final component set is known.
In reactive systems, `chemicalReactionInit()` can add species, so call it before
`createDatabase(true)` and `setMixingRule(...)`. For Fürst/electrolyte-CPA phases,
adding or removing components after configuration makes the ionic interaction
matrices stale. Initialization rejects this state with an `IllegalStateException`
that reports both component counts and the recovery steps.
Rebuild the database and reapply the rule, then restore any custom interaction
parameters and call `init(0)` before further calculations. Same-count replacements
or renaming also require rebuilding, although the count check cannot detect them.

Changing only the amounts of existing components does not require a rebuild.
This diagnostic is local to the electrolyte phase family and direct ionic
interaction recalculation. Ordinary cubic-EOS initialization retains its existing
behavior, including component preparation with `init(0)` before rebuilding the
rule. The ordinary zero-interaction `NO` rule is also unchanged. These paths do
not acquire automatic parameter repair or a new general topology guarantee. See the
[electrolyte CPA initialization guidance](ElectrolyteCPAModel.md#reaction-species-and-mixing-rule-initialization-order)
for the reaction-specific ordering and regression coverage.

---

## 3. Classic Mixing Rules

### 3.1 NO (kij = 0) - Type 1

The simplest mixing rule with no binary interactions. All $k_{ij}$ values are set to zero.

$$
a_{ij} = \sqrt{a_i a_j}
$$

**Use case:** Quick calculations, ideal mixture approximations, or when no interaction parameters are available.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("ethane", 0.3);
fluid.setMixingRule(1);  // or fluid.setMixingRule("NO");
```

### 3.2 CLASSIC - Type 2

The standard van der Waals one-fluid mixing rule with binary interaction parameters from the NeqSim database.

$$
a_{mix} = \sum_i \sum_j x_i x_j \sqrt{a_i a_j}(1 - k_{ij})
$$

$$
b_{mix} = \sum_i x_i b_i
$$

**Use case:** General hydrocarbon systems, natural gas processing, most industrial applications.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("CO2", 0.10);
fluid.addComponent("nitrogen", 0.05);
fluid.setMixingRule(2);  // or fluid.setMixingRule("classic");
```

### 3.3 CLASSIC_T - Type 8

Classic mixing rule with temperature-dependent binary interaction parameters:

$$
k_{ij}(T) = k_{ij,0} + k_{ij,T} \left(\frac{T}{273.15\ \mathrm{K}} - 1\right)
$$

Here, $T$ is absolute temperature in kelvin, $k_{ij,0}$ is the interaction parameter at
273.15 K, and $k_{ij,T}$ is a dimensionless coefficient for the normalized temperature
shift.

**Use case:** Systems where kij varies significantly with temperature.

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.8);
fluid.addComponent("H2S", 0.2);
fluid.setMixingRule(8);  // Temperature-dependent classic
```

### 3.4 CLASSIC_T2 - Type 12

Alternative temperature-dependent formulation:

$$
k_{ij}(T) = k_{ij,0} + \frac{k_{ij,T}}{T}
$$

Here, $T$ is absolute temperature in kelvin. Because $k_{ij}$ is dimensionless,
$k_{ij,T}$ has units of kelvin in this inverse-temperature formulation.

**Use case:** Systems requiring inverse temperature dependency.

```java
fluid.setMixingRule(12);
```

### 3.5 Independent calculations using a shared handler

Configured fluid clones can share a classic mixing-rule handler. The general
covolume sum (`ClassicVdW.calcBFull`), classic attraction temperature second
derivative (`ClassicSRK.calcATT`), and composition-dependent attraction sum
(`ClassicSRKT2x.calcA`) accumulate their results locally. Interleaved evaluations
of different phases therefore cannot overwrite these calculations' partial sums.
This matters when independent compressor trains run in parallel: a corrupted
attraction derivative can change heat capacity and invalidate pressure-target
replay even when the chart flow, head, efficiency and speed agree.

The equations, summation order and public APIs are unchanged. Legacy last-result
fields remain available after serial evaluation; they are not a snapshot for
concurrent callers. Configure interaction parameters before evaluation and keep
each phase's component state independent. This guarantee concerns these three
sums with read-only configuration, not every mixing-rule family or concurrent
configuration changes. `ClassicMixingRuleAccumulatorTest` checks deterministic
interleaving against isolated SRK and PR calculations for all three sums.

---

## 4. Huron-Vidal Mixing Rules

Huron-Vidal (HV) mixing rules combine cubic EoS with activity coefficient models (like NRTL or UNIFAC) for improved liquid-phase behavior.

For mixed HV/classic pairs, the modified NRTL calculation resets the classic
pair's nonrandomness parameter to zero at every initialization level. Property-only
initialization (`init(1)`) must not inherit this parameter from a preceding HV pair.
`ComponentGENRTLmodifiedHVInitializationTest` checks the zero-interaction invariant
at initialization levels 1, 2 and 3.

### 4.1 Mathematical Framework

The infinite-pressure excess Gibbs energy is incorporated into the EoS:

$$a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} - \frac{g_\infty^E}{\Lambda} \right)$$

Here $g_\infty^E$ is **molar** excess Gibbs energy, in the same energy-per-mole
units as $a_i/b_i$. The positive EoS constant is $\Lambda=\ln 2$ for SRK and
$\Lambda=\ln[(2+\sqrt{2})/(2-\sqrt{2})]/(2\sqrt{2})\approx0.62322524$ for PR.
The implementation obtains this constant from the EoS delta parameters, including
when generating classical-pair interactions.

The modified NRTL expression uses covolume-weighted local compositions:
$G_{ji}=b_j\exp(-\alpha_{ji}\tau_{ji})$. It is not the ordinary mole-weighted
NRTL model, nor Michelsen's zero-pressure MHV1/MHV2 mixing rules. See
[Huron and Vidal (1979)](https://doi.org/10.1016/0378-3812(79)80001-1).

### 4.2 CLASSIC_HV - Type 3

Huron-Vidal mixing rule with NRTL parameters from the database. In the current
implementation, both type 3 and type 4 receive the database temperature offsets
during initialization. Type 3 is therefore not a way to disable nonzero
`HVDijT` coefficients; use explicitly zero offsets for that comparison.

```java
SystemInterface fluid = new SystemSrkEos(350.0, 10.0);
fluid.addComponent("methanol", 0.3);
fluid.addComponent("water", 0.4);
fluid.addComponent("methane", 0.3);
fluid.setMixingRule(3);  // or fluid.setMixingRule("CLASSIC_HV");
```

### 4.3 HV - Type 4

Enhanced Huron-Vidal with temperature-dependent parameters (HVDijT):

$$\tau_{ij}(T)=\frac{D_{ij}}{T}+D_{ij,T}$$

`HVDij` is in kelvin, `HVDijT` is dimensionless, and temperature is in kelvin.
Equivalently, the interaction energy divided by $R$ is
$D_{ij}+D_{ij,T}T$. The two directed coefficients need not be equal.
The temperature derivatives are $\partial\tau_{ij}/\partial T=-D_{ij}/T^2$
and $\partial^2\tau_{ij}/\partial T^2=2D_{ij}/T^3$; a nonzero offset still
affects the local-composition weights and their derivatives.

**Use case:** Polar/non-polar mixtures, alcohol-water-hydrocarbon systems.

```java
SystemInterface fluid = new SystemPrEos(320.0, 20.0);
fluid.addComponent("ethanol", 0.2);
fluid.addComponent("water", 0.5);
fluid.addComponent("propane", 0.3);
fluid.setMixingRule(4);  // or fluid.setMixingRule("HV");
```

### 4.4 Huron-Vidal with UNIFAC

For predictive calculations without fitted parameters, use UNIFAC:

```java
SystemInterface fluid = new SystemSrkEos(320.0, 15.0);
fluid.addComponent("ethanol", 0.3);
fluid.addComponent("n-hexane", 0.4);
fluid.addComponent("water", 0.3);

// HV with UNIFAC activity coefficients
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");
```

Available GE models for HV:
- `"NRTL"` - Non-Random Two-Liquid (default)
- `"UNIFAC_UMRPRU"` - UNIFAC with UMR-PRU parameters
- `"UNIFAC"` - Standard UNIFAC
- `"UNIFAC_PSRK"` - UNIFAC with PSRK parameters

### 4.5 Accessing HV Parameters

```java
// Get the mixing rule interface
HVMixingRulesInterface hvRule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();

// Get/Set HV parameters
double dij = hvRule.getHVDijParameter(0, 1);
hvRule.setHVDijParameter(0, 1, 500.0);  // Set Dij in K

double alpha = hvRule.getHValphaParameter(0, 1);
hvRule.setHValphaParameter(0, 1, 0.3);  // Set alpha (non-randomness)
```

### 4.6 Parameter selection and qualification

`HVTYPE` selects fitted modified-NRTL interactions for an individual pair.
Pairs marked `Classic`, including missing database pairs, use zero
nonrandomness and interaction energies derived from the cubic EoS and its
classical binary coefficient. This recovers the classical mixing rule when all
pairs are classical. It does **not** generate fitted polar parameters or provide
an independently validated water/inhibitor correlation for an unknown oil or
pseudocomponent pair. Stored HV coefficients on a pair marked `Classic` do not
activate an HV fit.

The database supports separate complete `_SRK` and `_PR` HV coefficient
families, as described in the [interaction-table guide](inter_table_guide.md#huron-vidal-parameters).
The optional bundled families are initially empty, so existing fluids retain
their shared legacy coefficients. A malformed selected family raises an error
instead of changing to classical mixing. The correct EoS constant alone does
not establish that shared fitted coefficients are accurate for both EoS.
Parameter provenance, pure-component alpha/properties, fit ranges and EoS
transferability must be checked against independent measurements for the
intended mixture.
Do not infer accuracy from convergence, finite properties, or phase count.

`HuronVidalLiteratureReproductionTest` preserves an explicit source-compatible
SRK-HV MEG/n-heptane configuration and seven measured states at 1 bara. It
reproduces the published mean absolute relative errors of 6.7% for heptane in MEG
and 21.2% for MEG in heptane. That fit requires the published MEG critical pressure
of 82 bara and Mathias-Copeman alpha coefficients; it does not replace the library
defaults. The source inventory is in
`src/test/resources/neqsim/thermo/mixingrule/hv-literature-candidates.json`.
Reproducing a published correlation does not establish agreement within the
measurement uncertainties or justify transferring its parameters to another EOS.

Numerical qualification covers analytical temperature and composition
derivatives, Gibbs-Duhem and Hessian symmetry, classical-rule recovery for both
EoS, directed-parameter ordering, and gas-oil-water material balance and fugacity
equilibrium with methanol and MEG. These checks establish implementation
consistency for the tested states; they are separate from experimental accuracy.
Hydrate-temperature depression is a trend check on the coupled hydrate model,
not a validation of dissociation temperatures for every inhibitor concentration.

`HuronVidalThermodynamicModelConsistencyTest` also uses the existing
`ThermodynamicModelTest` fugacity/Gibbs, composition, pressure and temperature
identity checks at their default absolute tolerance of $10^{-10}$ for its
specified feeds. The independent numerical derivative check uses the established
mixed absolute/relative tolerance of $10^{-6}$ and requires agreement between two
step sizes. Bounded step refinement resolves trace-component one-sided stencil
truncation without relaxing that tolerance; an unresolved check still fails.

`HuronVidalModelConsistencyTest` and `HuronVidalMultiphaseQualificationTest` also
assert all five `ThermodynamicModelTest` identities and independently compare
every component's $\partial\ln\phi_i/\partial T$, $\partial\ln\phi_i/\partial P$
and $\partial\ln\phi_i/\partial n_j$ against refined finite differences. Coverage
includes both rules 3/4, prescribed asymmetric interactions and nonzero offsets,
non-unit mole amounts, temperature changes and return, and equilibrated gas,
oil and aqueous phases with and without methanol or MEG. Derivative trials retain
each phase root, pressure and component amounts without reflashing. Additional
checks compare attraction temperature/mixed derivatives and residual heat
capacity with independent differences. The numerical checker may halve its step
up to four additional times near dilute amounts; it still rejects unresolved derivatives
at the requested tolerance and preserves the supplied fluid state.

A stalled cubic-volume iteration must not be accepted solely because its update
is small. The volume solver checks its EOS-equation residual before accepting
that convergence, using the existing analytical cubic fallback if needed. The
fixed-root qualification explicitly checks the returned volume against the
pressure equation, including the SRK-HV spinodal regression at 280 K and 20 bara.
This also removes a spurious third phase in a classical SRK CO2/methane/ethane/
water regression at 255 K and 90 bara: its former vapor volume satisfied only
54.73 bara. The corrected two-phase result satisfies the EOS equation and has
lower Gibbs energy. Three-phase topology protection uses the same feed at
255 K and 60 bara, where all three volumes satisfy the pressure equation.

Public experimental data for a separate physical assessment are available for
[MEG-water-methane from Folas et al. (2007)](https://trc.nist.gov/ThermoML/10.1016/j.fluid.2006.11.001.html).
This study compares SRK-HV and CPA. Agreement reported for a published parameter
set must not be attributed automatically to the current database or to PR-HV.
An assessment of the six ternary states at 278.15/298.15 K and 50/100/200 bara
using the database at `8f471dd7b28d0f151d203d6f880a8cb95cc7753a` gave the
following mean absolute relative deviations in phase mole fractions:

| Observable | SRK-HV | PR-HV |
|---|---:|---:|
| Liquid methane | 28.09% | 36.29% |
| Vapor MEG | 97.76% | 188.60% |
| Vapor water | 29.27% | 19.96% |

The feed was constructed as $z=(x_{measured}+y_{measured})/2$ and flashed at
the measured temperature and pressure without fitting. This is a measured-feed
assessment, not a calculation imposing the measured liquid composition.
The predicted liquid MEG concentration differed from the measurement by at most
0.50% relative. Maximum material-balance and log-fugacity residuals were
$6.28\times10^{-12}$ and $2.53\times10^{-11}$, respectively. These residuals
demonstrate numerical equilibrium, while the phase-composition deviations expose
a physical qualification gap. Experimental uncertainty and the published
parameter set still need to be reconciled; these deviations are not acceptance
tolerances. The reproducible example and follow-up are tracked in
[#4573](https://github.com/equinor/neqsim/issues/4573).

Salt-containing inhibitor solutions require a separately qualified electrolyte
model; the neutral HV checks here do not cover brines.

**Concurrent calculations:** `PhaseEos.clone()` shares the mixing-rule object.
For HV this includes mutable GE and attraction-derivative work state, in addition
to the configured parameters. Concurrent flashes on cloned siblings are not
qualified: an interleaved calculation on a sibling can overwrite this work state.
Use independently constructed and configured systems per concurrent worker,
or serialize complete operations on cloned siblings. The reproducer and required
state-isolation work are tracked in
[#4575](https://github.com/equinor/neqsim/issues/4575).

The implementation and literature-qualification plan is tracked in
[#4576](https://github.com/equinor/neqsim/issues/4576). Performance qualification
includes classical-rule SRK/PR baselines at matched composition, temperature,
pressure and initialization order, plus complete flashes with reported phase
topology. Changing the equilibrium model can change solver work and results;
timing alone does not measure equivalent physical accuracy.

---

## 5. Wong-Sandler Mixing Rule

### 5.1 Overview

The Wong-Sandler (WS) mixing rule provides theoretically correct behavior at both low and high densities by matching:
- The second virial coefficient of the mixture
- The excess Gibbs energy at infinite pressure

### 5.2 Mathematical Formulation

$$
b_{mix} = \frac{\sum_i \sum_j x_i x_j \left( b - \frac{a}{RT} \right)_{ij}}{1 - \frac{A_\infty^E}{CRT} - \sum_i x_i \frac{a_i}{RTb_i}}
$$

$$
a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} + \frac{A_\infty^E}{C} \right)
$$

Where $C$ is an EoS-specific constant and $A_\infty^E$ is the excess Helmholtz energy at infinite pressure.

### 5.3 WS - Type 5

```java
SystemInterface fluid = new SystemPrEos(350.0, 30.0);
fluid.addComponent("methanol", 0.2);
fluid.addComponent("water", 0.5);
fluid.addComponent("CO2", 0.3);
fluid.setMixingRule(5);  // or fluid.setMixingRule("WS");
```

### 5.4 Wong-Sandler with NRTL

```java
SystemInterface fluid = new SystemSrkEos(320.0, 20.0);
fluid.addComponent("acetone", 0.3);
fluid.addComponent("water", 0.4);
fluid.addComponent("methane", 0.3);

// WS with NRTL activity coefficients
fluid.setMixingRule("WS", "NRTL");
```

### 5.5 Accessing WS Parameters

```java
HVMixingRulesInterface wsRule = (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();

// Get/Set Wong-Sandler kij parameter
double kijWS = wsRule.getKijWongSandler(0, 1);
wsRule.setKijWongSandler(0, 1, 0.1);
```

**Use case:** High-pressure VLE, polar/non-polar mixtures, CO2 capture systems.

---

## 6. CPA Mixing Rules

For Cubic-Plus-Association (CPA) equations of state, specialized mixing rules handle both the cubic EoS part and the association term.

### 6.1 CPA_MIX - Type 7

Classic mixing rule with CPA-specific binary interaction parameters from the database.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(320.0, 50.0);
fluid.addComponent("water", 0.1);
fluid.addComponent("methane", 0.8);
fluid.addComponent("MEG", 0.1);
fluid.setMixingRule(7);  // CPA classic mixing
```

### 6.2 CLASSIC_T_CPA - Type 9

Temperature-dependent classic mixing rule for CPA systems:

```java
fluid.setMixingRule(9);
```

### 6.3 CLASSIC_TX_CPA - Type 10

Temperature and composition dependent mixing rule for CPA. This is the **recommended mixing rule for CPA systems** as it:
- Uses asymmetric kij parameters (kij ≠ kji)
- Includes temperature dependency
- Automatically selects the appropriate sub-type based on parameter symmetry

```java
SystemInterface fluid = new SystemSrkCPAstatoil(330.0, 100.0);
fluid.addComponent("water", 0.15);
fluid.addComponent("methane", 0.70);
fluid.addComponent("MEG", 0.10);
fluid.addComponent("CO2", 0.05);
fluid.setMixingRule(10);  // Recommended for CPA
```

### 6.4 Cross-Association Parameters

CPA mixing rules also handle association site interactions between different molecules:

| Association Scheme | Description | Examples |
|-------------------|-------------|----------|
| CR-1 | Cross-association with single site | Water-MEG |
| ER | Elliott combining rule | General polar systems |
| 4C | Four-site model | Water, glycols |
| 2B | Two-site model | Alcohols |

---

## 7. Søreide-Whitson Mixing Rule

### 7.1 Overview

The Søreide-Whitson mixing rule is specifically designed for systems containing:
- Sour gases (CO2, H2S, N2)
- Hydrocarbons
- Aqueous brine solutions

It uses composition and salinity-dependent binary interaction parameters.

### 7.2 SOREIDE_WHITSON - Type 11

```java
import neqsim.thermo.system.SystemSoreideWhitson;

SystemSoreideWhitson fluid = new SystemSoreideWhitson(350.0, 200.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("CO2", 0.15);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("water", 0.10);

// Add salinity (important for sour gas solubility)
fluid.addSalinity(2.0, "mole/sec");

fluid.setMixingRule(11);  // Søreide-Whitson mixing rule

// Optional refreshed eight-gas drop-in parameterization
fluid.setSoreideWhitsonParameterization("BURGOYNE_NIELSEN_2026");
```

### 7.3 Salinity-Dependent kij

The mixing rule calculates kij for water-gas interactions based on salinity (S in mol/kg):

**For CO2-water:**
$$
k_{ij} = -0.31092(1 + 0.156 S^{0.75}) + 0.236(1 + 0.178 S^{0.98}) T_r - 21.26 e^{-6.72^{T_r} - S}
$$

This is the `LEGACY` default. For CO₂-NaCl brine, `CHABAB_2019` replaces only this aqueous
pair correlation with the 2019 refit; see [Søreide-Whitson Model](SoreideWhitsonModel.md) for
the equation, coefficients, units, validity range, and comparison example.

`BURGOYNE_NIELSEN_2026` refreshes aqueous and non-aqueous water-gas BIPs for CO₂, H₂S, methane,
nitrogen, hydrogen, ethane, propane, and n-butane while retaining the original water alpha and
the `LEGACY` default. See the model guide for validation scope and property-lineage caveats.

**For N2-water:**
$$
k_{ij} = -1.702(1 + 0.026 S^{0.75}) + 0.443(1 + 0.081 S^{0.75}) T_r
$$

**For hydrocarbons-water:**
$$
k_{ij} = (1 + a_0 S) A_0 + (1 + a_1 S) A_1 T_r + (1 + a_2 S) A_2 T_r^2
$$

Where $T_r$ is the reduced temperature and the $A$ and $a$ coefficients depend on the acentric factor.

**Use case:** Reservoir fluids with aqueous phases, sour gas processing, CCS with brine.

---

## 8. Binary Interaction Parameters (kij)

### 8.1 Default Database Values

NeqSim loads kij values from its internal database when you call `setMixingRule()`. These are stored in the `inter` table.

### 8.2 Setting Custom kij Values

```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("CO2", 0.1);
fluid.setMixingRule("classic");

// Get the mixing rule interface
EosMixingRulesInterface mixRule = fluid.getPhase(0).getMixingRule();

// Set custom kij (symmetric: kij = kji)
mixRule.setBinaryInteractionParameter(0, 1, 0.12);

// Get current kij value
double kij = mixRule.getBinaryInteractionParameter(0, 1);
System.out.println("kij(CH4-CO2) = " + kij);
```

### 8.3 Setting Asymmetric kij (for CPA)

```java
// For CPA systems with asymmetric parameters
mixRule.setBinaryInteractionParameterij(0, 1, 0.08);  // kij
mixRule.setBinaryInteractionParameterji(0, 1, 0.12);  // kji
```

### 8.4 Setting Temperature-Dependent kij

Select the temperature-dependent rule before obtaining its mixing-rule interface. The coefficient
meaning depends on the selected formulation.

```java
SystemInterface temperatureDependentFluid = new SystemSrkEos(300.0, 50.0);
temperatureDependentFluid.addComponent("methane", 0.9);
temperatureDependentFluid.addComponent("CO2", 0.1);
temperatureDependentFluid.setMixingRule(EosMixingRuleType.CLASSIC_T);

EosMixingRulesInterface temperatureDependentRule =
    temperatureDependentFluid.getPhase(0).getMixingRule();
temperatureDependentRule.setBinaryInteractionParameter(0, 1, 0.10);
temperatureDependentRule.setBinaryInteractionParameterT1(0, 1, 0.001);
```

For `CLASSIC_T`, the values above mean $k_{ij}(273.15\ \mathrm{K}) = 0.10$ and a
dimensionless normalized-temperature coefficient of 0.001. For `CLASSIC_T2`, the second
parameter instead has units of kelvin because it is divided by $T$. CPA rules 9 and 10 use the
temperature-dependence type supplied by the interaction-parameter database.

### 8.5 Displaying kij Matrix

```java
double[][] kijMatrix = mixRule.getBinaryInteractionParameters();
for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
    for (int j = 0; j < fluid.getNumberOfComponents(); j++) {
        System.out.printf("kij[%d][%d] = %.4f  ", i, j, kijMatrix[i][j]);
    }
    System.out.println();
}
```

---

## 9. Complete Reference Table

| Type | Name | String Key | Description | Best For |
|------|------|------------|-------------|----------|
| 1 | `NO` | `"NO"` | All kij = 0 | Quick estimates, ideal mixtures |
| 2 | `CLASSIC` | `"classic"` | Classic van der Waals with database kij | General hydrocarbons |
| 3 | `CLASSIC_HV` | `"CLASSIC_HV"` | Huron-Vidal with database parameters | Polar mixtures |
| 4 | `HV` | `"HV"` | Huron-Vidal with T-dependent parameters | Alcohol-water-HC |
| 5 | `WS` | `"WS"` | Wong-Sandler | High-pressure polar systems |
| 7 | `CPA_MIX` | `"CPA_MIX"` | Classic for CPA systems | Water-HC with CPA |
| 8 | `CLASSIC_T` | `"CLASSIC_T"` | T-dependent kij (linear) | Temperature-sensitive kij |
| 9 | `CLASSIC_T_CPA` | `"CLASSIC_T_CPA"` | T-dependent for CPA | CPA with T-dependency |
| 10 | `CLASSIC_TX_CPA` | `"CLASSIC_TX_CPA"` | T and x dependent for CPA | **Recommended for CPA** |
| 11 | `SOREIDE_WHITSON` | `"SOREIDE_WHITSON"` | Salinity-dependent | Sour gas, brine systems |
| 12 | `CLASSIC_T2` | `"CLASSIC_T2"` | T-dependent (inverse) | Alternative T-dependency |

---

## 10. Examples by Application

### 10.1 Natural Gas Processing

```java
SystemInterface gas = new SystemSrkEos(280.0, 70.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.03);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.06);
gas.addComponent("propane", 0.04);
gas.setMixingRule("classic");  // Type 2

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();
```

### 10.2 Glycol Dehydration (CPA)

```java
SystemInterface fluid = new SystemSrkCPAstatoil(320.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("water", 0.05);
fluid.addComponent("TEG", 0.15);  // Triethylene glycol
fluid.setMixingRule(10);  // CLASSIC_TX_CPA - recommended for CPA

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.3 Amine Gas Treating

```java
SystemInterface fluid = new SystemSrkEos(323.0, 20.0);
fluid.addComponent("CO2", 0.15);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("methane", 0.60);
fluid.addComponent("MDEA", 0.10);  // Methyldiethanolamine
fluid.addComponent("water", 0.10);
fluid.setMixingRule("HV", "NRTL");  // Huron-Vidal with NRTL

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.4 CO2 Capture and Storage

```java
SystemInterface fluid = new SystemPrEos(350.0, 150.0);
fluid.addComponent("CO2", 0.90);
fluid.addComponent("nitrogen", 0.05);
fluid.addComponent("oxygen", 0.02);
fluid.addComponent("water", 0.03);
fluid.setMixingRule("WS");  // Wong-Sandler for high pressure

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

### 10.5 Sour Gas with Brine

```java
SystemSoreideWhitson fluid = new SystemSoreideWhitson(380.0, 250.0);
fluid.addComponent("methane", 0.65);
fluid.addComponent("CO2", 0.20);
fluid.addComponent("H2S", 0.05);
fluid.addComponent("water", 0.10);
fluid.addSalinity("NaCl", 3.0, "mole/sec");  // Add NaCl salinity
fluid.setMixingRule(11);  // Søreide-Whitson

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());
fluid.prettyPrint();
```

### 10.6 Pharmaceutical/Chemical (UNIFAC Predictive)

```java
SystemInterface fluid = new SystemSrkEos(298.0, 1.0);
fluid.addComponent("ethanol", 0.3);
fluid.addComponent("acetone", 0.3);
fluid.addComponent("water", 0.4);
fluid.setMixingRule("HV", "UNIFAC_UMRPRU");  // Predictive UNIFAC

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
```

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide.md) - Complete guide to creating fluids
- [Mathematical Models](mathematical_models.md) - EoS equations and derivations
- [Thermodynamic Workflows](thermodynamic_workflows.md) - Flash calculations and operations
- [PVT and Fluid Characterization](pvt_fluid_characterization.md) - Heavy fraction handling
