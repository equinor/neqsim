# TEMA Standard Heat Exchanger Design

The `neqsim.process.mechanicaldesign.heatexchanger` package includes comprehensive TEMA (Tubular Exchanger Manufacturers Association) standard support for shell and tube heat exchanger design.

## Table of Contents

- [Overview](#overview)
- [TEMA Designations](#tema-designations)
- [TEMA Classes](#tema-classes)
- [Shell and Tube Design Calculator](#shell-and-tube-design-calculator)
- [Tube Bundle Design](#tube-bundle-design)
- [Baffle Design](#baffle-design)
- [Materials and Sizing](#materials-and-sizing)
- [Cost Estimation](#cost-estimation)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.mechanicaldesign.heatexchanger`

**Key Classes:**
- `TEMAStandard` - TEMA nomenclature and standard values
- `ShellAndTubeDesignCalculator` - Complete design calculations
- `HeatExchangerMechanicalDesign` - Integration with process equipment

**Standards Reference:**
- TEMA Standards, 10th Edition (2019)
- ASME Section VIII, Division 1
- ASME B31.3 Process Piping

---

## TEMA Designations

TEMA uses a three-letter code to specify heat exchanger configuration:

```
[Front Head] [Shell] [Rear Head]
    A          E        S       = AES type
```

### Front Head Types (Stationary)

| Type | Name | Description | Application |
|------|------|-------------|-------------|
| A | Channel & Removable Cover | Bolted cover for tube access | Most common |
| B | Bonnet (Integral Cover) | More economical | Clean services |
| C | Channel Integral with Tubesheet | High pressure | Process exchangers |
| N | Channel Integral (Large) | Similar to C | Large sizes |
| D | Special High Pressure | Breech-lock design | >1000 psi |

### Shell Types

| Type | Name | Description | ΔP Factor |
|------|------|-------------|-----------|
| E | One-Pass Shell | Most common, simplest | 1.0 |
| F | Two-Pass with Longitudinal Baffle | Better approach temp | 0.8 |
| G | Split Flow | Lower pressure drop | 0.6 |
| H | Double Split Flow | Very low ΔP | 0.5 |
| J | Divided Flow | Condensers | 0.65 |
| K | Kettle Type | Reboilers | 0.7 |
| X | Cross Flow | Gas cooling | 0.3 |

### Rear Head Types

| Type | Name | Removable Bundle | Thermal Expansion |
|------|------|-----------------|-------------------|
| L | Fixed like B | No | Poor |
| M | Fixed like A | No | Poor |
| N | Fixed like N | No | Poor |
| P | Outside Packed Floating | Yes | Good |
| S | Floating with Backing Device | Yes | Good |
| T | Pull-Through Floating | Yes | Good |
| U | U-Tube Bundle | Yes | Excellent |
| W | Externally Sealed Floating | Yes | Good |

### Common Configurations

| Type | Description | Use Case |
|------|-------------|----------|
| AES | Most versatile, full access | General process |
| BEM | Fixed tubesheet, economical | Clean fluids |
| AEU | U-tube, thermal expansion | High ΔT |
| AKT | Kettle reboiler | Distillation |
| BEU | U-tube with bonnet | Economical |

---

## TEMA Classes

| Class | Service | Application | Cost Factor |
|-------|---------|-------------|-------------|
| R | Severe | Refineries, petrochemical | 1.0 (baseline) |
| C | Moderate | Chemical, general process | 0.8 |
| B | Light | HVAC, commercial | 0.6 |

### Class R Requirements (Most Stringent)
- Minimum tube wall: 12.7 mm (BWG 18)
- Minimum corrosion allowance: 4.76 mm (3/16")
- Full radiography on welds
- Hydrostatic test: 1.5× design pressure

### Class C Requirements
- Minimum tube wall: 9.5 mm (BWG 20)
- Minimum corrosion allowance: 3.18 mm (1/8")
- Spot radiography on welds

### Class B Requirements
- Minimum tube wall: 6.35 mm (BWG 22)
- Minimum corrosion allowance: 2.11 mm (1/12")
- Visual examination on welds

---

## Shell and Tube Design Calculator

```java
import neqsim.process.mechanicaldesign.heatexchanger.*;
import neqsim.process.mechanicaldesign.heatexchanger.TEMAStandard.*;

// Create calculator
ShellAndTubeDesignCalculator calc = new ShellAndTubeDesignCalculator();

// Set TEMA configuration
calc.setTemaDesignation("AES");
calc.setTemaClass(TEMAClass.R);

// Set thermal requirements
calc.setRequiredArea(150.0);  // m²

// Set design conditions
calc.setShellSidePressure(30.0);   // bara
calc.setTubeSidePressure(15.0);    // bara
calc.setDesignTemperature(200.0);  // °C

// Set tube parameters
calc.setTubeSize(StandardTubeSize.TUBE_3_4_INCH);
calc.setTubeLength(6096.0);  // mm (20 ft)
calc.setTubePasses(2);

// Run calculation
calc.calculate();

// Get results
System.out.println("Shell ID: " + calc.getShellInsideDiameter() + " mm");
System.out.println("Shell wall: " + calc.getShellWallThickness() + " mm");
System.out.println("Tube count: " + calc.getTubeCount());
System.out.println("Actual area: " + calc.getActualArea() + " m²");
System.out.println("Baffle count: " + calc.getBaffleCount());
System.out.println("Dry weight: " + calc.getTotalDryWeight() + " kg");
System.out.println("Total cost: $" + calc.getTotalCost());

// Get JSON report
String json = calc.toJson();
```

---

## Tube Bundle Design

### Standard Tube Sizes

| Size | OD (mm) | OD (inch) | Common BWG |
|------|---------|-----------|------------|
| 3/8" | 9.525 | 0.375 | 18, 20, 22 |
| 1/2" | 12.7 | 0.500 | 16, 18, 20 |
| 5/8" | 15.875 | 0.625 | 14, 16, 18 |
| 3/4" | 19.05 | 0.750 | 14, 16, 18 |
| 1" | 25.4 | 1.000 | 12, 14, 16 |

### Tube Pitch Patterns

| Pattern | Angle | Min Ratio | Heat Transfer | Cleaning |
|---------|-------|-----------|---------------|----------|
| Triangular 30° | 30° | 1.25 | Best | Difficult |
| Rotated Triangular 60° | 60° | 1.25 | Good | Moderate |
| Square 90° | 90° | 1.25 | Baseline | Easy |
| Rotated Square 45° | 45° | 1.25 | Good | Moderate |

```java
// Set tube layout
calc.setPitchPattern(TubePitchPattern.TRIANGULAR_30);
calc.setTubePitchRatio(1.25);  // Pitch/OD ratio

// Calculate tube count
int tubeCount = TEMAStandard.estimateTubeCount(
    610.0,          // Shell ID (mm)
    19.05,          // Tube OD (mm)
    23.81,          // Tube pitch (mm)
    TubePitchPattern.TRIANGULAR_30,
    2               // Tube passes
);
```

---

## Baffle Design

### Baffle Types

| Type | Heat Transfer | Pressure Drop | Application |
|------|--------------|---------------|-------------|
| Single Segmental | 1.0 | 1.0 | Standard |
| Double Segmental | 0.75 | 0.6 | Lower ΔP |
| Triple Segmental | 0.5 | 0.4 | Very low ΔP |
| No-Tubes-In-Window | 0.6 | 0.5 | Long spans |
| Disc and Doughnut | 0.5 | 0.55 | Low ΔP |
| Rod Baffles | 0.2 | 0.3 | Vibration control |

### Baffle Spacing Limits

Per TEMA standards:

```java
// Minimum baffle spacing
double minSpacing = TEMAStandard.getMinBaffleSpacing(
    610.0,          // Shell ID (mm)
    TEMAClass.R     // TEMA class
);

// Maximum baffle spacing
double maxSpacing = TEMAStandard.getMaxBaffleSpacing(610.0);

// Maximum unsupported tube span
double maxSpan = TEMAStandard.getMaxUnsupportedSpan(
    19.05,          // Tube OD (mm)
    "Carbon Steel"  // Tube material
);
```

### Baffle Cut

Standard baffle cuts: 15%, 20%, 25%, 30%, 35%, 45%

| Cut | Effect |
|-----|--------|
| 15-20% | High heat transfer, high ΔP |
| 25% | Standard, balanced |
| 30-35% | Lower ΔP, reduced tube support |
| 45% | Very low ΔP, special applications |

---

## Materials and Sizing

### Common Materials

| Material | Grade | Allowable Stress (MPa) | Application |
|----------|-------|----------------------|-------------|
| Carbon Steel | SA-516-70 | 137.9 | Shell, tubesheets |
| Carbon Steel | SA-179 | 103.4 | Tubes |
| Stainless | SA-240-316L | 115.1 | Corrosive service |
| Copper-Nickel | SB-111-706 | 75.8 | Seawater |

### Shell Wall Thickness

Per ASME Section VIII, Div. 1:

```
t = (P × R) / (S × E - 0.6 × P) + CA

Where:
t  = Wall thickness (mm)
P  = Design pressure (MPa)
R  = Shell inside radius (mm)
S  = Allowable stress (MPa)
E  = Joint efficiency
CA = Corrosion allowance (mm)
```

### Tubesheet Thickness

Per TEMA:

```
t = G × √(0.785 × P / S) / η + CA

Where:
G  = Gasket diameter (mm)
P  = Design pressure (MPa)
S  = Allowable stress (MPa)
η  = Ligament efficiency
CA = Corrosion allowance (mm)
```

---

## Cost Estimation

### Weight-Based Estimation

```java
// Get component weights
double shellWeight = calc.getShellWeight();
double tubeWeight = calc.getTubeWeight();
double tubesheetWeight = calc.getTubesheetWeight();
double headWeight = calc.getHeadWeight();
double baffleWeight = calc.getBaffleWeight();

// Total weights
double dryWeight = calc.getTotalDryWeight();
double operatingWeight = calc.getOperatingWeight();

// Cost estimate
double materialCost = calc.getMaterialCost();
double fabricationCost = calc.getFabricationCost();
double totalCost = calc.getTotalCost();
```

### Cost Factors

| Factor | Impact on Cost |
|--------|----------------|
| TEMA Class R vs B | +40% for R |
| Floating head vs fixed | +20-25% |
| Stainless vs carbon | +300-400% |
| Pull-through vs split ring | +5-10% |
| K-shell (kettle) | +30% |

---

## Examples

### Example 1: Process Heat Exchanger

```java
// Oil cooler for offshore platform
ShellAndTubeDesignCalculator calc = new ShellAndTubeDesignCalculator();

calc.setTemaDesignation("AES");  // Floating head, easy maintenance
calc.setTemaClass(TEMAClass.R);  // Refinery grade

calc.setRequiredArea(200.0);
calc.setShellSidePressure(25.0);
calc.setTubeSidePressure(10.0);
calc.setDesignTemperature(150.0);

calc.setTubeSize(StandardTubeSize.TUBE_3_4_INCH);
calc.setTubeWallThickness(2.108);  // BWG 14
calc.setTubeLength(6096.0);        // 20 ft
calc.setTubePasses(4);

calc.setShellMaterial("Carbon Steel SA-516-70");
calc.setTubeMaterial("Stainless Steel SA-213-316L");

calc.calculate();

System.out.println(calc.toJson());
```

### Example 2: Reboiler

```java
// Kettle reboiler for distillation column
ShellAndTubeDesignCalculator calc = new ShellAndTubeDesignCalculator();

calc.setTemaDesignation("AKT");  // Kettle type, pull-through
calc.setTemaClass(TEMAClass.R);

calc.setRequiredArea(100.0);
calc.setShellSidePressure(5.0);   // Low pressure vapor space
calc.setTubeSidePressure(25.0);   // Steam or hot oil
calc.setDesignTemperature(250.0);

calc.calculate();
```

### Example 3: Configuration Selection

```java
// Recommend TEMA configuration
String config = TEMAStandard.recommendConfiguration(
    true,   // Needs mechanical cleaning
    true,   // Large temperature difference
    false,  // Not high pressure
    false   // Not hazardous
);
// Returns "AES"

// Get configuration details
TEMAConfiguration tema = TEMAStandard.getConfiguration(config);
System.out.println("Description: " + tema.getDescription());
System.out.println("Bundle removable: " + tema.isBundleRemovable());
System.out.println("Good thermal expansion: " + tema.hasGoodThermalExpansion());
System.out.println("Cost factor: " + tema.getCostFactor());
```

---

## JSON Output Example

```json
{
  "temaDesignation": "AES",
  "temaClass": "R",
  "shell": {
    "insideDiameter_mm": 610.0,
    "outsideDiameter_mm": 635.0,
    "wallThickness_mm": 12.7,
    "material": "Carbon Steel SA-516-70"
  },
  "tubes": {
    "count": 344,
    "outerDiameter_mm": 19.05,
    "wallThickness_mm": 2.108,
    "length_mm": 6096.0,
    "passes": 2,
    "pitch_mm": 23.81,
    "pitchPattern": "Triangular 30°",
    "material": "Carbon Steel SA-179"
  },
  "baffles": {
    "type": "Single Segmental",
    "count": 12,
    "spacing_mm": 457.0,
    "cut": 0.25
  },
  "area": {
    "required_m2": 150.0,
    "actual_m2": 158.4,
    "margin": 0.056
  },
  "weights": {
    "shell_kg": 1250.0,
    "tubes_kg": 2180.0,
    "tubesheets_kg": 580.0,
    "heads_kg": 420.0,
    "baffles_kg": 180.0,
    "totalDry_kg": 4610.0,
    "operating_kg": 5840.0
  },
  "costs": {
    "material_USD": 8500.0,
    "fabrication_USD": 25500.0,
    "total_USD": 34000.0
  }
}
```

---

## See Also

- [Heat Exchanger Equipment](../process/equipment/heat_exchangers.md)
- [Mechanical Design Overview](../process/mechanical_design_overview.md)
- [Process Design Guide](../process/process_design_guide.md)
- [ASME Standards](../standards/asme_standards.md)
