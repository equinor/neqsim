---
title: Pressure Boundary Optimization
description: This guide explains how to use the `PressureBoundaryOptimizer` class to calculate flow rates for given inlet and outlet pressure boundaries, generate lift curve tables for Eclipse reservoir simulation...
---

# Pressure Boundary Optimization

This guide explains how to use the `PressureBoundaryOptimizer` class to calculate flow rates for given inlet and outlet pressure boundaries, generate lift curve tables for Eclipse reservoir simulation, and optimize process operations.

## Overview

The `PressureBoundaryOptimizer` is a simplified wrapper around NeqSim's `ProductionOptimizer` framework, specifically designed for:

1. **Flow Rate Calculation** - Finding maximum feasible flow rate between pressure boundaries
2. **Lift Curve Generation** - Creating 2D performance tables (inlet pressure vs outlet pressure)
3. **Capacity Curves** - Generating 1D curves at fixed inlet pressure
4. **Power Optimization** - Finding operating points that minimize compressor power

## Quick Start

### Basic Flow Rate Calculation

```java
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.PressureBoundaryOptimizer;
import neqsim.process.util.optimizer.PressureBoundaryOptimizer.LiftCurveTable;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.heatexchanger.Cooler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.util.optimizer.ProductionOptimizer.OptimizationResult;
import neqsim.thermo.system.SystemSrkEos;

Logger logger = LogManager.getLogger("PressureBoundaryExample");

// Create a simple process
SystemSrkEos fluid = new SystemSrkEos(288.15, 50.0);
fluid.addComponent("methane", 0.9);
fluid.addComponent("ethane", 0.07);
fluid.addComponent("propane", 0.03);
fluid.setMixingRule("classic");

Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
feed.setTemperature(15.0, "C");
feed.setPressure(50.0, "bara");

ThrottlingValve valve = new ThrottlingValve("valve", feed);
valve.setOutletPressure(30.0, "bara");
// Illustrative installed throughput rating; pressure drop alone does not limit a fixed-pressure valve.
valve.addCapacityConstraint(new CapacityConstraint("installedMassFlow", "kg/hr",
    CapacityConstraint.ConstraintType.HARD)
    .setDesignValue(400.0)
    .setValueSupplier(() -> feed.getFlowRate("kg/hr")));

Stream outlet = new Stream("outlet", valve.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(outlet);
process.run();

// Create optimizer (feed and outlet streams are passed to the constructor)
PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process, feed, outlet);
optimizer.setRateUnit("kg/hr");
optimizer.setMaxFlowRate(500.0);

optimizer.setMinFlowRate(10.0);

// Find maximum flow rate
OptimizationResult result = optimizer.findMaxFlowRate(
    50.0,   // inlet pressure
    30.0,   // outlet pressure
    "bara"  // pressure unit
);
if (!result.isFeasible()) {
    throw new IllegalStateException("No feasible operating point");
}
double maxFlow = result.getOptimalRate();

logger.info("Maximum flow rate: " + maxFlow + " kg/hr");
```

Place the quick-start imports at the top of a Java source file and its statements in a
`main` or test method. Later examples require the solved process and named streams from
the indicated setup. `CapacityOptimizationDocumentationTest` exercises these APIs, including
feasible and infeasible pressure columns and the minimum-power objective.

The search restores the original feed pressure before returning. To use the chosen point
as the live process state, explicitly set the accepted inlet pressure and rate and run the
process again.

## Key Features

### 1. Finding Maximum Flow Rate

The `findMaxFlowRate()` method uses binary search to find the maximum flow rate that produces a feasible process state. It returns an `OptimizationResult`; read the rate with `getOptimalRate()`:

```java
OptimizationResult result = optimizer.findMaxFlowRate(
    inletPressure,    // pressure at inlet
    outletPressure,   // pressure at outlet
    "bara"            // pressure unit
);
if (!result.isFeasible()) {
    throw new IllegalStateException("No feasible operating point");
}
double maxFlow = result.getOptimalRate();
```

Feasibility is relative to the configured constraints and available equipment ratings.
The outlet pressure is checked against the target; the optimizer does **not** change valve
or compressor outlet setpoints to match that target. Set those before each search when the
model uses prescribed outlet pressures. A passive hydraulic model must instead calculate its
outlet pressure from its own pressure-loss relation.

