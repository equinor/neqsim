---
title: "Process Replay of Plant Time Series"
description: "Replay historian time series through a NeqSim ProcessSystem or ProcessModel with tag bindings, convergence gating, residual statistics, baseline standardisation, CUSUM change points and CSV/JSON export."
---

## Overview

`neqsim.process.operations.replay.ProcessReplay` drives a `ProcessSystem` or `ProcessModel`
through a time series of plant data. It replaces ad-hoc replay scripts with one reusable loop:

1. Each row is optionally filtered (a `SteadyStateDetector` or a user `StepFilter`).
2. Input tags are converted and written with `ProcessAutomation.setVariableValueSafe(...)`.
3. The model runs with `runUntilConverged(maxIterations)`; its `RunStatus` gates the step.
4. On non-convergence an optional `RetryHook` can perturb the model before a retry.
5. Output addresses are read with `getVariableValueSafe(...)` and compared with measured tags.

A bad row never aborts the replay. It is recorded as `INPUT_INVALID` (missing, out-of-range or
rejected input), `NOT_CONVERGED`, `FAILED` (exception or failed unit) or `SKIPPED` (filtered), and
the next row is processed.

The replay builds on existing infrastructure: the automation facade
([process automation](../simulation/process_automation.md) describes the
addresses), `OperationalTagMap` from the
[operational evidence package](operational_evidence_package.md) (use
`ProcessReplay.fromTagMap(process, tagMap)`: `INPUT` bindings become inputs, `BENCHMARK` bindings
become outputs) and the `SteadyStateDetector` from data reconciliation. For historian access see
[plant data and tagreader](plant-data-tagreader.md).

## Model state between steps

| Mode | Behaviour | When to use |
|------|-----------|-------------|
| Warm start (default) | One model instance; recycles, adjusters and controllers start from the previous step. After a failed step the model is restored to its pre-replay state (`setRestoreAfterFailure(true)`, default). | Long, smooth time series; fastest |
| Cold start (`setWarmStart(false)`) | Every row runs on a fresh deep copy of the model as it was when `run` was called. The model passed in is not modified. | Independent snapshots, sparse or shuffled rows, reproducibility |

Address auto-correction by fuzzy matching is disabled by default
(`setAllowAutoCorrection(false)`) so that a mistyped binding is reported instead of silently
writing to another variable.

## Example

The example replays 50 one-minute rows through a feed, cooler and separator flowsheet. The
synthetic data contain a hidden 3 K cooler bias from row 30.

```java
ReplayTimeSeries data = ReplayTimeSeries.uniform(50, 60.0)
    .addColumn("PT-100", pressure)
    .addColumn("TT-200", coolerTemperature)
    .addColumn("TT-300", separatorGasTemperature);

ProcessReplay replay = new ProcessReplay(process)
    .addInput(ReplayBinding.input("PT-100", "feed.pressure", "bara"))
    .addInput(ReplayBinding.input("TT-200", "cooler.outletTemperature", "C")
        .withValidRange(-20.0, 80.0))
    .addOutput(ReplayBinding.output("separator.gasOutStream.temperature", "TT-300", "C"))
    .setMaxIterations(5)
    .setWarmStart(true);

ReplayResult result = replay.run(data);
result.setBaselineWindow(0, 25);
ReplayResult.WindowStatistics event = result.getWindowStatistics("TT-300", 30, 50);
List<ReplayResult.ChangePoint> changes = result.detectChangePoints("TT-300", 0.5, 5.0, 1.0e-6);
String summary = result.getSummary();
String csv = result.toCsv();
String json = result.toJson();
```

`event.getBias()` is close to $-3$ K and `changes.get(0).getIndex()` is 30. The snippet is
exercised by `ProcessReplayDocExampleTest`.

Bindings convert data to the model unit with `withLinearConversion(scale, offset)` and an optional
`withTransform(DoubleUnaryOperator)`; `withValidRange(min, max)` marks out-of-range inputs as
invalid and out-of-range measurements as missing.

## Residual statistics and change points

The residual of output tag $j$ at row $i$ is $r_{ij} = \hat{y}_{ij} - y_{ij}$ (prediction minus
measurement). Only converged rows with a valid measurement are used.

- `getWindowStatistics(tag, start, end)` returns the bias $\bar{r}$, the sample standard deviation
  and the RMS over rows `[start, end)`.
- `setBaselineWindow(start, end)` stores the baseline mean $\mu_j$ and standard deviation
  $\sigma_j$; `getStandardizedResiduals(tag, minSigma)` returns
  $z_{ij} = (r_{ij} - \mu_j)/\max(\sigma_j, \sigma_{min})$.
- `detectChangePoints(tag, k, h, minSigma)` runs a two-sided tabular CUSUM on $z$:

$$
S^+_i = \max\left(0, S^+_{i-1} + z_i - \ell - k\right), \qquad
S^-_i = \max\left(0, S^-_{i-1} - z_i + \ell - k\right)
$$

A change is confirmed when $S^+$ or $S^-$ exceeds $h$. The change row is the maximum-likelihood
mean-shift split between the last zero of the sum and the confirmation row. The reference level
$\ell$ (initially zero) then moves to the mean of $z$ over at least
`ReplayResult.LEVEL_SEGMENT_ROWS` rows after the change, so a sustained shift is reported once.
Typical settings are $k = 0.5$ and $h = 4$ to $5$.

For continuous online drift monitoring use `ModelDriftMonitor` in
`neqsim.process.operations.continuous`; `ProcessReplay` is the offline, batch counterpart.

## Export

- `toCsv()` / `writeCsv(path)`: columns `index, time, status, attempts`, then `input:<tag>` per
  input, and `predicted:<tag>`, `measured:<tag>`, `residual:<tag>` (plus `z:<tag>` once a baseline
  is set) per output, and `message`. `ReplayTimeSeries.fromCsvLines(...)` reads it back.
- `toJson()` / `writeJson(path)`: summary counts per status, overall and baseline statistics,
  change points and the per-step records (non-finite numbers are written as `null`).

## Related

- [Operational evidence package](operational_evidence_package.md)
- [Plant data and tagreader](plant-data-tagreader.md)
- [Data reconciliation and parameter estimation](../calibration/data_reconciliation_parameter_estimation.md)
