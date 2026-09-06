---
title: "TwoFluidPipe Reporting and Validation"
description: "How to report long multiphase flowline results from TwoFluidPipe and compare them with traceable external data."
---

# TwoFluidPipe Reporting and Validation

This page describes how to extract engineering results from
`neqsim.process.equipment.pipeline.TwoFluidPipe` for long multiphase flowlines and how to compare
those results with external simulator or field data.

## Current reporting status

`TwoFluidPipe` exposes detailed profile and summary APIs. The convenience class
`neqsim.process.equipment.pipeline.twophasepipe.reporting.TwoFluidPipeReport` builds standard CSV,
text, JSON, event, and benchmark comparison outputs from those APIs.

The recommended production workflow is:

1. Build and run the `TwoFluidPipe` model.
2. Extract spatial profiles from the pipe, or call `TwoFluidPipeReport` helper methods.
3. Add summary metrics and flow-assurance indicators.
4. Export to CSV/JSON for plotting, review, or comparison.
5. If available, compare against traceable experimental, field, or external-simulator data using
   the benchmark harness. Keep model-to-model checks distinct from experimental validation.

## Steady-state profile reporting

After `pipe.run()`, the following methods provide one value per pipe section:

| API | Unit | Description |
|-----|------|-------------|
| `getPositionProfile()` | m | Section midpoint positions |
| `getPressureProfile()` | Pa | Pressure profile |
| `getTemperatureProfile()` | K | Fluid temperature profile |
| `getTemperatureProfile("C")` | degC | Fluid temperature profile in Celsius |
| `getLiquidHoldupProfile()` | fraction | Total liquid holdup |
| `getWaterCutProfile()` | fraction | Water fraction of liquid |
| `getOilHoldupProfile()` | fraction | Oil holdup |
| `getWaterHoldupProfile()` | fraction | Water holdup |
| `getGasVelocityProfile()` | m/s | Gas velocity |
| `getLiquidVelocityProfile()` | m/s | Liquid velocity |
| `getOilVelocityProfile()` | m/s | Oil velocity when oil-water slip is active |
| `getWaterVelocityProfile()` | m/s | Water velocity when oil-water slip is active |
| `getOilWaterSlipProfile()` | m/s | Oil velocity minus water velocity |
| `getFlowRegimeProfile()` | enum | Gas-liquid flow regime by section |
| `getOilWaterFlowRegimeProfile()` | enum | Oil-water flow configuration by section |
| `getWaterWettingProfile()` | boolean | Water-wetting indicator for corrosion screening |
| `getWaterDropoutRiskProfile()` | boolean | Water dropout / accumulation risk |
| `getEntrainmentFractionProfile()` | fraction | Estimated liquid entrainment in annular/mist flow |
| `getEntrainedDropletDiameterProfile()` | m | Characteristic entrained droplet diameter |
| `getInclinedSectionGasCarryoverNumberProfile()` | dimensionless | Local uphill liquid-carryover screen; not a system stability criterion |
| `getInclinedSectionLiquidFallbackPotentialProfile()` | boolean | Local fallback flag from the carryover screen |
| `getSevereSluggingNumberProfile()` | dimensionless | Deprecated alias for the local carryover screen |
| `getSevereSlugPotentialProfile()` | boolean | Result flag from the most recent explicit flowline-riser evaluation |
| `getHeatTransferProfile()` | W/(m2 K) | Heat-transfer coefficient profile, when configured |
| `getSurfaceTemperatureProfile()` | K | Ambient/surface temperature profile, when configured |

Example Java extraction:

