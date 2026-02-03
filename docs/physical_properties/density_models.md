---
title: Density Models
description: This guide documents the density correction models available in NeqSim for improving volumetric predictions.
---

# Density Models

This guide documents the density correction models available in NeqSim for improving volumetric predictions.

## Table of Contents
- [Overview](#overview)
- [Equation of State Density](#equation-of-state-density)
- [Volume Translation Methods](#volume-translation-methods)
  - [Peneloux Volume Shift](#peneloux-volume-shift)
  - [Component-Specific Corrections](#component-specific-corrections)
- [Liquid Density Correlations](#liquid-density-correlations)
  - [Costald](#costald)
  - [Rackett Equation](#rackett-equation)
- [Usage Examples](#usage-examples)
- [Model Selection Guide](#model-selection-guide)

---

## Overview

Density predictions from cubic equations of state (SRK, PR) often have systematic errors:
- **Liquid density:** Typically underpredicted by 5-15%
- **Vapor density:** Generally accurate
- **Critical region:** Poor accuracy

NeqSim provides volume translation and correlation-based methods to improve liquid density predictions.

**Basic density access:**
```java
fluid.init(3);  // Initialize with derivatives
fluid.initPhysicalProperties();

double density = fluid.getPhase(1).getDensity("kg/m3");  // Liquid phase
double molarVolume = fluid.getPhase(1).getMolarVolume();  // m³/mol
```

---

## Equation of State Density

### Direct EoS Calculation

Cubic equations of state calculate compressibility factor $Z$:

$$PV = ZnRT$$

Molar volume is then:
$$V_m = \frac{ZRT}{P}$$

Density:
$$\rho = \frac{PM_w}{ZRT}$$

**Issue with cubic EoS:**
At the critical point, $Z_c^{SRK} = 0.333$ and $Z_c^{PR} = 0.307$, while real hydrocarbons have $Z_c \approx 0.26$. This causes systematic liquid volume overprediction.

---

## Volume Translation Methods

### Peneloux Volume Shift

The Peneloux correction adds a constant shift to the EoS molar volume:

$$V_{corrected} = V_{EoS} - c$$

where $c$ is the volume shift parameter.

**Class:** `Peneloux`

**Mixture shift:**
$$c_{mix} = \sum_i x_i c_i$$

**Component shift correlation:**
$$c_i = 0.40768 \frac{RT_{c,i}}{P_{c,i}} \left( 0.29441 - Z_{RA,i} \right)$$

where $Z_{RA}$ is the Rackett compressibility factor (from COMP database).

**Setting shift parameters:**
```java
// Enable Peneloux correction (default for SRK)
fluid.setDensityModel("Peneloux");

// Or set component-specific shifts
fluid.getPhase(0).getComponent("methane").setVolumeCorrectionConst(0.0);
fluid.getPhase(1).getComponent("n-heptane").setVolumeCorrectionConst(-0.0105);
```

**Advantages:**
- Simple to implement
- Preserves vapor-liquid equilibrium
- Works for mixtures

**Limitations:**
- Temperature independent
- May not work well at extreme conditions
- Single parameter per component

---

### Component-Specific Corrections

NeqSim stores volume correction constants in the COMP database. For heavy hydrocarbons or polar compounds, these may need tuning.

**Accessing correction constants:**
```java
// Get current volume correction
double vc = fluid.getPhase(1).getComponent("n-decane").getVolumeCorrectionConst();

// Modify correction
fluid.getPhase(1).getComponent("n-decane").setVolumeCorrectionConst(-0.015);
```

**Temperature-dependent shift (Jhaveri-Youngren):**
Some systems require temperature-dependent corrections:

$$c(T) = c_0 + c_1 (T - T_{ref})$$

This is implemented in specific component models.

---

## Liquid Density Correlations

### Costald

The COSTALD (COrreSponding STAtes Liquid Density) correlation predicts saturated liquid volumes.

**Class:** `Costald`

**Equation:**
$$V_s = V^* V_R^{(0)} \left[ 1 - \omega_{SRK} V_R^{(\delta)} \right]$$

where:
- $V^*$ is the characteristic volume
- $V_R^{(0)}, V_R^{(\delta)}$ are functions of reduced temperature
- $\omega_{SRK}$ is the acentric factor

**Reduced volume functions:**
$$V_R^{(0)} = 1 + a(1-T_r)^{1/3} + b(1-T_r)^{2/3} + c(1-T_r) + d(1-T_r)^{4/3}$$

$$V_R^{(\delta)} = \frac{e + fT_r + gT_r^2 + hT_r^3}{T_r - 1.00001}$$

**Constants:**
- a = -1.52816
- b = 1.43907
- c = -0.81446
- d = 0.190454
- e = -0.296123
- f = 0.386914
- g = -0.0427258
- h = -0.0480645

**Mixing rules:**
$$V^*_{mix} = \frac{1}{4} \left[ \sum_i x_i V^*_i + 3 \left(\sum_i x_i V^{*2/3}_i\right) \left(\sum_i x_i V^{*1/3}_i\right) \right]$$

$$\omega_{mix} = \sum_i x_i \omega_i$$

**Usage:**
```java
fluid.setDensityModel("Costald");
fluid.initPhysicalProperties();

double liquidDensity = fluid.getPhase(1).getDensity("kg/m3");
```

**Best for:**
- Pure component liquid density
- Hydrocarbon mixtures at saturation
- Temperature range: 0.25 < Tr < 1.0

---

### Rackett Equation

A simple corresponding states correlation for saturated liquid density.

**Equation:**
$$V_s = \frac{RT_c}{P_c} Z_{RA}^{[1 + (1-T_r)^{2/7}]}$$

where $Z_{RA}$ is the Rackett compressibility factor.

**Spencer-Danner modification:**
Uses optimized $Z_{RA}$ values from experimental data rather than critical compressibility.

**For mixtures:**
$$Z_{RA,mix} = \sum_i x_i Z_{RA,i}$$
$$T_{c,mix} = \sum_i x_i T_{c,i}$$

**Usage:**
```java
// Access Rackett parameter
double Zra = fluid.getPhase(1).getComponent("n-pentane").getRacketZ();

// Rackett is used internally for volume correction
```

---

## Usage Examples

### Comparing Density Models

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

SystemInterface fluid = new SystemSrkEos(300.0, 50.0);
fluid.addComponent("methane", 0.1);
fluid.addComponent("n-pentane", 0.9);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();

// EoS density (no correction)
double densityEoS = fluid.getPhase(1).getDensity("kg/m3");
System.out.println("EoS only: " + densityEoS + " kg/m³");

// With Peneloux correction (default for SRK)
fluid.initPhysicalProperties();
double densityPeneloux = fluid.getPhase(1).getDensity("kg/m3");
System.out.println("Peneloux: " + densityPeneloux + " kg/m³");

// With Costald
fluid.setDensityModel("Costald");
fluid.initPhysicalProperties();
double densityCostald = fluid.getPhase(1).getDensity("kg/m3");
System.out.println("Costald: " + densityCostald + " kg/m³");
```

### Tuning Liquid Density

```java
// Create fluid with known experimental density
SystemInterface fluid = new SystemSrkEos(293.15, 1.01325);
fluid.addComponent("n-hexane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

double expDensity = 659.0;  // kg/m³ at 20°C
double calcDensity = fluid.getPhase(1).getDensity("kg/m3");
double error = (calcDensity - expDensity) / expDensity * 100;
System.out.println("Initial error: " + error + "%");

// Adjust volume correction to match experimental
double molarMass = fluid.getPhase(1).getMolarMass() * 1000;  // kg/kmol
double calcMolarVolume = molarMass / calcDensity;  // m³/kmol
double expMolarVolume = molarMass / expDensity;    // m³/kmol
double correction = (calcMolarVolume - expMolarVolume) / 1000;  // m³/mol

fluid.getPhase(1).getComponent("n-hexane").setVolumeCorrectionConst(correction);
fluid.initPhysicalProperties();

double newDensity = fluid.getPhase(1).getDensity("kg/m3");
System.out.println("Tuned density: " + newDensity + " kg/m³");
```

### Density vs Temperature

```java
SystemInterface fluid = new SystemSrkEos(300.0, 10.0);
fluid.addComponent("n-heptane", 1.0);
fluid.setMixingRule("classic");

double[] temps = {280, 300, 320, 340, 360, 380};

for (double T : temps) {
    fluid.setTemperature(T, "K");
    
    ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
    ops.TPflash();
    
    if (fluid.getPhase(1).getPhaseTypeName().equals("oil")) {
        fluid.initPhysicalProperties();
        double rho = fluid.getPhase(1).getDensity("kg/m3");
        System.out.println("T=" + T + " K: ρ=" + rho + " kg/m³");
    }
}
```

### High-Pressure Density

```java
// Compressed liquid density at high pressure
SystemInterface fluid = new SystemSrkEos(300.0, 500.0);  // 500 bar
fluid.addComponent("n-decane", 1.0);
fluid.setMixingRule("classic");

ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initPhysicalProperties();

double rho = fluid.getPhase(0).getDensity("kg/m3");
System.out.println("High-P density: " + rho + " kg/m³");

// For high-pressure liquids, Peneloux may be insufficient
// Consider using PC-SAFT or adjusting correction
```

---

## Model Selection Guide

| Situation | Recommended Model | Notes |
|-----------|------------------|-------|
| General hydrocarbons | Peneloux | Default, good accuracy |
| Near saturation | Costald | Better for sat. liquids |
| Polar compounds | PC-SAFT or CPA | Better fundamental basis |
| High pressure | Peneloux with tuning | May need adjustment |
| Critical region | GERG-2008 | If available |
| Quick estimate | EoS only | 5-15% error typical |

### Expected Accuracy

| Method | Liquid Density Error | Vapor Density Error |
|--------|---------------------|---------------------|
| SRK (no correction) | 5-15% | 1-3% |
| SRK + Peneloux | 1-3% | 1-3% |
| PR (no correction) | 3-10% | 1-3% |
| PR + Peneloux | 1-3% | 1-3% |
| Costald | 1-2% | N/A |
| GERG-2008 | 0.1-0.5% | 0.1-0.5% |

---

## API Reference

### Setting Density Model

```java
// Set density model for all phases
fluid.setDensityModel("Peneloux");  // or "Costald"

// The model affects initPhysicalProperties() calls
fluid.initPhysicalProperties();
```

### Accessing Density

```java
// Mass density
double rhoMass = phase.getDensity("kg/m3");
double rhoMass2 = phase.getDensity("lb/ft3");

// Molar density
double rhoMolar = phase.getDensity("mol/m3");

// Molar volume
double Vm = phase.getMolarVolume();  // m³/mol
```

### Volume Correction Parameters

```java
// Get/set volume correction constant
double c = component.getVolumeCorrectionConst();
component.setVolumeCorrectionConst(newValue);

// Get Rackett parameter
double Zra = component.getRacketZ();
```

---

## References

1. Peneloux, A., Rauzy, E., Freze, R. (1982). A Consistent Correction for Redlich-Kwong-Soave Volumes. Fluid Phase Equilib.
2. Hankinson, R.W., Thomson, G.H. (1979). A New Correlation for Saturated Densities of Liquids and Their Mixtures. AIChE J.
3. Rackett, H.G. (1970). Equation of State for Saturated Liquids. J. Chem. Eng. Data.
4. Spencer, C.F., Danner, R.P. (1972). Improved Equation for Prediction of Saturated Liquid Density. J. Chem. Eng. Data.
5. Jhaveri, B.S., Youngren, G.K. (1988). Three-Parameter Modification of the Peng-Robinson Equation of State. SPE Reservoir Eng.
