# NeqSim Documentation

**NeqSim** (Non-Equilibrium Simulator) is a comprehensive Java library for thermodynamic, physical property, and process simulation. This documentation covers all major packages and provides detailed guides for developing applications.

---

## Quick Start

```java
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

// Create a natural gas fluid
SystemInterface gas = new SystemSrkEos(298.15, 50.0);
gas.addComponent("methane", 0.90);
gas.addComponent("ethane", 0.05);
gas.addComponent("propane", 0.03);
gas.addComponent("CO2", 0.02);
gas.setMixingRule("classic");

// Perform flash calculation
ThermodynamicOperations ops = new ThermodynamicOperations(gas);
ops.TPflash();

// Get properties
System.out.println("Density: " + gas.getDensity("kg/m3") + " kg/m³");
System.out.println("Compressibility: " + gas.getZ());
```

---

## Package Documentation

### Core Thermodynamics

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.thermo` | [thermo/](thermo/README.md) | Thermodynamic systems, phases, components, equations of state, mixing rules, fluid characterization |
| `neqsim.thermodynamicoperations` | [thermodynamicoperations/](thermodynamicoperations/README.md) | Flash calculations, phase envelopes, saturation operations |
| `neqsim.physicalproperties` | [physical_properties/](physical_properties/README.md) | Transport properties: viscosity, thermal conductivity, diffusivity, interfacial tension |

### Process Simulation

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.process` | [process/](process/README.md) | Process equipment, unit operations, controllers, process systems, safety systems |
| `neqsim.fluidmechanics` | [fluidmechanics/](fluidmechanics/README.md) | Pipeline flow, pressure drop, two-phase flow, flow nodes |

### PVT and Reservoir

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.pvtsimulation` | [pvtsimulation/](pvtsimulation/README.md) | PVT experiments: CME, CVD, DL, separator tests, swelling tests |
| `neqsim.blackoil` | [blackoil/](blackoil/README.md) | Black oil model, PVT tables, Rs, Bo, Bg correlations |

### Chemical Reactions

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.chemicalreactions` | [chemicalreactions/](chemicalreactions/README.md) | Chemical equilibrium, reaction kinetics |

### Quality Standards

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.standards` | [standards/](standards/README.md) | ISO 6976, ISO 6578, ISO 15403, ASTM D6377, sales contracts |
| `neqsim.statistics` | [statistics/](statistics/README.md) | Parameter fitting, Monte Carlo simulation, data analysis |

### Utilities

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.util` | [util/](util/README.md) | Database access, unit conversion, serialization, exceptions |
| `neqsim.mathlib` | [mathlib/](mathlib/README.md) | Mathematical utilities, nonlinear solvers |

---

## Documentation Structure

