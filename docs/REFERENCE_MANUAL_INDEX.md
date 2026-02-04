---
title: NeqSim Reference Manual - Master Index
description: This document maps all 360+ documentation files to the reference manual structure. Use this as a comprehensive table of contents and navigation guide.
---

# NeqSim Reference Manual - Master Index

This document maps all 360+ documentation files to the reference manual structure. Use this as a comprehensive table of contents and navigation guide.

**NeqSim Homepage:** [https://equinor.github.io/neqsimhome/](https://equinor.github.io/neqsimhome/)

---

## About NeqSim

NeqSim is an open-source library for calculation of fluid behavior, phase equilibrium, and process simulation. It is used for fluids such as oil and gas, carbon dioxide, refrigerants, hydrogen, ammonia, water, and chemicals.

NeqSim is distributed under the Apache-2.0 license and can be used via:
- **Java** - Core library
- **Python** - Via neqsim-python package
- **MATLAB** - Via NeqSim MATLAB toolbox
- **.NET/Excel** - Via NeqSim.NET
- **Cape-Open** - For integration with process simulators
- **Web Application** - Browser-based interface

### NeqSim Toolboxes

| Toolbox | Repository | Description |
|---------|------------|-------------|
| NeqSim Java | [equinor/neqsim](https://github.com/equinor/neqsim) | Core Java library |
| NeqSim Python | [equinor/neqsimpython](https://github.com/equinor/neqsimpython) | Python bindings |
| NeqSim MATLAB | [equinor/neqsimmatlab](https://github.com/equinor/neqsimmatlab) | MATLAB toolbox |
| NeqSim .NET | [equinor/neqsimNET](https://github.com/equinor/neqsimNET) | .NET/C# integration |
| NeqSim Cape-Open | [equinor/neqsimcapeopen](https://github.com/equinor/neqsimcapeopen) | Cape-Open/Excel interface |
| NeqSim Native | [equinor/neqsim-native](https://github.com/equinor/neqsim-native) | Native compilation via GraalVM |

### Web Applications & APIs

| Application | Link | Description |
|-------------|------|-------------|
| NeqSim Web App | [neqsim.streamlit.app](https://neqsim.streamlit.app/) | Browser-based calculations |
| NeqSim Colab | [NeqSim-Colab](https://github.com/EvenSol/NeqSim-Colab) | Jupyter/Colab notebook examples |
| NeqSimLive API | [NeqSimAPI](https://github.com/equinor/NeqSimAPI) | Container-based APIs for digital twins |
| Process Models | [neqsimprocess](https://github.com/equinor/neqsimprocess) | Pre-built process model library |

### AI & Optimization

| Project | Repository | Description |
|---------|------------|-------------|
| RL Agents | [NeqSim-Process-RL-Agents](https://github.com/equinor/NeqSim-Process-RL-Agents) | Multi-agent reinforcement learning for process optimization |

### Support & Community

- **Discussions:** [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Academic Support:** [NTNU Energy and Process Engineering](https://www.ntnu.edu/employees/even.solbraa)
- **Benchmark:** [Performance benchmarks](https://equinor.github.io/neqsimhome/benchmark.html)

---

## Part I: Getting Started

### Chapter 1: Introduction

| Document | Path | Description |
|----------|------|-------------|
| Overview | [docs/README.md](README) | Package overview and quick start |
| Modules | [docs/modules.md](modules) | Architecture and module structure |
| **O&G Design Review** | [docs/NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW.md](NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW) | **Comprehensive capability review for oil & gas design and operations** |

### Chapter 2: Quickstart Guides (NEW!)

| Document | Path | Description |
|----------|------|-------------|
| **Quickstart Hub** | [docs/quickstart/index.md](quickstart/index) | **Get running in 5 minutes** - choose your platform |
| Java Quickstart | [docs/quickstart/java-quickstart.md](quickstart/java-quickstart) | Maven setup, first flash, first process |
| Python Quickstart | [docs/quickstart/python-quickstart.md](quickstart/python-quickstart) | pip install, jneqsim imports, gotchas |
| Colab Quickstart | [docs/quickstart/colab-quickstart.md](quickstart/colab-quickstart) | One-click notebooks, no setup required |

### Chapter 3: Tutorials & Learning Paths (NEW!)

| Document | Path | Description |
|----------|------|-------------|
| **Tutorials Hub** | [docs/tutorials/index.md](tutorials/index) | All tutorials organized by topic |
| Learning Paths | [docs/tutorials/learning-paths.md](tutorials/learning-paths) | PVT Engineer, Process Engineer, Developer tracks |
| **TEG Dehydration** | [docs/tutorials/teg_dehydration_tutorial.md](tutorials/teg_dehydration_tutorial) | **Complete TEG gas dehydration plant modeling** |
| **GOSP Tutorial** | [docs/tutorials/gosp_tutorial.md](tutorials/gosp_tutorial) | **Gas-oil separation plant (multi-stage separation)** |
| **PVT Lab Tests** | [docs/pvtsimulation/pvt_lab_tests.md](pvtsimulation/pvt_lab_tests) | **CCE, CVD, DL, separator test simulations** |

### Chapter 4: Installation & Setup

| Document | Path | Description |
|----------|------|-------------|
| Getting Started | [docs/wiki/getting_started.md](wiki/getting_started) | Installation and first steps |
| GitHub Setup | [docs/wiki/Getting-started-with-NeqSim-and-Github.md](wiki/Getting-started-with-NeqSim-and-Github) | NeqSim and GitHub setup |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP) | Development environment setup |

### Chapter 5: Quick Start Examples

| Document | Path | Description |
|----------|------|-------------|
| Usage Examples | [docs/wiki/usage_examples.md](wiki/usage_examples) | Basic usage patterns |
| FAQ | [docs/wiki/faq.md](wiki/faq) | Frequently asked questions |
| Wiki Index | [docs/wiki/index.md](wiki/index) | Wiki documentation index |

### Chapter 6: Cookbook & Troubleshooting (NEW!)

| Document | Path | Description |
|----------|------|-------------|
| **Cookbook Index** | [docs/cookbook/index.md](cookbook/index) | **Quick copy-paste recipes for common tasks** |
| Thermodynamics Recipes | [docs/cookbook/thermodynamics-recipes.md](cookbook/thermodynamics-recipes) | Fluids, flash, properties, phase envelopes |
| Process Recipes | [docs/cookbook/process-recipes.md](cookbook/process-recipes) | Separators, compressors, heat exchangers |
| Pipeline Recipes | [docs/cookbook/pipeline-recipes.md](cookbook/pipeline-recipes) | Pressure drop, multiphase flow |
| Unit Conversion | [docs/cookbook/unit-conversion-recipes.md](cookbook/unit-conversion-recipes) | All supported unit strings |
| **Troubleshooting** | [docs/troubleshooting/index.md](troubleshooting/index) | **Solutions to common problems** |

### External Getting Started Guides

| Platform | Guide |
|----------|-------|
| Java | [Getting started in Java](https://github.com/equinor/neqsim/wiki/Getting-started-with-NeqSim-and-Github) |
| Python | [Getting started in Python](https://github.com/equinor/neqsimpython/wiki/Getting-started-with-NeqSim-in-Python) |
| MATLAB | [Getting started in MATLAB](https://github.com/equinor/neqsimmatlab/wiki/Getting-started-with-NeqSim-in-Matlab) |
| Excel | [Getting started in Excel](https://github.com/equinor/neqsim.NET/wiki/Getting-started-with-NeqSim-in-Excel) |
| Google Colab | [Demo notebook](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb) |

---

## Part II: Thermodynamics

### Chapter 4: Fundamentals

| Document | Path | Description |
|----------|------|-------------|
| Thermo Overview | [docs/thermo/README.md](thermo/) | Thermodynamics module overview |
| Thermodynamics Guide | [docs/wiki/thermodynamics_guide.md](wiki/thermodynamics_guide) | Comprehensive thermodynamics guide |
| System Types | [docs/thermo/system/README.md](thermo/system/) | EoS system implementations |

### Chapter 5: Fluid Creation & Components

| Document | Path | Description |
|----------|------|-------------|
| Fluid Creation Guide | [docs/thermo/fluid_creation_guide.md](thermo/fluid_creation_guide) | Creating thermodynamic systems |
| **Fluid Classification** | [docs/thermo/fluid_classification.md](thermo/fluid_classification) | **Whitson methodology: FluidClassifier, ReservoirFluidType, GOR/C7+ classification** |
| **Component List** | [docs/thermo/component_list.md](thermo/component_list) | **Complete searchable list of all ~150+ components with CAS numbers and EoS availability** |
| Component Database | [docs/thermo/component_database_guide.md](thermo/component_database_guide) | Component properties and database |
| Component Package | [docs/thermo/component/README.md](thermo/component/) | Component class documentation |
| Mathematical Models | [docs/thermo/mathematical_models.md](thermo/mathematical_models) | Underlying mathematical models |

### Chapter 6: Equations of State

| Document | Path | Description |
|----------|------|-------------|
| Thermodynamic Models | [docs/thermo/thermodynamic_models.md](thermo/thermodynamic_models) | **Comprehensive guide** to all thermodynamic models (EoS, CPA, GERG, electrolytes, GE models) with theory and usage |
| **Søreide-Whitson Model** | [docs/thermo/SoreideWhitsonModel.md](thermo/SoreideWhitsonModel) | **Gas solubility in brine** - Modified PR EoS with salinity effects, used in NeqSimLive for produced water emissions |
| GERG-2008 | [docs/thermo/gerg2008_eoscg.md](thermo/gerg2008_eoscg) | GERG-2008 equation of state |
| Mixing Rules | [docs/thermo/mixing_rules_guide.md](thermo/mixing_rules_guide) | Mixing rules and BIPs |
| Mixing Rule Package | [docs/thermo/mixingrule/README.md](thermo/mixingrule/) | Mixing rule implementations |
| Phase Package | [docs/thermo/phase/README.md](thermo/phase/) | Phase modeling |
| Electrolyte CPA | [docs/thermo/ElectrolyteCPAModel.md](thermo/ElectrolyteCPAModel) | Electrolyte CPA model |

### Chapter 7: Flash Calculations

| Document | Path | Description |
|----------|------|-------------|
| Flash Guide | [docs/thermo/flash_calculations_guide.md](thermo/flash_calculations_guide) | Flash calculation methods |
| Flash Equations | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests) | Flash equations and testing |
| Thermo Operations | [docs/thermo/thermodynamic_operations.md](thermo/thermodynamic_operations) | Thermodynamic operations |
| TP Flash Algorithm | [docs/thermodynamicoperations/TPflash_algorithm.md](thermodynamicoperations/TPflash_algorithm) | TP flash algorithm details |
| Thermo Ops Overview | [docs/thermodynamicoperations/README.md](thermodynamicoperations/) | Thermodynamic operations module |

### Chapter 8: Fluid Characterization

Fluid characterization handles plus fraction splitting, property estimation, and lumping. **New:** A fluent API (`configureLumping()`) makes lumping configuration clearer - see the mathematics document for details.

| Document | Path | Description |
|----------|------|-------------|
| Characterization | [docs/wiki/fluid_characterization.md](wiki/fluid_characterization) | Fluid characterization guide with lumping API |
| TBP Fractions | [docs/wiki/tbp_fraction_models.md](wiki/tbp_fraction_models) | TBP fraction modeling |
| PVT Characterization | [docs/thermo/pvt_fluid_characterization.md](thermo/pvt_fluid_characterization) | PVT fluid characterization with lumping |
| Characterization Package | [docs/thermo/characterization/README.md](thermo/characterization/) | Characterization module |
| Combining Methods | [docs/thermo/characterization/fluid_characterization_combining.md](thermo/characterization/fluid_characterization_combining) | Fluid combining methods |
| Char Mathematics | [docs/pvtsimulation/fluid_characterization_mathematics.md](pvtsimulation/fluid_characterization_mathematics) | Characterization mathematics with lumping equations |

### Chapter 9: Physical Properties

| Document | Path | Description |
|----------|------|-------------|
| **Reading Fluid Properties** | [docs/thermo/reading_fluid_properties.md](thermo/reading_fluid_properties) | **Comprehensive guide to calculating and reading properties from fluids, phases, and components** |
| Properties Overview | [docs/thermo/physical_properties.md](thermo/physical_properties) | Physical property calculations |
| Physical Props Module | [docs/physical_properties/README.md](physical_properties/) | Physical properties module |
| **H2S Distribution (Quick)** | [docs/thermo/H2S_distribution_guide.md](thermo/H2S_distribution_guide) | **H2S phase distribution with Python examples - quick reference** |
| **H2S Distribution (Detailed)** | [docs/thermo/H2S_DISTRIBUTION_MODELING.md](thermo/H2S_DISTRIBUTION_MODELING) | **Comprehensive H2S modeling with Java examples and theory** |
| Viscosity Models | [docs/wiki/viscosity_models.md](wiki/viscosity_models) | Viscosity calculation models |
| Viscosity Detailed | [docs/physical_properties/viscosity_models.md](physical_properties/viscosity_models) | Detailed viscosity models |
| Density Models | [docs/physical_properties/density_models.md](physical_properties/density_models) | Density calculation models |
| Thermal Conductivity | [docs/physical_properties/thermal_conductivity_models.md](physical_properties/thermal_conductivity_models) | Thermal conductivity models |
| Diffusivity | [docs/physical_properties/diffusivity_models.md](physical_properties/diffusivity_models) | Diffusivity models |
| Interfacial Props | [docs/physical_properties/interfacial_properties.md](physical_properties/interfacial_properties) | Interfacial tension, etc. |
| Scale Potential | [docs/physical_properties/scale_potential.md](physical_properties/scale_potential) | Scale potential calculations |
| Steam Tables | [docs/wiki/steam_tables_if97.md](wiki/steam_tables_if97) | IF97 steam table implementation |
| Thermodynamic Workflows | [docs/thermo/thermodynamic_workflows.md](thermo/thermodynamic_workflows) | Common thermodynamic workflows |
| Interaction Tables | [docs/thermo/inter_table_guide.md](thermo/inter_table_guide) | Binary interaction parameters | |

### Chapter 10: Hydrates & Flow Assurance

| Document | Path | Description |
|----------|------|-------------|
| **Flow Assurance Overview** | [docs/pvtsimulation/flow_assurance_overview.md](pvtsimulation/flow_assurance_overview) | **Integrated guide: hydrates, wax, asphaltenes, scale screening** |
| **Mineral Scale Formation** | [docs/pvtsimulation/mineral_scale_formation.md](pvtsimulation/mineral_scale_formation) | **Carbonate/sulfate scale, seawater mixing, SR calculations** |
| **pH Stabilization & Corrosion** | [docs/pvtsimulation/ph_stabilization_corrosion.md](pvtsimulation/ph_stabilization_corrosion) | **Corrosion control, FeCO3 layer, Electrolyte CPA EoS** |
| Hydrate Models | [docs/thermo/hydrate_models.md](thermo/hydrate_models) | Hydrate equilibrium models |
| Hydrate Flash | [docs/thermodynamicoperations/hydrate_flash_operations.md](thermodynamicoperations/hydrate_flash_operations) | Hydrate flash operations |
| Wax Characterization | [docs/thermo/characterization/wax_characterization.md](thermo/characterization/wax_characterization) | Wax modeling, WAT calculation, flow assurance |
| Asphaltene Characterization | [docs/thermo/characterization/asphaltene_characterization.md](thermo/characterization/asphaltene_characterization) | SARA analysis, CII, CPA parameters |

---

## Part III: Process Simulation

### Chapter 11: Process Fundamentals

| Document | Path | Description |
|----------|------|-------------|
| Process Overview | [docs/process/README.md](process/) | Process simulation module |
| Process Guide | [docs/wiki/process_simulation.md](wiki/process_simulation) | Process simulation guide |
| Advanced Process | [docs/wiki/advanced_process_simulation.md](wiki/advanced_process_simulation) | Advanced techniques |
| Logical Operations | [docs/wiki/logical_unit_operations.md](wiki/logical_unit_operations) | Logical unit operations |
| Process Design | [docs/process/process_design_guide.md](process/process_design_guide) | Process design guide |
| Design Framework | [docs/process/DESIGN_FRAMEWORK.md](process/DESIGN_FRAMEWORK) | Automated design & optimization framework |
| Optimization Roadmap | [docs/process/OPTIMIZATION_IMPROVEMENT_PROPOSAL.md](process/OPTIMIZATION_IMPROVEMENT_PROPOSAL) | Optimization implementation status |

### Chapter 12: Process Systems & Models

| Document | Path | Description |
|----------|------|-------------|
| ProcessModel Overview | [docs/process/processmodel/README.md](process/processmodel/) | Process system management |
| ProcessSystem | [docs/process/processmodel/process_system.md](process/processmodel/process_system) | ProcessSystem class |
| ProcessModel | [docs/process/processmodel/process_model.md](process/processmodel/process_model) | Multi-process models |
| ProcessModule | [docs/process/processmodel/process_module.md](process/processmodel/process_module) | Modular process units |
| Graph Simulation | [docs/process/processmodel/graph_simulation.md](process/processmodel/graph_simulation) | Graph-based simulation |
| Diagram Export | [docs/process/processmodel/diagram_export.md](process/processmodel/diagram_export) | PFD diagram export |
| DEXPI Architecture | [docs/process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md](process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY) | DEXPI integration |

### Chapter 13: Streams & Mixers

| Document | Path | Description |
|----------|------|-------------|
| Streams | [docs/process/equipment/streams.md](process/equipment/streams) | Stream models |
| Mixers/Splitters | [docs/process/equipment/mixers_splitters.md](process/equipment/mixers_splitters) | Mixer and splitter models |
| Equipment Overview | [docs/process/equipment/README.md](process/equipment/) | Equipment module overview |

### Chapter 14: Separation Equipment

| Document | Path | Description |
|----------|------|-------------|
| Separators | [docs/process/equipment/separators.md](process/equipment/separators) | Two/three-phase separators, scrubbers, design parameters, performance constraints (K-value, droplet cut size, retention time), Equinor TR3500 & API 12J compliance |
| Distillation | [docs/process/equipment/distillation.md](process/equipment/distillation) | Distillation columns |
| Distillation Wiki | [docs/wiki/distillation_column.md](wiki/distillation_column) | Distillation column details |
| Absorbers | [docs/process/equipment/absorbers.md](process/equipment/absorbers) | Absorption equipment |
| **H2S Scavenger** | [docs/process/H2S_scavenger_guide.md](process/H2S_scavenger_guide) | **Chemical scavenging of H2S from gas - triazine, glyoxal, iron sponge, caustic, liquid redox** |
| Membrane | [docs/wiki/membrane_separation.md](wiki/membrane_separation) | Membrane separation |
| Membrane Equipment | [docs/process/equipment/membranes.md](process/equipment/membranes) | Membrane equipment |
| Filters | [docs/process/equipment/filters.md](process/equipment/filters) | Filter equipment |
| Water Treatment | [docs/process/equipment/water_treatment.md](process/equipment/water_treatment) | Hydrocyclones, produced water treatment trains, OIW limits |

### Chapter 15: Rotating Equipment

| Document | Path | Description |
|----------|------|-------------|
| Compressors | [docs/process/equipment/compressors.md](process/equipment/compressors) | Compressor models, drivers, speed-dependent power |
| Compressor Curves | [docs/process/equipment/compressor_curves.md](process/equipment/compressor_curves) | Compressor performance curves |
| Compressor Design | [docs/process/CompressorMechanicalDesign.md](process/CompressorMechanicalDesign) | Compressor mechanical design |
| Pumps | [docs/process/equipment/pumps.md](process/equipment/pumps) | Pump models |
| Pump Guide | [docs/wiki/pump_usage_guide.md](wiki/pump_usage_guide) | Pump usage guide |
| Pump Theory | [docs/wiki/pump_theory_and_implementation.md](wiki/pump_theory_and_implementation) | Pump theory |
| Expanders | [docs/process/equipment/expanders.md](process/equipment/expanders) | Expander models |
| Turboexpander | [docs/simulation/turboexpander_compressor_model.md](simulation/turboexpander_compressor_model) | Turboexpander model |
| Ejectors | [docs/process/equipment/ejectors.md](process/equipment/ejectors) | Ejector systems |

### Chapter 16: Heat Transfer Equipment

| Document | Path | Description |
|----------|------|-------------|
| Heat Exchangers | [docs/process/equipment/heat_exchangers.md](process/equipment/heat_exchangers) | Heat exchanger models |
| Air Cooler | [docs/wiki/air_cooler.md](wiki/air_cooler) | Air cooler models |
| Water Cooler | [docs/wiki/water_cooler.md](wiki/water_cooler) | Water cooler models |
| Steam Heater | [docs/wiki/steam_heater.md](wiki/steam_heater) | Steam heater models |
| Mechanical Design | [docs/wiki/heat_exchanger_mechanical_design.md](wiki/heat_exchanger_mechanical_design) | HX mechanical design |

### Chapter 17: Valves & Flow Control

| Document | Path | Description |
|----------|------|-------------|
| Valves | [docs/process/equipment/valves.md](process/equipment/valves) | Valve models |
| Valve Design | [docs/process/ValveMechanicalDesign.md](process/ValveMechanicalDesign) | Valve mechanical design |
| **Multiphase Choke Flow** | [docs/process/MultiphaseChokeFlow.md](process/MultiphaseChokeFlow) | **Sachdeva, Gilbert two-phase choke models** |
| Flow Meters | [docs/wiki/flow_meter_models.md](wiki/flow_meter_models) | Flow metering |
| Venturi | [docs/wiki/venturi_calculation.md](wiki/venturi_calculation) | Venturi calculations |
| Tanks | [docs/process/equipment/tanks.md](process/equipment/tanks) | Tank models |
| **Vessel Depressurization** | [docs/process/equipment/vessel_depressurization.md](process/equipment/vessel_depressurization) | **Blowdown simulation, fire scenarios, transient analysis** |
| **Measurement Devices** | [docs/process/equipment/measurement_devices.md](process/equipment/measurement_devices) | **CO2 emissions, FIV analysis, NMVOC, dew points, safety detectors** |

### Chapter 18: Special Equipment

| Document | Path | Description |
|----------|------|-------------|
| Reactors | [docs/process/equipment/reactors.md](process/equipment/reactors) | Reactor models |
| Gibbs Reactor | [docs/wiki/gibbs_reactor.md](wiki/gibbs_reactor) | Gibbs reactor |
| Electrolyzers | [docs/process/equipment/electrolyzers.md](process/equipment/electrolyzers) | Electrolyzer systems |
| CO2 Electrolyzer | [docs/pvtsimulation/CO2ElectrolyzerExample.md](pvtsimulation/CO2ElectrolyzerExample) | CO2 electrolyzer example |
| Flares | [docs/process/equipment/flares.md](process/equipment/flares) | Flare systems |
| Adsorbers | [docs/process/equipment/adsorbers.md](process/equipment/adsorbers) | Gas adsorption equipment |
| Power Generation | [docs/process/equipment/power_generation.md](process/equipment/power_generation) | Gas turbines, fuel cells, renewables |
| Diff. Pressure | [docs/process/equipment/differential_pressure.md](process/equipment/differential_pressure) | Orifice plates, flow measurement |
| Manifolds | [docs/process/equipment/manifolds.md](process/equipment/manifolds) | Multi-stream routing |
| **Battery Storage** | [docs/process/equipment/battery_storage.md](process/equipment/battery_storage) | **Energy storage systems, charge/discharge cycles, grid integration** |
| Solar Panel | [docs/wiki/solar_panel.md](wiki/solar_panel) | Solar panel models |
| **Failure Mode Modeling** | [docs/process/equipment/failure_modes.md](process/equipment/failure_modes) | **Equipment failure modes, reliability analysis, MTBF/MTTR calculations** |

### Chapter 19: Wells, Pipelines & Subsea

| Document | Path | Description |
|----------|------|-------------|
| Wells | [docs/process/equipment/wells.md](process/equipment/wells) | Well equipment |
| Well Simulation | [docs/simulation/well_simulation_guide.md](simulation/well_simulation_guide) | Well simulation guide |
| Well & Choke | [docs/simulation/well_and_choke_simulation.md](simulation/well_and_choke_simulation) | Choke valve simulation |
| Well Allocation | [docs/process/equipment/well_allocation.md](process/equipment/well_allocation) | Production allocation for commingled wells, VFM, well test methods |
| Pipelines | [docs/process/equipment/pipelines.md](process/equipment/pipelines) | Pipeline and riser models |
| **TwoFluidPipe Model** | [docs/process/TWOFLUIDPIPE_MODEL.md](process/TWOFLUIDPIPE_MODEL) | **Two-fluid multiphase flow model** |
| **TwoFluidPipe OLGA Comparison** | [docs/wiki/two_fluid_model_olga_comparison.md](wiki/two_fluid_model_olga_comparison) | **Mathematical equations, slug flow physics, Lagrangian tracking** |
| **TwoFluidPipe Tutorial (Jupyter)** | [docs/examples/TwoFluidPipe_Tutorial.ipynb](examples/TwoFluidPipe_Tutorial.ipynb) | **Interactive notebook: multiphase flow, slug tracking, terrain effects, heat transfer** |
| **Risers** | [docs/process/equipment/pipelines.md#risers](process/equipment/pipelines#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Beggs & Brill | [docs/process/PipeBeggsAndBrills.md](process/PipeBeggsAndBrills) | Beggs & Brill correlation |
| Networks | [docs/process/equipment/networks.md](process/equipment/networks) | Pipeline network modeling |
| **Looped Pipeline Networks** | [docs/process/equipment/looped_networks.md](process/equipment/looped_networks) | **Hardy Cross solver, ring mains, parallel pipelines, loop detection** |
| **Looped Network Solver** | [docs/process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md](process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT) | **Hardy Cross looped network solver for ring mains and parallel pipelines** |
| **Looped Network Tutorial** | [docs/examples/LoopedPipelineNetworkExample.ipynb](examples/LoopedPipelineNetworkExample.ipynb) | **Interactive notebook: ring mains, offshore rings, loop detection, Hardy Cross** |
| **Network Solver Tutorial** | [docs/examples/NetworkSolverTutorial.md](examples/NetworkSolverTutorial) | **Tutorial for pipeline network solvers with worked examples** |
| **Pipe Fittings & Equivalent Length** | [docs/process/PIPE_FITTINGS_EQUIVALENT_LENGTH.md](process/PIPE_FITTINGS_EQUIVALENT_LENGTH) | **Equivalent length method for fittings: elbows, tees, valves, reducers per CRANE TP-410** |
| Reservoirs | [docs/process/equipment/reservoirs.md](process/equipment/reservoirs) | Reservoir modeling |
| Subsea Systems | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems) | Subsea wells and flowlines |
| Subsea Equipment | [docs/process/equipment/subsea_equipment.md](process/equipment/subsea_equipment) | SubseaWell, SimpleFlowLine, flow assurance |
| **Subsea Trees** | [docs/process/equipment/subsea_trees.md](process/equipment/subsea_trees) | **Christmas trees: horizontal/vertical, dual-bore, valve configurations, wellhead integration** |
| **Subsea Manifolds** | [docs/process/equipment/subsea_manifolds.md](process/equipment/subsea_manifolds) | **Production/injection manifolds, valve skids, well routing, gathering systems** |
| **Subsea Boosters** | [docs/process/equipment/subsea_boosters.md](process/equipment/subsea_boosters) | **Subsea pumps & compressors, helico-axial/multiphase, performance curves** |
| **Umbilicals** | [docs/process/equipment/umbilicals.md](process/equipment/umbilicals) | **Control umbilicals: hydraulic, electrical, chemical injection lines** |
| **SURF Subsea Equipment** | [docs/process/SURF_SUBSEA_EQUIPMENT.md](process/SURF_SUBSEA_EQUIPMENT) | **Comprehensive SURF equipment: PLET, PLEM, manifolds, trees, jumpers, umbilicals, flexible pipes, boosters with mechanical design and cost estimation** |

### Chapter 20: Utility Equipment

| Document | Path | Description |
|----------|------|-------------|
| Utility Overview | [docs/process/equipment/util/README.md](process/equipment/util/) | Utility equipment |
| Adjusters | [docs/process/equipment/util/adjusters.md](process/equipment/util/adjusters) | Adjuster units |
| Recycles | [docs/process/equipment/util/recycles.md](process/equipment/util/recycles) | Recycle units |
| Calculators | [docs/process/equipment/util/calculators.md](process/equipment/util/calculators) | Calculator units |
| **Stream Fitters** | [docs/process/equipment/util/stream_fitters.md](process/equipment/util/stream_fitters) | **GORfitter, MPFMfitter: GOR/GVF adjustment, MPFM reference fluids** |

### Chapter 21: Process Control

| Document | Path | Description |
|----------|------|-------------|
| Controllers | [docs/process/controllers.md](process/controllers) | Process controllers |
| Process Control | [docs/wiki/process_control.md](wiki/process_control) | Control systems |
| Dynamic Simulation Guide | [docs/simulation/dynamic_simulation_guide.md](simulation/dynamic_simulation_guide) | Comprehensive dynamic/transient simulation guide |
| Transient Simulation | [docs/wiki/process_transient_simulation_guide.md](wiki/process_transient_simulation_guide) | Transient simulation patterns |

### Chapter 22: Optimization and Constraints

> **January 2026 Update:** ProductionOptimizer now includes configuration validation, stagnation detection, warm start, bounded LRU cache, and infeasibility diagnostics. See updated tutorials for details.

| Document | Path | Description |
|----------|------|-------------|
| **Optimization & Constraints Guide** | [docs/process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md](process/optimization/OPTIMIZATION_AND_CONSTRAINTS) | **COMPREHENSIVE: Complete guide to optimization algorithms, constraint types, bottleneck analysis, and practical examples** |
| **Optimization Overview** | [docs/process/optimization/OPTIMIZATION_OVERVIEW.md](process/optimization/OPTIMIZATION_OVERVIEW) | **START HERE: Introduction to process optimization, when to use ProcessOptimizationEngine vs ProductionOptimizer** |
| **ProductionOptimizer Tutorial** | [docs/examples/ProductionOptimizer_Tutorial.md](examples/ProductionOptimizer_Tutorial) | **Interactive Jupyter notebook with complete ProductionOptimizer guide: algorithms, single/multi-variable, Pareto, constraints** |
| **Python Optimization Tutorial** | [docs/examples/NeqSim_Python_Optimization.md](examples/NeqSim_Python_Optimization) | **Using SciPy/Python optimizers with NeqSim process simulations: constraints, Pareto, global optimization** |
| **Capacity Constraint Framework** | [docs/process/CAPACITY_CONSTRAINT_FRAMEWORK.md](process/CAPACITY_CONSTRAINT_FRAMEWORK) | **Framework for equipment capacity limits, bottleneck detection, utilization tracking, and AIV/FIV vibration analysis** |
| **Optimizer Plugin Architecture** | [docs/process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE.md](process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE) | **Equipment capacity strategies, throughput optimization, gradient descent, sensitivity analysis, shadow prices, and Eclipse VFP export** |
| **External Optimizer Integration** | [docs/integration/EXTERNAL_OPTIMIZER_INTEGRATION.md](integration/EXTERNAL_OPTIMIZER_INTEGRATION) | **ProcessSimulationEvaluator for Python/SciPy/NLopt/Pyomo integration with gradient estimation** |
| **Production Optimization Guide** | [docs/examples/PRODUCTION_OPTIMIZATION_GUIDE.md](examples/PRODUCTION_OPTIMIZATION_GUIDE) | **Complete guide to production optimization with Java and Python examples** |
| **Pressure Boundary Optimization** | [docs/process/pressure_boundary_optimization.md](process/pressure_boundary_optimization) | **Calculate flow rates for pressure boundaries, generate Eclipse VFP lift curves, optimize compressor power** |
| **Flow Rate Optimization** | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization) | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation** |
| **Compressor Optimization Guide** | [docs/process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE.md](process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE) | **Specialized guide for compressor train optimization, anti-surge control, and power minimization** |
| **Practical Examples** | [docs/process/optimization/PRACTICAL_EXAMPLES.md](process/optimization/PRACTICAL_EXAMPLES) | **Working examples for optimization scenarios including gas processing, LNG, and offshore platforms** |

### Chapter 23: Mechanical Design

| Document | Path | Description |
|----------|------|-------------|
| Mechanical Design | [docs/process/mechanical_design.md](process/mechanical_design) | Mechanical design overview, process design parameters, validation, and JSON export |
| **Equipment Design Parameters** | [docs/process/EQUIPMENT_DESIGN_PARAMETERS.md](process/EQUIPMENT_DESIGN_PARAMETERS) | **Comprehensive guide to autoSize vs MechanicalDesign, manual sizing, validation methods, and capacity constraints** |
| **Process Design Parameters** | [docs/process/mechanical_design.md#process-design-parameters](process/mechanical_design#process-design-parameters) | **Industry-standard process design parameters for separators, compressors, pumps, heat exchangers** |
| **Design Validation** | [docs/process/mechanical_design.md#design-validation](process/mechanical_design#design-validation) | **Validation methods per API-610, API-617, TEMA, API-12J standards** |
| **Mechanical Design Report** | [docs/process/mechanical_design.md#comprehensive-mechanical-design-report-json](process/mechanical_design#comprehensive-mechanical-design-report-json) | **Combined JSON output for all mechanical design data (equipment + piping)** |
| Design Standards | [docs/process/mechanical_design_standards.md](process/mechanical_design_standards) | Design standards |
| Design Database | [docs/process/mechanical_design_database.md](process/mechanical_design_database) | Design database |
| **Pipeline Mechanical Design** | [docs/process/pipeline_mechanical_design.md](process/pipeline_mechanical_design) | **Comprehensive pipeline mechanical design with wall thickness, stress analysis, cost estimation** |
| **Topside Piping Design** | [docs/process/topside_piping_design.md](process/topside_piping_design) | **Topside piping design with velocity, support spacing, vibration (AIV/FIV), stress analysis per ASME B31.3** |
| **Manifold Mechanical Design** | [docs/process/equipment/manifold_design.md](process/equipment/manifold_design) | **Manifold design for topside, onshore, and subsea with velocity limits, reinforcement, support per ASME B31.3 and DNV-ST-F101** |
| **Riser Mechanical Design** | [docs/process/riser_mechanical_design.md](process/riser_mechanical_design) | **Riser design with catenary mechanics, VIV, fatigue per DNV-OS-F201** |
| **Pipeline Design Math** | [docs/process/pipeline_mechanical_design_math.md](process/pipeline_mechanical_design_math) | **Mathematical methods and formulas for pipeline design** |
| **Subsea SURF Mechanical Design** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#mechanical-design](process/SURF_SUBSEA_EQUIPMENT#mechanical-design) | **Mechanical design for PLET, PLEM, trees, manifolds, jumpers, umbilicals, flexible pipes, boosters per DNV, API, ISO, NORSOK** |
| TORG Integration | [docs/process/torg_integration.md](process/torg_integration) | TORG integration |
| Field Development | [docs/process/field_development_orchestration.md](process/field_development_orchestration) | Field development orchestration |

### Chapter 23b: Cost Estimation

| Document | Path | Description |
|----------|------|-------------|
| **Cost Estimation Framework** | [docs/process/COST_ESTIMATION_FRAMEWORK.md](process/COST_ESTIMATION_FRAMEWORK) | **Comprehensive capital and operating cost estimation framework** |
| **Cost Estimation API** | [docs/process/COST_ESTIMATION_API_REFERENCE.md](process/COST_ESTIMATION_API_REFERENCE) | **Detailed API reference for all cost estimation classes** |
| **Subsea SURF Cost Estimation** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#cost-estimation](process/SURF_SUBSEA_EQUIPMENT#cost-estimation) | **Cost estimation for all SURF equipment with regional factors, labor rates, vessel costs, BOM generation** |
| Equipment Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#equipment-cost-estimation](process/COST_ESTIMATION_FRAMEWORK#equipment-cost-estimation) | Equipment-specific cost correlations |
| Tank Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#tank-cost](process/COST_ESTIMATION_FRAMEWORK#tank-cost) | Storage tank cost estimation (API 650/620) |
| Expander Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#expander-cost](process/COST_ESTIMATION_FRAMEWORK#expander-cost) | Turboexpander cost estimation |
| Ejector Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#ejector-cost](process/COST_ESTIMATION_FRAMEWORK#ejector-cost) | Ejector and vacuum system costs |
| Absorber Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#absorber-cost](process/COST_ESTIMATION_FRAMEWORK#absorber-cost) | Absorption tower cost estimation |
| Currency & Location | [docs/process/COST_ESTIMATION_FRAMEWORK.md#currency-and-location-support](process/COST_ESTIMATION_FRAMEWORK#currency-and-location-support) | Multi-currency and location factors |
| OPEX Estimation | [docs/process/COST_ESTIMATION_FRAMEWORK.md#operating-cost-opex-estimation](process/COST_ESTIMATION_FRAMEWORK#operating-cost-opex-estimation) | Operating cost calculation |
| Financial Metrics | [docs/process/COST_ESTIMATION_FRAMEWORK.md#financial-metrics](process/COST_ESTIMATION_FRAMEWORK#financial-metrics) | Payback, ROI, NPV calculations |

### Chapter 24: Serialization & Persistence

| Document | Path | Description |
|----------|------|-------------|
| Process Serialization | [docs/simulation/process_serialization.md](simulation/process_serialization) | Save/load process models |
| Process Model Lifecycle | [docs/process/lifecycle/process_model_lifecycle.md](process/lifecycle/process_model_lifecycle) | ProcessModelState, versioning, checkpointing, digital twin lifecycle |

---

## Part IV: Pipeline & Multiphase Flow

### Chapter 24: Pipeline Fundamentals

| Document | Path | Description |
|----------|------|-------------|
| Fluid Mechanics Overview | [docs/fluidmechanics/README.md](fluidmechanics/) | Fluid mechanics module |
| Pipeline Index | [docs/wiki/pipeline_index.md](wiki/pipeline_index) | Pipeline documentation index |
| **Pipeline Simulation** | [docs/process/equipment/pipeline_simulation.md](process/equipment/pipeline_simulation) | **Comprehensive pipeline simulation guide with PipeLineInterface, all pipe types, flow regimes, heat transfer** |
| Flow Equations | [docs/wiki/pipeline_flow_equations.md](wiki/pipeline_flow_equations) | Pipeline flow equations |
| Single Phase Flow | [docs/fluidmechanics/single_phase_pipe_flow.md](fluidmechanics/single_phase_pipe_flow) | Single phase pipe flow |
| **Flow Pattern Detection** | [docs/fluidmechanics/flow_pattern_detection.md](fluidmechanics/flow_pattern_detection) | **Taitel-Dukler, Baker, Barnea models, FlowPatternDetector API** |

### Chapter 25: Pressure Drop Calculations

| Document | Path | Description |
|----------|------|-------------|
| Pressure Drop | [docs/wiki/pipeline_pressure_drop.md](wiki/pipeline_pressure_drop) | Pressure drop calculation |
| Beggs & Brill | [docs/wiki/beggs_and_brill_correlation.md](wiki/beggs_and_brill_correlation) | Beggs & Brill correlation |
| Friction Factors | [docs/wiki/friction_factor_models.md](wiki/friction_factor_models) | Friction factor models |

### Chapter 26: Heat Transfer in Pipelines

| Document | Path | Description |
|----------|------|-------------|
| Heat Transfer | [docs/wiki/pipeline_heat_transfer.md](wiki/pipeline_heat_transfer) | Pipeline heat transfer |
| Heat Transfer Module | [docs/fluidmechanics/heat_transfer.md](fluidmechanics/heat_transfer) | Heat transfer module |
| Pipe Wall | [docs/wiki/pipe_wall_heat_transfer.md](wiki/pipe_wall_heat_transfer) | Pipe wall heat transfer |
| Interphase | [docs/fluidmechanics/InterphaseHeatMassTransfer.md](fluidmechanics/InterphaseHeatMassTransfer) | Interphase heat/mass transfer |
| Mass Transfer | [docs/fluidmechanics/mass_transfer.md](fluidmechanics/mass_transfer) | Mass transfer models |
| **Mass Transfer API** | [docs/fluidmechanics/MassTransferAPI.md](fluidmechanics/MassTransferAPI) | **Complete API documentation for mass transfer with methods, parameters, and examples** |
| **Evaporation & Dissolution Tutorial** | [docs/fluidmechanics/EvaporationDissolutionTutorial.md](fluidmechanics/EvaporationDissolutionTutorial) | **Practical tutorial for liquid evaporation and gas dissolution with worked examples** |
| **Model Improvements** | [docs/fluidmechanics/MASS_TRANSFER_MODEL_IMPROVEMENTS.md](fluidmechanics/MASS_TRANSFER_MODEL_IMPROVEMENTS) | **Technical review of mass transfer model with improvement recommendations** |

### Chapter 27: Two-Phase & Multiphase Flow

| Document | Path | Description |
|----------|------|-------------|
| Two-Phase Model | [docs/fluidmechanics/TwoPhasePipeFlowModel.md](fluidmechanics/TwoPhasePipeFlowModel) | Two-phase pipe flow |
| Two-Fluid Model | [docs/wiki/two_fluid_model.md](wiki/two_fluid_model) | Two-fluid model |
| Multiphase Transient | [docs/wiki/multiphase_transient_model.md](wiki/multiphase_transient_model) | Multiphase transient |
| Transient Pipe Wiki | [docs/wiki/transient_multiphase_pipe.md](wiki/transient_multiphase_pipe) | Transient multiphase pipe |
| Development Plan | [docs/fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md](fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan) | Development plan |

### Chapter 28: Transient Pipeline Simulation

| Document | Path | Description |
|----------|------|-------------|
| Transient Simulation | [docs/wiki/pipeline_transient_simulation.md](wiki/pipeline_transient_simulation) | Transient pipeline |
| Model Recommendations | [docs/wiki/pipeline_model_recommendations.md](wiki/pipeline_model_recommendations) | Model recommendations |
| Water Hammer | [docs/wiki/water_hammer_implementation.md](wiki/water_hammer_implementation) | Water hammer |

---

## Part V: Safety & Reliability

### Chapter 29: Safety Overview

| Document | Path | Description |
|----------|------|-------------|
| Safety Overview | [docs/safety/README.md](safety/) | Safety systems module |
| Safety Roadmap | [docs/safety/SAFETY_SIMULATION_ROADMAP.md](safety/SAFETY_SIMULATION_ROADMAP) | Safety simulation roadmap |
| Layered Architecture | [docs/safety/layered_safety_architecture.md](safety/layered_safety_architecture) | Layered safety architecture |
| Process Safety | [docs/process/safety/README.md](process/safety/) | Process safety module |

### Chapter 30: Alarm Systems

| Document | Path | Description |
|----------|------|-------------|
| Alarm System Guide | [docs/safety/alarm_system_guide.md](safety/alarm_system_guide) | Alarm system configuration |
| Alarm Logic Example | [docs/safety/alarm_triggered_logic_example.md](safety/alarm_triggered_logic_example) | Alarm-triggered logic |
| ESD Fire Alarm | [docs/wiki/esd_fire_alarm_system.md](wiki/esd_fire_alarm_system) | ESD/Fire alarm systems |

### Chapter 31: Pressure Relief Systems

| Document | Path | Description |
|----------|------|-------------|
| PSV Dynamic Sizing Wiki | [docs/wiki/psv_dynamic_sizing_example.md](wiki/psv_dynamic_sizing_example) | PSV dynamic sizing |
| PSV Dynamic Sizing | [docs/safety/psv_dynamic_sizing_example.md](safety/psv_dynamic_sizing_example) | PSV sizing example |
| PSD Valve Trip | [docs/wiki/psd_valve_hihi_trip.md](wiki/psd_valve_hihi_trip) | PSD valve HIHI trip |
| Rupture Disks | [docs/safety/rupture_disk_dynamic_behavior.md](safety/rupture_disk_dynamic_behavior) | Rupture disk behavior |

### Chapter 32: HIPPS Systems

| Document | Path | Description |
|----------|------|-------------|
| HIPPS Summary | [docs/safety/HIPPS_SUMMARY.md](safety/HIPPS_SUMMARY) | HIPPS summary |
| HIPPS Implementation | [docs/safety/hipps_implementation.md](safety/hipps_implementation) | HIPPS implementation |
| HIPPS Safety Logic | [docs/safety/hipps_safety_logic.md](safety/hipps_safety_logic) | HIPPS safety logic |

### Chapter 33: ESD & Fire Systems

| Document | Path | Description |
|----------|------|-------------|
| ESD Blowdown | [docs/safety/ESD_BLOWDOWN_SYSTEM.md](safety/ESD_BLOWDOWN_SYSTEM) | ESD blowdown system |
| Pressure Monitoring | [docs/safety/PRESSURE_MONITORING_ESD.md](safety/PRESSURE_MONITORING_ESD) | Pressure monitoring ESD |
| Fire Heat Transfer | [docs/safety/fire_heat_transfer_enhancements.md](safety/fire_heat_transfer_enhancements) | Fire heat transfer |
| Fire Blowdown | [docs/safety/fire_blowdown_capabilities.md](safety/fire_blowdown_capabilities) | Fire blowdown capabilities |

### Chapter 34: Integrated Safety Systems

| Document | Path | Description |
|----------|------|-------------|
| Integrated Safety | [docs/safety/INTEGRATED_SAFETY_SYSTEMS.md](safety/INTEGRATED_SAFETY_SYSTEMS) | Integrated safety systems |
| SIS Logic | [docs/safety/sis_logic_implementation.md](safety/sis_logic_implementation) | SIS logic implementation |
| Choke Protection | [docs/wiki/choke_collapse_psd_protection.md](wiki/choke_collapse_psd_protection) | Choke collapse protection |
| Safety Chain Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests) | Safety chain tests |
| Scenario Generation | [docs/process/safety/scenario-generation.md](process/safety/scenario-generation) | Automatic scenario generation |

### Chapter 35: Risk Simulation Framework

Comprehensive operational risk simulation framework for equipment failure analysis, production impact assessment, and degraded operation optimization. Includes Monte Carlo simulation, 5×5 risk matrix, process topology analysis, STID tagging per ISO 14224/NORSOK, and dependency analysis with cross-installation support.

| Document | Path | Description |
|----------|------|-------------|
| **Risk Framework Index** | [docs/risk/index.md](risk/index) | **START HERE**: Quick start guide, architecture overview, package structure |
| **Framework Overview** | [docs/risk/overview.md](risk/overview) | Core concepts, capabilities, industry standards compliance (ISO 14224, OREDA, NORSOK) |
| **Equipment Failure Modeling** | [docs/risk/equipment-failure.md](risk/equipment-failure) | FailureType enum, capacity factors, OREDA reliability data, λ/R(t)/MTTF formulas |
| **Risk Matrix** | [docs/risk/risk-matrix.md](risk/risk-matrix) | 5×5 probability/consequence matrix, risk scoring, cost calculations |
| **Monte Carlo Simulation** | [docs/risk/monte-carlo.md](risk/monte-carlo) | OperationalRiskSimulator, exponential sampling, P10/P50/P90 statistics, convergence |
| **Production Impact Analysis** | [docs/risk/production-impact.md](risk/production-impact) | Loss calculations, criticality index, cascade analysis, economic impact |
| **Degraded Operation** | [docs/risk/degraded-operation.md](risk/degraded-operation) | DegradedOperationOptimizer, recovery planning, operating modes |
| **Process Topology** | [docs/risk/topology.md](risk/topology) | ProcessTopologyAnalyzer, graph extraction, topological ordering, DOT/JSON export |
| **STID Tagging** | [docs/risk/stid-tagging.md](risk/stid-tagging) | FunctionalLocation class, PPPP-TT-NNNNN[S] format, installation/equipment codes |
| **Dependency Analysis** | [docs/risk/dependency-analysis.md](risk/dependency-analysis) | DependencyAnalyzer, cascade failure trees, cross-installation effects |
| **Mathematical Reference** | [docs/risk/mathematical-reference.md](risk/mathematical-reference) | Complete formulas: reliability, system availability, Monte Carlo, risk calculations |
| **API Reference** | [docs/risk/api-reference.md](risk/api-reference) | Full API documentation for all risk simulation classes |
| **Reliability Data Guide** | [docs/risk/RELIABILITY_DATA_GUIDE.md](risk/RELIABILITY_DATA_GUIDE) | OREDA-based reliability data, failure rate sources, equipment categories |
| **Physics-Based Integration** | [docs/risk/PHYSICS_BASED_RISK_INTEGRATION.md](risk/PHYSICS_BASED_RISK_INTEGRATION) | **Integration of physics-based models with risk simulation for dynamic failure analysis** |

### Chapter 35a: Advanced Risk Framework (**NEW**)

Extended risk analysis capabilities implementing P1-P7 priority improvements for oil & gas industry applications.

| Document | Path | Description |
|----------|------|-------------|
| **Advanced Framework Overview** | [docs/risk/README.md](risk/) | **START HERE**: Overview of all 7 priority packages |
| **P1: Dynamic Simulation** | [docs/risk/dynamic-simulation.md](risk/dynamic-simulation) | Monte Carlo with transient effects, shutdown/startup modeling |
| **P2: SIS/SIF Integration** | [docs/risk/sis-integration.md](risk/sis-integration) | IEC 61508/61511, LOPA analysis, SIL verification |
| **P4: Bow-Tie Analysis** | [docs/risk/bowtie-analysis.md](risk/bowtie-analysis) | Barrier analysis, threat/consequence visualization |
| **P6: Condition-Based Reliability** | [docs/risk/condition-based.md](risk/condition-based) | Health monitoring, RUL estimation, predictive maintenance |
| **Tutorial Notebook** | [docs/examples/AdvancedRiskFramework_Tutorial.ipynb](examples/AdvancedRiskFramework_Tutorial.ipynb) | Comprehensive Jupyter tutorial |

#### Advanced Risk Framework Packages

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `process.safety.risk.dynamic` | P1: Transient simulation | `DynamicRiskSimulator`, `ProductionProfile`, `TransientLossStatistics` |
| `process.safety.risk.sis` | P2: Safety systems | `SafetyInstrumentedFunction`, `SISIntegratedRiskModel`, `LOPAResult` |
| `process.safety.risk.realtime` | P3: Digital twin | `RealTimeRiskMonitor`, `RealTimeRiskAssessment` |
| `process.safety.risk.bowtie` | P4: Barrier analysis | `BowTieAnalyzer`, `BowTieModel` |
| `process.safety.risk.portfolio` | P5: Portfolio risk | `PortfolioRiskAnalyzer`, `PortfolioRiskResult` |
| `process.safety.risk.condition` | P6: CBR | `ConditionBasedReliability` |
| `process.safety.risk.ml` | P7: ML integration | `RiskMLInterface`, `MLPrediction` |
| `process.safety.risk.examples` | Quick-start examples | `RiskFrameworkQuickStart` |

#### Key Classes in Risk Framework

| Class | Package | Purpose |
|-------|---------|---------|
| `EquipmentFailureMode` | `process.equipment.failure` | Failure mode definitions with OREDA data |
| `ReliabilityDataSource` | `process.equipment.failure` | OREDA-based reliability data access |
| `ProductionImpactAnalyzer` | `process.safety.risk` | Production loss analysis |
| `DegradedOperationOptimizer` | `process.safety.risk` | Degraded mode optimization |
| `OperationalRiskSimulator` | `process.safety.risk` | Monte Carlo simulation engine |
| `RiskMatrix` | `process.safety.risk` | 5×5 risk assessment matrix |
| `ProcessTopologyAnalyzer` | `process.util.topology` | Process graph extraction |
| `FunctionalLocation` | `process.util.topology` | STID tag parsing (ISO 14224) |
| `DependencyAnalyzer` | `process.util.topology` | Equipment dependency analysis |

---

## Part VI: PVT & Flow Assurance

### Chapter 35: PVT Simulation

| Document | Path | Description |
|----------|------|-------------|
| **Phase Envelope Guide** | [docs/pvtsimulation/phase_envelope_guide.md](pvtsimulation/phase_envelope_guide) | **Cricondenbar, cricondentherm, HCDP, bubble/dew points** |
| **PVT Lab Tests** | [docs/pvtsimulation/pvt_lab_tests.md](pvtsimulation/pvt_lab_tests) | **CCE, CVD, DL, separator test, swelling test simulations** |
| PVT Overview | [docs/pvtsimulation/README.md](pvtsimulation/) | PVT simulation module |
| PVT Workflows | [docs/wiki/pvt_simulation_workflows.md](wiki/pvt_simulation_workflows) | PVT simulation workflows |
| PVT Workflow Module | [docs/pvtsimulation/pvt_workflow.md](pvtsimulation/pvt_workflow) | PVT workflow module |
| Property Flash | [docs/wiki/property_flash_workflows.md](wiki/property_flash_workflows) | Property flash workflows |
| Whitson Reader | [docs/pvtsimulation/whitson_pvt_reader.md](pvtsimulation/whitson_pvt_reader) | Whitson PVT reader |
| Solution Gas-Water Ratio | [docs/pvtsimulation/SolutionGasWaterRatio.md](pvtsimulation/SolutionGasWaterRatio) | Rsw calculation methods (McCain, Søreide-Whitson, Electrolyte CPA) |

### Chapter 36: Black Oil Models

| Document | Path | Description |
|----------|------|-------------|
| Black Oil Overview | [docs/blackoil/README.md](blackoil/) | Black oil module |
| Flash Playbook | [docs/wiki/black_oil_flash_playbook.md](wiki/black_oil_flash_playbook) | Black oil flash playbook |
| Black Oil Export | [docs/pvtsimulation/blackoil_pvt_export.md](pvtsimulation/blackoil_pvt_export) | Black oil PVT export and E300 compositional export |

### Chapter 37: Flow Assurance

| Document | Path | Description |
|----------|------|-------------|
| Flow Assurance | [docs/pvtsimulation/flowassurance/README.md](pvtsimulation/flowassurance/) | Flow assurance module |
| Asphaltene Modeling | [docs/pvtsimulation/flowassurance/asphaltene_modeling.md](pvtsimulation/flowassurance/asphaltene_modeling) | Asphaltene modeling |
| Asphaltene CPA | [docs/pvtsimulation/flowassurance/asphaltene_cpa_calculations.md](pvtsimulation/flowassurance/asphaltene_cpa_calculations) | CPA calculations |
| De Boer Screening | [docs/pvtsimulation/flowassurance/asphaltene_deboer_screening.md](pvtsimulation/flowassurance/asphaltene_deboer_screening) | De Boer screening |
| Method Comparison | [docs/pvtsimulation/flowassurance/asphaltene_method_comparison.md](pvtsimulation/flowassurance/asphaltene_method_comparison) | Method comparison |
| Parameter Fitting | [docs/pvtsimulation/flowassurance/asphaltene_parameter_fitting.md](pvtsimulation/flowassurance/asphaltene_parameter_fitting) | Parameter fitting |
| Validation | [docs/pvtsimulation/flowassurance/asphaltene_validation.md](pvtsimulation/flowassurance/asphaltene_validation) | Validation |

### Chapter 38: Gas Quality

| Document | Path | Description |
|----------|------|-------------|
| Gas Quality Standards | [docs/wiki/gas_quality_standards_from_tests.md](wiki/gas_quality_standards_from_tests) | Gas quality standards |
| Humid Air | [docs/wiki/humid_air_math.md](wiki/humid_air_math) | Humid air calculations |

---

## Part VII: Standards & Quality

### Chapter 39: ISO Standards

| Document | Path | Description |
|----------|------|-------------|
| Standards Overview | [docs/standards/README.md](standards/) | Standards module |
| ISO 6976 | [docs/standards/iso6976_calorific_values.md](standards/iso6976_calorific_values) | ISO 6976 calorific values |
| ISO 6578 | [docs/standards/iso6578_lng_density.md](standards/iso6578_lng_density) | ISO 6578 LNG density |
| ISO 15403 | [docs/standards/iso15403_cng_quality.md](standards/iso15403_cng_quality) | ISO 15403 CNG quality |
| Dew Point | [docs/standards/dew_point_standards.md](standards/dew_point_standards) | Dew point standards |
| ASTM D6377 | [docs/standards/astm_d6377_rvp.md](standards/astm_d6377_rvp) | ASTM D6377 RVP |
| Sales Contracts | [docs/standards/sales_contracts.md](standards/sales_contracts) | Sales contracts |

---

## Part VIII: Advanced Topics

### Chapter 40: Future Infrastructure

| Document | Path | Description |
|----------|------|-------------|
| Future Infrastructure | [docs/process/future-infrastructure.md](process/future-infrastructure) | Future infrastructure classes |
| API Reference | [docs/process/future-api-reference.md](process/future-api-reference) | Future API reference |

### Chapter 41: Digital Twins

| Document | Path | Description |
|----------|------|-------------|
| Digital Twin | [docs/process/digital-twin-integration.md](process/digital-twin-integration) | Digital twin integration |
| Lifecycle | [docs/process/lifecycle/README.md](process/lifecycle/) | Lifecycle management |

### Chapter 42: AI/ML Integration

| Document | Path | Description |
|----------|------|-------------|
| AI Platform | [docs/integration/ai_platform_integration.md](integration/ai_platform_integration) | AI platform integration |
| AI Validation | [docs/integration/ai_validation_framework.md](integration/ai_validation_framework) | AI validation framework |
| AI Validation PR | [docs/integration/PR_AI_VALIDATION_FRAMEWORK.md](integration/PR_AI_VALIDATION_FRAMEWORK) | AI validation PR docs |
| ML Integration | [docs/integration/ml_integration.md](integration/ml_integration) | ML integration guide |
| ML Surrogate | [docs/process/ml/README.md](process/ml/) | ML surrogate models |
| Integration Overview | [docs/integration/README.md](integration/) | Integration module |

### Chapter 43: Sustainability & Emissions

| Document | Path | Description |
|----------|------|-------------|
| Emissions Tracking | [docs/process/sustainability/README.md](process/sustainability/) | Emissions tracking overview |
| **Offshore Emission Reporting Guide** | [docs/emissions/OFFSHORE_EMISSION_REPORTING.md](emissions/OFFSHORE_EMISSION_REPORTING) | **Comprehensive guide for offshore platform GHG emission reporting with regulatory references** |
| **Produced Water Emissions Tutorial** | [docs/examples/ProducedWaterEmissions_Tutorial.md](examples/ProducedWaterEmissions_Tutorial) | **Comprehensive tutorial for produced water degassing emissions calculation** |
| **Norwegian Emission Methods Comparison** | [docs/examples/NorwegianEmissionMethods_Comparison.md](examples/NorwegianEmissionMethods_Comparison) | **NeqSim vs Norwegian handbook method: validation, uncertainty, regulatory compliance** |
| **NeqSimLive Integration** | [docs/GFMW_2023_Emissions_Paper.txt](GFMW_2023_Emissions_Paper.txt) | **GFMW 2023 paper: Virtual measurement of emissions using online process simulator** |

### Chapter 44: Optimization

| Document | Path | Description |
|----------|------|-------------|
| Optimization Overview | [docs/process/optimization/README.md](process/optimization/) | Optimization module |
| **Flow Rate Optimization** | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization) | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation** |
| **Multi-Objective Optimization** | [docs/process/optimization/multi-objective-optimization.md](process/optimization/multi-objective-optimization) | **Pareto front generation for competing objectives (throughput vs energy)** |
| Batch Studies | [docs/process/optimization/batch-studies.md](process/optimization/batch-studies) | Batch studies |
| Bottleneck Analysis | [docs/wiki/bottleneck_analysis.md](wiki/bottleneck_analysis) | Bottleneck analysis and ProductionOptimizer |
| **Multi-Variable Optimization** | [docs/wiki/bottleneck_analysis.md#multi-variable-optimization-with-manipulatedvariable](wiki/bottleneck_analysis#multi-variable-optimization-with-manipulatedvariable) | **ManipulatedVariable for split factors, dual feeds, pressure setpoints** |
| Calibration | [docs/process/calibration/README.md](process/calibration/) | Model calibration |
| Advisory | [docs/process/advisory/README.md](process/advisory/) | Advisory systems |

### Chapter 45: Real-Time Integration

| Document | Path | Description |
|----------|------|-------------|
| Real-Time Guide | [docs/integration/REAL_TIME_INTEGRATION_GUIDE.md](integration/REAL_TIME_INTEGRATION_GUIDE) | Real-time integration |
| MPC Integration | [docs/integration/mpc_integration.md](integration/mpc_integration) | MPC integration |
| Industrial MPC | [docs/integration/neqsim_industrial_mpc_integration.md](integration/neqsim_industrial_mpc_integration) | Industrial MPC |

### Chapter 46: External Integrations

| Document | Path | Description |
|----------|------|-------------|
| DEXPI Reader | [docs/integration/dexpi-reader.md](integration/dexpi-reader) | DEXPI reader |
| QRA Integration | [docs/integration/QRA_INTEGRATION_GUIDE.md](integration/QRA_INTEGRATION_GUIDE) | QRA integration |

### Chapter 47: Process Logic Framework

| Document | Path | Description |
|----------|------|-------------|
| Simulation Overview | [docs/simulation/README.md](simulation/) | Simulation module |
| Process Logic | [docs/simulation/process_logic_framework.md](simulation/process_logic_framework) | Process logic framework |
| Advanced Logic | [docs/simulation/advanced_process_logic.md](simulation/advanced_process_logic) | Advanced process logic |
| Implementation | [docs/simulation/process_logic_implementation_summary.md](simulation/process_logic_implementation_summary) | Implementation summary |
| Enhancements | [docs/simulation/ProcessLogicEnhancements.md](simulation/ProcessLogicEnhancements) | Logic enhancements |
| Runtime Flexibility | [docs/simulation/RuntimeLogicFlexibility.md](simulation/RuntimeLogicFlexibility) | Runtime flexibility |
| Graph-Based | [docs/simulation/graph_based_process_simulation.md](simulation/graph_based_process_simulation) | Graph-based simulation |
| Parallel Simulation | [docs/simulation/parallel_process_simulation.md](simulation/parallel_process_simulation) | Parallel simulation |
| Recycle Acceleration | [docs/simulation/recycle_acceleration_guide.md](simulation/recycle_acceleration_guide) | Recycle acceleration |
| Process Calculator | [docs/simulation/process_calculator.md](simulation/process_calculator) | Process calculator |
| Integrated Workflow | [docs/simulation/INTEGRATED_WORKFLOW_GUIDE.md](simulation/INTEGRATED_WORKFLOW_GUIDE) | Integrated workflow |
| Differentiable Thermo | [docs/simulation/differentiable_thermodynamics.md](simulation/differentiable_thermodynamics) | Auto-differentiation |
| Derivatives | [docs/simulation/derivatives_and_gradients.md](simulation/derivatives_and_gradients) | Derivatives and gradients |
| Equipment Factory | [docs/simulation/equipment_factory.md](simulation/equipment_factory) | Equipment factory |

### Chapter 48: Field Development

| Document | Path | Description |
|----------|------|-------------|
| Field Development Overview | [docs/fielddevelopment/README.md](fielddevelopment/) | Field development module overview |
| **Digital Field Twin** | [docs/fielddevelopment/DIGITAL_FIELD_TWIN.md](fielddevelopment/DIGITAL_FIELD_TWIN) | **NEW** Comprehensive architecture for lifecycle consistency |
| **Mathematical Reference** | [docs/fielddevelopment/MATHEMATICAL_REFERENCE.md](fielddevelopment/MATHEMATICAL_REFERENCE) | **NEW** Mathematical foundations for all calculations |
| **API Guide** | [docs/fielddevelopment/API_GUIDE.md](fielddevelopment/API_GUIDE) | **NEW** Detailed usage examples for all components |
| **Integrated Framework** | [docs/fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK) | PVT→Reservoir→Well→Process integration guide |
| **Strategy** | [docs/fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md](fielddevelopment/FIELD_DEVELOPMENT_STRATEGY) | Field development strategy and roadmap |
| **Late-Life Operations** | [docs/fielddevelopment/LATE_LIFE_OPERATIONS.md](fielddevelopment/LATE_LIFE_OPERATIONS) | **Turndown, debottlenecking, and decommissioning timing analysis** |
| **Multi-Scenario VFP Generation** | [docs/fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION.md](fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION) | VFP tables with varying GOR/water cut for reservoir simulation coupling |
| Field Planning | [docs/wiki/field_development_planning.md](wiki/field_development_planning) | Field development planning |
| Field Engine | [docs/simulation/field_development_engine.md](simulation/field_development_engine) | Field development engine |
| **Economics** | [docs/process/economics/README.md](process/economics/) | Economics module: NPV, IRR, tax models, decline curves |
| **Subsea Systems** | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems) | Subsea production systems, tieback analysis |

#### Digital Field Twin Lifecycle Components

| Lifecycle Phase | Documentation | Key Capabilities |
|----------------|---------------|------------------|
| Screening (DG0-DG1) | DIGITAL_FIELD_TWIN §3 | Concept comparison, flow assurance screening, tieback analysis |
| Selection (DG2) | MATHEMATICAL_REFERENCE §4-5 | NPV calculation, MCDA ranking, Norwegian tax model |
| Definition (DG3) | API_GUIDE §5-6 | Process system auto-generation, network modeling |
| Execution (DG4) | DIGITAL_FIELD_TWIN §4 | VFP table export, reservoir coupling |
| Operations | DIGITAL_FIELD_TWIN §5 | Real-time optimization, Monte Carlo uncertainty |
| Late-Life | LATE_LIFE_OPERATIONS | Turndown, debottlenecking, decommissioning timing |

#### New Classes in This PR

| Class | Package | Purpose |
|-------|---------|---------|
| `PortfolioOptimizer` | `economics` | Multi-project investment optimization |
| `DevelopmentOptionRanker` | `evaluation` | MCDA-based concept ranking |
| `MonteCarloRunner` | `evaluation` | Probabilistic uncertainty analysis |
| `ConceptToProcessLinker` | `facility` | Auto-generate ProcessSystem from concept |
| `MultiphaseFlowIntegrator` | `network` | Pipeline hydraulics integration |
| `ReservoirCouplingExporter` | `reservoir` | VFP tables and ECLIPSE keywords |
| `TiebackAnalyzer` | `tieback` | Tieback feasibility screening |

---

## Part IX: Developer Guide

### Chapter 49: Contributing

| Document | Path | Description |
|----------|------|-------------|
| Development Overview | [docs/development/README.md](development/) | Development overview |
| Contributing Structure | [docs/development/contributing-structure.md](development/contributing-structure) | Contributing guidelines |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP) | Developer setup |
| **Extending Process Equipment** | [docs/development/extending_process_equipment.md](development/extending_process_equipment) | **NEW: Add custom separators, reactors, unit operations** |
| **Extending Physical Properties** | [docs/development/extending_physical_properties.md](development/extending_physical_properties) | **NEW: Add viscosity, conductivity, diffusivity models** |
| **Extending Thermodynamic Models** | [docs/development/extending_thermodynamic_models.md](development/extending_thermodynamic_models) | **NEW: Add custom equations of state** |
| **Python Extension Patterns** | [docs/development/python_extension_patterns.md](development/python_extension_patterns) | **NEW: Python integration, wrappers, JPype interfaces** |

### Chapter 50: Testing

| Document | Path | Description |
|----------|------|-------------|
| Test Overview | [docs/wiki/test-overview.md](wiki/test-overview) | Test overview |
| Flash Tests | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests) | Flash equation tests |
| Safety Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests) | Safety chain tests |

### Chapter 51: Notebooks & Examples

| Document | Path | Description |
|----------|------|-------------|
| **Reading Fluid Properties** | [docs/examples/ReadingFluidProperties.md](examples/ReadingFluidProperties) | **NEW: Comprehensive guide to reading thermodynamic and physical properties** |
| Colab Notebooks | [docs/wiki/java_simulation_from_colab_notebooks.md](wiki/java_simulation_from_colab_notebooks) | Colab notebooks |
| Transient Slug Example | [docs/examples/transient_slug_separator_control_example.md](examples/transient_slug_separator_control_example) | Transient slug example |
| Selective Logic | [docs/examples/selective-logic-execution.md](examples/selective-logic-execution) | Selective logic execution |
| Comparison Quickstart | [docs/examples/comparesimulations_quickstart.md](examples/comparesimulations_quickstart) | Simulation comparison |
| **PVT Simulation & Tuning** | [docs/examples/PVT_Simulation_and_Tuning.md](examples/PVT_Simulation_and_Tuning) | **PVT simulation setup, EoS tuning, and characterization examples** |
| **TVP/RVP Study** | [docs/examples/TVP_RVP_Study.md](examples/TVP_RVP_Study) | **True Vapor Pressure and Reid Vapor Pressure calculations** |
| **ESP Pump Tutorial** | [docs/examples/ESP_Pump_Tutorial.md](examples/ESP_Pump_Tutorial) | **Electric Submersible Pump simulation and sizing** |
| **Graph-Based Simulation** | [docs/examples/GraphBasedProcessSimulation.md](examples/GraphBasedProcessSimulation) | **Graph-based process simulation tutorial** |
| **Field Development Workflow** | [docs/examples/FieldDevelopmentWorkflow.md](examples/FieldDevelopmentWorkflow) | **End-to-end field development workflow example** |
| **Multi-Scenario VFP Tutorial** | [docs/examples/MultiScenarioVFP_Tutorial.ipynb](examples/MultiScenarioVFP_Tutorial) | **VFP generation with varying GOR/water cut scenarios** |
| **Production System Bottleneck Analysis** | [docs/examples/ProductionSystem_BottleneckAnalysis.ipynb](examples/ProductionSystem_BottleneckAnalysis) | **Multi-well system optimization, bottleneck identification, and well prioritization** |
| **Integrated Production & Risk Analysis** | [docs/examples/IntegratedProductionRiskAnalysis.ipynb](examples/IntegratedProductionRiskAnalysis) | **Complete operational workflow combining bottleneck analysis with risk simulation, Monte Carlo, and risk matrix** |
| **MPC Integration Tutorial** | [docs/examples/MPC_Integration_Tutorial.md](examples/MPC_Integration_Tutorial) | **Model Predictive Control integration example** |
| **AI Platform Integration** | [docs/examples/AIPlatformIntegration.md](examples/AIPlatformIntegration) | **AI platform integration tutorial** |
| Examples Index | [docs/examples/index.md](examples/index) | Examples documentation index |

### Chapter 52: Documentation Infrastructure

| Document | Path | Description |
|----------|------|-------------|
| **GitHub Pages Setup** | [docs/GITHUB_PAGES_SETUP.md](GITHUB_PAGES_SETUP) | **NEW** Enable GitHub Pages for hosted documentation |
| Reference Manual | [docs/manual/neqsim_reference_manual.html](manual/neqsim_reference_manual) | Interactive reference manual |
| Documentation Index | [docs/index.md](index) | GitHub Pages home page |

---

## Appendices

### Appendix A: Chemical Reactions

| Document | Path | Description |
|----------|------|-------------|
| Chemical Reactions | [docs/chemicalreactions/README.md](chemicalreactions/) | Chemical reactions module |
| Deep Review | [docs/chemicalreactions/CHEMICAL_REACTION_DEEP_REVIEW.md](chemicalreactions/CHEMICAL_REACTION_DEEP_REVIEW) | Chemical reaction deep review |

### Appendix B: Statistics

| Document | Path | Description |
|----------|------|-------------|
| Statistics | [docs/statistics/README.md](statistics/) | Statistics module |
| Parameter Fitting | [docs/statistics/parameter_fitting.md](statistics/parameter_fitting) | Parameter fitting |
| Monte Carlo | [docs/statistics/monte_carlo_simulation.md](statistics/monte_carlo_simulation) | Monte Carlo simulation |
| Data Analysis | [docs/statistics/data_analysis.md](statistics/data_analysis) | Data analysis |

### Appendix C: Mathematical Library

| Document | Path | Description |
|----------|------|-------------|
| Math Library | [docs/mathlib/README.md](mathlib/) | Mathematical library |

### Appendix D: Utilities

| Document | Path | Description |
|----------|------|-------------|
| Utilities | [docs/util/README.md](util/) | Utility functions |
| Unit Conversion | [docs/util/unit_conversion.md](util/unit_conversion) | Unit conversion guide |
| **Unit Conversion Recipes** | [docs/cookbook/unit-conversion-recipes.md](cookbook/unit-conversion-recipes) | **NEW** Quick reference for all supported unit strings |
| **Optimizer Guide** | [docs/util/optimizer_guide.md](util/optimizer_guide) | **NEW** Comprehensive optimization framework with BFGS, Pareto, sensitivity analysis |

### Appendix F: Process Design Templates

| Document | Path | Description |
|----------|------|-------------|
| **Templates Guide** | [docs/process/design/templates_guide.md](process/design/templates_guide) | **NEW** Pre-built process templates (compression, dehydration, CO2 capture) |

### Appendix G: Mechanical Design Standards

| Document | Path | Description |
|----------|------|-------------|
| **TEMA Standard Guide** | [docs/process/mechanical_design/tema_standard_guide.md](process/mechanical_design/tema_standard_guide) | **NEW** TEMA shell and tube heat exchanger design standards |

### Appendix H: Cookbook (Quick Recipes)

| Document | Path | Description |
|----------|------|-------------|
| **Cookbook Index** | [docs/cookbook/index.md](cookbook/index) | **NEW** Quick copy-paste recipes for common tasks |
| Thermodynamics Recipes | [docs/cookbook/thermodynamics-recipes.md](cookbook/thermodynamics-recipes) | Fluids, flash, properties, phase envelopes |
| Process Recipes | [docs/cookbook/process-recipes.md](cookbook/process-recipes) | Separators, compressors, heat exchangers |
| Pipeline Recipes | [docs/cookbook/pipeline-recipes.md](cookbook/pipeline-recipes) | Pressure drop, multiphase flow |

### Appendix I: Troubleshooting

| Document | Path | Description |
|----------|------|-------------|
| **Troubleshooting Guide** | [docs/troubleshooting/index.md](troubleshooting/index) | **NEW** Solutions to common problems |

### Appendix E: Wiki Reference

| Document | Path | Description |
|----------|------|-------------|
| Wiki Overview | [docs/wiki/README.md](wiki/) | Wiki documentation |

---

## Document Statistics

| Category | Count |
|----------|-------|
| Wiki/Tutorials | 60 |
| Thermodynamics | 26 |
| Process Simulation | 47 |
| Safety Systems | 18 |
| **Risk Simulation** | **13** |
| Field Development | 11 |
| Integration/AI | 12 |
| Pipeline/Flow | 17 |
| PVT/Reservoir | 15 |
| Standards | 6 |
| Development | 8 |
| Statistics | 4 |
| Examples | 13 |
| **Optimization** | **5** |
| **Templates & Design** | **2** |
| **Quickstart Guides** | **4** |
| **Cookbook** | **5** |
| **Tutorials/Learning** | **2** |
| **Troubleshooting** | **1** |
| Other | 24 |
| **Total** | **293** |

---

## Navigation Tips

1. **Start Here**: If new to NeqSim, begin with [Quickstart Guides](quickstart/index) or [Getting Started](wiki/getting_started)
2. **Learning Path**: Follow the [Learning Paths](tutorials/learning-paths) for structured learning
3. **Quick Recipes**: Use the [Cookbook](cookbook/index) for copy-paste solutions
4. **By Task**: Use the chapter structure above to find relevant sections
5. **By Equipment**: See Part III, Chapters 13-20 for equipment-specific docs
6. **For Developers**: Jump to Part IX for contribution guidelines
7. **Problems?**: Check the [Troubleshooting Guide](troubleshooting/index)
8. **Process Serialization**: See [Chapter 23](simulation/process_serialization) for save/load

---

*This index was updated after comprehensive documentation review. Last updated: February 2026*
