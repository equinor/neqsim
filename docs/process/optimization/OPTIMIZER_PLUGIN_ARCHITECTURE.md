---
title: Optimizer Plugin Architecture
description: The Optimizer Plugin Architecture provides a flexible, extensible framework for evaluating equipment capacity constraints and optimizing process throughput. It enables automated bottleneck detection, ...
---

# Optimizer Plugin Architecture

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

## Overview

The Optimizer Plugin Architecture provides a flexible, extensible framework for evaluating equipment capacity constraints and optimizing process throughput. It enables automated bottleneck detection, lift curve generation, and integration with reservoir simulators like Eclipse.

### Related Documentation

| Document                                                                      | Description                       |
| ----------------------------------------------------------------------------- | --------------------------------- |
| [Optimization Overview](OPTIMIZATION_OVERVIEW)                                | When to use which optimizer       |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | ProductionOptimizer examples      |
| [Multi-Objective Optimization](multi-objective-optimization)                  | Pareto fronts and trade-offs      |
| [Flow Rate Optimization](flow-rate-optimization)                              | FlowRateOptimizer and lift curves |
| [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK)             | Equipment constraints             |

### Key Components

| Component                             | Description                                            | Location                                     |
| ------------------------------------- | ------------------------------------------------------ | -------------------------------------------- |
| **EquipmentCapacityStrategy**         | Interface for equipment-specific constraint evaluation | `neqsim.process.equipment.capacity`          |
| **EquipmentCapacityStrategyRegistry** | Singleton registry with auto-discovery                 | `neqsim.process.equipment.capacity`          |
| **ProcessOptimizationEngine**         | Unified API for process optimization                   | `neqsim.process.util.optimizer`              |
| **EclipseVFPExporter**                | Eclipse VFP table generation                           | `neqsim.process.util.optimizer`              |
| **Driver Package**                    | Driver curves for compressors                          | `neqsim.process.equipment.compressor.driver` |
| **OperatingEnvelope**                 | Compressor operating envelope tracking                 | `neqsim.process.equipment.compressor`        |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ProcessOptimizationEngine                             │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │  findMaximumThroughput() │ evaluateAllConstraints() │ generateLiftCurve()││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                 EquipmentCapacityStrategyRegistry (Singleton)            ││
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        ││
│  │  │ Compressor  │ │  Separator  │ │    Pump     │ │    Valve    │        ││
│  │  │  Strategy   │ │  Strategy   │ │  Strategy   │ │  Strategy   │        ││
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘        ││
│  │  ┌─────────────┐ ┌─────────────┐                                        ││
│  │  │    Pipe     │ │ HeatExchgr  │   + Custom Strategies (register)       ││
│  │  │  Strategy   │ │  Strategy   │                                        ││
│  │  └─────────────┘ └─────────────┘                                        ││
│  └─────────────────────────────────────────────────────────────────────────┘│
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────────┐│
│  │                       CapacityConstraint                                 ││
│  │  name │ unit │ type │ designValue │ maxValue │ valueSupplier │ severity ││
│  └─────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                           EclipseVFPExporter                                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐                │
│  │   VFPPROD       │ │    VFPINJ       │ │  Validated BHP  │                │
│  │  (Production)   │ │   (Injection)   │ │    (Export)     │                │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Start

Compile the complete `OptimizationGuideSetup.java` in [Getting Started](getting-started.md)
first. The snippets below are method bodies: place their imports above your class and run
each section after the base setup below. `process`, `feed`, `compressor`, `engine`, and
`logger` refer to that setup. Later result/report snippets follow the throughput search.
Each example is checked by `OptimizationEntryDocumentationTest` against the current source.
Use Java 8 or newer and the NeqSim dependency classpath. Driver and map data are synthetic.

### Basic Usage: Evaluate Process Constraints

<!-- optimization-example: 0 -->
```java
import java.util.Map;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Save and compile OptimizationGuideSetup.java from Getting Started first.
ProcessSystem process = OptimizationGuideSetup.createProcess();
Stream feed = (Stream) process.getUnit("feed");
Compressor compressor = (Compressor) process.getUnit("comp");
Logger logger = LogManager.getLogger("OptimizationGuide");
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
engine.setFeedStreamName("feed");
engine.setOutletStreamName("outlet");

ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
for (ProcessOptimizationEngine.EquipmentConstraintStatus status : report.getEquipmentStatuses()) {
    logger.info("{}: {}% utilization; hard limits satisfied: {}", status.getEquipmentName(),
        100.0 * status.getUtilization(), status.isWithinLimits());
}
logger.info("Bottleneck equipment: {}", engine.findBottleneckEquipment());
```

### Find Maximum Throughput

<!-- optimization-example: 1 -->
```java
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
engine.setTolerance(1.0); // absolute flow interval, kg/hr
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
if (!result.isConverged()) {
    throw new IllegalStateException(result.getErrorMessage());
}
// Explicitly restore the reported point for compatibility with neqsim 3.20.0.
feed.setFlowRate(result.getOptimalValue(), "kg/hr");
process.run();
logger.info("Optimal flow: {} kg/hr; bottleneck: {}; power: {} kW",
    result.getOptimalValue(), result.getBottleneck(), compressor.getPower("kW"));
logger.info("Reported constraint violations: {}", result.getConstraintViolations());
```

---

## Equipment Capacity Strategies

### Strategy Interface

Each equipment type has a dedicated strategy that understands its specific constraints:

