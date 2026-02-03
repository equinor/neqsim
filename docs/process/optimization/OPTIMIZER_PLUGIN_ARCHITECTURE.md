---
title: Optimizer Plugin Architecture
description: The Optimizer Plugin Architecture provides a flexible, extensible framework for evaluating equipment capacity constraints and optimizing process throughput. It enables automated bottleneck detection, ...
---

# Optimizer Plugin Architecture

> **New to process optimization?** Start with the [Optimization Overview](OPTIMIZATION_OVERVIEW) to understand when to use which optimizer.

## Overview

The Optimizer Plugin Architecture provides a flexible, extensible framework for evaluating equipment capacity constraints and optimizing process throughput. It enables automated bottleneck detection, lift curve generation, and integration with reservoir simulators like Eclipse.

### Related Documentation

| Document | Description |
|----------|-------------|
| [Optimization Overview](OPTIMIZATION_OVERVIEW) | When to use which optimizer |
| [Production Optimization Guide](../../examples/PRODUCTION_OPTIMIZATION_GUIDE) | ProductionOptimizer examples |
| [Multi-Objective Optimization](multi-objective-optimization) | Pareto fronts and trade-offs |
| [Flow Rate Optimization](flow-rate-optimization) | FlowRateOptimizer and lift curves |
| [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) | Equipment constraints |

### Key Components

| Component | Description | Location |
|-----------|-------------|----------|
| **EquipmentCapacityStrategy** | Interface for equipment-specific constraint evaluation | `neqsim.process.equipment.capacity` |
| **EquipmentCapacityStrategyRegistry** | Singleton registry with auto-discovery | `neqsim.process.equipment.capacity` |
| **ProcessOptimizationEngine** | Unified API for process optimization | `neqsim.process.util.optimizer` |
| **EclipseVFPExporter** | Eclipse VFP table generation | `neqsim.process.util.optimizer` |
| **Driver Package** | Driver curves for compressors | `neqsim.process.equipment.compressor.driver` |
| **OperatingEnvelope** | Compressor operating envelope tracking | `neqsim.process.equipment.compressor` |

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
│  │   VFPPROD       │ │    VFPINJ       │ │    VFPEXP       │                │
│  │  (Production)   │ │   (Injection)   │ │    (Export)     │                │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘                │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Quick Start

### Basic Usage: Evaluate Process Constraints

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemSrkEos;

// Create process
SystemInterface gas = new SystemSrkEos(288.15, 50.0);
gas.addComponent("methane", 0.85);
gas.addComponent("ethane", 0.10);
gas.addComponent("propane", 0.05);
gas.setMixingRule("classic");

Stream feed = new Stream("feed", gas);
feed.setFlowRate(100000, "kg/hr");
feed.setPressure(50.0, "bara");
feed.setTemperature(288.15, "K");

Separator separator = new Separator("HP Separator", feed);
Compressor compressor = new Compressor("Export Compressor", separator.getGasOutStream());
compressor.setOutletPressure(120.0);

ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(separator);
process.add(compressor);
process.run();

// Create optimization engine
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Evaluate all constraints
ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();

// Print equipment status
for (ProcessOptimizationEngine.EquipmentConstraintStatus status : report.getEquipmentStatuses()) {
    System.out.println(status.getEquipmentName() + ": " + 
        String.format("%.1f%%", status.getUtilization() * 100) + " utilization");
    if (!status.isWithinLimits()) {
        System.out.println("  WARNING: Exceeds limits!");
    }
}

// Find bottleneck
String bottleneck = engine.findBottleneckEquipment();
System.out.println("Bottleneck equipment: " + bottleneck);
```

### Find Maximum Throughput

```java
// Find maximum flow rate for given pressure constraints
double inletPressure = 50.0;  // bara
double outletPressure = 40.0; // bara
double minFlow = 10000.0;     // kg/hr
double maxFlow = 500000.0;    // kg/hr

ProcessOptimizationEngine.OptimizationResult result = 
    engine.findMaximumThroughput(inletPressure, outletPressure, minFlow, maxFlow);

System.out.println("Optimal flow rate: " + result.getOptimalFlowRate() + " kg/hr");
System.out.println("Feasible: " + result.isFeasible());
System.out.println("Bottleneck: " + result.getBottleneckEquipment());
System.out.println("Total power: " + result.getTotalPower() + " kW");

// Get constraint violations if any
for (String violation : result.getConstraintViolations()) {
    System.out.println("  Violation: " + violation);
}
```

---

## Equipment Capacity Strategies

### Strategy Interface

Each equipment type has a dedicated strategy that understands its specific constraints:

```java
public interface EquipmentCapacityStrategy {
    // Check if strategy supports this equipment
    boolean supports(ProcessEquipmentInterface equipment);
    
    // Get strategy priority (higher = more specific)
    int getPriority();
    
    // Evaluate current capacity utilization (0.0 to 1.0+)
    double evaluateCapacity(ProcessEquipmentInterface equipment);
    
    // Get all constraints for equipment
    Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment);
    
    // Get violated constraints
    List<CapacityConstraint> getViolations(ProcessEquipmentInterface equipment);
    
    // Get the limiting constraint
    CapacityConstraint getBottleneckConstraint(ProcessEquipmentInterface equipment);
    
    // Check if within hard limits (safety)
    boolean isWithinHardLimits(ProcessEquipmentInterface equipment);
    
    // Check if within soft limits (design)
    boolean isWithinSoftLimits(ProcessEquipmentInterface equipment);
}
```

### Built-in Strategies

#### 1. CompressorCapacityStrategy

Evaluates compressor constraints including:

| Constraint | Type | Description |
|------------|------|-------------|
| `speed` | HARD | Rotational speed vs max/min limits |
| `power` | HARD | Shaft power vs driver capacity |
| `surgeMargin` | HARD | Distance to surge line |
| `stonewallMargin` | SOFT | Distance to stonewall |
| `dischargeTemperature` | HARD | Outlet temperature vs limits |

```java
import neqsim.process.equipment.capacity.CompressorCapacityStrategy;

