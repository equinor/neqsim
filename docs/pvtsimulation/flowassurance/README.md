---
title: Flow Assurance in NeqSim
description: Flow assurance is the discipline ensuring that hydrocarbon fluids can be produced, transported, and processed safely and economically throughout the life of a field. NeqSim provides comprehensive tool...
---

# Flow Assurance in NeqSim

Flow assurance is the discipline ensuring that hydrocarbon fluids can be produced, transported, and processed safely and economically throughout the life of a field. NeqSim provides comprehensive tools for predicting and managing flow assurance challenges.

## Overview

Flow assurance encompasses the prevention and remediation of:

- **Hydrate formation** - Ice-like structures that can block pipelines
- **Wax deposition** - Paraffin precipitation at low temperatures
- **Asphaltene precipitation** - Heavy organic precipitation during pressure/temperature changes
- **Scale formation** - Mineral deposits from produced water
- **Corrosion** - Material degradation from H₂S, CO₂, and water
- **Slugging** - Unstable multiphase flow regimes

## Documentation Structure

| Topic | Description |
|-------|-------------|
| [Asphaltene Modeling](asphaltene_modeling) | Overview of asphaltene stability analysis |
| [CPA-Based Asphaltene Calculations](asphaltene_cpa_calculations) | Thermodynamic onset pressure/temperature |
| [De Boer Asphaltene Screening](asphaltene_deboer_screening) | Empirical screening correlation |
| [Asphaltene Parameter Fitting](asphaltene_parameter_fitting) | Tuning CPA parameters to experimental data |
| [Asphaltene Method Comparison](asphaltene_method_comparison) | Comparing CPA vs De Boer approaches |
| [Asphaltene Model Validation](asphaltene_validation) | Validation against SPE-24987 field data |

## Key Classes

### Asphaltene Analysis

| Class | Package | Purpose |
|-------|---------|---------|
| `AsphalteneCharacterization` | `neqsim.thermo.characterization` | SARA-based characterization |
| `AsphalteneStabilityAnalyzer` | `neqsim.pvtsimulation.flowassurance` | High-level CPA analysis API |
| `DeBoerAsphalteneScreening` | `neqsim.pvtsimulation.flowassurance` | Empirical De Boer screening |
| `AsphalteneMethodComparison` | `neqsim.pvtsimulation.flowassurance` | Compare multiple methods |
| `AsphalteneOnsetPressureFlash` | `neqsim.thermodynamicoperations` | Onset pressure calculation |
| `AsphalteneOnsetTemperatureFlash` | `neqsim.thermodynamicoperations` | Onset temperature calculation |
| `AsphalteneOnsetFitting` | `neqsim.pvtsimulation.util.parameterfitting` | Fit CPA parameters to experimental onset data |
| `AsphalteneOnsetFunction` | `neqsim.pvtsimulation.util.parameterfitting` | Levenberg-Marquardt function for fitting |

## Quick Start

### Simple Screening (De Boer Method)

```java
import neqsim.pvtsimulation.flowassurance.DeBoerAsphalteneScreening;

// Create screening for specific conditions
DeBoerAsphalteneScreening screening = new DeBoerAsphalteneScreening(
    350.0,  // Reservoir pressure [bar]
    150.0,  // Bubble point pressure [bar]
    750.0   // In-situ oil density [kg/m³]
);

// Get risk assessment
String risk = screening.evaluateRisk();
double riskIndex = screening.calculateRiskIndex();

System.out.println("Asphaltene Risk: " + risk);
System.out.println("Risk Index: " + riskIndex);
```

### Thermodynamic Analysis (CPA Method)

```java
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create CPA fluid with asphaltene
SystemSrkCPAstatoil fluid = new SystemSrkCPAstatoil(373.15, 200.0);
fluid.addComponent("methane", 0.5);
fluid.addComponent("n-heptane", 0.45);
fluid.addComponent("asphaltene", 0.05);
fluid.setMixingRule("classic");

// Calculate onset pressure
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.asphalteneOnsetPressure();

double onsetPressure = fluid.getPressure();
System.out.println("Onset Pressure: " + onsetPressure + " bar");
```

### Parameter Fitting (Tuning to Lab Data)

```java
import neqsim.pvtsimulation.util.parameterfitting.AsphalteneOnsetFitting;

// Create fitter with your fluid system
AsphalteneOnsetFitting fitter = new AsphalteneOnsetFitting(fluid);

// Add experimental onset points (T in Kelvin, P in bar)
fitter.addOnsetPoint(353.15, 350.0);  // 80°C
fitter.addOnsetPoint(373.15, 320.0);  // 100°C
fitter.addOnsetPoint(393.15, 280.0);  // 120°C

// Set initial parameter guesses and run fitting
fitter.setInitialGuess(3500.0, 0.005);  // epsilon/R, kappa
fitter.solve();

// Get fitted CPA parameters
double epsilonR = fitter.getFittedAssociationEnergy();
double kappa = fitter.getFittedAssociationVolume();
```

## Asphaltene Component in NeqSim Database

NeqSim includes a pre-defined asphaltene pseudo-component with CPA parameters suitable for typical asphaltene modeling:

| Property | Value | Unit |
|----------|-------|------|
| Molecular Weight | 750 | g/mol |
| Critical Temperature | 1049.85 | K |
| Critical Pressure | 8.0 | bar |
| Acentric Factor | 1.5 | - |
| Association Energy (ε/R) | 3500 | K |
| Association Volume (κ) | 0.005 | - |

These parameters can be tuned to match specific experimental data using the `AsphalteneOnsetFitting` class.

## PhaseType.ASPHALTENE

When asphaltene precipitates, NeqSim identifies it using the dedicated `PhaseType.ASPHALTENE` enum value. This enables:

- **Accurate phase identification** - Distinguish asphaltene from wax or hydrate
- **Correct physical properties** - Asphaltene-specific density (~1150 kg/m³), viscosity (~10,000 Pa·s), thermal conductivity (~0.20 W/mK)
- **Easy API access** - Use `fluid.hasPhaseType("asphaltene")` to detect precipitation

```java
import neqsim.thermo.phase.PhaseType;

// After flash calculation
if (fluid.hasPhaseType(PhaseType.ASPHALTENE)) {
    PhaseInterface asphaltene = fluid.getPhaseOfType("asphaltene");
    System.out.println("Asphaltene density: " + asphaltene.getDensity("kg/m3") + " kg/m³");
    System.out.println("Asphaltene fraction: " + (asphaltene.getBeta() * 100) + "%");
}
```

See [Asphaltene Modeling](asphaltene_modeling) for more details on `PhaseType.ASPHALTENE`.

## Related Topics

- [Thermodynamic Models](../../thermo/README) - Equation of state fundamentals
- [PVT Simulation](../README) - Fluid characterization
- [Process Equipment](../../process/README) - Separator and pipeline modeling
