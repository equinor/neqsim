---
title: "Reactive Flash: Simultaneous Chemical and Phase Equilibrium"
description: "Guide to the Modified RAND method for simultaneous chemical and phase equilibrium in NeqSim. Covers single-phase chemical equilibrium, multiphase VLE+CE, ionic reactions in aqueous systems, auto-discovery of reaction products, and reactive PH/PS flash (isenthalpic/isentropic) for adiabatic reactor temperature calculation."
---

# Reactive Flash: Simultaneous Chemical and Phase Equilibrium

## Overview

The reactive flash solves **simultaneous chemical equilibrium (CE) and phase equilibrium (PE)** by minimizing the total Gibbs energy subject to element balance constraints. Unlike stoichiometric methods that require explicit reactions and equilibrium constants, this **non-stoichiometric approach** (Modified RAND method) only needs the elemental composition of each component — reactions are discovered automatically from the formula matrix.

**Key capabilities:**

- Single-phase chemical equilibrium (gas-phase reactions, aqueous chemistry)
- Multiphase reactive equilibrium (VLE + CE, VLLE + CE)
- Ionic / electrolyte reactions (CO2 hydration, water dissociation)
- Auto-discovery of reaction products via `chemicalReactionInit`
- Electroneutrality enforcement for charged species

## Algorithm

The algorithm follows the state-of-the-art approach from Tsanas, Stenby & Yan (2017) and Ascani, Sadowski & Held (2023):

1. **Build the formula matrix** $A$ from component elemental composition, where $A_{ki}$ is the number of atoms of element $k$ in component $i$
2. **Determine independent reactions**: $N_R = N_C - \text{rank}(A)$, where $N_C$ is the number of components
3. **Reactive stability analysis**: Check if the single-phase chemically equilibrated feed is stable with respect to phase splitting
4. **Modified RAND iteration**: Solve the Lagrangian system for moles in each phase plus element-balance multipliers
5. **Phase addition/removal**: Add or remove phases as needed
6. **Convergence check**: Verify all equilibrium conditions are satisfied

The optimality condition for each species $i$ in each phase $j$ is:

$$
g_i^0 + \ln x_{ji} + \ln \hat{\varphi}_{ji} = \sum_k \lambda_k A_{ki}
$$

where $g_i^0$ is the dimensionless standard Gibbs energy, $x_{ji}$ is the mole fraction, $\hat{\varphi}_{ji}$ is the fugacity coefficient, and $\lambda_k$ are the Lagrange multipliers for element balance constraints.

## Quick Start

### Java

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.reactiveflash.*;

// Water-gas shift reaction: CO + H2O = CO2 + H2
SystemInterface system = new SystemSrkEos(600.0, 1.0);  // 600 K, 1 bar
system.addComponent("CO", 0.25);
system.addComponent("water", 0.25);
system.addComponent("CO2", 0.25);
system.addComponent("hydrogen", 0.25);
system.setMixingRule("classic");
system.setMaxNumberOfPhases(1);
system.setNumberOfPhases(1);
system.init(0);
system.init(1);

// Option 1: Via ThermodynamicOperations (simplest)
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.reactiveTPflash();

// Option 2: Direct use (more control)
ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(system);
flash.run();

// Read equilibrium compositions
double xCO2 = system.getPhase(0).getComponent("CO2").getx();
double xH2 = system.getPhase(0).getComponent("hydrogen").getx();
```

### Python

```python
from neqsim import jneqsim

# Create fluid
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
system = SystemSrkEos(600.0, 1.0)
system.addComponent("CO", 0.25)
system.addComponent("water", 0.25)
system.addComponent("CO2", 0.25)
system.addComponent("hydrogen", 0.25)
system.setMixingRule("classic")
system.setMaxNumberOfPhases(1)
system.setNumberOfPhases(1)
system.init(0)
system.init(1)

# Option 1: Via ThermodynamicOperations
ThermodynamicOperations = jneqsim.thermodynamicoperations.ThermodynamicOperations
ops = ThermodynamicOperations(system)
ops.reactiveTPflash()

# Option 2: Direct use
import jpype
ReactiveMultiphaseTPflash = jpype.JClass(
    "neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphaseTPflash")
flash = ReactiveMultiphaseTPflash(system)
flash.run()

