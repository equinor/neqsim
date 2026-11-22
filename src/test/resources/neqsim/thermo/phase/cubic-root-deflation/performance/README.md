# Cubic fallback precision: performance controls

These measurements qualify a numerical repair, not physical parameter accuracy
or universal speed. Baseline is PR #4577 head
`9863adc6d40a8029d8286e3885e56b034f22bcc7`; candidate differs only in
`PhaseEos` root filtering/deflation. Date: 2026-10-25. Runtime: OpenJDK 17.0.20+8,
64-bit Server VM, Linux amd64. Both PhaseEos variants were compiled by the same
JDK with `--release 8`. Classpath selects exactly one variant before otherwise
identical target classes and dependencies. No concurrent benchmark process was
run. Results are subject to host/JIT variability; no timing assertion is added.

## Matched complete workload

`root-performance.csv` contains 480 samples from 48 independent JVMs: two process
pairs per EOS (SRK/PR), mixing rule (2/4), and scenario (phase_init1,
phase_init3, flash_init3). Each process warms 40 batches and measures 10; version
and rule order alternate between process pairs. JVM flags: `-Xms128m -Xmx1g`.
One EOS/scenario/rule per process avoids switching EOS type profiles mid-process.
The existing `HuronVidalPerformanceBenchmark` is the executable harness. Each
phase batch includes setup and 2,000 initializations; each flash batch includes
setup, the initial flash, then 60 changed/repeated-temperature flashes and init3.
The source Javadoc gives complete feeds, T/P and checksums. All twelve cells
have identical baseline/candidate checksums. Summary ratios use pooled medians;
independent process ratios matter more than samples within one JVM.

Across these cells candidate/baseline pooled ratios range 0.838–1.035, but several
process pairs are unstable. This does not establish a general speedup. Candidate
HV/classical pooled ratios are SRK 2.01/2.86/2.46 and PR 1.94/3.17/2.87 for
init1/init3/flash3 respectively. These ratios include different thermodynamic
models, solver work and equilibria; they are not isolated mixing-rule overhead.
The observed HV cost remains substantial.

`root-performance-longwarm.csv` adds 18 independent JVMs: three process pairs
per PR init1 rule2, PR init1 rule4 and PR flash3 rule4; 150 warmup and 40 measured
batches, alternating version order. PR init1 rule2 remains unstable (ratios
0.989/1.468/0.966). PR init1 rule4 gives 0.935/0.991/0.954. PR flash3 rule4 gives
**1.062/1.021/1.128**. Thus full-flash non-regression is unresolved even with
longer warmup; do not promote the isolated-root improvement as full-flash speed.

To reproduce a cell, run the harness main class with arguments
`label output.csv EOS scenario warmup measured rule`, selecting the baseline or
candidate class directory first on the same dependency classpath. Use the matrix
and alternating order above. Retain per-process labels, every raw sample and
checksums, and compare independent process medians. Compilation/loading must be
identical. Do not mix instrumented profile/count runs into timings.

## Equilibrium closure and work

`root-closure-{baseline,candidate}.csv` are bit-identical, including compositions,
for rule2/rule4 SRK/PR gas/oil/water with MEG at 60 bara and four histories:
fresh 298.15 K, repeated 298.15 K, changed 298.35 K, returned 298.15 K. Across
these states maximum balance error is 3.17e-14, normalization 2.23e-16,
log-fugacity mismatch 6.83e-13 and relative EOS-pressure residual 7.21e-14.
`RootBenchmarkClosure.java.txt` is the separately compiled reporter. The phase
init scenarios intentionally request a single gas root rather than equilibrium;
phase-equilibrium residuals do not apply to those timings.

`solver-work-counts.json` records entry counts for the PR-HV flash harness with
40 warmup and 10 measurement batches. Baseline and candidate are identical:
2,122,200 gamma calls, 530,750 volume calls, 3,050 analytical fallbacks,
2,478,700 dFdV calls, and 3,050 TP flashes. The helpers in `work-counter/` use ASM9
method-entry instrumentation on copies of five classes; the selected class-name
mapping is explicit. Copy the .java.txt sources to their package paths, compile
against ASM9 and the same target classpath, run Instrument on each named class,
then put instrumented classes and RootWorkCounts first on the classpath.
RootWorkCounts receives label, CSV path and count-JSON path. This is a work-count
experiment only; it does not establish why non-instrumented timing differs.

A separate JFR `profile` recording of candidate PR-HV flash3 (150/10 batches),
rendered with stack-depth 128, has 543 samples within flash+init3 excluding
component database setup. The topmost NeqSim frame is modified-HV getGamma in
400/543 samples (73.7%); analytical-root methods have zero inclusive samples.
See `root-jfr-summary.json`. Sampling is not exact call counting. Pair/activity
precomputation may merit future profiling after calculation-state isolation;
this repair adds no shared mutable cache.

## Isolated valid fallback

`root-micro.json` contains two independent JVMs per version/EOS, each with 20
warmup batches and 12 measured batches of 100,000 alternating GAS/LIQUID calls.
Methane at 150 K/10 bara passes pressure closure with both versions. Median
ns/call: SRK 149.24 to 138.24 (ratio 0.926); PR 148.20 to 133.62 (0.902).
`RootFallbackTiming.java.txt` is the exact helper. Both versions retain the same
three-root allocation; candidate replaces two cosine calls with primitive
quadratic deflation. These timings cover no claim about large mixtures,
allocation rates, saturation accuracy or complete-flash speed.

## Remaining acceptance boundary

Keep draft. The broader volume/stability/process compatibility failures in #4579
remain, as do physical EOS-specific fit validation, inhibitor/hydrate measurements
and clone-state isolation. This experiment neither retunes parameters nor relaxes
thermodynamic or solver tolerances. Full Java8 runtime and hosted CI validation
must be reported separately from local --release8 compilation.
