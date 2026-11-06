# Run51 weighted volume inversion qualification

Parent c2c87ef2413f701a9221699dd0dd7df63c1c0aa1; baseb20e324;
current target5fa68e8. All10096parent files matched expected remote blob hashes
before edits. The exact-target unmodified9-test baseline and682-test integration
from Run50 remain valid same-revision evidence; they are not newly executed here.
All production Java/APIs/defaults/dependencies remain unchanged.

## Frozen inputs, independent results and native validation

See [WEIGHTED_SCOPE.md](WEIGHTED_SCOPE.md). Synthetic calibration-pressure offsets
are[-0.2,+0.4,-0.1]bara; the fourth pressure is held out. The laboratory-identified
EOS, whole420K CCE holdout, gas production/injection, four substeps and actual
separator basis remain unchanged. No EOS or hidden parameter fits reservoir volume.
Independent physical-volume golden-section minimization uses Decimal60digits;
the complete reference JSON reproduces byte-identically at75digits. Native code
retains its existing log-volume strategy and stopping rule.

| Case | Independent volume, m3 | Native volume, m3 | At supplied bound |
|---|---:|---:|---|
|Unequal sigmas|987.008165640|987.008205223|false|
|Equal sigmas|1024.847878669|1024.847910564|false|
|Unequal sigmas times3|987.008165640|987.008205223|false|
|Held-out pressure plus5bara|987.008165640|987.008205223|false|
|Upper bound1000m3|987.008165640|987.008192864|false|
|Upper bound980m3|980|980|true|

Unequal weights move the deterministic estimate by about37.84m3 relative to
equal weights for the same observations. Scaling calibration sigmas by3 retains
volume and divides calibration chi-square by9. Changing only held-out pressure
leaves fitted volume and calibration chi-square exactly unchanged. Its independent
held-out chi-square is115.289219742, not100: the noisy fit already leaves a nonzero
held-out residual. Bound diagnostics distinguish inactive1000 from active980;
a constraint-limited volume is not labeled true volume or a confidence interval.

Maximum native/independent volume error3.958321e-5m3 passes0.001. Calibration
chi-square error1.069723e-11 passes1e-6; pressure error4.963491e-7bara passes1e-6;
standardized residual error1.480689e-6 passes1e-5; produced-component error
2.209037e-5mol passes1e-4. Actual surface-gas checks pass1e-7relative. Retained
checkpoint serialization is byte-identical, and replay at each selected volume
reproduces pressure arrays exactly. Full independent and native rows are retained.
No oil exists: volume0/GORundefined remain explicit.

## Pre-native finding and authoring correction

The initial planned800..1000bound case was independently shown to be interior
before native tests. It is retained as a negative control; a separate800..980
case supplies an active-bound test. No original input/tolerance was changed to
force1000m3. The first native attempt failed on a reporting-convention error:
the reference used predicted-minus-observed rather than the documented API's
observed-minus-predicted residual. Only that reference sign was corrected; the
objective, fitted volumes, synthetic observations, EOS and every tolerance remain
unchanged. Failed10.603s execution is recorded, followed by the corrected pass.

## Commands, checks and test cost

Corrected focused method: six bounded fits plus replays; all assertions pass.
The raw focused per-fit timings total42.465s (machine/run dependent). These are
test costs, not ordinary simulation overhead. No production implementation changed.
Final full PVT selection:83source classes selected,81executed/**683tests**,
0failures/errors/skips, exit0,**319.844s**, explicit JaCoCo prepare/report.
The same two existing slow-tagged source classes remain excluded by default.
Per-class outcomes and selected production counters are retained. No new41-tooling
pass is claimed. Changed-test Java8 API compilation exits0, major52; private
doclint0warnings, scoped Checkstyle0issues, strict scoped SpotBugs0findings and
0analysis/missing-class errors with changed class included. No new clean
production-wide static pass or local actualJava8-runtime pass is claimed.

Direct formatter stability/check, docs search, generated inventory and both
current hook stages must pass before publication; first receipts are in
weighted-publication-gates.json, final frozen repeat in the issue ledger.
All actual commands/exits, including the failed focused attempt, are retained.
The preceding head c2c87ef has all observed hosted workflow groups passing;
Java8/21 Ubuntu/Windows each report19824tests,0failures/errors,209skips, with all
682PVT tests executing without skips. That is prior-head evidence; new-head CI
and independent review must be checked separately after this update.

## Documentation impact and stop boundary

Updated this complete reference/numerical example, test Javadoc, existing indexed
reservoir-phase guide, PVT-agent handoff, capability limits and generated inventory.
No new runtime/MCP operation, framework, dependency or notebook. This increment
qualifies deterministic unequal-sigma inversion of synthetic gas-only observations.
The supplied sigmas/offsets are not measured error estimates or random data;
a weighted residual or bound flag is not a posterior, uniqueness proof or a
recoverable-reserves estimate. Measured laboratory-to-field/two-phaseGOR, broader
wet/oil/critical PVT, aquifer/Bayesian, multi-tank and full Tran2025 acceptance
remain unresolved. Keep issue4740 open.
