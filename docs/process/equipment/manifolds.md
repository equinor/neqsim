---
title: Manifolds
description: Documentation for manifold equipment that combines stream mixing and splitting in NeqSim.
---

# Manifolds

Documentation for manifold equipment that combines stream mixing and splitting in NeqSim.

## Table of Contents
- [Overview](#overview)
- [Manifold Class](#manifold-class)
- [Usage Examples](#usage-examples)
- [Integration Patterns](#integration-patterns)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.manifold`

A manifold is a process equipment that combines the functionality of a mixer and a splitter. It can:
- Receive multiple input streams
- Combine them into a single mixed stream
- Split the combined stream into multiple output streams

This is particularly useful for:
- Production manifolds (gathering from multiple wells)
- Distribution headers
- Subsea collection systems
- Pipeline routing hubs

| Class | Description |
|-------|-------------|
| `Manifold` | Combined mixer/splitter unit |

---

## Manifold Class

The `Manifold` class extends `ProcessEquipmentBaseClass` and contains both a `Mixer` and a `Splitter` internally.

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── Manifold
    ├── contains: Mixer
    └── contains: Splitter
```

### Key Features

- Multiple input streams (via internal Mixer)
- Multiple output streams (via internal Splitter)
- Automatic mixing before splitting
- Configurable split ratios
- Mass and energy conservation

### Constructor

```java
import neqsim.process.equipment.manifold.Manifold;

// Basic constructor
Manifold manifold = new Manifold("PM-101");
```

### Methods

| Method | Description |
|--------|-------------|
| `addStream(Stream)` | Add an input stream to the manifold |
| `getSplitStream(int index)` | Get a specific output stream by index |
| `setSplitNumber(int n)` | Set number of output streams |
| `setSplitFactors(double[])` | Set split ratios for outputs |
| `getMixer()` | Access the internal mixer |
| `getSplitter()` | Access the internal splitter |
| `setInnerHeaderDiameter(double)` | Set header pipe diameter (m) |
| `setInnerBranchDiameter(double)` | Set branch pipe diameter (m) |
| `calculateHeaderLOF()` | Calculate header Likelihood of Failure |
| `calculateBranchLOF()` | Calculate branch Likelihood of Failure |
| `calculateHeaderFRMS()` | Calculate header RMS force (N/m) |
| `getCapacityConstraints()` | Get all capacity constraints |
| `autoSize(double)` | Auto-size header and branch diameters |
| `run()` | Execute mixing then splitting |

---

## Usage Examples

### Basic Manifold Operation

```java
import neqsim.process.equipment.manifold.Manifold;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create input streams (e.g., from multiple wells)
SystemInterface well1Fluid = new SystemSrkEos(350.0, 100.0);
well1Fluid.addComponent("methane", 0.85);
well1Fluid.addComponent("ethane", 0.10);
well1Fluid.addComponent("propane", 0.05);
well1Fluid.setMixingRule("classic");

Stream well1Stream = new Stream("Well-1", well1Fluid);
well1Stream.setFlowRate(50000, "Sm3/day");

SystemInterface well2Fluid = new SystemSrkEos(340.0, 95.0);
well2Fluid.addComponent("methane", 0.82);
well2Fluid.addComponent("ethane", 0.12);
well2Fluid.addComponent("propane", 0.06);
well2Fluid.setMixingRule("classic");

Stream well2Stream = new Stream("Well-2", well2Fluid);
well2Stream.setFlowRate(75000, "Sm3/day");

// Run inlet streams
well1Stream.run();
well2Stream.run();

// Create manifold
Manifold productionManifold = new Manifold("PM-101");
productionManifold.addStream(well1Stream);
productionManifold.addStream(well2Stream);

// Configure splitting (2 outlet lines)
productionManifold.setSplitNumber(2);
productionManifold.setSplitFactors(new double[] {0.6, 0.4});

// Run manifold
productionManifold.run();

// Access output streams
Stream toSeparator1 = (Stream) productionManifold.getSplitStream(0);
Stream toSeparator2 = (Stream) productionManifold.getSplitStream(1);

System.out.println("Total inlet: " + (50000 + 75000) + " Sm3/day");
System.out.println("Outlet 1: " + toSeparator1.getFlowRate("Sm3/day") + " Sm3/day");
System.out.println("Outlet 2: " + toSeparator2.getFlowRate("Sm3/day") + " Sm3/day");
```

### Production Manifold System

```java
import neqsim.process.processmodel.ProcessSystem;

ProcessSystem facility = new ProcessSystem("Offshore Platform");

// Wells
Stream well1 = createWellStream("Well-1", 50000);
Stream well2 = createWellStream("Well-2", 45000);
Stream well3 = createWellStream("Well-3", 60000);
facility.add(well1);
facility.add(well2);
facility.add(well3);

// Production manifold
Manifold manifold = new Manifold("Production Manifold");
manifold.addStream(well1);
manifold.addStream(well2);
manifold.addStream(well3);
manifold.setSplitNumber(2);  // Two production trains
manifold.setSplitFactors(new double[] {0.5, 0.5});
facility.add(manifold);

// Train separators
Separator separator1 = new Separator("V-101");
separator1.setInletStream(manifold.getSplitStream(0));
facility.add(separator1);

Separator separator2 = new Separator("V-201");
separator2.setInletStream(manifold.getSplitStream(1));
facility.add(separator2);

// Run facility
facility.run();
```

### Accessing Internal Components

```java
Manifold manifold = new Manifold("PM-101");
manifold.addStream(stream1);
manifold.addStream(stream2);

// Access internal mixer for mixed stream properties
Mixer mixer = manifold.getMixer();
mixer.run();
Stream mixedStream = mixer.getOutletStream();
System.out.println("Mixed temperature: " + mixedStream.getTemperature("C") + " °C");
System.out.println("Mixed pressure: " + mixedStream.getPressure("bara") + " bara");

// Access internal splitter
Splitter splitter = manifold.getSplitter();
```

---

## Integration Patterns

### Well Gathering System

```
Well-1 ──┐
         │
Well-2 ──┼──► [Manifold] ──┬──► Train A
         │                 │
Well-3 ──┘                 └──► Train B
```

### Load Balancing

```java
// Distribute load evenly across processing trains
int numTrains = 3;
double[] splitFactors = new double[numTrains];
for (int i = 0; i < numTrains; i++) {
    splitFactors[i] = 1.0 / numTrains;
}
manifold.setSplitFactors(splitFactors);
```

### Dynamic Rerouting

```java
// Redirect all flow to Train A (e.g., Train B offline)
manifold.setSplitFactors(new double[] {1.0, 0.0});
manifold.run();
```

---

## Flow-Induced Vibration (FIV) Analysis

The `Manifold` class provides FIV analysis for both header and branch piping, implementing `CapacityConstrainedEquipment`.

### FIV Methods

```java
Manifold manifold = new Manifold("Production Manifold", inlet1, inlet2);
manifold.setInnerHeaderDiameter(0.3);  // 12 inch header
manifold.setInnerBranchDiameter(0.15); // 6 inch branches
manifold.setMaxDesignVelocity(15.0);   // m/s
manifold.run();

// Header FIV analysis
double headerLOF = manifold.calculateHeaderLOF();
double headerFRMS = manifold.calculateHeaderFRMS();

// Branch FIV analysis
double branchLOF = manifold.calculateBranchLOF();

// Velocities
double headerVelocity = manifold.getHeaderVelocity();
double branchVelocity = manifold.getAverageBranchVelocity();
```

### Capacity Constraints

The manifold provides these constraints:

| Constraint | Type | Description |
|------------|------|-------------|
| `headerVelocity` | DESIGN | Header velocity vs erosional limit |
| `branchVelocity` | DESIGN | Branch velocity vs erosional limit |
| `headerLOF` | SOFT | Header Likelihood of Failure |
| `headerFRMS` | SOFT | Header RMS force per meter |
| `branchLOF` | SOFT | Branch Likelihood of Failure |

```java
// Get all constraints
Map<String, CapacityConstraint> constraints = manifold.getCapacityConstraints();

// Check bottleneck
CapacityConstraint bottleneck = manifold.getBottleneckConstraint();
System.out.println("Bottleneck: " + bottleneck.getName() + 
                   " at " + bottleneck.getUtilizationPercent() + "%");
```

### AutoSizing

```java
// Auto-size header and branch diameters
manifold.autoSize(1.2);  // 20% safety factor

// Check sizing report
System.out.println(manifold.getSizingReport());
```

For detailed FIV documentation, see [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md#flow-induced-vibration-fiv-analysis).

---

## Design Considerations

### Pressure Matching
Input streams should have similar pressures. If pressures differ significantly, use chokes or control valves upstream.

### Temperature Mixing
The manifold performs adiabatic mixing. The outlet temperature is calculated from energy balance.

### Composition
The mixed composition is the flow-weighted average of all inlet compositions.

---

## Comparison with Mixer/Splitter

| Aspect | Manifold | Mixer + Splitter |
|--------|----------|------------------|
| Construction | Single equipment | Two separate units |
| Modeling | Combined unit | Sequential execution |
| Use Case | Production headers | General mixing/splitting |
| Intermediate Access | Via getMixer()/getSplitter() | Direct access |

---

## Related Documentation

- [Mixers and Splitters](mixers_splitters.md) - Stream mixing and splitting
- [Streams](streams.md) - Process streams
- [Subsea Systems](subsea_systems.md) - Subsea manifold applications
- [Manifold Mechanical Design](manifold_design.md) - Detailed manifold mechanical design
- [Capacity Constraint Framework](../CAPACITY_CONSTRAINT_FRAMEWORK.md) - Capacity limits and FIV analysis
- [Pipelines](pipelines.md) - Pipeline equipment with FIV support
