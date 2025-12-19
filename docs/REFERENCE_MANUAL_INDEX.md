# NeqSim Reference Manual - Master Index

This document maps all 200+ documentation files to the reference manual structure. Use this as a comprehensive table of contents and navigation guide.

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
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP.md) | Development environment setup |

### Chapter 3: Quick Start Examples
| Document | Path | Description |
|----------|------|-------------|
| Usage Examples | [docs/wiki/usage_examples.md](wiki/usage_examples.md) | Basic usage patterns |
| FAQ | [docs/wiki/faq.md](wiki/faq.md) | Frequently asked questions |

---

## Part II: Thermodynamics

### Chapter 4: Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Thermo Overview | [docs/thermo/README.md](thermo/README.md) | Thermodynamics module overview |
| Thermodynamics Guide | [docs/wiki/thermodynamics_guide.md](wiki/thermodynamics_guide.md) | Comprehensive thermodynamics guide |

### Chapter 5: Fluid Creation & Components
| Document | Path | Description |
|----------|------|-------------|
| Fluid Creation Guide | [docs/thermo/fluid_creation_guide.md](thermo/fluid_creation_guide.md) | Creating thermodynamic systems |
| Component Database | [docs/thermo/component_database_guide.md](thermo/component_database_guide.md) | Component properties and database |
| Mathematical Models | [docs/thermo/mathematical_models.md](thermo/mathematical_models.md) | Underlying mathematical models |

### Chapter 6: Equations of State
| Document | Path | Description |
|----------|------|-------------|
| GERG-2008 | [docs/thermo/gerg2008_eoscg.md](thermo/gerg2008_eoscg.md) | GERG-2008 equation of state |
| Mixing Rules | [docs/thermo/mixing_rules_guide.md](thermo/mixing_rules_guide.md) | Mixing rules and BIPs |
| EOS Selection | [docs/thermo/eos_selection_guide.md](thermo/eos_selection_guide.md) | Choosing the right EOS |

### Chapter 7: Flash Calculations
| Document | Path | Description |
|----------|------|-------------|
| Flash Guide | [docs/thermo/flash_calculations_guide.md](thermo/flash_calculations_guide.md) | Flash calculation methods |
| Flash Equations | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests.md) | Flash equations and testing |
| Thermo Operations | [docs/thermo/thermodynamic_operations.md](thermo/thermodynamic_operations.md) | Thermodynamic operations |

### Chapter 8: Fluid Characterization
| Document | Path | Description |
|----------|------|-------------|
| Characterization | [docs/wiki/fluid_characterization.md](wiki/fluid_characterization.md) | Fluid characterization methods |
| TBP Fractions | [docs/wiki/tbp_fraction_models.md](wiki/tbp_fraction_models.md) | TBP fraction modeling |
| PVT Characterization | [docs/thermo/pvt_fluid_characterization.md](thermo/pvt_fluid_characterization.md) | PVT fluid characterization |

### Chapter 9: Physical Properties
| Document | Path | Description |
|----------|------|-------------|
| Properties Overview | [docs/thermo/physical_properties.md](thermo/physical_properties.md) | Physical property calculations |
| Viscosity Models | [docs/wiki/viscosity_models.md](wiki/viscosity_models.md) | Viscosity calculation models |
| Steam Tables | [docs/wiki/steam_tables_if97.md](wiki/steam_tables_if97.md) | IF97 steam table implementation |
| Thermodynamic Workflows | [docs/thermo/thermodynamic_workflows.md](thermo/thermodynamic_workflows.md) | Common thermodynamic workflows |

---

## Part III: Process Simulation

### Chapter 10: Process Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Process Overview | [docs/process/README.md](process/README.md) | Process simulation module |
| Process Guide | [docs/wiki/process_simulation.md](wiki/process_simulation.md) | Process simulation guide |
| Advanced Process | [docs/wiki/advanced_process_simulation.md](wiki/advanced_process_simulation.md) | Advanced techniques |
| Logical Operations | [docs/wiki/logical_unit_operations.md](wiki/logical_unit_operations.md) | Logical unit operations |

### Chapter 11: Separation Equipment
| Document | Path | Description |
|----------|------|-------------|
| Separators | [docs/process/equipment/separators.md](process/equipment/separators.md) | Separator models |
| Distillation | [docs/process/equipment/distillation.md](process/equipment/distillation.md) | Distillation columns |
| Absorbers | [docs/process/equipment/absorbers.md](process/equipment/absorbers.md) | Absorption equipment |
| Membrane | [docs/wiki/membrane_separation.md](wiki/membrane_separation.md) | Membrane separation |

