---
title: "Wax Characterization and Modeling"
description: "Comprehensive guide to wax precipitation modeling in NeqSim. Covers four thermodynamic models (Pedersen, Won, Wilson, Coutinho UNIQUAC), model selection API, parameter tuning, WAT calculation, wax curve generation, and fitting to experimental data."
---

# Wax Characterization and Modeling

## Table of Contents

- [Overview](#overview)
- [Wax Formation Theory](#wax-formation-theory)
- [Available Wax Models](#available-wax-models)
- [Model Selection API](#model-selection-api)
- [Getting Started](#getting-started)
- [Wax Curve Calculation](#wax-curve-calculation)
- [Fitting to Experimental Data](#fitting-to-experimental-data)
- [Flow Assurance Applications](#flow-assurance-applications)
- [Model Comparison and Selection Guide](#model-comparison-and-selection-guide)
- [References](#references)

---

## Overview

**Package:** `neqsim.thermo.characterization`, `neqsim.thermo.component`, `neqsim.thermo.phase`

Wax precipitation is a major flow assurance concern in oil and gas production, particularly in:
- Subsea pipelines (cold seabed, long tiebacks)
- Cold climate operations
- Production restarts after shutdown
- Waxy crude oil transport

NeqSim provides four thermodynamic wax models, all integrated into the
multiphase flash framework so that wax equilibrium is solved simultaneously
with vapour-liquid and other solid equilibria.

### Key Classes

| Class | Package | Description |
|-------|---------|-------------|
| `WaxCharacterise` | `thermo.characterization` | Main wax characterization and parameter management |
| `PhaseWax` | `thermo.phase` | Wax phase with configurable thermodynamic model |
| `ComponentWax` | `thermo.component` | Pedersen model (default) |
| `ComponentWonWax` | `thermo.component` | Won model |
| `ComponentWaxWilson` | `thermo.component` | Wilson local-composition model |
| `ComponentCoutinhoWax` | `thermo.component` | Coutinho predictive UNIQUAC model |
| `WaxFractionSim` | `pvtsimulation.simulation` | Wax curve simulation and parameter fitting |
| `WaxCurveCalculator` | `pvtsimulation.flowassurance` | Wax curve with monotonicity enforcement |
| `WaxFunction` | `pvtsimulation.util.parameterfitting` | Levenberg-Marquardt fitting function |

---

## Wax Formation Theory

### Solid-Liquid Equilibrium

The solid-liquid equilibrium for each wax-forming component $i$ is:

$$
\ln\left(\frac{x_i^S \gamma_i^S}{x_i^L \gamma_i^L}\right) = \frac{\Delta H_{f,i}}{R T}\left(1 - \frac{T}{T_{f,i}}\right) - \frac{\Delta C_{p,i}^{SL}}{R}\left(\frac{T_{f,i}}{T} - 1 - \ln\frac{T_{f,i}}{T}\right) + \frac{\Delta V_i^{SL}(P - P_{ref})}{R T}
$$

where:

| Symbol | Description |
|--------|-------------|
| $x_i^S, x_i^L$ | Mole fractions in solid and liquid |
| $\gamma_i^S, \gamma_i^L$ | Activity coefficients in solid and liquid |
| $\Delta H_{f,i}$ | Enthalpy of fusion at $T_{f,i}$ |
| $T_{f,i}$ | Fusion (triple point) temperature |
| $\Delta C_{p,i}^{SL}$ | Heat capacity change upon fusion |
| $\Delta V_i^{SL}$ | Volume change on fusion (Poynting correction) |

The four models differ in how they calculate the activity coefficients
$\gamma_i^S$ and $\gamma_i^L$, and how they correlate $\Delta H_f$,
$T_f$, and $\Delta C_p$ with molecular weight and carbon number.

### Key Temperatures

| Temperature | Description |
|-------------|-------------|
| WAT (Wax Appearance Temperature) | Highest temperature where the first wax crystals appear |
| Pour Point | Temperature below which the oil ceases to flow |
| Cloud Point | Experimental equivalent of WAT (cross-polarized microscopy or DSC) |

### Pedersen Correlations for Wax Properties

The following correlations (Pedersen et al., 1991) relate wax thermodynamic
properties to molecular weight (MW in g/mol):

$$
T_f = 374.5 + 0.02617 \cdot MW - \frac{20172}{MW} \quad [\text{K}]
$$

$$
\Delta H_f = \frac{0.1426}{0.238845} \cdot MW \cdot T_f \quad [\text{J/mol}]
$$

$$
\Delta C_p^{SL} = (0.3033 \cdot MW - 4.635 \times 10^{-4} \cdot MW \cdot T) \times 4.184 \quad [\text{J/(mol·K)}]
$$

---

## Available Wax Models

### 1. Pedersen Model (Default)

**Class:** `ComponentWax` | **Key:** `"Pedersen"`

The simplest model, based on Pedersen et al. (1991). Uses Clausius-Clapeyron
with a heat capacity correction. Assumes ideal solid solution ($\gamma_i^S = 1$).

**Strengths:** Fast, robust, easy to tune, good for screening.

**Limitations:** No solid-phase non-ideality; may overpredict wax amounts at low temperatures.

**Tunable parameters:** A, B, C in the wax characterization (heat of fusion and
triple point temperature correlations), plus heat of fusion and triple point
multipliers.

### 2. Won Model

**Class:** `ComponentWonWax` | **Key:** `"Won"`

Based on Won (1986, 1989). Uses a solubility parameter approach for the
solid-phase activity coefficient, with molar volume and solubility parameter
estimated from carbon number.

$$
\ln \gamma_i^S = \frac{V_i}{RT}\left(\delta_i - \bar{\delta}\right)^2
$$

where $V_i$ is the molar volume and $\delta_i$ is the solubility parameter
of component $i$.

**Strengths:** Accounts for solid-phase non-ideality; better for multi-component waxes.

**Limitations:** Solubility parameters are empirical; less predictive for very heavy components.

### 3. Wilson Model

**Class:** `ComponentWaxWilson` | **Key:** `"Wilson"`

Uses the Wilson local-composition equation for solid-phase activity coefficients,
with binary interaction energies estimated from characteristic energy parameters.
Includes full $\Delta C_p$ and sublimation enthalpy corrections.

$$
\ln \gamma_i^S = 1 - \ln\left(\sum_j x_j \Lambda_{ji}\right) - \sum_k \frac{x_k \Lambda_{ik}}{\sum_j x_j \Lambda_{jk}}
$$

**Strengths:** Better mixing rule than Won; accounts for local composition effects.

**Limitations:** More computationally expensive; binary parameters from correlations.

### 4. Coutinho Predictive UNIQUAC Model

**Class:** `ComponentCoutinhoWax` | **Key:** `"Coutinho"`

A local-composition solid-solution model based on Coutinho (1998, 2001).
Uses the UNIQUAC local-composition framework for solid-phase activity coefficients,
with predictive interaction parameters derived from sublimation enthalpies.

$$
\ln \gamma_i^S = \ln \gamma_i^{comb} + \ln \gamma_i^{res}
$$

where the combinatorial part uses the Staverman-Guggenheim equation (size/shape
corrections via UNIQUAC $r$ and $q$ parameters from Bondi group contributions),
and the residual part uses interaction parameters $\lambda_{ij}$ estimated from:

$$
\lambda_{ii} = -\frac{2}{Z}(\Delta H_{sub,i} - RT)
$$

For self interactions, use $Z = 10$ (coordination number) and $\Delta H_{sub}$ as the sublimation enthalpy.
For unlike molecules, the pair energy is the self energy of the shorter chain:
$\lambda_{ij}=\lambda_{ji}=\lambda_{short,short}$.

**Strengths:** Accounts for molecular size and solid-solution non-ideality.
The implemented correlations require validation against data for the fluid of interest.

**Limitations:** More sensitive to characterization quality; requires correct carbon number
assignment for TBP fractions.

---

## Model Selection API

### Selecting a Wax Model (Recommended)

Use `setWaxModelType()` on the fluid system **before** calling `addSolidComplexPhase("wax")`.
Names are case-insensitive; unknown or null names raise `IllegalArgumentException`.
Changing an already populated wax phase to a different model raises
`IllegalStateException` and preserves the existing configuration. Selecting its current
model again is allowed. The same rules apply to `PhaseWax.setWaxComponentModel()`.
To compare models, construct one fresh fluid for each selection:


```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(323.15, 50.0);
// ... add components ...
fluid.setMixingRule(2);

// Select wax model BEFORE adding solid phase
fluid.setWaxModelType("Coutinho");  // "Pedersen", "Won", "Wilson", or "Coutinho"
fluid.addSolidComplexPhase("wax");
fluid.setMultiphaseWaxCheck(true);
fluid.setMultiPhaseCheck(true);
```

### Numerical behavior and limits

The alternative models use Morgan-Kobayashi (1994) vaporization enthalpies with
the PERT2 coefficients. These correlations are evaluated only for wax-forming
components and require `0 < T < Tc`; methane and other excluded fluid components
do not enter solid activity sums. Solid compositions are normalized over wax formers.
Invalid correlation states or nonfinite/zero fugacity coefficients raise an exception
instead of being passed into the phase-fraction calculation. Such an exception is
not a prediction of zero wax.

All four models use the same liquid-reference, fusion, heat-capacity and pressure
corrections. The pressure contribution is `(Vs - Vl) * (P - 1 bara) / (R*T)`,
with volumes in m³/mol and the pressure difference converted to Pa; the current
solid-volume approximation is `Vs = 0.9*Vl`. Won's solid cohesive energy includes
both vaporization and fusion enthalpy. UNIQUAC uses relative interaction energies
so that its pure-component activity coefficient is one, and its infinite-dilution
coefficient is evaluated without dividing by a zero mole fraction.

The regression coverage for [issue #3914](https://github.com/equinor/neqsim/issues/3914)
checks actual component classes, finite coefficients, pure-solid limits, pressure
units and component inventories for the characterized methane/TBP/plus-fraction
mixture at 261 and 275 K and 5 bara. It also cross-checks a Morgan-Kobayashi
value against an independent implementation. These are numerical and thermodynamic
consistency checks, not experimental qualification of each model's WAT or wax amount.
Use measured WAT and wax-fraction data to qualify or tune predictions for a specific oil.

### Python (Jupyter)

```python
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(323.15, 50.0)
# ... add components ...
fluid.setMixingRule(2)

fluid.setWaxModelType("Coutinho")
fluid.addSolidComplexPhase("wax")
fluid.setMultiphaseWaxCheck(True)
fluid.setMultiPhaseCheck(True)
```

---

## Getting Started

### Executable wax-screening workflow

The following program uses the repository regression fluid to demonstrate a bounded numerical
WAT and unsmoothed wax-fraction screen. Replace the synthetic composition and heavy-end
characterization with representative fluid data before engineering use.
The synthetic TBP and plus fractions need temporary component registration during wax setup.
The example restores the previous database setting once the fluid is initialized.

<!-- wax-characterization-example: complete-screen -->
```java
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.pvtsimulation.flowassurance.WaxCurveCalculator;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

public final class WaxCharacterizationScreeningExample {
  private static final Logger logger =
      LogManager.getLogger(WaxCharacterizationScreeningExample.class);

  private WaxCharacterizationScreeningExample() {}

  private static SystemInterface createFluid() {
    boolean previousTemporaryTables = NeqSimDataBase.createTemporaryTables();
    NeqSimDataBase.setCreateTemporaryTables(true);
    try {
      SystemInterface fluid = new SystemSrkEos(298.0, 10.0);
      fluid.addComponent("methane", 6.78);
      fluid.addTBPfraction("C19", 10.13, 0.170, 0.7814);
      fluid.addPlusFraction("C20", 10.62, 0.381, 0.850871882888);
      fluid.setMixingRule(2);
      fluid.enableWaxModel("Pedersen");
      return fluid;
    } finally {
      NeqSimDataBase.setCreateTemporaryTables(previousTemporaryTables);
    }
  }

  public static void main(String[] args) {
    double pressureBara = 5.0;
    WaxCurveCalculator calculator = new WaxCurveCalculator(createFluid());
    calculator.setPressure(pressureBara);
    calculator.setEnforceMonotonicity(false);

    double refinedWatC = calculator.calculateWAT();
    if (!Double.isFinite(refinedWatC)) {
      throw new IllegalStateException("Numerical WAT was not resolved");
    }

    calculator.setTemperatureRange(refinedWatC - 5.0, refinedWatC + 5.0, 1.0);
    calculator.calculate();
    if (calculator.getFailCount() != 0) {
      throw new IllegalStateException("Wax curve contains failed flash points");
    }

    double[] rawFractions = calculator.getRawWaxFractions();
    String[] failures = calculator.getFailureMessages();
    assert rawFractions.length == failures.length;
    for (int point = 0; point < rawFractions.length; point++) {
      assert failures[point] == null;
      assert Double.isFinite(rawFractions[point]);
      assert rawFractions[point] >= 0.0 && rawFractions[point] < 1.0;
    }

    double gridWatC = calculator.getWaxAppearanceTemperatureC();
    assert Double.isFinite(gridWatC);
    assert Math.abs(gridWatC - refinedWatC) <= 1.1;

    double operatingTemperatureC = refinedWatC - 10.0;
    Map<Double, Double> operatingFractions = calculator.calculateAtMultiplePressures(
        new double[] {pressureBara}, operatingTemperatureC);
    double waxMassFraction = operatingFractions.get(pressureBara);
    double subcoolingC = refinedWatC - operatingTemperatureC;
    assert Double.isFinite(waxMassFraction);
    assert waxMassFraction > 0.0 && waxMassFraction < 1.0;
    assert subcoolingC > 0.0;

    logger.info(
        "Numerical WAT={} C, wax mass fraction={} at {} C and {} bara, subcooling={} C",
        refinedWatC,
        waxMassFraction,
        operatingTemperatureC,
        pressureBara,
        subcoolingC);
  }
}
```

The example deliberately preserves raw results. A failed flash, nonfinite value, or unbracketed
WAT is a failed screen rather than a wax-free result. WAT, equilibrium wax mass fraction, and
subcooling are indicators; they do not by themselves assign a deposition or plugging risk class.

### Numerical WAT contract

`calcWAT()` now locates the numerical wax onset with independent, freshly initialized
TP flashes at the current pressure. It brackets the transition through a wax mass
fraction of **1e-8 of the whole fluid**, then refines the temperature interval to
**1e-4 K** and repeats both endpoint flashes. The returned temperature is the warm
endpoint (wax mass fraction at or below the threshold). The fluid retains that
verified TP phase state; the operation does not append an artificial wax phase.
Downstream wax amounts should be obtained with a new `TPflash()` at the operating
temperature.

The initial temperature must lie within the numerical search bounds of **100-1000 K**.
The operation searches in 10 K increments from that guess and assumes a single
wax appearance transition in the bracket. These bounds are solver guards, not a
statement of physical model validity. Wax checks are enabled on the internal
trials; the caller's wax-check and fluid multiphase-check settings are preserved.
Enable `setMultiPhaseCheck(true)` before calculation when an aqueous phase must be
considered. A nonzero wax-forming component and `addSolidComplexPhase("wax")`
are prerequisites. For TBP/plus fractions, first perform wax characterization as
above. Named components already marked as wax formers in the component database
(for example `nc14`, `nc20` and `nc24`) can be used directly without `addTBPWax()`.

#### Named n-paraffins and `addTBPWax()`

Calling `getWaxModel().addTBPWax()` on a fluid whose wax formers are named n-paraffins is
supported, before or after `addSolidComplexPhase("wax")`. Named wax formers are not split
(they are n-paraffins as a whole). They keep the melting temperature and heat of fusion from
the component database where it has measured values (n-hexane, n-heptane, nC11 to nC14,
nC16). For nC15 and nC17 to nC24 the database holds only the placeholder default of
273.0 K and 6000 J/mol; for those, `addTBPWax()` of the default Pedersen model assigns its
correlations

$$T_f = f_T\left(374.5 + 0.02617\,M - \frac{20172}{M}\right), \qquad
\Delta H_f = f_H\,\frac{0.1426}{0.238845}\,M\,T_f$$

with $M$ in g/mol, $T_f$ in K and $\Delta H_f$ in J/mol, where $f_T$ and $f_H$ are the tuning
multipliers (parameters 4 and 3 below). For nC20 this gives $T_f \approx 310.5$ K. Calling
`addTBPWax()` again re-applies the correlations with the current multipliers. Without
`addTBPWax()` the placeholder values are used unchanged, which is the state the #4309 and
#4324 regressions below describe.

<!-- wax-characterization-example: named-paraffin -->
```java
SystemInterface fluid = new SystemSrkEos(300.0, 5.0);
fluid.addComponent("methane", 0.3);
fluid.addComponent("nC20", 0.7);
fluid.setMixingRule(2);
fluid.addSolidComplexPhase("wax");
fluid.setMultiphaseWaxCheck(true);
fluid.setMultiPhaseCheck(true);
fluid.getWaxModel().addTBPWax();

WaxFractionSim wax = new WaxFractionSim(fluid);
wax.setTemperaturesAndPressures(new double[] {280.0, 270.0, 260.0}, new double[] {5.0, 5.0, 5.0});
wax.runCalc();
double[] waxMassFraction = wax.getWaxFraction(); // about 0.976 at each point
```

Below the nC20 melting point practically all nC20 is wax and the methane is gas, so the wax
mass fraction is $0.7 M_{\mathrm{nC20}} / (0.7 M_{\mathrm{nC20}} + 0.3 M_{\mathrm{CH_4}}) \approx 0.976$.
Before [issue #5210](https://github.com/equinor/neqsim/issues/5210) this feed either threw
`Invalid reference-liquid fugacity coefficient for nC20` or returned a wax fraction above one.
Two defects were involved: the named nC20 kept the 273 K placeholder, and the wax phase-fraction
solver (`TPmultiflashWAX`) declared convergence on the absolute Newton step and on the last phase
only. When the liquid freezes out completely, the gas phase re-enters from a trace fraction and
grows only geometrically, so the solver stopped with a gas fraction near 4e-7 instead of 0.3 and
the phase mass fractions summed to 1.39. The solver now requires every phase fraction to settle
to a relative change below 1e-6. Regression evidence: `WaxFractionSimTest`
(`testNamedWaxFormerFeed`, `testNamedWaxFormerIsIndependentOfSetupOrder`,
`testNamedWaxFormerFollowsTuningAndKeepsMeasuredData`).

For a present wax phase, `getPhaseFraction("wax", "mass")` returns the wax mass
divided by the **total feed mass**, using phase mole fraction and molar masses:

$$w_{\mathrm{wax}}=\beta_{\mathrm{wax}}\frac{M_{\mathrm{wax}}}{M_{\mathrm{feed}}}$$

Here $\beta_{\mathrm{wax}}$ is the wax mole fraction and both molar masses have
the same units (kg/mol). This is the same inventory-based definition used by
`getWtFraction(...)`; it does not require a solid-density correlation or physical
property initialization. Gas, oil and aqueous mass fractions use the same
definition and sum to one with the wax fraction for a balanced flash. The volume
fraction is a separate property and depends on the volume model. Check
`hasPhaseType("wax")` before requesting its fraction, and use
`getPhaseNumberOfPhase("wax")` for APIs that take a logical phase number;
`getPhaseIndex("wax")` identifies a physical storage slot.

Invalid inputs or missing wax formers/configuration raise `IllegalArgumentException`.
Failure to find or reproduce a bracket, or a nonfinite/unbalanced TP trial, raises
`IllegalStateException` with pressure, trial/bracket temperatures and the number
of TP evaluations. Failure leaves the caller's temperature, phase state and
overall composition unchanged. Do not treat the initial temperature as a WAT after
an exception.

This resolves the internal consistency failure in [issue #3709](https://github.com/equinor/neqsim/issues/3709):
with the former plus-fraction distribution, the characterized synthetic oil returned
54.4595 °C at 60 bara although fresh TP flashes already predicted wax at 64 °C.
After correcting the Pedersen plus-fraction mass moment, this oil's numerical
onset is about 63.3575 °C at 60 bara, with fresh TP flashes bracketing it between
63 and 64 °C. This composition change affects internal regression references;
the TP-based onset definition is unchanged. Regression tests compare direct
WAT against a refined independent TP appearance bracket at 40, 60 and 80 bara,
including different initial guesses, reordered/existing wax phases and added water.
The named-component regressions for [issue #4309](https://github.com/equinor/neqsim/issues/4309)
also check phase mass fractions against component inventories and bracket WAT
with independent TP flashes for methane/n-heptane/nc14/nc20 feeds at 1 and 5 bara
and a heavier nc24-containing feed at 5 bara.
These named-component WAT checks start at 320 K. A separate unresolved stability
limitation is tracked in [issue #4324](https://github.com/equinor/neqsim/issues/4324):
the heavier feed can lose both gas and wax at 265 K, even though it has both at
270 K. Starting WAT in that incorrectly wax-free state can select a spurious
colder transition. Correct mass fractions alone do not qualify that phase state
or its onset; inspect independent TP results across the relevant temperature
range before using a WAT as an engineering margin.
This checks consistency within the selected EOS, wax model and characterization;
it is not experimental validation. Laboratory WAT and wax-fraction data are still
needed for fluid-specific prediction, and equilibrium wax appearance does not
by itself predict deposition rate or location.

The named-component cold-start regression in
[issue #4324](https://github.com/equinor/neqsim/issues/4324) uses SRK/classic with
methane / n-heptane / nc14 / nc20 / nc24 = 40 / 10 / 10 / 20 / 20 mol, the default
Pedersen wax model and fluid multiphase checking. Fresh, warm and cold starts,
with and without explicit initialization at the trial temperature, retain gas,
oil and wax at 260, 265 and 270 K at both 1 and 5 bara; 275 K is wax-free.
At 265 K and 5 bara the wax mass fraction is **0.331530523**. Initial guesses of
265, 270 and 320 K give the same numerical WAT: **270.421295 K at 5 bara** and
**271.307144 K at 1 bara**, within the search tolerance.

The repair restricts fluid-only endpoint cleanup to fluid calculations. The
homogeneous fluid Gibbs-energy reference must not overwrite a wax equilibrium:
the solid phase's caloric properties do not implement the wax fugacity reference
used by the equilibrium model. Equal fluid and solid compositions likewise do
not make the phases duplicates. WAT reads wax mass from phase mole inventory
and molar mass, independently of solid-density correlations.

`NamedWaxColdStartTest` checks normalized phases, component and total mass
conservation, equal component fugacities, and independent vapor, liquid and wax
tangent-plane trials seeded from the feed and each component. These are
model-consistency checks for this synthetic feed, not experimental qualification,
a validation of solid density/caloric properties, or a guarantee of global
stability for arbitrary compositions. Preserve unsmoothed results when diagnosing
a disappearing phase; material closure alone does not establish phase stability.

### Curve and operating-point interpretation

The complete program above calculates both a raw wax curve and an independent operating-point
wax fraction. Keep raw values and failure provenance in any report; optional monotonic smoothing
is presentation postprocessing and is not thermodynamic or experimental validation.

---

## Wax Curve Calculation

### Wax setup

Use `enableWaxModel(...)` as shown in the complete program when the fluid contains an unresolved
plus fraction. It characterizes the heavy end, refreshes component data, installs the wax phase,
enables the required checks, and initializes the fluid without changing the selected EOS or
mixing rule. Lower-level setup remains appropriate only when a research workflow needs explicit
control over each characterization step.

### WaxCurveCalculator

`WaxCurveCalculator` scans from high to low temperature using independent TP
flashes of the configured feed. Configure the wax phase and enable
`setMultiphaseWaxCheck(true)` before calculating. A disabled or missing wax
phase is a setup failure, not evidence of zero wax.

Failed flashes, nonfinite phase states, unnormalized phase compositions and
component-balance errors above an absolute feed mole-fraction tolerance of
$10^{-6}$ produce `NaN`, never zero or the preceding result. The per-point
`getFailureMessages()` array records temperature, pressure and the failure
reason; successful entries are null. Inspect it together with `getFailCount()`.
The pressure-sweep API uses the same checks and returns `NaN` for failed trials.
These checks establish finite and conserved results, not global phase stability
or experimental accuracy.

For compatibility, running-maximum smoothing remains enabled by default. It is
only postprocessing: it can conceal a physical or numerical trend and must not
be used as validation. Use `setEnforceMonotonicity(false)` for research, model
comparison and condensate studies, and retain `getRawWaxFractions()`. Missing
points stay missing, and smoothing restarts after each gap. Correction counts
reset on every calculation.

`getWaxAppearanceTemperatureC()` is a coarse interpolation of adjacent raw
samples across a total-feed wax mass fraction of $10^{-8}$. It returns `NaN`
when wax is already present at the warm boundary, when no onset is found, or
when a failed warmer point prevents a reliable bracket. This intentionally
replaces the previous misleading boundary-temperature result. Use
`calculateWAT()` for the native refined TP onset search described above; the
curve estimate is grid-dependent and is not an experimental WAT. Pressure must
be finite and positive in bara; grid temperatures must be finite and above
absolute zero, the upper bound must exceed the lower, and the step must be
positive. The minimum effective step remains 0.1 C and the grid is limited to
10001 points.

The complete program above uses this API with monotonicity disabled, retains the raw curve,
checks every failure entry, and evaluates an explicit operating point. Use the same sequence for
model comparison and condensate studies.




### Waxy-condensate research case: Hong et al. (2026)

[Issue #4116](https://github.com/equinor/neqsim/issues/4116) tracks qualification
against Hong, Wang, Meng and Wang, *AIP Advances* 16, 075039,
[DOI: 10.1063/5.0326546](https://doi.org/10.1063/5.0326546).
The paper combines gas/liquid/solid equilibrium, heavy-end characterization and
modified PR models. Its reported 12.4% wax-temperature and 20.2% best dew-point
pressure deviations are comparisons with reference/theoretical or simulated
results; they must not be relabeled as NeqSim experimental accuracy.

A reproducible comparison must preserve Table I's **mass-percent basis** and
convert to mole fractions using the selected component molar masses. Carbon
number cuts must not silently be replaced by pure normal paraffins: cut molar
masses, densities and normal-paraffin content affect the wax-forming inventory.
Record any such surrogate assumption explicitly. Obtain numerical reference
curves and their provenance before reporting a quantitative benchmark.

The current four-model regression fluid is a synthetic consistency test, not a
reconstruction of this field fluid. Required follow-up includes a sourced case
fixture, pressure/temperature phase and wax maps without smoothing, numerical
WAT brackets, characterization sensitivity, independent experimental WAT and
wax-fraction data, and a cleanly executed Colab example. Equilibrium wax amount
does not establish a deposition rate, location, or plugging time.

### WaxFractionSim

The `WaxFractionSim` class is used for PVT-style wax fraction simulation
and also supports parameter tuning via the Levenberg-Marquardt optimizer.

```java
import neqsim.pvtsimulation.simulation.WaxFractionSim;

WaxFractionSim sim = new WaxFractionSim(fluid);
double[] temps = {293.15, 283.15, 273.15, 264.15, 263, 262, 261};
double[] pres  = {5, 5, 5, 5, 5, 5, 5};
sim.setTemperaturesAndPressures(temps, pres);
sim.runCalc();
double[] waxFrac = sim.getWaxFraction();
```

---

## Fitting to Experimental Data

NeqSim includes a Levenberg-Marquardt optimizer for tuning wax model
parameters to match experimental wax fraction data.

### Tunable Parameters

| Index | Parameter | Description | Typical Range |
|-------|-----------|-------------|---------------|
| 0 | A | Wax characterization parameter A | 0.8 - 1.3 |
| 1 | B | Wax characterization parameter B | -0.01 to 0.01 |
| 2 | C | Wax characterization parameter C | 0 to 0.001 |
| 3 | HeatOfFusion multiplier | Scales $\Delta H_f$ | 0.8 - 1.2 |
| 4 | TriplePointTemp multiplier | Scales $T_f$ | 0.98 - 1.02 |

### Java Fitting Example

```java
WaxFractionSim sim = new WaxFractionSim(fluid);
double[] temps = {293.15, 283.15, 273.15, 264.15, 263, 262, 261};
double[] pres  = {5, 5, 5, 5, 5, 5, 5};
sim.setTemperaturesAndPressures(temps, pres);

// Set experimental wax wt% data
double[][] expData = {{4, 7, 9, 10, 11, 12, 13}};
sim.setExperimentalData(expData);

// Configure optimizer (3 params = A, B, C; up to 5 with HoF and Tf)
sim.getOptimizer().setNumberOfTuningParameters(3);
sim.getOptimizer().setMaxNumberOfIterations(20);

// Run tuning
sim.runTuning();

// Recalculate with tuned parameters
sim.runCalc();
double[] fitted = sim.getWaxFraction();
```

### Python (Jupyter) Fitting Example

```python
from neqsim import jneqsim

WaxFractionSim = jneqsim.pvtsimulation.simulation.WaxFractionSim

sim = WaxFractionSim(fluid)
temps = [293.15, 283.15, 273.15, 264.15]
pres  = [5.0, 5.0, 5.0, 5.0]
sim.setTemperaturesAndPressures(temps, pres)

# Experimental wax wt% (must be 2D array)
exp_data = [[4.0, 7.0, 9.0, 10.0]]
sim.setExperimentalData(exp_data)

sim.getOptimizer().setNumberOfTuningParameters(3)
sim.getOptimizer().setMaxNumberOfIterations(20)
sim.runTuning()
sim.runCalc()

fitted = list(sim.getWaxFraction())
```

---

## Flow Assurance Applications

### Pipeline wax-screening interpretation

Compare the operating temperature with the numerical WAT and report the subcooling in °C, as the
complete program demonstrates. Do not map subcooling alone to HIGH, MEDIUM, or LOW risk. A
deposition or restart assessment also needs representative characterization, laboratory WAT and
wax-fraction data, thermal/hydraulic history, wall conditions, shear, deposition and aging
models, gel strength, shutdown duration, restart hydraulics, pigging strategy, and chemical
performance evidence.

### Multi-Pressure Pipeline Profile

Use `calculateAtMultiplePressures(...)` only after validating every pressure and inspecting each
returned value. The complete program shows the one-pressure form; extend the array for a pressure
profile and treat any `NaN` as a failed point, not zero wax.

---

## Model Comparison and Selection Guide

| Criterion | Pedersen | Won | Wilson | Coutinho |
|-----------|----------|-----|--------|----------|
| Speed | Fast | Fast | Medium | Medium |
| Parameters to tune | 3-5 | 3-5 | 3-5 | 3-5 |
| Solid non-ideality | None | Solubility param | Wilson GE | UNIQUAC GE |
| $\Delta C_p$ correction | Yes | Yes | Yes | Yes |
| Best for | Screening, quick studies | Multi-component waxes | Moderate accuracy | High accuracy, validation |
| Literature validation | Pedersen 1991 | Won 1986, 1989 | - | Coutinho 1998, 2001 |

### Recommendations

1. **Screening and quick studies:** Use Pedersen (default). Fast, robust, easy to tune.
2. **Engineering design (single oil):** Use Pedersen or Won with parameter tuning to experimental data.
3. **Predictive work:** Compare model sensitivity and obtain experimental validation before relying on absolute wax amounts.
4. **Multi-crude blending or new field:** Validate the characterization and solid-solution model for the new compositions.

---

## References

- Pedersen, K.S., Skovborg, P., and Ronningsen, H.P., "Wax Precipitation from North Sea Crude Oils. 4. Thermodynamic Modeling," *Energy & Fuels*, 5, 924-932, 1991.
- Won, K.W., "Thermodynamics for Solid Solution-Liquid-Vapor Equilibria: Wax Phase Formation from Heavy Hydrocarbon Mixtures," *Fluid Phase Equilibria*, 30, 265-279, 1986.
- Won, K.W., "Thermodynamic Calculation of Cloud Point Temperatures and Wax Phase Compositions of Refined Hydrocarbon Mixtures," *Fluid Phase Equilibria*, 53, 377-396, 1989.
- Coutinho, J.A.P., "Predictive UNIQUAC: A New Model for the Description of Multiphase Solid-Liquid Equilibria in Complex Hydrocarbon Mixtures," *Ind. Eng. Chem. Res.*, 37, 4870-4875, 1998.
- Coutinho, J.A.P. and Daridon, J.-L., "Low-Pressure Modeling of Wax Formation in Crude Oils," *Energy & Fuels*, 15, 1454-1460, 2001.
- Coutinho, J.A.P. et al., *Fluid Phase Equilibria* 233 (2005), 28-33, Eq. 13: shortest-chain pair-interaction energy. [DOI](https://doi.org/10.1016/j.fluid.2005.04.007).
- Morgan, D.L. and Kobayashi, R., "Extension of Pitzer CSP Models for Vapor Pressures and Heats of Vaporization to Long-Chain Hydrocarbons," *Fluid Phase Equilibria*, 94, 51-87, 1994. [DOI](https://doi.org/10.1016/0378-3812(94)87051-9).
- Wang, W. et al., "Thermodynamics Prediction of Wax Precipitation in Black Oil Using Regular Solution Model and Plus Fraction Characterization," *Advances in Mechanical Engineering*, 2013, Eq. 11. [DOI](https://doi.org/10.1155/2013/829591).
- [Chemicals MK implementation](https://chemicals.readthedocs.io/_modules/chemicals/phase_change.html#MK): independent coefficient and numerical cross-check; the full original coefficient table was not used directly.
- Huang, Q., Huang, J., Zhao, Y., and Zhang, J., "Wax Deposition: Experimental Characterizations, Theoretical Modeling, and Field Practices," CRC Press, 2016.

---

## See Also

- [Flow Assurance](../../pvtsimulation/flow_assurance_overview) - Complete flow assurance guide
- [PVT Characterization](../pvt_fluid_characterization) - Fluid characterization
- [TBP Fractions](../../wiki/tbp_fraction_models) - Plus fraction handling
