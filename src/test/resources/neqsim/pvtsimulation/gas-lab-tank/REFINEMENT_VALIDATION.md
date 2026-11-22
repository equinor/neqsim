# Run49 continuous-flow refinement and limiting qualification

Base b20e324a60a94778b11425257bb3d8e9636389b7; parent draft head
65e1c25fdf06cba0f953c3f5ed92dd6605f1bf80. All10085 expected parent files
matched before editing. Fresh master and owned head are unchanged from Run48;
its unmodified-master nine-test baseline remains exact-revision evidence, not a
newly executed baseline claim. No production Java/API/default/dependency changes.

## Independent protocol and results

The same independently laboratory-identified EOS is frozen. No EOS/volume fitting
occurs in these new trials. A separate analytical well-mixed component ODE gives
the continuous endpoint. The independent finite reference implements the documented
withdrawal-before-injection protocol. Every trial restarts from the identical
initial checkpoint; named component order remains reversed in native input.

The [initial scope freeze and pre-native refinement response](REFINEMENT_SCOPE.md)
retain the failed1mol64-substep target. Independent errors4.24354/11.13817mol
triggered finer meshes before any native comparison. The tolerance remains1mol.
The native test retains64-substep errors as negative controls, checks first-order
error ratios0.45..0.55 at every doubling, and applies the same target at1024.

| Case | Native64-substep component error, mol | Final mesh | Native final error, mol |
|---|---:|---:|---:|
|Alternating injection|4.2435408961|1024|0.2651965218|
|Equal injection/production|11.1381684020|1024|0.6960267622|
|Withdrawal only|9.31323e-10|64|9.31323e-10|
|Injection only|9.31323e-10|64|9.31323e-10|
|No flow|9.31323e-10|64|9.31323e-10|

All43 native replays agree with the finite reference under unchanged bounds:
maximum retained-component discrepancy1.30386e-8mol (bound1e-4), pressure9.09495e-13bara
(bound1e-6), actual surface-gas relative discrepancy1.88738e-15 (bound1e-7).
Maximum named conservation residual1.21072e-8mol passes1e-9relative. These maxima
and timings refer to the focused run retained in continuous-results.json. The
surrounding run repeats the assertions. Constant total inventory is preserved;
composition changes by the exponential mixing limit. No-flow/injection-only have
no produced fluid and explicitly reject reconstruction; no oil/GOR is fabricated.
The1mol continuous-component target is not a1e-6bara continuous-pressure target:
the1024-substep continuous pressure differences are1.79245e-6/4.84888e-6bara.

Primary60/75digit pressure/inventory/product outputs are identical. Seven raw
withdrawal-only residual diagnostics differ (1e-53 versus1e-69mol, see summary).
Those are retained, not silently clipped to force whole-file identity. The original
four-substep reference.json remains byte-identical after reference.py's main guard.

## Executed validation and cost

Focused class:3tests pass,100.487s; new method84.478s,43replays82.531s in aggregate.
Final full PVT:83source classes selected,81executed/**682tests**,0failures/errors/skips,
exit0,**296.036s**, including explicit JaCoCo prepare/report. The same two
existing slow-tagged source classes remain excluded by unchanged defaults.
Raw per-class outcomes and production coverage counters are committed. No new
production-wide static or actualJava8-runtime pass is claimed locally.

Changed-test Java8 API compilation exits0 (major52); private doclint0warnings,
scoped Checkstyle0issues; strict scoped SpotBugs0findings,0analysis/missing-class
errors with changed class included. Scoped POM configuration is the same as Run48
ledger6675158885, with current classes. It remains outside publication. Actual
commands/exits are in refinement-commands.json; a diagnostic wrong-path inspection
is recorded separately from the successful compilation.

Direct formatter stability/check, generated inventory, documentation search and
both current hook stages must pass before publication; first-pass receipts are
in refinement-publication-gates.json and the frozen repeat is in the issue ledger.
All Maven/Spotless shells use the installed Maven bootstrap helper. Production
source is unchanged: measured test/replay cost is not ordinary simulation overhead
or a simulation-speed claim. Hosted exact-head CI/reviews are a separate gate.

## Documentation impact and boundary

Updated test Javadoc, this complete numerical example/reference, the existing
indexed reservoir-phase guide, PVT-agent handoff, capability limits and regenerated
inventory. No notebook, new API, runtime framework, dependencies or solver fork.
These are synthetic analytical/independent-code results, not measured accuracy.
Gas-only uniformly mixed constant prescribed rates do not qualify well inflow,
relative permeability, two-phase GOR, spatial displacement, finite aquifer/Bayesian
acceptance, multi-tank or Tran2025. Full measured-data/source blockers persist.
Issue4740 remains open; keep laboratory and reservoir calibration/holdouts separate.
