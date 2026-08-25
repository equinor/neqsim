---
title: Field Development Framework Documentation
description: Field-development documentation for digital field twins spanning exploration through decommissioning.
---

This folder documents NeqSim's field-development screening and integration capabilities from concept selection through late life. The APIs combine reservoir assumptions, facility screening, process-model generation, host-capacity checks, economics, emissions, and reservoir-simulator handoffs.

The high-level helpers are screening tools. They do not by themselves provide a calibrated reservoir model, a design-grade well model, or an independently reviewed cost estimate. Preserve the engineering basis, units, fluid characterization, uncertainty, and validation evidence when moving a concept into detailed design.

---

## Overview Documents

| Document | Description |
|----------|-------------|
| [DIGITAL_FIELD_TWIN.md](DIGITAL_FIELD_TWIN) | **Start here!** Architecture showing how NeqSim integrates all lifecycle phases |
| [MATHEMATICAL_REFERENCE.md](MATHEMATICAL_REFERENCE) | Mathematical foundations for all calculations (EoS, economics, flow) |
| [API_GUIDE.md](API_GUIDE) | Source-anchored concept, screening-KPI, option-ranking, unit, and engineering-boundary guide |
| [DECISION_ENGINE_WORKFLOWS.md](DECISION_ENGINE_WORKFLOWS) | Decision-engine workflows for tiebacks, greenfield concepts, portfolios, process coupling, reservoir exports, and report-ready tables |
| [HOST_TIE_IN_CAPACITY.md](HOST_TIE_IN_CAPACITY) | Host capacity, holdback, process-equipment bottlenecks, and debottleneck decisions for brownfield tiebacks |
| [INTEGRATED_PRODUCTION_MODELLING.md](INTEGRATED_PRODUCTION_MODELLING) | **Reservoir-to-market IPM** &mdash; reservoir drives, well deliverability curves, network solver, gas-lift allocation, well-test matching, artificial-lift pumps, and choke optimisation (GAP/PROSPER/MBAL + Pipesim style) |
| [FIELD_LIFECYCLE_SIMULATION.md](FIELD_LIFECYCLE_SIMULATION) | Time-marching field and area concepts with multi-host routing, facility sizing, product specifications, NPV and break-even |

---

## Model Continuity and Integration Boundaries

NeqSim can preserve thermodynamic consistency across a workflow when the caller explicitly passes or clones the same calibrated `SystemInterface`. The field-development convenience classes do not automatically propagate a tuned fluid through every calculation.

### PVT to process

Create and calibrate the fluid first, then pass a clone to each inlet `Stream`. A generated model from `ConceptToProcessLinker` instead creates a representative SRK screening fluid from the `FieldConcept` inputs. Replace that generated fluid or build the detailed `ProcessSystem` explicitly when tuned PVT behavior is required.

### Reservoir to facilities

`ReservoirCouplingExporter.generateVfpProd(String, SystemInterface, int)` accepts a fluid object and produces Eclipse-style VFP keywords. The current implementation uses a screening hydrostatic/friction correlation; it is not a full wellbore or compositional-flow calculation. Configure the export format with `setFormat(ExportFormat)`, retrieve text with `getEclipseKeywords()`, and write it with `exportToFile(String)`. Validate design work against a qualified well and flowline model.

### Technical screening to economics

`ConceptEvaluator.evaluate(FieldConcept)` auto-generates a facility configuration and combines simplified production, flow-assurance, safety, emissions, and economics screeners. Treat the resulting KPIs as comparative concept-screening evidence, not as project sanction or design certification.

---

## Package Structure

