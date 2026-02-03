---
title: "Thermodynamic Operations Package"
description: "The `thermodynamicoperations` package provides flash calculations, phase envelope construction, and chemical equilibrium solvers."
---

# Thermodynamic Operations Package

The `thermodynamicoperations` package provides flash calculations, phase envelope construction, and chemical equilibrium solvers.

## Table of Contents
- [Overview](#overview)
- [Package Structure](#package-structure)
- [Flash Calculations](#flash-calculations)
- [Phase Envelope Operations](#phase-envelope-operations)
- [Property Generators](#property-generators)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.thermodynamicoperations`

**Purpose:**
- Perform phase equilibrium calculations (flash operations)
- Calculate phase envelopes and critical points
- Generate property tables
- Handle chemical equilibrium

**Main Entry Point:** `ThermodynamicOperations`

```java
import neqsim.thermodynamicoperations.ThermodynamicOperations;

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();  // Temperature-Pressure flash
```

---

## Package Structure

```
thermodynamicoperations/
├── ThermodynamicOperations.java     # Main facade class
├── BaseOperation.java               # Base class for operations
├── OperationInterface.java          # Operation interface
│
├── flashops/                        # Flash calculations
│   ├── TPflash.java                 # Temperature-Pressure flash
│   ├── PHflash.java                 # Pressure-Enthalpy flash
│   ├── PSFlash.java                 # Pressure-Entropy flash
│   ├── TVflash.java                 # Temperature-Volume flash
│   ├── TSFlash.java                 # Temperature-Entropy flash (Q-function)
│   ├── THflash.java                 # Temperature-Enthalpy flash (Q-function)
│   ├── TUflash.java                 # Temperature-Internal Energy flash (Q-function)
│   ├── PVflash.java                 # Pressure-Volume flash (Q-function)
│   ├── VUflash.java                 # Volume-Internal Energy flash (Q-function)
│   ├── VHflash.java                 # Volume-Enthalpy flash (Q-function)
│   ├── VSflash.java                 # Volume-Entropy flash (Q-function)
│   ├── PUflash.java                 # Pressure-Internal Energy flash
│   ├── TVfractionFlash.java         # Temperature-Vapor fraction flash
│   ├── dTPflash.java                # Dual temperature flash
│   ├── TPmultiflash.java            # Multiphase TP flash
│   ├── SolidFlash.java              # Flash with solids
│   ├── CriticalPointFlash.java      # Critical point calculation
│   ├── QfuncFlash.java              # Base class for Q-function flashes
│   ├── RachfordRice.java            # Rachford-Rice solver
│   └── saturationops/               # Saturation calculations
│       ├── BubblePointPressureFlash.java
│       ├── BubblePointTemperatureFlash.java
│       ├── DewPointPressureFlash.java
│       ├── DewPointTemperatureFlash.java
│       ├── WaterDewPointFlash.java
│       └── HydrateEquilibrium.java
│
├── phaseenvelopeops/                # Phase envelope calculations
│   ├── multicomponentenvelopeops/
│   │   ├── PTPhaseEnvelope.java
│   │   └── PHPhaseEnvelope.java
│   └── reactivecurves/
│       └── ReactivePhaseEnvelope.java
│
├── chemicalequilibrium/             # Chemical equilibrium
│   └── ChemicalEquilibrium.java
│
└── propertygenerator/               # Property tables
    └── OLGApropertyTableGenerator.java
```

---

## Flash Calculations

### Flash Types

| Flash Type | Method | Known Variables | Solved Variables |
|------------|--------|-----------------|------------------|
| TP | `TPflash()` | T, P | Phase amounts, compositions |
| PH | `PHflash(H)` | P, H | T, phase amounts, compositions |
| PS | `PSflash(S)` | P, S | T, phase amounts, compositions |
| PU | `PUflash(U)` | P, U | T, phase amounts, compositions |
| TV | `TVflash(V)` | T, V | P, phase amounts, compositions |
| TS | `TSflash(S)` | T, S | P, phase amounts, compositions |
| TH | `THflash(H)` | T, H | P, phase amounts, compositions |
| TU | `TUflash(U)` | T, U | P, phase amounts, compositions |
| PV | `PVflash(V)` | P, V | T, phase amounts, compositions |
| VU | `VUflash(V, U)` | V, U | T, P, phase amounts |
| VH | `VHflash(V, H)` | V, H | T, P, phase amounts |
| VS | `VSflash(V, S)` | V, S | T, P, phase amounts |

### TP Flash

The most common flash calculation - given temperature and pressure, find equilibrium phases.

```java
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.1);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Vapor fraction: " + fluid.getBeta());
```

### PH Flash (Adiabatic)

Find temperature given pressure and enthalpy - essential for adiabatic processes.

```java
// Initial state
double H = fluid.getEnthalpy();

// Change pressure
fluid.setPressure(20.0);

// Find new temperature at same enthalpy
ops.PHflash(H);

System.out.println("New temperature: " + fluid.getTemperature("C") + " °C");
```

### PS Flash (Isentropic)

Find temperature given pressure and entropy - for isentropic compression/expansion.

```java
double S = fluid.getEntropy();
fluid.setPressure(100.0);
ops.PSflash(S);

System.out.println("Isentropic temperature: " + fluid.getTemperature("C") + " °C");
```

### VU Flash (Dynamic)

For dynamic simulations - given volume and internal energy, find T and P.

```java
double V = fluid.getVolume();
double U = fluid.getInternalEnergy();

// Simulate heat addition
double Unew = U + 10000.0;  // Add 10 kJ

ops.VUflash(V, Unew);
System.out.println("New T: " + fluid.getTemperature("C") + " °C");
System.out.println("New P: " + fluid.getPressure() + " bar");
```

### TV Fraction Flash

Find pressure at given vapor/liquid fraction.

```java
// Find pressure where vapor fraction = 0.5
ops.TVfractionFlash(0.5);
System.out.println("Pressure at 50% vapor: " + fluid.getPressure() + " bar");
```

---

## Saturation Operations

### Bubble Point

```java
// Bubble point pressure at current temperature
ops.bubblePointPressureFlash(false);
double Pbub = fluid.getPressure();

// Bubble point temperature at current pressure  
ops.bubblePointTemperatureFlash();
double Tbub = fluid.getTemperature();
```

### Dew Point

```java
// Dew point pressure at current temperature
ops.dewPointPressureFlash();
double Pdew = fluid.getPressure();

// Dew point temperature at current pressure
ops.dewPointTemperatureFlash();
double Tdew = fluid.getTemperature();
```

### Water Dew Point

```java
// Water dew point temperature at given pressure
ops.waterDewPointTemperatureFlash();
double TwaterDew = fluid.getTemperature();
```

### Hydrate Equilibrium

> **📚 See [Hydrate Flash Operations](hydrate_flash_operations) for complete documentation**

```java
// Hydrate formation temperature
ops.hydrateFormationTemperature();
double Thyd = fluid.getTemperature();

// Hydrate formation pressure
fluid.setTemperature(278.15);
ops.hydrateFormationPressure();
double Phyd = fluid.getPressure();

// Hydrate TPflash (phase equilibrium with hydrate)
ops.hydrateTPflash();

// Gas-Hydrate equilibrium (no aqueous phase)
ops.gasHydrateTPflash();
```

---

## Phase Envelope Operations

### PT Phase Envelope

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcPTphaseEnvelope();


// Get results
double[][] envelope = ops.getOperation().get2DData();
// envelope[0] = temperatures (K)
// envelope[1] = pressures (bar)

// Get cricondenbar and cricondentherm
double cricondenbar = ops.getOperation().getCricondenbar();
double cricondentherm = ops.getOperation().getCricondentherm();
```

### PH Phase Envelope

```java
ops.calcPHenveloppe();
double[][] phEnvelope = ops.getOperation().get2DData();
```

---

## Property Generators

### OLGA Property Tables

Generate property tables for multiphase flow simulators.

```java
import neqsim.thermodynamicoperations.propertygenerator.OLGApropertyTableGenerator;

OLGApropertyTableGenerator generator = new OLGApropertyTableGenerator(fluid);
generator.setFileName("fluid_properties");

// Set ranges
generator.setPressureRange(1.0, 200.0, 50);   // 1-200 bar, 50 points
generator.setTemperatureRange(250.0, 400.0, 30); // 250-400 K, 30 points
generator.setWaterCutRange(0.0, 1.0, 5);      // 0-100% water cut, 5 points

generator.run();
```

---

## Chemical Equilibrium

For reactive systems, calculate equilibrium composition considering reactions.

```java
// Set up reactive system
SystemInterface reactive = new SystemSrkEos(700.0, 10.0);
reactive.addComponent("methane", 1.0);
reactive.addComponent("water", 2.0);
reactive.addComponent("CO2", 0.0);
reactive.addComponent("hydrogen", 0.0);

// Enable chemical reactions
reactive.setChemicalReactions(true);

ThermodynamicOperations ops = new ThermodynamicOperations(reactive);
ops.calcChemicalEquilibrium();

// Get equilibrium composition
for (int i = 0; i < reactive.getNumberOfComponents(); i++) {
    System.out.println(reactive.getComponent(i).getName() + 
        ": " + reactive.getComponent(i).getx() + " mol/mol");
}
```

---

## Multi-Phase Flash

Handle systems with multiple liquid phases, solids, or hydrates.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5, 100.0);
fluid.addComponent("methane", 0.90);
fluid.addComponent("water", 0.10);
fluid.setMixingRule("CPA_Statoil");
fluid.setMultiPhaseCheck(true);  // Enable multi-phase check

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

System.out.println("Number of phases: " + fluid.getNumberOfPhases());
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    System.out.println("Phase " + i + ": " + fluid.getPhase(i).getPhaseTypeName());
}
```

### Solid Flash (Wax, Ice)

```java
fluid.setSolidPhaseCheck("wax");
ops.TPsolidflash();
```

---

## Advanced Options

### Calculation Identifiers

Track calculations with UUIDs for parallel processing.

```java
UUID calcId = UUID.randomUUID();
ops.TPflash(calcId);
```

### Flash Settings

```java
// Set maximum iterations
ops.setMaxIterations(100);

// Set convergence tolerance
ops.setTolerance(1e-10);
```

---

## Best Practices

1. **Always set mixing rule** before flash calculations
2. **Initialize fluid** with `createDatabase(true)` for new components
3. **Use multi-phase check** when expecting multiple liquid phases
4. **Check convergence** after flash - verify `getNumberOfPhases()` makes sense
5. **Handle exceptions** for failed convergence

---

## Related Documentation

- [Flash Calculations Guide](../thermo/flash_calculations_guide) - Detailed flash examples
- [Fluid Creation Guide](../thermo/fluid_creation_guide) - Setting up fluids
- [Mathematical Models](../thermo/mathematical_models) - EoS formulations
