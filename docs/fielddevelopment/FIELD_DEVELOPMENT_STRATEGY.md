---
title: NeqSim Field Development Strategy
description: This document outlines a comprehensive plan to transform NeqSim into a premier tool for **field development screening**, **production scheduling**, **tie-back analysis**, and **new development plannin...
---

# NeqSim Field Development Strategy

## Executive Summary

This document outlines a comprehensive plan to transform NeqSim into a premier tool for **field development screening**, **production scheduling**, **tie-back analysis**, and **new development planning**. The strategy builds on existing NeqSim strengths (thermodynamics, process simulation) while adding high-level orchestration capabilities.

---

## Book-Focused Roadmap Update (May 2026)

The TPG4230 field-development book now has direct NeqSim API support for the core examples it discusses: tieback screening, greenfield option comparison, host capacity checks, forecast uncertainty, and concept economics. The first implementation step focused on making existing NeqSim functionality easier to compose instead of adding a parallel study framework.

### Implemented Support

| Book workflow | NeqSim API support | Status |
|---------------|--------------------|--------|
| Tieback hydraulics and flow assurance | `TiebackAnalyzer` now configures `MultiphaseFlowIntegrator` and `FlowAssuranceScreener`, storing arrival pressure, arrival temperature, flow regime, hydrate formation temperature, hydrate margin, and shutdown cooldown risk on `TiebackOption`. | Implemented |
| Host capacity for brownfield tiebacks | `HostFacility.assessCapacity(...)` returns a `HostCapacityReport` covering gas, oil, water, liquid spare capacity and optional `ProcessSystem` bottleneck screening through `BottleneckAnalyzer`. | Implemented |
| Production forecasts for concept KPIs | `ConceptEvaluator` now derives field life and recovery from `ProductionProfileGenerator` and `ReservoirInput` resource/recovery assumptions instead of fixed placeholders. | Implemented |
| Sensitivity and Monte Carlo economics | `CashFlowEngine.copy()` preserves production profiles, CAPEX timing, tariffs, prices, OPEX, and tax configuration; `SensitivityAnalyzer` uses that copy for tornado, scenario, and Monte Carlo cases. | Implemented |
| Greenfield and tieback templates | `GreenfieldConceptFactory` creates comparable `DevelopmentCaseTemplate` objects for subsea tieback, standalone FPSO, fixed platform, subsea-to-shore, onshore terminal, and phased brownfield expansion. | Implemented |
| Book-ready examples | `docs/fielddevelopment/README.md` uses current APIs and includes a template comparison example verified by `DocExamplesCompilationTest`. | Implemented |
| Multi-segment route networks | `TiebackRouteNetwork` represents flowlines, branches, risers, shared corridors, and host hubs. `TiebackAnalyzer` accepts route networks and stores installed length, shared corridor length, branch/riser counts, and route summaries on `TiebackOption`. | Implemented |
| Probabilistic template assumptions | `DevelopmentCaseTemplate` now carries `DevelopmentCaseUncertainty` with P10/P50/P90 resource, CAPEX, schedule, price, and production-factor ranges. `ReservoirInput` supports resource uncertainty directly. | Implemented |
| Lifecycle emissions | `EmissionsTracker.estimateLifecycle(...)` generates year-by-year emissions tied to production decline, compression load, power source, flaring, fugitives, and vented CO2; templates carry a `LifecycleEmissionsProfile`. | Implemented |
| Reservoir/profile coupling | `ProductionProfileGenerator` now creates resource-capped profiles from `ReservoirInput` and `SimpleReservoir`, fits exponential decline from production history, and exports VFP-ready annual rate tables. | Implemented |
| Book/report tables | `FieldDevelopmentReportExporter` generates standard markdown comparison tables and figure-ready NPV data from tieback reports, templates, tornado results, and concept KPIs. | Implemented |

### Teaching Workflow Status

The TPG4230 book improvement has packaged NeqSim as three executable teaching workflows:

| Workflow | What the notebook should demonstrate | Primary APIs |
|----------|--------------------------------------|--------------|
| Tieback Screening Notebook | Define a satellite discovery, candidate hosts, route length/depth, representative fluid, hydraulics, flow assurance, host capacity, NPV, and feasibility ranking. | `TiebackAnalyzer`, `TiebackReport`, `HostFacility`, `MultiphaseFlowIntegrator`, `FlowAssuranceScreener` |
| Tieback vs Standalone Notebook | Compare host tieback, standalone FPSO, and fixed-platform alternatives using one production profile, CAPEX class, power/emissions basis, and tax model. | `GreenfieldConceptFactory`, `DevelopmentCaseTemplate`, `EconomicsEstimator`, `EmissionsTracker`, `CashFlowEngine` |
| Probabilistic Concept Selection Notebook | Run P10/P50/P90 NPV, tornado sensitivity, breakeven price, and a compact risk register for selected alternatives. | `SensitivityAnalyzer`, `ProductionProfileGenerator`, `CashFlowEngine`, `ConceptEvaluator` |

