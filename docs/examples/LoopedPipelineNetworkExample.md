---
title: Looped Pipeline Network Solver - Hardy Cross Method
description: This tutorial demonstrates NeqSim's Hardy Cross looped network solver for pipeline networks with multiple flow paths and ring mains.
---

# Looped Pipeline Network Solver - Hardy Cross Method

This tutorial demonstrates NeqSim's Hardy Cross looped network solver for pipeline networks with multiple flow paths and ring mains.

## Background

Traditional pipeline network solvers handle tree-like (branching) topologies where each node is connected by a single path. However, many real-world systems include:

- **Ring mains** for supply redundancy
- **Parallel pipelines** for increased capacity
- **Looped offshore networks** connecting multiple platforms

## The Hardy Cross Method

The Hardy Cross method (1936) is a classic iterative technique that:

1. Detects independent loops in the network using spanning tree analysis
2. Iteratively adjusts flow corrections in each loop until pressure drops balance
3. Converges to the unique solution satisfying both mass balance and pressure constraints

**Key formula:** For each loop, the flow correction is:

$$\Delta Q = -\frac{\sum_i H_i}{2 \sum_i \left|\frac{H_i}{Q_i}\right|}$$

where $H_i$ is the head loss in pipe $i$ and $Q_i$ is the flow rate.

## Topics Covered

1. Setting up a looped pipeline network
2. Loop detection using DFS spanning tree algorithm
3. Solving with the Hardy Cross iterative method
4. Analyzing flow distribution in parallel paths
5. Ring main configurations for offshore platforms

## View the Notebook

| Format | Link |
|--------|------|
| **nbviewer** | [View on nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) |
| **Colab** | [Open in Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) |
| **GitHub** | [View on GitHub](https://github.com/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) |

## Related Documentation

- [Pipeline Network Solver Enhancement](../process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md)
- [Network Solver Tutorial](NetworkSolverTutorial.md)
- [Pipeline Equipment](../process/equipment/pipelines.md)
- [Network Equipment](../process/equipment/networks.md)
