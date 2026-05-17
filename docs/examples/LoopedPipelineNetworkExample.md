---
layout: default
title: "LoopedPipelineNetworkExample"
description: "Jupyter notebook tutorial for NeqSim"
parent: Examples
nav_order: 1
---

# LoopedPipelineNetworkExample

> **Note:** This is an auto-generated Markdown version of the Jupyter notebook 
> [`LoopedPipelineNetworkExample.ipynb`](https://github.com/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb).
> You can also [view it on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) 
> or [open in Google Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb).

---

# Looped Pipeline Network Solver - Hardy Cross Method

This notebook demonstrates NeqSim's Hardy Cross looped network solver for pipeline networks with multiple flow paths and ring mains.

## Background

Traditional pipeline network solvers handle tree-like (branching) topologies where each node is connected by a single path. However, many real-world systems include:
- **Ring mains** for supply redundancy
- **Parallel pipelines** for increased capacity
- **Looped offshore networks** connecting multiple platforms

The Hardy Cross method (1936) is a classic iterative technique that:
1. Detects independent loops in the network using spanning tree analysis
2. Iteratively adjusts flow corrections in each loop until pressure drops balance
3. Converges to the unique solution satisfying both mass balance and pressure constraints

**Key formula:** For each loop, the flow correction is:

$$\Delta Q = -\frac{\sum_i H_i}{2 \sum_i \left|\frac{H_i}{Q_i}\right|}$$

where $H_i$ is the head loss in pipe $i$ and $Q_i$ is the flow rate.

```python
# Import NeqSim - Direct Java Access via jneqsim
from neqsim import jneqsim

# Import Java classes through the jneqsim gateway
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
LoopedPipeNetwork = jneqsim.process.equipment.network.LoopedPipeNetwork
LoopDetector = jneqsim.process.equipment.network.LoopDetector
NetworkLoop = jneqsim.process.equipment.network.NetworkLoop

print("NeqSim Hardy Cross network solver loaded successfully!")
```

## Example 1: Simple Triangle Loop

Let's start with a simple three-node network forming a triangle. This is the minimal looped network.

```
       A (supply)
      / \
     /   \
    B-----C (demand)
```

```python
# First, demonstrate loop detection with the LoopDetector class
detector = LoopDetector()

# Add edges for triangle A-B-C-A
detector.addEdge("A", "B", "pipe1")
detector.addEdge("B", "C", "pipe2")
detector.addEdge("C", "A", "pipe3")

# Verify graph structure
print(f"Nodes: {detector.getNodeCount()}")
print(f"Edges: {detector.getEdgeCount()}")

# Find loops
loops = detector.findLoops()
print(f"\nLoops found: {loops.size()}")

for loop in loops:
    print(f"  {loop.getLoopId()}: {loop.toString()}")
    print(f"  Members: {loop.getMembers().size()}")
```

```python
# Now create a full network with the Hardy Cross solver

# Define a natural gas fluid
gas = SystemSrkEos(298.15, 40.0)  # 25°C, 40 bar
gas.addComponent("methane", 0.92)
gas.addComponent("ethane", 0.05)
gas.addComponent("propane", 0.03)
gas.createDatabase(True)
gas.setMixingRule("classic")

# Create the triangle network
triangle = LoopedPipeNetwork("Triangle Loop Demo")
triangle.setFluidTemplate(gas)

# Add nodes
triangle.addSourceNode("supply", 45.0, 800.0)  # 45 bar, 800 kg/hr
triangle.addJunctionNode("junction1")
triangle.addSinkNode("customer", 800.0)  # Demand: 800 kg/hr

# Add pipes forming a loop
# Main path: supply -> junction1 -> customer
triangle.addPipe("supply", "junction1", "main_in", 2000.0, 0.25)  # 2 km, 10" pipe
triangle.addPipe("junction1", "customer", "main_out", 2000.0, 0.20)  # 2 km, 8" pipe

# Bypass pipe: supply -> customer (creates the loop)
triangle.addPipe("supply", "customer", "bypass", 3500.0, 0.15)  # 3.5 km, 6" pipe

# Set solver parameters
triangle.setMaxIterations(100)
triangle.setTolerance(1.0)  # 1 Pa pressure tolerance

# Run the solver
triangle.run()

# Display results
summary = triangle.getSolutionSummary()
print(f"Network: {summary.get('networkName')}")
print(f"Converged: {summary.get('converged')}")
print(f"Iterations: {summary.get('iterations')}")
print(f"Loops detected: {summary.get('numberOfLoops')}")
print(f"Solver type: {summary.get('solverType')}")
```

## Example 2: Distribution Ring Main

A typical gas distribution network has a ring main with multiple offtakes:

```
                Supply
                  |
        +---------A---------+
        |                   |
        B---Customer1       D
        |                   |
        +----C----Customer2-+
```

```python
# Create a ring main distribution network
ring_main = LoopedPipeNetwork("Distribution Ring Main")
ring_main.setFluidTemplate(gas)

# Supply point
ring_main.addSourceNode("supply", 50.0, 2000.0)  # 50 bar, 2000 kg/hr total

# Ring main junctions
ring_main.addJunctionNode("A")
ring_main.addJunctionNode("B")
ring_main.addJunctionNode("C")
ring_main.addJunctionNode("D")

# Customers (demand nodes)
ring_main.addSinkNode("customer1", 800.0)   # 800 kg/hr
ring_main.addSinkNode("customer2", 1200.0)  # 1200 kg/hr

# Main supply line
ring_main.addPipe("supply", "A", "supply_line", 1000.0, 0.35)  # 1 km, 14"

# Ring main pipes
ring_main.addPipe("A", "B", "ring_AB", 500.0, 0.25)   # 500 m, 10"
ring_main.addPipe("B", "C", "ring_BC", 600.0, 0.25)   # 600 m, 10"
ring_main.addPipe("A", "D", "ring_AD", 500.0, 0.25)   # 500 m, 10"
ring_main.addPipe("D", "C", "ring_DC", 600.0, 0.25)   # 600 m, 10" (closes the loop)

# Customer connections
ring_main.addPipe("B", "customer1", "to_cust1", 100.0, 0.15)  # 100 m, 6"
ring_main.addPipe("C", "customer2", "to_cust2", 150.0, 0.20)  # 150 m, 8"

# Run solver
ring_main.setMaxIterations(150)
ring_main.setTolerance(5.0)  # 5 Pa tolerance
ring_main.run()

# Display results
print("\n=== Ring Main Network Results ===")
print(f"Converged: {ring_main.isConverged()}")
print(f"Iterations: {ring_main.getIterationCount()}")
print(f"Loops found: {ring_main.getNumberOfLoops()}")

# Show flow distribution in the ring
print("\n--- Flow Distribution (kg/hr) ---")
for pipe_name in ['ring_AB', 'ring_BC', 'ring_AD', 'ring_DC']:
    flow = ring_main.getPipeFlowRate(pipe_name)
    print(f"  {pipe_name}: {flow:.1f} kg/hr")
```

## Example 3: Offshore Subsea Ring Network

An offshore gas export network with redundant subsea pipelines:

```
    Platform
    /      \
 Riser1   Riser2
   |        |
  M1--Ring--M2
   |    |    |
  Well1 M3  Well2
         |
       Well3
```

```python
# First, just demonstrate loop detection for this topology
detector = LoopDetector()

# Ring main: platform -> manifold1 -> manifold2 -> manifold3 -> platform
detector.addEdge("platform", "manifold1", "riser1")
detector.addEdge("manifold1", "manifold2", "ring12")
detector.addEdge("manifold2", "manifold3", "ring23")
detector.addEdge("manifold3", "platform", "riser2")  # Creates the loop

# Well connections (spurs off the ring - these don't create loops)
detector.addEdge("manifold1", "well1", "jumper1")
detector.addEdge("manifold2", "well2", "jumper2")
detector.addEdge("manifold3", "well3", "jumper3")

# Analyze topology
print(f"=== Offshore Ring Topology ===")
print(f"Nodes: {detector.getNodeCount()}")
print(f"Edges: {detector.getEdgeCount()}")

loops = detector.findLoops()
print(f"\nIndependent loops: {loops.size()}")

for loop in loops:
    print(f"  {loop.toString()}")
    print(f"  This loop contains {loop.getMembers().size()} pipes")
```

## Example 4: Figure-8 Network (Two Loops)

A network with two independent loops sharing a common segment:

```
    S
    |   
    A---B
   /| X |\  
  C-+-+-+-D
    | X |
    E---F
```

```python
# Demonstrate two-loop detection
detector = LoopDetector()

# Create figure-8: A-B-C-A and B-C-D-B (sharing edge B-C)
detector.addEdge("A", "B", "pipe1")
detector.addEdge("B", "C", "pipe2")
detector.addEdge("C", "A", "pipe3")  # Closes first loop
detector.addEdge("C", "D", "pipe4")
detector.addEdge("D", "B", "pipe5")  # Closes second loop

print("=== Figure-8 Network Topology ===")
print(f"Nodes: {detector.getNodeCount()}")
print(f"Edges: {detector.getEdgeCount()}")
print(f"Expected loops: E - V + 1 = {detector.getEdgeCount()} - {detector.getNodeCount()} + 1 = {detector.getEdgeCount() - detector.getNodeCount() + 1}")

loops = detector.findLoops()
print(f"\nIndependent loops found: {loops.size()}")

for loop in loops:
    print(f"\n  {loop.getLoopId()}:")
    print(f"    {loop.toString()}")
```

## Example 5: Network Loop Analysis API

Demonstrating the `NetworkLoop` class for understanding flow direction conventions.

```python
# Create a loop manually and inspect it
loop = NetworkLoop("manual-loop")

# Add members with directions
# Positive direction: flow goes clockwise around loop
# Negative direction: flow goes counter-clockwise
loop.addMember("pipe_A_to_B", 1)    # Clockwise
loop.addMember("pipe_B_to_C", 1)    # Clockwise  
loop.addMember("pipe_C_to_A", -1)   # Counter-clockwise (C->A is actually A->C going against loop)

print(f"Loop ID: {loop.getLoopId()}")
print(f"Number of members: {loop.getMembers().size()}")
print(f"String representation: {loop.toString()}")

# Iterate through members
print("\nLoop members:")
for member in loop.getMembers():
    direction = "clockwise (+)" if member.getDirection() > 0 else "counter-clockwise (-)"
    print(f"  Pipe: {member.getPipeName()}, Direction: {direction}")
```

## JSON Output for Integration

The network solver can export results in JSON format for integration with other systems.

```python
import json

# Create a simple network and get JSON output
simple_net = LoopedPipeNetwork("JSON Demo Network")
simple_net.setFluidTemplate(gas)

simple_net.addSourceNode("inlet", 55.0, 1000.0)
simple_net.addJunctionNode("mid")
simple_net.addSinkNode("outlet", 1000.0)

simple_net.addPipe("inlet", "mid", "segment1", 1000.0, 0.20)
simple_net.addPipe("mid", "outlet", "segment2", 1000.0, 0.20)

# Get JSON string
json_output = simple_net.toJson()

# Parse and pretty-print
data = json.loads(str(json_output))
print(json.dumps(data, indent=2))
```

## Summary

The NeqSim Hardy Cross looped network solver provides:

1. **Automatic Loop Detection** - Uses DFS spanning tree algorithm to find all independent loops
2. **Hardy Cross Iteration** - Classic iterative method for balancing pressure drops in loops  
3. **Multiple Solver Types** - HARDY_CROSS (for loops), SEQUENTIAL (for trees), NEWTON_RAPHSON (future)
4. **JSON Export** - Integration-ready output format
5. **Configurable Parameters** - Tolerance, max iterations, relaxation factor

### Applications

- Gas distribution networks with ring mains
- Offshore pipeline networks with redundant paths
- Water/steam distribution systems
- Any pipe network with multiple flow paths

### Theory Reference

The Hardy Cross method was developed by Hardy Cross in 1936. For a network with $L$ loops, the algorithm:

1. Assumes an initial flow distribution satisfying mass balance at each node
2. For each loop, calculates the algebraic sum of head losses: $\sum H = \sum k Q^n$
3. Calculates flow correction: $\Delta Q = -\frac{\sum H}{n \sum |H/Q|}$
4. Updates flows in all pipes of the loop
5. Repeats until pressure imbalance < tolerance