// Create with custom limits
CompressorCapacityStrategy strategy = new CompressorCapacityStrategy(
    0.10,    // minSurgeMargin (10%)
    0.05,    // minStonewallMargin (5%)
    200.0    // maxDischargeTemp (°C)
);

// Evaluate compressor
Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);

// Check surge margin
CapacityConstraint surgeConstraint = constraints.get("surgeMargin");
if (surgeConstraint != null) {
    System.out.println("Surge margin: " + surgeConstraint.getCurrentValue() + "%");
    System.out.println("Minimum required: " + surgeConstraint.getMinValue() + "%");
}
```

#### 2. SeparatorCapacityStrategy

Evaluates separator constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `liquidLevel` | SOFT | Liquid level vs max allowed |
| `gasLoadFactor` | SOFT | Gas velocity/terminal velocity ratio |

```java
import neqsim.process.equipment.capacity.SeparatorCapacityStrategy;

SeparatorCapacityStrategy strategy = new SeparatorCapacityStrategy(
    0.80,  // maxLiquidLevel (80%)
    0.10   // maxGasLoadFactor (K-factor)
);

Map<String, CapacityConstraint> constraints = strategy.getConstraints(separator);
```

#### 3. PumpCapacityStrategy

Evaluates pump constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `power` | HARD | Motor power vs rating |
| `npshMargin` | HARD | NPSH available - required |
| `flowRate` | SOFT | Flow vs minimum flow |

```java
import neqsim.process.equipment.capacity.PumpCapacityStrategy;

PumpCapacityStrategy strategy = new PumpCapacityStrategy(
    1.0,   // minNpshMargin (1.0 m)
    1.1    // maxPowerFactor (110% overload allowed)
);
```

#### 4. ValveCapacityStrategy

Evaluates valve constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `valveOpening` | SOFT | Opening % vs min/max range |
| `pressureDropRatio` | SOFT | ΔP/inlet pressure ratio |

#### 5. PipeCapacityStrategy

Evaluates pipe/pipeline constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `velocity` | SOFT | Superficial velocity vs erosional |
| `pressureDrop` | SOFT | Pressure drop vs allowable |

#### 6. HeatExchangerCapacityStrategy

Evaluates heat exchanger constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `duty` | SOFT | Heat transfer duty vs design |
| `outletTemperature` | SOFT | Outlet temperature |

### Custom Strategy Registration

Register custom strategies for specialized equipment:

```java
import neqsim.process.equipment.capacity.EquipmentCapacityStrategyRegistry;
import neqsim.process.equipment.capacity.EquipmentCapacityStrategy;

// Create custom strategy
public class MyCustomEquipmentStrategy implements EquipmentCapacityStrategy {
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        return equipment instanceof MyCustomEquipment;
    }
    
    @Override
    public int getPriority() {
        return 100;  // High priority for specific equipment
    }
    
    @Override
    public double evaluateCapacity(ProcessEquipmentInterface equipment) {
        MyCustomEquipment eq = (MyCustomEquipment) equipment;
        return eq.getCurrentLoad() / eq.getMaxLoad();
    }
    
    @Override
    public Map<String, CapacityConstraint> getConstraints(ProcessEquipmentInterface equipment) {
        Map<String, CapacityConstraint> constraints = new HashMap<>();
        MyCustomEquipment eq = (MyCustomEquipment) equipment;
        
        constraints.put("customConstraint", 
            new CapacityConstraint("customConstraint", "units", ConstraintType.HARD)
                .setDesignValue(100.0)
                .setMaxValue(120.0)
                .setValueSupplier(() -> eq.getCurrentValue()));
        
        return constraints;
    }
    
    // ... implement other methods
}

// Register with registry
EquipmentCapacityStrategyRegistry registry = EquipmentCapacityStrategyRegistry.getInstance();
registry.registerStrategy(new MyCustomEquipmentStrategy());
```

---

## Driver Package

The driver package provides compressor driver models with performance curves.

### DriverCurve Interface

```java
public interface DriverCurve {
    // Get available power at current conditions
    double getMaxAvailablePower();
    
    // Get rated power
    double getRatedPower();
    
    // Calculate efficiency at given load
    double getEfficiency(double loadFraction);
    
    // Calculate fuel/energy consumption
    double getFuelConsumption(double power);
    
    // Calculate speed change during transients
    double calculateSpeedChange(double currentSpeed, double targetSpeed, 
                                double power, double timeStep);
}
```

### GasTurbineDriver

Models gas turbine drivers with ambient derating:

```java
import neqsim.process.equipment.compressor.driver.GasTurbineDriver;

// Create gas turbine driver
GasTurbineDriver driver = new GasTurbineDriver();
driver.setRatedPower(15000.0);           // 15 MW rated
driver.setRatedSpeed(10000.0);           // 10,000 RPM
driver.setRatedEfficiency(0.35);         // 35% thermal efficiency
driver.setIsoConditionsTemperature(288.15);  // ISO 15°C
driver.setIsoConditionsAltitude(0.0);    // Sea level

// Set current ambient conditions
driver.setAmbientTemperature(303.15);    // 30°C (hot day)
driver.setAltitude(500.0);               // 500m elevation

// Get derated power
double availablePower = driver.getMaxAvailablePower();
System.out.println("Available power (derated): " + availablePower + " kW");
// Output: ~13,500 kW (derated from 15,000 due to high temp and altitude)

// Calculate fuel consumption
double fuelGas = driver.getFuelConsumption(10000.0);  // At 10 MW load
System.out.println("Fuel gas: " + fuelGas + " kg/hr");
```

### ElectricMotorDriver

Models electric motor drivers with VFD support:

```java
import neqsim.process.equipment.compressor.driver.ElectricMotorDriver;