<!-- optimization-example: 2 -->
```java
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;

EquipmentCapacityStrategy strategy = EquipmentCapacityStrategyRegistry.getInstance().findStrategy(compressor);
boolean supported = strategy.supports(compressor);
double utilization = strategy.evaluateCapacity(compressor);
Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);
boolean hardLimitsSatisfied = strategy.isWithinHardLimits(compressor);
boolean designLimitsSatisfied = strategy.isWithinSoftLimits(compressor);
logger.info("{}: utilization {}; hard {}; design {}", strategy.getName(), utilization,
    hardLimitsSatisfied, designLimitsSatisfied);
```

### Built-in Strategies

#### 1. CompressorCapacityStrategy

Evaluates compressor constraints including:

| Constraint             | Type | Description                        |
| ---------------------- | ---- | ---------------------------------- |
| `speed`                | HARD | Rotational speed vs max/min limits |
| `power`                | HARD | Shaft power vs driver capacity     |
| `surgeMargin`          | HARD | Distance to surge line             |
| `stonewallMargin`      | SOFT | Distance to stonewall              |
| `dischargeTemperature` | HARD | Outlet temperature vs limits       |

<!-- optimization-example: 3 -->
```java
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;

CompressorCapacityStrategy strategy = new CompressorCapacityStrategy(0.10, 0.05, 200.0);
Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);
CapacityConstraint surgeConstraint = constraints.get("surgeMargin");
if (surgeConstraint != null && surgeConstraint.isEnabled()) {
    logger.info("Surge constraint current: {} {}; lower bound: {}", surgeConstraint.getCurrentValue(),
        surgeConstraint.getUnit(), surgeConstraint.getMinValue());
} else {
    logger.info("No active surge constraint: supply a validated performance chart for surge analysis");
}
```

#### 2. SeparatorCapacityStrategy

Evaluates separator constraints:

| Constraint      | Type | Description                          |
| --------------- | ---- | ------------------------------------ |
| `liquidLevel`   | SOFT | Liquid level vs max allowed          |
| `gasLoadFactor` | SOFT | Gas velocity/terminal velocity ratio |

<!-- optimization-example: 4 -->
```java
import neqsim.process.equipment.capacity.SeparatorCapacityStrategy;

// Independent separator example using the solved inlet fluid; dimensions are illustrative.
Separator separator = new Separator("separator capacity example", feed);
separator.setInternalDiameter(2.0);
separator.setSeparatorLength(6.0);
separator.run();
SeparatorCapacityStrategy strategy = new SeparatorCapacityStrategy(
    0.10, // maximum gas load factor, m/s
    0.80  // maximum liquid level fraction
);
Map<String, CapacityConstraint> constraints = strategy.getConstraints(separator);
```

#### 3. PumpCapacityStrategy

Evaluates pump constraints:

| Constraint   | Type | Description               |
| ------------ | ---- | ------------------------- |
| `power`      | HARD | Motor power vs rating     |
| `npshMargin` | HARD | NPSH available - required |
| `flowRate`   | SOFT | Flow vs minimum flow      |

<!-- optimization-example: 5 -->
```java
import neqsim.process.equipment.capacity.PumpCapacityStrategy;

PumpCapacityStrategy strategy = new PumpCapacityStrategy(
    1.0,   // minNpshMargin (1.0 m)
    1.1    // maxPowerFactor (110% overload allowed)
);
```

#### 4. ValveCapacityStrategy

Evaluates valve constraints:

| Constraint          | Type | Description                |
| ------------------- | ---- | -------------------------- |
| `valveOpening`      | SOFT | Opening % vs min/max range |
| `pressureDropRatio` | SOFT | ΔP/inlet pressure ratio    |

#### 5. PipeCapacityStrategy

Evaluates pipe/pipeline constraints:

| Constraint     | Type | Description                       |
| -------------- | ---- | --------------------------------- |
| `velocity`     | SOFT | Superficial velocity vs erosional |
| `pressureDrop` | SOFT | Pressure drop vs allowable        |

#### 6. HeatExchangerCapacityStrategy

Evaluates heat exchanger constraints:

| Constraint          | Type | Description                  |
| ------------------- | ---- | ---------------------------- |
| `duty`              | SOFT | Heat transfer duty vs design |
| `outletTemperature` | SOFT | Outlet temperature           |

### Custom Strategy Registration

Register custom strategies for specialized equipment:

<!-- optimization-example: 6 -->
```java
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;

// Extend a complete strategy; restrict it to the named compressor.
CompressorCapacityStrategy customStrategy = new CompressorCapacityStrategy(0.15, 0.08, 180.0) {
    @Override
    public String getName() {
        return "NamedCompressorDemo";
    }
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        return equipment instanceof Compressor && equipment.getName().equals("comp");
    }

    @Override
    public int getPriority() {
        return 100;
    }
};
EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
registry.register(customStrategy);
try {
    logger.info("Selected strategy priority: {}", registry.findStrategy(compressor).getPriority());
} finally {
    registry.unregister(customStrategy.getName());
}
```

---

## Driver Package

The driver package provides compressor driver models with performance curves.

### DriverCurve Interface

<!-- optimization-example: 7 -->
```java
import neqsim.process.equipment.compressor.driver.DriverCurve;
import neqsim.process.equipment.compressor.driver.GasTurbineDriver;

DriverCurve curve = new GasTurbineDriver(15000.0, 10000.0, 0.35);
double availablePowerKW = curve.getAvailablePower(curve.getRatedSpeed());
double efficiency = curve.getEfficiency(curve.getRatedSpeed(), 0.75);
double fuelRate = curve.getFuelConsumption(10000.0, curve.getRatedSpeed());
logger.info("Available: {} kW; efficiency {}; fuel: {} kg/hr", availablePowerKW, efficiency, fuelRate);
```

