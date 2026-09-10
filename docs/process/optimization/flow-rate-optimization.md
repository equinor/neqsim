---
title: Flow Rate Optimization
description: This guide covers the `FlowRateOptimizer` class for calculating optimal flow rates given pressure boundary conditions and generating lift curve tables for Eclipse reservoir simulation.
---

# Flow Rate Optimization

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

This guide covers the `FlowRateOptimizer` class for calculating optimal flow rates given pressure boundary conditions and generating lift curve tables for Eclipse reservoir simulation.

## Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | When to use which optimizer |
| [Constraint Framework](constraint-framework) | Unified constraint system for all optimizers |
| [Optimizer Plugin Architecture](OPTIMIZER_PLUGIN_ARCHITECTURE) | ProcessOptimizationEngine and VFP export |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | ProductionOptimizer examples |

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
| **Reservoir coupling** | Requires VFP axis, datum, and unit conversion with EclipseVFPExporter |
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

Java code blocks are method bodies using the imports from the first example;
place statements in `main(String[] args) throws Exception`. Later sections reuse
that process and optimizer, with repeated local names in separate scopes. Python
blocks run in order in a session with `neqsim`, `numpy`, and `matplotlib` installed.


### Basic Flow Rate Calculation

Java output uses Log4j2. Declare this field inside your example class:
`private static final org.apache.logging.log4j.Logger logger = org.apache.logging.log4j.LogManager.getLogger("OptimizationExample");`.

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
optimizer.setMinFlowRate(25000.0); // kg/hr: bracket the known design point
optimizer.setMaxFlowRate(100000.0);
optimizer.setMinSurgeMargin(0.15);  // 15% surge margin
optimizer.setMaxPowerLimit(5000.0); // 5 MW max
optimizer.configureProcessCompressorCharts();

// Find max flow rate
FlowRateOptimizer.ProcessOperatingPoint result =
    optimizer.findMaxFlowRateAtPressureBoundaries(50.0, 100.0, "bara", 0.95);