// Create electric motor
ElectricMotorDriver motor = new ElectricMotorDriver();
motor.setRatedPower(5000.0);         // 5 MW
motor.setRatedSpeed(3000.0);         // 3000 RPM (2-pole, 50 Hz)
motor.setRatedEfficiency(0.96);      // 96% efficiency
motor.setVariableSpeedDrive(true);   // VFD installed
motor.setMinSpeed(600.0);            // 20% min speed with VFD
motor.setMaxSpeed(3600.0);           // 120% max speed

// Get efficiency at partial load
double efficiency = motor.getEfficiency(0.75);  // 75% load
System.out.println("Efficiency at 75% load: " + efficiency * 100 + "%");
```

### SteamTurbineDriver

Models steam turbine drivers with Willans line:

```java
import neqsim.process.equipment.compressor.driver.SteamTurbineDriver;

SteamTurbineDriver turbine = new SteamTurbineDriver();
turbine.setRatedPower(8000.0);              // 8 MW
turbine.setInletPressure(40.0);             // 40 bara steam
turbine.setInletTemperature(673.15);        // 400°C superheat
turbine.setExhaustPressure(4.0);            // 4 bara exhaust
turbine.setIsentropicEfficiency(0.78);      // 78% isentropic efficiency

// Calculate steam consumption
double steamFlow = turbine.getSteamConsumption(6000.0);  // At 6 MW
System.out.println("Steam consumption: " + steamFlow + " kg/hr");
```

---

## Compressor Operating Envelope

Track and validate compressor operation against surge/stonewall limits:

```java
import neqsim.process.equipment.compressor.OperatingEnvelope;

// Create envelope from compressor map data
OperatingEnvelope envelope = new OperatingEnvelope();

// Add surge line points (flow, head)
envelope.addSurgePoint(500.0, 80000.0);
envelope.addSurgePoint(700.0, 100000.0);
envelope.addSurgePoint(900.0, 115000.0);
envelope.addSurgePoint(1100.0, 125000.0);

// Add stonewall line points
envelope.addStonewallPoint(1800.0, 60000.0);
envelope.addStonewallPoint(2200.0, 80000.0);
envelope.addStonewallPoint(2600.0, 95000.0);
envelope.addStonewallPoint(3000.0, 105000.0);

// Set speed limits
envelope.setMinSpeed(7000.0);   // RPM
envelope.setMaxSpeed(11000.0);  // RPM

// Check operating point
double flow = 1200.0;   // Am3/hr
double head = 95000.0;  // J/kg
double speed = 9500.0;  // RPM

boolean withinEnvelope = envelope.isWithinEnvelope(flow, head, speed);
double surgeMargin = envelope.getSurgeMargin(flow, head);
double stonewallMargin = envelope.getStonewallMargin(flow, head);

System.out.println("Within envelope: " + withinEnvelope);
System.out.println("Surge margin: " + surgeMargin * 100 + "%");
System.out.println("Stonewall margin: " + stonewallMargin * 100 + "%");

// Get limiting constraint
String limitingConstraint = envelope.getLimitingConstraint(flow, head, speed);
System.out.println("Limiting: " + limitingConstraint);
```

---

## Compressor Constraint Configuration

Configure comprehensive compressor constraints:

```java
import neqsim.process.equipment.compressor.CompressorConstraintConfig;

// Create configuration
CompressorConstraintConfig config = new CompressorConstraintConfig();

// Surge/stonewall margins
config.setMinSurgeMargin(0.10);        // 10% minimum surge margin
config.setMinStonewallMargin(0.05);    // 5% minimum stonewall margin

// Speed limits
config.setMinSpeed(5000.0);            // Minimum RPM
config.setMaxSpeed(11000.0);           // Maximum RPM

// Power limits
config.setMaxPower(15000.0);           // kW max shaft power

// Temperature limits
config.setMaxDischargeTemperature(200.0);  // °C
config.setMaxSuctionTemperature(60.0);     // °C

// API 617 compliance
config.setApi617Compliant(true);

// Use factory methods for standard configurations
CompressorConstraintConfig conservative = CompressorConstraintConfig.createConservativeConfig();
CompressorConstraintConfig aggressive = CompressorConstraintConfig.createAggressiveConfig();
CompressorConstraintConfig api617 = CompressorConstraintConfig.createAPI617Config();
```

---

## Eclipse VFP Export

Generate VFP tables for reservoir simulation. **Capacity constraints directly affect the maximum flow rates in VFP tables**.

> **📘 See Also:** [Capacity Constraint Framework - VFP Section](../CAPACITY_CONSTRAINT_FRAMEWORK#constraints-in-eclipse-vfp-table-generation) for detailed documentation on constraint management for VFP studies.

### How Constraints Affect VFP Tables

When generating VFP tables, the optimizer finds the **maximum flow rate** at each operating point where:
1. Process converges thermodynamically
2. **All capacity constraints are satisfied** (utilization ≤ 100%)
3. No HARD limits are exceeded

```
Operating Point (Pin, Pout, WC, GOR) → Binary Search → Max Flow Rate
                                            ↓
                               Check ALL equipment constraints
                                            ↓
                               Bottleneck determines limit
```

### Constraint Configuration for VFP

```java
import neqsim.process.util.optimizer.EclipseVFPExporter;

// Create process with constrained equipment
ProcessSystem process = createProcess();

// Configure constraints BEFORE generating VFP
Compressor comp = (Compressor) process.getUnit("Export Compressor");
comp.autoSize(1.2);  // Sets speed, power, surge constraints

Separator sep = (Separator) process.getUnit("HP Separator");
sep.autoSize(1.2);   // Sets gasLoadFactor constraint

// Optionally modify constraints for study
CapacityConstraint speedLimit = comp.getCapacityConstraints().get("speed");
speedLimit.setDesignValue(9000.0);  // More conservative speed limit

