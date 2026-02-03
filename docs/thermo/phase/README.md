---
title: "Phase Package"
description: "Documentation for phase modeling in NeqSim."
---

# Phase Package

Documentation for phase modeling in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Phase Types](#phase-types)
- [Phase Interface](#phase-interface)
- [Gas Phase](#gas-phase)
- [Liquid Phase](#liquid-phase)
- [Aqueous Phase](#aqueous-phase)
- [Solid Phases](#solid-phases)

---

## Overview

**Location:** `neqsim.thermo.phase`

The phase package contains 62+ classes for modeling different phase types. Each phase type inherits from a base class that implements the PhaseInterface.

---

## Phase Types

### Available Phase Classes

| Category | Classes |
|----------|---------|
| Gas | `PhaseGas`, `PhaseGasEos`, `PhaseGasCPA`, `PhaseGasPCSAFT` |
| Liquid | `PhaseLiquid`, `PhaseLiquidEos`, `PhaseLiquidCPA`, `PhaseLiquidPCSAFT` |
| Aqueous | `PhaseAqueous`, `PhaseAqueousEos` |
| Solid | `PhaseSolid`, `PhaseSolidComplex`, `PhaseHydrate`, `PhaseWax` |

### Phase Hierarchy

```
PhaseInterface
└── Phase (abstract base)
    ├── PhaseEos (EoS phases)
    │   ├── PhaseGasEos
    │   ├── PhaseLiquidEos
    │   └── ...
    ├── PhaseCPA (CPA phases)
    │   ├── PhaseGasCPA
    │   ├── PhaseLiquidCPA
    │   └── ...
    └── PhaseSolid
        ├── PhaseHydrate
        └── PhaseWax
```

---

## Phase Interface

### Common Methods

```java
PhaseInterface phase = fluid.getPhase(0);

// Phase fraction
double beta = phase.getBeta();  // Mole fraction of total
double betaV = phase.getBetaV(); // Volume fraction

// Thermodynamic properties
double T = phase.getTemperature();
double P = phase.getPressure();
double V = phase.getMolarVolume();
double rho = phase.getDensity("kg/m3");
double Z = phase.getZ();

// Energetic properties
double H = phase.getEnthalpy("kJ/kg");
double S = phase.getEntropy("kJ/kgK");
double G = phase.getGibbsEnergy();
double U = phase.getInternalEnergy();
double A = phase.getHelmholtzEnergy();

// Heat capacities
double Cp = phase.getCp("J/molK");
double Cv = phase.getCv("J/molK");

// Transport properties
double visc = phase.getViscosity("cP");
double k = phase.getThermalConductivity("W/mK");
double D = phase.getDiffusionCoefficient("m2/s");

// Speed of sound
double u = phase.getSoundSpeed("m/s");
```

### Component Access

```java
// Get component in phase
ComponentInterface comp = phase.getComponent("methane");

// Mole fraction in phase
double x = comp.getx();

// Fugacity
double f = comp.getFugacity();
double phi = comp.getFugacityCoefficient();
```

---

## Gas Phase

### Properties

```java
PhaseInterface gas = fluid.getGasPhase();

// Compressibility
double Z = gas.getZ();

// Density
double rhoGas = gas.getDensity("kg/m3");

// Viscosity
double muGas = gas.getViscosity("cP");

// Specific volume
double Vm = gas.getMolarVolume();
```

### Gas Phase Types

```java
// Standard EoS gas phase
PhaseGasEos gasEos = (PhaseGasEos) fluid.getGasPhase();

// CPA gas phase (for associating components)
PhaseGasCPA gasCPA = (PhaseGasCPA) fluid.getGasPhase();

// PC-SAFT gas phase
PhaseGasPCSAFT gasSAFT = (PhaseGasPCSAFT) fluid.getGasPhase();
```

---

## Liquid Phase

### Oil Phase

```java
PhaseInterface oil = fluid.getLiquidPhase();

// Liquid density
double rhoLiq = oil.getDensity("kg/m3");

// API gravity
double API = 141.5 / (oil.getDensity("g/cm3") / 0.999) - 131.5;

// Viscosity
double muOil = oil.getViscosity("cP");
```

### Multiple Liquid Phases

```java
// When system has multiple liquid phases
if (fluid.getNumberOfLiquidPhases() > 1) {
    PhaseInterface oil = fluid.getPhase("oil");
    PhaseInterface aqueous = fluid.getPhase("aqueous");
}
```

---

## Aqueous Phase

For water-rich liquid phases.

```java
PhaseInterface aqueous = fluid.getPhase("aqueous");

// Water activity
double aW = aqueous.getComponent("water").getActivity();

// pH (if electrolytes present)
double pH = aqueous.getpH();

// Ionic strength
double I = aqueous.getIonicStrength();
```

---

## Solid Phases

### General Solid

```java
// Enable solid phase check
fluid.setSolidPhaseCheck(true);

// Get solid phase if formed
if (fluid.hasSolidPhase()) {
    PhaseInterface solid = fluid.getSolidPhase();
    double solidFraction = solid.getBeta();
}
```

### Hydrate Phase

```java
// Hydrate formation check
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

if (fluid.hasHydrate()) {
    PhaseInterface hydrate = fluid.getPhase("hydrate");
    double hydrateTemp = fluid.getHydrateTemperature();
}
```

### Wax Phase

```java
// Wax formation check
fluid.setWaxCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

if (fluid.hasWax()) {
    PhaseInterface wax = fluid.getPhase("wax");
    double waxFraction = wax.getBeta();
}
```

### Asphaltene Phases

NeqSim supports two approaches for modeling asphaltene precipitation:

1. **Solid Asphaltene (`PhaseType.ASPHALTENE`)**: Traditional approach where precipitated asphaltene is treated as a solid phase with literature-based physical properties.

2. **Liquid Asphaltene (`PhaseType.LIQUID_ASPHALTENE`)**: Pedersen's approach where asphaltene is modeled as a liquid phase using cubic EOS (SRK/PR), enabling liquid-liquid equilibrium calculations.

#### Solid Asphaltene Approach

```java
// Enable solid phase check for asphaltene
fluid.setSolidPhaseCheck("asphaltene");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Check for asphaltene phase using PhaseType
if (fluid.hasPhaseType(PhaseType.ASPHALTENE)) {
    PhaseInterface asphaltene = fluid.getPhaseOfType("asphaltene");
    double asphalteneFraction = asphaltene.getBeta();
    double density = asphaltene.getDensity("kg/m3");  // ~1150 kg/m³
    double viscosity = asphaltene.getViscosity("Pa*s"); // ~10,000 Pa·s
}
```

#### Pedersen's Liquid Asphaltene Approach

Pedersen's method treats asphaltene as a heavy liquid component using cubic EOS with estimated critical properties. This allows liquid-liquid equilibrium (LLE) calculations.

```java
import neqsim.thermo.characterization.PedersenAsphalteneCharacterization;

// Create fluid system
SystemInterface fluid = new SystemSrkEos(373.15, 50.0);
fluid.addComponent("methane", 0.30);
fluid.addComponent("n-pentane", 0.25);  // Precipitant
fluid.addComponent("n-heptane", 0.20);
fluid.addComponent("nC10", 0.15);

// Create and configure asphaltene characterization
PedersenAsphalteneCharacterization asphChar = new PedersenAsphalteneCharacterization();
asphChar.setAsphalteneMW(750.0);     // g/mol
asphChar.setAsphalteneDensity(1.10); // g/cm³

// Add asphaltene as pseudo-component (before mixing rule)
asphChar.addAsphalteneToSystem(fluid, 0.10);  // 10 mol%
fluid.setMixingRule("classic");

// Perform TPflash with automatic asphaltene detection
boolean hasAsphaltene = PedersenAsphalteneCharacterization.TPflash(fluid);

// Check for asphaltene-rich liquid phase
if (fluid.hasPhaseType(PhaseType.LIQUID_ASPHALTENE)) {
    PhaseInterface asphLiquid = fluid.getPhaseOfType("asphaltene liquid");
    System.out.println("Asphaltene liquid fraction: " + asphLiquid.getBeta());
}
```

#### Detecting Asphaltene-Rich Phases

```java
// Check if any phase is asphaltene-rich (works for both approaches)
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    PhaseInterface phase = fluid.getPhase(i);
    if (phase.isAsphalteneRich()) {
        System.out.println("Phase " + i + " is asphaltene-rich");
    }
}

// Using StateOfMatter helper
import neqsim.thermo.phase.StateOfMatter;
boolean isAsph = StateOfMatter.isAsphaltene(phase.getType());
```

**Asphaltene Phase Properties:**

| Property | Solid Approach | Liquid (Pedersen) | Unit |
|----------|----------------|-------------------|------|
| Density | ~1150 (literature) | EOS-calculated | kg/m³ |
| Heat Capacity (Cp) | ~0.9 (literature) | EOS-calculated | kJ/kgK |
| Thermal Conductivity | ~0.20 (literature) | EOS-calculated | W/mK |
| Viscosity | ~10,000 (literature) | EOS-calculated | Pa·s |
| Speed of Sound | ~1745 (literature) | EOS-calculated | m/s |

---

## Phase Identification

### Phase Type Detection

```java
// Get phase type
String type = phase.getPhaseTypeName();  // "gas", "oil", "aqueous", "asphaltene", etc.

// Check phase type using enum
boolean isGas = phase.getType() == PhaseType.GAS;
boolean isLiquid = phase.getType() == PhaseType.LIQUID;
boolean isAqueous = phase.getType() == PhaseType.AQUEOUS;
boolean isAsphaltene = phase.getType() == PhaseType.ASPHALTENE;
boolean isSolid = phase.getType() == PhaseType.SOLID;

// Check using string-based lookup (convenient API)
boolean hasGas = fluid.hasPhaseType("gas");
boolean hasAsphaltene = fluid.hasPhaseType("asphaltene");
```

### Available PhaseType Values

| PhaseType | Description | Value | StateOfMatter |
|-----------|-------------|-------|---------------|
| `GAS` | Gas phase | 1 | GAS |
| `LIQUID` | Generic liquid | 0 | LIQUID |
| `OIL` | Oil/hydrocarbon liquid | 2 | LIQUID |
| `AQUEOUS` | Water-rich liquid | 3 | LIQUID |
| `HYDRATE` | Gas hydrate solid | 4 | SOLID |
| `WAX` | Wax solid | 5 | SOLID |
| `SOLID` | Generic solid | 6 | SOLID |
| `SOLIDCOMPLEX` | Complex solid | 7 | SOLID |
| `ASPHALTENE` | Asphaltene solid | 8 | SOLID |
| `LIQUID_ASPHALTENE` | Asphaltene liquid (Pedersen) | 9 | LIQUID |

### Stability Analysis

```java
// Phase stability check
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.checkStability();

boolean stable = fluid.isPhaseStable();
```

---

## Phase Properties Calculation

### EoS Properties

```java
// Fugacity coefficients
for (int i = 0; i < phase.getNumberOfComponents(); i++) {
    double phi = phase.getComponent(i).getFugacityCoefficient();
    double lnPhi = phase.getComponent(i).getLogFugacityCoefficient();
}

// Compressibility factor derivatives
double dZdT = phase.getdZdT();
double dZdP = phase.getdZdP();

// Fugacity coefficient derivatives
double dPhidT = phase.getComponent(0).getdfugdT();
double dPhidP = phase.getComponent(0).getdfugdP();
```

### Mixing Properties

```java
// Excess properties
double GE = phase.getExcessGibbsEnergy();
double HE = phase.getExcessEnthalpy();
double SE = phase.getExcessEntropy();
double VE = phase.getExcessVolume();

// Activity coefficients (for liquid)
double gamma = phase.getComponent("ethanol").getActivityCoefficient();
```

---

## Example: Phase Analysis

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemSrkEos fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-pentane", 0.05);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());

for (int p = 0; p < fluid.getNumberOfPhases(); p++) {
    PhaseInterface phase = fluid.getPhase(p);
    
    System.out.println("\nPhase " + (p + 1) + ": " + phase.getPhaseTypeName());
    System.out.println("  Mole fraction: " + phase.getBeta());
    System.out.println("  Density: " + phase.getDensity("kg/m3") + " kg/m³");
    System.out.println("  Z-factor: " + phase.getZ());
    System.out.println("  Viscosity: " + phase.getViscosity("cP") + " cP");
    
    System.out.println("  Composition:");
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
        String name = phase.getComponent(i).getName();
        double x = phase.getComponent(i).getx();
        System.out.printf("    %s: %.4f%n", name, x);
    }
}
```

---

## Related Documentation

- [System Package](../system/README) - Fluid systems
- [Component Package](../component/README) - Component properties
- [Thermo Package](../README) - Package overview
