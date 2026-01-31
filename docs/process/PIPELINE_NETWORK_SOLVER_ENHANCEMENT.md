# Pipeline Network Solver Enhancement Proposal

## Implementation Status: ✅ COMPLETE

The Hardy Cross looped network solver has been implemented in NeqSim. See the [example notebook](../examples/LoopedPipelineNetworkExample.ipynb) for usage examples.

### Implemented Classes

| Class | Location | Purpose |
|-------|----------|---------|
| `LoopedPipeNetwork` | [network/LoopedPipeNetwork.java](../../src/main/java/neqsim/process/equipment/network/LoopedPipeNetwork.java) | Main network class with Hardy Cross solver |
| `LoopDetector` | [network/LoopDetector.java](../../src/main/java/neqsim/process/equipment/network/LoopDetector.java) | DFS spanning tree loop detection |
| `NetworkLoop` | [network/NetworkLoop.java](../../src/main/java/neqsim/process/equipment/network/NetworkLoop.java) | Loop representation with member pipes |

### Key Features

- **Automatic Loop Detection**: DFS spanning tree algorithm finds all independent loops
- **Hardy Cross Solver**: Iteratively balances pressure drops in loops
- **Multiple Node Types**: Source, sink, and junction nodes
- **JSON Output**: Integration-ready results format
- **Configurable**: Tolerance, max iterations, relaxation factor

---

## Current State Analysis

NeqSim currently has two network classes:

| Class | Location | Capabilities |
|-------|----------|--------------|
| `PipeFlowNetwork` | [network/PipeFlowNetwork.java](../../src/main/java/neqsim/process/equipment/network/PipeFlowNetwork.java) | Tree topology, TDMA solver, compositional tracking |
| `WellFlowlineNetwork` | [network/WellFlowlineNetwork.java](../../src/main/java/neqsim/process/equipment/network/WellFlowlineNetwork.java) | Well-flowline gathering, Beggs-Brill |

### Current Limitations

1. **Tree Topology Only**: Networks must be acyclic (no loops)
   - Each manifold can have only ONE outbound pipeline
   - No support for ring mains or looped distribution systems
   
2. **Sequential Solving**: Manifolds processed in topological order
   - Cannot handle pressure-dependent flow distribution in loops
   - No simultaneous solution of network equations

3. **Fixed Pressure Boundaries**: 
   - Inlet pressures from feed streams
   - No iterative pressure-flow balance

---

## Proposed Enhancement: Looped Network Solver

### Use Cases Requiring Looped Networks

| Application | Description |
|-------------|-------------|
| **Ring Main Gas Distribution** | Onshore gas distribution with looped mains for redundancy |
| **Offshore Export Systems** | Parallel export pipelines with crossovers |
| **Gathering Systems with Crossovers** | Multiple tie-ins with interconnecting flowlines |
| **Subsea Production Networks** | Complex manifold-to-manifold connections |
| **Injection Water Networks** | Looped distribution to multiple injectors |

---

## Implementation Approach

### Option A: Hardy Cross Method (Recommended for Steady-State)

The Hardy Cross method is the classic iterative technique for looped pipe networks:

```
For each loop in the network:
  1. Assume initial flow distribution satisfying continuity
  2. Calculate head loss in each loop: ΔH = Σ(K*Q²) - Σ(K*Q²)
  3. Calculate flow correction: ΔQ = -ΔH / (2*Σ|K*Q|)
  4. Update flows: Q_new = Q + ΔQ
  5. Repeat until |ΔQ| < tolerance
```

**Advantages:**
- Well-proven for pipe networks
- Intuitive physical interpretation
- Good convergence for most networks

**Implementation Steps:**
1. Detect loops in network topology using DFS
2. Identify independent loops (spanning tree chords)
3. Iteratively balance head losses around each loop

### Option B: Newton-Raphson Simultaneous Solution

For larger networks or transient simulations:

```
Solve F(Q, P) = 0 where:
  - Continuity at each node: Σ Q_in = Σ Q_out
  - Momentum for each pipe: P_in - P_out = f(Q, geometry, fluid)
  
Jacobian: J = ∂F/∂(Q,P)
Update: [ΔQ, ΔP] = -J⁻¹ * F
```

**Advantages:**
- Faster convergence for large networks
- Handles all constraints simultaneously
- Better for transient extension

### Option C: Gradient-Based Optimization

Minimize total network power dissipation:

```
min Σ ∫ (friction_loss * flow) dL
subject to:
  - Node continuity
  - Pressure bounds
  - Flow bounds
```

---

## Recommended Implementation

### Phase 1: Extend `PipeFlowNetwork` for Looped Topologies