// Create exporter - will respect all active constraints
EclipseVFPExporter exporter = new EclipseVFPExporter(process);
exporter.setTableNumber(1);
exporter.setConstraintEnforcement(true);  // Enable constraint checking (default: true)
```

### VFPPROD Tables (Production Wells)

```java
// Define parameter ranges
double[] thp = {20.0, 30.0, 40.0, 50.0};           // Tubing head pressures (bara)
double[] wfr = {0.0, 0.1, 0.3, 0.5};                // Water fractions
double[] gfr = {100.0, 200.0, 500.0, 1000.0};       // GOR (Sm3/Sm3)
double[] alq = {0.0};                               // Artificial lift (none)
double[] flowRates = {1000.0, 5000.0, 10000.0, 20000.0, 50000.0};  // kg/hr

// Generate VFPPROD table - max flow at each point limited by constraints
String vfpTable = exporter.generateVFPPROD(
    thp, wfr, gfr, alq, flowRates,
    "bara", "kg/hr"
);

// Write to file
Files.writeString(Path.of("VFPPROD_WELL1.INC"), vfpTable);
```

### VFPINJ Tables (Injection Wells)

```java
// Generate VFPINJ table for water injection
double[] injPressures = {100.0, 150.0, 200.0, 250.0};  // BHP
double[] injRates = {5000.0, 10000.0, 20000.0};        // m3/day

String vfpInj = exporter.generateVFPINJ(
    thp, injPressures, injRates,
    "bara", "m3/day"
);
```

### VFPEXP Tables (Export Pipelines)

```java
// Generate VFPEXP for export pipeline
double[] inletPressures = {50.0, 60.0, 70.0, 80.0};
double[] outletPressures = {40.0, 45.0, 50.0};
double[] temperatures = {20.0, 40.0, 60.0};

String vfpExp = exporter.generateVFPEXP(
    inletPressures, outletPressures, temperatures, flowRates,
    "bara", "C", "kg/hr"
);
```

### What-If Studies: Modifying Constraints for VFP Scenarios

```java
// Scenario 1: Baseline VFP with current constraints
String baselineVFP = exporter.generateVFPPROD(thp, wfr, gfr, alq, flowRates, "bara", "kg/hr");
Files.writeString(Path.of("VFP_BASELINE.INC"), baselineVFP);

// Scenario 2: Debottleneck compressor (increase speed limit)
CapacityConstraint speedConstraint = comp.getCapacityConstraints().get("speed");
double originalSpeed = speedConstraint.getDesignValue();
speedConstraint.setDesignValue(originalSpeed * 1.1);  // 10% higher

String debottleneckedVFP = exporter.generateVFPPROD(thp, wfr, gfr, alq, flowRates, "bara", "kg/hr");
Files.writeString(Path.of("VFP_DEBOTTLENECKED.INC"), debottleneckedVFP);

speedConstraint.setDesignValue(originalSpeed);  // Restore

// Scenario 3: No equipment constraints (thermodynamic limits only)
comp.clearCapacityConstraints();
sep.clearCapacityConstraints();

String unconstrainedVFP = exporter.generateVFPPROD(thp, wfr, gfr, alq, flowRates, "bara", "kg/hr");
Files.writeString(Path.of("VFP_UNCONSTRAINED.INC"), unconstrainedVFP);

// Restore constraints
comp.initializeCapacityConstraints();
sep.initializeCapacityConstraints();
```

### VFP Generation with Bottleneck Reporting

```java
// Generate VFP with detailed bottleneck information
VFPGenerationResult result = exporter.generateVFPPRODWithDetails(
    thp, wfr, gfr, alq, flowRates, "bara", "kg/hr");

// Access VFP table
String vfpTable = result.getVFPTable();

// Access bottleneck analysis
for (VFPPoint point : result.getPoints()) {
    if (point.isConstrained()) {
        System.out.printf("At THP=%.0f, WC=%.1f, GOR=%.0f: " +
                          "Max=%.0f kg/hr, Limited by %s (%s)%n",
            point.getTHP(), point.getWaterCut(), point.getGOR(),
            point.getMaxFlowRate(),
            point.getBottleneckEquipment(),
            point.getBottleneckConstraint());
    }
}
```

---

## ProcessOptimizationEngine API Reference

### Constructors

```java
// For ProcessSystem
public ProcessOptimizationEngine(ProcessSystem processSystem)

// For ProcessModule (supports nested modules)
public ProcessOptimizationEngine(ProcessModule processModule)
```

Creates optimization engine for the given process system or module.

### ProcessModule Support

The `ProcessOptimizationEngine` fully supports `ProcessModule`, which can contain multiple `ProcessSystem` instances and nested modules. All optimization methods work recursively across the entire module hierarchy.

```java
import neqsim.process.util.optimizer.ProcessOptimizationEngine;
import neqsim.process.processmodel.ProcessModule;
import neqsim.process.processmodel.ProcessSystem;

// Create a module with multiple systems
ProcessModule fieldModule = new ProcessModule("Field Development");

ProcessSystem subseaSystem = new ProcessSystem();
// ... add subsea equipment ...
fieldModule.add(subseaSystem);

ProcessSystem topsideSystem = new ProcessSystem();
// ... add topside equipment ...
fieldModule.add(topsideSystem);

fieldModule.run();

// Create engine with ProcessModule
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(fieldModule);

// Specify which feed stream to vary (searches across ALL systems in module)
engine.setFeedStreamName("WellheadFeed");

// Find maximum throughput - evaluates constraints across entire module
OptimizationResult result = engine.findMaximumThroughput(
    50.0,      // inlet pressure (bara)
    40.0,      // outlet pressure (bara)
    10000.0,   // min flow (kg/hr)
    500000.0   // max flow (kg/hr)
);

