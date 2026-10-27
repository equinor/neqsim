# Modified-NRTL HV allocation qualification

This is the allocation-only successor to the [clone ownership repair](../hv-clone-performance/README.md).
The numerical baseline is merged master `3ec4b8cff4d5c2dc4875cb28229be88660c67504`.
The next master `7bb4400c2a55907d720a7c88cc8ad73f5d6c9734` changes only two vapor-pressure
documentation pages; the benchmarked Java/data sources are identical.
No parameter, alpha convention, equation, summation order, stopping tolerance or shared cache changes.
The retained composition-derivative vector is cleared at every call and resized when needed.
The two derivative matrices remain call-local, stored flat rather than as separate row objects.
This avoids a retained cubic-size per-phase cache and preserves the merged clone ownership graph.

## Results and interpretation

**Allocation improvement established; wall-time speedup/non-regression unqualified.**
All 352 baseline/candidate JVMs and 4 identical-binary control JVMs complete successfully:
3,648 retained rounds and 8,880 final-state rows. Beta, x and logphi are bit-identical in every
four-JVM cell. Independent CSV/math.fsum reconstruction of 356 snapshots gives max normalization
1.555e-15, balance5.241e-14 and log-fugacity1.716e-12, below the unchanged gates.
All 1,512 activity/derivative fingerprint values also match byte-for-byte.

Both EOS give these warmed forced-gas system.init allocation centers in the longer phase matrix:

| Components | Init level | Baseline bytes/call | Candidate bytes/call | Removed bytes/call |
|---:|---:|---:|---:|---:|
| 4 | 1 | 195.24 | 3.24 | 192 |
| 4 | 3 | 2371.04 | 1539.04 | 832 |
| 20 | 1 | 3563.04 | 43.04 | 3520 |
| 20 | 3 | 155243.04 | 135723.04 | 19520 |

Rule 2 phase allocations and all clone-only allocations are unchanged. On this JVM (compressed
references/class pointers, 8-byte object alignment), the expected removed allocation is
`N*(16+8*N)` at init(1) and `48*N*N+16*N` at init(3) for these aligned N=4/20 sizes. This independently
accounts for the measured vector and matrix-row allocations. Fixed harness/counter costs are
included in absolute centers and cancel in the differences. No larger matrix is retained on a fluid.

Longer-warmup, unpinned four-component init3 clone-plus-flash candidate/baseline centers are
0.687 SRK-HV and 0.823 PR-HV; classical controls 1.027/1.000. A same-CPU repeat gives 1.474 SRK-HV
(an adverse observation retained in full), 0.562 PR-HV, with unchanged classical controls 0.749/1.041.
A subsequent four-JVM SRK-HV control uses the **same candidate binaries for both labels**, yet
reports 1.591 (per-JVM medians 818.8/1109.4/2183.3/1251.4 microseconds). Thus this environment's
persistent timing variation can exceed the apparent code effect. These results establish neither
a universal speedup nor a reproducible source-attributable slowdown. Do not mark throughput
accepted; repeat on controlled hardware with more independent forks before a timing claim.

Two separate PrintCompilation-instrumented diagnostic JVMs (excluded from the matrices and all
timing claims) compile both getGamma implementations at HotSpot tier 4. The bytecode lengths are
4510/4573 bytes; neither getGamma compilation is skipped. The candidate diagnostic separately records one H2
concurrent-class-loading skip; its line is retained in compilation-summary.json. This rules out a demonstrated failure to
optimize the candidate method in those diagnostics; it does not prove equal optimized CPU cost.
The earlier ownership-batch JFR profile places getGamma in 137/204 inclusive CPU samples, motivating
this bounded allocation work. Further scalar/precomputation/copy optimization needs its own
measured ownership and derivative contract.

## Protocol

Use the same Java 8-compatible `HuronVidalClonePerformanceBenchmark` on both separately built main
class directories, with identical dependencies. The baseline has the merged ownership repair, not
the earlier shared-state implementation. The harness/runner from that repair are unchanged.

Full screen: 64 cells, 256 independent JVMs, 16 warmup rounds, 8 measured rounds, 200 clone/init
operations or 8 complete TP flashes per round. Includes SRK/PR, 4/20 components, init1/init3,
rule2/rule4 and clone/phase/flash/clone_flash. ABBA/BAAB alternating order, two JVMs per variant.
Build/test checks overlapped parts of this screen: its wall times are diagnostic, not evidence of
an established timing speedup. Allocation/result observations are retained without discarding rounds.

The subsequent phase matrix (128 warmups,16 measured rounds;64 JVMs) and small-mixture complete
clone-plus-flash matrix (512 warmups,16 measured rounds;16 JVMs) run
without concurrent local build/test jobs. These separate the allocation result from short-run
compilation, scheduling and unchanged-control variation. Keep per-JVM medians and ranges; never
use a single best round or compare different EOS/scenarios in one type-profile-contaminated JVM.

