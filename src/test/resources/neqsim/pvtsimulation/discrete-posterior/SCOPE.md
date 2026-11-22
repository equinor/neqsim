# Frozen finite-scenario posterior increment

Base: 863f12f4bfb58026e523748aefab14ecaf2288aa. Reuse merged #4911
(d6f564c6be158738ba3fcd5e11d39e6a864b5da5); do not modify its benchmark.

Question: can the existing finite-scenario integrator return reproducible lower-tail
in-place parameter quantiles and retain joint volume/support, prior/grid, frozen-EOS
and prescribed-flow sensitivity against independent numerical predictions?

Scope: parameter quantiles in the existing ReservoirHistoryEnsemble, tightly coupled
unit tests, an optional complete independent gas-only SRK/PR numerical matrix,
diagnostics, runtime/coverage receipts, Javadoc and the existing indexed guide.
No new framework, EOS tuning, forward-model/flash changes or continuous inference.

The independent reference solves the specified four-interval linear-pressure
Fetkovich/tank equations by bisection using explicit cubic-EOS pressure. It does
not call NeqSim. This compares the discrete forward/likelihood contract; the
merged continuous-ODE timestep benchmark remains separate evidence. All samples,
EOS alternatives, amounts and observations here are synthetic, not measured PVT
calibration or field accuracy. Original and tuned checkpoints stay immutable and
identical within each explicitly supplied EOS hypothesis. All fluid is methane;
segregated water never enters the tracked component inventory.
The finite aquifer water volume is 100,000 reservoir m3; total compressibility
is the supplied capacity divided by that volume. Initial tank/aquifer pressure
is 200 bara, temperature 400 K, interval duration 2,500 s, and each of four
prescribed withdrawals is 4% of the independent initial SRK methane inventory
in a 1,000 m3 tank, times the explicitly supplied flow-realization factor.

Freeze before native comparison: endpoint pressure 2e-5 bara, water 2e-4 m3,
component inventory 1e-6 mol, posterior/prior mass 2e-6 absolute, relative
evidence 2e-5, calibration/holdout chi-square 1e-3, effective count 2e-4, volume means/quantiles 1e-3 m3,
productivity means/quantiles 1e-9 m3/(s bar), dimensionless correlation/shift
2e-5. Pressure quantiles use the endpoint pressure bound. All other parameter
quantiles equal the supplied finite support values; infinite storage may return
positive infinity. Discrete CDF crossings must be separated from requested
probabilities by at least 1e-5 before a cross-code support-value comparison;
boundary semantics are checked separately on controlled unit fixtures.

No tolerance may be relaxed after comparison. Retain failures. Prior probabilities
are complete joint cell masses; the reference integrates explicitly stated prior
densities over each cell. Refine 3x3, 5x5 and 9x9 hypotheses without declaring a
continuous posterior converged. Holdout/forecast pressures never enter weights.
Parameter quantiles are marginal lower-tail model hypotheses, not jointly feasible
parameter vectors, petroleum exceedance percentiles or recoverable reserves.

Stop boundary: measured lab-to-field admission, wet/critical paths, radial-aquifer
equivalence, independently measured production/GOR/composition, multi-tank and full
Tran2025 method/benchmark acceptance remain unresolved under #4740.
