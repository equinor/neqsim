# ISO 6578 - LNG Density Calculation

ISO 6578 provides methods for calculating the density of liquefied natural gas (LNG) from composition and temperature.

## Table of Contents
- [Overview](#overview)
- [Calculation Method](#calculation-method)
- [Implementation](#implementation)
- [Usage Examples](#usage-examples)
- [Correction Factors](#correction-factors)
- [Component Data](#component-data)

---

## Overview

**Standard:** ISO 6578:2017 - Refrigerated hydrocarbon liquids — Static measurement — Calculation procedure

**Purpose:** Calculate LNG density for custody transfer and inventory management.

**Scope:**
- Temperature range: -195°C to -100°C (78 K to 173 K)
- Applicable to typical LNG compositions
- Based on corresponding states principle with correction factors

**Class:** `Standard_ISO6578`

---

## Calculation Method

### Principle

LNG density is calculated from:

1. **Ideal mixing** - Pure component molar volumes
2. **Excess volume correction** - Klosek-McKinley correction factors

### Density Equation

$$\rho = \frac{M_{mix}}{V_{mix}}$$

where:
$$V_{mix} = \sum_i x_i V_i + \Delta V_{correction}$$

- $V_i$ = molar volume of pure component i at temperature T
- $\Delta V_{correction}$ = Klosek-McKinley correction for non-ideal mixing

### Klosek-McKinley Correction

$$V_{mix} = \sum_i x_i V_i - k_1 x_{N_2} - k_2 x_{CH_4}$$

where:
- $k_1$ = correction factor 1 (function of T and molar mass)
- $k_2$ = correction factor 2 (function of T and molar mass)
- $x_{N_2}$ = nitrogen mole fraction
- $x_{CH_4}$ = methane mole fraction

---

## Implementation

### Constructor

```java
import neqsim.standards.gasquality.Standard_ISO6578;

// Create standard
Standard_ISO6578 iso6578 = new Standard_ISO6578(thermoSystem);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform density calculation |
| `getValue("density", "kg/m3")` | Get density in kg/m³ |
| `useISO6578VolumeCorrectionFacotrs(boolean)` | Toggle ISO 6578 vs alternative factors |

### Internal Data

The class includes tabulated data for:
- Pure component molar volumes vs temperature
- $k_1$ and $k_2$ correction factor matrices
- Interpolation functions for intermediate temperatures

---

## Usage Examples

### Basic LNG Density Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.gasquality.Standard_ISO6578;

// Create LNG composition at storage temperature
SystemInterface lng = new SystemSrkEos(273.15 - 162, 1.01325);  // -162°C
lng.addComponent("methane", 0.9200);
lng.addComponent("ethane", 0.0500);
lng.addComponent("propane", 0.0180);
lng.addComponent("n-butane", 0.0040);
lng.addComponent("i-butane", 0.0030);
lng.addComponent("nitrogen", 0.0050);
lng.setMixingRule("classic");

// Calculate density
Standard_ISO6578 iso6578 = new Standard_ISO6578(lng);
iso6578.calculate();

double density = iso6578.getValue("density", "kg/m3");
System.out.printf("LNG Density at %.1f°C = %.2f kg/m³%n", 
    lng.getTemperature() - 273.15, density);
```

### Density at Different Temperatures

```java
// Temperature sweep
double[] temperatures = {-165, -162, -160, -155, -150};  // °C

for (double T : temperatures) {
    lng.setTemperature(T + 273.15);
    iso6578.calculate();
    double rho = iso6578.getValue("density", "kg/m3");
    System.out.printf("T = %.0f°C: ρ = %.2f kg/m³%n", T, rho);
}
```

### Rich LNG (High Ethane/Propane)

```java
// Rich LNG composition
SystemInterface richLNG = new SystemSrkEos(273.15 - 162, 1.01325);
richLNG.addComponent("methane", 0.8500);
richLNG.addComponent("ethane", 0.0900);
richLNG.addComponent("propane", 0.0400);
richLNG.addComponent("n-butane", 0.0100);
richLNG.addComponent("nitrogen", 0.0100);
richLNG.setMixingRule("classic");

Standard_ISO6578 standard = new Standard_ISO6578(richLNG);
standard.calculate();

double density = standard.getValue("density", "kg/m3");
System.out.printf("Rich LNG Density = %.2f kg/m³%n", density);

// Rich LNG has higher density due to heavier components
```

### Comparing Correction Factor Options

```java
// Using ISO 6578 correction factors (default)
Standard_ISO6578 iso_factors = new Standard_ISO6578(lng);
iso_factors.useISO6578VolumeCorrectionFacotrs(true);
iso_factors.calculate();
double rho_iso = iso_factors.getValue("density", "kg/m3");

// Using alternative correction factors
Standard_ISO6578 alt_factors = new Standard_ISO6578(lng);
alt_factors.useISO6578VolumeCorrectionFacotrs(false);
alt_factors.calculate();
double rho_alt = alt_factors.getValue("density", "kg/m3");

System.out.printf("ISO 6578 factors: %.2f kg/m³%n", rho_iso);
System.out.printf("Alternative factors: %.2f kg/m³%n", rho_alt);
```

---

## Correction Factors

### Klosek-McKinley Correction Factor Tables

The implementation includes two sets of correction factors:

#### ISO 6578 Factors

Temperature range: 93.15 K to 133.15 K (-180°C to -140°C)
Molar mass range: 16 to 30 g/mol

```
Temperatures: {93.15, 98.15, 103.15, 108.15, 113.15, 118.15, 123.15, 128.15, 133.15} K
Molar Masses: {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30} g/mol
```

#### Alternative Factors

Temperature range: 105 K to 135 K
Molar mass range: 16 to 25 g/mol

### Interpolation

Bicubic interpolation is used for:
- $k_1$ correction factor (nitrogen effect)
- $k_2$ correction factor (methane effect)

Linear interpolation for pure component molar volumes.

---

## Component Data

### Supported Components

| Component | Formula | Molar Volume Data |
|-----------|---------|-------------------|
| Methane | CH₄ | Yes |
| Ethane | C₂H₆ | Yes |
| Propane | C₃H₈ | Yes |
| n-Butane | C₄H₁₀ | Yes |
| i-Butane | C₄H₁₀ | Yes |
| n-Pentane | C₅H₁₂ | Yes |
| i-Pentane | C₅H₁₂ | Yes |
| n-Hexane | C₆H₁₄ | Yes |
| Nitrogen | N₂ | Yes |

### Molar Volume Data

Pure component molar volumes at reference temperatures:

```
Temperatures: {93.15, 98.15, 103.15, 108.15, 113.15, 118.15, 123.15, 128.15, 133.15} K

Example - Methane (dm³/mol):
{0.035771, 0.036315, 0.036891, 0.037500, 0.038149, 0.038839, 0.039580, 0.040375, 0.041237}
```

---

## Typical Results

### Lean LNG (High Methane)

| Composition | Value |
|-------------|-------|
| Methane | 95% |
| Ethane | 3% |
| Others | 2% |
| **Density at -162°C** | **425-435 kg/m³** |

### Standard LNG

| Composition | Value |
|-------------|-------|
| Methane | 90-92% |
| Ethane | 5-6% |
| Propane | 2% |
| Others | 1-2% |
| **Density at -162°C** | **440-460 kg/m³** |

### Rich LNG (High C2+)

| Composition | Value |
|-------------|-------|
| Methane | 85% |
| Ethane | 9% |
| Propane | 4% |
| Others | 2% |
| **Density at -162°C** | **470-490 kg/m³** |

---

## Accuracy Considerations

### Temperature Sensitivity

LNG density is strongly temperature dependent:
- Approximately -1 to -2 kg/m³ per °C increase

### Composition Sensitivity

- Higher C2+ content → higher density
- Higher nitrogen content → lower density

### Uncertainty

Typical uncertainty: ±0.1% for well-characterized LNG

---

## References

1. ISO 6578:2017 - Refrigerated hydrocarbon liquids — Static measurement — Calculation procedure
2. Klosek, J., McKinley, C. (1968). Densities of Liquefied Natural Gas and of Low Molecular Weight Hydrocarbons. Proceedings of the First International Conference on LNG.
3. GIIGNL Custody Transfer Handbook (5th Edition)
