---
title: Pipeline Mechanical Design
description: Comprehensive documentation for pipeline mechanical design in NeqSim, including wall thickness calculations, stress analysis, cost estimation, and detailed design per industry standards.
---

# Pipeline Mechanical Design

Comprehensive documentation for pipeline mechanical design in NeqSim, including wall thickness calculations, stress analysis, cost estimation, and detailed design per industry standards.

> **📘 See Also: Related Design Documentation**
> 
> - [Topside Piping Design](topside_piping_design.md) - Topside/platform piping with velocity, support spacing, vibration (AIV/FIV), stress analysis per ASME B31.3
> - [Riser Mechanical Design](riser_mechanical_design.md) - Riser design with catenary mechanics, VIV analysis, fatigue life per DNV-OS-F201

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Design Standards](#design-standards)
- [Wall Thickness Calculations](#wall-thickness-calculations)
- [Stress Analysis](#stress-analysis)
- [External Pressure and Buckling](#external-pressure-and-buckling)
- [Weight and Buoyancy](#weight-and-buoyancy)
- [Detailed Design Features](#detailed-design-features)
- [Cost Estimation](#cost-estimation)
- [JSON Reporting](#json-reporting)
- [Database Integration](#database-integration)
- [Examples](#examples)

---

## Overview

The pipeline mechanical design system provides:

- **Wall thickness sizing** per ASME B31.3/B31.4/B31.8 and DNV-OS-F101
- **Stress analysis** including hoop, longitudinal, and von Mises stress
- **External pressure design** for subsea pipelines
- **Weight and buoyancy** calculations
- **Support and expansion** design
- **Cost estimation** with bill of materials
- **JSON export** for reporting and integration

**Location:** `neqsim.process.mechanicaldesign.pipeline`

**Classes:**
- `PipelineMechanicalDesign` - Main design class
- `PipeMechanicalDesignCalculator` - Calculation engine
- `PipelineMechanicalDesignDataSource` - Database access

---

## Architecture

### Class Structure

```
PipelineMechanicalDesign extends MechanicalDesign
├── PipeMechanicalDesignCalculator (calculations)
├── PipelineMechanicalDesignDataSource (database)
└── MechanicalDesignResponse (JSON export)
```

### Design Flow

```
1. Set design conditions (pressure, temperature)
2. Select material grade and design code
3. Load standards from database
4. Calculate wall thickness
5. Perform stress analysis
6. Calculate weights and areas
7. Estimate costs
8. Export to JSON
```

---

## Design Standards

### Supported Standards

| Standard | Application | Key Features |
|----------|-------------|--------------|
| **ASME B31.3** | Process Piping | Allowable stress = SMYS/3 |
| **ASME B31.4** | Liquid Pipelines | Design factor 0.72 |
| **ASME B31.8** | Gas Transmission | Location classes 1-4 |
| **DNV-OS-F101** | Submarine Pipelines | Safety classes, resistance factors |
| **API 5L** | Line Pipe Specs | Material grades A-X120 |
| **ISO 13623** | Petroleum Pipelines | International standard |
| **NORSOK L-002** | Piping System Design | Norwegian standard |

### Material Grades (API 5L)

| Grade | SMYS (MPa) | SMTS (MPa) | Typical Application |
|-------|------------|------------|---------------------|
| A25 | 172 | 310 | Low-pressure utility |
| B | 241 | 414 | General service |
| X42 | 290 | 414 | Low-pressure pipelines |
| X52 | 359 | 455 | Process piping |
| X60 | 414 | 517 | High-pressure pipelines |
| X65 | 448 | 531 | Offshore standard |
| X70 | 483 | 565 | High-pressure gas |
| X80 | 552 | 621 | Very high strength |
| X100 | 690 | 760 | Ultra high strength |
| X120 | 827 | 931 | Extreme applications |

### ASME B31.8 Location Classes

| Class | Description | Design Factor (F) |
|-------|-------------|-------------------|
| Class 1 | Rural, <10 buildings | 0.72 |
| Class 2 | Semi-developed, 10-46 buildings | 0.60 |
| Class 3 | Developed, >46 buildings | 0.50 |
| Class 4 | High-density, multi-story | 0.40 |

### DNV-OS-F101 Safety Classes

| Safety Class | Description | γSC Factor |
|--------------|-------------|------------|
| Low | Minor environmental impact | 0.96 |
| Medium | Regional impact | 1.04 |
| High | Major environmental impact | 1.14 |

---

## Wall Thickness Calculations

### ASME B31.8 (Gas Transmission)

**Barlow Formula:**

$$t = \frac{P \cdot D}{2 \cdot S \cdot F \cdot E \cdot T}$$

Where:
- $t$ = minimum wall thickness (m)
- $P$ = design pressure (MPa)
- $D$ = outside diameter (m)
- $S$ = SMYS (MPa)
- $F$ = design factor (0.4-0.72)
- $E$ = longitudinal joint factor (1.0 for seamless)
- $T$ = temperature derating factor

**Code:**
```java
PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
calc.setDesignPressure(15.0);  // MPa
calc.setOuterDiameter(0.508, "m");  // 20 inch
calc.setMaterialGrade("X65");
calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_8);
calc.setLocationClass(2);  // Design factor = 0.60

double tMin = calc.calculateMinimumWallThickness();
System.out.println("Minimum wall thickness: " + (tMin * 1000) + " mm");
```

### ASME B31.3 (Process Piping)

$$t = \frac{P \cdot D}{2 \cdot (S \cdot E + P \cdot Y)}$$

Where:
- $S$ = allowable stress = SMYS/3
- $E$ = weld joint efficiency
- $Y$ = coefficient (0.4 for temperatures ≤ 482°C)

### DNV-OS-F101 (Submarine Pipelines)

$$t_1 = \frac{P_{li} - P_e}{p_b}$$

$$p_b = \frac{2 \cdot t \cdot f_y \cdot \alpha_U}{\sqrt{3} \cdot (D - t) \cdot \gamma_m \cdot \gamma_{SC}}$$

Where:
- $P_{li}$ = local incidental pressure
- $P_e$ = external pressure
- $f_y$ = yield strength
- $\alpha_U$ = material strength factor
- $\gamma_m$ = material resistance factor (1.15)
- $\gamma_{SC}$ = safety class resistance factor

**Code:**
```java
calc.setDesignCode(PipeMechanicalDesignCalculator.DNV_OS_F101);
calc.setWaterDepth(350.0);  // m
double tMin = calc.calculateMinimumWallThickness();
```

### Nominal Wall Thickness

After calculating minimum wall thickness, apply:

$$t_{nom} = \frac{t_{min} + t_{corr}}{f_{fab}}$$

Where:
- $t_{corr}$ = corrosion allowance (typically 3mm)
- $f_{fab}$ = fabrication tolerance (0.875 for seamless pipe)

---

## Stress Analysis

### Hoop Stress (Barlow)

$$\sigma_h = \frac{P \cdot D}{2 \cdot t}$$

**Code:**
```java
double hoopStress = calc.calculateHoopStress(designPressure);
double ratio = hoopStress / calc.getSmys() * 100;
System.out.println("Hoop stress: " + hoopStress + " MPa (" + ratio + "% SMYS)");
```

### Longitudinal Stress

For restrained pipe:

$$\sigma_L = \nu \cdot \sigma_h - E \cdot \alpha \cdot \Delta T + \frac{P \cdot D}{4 \cdot t}$$

Where:
- $\nu$ = Poisson's ratio (0.3)
- $E$ = Young's modulus (207,000 MPa)
- $\alpha$ = thermal expansion coefficient (11.7×10⁻⁶ /K)
- $\Delta T$ = temperature change from installation

For unrestrained pipe:

$$\sigma_L = \frac{P \cdot D}{4 \cdot t}$$

**Code:**
```java
double deltaT = 60.0;  // Temperature rise from installation
boolean restrained = true;  // Buried or anchored
double longStress = calc.calculateLongitudinalStress(designPressure, deltaT, restrained);
```

### Von Mises Equivalent Stress

$$\sigma_{vm} = \sqrt{\sigma_h^2 + \sigma_L^2 - \sigma_h \cdot \sigma_L + 3\tau^2}$$

For pipeline design (assuming τ ≈ 0):

$$\sigma_{vm} = \sqrt{\sigma_h^2 + \sigma_L^2 - \sigma_h \cdot \sigma_L}$$

**Code:**
```java
double vonMises = calc.calculateVonMisesStress(designPressure, deltaT, true);
boolean safe = calc.isDesignSafe();  // von Mises < 0.9 × SMYS
double margin = calc.calculateSafetyMargin();  // (SMYS - σvm) / SMYS
```

---

## External Pressure and Buckling

### External Pressure at Seabed

$$P_e = \rho_{sw} \cdot g \cdot h$$

Where:
- $\rho_{sw}$ = seawater density (1025 kg/m³)
- $g$ = gravity (9.81 m/s²)
- $h$ = water depth (m)

**Code:**
```java
calc.setWaterDepth(350.0);
double Pe = calc.calculateExternalPressure();  // MPa
```

### Collapse Pressure (DNV-OS-F101)

**Elastic Collapse:**

$$P_{el} = \frac{2 \cdot E \cdot (t/D)^3}{1 - \nu^2}$$

**Plastic Collapse:**

$$P_p = \frac{2 \cdot f_y \cdot t}{D}$$

**Combined Collapse:**
```java
double Pc = calc.calculateCollapsePressure();
```

### Propagation Buckling Pressure

$$P_{pr} = 35 \cdot f_y \cdot (t/D)^{2.5}$$

**Code:**
```java
double Ppr = calc.calculatePropagationBucklingPressure();
// Buckle arrestors required if Ppr < Pe
```

### Allowable Free Span Length

Based on vortex-induced vibration (VIV) avoidance:

$$L_{allow} = \left(\frac{\pi^2 \cdot E \cdot I}{4 \cdot m_e \cdot f_n^2}\right)^{0.25}$$

Where:
- $I$ = moment of inertia
- $m_e$ = effective mass (steel + added mass)
- $f_n$ = target natural frequency > 1.3 × vortex shedding frequency

**Code:**
```java
double currentVelocity = 0.5;  // m/s
double spanLength = calc.calculateAllowableSpanLength(currentVelocity);
```

---

## Weight and Buoyancy

### Weight Components

```java
calc.setPipelineLength(50000.0);  // 50 km
calc.setCoatingType("3LPE");
calc.setCoatingThickness(0.003);  // 3mm
calc.setConcreteCoatingThickness(0.050);  // 50mm CWC

calc.calculateWeightsAndAreas();

// Results per meter
double steelWeight = calc.getSteelWeightPerMeter();      // kg/m
double coatingWeight = calc.getCoatingWeightPerMeter();  // kg/m
double concreteWeight = calc.getConcreteWeightPerMeter(); // kg/m
double totalDry = calc.getTotalDryWeightPerMeter();      // kg/m
```

### Submerged Weight

$$W_{sub} = W_{dry} + W_{contents} - \rho_{sw} \cdot g \cdot V_{disp}$$

**Code:**
```java
double contentDensity = 800.0;  // kg/m³ (oil)
double submerged = calc.calculateSubmergedWeight(contentDensity);
// Negative = buoyant, Positive = sinks
```

### Concrete Coating for Stability

```java
double targetWeight = 50.0;  // kg/m submerged weight
double thickness = calc.calculateRequiredConcreteThickness(contentDensity, targetWeight);
```

---

## Detailed Design Features

### Support Spacing (Above-ground)

Based on deflection limits:

$$L = \left(\frac{384 \cdot E \cdot I \cdot \delta}{5 \cdot w}\right)^{0.25}$$

**Code:**
```java
double maxDeflection = 0.01;  // 10mm
double spacing = calc.calculateSupportSpacing(maxDeflection);
int numSupports = calc.getNumberOfSupports();
```

### Expansion Loop Sizing

$$L_{loop} = \sqrt{\frac{3 \cdot E \cdot D \cdot \Delta L}{\sigma_{allow}}}$$

Where $\Delta L = \alpha \cdot \Delta T \cdot L_{anchor}$

**Code:**
```java
double deltaT = 60.0;  // Temperature change
double loopLength = calc.calculateExpansionLoopLength(deltaT, "U-loop");
int numLoops = calc.getNumberOfExpansionLoops();
```

### Minimum Bend Radius

Per API 5L:

$$R_{min} = 18 \cdot D$$ (cold bends)

$$R_{min} = 5 \cdot D$$ (hot bends)

**Code:**
```java
double bendRadius = calc.calculateMinimumBendRadius();  // For cold bends
```

### Flange Class Selection

Per ASME B16.5:

| Class | Rating at 38°C (MPa) |
|-------|---------------------|
| 150 | 1.93 |
| 300 | 5.07 |
| 600 | 10.13 |
| 900 | 15.20 |
| 1500 | 25.33 |
| 2500 | 42.22 |

**Code:**
```java
int flangeClass = calc.selectFlangeClass();  // Based on design pressure
```

### Fatigue Life Estimation

Per DNV-RP-C203 (D-curve):

$$N = \frac{10^{11.764}}{S^3}$$

**Code:**
```java
double stressRange = 50.0;  // MPa
double cyclesPerYear = 1e6;
double fatigueLife = calc.estimateFatigueLife(stressRange, cyclesPerYear);
```

### Insulation Thickness

For temperature control:

```java
double inletTemp = 80.0;      // °C
double minArrivalTemp = 40.0; // °C
double massFlow = 50.0;       // kg/s
double cp = 2000.0;           // J/(kg·K)

double insThickness = calc.calculateInsulationThickness(
    inletTemp, minArrivalTemp, massFlow, cp);
```

---

## Cost Estimation

### Cost Components

| Component | Basis |
|-----------|-------|
| Steel | Weight × $/kg |
| Coating | Surface area × $/m² |
| Insulation | Volume × $/m³ |
| Concrete | Volume × $/m³ |
| Welding | Number of welds × $/weld |
| Flanges | Number of pairs × $/pair |
| Valves | Number × $/valve |
| Supports | Number × $/support |
| Anchors | Number × $/anchor |
| Installation | Length × $/m |
| Engineering | % of direct cost |
| Testing | % of direct cost |
| Contingency | % of direct cost |

### Cost Calculation

```java
// Set pipeline parameters
calc.setPipelineLength(50000.0);  // 50 km
calc.setNumberOfFlangePairs(10);
calc.setNumberOfValves(5);

// Set rates (optional - defaults available)
calc.setSteelPricePerKg(1.50);
calc.setFieldWeldCost(2500.0);
calc.setContingencyPercentage(0.15);

// Calculate costs
calc.calculateProjectCost();
calc.calculateLaborManhours();

// Get results
double totalCost = calc.getTotalProjectCost();
double directCost = calc.getTotalDirectCost();
double laborHours = calc.getTotalLaborManhours();
```

### Installation Cost Factors

| Method | Base Cost ($/m) | Depth Factor |
|--------|----------------|--------------|
| Onshore | 300 | +50 × burial_depth |
| S-lay | 800 | +2 × water_depth |
| J-lay | 1200 | +3 × water_depth |
| Reel-lay | 600 | +1.5 × water_depth |
| HDD | 1500 | - |

### Bill of Materials

```java
List<Map<String, Object>> bom = calc.generateBillOfMaterials();

for (Map<String, Object> item : bom) {
    System.out.println(item.get("item") + ": " + item.get("quantity") + " " + 
                       item.get("unit") + " - $" + item.get("totalCost_USD"));
}
```

---

## JSON Reporting

### Complete JSON Export

```java
PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipe.getMechanicalDesign();
design.calcDesign();
design.getCalculator().calculateProjectCost();

String json = design.toJson();
```

### JSON Structure

```json
{
  "equipmentType": "Pipeline",
  "designCode": "ASME_B31_8",
  "materialGrade": "X65",
  "pipelineLength_m": 50000.0,
  
  "designParameters": {
    "designPressure_MPa": 15.0,
    "designPressure_bar": 150.0,
    "designTemperature_C": 80.0,
    "outerDiameter_mm": 508.0,
    "corrosionAllowance_mm": 3.0
  },
  
  "materialProperties": {
    "smys_MPa": 448.0,
    "smts_MPa": 531.0,
    "youngsModulus_MPa": 207000.0,
    "steelDensity_kgm3": 7850.0
  },
  
  "designFactors": {
    "designFactor_F": 0.60,
    "jointFactor_E": 1.0,
    "temperatureDerating_T": 1.0,
    "locationClass": 2
  },
  
  "calculatedResults": {
    "minimumWallThickness_mm": 18.5,
    "maop_MPa": 14.2,
    "hoopStress_MPa": 287.0,
    "vonMisesStress_MPa": 265.0,
    "safetyMargin_percent": 40.8,
    "designIsSafe": true
  },
  
  "weightAndBuoyancy": {
    "steelWeight_kgm": 185.0,
    "totalDryWeight_kgm": 210.0,
    "submergedWeight_kgm": 120.0,
    "totalPipelineWeight_kg": 10500000.0
  },
  
  "detailedDesignResults": {
    "collapsePressure_MPa": 25.0,
    "propagationBucklingPressure_MPa": 8.5,
    "minimumBendRadius_m": 9.14,
    "allowableSpanLength_m": 45.0
  },
  
  "costEstimation": {
    "steelMaterialCost_USD": 15750000.0,
    "coatingCost_USD": 2000000.0,
    "weldingCost_USD": 10250000.0,
    "installationCost_USD": 40000000.0,
    "totalDirectCost_USD": 70000000.0,
    "totalProjectCost_USD": 91000000.0
  },
  
  "laborEstimation": {
    "totalLaborManhours": 125000.0
  }
}
```

---

## Database Integration

### Loading Design Parameters

```java
PipelineMechanicalDesign design = new PipelineMechanicalDesign(pipe);
design.setCompanySpecificDesignStandards("Equinor");
design.setDesignStandardCode("DNV-OS-F101");
design.setMaterialGrade("X65");

// Load from database
design.readDesignSpecifications();

// This queries:
// 1. MaterialPipeProperties - SMYS/SMTS
// 2. TechnicalRequirements_Process - Company factors
// 3. dnv_iso_en_standards - DNV safety class factors
// 4. norsok_standards - Additional requirements
```

### Database Tables

| Table | Content |
|-------|---------|
| `MaterialPipeProperties` | API 5L grade properties |
| `TechnicalRequirements_Process` | Company design parameters |
| `TechnicalRequirements_Piping` | Piping code requirements |
| `api_standards` | API 5L specifications |
| `asme_standards` | ASME B31 requirements |
| `dnv_iso_en_standards` | DNV/ISO/EN factors |
| `norsok_standards` | NORSOK requirements |

---

## Examples

### Example 1: Onshore Gas Pipeline (ASME B31.8)

```java
import neqsim.process.mechanicaldesign.pipeline.*;

// Create calculator
PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();

// Set design conditions
calc.setDesignPressure(10.0, "MPa");
calc.setDesignTemperature(60.0);
calc.setOuterDiameter(0.762, "m");  // 30 inch
calc.setMaterialGrade("X65");
calc.setPipelineLength(100000.0);  // 100 km

// Set design code
calc.setDesignCode(PipeMechanicalDesignCalculator.ASME_B31_8);
calc.setLocationClass(2);  // Semi-developed area

// Calculate
double tMin = calc.calculateMinimumWallThickness();
double maop = calc.calculateMAOP();
double testP = calc.calculateTestPressure();

System.out.println("=== ASME B31.8 Design ===");
System.out.println("Minimum wall thickness: " + (tMin * 1000) + " mm");
System.out.println("MAOP: " + (maop * 10) + " bar");
System.out.println("Test pressure: " + (testP * 10) + " bar");

// Stress analysis
double hoop = calc.calculateHoopStress(calc.getDesignPressure());
double vonMises = calc.calculateVonMisesStress(calc.getDesignPressure(), 40.0, true);

System.out.println("Hoop stress: " + hoop + " MPa (" + (100*hoop/calc.getSmys()) + "% SMYS)");
System.out.println("Von Mises stress: " + vonMises + " MPa");
System.out.println("Design is safe: " + calc.isDesignSafe());
```

### Example 2: Subsea Pipeline (DNV-OS-F101)

```java
// Create calculator
PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();

// Set design conditions
calc.setDesignPressure(20.0, "MPa");
calc.setDesignTemperature(80.0);
calc.setOuterDiameter(0.508, "m");  // 20 inch
calc.setMaterialGrade("X65");
calc.setPipelineLength(50000.0);  // 50 km

// DNV design code
calc.setDesignCode(PipeMechanicalDesignCalculator.DNV_OS_F101);
calc.setWaterDepth(350.0);
calc.setInstallationMethod("S-lay");

// Coating and concrete
calc.setCoatingType("3LPE");
calc.setCoatingThickness(0.003);  // 3mm
calc.setConcreteCoatingThickness(0.050);  // 50mm CWC

// Calculate
double tMin = calc.calculateMinimumWallThickness();
calc.setNominalWallThickness(Math.ceil(tMin * 1000) / 1000.0 + 0.002);  // Round up + 2mm

// External pressure and buckling
double Pe = calc.calculateExternalPressure();
double Pc = calc.calculateCollapsePressure();
double Ppr = calc.calculatePropagationBucklingPressure();

System.out.println("=== DNV-OS-F101 Design ===");
System.out.println("Minimum wall thickness: " + (tMin * 1000) + " mm");
System.out.println("External pressure: " + Pe + " MPa");
System.out.println("Collapse pressure: " + Pc + " MPa");
System.out.println("Propagation pressure: " + Ppr + " MPa");

// Weight and buoyancy
calc.calculateWeightsAndAreas();
double submerged = calc.calculateSubmergedWeight(800.0);  // Oil @ 800 kg/m³

System.out.println("Steel weight: " + calc.getSteelWeightPerMeter() + " kg/m");
System.out.println("Total dry weight: " + calc.getTotalDryWeightPerMeter() + " kg/m");
System.out.println("Submerged weight: " + submerged + " kg/m");

// Free span analysis
double spanLength = calc.calculateAllowableSpanLength(0.5);  // 0.5 m/s current
System.out.println("Allowable span length: " + spanLength + " m");
```

### Example 3: Complete Cost Estimation

```java
// Create calculator with full specifications
PipeMechanicalDesignCalculator calc = new PipeMechanicalDesignCalculator();
calc.setDesignPressure(15.0, "MPa");
calc.setOuterDiameter(0.508, "m");
calc.setMaterialGrade("X65");
calc.setDesignCode(PipeMechanicalDesignCalculator.DNV_OS_F101);
calc.setPipelineLength(50000.0);

// Installation parameters
calc.setInstallationMethod("S-lay");
calc.setWaterDepth(350.0);
calc.setNumberOfFlangePairs(10);
calc.setNumberOfValves(5);

// Coatings
calc.setCoatingType("3LPE");
calc.setCoatingThickness(0.003);
calc.setConcreteCoatingThickness(0.050);

// Calculate everything
calc.calculateMinimumWallThickness();
calc.calculateWeightsAndAreas();
calc.calculateJointsAndWelds();
calc.selectFlangeClass();
calc.calculateProjectCost();
calc.calculateLaborManhours();

// Print cost summary
System.out.println("=== COST ESTIMATION ===");
System.out.println("Total pipeline weight: " + calc.getTotalPipelineWeight()/1000 + " tonnes");
System.out.println("Number of joints: " + calc.getNumberOfJoints());
System.out.println("Number of welds: " + calc.getNumberOfFieldWelds());
System.out.println();
System.out.println("Direct Costs:");
System.out.println("  Steel: $" + String.format("%,.0f", calc.getSteelMaterialCost()));
System.out.println("  Coating: $" + String.format("%,.0f", calc.getCoatingCost()));
System.out.println("  Installation: $" + String.format("%,.0f", calc.getInstallationCost()));
System.out.println("  Total Direct: $" + String.format("%,.0f", calc.getTotalDirectCost()));
System.out.println();
System.out.println("Total Project Cost: $" + String.format("%,.0f", calc.getTotalProjectCost()));
System.out.println("Labor manhours: " + String.format("%,.0f", calc.getTotalLaborManhours()));

// Generate BOM
System.out.println("\n=== BILL OF MATERIALS ===");
List<Map<String, Object>> bom = calc.generateBillOfMaterials();
for (Map<String, Object> item : bom) {
    System.out.printf("%-25s %8s %-10s $%,15.0f%n",
        item.get("item"),
        item.get("quantity"),
        item.get("unit"),
        item.get("totalCost_USD"));
}
```

### Example 4: Integration with Process Simulation

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.AdiabaticPipe;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;

// Create fluid
SystemSrkEos gas = new SystemSrkEos(303.15, 150.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

// Create stream
Stream inlet = new Stream("Inlet", gas);
inlet.setFlowRate(20.0, "MSm3/day");
inlet.run();

// Create pipeline
AdiabaticPipe pipeline = new AdiabaticPipe("Export Pipeline", inlet);
pipeline.setLength(100000.0, "m");
pipeline.setDiameter(0.762, "m");
pipeline.run();

// Initialize mechanical design
pipeline.initMechanicalDesign();
PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipeline.getMechanicalDesign();

// Configure design
design.setMaxOperationPressure(150.0);  // bara
design.setMaxOperationTemperature(60.0);  // °C
design.setMaterialGrade("X65");
design.setLocationClass("Class 2");
design.setDesignStandardCode("ASME-B31.8");
design.setCompanySpecificDesignStandards("Equinor");

// Run design
design.readDesignSpecifications();
design.calcDesign();

// Get complete report
String jsonReport = design.toJson();
System.out.println(jsonReport);
```

---

## Related Documentation

- [Pipeline Simulation](equipment/pipeline_simulation.md) - Flow modeling
- [Mechanical Design Standards](mechanical_design_standards.md) - Standard details
- [Mechanical Design Database](mechanical_design_database.md) - Database tables
- [Mechanical Design Framework](mechanical_design.md) - Overall framework
