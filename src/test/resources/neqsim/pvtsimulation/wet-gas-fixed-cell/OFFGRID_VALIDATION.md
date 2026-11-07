# Off-grid wet-gas fixed-cell qualification

Frozen parent: **c84b3d81c2312455074670ebd3f3e4a8160068af**, PR4878.
Original PR base: **69de066ea61b559b7df727117e4a5a83352633fa**.
Current-master control: **f901a7b445bd8bd923dc21ee49be8befc3a8b13f**,
including merged cubic-phase and minor-water flash work. The issue ledger records
the final publication head/tree, exact-head CI and reviews. This increment changes
no production Java: all **3660** such file blobs are identical to the parent.

## Frozen concept and provenance

Read OFFGRID_SCOPE.md before generating/comparing new rows. This extends numerical
qualification of the same explicit initial-occupied-volume gas/aqueous experiment,
with gas-only withdrawal, retained gas, aqueous disappearance and no oil.
Appearance/reappearance, wet upper-dew inference, critical-degenerate behavior,
modified-black-oil extrapolation and measured accuracy remain unsupported.
All model constants, equilibrium/volume gates and comparison tolerances are unchanged.

The new inputs are 442.5/452.5/457.5 K, water mole fractions0.06/0.085/0.095,
initial0.01/10/100 mol, and the20pressures from200to2bara stated in the frozen
scope. All27paths/540reference states are retained:21paths have aqueous
disappearance and6stay gas-only. No reference path has aqueous appearance.
Both original/tuned checkpoint selections give54outcomes/1080endpoint comparisons.
The checkpoint labels are numerical consistency controls, not measured independent
EOS calibrations. Actual separator products use1.01325bara/298.15K.

independent_reference.py exposes its existing output loop as generate(), with
unchanged equations/constants and branch, common-tangent, lever-rule and bounded
finite-grid lower-support checks. Its original fixture regenerates byte for byte
(SHA256b81a1910cf8f37c9805d5c8a747e8a3e6681c8b34b7e32b770606c2732bf7376).
offgrid_reference.py declares the new complete paths. Repeat and optimized-Python
outputs are byte identical; versions, hashes and timings are in
offgrid-reference-proof.json. No NeqSim result or reservoir volume is used to fit
an input. These are original synthetic numerical artifacts under the repository
license, not transcribed measured PVT/field observations.

Each pressure sequence is a separate experiment protocol. The longer sequence
is a path check, not a claim of equal final composition under different withdrawal
schedules, differential-depletion convergence or reservoir timestep convergence.
The finite composition-grid support screen is not a global stability proof.

## Unmodified-master and current-target controls

Every one of6498baseline Java blobs (including resource probes) matched the fresh
master tree before baseline compilation. Maven clean rebuild and the six-test
FixedCellDepletionHandoffTest control pass. OffGridMasterProbe uses only the old
fixed-family method, retains all27outcomes, and reports21phase-family rejections
and6completed paths, with no caller mutation. Those rejections implement the old
method's contract; the distinct wet-gas route supplies the required convention.
The original nine-case control still reports5rejections/4completed.

A separate local source combination used the owned CVD/generator plus the exact
current-master phase/flash implementations. All23focused tests in five classes
pass, including all1080new comparisons. Its complete diagnostics are byte identical
to those from the separately rebuilt owned source. No shared solver/default was
changed or copied into the PR, and master was not merged/rebased into the branch.

## Results at unchanged bounds

All54new outcomes pass. offgrid-results.json retains every row's present phases,
phase amounts/compositions, retained/withdrawn component inventories, occupied
volume and actual separator products. offgrid-summary.json records:

| Quantity | Maximum error | Frozen comparison bound |
| --- | ---: | ---: |
| Phase/component/withdrawal amount | 7.114975275612778e-12mol | 1e-8mol absolute |
| Present phase composition | 8.594513989379493e-14 | 1e-8 |
| Relative independent reference volume | 1.687538997430238e-14 | 1e-8 |
| Internal relative volume residual | 1.532107773982716e-14 | unchanged1e-12 |
| Named component closure | 2.842170943040401e-14mol | 1e-10*max(1,initial named component mol) |

No failed case, phase, pressure or scale is omitted. The original native results
remain byte identical to the parent's results.json. The shared fixture helper
still requires27cases and10rows for the original matrix; the new fixture requires
27cases and20rows. Count guards, all numerical assertions, checkpoint/caller
serialization checks and surface-component closure checks remain active.

## Executed checks and interpretation

- Owned-source clean full PVT selection:82source classes selected,80executed
  classes,**679tests**,0failures/errors/skips. The existing slow-tagged CO2CorrosionAnalyzerTest and
  HydrocarbonScrubberSaturationPressureStabilityTest source classes are excluded
  by the unchanged build configuration. See offgrid-tests.json.
- Explicit JaCoCo prepare-agent and report executed. offgrid-coverage.json is the
  standard production-class report; JUnit XML confirms fixture method execution.
- Changed test/probe and affected APIs compile with javac --release8 (major52).
  Local execution is Java17, not an actual Java8 runtime claim.
- Private Java8 doclint exits0 with the same47warning signatures as the parent;
  scoped Checkstyle exits0 with the same6existing warnings.
- Strict SpotBugs fixture/probe scope: parent0/candidate0findings,0analysis or
  missing-class errors. Strict unchanged-main scope:20findings,0added/removed,
  exit1. This is retained static debt, not a clean production static pass.
  Temporary POMs set failOnError=true; their initial namespace serialization
  failed before analysis, was corrected and all three scopes were rerun.
-41capability/search/tooling tests pass. The project runner resolves modules from
  this checkout, not the previous scratch checkout.
- Direct stable Spotless, documentation search, inventory build/freshness and both
  current pre-commit stages are required before publication; actual final exits
  are retained in offgrid-commands.json and the final exact-head issue ledger.
  No formatter failure is waived.

New qualification runtime is measured separately in the command receipts.
Production Java bytes and dependencies are unchanged by this increment. The
parent's noisy ordinary-path runtime control remains recorded; no new simulation
speedup or universal no-regression claim is made.

## Documentation impact and stop boundary

Updated this receipt/README, the depletion guide, EOS-regression and PVT-agent
handoffs, capability limitations and regenerated inventory. The PVT index already
routes to that guide. The executable JUnit test is the output-complete example;
no notebook is created. This test/data increment changes no public API, runtime
input, default, component identity or numerical target.

The frozen27off-grid paths complete this qualification batch; further physics
is a separate acceptance boundary. Measured whole-experiment PVT calibration and
holdouts, laboratory-to-field pressure/GOR/composition validation, remaining
finite-aquifer/Bayesian uncertainty/sensitivity, qualified multi-tank matching and
the full Tran2025 equations/version/independent numerical benchmark remain open.
The source-access and NIST/NS1 admission blockers remain in RESEARCH_BLOCKERS.md
and the issue ledger. No measurement sigma, in-place/recoverable-reserves mapping,
EOS retuning or field accuracy is inferred from these deterministic comparisons.
#4740 stays open.
