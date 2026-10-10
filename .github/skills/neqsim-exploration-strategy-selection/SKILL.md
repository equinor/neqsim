---
name: neqsim-exploration-strategy-selection
description: "Screening workflow for choosing how to explore a near-field target next to a producing host: dedicated exploration well versus an exploration sidetrack, pilot or extended-reach lateral drilled from a planned production well (PLX), or a late PLX in a later producer. Monte Carlo of discovery, volume, fluid, development value, timing and cost with exploration cost paid in every outcome, EMV, outcome distribution, value of information, break-even chance of success, crossovers and an approval-point data checklist. USE WHEN: a task asks which exploration strategy to prefer for a satellite or cellar target, whether to piggy-back on a planned producer, or what to put in an approval-point (APbo/DG0) package for an exploration decision."
---

# Exploration strategy selection (dedicated well vs PLX)

Reference implementation: `task_solve/2026-10-10_limbus_apbo_exploration_strategy_dedicated_well_vs_plx_from/step2_analysis/scripts` (`limbus_model.py` development economics and volumes, `07_strategies.py` strategies, `08_sensitivity.py` tornado and maps, `13_crossovers.py`). Copy the pattern, not the numbers: every prospect input there is an assumption.

## 1. Strategies to compare

| Id | Strategy | What differs |
|----|----------|--------------|
| S1 | Dedicated exploration well | Full rig spread and well cost in every outcome, earlier data, long lag from discovery to first oil (appraisal, concept, sanction) |
| S2 | PLX from the planned producer | Cost is a sidetrack or lateral (days, not months) and shared with the producer, data later (tied to the producer schedule), shorter lag because the PLX can be completed as a producer, but a harm term when the planned producer is delayed or lost |
| S2h | S2 with S1 as fall-back | Add a reach probability (trajectory, step-out, torque and drag, ECD); if the PLX cannot reach, the dedicated well is drilled |
| S3 | PLX in a second, later producer | Later data, equal cost, optional gain in chance of success from the earlier wells |

Do nothing is a fifth option with value zero; it is only dominated if the EMV of the best strategy is positive at the honest chance of success.

## 2. Model rules that decide the answer

1. **Exploration cost is paid in every outcome** (dry, discovered but uncommercial, commercial). Subtract it from every realisation, discount it from its own year, and refund it at the NCS tax rate (78 % with the 2022 cash-flow tax; use the task's fiscal block). Putting the cost only in the success branch hides the dry-hole risk and flips the ranking.
2. **Value after discovery is floored at zero** (a commercial development is only sanctioned when its post-tax NPV is positive), but the dry well cost is not.
3. **Same worlds for every strategy** (common random numbers): sample geology, price and cost once, then evaluate all strategies on them. Report the paired difference and its standard error, not two independent EMVs.
4. **Chance of success is the product of prospect factors** (presence/reservoir, quality, trap/seal, charge). The break-even chance of success is the multiplier on Pg at which EMV is zero; compare it with the mean Pg.
5. **PLX harm term**: probability that the planned producer is lost or delayed (x) value at risk of the producer, plus a delay cost, plus non-productive time. This is the term that makes S1 competitive; scan it.
6. **Data sufficiency** is a probability, not a given. When a PLX does not deliver a sufficient sample, test and pressure data, add an appraisal step (cost and one to two extra years) before sanction.
7. **Value of information** of perfect knowledge of discovery before drilling = E[max(value, 0)] - max(E[value], 0). It bounds what a cheaper data or reach study may spend.
8. **Crossovers**: scan the differential EMV(S2h) - EMV(S1) over one input at a time (loss probability, value at risk, PLX duration, planned-producer delay, extra PLX lag, dedicated-well duration, dedicated-well lag) with a fixed seed, and interpolate the zero. A recommendation without crossovers is an opinion.

## 3. Traps

- `np.percentile(x, 10)` is the **P90 (low)** value; keep the P90/P50/P10 convention in tables and the p10 < p50 < p90 ordering in `results.json`.
- Calibrating GRV so the P50 volume lands between two analogues is circular if the same analogues are then used to "validate" the volume. Label it an assumption and put the ranges in `data_gaps`.
- A peer-fleet benchmark of a prior P10 with n about 20 wells is a poorly defined percentile; use a bootstrap interval of the sample percentile, not a point comparison.
- Lag asymmetry (long after a dedicated well, short after a PLX) is a modelling assumption that carries part of the PLX advantage; show it as a crossover (`p_lag_add`, `d_lag_add`) rather than burying it.
- Cumulative production to date is a lower bound for EUR; do not call a prior that is above the cumulative of the median host well a failure without saying so.
- A PDM rate benchmark needs one gas-to-oil-equivalent convention for the host plan and the allocated data; Sodir uses 1000 Sm3 gas = 1 Sm3 OE, Centuries rich gas used about 1.15e-3.
- Hydrate with SRK-CPA needs `setHydrateCheck(True)` before `hydrateFormationTemperature()` (see `neqsim-flow-assurance`); the failure "Can not return phase number 4" means it was forgotten.
- A stand-alone prospect far from infrastructure has no host to curtail it: anchor CAPEX on Sodir cumulative field investment (`prfInvestmentsMillNOK`, nominal NOK) against peak annual OE rate of stand-alone fields, report the minimum economic volume per infrastructure share borne by the discovery (1.0 stand-alone, 0.4 area hub) instead of a named host, and expect the Sodir sample of far discoveries with a resource estimate to be too small (n = 1) for a size distribution: use the whole frontier distribution and state the bias. Pg for remote wells: Sodir wildcat layer with a distance-to-facility filter (more than 50 km: about 0.35 in the Norwegian Sea and Barents Sea, 2013-2026).

## 4. Evidence to collect first

Sodir FactMaps (wellbore, discovery, field reserves; the wellbore layer shows when a discovery was drilled from a producer template, which is the PLX precedent), host profile (Centuries or equivalent), allocated host and well rates (PDM), a matched fluid from NeqSim (separator GOR against the producer's measured GOR), and the company approval-point data checklist: fluid and water sampling, dynamic data to evaluate boundaries and connectivity, in-situ stress and rock-mechanical data, host capacity, compatibility and schedule.

## 5. Hand-offs

`neqsim-field-economics` (tax, NPV), `neqsim-uncertainty-quantification` (Monte Carlo, tornado), `neqsim-flow-assurance` (hydrate, scale), `neqsim-tight-reservoir-test-design` (test duration and gate design when the target is tight), `neqsim-subsea-and-wells` (well cost), `neqsim-capacity-increase-screening` (host ullage), enterprise `enterprise-prospect-risking` and `enterprise-area-development-scorecard` for chance-of-success factors and portfolio ranking.
