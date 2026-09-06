---
title: Large steady-state process performance benchmark
description: Reproducible paired JVM measurements and numerical qualification of multiphase stability work in large process models.
---

# Large steady-state process performance benchmark

This study compares NeqSim 3.19.0 master
`3abb213bd22fd761cb4f7041785c447ecf457bed` with a bounded optimization of
`TPmultiflash.stabilityAnalysis()`. It measures complete steady-state process
execution, including changed operating conditions, rather than isolated EOS calls.
The fixtures use synthetic, public compositions; they are not calibrated plant models.

## Bottleneck and selected change

An initial Java Flight Recorder profile of a 44-unit, four-train SRK process with
mixers found multiphase stability analysis in 467 of 644 sampled stacks (72.5%).
Physical-property initialization appeared in 24 samples (3.7%). These are sampled
stack proportions, including warmup, not precise exclusive CPU-time fractions.

The retained implementation removes repeated system/phase/component lookups inside
the stability iteration and reuses an already calculated exponential in the
pure-component successive-substitution and DEM paths. Phase references are obtained
after initialization. Newton retains its original exponential of the logarithm of
the squared trial variable, preserving that path's floating-point rounding.

There is no cache across flashes, new shared state, skipped stability trial, changed
initial guess, altered convergence threshold, or change to iteration acceptance.
The optimization requires no user setting and applies when these existing stability
paths execute.

An alternative shared-equilibrium splitter implementation was evaluated and rejected.
On the 162-unit process it achieved only 3.06% median paired improvement (two of three
wins), below the 5% representative-workload acceptance criterion. Its additional
model guards and scaling behavior are not included in the delivered implementation.

## Workloads and measurements

`LargeProcessSteadyStateBenchmark` builds serial heating/cooling/valve chains,
independent parallel trains, a splitter feeding parallel trains, isobaric thermal
recycles, a CPA water-bearing control, and connected `ProcessModel` areas with
separation and compression. Size and execution strategy are explicit arguments.

The SRK feed contains nitrogen, CO2, methane, ethane, propane, isobutane, n-butane,
isopentane, n-pentane, n-hexane, n-heptane, and n-octane. Its mole fractions are
0.01, 0.02, 0.65, 0.10, 0.06, 0.02, 0.03, 0.015, 0.015, 0.03, 0.03, and 0.02.
CPA adds 0.01 molar parts water and normalizes the mixture, using mixing rule 10;
SRK uses classic mixing rule 2. Multiphase checking is enabled.
The nominal feed is 12,000 kg/h, 313.15 K, and 80 bara.
Changed-state samples alternate feed flow by 1% and temperature by 0.5 K.

Timing excludes model construction, result validation, and JSON output. Cold mode
times the first solve of fresh models; it is not a fresh JVM for every sample.
Per-unit profiling is enabled identically for both versions. Parallel unit durations
overlap and cannot be added to obtain process wall time. Reported allocation is for
the calling thread only and excludes worker threads.

Each pair uses fresh JVMs and alternates baseline/candidate order. The runner stores
individual samples, fork medians and means, paired gains, and engineering comparisons.
The result is platform- and workload-specific; no wall-clock assertion is installed
in ordinary CI. Broad accuracy or universal simulator speed claims are not implied.

## Reproduction

Compile production classes from the recorded baseline and candidate in separate
directories. Compile the same benchmark sources against each compatible classpath,
and include the project's runtime/test dependencies. The benchmark runs with Java 8
compatible source; the measurements in this study use OpenJDK 17.

```text
java -Xms512m -Xmx512m -XX:ActiveProcessorCount=4 -cp <classpath> \
  neqsim.process.processmodel.LargeProcessSteadyStateBenchmark \
  serial changed 40 60 20 sequential <output.json>

python devtools/benchmark_large_process.py \
  --baseline-classpath <baseline-classpath> \
  --candidate-classpath <candidate-classpath> \
  --output <output-directory> --workload serial --size 20 \
  --mode changed --strategy sequential --pairs 5 --warmups 40 --repeats 60

java -cp <classpath> \
  neqsim.thermodynamicoperations.flashops.StabilityOptimizationBenchmark \
  <matrix-output.json> 1 all
```

The process runner fails on nonconvergence or failed mass/component/energy checks.
The paired driver also rejects changed phase counts, product conditions, duties,
checksums, or iteration counts outside its explicit comparison tolerances, and
separately reports exact equality of every recorded engineering field.

The flash matrix records complete phase compositions, fractions, fugacity
coefficients, thermodynamic properties and residuals for SRK, PR, CPA, water-bearing,
CO2-rich, hydrogen-rich, trace-component, near-boundary and electrolyte cases.
It preserves failed baseline cases as explicit evidence rather than hiding them.

