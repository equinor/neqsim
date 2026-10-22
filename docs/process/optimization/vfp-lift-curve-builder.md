---
title: VFP lift curve builder
description: "Building deliverable VFPPROD and VFPINJ lift curve tables for Eclipse and OPM Flow with VfpTableBuilder, a tubing survey lift model and sanity enforcement."
---

# VFP lift curve builder

`VfpTableBuilder` (package `neqsim.process.fielddevelopment.reservoir`) builds a `VFPPROD` or `VFPINJ`
table from a pluggable lift model. It owns the axes, the evaluation of the model at every coordinate, rate caps,
repair of infeasible points, physical sanity enforcement, diagnostics and the deck text with a provenance header.
Deck formatting and unit conversion are delegated to `EclipseVFPExporter`
(see the [export contract](vfp-export-contract)). The method guide for engineers and agents is the skill
`neqsim-vfp-lift-curves`.

## Building blocks

| Class | Role |
|---|---|
| `VlpProvider` | Single-method interface: flowing pressure at datum for rate, outlet pressure, water ratio, gas ratio and ALQ |
| `TubingVlpProvider` | Deviated tubing survey (`TubingPerformance.setSurvey`) with an inlet-pressure solve for the wellhead pressure |
| `ForwardVlpProvider` | Turns any forward model (outlet pressure from inlet pressure: pipes, valves, a process) into a provider by inverse solve |
| `RecombinationFluidFactory` | Recombines surface gas, stock-tank liquid and water to the well stream for each coordinate |
| `VfpTableBuilder` | Axes, evaluation, rate cap, sanity enforcement, deck text |
| `VfpDeckReader` | Parses a `VFPPROD`/`VFPINJ` keyword back to numbers, for round-trip checks |

## Gas well example

```java
RecombinationFluidFactory wellStream = new RecombinationFluidFactory(template, surfaceGas, condensate)
    .setRateBasis("GAS").setWaterRatioType("WGR").setGasRatioType("OGR");
TubingVlpProvider vlp = new TubingVlpProvider(wellStream, new double[] {0.0, 1500.0, 3200.0},
    new double[] {0.0, 1450.0, 2500.0}, new double[] {0.15, 0.15}).setTemperatures(95.0, 40.0)
    .setNumberOfSegments(6).setCorrelation(TubingPerformance.CorrelationType.GRAY);
VfpTableBuilder.Result result = VfpTableBuilder.gasWell(1).setDatumDepth(2500.0)
    .setRates(VfpTableBuilder.rateAxis(0.2e6, 1.0e6, 5, 1.2))
    .setOutletPressures(VfpTableBuilder.pressureAxis(40.0, 80.0, 4, 0.1, 0.1))
    .setWaterRatios(new double[] {1.0e-6, 1.0e-4}).setGasRatios(new double[] {1.0e-5, 1.0e-4})
    .setRateMonotoneMode(VfpTableBuilder.RateMonotoneMode.FULL).setRateCap(1.1e6, 200.0)
    .addProvenance("Gray tubing survey, synthetic well").build(vlp);
result.writeDeck(java.nio.file.Paths.get("vfp1.inc"));
```

This example is exercised by `VfpTableBuilderTest.documentedGasWellExampleRuns`; the round trip, off-grid accuracy,
rate cap, failure policy and monotonicity behaviour are covered by the other tests in the same class.

## Sanity rules

The builder applies these rules in order and reports every repair in `Result.getWarnings()`:

1. The flowing pressure is not below the outlet pressure: $p_{wf} \ge p_{thp}$.
2. The flowing pressure increases with the outlet pressure with slope at least $m_{min}$ (default 0.5 bar/bar).
3. Rate dependence: `ABOVE_MINIMUM` (default) keeps the liquid-loading side of a J-shaped gas curve and makes the rest
   non-decreasing; `FULL` makes the whole curve non-decreasing, which OPM Flow needs to avoid flipping between rate
   and THP control.

A rate cap $Q_c$ replaces the pressure above the cap by a penalty slope:

$$p_{wf}(Q) = p_{wf}(Q_c) + \lambda \left(\frac{Q}{Q_c} - 1\right), \quad Q > Q_c$$

where $\lambda$ is the penalty in bar per 100 percent above the cap.

## Interpolation check

`Result.interpolate(...)` reproduces the multilinear interpolation of the simulator, so off-grid accuracy can be
checked against a direct provider solve before the deck is used.

## Python

`examples/neqsim/process/vfp_gas_well_lift_curve.py` runs the same synthetic gas well through jpype with the workspace
classes (`neqsim_init`) and writes the include file. A custom lift model in Python implements `VlpProvider` with
`jpype.JImplements`; see the skill `neqsim-vfp-lift-curves`.

## Related

- [VFP export contract](vfp-export-contract) for the deck keyword, units and what the exporter refuses to do.
- `TubingPerformance.setSurvey` and `calculateInletPressureForOutlet` for deviated wells.
- `neqsim.mcp.runners.VfpTableRunner` for a JSON interface (not yet a registered MCP tool).
