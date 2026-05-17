---
title: "JSON Fluid File Format and Import into NeqSim"
description: "Guide to the NeqSim JSON fluid format (.json), supported fields, and how to read, create, write, and convert fluids using JsonFluidReadWrite. Covers E300 conversion, pseudo-component handling, BIC specification, viscosity models, and round-trip workflows."
---

## Overview

NeqSim provides a **JSON-based fluid format** (`neqsim-fluid`) that offers full EOS-level fidelity equivalent to the [Eclipse E300 format](eclipse_e300_fluid_import.md), but in a structured, human-readable, and machine-friendly format.

The JSON format supports all parameters needed to exactly reproduce a PVT-tuned fluid:

- Equation of state selection (SRK, PR, PR1978)
- Component critical properties ($T_c$, $P_c$, $\omega$, $MW$, $T_{bp}$, $V_c$)
- Volume translation (shift) parameters
- Parachor parameters for IFT calculations
- Binary interaction coefficients ($k_{ij}$)
- Viscosity model configuration (LBC coefficients or Pedersen/PFCT)
- Pseudo-component (TBP fraction) definitions with density
- Water handling with configurable $k_{ij}$

The Java class that handles JSON fluids is `neqsim.thermo.util.readwrite.JsonFluidReadWrite`.

### JSON vs E300 Format Comparison

| Feature                  | E300 Format                               | JSON Format                                         |
| ------------------------ | ----------------------------------------- | --------------------------------------------------- |
| Human readable           | Partially (keyword blocks)                | Fully (structured key-value)                        |
| Machine parseable        | Custom parser required                    | Standard JSON parsers                               |
| BIC specification        | Lower triangular matrix (positional)      | Named component pairs                               |
| Component properties     | Separate keyword blocks per property      | All properties grouped per component                |
| Viscosity model          | Separate keywords (`LBCCOEF`, `PEDERSEN`) | Nested object with type and coefficients            |
| Metadata                 | Comments only                             | Structured metadata (version, generator, timestamp) |
| Interoperability         | Eclipse/PVTsim ecosystem                  | Any JSON-capable tool, AI agents, REST APIs         |
| Bidirectional conversion | Via `EclipseFluidReadWrite`               | Via `JsonFluidReadWrite.convertE300ToJson()`        |

## JSON Fluid Format Reference

### Top-Level Structure

```json
{
  "format": "neqsim-fluid",
  "version": "1.0",
  "generatedBy": "NeqSim",
  "generatedAt": "2026-03-05 14:00:00",
  "eos": "SRK",
  "prcorr": false,
  "reservoirTemperature": { "value": 90.0, "unit": "C" },
  "standardConditions": { "temperature": 15.0, "pressure": 1.01325 },
  "components": [ ... ],
  "binaryInteractionCoefficients": [ ... ],
  "viscosityModel": { ... }
}
```

### Field Reference

| Field                           | Type    | Required | Description                                                   |
| ------------------------------- | ------- | -------- | ------------------------------------------------------------- |
| `format`                        | string  | No       | Format identifier: `"neqsim-fluid"`                           |
| `version`                       | string  | No       | Format version: `"1.0"`                                       |
| `generatedBy`                   | string  | No       | Tool that generated the file                                  |
| `generatedAt`                   | string  | No       | Generation timestamp                                          |
| `eos`                           | string  | No       | Equation of state: `"SRK"` (default) or `"PR"`                |
| `prcorr`                        | boolean | No       | If true with `"eos": "PR"`, uses PR1978 alpha function        |
| `reservoirTemperature`          | object  | No       | Reservoir temperature with `value` and `unit`                 |
| `standardConditions`            | object  | No       | Standard conditions: `temperature` (°C) and `pressure` (bara) |
| `components`                    | array   | **Yes**  | Array of component objects (at least one required)            |
| `binaryInteractionCoefficients` | array   | No       | Array of BIC entries                                          |
| `viscosityModel`                | object  | No       | Viscosity model configuration                                 |

### Component Object Fields

Each entry in the `components` array is an object with these fields:

