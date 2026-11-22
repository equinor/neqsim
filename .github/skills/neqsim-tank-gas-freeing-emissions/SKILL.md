---
name: neqsim-tank-gas-freeing-emissions
version: "1.0.0"
description: "Vent-gas composition (CH4, nmVOC, ethane, inert gas) and released mass when an inerted crude storage tank is gas freed by inert-gas purge to vent. USE WHEN: asked for mol% CH4/nmVOC or kg released during tank gas freeing, purging or inerting of cargo tanks on an FSU, FPSO or terminal, or when request dates must be matched to the real purge window in historian data."
last_verified: "2026-11-20"
requires:
  java_packages:
    - neqsim.thermo.system
    - neqsim.thermodynamicoperations
---

# NeqSim Tank Gas Freeing: vent-gas composition and released CH4 / nmVOC

Worked example: Njord B (Njord Bravo FSU) cargo tanks, task RITM4367319. Reference code is in `reference/` (`gasfree_model.py`: tables, constants, IG composition;
`gasfree_event_model.py`: the three-phase event model; `build_tables_example.py`: how the NeqSim saturation tables are built). Site data stay in the task folder.

## When to use

- "mol% methane and nmVOC in the gas when tank X is gas freed" (Miljodirektoratet / Sokkeldirektoratet reporting, Offshore Norge guide).
- Mass of CH4 and nmVOC released by an inert-gas purge that bypasses the VOC recovery unit.
- Matching a list of dates in a request to the real vent window.

## Definitions

nmVOC = hydrocarbons except methane (C2+, ethane included), as in the Norwegian design documents; always also report ethane and C3+ separately so the guide's
definition can be applied. mol% = vol% at about 1 bara. Report wet basis and say so.

## Physics (what the numbers depend on)

1. **Offload** (crude out, inert gas in): the ullage is in contact with a large crude surface at storage temperature, so its HC fraction approaches the crude's saturation
   (about TVP/P times a mass-transfer factor). This, not the purge, fixes the hydrocarbon inventory (often 40-60 mol% HC).
2. **Standing** (empty, closed): exchange with residual crude/clingage at the measured gas temperature; changes the inventory little.
3. **Purge** (inert gas to vent): N mixed stages in series, outlet fraction `y0 * Gamma(N, N*theta)/Gamma(N)`, theta = Q t / N_gas. Released mass = inventory + heel evaporation,
   independent of the inert-gas flow once several tank volumes have passed; the flow-weighted mean mol% scales with 1/(volumes supplied).
4. Methane and ethane are dissolved light ends of the stored crude (K about 100 at 1 bara), so the CH4 share of the HC part (10-40 mol%) is a property of the crude's weathering.

## Method

1. Fluid: PR78/SRK crude with pseudo-components (see `neqsim-api-patterns`, `neqsim-thermodynamic-initialization`); check TVP/RVP against the documented crude properties.
2. Tables (`build_tables_example.py`): bubble point of the crude liquid at the storage-temperature flash pressure P_w (weathering state) and at the gas temperature; effective
   saturation pressures `Psat_eff_i = y_i P_b / x_i` (Raoult form for the exchange with the liquid). Verify against a NeqSim TP flash of liquid + N2 (deviation of partial pressures < 3 percent).
3. Event model: species balance for O2, CO2, N2, H2O and all HC components; offload with growing ullage and IG make-up = outflow minus evaporation; standing; purge for the
   measured IG run window. Use explicit Euler with dt <= 0.004 tau (error < 0.2 percent for 1-3 stages).
4. Monte Carlo on crude weathering, volatility, mass-transfer conductances, residual crude volume, IG flow, mixing; fix measured quantities. Rank inputs by Spearman correlation **within** event groups (pooled correlations are confounded by tank volume).
5. Report per event: flow-weighted mean over the purge, first-3-hour mean, peak, kg released, residual HC left in the tank at the end of the IG run (released later), with P10/P50/P90.

## Historian signature of a purge window (PI/OTS, 1 h averages)

| Phase | Signals |
|---|---|
| Offload | tank level 80-90 percent to about 0.2 percent in 10-14 h; IGG running (EV open) at high manifold pressure (about 1000 mmWG); crude outflow about 7500 m3/h (sum of level x volume) = IG demand |
| Standing | level about 0.2 percent; tank pressure 60-110 mbarg (closed); tank temperature falls 10-15 C within a day |
| Purge to vent | IGG running again, **tank pressure falls to 3-40 mbarg** (open to vent line) while level is about 0.2 percent; IG O2 stays at the set point (3 percent) = inert gas, not air |
| Air ventilation | IG O2 at about 20.9 percent while the IGG runs; tank pressure 2-4 mbarg |

The dates in a request are often the **emptying** dates; the purge can be hours to weeks later. Cells of a divided tank purged together show pressures falling together (open bulkhead valve): treat as one gas volume and allocate by volume.

## Validation tests (all must pass; see `32_validation.py` in the task)

- Species and heel mass balance closure < 1e-6; HC inventory hand calculation; offload dilution analytic (no evaporation) y_end = y_u0 V_u0/V.
- Purge vs exact Erlang solution for 1, 3, 5 stages (error < 1 percent of the start value); Raoult-form partial pressures vs independent NeqSim flash.
- Crude TVP vs documented values; IGG composition vs data sheet (3 percent O2: CO2 about 14 percent).
- Cross-check against reported emission estimates (Emisoft monthly Diffuse emissions: subtract gas leaks, 50/50 CH4/nmVOC per guideline 44, and a baseline) and run an earlier event blind.

## Pitfalls

- Fixing an initial HC fraction (for example 10 vol%) instead of deriving it from the offload physics under-predicts the mass by a factor 3-5 for volatile crudes.
- The IG flow is rarely metered: report mass (flow independent) and the mean mol% together with the IG volume it belongs to.
- Hydrocarbons left in the tank at the end of the IG run are emitted when the tank is later vented or opened; list them separately.
- The methane content of the stored crude from a process model is unmeasured; ask for a crude GC (C1-C6) and a CH4/C2+ reading in the first purge hour.
- Do not mix up TVP (bubble point) and RVP (ASTM D323); compare TVP with TVP.

## Related

`neqsim-flow-assurance`, `neqsim-plant-data`, `neqsim-depressurization-mdmt` (blowdown, not purge), `neqsim-stid-retriever`, `neqsim-release-source-terms`.
