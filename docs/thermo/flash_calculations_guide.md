---
title: "Flash Calculations Guide"
description: "This guide provides comprehensive documentation of flash calculations available in NeqSim via the `ThermodynamicOperations` class. Flash calculations determine the equilibrium state of a thermodynamic..."
keywords: "flash, TPflash, PHflash, PSflash, TVflash, dew point, bubble point, phase envelope, cricondenbar, cricondentherm, VLE, phase equilibrium, ThermodynamicOperations"
---

# Flash Calculations Guide

This guide provides comprehensive documentation of flash calculations available in NeqSim via the `ThermodynamicOperations` class. Flash calculations determine the equilibrium state of a thermodynamic system by solving phase equilibrium equations under specified constraints.

## Table of Contents
- [Overview](#overview)
- [Basic Usage](#basic-usage)
- [Flash Types](#flash-types)
  - [TP Flash (Temperature-Pressure)](#tp-flash-temperature-pressure)
    - [Algorithm Overview](#algorithm-overview)
    - [Rachford-Rice Equation](#rachford-rice-equation)
    - [Successive Substitution](#successive-substitution)
    - [GDEM-2 Acceleration](#gdem-2-acceleration)
    - [Newton-Raphson Solver](#newton-raphson-solver)
    - [Stability Analysis](#stability-analysis)
    - [Phase Detection Configuration](#phase-detection-configuration-systeminterface-methods)
    - [Convergence Parameters](#convergence-parameters)
  - [PH Flash (Pressure-Enthalpy)](#ph-flash-pressure-enthalpy)
  - [PS Flash (Pressure-Entropy)](#ps-flash-pressure-entropy)
  - [PU Flash (Pressure-Internal Energy)](#pu-flash-pressure-internal-energy)
  - [TV Flash (Temperature-Volume)](#tv-flash-temperature-volume)
  - [TS Flash (Temperature-Entropy)](#ts-flash-temperature-entropy)
  - [TH Flash (Temperature-Enthalpy)](#th-flash-temperature-enthalpy)
  - [TU Flash (Temperature-Internal Energy)](#tu-flash-temperature-internal-energy)
  - [PV Flash (Pressure-Volume)](#pv-flash-pressure-volume)
  - [VH Flash (Volume-Enthalpy)](#vh-flash-volume-enthalpy)
  - [VU Flash (Volume-Internal Energy)](#vu-flash-volume-internal-energy)
  - [VS Flash (Volume-Entropy)](#vs-flash-volume-entropy)
- [Q-Function Flash Methodology](#q-function-flash-methodology)
- [Saturation Calculations](#saturation-calculations)
  - [Bubble Point](#bubble-point)
  - [Dew Point](#dew-point)
  - [Water Dew Point](#water-dew-point)
  - [Hydrocarbon Dew Point](#hydrocarbon-dew-point)
- [Special Flash Types](#special-flash-types)
  - [Solid Phase Flash](#solid-phase-flash)
  - [Critical Point Flash](#critical-point-flash)
  - [Gradient Flash](#gradient-flash)
  - [Phase Fraction Flash](#phase-fraction-flash)
- [Reference EoS Flash Methods](#reference-eos-flash-methods)
  - [GERG-2008 Flashes](#gerg-2008-flashes)
  - [Leachman (Hydrogen) Flashes](#leachman-hydrogen-flashes)
  - [Vega (CO₂) Flashes](#vega-co2-flashes)
- [Hydrate Calculations](#hydrate-calculations)
- [Unit Handling](#unit-handling)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [API Reference Table](#api-reference-table)

---

## Overview

Flash calculations solve phase equilibrium problems by finding:
- The number and types of phases present
- The composition of each phase
- The phase fractions (vapor/liquid/solid split)
- Temperature or pressure (for non-TP flashes)

The mathematical basis is the equality of chemical potentials (or fugacities) for all components across all phases:

$$f_i^{vapor} = f_i^{liquid} = f_i^{solid}$$

where $f_i$ is the fugacity of component $i$.

---

## Basic Usage

All flash calculations use the `ThermodynamicOperations` class:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// 1. Create a fluid system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);  // T in K, P in bara
fluid.addComponent("methane", 0.8);
fluid.addComponent("ethane", 0.15);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic");

// 2. Create operations object
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// 3. Run the flash calculation
ops.TPflash();

// 4. Access results
System.out.println("Vapor fraction: " + fluid.getBeta());
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Temperature: " + fluid.getTemperature("C") + " °C");
System.out.println("Pressure: " + fluid.getPressure("bara") + " bara");
```

---

## Flash Types

### TP Flash (Temperature-Pressure)

The most common flash type. Given temperature and pressure, find phase split and compositions.

**Method signatures:**
```java
void TPflash()
void TPflash(boolean checkForSolids)
```

**Example:**
```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("n-heptane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();  // Required before reading physical properties

// Results
double vaporFraction = fluid.getBeta();  // Molar vapor fraction
double liquidDensity = fluid.getPhase("oil").getDensity("kg/m3");
```

**With solid phase checking:**
```java
fluid.setSolidPhaseCheck(true);
ops.TPflash(true);  // Includes solid equilibrium
```

#### Algorithm Overview

The TPflash implements the classical Michelsen (1982) algorithm with a hybrid successive-substitution / Newton-Raphson solver and tangent-plane-distance (TPD) stability analysis. The algorithm proceeds through five phases:

```
TPflash.run()
│
├── 1. Initialization
│   ├── findLowestGibbsEnergyPhase()
│   ├── Store reference chemical potentials: d_i = ln(z_i) + ln(φ_i)
│   └── Wilson K-values → Rachford-Rice → initial β
│
├── 2. Initial successive substitution (3 iterations)
│   ├── If Wilson K gives β at limits → reset β = 0.5
│   └── Check if 2-phase Gibbs energy < single-phase minimum
│
├── 3. Stability testing (if no clear 2-phase split found)
│   ├── Tangent plane distance (TPD) for both phases
│   ├── stabilityAnalysis() — SS + DEM + Newton on TPD
│   ├── amplifiedKStabilityRetry() — near-critical VLE fallback
│   ├── pureComponentStabilityTrials() — LLE fallback
│   │   (only when setCheckForLiquidLiquidSplit(true))
│   ├── If stable → single phase, return
│   └── If unstable → update K-values, continue
│
├── 4. Main convergence loop (hybrid solver)
│   ├── Iterations 1–11: SS + GDEM-2 acceleration every 5th step
│   ├── Iterations 12+: Newton-Raphson (SysNewtonRhapsonTPflash)
│   ├── Gibbs energy guard: resetK() if G increases
│   └── Chemical equilibrium outer loop (if reactive system)
│
└── 5. Post-processing
    ├── If doMultiPhaseCheck() → TPmultiflash.run()
    ├── Post-convergence stability verification if β at limits
    │   └── Includes pureComponentStabilityTrials() for LLE
    ├── Volume root selection (gas vs liquid Gibbs)
    ├── Remove trivial phases (β < phaseFractionMinimumLimit)
    └── orderByDensity() + final init
```

#### Rachford-Rice Equation

The vapor fraction $\beta$ is found by solving the Rachford-Rice equation:

$$\sum_i \frac{z_i (K_i - 1)}{1 + \beta(K_i - 1)} = 0$$

Two solver methods are available, switchable via `RachfordRice.setMethod()`:

| Method | Reference | Description |
|--------|-----------|-------------|
| `"Michelsen2001"` | Michelsen & Mollerup (2001) | Newton-Raphson with Whitson-Torp bounds |
| `"Nielsen2023"` (default) | Nielsen & Lia (2023) | Round-off robust reformulation |

Initial K-values come from the Wilson correlation:

$$K_i = \frac{P_{c,i}}{P} \exp\left[5.373(1+\omega_i)\left(1-\frac{T_{c,i}}{T}\right)\right]$$

#### Successive Substitution

Each successive substitution step updates K-values from fugacity coefficients:

$$K_i = \frac{\hat{\phi}_i^{\text{liquid}}}{\hat{\phi}_i^{\text{vapor}}}$$

then solves the Rachford-Rice equation for $\beta$, updates compositions via `calc_x_y()`, and re-initializes the thermodynamic model.

#### GDEM-2 Acceleration

After the initial iterations, the General Dominant Eigenvalue Method with 2 eigenvalues (Risnes & Dalen, 1984; Michelsen & Mollerup, 2007, §9.5) accelerates convergence. It solves the 2×2 system:

$$\begin{bmatrix} b_{11} & b_{12} \\ b_{12} & b_{22} \end{bmatrix} \begin{bmatrix} \mu_1 \\ \mu_2 \end{bmatrix} = \begin{bmatrix} c_1 \\ c_2 \end{bmatrix}$$

where $b_{ij} = \Delta g_{n-i} \cdot \Delta g_{n-j}$ and $c_i = \Delta g_n \cdot \Delta g_{n-i}$, then extrapolates:

$$\ln K_i^{\text{new}} = \ln K_i + \mu_1 \Delta g_n[i] + \mu_2 \Delta g_{n-1}[i]$$

Falls back to standard 1-eigenvalue DEM (Michelsen, 1982b) when $|\mu_1| > 5$ or $|\mu_2| > 5$.

#### Newton-Raphson Solver

After 12 successive substitution iterations (configurable via `newtonLimit`), the solver switches to a second-order Newton method (`SysNewtonRhapsonTPflash`) using Michelsen's $u$-variable formulation:

- **Variables**: $u_i = \beta \cdot y_i$
- **Residual**: $g_i = \ln(\hat{f}_i^{\text{gas}}) - \ln(\hat{f}_i^{\text{liq}})$
- **Jacobian**: Hessian of the reduced Gibbs energy $Q$ with Levenberg-Marquardt regularization ($\lambda = 10^{-8}$)
- **Line search**: Armijo backtracking on $Q$ with $c_1 = 10^{-4}$, max 8 backtrack steps
- **Linear algebra**: EJML pre-allocated dense LU solver

#### Stability Analysis

Michelsen's tangent plane distance (TPD) criterion determines if a single-phase solution is thermodynamically stable. A phase is unstable if:

$$\text{tm} = 1 - \sum_i W_i < \text{tmLimit}$$

where $W_i$ satisfies $\ln W_i + \ln \hat{\phi}_i^{\text{trial}} = \ln z_i + \ln \hat{\phi}_i^{\text{ref}}$ and tmLimit = $-10^{-8}$.

The analysis uses:
- **Successive substitution** with **DEM acceleration** (Risnes et al., 1981) on $\ln W$ variables
- **Two initial trials**: vapor-like ($W_i = z_i K_i$) and liquid-like ($W_i = z_i / K_i$)
- **Second-order Newton** (alpha-substitution $\alpha_i = 2\sqrt{W_i}$) in the last 10 iterations
- **Trivial solution detection**: cosine similarity > 0.9999 between trial and feed
- **Amplified K-value retry** for near-critical VLE (amplification factor 2.5–4.0×)
- **Pure-component LLE trials** (heaviest/lightest component) when `setCheckForLiquidLiquidSplit(true)` is enabled

#### Phase Detection Configuration (`SystemInterface` Methods)

NeqSim provides three `SystemInterface` methods that control how aggressively the TPflash searches for phase splits. These operate independently and are additive — each enables a different detection strategy:

| Method | Default | What it enables |
|--------|---------|-----------------|
| `setMultiPhaseCheck(true)` | `false` | Delegates to `TPmultiflash` for 3+ phases (gas + multiple liquids). Heavy computation. |
| `setCheckForLiquidLiquidSplit(true)` | `false` | Adds **pure-component stability trials** inside the two-phase flash to detect LLE. Lightweight (~0.1–0.5 ms overhead). |
| `setEnhancedMultiPhaseCheck(true)` | `false` | Activates enhanced stability analysis in `TPmultiflash` with acentric-factor perturbation trials. Requires `setMultiPhaseCheck(true)`. |

##### `setCheckForLiquidLiquidSplit(boolean)` — LLE detection in the two-phase flash

This is the recommended option when you suspect a **liquid-liquid split** but do not need full three-phase (VLLE) flash. It works within the standard two-phase `TPflash` without invoking `TPmultiflash`, keeping computational cost low.

The standard stability analysis uses Wilson K-value initial guesses, which assume gas-liquid equilibrium. At temperatures well below the critical temperature of the lightest component, Wilson K-values converge close to 1 for all components, making them ineffective for detecting LLE. When this flag is enabled, the flash supplements the standard analysis with **pure-component stability trials**: trial phases initialized as nearly-pure heaviest and lightest components, solved with successive substitution + DEM acceleration, with the trial phase forced to the liquid root of the cubic EOS.

The pure-component trials are invoked at two points in the algorithm:
1. **During initial stability check** (Phase 3 above) — if the standard analysis declares stable but `doCheckForLiquidLiquidSplit()` is true
2. **During post-convergence verification** (Phase 5) — if the converged two-phase flash pushed $\beta$ to the limits (effectively single phase)

```java
// Detect liquid-liquid split (e.g., methane/n-heptane at low T)
SystemInterface fluid = new SystemSrkEos(200.0, 50.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("n-heptane", 0.5);
fluid.setMixingRule("classic");

// Enable LLE detection in two-phase flash (lightweight)
fluid.setCheckForLiquidLiquidSplit(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Beta (phase 0): " + fluid.getBeta(0));
// Correctly finds two liquid phases instead of a single liquid
```

**When to use `setCheckForLiquidLiquidSplit(true)`:**
- Binary or multicomponent systems with large molecular weight differences (e.g., methane + heavy hydrocarbons)
- Temperatures well below the critical temperature of the lightest component
- LLE phase envelope calculations where the two-phase flash must track both liquid phases
- Any system where the standard flash returns a single liquid phase but you expect two liquids

##### `setMultiPhaseCheck(boolean)` — Full multi-phase flash (3+ phases)

Delegates post-convergence processing to `TPmultiflash`, which can detect gas + multiple liquid phases (e.g., gas-oil-aqueous). This is the most thorough but computationally expensive option:

```java
fluid.setMultiPhaseCheck(true);
ops.TPflash();
// Will detect gas + multiple liquid phases (e.g., gas-oil-aqueous)
```

##### `setEnhancedMultiPhaseCheck(boolean)` — Enhanced stability in multi-phase flash

Activates enhanced stability analysis within `TPmultiflash`. Requires `setMultiPhaseCheck(true)`. Uses three trial types per reference phase:
1. **Vapor-like** ($W_i = K_i$) — standard VLE gas detection
2. **Liquid-like** ($W_i = 1/K_i$) — standard VLE liquid detection
3. **LLE trial** ($W_i = z_i \cdot f(\omega_i)$) — acentric factor-based perturbation for polarity-driven splits

Also tests stability **against all existing phases** (not just the reference phase) and uses Wegstein acceleration.

```java
// Full three-phase detection for sour gas
SystemInterface sourGas = new SystemPrEos(210.0, 55.0);
sourGas.addComponent("methane", 49.88);
sourGas.addComponent("CO2", 9.87);
sourGas.addComponent("H2S", 40.22);

sourGas.setMixingRule("classic");
sourGas.setMultiPhaseCheck(true);
sourGas.setEnhancedMultiPhaseCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(sourGas);
ops.TPflash();
sourGas.initProperties();

System.out.println("Number of phases: " + sourGas.getNumberOfPhases());
// May find: vapor + CO2-rich liquid + H2S-rich liquid
```

##### When to use which method

| Scenario | Recommended method |
|----------|--------------------|
| Simple VLE (gas-liquid) | None (default) |
| Binary LLE (e.g., methane/n-heptane) | `setCheckForLiquidLiquidSplit(true)` |
| Oil + water systems | `setMultiPhaseCheck(true)` |
| Sour gas / CO₂ VLLE | `setMultiPhaseCheck(true)` + `setEnhancedMultiPhaseCheck(true)` |
| Phase envelope sweeps needing LLE | `setCheckForLiquidLiquidSplit(true)` |
| Near-critical conditions | `setMultiPhaseCheck(true)` + `setEnhancedMultiPhaseCheck(true)` |

**Note:** `setCheckForLiquidLiquidSplit(true)` adds minimal overhead and can be safely left on for systems where LLE is possible. `setMultiPhaseCheck(true)` and `setEnhancedMultiPhaseCheck(true)` add significant computational cost and should only be enabled when three or more phases are expected.

#### Convergence Parameters

| Parameter | Value | Description |
|-----------|-------|-------------|
| `maxNumberOfIterations` | 50 | Max iterations per inner convergence loop |
| `accelerateInterval` | 5 | GDEM-2 acceleration frequency |
| `newtonLimit` | 12 | SS iterations before switching to Newton |
| `tmLimit` | $-10^{-8}$ | Tangent plane distance threshold for instability |
| `phaseFractionMinimumLimit` | $10^{-12}$ | Minimum phase fraction before phase removal |
| Gibbs energy guard cooldown | 6 iterations | Wait time after Gibbs increase before allowing acceleration |

#### References

- Michelsen, M.L. (1982a). "The isothermal flash problem. Part I. Stability." *Fluid Phase Equilibria*, 9, 1-19.
- Michelsen, M.L. (1982b). "The isothermal flash problem. Part II. Phase-split calculation." *Fluid Phase Equilibria*, 9, 21-40.
- Michelsen, M.L. & Mollerup, J.M. (2007). *Thermodynamic Models: Fundamentals & Computational Aspects.* Tie-Line Publications.
- Risnes, R. & Dalen, V. (1984). "Equilibrium calculations for coexisting liquid phases." *SPE Journal*, 24, 87-95.
- Risnes, R., Dalen, V. & Jensen, J.I. (1981). "Phase equilibrium calculations in the near-critical region." *Proc. European Symposium on EOR*, Bournemouth.
- Nielsen, R.F. & Lia, T. (2023). "A numerically robust Rachford-Rice solver." *J. Comp. Physics*.

---

### PH Flash (Pressure-Enthalpy)

Given pressure and total enthalpy, find temperature and phase split. Essential for:
- Valve/throttling calculations (isenthalpic expansion)
- Heat exchanger design
- Process streams after heating/cooling

**Method signatures:**
```java
void PHflash(double Hspec)                    // H in J
void PHflash(double Hspec, String unit)       // Supported: J, J/mol, J/kg, kJ/kg
void PHflash(double Hspec, int type)          // type 0 = standard
```

**Example - Joule-Thomson expansion:**
```java
SystemInterface fluid = new SystemSrkEos(350.0, 100.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Store inlet enthalpy
double inletH = fluid.getEnthalpy("J");

// Reduce pressure (isenthalpic process)
fluid.setPressure(10.0, "bara");

// Find new temperature at same enthalpy
ops.PHflash(inletH);

System.out.println("Outlet temperature: " + fluid.getTemperature("C") + " °C");
// Demonstrates Joule-Thomson cooling
```

**With unit specification:**
```java
// Enthalpy specified in kJ/kg
ops.PHflash(-150.0, "kJ/kg");
```

---

### PS Flash (Pressure-Entropy)

Given pressure and total entropy, find temperature and phase split. Used for:
- Isentropic compression/expansion
- Turbine/compressor calculations
- Ideal work calculations

**Method signatures:**
```java
void PSflash(double Sspec)                    // S in J/K
void PSflash(double Sspec, String unit)       // Supported: J/K, J/molK, J/kgK, kJ/kgK
```

**Example - Isentropic compression:**
```java
SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

// Inlet conditions
double T1 = fluid.getTemperature("K");
double S_inlet = fluid.getEntropy("J/K");

// Compress to higher pressure (isentropic)
fluid.setPressure(50.0, "bara");
ops.PSflash(S_inlet);

double T2 = fluid.getTemperature("K");
System.out.println("Isentropic outlet T: " + (T2 - 273.15) + " °C");

// Compare to actual with polytropic efficiency
double eta_poly = 0.85;
double T2_actual = T1 + (T2 - T1) / eta_poly;
```

---

### PU Flash (Pressure-Internal Energy)

Given pressure and internal energy, find temperature and phase split.

**Method signatures:**
```java
void PUflash(double Uspec)                    // U in J
void PUflash(double Uspec, String unit)       // Supported: J, J/mol, J/kg, kJ/kg
void PUflash(double Pspec, double Uspec, String unitP, String unitU)
```

**Example:**
```java
ops.PUflash(100.0, -500.0, "bara", "kJ/kg");
```

---

### TV Flash (Temperature-Volume)

Given temperature and total volume, find pressure and phase split. Used for:
- Fixed-volume vessel calculations
- Constant volume processes

**Method signatures:**
```java
void TVflash(double Vspec)                    // V in cm³
void TVflash(double Vspec, String unit)       // Supported: m3
```

**Example - Fixed volume vessel:**
```java
SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
fluid.addComponent("nitrogen", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Vessel volume = 1 m³
double vesselVolume = 1.0;  // m³

// Heat the vessel (isochoric process)
fluid.setTemperature(400.0, "K");
ops.TVflash(vesselVolume, "m3");

System.out.println("New pressure: " + fluid.getPressure("bara") + " bara");
```

---

### TS Flash (Temperature-Entropy)

Given temperature and entropy, find pressure and phase split. Uses the Q-function methodology based on Michelsen (1999).

**Method signatures:**
```java
void TSflash(double Sspec)                    // S in J/K
void TSflash(double Sspec, String unit)       // Supported: J/K, J/molK, J/kgK, kJ/kgK
```

**Thermodynamic derivative:**
$$\left(\frac{\partial S}{\partial P}\right)_T = -\left(\frac{\partial V}{\partial T}\right)_P$$

---

### TH Flash (Temperature-Enthalpy)

Given temperature and enthalpy, find pressure and phase split. Uses the Q-function methodology with Newton iteration.

**Applications:**
- Heat exchanger design at fixed temperature
- Isenthalpic processes with temperature constraint
- Process simulation where T and H are specified independently

**Method signatures:**
```java
void THflash(double Hspec)                    // H in J
void THflash(double Hspec, String unit)       // Supported: J, J/mol, J/kg, kJ/kg
```

**Example:**
```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Store enthalpy at initial pressure
double H_target = fluid.getEnthalpy("J");

// Change temperature (enthalpy will change)
fluid.setTemperature(280.0, "K");

// Find pressure that gives same enthalpy at new temperature
ops.THflash(H_target);
System.out.println("Pressure for same H at new T: " + fluid.getPressure("bara") + " bara");
```

**Thermodynamic derivative:**
$$\left(\frac{\partial H}{\partial P}\right)_T = V - T\left(\frac{\partial V}{\partial T}\right)_P$$

---

### TU Flash (Temperature-Internal Energy)

Given temperature and internal energy, find pressure and phase split. Uses the Q-function methodology with Newton iteration.

**Applications:**
- Isochoric (constant volume) combustion processes
- Fixed temperature, constant internal energy processes
- Closed system thermodynamic analysis

**Method signatures:**
```java
void TUflash(double Uspec)                    // U in J
void TUflash(double Uspec, String unit)       // Supported: J, J/mol, J/kg, kJ/kg
```

**Example:**
```java
SystemInterface fluid = new SystemSrkEos(350.0, 20.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Store internal energy
double U_target = fluid.getInternalEnergy("J");

// Change temperature
fluid.setTemperature(300.0, "K");

// Find pressure that maintains same internal energy
ops.TUflash(U_target);
System.out.println("Pressure for same U at new T: " + fluid.getPressure("bara") + " bara");
```

**Thermodynamic derivative:**
$$\left(\frac{\partial U}{\partial P}\right)_T = -T\left(\frac{\partial V}{\partial T}\right)_P - P\left(\frac{\partial V}{\partial P}\right)_T$$

---

### PV Flash (Pressure-Volume)

Given pressure and volume, find temperature and phase split. Uses the Q-function methodology with Newton iteration.

**Applications:**
- Fixed volume vessels at constant pressure
- Density specifications (since V = m/ρ for known mass)
- Process simulation where P and V are specified independently

**Method signatures:**
```java
void PVflash(double Vspec)                    // V in m³
void PVflash(double Vspec, String unit)       // Supported: m3
```

**Example:**
```java
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("nitrogen", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Store volume at initial conditions
double V_target = fluid.getVolume("m3");

// Change pressure
fluid.setPressure(100.0, "bara");

// Find temperature that gives same volume at new pressure
ops.PVflash(V_target);
System.out.println("Temperature for same V at new P: " + fluid.getTemperature("C") + " °C");
```

**Thermodynamic derivative:**
$$\left(\frac{\partial V}{\partial T}\right)_P$$

---

### VH Flash (Volume-Enthalpy)

Given volume and enthalpy, find temperature, pressure, and phase split. Used for:
- Dynamic simulations
- Closed system energy balances

**Method signatures:**
```java
void VHflash(double Vspec, double Hspec)
void VHflash(double V, double H, String unitV, String unitH)
```

**Example:**
```java
ops.VHflash(0.5, -50000.0, "m3", "J");
```

---

### VU Flash (Volume-Internal Energy)

Given volume and internal energy, find temperature, pressure, and phase split. Critical for:
- Dynamic vessel simulations
- Depressurization calculations
- Blowdown modeling

**Method signatures:**
```java
void VUflash(double Vspec, double Uspec)
void VUflash(double V, double U, String unitV, String unitU)
```

**Example - Dynamic depressurization:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(300.0, 100.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("CO2", 0.1);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Initial state
double V0 = fluid.getVolume("m3");
double U0 = fluid.getInternalEnergy("J");

// Simulate adiabatic expansion (U constant, V increases)
double V_new = V0 * 2.0;  // Volume doubles
ops.VUflash(V_new, U0, "m3", "J");

System.out.println("New T: " + fluid.getTemperature("C") + " °C");
System.out.println("New P: " + fluid.getPressure("bara") + " bara");
```

---

### VS Flash (Volume-Entropy)

Given volume and entropy, find temperature, pressure, and phase split.

**Method signatures:**
```java
void VSflash(double Vspec, double Sspec)
void VSflash(double V, double S, String unitV, String unitS)
```

---

## Q-Function Flash Methodology

Several flash types in NeqSim use the Q-function methodology described by Michelsen (1999). This approach is particularly effective for state function specifications (entropy, enthalpy, internal energy, volume) where the flash must solve for temperature and/or pressure.

### Theory

The Q-function method uses a nested iteration approach:
1. **Outer loop**: Newton iteration on the state function residual (e.g., $S - S_{spec}$)
2. **Inner loop**: TP flash to determine phase equilibrium at each T,P guess

The key advantage is that the inner TP flash handles all the phase equilibrium complexity, while the outer loop only needs to adjust T or P based on the state function derivative.

### Thermodynamic Derivatives

The Q-function flashes use analytical thermodynamic derivatives computed via `init(3)`:

| Flash | Specification | Solved | Derivative Used |
|-------|--------------|--------|-----------------|
| TSflash | T, S | P | $\left(\frac{\partial S}{\partial P}\right)_T = -\left(\frac{\partial V}{\partial T}\right)_P$ |
| THflash | T, H | P | $\left(\frac{\partial H}{\partial P}\right)_T = V - T\left(\frac{\partial V}{\partial T}\right)_P$ |
| TUflash | T, U | P | $\left(\frac{\partial U}{\partial P}\right)_T = -T\left(\frac{\partial V}{\partial T}\right)_P - P\left(\frac{\partial V}{\partial P}\right)_T$ |
| TVflash | T, V | P | $\left(\frac{\partial V}{\partial P}\right)_T$ |
| PVflash | P, V | T | $\left(\frac{\partial V}{\partial T}\right)_P$ |
| VUflash | V, U | T, P | 2D Newton with $\frac{\partial U}{\partial T}$, $\frac{\partial U}{\partial P}$, $\frac{\partial V}{\partial T}$, $\frac{\partial V}{\partial P}$ |
| VHflash | V, H | T, P | 2D Newton with $\frac{\partial H}{\partial T}$, $\frac{\partial H}{\partial P}$, $\frac{\partial V}{\partial T}$, $\frac{\partial V}{\partial P}$ |
| VSflash | V, S | T, P | 2D Newton with $\frac{\partial S}{\partial T}$, $\frac{\partial S}{\partial P}$, $\frac{\partial V}{\partial T}$, $\frac{\partial V}{\partial P}$ |

### Implementation Pattern

All Q-function flashes follow a similar pattern:

```java
// Example: TH flash pseudocode
public double solveQ() {
    do {
        system.init(3);  // Calculate derivatives

        double residual = system.getEnthalpy() - Hspec;

        // Analytical derivative: (dH/dP)_T = V - T*(dV/dT)_P
        double V = system.getVolume();
        double T = system.getTemperature();
        double dVdT = -system.getdVdTpn();  // Note sign convention
        double dHdP = (V - T * dVdT) * 1e5;  // Convert to J/bar

        // Newton step with damping
        double deltaP = -factor * residual / dHdP;

        nyPres = oldPres + deltaP;
        system.setPressure(nyPres);
        tpFlash.run();

    } while (error > tolerance && iterations < maxIter);
}
```

### NeqSim Sign Conventions

Note the sign conventions used in NeqSim for thermodynamic derivatives:
- `getdVdTpn()` returns $-\left(\frac{\partial V}{\partial T}\right)_P$
- `getdVdPtn()` returns $\left(\frac{\partial V}{\partial P}\right)_T$

### References

1. Michelsen, M.L. (1999). "State function based flash specifications." *Fluid Phase Equilibria*, 158-160, 617-626.
2. Michelsen, M.L. and Mollerup, J.M. (2007). *Thermodynamic Models: Fundamentals & Computational Aspects*, 2nd Edition. Tie-Line Publications.

---

## Saturation Calculations

### Bubble Point

Calculate the bubble point (onset of vaporization) at a given temperature or pressure.

**Temperature flash (find T at given P):**
```java
void bubblePointTemperatureFlash()
```

**Pressure flash (find P at given T):**
```java
void bubblePointPressureFlash()
void bubblePointPressureFlash(boolean derivatives)  // Include dP/dT, dP/dx
```

**Example:**
```java
SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
fluid.addComponent("propane", 0.5);
fluid.addComponent("n-butane", 0.5);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// Find bubble point pressure at 25°C
fluid.setTemperature(298.15, "K");
ops.bubblePointPressureFlash();
System.out.println("Bubble point pressure: " + fluid.getPressure("bara") + " bara");

// Find bubble point temperature at 5 bar
fluid.setPressure(5.0, "bara");
ops.bubblePointTemperatureFlash();
System.out.println("Bubble point temperature: " + fluid.getTemperature("C") + " °C");
```

---

### Dew Point

Calculate the dew point (onset of condensation) at a given temperature or pressure.

**Temperature flash:**
```java
void dewPointTemperatureFlash()
void dewPointTemperatureFlash(boolean derivatives)
```

**Pressure flash:**
```java
void dewPointPressureFlash()
```

**Example:**
```java
// Natural gas dew point
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.04);
gas.addComponent("n-butane", 0.02);
gas.addComponent("n-pentane", 0.01);
gas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);

// Find dew point at 50 bar
ops.dewPointTemperatureFlash();
System.out.println("Hydrocarbon dew point: " + gas.getTemperature("C") + " °C");
```

---

### Water Dew Point

Calculate the water dew point (onset of water condensation).

**Methods:**
```java
void waterDewPointTemperatureFlash()
void waterDewPointTemperatureMultiphaseFlash()  // For complex systems
```

**Example:**
```java
SystemInterface wetGas = new SystemSrkCPAstatoil(298.15, 80.0);
wetGas.addComponent("methane", 0.95);
wetGas.addComponent("water", 0.05);
wetGas.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(wetGas);
ops.waterDewPointTemperatureFlash();

System.out.println("Water dew point: " + wetGas.getTemperature("C") + " °C");
```

---

### Hydrocarbon Dew Point

Calculate the cricondentherm (maximum temperature for two-phase region).

```java
void dewPointPressureFlashHC()
```

---

## Special Flash Types

### Solid Phase Flash

For systems with potential solid precipitation (wax, ice, hydrates).

```java
void TPSolidflash()
void PHsolidFlash(double Hspec)
void freezingPointTemperatureFlash()
```

**Example - Wax precipitation:**
```java
SystemInterface oil = new SystemSrkEos(320.0, 10.0);
oil.addComponent("n-C20", 0.1);
oil.addComponent("n-C10", 0.9);
oil.setSolidPhaseCheck("n-C20");
oil.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(oil);
ops.freezingPointTemperatureFlash();
System.out.println("Wax appearance temperature: " + oil.getTemperature("C") + " °C");
```

---

### Critical Point Flash

Find the critical point of a mixture.

```java
void criticalPointFlash()
```

**Example:**
```java
SystemInterface mix = new SystemSrkEos(300.0, 50.0);
mix.addComponent("methane", 0.7);
mix.addComponent("ethane", 0.3);
mix.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(mix);
ops.criticalPointFlash();

System.out.println("Critical T: " + mix.getTemperature("K") + " K");
System.out.println("Critical P: " + mix.getPressure("bara") + " bara");
```

---

### Gradient Flash

Calculate composition variation with depth (gravitational segregation).

```java
SystemInterface TPgradientFlash(double height, double temperature)
```

**Parameters:**
- `height`: Depth in meters
- `temperature`: Temperature at depth in Kelvin

---

### Phase Fraction Flash

Find conditions for a specified phase fraction.

```java
void constantPhaseFractionPressureFlash(double fraction)      // Find P at given vapor fraction
void constantPhaseFractionTemperatureFlash(double fraction)   // Find T at given vapor fraction
void TVfractionFlash(double Vfraction)                        // Volume fraction based
```

---

## Reference EoS Flash Methods

For high-accuracy calculations, NeqSim provides flash methods using reference equations of state.

### GERG-2008 Flashes

For natural gas systems with GERG-2008 accuracy:

```java
void PHflashGERG2008(double Hspec)
void PSflashGERG2008(double Sspec)
```

**Example:**
```java
SystemInterface gas = new SystemGERG2008Eos(280.0, 100.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.06);
gas.addComponent("CO2", 0.04);

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();
double H = gas.getEnthalpy("J");

gas.setPressure(50.0);
ops.PHflashGERG2008(H);  // High-accuracy isenthalpic flash
```

### Leachman (Hydrogen) Flashes

For pure hydrogen systems:

```java
void PHflashLeachman(double Hspec)
void PSflashLeachman(double Sspec)
```

### Vega (CO₂) Flashes

For pure CO₂ systems:

```java
void PHflashVega(double Hspec)
void PSflashVega(double Sspec)
```

---

## Hydrate Calculations

NeqSim provides comprehensive calculations for gas hydrate formation, including multi-phase equilibrium with hydrate, inhibitor effects, and cavity occupancy calculations.

> **📚 Detailed Documentation:**
> - [Hydrate Models](hydrate_models) - Thermodynamic models (vdWP, CPA, PVTsim)
> - [Hydrate Flash Operations](../thermodynamicoperations/hydrate_flash_operations) - Complete flash API

### Hydrate TPflash

Calculate phase equilibrium including hydrate at given T and P:

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("water", 0.03);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateTPflash();

// Check phases: GAS, AQUEOUS, HYDRATE
fluid.prettyPrint();
```

### Gas-Hydrate Equilibrium (No Aqueous)

For systems with trace water where all water can be consumed by hydrate:

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 15.0, 250.0);
fluid.addComponent("methane", 0.9998);
fluid.addComponent("water", 0.0002);  // 200 ppm water
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.gasHydrateTPflash();  // Targets gas-hydrate equilibrium

// Result: GAS + HYDRATE phases (no AQUEOUS)
```

### Formation Temperature

```java
void hydrateFormationTemperature()
void hydrateFormationTemperature(double initialGuess)
void hydrateFormationTemperature(int structure)  // 0=ice, 1=sI, 2=sII
```

### Formation Pressure

```java
void hydrateFormationPressure()
void hydrateFormationPressure(int structure)
```

### Inhibitor Calculations

```java
void hydrateInhibitorConcentration(String inhibitor, double targetT)
void hydrateInhibitorConcentrationSet(String inhibitor, double wtFrac)
```

**Supported inhibitors:** MEG, TEG, methanol, ethanol

### Complete Example

```java
SystemInterface gas = new SystemSrkCPAstatoil(280.0, 100.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.05);
gas.addComponent("CO2", 0.02);
gas.addComponent("water", 0.03);
gas.setMixingRule(10);
gas.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(gas);

// Calculate hydrate formation temperature
ops.hydrateFormationTemperature();
System.out.println("Hydrate formation T: " + gas.getTemperature("C") + " °C");

// Check if hydrate forms at 5°C
gas.setTemperature(273.15 + 5.0);
ops.hydrateTPflash();
if (gas.hasHydratePhase()) {
    System.out.println("Hydrate fraction: " + gas.getBeta(PhaseType.HYDRATE));
}
```

### Four-Phase Equilibrium (Gas-Oil-Aqueous-Hydrate)

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 4.0, 100.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-hexane", 0.02);
fluid.addComponent("n-heptane", 0.05);
fluid.addComponent("water", 0.10);
fluid.setMixingRule(10);

ops.hydrateTPflash();
// Phases: GAS, OIL, AQUEOUS, HYDRATE
```

---

## Unit Handling

Most flash methods accept unit specifications for flexibility:

### Enthalpy Units (PH Flash)

| Unit | Description |
|------|-------------|
| `J` | Joules (total) |
| `J/mol` | Joules per mole |
| `J/kg` | Joules per kilogram |
| `kJ/kg` | Kilojoules per kilogram |

### Entropy Units (PS Flash)

| Unit | Description |
|------|-------------|
| `J/K` | Joules per Kelvin (total) |
| `J/molK` | Joules per mole-Kelvin |
| `J/kgK` | Joules per kg-Kelvin |
| `kJ/kgK` | Kilojoules per kg-Kelvin |

### Volume Units

| Unit | Description |
|------|-------------|
| `m3` | Cubic meters |
| (default) | cm³ for internal methods |

---

## Error Handling

Flash calculations can fail if:
- Conditions are outside valid range
- No solution exists (e.g., above critical point for saturation)
- Convergence not achieved

**Best practice:**
```java
try {
    ops.dewPointTemperatureFlash();
} catch (IsNaNException e) {
    System.err.println("No dew point found: " + e.getMessage());
}

// Check for valid result
if (Double.isNaN(fluid.getTemperature())) {
    System.err.println("Flash calculation did not converge");
}
```

---

## Best Practices

1. **Always initialize before flashing:**
   ```java
   fluid.init(0);  // Basic initialization
   ops.TPflash();
   fluid.init(3);  // Full thermodynamic initialization after flash
   ```

2. **Reuse ThermodynamicOperations:**
   ```java
   // Good - single instance
   ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
   for (double T : temperatures) {
       fluid.setTemperature(T, "K");
       ops.TPflash();
   }
   ```

3. **Clone fluids for independent calculations:**
   ```java
   SystemInterface fluid2 = fluid.clone();
   ThermodynamicOperations ops2 = new ThermodynamicOperations(fluid2);
   ```

4. **Check phase existence before accessing:**
   ```java
   if (fluid.hasPhaseType("gas")) {
       double gasRho = fluid.getPhase("gas").getDensity("kg/m3");
   }
   ```

5. **Use appropriate EoS for the application:**
   - Hydrocarbons: SRK or PR
   - Polar/associating: CPA
   - Natural gas (high accuracy): GERG-2008
   - CO₂ systems: CPA or Vega

---

## API Reference Table

| Method | Input Specs | Output | Use Case |
|--------|-------------|--------|----------|
| `TPflash()` | T, P | phases, compositions | General equilibrium |
| `PHflash(H)` | P, H | T, phases | Valves, heat exchangers |
| `PSflash(S)` | P, S | T, phases | Compressors, turbines |
| `PUflash(U)` | P, U | T, phases | Energy balance |
| `TVflash(V)` | T, V | P, phases | Fixed-volume systems |
| `TSflash(S)` | T, S | P, phases | Process analysis |
| `VHflash(V,H)` | V, H | T, P, phases | Dynamic simulation |
| `VUflash(V,U)` | V, U | T, P, phases | Blowdown, depressurization |
| `VSflash(V,S)` | V, S | T, P, phases | Isentropic vessel |
| `bubblePointTemperatureFlash()` | P | T_bubble | Evaporator design |
| `bubblePointPressureFlash()` | T | P_bubble | Vapor pressure |
| `dewPointTemperatureFlash()` | P | T_dew | Condenser design |
| `dewPointPressureFlash()` | T | P_dew | Dew point control |
| `waterDewPointTemperatureFlash()` | P | T_wdp | Gas dehydration |
| `hydrateFormationTemperature()` | P | T_hydrate | Hydrate prevention |
| `criticalPointFlash()` | - | T_c, P_c | Mixture critical |

---

## See Also

- [Fluid Creation Guide](fluid_creation_guide) - Creating thermodynamic systems
- [Mixing Rules Guide](mixing_rules_guide) - Configuring phase equilibrium models
- [Component Database Guide](component_database_guide) - Pure component parameters
- [Thermodynamic Operations](thermodynamic_operations) - Overview of operations

---

## References

1. Michelsen, M.L. & Mollerup, J.M. (2007). Thermodynamic Models: Fundamentals & Computational Aspects.
2. Rachford, H.H. & Rice, J.D. (1952). Procedure for Use of Electronic Digital Computers in Calculating Flash Vaporization.
3. Kunz, O. & Wagner, W. (2012). The GERG-2008 Wide-Range Equation of State for Natural Gases.
