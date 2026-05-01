---
title: Distillation Equipment
description: Documentation for distillation column equipment in NeqSim process simulation.
keywords: "distillation, column, tray, absorber, stripper, deethanizer, debutanizer, reboiler, condenser, reflux, fractionation, NGL, inside-out solver, MESH residual, convergence diagnostics"
---

# Distillation Equipment

Documentation for distillation column equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Column Types](#column-types)
- [Configuration](#configuration)
- [Builder Pattern](#builder-pattern)
- [Solver Options](#solver-options)
- [Column Specifications](#column-specifications)
- [Diagnostics and Residuals](#diagnostics-and-residuals)
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

// Create column with 10 trays, reboiler, and condenser
DistillationColumn column = new DistillationColumn("Deethanizer", 10, true, true);
column.addFeedStream(feedStream, 5);  // Feed on tray 5
column.setCondenserTemperature(273.15 + 40.0);  // Kelvin
column.setReboilerTemperature(273.15 + 120.0);  // Kelvin
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
| `topSpecification(ColumnSpecification)` | Set top product specification |
| `bottomSpecification(ColumnSpecification)` | Set bottom product specification |
| `topProductPurity(String, double)` | Shortcut: top product purity for named component |
| `bottomProductPurity(String, double)` | Shortcut: bottom product purity for named component |
| `build()` | Build the configured column |

---

## Column Configuration

### Number of Trays

```java
// Constructor: (name, numTrays, hasReboiler, hasCondenser)
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
column.setCondenserTemperature(273.15 + 40.0);  // Kelvin

// Reboiler temperature
column.setReboilerTemperature(273.15 + 120.0);  // Kelvin
```

### Pressure Profile

```java
// Top pressure
column.setTopPressure(15.0);  // bara

// Bottom pressure (or pressure drop)
column.setBottomPressure(16.0);  // bara
```

### Reflux Specifications

```java
// Reflux ratio
column.setCondenserRefluxRatio(3.0);

// Condenser duty
column.getCondenser().setHeatInput(-5000000.0);  // W (negative = cooling)
```

### Reboiler Specifications

```java
// Reboiler duty
column.getReboiler().setHeatInput(6000000.0);  // W

// Boilup ratio
column.setReboilerBoilupRatio(2.5);
```

---

## Column Specifications

NeqSim supports flexible column specifications through the `ColumnSpecification` class.
Instead of specifying condenser/reboiler temperatures or duties directly, you can
specify desired product quality targets and NeqSim will adjust operating conditions
automatically using a secant-method outer loop.

### Specification Types

| Type | Description | Example |
|------|------------|--------|
| `PRODUCT_PURITY` | Mole-fraction purity of a component in a product stream | 95 mol% ethane overhead |
| `REFLUX_RATIO` | Condenser reflux ratio (L/D) | Reflux ratio of 3.5 |
| `COMPONENT_RECOVERY` | Fraction of feed component recovered in a product (0–1) | 99% ethane recovery overhead |
| `PRODUCT_FLOW_RATE` | Total molar flow rate of a product stream (kmol/h) | 100 kmol/h overhead |
| `DUTY` | Condenser or reboiler duty (W) | Reboiler duty 5 MW |

### Product Purity Specification

```java
// Specify top and bottom product purities
column.setTopProductPurity("ethane", 0.95);      // 95 mol% ethane overhead
column.setBottomProductPurity("propane", 0.98);   // 98 mol% propane in bottoms
column.run();  // Outer loop adjusts condenser/reboiler temperatures
```

### Component Recovery Specification

```java
// Recover 99% of feed ethane in the overhead product
column.setTopComponentRecovery("ethane", 0.99);
column.run();
```

### Reflux and Boilup Ratio

```java
// Set reflux ratio (applied directly, no outer loop needed)
column.setCondenserRefluxRatio(3.5);

// Set boilup ratio
column.setReboilerBoilupRatio(2.0);
column.run();
```

### Product Flow Rate Specification

```java
// Set overhead flow rate target
column.setTopProductFlowRate(100.0);   // kmol/h
column.run();
```

### Using ColumnSpecification Directly

```java
import neqsim.process.equipment.distillation.ColumnSpecification;
import neqsim.process.equipment.distillation.ColumnSpecification.SpecificationType;
import neqsim.process.equipment.distillation.ColumnSpecification.ProductLocation;

// Create a custom specification
ColumnSpecification topSpec = new ColumnSpecification(
    SpecificationType.PRODUCT_PURITY,
    ProductLocation.TOP,
    0.95,        // target value
    "ethane"     // component name
);
topSpec.setTolerance(1e-3);      // convergence tolerance
topSpec.setMaxIterations(30);    // max outer-loop iterations

column.setTopSpecification(topSpec);
column.run();
```

### Builder Pattern with Specifications

```java
DistillationColumn column = DistillationColumn.builder("Deethanizer")
    .numberOfTrays(25)
    .withCondenserAndReboiler()
    .topPressure(25.0, "bara")
    .insideOut()
    .addFeedStream(feed, 12)
    .topProductPurity("ethane", 0.95)
    .bottomProductPurity("propane", 0.98)
    .build();
column.run();
```

### How It Works

For **direct specifications** (reflux ratio, duty), the values are applied on
the condenser or reboiler before the inner column solver runs.

For **iterative specifications** (product purity, component recovery, product
flow rate), NeqSim wraps the inner solver in a secant-method outer loop that:

1. Runs the column with initial temperature guesses
2. Evaluates the specification error (current − target)
3. Adjusts condenser or reboiler temperature using the secant update
4. Repeats until the error is within tolerance (default 1e-4)

---

## Solver Options

### Available Solvers

| Solver type | Strategy | Typical use |
|-------------|----------|-------------|
| `DIRECT_SUBSTITUTION` | Classic tray-by-tray substitution without extra damping. | Default choice for simple and well-posed columns. |
| `DAMPED_SUBSTITUTION` | Sequential substitution with an initial fixed relaxation factor. | Stiffer cases where direct substitution overshoots. |
| `INSIDE_OUT` | Inside-out style flow correction with K-value tracking and polishing. | General process work, deethanizers, and multi-feed columns. |
| `WEGSTEIN` | Accelerated successive substitution after a warm-up phase. | Well-conditioned columns where faster convergence is useful. |
| `SUM_RATES` | Flow-corrected tearing method using sum-rate style updates. | Absorbers, strippers, and flow-sensitive columns. |
| `NEWTON` | Tray-temperature correction accelerator with finite-difference Jacobian and line search. | Difficult temperature convergence cases. This is not a full simultaneous MESH Newton solver. |
| `MESH_RESIDUAL` | Runs inside-out initialization with Newton polishing and records full MESH residual diagnostics. | Auditing material, equilibrium, summation, energy, and specification residuals before future rigorous solver work. |

### Convergence Settings

| Method | Purpose |
|--------|---------|
| `setMaxNumberOfIterations(int)` | Set the minimum requested solver iteration limit; the column may use a larger adaptive limit for complex cases. |
| `setTemperatureTolerance(double)` | Override the adaptive average tray-temperature tolerance in Kelvin. |
| `setMassBalanceTolerance(double)` | Override the adaptive relative mass-balance tolerance. |
| `setEnthalpyBalanceTolerance(double)` | Override the adaptive relative enthalpy-balance tolerance. |
| `setEnforceEnergyBalanceTolerance(boolean)` | Require the energy residual to pass before `solved()` returns true. Disabled by default for backward compatibility. |
| `setRelaxationFactor(double)` | Set the starting relaxation factor for `DAMPED_SUBSTITUTION`. |
| `setMeshResidualTolerance(double)` | Set the scaled MESH residual norm tolerance used by the optional MESH convergence gate. |
| `setEnforceMeshResidualTolerance(boolean)` | Require the latest MESH residual vector to pass before `solved()` returns true. Disabled by default. |

### Initialization

Column initialization is automatic. `init()` runs the feed streams, places any unassigned feed near
the closest tray temperature, seeds a pressure profile from top and bottom pressure settings, and
links the neighboring vapor and liquid streams used by the tray sweeps. User-controlled condenser
and reboiler temperatures or duties remain the main practical way to influence the starting profile.

---

## Diagnostics and Residuals

Every `DistillationColumn.run()` records scalar convergence metrics and a scaled MESH residual
vector for the final column state. The residual vector groups equations into material,
equilibrium, summation, energy, and active specification residuals.

| Getter | Description |
|--------|-------------|
| `getLastIterationCount()` | Number of iterations used by the active solver. |
| `getLastTemperatureResidual()` | Average tray-temperature residual in Kelvin. |
| `getLastMassResidual()` | Relative mass-balance residual. |
| `getLastEnergyResidual()` | Relative enthalpy-balance residual. |
| `getLastSpecificationResidual()` | Largest absolute active top/bottom specification residual. |
| `getLastMeshResidualNorm()` | Infinity norm of the full scaled MESH residual vector. |
| `getLastMeshMaterialResidualNorm()` | Infinity norm of component material residuals. |
| `getLastMeshEquilibriumResidualNorm()` | Infinity norm of fugacity-equilibrium residuals. |
| `getLastMeshSummationResidualNorm()` | Infinity norm of vapor/liquid mole-fraction summation residuals. |
| `getLastMeshEnergyResidualNorm()` | Infinity norm of tray energy residuals. |
| `getLastMeshSpecificationResidualNorm()` | Infinity norm of active specification residuals. |
| `getLastMeshResidualVector()` | Copy of the full residual vector, ordered by internal equation metadata. |

The optional MESH residual convergence gate is off by default. Enable it when a workflow should
treat the full residual vector as part of the convergence contract rather than as diagnostics only.

---

## Column Results

### Tray-by-Tray Profiles

```java
column.run();

// Temperature profile
for (int i = 0; i < column.getTrays().size(); i++) {
    double temperatureC = column.getTray(i).getTemperature() - 273.15;
    double vaporFlow = column.getTray(i).getVaporFlowRate("kg/hr");
    double liquidFlow = column.getTray(i).getLiquidFlowRate("kg/hr");
    System.out.println("Tray " + i + ": " + temperatureC + " C, V=" + vaporFlow
        + " kg/hr, L=" + liquidFlow + " kg/hr");
}
```

### Duties

```java
double Qcond = column.getCondenser().getDuty();  // W
double Qreb = column.getReboiler().getDuty();    // W

System.out.println("Condenser duty: " + (-Qcond/1e6) + " MW");
System.out.println("Reboiler duty: " + (Qreb/1e6) + " MW");
```

### Separation Performance

```java
// Product purities
double overheadEthaneMoleFraction = overhead.getFluid().getComponent("ethane").getx();
double bottomsPropaneMoleFraction = bottoms.getFluid().getComponent("propane").getx();
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
deethanizer.setTopPressure(25.0);  // bara
deethanizer.setCondenserTemperature(273.15 - 10.0);  // Kelvin
deethanizer.setReboilerTemperature(273.15 + 100.0);  // Kelvin
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
depropanizer.setTopPressure(18.0);  // bara
depropanizer.setCondenserTemperature(273.15 + 45.0);  // Kelvin
depropanizer.setReboilerTemperature(273.15 + 110.0);  // Kelvin
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
stripper.setReboilerTemperature(273.15 + 120.0);  // Kelvin
stripper.run();

Stream acidGas = stripper.getGasOutStream();
Stream leanSolvent = stripper.getLiquidOutStream();
```

---

## Related Documentation

- [Process Package](../) - Package overview
- [Heat Exchangers](heat_exchangers) - Condensers and reboilers
- [Absorbers](absorbers) - Absorption columns
