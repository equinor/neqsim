---
title: Water Treatment Equipment
description: "Documentation for produced water treatment equipment in NeqSim: hydrocyclones with physics-based d50, DSD integration, PDR model, liner sizing, OSPAR compliance, ASME VIII mechanical design, gas flotation units (IGF/DGF), treatment trains, and regulatory compliance."
---

# Water Treatment Equipment

Documentation for produced water treatment equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Hydrocyclone](#hydrocyclone)
  - [Liner Sizing and Capacity](#liner-sizing-and-capacity)
  - [Physics-Based d50 Calculation](#physics-based-d50-calculation)
  - [Droplet Size Distribution (DSD) Integration](#droplet-size-distribution-dsd-integration)
  - [Pressure Drop Ratio (PDR) Model](#pressure-drop-ratio-pdr-model)
  - [OSPAR Compliance](#ospar-compliance)
  - [Mechanical Design](#mechanical-design)
- [Gas Flotation Unit](#gas-flotation-unit)
- [Produced Water Treatment Train](#produced-water-treatment-train)
- [Design Considerations](#design-considerations)
- [Regulatory Compliance](#regulatory-compliance)

---

## Overview

**Package:** `neqsim.process.equipment.watertreatment`

Produced water treatment is critical for offshore oil and gas operations. NeqSim provides equipment models for simulating oil-in-water separation processes, helping engineers design systems that meet discharge regulations.

### Key Classes

| Class | Package | Description |
|-------|---------|-------------|
| `Hydrocyclone` | `process.equipment.watertreatment` | Centrifugal oil-water separator with physics-based d50, DSD, PDR model |
| `GasFlotationUnit` | `process.equipment.watertreatment` | IGF/DGF multi-stage flotation |
| `ProducedWaterTreatmentTrain` | `process.equipment.watertreatment` | Multi-stage treatment system |
| `HydrocycloneMechanicalDesign` | `process.mechanicaldesign.watertreatment` | ASME VIII vessel design, nozzle sizing, weight estimation |

---

## Hydrocyclone

### Overview

Hydrocyclones use centrifugal force to separate oil droplets from water. The swirling flow creates centrifugal acceleration many times greater than gravity, causing lighter oil droplets to migrate to the center and exit through the reject stream.

### Performance Characteristics

| Parameter | Typical Value | Range |
|-----------|--------------|-------|
| d50 cut size | 10-15 μm | 8-20 μm |
| d100 removal | 20-30 μm | 15-40 μm |
| Reject ratio | 1-3% | 0.5-5% |
| Pressure drop | 1-3 bar | 0.5-5 bar |
| Oil removal efficiency | 90-98% | 85-99% |

### Separation Efficiency Model

The grade efficiency is modeled using:

$$\eta(d) = 1 - \exp\left(-A \cdot \left(\frac{d}{d_{50}}\right)^n\right)$$

where:
- $d$ = droplet diameter (μm)
- $d_{50}$ = cut size (50% removal efficiency)
- $n$ = typically 2-4 (sharpness of separation)

### Basic Usage

```java
import neqsim.process.equipment.watertreatment.Hydrocyclone;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create produced water stream
SystemSrkEos water = new SystemSrkEos(323.15, 10.0);
water.addComponent("water", 0.995);
water.addComponent("n-heptane", 0.005);  // Oil phase
water.setMixingRule("classic");

Stream producedWater = new Stream("Produced Water", water);
producedWater.setFlowRate(500.0, "m3/hr");
producedWater.run();

// Create hydrocyclone
Hydrocyclone cyclone = new Hydrocyclone("HP Hydrocyclone", producedWater);
cyclone.setD50Microns(12.0);
cyclone.setRejectRatio(0.02);
cyclone.setPressureDrop(2.0);
cyclone.setOilRemovalEfficiency(0.95);
cyclone.run();

// Get results
System.out.println("Outlet OIW: " + cyclone.getOutletOilConcentrationMgL() + " mg/L");
System.out.println("Recovered oil: " + cyclone.getRecoveredOilM3h() + " m³/h");
```

### Configuration Methods

```java
// Set d50 cut size in microns
cyclone.setD50Microns(12.0);

// Set reject ratio (oil-rich stream / feed)
cyclone.setRejectRatio(0.02);

// Set pressure drop across cyclone
cyclone.setPressureDrop(2.0);

// Set target oil removal efficiency
cyclone.setOilRemovalEfficiency(0.95);

// Set inlet oil concentration
cyclone.setInletOilConcentration(1000.0);  // mg/L
```

### Output Streams

```java
// Treated water (underflow) - main outlet
Stream treatedWater = (Stream) cyclone.getOutletStream();

// Rejected oil-rich stream (overflow)
Stream oilReject = (Stream) cyclone.getOilOutStream();
```

### Design Validation

The `Hydrocyclone` includes differential pressure and efficiency validation:

```java
// Check if differential pressure is adequate (typically 1-3 bar)
boolean dpOk = cyclone.isDifferentialPressureAdequate();

// Calculate required inlet pressure for a target dP
double requiredInletP = cyclone.calcRequiredInletPressure(5.0);  // target outlet 5 bar

// Estimate efficiency from operating conditions
double estimatedEff = cyclone.estimateEfficiencyFromConditions(
    850.0,   // oil density kg/m3
    1025.0,  // water density kg/m3
    15.0     // droplet size microns
);

// Full validation summary
String summary = cyclone.getDesignValidationSummary();
System.out.println(summary);
```

### Hydrocyclone Design Parameters

| Parameter | Default | Method | Description |
|-----------|---------|--------|-------------|
| d50 cut size | 12 μm | `setD50Microns()` | 50% removal droplet size |
| Reject ratio | 2% | `setRejectRatio()` | Oil-rich stream fraction |
| Pressure drop | 2.0 bar | `setPressureDrop()` | Across cyclone |
| Min dP | 2.0 bar | — | Minimum design dP (constant) |
| Recommended dP | 5.0 bar | — | Recommended dP (constant) |
| Oil removal efficiency | 95% | `setOilRemovalEfficiency()` | Overall efficiency |
| Liner diameter | 35 mm | `setLinerDiameterMm()` | Cone insert diameter |
| Number of liners | — | `setNumberOfLiners()` | Active liner count |
| Spare liners | 0 | `setNumberOfSpareLiners()` | Spare liner count |
| Liners per vessel | 6 | `setLinersPerVessel()` | Liners per pressure housing |
| PDR | 1.8 | `setPDR()` | Pressure drop ratio |
| Sharpness index | 3.0 | `setSharpnessIndex()` | Grade efficiency curve shape |
| DSD dv50 | 30 μm | `setDv50Microns()` | Volume median droplet size |
| Geometric std dev | 2.5 | `setGeometricStdDev()` | DSD spread parameter |

### Standard Liner Sizes

Three standard liner diameters are available via `Hydrocyclone.STANDARD_LINER_SIZES_MM`:

| Liner Size | Design Flow | Min Flow | Max Flow | Typical Application |
|------------|-------------|----------|----------|---------------------|
| 35 mm | 5.0 m³/h | 2.0 m³/h | 8.0 m³/h | Standard de-oiling |
| 45 mm | 8.3 m³/h | 3.3 m³/h | 13.3 m³/h | Higher capacity |
| 60 mm | 14.7 m³/h | 5.9 m³/h | 23.5 m³/h | Large flow applications |

### Liner Sizing and Capacity

The hydrocyclone package consists of multiple liner inserts housed in pressure vessels. The sizing API determines the number of liners, vessels, and operating envelope:

```java
// Manual sizing
cyclone.setLinerDiameterMm(35.0);     // 35 mm liner
cyclone.setNumberOfLiners(30);         // 30 active liners
cyclone.setNumberOfSpareLiners(6);     // 6 spare liners
cyclone.setLinersPerVessel(9);         // 9 liners per vessel

// Auto-sizing from feed stream flow rate
cyclone.autoSize();

// Or calculate liners required for a specific flow
int liners = cyclone.calcNumberOfLiners(200.0);  // 200 m³/h → 40 liners

// Query capacity
double maxCap  = cyclone.getMaxDesignCapacityM3h();   // 150 m³/h (30 × 5.0)
double minFlow = cyclone.getMinOperatingFlowM3h();     // 60 m³/h  (30 × 2.0)
double maxFlow = cyclone.getMaxOperatingFlowM3h();     // 240 m³/h (30 × 8.0)
double turndown = cyclone.getTurndownRatio();           // 4.0:1

// After running
int vessels = cyclone.getNumberOfVessels();             // ceil((30+6)/9) = 4
double util = cyclone.getHydrocycloneCapacityUtilization(); // 0.7-1.2 optimum
boolean overloaded = cyclone.isOverloaded();
```

### Physics-Based d50 Calculation

When `setD50Microns()` is not called, the hydrocyclone automatically calculates the d50 cut size from a centrifugal Stokes settling model during `run()`:

$$d_{50} = \sqrt{ C_{emp} \cdot \frac{\mu_w \cdot Q}{\Delta\rho \cdot D_L^2} } \times 10^6 \text{ (μm)}$$

where:
- $C_{emp} = 2.2 \times 10^{-5}$ (calibrated to vendor data)
- $\mu_w$ = water viscosity (Pa·s)
- $Q$ = flow per liner (m³/s)
- $\Delta\rho$ = water density − oil density (kg/m³)
- $D_L$ = liner diameter (m)

```java
// Let d50 be calculated automatically (from fluid properties):
// Do NOT call setD50Microns() → d50 is computed from the Stokes model
cyclone.run();
double d50 = cyclone.getCalculatedD50();  // e.g. 11.8 μm

// Override fluid properties for manual sensitivity studies
cyclone.setWaterViscosity(0.001);      // Pa·s
cyclone.setOilDensity(850.0);          // kg/m³
cyclone.setWaterDensity(1025.0);       // kg/m³
```

### Droplet Size Distribution (DSD) Integration

When oil removal efficiency is not set manually, the model integrates the grade efficiency curve over a log-normal droplet size distribution:

$$\eta_{overall} = \frac{\int \eta(d) \cdot f(d) \, d(\ln d)}{\int f(d) \, d(\ln d)}$$

where $\eta(d)$ is the Rosin–Rammler grade efficiency and $f(d)$ is the log-normal PDF parameterised by $d_{v50}$ and geometric standard deviation $\sigma_g$.

```java
// Configure DSD parameters
cyclone.setDv50Microns(30.0);       // Volume median droplet size
cyclone.setGeometricStdDev(2.5);    // Log-normal spread

// Calculate efficiency for a single droplet size
double eta15 = cyclone.getEfficiencyForDropletSize(15.0);  // e.g. 0.72

// Integrate over full DSD (called automatically during run())
double etaDSD = cyclone.calcEfficiencyFromDSD();  // e.g. 0.93
```

### Pressure Drop Ratio (PDR) Model

The PDR governs the reject ratio and provides an efficiency correction factor:

```java
// Set PDR (controls reject ratio and efficiency correction)
cyclone.setPDR(1.8);

// Reject ratio from PDR correlation: RR ≈ 0.01 × PDR^1.5
double rr = cyclone.calcRejectRatioFromPDR();  // ~2.4%

// PDR efficiency factor (optimum at PDR 1.6–2.2)
double factor = cyclone.getPDREfficiencyFactor();  // e.g. 0.98
```

| PDR Range | Efficiency Factor | Behaviour |
|-----------|-------------------|-----------|
| < 1.2 | 0.70 | Short-circuiting, poor separation |
| 1.2 – 1.4 | 0.70 – 0.95 | Improving separation |
| 1.4 – 2.2 | 0.95 – 1.00 | Near-optimum performance |
| > 2.2 | 1.00 | Diminishing returns, excessive water loss |

### OSPAR Compliance

```java
// Check compliance against the OSPAR 30 mg/L limit
boolean compliant = cyclone.isOSPARCompliant();

// Constants
double limit = Hydrocyclone.OSPAR_OIW_LIMIT_MGL;  // 30.0 mg/L
```

### Comprehensive Sizing Report

```java
// Human-readable multi-section report
String report = cyclone.getDesignValidationSummary();
System.out.println(report);

// Programmatic access to all sizing results
Map<String, Object> sizing = cyclone.getSizingResults();
// Keys include: linerDiameterMm, activeLiners, spareLiners, numberOfVessels,
//   feedFlowM3h, flowPerLinerM3h, capacityUtilization, d50Microns,
//   oilRemovalEfficiency, pressureDropBar, pdr, rejectRatio,
//   inletOilMgL, outletOilMgL, osparCompliant, withinOperatingRange, ...
```

### Complete Sizing Example

```java
// Create produced water stream
SystemSrkEos water = new SystemSrkEos(273.15 + 60.0, 10.0);
water.addComponent("water", 0.995);
water.addComponent("n-heptane", 0.005);
water.setMixingRule("classic");

Stream pw = new Stream("PW Feed", water);
pw.setFlowRate(200.0, "m3/hr");
pw.run();

// Create and configure hydrocyclone
Hydrocyclone hc = new Hydrocyclone("HC-100", pw);
hc.setLinerDiameterMm(35.0);
hc.setNumberOfLiners(30);
hc.setNumberOfSpareLiners(6);
hc.setLinersPerVessel(9);
hc.setPressureDrop(3.0);
hc.setPDR(1.8);
hc.setInletOilConcentration(500.0);
hc.setDv50Microns(25.0);
hc.setGeometricStdDev(2.5);

// Run — d50 and efficiency calculated automatically
hc.run();

// Results
System.out.println("Vessels:     " + hc.getNumberOfVessels());
System.out.println("d50:         " + hc.getCalculatedD50() + " μm");
System.out.println("Efficiency:  " + (hc.getCalculatedEfficiency() * 100) + "%");
System.out.println("Outlet OIW:  " + hc.getOutletOilMgL() + " mg/L");
System.out.println("OSPAR OK:    " + hc.isOSPARCompliant());
System.out.println("Capacity:    " + (hc.getHydrocycloneCapacityUtilization() * 100) + "%");
```

### Mechanical Design

The `HydrocycloneMechanicalDesign` class provides pressure vessel sizing, wall thickness calculation, nozzle sizing, and weight estimation for multi-liner hydrocyclone packages.

**Package:** `neqsim.process.mechanicaldesign.watertreatment`

**Design Standards:**

| Standard | Scope |
|----------|-------|
| ASME VIII Div 1, UG-27 | Cylindrical shell wall thickness |
| ASME VIII Div 1, UG-32 | 2:1 Ellipsoidal head thickness |
| NORSOK P-001 | Process design requirements |
| NORSOK M-001 | Material selection |
| API RP 14E | Nozzle erosional velocity limits |

#### Basic Usage

```java
// After running the hydrocyclone
hc.run();

// Initialize mechanical design
hc.initMechanicalDesign();
HydrocycloneMechanicalDesign design =
    (HydrocycloneMechanicalDesign) hc.getMechanicalDesign();

// Calculate design (runs vessel sizing, wall thickness, nozzles, weights)
design.calcDesign();
```

#### Design Conditions

Design conditions are derived from the operating point with configurable margins:

| Parameter | Calculation | Default Margin |
|-----------|-------------|----------------|
| Design pressure | Operating P × margin factor | 1.10 (10% above operating) |
| Design temperature (high) | Operating T + margin | +25°C |
| Design temperature (low) | Material MDMT | −46°C |
| Corrosion allowance | Produced water service | 3.0 mm |

#### Wall Thickness (ASME VIII Div 1)

Cylindrical shell thickness per UG-27:

$$t = \frac{P \cdot R}{S \cdot E - 0.6 \cdot P} + CA$$

2:1 Ellipsoidal head thickness per UG-32:

$$t_h = \frac{P \cdot D}{2 \cdot S \cdot E - 0.2 \cdot P} + CA$$

where $P$ = design pressure (MPa), $R$ = inside radius (mm), $S$ = allowable stress (MPa), $E$ = joint efficiency, $CA$ = corrosion allowance (mm).

#### Material Selection

```java
// Default: SA-316L austenitic stainless (produced water service)
design.setMaterialGrade("SA-316L");       // S = 115 MPa

// Options:
design.setMaterialGrade("22Cr Duplex");   // S = 207 MPa (sour service)
design.setMaterialGrade("SA-516-70");     // S = 138 MPa (carbon steel)

// Custom material
design.setMaterialGrade("Custom");
design.setAllowableStressMPa(180.0);
```

| Material Grade | Allowable Stress (MPa) | Typical Application |
|----------------|------------------------|---------------------|
| SA-316L | 115 | Standard produced water |
| 22Cr Duplex / SA-790 | 207 | Sour service, high chloride |
| SA-516-70 | 138 | Carbon steel (sweet service) |

#### Design Customisation

```java
// Adjust design margins
design.setDesignPressureMarginFactor(1.15);   // 15% above operating
design.setCorrosionAllowanceMm(6.0);          // 6 mm for severe service

// Recalculate
design.calcDesign();
```

#### Results Access

```java
// Vessel dimensions
double vesselID   = design.getVesselInnerDiameterM();    // m
double wallT      = design.getVesselWallThicknessMm();   // mm
double headT      = design.getHeadThicknessMm();         // mm
double vesselLen  = design.getVesselLengthM();            // m
int vessels       = design.getNumberOfVessels();

// Design conditions
double designP    = design.getDesignPressureBarg();       // barg
double designTHi  = design.getDesignTemperatureHighC();   // °C
double designTLo  = design.getDesignTemperatureLowC();    // °C

// Nozzle sizes (standard pipe sizes per API RP 14E)
double inletNPS   = design.getInletNozzleIdMm();          // mm
double overflowNPS= design.getOverflowNozzleIdMm();       // mm
double rejectNPS  = design.getRejectNozzleIdMm();         // mm

// Weights
double vesselWt   = design.getEmptyVesselWeightKg();      // kg per vessel
double linerWt    = design.getLinerWeightPerVesselKg();    // kg per vessel
double totalWt    = design.getWeightTotal();               // kg all vessels
```

#### Weight Breakdown

The weight estimation covers the full package:

| Component | Estimation Method |
|-----------|-------------------|
| Shell | $\pi \cdot D \cdot L \cdot t \cdot \rho_{steel}$ |
| Heads | 2 × ellipsoidal head area × thickness × density |
| Liner inserts | 3 kg per 35 mm liner, scaled by $(D_L / 35)^2$ |
| Nozzles | 15% of vessel weight |
| Piping | 40% of vessel weight |
| Structural steel | 10% of (vessel + liners) |
| E&I | 8% of (vessel + liners) |

#### JSON Reporting

```java
// Full JSON report with all mechanical design data
String json = design.toJson();

// Structured summary as a Map
Map<String, Object> summary = design.getHydrocycloneDesignSummary();
// Keys: designCode, materialGrade, designPressureBarg,
//   designTemperatureHighC, designTemperatureLowC,
//   numberOfVessels, linersPerVessel, totalActiveLiners, spareLiners,
//   vesselInnerDiameterMm, vesselWallThicknessMm, headThicknessMm,
//   vesselLengthM, inletNozzleIdMm, overflowNozzleIdMm, rejectNozzleIdMm,
//   emptyVesselWeightKg, totalPackageWeightKg
```

#### Complete Mechanical Design Example

```java
// Create and run hydrocyclone
SystemSrkEos water = new SystemSrkEos(273.15 + 60.0, 10.0);
water.addComponent("water", 0.995);
water.addComponent("n-heptane", 0.005);
water.setMixingRule("classic");

Stream pw = new Stream("PW Feed", water);
pw.setFlowRate(200.0, "m3/hr");
pw.run();

Hydrocyclone hc = new Hydrocyclone("HC-100", pw);
hc.setLinerDiameterMm(35.0);
hc.setNumberOfLiners(30);
hc.setNumberOfSpareLiners(6);
hc.setLinersPerVessel(9);
hc.setPressureDrop(3.0);
hc.setInletOilConcentration(500.0);
hc.run();

// Mechanical design
hc.initMechanicalDesign();
HydrocycloneMechanicalDesign design =
    (HydrocycloneMechanicalDesign) hc.getMechanicalDesign();
design.setMaterialGrade("22Cr Duplex");

design.calcDesign();

// Print results
System.out.println("Design pressure:   " + design.getDesignPressureBarg() + " barg");
System.out.println("Vessel ID:         " + (design.getVesselInnerDiameterM() * 1000) + " mm");
System.out.println("Wall thickness:    " + design.getVesselWallThicknessMm() + " mm");
System.out.println("Head thickness:    " + design.getHeadThicknessMm() + " mm");
System.out.println("Vessel length:     " + design.getVesselLengthM() + " m");
System.out.println("Number of vessels: " + design.getNumberOfVessels());
System.out.println("Inlet nozzle:      " + design.getInletNozzleIdMm() + " mm");
System.out.println("Overflow nozzle:   " + design.getOverflowNozzleIdMm() + " mm");
System.out.println("Reject nozzle:     " + design.getRejectNozzleIdMm() + " mm");
System.out.println("Total weight:      " + design.getWeightTotal() + " kg");

// JSON report
System.out.println(design.toJson());
```

---

## Gas Flotation Unit

### Overview

The `GasFlotationUnit` models Induced Gas Flotation (IGF) or Dissolved Gas Flotation (DGF) for removing dispersed oil from produced water. Fine gas bubbles are injected into the water; oil droplets attach to the bubbles and rise to the surface where they are skimmed off.

### Design Requirements

| Parameter | Requirement |
|-----------|-------------|
| Gas supply pressure | Minimum 4 bar above water pressure |
| Gas volume | Minimum 10 Avol% of water flow |
| Reject flow | Minimum 2% of inlet water per stage |
| Gas mixing dP | At least 0.5 bar across mixing valve |
| Typical stages | 3-4 in series |
| Oil removal | 80-95% overall |

### Basic Usage

```java
import neqsim.process.equipment.watertreatment.GasFlotationUnit;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Produced water stream
SystemSrkEos pw = new SystemSrkEos(273.15 + 60.0, 5.0);
pw.addComponent("water", 0.99);
pw.addComponent("n-heptane", 0.01);
pw.setMixingRule("classic");

Stream pwStream = new Stream("Produced Water", pw);
pwStream.setFlowRate(200.0, "m3/hr");

// Gas flotation unit
GasFlotationUnit igf = new GasFlotationUnit("IGF-100", pwStream);
igf.setNumberOfStages(4);
igf.setOilRemovalEfficiency(0.90);
igf.setInletOilConcentration(200.0);  // mg/L
igf.setWaterFlowRate(200.0);          // m3/h

// Wire into process
ProcessSystem process = new ProcessSystem();
process.add(pwStream);
process.add(igf);
process.run();

// Results
System.out.println("Outlet OIW: " + igf.getOutletOilMgL() + " mg/L");
```

### Per-Stage Efficiency

The overall efficiency is distributed across stages. Per-stage efficiency is calculated from the overall target:

$$1 - \eta_{overall} = (1 - \eta_{stage})^N$$

```java
igf.setNumberOfStages(4);
igf.setOilRemovalEfficiency(0.90);
double perStage = igf.calcPerStageEfficiency();  // ~0.44
```

### Gas and Reject Flow

```java
// Minimum gas flow (10 Avol% of water flow)
igf.setWaterFlowRate(200.0);
double minGas = igf.calcMinimumGasFlowRate();  // 20 Am3/h

// Reject flow per stage (2% of inlet water per stage)
double rejectPerStage = igf.calcRejectFlowPerStage();  // 4.0 m3/h
double totalReject = igf.getTotalRejectFlow();          // 16.0 m3/h
```

### Nitrogen Corrosion Warning

When nitrogen is used as the flotation gas instead of fuel gas, the unit flags a corrosion risk:

```java
igf.setFlotationGasType("nitrogen");
igf.run();

// Design summary will include corrosion warning
String summary = igf.getDesignValidationSummary();
// Contains: "nitrogen as flotation gas may cause corrosion..."
```

### GasFlotationUnit Design Parameters

| Parameter | Default | Method | Description |
|-----------|---------|--------|-------------|
| Number of stages | 4 | `setNumberOfStages()` | Flotation stages in series |
| Oil removal efficiency | 90% | `setOilRemovalEfficiency()` | Overall target |
| Min gas overpressure | 4 bar | `setMinGasOverpressureBar()` | Above water pressure |
| Min gas volume | 10 Avol% | `setMinGasVolumeFractionPct()` | Of water flow |
| Min reject fraction | 2%/stage | `setMinRejectFractionPerStage()` | Of inlet water |
| Min gas mixing dP | 0.5 bar | `setMinGasMixingDPBar()` | Across mixing valve |
| Flotation gas type | fuel_gas | `setFlotationGasType()` | fuel_gas or nitrogen |

---

## Produced Water Treatment Train

### Overview

The `ProducedWaterTreatmentTrain` models a complete multi-stage treatment system typically used on offshore platforms. It combines multiple treatment technologies to achieve discharge compliance.

### Typical Treatment Stages

| Stage | Equipment | Target Droplets | Efficiency |
|-------|-----------|-----------------|------------|
| Primary | Hydrocyclone | >20 μm | 90-98% |
| Secondary | IGF/DGF | >5 μm | 80-95% |
| Polishing | Skim Tank | >50 μm | 60-80% |

### Basic Usage

```java
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain;
import neqsim.process.equipment.stream.Stream;

// Create treatment train
ProducedWaterTreatmentTrain train = new ProducedWaterTreatmentTrain(
    "PW Treatment",
    producedWater
);

// Configure inlet conditions
train.setInletOilConcentration(1000.0);  // mg/L from separator
train.setWaterFlowRate(200.0);  // m³/h

// Run simulation
train.run();

// Check compliance
System.out.println("Outlet OIW: " + train.getOutletOilConcentration() + " mg/L");
System.out.println("Compliant: " + train.isCompliant());
System.out.println("Overall efficiency: " + (train.getOverallEfficiency() * 100) + "%");
```

### Stage Types

```java
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain.StageType;

// Available stage types
StageType.HYDROCYCLONE    // Centrifugal separation
StageType.FLOTATION       // IGF/DGF units
StageType.SKIM_TANK       // Gravity separation
StageType.FILTER          // Filtration
StageType.MEMBRANE        // Membrane separation
```

### Custom Stage Configuration

```java
// Clear default stages
train.clearStages();

// Add custom stages
train.addStage("Primary Cyclone", StageType.HYDROCYCLONE, 0.95);
train.addStage("Compact Floatation", StageType.FLOTATION, 0.92);
train.addStage("Final Polish", StageType.SKIM_TANK, 0.75);

// Run with custom configuration
train.run();
```

### Detailed Results

```java
// Get stage-by-stage results
for (WaterTreatmentStage stage : train.getStages()) {
    System.out.println(stage.getName() + ":");
    System.out.println("  Inlet OIW: " + stage.getInletOilMgL() + " mg/L");
    System.out.println("  Outlet OIW: " + stage.getOutletOilMgL() + " mg/L");
    System.out.println("  Efficiency: " + (stage.getEfficiency() * 100) + "%");
}

// Get treated water and oil streams
Stream treatedWater = train.getTreatedWaterStream();
Stream recoveredOil = train.getRecoveredOilStream();
```

---

## Design Considerations

### Droplet Size Distribution

The performance of water treatment equipment depends heavily on the oil droplet size distribution in the feed:

| Source | Typical d50 | Comments |
|--------|-------------|----------|
| HP Separator | 100-300 μm | Large droplets, easy separation |
| LP Separator | 30-100 μm | Moderate separation |
| Degasser | 10-30 μm | Fine droplets, challenging |
| Direct discharge | <10 μm | Very fine, requires flotation |

### Sizing Guidelines

```java
// Hydrocyclone sizing (typical)
double feedFlowM3h = 200.0;
int numberOfLiners = (int) Math.ceil(feedFlowM3h / 35.0);  // ~35 m³/h per liner
double cycloneDP = 1.5 + 0.02 * feedFlowM3h / numberOfLiners;

// Flotation unit sizing
double retentionTime = 3.0;  // minutes
double flotationVolume = feedFlowM3h * retentionTime / 60.0;
```

### Temperature Effects

Oil-water separation efficiency varies with temperature:
- Higher temperature → lower viscosity → faster separation
- Typical operating range: 40-70°C
- Minimum temperature for effective separation: ~30°C

---

## Regulatory Compliance

### Norwegian Continental Shelf (NCS)

| Requirement | Limit | Monitoring |
|-------------|-------|------------|
| Monthly average OIW | 30 mg/L | Weighted average |
| Dispersed oil | Monitored | Daily sampling |
| Zero discharge target | Best available technology | Continuous improvement |

### OSPAR Convention

| Region | OIW Limit | Notes |
|--------|-----------|-------|
| North Sea | 30 mg/L | Monthly average |
| Atlantic | 30 mg/L | Monthly average |

### Compliance Checking

```java
// Check against NCS requirements
boolean ncsCompliant = train.getOutletOilConcentration()
    <= ProducedWaterTreatmentTrain.NCS_OIW_LIMIT_MGL;

// Check against OSPAR
boolean osparCompliant = train.getOutletOilConcentration()
    <= ProducedWaterTreatmentTrain.OSPAR_OIW_LIMIT_MGL;

// Get compliance report
String report = train.getComplianceReport();
System.out.println(report);
```

---

## Integration with Process Systems

### Complete Process Example

```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain;

// Create process system
ProcessSystem process = new ProcessSystem();

// Add production separator
ThreePhaseSeparator prodSep = new ThreePhaseSeparator("Production Separator", wellStream);
process.add(prodSep);

// Add water treatment train
ProducedWaterTreatmentTrain pwTrain = new ProducedWaterTreatmentTrain(
    "PW Treatment",
    prodSep.getWaterOutStream()
);
pwTrain.setInletOilConcentration(800.0);
process.add(pwTrain);

// Run process
process.run();

// Check results
System.out.println("Water cut: " + (prodSep.getWaterCut() * 100) + "%");
System.out.println("OIW to discharge: " + pwTrain.getOutletOilConcentration() + " mg/L");
System.out.println("Compliant: " + pwTrain.isCompliant());
```

---

## See Also

- [Separators](separators) - Three-phase separators
- [Filters](filters) - Filtration equipment
- [Membrane Separators](membrane) - Membrane-based separation
