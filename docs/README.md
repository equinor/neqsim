---
title: NeqSim Documentation
description: Comprehensive Java library for thermodynamic, physical property, and process simulation. Includes guides for all major packages and application development.
---

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
| `neqsim.thermo` | [thermo/](thermo/README) | Thermodynamic systems, phases, components, equations of state, mixing rules, fluid characterization |
| `neqsim.thermodynamicoperations` | [thermodynamicoperations/](thermodynamicoperations/README) | Flash calculations, phase envelopes, saturation operations |
| `neqsim.physicalproperties` | [physical_properties/](physical_properties/README) | Transport properties: viscosity, thermal conductivity, diffusivity, interfacial tension |

### Process Simulation

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.process` | [process/](process/README) | Process equipment, unit operations, controllers, process systems, safety systems |
| `neqsim.fluidmechanics` | [fluidmechanics/](fluidmechanics/README) | Pipeline flow, pressure drop, two-phase flow, flow nodes |

### PVT and Reservoir

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.pvtsimulation` | [pvtsimulation/](pvtsimulation/README) | PVT experiments: CME, CVD, DL, separator tests, swelling tests |
| `neqsim.blackoil` | [blackoil/](blackoil/README) | Black oil model, PVT tables, Rs, Bo, Bg correlations |

### Flow Assurance

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.pvtsimulation.flowassurance` | [pvtsimulation/flowassurance/](pvtsimulation/flowassurance/README) | Asphaltene stability, De Boer screening, CPA-based onset calculations |

### Chemical Reactions

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.chemicalreactions` | [chemicalreactions/](chemicalreactions/README) | Chemical equilibrium, reaction kinetics |

### Quality Standards

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.standards` | [standards/](standards/README) | ISO 6976, ISO 6578, ISO 15403, ASTM D6377, sales contracts |
| `neqsim.statistics` | [statistics/](statistics/README) | Parameter fitting, Monte Carlo simulation, data analysis |

### Utilities

| Package | Documentation | Description |
|---------|---------------|-------------|
| `neqsim.util` | [util/](util/README) | Database access, unit conversion, serialization, exceptions |
| `neqsim.mathlib` | [mathlib/](mathlib/README) | Mathematical utilities, nonlinear solvers |

---

## Documentation Structure

```
docs/
├── README.md                      # This file - main index
├── modules.md                     # Module overview
│
├── thermo/                        # Thermodynamic package
│   ├── README.md                  # Package overview
│   ├── system/                    # EoS implementations
│   ├── phase/                     # Phase modeling
│   ├── component/                 # Component properties
│   ├── mixingrule/                # Mixing rules
│   └── characterization/          # Plus fraction handling
│
├── thermodynamicoperations/       # Flash operations
│   └── README.md
│
├── physical_properties/           # Transport properties
│   └── README.md
│
├── process/                       # Process simulation
│   ├── README.md                  # Package overview
│   ├── equipment/                 # Equipment documentation
│   ├── processmodel/              # ProcessSystem, modules
│   └── safety/                    # Safety systems
│
├── fluidmechanics/               # Pipe flow
│   └── README.md
│
├── pvtsimulation/                # PVT experiments
│   ├── README.md
│   └── flowassurance/            # Flow assurance (asphaltene, wax, hydrates)
│       ├── README.md
│       ├── asphaltene_modeling.md
│       ├── asphaltene_cpa_calculations.md
│       ├── asphaltene_deboer_screening.md
│       ├── asphaltene_parameter_fitting.md
│       ├── asphaltene_method_comparison.md
│       └── asphaltene_validation.md
│
├── blackoil/                     # Black oil model
│   └── README.md
│
├── chemicalreactions/            # Reactions
│   └── README.md
│
├── standards/                    # Quality standards
│   └── README.md
│
├── statistics/                   # Statistics package
│   └── README.md
│
├── util/                         # Utilities
│   └── README.md
│
├── mathlib/                      # Math utilities
│   └── README.md
│
├── safety/                       # Safety system guides
│   ├── ESD_BLOWDOWN_SYSTEM.md
│   ├── HIPPS_SUMMARY.md
│   ├── hipps_implementation.md
│   ├── sis_logic_implementation.md
│   ├── fire_blowdown_capabilities.md
│   ├── psv_dynamic_sizing_example.md
│   └── alarm_system_guide.md
│
├── simulation/                   # Process simulation guides
│   ├── advanced_process_logic.md
│   ├── graph_based_process_simulation.md
│   ├── parallel_process_simulation.md
│   ├── recycle_acceleration_guide.md
│   ├── well_simulation_guide.md
│   └── turboexpander_compressor_model.md
│
├── integration/                  # Integration guides
│   ├── ai_platform_integration.md
│   ├── ml_integration.md
│   ├── mpc_integration.md
│   ├── REAL_TIME_INTEGRATION_GUIDE.md
│   └── dexpi-reader.md
│
├── development/                  # Developer guides
│   ├── DEVELOPER_SETUP.md
│   └── contributing-structure.md
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
| [ESD_BLOWDOWN_SYSTEM.md](safety/ESD_BLOWDOWN_SYSTEM) | Emergency shutdown and blowdown systems |
| [HIPPS_SUMMARY.md](safety/HIPPS_SUMMARY) | High Integrity Pressure Protection Systems |
| [hipps_implementation.md](safety/hipps_implementation) | HIPPS implementation details |
| [hipps_safety_logic.md](safety/hipps_safety_logic) | HIPPS safety logic |
| [INTEGRATED_SAFETY_SYSTEMS.md](safety/INTEGRATED_SAFETY_SYSTEMS) | Integrated safety systems overview |
| [layered_safety_architecture.md](safety/layered_safety_architecture) | Layered safety architecture |
| [sis_logic_implementation.md](safety/sis_logic_implementation) | SIS logic implementation |
| [SAFETY_SIMULATION_ROADMAP.md](safety/SAFETY_SIMULATION_ROADMAP) | Safety simulation roadmap |