The snippets below reuse the quick-start variables inside separate method scopes;
`inletPressure = 50.0` and `outletPressure = 30.0` are in bara. The quick start finds
approximately 400 kg/hr, within the configured search tolerance. This limit comes from the
explicit illustrative rating, not from a calculated valve Cv.

### 2. Generating Lift Curve Tables

Lift curve tables map inlet/outlet pressure combinations to maximum flow rates. These are essential for coupling surface network models with reservoir simulators:

```java
// Define pressure ranges
double[] inletPressures = {40.0, 50.0, 60.0};
double[] outletPressures = {30.0, 35.0};
// The fixed 30 bara valve makes the 35 bara column infeasible, as intended.

// Generate table
LiftCurveTable table = optimizer.generateLiftCurveTable(
    inletPressures,
    outletPressures,
    "bara"
);

// Export a commented text matrix (not a VFPPROD keyword)
logger.info(table.toEclipseFormat());

// Export to JSON for other applications
logger.info(table.toJson());
```

### 3. Capacity Curves

Generate a 1D curve showing flow capacity vs outlet pressure at a fixed inlet pressure:

```java
double[] outletPressures = {30.0, 35.0};

// Returns an array of max flow rates, one per outlet pressure (same order)
double[] curve = optimizer.generateCapacityCurve(
    60.0,             // fixed inlet pressure
    outletPressures,  // outlet pressures to evaluate
    "bara"
);

for (int i = 0; i < outletPressures.length; i++) {
    logger.info("P_out=" + outletPressures[i] + " bara -> " + curve[i] + " kg/hr");
}
```

### 4. Minimum Power Optimization

This searches **feed rate** between the target minimum and `maxFlowRate`; it does not
optimize staging, pressure ratios, or equipment selection. For this example use the gas
compression system below, with its compressor already set to 100 bara, and construct
`optimizer` for that system. Use `setAutoConfigureCompressors(false)` for the fixed-efficiency
example. A generated chart is a synthetic screening map, not vendor capacity evidence.

```java
OptimizationResult result = optimizer.findMinimumPowerOperatingPoint(
    50.0,    // inlet pressure
    100.0,   // target outlet pressure
    "bara",  // pressure unit
    250.0    // target flow rate
);

logger.info("Minimum power: " + result.getObjectiveValues().get("totalPower") + " kW");
logger.info("Achieved flow: " + result.getOptimalRate());
logger.info("Feasible: {}", result.isFeasible());
```

## Configuration Options

Inlet and outlet streams are supplied to the constructor; the remaining parameters are configured with setters:

| Parameter            | Method                   | Description                              | Default   |
| -------------------- | ------------------------ | ---------------------------------------- | --------- |
| Rate Unit            | `setRateUnit()`          | Unit for flow rate results               | "kg/hr"   |
| Max Flow             | `setMaxFlowRate()`       | Upper bound for flow rate search         | 1e9       |
| Min Flow             | `setMinFlowRate()`       | Lower bound for flow rate search         | 0.001     |
| Flow Tolerance       | `setTolerance()`         | Fraction of configured search interval      | 0.001     |
| Pressure Tolerance   | `setPressureTolerance()` | Outlet pressure feasibility tolerance    | 0.02      |
| Max Utilization      | `setMaxUtilization()`    | Default equipment utilization limit      | 1.0       |
| Minimum Surge Margin | `setMinSurgeMargin()`    | Required distance from compressor surge  | 0.1 (10%) |
| Max Power Limit      | `setMaxPowerLimit()`     | Maximum power of each compressor (kW)            | unlimited |
| Auto Charts          | `setAutoConfigureCompressors()` | Generate synthetic maps if absent | true |
| Max Iterations       | `setMaxIterations()` | Search iteration budget | 50 |
| Speed Limits         | `setSpeedLimits()`       | Min/max compressor speed (RPM)           | unbounded |

## Process Types

The optimizer works with any `ProcessSystem` that has definable inlet/outlet streams:

### Simple Pipeline with Valve

