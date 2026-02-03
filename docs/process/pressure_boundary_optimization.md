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
import neqsim.thermo.system.SystemSrkEos;

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

Stream outlet = new Stream("outlet", valve);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(valve);
process.add(outlet);
process.run();

// Create optimizer
PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process);
optimizer.setInletStream(feed);
optimizer.setOutletStream(outlet);
optimizer.setFlowUnit("kg/hr");
optimizer.setMaxFlow(500.0);

// Find maximum flow rate
double maxFlow = optimizer.findMaxFlowRate(
    50.0,   // inlet pressure
    30.0,   // outlet pressure  
    "bara"  // pressure unit
);

System.out.println("Maximum flow rate: " + maxFlow + " kg/hr");
```

## Key Features

### 1. Finding Maximum Flow Rate

The `findMaxFlowRate()` method uses binary search to find the maximum flow rate that produces a feasible process state:

```java
double maxFlow = optimizer.findMaxFlowRate(
    inletPressure,    // pressure at inlet
    outletPressure,   // pressure at outlet
    "bara"            // pressure unit
);
```

A process state is considered "feasible" when:
- Outlet pressure is within tolerance of target
- All compressor surge/stonewall margins are satisfied
- All equipment operates within design limits

### 2. Generating Lift Curve Tables

Lift curve tables map inlet/outlet pressure combinations to maximum flow rates. These are essential for coupling surface network models with reservoir simulators:

```java
// Define pressure ranges
double[] inletPressures = {40.0, 50.0, 60.0, 70.0, 80.0};
double[] outletPressures = {90.0, 100.0, 110.0, 120.0};

// Generate table
LiftCurveTable table = optimizer.generateLiftCurveTable(
    inletPressures,
    outletPressures,
    "bara"
);

// Export to Eclipse format
System.out.println(table.toEclipseFormat());

// Export to JSON for other applications
System.out.println(table.toJson());
```

### 3. Capacity Curves

Generate a 1D curve showing flow capacity vs outlet pressure at a fixed inlet pressure:

```java
double[] outletPressures = {80.0, 90.0, 100.0, 110.0, 120.0};

Map<Double, Double> curve = optimizer.generateCapacityCurve(
    60.0,             // fixed inlet pressure
    outletPressures,  // outlet pressures to evaluate
    "bara"
);

// Result: Map<OutletPressure, MaxFlowRate>
curve.forEach((pOut, flow) -> 
    System.out.println("P_out=" + pOut + " bara -> " + flow + " kg/hr")
);
```

### 4. Minimum Power Optimization

Find the operating point that minimizes total compressor power while meeting constraints:

```java
Map<String, Object> result = optimizer.findMinimumPowerOperatingPoint(
    50.0,    // inlet pressure
    100.0,   // target outlet pressure
    250.0,   // target flow rate
    "bara",  // pressure unit
    "kg/hr"  // flow unit
);

System.out.println("Minimum power: " + result.get("minimumPower") + " kW");
System.out.println("Achieved flow: " + result.get("achievedFlow"));
System.out.println("Converged: " + result.get("converged"));
```

## Configuration Options

| Parameter | Method | Description | Default |
|-----------|--------|-------------|---------|
| Inlet Stream | `setInletStream()` | Stream where flow rate is adjusted | Required |
| Outlet Stream | `setOutletStream()` | Stream where outlet pressure is measured | Required |
| Flow Unit | `setFlowUnit()` | Unit for flow rate results | "kg/hr" |
| Max Flow | `setMaxFlow()` | Upper bound for flow rate search | 1000.0 |
| Min Flow | `setMinFlow()` | Lower bound for flow rate search | 0.0 |
| Flow Tolerance | `setFlowTolerance()` | Binary search convergence tolerance | 0.1 |
| Pressure Tolerance | `setPressureTolerance()` | Outlet pressure feasibility tolerance | 0.1 bara |
| Minimum Surge Margin | `setMinSurgeMargin()` | Required distance from compressor surge | 0.1 (10%) |
| Minimum Stonewall Margin | `setMinStonewallMargin()` | Required distance from compressor stonewall | 0.1 (10%) |

## Process Types

The optimizer works with any `ProcessSystem` that has definable inlet/outlet streams:

### Simple Pipeline with Valve

```java
Stream feed = new Stream("feed", fluid);
ThrottlingValve valve = new ThrottlingValve("valve", feed);
valve.setOutletPressure(targetPressure, "bara");
Stream outlet = new Stream("outlet", valve);

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
Cooler aftercooler = new Cooler("cooler", compressor);
aftercooler.setOutTemperature(40.0, "C");
Stream outlet = new Stream("outlet", aftercooler);

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
Cooler cooler1 = new Cooler("cooler1", comp1);
cooler1.setOutTemperature(40.0, "C");

