---
name: neqsim-capacity-increase-screening
description: "Ideation and screening of capacity-increase ideas on an existing host across wells, subsea flow system and topside - AICD/ICD/DAR completions, subsea separation or boosting, separator pressure, scrubber internals, compressor and pump modifications. USE WHEN: asked to accelerate or increase production, find what to debottleneck first, or rank ideas on scope, cost, emissions, HSE and value with a maturation pipeline and host ullage for future tie-ins."
last_verified: "2026-10-08"
---

# Capacity Increase Screening (wells, subsea, topside)

Use this skill when the question is "which ideas lift or accelerate production on this host, and which should be matured?" It turns a calibrated host model into a ranked idea register. It does not replace the equipment-specific skills it calls.

## Workflow

1. **Baseline.** Build or reuse the host model on a plan profile (oil, gas, water per year). Close mass balance and compare with plant data before any lever is tested (`neqsim-process-modeling`, `neqsim-model-calibration-and-data-reconciliation`).
2. **Constraint map.** Register the limit of every unit on the oil, gas, water and power paths and label its basis (vendor design case, code, operator monitoring limit, demonstrated peak). Run the year grid and record utilisation per constraint, not only the maximum (`neqsim-capacity-and-utilization-analysis`; `ProcessModelDebottleneckStudy`, `BottleneckTracker`).
3. **Binding sequence.** List which constraint binds in which years and what the next one is. A lever that relieves constraint A only buys time until constraint B binds.
4. **Idea long list.** Fill the lever catalogue below with the asset's own ideas first, then add missing families. Ask the asset for design margins, planned shutdowns and earlier studies before ranking.
5. **Lever runs.** Express each idea as a change to the model or its feed, rerun the year grid, and record the new binding sequence and the volume or time gained.
6. **Register and score.** One row per idea with scope, cost class, emissions, HSE, value and maturity (schema below). Test combinations, since levers rarely add.
7. **Maturation pipeline and host ullage.** Assign each idea a next stage and report the per-year ullage left on every constraint for future tie-ins (`TieInCapacityPlanner`, `HostFacility`).

## Lever catalogue

| Domain | Idea | Constraint it relieves | How to model it | Main risk |
|---|---|---|---|---|
| Wells | AICD, AICV, ICD, DAR-type autonomous or adjustable completions | Water and gas handling, liquid rate | `InflowControlCompletion.compareAtSameOil` gives the water and gas change per barrel of oil and the extra drawdown; carry the result into `WellDeliverabilityCurve` / `IntegratedProductionModel` | Benefit is reservoir-dependent and unproven until a well test; productivity penalty |
| Wells | Gas lift rate, lift-gas allocation, choke strategy | Liquid rate, gas compression | `GasLiftNetworkOptimizer`, `NetworkOptimizer` | Lift gas loads the same compressors |
| Wells | New or sidetracked wells, infill | Plateau length | Plan profile with new wells; not a debottleneck | Cost class and rig availability |
| Subsea | Boosting (multiphase pump, wet-gas compression) | Wellhead back-pressure, plateau rate | `SubseaBooster` plus flowline hydraulics (`PipeBeggsAndBrills`, `TwoFluidPipe`) | Power supply, reliability, intervention cost |
| Subsea | Bulk water or gas separation | Topside water, liquid or gas path | `SubseaSeparationStation`: split gas, liquid and water before the riser, boost liquid and reinject water, read the power | Qualification, power, controls, hydrate and slug control |
| Subsea | Flowline or riser pressure-drop reduction, slug control | Back-pressure, separator surge | Hydraulic rerun with changed diameter, routing or choking (`neqsim-flow-assurance`) | Hydrate, wax and slugging margin |
| Topside | Higher separation pressure | Gas compression suction, scrubber load | Pressure setpoint on the inlet separator; recompression power falls | Oil spec, flashing, vessel rating |
| Topside | Scrubber internals, cyclones, vane packs | Gas load factor (K) and momentum limits | New K limit on the unit; check carry-over (`SeparatorMechanicalDesign`) | Vessel pressure rating, liquid carry-over |
| Topside | Compressor re-wheel, speed, driver, anti-surge strategy | Compressor flow, power, surge | New chart or driver limit (`neqsim-rotating-equipment-design`, `neqsim-compressor-antisurge-recycle`) | Casing limits, power from the driver or grid |
| Topside | Pump upgrade (water injection, export), heater or cooler capacity | Water, liquid or temperature path | Capacity constraint on the unit | Utility demand |
| Topside | Produced-water treatment, flare, power capacity | Water discharge, flare load, electrical load | Separate screen against demonstrated peak and design case | Permit limits |
| System | Debottleneck sequencing across several of the above | The next binding constraint | `DebottleneckingAdvisor`, `ProcessModelDebottleneckRanking` | Interactions |

