# Invocation-local classical column terms

This batch preserves the modified-NRTL model and removes repeated evaluation of
one column's pure energy, first T derivative and second T derivative. Within a
single `ComponentGENRTLmodifiedHV.getGamma` call, the column component, temperature
and its configured alpha parameters are fixed. The pure terms are evaluated on
the first classical row and reused by later classical rows. HV-only columns do
not evaluate an unused pure alpha model. Row-side pure terms, interaction values,
expressions and summation order are unchanged.

No field, retained cache, workspace allocation, database value, parameter family,
alpha model, initialization level or flash tolerance is introduced. Reuse ends
at the current invocation; changed-temperature and fixed-temperature alpha-input
changes therefore re-evaluate the configured component. This does not change the
separate clone isolation contract or replace independently configured workers.

## Frozen baseline and numerical contract

Baseline: `a160d27fb5db6ecb8f5035e39597aa4c9ea4c339`. All 9,422 baseline file
blobs were checked against the connected GitHub tree before editing. The sole
production delta is `ComponentGENRTLmodifiedHV.java`; the generated engineering
coverage digest follows that source change.

The existing HV/adjacent selection passes 143 tests on the unmodified baseline
and 145 on the candidate, with zero failures/errors/skips. Two added lifecycle
cases change pure alpha `m` synthetically at fixed temperature, compare fresh
and reused evaluators and return to the original inputs. They also pass with
the unmodified evaluator. These perturbations are regression inputs, not fits.

The selection includes asserted `ThermodynamicModelTest` fugacity, Dn, Dn2, DP,
DT and refined numerical checks, independent finite differences, Gibbs-Duhem,
Hessian symmetry, classical reduction, pure/trace/amount/order cases, repeated
and changed-state gas/oil/water flashes, methanol/MEG and clone/serialization
checks. Native identities use 1e-8 and step-converged numerical checks use 1e-6
where the established protocol applies; no checker or tolerance was changed.

`activity-fingerprint.csv` contains all 1,512 hexadecimal outputs from the
existing `HuronVidalWorkspaceLifecycleTest.main` protocol. Baseline and candidate
reports are byte-identical (SHA256
`ee6fa7aa8b13e09d006cfd624213b87c77a61579eff552ac74e8a424a1c55088`).
Clean default-POM and clean Java8-POM builds each pass all 145 tests on Java17.
A mixed-output POM switch initially produced EJML linkage errors; separate clean
output trees resolved them without source or dependency changes. No local Java8
runtime execution is claimed. The 488-case pure-water reference/root/native audit
also reproduces its previous complete report byte-for-byte, SHA256
`429090a37e4437ee46547ea750b5a1e017b7706e3bfd4b7b3b4e1dd3603e633c`.
Both nitrogen/water prediction reports match their published fixtures exactly.

It covers SRK/PR, sizes 4/1/2/4, initialization 3/1/2/3, seven T/composition
variants, gamma, log gamma, both T derivatives and composition derivatives.
Numerical identity does not establish experimental accuracy.

## Profile and measurement protocol

`profile.json` preserves the exploratory 20-component SRK init3 JFR sample
counts, exact source baseline, command options and recording hash. The HV
evaluator appears in 613 of 710 execution-sample stacks, including 590 leaf
samples. This localizes a hot path. Loop-backedge line attribution does not
measure the isolated cost of one statement; repeated column-side pure work
was identified from the source. The complete algorithm still includes cubic
component loops. No asymptotic complexity reduction is claimed.

The reusable orchestrator is `devtools/benchmark_huron_vidal_columns.py`. It
invokes the unchanged `HuronVidalClonePerformanceBenchmark` in separate JVMs:

- SRK and PR, 4 and 20 components, phase initialization and complete TP flashes,
  initialization 1 and 3: 16 scenarios.
- Baseline HV, candidate HV, identical-baseline-binary HV control and classical
  rule2, with three independent forks and alternating cell/arm order.
- One EOS/scenario/rule per JVM; fixed 512 MiB heap, CPU6 affinity and `-Xbatch`
  to avoid background compilation overlapping measurement. Affinity does not
  establish exclusive host ownership.
