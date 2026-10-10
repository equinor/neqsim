---
name: neqsim-exploration-strategy-selection
description: "Screening workflow for choosing how to explore a near-field target next to a producing host: dedicated exploration well versus an exploration sidetrack, pilot or extended-reach lateral drilled from a planned production well (PLX), or a late PLX in a later producer. Monte Carlo of discovery, volume, fluid, development value, timing and cost with exploration cost paid in every outcome, EMV, outcome distribution, value of information, break-even chance of success, crossovers and an approval-point data checklist. USE WHEN: a task asks which exploration strategy to prefer for a satellite or cellar target, whether to piggy-back on a planned producer, or what to put in an approval-point (APbo/DG0) package for an exploration decision."
---

# Exploration strategy selection (dedicated well vs PLX)

Second implementation with two zones, host-life gate and flow assurance: the Tyrihans cellar task (`tyr_model.py`, `07_strategies.py`, `13_crossovers.py`, `14_build_results.py`).
Third implementation, infrastructure-reuse variant with IOGR, tie-back routes and exploration in one Monte Carlo: the Morvin revitalization task (`mmodel.py`, `20_options.py`, `21_sens.py`).

Reference implementation: `task_solve/2026-10-10_limbus_apbo_exploration_strategy_dedicated_well_vs_plx_from/step2_analysis/scripts` (`limbus_model.py` development economics and volumes, `07_strategies.py` strategies, `08_sensitivity.py` tornado and maps, `13_crossovers.py`). Copy the pattern, not the numbers: every prospect input there is an assumption.

## 1. Strategies to compare

| Id | Strategy | What differs |
|----|----------|--------------|
| S1 | Dedicated exploration well | Full rig spread and well cost in every outcome, earlier data, long lag from discovery to first oil (appraisal, concept, sanction) |
| S2 | PLX from the planned producer | Cost is a sidetrack or lateral (days, not months) and shared with the producer, data later (tied to the producer schedule), shorter lag because the PLX can be completed as a producer, but a harm term when the planned producer is delayed or lost |
| S2h | S2 with S1 as fall-back | Add a reach probability (trajectory, step-out, torque and drag, ECD); if the PLX cannot reach, the dedicated well is drilled |
| S3 | PLX in a second, later producer | Later data, equal cost, optional gain in chance of success from the earlier wells |

**S2h only works when the dedicated well it falls back to has a positive EMV.** At Tyrihans (EMV of the dedicated well -13 MUSD) the fall-back made S2h worse than S2; report S2 as "pilot only if reachable, else do nothing" and S2h as the reference, and do not recommend a pre-agreed fall-back until EMV(S1) > 0.

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

