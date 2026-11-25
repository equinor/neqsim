# Rejected HV scratch reuse and reliable benchmark receipts

Campaign #4576, coordinated with #2939. **Neither storage proposal is adopted.**
The delivery preserves the production evaluator, equations, parameters, alpha
models, selected pairs, CLASSICAL kij representation and default behavior.
It adds a changed-initialization/parameter regression, atomic controller receipt
writes and independently re-auditable negative performance evidence.

## Disposition and cause

The baseline is master `5a09e550141d99da1dd2854070c6688d754fb678`. Delivery is
reconciled with `3c277b1faefe58c8160d959063e6f511e32dd7fc`, whose nine changed
paths belong to the unrelated pipeline #5245. The original timing graph remains
frozen at the earlier baseline; it is not relabeled a run of the later master.

Tuple storage reused two double[N*N] matrices and two double[N] reductions in
one standard complete activity invocation. Every matrix entry was recalculated
for each component, reductions cleared, and arithmetic/summation order preserved.
No computed value or workspace survived the invocation. Standalone, pure and
custom-subclass paths retained fresh storage. The second proposal replaced the
outer array with a private final-field descriptor; its four data arrays were
unchanged. The two `.patch.gz` files preserve rejected experimental source byte-for-byte
(decompress for comparison), not instructions
to apply them or opt-in production profiles.

Both proposals preserved numerical endpoints but reproduced small-PR Java17
latency increases against baseline and identical-baseline controls. The original
small PR2016 init3 ratios were 1.1085/1.1067 versus baseline and 1.1365/1.0530
versus control. A separate repeat gave 1.0236/1.1143 and 1.0369/1.1556. The named
field descriptor gave 1.0037/1.1113 and 1.1655/1.1111. Control drift remains visible;
no observation is filtered or pooled to erase an adverse result. Exact compiled
causation remains unresolved. Allocation reduction alone fails the latency gate.

## Complete retained matrices

`raw.zip` contains ordered manifests, raw rounds, phase/component endpoints and
logs for all five separate experiments. `validation.json` contains their exact
summaries and the archive SHA256. The existing auditor reconstructs every raw
hash, roster, work count, equilibrium gate and baseline/candidate/control
comparison; its new retained-evidence test runs in the existing Python workflow.
From the repository root, replay it with
`python -m unittest discover -s devtools -p test_benchmark_huron_vidal_rows.py -v`
and repeat with `python -O`. This audits frozen data and executes the receipt
failure controls; it does not launch JVM timing measurements.

| Dataset | JVMs | Scope |
| --- | ---: | --- |
| tuple-full | 216 | Three retained profiles, 5/15 components, init1/init3/complete TPflash+init3, rules2/4, two reversed forks and three binary arms |
| tuple-repeat | 24 | Separate small PR2016/PR-Twu init3 repeat on Java17 |
| tuple-java21 | 24 | Same Java17-compiled tuple bytecode on actual Java21; separate runtime experiment |
| tuple-boundary | 24 | Separate large SRK-Twu/PR-Twu full-flash checks across the interrupted boundaries |
| descriptor-pilot | 24 | Small PR2016/PR-Twu init3 descriptor trial on Java17 |

The total is 312 accepted JVMs, 3,120 rounds and 4,368,000 measured operations.
Every experiment has zero maximum same-case beta/z/x/logphi difference. Across
the full matrix the maximum balance is 4.16334e-15, normalization 3.33067e-16 and
log-fugacity residual 8.74235e-12. Gates remain 1e-10/1e-12/1e-8 and 1e-12 for
same-case comparisons. Rule2 and rule4 topology/results are reported separately;
they are not presumed equivalent thermodynamic work. Only the proven selected
CO2/water pair uses HV in the selected-pair profiles; other pairs retain CLASSICAL.

Allocation for tuple type3 falls from 5,600.024 to 1,312.024 B/operation for five
components and 176,184.024 to 12,312.024 for fifteen. The native Java17 array-layout
probe independently predicts reductions 4,288/163,872 B, matching observations.
These are main-thread allocation measurements, not total-process allocation.

The unchanged baseline also retains the required CLASSICAL comparison. Matched
init3 rule4/rule2 elapsed-time ratios, shown as both forks, are:

| Profile | 5 components | 15 components |
| --- | --- | --- |
| PR2016 | 11.755 / 11.861 | 55.188 / 50.095 |
| SRK2017 Twu | 22.710 / 26.377 | 108.636 / 107.944 |
| PR2017 Twu | 23.727 / 23.038 | 88.838 / 87.149 |

