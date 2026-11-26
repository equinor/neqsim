---
name: neqsim-hp-lp-interface-overpressure
version: "1.2.0"
description: "Assess overpressure when a high-pressure well, flowline or separator feeds a low-pressure system: packed-line IVO with choke collapse, blocked outlet, relief and flare capacity, PAHH/HIPPS, LOPA, SIL. Mandatory: scenario register, installed valve-Cv input table with sources, Cv sensitivity for failed-open and blocked outlet, comparison with existing wells. Use for HP/LP tie-ins or reuse of an approved study with a new source."
last_verified: "2026-11-26"
requires:
  java_packages: [neqsim.process.safety.overpressure, neqsim.process.safety.risk.sis, neqsim.process.safety.risk.fta, neqsim.process.safety.risk.eta, neqsim.process.safety.depressurization]
---

# HP source against an LP system: overpressure method

Distilled from a screening study of a gas well with SIWHP ~241 bara produced against an LP manifold rated 26.5 barg. Site data stays in the task folder; this is the reusable method.

## 1. Decide the governing question before computing

| Question | Answer from | Result in the reference case |
|---|---|---|
| Is the source steady (well deliverability) or stored (packed line)? | well IPR/VLP rate (kg/s) versus PZV capacity; stored mass = V1 x rho(P_pack) | steady 6-19 kg/s (harmless); stored gas dumped through the PV in 2-3 s (governing) |
| What is the credible source pressure? | TR3001 13.1.1 / API 521 4.4.9.2: maximum operating pressure; a non-manipulable PAHH may define it only if SR-26553 holds, else full SIWHP (SR-26550) | 137.5 barg with PAHH, 241 bara without |
| Which accumulation applies? | API 520-I 5.4.2: 10 % one PZV, 16 % several, 21 % fire; STS0131 Req-15733 frequency bands above design, test and 2 x design | LP peak 29.7 / 32.0 / 47 barg against 26.5 barg design |
| Can QRA/LOPA remove the scenario? | No: TR3001 SR-26425, SR-26540, P-002 7.4.1; no PSD credit in relief sizing (SR-26440) | IVO+CC stays |

## 2. Model recipe (lumped, NeqSim-driven)

1. Property tables with NeqSim (`SystemPrEos`, `TPflash`, `initProperties`): vapour density, Z, MW at the node temperatures. Use gamma = 1.3 (ideal-gas) in the ISA/API formulas, not the real-gas cp/cv (1.7-2.2 at dense states destroys the choking factor).
2. Three nodes: pack volume (flowline + riser), Tune/HP separator, LP gas volume (+ optional 2nd-stage separator). Valves: ISA 75.01 gas flow with `N6 = 31.6` for Cv in US units and P in bar. PZV: API 520-I critical flow with the area back-calculated from the study's relieving rate and lift pair, quadratic lift law, two-phase factor = total/vapour rate.
3. Calibrate on the approved case: pack volume from the peak LP pressure, upstream friction from the steady "pressure boundary" relief case; keep LP2/LP3-type cases as independent checks. Cross-check the PZV area against the data sheet (reference: 113 vs 115.7 cm2) and the flows against `PsvValveModel` and `ControlValveFailureRelief` (the nozzle model is ~11 % above ISA for Cv 418).
4. Sweep pack pressure (PAHH level), volume, Cv and PZV count; Monte Carlo (lognormal volume, uniform Cv/area/lift) and a tornado. Mass balance of every run must close within 0.5 %.

## 2b. Mandatory: compare with existing wells and inputs, and check blocked outlet against current inflow

Do both before the conclusions; reviewers ask for them whenever a new well or tie-in feeds a receiving separator or header that already has producers. Skipping them leaves the answer to "is the new source above what the system was approved for?" unproven.

**A. Compare against existing/previous wells and inputs to the receiving separator/header.** Tabulate for each existing well or input and for the new source: riser/inlet (arrival) pressure, flowing and shut-in wellhead pressure (design-basis SIWHP and recorded maximum), rates (MSm3/d and kg/s), choke dP, and what the PSV/relief basis was sized for (source pressure, case, relieving rate, PSV capacity; say "not documented" when the sizing case is missing). Then:

1. Compute the ratio new/historical for source pressure (approved basis, highest existing design-basis SIWHP, highest recorded value, any original design basis) and for choke dP and rate.
2. Rerun the calibrated IVO+CC model at each historical source pressure, for the existing and the new fluid, and report peak LP pressure, relief rate through the LP PSVs, PV flow and receiving-separator PSV load, each as a ratio to the approved case. Include the cases with and without the pressure-limiting barrier, because an approval that rests on a PAHH is only comparable when the barrier holds.
3. State separately whether the new source pressure exceeds anything used or observed before, by how much, and for how long (depletion year when it falls below the approved basis).
4. Check data quality: daily historian or allocation values are not SIWHP; a well that was converted from injection can show non-producer pressures; a design-basis table may be a profile rather than a measurement.

