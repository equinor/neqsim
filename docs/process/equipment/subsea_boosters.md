---
title: Subsea Boosters
description: Documentation for subsea booster pump and compressor equipment in NeqSim.
---

# Subsea Boosters

Documentation for subsea booster pump and compressor equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [SubseaBooster Class](#subseabooster-class)
- [Booster Types](#booster-types)
- [Pump Types](#pump-types)
- [Performance Modeling](#performance-modeling)
- [Usage Examples](#usage-examples)
- [Design Standards](#design-standards)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.subsea`

A subsea booster is a pump or compressor installed on the seabed to enhance production from subsea wells. The `SubseaBooster` class models:

- Multiphase pumping (gas-liquid mixtures)
- Single-phase liquid pumping
- Wet gas compression
- Dry gas compression (after subsea separation)
- Long-distance tieback enabling

| Class | Description |
|-------|-------------|
| `SubseaBooster` | Subsea pump/compressor unit |
| `SubseaBoosterMechanicalDesign` | Mechanical design calculations |

---

## SubseaBooster Class

### Class Hierarchy

```
TwoPortEquipment
└── SubseaBooster
    ├── enum: BoosterType
    ├── enum: PumpType
    └── contains: Compressor or Pump
```

### Key Features

- **Multiple Booster Types**: Multiphase pump, liquid pump, wet/dry compressor
- **Pump Type Selection**: Helico-axial, twin-screw, ESP, centrifugal
- **Power Modeling**: Electrical power consumption
- **Efficiency Curves**: Performance mapping
- **Turndown Capability**: Variable speed operation

### Constructor

```java
import neqsim.process.equipment.subsea.SubseaBooster;

// Create subsea booster
SubseaBooster booster = new SubseaBooster("Subsea Booster", wellStream);
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
booster.setWaterDepth(450.0);
booster.setOutletPressure(150.0);
```

---

## Booster Types

### BoosterType Enumeration

| Type | Description | Application |
|------|-------------|-------------|
| `MULTIPHASE_PUMP` | Handles gas-liquid mixture | Standard production |
| `LIQUID_PUMP` | Single-phase liquid only | After separation |
| `WET_GAS_COMPRESSOR` | Compressor with liquid tolerance | High GVF streams |
| `DRY_GAS_COMPRESSOR` | Compressor after separation | Subsea separation |
| `SEPARATOR_BOOSTER` | Combined separator + booster | Complex systems |

### Selecting Booster Type

```java
// Multiphase pump for well stream
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);

// Liquid pump after subsea separator
booster.setBoosterType(SubseaBooster.BoosterType.LIQUID_PUMP);

// Wet gas compressor for high GVF
booster.setBoosterType(SubseaBooster.BoosterType.WET_GAS_COMPRESSOR);
```

### Booster Type Selection Guide

| Stream GVF | Water Cut | Recommended Type |
|------------|-----------|------------------|
| 0-50% | Any | MULTIPHASE_PUMP |
| 50-90% | <30% | MULTIPHASE_PUMP |
| 50-90% | >30% | WET_GAS_COMPRESSOR |
| >90% | <5% | WET_GAS_COMPRESSOR |
| 100% gas | 0% | DRY_GAS_COMPRESSOR |
| 0% gas | Any | LIQUID_PUMP |

---

## Pump Types

### PumpType Enumeration

| Type | Description | Max GVF | ΔP Range |
|------|-------------|---------|----------|
| `HELICO_AXIAL` | Helico-axial multiphase | 95% | 50-150 bar |
| `TWIN_SCREW` | Twin-screw positive displacement | 100% | 20-80 bar |
| `COUNTER_ROTATING_AXIAL` | Counter-rotating design | 95% | 80-200 bar |
| `ESP` | Electrical submersible pump | 20% | 100-300 bar |
| `CENTRIFUGAL_SINGLE` | Single-stage centrifugal | 10% | 10-30 bar |
| `CENTRIFUGAL_MULTI` | Multi-stage centrifugal | 10% | 50-200 bar |

### Selecting Pump Type

```java
// Helico-axial for moderate GVF
booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);

// Twin-screw for high GVF or slugging
booster.setPumpType(SubseaBooster.PumpType.TWIN_SCREW);

// ESP for low GVF, high head
booster.setPumpType(SubseaBooster.PumpType.ESP);
```

---

## Performance Modeling

### Setting Operating Point

```java
// Set target outlet pressure
booster.setOutletPressure(180.0);  // bara

// Or set differential pressure
booster.setDifferentialPressure(80.0);  // bar

// Set speed (% of rated)
booster.setSpeed(95.0);  // 95% speed
```

### Power and Efficiency

```java
// Set power rating
booster.setPowerRatingMW(5.0);  // 5 MW motor

// Set efficiency
booster.setEfficiency(0.75);  // 75% hydraulic efficiency

// After running
booster.run();

// Get actual power consumption
double power = booster.getPower("MW");
System.out.println("Power consumption: " + power + " MW");
```

### Performance Curves

```java
// Set performance curve (flow vs head)
double[] flows = {0, 500, 1000, 1500, 2000};  // m³/hr
double[] heads = {200, 195, 180, 155, 120};   // m

booster.setPerformanceCurve(flows, heads);

// Set efficiency curve
double[] efficiencies = {0, 0.65, 0.75, 0.72, 0.60};
booster.setEfficiencyCurve(flows, efficiencies);
```

---

## Usage Examples

### Example 1: Multiphase Pump for Production Boosting

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.subsea.SubseaBooster;

// Create well fluid
SystemSrkEos fluid = new SystemSrkEos(353.15, 100.0);
fluid.addComponent("methane", 60.0);
fluid.addComponent("ethane", 8.0);
fluid.addComponent("propane", 5.0);
fluid.addComponent("n-heptane", 25.0);
fluid.addComponent("water", 2.0);
fluid.setMixingRule("classic");

// Create manifold outlet stream
Stream manifoldStream = new Stream("Manifold Outlet", fluid);
manifoldStream.setFlowRate(80000.0, "kg/hr");
manifoldStream.setTemperature(60.0, "C");
manifoldStream.setPressure(80.0, "bara");
manifoldStream.run();

// Create multiphase booster
SubseaBooster booster = new SubseaBooster("Subsea Booster", manifoldStream);
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
booster.setWaterDepth(500.0);
booster.setOutletPressure(150.0);  // Boost to 150 bara
booster.setPowerRatingMW(4.0);
booster.setEfficiency(0.72);

// Run booster
booster.run();

// Results
System.out.println("Inlet pressure: " + manifoldStream.getPressure() + " bara");
System.out.println("Outlet pressure: " + booster.getOutletStream().getPressure() + " bara");
System.out.println("Power: " + booster.getPower("MW") + " MW");
System.out.println("ΔP: " + booster.getDifferentialPressure() + " bar");
```

### Example 2: Long-Distance Tieback with Boosting

```java
import neqsim.process.equipment.pipeline.AdiabaticPipe;

// Well stream at remote location
Stream wellStream = new Stream("Remote Well", fluid);
wellStream.setFlowRate(30000.0, "kg/hr");
wellStream.setPressure(120.0, "bara");
wellStream.run();

// First pipeline segment (25 km to booster)
AdiabaticPipe pipe1 = new AdiabaticPipe("Flowline 1", wellStream);
pipe1.setLength(25000.0);
pipe1.setDiameter(0.254);  // 10"
pipe1.run();

System.out.println("Pressure at booster inlet: " + pipe1.getOutletPressure() + " bara");

// Subsea booster station
SubseaBooster booster = new SubseaBooster("Mid-Line Booster", pipe1.getOutletStream());
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
booster.setOutletPressure(150.0);
booster.run();

System.out.println("Pressure after booster: " + booster.getOutletPressure() + " bara");

// Second pipeline segment (35 km to platform)
AdiabaticPipe pipe2 = new AdiabaticPipe("Flowline 2", booster.getOutletStream());
pipe2.setLength(35000.0);
pipe2.setDiameter(0.254);
pipe2.run();

System.out.println("Arrival pressure: " + pipe2.getOutletPressure() + " bara");
```

### Example 3: Twin-Screw Pump for Slugging Flow

```java
// Twin-screw pump handles slugs better than helico-axial
SubseaBooster booster = new SubseaBooster("Slug Handler", manifoldStream);
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
booster.setPumpType(SubseaBooster.PumpType.TWIN_SCREW);
booster.setWaterDepth(300.0);
booster.setOutletPressure(120.0);

// Twin-screw specific parameters
booster.setMaxGVF(0.98);           // Can handle 98% GVF
booster.setSlugTolerance(true);    // Designed for slugging

booster.run();

System.out.println("Differential pressure: " + booster.getDifferentialPressure() + " bar");
```

### Example 4: Wet Gas Compressor

```java
// High GVF stream from gas well
SystemSrkEos gasFluid = new SystemSrkEos(323.15, 60.0);
gasFluid.addComponent("methane", 88.0);
gasFluid.addComponent("ethane", 5.0);
gasFluid.addComponent("propane", 2.0);
gasFluid.addComponent("n-pentane", 1.0);
gasFluid.addComponent("water", 4.0);
gasFluid.setMixingRule("classic");

Stream gasStream = new Stream("Gas Well", gasFluid);
gasStream.setFlowRate(100000.0, "kg/hr");
gasStream.run();

// Wet gas compressor
SubseaBooster compressor = new SubseaBooster("Subsea Compressor", gasStream);
compressor.setBoosterType(SubseaBooster.BoosterType.WET_GAS_COMPRESSOR);
compressor.setWaterDepth(800.0);
compressor.setOutletPressure(120.0);
compressor.setCompressionRatio(2.0);
compressor.setPolytropicEfficiency(0.80);

compressor.run();

System.out.println("Outlet temperature: " + (compressor.getOutletTemperature() - 273.15) + " °C");
System.out.println("Power: " + compressor.getPower("MW") + " MW");
```

### Example 5: Booster with Mechanical Design

```java
import neqsim.process.mechanicaldesign.subsea.SubseaBoosterMechanicalDesign;

// Create booster
SubseaBooster booster = new SubseaBooster("Export Booster", stream);
booster.setBoosterType(SubseaBooster.BoosterType.MULTIPHASE_PUMP);
booster.setPumpType(SubseaBooster.PumpType.HELICO_AXIAL);
booster.setWaterDepth(600.0);
booster.setDesignPressure(250.0);
booster.setDesignTemperature(150.0);
booster.setPowerRatingMW(6.0);

// Get mechanical design
SubseaBoosterMechanicalDesign design = booster.getMechanicalDesign();

// Configure design parameters
design.setDesignLife(25);          // years
design.setMaterialGrade("6Mo");
design.setServiceType("Sour");
design.setCorrosionAllowance(3.0); // mm

// Calculate design
design.calcDesign();

// Get weight estimate
System.out.println("Booster dry weight: " + design.getDryWeight() + " tonnes");
System.out.println("Foundation weight: " + design.getFoundationWeight() + " tonnes");

// Get JSON report
String report = design.toJson();
```

---

## Design Standards

### Applicable Standards

| Standard | Title |
|----------|-------|
| API RP 17Q | Subsea Equipment Qualification |
| API RP 17V | Subsea Boosting Systems |
| DNV-ST-E101 | Drilling Plants |
| ISO 13628-6 | Subsea Production Control Systems |

### Design Considerations

| Parameter | Typical Range | Unit |
|-----------|--------------|------|
| Water Depth | 200-3000 | m |
| Design Pressure | 150-400 | bar |
| Design Temperature | -10 to 150 | °C |
| Power Rating | 1-15 | MW |
| Design Life | 20-30 | years |
| Availability | >95% | - |

### Power Supply Options

| Option | Typical Range | Application |
|--------|--------------|-------------|
| Long step-out AC | 50-150 km | Moderate distance |
| DC transmission | 100-300+ km | Long tieback |
| All-electric | Any | Modern systems |
| Hydraulic | <30 km | Legacy systems |

---

## Turndown and Operating Envelope

### Speed Variation

```java
// Variable speed operation
double[] speeds = {60, 70, 80, 90, 100};  // % of rated

for (double speed : speeds) {
    booster.setSpeed(speed);
    booster.run();
    
    double flow = booster.getOutletStream().getFlowRate("m3/hr");
    double dp = booster.getDifferentialPressure();
    double power = booster.getPower("MW");
    
    System.out.printf("Speed: %.0f%%, Flow: %.0f m³/hr, ΔP: %.1f bar, Power: %.2f MW%n",
        speed, flow, dp, power);
}
```

### Affinity Laws

For centrifugal-type boosters:

$$\frac{Q_2}{Q_1} = \frac{N_2}{N_1}$$

$$\frac{H_2}{H_1} = \left(\frac{N_2}{N_1}\right)^2$$

$$\frac{P_2}{P_1} = \left(\frac{N_2}{N_1}\right)^3$$

Where:
- $Q$ = Flow rate
- $H$ = Head
- $P$ = Power
- $N$ = Speed

---

## Related Documentation

- [Subsea Trees](subsea_trees) - Christmas tree modeling
- [Subsea Manifolds](subsea_manifolds) - Manifold modeling
- [Umbilicals](umbilicals) - Power and control supply
- [Compressors](compressors) - Topside compressor modeling
- [Pumps](pumps) - Topside pump modeling

---

## API Reference

### SubseaBooster

```java
// Constructors
SubseaBooster(String name)
SubseaBooster(String name, StreamInterface inletStream)

// Booster configuration
void setBoosterType(BoosterType type)
BoosterType getBoosterType()
void setPumpType(PumpType type)
PumpType getPumpType()

// Design parameters
void setWaterDepth(double depth)
void setDesignPressure(double pressure)
void setDesignTemperature(double temperature)
void setPowerRatingMW(double power)

// Operating parameters
void setOutletPressure(double pressure)
void setDifferentialPressure(double dp)
void setSpeed(double percentOfRated)
void setEfficiency(double efficiency)
void setCompressionRatio(double ratio)
void setPolytropicEfficiency(double efficiency)

// Performance curves
void setPerformanceCurve(double[] flows, double[] heads)
void setEfficiencyCurve(double[] flows, double[] efficiencies)

// Mechanical design
SubseaBoosterMechanicalDesign getMechanicalDesign()

// Run simulation
void run()
void run(UUID id)

// Results
StreamInterface getOutletStream()
double getOutletPressure()
double getOutletTemperature()
double getDifferentialPressure()
double getPower(String unit)
```

### BoosterType Enum

```java
enum BoosterType {
    MULTIPHASE_PUMP,
    LIQUID_PUMP,
    WET_GAS_COMPRESSOR,
    DRY_GAS_COMPRESSOR,
    SEPARATOR_BOOSTER
}
```

### PumpType Enum

```java
enum PumpType {
    HELICO_AXIAL,
    TWIN_SCREW,
    COUNTER_ROTATING_AXIAL,
    ESP,
    CENTRIFUGAL_SINGLE,
    CENTRIFUGAL_MULTI
}
```