// Check which stream is being varied
System.out.println("Feed stream: " + engine.getFeedStreamName());
```

### Feed Stream Configuration

By default, the optimization engine varies the **first unit operation** in the process. For complex processes or modules, you should explicitly specify the feed stream:

| Method | Description |
|--------|-------------|
| `setFeedStreamName(String name)` | Set the name of the stream to vary during optimization |
| `getFeedStreamName()` | Get the name of the stream being varied |

```java
// Explicitly set which stream to vary
engine.setFeedStreamName("InletManifold");

// Method chaining is supported
OptimizationResult result = engine
    .setFeedStreamName("WellStream")
    .findMaximumThroughput(50.0, 40.0, 1000.0, 100000.0);

// Verify which stream is being used
System.out.println("Optimizing flow rate of: " + engine.getFeedStreamName());
```

### Outlet Stream Configuration

By default, the optimization engine monitors the **last unit operation** for outlet conditions. For complex processes or modules, you can explicitly specify the outlet stream:

| Method | Description |
|--------|-------------|
| `setOutletStreamName(String name)` | Set the name of the outlet stream to monitor |
| `getOutletStreamName()` | Get the name of the outlet stream being monitored |
| `getOutletTemperature()` | Get outlet temperature in Kelvin |
| `getOutletTemperature(String unit)` | Get outlet temperature in specified unit ("C", "K", "F", "R") |
| `getOutletFlowRate(String flowUnit)` | Get outlet flow rate in specified unit ("kg/hr", "MSm3/day") |

```java
// Configure both feed and outlet streams
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(fieldModule);
engine.setFeedStreamName("Well Feed");        // Input stream to vary
engine.setOutletStreamName("Export Gas");     // Output stream to monitor

// Run optimization
OptimizationResult result = engine.findMaximumThroughput(50.0, 40.0, 1000.0, 100000.0);

// Get outlet conditions from the specified stream
double outletTemp = engine.getOutletTemperature("C");
double outletFlow = engine.getOutletFlowRate("MSm3/day");
System.out.println("Export temperature: " + outletTemp + " °C");
System.out.println("Export flow rate: " + outletFlow + " MSm3/day");
```

#### ProcessModule Example with Feed and Outlet Streams

```java
// Module with multiple process systems
ProcessModule facilityModule = new ProcessModule("Offshore Facility");

// Subsea system
ProcessSystem subseaSystem = new ProcessSystem("Subsea");
subseaSystem.add(new Stream("Wellhead Feed", wellFluid));
subseaSystem.add(new AdiabaticPipe("Flowline", subseaSystem.getUnit("Wellhead Feed")));
facilityModule.add(subseaSystem);

// Topside system
ProcessSystem topsideSystem = new ProcessSystem("Topside");
topsideSystem.add(new Separator("HP Separator", subseaSystem.getUnit("Flowline")));
topsideSystem.add(new Compressor("Export Compressor", topsideSystem.getUnit("HP Separator")));
topsideSystem.add(new Stream("Export Gas", topsideSystem.getUnit("Export Compressor")));
facilityModule.add(topsideSystem);

facilityModule.run();

// Optimize with explicit feed/outlet
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(facilityModule);
engine.setFeedStreamName("Wellhead Feed");  // From subseaSystem
engine.setOutletStreamName("Export Gas");   // From topsideSystem

