# Standard modified-NRTL HV clone ownership and measured cost

This is the independent ownership batch for issues #4575/#4576. Baseline:
`60e1754c6b7ed14ef9871e8e9d8cf3856676bfa7`. The baseline/candidate use identical
dependencies, component database, benchmark class and JVM options. Source digests
and benchmark bytecode identity are retained in the run manifest. No calibrated
number, alpha formula, mixing equation, solver tolerance or default is changed.

## Ownership acceptance

HuronVidalCloneIsolationTest reproduces retained AT/ATT contamination before the
repair in all eight SRK/PR x rules 3/4 x init1/init3 cases. A sibling temperature
or composition change must preserve the source's cached values exactly. The
copy retains scalar results and independently owns the rule's handler, GE phase,
GE components and work arrays; it never reloads a database or evaluates gamma.
Java serialization preserves the detached graph and runtime tuning.

A separate pre-initialization regression found that rebinding parameters could
activate a different retained GE temperature convention. It failed for both EOS
before preserving that convention. Four SRK/PR x classical/HV regressions also
reproduced shared indexed Mathias-Copeman coefficient arrays; changing the clone
from synthetic coefficient 0.8 to 1.7 retuned the source before the repair.
These coefficients test ownership and are not fitted or recommended parameters.

All five ThermodynamicModelTest boolean checks and checkNumerically() are
asserted where a thermodynamic state is evaluated, with existing numerical
derivative accuracy 1e-6. The independent multiphase controls require balances
1e-10, phase/composition normalization 1e-12, and interphase log-fugacity equality
1e-8. Concurrent workers each own their fluid and operations object. Finish
configuration/cloning before launching them; concurrent mutation or cloning of
the same live fluid, other GE implementations, reactive/hydrate/solid models and
whole-process concurrency require their own qualification.

The final 24-case clone suite includes parallel methanol as well as MEG workers,
classical-kij and covolume-selection retention, and an unchanged attraction
fingerprint after mutating a copied pair activation. The covolume-selection test
checks configuration ownership only; it does not extend nonlinear-covolume
thermodynamic qualification.

## Reproduce the default cost matrix

Build baseline and candidate main classes with the same compiler/dependencies.
Compile the candidate HuronVidalClonePerformanceBenchmark separately so that the
same harness precedes test utilities and follows the chosen main-class tree on
each classpath. A clean explicit compilation avoids stale incremental-build
harness classes. The runner refuses preexisting result files and missing state
reports.

The executed default command was:

```bash
python3 devtools/benchmark_huron_vidal_clone.py --baseline-classes target/hv-baseline-classes --test-classes target/hv-benchmark-harness --baseline-label 60e1754c6b7ed14ef9871e8e9d8cf3856676bfa7
```

Summarize the complete default matrix with:

```bash
python3 devtools/summarize_huron_vidal_clone.py target/hv-clone-performance/full
```

There are 64 configurations: SRK/PR x 4/20 components x clone/phase-init/TP-flash/
clone-plus-TP-flash x init1/init3 x classical rule2/HV rule4. Each cell uses four
fresh JVMs, alternating ABBA/BAAB order, two independent JVMs per variant,
16 warmup rounds and eight measured rounds. Each clone/init round has 200 calls;
each flash round has eight complete flashes. JVM options are -Xms128m -Xmx512m.
The reported center is the median of the two per-JVM medians. Raw rounds and
between-JVM min/max remain available; timing is not a CI assertion.

The four-component feed is methane/nC10/water/MEG = 0.65/0.20/0.12/0.03 mol.
The 20-component feed adds 0.005 mol each of ethane, propane, n/i-butane,
n/i-pentane, n-hexane through n-nonane, nC11 through nC15 and nitrogen, then
normalizes the 1.08 mol total. Phase initialization requests one gas root at
310 K/20 bara. The archived final label is GAS for 4 components and OIL for
20 components, for both EOS/rules/init levels. PhaseEos classifies the solved
volume/composition after the request; these prescribed one-phase kernels do
not establish global phase stability. Other cases start at 298.15 K/60 bara
with multiphase checking; flashes
alternate 298.15/298.25 K. Setup and result checks are outside the clock. Cloning
is included only in clone/clone_flash, while internal solver clones remain part
of complete TP-flash cost. Checksums consume calculated results in every batch.
Allocation counts come from the optional HotSpot current-thread counter; they
include the same harness work in both variants and may include JIT-sensitive
temporary allocations.

Classical and HV equilibrium compositions differ. A classical/HV time ratio
combines model evaluation and changed solver work, and does not compare equal
physical accuracy. No complete public iteration/evaluation counter is available
for this path; prescribed full-flash counts, phase types, fractions, compositions,
fugacity coefficients and residuals are retained instead. The unsafe shared-state
baseline is a cost reference, never an acceptable concurrent implementation.

