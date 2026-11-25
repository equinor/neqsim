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

### Independent gas-only laboratory-to-tank chain

[`IndependentGasLabTankTest`](../../src/test/java/neqsim/pvtsimulation/util/IndependentGasLabTankTest.java)
and its [complete reference protocol and data](../../src/test/resources/neqsim/pvtsimulation/gas-lab-tank/README.md)
provide a separate, independently calculated binary-SRK benchmark. One synthetic
closed-cell laboratory point identifies the sole interaction parameter; a whole
420 K CCE is held out before the calibrated checkpoint enters the reservoir.
Independent component balances and the pressure equation generate four
production/injection intervals. Only volume is matched to three pressure points;
the fourth pressure, produced composition and actual surface gas volume are
withheld. A held-out pressure perturbation checks fit exclusion. Original and
calibrated checkpoints remain separate and immutable.

The same benchmark also checks the continuous well-mixed gas balance against an
independent analytical solution, with 1 through 1,024 explicit substeps for
alternating injection and equal injection/production. Withdrawal-only,
injection-only and no-flow endpoints are invariant under refinement. A constant
total inventory still changes composition according to the exponential mixing
limit. The [frozen refinement scope](../../src/test/resources/neqsim/pvtsimulation/gas-lab-tank/REFINEMENT_SCOPE.md)
retains the failed 1 mol target at 64 substeps and tests finer meshes against the
same target. The four-substep finite-protocol benchmark above must not be treated
as a converged continuous-flow answer. These limits assume uniform gas mixing
and prescribed molar rates; they do not qualify well inflow, relative permeability
or spatial displacement.

The [unequal-sigma inversion reference](../../src/test/resources/neqsim/pvtsimulation/gas-lab-tank/WEIGHTED_SCOPE.md)
adds declared synthetic pressure perturbations and independently minimizes the
calibration-only weighted objective. It compares unequal and equal sigmas,
common sigma scaling, held-out exclusion, an inactive upper bound and an active
upper bound. A bound flag describes the supplied search constraint; it does not
establish geological accuracy, uniqueness or a posterior interval. The synthetic
sigmas are numerical test inputs, not measured uncertainty estimates.

This is gas-only synthetic numerical qualification. No oil is present and GOR
remains undefined, so it does not complete two-phase GOR or measured laboratory
and field acceptance under #4740. No EOS parameter is fitted against reservoir
volume, and no recoverable-reserves interpretation is introduced.

### Existing two-phase consistency and refinement checks

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

## Independent binary two-phase numerical chain

The [binary PR reference](../../src/test/resources/neqsim/pvtsimulation/binary-tank/SCOPE.md)
adds finite-protocol two-phase predictions independently of NeqSim. Its standard-library
oracle isolates cubic roots by derivative extrema/bisection, solves liquid/vapor
fugacity equality for binary tie lines, applies the lever rule and bisects pressure
at fixed tank volume. Independent all-root Gibbs checks sample the bounded path;
they are not a general phase-stability or near-critical guarantee.