These notebooks should live in the TPG4230 book Chapter 24 notebook folder and feed results back
into Chapters 11, 13, 17, 18, and 28. Each notebook should have a `plan.json`, `results.json`, and
`claims_manifest.json` entry so the PaperLab replication gate can rebuild the calculations from a
clean checkout.

The next book-facing work is to use the decision-engine objects in Chapter 24 and the related
field-development chapters: replace notebook-local tables with `FieldDevelopmentReportExporter`,
add one route-network example with shared corridor economics, and include lifecycle-emissions plots
beside the existing NPV and sensitivity figures.

---

## Current State Analysis

### Existing Building Blocks ✅

NeqSim already has substantial infrastructure for field development:

| Package | Class | Purpose | Maturity |
|---------|-------|---------|----------|
| `process.equipment.reservoir` | `SimpleReservoir` | Tank-type material balance model | ✅ Stable |
| `process.equipment.reservoir` | `WellFlow` | IPR (inflow performance) modeling | ✅ Stable |
| `process.equipment.reservoir` | `WellSystem` | Combined IPR + VLP (tubing) model | ✅ Stable |
| `process.equipment.reservoir` | `TubingPerformance` | Vertical lift performance | ✅ Stable |
| `process.fielddevelopment.concept` | `FieldConcept` | High-level concept definition | ✅ New |
| `process.fielddevelopment.concept` | `ReservoirInput` | Reservoir characterization | ✅ New |
| `process.fielddevelopment.concept` | `WellsInput` | Well configuration | ✅ New |
| `process.fielddevelopment.concept` | `InfrastructureInput` | Infrastructure definition | ✅ New |
| `process.fielddevelopment.screening` | `FlowAssuranceScreener` | Hydrate/wax/corrosion screening | ✅ New |
| `process.fielddevelopment.screening` | `EconomicsEstimator` | CAPEX/OPEX estimation | ✅ New |
| `process.fielddevelopment.screening` | `SafetyScreener` | Safety screening | ✅ New |
| `process.fielddevelopment.screening` | `EmissionsTracker` | CO2 emissions estimation | ✅ New |
| `process.fielddevelopment.evaluation` | `ConceptEvaluator` | Concept orchestration | ✅ New |
| `process.fielddevelopment.evaluation` | `BatchConceptRunner` | Multi-concept comparison | ✅ New |
| `process.fielddevelopment.facility` | `FacilityBuilder` | Modular facility configuration | ✅ New |
| `process.util.fielddevelopment` | `ProductionProfile` | Decline curve modeling | ✅ Stable |
| `process.util.fielddevelopment` | `WellScheduler` | Well intervention scheduling | ✅ Stable |
| `process.util.fielddevelopment` | `FacilityCapacity` | Bottleneck analysis | ✅ Stable |
| `process.util.fielddevelopment` | `SensitivityAnalysis` | Monte Carlo analysis | ✅ Stable |
| `process.util.fielddevelopment` | `FieldProductionScheduler` | Production scheduling | 🔄 New (basic) |
| `process.util.optimization` | `ProductionOptimizer` | Production optimization | ✅ Stable |

### Gap Closure Status

The original gap list has now moved from architecture planning into implemented API coverage:

| Original gap | Current NeqSim support | Verification |
|--------------|------------------------|--------------|
| Tie-back analysis engine | `TiebackAnalyzer`, `TiebackOption`, `TiebackReport`, `HostFacility`, and `TiebackRouteNetwork` screen hosts, route networks, hydraulics, flow assurance, capacity, CAPEX, and NPV. | `TiebackTest` |
| Multi-field portfolio | `PortfolioOptimizer` ranks field, tieback, IOR, exploration, and infrastructure projects under total and annual capital constraints. | `PortfolioOptimizerTest` |
| Norwegian petroleum economics | `NorwegianTaxModel`, `TaxModelRegistry`, `FiscalParameters`, and `CashFlowEngine` cover the 22% corporate plus 56% petroleum-tax model and generic fiscal alternatives. | `EconomicsTest`, `TaxModelTest`, `FieldDevelopmentNPVTest` |
| Facilities integration | `ConceptToProcessLinker` generates screening-to-concept process systems from `FieldConcept` definitions and exposes utility summaries from the generated `ProcessSystem`. | `FieldDevelopmentIntegrationUtilitiesTest` |
| Time-series export | `ReservoirCouplingExporter` generates VFP production/injection tables, schedule keywords, group/well controls, separator efficiency tables, and CSV production forecasts for reservoir coupling. | `FieldDevelopmentIntegrationUtilitiesTest` |
| Decision support | `DevelopmentOptionRanker`, `ConceptEvaluator`, and `BatchConceptRunner` provide multi-criteria ranking, concept KPI scoring, and batch comparison. | `DevelopmentOptionRankerTest`, `FieldDevelopmentEngineTest` |
| Pipeline hydraulics integration | `MultiphaseFlowIntegrator`, `TiebackRouteNetwork`, `NetworkSolver`, and `NetworkResult` connect tieback screening to multiphase pipeline and gathering-network calculations. | `TiebackTest`, `FieldDevelopmentIntegrationUtilitiesTest` |

