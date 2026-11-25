---
name: neqsim-hp-lp-interface-overpressure
version: "1.0.0"
description: "Assess overpressure when a high-pressure well, flowline or separator feeds a low-pressure system. Covers shut-in and packed-line pressures, inadvertent valve opening with choke collapse (IVO+CC), relief and flare capacity, PAHH and HIPPS credit, LOPA, SIL verification and mitigation ranking. Use for HP/LP tie-ins, specification breaks, trip settings or reuse of an approved study with a new fluid or source pressure."
last_verified: "2026-11-24"
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

## 3. Risk side

- LOPA with `LOPAResult.getSTS0131OverpressureTargetFrequency(eventBara, designBara, testBara)` (categories are checked in the order 2 x design, test, design). Initiating frequency = f_IVO x P_CC (x exposure E; design basis E = 1).
- SIL: `SafetyInstrumentedFunction.calculatePfd1oo1/1oo2/2oo3(lambdaDU, tau)` have no beta; add `beta*lambda*tau/2` (GL070: PT 6 %, valves/solenoids 10 %, logic 5 %). GL070 Table A.2.1 lambda-DU per 1E6 h: PT 0.5, HIPPS valve 1.5, XV 1.9, pilot 0.6, PSV 1.1; test 12 months.
- Fault tree: `FaultTreeNode.basic/voting/withCCF`; in Python `and` and `or` are keywords, so use `getattr(FT, "and_")` and `getattr(FT, "or_")`. `EventTreeAnalyzer` needs `(name, initiatingFrequency)`. `FaultTreeAnalyzer.minimalCutSets(root, maxCardinality)` returns the cut sets.
- `PsvValveModel.massFlowKgPerS` returns 0 until `update(P)` has latched the valve open; for ODE right-hand sides use the stateless full-open and lift-fraction methods.

## 4. Read the conclusions the right way

- A numeric frequency target alone rarely demands HIPPS (reference case: required PFD >= 0.27 even at E = 1, i.e. at most SIL1). The need for a pressure-limiting barrier comes from the deterministic rules (source pressure, accumulation, no PSD credit in sizing).
- HIPPS instead of PZV capacity needs a deviation from P-002 7.4.3 and a LOPA-based SIL (GL070 7.6.1). Cheaper compliant levers: lower the PAHH setpoint, document its SIL, add PZV capacity, or a Cv limiter.
- Always report P10/P50/P90 for the peak, the pack pressure that meets 110/116/121 %, and the data gaps (line volume, pipe class, flare capacity, MDMT for cold JT).

## 5. Gotchas

- Rich (gas-condensate) pack fluid: use homogeneous mixture density for the inventory; lean separator gas (study fluid) understates stored mass (+60 % in the reference case, +2.4 bar on the peak).
- Cold shut-in isenthalpic expansion can pass the LP MDMT (-46 C reference); the approved study only reported the warm case.
- Percent-format task scripts convert with `devtools/py_to_notebook.py`; for runner execution build the notebooks with nbformat and submit them to `neqsim_runner.AgentBridge`.

References: TR3001 v4.02, STS0131, NORSOK S-001/P-002, API STD 521/520, API RP 14C App. E, API STD 17O, Guideline 070 rev 3, GL0139.
