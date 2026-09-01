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
  System.out.printf("%8.1f,%10.4f,%8.3f,%8.5f,%8.5f,%8.3f,%8.3f,%s%n",
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

For transient cases, call `runTransient(dt, id)` repeatedly and store a snapshot at the reporting
interval required by the study. Do not store every internal sub-step for long pipelines unless
high-frequency pressure waves or slug arrivals are being investigated.

```java
UUID id = UUID.randomUUID();
double dt = 1.0;
int reportEvery = 10;

for (int step = 0; step < 3600; step++) {
  pipe.runTransient(dt, id);

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

The phase-resolved closure conserves bulk phase inventories, but it does not yet transport a full
component-composition vector in every hydrodynamic cell. Do not interpret it as commercial-simulator
equivalence or use total-mass closure alone as validation of liquid identity.

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
Path reference = Path.of("reference_export.csv");
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

`SevereSluggingExperimentalBenchmarkTest` reproduces large-facility Test 3 with the physical
flowline/riser geometry and RK4 integration. Severe slugging here is a deterministically chaotic
limit cycle: a relative inlet-pressure perturbation of 1e-12, far below any experimental
significance, changes the peak-to-peak riser-base pressure by more than a factor of two and the
apparent period by more than a factor of 1.5. The benchmark therefore evaluates a four-member
ensemble (12 and 16 sections, 0.1 and 0.2 s outer steps, and a perturbed trajectory) and separates
reproducible from trajectory-sensitive evidence.

Reproducible across the ensemble: phase-resolved and total mass closure below 1e-15, a
time-averaged riser-base pressure of 171-176 kPa that agrees within 4% across mesh, outer step and
perturbation, and an outlet liquid rate that blows out above and falls back below the liquid feed
rate in every realization.

Trajectory-sensitive and reported as a range only: peak-to-peak pressure 42-300 kPa versus
98 +/- 5 kPa digitized, apparent cycle period 14-35 s versus 38 +/- 2 s measured, and a maximum
tracked outlet slug of 1.5-4.9 m (0.10-0.33 riser heights), below the experimental severe-slug
definition. The modelled pressure swing brackets the measured amplitude, but the period is
systematically short. These residuals must accompany any reported severe-slugging result; they are
not hidden by a pass/fail summary.

The benchmark disables the steady-state wall-clock guard and asserts it did not fire, so results do
not depend on how fast the executing machine is.

The source is S. Tengesdal's 2002 BSEE report:
https://www.bsee.gov/sites/bsee.gov/files/tap-technical-assessment-program/397aa.pdf.

## Gaps and planned improvements

The current API is adequate for engineering studies and benchmark development. A polished
industrial report workflow should still add:

- Plot templates for pressure, temperature, holdup, water cut, flow regime, and slug events.
- Direct import of additional third-party export formats where licensing allows it.