### GasTurbineDriver

Models gas turbine drivers with ambient derating:

<!-- optimization-example: 8 -->
```java
import neqsim.process.equipment.compressor.driver.GasTurbineDriver;

GasTurbineDriver driver = new GasTurbineDriver(15000.0, 10000.0, 0.35);
driver.setAmbientTemperature(30.0); // Celsius, not Kelvin
driver.setAltitude(500.0); // metres
double availablePower = driver.getAvailablePower(driver.getRatedSpeed());
double fuelGas = driver.getFuelConsumption(10000.0, driver.getRatedSpeed());
logger.info("Available: {} kW; fuel at 10 MW: {} kg/hr", availablePower, fuelGas);
```

### ElectricMotorDriver

Models electric motor drivers with VFD support:

<!-- optimization-example: 9 -->
```java
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;

ElectricMotorDriver motor = new ElectricMotorDriver(5000.0, 3000.0, 0.96);
motor.setHasVFD(true);
motor.setMinSpeed(600.0);
motor.setMaxSpeed(3600.0);
double efficiency = motor.getEfficiency(3000.0, 0.75);
logger.info("Motor efficiency at 3000 rpm and 75% load: {}%", efficiency * 100.0);
```

### SteamTurbineDriver

Models steam turbine drivers with Willans line:

<!-- optimization-example: 10 -->
```java
import neqsim.process.equipment.compressor.driver.SteamTurbineDriver;

SteamTurbineDriver turbine = new SteamTurbineDriver(8000.0, 10000.0, 0.78);
turbine.setInletPressure(40.0); // bara
turbine.setInletTemperature(400.0); // Celsius
turbine.setExhaustPressure(4.0); // bara
double steamFlow = turbine.getSteamConsumption(6000.0, turbine.getRatedSpeed());
logger.info("Steam consumption at 6 MW: {} kg/hr", steamFlow);
```

---

## Compressor Operating Envelope

Track and validate compressor operation against surge/stonewall limits:

<!-- optimization-example: 11 -->
```java
import neqsim.process.equipment.compressor.OperatingEnvelope;

OperatingEnvelope envelope = new OperatingEnvelope(7000.0, 11000.0);
envelope.setRatedSpeed(10000.0);
// Illustrative map: actual m3/hr, kJ/kg, rpm. Supply vendor curves for design work.
envelope.setSurgeLine(new double[] {500, 700, 900, 1100},
    new double[] {80, 100, 115, 125}, new double[] {10000, 10000, 10000, 10000});
envelope.setStonewallLine(new double[] {1800, 2200, 2600, 3000},
    new double[] {60, 80, 95, 105}, new double[] {10000, 10000, 10000, 10000});
double flow = 1200.0;
double head = 95.0;
double speed = 9500.0;
boolean withinEnvelope = envelope.isWithinEnvelope(flow, head, speed);
double surgeMargin = envelope.getSurgeMargin(flow, head, speed);
double stonewallMargin = envelope.getStonewallMargin(flow, head, speed);
logger.info("Within envelope: {}; surge {}%; stonewall {}%; limiting {}", withinEnvelope,
    100.0 * surgeMargin, 100.0 * stonewallMargin, envelope.getLimitingConstraint(flow, head, speed));
```

---

## Compressor Constraint Configuration

Configure comprehensive compressor constraints:

<!-- optimization-example: 12 -->
```java
import neqsim.process.equipment.compressor.CompressorConstraintConfig;

CompressorConstraintConfig config = new CompressorConstraintConfig();
config.setMinSurgeMargin(0.10);
config.setMinStonewallMargin(0.05);
config.setRatedSpeed(10000.0);
config.setMinSpeedRatio(0.5); // 5000 rpm
config.setMaxSpeedRatio(1.1); // 11000 rpm
config.setMaxPower(15000.0); // kW
config.setMaxDischargeTemperatureCelsius(200.0);
config.setMaxSuctionTemperature(333.15); // Kelvin: 60 C
CompressorConstraintConfig conservative = CompressorConstraintConfig.createConservativeConfig();
CompressorConstraintConfig aggressive = CompressorConstraintConfig.createAggressiveConfig();
CompressorConstraintConfig api617 = CompressorConstraintConfig.createAPI617Config();
```

---

## Eclipse VFP Export

Format supplied well BHP tables for reservoir simulation. Qualify the well calculation and its feasible operating envelope before export.

