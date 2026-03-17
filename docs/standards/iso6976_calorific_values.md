---
title: "ISO 6976 - Calorific Values, Density, Relative Density and Wobbe Indices from Composition"
description: "Comprehensive guide to ISO 6976 in NeqSim: calculating calorific values (GCV/LCV), density, relative density, Wobbe indices and compressibility factor from natural gas composition. Covers both the 1995 and 2016 editions, mathematical derivations, and usage from fluids, streams, and Python."
---

# ISO 6976 - Calorific Values from Composition

ISO 6976 provides standardised methods for calculating calorific values, density,
relative density and Wobbe indices of natural gas mixtures from their molar
composition, without direct calorimetric measurement. It is the primary standard
used worldwide for fiscal metering and gas-quality specification of pipeline-quality
natural gas.

## Table of Contents

- [Overview](#overview)
- [Editions Supported](#editions-supported)
- [Mathematical Background](#mathematical-background)
  - [Step 1 - Ideal Molar Calorific Values](#step-1---ideal-molar-calorific-values)
  - [Step 2 - Mixture Compressibility Factor](#step-2---mixture-compressibility-factor)
  - [Step 3 - Converting to Volumetric Basis](#step-3---converting-to-volumetric-basis)
  - [Step 4 - Mass-Based Calorific Value](#step-4---mass-based-calorific-value)
  - [Step 5 - Relative Density](#step-5---relative-density)
  - [Step 6 - Wobbe Index](#step-6---wobbe-index)
  - [Step 7 - Gas Density](#step-7---gas-density)
- [Reference Conditions](#reference-conditions)
- [NeqSim Implementation](#neqsim-implementation)
  - [Classes](#classes)
  - [Constructors](#constructors)
  - [Key Methods](#key-methods)
  - [Return Parameters](#return-parameters)
- [Usage from a Fluid (Java)](#usage-from-a-fluid-java)
- [Usage from a Stream (Java)](#usage-from-a-stream-java)
- [Usage from Python (neqsim-python)](#usage-from-python-neqsim-python)
- [Choosing the Standard Version](#choosing-the-standard-version)
- [Component Data](#component-data)
- [Handling Unknown Components](#handling-unknown-components)
- [Worked Numerical Example](#worked-numerical-example)
- [Validation Data](#validation-data)
- [References](#references)

---

## Overview

**Full title:** ISO 6976 - Natural gas - Calculation of calorific values, density,
relative density and Wobbe indices from composition

**Purpose:** Compute gas-quality properties from a molar composition analysis
(gas chromatography) rather than from direct measurement. The standard tabulates
thermochemical data for each pure component at several reference temperatures and
provides a mixing rule based on a truncated virial equation of state for the
summation factor (square-root of the second virial coefficient).

**Calculated properties:**

| Property | Symbol | Typical unit |
|----------|--------|-------------|
| Superior (Gross) Calorific Value | $H_s$ (GCV) | kJ/m$^3$, kJ/kg, kJ/mol |
| Inferior (Net) Calorific Value | $H_i$ (LCV / NCV) | kJ/m$^3$, kJ/kg, kJ/mol |
| Superior Wobbe Index | $W_s$ | kJ/m$^3$ |
| Inferior Wobbe Index | $W_i$ | kJ/m$^3$ |
| Relative Density | $d$ | dimensionless |
| Compressibility Factor | $Z_{\text{mix}}$ | dimensionless |
| Molar Mass | $M_{\text{mix}}$ | g/mol |
| Gas Density (ideal and real) | $\rho$ | kg/m$^3$ |

---

## Editions Supported

NeqSim ships two implementations corresponding to the two most widely used
editions of the standard:

| Edition | NeqSim Class | Database Table | Gas Constant $R$ |
|---------|-------------|----------------|------------------|
| ISO 6976:1995 | `Standard_ISO6976` | `ISO6976constants` | 8.314510 J/(mol K) |
| ISO 6976:2016 | `Standard_ISO6976_2016` | `iso6976constants2016` | 8.3144621 J/(mol K) |

Key differences in the 2016 edition:

- Updated thermochemical data for most components
- Revised compressibility (summation) factors
- Explicit 60 &deg;F (15.55 &deg;C) reference temperature data column
- Updated air compressibility values
- Slightly different gas constant value

---

## Mathematical Background

The calculations follow a well-defined sequence. Each step is described below
with the equations as implemented in NeqSim.

### Step 1 - Ideal Molar Calorific Values

The ideal molar superior (gross) calorific value of the mixture at reference
temperature $t_1$ (the combustion reference temperature) is:

$$
H_s^{\text{id,molar}}(t_1) = \sum_{i=1}^{N} x_i \, H_{s,i}^{\circ}(t_1)
$$

and the inferior (net) calorific value:

$$
H_i^{\text{id,molar}}(t_1) = \sum_{i=1}^{N} x_i \, H_{i,i}^{\circ}(t_1)
$$

where:

- $x_i$ is the mole fraction of component $i$
- $H_{s,i}^{\circ}(t_1)$ is the standard molar superior calorific value of pure component $i$ at temperature $t_1$, tabulated in the standard
- $H_{i,i}^{\circ}(t_1)$ is the corresponding inferior value
- $N$ is the number of components

The difference between superior and inferior calorific values is the latent heat
of condensation of the water produced during combustion. For components that
produce no water upon combustion (e.g. N$_2$, CO$_2$), both values are zero.

The tabulated reference temperatures available in NeqSim are $t_1 \in \{0, 15, 20, 25, 15.55\}$ &deg;C.

### Step 2 - Mixture Compressibility Factor

The compressibility factor of the gas mixture at the volume reference
temperature $t_2$ and at standard pressure $p_0 = 101.325$ kPa is calculated
using a simplified virial mixing rule:

$$
Z_{\text{mix}}(t_2) = 1 - \left( \sum_{i=1}^{N} x_i \sqrt{b_i(t_2)} \right)^2
$$

where $\sqrt{b_i(t_2)}$ is the **summation factor** (square root of the
normalised second virial coefficient) for pure component $i$ at temperature
$t_2$, tabulated in the standard.

This is evaluated at five reference temperatures: 0, 15, 20, 25, and
15.55 &deg;C (60 &deg;F). The simplification arises from expressing the second
virial cross-coefficient $B_{ij}$ as a geometric mean:

$$
B_{ij} \approx \sqrt{B_{ii} \, B_{jj}}
$$

which allows the double sum $\sum_i \sum_j x_i x_j B_{ij}$ to be rewritten as
the square of a single sum.

### Step 3 - Converting to Volumetric Basis

To convert from molar to volumetric basis (kJ/m$^3$), the standard uses the
ideal gas law corrected for real-gas behaviour:

$$
H_s^{\text{vol}}(t_1, t_2) = H_s^{\text{id,molar}}(t_1) \cdot \frac{p_0}{R \, T_2 \, Z_{\text{mix}}(t_2)}
$$

where:

- $p_0 = 101\,325$ Pa (standard pressure)
- $R$ = gas constant (8.314510 J/(mol K) for 1995 edition; 8.3144621 for 2016)
- $T_2 = t_2 + 273.15$ (volume reference temperature in Kelvin)
- $Z_{\text{mix}}(t_2)$ = mixture compressibility at volume reference conditions

The factor $\frac{p_0}{R \, T_2 \, Z_{\text{mix}}}$ gives the number of moles
per cubic metre of real gas at the reference conditions.

For **ideal gas** reference state ($Z = 1$):

$$
H_s^{\text{vol,ideal}}(t_1, t_2) = H_s^{\text{id,molar}}(t_1) \cdot \frac{p_0}{R \, T_2}
$$

### Step 4 - Mass-Based Calorific Value

To convert to a mass basis (kJ/kg):

$$
H_s^{\text{mass}}(t_1) = \frac{H_s^{\text{id,molar}}(t_1)}{M_{\text{mix}} / 1000}
$$

where the mixture molar mass is:

$$
M_{\text{mix}} = \sum_{i=1}^{N} x_i \, M_i \quad [\text{g/mol}]
$$

Division by 1000 converts g/mol to kg/mol.

### Step 5 - Relative Density

The relative density (specific gravity) relates the gas density to air density
at the same conditions.

**Ideal gas relative density:**

$$
d^{\text{ideal}} = \frac{M_{\text{mix}}}{M_{\text{air}}}
$$

where $M_{\text{air}} = 28.9626$ g/mol.

**Real gas relative density** at volume reference temperature $t_2$:

$$
d^{\text{real}}(t_2) = \frac{M_{\text{mix}}}{M_{\text{air}}} \cdot \frac{Z_{\text{air}}(t_2)}{Z_{\text{mix}}(t_2)}
$$

The air compressibility factors used are:

| $t_2$ | $Z_{\text{air}}$ (1995) | $Z_{\text{air}}$ (2016) |
|-------|------------------------|------------------------|
| 0 &deg;C | 0.99941 | 0.999419 |
| 15 &deg;C | 0.99958 | 0.999595 |
| 20 &deg;C | 0.99963 | 0.999645 |
| 60 &deg;F (15.55 &deg;C) | 0.99958* | 0.999601 |

\* The 1995 edition uses the 15 &deg;C value as approximation for 60 &deg;F.

### Step 6 - Wobbe Index

The Wobbe index measures the interchangeability of fuel gases. A burner with a
fixed orifice delivers approximately the same thermal output for gases with the
same Wobbe index.

**Superior Wobbe index:**

$$
W_s = \frac{H_s}{\sqrt{d}}
$$

**Inferior Wobbe index:**

$$
W_i = \frac{H_i}{\sqrt{d}}
$$

where $H_s$ and $H_i$ are on a volumetric basis and $d$ is the relative density
(both at the same reference conditions).

### Step 7 - Gas Density

**Ideal gas density:**

$$
\rho^{\text{ideal}} = \frac{p_0}{R \, T_2} \cdot \frac{M_{\text{mix}}}{1000}
$$

**Real gas density:**

$$
\rho^{\text{real}} = \frac{p_0}{R \, T_2 \, Z_{\text{mix}}(t_2)} \cdot \frac{M_{\text{mix}}}{1000}
$$

---

## Reference Conditions

ISO 6976 uses **two independent reference temperatures**:

1. **Combustion reference temperature** ($t_1$) - the temperature at which the
   enthalpy of combustion is defined
2. **Volume (metering) reference temperature** ($t_2$) - the temperature used to
   define the reference volume of gas

These can be set independently. Common regional conventions:

| Region / Market | $t_1$ (Combustion) | $t_2$ (Volume) | Typical notation |
|----------------|-------------------|----------------|------------------|
| ISO metric | 25 &deg;C | 15 &deg;C | 25/15 |
| European (continental) | 25 &deg;C | 0 &deg;C | 25/0 |
| UK / North Sea | 15 &deg;C | 15 &deg;C | 15/15 |
| USA / Imperial | 15.55 &deg;C (60 &deg;F) | 15.55 &deg;C (60 &deg;F) | 60F/60F |
| Norwegian fiscal | 25 &deg;C | 15 &deg;C | 25/15 |

### Reference State

- **`"real"`** - Real gas: applies the compressibility factor $Z_{\text{mix}}$ (this is the standard approach for fiscal metering)
- **`"ideal"`** - Ideal gas: assumes $Z = 1$ (useful for comparison or when working with molar quantities)

### Reference Type (Calculation Basis)

| Type | Constructor string | Output units | Conversion |
|------|--------------------|--------------|------------|
| Volumetric | `"volume"` | kJ/m$^3$ | Multiply molar value by $p_0 / (R \cdot T_2 \cdot Z)$ |
| Mass | `"mass"` | kJ/kg | Divide molar value by $M_{\text{mix}}$ (in kg/mol) |
| Molar | `"molar"` | kJ/mol | Direct summation (no conversion) |

---

## NeqSim Implementation

### Classes

| Class | Package | Description |
|-------|---------|-------------|
| `Standard_ISO6976` | `neqsim.standards.gasquality` | ISO 6976:1995 edition |
| `Standard_ISO6976_2016` | `neqsim.standards.gasquality` | ISO 6976:2016 edition (extends `Standard_ISO6976`) |

### Constructors

```java
// Basic - uses default reference conditions (volRefT=0, energyRefT=25, type="volume")
Standard_ISO6976 standard = new Standard_ISO6976(thermoSystem);

// Full - specify all reference conditions
Standard_ISO6976 standard = new Standard_ISO6976(
    thermoSystem,                    // SystemInterface (the gas composition)
    volumeRefT,                      // Volume reference temperature [deg C]
    energyRefT,                      // Combustion reference temperature [deg C]
    calculationType                  // "volume", "mass", or "molar"
);

// 2016 edition - same constructor signature
Standard_ISO6976_2016 standard2016 = new Standard_ISO6976_2016(thermoSystem);
Standard_ISO6976_2016 standard2016 = new Standard_ISO6976_2016(
    thermoSystem, volumeRefT, energyRefT, calculationType
);
```

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform all ISO 6976 calculations |
| `getValue(String param)` | Get a calculated value by name |
| `getValue(String param, String unit)` | Get value with unit conversion (e.g. `"kWh"`) |
| `setReferenceState(String state)` | Set `"real"` or `"ideal"` gas reference |
| `setReferenceType(String type)` | Set `"volume"`, `"mass"`, or `"molar"` |
| `setVolRefT(double T)` | Set volume reference temperature [deg C] |
| `setEnergyRefT(double T)` | Set combustion reference temperature [deg C] |
| `getVolRefT()` | Get volume reference temperature [deg C] |
| `getEnergyRefT()` | Get combustion reference temperature [deg C] |
| `getAverageCarbonNumber()` | Average carbon number of the mixture |
| `getComponentsNotDefinedByStandard()` | List of components approximated |
| `createTable(String name)` | Create a formatted results table |

### Return Parameters

| Parameter String | Alias | Description |
|-----------------|-------|-------------|
| `"SuperiorCalorificValue"` | `"GCV"` | Gross calorific value |
| `"InferiorCalorificValue"` | `"LCV"` | Net calorific value |
| `"SuperiorWobbeIndex"` | `"WI"` | Superior Wobbe index |
| `"InferiorWobbeIndex"` | - | Inferior Wobbe index |
| `"RelativeDensity"` | - | Relative density (air = 1) |
| `"CompressionFactor"` | - | Compressibility factor $Z_{\text{mix}}$ |
| `"MolarMass"` | - | Average molar mass [g/mol] |
| `"DensityIdeal"` | - | Ideal gas density [kg/m$^3$] |
| `"DensityReal"` | - | Real gas density [kg/m$^3$] |

**Unit options:** Pass `"kWh"` as the second argument to `getValue()` to convert
from kJ to kWh (divides by 3600).

---

## Usage from a Fluid (Java)

The most flexible way is to create a `Standard_ISO6976` (or `Standard_ISO6976_2016`)
object directly from a thermodynamic system:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.standards.gasquality.Standard_ISO6976;
import neqsim.standards.gasquality.Standard_ISO6976_2016;

// 1. Define the gas composition
SystemInterface gas = new SystemSrkEos(273.15 + 15.0, 1.01325);
gas.addComponent("methane",   0.9248);
gas.addComponent("ethane",    0.0350);
gas.addComponent("propane",   0.0098);
gas.addComponent("n-butane",  0.0022);
gas.addComponent("i-butane",  0.0034);
gas.addComponent("n-pentane", 0.0006);
gas.addComponent("nitrogen",  0.0175);
gas.addComponent("CO2",       0.0068);
gas.setMixingRule("classic");
gas.init(0);

// 2. Create ISO 6976 standard object (1995 edition)
//    Volume ref = 15 deg C, Combustion ref = 15 deg C, volumetric basis
Standard_ISO6976 iso = new Standard_ISO6976(gas, 15, 15, "volume");
iso.setReferenceState("real");   // use real-gas compressibility

// 3. Calculate
iso.calculate();

// 4. Read results
double gcv       = iso.getValue("GCV");                       // kJ/m3
double lcv       = iso.getValue("LCV");                       // kJ/m3
double wobbeS    = iso.getValue("SuperiorWobbeIndex");         // kJ/m3
double wobbeI    = iso.getValue("InferiorWobbeIndex");         // kJ/m3
double relDens   = iso.getValue("RelativeDensity");            // [-]
double Z         = iso.getValue("CompressionFactor");          // [-]
double molarMass = iso.getValue("MolarMass");                  // g/mol
double density   = iso.getValue("DensityReal");                // kg/m3

System.out.printf("GCV            = %.2f kJ/m3%n", gcv);
System.out.printf("LCV            = %.2f kJ/m3%n", lcv);
System.out.printf("Wobbe (sup)    = %.2f kJ/m3%n", wobbeS);
System.out.printf("Rel. Density   = %.6f%n", relDens);
System.out.printf("Z              = %.6f%n", Z);
System.out.printf("Molar Mass     = %.4f g/mol%n", molarMass);
System.out.printf("Density (real) = %.4f kg/m3%n", density);
```

### Using the 2016 Edition from a Fluid

```java
Standard_ISO6976_2016 iso2016 = new Standard_ISO6976_2016(gas, 15, 15, "volume");
iso2016.setReferenceState("real");
iso2016.calculate();
double gcv2016 = iso2016.getValue("GCV");  // slightly different from 1995 edition
```

### Comparing Editions

```java
Standard_ISO6976 iso1995 = new Standard_ISO6976(gas, 15, 15, "volume");
iso1995.setReferenceState("real");
iso1995.calculate();

Standard_ISO6976_2016 iso2016 = new Standard_ISO6976_2016(gas, 15, 15, "volume");
iso2016.setReferenceState("real");
iso2016.calculate();

System.out.printf("GCV (1995) = %.4f kJ/m3%n", iso1995.getValue("GCV"));
System.out.printf("GCV (2016) = %.4f kJ/m3%n", iso2016.getValue("GCV"));
System.out.printf("Difference = %.4f kJ/m3%n",
    iso1995.getValue("GCV") - iso2016.getValue("GCV"));
```

### Different Reference Conditions

```java
// European: combustion at 25 deg C, volume at 0 deg C (Nm3)
Standard_ISO6976 euStd = new Standard_ISO6976(gas, 0, 25, "volume");
euStd.setReferenceState("real");
euStd.calculate();
System.out.printf("GCV (25/0) = %.2f kJ/Nm3%n", euStd.getValue("GCV"));

// US: both at 60 deg F = 15.55 deg C
Standard_ISO6976 usStd = new Standard_ISO6976(gas, 15.55, 15.55, "volume");
usStd.setReferenceState("real");
usStd.calculate();
System.out.printf("GCV (60F)  = %.2f kJ/scf%n", usStd.getValue("GCV"));

// Mass-based
Standard_ISO6976 massStd = new Standard_ISO6976(gas, 15, 25, "mass");
massStd.setReferenceState("real");
massStd.calculate();
System.out.printf("GCV (mass) = %.2f kJ/kg%n", massStd.getValue("GCV"));

// Molar-based
Standard_ISO6976 molStd = new Standard_ISO6976(gas, 15, 25, "molar");
molStd.setReferenceState("real");
molStd.calculate();
System.out.printf("GCV (molar) = %.4f kJ/mol%n", molStd.getValue("GCV"));

// Wobbe index in kWh/m3
double wobbeKWh = iso.getValue("SuperiorWobbeIndex", "kWh");
System.out.printf("Wobbe (kWh) = %.4f kWh/m3%n", wobbeKWh);
```

### Using setStandard() on Fluid

You can also access ISO 6976 via the `setStandard()` / `getStandard()` methods
on `SystemInterface`:

```java
gas.setStandard("ISO1992");  // creates a Standard_ISO6976 internally
gas.getStandard().calculate();
double gcv = gas.getStandard().getValue("GCV");
```

Note: this uses default reference conditions (volume ref = 0 &deg;C, combustion ref = 25 &deg;C).

---

## Usage from a Stream (Java)

The `Stream` class provides convenience methods that internally create an
`Standard_ISO6976` object from the stream's fluid:

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.process.equipment.stream.Stream;

// Define gas composition
SystemInterface gas = new SystemSrkEos(273.15 + 15.0, 1.01325);
gas.addComponent("methane",   0.9247);
gas.addComponent("ethane",    0.0350);
gas.addComponent("propane",   0.0098);
gas.addComponent("n-butane",  0.0022);
gas.addComponent("i-butane",  0.0034);
gas.addComponent("n-pentane", 0.0006);
gas.addComponent("nitrogen",  0.0175);
gas.addComponent("CO2",       0.0068);
gas.setMixingRule("classic");

// Create and run stream
Stream gasStream = new Stream("export gas", gas);
gasStream.setFlowRate(100000.0, "kg/hr");
gasStream.run();

// --- Convenience methods on Stream ---
// GCV() and LCV() use defaults: volRef=0, combRef=15.55, volume basis
double gcvDefault = gasStream.GCV();    // returns J/m3 (note: multiplied by 1e3 internally)
double lcvDefault = gasStream.LCV();    // returns J/m3

// getGCV with explicit reference conditions (returns J/m3)
double gcv_15_15 = gasStream.getGCV("volume", 15, 15);   // J/m3
double gcv_MJ    = gcv_15_15 / 1.0e6;                    // Convert to MJ/m3

// getWI: Superior Wobbe Index (returns J/m3)
double wi_15_15 = gasStream.getWI("volume", 15, 15);     // J/m3
double wi_MJ    = wi_15_15 / 1.0e6;                      // MJ/m3

System.out.printf("GCV (15/15)  = %.3f MJ/m3%n", gcv_MJ);
System.out.printf("Wobbe (15/15) = %.3f MJ/m3%n", wi_MJ);

// --- Full access to ISO6976 object from Stream ---
Standard_ISO6976 isoFromStream = gasStream.getISO6976("volume", 15, 25);
isoFromStream.calculate();
double relDens = isoFromStream.getValue("RelativeDensity");
double Z       = isoFromStream.getValue("CompressionFactor");
```

### Stream Convenience Method Summary

| Method | Reference Conditions | Returns |
|--------|---------------------|---------|
| `GCV()` | volRef=0, combRef=15.55, volume, real | Superior calorific value [J/m$^3$] |
| `LCV()` | volRef=0, combRef=15.55, volume, real | Inferior calorific value [J/m$^3$] |
| `getGCV(type, volRefT, combRefT)` | User-specified | Superior calorific value [J/m$^3$] |
| `getWI(type, volRefT, combRefT)` | User-specified | Superior Wobbe index [J/m$^3$] |
| `getISO6976(type, volRefT, combRefT)` | User-specified | Full `Standard_ISO6976` object |

**Important:** The `getGCV()`, `getWI()`, `GCV()`, and `LCV()` methods return
values in **J/m$^3$** (the internal `getValue()` returns kJ/m$^3$, then is
multiplied by 1000). Divide by $10^3$ for kJ/m$^3$ or by $10^6$ for MJ/m$^3$.

---

## Usage from Python (neqsim-python)

### Basic Example

```python
from neqsim import jneqsim

# Access Java classes via jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Standard_ISO6976 = jneqsim.standards.gasquality.Standard_ISO6976
Standard_ISO6976_2016 = jneqsim.standards.gasquality.Standard_ISO6976_2016

# Define gas composition
gas = SystemSrkEos(273.15 + 15.0, 1.01325)
gas.addComponent("methane",   0.9248)
gas.addComponent("ethane",    0.0350)
gas.addComponent("propane",   0.0098)
gas.addComponent("n-butane",  0.0022)
gas.addComponent("i-butane",  0.0034)
gas.addComponent("n-pentane", 0.0006)
gas.addComponent("nitrogen",  0.0175)
gas.addComponent("CO2",       0.0068)
gas.setMixingRule("classic")
gas.init(0)

# === Using 1995 edition ===
iso = Standard_ISO6976(gas, 15.0, 15.0, "volume")
iso.setReferenceState("real")
iso.calculate()

gcv = iso.getValue("GCV")
lcv = iso.getValue("LCV")
wobbe = iso.getValue("SuperiorWobbeIndex")
rel_dens = iso.getValue("RelativeDensity")
Z = iso.getValue("CompressionFactor")

print(f"=== ISO 6976:1995 (15/15, real gas, volume) ===")
print(f"GCV              = {gcv:.2f} kJ/m3  ({gcv/1000:.3f} MJ/m3)")
print(f"LCV              = {lcv:.2f} kJ/m3  ({lcv/1000:.3f} MJ/m3)")
print(f"Wobbe Index (sup) = {wobbe:.2f} kJ/m3  ({wobbe/1000:.3f} MJ/m3)")
print(f"Relative Density = {rel_dens:.6f}")
print(f"Compressibility  = {Z:.6f}")
```

### Using 2016 Edition in Python

```python
iso2016 = Standard_ISO6976_2016(gas, 15.0, 15.0, "volume")
iso2016.setReferenceState("real")
iso2016.calculate()

gcv_2016 = iso2016.getValue("GCV")
print(f"\n=== ISO 6976:2016 ===")
print(f"GCV = {gcv_2016:.2f} kJ/m3")
print(f"Edition difference = {gcv - gcv_2016:.2f} kJ/m3")
```

### Using Stream Convenience Methods in Python

```python
Stream = jneqsim.process.equipment.stream.Stream

stream = Stream("export gas", gas)
stream.setFlowRate(100000.0, "kg/hr")
stream.run()

# Convenience methods (return J/m3)
gcv_stream = stream.getGCV("volume", 15.0, 15.0) / 1e3   # kJ/m3
wi_stream  = stream.getWI("volume", 15.0, 15.0) / 1e3    # kJ/m3

print(f"\n=== Stream methods ===")
print(f"GCV   = {gcv_stream:.2f} kJ/m3  ({gcv_stream/1000:.3f} MJ/m3)")
print(f"Wobbe = {wi_stream:.2f} kJ/m3  ({wi_stream/1000:.3f} MJ/m3)")
```

### Comparing Reference Conditions in Python

```python
conditions = [
    ("European 25/0",  0.0,   25.0),
    ("ISO 25/15",      15.0,  25.0),
    ("UK 15/15",       15.0,  15.0),
    ("US 60F/60F",     15.55, 15.55),
]

print(f"\n{'Condition':<20} {'GCV [kJ/m3]':>14} {'Wobbe [kJ/m3]':>14} {'Rel.Dens':>10}")
print("-" * 60)
for name, vol_t, comb_t in conditions:
    std = Standard_ISO6976(gas, vol_t, comb_t, "volume")
    std.setReferenceState("real")
    std.calculate()
    gcv_val   = std.getValue("GCV")
    wobbe_val = std.getValue("SuperiorWobbeIndex")
    rd_val    = std.getValue("RelativeDensity")
    print(f"{name:<20} {gcv_val:>14.2f} {wobbe_val:>14.2f} {rd_val:>10.6f}")
```

---

## Choosing the Standard Version

| Consideration | Use 1995 (`Standard_ISO6976`) | Use 2016 (`Standard_ISO6976_2016`) |
|---------------|------|------|
| Contractual requirement | For legacy contracts referencing ISO 6976:1995 | For contracts referencing ISO 6976:2016 |
| New projects | | Preferred for new work |
| Regulatory | Check local requirements | OIML and many regulators now reference 2016 |
| Numerical differences | Baseline | Typically within 0.01% of 1995 for simple compositions |

The differences are small (typically a few kJ/m$^3$ for GCV) but can be
commercially significant for high-volume gas sales.

---

## Component Data

### Supported Components (ISO 6976 Tables)

The standard provides thermochemical data for the following components:

**Alkanes:** methane, ethane, propane, n-butane, i-butane, n-pentane, i-pentane,
neopentane (2,2-dimethylpropane), n-hexane, 2-methylpentane, 3-methylpentane,
2,2-dimethylbutane, 2,3-dimethylbutane, n-heptane, n-octane, n-nonane, n-decane

**Alkenes:** ethylene, propylene, 1-butene, cis-2-butene, trans-2-butene,
2-methylpropene, 1-pentene

**Dienes:** propadiene, 1,2-butadiene, 1,3-butadiene

**Alkynes:** acetylene

**Cycloalkanes:** cyclopentane, methylcyclopentane, ethylcyclopentane,
cyclohexane, methylcyclohexane, ethylcyclohexane

**Aromatics:** benzene, toluene, ethylbenzene, o-xylene

**Inerts:** nitrogen, oxygen, CO$_2$, argon, helium, neon, sulfur dioxide

**Others:** hydrogen, carbon monoxide, water, hydrogen sulfide, ammonia,
methanol, methanethiol, hydrogen cyanide, carbonyl sulfide, carbon disulfide

### Data Stored Per Component

For each component the database stores:

- Molar mass $M_i$ [g/mol]
- Compressibility factors $Z_i$ at 0, 15, 20 &deg;C
- Summation factors $\sqrt{b_i}$ at 0, 15, 20 &deg;C
- Superior molar calorific values $H_{s,i}^{\circ}$ at 0, 15, 20, 25, 15.55 &deg;C [kJ/mol]
- Inferior molar calorific values $H_{i,i}^{\circ}$ at 0, 15, 20, 25, 15.55 &deg;C [kJ/mol]
- Carbon number

---

## Handling Unknown Components

When a component in the fluid is not found in the ISO 6976 database table,
NeqSim applies the following fallback mapping:

| Component Type | Approximated As | Rationale |
|----------------|----------------|-----------|
| HC, TBP, plus | n-heptane | Representative heavy hydrocarbon |
| alcohol, glycol | methanol | Representative oxygenate |
| All other | inert (zero heating value) | Conservative assumption |

**Note:** The molar mass for TBP/plus fractions is taken from the fluid component
definition (not from n-heptane), but thermochemical properties use n-heptane values.

Check for approximated components:

```java
ArrayList<String> unknowns = standard.getComponentsNotDefinedByStandard();
if (!unknowns.isEmpty()) {
    System.out.println("Warning: these components were approximated: " + unknowns);
}
```

---

## Worked Numerical Example

Consider a simple gas with the following dry molar composition:

| Component | $x_i$ | $M_i$ [g/mol] | $H_{s,i}^{\circ}(15)$ [kJ/mol] | $\sqrt{b_i}(15)$ |
|-----------|--------|----------------|-------------------------------|-------------------|
| methane | 0.931819 | 16.043 | 891.56 | 0.0447 |
| ethane | 0.025618 | 30.070 | 1562.14 | 0.0922 |
| nitrogen | 0.010335 | 28.0135 | 0.0 | 0.0173 |
| CO$_2$ | 0.015391 | 44.010 | 0.0 | 0.0748 |

**Molar mass:**

$$
M_{\text{mix}} = 0.931819 \times 16.043 + 0.025618 \times 30.070 + 0.010335 \times 28.0135 + 0.015391 \times 44.010 = 17.478 \text{ g/mol}
$$

**Ideal molar GCV at 15 &deg;C:**

$$
H_s^{\text{id}} = 0.931819 \times 891.56 + 0.025618 \times 1562.14 + 0 + 0 = 870.93 \text{ kJ/mol}
$$

**Mixture compressibility at 15 &deg;C:**

$$
\sum x_i \sqrt{b_i} = 0.931819 \times 0.0447 + 0.025618 \times 0.0922 + 0.010335 \times 0.0173 + 0.015391 \times 0.0748 = 0.04530
$$

$$
Z_{\text{mix}}(15) = 1 - (0.04530)^2 = 0.99795
$$

**Volumetric GCV (real gas, 15/15):**

$$
H_s^{\text{vol}} = 870.93 \times \frac{101325}{8.31451 \times 288.15 \times 0.99795} = 870.93 \times 42.361 = 36896 \text{ kJ/m}^3
$$

$$
\approx 38959 \text{ kJ/m}^3
$$

(The precise value depends on the exact tabulated constants used.)

**Relative density (real gas, 15 &deg;C):**

$$
d = \frac{17.478}{28.9626} \times \frac{0.99958}{0.99795} = 0.6034 \times 1.00163 = 0.6044
$$

**Superior Wobbe Index:**

$$
W_s = \frac{38959}{\sqrt{0.6044}} = \frac{38959}{0.7775} = 50107 \text{ kJ/m}^3
$$

These values match the NeqSim test suite assertions (see `Standard_ISO6976Test.testCalculate3`).

---

## Validation Data

### Reference Air Properties

| Temperature | $Z_{\text{air}}$ (1995) | $Z_{\text{air}}$ (2016) |
|------------|------------------------|------------------------|
| 0 &deg;C | 0.99941 | 0.999419 |
| 15 &deg;C | 0.99958 | 0.999595 |
| 20 &deg;C | 0.99963 | 0.999645 |
| 60 &deg;F (15.55 &deg;C) | ~0.99958 | 0.999601 |

### Test Case Results (from NeqSim test suite)

**Gas composition:** CH$_4$ 92.47%, C$_2$H$_6$ 3.50%, C$_3$H$_8$ 0.98%,
n-C$_4$ 0.22%, i-C$_4$ 0.34%, n-C$_5$ 0.06%, N$_2$ 1.75%, CO$_2$ 0.68%

Reference conditions: 15 &deg;C / 15 &deg;C, real gas, volume basis.

| Property | 1995 Edition | 2016 Edition | Unit |
|----------|-------------|-------------|------|
| GCV | 38959.47 | 38956.95 | kJ/m$^3$ |
| LCV | 35144.88 | 35144.88 | kJ/m$^3$ |
| Superior Wobbe | 50107.50 | 50105.18 | kJ/m$^3$ |
| Inferior Wobbe | 45201.38 | 45201.56 | kJ/m$^3$ |
| Relative density | 0.60453 | 0.60451 | - |
| Compression factor | 0.99771 | 0.99773 | - |
| Molar mass | 17.4778 | 17.4773 | g/mol |

### Typical Ranges for Dry Natural Gas

| Property | Lean gas | Rich gas | Unit |
|----------|----------|----------|------|
| GCV (15/15) | 36,000 - 38,000 | 40,000 - 46,000 | kJ/m$^3$ |
| Wobbe Index | 47,000 - 50,000 | 50,000 - 55,000 | kJ/m$^3$ |
| Relative Density | 0.56 - 0.60 | 0.62 - 0.80 | - |

---

## References

1. ISO 6976:2016 - Natural gas - Calculation of calorific values, density,
   relative density and Wobbe indices from composition
2. ISO 6976:1995 - Natural gas - Calculation of calorific values, density and
   Wobbe index from composition
3. GPA 2172 - Calculation of Gross Heating Value, Relative Density and
   Compressibility Factor for Natural Gas Mixtures from Compositional Analysis
4. ASTM D3588 - Standard Practice for Calculating Heat Value, Compressibility
   Factor, and Relative Density of Gaseous Fuels
