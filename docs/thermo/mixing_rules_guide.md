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

Both `CLASSIC_HV` (type 3) and `HV` (type 4) receive the database temperature
offsets during initialization. Type 3 therefore does not disable nonzero
`HVDijT` coefficients; use explicitly zero offsets for that comparison. The
dimensionless directional NRTL parameter is evaluated as

$$\tau_{ij}(T) = \frac{D_{ij}}{T} + D_{ij,T}.$$

Equivalently, define an effective energy numerator
$D_{ij}^{\mathrm{eff}}(T)=D_{ij}+D_{ij,T}T$ and divide it by $T$.
Consequently, `Dij` has units of kelvin and `DijT` is dimensionless; it is
not a second kelvin-valued slope. `HVMixingRulesInterface` exposes both
directional matrices and the dimensionless nonrandomness parameter `alpha`.
The two directed coefficients need not be equal. The temperature derivatives
are $\partial\tau_{ij}/\partial T=-D_{ij}/T^2$ and
$\partial^2\tau_{ij}/\partial T^2=2D_{ij}/T^3$; a nonzero offset still
affects the local-composition weights and their derivatives.

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

### 4.4 Parameter selection and qualification

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

The same explicit source-paired MEG alpha is also checked against 15 existing
NIST pure-MEG vapor pressures at 307.7–384.5 K. Its pressure AARD is 5.86%, with
15 predictions inside the supplied compiler-expanded 95% point-pressure
uncertainties. This comparison does not propagate input or fitted-parameter
uncertainty and does not qualify inhibitor partition in mixtures. The native
identity and refined numerical checks are asserted for the source-compatible
liquids, changed/repeated/reversed-order states and pure coexistence. Pure
coexistence uses equal phase amounts at the unchanged bubble pressure to avoid
roundoff in mole differences on the solver's infinitesimal incipient phase;
mixture phase fractions are retained. The
[source-compatible assessment protocol and raw results](../../src/test/resources/neqsim/thermo/mixingrule/hv-source-compatible/README.md)
record this numerical conditioning limitation and the separate physical errors.

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

**Concurrent calculations:** for the standard modified-NRTL HV implementation,
`PhaseEos.clone()` copies the parameter handler, HV rule, GE phase/components and
retained attraction/derivative work arrays. Runtime-tuned directed coefficients,
nonrandomness, pair activation and pure-alpha coefficients survive copying;
changing them on a clone does not retune its source. Copying performs no database
reload or activity-coefficient reevaluation. Serialization retains the detached
ownership graph.

