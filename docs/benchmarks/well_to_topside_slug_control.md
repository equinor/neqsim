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
| Mean production (kg/s) | 18.1120 | 17.9246 | -1.03% |
| Peak riser liquid rate (kg/s) | 14.4868 | 13.3543 | -7.82% |
| Separator pressure range (bar) | 1.0219 | 0.9289 | -9.10% |
| Total-liquid level range (m) | 0.01390 | 0.01571 | +13.04% |
| Oil/water-interface range (m) | 0.00618 | 0.00725 | +17.39% |
| Maximum pipe phase residual (kg) | 2.85e-11 | 2.68e-11 | — |

The regulated choke reduces pressure variation and peak riser liquid flow. The
total-liquid level range increases by 13% and the oil/water-interface range by
17% with the same separator controllers in both runs. The complete series contains 91
samples per mode. The table and CSV are from the 0.25 s replay on 2026-11-25.

The regression checks the separator pressure standard deviation over all one-second samples
in the analysis window (23–90 s). At 0.25 s steps it falls from 0.323 to 0.292 bar. Halving
the step gives 0.321 and 0.289 bar. Both runs exceed the required 0.02 bar absolute reduction.
The full pressure range must also decrease. The study explicitly selects the holdup-weighted
annular friction used for its original qualification.

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

## Two wells and a platform inlet choke

The [production restart decision study](../../examples/production-decision/README.md)
places the choke between the riser and the separator. It adds pressure-dependent
well inflow, a live separator pressure boundary, recompression with recycle,
fluid alternatives, withheld refinements and a field-history comparison.
The original wellhead-choke replay remains available through
`runPipeline`. Its `quantityEvidence` response records the mesh, requested
timestep, prescribed outlet boundary, accepted pipe phase residual, peak riser
liquid rate and mean inlet production for each control mode. A single call
performs no cross-mesh comparison. The rates use kg/s and the residual uses kg.
