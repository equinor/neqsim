---
title: "Hydrate Flash Operations in NeqSim"
description: "This document provides comprehensive documentation for hydrate phase equilibrium flash calculations in NeqSim."
---

# Hydrate Flash Operations in NeqSim

This document provides comprehensive documentation for hydrate phase equilibrium flash calculations in NeqSim.

## Table of Contents

- [Overview](#overview)
- [Hydrate Flash Types](#hydrate-flash-types)
  - [Hydrate TPflash](#hydrate-tpflash)
  - [Gas-Hydrate TPflash](#gas-hydrate-tpflash)
  - [Hydrate Formation Temperature](#hydrate-formation-temperature)
  - [Hydrate Formation Pressure](#hydrate-formation-pressure)
  - [Hydrate Inhibitor Calculations](#hydrate-inhibitor-calculations)
  - [Hydrate Equilibrium Line](#hydrate-equilibrium-line)
- [Multi-Phase Equilibrium](#multi-phase-equilibrium)
  - [Gas-Aqueous-Hydrate](#gas-aqueous-hydrate)
  - [Gas-Oil-Aqueous-Hydrate](#gas-oil-aqueous-hydrate)
  - [Gas-Hydrate Only](#gas-hydrate-only-no-aqueous)
- [API Reference](#api-reference)
- [Usage Examples](#usage-examples)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

---

## Overview

NeqSim provides specialized flash calculations for systems containing gas hydrates. These operations extend the standard thermodynamic operations to include hydrate phase equilibrium.

**Key Classes:**
- `TPHydrateFlash` - TP flash with hydrate phase equilibrium
- `HydrateFormationTemperatureFlash` - Calculate hydrate formation temperature
- `HydrateFormationPressureFlash` - Calculate hydrate formation pressure
- `HydrateInhibitorConcentrationFlash` - Calculate inhibitor requirements
- `HydrateEquilibriumLine` - Generate hydrate PT curve

---

## Hydrate Flash Types

### Hydrate TPflash

Performs a temperature-pressure flash calculation including hydrate phase equilibrium. This is the main method for calculating hydrate phase fraction and composition at given T and P.

**Method:** `ThermodynamicOperations.hydrateTPflash()`

**Algorithm:**
1. Perform standard TPflash (gas/liquid/aqueous equilibrium)
2. Calculate hydrate water fugacity using vdWP model
3. Compare with fluid water fugacity
4. If hydrate is stable (lower fugacity), calculate hydrate fraction
5. Update system with hydrate phase

**Example:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.04);
fluid.addComponent("water", 0.03);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateTPflash();

// Check results
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Has hydrate: " + fluid.hasHydratePhase());
if (fluid.hasHydratePhase()) {
    System.out.println("Hydrate fraction: " + fluid.getBeta(PhaseType.HYDRATE));
}
fluid.prettyPrint();
```

**Output Phases:**
| Phase | Type | Description |
|-------|------|-------------|
| 0 | GAS | Vapor phase with dissolved water |
| 1 | AQUEOUS | Water-rich phase |
| 2+ | HYDRATE | Clathrate hydrate phase |

### Gas-Hydrate TPflash

Specialized flash targeting gas-hydrate equilibrium without aqueous phase. This is useful for systems with trace water where all water can be consumed by hydrate formation.

**Method:** `ThermodynamicOperations.gasHydrateTPflash()`

**When to Use:**
- Very low water content (< 1 mol%)
- Modeling gas-hydrate direct equilibrium
- Trace water in dry gas systems

**Example:**
```java
// Dry gas with trace water
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 15.0, 250.0);
fluid.addComponent("methane", 0.9998);
fluid.addComponent("water", 0.0002);  // 200 ppm water
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.gasHydrateTPflash();

// Result: GAS + HYDRATE phases (no AQUEOUS)
boolean hasAqueous = false;
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueous = true;
    }
}
System.out.println("Has aqueous phase: " + hasAqueous);  // false
```

**Algorithm:**
1. Perform standard TPflash
2. Enable gas-hydrate-only mode
3. Calculate hydrate equilibrium from gas phase fugacity
4. Remove aqueous phase if all water consumed by hydrate
5. Redistribute phase fractions

### Hydrate Formation Temperature

Calculates the temperature at which hydrate first forms at given pressure.

**Methods:**
```java
void hydrateFormationTemperature()
void hydrateFormationTemperature(double initialGuess)
void hydrateFormationTemperature(int structure)  // 0=ice, 1=sI, 2=sII
```

**Example:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(280.0, 100.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("CO2", 0.02);
fluid.addComponent("water", 0.03);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateFormationTemperature();

System.out.println("Hydrate formation T: " + fluid.getTemperature("C") + " °C");
System.out.println("At pressure: " + fluid.getPressure("bara") + " bara");
```

### Hydrate Formation Pressure

Calculates the pressure at which hydrate first forms at given temperature.

**Method:**
```java
void hydrateFormationPressure()
void hydrateFormationPressure(int structure)
```

**Example:**
```java
fluid.setTemperature(278.15);  // 5°C
ops.hydrateFormationPressure();
System.out.println("Hydrate formation P: " + fluid.getPressure("bara") + " bara");
```

### Hydrate Inhibitor Calculations

Calculate required inhibitor concentration to prevent hydrate formation.

**Methods:**
```java
// Calculate inhibitor needed for target temperature
void hydrateInhibitorConcentration(String inhibitor, double targetTemperature)

// Set inhibitor weight fraction and calculate effect
void hydrateInhibitorConcentrationSet(String inhibitor, double wtFraction)
```

**Supported Inhibitors:**
- `"MEG"` - Monoethylene glycol
- `"TEG"` - Triethylene glycol
- `"methanol"` - Methanol
- `"ethanol"` - Ethanol

**Example:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("water", 0.10);
fluid.addComponent("MEG", 0.05);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);

// What MEG concentration prevents hydrate at 5°C?
ops.hydrateInhibitorConcentration("MEG", 273.15 + 5.0);
System.out.println("Required MEG (wt%): " + 
    fluid.getPhase("aqueous").getComponent("MEG").getwtfrac() * 100);
```

### Hydrate Equilibrium Line

Generate the complete hydrate equilibrium curve (P-T diagram).

**Class:** `HydrateEquilibriumLine`

**Example:**
```java
SystemInterface fluid = new SystemSrkCPAstatoil(280.0, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("water", 0.1);
fluid.setMixingRule(10);
fluid.setHydrateCheck(true);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcHydrateEquilibriumLine();

// Get curve data
double[][] curve = ops.getOperation().get2DData();
// curve[0] = temperatures (K)
// curve[1] = pressures (bar)
```

---

## Multi-Phase Equilibrium

### Gas-Aqueous-Hydrate

The most common scenario: gas in equilibrium with water and hydrate.

```java
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 2.0, 80.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.05);
fluid.addComponent("propane", 0.03);
fluid.addComponent("n-butane", 0.01);
fluid.addComponent("CO2", 0.01);
fluid.addComponent("water", 0.10);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateTPflash();

// Expected phases: GAS, AQUEOUS, HYDRATE
fluid.prettyPrint();
```

### Gas-Oil-Aqueous-Hydrate

Four-phase equilibrium with condensate/oil phase.

```java
// Rich gas condensate with water
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 4.0, 100.0);

// Gas components
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-butane", 0.02);
fluid.addComponent("n-pentane", 0.01);

// Oil/condensate components
fluid.addComponent("n-hexane", 0.01);
fluid.addComponent("n-heptane", 0.02);
fluid.addComponent("n-octane", 0.01);

// Water
fluid.addComponent("water", 0.10);

fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.hydrateTPflash();

// Expected phases: GAS, OIL, AQUEOUS, HYDRATE
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    System.out.println("Phase " + i + ": " + fluid.getPhase(i).getType() + 
        ", beta = " + fluid.getBeta(i));
}
```

### Gas-Hydrate Only (No Aqueous)

For systems with very low water content where hydrate consumes all water.

```java
// 500 ppm water at extreme conditions
SystemInterface fluid = new SystemSrkCPAstatoil(273.15 - 20.0, 300.0);
fluid.addComponent("methane", 0.9995);
fluid.addComponent("water", 0.0005);
fluid.setMixingRule(10);

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.gasHydrateTPflash();

// Expected phases: GAS, HYDRATE (no AQUEOUS)
boolean hasAqueous = false;
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueous = true;
    }
}
System.out.println("Has aqueous: " + hasAqueous);  // false
```

---

## API Reference

### ThermodynamicOperations Methods

| Method | Description |
|--------|-------------|
| `hydrateTPflash()` | TP flash with hydrate equilibrium |
| `hydrateTPflash(boolean checkForSolids)` | TP flash with solid check |
| `gasHydrateTPflash()` | TP flash targeting gas-hydrate equilibrium |
| `hydrateFormationTemperature()` | Calculate hydrate formation T |
| `hydrateFormationTemperature(double guess)` | With initial guess |
| `hydrateFormationTemperature(int structure)` | For specific structure |
| `hydrateFormationPressure()` | Calculate hydrate formation P |
| `hydrateFormationPressure(int structure)` | For specific structure |
| `hydrateInhibitorConcentration(String, double)` | Calculate inhibitor needed |
| `hydrateInhibitorConcentrationSet(String, double)` | Set inhibitor and calculate |
| `calcHydrateEquilibriumLine()` | Generate PT curve |

### SystemInterface Methods

| Method | Description |
|--------|-------------|
| `setHydrateCheck(boolean)` | Enable/disable hydrate phase |
| `hasHydratePhase()` | Check if hydrate exists |
| `getHydrateFraction()` | Get hydrate mole fraction |

### TPHydrateFlash Methods

| Method | Description |
|--------|-------------|
| `isHydrateFormed()` | Check if hydrate formed |
| `getHydrateFraction()` | Get hydrate beta value |
| `getStableHydrateStructure()` | Get structure (1 or 2) |
| `getCavityOccupancy(String, int, int)` | Get cavity occupancy |
| `setGasHydrateOnlyMode(boolean)` | Enable gas-hydrate mode |
| `isGasHydrateOnlyMode()` | Check if mode enabled |

---

## Usage Examples

### Complete Workflow Example

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class HydrateAnalysis {
    public static void main(String[] args) {
        // 1. Create production fluid
        SystemInterface fluid = new SystemSrkCPAstatoil(273.15 + 5.0, 100.0);
        fluid.addComponent("methane", 0.80);
        fluid.addComponent("ethane", 0.06);
        fluid.addComponent("propane", 0.04);
        fluid.addComponent("CO2", 0.02);
        fluid.addComponent("water", 0.08);
        fluid.setMixingRule(10);
        fluid.setHydrateCheck(true);
        
        ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
        
        // 2. Calculate hydrate formation temperature
        ops.hydrateFormationTemperature();
        double Thyd = fluid.getTemperature("C");
        System.out.println("Hydrate formation temperature: " + Thyd + " °C");
        
        // 3. At 5°C, check if hydrate exists
        fluid.setTemperature(273.15 + 5.0);
        ops.hydrateTPflash();
        
        if (fluid.hasHydratePhase()) {
            System.out.println("Hydrate forms at 5°C, 100 bar");
            
            // Get phase fractions
            for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
                System.out.printf("Phase %d (%s): %.4f mol%%\n", 
                    i, fluid.getPhase(i).getType(), fluid.getBeta(i) * 100);
            }
        }
        
        // 4. Calculate MEG needed to prevent hydrate
        fluid.addComponent("MEG", 0.05);  // Add MEG
        fluid.createDatabase(true);
        ops.hydrateInhibitorConcentration("MEG", 273.15 + 5.0);
        double megWt = fluid.getPhase("aqueous").getComponent("MEG").getwtfrac();
        System.out.println("Required MEG in aqueous phase: " + megWt * 100 + " wt%");
    }
}
```

### Process Integration Example

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.measurementdevice.HydrateEquilibriumTemperatureAnalyser;

// Create stream with hydrate checking
SystemInterface gas = new SystemSrkCPAstatoil(280.0, 100.0);
gas.addComponent("methane", 0.9);
gas.addComponent("water", 0.1);
gas.setMixingRule(10);
gas.setHydrateCheck(true);

Stream gasStream = new Stream("Gas Feed", gas);
gasStream.setFlowRate(100.0, "kg/hr");
gasStream.run();

// Add hydrate analyser
HydrateEquilibriumTemperatureAnalyser hydrateAnalyser = 
    new HydrateEquilibriumTemperatureAnalyser("Hydrate Monitor", gasStream);
hydrateAnalyser.run();

double hydrateT = hydrateAnalyser.getMeasuredValue("C");
System.out.println("Hydrate equilibrium temperature: " + hydrateT + " °C");
```

---

## Best Practices

### 1. Always Set Mixing Rule

For CPA-based hydrate calculations, use mixing rule 10:
```java
fluid.setMixingRule(10);
```

### 2. Enable Hydrate Check

Before hydrate calculations:
```java
fluid.setHydrateCheck(true);
```

### 3. Verify Mass Conservation

After hydrate flash, verify beta sum equals 1.0:
```java
double betaSum = 0.0;
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    betaSum += fluid.getBeta(i);
}
assert Math.abs(betaSum - 1.0) < 1e-6 : "Mass conservation violated";
```

### 4. Check Phase Types

Always verify expected phases exist:
```java
boolean hasHydrate = fluid.hasHydratePhase();
boolean hasAqueous = false;
for (int i = 0; i < fluid.getNumberOfPhases(); i++) {
    if (fluid.getPhase(i).getType() == PhaseType.AQUEOUS) {
        hasAqueous = true;
    }
}
```

### 5. Use Appropriate Flash Method

| Scenario | Method |
|----------|--------|
| Normal water content (> 1%) | `hydrateTPflash()` |
| Trace water (< 1%) | `gasHydrateTPflash()` |
| Find formation T | `hydrateFormationTemperature()` |
| Find formation P | `hydrateFormationPressure()` |

---

## Troubleshooting

### Hydrate Not Forming When Expected

1. Check if hydrate check is enabled: `fluid.setHydrateCheck(true)`
2. Verify conditions are below hydrate curve
3. Ensure water component is present
4. Check mixing rule is set correctly

### Convergence Issues

1. Provide good initial temperature guess
2. Reduce step size for near-critical conditions
3. Check component fugacity calculations

### Unexpected Phase Fractions

1. Verify input composition sums to 1.0
2. Check for negative mole numbers
3. Use `prettyPrint()` to inspect phase compositions

### No Aqueous Phase with Low Water

This is expected behavior. Use `gasHydrateTPflash()` for systems with trace water to achieve gas-hydrate equilibrium directly.

---

## Related Documentation

- [Hydrate Models](../thermo/hydrate_models) - Thermodynamic model details
- [Flash Calculations Guide](../thermo/flash_calculations_guide) - General flash operations
- [Fluid Creation Guide](../thermo/fluid_creation_guide) - Setting up fluids
- [Process Equipment](../process/README) - Process integration
