---
title: "Finite-aquifer history replay"
description: "Physical reservoir-volume aquifer support and implicit compositional tank pressure/volume coupling."
---

# Finite-aquifer history replay

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

## Qualification boundary

Analytical finite-storage limits, independent RK4 comparisons, dilute-gas interval
refinement and reservoir/component volume closure are synthetic qualification.
Independent public laboratory/field matching, joint Bayesian uncertainty and the
remaining multi-tank/research deliveries remain under issue #4740.
