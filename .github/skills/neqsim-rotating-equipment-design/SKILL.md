---
name: neqsim-rotating-equipment-design
description: "This skill guides agents through NeqSim compressor, expander and pump performance modeling and mechanical screening. USE WHEN: loading or calibrating vendor maps, checking chart extrapolation, modeling a compressor train or turboexpander, checking pump NPSH/API 610 inputs, or screening rotating-equipment mechanics. Anchors: CompressorChart, CompressorChartCalibrator, CompressorTrain, TurboExpanderMapIngestion, PumpChart, PumpApi610DesignCalculator."
last_verified: "2026-10-03"
---

# Rotating Equipment Design

## When to use this

Use this skill when an agent needs to create, correct, apply or interpret compressor, turboexpander or pump performance maps, operating envelopes, operating profiles, or first-pass mechanical checks. Load `neqsim-compressor-antisurge-recycle` for recycle-control topology and anti-surge dynamics. This skill covers machine performance/design API and data validity, not a vendor-certified selection.

## Class map

| Class | Package | What it does | Verified API |
|---|---|---|---|
| `CompressorChart`, `CompressorChartInterface` | `neqsim.process.equipment.compressor` | Stores speed curves and retrieves head/efficiency at flow and speed | `addCurve(double,double[],double[],double[])`, `getPolytropicHead(double,double)`, `getPolytropicEfficiency(double,double)` |
| `CompressorChartReader`, `CompressorChartJsonReader` | `neqsim.process.equipment.compressor` | Loads curve data and attaches chart curves to a compressor | `setCurvesToCompressor(Compressor)` |
| `CompressorChartCalibrator`, `SafeSplineSurgeCurve`, `SafeSplineStoneWallCurve` | `neqsim.process.equipment.compressor` | Fits measured surge points and provides safe surge/stonewall curve implementations | `fitSurgeCurve(double[],double[])`, `molarMassHeadCorrectionFactor(double,double)` |
| `CompressorCurveCorrections`, `CompressorCurveTemplate`, `CompressorChartMWInterpolation` | `neqsim.process.equipment.compressor` | Applies declared curve corrections/templates or interpolates maps by gas molecular weight | `calculateReynoldsEfficiencyCorrection(double,double)`, `addMapAtMW(double,double[],double[][],double[][],double[][])` |
| `CompressorChartAlternativeMapLookup`, `CompressorChartAlternativeMapLookupExtrapolate`, `CompressorChartKhader2015` | `neqsim.process.equipment.compressor` | Alternative map lookup, explicit extrapolation variant and Khader chart form | `getPolytropicHead(...)`, `getPolytropicEfficiency(...)`, `setCurves(...)` |
| `Compressor`, `CompressorTrain` | `neqsim.process.equipment.compressor` | Core machine and a separator-compressor-aftercooler composite | `setCompressorChart(...)`, `getChartFlowStatus()`, `isChartExtrapolated()`, `getValidatedDistanceToSurge()`, `getCompressor()` |
| `OperatingEnvelope`, `CompressorThermalModel`, `StartupProfile`, `ShutdownProfile` | `neqsim.process.equipment.compressor` | Surge/stonewall boundaries, compressor heat network and speed-versus-time profiles | `getSurgeMargin(double,double,double)`, `getStonewallMargin(double,double,double)`, `getNodes()`, `getTargetSpeedAtTime(double,double)` |
| `CompressorOperatingHistory`, `CompressorWashing`, `CompressorMechanicalLosses`, `RecycleFlowCoordinator`, `AdvancedAntiSurgeControlSystem` | `neqsim.process.equipment.compressor` | History, degradation/wash, seal and bearing losses, recycle and advanced controller calculations | `getCurrentFoulingFactor()`, `getTotalMechanicalLoss()`, `getLastRecycleFlow()`, `calculateValveCommand(double,double,double)` |
| `TurboExpanderCompressor`, `MapTurboExpanderCompressor`, `Expander` | `neqsim.process.equipment.expander` | Expander or coupled expander/compressor machine and power-balance state | `getPowerExpander("kW")`, `getPowerCompressor("kW")`, `getPowerBalanceResidual()`, `getOperatingStatus()` |
| `TurboExpanderMapIngestion`, `RadialExpanderGeometryMap`, `TurboExpanderOperatingEnvelope`, `ExpanderChartKhader` | `neqsim.process.equipment.expander` | Map anchors, geometry-derived curves, operating grid and Khader chart lookup | `addAnchorPoint(String,double,double,double)`, `setReferenceFluid(SystemInterface)`, `setGrid(double[],double[])`, `getFeasibility()` |
| `Pump`, `PumpChart`, `SuckerRodPump` | `neqsim.process.equipment.pump` | Centrifugal pump thermodynamics/map; sucker-rod pump specialty model | `setOutletPressure(double)`, `getPower(String)`, `getPumpChart()`, chart `getHead(double,double)` and `getNPSHRequired(double,double)` |
| `PumpHydraulicsNpshCalculator`, `PumpApi610DesignCalculator` | `neqsim.process.mechanicaldesign.pump` | Hydraulic power/NPSH calculation and evidence-based API 610 screening | `setDutyPoint(double,double,double,double)`, `setSuctionConditions(double,double,double,double,double,double)`, `calcHydraulics()`, `getNpshAvailable()`, API 610 `calculate()`, `getChecks()` |
| `AxialThrustScreening`, `CompressorCasingDesignCalculator`, `RotorUnbalanceAssessment`, `CompressorDesignFeasibilityReport` | `neqsim.process.mechanicaldesign.compressor` | Compressor thrust, casing, unbalance and feasibility screens | `getPredictedThrust()`, `getRequiredWallThicknessMm()`, `getUnbalanceRatio()`, `getVerdict()` |
| `TurboExpanderSealGasEnvelope` | `neqsim.process.mechanicaldesign.expander` | Coupled expander/compressor seal-gas and thrust envelope | `getNetAxialThrust()`, `getThrustUtilisation()`, `getRequiredHeaterDuty()` |

