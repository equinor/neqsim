---
title: Topside Piping Mechanical Design
description: Comprehensive documentation for topside (offshore platform and onshore facility) piping design in NeqSim, including velocity analysis, support spacing, vibration screening, and stress analysis per ind...
---

# Topside Piping Mechanical Design

Comprehensive documentation for topside (offshore platform and onshore facility) piping design in NeqSim, including velocity analysis, support spacing, vibration screening, and stress analysis per industry standards.

> **📘 Related Documentation**
> 
> - [Pipeline Mechanical Design](pipeline_mechanical_design) - Subsea/onshore pipeline design
> - [Riser Mechanical Design](riser_mechanical_design) - Riser design with catenary and VIV
> - [Mechanical Design Standards](mechanical_design_standards) - Standards database reference
> - [Beggs and Brills Pipe Model](PipeBeggsAndBrills) - Flow modeling documentation

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Service Types](#service-types)
- [Design Standards](#design-standards)
- [Velocity Analysis](#velocity-analysis)
- [Support Spacing](#support-spacing)
- [Vibration Screening](#vibration-screening)
- [Stress Analysis](#stress-analysis)
- [Thermal Expansion](#thermal-expansion)
- [Pipe Schedules and Materials](#pipe-schedules-and-materials)
- [Insulation Types](#insulation-types)
- [JSON Reporting](#json-reporting)
- [Examples](#examples)
- [Python Integration](#python-integration)

---

## Overview

The topside piping design system provides mechanical design capabilities for:

- **Process piping** on offshore platforms and onshore facilities
- **Velocity sizing** per API RP 14E erosional velocity limits
- **Support spacing** per NORSOK L-002 and ASME B31.3
- **Vibration screening** for AIV/FIV per Energy Institute Guidelines
- **Stress analysis** per ASME B31.3 Process Piping Code
- **Thermal expansion** and anchor force calculations

**Location:** `neqsim.process.equipment.pipeline` and `neqsim.process.mechanicaldesign.pipeline`

**Key Classes:**
- `TopsidePiping` - Main equipment class with service type configuration
- `TopsidePipingMechanicalDesign` - Design coordination class
- `TopsidePipingMechanicalDesignCalculator` - Calculation engine
- `TopsidePipingMechanicalDesignDataSource` - Database access

---

## Architecture

### Class Hierarchy

```
TopsidePiping extends PipeBeggsAndBrills
├── ServiceType enum (12 service categories)
├── PipeSchedule enum (14 schedules)
├── InsulationType enum (7 insulation types)
└── TopsidePipingMechanicalDesign
    ├── TopsidePipingMechanicalDesignCalculator
    └── TopsidePipingMechanicalDesignDataSource
```

### Design Flow

```
1. Create TopsidePiping with service type
2. Configure operating envelope (P, T ranges)
3. Set fittings (elbows, tees, valves)
4. Set insulation if required
5. Initialize mechanical design
6. Configure material grade and design code
7. Run design calculations
8. Export JSON report
```

---

## Service Types

The `ServiceType` enum defines the piping service category, which affects velocity limits and material selection:

| Service Type | Description | Max Velocity Factor | Typical Application |
|--------------|-------------|---------------------|---------------------|
| `PROCESS_GAS` | Hydrocarbon gas service | 1.0 | Production headers, export gas |
| `PROCESS_LIQUID` | Hydrocarbon liquid service | 1.0 | Crude oil, condensate |
| `MULTIPHASE` | Two-phase gas/liquid | 0.8 | Well flowlines, separators |
| `STEAM` | Steam service | 1.2 | Process heating, turbines |
| `FLARE` | Flare system | 1.5 | HP/LP flare headers |
| `VENT` | Atmospheric vent | 1.5 | Tank vents, relief |
| `FUEL_GAS` | Fuel gas system | 0.9 | Turbine fuel, heating |
| `INSTRUMENT_AIR` | Instrument air | 1.0 | Control systems |
| `HYDRAULIC` | Hydraulic fluid | 0.7 | Valve actuators |
| `COOLING_WATER` | Cooling water | 1.0 | Heat exchangers |
| `PRODUCED_WATER` | Produced water | 0.9 | Water treatment |
| `CHEMICAL_INJECTION` | Chemical injection | 0.8 | MEG, corrosion inhibitor |

### Factory Methods

```java
// Create gas process header
TopsidePiping gasHeader = TopsidePiping.createProcessGas("Gas Header", feed);
gasHeader.setLength(50.0);
gasHeader.setDiameter(0.2032); // 8 inch

// Create flare header
TopsidePiping flareHeader = TopsidePiping.createFlareHeader("HP Flare", feed);

// Create steam line
TopsidePiping steamLine = TopsidePiping.createSteamLine("HP Steam", feed);

// Create cooling water line
TopsidePiping cwLine = TopsidePiping.createCoolingWater("CW Supply", feed);
```

---

## Design Standards

### Primary Standards

| Standard | Application | Key Parameters |
|----------|-------------|----------------|
| **ASME B31.3** | Process Piping | Allowable stress, wall thickness, stress analysis |
| **API RP 14E** | Erosional Velocity | C-factor, mixture density correlation |
| **NORSOK L-002** | Piping Layout | Support spacing, flexibility requirements |
| **Energy Institute** | AIV/FIV Guidelines | Acoustic power level, vibration screening |
| **ASME B16.5** | Flanges | Pressure-temperature ratings |

### ASME B31.3 Allowable Stresses

Built-in material data for common piping materials:

| Material Grade | 20°C (MPa) | 100°C (MPa) | 200°C (MPa) | 300°C (MPa) |
|----------------|------------|-------------|-------------|-------------|
| A106-B | 138.0 | 138.0 | 138.0 | 132.0 |
| A106-C | 159.0 | 159.0 | 159.0 | 152.0 |
| A333-6 | 138.0 | 138.0 | 138.0 | 132.0 |
| A312-TP304 | 138.0 | 115.0 | 101.0 | 90.0 |
| A312-TP316 | 138.0 | 115.0 | 103.0 | 92.0 |
| A312-TP316L | 115.0 | 103.0 | 92.0 | 83.0 |
| A790-S31803 (Duplex) | 207.0 | 192.0 | 177.0 | 165.0 |
| A790-S32750 (Super Duplex) | 241.0 | 226.0 | 211.0 | 197.0 |

---

## Velocity Analysis

### Erosional Velocity (API RP 14E)

The erosional velocity is the maximum velocity at which erosion-corrosion becomes significant:

$$V_e = \frac{C}{\sqrt{\rho_m}}$$

Where:
- $V_e$ = Erosional velocity (m/s)
- $C$ = Empirical constant (100-150)
- $\rho_m$ = Mixture density (kg/m³)

### C-Factor Guidelines

| Condition | C-Factor | Notes |
|-----------|----------|-------|
| Continuous service, clean | 100 | Standard design |
| Intermittent service | 125 | Short-duration operations |
| Clean gas, no solids | 150 | Conservative for gas |
| Sand production | 70-100 | Reduced for erosive conditions |

### Service-Specific Velocity Limits

| Service | Max Velocity (m/s) | Notes |
|---------|-------------------|-------|
| Gas | 20 | Noise and vibration limit |
| Liquid | 3 | Erosion and water hammer |
| Multiphase | 15 | Slug flow considerations |
| Noise limit | 40 | Acoustic emission limit |

### Java Example

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

// Set flow conditions
calc.setMassFlowRate(10.0);        // kg/s
calc.setMixtureDensity(80.0);      // kg/m³
calc.setOuterDiameter(0.2032);     // 8 inch
calc.setNominalWallThickness(0.00823);

// Calculate velocities
double erosionalVel = calc.calculateErosionalVelocity();
double actualVel = calc.calculateActualVelocity();

// Check limits
boolean velocityOK = calc.checkVelocityLimits();

System.out.println("Erosional velocity: " + erosionalVel + " m/s");
System.out.println("Actual velocity: " + actualVel + " m/s");
System.out.println("Velocity OK: " + velocityOK);
```

---

## Support Spacing

### ASME B31.3 Simplified Method

The calculator includes a simplified support spacing table based on pipe size:

| Pipe Size (NPS) | Support Spacing (m) |
|-----------------|---------------------|
| 2" | 2.1 |
| 4" | 2.7 |
| 6" | 3.4 |
| 8" | 3.7 |
| 12" | 4.3 |
| 16" | 4.6 |
| 20" | 5.2 |
| 24"+ | 5.8 |

### Detailed Calculation Method

For more accurate calculations, the system uses both deflection and stress criteria:

**Deflection-based spacing:**

$$L_{deflection} = \left(\frac{\delta_{max} \cdot 384 \cdot E \cdot I}{5 \cdot w}\right)^{0.25}$$

**Stress-based spacing:**

$$L_{stress} = \sqrt{\frac{8 \cdot \sigma_{allow} \cdot Z}{w}}$$

Where:
- $\delta_{max}$ = Maximum allowable deflection (typically 12.5 mm)
- $E$ = Young's modulus (GPa)
- $I$ = Second moment of area (m⁴)
- $w$ = Weight per unit length including contents and insulation (N/m)
- $\sigma_{allow}$ = Allowable bending stress (MPa)
- $Z$ = Section modulus (m³)

### Java Example

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

calc.setOuterDiameter(0.2191);     // 8"
calc.setNominalWallThickness(0.00823);
calc.setMaterialGrade("A106-B");
calc.setDesignTemperature(50.0);
calc.setMixtureDensity(800.0);     // Liquid density

// Calculate support spacing
double spacing = calc.calculateSupportSpacing();
double asmeSpacing = calc.calculateSupportSpacingASME();

// Calculate number of supports
int numSupports = calc.calculateNumberOfSupports(100.0); // 100m pipe

System.out.println("Calculated spacing: " + spacing + " m");
System.out.println("ASME spacing: " + asmeSpacing + " m");
System.out.println("Number of supports: " + numSupports);
```

---

## Vibration Screening

### Acoustic Induced Vibration (AIV)

AIV screening per Energy Institute Guidelines uses acoustic power level:

$$P_{acoustic} = 3.2 \times 10^{-9} \cdot \dot{m} \cdot P_1 \cdot \left(\frac{\Delta P}{P_1}\right)^{3.6} \cdot \left(\frac{T}{273}\right)^{0.8}$$

Where:
- $\dot{m}$ = Mass flow rate (kg/s)
- $P_1$ = Upstream pressure (Pa)
- $\Delta P$ = Pressure drop (Pa)
- $T$ = Temperature (K)

> **Note:** AIV is also available as a capacity constraint on `PipeBeggsAndBrills` and `ThrottlingValve` classes via `calculateAIV()` and `setMaxDesignAIV()` methods. See [Capacity Constraint Framework](CAPACITY_CONSTRAINT_FRAMEWORK.md#acoustic-induced-vibration-aiv-analysis) for details.

### AIV Risk Levels (Acoustic Power Based)

| Acoustic Power (kW) | Risk Level | Action Required |
|---------------------|------------|-----------------|
| < 1 | LOW | No action required |
| 1 - 10 | MEDIUM | Review piping layout |
| 10 - 25 | HIGH | Detailed analysis required |
| > 25 | VERY HIGH | Mitigation required |

### Likelihood of Failure Assessment

| Screening Parameter | LOF Category | Action Required |
|---------------------|--------------|-----------------|
| < 10⁴ | Low (0.1) | No action |
| 10⁴ - 10⁵ | Medium-Low (0.3) | Monitor |
| 10⁵ - 10⁶ | Medium-High (0.6) | Detailed analysis |
| > 10⁶ | High (0.9) | Mitigation required |

### Flow Induced Vibration (FIV)

FIV screening considers vortex shedding frequency vs. pipe natural frequency:

$$f_n = \frac{\pi}{2} \sqrt{\frac{E \cdot I}{m \cdot L^4}}$$

$$f_{vs} = \frac{St \cdot V}{D}$$

Lock-in risk exists when:
$$0.8 \cdot f_n < f_{vs} < 1.2 \cdot f_n$$

### Java Example

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

calc.setMassFlowRate(5.0);
calc.setOuterDiameter(0.1524);     // 6"
calc.setNominalWallThickness(0.00711);

// Calculate AIV
double acousticPower = calc.calculateAcousticPowerLevel(
    70.0,   // Upstream pressure (bara)
    50.0,   // Downstream pressure (bara)
    50.0,   // Temperature (°C)
    20.0    // Molecular weight
);

double lof = calc.calculateAIVLikelihoodOfFailure(0.1524, 0.3048);

// Calculate FIV
double fivNumber = calc.calculateFIVScreening(3.5); // 3.5m span
boolean lockInRisk = calc.checkLockInRisk();

System.out.println("Acoustic power: " + acousticPower + " W");
System.out.println("AIV LOF: " + lof);
System.out.println("FIV screening number: " + fivNumber);
System.out.println("Lock-in risk: " + lockInRisk);
```

---

## Stress Analysis

### ASME B31.3 Stress Categories

| Stress Category | Formula | Allowable |
|-----------------|---------|-----------|
| **Sustained** | $S_L = \frac{P \cdot D}{2t} + \frac{M_A}{Z}$ | ≤ $S_h$ |
| **Expansion** | $S_E = \sqrt{S_b^2 + 4S_t^2}$ | ≤ $S_A$ |
| **Occasional** | $S_L + S_{occ}$ | ≤ 1.33 $S_h$ |

Where:
- $S_A = f(1.25 S_c + 0.25 S_h)$ (Allowable expansion stress range)
- $f$ = Stress range reduction factor

### Sustained Stress Calculation

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

calc.setDesignPressure(50.0);      // bar
calc.setOuterDiameter(0.2032);     // 8"
calc.setNominalWallThickness(0.00823);
calc.setMaterialGrade("A106-B");
calc.setDesignTemperature(100.0);

// Calculate stresses
double allowable = calc.calculateAllowableStress();
double sustained = calc.calculateSustainedStress(3.7); // 3.7m span

System.out.println("Allowable stress: " + allowable + " MPa");
System.out.println("Sustained stress: " + sustained + " MPa");
System.out.println("Stress ratio: " + (sustained/allowable));
```

---

## Thermal Expansion

### Free Expansion

$$\Delta L = \alpha \cdot L \cdot \Delta T$$

### Expansion Loop Sizing

For a U-loop configuration:

$$L_{loop} = \sqrt{\frac{3 \cdot D \cdot \Delta L}{0.03}}$$

### Anchor Force

$$F_{anchor} = E \cdot A \cdot \alpha \cdot \Delta T$$

### Java Example

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

calc.setOuterDiameter(0.2032);
calc.setNominalWallThickness(0.00823);
calc.setInstallationTemperature(20.0);   // °C
calc.setOperatingTemperature(80.0);      // °C
calc.setMaterialGrade("A106-B");

// Calculate thermal expansion
double thermalStress = calc.calculateThermalExpansionStress(50.0); // 50m between anchors

System.out.println("Free expansion: " + calc.getFreeExpansion() + " mm");
System.out.println("Required loop length: " + calc.getRequiredLoopLength() + " m");
System.out.println("Anchor force: " + calc.getAnchorForce() + " kN");
System.out.println("Thermal stress: " + thermalStress + " MPa");
```

---

## Pipe Schedules and Materials

### Schedule Selection

The `PipeSchedule` enum provides standard ASME schedules:

| Schedule | Wall Category | Typical Use |
|----------|--------------|-------------|
| SCH_5 | Thin wall | Low pressure utility |
| SCH_10 | Light weight | Instrument air, low-P water |
| SCH_40 | Standard | General process |
| SCH_80 | Extra strong | High pressure, corrosive |
| SCH_160 | Double extra strong | Very high pressure |
| STD | Standard weight | API standard |
| XS | Extra strong | API extra strong |
| XXS | Double extra strong | Extreme service |

### Standard Pipe Dimensions

Built-in dimensions for common sizes:

| NPS | OD (mm) | SCH 40 t (mm) | SCH 80 t (mm) |
|-----|---------|---------------|---------------|
| 2" | 60.3 | 3.91 | 5.54 |
| 4" | 114.3 | 6.02 | 8.51 |
| 6" | 168.3 | 7.11 | 10.97 |
| 8" | 219.1 | 8.23 | 12.70 |
| 10" | 273.1 | 9.27 | 12.70 |
| 12" | 323.9 | 10.48 | 12.70 |
| 16" | 406.4 | 12.70 | 15.88 |
| 20" | 508.0 | 12.70 | 15.88 |
| 24" | 609.6 | 14.22 | 17.78 |

---

## Insulation Types

The `InsulationType` enum provides common insulation materials with thermal properties:

| Type | Conductivity (W/m·K) | Density (kg/m³) | Max Temp (°C) |
|------|---------------------|-----------------|---------------|
| NONE | - | - | - |
| MINERAL_WOOL | 0.040 | 100 | 650 |
| CALCIUM_SILICATE | 0.055 | 240 | 650 |
| POLYURETHANE_FOAM | 0.025 | 40 | 120 |
| AEROGEL | 0.015 | 150 | 650 |
| CELLULAR_GLASS | 0.045 | 120 | 430 |
| HEAT_TRACED | 0.040 | 100 | 200 |

### Java Example

```java
TopsidePiping pipe = TopsidePiping.createProcessGas("Gas Header", feed);

// Set insulation
pipe.setInsulation(TopsidePiping.InsulationType.MINERAL_WOOL, 0.05); // 50mm

// Get insulation properties
double conductivity = pipe.getInsulationTypeEnum().getThermalConductivity();
double density = pipe.getInsulationTypeEnum().getDensity();

System.out.println("Insulation conductivity: " + conductivity + " W/(m·K)");
System.out.println("Insulation density: " + density + " kg/m³");
```

---

## JSON Reporting

### Complete Design Report

The calculator provides comprehensive JSON output:

```java
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

// Configure and run calculations...
calc.performDesignVerification();

String json = calc.toJson();
System.out.println(json);
```

### JSON Structure

```json
{
  "velocityAnalysis": {
    "actualVelocity_m_s": 12.5,
    "erosionalVelocity_m_s": 25.0,
    "erosionalCFactor": 100.0,
    "velocityRatio": 0.5,
    "velocityCheckPassed": true
  },
  "vibrationAnalysis": {
    "acousticPowerLevel_W": 1500.0,
    "aivLikelihoodOfFailure": 0.3,
    "fivScreeningNumber": 0.05,
    "pipeNaturalFrequency_Hz": 15.2,
    "vibrationCheckPassed": true
  },
  "supportAnalysis": {
    "calculatedSupportSpacing_m": 3.7,
    "maxAllowedDeflection_mm": 12.5,
    "totalWeightPerMeter_kg_m": 45.2,
    "supportCheckPassed": true
  },
  "stressAnalysis": {
    "allowableStress_MPa": 138.0,
    "sustainedStress_MPa": 85.0,
    "thermalExpansionStress_MPa": 45.0,
    "stressCheckPassed": true
  },
  "thermalExpansion": {
    "installationTemperature_C": 20.0,
    "operatingTemperature_C": 80.0,
    "freeExpansion_mm": 36.0,
    "requiredLoopLength_m": 8.5,
    "anchorForce_kN": 125.0
  },
  "appliedStandards": [
    "API-RP-14E - Erosional Velocity",
    "ASME B31.3 Table A-1 - Allowable Stress",
    "NORSOK L-002 - Pipe Support Spacing",
    "Energy Institute Guidelines - AIV Assessment"
  ]
}
```

---

## Examples

### Complete Design Workflow

```java
import neqsim.process.equipment.pipeline.TopsidePiping;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesign;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

// Create fluid system
SystemInterface fluid = new SystemSrkEos(298.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.setMixingRule("classic");

// Create feed stream
Stream feed = new Stream("Feed", fluid);
feed.setFlowRate(10000.0, "kg/hr");
feed.setTemperature(40.0, "C");
feed.setPressure(50.0, "bara");
feed.run();

// Create topside piping
TopsidePiping gasHeader = TopsidePiping.createProcessGas("Gas Header", feed);
gasHeader.setLength(50.0);
gasHeader.setDiameter(0.2032);  // 8 inch
gasHeader.setElevation(0.0);
gasHeader.setOperatingEnvelope(5.0, 55.0, -10.0, 60.0);
gasHeader.setFittings(4, 2, 1, 2);  // 4 elbows, 2 tees, 1 reducer, 2 valves
gasHeader.setInsulation(TopsidePiping.InsulationType.MINERAL_WOOL, 0.05);
gasHeader.setFlangeRating(300);
gasHeader.run();

// Initialize and configure mechanical design
TopsidePipingMechanicalDesign design = gasHeader.getTopsideMechanicalDesign();
design.setMaxOperationPressure(55.0);
design.setMaxOperationTemperature(60.0 + 273.15);
design.setMaterialGrade("A106-B");
design.setDesignStandardCode("ASME-B31.3");
design.setCompanySpecificDesignStandards("Equinor");

// Run design calculations
design.readDesignSpecifications();
design.calcDesign();

// Get results
TopsidePipingMechanicalDesignCalculator calc = design.getTopsideCalculator();
System.out.println("Support spacing: " + calc.getSupportSpacing() + " m");
System.out.println("Allowable stress: " + calc.getAllowableStress() + " MPa");
System.out.println("Velocity check: " + calc.isVelocityCheckPassed());
System.out.println("Vibration check: " + calc.isVibrationCheckPassed());
System.out.println("Stress check: " + calc.isStressCheckPassed());

// Export JSON report
String json = design.toJson();
System.out.println(json);
```

### Velocity Sizing Example

```java
// Size pipe for given flow rate
TopsidePipingMechanicalDesignCalculator calc = new TopsidePipingMechanicalDesignCalculator();

calc.setMassFlowRate(5.0);         // 5 kg/s
calc.setMixtureDensity(50.0);      // Gas at 50 kg/m³
calc.setErosionalCFactor(100.0);

// Calculate minimum diameter
double minDiameter = calc.calculateMinimumDiameter();
System.out.println("Minimum pipe ID: " + (minDiameter * 1000) + " mm");

// Select next standard size (e.g., 8" SCH 40)
calc.setOuterDiameter(0.2191);
calc.setNominalWallThickness(0.00823);

// Verify velocity
calc.calculateActualVelocity();
calc.calculateErosionalVelocity();
boolean ok = calc.checkVelocityLimits();

System.out.println("Actual velocity: " + calc.getActualVelocity() + " m/s");
System.out.println("Erosional velocity: " + calc.getErosionalVelocity() + " m/s");
System.out.println("Acceptable: " + ok);
```

---

## Python Integration

### Using neqsim-python

```python
from neqsim.thermo import fluid
from neqsim import jNeqSim

# Create fluid
gas = fluid('srk')
gas.addComponent('methane', 0.9)
gas.addComponent('ethane', 0.07)
gas.addComponent('propane', 0.03)
gas.setMixingRule('classic')

# Create stream
Stream = jNeqSim.process.equipment.stream.Stream
feed = Stream("Feed", gas)
feed.setFlowRate(10000.0, "kg/hr")
feed.setTemperature(40.0, "C")
feed.setPressure(50.0, "bara")
feed.run()

# Create topside piping
TopsidePiping = jNeqSim.process.equipment.pipeline.TopsidePiping
gasHeader = TopsidePiping.createProcessGas("Gas Header", feed)
gasHeader.setLength(50.0)
gasHeader.setDiameter(0.2032)
gasHeader.setElevation(0.0)
gasHeader.run()

# Get mechanical design
design = gasHeader.getTopsideMechanicalDesign()
design.setMaxOperationPressure(55.0)
design.setMaterialGrade("A106-B")
design.readDesignSpecifications()
design.calcDesign()

# Get calculator results
calc = design.getTopsideCalculator()
print(f"Support spacing: {calc.getSupportSpacing():.2f} m")
print(f"Velocity OK: {calc.isVelocityCheckPassed()}")

# Export JSON
import json
report = json.loads(design.toJson())
print(json.dumps(report, indent=2))
```

### Velocity Analysis Script

```python
from neqsim import jNeqSim

# Create calculator directly
Calculator = jNeqSim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesignCalculator
calc = Calculator()

# Configure
calc.setMassFlowRate(5.0)
calc.setMixtureDensity(80.0)
calc.setOuterDiameter(0.2032)
calc.setNominalWallThickness(0.00823)
calc.setMaterialGrade("A106-B")
calc.setDesignTemperature(50.0)

# Run all checks
calc.performDesignVerification()

# Print results
print(f"Erosional velocity: {calc.getErosionalVelocity():.2f} m/s")
print(f"Actual velocity: {calc.getActualVelocity():.2f} m/s")
print(f"Support spacing: {calc.getSupportSpacing():.2f} m")
print(f"Allowable stress: {calc.getAllowableStress():.1f} MPa")

# Get full JSON
import json
results = json.loads(calc.toJson())
for key, value in results.items():
    print(f"\n{key}:")
    if isinstance(value, dict):
        for k, v in value.items():
            print(f"  {k}: {v}")
```

---

## Database Integration

### TechnicalRequirements_Process Table

The design system loads parameters from the database:

```sql
SELECT ParameterName, MinValue, MaxValue, Unit, Standard
FROM TechnicalRequirements_Process
WHERE EquipmentType = 'TopsidePiping'
```

### Available Parameters

| Parameter | Description | Unit | Standard |
|-----------|-------------|------|----------|
| maxGasVelocity | Maximum gas velocity | m/s | NORSOK L-002 |
| maxLiquidVelocity | Maximum liquid velocity | m/s | NORSOK L-002 |
| erosionalCFactor | API RP 14E C-factor | - | API RP 14E |
| corrosionAllowance | Corrosion allowance | mm | ASME B31.3 |
| jointEfficiency | Weld joint efficiency | - | ASME B31.3 |
| designFactor | Design factor | - | ASME B31.3 |
| fabricationTolerance | Manufacturing tolerance | - | ASME B31.3 |

### Loading Custom Parameters

```java
TopsidePipingMechanicalDesignDataSource dataSource = 
    new TopsidePipingMechanicalDesignDataSource();

TopsidePipingMechanicalDesignCalculator calc = 
    new TopsidePipingMechanicalDesignCalculator();

// Load parameters for specific company
dataSource.loadIntoCalculator(calc, "Equinor", "ASME-B31.3", "PROCESS_GAS");

// Or load specific categories
dataSource.loadVelocityLimits(calc, "Equinor", "PROCESS_GAS");
dataSource.loadVibrationParameters(calc, "Equinor");
```

---

## See Also

- [Pipeline Mechanical Design](pipeline_mechanical_design) - Subsea and onshore pipeline design
- [Riser Mechanical Design](riser_mechanical_design) - Riser mechanical design
- [Mechanical Design Standards](mechanical_design_standards) - Standards database
- [PipeBeggsAndBrills](PipeBeggsAndBrills) - Flow modeling documentation
- [Process Design Guide](process_design_guide) - Overall process design guide
