---
title: "Characterization Package"
description: "Documentation for plus fraction and asphaltene characterization in NeqSim."
---

# Characterization Package

Documentation for plus fraction and asphaltene characterization in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Plus Fraction Methods](#plus-fraction-methods)
- [Characterization Approaches](#characterization-approaches)
- [Lumping Configuration](#lumping-configuration)
- [Asphaltene Characterization](#asphaltene-characterization)
- [TBP Methods](#tbp-methods)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.thermo.characterization`

The characterization package handles petroleum plus fraction and asphaltene characterization:
- Converting C7+ (or other plus fractions) into pseudo-components
- Estimating critical properties from correlations
- Splitting heavy ends into discrete pseudo-components
- Characterizing asphaltene components for precipitation modeling

---

## Plus Fraction Methods

### Adding Plus Fractions

```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(373.15, 100.0);

// Light components
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-butane", 0.05);

// C7+ as single pseudo-component
fluid.addTBPfraction("C7+", 0.07, 150.0, 0.78);  // name, moles, MW, SG
```

### Multiple Plus Fractions

```java
// Split C7+ into multiple fractions
fluid.addTBPfraction("C7", 0.02, 96.0, 0.727);
fluid.addTBPfraction("C8", 0.015, 107.0, 0.749);
fluid.addTBPfraction("C9", 0.01, 121.0, 0.768);
fluid.addTBPfraction("C10+", 0.025, 180.0, 0.82);
```

---

## Characterization Approaches

### Pedersen Method

```java
import neqsim.thermo.characterization.PedersenCharacterization;

// Characterize using Pedersen correlations
PedersenCharacterization charPedersen = new PedersenCharacterization(fluid);
charPedersen.characterize();
```

### Whitson Gamma Distribution

```java
import neqsim.thermo.characterization.WhitsonCharacterization;

// Characterize using Whitson gamma distribution
WhitsonCharacterization charWhitson = new WhitsonCharacterization(fluid);
charWhitson.setAlpha(1.0);  // Shape parameter
charWhitson.characterize();
```

---

## TBP Methods

### Adding TBP Fractions

```java
// addTBPfraction(name, moles, MW, specificGravity)
fluid.addTBPfraction("C7", moles, 96.0, 0.727);

// addPlusFraction with characterization
fluid.addPlusFraction("C20+", moles, 400.0, 0.90);
```

### Property Estimation

For pseudo-components, critical properties are estimated using correlations:

| Correlation | Properties Estimated |
|-------------|---------------------|
| Twu | Tc, Pc, omega from MW, SG |
| Lee-Kesler | Tc, Pc, omega from Tb, SG |
| Riazi-Daubert | Tb from MW, SG |
| Pedersen | Tc, Pc, omega for petroleum |

---

## Lumping Configuration

After plus fraction splitting, lumping reduces the number of pseudo-components for computational efficiency. NeqSim provides a fluent API for clear, explicit configuration.

### Fluent API (Recommended)

```java
// PVTlumpingModel: Preserve C6-C9, lump C10+ into 5 groups
fluid.getCharacterization().configureLumping()
    .model("PVTlumpingModel")
    .plusFractionGroups(5)
    .build();

// Standard model: Lump all from C6 into 6 pseudo-components
fluid.getCharacterization().configureLumping()
    .model("standard")
    .totalPseudoComponents(6)
    .build();

// Custom boundaries to match PVT lab report
fluid.getCharacterization().configureLumping()
    .customBoundaries(6, 7, 10, 15, 20)  // C6, C7-C9, C10-C14, C15-C19, C20+
    .build();

// No lumping: keep all SCN components
fluid.getCharacterization().configureLumping()
    .noLumping()
    .build();
```

### Lumping Models Comparison

| Model | Behavior | Use Case |
|-------|----------|----------|
| `PVTlumpingModel` | Preserves TBP fractions (C6-C9), lumps only C10+ | Standard PVT matching |
| `standard` | Lumps all heavy fractions from C6 | Minimal components for fast simulation |
| `no lumping` | Keeps all individual SCN components | Detailed compositional studies |

### Quick Reference

| I want to... | Fluent API |
|--------------|------------|
| Keep C6-C9 separate, lump C10+ into N groups | `.model("PVTlumpingModel").plusFractionGroups(N)` |
| Get exactly N total pseudo-components | `.model("standard").totalPseudoComponents(N)` |
| Match specific PVT lab groupings | `.customBoundaries(6, 10, 20)` |
| Keep all SCN components | `.noLumping()` |

For complete mathematical details, see [Fluid Characterization Mathematics](../../pvtsimulation/fluid_characterization_mathematics.md#lumping-methods).

---

## Asphaltene Characterization

### Pedersen's Asphaltene Method

The `PedersenAsphalteneCharacterization` class implements Pedersen's approach for treating asphaltene as a heavy liquid pseudo-component. This enables liquid-liquid equilibrium (LLE) calculations for asphaltene precipitation.

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid system
SystemInterface fluid = new SystemSrkEos(373.15, 50.0);
fluid.addComponent("methane", 0.40);
fluid.addComponent("n-pentane", 0.25);
fluid.addComponent("n-heptane", 0.20);
fluid.addComponent("nC10", 0.10);

// Create and configure asphaltene characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(750.0);     // Molecular weight g/mol
asphChar.setAsphalteneDensity(1.10); // Density g/cm³

// Add asphaltene pseudo-component (BEFORE setting mixing rule)
asphChar.addAsphalteneToSystem(fluid, 0.05);  // 5 mol% asphaltene

// Set mixing rule (AFTER adding all components)
fluid.setMixingRule("classic");

// Print estimated critical properties
System.out.println(asphChar.toString());
```

### Critical Property Correlations

Pedersen's method estimates critical properties from molecular weight (MW) and liquid density (ρ):

| Property | Correlation |
|----------|-------------|
| Critical Temperature (Tc) | f(MW, ρ) |
| Critical Pressure (Pc) | f(MW, ρ) |
| Acentric Factor (ω) | f(MW, ρ) |
| Normal Boiling Point (Tb) | f(MW, ρ) |

Typical values for asphaltene (MW=750 g/mol, ρ=1.10 g/cm³):

| Property | Value | Unit |
|----------|-------|------|
| Tc | 996 | K |
| Pc | 16.3 | bar |
| ω | 0.925 | - |
| Tb | 838 | K |

### TPflash with Automatic Asphaltene Detection

The class provides static methods for TPflash with automatic detection of asphaltene-rich phases:

```java
// Static TPflash - marks asphaltene-rich liquid phases as LIQUID_ASPHALTENE
boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(fluid);

// With explicit T,P specification
boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(fluid, 373.15, 50.0);

// Check result
if (hasAsphaltene) {
    System.out.println("Asphaltene-rich liquid phase detected");
    fluid.prettyPrint();  // Shows "ASPHALTENE LIQUID" column
}
```

### Asphaltene Detection Criteria

A liquid phase is marked as `PhaseType.LIQUID_ASPHALTENE` when:
- The phase contains an "Asphaltene" component, AND
- The asphaltene mole fraction in that phase exceeds 0.5 (50%)

---

## Examples

### Example 1: Natural Gas with C7+

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos gas = new SystemSrkEos(373.15, 100.0);

// Wellstream composition
gas.addComponent("nitrogen", 0.015);
gas.addComponent("CO2", 0.020);
gas.addComponent("methane", 0.750);
gas.addComponent("ethane", 0.080);
gas.addComponent("propane", 0.045);
gas.addComponent("i-butane", 0.012);
gas.addComponent("n-butane", 0.020);
gas.addComponent("i-pentane", 0.008);
gas.addComponent("n-pentane", 0.010);
gas.addComponent("n-hexane", 0.015);

// C7+ fraction
gas.addTBPfraction("C7+", 0.025, 145.0, 0.78);

gas.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

System.out.println("Gas fraction: " + gas.getGasPhase().getBeta());
System.out.println("C7+ in gas: " + gas.getGasPhase().getComponent("C7+_PC").getx());
```

### Example 2: Oil Characterization

```java
// Black oil with detailed C7+ split
SystemSrkEos oil = new SystemSrkEos(350.0, 50.0);

oil.addComponent("methane", 0.40);
oil.addComponent("ethane", 0.08);
oil.addComponent("propane", 0.06);
oil.addComponent("n-butane", 0.04);
oil.addComponent("n-pentane", 0.03);
oil.addComponent("n-hexane", 0.03);

// Detailed C7+ split
oil.addTBPfraction("C7", 0.05, 96.0, 0.727);
oil.addTBPfraction("C8", 0.05, 107.0, 0.749);
oil.addTBPfraction("C9", 0.04, 121.0, 0.768);
oil.addTBPfraction("C10", 0.04, 134.0, 0.782);
oil.addTBPfraction("C11", 0.03, 147.0, 0.793);
oil.addTBPfraction("C12+", 0.15, 250.0, 0.85);

oil.setMixingRule("classic");
```

---

## Related Documentation

- [PVT Fluid Characterization](../pvt_fluid_characterization.md) - Detailed characterization guide
- [Fluid Creation Guide](../fluid_creation_guide.md) - Creating fluids
- [Thermo Package](../README.md) - Package overview
