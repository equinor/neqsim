---
title: "ISO 6976 - Calorific Values from Composition"
description: "ISO 6976 provides methods for calculating calorific values, density, relative density, and Wobbe indices from natural gas composition."
---

# ISO 6976 - Calorific Values from Composition

ISO 6976 provides methods for calculating calorific values, density, relative density, and Wobbe indices from natural gas composition.

## Table of Contents
- [Overview](#overview)
- [Calculated Properties](#calculated-properties)
- [Reference Conditions](#reference-conditions)
- [Implementation](#implementation)
- [Usage Examples](#usage-examples)
- [Return Parameters](#return-parameters)
- [Component Data](#component-data)
- [Versions](#versions)

---

## Overview

**Standard:** ISO 6976:2016 (and earlier editions)

**Purpose:** Calculate physical properties of natural gas from composition analysis without direct measurement.

**Scope:**
- Gross and net calorific values (GCV/LCV)
- Wobbe indices (superior/inferior)
- Relative density
- Compressibility factor
- Gas density

**Classes:**
- `Standard_ISO6976` - ISO 6976:1995 edition
- `Standard_ISO6976_2016` - ISO 6976:2016 edition

---

## Calculated Properties

### Calorific Values

**Gross Calorific Value (GCV)** - Also called Higher Heating Value (HHV):
$$H_s = \sum_i x_i \cdot H_{s,i}^{id}$$

**Net Calorific Value (LCV)** - Also called Lower Heating Value (LHV):
$$H_i = \sum_i x_i \cdot H_{i,i}^{id}$$

where:
- $x_i$ = mole fraction of component i
- $H_{s,i}^{id}$ = ideal molar superior calorific value of component i
- $H_{i,i}^{id}$ = ideal molar inferior calorific value of component i

### Wobbe Index

The Wobbe index indicates interchangeability of fuel gases:

$$W_s = \frac{H_s}{\sqrt{d}}$$

where $d$ is the relative density.

### Compressibility Factor

Mixture compressibility at reference conditions:

$$Z_{mix} = 1 - \left(\sum_i x_i \sqrt{b_i}\right)^2$$

where $b_i$ is the summation factor for component i.

### Relative Density

$$d = \frac{\rho_{gas}}{\rho_{air}} = \frac{M_{gas}}{M_{air}} \cdot \frac{Z_{air}}{Z_{gas}}$$

---

## Reference Conditions

### Temperature Options

| Reference T | Description |
|-------------|-------------|
| 0°C (273.15 K) | European standard |
| 15°C (288.15 K) | ISO standard / metric |
| 15.55°C (60°F) | US/UK standard |
| 20°C (293.15 K) | Engineering standard |
| 25°C (298.15 K) | Thermochemical standard |

### Reference State

- **Real gas**: Uses compressibility factor $Z$ from ISO 6976 correlation
- **Ideal gas**: Assumes $Z = 1$

### Reference Type

- **Volume** (`"volume"`): kJ/m³ - Standard for gas sales
- **Mass** (`"mass"`): kJ/kg - Useful for comparisons
- **Molar** (`"molar"`): kJ/mol - Thermodynamic basis

---

## Implementation

### Constructor

```java
// Basic constructor (uses default reference conditions)
Standard_ISO6976 standard = new Standard_ISO6976(thermoSystem);

// Full constructor with reference conditions
Standard_ISO6976 standard = new Standard_ISO6976(
    thermoSystem,                    // SystemInterface
    volumeRefT,                      // Volume reference T [°C]
    energyRefT,                      // Energy reference T [°C]
    calculationType                  // "volume", "mass", or "molar"
);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform calculations |
| `getValue(param)` | Get calculated value |
| `getValue(param, unit)` | Get value with unit conversion |
| `setReferenceState(state)` | Set "real" or "ideal" |
| `setReferenceType(type)` | Set "volume", "mass", or "molar" |
| `setVolRefT(T)` | Set volume reference temperature |
| `setEnergyRefT(T)` | Set energy reference temperature |
| `getAverageCarbonNumber()` | Average C number of mixture |

### Internal Data

Component properties are loaded from database table `ISO6976constants`:
- Molar mass
- Compressibility factors at 0, 15, 20°C
- Summation factors for Z calculation
- Superior/inferior calorific values at reference temperatures

---

## Usage Examples

### Basic Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create natural gas composition
SystemInterface gas = new SystemSrkEos(273.15 + 15, 1.01325);
gas.addComponent("methane", 0.9248);
gas.addComponent("ethane", 0.0350);
gas.addComponent("propane", 0.0098);
gas.addComponent("n-butane", 0.0022);
gas.addComponent("i-butane", 0.0034);
gas.addComponent("n-pentane", 0.0006);
gas.addComponent("i-pentane", 0.0005);
gas.addComponent("nitrogen", 0.0107);
gas.addComponent("CO2", 0.0130);
gas.setMixingRule("classic");

// Flash to ensure single phase
ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

// Create ISO 6976 standard
Standard_ISO6976 iso6976 = new Standard_ISO6976(gas, 15, 15, "volume");
iso6976.setReferenceState("real");

// Calculate
iso6976.calculate();

// Get results
System.out.println("=== ISO 6976 Results (15°C/15°C, real gas) ===");
System.out.printf("GCV = %.2f kJ/m³%n", iso6976.getValue("GCV"));
System.out.printf("LCV = %.2f kJ/m³%n", iso6976.getValue("LCV"));
System.out.printf("Wobbe Index = %.2f kJ/m³%n", iso6976.getValue("SuperiorWobbeIndex"));
System.out.printf("Relative Density = %.5f%n", iso6976.getValue("RelativeDensity"));
System.out.printf("Z (compressibility) = %.6f%n", iso6976.getValue("CompressionFactor"));
System.out.printf("Molar Mass = %.4f g/mol%n", iso6976.getValue("MolarMass"));
System.out.printf("Density (real) = %.4f kg/m³%n", iso6976.getValue("DensityReal"));
```

### Different Reference Conditions

```java
// Standard conditions for US market (60°F)
Standard_ISO6976 us_standard = new Standard_ISO6976(gas, 15.55, 15.55, "volume");
us_standard.setReferenceState("real");
us_standard.calculate();
System.out.printf("GCV (60°F) = %.2f kJ/m³%n", us_standard.getValue("GCV"));

// European conditions (0°C metering, 25°C combustion)
Standard_ISO6976 eu_standard = new Standard_ISO6976(gas, 0, 25, "volume");
eu_standard.setReferenceState("real");
eu_standard.calculate();
System.out.printf("GCV (0°C/25°C) = %.2f kJ/m³%n", eu_standard.getValue("GCV"));

// Mass-based calorific value
Standard_ISO6976 mass_standard = new Standard_ISO6976(gas, 15, 25, "mass");
mass_standard.calculate();
System.out.printf("GCV (mass) = %.2f kJ/kg%n", mass_standard.getValue("GCV"));
```

### Using in kWh

```java
// Get Wobbe index in kWh/m³
double wobbeKWh = iso6976.getValue("SuperiorWobbeIndex", "kWh");
System.out.printf("Wobbe Index = %.4f kWh/m³%n", wobbeKWh);
```

### With Pseudo-Components (TBP Fractions)

```java
// Gas with heavy ends characterized as TBP fraction
SystemInterface richGas = new SystemSrkEos(273.15 + 15, 1.01325);
richGas.addComponent("methane", 0.85);
richGas.addComponent("ethane", 0.05);
richGas.addComponent("propane", 0.03);
richGas.addTBPfraction("C7+", 0.02, 100.0/1000.0, 0.75);  // MW=100, SG=0.75
richGas.addComponent("CO2", 0.03);
richGas.addComponent("nitrogen", 0.02);
richGas.setMixingRule("classic");

Standard_ISO6976 standard = new Standard_ISO6976(richGas, 15, 15, "volume");
standard.calculate();

// Note: TBP fractions are approximated using n-heptane properties
double gcv = standard.getValue("GCV");
```

### Display Results Table

```java
// Create formatted table
String[][] table = iso6976.createTable("ISO 6976 Results");

// Or display in GUI
iso6976.display();
```

---

## Return Parameters

| Parameter | Aliases | Description |
|-----------|---------|-------------|
| `SuperiorCalorificValue` | `GCV` | Gross calorific value |
| `InferiorCalorificValue` | `LCV` | Net calorific value |
| `SuperiorWobbeIndex` | `WI` | Superior Wobbe index |
| `InferiorWobbeIndex` | - | Inferior Wobbe index |
| `RelativeDensity` | - | Relative density (air=1) |
| `CompressionFactor` | - | Compressibility factor Z |
| `MolarMass` | - | Average molar mass [g/mol] |
| `DensityIdeal` | - | Ideal gas density [kg/m³] |
| `DensityReal` | - | Real gas density [kg/m³] |

### Unit Options

For calorific values and Wobbe index:
- Default: kJ per reference unit (m³, kg, or mol)
- `"kWh"`: Converts kJ to kWh (divides by 3600)

---

## Component Data

### Supported Components

The standard includes data for:
- Alkanes: methane through n-decane, iso-butane, iso-pentane, neopentane
- Other hydrocarbons: ethene, propene, benzene
- Inerts: nitrogen, carbon dioxide, hydrogen sulfide, helium, argon
- Other: hydrogen, carbon monoxide, water

### Handling Unknown Components

Components not in the ISO 6976 database are approximated:

| Component Type | Approximated As |
|----------------|-----------------|
| HC, TBP, plus | n-heptane |
| alcohol, glycol | methanol |
| Other | inert (N₂) |

Check for approximations:
```java
ArrayList<String> unknowns = standard.getComponentsNotDefinedByStandard();
if (!unknowns.isEmpty()) {
    System.out.println("Warning: Components approximated: " + unknowns);
}
```

---

## Versions

### Standard_ISO6976 (1995 Edition)

Original implementation based on ISO 6976:1995.

### Standard_ISO6976_2016 (2016 Edition)

Updated implementation with:
- Revised component data
- Additional temperature point (60°F / 15.55°C)
- Updated air compressibility values
- Uses database table `iso6976constants2016`

```java
import neqsim.standards.gasquality.Standard_ISO6976_2016;

Standard_ISO6976_2016 iso2016 = new Standard_ISO6976_2016(gas, 15, 25, "volume");
iso2016.calculate();
double gcv = iso2016.getValue("GCV");
```

---

## Validation

### Reference Air Properties

| Temperature | Z_air (1995) | Z_air (2016) |
|-------------|--------------|--------------|
| 0°C | 0.99941 | 0.999419 |
| 15°C | 0.99958 | 0.999595 |
| 20°C | 0.99963 | 0.999645 |
| 60°F | - | 0.999601 |

### Typical Results for Dry Gas

For a typical North Sea gas (mostly methane):
- GCV: ~37,000-40,000 kJ/m³
- Wobbe Index: ~48,000-52,000 kJ/m³
- Relative Density: ~0.58-0.62

---

## References

1. ISO 6976:2016 - Natural gas — Calculation of calorific values, density, relative density and Wobbe indices from composition
2. ISO 6976:1995 - Natural gas — Calculation of calorific values, density and Wobbe index from composition
3. GPA 2172 - Calculation of Gross Heating Value, Relative Density and Compressibility Factor for Natural Gas Mixtures from Compositional Analysis
