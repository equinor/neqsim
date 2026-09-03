---
layout: default
title: "Examples"
description: "NeqSim code examples and tutorials"
nav_order: 5
has_children: true
---

This section contains tutorials, code examples, and Jupyter notebooks demonstrating NeqSim capabilities.

## Maintained workflow notebooks

These repository workflows live under `examples/notebooks/` and are maintained
with their engineering guides. Their rows describe the validation evidence stored
with each notebook; follow the linked guide for exact scope and limitations.

| Notebook | Description | View Options |
|----------|-------------|--------------|
| **Complete Offshore Process Engineering Study** | Full three-stage oil/gas process benchmark, closed design loop, discipline results, closed-loop SIF/reliability/HAZOP-LOPA-SRS/facility-response lifecycle, revisioned model packages, change revalidation, method benchmarks, and inline PyDEXPI P&ID rendering | [Guide](../integration/complete-offshore-process-engineering-study.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/complete_offshore_process_engineering_study.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/complete_offshore_process_engineering_study.ipynb) |
| **Full DEXPI Engineering ProcessSystem** | Executed line-list, relief, SIL/PFD/voting, shutdown, PSV, blowdown/flare, materials, readiness, and governed DEXPI workflow | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_full_processsystem.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_full_processsystem.ipynb) |
| **DEXPI Engineering ProcessModel** | Executed multi-area packages with area-specific engineering inputs and readiness comparison | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_processmodel.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_engineering_processmodel.ipynb) |
| **DEXPI P&ID Visualization** | Executed, dependency-light native DEXPI/Proteus parser and deterministic structural P&ID PNG/SVG renderer with a committed figure | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_pid_visualization.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/dexpi_pid_visualization.ipynb) |
| **Energy Network Dispatch and Reporting** | Executed multi-source and multi-load electrical dispatch with priorities, shortage and curtailment allocation, cost, emissions, and auditable network reports | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/01_energy_dispatch_and_reporting.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/01_energy_dispatch_and_reporting.ipynb) |
| **Rotating Equipment and Converter Maps** | Executed motor and VFD part-load performance, shaft coupling, and load-dependent generator and prime-mover efficiency maps | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/02_rotating_equipment_and_converter_maps.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/02_rotating_equipment_and_converter_maps.ipynb) |
| **Thermal Utilities and Hydraulics** | Executed utility mass-flow, temperature-quality, exergy, cooling-water pressure-drop, and pump-power screening workflow | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/03_thermal_utilities_and_hydraulics.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/03_thermal_utilities_and_hydraulics.ipynb) |
| **Chronological Offshore Energy Benchmark** | Executed time-series energy balance, generator commitment, operating-cost and CO2 accounting, and offshore wind-gas benchmark | [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/04_time_series_commitment_offshore_benchmark.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/energy_networks/04_time_series_commitment_offshore_benchmark.ipynb) |

## Local notebook catalog

Stored status describes the committed notebook only; it is not a rerun against the
current `master` branch:

- **Executed** — every code cell has a stored execution count and there is no stored
  exception or standard-error stream.
- **Partial** — some, but not all, code cells have stored execution counts.
- **Source only** — no code cell has a stored execution count.

For engineering use, rerun from a clean environment, inspect all outputs, and validate
the model, units, assumptions, and operating range. A rendered Markdown page is a
reading aid, not execution evidence.

