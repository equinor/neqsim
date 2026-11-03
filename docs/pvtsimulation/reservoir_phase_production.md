---
title: "Predict produced composition from equilibrium reservoir phases"
description: "Phase-selective molar withdrawal, component-conservative tank replay, explicit separator bases and independent prediction checks."
---

# Predict produced composition from equilibrium reservoir phases

`CompositionalReservoirHistory.Step.phaseWithdrawal` adds an opt-in extraction
protocol to the existing frozen-EOS tank history. Supply interval totals in **mol
of reservoir gas, oil and aqueous phase**, in that order, the injected component
amounts on the named EOS basis, and a positive explicit subdivision count.
These phase totals are inputs; their **component compositions are predictions**.
The original `Step` constructor still accepts measured component withdrawals.
Its prescribed compositions cannot be used as independent validation predictions.

This is an isothermal, well-mixed tank with specified phase extraction rates.
It does not infer relative permeability, mobility, a well productivity index,
bottomhole pressure, or reservoir-phase molar rates from measured surface volumes.
Those require a separate qualified flow/basis conversion model. Preserve the
original and independently laboratory-tuned `CalibratedPvtModel` checkpoints.
Changing reservoir volume never retunes the EOS.

## Physical step and refinement

Each measured interval is divided into equal substeps. At substep $j$, withdraw
the specified phase's **start-state** equilibrium composition, add the uniform
component injection, and call the existing `SimpleReservoir` TV-flash step:

$$\Delta n_{i,j}^{\mathrm{prod}}=\sum_{\alpha\in\{g,o,w\}}\frac{N_{\alpha,k}}{m_k}x_{i,\alpha,j}.$$

Here $N_{\alpha,k}$ is the supplied phase amount in mol for interval $k$,
$m_k$ is its subdivision count, and $x_{i,\alpha,j}$ is the equilibrium phase
mole fraction at the accepted substep start. The native component ledger is

$$n_{i,j+1}=n_{i,j}+\frac{N_{i,k}^{\mathrm{inj}}}{m_k}-\Delta n_{i,j}^{\mathrm{prod}}.$$

Withdrawal occurs before injection; fresh injection is not implicitly available
for the same substep's withdrawal. Missing requested phases, requests larger than
available phase inventory, invalid compositions and failed TV-flash closure reject
the complete replay. A zero requested amount does not require that phase to exist.
No absent-phase composition is extrapolated. Restart every trial from the frozen
initial state; a rejected trial does not modify the next volume trial or checkpoint.

The extraction integration is first order. Refine subdivisions until pressure,
produced composition and separator outputs meet the study's declared tolerance.
The optional segregated finite aquifer is coupled on every substep; its water is
separate from tracked EOS aqueous fluid and cannot be produced by selecting the
aqueous phase. The model retains its fixed-temperature/immiscible-support limits.

## Inspect actual production and surface products

`Result.getProducedComponentMoles()` returns actual interval-by-component amounts
on the history's named basis, for both input modes. It has no initial-state row.
`Step.getProducedMoles()` is only valid for measured-component inputs; phase mode
throws instead of returning fabricated zero withdrawals. Use `isPhaseWithdrawal`,
`getProducedPhaseMoles` and `getSubsteps` to inspect the schedule.

`Result.getProducedFluid(k)` restores an independent EOS mixture containing the
complete interval's withdrawn amounts. Zero-production intervals have no produced
mixture. Its restored tank pressure/temperature is not a measured surface state.
Processing this aggregate assumes all substep/phase withdrawals are commingled
before separation; separate changing substep conditions explicitly when that
assumption is unsuitable.

`PvtSeparatorPath` freezes strictly decreasing stage pressures in **bara**, stage
temperatures in **K**, and a required protocol/basis provenance reference. The
last pressure/temperature pair defines the final product-volume basis. Every
stage's offgas is independently flashed to that final basis, retaining its oil,
gas and aqueous products. Both liquid phases continue together to the next stage.
Unsupported phases fail; all native EOS components must be accounted for.

`getVolumesM3()` and `getComponentMoles()` return final gas/oil/aqueous products in
that order. `getGasOilRatio()` is final gas m3 divided by final oil m3; no oil gives
NaN because the ratio is undefined. Never label these volumes Sm3 without stating
the final conditions. No separator efficiency, entrainment or sizing is inferred.
The EOS and its multiphase options remain the caller's responsibility.

