---
title: Flow Rate Optimization
description: This guide covers the `FlowRateOptimizer` class for calculating optimal flow rates given pressure boundary conditions and generating lift curve tables for Eclipse reservoir simulation.
---

# Flow Rate Optimization

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW.md) to understand when to use which optimizer.

This guide covers the `FlowRateOptimizer` class for calculating optimal flow rates given pressure boundary conditions and generating lift curve tables for Eclipse reservoir simulation.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE.md) | ProcessOptimizationEngine and VFP export |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | ProductionOptimizer examples |

## Overview

The `FlowRateOptimizer` is designed to solve a common production optimization problem:

> *Given inlet and outlet pressure constraints, what is the maximum achievable flow rate?*

This is essential for:
- **Reservoir simulation coupling** - Eclipse VFP tables relate BHP to production rates
- **Debottlenecking studies** - Finding capacity limits in compression systems
- **Operational optimization** - Maximizing throughput while respecting equipment limits

## Key Features

| Feature | Description |
|---------|-------------|
| **Pressure boundary search** | Find max flow at given inlet/outlet pressures |
| **Lift curve tables** | 2D tables for Eclipse VFP/VFPPROD keywords |
| **Capacity curves** | 1D curves at fixed inlet pressure |
| **Compressor constraints** | Surge, stonewall, power, speed limits |
| **Eclipse export** | Direct VFPPROD/VFPINJ format output |
| **JSON export** | Machine-readable results for external tools |

## Table of Contents

