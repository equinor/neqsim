---
title: Cached K-Value Fast Process Simulation
description: "Guide to the cached K-value fast process simulation method for NeqSim ProcessSystem scenario sweeps. Covers base-case extraction, Rachford-Rice K-value routing, supported equipment, limitations, API usage, and benchmarking."
---

# Cached K-Value Fast Process Simulation

The cached K-value fast simulator is a proxy method for running many process-flow scenarios after one rigorous base-case `ProcessSystem` solve. It is intended for screening, production allocation studies, digital-twin sensitivity sweeps, and agentic optimization loops where source rates change frequently but the thermodynamic operating envelope stays close to a calibrated reference case.

The method lives in `neqsim.process.fastsimulation` and is exposed from `ProcessSystem` through:

- `createKValueProcessSimulator()`
- `runFastKValueSimulation()`

## When To Use It

Use this method when:

- A rigorous `ProcessSystem.run()` has already established a trustworthy base case.
- Later cases mainly change source total flow rates, while source compositions, separator pressures, and heater or valve set points remain close to the base case.
- You need component-resolved source-to-product rates without paying for a full EOS flash in every unit on every trial.
- You want a proxy method similar in spirit to the recovery-factor allocation solver, but with separator splits recomputed from cached `K = y/x` values instead of fixed component split factors.

Do not use it as the final design calculation when pressures, temperatures, fluid compositions, phase behaviour, or three-phase separation materially change. Re-run the rigorous process and rebuild the proxy when the thermodynamic envelope moves.

## Method

The workflow is:

1. Build and run a normal rigorous process model.
2. Extract a `KValueProcessSimulator` from the solved process.
3. Run fast source-rate scenarios with `runWithSourceFlowMultipliers(...)` or `runWithSourceFlowRates(...)`.
4. Query component and total flows from the `KValueProcessResult`.

During extraction, the simulator scans the process topology using each unit's inlet and outlet streams. It creates a component slate, stores source stream component flows, and builds one `KValueUnitProfile` per unit operation that has outlet streams.

For separator-like two-outlet units, the base-case gas and liquid outlet compositions are converted to component K-values:

$$
K_i = \frac{y_i}{x_i}
$$

For a later inlet composition $z_i$, the vapour fraction beta is solved from the Rachford-Rice equation:

$$
\sum_i \frac{z_i (K_i - 1)}{1 + \beta (K_i - 1)} = 0
$$

The component flows are then routed without an EOS flash:

$$
x_i = \frac{z_i}{1 + \beta (K_i - 1)}
$$

$$
y_i = K_i x_i
$$

For gas/oil/water separators, the oil outlet is used as the reference phase. The method stores gas/oil ratios and water/oil distribution ratios:

$$
K^g_i = \frac{y_i}{x_i}
$$

$$
K^w_i = \frac{w_i}{x_i}
$$

The fast rerun solves two coupled Rachford-Rice-style equations for gas and water phase fractions. With oil fraction $\beta_o = 1 - \beta_g - \beta_w$:

$$
\sum_i \frac{z_i (K^g_i - 1)}{1 + \beta_g (K^g_i - 1) + \beta_w (K^w_i - 1)} = 0
$$

$$
\sum_i \frac{z_i (K^w_i - 1)}{1 + \beta_g (K^g_i - 1) + \beta_w (K^w_i - 1)} = 0
$$

Single-outlet units pass component flows through. Unsupported multi-outlet units fall back to frozen base-case component split factors. Recycle loops are handled by fixed-point iteration over stream component flow vectors.

## Supported Scope

| Model element | Fast-simulation behaviour |
| ------------- | -------------------------- |
| Source streams | Base component composition is preserved; total source rate can be scaled or replaced. |
| Mixers and single-outlet units | Component molar flows are passed through conservatively. |
| Two-outlet gas/liquid separators | Cached gas/liquid K-values are used with Rachford-Rice routing for the current inlet composition. |
| Three-outlet gas/oil/water separators | Cached gas/oil and water/oil ratios are used with a two-variable phase-fraction solve for the current inlet composition. |
| Wrapper product streams | Product aliases such as `ExportGas` are mapped back to their internal separator outlet flow. |
| Recycles | Stream flows are iterated to a fixed-point tolerance. |
| Unsupported multi-outlet units | Frozen base-case component split factors are used as a fallback. |

## API Example

The example assumes `process` is a solved `ProcessSystem` containing source streams named `Well-A` and `Well-B`, and a product stream alias named `ExportGas`. The same API calls are covered by `KValueProcessSimulatorTest`.

```java
process.run();

KValueProcessSimulator simulator = process.createKValueProcessSimulator();
simulator.setMaxIterations(50);
simulator.setTolerance(1.0e-9);

Map<String, Double> multipliers = new LinkedHashMap<String, Double>();
multipliers.put("Well-B", 1.25);

KValueProcessResult result = simulator.runWithSourceFlowMultipliers(multipliers);
double exportGasKgPerHr = result.getStreamTotalFlow("ExportGas", "kg/hr");
double methaneKgPerHr = result.getStreamComponentFlow("ExportGas", "methane", "kg/hr");
double totalProductKgPerHr = result.getTerminalTotalFlow("kg/hr");
String resultJson = result.toJson();
```

You can also replace total source flow rates while keeping the base-case source compositions:

```java
Map<String, Double> sourceRates = new LinkedHashMap<String, Double>();
sourceRates.put("Well-A", 1200.0);
KValueProcessResult rateCase = simulator.runWithSourceFlowRates(sourceRates, "kg/hr");
```

## Benchmarking

`KValueProcessSimulator` includes a convenience benchmark that compares repeated proxy cases against repeated rigorous `ProcessSystem.run()` cases for source-flow multipliers:

```java
KValueProcessBenchmarkResult benchmark = simulator.benchmarkSourceFlowMultipliers("Well-B",
    new double[] { 0.80, 1.00, 1.20 });
double speedup = benchmark.getSpeedup();
String benchmarkJson = benchmark.toJson();
```

The focused unit test uses a multistage PR-EOS HP/MP/LP separation process based on the same reference process family as the recovery-factor production-allocation benchmark. The proxy is expected to be faster because it avoids repeated EOS flashes while retaining component-resolved separator routing around the calibrated base case.

## Result Naming

Internal unit outlets use keys of the form `unitName.outletStreamName`, for example:

- `Inlet Separator.gasOutStream`
- `Inlet Separator.liquidOutStream`

If the process also contains wrapper streams such as `ExportGas` or `StabOil`, the result exposes those aliases as stream names too. Use `result.getStreamNames()` or `result.toJson()` to inspect the available keys for a model.

## Limitations

- The method is a local proxy, not a replacement for a rigorous process simulation.
- Source composition changes are not yet an API input; source totals are scaled from the extracted base composition.
- Separator K-values are frozen from the base case, so large pressure, temperature, or composition changes require a new rigorous run and a new simulator.
- Heaters, valves, compressors, and other single-outlet units do not recalculate thermodynamic state in the proxy path. They pass component flows to downstream units.
- Three-phase separator routing uses cached distribution ratios, but it remains a local proxy around the base case. A future hybrid flash route can seed rigorous three-phase flashes from saved K-values to reduce flash iterations while retaining full EOS recalculation.

## Related Documentation

- [Production allocation](production-allocation.md)
- [ProcessSystem](processmodel/process_system.md)
- [Graph simulation](processmodel/graph_simulation.md)
- [Separators](equipment/separators.md)