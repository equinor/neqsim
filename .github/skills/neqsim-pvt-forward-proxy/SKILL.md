---
name: neqsim-pvt-forward-proxy
version: "0.1.0"
description: "Fits ONE forward (no-recycle) separator/scrubber P/T set so a proxy surface process reproduces the standard oil and gas rates of a detailed NeqSim process with recycle over a 30-100 fluid ensemble, for PVT tables in Eclipse 300, PVTsim, FluidMagic, OLGA and black-oil tools. USE WHEN: recycle/reflux must become a forward separator train, separator-test or SEPCOND conditions need calibrating, or neqsim pvt-proxy is requested."
last_verified: "2026-11-08"
requires:
  python_packages: [pyyaml, jpype1, numpy, scipy, matplotlib]
---

# PVT forward-proxy surface process

Use the executable `pvt_proxy` workflow (`neqsim pvt-proxy`, or `python -m pvt_proxy`
from `devtools/`). It reuses NeqSim process equipment (`Stream`, `Heater`, `Separator`,
`Mixer`, `Compressor`) and closes reference loops with `ProcessSystem.makeRecycles`.
The orchestrator is [@pvt-forward-proxy](../../agents/pvt-forward-proxy.agent.md); the
guide is [docs/pvtsimulation/pvt_forward_proxy.md](../../../docs/pvtsimulation/pvt_forward_proxy.md).

Purpose boundary: the proxy defines the surface separation for **PVT-table generation**.
It is not a molar-production allocation method and does not replace the Eclipse to
FluidMagic to OLGA to NeqSim production workflow. Say so in every deliverable.

## Entry points

```bash
neqsim pvt-proxy example recompression --out proxy.yaml   # or: synthetic
neqsim pvt-proxy run proxy.yaml --out <task>/step2_analysis/pvt_proxy --strict
neqsim pvt-proxy evaluate <out>/proxy_definition.yaml proxy.yaml --fluids new_fluids.json --out eval.csv
neqsim pvt-proxy export <out>/proxy_definition.yaml --target e300 --target pvtsim --out exports
```

`--strict` exits 2 when the fit status is `review_required`. `--resume` restarts from
`checkpoint.json` only when the configuration hash matches. Reference results are cached
in `reference_cache.json` by reference, feed, standard conditions, template and composition.

## Skill 1 - build_neqsim_process (reference and proxy)

- `reference.type: recompression`: decreasing-pressure `stages`, plus gas-handling steps
  (`gas_sources`, `scrubber_pressure_bara` (alias `discharge_pressure_bara`),
  `cooler_temperature_C`, `liquid_return_stage`). A compressor is inserted only when the
  scrubber pressure is above every source pressure; otherwise the step is a suction cooler and
  scrubber. Optional `gas_return: {to_stage, rate_Sm3_h}` returns a fixed gas rate (gas lift).
  Keep `recycle_tolerance` at 1e-6. The default 1e-2 tear tolerance of `makeRecycles()` leaves
  0.1-0.3 % mass imbalance and biases the stock-tank oil. A reference above
  `max_mass_balance_error` (1e-4) is failed.
- Several scrubber liquids returning to the same separator need one tear each.
  `AutoRecycleBuilder` before 2026-11-08 tore only the first (2-23 % mass imbalance); use a NeqSim
  build with the fix.
- Gas lift, fuel gas and anti-surge recycles do not change the products when every vessel is at
  fixed P and T: the recirculated equilibrium gas forms a linear internal loop. Take the separator
  temperature from the solved plant, because that is where gas-lift heating acts.
- `reference.type: python` with `callable: "module:function"` (module next to the config)
  for an existing NeqSim model. It receives `(fluid, feed, standard_conditions, JClass)`
  and returns `oil_Sm3_h` and `gas_Sm3_h` on the same product definitions.
- `reference.type: forward` is a known forward train, for recovery tests only.
- Proxy: forward `stages` plus an optional `scrubber` (`gas_sources`,
  `liquid_destination` = a later stage or `stock_tank`). A backward destination is a
  recycle and is rejected. Each P/T is a fixed number or `{value, lower, upper}`.
- Product definitions are identical for reference and proxy: oil = stock-tank liquid
  (EOS density) at standard conditions; gas = all gas leaving, including stock-tank gas,
  as ideal-gas Sm3; same feed molar rate. Water is excluded from the ensemble.

## Skill 2 - generate_fluid_ensemble

- One `fluids.template` for all fluids: `components` (library names, TBP pseudo
  components with `mw_kg_mol` and `density_g_cm3`), `e300`, or `factory: "module:function"`
  returning a characterized NeqSim fluid. Use the factory to reuse a tuned project EOS (custom Tc,
  Pc, acentric factor, volume shift and kij) unchanged; the module must sit next to the config.
  This keeps the component mapping stable across all simulations.
