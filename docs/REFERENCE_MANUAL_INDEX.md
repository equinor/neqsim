# NeqSim Reference Manual - Master Index

This document maps all 230+ documentation files to the reference manual structure. Use this as a comprehensive table of contents and navigation guide.

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
| Overview | [docs/README.md](README.md) | Package overview and quick start |
| Modules | [docs/modules.md](modules.md) | Architecture and module structure |
| **O&G Design Review** | [docs/NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW.md](NEQSIM_OIL_GAS_DESIGN_OPERATIONS_REVIEW.md) | **Comprehensive capability review for oil & gas design and operations** |

### Chapter 2: Installation & Setup
| Document | Path | Description |
|----------|------|-------------|
| Getting Started | [docs/wiki/getting_started.md](wiki/getting_started.md) | Installation and first steps |
| GitHub Setup | [docs/wiki/Getting-started-with-NeqSim-and-Github.md](wiki/Getting-started-with-NeqSim-and-Github.md) | NeqSim and GitHub setup |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP.md) | Development environment setup |

### Chapter 3: Quick Start Examples
| Document | Path | Description |
|----------|------|-------------|
| Usage Examples | [docs/wiki/usage_examples.md](wiki/usage_examples.md) | Basic usage patterns |
| FAQ | [docs/wiki/faq.md](wiki/faq.md) | Frequently asked questions |
| Wiki Index | [docs/wiki/index.md](wiki/index.md) | Wiki documentation index |

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
| Thermo Overview | [docs/thermo/README.md](thermo/README.md) | Thermodynamics module overview |
| Thermodynamics Guide | [docs/wiki/thermodynamics_guide.md](wiki/thermodynamics_guide.md) | Comprehensive thermodynamics guide |
| System Types | [docs/thermo/system/README.md](thermo/system/README.md) | EoS system implementations |

### Chapter 5: Fluid Creation & Components
| Document | Path | Description |
|----------|------|-------------|
| Fluid Creation Guide | [docs/thermo/fluid_creation_guide.md](thermo/fluid_creation_guide.md) | Creating thermodynamic systems |
| **Fluid Classification** | [docs/thermo/fluid_classification.md](thermo/fluid_classification.md) | **Whitson methodology: FluidClassifier, ReservoirFluidType, GOR/C7+ classification** |
| Component Database | [docs/thermo/component_database_guide.md](thermo/component_database_guide.md) | Component properties and database |
| Component Package | [docs/thermo/component/README.md](thermo/component/README.md) | Component class documentation |
| Mathematical Models | [docs/thermo/mathematical_models.md](thermo/mathematical_models.md) | Underlying mathematical models |

### Chapter 6: Equations of State
| Document | Path | Description |
|----------|------|-------------|
| Thermodynamic Models | [docs/thermo/thermodynamic_models.md](thermo/thermodynamic_models.md) | **Comprehensive guide** to all thermodynamic models (EoS, CPA, GERG, electrolytes, GE models) with theory and usage |
| GERG-2008 | [docs/thermo/gerg2008_eoscg.md](thermo/gerg2008_eoscg.md) | GERG-2008 equation of state |
| Mixing Rules | [docs/thermo/mixing_rules_guide.md](thermo/mixing_rules_guide.md) | Mixing rules and BIPs |
| Mixing Rule Package | [docs/thermo/mixingrule/README.md](thermo/mixingrule/README.md) | Mixing rule implementations |
| Phase Package | [docs/thermo/phase/README.md](thermo/phase/README.md) | Phase modeling |
| Electrolyte CPA | [docs/thermo/ElectrolyteCPAModel.md](thermo/ElectrolyteCPAModel.md) | Electrolyte CPA model |

### Chapter 7: Flash Calculations
| Document | Path | Description |
|----------|------|-------------|
| Flash Guide | [docs/thermo/flash_calculations_guide.md](thermo/flash_calculations_guide.md) | Flash calculation methods |
| Flash Equations | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests.md) | Flash equations and testing |
| Thermo Operations | [docs/thermo/thermodynamic_operations.md](thermo/thermodynamic_operations.md) | Thermodynamic operations |
| TP Flash Algorithm | [docs/thermodynamicoperations/TPflash_algorithm.md](thermodynamicoperations/TPflash_algorithm.md) | TP flash algorithm details |
| Thermo Ops Overview | [docs/thermodynamicoperations/README.md](thermodynamicoperations/README.md) | Thermodynamic operations module |

### Chapter 8: Fluid Characterization

Fluid characterization handles plus fraction splitting, property estimation, and lumping. **New:** A fluent API (`configureLumping()`) makes lumping configuration clearer - see the mathematics document for details.

| Document | Path | Description |
|----------|------|-------------|
| Characterization | [docs/wiki/fluid_characterization.md](wiki/fluid_characterization.md) | Fluid characterization guide with lumping API |
| TBP Fractions | [docs/wiki/tbp_fraction_models.md](wiki/tbp_fraction_models.md) | TBP fraction modeling |
| PVT Characterization | [docs/thermo/pvt_fluid_characterization.md](thermo/pvt_fluid_characterization.md) | PVT fluid characterization with lumping |
| Characterization Package | [docs/thermo/characterization/README.md](thermo/characterization/README.md) | Characterization module |
| Combining Methods | [docs/thermo/characterization/fluid_characterization_combining.md](thermo/characterization/fluid_characterization_combining.md) | Fluid combining methods |
| Char Mathematics | [docs/pvtsimulation/fluid_characterization_mathematics.md](pvtsimulation/fluid_characterization_mathematics.md) | Characterization mathematics with lumping equations |

### Chapter 9: Physical Properties
| Document | Path | Description |
|----------|------|-------------|
| Properties Overview | [docs/thermo/physical_properties.md](thermo/physical_properties.md) | Physical property calculations |
| Physical Props Module | [docs/physical_properties/README.md](physical_properties/README.md) | Physical properties module |
| Viscosity Models | [docs/wiki/viscosity_models.md](wiki/viscosity_models.md) | Viscosity calculation models |
| Viscosity Detailed | [docs/physical_properties/viscosity_models.md](physical_properties/viscosity_models.md) | Detailed viscosity models |
| Density Models | [docs/physical_properties/density_models.md](physical_properties/density_models.md) | Density calculation models |
| Thermal Conductivity | [docs/physical_properties/thermal_conductivity_models.md](physical_properties/thermal_conductivity_models.md) | Thermal conductivity models |
| Diffusivity | [docs/physical_properties/diffusivity_models.md](physical_properties/diffusivity_models.md) | Diffusivity models |
| Interfacial Props | [docs/physical_properties/interfacial_properties.md](physical_properties/interfacial_properties.md) | Interfacial tension, etc. |
| Scale Potential | [docs/physical_properties/scale_potential.md](physical_properties/scale_potential.md) | Scale potential calculations |
| Steam Tables | [docs/wiki/steam_tables_if97.md](wiki/steam_tables_if97.md) | IF97 steam table implementation |
| Thermodynamic Workflows | [docs/thermo/thermodynamic_workflows.md](thermo/thermodynamic_workflows.md) | Common thermodynamic workflows |
| Interaction Tables | [docs/thermo/inter_table_guide.md](thermo/inter_table_guide.md) | Binary interaction parameters |

