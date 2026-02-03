---
title: "Wax Characterization"
description: "Documentation for wax modeling and characterization in NeqSim."
---

# Wax Characterization

Documentation for wax modeling and characterization in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Wax Formation Theory](#wax-formation-theory)
- [WaxCharacterise Class](#waxcharacterise-class)
- [Wax Models](#wax-models)
- [Usage Examples](#usage-examples)
- [Flow Assurance Applications](#flow-assurance-applications)

---

## Overview

**Package:** `neqsim.thermo.characterization`

Wax precipitation is a major flow assurance concern in oil production, particularly in:
- Subsea pipelines
- Cold climate operations
- Production restarts after shutdown

NeqSim provides wax characterization and thermodynamic modeling capabilities based on the Pedersen model and related approaches.

### Key Classes

| Class | Description |
|-------|-------------|
| `WaxCharacterise` | Main wax characterization class |
| `WaxModelInterface` | Interface for wax models |
| `PedersenWaxModel` | Pedersen's wax precipitation model |

---

## Wax Formation Theory

### What is Wax?

Wax consists of high molecular weight n-paraffins (typically C18+) that crystallize when crude oil is cooled below the Wax Appearance Temperature (WAT).

### Key Temperatures

| Temperature | Description |
|-------------|-------------|
| WAT (Wax Appearance Temperature) | First crystals appear |
| Pour Point | Oil stops flowing |
| Gel Point | Oil becomes semi-solid |

### Wax Precipitation Mechanism

1. **Nucleation**: Wax molecules cluster as temperature drops
2. **Crystal Growth**: Wax crystals grow on nuclei
3. **Deposition**: Crystals deposit on cold surfaces (pipe walls)
4. **Aging**: Deposited wax hardens over time

### Thermodynamic Model

The solid-liquid equilibrium for wax is described by:

$$\ln\left(\frac{x_i^L \gamma_i^L}{x_i^S \gamma_i^S}\right) = \frac{\Delta H_f}{R} \left(\frac{1}{T_f} - \frac{1}{T}\right) + \frac{\Delta C_p}{R} \left(\frac{T_f}{T} - 1 - \ln\frac{T_f}{T}\right)$$

where:
- $x_i^L, x_i^S$ = mole fractions in liquid and solid
- $\gamma_i^L, \gamma_i^S$ = activity coefficients
- $\Delta H_f$ = heat of fusion
- $T_f$ = fusion (melting) temperature
- $\Delta C_p$ = heat capacity change upon fusion

---

## WaxCharacterise Class

### Creating a Wax Characterization

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.characterization.WaxCharacterise;

// Create oil system with plus fraction
SystemSrkEos oil = new SystemSrkEos(323.15, 50.0);
oil.addComponent("methane", 0.40);
oil.addComponent("ethane", 0.10);
oil.addComponent("propane", 0.08);
oil.addComponent("n-butane", 0.05);
oil.addComponent("n-pentane", 0.04);
oil.addComponent("n-hexane", 0.03);
oil.addTBPfraction("C7", 0.10, 95.0 / 1000, 0.72);
oil.addTBPfraction("C10", 0.08, 135.0 / 1000, 0.78);
oil.addTBPfraction("C15", 0.06, 210.0 / 1000, 0.82);
oil.addTBPfraction("C20", 0.04, 280.0 / 1000, 0.85);
oil.addTBPfraction("C30", 0.02, 420.0 / 1000, 0.88);
oil.setMixingRule("classic");

// Create wax characterization
WaxCharacterise waxChar = new WaxCharacterise(oil);
```

### Setting Wax Parameters

```java
// Set wax model parameters
// Parameters depend on the model used

// For Pedersen model, typical parameters:
double[] waxParams = new double[3];
waxParams[0] = 1.0;    // Parameter A
waxParams[1] = 0.0;    // Parameter B  
waxParams[2] = 0.0;    // Parameter C

waxChar.setWaxParameters(waxParams);

// Set individual parameter
waxChar.setWaxParameter(0, 1.05);
```

### Heat of Fusion Parameters

```java
// Set heat of fusion correlation parameter
waxChar.setParameterWaxHeatOfFusion(0, 0.0);

// Set triple point temperature parameter
waxChar.setParameterWaxTriplePointTemperature(0, 0.0);
```

---

## Wax Models

### Pedersen Wax Model

The default model based on Pedersen's work, which correlates wax properties with carbon number.

#### Heat of Fusion Correlation

$$\Delta H_f = A + B \cdot MW + C \cdot MW^2$$

where MW is the molecular weight of the n-paraffin.

#### Triple Point Temperature

$$T_{tp} = A_1 + A_2 \cdot \ln(CN) + A_3 \cdot CN$$

where CN is the carbon number.

### Model Interface

```java
import neqsim.thermo.characterization.WaxModelInterface;

// Get the wax model
WaxModelInterface model = waxChar.getModel();

// Add TBP fractions as wax-forming components
model.addTBPWax();

// Get wax parameters
double[] params = model.getWaxParameters();
```

---

## Usage Examples

### Basic Wax Appearance Temperature Calculation

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermo.characterization.WaxCharacterise;

// Create waxy crude oil
SystemSrkCPAstatoil oil = new SystemSrkCPAstatoil(323.15, 50.0);
oil.addComponent("methane", 0.30);
oil.addComponent("ethane", 0.08);
oil.addComponent("propane", 0.05);
oil.addComponent("n-hexane", 0.10);
oil.addTBPfraction("C10", 0.15, 0.140, 0.78);
oil.addTBPfraction("C20", 0.12, 0.280, 0.84);
oil.addTBPfraction("C30", 0.10, 0.420, 0.87);
oil.addTBPfraction("C40", 0.07, 0.560, 0.89);
oil.addTBPfraction("C50+", 0.03, 0.700, 0.91);
oil.setMixingRule(10);

// Characterize wax
WaxCharacterise waxChar = new WaxCharacterise(oil);
waxChar.getModel().addTBPWax();

// Calculate WAT (Wax Appearance Temperature)
ThermodynamicOperations ops = new ThermodynamicOperations(oil);
try {
    ops.calcWAT();
    double wat = oil.getTemperature() - 273.15;  // Convert to Celsius
    System.out.println("WAT: " + wat + " °C");
} catch (Exception e) {
    System.out.println("WAT calculation failed: " + e.getMessage());
}
```

### Wax Amount vs Temperature

```java
// Calculate wax precipitation curve
double[] temperatures = {50, 45, 40, 35, 30, 25, 20, 15, 10};  // °C
double pressure = 50.0;  // bara

System.out.println("Temperature (°C) | Wax (wt%)");
System.out.println("--------------------------");

for (double tempC : temperatures) {
    SystemSrkCPAstatoil system = oil.clone();
    system.setTemperature(tempC + 273.15);
    system.setPressure(pressure);
    
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    
    // Get wax amount if solid phase exists
    if (system.hasPhaseType("wax") || system.hasPhaseType("solid")) {
        double waxFraction = system.getWtFraction(system.getPhaseIndex("solid"));
        System.out.printf("%8.1f         | %5.2f%n", tempC, waxFraction * 100);
    } else {
        System.out.printf("%8.1f         | %5.2f%n", tempC, 0.0);
    }
}
```

### Complete Wax Characterization Workflow

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.characterization.WaxCharacterise;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

public class WaxCharacterizationExample {
    
    public static void main(String[] args) {
        // Step 1: Create fluid from PVT data
        SystemSrkCPAstatoil fluid = createFluidFromPVT();
        
        // Step 2: Characterize wax components
        WaxCharacterise waxChar = new WaxCharacterise(fluid);
        waxChar.getModel().addTBPWax();
        
        // Step 3: Tune wax parameters to match experimental data
        // (Example: adjust heat of fusion parameter to match experimental WAT)
        tuneWaxParameters(waxChar, 35.0);  // Target WAT = 35°C
        
        // Step 4: Calculate wax precipitation curve
        calculateWaxCurve(fluid);
        
        // Step 5: Export results
        exportResults(fluid);
    }
    
    private static SystemSrkCPAstatoil createFluidFromPVT() {
        SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(323.15, 100.0);
        
        // Add light components
        fluid.addComponent("nitrogen", 0.5);
        fluid.addComponent("CO2", 1.5);
        fluid.addComponent("methane", 35.0);
        fluid.addComponent("ethane", 8.0);
        fluid.addComponent("propane", 5.0);
        fluid.addComponent("i-butane", 1.5);
        fluid.addComponent("n-butane", 3.0);
        fluid.addComponent("i-pentane", 1.5);
        fluid.addComponent("n-pentane", 2.0);
        
        // Add characterized heavy fractions (potential wax formers)
        fluid.addTBPfraction("C6", 3.0, 0.086, 0.69);
        fluid.addTBPfraction("C7-C9", 8.0, 0.107, 0.74);
        fluid.addTBPfraction("C10-C15", 12.0, 0.160, 0.79);
        fluid.addTBPfraction("C16-C20", 8.0, 0.250, 0.83);
        fluid.addTBPfraction("C21-C30", 6.0, 0.380, 0.86);
        fluid.addTBPfraction("C31-C40", 3.0, 0.520, 0.88);
        fluid.addTBPfraction("C41+", 2.0, 0.700, 0.91);
        
        fluid.setMixingRule(10);
        return fluid;
    }
    
    private static void tuneWaxParameters(WaxCharacterise waxChar, 
                                          double targetWAT) {
        // Iterative tuning to match experimental WAT
        // This is a simplified example
        double[] params = waxChar.getWaxParameters();
        
        // Adjust parameters to match target WAT
        // In practice, this would involve optimization
        params[0] = 1.0 + (targetWAT - 30.0) * 0.01;
        waxChar.setWaxParameters(params);
    }
    
    private static void calculateWaxCurve(SystemSrkCPAstatoil fluid) {
        // ... implementation
    }
    
    private static void exportResults(SystemSrkCPAstatoil fluid) {
        // ... implementation
    }
}
```

---

## Flow Assurance Applications

### Pipeline Wax Deposition Prediction

```java
// Pipeline conditions
double inletTemp = 60.0;   // °C
double outletTemp = 15.0;  // °C (cold seabed)
double pressure = 80.0;    // bara

// Calculate wax deposition risk
SystemSrkCPAstatoil fluid = createWaxyOil();
WaxCharacterise waxChar = new WaxCharacterise(fluid);
waxChar.getModel().addTBPWax();

// Check if operating below WAT
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.calcWAT();
double wat = fluid.getTemperature() - 273.15;

if (outletTemp < wat) {
    System.out.println("WARNING: Operating below WAT!");
    System.out.println("WAT: " + wat + " °C");
    System.out.println("Outlet temp: " + outletTemp + " °C");
    System.out.println("Subcooling: " + (wat - outletTemp) + " °C");
    
    // Estimate wax buildup potential
    double subcooling = wat - outletTemp;
    String risk = subcooling > 20 ? "HIGH" : 
                  subcooling > 10 ? "MEDIUM" : "LOW";
    System.out.println("Deposition risk: " + risk);
}
```

### Wax Inhibitor Evaluation

```java
// Test effect of pour point depressant (PPD)
SystemSrkCPAstatoil fluidWithPPD = createWaxyOil();

// Modify wax properties to simulate PPD effect
WaxCharacterise waxCharPPD = new WaxCharacterise(fluidWithPPD);
double[] params = waxCharPPD.getWaxParameters();
params[0] *= 0.85;  // Reduce wax formation tendency
waxCharPPD.setWaxParameters(params);

// Compare WAT with and without PPD
ThermodynamicOperations opsBase = new ThermodynamicOperations(createWaxyOil());
opsBase.calcWAT();
double watBase = opsBase.getThermoSystem().getTemperature() - 273.15;

ThermodynamicOperations opsPPD = new ThermodynamicOperations(fluidWithPPD);
opsPPD.calcWAT();
double watPPD = opsPPD.getThermoSystem().getTemperature() - 273.15;

System.out.println("WAT without PPD: " + watBase + " °C");
System.out.println("WAT with PPD: " + watPPD + " °C");
System.out.println("WAT reduction: " + (watBase - watPPD) + " °C");
```

### Restart Analysis

```java
// Analyze restart conditions after cold shutdown
double ambientTemp = 4.0;  // °C (seabed temperature)
double shutdownTime = 48.0;  // hours

// Check gel formation risk
if (ambientTemp < pourPoint) {
    System.out.println("CRITICAL: Gel formation likely!");
    System.out.println("Ambient: " + ambientTemp + " °C");
    System.out.println("Pour point: " + pourPoint + " °C");
    System.out.println("Margin: " + (pourPoint - ambientTemp) + " °C");
    
    // Recommend restart procedure
    System.out.println("\nRecommended actions:");
    System.out.println("1. Chemical treatment before restart");
    System.out.println("2. Hot oil circulation");
    System.out.println("3. Controlled pressure buildup");
}
```

---

## Parameter Estimation

### From Experimental Data

Wax model parameters can be estimated from experimental data:

| Data Type | Use |
|-----------|-----|
| WAT | Primary parameter tuning |
| Wax content vs T | Validate precipitation curve |
| Pour point | Confirm gel behavior |
| n-Paraffin distribution | Component characterization |

### Typical Parameter Ranges

| Parameter | Typical Range | Effect |
|-----------|---------------|--------|
| A (heat of fusion) | 0.8 - 1.2 | Higher = higher WAT |
| B (MW coefficient) | -0.01 to 0.01 | Shape of curve |
| C (MW² coefficient) | 0 to 0.001 | High MW behavior |

---

## See Also

- [Asphaltene Characterization](asphaltene_characterization) - Asphaltene modeling
- [Flow Assurance](../../pvtsimulation/flow_assurance_overview) - Complete flow assurance guide
- [PVT Characterization](../pvt_fluid_characterization) - Fluid characterization
- [TBP Fractions](../characterization/tbp_fractions) - Plus fraction handling