Name a family as **screened only** when the model rests on assumed constants rather than tests. The completion and separation classes below are screening models: the device constants and the carry-over fractions must come from vendor flow-loop or qualification data before the result is used for more than ranking.

## NeqSim classes for the well and subsea levers

- `InflowControlDevice` (`neqsim.process.equipment.reservoir`): pressure drop of an ICD (nozzle), AICD (`dP = a rho_mix^2/rho_cal (mu_cal/mu_mix)^y q^x`, calibrated on a reference point), AICV (closes on low viscosity) and DAR (density-window restrictor; the window and residual opening are inputs because the characteristic is vendor specific), and the inverse flow for a given pressure drop.
- `InflowControlCompletion`: zones with productivity index and in-situ water and gas fractions sharing one drawdown. `compareAtSameOil(bareDrawdown, maxDrawdown)` returns the extra drawdown the device needs for the same oil and the water and gas reductions at that oil; `isOilRateReached()` is false when the limit stops it. The phase split of a zone is fixed, so a coning-driven benefit is an upper bound. Use the water and gas reductions as the low/base/high lever in the host model.
- `SubseaSeparationStation` (`neqsim.process.equipment.subsea`): `ThreePhaseSeparator` with liquid and water pumps; set `setWaterRemovalEfficiency`, `setOilInWaterFraction`, `setGasCarryUnderFraction`, `setLiquidExportPressure`, `setWaterInjectionPressure`. Read `getGasOutStream()`, `getLiquidOutStream()` (to the host), `getWaterOutStream()`, `getWaterRemovedFraction()` and `getTotalPowerKW()`. Feed the liquid and gas streams to the host model in place of the well stream, and put the power in the emissions and electrical-load check.

Other stations (compression, hydrate and slug control, power from shore) are outside these classes; `SubseaBooster` covers boosting.

## Rules that keep the ranking honest

- **Model the lever, not the outcome.** An AICD or DAR lever is an assumed shift in water and gas per barrel of oil. Report low, base and high shifts and the break-even shift at which the idea stops paying; never a single point.
- **Binding metric switches.** A vessel can switch binding metric (water, then oil, then gas) when the feed changes. Fit responses per (unit, metric) and rerun after every lever.
- **Calibrate on the plant before using a window.** If an operating window includes anti-surge recycle, a calibration of K or power against total flow overstates the future. Calibrate on net flow and keep the recycle share separate.
- **Show two limit bases.** Vendor or code limit and operator monitoring limit can differ in both directions. Present utilisation on both and say which one the decision uses.
- **Annual mean hides peaks.** Run mean and p90-day rates; exceedances occur on peak days.
- **Surge and minimum-flow rows are turndown indicators**, not capacity rows.
- **Value needs a timeline.** Acceleration value is earlier barrels, not only more barrels. Use the plan profile with and without the lever and discount both (`neqsim-field-economics`).
- **Combinations are not additive.** Run the pair and the triple of the top ideas. Report the interaction. With up to about six levers, evaluate every combination on the utilisation algebra (not new model runs) and give each lever its Shapley share of the combined gain.
- **The plan profile is already capacity-capped.** Count a lever's gain only in years where the base utilisation of the relieved constraint is near 1 (for example 0.85 or more), and state where the unconstrained potential comes from. If it is missing, that is the main data gap, since it sets the value scale.
- **Scale rate-dependent case inputs.** Wash water, lift gas and similar fixed inputs in a case runner give mass imbalance at low rates; scale them with rate and check the mass balance in the first and last year.
- **Record what is ruled out and why** (for example a lever that moves a constraint that is not binding), so it is not re-proposed.
- **Do not let the model rank ideas it cannot see.** If the dominant limit is outside the model (produced water, flare, power), screen it against demonstrated peaks and label it so.
- **Match the subsea option to the binding stream.** Water separation helps only when water or liquid binds; when a gas-side cluster binds (gas scrubber K, contactor momentum, injection power), screen gas-liquid separation with local gas reinjection as the subsea alternative, with the compression power from a `Compressor` on the separated gas, and compare it with the topside package it would replace.
- **Re-run the saved valuation before reusing a task.** Rebuild `results.json` from the scripts; a copied task can hold results from an older script version (one such rerun moved an all-levers NPV by 20 %). Check that valuation scripts still run after any edit, since a line merge can hide an `elif` inside a comment.