**B. Blocked outlet as a function of current inflow (not design inflow).** Compute margin = PSV capacity / gas inflow for the receiving separator and for the LP header as a function of the inflow, with cases for current rate (last 90 days), 12-month P50/P95/maximum, forecast profile, original design rate and the new source on top. State explicitly whether blocked outlet governs. Caveats to carry: lower production today reduces blocked outlet but does not reduce IVO+CC, whose relief rate follows pack pressure, stored mass and valve Cv; when the new source pressure exceeds earlier wells, failed-open or choke-collapse is usually the governing PSV case; the margins are stationary estimates, not dynamic; lowering a PSV set pressure cuts its capacity roughly in proportion and invalidates the margin, so recheck when a set pressure changes; do not take credit for trips in sizing.

Record both in `results.json` under `existing_wells_comparison` (keys: `existing_inputs`, `relief_basis`, `ratios`, `relief_effect`, `blocked_outlet` with `governs`, `statement` and `inflow_cases`, `findings`, `sources`, `blockers`; `devtools/validate_task_results.py` warns when an overpressure study lacks the block) and in two report sections: "Sammenligning med eksisterende brønner mot <separator>" / "Comparison with existing wells to <separator>" and "Blokkert utløp som dimensjonerende case" / "Blocked outlet as design case", each with a table and one figure. Read-only data sources (PDM/allocation, historian, STID datasheets and HAZOPs, design basis, earlier tasks) are listed per source with the exact blocker when unreachable; never open a new interactive sign-in to fetch them. Site-specific source locations belong in the enterprise skills (enterprise skill `enterprise-relief-device-register`, section "Receiving-system history").

## 2c. Mandatory: scenario register and valve-Cv inputs (open the relief chapter with them)

A reviewer cannot judge a failed-open study unless every number is tied to a named scenario and to the valve data it rests on. Rates depend on the installed choke Cv and the actual dP (and on stored inventory), so the Cv inputs and their effect must be visible, not buried in the model. Open the relief/scenario chapters of every report with A and B, and add C and D.

**A. Scenario register (table at the start of the relief chapter).** One row per scenario: failed-open / IVO of the riser EV or PV with and without choke collapse; choke collapse alone; blocked outlet at each receiving separator and header (e.g. HP inlet separator, LP flowline / 3rd stage); gas blow-by; PSV failure; thermal/fire; hydrate plug release; cold JT/MDMT; start-up and well kill. Columns:

| Column | Content |
|---|---|
| Study status | **Calculated** (calibrated transient model), **simple estimate** (stationary or formula), **qualitative**; flag extrapolation beyond the calibration point |
| Valve states | which valves are open, closed or failed; opening %, stroke time, collapsed or intact choke |
| Limiting mechanism | stored inventory (pack pressure, volume), source deliverability (IPR/VLP), valve Cv at the actual dP, PSV capacity |
| Result | relief rate (kg/s) and peak pressure |
| Governing | yes or no, and why |

The figure title and discussion of every relief-rate figure must name the scenario it shows (for example "failed-open of the riser EV with choke collapse, not blocked outlet").

**B. Installed-Cv input table.** Every flow-limiting element, with value, unit, source (document, revision, page: process datasheet, valve sizing calculation, vendor curve, quoted from an approved study, tuned, or ASSUMED), quality grade (A datasheet read directly, B quoted from an approved study, C back-calculated or tuned, D assumed) and where it is used in the model: subsea and topside chokes (intact and collapsed Cv, opening %, characteristic), riser/production EV (Cv, stroke time), PV and its maximum-opening limit, crossover valves, flowline/riser volumes and friction (including dummy-valve Cv), PSV set pressure, orifice and capacity, flare backpressure. Fetch real Cv read-only from the task references and then from document systems (process datasheets, valve sizing calculations, vendor curves); label unsourced values ASSUMED and list exact blockers (scanned documents without text layer, tags with no document). Check the model's valve equation against a document with known Cv and rate (reference case: 8 collapse points, mean +3.7 %). Datasheet Cv of chokes is usually multiphase; gas studies tune it (reference: +78 %): report both.

**C. Cv sensitivity for failed-open AND blocked outlet** (script, executed notebook, figure with at least 7 pt text, table, `cv_sensitivity` key):

- Failed-open (IVO+CC, transient): relief rate, time to PSV set pressure and LP peak against total choke Cv (datasheet versus tuned, one choke collapsed versus both intact, normal throttle opening), PV Cv and maximum opening, EV Cv and stroke time, a choke x PV grid, each at the PAHH and no-PAHH source pressures.
- Blocked outlet and choke collapse alone (stationary): rate = minimum of source deliverability (IPR/VLP) and valve capacity at the actual dP, with PV in series where it is open. The dP over the choke shrinks as the pressure builds, so show the Cv below which the choke limits the rate and the source cap above it.

