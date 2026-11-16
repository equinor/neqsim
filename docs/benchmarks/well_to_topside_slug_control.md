---
title: "Well-to-topside slug-control replay"
description: "Synthetic fixed-boundary wellhead-choke, two-fluid riser and three-phase inlet-separator control replay."
---

# Well-to-topside slug-control replay

This public synthetic replay covers the NeqSim `#5036` wellhead choke, 5.4 km
flowline/riser and three-phase inlet-separator case. It compares a fixed 58%
wellhead-choke opening with a bounded liquid-level controller while retaining
the same sinusoidal supply-pressure input and the same separator pressure,
total-liquid and oil/water-interface loops.

## Reproduction contract

- Input: [`data/well_to_topside_slug_control_input.json`](data/well_to_topside_slug_control_input.json)
- MCP route: `runPipeline`
- Analysis selector: `wellToTopsideSlugControl`
- Output receipt: `data/well_to_topside_slug_control_timeseries.csv`
- Model: SRK, 20 kg/s three-phase synthetic feed
- Pipe: 5.4 km, 0.2032 m ID, 60 cells, explicit undulating terrain and rising outlet
- Step/refinement evidence: the fixed-boundary handoff remains qualified by
  `TwoFluidPipeThreePhaseHandoffTest` at 60/120 cells and 0.25/0.125 s.
- Analysis window: the final 75% of the replay; the first 25% is common
  preconditioning.

The coarse-mesh 90 s receipt requires less than 2% difference in mean
production between the cases, a lower regulated liquid peak, pipe phase-mass
residual below `1e-8 kg`, and complete sampled series for choke opening, riser
liquid rate, separator pressure, total liquid level and oil/water interface.

| Metric (analysis window) | Open loop | Regulated | Relative change |
|---|---:|---:|---:|
| Mean production (kg/s) | 18.0989 | 17.9198 | -0.99% |
| Peak riser liquid rate (kg/s) | 14.4430 | 13.2260 | -8.43% |
| Separator pressure range (bar) | 2.3742 | 1.7804 | -25.01% |
| Total-liquid level range (m) | 0.01920 | 0.01759 | -8.39% |
| Oil/water-interface range (m) | 0.00497 | 0.00658 | +32.43% |
| Maximum pipe phase residual (kg) | 2.50e-11 | 3.21e-11 | — |

The water-interface excursion is an explicit tradeoff in this controller
configuration. It is reported rather than hidden by retuning the common
separator loops independently between cases. The complete series contains 91
samples per mode. The MCP replay completed in 67.8 s on the qualification host.

## Boundary qualification

The riser outlet remains at the exact qualified pressure, `56.40099362 bara`.
The separator pressure controller is centred on the same target. Feeding the
live separator pressure back through `TwoFluidPipe.setOutletPressure` is **not**
claimed as qualified by this study: millibar-scale repeated changes can suppress
liquid through-flow even though each individual boundary value is valid. That
route needs a separate moving-pressure-boundary qualification; enforcing a
nonnegative outlet sign is not a substitute.

This is a deterministic numerical reference study. It is not experimental or
field validation, and it does not establish commercial simulator parity.