xCO2 = system.getPhase(0).getComponent("CO2").getx()
```

## Examples

### 1. Single-Phase Gas-Phase Reaction

Reactions where all species are in one phase (e.g., combustion, reforming):

```java
// Steam methane reforming: CH4 + H2O = CO + 3H2  (and WGS)
// 5 components, 3 elements (C, H, O) => 2 independent reactions
SystemInterface system = new SystemSrkEos(1100.0, 1.0);
system.addComponent("methane", 0.20);
system.addComponent("water", 0.30);
system.addComponent("CO", 0.15);
system.addComponent("CO2", 0.10);
system.addComponent("hydrogen", 0.25);
system.setMixingRule("classic");
system.setMaxNumberOfPhases(1);
system.setNumberOfPhases(1);
system.init(0);
system.init(1);

ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(system);
flash.run();
// At 1100 K, methane is significantly converted to syngas
```

### 2. Ammonia Synthesis at High Pressure

The classic Haber-Bosch reaction demonstrates pressure effects:

```java
// N2 + 3H2 = 2NH3 at 500 K, 300 bar
SystemInterface system = new SystemSrkEos(500.0, 300.0);
system.addComponent("nitrogen", 0.375);
system.addComponent("hydrogen", 0.375);
system.addComponent("ammonia", 0.25);
system.setMixingRule("classic");
system.setMaxNumberOfPhases(1);
system.setNumberOfPhases(1);
system.init(0);
system.init(1);

ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(system);
flash.run();
// High pressure shifts equilibrium toward NH3 (fewer moles on product side)
```

### 3. Multiphase VLE + Chemical Equilibrium

For systems with both phase splitting and reactions (e.g., hydrocarbon/water with CO2 hydration), use a **two-step approach**: standard VLE flash first, then reactive flash:

```java
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 50.0);
system.addComponent("methane", 0.50);
system.addComponent("CO2", 0.05);
system.addComponent("n-heptane", 0.10);
system.addComponent("water", 0.35);
// Ionic products of CO2/water equilibrium (charge-balanced)
system.addComponent("HCO3-", 1.0e-10);
system.addComponent("H3O+", 4.0e-10);  // = HCO3- + OH- + 2*CO3--
system.addComponent("OH-", 1.0e-10);
system.addComponent("CO3--", 1.0e-10);
system.setMixingRule(10);  // electrolyte CPA
system.init(0);
system.init(1);

// Step 1: Standard VLE flash for phase splitting
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.TPflash();

// Step 2: Reactive flash for chemical equilibrium on top of VLE
ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(system);
flash.run();
// Result: gas phase (methane, n-heptane) + aqueous phase (water, CO2, ions)
```

### 4. Auto-Discovery of Reactions (chemicalReactionInit)

When you provide only molecular species, the flash can automatically discover ionic products from the reaction database:

```java
SystemInterface system = new SystemElectrolyteCPAstatoil(298.15, 1.0);
system.addComponent("CO2", 0.01);
system.addComponent("water", 0.99);
system.setMixingRule(10);
system.setMaxNumberOfPhases(1);
system.setNumberOfPhases(1);
system.init(0);
system.init(1);

ReactiveMultiphaseTPflash flash = new ReactiveMultiphaseTPflash(system);
flash.setUseChemicalReactionInit(true);  // auto-discover ionic species
flash.setMaxNumberOfPhases(1);           // single aqueous phase
flash.run();

