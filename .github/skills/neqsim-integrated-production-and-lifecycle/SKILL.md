---
name: neqsim-integrated-production-and-lifecycle
description: "Guides integrated production and lifecycle studies using IntegratedProductionModel, WellDeliverabilityCurve, TieInCapacityPlanner, FieldLifecycleEvaluator, CashFlowEngine, and ReservoirCouplingExporter. USE WHEN: a user asks to couple well deliverability with a gathering network, match well tests, allocate limited gas lift, compare host tie-in capacity, screen lifecycle modifications or product specifications, connect reservoir production to fiscal cash flow, or export well VFP tables."
last_verified: "2026-10-03"
---

# Integrated Production and Lifecycle

Use this guide for executable APIs that sit between the broad field-development workflow and a detailed process model. The existing field-development and economics skills remain the entry point for concept framing, cost/economic assumptions, flow assurance and reporting. This guide fills in the newer integrated well/network solver, explicit tie-in capacity planning, lifecycle product checks, concept-to-process bridge and reservoir coupling export.

## When to use this

- The user needs a field-rate solution that couples simple reservoir pressure decline, precomputed well deliverability and flowline pressure loss.
- The user has measured well tests to fit into a PI or Vogel curve before a network solve.
- The user has a fixed lift-gas budget to allocate across wells using supplied lift-performance curves.
- The user needs annual base-host/satellite allocation, holdback/deferment, or process-model tie-in capacity checks.
- The user needs to determine how a lifecycle study counts deferred production, host load, facility utilization, off-spec years, or candidate modifications.
- The user needs a concept-derived process skeleton or Eclipse-style VFP keyword export, not a full reservoir simulator.

For concept screening, CAPEX/SURF cost, decline fitting, general tax/economics, standards and flow assurance, first use the existing skills in the routing table below. Keep model fidelity explicit: several classes in this area are screening surrogates, not a rigorously coupled subsurface simulator or approved design calculation.

## Class map