### Chapter 10: Hydrates & Flow Assurance
| Document | Path | Description |
|----------|------|-------------|
| Hydrate Models | [docs/thermo/hydrate_models.md](thermo/hydrate_models.md) | Hydrate equilibrium models |
| Hydrate Flash | [docs/thermodynamicoperations/hydrate_flash_operations.md](thermodynamicoperations/hydrate_flash_operations.md) | Hydrate flash operations |
| Wax Characterization | [docs/thermo/characterization/wax_characterization.md](thermo/characterization/wax_characterization.md) | Wax modeling, WAT calculation, flow assurance |
| Asphaltene Characterization | [docs/thermo/characterization/asphaltene_characterization.md](thermo/characterization/asphaltene_characterization.md) | SARA analysis, CII, CPA parameters |

---

## Part III: Process Simulation

### Chapter 11: Process Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Process Overview | [docs/process/README.md](process/README.md) | Process simulation module |
| Process Guide | [docs/wiki/process_simulation.md](wiki/process_simulation.md) | Process simulation guide |
| Advanced Process | [docs/wiki/advanced_process_simulation.md](wiki/advanced_process_simulation.md) | Advanced techniques |
| Logical Operations | [docs/wiki/logical_unit_operations.md](wiki/logical_unit_operations.md) | Logical unit operations |
| Process Design | [docs/process/process_design_guide.md](process/process_design_guide.md) | Process design guide |
| Design Framework | [docs/process/DESIGN_FRAMEWORK.md](process/DESIGN_FRAMEWORK.md) | Automated design & optimization framework |
| Optimization Roadmap | [docs/process/OPTIMIZATION_IMPROVEMENT_PROPOSAL.md](process/OPTIMIZATION_IMPROVEMENT_PROPOSAL.md) | Optimization implementation status |

### Chapter 12: Process Systems & Models
| Document | Path | Description |
|----------|------|-------------|
| ProcessModel Overview | [docs/process/processmodel/README.md](process/processmodel/README.md) | Process system management |
| ProcessSystem | [docs/process/processmodel/process_system.md](process/processmodel/process_system.md) | ProcessSystem class |
| ProcessModel | [docs/process/processmodel/process_model.md](process/processmodel/process_model.md) | Multi-process models |
| ProcessModule | [docs/process/processmodel/process_module.md](process/processmodel/process_module.md) | Modular process units |
| Graph Simulation | [docs/process/processmodel/graph_simulation.md](process/processmodel/graph_simulation.md) | Graph-based simulation |
| Diagram Export | [docs/process/processmodel/diagram_export.md](process/processmodel/diagram_export.md) | PFD diagram export |
| DEXPI Architecture | [docs/process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md](process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md) | DEXPI integration |

### Chapter 13: Streams & Mixers
| Document | Path | Description |
|----------|------|-------------|
| Streams | [docs/process/equipment/streams.md](process/equipment/streams.md) | Stream models |
| Mixers/Splitters | [docs/process/equipment/mixers_splitters.md](process/equipment/mixers_splitters.md) | Mixer and splitter models |
| Equipment Overview | [docs/process/equipment/README.md](process/equipment/README.md) | Equipment module overview |

### Chapter 14: Separation Equipment
| Document | Path | Description |
|----------|------|-------------|
| Separators | [docs/process/equipment/separators.md](process/equipment/separators.md) | Two/three-phase separators, scrubbers, design parameters, performance constraints (K-value, droplet cut size, retention time), Equinor TR3500 & API 12J compliance |
| Distillation | [docs/process/equipment/distillation.md](process/equipment/distillation.md) | Distillation columns |
| Distillation Wiki | [docs/wiki/distillation_column.md](wiki/distillation_column.md) | Distillation column details |
| Absorbers | [docs/process/equipment/absorbers.md](process/equipment/absorbers.md) | Absorption equipment |
| Membrane | [docs/wiki/membrane_separation.md](wiki/membrane_separation.md) | Membrane separation |
| Membrane Equipment | [docs/process/equipment/membranes.md](process/equipment/membranes.md) | Membrane equipment |
| Filters | [docs/process/equipment/filters.md](process/equipment/filters.md) | Filter equipment |
| Water Treatment | [docs/process/equipment/water_treatment.md](process/equipment/water_treatment.md) | Hydrocyclones, produced water treatment trains, OIW limits |

### Chapter 15: Rotating Equipment
| Document | Path | Description |
|----------|------|-------------|
| Compressors | [docs/process/equipment/compressors.md](process/equipment/compressors.md) | Compressor models, drivers, speed-dependent power |
| Compressor Curves | [docs/process/equipment/compressor_curves.md](process/equipment/compressor_curves.md) | Compressor performance curves |
| Compressor Design | [docs/process/CompressorMechanicalDesign.md](process/CompressorMechanicalDesign.md) | Compressor mechanical design |
| Pumps | [docs/process/equipment/pumps.md](process/equipment/pumps.md) | Pump models |
| Pump Guide | [docs/wiki/pump_usage_guide.md](wiki/pump_usage_guide.md) | Pump usage guide |
| Pump Theory | [docs/wiki/pump_theory_and_implementation.md](wiki/pump_theory_and_implementation.md) | Pump theory |
| Expanders | [docs/process/equipment/expanders.md](process/equipment/expanders.md) | Expander models |
| Turboexpander | [docs/simulation/turboexpander_compressor_model.md](simulation/turboexpander_compressor_model.md) | Turboexpander model |
| Ejectors | [docs/process/equipment/ejectors.md](process/equipment/ejectors.md) | Ejector systems |

### Chapter 16: Heat Transfer Equipment
| Document | Path | Description |
|----------|------|-------------|
| Heat Exchangers | [docs/process/equipment/heat_exchangers.md](process/equipment/heat_exchangers.md) | Heat exchanger models |
| Air Cooler | [docs/wiki/air_cooler.md](wiki/air_cooler.md) | Air cooler models |
| Water Cooler | [docs/wiki/water_cooler.md](wiki/water_cooler.md) | Water cooler models |
| Steam Heater | [docs/wiki/steam_heater.md](wiki/steam_heater.md) | Steam heater models |
| Mechanical Design | [docs/wiki/heat_exchanger_mechanical_design.md](wiki/heat_exchanger_mechanical_design.md) | HX mechanical design |

### Chapter 17: Valves & Flow Control
| Document | Path | Description |
|----------|------|-------------|
| Valves | [docs/process/equipment/valves.md](process/equipment/valves.md) | Valve models |
| Valve Design | [docs/process/ValveMechanicalDesign.md](process/ValveMechanicalDesign.md) | Valve mechanical design |
| **Multiphase Choke Flow** | [docs/process/MultiphaseChokeFlow.md](process/MultiphaseChokeFlow.md) | **Sachdeva, Gilbert two-phase choke models** |
| Flow Meters | [docs/wiki/flow_meter_models.md](wiki/flow_meter_models.md) | Flow metering |
| Venturi | [docs/wiki/venturi_calculation.md](wiki/venturi_calculation.md) | Venturi calculations |
| Tanks | [docs/process/equipment/tanks.md](process/equipment/tanks.md) | Tank models |
| **Vessel Depressurization** | [docs/process/equipment/vessel_depressurization.md](process/equipment/vessel_depressurization.md) | **Blowdown simulation, fire scenarios, transient analysis** |
| **Measurement Devices** | [docs/process/equipment/measurement_devices.md](process/equipment/measurement_devices.md) | **CO2 emissions, FIV analysis, NMVOC, dew points, safety detectors** |

