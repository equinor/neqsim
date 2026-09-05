---
title: "Early-phase tie-back design"
description: "Workflow for screening a subsea tie-back in NeqSim: shut-in wellhead pressure, API RP 14E flowline sizing, two-fluid hydraulics on a surveyed route, DNV-ST-F101 wall thickness, and the coupling between wall thickness and cooldown insulation. Covers RouteProfile, FlowlineSizeSelector, TiebackThermalDesign, SubseaWell shut-in pressure, and the CALCULATE_INLET_PRESSURE mode."
---

A subsea tie-back study asks a small number of questions in a fixed order, and
each answer constrains the next. This page walks that order and points at the
class that answers each step.

## The order matters

1. **What pressure can a closed-in well impose?** That sets the flowline design
   pressure, which sets the wall thickness, which is part of the cooldown
   thermal mass. Start here.
2. **What size line?** The erosional limit gives a floor on diameter.
3. **What inlet pressure does the host's arrival pressure demand?** The
   reservoir has to supply it, or the concept does not work.
4. **What wall thickness?** DNV-ST-F101, against the design pressure from step 1.
5. **How much insulation?** This depends on the wall from step 4 — that coupling
   is the one most often missed.

## 1. Shut-in wellhead pressure

`SubseaWell.calculateShutInWellheadPressure` integrates a static column from the
reservoir datum to the seabed, re-flashing at each step so the density follows
pressure and temperature. A single-density estimate over-predicts the drop badly
for a gas column, because the gas is much lighter near the top.

```java
SubseaWell well = new SubseaWell("P-1", stream);
well.setReservoirPressure(345.0);
well.setReservoirTemperature(90.0);
well.setTrueVerticalDepth(3100.0);
well.setWaterDepth(261.0);

double shutIn = well.calculateShutInWellheadPressure(reservoirFluid, 40.0, 60);
```

There is no friction term: a shut-in well is not flowing. The result is the
settled-out pressure, which is the correct basis for a containment design.

## 2. Flowline size

`FlowlineSizeSelector` applies API RP 14E,

$$
v_e = \frac{1.22\,c}{\sqrt{\rho}}
$$

with $v_e$ in m/s, $\rho$ in kg/m³ and $c = 100$ for continuous service.

Evaluate it at the condition where the mixture is **least dense** — normally the
arrival, not the inlet. Sizing on inlet density under-sizes the line.

```java
FlowlineSizeSelector selector = new FlowlineSizeSelector()
    .setBasisFromFluid(fluidAtArrivalConditions, 16.13)
    .setWallThickness(0.0159);
Map<String, Object> selected = selector.select();
```

`select()` returns the smallest standard size that passes, or `null` when
nothing does. Every candidate is in `getCandidates()` with its velocity and its
percentage of the erosional limit.

This is a velocity screen only. A size that passes still has to deliver the
required arrival pressure and pass DNV-ST-F101.

## 3. Hydraulics on a surveyed route

### Use the two-fluid model for a wellstream

`PipeBeggsAndBrills` is a correlation fitted on small-diameter air-water
laboratory loops at liquid fractions around 1–2 % and above. A wet-gas tie-back
runs well below that, where the two-phase friction multiplier is extrapolated
and over-predicts the pressure drop substantially. Prefer
`TwoFluidPipe`, which resolves the phases separately.

Always assert both conditions before believing a two-fluid profile:

```java
pipe.run();
if (!pipe.isSteadyStateConverged() || pipe.getSteadyStateIterationsUsed() <= 1) {
  // A single-iteration exit means the pressure profile was frozen on the inlet
  // densities and never picked up the flash. Reject it.
}
```

`MultiphaseFlowIntegrator` now defaults to `HydraulicModel.TWO_FLUID` and warns
when the solve exits after one iteration. Set
`setHydraulicModel(HydraulicModel.BEGGS_BRILL)` for a fast correlation check on
a liquid-dominated line inside the correlation's calibration range.

### Feeding it a real seabed

`RouteProfile` takes survey data once and hands each pipe model what it wants.
Depths are positive downwards as surveyed; elevations are negative downwards as
the models expect, and the conversion happens in one place.

```java
RouteProfile route = RouteProfile.fromDepths(kpMetres, depthMetres)
    .withRiser(216.0, 25.0)
    .resample(60);
route.applyTo(twoFluidPipe);
```

`getElevationProfile()` deliberately has one more entry than
`getSectionLengths()`, which is what `TwoFluidPipe.setElevationProfile` requires.
`getLowPointKp()` returns the local minima — the terrain-slug traps, and the
first place to look when a line is filling with liquid.

`applyTo(PipeBeggsAndBrills)` collapses the route to a single averaged segment
and warns when the maximum inclination exceeds 5°, because that averaging
discards the undulation that drives hold-up.

### Solving for the inlet pressure

