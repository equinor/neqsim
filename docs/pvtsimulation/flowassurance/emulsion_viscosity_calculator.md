---
title: "Emulsion Viscosity Calculator"
description: "Oil-water emulsion viscosity prediction with Einstein, Taylor, Brinkman, Pal-Rhodes, Woelflin, and Richardson models plus phase inversion detection."
---

# Emulsion Viscosity Calculator

The `EmulsionViscosityCalculator` predicts the effective viscosity of oil-water emulsions using six established correlations and includes automatic phase inversion detection.

**Class:** `neqsim.pvtsimulation.flowassurance.EmulsionViscosityCalculator`

## Why Emulsion Viscosity Matters

In multiphase oil-water production:
- Emulsion viscosity can be 10-100x higher than either phase alone
- Phase inversion (W/O to O/W) causes abrupt viscosity changes
- Pipeline pressure drop, pump sizing, and separator design all depend on emulsion rheology
- Demulsifier dosage optimization requires viscosity prediction

---

## Available Models

| Model | Formula | Best For |
|-------|---------|----------|
| **Einstein** | $\mu_r = 1 + 2.5\phi$ | Dilute systems ($\phi < 0.05$) |
| **Taylor** | Includes dispersed phase viscosity ratio | Low-moderate concentration |
| **Brinkman** | $\mu_r = (1 - \phi)^{-2.5}$ | Moderate concentration |
| **Pal-Rhodes** | Accounts for packing fraction | General purpose (default) |
| **Woelflin** | Empirical with tightness factor | Field emulsions |
| **Richardson** | Exponential model | Heavy oils |

Where $\mu_r$ is relative viscosity and $\phi$ is dispersed phase volume fraction.

---

## Phase Inversion

The calculator automatically detects phase inversion using the Arirachakaran correlation:

$$f_{inv} = \frac{1}{1 + \left(\frac{\mu_o}{\mu_w}\right)^{0.25} \left(\frac{\rho_w}{\rho_o}\right)^{0.5}}$$

- Below inversion point: **W/O** (water-in-oil) emulsion - oil is continuous phase
- Above inversion point: **O/W** (oil-in-water) emulsion - water is continuous phase

The inversion is adjusted by the tightness factor (emulsifier/surfactant effect).

---

## Usage

### Basic Calculation

```java
import neqsim.pvtsimulation.flowassurance.EmulsionViscosityCalculator;

EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
calc.setOilViscosity(20.0);      // mPa.s (cP)
calc.setWaterViscosity(0.8);     // mPa.s (cP)
calc.setOilDensity(850.0);       // kg/m3
calc.setWaterDensity(1020.0);    // kg/m3
calc.setWaterCut(0.30);          // 30% water cut
calc.setModel("pal_rhodes");     // Default model
calc.calculate();

double effectiveVisc = calc.getEffectiveViscosity(); // mPa.s
double relativeVisc = calc.getRelativeViscosity();   // dimensionless
String emulsionType = calc.getEmulsionType();        // "W/O" or "O/W"
double inversionPt = calc.getInversionWaterCut();    // volume fraction

System.out.printf("Effective viscosity: %.2f cP%n", effectiveVisc);
System.out.printf("Relative viscosity: %.2f%n", relativeVisc);
System.out.printf("Emulsion type: %s%n", emulsionType);
System.out.printf("Inversion point: %.1f%% WC%n", inversionPt * 100);
```

### Comparing Models

```java
String[] models = {"einstein", "taylor", "brinkman", "pal_rhodes", "woelflin", "richardson"};

EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
calc.setOilViscosity(15.0);
calc.setWaterViscosity(1.0);
calc.setOilDensity(860.0);
calc.setWaterDensity(1020.0);
calc.setWaterCut(0.25);

for (String model : models) {
    calc.setModel(model);
    calc.calculate();
    System.out.printf("%-12s  mu=%.2f cP  mu_r=%.3f%n",
        model, calc.getEffectiveViscosity(), calc.getRelativeViscosity());
}
```

### Viscosity vs Water Cut Curve

Generate data for plotting viscosity as a function of water cut:

```java
EmulsionViscosityCalculator calc = new EmulsionViscosityCalculator();
calc.setOilViscosity(20.0);
calc.setWaterViscosity(1.0);
calc.setOilDensity(850.0);
calc.setWaterDensity(1020.0);
calc.setModel("pal_rhodes");

// Generate curve from 0% to 80% water cut, 50 points
double[][] curve = calc.calculateViscosityCurve(0.0, 0.80, 50);

System.out.println("WaterCut  Viscosity_cP");
for (double[] point : curve) {
    System.out.printf("%.3f     %.2f%n", point[0], point[1]);
}
```

