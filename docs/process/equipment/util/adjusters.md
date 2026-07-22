---
title: Adjusters
description: "Documentation for adjuster equipment in NeqSim process simulation. Covers single-variable Adjuster with standard and functional interface modes, and MultiVariableAdjuster for simultaneous multi-variable specification using damped successive substitution."
---

# Adjusters

Documentation for adjuster equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Adjuster Class](#adjuster-class)
- [Configuration](#configuration)
- [Functional Interface Mode](#functional-interface-mode)
- [Usage Examples](#usage-examples)
- [Multiple Adjusters](#multiple-adjusters)
- [Multi-Variable Adjuster](#multi-variable-adjuster)
- [Troubleshooting](#troubleshooting)

---

## Overview

**Location:** `neqsim.process.equipment.util`

**Class:** `Adjuster`

Adjusters are iterative solvers that modify one process variable to achieve a target specification. They are essential for solving design problems where:
- An output specification is known
- The input parameter to achieve it must be found

NeqSim supports two configuration modes:
1. **Standard Mode**: Using predefined variable names (`setAdjustedVariable`, `setTargetVariable`)
2. **Functional Interface Mode**: Using lambda expressions for complete flexibility

---

## Adjuster Class

### Basic Usage

```java
import neqsim.process.equipment.util.Adjuster;

// Create adjuster
Adjuster adjuster = new Adjuster("Temperature Controller");

// Set the variable to adjust
adjuster.setAdjustedVariable(heater, "outTemperature");

// Set the target specification
adjuster.setTargetVariable(stream, "temperature", 80.0, "C");

// Add to process
process.add(adjuster);
process.run();

// Get the adjusted value
double adjustedTemp = heater.getOutTemperature("C");
```

---

## Configuration

### Adjusted Variables

The variable that the adjuster will modify:

| Equipment | Variable | Description |
|-----------|----------|-------------|
| `Heater/Cooler` | `"duty"` | Heat duty (W) |
| `Heater/Cooler` | `"outTemperature"` | Outlet temperature |
| `Compressor` | `"outletPressure"` | Discharge pressure |
| `Valve` | `"outletPressure"` | Outlet pressure |
| `Valve` | `"percentValveOpening"` | Valve position |
| `Splitter` | `"splitFactor"` | Split ratio |
| `Stream` | `"flowRate"` | Flow rate |

```java
// Examples
adjuster.setAdjustedVariable(heater, "duty");
adjuster.setAdjustedVariable(compressor, "outletPressure");
adjuster.setAdjustedVariable(valve, "percentValveOpening");
adjuster.setAdjustedVariable(splitter, "splitFactor", 0);  // First split
```

### Target Variables

The specification to be achieved:

| Equipment | Variable | Description |
|-----------|----------|-------------|
| `Stream` | `"temperature"` | Stream temperature |
| `Stream` | `"pressure"` | Stream pressure |
| `Stream` | `"flowRate"` | Stream flow rate |
| `Stream` | `"moleFraction"` | Component mole fraction |
| `Separator` | `"liquidLevel"` | Liquid level fraction |
| Any | Custom | User-defined property |

```java
// Examples
adjuster.setTargetVariable(stream, "temperature", 80.0, "C");
adjuster.setTargetVariable(separator, "liquidLevel", 0.5);
adjuster.setTargetVariable(stream, "moleFraction", 0.02, "CO2");
```

### Solver Settings

```java
// Maximum iterations
adjuster.setMaximumIterations(100);

// Convergence tolerance
adjuster.setTolerance(1e-6);

// Bounds on adjusted variable
adjuster.setMinimumValue(-1e7);  // Lower bound
adjuster.setMaximumValue(1e7);   // Upper bound

// Step size for numerical derivatives
adjuster.setStepSize(0.001);
```

---

## Functional Interface Mode

For complex scenarios where standard variable names are insufficient, the Adjuster supports **functional interfaces** (lambda expressions) for complete flexibility. This allows you to:

- Adjust variables that don't have predefined names
- Use complex logic for getting/setting values
- Define custom target calculations

### Functional Interface Methods

| Method | Signature | Description |
|--------|-----------|-------------|
| `setAdjustedValueSetter` | `Consumer<Double>` | Lambda to set the adjusted value |
| `setAdjustedValueGetter` | `Supplier<Double>` | Lambda to get the current adjusted value |
| `setTargetValueCalculator` | `Supplier<Double>` | Lambda to calculate the target (measured) value |
| `setTargetValue` | `double` | The setpoint that the target should reach |

### Basic Structure

```java
Adjuster adjuster = new Adjuster("adjuster");

// Set the setpoint (what targetValueCalculator should return)
adjuster.setTargetValue(desiredValue);

// Set bounds for the adjusted variable
adjuster.setMinAdjustedValue(minValue);
adjuster.setMaxAdjustedValue(maxValue);

// Lambda: How to SET the adjusted variable
adjuster.setAdjustedValueSetter((val) -> {
    // Your logic to set the value
    equipment.setSomeProperty(val);
});

// Lambda: How to GET the current adjusted variable value
adjuster.setAdjustedValueGetter(() -> {
    // Your logic to get the current value
    return equipment.getSomeProperty();
});

// Lambda: How to CALCULATE the measured variable (compared to setpoint)
adjuster.setTargetValueCalculator(() -> {
    // Your logic to calculate current target value
    return someCalculation();
});
```

### Example: Splitter Flow Control

Adjust a splitter's second outlet flow rate to achieve a target flow in the first outlet:

```java
// Create process equipment
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(1000.0, "kg/hr");

Splitter splitter = new Splitter("splitter", feed);
splitter.setSplitNumber(2);
splitter.setSplitFactors(new double[] {0.5, 0.5});

Stream stream1 = new Stream("stream1", splitter.getSplitStream(0));
Stream stream2 = new Stream("stream2", splitter.getSplitStream(1));

// Create adjuster with functional interfaces
Adjuster adjuster = new Adjuster("Flow Adjuster");

// Setpoint: we want stream1 to have 800 kg/hr
adjuster.setTargetValue(800.0);

// Bounds: stream2 flow must be between 0 and 1000 kg/hr
adjuster.setMinAdjustedValue(0.0);
adjuster.setMaxAdjustedValue(1000.0);

// Setter: Adjust the flow rate of stream2 via splitter
// Note: Splitter.setFlowRates uses -1 for "calculate this one"
adjuster.setAdjustedValueSetter((val) -> {
    splitter.setFlowRates(new double[] {-1, val}, "kg/hr");
});

// Getter: Get current flow rate of stream2
adjuster.setAdjustedValueGetter(() -> {
    return splitter.getSplitStream(1).getFlowRate("kg/hr");
});

// Target Calculator: Get flow rate of stream1 (what we're controlling)
adjuster.setTargetValueCalculator(() -> {
    return stream1.getFlowRate("kg/hr");
});

// Add to process
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(splitter);
process.add(stream1);
process.add(stream2);
process.add(adjuster);
process.run();

// Result: stream1 = 800 kg/hr, stream2 = 200 kg/hr
```

### Example: Custom Temperature Control

Adjust temperature to achieve a target product of flow × temperature:

```java
Stream inletStream = new Stream("inlet", fluid);
inletStream.setFlowRate(100.0, "kg/hr");
inletStream.setTemperature(200.0, "K");

Adjuster adjuster = new Adjuster("Custom Adjuster");

// Setpoint: flow × temperature = 30000
adjuster.setTargetValue(30000.0);
adjuster.setMinAdjustedValue(100.0);   // Min temp 100 K
adjuster.setMaxAdjustedValue(500.0);   // Max temp 500 K

// Setter: Adjust temperature
adjuster.setAdjustedValueSetter((val) -> {
    inletStream.setTemperature(val, "K");
});

// Getter: Get current temperature
adjuster.setAdjustedValueGetter(() -> {
    return inletStream.getTemperature("K");
});

// Target Calculator: flow × temperature
adjuster.setTargetValueCalculator(() -> {
    return inletStream.getFlowRate("kg/hr") * inletStream.getTemperature("K");
});

ProcessSystem process = new ProcessSystem();
process.add(inletStream);
process.add(adjuster);
process.run();

// Result: temperature adjusts to 300 K (100 × 300 = 30000)
```

### Equipment-Based Functional Interfaces

For cleaner code when working with specific equipment, use the equipment-aware signatures:

```java
// Set the equipment references
adjuster.setAdjustedVariable(inletStream);
adjuster.setTargetVariable(inletStream);

// Setter with equipment reference
adjuster.setAdjustedValueSetter((equipment, val) -> {
    Stream s = (Stream) equipment;
    s.setTemperature(val, "K");
});

// Getter with equipment reference
adjuster.setAdjustedValueGetter((equipment) -> {
    Stream s = (Stream) equipment;
    return s.getTemperature("K");
});

// Target calculator with equipment reference
adjuster.setTargetValueCalculator((equipment) -> {
    Stream s = (Stream) equipment;
    return s.getFlowRate("kg/hr") * s.getTemperature("K");
});
```

### When to Use Functional Interfaces

| Scenario | Recommended Approach |
|----------|---------------------|
| Standard properties (temperature, pressure, flow) | Standard mode with `setAdjustedVariable` |
| Properties not in predefined list | Functional interface mode |
| Complex calculations for target | Use `setTargetValueCalculator` |
| Adjusting one equipment to affect another | Functional interface mode |
| Multiple variables combined in target | Functional interface mode |
| Conditional logic in getting/setting | Functional interface mode |

---

## Usage Examples

### Temperature Control

```java
// Adjust heater duty to achieve target outlet temperature
Adjuster tempControl = new Adjuster("TC-100");
tempControl.setAdjustedVariable(heater, "duty");
tempControl.setTargetVariable(heater.getOutletStream(), "temperature", 100.0, "C");
process.add(tempControl);
```

### Dew Point Control

```java
// Adjust cooler to achieve hydrocarbon dew point
Adjuster dewPointControl = new Adjuster("HCDP Controller");
dewPointControl.setAdjustedVariable(cooler, "outTemperature");
dewPointControl.setTargetPhaseCondition(stream, "cricondenbar", 50.0, "bara");
process.add(dewPointControl);
```

### Product Purity

```java
// Adjust column reflux to achieve product purity
Adjuster purityControl = new Adjuster("Purity Controller");
purityControl.setAdjustedVariable(column, "refluxRatio");
purityControl.setTargetVariable(overhead, "moleFraction", 0.99, "methane");
process.add(purityControl);
```

### Separator Level Control

```java
// Adjust outlet valve to maintain liquid level
Adjuster levelControl = new Adjuster("LC-100");
levelControl.setAdjustedVariable(outletValve, "percentValveOpening");
levelControl.setTargetVariable(separator, "liquidLevel", 0.5);
process.add(levelControl);
```

### Flow Split Optimization

```java
// Adjust split ratio to achieve target flow in branch
Adjuster flowControl = new Adjuster("FC-100");
flowControl.setAdjustedVariable(splitter, "splitFactor", 0);
flowControl.setTargetVariable(branchStream, "flowRate", 5000.0, "kg/hr");
process.add(flowControl);
```

---

## Multiple Adjusters

When using multiple adjusters, add them in order of priority:

```java
// First adjuster (higher priority)
Adjuster adj1 = new Adjuster("Primary");
adj1.setAdjustedVariable(heater, "duty");
adj1.setTargetVariable(stream1, "temperature", 80.0, "C");
process.add(adj1);

// Second adjuster (solved after first converges)
Adjuster adj2 = new Adjuster("Secondary");
adj2.setAdjustedVariable(cooler, "duty");
adj2.setTargetVariable(stream2, "temperature", 30.0, "C");
process.add(adj2);
```

---

## Multi-Variable Adjuster

**Class:** `MultiVariableAdjuster` (package `neqsim.process.equipment.util`)

The `MultiVariableAdjuster` solves N equations in N unknowns simultaneously using
**damped successive substitution**. This is more robust than
chaining N single-variable adjusters, which solve sequentially and may oscillate when
the variables interact.

### When to Use MultiVariableAdjuster vs Single Adjuster

| Scenario | Recommended |
|----------|-------------|
| One spec, one variable | `Adjuster` (single) |
| N independent specs | N separate `Adjuster` instances |
| N interacting specs (e.g., simultaneous T and P targets) | `MultiVariableAdjuster` |
| Column condenser/reboiler coupled duties | `MultiVariableAdjuster` |

### Basic Usage

```java
import neqsim.process.equipment.util.MultiVariableAdjuster;

// Build the process
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(5000.0, "kg/hr");

Heater heater = new Heater("heater", feed);
heater.setOutTemperature(350.0);  // Initial guess

Compressor comp = new Compressor("compressor", heater.getOutletStream());
comp.setOutletPressure(80.0);    // Initial guess

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(heater);
process.add(comp);

// Multi-variable adjuster: adjust 2 variables to hit 2 targets
MultiVariableAdjuster mva = new MultiVariableAdjuster("MVA");

// Variable 1: heater outlet temperature
mva.addAdjustedVariable(heater, "outTemperature", "K");

// Variable 2: compressor outlet pressure
mva.addAdjustedVariable(comp, "outletPressure", "bara");

// Target 1: product stream temperature = 400 K
mva.addTargetSpecification(comp, "temperature", 400.0, "K");

// Target 2: product stream pressure = 100 bara
mva.addTargetSpecification(comp, "pressure", 100.0, "bara");

// Optional: bounds and solver settings
mva.setVariableBounds(0, 300.0, 500.0);  // Heater temp bounds
mva.setVariableBounds(1, 50.0, 200.0);   // Compressor P bounds
mva.setMaxIterations(50);
mva.setTolerance(1e-4);

// Add to process and run
process.add(mva);
process.run();

// Check convergence
if (mva.isConverged()) {
    System.out.println("Converged in " + mva.getIterations() + " iterations");
    System.out.println("Max residual: " + mva.getMaxResidual());
} else {
    System.out.println("WARNING: did not converge");
}
```

### Key Methods

| Method | Description |
|--------|-------------|
| `addAdjustedVariable(equipment, variable, unit)` | Add a variable to manipulate |
| `addTargetSpecification(equipment, variable, target, unit)` | Add a target to satisfy |
| `setVariableBounds(index, lower, upper)` | Set bounds on adjusted variable by index |
| `setMaxIterations(int)` | Maximum solver iterations (default: 50) |
| `setTolerance(double)` | Convergence tolerance (default: 1e-4) |
| `isConverged()` | Whether the solver converged |
| `getIterations()` | Number of iterations used |
| `getMaxResidual()` | Largest residual at convergence |
| `getNumberOfVariables()` | Number of adjusted variables |

### How It Works

The damped successive substitution algorithm adjusts each variable by a fraction
of the current residual:

$$\mathbf{x}_{k+1} = \mathbf{x}_k + \alpha \cdot \mathbf{r}_k$$

where $\mathbf{r}_k = \mathbf{t} - \mathbf{y}(\mathbf{x}_k)$ is the residual vector
(target minus current output) and $\alpha = 0.1$ is the damping factor. This
provides robust first-order convergence for a wide range of process gains.
The method converges when $|1 - \alpha \cdot g| < 1$ for all variables, where
$g$ is the process gain (sensitivity of output to input).

---

## Troubleshooting

### Convergence Issues

```java
// Increase iterations
adjuster.setMaximumIterations(200);

// Widen bounds
adjuster.setMinimumValue(-1e8);
adjuster.setMaximumValue(1e8);

// Check if converged
if (!adjuster.isConverged()) {
    System.out.println("Adjuster did not converge");
    System.out.println("Current error: " + adjuster.getError());
}
```

### Infeasible Specifications

Some specifications may be physically impossible:
- Heating to above decomposition temperature
- Cooling below freezing with liquid product
- Pressure above equipment limits

Check that specifications are achievable before troubleshooting solver settings.

---

## Related Documentation

- [Recycles](recycles) - Recycle handling
- [Calculators](calculators) - Custom calculations
- [Process Controllers](../../controllers) - PID and logic control
- [SQP Optimizer](../../optimization/sqp_optimizer) - Nonlinear constrained optimization
- [Optimization Overview](../../optimization/OPTIMIZATION_OVERVIEW) - All optimization tools