### Map workflow

1. Fix a reference gas, speed, inlet basis, flow unit and head unit from the vendor source. Keep the raw points and their source alongside derived points.
2. Choose the chart class by interpolation/extrapolation behavior. Load measured curves through `CompressorChartReader`/`CompressorChartJsonReader` or use `CompressorChart.addCurve(...)`/`setCurves(...)` for explicit points.
3. Attach via `Compressor.setCompressorChart(...)`; evaluate head and efficiency at representative vendor points before using a full process train.
4. Apply only corrections supported by documented inputs. `CompressorChartCalibrator.fitSurgeCurve(...)` installs a measured surge curve; its molecular-weight method returns a correction factor, it does not alter a map by itself.
5. At every design, sweep and optimizer point, inspect `getChartFlowStatus()` and `isChartExtrapolated()`. Use `getValidatedDistanceToSurge()` where distance-to-surge is consumed by a safety/optimization decision.

Turboexpander map work is separate: create `TurboExpanderMapIngestion`, add vendor/test anchor points with velocity ratio, IGV opening and measured efficiency, configure the reference fluid and impeller diameters, then validate against the expander/compressor operating envelope. Geometry-derived maps and `ExpanderChartKhader` are correlations/models, not converted vendor data.

## Build pattern

This Java 8 example follows the `CompressorTrain` usage shown in source. It creates a simple gas feed, runs a scrubber-compressor-aftercooler unit and reads shaft power. Replace the illustrative conditions and efficiencies with a controlled design basis.

```java
import neqsim.process.equipment.compressor.CompressorTrain;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

SystemInterface gas = new SystemSrkEos(280.0, 30.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.08);
gas.addComponent("propane", 0.02);
gas.setMixingRule("classic");
Stream feed = new Stream("Feed", gas);
feed.setFlowRate(5000.0, "kg/hr");
CompressorTrain train = new CompressorTrain("HP Train", feed);
train.getCompressor().setOutletPressure(85.0);
train.getCompressor().setPolytropicEfficiency(0.76);
train.getCompressor().setUsePolytropicCalc(true);
train.getAftercooler().setOutletTemperature(308.15);
train.run();
double powerKW = train.getPower("kW");
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
Stream = jneqsim.process.equipment.stream.Stream
CompressorTrain = jneqsim.process.equipment.compressor.CompressorTrain
gas = SystemSrkEos(280.0, 30.0)
gas.addComponent("methane", 0.90)
gas.addComponent("ethane", 0.08)
gas.addComponent("propane", 0.02)
gas.setMixingRule("classic")
feed = Stream("Feed", gas)
feed.setFlowRate(5000.0, "kg/hr")
train = CompressorTrain("HP Train", feed)
train.getCompressor().setOutletPressure(85.0)
train.getCompressor().setPolytropicEfficiency(0.76)
train.getCompressor().setUsePolytropicCalc(True)
train.getAftercooler().setOutletTemperature(308.15)
train.run()
power_kw = train.getPower("kW")
```

## Result extraction