| Field                 | Type    | Required | Unit    | Description                                                  |
| --------------------- | ------- | -------- | ------- | ------------------------------------------------------------ |
| `name`                | string  | **Yes**  | —       | Component name (NeqSim database name or E300 shorthand)      |
| `moleFraction`        | number  | **Yes**  | —       | Overall mole fraction ($z_i$)                                |
| `criticalTemperature` | number  | No       | K       | Critical temperature ($T_c$)                                 |
| `criticalPressure`    | number  | No       | bar     | Critical pressure ($P_c$)                                    |
| `acentricFactor`      | number  | No       | —       | Acentric factor ($\omega$)                                   |
| `molarMass`           | number  | No       | g/mol   | Molecular weight ($MW$)                                      |
| `normalBoilingPoint`  | number  | No       | K       | Normal boiling point ($T_{bp}$)                              |
| `criticalVolume`      | number  | No       | m³/kmol | Critical volume ($V_c$)                                      |
| `volumeShift`         | number  | No       | —       | Volume translation parameter (SSHIFT)                        |
| `volumeShiftSurface`  | number  | No       | —       | Volume shift at surface conditions (SSHIFTS, takes priority) |
| `parachor`            | number  | No       | dyn/cm  | Parachor for IFT calculation                                 |
| `isPseudo`            | boolean | No       | —       | If true, added as TBP pseudo-fraction                        |
| `density`             | number  | No       | kg/m³   | Standard liquid density (for pseudo-components)              |

### Component Name Mapping

Both NeqSim database names and E300 shorthand names are accepted in the `name` field:

| Input Name            | Maps to NeqSim Name   | Type                          |
| --------------------- | --------------------- | ----------------------------- |
| `methane` or `C1`     | methane               | Database                      |
| `ethane` or `C2`      | ethane                | Database                      |
| `propane` or `C3`     | propane               | Database                      |
| `i-butane` or `iC4`   | i-butane              | Database                      |
| `n-butane` or `C4`    | n-butane              | Database                      |
| `i-pentane` or `iC5`  | i-pentane             | Database                      |
| `n-pentane` or `C5`   | n-pentane             | Database                      |
| `n-hexane` or `C6`    | n-hexane              | Database                      |
| `nitrogen` or `N2`    | nitrogen              | Database                      |
| `CO2`                 | CO2                   | Database                      |
| `water` or `H2O`      | water                 | Database                      |
| `H2S`                 | H2S                   | Database                      |
| `C7`, `C10-C12`, etc. | Added as TBP fraction | Pseudo (set `isPseudo: true`) |

### Binary Interaction Coefficients

The `binaryInteractionCoefficients` array uses named component pairs, making it far more readable than the positional lower-triangular matrix in E300 format:

```json
"binaryInteractionCoefficients": [
  { "i": "nitrogen", "j": "methane", "kij": -0.0170 },
  { "i": "nitrogen", "j": "CO2", "kij": 0.0311 },
  { "i": "CO2", "j": "methane", "kij": 0.1200 }
]
```

| Field | Type   | Description                                      |
| ----- | ------ | ------------------------------------------------ |
| `i`   | string | First component name                             |
| `j`   | string | Second component name                            |
| `kij` | number | Binary interaction parameter ($k_{ij} = k_{ji}$) |

Only non-zero $k_{ij}$ values need to be listed. The matrix is symmetric — specifying $(i, j)$ automatically sets $(j, i)$.

### Viscosity Model

The `viscosityModel` object specifies the viscosity correlation:

**LBC (Lorentz-Bray-Clark):**

```json
"viscosityModel": {
  "type": "LBC",
  "coefficients": [0.1023, 0.023364, 0.058533, -0.040758, 0.0093324]
}
```

**Pedersen (PFCT corresponding states):**

```json
"viscosityModel": {
  "type": "PEDERSEN"
}
```

| Field          | Type   | Description                                              |
| -------------- | ------ | -------------------------------------------------------- |
| `type`         | string | `"LBC"` or `"PEDERSEN"` (also accepts `"PFCT"`)          |
| `coefficients` | array  | 5 LBC dense-fluid contribution parameters (only for LBC) |

### Pseudo-Component Density

For pseudo-components (`isPseudo: true`), if no `density` is provided, the standard liquid density is estimated from molecular weight using:

$$
\rho_{std} = 0.5046 \times \frac{MW}{1000} + 0.668468
$$

### Equation of State Selection

| `eos` Value | `prcorr` | NeqSim Class      | Description                            |
| ----------- | -------- | ----------------- | -------------------------------------- |
| `"SRK"`     | —        | `SystemSrkEos`    | Soave-Redlich-Kwong (default)          |
| `"PR"`      | `false`  | `SystemPrEos`     | Original Peng-Robinson (1976)          |
| `"PR"`      | `true`   | `SystemPrEos1978` | Peng-Robinson with 1978 alpha function |

## Complete Example JSON File

