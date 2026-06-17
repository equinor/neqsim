---
title: Parallel Scenario Sweeps with Clone-and-Run
description: How to run process scenario sweeps and Design-of-Experiments (DoE) in parallel safely by copying a base ProcessSystem. Covers ProcessSystem.copy() deep-copy independence, thread safety, and a worked parameter-sweep example.
keywords: "parallel, scenario sweep, DoE, copy, clone, thread safety, ProcessSystem, parameter study, multithread"
---

# Parallel Scenario Sweeps with Clone-and-Run

When running a parameter sweep or Design-of-Experiments (DoE) study, you often want to
evaluate many variations of the same flowsheet — for example, the compressor power at a
range of discharge pressures. Because each scenario is independent, these runs can be
executed **in parallel** for a large speedup.

The safe pattern is **clone-and-run**: build a base `ProcessSystem` once, create an
independent `copy()` for each scenario, mutate the copy, and run the copies concurrently.

## Why It Is Safe

`ProcessSystem.copy()` performs a **serialization deep copy** (via
`ObjectOutputStream` / `ObjectInputStream`). The result is a fully independent object
graph — equipment, streams, and the underlying thermodynamic fluids are all duplicated,
not shared by reference. Mutating or running one copy therefore has no effect on the base
system or on any other copy, which makes it safe to run copies on separate threads.

> **Key rule:** Never run the *same* `ProcessSystem` instance from multiple threads. Give
> each thread its own `copy()`.

## Worked Example

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.processmodel.ProcessSystem;

// 1. Build a base flowsheet once.
ProcessSystem base = buildScenario(100.0); // feed -> separator -> compressor

// 2. Create an independent copy per scenario and submit to a thread pool.
ExecutorService pool = Executors.newFixedThreadPool(8);
List<Future<Double>> futures = new ArrayList<Future<Double>>();
for (int i = 0; i < 16; i++) {
  final double outletPressure = 110.0 + i;
  final ProcessSystem scenario = base.copy(); // deep, independent copy
  futures.add(pool.submit(new Callable<Double>() {
    @Override
    public Double call() {
      scenario.setVariableValue("Compressor.outletPressure", outletPressure, "bara");
      scenario.run();
      return ((Compressor) scenario.getUnit("Compressor")).getPower();
    }
  }));
}

// 3. Collect results.
List<Double> powers = new ArrayList<Double>();
for (Future<Double> f : futures) {
  powers.add(f.get());
}
pool.shutdown();
```

Each `Callable` works on its own `copy()`, so the runs do not interfere. The parallel
results match what you would obtain by running the same scenarios one after another.

## Verification

This guarantee is covered by a regression test,
`ProcessSystemParallelCloneTest`, which:

- runs 16 scenarios sequentially as a baseline, then runs independent copies concurrently
  on an 8-thread pool and asserts each parallel result matches its sequential baseline
  within a tight tolerance, and
- mutates and runs one copy and asserts the base system's results are unchanged.

## When to Use This

| Use case | Pattern |
|----------|---------|
| Parameter sweep / DoE | `copy()` per point, run copies in parallel |
| Monte Carlo uncertainty | `copy()` per sample, run copies in parallel |
| Sensitivity / tornado study | `copy()` per perturbation, run copies in parallel |
| Single flowsheet with recycles | Run a single instance; use `runOptimized()` / `runHybrid()` for in-run parallelism |

For internal (within a single run) parallelism of a feed-forward or recycle flowsheet,
see the execution strategies in [ProcessSystem](process_system) and
[Graph-Based Simulation](graph_simulation).

---

## Related Documentation

- [ProcessSystem](process_system) — execution strategies and thread-safe shared-stream handling
- [ProcessModel](process_model) — multi-area model coordination
- [Process Serialization](../../simulation/process_serialization) — the deep-copy mechanism behind `copy()`