### Chapter 18: Special Equipment
| Document | Path | Description |
|----------|------|-------------|
| Reactors | [docs/process/equipment/reactors.md](process/equipment/reactors.md) | Reactor models |
| Gibbs Reactor | [docs/wiki/gibbs_reactor.md](wiki/gibbs_reactor.md) | Gibbs reactor |
| Electrolyzers | [docs/process/equipment/electrolyzers.md](process/equipment/electrolyzers.md) | Electrolyzer systems |
| CO2 Electrolyzer | [docs/pvtsimulation/CO2ElectrolyzerExample.md](pvtsimulation/CO2ElectrolyzerExample.md) | CO2 electrolyzer example |
| Flares | [docs/process/equipment/flares.md](process/equipment/flares.md) | Flare systems |
| Adsorbers | [docs/process/equipment/adsorbers.md](process/equipment/adsorbers.md) | Gas adsorption equipment |
| Power Generation | [docs/process/equipment/power_generation.md](process/equipment/power_generation.md) | Gas turbines, fuel cells, renewables |
| Diff. Pressure | [docs/process/equipment/differential_pressure.md](process/equipment/differential_pressure.md) | Orifice plates, flow measurement |
| Manifolds | [docs/process/equipment/manifolds.md](process/equipment/manifolds.md) | Multi-stream routing |
| Battery Storage | [docs/wiki/battery_storage.md](wiki/battery_storage.md) | Battery storage |
| Solar Panel | [docs/wiki/solar_panel.md](wiki/solar_panel.md) | Solar panel models |

### Chapter 19: Wells, Pipelines & Subsea
| Document | Path | Description |
|----------|------|-------------|
| Wells | [docs/process/equipment/wells.md](process/equipment/wells.md) | Well equipment |
| Well Simulation | [docs/simulation/well_simulation_guide.md](simulation/well_simulation_guide.md) | Well simulation guide |
| Well & Choke | [docs/simulation/well_and_choke_simulation.md](simulation/well_and_choke_simulation.md) | Choke valve simulation |
| Well Allocation | [docs/process/equipment/well_allocation.md](process/equipment/well_allocation.md) | Production allocation for commingled wells, VFM, well test methods |
| Pipelines | [docs/process/equipment/pipelines.md](process/equipment/pipelines.md) | Pipeline and riser models |
| **TwoFluidPipe Model** | [docs/process/TWOFLUIDPIPE_MODEL.md](process/TWOFLUIDPIPE_MODEL.md) | **Two-fluid multiphase flow model** |
| **TwoFluidPipe OLGA Comparison** | [docs/wiki/two_fluid_model_olga_comparison.md](wiki/two_fluid_model_olga_comparison.md) | **Mathematical equations, slug flow physics, Lagrangian tracking** |
| **TwoFluidPipe Tutorial (Jupyter)** | [docs/examples/TwoFluidPipe_Tutorial.ipynb](examples/TwoFluidPipe_Tutorial.ipynb) | **Interactive notebook: multiphase flow, slug tracking, terrain effects, heat transfer** |
| **Risers** | [docs/process/equipment/pipelines.md#risers](process/equipment/pipelines.md#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Beggs & Brill | [docs/process/PipeBeggsAndBrills.md](process/PipeBeggsAndBrills.md) | Beggs & Brill correlation |
| Networks | [docs/process/equipment/networks.md](process/equipment/networks.md) | Pipeline network modeling |
| **Looped Network Solver** | [docs/process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md](process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md) | **Hardy Cross looped network solver for ring mains and parallel pipelines** |
| **Looped Network Tutorial** | [docs/examples/LoopedPipelineNetworkExample.ipynb](examples/LoopedPipelineNetworkExample.ipynb) | **Interactive notebook: ring mains, offshore rings, loop detection, Hardy Cross** |
| Reservoirs | [docs/process/equipment/reservoirs.md](process/equipment/reservoirs.md) | Reservoir modeling |
| Subsea Systems | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems.md) | Subsea wells and flowlines |
| Subsea Equipment | [docs/process/equipment/subsea_equipment.md](process/equipment/subsea_equipment.md) | SubseaWell, SimpleFlowLine, flow assurance |
| **SURF Subsea Equipment** | [docs/process/SURF_SUBSEA_EQUIPMENT.md](process/SURF_SUBSEA_EQUIPMENT.md) | **Comprehensive SURF equipment: PLET, PLEM, manifolds, trees, jumpers, umbilicals, flexible pipes, boosters with mechanical design and cost estimation** |

### Chapter 20: Utility Equipment
| Document | Path | Description |
|----------|------|-------------|
| Utility Overview | [docs/process/equipment/util/README.md](process/equipment/util/README.md) | Utility equipment |
| Adjusters | [docs/process/equipment/util/adjusters.md](process/equipment/util/adjusters.md) | Adjuster units |
| Recycles | [docs/process/equipment/util/recycles.md](process/equipment/util/recycles.md) | Recycle units |
| Calculators | [docs/process/equipment/util/calculators.md](process/equipment/util/calculators.md) | Calculator units |
| **Stream Fitters** | [docs/process/equipment/util/stream_fitters.md](process/equipment/util/stream_fitters.md) | **GORfitter, MPFMfitter: GOR/GVF adjustment, MPFM reference fluids** |

### Chapter 21: Process Control
| Document | Path | Description |
|----------|------|-------------|
| Controllers | [docs/process/controllers.md](process/controllers.md) | Process controllers |
| Process Control | [docs/wiki/process_control.md](wiki/process_control.md) | Control systems |
| Dynamic Simulation Guide | [docs/simulation/dynamic_simulation_guide.md](simulation/dynamic_simulation_guide.md) | Comprehensive dynamic/transient simulation guide |
| Transient Simulation | [docs/wiki/process_transient_simulation_guide.md](wiki/process_transient_simulation_guide.md) | Transient simulation patterns |

### Chapter 22: Optimization and Constraints

> **January 2026 Update:** ProductionOptimizer now includes configuration validation, stagnation detection, warm start, bounded LRU cache, and infeasibility diagnostics. See updated tutorials for details.

