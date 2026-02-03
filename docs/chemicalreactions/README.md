---
title: Chemical Reactions Package
description: The `chemicalreactions` package provides tools for chemical equilibrium calculations and reaction kinetics.
---

# Chemical Reactions Package

The `chemicalreactions` package provides tools for chemical equilibrium calculations and reaction kinetics.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Chemical Equilibrium](#chemical-equilibrium)
- [Reaction Kinetics](#reaction-kinetics)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.chemicalreactions`

**Purpose:**
- Calculate chemical equilibrium composition
- Model reaction kinetics
- Support reactive flash calculations
- Handle simultaneous phase and chemical equilibrium

---

## Package Structure

```
chemicalreactions/
├── ChemicalReactionOperations.java   # Main operations class
│
├── chemicalequilibrium/              # Equilibrium calculations
│   ├── ChemicalEquilibrium.java      # Base class
│   ├── ChemEq.java                   # Equilibrium solver
│   ├── LinearProgrammingChemicalEquilibrium.java
│   └── ReferencePotComparator.java
│
├── chemicalreaction/                 # Reaction definitions
│   ├── ChemicalReaction.java         # Single reaction
│   └── ChemicalReactionList.java     # Reaction set
│
└── kinetics/                         # Kinetics models
    └── Kinetics.java                 # Kinetic rate calculations
```

---

## Chemical Equilibrium

### Theory

Chemical equilibrium is achieved when the Gibbs energy is minimized:

$$\min G = \sum_i n_i \mu_i$$

Subject to element balance constraints:

$$\sum_i a_{ji} n_i = b_j \quad \text{for each element } j$$

Where:
- $n_i$ = moles of species $i$
- $\mu_i$ = chemical potential of species $i$
- $a_{ji}$ = atoms of element $j$ in species $i$
- $b_j$ = total moles of element $j$

### Basic Usage

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create reactive system
SystemInterface reactive = new SystemSrkEos(700.0, 10.0);
reactive.addComponent("methane", 1.0);
reactive.addComponent("water", 2.0);
reactive.addComponent("CO2", 0.0);
reactive.addComponent("CO", 0.0);
reactive.addComponent("hydrogen", 0.0);
reactive.setMixingRule("classic");

// Enable chemical reactions
reactive.setChemicalReactions(true);

// Perform equilibrium calculation
ThermodynamicOperations ops = new ThermodynamicOperations(reactive);
ops.calcChemicalEquilibrium();

// Display results
for (int i = 0; i < reactive.getNumberOfComponents(); i++) {
    System.out.println(reactive.getComponent(i).getName() + 
        ": " + reactive.getComponent(i).getNumberOfmable() + " mol");
}
```

---

## Supported Reactions

### Steam Reforming

```
CH₄ + H₂O ⇌ CO + 3H₂
CO + H₂O ⇌ CO₂ + H₂
```

### Combustion

```
CH₄ + 2O₂ → CO₂ + 2H₂O
C₂H₆ + 3.5O₂ → 2CO₂ + 3H₂O
```

### Acid Gas Reactions

```
CO₂ + H₂O ⇌ H₂CO₃
H₂S + H₂O ⇌ HS⁻ + H₃O⁺
NH₃ + H₂O ⇌ NH₄⁺ + OH⁻
```

### Amine Reactions

```
CO₂ + 2RNH₂ ⇌ RNHCOO⁻ + RNH₃⁺
CO₂ + RNH₂ + H₂O ⇌ RNH₃⁺ + HCO₃⁻
```

---

## ChemicalReactionOperations

### Main Class

```java
import neqsim.chemicalreactions.ChemicalReactionOperations;

ChemicalReactionOperations reactionOps = new ChemicalReactionOperations(fluid);

// Add reactions
reactionOps.addReaction("methane_reforming");
reactionOps.addReaction("water_gas_shift");

// Calculate equilibrium
reactionOps.calcChemicalEquilibrium();

// Get equilibrium constants
double Keq = reactionOps.getEquilibriumConstant("methane_reforming");
```

---

## Reaction Kinetics

For rate-limited reactions, use kinetic models.

### Kinetic Rate Expression

General rate expression:

$$r = k \cdot \prod_i C_i^{n_i}$$

Where:
- $k$ = rate constant
- $C_i$ = concentration of species $i$
- $n_i$ = reaction order with respect to species $i$

### Temperature Dependence (Arrhenius)

$$k = A \cdot \exp\left(-\frac{E_a}{RT}\right)$$

Where:
- $A$ = pre-exponential factor
- $E_a$ = activation energy
- $R$ = gas constant
- $T$ = temperature

### Usage

```java
import neqsim.chemicalreactions.kinetics.Kinetics;

Kinetics kinetics = new Kinetics(fluid);

// Set reaction parameters
kinetics.setPreExponentialFactor(1.0e10);  // 1/s
kinetics.setActivationEnergy(80000.0);      // J/mol

// Calculate rate at current conditions
double rate = kinetics.getReactionRate();
```

---

## Reactive Flash Calculations

Combine phase equilibrium with chemical equilibrium.

### TP Flash with Reactions

```java
// Set up reactive system
SystemInterface fluid = new SystemSrkEos(500.0, 20.0);
fluid.addComponent("methane", 1.0);
fluid.addComponent("oxygen", 0.5);
fluid.addComponent("CO2", 0.0);
fluid.addComponent("water", 0.0);
fluid.setMixingRule("classic");
fluid.setChemicalReactions(true);

// Reactive TP flash
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Results include both phase and chemical equilibrium
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
    System.out.println(fluid.getComponent(i).getName() + 
        ": " + fluid.getComponent(i).getz() + " mol/mol");
}
```

---

## Gibbs Energy Minimization

### Linear Programming Method

For complex systems, use linear programming approach.

```java
import neqsim.chemicalreactions.chemicalequilibrium.LinearProgrammingChemicalEquilibrium;

LinearProgrammingChemicalEquilibrium lpEquil = 
    new LinearProgrammingChemicalEquilibrium(fluid);
lpEquil.solve();

// Get equilibrium composition
double[] composition = lpEquil.getEquilibriumComposition();
```

---

## Database Integration

Chemical reactions and their parameters are stored in the database.

### Reaction Database

| Field | Description |
|-------|-------------|
| name | Reaction identifier |
| reactants | Reactant species |
| products | Product species |
| stoichiometry | Stoichiometric coefficients |
| deltaH | Enthalpy of reaction |
| deltaG | Gibbs energy of reaction |
| Keq_A, Keq_B, Keq_C | Equilibrium constant correlation |

### Loading Reactions

```java
// Load reactions from database
fluid.createChemicalReactions(true);

// Or specify specific reactions
fluid.addChemicalReaction("steam_reforming");
fluid.addChemicalReaction("water_gas_shift");
```

---

## Example: Ammonia Synthesis

```java
// Ammonia synthesis: N₂ + 3H₂ ⇌ 2NH₃
SystemInterface syngas = new SystemSrkEos(673.15, 200.0);  // 400°C, 200 bar
syngas.addComponent("nitrogen", 1.0);
syngas.addComponent("hydrogen", 3.0);
syngas.addComponent("ammonia", 0.0);
syngas.setMixingRule("classic");
syngas.setChemicalReactions(true);

ThermodynamicOperations ops = new ThermodynamicOperations(syngas);
ops.calcChemicalEquilibrium();

double NH3fraction = syngas.getComponent("ammonia").getz();
double conversion = 2 * NH3fraction / 
    (syngas.getComponent("nitrogen").getz() + NH3fraction);

System.out.println("NH₃ mole fraction: " + NH3fraction);
System.out.println("N₂ conversion: " + (conversion * 100) + "%");
```

---

## Example: CO₂ Capture with Amine

```java
// CO₂ absorption in MEA solution
SystemInterface solution = new SystemElectrolyteCPA(313.15, 1.01325);
solution.addComponent("CO2", 0.05);
solution.addComponent("water", 0.75);
solution.addComponent("MEA", 0.20);
solution.setMixingRule("CPA_Statoil");
solution.setChemicalReactions(true);

// Flash with reactions
ThermodynamicOperations ops = new ThermodynamicOperations(solution);
ops.TPflash();

// Get CO₂ loading
double CO2loading = solution.getComponent("CO2").getx() / 
    solution.getComponent("MEA").getx();
System.out.println("CO₂ loading: " + CO2loading + " mol CO₂/mol MEA");
```

---

## Best Practices

1. **Initialize products** with small but non-zero amounts
2. **Check element balance** before and after equilibrium
3. **Use appropriate thermodynamic model** (electrolyte models for ionic reactions)
4. **Verify equilibrium constants** against literature
5. **Consider kinetic limitations** for slow reactions

---

## Limitations

- Not all reactions are in the database
- Custom reactions require database extension
- Some complex reaction mechanisms not supported
- High-temperature kinetics may need external data

---

## Related Documentation

- [Thermodynamic Operations](../thermodynamicoperations/README) - Flash calculations
- [Fluid Creation Guide](../thermo/fluid_creation_guide) - Creating reactive systems
- [Electrolyte Models](../thermo/README) - Electrolyte CPA for ionic reactions
