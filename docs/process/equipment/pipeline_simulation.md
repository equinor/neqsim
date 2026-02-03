---
title: Pipeline Simulation Guide
description: Comprehensive documentation for pipeline simulation in NeqSim, covering all pipeline types, common interface, flow modeling, and integration with mechanical design.
---

# Pipeline Simulation Guide

Comprehensive documentation for pipeline simulation in NeqSim, covering all pipeline types, common interface, flow modeling, and integration with mechanical design.

## Table of Contents

- [Overview](#overview)
- [Pipeline Interface](#pipeline-interface)
- [Pipeline Types](#pipeline-types)
- [Common Functionality](#common-functionality)
- [Flow Regime Detection](#flow-regime-detection)
- [Heat Transfer](#heat-transfer)
- [Pressure Drop Calculations](#pressure-drop-calculations)
- [Profile Methods](#profile-methods)
- [Geometry and Properties](#geometry-and-properties)
- [Mechanical Design Integration](#mechanical-design-integration)
- [Examples](#examples)

---

## Overview

NeqSim provides a unified pipeline simulation framework supporting:

- **Single-phase flow** - Gas or liquid pipelines
- **Two-phase flow** - Gas-liquid systems with holdup and slip
- **Multiphase flow** - Gas-oil-water systems
- **Transient flow** - Time-dependent simulations
- **Steady-state flow** - Equilibrium calculations

All pipeline types implement the `PipeLineInterface` which provides 70+ common methods for consistent access to pipeline properties and behavior.

**Location:** `neqsim.process.equipment.pipeline`

---

## Pipeline Interface

All pipeline classes implement `PipeLineInterface`, providing a consistent API:

```java
public interface PipeLineInterface extends ProcessEquipmentInterface {
    // Geometry
    void setDiameter(double diameter);
    double getDiameter();
    void setLength(double length);
    double getLength();
    void setRoughness(double roughness);
    double getRoughness();
    void setAngle(double angle);
    double getAngle();
    void setElevationChange(double elevation);
    double getElevationChange();
    void setWallThickness(double thickness);
    double getWallThickness();
    
    // Flow Properties
    double getVelocity();
    double getVelocity(String unit);
    double getSuperficialVelocity();
    double getReynoldsNumber();
    double getFrictionFactor();
    double getFlowRegime();
    String getFlowRegimeDescription();
    
    // Pressure Drop
    double getPressureDrop();
    double getPressureDrop(String unit);
    double getTotalPressureDrop();
    double getFrictionalPressureDrop();
    double getGravitationalPressureDrop();
    double getAccelerationalPressureDrop();
    
    // Two-Phase Properties
    double getLiquidHoldup();
    double getGasVoidFraction();
    double getSlipRatio();
    double getMixtureVelocity();
    double getLiquidSuperficialVelocity();
    double getGasSuperficialVelocity();
    
    // Heat Transfer
    void setOverallHeatTransferCoefficient(double U);
    double getOverallHeatTransferCoefficient();
    void setAmbientTemperature(double temp);
    double getAmbientTemperature();
    double getHeatLoss();
    double getHeatLoss(String unit);
    
    // Profile Data
    double[] getPressureProfile();
    double[] getTemperatureProfile();
    double[] getLiquidHoldupProfile();
    double[] getVelocityProfile();
    int getNumberOfNodes();
    void setNumberOfNodes(int nodes);
    
    // Mechanical Design
    MechanicalDesign getMechanicalDesign();
    void initMechanicalDesign();
}
```

---

## Pipeline Types

### PipeBeggsAndBrills

Two-phase flow using Beggs-Brill correlation with flow regime detection.

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Flowline", inlet);
pipe.setLength(5000.0, "m");
pipe.setDiameter(0.254, "m");  // 10 inch
pipe.setAngle(5.0);  // Upward inclination
pipe.run();

// Flow regime
String regime = pipe.getFlowRegimeDescription();  // "Intermittent", "Segregated", etc.
double holdup = pipe.getLiquidHoldup();
```

### AdiabaticPipe

Simple pipe with no heat transfer (adiabatic walls).

```java
import neqsim.process.equipment.pipeline.AdiabaticPipe;

AdiabaticPipe pipe = new AdiabaticPipe("Gas Pipe", inlet);
pipe.setLength(1000.0, "m");
pipe.setDiameter(0.3, "m");
pipe.run();

// Temperature remains constant
double dT = pipe.getOutletStream().getTemperature("C") 
          - pipe.getInletStream().getTemperature("C");
// dT ≈ 0 (adiabatic)
```

### OnePhasePipe

Optimized for single-phase (gas or liquid) flow.

```java
import neqsim.process.equipment.pipeline.OnePhasePipe;

OnePhasePipe pipe = new OnePhasePipe("Liquid Line", inlet);
pipe.setLength(2000.0, "m");
pipe.setDiameter(0.15, "m");
pipe.run();

double reynolds = pipe.getReynoldsNumber();
double friction = pipe.getFrictionFactor();
```

### MultiphasePipe

Wrapper for `TwoPhasePipeFlowSystem` with full multiphase capabilities.

```java
import neqsim.process.equipment.pipeline.MultiphasePipe;

MultiphasePipe pipe = new MultiphasePipe("Export Pipeline", inlet);
pipe.setLength(50000.0, "m");
pipe.setDiameter(0.4, "m");
pipe.setNumberOfNodes(100);
pipe.setOverallHeatTransferCoefficient(15.0, "W/m2K");
pipe.setAmbientTemperature(4.0, "C");
pipe.run();

// Get profiles
double[] pressure = pipe.getPressureProfile();
double[] temperature = pipe.getTemperatureProfile();
double[] holdup = pipe.getLiquidHoldupProfile();
```

### TransientPipe

Time-dependent pipeline simulation.

```java
import neqsim.process.equipment.pipeline.TransientPipe;

TransientPipe pipe = new TransientPipe("Transient Line", inlet);
pipe.setLength(10000.0, "m");
pipe.setDiameter(0.3, "m");
pipe.setTimeStep(1.0);  // seconds
pipe.setSimulationTime(3600.0);  // 1 hour
pipe.run();

// Access time-dependent results
double[][] pressureVsTime = pipe.getPressureHistory();
```

---

## Common Functionality

### Setting Geometry

All pipeline types support consistent geometry methods:

```java
// Length
pipe.setLength(5000.0, "m");
pipe.setLength(16404.0, "ft");

// Diameter
pipe.setDiameter(0.254, "m");      // Outer diameter
pipe.setInnerDiameter(0.244, "m"); // Inner diameter

// Wall thickness
pipe.setWallThickness(0.01, "m");  // 10mm

// Roughness
pipe.setRoughness(0.0001, "m");    // Absolute roughness
pipe.setRoughness(0.1, "mm");      // With unit

// Elevation
pipe.setElevationChange(100.0, "m");  // Total rise
pipe.setAngle(5.7);                   // Degrees from horizontal
```

### Getting Flow Properties

```java
// Velocity
double velocity = pipe.getVelocity("m/s");
double superficial = pipe.getSuperficialVelocity();

// Pressure drop
double totalDP = pipe.getTotalPressureDrop();
double frictionDP = pipe.getFrictionalPressureDrop();
double gravityDP = pipe.getGravitationalPressureDrop();
double accelDP = pipe.getAccelerationalPressureDrop();

// Dimensionless numbers
double Re = pipe.getReynoldsNumber();
double f = pipe.getFrictionFactor();
```

### Two-Phase Properties

```java
// Holdup and void fraction
double holdup = pipe.getLiquidHoldup();      // Liquid volume fraction
double voidFrac = pipe.getGasVoidFraction(); // Gas volume fraction

// Superficial velocities
double vsl = pipe.getLiquidSuperficialVelocity();
double vsg = pipe.getGasSuperficialVelocity();
double vm = pipe.getMixtureVelocity();

// Slip ratio
double slip = pipe.getSlipRatio();  // vg/vl
```

---

## Flow Regime Detection

The Beggs-Brill correlation identifies flow regimes:

| Regime | Description | Typical Conditions |
|--------|-------------|-------------------|
| **Segregated** | Stratified flow, liquid at bottom | Low gas, low liquid velocity |
| **Intermittent** | Slug/plug flow | Moderate velocities |
| **Distributed** | Annular/mist flow | High gas velocity |
| **Transition** | Between regimes | Boundary conditions |

```java
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Pipe", inlet);
pipe.setLength(1000.0, "m");
pipe.setDiameter(0.2, "m");
pipe.run();

// Get flow regime
int regimeCode = pipe.getFlowRegime();
String regimeDesc = pipe.getFlowRegimeDescription();

switch (regimeCode) {
    case 1: System.out.println("Segregated flow"); break;
    case 2: System.out.println("Intermittent flow"); break;
    case 3: System.out.println("Distributed flow"); break;
    case 4: System.out.println("Transition"); break;
}
```

### Flow Pattern Map

The Beggs-Brill flow pattern boundaries are defined by:

$$L_1 = 316 \cdot \lambda_L^{0.302}$$
$$L_2 = 0.0009252 \cdot \lambda_L^{-2.4684}$$
$$L_3 = 0.10 \cdot \lambda_L^{-1.4516}$$
$$L_4 = 0.5 \cdot \lambda_L^{-6.738}$$

Where $\lambda_L$ is the no-slip liquid holdup and $N_{Fr}$ is the Froude number.

---

## Heat Transfer

### Overall Heat Transfer Coefficient

```java
// Set U-value
pipe.setOverallHeatTransferCoefficient(25.0, "W/m2K");
pipe.setOverallHeatTransferCoefficient(4.4, "BTU/hr-ft2-F");

// Set ambient conditions
pipe.setAmbientTemperature(15.0, "C");
pipe.setAmbientTemperature(4.0, "C");  // Seabed

// Calculate heat loss
pipe.run();
double heatLoss = pipe.getHeatLoss("kW");
double heatLossMW = pipe.getHeatLoss("MW");
```

### Typical U-Values

| Application | U-Value (W/m²K) |
|-------------|-----------------|
| Bare pipe in air | 10-25 |
| Insulated pipe in air | 1-5 |
| Buried pipe | 2-10 |
| Subsea pipe (uninsulated) | 15-50 |
| Subsea pipe (insulated) | 1-5 |
| Pipe-in-pipe | 0.5-2 |

---

## Pressure Drop Calculations

### Total Pressure Drop

$$\Delta P_{total} = \Delta P_{friction} + \Delta P_{gravity} + \Delta P_{acceleration}$$

### Frictional Pressure Drop (Beggs-Brill)

$$\Delta P_{friction} = \frac{f_{tp} \cdot \rho_{ns} \cdot v_m^2}{2 \cdot D} \cdot L$$

Where:
- $f_{tp}$ = two-phase friction factor
- $\rho_{ns}$ = no-slip mixture density
- $v_m$ = mixture velocity
- $D$ = pipe diameter
- $L$ = pipe length

### Gravitational Pressure Drop

$$\Delta P_{gravity} = \rho_s \cdot g \cdot \sin(\theta) \cdot L$$

Where:
- $\rho_s$ = slip mixture density = $\rho_L \cdot H_L + \rho_g \cdot (1-H_L)$
- $H_L$ = liquid holdup
- $\theta$ = pipe inclination angle

### Liquid Holdup Correlation

$$H_L(\theta) = H_L(0) \cdot \psi$$

Where $\psi$ is the inclination correction factor.

---

## Profile Methods

For pipelines divided into multiple nodes:

```java
MultiphasePipe pipe = new MultiphasePipe("Pipeline", inlet);
pipe.setLength(50000.0, "m");
pipe.setNumberOfNodes(100);
pipe.run();

// Pressure profile
double[] pressure = pipe.getPressureProfile();

// Temperature profile
double[] temperature = pipe.getTemperatureProfile();

// Liquid holdup profile
double[] holdup = pipe.getLiquidHoldupProfile();

// Velocity profile
double[] velocity = pipe.getVelocityProfile();

// Plot profiles
for (int i = 0; i < pipe.getNumberOfNodes(); i++) {
    double distance = i * pipe.getLength() / pipe.getNumberOfNodes();
    System.out.printf("%.0f m: P=%.1f bar, T=%.1f°C, HL=%.2f%n",
        distance, pressure[i], temperature[i], holdup[i]);
}
```

---

## Geometry and Properties

### Standard Pipe Sizes (API 5L)

| NPS (inch) | OD (mm) | OD (m) |
|------------|---------|--------|
| 2" | 60.3 | 0.0603 |
| 4" | 114.3 | 0.1143 |
| 6" | 168.3 | 0.1683 |
| 8" | 219.1 | 0.2191 |
| 10" | 273.1 | 0.2731 |
| 12" | 323.9 | 0.3239 |
| 16" | 406.4 | 0.4064 |
| 20" | 508.0 | 0.5080 |
| 24" | 609.6 | 0.6096 |
| 30" | 762.0 | 0.7620 |
| 36" | 914.4 | 0.9144 |
| 42" | 1066.8 | 1.0668 |
| 48" | 1219.2 | 1.2192 |

---

## Mechanical Design Integration

All pipeline types integrate with the mechanical design framework:

```java
// Initialize mechanical design
AdiabaticPipe pipe = new AdiabaticPipe("Export Line", inlet);
pipe.setLength(50000.0, "m");
pipe.setDiameter(0.508, "m");
pipe.initMechanicalDesign();

// Configure design
PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipe.getMechanicalDesign();
design.setMaxOperationPressure(150.0);  // bara
design.setMaxOperationTemperature(80.0);  // °C
design.setMaterialGrade("X65");
design.setDesignStandardCode("DNV-OS-F101");
design.setCompanySpecificDesignStandards("Equinor");

// Calculate design
design.calcDesign();

// Get results
double wallThickness = design.getWallThickness();  // mm
String json = design.toJson();  // Complete report
```

See [Pipeline Mechanical Design](../pipeline_mechanical_design) for detailed mechanical design documentation.

---

## Examples

### Example 1: Gas Export Pipeline

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.pipeline.AdiabaticPipe;

// Dry gas
SystemSrkEos gas = new SystemSrkEos(303.15, 150.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.02);
gas.addComponent("CO2", 0.01);
gas.setMixingRule("classic");

Stream inlet = new Stream("Gas Inlet", gas);
inlet.setFlowRate(20.0, "MSm3/day");
inlet.run();

// 100 km pipeline
AdiabaticPipe pipeline = new AdiabaticPipe("Export Pipeline", inlet);
pipeline.setLength(100000.0, "m");
pipeline.setDiameter(0.762, "m");  // 30 inch
pipeline.setRoughness(0.0001, "m");
pipeline.run();

System.out.println("Inlet: " + inlet.getPressure("bara") + " bara");
System.out.println("Outlet: " + pipeline.getOutletStream().getPressure("bara") + " bara");
System.out.println("Pressure drop: " + pipeline.getPressureDrop("bara") + " bara");
System.out.println("Velocity: " + pipeline.getVelocity("m/s") + " m/s");
```

### Example 2: Subsea Multiphase Flowline

```java
import neqsim.process.equipment.pipeline.MultiphasePipe;

// Wellstream
SystemSrkEos fluid = new SystemSrkEos(350.0, 200.0);
fluid.addComponent("methane", 0.65);
fluid.addComponent("ethane", 0.08);
fluid.addComponent("propane", 0.05);
fluid.addComponent("n-hexane", 0.10);
fluid.addComponent("n-decane", 0.10);
fluid.addComponent("water", 0.02);
fluid.setMixingRule("classic");
fluid.setMultiPhaseCheck(true);

Stream wellhead = new Stream("Wellhead", fluid);
wellhead.setFlowRate(50000.0, "kg/hr");
wellhead.run();

// 25 km subsea flowline
MultiphasePipe flowline = new MultiphasePipe("Subsea Flowline", wellhead);
flowline.setLength(25000.0, "m");
flowline.setDiameter(0.254, "m");  // 10 inch
flowline.setNumberOfNodes(50);
flowline.setOverallHeatTransferCoefficient(15.0, "W/m2K");
flowline.setAmbientTemperature(4.0, "C");
flowline.run();

// Results
System.out.println("Outlet pressure: " + flowline.getOutletStream().getPressure("bara") + " bara");
System.out.println("Outlet temperature: " + flowline.getOutletStream().getTemperature("C") + " °C");
System.out.println("Liquid holdup: " + flowline.getLiquidHoldup());
System.out.println("Flow regime: " + flowline.getFlowRegimeDescription());
System.out.println("Heat loss: " + flowline.getHeatLoss("MW") + " MW");
```

### Example 3: Vertical Riser

```java
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

// Production from seabed
Stream production = new Stream("Seabed Production", wellfluid);
production.setFlowRate(30000.0, "kg/hr");
production.run();

// 500m riser
PipeBeggsAndBrills riser = new PipeBeggsAndBrills("Riser", production);
riser.setLength(550.0, "m");  // Include catenary
riser.setDiameter(0.2, "m");
riser.setElevationChange(500.0, "m");  // Vertical rise
riser.run();

System.out.println("Bottom pressure: " + production.getPressure("bara") + " bara");
System.out.println("Top pressure: " + riser.getOutletStream().getPressure("bara") + " bara");
System.out.println("Flow regime: " + riser.getFlowRegimeDescription());
System.out.println("Liquid holdup: " + riser.getLiquidHoldup());
```

### Example 4: Pipeline with Mechanical Design

```java
// Create pipeline
AdiabaticPipe pipe = new AdiabaticPipe("Gas Pipeline", inlet);
pipe.setLength(50000.0, "m");
pipe.setDiameter(0.508, "m");
pipe.run();

// Mechanical design
pipe.initMechanicalDesign();
PipelineMechanicalDesign design = (PipelineMechanicalDesign) pipe.getMechanicalDesign();
design.setMaxOperationPressure(150.0);
design.setMaterialGrade("X65");
design.setDesignStandardCode("ASME-B31.8");
design.setLocationClass("Class 2");
design.calcDesign();

// Get design results
System.out.println("Wall thickness: " + design.getWallThickness() + " mm");
System.out.println("MAOP: " + design.getCalculator().getMAOP("bar") + " bar");
System.out.println("Test pressure: " + design.getCalculator().calculateTestPressure() + " MPa");

// Cost estimation
design.getCalculator().calculateProjectCost();
System.out.println("Total cost: $" + design.getCalculator().getTotalProjectCost());

// Full JSON report
String json = design.toJson();
```

---

## Related Documentation

- [Pipeline Mechanical Design](../pipeline_mechanical_design) - Wall thickness, stress analysis, cost estimation
- [Mechanical Design Standards](../mechanical_design_standards) - ASME, DNV, API standards
- [Fluid Mechanics](../../fluidmechanics/) - Detailed flow modeling
- [Valves](valves) - Flow control devices
- [Equipment Index](./\) - All equipment types
