# Pipelines and Pipes

Documentation for pipeline equipment in NeqSim.

> **📘 Comprehensive Documentation Available**
> 
> For detailed documentation on all pipeline types, the `PipeLineInterface`, flow regime detection, heat transfer, profile methods, and complete examples, see:
> - [Pipeline Simulation Guide](pipeline_simulation.md) - Complete simulation documentation
> - [Pipeline Mechanical Design](../pipeline_mechanical_design.md) - Wall thickness, stress analysis, cost estimation
> - [Topside Piping Design](../topside_piping_design.md) - **Platform piping with velocity, support spacing, vibration (AIV/FIV)**
> - [Riser Mechanical Design](../riser_mechanical_design.md) - Riser design with catenary, VIV, fatigue
> - [Pipeline Mechanical Design Math](../pipeline_mechanical_design_math.md) - Mathematical formulas reference

## Table of Contents
- [Overview](#overview)
- [Pipe Segment](#pipe-segment)
- [Pipeline](#pipeline)
- [Topside Piping](#topside-piping)
- [Risers](#risers)
- [Pressure Drop](#pressure-drop)
- [Heat Transfer](#heat-transfer)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.pipeline`

**Classes:**
| Class | Description | FIV | AutoSize |
|-------|-------------|-----|----------|
| `PipeBeggsAndBrills` | Beggs-Brill correlation | ✅ | ✅ |
| `AdiabaticPipe` | Adiabatic pipe segment | ✅ | ✅ |
| `OnePhasePipe` | Single-phase pipe | - | - |
| `TwoPhasePipeLine` | Two-phase pipeline | - | - |
| `TopsidePiping` | Topside/platform piping with service types and mechanical design | ✅ | ✅ |
| `Riser` | Subsea risers (SCR, TTR, Flexible, Lazy-Wave) | - | - |

For detailed pipe flow modeling, see also [Fluid Mechanics](../../fluidmechanics/README.md).

---

## Pipe Segment

### Basic Usage

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipe", inletStream);
pipe.setLength(1000.0, "m");
pipe.setDiameter(0.3, "m");
pipe.setAngle(0.0);  // Horizontal
pipe.run();

double pressureDrop = pipe.getPressureDrop("bara");
Stream outlet = pipe.getOutletStream();
```

### Geometry

```java
// Length
pipe.setLength(5000.0, "m");

// Inner diameter
pipe.setDiameter(0.254, "m");  // 10 inch
pipe.setInnerDiameter(0.254, "m");

// Wall roughness
pipe.setRoughness(0.0001, "m");

// Elevation change
pipe.setElevationChange(100.0, "m");  // 100m rise
pipe.setAngle(5.7);  // degrees from horizontal
```

---

## Pipeline

For longer pipelines with multiple segments.

### Basic Usage

```java
import neqsim.process.equipment.pipeline.TwoPhasePipeLine;

TwoPhasePipeLine pipeline = new TwoPhasePipeLine("Export Pipeline", inletStream);
pipeline.setLength(50000.0, "m");
pipeline.setDiameter(0.4, "m");
pipeline.setNumberOfNodes(100);
pipeline.run();

// Get profile
double[] pressure = pipeline.getPressureProfile();
double[] temperature = pipeline.getTemperatureProfile();
double[] holdup = pipeline.getLiquidHoldupProfile();
```

---

## Topside Piping

The `TopsidePiping` class provides specialized modeling for offshore platform and onshore facility piping with service type configuration and comprehensive mechanical design.

> **📘 Complete Documentation:** [Topside Piping Design](../topside_piping_design.md)

### Service Types

| Type | Description | Velocity Factor |
|------|-------------|-----------------|
| `PROCESS_GAS` | Hydrocarbon gas | 1.0 |
| `PROCESS_LIQUID` | Hydrocarbon liquid | 1.0 |
| `MULTIPHASE` | Two-phase flow | 0.8 |
| `STEAM` | Steam service | 1.2 |
| `FLARE` | Flare headers | 1.5 |
| `FUEL_GAS` | Fuel gas system | 0.9 |
| `COOLING_WATER` | Cooling water | 1.0 |
| `CHEMICAL_INJECTION` | Chemical injection | 0.8 |

### Factory Methods

```java
import neqsim.process.equipment.pipeline.TopsidePiping;

// Gas process header
TopsidePiping gasHeader = TopsidePiping.createProcessGas("Gas Header", feed);
gasHeader.setLength(50.0);
gasHeader.setDiameter(0.2032);  // 8 inch

// Flare header
TopsidePiping flareHeader = TopsidePiping.createFlareHeader("HP Flare", feed);

// Steam line
TopsidePiping steamLine = TopsidePiping.createSteamLine("HP Steam", feed);

// Cooling water
TopsidePiping cwLine = TopsidePiping.createCoolingWater("CW Supply", feed);
```

### Configuration

```java
TopsidePiping pipe = new TopsidePiping("Process Gas Header", feed);
pipe.setServiceType(TopsidePiping.ServiceType.PROCESS_GAS);
pipe.setPipeSchedule(TopsidePiping.PipeSchedule.SCH_40);
pipe.setLength(50.0);
pipe.setDiameter(0.2032);              // 8 inch ID
pipe.setElevation(0.0);

// Set operating envelope
pipe.setOperatingEnvelope(5.0, 80.0,   // Min/max pressure (bara)
                          -10.0, 60.0); // Min/max temperature (°C)

// Set fittings
pipe.setFittings(4, 2, 1, 2);  // 4 elbows, 2 tees, 1 reducer, 2 valves

// Set insulation
pipe.setInsulation(TopsidePiping.InsulationType.MINERAL_WOOL, 0.05);  // 50mm

// Set flange rating
pipe.setFlangeRating(300);  // ASME B16.5 Class 300

pipe.run();
```

### Mechanical Design

```java
// Get mechanical design
TopsidePipingMechanicalDesign design = pipe.getTopsideMechanicalDesign();
design.setMaxOperationPressure(80.0);
design.setMaterialGrade("A106-B");
design.setDesignStandardCode("ASME-B31.3");
design.setCompanySpecificDesignStandards("Equinor");

// Run design calculations
design.readDesignSpecifications();
design.calcDesign();

// Get results
TopsidePipingMechanicalDesignCalculator calc = design.getTopsideCalculator();
System.out.println("Support spacing: " + calc.getSupportSpacing() + " m");
System.out.println("Velocity OK: " + calc.isVelocityCheckPassed());
System.out.println("Vibration OK: " + calc.isVibrationCheckPassed());
System.out.println("Stress OK: " + calc.isStressCheckPassed());

// Export JSON report
String json = design.toJson();
```

---

## Risers

The `Riser` class provides specialized modeling for subsea risers with support for various riser configurations and dedicated mechanical design calculations.

### Riser Types

| Type | Description | Key Features |
|------|-------------|--------------|
| `STEEL_CATENARY_RISER` (SCR) | Free-hanging catenary from FPSO | Touchdown point stress, catenary mechanics |
| `TOP_TENSIONED_RISER` (TTR) | Tensioned from platform | Stroke requirements, tension variation |
| `FLEXIBLE_RISER` | Unbonded flexible pipe | Bend radius limits, fatigue |
| `LAZY_WAVE` | SCR with buoyancy modules | Reduces touchdown stress |
| `STEEP_WAVE` | Steep wave configuration | Compact footprint |
| `HYBRID_RISER` | Jumper + tower riser | Deep water applications |
| `FREE_STANDING` | Tower riser | Ultra-deep water |
| `VERTICAL` | Vertical tensioned | TLP applications |

### Factory Methods

```java
import neqsim.process.equipment.pipeline.Riser;

// Steel Catenary Riser
Riser scr = Riser.createSCR("Production SCR", inletStream, 800.0);  // 800m water depth

// Top Tensioned Riser
Riser ttr = Riser.createTTR("Export TTR", inletStream, 500.0);

// Lazy-Wave Riser
Riser lazyWave = Riser.createLazyWave("Gas Export", inletStream, 1200.0, 400.0);  // buoyancy at 400m

// Flexible Riser
Riser flexible = Riser.createFlexible("Water Injection", inletStream, 300.0);

// Hybrid Riser
Riser hybrid = Riser.createHybrid("Deepwater Export", inletStream, 2000.0);
```

### Riser Configuration

```java
Riser riser = new Riser("Production Riser", inletStream);
riser.setRiserType(Riser.RiserType.STEEL_CATENARY_RISER);
riser.setWaterDepth(800.0);            // Water depth in meters
riser.setTopAngle(12.0);               // Angle from vertical at top (degrees)
riser.setDepartureAngle(18.0);         // Angle from horizontal at seabed
riser.setDiameter(0.254);              // Inner diameter in meters (10 inch)

// Environmental conditions
riser.setCurrentVelocity(0.8);         // Mid-depth current (m/s)
riser.setSeabedCurrentVelocity(0.3);   // Seabed current (m/s)
riser.setSignificantWaveHeight(4.0);   // Hs in meters
riser.setPeakWavePeriod(12.0);         // Tp in seconds
riser.setPlatformHeaveAmplitude(3.0);  // Heave motion (m)

// TTR specific
riser.setAppliedTopTension(2000.0);    // Top tension in kN

// Lazy-wave specific
riser.setBuoyancyModuleDepth(400.0);   // Depth of buoyancy section
riser.setBuoyancyModuleLength(150.0);  // Length of buoyancy section

riser.run();
```

### Riser Mechanical Design

The `RiserMechanicalDesign` class provides riser-specific mechanical design calculations per DNV-OS-F201, DNV-RP-F204, and API RP 2RD.

```java
Riser riser = Riser.createSCR("Export Riser", inletStream, 1000.0);
riser.setDiameter(0.3048);  // 12 inch
riser.setCurrentVelocity(0.6);
riser.setSignificantWaveHeight(3.5);
riser.run();

// Get mechanical design
RiserMechanicalDesign design = riser.getRiserMechanicalDesign();
design.setMaxOperationPressure(150.0);
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-OS-F201");
design.setCompanySpecificDesignStandards("Equinor");

design.readDesignSpecifications();
design.calcDesign();

// Get riser-specific results
RiserMechanicalDesignCalculator calc = design.getRiserCalculator();

// Top tension (catenary/TTR)
double topTension = calc.getTopTension();         // kN
double minTension = calc.getMinTopTension();      // kN
double maxTension = calc.getMaxTopTension();      // kN

// Touchdown point analysis
double tdpStress = calc.getTouchdownPointStress();     // MPa
double tdpRadius = calc.getTouchdownCurvatureRadius(); // m
double tdpLength = calc.getTouchdownZoneLength();      // m

// VIV response
double vivFreq = calc.getVortexSheddingFrequency();   // Hz
double natFreq = calc.getNaturalFrequency();          // Hz
double vivAmp = calc.getVIVAmplitude();               // A/D ratio
boolean lockIn = calc.isVIVLockIn();

// Dynamic response
double waveStress = calc.getWaveInducedStress();      // MPa
double heaveStress = calc.getHeaveInducedStress();    // MPa
double strokeReq = calc.getStrokeRequirement();       // m (TTR)

// Fatigue analysis
double fatigueLife = calc.getRiserFatigueLife();      // years
double vivDamage = calc.getVIVFatigueDamage();        // per year

// Check design
boolean acceptable = design.isDesignAcceptable();
```

### Riser Design Standards

Parameters are loaded from the NeqSim design database:

| Standard | Parameters |
|----------|------------|
| **DNV-OS-F201** | Usage factor, safety class factors, DAF, max utilization |
| **DNV-RP-F204** | Fatigue design factor, S-N curve parameters, SCF |
| **DNV-RP-C203** | S-N curve parameters (seawater, air) |
| **DNV-RP-C205** | Strouhal number, drag/lift/added mass coefficients |
| **API RP 2RD** | Design factor, dynamic load factor |
| **API RP 17B** | Min bend radius, max axial strain (flexible) |

### JSON Export

```java
// Full design report
String json = design.toJson();

// Calculator results
String calcJson = calc.toJson();
```

---

## Pressure Drop

### Friction Factor Correlations

| Method | Application |
|--------|-------------|
| Beggs-Brill | Two-phase flow |
| Moody | Single-phase turbulent |
| Colebrook | Single-phase implicit |

### Beggs-Brill Correlation

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipe", stream);
pipe.setLength(1000.0, "m");
pipe.setDiameter(0.3, "m");
pipe.run();

// Flow regime
String regime = pipe.getFlowRegime();  // Segregated, Intermittent, Distributed

// Liquid holdup
double holdup = pipe.getLiquidHoldup();
```

### Pressure Drop Components

$$\Delta P_{total} = \Delta P_{friction} + \Delta P_{elevation} + \Delta P_{acceleration}$$

---

## Heat Transfer

### Adiabatic Pipe

```java
import neqsim.process.equipment.pipeline.AdiabaticPipe;

AdiabaticPipe pipe = new AdiabaticPipe("Adiabatic Pipe", stream);
pipe.setLength(500.0, "m");
pipe.setDiameter(0.2, "m");
pipe.run();

// Temperature remains constant (no heat loss)
double Tin = pipe.getInletStream().getTemperature("C");
double Tout = pipe.getOutletStream().getTemperature("C");
```

### Pipe with Heat Transfer

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipe", stream);
pipe.setLength(1000.0, "m");
pipe.setDiameter(0.3, "m");

// Heat transfer coefficient
pipe.setOverallHeatTransferCoefficient(25.0, "W/m2K");

// Ambient temperature
pipe.setAmbientTemperature(5.0, "C");

pipe.run();

double heatLoss = pipe.getHeatLoss("kW");
```

---

## Examples

### Example 1: Simple Pipe Segment

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

// Natural gas
SystemSrkEos gas = new SystemSrkEos(303.15, 80.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream inlet = new Stream("Inlet", gas);
inlet.setFlowRate(1000000.0, "Sm3/day");
inlet.run();

// Pipe
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipe", inlet);
pipe.setLength(5000.0, "m");
pipe.setDiameter(0.254, "m");  // 10 inch
pipe.setRoughness(0.00005, "m");
pipe.run();

System.out.println("Inlet P: " + inlet.getPressure("bara") + " bara");
System.out.println("Outlet P: " + pipe.getOutletStream().getPressure("bara") + " bara");
System.out.println("ΔP: " + pipe.getPressureDrop("bara") + " bara");
System.out.println("Velocity: " + pipe.getVelocity("m/s") + " m/s");
```

### Example 2: Two-Phase Pipeline

```java
// Wellstream (gas + condensate)
SystemSrkEos wellstream = new SystemSrkEos(350.0, 100.0);
wellstream.addComponent("methane", 0.70);
wellstream.addComponent("ethane", 0.10);
wellstream.addComponent("propane", 0.08);
wellstream.addComponent("n-pentane", 0.05);
wellstream.addComponent("n-heptane", 0.05);
wellstream.addComponent("n-decane", 0.02);
wellstream.setMixingRule("classic");

Stream inlet = new Stream("Wellstream", wellstream);
inlet.setFlowRate(50000.0, "kg/hr");
inlet.run();

// Two-phase pipeline
TwoPhasePipeLine pipeline = new TwoPhasePipeLine("Flowline", inlet);
pipeline.setLength(10000.0, "m");
pipeline.setDiameter(0.2, "m");
pipeline.setNumberOfNodes(50);
pipeline.run();

System.out.println("Inlet: " + inlet.getPressure("bara") + " bara, " + 
                   inlet.getTemperature("C") + " °C");
System.out.println("Outlet: " + pipeline.getOutletStream().getPressure("bara") + " bara, " +
                   pipeline.getOutletStream().getTemperature("C") + " °C");
```

### Example 3: Subsea Pipeline with Heat Loss

```java
// Subsea conditions
SystemSrkEos gas = new SystemSrkEos(350.0, 150.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.05);
gas.addComponent("CO2", 0.02);
gas.addComponent("water", 0.01);
gas.setMixingRule("classic");

Stream inlet = new Stream("Wellhead", gas);
inlet.setFlowRate(5000000.0, "Sm3/day");
inlet.run();

// Subsea pipeline
PipeBeggsAndBrills pipeline = new PipeBeggsAndBrills("Subsea", inlet);
pipeline.setLength(50000.0, "m");
pipeline.setDiameter(0.4, "m");
pipeline.setOverallHeatTransferCoefficient(15.0, "W/m2K");
pipeline.setAmbientTemperature(4.0, "C");  // Seabed temperature
pipeline.run();

System.out.println("Outlet temperature: " + pipeline.getOutletStream().getTemperature("C") + " °C");
System.out.println("Heat loss: " + pipeline.getHeatLoss("MW") + " MW");

// Check hydrate temperature
double hdtTemp = pipeline.getOutletStream().getFluid().getHydrateTemperature();
System.out.println("Hydrate temperature: " + (hdtTemp - 273.15) + " °C");
```

### Example 4: Steel Catenary Riser

```java
import neqsim.process.equipment.pipeline.Riser;
import neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesign;

// Production stream
Stream production = new Stream("Production", wellFluid);
production.setFlowRate(10000.0, "kg/hr");
production.run();

// Steel Catenary Riser (800m water depth)
Riser riser = Riser.createSCR("Production Riser", production, 800.0);
riser.setDiameter(0.254);              // 10 inch
riser.setTopAngle(12.0);               // 12 degrees from vertical
riser.setCurrentVelocity(0.6);         // 0.6 m/s current
riser.setSignificantWaveHeight(3.5);   // Hs = 3.5m
riser.run();

System.out.println("Bottom P: " + production.getPressure("bara") + " bara");
System.out.println("Top P: " + riser.getOutletStream().getPressure("bara") + " bara");
System.out.println("Flow regime: " + riser.getFlowRegime());
System.out.println("Riser length: " + riser.getLength() + " m");

// Mechanical design
RiserMechanicalDesign design = riser.getRiserMechanicalDesign();
design.setMaxOperationPressure(100.0);
design.setMaterialGrade("X65");
design.readDesignSpecifications();
design.calcDesign();

System.out.println("Top tension: " + design.getRiserCalculator().getTopTension() + " kN");
System.out.println("Fatigue life: " + design.getRiserCalculator().getRiserFatigueLife() + " years");
System.out.println("VIV lock-in: " + design.getRiserCalculator().isVIVLockIn());
```

### Example 5: Top Tensioned Riser

```java
// TTR for TLP
Riser ttr = Riser.createTTR("Export TTR", production, 500.0);
ttr.setDiameter(0.3048);  // 12 inch
ttr.setAppliedTopTension(2500.0);  // 2500 kN applied tension
ttr.setTensionVariationFactor(0.15);  // 15% variation from heave
ttr.setPlatformHeaveAmplitude(2.5);
ttr.run();

RiserMechanicalDesign ttrDesign = ttr.getRiserMechanicalDesign();
ttrDesign.setMaxOperationPressure(200.0);
ttrDesign.setMaterialGrade("X65");
ttrDesign.calcDesign();

System.out.println("TTR tension: " + ttrDesign.getRiserCalculator().getTopTension() + " kN");
System.out.println("Stroke requirement: " + ttrDesign.getRiserCalculator().getStrokeRequirement() + " m");
```

---

## Flow-Induced Vibration (FIV) Analysis

Pipeline equipment (`PipeBeggsAndBrills`, `AdiabaticPipe`, `Pipeline`) includes built-in FIV analysis with capacity constraints.

### FIV Methods

All pipeline types provide these FIV methods:

```java
// LOF - Likelihood of Failure (dimensionless)
double lof = pipe.calculateLOF();

// FRMS - RMS force per meter (N/m)  
double frms = pipe.calculateFRMS();

// Erosional velocity per API RP 14E
double erosionalVel = pipe.getErosionalVelocity();

// Actual mixture velocity
double velocity = pipe.getMixtureVelocity();

// Full FIV analysis
Map<String, Object> fivAnalysis = pipe.getFIVAnalysis();
String fivJson = pipe.getFIVAnalysisJson();
```

### Support Arrangement

Configure pipe support stiffness:

```java
pipe.setSupportArrangement("Stiff");        // Coefficient 1.0
pipe.setSupportArrangement("Medium stiff"); // Coefficient 1.5
pipe.setSupportArrangement("Medium");       // Coefficient 2.0
pipe.setSupportArrangement("Flexible");     // Coefficient 3.0
```

### Capacity Constraints

Pipeline types implement `CapacityConstrainedEquipment`:

```java
// Get all constraints
Map<String, CapacityConstraint> constraints = pipe.getCapacityConstraints();

// Available constraints:
// - velocity: actual vs erosional velocity
// - LOF: Likelihood of Failure
// - FRMS: RMS force per meter  
// - pressureDrop: (AdiabaticPipe only)

// Check if any limit exceeded
if (pipe.isCapacityExceeded()) {
    CapacityConstraint bottleneck = pipe.getBottleneckConstraint();
    System.out.println("Limit exceeded: " + bottleneck.getName());
}
```

### AutoSizing

`PipeBeggsAndBrills` and `AdiabaticPipe` support auto-sizing:

```java
// Auto-size with 20% safety factor
pipe.autoSize(1.2);

// Auto-size per company standard
pipe.autoSize("Equinor", "TR1414");

// Check sizing report
System.out.println(pipe.getSizingReport());
System.out.println(pipe.getSizingReportJson());
```

For detailed FIV documentation, see [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md#flow-induced-vibration-fiv-analysis).

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Fluid Mechanics](../../fluidmechanics/README.md) - Detailed pipe flow
- [Pipeline Mechanical Design](../pipeline_mechanical_design.md) - Wall thickness, stress, costs
- [Riser Mechanical Design](../riser_mechanical_design.md) - Riser-specific design
- [Valves](valves.md) - Flow control
