---
title: Field Development Decision Engine Workflows
description: Detailed workflow guide for NeqSim field development decision support, covering tieback screening, greenfield templates, portfolio optimization, Norwegian economics, process coupling, reservoir exports, route networks, and report-ready tables.
---

# Field Development Decision Engine Workflows

NeqSim's field-development layer now supports an end-to-end teaching and screening workflow for brownfield tiebacks, greenfield concepts, and multi-field portfolio decisions. The decision engine keeps early screening models tied to the same thermodynamic and process-simulation foundation used later in detailed studies.

This page explains how the new APIs fit together and where to find executable notebook examples that import workspace classes through `devtools/neqsim_dev_setup.py`.

## Workflow Overview

| Step | Decision question | Primary APIs | Outputs |
|------|-------------------|--------------|---------|
| 1. Define concept basis | What reservoir, well, and infrastructure assumptions define the case? | `FieldConcept`, `ReservoirInput`, `WellsInput`, `InfrastructureInput`, `GreenfieldConceptFactory` | Comparable concept objects, production profiles, CAPEX assumptions, uncertainty ranges |
| 2. Screen tieback routes | Which host and route are technically and economically feasible? | `TiebackAnalyzer`, `HostFacility`, `TiebackRouteNetwork`, `MultiphaseFlowIntegrator` | Host capacity status, route length, arrival pressure and temperature, hydrate margin, CAPEX, NPV |
| 3. Rank alternatives | Which concept is preferred when economics, CO2, risk, and strategic fit are weighted together? | `DevelopmentOptionRanker`, `ConceptEvaluator`, `BatchConceptRunner` | Weighted MCDA ranking, normalized criteria, best option, sensitivity-ready scores |
| 4. Optimize portfolio | Which projects should be selected under budget and risk constraints? | `PortfolioOptimizer`, `CashFlowEngine`, `NorwegianTaxModel`, `TaxModelRegistry` | Selected and deferred projects, capital use by year, EMV, NPV, capital efficiency |
| 5. Bridge to facilities | What process model and utility load follows from the concept? | `ConceptToProcessLinker`, `FacilityBuilder`, `FacilityConfig` | Generated `ProcessSystem`, power demand, heating duty, cooling duty, emissions estimate |
| 6. Bridge to reservoir models | What data should be exported to ECLIPSE/E300 workflows? | `ReservoirCouplingExporter` | VFPPROD/VFPINJ tables, schedule keywords, group and well controls, forecast CSV |
| 7. Publish results | How are book and report tables kept consistent? | `FieldDevelopmentReportExporter` | Markdown tables and figure-ready data for notebooks, reports, and book chapters |

## New Example Notebooks

| Notebook | Focus | What it demonstrates |
|----------|-------|----------------------|
| [field_development_decision_engine.ipynb](../../examples/notebooks/field_development_decision_engine.ipynb) | Concept comparison, MCDA, portfolio optimization | `GreenfieldConceptFactory`, lifecycle emissions, `FieldDevelopmentReportExporter`, `DevelopmentOptionRanker`, `PortfolioOptimizer` |
| [field_development_process_reservoir_coupling.ipynb](../../examples/notebooks/field_development_process_reservoir_coupling.ipynb) | Tieback route networks, facility generation, reservoir exports | `TiebackRouteNetwork`, `TiebackAnalyzer`, `NetworkSolver`, `ConceptToProcessLinker`, `ReservoirCouplingExporter` |

Both notebooks are local developer examples. They load Java classes from the workspace through the devtools setup cell so newly added field-development APIs can be exercised before a release package is published.

## API Grouping

### Concept Templates and Uncertainty

`GreenfieldConceptFactory` creates standardized development cases for subsea tieback, standalone FPSO, fixed platform, subsea-to-shore, onshore terminal, and phased brownfield expansion concepts. Each `DevelopmentCaseTemplate` carries:

| Data carried by template | Purpose |
|--------------------------|---------|
| `FieldConcept` | Source reservoir, wells, and infrastructure assumptions |
| `FacilityConfig` | Auto-generated block model used by screening estimates |
| CAPEX breakdown | Comparable installed-cost basis for economic screening |
| Production profile | Annual gas or oil production used by economics and emissions |
| `DevelopmentCaseUncertainty` | P10/P50/P90 resource, CAPEX, schedule, price, and production-factor ranges |
| `LifecycleEmissionsProfile` | Annual emissions, load factor, power source, and intensity data |
| `CashFlowResult` | NPV, IRR, and cash-flow metrics from the screening model |

Use these templates when a notebook or book chapter needs repeatable assumptions rather than ad hoc local tables.

