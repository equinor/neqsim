# Adjusters

Documentation for adjuster equipment in NeqSim process simulation.

## Table of Contents
- [Overview](#overview)
- [Adjuster Class](#adjuster-class)
- [Configuration](#configuration)
- [Usage Examples](#usage-examples)

---

## Overview

**Location:** `neqsim.process.equipment.util`

**Class:** `Adjuster`

Adjusters are iterative solvers that modify one process variable to achieve a target specification. They are essential for solving design problems where:
- An output specification is known
- The input parameter to achieve it must be found

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

- [Recycles](recycles.md) - Recycle handling
- [Calculators](calculators.md) - Custom calculations
- [Process Controllers](../../controllers.md) - PID and logic control
