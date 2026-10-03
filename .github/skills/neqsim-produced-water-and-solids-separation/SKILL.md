---
name: neqsim-produced-water-and-solids-separation
description: "Guides agents through NeqSim produced-water treatment and solids separation. USE WHEN: users ask to screen produced-water oil removal, hydrocyclone or flotation performance, solids split/dewatering, filtration pressure drop, crystallization, liquid-liquid extraction, water degassing, or pipeline evaporation/dissolution. Anchors: ProducedWaterTreatmentTrain, Hydrocyclone, GasFlotationUnit, SolidsSeparator, Filter, Crystallizer, ProducedWaterDegassingSystem."
last_verified: "2026-10-03"
---

# Produced Water and Solids Separation

## When to use this

- Screen a produced-water treatment train, oil-in-water outlet, or discharge volume.
- Size/check hydrocyclone liners, differential pressure, turndown, or droplet response.
- Represent gas flotation, solids splitting, centrifuge dewatering, filter pressure drop, or sulfur removal.
- Evaluate crystallization, liquid-liquid extraction, cryogenic separation, or end-flash where those unit models fit the stated process.
- Evaluate gas/liquid mass transfer over a pipeline with the evaporation/dissolution studies.
- Estimate dissolved-gas release from produced water with the degassing utility.

This skill owns separation and treatment equipment. It does not own chemical selection or dose optimization. Route demulsifier dose-response, lag, compatibility, and recommended dose to `neqsim-production-chemistry`; reuse its result rather than repeating that model here. Build ion composition and scale chemistry with `neqsim-electrolyte-systems`.

## Class map

| Class family | Package | What it does | Verified entry points / result methods |
|---|---|---|---|
| Produced-water stage screen: `ProducedWaterTreatmentTrain`, `WaterTreatmentStage` | `neqsim.process.equipment.watertreatment` | Applies stated fractional stage efficiencies and reports a screening OiW concentration, recovery, and discharge status. | `setInletOilConcentration`, `setWaterFlowRate`, `addStage`, `run`, `getOilInWaterMgL`, `getOverallEfficiency`, `isDischargeCompliant` |
| Hydrocyclone: water-treatment `Hydrocyclone` | `neqsim.process.equipment.watertreatment` | Estimates liner capacity, d50/droplet response, DP adequacy, design utilization, and oil-in-water performance. | `calcNumberOfLiners`, `calcD50FromConditions`, `estimateEfficiencyFromConditions`, `getEfficiencyForDropletSize`, `getOutletOilMgL`, `getRejectFlowM3h`, `getDesignValidationSummary` |
| Gas flotation: `GasFlotationUnit` | `neqsim.process.equipment.watertreatment` | Separator-derived flotation stage with configurable stages and performance inputs. | `new GasFlotationUnit(name, inletStream)`, `setNumberOfStages`, `getOilRemovalEfficiency` |
| OiW measurement/compliance: `OilInWaterAnalyzerDriftModel`, `OilInWaterMonthlyComplianceMonitor` | `neqsim.process.equipment.watertreatment` | Simulates analyzer bias/drift/noise and water-volume-weighted monthly sample status. | `measure`, `correctMeasuredValue`, `measureConservative`, `isCalibrationDue`, `addSample`, `calculateStatus`, `getWeightedAverageMgL` |
| Solid phase splitting: `SolidsSeparator`, `SolidsCentrifuge` | `neqsim.process.equipment.separator` | Separates modeled solid components and reports solids/liquid outlets; centrifuge exposes g-force-based inputs. | `setSolidsSplitFraction`, `setDefaultSolidsSplit`, `getSolidsOutStream`, `getLiquidOutStream`, `SolidsCentrifuge.setGForce` |
| Mechanical dewatering: `RotaryVacuumFilter`, `PressureFilter`, `ScrewPress` | `neqsim.process.equipment.separator` | Represents pressure/vacuum filtration and screw pressing with equipment-specific operating inputs. | `setVacuumPressure`, `setFilterArea`, `setOperatingPressure`, `setScrewSpeed`, `setCompressionRatio` |
| Filter service and curves: `Filter`, `CharCoalFilter`, `SulfurFilter`, `FilterPerformanceCurve`, `FilterPressureDropCurve` | `neqsim.process.equipment.filter` | Applies pressure-drop and particle-removal curves; sulfur filter reports sulfur removal and change interval. | `setDeltaP`, `setPressureDropModel`, `setPoints`, `getRemovalEfficiency`, `getPressureDrop`, `getSolidSulfurRemovalRate` |
| Crystallization/extraction: `Crystallizer`, `LiquidLiquidExtractor` | `neqsim.process.equipment.separator` | Produces crystal/mother-liquor streams or extract/raffinate streams from configured unit inputs. | `setSolidRecovery`, `setTargetSolute`, `getCrystalStream`, `getMotherLiquorStream`, `setNumberOfStages`, `getExtractStream`, `getRaffinateStream` |
| Adjacent separator duties: `TwoPhaseSeparator`, `CryogenicSeparator`, `EndFlash`, `SeparatorCapacityAssessment` | `neqsim.process.equipment.separator` | Handles gas/liquid separation, cryogenic impurity limits/end flash, and explicit capacity assessment. | `setMaxCO2MolFrac`, `setMaxWaterPpm`, `getN2InLNGMolFrac`, `SeparatorCapacityAssessment.getStatus`, `getDiagnostic` |
| Pipeline phase transfer: `PipelineEvaporationStudy`, `PipelineDissolutionStudy` | `neqsim.process.equipment.pipeline.evaporation` | Integrates component transfer and heat balance along a configured pipe length. | Constructors take `(SystemInterface, PipelineEvaporationConfig)`; inspect `PipelineEvaporationResult.getProfile` and balance/convergence fields |
| Produced-water gas release: `ProducedWaterDegassingSystem` | `neqsim.process.equipment.util` | Screens dissolved-gas release from water using explicit flow, temperature, inlet, and degasser pressure inputs. | `setWaterFlowRate(value, unit)`, `setWaterTemperature(value, unit)`, `setInletPressure(value, unit)`, `setDegasserPressure(value, unit)` |

