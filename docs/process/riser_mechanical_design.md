---
title: Riser Mechanical Design
description: Comprehensive documentation for riser mechanical design in NeqSim, including catenary mechanics, VIV analysis, fatigue life estimation, and dynamic response calculations per industry standards.
---

# Riser Mechanical Design

Comprehensive documentation for riser mechanical design in NeqSim, including catenary mechanics, VIV analysis, fatigue life estimation, and dynamic response calculations per industry standards.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Riser Types](#riser-types)
- [Design Standards](#design-standards)
- [Catenary Mechanics](#catenary-mechanics)
- [Top Tensioned Risers](#top-tensioned-risers)
- [VIV Analysis](#viv-analysis)
- [Fatigue Analysis](#fatigue-analysis)
- [Dynamic Response](#dynamic-response)
- [Database Integration](#database-integration)
- [JSON Reporting](#json-reporting)
- [Examples](#examples)

---

## Overview

The riser mechanical design system provides:

- **Catenary mechanics** for SCR, flexible, and lazy-wave risers
- **Top tension calculations** for TTR systems
- **VIV (Vortex-Induced Vibration)** response and fatigue
- **Touchdown point stress** analysis
- **Dynamic stress** from waves and heave motion
- **Fatigue life estimation** combining all damage sources
- **Database-driven design parameters** from DNV, API, and company standards

**Location:** `neqsim.process.mechanicaldesign.pipeline`

**Classes:**
- `RiserMechanicalDesign` - Main design class for risers
- `RiserMechanicalDesignCalculator` - Riser-specific calculations
- `RiserMechanicalDesignDataSource` - Database access for standards

---

## Architecture

### Class Hierarchy

```
RiserMechanicalDesign extends PipelineMechanicalDesign
├── RiserMechanicalDesignCalculator extends PipeMechanicalDesignCalculator
│   ├── Catenary calculations (top tension, TDP stress)
│   ├── TTR calculations (tension, stroke)
│   ├── VIV analysis (frequency, amplitude, fatigue)
│   ├── Dynamic response (wave, heave stress)
│   └── Fatigue life (combined sources)
├── RiserMechanicalDesignDataSource
│   ├── TechnicalRequirements_Process (company-specific)
│   └── dnv_iso_en_standards (industry standards)
└── Inherits from PipelineMechanicalDesign
    ├── Wall thickness calculations
    ├── Stress analysis
    └── Weight and buoyancy
```

### Design Flow

```
1. Create Riser with type and water depth
2. Set environmental conditions (current, waves, heave)
3. Run riser simulation
4. Initialize mechanical design
5. Load design parameters from database
6. Calculate riser-specific parameters
7. Perform fatigue analysis
8. Export to JSON
```

---

## Riser Types

### Steel Catenary Riser (SCR)

Free-hanging catenary configuration from FPSO or semi-submersible.

**Key calculations:**
- Catenary top tension
- Touchdown point stress and curvature
- Touchdown zone length

```java
Riser scr = Riser.createSCR("Production SCR", inletStream, 800.0);
scr.setTopAngle(12.0);        // Degrees from vertical
scr.setDepartureAngle(18.0);  // Degrees from horizontal at TDP
```

### Top Tensioned Riser (TTR)

Tensioned from TLP or Spar platform with tensioner system.

**Key calculations:**
- Applied top tension
- Tension variation from heave
- Stroke requirement

```java
Riser ttr = Riser.createTTR("Export TTR", inletStream, 500.0);
ttr.setAppliedTopTension(2500.0);       // kN
ttr.setTensionVariationFactor(0.15);    // 15% variation
ttr.setPlatformHeaveAmplitude(2.5);     // meters
```

### Lazy-Wave Riser

SCR with buoyancy modules creating wave shape to reduce TDP stress.

**Key calculations:**
- Buoyancy effect on tension
- Reduced touchdown stress

```java
Riser lazyWave = Riser.createLazyWave("Gas Export", inletStream, 1200.0, 400.0);
lazyWave.setBuoyancyModuleLength(150.0);  // meters
lazyWave.setBuoyancyPerMeter(500.0);      // N/m
```

### Flexible Riser

Unbonded flexible pipe for dynamic applications.

**Key features:**
- Lower bending stiffness
- Larger minimum bend radius
- Fatigue in armor wires

```java
Riser flexible = Riser.createFlexible("Water Injection", inletStream, 300.0);
```

### Hybrid Riser

Tower riser with flexible jumper, for ultra-deep water.

```java
Riser hybrid = Riser.createHybrid("Deepwater Export", inletStream, 2000.0);
```

---

## Design Standards

### Supported Standards

| Standard | Version | Scope |
|----------|---------|-------|
| **DNV-OS-F201** | 2010 | Dynamic Risers - main design standard |
| **DNV-RP-F204** | 2010 | Riser Fatigue |
| **DNV-RP-C203** | 2021 | Fatigue Design of Offshore Structures |
| **DNV-RP-C205** | 2021 | Environmental Conditions and Loads |
| **API RP 2RD** | 2013 | Riser Design for FPS |
| **API RP 17B** | 2014 | Flexible Pipe |

### Key Parameters from Database

#### DNV-OS-F201 (Dynamic Risers)

| Parameter | Value Range | Description |
|-----------|-------------|-------------|
| Usage Factor | 0.77 - 0.83 | Resistance utilization |
| Safety Class (Low) | 1.046 | Low consequence |
| Safety Class (Medium) | 1.138 | Medium consequence |
| Safety Class (High) | 1.308 | High consequence |
| Dynamic Amplification Factor | 1.1 - 1.3 | DAF for dynamic loading |
| Max Utilization | 0.80 | Combined loading limit |

#### DNV-RP-F204 (Riser Fatigue)

| Parameter | Value | Description |
|-----------|-------|-------------|
| Fatigue Design Factor | 3 - 10 | Per safety class |
| S-N Parameter (seawater) | 12.164 | log(a) for D-curve |
| S-N Slope | 3.0 | m parameter |
| SCF (girth weld) | 1.2 - 1.5 | Stress concentration |

#### DNV-RP-C205 (Environmental Loads)

| Parameter | Value | Description |
|-----------|-------|-------------|
| Strouhal Number | 0.18 - 0.22 | VIV frequency |
| Drag Coefficient | 0.9 - 1.2 | Bare cylinder |
| Added Mass Coefficient | 1.0 | Hydrodynamic mass |
| Lift Coefficient | 0.8 - 1.0 | VIV lift |

---

## Catenary Mechanics

### Top Tension Calculation

For a catenary riser, the top tension is calculated from:

$$T_{top} = \frac{w \cdot H}{\sin(\theta_{top})}$$

Where:
- $w$ = submerged weight per meter (N/m)
- $H$ = water depth (m)
- $\theta_{top}$ = angle from horizontal at top

```java
RiserMechanicalDesignCalculator calc = design.getRiserCalculator();
calc.calculateCatenaryTopTension();

double topTension = calc.getTopTension();       // kN
double bottomTension = calc.getBottomTension(); // kN
double catenaryParam = calc.getCatenaryParameter(); // m
```

### Touchdown Point Stress

Bending stress at the touchdown point:

$$\sigma_b = \frac{E \cdot D_o}{2 \cdot R_{TDP}}$$

Where:
- $E$ = Young's modulus (210 GPa for steel)
- $D_o$ = outer diameter
- $R_{TDP}$ = radius of curvature at touchdown

```java
calc.calculateTouchdownPointStress();

double tdpStress = calc.getTouchdownPointStress();     // MPa
double tdpRadius = calc.getTouchdownCurvatureRadius(); // m
double tdpLength = calc.getTouchdownZoneLength();      // m
```

---

## Top Tensioned Risers

### TTR Tension

For TTR, the applied tension must exceed the riser weight plus environmental loads:

$$T_{applied} > T_{riser} + T_{environmental}$$

```java
calc.setAppliedTopTension(2500.0);  // kN
calc.setTensionVariationFactor(0.15);

calc.calculateTTRTension();

double topTension = calc.getTopTension();
double minTension = calc.getMinTopTension();
double maxTension = calc.getMaxTopTension();
```

### Stroke Requirement

Tensioner stroke required for heave compensation:

$$Stroke = 2 \cdot A_{heave} \cdot (1 + \text{margin})$$

```java
calc.setPlatformHeaveAmplitude(3.0);
calc.calculateStrokeRequirement();

double stroke = calc.getStrokeRequirement();  // m
```

---

## VIV Analysis

### Vortex Shedding Frequency

$$f_v = \frac{St \cdot V}{D}$$

Where:
- $St$ = Strouhal number (≈ 0.2)
- $V$ = current velocity (m/s)
- $D$ = outer diameter (m)

### Lock-In Detection

Lock-in occurs when the vortex shedding frequency is close to a natural frequency:

$$0.7 < \frac{f_v}{f_n} < 1.3$$

```java
calc.calculateVIVResponse();

double vortexFreq = calc.getVortexSheddingFrequency();  // Hz
double naturalFreq = calc.getNaturalFrequency();         // Hz
boolean lockIn = calc.isVIVLockIn();
double amplitude = calc.getVIVAmplitude();               // A/D ratio
```

### VIV Fatigue Damage

Annual fatigue damage from VIV:

$$D_{VIV} = \frac{n \cdot f_v \cdot 3.15 \times 10^7}{N_{cycles}}$$

```java
calc.calculateVIVFatigueDamage();

double vivDamage = calc.getVIVFatigueDamage();  // per year
```

---

## Fatigue Analysis

### Combined Fatigue Life

Total fatigue life combines multiple damage sources:

$$\text{Life} = \frac{1}{D_{VIV} + D_{wave} + D_{TDP} + D_{heave}}$$

```java
calc.calculateRiserFatigueLife();

double fatigueLife = calc.getRiserFatigueLife();  // years
double totalDamage = calc.getTotalFatigueDamageRate();  // per year
```

### S-N Curve

Fatigue life per DNV-RP-C203:

$$\log N = \log a - m \cdot \log \Delta\sigma$$

Where:
- $N$ = cycles to failure
- $a$ = S-N parameter (10^12.164 for seawater with CP)
- $m$ = slope (3.0)
- $\Delta\sigma$ = stress range (MPa)

---

## Dynamic Response

### Wave-Induced Stress

Stress from wave loading:

$$\sigma_{wave} = DAF \cdot \frac{H_s \cdot \rho \cdot g \cdot D}{16 \cdot t}$$

```java
calc.setSignificantWaveHeight(4.0);
calc.setPeakWavePeriod(12.0);
calc.calculateWaveInducedStress();

double waveStress = calc.getWaveInducedStress();  // MPa
```

### Heave-Induced Stress

For TTR, heave motion induces axial stress variation:

```java
calc.setPlatformHeaveAmplitude(3.0);
calc.setPlatformHeavePeriod(10.0);
calc.calculateHeaveInducedStress();

double heaveStress = calc.getHeaveInducedStress();  // MPa
```

---

## Database Integration

### Loading Design Parameters

Parameters are loaded from `TechnicalRequirements_Process` and `dnv_iso_en_standards` tables:

```java
RiserMechanicalDesign design = riser.getRiserMechanicalDesign();
design.setDesignStandardCode("DNV-OS-F201");
design.setCompanySpecificDesignStandards("Equinor");

design.readDesignSpecifications();  // Loads from database
```

### Configurable Parameters

| Parameter | Method | Source |
|-----------|--------|--------|
| Strouhal Number | `setStrouhalNumber()` | DNV-RP-C205 |
| Drag Coefficient | `setDragCoefficient()` | DNV-RP-C205 |
| Added Mass | `setAddedMassCoefficient()` | DNV-RP-C205 |
| S-N Parameter | `setSnParameter()` | DNV-RP-C203 |
| S-N Slope | `setSnSlope()` | DNV-RP-C203 |
| Fatigue Design Factor | `setFatigueDesignFactor()` | DNV-RP-F204 |
| DAF | `setDynamicAmplificationFactor()` | DNV-OS-F201 |
| SCF | `setStressConcentrationFactor()` | DNV-RP-F204 |

---

## JSON Reporting

### Full Design Report

```java
String json = design.toJson();
```

Output includes:
- Riser configuration (type, water depth, angles)
- Environmental conditions
- Top tension results
- Touchdown analysis
- VIV response
- Dynamic stresses
- Fatigue life
- Design acceptability

### Calculator Results

```java
String calcJson = design.getRiserCalculator().toJson();
```

---

## Examples

### Example 1: Complete SCR Design

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.Riser;
import neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesign;

// Production fluid
SystemSrkEos fluid = new SystemSrkEos(350.0, 100.0);
fluid.addComponent("methane", 0.80);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-heptane", 0.05);
fluid.setMixingRule("classic");

Stream production = new Stream("Production", fluid);
production.setFlowRate(50000.0, "kg/hr");
production.run();

// SCR configuration
Riser scr = Riser.createSCR("Export SCR", production, 1000.0);
scr.setDiameter(0.3048);           // 12 inch
scr.setTopAngle(12.0);
scr.setDepartureAngle(15.0);

// Environmental conditions
scr.setCurrentVelocity(0.8);
scr.setSeabedCurrentVelocity(0.3);
scr.setSignificantWaveHeight(4.0);
scr.setPeakWavePeriod(12.0);
scr.setPlatformHeaveAmplitude(3.0);

scr.run();

// Mechanical design
RiserMechanicalDesign design = scr.getRiserMechanicalDesign();
design.setMaxOperationPressure(150.0);
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-OS-F201");
design.setCompanySpecificDesignStandards("Equinor");

design.readDesignSpecifications();
design.calcDesign();

// Results
RiserDesignCalculator calc = design.getRiserCalculator();
System.out.println("=== SCR Design Results ===");
System.out.println("Top Tension: " + calc.getTopTension() + " kN");
System.out.println("TDP Stress: " + calc.getTouchdownPointStress() + " MPa");
System.out.println("VIV Lock-In: " + calc.isVIVLockIn());
System.out.println("Fatigue Life: " + calc.getRiserFatigueLife() + " years");
System.out.println("Design OK: " + design.isDesignAcceptable());
```

### Example 2: TTR for TLP

```java
Riser ttr = Riser.createTTR("Gas Export TTR", production, 600.0);
ttr.setDiameter(0.254);
ttr.setAppliedTopTension(3000.0);
ttr.setTensionVariationFactor(0.12);
ttr.setPlatformHeaveAmplitude(2.0);
ttr.run();

RiserMechanicalDesign ttrDesign = ttr.getRiserMechanicalDesign();
ttrDesign.setMaxOperationPressure(200.0);
ttrDesign.setMaterialGrade("X65");
ttrDesign.calcDesign();

RiserDesignCalculator ttrCalc = ttrDesign.getRiserCalculator();
System.out.println("TTR Tension: " + ttrCalc.getTopTension() + " kN");
System.out.println("Min Tension: " + ttrCalc.getMinTopTension() + " kN");
System.out.println("Stroke Req: " + ttrCalc.getStrokeRequirement() + " m");
```

### Example 3: Deepwater Lazy-Wave

```java
Riser lazyWave = Riser.createLazyWave("Ultra-Deep", production, 2500.0, 800.0);
lazyWave.setDiameter(0.3048);
lazyWave.setBuoyancyModuleLength(200.0);
lazyWave.setCurrentVelocity(0.5);
lazyWave.run();

RiserMechanicalDesign lwDesign = lazyWave.getRiserMechanicalDesign();
lwDesign.setMaxOperationPressure(180.0);
lwDesign.setMaterialGrade("X70");
lwDesign.calcDesign();

System.out.println("Lazy-Wave Top Tension: " + 
    lwDesign.getRiserCalculator().getTopTension() + " kN");
```

---

## Related Documentation

- [Pipelines](equipment/pipelines) - Riser class and configuration
- [Pipeline Mechanical Design](pipeline_mechanical_design) - Base calculations
- [Pipeline Design Math](pipeline_mechanical_design_math) - Formula reference
- [Subsea Systems](equipment/subsea_systems) - Subsea wells and flowlines
