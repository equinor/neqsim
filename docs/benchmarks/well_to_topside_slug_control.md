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
production between the cases, a lower regulated liquid peak, a lower regulated
separator pressure range, pipe phase-mass residual below `1e-8 kg`, and complete
sampled series for choke opening, riser liquid rate, separator pressure, total
liquid level and oil/water interface.

| Metric (analysis window) | Open loop | Regulated | Relative change |
|---|---:|---:|---:|
| Mean production (kg/s) | 18.0909 | 17.9027 | -1.04% |
| Peak riser liquid rate (kg/s) | 14.3846 | 13.3406 | -7.26% |
| Separator pressure range (bar) | 1.0068 | 0.9216 | -8.47% |
| Total-liquid level range (m) | 0.01390 | 0.01591 | +14.49% |
| Oil/water-interface range (m) | 0.00618 | 0.00725 | +17.39% |
| Maximum pipe phase residual (kg) | 2.97e-11 | 2.44e-11 | — |

The regulated case trades separator level for riser liquid rate in this
controller configuration: both the total-liquid level range and the
oil/water-interface range are larger with the level controller acting on the
choke. They are reported rather than hidden by retuning the common separator
loops independently between cases. The complete series contains 91 samples per
mode. The MCP replay completed in 32 s on a two-core cloud host.

The table and the stored series were regenerated on 2026-11-22 with the
frozen-composition density update that `TwoFluidPipe` applies by default when no
model moves mass between gas and oil. With that update switched off the same
replay gives a separator pressure range change of -12.08% and a liquid peak
change of -7.63%, which are the values of the revision before the update was
introduced. Earlier values of this table (separator pressure range 2.3742 to
1.7804 bar, -25.01%) predate that update and other model changes made since
they were recorded.

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