| Document | Path | Description |
|----------|------|-------------|
| **Optimization & Constraints Guide** | [docs/process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md](process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md) | **COMPREHENSIVE: Complete guide to optimization algorithms, constraint types, bottleneck analysis, and practical examples** |
| **Optimization Overview** | [docs/process/optimization/OPTIMIZATION_OVERVIEW.md](process/optimization/OPTIMIZATION_OVERVIEW.md) | **START HERE: Introduction to process optimization, when to use ProcessOptimizationEngine vs ProductionOptimizer** |
| **ProductionOptimizer Tutorial** | [docs/examples/ProductionOptimizer_Tutorial.md](examples/ProductionOptimizer_Tutorial.md) | **Interactive Jupyter notebook with complete ProductionOptimizer guide: algorithms, single/multi-variable, Pareto, constraints** |
| **Python Optimization Tutorial** | [docs/examples/NeqSim_Python_Optimization.md](examples/NeqSim_Python_Optimization.md) | **Using SciPy/Python optimizers with NeqSim process simulations: constraints, Pareto, global optimization** |
| **Capacity Constraint Framework** | [docs/process/CAPACITY_CONSTRAINT_FRAMEWORK.md](process/CAPACITY_CONSTRAINT_FRAMEWORK.md) | **Framework for equipment capacity limits, bottleneck detection, utilization tracking, and AIV/FIV vibration analysis** |
| **Optimizer Plugin Architecture** | [docs/process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE.md](process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE.md) | **Equipment capacity strategies, throughput optimization, gradient descent, sensitivity analysis, shadow prices, and Eclipse VFP export** |
| **External Optimizer Integration** | [docs/integration/EXTERNAL_OPTIMIZER_INTEGRATION.md](integration/EXTERNAL_OPTIMIZER_INTEGRATION.md) | **ProcessSimulationEvaluator for Python/SciPy/NLopt/Pyomo integration with gradient estimation** |
| **Production Optimization Guide** | [docs/examples/PRODUCTION_OPTIMIZATION_GUIDE.md](examples/PRODUCTION_OPTIMIZATION_GUIDE.md) | **Complete guide to production optimization with Java and Python examples** |
| **Pressure Boundary Optimization** | [docs/process/pressure_boundary_optimization.md](process/pressure_boundary_optimization.md) | **Calculate flow rates for pressure boundaries, generate Eclipse VFP lift curves, optimize compressor power** |
| **Flow Rate Optimization** | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization.md) | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation** |

