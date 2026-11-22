# VU caloric state qualification

Base: draft PR #4577 head `29d8d307cef748f186150206b832b27a14b7e58e`,
master `ab0a14e64104c003b61afe117d08ae7541e714f6`. Date: 2026-10-25.
The selected production change is caloric initialization/reuse only. It does not
activate the coupled-Newton proposal stored under `rejected-coupled-trial/`.
No model, fitted parameter, damping, bound, acceptance threshold or global
initialization policy is retuned.

## Numerical/state evidence

`VUCaloricStateQualificationTest` independently configures SRK/PR, mixing rules
2/4 and gas (350 K/40 bara), propane/butane liquid (300 K/20 bara) and
methanol/water liquid (330 K/50 bara) fixtures. The source gives exact mole feeds.
Its temperature/pressure derivatives of V and U are resolved with central steps
(0.005 K, 0.002 bara) and (0.0025 K, 0.001 bara), at 1 and 1000 mol scales.
`jacobian.csv` retains all 96 entries, including direct derivatives and both
numerical steps; maximum declared mixed error is 1.69e-7 (acceptance 1e-5).
The matrix entries describe fixed-composition phase thermodynamics; they do not
establish a globally convergent VU update through phase transitions.

Native Gibbs, pressure and temperature identities are asserted. The identities
are extensive sums: the default 1e-10 bound is retained per mole and explicitly
multiplied by the inventory scale. Investigation before choosing this scale
found oil Gibbs residuals about 2.51e-12 mol at 1 mol and 2.51e-9 mol at 1000 mol,
with EOS pressure residual about 6.33e-10 bara. The same per-mole residual is
resolved at both inventories; the checker implementation and default are not
changed. The existing broader native-model qualification independently asserts
all composition identities and numerical fugacity derivatives.

The returned-state regression checks fresh, repeated, changed and returned V/U
targets. It compares H/U before and after explicit level-two initialization and
against a separately configured fluid at the returned T/P/feed. It tests the
existing general VU acceptance contract, not a new strict-convergence promise.
The original 32-refinement/1e-11 release-inventory checks remain unchanged.
The maintained CPA expansion documentation example is also executed as a test.

Volume derivatives use NeqSim internal volume units (physical m3 multiplied by
1e5), T is K, P is bara and U/H are J. The identity coefficients therefore satisfy
U_T=Cp-P*V_T and U_P=-T*V_T-P*V_P without an extra pressure conversion. The standalone
raw-evidence reporter is retained as a Java source text file; it requires the
compiled test fixture and the same dependency classpath.

## Final-source timing protocol

`performance.csv` and `performance-summary.json` record 64 independent JVMs:
SRK/PR x rules2/4 x gas/oil/aqueous/24-component gas x baseline/candidate x two
process pairs. Each JVM runs one EOS/rule/scenario, warms 10 batches and measures
6 batches; version and rule order alternate between pairs. Each batch constructs
a fixture and solves 20 alternating V/U targets, permitting up to32 public
refinements per target. Both versions must meet the same V/U gates1e-6/1e-5,
EOS-pressure1e-8, component-balance1e-10 and normalization1e-12. Target construction
is outside timing. Actual residuals, checksums, iterations and solve counts are
retained; results are not assumed bit-identical merely because they meet common
gates. These are synthetic numerical workloads, not measured inhibitor accuracy.

JVM: OpenJDK17.0.20+8, Linux amd64, `-Xms128m -Xmx1g`. Both production-class
variants were compiled identically with `--release8` and selected first on an
otherwise identical classpath. No local build, test or other timed JVM ran
concurrently with this matrix. Thread-local allocated bytes use the optional
HotSpot management interface and include fixture setup/validation. This is not a
heap-retention or universal allocation claim. Timing remains host/JIT sensitive;
there is no wall-clock CI assertion or absolute-optimality claim.

Reproduce with `run-performance.py`, providing a common compiled classpath and
baseline/candidate class directories. Use `--warmup 10 --measured 6` for this
matrix. Compile the baseline from the stated base head, not a moving branch.
The executable Java harness is `VUSinglePhasePerformanceBenchmark`; its Javadoc
and factory give the exact feeds/work. The small fixtures contain 2/3 components;
the large fixture has24 components, methane0.99mol, each C2–C20 component0.0001mol,
and nitrogen/CO2/water/MEG0.00001mol each (total0.99194mol).

Final-source observations: all16 checksum cells and iteration/solve counts match.
Returned caloric drift falls from a worst2.36e-7 relative scale to zero; the48
separate returned-state records also match independently configured H/U exactly.
The24-component HV batch allocates about15.1% fewer bytes than baseline; small
cases show little allocation change. Timing is mixed: process-pair ratios span
0.771–1.260 in the10/6 matrix. The apparent SRK-classical gas slowdown is not
stable in three longer150-warmup/40-measurement process pairs: ratios are
0.866/0.977/1.103 (see `longwarm.csv` and summary). Large PR-HV pairs are about
4% slower in the shorter matrix despite fewer allocated bytes. No general speedup
or full non-regression classification is made. Correct current caloric values
require a final level-two state; no unsafe cache-validity assumption is used to
avoid that refresh. Further cost minimization remains open.

Earlier complete-TP rule2/HV4 controls and their unresolved timing uncertainty
remain under `neqsim/thermo/phase/cubic-root-deflation/performance/`. The production
change here is confined to VU; no new TP-flash speedup is inferred from VU timing.
The large-mixture HV/classical ratio remains an explicit optimization target.

## Rejected coupled-Newton trial

The subfolder preserves the experimental production source, 64-JVM30/12 timing
matrix, derivative/strict-state records and separator traces. Those timings are
**not evidence for the selected initialization-only change**. The trial passed
62 focused tests and the liquid-withdrawal case, but its broader347-test run
introduced a transient-separator pressure-variance failure. Isolated baseline,
refresh-only, coupled and reuse controls attributed that new failure to the
coupled proposal; the selected initialization-only path passes the unchanged
separator smoke test. No test or variance tolerance was relaxed.

More seriously, the baseline separator trace shows20 of31 rejected VU results
being consumed despite that smoke test passing. The first rejection has14.18%
volume error and the last relative volume error is4848.28. The coupled trial finds
a locally closed compressed-liquid state at an intermediate target and changes
the trajectory, but phase-transition/globalization, controller behavior and
caller rejection/rollback need separate qualification. This is tracked in
[issue #4583](https://github.com/equinor/neqsim/issues/4583), coordinated with
#3298/#3860/#2937/#4579. Do not reactivate the experimental source based on timing
or one locally closed state. Neither trace proves a qualified full transient.

`inventory-baseline.csv` records the separate strict propane/butane release
refinement failure: targetV=0.02m3, targetU=-3125236.8772788546J,
energyScale=3125289.414030712J; all32 attempts are retained. The trial's passing
inventory endpoint is archived separately. Caloric refresh does not by itself
resolve that strict VU/globalization acceptance criterion or the other #4579
legacy/stability/process failures. Physical HV parameter, hydrate and clone-state
qualification remain open in #4573/#4575/#4576.