The curve typically shows:
1. Gradual increase in viscosity as water cut rises (W/O region)
2. Peak near the phase inversion point
3. Drop to lower values in the O/W region

### Demulsifier Effect

```java
calc.setWaterCut(0.35);
calc.calculate();
double withoutDemulsifier = calc.getEffectiveViscosity();

calc.setDemulsifierPresent(true);
calc.setDemulsifierEfficiency(0.6); // 0-1 scale
calc.calculate();
double withDemulsifier = calc.getEffectiveViscosity();

System.out.printf("Without demulsifier: %.2f cP%n", withoutDemulsifier);
System.out.printf("With demulsifier:    %.2f cP%n", withDemulsifier);
System.out.printf("Reduction: %.0f%%%n",
    (1.0 - withDemulsifier / withoutDemulsifier) * 100);
```

### Emulsion Tightness

The tightness factor models how strongly the emulsion resists coalescence:

```java
calc.setTightnessFactor(0.2);  // Loose emulsion (easy to break)
calc.calculate();
double loose = calc.getEffectiveViscosity();

calc.setTightnessFactor(0.8);  // Tight emulsion (hard to break)
calc.calculate();
double tight = calc.getEffectiveViscosity();
```

Tight emulsions (high surfactant, asphaltene, or fine solid content) have higher effective viscosity and shift the inversion point to higher water cuts.

---

## Input Parameters

| Parameter | Method | Unit | Default |
|-----------|--------|------|---------|
| Oil viscosity | `setOilViscosity()` | mPa.s (cP) | 10.0 |
| Water viscosity | `setWaterViscosity()` | mPa.s (cP) | 1.0 |
| Oil density | `setOilDensity()` | kg/m3 | 850.0 |
| Water density | `setWaterDensity()` | kg/m3 | 1020.0 |
| Water cut | `setWaterCut()` | fraction (0-1) | 0.0 |
| Temperature | `setTemperatureC()` | °C | 60.0 |
| Pressure | `setPressureBara()` | bara | 10.0 |
| Model | `setModel()` | string | `"pal_rhodes"` |
| Max packing fraction | `setMaxPackingFraction()` | fraction | 0.74 |
| Tightness factor | `setTightnessFactor()` | 0-1 | 0.5 |
| Demulsifier present | `setDemulsifierPresent()` | boolean | false |
| Demulsifier efficiency | `setDemulsifierEfficiency()` | 0-1 | 0.0 |

## Output Values

| Result | Method | Unit |
|--------|--------|------|
| Effective viscosity | `getEffectiveViscosity()` | mPa.s (cP) |
| Relative viscosity | `getRelativeViscosity()` | dimensionless |
| Viscosity ratio | `getViscosityRatio()` | dimensionless |
| Emulsion type | `getEmulsionType()` | "W/O" or "O/W" |
| Phase inverted? | `isInverted()` | boolean |
| Inversion water cut | `getInversionWaterCut()` | fraction |

---

## JSON Output

```java
calc.calculate();
String json = calc.toJson();
```

Returns a JSON object with:
- `inputs` - all input parameters
- `results` - effective viscosity, relative viscosity, emulsion type, inversion point

---

## Python Usage

```python
from neqsim import jneqsim

EmulsionViscosityCalculator = jneqsim.pvtsimulation.flowassurance.EmulsionViscosityCalculator

calc = EmulsionViscosityCalculator()
calc.setOilViscosity(20.0)
calc.setWaterViscosity(1.0)
calc.setOilDensity(850.0)
calc.setWaterDensity(1020.0)
calc.setWaterCut(0.30)
calc.setModel("pal_rhodes")
calc.calculate()

print(f"Effective viscosity: {calc.getEffectiveViscosity():.2f} cP")
print(f"Emulsion type: {calc.getEmulsionType()}")
print(f"Inversion point: {calc.getInversionWaterCut()*100:.1f}% WC")

# Generate viscosity curve
curve = calc.calculateViscosityCurve(0.0, 0.8, 50)
for i in range(len(curve)):
    print(f"WC={curve[i][0]:.2f}  mu={curve[i][1]:.2f} cP")
```

---

## Model Selection Guide

| Scenario | Recommended Model |
|----------|-------------------|
| Quick screening | `brinkman` |
| General production | `pal_rhodes` (default) |
| Heavy oil, high water cut | `woelflin` with tightness factor |
| Dilute emulsions (< 5% WC) | `einstein` |
| Detailed study | Compare all models |

---

## Related Documentation

- [Erosion Prediction Calculator](erosion_prediction)
- [Flow Assurance Screening Tools](flow_assurance_screening_tools)
- [Flow Assurance Overview](../flow_assurance_overview)