### Tieback Routes and Host Screening

`TiebackRouteNetwork` adds route topology to the existing tieback screening flow. The network records main flowlines, shared corridors, branches, risers, and host hubs. `TiebackAnalyzer` accepts either a simple distance or a route network and stores both hydraulic screening results and route metadata on `TiebackOption`.

| Route metric | Why it matters |
|--------------|----------------|
| Screening length | Equivalent hydraulic path for pressure-drop and arrival-temperature screening |
| Installed length | CAPEX basis including branches, risers, and shared corridors |
| Shared corridor length | Identifies infrastructure that may support future tiebacks or phased development |
| Branch and riser counts | Captures extra complexity for reports and early risk discussion |
| Equivalent diameter and heat transfer | Feeds the route-aware hydraulic and thermal screening calculation |

### Decision Ranking

`DevelopmentOptionRanker` performs a weighted MCDA calculation. Criteria include NPV, IRR, payback, capital efficiency, breakeven price, technical complexity, technical risk, reservoir uncertainty, recovery factor, CO2 intensity, total emissions, environmental impact, strategic fit, infrastructure synergy, optionality, schedule flexibility, HSE risk, execution risk, commercial risk, and regulatory risk.

The ranker supports preset profiles named `economic`, `environmental`, `risk`, and `balanced`. For teaching workflows, the balanced profile is a good default because it prevents the decision from becoming a single-factor NPV ranking.

### Portfolio Optimization

`PortfolioOptimizer` selects projects under total and annual budget constraints. Candidate projects carry CAPEX, NPV, project type, probability of success, start year, optional annual CAPEX profiles, and dependencies.

| Strategy | Use case |
|----------|----------|
| `GREEDY_NPV_RATIO` | Quick capital-efficiency screening |
| `GREEDY_ABSOLUTE_NPV` | Maximize absolute value where budget pressure is limited |
| `RISK_WEIGHTED` | Favor high-value projects with higher probability of success |
| `EMV_MAXIMIZATION` | Exploration-heavy portfolios where chance of success drives expected value |
| `BALANCED` | Maintain diversity across development, tieback, IOR, exploration, and infrastructure projects |

### Process and Reservoir Coupling

`ConceptToProcessLinker` turns a `FieldConcept` into a screening or concept-level `ProcessSystem`. Always run the generated process before reading utility summaries because compressor and cooler duties are populated during `process.run()`.

`ReservoirCouplingExporter` creates VFP and schedule artifacts for reservoir-simulator coupling. When configuring ranges, use at least two points for pressure, rate, water cut, and GOR ranges.

### Report-Ready Tables

`FieldDevelopmentReportExporter` centralizes tables and figure-ready data for notebooks and book chapters. Use it when publishing comparisons so the same API generates:

| Export method | Output |
|---------------|--------|
| `exportTemplateComparisonMarkdown` | Concept template comparison table |
| `exportTiebackOptionsMarkdown` | Tieback option table from a `TiebackReport` |
| `exportTornadoMarkdown` | Sensitivity/tornado table |
| `exportConceptKpisMarkdown` | Batch concept KPI comparison |
| `exportTemplateNpvFigureData` | Rows for NPV bar charts |

## Validation Coverage

| Capability | Regression tests |
|------------|------------------|
| Tieback screening, host capacity, route networks | `TiebackTest` |
| Concept templates, lifecycle emissions, report exporter | `FieldDevelopmentEngineTest` |
| Norwegian tax, cash flow, sensitivity, production profiles | `EconomicsTest`, `TaxModelTest`, `FieldDevelopmentNPVTest` |
| Portfolio optimization | `PortfolioOptimizerTest` |
| Decision ranking | `DevelopmentOptionRankerTest` |
| Process linking, VFP export, gathering-network solver | `FieldDevelopmentIntegrationUtilitiesTest` |

## Practical Workflow for Book Chapters

1. Start from `GreenfieldConceptFactory` or a `FieldConcept` builder rather than notebook-local tables.
2. Use `TiebackAnalyzer` and `TiebackRouteNetwork` for brownfield options where host capacity and route complexity matter.
3. Use `DevelopmentOptionRanker` for transparent MCDA rather than hard-coded concept ordering.
4. Use `PortfolioOptimizer` when the chapter discusses capital rationing across multiple fields or phases.
5. Use `ConceptToProcessLinker` and `NetworkSolver` to connect screening decisions to process and network consequences.
6. Use `ReservoirCouplingExporter` when the scenario needs VFP or schedule exports for reservoir coupling.
7. Use `FieldDevelopmentReportExporter` for all report/book tables so examples remain consistent as the APIs evolve.