> **📘 See Also:** [Capacity Constraint Framework - VFP Section](../CAPACITY_CONSTRAINT_FRAMEWORK#constraints-in-eclipse-vfp-table-generation) for detailed documentation on constraint management for VFP studies.

### How Constraints Affect VFP Tables

`EclipseVFPExporter` is a formatter for supplied pressure tables. It has no process-taking
constructor, constraint-enforcement switch, BHP solver, or pointwise bottleneck result API.
Solve the well/pipeline separately and reject failed or infeasible points before supplying
finite BHP values. Never substitute a throughput maximum for BHP.

The writer serializes every supplied composition and ALQ slice and rejects missing or infeasible
pressures. OPM/Eclipse `VFPPROD` and `VFPINJ` are supported with explicit `METRIC` or `FIELD`
headers. Input rates default to Sm3/day and pressures to bara; the output units are converted,
including the FIELD Mscf/STB gas-ratio convention. The surrounding deck must use the same unit
system. See the [VFP export contract](vfp-export-contract.md) for supported definitions,
index order, conversion rules and migration from the legacy methods. These examples qualify
formatting; a well model must be independently validated.

### Constraint Configuration for VFP

<!-- optimization-example: 13 -->
```java
import neqsim.process.util.optimizer.EclipseVFPExporter;

// Exporter formats supplied BHP data; it does not execute or constrain a process.
EclipseVFPExporter exporter = new EclipseVFPExporter(1);
exporter.setDatumDepth(1000.0); // metres
exporter.setFlowRateType("GAS");
exporter.setWaterCutType("WGR");
exporter.setGORType("OGR");
exporter.setUnitSystem("METRIC");
exporter.setTableTitle("Synthetic dry-gas format example; not a qualified well model");
```

### VFPPROD Tables (Production Wells)

<!-- optimization-example: 14 -->
```java
double[] thp = {20.0, 40.0}; // bara
double[] flowRates = {10000.0, 20000.0, 40000.0}; // gas Sm3/day for METRIC/GAS
// Input order: [flow][THP][water ratio][gas ratio][ALQ].
double[][][][][] bhp = new double[3][2][1][1][1];
bhp[0][0][0][0][0] = 30.0;
bhp[1][0][0][0][0] = 36.0;
bhp[2][0][0][0][0] = 48.0;
bhp[0][1][0][0][0] = 50.0;
bhp[1][1][0][0][0] = 56.0;
bhp[2][1][0][0][0] = 68.0;
exporter.setTHPs(thp);
exporter.setFlowRates(flowRates);
exporter.setWaterCuts(new double[] {0.0});
exporter.setGORs(new double[] {0.0});
exporter.setALQs(new double[] {0.0});
exporter.setBHPTable(bhp);
String vfpTable = exporter.getVFPPRODString();
exporter.exportVFPPROD("VFPPROD_WELL1.INC");
```

### VFPINJ Tables (Injection Wells)

<!-- optimization-example: 15 -->
```java
// Reuse the supplied dry-gas BHP table; this example injects gas, not water.
String vfpInj = exporter.getVFPINJString();
exporter.exportVFPINJ("VFPINJ_GAS1.INC");
```

### VFPEXP Method (Legacy Export-System Writer)

<!-- optimization-example: 16 -->
```java
// The unsupported VFPEXP dialect fails before opening the output file.
try {
    exporter.exportVFPEXP("EXPORT_SYSTEM.INC");
    throw new AssertionError("VFPEXP must reject unsupported export-system semantics");
} catch (UnsupportedOperationException expected) {
    logger.info("Use process capacity CSV/JSON for facility screening");
}
```

### What-If Studies: Modifying Constraints for VFP Scenarios

<!-- optimization-example: 17 -->
```java
// Compare installed power limits in the PROCESS, separately from formatting a BHP table.
double originalPowerKW = compressor.getMechanicalDesign().maxDesignPower;
try {
    for (double ratingKW : new double[] {4000.0, 4400.0}) {
        compressor.getMechanicalDesign().setMaxDesignPower(ratingKW);
        engine.clearCache();
        ProcessOptimizationEngine.OptimizationResult candidate =
            engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
        logger.info("Rating {} kW: throughput {} kg/hr; converged {}", ratingKW,
            candidate.getOptimalValue(), candidate.isConverged());
    }
} finally {
    compressor.getMechanicalDesign().setMaxDesignPower(originalPowerKW);
    feed.setFlowRate(50000.0, "kg/hr");
    process.run();
    engine.clearCache();
}
```

### Process Bottleneck Reporting Alongside Export

<!-- optimization-example: 18 -->
```java
// Evaluate an actual solved process point; the exporter does not supply bottleneck records.
ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
for (ProcessOptimizationEngine.EquipmentConstraintStatus point : report.getEquipmentStatuses()) {
    logger.info("{}: utilization {}%; limiting {}; hard limits satisfied {}", point.getEquipmentName(),
        100.0 * point.getUtilization(), point.getBottleneckConstraint(), point.isWithinLimits());
}
```

---

## ProcessOptimizationEngine API Reference

### Constructors

<!-- optimization-example: 19 -->
```java
import neqsim.process.processmodel.ProcessModule;

ProcessOptimizationEngine systemEngine = new ProcessOptimizationEngine(process);
ProcessModule fieldModule = new ProcessModule("Field Development");
fieldModule.add(process);
ProcessOptimizationEngine moduleEngine = new ProcessOptimizationEngine(fieldModule);
```

Creates optimization engine for the given process system or module.

### ProcessModule Support

The `ProcessOptimizationEngine` fully supports `ProcessModule`, which can contain multiple `ProcessSystem` instances and nested modules. All optimization methods work recursively across the entire module hierarchy.

<!-- optimization-example: 20 -->
```java
import neqsim.process.processmodel.ProcessModule;

ProcessModule fieldModule = new ProcessModule("Field Development");
fieldModule.add(OptimizationGuideSetup.createProcess());
fieldModule.run();
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(fieldModule);
engine.setFeedStreamName("feed");
engine.setOutletStreamName("outlet");
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
engine.setTolerance(1.0);
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
logger.info("Feed stream: {}; optimum: {} kg/hr", engine.getFeedStreamName(), result.getOptimalValue());
```

### Feed Stream Configuration

By default, the optimization engine varies the **first unit operation** in the process. For complex processes or modules, you should explicitly specify the feed stream:

| Method                           | Description                                            |
| -------------------------------- | ------------------------------------------------------ |
| `setFeedStreamName(String name)` | Set the name of the stream to vary during optimization |
| `getFeedStreamName()`            | Get the name of the stream being varied                |

<!-- optimization-example: 21 -->
```java
engine.setFeedStreamName("feed");
ProcessOptimizationEngine.OptimizationResult result = engine
    .setFeedStreamName("feed")
    .findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
logger.info("Optimizing flow rate of: {}", engine.getFeedStreamName());
```

### Outlet Stream Configuration

By default, the optimization engine monitors the **last unit operation** for outlet conditions. For complex processes or modules, you can explicitly specify the outlet stream:

| Method                               | Description                                                   |
| ------------------------------------ | ------------------------------------------------------------- |
| `setOutletStreamName(String name)`   | Set the name of the outlet stream to monitor                  |
| `getOutletStreamName()`              | Get the name of the outlet stream being monitored             |
| `getOutletTemperature()`             | Get outlet temperature in Kelvin                              |
| `getOutletTemperature(String unit)`  | Get outlet temperature in specified unit ("C", "K", "F", "R") |
| `getOutletFlowRate(String flowUnit)` | Get outlet flow rate in specified unit ("kg/hr", "MSm3/day")  |

<!-- optimization-example: 22 -->
```java
engine.setFeedStreamName("feed");
engine.setOutletStreamName("outlet");
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
feed.setFlowRate(result.getOptimalValue(), "kg/hr");
process.run();
logger.info("Outlet: {} C; {} MSm3/day", engine.getOutletTemperature("C"),
    engine.getOutletFlowRate("MSm3/day"));
```

#### ProcessModule Example with Feed and Outlet Streams

<!-- optimization-example: 23 -->
```java
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.equipment.heatexchanger.Cooler;

ProcessModule facilityModule = new ProcessModule("Offshore Facility");
ProcessSystem compression = OptimizationGuideSetup.createProcess();
facilityModule.add(compression);
Stream compressionOutlet = (Stream) compression.getUnit("outlet");
Cooler exportCooler = new Cooler("export cooler", compressionOutlet);
exportCooler.setOutTemperature(313.15);
Stream exportGas = new Stream("Export Gas", exportCooler.getOutletStream());
ProcessSystem export = new ProcessSystem("Export");
export.add(exportCooler);
export.add(exportGas);
facilityModule.add(export);
facilityModule.run();
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(facilityModule);
engine.setFeedStreamName("feed");
engine.setOutletStreamName("Export Gas");
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
engine.setTolerance(1.0);
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);
```

### Core Methods

| Method                                                       | Returns                      | Description                                |
| ------------------------------------------------------------ | ---------------------------- | ------------------------------------------ |
| `evaluateAllConstraints()`                                   | `ConstraintReport`           | Evaluate constraints on all equipment      |
| `findMaximumThroughput(pin, pout, minQ, maxQ)`               | `OptimizationResult`         | Find max flow for pressure constraints     |
| `findRequiredInletPressure(outletP, flowRate)`               | `OptimizationResult`         | Find inlet pressure for target flow        |
| `findBottleneckEquipment()`                                  | `String`                     | Get name of bottleneck equipment           |
| `generateCapacityScreening(pressures, temperaturesK)`         | `LiftCurveData`              | Fixed-composition mass-throughput screening; no BHP or recombination      |
| `analyzeSensitivity(flow, inletP, outletP)`                  | `SensitivityResult`          | Analyze flow sensitivity and margins       |
| `calculateShadowPrices(flow, inletP, outletP)`               | `Map<String, Double>`        | Calculate heuristic constraint-relief indicators         |
| `createFlowRateOptimizer()`                                  | `FlowRateOptimizer`          | Create integrated FlowRateOptimizer        |
| `generateComprehensiveLiftCurve(stream, pressures, outletP)` | `FlowRateOptimizer`          | Generate lift curves via FlowRateOptimizer |
| `evaluateConstraintsWithCache()`                             | `ConstraintEvaluationResult` | Evaluate with caching enabled              |
| `calculateFlowSensitivities(flow, unit)`                     | `Map<String, Double>`        | Calculate flow sensitivities by equipment  |
| `estimateMaximumFlow(currentFlow, unit)`                     | `double`                     | Estimate max feasible flow                 |
| `getConstraintEvaluator()`                                   | `ProcessConstraintEvaluator` | Get underlying constraint evaluator        |

### OptimizationResult Class

<!-- optimization-example: 24 -->
```java
// Engine result: flow for findMaximumThroughput, pressure for findRequiredInletPressure.
double optimalValue = result.getOptimalValue();
boolean converged = result.isConverged();
String bottleneck = result.getBottleneck();
java.util.List<String> violations = result.getConstraintViolations();
// Power is read from the solved equipment with an explicit unit.
double powerKW = compressor.getPower("kW");
```

### ConstraintReport Class

<!-- optimization-example: 25 -->
```java
ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
ProcessOptimizationEngine.EquipmentConstraintStatus bottleneck = report.getBottleneck();
double highestUtilization = bottleneck == null ? 0.0 : bottleneck.getUtilization();
logger.info("Highest reported utilization: {}%", 100.0 * highestUtilization);
```

### EquipmentConstraintStatus Class

<!-- optimization-example: 26 -->
```java
for (ProcessOptimizationEngine.EquipmentConstraintStatus status :
        engine.evaluateAllConstraints().getEquipmentStatuses()) {
    logger.info("{} ({}): {}%; hard limits satisfied {}", status.getEquipmentName(),
        status.getEquipmentType(), 100.0 * status.getUtilization(), status.isWithinLimits());
    for (CapacityConstraint constraint : status.getConstraints()) {
        if (constraint.isEnabled()) {
            logger.info("{}: {} {}", constraint.getName(), constraint.getCurrentValue(), constraint.getUnit());
        }
    }
}
```

---

## Integration Examples

### Example 1: Production Optimization with Constraints

<!-- optimization-example: 27 -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;

public class ProductionOptimizationExample {
    private static final Logger logger = LogManager.getLogger(ProductionOptimizationExample.class);

    public static void main(String[] args) {
        ProcessSystem process = OptimizationGuideSetup.createProcess();
        ProcessOptimizationEngine.OptimizationResult result = OptimizationGuideSetup.optimize(process);
        Compressor compressor = (Compressor) process.getUnit("comp");
        logger.info("Maximum throughput: {} kg/hr; power: {} kW; bottleneck: {}",
            result.getOptimalValue(), compressor.getPower("kW"), result.getBottleneck());
    }
}
```

### Example 2: Fixed-Composition Throughput Sweep for Eclipse

<!-- optimization-example: 28 -->
```java
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LiftCurveExample {
    private static final Logger logger = LogManager.getLogger(LiftCurveExample.class);

    public static void main(String[] args) {
        ProcessSystem process = OptimizationGuideSetup.createProcess();
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        engine.setFeedStreamName("feed");
        engine.setOutletStreamName("outlet");
        engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.BINARY_SEARCH);
        engine.setTolerance(1.0);
        // Fixed composition and export pressure; each point is an independent throughput search.
        for (double inletBara : new double[] {40.0, 50.0, 60.0}) {
            ProcessOptimizationEngine.OptimizationResult result =
                engine.findMaximumThroughput(inletBara, 150.0, 10000.0, 200000.0);
            if (!result.isConverged()) {
                throw new IllegalStateException(result.getErrorMessage());
            }
            logger.info("Inlet {} bara: maximum throughput {} kg/hr", inletBara, result.getOptimalValue());
        }
    }
}
```

### Example 3: Using Strategy Registry Directly

<!-- optimization-example: 29 -->
```java
import java.util.Map;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.processmodel.ProcessSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StrategyUsageExample {
    private static final Logger logger = LogManager.getLogger(StrategyUsageExample.class);

    public static void main(String[] args) {
        ProcessSystem process = OptimizationGuideSetup.createProcess();
        Compressor compressor = (Compressor) process.getUnit("comp");
        EquipmentCapacityStrategy strategy = EquipmentCapacityStrategyRegistry.getInstance().findStrategy(compressor);
        for (CapacityConstraint constraint : strategy.getConstraints(compressor).values()) {
            if (constraint.isEnabled()) {
                logger.info("{}: {} {} ({}%)", constraint.getName(), constraint.getCurrentValue(),
                    constraint.getUnit(), constraint.getUtilizationPercent());
            }
        }
        logger.info("Violations: {}", strategy.getViolations(compressor));
    }
}
```

---

## Unified Result Classes

`OptimizationResultBase` is a separate result container. The engine and `ProductionOptimizer`
retain their own nested result types; do not interchange their getters or cast one to another.

### OptimizationResultBase

The `OptimizationResultBase` class is a standalone result container:

<!-- optimization-example: 30 -->
```java
import neqsim.process.util.optimizer.OptimizationResultBase;