### Chapter 12: Rotating Equipment
| Document | Path | Description |
|----------|------|-------------|
| Compressors | [docs/process/equipment/compressors.md](process/equipment/compressors.md) | Compressor models |
| Pumps | [docs/wiki/pump_usage_guide.md](wiki/pump_usage_guide.md) | Pump usage guide |
| Ejectors | [docs/process/equipment/ejectors.md](process/equipment/ejectors.md) | Ejector systems |

### Chapter 13: Heat Transfer Equipment
| Document | Path | Description |
|----------|------|-------------|
| Air Cooler | [docs/wiki/air_cooler.md](wiki/air_cooler.md) | Air cooler models |
| Water Cooler | [docs/wiki/water_cooler.md](wiki/water_cooler.md) | Water cooler models |
| Steam Heater | [docs/wiki/steam_heater.md](wiki/steam_heater.md) | Steam heater models |
| Mechanical Design | [docs/wiki/heat_exchanger_mechanical_design.md](wiki/heat_exchanger_mechanical_design.md) | HX mechanical design |

### Chapter 14: Valves & Flow Control
| Document | Path | Description |
|----------|------|-------------|
| Valves | [docs/process/equipment/valves.md](process/equipment/valves.md) | Valve models |
| Flow Meters | [docs/wiki/flow_meter_models.md](wiki/flow_meter_models.md) | Flow metering |
| Venturi | [docs/wiki/venturi_calculation.md](wiki/venturi_calculation.md) | Venturi calculations |
| Tanks | [docs/process/equipment/tanks.md](process/equipment/tanks.md) | Tank models |

### Chapter 15: Special Equipment
| Document | Path | Description |
|----------|------|-------------|
| Reactors | [docs/wiki/gibbs_reactor.md](wiki/gibbs_reactor.md) | Gibbs reactor |
| Electrolyzers | [docs/process/equipment/electrolyzers.md](process/equipment/electrolyzers.md) | Electrolyzer systems |
| Battery Storage | [docs/wiki/battery_storage.md](wiki/battery_storage.md) | Battery storage |
| Solar Panel | [docs/wiki/solar_panel.md](wiki/solar_panel.md) | Solar panel models |

### Chapter 16: Process Control
| Document | Path | Description |
|----------|------|-------------|
| Controllers | [docs/process/controllers.md](process/controllers.md) | Process controllers |
| Process Control | [docs/wiki/process_control.md](wiki/process_control.md) | Control systems |
| Transient Simulation | [docs/wiki/process_transient_simulation_guide.md](wiki/process_transient_simulation_guide.md) | Transient simulation |

---

## Part IV: Pipeline & Multiphase Flow

### Chapter 17: Pipeline Fundamentals
| Document | Path | Description |
|----------|------|-------------|
| Fluid Mechanics Overview | [docs/fluidmechanics/README.md](fluidmechanics/README.md) | Fluid mechanics module |
| Pipeline Index | [docs/wiki/pipeline_index.md](wiki/pipeline_index.md) | Pipeline documentation index |
| Flow Equations | [docs/wiki/pipeline_flow_equations.md](wiki/pipeline_flow_equations.md) | Pipeline flow equations |
| Single Phase Flow | [docs/fluidmechanics/single_phase_pipe_flow.md](fluidmechanics/single_phase_pipe_flow.md) | Single phase pipe flow |

### Chapter 18: Pressure Drop Calculations
| Document | Path | Description |
|----------|------|-------------|
| Pressure Drop | [docs/wiki/pipeline_pressure_drop.md](wiki/pipeline_pressure_drop.md) | Pressure drop calculation |
| Beggs & Brill | [docs/wiki/beggs_and_brill_correlation.md](wiki/beggs_and_brill_correlation.md) | Beggs & Brill correlation |
| Friction Factors | [docs/wiki/friction_factor_models.md](wiki/friction_factor_models.md) | Friction factor models |

### Chapter 19: Heat Transfer in Pipelines
| Document | Path | Description |
|----------|------|-------------|
| Heat Transfer | [docs/wiki/pipeline_heat_transfer.md](wiki/pipeline_heat_transfer.md) | Pipeline heat transfer |
| Pipe Wall | [docs/wiki/pipe_wall_heat_transfer.md](wiki/pipe_wall_heat_transfer.md) | Pipe wall heat transfer |
| Interphase | [docs/fluidmechanics/InterphaseHeatMassTransfer.md](fluidmechanics/InterphaseHeatMassTransfer.md) | Interphase heat/mass transfer |
| Mass Transfer | [docs/fluidmechanics/mass_transfer.md](fluidmechanics/mass_transfer.md) | Mass transfer models |