| Class | Package | What it does | Key methods verified |
|---|---|---|---|
| `FieldConcept`, `ReservoirInput`, `WellsInput`, `InfrastructureInput`, `DevelopmentCaseUncertainty`, `UncertaintyRange`; `FacilityBuilder`, `BlockConfig`, `FacilityConfig` | `process.fielddevelopment.concept`; `process.fielddevelopment.facility` | Defines screening concepts/uncertainty and translates concepts into modular facility-block design intent. | `FieldConcept.builder`, `FieldConcept.gasTieback`, input `builder` methods; `FacilityBuilder.forConcept`, `autoGenerate`, `addBlock`, `build` |
| `ConceptToProcessLinker` | `process.fielddevelopment.facility` | Generates a screening-to-FEED-fidelity `ProcessSystem` from a concept. | `generateProcessSystem(FieldConcept, FidelityLevel)` |
| `WellDeliverabilityCurve`, `WellTestMatcher`, `WellBranch`, `FlowlineBranch`, `NetworkNewtonSolver` | `process.fielddevelopment.integrated` | Well back-pressure curves, test fits, branch pressure-flow equations and network balance. | `fromArrays`, `fromVogel`, `fromWellSystem`, `addTestPoint`, `fitVogel`, `fitProductivityIndex`, `solve` |
| `IntegratedProductionModel`, `IntegratedSolveResult`, `ReservoirDrive`, `MaterialBalanceGasDrive`, `OilTankDrive` | `process.fielddevelopment.integrated` | Couples per-well curves and simple pressure-depletion drives to an export sink. | `addWell`, `setExportPressure`, `solve`, `runProfile`, `getFieldRate`, `getWellRates`, `getNodePressures` |
| `GasLiftNetworkOptimizer`, `GasLiftPerformanceCurve`, `ChokeAndGasLiftAllocationOptimizer` | `process.fielddevelopment.integrated` | Allocates gas lift from supplied response curves, optionally with choke decisions. | `addWell`, `allocate`, `setOilPrice`, `optimize` |
| `TiebackAnalyzer`, `HostFacility`, `TiebackOption`, `TiebackReport` | `process.fielddevelopment.tieback` | Screens candidate tieback configurations against a host and supplied fluid. | `HostFacility.builder`, `TiebackAnalyzer.analyze` |
| `TieInCapacityPlanner`, `HostTieInPoint`, `ProductionProfileSeries`, `ProductionLoad`, `TieInCapacityResult`, `DebottleneckDecision` | `process.fielddevelopment.tieback.capacity` | Compares host nameplate and optionally live process constraints over time. | `setHostProductionProfile`, `setSatelliteProductionProfile`, `setTieInPoint`, `setAllocationPolicy`, `run`, `getPeriodResults` |
| `FieldLifecycleConcept`, `FieldLifecycleModel`, `FieldLifecycleConfiguration`, `FacilityLifecycleStrategy`, `FieldLifecycleEvaluator`, `FacilityCapacityAllocator` | `process.fielddevelopment.lifecycle` | Runs annual reservoir/process production and brownfield/greenfield capacity allocation. | `FieldLifecycleModel.builder`, `FacilityLifecycleStrategy.tieback`, `allocate`, `evaluate` |
| `FieldLifecycleResult`, `FacilityModificationPlanner`, `FacilityModificationPlan`, `FacilityDesignResult`, `FacilityProductionRate` | `process.fielddevelopment.lifecycle` | Reports annual/economic lifecycle results and design/capacity outcomes; creates candidate modification scopes. | `getNpvMusd`, `getStopReason`, `analyse(FieldLifecycleResult, double)`, `hasCandidates`, `getDevelopmentMode`, `getAutoSizedEquipmentCount` |
| `FieldProductSpecifications`, `ProductSpecificationEvaluator`, `ProductSpecificationResult`, `FieldProductQualityProvider` | `process.fielddevelopment.lifecycle` | Evaluates configured gas, oil and produced-water quality limits. | `FieldProductSpecifications.builder`, `evaluate`, `isEvaluated`, `isCompliant`, `getViolations` |
| `CashFlowEngine`, `FiscalParameters`, `TaxModelRegistry`, `CommodityFeeSchedule`, `PortfolioOptimizer`, `SensitivityAnalyzer` | `process.fielddevelopment.economics` | Configurable cash flow, tax-model selection, fees and economic sensitivities. | `setCapex`, `addAnnualProduction`, `calculate`, `TaxModelRegistry.getModel`, `FiscalParameters.builder` |
| `ReservoirCouplingExporter`, `InjectionWellModel`, `TransientWellModel` | `process.fielddevelopment.reservoir` | Exports well VFP tables/schedule keywords and provides reduced-order well/injection coupling utilities. | `setPressureRange`, `setRateRange`, `generateVfpProd`, `getEclipseKeywords`, `exportToFile`, `calculate` |
| `SimpleReservoir`, `WellSystem`, `TubingPerformance`, `ReservoirCVDsim`, `ReservoirDiffLibsim`, `ReservoirTPsim`, `ReservoirSurveillance`, `AnnularLeakagePath`, `CementDegradationModel`, `InjectionConformanceMonitor` | `process.equipment.reservoir`; `process.equipment.well` | Existing equipment-level reservoir, well, surveillance and integrity utilities. | Use the specific equipment API and tests; do not infer full-field simulator behavior from these names. |
| `ConceptEvaluator`, `BatchConceptRunner`, `BottleneckAnalyzer`, `DevelopmentOptionRanker`, `ScenarioAnalyzer`, `DecommissioningEstimator`, `EnvironmentalReporter`, `ProductionAllocator`, `SeparatorSizingCalculator`, `FieldDevelopmentReportExporter` | `process.fielddevelopment.evaluation`; `process.fielddevelopment.reporting` | Evaluates/ranks concepts and reports separator, environmental and decommissioning results. | `evaluate`, `runAll`, `DevelopmentOptionRanker.rank`, `allocate`; see each class API for its task-specific arguments. |
| `FieldProductionScheduler`, `WellScheduler`, `FacilityCapacity`, `ProductionProfile`, `DCFCalculator`, `SensitivityAnalysis` | `process.util.fielddevelopment` | Lower-level schedules, profile constraints, DCF and sensitivity utilities. | Use the existing production-optimization/economics guides for the general recipes. |

### Routing table

| Question | Class/API to route to | Existing skill that covers the adjacent workflow |
|---|---|---|
| Which development concept, fluid assumptions and screening stage? | `FieldConcept`, `ConceptEvaluator`, `BatchConceptRunner` | `neqsim-field-development` |
| Which process blocks should represent the concept? | `FacilityBuilder.autoGenerate`, `ConceptToProcessLinker.generateProcessSystem` | `neqsim-field-development`, `neqsim-process-modeling` |
| How do well rates respond to wellhead pressure and network backpressure? | `WellDeliverabilityCurve`, `IntegratedProductionModel.solve` | `neqsim-production-optimization`, `neqsim-process-modeling` |
| Can observed well tests reproduce a deliverability curve? | `WellTestMatcher.fitVogel` or `fitProductivityIndex` | `neqsim-production-optimization` |
| How should a fixed lift-gas budget be shared? | `GasLiftNetworkOptimizer.allocate` | `neqsim-production-optimization` |
| Which host admits the satellite profile and how much is held back? | `TieInCapacityPlanner.run`, `HostFacility` | `neqsim-subsea-and-wells`, `neqsim-field-development` |
| How does host/process loading, product quality and deferred oil evolve by year? | `FieldLifecycleEvaluator.evaluate`, `ProductSpecificationEvaluator.evaluate` | `neqsim-field-development`, `neqsim-field-economics` |
| What candidate modification follows lifecycle utilization? | `FacilityModificationPlanner.analyse` | `neqsim-field-development`, `neqsim-agentic-process-optimization` |
| How do configurable fiscal parameters affect cash flow? | `CashFlowEngine`, `TaxModelRegistry`, `FiscalParameters` | `neqsim-field-economics` |
| Which well tables/keywords can be passed to a reservoir simulator? | `ReservoirCouplingExporter` | `neqsim-production-optimization`, `neqsim-e300-fluid-io` |

