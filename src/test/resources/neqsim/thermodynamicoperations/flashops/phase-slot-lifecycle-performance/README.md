# TPflash phase-slot lifecycle performance evidence

This directory records the matched full-flash performance and numerical qualification requested by
[#4310](https://github.com/equinor/neqsim/issues/4310) for the final
[#4649](https://github.com/equinor/neqsim/pull/4649) phase-slot lifecycle tree.

## Frozen comparison

- Base: `e2cb5f792f2a3480fab451bb9a161e586b0e3fda`
- Final feature head: `c3b77077952a033acf12a01086117fcca50384e1`
- Runtime: OpenJDK 17.0.20, Java 8-compatible project compilation, Linux
- Protocol: five independent JVM pairs in alternating base/head order, five complete-sequence warmups, and ten
  measured sequences per case
- Evidence: [`raw.csv`](raw.csv), containing 2,400 complete flashes per tree and 4,800 flashes in total

`TPflashPhaseSlotLifecyclePerformanceBenchmark` measures the same four cases on each exact tree:

1. water-bearing PR changed-state reuse across low- and high-pressure endpoints;
2. methane/n-heptane PR cold dense splits, collapse states, warm phase-boundary states, and return continuity;
3. ordinary UMR-PRU hydrocarbon-scrubber pressure cycling through phase appearance and disappearance;
4. the same UMR-PRU pressure cycle with enhanced multiphase stability enabled.

Every measured endpoint is checked for deterministic topology, finite normalized phase fractions and compositions,
component balance no weaker than `1e-10`, and interphase log-fugacity residual no weaker than `1e-8`. Invalid endpoints
are counted explicitly and cannot be accepted as performance evidence.

## Result

All 4,800 flashes had zero invalid endpoints and zero topology mismatches. Across both trees, the maximum component
balance residual was `5.41e-13`, the maximum phase-fraction normalization residual was `1.11e-16`, and the maximum
composition normalization residual was `4.13e-14`. The largest final-head log-fugacity residual was `8.09e-9`, within
the frozen campaign gate.

| Complete lifecycle case | Paired median head/base wall time | Paired median head/base allocation |
| --- | ---: | ---: |
| Water-bearing PR changed-state reuse | 0.989 | 1.0000 |
| Methane/n-heptane PR phase-map reuse | 0.915 | 1.0000 |
| UMR-PRU scrubber, ordinary stability | 1.000 | 1.0001 |
| UMR-PRU scrubber, enhanced stability | 0.155 | 0.7604 |

The three ordinary paths show no attributable allocation regression and noisy wall-time ratios centered on
non-regression. The enhanced UMR-PRU path has a repeatable `0.116-0.212` paired head/base time ratio and a `0.7604`
median allocation ratio. This is evidence for the exact #4649 tree on the frozen fixtures, not a portable global
TPflash speedup claim.

## Reproduction

Copy the benchmark source into exact detached worktrees of the two commits, then run each tree in a fresh JVM. The
publication run used:

```text
./mvnw -q -f pomJava8.xml \
  -Dtest=TPflashPhaseSlotLifecyclePerformanceBenchmark \
  -DexcludedTestGroups= -Djacoco.skip=true \
  -Dtpflash.benchmark.warmup=5 \
  -Dtpflash.benchmark.sequences=10 \
  -Dtpflash.benchmark.requireValid=true \
  -Dtpflash.benchmark.commit=<exact-commit> \
  -Dtpflash.benchmark.output=<unique-output.csv> \
  surefire:test
```

The benchmark reports `System.nanoTime()` wall time and `com.sun.management.ThreadMXBean` main-thread allocated bytes.
It does not measure other-thread allocation, retained heap, peak memory, or cross-host throughput. Those limitations,
JIT variation, and the wide wall-time ranges are why only the paired frozen-fixture result is accepted here.

Documentation impact: none. This is test-only performance and numerical evidence; it does not change a public API,
model, default, unit, tolerance, configuration, or recommended user workflow.
