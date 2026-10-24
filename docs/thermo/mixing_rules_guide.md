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

Huron-Vidal (HV) mixing rules combine a cubic EoS with an excess-Gibbs-energy
model for mixtures whose liquid-phase non-ideality is not represented adequately
by a classic binary-interaction rule.

For mixed HV/classic pairs, the modified NRTL calculation resets the classic
pair's nonrandomness parameter to zero at every initialization level. Property-only
initialization (`init(1)`) must not inherit this parameter from a preceding HV pair.
`ComponentGENRTLmodifiedHVInitializationTest` checks the zero-interaction invariant
at initialization levels 1, 2 and 3.

### 4.1 Mathematical framework

The excess Gibbs energy from the selected activity-coefficient model is incorporated
into the EoS:

$$a_{mix} = b_{mix} \left( \sum_i x_i \frac{a_i}{b_i} - \frac{G^E}{\Lambda} \right)$$

Here, $G^E$ is the excess Gibbs energy and $\Lambda$ is the EoS-specific
constant (approximately 0.693 for SRK).

`CLASSIC_HV` (type 3) constructs the modified NRTL rule with the database
`HVDij` matrix only. `HV` (type 4) also supplies `HVDijT`, so its
dimensionless directional NRTL parameter is evaluated in source as

$$\tau_{ij}(T) = \frac{D_{ij}}{T} + D_{ij,T}.$$

Equivalently, define an effective energy numerator
$D_{ij}^{\mathrm{eff}}(T)=D_{ij}+D_{ij,T}T$ and divide it by $T$.
Consequently, `Dij` has units of kelvin and `DijT` is dimensionless; it is
not a second kelvin-valued slope. `HVMixingRulesInterface` exposes both
directional matrices and the dimensionless nonrandomness parameter `alpha`.

### 4.2 Complete HV/NRTL example

The program below selects type 4 by name, performs a TP flash, reads the
directional NRTL parameters actually installed on the phase, and verifies both
finite results and total-mole conservation. Run it with assertions enabled
(`java -ea`).

<!-- hv-nrtl-example:start -->
```java
import neqsim.thermo.mixingrule.HVMixingRulesInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HuronVidalNrtlExample {
  private static final Logger logger = LogManager.getLogger(HuronVidalNrtlExample.class);

  private HuronVidalNrtlExample() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(350.0, 10.0);
    fluid.addComponent("methanol", 0.30);
    fluid.addComponent("water", 0.40);
    fluid.addComponent("methane", 0.30);
    fluid.setMixingRule("HV", "NRTL");

    double feedMoles = fluid.getTotalNumberOfMoles();
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);

    assert "Huron-Vidal".equals(fluid.getMixingRuleName());

    HVMixingRulesInterface hvRule =
        (HVMixingRulesInterface) fluid.getPhase(0).getMixingRule();
    double temperatureK = fluid.getTemperature();
    double dijK = hvRule.getHVDijParameter(0, 1);
    double dijT = hvRule.getHVDijTParameter(0, 1);
    double alpha = hvRule.getHValphaParameter(0, 1);
    double tau = dijK / temperatureK + dijT;

    assert Double.isFinite(dijK);
    assert Double.isFinite(dijT);
    assert Double.isFinite(alpha);
    assert Double.isFinite(tau);

    double recoveredMoles = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      double phaseMoles = phase.getNumberOfMolesInPhase();
      assert Double.isFinite(phaseMoles) && phaseMoles >= 0.0;
      assert Double.isFinite(phase.getDensity()) && phase.getDensity() > 0.0;
      recoveredMoles += phaseMoles;
    }
    assert Math.abs(recoveredMoles - feedMoles)
        <= 1.0e-10 * Math.max(1.0, feedMoles);

    logger.info(
        "rule {}; phases {}; Dij {} K; DijT {}; alpha {}; tau {}; recovered moles {}",
        fluid.getMixingRuleName(),
        fluid.getNumberOfPhases(),
        dijK,
        dijT,
        alpha,
        tau,
        recoveredMoles);
  }
}
```
<!-- hv-nrtl-example:end -->

### 4.3 GE-model selector

The second argument to `setMixingRule("HV", geModel)` chooses the
excess-Gibbs-energy implementation. The type-4 handler currently accepts:

- `"NRTL"` — modified NRTL with `HVDij` and `HVDijT`.
- `"NRTL_HV"` — the legacy modified-NRTL HV construction.
- `"UNIFAC"` — standard UNIFAC.
- `"UNIFAC_PSRK"` — UNIFAC with PSRK parameters.
- `"UNIFAC_UMRPRU"` — UNIFAC with UMR-PRU parameters.

`"UNIQUAC"` is explicitly rejected for this mixing-rule path. Treat selector
strings as source-level contracts: configure the final component list first,
then create the database and select the rule before changing or reading custom
interaction parameters.

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
