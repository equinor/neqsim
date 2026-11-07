# Frozen unequal-sigma volume inversion qualification

Before independent generation and native comparison: sole draft4885 head
c2c87ef2413f701a9221699dd0dd7df63c1c0aa1, baseb20e324, target5fa68e8.
Reuse exactly the existing independently laboratory-identified classic SRK sample,
400K/200bara tank,1017.3m3 true initial volume, four equal substeps per interval,
named phase production/injection schedule and separate whole420K laboratory holdout.
No EOS/production/surface-basis retuning; no new physical model or posterior claim.

Add explicit synthetic calibration-pressure offsets[-0.2,+0.4,-0.1]bara to the
first three independent pressure observations. Fourth pressure stays an independent
held-out observation, with sigma0.5bara. These chosen perturbations/sigmas are
synthetic numerical inputs, not random draws or experimental error estimates.
Fit sum((predicted-observed)/sigma)^2 for calibration intervals only.
Compare unequal sigmas[0.1,0.7,0.25]bara with equal[0.5,0.5,0.5]bara. A third
case scales the unequal sigmas by3; a fourth shifts only held-out pressure+5bara.
Use independent Decimal forward component balances/SRK pressure and physical-volume
golden-section minimization over800..1300m3, including both bounds. Native search
uses its existing log-volume strategy, without algorithm/default changes.
A fifth case restricts unequal-weight fit to800..1000m3, with explicit bound flag.

Freeze tolerances: native/independent fit volume0.001m3, calibration chi-square1e-6,
independent pressure1e-6bara, standardized residual1e-5. Native predicted outputs
at fitted volume must match independently replayed fitted-volume reference with
component1e-4mol and actual surface gas relative1e-7. No equality with the true
unperturbed produced composition is expected after a noisy volume fit.
Scaled sigmas retain fit volume within0.001m3 and scale chi-square by1/9 within1e-6.
Held-out perturbation must leave fitted volume/calibration chi-square exactly
unchanged. Its residual/chi-square is independently evaluated, not assumed100
because a noisy calibration may already leave a nonzero held-out residual.
Bounded case must select1000m3 exactly and flag Fit.isAtBound(), without claiming
true-volume recovery or an uncertainty interval. Serialization stays unchanged.

Before native comparisons independently regenerate at60/75digits and compare
primary JSON numerical outputs. Preserve all cases and any failures. This
qualifies deterministic weighted inversion only, not measured accuracy, Bayesian
identifiability, continuous posterior inference, aquifer or multi-tank acceptance.

## Pre-native independent finding
The unequal-sigma optimum is987.00816564m3; equal weights give1024.84787867m3.
Thus the originally proposed800..1000case is interior, contrary to the initial
bound-limited expectation. Preserve it as a negative control (boundLimited=false),
not as a manufactured1000m3 solution. Add a sixth independently defined800..980case
to exercise an actual upper-bound solution at980m3. Retain all original inputs,
tolerances and cases; this additional bound was frozen before native execution.

Standardized residuals follow the documented API convention observed-minus-predicted.
The first native attempt failed because the reference serialized the opposite sign;
correct only this reporting convention. The fit objective, optimal volumes, squared
objectives, synthetic observations, EOS and every tolerance are unchanged.