---

## Strategic Architecture

### Implemented Module Structure

The field-development engine now uses an implemented package layout rather than a proposed one:

| Package | Implemented role |
|---------|------------------|
| `concept/` | Field concepts, reservoir/well/infrastructure inputs, standardized templates, and P10/P50/P90 uncertainty bundles. |
| `evaluation/` | Concept KPI evaluation, batch comparisons, Monte Carlo support, and multi-criteria option ranking. |
| `screening/` | Flow assurance, safety, emissions, lifecycle emissions, artificial lift, and cost screening. |
| `facility/` | Facility block configuration and `ConceptToProcessLinker` for generating `ProcessSystem` models from concepts. |
| `tieback/` | Host-facility modelling, tieback option analysis, feasibility reports, and route-aware ranking. |
| `network/` | Route-network metadata, multiphase pipeline screening, gathering-network solving, and network result reporting. |
| `economics/` | Cash-flow engine, Norwegian and generic fiscal models, sensitivity/Monte Carlo economics, production profiles, and portfolio optimization. |
| `reservoir/` | Transient well models, injection strategy support, and ECLIPSE/E300 coupling exports. |
| `reporting/` | Book/report-ready markdown tables and figure data for comparisons, KPIs, sensitivities, and tieback cases. |
| `workflow/` | Unified workflow orchestration across screening, conceptual, and detailed study phases. |

### Integration Shape

The decision engine is intentionally layered:

1. `FieldConcept` captures the field-development assumptions.
2. Screening classes calculate flow-assurance, emissions, safety, and economic indicators.
3. `TiebackAnalyzer`, `GreenfieldConceptFactory`, `PortfolioOptimizer`, and `DevelopmentOptionRanker` compare alternatives.
4. `ConceptToProcessLinker`, `NetworkSolver`, and `ReservoirCouplingExporter` bridge the screening models to detailed process, network, and reservoir workflows.
5. `FieldDevelopmentReportExporter` packages outputs for notebooks, reports, and the TPG4230 book.

---

## Implementation Status and Remaining Maturation

| Capability | Status | Next maturation step |
|------------|--------|----------------------|
| Core economics and Norwegian tax | Implemented in `CashFlowEngine`, `NorwegianTaxModel`, and `TaxModelRegistry`. | Calibrate fiscal examples against public NCS case studies and add a concise tutorial. |
| Tieback screening | Implemented in `TiebackAnalyzer` with host capacity, hydraulics, flow assurance, economics, and route-network metadata. | Add more public benchmark cases for long, cold, and shared-corridor tiebacks. |
| Production forecasting | Implemented through `ProductionProfileGenerator` and `ReservoirInput` resource/recovery assumptions. | Connect additional history-matching examples to public production data. |
| Portfolio optimization | Implemented in `PortfolioOptimizer`. | Add efficient-frontier plotting helpers and infrastructure-synergy examples. |
| Pipeline and gathering networks | Implemented in `TiebackRouteNetwork`, `MultiphaseFlowIntegrator`, `NetworkSolver`, and `NetworkResult`. | Replace selected screening correlations with higher-fidelity `PipeBeggsAndBrills` cases where runtime allows. |
| Reservoir coupling export | Implemented in `ReservoirCouplingExporter` and VFP-ready profile export helpers. | Add round-trip examples that feed generated schedule/VFP snippets into reservoir-simulator workflows. |
| Decision support and reporting | Implemented in `DevelopmentOptionRanker`, `BatchConceptRunner`, `ConceptEvaluator`, and `FieldDevelopmentReportExporter`. | Use the exporter consistently in Chapter 24 notebooks and field-development documentation. |

---

## Use Case Workflows

The implemented APIs now support three reusable workflow families for teaching, screening, and
early concept selection.