// Second stage
Compressor comp2 = new Compressor("comp2", cooler1);
comp2.setOutletPressure(90.0, "bara");
Cooler cooler2 = new Cooler("cooler2", comp2);
cooler2.setOutTemperature(40.0, "C");

// The optimizer will track total power across all compressors
double totalPower = optimizer.calculateTotalPower();
```

## Eclipse VFP Table Integration

The optimizer generates tables compatible with Eclipse VFPPROD keyword:

```java
LiftCurveTable table = optimizer.generateLiftCurveTable(
    new double[] {30, 40, 50, 60, 70},      // THP values
    new double[] {80, 90, 100, 110, 120},   // Export pressures
    "bara"
);

// Get Eclipse-formatted output
String eclipseTable = table.toEclipseFormat();
```

Output format:
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

1. **Pressure Constraint**: Outlet pressure is within `pressureTolerance` of target
2. **Compressor Surge**: All compressors operate above their surge limit with the configured margin
3. **Compressor Stonewall**: All compressors operate below their stonewall limit with the configured margin
4. **Process Convergence**: The process simulation converges successfully

When generating lift curve tables, infeasible points are marked with `Double.NaN` internally and `1*` in Eclipse format output.

## Power Tracking

The optimizer tracks compressor power consumption:

```java
// Get total power after running
double totalPower = optimizer.calculateTotalPower();

// LiftCurveTable includes power at each operating point
LiftCurveTable table = optimizer.generateLiftCurveTable(...);
double powerAtPoint = table.getPower(rowIndex, colIndex);
```

## JSON Output

The `LiftCurveTable` provides JSON export for integration with external tools:

```java
String json = table.toJson();
```

Output:
```json
{
  "inletPressures": [30.0, 40.0, 50.0],
  "outletPressures": [80.0, 90.0, 100.0],
  "pressureUnit": "bara",
  "flowUnit": "kg/hr",
  "flowRates": [
    [450.5, 380.2, 310.0],
    [520.3, 450.8, 380.4],
    [580.1, 520.5, 450.2]
  ],
  "power_kW": [
    [1200.5, 1450.2, 1700.0],
    [1100.3, 1350.8, 1600.4],
    [1050.1, 1280.5, 1520.2]
  ],
  "bottlenecks": [
    ["compressor1", "compressor1", "compressor2"],
    ["compressor1", "compressor1", "compressor2"],
    ["valve1", "compressor1", "compressor2"]
  ],
  "feasiblePointCount": 9,
  "totalPoints": 9
}
```

## Best Practices

### 1. Set Realistic Bounds

```java
// Always set bounds based on process capabilities
optimizer.setMinFlow(10.0);      // Minimum stable flow
optimizer.setMaxFlow(1000.0);    // Equipment limits
```

### 2. Configure Tolerances Appropriately

```java
// Tighter tolerances = more accurate but slower
optimizer.setFlowTolerance(0.1);     // 0.1 kg/hr
optimizer.setPressureTolerance(0.05); // 0.05 bar
```

### 3. Check Feasibility Before Using Results

```java
double flow = optimizer.findMaxFlowRate(pin, pout, "bara");
if (Double.isNaN(flow) || flow <= 0) {
    System.out.println("No feasible flow rate found");
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
- [Process Equipment](README) - Process equipment overview

## Example: Complete Workflow

```java
// 1. Create and configure process
ProcessSystem process = createGasCompressionProcess();

// 2. Configure optimizer
PressureBoundaryOptimizer optimizer = new PressureBoundaryOptimizer(process);
optimizer.setInletStream(process.getStreamByName("feed"));
optimizer.setOutletStream(process.getStreamByName("outlet"));
optimizer.setFlowUnit("MSm3/day");
optimizer.setMinFlow(0.5);
optimizer.setMaxFlow(15.0);
optimizer.setPressureTolerance(0.1);

// 3. Generate lift curve table
double[] inletP = generateRange(25, 75, 11);   // 25-75 bara, 11 points
double[] outletP = generateRange(80, 150, 15); // 80-150 bara, 15 points

LiftCurveTable table = optimizer.generateLiftCurveTable(inletP, outletP, "bara");

// 4. Export results
System.out.println("Feasible points: " + table.countFeasiblePoints() + 
                   "/" + (inletP.length * outletP.length));

// Save to file for Eclipse
Files.writeString(Path.of("vfp_table.inc"), table.toEclipseFormat());

// Save JSON for analysis
Files.writeString(Path.of("lift_curve.json"), table.toJson());
```

## Troubleshooting

| Issue | Possible Cause | Solution |
|-------|----------------|----------|
| All points infeasible | Pressure range too extreme | Reduce outlet pressure range |
| Very slow generation | Grid too fine | Use coarser grid or parallel execution |
| NaN flow rates | Process doesn't converge | Check fluid composition and EOS |
| Power values missing | No compressors in process | Expected for simple valve systems |