### Chapter 20: Two-Phase & Multiphase Flow
| Document | Path | Description |
|----------|------|-------------|
| Two-Phase Model | [docs/fluidmechanics/TwoPhasePipeFlowModel.md](fluidmechanics/TwoPhasePipeFlowModel.md) | Two-phase pipe flow |
| Two-Fluid Model | [docs/wiki/two_fluid_model.md](wiki/two_fluid_model.md) | Two-fluid model |
| Multiphase Transient | [docs/wiki/multiphase_transient_model.md](wiki/multiphase_transient_model.md) | Multiphase transient |
| Development Plan | [docs/fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md](fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md) | Development plan |

### Chapter 21: Transient Pipeline Simulation
| Document | Path | Description |
|----------|------|-------------|
| Transient Simulation | [docs/wiki/pipeline_transient_simulation.md](wiki/pipeline_transient_simulation.md) | Transient pipeline |
| Model Recommendations | [docs/wiki/pipeline_model_recommendations.md](wiki/pipeline_model_recommendations.md) | Model recommendations |
| Water Hammer | [docs/wiki/water_hammer_implementation.md](wiki/water_hammer_implementation.md) | Water hammer |

---

## Part V: Safety & Reliability

### Chapter 22: Safety Overview
| Document | Path | Description |
|----------|------|-------------|
| Safety Overview | [docs/safety/README.md](safety/README.md) | Safety systems module |
| Safety Roadmap | [docs/safety/SAFETY_SIMULATION_ROADMAP.md](safety/SAFETY_SIMULATION_ROADMAP.md) | Safety simulation roadmap |
| Layered Architecture | [docs/safety/layered_safety_architecture.md](safety/layered_safety_architecture.md) | Layered safety architecture |

### Chapter 23: Pressure Relief Systems
| Document | Path | Description |
|----------|------|-------------|
| PSV Dynamic Sizing | [docs/wiki/psv_dynamic_sizing_example.md](wiki/psv_dynamic_sizing_example.md) | PSV dynamic sizing |
| PSD Valve Trip | [docs/wiki/psd_valve_hihi_trip.md](wiki/psd_valve_hihi_trip.md) | PSD valve HIHI trip |
| Rupture Disks | [docs/safety/rupture_disk_dynamic_behavior.md](safety/rupture_disk_dynamic_behavior.md) | Rupture disk behavior |

### Chapter 24: HIPPS Systems
| Document | Path | Description |
|----------|------|-------------|
| HIPPS Summary | [docs/safety/HIPPS_SUMMARY.md](safety/HIPPS_SUMMARY.md) | HIPPS summary |
| HIPPS Implementation | [docs/safety/hipps_implementation.md](safety/hipps_implementation.md) | HIPPS implementation |
| HIPPS Safety Logic | [docs/safety/hipps_safety_logic.md](safety/hipps_safety_logic.md) | HIPPS safety logic |

### Chapter 25: ESD & Fire Systems
| Document | Path | Description |
|----------|------|-------------|
| ESD Fire Alarm | [docs/wiki/esd_fire_alarm_system.md](wiki/esd_fire_alarm_system.md) | ESD/Fire alarm systems |
| Pressure Monitoring | [docs/safety/PRESSURE_MONITORING_ESD.md](safety/PRESSURE_MONITORING_ESD.md) | Pressure monitoring ESD |
| Fire Heat Transfer | [docs/safety/fire_heat_transfer_enhancements.md](safety/fire_heat_transfer_enhancements.md) | Fire heat transfer |

### Chapter 26: Integrated Safety Systems
| Document | Path | Description |
|----------|------|-------------|
| Integrated Safety | [docs/safety/INTEGRATED_SAFETY_SYSTEMS.md](safety/INTEGRATED_SAFETY_SYSTEMS.md) | Integrated safety systems |
| SIS Logic | [docs/safety/sis_logic_implementation.md](safety/sis_logic_implementation.md) | SIS logic implementation |
| Choke Protection | [docs/wiki/choke_collapse_psd_protection.md](wiki/choke_collapse_psd_protection.md) | Choke collapse protection |
| Safety Chain Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests.md) | Safety chain tests |
| Scenario Generation | [docs/process/safety/scenario-generation.md](process/safety/scenario-generation.md) | Automatic scenario generation |