- Thirty warmup batches and ten measured batches. Phase batches contain 2,000
  operations for 4 components and 200 for 20; flash batches contain 20 and 4.
  Construction and the first flash are outside measured intervals. Each batch
  includes the existing harness's operation wrapper and result checksum.
- Both compared evaluator classes are compiled by the same installed compiler
  module with `--release 8`, identical dependency classpath and compiler options.
  The rest of the compiled application and harness are shared. Environment
  metadata records class hashes and class-file versions; the runner rejects
  differing versions. Runtime here is Java17; local Java8 runtime performance
  is not claimed.

An earlier exploratory run mixed Maven's Java17 class with a `--release 8`
candidate. Its timings were discarded, as was a short overlapping restart.
Neither contributes to the matched reports or any performance conclusion.

The four-component raw molar amounts are methane .65, nC10 .20, water .12 and
MEG .03. The large fixture adds .005 each of ethane, propane, n-butane,
i-butane, n-pentane, i-pentane, n-hexane, n-heptane, n-octane, n-nonane,
nC11, nC12, nC13, nC14, nC15 and nitrogen; the total 1.08 is normalized by the
existing fluid setup. Phase initialization uses a forced gas at 310 K/20 bara.
Complete flashes alternate 298.15/298.25 K at 60 bara, with multiphase checking.
No lower initialization level replaces a requested derivative calculation.

Every timed result retains phase count, T/P, checksum, allocated bytes and
untimed residual checks. Material balance must be at most 1e-10, phase and
composition normalization 1e-12, log-fugacity mismatch 1e-8. `states.csv` stores
deduplicated complete final phase/component results. The runner requires exact
CSV state equality between baseline/candidate/control and repeated forks within
each EOS/size/scenario/init/rule. It does not require different mixing rules to
produce the same thermodynamics or do the same solver work.

To reproduce, build the frozen baseline and candidate, preserve their evaluator
class directories, generate a Maven dependency classpath file, then invoke the
runner with `--project`, `--baseline`, `--candidate`, `--dependencies`, a new
`--output` directory and `--base-sha`. CPU affinity is optional via `--cpu`;
`--help` lists the bounded matrix controls. Both evaluator binaries must be
compiled consistently. The orchestrator performs no downloads or builds. Interrupted runs can resume
with `--resume`; only complete, input-matched JVM receipts are reused. Incomplete
per-JVM outputs are regenerated. The harness-hash guard rejected a changed build
before resumption; the original harness was then restored by the clean default
build. No timing receipt from a different harness was combined.

## Limits

Timing is never a CI assertion. Identical-binary control spread, warmup,
allocation, phase topology and equilibrium results must be examined before
attributing a difference to code overhead. Rule4/rule2 ratios compare different
models and may include different flash work. Neither this synthetic matrix nor
allocation reduction qualifies physical parameter accuracy, hydrate
dissociation, ice/brine extrapolation or universal production throughput.
The campaign's unresolved primary pure-input and inhibitor-table provenance
gates remain open; no coefficient or default was retuned by this batch.

## Observed matched matrix

All 192 JVMs and 1,920 rounds completed (1,067,520 measured operations).
All final states are exactly equal across HV baseline/candidate/control forks.
Phase cases are one-phase forced gas; complete flashes retain three phases.
Maximum balance residual is 5.141e-14; maximum log-fugacity mismatch is 1.521e-12.
Normalization is checked at 1e-12 by the unchanged harness.

Ratios below use per-JVM round medians, then three fork ratios. Below one means
less observed elapsed time. Ranges and identical-binary controls are retained;
these are observations, not established portable speedups.

