# Wet-gas fixed-cell validation receipt

Base: **69de066ea61b559b7df727117e4a5a83352633fa**, 2026-11-05.
#4868 was merged as **24ef4a6f5ef5769c0f10462585df1cba365acfbb**,
with preserved delivery head **45bc4ca169d69dd15a67f4e331937b64cc35ad26**.
This is the next distinct protocol boundary, not a replacement of that PR.
The issue ledger records the publication commit/tree and exact-head CI/reviews.

## Frozen scope and evidence

Read SCOPE.md before comparison. The sole new domain is explicit first-occupied-
volume gas/aqueous depletion with gas-only withdrawal, retained gas, aqueous
disappearance and no oil. Appearance/reappearance, wet upper-dew inference,
saturation-normalized wet CVD, critical-degenerate paths and measured accuracy
remain unsupported. No shared TPflash solver/default, EOS parameter or reservoir
volume fit changes. Coordinate water/flash ownership through #2937; #4872–#4877
were inspected at their exact heads before this delivery and are not duplicated.

A supporting source snapshot was refreshed from the connected GitHub source,
not trusted as historical checkout state: all 6484 baseline Java blobs matched
the frozen master tree before edits; refreshed supporting text was byte exact.
The only existing Java changes are ConstantVolumeDepletion and
DepletionPvtHistoryGenerator. A new WetGasFixedCellHandoffTest and the standalone
resource probe accompany the implementation. Unrelated historical performance
ZIPs were not needed or used.

Unmodified-master FixedCellDepletionHandoffTest: six tests passed (28.08 s).
WetGasProbe.java then ran the nine unit-inventory temperature/composition paths
on original-master classes: five rejected the phase-family gate and four
completed. baseline.json retains every outcome. A repeat with freshly compiled
master classes was byte identical. The new named protocol completes all 27
independent paths at three inventory scales, for both immutable checkpoint
choices: 54 outcomes / 540 endpoint checks. Fifteen independent paths have
aqueous disappearance; twelve are homogeneous gas limits.

reference.json is generated without NeqSim from independent_reference.py. Its
SHA256 and repeated/optimized-Python reproduction are in reference-proof.json.
The common-tangent/root/lever-rule calculation has a finite-grid lower-support
screen, not a universal stability proof. The constants are shared unfitted
model inputs. These are numerical consistency checkpoints, not independently
measured original/tuned calibrations. No tolerance was adjusted after comparison.

results.json is the exact generated diagnostic, including every outcome,
phase/component/withdrawal inventory and actual separator product volume at
1.01325 bara / 298.15 K. Only present reservoir-phase compositions are reported.
summary.json records maxima: amount error 5.3429261015480733e-11 mol; phase
composition error 7.271960811294775e-14; independent relative volume error
9.43689570931383e-15; internal volume residual 1.1324274851176597e-14; named
component closure 1.1368683772161603e-13 mol. The frozen comparison limits remain
1e-8 mol absolute, 1e-8 composition and 1e-8 relative independent volume, with
unchanged internal 1e-12 stopping targets.

Separate tests cover registered zero-water vs pure methane at three temperatures
and scales, oil/missing-gas/invalid-input rejection, old fixed-family rejection,
translated checkpoint selection, serialized native metadata, defensive snapshots
and unchanged caller/checkpoint serialized state on success and failure.

## Executed validation

Maven was bootstrapped with the requested work-with-neqsim prepare_maven.py helper
before qualified Maven/Spotless runs. commands.json records actual commands,
exits and timings, including earlier failed invocations and their resolution.

- Focused/neighboring fixed-cell suite: 22 tests, exit 0.
- Final full PVT selection: 80 classes, 678 tests, zero failures/errors/skips,
  exit 0; JaCoCo prepare-agent and report explicitly executed.
- Changed APIs/test compile with javac --release 8, exit 0 (class major 52).
  Local runtime is Java 17; this is not Java 8 runtime execution.
- Private Java 8 doclint: exit 0, 47 warnings on both baseline and candidate,
  identical warning signatures. Scoped Checkstyle exits 0 with six reported
  warnings with identical baseline warning signatures; documentation-static.json
  preserves them.
- Strict scoped SpotBugs deliberately sets failOnError=true in temporary POMs: 
  baseline and candidate both exit 1 with the same 20 finding signatures and
  zero analysis errors, zero additions/removals. This is an existing static
  debt, not a clean static-analysis pass. Both use Java 17 bytecode matching
  the default POM. Comparing Java 8 synthetic-accessor baseline bytecode with
  Java 17 nestmate candidate bytecode initially produced an extra unchanged-
  Surface warning; that invalid compiler comparison was corrected, not hidden.
  static-pom-differences.json records all semantic temporary-POM differences.
- Capability/search tooling: pytest with the declared devtools test group,
  41 tests passed. The initial unittest invocation failed to import pytest
  without that group and was replaced by the appropriate runner.

coverage.json is generated from the final JaCoCo XML. Both new public entry
points and the protocol getter have all lines covered; the shared core has
57 covered / 11 missed lines and 73 covered / 17 missed branches. Rejection
coverage is bounded; a universal phase-domain rejection proof is not claimed.

Direct Spotless apply is repeated to a stable tree; direct check, documentation
search, generated inventory freshness and current pre-commit/pre-push stages
are mandatory before publication. pre-commit was made available with the
project-managed uv runner. Initial hooks without inherited Maven-bootstrap
exports failed plugin resolution; rerunning with those exports resolves that
infrastructure mistake. The initial inventory-modification hook failure is
retained, with the regenerated inventory included. No hook is skipped.

## Reproduction and interpretation

From the repository root, bootstrap Maven using prepare_maven.py, then run
`./mvnw -q -Dtest=WetGasFixedCellHandoffTest test`. The complete selected test
command is in commands.json. Generate the independent file using Python with
NumPy/SciPy, then compare the regenerated bytes with reference.json. Full PVT
reference generation is deliberately separate from Java tests.

For the baseline control, compile the two Java files from the base commit to
an isolated directory, using the same --release 17 target as the current POM
and the candidate dependency classpath. Prepend those classes when running
WetGasProbe or the existing FixedCellRuntimeProbe in fixed-cell-transitions.
Use --release 8 separately for API compatibility. No remote file is fetched
by these probes or the independent reference.

Runtime raw data and commands are in runtime.json: four alternating baseline/
candidate fresh-JVM pairs, 30 warmups and eight samples of ten trials, identical
inputs and result checksums. The geometric mean of pair-median ratios is
1.040190, with pair ratios 0.803104–1.174516. Timing scatter limits interpretation; no general
speedup or universal no-regression claim is made. This is an ordinary unchanged-
family API control, not wet-path throughput benchmarking.

## Documentation impact and remaining acceptance

Updated public/private Javadocs, depletion guide, PVT index, PVT agent and EOS
regression handoffs, capability routing and generated engineering inventory.
The executable test is the output-complete laboratory-to-separator example;
no notebook is created. API signatures remain Java 8 compatible and add no
numerical dependency; independent Python/SciPy is qualification tooling only.

RESEARCH_BLOCKERS.md retains primary-source access and data-admission gaps.
No public measured laboratory-to-field chain is claimed; single-tank observed
GOR/composition, finite aquifer/Bayesian uncertainty, multi-tank and full Tran
RCVD numerical acceptance remain open. Laboratory evidence remains separate
from reservoir calibration/holdouts. #4740 must remain open.
