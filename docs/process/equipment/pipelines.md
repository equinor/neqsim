# Pipelines and Pipes

Documentation for pipeline equipment in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Pipe Segment](#pipe-segment)
- [Pipeline](#pipeline)
- [Pressure Drop](#pressure-drop)
- [Heat Transfer](#heat-transfer)
- [Examples](#examples)

---

## Overview

**Location:** `neqsim.process.equipment.pipeline`

**Classes:**
| Class | Description |
|-------|-------------|
| `PipeBeggsAndBrills` | Beggs-Brill correlation |
| `AdiabaticPipe` | Adiabatic pipe segment |
| `OnePhasePipe` | Single-phase pipe |
| `TwoPhasePipeLine` | Two-phase pipeline |

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

### Example 4: Riser with Elevation

```java
// Gas lift production
Stream production = new Stream("Production", wellFluid);
production.setFlowRate(10000.0, "kg/hr");
production.run();

// Riser (500m water depth)
PipeBeggsAndBrills riser = new PipeBeggsAndBrills("Riser", production);
riser.setLength(550.0, "m");  // Account for catenary
riser.setDiameter(0.2, "m");
riser.setElevationChange(500.0, "m");  // Rise from seabed
riser.run();

System.out.println("Bottom P: " + production.getPressure("bara") + " bara");
System.out.println("Top P: " + riser.getOutletStream().getPressure("bara") + " bara");
System.out.println("Flow regime: " + riser.getFlowRegime());
```

---

## Related Documentation

- [Equipment Index](README.md) - All equipment
- [Fluid Mechanics](../../fluidmechanics/README.md) - Detailed pipe flow
- [Valves](valves.md) - Flow control