---

## Part VI: PVT & Reservoir

### Chapter 27: PVT Simulation
| Document | Path | Description |
|----------|------|-------------|
| PVT Overview | [docs/pvtsimulation/README.md](pvtsimulation/README.md) | PVT simulation module |
| PVT Workflows | [docs/wiki/pvt_simulation_workflows.md](wiki/pvt_simulation_workflows.md) | PVT simulation workflows |
| Property Flash | [docs/wiki/property_flash_workflows.md](wiki/property_flash_workflows.md) | Property flash workflows |

### Chapter 28: Black Oil Models
| Document | Path | Description |
|----------|------|-------------|
| Black Oil Overview | [docs/blackoil/README.md](blackoil/README.md) | Black oil module |
| Flash Playbook | [docs/wiki/black_oil_flash_playbook.md](wiki/black_oil_flash_playbook.md) | Black oil flash playbook |

### Chapter 29: Gas Quality
| Document | Path | Description |
|----------|------|-------------|
| Gas Quality Standards | [docs/wiki/gas_quality_standards_from_tests.md](wiki/gas_quality_standards_from_tests.md) | Gas quality standards |
| Humid Air | [docs/wiki/humid_air_math.md](wiki/humid_air_math.md) | Humid air calculations |

---

## Part VII: Advanced Topics

### Chapter 30: Future Infrastructure
| Document | Path | Description |
|----------|------|-------------|
| Future Infrastructure | [docs/process/future-infrastructure.md](process/future-infrastructure.md) | Future infrastructure classes |
| API Reference | [docs/process/future-api-reference.md](process/future-api-reference.md) | Future API reference |

### Chapter 31: Digital Twins
| Document | Path | Description |
|----------|------|-------------|
| Digital Twin | [docs/process/digital-twin-integration.md](process/digital-twin-integration.md) | Digital twin integration |
| Lifecycle | [docs/process/lifecycle/README.md](process/lifecycle/README.md) | Lifecycle management |

### Chapter 32: AI/ML Integration
| Document | Path | Description |
|----------|------|-------------|
| AI Platform | [docs/integration/ai_platform_integration.md](integration/ai_platform_integration.md) | AI platform integration |
| ML Integration | [docs/integration/ml_integration.md](integration/ml_integration.md) | ML integration guide |
| ML Surrogate | [docs/process/ml/README.md](process/ml/README.md) | ML surrogate models |

### Chapter 33: Sustainability
| Document | Path | Description |
|----------|------|-------------|
| Emissions Tracking | [docs/process/sustainability/README.md](process/sustainability/README.md) | Emissions tracking |

### Chapter 34: Optimization
| Document | Path | Description |
|----------|------|-------------|
| Batch Studies | [docs/process/optimization/batch-studies.md](process/optimization/batch-studies.md) | Batch studies |
| Bottleneck Analysis | [docs/wiki/bottleneck_analysis.md](wiki/bottleneck_analysis.md) | Bottleneck analysis |
| Calibration | [docs/process/calibration/README.md](process/calibration/README.md) | Model calibration |

### Chapter 35: Real-Time Integration
| Document | Path | Description |
|----------|------|-------------|
| Real-Time Guide | [docs/integration/REAL_TIME_INTEGRATION_GUIDE.md](integration/REAL_TIME_INTEGRATION_GUIDE.md) | Real-time integration |
| MPC Integration | [docs/integration/mpc_integration.md](integration/mpc_integration.md) | MPC integration |
| Industrial MPC | [docs/integration/neqsim_industrial_mpc_integration.md](integration/neqsim_industrial_mpc_integration.md) | Industrial MPC |

### Chapter 36: External Integrations
| Document | Path | Description |
|----------|------|-------------|
| DEXPI Reader | [docs/integration/dexpi-reader.md](integration/dexpi-reader.md) | DEXPI reader |
| QRA Integration | [docs/integration/QRA_INTEGRATION_GUIDE.md](integration/QRA_INTEGRATION_GUIDE.md) | QRA integration |

### Chapter 37: Process Logic Framework
| Document | Path | Description |
|----------|------|-------------|
| Process Logic | [docs/simulation/process_logic_framework.md](simulation/process_logic_framework.md) | Process logic framework |
| Implementation | [docs/simulation/process_logic_implementation_summary.md](simulation/process_logic_implementation_summary.md) | Implementation summary |
| Enhancements | [docs/simulation/ProcessLogicEnhancements.md](simulation/ProcessLogicEnhancements.md) | Logic enhancements |
| Graph-Based | [docs/simulation/graph_based_process_simulation.md](simulation/graph_based_process_simulation.md) | Graph-based simulation |