```
docs/
├── README.md                      # This file - main index
├── modules.md                     # Module overview
├── DEVELOPER_SETUP.md             # Development environment setup
│
├── thermo/                        # Thermodynamic package
│   ├── README.md                  # Package overview
│   ├── system/                    # EoS implementations
│   ├── phase/                     # Phase modeling
│   ├── component/                 # Component properties
│   ├── mixingrule/                # Mixing rules
│   ├── characterization/          # Plus fraction handling
│   ├── fluid_creation_guide.md
│   ├── mixing_rules_guide.md
│   ├── component_database_guide.md
│   ├── inter_table_guide.md
│   ├── flash_calculations_guide.md
│   ├── mathematical_models.md
│   ├── gerg2008_eoscg.md
│   └── pvt_fluid_characterization.md
│
├── thermodynamicoperations/       # Flash operations
│   └── README.md
│
├── physical_properties/           # Transport properties
│   ├── README.md
│   ├── viscosity_models.md
│   ├── thermal_conductivity_models.md
│   ├── diffusivity_models.md
│   ├── interfacial_properties.md
│   └── density_models.md
│
├── process/                       # Process simulation
│   ├── README.md                  # Package overview
│   ├── controllers.md             # Controllers and adjusters
│   ├── equipment/                 # Equipment documentation
│   │   ├── README.md              # Equipment index
│   │   ├── streams.md
│   │   ├── separators.md
│   │   ├── heat_exchangers.md
│   │   ├── compressors.md
│   │   ├── pumps.md
│   │   ├── expanders.md
│   │   ├── valves.md
│   │   ├── distillation.md
│   │   ├── absorbers.md
│   │   ├── reactors.md
│   │   ├── wells.md
│   │   ├── tanks.md
│   │   ├── pipelines.md
│   │   └── mixers_splitters.md
│   └── safety/                    # Safety systems
│
├── fluidmechanics/               # Pipe flow
│   └── README.md
│
├── pvtsimulation/                # PVT experiments
│   └── README.md
│
├── blackoil/                     # Black oil model
│   └── README.md
│
├── chemicalreactions/            # Reactions
│   └── README.md
│
├── standards/                    # Quality standards
│   ├── README.md
│   ├── iso6976_calorific_values.md
│   ├── iso6578_lng_density.md
│   ├── iso15403_cng_quality.md
│   ├── dew_point_standards.md
│   ├── astm_d6377_rvp.md
│   └── sales_contracts.md
│
├── statistics/                   # Statistics package
│   ├── README.md
│   ├── parameter_fitting.md
│   ├── monte_carlo_simulation.md
│   └── data_analysis.md
│
├── util/                         # Utilities
│   └── README.md
│
├── mathlib/                      # Math utilities
│   └── README.md
│
├── examples/                     # Code examples
│   └── ...
│
└── wiki/                         # Additional wiki pages
    └── ...
```

---

## Topic Guides

Specialized guides for advanced features and use cases:

### Safety and Emergency Systems

| Guide | Description |
|-------|-------------|
| [ESD_BLOWDOWN_SYSTEM.md](ESD_BLOWDOWN_SYSTEM.md) | Emergency shutdown and blowdown systems |
| [HIPPS_SUMMARY.md](HIPPS_SUMMARY.md) | High Integrity Pressure Protection Systems |
| [hipps_implementation.md](hipps_implementation.md) | HIPPS implementation details |
| [hipps_safety_logic.md](hipps_safety_logic.md) | HIPPS safety logic |
| [INTEGRATED_SAFETY_SYSTEMS.md](INTEGRATED_SAFETY_SYSTEMS.md) | Integrated safety systems overview |
| [layered_safety_architecture.md](layered_safety_architecture.md) | Layered safety architecture |
| [sis_logic_implementation.md](sis_logic_implementation.md) | SIS logic implementation |
| [SAFETY_SIMULATION_ROADMAP.md](SAFETY_SIMULATION_ROADMAP.md) | Safety simulation roadmap |

### Process Logic and Control

| Guide | Description |
|-------|-------------|
| [process_logic_framework.md](process_logic_framework.md) | Process logic framework |
| [ProcessLogicEnhancements.md](ProcessLogicEnhancements.md) | Logic enhancements |
| [advanced_process_logic.md](advanced_process_logic.md) | Advanced process logic |
| [alarm_system_guide.md](alarm_system_guide.md) | Alarm system guide |
| [alarm_triggered_logic_example.md](alarm_triggered_logic_example.md) | Alarm-triggered logic |
| [mpc_integration.md](mpc_integration.md) | MPC integration |

### Dynamic Simulation

| Guide | Description |
|-------|-------------|
| [fire_blowdown_capabilities.md](fire_blowdown_capabilities.md) | Fire and blowdown simulation |
| [fire_heat_transfer_enhancements.md](fire_heat_transfer_enhancements.md) | Fire heat transfer |
| [psv_dynamic_sizing_example.md](psv_dynamic_sizing_example.md) | PSV dynamic sizing |
| [rupture_disk_dynamic_behavior.md](rupture_disk_dynamic_behavior.md) | Rupture disk behavior |
| [turboexpander_compressor_model.md](turboexpander_compressor_model.md) | Turboexpander modeling |

### Well and Reservoir