The bounded CVD/DLE generator now uses this same separator implementation, while
retaining its dry two-hydrocarbon-phase requirement and fixed 1.01325 bara /
288.15 K handoff basis. This does **not** add wet or absent-phase depletion support.

## Executable synthetic qualification

The full reproducible example is
[`PhaseWithdrawalReservoirHistoryTest`](../../src/test/java/neqsim/pvtsimulation/util/PhaseWithdrawalReservoirHistoryTest.java).
It defines untuned PR/classic methane/n-heptane, a 100 m3 tank at 323.15 K and
80 bara, initial gas/oil volume fractions 0.7/0.3, and one 86400 s interval:
80000 mol gas and 20000 mol oil withdrawn, with 2000 mol methane and 100 mol
n-heptane injected. The named array basis is deliberately reversed. The separator
has 10 bara / 303.15 K followed by 1.01325 bara / 288.15 K.

Predeclared checks cover independent TP-phase extraction and direct TV-flash
pressure, native component conservation, unchanged checkpoints/replay,
serialization, unavailable/exhausted-phase rejection, the dilute-gas analytical
pressure limit and subdivision refinement at 1/2/4/8/16. For this fixture the
8-to-16 agreement criteria are 0.5% for pressure/GOR and 0.001 absolute produced
heavy-component fraction; these are example discretization checks, not field
acceptance tolerances. The test logs physical predictions and elapsed runtime.

The executed example gives these conditional predictions. GOR uses the fixed
final basis above; the produced heavy fraction is n-heptane mol/mol.

| Substeps | Final pressure, bara | Produced heavy fraction | GOR, m3/m3 |
| --- | --- | --- | --- |
| 1 | 59.958283 | 0.142949 | 1028.8614 |
| 2 | 60.030818 | 0.146215 | 1000.3103 |
| 4 | 60.067585 | 0.147871 | 986.3514 |
| 8 | 60.086089 | 0.148704 | 979.4513 |
| 16 | 60.095371 | 0.149123 | 976.0211 |

A separate gas-only synthetic pressure inversion recovers 1017.2999925 m3 from
a 1017.3 m3 forward case. A held-out pressure shifted by 5 bara with 0.5 bara
sigma gives held-out chi-square 100.000006 and does not enter the fit. These
are numerical checks with deliberately synthetic observations, not field accuracy.

[`PvtSeparatorPathTest`](../../src/test/java/neqsim/pvtsimulation/util/PvtSeparatorPathTest.java)
also checks an independent stock-tank flash, offgas condensation, explicit final
conditions, water accounting and ownership. Existing CVD/DLE generator tests guard
the shared implementation. These establish analytical/numerical software behavior,
not measured laboratory-to-field accuracy.

## Public qualification sources and remaining acceptance

The public primary [SLB Duvernay report 2012-10037190](https://manual.pvt.whitson.com/files/Duvernay-PVT-Data/SLB-Gas-Condensate-SEP-REC-Sample%20%282.08-2.09%29.pdf)
provides CCE/CVD laboratory evidence. Printed pages 3 and 5 identify 106 °C
reservoir temperature, a reported 4661 psia dew point, and standard conditions
15.56 °C / 101.33 kPa. This differs from the generalized-PVT handoff basis above.
The report is a qualification candidate, not a calibrated fluid/field-history
fixture or an implied redistribution license; this delivery does not reproduce
its dataset or claim agreement with it.

The [2025 Tran et al. paper](https://doi.org/10.1016/j.jgsce.2025.205551) and its
[SSRN preprint](https://ssrn.com/abstract=4932591) remain research leads. Full-text
publisher/identified SSRN delivery access returned errors during the 31 October
2026 source audit. Full equations and independent benchmark reproduction remain
required; no abstract-only implementation is presented.

Issue [#4740](https://github.com/equinor/neqsim/issues/4740) retains public
laboratory-to-field calibration/holdouts, independent measured produced-phase
validation, broader depletion phase paths, multi-tank matching and research-method
qualification. Pressure-volume fitting and the supplied posterior still use the
existing pressure likelihood only. Compare held-out measured GOR/compositions
separately on the same sample/interval/standard/separator basis; do not reuse their
measurements to prescribe the produced composition or silently count them in a
pressure likelihood. In-place pore volume is not recoverable reserves.
