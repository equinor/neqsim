---
name: neqsim-cpa-water-boundaries
description: "Calculate and explain liquid-water, ice and hydrate onset temperatures together with Statoil CPA and mixing rule 10. USE WHEN: water dew point with CPA, vannduggpunkt med CPA, frost/ice formation, hydrate comparison, inhibitors such as MEG or methanol, or oil/gas/water multiphase fluids."
---

# CPA water, ice and hydrate boundaries

For a request such as **water dew point with CPA** or **vannduggpunkt med CPA**,
calculate and report all three boundaries by default, unless the user explicitly
requests only one. Explain the phase reference and convergence status of each.

## Choose the interface

For a new fluid defined by JSON, prefer the connected `run_flow_assurance`
capability with `analysis: "waterPhaseBoundaries"`. Read its current schema and
use `validateInput` before invocation. Supply explicit `pressure_bara`,
`temperature_C` and a wet-basis `components` map. This analysis selects Statoil
CPA and mixing rule 10 by default; the tested example also states `model: "CPA"`
explicitly. Use only schema-defined fields for characterized oil fractions.
See the [executed JSON example](../../../docs/thermodynamicoperations/cpa_water_phase_boundaries.md#agent-facing-mcp-operation).

For an existing configured, tuned or characterized Java fluid, retain that
object's full model basis and use the combined facade operation below. Do not
reconstruct it from a simpler JSON component map that omits its characterization
or tuned parameters.

## Configure the actual fluid

1. Use `SystemSrkCPAstatoil` and numeric `setMixingRule(10)`. For a new fluid,
   add every component before selecting the mixing rule. Use kelvin and absolute
   pressure in bara.
2. Retain the complete component inventory, oil characterization, tuned
   parameters and phase options of an existing configured CPA fluid. Do not
   rebuild characterized oil from component names, strip oil or inhibitors, or
   force its overall composition into a single gas phase.
3. State the moisture basis. For wet-gas ppmv, use `y_water = ppmv * 1e-6` and
   multiply normalized dry-gas fractions by `1 - y_water`. For an oil/gas/water
   fluid, retain its actual water inventory and inhibitor concentrations rather
   than replacing these with gas ppmv.
4. Include MEG, methanol and other supplied inhibitors in the fluid. State their
   concentration and whether it is defined in the aqueous phase or in the whole
   fluid. Enable `setMultiPhaseCheck(true)` when the supplied system can contain
   oil, gas and aqueous liquid. Do not add free water to obtain a hydrate result.
5. Verify finite positive pressure and finite positive water content. The
   Java facade requires an already configured Statoil-CPA/rule-10 fluid; an
   unsupported model is an input problem, not permission for a silent conversion
   that loses characterization or tuning.

## Calculate once and inspect every result

For an existing Java fluid, call
`new ThermodynamicOperations(fluid).cpaWaterPhaseBoundaryTemperatures()`.
It returns `WaterPhaseBoundaryResult` and calculates on independent copies,
preserving the supplied fluid. Inspect `getLiquidWater()`, `getIce()` and
`getHydrate()` separately. For each boundary, retain `getStatus()`,
`isConverged()`, `getTemperatureC()`, `getResidual()`, `getPhaseReference()`,
`getWaterActivity()` and `getDiagnostic()`.

Report failed or inapplicable boundaries explicitly. Do not turn `NaN`, an
unconverged iterate or a `NOT_APPLICABLE` result into a dew-point temperature.
In a multiphase input, the fixed-feed-gas liquid-water dew point may be
inapplicable; retain the reason while reporting the independently calculated
ice and hydrate boundaries.

For the MCP route, inspect top-level `status` first. For `error`, report the
error diagnostic and any retained partial data; do not assume a `data` object
exists. For `success` or `partial`, inspect the `data.liquidWater`, `data.ice`
and `data.hydrate` objects. Each contains
`status`, `converged`, `temperature_C`, `residual`, `waterActivity`,
`phaseReference` and `diagnostic`. Unavailable numerical values are JSON `null`.
A `partial` comparison is not an all-boundaries-converged result; explain every
failed or inapplicable boundary while retaining the usable results.

## Explain the three temperatures

| Boundary | Physical meaning | Interpretation |
| --- | --- | --- |
| Liquid water | Appearance of a water-rich liquid | Below freezing, this is an undercooled, metastable liquid reference |
| Ice | Equilibrium with pure ice Ih | Uses the existing fusion/heat-capacity approximation with an IAPWS-slope pressure correction; it is not the full IAPWS ice EOS |
| Hydrate | Equilibrium with a water/guest-gas clathrate | Uses the water activity and fluid-phase split of the actual inhibited or uninhibited fluid |

Treat these as separate onset calculations. The highest applicable equilibrium
temperature is a candidate first boundary during cooling; after a phase forms,
water and guest inventories change, so the other tabulated temperatures are not
a sequential cooling trajectory. Equilibrium does not predict nucleation delay
or hydrate growth rate.

Report composition, pressure, moisture/inhibitor bases, CPA model, rule 10,
NeqSim version, phase references and per-boundary diagnostics. Compare with
GERG-water only when requested and label its ISO ice/liquid reference separately;
do not replace this CPA calculation with GERG-2008 or ISO 18453.

Use the [tested setup and qualification guide](../../../docs/thermodynamicoperations/cpa_water_phase_boundaries.md).
The [gas-quality agent](../../agents/gas-quality.agent.md) and
[thermo-fluid agent](../../agents/thermo-fluid.agent.md) route these requests.