9. **Several stacked zones** (for example Tilje and Lower Åre under a producing field): sample one shared trap and charge factor plus a zone-specific reservoir factor, so Pg(at least one zone) is below the sum of the zone chances and the discoveries are correlated; report Pg per zone and combined.
10. **Benchmark Pg against the regional hit rate** of the unit from Sodir wells that penetrated it (`devtools/sodir_wellbore_stats.py`, Beta posterior), and anchor pilot (observation) hole and sidetrack durations on the Sodir pilot holes in the same field.
11. **Host-life gate**: the value of a late discovery is set by Centuries host profile end year, a subsea compression decision and the licence expiry year (Centuries field metadata carries `licenceExpiryYear` and equity). Report EMV with and without the life extension and treat the licence extension as a gate condition.
12. **Break-even of the pilot itself** (extension days, cost, producer loss probability at which EMV(S2) = 0) is the gate criterion for the desk phase; the crossover against the dedicated well is a different number.
13. Monte Carlo EMV of a strategy that is positive in only 5-10 % of worlds moves by about 1-2 MUSD between 1 000 and 8 000 worlds: quote the largest run and call crossovers indicative.
14. **Infrastructure-reuse screen (ageing host or tail field, Morvin task)**: before crediting an old template or line with value for new volumes, compare the straight-line distance of each candidate to the old infrastructure and to the host platform (Sodir facility layer 6000 for coordinates). A candidate that is no closer to the old template than to the host gains nothing from routing through it. Evaluate the routes (via old line, direct, via a neighbour's line) on the same worlds, add a keep-alive cost K for the old line (preservation about 0.25 K before first flow), and report the K at which the old route breaks even. At Morvin that K was about 7 MNOK/yr against an assumed 80-250.

## 3. Traps

- `np.percentile(x, 10)` is the **P90 (low)** value; keep the P90/P50/P10 convention in tables and the p10 < p50 < p90 ordering in `results.json`.
- Calibrating GRV so the P50 volume lands between two analogues is circular if the same analogues are then used to "validate" the volume. Label it an assumption and put the ranges in `data_gaps`.
- A peer-fleet benchmark of a prior P10 with n about 20 wells is a poorly defined percentile; use a bootstrap interval of the sample percentile, not a point comparison.
- Lag asymmetry (long after a dedicated well, short after a PLX) is a modelling assumption that carries part of the PLX advantage; show it as a crossover (`p_lag_add`, `d_lag_add`) rather than burying it.
- Cumulative production to date is a lower bound for EUR; do not call a prior that is above the cumulative of the median host well a failure without saying so.
- A PDM rate benchmark needs one gas-to-oil-equivalent convention for the host plan and the allocated data; Sodir uses 1000 Sm3 gas = 1 Sm3 OE, Centuries rich gas used about 1.15e-3.
- Hydrate with SRK-CPA needs `setHydrateCheck(True)` before `hydrateFormationTemperature()` (see `neqsim-flow-assurance`); the failure "Can not return phase number 4" means it was forgotten.
- A stand-alone prospect far from infrastructure has no host to curtail it: anchor CAPEX on Sodir cumulative field investment (`prfInvestmentsMillNOK`, nominal NOK) against peak annual OE rate of stand-alone fields, report the minimum economic volume per infrastructure share borne by the discovery (1.0 stand-alone, 0.4 area hub) instead of a named host, and expect the Sodir sample of far discoveries with a resource estimate to be too small (n = 1) for a size distribution: use the whole frontier distribution and state the bias. Pg for remote wells: Sodir wildcat layer with a distance-to-facility filter (more than 50 km: about 0.35 in the Norwegian Sea and Barents Sea, 2013-2026).

## 3b. Preparing the development design before the well (fast-track VPbo)

When the question is what to prepare so a discovery can go straight to VPbo (not how to explore):

- Value a **design x outcome-scenario matrix** (base, small, large, compartmentalised, out of reach of an extended-reach well, gas cap) post-tax, then compare a design **fixed before the well** with the design **chosen after the result**. Report regret, EVPI among the fixed designs, break-even volume and Brent per design, and the recovery below which an injector pays (injector only if recovery without it is below the supported-recovery ratio). The adaptive minus best-fixed value is the value of preparing the family.
- The brief concept (for example producer plus single-slot injector) is one option, not the default: a bare-bone producer-only case is often the cheapest to break even. Always include a stand-alone fallback with a delay penalty and a spare-slot / pre-installed-hardware option with its break-even development probability.
- **Value of speed depends on host ullage.** With a liquid-limited host, earlier first oil displaces base barrels and can be negative; debottlenecking the host liquid or water handling turns it positive. Model displacement of base production (with a deferral credit) before quoting a per-month value of acceleration.
- Schedule risk: Monte Carlo from well result to first oil with and without a prepared path; report the latest result date that still meets the target at P50 and the probability at the latest date.
- The exploration EMV is Pd times the adaptive success value minus the well in every outcome (after the tax refund); give the break-even Pd both with and without refund.

## 4. Evidence to collect first

Sodir FactMaps (wellbore, discovery, field reserves; the wellbore layer shows when a discovery was drilled from a producer template, which is the PLX precedent), host profile (Centuries or equivalent), allocated host and well rates (PDM), a matched fluid from NeqSim (separator GOR against the producer's measured GOR), and the company approval-point data checklist: fluid and water sampling, dynamic data to evaluate boundaries and connectivity, in-situ stress and rock-mechanical data, host capacity, compatibility and schedule.

## 5. Hand-offs

`neqsim-field-economics` (tax, NPV), `neqsim-uncertainty-quantification` (Monte Carlo, tornado), `neqsim-flow-assurance` (hydrate, scale), `neqsim-tight-reservoir-test-design` (test duration and gate design when the target is tight), `neqsim-subsea-and-wells` (well cost), `neqsim-capacity-increase-screening` (host ullage), enterprise `enterprise-prospect-risking` and `enterprise-area-development-scorecard` for chance-of-success factors and portfolio ranking.