```java
org.apache.logging.log4j.Logger logger =
    org.apache.logging.log4j.LogManager.getLogger("TwoFluidPipeReporting");
pipe.run();

double[] x = pipe.getPositionProfile();
double[] pressurePa = pipe.getPressureProfile();
double[] temperatureC = pipe.getTemperatureProfile("C");
double[] liquidHoldup = pipe.getLiquidHoldupProfile();
double[] waterCut = pipe.getWaterCutProfile();
double[] gasVelocity = pipe.getGasVelocityProfile();
double[] liquidVelocity = pipe.getLiquidVelocityProfile();
PipeSection.FlowRegime[] regimes = pipe.getFlowRegimeProfile();
double[] entrainment = pipe.getEntrainmentFractionProfile();
double[] gasCarryoverNumber = pipe.getInclinedSectionGasCarryoverNumberProfile();
boolean[] localFallback = pipe.getInclinedSectionLiquidFallbackPotentialProfile();
boolean[] waterWetting = pipe.getWaterWettingProfile();

for (int i = 0; i < x.length; i++) {
  double pBara = pressurePa[i] * 1.0e-5;
  logger.info("{},{},{},{},{},{},{},{}",
      x[i], pBara, temperatureC[i], liquidHoldup[i], waterCut[i],
      gasVelocity[i], liquidVelocity[i], regimes[i]);
}
```

The same steady-state results can be exported directly:

```java
String profileCsv = TwoFluidPipeReport.toSteadyStateProfileCsv(pipe);
String summaryText = TwoFluidPipeReport.toSummaryText(pipe);
String summaryJson = TwoFluidPipeReport.toSummaryJson(pipe);
String eventsCsv = TwoFluidPipeReport.toSlugAndFlowAssuranceCsv(pipe);
```

## Transient reporting

A successful `runTransient(dt, id)` completes the requested interval to floating-point
precision. `getTime()`, `getSimulationTime()`, and the mass report's elapsed time count only
accepted substeps. `setMaximumTransientSubsteps(...)` limits attempted substeps; exhausting it
throws instead of increasing the CFL step or reporting an incomplete interval as successful.
An exception can leave an accepted partial interval: inspect the report and clock before
resuming. Rejected hydrodynamic trials restore cell pressure, density, conservative state,
configured oil-water closures and their last accepted diagnostics. Stiff source splitting must
preserve an invalid raw trial for rejection before primitive recovery can repair phase masses.
Raw negative phase mass or nonfinite state is rejected even when adaptive retry is disabled;
in that mode the call fails at the last accepted state. A stable step below clock resolution
reports that limitation separately from exhausting the attempt budget. The public integrator
CFL helpers also retain the stable upper bound when it is below the preferred minimum step.
This is substep recovery, not transactional rollback of an entire connected process.
`setCflNumber(...)` honors finite values strictly between zero and one, including values below
0.1 used for refinement studies. Invalid values throw rather than silently selecting another CFL.

For transient cases, call `runTransient(dt, id)` repeatedly and store a snapshot at the reporting
interval required by the study. Do not store every internal sub-step for long pipelines unless
high-frequency pressure waves or slug arrivals are being investigated.

```java
double dt = 1.0;
int reportEvery = 10;

for (int step = 0; step < 3600; step++) {
  pipe.runTransient(dt, UUID.randomUUID());

  if (step % reportEvery == 0) {
    double time = pipe.getSimulationTime();
    double[] x = pipe.getPositionProfile();
    double[] p = pipe.getPressureProfile();
    double[] hL = pipe.getLiquidHoldupProfile();
    // Write one row per position with this time stamp.
  }
}
```

Recommended transient CSV columns:

```text
time_s,position_m,pressure_bara,temperature_C,liquid_holdup,water_cut,
gas_velocity_m_s,liquid_velocity_m_s,oil_velocity_m_s,water_velocity_m_s,
flow_regime,oil_water_flow_regime,water_wetting,water_dropout_risk,
entrainment_fraction,entrained_droplet_diameter_m,
inclined_section_gas_carryover_number,inclined_section_liquid_fallback_potential,
severe_slugging_number,severe_slug_potential
```

### Thermal-energy validation

