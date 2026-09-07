---
title: Industrial S/M Optimization Benchmark Evidence
description: Executed small-guide and medium multi-train recycle evidence for the industrial ProcessSystem optimization baseline.
---

# Industrial S/M Optimization Benchmark Evidence

This page records the maintained S/M qualification evidence for the
[industrial optimization baseline](industrial-process-optimization-baseline). It is measurement
evidence for roadmap [#3154](https://github.com/equinor/neqsim/issues/3154), not a performance
improvement claim and not qualification of the flowsheets for design or operations.

The initial baseline used unmodified NeqSim `master` commit
`f3a2cf5f0891322ab2462817f0c06d0d9409f1f6`. The harness is additive test code. It does not change
process calculations, thermodynamics, scheduling, recycle convergence, caching, or optimizer search
behavior.

## Engineering question and stop boundary

Can the frozen small guide case and a deterministic 25–50 unit multi-train case emit attributable,
repeatable records for convergence, equipment work, balances, utilization, invalid proposals,
constraint changes, and reversible line-up actions using current public APIs?

The initial benchmark increment stopped after executing and versioning cases S and M. It did not add plant-wide
constraint or shared-resource identity, total-power or common-shaft constraints, separator/piping
evidence adapters, solver orchestration, or execution-layer instrumentation.

## Cases and acceptance criteria

| Case | Fixed definition | Acceptance criteria |
|---|---|---|
| S | SRK guide fluid; feed, HP separator, and compressor; 5,000 kg/hr; 100 bara discharge; synthetic 165 kW installed power basis | Solved and finite; mass residual at most 0.1 kg/hr; unchanged product exactly repeatable; installed-power change binds; `NaN` proposal is rejected before feed mutation. |
| M | SRK rich gas; 27 units; three compression trains; 90,000 kg/hr; one 5% tail recycle; 120 bara discharge | 25–50 units and at least one recycle; all executed modes solved; mass residual at most 0.1 kg/hr; five unchanged repetitions per fork exactly repeat product; restored product within 0.1 kg/hr of cold state. |

All rates use a mass basis in kg/hr, pressures use bara, compressor power uses kW, and pipe velocity
uses m/s. The fluids and installed limits are deterministic synthetic benchmark inputs; no
proprietary plant data is used. Capacity evidence from default equipment constraints can still have
unset provenance or validity, which is recorded rather than inferred.

## How to run

The class is tagged `slow`. NeqSim excludes slow tests by default, so both the selected group and an
empty exclusion list are mandatory. A zero-test Maven result is not valid benchmark evidence.

First prepare Maven as required by the NeqSim development workflow. Then use the checked-in runner;
it executes five independent Maven/JVM forks, rejects a zero-test or malformed report, preserves
each exact harness report under `forks[].rawReport`, derives the statistics, and validates the final
aggregate before writing it.

```bash
eval "$(python3 /path/to/work-with-neqsim/scripts/prepare_maven.py)"
python devtools/industrial_sm_benchmark.py run \
  --baseline-commit "$(git rev-parse HEAD)" \
  --forks 5 \
  --raw-dir target/industrial-sm-benchmark \
  --output target/industrial-sm-baseline.json
```

Validate a previously generated or checked-in aggregate without running Maven:

```bash
python devtools/industrial_sm_benchmark.py validate \
  --input docs/process/optimization/benchmarks/industrial-sm-baseline-f3a2cf5f.json
```

The aggregate uses schema `2.0`. It records the generator, raw schema, fork count, wall time,
canonical byte count and SHA-256 digest for every preserved raw report. Validation recomputes the
full aggregate from those reports and fails if any field or statistic differs. Each raw report
contains deterministic calculation identities, case and topology counts, every mode,
per-equipment calls and timing, run status, mass-balance residuals, constraint evidence, the
heap-before/after proxy, observation size, failure or restoration outcome, and unsupported metrics
with reasons.

## Reference environment

- OpenJDK 17.0.19, Ubuntu build, 64-bit Server VM
- Linux 6.18.35, amd64
- AMD EPYC 9V74 host, nine processors available to the container
- Maven Surefire heap maximum 3 GiB
- SRK thermodynamic model with classic mixing rule
- five independent Maven/JVM forks; one harness execution per fork
- no warm-up before each cold case; five unchanged M repetitions inside each fork

The CPU model is captured here because portable Java 8 does not expose it. Exact JVM, OS, processor
count, heap, and JVM input arguments are also stored in the machine-readable record.

## Measured baseline

| Observation | Samples | Median | Median absolute deviation | Range |
|---|---:|---:|---:|---:|
| Maven-fork-inclusive S/M harness wall time | 5 forks | 49,955.003 ms | 5,482.386 ms | 41,493.165–67,879.749 ms |
| S cold process solve | 5 forks | 110.738 ms | 20.099 ms | 90.192–147.210 ms |
| S unchanged process solve | 5 forks | 0.192 ms | 0.005 ms | 0.144–0.197 ms |
| M cold process solve | 5 forks | 422.029 ms | 147.526 ms | 274.503–822.562 ms |
| M unchanged process solve | 25 runs | 140.867 ms | 44.528 ms | 59.881–602.387 ms |

The five Maven-fork-inclusive samples are `67879.749`, `41493.165`, `55437.389`, `48105.170`,
and `49955.003` ms. They include Maven startup and any compile work and therefore are not a pure
process-solve performance metric. The exact preserved raw reports and derived measurements are in
[`industrial-sm-baseline-f3a2cf5f.json`](benchmarks/industrial-sm-baseline-f3a2cf5f.json).

Every unchanged M run reproduced the cold product mass rate exactly within double precision. Across
the five forks, the restored-line-up product differed from cold by a median `0.0258211 kg/hr`; the
largest observed difference was `0.0258211 kg/hr`, below the `0.1 kg/hr` acceptance criterion. The
largest unit mass-balance residual across 50 M mode records was `0.0222922 kg/hr`. Serialized M
utilization observations ranged from 24,429 to 24,733 bytes. All 70 successful mode records retain
their per-equipment execution maps; total equipment calls range from 1 to 63 per mode.

Case S rejected a non-finite external proposal before it mutated the feed. Case M observed the
default export-pipe velocity constraint, then a controlled installed feed-pipe velocity constraint
after the limit change, and completed a train-unavailable action plus full replay restoration.

The earlier
[`industrial-sm-baseline-5a851750.json`](benchmarks/industrial-sm-baseline-5a851750.json) is retained
as historical evidence but is superseded. It normalized harness fields without a checked-in
aggregation contract and cannot satisfy the reproducibility gate used by later roadmap increments.

## Configuration and coverage qualification — 7 September 2026

The [local validation record](benchmarks/plant-capacity-validation-3847d75f.json) retains the
146-test regression result, executed commands, JPype evidence, and validation limits for this
same source commit. Hosted CI and accountable maintainer/domain approval remain separate gates.

The maintained harness was rerun against both the current baseline and the frozen configuration
and coverage candidate. Each side executed five independent Maven/JVM forks with one actual
JUnit harness test per fork, zero failures, zero errors, and zero skipped tests. Each aggregate
passed the existing schema `2.0` validator with 70 successful mode records and five rejected
invalid candidates. Both runs used Python 3.12.13, Maven 3.9.16, OpenJDK 17.0.20 on Linux
6.18.35/amd64, eight available processors, a 1 GiB Maven heap, and a 3 GiB Surefire heap.

| Evidence | Exact source commit | Aggregate |
|---|---|---|
| Baseline | `e8895a9f2a400a5a989622c6976440852f7b5089` | [Preserved baseline](benchmarks/industrial-sm-baseline-e8895a9f.json.gz) |
| Candidate | `3847d75fc9298b9677b2a39c91d886fa6bf92460` | [Preserved candidate](benchmarks/industrial-sm-candidate-3847d75f.json.gz) |

The baseline ran in a separate clean checkout. The candidate was measured from a workspace whose
complete Git tree was independently verified before and after measurement as
`436c7e5b35be79be388443b06fe720314edb7d7f`, identical to the published candidate commit's tree.
The local `HEAD` still pointed to the baseline; the candidate commit argument identifies the
verified source tree, not that local pointer. The measured source remained frozen throughout the
five forks. The documentation and compressed evidence were added after measurement.

| Observation | Baseline | Candidate | Acceptance |
|---|---:|---:|---|
| Actual harness tests | 5 passed | 5 passed | All five forks execute |
| M unchanged product differences, 25 observations | All zero | All zero | Exactly repeatable |
| Maximum M unit mass residual | 0.022292153 kg/hr | 0.022292153 kg/hr | At most 0.1 kg/hr |
| Maximum M restoration difference | 0.025876843 kg/hr | 0.025884452 kg/hr | At most 0.1 kg/hr |
| Median S cold solve | 55.924 ms | 66.792 ms | Recorded observation |
| Median S unchanged solve | 0.160 ms | 0.192 ms | Recorded observation |
| Median M cold solve | 151.307 ms | 189.817 ms | Recorded observation |
| Median M unchanged solve, 25 observations | 38.346 ms | 44.422 ms | Recorded observation |
| Median Maven-fork-inclusive wall time | 22,262.180 ms | 22,631.001 ms | Includes startup and compilation |

The conservation, convergence, invalid-candidate, repeatability, and restoration gates pass on
both sources. Candidate solve-time medians are higher in these sequential host observations;
the measurements do not isolate the cause or establish a performance improvement. No speedup
claim is made. The five-fork wall-time totals were 128.385 s before and 116.614 s after; different
compilation work contributes to those totals. Timing distributions and per-mode execution counts
remain available in the preserved reports. Large-plant qualification and the shared-power and
common-shaft transitions remain open roadmap acceptance work.

The exact aggregates are stored as deterministic gzip files with an empty filename and `mtime=0`.
Decompression reproduces the original aggregate bytes, including every fork's raw report and its
canonical checksum. SHA-256 values for independent verification are:

| Record | Aggregate JSON SHA-256 | Gzip SHA-256 |
|---|---|---|
| Baseline | `0e1444f4d8a18447dfea7ab4e919f9b27268c4e5daaf798f13d701d421b89d31` | `6af77d4cfcba538396b49d976b2ee5f5a75f2946f8a4a24f52ebf4ac4d52242b` |
| Candidate | `f1a5a52fbd4137654b57407364829311cb62bb787f5f3d6e9c23b7f147ac49e2` | `957ce260efad225c65e0b37a68761e2b317499680c176aa189d317eae967c67d` |

To validate the stored evidence, replace `<python-executable>` with the selected Python interpreter:

```bash
mkdir -p target/industrial-sm-qualification
gzip -dc docs/process/optimization/benchmarks/industrial-sm-baseline-e8895a9f.json.gz \
  > target/industrial-sm-qualification/before.json
gzip -dc docs/process/optimization/benchmarks/industrial-sm-candidate-3847d75f.json.gz \
  > target/industrial-sm-qualification/after.json
<python-executable> devtools/industrial_sm_benchmark.py validate \
  --input target/industrial-sm-qualification/before.json
<python-executable> devtools/industrial_sm_benchmark.py validate \
  --input target/industrial-sm-qualification/after.json
```

The execution command for each source was the maintained `run` command shown above with
`--forks 5 --generated-date 2026-09-07`, the exact source commit from the table, and separate raw
and aggregate output paths. The runner selected `IndustrialPlantOptimizationBaselineTest`,
enabled the `slow` group, cleared `excludedTestGroups`, and disabled JaCoCo. No benchmark or
acceptance thresholds were changed for this comparison.

## Measured gaps and handoffs

The record marks these metrics unavailable rather than zero:

- ProcessSystem has no public attributable area-run counter;
- no public per-candidate flash/property-work counter is available;
- this solve-only baseline does not exercise optimizer-cache hits, misses, or invalidation;
- portable Java 8 provides no configured per-run allocated-byte counter or peak-heap sampler; and
- ProcessSystem has no whole-system energy-balance residual API comparable to its mass-balance API.

Execution counts, dirty/dependency scheduling, cache attribution, allocation, and end-to-end
performance remain coordinated with [#2939](https://github.com/equinor/neqsim/issues/2939). The
ordered M transition from piping through compressor to separator or total power remains incomplete;
it depends on the next #3154 stable plant constraint/resource identity increment. No missing metric
or transition is represented as a passed gate.
