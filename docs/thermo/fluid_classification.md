---
title: "Reservoir Fluid Classification"
description: "This document describes NeqSim's reservoir fluid classification capabilities using the `FluidClassifier` utility class based on the Whitson methodology."
---

# Reservoir Fluid Classification

This document describes NeqSim's reservoir fluid classification capabilities using the `FluidClassifier` utility class based on the Whitson methodology.

## Overview

Reservoir fluid classification is essential for selecting appropriate modeling approaches and simulation strategies. NeqSim implements the industry-standard Whitson classification methodology to categorize fluids into:

- **Dry Gas** - No liquid dropout at any pressure/temperature
- **Wet Gas** - Produces liquid at surface but remains single-phase in reservoir
- **Gas Condensate** - Exhibits retrograde condensation in reservoir
- **Volatile Oil** - High shrinkage oil with significant gas liberation
- **Black Oil** - Conventional crude oil with moderate gas content
- **Heavy Oil** - High viscosity, low API gravity oil

## Classification Criteria

The Whitson classification uses three primary criteria:

| Fluid Type | GOR (scf/STB) | C7+ (mol%) | API Gravity |
|------------|---------------|------------|-------------|
| Dry Gas | > 100,000 | < 0.7 | N/A |
| Wet Gas | 15,000 - 100,000 | 0.7 - 4 | 40-60° |
| Gas Condensate | 3,300 - 15,000 | 4 - 12.5 | 40-60° |
| Volatile Oil | 1,000 - 3,300 | 12.5 - 20 | 40-50° |
| Black Oil | < 1,000 | > 20 | 15-40° |
| Heavy Oil | < 200 | > 30 | 10-15° |

## Basic Usage

### Classification by Composition

```java
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermo.util.FluidClassifier;
import neqsim.thermo.util.ReservoirFluidType;

// Create a fluid
SystemInterface fluid = new SystemSrkEos(373.15, 100.0);
fluid.addComponent("methane", 0.70);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.08);
fluid.addComponent("n-heptane", 0.07);
fluid.addComponent("C10", 0.05);
fluid.createDatabase(true);
fluid.setMixingRule("classic");

// Classify the fluid
ReservoirFluidType type = FluidClassifier.classify(fluid);
System.out.println("Fluid type: " + type.getDisplayName());
// Output: Fluid type: Gas Condensate
```

### Classification by GOR

```java
// Classify directly from GOR measurement
double gorScfStb = 5000.0;  // scf/STB
ReservoirFluidType type = FluidClassifier.classifyByGOR(gorScfStb);
System.out.println("Fluid type: " + type.getDisplayName());
// Output: Fluid type: Gas Condensate
```

### Classification by C7+ Content

```java
// Classify directly from C7+ content
double c7PlusMolPercent = 8.5;  // mol%
ReservoirFluidType type = FluidClassifier.classifyByC7Plus(c7PlusMolPercent);
System.out.println("Fluid type: " + type.getDisplayName());
// Output: Fluid type: Gas Condensate
```

## Advanced Classification

### With Phase Envelope Analysis

For more accurate classification, use the phase envelope method which considers:
- Critical point location
- Reservoir temperature relative to phase envelope
- Cricondenbar and cricondentherm

```java
// Classify using phase envelope analysis
double reservoirTempK = 373.15;  // 100°C
ReservoirFluidType type = FluidClassifier.classifyWithPhaseEnvelope(fluid, reservoirTempK);
System.out.println("Fluid type: " + type.getDisplayName());
```

### Calculating C7+ Content

```java
// Calculate C7+ content for any fluid
double c7Plus = FluidClassifier.calculateC7PlusContent(fluid);
System.out.println("C7+ content: " + c7Plus + " mol%");
```

### Estimating API Gravity

```java
// Estimate API gravity from fluid composition
double apiGravity = FluidClassifier.estimateAPIGravity(fluid);
if (!Double.isNaN(apiGravity)) {
    System.out.println("Estimated API gravity: " + apiGravity + "°");
}
```

## Classification Report

Generate a comprehensive classification report:

```java
String report = FluidClassifier.generateClassificationReport(fluid);
System.out.println(report);
```

Output:
```
=== Reservoir Fluid Classification Report ===

Composition Analysis:
  C7+ Content: 12.00 mol%

Classification Result:
  Fluid Type: Gas Condensate
  Typical GOR Range: 3,300 - 15,000 scf/STB
  Typical C7+ Range: 4 - 12.5 mol%

  Estimated API Gravity: 48.5°

Modeling Recommendations:
  - Compositional simulation recommended
  - CVD experiment important for liquid dropout curve
  - Consider modified black-oil with OGR (Rv)
```

## ReservoirFluidType Enum

The `ReservoirFluidType` enum provides detailed information for each fluid type:

```java
ReservoirFluidType type = ReservoirFluidType.GAS_CONDENSATE;

// Get display name
String name = type.getDisplayName();  // "Gas Condensate"

// Get typical ranges
String gorRange = type.getTypicalGORRange();  // "3,300 - 15,000"
String c7PlusRange = type.getTypicalC7PlusRange();  // "4 - 12.5"
```