### Process Logic and Control

| Guide | Description |
|-------|-------------|
| [process_logic_framework.md](simulation/process_logic_framework) | Process logic framework |
| [ProcessLogicEnhancements.md](simulation/ProcessLogicEnhancements) | Logic enhancements |
| [advanced_process_logic.md](simulation/advanced_process_logic) | Advanced process logic |
| [alarm_system_guide.md](safety/alarm_system_guide) | Alarm system guide |
| [alarm_triggered_logic_example.md](safety/alarm_triggered_logic_example) | Alarm-triggered logic |
| [mpc_integration.md](integration/mpc_integration) | MPC integration |

### Dynamic Simulation

| Guide | Description |
|-------|-------------|
| [fire_blowdown_capabilities.md](safety/fire_blowdown_capabilities) | Fire and blowdown simulation |
| [fire_heat_transfer_enhancements.md](safety/fire_heat_transfer_enhancements) | Fire heat transfer |
| [psv_dynamic_sizing_example.md](safety/psv_dynamic_sizing_example) | PSV dynamic sizing |
| [rupture_disk_dynamic_behavior.md](safety/rupture_disk_dynamic_behavior) | Rupture disk behavior |
| [turboexpander_compressor_model.md](simulation/turboexpander_compressor_model) | Turboexpander modeling |

### Well and Reservoir

| Guide | Description |
|-------|-------------|
| [well_simulation_guide.md](simulation/well_simulation_guide) | Well simulation guide |
| [well_and_choke_simulation.md](simulation/well_and_choke_simulation) | Choke simulation |
| [field_development_engine.md](simulation/field_development_engine) | Field development |

### PVT and Characterization

| Guide | Description |
|-------|-------------|
| [pvt_workflow.md](pvtsimulation/pvt_workflow) | PVT workflow |
| [blackoil_pvt_export.md](pvtsimulation/blackoil_pvt_export) | Black oil PVT export |
| [whitson_pvt_reader.md](pvtsimulation/whitson_pvt_reader) | Whitson PVT reader |
| [fluid_characterization_mathematics.md](pvtsimulation/fluid_characterization_mathematics) | Characterization math |

### Advanced Features

| Guide | Description |
|-------|-------------|
| [parallel_process_simulation.md](simulation/parallel_process_simulation) | Parallel simulation |
| [recycle_acceleration_guide.md](simulation/recycle_acceleration_guide) | Recycle convergence |
| [graph_based_process_simulation.md](simulation/graph_based_process_simulation) | Graph-based simulation |
| [differentiable_thermodynamics.md](simulation/differentiable_thermodynamics) | Auto-differentiation |
| [equipment_factory.md](simulation/equipment_factory) | Equipment factory |
| [dexpi-reader.md](integration/dexpi-reader) | DEXPI P&ID reader |

### Integration

| Guide | Description |
|-------|-------------|
| [ai_platform_integration.md](integration/ai_platform_integration) | AI/ML integration |
| [ml_integration.md](integration/ml_integration) | Machine learning |
| [REAL_TIME_INTEGRATION_GUIDE.md](integration/REAL_TIME_INTEGRATION_GUIDE) | Real-time systems |
| [QRA_INTEGRATION_GUIDE.md](integration/QRA_INTEGRATION_GUIDE) | QRA integration |

### Development

| Guide | Description |
|-------|-------------|
| [DEVELOPER_SETUP.md](development/DEVELOPER_SETUP) | Development environment setup |
| [contributing-structure.md](development/contributing-structure) | Contributing guidelines |

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
- [Example Notebooks](examples/index)

