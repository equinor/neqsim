# Frozen same-sample laboratory-to-tank qualification

Frozen before native comparison on master
`99fa305f69dac3dc82f237aa41389774f68fadc1`, 25 November 2026. Reuse every
constant, EOS convention, surface basis, extraction schedule, pressure fitting
bound and numerical tolerance from [SCOPE.md](SCOPE.md).

## Engineering question and compatibility

Can the accepted laboratory sample initialize every reservoir-volume trial with
its own bulk composition, rather than an independently prescribed gas/oil volume
split? The new `fromSampleComposition` factory must derive gas/oil/aqueous
fractions using the flashed sample's EOS phase volumes in m3, the same basis
`SimpleReservoir` scales. Preserve every named component fraction and proportional
inventory scaling across volumes. No density-based volume substitution, added
component, absent-phase seed, EOS parameter change or new flash solver. The
existing explicit-phase constructor remains unchanged.

Duplicate or unsupported phase types, nonpositive/nonfinite phase volumes and
positive fraction underflow must reject explicitly. Gas-only, oil-only and wet
supported-EOS limits require conservation and restart tests; these are bounded
initialization checks, not universal phase-domain qualification.

## Independent laboratory and reservoir protocol

The synthetic sample is methane 0.8 / n-heptane 0.2 mol/mol throughout laboratory
calibration, laboratory holdouts and initial tank. Original kij = 0 and accepted
kij = 0.02 stay separate. Only the independent 600 K / 200 bara one-mole occupied
volume identifies kij. The entire 650 K CCE remains withheld. Add an entire
323.15 K closed-mass two-phase CCE at 80/70/60/50 bara, also withheld from fitting.
CCE retains all components at every pressure; the independent lever rule gives
phase amounts and total volume, rather than separate arbitrary-root flashes.

At 323.15 K / 80 bara the two-phase lever rule for z = 0.8 determines molar beta,
then gas/oil EOS volume fractions. Scale the complete sample to 100 m3. Keep four
86400 s intervals, each prescribing 8000 mol gas / 2000 mol oil withdrawal and
200 mol methane / 10 mol heptane injection, with four equal withdrawal-before-
injection substeps. Fit only initial volume to the first three pressures, sigma
0.1 bar, bounds [80,120] m3; the final pressure/composition/GOR are held out.
Also compare matched finite protocols at 1/2/4/8/16 substeps. Altering a held-out
pressure must not alter the fitted volume.

## Frozen acceptance bounds

| Quantity | Bound |
| --- | --- |
| Pressure | 1e-4 bar absolute |
| Produced fraction | 1e-6 mol/mol absolute |
| Final-basis GOR | 1e-4 relative |
| Fitted volume | 0.01 m3 absolute |
| Laboratory volume | 1e-7 relative |
| Initial component fractions | 1e-10 absolute |
| Normalized EOS/component/volume closure | 1e-9 |
| Log fugacity equality | 2e-9 |
| Sampled Gibbs/tangent lower-bound allowance | 1e-9 dimensionless Gibbs/RT |

Preserve NaN/infinity admission controls. No bound may be enlarged after
comparison. Original/tuned checkpoints must remain byte-identical after failures,
restart and matching.

## Coupled interval-admission regression

The first native chain completed slowly; a later stack diagnostic locates the
long-running stage in volume fitting, before the infeasible-trial test. Independently
of that cost, the 1 m3 trial is provably infeasible. Before comparing this admission
repair, add the independent necessary condition
Nend = Ninitial + sum(injected) - sum(phase withdrawals) > 0 before any interval
TV flash. The 1 m3 same-sample inventory is approximately 4054.789 mol; its
10000 mol extraction and 210 mol injection cannot satisfy that balance at any
subdivision. Reject it at substep 0.

Use exact sums of the supplied binary floating-point amounts so cancellation or
intermediate overflow cannot falsely reject a positive residual. This is an
admission condition only; retain per-phase/start-state substep feasibility and
failed-trial restart checks. Freeze cancellation, overflow, exact-zero, temporal
ordering and ordinary-valid-case controls. No accuracy bound changes. Measure
rejection and valid replay/fit timings separately; the earlier full-case elapsed
time alone does not attribute a cause or establish a speedup.

## Provenance and stop boundary

Synthetic numerical accuracy only: original repository-generated inputs under the
repository license, PR equation attribution DOI 10.1021/i160057a011. No measured
laboratory/field dataset, full CVD/DLE/separator qualification, universal global
stability/critical-path proof, water mixing, mobility, aquifer uncertainty,
multi-tank, RCVD/Tran 2025 method, continuous-flow limit or recoverable-reserves
claim. The old 0.7/0.3 volume-fraction case remains a distinct composition-family
test. Full #4740 completion still requires all original merged acceptance evidence.