| Stored status | Notebook | Description | View options |
|---------------|----------|-------------|--------------|
| **Source only** | **NeqSim AI Platform Integration** | Notebook for NeqSim AI Platform Integration, including NeqSim Python examples and workflow context. | [Markdown](AIPlatformIntegration.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/AIPlatformIntegration.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/AIPlatformIntegration.ipynb) |
| **Source only** | **NeqSim Advanced Risk Framework Tutorial** | Notebook for NeqSim Advanced Risk Framework Tutorial, including NeqSim Python examples and workflow context. | [Markdown](AdvancedRiskFramework_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/AdvancedRiskFramework_Tutorial.ipynb) |
| **Partial** | **Beer Brewing Process Simulation with NeqSim Bio-Processing** | Notebook for Beer Brewing Process Simulation with NeqSim Bio-Processing, including NeqSim Python examples and workflow context. | [Markdown](BeerBrewing_BioProcess_Simulation.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/BeerBrewing_BioProcess_Simulation.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/BeerBrewing_BioProcess_Simulation.ipynb) |
| **Source only** | **ESP Pump Tutorial** | Notebook for ESP Pump Tutorial, including NeqSim Python examples and workflow context. | [Markdown](ESP_Pump_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ESP_Pump_Tutorial.ipynb) |
| **Executed** | **Transparent field-development screening** | Executable NeqSim tutorial for unit-safe gas-production profiles, after-tax cash flow, and bounded sensitivities. | [Markdown](FieldDevelopmentWorkflow.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/FieldDevelopmentWorkflow.ipynb) |
| **Executed** | **GERG-2008-NH3: Ammonia Thermodynamic Properties** | Notebook for GERG-2008-NH3: Ammonia Thermodynamic Properties, including NeqSim Python examples and workflow context. | [Markdown](GERG2008_NH3_Ammonia_Properties.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/GERG2008_NH3_Ammonia_Properties.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/GERG2008_NH3_Ammonia_Properties.ipynb) |
| **Source only** | **GraphBasedProcessSimulation** | Notebook for GraphBasedProcessSimulation, including NeqSim Python examples and workflow context. | [Markdown](GraphBasedProcessSimulation.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/GraphBasedProcessSimulation.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/GraphBasedProcessSimulation.ipynb) |
| **Source only** | **H2S Distribution Between Gas, Oil, and Water Phases in NeqSim** | Notebook for H2S Distribution Between Gas, Oil, and Water Phases in NeqSim, including NeqSim Python examples and workflow context. | [Markdown](H2S_Distribution_Modeling.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/H2S_Distribution_Modeling.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/H2S_Distribution_Modeling.ipynb) |
| **Executed** | **Integrated Production & Risk Analysis** | Notebook for Integrated Production & Risk Analysis, including NeqSim Python examples and workflow context. | [Markdown](IntegratedProductionRiskAnalysis.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/IntegratedProductionRiskAnalysis.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/IntegratedProductionRiskAnalysis.ipynb) |
| **Source only** | **Looped Pipeline Network Solver - Hardy Cross Method** | Notebook for Looped Pipeline Network Solver - Hardy Cross Method, including NeqSim Python examples and workflow context. | [Markdown](LoopedPipelineNetworkExample.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/LoopedPipelineNetworkExample.ipynb) |
| **Source only** | **MPC Integration Tutorial** | Notebook for MPC Integration Tutorial, including NeqSim Python examples and workflow context. | [Markdown](MPC_Integration_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/MPC_Integration_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/MPC_Integration_Tutorial.ipynb) |
| **Executed** | **Mercury Removal in LNG Pre-Treatment** | Executable NeqSim mercury-removal screening with transient loading, preliminary design and cost boundaries, and internal verification | [Markdown](MercuryRemoval_LNG_Pretreatment.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/MercuryRemoval_LNG_Pretreatment.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/MercuryRemoval_LNG_Pretreatment.ipynb) |
| **Partial** | **Multi-Scenario VFP Generation with NeqSim** | Notebook for Multi-Scenario VFP Generation with NeqSim, including NeqSim Python examples and workflow context. | [Markdown](MultiScenarioVFP_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/MultiScenarioVFP_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/MultiScenarioVFP_Tutorial.ipynb) |
| **Partial** | **🌊 Interactive Multiphase Pipeline & S-Riser Simulation** | Notebook for 🌊 Interactive Multiphase Pipeline & S-Riser Simulation, including NeqSim Python examples and workflow context. | [Markdown](MultiphaseFlowPipelineRiser_Interactive.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/MultiphaseFlowPipelineRiser_Interactive.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/MultiphaseFlowPipelineRiser_Interactive.ipynb) |
| **Source only** | **NeqSim Process Optimization with Python** | Notebook for NeqSim Process Optimization with Python, including NeqSim Python examples and workflow context. | [Markdown](NeqSim_Python_Optimization.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/NeqSim_Python_Optimization.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NeqSim_Python_Optimization.ipynb) |
| **Source only** | **Multi-Well Network Solver Tutorial** | Notebook for Multi-Well Network Solver Tutorial, including NeqSim Python examples and workflow context. | [Markdown](NetworkSolverTutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) |
| **Source only** | **PVT Simulation and Tuning** | Notebook for PVT Simulation and Tuning, including NeqSim Python examples and workflow context. | [Markdown](PVT_Simulation_and_Tuning.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) |
| **Partial** | **🌱 Produced Water Emissions Calculation with NeqSim** | Notebook for 🌱 Produced Water Emissions Calculation with NeqSim, including NeqSim Python examples and workflow context. | [Markdown](ProducedWaterEmissions_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProducedWaterEmissions_Tutorial.ipynb) |
| **Source only** | **ProductionOptimizer - Comprehensive Tutorial** | Notebook for ProductionOptimizer - Comprehensive Tutorial, including NeqSim Python examples and workflow context. | [Markdown](ProductionOptimizer_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb) |
| **Executed** | **Production System Optimization & Bottleneck Analysis** | Notebook for Production System Optimization & Bottleneck Analysis, including NeqSim Python examples and workflow context. | [Markdown](ProductionSystem_BottleneckAnalysis.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ProductionSystem_BottleneckAnalysis.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProductionSystem_BottleneckAnalysis.ipynb) |
| **Executed** | **Reading Fluid Properties with Python** | Executed NeqSim Python tutorial for thermodynamic, transport, phase, component, unit-conversion, interfacial, and JSON property workflows. | [Markdown](ReadingFluidProperties.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) |
| **Executed** | **Reservoir-to-Market Debottleneck Portfolio** | Executed, unit-safe NeqSim workflow for paired installed-capacity studies, deterministic alternative ranking, restoration, conservation, serialization, and fail-closed diagnostics. | [Markdown](ReservoirToMarket_DebottleneckPortfolio.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/ReservoirToMarket_DebottleneckPortfolio.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReservoirToMarket_DebottleneckPortfolio.ipynb) |
| **Executed** | **Separator & Gas Scrubber Separation Efficiency** | Notebook for Separator & Gas Scrubber Separation Efficiency, including NeqSim Python examples and workflow context. | [Markdown](SeparatorEfficiency_GasScrubber_ThreePhase.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/SeparatorEfficiency_GasScrubber_ThreePhase.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/SeparatorEfficiency_GasScrubber_ThreePhase.ipynb) |
| **Executed** | **True Vapor Pressure (TVP) vs Reid Vapor Pressure (RVP) Study** | Notebook for True Vapor Pressure (TVP) vs Reid Vapor Pressure (RVP) Study, including NeqSim Python examples and workflow context. | [Markdown](TVP_RVP_Study.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/TVP_RVP_Study.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/TVP_RVP_Study.ipynb) |
| **Partial** | **TwoFluidPipe Model Tutorial** | Notebook for TwoFluidPipe Model Tutorial, including NeqSim Python examples and workflow context. | [Markdown](TwoFluidPipe_Tutorial.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/TwoFluidPipe_Tutorial.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/TwoFluidPipe_Tutorial.ipynb) |
| **Executed** | **Auto-size & optimize: three production-optimization workflows** | Notebook for Auto-size & optimize: three production-optimization workflows, including NeqSim Python examples and workflow context. | [Markdown](autosize_and_optimize_workflows.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/autosize_and_optimize_workflows.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/autosize_and_optimize_workflows.ipynb) |
| **Executed** | **Oil & Gas Topside: Production & Energy Optimization** | Notebook for Oil & Gas Topside: Production & Energy Optimization, including NeqSim Python examples and workflow context. | [Markdown](oilgas_production_energy_optimization.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/oilgas_production_energy_optimization.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/oilgas_production_energy_optimization.ipynb) |
| **Partial** | **Reservoir-to-Market Optimisation with NeqSim Process Equipment** | Executed reservoir-to-market process-equipment workflow with field-life depletion, well and flowline hydraulics, export compression, production optimisation, and value-chain economics. | [Markdown](process%20equipmentutl.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/process%20equipmentutl.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/process%20equipmentutl.ipynb) |
| **Executed** | **Plant-wide optimization of a multi-area `ProcessModel`** | Notebook for Plant-wide optimization of a multi-area `ProcessModel`, including NeqSim Python examples and workflow context. | [Markdown](processmodel_plant_optimization.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/processmodel_plant_optimization.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/processmodel_plant_optimization.ipynb) |
| **Partial** | **Reservoir-to-Market Optimisation with NeqSim Process Equipment** | Notebook for Reservoir-to-Market Optimisation with NeqSim Process Equipment, including NeqSim Python examples and workflow context. | [Markdown](reservoir_to_market_optimization.md) \| [nbviewer](https://nbviewer.org/github/equinor/neqsim/blob/master/docs/examples/reservoir_to_market_optimization.ipynb) \| [Colab](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/reservoir_to_market_optimization.ipynb) |

## Standalone Java source examples

These files are outside Maven's compiled source tree. The
`StandaloneJavaDocumentationCompilationTest` compiles the exact catalog against the
current NeqSim API. This is build verification only, not runtime or engineering-result
validation. The files retain legacy console output; inspect assumptions and execute the
required workflow before engineering reuse. For a supported starting point, use the
[Java getting-started guide](../java-getting-started.md).

| Example | Build status | Capability |
|---------|---------------|------------|
| [EclipseE300ExportImportExample](EclipseE300ExportImportExample.java) | **Build-verified source** | Eclipse E300 fluid export and import workflow |
| [FlowRegimeDebug](FlowRegimeDebug.java) | **Build-verified source** | Flow-regime diagnostic calculations |
| [FlowRegimeDetectionExample](FlowRegimeDetectionExample.java) | **Build-verified source** | Flow-regime detection across operating cases |
| [MultiScenarioVFPExample](MultiScenarioVFPExample.java) | **Build-verified source** | Multi-scenario vertical-flow-performance comparison |
| [MultiphaseModelPressureDropComparison](MultiphaseModelPressureDropComparison.java) | **Build-verified source** | Multiphase pressure-drop model comparison |
| [OffshoreEmissionReportingExample](OffshoreEmissionReportingExample.java) | **Build-verified source** | Offshore emissions accounting workflow |
| [RealTimeIntegrationExample](RealTimeIntegrationExample.java) | **Build-verified source** | Real-time process-data integration pattern |
| [SlugTrackingComparisonExample](SlugTrackingComparisonExample.java) | **Build-verified source** | Slug-tracking model comparison |
| [TransientPipelineLiquidAccumulationExample](TransientPipelineLiquidAccumulationExample.java) | **Build-verified source** | Transient pipeline liquid-accumulation study |
| [TwoFluidPipeExample](TwoFluidPipeExample.java) | **Build-verified source** | Two-fluid pipe setup and reporting |
| [TwoFluidPipeSlugTrackingExample](TwoFluidPipeSlugTrackingExample.java) | **Build-verified source** | Two-fluid slug-tracking workflow |
| [TwoFluidPipelineLiquidAccumulationExample](TwoFluidPipelineLiquidAccumulationExample.java) | **Build-verified source** | Two-fluid pipeline accumulation study |
| [TwoFluidVsDriftFluxComparisonExample](TwoFluidVsDriftFluxComparisonExample.java) | **Build-verified source** | Two-fluid and drift-flux comparison |
| [WellToOilStabilizationExample](WellToOilStabilizationExample.java) | **Build-verified source** | Well-to-oil-stabilization process workflow |

## Other Tutorials

Additional documentation and guides:

- [Norwegianemissionmethods Comparison](NorwegianEmissionMethods_Comparison.md)
- [Production Optimization Guide](PRODUCTION_OPTIMIZATION_GUIDE.md)
- [Comparesimulations Quickstart](comparesimulations_quickstart.md)
- [Selective Logic Execution](selective-logic-execution.md)
- [Transient Slug Separator Control Example](transient_slug_separator_control_example.md)

---

## Running the Notebooks

### Prerequisites

1. Install neqsim-python:
   ```bash
   pip install neqsim
   ```

2. Or open a Google Colab link above. Run and inspect the notebook's setup cell first; dependency installation and stored execution status vary by notebook.

### Local Jupyter Setup

```bash
# Create a virtual environment
python -m venv neqsim-env
source neqsim-env/bin/activate  # On Windows: neqsim-env\Scripts\activate

# Install dependencies
pip install neqsim jupyter matplotlib pandas numpy

# Start Jupyter
jupyter notebook
```

Then open any of the `.ipynb` files from this directory.