### Chapter 38: Field Development
| Document | Path | Description |
|----------|------|-------------|
| Field Planning | [docs/wiki/field_development_planning.md](wiki/field_development_planning.md) | Field development planning |

---

## Part VIII: Developer Guide

### Chapter 39: Contributing
| Document | Path | Description |
|----------|------|-------------|
| Development Overview | [docs/development/README.md](development/README.md) | Development overview |
| Contributing Structure | [docs/development/contributing-structure.md](development/contributing-structure.md) | Contributing guidelines |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP.md) | Developer setup |

### Chapter 40: Testing
| Document | Path | Description |
|----------|------|-------------|
| Test Overview | [docs/wiki/test-overview.md](wiki/test-overview.md) | Test overview |
| Flash Tests | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests.md) | Flash equation tests |
| Safety Tests | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests.md) | Safety chain tests |

### Chapter 41: Notebooks & Examples
| Document | Path | Description |
|----------|------|-------------|
| Colab Notebooks | [docs/wiki/java_simulation_from_colab_notebooks.md](wiki/java_simulation_from_colab_notebooks.md) | Colab notebooks |

### Chapter 42: Standards
| Document | Path | Description |
|----------|------|-------------|
| ASTM Standards | [docs/standards/astm_d6377_rvp.md](standards/astm_d6377_rvp.md) | ASTM D6377 RVP |

---

## Appendices

### Appendix A: Chemical Reactions
| Document | Path | Description |
|----------|------|-------------|
| Chemical Reactions | [docs/chemicalreactions/README.md](chemicalreactions/README.md) | Chemical reactions module |

### Appendix B: Statistics
| Document | Path | Description |
|----------|------|-------------|
| Statistics | [docs/statistics/README.md](statistics/README.md) | Statistics module |

### Appendix C: Mathematical Library
| Document | Path | Description |
|----------|------|-------------|
| Math Library | [docs/mathlib/README.md](mathlib/README.md) | Mathematical library |

### Appendix D: Utilities
| Document | Path | Description |
|----------|------|-------------|
| Utilities | [docs/util/README.md](util/README.md) | Utility functions |

---

## Additional Wiki Documents

The following documents from `docs/wiki/` provide supplementary information:

### Process Equipment (Additional)
| Document | Description |
|----------|-------------|
| [distillation_column.md](wiki/distillation_column.md) | Distillation column details |
| [separator_calculations.md](wiki/separator_calculations.md) | Separator calculations |
| [compressor_calculations.md](wiki/compressor_calculations.md) | Compressor calculations |
| [heat_exchanger_calculations.md](wiki/heat_exchanger_calculations.md) | Heat exchanger calculations |

### Thermodynamics (Additional)
| Document | Description |
|----------|-------------|
| [phase_behavior.md](wiki/phase_behavior.md) | Phase behavior |
| [fugacity_calculations.md](wiki/fugacity_calculations.md) | Fugacity calculations |
| [activity_coefficients.md](wiki/activity_coefficients.md) | Activity coefficients |

### Pipeline (Additional)
| Document | Description |
|----------|-------------|
| [pipeline_design.md](wiki/pipeline_design.md) | Pipeline design |
| [slug_flow.md](wiki/slug_flow.md) | Slug flow modeling |
| [erosion_velocity.md](wiki/erosion_velocity.md) | Erosion velocity |

---

## Document Statistics

| Category | Count |
|----------|-------|
| Wiki/Tutorials | 58 |
| Thermodynamics | 17 |
| Process Simulation | 25+ |
| Safety Systems | 15 |
| Integration/AI | 9 |
| Pipeline/Flow | 12 |
| PVT/Reservoir | 6 |
| Development | 5 |
| **Total** | **200+** |

---

## Navigation Tips

1. **Start Here**: If new to NeqSim, begin with [Getting Started](wiki/getting_started.md)
2. **By Task**: Use the chapter structure above to find relevant sections
3. **By Equipment**: See Part III, Chapters 11-15 for equipment-specific docs
4. **For Developers**: Jump to Part VIII for contribution guidelines
5. **Search**: Use MkDocs search (if enabled) for keyword searches

---

*This index was auto-generated. Last updated: December 2024*
