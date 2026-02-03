---
title: "Asphaltene Characterization"
description: "Documentation for asphaltene modeling using SARA analysis in NeqSim."
---

# Asphaltene Characterization

Documentation for asphaltene modeling using SARA analysis in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SARA Analysis](#sara-analysis)
- [Colloidal Instability Index](#colloidal-instability-index)
- [AsphalteneCharacterization Class](#asphaltenecharacterization-class)
- [CPA Parameters](#cpa-parameters)
- [Usage Examples](#usage-examples)
- [De Boer Screening](#de-boer-screening)

---

## Overview

**Package:** `neqsim.thermo.characterization`

Asphaltene precipitation is a critical flow assurance issue that can cause:
- Well bore plugging
- Pipeline blockages
- Separator fouling
- Production downtime

NeqSim provides tools for characterizing asphaltene content and predicting precipitation using thermodynamic models (primarily CPA).

### Key Classes

| Class | Description |
|-------|-------------|
| `AsphalteneCharacterization` | SARA-based characterization |
| `PedersenAsphalteneCharacterization` | Pedersen's correlation approach |

---

## SARA Analysis

### What is SARA?

SARA analysis separates crude oil into four fractions:

| Fraction | Description | Characteristics |
|----------|-------------|-----------------|
| **S**aturates | Alkanes (linear, branched, cyclic) | Non-polar, lightest |
| **A**romatics | Aromatic rings | Moderately polar |
| **R**esins | Polar aromatics with heteroatoms | Stabilize asphaltenes |
| **A**sphaltenes | Large polyaromatic molecules | Heaviest, most polar |

### SARA Fraction Properties

| Property | Saturates | Aromatics | Resins | Asphaltenes |
|----------|-----------|-----------|--------|-------------|
| MW (g/mol) | 300-600 | 300-800 | 500-1200 | 1000-10000 |
| H/C ratio | ~2.0 | 1.0-1.5 | 1.0-1.4 | 0.9-1.2 |
| Polarity | Non-polar | Low | Medium | High |
| Solubility | n-alkanes | Toluene | Toluene | Toluene |

---

## Colloidal Instability Index

### Definition

The Colloidal Instability Index (CII) predicts asphaltene stability:

$$CII = \frac{Saturates + Asphaltenes}{Aromatics + Resins}$$

### Stability Criteria

| CII Value | Stability | Risk |
|-----------|-----------|------|
| < 0.7 | Stable | Low precipitation risk |
| 0.7 - 0.9 | Metastable | Moderate risk |
| > 0.9 | Unstable | High precipitation risk |

### Physical Interpretation

- **Resins** stabilize asphaltenes by forming a solvation shell
- **Aromatics** provide a favorable solvent environment
- **Saturates** are poor solvents for asphaltenes
- High CII indicates insufficient stabilization

---

## AsphalteneCharacterization Class

### Creating a Characterization

```java
import neqsim.thermo.characterization.AsphalteneCharacterization;

// Create from SARA fractions (weight fractions, must sum to 1.0)
AsphalteneCharacterization asphChar = new AsphalteneCharacterization(
    0.45,   // Saturates
    0.30,   // Aromatics
    0.20,   // Resins
    0.05    // Asphaltenes
);

// Or create empty and set values
AsphalteneCharacterization asphChar2 = new AsphalteneCharacterization();
asphChar2.setSaturates(0.45);
asphChar2.setAromatics(0.30);
asphChar2.setResins(0.20);
asphChar2.setAsphaltenes(0.05);
```

### Calculating Stability Indices

```java
// Calculate Colloidal Instability Index
double cii = asphChar.calcColloidalInstabilityIndex();
System.out.println("CII: " + cii);

// Check stability
if (cii < AsphalteneCharacterization.CII_STABLE_LIMIT) {
    System.out.println("Asphaltenes are stable");
} else if (cii < AsphalteneCharacterization.CII_UNSTABLE_LIMIT) {
    System.out.println("Asphaltenes are metastable - monitor carefully");
} else {
    System.out.println("Asphaltenes are unstable - high precipitation risk");
}

// Calculate resin-to-asphaltene ratio
double raRatio = asphChar.calcResinAsphalteneRatio();
System.out.println("R/A ratio: " + raRatio);
```

### Setting C7+ Properties

```java
// Set C7+ fraction properties for better characterization
asphChar.setMwC7plus(350.0);        // g/mol
asphChar.setDensityC7plus(850.0);   // kg/m³

// Estimate asphaltene properties
double mwAsph = asphChar.estimateAsphalteneMW();
double mwResin = asphChar.estimateResinMW();
System.out.println("Estimated asphaltene MW: " + mwAsph + " g/mol");
System.out.println("Estimated resin MW: " + mwResin + " g/mol");
```

---

## CPA Parameters

### Asphaltene Association Parameters

For CPA modeling, asphaltenes are treated as associating molecules:

```java
// Set CPA parameters
asphChar.setAsphalteneMW(1700.0);              // g/mol
asphChar.setResinMW(800.0);                     // g/mol
asphChar.setAsphalteneAssociationEnergy(3500.0); // K
asphChar.setAsphalteneAssociationVolume(0.05);
asphChar.setResinAsphalteneAssociationEnergy(2500.0); // K (cross-association)

// Get parameters for CPA model
double epsilon = asphChar.getAsphalteneAssociationEnergy();  // K
double kappa = asphChar.getAsphalteneAssociationVolume();
```

### Association Scheme

Asphaltenes are typically modeled with:
- Self-association sites (asphaltene-asphaltene bonding)
- Cross-association sites (resin-asphaltene bonding)

| Interaction | Energy (K) | Volume |
|-------------|------------|--------|
| Asphaltene-Asphaltene | 3000-4000 | 0.03-0.07 |
| Resin-Asphaltene | 2000-3000 | 0.02-0.05 |

---

## Usage Examples

### Complete Characterization Workflow

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermo.characterization.AsphalteneCharacterization;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Step 1: Create asphaltene characterization from SARA data
AsphalteneCharacterization asphChar = new AsphalteneCharacterization(
    0.42,  // Saturates
    0.32,  // Aromatics
    0.22,  // Resins  
    0.04   // Asphaltenes
);

// Step 2: Set additional properties
asphChar.setMwC7plus(380.0);
asphChar.setDensityC7plus(870.0);
asphChar.setAsphalteneMW(1800.0);
asphChar.setResinMW(850.0);

// Step 3: Calculate stability indices
double cii = asphChar.calcColloidalInstabilityIndex();
double raRatio = asphChar.calcResinAsphalteneRatio();

System.out.println("=== Asphaltene Stability Analysis ===");
System.out.println("CII: " + String.format("%.3f", cii));
System.out.println("R/A ratio: " + String.format("%.2f", raRatio));
System.out.println("Stability: " + asphChar.getStabilityClassification());

// Step 4: Create fluid system with asphaltene pseudo-component
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 200.0);

// Add light components
fluid.addComponent("methane", 0.35);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-hexane", 0.12);

// Add characterized fractions including asphaltene
fluid.addTBPfraction("Saturates", 0.20, 0.300, 0.78);
fluid.addTBPfraction("Aromatics", 0.12, 0.350, 0.88);
fluid.addTBPfraction("Resins", 0.06, asphChar.getResinMW()/1000, 0.98);
fluid.addComponent("asphaltene", 0.02);  // As pseudo-component

fluid.setMixingRule(10);  // CPA mixing rule

// Step 5: Run flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// Check for asphaltene precipitation
if (fluid.hasPhaseType("solid") || fluid.hasPhaseType("asphaltene")) {
    System.out.println("Asphaltene precipitation predicted!");
    double precipAmount = fluid.getPhase("solid").getMolarMass() * 
                         fluid.getPhase("solid").getNumberOfMolesInPhase();
    System.out.println("Precipitated amount: " + precipAmount + " kg");
}
```

### Pressure-Induced Precipitation

```java
// Analyze asphaltene stability vs pressure (common during depressurization)
double temperature = 100.0 + 273.15;  // K
double[] pressures = {400, 350, 300, 250, 200, 150, 100, 50};  // bara

System.out.println("Pressure (bara) | Asphaltene Phase | Amount");
System.out.println("----------------------------------------------");

for (double pressure : pressures) {
    SystemSrkCPAstatoil system = fluid.clone();
    system.setTemperature(temperature);
    system.setPressure(pressure);
    
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    ops.TPflash();
    
    String phase = system.hasPhaseType("solid") ? "Precipitated" : "Dissolved";
    double amount = system.hasPhaseType("solid") ? 
        system.getPhase("solid").getNumberOfMolesInPhase() : 0.0;
    
    System.out.printf("%8.0f        | %12s     | %.4f%n", 
        pressure, phase, amount);
}
```

### Gas Injection Effect

```java
// Evaluate asphaltene stability during CO2/gas injection
SystemSrkCPAstatoil baseFluid = createReservoirFluid();
AsphalteneCharacterization asphChar = new AsphalteneCharacterization(
    0.40, 0.30, 0.25, 0.05
);

double[] injectionRatios = {0.0, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30};

System.out.println("CO2 Injection Effect on Asphaltene Stability");
System.out.println("--------------------------------------------");

for (double ratio : injectionRatios) {
    SystemSrkCPAstatoil fluid = baseFluid.clone();
    
    // Add injection gas
    double totalMoles = fluid.getTotalNumberOfMoles();
    fluid.addComponent("CO2", totalMoles * ratio / (1 - ratio));
    
    // Flash at reservoir conditions
    fluid.setTemperature(373.15);
    fluid.setPressure(250.0);
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    
    boolean precipitated = fluid.hasPhaseType("solid");
    System.out.printf("CO2: %5.1f%% | Precipitation: %s%n",
        ratio * 100, precipitated ? "YES" : "NO");
}
```

---

## De Boer Screening

### Overview

The De Boer plot is a screening method for asphaltene precipitation risk based on:
- Reservoir pressure and saturation pressure
- In-situ density difference

### De Boer Correlation

$$\Delta\rho = \rho_{res} - \rho_{sat}$$

The risk is assessed by plotting $P_{res} - P_{sat}$ vs $\Delta\rho$:

| Region | Risk Level |
|--------|------------|
| Below lower curve | Low risk |
| Between curves | Moderate risk |
| Above upper curve | High risk |

### Implementation

```java
// De Boer screening calculation
double reservoirPressure = 350.0;  // bara
double saturationPressure = 180.0;  // bara (bubble point)
double reservoirDensity = 650.0;    // kg/m³
double saturationDensity = 580.0;   // kg/m³ (at bubble point)

double deltaP = reservoirPressure - saturationPressure;
double deltaRho = reservoirDensity - saturationDensity;

// De Boer risk assessment
String risk;
if (deltaP > 200 && deltaRho > 100) {
    risk = "HIGH - Asphaltene precipitation very likely";
} else if (deltaP > 100 || deltaRho > 50) {
    risk = "MODERATE - Monitor conditions carefully";
} else {
    risk = "LOW - Unlikely to have problems";
}

System.out.println("De Boer Screening Results:");
System.out.println("ΔP (res - sat): " + deltaP + " bar");
System.out.println("Δρ (res - sat): " + deltaRho + " kg/m³");
System.out.println("Risk: " + risk);
```

---

## Best Practices

### SARA Data Quality

1. **Laboratory method**: Ensure consistent SARA analysis method (ASTM D2007, ASTM D4124)
2. **Sample handling**: Prevent oxidation and evaporation
3. **Reproducibility**: Use average of multiple analyses

### Model Tuning

1. **Onset pressure**: Tune association parameters to match experimental onset
2. **Amount precipitated**: Validate against filtration experiments
3. **Temperature dependence**: Check stability at multiple temperatures

### Operational Recommendations

| CII Range | Recommendation |
|-----------|----------------|
| < 0.7 | Standard operations |
| 0.7-0.9 | Regular monitoring, consider inhibitors |
| > 0.9 | Inhibitor treatment, pressure management |

---

## See Also

- [Wax Characterization](wax_characterization) - Wax modeling
- [Flow Assurance](../../pvtsimulation/flow_assurance_overview) - Complete guide
- [Electrolyte CPA](../ElectrolyteCPAModel) - CPA model details
