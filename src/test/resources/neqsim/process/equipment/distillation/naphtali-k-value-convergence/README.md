# Bounded Naphtali-Sandholm K-value convergence

Baseline: PR #4577 head `9b2269837eee56d9adb4368460504d5b61354f21`.
Only NaphtaliSandholmSolver bytecode is substituted for the baseline control;
all existing HV derivatives, pressure-root checks and physical parameters remain.
The candidate is this resource's enclosing commit. OpenJDK 17.0.20 is used.
The changed Java sources also compile with ECJ 3.41.0 `--release 8` against
Java 8 APIs; this is not a Java 8 runtime suite.

`ColumnConvergenceEvidence.java.txt` reuses the unchanged synthetic cold-column
fixture and its full published balance assertions from
`NaphtaliSandholmPublishedStateTest`. Rename it to `ColumnConvergenceEvidence.java`,
compile with the source-built main/test classes and test dependency classpath,
then run `ColumnConvergenceEvidence output.csv implementation-label`. To reproduce
the baseline, compile only the baseline solver into a separate output directory
and place it before candidate main classes on that JVM's classpath. Do not replace
candidate source or phase-root code. The output path is a CSV; no exception is
silently treated as acceptance.

The two/three-sweep control has 13,455 capped K evaluations and a maximum final
log-K update of 3.8923 over the solve trajectory. It exhausts/falls back, publishes
a 115.3171 W inconsistent reboiler duty, and fails the original assertions.
The bounded 30-sweep candidate has nine capped intermediate evaluations, maximum
trajectory log-K update 1.6373e-5, and converges in 32 Newton iterations. Its public
relative energy/MESH-energy residuals are 5.60796e-10 / 2.80398e-10, and the original
0.02 W terminal/per-tray checks and component balances pass. Telemetry maxima
include off-solution trials; they are not final accepted equilibrium residuals.
The iteration cap remains a bound, not a promise of convergence for every column.

More fugacity sweeps are required than in the failed baseline. These sweeps now
use level-1 fixed-T/P initialization, avoiding unused caloric derivatives; separate
enthalpy calculations retain level 2. The cold/retained/recovery bound and the
existing 1e-8 convergence tolerance are shared. No material, energy, phase-root,
equilibrium or public solve-acceptance gate is relaxed.

`baseline.csv` and `candidate.csv` each contain one independent JVM run. The saved
17.28 / 7.85 s times were measured while other validation ran. They are illustrative
only: the baseline fails and performs fallback work, so this is not a matched
successful-work speedup or a general performance/non-regression claim. Work counts
and physical acceptance are the primary evidence.

Validation on the final source: 171/171 focused column, HV/native/numerical fugacity,
root, saturation, TV and incipient tests; 540/540 documentation tests. The eight
column-publication methods include an added SRK/PR x classical/HV x gas/liquid
check that level-1 fugacities/volumes agree with level 2 and satisfy pressure closure.

The wider compatibility suite still fails: packed-column failure handling, scrub
column, transient three-phase response, aqueous endpoint roundoff, gradient roundoff,
reactor scenarios 3/4, export-compressor map/PH input, and incomplete ternary phase
envelope. Two total-condenser MESH cases also remain unqualified in a preceding
30/30/20-bound trial. That trial is not final-tree qualification of the shared
30-bound policy; fresh CI must retest them. The existing #4579 compatibility issue
and draft status remain. No unresolved result or tolerance is changed to force a pass.

## Implicit vapor-composition Newton follow-up

The `implicit-newton-control.csv` control uses exactly the solver source from PR
head `02917efba52dc87e412dcdbad8d222c6db5fc8d1`, compiled into an isolated directory
and placed before the candidate classes. All other thermodynamic classes, parameters,
and the unchanged cold-column fixture/assertions are shared. `implicit-newton.csv`
uses the enclosing commit's bounded Newton K correction and guarded local derivative
predictor. Both JVMs used OpenJDK 17.0.20 and a 650 MiB Java heap. Reproduce them with
the same saved `ColumnConvergenceEvidence.java.txt` harness described above.

Both runs pass every published material and energy assertion. Forced-root fugacity
sweeps fall from 102,200 to 54,573, while tray evaluations rise from 22,450 to 25,910
and outer iterations rise from 32 to 57. The candidate's public energy and MESH-energy
residuals are 1.36450e-11 and 6.82251e-12. Each predictor receives a fresh EOS residual
check; the 30-sweep bound and 1e-8 inner convergence gate are unchanged. Vapor derivative
evaluations require `init(3)`; liquid fugacity-only work retains `init(1)`.

Capped intermediate evaluations rise from nine to 197, with maximum final log-K
updates 1.6373e-5 and 3.17215 respectively over the off-solution trajectory. Those
counters describe trial states, not final accepted equilibrium. This is a successful
work-budget repair for the tested fixtures, not evidence of universally improved
stability. The independent public balance gates remain necessary. Single-pair wall
times of 7.70 and 5.65 seconds were obtained during other validation and establish
no overall speedup or general performance non-regression.

The two telemetry fixtures retain their original less-than-two-sweeps-per-evaluation
budgets. Their old requirement to observe a capped two-sweep evaluation is replaced
with stronger assertions: zero capped evaluations and maximum final update no greater
than 1e-8 in the nominal telemetry case. The severe and changed-inlet work budgets,
all published-state balances, and all physical acceptance tolerances are unchanged.
The focused 12-method telemetry, changed/severe warm-start and publication suite passes.
The total-condenser connectivity repair is separately covered by a recording-tray
regression that fails against the original accelerated sweep and by the unchanged
MESH material/ratio/temperature acceptance cases.

The final no-side-draw publication retains the independent total vapor flow `V` and
normalized `K*x` composition used by the enthalpy calculation. Directly publishing
an unnormalized vapor-flow vector changed that established basis at approximate
BP/Sum-Rates endpoints. Preserving `V` while atomically assigning component flows
and retaining the forced root fixes the three EOS/mixing-rule/inactive-tolerance
cache regressions without changing their physical balance tolerances. All 15
focused cache, K-work, severe/changed warm-start and publication methods pass.
The complete 34-method `ColumnSpecificationTest` class passes with its one existing
skip after AUTO accepts already qualified warm bases; the two original MESH cases,
connectivity regression and selected Newton repeatability methods also pass.