```java
Stream feed = new Stream("feed", fluid);
ThrottlingValve valve = new ThrottlingValve("valve", feed);
valve.setOutletPressure(targetPressure, "bara");
Stream outlet = new Stream("outlet", valve.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(outlet);
```

### Gas Compression System

```java
Stream feed = new Stream("feed", fluid);
Compressor compressor = new Compressor("compressor", feed);
compressor.setPolytropicEfficiency(0.75);
compressor.setUsePolytropicCalc(true);
compressor.setOutletPressure(100.0, "bara");
Cooler aftercooler = new Cooler("cooler", compressor.getOutletStream());
aftercooler.setOutTemperature(40.0, "C");
Stream outlet = new Stream("outlet", aftercooler.getOutletStream());

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(compressor);
process.add(aftercooler);
process.add(outlet);
```

### Multi-Stage Compression Train

```java
// First stage
Compressor comp1 = new Compressor("comp1", feed);
comp1.setOutletPressure(45.0, "bara");
Cooler cooler1 = new Cooler("cooler1", comp1.getOutletStream());
cooler1.setOutTemperature(40.0, "C");

// Second stage
Compressor comp2 = new Compressor("comp2", cooler1.getOutletStream());
comp2.setOutletPressure(90.0, "bara");
Cooler cooler2 = new Cooler("cooler2", comp2.getOutletStream());
cooler2.setOutTemperature(40.0, "C");

// Register and solve the complete train before constructing its optimizer.
Stream trainOutlet = new Stream("train outlet", cooler2.getOutletStream());
ProcessSystem train = new ProcessSystem();
train.add(feed);
train.add(comp1);
train.add(cooler1);
train.add(comp2);
train.add(cooler2);
train.add(trainOutlet);
train.run();
PressureBoundaryOptimizer trainOptimizer =
    new PressureBoundaryOptimizer(train, feed, trainOutlet);
double totalPower = trainOptimizer.calculateTotalPower();
```

## Eclipse VFP Table Integration

`LiftCurveTable.toEclipseFormat()` writes a commented capacity matrix. It does **not**
produce a complete Eclipse `VFPPROD` keyword: its dependent variable is maximum rate,
whereas a production VFP table needs bottom-hole pressures on flow/THP/water/GOR/lift axes.
Use `EclipseVFPExporter` only after calculating and validating that separate pressure table.

```java
LiftCurveTable table = optimizer.generateLiftCurveTable(
    new double[] {40, 50, 60}, // Inlet pressures
    new double[] {30, 35},     // Fixed 30 bara model: second column is infeasible
    "bara"
);

// Get the commented capacity matrix
String eclipseTable = table.toEclipseFormat();
```

Illustrative output format (numbers below are not results of the quick start):
```
-- Lift Curve Table
-- Generated by NeqSim PressureBoundaryOptimizer
-- Rows: Inlet Pressure [bara]
-- Columns: Outlet Pressure [bara]
-- Values: Flow Rate [kg/hr]

-- Infeasible points marked with 1*

-- Outlet Pressures: 80.00 90.00 100.00 110.00 120.00

-- Pin=30.00
  450.50 380.25 310.00 240.75 1*

-- Pin=40.00
  520.30 450.80 380.40 310.20 245.00

-- ... etc
```

## Understanding Feasibility

A process state is considered **feasible** when:

1. **Pressure constraint**: Absolute outlet error is at most `targetPressure * pressureTolerance`
   in the supplied unit. Use absolute pressure units such as bara.
2. **Equipment limits**: Enabled capacity constraints and configured utilization limits are checked.
   Disabled or absent ratings do not establish installed capacity.
3. **Compressor envelope**: Enabled map constraints apply when a chart is active. Chartless
   compressors have no surge/stonewall evidence. Soft limits may be reported with penalties.
4. **Simulation validity**: Inspect the returned feasibility and constraint statuses. A feasible
   optimization result is not a separate certificate of complete plant convergence or balances.

When generating lift curve tables, infeasible points are marked with `Double.NaN` internally and `1*` in Eclipse format output.

## Power Tracking

The optimizer tracks compressor power consumption:

```java
// Get total power after running
double totalPower = optimizer.calculateTotalPower();

// LiftCurveTable includes power at each operating point
LiftCurveTable table = optimizer.generateLiftCurveTable(
    new double[] {50.0}, new double[] {30.0}, "bara");
double powerAtPoint = table.getPower(0, 0);
```