// Demonstrates result bookkeeping; these illustrative values are not a new optimization run.
OptimizationResultBase result = new OptimizationResultBase();
result.markStart();
result.setObjective("MaxThroughput");
result.incrementIterations();
result.incrementFunctionEvaluations();
result.setOptimalValue(5500.0);
result.addOptimalValue("FlowRate", 5500.0);
result.setObjectiveValue(5500.0);
result.setBottleneckEquipment("comp");
result.setBottleneckConstraint("power");
result.setConverged(true);
result.markEnd();
logger.info("{}; elapsed {} s", result.getSummary(), result.getElapsedTimeSeconds());
```

### Status Enum

The `Status` enum tracks optimization state:

| Status                   | Description                |
| ------------------------ | -------------------------- |
| `NOT_STARTED`            | Optimization not yet begun |
| `IN_PROGRESS`            | Currently running          |
| `CONVERGED`              | Successfully converged     |
| `MAX_ITERATIONS_REACHED` | Hit iteration limit        |
| `INFEASIBLE`             | No feasible solution found |
| `FAILED`                 | Error during optimization  |
| `CANCELLED`              | User cancelled             |

### ConstraintViolation Class

Track constraint violations with detailed information:

<!-- optimization-example: 31 -->
```java
OptimizationResultBase.ConstraintViolation violation =
    new OptimizationResultBase.ConstraintViolation(
        "Compressor1",     // equipment name
        "MaxPower",        // constraint name
        15.0,              // current value
        12.0,              // limit value
        "MW",              // unit
        true               // is hard constraint
    );