Four-component feed is methane/decane/water/MEG = .65/.20/.12/.03 mol. The 20-component fixture
adds 16 species at .005 mol each and normalizes the total1.08 mol. Phase initialization is a
forced gas at310 K/20 bara. Flash workloads use298.15/298.25 K and60 bara with multiphase checking.
All raw phase types, beta, x and logphi are included. The harness checks every measured round:
balance1e-10, normalization1e-12, interphase log-fugacity1e-8. Classical and HV can give different
compositions/solver work; their cost ratio is not a physical-accuracy comparison. There is no
public complete iteration counter; the frozen number of calls and observed states/residuals
are reported, rather than assuming equal equilibrium iterations across rules.
The pinned repeat and identical-binary control use taskset -c 0, selecting one allowed logical CPU;
this is affinity, not an exclusive CPU reservation. Every successful sample is retained.

## Reproduction

After compiling the current source and the frozen baseline into separate output directories,
run the same harness class directory with both (see the linked clone protocol for compilation).
Evaluate the work-with-neqsim Maven bootstrap before any Maven/Spotless command.

```bash
python3 devtools/benchmark_huron_vidal_clone.py --baseline-classes target/hv-merged-baseline-classes --candidate-classes target/classes --test-classes target/hv-benchmark-harness --baseline-label 3ec4b8cff4d5c2dc4875cb28229be88660c67504 --output target/hv-allocation-performance/full
python3 devtools/summarize_huron_vidal_clone.py target/hv-allocation-performance/full
```


Longer-warmup phase and complete-flash repetitions (the supplied summarizer accepts the deterministic gzip
fixtures directly as well as fresh CSV output):

```bash
python3 devtools/benchmark_huron_vidal_clone.py --baseline-classes target/hv-merged-baseline-classes --test-classes target/hv-benchmark-harness --baseline-label 3ec4b8cf --output target/hv-allocation-performance/phase-quiet --operations phase --warmups 128 --rounds 16
python3 devtools/summarize_huron_vidal_clone.py target/hv-allocation-performance/phase-quiet --cells 16 --rounds 16
python3 devtools/benchmark_huron_vidal_clone.py --baseline-classes target/hv-merged-baseline-classes --test-classes target/hv-benchmark-harness --baseline-label 3ec4b8cf --output target/hv-allocation-performance/clone-flash-quiet --operations clone_flash --sizes 4 --init-levels 3 --warmups 512 --rounds 16
python3 devtools/summarize_huron_vidal_clone.py target/hv-allocation-performance/clone-flash-quiet --cells 4 --rounds 16
```

Repeat the complete-flash command under `taskset -c 0` with a fresh output directory for the affinity
control. The separate identity-pinned control selects only SRK/rule4 and passes target/classes as
both baseline and candidate directories, with label candidate-identical-binary-control. Its four
forks measure the same binaries; it is not another independent implementation comparison.
The two optional compiler diagnostics add `-XX:+PrintCompilation` to the same manual Java entry
point on CPU0, using512 warmups/16 rounds/eight operations. Their timings are excluded.

The summarizer defaults still require all 64 cells/four JVMs/eight rounds. Explicit `--cells` and
`--rounds` select a different frozen matrix, keeping strict round/sample uniqueness and final-state
sample identity. It also requires every measured cell/JVM, the positive phase count reported
consistently in every round, and every component exactly once in every final phase. It rejects
empty tables and whole missing cells, phases or components before writing either summary.
Fifteen Python tests cover these rejection cases, a valid multicomponent/multiphase roster,
all five allocation matrices and the original published default fixture. Previously the group-wise
comparison could silently accept deletion of an entire matching group; the original published
raw data pass the complete-roster checks unchanged. The numerical gates and timing interpretation
are unchanged. CSV gzip uses deterministic mtime 0; raw logs are
scratch diagnostics, with successful process exits recorded in each manifest.

`HuronVidalWorkspaceLifecycleTest` asserts fresh-versus-retained values across count changes,
init3/1/2 transitions, temperatures, pure and trace states and clone/serialization interleaving.
Its executed `main(outputCSV)` writes 224 evaluations/1,512 hexadecimal gamma/lnGamma/T/TT/N values.
Baseline and candidate CSVs are byte-identical; the published copy is a single common fingerprint.
Native ThermodynamicModelTest booleans and checkNumerically(), independent temperature/composition
finite differences, Gibbs-Duhem, Hessians, component order, amount limits, measured-feed states,
MEG/methanol gas/oil/water and adjacent WS/NRTL/clone tests remain asserted in the 190-test Java suite.
The unchanged numerical derivative gate is 1e-6; balance, normalization and equilibrium gates are
listed above. Java8 API compilation and strict private-member Javadoc run on OpenJDK 17.0.20+8; hosted Java8
runtime/full-repository checks remain distinct publication follow-up.

## Physical and ownership limits

Allocation reduction does not calibrate water/gas reverse solubility, inhibitor partition, TEG,
heavy fractions, hydrate dissociation, brine, ice or extrapolation. No literature numbers are
introduced or activated; source/alpha/property blockers remain in[#4573](https://github.com/equinor/neqsim/issues/4573).
The standard modified-NRTL worker contract in the mixing-rule guide is unchanged. Same-live-fluid
concurrent mutation and other GE implementations retain their separate contracts. The earlier
PR-HV copy-cost observation remains in the linked ownership matrix; new numbers must not be
silently compared across different baselines.
