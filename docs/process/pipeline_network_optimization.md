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
| Generalized NLP | `NetworkOptimizer` | Bounded source, sink, route, valve, compressor, and pump decisions |
| Typed Constraints | `NetworkConstraint` | Hard rejection and scaled soft-residual penalties |
| Composable Objectives | `NetworkObjective` | Throughput, power, economics, emissions, and Java callbacks |
| Automation | `ProcessAutomation` | Discoverable safe network node/edge addresses |
| Sparse Solver | `NetworkLinearSolver` | Auto-selects Gaussian, Dense EJML, or Sparse CSC |
| Multiphase Caching | `LoopedPipeNetwork` | Reuses Beggs-Brill models across iterations |
| Benchmarks | `NetworkValidationBenchmarks` | 6 analytical/published verification cases |

## Generalized whole-network optimization

Register finite-bounded variables, objective terms, and typed constraints. The
optimizer evaluates hydraulics and point quality on the same candidate and
restores all decision state after every evaluation.

```java
NetworkOptimizer optimizer = new NetworkOptimizer(network);
optimizer.setAlgorithm(NetworkOptimizer.Algorithm.BOBYQA);
optimizer.setDeterministicSeed(42L);

optimizer.addDecisionVariable(new NetworkDecisionVariable(
    "source.field-a.rate",
    NetworkDecisionVariable.Type.SOURCE_RATE,
    "field-a", "kg/hr",
    NetworkDecisionVariable.RateBasis.MASS,
    10000.0, 200000.0));
optimizer.addDecisionVariable(new NetworkDecisionVariable(
    "compressor.export.speed",
    NetworkDecisionVariable.Type.COMPRESSOR_SPEED,
    "export compressor", "rpm",
    NetworkDecisionVariable.RateBasis.NONE,
    3000.0, 9000.0));
optimizer.addDecisionVariable(new NetworkDecisionVariable(
    "route.north.availability",
    NetworkDecisionVariable.Type.ROUTE_ALLOCATION,
    "north route", "-",
    NetworkDecisionVariable.RateBasis.NONE,
    0.01, 1.0));

optimizer.addObjective(NetworkObjectives.maximizeThroughput(1.0));
optimizer.addObjective(
    NetworkObjectives.minimizeCompressorPower(0.02));
optimizer.addConstraint(NetworkConstraints.convergence());
optimizer.addConstraint(
    NetworkConstraints.nodePressure(
        "delivery", 45.0, 80.0, true));
optimizer.addConstraint(
    NetworkConstraints.qualityCompliance(true));

NetworkOptimizer.OptimizationResult result = optimizer.optimize();
```

`discoverDecisionVariables(...)` registers common source/sink rates, source
pressures, chokes, regulators, compressor/pump speeds, and edge availability.
Use explicit registration when bounds or rate basis differ by asset.

Hard constraints make a candidate infeasible. Soft constraints add the square
of the scaled residual, so a larger physical violation receives a larger
penalty. `NetworkCandidateEvaluation` reports decisions, rate bases, objective
terms, feasibility, residuals, active constraints, and solver diagnostics as
Java objects and JSON.

BOBYQA is a local derivative-free optimizer. CMA-ES is useful for discontinuous
availability/routing responses but requires more evaluations. Both require
finite bounds; neither is a proof of global optimality.

### ProcessAutomation addresses

Internal network values are discoverable through `ProcessAutomation`:

```text
allocation network.node.delivery.pressure
allocation network.source.field-a.rate
allocation network.sink.delivery.nomination
allocation network.edge.export.flowRate
allocation network.edge.export.availability
allocation network.choke.well-a.opening
allocation network.regulator.handover.setPoint
allocation network.compressor.export.speed
allocation network.pump.oil booster.speed
```

Address get/set performs unit conversion and rejects non-writable calculated
values. This lets generic automation and agentic optimization inspect network
internals without bypassing the bounded network API.

## Legacy choke optimization

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

### Legacy objective types

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

For generalized problems, construct `NetworkDecisionVariable`,
`NetworkConstraints`, and `NetworkObjectives` through `jneqsim` exactly as in
Java. The executed gas and oil notebooks below demonstrate JPype collection,
enum, and JSON handling.

## Related Documentation

- [Pipeline Network Optimization Notebook](https://github.com/equinor/neqsim/blob/master/examples/notebooks/pipeline_network_optimization.ipynb)
- [Gas Network Operations and Optimization](gas_network_operations)
- [Oil Pipeline and Terminal Operations](oil_network_operations)
- [Executed synthetic NCS gas example](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process/norwegian_ncs_gas_network_optimization.ipynb)
- [Executed synthetic NCS oil example](https://github.com/equinor/neqsim/blob/master/examples/notebooks/process/norwegian_ncs_oil_network_optimization.ipynb)