## Build pattern

`ProducedWaterTreatmentTrain` is a deterministic screening stage model. The stage efficiencies are user inputs as fractions from 0 to 1; they are not derived from hydrocyclone geometry. The example's flow and inlet OiW data are explicit.

```java
ProducedWaterTreatmentTrain train = new ProducedWaterTreatmentTrain("PW train");
train.setWaterFlowRate(416.67); // m3/h
train.setInletOilConcentration(1000.0); // mg/L
train.addStage(new WaterTreatmentStage("primary", StageType.HYDROCYCLONE, 0.95));
train.addStage(new WaterTreatmentStage("secondary", StageType.FLOTATION, 0.80));
train.run();

double outletOiwMgL = train.getOilInWaterMgL();
boolean withinBuiltInLimit = train.isDischargeCompliant();
```

Equivalent Python class lookup:

```python
from neqsim import jneqsim

ProducedWaterTreatmentTrain = jneqsim.process.equipment.watertreatment.ProducedWaterTreatmentTrain
StageType = ProducedWaterTreatmentTrain.StageType
WaterTreatmentStage = ProducedWaterTreatmentTrain.WaterTreatmentStage

train = ProducedWaterTreatmentTrain("PW train")
train.setWaterFlowRate(416.67)  # m3/h
train.setInletOilConcentration(1000.0)  # mg/L
train.addStage(WaterTreatmentStage("primary", StageType.HYDROCYCLONE, 0.95))
train.addStage(WaterTreatmentStage("secondary", StageType.FLOTATION, 0.80))
train.run()
outlet_oiw_mg_l = train.getOilInWaterMgL()
within_built_in_limit = train.isDischargeCompliant()
```

### Analyzer and monthly-monitor pattern

Use the analyzer model to expose measurement bias and drift, then pass a corrected or conservative measured value and its sampled water volume to the monthly monitor. This is a monitoring calculation, not a chemical-dose recommendation.

```java
OilInWaterAnalyzerDriftModel analyzer = new OilInWaterAnalyzerDriftModel();
double measuredMgL = analyzer.measure(25.0, 10.0);
double correctedMgL = analyzer.correctMeasuredValue(measuredMgL, 10.0);

OilInWaterMonthlyComplianceMonitor monitor = new OilInWaterMonthlyComplianceMonitor();
monitor.setMonthlyLimitMgL(30.0);
monitor.setProjectedDailyWaterVolumeM3(1000.0);
monitor.addSample(correctedMgL, 1000.0);
OilInWaterMonthlyComplianceMonitor.MonthlyStatus status = monitor.calculateStatus(10);
double weightedMeanMgL = monitor.getWeightedAverageMgL();
```

### Select the separation model

- Use the treatment train when the available basis is inlet OiW, water flow, and stated stage efficiencies.
- Use the hydrocyclone equipment when feed stream phases, DP, liner count, d50, and outlet response are part of the question.
- Use `SolidsSeparator` for component-level solid splits; do not infer a particle-size cut from a split fraction.
- Use filter curves when particle-size capture or flow-dependent pressure drop is known from the selected element/test data.
- Use the pipeline evaporation/dissolution studies only when the inlet has the required gas/liquid phase basis and transfer geometry.

