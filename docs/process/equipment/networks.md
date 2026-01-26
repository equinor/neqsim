# Pipeline Networks

Documentation for pipeline network modeling in NeqSim.

## Table of Contents
- [Overview](#overview)
- [PipeFlowNetwork](#pipeflownetwork)
- [WellFlowlineNetwork](#wellflowlinenetwork)
- [Network Architecture](#network-architecture)
- [Usage Examples](#usage-examples)
- [Advanced Features](#advanced-features)
- [Related Documentation](#related-documentation)

---

## Overview

**Location:** `neqsim.process.equipment.network`

The network package provides classes for modeling interconnected pipeline systems where multiple pipelines converge at manifolds. This is essential for:

- Gas gathering systems
- Oil production networks
- Subsea tie-backs
- Distribution networks
- Well flowline networks

| Class | Description |
|-------|-------------|
| `PipeFlowNetwork` | Compositional network with TDMA solver |
| `WellFlowlineNetwork` | Network using Beggs-Brill correlations |

---

## PipeFlowNetwork

The `PipeFlowNetwork` class models pipeline networks where multiple pipelines converge to manifolds, using compositional `OnePhasePipeLine` with TDMA (Tri-Diagonal Matrix Algorithm) solvers.

### Class Hierarchy

```
ProcessEquipmentBaseClass
└── PipeFlowNetwork
    ├── contains: ManifoldNode[]
    └── contains: PipelineSegment[]
```

### Key Features

- **Compositional Tracking**: Full compositional mass balance through the network
- **Steady-State & Transient**: Support for both solution modes
- **Energy Balance**: Heat transfer calculations included
- **Graph-Based Architecture**: Nodes (manifolds) connected by edges (pipelines)

### Architecture

The network is modeled as a directed graph:

```
        [Feed 1]             [Feed 2]
           │                    │
           ▼                    ▼
      ┌─────────┐         ┌─────────┐
      │ Pipe 1  │         │ Pipe 2  │
      └────┬────┘         └────┬────┘
           │                   │
           ▼                   ▼
        ┌─────────────────────────┐
        │    Manifold A (Mixer)   │
        └───────────┬─────────────┘
                    │
                    ▼
             ┌─────────────┐
             │ Export Pipe │
             └──────┬──────┘
                    │
                    ▼
        ┌─────────────────────────┐
        │   End Manifold (Mixer)  │
        └───────────┬─────────────┘
                    │
                    ▼
              [Outlet Stream]
```

### Inner Classes

#### PipelineSegment
Represents a pipeline in the network:

```java
public static class PipelineSegment {
    String name;
    OnePhasePipeLine pipeline;
    String fromManifold;  // null for inlet pipes
    String toManifold;
    
    boolean isInletPipeline();  // true if fromManifold is null
}
```

#### ManifoldNode
Represents a junction/manifold in the network:

```java
public static class ManifoldNode {
    String name;
    Mixer mixer;
    List<PipelineSegment> inboundPipelines;
    PipelineSegment outboundPipeline;
    
    boolean isTerminal();  // true if no outbound pipeline
}
```

### Constructors

```java
import neqsim.process.equipment.network.PipeFlowNetwork;

// Basic constructor
PipeFlowNetwork network = new PipeFlowNetwork("gathering network");
```

### Key Methods

| Method | Description |
|--------|-------------|
| `createManifold(String name)` | Create a new manifold node |
| `addInletPipeline(name, feed, toManifold, length, diameter, nodes)` | Add inlet pipeline |
| `connectManifolds(from, to, name, length, diameter, nodes)` | Connect two manifolds |
| `run()` | Execute network simulation |
| `getOutletStream()` | Get the network outlet stream |
| `getPipeline(String name)` | Get a specific pipeline by name |
| `getManifold(String name)` | Get a manifold node by name |

---

## Usage Examples

### Gas Gathering Network

```java
import neqsim.process.equipment.network.PipeFlowNetwork;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

// Create feed streams from wells
SystemInterface gas1 = new SystemSrkEos(320.0, 150.0);
gas1.addComponent("methane", 0.92);
gas1.addComponent("ethane", 0.05);
gas1.addComponent("propane", 0.03);
gas1.setMixingRule("classic");

Stream feed1 = new Stream("well-1", gas1);
feed1.setFlowRate(5.0, "MSm3/day");
feed1.run();

SystemInterface gas2 = new SystemSrkEos(315.0, 145.0);
gas2.addComponent("methane", 0.90);
gas2.addComponent("ethane", 0.06);
gas2.addComponent("propane", 0.04);
gas2.setMixingRule("classic");

Stream feed2 = new Stream("well-2", gas2);
feed2.setFlowRate(3.0, "MSm3/day");
feed2.run();

// Create network
PipeFlowNetwork network = new PipeFlowNetwork("gathering system");

// Create manifolds
String manifoldA = network.createManifold("manifold A");
String endManifold = network.createManifold("end manifold");

// Add inlet pipelines to manifold A
// Parameters: name, feedStream, toManifold, length(m), diameter(m), nodes
network.addInletPipeline("pipe1", feed1, manifoldA, 5000.0, 0.3, 50);
network.addInletPipeline("pipe2", feed2, manifoldA, 4500.0, 0.25, 45);

// Connect manifold A to end manifold with export pipeline
network.connectManifolds(manifoldA, endManifold, "export", 15000.0, 0.5, 100);

// Run steady-state simulation
network.run();

// Access results
StreamInterface outlet = network.getOutletStream();
System.out.println("Outlet pressure: " + outlet.getPressure("bara") + " bara");
System.out.println("Outlet temperature: " + outlet.getTemperature("C") + " °C");
System.out.println("Total flow: " + outlet.getFlowRate("MSm3/day") + " MSm3/day");
```

### Multi-Tier Network

```java
// Create complex gathering network
PipeFlowNetwork network = new PipeFlowNetwork("field gathering");

// Create manifold hierarchy
String tier1ManifoldA = network.createManifold("tier1-A");
String tier1ManifoldB = network.createManifold("tier1-B");
String tier2Manifold = network.createManifold("tier2");
String centralManifold = network.createManifold("central");

// Tier 1: Wells to first-level manifolds
network.addInletPipeline("well1", well1Stream, tier1ManifoldA, 2000.0, 0.2, 30);
network.addInletPipeline("well2", well2Stream, tier1ManifoldA, 2500.0, 0.2, 30);
network.addInletPipeline("well3", well3Stream, tier1ManifoldB, 1800.0, 0.2, 30);
network.addInletPipeline("well4", well4Stream, tier1ManifoldB, 3000.0, 0.2, 30);

// Tier 2: First-level to second-level
network.connectManifolds(tier1ManifoldA, tier2Manifold, "line-A", 5000.0, 0.35, 50);
network.connectManifolds(tier1ManifoldB, tier2Manifold, "line-B", 4500.0, 0.35, 50);

// Tier 3: Second-level to central
network.connectManifolds(tier2Manifold, centralManifold, "export", 10000.0, 0.5, 100);

// Run
network.run();
```

### Accessing Pipeline Results

```java
// Run network
network.run();

// Access specific pipeline
PipeFlowNetwork.PipelineSegment segment = network.getPipelineSegment("pipe1");
OnePhasePipeLine pipeline = segment.getPipeline();

// Get pressure profile
double[] pressures = pipeline.getPressureProfile();
double[] positions = pipeline.getPositionProfile();

for (int i = 0; i < positions.length; i++) {
    System.out.printf("Position: %.1f m, Pressure: %.2f bara%n", 
                      positions[i], pressures[i]);
}
```

---

## WellFlowlineNetwork

A simplified network model using Beggs-Brill correlations instead of the full TDMA solver.

### Key Differences from PipeFlowNetwork

| Aspect | PipeFlowNetwork | WellFlowlineNetwork |
|--------|-----------------|---------------------|
| Flow Model | TDMA solver | Beggs-Brill correlation |
| Compositional | Full | Simplified |
| Speed | Slower | Faster |
| Accuracy | Higher | Lower |
| Use Case | Detailed analysis | Quick screening |

---

## Advanced Features

### Transient Simulation

```java
PipeFlowNetwork network = new PipeFlowNetwork("transient network");
// ... setup network ...

// Configure transient parameters
network.setTransientMode(true);
network.setTimeStep(1.0);  // seconds

// Run transient for 1 hour
for (double t = 0; t < 3600; t += 1.0) {
    // Update boundary conditions if needed
    feed1.setFlowRate(5.0 + 0.5 * Math.sin(t / 600), "MSm3/day");
    feed1.run();
    
    network.run();
    
    if (t % 60 == 0) {
        System.out.printf("t=%.0f s, P_outlet=%.2f bara%n", 
                          t, network.getOutletStream().getPressure("bara"));
    }
}
```

### Heat Transfer

```java
// Configure pipeline heat transfer
OnePhasePipeLine pipeline = network.getPipelineSegment("export").getPipeline();
pipeline.setOuterTemperature(278.15);  // Ambient temperature (K)
pipeline.setOverallHeatTransferCoefficient(5.0);  // W/m²/K
```

---

## Design Considerations

### Network Topology
- Ensure no cycles in the network (currently supports tree structures)
- Each manifold should have at least one inbound pipeline
- Only the terminal manifold should have no outbound pipeline

### Pressure Matching
- Feed stream pressures should be high enough to deliver to manifolds
- Back-pressure from downstream affects all upstream pipelines

### Computational Performance
- Use fewer nodes for quick estimates
- Use more nodes (50-100 per km) for accurate pressure profiles
- Consider WellFlowlineNetwork for large screening studies

---

## Related Documentation

- [Pipelines](pipelines.md) - Single pipeline modeling
- [Manifolds](manifolds.md) - Manifold equipment
- [Mixers and Splitters](mixers_splitters.md) - Stream mixing and splitting
- [Subsea Systems](subsea_systems.md) - Subsea production