| Quantity | API / unit |
|---|---|
| Compressor chart head and efficiency | `getPolytropicHead(flow,speed)` in chart head unit; `getPolytropicEfficiency(flow,speed)` as chart value |
| Chart-range status | `getChartFlowStatus()` returns `IN_RANGE`, `EXTRAPOLATED_LOW_FLOW`, `EXTRAPOLATED_HIGH_FLOW` or `NO_CHART` |
| Validated surge distance | `getValidatedDistanceToSurge()` returns the normal distance or `Double.NaN` when the chart point is extrapolated |
| Train power | `CompressorTrain.getPower("kW")` |
| Pump map | `PumpChart.getHead(flow,speed)`, `getEfficiency(flow,speed)`, `getNPSHRequired(flow,speed)` |
| Pump NPSH/hydraulics | `PumpHydraulicsNpshCalculator.getNpshAvailable()`, `getNpshMargin()`, `getHydraulicPower()`, `getBrakePower()` |
| API 610 review | `PumpApi610DesignCalculator.getAssessmentStatus()`, `getChecks()` and per-check unit/limit/status |
| Expander/compressor power | `getPowerExpander("kW")`, `getPowerCompressor("kW")`; coupled machine residual from `getPowerBalanceResidual()` |
| Mechanical screens | Check units on each result class; examples include casing thickness mm/pressure MPa, thrust N, and unbalance g·mm |

## Gotchas

- `AxialThrustScreening` rejects non-finite inputs/results and requires a positive fitted zero-crossing for a relative margin. An invalid case is not a safe margin or `NULL` thrust. Report the data range, extrapolation and OEM thrust sign convention.
- `ValveRangeabilityScreening` rejects missing trim and non-finite travel/rangeability. Finite travel is clamped to 0–1; a zero reference flow gives an undefined ratio (`NaN`). Its flow ratio assumes unchanged fluid conditions and pressure drop, not an installed-system response.

- Chart interpolation and extrapolation are not equivalent. `CompressorChartAlternativeMapLookupExtrapolate` explicitly permits extrapolation; outside available reference speeds, it can use a single nearest curve and scale head with speed. Treat that output as extrapolated, not vendor-supported operation.
- A chart can return finite, plausible-looking values outside its calibrated flow range. `getChartFlowStatus()` may report extrapolation even at an auto-generated nominal point. Gate every sweep point; `getValidatedDistanceToSurge()` is `NaN` outside the reported valid flow range.
- `getDistanceToSurge()` alone does not enforce the chart-range gate. Do not bisect or optimize on it without filtering extrapolated points and reporting the number excluded.
- `fitSurgeCurve` fits the control/surge boundary from measured flow/head; `molarMassHeadCorrectionFactor` only returns `mwReference / mwActual`. Neither certifies the rest of a vendor map.
- Use `getAftercooler()` on `CompressorTrain`; the earlier source Javadoc example calling `getCooler()` was corrected 2026-10.
- Do not infer chart units from array names. Flow points are inlet actual m3/hr in `CompressorChartCalibrator.fitSurgeCurve`; head must match the chart head unit; verify map reader metadata and reference conditions.
- `PumpApi610DesignCalculator` compares caller-supplied duty, BEP, NPSH, pressure, driver and mechanical evidence. It does not derive missing vendor data or certify API 610 compliance.
- NPSH margin is a screening result only when suction pressure, vapor pressure, static head, duty and NPSHr provenance are correct and use compatible units.
- A turboexpander anchor-point map is only as good as its reference fluid and anchor provenance. Confirm power balance, shaft mode/speed limits, cold-end temperature and hydrate margin.
- Compressor thermal/startup/shutdown models do not by themselves establish a stable controller or machine protection function; pair with dynamic process and anti-surge workflows as needed.

## Validation / benchmarks

Use `CompressorTrainTest`, `CompressorChartGeneratorTest`, `CompressorChartSpeedInterpolationTest`, `TurboExpanderEnhancementsTest`, pump chart tests, and the corresponding design-calculator tests as API/regression evidence. Validate map values at vendor-rated points, hold out at least one point or curve, verify reference-gas conversion, and demonstrate the full intended operating envelope is in range. For API 610/API 617 screening, retain data provenance, assumptions, standard edition and unresolved checks. A generated map or a passing software test is not a vendor performance test.

## Related skills

- `neqsim-compressor-antisurge-recycle` — anti-surge recycle and minimum-speed coordination
- `neqsim-api-patterns` — compressor and pump process API patterns
- `neqsim-dynamic-equipment-implementation` — adding transient behavior to equipment
- `neqsim-troubleshooting` — operating-point and process convergence diagnostics
- `neqsim-power-generation` — gas-turbine/driver coupling
- `neqsim-dynamic-simulation` — transient controllers and process response
