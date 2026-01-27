# Thermo System Package

Documentation for fluid system implementations in NeqSim.

## Table of Contents
- [Overview](#overview)
- [System Hierarchy](#system-hierarchy)
- [Cubic Equations of State](#cubic-equations-of-state)
- [Association Equations](#association-equations)
- [SAFT-Based EoS](#saft-based-eos)
- [Activity Models](#activity-models)
- [Reference EoS](#reference-eos)

---

## Overview

**Location:** `neqsim.thermo.system`

The system package contains 58+ implementations of thermodynamic models, from simple ideal gas to complex associating equations of state.

---

## System Hierarchy

```
SystemInterface
└── SystemThermo (abstract base)
    ├── SystemEos (cubic EoS base)
    │   ├── SystemSrkEos
    │   ├── SystemPrEos
    │   └── ...
    ├── SystemSrkCPA (CPA base)
    │   ├── SystemSrkCPAstatoil
    │   └── ...
    └── SystemPCSAFT (SAFT base)
        └── ...
```

---

## Cubic Equations of State

### Soave-Redlich-Kwong (SRK)

```java
import neqsim.thermo.system.SystemSrkEos;

SystemSrkEos fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");
```

$$P = \frac{RT}{V-b} - \frac{a\alpha(T)}{V(V+b)}$$

### Peng-Robinson (PR)

```java
import neqsim.thermo.system.SystemPrEos;

SystemPrEos fluid = new SystemPrEos(298.15, 50.0);
fluid.addComponent("methane", 1.0);
fluid.setMixingRule("classic");
```

$$P = \frac{RT}{V-b} - \frac{a\alpha(T)}{V(V+b)+b(V-b)}$$

### Available Cubic EoS Classes

| Class | Description |
|-------|-------------|
| `SystemSrkEos` | Standard SRK |
| `SystemPrEos` | Standard PR |
| `SystemSrkMathiasCopeman` | SRK with Mathias-Copeman alpha |
| `SystemPrMathiasCopeman` | PR with Mathias-Copeman alpha |
| `SystemSrkSchwartzentruberRenon` | SRK with GE mixing |
| `SystemPrSchwartzentruberRenon` | PR with GE mixing |
| `SystemSrkTwuCoon` | SRK with Twu-Coon alpha |
| `SystemPrTwuCoon` | PR with Twu-Coon alpha |
| `SystemPrDanesh` | PR with Danesh modifications |

### Alpha Function Options

```java
// Standard Soave alpha
SystemSrkEos std = new SystemSrkEos(T, P);

// Mathias-Copeman alpha (better for polar)
SystemSrkMathiasCopeman mc = new SystemSrkMathiasCopeman(T, P);

// Twu-Coon alpha
SystemSrkTwuCoon tc = new SystemSrkTwuCoon(T, P);
```

---

## Association Equations

### CPA (Cubic-Plus-Association)

For systems with hydrogen bonding (water, alcohols, glycols, amines).

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;

SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(298.15, 10.0);
fluid.addComponent("water", 1.0);
fluid.addComponent("methane", 2.0);
fluid.setMixingRule(10);  // CPA mixing rule
```

### Available CPA Classes

| Class | Description |
|-------|-------------|
| `SystemSrkCPAstatoil` | SRK-CPA (Equinor parameters) |
| `SystemPrCPA` | PR-CPA |
| `SystemSrkCPA` | SRK-CPA (generic) |
| `SystemElectrolyteCPA` | CPA with electrolytes |

### CPA Mixing Rules

```java
// Standard CPA mixing rule
fluid.setMixingRule(10);

// With cross-association
fluid.setMixingRule(10);
```

---

## SAFT-Based EoS

### PC-SAFT

Statistical Associating Fluid Theory with perturbed chain.

```java
import neqsim.thermo.system.SystemPCSAFT;

SystemPCSAFT fluid = new SystemPCSAFT(298.15, 10.0);
fluid.addComponent("methane", 1.0);
fluid.addComponent("n-hexane", 0.5);
fluid.setMixingRule("classic");
```

### ePCSAFT (Electrolyte PC-SAFT)

```java
import neqsim.thermo.system.SystemElectrolytePCSAFT;

SystemElectrolytePCSAFT brine = new SystemElectrolytePCSAFT(298.15, 1.0);
brine.addComponent("water", 1.0);
brine.addComponent("Na+", 0.1);
brine.addComponent("Cl-", 0.1);
```

---

## Activity Models

### NRTL

```java
import neqsim.thermo.system.SystemNRTL;

SystemNRTL liquid = new SystemNRTL(298.15, 1.0);
liquid.addComponent("ethanol", 0.5);
liquid.addComponent("water", 0.5);
```

### UNIFAC

```java
import neqsim.thermo.system.SystemUNIFAC;

SystemUNIFAC liquid = new SystemUNIFAC(298.15, 1.0);
liquid.addComponent("acetone", 0.5);
liquid.addComponent("water", 0.5);
```

### EoS/GE Combinations

```java
// SRK with UNIFAC for liquid
SystemSrkSchwartzentruberRenon fluid = new SystemSrkSchwartzentruberRenon(T, P);
```

---

## Reference EoS

### GERG-2008

High-accuracy reference equation for natural gas.

```java
import neqsim.thermo.system.SystemGERG2008;

SystemGERG2008 gas = new SystemGERG2008(288.15, 50.0);
gas.addComponent("nitrogen", 0.02);
gas.addComponent("CO2", 0.01);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.04);
```

**Accuracy:** 
- Density: ±0.1% (normal conditions)
- Speed of sound: ±0.1%

### UMR-PRU

Universal Mixing Rule with PR EoS.

```java
import neqsim.thermo.system.SystemUMRPRU;

SystemUMRPRU lng = new SystemUMRPRU(110.0, 1.0);
lng.addComponent("methane", 0.92);
lng.addComponent("ethane", 0.05);
lng.addComponent("propane", 0.03);
```

---

## Creating Custom Systems

### Temperature and Pressure

```java
// Create at specific T, P
SystemSrkEos fluid = new SystemSrkEos(300.0, 10.0);  // K, bar

// Change conditions later
fluid.setTemperature(350.0);
fluid.setPressure(50.0);

// With units
fluid.setTemperature(25.0, "C");
fluid.setPressure(50.0, "bara");
```

### Components

```java
// By name and moles
fluid.addComponent("methane", 100.0);

// By index
fluid.addComponent(0, 100.0);

// TBP fraction (for plus fractions)
fluid.addTBPfraction("C7+", 10.0, 150.0, 0.78);  // name, moles, MW, SG

// Set mole fractions directly
double[] z = {0.85, 0.10, 0.05};
fluid.setMolarComposition(z);
```

### Phase Specifications

```java
// Force number of phases
fluid.setNumberOfPhases(2);

// Specify phase types
fluid.setPhaseType(0, "gas");
fluid.setPhaseType(1, "oil");

// Allow solid phases
fluid.setSolidPhaseCheck(true);
```

---

## System Methods

### Initialization

```java
// Initialize thermodynamic properties
fluid.init(0);  // Molar volumes only
fluid.init(1);  // Plus fugacity coefficients
fluid.init(2);  // Plus all derivatives
fluid.init(3);  // Plus second derivatives
```

### Property Access

```java
// Bulk properties
double rho = fluid.getDensity("kg/m3");
double MW = fluid.getMolarMass("kg/mol");
double H = fluid.getEnthalpy("kJ/kg");
double S = fluid.getEntropy("kJ/kgK");
double Cp = fluid.getCp("J/molK");
double Cv = fluid.getCv("J/molK");
double Z = fluid.getZ();
double kappa = fluid.getKappa();  // Cp/Cv

// Transport properties
double visc = fluid.getViscosity("cP");
double k = fluid.getThermalConductivity("W/mK");

// Phase properties
double gasZ = fluid.getGasPhase().getZ();
double liqRho = fluid.getLiquidPhase().getDensity("kg/m3");
```

### Cloning

```java
// Deep copy
SystemInterface copy = fluid.clone();

// Modify copy without affecting original
copy.setTemperature(400.0);
```

---

## Related Documentation

- [Phase Package](../phase/README.md) - Phase modeling
- [Component Package](../component/README.md) - Component properties
- [Mixing Rules](../mixingrule/README.md) - Binary interactions
- [Thermo Package](../README.md) - Package overview