logger.info("Violation: " + violation.getViolationAmount());  // 3.0 MW over
logger.info("Percent over: " + violation.getViolationPercent() + "%");  // 25%
```

---

## ProcessConstraintEvaluator

The `ProcessConstraintEvaluator` provides composite constraint evaluation with caching and sensitivity analysis.

### Basic Usage

<!-- optimization-example: 32 -->
```java
import neqsim.process.util.optimizer.ProcessConstraintEvaluator;

// Create evaluator
ProcessConstraintEvaluator evaluator = new ProcessConstraintEvaluator(process);

// Evaluate all constraints
ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();

logger.info("Overall utilization: " + result.getOverallUtilization() * 100 + "%");
logger.info("Bottleneck: " + result.getBottleneckEquipment());
logger.info("Feasible: " + result.isFeasible());
logger.info("Violations: " + result.getTotalViolationCount());

// Get per-equipment summaries
for (Map.Entry<String, ProcessConstraintEvaluator.EquipmentConstraintSummary> entry :
        result.getEquipmentSummaries().entrySet()) {
    ProcessConstraintEvaluator.EquipmentConstraintSummary summary = entry.getValue();
    logger.info(String.format("%s: %.1f%% utilization, margin to limit: %.1f%%%n",
        summary.getEquipmentName(),
        summary.getUtilization() * 100,
        summary.getMarginToLimit() * 100));
}
```

### Constraint Caching

The cache belongs to the evaluator, not the process. Its key does not represent arbitrary
temperature, composition, pressure or rating edits. Clear it after changing inputs or limits,
and before reading constraints from a new solve:

<!-- optimization-example: 33 -->
```java
// Configure cache TTL (default 10 seconds)
evaluator.setCacheTTLMillis(30000);  // 30 seconds