```
neqsim.process.fielddevelopment/
├── concept/           # Core data structures (FieldConcept, ReservoirInput, etc.)
│   ├── GreenfieldConceptFactory
│   └── DevelopmentCaseTemplate
├── economics/         # NPV, tax, portfolio optimization
│   ├── CashFlowEngine
│   ├── NorwegianTaxModel
│   └── PortfolioOptimizer
├── evaluation/        # Decision support
│   ├── ConceptEvaluator
│   ├── DevelopmentOptionRanker
│   └── MonteCarloRunner
├── facility/          # Process generation
│   ├── ConceptToProcessLinker
│   └── FacilityBuilder
├── lifecycle/         # Executable reservoir-to-market lifetime and area concepts
│   ├── AreaDevelopmentPortfolio
│   ├── FieldLifecycleSimulator
│   ├── FieldLifecycleModel (ProcessSystem/ProcessModel + existing SURF)
│   ├── FacilityLifecycleStrategy
│   ├── FacilityCapacityAllocator
│   ├── FacilityModificationPlanner
│   ├── FieldProductSpecifications
│   └── NorwegianOilFieldCase (greenfield + multi-host area portfolio)
├── network/           # Pipeline network
│   ├── MultiphaseFlowIntegrator
│   └── NetworkSolver
├── reservoir/         # Reservoir coupling
│   ├── ReservoirCouplingExporter
│   └── TransientWellModel
├── screening/         # Technical screening
│   ├── FlowAssuranceScreener
│   ├── ArtificialLiftScreener
│   └── EmissionsTracker
├── subsea/            # Subsea systems
│   └── SubseaProductionSystem
└── tieback/           # Tieback analysis
    ├── TiebackAnalyzer
    ├── HostFacility
    └── capacity/      # Host tie-in capacity and holdback planning
        ├── TieInCapacityPlanner
        ├── ProductionProfileSeries
        └── HostTieInPoint
```

---

## Executable Screening Workflow

The following Java 8 program exercises the current public APIs for concept evaluation, option ranking, host-capacity holdback, process generation, reservoir export, and SURF cost screening. It validates key results before logging them. Replace the illustrative assumptions and screening correlations with project data and independently reviewed models before using the results for an engineering decision.

