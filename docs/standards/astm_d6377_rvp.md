# ASTM D6377 - Reid Vapor Pressure

ASTM D6377 provides methods for determining vapor pressure of crude oil and petroleum products.

## Table of Contents
- [Overview](#overview)
- [Vapor Pressure Definitions](#vapor-pressure-definitions)
- [Implementation](#implementation)
- [Usage Examples](#usage-examples)
- [Method Selection](#method-selection)
- [Correlations](#correlations)

---

## Overview

**Standard:** ASTM D6377 - Standard Test Method for Determination of Vapor Pressure of Crude Oil: VPCRx (Expansion Method)

**Purpose:** Determine the vapor pressure of crude oil and condensates for:
- Safety in storage and transport
- Product specifications
- Blending calculations
- Regulatory compliance

**Class:** `Standard_ASTM_D6377`

---

## Vapor Pressure Definitions

### True Vapor Pressure (TVP)

The equilibrium pressure of vapor above a liquid at a specified temperature when vapor/liquid ratio approaches zero.

$$TVP = P_{bubble}(T)$$

### Reid Vapor Pressure (RVP)

The vapor pressure measured at 100°F (37.8°C) in a standardized apparatus with vapor/liquid volume ratio of 4:1.

### VPCR4 (Vapor Pressure at V/L = 4)

The pressure at which 80% by volume is vapor at 37.8°C (100°F).

### VPCR Relationship

Different VPCR ratios are used in various standards:
- VPCR4: 80% vapor (ASTM D6377)
- VPCR1: 50% vapor
- VPCR0.02: ~2% vapor (approximates TVP)

---

## Implementation

### Constructor

```java
import neqsim.standards.oilquality.Standard_ASTM_D6377;

// Create standard from fluid
Standard_ASTM_D6377 rvpStandard = new Standard_ASTM_D6377(thermoSystem);
```

### Available Methods

| Method Name | Description |
|-------------|-------------|
| `VPCR4` | Vapor pressure at V/L = 4 (default) |
| `VPCR4_no_water` | VPCR4 excluding water |
| `RVP_ASTM_D6377` | RVP correlation from D6377 |
| `RVP_ASTM_D323_73_79` | RVP per D323 (1973/1979) |
| `RVP_ASTM_D323_82` | RVP per D323 (1982) |

### Key Methods

| Method | Description |
|--------|-------------|
| `calculate()` | Perform vapor pressure calculations |
| `getValue("RVP", "bara")` | Get Reid vapor pressure |
| `getValue("TVP", "bara")` | Get true vapor pressure |
| `getValue("VPCR4", "bara")` | Get VPCR4 |
| `setMethodRVP(method)` | Select RVP calculation method |
| `getMethodRVP()` | Get current method |

---

## Usage Examples

### Basic RVP Calculation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.standards.oilquality.Standard_ASTM_D6377;

// Create condensate/crude composition
SystemInterface crude = new SystemSrkEos(273.15 + 15, 1.01325);
crude.addComponent("methane", 0.01);
crude.addComponent("ethane", 0.02);
crude.addComponent("propane", 0.04);
crude.addComponent("n-butane", 0.06);
crude.addComponent("i-butane", 0.03);
crude.addComponent("n-pentane", 0.08);
crude.addComponent("i-pentane", 0.05);
crude.addComponent("n-hexane", 0.10);
crude.addTBPfraction("C7", 0.15, 100.0/1000.0, 0.72);
crude.addTBPfraction("C10", 0.20, 142.0/1000.0, 0.78);
crude.addTBPfraction("C20", 0.26, 282.0/1000.0, 0.85);
crude.setMixingRule("classic");

// Calculate RVP
Standard_ASTM_D6377 rvpStandard = new Standard_ASTM_D6377(crude);
rvpStandard.setMethodRVP("VPCR4");
rvpStandard.calculate();

// Get results
double tvp = rvpStandard.getValue("TVP", "bara");
double rvp = rvpStandard.getValue("RVP", "bara");
double vpcr4 = rvpStandard.getValue("VPCR4", "bara");

System.out.println("=== Vapor Pressure Results ===");
System.out.printf("True Vapor Pressure (TVP) = %.4f bara%n", tvp);
System.out.printf("Reid Vapor Pressure (RVP) = %.4f bara%n", rvp);
System.out.printf("VPCR4 = %.4f bara%n", vpcr4);
```

### Comparing Different RVP Methods

```java
// Calculate using all available methods
String[] methods = {"VPCR4", "RVP_ASTM_D6377", "RVP_ASTM_D323_73_79", "RVP_ASTM_D323_82"};

System.out.println("Method                | RVP (bara)");
System.out.println("----------------------|----------");

for (String method : methods) {
    Standard_ASTM_D6377 std = new Standard_ASTM_D6377(crude);
    std.setMethodRVP(method);
    std.calculate();
    double rvp = std.getValue("RVP", "bara");
    System.out.printf("%-21s | %.4f%n", method, rvp);
}
```

### Effect of Light Ends on RVP

```java
// Analyze RVP sensitivity to light ends
double[] methaneContent = {0.0, 0.005, 0.01, 0.02, 0.05};

System.out.println("Methane (mol%) | RVP (bara)");
System.out.println("---------------|----------");

for (double ch4 : methaneContent) {
    SystemInterface fluid = new SystemSrkEos(273.15 + 15, 1.0);
    fluid.addComponent("methane", ch4);
    fluid.addComponent("ethane", 0.02);
    fluid.addComponent("propane", 0.04);
    fluid.addComponent("n-butane", 0.08);
    fluid.addComponent("n-pentane", 0.10);
    fluid.addTBPfraction("C7", 0.20, 100/1000.0, 0.72);
    fluid.addTBPfraction("C15", 0.56 - ch4, 200/1000.0, 0.80);
    fluid.setMixingRule("classic");
    
    Standard_ASTM_D6377 std = new Standard_ASTM_D6377(fluid);
    std.calculate();
    double rvp = std.getValue("RVP", "bara");
    
    System.out.printf("%14.1f | %.4f%n", ch4 * 100, rvp);
}
```

### Wet vs Dry RVP

```java
// Calculate with and without water
SystemInterface wetCrude = crude.clone();
wetCrude.addComponent("water", 0.01);  // 1% water

Standard_ASTM_D6377 wetStd = new Standard_ASTM_D6377(wetCrude);
wetStd.calculate();

double vpcr4Wet = wetStd.getValue("VPCR4", "bara");
double vpcr4Dry = wetStd.getValue("VPCR4_no_water", "bara");

System.out.printf("VPCR4 (with water) = %.4f bara%n", vpcr4Wet);
System.out.printf("VPCR4 (dry basis) = %.4f bara%n", vpcr4Dry);
```

---

## Method Selection

### VPCR4 (Default)

Best for:
- General crude oil characterization
- Comparison with standard lab measurements
- Regulatory compliance

### RVP_ASTM_D6377

Correlation from ASTM D6377:
$$RVP = 0.834 \times VPCR4$$

### RVP_ASTM_D323_82

Correlation from ASTM D323 (1982 edition):
$$RVP = \frac{0.752 \times (100 \times VPCR4) + 6.07}{100}$$

### RVP_ASTM_D323_73_79

For comparison with historical data using D323 (1973/1979 editions).
Uses VPCR4 without water contribution.

---

## Correlations

### TVP to RVP

Approximate relationship:
$$RVP \approx 0.75 \times TVP + constant$$

The constant depends on crude composition.

### Temperature Dependence

For estimation at temperatures other than 37.8°C:

$$\log_{10}(P_{vap}) = A - \frac{B}{T + C}$$

Antoine-type equation where A, B, C are crude-specific.

### RVP Specifications

| Product | Typical RVP Limit |
|---------|-------------------|
| Crude oil (export) | < 0.7 bara (10 psia) |
| Stabilized condensate | < 0.5 bara (7 psia) |
| Gasoline (summer) | < 0.62 bara (9 psi) |
| Gasoline (winter) | < 0.90 bara (13 psi) |

---

## Technical Details

### Calculation Procedure

1. Set temperature to 37.8°C (100°F)
2. Perform bubble point flash to get TVP
3. Perform flash at vapor/liquid volume ratio = 4
4. Apply correlation for RVP estimation

### Reference Conditions

| Parameter | Value |
|-----------|-------|
| Temperature | 37.8°C (100°F) |
| V/L ratio | 4:1 (80% vapor by volume) |
| Pressure | Equilibrium |

### Equation of State

Uses SRK-EoS for phase equilibrium calculations.

---

## Accuracy Considerations

### Factors Affecting Accuracy

1. **Light end characterization** - Accurate C1-C4 composition critical
2. **Heavy end representation** - TBP fractions affect liquid volume
3. **Water content** - Can significantly affect measured RVP
4. **Sample handling** - Light end loss during sampling

### Typical Uncertainty

| Method | Uncertainty |
|--------|-------------|
| VPCR4 calculation | ±0.02 bara |
| RVP correlation | ±0.03-0.05 bara |

### Recommendations

1. Ensure accurate light ends (C1-C5) analysis
2. Use consistent method for comparison
3. Report method used with results
4. Consider water content effects

---

## References

1. ASTM D6377 - Standard Test Method for Determination of Vapor Pressure of Crude Oil: VPCRx (Expansion Method)
2. ASTM D323 - Standard Test Method for Vapor Pressure of Petroleum Products (Reid Method)
3. ASTM D5191 - Standard Test Method for Vapor Pressure of Petroleum Products and Liquid Fuels (Mini Method)
4. API MPMS Chapter 8 - Sampling
