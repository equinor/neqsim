# Same-inventory Gibbs reference for explicit fixed-cell refinement

Base: `e316ffb6d7eb42a176156dd278731a010d9a5196`. Original diagnostic and
ownership coordination: issue #4740, comments 6637082176 and 6637100803;
#2937 comment 6637443544. All fluids and observations here are project-created
synthetic numerical controls. There are no measured data or fitted parameters.

## Physical and numerical contract

Gibbs ordering is a constrained comparison at the same temperature, pressure and
named component amounts. The three retained public-API inputs use methane,
n-butane and n-heptane, classic unshifted SRK, repository component/BIP data,
300/325 K and absolute pressures 10/9.9 bara. Original inventories and reference
energies in mol/J are retained in `baseline-energy.json`. The initial approximate
phase allocations miss stored component totals by up to 2.86e-12 mol. The old
reference therefore compares different inventories and rejects the more accurate
endpoint despite passing fugacity closure.

For each component i and homogeneous phase p, let a[p,i] be its initial
nonnegative phase amount and N[i] its stored total. Reconstruct a feasible trial
allocation as a'[p,i] = a[p,i] * N[i] / sum_p(a[p,i]). This conserves each component
algebraically and preserves nonnegativity. Evaluate these homogeneous phases on
serialized copies with the same EOS, T/P and phase branches. The copies need not
be mutually equilibrated: any feasible trial is a valid upper bound for the
constrained Gibbs comparison. Reconstructed totals must agree within 64 ulps
at max(1,abs(N[i])); inputs already agreeing at that roundoff level keep their
original reference without phase copies.

The original 1e-10*max(1,abs(G_reference)) J energy allowance is unchanged.
So are the 1e-12 log-fugacity and relative-volume targets, final 1e-12 component
balance, phase-family/Gibbs checks and transactional publication. Reference
construction does not modify the caller or fit/change its EOS. Standard TPflash
and legacy runCalc do not execute this reference reconstruction. This local
upper-bound check is not a global phase-stability proof or measured accuracy.

## Retained evidence

- `baseline-energy.json`: three failing warm states and fresh public replays,
  raw and independently reconstructed same-inventory energies, component closure.
- `baseline-mesh.json`: four cases and 16 gas-parcel meshes; 13 complete, three
  rejected under the original energy reference.
- `candidate-mesh.json`: all 16 meshes complete (80 endpoints). All 20 native
  coarse endpoints have bit-identical component inventories to baseline.
- `WithdrawalProbe.java`: complete standalone parcel controller and diagnostics.
  It shares the EOS and uses reflection only to isolate the existing precision
  gate. It is not a proposed public API or independent thermodynamic simulator.
- `FixedCellFeasibleGibbsTest`: nine fresh public replays across three extensive
  scales, every original closure gate, frozen energy controls, zero-component
  reference construction and byte-level input immutability.
- Runtime and validation JSON/receipt retain actual executed results. No absent
  phase properties, recoverable-reserves claim or field prediction is supplied.

### EOS-control qualification

The same test also retains108 energy-reference controls frozen in
`EOS_CONTROL_SCOPE.md`: classic SRK/PR, volume correction off/on, the three
published inventories, scales0.001/1/1000, and paired nearby T/P points. It checks
the exact identity shortcut, analytic extensive scaling by1.125 and nonuniform
component redistribution against newly constructed homogeneous systems. These
systems use component names/amounts and declared EOS settings, without cloning,
`phaseToSystem` or calling the production energy-reference helper. Every copied
operation must preserve caller serialization, and repeat energies are bit-identical.

The energy bound is frozen at1e-9*sampleScale J. This is an analytic extensivity
control plus independent construction sharing NeqSim's EOS, not an independent
thermodynamic simulator. It does not admit a new depletion pressure path, wet or
critical fluid, customized EOS/BIP or measured laboratory/field case. Aggregate
maximum errors are logged by the test when its logger is enabled; the validation
receipt and qualification JSON retain the executed values.

For the aggregate error log, run the EOS-control test method with Surefire's
`test` selector set to
`FixedCellFeasibleGibbsTest#eosControlsPreserveExtensivityBranchesAndCallerState`
and `log4j.configurationFile` set to the absolute file URI of the adjacent
`eos-controls-log4j2.properties`. The normal test logger remains unchanged.
The full surrounding Surefire selector is retained verbatim in
`surrounding-test-selection.txt`; configured slow/benchmark tag exclusions remain.

The initial 1e-8 mol absolute independent-reference bounds remain frozen. A
registered zero-component 275 K native withdrawal path still reaches the original
1e-12 volume overshoot boundary on unmodified master; that inherited path is
outside this repair, not a newly accepted depletion path. The zero-component
unit test concerns energy-reference construction only.

That negative native-path receipt describes the original baseline. The subsequent
initial-state reuse repair and its bounded zero-registration qualification are
recorded separately in `../fixed-cell-initial-state/`; the baseline failures here
are retained unchanged for provenance.

## Reproduction

Use the exact base and candidate revisions recorded in the PR/#4740 ledger.
Bootstrap Maven with the work-with-neqsim helper. Resolve the Java 8-profile
project test classpath and compile the affected API/tests to Java 8 bytecode.
The recorded local runtime is Java 17; module compiler/Javadoc launchers were
used because the javac executable is not present. Actual Java 8 runtime testing
is reported separately from API/bytecode targeting.

The standalone controller takes one output filename. Compile it outside source
roots with the resolved project test dependencies and candidate classes, then
run it with a 512 MB heap. Repeat with separately compiled unmodified CVD classes
first on the classpath. Do not mix in a packaged NeqSim JAR. The complete report
retains rejected meshes as failures even when report generation exits zero.

The unchanged explicit-cell runtime control is the existing
`../fixed-cell-transitions/FixedCellRuntimeProbe.java` and portable
`../fixed-cell-transitions/compare_runtime.py`. Compare alternating baseline and
candidate forks with identical heap and inputs. This receipt uses 50 warmups,
20 trials/sample and retains both the preliminary three-pair run and the standard
six-pair driver. All output checksums must be identical. These shared-runner
samples do not establish general speedup or a no-regression guarantee.

Wet/critical paths, independently measured laboratory calibration/holdouts,
field-history matching, aquifer/Bayesian/multi-tank acceptance and the full Tran
RCVD benchmark remain separate #4740 dependencies. Do not retune the EOS against
reservoir volume or reinterpret a numerical control as experimental calibration.
