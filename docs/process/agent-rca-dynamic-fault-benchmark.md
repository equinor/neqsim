---
title: "AgentRCA Dynamic Fault Benchmark"
description: "Normal-only, evidence-grounded root-cause analysis using a dynamic NeqSim gas/liquid separator process with sensor bias, leaks, blockage, and imposed slugging excitation."
keywords: "AgentRCA, root cause analysis, fault diagnosis, digital twin, dynamic simulation, sensor fault, gas leak, blockage, slugging"
---

## Purpose

`AgentRcaDynamicProcessBenchmark` is a deterministic NeqSim reproduction of the
normal-only, evidence-grounded parts of
[AgentRCA](https://arxiv.org/abs/2607.22385). It generates process data with a
real transient NeqSim flowsheet, learns only from normal operation, calculates
auditable statistical evidence, and ranks physical hypotheses without training
on faulty examples.

The synthetic process contains:

- separate PR-EOS gas and liquid feeds at 35 bara and 25 °C;
- a multiphase mixer and inlet restriction;
- a 4 m × 1 m horizontal separator with dynamic inventory;
- gas and liquid outlet valves at 5 bara;
- a downstream gas leak junction;
- pressure and level measurements.

The default window is 60 samples at 1 s, matching the PRONTO window duration
reported by Wei and Fink. The fluid, geometry and operating point are public
synthetic inputs for regression testing and teaching; they are not a design
case.

## Controlled scenarios

| Scenario | Physical injection | Principal evidence |
|---|---|---|
| `NORMAL` | No fault | All signals remain near the normal regime |
| `PRESSURE_SENSOR_BIAS` | +2 bara through `PressureTransmitter.setFault(BIAS, 2.0)` | Pressure mean rises while process flows and level remain physical |
| `EXPORT_GAS_LEAK` | Gas split ramps from 0 to 25% after the gas outlet valve | Delivered gas falls while separator inlet and liquid export remain near normal |
| `INLET_BLOCKAGE` | Inlet-valve fouling ramps from 0 to 0.75 | Effective Kv and separator inlet flow fall; both products respond |
| `MULTIPHASE_SLUGGING` | Periodic out-of-phase gas/liquid feed bursts with increasing amplitude | Coherent variance and correlation changes in liquid feed, vessel level and liquid export |

The leak branch is included in the simulated mass balance but excluded from the
diagnostic signals. `ScenarioRun.getLeakedMassKg()` retains the ground-truth
integral for validation.

The slugging scenario is deliberately called **imposed slugging excitation**.
It propagates controlled gas/liquid bursts through a dynamic separator, but it
does not predict hydrodynamic instability, slug frequency, slug length, or
terrain-induced severe slugging. Do not use it as validation of a slug-capturing
pipeline model.

## Evidence and reasoning API

```java
AgentRcaDynamicProcessBenchmark benchmark =
    new AgentRcaDynamicProcessBenchmark();

AgentRcaDynamicProcessBenchmark.BenchmarkResult result =
    benchmark.runBenchmark();

RcaDiagnosis leakDiagnosis =
    result.getDiagnosis(
        AgentRcaDynamicProcessBenchmark.Scenario.EXPORT_GAS_LEAK);

String topDiagnosis = leakDiagnosis.getTopHypothesis().getName();
String auditableEvidence = leakDiagnosis.toJson();
```

The diagnosis and evidence strings can be logged, stored with the process
snapshot, or passed to an optional narrative reasoning layer.

The reusable diagnostic layer is independent of this example flowsheet:

1. Build normal `RcaProcessWindow` objects using raw engineering values and
   operating-condition coordinates.
2. Fit `RcaNormalOperationModel` using normal windows only.
3. Describe physical expectations with `RcaFaultHypothesis` rules.
4. Call `RcaDiagnosisEngine.diagnose(...)`.
5. Inspect every `RcaDiagnosis.RuleTrace`, including observed value, scale,
   weight, signed support and engineering rationale.

`RcaNormalOperationModel` exposes:

- standardized Euclidean operating-regime matching;
- signed mean shifts;
- log variance and range ratios;
- normalized linear slope;
- lag-one autocorrelation shifts;
- pairwise Pearson-correlation discrepancies;
- a dimensionless screening-level anomaly score.

`RcaDiagnosis.toJson()` is the integration boundary for an optional external
language-model agent. Keep the numerical evidence and deterministic hypothesis
ranking available for audit even when an LLM adds a narrative explanation.

## Reproduction boundary

The July 24, 2026 AgentRCA preprint describes a convolutional autoencoder,
condition-specific statistics, diagnostic tools and a tool-augmented LLM. Its
implementation is stated to be released after paper acceptance and was not
public when this benchmark was implemented. This NeqSim reproduction therefore
implements:

- normal-only training;
- condition-specific regime matching;
- directional statistical, correlation and temporal evidence;
- a ranked hypothesis table with supporting and contradicting traces;
- the 60 s window convention;
- dynamic multiphase process faults.

It does **not** claim to reproduce:

- the paper's trained convolutional autoencoder;
- Qwen/GPT/Gemma inference or prompt traces;
- the reported PRONTO or Tennessee Eastman accuracy;
- exact PRONTO facility dynamics.

The public [PRONTO dataset](https://doi.org/10.5281/zenodo.1341583) remains the
appropriate external data benchmark once the authors' preprocessing and code
are available.

## Validation and safety

Focused tests verify:

- finite positive pressure, non-negative flows, and separator level within
  `[0, 1]`;
- integrated leak mass and final blockage severity;
- increased liquid-feed variance in the imposed slugging case;
- deterministic repeated execution with a fixed sensor-noise seed;
- finite behavior at 0.5 s and 1.0 s time steps;
- condition-specific regime matching and immutable window data;
- correct scenario within the Top-2 deterministic hypothesis ranking.

This is a diagnostic and teaching benchmark. It does not replace alarms,
shutdown systems, HAZOP, validated dynamic design tools, or qualified process
and control engineering review. AgentRCA itself is presented as a
human-supporting diagnostic layer rather than a low-latency safety system.

## References

1. Wei, A. and Fink, O. (2026), “Agentic Root Cause Analysis through
   Evidence-Grounded Reasoning,” arXiv:2607.22385,
   [https://doi.org/10.48550/arXiv.2607.22385](https://doi.org/10.48550/arXiv.2607.22385).
2. Stief, A. et al., PRONTO heterogeneous process dataset,
   [https://doi.org/10.5281/zenodo.1341583](https://doi.org/10.5281/zenodo.1341583).
3. [Dynamic simulation enhancements](dynamic-simulation-enhancements.md).
4. [Dynamic Simulation with DynamicProcessHelper](dynamic-simulation.md).