```java
public class LoopedPipeNetwork extends PipeFlowNetwork {
    
    /** Loop detection and representation */
    private List<NetworkLoop> independentLoops;
    
    /** Solver selection */
    public enum NetworkSolver {
        HARDY_CROSS,      // Simple iterative for steady-state
        NEWTON_RAPHSON,   // Simultaneous for complex networks
        SEQUENTIAL        // Current tree-topology solver
    }
    
    /** Detect and store network loops */
    public void detectLoops() {
        // Use DFS to find spanning tree
        // Chords (non-tree edges) define independent loops
    }
    
    /** Hardy Cross iteration */
    private void solveHardyCross(UUID id, double tolerance, int maxIter) {
        for (int iter = 0; iter < maxIter; iter++) {
            double maxCorrection = 0;
            
            for (NetworkLoop loop : independentLoops) {
                // Calculate head loss around loop
                double headLoss = loop.calculateHeadLoss();
                
                // Calculate flow correction
                double correction = loop.calculateFlowCorrection(headLoss);
                
                // Apply correction to all pipes in loop
                loop.applyCorrection(correction);
                
                maxCorrection = Math.max(maxCorrection, Math.abs(correction));
            }
            
            // Re-run pipe hydraulics with updated flows
            for (PipelineSegment pipe : allPipelines) {
                pipe.getPipeline().run(id);
            }
            
            if (maxCorrection < tolerance) {
                break; // Converged
            }
        }
    }
}
```

### Phase 2: Network Loop Representation

```java
public class NetworkLoop {
    /** Pipes in this loop with direction (+1 or -1) */
    private List<LoopMember> members;
    
    public static class LoopMember {
        PipelineSegment pipe;
        int direction; // +1 = same as loop direction, -1 = opposite
    }
    
    /** Calculate sum of head losses around the loop */
    public double calculateHeadLoss() {
        double totalHead = 0;
        for (LoopMember member : members) {
            double pipeLoss = member.pipe.getPipeline().getPressureDrop("Pa");
            totalHead += member.direction * pipeLoss;
        }
        return totalHead;
    }
    
    /** Calculate Hardy Cross flow correction */
    public double calculateFlowCorrection(double headLoss) {
        double denominator = 0;
        for (LoopMember member : members) {
            // ∂H/∂Q ≈ 2*H/Q for turbulent flow
            double flow = member.pipe.getPipeline().getFlowRate("kg/sec");
            double loss = member.pipe.getPipeline().getPressureDrop("Pa");
            if (Math.abs(flow) > 1e-10) {
                denominator += 2 * Math.abs(loss / flow);
            }
        }
        return -headLoss / denominator;
    }
    
    /** Apply flow correction to all pipes in loop */
    public void applyCorrection(double deltaQ) {
        for (LoopMember member : members) {
            double currentFlow = member.pipe.getPipeline().getFlowRate("kg/sec");
            double newFlow = currentFlow + member.direction * deltaQ;
            member.pipe.getPipeline().setFlowRate(newFlow, "kg/sec");
        }
    }
}
```

### Phase 3: Loop Detection Algorithm

```java
/** 
 * Detect independent loops using DFS spanning tree.
 * Each non-tree edge (chord) defines one independent loop.
 */
public List<NetworkLoop> detectIndependentLoops() {
    List<NetworkLoop> loops = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Map<String, String> parent = new HashMap<>();
    Set<PipelineSegment> treeEdges = new HashSet<>();
    
    // DFS to build spanning tree
    String startNode = findSourceManifold();
    dfsSpanningTree(startNode, null, visited, parent, treeEdges);
    
    // Non-tree edges (chords) define loops
    for (PipelineSegment pipe : allPipelines) {
        if (!treeEdges.contains(pipe)) {
            // This chord creates a loop
            NetworkLoop loop = traceLoop(pipe, parent);
            loops.add(loop);
        }
    }
    
    return loops;
}

private NetworkLoop traceLoop(PipelineSegment chord, Map<String, String> parent) {
    // Find path in tree between chord endpoints
    String node1 = chord.getFromManifold();
    String node2 = chord.getToManifold();
    
    // Trace paths to common ancestor and construct loop
    // ... implementation details ...
}
```

---

## Integration with Existing Classes

### Modified `PipeFlowNetwork.run()`:

```java
@Override
public void run(UUID id) {
    // Detect topology type
    detectLoops();
    
    if (independentLoops.isEmpty()) {
        // Tree topology - use existing sequential solver
        runSequential(id);
    } else {
        // Looped topology - use Hardy Cross
        runHardyCross(id);
    }
}
```

### New Network Builder API:

```java
// Create looped network
PipeFlowNetwork network = new PipeFlowNetwork("Distribution");

// Create manifolds
String manifoldA = network.createManifold("A");
String manifoldB = network.createManifold("B");
String manifoldC = network.createManifold("C");

// Create loop: A -> B -> C -> A
network.connectManifolds(manifoldA, manifoldB, "pipe-AB", 1000, 0.3, 20);
network.connectManifolds(manifoldB, manifoldC, "pipe-BC", 1500, 0.25, 30);
network.connectManifolds(manifoldC, manifoldA, "pipe-CA", 1200, 0.2, 25);  // Closes loop

// Add feed and offtake
network.addInletPipeline("feed", feedStream, manifoldA, 500, 0.4, 10);
network.addOutletDemand(manifoldB, 5.0, "MSm3/day");  // New: demand at node

// Solve
network.run();
```

---

## Validation Test Cases

### Test 1: Simple Triangle Loop

```
       A
      / \
     /   \
    B-----C
    
Feed at A, demands at B and C
Verify: Q_AB + Q_AC = Q_feed
        Q_AB - Q_BC = Demand_B
        Q_AC + Q_BC = Demand_C
```

### Test 2: Ring Main with Parallel Paths

```
    Feed
      |
      A----B----C
      |         |
      D----E----F
      |
    Outlet
    
Multiple paths from A to F
Verify flows distribute according to resistance
```

### Test 3: Offshore Gathering with Crossover

```
    Well1 --- Manifold1 ---+--- Export
                          |
    Well2 --- Manifold2 ---+
    
Crossover between manifolds for flexibility
```

---

## Performance Considerations

| Network Size | Recommended Solver | Expected Iterations |
|--------------|-------------------|---------------------|
| < 10 loops | Hardy Cross | 5-15 |
| 10-50 loops | Newton-Raphson | 3-8 |
| > 50 loops | Newton-Raphson with sparse matrix | 3-8 |

---

## Dependencies

- Existing: `TDMAsolve` for individual pipe solutions
- New: Graph library for loop detection (or implement simple DFS)
- Optional: Sparse matrix library for large Newton-Raphson (EJML already included)

---

## Effort Estimate

| Phase | Effort | Priority |
|-------|--------|----------|
| Phase 1: Loop detection | 2-3 days | High |
| Phase 2: Hardy Cross solver | 3-4 days | High |
| Phase 3: Newton-Raphson (optional) | 4-5 days | Medium |
| Testing & validation | 3-4 days | High |
| Documentation | 1-2 days | Medium |

**Total: ~2-3 weeks for full implementation**

---

## References

1. Cross, H. (1936). "Analysis of Flow in Networks of Conduits or Conductors." University of Illinois Bulletin 286.
2. Todini, E. & Pilati, S. (1988). "A gradient algorithm for the analysis of pipe networks." Computer Applications in Water Supply.
3. Rossman, L.A. (2000). "EPANET 2 Users Manual." US EPA.

---

## Example: Gas Distribution Network

```java
// Norwegian gas distribution network example
SystemInterface gas = new SystemGERG2008Eos(278.15, 70.0);
gas.addComponent("methane", 0.92);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.setMixingRule("classic");

Stream feed = new Stream("Kårstø feed", gas);
feed.setFlowRate(35.0, "MSm3/day");
feed.run();

// Create ring main network
PipeFlowNetwork network = new PipeFlowNetwork("Rogaland Distribution");

// Main manifolds
String karsto = network.createManifold("Kårstø");
String stavanger = network.createManifold("Stavanger");
String sandnes = network.createManifold("Sandnes");
String haugesund = network.createManifold("Haugesund");

// Ring main (looped for redundancy)
network.addInletPipeline("feed", feed, karsto, 1000, 0.8, 20);
network.connectManifolds(karsto, stavanger, "main-1", 45000, 0.6, 100);
network.connectManifolds(stavanger, sandnes, "main-2", 15000, 0.5, 50);
network.connectManifolds(sandnes, haugesund, "main-3", 60000, 0.5, 120);
network.connectManifolds(haugesund, karsto, "main-4", 55000, 0.5, 110); // Closes loop

// Add demands
network.addOutletDemand(stavanger, 12.0, "MSm3/day");
network.addOutletDemand(sandnes, 8.0, "MSm3/day");
network.addOutletDemand(haugesund, 10.0, "MSm3/day");

// Solve looped network
network.setNetworkSolver(NetworkSolver.HARDY_CROSS);
network.run();

// Results
System.out.println("Flow Kårstø->Stavanger: " + network.getFlowRate("main-1", "MSm3/day"));
System.out.println("Flow Haugesund->Kårstø: " + network.getFlowRate("main-4", "MSm3/day"));
```