State in the report what the relief rate depends on and what it does not. Reference result (screening case, 2026-11): the failed-open relief rate follows PV Cv and stored mass; choke Cv is irrelevant above about 750 total with one choke collapsed (the +78 % tuning changed relief by less than 1 %) but with both chokes intact relief was 15-58 % lower and followed the choke Cv; EV stroke time 1-30 s changed relief by less than 1 %; blocked outlet was capped by well deliverability (8-19 kg/s) and the choke limited only below Cv of about 25-100.

**D. Usability statement.** A short section "what the report can and cannot be used for": what is calculated versus assumed; the inputs that must be confirmed before it is a basis for a formal failed-open study (choke Cv and opening, valve stroke times, line volumes, PSV datasheets, flare backpressure); how it compares with an approved dynamic study (screening and scoping, not a replacement).

Record `scenario_register`, `valve_cv_inputs`, `cv_sensitivity` (and `usability_statement`) in `results.json`; `devtools/validate_task_results.py` warns when an overpressure study lacks them. Site-specific places to find Cv, stroke times and PSV datasheets belong in the enterprise skills (`enterprise-relief-device-register`, section "Where to find valve Cv").

## 3. Risk side

- LOPA with `LOPAResult.getSTS0131OverpressureTargetFrequency(eventBara, designBara, testBara)` (categories are checked in the order 2 x design, test, design). Initiating frequency = f_IVO x P_CC (x exposure E; design basis E = 1).
- SIL: `SafetyInstrumentedFunction.calculatePfd1oo1/1oo2/2oo3(lambdaDU, tau)` have no beta; add `beta*lambda*tau/2` (GL070: PT 6 %, valves/solenoids 10 %, logic 5 %). GL070 Table A.2.1 lambda-DU per 1E6 h: PT 0.5, HIPPS valve 1.5, XV 1.9, pilot 0.6, PSV 1.1; test 12 months.
- Fault tree: `FaultTreeNode.basic/voting/withCCF`; in Python `and` and `or` are keywords, so use `getattr(FT, "and_")` and `getattr(FT, "or_")`. `EventTreeAnalyzer` needs `(name, initiatingFrequency)`. `FaultTreeAnalyzer.minimalCutSets(root, maxCardinality)` returns the cut sets.
- `PsvValveModel.massFlowKgPerS` returns 0 until `update(P)` has latched the valve open; for ODE right-hand sides use the stateless full-open and lift-fraction methods. Supply finite gas properties at every lift and a finite lift command. Invalid input raises `IllegalArgumentException`.

## 4. Read the conclusions the right way

- A numeric frequency target alone rarely demands HIPPS (reference case: required PFD >= 0.27 even at E = 1, i.e. at most SIL1). The need for a pressure-limiting barrier comes from the deterministic rules (source pressure, accumulation, no PSD credit in sizing).
- HIPPS instead of PZV capacity needs a deviation from P-002 7.4.3 and a LOPA-based SIL (GL070 7.6.1). Cheaper compliant levers: lower the PAHH setpoint, document its SIL, add PZV capacity, or a Cv limiter.
- Always report P10/P50/P90 for the peak, the pack pressure that meets 110/116/121 %, and the data gaps (line volume, pipe class, flare capacity, MDMT for cold JT).

## 5. Gotchas

- Rich (gas-condensate) pack fluid: use homogeneous mixture density for the inventory; lean separator gas (study fluid) understates stored mass (+60 % in the reference case, +2.4 bar on the peak).
- Cold shut-in isenthalpic expansion can pass the LP MDMT (-46 C reference); the approved study only reported the warm case.
- Percent-format task scripts convert with `devtools/py_to_notebook.py`; for runner execution build the notebooks with nbformat and submit them to `neqsim_runner.AgentBridge`.
- A failed-open model calibrated at one Cv (reference: choke 2134, PV 418) is only indicative far from it; mark runs with a total choke Cv well below the calibration point (both chokes intact, normal throttle opening) as extrapolation. A linear EV opening ramp and a linear intact-choke characteristic are assumptions, not data.
- Reference-case evidence for 2b (screening, one gas well, 2026-11): the new source was 1.74 times the approved pack pressure (+102 bar), 1.43 times the highest existing design-basis SIWHP; with the PAHH in place the pack pressure was unchanged and rich gas added 14 % relief; without it LP-PSV relief was 1.65 times the approved case. Blocked outlet had margin 3.7-9.5 at current inflow and became governing only above the PSV capacity (about 18 MSm3/d) or if the receiving-separator PSV set pressure were lowered. An existing-wells table built from design-basis SIWHP, daily allocation data and a recorded maximum gave three different "highest existing" values; report all three.

References: TR3001 v4.02, STS0131, NORSOK S-001/P-002, API STD 521/520, API RP 14C App. E, API STD 17O, Guideline 070 rev 3, GL0139. Related: `neqsim-process-safety` (Method 9, scenario register and Cv inputs), `neqsim-relief-flare-network`, `neqsim-task-workflow` (safety and overpressure tasks), `neqsim-professional-reporting` (evidence matrix).
