Seven additions for early-phase subsea tie-back work. Each replaces a workaround
that was being rewritten in every study: shut-in wellhead pressure, flowline
sizing, route ingestion, the inlet-pressure solve, the wall-versus-insulation
coupling, and telling a dry fluid from a wet one.

## ⚠️ Behaviour change — read this first

`MultiphaseFlowIntegrator` now defaults to `HydraulicModel.TWO_FLUID` where it
previously used Beggs & Brill.

The Beggs & Brill two-phase friction multiplier and hold-up correlation were
fitted on small-diameter air–water laboratory loops at liquid fractions around
1–2 % and above. A wet-gas tie-back runs well below that, where the correlation
is extrapolated and over-predicts the pressure drop. Since
`TiebackOption.arrivalPressureBara` feeds both the feasibility check and the
NPV, that error propagates into concept selection.

**This will move existing tie-back pressure drops and any economics derived from
them.** `setHydraulicModel(HydraulicModel.BEGGS_BRILL)` restores the previous
behaviour exactly.

The integrator also warns when a two-fluid solve exits after a single iteration,
which means the pressure profile was frozen on the inlet densities.

## New classes

**`RouteProfile`** (`process.equipment.pipeline`) — converts a bathymetric
survey into the mesh either pipe model needs. Handles the depth/elevation sign
convention, appends the riser, resamples, and reports the low points where
liquid collects. `getElevationProfile()` deliberately carries one more entry
than `getSectionLengths()`, which is what `TwoFluidPipe.setElevationProfile`
requires — an off-by-one that is easy to get wrong.
`applyTo(PipeBeggsAndBrills)` warns when collapsing an undulating route onto a
single averaged segment.

**`FlowlineSizeSelector`** (`process.mechanicaldesign.subsea`) — API RP 14E,
`v_e = 1.22c/√ρ`, with a full candidate table and the smallest passing size.
Must be evaluated at the arrival condition, where the mixture is least dense and
the velocity peaks; sizing on inlet density under-sizes the line.

**`TiebackThermalDesign`** (`process.mechanicaldesign.subsea`) — sweeps wall
thickness against insulation together. The steel is part of the cooldown thermal
mass, so thinning the wall lengthens the insulation needed for the same
no-touch time. In the study that motivated this, rating for full shut-in gave a
22.2 mm wall where 75 mm of insulation comfortably exceeded an 8 h target;
crediting HIPPS dropped the wall to 14.3 mm and the same 75 mm then gave 7.9 h.
Neither analysis alone would have shown it.

## Changed

**`PipeBeggsAndBrills`** — new `CalculationMode.CALCULATE_INLET_PRESSURE` and
`getSolvedInletPressure()`. A host imposes an arrival pressure; the question is
what inlet the reservoir must supply. Both pipe models march forward from a
fixed inlet, so this was previously a hand-rolled bisection in every study. A
trial that throws is treated as "inlet too low" rather than propagating.

**`SubseaWell`** — `calculateShutInWellheadPressure(fluid[, wellheadTempC,
steps])`. `setMaxWellheadPressure` was a setter with no calculation behind it,
yet this is the number that sets the flowline design pressure. The column is
re-flashed at each step so density follows pressure and temperature; a
single-density estimate over-predicts the drop badly for gas. No friction term —
a shut-in well is not flowing.

**`SurfCooldownAnalyzer`** — `isWaterPresent()`, `getWaterMoleFraction()`,
`setRequireWater(boolean)`, and `waterPresentInFluid` in the JSON.

The default verdict is **unchanged**: a fluid with no water still reports
`NO_HYDRATE_RISK` with an unbounded no-touch time, and
`SurfCooldownAnalyzerTest.testDryGasReportsNoHydrateRisk` passes untouched. That
is correct for a deliberately dry gas.

The gap being closed is that a **wet** line whose fluid file simply has no water
component gives the identical answer, and the verdict alone cannot separate the
two. The distinction is now queryable, and a design workflow can make it a hard
gate. `TiebackThermalDesign` sets `setRequireWater(true)` by default, because
there a fluid without water is almost always the wrong file.

## Testing

New: `RouteProfileTest` (12), `FlowlineSizeSelectorTest` (9),
`SurfCooldownAnalyzerWaterGuardTest` (6).

Regression over `process.equipment.pipeline`, `pvtsimulation.flowassurance`,
`process.mechanicaldesign.subsea` and `process.equipment.subsea`:
**1153 tests, 0 failures.** `spotless:check` clean.

## Documentation

- `docs/process/tieback_early_phase_design.md` — the workflow in dependency
  order, since each answer constrains the next. Registered in
  `REFERENCE_MANUAL_INDEX.md`; all cross-links verified.
- `.github/skills/neqsim-subsea-and-wells` — new sizing, hydraulics, shut-in and
  thermal-coupling sections, plus eight new rows in the pitfalls table.

## Notes for the reviewer

- Java 8 throughout; no `var`, `List.of`, or text blocks.
- The fatigue limit state in the F101 examples uses a **placeholder spectrum**; a
  real assessment needs the VIV and installation stress history from a free-span
  analysis. Stated in both the code comment and the doc.
- `RouteProfile.applyTo(PipeBeggsAndBrills)` is intentionally lossy and says so.
- Trap found during development, in case it helps elsewhere:
  `component.getz()` returns 0 before `init(0)`, so the water detector reads
  component moles instead.