if (result != null && result.isFeasible()) {
    logger.info("Max flow rate: " + result.getFlowRate() + " kg/hr");
    logger.info("Total power: " + result.getTotalPower() + " kW");
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

// Inspect the capacity report (not a reservoir VFP deck)
String capacityReport = table.toFormattedString();
logger.info(capacityReport);
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
if (point != null && point.isFeasible()) {
    logger.info("Flow at Pin=50, Pout=110: " + point.getFlowRate() + " kg/hr");
}
```

### Capacity Tables and Eclipse VFP Tables

A capacity table gives maximum **mass flow** for imposed inlet/outlet pressures.
It is not a reservoir VFP table. The legacy `toEclipseFormat()` helper does not
convert mass rates to standard phase-volume rates and does not establish the
well datum or the required THP/BHP convention; do not insert its output directly
into an Eclipse deck.

Use `toJson()`, `toCsv()`, or `toFormattedString()` for capacity screening. For
reservoir coupling, generate physically defined BHP values on the prescribed
flow/THP/water-fraction/gas-fraction/artificial-lift axes and pass them to
`EclipseVFPExporter`. Set its unit system and flow-rate type explicitly; its
setters describe already-converted data, they do not convert kg/hr to Sm3/day.
See [VFP export configuration](OPTIMIZER_PLUGIN_ARCHITECTURE) for the exporter
API and [pressure boundary optimization](../pressure_boundary_optimization)
for solving the process boundary problem.

---

## Professional Lift Curve Generation

Use `LiftCurveConfiguration` to collect capacity, pressure-flow, and performance
tables with a common configuration. These are model screening results; replace
auto-generated compressor charts with validated equipment data for plant studies:

```java
// Configure lift curve generation
FlowRateOptimizer.LiftCurveConfiguration config =
    new FlowRateOptimizer.LiftCurveConfiguration()
        .withInletPressureRange(40, 80, 5)    // min, max, number of points
        .withOutletPressureRange(90, 120, 4)  // min, max, number of points
        .withPressureUnit("bara")
        .withFlowRateUnit("kg/hr")
        .withMaxUtilization(0.95)
        .withSurgeMargin(0.15)
        .withMaxPowerLimit(5000.0)
        .withTables(true, true, true);        // capacity, lift curve, performance

// Generate professional lift curves
FlowRateOptimizer.LiftCurveResult result =
    optimizer.generateProfessionalLiftCurves(config);

// Get the capacity report
logger.info(result.getCapacityTable().toFormattedString());

// Check for warnings
for (String warning : result.getWarnings()) {
    logger.info("Warning: " + warning);
}

// Get statistics
logger.info("Total evaluations: " + result.getTotalEvaluations());
logger.info("Feasible points: " + result.getFeasiblePoints());
logger.info("Generation time: " + result.getGenerationTimeMs() + " ms");
```

---

## Constraint Configuration

### Compressor Constraints

```java
// Set surge margin
optimizer.setMinSurgeMargin(0.15);      // 15% minimum surge margin

// Set power limits
optimizer.setMaxPowerLimit(5000.0);        // Per compressor limit (kW)
optimizer.setMaxTotalPowerLimit(15000.0);  // Total system power limit (kW)

// Set speed limits (absolute RPM)
optimizer.setMinSpeedLimit(7000.0);     // Minimum speed (RPM)
optimizer.setMaxSpeedLimit(10500.0);    // Maximum speed (RPM)

// Configure compressor charts automatically
optimizer.configureProcessCompressorCharts();
```

### Equipment Utilization Limits

```java
// Set overall max equipment utilization (applies to all equipment)
optimizer.setMaxEquipmentUtilizationLimit(0.95);  // 95% max for all equipment
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
logger.info(perfTable.toFormattedString());

// Get data programmatically
for (int i = 0; i < flowRates.length; i++) {
    FlowRateOptimizer.ProcessOperatingPoint pt = perfTable.getOperatingPoint(i);
    logger.info(String.format("Flow: %.0f kg/hr, Power: %.0f kW, Feasible: %b%n",
        pt.getFlowRate(), pt.getTotalPower(), pt.isFeasible()));
}
```

---

## Compressor Operating Point Data

Each `ProcessOperatingPoint` includes detailed compressor data:

```java
FlowRateOptimizer.ProcessOperatingPoint point =
    optimizer.findMaxFlowRateAtPressureBoundaries(50.0, 100.0, "bara", 0.95);

// Get compressor details
if (point == null) {
    throw new IllegalStateException("No feasible compressor point at these boundaries");
}
for (String compName : point.getCompressorNames()) {
    FlowRateOptimizer.CompressorOperatingPoint cop =
        point.getCompressorOperatingPoint(compName);

    logger.info("Compressor: " + compName);
    logger.info("  Power: " + cop.getPower() + " kW");
    logger.info("  Speed: " + cop.getSpeed() + " RPM");
    logger.info("  Flow: " + cop.getFlowRate() + " " + cop.getFlowRateUnit());
    logger.info("  Head: " + cop.getPolytropicHead() + " kJ/kg");
    logger.info("  Surge margin: " + cop.getSurgeMargin() * 100 + "%");
    logger.info("  At stonewall: " + cop.isAtStoneWall());
}
```

---

## Operating Modes

The `FlowRateOptimizer` supports two operating modes:

### 1. PROCESS_SYSTEM Mode (Default)

For `ProcessSystem` objects with compressors:

```java
FlowRateOptimizer systemOptimizer =
    new FlowRateOptimizer(process, "Feed", "Export Compressor");
```

### 2. PROCESS_MODEL Mode

For `ProcessModel` objects:

```java
neqsim.process.processmodel.ProcessModel processModel =
    new neqsim.process.processmodel.ProcessModel();
processModel.add("export", process);
FlowRateOptimizer modelOptimizer =
    new FlowRateOptimizer(processModel, "Feed", "Export Compressor");
```

### Pipe-Only Pressure-Drop Calculations

For pressure-drop calculations, construct a pipe process. There is no no-argument
constructor, `Mode.SIMPLE`, or `setMode()` method.

```java
// A pipe-only calculation still uses PROCESS_SYSTEM mode.
Stream pipeFeed = new Stream("Pipe Feed", gas.clone());
pipeFeed.setFlowRate(10000.0, "kg/hr");
neqsim.process.equipment.pipeline.PipeBeggsAndBrills pipe =
    new neqsim.process.equipment.pipeline.PipeBeggsAndBrills("Pipeline", pipeFeed);
pipe.setLength(10000.0);
pipe.setDiameter(0.20);
ProcessSystem pipeSystem = new ProcessSystem();
pipeSystem.add(pipeFeed);
pipeSystem.add(pipe);
pipeSystem.run();
FlowRateOptimizer pipeOptimizer = new FlowRateOptimizer(pipeSystem, "Pipe Feed", "Pipeline");
pipeOptimizer.setMinFlowRate(1000.0);
pipeOptimizer.setMaxFlowRate(100000.0);
neqsim.process.util.optimizer.FlowRateOptimizationResult pipeResult =
    pipeOptimizer.findFlowRate(50.0, 45.0, "bara");
```

---

## Validation

Validate optimizer configuration before running:

```java
List<String> issues = optimizer.validateConfiguration();
if (!issues.isEmpty()) {
    logger.info("Configuration issues:");
    for (String issue : issues) {
        logger.info("  - " + issue);
    }
} else {
    logger.info("Configuration valid, ready to optimize");
}
```

---

## JSON Export

Export results in JSON format for integration with external tools:

```java
// Table JSON is available directly.
String tableJson = table.toJson();
// Individual operating points and LiftCurveResult have no toJson() method.
// Gson serializes their data fields; permit undefined diagnostic values explicitly.
com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
    .serializeSpecialFloatingPointValues().create();
String pointJson = gson.toJson(point);
String resultJson = gson.toJson(result);
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

### 1. Configure Compressor Charts

```java
// Before optimization
optimizer.configureProcessCompressorCharts();
```

Auto-generated curves approximate the design operating point. They are educational
fixtures, not vendor surge/stonewall evidence.

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
FlowRateOptimizer.ProcessOperatingPoint candidate =
    optimizer.findMaxFlowRateAtPressureBoundaries(50.0, 100.0, "bara", 0.95);
if (candidate == null || !candidate.isFeasible()) {
    logger.info("No feasible operating point found; inspect constraint diagnostics");
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
optimizer.setMinFlowRate(25000.0)  # kg/hr: bracket the known design point
optimizer.setMaxFlowRate(100000.0)
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

# Inspect the capacity report
capacity_report = table.toFormattedString()
print(capacity_report)

# Export to JSON
json_output = table.toJson()

# Access individual operating points
point = table.getOperatingPoint(1, 2)  # Pin=50, Pout=110
if point is not None and point.isFeasible():
    print(f"Flow at Pin=50, Pout=110: {point.getFlowRate():.0f} kg/hr")
else:
    print("This pressure pair has no feasible operating point")
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

print(f"Feasible points: {table.countFeasiblePoints()}")
```

### Professional Lift Curves with Configuration

```python
# Create configuration object
LiftCurveConfiguration = FlowRateOptimizer.LiftCurveConfiguration

config = LiftCurveConfiguration() \
    .withInletPressureRange(40, 80, 5) \
    .withOutletPressureRange(90, 120, 4) \
    .withPressureUnit("bara") \
    .withFlowRateUnit("kg/hr") \
    .withMaxUtilization(0.95) \
    .withSurgeMargin(0.15) \
    .withMaxPowerLimit(5000.0) \
    .withTables(True, True, True)

# Generate professional lift curves
result = optimizer.generateProfessionalLiftCurves(config)

# Get the capacity report
print(result.getCapacityTable().toFormattedString())

# Check warnings
for warning in result.getWarnings():
    print(f"Warning: {warning}")
```

### Processing Results in Python

```python
import json

# Parse JSON results for pandas/numpy analysis
json_str = table.toJson()
data = json.loads(str(json_str))

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

- [OPTIMIZER_PLUGIN_ARCHITECTURE.md](OPTIMIZER_PLUGIN_ARCHITECTURE) - Equipment capacity strategies
- [EXTERNAL_OPTIMIZER_INTEGRATION.md](../../integration/EXTERNAL_OPTIMIZER_INTEGRATION) - Python/SciPy integration
- [pressure_boundary_optimization.md](../pressure_boundary_optimization) - Simplified optimizer wrapper
- [PRODUCTION_OPTIMIZATION_GUIDE.md](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) - Complete examples

---

## API Reference

### FlowRateOptimizer

| Method | Description |
|--------|-------------|
| `findMaxFlowRateAtPressureBoundaries()` | Find max flow for pressure boundaries |
| `generateProcessCapacityTable()` | Generate 2D lift curve table |
| `generateProcessPerformanceTable()` | Generate 1D performance table |
| `generateProfessionalLiftCurves()` | Generate configured screening tables |
| `configureProcessCompressorCharts()` | Auto-configure compressor charts |
| `validateConfiguration()` | Validate optimizer setup |
| `findProcessOperatingPoint()` | Find operating point at specific flow |

### ProcessCapacityTable

| Method | Description |
|--------|-------------|
| `toEclipseFormat()` | Legacy diagnostic; requires physical/unit conversion before reservoir use |
| `toJson()` | Export to JSON |
| `getOperatingPoint(i, j)` | Get point at grid indices |
| `countFeasiblePoints()` | Count of feasible points |

### ProcessOperatingPoint

| Method | Description |
|--------|-------------|
| `getFlowRate()` | Flow rate value |
| `getTotalPower()` | Total compressor power |
| `isFeasible()` | Feasibility status |
| `getCompressorOperatingPoint()` | Detailed compressor data |
| `toJson()` | Export table data to JSON |