Below is a complete JSON fluid file for the **same 22-component oil** shown in the [E300 example](eclipse_e300_fluid_import.md#example-e300-file), using Peng-Robinson EOS with LBC viscosity:

```json
{
  "format": "neqsim-fluid",
  "version": "1.0",
  "generatedBy": "NeqSim",
  "eos": "PR",
  "prcorr": true,
  "reservoirTemperature": { "value": 90.0, "unit": "C" },
  "standardConditions": { "temperature": 15.0, "pressure": 1.01325 },
  "components": [
    {
      "name": "nitrogen",
      "moleFraction": 0.003912,
      "criticalTemperature": 126.200,
      "criticalPressure": 33.9439,
      "acentricFactor": 0.04000,
      "molarMass": 28.014,
      "normalBoilingPoint": 77.4,
      "criticalVolume": 0.0895,
      "volumeShift": -0.175888,
      "parachor": 41.0,
      "isPseudo": false
    },
    {
      "name": "CO2",
      "moleFraction": 0.003010,
      "criticalTemperature": 304.200,
      "criticalPressure": 73.7646,
      "acentricFactor": 0.22500,
      "molarMass": 44.010,
      "normalBoilingPoint": 194.7,
      "criticalVolume": 0.0940,
      "volumeShift": -0.049181,
      "parachor": 78.0,
      "isPseudo": false
    },
    {
      "name": "methane",
      "moleFraction": 0.403275,
      "criticalTemperature": 190.600,
      "criticalPressure": 46.0015,
      "acentricFactor": 0.00800,
      "molarMass": 16.043,
      "normalBoilingPoint": 111.6,
      "criticalVolume": 0.0986,
      "volumeShift": -0.194020,
      "parachor": 77.3,
      "isPseudo": false
    },
    {
      "name": "ethane",
      "moleFraction": 0.076341,
      "criticalTemperature": 305.400,
      "criticalPressure": 48.8387,
      "acentricFactor": 0.09800,
      "molarMass": 30.070,
      "normalBoilingPoint": 184.6,
      "criticalVolume": 0.1455,
      "volumeShift": -0.143142,
      "parachor": 108.9,
      "isPseudo": false
    },
    {
      "name": "propane",
      "moleFraction": 0.079752,
      "criticalTemperature": 369.800,
      "criticalPressure": 42.4552,
      "acentricFactor": 0.15200,
      "molarMass": 44.097,
      "normalBoilingPoint": 231.1,
      "criticalVolume": 0.2000,
      "volumeShift": -0.112702,
      "parachor": 151.9,
      "isPseudo": false
    },
    {
      "name": "i-butane",
      "moleFraction": 0.011938,
      "criticalTemperature": 408.100,
      "criticalPressure": 36.4770,
      "acentricFactor": 0.17600,
      "molarMass": 58.124,
      "normalBoilingPoint": 261.4,
      "criticalVolume": 0.2627,
      "volumeShift": -0.099214,
      "parachor": 181.5,
      "isPseudo": false
    },
    {
      "name": "n-butane",
      "moleFraction": 0.040929,
      "criticalTemperature": 425.200,
      "criticalPressure": 37.9969,
      "acentricFactor": 0.19300,
      "molarMass": 58.124,
      "normalBoilingPoint": 272.7,
      "criticalVolume": 0.2550,
      "volumeShift": -0.089659,
      "parachor": 191.7,
      "isPseudo": false
    },
    {
      "name": "i-pentane",
      "moleFraction": 0.013944,
      "criticalTemperature": 460.400,
      "criticalPressure": 33.8426,
      "acentricFactor": 0.22700,
      "molarMass": 72.151,
      "normalBoilingPoint": 301.0,
      "criticalVolume": 0.3060,
      "volumeShift": -0.070455,
      "parachor": 225.0,
      "isPseudo": false
    },
    {
      "name": "n-pentane",
      "moleFraction": 0.021568,
      "criticalTemperature": 469.600,
      "criticalPressure": 33.7412,
      "acentricFactor": 0.25100,
      "molarMass": 72.151,
      "normalBoilingPoint": 309.2,
      "criticalVolume": 0.3040,
      "volumeShift": -0.056872,
      "parachor": 233.9,
      "isPseudo": false
    },
    {
      "name": "n-hexane",
      "moleFraction": 0.027988,
      "criticalTemperature": 507.400,
      "criticalPressure": 29.6882,
      "acentricFactor": 0.29600,
      "molarMass": 86.178,
      "normalBoilingPoint": 341.9,
      "criticalVolume": 0.3700,
      "volumeShift": 0.012573,
      "parachor": 271.0,
      "isPseudo": false
    },
    {
      "name": "C7",
      "moleFraction": 0.042936,
      "criticalTemperature": 548.083,
      "criticalPressure": 29.4519,
      "acentricFactor": 0.33744,
      "molarMass": 96.0,
      "normalBoilingPoint": 366.0,
      "criticalVolume": 0.392,
      "volumeShift": 0.074067,
      "parachor": 283.94,
      "isPseudo": true,
      "density": 717.0
    },
    {
      "name": "C8",
      "moleFraction": 0.043237,
      "criticalTemperature": 568.470,
      "criticalPressure": 27.6423,
      "acentricFactor": 0.37547,
      "molarMass": 107.0,
      "normalBoilingPoint": 390.0,
      "criticalVolume": 0.421,
      "volumeShift": 0.085121,
      "parachor": 309.68,
      "isPseudo": true,
      "density": 722.5
    },
    {
      "name": "C9",
      "moleFraction": 0.030898,
      "criticalTemperature": 592.686,
      "criticalPressure": 25.5535,
      "acentricFactor": 0.42325,
      "molarMass": 121.0,
      "normalBoilingPoint": 416.0,
      "criticalVolume": 0.456,
      "volumeShift": 0.081268,
      "parachor": 342.44,
      "isPseudo": true,
      "density": 729.6
    },
    {
      "name": "C10-C12",
      "moleFraction": 0.043939,
      "criticalTemperature": 631.845,
      "criticalPressure": 22.7296,
      "acentricFactor": 0.50535,
      "molarMass": 148.0,
      "normalBoilingPoint": 457.0,
      "criticalVolume": 0.523,
      "volumeShift": 0.069060,
      "parachor": 402.209,
      "isPseudo": true,
      "density": 743.2
    },
    {
      "name": "C13-C14",
      "moleFraction": 0.045143,
      "criticalTemperature": 680.299,
      "criticalPressure": 20.0143,
      "acentricFactor": 0.61393,
      "molarMass": 190.0,
      "normalBoilingPoint": 507.0,
      "criticalVolume": 0.610,
      "volumeShift": 0.048755,
      "parachor": 485.824,
      "isPseudo": true,
      "density": 764.4
    },
    {
      "name": "C15-C17",
      "moleFraction": 0.022571,
      "criticalTemperature": 727.035,
      "criticalPressure": 18.1224,
      "acentricFactor": 0.72473,
      "molarMass": 237.0,
      "normalBoilingPoint": 557.0,
      "criticalVolume": 0.716,
      "volumeShift": 0.018239,
      "parachor": 577.490,
      "isPseudo": true,
      "density": 788.1
    },
    {
      "name": "C18-C21",
      "moleFraction": 0.025180,
      "criticalTemperature": 774.284,
      "criticalPressure": 16.7108,
      "acentricFactor": 0.83712,
      "molarMass": 291.0,
      "normalBoilingPoint": 607.0,
      "criticalVolume": 0.834,
      "volumeShift": -0.017443,
      "parachor": 679.641,
      "isPseudo": true,
      "density": 815.3
    },
    {
      "name": "C22-C28",
      "moleFraction": 0.021188,
      "criticalTemperature": 851.846,
      "criticalPressure": 15.1759,
      "acentricFactor": 1.00708,
      "molarMass": 384.0,
      "normalBoilingPoint": 685.0,
      "criticalVolume": 1.020,
      "volumeShift": -0.077518,
      "parachor": 866.931,
      "isPseudo": true,
      "density": 862.3
    },
    {
      "name": "C29-C36",
      "moleFraction": 0.014111,
      "criticalTemperature": 943.373,
      "criticalPressure": 14.0297,
      "acentricFactor": 1.15740,
      "molarMass": 510.0,
      "normalBoilingPoint": 774.0,
      "criticalVolume": 1.270,
      "volumeShift": -0.156174,
      "parachor": 1111.372,
      "isPseudo": true,
      "density": 925.8
    },
    {
      "name": "C37-C45",
      "moleFraction": 0.012845,
      "criticalTemperature": 1038.592,
      "criticalPressure": 13.2891,
      "acentricFactor": 1.21951,
      "molarMass": 656.0,
      "normalBoilingPoint": 860.0,
      "criticalVolume": 1.585,
      "volumeShift": -0.235730,
      "parachor": 1387.629,
      "isPseudo": true,
      "density": 999.4
    },
    {
      "name": "C46-C58",
      "moleFraction": 0.008955,
      "criticalTemperature": 1152.236,
      "criticalPressure": 12.7370,
      "acentricFactor": 1.23925,
      "molarMass": 849.0,
      "normalBoilingPoint": 957.0,
      "criticalVolume": 1.984,
      "volumeShift": -0.320950,
      "parachor": 1739.859,
      "isPseudo": true,
      "density": 1096.8
    },
    {
      "name": "C59-C80",
      "moleFraction": 0.006340,
      "criticalTemperature": 1317.304,
      "criticalPressure": 12.2645,
      "acentricFactor": 1.21155,
      "molarMass": 1160.0,
      "normalBoilingPoint": 1090.0,
      "criticalVolume": 2.663,
      "volumeShift": -0.420868,
      "parachor": 2282.641,
      "isPseudo": true,
      "density": 1254.0
    }
  ],
  "binaryInteractionCoefficients": [
    { "i": "nitrogen", "j": "CO2", "kij": -0.0170 },
    { "i": "nitrogen", "j": "methane", "kij": 0.0311 },
    { "i": "nitrogen", "j": "ethane", "kij": 0.0515 },
    { "i": "nitrogen", "j": "propane", "kij": 0.0852 },
    { "i": "nitrogen", "j": "i-butane", "kij": 0.0800 },
    { "i": "nitrogen", "j": "n-butane", "kij": 0.0800 },
    { "i": "nitrogen", "j": "i-pentane", "kij": 0.1000 },
    { "i": "nitrogen", "j": "n-pentane", "kij": 0.1000 },
    { "i": "nitrogen", "j": "n-hexane", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C7", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C8", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C9", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C10-C12", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C13-C14", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C15-C17", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C18-C21", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C22-C28", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C29-C36", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C37-C45", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C46-C58", "kij": 0.1000 },
    { "i": "nitrogen", "j": "C59-C80", "kij": 0.1000 },
    { "i": "CO2", "j": "methane", "kij": 0.1200 },
    { "i": "CO2", "j": "ethane", "kij": 0.1200 },
    { "i": "CO2", "j": "propane", "kij": 0.1200 },
    { "i": "CO2", "j": "i-butane", "kij": 0.1200 },
    { "i": "CO2", "j": "n-butane", "kij": 0.1200 },
    { "i": "CO2", "j": "i-pentane", "kij": 0.1200 },
    { "i": "CO2", "j": "n-pentane", "kij": 0.1200 },
    { "i": "CO2", "j": "n-hexane", "kij": 0.1200 },
    { "i": "CO2", "j": "C7", "kij": 0.1200 },
    { "i": "CO2", "j": "C8", "kij": 0.1200 },
    { "i": "CO2", "j": "C9", "kij": 0.1200 },
    { "i": "CO2", "j": "C10-C12", "kij": 0.1200 },
    { "i": "CO2", "j": "C13-C14", "kij": 0.1200 },
    { "i": "CO2", "j": "C15-C17", "kij": 0.1200 },
    { "i": "CO2", "j": "C18-C21", "kij": 0.1200 },
    { "i": "CO2", "j": "C22-C28", "kij": 0.1200 },
    { "i": "CO2", "j": "C29-C36", "kij": 0.1200 },
    { "i": "CO2", "j": "C37-C45", "kij": 0.1200 },
    { "i": "CO2", "j": "C46-C58", "kij": 0.1200 },
    { "i": "CO2", "j": "C59-C80", "kij": 0.1200 }
  ],
  "viscosityModel": {
    "type": "LBC",
    "coefficients": [0.1023000, 0.0233640, 0.0585330, -0.0407580, 0.0093324]
  }
}
```

This JSON file is fully equivalent to the E300 file shown in the [Eclipse E300 documentation](eclipse_e300_fluid_import.md#example-e300-file). Note how:

- Each component has all its properties grouped together (vs. separate keyword blocks in E300)
- Binary interaction coefficients use named pairs (vs. positional lower-triangular matrix in E300)
- Pseudo-components are explicitly marked with `"isPseudo": true`

## Reading JSON Fluid Files in NeqSim

### Java API

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.readwrite.JsonFluidReadWrite;

// Read from file
SystemInterface fluid = JsonFluidReadWrite.read("path/to/fluid.json");

// Read with water addition (kij = 0.5 for water vs all components)
SystemInterface fluid = JsonFluidReadWrite.read("path/to/fluid.json", true);

// Read with water and custom kij
SystemInterface fluid = JsonFluidReadWrite.read("path/to/fluid.json", true, 0.45);

// Read from a JSON string (e.g., from an API response or embedded data)
String jsonContent = "{ \"eos\": \"SRK\", \"components\": [...] }";
SystemInterface fluid = JsonFluidReadWrite.readString(jsonContent);

// Read from string with water
SystemInterface fluid = JsonFluidReadWrite.readString(jsonContent, true);
```

After reading, the fluid is ready for thermodynamic calculations:

```java
// Set conditions
fluid.setPressure(100.0, "bara");
fluid.setTemperature(90.0, "C");

// Run flash calculation
neqsim.thermodynamicoperations.ThermodynamicOperations ops =
    new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid);
ops.TPflash();

// Get results
System.out.println("Number of phases: " + fluid.getNumberOfPhases());
System.out.println("Gas density: " + fluid.getPhase("gas").getDensity("kg/m3"));
```

### Python API (neqsim-python)

```python
from neqsim import jneqsim

JsonFluidReadWrite = jneqsim.thermo.util.readwrite.JsonFluidReadWrite

# Read from file
fluid = JsonFluidReadWrite.read('path/to/fluid.json')

# Read with water
fluid = JsonFluidReadWrite.read('path/to/fluid.json', True)

# Read from JSON string
import json
with open('fluid.json', 'r') as f:
    json_str = f.read()
fluid = JsonFluidReadWrite.readString(json_str)

# Set conditions and flash
fluid.setPressure(100.0, 'bara')
fluid.setTemperature(90.0, 'C')

from neqsim.thermo.thermoTools import TPflash, printFrame
TPflash(fluid)
printFrame(fluid)
```

## Writing JSON Fluid Files from NeqSim

### Java API

```java
import neqsim.thermo.util.readwrite.JsonFluidReadWrite;

// Write fluid to JSON file
JsonFluidReadWrite.write(fluid, "output.json");

// Write with reservoir temperature
JsonFluidReadWrite.write(fluid, "output.json", 120.0);  // 120 °C

// Get JSON content as string (for logging, APIs, or embedding)
String jsonContent = JsonFluidReadWrite.toJsonString(fluid);
System.out.println(jsonContent);

// Get JSON string with specific reservoir temperature
String jsonContent = JsonFluidReadWrite.toJsonString(fluid, 90.0);
```

### Python API

```python
from neqsim import jneqsim

JsonFluidReadWrite = jneqsim.thermo.util.readwrite.JsonFluidReadWrite

# Write to file
JsonFluidReadWrite.write(fluid, 'exported_fluid.json')

# Write with reservoir temperature
JsonFluidReadWrite.write(fluid, 'exported_fluid.json', 120.0)

# Get as JSON string
json_string = str(JsonFluidReadWrite.toJsonString(fluid))
print(json_string)
```

## Converting Between E300 and JSON Formats

`JsonFluidReadWrite` provides direct conversion methods between E300 and JSON:

### Java API

```java
import neqsim.thermo.util.readwrite.JsonFluidReadWrite;

// E300 to JSON
JsonFluidReadWrite.convertE300ToJson("input.e300", "output.json");

// E300 to JSON with reservoir temperature
JsonFluidReadWrite.convertE300ToJson("input.e300", "output.json", 90.0);

// JSON to E300
JsonFluidReadWrite.convertJsonToE300("input.json", "output.e300");

// JSON to E300 with reservoir temperature
JsonFluidReadWrite.convertJsonToE300("input.json", "output.e300", 90.0);
```

### Python API

```python
from neqsim import jneqsim

JsonFluidReadWrite = jneqsim.thermo.util.readwrite.JsonFluidReadWrite

# E300 to JSON
JsonFluidReadWrite.convertE300ToJson('reservoir_fluid.e300', 'reservoir_fluid.json')

# JSON to E300
JsonFluidReadWrite.convertJsonToE300('reservoir_fluid.json', 'reservoir_fluid.e300')
```

### Round-Trip Verification

Verify that JSON read/write preserves all EOS parameters:

```java
// Read from JSON
SystemInterface original = JsonFluidReadWrite.read("fluid.json");

// Write to new file
JsonFluidReadWrite.write(original, "roundtrip.json", 90.0);

// Read back and compare
SystemInterface roundtripped = JsonFluidReadWrite.read("roundtrip.json");

for (int i = 0; i < original.getNumberOfComponents(); i++) {
    System.out.printf("%-12s  Tc=%.3f  Pc=%.4f  w=%.5f  MW=%.3f  shift=%.6f%n",
        original.getComponent(i).getComponentName(),
        original.getComponent(i).getTC(),
        original.getComponent(i).getPC(),
        original.getComponent(i).getAcentricFactor(),
        original.getComponent(i).getMolarMass() * 1000,
        original.getComponent(i).getVolumeCorrectionConst());
}
```

## Workflow: Building a JSON Fluid Programmatically

You can create a JSON fluid definition as a string and pass it directly to NeqSim, which is useful for web services, REST APIs, or AI-generated workflows:

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import neqsim.thermo.util.readwrite.JsonFluidReadWrite;

// Build JSON object
JsonObject root = new JsonObject();
root.addProperty("eos", "SRK");

JsonArray components = new JsonArray();

JsonObject methane = new JsonObject();
methane.addProperty("name", "methane");
methane.addProperty("moleFraction", 0.85);
methane.addProperty("criticalTemperature", 190.564);
methane.addProperty("criticalPressure", 45.99);
methane.addProperty("acentricFactor", 0.0115);
methane.addProperty("molarMass", 16.043);
methane.addProperty("normalBoilingPoint", 111.632);
methane.addProperty("criticalVolume", 0.0986);
methane.addProperty("volumeShift", -0.154);
methane.addProperty("parachor", 77.3);
components.add(methane);

JsonObject ethane = new JsonObject();
ethane.addProperty("name", "ethane");
ethane.addProperty("moleFraction", 0.15);
ethane.addProperty("criticalTemperature", 305.32);
ethane.addProperty("criticalPressure", 48.72);
ethane.addProperty("acentricFactor", 0.0995);
ethane.addProperty("molarMass", 30.07);
ethane.addProperty("normalBoilingPoint", 184.55);
ethane.addProperty("criticalVolume", 0.1455);
ethane.addProperty("volumeShift", -0.1002);
ethane.addProperty("parachor", 112.91);
components.add(ethane);

root.add("components", components);

// Create fluid from JSON
SystemInterface fluid = JsonFluidReadWrite.readString(root.toString());
```

## Minimal JSON Examples

### Simple Gas

A minimal two-component gas — only `components` is required:

```json
{
  "eos": "SRK",
  "components": [
    {
      "name": "methane",
      "moleFraction": 0.95,
      "criticalTemperature": 190.564,
      "criticalPressure": 45.99,
      "acentricFactor": 0.0115,
      "molarMass": 16.043,
      "normalBoilingPoint": 111.632,
      "criticalVolume": 0.0986,
      "volumeShift": -0.154,
      "parachor": 77.3
    },
    {
      "name": "CO2",
      "moleFraction": 0.05,
      "criticalTemperature": 304.2,
      "criticalPressure": 73.76,
      "acentricFactor": 0.225,
      "molarMass": 44.01,
      "normalBoilingPoint": 194.7,
      "criticalVolume": 0.094,
      "volumeShift": -0.049,
      "parachor": 78.0
    }
  ]
}
```

### Using E300 Short Names

E300-style short names are automatically mapped:

```json
{
  "eos": "SRK",
  "components": [
    { "name": "N2", "moleFraction": 0.02, "criticalTemperature": 126.2, "criticalPressure": 33.94, "acentricFactor": 0.04, "molarMass": 28.014, "normalBoilingPoint": 77.4, "criticalVolume": 0.0895, "volumeShift": 0.0, "parachor": 41.0 },
    { "name": "C1", "moleFraction": 0.80, "criticalTemperature": 190.6, "criticalPressure": 46.0, "acentricFactor": 0.008, "molarMass": 16.043, "normalBoilingPoint": 111.6, "criticalVolume": 0.0986, "volumeShift": 0.0, "parachor": 77.3 },
    { "name": "C2", "moleFraction": 0.18, "criticalTemperature": 305.4, "criticalPressure": 48.8, "acentricFactor": 0.098, "molarMass": 30.07, "normalBoilingPoint": 184.6, "criticalVolume": 0.148, "volumeShift": 0.0, "parachor": 112.9 }
  ]
}
```

### With Pedersen Viscosity

```json
{
  "eos": "PR",
  "prcorr": true,
  "components": [
    { "name": "methane", "moleFraction": 0.70, "criticalTemperature": 190.6, "criticalPressure": 46.0, "acentricFactor": 0.008, "molarMass": 16.043, "normalBoilingPoint": 111.6, "criticalVolume": 0.0986, "volumeShift": -0.194, "parachor": 77.3 },
    { "name": "C7", "moleFraction": 0.30, "isPseudo": true, "criticalTemperature": 548.0, "criticalPressure": 29.5, "acentricFactor": 0.337, "molarMass": 96.0, "normalBoilingPoint": 366.0, "criticalVolume": 0.392, "volumeShift": 0.074, "parachor": 284.0, "density": 717.0 }
  ],
  "viscosityModel": {
    "type": "PEDERSEN"
  }
}
```

## Advanced Features

### Water Handling

JSON fluid files typically define hydrocarbon components only. Add water when reading:

```java
// Add water with default kij = 0.5
SystemInterface fluid = JsonFluidReadWrite.read("fluid.json", true);

// Add water with custom kij
SystemInterface fluid = JsonFluidReadWrite.read("fluid.json", true, 0.45);
```

Water is added with:
- Zero mole fraction (does not affect hydrocarbon equilibrium)
- Binary interaction parameter $k_{ij}$ against all other components (default 0.5)
- Volume correction constant of 0.084004
- Parachor parameter of 10.0
- Multi-phase check enabled for aqueous phase detection

### Integration with Web Services and AI Agents

The JSON format is ideal for programmatic fluid exchange:

```python
# Example: receive fluid definition from a REST API
import requests
import json
from neqsim import jneqsim

response = requests.get('https://api.example.com/fluid/well-A')
fluid_json = json.dumps(response.json())

JsonFluidReadWrite = jneqsim.thermo.util.readwrite.JsonFluidReadWrite
fluid = JsonFluidReadWrite.readString(fluid_json)

# Run calculations
fluid.setPressure(85.0, 'bara')
fluid.setTemperature(65.0, 'C')
```

## Troubleshooting

| Problem                                                                  | Cause                                | Solution                                                       |
| ------------------------------------------------------------------------ | ------------------------------------ | -------------------------------------------------------------- |
| `IllegalArgumentException: JSON input is null or empty`                  | Empty or null JSON string            | Provide valid JSON content                                     |
| `IllegalArgumentException: Failed to parse JSON`                         | Malformed JSON syntax                | Validate JSON (check commas, brackets, quotes)                 |
| `IllegalArgumentException: JSON fluid must contain a 'components' array` | Missing `components` field           | Add `"components": [...]` to the JSON                          |
| `IllegalArgumentException: Component is missing required field 'name'`   | Component without name               | Add `"name"` to each component object                          |
| `IllegalArgumentException: JSON fluid file does not exist`               | Wrong file path                      | Verify the path is correct                                     |
| Wrong EOS used                                                           | Missing or incorrect `eos` field     | Set `"eos": "SRK"` or `"eos": "PR"`                            |
| PR1978 not activated                                                     | Missing `prcorr`                     | Add `"prcorr": true` with `"eos": "PR"`                        |
| BIC not applied                                                          | Component name mismatch in BIC entry | Ensure `i` and `j` names match component `name` fields exactly |
| Pseudo-component density wrong                                           | Missing `density` field              | Add explicit `"density"` value in kg/m³                        |
| Viscosity not matching                                                   | Missing `viscosityModel`             | Add `"viscosityModel"` with type and coefficients              |
| Water phase not appearing                                                | Water not added                      | Use `read(file, true)` or `readString(json, true)`             |

## Related Resources

- [Eclipse E300 Fluid Import](eclipse_e300_fluid_import.md) — E300 file format reference and import guide
- [PVT Workflow Guide](pvt_workflow.md) — overall PVT simulation methodology
- [Fluid Characterization Mathematics](fluid_characterization_mathematics.md) — TBP fraction correlations
- [Phase Envelope Guide](phase_envelope_guide.md) — calculating phase envelopes from imported fluids

## API Reference

### `JsonFluidReadWrite` (Java)

| Method                                                                          | Description                                       |
| ------------------------------------------------------------------------------- | ------------------------------------------------- |
| `read(String inputFile)`                                                        | Read JSON file, return `SystemInterface`          |
| `read(String inputFile, boolean addWater)`                                      | Read with optional water addition (kij=0.5)       |
| `read(String inputFile, boolean addWater, double waterKij)`                     | Read with custom water kij                        |
| `readString(String json)`                                                       | Read from JSON string                             |
| `readString(String json, boolean addWater)`                                     | Read from string with water                       |
| `readString(String json, boolean addWater, double waterKij)`                    | Read from string with custom water kij            |
| `write(SystemInterface fluid, String outputFile)`                               | Write to JSON file                                |
| `write(SystemInterface fluid, String outputFile, double reservoirTempC)`        | Write with reservoir temperature                  |
| `toJsonString(SystemInterface fluid)`                                           | Convert to JSON string                            |
| `toJsonString(SystemInterface fluid, double reservoirTempC)`                    | Convert to JSON string with reservoir temperature |
| `convertE300ToJson(String inputE300, String outputJson)`                        | Convert E300 file to JSON                         |
| `convertE300ToJson(String inputE300, String outputJson, double reservoirTempC)` | Convert with reservoir temperature                |
| `convertJsonToE300(String inputJson, String outputE300)`                        | Convert JSON file to E300                         |
| `convertJsonToE300(String inputJson, String outputE300, double reservoirTempC)` | Convert with reservoir temperature                |