A host imposes an arrival pressure; the question is what inlet the reservoir must
supply. Both pipe models march forward from a fixed inlet, so this used to be a
hand-rolled bisection in every study.

```java
pipe.setCalculationMode(PipeBeggsAndBrills.CalculationMode.CALCULATE_INLET_PRESSURE);
pipe.setOutletPressure(70.0, "bara");
pipe.run();
double requiredInlet = pipe.getSolvedInletPressure();
```

A trial that throws — typically because the pressure ran negative part way along
— is treated as "inlet too low" rather than propagating, so the bracket closes
from below. If the line cannot deliver the target at that rate at any inlet, the
run fails with that stated explicitly.

## 4. Wall thickness

`DnvStF101PipelineDesignCalculator` returns every limit state with its
utilisation and a PASS/FAIL status. Sweep the wall and take the thinnest that
passes all of them:

```java
DnvStF101PipelineAssessment assessment = DnvStF101PipelineDesignCalculator.calculate(input);
for (DnvStF101LimitStateCheck check : assessment.getChecks()) {
  // check.getLimitState(), check.getUtilization(), check.getStatus()
}
```

The governing check is usually system-test-pressure containment at high design
pressure, and propagation buckling at low design pressure in deep water.

## 5. Insulation — and why it depends on step 4

This is the coupling that gets missed. The steel wall is part of the cooldown
thermal mass. A decision that thins the wall — typically installing HIPPS so the
line need not be rated for full shut-in — removes stored heat and **lengthens**
the insulation needed to hold the same no-touch time.

A worked case: rating a tie-back for full shut-in required a 22.2 mm wall, and
75 mm of insulation comfortably exceeded an 8 h no-touch target. Protecting it
instead and rating it for the flowing pressure dropped the wall to 14.3 mm — and
the same 75 mm then gave only 7.9 h. The steel saving was real; so was the extra
insulation. Neither analysis alone would have shown it.

`TiebackThermalDesign` sweeps both together:

```java
TiebackThermalDesign design = new TiebackThermalDesign(wetFluid)
    .setInternalDiameter(0.1873)
    .setWallThicknesses(new double[] {0.0143, 0.0222})
    .setInsulationThicknesses(new double[] {0.050, 0.075, 0.100})
    .setSeabedTemperature(6.0)
    .setOperatingTemperature(35.0)
    .setRequiredNoTouchTime(8.0);
design.calculate();
double insulationForThinWall = design.getRequiredInsulationForWall(14.3);
```

### The fluid must carry water

A fluid with no water cannot form hydrates, so `SurfCooldownAnalyzer` reports
`NO_HYDRATE_RISK` with an unbounded no-touch time. That is correct for a
deliberately dry gas — and it is the same answer a **wet** line gives when its
fluid file simply has no water component. The verdict alone cannot tell the two
apart.

So check it explicitly, and in a design workflow make it a hard gate:

```java
analyzer.isWaterPresent();          // false for a dry gas AND for a wrong file
analyzer.getWaterMoleFraction();
analyzer.setRequireWater(true);     // throw rather than return an unbounded time
```

`TiebackThermalDesign` sets `setRequireWater(true)` by default, because in a
tie-back design a fluid without water is almost always the wrong file.

Add water through NeqSim's own path, which sets the water binary interaction
parameters and enables the VLLE flash — not a bare `addComponent`:

```java
SystemInterface wet = EclipseFluidReadWrite.read(file, true);        // kij 0.5
EclipseFluidReadWrite.addWaterToFluid(existingFluid, 0.5);           // in place
```

Do **not** call `setMixingRule` after `EclipseFluidReadWrite.read`: `read`
installs the mixing rule and the file's BIC block, and `setMixingRule` reloads
database kij, silently discarding any regressed interaction parameters the
characterisation carried.

## Checklist

- [ ] Shut-in wellhead pressure calculated, not assumed.
- [ ] Flowline sized at the least-dense condition.
- [ ] Wellstream hydraulics run with the two-fluid model.
- [ ] Two-fluid solve converged in more than one iteration.
- [ ] Route elevations from a real survey, not a flat default.
- [ ] Wall thickness passes every DNV-ST-F101 limit state.
- [ ] Insulation checked against the **selected** wall thickness.
- [ ] Fluid carries water before any hydrate or cooldown number is believed
      (`isWaterPresent()`, or `setRequireWater(true)` to make it a gate).
- [ ] If HIPPS is credited, its SIL requirement is determined elsewhere.

## Related documentation

- [DNV-ST-F101 pipeline screening](dnv_st_f101_pipeline_screening.md)
- [Pipeline mechanical design](pipeline_mechanical_design.md)
- [SURF subsea equipment](SURF_SUBSEA_EQUIPMENT.md)
- [Subsea systems](equipment/subsea_systems.md)
- [Pipeline simulation](equipment/pipeline_simulation.md)
- [Flow assurance overview](../pvtsimulation/flow_assurance_overview.md)