```java
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.fielddevelopment.concept.DevelopmentCaseTemplate;
import neqsim.process.fielddevelopment.concept.FieldConcept;
import neqsim.process.fielddevelopment.concept.GreenfieldConceptFactory;
import neqsim.process.fielddevelopment.evaluation.ConceptEvaluator;
import neqsim.process.fielddevelopment.evaluation.ConceptKPIs;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.Criterion;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.DevelopmentOption;
import neqsim.process.fielddevelopment.evaluation.DevelopmentOptionRanker.RankingResult;
import neqsim.process.fielddevelopment.facility.ConceptToProcessLinker;
import neqsim.process.fielddevelopment.facility.ConceptToProcessLinker.FidelityLevel;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.ExportFormat;
import neqsim.process.fielddevelopment.reservoir.ReservoirCouplingExporter.VfpTable;
import neqsim.process.fielddevelopment.tieback.HostFacility;
import neqsim.process.fielddevelopment.tieback.capacity.CapacityAllocationPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.HoldbackPolicy;
import neqsim.process.fielddevelopment.tieback.capacity.ProductionProfileSeries;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityPlanner;
import neqsim.process.fielddevelopment.tieback.capacity.TieInCapacityResult;
import neqsim.process.mechanicaldesign.subsea.SubseaCostEstimator;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class FieldDevelopmentOverviewExample {
  private static final Logger logger =
      LogManager.getLogger(FieldDevelopmentOverviewExample.class);

  private FieldDevelopmentOverviewExample() {}

  public static void main(String[] args) throws Exception {
    FieldConcept concept =
        FieldConcept.gasTieback("Demo gas tieback", 30.0, 2, 0.8);
    ConceptKPIs kpis = new ConceptEvaluator().evaluate(concept);

    DevelopmentCaseTemplate tieback =
        GreenfieldConceptFactory.subseaTieback("Book tieback");
    DevelopmentCaseTemplate fpso =
        GreenfieldConceptFactory.standaloneFpso("Book FPSO");

    DevelopmentOptionRanker ranker = new DevelopmentOptionRanker();
    DevelopmentOption fpsoOption = ranker.addOption("FPSO");
    fpsoOption.setScore(Criterion.NPV, 1200.0);
    fpsoOption.setScore(Criterion.CO2_INTENSITY, 12.0);
    DevelopmentOption tiebackOption = ranker.addOption("Tieback");
    tiebackOption.setScore(Criterion.NPV, 650.0);
    tiebackOption.setScore(Criterion.CO2_INTENSITY, 7.0);
    RankingResult ranking = ranker.rank();

    HostFacility host =
        HostFacility.builder("Brownfield host").gasCapacity(10.0).build();
    ProductionProfileSeries base =
        new ProductionProfileSeries("base")
            .addPeriod(2028, 7.0, 0.0, 0.0, 0.0);
    ProductionProfileSeries satellite =
        new ProductionProfileSeries("satellite")
            .addPeriod(2028, 4.0, 0.0, 0.0, 0.0);
    TieInCapacityResult capacity =
        new TieInCapacityPlanner(host)
            .setHostProductionProfile(base)
            .setSatelliteProductionProfile(satellite)
            .setAllocationPolicy(CapacityAllocationPolicy.BASE_FIRST)
            .setHoldbackPolicy(HoldbackPolicy.DEFER_TO_LATER_YEARS)
            .run();

    ConceptToProcessLinker linker = new ConceptToProcessLinker();
    ProcessSystem process =
        linker.generateProcessSystem(concept, FidelityLevel.CONCEPT);
    process.run();
    double powerMW = linker.getTotalPowerMW(process);

    SystemInterface baseFluid = new SystemSrkEos(358.15, 250.0);
    baseFluid.addComponent("methane", 0.85);
    baseFluid.addComponent("ethane", 0.08);
    baseFluid.addComponent("propane", 0.04);
    baseFluid.addComponent("n-butane", 0.02);
    baseFluid.addComponent("CO2", 0.01);
    baseFluid.setMixingRule("classic");

    ReservoirCouplingExporter exporter =
        new ReservoirCouplingExporter(process);
    exporter.setFormat(ExportFormat.ECLIPSE_100);
    exporter.setPressureRange(40.0, 60.0, 2);
    exporter.setRateRange(500.0, 1000.0, 2);
    exporter.setWctRange(0.0, 0.5, 2);
    exporter.setGorRange(100.0, 300.0, 2);
    VfpTable vfp = exporter.generateVfpProd("PROD-A1", baseFluid, 1);
    String eclipseKeywords = exporter.getEclipseKeywords();

    Path output = Files.createTempFile("neqsim-vfp-", ".inc");
    try {
      exporter.exportToFile(output.toString());

      SubseaCostEstimator cost =
          new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);
      cost.calculateTreeCost(10000.0, 7.0, 380.0, true, false);
      double treeCostUSD = cost.getTotalCost();
      cost.calculateManifoldCost(6, 80.0, 380.0, true);
      double manifoldCostUSD = cost.getTotalCost();
      cost.calculateUmbilicalCost(48.0, 4, 3, 2, 380.0, false);
      double umbilicalCostUSD = cost.getTotalCost();
      cost.calculateFlexiblePipeCost(1200.0, 8.0, 380.0, true, true);
      double riserCostUSD = cost.getTotalCost();

      if (kpis.getTotalCapexMUSD() <= 0.0
          || tieback.getFacilityConfig() == null
          || fpso.getFacilityConfig() == null
          || ranking.getRankedOptions().isEmpty()
          || !capacity.hasHoldback()
          || process.size() < 2
          || powerMW < 0.0
          || vfp.getBhpValues()[0][0][0][0][0]
              <= vfp.getThpValues()[0]
          || !eclipseKeywords.contains("VFPPROD")
          || Files.size(output) == 0L
          || treeCostUSD <= 0.0
          || manifoldCostUSD <= 0.0
          || umbilicalCostUSD <= 0.0
          || riserCostUSD <= 0.0) {
        throw new IllegalStateException(
            "Field-development screening validation failed");
      }

      logger.info(
          "Concept {}: CAPEX {} MUSD, field life {} years, "
              + "recovery {}%, CO2 intensity {} kg/boe",
          concept.getName(),
          kpis.getTotalCapexMUSD(),
          kpis.getFieldLifeYears(),
          kpis.getEstimatedRecoveryPercent(),
          kpis.getCo2IntensityKgPerBoe());
      logger.info(
          "Recommended option {}, held-back gas {} MSm3, "
              + "process power {} MW, VFP file {}",
          ranking.getBestOption().getName(),
          capacity.getTotalHeldBackGasMSm3(),
          powerMW,
          output);
      logger.info(
          "Screening costs (USD): tree {}, manifold {}, "
              + "umbilical {}, flexible riser {}",
          treeCostUSD,
          manifoldCostUSD,
          umbilicalCostUSD,
          riserCostUSD);
    } finally {
      Files.deleteIfExists(output);
    }
  }
}
```