For a closed-boundary thermal `runTransient(...)`, call
`getLastThermalEnergyBalanceReport()` to validate the same post-step sensible-energy model that
changed the fluid and wall temperatures. Its discrete balance is

$$\Delta E_f+\Delta E_w=E_{adv}+E_{JT}-E_{amb}$$

where positive $E_{adv}$ and $E_{JT}$ add energy and positive $E_{amb}$ removes energy. CLOSED
external faces contribute zero sensible advection, but internal face transport remains active. In
the multilayer model, the fluid and first wall layer use the same instantaneous heat rate; the
reported ambient loss is the last-layer flux from the same explicit update.

```java
pipe.setHeatTransferCoefficient(25.0); // W/(m2 K); the report is null when heat transfer is disabled
pipe.runTransient(0.001, UUID.randomUUID());
TwoFluidThermalEnergyBalanceReport thermal = pipe.getLastThermalEnergyBalanceReport();
boolean closes = thermal.isWithinTolerance(1.0e-5, 1.0e-10);
```

The report is `null` when heat transfer is disabled. It covers fluid sensible energy and simple-wall
or radial-layer storage. Strict domain-level closure applies only when both external mass boundaries
are CLOSED and phase transfer does not change material inventory. Open boundaries require explicit
boundary-enthalpy terms; phase-changing cases additionally require compositional and latent-energy
terms. In those cases this report is an internal post-step temperature-model diagnostic, not a full
domain energy audit. For a closed cooldown, also verify zero boundary mass/enthalpy transport,
monotonic all-cell cooling without ambient undershoot, repeatability, serialization/copy behavior,
both explicit and IMEX paths, and mesh/time-step refinement.

### Phase-transfer validation

When `setIncludeMassTransfer(true)` is enabled, validate gas, oil, and water separately rather than
checking total mass alone. Condensation is assigned from the equilibrium oil/aqueous mass split,
whereas evaporation is withdrawn from the actual donor inventories. The transfer-only requirements
are:

$$\Gamma_G+\Gamma_O+\Gamma_W=0$$

and, using donor velocity for transferred momentum,

$$S_{p,G}+S_{p,O}+S_{p,W}=0$$

Use `TwoFluidMassBalanceReport` to check `GAS`, `OIL`, `WATER`, `LIQUID`, and `TOTAL`. A useful
phase-transition test starts from a cell with no liquid seed, crosses the SRK/CPA dew point in both
directions, and sweeps at least three nearby temperatures on each side. Report the EOS, mixing rule,
composition, absolute pressure, temperature, relaxation time, time step, mesh, and phase inventories.
Repeat the run to verify deterministic behavior and compare a refined time step and mesh. Serialize a
condensed-state copy and require the original and copy to follow the same reheating trajectory. As a
negative control, repeat the cooldown with `setIncludeMassTransfer(false)` and require every phase
source and inventory change to remain zero even though the temperature crosses the dew point.

For an aqueous-first transition, the first condensation source must be water even though the
gas-only hydrodynamic water cut defaults to zero. For an oil-first transition, the water source must
remain zero. In a gas + oil + aqueous flash, the reported equilibrium liquid mass fractions must both
be included and sum to one. `FlashTable` and rigorous-flash runs should give the same phase identity;
use sufficiently fine tables near phase boundaries.

The default phase-resolved closure tracks bulk phase inventories. Optional component transport,
enabled with `setComponentTransportEnabled(true)` before initialization, also carries named
component inventories by phase and cell and exposes component-conservation reports. Its boundary
and phase-transfer limitations must be checked separately. Neither total-mass closure nor component
closure establishes commercial-simulator equivalence or experimental transient accuracy.

The `severe_slugging_number` header is retained as a deprecated duplicate for CSV compatibility.
It contains the local inclined-section gas-carryover number, not the explicit system stability
result. New consumers should use `inclined_section_gas_carryover_number`. Call
`evaluateSevereSluggingSystem(...)` before exporting if `severe_slug_potential` should contain
a system-level classification.

