# Feasible Gibbs reference qualification — 2026-11-04

Exact base: e316ffb6d7eb42a176156dd278731a010d9a5196. The draft PR and issue #4740
record the exact candidate head. Java 17.0.20 was used with Java 8 API/bytecode
targeting; both inspected class major versions are52. Actual Java 8 runtime CI
is reported separately, never inferred from local targeting.

## Executed validation

All Maven/Spotless shells evaluated the loaded work-with-neqsim prepare_maven.py
exports first; no resolver/POM/wrapper workaround is committed.

1. Fresh remote recursive tree was compared with the isolated snapshot. 9,998 non-ZIP files,
   including all main/test Java, matched before edits; two unrelated
   text files had normalized formatting, and the generated inventory was hydrated
   separately. No source correctness relies on the original scratch checkout's
   Git HEAD. Missing reference ZIP archives are not used by this PVT test selection.
2. Unmodified master: Java8-profile Maven test for DryFixedCellPhaseTransitionTest
   and FixedCellDepletionHandoffTest: exit0,12tests. Fresh public-entry regression
   launcher against exact base API classes: expected exit1 with the Gibbs gate.
   The independent recorded diagnostic retains all three fresh failing states.
3. Candidate API and new JUnit were compiled using the jdk.compiler module with
   --release8: exit0. Focused direct Surefire: exit0,15tests, zero failures/errors.
   An explicit JaCoCo0.8.15 agent was attached. The final style-only final-variable
   change was recompiled and the main class compared byte-for-byte: identical.
4. Java8-profile Surefire for all79 existing PVT *Test selections plus the new
   class: exit0,670tests in78executed classes, zero failures/errors/skips. Configured
   slow/benchmark exclusions remain; this is not full-repository local testing.
5. Explicit JaCoCo report with target/feasible-gibbs-jacoco.exec: exit0. Exact
   covered/missed method counters are in qualification-results.json.
6. The independently controlled parcel mesh completes16/16 candidate meshes,
   80endpoints, versus13/16 at baseline. Native coarse component inventories are
   bit-identical for all20endpoints. Every mesh closes components/volume under
   its frozen bounds. This shares the EOS and is analytic/synthetic consistency,
   not independent thermodynamic or measured laboratory accuracy.
7. Private Javadoc/doclint for the production class and new JUnit: exit0,24existing
   missing-comment warnings. Checkstyle: exit0; one Java file plus four property
   files analyzed. Production warnings fall from8to6, with no new warning signature.
   Tests are not analyzed by this Checkstyle configuration.
8. Scoped SpotBugs4.10.5.0 with failOnError=true: baseline exit1 and candidate
   exit1,19identical existing signatures, zero analysis errors and zero introduced
   findings. Equivalent baseline class tree replaces only the CVD classes with
   exact base bytecode. This is not a clean SpotBugs pass; existing findings remain.
9. Locked repository tooling tests:50passed. An initial selected-interpreter
   pytest attempt failed because pytest was unavailable; the corrected invocation
   used uv run --project devtools --locked python -m pytest on the four coverage,
   discovery, documentation-search and workflow tooling files.

## Retained unsuccessful implementation probes

The first reference builder omitted mass-density initialization before checking
homogeneous phase volume and was rejected. Initializing the copied properties
fixed that implementation error. A subsequent immutability assertion caught
cached-state changes during shallow-copy reference construction; reconstructing
on a serialized copy fixed it. No frozen input, EOS parameter or numerical bound
was changed to obtain agreement. All final regression/independent matrices pass.

A separate registered-zero-component native path at275K,100/60/30/10/2bara
hits the existing1e-12 volume overshoot gate on both exact baseline and final
candidate: both diagnostic launchers exit1. That path is retained as unsupported,
not silently omitted from an accepted matrix. The new zero-component unit tests
only reference construction and preserves the original zero inventory exactly.

## Runtime

The unchanged explicit-cell control uses the published FixedCellRuntimeProbe,
separately compiled exact-base API classes, identical heap128/512MB,50warmups,
8samples of20trials per fork, and alternating orders. Preliminary three-pair
ratio0.918879 and standard six-pair ratio1.169388 are both retained. Six-pair
ratios span0.698416–2.273162; shared-runner variability is material. No timing
threshold was tuned or result removed. All output checksums are identical.
These measurements are inconclusive for a general no-regression/speedup claim.
The new reference is reached only by explicit fixed-cell precision refinement;
standard TPflash and legacy runCalc execute no additional thermodynamic work.

## Scope and documentation impact

The repair corrects the inventory constraint of the existing energy reference.
It does not change shared flash defaults, EOS versions/parameters, pressure or
withdrawal protocols, public signatures, serialized contracts or field matching.
Existing fugacity/phase/energy/component/volume tolerances remain unchanged.