- `base_fluids` are named compositions (dict by component name, or a list in template order;
  sum to 1 within 1e-3). Methods: `explicit`,
  `mixing` (`endmembers`, `n` or `gas_fractions`), `variation` (`base`, `n`,
  `relative_range`, `seed`), `depletion` (CVD: `base`, `reservoir_temperature_C`,
  decreasing `pressures_bara`, optional `liquid_recombination`).
- Cover the field-life range from the heaviest oil to the lightest gas with at least 30
  fluids. Steps rejected during generation are kept in `fluids.json` with a reason.
- `fluids.weights` sets operational importance per fluid id. Report the weights used.

## Skill 3 - calibrate_forward_proxy

- One common parameter vector for all fluids. Never fit per fluid.
- Default objective: weighted normalized least squares on oil and gas rates, with oil
  weight 2, gas weight 1, GOR weight 0 (diagnostic), soft-L1 loss, oil floor
  `eps_oil_fraction` 0.05 of the median oil, and fluids above GOR 15 000 weighted 0.25.
  Do not optimize GOR alone: super-dry gas would dominate and bias oil at moderate GOR.
- Default optimizer `de+lsq`. Keep `fd_step` at about 1e-3 (unit box): flash noise is
  about 1e-6 in the normalized residuals, and a 1e-4 step stalls the least-squares
  refinement short of the optimum.
- Invalid pressure sequences are rejected without simulation. Failed proxy runs are
  penalized (`failed_penalty`) and reported, never dropped.

## Skill 4 - validate_proxy

Read `validation_report.md`, `validation_per_fluid.csv` and `validation_summary.json`:

- Report oil, gas and GOR errors separately (MAPE, bias, P50/P90, max), by fluid class
  and for the worst fluids. Report failed cases with their reasons.
- **Dominance:** a flagged fluid or dry-gas group means the weighting must be revisited.
- **Identifiability:** rates usually determine only some stage conditions. Variables
  marked "poorly" (resolution with the others re-fitted above a quarter of the range)
  must be reported with their resolution. Fix them from the design basis, or set a small
  `objective.prior_weight` (for example 1e-5). Do not present them as measured values.
- Variables "at bound": the optimum lies on the bound. Widen the bound only when the
  value is physically realistic. A cold scrubber bound usually means the forward proxy
  is compensating for condensate that the reference recycles back into the oil.
- `pass` / `review_required` follows `acceptance` (oil/gas MAPE, oil P90, failed count,
  dominance). Use `evaluate` for new fluids: outside the stored calibration range it
  warns about extrapolation.

## Skill 5 - export_pvt_configuration

`proxy_definition.yaml/.json` is the single source. It holds the stages, scrubber,
routing, flow sequence with liquid/vapour destinations, standard conditions, units, the
exact objective and weights, optimizer settings, fit metrics, calibration range, status,
fit timestamp, software version and git commit. Exporters: `neqsim` (fixed proxy block),
`e300` (SEPCOND-style table; check the item order against the simulator manual),
`pvtsim`/`fluidmagic` (separator-test JSON and stage CSV), `blackoil`/`olga` (stage CSV
and conditions note). Add a target by registering a function in `pvt_proxy.export.EXPORTERS`.

## Verified behaviour (example configurations)

- `synthetic_three_stage.yaml`: with HP pressure and LP temperature free from wrong
  starts, the fit recovers 60.000 bara and 35.000 degC. Freeing MP or LP pressure as well
  produces a flat valley, and the identifiability table flags it.
- `recompression_reference.yaml`: 41 fluids (black oil to super-dry gas, CVD), two
  recompression loops. The fit gives oil MAPE 0.05 % and gas 0.02 % in about 90 s,
  with the scrubber temperature at its lower bound.
- A producing installation (plant model with three scrubbers returning condensate to one separator, gas lift;
  46 fluids, GOR 125-6 960): separator-only proxies give 1.4-1.6 % oil MAPE (up to 6 %). One
  separator plus scrubber gives 0.06 % with all four conditions identifiable, so compare a scrubber
  topology first when the plant returns condensate. Hold-out fluids beyond the training GOR lost up
  to 5 % oil, so include the leanest late-life fluids. Warmer suction coolers (+8 C) biased oil by
  +1.8 %, so refit per cooling regime.
- Run candidates one after another (or in separate out folders) in OneDrive task folders; the sync
  client can lock freshly copied files and make a run exit early.

## Related

- `neqsim-forward-separator-train` (community skill) runs one fluid through a fixed
  forward train, or fits stage P/T to reservoir-simulator rates.
- [neqsim-process-modeling](../neqsim-process-modeling/SKILL.md) for building a detailed
  reference process to plug in via `reference.type: python`.
- [neqsim-optimization-and-doe](../neqsim-optimization-and-doe/SKILL.md) for broader
  optimizer selection.