## Summary metrics

Use these methods for an executive summary or design report:

| API | Description |
|-----|-------------|
| `getInletPressure()` | Inlet pressure in bara |
| `getOutletPressure()` | Outlet pressure in bara |
| `getAverageLiquidHoldup()` | Volume-weighted average liquid holdup |
| `getDominantFlowRegime()` | Most frequent flow regime |
| `getAverageSuperficialGasVelocity()` | Average superficial gas velocity |
| `getAverageSuperficialLiquidVelocity()` | Average superficial liquid velocity |
| `getAverageMixtureDensity()` | Volume-weighted mixture density |
| `getMaxMixtureVelocity()` | Maximum mixture velocity |
| `getErosionalVelocity()` | API 14E erosional velocity |
| `getErosionalVelocityMargin(double)` | Maximum velocity divided by erosional velocity |
| `getFlowAnalysisSummary()` | Mid-pipe dimensionless flow summary |
| `getThermalSummary()` | Thermal model summary |
| `getSlugStatisticsSummary()` | Slug-tracking summary |
| `getHydrateRiskSections()` | Sections below configured hydrate temperature |
| `getWaxRiskSections()` | Sections below configured wax appearance temperature |

## Closure diagnostics

The two-fluid closure pass updates additional diagnostics that are useful for model review and
validation. Each value is available both on `TwoFluidSection` and as a top-level
`TwoFluidPipe` profile:

| Section API | Pipe profile API | Description |
|-------------|------------------|-------------|
| `getOilWaterFlowRegime()` | `getOilWaterFlowRegimeProfile()` | Oil-water flow configuration |
| `isWaterWetting()` | `getWaterWettingProfile()` | Water-wetting indicator for corrosion screening |
| `isWaterDropoutRisk()` | `getWaterDropoutRiskProfile()` | Water dropout / accumulation risk |
| `getEntrainmentFraction()` | `getEntrainmentFractionProfile()` | Estimated liquid entrainment fraction |
| `getEntrainedDropletDiameter()` | `getEntrainedDropletDiameterProfile()` | Entrained droplet diameter |
| `getInclinedSectionGasCarryoverNumber()` | `getInclinedSectionGasCarryoverNumberProfile()` | Local uphill liquid-carryover screen |
| `isInclinedSectionLiquidFallbackPotential()` | `getInclinedSectionLiquidFallbackPotentialProfile()` | Local fallback flag |
| `getSevereSluggingNumber()` | `getSevereSluggingNumberProfile()` | Deprecated aliases for the same local screen |
| `isSevereSlugPotential()` | `getSevereSlugPotentialProfile()` | Last explicit flowline-riser system result |

The steady-state and transient profile CSV exporters include these diagnostics. Boolean values are
written as `true` or `false`; a missing oil-water regime is written as an empty field.
The system-result profile is meaningful only after `evaluateSevereSluggingSystem(...)` and is
cleared when the next transient step changes the solved state.

## Benchmark comparison format

The validation harness reads external simulator or field data in this CSV format:

```csv
case,time_s,position_m,variable,value,abs_tolerance,rel_tolerance,source
```

Supported captured variables include:

```text
pressure_pa
pressure_bara
temperature_k
liquid_holdup
water_cut
oil_holdup
water_holdup
gas_velocity_m_s
liquid_velocity_m_s
oil_velocity_m_s
water_velocity_m_s
entrainment_fraction
entrained_droplet_diameter_m
inclined_section_gas_carryover_number
inclined_section_liquid_fallback_flag
severe_slugging_number
water_wetting_flag
water_dropout_risk_flag
severe_slug_potential_flag
```

`severe_slugging_number` is the deprecated benchmark key for
`inclined_section_gas_carryover_number`; it is retained only for existing comparison files.

Example use:

```java
Path reference = java.nio.file.Paths.get("reference_export.csv");
List<TwoFluidBenchmarkHarness.BenchmarkPoint> points =
    TwoFluidBenchmarkHarness.readCsv(reference);

TwoFluidBenchmarkHarness.Snapshot snapshot = TwoFluidBenchmarkHarness.capture(pipe);
TwoFluidBenchmarkHarness.Comparison comparison =
    TwoFluidBenchmarkHarness.compare(snapshot, points);

if (!comparison.isPassed()) {
  throw new AssertionError(comparison.failureSummary());
}
```

For transient comparisons, capture and pass a list of snapshots. The harness uses linear
interpolation in time and position for continuous profiles. Variables ending in `_flag` use
nearest-neighbour sampling so boolean diagnostics remain `0.0` or `1.0`. Intervals containing a
non-finite diagnostic sentinel also use the nearest endpoint instead of producing `NaN`.
Comparison results can be exported as CSV:

```java
String comparisonCsv = TwoFluidPipeReport.toComparisonCsv(comparison);
```

## Reporting recommendations for long flowlines

For long oil and gas flowlines, report at least:

- Geometry and discretization: length, diameter, roughness, elevation profile, number of sections,
  mesh refinement strategy.
- Boundary conditions: inlet stream, flow rate, outlet pressure, transient changes.
- Thermodynamics: fluid model, flash interval, mass-transfer relaxation time, heat-transfer setup.
- Pressure and temperature profiles.
- Liquid holdup, water cut, phase velocity, flow-regime, entrainment, wetting, and severe-slugging
  profiles.
- Slug statistics and terrain low-point liquid accumulation.
- Hydrate/wax/thermal risk sections if thresholds are configured.
- Erosional velocity margin and maximum mixture velocity.
- Benchmark comparison table with source, assumptions, uncertainty, mesh/timestep study, and failed
  as well as passed metrics when external data are available.

## Public severe-slugging evidence

`SevereSluggingBenchmarkHarness` reads the digitized public Tengesdal (2002) -3-degree velocity
map and reports a confusion matrix without forcing transition observations into a binary class.
Across 26 severe and 15 stable observations, the current Taitel system screen has 22 true
positives, 4 false negatives, 8 false positives, and 7 true negatives (70.7% accuracy). The 14
transition observations are reported separately as 6 predicted severe and 8 predicted stable.