Affected private Javadoc, the indexed depletion guide, PVT agent/EOS-regression
handoffs and capability test routing/generated snapshot are updated. The literal
guide example remains executable and passes its existing test. No notebook or
new standalone user-guide page is introduced. Wet/critical, measured laboratory
and field, aquifer/Bayesian/multi-tank and Tran acceptance remain open in #4740.

Final formatting, documentation-search, pre-commit/pre-push, inventory freshness
and exact-head hosted CI/reviews are recorded in the PR/issue ledger after the
last source edit. Never infer a complete check from this receipt alone.

## Supplemental EOS-control qualification

Parent draft head: d24962df86f5417836978a24bb0d5e9edde0f46e; unchanged master
e316ffb6d7eb42a176156dd278731a010d9a5196. Fresh remote refs/tree and local blob
comparison establish that production CVD remains blob
15dd76fd6242796a1bf981ecc9bfc10bacc78901. No production implementation, EOS,
protocol or acceptance tolerance was changed for this supplemental batch.
The previous unmodified-master negative regressions and12-test baseline apply
to this same master; no new baseline pass is inferred from the draft.

Before comparing, EOS_CONTROL_SCOPE.md froze108 reference-only controls:
classic SRK/PR, volume correction false/true, the three published inventories,
scales0.001/1/1000, and paired nearby temperature offsets-2/0/+2K with absolute
pressures9.8/10/10.2bara. All108 remain gas/oil. Each makes four reference calls:
exact identity, analytic extensive factor1.125, component target factors
[1.000002,0.999997,1.000001], and deterministic repeat. The nonuniform control
constructs216 homogeneous systems directly from component names/amounts and
declared model settings, without clone/phaseToSystem/the production helper.
Target vectors are mathematical diagnostic inputs, not retuned EOS parameters
or a new physical withdrawal protocol.

The frozen energy bound is1e-9*sampleScale J. Maximum absolute energy errors
divided by sampleScale are1.2212453270876722e-12J for both analytic extensivity
and fresh homogeneous construction. Identity/repeated energies change0J;
source serialization is byte-identical after each of432 reference operations.
All fresh-phase component totals close within64ulp(max(1,N)), with unchanged
T/P/phase types and finite positive volumes. This is analytic consistency and
independent construction sharing NeqSim's EOS, not independent thermodynamic
simulation, measured accuracy, global stability or new depletion-path admission.

Executed on Java17 with Java8 targeting after Maven bootstrap:

- Direct module compiler --release8: exit0 for the changed JUnit; class major52.
- Direct Spotless apply after each Java edit: exit0.
- Surefire FixedCellFeasibleGibbsTest alone: exit0,4tests, including108controls.
- Coupled Surefire FixedCellFeasibleGibbsTest,DryFixedCellPhaseTransitionTest,
  FixedCellDepletionHandoffTest: exit0,16tests, zero failures/errors/skips;
  per-class times12.017/87.965/42.257seconds, with the explicit JaCoCo agent.
- A diagnostic repeat of the control method with the adjacent logger config:
  exit0,108controls; supplies the two observed maxima above. Logging configuration
  is opt-in and does not change the normal test logger.
- The same four locked devtools pytest selections:50passed,exit0.
- Private Javadoc/doclint of the changed test: exit0,zero warnings.
- Surrounding Java8-profile Surefire with the committed surrounding-test-selection.txt:
  exit0,671tests in78classes, zero failures/errors/skips. All selected XML reports
  are newer than the current fork's start; none is an old result. Configured
  slow/benchmark exclusions remain. This reruns the original670-test matrix
  together with the added control method; it is not full-repository local testing.
- Explicit JaCoCo0.8.15 report from feasible-gibbs-jacoco.exec: exit0;
  the unchanged production helper remains covered at42/47lines,32/42branches,
  and262/287instructions. Refinement remains25/28lines,16/24branches.

Ordinary simulation runtime is unaffected by these test/documentation changes:
the production source blob is identical to the parent draft. The earlier paired
base/draft simulation timings remain inconclusive; no broader speed guarantee
is inferred. The19 existing production SpotBugs findings and earlier Checkstyle
comparison remain unchanged historical evidence, not a newly clean static pass.

Documentation impact: this receipt/README/frozen scope, indexed depletion guide
and PVT agent/EOS-regression handoff distinguish the controls from physical
domain admission; the existing generated capability inventory is regenerated.
No public API, user input/output or notebook changes. Final surrounding-suite,
formatting/docs/hooks/inventory/coverage and new exact-head CI/review evidence
are recorded in the PR/#4740 ledger after the last edit.
