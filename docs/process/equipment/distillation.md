---
title: Distillation Equipment
description: Documentation for distillation column equipment in NeqSim process simulation.
---

# Distillation Equipment

Documentation for distillation column equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Column Types](#column-types)
- [Configuration](#configuration)
- [Builder Pattern](#builder-pattern)
- [Solver Options](#solver-options)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.distillation`

**Classes:**
- `DistillationColumn` - Main distillation column
- `SimpleTray` - Individual tray
- `Condenser` - Column condenser
- `Reboiler` - Column reboiler

---

## Basic Usage

```java
import neqsim.process.equipment.distillation.DistillationColumn;

// Create column with 10 trays, condenser, and reboiler
DistillationColumn column = new DistillationColumn("Deethanizer", 10, true, true);
column.addFeedStream(feedStream, 5);  // Feed on tray 5
column.setCondenserTemperature(40.0, "C");
column.setReboilerTemperature(120.0, "C");
column.run();

// Get products
Stream overhead = column.getGasOutStream();
Stream bottoms = column.getLiquidOutStream();
```

---

## Builder Pattern

For complex column configurations, use the fluent Builder API:

```java
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.distillation.DistillationColumn.SolverType;

// Build column with fluent API
DistillationColumn column = DistillationColumn.builder("Deethanizer")
    .numberOfTrays(15)
    .withCondenserAndReboiler()
    .topPressure(25.0, "bara")
    .bottomPressure(26.0, "bara")
    .temperatureTolerance(0.001)
    .massBalanceTolerance(0.01)
    .maxIterations(100)
    .solverType(SolverType.INSIDE_OUT)
    .internalDiameter(2.5)
    .addFeedStream(feedStream, 8)
    .build();

column.run();
```

### Builder Methods

| Method | Description |
|--------|-------------|
| `numberOfTrays(int)` | Set number of simple trays (excluding condenser/reboiler) |
| `withCondenser()` | Add condenser at top |
| `withReboiler()` | Add reboiler at bottom |
| `withCondenserAndReboiler()` | Add both |
| `topPressure(double, String)` | Set top pressure with unit |
| `bottomPressure(double, String)` | Set bottom pressure with unit |
| `pressure(double, String)` | Set same pressure top and bottom |
| `temperatureTolerance(double)` | Convergence tolerance for temperature |
| `massBalanceTolerance(double)` | Convergence tolerance for mass balance |
| `tolerance(double)` | Set all tolerances at once |
| `maxIterations(int)` | Maximum solver iterations |
| `solverType(SolverType)` | Set solver algorithm |
| `directSubstitution()` | Use direct substitution solver |
| `dampedSubstitution()` | Use damped substitution solver |
| `insideOut()` | Use inside-out solver |
| `relaxationFactor(double)` | Damping factor for solver |
| `internalDiameter(double)` | Column internal diameter (meters) |
| `multiPhaseCheck(boolean)` | Enable/disable multi-phase check |
| `addFeedStream(Stream, int)` | Add feed stream to specified tray |
| `build()` | Build the configured column |

---

## Column Configuration

### Number of Trays

```java
// Constructor: (name, numTrays, hasCondenser, hasReboiler)
DistillationColumn column = new DistillationColumn("T-100", 20, true, true);
```

### Feed Location

```java
// Single feed
column.addFeedStream(feed, 10);  // Tray 10 from bottom

// Multiple feeds
column.addFeedStream(feed1, 8);
column.addFeedStream(feed2, 12);
```

### Condenser Type

```java
// Total condenser
column.setCondenserType("total");

// Partial condenser (vapor overhead)
column.setCondenserType("partial");
```

### Side Draws

```java
// Liquid side draw
column.addSideDraw(7, "liquid", 100.0, "kg/hr");

// Vapor side draw
column.addSideDraw(15, "vapor", 50.0, "kg/hr");
```

---

## Operating Specifications

### Temperature Specifications

```java
// Condenser temperature
column.setCondenserTemperature(40.0, "C");

// Reboiler temperature
column.setReboilerTemperature(120.0, "C");
```

### Pressure Profile

```java
// Top pressure
column.setTopPressure(15.0, "bara");

// Bottom pressure (or pressure drop)
column.setBottomPressure(16.0, "bara");

// Or specify pressure drop per tray
column.setPressureDropPerTray(0.05, "bar");
```

### Reflux Specifications

```java
// Reflux ratio
column.setRefluxRatio(3.0);

// Condenser duty
column.setCondenserDuty(-5000000.0);  // W (negative = cooling)
```

### Reboiler Specifications

```java
// Reboiler duty
column.setReboilerDuty(6000000.0);  // W

// Boilup ratio
column.setBoilupRatio(2.5);
```

---

## Solver Options

### Available Solvers

```java
// Standard sequential solver
column.setSolverType(DistillationColumn.SolverType.STANDARD);

// Damped solver (more robust)
column.setSolverType(DistillationColumn.SolverType.DAMPED);

// Inside-out solver (fastest for converged cases)
column.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
```

### Convergence Settings

```java
// Maximum iterations
column.setMaxIterations(100);

// Tolerance
column.setTolerance(1e-6);

// Damping factor
column.setDampingFactor(0.5);
```

### Initialization

```java
// Linear temperature profile initialization
column.setInitialTemperatureProfile("linear");

// Custom initialization
double[] initTemps = {120, 115, 110, 105, 100, 95, 90, 85, 80, 75, 70};
column.setInitialTemperatures(initTemps);
```

---

## Column Results

### Tray-by-Tray Profiles

```java
column.run();

// Temperature profile
for (int i = 0; i < column.getNumberOfTrays(); i++) {
    double T = column.getTray(i).getTemperature("C");
    System.out.println("Tray " + i + ": " + T + " °C");
}

// Composition profile
for (int i = 0; i < column.getNumberOfTrays(); i++) {
    double[] x = column.getTray(i).getLiquidComposition();
    double[] y = column.getTray(i).getVaporComposition();
}
```

### Duties

```java
double Qcond = column.getCondenserDuty();  // W
double Qreb = column.getReboilerDuty();    // W

System.out.println("Condenser duty: " + (-Qcond/1e6) + " MW");
System.out.println("Reboiler duty: " + (Qreb/1e6) + " MW");
```

### Separation Performance

```java
// Product purities
double overheadPurity = overhead.getFluid().getComponent("ethane").getx();
double bottomsRecovery = 1.0 - (overhead.getFluid().getComponent("propane").getNumberOfmable() /
    feedStream.getFluid().getComponent("propane").getNumberOfmable());
```

---

## Example: NGL Fractionation

### Deethanizer

```java
// Feed: NGL from gas plant
SystemInterface ngl = new SystemSrkEos(273.15 + 30, 25.0);
ngl.addComponent("methane", 0.02);
ngl.addComponent("ethane", 0.25);
ngl.addComponent("propane", 0.35);
ngl.addComponent("i-butane", 0.10);
ngl.addComponent("n-butane", 0.18);
ngl.addComponent("n-pentane", 0.10);
ngl.setMixingRule("classic");

Stream feed = new Stream("NGL Feed", ngl);
feed.setFlowRate(5000.0, "kg/hr");

ProcessSystem process = new ProcessSystem();
process.add(feed);

// Deethanizer column
DistillationColumn deethanizer = new DistillationColumn("Deethanizer", 25, true, true);
deethanizer.addFeedStream(feed, 12);
deethanizer.setTopPressure(25.0, "bara");
deethanizer.setCondenserTemperature(-10.0, "C");
deethanizer.setReboilerTemperature(100.0, "C");
deethanizer.setSolverType(DistillationColumn.SolverType.INSIDE_OUT);
process.add(deethanizer);

process.run();

// Results
Stream ethaneProduct = deethanizer.getGasOutStream();
Stream c3plusProduct = deethanizer.getLiquidOutStream();

System.out.println("Ethane product:");
System.out.println("  Flow: " + ethaneProduct.getFlowRate("kg/hr") + " kg/hr");
System.out.println("  C2 purity: " + 
    ethaneProduct.getFluid().getComponent("ethane").getx() * 100 + " mol%");

System.out.println("C3+ product:");
System.out.println("  Flow: " + c3plusProduct.getFlowRate("kg/hr") + " kg/hr");
System.out.println("  C2 content: " + 
    c3plusProduct.getFluid().getComponent("ethane").getx() * 100 + " mol%");
```

### Depropanizer

```java
// Feed from deethanizer bottoms
DistillationColumn depropanizer = new DistillationColumn("Depropanizer", 30, true, true);
depropanizer.addFeedStream(c3plusProduct, 15);
depropanizer.setTopPressure(18.0, "bara");
depropanizer.setCondenserTemperature(45.0, "C");
depropanizer.setReboilerTemperature(110.0, "C");
process.add(depropanizer);

process.run();

Stream propaneProduct = depropanizer.getGasOutStream();
Stream c4plusProduct = depropanizer.getLiquidOutStream();
```

---

## Absorber Column

For absorption without reboiler:

```java
DistillationColumn absorber = new DistillationColumn("Absorber", 10, false, false);
absorber.addFeedStream(gasStream, 1);      // Gas at bottom
absorber.addFeedStream(leanSolvent, 10);   // Solvent at top
absorber.run();

Stream richSolvent = absorber.getLiquidOutStream();
Stream sweetGas = absorber.getGasOutStream();
```

---

## Stripper Column

For stripping without condenser:

```java
DistillationColumn stripper = new DistillationColumn("Stripper", 8, false, true);
stripper.addFeedStream(richSolvent, 1);
stripper.setReboilerTemperature(120.0, "C");
stripper.run();

Stream acidGas = stripper.getGasOutStream();
Stream leanSolvent = stripper.getLiquidOutStream();
```

---

## Related Documentation

- [Process Package](../README) - Package overview
- [Heat Exchangers](heat_exchangers) - Condensers and reboilers
- [Absorbers](absorbers) - Absorption columns