// Optimization searches across ALL systems in the module
OptimizationResult result = engine.findMaximumThroughput(85.0, 40.0, 5000.0, 200000.0);
```

### Core Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `evaluateAllConstraints()` | `ConstraintReport` | Evaluate constraints on all equipment |
| `findMaximumThroughput(pin, pout, minQ, maxQ)` | `OptimizationResult` | Find max flow for pressure constraints |
| `findRequiredInletPressure(outletP, flowRate)` | `OptimizationResult` | Find inlet pressure for target flow |
| `findBottleneckEquipment()` | `String` | Get name of bottleneck equipment |
| `generateLiftCurve(pins, pouts, temps, wcuts, gors)` | `LiftCurveData` | Generate multi-dimensional lift curve |
| `analyzeSensitivity(flow, inletP, outletP)` | `SensitivityResult` | Analyze flow sensitivity and margins |
| `calculateShadowPrices(flow, inletP, outletP)` | `Map<String, Double>` | Calculate constraint shadow prices |
| `createFlowRateOptimizer()` | `FlowRateOptimizer` | Create integrated FlowRateOptimizer |
| `generateComprehensiveLiftCurve(stream, pressures, outletP)` | `FlowRateOptimizer` | Generate lift curves via FlowRateOptimizer |
| `evaluateConstraintsWithCache()` | `ConstraintEvaluationResult` | Evaluate with caching enabled |
| `calculateFlowSensitivities(flow, unit)` | `Map<String, Double>` | Calculate flow sensitivities by equipment |
| `estimateMaximumFlow(currentFlow, unit)` | `double` | Estimate max feasible flow |
| `getConstraintEvaluator()` | `ProcessConstraintEvaluator` | Get underlying constraint evaluator |

### OptimizationResult Class

```java
public class OptimizationResult {
    double getOptimalFlowRate();      // Optimal flow in kg/hr
    boolean isFeasible();              // True if constraints satisfied
    String getBottleneckEquipment();   // Name of limiting equipment
    double getTotalPower();            // Total power consumption (kW)
    List<String> getConstraintViolations();  // List of violations
}
```

### ConstraintReport Class

```java
public class ConstraintReport {
    List<EquipmentConstraintStatus> getEquipmentStatuses();
    boolean hasViolations();
    String getBottleneckEquipment();
    double getOverallUtilization();
}
```

### EquipmentConstraintStatus Class

```java
public class EquipmentConstraintStatus {
    String getEquipmentName();
    String getEquipmentType();
    double getUtilization();           // 0.0 to 1.0+
    boolean isWithinLimits();
    String getBottleneckConstraint();  // Name of limiting constraint
    List<CapacityConstraint> getConstraints();
}
```

---

## Integration Examples

### Example 1: Production Optimization with Constraints

```java
// Complete production optimization example
public class ProductionOptimizationExample {
    public static void main(String[] args) {
        // Create wellstream fluid
        SystemInterface wellFluid = new SystemSrkEos(330.0, 80.0);
        wellFluid.addComponent("methane", 0.70);
        wellFluid.addComponent("ethane", 0.08);
        wellFluid.addComponent("propane", 0.05);
        wellFluid.addComponent("n-butane", 0.03);
        wellFluid.addComponent("n-pentane", 0.02);
        wellFluid.addComponent("nC10", 0.07);
        wellFluid.addComponent("water", 0.05);
        wellFluid.setMixingRule("classic");
        wellFluid.setMultiPhaseCheck(true);
        
        // Create process
        Stream wellStream = new Stream("wellStream", wellFluid);
        wellStream.setFlowRate(50000, "kg/hr");
        wellStream.setPressure(80.0, "bara");
        wellStream.setTemperature(330.0, "K");
        
        ThreePhaseSeparator hpSeparator = new ThreePhaseSeparator("HP Separator", wellStream);
        
        Heater gasHeater = new Heater("Gas Heater", hpSeparator.getGasOutStream());
        gasHeater.setOutTemperature(320.0);
        
        Compressor exportCompressor = new Compressor("Export Compressor", gasHeater.getOutletStream());
        exportCompressor.setOutletPressure(150.0);
        exportCompressor.setPolytropicEfficiency(0.78);
        
        Cooler aftercooler = new Cooler("Aftercooler", exportCompressor.getOutletStream());
        aftercooler.setOutTemperature(313.15);
        
        ProcessSystem process = new ProcessSystem();
        process.add(wellStream);
        process.add(hpSeparator);
        process.add(gasHeater);
        process.add(exportCompressor);
        process.add(aftercooler);
        process.run();
        
        // Create optimization engine
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        
        // Evaluate current constraints
        ProcessOptimizationEngine.ConstraintReport report = engine.evaluateAllConstraints();
        
        System.out.println("=== Current Operating Status ===");
        for (ProcessOptimizationEngine.EquipmentConstraintStatus status : report.getEquipmentStatuses()) {
            System.out.printf("%s: %.1f%% utilization%n", 
                status.getEquipmentName(), status.getUtilization() * 100);
            
            for (CapacityConstraint constraint : status.getConstraints()) {
                System.out.printf("  - %s: %.2f %s (%.1f%% of design)%n",
                    constraint.getName(),
                    constraint.getCurrentValue(),
                    constraint.getUnit(),
                    constraint.getUtilizationPercent());
            }
        }
        
        // Find maximum throughput
        System.out.println("\n=== Optimization Results ===");
        ProcessOptimizationEngine.OptimizationResult result = 
            engine.findMaximumThroughput(80.0, 150.0, 10000.0, 200000.0);
        
        System.out.printf("Maximum throughput: %.0f kg/hr%n", result.getOptimalFlowRate());
        System.out.printf("Bottleneck: %s%n", result.getBottleneckEquipment());
        System.out.printf("Total power: %.1f kW%n", result.getTotalPower());
        
        if (!result.getConstraintViolations().isEmpty()) {
            System.out.println("Constraint violations at max rate:");
            for (String violation : result.getConstraintViolations()) {
                System.out.println("  - " + violation);
            }
        }
    }
}
```

### Example 2: Lift Curve Generation for Eclipse

```java
// Generate lift curves for reservoir simulation
public class LiftCurveExample {
    public static void main(String[] args) {
        // ... create process as above ...
        
        ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);
        
        // Define parameter ranges for lift curve
        double[] wellheadPressures = {30.0, 40.0, 50.0, 60.0, 70.0, 80.0};
        double[] separatorPressures = {20.0, 25.0, 30.0};
        double[] temperatures = {20.0, 40.0, 60.0};
        double[] waterCuts = {0.0, 0.1, 0.3, 0.5};
        double[] gors = {100.0, 200.0, 500.0};
        
        // Generate lift curve data
        ProcessOptimizationEngine.LiftCurveData liftCurve = 
            engine.generateLiftCurve(
                wellheadPressures, 
                separatorPressures, 
                temperatures, 
                waterCuts, 
                gors
            );
        
        // Export to Eclipse format
        EclipseVFPExporter exporter = new EclipseVFPExporter(process);
        exporter.setTableNumber(1);
        
        String vfpTable = exporter.generateVFPPROD(
            wellheadPressures,
            waterCuts,
            gors,
            new double[]{0.0},  // No artificial lift
            new double[]{10000, 20000, 50000, 100000, 150000},
            "bara",
            "kg/hr"
        );
        
        // Save to file
        Files.writeString(Path.of("VFPPROD_TABLE1.INC"), vfpTable);
        System.out.println("VFP table written to VFPPROD_TABLE1.INC");
    }
}
```

### Example 3: Using Strategy Registry Directly

```java
// Direct strategy usage for custom analysis
public class StrategyUsageExample {
    public static void main(String[] args) {
        // Get registry singleton
        EquipmentCapacityStrategyRegistry registry = 
            EquipmentCapacityStrategyRegistry.getInstance();
        
        // Find strategy for specific equipment
        Compressor compressor = new Compressor("test", feedStream);
        compressor.setOutletPressure(100.0);
        compressor.run();
        
        EquipmentCapacityStrategy strategy = registry.findStrategy(compressor);
        
        if (strategy != null) {
            // Get all constraints
            Map<String, CapacityConstraint> constraints = strategy.getConstraints(compressor);
            
            System.out.println("Compressor Constraints:");
            for (Map.Entry<String, CapacityConstraint> entry : constraints.entrySet()) {
                CapacityConstraint c = entry.getValue();
                System.out.printf("  %s: %.2f / %.2f %s (%.1f%%)%n",
                    c.getName(),
                    c.getCurrentValue(),
                    c.getDesignValue(),
                    c.getUnit(),
                    c.getUtilizationPercent());
            }
            
            // Check for violations
            List<CapacityConstraint> violations = strategy.getViolations(compressor);
            if (!violations.isEmpty()) {
                System.out.println("\nViolations:");
                for (CapacityConstraint v : violations) {
                    System.out.printf("  %s: %.2f exceeds %.2f %s%n",
                        v.getName(), v.getCurrentValue(), v.getDesignValue(), v.getUnit());
                }
            }
            
            // Get bottleneck
            CapacityConstraint bottleneck = strategy.getBottleneckConstraint(compressor);
            if (bottleneck != null) {
                System.out.println("\nBottleneck constraint: " + bottleneck.getName());
            }
        }
    }
}
```

---

## Unified Result Classes

### OptimizationResultBase

The `OptimizationResultBase` class provides a unified structure for all optimization results:

```java
import neqsim.process.util.optimizer.OptimizationResultBase;