// Evaluate with caching
ProcessConstraintEvaluator.ConstraintEvaluationResult result1 = evaluator.evaluate();
process.run();
evaluator.clearCache();
ProcessConstraintEvaluator.ConstraintEvaluationResult result2 = evaluator.evaluate();  // Fresh evaluation after the new solve

// Clear cache when needed
evaluator.clearCache();
```

### CachedConstraints Class

Manual cache management:

<!-- optimization-example: 34 -->
```java
ProcessConstraintEvaluator.CachedConstraints cache =
    new ProcessConstraintEvaluator.CachedConstraints();

cache.setFlowRate(5000.0);
cache.setTimestamp(System.currentTimeMillis());
cache.setTtlMillis(10000);  // 10 second TTL
cache.setValid(true);

// Check cache status
if (!cache.isExpired() && cache.isValid()) {
    // Use cached results
    double cachedFlow = cache.getFlowRate();
}

// Invalidate when process changes
cache.invalidate();
```

### Flow Sensitivity Analysis

Calculate how constraint utilization changes with flow:

<!-- optimization-example: 35 -->
```java
// Calculate sensitivities at current operating point
Map<String, Double> sensitivities = evaluator.calculateFlowSensitivities(8000.0, "kg/hr");

for (Map.Entry<String, Double> entry : sensitivities.entrySet()) {
    logger.info(String.format("%s: sensitivity = %.3f (utilization change per kg/hr)%n",
        entry.getKey(), entry.getValue()));
}

// Estimate maximum feasible flow
double maxFlow = evaluator.estimateMaxFlow(8000.0, "kg/hr");
logger.info("Estimated max flow: " + maxFlow + " kg/hr");
```

---

## Gradient-Based Optimization

The `ProcessOptimizationEngine` supports gradient descent optimization for smooth objective functions.

### Search Algorithms

| Algorithm          | Description                              | Best For                       |
| ------------------ | ---------------------------------------- | ------------------------------ |
| `BINARY_SEARCH`    | Binary search for feasibility boundary   | Simple monotonic problems      |
| `GOLDEN_SECTION`   | Golden section search                    | Unimodal objectives            |
| `GRADIENT_DESCENT` | Gradient descent with finite differences | Smooth single-flow searches |

### Using Gradient Descent

<!-- optimization-example: 36 -->
```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Select gradient descent algorithm
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
engine.setTolerance(1e-4);
engine.setMaxIterations(100);
engine.setEnforceConstraints(true);

// Find maximum throughput
ProcessOptimizationEngine.OptimizationResult result =
    engine.findMaximumThroughput(50.0, 150.0, 10000.0, 200000.0);

logger.info("Optimal flow: " + result.getOptimalValue() + " kg/hr");
logger.info("Converged: " + result.isConverged());
logger.info("Configured iteration budget: {}", engine.getMaxIterations());
```

### Gradient Descent Features

- **Finite-difference gradient estimation** for the feed flow variable
- **Adaptive step size** with line search backtracking
- **Constraint penalties** for infeasible solutions
- **Bounds enforcement** to stay within search range

---

## Disabled Compressor Constraints

`CompressorCapacityStrategy` excludes disabled constraints from violation lists,
bottleneck selection, and hard/soft limit checks. This matters for fixed-pressure
compressors without performance charts, whose surge and speed constraints are
disabled. Disabled map constraints must not reject an otherwise feasible power
limited operating point. Active limits remain enforced.

## Sensitivity Analysis

Analyze how the optimal solution responds to parameter changes. Sensitivity probes
restore and solve the supplied base flow and inlet pressure before returning, so
equipment outputs remain consistent with the reported throughput. The flow buffer
is the last feasible sampled increase from up to 50 successive 1% probes. It is
zero when the first probe violates a hard limit; it is not the size of that
infeasible step or a guaranteed maximum margin. See the executable
[Practical Examples](PRACTICAL_EXAMPLES#basic-process-optimization) for the explicit
operating-point re-run needed with the 3.20.0 release.

### SensitivityResult Class

<!-- optimization-example: 37 -->
```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Analyze sensitivity at current operating point
ProcessOptimizationEngine.SensitivityResult sensitivity =
    engine.analyzeSensitivity(50000.0, 50.0, 150.0);

logger.info("Base flow: " + sensitivity.getBaseFlow() + " kg/hr");
logger.info("Flow gradient: " + sensitivity.getFlowGradient());
logger.info("Tightest constraint: " + sensitivity.getTightestConstraint());
logger.info("Margin to limit: " + sensitivity.getTightestMargin() * 100 + "%");
logger.info("Flow buffer: " + sensitivity.getFlowBuffer() + " kg/hr");

// Check if near capacity
if (sensitivity.isAtCapacity()) {
    logger.info("WARNING: Operating near capacity!");
    logger.info("Bottleneck: " + sensitivity.getBottleneckEquipment());
}

