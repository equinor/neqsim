---
title: Pipeline Network Optimization
description: "Guide to NeqSim pipeline network solver optimization features: NLP choke allocation (BOBYQA/CMA-ES), multi-objective Pareto, sparse matrix solvers, and analytical validation benchmarks."
---

# Pipeline Network Optimization

NeqSim's `LoopedPipeNetwork` provides a comprehensive pipeline network solver with formal
optimization, sparse linear algebra, and analytical validation benchmarks.

## Overview

| Feature | Class | Description |
|---------|-------|-------------|
| NLP Optimizer | `NetworkOptimizer` | BOBYQA and CMA-ES choke allocation |
| Multi-Objective | `NetworkOptimizer` | Weighted-sum Pareto front exploration |
| Sparse Solver | `NetworkLinearSolver` | Auto-selects Gaussian, Dense EJML, or Sparse CSC |
| Multiphase Caching | `LoopedPipeNetwork` | Reuses Beggs-Brill models across iterations |
| Benchmarks | `NetworkValidationBenchmarks` | 6 analytical/published verification cases |

## NLP Choke Optimization

The `NetworkOptimizer` replaces gradient-finite-difference with formal bound-constrained
optimization from Apache Commons Math:

- **BOBYQA**: Derivative-free trust-region (best for 2-20 variables, smooth objectives)
- **CMA-ES**: Population-based global optimizer (robust for noisy/multi-modal, 5-50 variables)

### Java Example

```java
LoopedPipeNetwork network = new LoopedPipeNetwork("MyNetwork");
network.setFluidTemplate(gas);
network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON);
network.setMaxIterations(200);
network.setTolerance(100.0);

// Build network with wells, chokes, and export
network.addSourceNode("res1", 200.0, 0.0);
network.addJunctionNode("wh1");
network.addJunctionNode("manifold");
network.addFixedPressureSinkNode("export", 50.0);

network.addWellIPR("res1", "wh1", "ipr1", 5e-6, false);
network.addChoke("wh1", "manifold", "choke1", 50.0, 80.0);
network.addPipe("manifold", "export", "export_pipe", 20000.0, 0.3, 0.00005);

// Create and configure optimizer
NetworkOptimizer optimizer = network.createOptimizer();
optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA);
optimizer.setObjectiveType(NetworkOptimizer.ObjectiveType.MAX_PRODUCTION);
optimizer.setMaxEvaluations(300);

// Run optimization
NetworkOptimizer.OptimizationResult result = optimizer.optimize();
System.out.println("Production: " + result.totalProductionKgHr + " kg/hr");
System.out.println("Converged: " + result.converged);
```

### Objective Types

| Type | Description |
|------|-------------|
| `MAX_PRODUCTION` | Maximize total mass flow at all sinks |
| `MAX_REVENUE` | Maximize price-weighted production |
| `MIN_COMPRESSOR_POWER` | Minimize total compressor power |
| `MAX_SPECIFIC_PRODUCTION` | Maximize production per unit power |

### Convenience Methods

```java
// Quick single-objective optimization
NetworkOptimizer.OptimizationResult result = network.optimizeProductionNLP();

// Multi-objective Pareto front (11 points)
List<NetworkOptimizer.OptimizationResult> pareto = network.optimizeMultiObjective(11);
```

## Multi-Objective Pareto Optimization

The multi-objective method sweeps a weight parameter $w$ from 0 to 1:

$$
f(x) = w \cdot \text{production}(x) - (1 - w) \cdot \text{power}(x)
$$

Each Pareto point represents a different production-vs-power tradeoff. Results include
`paretoWeight`, `totalProductionKgHr`, and `totalCompressorPowerKW`.

## Sparse Matrix Solver

`NetworkLinearSolver` automatically selects the optimal solver for the Newton-Raphson
Schur complement system:

| System Size | Solver | Reason |
|-------------|--------|--------|
| n ≤ 30 | Gaussian elimination | Backward compatible, fastest for small systems |
| 30 < n ≤ 100 | Dense EJML LU | Better numerical stability |
| n > 100 | Sparse CSC LU | Exploits sparsity for large networks |

All three solvers are also available directly:

```java
double[] x = NetworkLinearSolver.solve(matA, vecB, n);      // Auto-select
double[] x = NetworkLinearSolver.solveGaussian(matA, vecB, n); // Force Gaussian
double[] x = NetworkLinearSolver.solveDense(matA, vecB, n);    // Force Dense EJML
double[] x = NetworkLinearSolver.solveSparse(matA, vecB, n);   // Force Sparse EJML
```

## Multiphase Stream Caching

The multiphase head loss calculation now caches `PipeBeggsAndBrills` models on each
`NetworkPipe`, avoiding re-creation of `Stream` and solver objects on every iteration.
This is transparent — no API changes required.

## Validation Benchmarks

Six analytical/published benchmark cases verify solver accuracy:

| # | Benchmark | Verification |
|---|-----------|-------------|
| 1 | Single Pipe (Darcy-Weisbach) | Swamee-Jain analytical pressure drop |
| 2 | Two Parallel Pipes | Known flow split ratio $(D_1/D_2)^{5/2}$ |
| 3 | Triangle Loop | Mass balance conservation at all nodes |
| 4 | HC vs NR Cross-Verification | Both solvers converge to same solution |
| 5 | Pressure Monotonicity | Pressure decreases along flow direction |
| 6 | Sparse vs Dense Agreement | All three solvers produce identical results |

### Running Benchmarks

```java
List<NetworkValidationBenchmarks.BenchmarkResult> results =
    NetworkValidationBenchmarks.runAllBenchmarks();

for (NetworkValidationBenchmarks.BenchmarkResult r : results) {
    System.out.println(r.getSummary());
}
```

Or via the static convenience method on `LoopedPipeNetwork`:

```java
List<NetworkValidationBenchmarks.BenchmarkResult> results =
    LoopedPipeNetwork.runValidationBenchmarks();

for (NetworkValidationBenchmarks.BenchmarkResult r : results) {
    System.out.println(r.getSummary());
}
```

## Python Example

```python
from neqsim import jneqsim

LoopedPipeNetwork = jneqsim.process.equipment.network.LoopedPipeNetwork
NetworkOptimizer = jneqsim.process.equipment.network.NetworkOptimizer
SystemSrkEos = jneqsim.thermo.system.SystemSrkEos

gas = SystemSrkEos(298.15, 50.0)
gas.addComponent("methane", 0.85)
gas.addComponent("ethane", 0.10)
gas.addComponent("propane", 0.05)
gas.createDatabase(True)
gas.setMixingRule("classic")
gas.init(0)
gas.init(1)

network = LoopedPipeNetwork("GatheringNetwork")
network.setFluidTemplate(gas)
network.setSolverType(LoopedPipeNetwork.SolverType.NEWTON_RAPHSON)
network.setMaxIterations(200)
network.setTolerance(100.0)

# Build network...
network.run()

# Optimize
optimizer = network.createOptimizer()
optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA)
optimizer.setMaxEvaluations(300)
result = optimizer.optimize()
print(f"Production: {result.totalProductionKgHr:.0f} kg/hr")
```

## Related Documentation

- [Pipeline Network Optimization Notebook](../../examples/notebooks/pipeline_network_optimization.ipynb)
