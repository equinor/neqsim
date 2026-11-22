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

### Pedersen asphaltene properties

`PedersenAsphalteneCharacterization` calculates its own critical temperature, critical
pressure and acentric factor. Both single-component and distributed-asphaltene addition pass
those values through the explicit-property `addTBPfraction` overload and retain each
asphaltene component's independently calculated normal boiling point. This route remains
available for cuts above 1120 g/mol because it does not estimate their critical properties
with the rejected generic Pedersen heavy-oil TBP branch; see
[TBP Fraction Models](../../wiki/tbp_fraction_models) for that boundary.

The asphaltene correlations remain empirical. Calibrate their inputs and binary interaction
parameters against representative density, phase-equilibrium and precipitation measurements;
this separate property pathway does not validate the generic heavy-oil correlations or
establish accuracy outside the asphaltene model's qualified range.

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

Use the constructor when all four SARA mass fractions are known. They must sum to 1.0 within
the class tolerance. For a reusable object, update all fractions together so validation is applied
to the complete assay.

```java
import neqsim.thermo.characterization.AsphalteneCharacterization;

AsphalteneCharacterization asphChar =
    new AsphalteneCharacterization(0.45, 0.30, 0.20, 0.05);

AsphalteneCharacterization reusable = new AsphalteneCharacterization();
reusable.setSARAFractions(0.45, 0.30, 0.20, 0.05);
```

### Calculating Stability Indices

```java
double cii = asphChar.getColloidalInstabilityIndex();
double resinToAsphaltene = asphChar.getResinToAsphalteneRatio();
String assessment = asphChar.evaluateStability();
```

The CII and resin-to-asphaltene ratio are screening quantities derived from the supplied SARA
assay. The returned assessment applies the current class thresholds; it is not a calibrated
precipitation-onset or deposition prediction.

### Setting C7+ Properties

Set the C7+ molecular weight in g/mol and density in kg/m3 together before requesting the
correlation estimates.

```java
asphChar.setC7plusProperties(350.0, 850.0);

double estimatedAsphalteneMw = asphChar.estimateAsphalteneMolecularWeight();
double estimatedResinMw = asphChar.estimateResinMolecularWeight();
```

---

## CPA Parameters

### Asphaltene Association Parameters

The characterization object exposes the asphaltene and resin molecular weights plus the
asphaltene self-association energy and volume:

```java
asphChar.setMwAsphaltene(1700.0);
asphChar.setMwResin(800.0);
asphChar.setAsphalteneAssociationEnergy(3500.0);
asphChar.setAsphalteneAssociationVolume(0.05);

double epsilon = asphChar.getAsphalteneAssociationEnergy();
double kappa = asphChar.getAsphalteneAssociationVolume();
```

The class applies its resin association treatment internally when
`addAsphalteneComponents(system, totalC7plusMoles)` is used. It does not expose a separate
public cross-association setter. Treat the default values as starting points and qualify or fit
them against representative onset or precipitation data before engineering use.

---

## Usage Examples

### Complete SARA Screening Workflow

<!-- asphaltene-characterization-quick-start: complete -->
```java
import neqsim.thermo.characterization.AsphalteneCharacterization;

public final class AsphalteneCharacterizationExample {
    private AsphalteneCharacterizationExample() {
    }

    public static void main(String[] args) {
        AsphalteneCharacterization characterization =
                new AsphalteneCharacterization(0.42, 0.32, 0.22, 0.04);
        characterization.setC7plusProperties(380.0, 870.0);

        double cii = characterization.getColloidalInstabilityIndex();
        if (!Double.isFinite(cii) || Math.abs(cii - 0.8518518518518519) > 1.0e-12) {
            throw new IllegalStateException("Unexpected colloidal instability index");
        }

        double resinToAsphaltene = characterization.getResinToAsphalteneRatio();
        if (!Double.isFinite(resinToAsphaltene)
                || Math.abs(resinToAsphaltene - 5.5) > 1.0e-12) {
            throw new IllegalStateException("Unexpected resin-to-asphaltene ratio");
        }

        double asphalteneMw = characterization.estimateAsphalteneMolecularWeight();
        double resinMw = characterization.estimateResinMolecularWeight();
        if (!Double.isFinite(asphalteneMw)
                || Math.abs(asphalteneMw - 2793.0) > 1.0e-9
                || !Double.isFinite(resinMw)
                || Math.abs(resinMw - 1396.5) > 1.0e-9) {
            throw new IllegalStateException("Unexpected C7+ correlation estimate");
        }

        String assessment = characterization.evaluateStability();
        if (!assessment.contains("MODERATE RISK")
                || !assessment.contains("good peptization")) {
            throw new IllegalStateException("Unexpected screening classification");
        }
    }
}
```

The example is an executable SARA screening contract. It demonstrates current API names, units,
correlation output, and classification thresholds without interpreting a generic thermodynamic
`solid` phase as asphaltene.

### Pressure Depletion and Gas Injection

A generic TP flash does not by itself establish an asphaltene phase, precipitation amount, or
onset pressure. Pressure-depletion and gas-injection studies require a configured asphaltene
model, fluid-specific pseudo-components or CPA parameters, and calibration against representative
experimental data. Continue with the
[asphaltene modeling guide](../../pvtsimulation/flowassurance/asphaltene_modeling.md),
[CPA calculations](../../pvtsimulation/flowassurance/asphaltene_cpa_calculations.md), and
[parameter fitting](../../pvtsimulation/flowassurance/asphaltene_parameter_fitting.md).

---

## De Boer Screening

Use the dedicated [De Boer screening guide](../../pvtsimulation/flowassurance/asphaltene_deboer_screening.md)
and its executable example rather than copying threshold logic into an application. De Boer is an
empirical screening method; it does not replace fluid-specific onset or deposition qualification.

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
