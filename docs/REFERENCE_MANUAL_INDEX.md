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
| Document | Path | Description |
|----------|------|-------------|
| Characterization | [docs/wiki/fluid_characterization.md](wiki/fluid_characterization.md) | Fluid characterization methods |
| TBP Fractions | [docs/wiki/tbp_fraction_models.md](wiki/tbp_fraction_models.md) | TBP fraction modeling |
| PVT Characterization | [docs/thermo/pvt_fluid_characterization.md](thermo/pvt_fluid_characterization.md) | PVT fluid characterization |
| Characterization Package | [docs/thermo/characterization/README.md](thermo/characterization/README.md) | Characterization module |
| Combining Methods | [docs/thermo/characterization/fluid_characterization_combining.md](thermo/characterization/fluid_characterization_combining.md) | Fluid combining methods |
| Char Mathematics | [docs/pvtsimulation/fluid_characterization_mathematics.md](pvtsimulation/fluid_characterization_mathematics.md) | Characterization mathematics |

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
| Separators | [docs/process/equipment/separators.md](process/equipment/separators.md) | Separator models |
| Distillation | [docs/process/equipment/distillation.md](process/equipment/distillation.md) | Distillation columns |
| Distillation Wiki | [docs/wiki/distillation_column.md](wiki/distillation_column.md) | Distillation column details |
| Absorbers | [docs/process/equipment/absorbers.md](process/equipment/absorbers.md) | Absorption equipment |
| Membrane | [docs/wiki/membrane_separation.md](wiki/membrane_separation.md) | Membrane separation |
| Membrane Equipment | [docs/process/equipment/membranes.md](process/equipment/membranes.md) | Membrane equipment |
| Filters | [docs/process/equipment/filters.md](process/equipment/filters.md) | Filter equipment |

### Chapter 15: Rotating Equipment
| Document | Path | Description |
|----------|------|-------------|
| Compressors | [docs/process/equipment/compressors.md](process/equipment/compressors.md) | Compressor models |
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
| Flow Meters | [docs/wiki/flow_meter_models.md](wiki/flow_meter_models.md) | Flow metering |
| Venturi | [docs/wiki/venturi_calculation.md](wiki/venturi_calculation.md) | Venturi calculations |
| Tanks | [docs/process/equipment/tanks.md](process/equipment/tanks.md) | Tank models |

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
| Pipelines | [docs/process/equipment/pipelines.md](process/equipment/pipelines.md) | Pipeline models |
| Beggs & Brill | [docs/process/PipeBeggsAndBrills.md](process/PipeBeggsAndBrills.md) | Beggs & Brill correlation |
| Networks | [docs/process/equipment/networks.md](process/equipment/networks.md) | Pipeline network modeling |
| Reservoirs | [docs/process/equipment/reservoirs.md](process/equipment/reservoirs.md) | Reservoir modeling |
| Subsea Systems | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems.md) | Subsea wells and flowlines |

### Chapter 20: Utility Equipment
| Document | Path | Description |
|----------|------|-------------|
| Utility Overview | [docs/process/equipment/util/README.md](process/equipment/util/README.md) | Utility equipment |
| Adjusters | [docs/process/equipment/util/adjusters.md](process/equipment/util/adjusters.md) | Adjuster units |
| Recycles | [docs/process/equipment/util/recycles.md](process/equipment/util/recycles.md) | Recycle units |
| Calculators | [docs/process/equipment/util/calculators.md](process/equipment/util/calculators.md) | Calculator units |

### Chapter 21: Process Control
| Document | Path | Description |
|----------|------|-------------|
| Controllers | [docs/process/controllers.md](process/controllers.md) | Process controllers |
| Process Control | [docs/wiki/process_control.md](wiki/process_control.md) | Control systems |
| Dynamic Simulation Guide | [docs/simulation/dynamic_simulation_guide.md](simulation/dynamic_simulation_guide.md) | Comprehensive dynamic/transient simulation guide |
| Transient Simulation | [docs/wiki/process_transient_simulation_guide.md](wiki/process_transient_simulation_guide.md) | Transient simulation patterns |

### Chapter 22: Mechanical Design
| Document | Path | Description |
|----------|------|-------------|
| Mechanical Design | [docs/process/mechanical_design.md](process/mechanical_design.md) | Mechanical design overview |
| Design Standards | [docs/process/mechanical_design_standards.md](process/mechanical_design_standards.md) | Design standards |
| Design Database | [docs/process/mechanical_design_database.md](process/mechanical_design_database.md) | Design database |
| TORG Integration | [docs/process/torg_integration.md](process/torg_integration.md) | TORG integration |
| Field Development | [docs/process/field_development_orchestration.md](process/field_development_orchestration.md) | Field development orchestration |

### Chapter 23: Serialization & Persistence
| Document | Path | Description |
|----------|------|-------------|
| Process Serialization | [docs/simulation/process_serialization.md](simulation/process_serialization.md) | Save/load process models |

---

## Part IV: Pipeline & Multiphase Flow

### Chapter 24: Pipeline Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Fluid Mechanics Overview | [docs/fluidmechanics/README.md](fluidmechanics/README.md) | Fluid mechanics module |
| Pipeline Index | [docs/wiki/pipeline_index.md](wiki/pipeline_index.md) | Pipeline documentation index |
| Flow Equations | [docs/wiki/pipeline_flow_equations.md](wiki/pipeline_flow_equations.md) | Pipeline flow equations |
| Single Phase Flow | [docs/fluidmechanics/single_phase_pipe_flow.md](fluidmechanics/single_phase_pipe_flow.md) | Single phase pipe flow |

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
| Batch Studies | [docs/process/optimization/batch-studies.md](process/optimization/batch-studies.md) | Batch studies |
| Bottleneck Analysis | [docs/wiki/bottleneck_analysis.md](wiki/bottleneck_analysis.md) | Bottleneck analysis |
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
| **Integrated Framework** | [docs/fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md) | PVT→Reservoir→Well→Process integration guide |
| **Strategy** | [docs/fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md](fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md) | Field development strategy and roadmap |
| Field Planning | [docs/wiki/field_development_planning.md](wiki/field_development_planning.md) | Field development planning |
| Field Engine | [docs/simulation/field_development_engine.md](simulation/field_development_engine.md) | Field development engine |
| **Economics** | [docs/process/economics/README.md](process/economics/README.md) | Economics module: NPV, IRR, tax models, decline curves |

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
| Integration/AI | 12 |
| Pipeline/Flow | 15 |
| PVT/Reservoir | 15 |
| Standards | 6 |
| Development | 5 |
| Statistics | 4 |
| Examples | 3 |
| Other | 24 |
| **Total** | **232** |

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
