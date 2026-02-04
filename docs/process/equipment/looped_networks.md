---
title: Looped Pipeline Networks
description: Documentation for looped pipeline network modeling with Hardy Cross solver in NeqSim.
---

# Looped Pipeline Networks

Documentation for looped pipeline network modeling with Hardy Cross solver in NeqSim.

## Table of Contents
- [Overview](#overview)
- [LoopedPipeNetwork Class](#loopedpipenetwork-class)
- [Hardy Cross Method](#hardy-cross-method)
- [Network Topology](#network-topology)
- [Solver Configuration](#solver-configuration)
- [Usage Examples](#usage-examples)
- [Advanced Features](#advanced-features)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.network`

The `LoopedPipeNetwork` class extends basic pipeline network concepts to support looped configurations commonly found in:

- Water distribution systems
- Oil and gas gathering networks
- Gas transmission systems
- District heating networks

| Class | Description |
|-------|-------------|
| `LoopedPipeNetwork` | Network with loop support and Hardy Cross solver |
| `NetworkLoop` | Represents an independent loop in the network |
| `LoopDetector` | Detects loops using DFS spanning tree algorithm |

---

## LoopedPipeNetwork Class

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── LoopedPipeNetwork
    ├── contains: List<PipeNode>
    ├── contains: List<PipeSegment>
    ├── contains: List<NetworkLoop>
    └── uses: LoopDetector
```

### Key Features

- **Loop Detection**: Automatic identification of independent loops
- **Hardy Cross Solver**: Iterative flow distribution algorithm
- **Node Types**: Source, sink, and junction nodes
- **Pressure Calculation**: Node pressures from flow solution
- **Compositional Tracking**: Full compositional mass balance

### Constructor

```java
import neqsim.process.equipment.network.LoopedPipeNetwork;

// Create network
LoopedPipeNetwork network = new LoopedPipeNetwork("Distribution Network");

// Set fluid template
network.setFluidTemplate(gasFluid);
```

---

## Hardy Cross Method

### Algorithm Overview

The Hardy Cross method is an iterative technique for solving flow networks with loops:

1. **Initial Flow Estimates**: Assign flows satisfying mass balance at nodes
2. **Loop Detection**: Identify independent loops using DFS
3. **Head Loss Calculation**: For each loop, calculate head loss imbalance

$$\Delta H = \sum_{pipes \in loop} h_i \cdot sign_i$$

4. **Flow Correction**: Calculate correction for each loop

$$\Delta Q = -\frac{\Delta H}{\sum_{pipes \in loop} |dh_i/dQ_i|}$$

5. **Apply Corrections**: Update all pipes in each loop
6. **Iterate**: Repeat until convergence ($\Delta H < tolerance$)

### Head Loss Equations

For turbulent flow (Darcy-Weisbach):

$$h = f \cdot \frac{L}{D} \cdot \frac{v^2}{2g} = K \cdot Q^n$$

Where:
- $f$ = Darcy friction factor
- $L$ = Pipe length
- $D$ = Pipe diameter
- $v$ = Fluid velocity
- $n$ ≈ 2 for turbulent flow

### Convergence Criteria

```java
// Set convergence tolerance
network.setTolerance(1e-6);  // Pressure balance tolerance

// Set maximum iterations
network.setMaxIterations(100);

// Get convergence info
int iterations = network.getIterationCount();
double residual = network.getResidual();
boolean converged = network.isConverged();
```

---

## Network Topology

### Node Types

| Type | Description | Required Input |
|------|-------------|----------------|
| Source | Supplies fluid (well, compressor) | Pressure, flow rate |
| Sink | Removes fluid (customer, export) | Flow rate (demand) |
| Junction | Connection point | None |

### Adding Nodes

```java
// Add source node (supply point)
network.addSourceNode("Supply-1", 50.0, 1000.0);  // 50 bar, 1000 kg/hr

// Add sink node (demand point)
network.addSinkNode("Customer-1", 200.0);  // 200 kg/hr demand
network.addSinkNode("Customer-2", 300.0);  // 300 kg/hr demand

// Add junction node (connection point)
network.addJunctionNode("Junction-A");
network.addJunctionNode("Junction-B");
```

### Adding Pipes

```java
// Connect nodes with pipes
// addPipe(fromNode, toNode, pipeName, length, diameter)

network.addPipe("Supply-1", "Junction-A", "Pipe-1", 5000.0, 0.3);   // 5km, 12"
network.addPipe("Junction-A", "Junction-B", "Pipe-2", 3000.0, 0.25); // 3km, 10"
network.addPipe("Junction-B", "Customer-1", "Pipe-3", 2000.0, 0.15); // 2km, 6"
network.addPipe("Junction-A", "Customer-2", "Pipe-4", 4000.0, 0.2);  // 4km, 8"

// Create loop by connecting B back to A
network.addPipe("Junction-B", "Junction-A", "Pipe-5", 2500.0, 0.2);  // Loop
```

### Network Visualization

```
         [Supply-1]
              │
              ▼
        ┌─────────┐
        │ Pipe-1  │
        └────┬────┘
             │
             ▼
      ┌──────────────┐
      │  Junction-A  │◄────────┐
      └──────┬───────┘         │
             │                 │
    ┌────────┼────────┐        │
    │        │        │        │
    ▼        ▼        ▼        │
 Pipe-4   Pipe-2   Pipe-5      │
    │        │        │        │
    ▼        ▼        └────────┤
Customer-2  Junction-B ────────┘
                │
                ▼
             Pipe-3
                │
                ▼
           Customer-1
```

---

## Solver Configuration

### Solver Types

```java
// Set solver type
network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);

// Other options:
// SolverType.NEWTON_RAPHSON - Faster convergence, more complex
// SolverType.LINEAR - For small networks
```

### Solver Parameters

```java
// Convergence settings
network.setTolerance(1e-6);
network.setMaxIterations(100);

// Damping factor (0-1, helps stability)
network.setDampingFactor(0.7);

// Pressure drop model
network.setPressureDropModel("Darcy-Weisbach");  // or "Hazen-Williams"
```

### Running the Solver

```java
// Run network solution
network.run();

// Check convergence
if (network.isConverged()) {
    System.out.println("Converged in " + network.getIterationCount() + " iterations");
} else {
    System.out.println("Failed to converge. Residual: " + network.getResidual());
}
```

---

## Usage Examples

### Example 1: Simple Ring Main Network

```java
import neqsim.process.equipment.network.LoopedPipeNetwork;
import neqsim.thermo.system.SystemSrkEos;

// Create gas fluid template
SystemSrkEos gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.9);
gas.addComponent("ethane", 0.07);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

// Create network
LoopedPipeNetwork network = new LoopedPipeNetwork("Ring Main");
network.setFluidTemplate(gas);

// Add nodes
network.addSourceNode("Supply", 50.0, 1000.0);  // 50 bar, 1000 kg/hr
network.addJunctionNode("A");
network.addJunctionNode("B");
network.addJunctionNode("C");
network.addSinkNode("Demand-1", 300.0);  // 300 kg/hr
network.addSinkNode("Demand-2", 400.0);  // 400 kg/hr
network.addSinkNode("Demand-3", 300.0);  // 300 kg/hr

// Connect with pipes (creates ring)
network.addPipe("Supply", "A", "Main", 2000.0, 0.3);
network.addPipe("A", "B", "Ring-AB", 1000.0, 0.2);
network.addPipe("B", "C", "Ring-BC", 1000.0, 0.2);
network.addPipe("C", "A", "Ring-CA", 1000.0, 0.2);  // Closes ring
network.addPipe("A", "Demand-1", "Spur-1", 500.0, 0.15);
network.addPipe("B", "Demand-2", "Spur-2", 500.0, 0.15);
network.addPipe("C", "Demand-3", "Spur-3", 500.0, 0.15);

// Solve
network.setSolverType(LoopedPipeNetwork.SolverType.HARDY_CROSS);
network.setTolerance(1e-6);
network.run();

// Results
System.out.println("Converged: " + network.isConverged());
System.out.println("Iterations: " + network.getIterationCount());

// Print pipe flows
for (String pipeName : network.getPipeNames()) {
    double flow = network.getPipeFlowRate(pipeName);
    System.out.printf("%s: %.1f kg/hr%n", pipeName, flow);
}

// Print node pressures
for (String nodeName : network.getNodeNames()) {
    double pressure = network.getNodePressure(nodeName);
    System.out.printf("%s: %.2f bar%n", nodeName, pressure);
}
```

### Example 2: Gas Gathering Network

```java
// Multi-well gathering system
LoopedPipeNetwork gathering = new LoopedPipeNetwork("Gas Gathering");
gathering.setFluidTemplate(gasFluid);

// Wells as source nodes
gathering.addSourceNode("Well-A", 80.0, 500.0);
gathering.addSourceNode("Well-B", 75.0, 400.0);
gathering.addSourceNode("Well-C", 70.0, 600.0);
gathering.addSourceNode("Well-D", 72.0, 350.0);

// Central processing as sink
gathering.addSinkNode("CPF", 1850.0);  // Total flow

// Manifolds as junctions
gathering.addJunctionNode("Manifold-North");
gathering.addJunctionNode("Manifold-South");
gathering.addJunctionNode("Main-Header");

// Well connections
gathering.addPipe("Well-A", "Manifold-North", "FL-A", 3000.0, 0.15);
gathering.addPipe("Well-B", "Manifold-North", "FL-B", 2500.0, 0.15);
gathering.addPipe("Well-C", "Manifold-South", "FL-C", 4000.0, 0.15);
gathering.addPipe("Well-D", "Manifold-South", "FL-D", 3500.0, 0.15);

// Trunk lines
gathering.addPipe("Manifold-North", "Main-Header", "Trunk-N", 5000.0, 0.25);
gathering.addPipe("Manifold-South", "Main-Header", "Trunk-S", 6000.0, 0.25);

// Loop for reliability
gathering.addPipe("Manifold-North", "Manifold-South", "Tie-Line", 4000.0, 0.2);

// Export
gathering.addPipe("Main-Header", "CPF", "Export", 8000.0, 0.3);

// Solve
gathering.run();

// Check minimum pressure
double minPressure = gathering.getMinimumNodePressure();
System.out.println("Minimum node pressure: " + minPressure + " bar");
```

### Example 3: Water Distribution Network

```java
// Water network using Hazen-Williams
LoopedPipeNetwork water = new LoopedPipeNetwork("Water Distribution");
water.setPressureDropModel("Hazen-Williams");
water.setHazenWilliamsC(130.0);  // Ductile iron

// Create water fluid
SystemSrkEos waterFluid = new SystemSrkEos(293.15, 5.0);
waterFluid.addComponent("water", 1.0);
waterFluid.setMixingRule("classic");
water.setFluidTemplate(waterFluid);

// Water treatment plant
water.addSourceNode("WTP", 5.0, 10000.0);  // 5 bar, 10 m³/hr

// Distribution points
water.addSinkNode("Area-A", 2500.0);
water.addSinkNode("Area-B", 3000.0);
water.addSinkNode("Area-C", 2000.0);
water.addSinkNode("Area-D", 2500.0);

// Junctions
water.addJunctionNode("J1");
water.addJunctionNode("J2");
water.addJunctionNode("J3");
water.addJunctionNode("J4");

// Pipes forming grid with multiple loops
water.addPipe("WTP", "J1", "Main-1", 1000.0, 0.4);
water.addPipe("J1", "J2", "Main-2", 800.0, 0.3);
water.addPipe("J2", "J3", "Main-3", 600.0, 0.3);
water.addPipe("J3", "J4", "Main-4", 800.0, 0.3);
water.addPipe("J4", "J1", "Main-5", 700.0, 0.25);  // Loop 1
water.addPipe("J1", "J3", "Cross-1", 900.0, 0.2);  // Loop 2

// Connections to areas
water.addPipe("J1", "Area-A", "Dist-A", 300.0, 0.2);
water.addPipe("J2", "Area-B", "Dist-B", 400.0, 0.2);
water.addPipe("J3", "Area-C", "Dist-C", 350.0, 0.2);
water.addPipe("J4", "Area-D", "Dist-D", 500.0, 0.2);

// Solve
water.run();

// Verify minimum pressure
for (String sink : Arrays.asList("Area-A", "Area-B", "Area-C", "Area-D")) {
    double p = water.getNodePressure(sink);
    System.out.printf("%s pressure: %.2f bar (min 2.0 bar required)%n", sink, p);
}
```

### Example 4: Network with Multiple Sources

```java
// Network with redundant supply
LoopedPipeNetwork redundant = new LoopedPipeNetwork("Redundant Supply");
redundant.setFluidTemplate(gasFluid);

// Two gas supply stations
redundant.addSourceNode("Station-A", 60.0, 5000.0);
redundant.addSourceNode("Station-B", 58.0, 4000.0);

// Large industrial customer
redundant.addSinkNode("Industry", 9000.0);

// Grid junctions
for (int i = 1; i <= 4; i++) {
    redundant.addJunctionNode("J" + i);
}

// Build interconnected grid
redundant.addPipe("Station-A", "J1", "Feed-A", 5000.0, 0.4);
redundant.addPipe("Station-B", "J2", "Feed-B", 6000.0, 0.4);
redundant.addPipe("J1", "J2", "Tie-12", 3000.0, 0.3);
redundant.addPipe("J1", "J3", "Main-13", 4000.0, 0.35);
redundant.addPipe("J2", "J4", "Main-24", 4500.0, 0.35);
redundant.addPipe("J3", "J4", "Tie-34", 2000.0, 0.3);
redundant.addPipe("J3", "Industry", "Dist-3", 1000.0, 0.3);
redundant.addPipe("J4", "Industry", "Dist-4", 1500.0, 0.3);

// Solve normal operation
redundant.run();
System.out.println("Normal operation - Industry pressure: " + 
    redundant.getNodePressure("Industry") + " bar");

// Simulate Station-A outage
redundant.setSourceFlow("Station-A", 0.0);
redundant.run();
System.out.println("Station-A outage - Industry pressure: " + 
    redundant.getNodePressure("Industry") + " bar");
```

---

## Advanced Features

### Loop Detection

```java
// Get detected loops
List<NetworkLoop> loops = network.getLoops();
System.out.println("Number of independent loops: " + loops.size());

for (NetworkLoop loop : loops) {
    System.out.println("Loop: " + loop.getName());
    for (String pipe : loop.getPipes()) {
        System.out.println("  - " + pipe);
    }
}
```

### Sensitivity Analysis

```java
// Calculate sensitivity of node pressure to pipe change
double sensitivity = network.calculatePressureSensitivity("Customer-1", "Pipe-2");
System.out.println("dP/dD for Customer-1 vs Pipe-2: " + sensitivity);
```

### JSON Export

```java
// Export network to JSON
String json = network.toJson();

// Includes:
// - Node definitions and pressures
// - Pipe definitions and flows
// - Loop information
// - Convergence data
```

---

## Related Documentation

- [Pipeline Networks (Basic)](networks) - Non-looped networks
- [Pipelines](pipelines) - Individual pipeline modeling
- [Process Systems](../processmodel/) - System integration

---

## API Reference

### LoopedPipeNetwork

```java
// Constructor
LoopedPipeNetwork(String name)

// Fluid template
void setFluidTemplate(SystemInterface fluid)

// Node management
void addSourceNode(String name, double pressure, double flow)
void addSinkNode(String name, double demand)
void addJunctionNode(String name)
void setSourceFlow(String name, double flow)

// Pipe management
void addPipe(String from, String to, String name, double length, double diameter)
List<String> getPipeNames()
double getPipeFlowRate(String pipeName)

// Solver configuration
void setSolverType(SolverType type)
void setTolerance(double tol)
void setMaxIterations(int max)
void setDampingFactor(double factor)
void setPressureDropModel(String model)
void setHazenWilliamsC(double c)

// Run and results
void run()
boolean isConverged()
int getIterationCount()
double getResidual()

// Node results
List<String> getNodeNames()
double getNodePressure(String nodeName)
double getMinimumNodePressure()

// Loop information
List<NetworkLoop> getLoops()

// Analysis
double calculatePressureSensitivity(String node, String pipe)

// Export
String toJson()
```

### SolverType Enum

```java
enum SolverType {
    HARDY_CROSS,
    NEWTON_RAPHSON,
    LINEAR
}
```

### NetworkLoop Class

```java
class NetworkLoop {
    String getName()
    List<String> getPipes()
    double getHeadLossImbalance()
}
```
