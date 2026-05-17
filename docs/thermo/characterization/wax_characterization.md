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
\ln\left(\frac{x_i^S \gamma_i^S}{x_i^L \gamma_i^L}\right) = -\frac{\Delta H_{f,i}}{R T}\left(1 - \frac{T}{T_{f,i}}\right) + \frac{\Delta C_{p,i}^{SL}}{R}\left(\frac{T_{f,i}}{T} - 1 - \ln\frac{T_{f,i}}{T}\right) - \frac{\Delta V_i^{SL}(P - P_{ref})}{R T}
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

The most thermodynamically rigorous model, based on Coutinho (1998, 2001).
Uses the UNIQUAC local-composition framework for solid-phase activity coefficients,
with predictive interaction parameters derived from sublimation enthalpies.

$$
\ln \gamma_i^S = \ln \gamma_i^{comb} + \ln \gamma_i^{res}
$$

where the combinatorial part uses the Staverman-Guggenheim equation (size/shape
corrections via UNIQUAC $r$ and $q$ parameters from Bondi group contributions),
and the residual part uses interaction parameters $\lambda_{ij}$ estimated from:

$$
\lambda_{ij} = -\frac{2}{Z}\sqrt{(\Delta H_{sub,i} - RT)(\Delta H_{sub,j} - RT)}
$$

with $Z = 10$ (coordination number) and $\Delta H_{sub}$ the sublimation enthalpy.

**Strengths:** Most predictive; validated against pure n-alkane mixtures and crude oils;
accounts for solid solution non-ideality through first-principles approach.

**Limitations:** More sensitive to characterization quality; requires correct carbon number
assignment for TBP fractions.

---

## Model Selection API

### Selecting a Wax Model (Recommended)

Use `setWaxModelType()` on the fluid system **before** calling `addSolidComplexPhase("wax")`:

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

### Step 1: Create a Waxy Fluid

```java
SystemSrkEos fluid = new SystemSrkEos(323.15, 50.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-butane", 0.05);
fluid.addComponent("n-pentane", 0.04);
fluid.addComponent("n-hexane", 0.03);
fluid.addTBPfraction("C7", 0.10, 95.0 / 1000, 0.72);
fluid.addTBPfraction("C10", 0.08, 135.0 / 1000, 0.78);
fluid.addTBPfraction("C15", 0.06, 210.0 / 1000, 0.82);
fluid.addTBPfraction("C20", 0.04, 280.0 / 1000, 0.85);
fluid.addTBPfraction("C30", 0.02, 420.0 / 1000, 0.88);
```

### Step 2: Characterize Wax

```java
fluid.setMixingRule(2);

// Characterize wax-forming fractions
fluid.getWaxModel().addTBPWax();

// Select model (optional, defaults to Pedersen)
fluid.setWaxModelType("Pedersen");

// Enable wax phase
fluid.addSolidComplexPhase("wax");
fluid.setMultiphaseWaxCheck(true);
fluid.setMultiPhaseCheck(true);
fluid.init(0);
fluid.init(1);
```

### Step 3: Calculate WAT

```java
import neqsim.thermodynamicoperations.ThermodynamicOperations;

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcWAT();
double watC = fluid.getTemperature() - 273.15;
```

### Step 4: Wax Fraction vs Temperature

```java
// Using WaxCurveCalculator (recommended)
import neqsim.pvtsimulation.flowassurance.WaxCurveCalculator;

WaxCurveCalculator calc = new WaxCurveCalculator(fluid);
calc.setPressure(50.0);
calc.setTemperatureRange(-10.0, 80.0, 2.0);
calc.calculate();

double wat = calc.getWaxAppearanceTemperatureC();
double[] temps = calc.getTemperaturesC();
double[] fractions = calc.getWaxWeightFractions();
```

---

## Wax Curve Calculation

### WaxCurveCalculator

The `WaxCurveCalculator` class provides the most robust way to generate wax
precipitation curves. It scans from high to low temperature, performs a
TP flash at each point, and applies monotonicity enforcement to remove
non-physical artifacts.