## Build pattern

The compact example below exercises a deterministic, single-well reservoir-to-export solve. Pressure is bara; deliverability is surface rate in Sm3/day. The curve is a Vogel-like screening curve, and the drive is a material-balance surrogate. Check `isConverged()` before using any result.

```java
import neqsim.process.fielddevelopment.integrated.IntegratedProductionModel;
import neqsim.process.fielddevelopment.integrated.IntegratedSolveResult;
import neqsim.process.fielddevelopment.integrated.MaterialBalanceGasDrive;
import neqsim.process.fielddevelopment.integrated.WellDeliverabilityCurve;

public class FieldNetworkExample {
  public static void main(String[] args) {
    IntegratedProductionModel model = new IntegratedProductionModel("Gas field");
    model.setExportPressure(40.0);
    model.setHydrocarbonPrice(0.25);
    model.setEnergyIntensity(0.02);
    model.setEmissionIntensity(0.04);
    model.addWell("W-1", new MaterialBalanceGasDrive(250.0, 5.0e9, 0.90),
        WellDeliverabilityCurve.fromVogel(2.0e6, 250.0));

    IntegratedSolveResult result = model.solve();
    if (!result.isConverged()) {
      throw new IllegalStateException("Network solve did not converge: " + result.getMethod());
    }
    System.out.println("Field rate Sm3/day: " + result.getFieldRate());
    System.out.println("Wells: " + result.getWellRates());
  }
}
```

Equivalent class lookup from Python via JPype:

```python
from neqsim import jneqsim

IntegratedProductionModel = jneqsim.process.fielddevelopment.integrated.IntegratedProductionModel
MaterialBalanceGasDrive = jneqsim.process.fielddevelopment.integrated.MaterialBalanceGasDrive
WellDeliverabilityCurve = jneqsim.process.fielddevelopment.integrated.WellDeliverabilityCurve

model = IntegratedProductionModel("Gas field")
model.setExportPressure(40.0)
model.setHydrocarbonPrice(0.25)
model.setEnergyIntensity(0.02)
model.setEmissionIntensity(0.04)
drive = MaterialBalanceGasDrive(250.0, 5.0e9, 0.90)
curve = WellDeliverabilityCurve.fromVogel(2.0e6, 250.0)
model.addWell("W-1", drive, curve)
result = model.solve()
if not result.isConverged():
    raise RuntimeError("Network solve did not converge: " + str(result.getMethod()))
print(result.getFieldRate(), result.getWellRates())
```

To bridge a concept to a process skeleton, `FieldConcept.gasTieback("name", tiebackKm, wellCount, ratePerWellMSm3d)` creates inputs, and `new ConceptToProcessLinker().generateProcessSystem(concept, ConceptToProcessLinker.FidelityLevel.SCREENING)` creates a process model. `FacilityBuilder.autoGenerate(concept).build()` creates a `FacilityConfig`; it does not itself instantiate every config block into validated equipment. The linker also creates representative fluid/process inputs, so replace those with project fluid and measured data before design use.

For lifecycle work, build a `FieldLifecycleConcept` using `NorwegianOilFieldCase` examples as patterns or assemble `FieldLifecycleModel` and `FieldLifecycleConfiguration` directly; evaluate with `new FieldLifecycleEvaluator().evaluate(concept)`. The tested lifecycle path supports user-supplied production-potential providers and existing `ProcessSystem`/multi-area `ProcessModel` mappings. For VFP export, instantiate `ReservoirCouplingExporter(process)`, configure pressure/rate ranges, call `generateVfpProd(wellName, fluid, tableNumber)`, then retrieve `getEclipseKeywords()`.

## Result extraction

