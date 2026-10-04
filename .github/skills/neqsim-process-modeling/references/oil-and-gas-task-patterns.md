# Oil and gas task patterns

Select a row after defining the model basis. These are investigation/build routes,
not claims that every service or operating range is already qualified. Search the
current API, examples and MCP catalog for the exact operation; load only relevant
skills. An unavailable specialist/engine stays a documented gap. Keep generic model
construction in [model-build-contract](model-build-contract.md).

| Task | Minimum physical model and input basis | Checks and specialist route |
|---|---|---|
| Oil stabilization and separator trains | Characterized oil/gas/water; stage P/T and transfer equipment; all phase outlets, water drains and flash-gas destinations | Component/recovery balance, product volatility test basis, pressure-compatible transfers; `neqsim-platform-modeling`, `neqsim-eos-regression` |
| Gas recompression and export | Suction scrubbers, compression, intercooling, condensate returns; maps/driver data for capacity, efficiency basis for duty estimates | Suction phase, temperature, power, surge/stonewall where maps exist; `neqsim-platform-modeling`, `neqsim-compressor-antisurge-recycle` |
| TEG dehydration / MEG systems | Water-bearing gas with appropriate associating thermodynamics; absorber/regeneration and solvent/water balances; solvent purity/circulation and utility basis | Water dew point at product pressure, solvent losses, regeneration duty and limits; discover dehydration/absorber patterns through `neqsim-capability-map`, `neqsim-api-patterns` |
| Acid-gas removal | Acid-gas/solvent composition and chemistry, absorber/regenerator representation, reactions/parameters and validity evidence | Acid-gas pickup, elemental/solvent balances and duty; verify current reactive model through `neqsim-capability-map`, `neqsim-troubleshooting`; do not substitute a component splitter and claim treatment performance |
| Dew-point control / NGL recovery | Characterized heavy ends, cooling/expansion, cold separation and product routes; column/heat integration when fractionation is required | Envelope branch/phase identity, recovery, utility/power, product test basis; `neqsim-phase-envelope`, `neqsim-distillation-design`, `neqsim-heat-integration` |
| Wells, gathering and subsea tiebacks | Production boundary from supplied well/reservoir data; manifold routing, pipe geometry/elevation/roughness/thermal boundary, fluid basis | Pressure/temperature profiles, phase behavior and inhibition; `neqsim-subsea-and-wells`, `neqsim-flow-assurance`, `neqsim-pipeline-survey-processing`; do not label steady-state pipe flow as a severe-slugging prediction |
| Produced-water / injection services | Aqueous/oil/gas outlets, documented dissolved species/ions, pumping and routing; distinguish equilibrium split from separator efficiency | Water/oil/component balance, pump suction and corrosion/scale applicability; `neqsim-production-chemistry`, `neqsim-capability-map`; do not infer droplet removal or water quality from equilibrium alone |
| Capacity, turndown and load sharing | Accepted base model plus real equipment geometry/maps/Cv/utility limits, operating modes, manipulated variables and product specs | All candidate constraints and fresh replay; `neqsim-production-optimization`, `neqsim-agentic-process-optimization`, `neqsim-controllability-operability`; route changed inventories/pressure sections to safety review |
| Fuel gas, utilities and emissions | Tie fuel demand and cooling/heating loads to the process; explicit heating-value, driver efficiency and emissions basis | Energy closure and utility constraints; `neqsim-heat-integration`, `neqsim-capability-map`; distinguish fuel consumption/CO2 estimates from validated combustion kinetics or pollutant predictions |
| Startup, trips, depressuring and relief | Accepted steady state plus geometry, initial inventories, heat transfer, valve/actuator/controller and protection data | Time-step/inventory checks and credible event cases; `neqsim-dynamic-simulation`, `neqsim-depressurization-mdmt`, `neqsim-relief-flare-network`, `neqsim-water-hammer` where applicable |

## Cross-task traps

- Preserve the user's scenario meaning. A **holdback** case (restricted production
  or deferred flow) is not a fallback contingency; record its control/flow changes.
- Rich-gas heavy ends affect recovery and dew point; a methane-only surrogate is a
  different synthetic task, not a silent replacement for the supplied assay.
- Map-based compressor capacity must account for gas composition, suction state
  and curve basis. Fixed outlet pressure/efficiency cannot demonstrate load sharing.
- Seal-gas conditioning requires the actual supply route, pressure/temperature
  margins and moisture/hydrocarbon envelope. A dry-gas compressor inlet alone
  cannot establish dry-gas-seal protection; discover the exact supported tools.
- Recycle/condensate return rates can dominate apparent production improvement.
  Compare net external products, not internal circulating flow.
- Multi-area studies preserve common feed definitions, utilities and boundary
  conditions across specialist handoffs. Do not count inter-area streams twice.

## Model-building evaluation prompts

Use these as standalone model-planning exercises. Require a task-local build plan,
input gaps and physical validation plan before numerical execution:

1. Build HP/LP oil separation with gas recompression; each intercooler scrubber
   returns condensate to a matching pressure section. Include produced water.
2. Add a wet-gas TEG unit to a 50 MMSCFD feed whose standard reference conditions
   and heavy-end assay were not supplied. Determine what can be screened.
3. Compare two compression trains sharing an export header with a driver-power
   limit; one train has no map. Explain which load-sharing claims are supported.
4. Evaluate a 20 percent production holdback for a subsea tieback without changing
   reservoir-fluid characterization. Preserve net production and thermal boundaries.
5. Re-run a base case after a failed recycle scenario and show how stale state and
   omitted drains are prevented from passing validation.

Routing golden cases in `devtools/agent_eval_cases.json` check discoverability;
these exercises assess reasoning/handoffs. Neither alone proves numerical physics
or that an autonomous agent has executed a full industrial study.