```java
WaxCurveCalculator calc = new WaxCurveCalculator(fluid);
calc.setPressure(100.0);
calc.setTemperatureRange(-10.0, 80.0, 1.0);
calc.setEnforceMonotonicity(true); // default
calc.calculate();

// Results
double watC = calc.getWaxAppearanceTemperatureC();
double[] temperaturesCurve = calc.getTemperaturesC();
double[] waxWtFractions = calc.getWaxWeightFractions();

// Multi-pressure evaluation
double[] pressures = {50.0, 100.0, 200.0};
Map<Double, Double> results = calc.calculateAtMultiplePressures(pressures, 10.0);
```

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

### Pipeline Wax Deposition Risk

```java
// Check if operating below WAT
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcWAT();
double wat = fluid.getTemperature() - 273.15;
double outletTemp = 15.0; // seabed temperature

if (outletTemp < wat) {
    double subcooling = wat - outletTemp;
    String risk = subcooling > 20 ? "HIGH" :
                  subcooling > 10 ? "MEDIUM" : "LOW";
    System.out.println("WAT: " + wat + " C, Subcooling: " + subcooling + " C, Risk: " + risk);
}
```

### Multi-Pressure Pipeline Profile

```java
WaxCurveCalculator calc = new WaxCurveCalculator(fluid);
double[] pressures = {50, 80, 100, 150, 200};
Map<Double, Double> waxAtPressures = calc.calculateAtMultiplePressures(pressures, 10.0);
```

---

## Model Comparison and Selection Guide

| Criterion | Pedersen | Won | Wilson | Coutinho |
|-----------|----------|-----|--------|----------|
| Speed | Fast | Fast | Medium | Medium |
| Parameters to tune | 3-5 | 3-5 | 3-5 | 3-5 |
| Solid non-ideality | None | Solubility param | Wilson GE | UNIQUAC GE |
| $\Delta C_p$ correction | Yes | No | Yes | Yes |
| Best for | Screening, quick studies | Multi-component waxes | Moderate accuracy | High accuracy, validation |
| Literature validation | Pedersen 1991 | Won 1986, 1989 | - | Coutinho 1998, 2001 |

### Recommendations

1. **Screening and quick studies:** Use Pedersen (default). Fast, robust, easy to tune.
2. **Engineering design (single oil):** Use Pedersen or Won with parameter tuning to experimental data.
3. **Predictive work (no experimental data):** Use Coutinho — most thermodynamically rigorous.
4. **Multi-crude blending or new field:** Use Coutinho — best extrapolation outside fitted range.

---

## References

- Pedersen, K.S., Skovborg, P., and Ronningsen, H.P., "Wax Precipitation from North Sea Crude Oils. 4. Thermodynamic Modeling," *Energy & Fuels*, 5, 924-932, 1991.
- Won, K.W., "Thermodynamics for Solid Solution-Liquid-Vapor Equilibria: Wax Phase Formation from Heavy Hydrocarbon Mixtures," *Fluid Phase Equilibria*, 30, 265-279, 1986.
- Won, K.W., "Thermodynamic Calculation of Cloud Point Temperatures and Wax Phase Compositions of Refined Hydrocarbon Mixtures," *Fluid Phase Equilibria*, 53, 377-396, 1989.
- Coutinho, J.A.P., "Predictive UNIQUAC: A New Model for the Description of Multiphase Solid-Liquid Equilibria in Complex Hydrocarbon Mixtures," *Ind. Eng. Chem. Res.*, 37, 4870-4875, 1998.
- Coutinho, J.A.P. and Daridon, J.-L., "Low-Pressure Modeling of Wax Formation in Crude Oils," *Energy & Fuels*, 15, 1454-1460, 2001.
- Huang, Q., Huang, J., Zhao, Y., and Zhang, J., "Wax Deposition: Experimental Characterizations, Theoretical Modeling, and Field Practices," CRC Press, 2016.

---

## See Also

- [Flow Assurance](../../pvtsimulation/flow_assurance_overview) - Complete flow assurance guide
- [PVT Characterization](../pvt_fluid_characterization) - Fluid characterization
- [TBP Fractions](../characterization/tbp_fractions) - Plus fraction handling