- `IntegratedSolveResult.isConverged()` and `getIterations()` report solve state and iteration count; `getMethod()` reports the solver method.
- `getFieldRate()` and `getWellRates()` are Sm3/day; `getNodePressures()` is bara. `getRevenue()` is the configured price multiplied by daily rate, in the caller's price-derived currency/day. `getEnergyKWhPerDay()` is kWh/day and `getEmissionsKgPerDay()` is kg/day, both based only on configured intensities.
- `FieldLifecycleResult.getAnnualResults()` returns annual records. The nested annual values include oil/export-gas/injection/water volumes in Sm3, annual energy in MWh, CO2 in tonnes, annual rate fields in Sm3/day and `getMaximumFacilityUtilization()` as a fraction. `getNpvMusd()`, `getIrr()`, `getPaybackYears()`, `getBreakevenOilPriceUsdPerBbl()` and `getBreakevenGasPriceUsdPerSm3()` expose economic outputs.
- Product quality is in `ProductSpecificationResult`: gas CO2/O2 mole percent, H2S ppm, dew points °C, GCV/Wobbe MJ/Sm3, oil RVP bara, oil BSW volume percent and oil-in-water mg/L. Use `isEvaluated()`, `isCompliant()` and `getViolations()`; unevaluated is not a pass.
- `TieInCapacityResult` exposes period results and accepted/held-back volumes. Gas totals are MSm3, oil totals are bbl and water/liquid totals are m3; periods also expose their named bottleneck and deferred-value MUSD.
- `CashFlowEngine.calculate(discountRate)` returns `CashFlowResult`; its `getNpv()` is MUSD, `getIrr()` a fraction, and `getPaybackYears()` years. Input units differ by commodity: oil barrels, gas Sm3 and NGL barrels per year; CAPEX and cash-flow amounts are MUSD.
- `ReservoirCouplingExporter.getEclipseKeywords()` returns a text keyword block. `VfpTable.getFlowRates()` is Sm3/day, `getThpValues()` bara, `getWctValues()` fraction and datum depth m.

## Gotchas

- `IntegratedProductionModel` is not the full `FieldLifecycleModel`; it couples a simple `ReservoirDrive`, well-curve surrogate and branch network. `MaterialBalanceGasDrive` and `OilTankDrive` are simplified depletion models, not a compositional reservoir simulator.
- `WellDeliverabilityCurve.fromVogel` is an analytic Vogel-like shape. `fromWellSystem` samples a configured `WellSystem`; failed/nonphysical samples are clamped to zero and sampled rates are forced non-increasing with pressure. Check the source data and fit envelope.
- `runProfile(years, dtYears)` records `ceil(years/dtYears)+1` points (t = 0 to the endpoint); depletion and cumulative production cover exactly `years` (the extra post-endpoint step was removed 2026-10).
- `TieInCapacityPlanner`'s nameplate-only mode is a rate-allocation screen. Process-aware mode needs a real `HostFacility` process/model plus a correct `HostTieInPoint`; `ProcessModel` stream names should be area-qualified, such as `gathering::Host Feed`, when duplicates exist. Planner tests confirm the base host process is restored after trial loads.
- Lifecycle modification plans identify candidate scope; they do not size or cost a real equipment modification. Rebuild/rerun the facility and add verified modification CAPEX before claiming production or NPV improvement.
- Concept-generated fluids, equipment sizing and product limits are only as strong as the supplied inputs and chosen model. `CashFlowEngine` fiscal behavior is selected/configured; do not treat built-in defaults as current tax advice or infer a jurisdiction's rates from this guide.
- `ReservoirCouplingExporter` emits coupling tables/keywords; it does not run ECLIPSE, INTERSECT, OPM Flow or a full reservoir simulator.

## Validation / benchmarks

- `IntegratedProductionTest` covers curve monotonicity/interpolation, single-well solve, reservoir-drive depletion, gas-lift budget, PI/Vogel test matching, field solve, declining profile and capacity-limited optimization. Reuse its deterministic `fromVogel`/`fromArrays` fixtures for a quick model check.
- `TieInCapacityPlannerTest` covers base-first and pro-rata policy, deferred backlog, process-capacity injection, multi-area qualified tie-ins and restoration of the operating point.
- `NorwegianOilFieldLifecycleTest` is tagged slow and covers lifecycle economics, product specs, facility capacity, host tieback, modification candidates and multi-area process linking.
- A valid network result must converge, keep rates nonnegative, and satisfy rate consistency: field rate is the sum of per-well rates. For a profile, compare recorded rates and time-step integration separately; do not accept cumulative production solely because the run returned.
- Independent reservoir/well or fiscal benchmark data is still required for calibration. Do not call a model validated because its internal unit tests pass.

## Related skills

- `neqsim-field-development` — lifecycle framing, concept selection and integrated field workflows.
- `neqsim-field-economics` — cost, cash-flow, tax, NPV/IRR, uncertainty and sensitivity assumptions.
- `neqsim-production-optimization` — production profiles, decline analysis and reservoir surveillance.
- `neqsim-subsea-and-wells` — well design, SURF costs and tieback/host context.
- `neqsim-agentic-process-optimization` — closed-loop optimization of an already-built process model.
- `neqsim-process-modeling` — detailed flowsheet construction and convergence checks.
- `neqsim-e300-fluid-io` — Eclipse E300 fluid import/export; distinct from VFP table generation.