## JSON Output

The nested `PressureBoundaryOptimizer.LiftCurveTable` provides JSON export for integration
with external tools. Infeasible or non-finite numeric entries are `null`; names are JSON-escaped.
Do not confuse this capacity matrix with the top-level `LiftCurveTable` BHP matrix.

```java
String json = table.toJson();
```

Illustrative schema (numbers below are not results of the quick start):
```json
{
  "tableName": "LiftCurve",
  "inletPressures": [30.0, 40.0, 50.0],
  "outletPressures": [80.0, 90.0, 100.0],
  "pressureUnit": "bara",
  "rateUnit": "kg/hr",
  "flowRates": [
    [450.5, 380.2, 310.0],
    [520.3, 450.8, 380.4],
    [580.1, 520.5, 450.2]
  ],
  "powers": [
    [1200.5, 1450.2, 1700.0],
    [1100.3, 1350.8, 1600.4],
    [1050.1, 1280.5, 1520.2]
  ],
  "feasiblePoints": 9
}
```

## Best Practices

### 1. Set Realistic Bounds

```java
// Always set bounds based on process capabilities
optimizer.setMinFlowRate(10.0);    // Minimum stable flow
optimizer.setMaxFlowRate(1000.0);  // Equipment limits
```

### 2. Configure Tolerances Appropriately

```java
// Tighter tolerances = more accurate but slower
optimizer.setTolerance(0.01);         // 1% of (maxFlowRate - minFlowRate)
optimizer.setPressureTolerance(0.02); // relative outlet-pressure tolerance
```

### 3. Check Feasibility Before Using Results

```java
OptimizationResult result = optimizer.findMaxFlowRate(50.0, 30.0, "bara");
if (!result.isFeasible() || result.getOptimalRate() <= 0) {
    logger.info("No feasible flow rate found");
}
```

### 4. Use Appropriate Grid Resolution

```java
// Coarse grid for initial exploration
double[] pressuresCoarse = {30, 50, 70, 90};

// Fine grid for production tables
double[] pressuresFine = new double[21];
for (int i = 0; i < 21; i++) {
    pressuresFine[i] = 30 + i * 3.0;  // 30 to 90 in 3 bar steps
}
```

## Thread Safety

The `PressureBoundaryOptimizer` is **NOT** thread-safe. The underlying `ProcessSystem` maintains state during simulation runs. For parallel table generation, create separate `ProcessSystem` instances.

## Related Classes

- [Compressors](equipment/compressors) - Centrifugal compressor documentation
- [Pipelines](equipment/pipelines) - Pipeline documentation
- [Process Equipment](equipment/index.md) - Process equipment overview

## Example: Complete Workflow

The following continues the quick start and writes the two exports with Java 8 APIs.
Run it in a method declaring `throws java.io.IOException`:

```java
// The valve outlet is fixed at 30 bara; show the feasible pressure column explicitly.
optimizer.setRateUnit("kg/hr");
optimizer.setMinFlowRate(10.0);
optimizer.setMaxFlowRate(500.0);
double[] inletP = {40.0, 50.0, 60.0};
double[] outletP = {30.0};
LiftCurveTable table = optimizer.generateLiftCurveTable(inletP, outletP, "bara");
logger.info("Feasible points: {}/{}", table.countFeasiblePoints(),
    inletP.length * outletP.length);

java.nio.file.Files.write(java.nio.file.Paths.get("capacity_matrix.txt"),
    table.toEclipseFormat().getBytes(java.nio.charset.StandardCharsets.UTF_8));
java.nio.file.Files.write(java.nio.file.Paths.get("lift_curve.json"),
    table.toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8));
```

## Troubleshooting

| Issue                 | Possible Cause             | Solution                               |
| --------------------- | -------------------------- | -------------------------------------- |
| All points infeasible | Pressure range too extreme | Reduce outlet pressure range           |
| Very slow generation  | Grid too fine              | Use coarser grid or parallel execution |
| NaN flow rates        | Process doesn't converge   | Check fluid composition and EOS        |
| Zero power | No compressors in process | Expected for a valve-only process |