### Available Fluid Types

| Enum Value | Display Name | Description |
|------------|--------------|-------------|
| `DRY_GAS` | Dry Gas | No liquid dropout |
| `WET_GAS` | Wet Gas | Surface liquid only |
| `GAS_CONDENSATE` | Gas Condensate | Retrograde condensation |
| `VOLATILE_OIL` | Volatile Oil | High shrinkage oil |
| `BLACK_OIL` | Black Oil | Conventional crude |
| `HEAVY_OIL` | Heavy Oil | High viscosity crude |
| `UNKNOWN` | Unknown | Unclassified |

## Python Usage

```python
from jpype import JClass

# Import classes
FluidClassifier = JClass('neqsim.thermo.util.FluidClassifier')
ReservoirFluidType = JClass('neqsim.thermo.util.ReservoirFluidType')
SystemSrkEos = JClass('neqsim.thermo.system.SystemSrkEos')

# Create fluid
fluid = SystemSrkEos(373.15, 100.0)
fluid.addComponent("methane", 0.70)
fluid.addComponent("ethane", 0.10)
fluid.addComponent("n-heptane", 0.12)
fluid.addComponent("C10", 0.08)
fluid.createDatabase(True)
fluid.setMixingRule("classic")

# Classify
fluid_type = FluidClassifier.classify(fluid)
print(f"Fluid type: {fluid_type.getDisplayName()}")

# Get C7+ content
c7plus = FluidClassifier.calculateC7PlusContent(fluid)
print(f"C7+ content: {c7plus:.2f} mol%")

# Generate report
report = FluidClassifier.generateClassificationReport(fluid)
print(report)
```

## Modeling Recommendations by Fluid Type

### Dry Gas and Wet Gas
- Use equation of state (SRK or PR) for accurate Z-factor
- Black-oil model may be sufficient for simulation
- Focus on gas density and compressibility

### Gas Condensate
- **Compositional simulation recommended**
- CVD experiment important for liquid dropout curve
- Consider modified black-oil with OGR (Rv)
- Track condensate banking near wellbore

### Volatile Oil
- **Compositional simulation strongly recommended**
- Modified black-oil model may be acceptable
- DLE and separator tests are essential
- Significant solution gas-oil ratio variation

### Black Oil
- Traditional black-oil model typically adequate
- DLE experiment for Bo, Rs, viscosity
- Simpler correlation-based methods work well

### Heavy Oil
- **Viscosity modeling is critical**
- Consider thermal effects if applicable
- LBC viscosity may need tuning
- Dead oil viscosity correlations important

## Implementation Details

### C7+ Detection Algorithm

The `calculateC7PlusContent` method identifies C7+ components by:
1. Molar mass ≥ 100 g/mol
2. Component name starting with C7, C8, C9, etc.
3. Component name containing "heptane", "octane", "nonane", "decane"
4. Components flagged as TBP fractions (`isIsTBPfraction()`)
5. Components flagged as plus fractions (`isIsPlusFraction()`)

### Phase Envelope Classification

The `classifyWithPhaseEnvelope` method refines composition-based classification by:
1. Calculating the critical point using phase envelope algorithm
2. Comparing reservoir temperature to critical temperature
3. Adjusting classification if reservoir T is near or above Tc

## References

- Whitson, C.H. and Brulé, M.R., "Phase Behavior", SPE Monograph Series
- [Whitson Wiki - Reservoir Fluid Classification](https://wiki.whitson.com/phase_behavior/classification/reservoir_fluid_type/)
- McCain, W.D., "Properties of Petroleum Fluids", 2nd ed.

## API Reference

### FluidClassifier Class

| Method | Parameters | Returns | Description |
|--------|------------|---------|-------------|
| `classify` | `SystemInterface fluid` | `ReservoirFluidType` | Classify by composition |
| `classifyByGOR` | `double gorScfStb` | `ReservoirFluidType` | Classify by GOR |
| `classifyByC7Plus` | `double c7PlusMolPercent` | `ReservoirFluidType` | Classify by C7+ content |
| `classifyWithPhaseEnvelope` | `SystemInterface fluid, double reservoirTemperatureK` | `ReservoirFluidType` | Classify with phase envelope |
| `calculateC7PlusContent` | `SystemInterface fluid` | `double` | Calculate C7+ content (mol%) |
| `estimateAPIGravity` | `SystemInterface fluid` | `double` | Estimate API gravity |
| `generateClassificationReport` | `SystemInterface fluid` | `String` | Generate full report |

### ReservoirFluidType Enum

| Method | Returns | Description |
|--------|---------|-------------|
| `getDisplayName()` | `String` | Human-readable fluid type name |
| `getTypicalGORRange()` | `String` | Typical GOR range string |
| `getTypicalC7PlusRange()` | `String` | Typical C7+ range string |

## See Also

- [Fluid Creation Guide](fluid_creation_guide.md)
- [PVT Characterization](pvt_fluid_characterization.md)
- [Fluid Characterization](../wiki/fluid_characterization.md)
- [Black Oil Models](../blackoil/README.md)