Small cases end gas/aqueous and large cases gas/oil/aqueous under both rules,
but phase compositions and equilibrium still differ. These are matched model
costs, not equivalent-work software overhead. All init1/full-flash ratios and
topology are recorded separately in `validation.json`; absolute timing is not
portable and the present increment does not improve those costs.

Type1 allocation remains unchanged. Full-flash work and allocation are retained;
no universal speedup, optimality or physical-accuracy acceptance is claimed.

The unchanged harness uses 20,000 initialization or 1,000 flash warmups, ten
measured rounds with 2,000 initialization or 50 flash operations, 512 MiB heap,
-Xbatch and two compiler threads. Each EOS/scenario/arm has its own JVM to avoid
switching type profiles. Different epochs, runtimes, the earlier common-3.29.0
prototype pilot and instrumented measurements are never pooled.

Eight additional diagnostic JVMs preserve the same endpoints. Full-stack JFR
and compilation logs show the evaluator dominates sampled work, both arms reach
C2, and the init3 cases record zero operation deoptimizations. The full-flash
pair each records 16. Sample counts include warmup and are not CPU-time shares,
call counts or proof of the latency mechanism. `diagnostic-summary.json` retains
method/line counts, `jit-methods.json` retains the bounded C2 records and
`diagnostic-execution.json` records exact commands and raw hashes. Full selected-event
exports and JIT logs belong to the
supporting qualification archive. Raw JFR environment metadata is not published.
The first whole-JSON summary process was killed (exit137); bounded streaming
analysis completed on unchanged diagnostic files without rerunning JVMs.

## Receipt repair and recovery limits

The old controller directly rewrote JSON. An interrupted or full-device write
could truncate the last accepted checkpoint. All six JSON write sites now use a
unique same-directory exclusive temporary, complete write/flush/fsync and atomic
replacement. Existing file modes and normal umask-based creation are preserved.
A failed partial write, fsync, replacement or serialization preserves old bytes.
Cleanup failure annotates the original exception and reports the orphan path.
This does not guarantee power-loss durability, add automatic resume/retry or
establish that a native JVM has stopped. No timing workload or auditor gate changes.

The original 216 matrix had controller losses and an ENOSPC receipt failure.
Saved ordered prefixes at 108, 113 and 145 were hash/numerically rechecked before
continuing. Unknown/empty attempts remain under `interrupted-attempts`; no failed
completed measurement was removed or retried. Earlier epochs observed the full
660-second orphan guard. A later local adapter used an inherited POSIX lease,
independently proved on native Java17/21, to exclude overlapping JVMs. This adapter
and lease mechanism are supporting validation only, not the shipped controller.
The original shared foreground checkout was preserved and all validation moved
to an independent Git/source tree. Only this campaign's reproducible caches were
reclaimed. Full chronology and identities are retained in the qualification archive.

## Numerical and physical boundaries

The regression asserts exact standalone gamma and exposed derivatives through
SRK/PR water/CO2/methane/methanol/MEG, changed directed offsets, changed/returned
temperature and initialization3/1/3/2/3. Synthetic test coefficients are not
calibrated literature parameters. Existing ThermodynamicModelTest acceptance
asserts fugacity, dn, dn2, DP, DT and numerical checks, alongside independently
resolved finite differences, amount/order/pure/trace, clone, serialization and
interleaving controls. Tolerances and the test harness remain unchanged.

`native-validation.json` is explicitly the rejected tuple's earlier 150/150
Java17 and actual Java8 qualification, with complete component coverage and
byte-identical six measured-assessment CSVs. It is not final-delivery acceptance.
The descriptor has only 28 focused Java17 passes; no Java8, full-suite or static
qualification is claimed for that rejected prototype. Final-delivery commands,
results and coverage are recorded separately in `validation.json`.

No new parameter, fit, EOS/alpha or formulation is introduced. Existing opt-in
profiles and the seven recovered PR water/light-gas tuples retain their status.
The retained ten measured vapor-MEG points have AARD 88.47% SRK /171.18% PR;
the distinct historical six-state assessment is not a universal tolerance.
The 15 pure-MEG controls cover 307.7–384.5 K and show pressure overprediction,
but all ten partition measurements are at 288.16–298.29 K: there is zero overlap.
Those controls cannot justify a low-temperature compensating fit or transferred
correction. Compatible low-temperature pure controls, exact source alpha/critical
inputs and independent bidirectional partition holdouts are the next physical
dependency. Epelle2020/Hemmingsen2011 original tables remain unrecovered; ordinary
NRTL, MHV, CPA or PRSV tuples are not replacements. Folas MC-alpha coefficients
are not ordinary Soave-SRK coefficients. Hydrate dissociation needs separate
experiments; inhibitor gas/oil/water partition, ice/brine and extrapolation remain
open. Independently configured worker fluids remain the safe concurrent route.