// Access constraint margins
Map<String, Double> margins = sensitivity.getConstraintMargins();
for (Map.Entry<String, Double> entry : margins.entrySet()) {
    logger.info(String.format("  %s: %.1f%% margin%n", entry.getKey(), entry.getValue() * 100));
}
```

### Shadow Prices

The engine's `calculateShadowPrices` currently returns a numerical constraint-relief
dimensionless indicator derived from a 1% flow perturbation and capacity utilization. It is not an economic shadow price
or a reoptimized throughput gain. Use controlled, independently reoptimized alternatives
to quantify a debottleneck benefit.

<!-- optimization-example: 38 -->
```java
// Calculate numerical constraint-relief indicators
Map<String, Double> shadowPrices = engine.calculateShadowPrices(50000.0, 50.0, 150.0);

logger.info("Constraint-relief indicators (heuristic):");
for (Map.Entry<String, Double> entry : shadowPrices.entrySet()) {
    if (entry.getValue() > 0) {
        logger.info(String.format("  %s: %.2f (heuristic)%n",
            entry.getKey(), entry.getValue()));
    }
}

// Rank the heuristic indicators; confirm benefits with separate optimization runs
String mostValuable = shadowPrices.entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .map(Map.Entry::getKey)
    .orElse("none");
logger.info("Most valuable to relax: " + mostValuable);
```

---

## FlowRateOptimizer Integration

The `ProcessOptimizationEngine` integrates with `FlowRateOptimizer` for advanced lift curve generation.

### Creating FlowRateOptimizer

<!-- optimization-example: 39 -->
```java
import neqsim.process.util.optimizer.FlowRateOptimizer;

// FlowRateOptimizer matches a receiving-pressure target using an actual pressure-loss model.
// For the fixed-discharge compressor fixture, use throughput constraints in the engine instead.
FlowRateOptimizer optimizer = engine.createFlowRateOptimizer();
optimizer.setMinFlowRate(10000.0);
optimizer.setMaxFlowRate(200000.0);
logger.info("Flow optimizer configured; attach a pressure-loss model before a hydraulic search");
```

### Comprehensive Lift Curve Generation

<!-- optimization-example: 40 -->
```java
import neqsim.process.util.optimizer.FlowRateOptimizer;

// Use the fully constructed hydraulic example in the Flow Rate Optimization guide.
// Here a compressor pressure sweep is performed with the engine's explicit flow bounds.
for (double inletBara : new double[] {40.0, 50.0, 60.0}) {
    ProcessOptimizationEngine.OptimizationResult point =
        engine.findMaximumThroughput(inletBara, 150.0, 10000.0, 200000.0);
    logger.info("{} bara: {} kg/hr; converged {}", inletBara, point.getOptimalValue(), point.isConverged());
}
```

---

## Configuration and Tuning

### Optimization Tolerances

<!-- optimization-example: 41 -->
```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Set convergence tolerance (default 1e-6)
engine.setTolerance(1e-4);

// Set maximum iterations (default 100)
engine.setMaxIterations(50);
```

### Strategy Priority System

When multiple strategies support the same equipment type, the one with highest priority is used:

| Strategy                      | Default Priority |
| ----------------------------- | ---------------- |
| Custom strategies             | User-defined     |
| CompressorCapacityStrategy    | 10               |
| SeparatorCapacityStrategy     | 10               |
| PumpCapacityStrategy          | 10               |
| ValveCapacityStrategy         | 10               |
| PipeCapacityStrategy          | 10               |
| HeatExchangerCapacityStrategy | 10               |

To override, create a custom strategy with higher priority:

<!-- optimization-example: 42 -->
```java
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;

CompressorCapacityStrategy namedCompressorStrategy = new CompressorCapacityStrategy() {
    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        return equipment instanceof Compressor && equipment.getName().equals("comp");
    }
};
```

---

## Troubleshooting

### Common Issues

| Issue                      | Cause                         | Solution                               |
| -------------------------- | ----------------------------- | -------------------------------------- |
| No strategy found          | Equipment type not registered | Register custom strategy               |
| Constraints return 0       | Equipment not run             | Call `equipment.run()` first           |
| Invalid utilization values | Missing design values         | Set design values in constraints       |
| VFP export fails | Missing or invalid supplied BHP data | Supply finite values with the documented dimensions |

### Debug Mode

Enable detailed logging:

<!-- optimization-example: 43 -->
```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// In log4j2.xml, set level to DEBUG for optimizer package
// <Logger name="neqsim.process.util.optimizer" level="DEBUG"/>
```

---

## See Also

- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) - Detailed constraint system documentation
- [Process Optimization Framework](index.md) - Parameter estimation and calibration
- [Multi-Objective Optimization](multi-objective-optimization) - Pareto optimization
- [Batch Studies](batch-studies) - Sensitivity analysis

---

## Version History

| Version | Date    | Changes                                                       |
| ------- | ------- | ------------------------------------------------------------- |
| 1.0     | 2026-01 | Initial release with plugin architecture                      |
| 1.1     | 2026-01 | Added driver package and operating envelope                   |
| 1.2     | 2026-01 | Added Eclipse VFP export support                              |
| 1.3     | 2026-01 | Added OptimizationResultBase unified result class             |
| 1.4     | 2026-01 | Added ProcessConstraintEvaluator with caching and sensitivity |
| 1.5     | 2026-01 | Added gradient descent optimization                           |
| 1.6     | 2026-01 | Added FlowRateOptimizer integration and shadow prices         |
