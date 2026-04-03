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
- [Phase Equilibrium Modes (VLE, LLE, VLLE)](#phase-equilibrium-modes-vle-lle-vlle)
  - [VLE — Vapor-Liquid Equilibrium (Default)](#vle--vapor-liquid-equilibrium-default)
  - [LLE — Liquid-Liquid Equilibrium](#lle--liquid-liquid-equilibrium)
  - [VLLE — Vapor-Liquid-Liquid Equilibrium](#vlle--vapor-liquid-liquid-equilibrium)
  - [Automatic LLE Detection](#automatic-lle-detection)
  - [Quick Reference Table](#quick-reference-table)
- [Other Flash Types](#other-flash-types)
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

The most common flash type. Given temperature and pressure, find phase split and compositions. NeqSim implements the classical Michelsen flash algorithm with stability analysis.

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

---

## Phase Equilibrium Modes (VLE, LLE, VLLE)

NeqSim supports three phase equilibrium modes with increasing computational cost. The mode is controlled by two flags on the fluid (`SystemInterface`) **before** running any flash calculation.

| Mode | Method Calls | Phases Detected | Cost |
|------|-------------|-----------------|------|
| **VLE** (default) | *(none — default)* | Gas + one liquid | Low |
| **LLE / VLLE** | `setMultiPhaseCheck(true)` | Gas + multiple liquids | Medium |
| **Enhanced VLLE** | `setEnhancedMultiPhaseCheck(true)` | Robust multi-phase detection | Higher |

### VLE — Vapor-Liquid Equilibrium (Default)

The default mode solves a two-phase (vapor-liquid) flash using the Michelsen algorithm with Wilson K-value initialization, successive substitution, and second-order Newton refinement.

```java
// VLE is the default — no extra flags needed
SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("n-heptane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

System.out.println("Phases: " + fluid.getNumberOfPhases());      // 1 or 2
System.out.println("Vapor fraction: " + fluid.getBeta());         // Molar vapor fraction
System.out.println("Phase types: " + fluid.getPhaseTypeName(0));  // "gas" or "oil"
```

```python
# Python equivalent
from neqsim import jneqsim

fluid = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("n-heptane", 0.1)
fluid.setMixingRule("classic")

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Phases: {fluid.getNumberOfPhases()}")
print(f"Vapor fraction: {fluid.getBeta():.4f}")
```

**When to use:** Simple hydrocarbon systems (gas processing, compression, pipelines) where at most one liquid phase forms.

### LLE — Liquid-Liquid Equilibrium

For systems that can form two immiscible liquid phases (e.g., oil + water, glycol + hydrocarbon), enable multi-phase checking. This runs `TPmultiflash` after the initial two-phase flash to detect additional liquid phases.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(303.15, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("n-heptane", 0.2);
fluid.addComponent("water", 0.1);
fluid.setMixingRule(10);  // CPA mixing rule for water association
fluid.setMultiPhaseCheck(true);  // Enable LLE / VLLE detection

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

System.out.println("Phases: " + fluid.getNumberOfPhases());  // Typically 3 (gas + oil + aqueous)

// Access individual phases
if (fluid.hasPhaseType("gas")) {
    System.out.println("Gas density: " + fluid.getPhase("gas").getDensity("kg/m3"));
}
if (fluid.hasPhaseType("oil")) {
    System.out.println("Oil density: " + fluid.getPhase("oil").getDensity("kg/m3"));
}
if (fluid.hasPhaseType("aqueous")) {
    System.out.println("Water density: " + fluid.getPhase("aqueous").getDensity("kg/m3"));
}
```

```python
# Python equivalent
fluid = jneqsim.thermo.system.SystemSrkCPAstatoil(303.15, 50.0)
fluid.addComponent("methane", 0.7)
fluid.addComponent("n-heptane", 0.2)
fluid.addComponent("water", 0.1)
fluid.setMixingRule(10)
fluid.setMultiPhaseCheck(True)  # Enable LLE / VLLE detection

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    phase = fluid.getPhase(i)
    print(f"  Phase {i}: {fluid.getPhaseTypeName(i)}, "
          f"density = {phase.getDensity('kg/m3'):.1f} kg/m3")
```

**How it works internally:**
1. `TPflash` first solves the standard two-phase problem
2. If `doMultiPhaseCheck()` is true, `TPmultiflash` is invoked
3. `TPmultiflash` performs additional stability analysis against all existing phases and adds/removes phases until Gibbs energy is minimized

**When to use:** Any system containing water + hydrocarbons, glycol systems, methanol injection, or other mixtures where two liquid phases can coexist.

### VLLE — Vapor-Liquid-Liquid Equilibrium

For robust three-phase detection in complex systems (sour gas, CO₂-rich streams, near-critical conditions), use enhanced multi-phase checking. This activates additional supplementary stability trials:

- **Amplified K-value trials** — catch near-critical VLE instability near the cricondenbar where standard Wilson K-values converge to trivial solutions
- **Pure-component stability trials** — catch LLE instability at temperatures well below the lightest component's critical temperature, where Wilson K-values are ineffective
- **Cosine-similarity trivial-solution filtering** — rejects spurious two-phase results where both phases have nearly identical composition

```java
// Sour gas system with potential three-phase split
SystemInterface fluid = new SystemPrEos(210.0, 55.0);  // -63°C, 55 bara
fluid.addComponent("methane", 49.88);
fluid.addComponent("CO2", 9.87);
fluid.addComponent("H2S", 40.22);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);
fluid.setEnhancedMultiPhaseCheck(true);  // Enhanced stability trials

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();

System.out.println("Phases: " + fluid.getNumberOfPhases());
// May find: vapor + CO2-rich liquid + H2S-rich liquid
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    System.out.println("Phase " + i + ": " + fluid.getPhaseTypeName(i)
        + ", beta=" + fluid.getBeta(i));
}
```

```python
# Python equivalent
fluid = jneqsim.thermo.system.SystemPrEos(210.0, 55.0)
fluid.addComponent("methane", 49.88)
fluid.addComponent("CO2", 9.87)
fluid.addComponent("H2S", 40.22)
fluid.setMixingRule("classic")
fluid.setMultiPhaseCheck(True)
fluid.setEnhancedMultiPhaseCheck(True)

ops = jneqsim.thermodynamicoperations.ThermodynamicOperations(fluid)
ops.TPflash()
fluid.initProperties()

print(f"Phases: {fluid.getNumberOfPhases()}")
for i in range(fluid.getNumberOfPhases()):
    print(f"  Phase {i}: {fluid.getPhaseTypeName(i)}, beta={fluid.getBeta(i):.4f}")
```

**Note:** `setEnhancedMultiPhaseCheck(true)` implicitly calls `setMultiPhaseCheck(true)`, so you do not need to set both.

**When to use:**
- Sour gas (methane/CO₂/H₂S) at low temperatures
- CO₂ injection and sequestration systems
- Near-critical conditions where phase detection is difficult
- Any system where the standard flash misses expected phases

### Automatic LLE Detection

For certain equation-of-state models, NeqSim automatically enables supplementary LLE stability checks even without `setEnhancedMultiPhaseCheck(true)`, provided `setMultiPhaseCheck(true)` is set:

| Model Type | Auto LLE | Condition |
|------------|----------|----------|
| **CPA** (e.g., `SystemSrkCPAstatoil`) | Yes | Always, when `setMultiPhaseCheck(true)` |
| **Electrolyte** models | Yes | Always, when `setMultiPhaseCheck(true)` |
| **Water-containing** (any EoS) | Only with enhanced | `setEnhancedMultiPhaseCheck(true)` |
| **Chemical reaction** systems | Only with enhanced | `setEnhancedMultiPhaseCheck(true)` |
| **Standard cubic** (SRK, PR) | No | Use `setMultiPhaseCheck(true)` for `TPmultiflash` |

This means CPA models (commonly used for water + hydrocarbon systems) automatically get robust LLE detection when multi-phase check is enabled — no need for `setEnhancedMultiPhaseCheck(true)`.

```java
// CPA model: automatic LLE detection with just setMultiPhaseCheck
SystemInterface fluid = new SystemSrkCPAstatoil(303.15, 50.0);
fluid.addComponent("methane", 0.7);
fluid.addComponent("MEG", 0.3);
fluid.setMixingRule(10);
fluid.setMultiPhaseCheck(true);  // Sufficient for CPA — auto LLE kicks in

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();
// LLE between MEG-rich and hydrocarbon-rich phases detected automatically
```

### Quick Reference Table

| Scenario | `setMultiPhaseCheck` | `setEnhancedMultiPhaseCheck` | EoS |
|----------|---------------------|------------------------------|-----|
| Dry gas compression | — | — | SRK/PR |
| Gas + condensate | — | — | SRK/PR |
| Gas + water (simple) | `true` | — | CPA |
| Oil + water separation | `true` | — | CPA |
| TEG dehydration | `true` | — | CPA |
| Sour gas (CO₂/H₂S) | `true` | `true` | PR |
| CO₂ injection/CCS | `true` | `true` | CPA or PR |
| Near-critical fluids | `true` | `true` | SRK/PR |
| Methanol injection | `true` | — | CPA |
| Electrolyte systems | `true` | — | Electrolyte CPA |

**Performance tip:** Only enable multi-phase checking when needed. Each additional stability trial adds computational cost — roughly 2-3x for `setMultiPhaseCheck(true)` and 3-5x for `setEnhancedMultiPhaseCheck(true)` compared to the default VLE flash.

---

## Other Flash Types

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
