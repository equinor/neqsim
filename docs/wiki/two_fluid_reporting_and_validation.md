---
title: "TwoFluidPipe Reporting and Validation"
description: "How to report long multiphase flowline results from TwoFluidPipe and compare them with OLGA, LedaFlow, or field data."
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
5. If available, compare against OLGA, LedaFlow, or field data using the benchmark harness.

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
| `getHeatTransferProfile()` | W/(m2 K) | Heat-transfer coefficient profile, when configured |
| `getSurfaceTemperatureProfile()` | K | Ambient/surface temperature profile, when configured |

Example Java extraction:

```java
pipe.run();

double[] x = pipe.getPositionProfile();
double[] pressureBara = pipe.getPressureProfile();
double[] temperatureC = pipe.getTemperatureProfile("C");
double[] liquidHoldup = pipe.getLiquidHoldupProfile();
double[] waterCut = pipe.getWaterCutProfile();
double[] gasVelocity = pipe.getGasVelocityProfile();
double[] liquidVelocity = pipe.getLiquidVelocityProfile();
PipeSection.FlowRegime[] regimes = pipe.getFlowRegimeProfile();

for (int i = 0; i < x.length; i++) {
  double pBara = pressureBara[i] * 1.0e-5;
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
flow_regime
```

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

The two-fluid closure pass updates additional section-level diagnostics that are useful for model
review and validation:

| Section API | Description |
|-------------|-------------|
| `getOilWaterFlowRegime()` | Oil-water flow configuration |
| `isWaterWetting()` | Water-wetting indicator for corrosion screening |
| `isWaterDropoutRisk()` | Water dropout / accumulation risk |
| `getEntrainmentFraction()` | Estimated liquid entrainment fraction in annular/mist flow |
| `getEntrainedDropletDiameter()` | Characteristic entrained droplet diameter |
| `getSevereSluggingNumber()` | Riser-base stability indicator |
| `isSevereSlugPotential()` | Severe slugging risk flag |

These values are stored on `TwoFluidSection` objects. They are not yet exposed as top-level
`TwoFluidPipe` profile arrays, so a future report exporter should add profile accessors for them.

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
```

Example use:

```java
Path reference = Paths.get("olga_export.csv");
List<TwoFluidBenchmarkHarness.BenchmarkPoint> points =
    TwoFluidBenchmarkHarness.readCsv(reference);

TwoFluidBenchmarkHarness.Snapshot snapshot = TwoFluidBenchmarkHarness.capture(pipe);
TwoFluidBenchmarkHarness.Comparison comparison =
    TwoFluidBenchmarkHarness.compare(snapshot, points);

if (!comparison.isPassed()) {
  throw new AssertionError(comparison.failureSummary());
}
```

For transient comparisons, capture and pass a list of snapshots. The harness interpolates in both
time and position. Comparison results can be exported as CSV:

```java
String comparisonCsv = TwoFluidPipeReport.toComparisonCsv(comparison);
```

Sample corpus files are provided under
`src/test/resources/data/twofluid_benchmarks/` for OLGA export samples, LedaFlow export samples, and
field-arrival trends. These files are schema examples; replace the sample values with
project-approved exported data before using them as validation evidence.

`PipeBeggsAndBrills` should remain the steady pressure-drop correlation benchmark. For dynamic
validation, prefer OLGA/LedaFlow exports, field trends, or laboratory transient measurements because
Beggs-Brill is not a distributed transient truth model.

## Acceptance metrics by use case

Declare the comparison metric and tolerance before running a benchmark. Recommended defaults are:

| Use case | Primary metric | Typical acceptance basis |
|----------|----------------|--------------------------|
| Steady pressure-drop screening | Inlet-to-outlet pressure drop | Within agreed percent or absolute bar tolerance against `PipeBeggsAndBrills`, OLGA, LedaFlow, or field steady data |
| Arrival pressure validation | Outlet or receiving-facility pressure | Absolute bar tolerance at defined times or rates |
| Holdup profile validation | Liquid holdup versus position | Absolute holdup-fraction tolerance and trend agreement at low points/riser base |
| Slugging validation | Slug frequency, slug length, or outlet liquid-rate oscillation | Frequency/arrival-time tolerance against OLGA/LedaFlow or measured slug events |
| Liquid inventory validation | Total line liquid inventory | Percent tolerance and monotonic response during rate changes/shutdown/restart |
| Arrival time validation | Time to pressure front, thermal front, or liquid-rate response | Absolute time tolerance based on sampling interval and operational requirement |

For dynamic studies, run `initializeTransientFromSteadyState(...)` before storing the reported time
history unless the initial relaxation itself is the subject of the study.

## Reporting recommendations for long flowlines

For long oil and gas flowlines, report at least:

- Geometry and discretization: length, diameter, roughness, elevation profile, number of sections,
  mesh refinement strategy.
- Boundary conditions: inlet stream, flow rate, outlet pressure, transient changes.
- Thermodynamics: fluid model, flash interval, mass-transfer relaxation time, heat-transfer setup.
- Pressure and temperature profiles.
- Liquid holdup, water cut, phase velocity, and flow-regime profiles.
- Slug statistics and terrain low-point liquid accumulation.
- Hydrate/wax/thermal risk sections if thresholds are configured.
- Erosional velocity margin and maximum mixture velocity.
- Benchmark comparison table when OLGA, LedaFlow, or field measurements are available.
- Acceptance metric table covering pressure drop, arrival pressure, holdup profile, slug frequency,
  liquid inventory, and arrival time where relevant.

## Gaps and planned improvements

The current API is adequate for engineering studies and benchmark development. A polished
industrial report workflow should still add:

- Top-level profile accessors for closure diagnostics such as entrainment fraction, water wetting,
  and severe slugging number.
- Plot templates for pressure, temperature, holdup, water cut, flow regime, and slug events.
- Direct import of OLGA/LedaFlow export formats where licensing allows it.