`SevereSluggingExperimentalBenchmarkTest` contains the large-facility Test 3 geometry, but is
currently disabled at class level: the existing transient routes do not produce a valid unclamped
solution (issues #2909 and #2911). The previously reported ensemble numbers are historical
exploratory output, not current passing validation. Sensitivity to tiny perturbations alone does
not establish physical chaos; numerical instability must first be excluded through mesh and
timestep studies. The active flow-map screen above does not time-march the pipe.

The opt-in coupled-pressure progress and long-horizon tests check numerical progress, residuals
and boundedness. They do not establish measured severe-slugging period, amplitude or outlet slug
distributions. Liquid-rich fixed-point and pressure-runaway acceptance tests also remain disabled.
The 600-second liquid-outlet/inventory acceptance case has been re-enabled after phase-force
corrections. This is a narrower result than steady-to-transient consistency: the separate
1800-second fixed-point test still exceeds its original 5% inventory-drift limit.

The coupled solver applies donor-bounded phase transfers and a common damping factor for pressure
corrections. Its positive numerical pressure bound is configurable in Pa and defaults to 1 Pa;
an atmospheric outlet is not an interior lower bound. The standalone solver retains the affine
sound-speed-based density response for compatibility. The coupled pipe defaults to a local
polytropic gas response, anchored in the supplied pressure, density and sound speed. It also
enables implicit face momentum interpolation to remove alternating cell-pressure modes.
An inadmissible correction returns nonconvergence; the same applied pressure increment must drive
density, mass flux and momentum. Convergence requires both cell-volume closure and the fixed
outlet-pressure residual to meet the configured relative tolerance. Reset and failed full steps
clear previous correction results and phase-transfer ledgers. Coarse and refined Tengesdal progress cases now complete their
five-second intervals under the original 24-iteration, 1e-7 volume and 1e-9 mass-residual gates.
Their assertions remain active and unchanged. This does not qualify measured severe-slugging
period or amplitude. General phase-transition dynamics require a qualified thermodynamic pressure
closure; the local polytropic approximation is not a full real-fluid energy/EOS solve.

The coupled IMEX predictor uses centered pressure flux to avoid retaining AUSM's explicit acoustic
velocity-diffusion timestep restriction. Standalone AUSM and the legacy pipe path keep their
original split. Analytical pressure-mode damping and a constant-gradient interpolation check do
not establish general nonuniform-grid gravity well-balancing or shock-capturing accuracy.

The experimental source is [Tengesdal's 2002 BSEE report](https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf).

### Dynamic qualification scope

Numerical regression coverage includes phase-identity preservation at water-cut endpoints,
separate phase momenta, a stationary closed three-phase pressure check, accepted-time accounting,
slug merge inventory, and tracker geometry at cell midpoints. These checks address specific code
defects; they do not qualify a general operating envelope.

The legacy `SIMPLIFIED` and `LAGRANGIAN` modes remain empirical overlays. Their diagnostic inventory
accounting is separate from the Eulerian phase balance: removing a tracked slug does not inject
liquid into cells from which it was never withdrawn.

`setSlugTrackingMode(TwoFluidPipe.SlugTrackingMode.CONSERVATIVE_LAGRANGIAN)` selects predictive
liquid-continuity interface motion and conservative subcell slug/film reconstruction. It also
enables coupled pressure, interfacial pressure and adaptive stepping. For each partly occupied
cell, `SlugFilmCoupling` constructs body and film states whose weighted average equals all seven
stored conservative variables. Only the existing shared phase face flux moves physical inventory.
The tracker reads the accepted film state; geometry feeds the next face reconstruction. Oil and
water use independent donors when slip is enabled. Available phase mass, momentum and sensible
energy bound reconstruction; a marker cannot manufacture a high-holdup liquid body. Closed
boundaries constrain the external flux explicitly, and rejected steps cannot advance markers.
The timestep resolves both reconstructed velocities and phase-inventory draining: a body face
can have much greater liquid holdup than the cell average. Pressure-correction phase transfers
also enter accepted component and sensible-temperature face ledgers.

In this mode the Taylor-bubble closure supplies tail velocity and liquid continuity determines
front velocity and pickup/shedding. Empirical initiation and bubble-velocity closures remain.
Tracked phase mass/momentum/energy fields describe a partition of existing Eulerian inventory;
do not add them to pipe inventory. Legacy geometric outlet volumes remain marker diagnostics;
actual delivery is the accepted phase outlet-flux integral in `TwoFluidMassBalanceReport`.
The jump closure uses total liquid continuity; independently predictive selective oil/water
pickup still requires separate qualified closures.

Conservative interface velocities are evaluated for all markers before any marker moves, so
they use a common accepted geometry. Complete reverse exits at an open inlet remove markers
and are counted separately by `getTotalSlugsExitedAtInlet()` and `getTotalMassExitedAtInlet()`.
These are overlay diagnostics; they do not add or remove Eulerian phase inventory and do not
change downstream outlet-arrival statistics.

Register a fixed-position observation after initialization with
`SlugProbe probe = pipe.getLagrangianSlugTracker().addProbe(positionMetres)`.
`probe.drainEvents()` returns accepted front/tail passages with crossing time, direction, speed
and connected liquid-union length. Crossings are collected before merge/exit handling; endpoints
inside overlapping bodies are suppressed. Instantaneous birth and merge geometry do not invent
passages. The history is bounded, so inspect `getDroppedEventCount()` before treating it as
complete. This measures tracked marker geometry, not an independent Eulerian holdup detector.

`MohmmedSlugFlowBenchmark` reads all 75 tabulated observations from the CC BY 4.0
[Mohmmed et al. experimental dataset](https://doi.org/10.1016/j.dib.2017.11.026): 15 translation
speeds, 30 lengths and 30 frequencies. The fixture records original spreadsheet cells, missing
pointwise uncertainty, the conflicting upstream 54D/58D labels, and unspecified speed stations.
Comparison targets are fixed engineering tolerances, not measurement uncertainties. The separate
benchmark-tagged marker experiment advances geometry in prescribed flow and tests the
tail/translation closure under an explicit source-definition assumption. It does not validate
pipe pressure, film thickness, slug generation or independently measured three-phase dynamics.

`MohmmedTwoFluidPipeExperimentalBenchmarkTest` runs all 15 velocity operating points through the
actual coupled pipe and compares 42 observations: 15 speeds, 13 lengths and 14 frequencies.
The downstream probe is at 81D; upstream 54D/58D observations and three unmatched downstream
operating points are explicitly outside the simulated grid. The default 40-cell calculation uses
a five-second warm-up and ten-second observation window. Joint refinement is available through
`-Dneqsim.mohmmed.cells=80 -Dneqsim.mohmmed.outerStep=0.025` and `160`/`0.0125`, keeping the same
physical inputs, duration, random seed and targets. Run deliberately with
`-DexcludedTestGroups= -Dtest=MohmmedTwoFluidPipeExperimentalBenchmarkTest`.
The log retains every failed/missing prediction, probe event and boundary limitation. A
numerically complete calculation is not necessarily a boundary-qualified experimental match.

The earlier 2026-09-05 joint refinement study met 11/42, 3/42 and 1/42 fixed targets at
40, 80 and 160 cells respectively; 15/15, 14/15 and 13/15 cases completed the
15-second interval. All cases reported pressure-floor-limited steady initialization
and outlet phase-backflow clamping. No length target passed at any level. The
study therefore failed experimental qualification and did not establish convergence.
The prescribed-flow marker experiment met 14/15 speed targets, but that narrower
result does not qualify the coupled pipe. These outcomes use one seed and joint
spatial/outer-step refinement; they do not isolate spatial or temporal error.
After the subsequent final code review, the unchanged 40-cell screen completed all
15 cases and met 5/42 targets (2/15 speeds, 0/13 lengths, 3/14 frequencies), with
the same reported boundary limitations. The controlled marker result remained
14/15. The review fixes therefore do not demonstrate improved measured accuracy;
the 80- and 160-cell levels have not been repeated on that revision.

The seven-equation explicit total-energy flux includes kinetic enthalpy, signed gravitational
work and wall heat. However, the coupled pressure corrector changes mass/momenta without a fully
coupled local enthalpy/pressure-work redistribution, and the separate sensible-temperature update
is not an inversion of conservative total energy. Exact reconstructed or closed global energy
budgets therefore do not establish general nonisothermal acoustic qualification.

Simplified outlet statistics count the first front crossing at the physical pipe end, with the
timestamp rounded to the accepted substep end. Lagrangian outlet statistics record completed tail
exits; their reported maximum length and arrival time refer only to those exit events. These are
different event definitions and should not be mixed in slug-frequency or arrival comparisons.

Before claiming dynamic qualification, require independent pressure, holdup, phase-flow and slug
data for single-phase transients, stratified flow, hydrodynamic and terrain slugging, shutdown and
restart, and three-phase water-cut changes. Report measured uncertainty and at least three mesh
and timestep levels, together with every failed or disabled acceptance gate. An `OLGA` API name
or a synthetic benchmark fixture is not evidence of parity with a commercial simulator.

## Gaps and planned improvements

The current API is adequate for engineering studies and benchmark development. A polished
industrial report workflow should still add:

- Plot templates for pressure, temperature, holdup, water cut, flow regime, and slug events.
- Direct import of additional third-party export formats where licensing allows it.
