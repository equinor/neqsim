---
title: "NeqSim Thermodynamics Guide"
description: "NeqSim (Non-Equilibrium Simulator) is a comprehensive library for thermodynamic calculations, specializing in oil and gas fluids, CO2 systems, and aqueous electrolytes. This guide provides an overview..."
---

# NeqSim Thermodynamics Guide

NeqSim (Non-Equilibrium Simulator) is a comprehensive library for thermodynamic calculations, specializing in oil and gas fluids, CO2 systems, and aqueous electrolytes. This guide provides an overview of the available models, methods, and how to use them.

## 1. Thermodynamic Models (Equations of State)

The core of any simulation is the Equation of State (EOS). NeqSim supports a wide range of EOSs tailored for different applications.

### 1.1 Cubic Equations of State
Standard models for oil and gas processing.
*   **SRK (Soave-Redlich-Kwong)**: `SystemSrkEos`. The industry standard for general hydrocarbon systems.
*   **PR (Peng-Robinson)**: `SystemPrEos`. Often preferred for reservoir engineering and density predictions.
*   **Modifications**:
    *   **Peneloux Volume Correction**: Improves liquid density predictions (`SystemSrkPenelouxEos`).
    *   **Twu-Coon**: Improved alpha functions for polar components.

### 1.2 Cubic Plus Association (CPA)
Essential for systems containing polar molecules (water, methanol, glycol) and hydrocarbons. It combines a cubic EOS (SRK or PR) with an association term (Wertheim).
*   **SRK-CPA**: `SystemSrkCPAstatoil`. Recommended for gas-hydrate inhibition (MEG/MeOH) and water-hydrocarbon VLE/LLE.
*   **PR-CPA**: `SystemPrCPA`.

### 1.3 Reference Equations
High-precision multiparameter equations for specific fluids or mixtures.
*   **GERG-2008**: `SystemGERG2008Eos`. The ISO standard for natural gas properties. Excellent for custody transfer and density calculation.
*   **Span-Wagner**: `SystemSpanWagnerEos`. High-precision EOS for pure CO2.
*   **IAPWS-IF97**: `SystemWaterIF97`. Industrial standard for water and steam.

### 1.4 Electrolyte Models
For systems containing salts and ions.
*   **Electrolyte CPA**: `SystemElectrolyteCPAstatoil`. Extends CPA to handle salt solubility and the effect of ions on phase equilibria.
*   **Furst-Renon**: `SystemFurstElectrolyteEos`.

## 2. Mixing Rules

Mixing rules define how pure component parameters are combined for mixtures.

*   **Classic (van der Waals)**: The default for non-polar hydrocarbon mixtures.
    *   `system.setMixingRule("classic")` or `system.setMixingRule(2)`
*   **Huron-Vidal (HV)**: Uses excess Gibbs energy models (like NRTL) to handle non-ideal mixtures.
    *   `system.setMixingRule("HV", "NRTL")`
*   **Wong-Sandler (WS)**: Another advanced mixing rule for highly non-ideal systems.

## 3. Flash Calculations

NeqSim performs various types of equilibrium calculations (flashes) via the `ThermodynamicOperations` class.

### 3.1 Standard Flashes
*   **TP Flash** (Temperature-Pressure): Calculates phase distribution at fixed T and P.
    *   `ops.TPflash()`
*   **PH Flash** (Pressure-Enthalpy): Used for isenthalpic processes (e.g., valves, throttling).
    *   `ops.PHflash(enthalpy, unit)`
*   **PS Flash** (Pressure-Entropy): Used for isentropic processes (e.g., compressors, expanders).
    *   `ops.PSflash(entropy, unit)`

### 3.2 Saturation Points
*   **Bubble Point**: `ops.bubblePointPressureFlash(false)` or `ops.bubblePointTemperatureFlash()`
*   **Dew Point**: `ops.dewPointPressureFlash()` or `ops.dewPointTemperatureFlash()`
*   **Water Dew Point**: `ops.waterDewPointTemperatureFlash()`

### 3.3 Solid Formation
*   **Hydrates**: Calculates hydrate formation temperature/pressure and inhibitor requirements.
    *   `ops.hydrateFormationTemperatureFlash()`
*   **Wax**: Wax Appearance Temperature (WAT).
    *   `ops.calcWAT()`
*   **Scale**: Mineral scale precipitation potential.
    *   `ops.checkScalePotential(phaseNumber)`

## 4. Physical Properties

Once a flash is performed, physical properties are available from the `Phase` objects.

*   **Density**: `phase.getDensity("kg/m3")`
*   **Viscosity**: `phase.getViscosity("kg/msec")`. See [Viscosity Models](viscosity_models).
*   **Thermal Conductivity**: `phase.getThermalConductivity("W/mK")`
*   **Surface Tension**: `system.getInterfacialTension(phase1, phase2)`
*   **Heat Capacity (Cp/Cv)**: `phase.getCp()`, `phase.getCv()`

## 5. Code Examples

### Java Example: Natural Gas Dew Point

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class DewPointExample {
    public static void main(String[] args) {
        // 1. Create System
        SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
        gas.addComponent("methane", 90.0);
        gas.addComponent("ethane", 5.0);
        gas.addComponent("propane", 3.0);
        gas.addComponent("water", 0.1); // Saturated water
        
        // 2. Set Mixing Rule
        gas.setMixingRule("classic");
        
        // 3. Initialize Operations
        ThermodynamicOperations ops = new ThermodynamicOperations(gas);
        
        // 4. Calculate Hydrocarbon Dew Point
        try {
            ops.dewPointTemperatureFlash();
            System.out.println("HC Dew Point: " + gas.getTemperature("C") + " C");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 5. Calculate Water Dew Point
        try {
            ops.waterDewPointTemperatureFlash();
            System.out.println("Water Dew Point: " + gas.getTemperature("C") + " C");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### Python Example: CO2 Density with GERG-2008

```python
from neqsim.thermo import SystemGERG2008Eos
from neqsim.thermodynamicoperations import ThermodynamicOperations

# 1. Create System
co2 = SystemGERG2008Eos(300.0, 100.0) # 300 K, 100 bar
co2.addComponent("CO2", 1.0)

# 2. Flash
ops = ThermodynamicOperations(co2)
ops.TPflash()

# 3. Get Properties
rho = co2.getPhase(0).getDensity("kg/m3")
print(f"CO2 Density at 100 bar/300 K: {rho} kg/m3")
```

## 6. Fluid Characterization

For real reservoir fluids containing heavy fractions (C7+), NeqSim provides tools to characterize the fluid based on specific gravity and molecular weight.

*   **Plus Fractions**: Use `system.addTBPfraction()` or `system.addPlusFraction()`.
*   **Lumping**: Reduce the number of components for faster simulation using `ModelLumping`.

### Advanced Options
*   **Heavy Oil**: For very heavy oils, use `setPlusFractionModel("Pedersen Heavy Oil")`.
*   **Whitson Gamma**: Use `setPlusFractionModel("Whitson Gamma")` if you have specific gamma distribution parameters.
*   **No Lumping**: To keep all individual carbon number components (C6, C7... C80), use `setLumpingModel("no lumping")`.

See [Fluid Characterization](fluid_characterization) for details.