| Guide | Description |
|-------|-------------|
| [well_simulation_guide.md](well_simulation_guide.md) | Well simulation guide |
| [well_and_choke_simulation.md](well_and_choke_simulation.md) | Choke simulation |
| [field_development_engine.md](field_development_engine.md) | Field development |

### PVT and Characterization

| Guide | Description |
|-------|-------------|
| [pvt_workflow.md](pvt_workflow.md) | PVT workflow |
| [blackoil_pvt_export.md](blackoil_pvt_export.md) | Black oil PVT export |
| [whitson_pvt_reader.md](whitson_pvt_reader.md) | Whitson PVT reader |
| [fluid_characterization_mathematics.md](fluid_characterization_mathematics.md) | Characterization math |

### Advanced Features

| Guide | Description |
|-------|-------------|
| [parallel_process_simulation.md](parallel_process_simulation.md) | Parallel simulation |
| [recycle_acceleration_guide.md](recycle_acceleration_guide.md) | Recycle convergence |
| [graph_based_process_simulation.md](graph_based_process_simulation.md) | Graph-based simulation |
| [differentiable_thermodynamics.md](differentiable_thermodynamics.md) | Auto-differentiation |
| [equipment_factory.md](equipment_factory.md) | Equipment factory |
| [dexpi-reader.md](dexpi-reader.md) | DEXPI P&ID reader |

### Integration

| Guide | Description |
|-------|-------------|
| [ai_platform_integration.md](ai_platform_integration.md) | AI/ML integration |
| [ml_integration.md](ml_integration.md) | Machine learning |
| [REAL_TIME_INTEGRATION_GUIDE.md](REAL_TIME_INTEGRATION_GUIDE.md) | Real-time systems |
| [QRA_INTEGRATION_GUIDE.md](QRA_INTEGRATION_GUIDE.md) | QRA integration |

---

## Equations of State Quick Reference

| EoS | Class | Application |
|-----|-------|-------------|
| SRK | `SystemSrkEos` | General hydrocarbon systems |
| PR | `SystemPrEos` | General hydrocarbon systems |
| PR-1978 | `SystemPrEos1978` | Improved liquid densities |
| SRK-CPA | `SystemSrkCPAstatoil` | Associating fluids (water, alcohols, glycols) |
| PC-SAFT | `SystemPCSAFT` | Polymers, associating fluids |
| GERG-2008 | `SystemGERG2008Eos` | Natural gas reference |
| EOS-CG | `SystemEOSCGEos` | CO₂-rich systems (CCS) |
| UMR-PRU | `SystemUMRPRUMCEos` | Wide-range hydrocarbon systems |

---

## Process Equipment Quick Reference

| Category | Equipment | Class |
|----------|-----------|-------|
| Separation | 2-phase separator | `Separator` |
| | 3-phase separator | `ThreePhaseSeparator` |
| | Distillation column | `DistillationColumn` |
| Heat Transfer | Heater | `Heater` |
| | Cooler | `Cooler` |
| | Heat exchanger | `HeatExchanger` |
| Compression | Compressor | `Compressor` |
| | Pump | `Pump` |
| | Expander | `Expander` |
| Flow Control | Valve | `ThrottlingValve` |
| | Mixer | `Mixer`, `StaticMixer` |
| | Splitter | `Splitter` |
| Well/Reservoir | Well | `SimpleWell` |
| | Choke | `ChokeValve` |

---

## Getting Help

- **GitHub Issues:** Report bugs and request features
- **Discussions:** Ask questions and share knowledge
- **API JavaDoc:** Generated from source code
- **Examples:** See `examples/` and `notebooks/` directories

---

## Version Compatibility

- **Java:** 8+ (builds on 8, 11, 17, 21)
- **Python:** Via jpype (`neqsim-python` package)
- **MATLAB:** Via Java interface

---

## Related Resources

- [NeqSim GitHub Repository](https://github.com/equinor/neqsim)
- [neqsim-python](https://github.com/equinor/neqsim-python)
- [Example Notebooks](../notebooks/)