| Use case | Workflow | Primary outputs |
|----------|----------|-----------------|
| Gas tieback screening | Define a discovery, candidate hosts, optional route networks, representative fluid, and host constraints; run `TiebackAnalyzer`. | Feasible hosts, hydraulic margins, flow-assurance risks, host-capacity bottlenecks, CAPEX, NPV, and route summaries. |
| Field development with NPV | Build `DevelopmentCaseTemplate` objects or `FieldConcept` inputs; evaluate production, emissions, and fiscal economics using `GreenfieldConceptFactory`, `ConceptEvaluator`, and `CashFlowEngine`. | Production profile, lifecycle emissions, after-tax cash flow, NPV, breakeven price, and uncertainty ranges. |
| Portfolio investment planning | Add candidate projects to `PortfolioOptimizer`, set total or annual budget constraints, then compare optimization strategies. | Selected/deferred projects, budget use by year, portfolio NPV, EMV, and capital efficiency. |
| Reservoir/process coupling | Generate process systems with `ConceptToProcessLinker` and export VFP/schedule data with `ReservoirCouplingExporter`. | Process utility summaries, VFPPROD/VFPINJ tables, group/well controls, and production-forecast CSV data. |
| Decision support | Score alternatives with `DevelopmentOptionRanker` and publish tables with `FieldDevelopmentReportExporter`. | Ranked concept list, weighted MCDA scores, tornado tables, KPI comparison tables, and figure-ready data. |

---

## Integration Points

### With Existing NeqSim

| Component | Integration |
|-----------|-------------|
| `SystemInterface` | Fluid PVT for reservoir, process, and flow-assurance calculations. |
| `SimpleReservoir` | Material-balance depletion and resource-capped production profiles. |
| `WellFlow` / `WellSystem` | IPR/VLP well models feeding network and reservoir-coupling workflows. |
| `ProcessSystem` | Generated facility models and optional host-facility bottleneck screening. |
| `PipeBeggsAndBrills` and pipeline equipment | Higher-fidelity hydraulic checks behind screening-level route and network abstractions. |
| `CashFlowEngine` and tax models | Country-specific after-tax economics and copy-safe scenario/sensitivity analysis. |

### With External Tools

| Tool | Integration Method |
|------|-------------------|
| ECLIPSE/E300 | VFP tables and SCHEDULE keyword snippets from `ReservoirCouplingExporter`. |
| Excel / CSV tools | Production forecast, portfolio, and comparison table exports. |
| Python/Jupyter | neqsim-python bindings plus book notebooks using the workspace Java classes. |
| Power BI / Spotfire | CSV/JSON-ready outputs from report and export helpers. |

---

## Testing Strategy

Implemented coverage now includes:

| Test area | Current tests |
|-----------|---------------|
| Tieback, host capacity, hydraulics, route networks | `TiebackTest` |
| Field concepts, templates, lifecycle emissions, report exporter | `FieldDevelopmentEngineTest` |
| Norwegian tax, cash flow, sensitivity, production profiles | `EconomicsTest`, `TaxModelTest`, `FieldDevelopmentNPVTest` |
| Portfolio optimization | `PortfolioOptimizerTest` |
| Decision ranking | `DevelopmentOptionRankerTest` |
| Process linking, reservoir export, network solver | `FieldDevelopmentIntegrationUtilitiesTest` |

Remaining test growth should focus on public benchmark studies, round-trip reservoir coupling,
and higher-fidelity multiphase hydraulics comparisons.

---

## Success Metrics

1. **Screening speed**: Evaluate a tieback option in less than 5 seconds for teaching-scale cases.
2. **Accuracy**: Keep screening NPV within about 20% of detailed engineering once public calibration cases are available.
3. **Usability**: Maintain simple APIs for common workflows while preserving detailed NeqSim escape hatches.
4. **Integration**: Keep direct paths from concept screening to process, network, reservoir, economics, and reporting tools.
5. **Documentation**: Keep the strategy, README examples, tests, and TPG4230 notebooks synchronized with current APIs.

---

## Next Steps

1. Move Chapter 24 notebooks fully onto `FieldDevelopmentReportExporter` outputs.
2. Add one route-network teaching example with branches, a riser, and shared-corridor economics.
3. Add lifecycle-emissions plots next to existing NPV and sensitivity figures.
4. Add public benchmark/calibration cases for Norwegian fiscal economics, tieback hydraulics, and reservoir coupling exports.
5. Mature portfolio optimization with infrastructure synergy and efficient-frontier visualizations.

---

## Appendix: Norwegian Petroleum Economics Reference

### Tax Rates (2024)
- Corporate tax: 22%
- Special petroleum tax: 56%
- **Total marginal rate: 78%**

### Deductions
- Depreciation: Straight-line over 6 years
- Uplift: 5.5% per year for 4 years (22% total)
- Exploration costs: 100% deductible

### Tariffs (Typical)
- Gas transport (Gassled): 0.10-0.15 NOK/Sm³
- Oil transport: Varies by system

### Price Assumptions (Planning)
- Oil: 70-80 USD/bbl
- Gas: 0.20-0.30 USD/Sm³
- Exchange rate: 10 NOK/USD
