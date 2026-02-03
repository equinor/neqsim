---
title: "Pipeline Pressure Drop Calculations in NeqSim"
description: "NeqSim provides three main pipeline models for calculating pressure drop:"
---

# Pipeline Pressure Drop Calculations in NeqSim

## Overview

NeqSim provides three main pipeline models for calculating pressure drop:

| Model | Class | Best For |
|-------|-------|----------|
| `AdiabaticPipe` | Single-phase compressible gas | High-pressure gas transmission |
| `AdiabaticTwoPhasePipe` | General two-phase flow | Moderate accuracy, fast computation |
| `PipeBeggsAndBrills` | Multiphase flow with correlations | Wells, flowlines, complex terrain |

## Quick Start

### Single-Phase Gas Pipeline

```java
// Create gas system
SystemInterface gas = new SystemSrkEos(298.15, 100.0); // 25°C, 100 bara
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.1);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(50000, "kg/hr");
feed.run();

// Simple pipe model
AdiabaticPipe pipe = new AdiabaticPipe("pipeline", feed);
pipe.setLength(10000);        // 10 km
pipe.setDiameter(0.3);        // 300 mm
pipe.run();

double pressureDrop = feed.getPressure() - pipe.getOutletPressure();
```

### Two-Phase (Gas-Liquid) Pipeline

```java
// Create two-phase system
SystemInterface fluid = new SystemSrkEos(333.15, 50.0); // 60°C, 50 bara
fluid.addComponent("methane", 5000, "kg/hr");
fluid.addComponent("nC10", 50000, "kg/hr");
fluid.setMixingRule(2);

Stream feed = new Stream("feed", fluid);
feed.run();

// Beggs & Brill correlation
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("flowline", feed);
pipe.setLength(1000);           // 1 km
pipe.setDiameter(0.1);          // 100 mm
pipe.setElevation(50);          // 50 m uphill
pipe.setPipeWallRoughness(4.6e-5); // Steel roughness
pipe.setNumberOfIncrements(20);
pipe.run();

// Get results
double dp = pipe.getInletPressure() - pipe.getOutletPressure();
String flowRegime = pipe.getFlowRegime().toString();
double liquidHoldup = pipe.getSegmentLiquidHoldup(20);
```

### Calculate Flow Rate from Outlet Pressure

All pipeline models support a reverse calculation mode where you specify the desired outlet pressure and the model calculates the required flow rate:

```java
// Create gas system with initial flow estimate
SystemInterface gas = new SystemSrkEos(298.15, 100.0); // 25°C, 100 bara
gas.addComponent("methane", 1.0);
gas.setMixingRule("classic");
gas.setTotalFlowRate(10000, "kg/hr"); // Initial estimate (will be recalculated)

Stream feed = new Stream("feed", gas);
feed.run();

// PipeBeggsAndBrills - most accurate for flow calculation
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("pipeline", feed);
pipe.setLength(10000);           // 10 km
pipe.setDiameter(0.3);           // 300 mm
pipe.setPipeWallRoughness(4.6e-5);
pipe.setNumberOfIncrements(10);
pipe.setOutletPressure(90.0);    // Specify target outlet pressure (bara)
pipe.run();

// Get calculated flow rate
double flowRate = pipe.getInletStream().getFlowRate("kg/hr");
double achievedOutletP = pipe.getOutletPressure();
// flowRate ≈ 144,000 kg/hr for 10 bar pressure drop
```

#### Supported Models for Flow Calculation

| Model | Method | Accuracy | Notes |
|-------|--------|----------|-------|
| `PipeBeggsAndBrills` | `setOutletPressure(double)` | Best | Uses bisection iteration |
| `AdiabaticPipe` | `setOutPressure(double)` | Good | Single-phase gas only |
| `AdiabaticTwoPhasePipe` | `setOutPressure(double)` | Moderate | Two-phase capable |

## Model Selection Guide

### Use `AdiabaticPipe` when:
- Single-phase gas flow
- Long-distance transmission pipelines
- High pressure (>20 bara)
- Need fast computation

### Use `AdiabaticTwoPhasePipe` when:
- Two-phase flow with moderate accuracy needs
- Processing facility piping
- Quick screening calculations

### Use `PipeBeggsAndBrills` when:
- Multiphase flow (gas-oil, gas-oil-water)
- Well tubing and flowlines
- Significant elevation changes
- Need flow regime identification
- Liquid holdup predictions required
- Transient simulations

## Accuracy Comparison

Based on validation against Darcy-Weisbach reference:

| Condition | AdiabaticPipe | TwoPhasePipe | BeggsAndBrills |
|-----------|---------------|--------------|----------------|
| Single-phase gas | +0.9% | -0.1% | +0.5% |
| Single-phase liquid (turbulent) | -4.1% | -1.4% | -1.4% |
| Single-phase liquid (laminar) | N/A | ~0% | ~0% |
| Two-phase horizontal | N/A | N/A | Validated |
| Inclined pipe | N/A | N/A | Validated |

## Calculation Modes

### Forward Mode (Default)
Specify flow rate → Calculate outlet pressure

```java
feed.setFlowRate(50000, "kg/hr");  // Known flow rate
pipe.run();
double pOut = pipe.getOutletPressure();  // Calculated
```

### Reverse Mode
Specify outlet pressure → Calculate flow rate

```java
pipe.setOutletPressure(90.0);  // Target outlet pressure
pipe.run();
double flow = feed.getFlowRate("kg/hr");  // Calculated
```

The reverse calculation uses a bisection algorithm that iteratively adjusts the flow rate until the calculated outlet pressure matches the specified target.

## See Also

- [Beggs & Brill Correlation Details](beggs_and_brill_correlation.md)
- [Pipeline Transient Simulation](pipeline_transient_simulation.md)
- [Friction Factor Calculations](friction_factor_models.md)
- [Heat Transfer in Pipelines](pipeline_heat_transfer.md)
