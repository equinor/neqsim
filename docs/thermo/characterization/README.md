# Characterization Package

Documentation for plus fraction characterization in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Plus Fraction Methods](#plus-fraction-methods)
- [Characterization Approaches](#characterization-approaches)
- [TBP Methods](#tbp-methods)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.thermo.characterization`

The characterization package handles petroleum plus fraction characterization:
- Converting C7+ (or other plus fractions) into pseudo-components
- Estimating critical properties from correlations
- Splitting heavy ends into discrete pseudo-components

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