| EOS | n | Work/init | Candidate/baseline median [range] | Identical control/baseline range | Candidate/control median | HV/classic baseline median |
|---|---:|---|---:|---:|---:|---:|
| SRK | 4 | phase/1 | 0.935 [0.910, 1.037] | [0.801, 1.250] | 1.013 | 5.18 |
| SRK | 4 | phase/3 | 0.791 [0.746, 0.931] | [0.792, 0.948] | 0.982 | 7.74 |
| SRK | 4 | flash/1 | 0.760 [0.650, 0.926] | [0.731, 1.077] | 0.889 | 3.23 |
| SRK | 4 | flash/3 | 0.912 [0.828, 3.919] | [0.980, 2.034] | 0.931 | 3.37 |
| SRK | 20 | phase/1 | 0.788 [0.610, 0.794] | [0.993, 1.296] | 0.614 | 103.72 |
| SRK | 20 | phase/3 | 0.928 [0.770, 0.939] | [1.000, 1.079] | 0.914 | 93.85 |
| SRK | 20 | flash/1 | 0.895 [0.885, 0.986] | [0.995, 1.115] | 0.894 | 32.47 |
| SRK | 20 | flash/3 | 0.836 [0.803, 0.970] | [0.880, 1.191] | 0.841 | 37.18 |
| PR | 4 | phase/1 | 0.929 [0.847, 1.010] | [0.817, 0.989] | 1.022 | 4.95 |
| PR | 4 | phase/3 | 0.992 [0.733, 0.993] | [0.782, 1.006] | 0.986 | 7.97 |
| PR | 4 | flash/1 | 0.935 [0.850, 1.010] | [0.693, 1.043] | 1.041 | 3.50 |
| PR | 4 | flash/3 | 0.952 [0.710, 1.234] | [0.766, 1.329] | 0.927 | 2.71 |
| PR | 20 | phase/1 | 0.846 [0.805, 1.062] | [0.917, 1.053] | 0.877 | 95.98 |
| PR | 20 | phase/3 | 0.823 [0.803, 0.927] | [0.893, 1.035] | 0.896 | 98.38 |
| PR | 20 | flash/1 | 0.880 [0.742, 1.041] | [0.852, 1.000] | 0.929 | 35.47 |
| PR | 20 | flash/3 | 0.808 [0.724, 0.848] | [0.677, 0.915] | 0.947 | 37.97 |

Large-mixture phase-init3 candidate/baseline ratios are below one in all three
forks for both EOS. The complete-flash matrix has substantial spread, including
an adverse 3.919 candidate/baseline SRK/4/init3 sample with a 2.034 identical-binary
control/baseline ratio. It is retained, not discarded as an inconvenient outlier.
A longer-warmup follow-up is reported separately. Reliable general throughput
acceptance remains open; no absolute optimality is claimed.

The remaining HV/classical cost is substantial. These ratios include different
models and solver work, so they cannot be assigned solely to evaluator overhead.
The row-side terms and cross-component repeated work remain; sharing prepared
phase work would require a separate ownership/subclass-dispatch validation contract.

Optional static analysis: Checkstyle completes with 12 existing declaration-distance
warnings in both evaluator versions; PMD reports the same 6,201 repository findings
(seven in this evaluator) with no added or removed findings. Its check goal fails
on that existing inventory. SpotBugs prefix resolution is unavailable in the
current POM. These are not claimed as clean static-analysis passes. Direct Spotless
apply/check, documentation search, generated coverage and both hook stages pass.

### Longer-warmup follow-up

The adverse SRK/4-component/full-flash/init3 cell was repeated with 300 warmup
batches and 30 measurement batches, retaining all four arms and three forks:
12 further JVMs, 360 rounds and 7,200 measured flashes. Candidate/baseline fork
ratios are 0.823, 1.041 and 0.952; identical-control/baseline ratios are 0.802,
1.203 and 1.000. Candidate/control ratios are 1.026, 0.866 and 0.952.
The 3.919 excursion did not reproduce under this longer warmup. The remaining
control spread still prevents a reliable general throughput claim. No measured
round or adverse result was removed, no tolerance or initialization was reduced,
and no thermodynamic result changed. Raw follow-up reports are in `long-warmup/`.

Combined characterization: 204 JVMs, 2,280 measurement rounds and 1,074,720
measured operations. This completes the bounded runtime experiment and its
controls; independent domain review and portable throughput acceptance remain
pending. It does not complete campaign #4576 or its physical-accuracy criteria.