For a mechanistic hydrocyclone case, use the water-treatment class with an inlet stream: `new Hydrocyclone(name, inletStream)`, set/verify pressure drop and feed conditions, run it, and inspect both `getDesignValidationSummary()` and the calculated outlet. Do not confuse it with `neqsim.process.equipment.separator.Hydrocyclone`, which is a distinct separator-package class.

## Result extraction

- `ProducedWaterTreatmentTrain.getOilInWaterMgL()` and `getOilInWaterPpm()` return **mg/L** and ppm respectively; the class documents them as numerically equivalent for water.
- `getOverallEfficiency()` is a fraction from 0 to 1; `getRecoveredOilM3h()` is **m3/h**; `getAnnualOilDischargeTonnes(hours)` returns **tonnes** for the supplied annual operating hours.
- `isDischargeCompliant()` checks the class's built-in `NCS_OIW_LIMIT_MGL` of 30 mg/L. Use `isCompliantWith(limitMgL)` for a different declared limit; do not present the built-in default as a project-specific permit.
- Water-treatment `Hydrocyclone.getD50Microns()` and `getEfficiencyForDropletSize(microns)` use **microns**; `getPressureDropBar()` is **bar**; capacity and reject-flow getters are **m3/h**; `getOutletOilMgL()` is **mg/L**.
- `OilInWaterMonthlyComplianceMonitor.getWeightedAverageMgL()` is a water-volume-weighted **mg/L** result. Preserve sample volume and status, not only the mean.
- `Filter.getDeltaP()` returns the internal pressure-drop value in **bar**. `setDeltaP(value, unit)` converts a differential-pressure unit to bar; filter curve methods take particle size in micrometres and flow in m3/hr as named by their parameters.
- `PipelineEvaporationResult.getProfile()` contains distance-indexed profile points; check completion, component balance, energy balance, and warnings before reporting transfer distance.

## Gotchas

- The stage train computes sequential remaining concentration as `Cout = Cin * (1 - efficiency)` and expects each efficiency in **0-1**, not percent. It uses a default oil density of 850 kg/m3 for recovered-oil estimates.
- The train's `getOverallEfficiency()` divides by inlet OiW. A zero inlet concentration can yield `NaN`; set a positive, measured inlet basis and guard zero before reporting efficiency.
- When an inlet stream is supplied, the train reads aqueous volume and oil mass for its calculation but creates outlet streams by cloning the inlet fluid. Those streams are not a component-by-component physical separation result; do not use them to claim a closed process mass balance.
- The default discharge threshold is a code default, not a complete permit. Monthly weighted monitoring and conservative analyzer readings are evidence models, not laboratory certification.
- The `watertreatment.Hydrocyclone` and `separator.Hydrocyclone` names refer to different classes. State the package whenever you select one.
- `SolidsSeparator` split fractions must be within 0 and 1. Filter performance-curve points require matching non-empty arrays, increasing particle sizes, and beta ratios >= 1; pressure-drop curve flow points must increase and pressure drops must be non-decreasing.
- `PipelineEvaporationStudy` requires an explicit gas and liquid phase, gas in phase 0, and a supported dispersed phase. It rejects missing/invalid phases and reports energy-balance convergence failures; do not hide those warnings.
- No `validateSetup()` override was found in the water-treatment/filter/evaporation classes covered here. Use the Hydrocyclone design summary, validated setters, and explicit stream/phase/balance checks instead.
- Demulsifier dose response, dose lag, and dose recommendation belong to `neqsim-production-chemistry`; this skill does not prescribe chemical dosage.

## Validation / benchmarks

- `ProducedWaterTreatmentTrainTest` checks sequential OiW reduction, compliance, conversion, annual discharge, and reporting. `WaterTreatmentEquipmentTest` checks hydrocyclone d50, DP, capacity, and droplet-size response.
- `OilInWaterDecisionSupportTest` covers analyzer drift and volume-weighted monthly monitoring; its dose-response and dose-optimizer cases are owned by `neqsim-production-chemistry`.
- For any case, compare inlet/outlet concentration with measured data, check water throughput and operating time, inspect separator/filter capacity and pressure drop, and distinguish model status from permit acceptance.
- These models do not replace vendor guarantees, particle-size distribution testing, detailed solids rheology, discharge permitting, or site-specific equipment design. Several are screening or empirical models with declared inputs.

## Related skills

- `neqsim-production-chemistry` — demulsifier and other chemical dose/compatibility workflows; do not duplicate dose calculations here.
- `neqsim-electrolyte-systems` — electrolyte fluid setup, brine chemistry, and scale prediction.
- `neqsim-process-modeling` — process stream/equipment wiring and mass-balance checks.
- `neqsim-flow-assurance` — pipeline threats, scale deposition, and transport context.
- `neqsim-separator-modelling` — general separator gas/liquid capacity screening.