// Create result and track optimization
OptimizationResultBase result = new OptimizationResultBase();
result.markStart();  // Start timing
result.setObjective("MaxThroughput");

// During optimization
for (int i = 0; i < maxIterations; i++) {
    result.incrementIterations();
    result.incrementFunctionEvaluations();
    // ... optimization logic ...
}

// Record results
result.setOptimalValue(5500.0);
result.addOptimalValue("FlowRate", 5500.0);
result.setObjectiveValue(5500.0);
result.setBottleneckEquipment("Compressor1");
result.setBottleneckConstraint("MaxPower");
result.setConverged(true);
result.markEnd();  // End timing

// Get summary
System.out.println(result.getSummary());
System.out.println("Elapsed time: " + result.getElapsedTimeSeconds() + " s");
```

### Status Enum

The `Status` enum tracks optimization state:

| Status | Description |
|--------|-------------|
| `NOT_STARTED` | Optimization not yet begun |
| `IN_PROGRESS` | Currently running |
| `CONVERGED` | Successfully converged |
| `MAX_ITERATIONS_REACHED` | Hit iteration limit |
| `INFEASIBLE` | No feasible solution found |
| `FAILED` | Error during optimization |
| `CANCELLED` | User cancelled |

### ConstraintViolation Class

Track constraint violations with detailed information:

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

System.out.println("Violation: " + violation.getViolationAmount());  // 3.0 MW over
System.out.println("Percent over: " + violation.getViolationPercent() + "%");  // 25%
```

---

## ProcessConstraintEvaluator

The `ProcessConstraintEvaluator` provides composite constraint evaluation with caching and sensitivity analysis.

### Basic Usage

```java
import neqsim.process.util.optimizer.ProcessConstraintEvaluator;

// Create evaluator
ProcessConstraintEvaluator evaluator = new ProcessConstraintEvaluator(processSystem);

// Evaluate all constraints
ProcessConstraintEvaluator.ConstraintEvaluationResult result = evaluator.evaluate();

System.out.println("Overall utilization: " + result.getOverallUtilization() * 100 + "%");
System.out.println("Bottleneck: " + result.getBottleneckEquipment());
System.out.println("Feasible: " + result.isFeasible());
System.out.println("Violations: " + result.getTotalViolationCount());

// Get per-equipment summaries
for (Map.Entry<String, ProcessConstraintEvaluator.EquipmentConstraintSummary> entry : 
        result.getEquipmentSummaries().entrySet()) {
    ProcessConstraintEvaluator.EquipmentConstraintSummary summary = entry.getValue();
    System.out.printf("%s: %.1f%% utilization, margin to limit: %.1f%%%n",
        summary.getEquipmentName(),
        summary.getUtilization() * 100,
        summary.getMarginToLimit() * 100);
}
```

### Constraint Caching

Enable caching for repeated evaluations:

```java
// Configure cache TTL (default 10 seconds)
evaluator.setCacheTTLMillis(30000);  // 30 seconds

// Evaluate with caching
ProcessConstraintEvaluator.ConstraintEvaluationResult result1 = evaluator.evaluate();
// ... process runs again ...
ProcessConstraintEvaluator.ConstraintEvaluationResult result2 = evaluator.evaluate();  // Uses cache if valid

// Clear cache when needed
evaluator.clearCache();
```

### CachedConstraints Class

Manual cache management:

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

```java
// Calculate sensitivities at current operating point
Map<String, Double> sensitivities = evaluator.calculateFlowSensitivities(8000.0, "kg/hr");

for (Map.Entry<String, Double> entry : sensitivities.entrySet()) {
    System.out.printf("%s: sensitivity = %.3f (utilization change per kg/hr)%n",
        entry.getKey(), entry.getValue());
}

// Estimate maximum feasible flow
double maxFlow = evaluator.estimateMaxFlow(8000.0, "kg/hr");
System.out.println("Estimated max flow: " + maxFlow + " kg/hr");
```

---

## Gradient-Based Optimization

The `ProcessOptimizationEngine` supports gradient descent optimization for smooth objective functions.

### Search Algorithms

| Algorithm | Description | Best For |
|-----------|-------------|----------|
| `BINARY_SEARCH` | Binary search for feasibility boundary | Simple monotonic problems |
| `GOLDEN_SECTION` | Golden section search | Unimodal objectives |
| `GRADIENT_DESCENT` | Gradient descent with finite differences | Smooth multi-variable problems |

### Using Gradient Descent

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(processSystem);

// Select gradient descent algorithm
engine.setSearchAlgorithm(ProcessOptimizationEngine.SearchAlgorithm.GRADIENT_DESCENT);
engine.setTolerance(1e-4);
engine.setMaxIterations(100);
engine.setEnforceConstraints(true);

// Find maximum throughput
ProcessOptimizationEngine.OptimizationResult result = 
    engine.findMaximumThroughput(50.0, 10.0, 1000.0, 100000.0);