Expected checks are qualitative rather than fixed output snapshots: positive screening CAPEX and costs, populated templates and ranking, detected host holdback, a runnable generated process, a VFP bottom-hole pressure above tubing-head pressure, and a non-empty Eclipse keyword file. Results depend on the assumptions and should be reported with their units and screening limitations.

---

## SURF Equipment Classes

NeqSim provides screening and mechanical-design helpers for SURF (Subsea, Umbilical, Riser, Flowline) equipment in `neqsim.process.equipment.subsea`:

| Class | Description |
|-------|-------------|
| `SubseaTree` | Christmas tree for well control (horizontal/vertical) |
| `SubseaManifold` | Production/test/injection routing with well slots |
| `PLET` | Pipeline End Termination structures |
| `PLEM` | Pipeline End Manifold with multiple connections |
| `SubseaJumper` | Rigid or flexible inter-equipment connections |
| `Umbilical` | Control, power, and chemical injection lines |
| `FlexiblePipe` | Dynamic risers and static flowlines |
| `SubseaBooster` | Multiphase pumps and wet gas compressors |

Depending on the equipment type, the mechanical-design helper can provide:

- screening wall-thickness and structural calculations;
- standard-oriented checks that still require project verification;
- regionalized screening-cost estimates;
- bill-of-material and JSON-report outputs.

See [SURF Subsea Equipment Guide](../process/SURF_SUBSEA_EQUIPMENT) for detailed documentation.

---

## Related Documentation

| Topic | Document |
|-------|----------|
| Integrated Field Lifecycle Simulation | [FIELD_LIFECYCLE_SIMULATION.md](FIELD_LIFECYCLE_SIMULATION) — detailed wells/SURF/process lifetime, multi-host area routing, product specifications, bottlenecks, NPV and break-even |
| SURF Subsea Equipment | [SURF_SUBSEA_EQUIPMENT.md](../process/SURF_SUBSEA_EQUIPMENT) |
| Late-Life Operations | [LATE_LIFE_OPERATIONS.md](LATE_LIFE_OPERATIONS) |
| Field Development Strategy | [FIELD_DEVELOPMENT_STRATEGY.md](FIELD_DEVELOPMENT_STRATEGY) |
| Integrated Framework | [INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK) |
| Decision Engine Workflows | [DECISION_ENGINE_WORKFLOWS.md](DECISION_ENGINE_WORKFLOWS) |
| **Multi-Scenario Production Optimization** | [MULTI_SCENARIO_PRODUCTION_OPTIMIZATION.md](MULTI_SCENARIO_PRODUCTION_OPTIMIZATION) |

---

## Executable Notebook Examples

The following developer notebooks import NeqSim Java classes from the workspace through `devtools/neqsim_dev_setup.py`, making them suitable for unreleased field-development APIs:

| Notebook | Description |
|----------|-------------|
| [field_development_decision_engine.ipynb](https://github.com/equinor/neqsim/blob/master/examples/notebooks/field_development_decision_engine.ipynb) | Standardized concept templates, lifecycle emissions, MCDA ranking, portfolio optimization, and report-ready tables |
| [field_development_process_reservoir_coupling.ipynb](https://github.com/equinor/neqsim/blob/master/examples/notebooks/field_development_process_reservoir_coupling.ipynb) | Tieback route networks, multi-well gathering allocation, concept-to-process linking, and VFP/schedule export |

---

## See Also

- [Process Simulation Guide](../wiki/process_simulation)
- [Thermodynamic Models](../thermo/thermodynamic_models)
- [Economics Module](../process/economics/)
- [Reference Manual Index](../REFERENCE_MANUAL_INDEX)

---

## AI Agent & Skills

Use `@field.development` in VS Code Copilot Chat for AI-assisted field development workflows.
This agent automatically loads the following skills:

| Skill | Scope |
|-------|-------|
| `neqsim-field-development` | Lifecycle workflows, concept selection, reservoir/well/facility APIs |
| `neqsim-field-economics` | NPV, IRR, cash flow, tax regimes (Norwegian NCS, UK), cost estimation |
| `neqsim-subsea-and-wells` | Subsea systems, casing design (API 5C3), SURF costs, tieback analysis |
| `neqsim-production-optimization` | Decline curves, bottleneck analysis, gas lift, IOR/EOR screening |

See [AI Agents Reference](../integration/ai_agents_reference) for the full catalog.
