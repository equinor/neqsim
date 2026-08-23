---
title: Industrial S/M Optimization Benchmark Evidence
description: Executed small-guide and medium multi-train recycle evidence for the industrial ProcessSystem optimization baseline.
---

# Industrial S/M Optimization Benchmark Evidence

This page records the first executed increment of the
[industrial optimization baseline](industrial-process-optimization-baseline). It is measurement
evidence for roadmap [#3154](https://github.com/equinor/neqsim/issues/3154), not a performance
improvement claim and not qualification of the flowsheets for design or operations.

The measured source is unmodified NeqSim `master` commit
`5a851750f8d12b3a598a87da0acadb3faef8b4e6`. The harness is additive test code. It does not change
process calculations, thermodynamics, scheduling, recycle convergence, caching, or optimizer search
behavior.

## Engineering question and stop boundary

Can the frozen small guide case and a deterministic 25–50 unit multi-train case emit attributable,
repeatable records for convergence, equipment work, balances, utilization, invalid proposals,
constraint changes, and reversible line-up actions using current public APIs?

This increment stops after executing and versioning cases S and M. It does not add plant-wide
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

```bash
eval "$(python3 /path/to/work-with-neqsim/scripts/prepare_maven.py)"
./mvnw -Dgroups=slow "-DexcludedTestGroups=" -Djacoco.skip=true \
  -Dtest=neqsim.process.util.optimizer.IndustrialPlantOptimizationBaselineTest \
  -Dneqsim.optimization.baseline.commit="$(git rev-parse HEAD)" \
  -Dneqsim.optimization.baseline.output=target/industrial-optimization-sm-baseline.json \
  test
```

Confirm that Surefire reports one executed test with zero failures. Repeat the command in at least
five independent Maven/JVM forks before comparing wall time. The JSON output records deterministic
calculation identities, case and topology counts, every mode, per-equipment execution work,
mass-balance residuals, constraint evidence, heap before/after proxy, observation size, failure or
restoration outcome, and unsupported metrics with reasons.

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
| Startup-inclusive S/M harness wall time | 5 forks | 18,954.293 ms | 245.332 ms | 18,507.921–19,222.640 ms |
| S cold process solve | 5 forks | 54.215 ms | 3.757 ms | 50.208–69.074 ms |
| S unchanged process solve | 5 forks | 0.175 ms | 0.027 ms | 0.142–0.256 ms |
| M cold process solve | 5 forks | 155.787 ms | 6.491 ms | 131.767–162.277 ms |
| M unchanged process solve | 25 runs | 33.460 ms | 4.618 ms | 22.612–50.351 ms |

The five startup-inclusive fork samples are `18507.921`, `19222.640`, `18925.934`, `18954.293`,
and `19199.625` ms. The full raw arrays and per-mode measurements are in
[`industrial-sm-baseline-5a851750.json`](benchmarks/industrial-sm-baseline-5a851750.json).

Every unchanged M run reproduced the cold product mass rate exactly within double precision. Across
the five forks, the restored-line-up product differed from cold by a median `0.0258211 kg/hr`; the
largest observed difference was `0.0258364 kg/hr`, below the `0.1 kg/hr` acceptance criterion. The
largest unit mass-balance residual across 50 M mode records was `0.0222922 kg/hr`. Serialized M
utilization observations ranged from 24,428 to 24,734 bytes.

Case S rejected a non-finite external proposal before it mutated the feed. Case M observed the
default export-pipe velocity constraint, then a controlled installed feed-pipe velocity constraint
after the limit change, and completed a train-unavailable action plus full replay restoration.

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