## Recorded results

All 256 default-matrix JVMs succeeded: 2,048 measured rounds and 7,680 final
phase/component rows. Every baseline/candidate phase fraction, composition and
log fugacity coefficient is bit-identical in this matrix. Maximum component
balance residual is 5.1404e-14 and maximum interphase log-fugacity mismatch is
1.7196e-12. Forced-init cases have one gas phase; full-flash cases have gas, oil
and aqueous phases. These are synthetic software checks, not measurements.

Timing variability remains material, including controls whose calculation code
did not change. The full summary retains every round and both JVM medians.
For init3 clone-only calls, the incremental allocation is 81,528 bytes for four
components and 669,240 bytes for 20 components with HV. The classical controls
add 1,920 and 5,760 bytes respectively for detached pure-alpha arrays, while
retaining the existing shared classical-rule object. This repair therefore has
a real memory cost; it does not establish whole-family concurrency safety.

The four-component clone-plus-flash subset was repeated with 512 warmup rounds,
16 measured rounds, eight flashes per round, and four fresh JVMs per cell:

| EOS | Rule | Baseline center (us/call) | Candidate center (us/call) | Candidate/baseline |
|---|---:|---:|---:|---:|
| SRK | 2 | 222.25 | 230.62 | 1.038 |
| SRK | 4 | 749.70 | 743.52 | 0.992 |
| PR | 2 | 225.03 | 218.74 | 0.972 |
| PR | 4 | 724.97 | 840.95 | 1.160 |

These centers are medians of two independent JVM medians, not a confidence
interval. SRK's small apparent benefit is unresolved; PR's observed added cost
remains a performance limitation. HV/classical model ratios also include
different equilibrium compositions and solver work. No speedup or absolute
optimality is established. Reproduce this subset using the runner with
--sizes 4 --operations clone_flash --init-levels 3 --warmups 512 --rounds 16
and a fresh --output directory. Raw longer-warmup rounds, states and manifest
are stored alongside the default matrix.

A separate OpenJDK 17 JFR profile of SRK/four-component HV clone-plus-flash,
init3, retained 204 CPU samples and 918 allocation samples. getGamma appears
in 137 CPU stacks (67.2% inclusive); PhaseEos.clone appears in seven (3.4%).
Allocation sampling attributes substantial weight to phase/component and
physical-property copying. Inclusive weights overlap and are not exact
per-method allocations. This single profile locates work; it does not establish
the same proportions for PR, large mixtures or every production workload.
The profile's timings are excluded from the cost matrix. Raw allocation
counters, source/binary identity and aggregate profile stacks are retained.
Further GE-workspace/allocation optimization must preserve the newly independent
state, exact model values, derivative history and public tuning behavior; it is
not safe to restore sharing merely to recover clone time.

The profile used StartFlightRecording with settings=profile, delay=2s and
dumponexit=true, followed by the manual benchmark arguments:
profile, output-CSV, SRK, 4, clone_flash, 3, 4, 128, 128, 16. The reproducible
Java entry point is HuronVidalClonePerformanceBenchmark; JFR is optional and is
separate from Java 8 API compatibility and hosted Java 8 runtime testing.

### Raw-data integrity

The strict summarizer initially rejected eight extra timing rows and a malformed
state prefix left by a failed CSV-format preflight. Both files began with the
exact failed-preflight bytes. Those prefixes were quarantined byte-for-byte;
the accepted files contain their original header plus every remaining byte.
No successful warmed sample, slow round or outlier was dropped. The retained
preflight fragments and data-quality.json permit reconstruction and SHA-256
verification of the original combined outputs. The accepted matrix is backed
by 256 successful manifest entries and unique cell/JVM/round identities.
Analysis used target/hv-clone-performance/qualified; a clean new default run
can be summarized directly from target/hv-clone-performance/full.

Stored round/state files are deterministic gzip containers. summary.csv holds
all 64 centers, JVM ranges and allocation counts; numerical-summary.json records
closure/result differences. The separate long-warm files retain all 256 additional
rounds and 192 states. Neither failed preflights nor JFR-instrumented timings
are included in the accepted timing summaries.

## Interpretation and boundaries

The guide's [concurrency contract](../../../../../../../docs/thermo/mixing_rules_guide.md)
and thermo-fluid agent describe the bounded supported route. This batch qualifies
software ownership and numerical closure, not inhibitor accuracy or hydrate
dissociation. The existing measured-data errors remain diagnostics, not acceptance
tolerances. Literature/alpha/pure-property compatibility, oil/TEG/heavy-fraction
partition, ice/brine/extrapolation limits and independent hydrate measurements
remain separate campaign dependencies. No absolute optimality or statistically
established speedup is claimed.
