---
title: LNG Liquefaction Process Models
description: Closed-loop SMR, C3MR, DMR, and nitrogen-expander process templates with common performance and benchmark metrics.
---

# LNG Liquefaction Process Models

NeqSim provides closed-loop screening models for four common LNG liquefaction
routes in the `neqsim.process.lng` package. Each template uses the standard
NeqSim equation-of-state flashes and unit operations, an
`LNGHeatExchanger` for rigorous multi-stream temperature-enthalpy curves,
and explicit `Recycle` units for refrigerant-loop convergence.

## Supported routes

| Cycle | Template scope | Main equipment |
|---|---|---|
| `SMR` | Single mixed-refrigerant loop | Two-stage MR compression, JT expansion, one MCHE |
| `C3MR` | Single-level equivalent propane precooling plus mixed refrigerant | Propane and MR compressor trains, precooler, MCHE |
| `DMR` | Warm and cold mixed-refrigerant loops | Two MR compressor trains, precooler, MCHE |
| `NITROGEN_EXPANDER` | Closed reverse-Brayton nitrogen loop | Two-stage N2 compression, turboexpander, MCHE |

The C3MR template represents one equivalent propane evaporation level. It is
appropriate for route screening and API examples; detailed design studies
should model the actual three- or four-level propane cascade and optimize
refrigerant inventories, pressure levels, and exchanger geometry.

## Quick start

```java
LNGProcessModel model = new LNGProcessBuilder()
    .setName("C3MR screening case")
    .setCycle(LNGProcessCycle.C3MR)
    .setFeedFlowRate(100000.0)
    .setFeedPressure(60.0)
    .setNumberOfZones(16)
    .setAdaptiveRefinement(true)
    .build();

LNGProcessModel.Result result = model.run();
double specificEnergy = result.getSpecificEnergyKWhPerKgLNG();
double lngYield = result.getLNGYield();
double minimumApproach = result.getMinimumInternalTemperatureApproachC();
LNGProcessBenchmark.Assessment benchmark = result.assessBenchmark();
```

Use `setFeedFluid(SystemInterface)` to supply a custom pretreated feed. The
builder clones the supplied thermodynamic system and applies the configured
flow, temperature, and pressure. Acid gas, water, mercury, and heavy
hydrocarbon removal must be represented upstream when those contaminants are
present.

## Common comparison metrics

`LNGProcessModel.Result` reports these metrics on the same basis for every
route:

- liquid LNG rate and mass yield;
- capacity in MTPA at 8000 operating hours per year;
- total compressor, recovered expander, and net shaft power;
- net specific energy in kWh/kg LNG;
- product temperature, pressure, and density;
- minimum internal temperature approach (MITA);
- summed exchanger exergy destruction; and
- wall-clock process execution time.

## Accuracy controls

The exchanger performs TP flashes at zone boundaries, applies per-stream
pressure drops, and calculates composite curves, MITA, and zone exergy
destruction. Adaptive refinement now scans every registered stream, so a
phase transition on a refrigerant stream can refine the grid even when the
natural-gas enthalpy curve is smooth.

For higher-fidelity studies:

1. increase `setNumberOfZones` until MITA and specific energy are
   grid-independent;
2. keep adaptive refinement enabled around refrigerant phase changes;
3. set measured pressure drops for every exchanger passage;
4. use the actual pretreated feed composition and an appropriate mixing rule;
5. calibrate compressor and expander efficiencies; and
6. optimize refrigerant composition, circulation rate, and pressure levels.

## Speed controls

Adjacent exchanger zone flashes reuse one thermodynamic state and
`ThermodynamicOperations` object per stream by default. This removes
per-zone clone/allocation overhead and carries the previous converged state
into the next flash. Use
`LNGHeatExchanger.setReuseZoneFlashState(false)` for independent-zone
regression comparisons.

The builder also enables process flash warm starts and optimized execution.
For parameter studies, converge a base case before making small changes, and
verify the fast configuration against a higher-zone reference case.

## Literature comparison points

These are comparison points, not universal acceptance limits. Feed
composition, ambient conditions, LNG flash definition, driver efficiency,
and flowsheet complexity must match before interpreting deviations.

| Cycle | Reference specific energy (kWh/kg LNG) | Source |
|---|---:|---|
| SMR | 0.2561 | Pereira et al. (2022) |
| C3MR | 0.2548 | Pereira et al. (2022) |
| DMR | 0.2456 | Pereira et al. (2022) |
| Parallel nitrogen expansion | 0.6180 | He et al. (2019) |

Pereira et al. compared optimized SMR, C3MR, DMR, and AP-X cases for the
same 20,000 kg/h feed:
[Energy Conversion and Management 272 (2022) 116364](https://doi.org/10.1016/j.enconman.2022.116364).

The nitrogen reference is the optimized parallel nitrogen expansion case
reported by He et al.:
[Energy 167 (2019) 1-12](https://doi.org/10.1016/j.energy.2018.10.169).

`LNGProcessBenchmark.assess` uses deliberately broad screening envelopes
around these points and also checks product temperature, liquid yield, and
temperature crosses. A passing screening result demonstrates physical
plausibility on this basis; it does not replace project-specific validation
against plant or licensor data.