The regression matrix covers SRK/PR, rules 3/4, initialization levels 1/3,
deterministic temperature/composition interleaving, tuned methanol/water and
parallel sibling flashes of neutral methane/decane/water with MEG or methanol. Each worker owns its
fluid and operations object. Configure and finish cloning before launching workers;
concurrent mutation or cloning of the same live fluid is outside this contract.
Other mixing rules, HV with other GE implementations, reactive fluids, hydrate/solid
coupling and whole-process concurrency retain their separate ownership requirements.
Independently constructed and configured worker fluids remain a safe alternative.
The reproducer, scope and measured copy-cost follow-up are tracked in
[#4575](https://github.com/equinor/neqsim/issues/4575).

See the [clone qualification and raw cost matrix](../../src/test/resources/neqsim/thermo/mixingrule/hv-clone-performance/README.md)
for the executed ownership contract and timing limitations. The
[thermo-fluid agent](../../.github/agents/thermo-fluid.agent.md) carries the same
worker-setup restrictions.

The implementation and literature-qualification plan is tracked in
[#4576](https://github.com/equinor/neqsim/issues/4576). Performance qualification
includes classical-rule SRK/PR baselines at matched composition, temperature,
pressure and initialization order, plus complete flashes with reported phase
topology. Changing the equilibrium model can change solver work and results;
timing alone does not measure equivalent physical accuracy.

The modified-NRTL evaluator reduces temporary allocation by reusing each
component's independently owned composition-derivative vector and clearing it
at every initialization level. Its derivative matrices remain local to each
call; flattening their storage avoids separate row objects without retaining
larger caches on cloned fluids. The equations and arithmetic order are unchanged.
The [allocation qualification matrix](../../src/test/resources/neqsim/thermo/mixingrule/hv-allocation-performance/README.md)
records fresh-master comparisons, exact activity/derivative fingerprints,
classical controls and timing limitations. Lower allocation alone does not
establish a universal flash speedup or resolve the physical-accuracy gaps above.

Within each modified-NRTL column reduction, classical-pair pure column energy
and its requested temperature derivatives are evaluated once and reused for
the remaining rows. This reuse is lazy and local to one evaluator call: it
introduces no retained cache, clone state, allocation or parameter selection.
The row contributions and their summation order remain unchanged. Fixed-T/P
alpha-parameter changes are covered by the workspace lifecycle regression.
See the [column-evaluation protocol and controls](../../src/test/resources/neqsim/thermo/mixingrule/hv-column-performance/README.md)
for matched compiler settings, independent JVMs, identical-binary controls,
classical-rule comparisons, full equilibrium states and timing limitations.
Use the saved phase types when interpreting these measurements: the one-phase
initialization fixtures finish as GAS for 4 components and OIL for 20 components,
despite initially requesting a gas root. They do not establish global one-phase
stability. The complete-flash fixtures retain gas, oil and aqueous phases.

The [request and selector regression](../../src/test/resources/neqsim/thermo/mixingrule/hv-row-pure/README.md)
freezes existing pure-function counts and complete activity/derivative values
for both EOS families, selected/classical/all-HV cells, every evaluated
component and padded/distinct raw selector strings. The primary reduction's
trimmed comparison and the nested reduction's exact comparison remain unchanged.
These synthetic fixtures do not introduce physical parameter profiles.

The [binary qualification protocol](../../src/test/resources/neqsim/thermo/mixingrule/hv-row-performance/README.md)
retains warmed independent JVM comparisons, identical-binary controls,
allocations, full equilibrium vectors and rejected row-cache/selector proposals.
Those proposals passed numerical checks yet failed the performance gate, so
they are held outside production. Lower request counts alone do not establish
lower full-flash cost or justify a runtime change.

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
| CR-1 | Cross energy and volume parameters, with arithmetic/geometric combining defaults | Water-TEG |
| ER | Elliott combining rule | General polar systems |
| 4C | Four-site model | Water, glycols |
| 2B | Two-site model | Alcohols |

### 6.5 Caloric consistency

At fixed pressure and component amounts, a single phase must satisfy

$$h^{\mathrm{res}}=-RT^2\left(\frac{\partial\sum_i x_i\ln\phi_i}{\partial T}\right)_{P,n},\qquad C_p=\left(\frac{\partial H}{\partial T}\right)_{P,n}.$$

Here $h^{\mathrm{res}}$ is residual molar enthalpy in J/mol, $R$ is in J/(mol K),
$T$ is in K, $x_i$ is mole fraction, $\phi_i$ is fugacity coefficient, and $H$
and $C_p$ are extensive enthalpy and heat capacity. These checks apply to an
explicit single-phase root; an equilibrium multiphase temperature perturbation
also changes the phase split.

For temperature-independent association parameters and fixed volume, the CR-1
strength has the form $\Delta=g b\beta[\exp(\epsilon/(RT))-1]$. Its explicit
temperature derivative contains $\exp(\epsilon/(RT))$, with no subtraction of
one. The radial distribution $g$ is constant in this partial derivative;
finite differences of $\Delta/g$ isolate the same explicit dependence.
The ER rule instead combines the pure-component strengths geometrically.
Here $g$ and $\beta$ are dimensionless, $b$ is the cross co-volume in m³/mol,
and $\epsilon$ is association energy in J/mol, so $\Delta$ is in m³/mol.

For rule 10, both the first and second temperature derivatives of the cubic
attraction must use the same composition-dependent $k_{ij}$ as the attraction
itself. These analytical derivatives feed enthalpy, entropy and heat capacity.
Correcting them changes caloric results for affected mixtures without changing
the attraction or association-strength expressions used for phase equilibrium.
`CPATemperatureConsistencyTest` covers the water/TEG case reported in
[#4634](https://github.com/equinor/neqsim/issues/4634), nearby compositions and
temperatures, pure-component controls and alternative mixing rules.

The same checks cover PR-CPA, SRK-CPA, simplified SRK-CPA, electrolyte-CPA (a neutral
water/TEG control), UMR-CPA and the implicit/reduced/Anderson/Broyden SRK-CPA
solvers. Volume-translated UMR-CPA also differentiates its implemented,
temperature-independent Peneloux translation as a constant. A stored database
temperature coefficient does not enter that translation expression and must not
be substituted for its derivative.

`SystemUMRCPAvolcor` refreshes the extensive translation and its derivative caches
after component initialization and before solving the volume or evaluating
fugacities. This ordering also applies after composition or phase-inventory changes;
using the preceding inventory's translation can corrupt the aqueous phase split.
`PhaseUMRCPAvolcorTest` checks the translation derivatives and EOS pressure equation,
plus repeated methane/water flashes with component conservation and phase-fugacity
equality. These are numerical consistency checks, not experimental qualification
of the UMR-CPA parameter set.

For the built-in arithmetic volume-translation rule, the mixture translation and
its first-derivative caches use factored sums with linear component-count cost.
Subclasses retain the general pair-rule calculation and virtual mixing hooks;
second-derivative storage remains quadratic. `PhaseUMRCPAvolcorMixingRuleTest`
compares the factored expressions with independent pair sums across component
inventories and mole scales, including custom pair rules and nonzero temperature
responses. The equilibrium solver and its acceptance tolerances are unchanged.

`SystemPrCPA` uses the existing PR-based CPA mass-action solver and analytical
site responses, with its own generic PR-CPA component parameters and PR alpha.
It does not select the dedicated UMR-CPA parameter or alpha family merely
because it shares that solver. `PrCpaImplementationTest` additionally checks
mass-action residuals, the non-associating PR limit, site sensitivities, phase
equilibrium, repeated initialization, cloning and serialization for the repair
of [#4636](https://github.com/equinor/neqsim/issues/4636).
See the [PR-CPA model and compatibility notes](thermodynamic_models.md#42-available-cpa-models).
This consistency coverage does not establish experimental mixture accuracy or
qualify ionic mixtures.

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


### Pure cubic saturation root qualification

For an ordinary pure SRK or PR phase, `CubicPureSaturation` locates the two
mechanical spinodals and brackets equal liquid/vapor fugacity strictly between
them. The bubble-pressure calculation uses that pressure to initialize distinct
physical roots. It does not accept identical roots merely because their fugacity
coefficients agree. The helper does not mutate its supplied phase, introduce
fitted parameters, or run on mixture TP-flash paths. The inner density and spinodal
bisections stop when rounding makes the midpoint equal an endpoint: further
steps cannot change the returned value. The existing 100-step cap and outer
pressure tolerance are unchanged. Matched baseline/candidate measurements and
bitwise pressure comparisons are retained in
`src/test/resources/neqsim/thermo/phase/cubic-saturation-performance/`.


`CubicPureSaturationTest` checks hydrogen at 20.369 K, CO2 at 302 K, methane at
150 K and water at 373.15 K for both EOS, with three starting pressures each.
Reference pressures come from independently solving the spinodal quartic and
cubic pressure polynomial with NumPy, then bisecting the log-fugacity difference.
The complete scalar inputs and results are retained in
`src/test/resources/neqsim/thermo/mixingrule/hv-pure-cubic-saturation-reference.json`,
with its standalone reproduction script. These are equation-implementation
checks, not measured vapor-pressure accuracy or a new parameter fit.

This qualification excludes CPA, electrolytes, translated-volume phases, SAFT,
mixture saturation and critical/dew continuation. Broader TV, stability and
caloric compatibility of the stalled-volume correction remains tracked in
[issue #4579](https://github.com/equinor/neqsim/issues/4579).


### TV endpoint and caloric qualification

Rejecting stalled cubic volumes changes the UMR-PRU/Mathias-Copeman, rule-2,
quadratic-covolume 50-addition TV fixture from a homogeneous gas to gas plus oil.
Independent evaluation at the same temperature, volume and component inventories
lowers Helmholtz energy by 25.868 kJ. Five perturbed-start SciPy minimizations
recover the same two-phase local minimum. This rejects the former homogeneous
energy reference; it does not prove global stability for arbitrary mixtures.

The independent residual enthalpy is -534369.102300 J. The new independent addition-history regression retains the
original 1 J absolute energy tolerance and compares total enthalpy after subtracting
the component ideal-gas contributions, so independent ideal heat-capacity
maintenance does not alter the residual reference. Its TV calculation exceptions
fail the test directly. The legacy total-enthalpy golden check remains unchanged
and is still tracked as compatibility debt in issue #4579. The corresponding `CubicTvHelmholtzQualificationTest` checks three
nearby temperatures, material/volume/pressure/fugacity closure, lower Helmholtz
energy and residual enthalpy from refined fixed-volume temperature differences.
Complete molar inventories, pure/cross coefficients, units, protocol and the
NumPy/SciPy reproduction are under
`src/test/resources/neqsim/thermodynamicoperations/flashops/tv-helmholtz/`.
These are independent equation-implementation checks, not empirical calibration.
Remaining stability, process and strict-gradient compatibility is tracked in
[issue #4579](https://github.com/equinor/neqsim/issues/4579).


### Cubic fallback precision and cost

The cubic fallback obtains the largest root with Cardano's formula and recovers
the other roots by stable quadratic deflation. For largest root $r$, the remaining
quadratic has constant coefficient $v=-c_0/r$ and linear coefficient
$u=(v-c_1)/r$. Computing $u$ this way avoids subtracting two numbers near unity
at dilute pressures. A stable quadratic formula then retains the small liquid
roots. Roots inside the excluded volume are rejected. The trigonometric branch
needs only the largest root, saving two redundant cosine evaluations.

`CubicAnalyticalRootPressureTest` checks SRK/PR water, CO2, methane and hexadecane
from $10^{-10}$ to 200 bara at three molar inventory scales. It checks pressure,
excluded volume, mechanical stability and root order through both direct fallback
and public forced-root initialization. Low-pressure liquid branches may be
metastable; this is EOS root qualification, not saturation or measurement accuracy.
The previous fallback failed pressure closure in 25 of 128 direct checks; the
repaired roots agree with independent 80-digit Decimal calculations to about
$3.5\times10^{-15}$ relative volume error for the saved scalar inputs. Full
baseline/input/reference data and the standalone reproduction script are under
`src/test/resources/neqsim/thermo/phase/cubic-root-deflation/`.

The change uses local primitive arithmetic and the existing three-root array;
it adds no shared cache or per-call allocation. Complete-flash cost must still
be distinguished from isolated root evaluation and from changed phase equilibria.
Matched classical-rule/HV timing evidence and remaining compatibility failures
are tracked in [issue #4579](https://github.com/equinor/neqsim/issues/4579).


### Public MEG measured-data assessment

The source-transcribed MEG/water/methane and pure-MEG measurements in
[src/test/resources/neqsim/thermo/mixingrule/hv-meg-qualification/README.md](../../src/test/resources/neqsim/thermo/mixingrule/hv-meg-qualification/README.md)
are exercised by HuronVidalMegExperimentalAssessmentTest. It asserts native
fugacity identities and numerical derivatives, balances, distinct pure roots and
state-history/component-order invariance. The generated CSVs under
target/hv-meg-assessment keep experimental errors and point uncertainties visible.
The separate six-state Folas 2007 report preserves the original endpoint-derived
feed protocol, z = 0.5 x + 0.5 y. It reports the resulting liquid-MEG shift and is
distinct from the measured overall feeds of Kruger 2018. Missing uncertainty is
not replaced with a nominal percentage; the ambiguous binary-gas dataset is excluded.

Passing these numerical tests does not establish measured accuracy: the recorded
default SRK/PR-HV vapor-MEG errors are substantial. No empirical accuracy tolerance,
new alpha function, fitted parameter or default retuning is introduced. Preserve
missing observations, raw rounded feeds, source uncertainty scales and the distinction
between liquid mass fractions and mole fractions. The fixture does not qualify oil
partition, hydrate dissociation, brine, ice, clone concurrency or flash throughput.

### Staged PR water/gas literature and bidirectional nitrogen assessment

The research catalog now records seven complete water-first PR-HV tuples from
Yushchenko/Brusilovsky2016 Table1, with visually verified signs, units, directed
order and infinite-pressure covolume-weighted convention. They remain staged;
no production database or default selection is changed. Expanded equations in
the source contain index/sign inconsistencies; preserve the thermodynamically
derived GE/fugacity expressions rather than transcribing those expansions.

`HuronVidalWaterNitrogenAssessmentTest` assesses only the nitrogen tuple against
35 water-in-nitrogen and52 nitrogen-in-water observations. It asserts native
thermodynamic identities and numerical derivatives for SRK-HV/PR-HV defaults
and the staged PR configuration, including component-order/state-history checks.
The equal-mole feed is synthetic, and current library pure inputs are reported:
the exact fitted-source Tc/Pc/acentric configuration remains unresolved.
A pure-water vapor-pressure/alpha audit is required before attributing errors
to the interaction parameters.

The staged transfer AARD is8.44% for water in gas and32.02% in the reverse
direction, over all87 observations. Only11 observations lie inside the source's
stated T/P emphasis. Numerical consistency and improvement over defaults do
not establish experimental accuracy or throughput acceptance. See the
[fixture protocol and raw reports](../../src/test/resources/neqsim/thermo/mixingrule/hv-water-nitrogen/README.md).
The other six cataloged PR pairs, inhibitors, brines, ice and hydrate dissociation
remain outside this assessment. No source-fit error becomes a test tolerance.


### Staged CO2/water source configurations

The separate [CO2 fixture protocol](../../src/test/resources/neqsim/thermo/mixingrule/hv-co2-source/README.md)
records the recovered2016 PR tuple and paired2017 SRK/PR Twu configurations.
These are declared-input research diagnostics, not new defaults or an accepted
public profile. Complete directed tuples and explicit pure critical/cubic inputs
remain separate by EOS. Original-author pure inputs are not fully established.

The native test asserts all five ThermodynamicModelTest identities, numerical
derivatives, serialization preservation, repeated/changed/order behavior and
independently resolved liquid temperature differences. All357 usable
measurement/profile flashes close numerically; physical errors remain visible.
The2016 transfer has75.87%/83.65% reverse-solubility AARD on two post-fit studies.
The2017 declared SRK/PR configurations perform better, but most reverse
predictions still exceed author uncertainty. No empirical error becomes a test
tolerance. One original malformed pressure remains explicitly unresolved.

The matched binary benchmark records CLASSICAL/HV init1/init3 and complete
nearby-temperature flashes in separate JVMs, with untimed endpoint topology,
compositions and residuals. Model differences change solver work; timing ratios
do not isolate code overhead or qualify physical accuracy. Preserve every raw
fork and round, including adverse/control variability. The separate forced-phase five-component control preserves ordinary kij for
every unselected pair, checks dormant-coefficient invariance and CLASSICAL
reduction, and asserts all native checks across both component orders and changed
states. It does not qualify multicomponent equilibrium partition. General
throughput, source/pure-pressure reconciliation,
light-gas inhibitor partition and independent hydrate accuracy remain open.

The [selected-pair multicomponent cost protocol](../../src/test/resources/neqsim/thermo/mixingrule/hv-selected-pair-performance/README.md)
extends this characterization to five- and fifteen-component synthetic feeds,
including gas/oil/aqueous flashes and allocation counters. It uses the same staged
CO2/water profiles; methanol, MEG and all other unselected interactions remain
CLASSICAL. All native identities, detached numerical checks, history/order and
full CLASSICAL reduction are asserted before timing. Complete phase vectors and
an independent raw-data auditor distinguish numerical closure from physical
inhibitor qualification. No production optimization or default retuning follows
from these timing ratios.


The same staged fixture also checks native pure-water saturation against14
independently bracketed roots and IAPWS reference pressures, using the declared
paired2017 Twu inputs. Both mixing rules, starting pressures and repeated calls
retain distinct stable roots. This reconciles implementation under the declared
pure inputs; original-author input reconstruction and mixture accuracy remain open.
The600K pure point does not qualify mixture extrapolation.

The staged pure-CO2 prerequisite now compares all three declared profiles with
24 independent stable-root solutions and a pinned CO2 reference correlation.
The executable fixture tests384 native fresh/repeated/changed/returned
saturations, rules2/4 and two pressure starts; all native identities and refined
numerical checks are asserted. See the fixture protocol for coefficients,
reference limits, errors and commands. This confirms implementation under the
declared pure inputs; it does not activate parameters or establish source-author
reproduction, bidirectional partition accuracy, solid/hydrate equilibrium or
mixture extrapolation.