## Idea register schema

One row per idea; keep it in the task folder and in `results.json` (`key_results.idea_register`).

| Field | Content |
|---|---|
| `id`, `domain`, `title` | Stable id, `well`/`subsea`/`topside`/`system`, one line |
| `mechanism`, `constraint_relieved` | Physical reason, the binding constraint and years |
| `uplift` | Oil, gas and liquid volume or acceleration per year, low/base/high |
| `scope` | What is installed or changed, shutdown needed (yes/no, days), interfaces |
| `cost_class` | AACE class with range; `neqsim-equipment-cost-estimation`, `ProcessCostEstimate` |
| `emissions` | Change in power, flaring and fuel as kg CO2e per Sm3 oil equivalent and absolute; `EmissionsTracker` |
| `hse` | Exposure change: lifting and shutdown hours, new hydrocarbon inventory, new rotating equipment, ESD or relief impact. Use a 1-5 rating and a one-line reason |
| `value` | NPV or value per unit cost with the discount rate, price and profile stated |
| `maturity` | Concept status, TRL, evidence in hand, evidence missing |
| `interactions` | Ideas it adds to, overlaps with, or blocks |
| `stage`, `next_step` | Pipeline stage and the one action that moves it |

Score on the five criteria (scope, cost, emissions, HSE, value) with stated weights, and show the ranking under at least two weight sets so a reader can see whether it is stable.

## Maturation pipeline

| Stage | Meaning | Exit test |
|---|---|---|
| 0 Idea | Named, no evidence | Mechanism and constraint identified |
| 1 Screened | Modelled, scored, Class 5 cost | Positive value in base case, no HSE showstopper |
| 2 Asset-validated | Asset confirms limits, shutdown windows and design margins | Local knowledge recorded as evidence |
| 3 Concept study | FEL-1 study with Class 4 estimate | Value robust to low case |
| 4 Modification project or new feature | Handed to the owner | Funding route and owner named |
| Parked / killed | Reason recorded | Revisit trigger stated |

Typical stage-2 questions for the asset: measured design margins, vendor datasheet case, planned turnaround dates, installed spare slots, power headroom, past debottlenecking scope, and well-test evidence for completion ideas.

## Host readiness for future tie-ins

After the lever runs, report for each year and constraint: remaining ullage on oil, gas, water, power and the unit that binds. Hand the result to `TieInCapacityPlanner` or the area-development workflow with the capacity basis and plan version. State which ideas must be installed before a given tie-in and which only add margin.

## Deliverables

- Constraint map and binding sequence on the year grid, with the limit basis per constraint.
- Lever results per idea and for the top combinations.
- The idea register, the ranking under two weight sets and the pipeline view.
- Host ullage table.
- Assumptions, data gaps, and a list of asset questions. Fill `results.json` `assumptions`, `data_gaps` and `agent_workflow_plan`.

## Related skills

- `neqsim-capacity-and-utilization-analysis` - constraints, snapshots, tie-in bottleneck study.
- `neqsim-integrated-production-and-lifecycle` - well deliverability, gas-lift allocation, tie-in planner, lifecycle.
- `neqsim-subsea-and-wells` - subsea equipment, well design, SURF cost.
- `neqsim-field-development` and `neqsim-field-economics` - concept comparison and NPV.
- `neqsim-equipment-cost-estimation` - AACE-class cost.
- `neqsim-optimization-and-doe` and `neqsim-agentic-process-optimization` - search and closed-loop setpoints.
- `neqsim-professional-reporting` - how the result is written up.