// The flash automatically adds HCO3-, H3O+, OH-, CO3-- from database
// and solves the aqueous-phase chemical equilibrium
```

## API Reference

### ReactiveMultiphaseTPflash

Main entry point for the reactive flash.

| Method | Description |
|--------|-------------|
| `ReactiveMultiphaseTPflash(system)` | Constructor |
| `run()` | Execute the reactive flash |
| `isConverged()` | Check if the flash converged |
| `getTotalIterations()` | Total RAND iterations used |
| `getNumberOfReactions()` | Number of independent reactions ($N_R = N_C - \text{rank}(A)$) |
| `getEquilibriumTotalMoles()` | Total moles at equilibrium (may differ from 1.0 when reactions change total moles) |
| `getFinalGibbsEnergy()` | Final Gibbs energy of the equilibrium state |
| `setUseChemicalReactionInit(boolean)` | Enable auto-discovery of reaction products |
| `setMaxNumberOfPhases(int)` | Override the system's max phases (use when `init()` resets it) |
| `setUseDIIS(boolean)` | Enable/disable DIIS acceleration (default: true) |

### FormulaMatrix

Builds the element-component mapping from the thermodynamic system.

| Method | Description |
|--------|-------------|
| `FormulaMatrix(system)` | Constructor; extracts elemental composition from database |
| `getNumberOfElements()` | Number of elements (rows of $A$) |
| `getRank()` | Rank of the formula matrix |
| `getNumberOfIndependentReactions()` | $N_R = N_C - \text{rank}(A)$ |
| `hasIonicSpecies()` | Whether the system has charged species |
| `getElementNames()` | Element names (includes "charge" if ions present) |

### ThermodynamicOperations Integration

```java
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.reactiveTPflash();  // convenience method
```

## Important Notes

### Electrolyte Systems and `init()` Behavior

When using `SystemElectrolyteCPAstatoil` with `setMixingRule(10)`, calling `init(0)` or `init(1)` resets `maxNumberOfPhases` to 2 regardless of what you set. If you need single-phase chemical equilibrium for an electrolyte system, use:

```java
flash.setMaxNumberOfPhases(1);  // on the flash, not the system
```

### Charge Balance for Ionic Species

When manually adding ionic species, ensure the feed is charge-balanced:

$$
\sum_i z_i \cdot n_i = 0
$$

where $z_i$ is the ionic charge and $n_i$ is the moles of species $i$. For the CO2/water system:

```
HCO3- (z=-1):  1e-10
H3O+  (z=+1):  4e-10    = 1e-10 + 1e-10 + 2*1e-10
OH-   (z=-1):  1e-10
CO3-- (z=-2):  1e-10
```

The formula matrix automatically adds a charge balance row for systems with ionic species.

### Convergence for Multiphase Reactive Systems

Multiphase reactive flash is significantly harder than single-phase CE. The solver uses:

- **Adaptive damping** with a sliding window to balance stability and speed
- **Relaxed convergence** (tolerance 1e-4) for multiphase systems — engineering-accurate
- **Outer loop restart** to escape local minima
- **Near-convergence acceptance** for systems that asymptotically approach the solution

For complex systems (8+ components, 2+ phases, ionic reactions), convergence may take 30-60 seconds.

## Supported Systems

| System Type | EOS | Example |
|-------------|-----|---------|
| Ideal gas reactions | `SystemSrkEos` | WGS, SMR, ammonia synthesis |
| Non-ideal gas reactions | `SystemSrkEos`, `SystemPrEos` | High-pressure synthesis |
| Aqueous ionic equilibria | `SystemElectrolyteCPAstatoil` | CO2/water, acid-base |
| Multiphase VLE + CE | `SystemElectrolyteCPAstatoil` | Hydrocarbon/water/ions |

## Reactive PH Flash (Isenthalpic) and PS Flash (Isentropic)

The **reactive PH flash** finds the equilibrium temperature when pressure and total enthalpy are specified, while simultaneously satisfying chemical and phase equilibrium. Similarly, the **PS flash** does the same for an entropy specification.

### Algorithm

The solver uses a **secant + bisection hybrid** on temperature, wrapping an inner reactive TP flash (Modified RAND) at each iteration:

1. Run an initial reactive TP flash at the current temperature guess
2. Compute enthalpy residual: $Q(T) = [H(T) - H_{\text{spec}}] / |H_{\text{spec}}|$
3. First iteration: Cp-based Newton step; subsequent iterations: secant method
4. If a bracket is found and the secant step falls outside, use bisection
5. Repeat until $|Q(T)| < 10^{-8}$

The **secant method** is essential for reactive systems because it naturally captures the effective $dH/dT$ including reaction enthalpy contributions (Le Chatelier shift), unlike a Cp-only derivative that misses the enthalpy change from equilibrium composition shifts. For strongly endothermic reactions like steam methane reforming ($\Delta H \approx +206$ kJ/mol), the effective $dH/dT$ can be 3-5× larger than the sensible $C_p$ alone.

**Convergence**: Typically 5-10 outer iterations for perturbations up to ±400 K, with sub-millikelvin temperature accuracy.

### Java Example

```java
// Step 1: Get reference enthalpy from reactive TP flash
SystemInterface system = new SystemSrkEos(800.0, 10.0);
system.addComponent("CO", 0.3);
system.addComponent("water", 0.3);
system.addComponent("CO2", 0.2);
system.addComponent("hydrogen", 0.2);
system.setMixingRule("classic");