The synthetic methane/n-heptane sample has explicitly declared critical constants,
acentric factors and PR coefficients. An independent 600 K/200 bara occupied-volume
observation identifies only the symmetric interaction parameter. An entire 650 K CCE
is withheld. Original and tuned serialized checkpoints remain separate. No reservoir
measurement enters that identification. The source, synthetic observations and
reference implementation are original project work under the repository license;
no third-party laboratory dataset is redistributed. The mathematical model follows
[Peng and Robinson (1976)](https://doi.org/10.1021/i160057a011), with the numerical
coefficients explicitly frozen in the reference rather than inferred from a database.

At 323.15 K/80 bara the initial gas/oil volume fractions are 0.7/0.3 in a 100 m3
tank. These are **volume**, not mole fractions. Each of four 86400 s intervals
withdraws 8000 mol reservoir gas and 2000 mol reservoir oil and injects 200 mol
methane/10 mol n-heptane, with four equal substeps. Produced component amounts
are predictions from equilibrium phase compositions, not prescribed component
histories. A single equilibrium separator at 288.15 K/1.01325 bara defines both
final product volumes; GOR is gas m3/oil m3 on that named basis.

The first three pressures calibrate only bounded initial volume [80,120] m3.
The fourth pressure, produced composition and GOR are held out. The native
[workspace test](../../devtools/test_binary_reservoir_java.py) checks those outputs
at the fitted volume, independent reference inventories, held-out exclusion,
failed-trial restart and byte-identical EOS checkpoints. It also compares matched
finite protocols at 1/2/4/8/16 substeps. No finite subdivision count is silently
called a converged continuous-flow result. The [retained reference values](../../src/test/resources/neqsim/pvtsimulation/binary-tank/reference.json)
and frozen tolerances make the numerical benchmark reproducible.

| Interval | Reference pressure, bara | Produced methane, mol/mol | Reference surface GOR, m3/m3 |
| --- | --- | --- | --- |
| 1 | 77.967827862 | 0.853295968 | 1254.847451 |
| 2 | 75.936477269 | 0.852053280 | 1238.785710 |
| 3 | 73.905750266 | 0.850793008 | 1222.864849 |
| 4 (held out) | 71.875450588 | 0.849514763 | 1207.082864 |

Run the oracle's `test_*.py` modules with unittest discovery. Compile the workspace
and dependency classpath, then run `devtools/test_binary_reservoir_java.py` through
pytest in its own process with `NEQSIM_TEST_RESERVOIR_JAVA=1`. The test owns its JVM
and validates that history classes come from workspace `target/classes`. CI performs
both commands explicitly and retains numerical diagnostics in its log.

This strengthens **independent synthetic numerical qualification** of the existing
single-tank/phase-extraction/separator APIs. It does not establish measured accuracy,
water behavior, wet/critical PVT paths, field inflow, relative permeability, aquifer
or Bayesian uncertainty, multi-tank transmissibility, the Tran method, or whole-issue
#4740 completion. Report fitted in-place pore volume; recoverable reserves require
additional recovery physics and are outside this benchmark.

The laboratory calibration/holdout composition is methane/heptane 0.8/0.2.
The specified reservoir phase volumes imply their own explicitly calculated
bulk composition. Thus this experiment tests transfer of an independently
identified binary interaction across a synthetic composition family. It does
not establish a composition-identical measured sample-to-reservoir chain.

## Preserve the laboratory sample during tank initialization

Use `CompositionalReservoirHistory.fromSampleComposition(model, historyReference,
components, temperatureK, pressureBara, steps)` for a representative homogeneous
sample whose initial bulk composition should be retained. It restores the tuned
checkpoint independently, flashes it at reservoir T/P and derives the initial
gas/oil/aqueous **EOS phase-volume fractions**. Missing phases receive exactly
zero volume; unsupported or duplicate phase types, invalid volumes and failed
flashes reject explicitly. The factory retains caller-accepted EOS/multiphase
settings. It does not reconstruct spatial gas/oil contacts from a single sample.

For each equilibrium phase, its fraction is $f_\alpha=V_\alpha/\sum_\gamma V_\gamma$.
The existing tank initialization multiplies its component amounts by
$V_\mathrm{trial}f_\alpha/V_\alpha=V_\mathrm{trial}/\sum_\gamma V_\gamma$.
Every phase therefore receives the same factor, preserving the complete bulk
composition for every volume trial. These are the EOS volumes used by
`SimpleReservoir`, rather than molar phase beta or separately density-corrected
phase volumes. Neither substitute guarantees equal phase scaling.

The original constructor with explicit phase-volume fractions remains available
for geological phase-volume assumptions that deliberately imply a different bulk
mixture. Record that distinction in sample/history provenance. Both routes retain
immutable original/tuned models and transactional trial restart; only pore volume
is fitted. This initialization adds one TP flash when constructing the opt-in
history and leaves the original constructor unchanged. Phase-selective replay
now checks the necessary whole-interval mole balance before its first substep:
initial moles plus all interval injection minus all phase extraction must remain
positive. Exact sums of the supplied floating-point amounts avoid cancellation
and intermediate-overflow rejection of positive residuals. Passing this admission
check does not establish temporal or phase feasibility; the existing
withdrawal-before-injection availability checks still apply. It adds no replay
TP flash and leaves measured-component replay unchanged.

The [same-sample benchmark](../../src/test/resources/neqsim/pvtsimulation/binary-tank/SAME_SAMPLE_SCOPE.md)
keeps methane0.8/n-heptane0.2 throughout the laboratory and initial tank. It uses
the same independent PR constants and600K/200bara kij identification as the binary
family test, retains the entire650K CCE holdout, and adds an entire323.15K
closed-mass two-phase CCE at80/70/60/50bara. The native calculation uses the actual
closed-mass CCE protocol with no withdrawal. Independent fugacity equality and
lever rule predict cell volume, phase amounts and phase compositions.

At the same sample's323.15K/80bara state, the independently derived phase-volume
split initializes100m3. Four prescribed extraction/injection intervals and a
single288.15K/1.01325bara separator give independently predicted pressures,
produced compositions and final-basis GOR. The first three pressures alone fit
volume over[80,120]m3; the final pressure/composition/GOR are held out. The
[complete reference outputs](../../src/test/resources/neqsim/pvtsimulation/binary-tank/same-sample-reference.json)
and native receipts preserve the numerical results, frozen bounds and runtime.
The Java test `SampleCompositionReservoirHistoryTest` checks the factory call,
named-basis conservation, gas/oil/wet limits, duplicate/unsupported phase guards,
volume scaling, serialization and immutable checkpoints. The dedicated native
Python test qualifies the full independent chain against workspace Java.

The independent initial gas/oil EOS volume fractions are 0.860817100494 /
0.139182899506; the molar sample composition remains 0.8/0.2. The independent
finite four-substep protocol predicts:

| Interval | Pressure, bara | Produced methane, mol/mol | Surface GOR, m3/m3 |
| --- | --- | --- | --- |
| 1 | 78.070298613 | 0.853319428 | 1255.154224 |
| 2 | 76.140117252 | 0.852139863 | 1239.892967 |
| 3 | 74.209281999 | 0.850943690 | 1224.749297 |
| 4 (held out) | 72.277619736 | 0.849730544 | 1209.721713 |

This closes composition-identical initialization for the stated synthetic case.
It does not establish measured laboratory/field accuracy, wet production,
critical-degenerate depletion, general global stability, continuous-flow
convergence, aquifer/Bayesian acceptance, multi-tank matching or the Tran method.
All remaining #4740 acceptance criteria and the final merged audit still apply.