System.out.println("Optimal flow: " + result.getOptimalValue() + " kg/hr");
System.out.println("Converged: " + result.isConverged());
System.out.println("Iterations: " + result.getIterations());
```

### Gradient Descent Features

- **Finite-difference gradient estimation** with configurable step size
- **Adaptive step size** with line search backtracking
- **Constraint penalties** for infeasible solutions
- **Bounds enforcement** to stay within search range

---

## Sensitivity Analysis

Analyze how the optimal solution responds to parameter changes.

### SensitivityResult Class

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(processSystem);

// Analyze sensitivity at current operating point
ProcessOptimizationEngine.SensitivityResult sensitivity = 
    engine.analyzeSensitivity(5000.0, 50.0, 10.0);

System.out.println("Base flow: " + sensitivity.getBaseFlow() + " kg/hr");
System.out.println("Flow gradient: " + sensitivity.getFlowGradient());
System.out.println("Tightest constraint: " + sensitivity.getTightestConstraint());
System.out.println("Margin to limit: " + sensitivity.getTightestMargin() * 100 + "%");
System.out.println("Flow buffer: " + sensitivity.getFlowBuffer() + " kg/hr");

// Check if near capacity
if (sensitivity.isAtCapacity()) {
    System.out.println("WARNING: Operating near capacity!");
    System.out.println("Bottleneck: " + sensitivity.getBottleneckEquipment());
}

// Access constraint margins
Map<String, Double> margins = sensitivity.getConstraintMargins();
for (Map.Entry<String, Double> entry : margins.entrySet()) {
    System.out.printf("  %s: %.1f%% margin%n", entry.getKey(), entry.getValue() * 100);
}
```

### Shadow Prices

Calculate the economic value of relaxing constraints:

```java
// Calculate shadow prices (value of constraint relaxation)
Map<String, Double> shadowPrices = engine.calculateShadowPrices(5000.0, 50.0, 10.0);

System.out.println("Shadow Prices (flow increase per unit constraint relaxation):");
for (Map.Entry<String, Double> entry : shadowPrices.entrySet()) {
    if (entry.getValue() > 0) {
        System.out.printf("  %s: %.2f kg/hr per unit%n", 
            entry.getKey(), entry.getValue());
    }
}

// Identify most valuable constraint to relax
String mostValuable = shadowPrices.entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .map(Map.Entry::getKey)
    .orElse("none");
System.out.println("Most valuable to relax: " + mostValuable);
```

---

## FlowRateOptimizer Integration

The `ProcessOptimizationEngine` integrates with `FlowRateOptimizer` for advanced lift curve generation.

### Creating FlowRateOptimizer

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(processSystem);

// Create FlowRateOptimizer with auto-detected streams
FlowRateOptimizer optimizer = engine.createFlowRateOptimizer();

// Use optimizer for detailed flow rate calculations
double maxFlow = optimizer.findFlowRate(50.0, 10.0, "bara");
System.out.println("Max flow at P_in=50, P_out=10: " + maxFlow + " kg/hr");
```

### Comprehensive Lift Curve Generation

```java
// Define inlet pressure range
double[] inletPressures = {30.0, 40.0, 50.0, 60.0, 70.0, 80.0};
double outletPressure = 10.0;

// Generate comprehensive lift curves
FlowRateOptimizer liftOptimizer = 
    engine.generateComprehensiveLiftCurve("feed", inletPressures, outletPressure);

// Use the optimizer for additional calculations
for (double pin : inletPressures) {
    double flow = liftOptimizer.findFlowRate(pin, outletPressure, "bara");
    System.out.printf("P_in=%.0f bara -> Max flow = %.0f kg/hr%n", pin, flow);
}
```

---

## Configuration and Tuning

### Optimization Tolerances

```java
ProcessOptimizationEngine engine = new ProcessOptimizationEngine(process);

// Set convergence tolerance (default 1e-6)
engine.setTolerance(1e-4);

// Set maximum iterations (default 100)
engine.setMaxIterations(50);
```

### Strategy Priority System

When multiple strategies support the same equipment type, the one with highest priority is used:

| Strategy | Default Priority |
|----------|-----------------|
| Custom strategies | User-defined |
| CompressorCapacityStrategy | 10 |
| SeparatorCapacityStrategy | 10 |
| PumpCapacityStrategy | 10 |
| ValveCapacityStrategy | 10 |
| PipeCapacityStrategy | 10 |
| HeatExchangerCapacityStrategy | 10 |

To override, create a custom strategy with higher priority:

```java
public class MySpecialCompressorStrategy extends CompressorCapacityStrategy {
    @Override
    public int getPriority() {
        return 100;  // Higher than default 10
    }
    
    @Override
    public boolean supports(ProcessEquipmentInterface equipment) {
        // Only for specific compressor types
        return equipment instanceof MySpecialCompressor;
    }
}
```

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| No strategy found | Equipment type not registered | Register custom strategy |
| Constraints return 0 | Equipment not run | Call `equipment.run()` first |
| Invalid utilization values | Missing design values | Set design values in constraints |
| VFP export fails | Process not converging | Check fluid composition and conditions |

### Debug Mode

Enable detailed logging:

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// In log4j2.xml, set level to DEBUG for optimizer package
// <Logger name="neqsim.process.util.optimizer" level="DEBUG"/>
```

---

## See Also

- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK) - Detailed constraint system documentation
- [Process Optimization Framework](./\) - Parameter estimation and calibration
- [Multi-Objective Optimization](multi-objective-optimization) - Pareto optimization
- [Batch Studies](batch-studies) - Sensitivity analysis

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-01 | Initial release with plugin architecture |
| 1.1 | 2026-01 | Added driver package and operating envelope |
| 1.2 | 2026-01 | Added Eclipse VFP export support |
| 1.3 | 2026-01 | Added OptimizationResultBase unified result class |
| 1.4 | 2026-01 | Added ProcessConstraintEvaluator with caching and sensitivity |
| 1.5 | 2026-01 | Added gradient descent optimization |
| 1.6 | 2026-01 | Added FlowRateOptimizer integration and shadow prices |
