---
name: neqsim-produced-water-compatibility
description: "Plans, runs and reviews produced-water and formation-water compatibility studies. Use for commingling reservoir brines, gas/oil/water mixing, silica or silicate scaling, calcite or barite, CO2 degassing and pH, seawater or injection-water blends, sulfate incompatibility, mineral saturation maps and equilibrium precipitation potential."
last_verified: "2026-11-17"
---

# Produced-water compatibility studies

Use the [produced-water compatibility example](../../../examples/produced-water-compatibility/README.md)
at the selected NeqSim revision. It includes a case schema, CLI, local MCP server, a three-phase
process example and a water-assay station example. Use the existing program to run the
study and preserve its input and unrounded outputs.

## Establish the basis

- Identify the decision, water sources, equipment or location, operating cases, and acceptance criteria.
  Keep formation, seawater, injection and recycled waters individually traceable. Label every
  assumed input. When the user asks for a demonstration, proceed with a labelled synthetic case.
- Record the sample ID, temperature, absolute pressure, pH basis, alkalinity basis, density, major ions,
  dissolved silica, filtration and preservation where available. Record measured DIC when available as
  an independent check on the example's pH/alkalinity speciation. Report other relevant species the
  example cannot represent, including Al, Fe, sulfide, organic acids and inhibitors.
- Convert assays to the schema's solvent-water mass basis. Convert mg/L of solution using kg of
  solvent water per litre of that brine, calculated from solution density and solvent-water mass
  fraction. Convert elemental Si to SiO₂ using the molar-mass ratio. Preserve the original assay
  and conversion assumptions. Record detection limits or bounds for censored values and distinguish
  them from measured zeros. Resolve ionic imbalance with the assay owner or document a sensitivity.
- When three-phase PVT is needed, record rates, reference conditions, oil characterization, gas
  composition and actual pressure/temperature stations. Define blend fractions on incoming solvent
  water mass, using synchronized rates where available. The example's six-component hydrocarbon
  surrogate and prescribed stations bound what its flash can represent.

## Select and run the model

| Available basis | Workflow |
|-----------------|----------|
| Gas, oil and brine feed inventories with process conditions | Use the NeqSim/PHREEQC example in process mode. Verify phase identity and component closure. Pass gas CO₂ fugacity and aqueous solvent-water retention from the flash. |
| Assays and a station temperature and pressure | Use station mode. Run closed aqueous DIC. Where measured or independently calculated CO₂ fugacity exists, also run fixed station CO₂. Document whether retained water is supplied or assumed to be 100%. |
| Speciated H₄SiO₄, water activity and a matching dissolution constant are already available | Use `SilicaScaleSaturation` for amorphous silica, chalcedony or quartz. Its reference equilibrium constants apply at 1 atm and 25–100 °C. Use a condition-matched constant at other pressures. |
| Missing conditions, unsupported assay chemistry or absent executable backend | Record the precise gap and its effect. Use a qualified backend if available, or report the calculable scenarios and leave the rest unresolved. |

With the supplied CLI, use the exact case schema and the pinned Pitzer database. With the example's
local MCP server, read `produced_water_case_schema` first and use `screen_produced_water` for
station summaries and selected blends. Use the CLI to save the complete matrix. Do not infer CO₂
fugacity from total pressure, directly substitute gas partial pressure for fugacity, or blend pH.

1. Include both end members of every pair, intermediate ratios, actual operating blends and
   relevant low/high water-rate cases. Cover stations before and after cooling, pressure reduction,
   mixing and reinjection as relevant. Refine the ratio grid around a peak or an SI sign change
   before reporting a critical blend window. For uncertain CO₂ or assay concentrations, keep each
   sensitivity traceable as its own run and show the range before interpreting the result.
2. Calculate pre-precipitation SI for amorphous silica, quartz, chalcedony, supported silicates,
   carbonate and sulfate minerals. Report the mineral identifier and log base. Use the local
   SiO₂(a)-only equilibrium mass potential at each station. A rate basis needs a supplied water
   mass flow. Cumulative precipitation and deposition require transport, solids history and kinetics.
3. Keep database name and digest, backend versions, water and CO₂ assumptions, and unsupported
   aqueous interactions or mineral models with the result. For design, require applicability
   evidence for the actual salinity, temperature, pressure and brine composition.

## Validate and report

- Verify pure end members, normalized blends, non-negative phase and solid amounts, and distinct
  CO₂ boundaries. Check reported electroneutrality, conservative-ion and silicon balances, closed
  DIC closure or imposed-fugacity agreement, and post-precipitation silica equilibrium. Check
  NeqSim component balance when the process flash runs. Treat failed or unavailable cases as gaps.
- Deliver a study basis, case input, complete CSV/JSON, validation summary, and mineral-by-station
  results. Plot SI against blend ratio with the SI = 0 line. For three-source operating blends,
  report all three fractions and use a ternary plot when the sampling supports one.
  Give the governing blend and station for each mineral, pH and ionic strength there, CO₂ boundary,
  and equilibrium silica mass per kg of incoming solvent water where available.
- Record the purpose of each benchmark and distinguish numerical closure, published solubility
  checks and comparison against measured blends. Use available bottle or dynamic-flow tests and
  field deposits to evaluate uncertain operating limits, inhibitor response, deposition and
  injectivity. A positive SI indicates thermodynamic supersaturation.