## Results and validation

Measurements use OpenJDK 17.0.20, Linux 6.18.35 x86-64, an AMD EPYC 9V74 host,
G1 GC, a fixed 512 MiB heap, and four JVM-visible processors inside an eight-CPU
container quota. This is a shared virtual environment, not dedicated production hardware.

| Workload | Units | Baseline median fork, ms | Candidate median fork, ms | Median paired gain | Paired wins |
| --- | ---: | ---: | ---: | ---: | ---: |
| Serial, changed feed | 62 | 63.460 | 55.956 | 8.92% | 5/5 |
| Independent trains, changed feed, automatic dataflow | 88 | 24.093 | 22.202 | 12.58% | 3/5 |
| Splitter and processing trains, changed feed | 162 | 164.704 | 151.362 | 8.54% | 4/5 |
| Four recycle loops, changed feed | 60 | 255.600 | 229.530 | 10.02% | 5/5 |
| CPA water-bearing control, changed feed | 26 | 594.878 | 600.465 | -3.03% | 2/5 |
| Four connected process areas, changed feed | 34 | 78.627 | 77.118 | -1.74% | 2/5 |
| Serial, first solve of fresh model | 62 | 72.535 | 65.179 | 10.66% | 2/3 |
| Serial, unchanged repeated solve | 62 | 57.824 | 57.684 | 2.99% | 2/3 |

A positive paired gain means faster execution. Each paired gain is calculated from
that pair's fork medians, then the gains are median-aggregated; it is therefore not
the ratio of the two separately aggregated timing columns. CPA and multi-area controls
show mixed small changes and **no demonstrated benefit**. These results support a
roughly 9–13% improvement for the tested state-changing SRK workloads, not a general
speedup for every process or equation of state.

Serial changed uses 40 warmups/60 measured runs per fork; wide uses 120/80; splitter,
CPA and multi-area use 20/30; recycle uses 15/20; cold uses 10/15; unchanged uses 40/60.
The initial wide 20/30 screen was invalid for steady throughput: some forks fell from
about 80 to 22 ms, or 170 to 28 ms, during measurement as JVM compilation progressed.
The long-warmup rerun above is authoritative; unfavorable initial results remain in
[the machine-readable evidence](../../devtools/baselines/steady_state_performance_20260906.json).

### Numerical evidence

- Every recorded engineering field in the paired process runs is exactly equal
  between baseline and candidate, including iteration counts and balance residuals.
- An additional full-product comparison across serial, splitter, CPA, multi-area and
  recycle workloads checks individual enthalpies, component molar rates, phase types,
  fractions, compositions and compressibility. All ten operating-point comparisons
  match exactly; the long-warmup parallel run also records and compares these fields.
- All 96 complete-flash snapshots match exactly, including phase topology, composition,
  fractions, fugacity coefficients, Gibbs energy, enthalpy, entropy and density.
- Maximum matrix material residual is `4.675e-12`, log-fugacity residual `5.235e-11`,
  phase-normalization residual `4.774e-15`, and beta-normalization residual `1.110e-16`.
- The strict matrix gates pass 94/96 states for both versions. Two existing ordinary
  PR near-cricondenbar snapshots have mole-inventory residual `1.000018e-12`, just above
  the unchanged `1e-12` gate. They are retained as failures; their equilibrium residuals
  and complete states are unchanged. This optimization does not claim to repair them.
- Worst process component/mass residual is approximately `6.400e-7` in the recycle
  workload, and worst normalized energy residual is `1.704e-7`; both equal baseline.

The focused Java suite passes **136 tests across 21 classes**, including multiphase
phase disappearance, aqueous and CPA stability, ionic systems, hydrogen and sour-gas
cases, PH flashes, mixer/separator/compressor behavior, dataflow and multi-area execution.
The new invariant test explicitly checks one-, two- and three-phase topology and
cold/repeated/changed-state behavior. This is numerical and regression evidence, not
independent experimental qualification of every thermodynamic model.

Production and benchmark sources compile with Java 8 source compatibility. Tests and
measurements ran on Java 17; an actual Java 8 runtime, Java 21 matrix, Windows, and the
full repository suite remain for CI. Formatting and documentation gates are recorded
in the pull request. No numerical threshold or existing test was weakened.

The evidence JSON retains raw per-fork samples, every paired result, all flash snapshots,
full-product comparison digests, and the rejected/insufficient-warmup investigations.
The reusable benchmark runner writes complete per-run product snapshots when rerun.

Related work: process performance roadmap #2939 and flash/stability roadmap #2937.