### Chapter 23: Mechanical Design
| Document | Path | Description |
|----------|------|-------------|
| Mechanical Design | [docs/process/mechanical_design.md](process/mechanical_design.md) | Mechanical design overview, process design parameters, validation, and JSON export |
| **Equipment Design Parameters** | [docs/process/EQUIPMENT_DESIGN_PARAMETERS.md](process/EQUIPMENT_DESIGN_PARAMETERS.md) | **Comprehensive guide to autoSize vs MechanicalDesign, manual sizing, validation methods, and capacity constraints** |
| **Process Design Parameters** | [docs/process/mechanical_design.md#process-design-parameters](process/mechanical_design.md#process-design-parameters) | **Industry-standard process design parameters for separators, compressors, pumps, heat exchangers** |
| **Design Validation** | [docs/process/mechanical_design.md#design-validation](process/mechanical_design.md#design-validation) | **Validation methods per API-610, API-617, TEMA, API-12J standards** |
| **Mechanical Design Report** | [docs/process/mechanical_design.md#comprehensive-mechanical-design-report-json](process/mechanical_design.md#comprehensive-mechanical-design-report-json) | **Combined JSON output for all mechanical design data (equipment + piping)** |
| Design Standards | [docs/process/mechanical_design_standards.md](process/mechanical_design_standards.md) | Design standards |
| Design Database | [docs/process/mechanical_design_database.md](process/mechanical_design_database.md) | Design database |
| **Pipeline Mechanical Design** | [docs/process/pipeline_mechanical_design.md](process/pipeline_mechanical_design.md) | **Comprehensive pipeline mechanical design with wall thickness, stress analysis, cost estimation** |
| **Topside Piping Design** | [docs/process/topside_piping_design.md](process/topside_piping_design.md) | **Topside piping design with velocity, support spacing, vibration (AIV/FIV), stress analysis per ASME B31.3** |
| **Manifold Mechanical Design** | [docs/process/equipment/manifold_design.md](process/equipment/manifold_design.md) | **Manifold design for topside, onshore, and subsea with velocity limits, reinforcement, support per ASME B31.3 and DNV-ST-F101** |
| **Riser Mechanical Design** | [docs/process/riser_mechanical_design.md](process/riser_mechanical_design.md) | **Riser design with catenary mechanics, VIV, fatigue per DNV-OS-F201** |
| **Pipeline Design Math** | [docs/process/pipeline_mechanical_design_math.md](process/pipeline_mechanical_design_math.md) | **Mathematical methods and formulas for pipeline design** |
| **Subsea SURF Mechanical Design** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#mechanical-design](process/SURF_SUBSEA_EQUIPMENT.md#mechanical-design) | **Mechanical design for PLET, PLEM, trees, manifolds, jumpers, umbilicals, flexible pipes, boosters per DNV, API, ISO, NORSOK** |
| TORG Integration | [docs/process/torg_integration.md](process/torg_integration.md) | TORG integration |
| Field Development | [docs/process/field_development_orchestration.md](process/field_development_orchestration.md) | Field development orchestration |

### Chapter 23b: Cost Estimation
| Document | Path | Description |
|----------|------|-------------|
| **Cost Estimation Framework** | [docs/process/COST_ESTIMATION_FRAMEWORK.md](process/COST_ESTIMATION_FRAMEWORK.md) | **Comprehensive capital and operating cost estimation framework** |
| **Cost Estimation API** | [docs/process/COST_ESTIMATION_API_REFERENCE.md](process/COST_ESTIMATION_API_REFERENCE.md) | **Detailed API reference for all cost estimation classes** |
| **Subsea SURF Cost Estimation** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#cost-estimation](process/SURF_SUBSEA_EQUIPMENT.md#cost-estimation) | **Cost estimation for all SURF equipment with regional factors, labor rates, vessel costs, BOM generation** |
| Equipment Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#equipment-cost-estimation](process/COST_ESTIMATION_FRAMEWORK.md#equipment-cost-estimation) | Equipment-specific cost correlations |
| Tank Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#tank-cost](process/COST_ESTIMATION_FRAMEWORK.md#tank-cost) | Storage tank cost estimation (API 650/620) |
| Expander Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#expander-cost](process/COST_ESTIMATION_FRAMEWORK.md#expander-cost) | Turboexpander cost estimation |
| Ejector Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#ejector-cost](process/COST_ESTIMATION_FRAMEWORK.md#ejector-cost) | Ejector and vacuum system costs |
| Absorber Costs | [docs/process/COST_ESTIMATION_FRAMEWORK.md#absorber-cost](process/COST_ESTIMATION_FRAMEWORK.md#absorber-cost) | Absorption tower cost estimation |
| Currency & Location | [docs/process/COST_ESTIMATION_FRAMEWORK.md#currency-and-location-support](process/COST_ESTIMATION_FRAMEWORK.md#currency-and-location-support) | Multi-currency and location factors |
| OPEX Estimation | [docs/process/COST_ESTIMATION_FRAMEWORK.md#operating-cost-opex-estimation](process/COST_ESTIMATION_FRAMEWORK.md#operating-cost-opex-estimation) | Operating cost calculation |
| Financial Metrics | [docs/process/COST_ESTIMATION_FRAMEWORK.md#financial-metrics](process/COST_ESTIMATION_FRAMEWORK.md#financial-metrics) | Payback, ROI, NPV calculations |

### Chapter 24: Serialization & Persistence
| Document | Path | Description |
|----------|------|-------------|
| Process Serialization | [docs/simulation/process_serialization.md](simulation/process_serialization.md) | Save/load process models |
| Process Model Lifecycle | [docs/process/lifecycle/process_model_lifecycle.md](process/lifecycle/process_model_lifecycle.md) | ProcessModelState, versioning, checkpointing, digital twin lifecycle |

---

## Part IV: Pipeline & Multiphase Flow

### Chapter 24: Pipeline Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Fluid Mechanics Overview | [docs/fluidmechanics/README.md](fluidmechanics/README.md) | Fluid mechanics module |
| Pipeline Index | [docs/wiki/pipeline_index.md](wiki/pipeline_index.md) | Pipeline documentation index |
| **Pipeline Simulation** | [docs/process/equipment/pipeline_simulation.md](process/equipment/pipeline_simulation.md) | **Comprehensive pipeline simulation guide with PipeLineInterface, all pipe types, flow regimes, heat transfer** |
| Flow Equations | [docs/wiki/pipeline_flow_equations.md](wiki/pipeline_flow_equations.md) | Pipeline flow equations |
| Single Phase Flow | [docs/fluidmechanics/single_phase_pipe_flow.md](fluidmechanics/single_phase_pipe_flow.md) | Single phase pipe flow |
| **Flow Pattern Detection** | [docs/fluidmechanics/flow_pattern_detection.md](fluidmechanics/flow_pattern_detection.md) | **Taitel-Dukler, Baker, Barnea models, FlowPatternDetector API** |

### Chapter 25: Pressure Drop Calculations
| Document | Path | Description |
|----------|------|-------------|
| Pressure Drop | [docs/wiki/pipeline_pressure_drop.md](wiki/pipeline_pressure_drop.md) | Pressure drop calculation |
| Beggs & Brill | [docs/wiki/beggs_and_brill_correlation.md](wiki/beggs_and_brill_correlation.md) | Beggs & Brill correlation |
| Friction Factors | [docs/wiki/friction_factor_models.md](wiki/friction_factor_models.md) | Friction factor models |

### Chapter 26: Heat Transfer in Pipelines
| Document | Path | Description |
|----------|------|-------------|
| Heat Transfer | [docs/wiki/pipeline_heat_transfer.md](wiki/pipeline_heat_transfer.md) | Pipeline heat transfer |
| Heat Transfer Module | [docs/fluidmechanics/heat_transfer.md](fluidmechanics/heat_transfer.md) | Heat transfer module |
| Pipe Wall | [docs/wiki/pipe_wall_heat_transfer.md](wiki/pipe_wall_heat_transfer.md) | Pipe wall heat transfer |
| Interphase | [docs/fluidmechanics/InterphaseHeatMassTransfer.md](fluidmechanics/InterphaseHeatMassTransfer.md) | Interphase heat/mass transfer |
| Mass Transfer | [docs/fluidmechanics/mass_transfer.md](fluidmechanics/mass_transfer.md) | Mass transfer models |

### Chapter 27: Two-Phase & Multiphase Flow
| Document | Path | Description |
|----------|------|-------------|
| Two-Phase Model | [docs/fluidmechanics/TwoPhasePipeFlowModel.md](fluidmechanics/TwoPhasePipeFlowModel.md) | Two-phase pipe flow |
| Two-Fluid Model | [docs/wiki/two_fluid_model.md](wiki/two_fluid_model.md) | Two-fluid model |
| Multiphase Transient | [docs/wiki/multiphase_transient_model.md](wiki/multiphase_transient_model.md) | Multiphase transient |
| Transient Pipe Wiki | [docs/wiki/transient_multiphase_pipe.md](wiki/transient_multiphase_pipe.md) | Transient multiphase pipe |
| Development Plan | [docs/fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md](fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md) | Development plan |

### Chapter 28: Transient Pipeline Simulation
| Document | Path | Description |
|----------|------|-------------|
| Transient Simulation | [docs/wiki/pipeline_transient_simulation.md](wiki/pipeline_transient_simulation.md) | Transient pipeline |
| Model Recommendations | [docs/wiki/pipeline_model_recommendations.md](wiki/pipeline_model_recommendations.md) | Model recommendations |
| Water Hammer | [docs/wiki/water_hammer_implementation.md](wiki/water_hammer_implementation.md) | Water hammer |

---

## Part V: Safety & Reliability

### Chapter 29: Safety Overview
| Document | Path | Description |
|----------|------|-------------|
| Safety Overview | [docs/safety/README.md](safety/README.md) | Safety systems module |
| Safety Roadmap | [docs/safety/SAFETY_SIMULATION_ROADMAP.md](safety/SAFETY_SIMULATION_ROADMAP.md) | Safety simulation roadmap |
| Layered Architecture | [docs/safety/layered_safety_architecture.md](safety/layered_safety_architecture.md) | Layered safety architecture |
| Process Safety | [docs/process/safety/README.md](process/safety/README.md) | Process safety module |

### Chapter 30: Alarm Systems
| Document | Path | Description |
|----------|------|-------------|
| Alarm System Guide | [docs/safety/alarm_system_guide.md](safety/alarm_system_guide.md) | Alarm system configuration |
| Alarm Logic Example | [docs/safety/alarm_triggered_logic_example.md](safety/alarm_triggered_logic_example.md) | Alarm-triggered logic |
| ESD Fire Alarm | [docs/wiki/esd_fire_alarm_system.md](wiki/esd_fire_alarm_system.md) | ESD/Fire alarm systems |

### Chapter 31: Pressure Relief Systems
| Document | Path | Description |
|----------|------|-------------|
| PSV Dynamic Sizing Wiki | [docs/wiki/psv_dynamic_sizing_example.md](wiki/psv_dynamic_sizing_example.md) | PSV dynamic sizing |
| PSV Dynamic Sizing | [docs/safety/psv_dynamic_sizing_example.md](safety/psv_dynamic_sizing_example.md) | PSV sizing example |
| PSD Valve Trip | [docs/wiki/psd_valve_hihi_trip.md](wiki/psd_valve_hihi_trip.md) | PSD valve HIHI trip |
| Rupture Disks | [docs/safety/rupture_disk_dynamic_behavior.md](safety/rupture_disk_dynamic_behavior.md) | Rupture disk behavior |

### Chapter 32: HIPPS Systems
| Document | Path | Description |
|----------|------|-------------|
| HIPPS Summary | [docs/safety/HIPPS_SUMMARY.md](safety/HIPPS_SUMMARY.md) | HIPPS summary |
| HIPPS Implementation | [docs/safety/hipps_implementation.md](safety/hipps_implementation.md) | HIPPS implementation |
| HIPPS Safety Logic | [docs/safety/hipps_safety_logic.md](safety/hipps_safety_logic.md) | HIPPS safety logic |

### Chapter 33: ESD & Fire Systems
| Document | Path | Description |
|----------|------|-------------|
| ESD Blowdown | [docs/safety/ESD_BLOWDOWN_SYSTEM.md](safety/ESD_BLOWDOWN_SYSTEM.md) | ESD blowdown system |
| Pressure Monitoring | [docs/safety/PRESSURE_MONITORING_ESD.md](safety/PRESSURE_MONITORING_ESD.md) | Pressure monitoring ESD |
| Fire Heat Transfer | [docs/safety/fire_heat_transfer_enhancements.md](safety/fire_heat_transfer_enhancements.md) | Fire heat transfer |
| Fire Blowdown | [docs/safety/fire_blowdown_capabilities.md](safety/fire_blowdown_capabilities.md) | Fire blowdown capabilities |

### Chapter 34: Integrated Safety Systems
| Document | Path | Description |
|----------|------|-------------|
| Integrated Safety | [docs/safety/INTEGRATED_SAFETY_SYSTEMS.md](safety/INTEGRATED_SAFETY_SYSTEMS.md) | Integrated safety systems |
| SIS Logic | [docs/safety/sis_logic_implementation.md](safety/sis_logic_implementation.md) | SIS logic implementation |
| Choke Protection | [docs/wiki/choke_collapse_psd_protection.md](wiki/choke_collapse_psd_protection.md) | Choke collapse protection |
| Safety Chain Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests.md) | Safety chain tests |
| Scenario Generation | [docs/process/safety/scenario-generation.md](process/safety/scenario-generation.md) | Automatic scenario generation |

### Chapter 35: Risk Simulation Framework

Comprehensive operational risk simulation framework for equipment failure analysis, production impact assessment, and degraded operation optimization. Includes Monte Carlo simulation, 5×5 risk matrix, process topology analysis, STID tagging per ISO 14224/NORSOK, and dependency analysis with cross-installation support.

| Document | Path | Description |
|----------|------|-------------|
| **Risk Framework Index** | [docs/risk/index.md](risk/index.md) | **START HERE**: Quick start guide, architecture overview, package structure |
| **Framework Overview** | [docs/risk/overview.md](risk/overview.md) | Core concepts, capabilities, industry standards compliance (ISO 14224, OREDA, NORSOK) |
| **Equipment Failure Modeling** | [docs/risk/equipment-failure.md](risk/equipment-failure.md) | FailureType enum, capacity factors, OREDA reliability data, λ/R(t)/MTTF formulas |
| **Risk Matrix** | [docs/risk/risk-matrix.md](risk/risk-matrix.md) | 5×5 probability/consequence matrix, risk scoring, cost calculations |
| **Monte Carlo Simulation** | [docs/risk/monte-carlo.md](risk/monte-carlo.md) | OperationalRiskSimulator, exponential sampling, P10/P50/P90 statistics, convergence |
| **Production Impact Analysis** | [docs/risk/production-impact.md](risk/production-impact.md) | Loss calculations, criticality index, cascade analysis, economic impact |
| **Degraded Operation** | [docs/risk/degraded-operation.md](risk/degraded-operation.md) | DegradedOperationOptimizer, recovery planning, operating modes |
| **Process Topology** | [docs/risk/topology.md](risk/topology.md) | ProcessTopologyAnalyzer, graph extraction, topological ordering, DOT/JSON export |
| **STID Tagging** | [docs/risk/stid-tagging.md](risk/stid-tagging.md) | FunctionalLocation class, PPPP-TT-NNNNN[S] format, installation/equipment codes |
| **Dependency Analysis** | [docs/risk/dependency-analysis.md](risk/dependency-analysis.md) | DependencyAnalyzer, cascade failure trees, cross-installation effects |
| **Mathematical Reference** | [docs/risk/mathematical-reference.md](risk/mathematical-reference.md) | Complete formulas: reliability, system availability, Monte Carlo, risk calculations |
| **API Reference** | [docs/risk/api-reference.md](risk/api-reference.md) | Full API documentation for all risk simulation classes |
| **Reliability Data Guide** | [docs/risk/RELIABILITY_DATA_GUIDE.md](risk/RELIABILITY_DATA_GUIDE.md) | OREDA-based reliability data, failure rate sources, equipment categories |

### Chapter 35a: Advanced Risk Framework (**NEW**)

Extended risk analysis capabilities implementing P1-P7 priority improvements for oil & gas industry applications.

| Document | Path | Description |
|----------|------|-------------|
| **Advanced Framework Overview** | [docs/risk/README.md](risk/README.md) | **START HERE**: Overview of all 7 priority packages |
| **P1: Dynamic Simulation** | [docs/risk/dynamic-simulation.md](risk/dynamic-simulation.md) | Monte Carlo with transient effects, shutdown/startup modeling |
| **P2: SIS/SIF Integration** | [docs/risk/sis-integration.md](risk/sis-integration.md) | IEC 61508/61511, LOPA analysis, SIL verification |
| **P4: Bow-Tie Analysis** | [docs/risk/bowtie-analysis.md](risk/bowtie-analysis.md) | Barrier analysis, threat/consequence visualization |
| **P6: Condition-Based Reliability** | [docs/risk/condition-based.md](risk/condition-based.md) | Health monitoring, RUL estimation, predictive maintenance |
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
| PVT Overview | [docs/pvtsimulation/README.md](pvtsimulation/README.md) | PVT simulation module |
| PVT Workflows | [docs/wiki/pvt_simulation_workflows.md](wiki/pvt_simulation_workflows.md) | PVT simulation workflows |
| PVT Workflow Module | [docs/pvtsimulation/pvt_workflow.md](pvtsimulation/pvt_workflow.md) | PVT workflow module |
| Property Flash | [docs/wiki/property_flash_workflows.md](wiki/property_flash_workflows.md) | Property flash workflows |
| Whitson Reader | [docs/pvtsimulation/whitson_pvt_reader.md](pvtsimulation/whitson_pvt_reader.md) | Whitson PVT reader |
| Solution Gas-Water Ratio | [docs/pvtsimulation/SolutionGasWaterRatio.md](pvtsimulation/SolutionGasWaterRatio.md) | Rsw calculation methods (McCain, Søreide-Whitson, Electrolyte CPA) |

### Chapter 36: Black Oil Models
| Document | Path | Description |
|----------|------|-------------|
| Black Oil Overview | [docs/blackoil/README.md](blackoil/README.md) | Black oil module |
| Flash Playbook | [docs/wiki/black_oil_flash_playbook.md](wiki/black_oil_flash_playbook.md) | Black oil flash playbook |
| Black Oil Export | [docs/pvtsimulation/blackoil_pvt_export.md](pvtsimulation/blackoil_pvt_export.md) | Black oil PVT export and E300 compositional export |

### Chapter 37: Flow Assurance
| Document | Path | Description |
|----------|------|-------------|
| Flow Assurance | [docs/pvtsimulation/flowassurance/README.md](pvtsimulation/flowassurance/README.md) | Flow assurance module |
| Asphaltene Modeling | [docs/pvtsimulation/flowassurance/asphaltene_modeling.md](pvtsimulation/flowassurance/asphaltene_modeling.md) | Asphaltene modeling |
| Asphaltene CPA | [docs/pvtsimulation/flowassurance/asphaltene_cpa_calculations.md](pvtsimulation/flowassurance/asphaltene_cpa_calculations.md) | CPA calculations |
| De Boer Screening | [docs/pvtsimulation/flowassurance/asphaltene_deboer_screening.md](pvtsimulation/flowassurance/asphaltene_deboer_screening.md) | De Boer screening |
| Method Comparison | [docs/pvtsimulation/flowassurance/asphaltene_method_comparison.md](pvtsimulation/flowassurance/asphaltene_method_comparison.md) | Method comparison |
| Parameter Fitting | [docs/pvtsimulation/flowassurance/asphaltene_parameter_fitting.md](pvtsimulation/flowassurance/asphaltene_parameter_fitting.md) | Parameter fitting |
| Validation | [docs/pvtsimulation/flowassurance/asphaltene_validation.md](pvtsimulation/flowassurance/asphaltene_validation.md) | Validation |

### Chapter 38: Gas Quality
| Document | Path | Description |
|----------|------|-------------|
| Gas Quality Standards | [docs/wiki/gas_quality_standards_from_tests.md](wiki/gas_quality_standards_from_tests.md) | Gas quality standards |
| Humid Air | [docs/wiki/humid_air_math.md](wiki/humid_air_math.md) | Humid air calculations |

---

## Part VII: Standards & Quality

### Chapter 39: ISO Standards
| Document | Path | Description |
|----------|------|-------------|
| Standards Overview | [docs/standards/README.md](standards/README.md) | Standards module |
| ISO 6976 | [docs/standards/iso6976_calorific_values.md](standards/iso6976_calorific_values.md) | ISO 6976 calorific values |
| ISO 6578 | [docs/standards/iso6578_lng_density.md](standards/iso6578_lng_density.md) | ISO 6578 LNG density |
| ISO 15403 | [docs/standards/iso15403_cng_quality.md](standards/iso15403_cng_quality.md) | ISO 15403 CNG quality |
| Dew Point | [docs/standards/dew_point_standards.md](standards/dew_point_standards.md) | Dew point standards |
| ASTM D6377 | [docs/standards/astm_d6377_rvp.md](standards/astm_d6377_rvp.md) | ASTM D6377 RVP |
| Sales Contracts | [docs/standards/sales_contracts.md](standards/sales_contracts.md) | Sales contracts |

---

## Part VIII: Advanced Topics

### Chapter 40: Future Infrastructure
| Document | Path | Description |
|----------|------|-------------|
| Future Infrastructure | [docs/process/future-infrastructure.md](process/future-infrastructure.md) | Future infrastructure classes |
| API Reference | [docs/process/future-api-reference.md](process/future-api-reference.md) | Future API reference |

### Chapter 41: Digital Twins
| Document | Path | Description |
|----------|------|-------------|
| Digital Twin | [docs/process/digital-twin-integration.md](process/digital-twin-integration.md) | Digital twin integration |
| Lifecycle | [docs/process/lifecycle/README.md](process/lifecycle/README.md) | Lifecycle management |

### Chapter 42: AI/ML Integration
| Document | Path | Description |
|----------|------|-------------|
| AI Platform | [docs/integration/ai_platform_integration.md](integration/ai_platform_integration.md) | AI platform integration |
| AI Validation | [docs/integration/ai_validation_framework.md](integration/ai_validation_framework.md) | AI validation framework |
| AI Validation PR | [docs/integration/PR_AI_VALIDATION_FRAMEWORK.md](integration/PR_AI_VALIDATION_FRAMEWORK.md) | AI validation PR docs |
| ML Integration | [docs/integration/ml_integration.md](integration/ml_integration.md) | ML integration guide |
| ML Surrogate | [docs/process/ml/README.md](process/ml/README.md) | ML surrogate models |
| Integration Overview | [docs/integration/README.md](integration/README.md) | Integration module |

### Chapter 43: Sustainability
| Document | Path | Description |
|----------|------|-------------|
| Emissions Tracking | [docs/process/sustainability/README.md](process/sustainability/README.md) | Emissions tracking |

### Chapter 44: Optimization
| Document | Path | Description |
|----------|------|-------------|
| Optimization Overview | [docs/process/optimization/README.md](process/optimization/README.md) | Optimization module |
| **Flow Rate Optimization** | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization.md) | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation** |
| **Multi-Objective Optimization** | [docs/process/optimization/multi-objective-optimization.md](process/optimization/multi-objective-optimization.md) | **Pareto front generation for competing objectives (throughput vs energy)** |
| Batch Studies | [docs/process/optimization/batch-studies.md](process/optimization/batch-studies.md) | Batch studies |
| Bottleneck Analysis | [docs/wiki/bottleneck_analysis.md](wiki/bottleneck_analysis.md) | Bottleneck analysis and ProductionOptimizer |
| **Multi-Variable Optimization** | [docs/wiki/bottleneck_analysis.md#multi-variable-optimization-with-manipulatedvariable](wiki/bottleneck_analysis.md#multi-variable-optimization-with-manipulatedvariable) | **ManipulatedVariable for split factors, dual feeds, pressure setpoints** |
| Calibration | [docs/process/calibration/README.md](process/calibration/README.md) | Model calibration |
| Advisory | [docs/process/advisory/README.md](process/advisory/README.md) | Advisory systems |

### Chapter 45: Real-Time Integration
| Document | Path | Description |
|----------|------|-------------|
| Real-Time Guide | [docs/integration/REAL_TIME_INTEGRATION_GUIDE.md](integration/REAL_TIME_INTEGRATION_GUIDE.md) | Real-time integration |
| MPC Integration | [docs/integration/mpc_integration.md](integration/mpc_integration.md) | MPC integration |
| Industrial MPC | [docs/integration/neqsim_industrial_mpc_integration.md](integration/neqsim_industrial_mpc_integration.md) | Industrial MPC |

### Chapter 46: External Integrations
| Document | Path | Description |
|----------|------|-------------|
| DEXPI Reader | [docs/integration/dexpi-reader.md](integration/dexpi-reader.md) | DEXPI reader |
| QRA Integration | [docs/integration/QRA_INTEGRATION_GUIDE.md](integration/QRA_INTEGRATION_GUIDE.md) | QRA integration |

### Chapter 47: Process Logic Framework
| Document | Path | Description |
|----------|------|-------------|
| Simulation Overview | [docs/simulation/README.md](simulation/README.md) | Simulation module |
| Process Logic | [docs/simulation/process_logic_framework.md](simulation/process_logic_framework.md) | Process logic framework |
| Advanced Logic | [docs/simulation/advanced_process_logic.md](simulation/advanced_process_logic.md) | Advanced process logic |
| Implementation | [docs/simulation/process_logic_implementation_summary.md](simulation/process_logic_implementation_summary.md) | Implementation summary |
| Enhancements | [docs/simulation/ProcessLogicEnhancements.md](simulation/ProcessLogicEnhancements.md) | Logic enhancements |
| Runtime Flexibility | [docs/simulation/RuntimeLogicFlexibility.md](simulation/RuntimeLogicFlexibility.md) | Runtime flexibility |
| Graph-Based | [docs/simulation/graph_based_process_simulation.md](simulation/graph_based_process_simulation.md) | Graph-based simulation |
| Parallel Simulation | [docs/simulation/parallel_process_simulation.md](simulation/parallel_process_simulation.md) | Parallel simulation |
| Recycle Acceleration | [docs/simulation/recycle_acceleration_guide.md](simulation/recycle_acceleration_guide.md) | Recycle acceleration |
| Process Calculator | [docs/simulation/process_calculator.md](simulation/process_calculator.md) | Process calculator |
| Integrated Workflow | [docs/simulation/INTEGRATED_WORKFLOW_GUIDE.md](simulation/INTEGRATED_WORKFLOW_GUIDE.md) | Integrated workflow |
| Differentiable Thermo | [docs/simulation/differentiable_thermodynamics.md](simulation/differentiable_thermodynamics.md) | Auto-differentiation |
| Derivatives | [docs/simulation/derivatives_and_gradients.md](simulation/derivatives_and_gradients.md) | Derivatives and gradients |
| Equipment Factory | [docs/simulation/equipment_factory.md](simulation/equipment_factory.md) | Equipment factory |

### Chapter 48: Field Development
| Document | Path | Description |
|----------|------|-------------|
| **Digital Field Twin** | [docs/fielddevelopment/DIGITAL_FIELD_TWIN.md](fielddevelopment/DIGITAL_FIELD_TWIN.md) | **NEW** Comprehensive architecture for lifecycle consistency |
| **Mathematical Reference** | [docs/fielddevelopment/MATHEMATICAL_REFERENCE.md](fielddevelopment/MATHEMATICAL_REFERENCE.md) | **NEW** Mathematical foundations for all calculations |
| **API Guide** | [docs/fielddevelopment/API_GUIDE.md](fielddevelopment/API_GUIDE.md) | **NEW** Detailed usage examples for all components |
| **Integrated Framework** | [docs/fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md) | PVT→Reservoir→Well→Process integration guide |
| **Strategy** | [docs/fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md](fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md) | Field development strategy and roadmap |
| Field Planning | [docs/wiki/field_development_planning.md](wiki/field_development_planning.md) | Field development planning |
| Field Engine | [docs/simulation/field_development_engine.md](simulation/field_development_engine.md) | Field development engine |
| **Economics** | [docs/process/economics/README.md](process/economics/README.md) | Economics module: NPV, IRR, tax models, decline curves |
| **Subsea Systems** | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems.md) | Subsea production systems, tieback analysis |

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
| Development Overview | [docs/development/README.md](development/README.md) | Development overview |
| Contributing Structure | [docs/development/contributing-structure.md](development/contributing-structure.md) | Contributing guidelines |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP.md) | Developer setup |

### Chapter 50: Testing
| Document | Path | Description |
|----------|------|-------------|
| Test Overview | [docs/wiki/test-overview.md](wiki/test-overview.md) | Test overview |
| Flash Tests | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests.md) | Flash equation tests |
| Safety Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests.md) | Safety chain tests |

### Chapter 51: Notebooks & Examples
| Document | Path | Description |
|----------|------|-------------|
| Colab Notebooks | [docs/wiki/java_simulation_from_colab_notebooks.md](wiki/java_simulation_from_colab_notebooks.md) | Colab notebooks |
| Transient Slug Example | [docs/examples/transient_slug_separator_control_example.md](examples/transient_slug_separator_control_example.md) | Transient slug example |
| Selective Logic | [docs/examples/selective-logic-execution.md](examples/selective-logic-execution.md) | Selective logic execution |
| Comparison Quickstart | [docs/examples/comparesimulations_quickstart.md](examples/comparesimulations_quickstart.md) | Simulation comparison |

### Chapter 52: Documentation Infrastructure
| Document | Path | Description |
|----------|------|-------------|
| **GitHub Pages Setup** | [docs/GITHUB_PAGES_SETUP.md](GITHUB_PAGES_SETUP.md) | **NEW** Enable GitHub Pages for hosted documentation |
| Reference Manual | [docs/manual/neqsim_reference_manual.html](manual/neqsim_reference_manual.html) | Interactive reference manual |
| Documentation Index | [docs/index.md](index.md) | GitHub Pages home page |

---

## Appendices

### Appendix A: Chemical Reactions
| Document | Path | Description |
|----------|------|-------------|
| Chemical Reactions | [docs/chemicalreactions/README.md](chemicalreactions/README.md) | Chemical reactions module |
| Deep Review | [docs/chemicalreactions/CHEMICAL_REACTION_DEEP_REVIEW.md](chemicalreactions/CHEMICAL_REACTION_DEEP_REVIEW.md) | Chemical reaction deep review |

### Appendix B: Statistics
| Document | Path | Description |
|----------|------|-------------|
| Statistics | [docs/statistics/README.md](statistics/README.md) | Statistics module |
| Parameter Fitting | [docs/statistics/parameter_fitting.md](statistics/parameter_fitting.md) | Parameter fitting |
| Monte Carlo | [docs/statistics/monte_carlo_simulation.md](statistics/monte_carlo_simulation.md) | Monte Carlo simulation |
| Data Analysis | [docs/statistics/data_analysis.md](statistics/data_analysis.md) | Data analysis |

### Appendix C: Mathematical Library
| Document | Path | Description |
|----------|------|-------------|
| Math Library | [docs/mathlib/README.md](mathlib/README.md) | Mathematical library |

### Appendix D: Utilities
| Document | Path | Description |
|----------|------|-------------|
| Utilities | [docs/util/README.md](util/README.md) | Utility functions |
| Unit Conversion | [docs/util/unit_conversion.md](util/unit_conversion.md) | Unit conversion guide |
| **Optimizer Guide** | [docs/util/optimizer_guide.md](util/optimizer_guide.md) | **NEW** Comprehensive optimization framework with BFGS, Pareto, sensitivity analysis |

### Appendix F: Process Design Templates
| Document | Path | Description |
|----------|------|-------------|
| **Templates Guide** | [docs/process/design/templates_guide.md](process/design/templates_guide.md) | **NEW** Pre-built process templates (compression, dehydration, CO2 capture) |

### Appendix G: Mechanical Design Standards
| Document | Path | Description |
|----------|------|-------------|
| **TEMA Standard Guide** | [docs/process/mechanical_design/tema_standard_guide.md](process/mechanical_design/tema_standard_guide.md) | **NEW** TEMA shell and tube heat exchanger design standards |

### Appendix E: Wiki Reference
| Document | Path | Description |
|----------|------|-------------|
| Wiki Overview | [docs/wiki/README.md](wiki/README.md) | Wiki documentation |

---

## Document Statistics

| Category | Count |
|----------|-------|
| Wiki/Tutorials | 60 |
| Thermodynamics | 25 |
| Process Simulation | 45 |
| Safety Systems | 18 |
| **Risk Simulation** | **12** |
| Field Development | 10 |
| Integration/AI | 12 |
| Pipeline/Flow | 15 |
| PVT/Reservoir | 15 |
| Standards | 6 |
| Development | 8 |
| Statistics | 4 |
| Examples | 3 |
| **Optimization** | **3** |
| **Templates & Design** | **2** |
| Other | 24 |
| **Total** | **262** |

---

## Navigation Tips

1. **Start Here**: If new to NeqSim, begin with [Getting Started](wiki/getting_started.md)
2. **By Task**: Use the chapter structure above to find relevant sections
3. **By Equipment**: See Part III, Chapters 13-20 for equipment-specific docs
4. **For Developers**: Jump to Part IX for contribution guidelines
5. **Search**: Use MkDocs search (if enabled) for keyword searches
6. **Process Serialization**: See [Chapter 23](simulation/process_serialization.md) for save/load

---

*This index was updated after comprehensive documentation review. Last updated: January 2026*