ReactiveMultiphaseTPflash tpFlash = new ReactiveMultiphaseTPflash(system);
tpFlash.run();
system.init(2);
double Hspec = system.getEnthalpy();

// Step 2: Perturb temperature and recover via PH flash
system.setTemperature(600.0); // Large perturbation
ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(system, Hspec);
phFlash.run();
// system.getTemperature() ≈ 800.0 K (recovered)
```

### PS Flash (Entropy Specification)

```java
system.setTemperature(600.0);
ReactiveMultiphasePHflash psFlash = new ReactiveMultiphasePHflash(system, 0.0);
psFlash.setEntropySpec(Sspec); // Switches to entropy mode
psFlash.run();
```

### Convenience API

```java
ThermodynamicOperations ops = new ThermodynamicOperations(system);
ops.reactivePHflash(Hspec, 0);  // PH flash
ops.reactivePSflash(Sspec);     // PS flash
```

### Python Example

```python
from neqsim import jneqsim
import jpype

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ReactiveMultiphaseTPflash = jpype.JClass(
    "neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphaseTPflash")
ReactiveMultiphasePHflash = jpype.JClass(
    "neqsim.thermodynamicoperations.flashops.reactiveflash.ReactiveMultiphasePHflash")

system = SystemSrkEos(800.0, 10.0)
system.addComponent("CO", 0.3)
system.addComponent("water", 0.3)
system.addComponent("CO2", 0.2)
system.addComponent("hydrogen", 0.2)
system.setMixingRule("classic")

# Reactive TP flash → get Hspec
tpFlash = ReactiveMultiphaseTPflash(system)
tpFlash.run()
system.init(2)
Hspec = float(system.getEnthalpy())

# Perturb and recover
system.setTemperature(600.0)
phFlash = ReactiveMultiphasePHflash(system, Hspec)
phFlash.run()
print(f"Recovered T = {float(system.getTemperature()):.4f} K")
```

### Application: Adiabatic Reactor Outlet Temperature

A key practical use is computing the adiabatic outlet temperature of a reactor. Given a feed at known conditions (unreacted), the PH flash finds the temperature at which the equilibrium composition has the same total enthalpy:

```java
// Compute feed enthalpy (non-reactive flash)
ThermodynamicOperations ops = new ThermodynamicOperations(feed);
ops.TPflash();
feed.initProperties();
double Hfeed = feed.getEnthalpy();

// Find adiabatic outlet temperature (reactive PH flash)
ReactiveMultiphasePHflash phFlash = new ReactiveMultiphasePHflash(feed, Hfeed);
phFlash.run();
double T_outlet = feed.getTemperature(); // Adiabatic reactor outlet temperature
```

## References

1. White, W.B., Johnson, S.M., Dantzig, G.B. (1958). Chemical equilibrium in complex mixtures. *J. Chem. Phys.* 28, 751-755.
2. Eriksson, G. (1971). Thermodynamic studies of high temperature equilibria. *Acta Chem. Scand.* 25, 2651-2658.
3. Smith, W.R., Missen, R.W. (1982). *Chemical Reaction Equilibrium Analysis: Theory and Algorithms*. Wiley.
4. Michelsen, M.L. (1987). Multiphase isenthalpic and isentropic flash algorithms. *Fluid Phase Equilib.* 33, 13-27.
5. Tsanas, C., Stenby, E.H., Yan, W. (2017). Calculation of multiphase chemical equilibrium by the modified RAND method. *Ind. Eng. Chem. Res.* 56, 11983-11995.
6. Paterson, D., Michelsen, M.L., Stenby, E.H., Yan, W. (2018). RAND-based formulations for isothermal multiphase flash. *SPE Journal* 23(03), 609-622.
7. Ascani, M., Sadowski, G., Held, C. (2023). Calculation of multiphase equilibria containing mixed solvents and mixed electrolytes using the heterogeneous approach. *Molecules* 28, 1768.

## Related Documentation

- [Flash Calculations Guide](flash_calculations_guide.md) — Standard TP, PH, PS flash methods
- [Electrolyte CPA Model](ElectrolyteCPAModel.md) — The EOS used for ionic systems
- [Thermodynamic Operations](thermodynamic_operations.md) — All available thermodynamic operations
- [PH Flash Examples (Notebook)](../../examples/notebooks/reactive_ph_flash_examples.ipynb) — Jupyter notebook with 8 PH/PS flash examples