- [Quick Start](#quick-start)
- [Lift Curve Generation](#lift-curve-generation)
- [Professional Lift Curve Generation](#professional-lift-curve-generation)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [Python Usage (via JPype)](#python-usage-via-jpype)
  - [Basic Setup](#basic-setup)
  - [Finding Maximum Flow Rate](#finding-maximum-flow-rate)
  - [Generating Lift Curve Tables](#generating-lift-curve-tables)
  - [Parallel Lift Curve Generation](#parallel-lift-curve-generation)
  - [Plotting Lift Curves (matplotlib)](#plotting-lift-curves-matplotlib)
- [API Reference](#api-reference)

---

## Quick Start

### Basic Flow Rate Calculation

```java
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.FlowRateOptimizer;
import neqsim.thermo.system.SystemSrkEos;

// Create process
SystemSrkEos gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("Feed", gas);
feed.setFlowRate(50000, "kg/hr");
feed.setPressure(50.0, "bara");

Compressor comp = new Compressor("Export Compressor", feed);
comp.setOutletPressure(100.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(comp);
process.run();

// Create optimizer
FlowRateOptimizer optimizer = new FlowRateOptimizer(process, "Feed", "Export Compressor");
optimizer.setMinSurgeMargin(0.15);  // 15% surge margin
optimizer.setMaxPowerLimit(5000.0); // 5 MW max
optimizer.configureProcessCompressorCharts();

// Find max flow rate
FlowRateOptimizer.ProcessOperatingPoint result = 
    optimizer.findMaxFlowRateAtPressureBoundaries(50.0, 100.0, "bara", 0.95);

if (result != null && result.isFeasible()) {
    System.out.println("Max flow rate: " + result.getFlowRate() + " kg/hr");
    System.out.println("Total power: " + result.getTotalPower() + " kW");
}
```

---

## Lift Curve Generation

### Process Capacity Table

Generate a 2D table of operating points for multiple inlet/outlet pressure combinations:

```java
// Define pressure grid
double[] inletPressures = {40.0, 50.0, 60.0, 70.0, 80.0};      // bara
double[] outletPressures = {90.0, 100.0, 110.0, 120.0, 130.0}; // bara

// Generate table (sequential by default)
FlowRateOptimizer.ProcessCapacityTable table = 
    optimizer.generateProcessCapacityTable(
        inletPressures, 
        outletPressures, 
        "bara", 
        0.95  // max utilization
    );

// Export to Eclipse format
String eclipseVFP = table.toEclipseFormat();
System.out.println(eclipseVFP);
```

### Parallel Lift Curve Generation

For large pressure grids, enable parallel evaluation to speed up generation:

```java
// Enable parallel evaluation for faster lift curve generation
optimizer.setEnableParallelEvaluation(true);
optimizer.setParallelThreads(4);  // Use 4 threads (default: CPU count)

// Generate table in parallel - each pressure combination evaluated concurrently
FlowRateOptimizer.ProcessCapacityTable table = 
    optimizer.generateProcessCapacityTable(
        inletPressures,   // e.g., 10 inlet pressures
        outletPressures,  // e.g., 10 outlet pressures = 100 evaluations
        "bara", 
        0.95
    );
```

**Notes on parallel evaluation:**
- Each thread uses a cloned copy of the process system for thread safety
- Memory usage increases with thread count
- Best for large tables (50+ pressure combinations)
- Progress callbacks still work but may report out-of-order

### Export and Access Results

```java
// Export to JSON
String json = table.toJson();

// Get specific operating point
FlowRateOptimizer.ProcessOperatingPoint point = table.getOperatingPoint(1, 2);
System.out.println("Flow at Pin=50, Pout=110: " + point.getFlowRate() + " kg/hr");
```

### Eclipse VFP Output Format

The `toEclipseFormat()` method generates Eclipse-compatible VFPPROD tables:

```
-- =============================================================
-- Process Capacity Table: Export System
-- Generated by NeqSim FlowRateOptimizer
-- Generation Date: 2026-01-18T10:30:00
-- Max Utilization Constraint: 95.0%
-- Pressure Unit: bara
-- Flow Rate Unit: kg/hr
-- =============================================================

VFPPROD
1                                  / TABLE NUMBER
50000.0 60000.0 70000.0           / FLOW RATES
40.0 50.0 60.0 70.0 80.0          / THP VALUES
90.0 100.0 110.0 120.0 130.0      / BHP VALUES
...
/
```

---

## Professional Lift Curve Generation

For production-quality lift curves, use the `LiftCurveConfiguration` builder:

```java
// Configure lift curve generation
FlowRateOptimizer.LiftCurveConfiguration config = 
    new FlowRateOptimizer.LiftCurveConfiguration()
        .setTableName("Export_System_VFP")
        .setTableNumber(1)
        .setInletPressures(new double[] {40, 50, 60, 70, 80})
        .setOutletPressures(new double[] {90, 100, 110, 120})
        .setPressureUnit("bara")
        .setFlowUnit("kg/hr")
        .setMaxUtilization(0.95)
        .setSurgeMargin(0.15)
        .setMaxPowerLimit(5000.0)
        .setIncludePowerData(true)
        .setIncludeCompressorDetails(true);

// Generate professional lift curves
FlowRateOptimizer.LiftCurveResult result = 
    optimizer.generateProfessionalLiftCurves(config);

// Get Eclipse format
System.out.println(result.getCapacityTable().toEclipseFormat());

// Check for warnings
for (String warning : result.getWarnings()) {
    System.out.println("Warning: " + warning);
}

// Get statistics
System.out.println("Total points: " + result.getTotalPoints());
System.out.println("Feasible points: " + result.getFeasiblePoints());
System.out.println("Generation time: " + result.getGenerationTimeMs() + " ms");
```

---

## Constraint Configuration

### Compressor Constraints

```java
// Set surge/stonewall margins
optimizer.setMinSurgeMargin(0.15);      // 15% minimum surge margin
optimizer.setMinStonewallMargin(0.05);  // 5% minimum stonewall margin

// Set power limits
optimizer.setMaxPowerLimit(5000.0);     // Per compressor limit (kW)
optimizer.setTotalMaxPower(15000.0);    // Total system power limit (kW)

// Set speed limits
optimizer.setMinSpeedRatio(0.7);        // Minimum 70% of design speed
optimizer.setMaxSpeedRatio(1.05);       // Maximum 105% of design speed

// Configure compressor charts automatically
optimizer.configureProcessCompressorCharts();
```

### Equipment Utilization Limits

```java
// Set overall max utilization
optimizer.setMaxUtilization(0.95);  // 95% max for all equipment

// Set equipment-specific limits
optimizer.setEquipmentUtilizationLimit("HP Separator", 0.85);
optimizer.setEquipmentUtilizationLimit("Export Compressor", 0.90);
```

---

## Performance Tables

### Process Performance Table

Generate a table showing performance at different flow rates:

```java
double[] flowRates = {30000, 50000, 70000, 90000, 110000};  // kg/hr

FlowRateOptimizer.ProcessPerformanceTable perfTable = 
    optimizer.generateProcessPerformanceTable(
        flowRates,
        "kg/hr",
        60.0,    // inlet pressure
        "bara"
    );

// Print formatted table
System.out.println(perfTable.toFormattedString());

// Get data programmatically
for (int i = 0; i < flowRates.length; i++) {
    FlowRateOptimizer.ProcessOperatingPoint pt = perfTable.getOperatingPoint(i);
    System.out.printf("Flow: %.0f kg/hr, Power: %.0f kW, Feasible: %b%n",
        pt.getFlowRate(), pt.getTotalPower(), pt.isFeasible());
}
```

---

## Compressor Operating Point Data

Each `ProcessOperatingPoint` includes detailed compressor data:

```java
FlowRateOptimizer.ProcessOperatingPoint point = 
    optimizer.findMaxFlowRateAtPressureBoundaries(50.0, 100.0, "bara", 0.95);

// Get compressor details
for (String compName : point.getCompressorNames()) {
    FlowRateOptimizer.CompressorOperatingPoint cop = 
        point.getCompressorOperatingPoint(compName);
    
    System.out.println("Compressor: " + compName);
    System.out.println("  Power: " + cop.getPower() + " kW");
    System.out.println("  Speed: " + cop.getSpeed() + " RPM");
    System.out.println("  Flow: " + cop.getActualInletVolumeFlow() + " Am3/hr");
    System.out.println("  Head: " + cop.getPolytropicHead() + " kJ/kg");
    System.out.println("  Surge margin: " + cop.getSurgeMargin() * 100 + "%");
    System.out.println("  Stonewall margin: " + cop.getStonewallMargin() * 100 + "%");
}
```

---

## Operating Modes

The `FlowRateOptimizer` supports three operating modes:

### 1. PROCESS_SYSTEM Mode (Default)

For `ProcessSystem` objects with compressors:

```java
FlowRateOptimizer optimizer = new FlowRateOptimizer(processSystem, "Feed", "Outlet");
```

### 2. PROCESS_MODEL Mode

For `ProcessModel` objects:

```java
FlowRateOptimizer optimizer = new FlowRateOptimizer(processModel, "Feed", "Outlet");
```

### 3. SIMPLE Mode

For simple pressure drop calculations without detailed equipment:

```java
FlowRateOptimizer optimizer = new FlowRateOptimizer();
optimizer.setInletStream(inletStream);
optimizer.setOutletStream(outletStream);
optimizer.setMode(FlowRateOptimizer.Mode.SIMPLE);
```

---

## Validation

Validate optimizer configuration before running:

```java
List<String> issues = optimizer.validateConfiguration();
if (!issues.isEmpty()) {
    System.out.println("Configuration issues:");
    for (String issue : issues) {
        System.out.println("  - " + issue);
    }
} else {
    System.out.println("Configuration valid, ready to optimize");
}
```

---

## JSON Export

Export results in JSON format for integration with external tools:

```java
// Operating point to JSON
String pointJson = point.toJson();

// Capacity table to JSON
String tableJson = table.toJson();

// Full result to JSON
String resultJson = result.toJson();
```

Example JSON output:

```json
{
  "tableName": "Export_System_VFP",
  "pressureUnit": "bara",
  "flowRateUnit": "kg/hr",
  "maxUtilization": 0.95,
  "inletPressures": [40.0, 50.0, 60.0, 70.0, 80.0],
  "outletPressures": [90.0, 100.0, 110.0, 120.0],
  "operatingPoints": [
    {
      "inletPressure": 40.0,
      "outletPressure": 90.0,
      "flowRate": 45000.0,
      "totalPower": 3200.0,
      "feasible": true,
      "compressors": {
        "Export Compressor": {
          "power": 3200.0,
          "speed": 9500.0,
          "surgeMargin": 0.18
        }
      }
    }
  ]
}
```

---

## Best Practices

### 1. Always Configure Compressor Charts

```java
// Before optimization
optimizer.configureProcessCompressorCharts();
```

### 2. Use Appropriate Surge Margins

| Application | Recommended Surge Margin |
|-------------|-------------------------|
| Steady-state operations | 10-15% |
| Transient operations | 15-20% |
| Start-up/shutdown | 20-25% |

### 3. Validate Before Running

```java
List<String> issues = optimizer.validateConfiguration();
if (!issues.isEmpty()) {
    throw new IllegalStateException("Invalid configuration: " + issues);
}
```

### 4. Handle Infeasible Points

```java
ProcessOperatingPoint point = optimizer.findMaxFlowRateAtPressureBoundaries(...);
if (point == null || !point.isFeasible()) {
    System.out.println("No feasible operating point found");
    // Consider relaxing constraints or checking equipment sizing
}
```

---

## Troubleshooting

### No Feasible Points Found

1. Check compressor charts are configured
2. Verify pressure ranges are achievable
3. Check power/speed limits aren't too restrictive
4. Try relaxing surge margin temporarily

### Slow Performance

1. Reduce pressure grid resolution
2. Increase convergence tolerance
3. Use fewer iterations for initial exploration

### Eclipse Export Issues

1. Verify flow rates are in expected units
2. Check pressure monotonicity
3. Ensure at least 2 feasible points per curve

---

## Python Usage (via JPype)

All FlowRateOptimizer functionality is accessible from Python using neqsim-python and JPype.

### Basic Setup

```python
from neqsim.neqsimpython import jneqsim
import numpy as np

# Import classes
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
Compressor = jneqsim.process.equipment.compressor.Compressor
Cooler = jneqsim.process.equipment.heatexchanger.Cooler
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

FlowRateOptimizer = jneqsim.process.util.optimizer.FlowRateOptimizer
```

### Creating a Process and Optimizer

```python
# Create gas composition
gas = SystemSrkEos(288.15, 50.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.setMixingRule("classic")

# Build process
feed = Stream("Feed", gas)
feed.setFlowRate(50000, "kg/hr")
feed.setPressure(50.0, "bara")

compressor = Compressor("Export Compressor", feed)
compressor.setOutletPressure(100.0)

afterCooler = Cooler("Aftercooler", compressor.getOutletStream())
afterCooler.setOutTemperature(313.15)  # 40°C

process = ProcessSystem()
process.add(feed)
process.add(compressor)
process.add(afterCooler)
process.run()

# Create optimizer
optimizer = FlowRateOptimizer(process, "Feed", "Export Compressor")
optimizer.setMinSurgeMargin(0.15)  # 15% surge margin
optimizer.setMaxPowerLimit(5000.0)  # 5 MW max
optimizer.configureProcessCompressorCharts()
```

### Finding Maximum Flow Rate

```python
# Find max flow at pressure boundaries
result = optimizer.findMaxFlowRateAtPressureBoundaries(
    50.0,    # inlet pressure (bara)
    100.0,   # outlet pressure (bara)
    "bara",  # pressure unit
    0.95     # max utilization
)

if result is not None and result.isFeasible():
    print(f"Max flow rate: {result.getFlowRate():.0f} kg/hr")
    print(f"Total power: {result.getTotalPower():.1f} kW")
    print(f"Feasible: {result.isFeasible()}")
else:
    print("No feasible operating point found")
```

### Generating Lift Curve Tables

```python
import numpy as np

# Define pressure grids
inlet_pressures = [40.0, 50.0, 60.0, 70.0, 80.0]     # bara
outlet_pressures = [90.0, 100.0, 110.0, 120.0, 130.0]  # bara

# Convert to Java arrays (required for JPype)
from jpype import JArray, JDouble
java_inlet = JArray(JDouble)(inlet_pressures)
java_outlet = JArray(JDouble)(outlet_pressures)

# Generate lift curve table
table = optimizer.generateProcessCapacityTable(
    java_inlet,
    java_outlet,
    "bara",
    0.95  # max utilization
)

# Export to Eclipse format
eclipse_vfp = table.toEclipseFormat()
print(eclipse_vfp)

# Export to JSON
json_output = table.toJson()

# Access individual operating points
point = table.getOperatingPoint(1, 2)  # Pin=50, Pout=110
print(f"Flow at Pin=50, Pout=110: {point.getFlowRate():.0f} kg/hr")
```

### Parallel Lift Curve Generation

```python
# Enable parallel evaluation for large tables
optimizer.setEnableParallelEvaluation(True)
optimizer.setParallelThreads(4)  # Use 4 threads

# Generate table in parallel
table = optimizer.generateProcessCapacityTable(
    java_inlet,
    java_outlet,
    "bara",
    0.95
)

print(f"Feasible points: {table.getFeasibleCount()}")
```

### Professional Lift Curves with Configuration

```python
# Create configuration object
LiftCurveConfiguration = FlowRateOptimizer.LiftCurveConfiguration

config = LiftCurveConfiguration() \
    .setTableName("Export_System_VFP") \
    .setTableNumber(1) \
    .setInletPressures(java_inlet) \
    .setOutletPressures(java_outlet) \
    .setPressureUnit("bara") \
    .setFlowUnit("kg/hr") \
    .setMaxUtilization(0.95) \
    .setSurgeMargin(0.15) \
    .setMaxPowerLimit(5000.0) \
    .setIncludePowerData(True) \
    .setIncludeCompressorDetails(True)

# Generate professional lift curves
result = optimizer.generateProfessionalLiftCurves(config)

# Get Eclipse format
print(result.getCapacityTable().toEclipseFormat())

# Check warnings
for warning in result.getWarnings():
    print(f"Warning: {warning}")
```

### Processing Results in Python

```python
import json

# Parse JSON results for pandas/numpy analysis
json_str = table.toJson()
data = json.loads(json_str)

# Extract flow rates into numpy array
import numpy as np
flow_matrix = np.zeros((len(inlet_pressures), len(outlet_pressures)))

for i, pin in enumerate(inlet_pressures):
    for j, pout in enumerate(outlet_pressures):
        point = table.getOperatingPoint(i, j)
        if point is not None and point.isFeasible():
            flow_matrix[i, j] = point.getFlowRate()
        else:
            flow_matrix[i, j] = np.nan

print("Flow rate matrix (kg/hr):")
print(flow_matrix)
```

### Plotting Lift Curves (matplotlib)

```python
import matplotlib.pyplot as plt
import numpy as np

# Collect data for plotting
fig, ax = plt.subplots(figsize=(10, 6))

for i, pin in enumerate(inlet_pressures):
    flows = []
    pressures = []
    for j, pout in enumerate(outlet_pressures):
        point = table.getOperatingPoint(i, j)
        if point is not None and point.isFeasible():
            flows.append(point.getFlowRate())
            pressures.append(pout)
    
    if flows:
        ax.plot(flows, pressures, 'o-', label=f'Pin={pin} bara')

ax.set_xlabel('Flow Rate (kg/hr)')
ax.set_ylabel('Outlet Pressure (bara)')
ax.set_title('Lift Curves - Export Compression System')
ax.legend()
ax.grid(True)
plt.savefig('lift_curves.png', dpi=150)
plt.show()
```

---

## Related Documentation

- [OPTIMIZER_PLUGIN_ARCHITECTURE.md](OPTIMIZER_PLUGIN_ARCHITECTURE.md) - Equipment capacity strategies
- [EXTERNAL_OPTIMIZER_INTEGRATION.md](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) - Python/SciPy integration
- [pressure_boundary_optimization.md](../pressure_boundary_optimization.md) - Simplified optimizer wrapper
- [PRODUCTION_OPTIMIZATION_GUIDE.md](../../examples/PRODUCTION_OPTIMIZATION_GUIDE.md) - Complete examples

---

## API Reference

### FlowRateOptimizer

| Method | Description |
|--------|-------------|
| `findMaxFlowRateAtPressureBoundaries()` | Find max flow for pressure boundaries |
| `generateProcessCapacityTable()` | Generate 2D lift curve table |
| `generateProcessPerformanceTable()` | Generate 1D performance table |
| `generateProfessionalLiftCurves()` | Generate production-quality lift curves |
| `configureProcessCompressorCharts()` | Auto-configure compressor charts |
| `validateConfiguration()` | Validate optimizer setup |
| `findProcessOperatingPoint()` | Find operating point at specific flow |

### ProcessCapacityTable

| Method | Description |
|--------|-------------|
| `toEclipseFormat()` | Export to Eclipse VFPPROD format |
| `toJson()` | Export to JSON |
| `getOperatingPoint(i, j)` | Get point at grid indices |
| `getFeasibleCount()` | Count of feasible points |

### ProcessOperatingPoint

| Method | Description |
|--------|-------------|
| `getFlowRate()` | Flow rate value |
| `getTotalPower()` | Total compressor power |
| `isFeasible()` | Feasibility status |
| `getCompressorOperatingPoint()` | Detailed compressor data |
| `toJson()` | Export to JSON |

