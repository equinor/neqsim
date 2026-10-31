---
title: "Finite-aquifer history replay and discrete Bayesian uncertainty"
description: "Physical reservoir-volume aquifer support, implicit compositional tank coupling, joint prior scenarios and pressure-history posterior diagnostics."
---

# Finite-aquifer history replay and discrete Bayesian uncertainty

This workflow extends the [frozen-EOS measured-history tank](reservoir_material_balance.md#compositional-measured-history-replay).
Keep the original sample and independently laboratory-tuned EOS in `CalibratedPvtModel`.
Reservoir matching changes volume/support assumptions; it does not retune that EOS.

## Physical aquifer and units

`FetkovichAquifer` accepts initial water volume $W_i$ in reservoir m3, total water/rock
compressibility $c_t$ in 1/bar, productivity $J$ in reservoir m3/(s bar), and initial
average aquifer pressure $p_{ai}$ in bara. Its rate and storage equations are

$$C=c_tW_i,\qquad\frac{dW_e}{dt}=J(p_a-p_r),\qquad p_a=p_{ai}-\frac{W_e}{C}.$$

Positive $W_e$ means water entering the reservoir. No surface-volume conversion or
extra multiplication by `Bw` is applied. The water-volume basis is fixed; the
slightly-compressible, constant-property assumptions need engineering justification.
The model follows the rate-plus-material-balance structure of
[Fetkovich (1971), SPE 2603](https://doi.org/10.2118/2603-PA).
The [OPM AQUFETP reference](https://opm.github.io/opm-flow-editor-support/keywords/AQUFETP.html)
documents corresponding physical parameters. OPM deck water volumes/productivity
use a surface-volume basis with PVTW; convert that basis before reusing them here.

`initialState()` returns an immutable zero-influx checkpoint.
`advance(state, durationSeconds, pressureStartBara, pressureEndBara)` integrates the
linear pressure path exactly. For $x=J\Delta t/C$, $L=1-e^{-x}$ and
$R=1-L/x$, the finite-storage interval increment is

$$\Delta W_e=C[(p_{a0}-p_{r0})L-(p_{r1}-p_{r0})R].$$

The implementation uses series expansions and `expm1` near zero exposure.
Zero productivity gives no exchange. Zero storage gives no volumetric support.
Explicitly infinite water volume with positive compressibility gives fixed aquifer
pressure. Signed exchange permits recharge; finite-water exhaustion, including an
interior pressure-ramp extremum, is rejected. This lumped storage law is distinct
from radial transient diffusion in `VanEverdingenHurstAquifer`; only common storage
limits are compared, not claimed transient equivalence.

## Coupling to the existing EOS tank

`CompositionalReservoirHistory.replay(initialVolumeM3, aquifer)` solves the reservoir
end pressure implicitly with the aquifer interval response. The existing
`SimpleReservoir` TV flash closes the tracked EOS fluid volume to

$$V_{\mathrm{tracked}}=V_{\mathrm{pore}}-W_e.$$

Aquifer water stays segregated and immiscible. It occupies pore space, but does not
enter the tracked component inventory. The measured component schedule therefore
cannot produce this segregated water. Initial EOS water and measured component
injection remain part of the tracked fluid; they are separate from aquifer support.
Water mixing, dissolution, salinity, water chemistry, rock expansion and thermal
balances are not added by this approximation. Use a different qualified model when
those effects matter.

For opt-in [phase-selective withdrawals](reservoir_phase_production.md), the aquifer
is coupled on each explicit extraction substep. Only tracked EOS aqueous fluid
can be selected for production; segregated support water remains separate.

Each accepted result retains initial/end reservoir pressures, average aquifer
pressures, cumulative aquifer volume, times and tracked component amounts.
Reject a nonpositive remaining fluid volume, unavailable components, negative
cumulative segregated support, failed TV-flash volume closure or a failed coupled
root. Root probes outside physical storage bounds never become predictions. Minimum/maximum interval influx
are checked as well as endpoints, so recharge cannot conceal temporarily negative
segregated water or exhausted pore space.
`SimpleReservoir.advanceComponentAmounts` has an overload accepting the complete
cumulative external water volume; later ordinary component steps retain the last
accepted external volume. Rejected steps leave state, time and calculation identity
unchanged.

`fitVolume(minimumVolumeM3, maximumVolumeM3, aquifer)` holds aquifer parameters fixed
while estimating initial pore volume against the supplied pressure sigmas. Report
bound-limited solutions and rejected trials. Refine the measurement/flow intervals:
exact integration of a prescribed linear pressure path does not make the unknown
coupled nonlinear pressure trajectory exact.

## Joint Bayesian scenarios

`ReservoirHistoryEnsemble.Candidate` combines one frozen history, pore volume,
optional physical aquifer, positive prior probability mass and provenance.
Use initial gas/oil/aqueous fractions in each history to encode geological gas-cap
assumptions. Different independently accepted laboratory models and measured
component-amount realizations can represent PVT and production-history uncertainty.
These uncertainties are propagated only when explicitly supplied; the utility does
not infer missing distributions or repair a miscalibrated EOS.

All candidates must share named component order, initial temperature/pressure,
observation/forecast times, pressure observations, sigmas and holdout selection.
For independent Gaussian pressure errors, evaluation uses

$$w_i=\frac{\pi_i\exp(-\chi_i^2/2)}{\sum_j\pi_j\exp(-\chi_j^2/2)}.$$

Here $\pi_i$ is a complete **joint prior mass**, not a density at a grid point.
A nonuniform grid needs cell-integrated masses. Logarithmic normalization prevents
likelihood underflow. Correlated pressure errors require another likelihood;
production realizations and laboratory uncertainty should not count the same
measurement uncertainty twice.

| Returned evidence | Interpretation |
| --- | --- |
| Normalized prior/posterior masses | Discrete joint hypotheses, including EOS/history assumptions |
| Best likelihood index | Minimum calibration chi-square, separately from prior preference |
| Rejected candidates and prior mass fraction | Infeasible hypotheses retain explicit diagnostic weight |
| Posterior means and parameter correlations | Volume, initial gas/aqueous fractions, aquifer capacity and productivity; constant/non-finite dimensions report NaN |
| Prior standardized shifts | Posterior-minus-prior mean in prior standard deviations; a conflict diagnostic, not a significance test |
| Effective scenario count | Inverse sum of squared weights; concentration of this ensemble, not MCMC sample size |
| Log evidence without Gaussian constant | Relative normalizer only; not absolute data-density evidence |
| `pressureQuantiles(q)` | Lower-tail model-pressure quantiles over all history/forecast times |

Held-out pressures never enter the posterior likelihood. Prediction-only intervals
use NaN pressure/sigma pairs and remain available for forecast quantiles. Quantile
0.1 is the lower tenth percentile, not the petroleum exceedance convention.
Quantiles exclude future measurement noise. Only the product $C=c_tW_i$ enters pressure/storage response; pressure history
cannot distinguish $c_t$ from $W_i$ separately. Include observations around the
response timescale $C/J$ and independent geological bounds. Refine the joint hypothesis grid and
check sensitivity to prior/support assumptions before interpreting intervals.

The tests check independent Bayes odds, heldout exclusion, sigma weighting,
underflow-resistant normalization, rejected prior mass, volume/aquifer confounding,
production-realization uncertainty, serialization and quantile ordering.
Aquifer tests compare analytical storage limits and an independent RK4 integration;
coupled tests check separate inventories, pore-volume closure, known-volume recovery
and interval refinement. These are analytical/synthetic software qualifications.
They are not a matched public laboratory-to-field validation or recoverable-reserves
estimate.

## Remaining qualification

Issue [#4740](https://github.com/equinor/neqsim/issues/4740) also requires independent
laboratory/field qualification, broader phase-path coverage and independently
predicted produced-phase validation. Multi-tank transmissibility follows qualified
single-tank matching. ES-MDA/external ensemble integration and the 2025 rescaled-CVD
research method are separate gated extensions. None is implied by this finite
scenario integrator.
