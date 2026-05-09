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

| Toolbox          | Repository                                                          | Description                    |
| ---------------- | ------------------------------------------------------------------- | ------------------------------ |
| NeqSim Java      | [equinor/neqsim](https://github.com/equinor/neqsim)                 | Core Java library              |
| NeqSim Python    | [equinor/neqsimpython](https://github.com/equinor/neqsimpython)     | Python bindings                |
| NeqSim MATLAB    | [equinor/neqsimmatlab](https://github.com/equinor/neqsimmatlab)     | MATLAB toolbox                 |
| NeqSim .NET      | [equinor/neqsimNET](https://github.com/equinor/neqsimNET)           | .NET/C# integration            |
| NeqSim Cape-Open | [equinor/neqsimcapeopen](https://github.com/equinor/neqsimcapeopen) | Cape-Open/Excel interface      |
| NeqSim Native    | [equinor/neqsim-native](https://github.com/equinor/neqsim-native)   | Native compilation via GraalVM |

### Web Applications & APIs

| Application    | Link                                                      | Description                            |
| -------------- | --------------------------------------------------------- | -------------------------------------- |
| NeqSim Web App | [neqsim.streamlit.app](https://neqsim.streamlit.app/)     | Browser-based calculations             |
| NeqSim Colab   | [NeqSim-Colab](https://github.com/EvenSol/NeqSim-Colab)   | Jupyter/Colab notebook examples        |
| NeqSimLive API | [NeqSimAPI](https://github.com/equinor/NeqSimAPI)         | Container-based APIs for digital twins |
| Process Models | [neqsimprocess](https://github.com/equinor/neqsimprocess) | Pre-built process model library        |

### AI & Optimization

| Project   | Repository                                                                      | Description                                                 |
| --------- | ------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| RL Agents | [NeqSim-Process-RL-Agents](https://github.com/equinor/NeqSim-Process-RL-Agents) | Multi-agent reinforcement learning for process optimization |

### Support & Community

- **Discussions:** [GitHub Discussions](https://github.com/equinor/neqsim/discussions)
- **Academic Support:** [NTNU Energy and Process Engineering](https://www.ntnu.edu/employees/even.solbraa)
- **Benchmark:** [Performance benchmarks](https://equinor.github.io/neqsimhome/benchmark.html)

---

## Part I: Getting Started

### Chapter 1: Introduction

| Document              | Path                                                                                       | Description                                                             |
| --------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------- |
| Overview              | [docs/README.md](README)                                                                   | Package overview and quick start                                        |
| Modules               | [docs/modules.md](modules)                                                                 | Architecture and module structure                                       |

### Chapter 2: Quickstart Guides (NEW!)

| Document           | Path                                                                 | Description                                         |
| ------------------ | -------------------------------------------------------------------- | --------------------------------------------------- |
| **Quickstart Hub** | [docs/quickstart/index.md](quickstart/index)                         | **Get running in 5 minutes** - choose your platform |
| Java Quickstart    | [docs/quickstart/java-quickstart.md](quickstart/java-quickstart)     | Maven setup, first flash, first process             |
| Python Quickstart  | [docs/quickstart/python-quickstart.md](quickstart/python-quickstart) | pip install, jneqsim imports, gotchas               |
| Colab Quickstart   | [docs/quickstart/colab-quickstart.md](quickstart/colab-quickstart)   | One-click notebooks, no setup required              |

### Chapter 3: Tutorials & Learning Paths (NEW!)

| Document            | Path                                                                             | Description                                           |
| ------------------- | -------------------------------------------------------------------------------- | ----------------------------------------------------- |
| **Tutorials Hub**   | [docs/tutorials/index.md](tutorials/index)                                       | All tutorials organized by topic                      |
| Learning Paths      | [docs/tutorials/learning-paths.md](tutorials/learning-paths)                     | PVT Engineer, Process Engineer, Developer tracks      |
| **TEG Dehydration** | [docs/tutorials/teg_dehydration_tutorial.md](tutorials/teg_dehydration_tutorial) | **Complete TEG gas dehydration plant modeling**       |
| **Solve Engineering Task** | [docs/tutorials/solve-engineering-task.md](tutorials/solve-engineering-task) | **Complete hands-on guide to the 3-step AI task-solving workflow** |
| **GOSP Tutorial**   | [docs/tutorials/gosp_tutorial.md](tutorials/gosp_tutorial)                       | **Gas-oil separation plant (multi-stage separation)** |
| **PVT Lab Tests**   | [docs/pvtsimulation/pvt_lab_tests.md](pvtsimulation/pvt_lab_tests)               | **CCE, CVD, DL, separator test simulations**          |

### Chapter 4: Installation & Setup

| Document        | Path                                                                                               | Description                   |
| --------------- | -------------------------------------------------------------------------------------------------- | ----------------------------- |
| Getting Started | [docs/wiki/getting_started.md](wiki/getting_started)                                               | Installation and first steps  |
| GitHub Setup    | [docs/wiki/Getting-started-with-NeqSim-and-Github.md](wiki/Getting-started-with-NeqSim-and-Github) | NeqSim and GitHub setup       |
| Developer Setup | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP)                                 | Development environment setup |
| Productization Roadmap | [docs/development/PRODUCTIZATION_ROADMAP.md](development/PRODUCTIZATION_ROADMAP)              | Adoption, trust, contributor scaling plan |
| Image Tools, Agents, and Skills | [docs/development/image_tools_agents_skills.md](development/image_tools_agents_skills)        | Engineering-image workflow for P&IDs, drawings, scanned PDFs, maps, screenshots, and related agents/skills |
| Benchmark Gallery | [docs/benchmarks/index.md](benchmarks/index)                                                    | Validation against NIST and published data |
| Consolidated Benchmarks | [docs/benchmarks/consolidated_benchmarks.md](benchmarks/consolidated_benchmarks)          | All benchmark results from task-solving studies |

### Chapter 5: Quick Start Examples

| Document       | Path                                               | Description                |
| -------------- | -------------------------------------------------- | -------------------------- |
| Usage Examples | [docs/wiki/usage_examples.md](wiki/usage_examples) | Basic usage patterns       |
| FAQ            | [docs/wiki/faq.md](wiki/faq)                       | Frequently asked questions |
| Wiki Index     | [docs/wiki/index.md](wiki/index)                   | Wiki documentation index   |

### Chapter 6: Cookbook & Troubleshooting (NEW!)

| Document               | Path                                                                         | Description                                   |
| ---------------------- | ---------------------------------------------------------------------------- | --------------------------------------------- |
| **Cookbook Index**     | [docs/cookbook/index.md](cookbook/index)                                     | **Quick copy-paste recipes for common tasks** |
| Thermodynamics Recipes | [docs/cookbook/thermodynamics-recipes.md](cookbook/thermodynamics-recipes)   | Fluids, flash, properties, phase envelopes    |
| Process Recipes        | [docs/cookbook/process-recipes.md](cookbook/process-recipes)                 | Separators, compressors, heat exchangers      |
| Pipeline Recipes       | [docs/cookbook/pipeline-recipes.md](cookbook/pipeline-recipes)               | Pressure drop, multiphase flow                |
| Unit Conversion        | [docs/cookbook/unit-conversion-recipes.md](cookbook/unit-conversion-recipes) | All supported unit strings                    |
| **Troubleshooting**    | [docs/troubleshooting/index.md](troubleshooting/index)                       | **Solutions to common problems**              |

### External Getting Started Guides

| Platform     | Guide                                                                                                                                  |
| ------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| Java         | [Getting started in Java](https://github.com/equinor/neqsim/wiki/Getting-started-with-NeqSim-and-Github)                               |
| Python       | [Getting started in Python](https://github.com/equinor/neqsimpython/wiki/Getting-started-with-NeqSim-in-Python)                        |
| MATLAB       | [Getting started in MATLAB](https://github.com/equinor/neqsimmatlab/wiki/Getting-started-with-NeqSim-in-Matlab)                        |
| Excel        | [Getting started in Excel](https://github.com/equinor/neqsim.NET/wiki/Getting-started-with-NeqSim-in-Excel)                            |
| Google Colab | [Demo notebook](https://colab.research.google.com/github/EvenSol/NeqSim-Colab/blob/master/notebooks/examples_of_NeqSim_in_Colab.ipynb) |

---

## Part II: Thermodynamics

### Chapter 4: Fundamentals

| Document             | Path                                                           | Description                        |
| -------------------- | -------------------------------------------------------------- | ---------------------------------- |
| Thermo Overview      | [docs/thermo/README.md](thermo/)                               | Thermodynamics module overview     |
| Thermodynamics Guide | [docs/wiki/thermodynamics_guide.md](wiki/thermodynamics_guide) | Comprehensive thermodynamics guide |
| System Types         | [docs/thermo/system/README.md](thermo/system/)                 | EoS system implementations         |

### Chapter 5: Fluid Creation & Components

| Document                 | Path                                                                       | Description                                                                                |
| ------------------------ | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Fluid Creation Guide     | [docs/thermo/fluid_creation_guide.md](thermo/fluid_creation_guide)         | Creating thermodynamic systems                                                             |
| **Fluid Classification** | [docs/thermo/fluid_classification.md](thermo/fluid_classification)         | **Whitson methodology: FluidClassifier, ReservoirFluidType, GOR/C7+ classification**       |
| **Component List**       | [docs/thermo/component_list.md](thermo/component_list)                     | **Complete searchable list of all ~150+ components with CAS numbers and EoS availability** |
| Component Database       | [docs/thermo/component_database_guide.md](thermo/component_database_guide) | Component properties and database                                                          |
| Component Package        | [docs/thermo/component/README.md](thermo/component/)                       | Component class documentation                                                              |
| Mathematical Models      | [docs/thermo/mathematical_models.md](thermo/mathematical_models)           | Underlying mathematical models                                                             |

### Chapter 6: Equations of State

| Document                  | Path                                                               | Description                                                                                                          |
| ------------------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| Thermodynamic Models      | [docs/thermo/thermodynamic_models.md](thermo/thermodynamic_models) | **Comprehensive guide** to all thermodynamic models (EoS, CPA, GERG, electrolytes, GE models) with theory and usage  |
| **Søreide-Whitson Model** | [docs/thermo/SoreideWhitsonModel.md](thermo/SoreideWhitsonModel)   | **Gas solubility in brine** - Modified PR EoS with salinity effects, used in NeqSimLive for produced water emissions |
| GERG-2008                 | [docs/thermo/gerg2008_eoscg.md](thermo/gerg2008_eoscg)             | GERG-2008, GERG-2008-H2, GERG-2008-NH3, and EOS-CG equations of state                                               |
| GERG-2008-NH3 Notebook    | [docs/examples/GERG2008_NH3_Ammonia_Properties.ipynb](examples/GERG2008_NH3_Ammonia_Properties.ipynb) | Ammonia properties with Gao EOS — density validation, mixture properties, isotherms |
| Mixing Rules              | [docs/thermo/mixing_rules_guide.md](thermo/mixing_rules_guide)     | Mixing rules and BIPs                                                                                                |
| Mixing Rule Package       | [docs/thermo/mixingrule/README.md](thermo/mixingrule/)             | Mixing rule implementations                                                                                          |
| Phase Package             | [docs/thermo/phase/README.md](thermo/phase/)                       | Phase modeling                                                                                                       |
| Electrolyte CPA           | [docs/thermo/ElectrolyteCPAModel.md](thermo/ElectrolyteCPAModel)   | Electrolyte CPA model                                                                                                |

### Chapter 7: Flash Calculations

| Document            | Path                                                                                           | Description                     |
| ------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------- |
| Flash Guide         | [docs/thermo/flash_calculations_guide.md](thermo/flash_calculations_guide)                     | Flash calculation methods       |
| Flash Equations     | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests)                       | Flash equations and testing     |
| Thermo Operations   | [docs/thermo/thermodynamic_operations.md](thermo/thermodynamic_operations)                     | Thermodynamic operations        |
| TP Flash Algorithm  | [docs/thermodynamicoperations/TPflash_algorithm.md](thermodynamicoperations/TPflash_algorithm) | TP flash algorithm details      |
| Reactive Flash      | [docs/thermo/reactive_flash.md](thermo/reactive_flash)                                        | Simultaneous chemical and phase equilibrium (Modified RAND method) |
| Reactive PH Flash   | [examples/notebooks/reactive_ph_flash_examples.ipynb](../examples/notebooks/reactive_ph_flash_examples.ipynb) | Isenthalpic/isentropic reactive flash examples (PH, PS flash) |
| Reactive Distillation | [docs/process/reactive_distillation.md](process/reactive_distillation)                        | Reactive distillation column with equilibrium-based reactive flash on each tray |
| Phase Envelope Algorithm | [docs/thermodynamicoperations/phase_envelope_algorithm.md](thermodynamicoperations/phase_envelope_algorithm) | Michelsen continuation method, cricondenbar/cricondentherm Newton refinement, critical point detection |
| Thermo Ops Overview | [docs/thermodynamicoperations/README.md](thermodynamicoperations/)                             | Thermodynamic operations module |

### Chapter 8: Fluid Characterization

Fluid characterization handles plus fraction splitting, property estimation, and lumping. **New:** A fluent API (`configureLumping()`) makes lumping configuration clearer - see the mathematics document for details.

| Document                 | Path                                                                                                                         | Description                                         |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Characterization         | [docs/wiki/fluid_characterization.md](wiki/fluid_characterization)                                                           | Fluid characterization guide with lumping API       |
| TBP Fractions            | [docs/wiki/tbp_fraction_models.md](wiki/tbp_fraction_models)                                                                 | TBP fraction modeling                               |
| PVT Characterization     | [docs/thermo/pvt_fluid_characterization.md](thermo/pvt_fluid_characterization)                                               | PVT fluid characterization with lumping             |
| Characterization Package | [docs/thermo/characterization/README.md](thermo/characterization/)                                                           | Characterization module                             |
| Combining Methods        | [docs/thermo/characterization/fluid_characterization_combining.md](thermo/characterization/fluid_characterization_combining) | Fluid combining methods                             |
| Char Mathematics         | [docs/pvtsimulation/fluid_characterization_mathematics.md](pvtsimulation/fluid_characterization_mathematics)                 | Characterization mathematics with lumping equations |

### Chapter 9: Physical Properties

| Document                        | Path                                                                                                       | Description                                                                                       |
| ------------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| **Reading Fluid Properties**    | [docs/thermo/reading_fluid_properties.md](thermo/reading_fluid_properties)                                 | **Comprehensive guide to calculating and reading properties from fluids, phases, and components** |
| Properties Overview             | [docs/thermo/physical_properties.md](thermo/physical_properties)                                           | Physical property calculations                                                                    |
| Physical Props Module           | [docs/physical_properties/README.md](physical_properties/)                                                 | Physical properties module                                                                        |
| **H2S Distribution (Quick)**    | [docs/thermo/H2S_distribution_guide.md](thermo/H2S_distribution_guide)                                     | **H2S phase distribution with Python examples - quick reference**                                 |
| **H2S Distribution (Detailed)** | [docs/thermo/H2S_DISTRIBUTION_MODELING.md](thermo/H2S_DISTRIBUTION_MODELING)                               | **Comprehensive H2S modeling with Java examples and theory**                                      |
| Viscosity Models                | [docs/wiki/viscosity_models.md](wiki/viscosity_models)                                                     | Viscosity calculation models                                                                      |
| Viscosity Detailed              | [docs/physical_properties/viscosity_models.md](physical_properties/viscosity_models)                       | Detailed viscosity models                                                                         |
| **Density Models**              | [docs/physical_properties/density_models.md](physical_properties/density_models)                           | **COSTALD (Hankinson-Thomson), Peneloux volume translation, Rackett — liquid density for mixtures, TBP fractions, aqueous/polar systems** |
| Thermal Conductivity            | [docs/physical_properties/thermal_conductivity_models.md](physical_properties/thermal_conductivity_models) | Thermal conductivity models                                                                       |
| Diffusivity                     | [docs/physical_properties/diffusivity_models.md](physical_properties/diffusivity_models)                   | Gas and liquid diffusivity models (Chapman-Enskog, Wilke-Lee, Fuller, Siddiqi-Lucas, Wilke-Chang, Tyn-Calus, Hayduk-Minhas) with experimental validation |
| Interfacial Props               | [docs/physical_properties/interfacial_properties.md](physical_properties/interfacial_properties)           | Interfacial tension, etc.                                                                         |
| Scale Potential                 | [docs/physical_properties/scale_potential.md](physical_properties/scale_potential)                         | Scale potential calculations                                                                      |
| **Adsorption Isotherm Models**  | [docs/thermo/adsorption_isotherms.md](thermo/adsorption_isotherms)                                         | **Langmuir, BET, Freundlich, Sips, DRA potential theory, capillary condensation**                 |
| Steam Tables                    | [docs/wiki/steam_tables_if97.md](wiki/steam_tables_if97)                                                   | IF97 steam table implementation                                                                   |
| Thermodynamic Workflows         | [docs/thermo/thermodynamic_workflows.md](thermo/thermodynamic_workflows)                                   | Common thermodynamic workflows                                                                    |
| Interaction Tables              | [docs/thermo/inter_table_guide.md](thermo/inter_table_guide)                                               | Binary interaction parameters                                                                     |  |

### Chapter 10: Hydrates & Flow Assurance

| Document                         | Path                                                                                                               | Description                                                       |
| -------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| **Flow Assurance Overview**      | [docs/pvtsimulation/flow_assurance_overview.md](pvtsimulation/flow_assurance_overview)                             | **Integrated guide: hydrates, wax, asphaltenes, scale screening** |
| **Mineral Scale Formation**      | [docs/pvtsimulation/mineral_scale_formation.md](pvtsimulation/mineral_scale_formation)                             | **Carbonate/sulfate scale, seawater mixing, SR calculations**     |
| **Scale Prediction API**         | [docs/pvtsimulation/scale_prediction_api.md](pvtsimulation/scale_prediction_api)                                   | **API reference: empirical vs EOS, solid solution, compatibility** |
| **pH Stabilization & Corrosion** | [docs/pvtsimulation/ph_stabilization_corrosion.md](pvtsimulation/ph_stabilization_corrosion)                       | **Corrosion control, FeCO3 layer, Electrolyte CPA EoS**           |
| **Chemical Compatibility & RCA** | [docs/chemistry/chemical_compatibility_guide.md](chemistry/chemical_compatibility_guide)                           | **Production chemistry: compatibility, scale/CI/acid/scavenger, root cause analysis** |
| Hydrate Models                   | [docs/thermo/hydrate_models.md](thermo/hydrate_models)                                                             | Hydrate equilibrium models                                        |
| Hydrate Flash                    | [docs/thermodynamicoperations/hydrate_flash_operations.md](thermodynamicoperations/hydrate_flash_operations)       | Hydrate flash operations                                          |
| Wax Characterization             | [docs/thermo/characterization/wax_characterization.md](thermo/characterization/wax_characterization)               | Wax modeling, WAT calculation, flow assurance                     |
| Asphaltene Characterization      | [docs/thermo/characterization/asphaltene_characterization.md](thermo/characterization/asphaltene_characterization) | SARA analysis, CII, CPA parameters                                |

---

## Part III: Process Simulation

### Chapter 11: Process Fundamentals

| Document             | Path                                                                                           | Description                               |
| -------------------- | ---------------------------------------------------------------------------------------------- | ----------------------------------------- |
| Process Overview     | [docs/process/README.md](process/)                                                             | Process simulation module                 |
| Process Guide        | [docs/wiki/process_simulation.md](wiki/process_simulation)                                     | Process simulation guide                  |
| Advanced Process     | [docs/wiki/advanced_process_simulation.md](wiki/advanced_process_simulation)                   | Advanced techniques                       |
| Logical Operations   | [docs/wiki/logical_unit_operations.md](wiki/logical_unit_operations)                           | Logical unit operations                   |
| Process Design       | [docs/process/process_design_guide.md](process/process_design_guide)                           | Process design guide                      |
| Design Framework     | [docs/process/DESIGN_FRAMEWORK.md](process/DESIGN_FRAMEWORK)                                   | Automated design & optimization framework |
| Pipeline Network Optimization | [docs/process/pipeline_network_optimization.md](process/pipeline_network_optimization) | NLP optimizer, sparse solver, Pareto, benchmarks |

### Chapter 12: Process Systems & Models

| Document              | Path                                                                                                                       | Description               |
| --------------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| ProcessModel Overview | [docs/process/processmodel/README.md](process/processmodel/)                                                               | Process system management |
| ProcessSystem         | [docs/process/processmodel/process_system.md](process/processmodel/process_system)                                         | ProcessSystem class (connections, stream introspection, named controllers, unified elements) |
| ProcessModel          | [docs/process/processmodel/process_model.md](process/processmodel/process_model)                                           | Multi-process models      |
| ProcessModule         | [docs/process/processmodel/process_module.md](process/processmodel/process_module)                                         | Modular process units     |
| Process JSON Export   | [docs/process/process_json_export_and_e300_fluids.md](process/process_json_export_and_e300_fluids)                         | Export ProcessSystem and ProcessModel JSON for MCP, including E300-equivalent component properties, volume correction, stream-specific fluids, recycle settings, and convergence checks |
| Graph Simulation      | [docs/process/processmodel/graph_simulation.md](process/processmodel/graph_simulation)                                     | Graph-based simulation    |
| Diagram Export        | [docs/process/processmodel/diagram_export.md](process/processmodel/diagram_export)                                         | PFD diagram export        |
| DEXPI Architecture    | [docs/process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY.md](process/processmodel/DIAGRAM_ARCHITECTURE_DEXPI_SYNERGY) | DEXPI integration         |
| Simulation Hooks      | [docs/process/simulation-hooks-and-events.md](process/simulation-hooks-and-events)                                         | Lifecycle hooks, ProcessEventBus, auto-validation for ProcessSystem and ProcessModel |
| **UniSim/HYSYS Conversion** | [docs/process/unisim-to-neqsim-conversion.md](process/unisim-to-neqsim-conversion)                                  | **Convert UniSim Design (.usc) models to NeqSim and export NeqSim back to UniSim — COM automation, mapping tables, topology reconstruction, verification** |
| **Exergy Analysis**   | [docs/process/exergy-analysis.md](process/exergy-analysis)                                                                 | **Plant-wide exergy destruction hotspots — ProcessSystem, ProcessModel, ExergyAnalysisReport API, JSON export** |

### Chapter 13: Streams & Mixers

| Document           | Path                                                                             | Description               |
| ------------------ | -------------------------------------------------------------------------------- | ------------------------- |
| Streams            | [docs/process/equipment/streams.md](process/equipment/streams)                   | Stream models             |
| Mixers/Splitters   | [docs/process/equipment/mixers_splitters.md](process/equipment/mixers_splitters) | Mixer and splitter models |
| Equipment Overview | [docs/process/equipment/README.md](process/equipment/)                           | Equipment module overview |

### Chapter 13b: Bio-Processing Unit Operations

| Document             | Path                                                   | Description                                                                                                                                                                                                 |
| -------------------- | ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Bio-Processing Guide | [docs/process/bioprocessing.md](process/bioprocessing) | **Reactors, fermenters, enzyme treatment, solid-liquid separators, liquid-liquid extraction, multi-effect evaporators, dryers, crystallizers — mathematical models, design equations, simulation examples** |

### Chapter 14: Separation Equipment

| Document           | Path                                                                           | Description                                                                                                                                                                                                        |
| ------------------ | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Separators         | [docs/process/equipment/separators.md](process/equipment/separators)           | Two/three-phase separators, scrubbers, **entrainment specification (setEntrainment)**, design parameters, performance constraints (K-value, droplet cut size, retention time), Equinor TR3500 & API 12J compliance, **entrainment performance in mechanical design JSON**, **GasScrubberMechanicalDesign internals API** (cyclones, mesh pads, vane packs, drain pipes, level alarms), **conformity checking** (`checkConformity()` with TR3500 rule set) |
| Separator Demisting Internals | (Java source) | **Demisting internal classes** (`process.mechanicaldesign.separator.internals`) — `DemistingInternal` (wire mesh, vane pack, cyclone; Souders-Brown velocity, Eu-number pressure drop, carry-over model), `DemistingInternalWithDrainage` (adds drainage efficiency). Used by `SeparatorMechanicalDesign` for mist eliminator sizing. |
| Separator Primary Separation | (Java source) | **Primary separation / inlet device classes** (`process.mechanicaldesign.separator.primaryseparation`) — `PrimarySeparation` (inlet momentum, bulk efficiency), `InletVane` (6000 Pa, 85%), `InletVaneWithMeshpad` (92%+mesh), `InletCyclones` (8000 Pa, 95%). Used by `SeparatorMechanicalDesign` bridge methods. |
| Gas Scrubber Internals API | (Java source) | **`GasScrubberMechanicalDesign`** — ~40 methods for configuring scrubber internals: `setInletDevice(String)`, `setDemistingCyclones(n, diam, deckElev, length)`, `setMeshPad(area, thickness)`, `setVanePack(area)`, drain pipes, level alarm elevations. Geometry owned by `SeparatorMechanicalDesign`; `Separator` delegates via computed getters. |
| Scrubber Conformity Checking | (Java source) | **Conformity checking package** (`process.mechanicaldesign.separator.conformity`) — `ConformityResult` (PASS/WARNING/FAIL, 90% threshold), `ConformityReport` (collection with `isConforming()`, `toTextReport()`), `ConformityRuleSet` (abstract base + `TR3500RuleSet`: K-factor ≤0.15, inlet momentum ≤15000 Pa, drainage head, cyclone-dp-to-drain ≤50 mbar, mesh-K ≤0.27). |
| Separator Entrainment Modeling | [docs/process/equipment/separator-entrainment-modeling.md](process/equipment/separator-entrainment-modeling) | **Enhanced physics-based separator performance** — droplet size distributions, flow regime prediction (Mandhane, Taitel-Dukler), inlet device modeling (7 types, Bothamley momentum), grade efficiency curves, Schiller-Naumann settling, **expanded internals database (70+ internals, 31 inlet devices, 25 vendor curves)**, K-factor flooding, three-phase liquid-liquid separation, **Csanady turbulence correction**, **API 12J compliance check**, **partial flooding model** (Fabian/GPSA), **auto liquid-liquid DSD** (Hinze/ε inlet dissipation), EOS-derived oil-water volume fractions, **vendor-certified efficiency curves** (from factory acceptance tests), **calibration framework** (manual, auto, grouped, CSV batch fitting, JSON reports), **mechanical design integration** (entrainment fractions, efficiency, calibration factors in `calcDesign()`/`toJson()`), **dynamic simulation integration** (transient entrainment via `runTransient()`, VU flash → performance calculator → outlet composition, vessel inventory preserved, two-phase and three-phase separators) |
| Separator Entrainment Notebook | [examples/notebooks/separator_entrainment_modeling.ipynb](../examples/notebooks/separator_entrainment_modeling.ipynb) | **Jupyter notebook: separator entrainment modeling** — physics-based entrainment, droplet distributions, grade efficiency, internals selection, K-factor analysis, flow regime maps |
| Separator Vendor Curves & Calibration Notebook | [examples/notebooks/separator_vendor_curves_and_calibration.ipynb](../examples/notebooks/separator_vendor_curves_and_calibration.ipynb) | **Jupyter notebook: vendor curves & calibration** — expanded internals database (70+ records), 25 vendor-certified efficiency curves, vendor vs generic comparison, calibration framework (manual, auto, grouped, batch), JSON calibration reports, full database catalog export |
| Separator Dynamic Entrainment Notebook | [examples/notebooks/separator_dynamic_entrainment.ipynb](../examples/notebooks/separator_dynamic_entrainment.ipynb) | **Jupyter notebook: dynamic separator entrainment** — transient simulation with enhanced entrainment, entrainment tracking over time, feed rate disturbance response, with/without entrainment comparison, three-phase separator dynamic, matplotlib visualizations |
| Distillation       | [docs/process/equipment/distillation.md](process/equipment/distillation)       | Distillation columns, **ColumnSpecification (product purity, reflux ratio, component recovery, flow rate, duty)**, Builder pattern, solver options, MESH residual diagnostics                                      |
| Distillation Wiki  | [docs/wiki/distillation_column.md](wiki/distillation_column)                   | Distillation column equations, solver details, convergence diagnostics, and MESH residual monitoring                                                                                                                |
| Absorbers          | [docs/process/equipment/absorbers.md](process/equipment/absorbers)             | Absorption equipment: **SimpleTEGAbsorber (Fs-factor sizing, dew point validation), SimpleAmineAbsorber (MDEA/DEA/MEA gas sweetening, design validation), RateBasedAbsorber (Onda/Billet-Schultes mass transfer, Hatta enhancement, packed column design)** |
| **H2S Scavenger**  | [docs/process/H2S_scavenger_guide.md](process/H2S_scavenger_guide)             | **Chemical scavenging of H2S from gas - triazine, glyoxal, iron sponge, caustic, liquid redox**                                                                                                                    |
| Membrane           | [docs/wiki/membrane_separation.md](wiki/membrane_separation)                   | Membrane separation                                                                                                                                                                                                |
| Membrane Equipment | [docs/process/equipment/membranes.md](process/equipment/membranes)             | Membrane equipment                                                                                                                                                                                                 |
| Filters            | [docs/process/equipment/filters.md](process/equipment/filters)                 | Filter equipment                                                                                                                                                                                                   |
| Water Treatment    | [docs/process/equipment/water_treatment.md](process/equipment/water_treatment) | **Hydrocyclones (physics-based d50, DSD integration, PDR model, liner sizing, OSPAR compliance, ASME VIII mechanical design), GasFlotationUnit (IGF/DGF, per-stage efficiency, reject flow)**, produced water treatment trains, OIW limits |

### Chapter 15: Rotating Equipment

| Document          | Path                                                                                           | Description                                       |
| ----------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| Compressors       | [docs/process/equipment/compressors.md](process/equipment/compressors)                         | Compressor models, drivers, speed-dependent power |
| Compressor Curves | [docs/process/equipment/compressor_curves.md](process/equipment/compressor_curves)             | Compressor performance curves                     |
| Compressor Design | [docs/process/CompressorMechanicalDesign.md](process/CompressorMechanicalDesign)               | Compressor mechanical design                      |
| Compressor Feasibility | [docs/process/compressor_design_feasibility.md](process/compressor_design_feasibility) | Feasibility report: mechanical, cost, suppliers, curves |
| Pumps             | [docs/process/equipment/pumps.md](process/equipment/pumps)                                     | Pump models                                       |
| Pump Guide        | [docs/wiki/pump_usage_guide.md](wiki/pump_usage_guide)                                         | Pump usage guide                                  |
| Pump Theory       | [docs/wiki/pump_theory_and_implementation.md](wiki/pump_theory_and_implementation)             | Pump theory                                       |
| Expanders         | [docs/process/equipment/expanders.md](process/equipment/expanders)                             | Expander models                                   |
| Turboexpander     | [docs/simulation/turboexpander_compressor_model.md](simulation/turboexpander_compressor_model) | Turboexpander model                               |
| Ejectors          | [docs/process/equipment/ejectors.md](process/equipment/ejectors)                               | Ejector systems                                   |

### Chapter 16: Heat Transfer Equipment

| Document                        | Path                                                                                                 | Description                                                                                                         |
| ------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Heat Exchangers                 | [docs/process/equipment/heat_exchangers.md](process/equipment/heat_exchangers)                       | Heat exchanger models                                                                                               |
| **Multi-Stream Heat Exchanger** | [docs/process/equipment/multistream_heat_exchanger.md](process/equipment/multistream_heat_exchanger) | **Comprehensive guide: composite curves, pinch analysis, LMTD, 1-3 unknown solvers for LNG/cryogenic applications** |
| **LNG Heat Exchanger (BAHX)**   | [docs/process/equipment/LNGHeatExchanger.md](process/equipment/LNGHeatExchanger)                     | **Brazed aluminium plate-fin: P1-P10 features, exergy, adaptive zones, Manglik-Bergles, transient, core sizing, freeze-out, maldistribution, mechanical design, cost** |
| **LNG Ageing Package**          | [docs/process/lng-ageing.md](process/lng-ageing)                                                     | **LNG weathering, stratification, rollover, BOG handling, methane number, sloshing, multi-tank ship models, ISO 6578** |
| Air Cooler                      | [docs/wiki/air_cooler.md](wiki/air_cooler)                                                           | Air cooler models                                                                                                   |
| **Process Sim Enhancements**    | [docs/process/process-simulation-enhancements.md](process/process-simulation-enhancements)           | **AirCooler thermal design, PackedColumn, column internals sizing, shortcut distillation, PVF flash, stream summary** |
| Water Cooler                    | [docs/wiki/water_cooler.md](wiki/water_cooler)                                                       | Water cooler models                                                                                                 |
| Steam Heater                    | [docs/wiki/steam_heater.md](wiki/steam_heater)                                                       | Steam heater models                                                                                                 |
| **Water Cooler & Reboiler**     | [docs/process/equipment/water_cooler_reboiler.md](process/equipment/water_cooler_reboiler)           | **WaterCooler (IAPWS), ReBoiler for distillation**                                                                  |
| Mechanical Design               | [docs/wiki/heat_exchanger_mechanical_design.md](wiki/heat_exchanger_mechanical_design)               | HX mechanical design                                                                                                |
| **Thermal-Hydraulic Design**    | [docs/process/mechanical_design/thermal_hydraulic_design.md](process/mechanical_design/thermal_hydraulic_design) | **Shell-and-tube thermal design: Gnielinski, Kern, Bell-Delaware, LMTD correction, vibration screening, zone analysis** |
| **Two-Phase Heat Transfer**     | [docs/process/mechanical_design/two_phase_heat_transfer.md](process/mechanical_design/two_phase_heat_transfer) | **Shah condensation, Chen/Gungor-Winterton boiling, Friedel/MSH two-phase pressure drop, Ebert-Panchal fouling, incremental zone analysis, tube inserts** |
| **Heat Integration (Pinch Analysis)** | [docs/process/equipment/heat_integration.md](process/equipment/heat_integration) | **PinchAnalysis class: Linnhoff method, composite curves, grand composite curve, minimum utility targeting, HeatStream model** |

### Chapter 17: Valves & Flow Control

| Document                      | Path                                                                                           | Description                                                                                                                                                            |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Valves                        | [docs/process/equipment/valves.md](process/equipment/valves)                                   | Valve models                                                                                                                                                           |
| **Control Valves**            | [docs/process/equipment/control_valves.md](process/equipment/control_valves)                   | **CheckValve, LevelControlValve, PressureControlValve, ESD/PSD valves**                                                                                                |
| Valve Design                  | [docs/process/ValveMechanicalDesign.md](process/ValveMechanicalDesign)                         | Valve mechanical design                                                                                                                                                |
| **Multiphase Choke Flow**     | [docs/process/MultiphaseChokeFlow.md](process/MultiphaseChokeFlow)                             | **Sachdeva, Gilbert two-phase choke models**                                                                                                                           |
| **Well Choke Implementation** | [docs/process/well_choke_implementation.md](process/well_choke_implementation)                 | **Architecture, models, ThrottlingValve integration, Python usage**                                                                                                    |
| Flow Meters                   | [docs/wiki/flow_meter_models.md](wiki/flow_meter_models)                                       | Flow metering                                                                                                                                                          |
| Venturi                       | [docs/wiki/venturi_calculation.md](wiki/venturi_calculation)                                   | Venturi calculations                                                                                                                                                   |
| Tanks                         | [docs/process/equipment/tanks.md](process/equipment/tanks)                                     | Tank models                                                                                                                                                            |
| **Vessel Depressurization**   | [docs/process/equipment/vessel_depressurization.md](process/equipment/vessel_depressurization) | **Filling, blowdown, fire cases, transient wall HT, composite vessels, CNG/H2 tanks, flow assurance, real-gas beta, Biot correction, Rohsenow boiling, API reference** |
| **Measurement Devices**       | [docs/process/equipment/measurement_devices.md](process/equipment/measurement_devices)         | **CO2 emissions, FIV analysis, NMVOC, dew points, safety detectors**                                                                                                   |

### Chapter 18: Special Equipment

| Document                       | Path                                                                                       | Description                                                                                                        |
| ------------------------------ | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| Reactors                       | [docs/process/equipment/reactors.md](process/equipment/reactors)                           | All reactor types overview (PFR, CSTR, Gibbs, stoichiometric, ammonia, sulfur, bio-processing)                     |
| **Plug Flow Reactor Guide**    | [docs/process/equipment/plug_flow_reactor.md](process/equipment/plug_flow_reactor)         | **Kinetic PFR: power-law/LHHW/reversible kinetics, catalyst bed, Ergun ΔP, energy modes, axial profiles, Python** |
| Gibbs Reactor                  | [docs/wiki/gibbs_reactor.md](wiki/gibbs_reactor)                                           | Gibbs reactor                                                                                                      |
| Electrolyzers                  | [docs/process/equipment/electrolyzers.md](process/equipment/electrolyzers)                 | Electrolyzer systems                                                                                               |
| CO2 Electrolyzer               | [docs/pvtsimulation/CO2ElectrolyzerExample.md](pvtsimulation/CO2ElectrolyzerExample)       | CO2 electrolyzer example                                                                                           |
| Flares                         | [docs/process/equipment/flares.md](process/equipment/flares)                               | Flare systems                                                                                                      |
| Adsorbers (SimpleAdsorber)     | [docs/process/equipment/adsorbers.md](process/equipment/adsorbers)                         | Simplified gas absorption with MDEA                                                                                |
| **Adsorption Bed (Transient)** | [docs/process/equipment/adsorption_bed.md](process/equipment/adsorption_bed)               | **Fixed-bed adsorption with LDF mass transfer, MTZ, PSA/TSA cycles**                                               |
| **Mercury Removal Guard Bed**  | [docs/process/mercury_removal.md](process/mercury_removal)                                 | **Chemisorption (PuraSpec), transient bed loading, breakthrough, degradation, mechanical design, cost estimation** |
| Power Generation               | [docs/process/equipment/power_generation.md](process/equipment/power_generation)           | **GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem, FuelCell, renewables (SolarPanel, WindTurbine, WindFarm). Capacity constraints, auto-sizing, and optimization integration** |
| Diff. Pressure                 | [docs/process/equipment/differential_pressure.md](process/equipment/differential_pressure) | Orifice plates, flow measurement                                                                                   |
| Manifolds                      | [docs/process/equipment/manifolds.md](process/equipment/manifolds)                         | Multi-stream routing                                                                                               |
| **Battery Storage**            | [docs/process/equipment/battery_storage.md](process/equipment/battery_storage)             | **Energy storage systems, charge/discharge cycles, grid integration**                                              |
| Solar Panel                    | [docs/wiki/solar_panel.md](wiki/solar_panel)                                               | Solar panel models                                                                                                 |
| **Failure Mode Modeling**      | [docs/process/equipment/failure_modes.md](process/equipment/failure_modes)                 | **Equipment failure modes, reliability analysis, MTBF/MTTR calculations**                                          |

### Chapter 19: Wells, Pipelines & Subsea

| Document                              | Path                                                                                               | Description                                                                                                                                              |
| ------------------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Wells                                 | [docs/process/equipment/wells.md](process/equipment/wells)                                         | Well equipment                                                                                                                                           |
| Well Simulation                       | [docs/simulation/well_simulation_guide.md](simulation/well_simulation_guide)                       | Well simulation guide                                                                                                                                    |
| Well & Choke                          | [docs/simulation/well_and_choke_simulation.md](simulation/well_and_choke_simulation)               | Choke valve simulation                                                                                                                                   |
| Well Allocation                       | [docs/process/equipment/well_allocation.md](process/equipment/well_allocation)                     | Production allocation for commingled wells, VFM, well test methods                                                                                       |
| Pipelines                             | [docs/process/equipment/pipelines.md](process/equipment/pipelines)                                 | Pipeline and riser models                                                                                                                                |
| **Piping Route Builder**              | [docs/process/piping_route_builder.md](process/piping_route_builder)                               | **STID/E3D line-list route hydraulics: build serial Beggs-and-Brill pipe models from route tables, fittings, valves, elevations, and equipment nodes**    |
| **TwoFluidPipe Model**                | [docs/process/TWOFLUIDPIPE_MODEL.md](process/TWOFLUIDPIPE_MODEL)                                   | **Two-fluid multiphase flow model**                                                                                                                      |
| **TwoFluidPipe OLGA Comparison**      | [docs/wiki/two_fluid_model_olga_comparison.md](wiki/two_fluid_model_olga_comparison)               | **Mathematical equations, slug flow physics, Lagrangian tracking**                                                                                       |
| **TwoFluidPipe Tutorial (Jupyter)**   | [docs/examples/TwoFluidPipe_Tutorial.ipynb](examples/TwoFluidPipe_Tutorial.ipynb)                  | **Interactive notebook: multiphase flow, slug tracking, terrain effects, heat transfer**                                                                 |
| **TwoFluidPipe Comprehensive Tutorial** | [examples/notebooks/TwoFluidPipeMultiphaseFlowTutorial.ipynb](https://github.com/equinor/neqsim/blob/master/examples/notebooks/TwoFluidPipeMultiphaseFlowTutorial.ipynb) | **Complete guide: steady-state, transient, three-phase (gas-oil-water), terrain effects, heat transfer, Beggs-Brill validation, slug tracking** |
| **Risers**                            | [docs/process/equipment/pipelines.md#risers](process/equipment/pipelines#risers)                   | **SCR, TTR, Flexible, Lazy-Wave risers**                                                                                                                 |
| Beggs & Brill                         | [docs/process/PipeBeggsAndBrills.md](process/PipeBeggsAndBrills)                                   | Beggs & Brill correlation                                                                                                                                |
| **Multiphase Flow Correlations**      | [docs/process/equipment/multiphase_flow_correlations.md](process/equipment/multiphase_flow_correlations) | **Hagedorn-Brown (1965), Mukherjee-Brill (1985), comparison with Beggs & Brill — holdup, pressure gradient, flow pattern** |
| **CO2 Injection Well Analysis**       | [docs/process/co2_injection_well_analysis.md](process/co2_injection_well_analysis)                  | **CO2InjectionWellAnalyzer, ImpurityMonitor, TransientWellbore, CO2FlowCorrections — integrated safety analysis for CO2 injection wells**                 |
| Networks                              | [docs/process/equipment/networks.md](process/equipment/networks)                                   | Pipeline network modeling                                                                                                                                |
| **Looped Pipeline Networks**          | [docs/process/equipment/looped_networks.md](process/equipment/looped_networks)                     | **Hardy Cross solver, ring mains, parallel pipelines, loop detection**                                                                                   |
| **Production Well Networks**          | [docs/process/equipment/production_well_networks.md](process/equipment/production_well_networks)   | **IPR (PI, Vogel, Fetkovich), chokes, tubing VLP, multiphase pipe, artificial lift, water handling, sand/solids, corrosion, emissions in NR-GGA solver** |
| **Production Network Tutorial**       | [examples/notebooks/production_well_network.ipynb](../examples/notebooks/production_well_network.ipynb) | **8-example notebook: IPR models, choke sensitivity, complete well system, multi-well gathering, choke allocation optimization**                    |
| **Advanced Network Features**         | [examples/notebooks/production_network_advanced_features.ipynb](../examples/notebooks/production_network_advanced_features.ipynb) | **Artificial lift, 120-well scale, water handling, sand erosion (DNV RP O501), corrosion (NORSOK M-506), GHG emissions** |
| **Looped Network Solver**             | [docs/process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT.md](process/PIPELINE_NETWORK_SOLVER_ENHANCEMENT) | **Hardy Cross looped network solver for ring mains and parallel pipelines**                                                                              |
| **Looped Network Tutorial**           | [docs/examples/LoopedPipelineNetworkExample.ipynb](examples/LoopedPipelineNetworkExample.ipynb)    | **Interactive notebook: ring mains, offshore rings, loop detection, Hardy Cross**                                                                        |
| **Network Solver Tutorial**           | [docs/examples/NetworkSolverTutorial.md](examples/NetworkSolverTutorial)                           | **Tutorial for pipeline network solvers with worked examples**                                                                                           |
| **Pipe Fittings & Equivalent Length** | [docs/process/PIPE_FITTINGS_EQUIVALENT_LENGTH.md](process/PIPE_FITTINGS_EQUIVALENT_LENGTH)         | **Equivalent length method for fittings: elbows, tees, valves, reducers per CRANE TP-410**                                                               |
| Reservoirs                            | [docs/process/equipment/reservoirs.md](process/equipment/reservoirs)                               | Reservoir modeling                                                                                                                                       |
| **Out-of-Zone Injection**             | [docs/process/out_of_zone_injection.md](process/out_of_zone_injection)                             | **Multi-zone injection, fracture containment, annular leakage, MAASP (API RP 90), cement degradation, conformance monitoring**                           |
| **Out-of-Zone Injection Tutorial**    | [examples/notebooks/out_of_zone_injection.ipynb](../examples/notebooks/out_of_zone_injection.ipynb)| **Interactive notebook: WellFlow injection mode, thermal stress, cement CO2 degradation, Hall plot**                                                     |
| Subsea Systems                        | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems)                       | Subsea wells and flowlines                                                                                                                               |
| Subsea Equipment                      | [docs/process/equipment/subsea_equipment.md](process/equipment/subsea_equipment)                   | SubseaWell, SimpleFlowLine, flow assurance                                                                                                               |
| **Subsea Trees**                      | [docs/process/equipment/subsea_trees.md](process/equipment/subsea_trees)                           | **Christmas trees: horizontal/vertical, dual-bore, valve configurations, wellhead integration**                                                          |
| **Subsea Manifolds**                  | [docs/process/equipment/subsea_manifolds.md](process/equipment/subsea_manifolds)                   | **Production/injection manifolds, valve skids, well routing, gathering systems**                                                                         |
| **Subsea Boosters**                   | [docs/process/equipment/subsea_boosters.md](process/equipment/subsea_boosters)                     | **Subsea pumps & compressors, helico-axial/multiphase, performance curves**                                                                              |
| **Umbilicals**                        | [docs/process/equipment/umbilicals.md](process/equipment/umbilicals)                               | **Control umbilicals: hydraulic, electrical, chemical injection lines**                                                                                  |
| **SURF Subsea Equipment**             | [docs/process/SURF_SUBSEA_EQUIPMENT.md](process/SURF_SUBSEA_EQUIPMENT)                             | **Comprehensive SURF equipment: PLET, PLEM, manifolds, trees, jumpers, umbilicals, flexible pipes, boosters with mechanical design and cost estimation** |
| **Well Mechanical Design**            | [docs/process/well_mechanical_design.md](process/well_mechanical_design)                           | **Standards-based well design: barrier elements/envelopes per NORSOK D-010, MAASP per API RP 90, CSV-driven design factors, cost estimation**            |

### Chapter 20: Utility Equipment

| Document                     | Path                                                                                                       | Description                                                                                      |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Utility Overview             | [docs/process/equipment/util/README.md](process/equipment/util/)                                           | Utility equipment                                                                                |
| Adjusters                    | [docs/process/equipment/util/adjusters.md](process/equipment/util/adjusters)                               | **Adjuster (single-variable) and MultiVariableAdjuster (Broyden quasi-Newton for N simultaneous specs)** |
| Recycles                     | [docs/process/equipment/util/recycles.md](process/equipment/util/recycles)                                 | Recycle units                                                                                    |
| Calculators                  | [docs/process/equipment/util/calculators.md](process/equipment/util/calculators)                           | Calculator units                                                                                 |
| **Stream Saturator**         | [docs/process/equipment/util/saturators.md](process/equipment/util/saturators)                             | **Water saturation utility for simulating reservoir conditions and wet gas systems**             |
| **Stream Fitters**           | [docs/process/equipment/util/stream_fitters.md](process/equipment/util/stream_fitters)                     | **GORfitter, MPFMfitter: GOR/GVF adjustment, MPFM reference fluids**                             |
| **Fuel Gas System**          | [docs/process/equipment/util/fuel_gas_system.md](process/equipment/util/fuel_gas_system)                   | **Complete fuel gas conditioning: gas turbines, fired heaters, pilots, Wobbe Index, JT cooling** |
| **Utility Air System**       | [docs/process/equipment/util/utility_air_system.md](process/equipment/util/utility_air_system)             | **ISO 8573-1 utility air: instrument/plant/breathing air, compressor/dryer sizing**              |
| **Produced Water Degassing** | [docs/process/equipment/util/produced_water_degassing.md](process/equipment/util/produced_water_degassing) | **Multi-stage degassing with GHG emissions per Norwegian regulations (Aktivitetsforskriften)**   |

### Chapter 21: Process Control

| Document                 | Path                                                                                       | Description                                      |
| ------------------------ | ------------------------------------------------------------------------------------------ | ------------------------------------------------ |
| Controllers              | [docs/process/controllers.md](process/controllers)                                         | Process controllers                              |
| Process Control          | [docs/wiki/process_control.md](wiki/process_control)                                       | Control systems                                  |
| Dynamic Simulation Guide | [docs/simulation/dynamic_simulation_guide.md](simulation/dynamic_simulation_guide)         | Comprehensive dynamic/transient simulation guide |
| Transient Simulation     | [docs/wiki/process_transient_simulation_guide.md](wiki/process_transient_simulation_guide) | Transient simulation patterns                    |

### Chapter 22: Optimization and Constraints

> **January 2026 Update:** ProductionOptimizer now includes configuration validation, stagnation detection, warm start, bounded LRU cache, and infeasibility diagnostics. See updated tutorials for details.

| Document                             | Path                                                                                                             | Description                                                                                                                               |
| ------------------------------------ | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| **Engineering Utilities Reference**  | [docs/util/engineering_utilities.md](util/engineering_utilities)                                                  | **FluidBuilder, HMB, SensitivityAnalysis, MonteCarloSimulator, ConvergenceDiagnostics, HydrateRiskMapper, EOSComparison**                |
| **Process Engineering Utilities v2** | [docs/process/engineering_utilities_v2.md](process/engineering_utilities_v2)                                     | **PinchAnalyzer, DCFCalculator, DebottleneckAnalyzer, FiredHeater, ProcessValidator, CoolingWaterSystem**                                |
| **Optimization & Constraints Guide** | [docs/process/optimization/OPTIMIZATION_AND_CONSTRAINTS.md](process/optimization/OPTIMIZATION_AND_CONSTRAINTS)   | **COMPREHENSIVE: Complete guide to optimization algorithms, constraint types, bottleneck analysis, and practical examples**               |
| **Optimization Overview**            | [docs/process/optimization/OPTIMIZATION_OVERVIEW.md](process/optimization/OPTIMIZATION_OVERVIEW)                 | **START HERE: Introduction to process optimization, when to use ProcessOptimizationEngine vs ProductionOptimizer**                        |
| **Process Researcher**               | [docs/process/optimization/process-researcher.md](process/optimization/process-researcher)                       | **Generate, simulate, optimize, and rank candidate flowsheets from feed/product targets, including reaction routes**                      |
| **ProductionOptimizer Tutorial**     | [docs/examples/ProductionOptimizer_Tutorial.md](examples/ProductionOptimizer_Tutorial)                           | **Interactive Jupyter notebook with complete ProductionOptimizer guide: algorithms, single/multi-variable, Pareto, constraints**          |
| **Python Optimization Tutorial**     | [docs/examples/NeqSim_Python_Optimization.md](examples/NeqSim_Python_Optimization)                               | **Using SciPy/Python optimizers with NeqSim process simulations: constraints, Pareto, global optimization**                               |
| **Capacity Constraint Framework**    | [docs/process/CAPACITY_CONSTRAINT_FRAMEWORK.md](process/CAPACITY_CONSTRAINT_FRAMEWORK)                           | **Framework for equipment capacity limits, bottleneck detection, utilization tracking, and AIV/FIV vibration analysis**                   |
| **Optimizer Plugin Architecture**    | [docs/process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE.md](process/optimization/OPTIMIZER_PLUGIN_ARCHITECTURE) | **Equipment capacity strategies, throughput optimization, gradient descent, sensitivity analysis, shadow prices, and Eclipse VFP export** |
| **External Optimizer Integration**   | [docs/integration/EXTERNAL_OPTIMIZER_INTEGRATION.md](integration/EXTERNAL_OPTIMIZER_INTEGRATION)                 | **ProcessSimulationEvaluator for Python/SciPy/NLopt/Pyomo integration with gradient estimation**                                          |
| **Capacity Constraints Demo**        | [examples/notebooks/capacity_constraints_optimization_demo.ipynb](../examples/notebooks/capacity_constraints_optimization_demo) | **Interactive notebook: constraint builder API, equipment/system queries, strategy registry, enable/disable, flow sweep, utilization charts** |
| **Web API / JSON Process Builder**   | [docs/integration/web_api_json_process_builder.md](integration/web_api_json_process_builder)                     | **Build and run process simulations from JSON, structured error responses, equipment wiring API, multi-user session management**          |
| **MCP Core Layer**                   | [docs/integration/mcp_neqsim_core_layer.md](integration/mcp_neqsim_core_layer)                                  | **MCP runners (FlashRunner, ProcessRunner, Validator, ComponentQuery), typed models, example/schema catalogs**                            |
| **MCP Getting Started**              | [docs/integration/mcp_getting_started.md](integration/mcp_getting_started)                                       | **5-minute guide: connect any LLM to NeqSim via MCP — setup, first calculation, tool selection, common patterns**                        |
| **MCP Server Guide**                | [docs/integration/mcp_server_guide.md](integration/mcp_server_guide)                                             | **Quarkus MCP Server for VS Code Copilot, Claude Desktop, Cursor — installation, tools, resources, testing**                              |
| **Production Optimization Guide**    | [docs/examples/PRODUCTION_OPTIMIZATION_GUIDE.md](examples/PRODUCTION_OPTIMIZATION_GUIDE)                         | **Complete guide to production optimization with Java and Python examples**                                                               |
| **Pressure Boundary Optimization**   | [docs/process/pressure_boundary_optimization.md](process/pressure_boundary_optimization)                         | **Calculate flow rates for pressure boundaries, generate Eclipse VFP lift curves, optimize compressor power**                             |
| **Flow Rate Optimization**           | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization)               | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation**                                         |
| **Compressor Optimization Guide**    | [docs/process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE.md](process/optimization/COMPRESSOR_OPTIMIZATION_GUIDE) | **Specialized guide for compressor train optimization, anti-surge control, and power minimization**                                       |
| **Practical Examples**               | [docs/process/optimization/PRACTICAL_EXAMPLES.md](process/optimization/PRACTICAL_EXAMPLES)                       | **Working examples for optimization scenarios including gas processing, LNG, and offshore platforms**                                     |
| **SQP Optimizer**                    | [docs/process/optimization/sqp_optimizer.md](process/optimization/sqp_optimizer)                                 | **Sequential Quadratic Programming — constrained NLP with BFGS Hessian, active-set QP, L1 merit function**                              |

### Chapter 23: Mechanical Design

| Document                          | Path                                                                                                                                                   | Description                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------- |
| Mechanical Design                 | [docs/process/mechanical_design.md](process/mechanical_design)                                                                                         | Mechanical design overview, process design parameters, validation, and JSON export                                               |
| **Equipment Design Parameters**   | [docs/process/EQUIPMENT_DESIGN_PARAMETERS.md](process/EQUIPMENT_DESIGN_PARAMETERS)                                                                     | **Comprehensive guide to autoSize vs MechanicalDesign, manual sizing, validation methods, and capacity constraints**             |
| **Process Design Parameters**     | [docs/process/mechanical_design.md#process-design-parameters](process/mechanical_design#process-design-parameters)                                     | **Industry-standard process design parameters for separators, compressors, pumps, heat exchangers**                              |
| **Design Validation**             | [docs/process/mechanical_design.md#design-validation](process/mechanical_design#design-validation)                                                     | **Validation methods per API-610, API-617, TEMA, API-12J standards**                                                             |
| **Mechanical Design Report**      | [docs/process/mechanical_design.md#comprehensive-mechanical-design-report-json](process/mechanical_design#comprehensive-mechanical-design-report-json) | **Combined JSON output for all mechanical design data (equipment + piping)**                                                     |
| **Compressor Casing Design**      | [docs/process/CompressorMechanicalDesign.md#casing-wall-thickness-asme-viii-div-1-ug-27](process/CompressorMechanicalDesign#casing-wall-thickness-asme-viii-div-1-ug-27) | **Compressor casing design per API 617 / ASME VIII: wall thickness, material selection, flange rating, nozzle loads, NACE MR0175, thermal growth, split-line bolts, barrel casing** |
| Design Standards                  | [docs/process/mechanical_design_standards.md](process/mechanical_design_standards)                                                                     | Design standards                                                                                                                 |
| Design Database                   | [docs/process/mechanical_design_database.md](process/mechanical_design_database)                                                                       | Design database                                                                                                                  |
| **Pipeline Mechanical Design**    | [docs/process/pipeline_mechanical_design.md](process/pipeline_mechanical_design)                                                                       | **Comprehensive pipeline mechanical design with wall thickness, stress analysis, cost estimation**                               |
| **Topside Piping Design**         | [docs/process/topside_piping_design.md](process/topside_piping_design)                                                                                 | **Topside piping design with velocity, support spacing, vibration (AIV/FIV), stress analysis per ASME B31.3**                    |
| **Manifold Mechanical Design**    | [docs/process/equipment/manifold_design.md](process/equipment/manifold_design)                                                                         | **Manifold design for topside, onshore, and subsea with velocity limits, reinforcement, support per ASME B31.3 and DNV-ST-F101** |
| **Riser Mechanical Design**       | [docs/process/riser_mechanical_design.md](process/riser_mechanical_design)                                                                             | **Riser design with catenary mechanics, VIV, fatigue per DNV-OS-F201**                                                           |
| **Pipeline Design Math**          | [docs/process/pipeline_mechanical_design_math.md](process/pipeline_mechanical_design_math)                                                             | **Mathematical methods and formulas for pipeline design**                                                                        |
| **Subsea SURF Mechanical Design** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#mechanical-design](process/SURF_SUBSEA_EQUIPMENT#mechanical-design)                                             | **Mechanical design for PLET, PLEM, trees, manifolds, jumpers, umbilicals, flexible pipes, boosters per DNV, API, ISO, NORSOK**  |
| **Well Mechanical Design**        | [docs/process/well_mechanical_design.md](process/well_mechanical_design)                                                                               | **Standards-based well design: barrier elements/envelopes per NORSOK D-010, MAASP per API RP 90, CSV-driven design factors, cost estimation**|
| **Equipment Datasheet Generator** | [docs/process/equipment_datasheets.md](process/equipment_datasheets)                                                                                   | **Structured JSON equipment datasheets from process simulation (separator, compressor, heater, valve)**                          |
| **Dual EoS Comparison**           | [docs/process/dual_eos_comparison.md](process/dual_eos_comparison)                                                                                     | **SRK vs PR78 cross-check per TR1244 for field development QA**                                                                  |
| TORG Integration                  | [docs/process/torg_integration.md](process/torg_integration)                                                                                           | TORG integration                                                                                                                 |
| Field Development                 | [docs/process/field_development_orchestration.md](process/field_development_orchestration)                                                             | Field development orchestration                                                                                                  |

### Chapter 24: Electrical Design

| Document                          | Path                                                                                                       | Description                                                                                                                      |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Electrical Design Guide**       | [docs/process/electrical-design.md](process/electrical-design)                                             | **Comprehensive guide to electrical design: motor sizing (IEC 60034), VFD topology, cable sizing, transformer, switchgear, hazardous area, load list, equipment-specific designs (separator, heater/cooler, pipeline), plant-wide SystemElectricalDesign** |
| **Motor Mechanical Design**       | [docs/process/motor-mechanical-design.md](process/motor-mechanical-design)                                 | **Motor physical/mechanical design: foundation loads (IEEE 841), cooling (IEC 60034-6), bearings (ISO 281), vibration (ISO 10816-3), noise (IEC 60034-9, NORSOK S-002), enclosure (IEC 60034-5, IEC 60079), derating (IEC 60034-1)** |
| **Combined Equipment Design Report** | [docs/process/motor-mechanical-design.md#equipmentdesignreport](process/motor-mechanical-design#equipmentdesignreport) | **EquipmentDesignReport: combined mechanical + electrical + motor design report with feasibility verdict for any equipment** |
| **Compressor Electrical Design**  | [examples/notebooks/electrical/compressor_electrical_design.ipynb](../examples/notebooks/electrical/compressor_electrical_design.ipynb) | **Jupyter notebook: 2-stage compression electrical design with motor curves, power triangle, efficiency chain** |
| **Process Plant Load List**       | [examples/notebooks/electrical/process_plant_load_list.ipynb](../examples/notebooks/electrical/process_plant_load_list.ipynb) | **Jupyter notebook: plant-wide electrical load list, demand/diversity factors, transformer sizing** |
| **Motor & VFD Analysis**          | [examples/notebooks/electrical/motor_vfd_analysis.ipynb](../examples/notebooks/electrical/motor_vfd_analysis.ipynb) | **Jupyter notebook: motor efficiency classes IE1-IE4, VFD topology selection, harmonics, efficiency maps, cable sizing, hazardous area** |
| **Power-from-Shore Feasibility**  | [examples/notebooks/electrical/power_from_shore_feasibility.ipynb](../examples/notebooks/electrical/power_from_shore_feasibility.ipynb) | **Jupyter notebook: submarine cable sizing, HVAC/HVDC, cost estimation, CO₂ comparison, regional analysis (Norway, UK, Brazil, GoM)** |

### Chapter 25: Instrument Design

| Document                          | Path                                                                                                       | Description                                                                                                                      |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Instrument Design Guide**       | [docs/process/instrument-design.md](process/instrument-design)                                             | **Comprehensive guide to instrument design: ISA-5.1 identification, SIL-rated safety instruments, I/O counting, DCS/SIS cabinet sizing, cost estimation, equipment-specific designs (separator, compressor, heat exchanger, pipeline, valve), plant-wide SystemInstrumentDesign** |

### Chapter 26: Dynamic Simulation

| Document                          | Path                                                                                                       | Description                                                                                                                      |
| --------------------------------- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Dynamic Simulation Guide**      | [docs/process/dynamic-simulation.md](process/dynamic-simulation)                                           | **DynamicProcessHelper utility — auto-instruments a sized steady-state process with transmitters and PID controllers for transient simulation, configurable PID tuning, flow and temperature control loops** |
| **Dynamic Simulation Enhancements** | [docs/process/dynamic-simulation-enhancements.md](process/dynamic-simulation-enhancements)               | **Advanced dynamic features: controller modes (AUTO/MANUAL/CASCADE), bumpless transfer, 2-DOF PID, SFC sequencing (IEC 61131-3), override/split-range control, sensor fault injection, transmitter filtering, alarm shelving, valve nonlinearities (deadband/stiction/hysteresis), separator internals (weir, mist eliminator), HX thermal ODE (wall + CSTR holdup), distillation MESH dynamics, adaptive time stepping, parallel transient, RK4 integration** |

### Chapter 23b: Cost Estimation

| Document                        | Path                                                                                                                                         | Description                                                                                                 |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| **Cost Estimation Framework**   | [docs/process/COST_ESTIMATION_FRAMEWORK.md](process/COST_ESTIMATION_FRAMEWORK)                                                               | **Comprehensive capital and operating cost estimation framework**                                           |
| **Cost Estimation API**         | [docs/process/COST_ESTIMATION_API_REFERENCE.md](process/COST_ESTIMATION_API_REFERENCE)                                                       | **Detailed API reference for all cost estimation classes**                                                  |
| **Subsea SURF Cost Estimation** | [docs/process/SURF_SUBSEA_EQUIPMENT.md#cost-estimation](process/SURF_SUBSEA_EQUIPMENT#cost-estimation)                                       | **Cost estimation for all SURF equipment with regional factors, labor rates, vessel costs, BOM generation** |
| Equipment Costs                 | [docs/process/COST_ESTIMATION_FRAMEWORK.md#equipment-cost-estimation](process/COST_ESTIMATION_FRAMEWORK#equipment-cost-estimation)           | Equipment-specific cost correlations                                                                        |
| Tank Costs                      | [docs/process/COST_ESTIMATION_FRAMEWORK.md#tank-cost](process/COST_ESTIMATION_FRAMEWORK#tank-cost)                                           | Storage tank cost estimation (API 650/620)                                                                  |
| Expander Costs                  | [docs/process/COST_ESTIMATION_FRAMEWORK.md#expander-cost](process/COST_ESTIMATION_FRAMEWORK#expander-cost)                                   | Turboexpander cost estimation                                                                               |
| Ejector Costs                   | [docs/process/COST_ESTIMATION_FRAMEWORK.md#ejector-cost](process/COST_ESTIMATION_FRAMEWORK#ejector-cost)                                     | Ejector and vacuum system costs                                                                             |
| Absorber Costs                  | [docs/process/COST_ESTIMATION_FRAMEWORK.md#absorber-cost](process/COST_ESTIMATION_FRAMEWORK#absorber-cost)                                   | Absorption tower cost estimation                                                                            |
| Currency & Location             | [docs/process/COST_ESTIMATION_FRAMEWORK.md#currency-and-location-support](process/COST_ESTIMATION_FRAMEWORK#currency-and-location-support)   | Multi-currency and location factors                                                                         |
| OPEX Estimation                 | [docs/process/COST_ESTIMATION_FRAMEWORK.md#operating-cost-opex-estimation](process/COST_ESTIMATION_FRAMEWORK#operating-cost-opex-estimation) | Operating cost calculation                                                                                  |
| Financial Metrics               | [docs/process/COST_ESTIMATION_FRAMEWORK.md#financial-metrics](process/COST_ESTIMATION_FRAMEWORK#financial-metrics)                           | Payback, ROI, NPV calculations                                                                              |

### Chapter 23c: Corrosion Analysis

| Document                              | Path                                                                                                             | Description                                                                                                     |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Corrosion Module Overview**         | [docs/process/corrosion/index.md](process/corrosion/)                                                           | **Package overview, quick start, standards coverage for NORSOK M-506 and M-001**                                 |
| **Process-Wide Materials Review**     | [docs/process/corrosion/materials_review.md](process/corrosion/materials_review)                                 | **STID-backed material selection, degradation, CUI, remaining-life, and MCP materials review workflow**           |
| **NORSOK M-506 Corrosion Rate**       | [docs/process/corrosion/norsok_m506_corrosion_rate.md](process/corrosion/norsok_m506_corrosion_rate)             | **CO2 corrosion rate prediction — fugacity, pH, correction factors, parameter sweeps, JSON reporting**           |
| **NORSOK M-001 Material Selection**   | [docs/process/corrosion/norsok_m001_material_selection.md](process/corrosion/norsok_m001_material_selection)     | **Material grade recommendation — sweet/sour classification, CRA selection, chloride SCC, corrosion allowance** |
| **Pipeline Corrosion Integration**    | [docs/process/corrosion/pipeline_corrosion_integration.md](process/corrosion/pipeline_corrosion_integration)     | **Automated corrosion analysis from process simulation — stream extraction, combined mechanical + corrosion**    |
| **Sour Service Assessment**           | [docs/process/corrosion/sour_service_assessment.md](process/corrosion/sour_service_assessment)                   | **ISO 15156 / NACE MR0175 sour region classification, SSC/HIC/SOHIC risk, material recommendations**           |
| **CO2 Corrosion Material Selection**  | [docs/process/corrosion/co2_corrosion_material_selection.md](process/corrosion/co2_corrosion_material_selection) | **CRA selection hierarchy — 13Cr/22Cr/25Cr/nickel alloy based on CO2 rate, H2S, chloride, temperature**        |
| **Chloride SCC Assessment**           | [docs/process/corrosion/chloride_scc_assessment.md](process/corrosion/chloride_scc_assessment)                   | **Chloride stress corrosion cracking risk for austenitic and duplex stainless steels per NORSOK M-001/MTI 15**  |
| **Oxygen Corrosion Assessment**       | [docs/process/corrosion/oxygen_corrosion_assessment.md](process/corrosion/oxygen_corrosion_assessment)           | **Dissolved O2 corrosion/pitting for injection water and utility systems per NORSOK M-001 / NACE SP0499**       |
| **Dense Phase CO2 Corrosion**         | [docs/process/corrosion/dense_phase_co2_corrosion.md](process/corrosion/dense_phase_co2_corrosion)               | **CCS pipeline corrosion — impurity specs, free water risk, water solubility per DNV-RP-J202 / ISO 27913**      |
| **Ammonia Compatibility**             | [docs/process/corrosion/ammonia_compatibility.md](process/corrosion/ammonia_compatibility)                       | **NH3 service material assessment — SCC, O2 inhibitor, copper restriction per CGA G-2.1 / ASME B31.3**         |

### Chapter 24: Serialization & Persistence

| Document                | Path                                                                                           | Description                                                          |
| ----------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Process Serialization   | [docs/simulation/process_serialization.md](simulation/process_serialization)                   | Save/load process models                                             |
| Process Model Lifecycle | [docs/process/lifecycle/process_model_lifecycle.md](process/lifecycle/process_model_lifecycle) | ProcessModelState, versioning, checkpointing, digital twin lifecycle |
| Process Automation API  | [docs/simulation/process_automation.md](simulation/process_automation)                         | String-addressable API for reading/writing simulation variables       |
| ProcessAutomation Foundations | [docs/simulation/automation/automation_foundations.md](simulation/automation/automation_foundations) | Address resolution, variable catalogs, unit handling, and safe-access schema |
| ProcessAutomation Integration Patterns | [docs/simulation/automation/automation_integrations.md](simulation/automation/automation_integrations) | Multi-area addressing plus optimizer/digital-twin integration workflows |
| ProcessAutomation Operations and Troubleshooting | [docs/simulation/automation/automation_operations.md](simulation/automation/automation_operations) | Diagnostics playbook, bounds validation, and troubleshooting checklist |

---

## Part IV: Pipeline & Multiphase Flow

### Chapter 24: Pipeline Fundamentals

| Document                   | Path                                                                                   | Description                                                                                                     |
| -------------------------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Fluid Mechanics Overview   | [docs/fluidmechanics/README.md](fluidmechanics/)                                       | Fluid mechanics module                                                                                          |
| Pipeline Index             | [docs/wiki/pipeline_index.md](wiki/pipeline_index)                                     | Pipeline documentation index                                                                                    |
| **Pipeline Simulation**    | [docs/process/equipment/pipeline_simulation.md](process/equipment/pipeline_simulation) | **Comprehensive pipeline simulation guide with PipeLineInterface, all pipe types, flow regimes, heat transfer** |
| Flow Equations             | [docs/wiki/pipeline_flow_equations.md](wiki/pipeline_flow_equations)                   | Pipeline flow equations                                                                                         |
| Single Phase Flow          | [docs/fluidmechanics/single_phase_pipe_flow.md](fluidmechanics/single_phase_pipe_flow) | Single phase pipe flow                                                                                          |
| **Flow Pattern Detection** | [docs/fluidmechanics/flow_pattern_detection.md](fluidmechanics/flow_pattern_detection) | **Taitel-Dukler, Baker, Barnea models, FlowPatternDetector API**                                                |

### Chapter 25: Pressure Drop Calculations

| Document         | Path                                                                         | Description               |
| ---------------- | ---------------------------------------------------------------------------- | ------------------------- |
| Pressure Drop    | [docs/wiki/pipeline_pressure_drop.md](wiki/pipeline_pressure_drop)           | Pressure drop calculation |
| Beggs & Brill    | [docs/wiki/beggs_and_brill_correlation.md](wiki/beggs_and_brill_correlation) | Beggs & Brill correlation |
| Friction Factors | [docs/wiki/friction_factor_models.md](wiki/friction_factor_models)           | Friction factor models    |

### Chapter 26: Heat Transfer in Pipelines

| Document                               | Path                                                                                                       | Description                                                                             |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Heat Transfer                          | [docs/wiki/pipeline_heat_transfer.md](wiki/pipeline_heat_transfer)                                         | Pipeline heat transfer                                                                  |
| Heat Transfer Module                   | [docs/fluidmechanics/heat_transfer.md](fluidmechanics/heat_transfer)                                       | Heat transfer module                                                                    |
| Pipe Wall                              | [docs/wiki/pipe_wall_heat_transfer.md](wiki/pipe_wall_heat_transfer)                                       | Pipe wall heat transfer                                                                 |
| Interphase                             | [docs/fluidmechanics/InterphaseHeatMassTransfer.md](fluidmechanics/InterphaseHeatMassTransfer)             | Interphase heat/mass transfer                                                           |
| **Droplet/Bubble Flow Correlations**   | [docs/fluidmechanics/droplet_flow_correlations.md](fluidmechanics/droplet_flow_correlations)               | **Ranz-Marshall, Kronig-Brink, Abramzon-Sirignano for dispersed flow**                 |
| Mass Transfer                          | [docs/fluidmechanics/mass_transfer.md](fluidmechanics/mass_transfer)                                       | Mass transfer models                                                                    |
| **Mass Transfer API**                  | [docs/fluidmechanics/MassTransferAPI.md](fluidmechanics/MassTransferAPI)                                   | **Complete API documentation for mass transfer with methods, parameters, and examples** |
| **Evaporation & Dissolution Tutorial** | [docs/fluidmechanics/EvaporationDissolutionTutorial.md](fluidmechanics/EvaporationDissolutionTutorial)     | **Practical tutorial for liquid evaporation and gas dissolution with worked examples**  |
| **Model Improvements**                 | [docs/fluidmechanics/MASS_TRANSFER_MODEL_IMPROVEMENTS.md](fluidmechanics/MASS_TRANSFER_MODEL_IMPROVEMENTS) | **Technical review of mass transfer model with improvement recommendations**            |

### Chapter 27: Two-Phase & Multiphase Flow

| Document             | Path                                                                                                                     | Description               |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------ | ------------------------- |
| Two-Phase Model      | [docs/fluidmechanics/TwoPhasePipeFlowModel.md](fluidmechanics/TwoPhasePipeFlowModel)                                     | Two-phase pipe flow       |
| Two-Fluid Model      | [docs/wiki/two_fluid_model.md](wiki/two_fluid_model)                                                                     | Two-fluid model           |
| Multiphase Transient | [docs/wiki/multiphase_transient_model.md](wiki/multiphase_transient_model)                                               | Multiphase transient      |
| Transient Pipe Wiki  | [docs/wiki/transient_multiphase_pipe.md](wiki/transient_multiphase_pipe)                                                 | Transient multiphase pipe |
| Development Plan     | [docs/fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan.md](fluidmechanics/TwoPhasePipeFlowSystem_Development_Plan) | Development plan          |

### Chapter 28: Transient Pipeline Simulation

| Document              | Path                                                                               | Description           |
| --------------------- | ---------------------------------------------------------------------------------- | --------------------- |
| Transient Simulation  | [docs/wiki/pipeline_transient_simulation.md](wiki/pipeline_transient_simulation)   | Transient pipeline    |
| Model Recommendations | [docs/wiki/pipeline_model_recommendations.md](wiki/pipeline_model_recommendations) | Model recommendations |
| Water Hammer          | [docs/wiki/water_hammer_implementation.md](wiki/water_hammer_implementation)       | Water hammer          |

---

## Part V: Safety & Reliability

### Chapter 29: Safety Overview

| Document             | Path                                                                             | Description                 |
| -------------------- | -------------------------------------------------------------------------------- | --------------------------- |
| Safety Overview      | [docs/safety/README.md](safety/)                                                 | Safety systems module       |
| Safety Roadmap       | [docs/safety/SAFETY_SIMULATION_ROADMAP.md](safety/SAFETY_SIMULATION_ROADMAP)     | Safety simulation roadmap   |
| Layered Architecture | [docs/safety/layered_safety_architecture.md](safety/layered_safety_architecture) | Layered safety architecture |
| Process Safety       | [docs/process/safety/README.md](process/safety/)                                 | Process safety module       |

### Chapter 30: Alarm Systems

| Document            | Path                                                                                 | Description                |
| ------------------- | ------------------------------------------------------------------------------------ | -------------------------- |
| Alarm System Guide  | [docs/safety/alarm_system_guide.md](safety/alarm_system_guide)                       | Alarm system configuration |
| Alarm Logic Example | [docs/safety/alarm_triggered_logic_example.md](safety/alarm_triggered_logic_example) | Alarm-triggered logic      |
| ESD Fire Alarm      | [docs/wiki/esd_fire_alarm_system.md](wiki/esd_fire_alarm_system)                     | ESD/Fire alarm systems     |

### Chapter 31: Pressure Relief Systems

| Document                    | Path                                                                                 | Description                                                                           |
| --------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| PSV Dynamic Sizing Wiki     | [docs/wiki/psv_dynamic_sizing_example.md](wiki/psv_dynamic_sizing_example)           | PSV dynamic sizing                                                                    |
| PSV Dynamic Sizing          | [docs/safety/psv_dynamic_sizing_example.md](safety/psv_dynamic_sizing_example)       | PSV sizing example                                                                    |
| Trapped Inventory Calculator | [docs/safety/trapped_inventory_calculator.md](safety/trapped_inventory_calculator) | Evidence-linked trapped inventory for isolation, blowdown, flare-load, and MDMT screening |
| **Relief Valve Sizing API** | [docs/safety/relief_valve_sizing_api.md](safety/relief_valve_sizing_api)             | **API 520/521 PSV sizing for gas, liquid, and two-phase relief with fire heat input** |
| PSD Valve Trip              | [docs/wiki/psd_valve_hihi_trip.md](wiki/psd_valve_hihi_trip)                         | PSD valve HIHI trip                                                                   |
| Rupture Disks               | [docs/safety/rupture_disk_dynamic_behavior.md](safety/rupture_disk_dynamic_behavior) | Rupture disk behavior                                                                 |

### Chapter 32: HIPPS Systems

| Document             | Path                                                               | Description          |
| -------------------- | ------------------------------------------------------------------ | -------------------- |
| HIPPS Summary        | [docs/safety/HIPPS_SUMMARY.md](safety/HIPPS_SUMMARY)               | HIPPS summary        |
| HIPPS Implementation | [docs/safety/hipps_implementation.md](safety/hipps_implementation) | HIPPS implementation |
| HIPPS Safety Logic   | [docs/safety/hipps_safety_logic.md](safety/hipps_safety_logic)     | HIPPS safety logic   |

### Chapter 33: ESD & Fire Systems

| Document            | Path                                                                                     | Description                |
| ------------------- | ---------------------------------------------------------------------------------------- | -------------------------- |
| ESD Blowdown        | [docs/safety/ESD_BLOWDOWN_SYSTEM.md](safety/ESD_BLOWDOWN_SYSTEM)                         | ESD blowdown system        |
| Pressure Monitoring | [docs/safety/PRESSURE_MONITORING_ESD.md](safety/PRESSURE_MONITORING_ESD)                 | Pressure monitoring ESD    |
| Fire Heat Transfer  | [docs/safety/fire_heat_transfer_enhancements.md](safety/fire_heat_transfer_enhancements) | Fire heat transfer         |
| Fire Blowdown       | [docs/safety/fire_blowdown_capabilities.md](safety/fire_blowdown_capabilities)           | Fire blowdown capabilities |

### Chapter 34: Integrated Safety Systems

| Document            | Path                                                                                   | Description                   |
| ------------------- | -------------------------------------------------------------------------------------- | ----------------------------- |
| Integrated Safety   | [docs/safety/INTEGRATED_SAFETY_SYSTEMS.md](safety/INTEGRATED_SAFETY_SYSTEMS)           | Integrated safety systems     |
| SIS Logic           | [docs/safety/sis_logic_implementation.md](safety/sis_logic_implementation)             | SIS logic implementation      |
| Choke Protection    | [docs/wiki/choke_collapse_psd_protection.md](wiki/choke_collapse_psd_protection)       | Choke collapse protection     |
| Safety Chain Tests  | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests) | Safety chain tests            |
| Scenario Generation | [docs/process/safety/scenario-generation.md](process/safety/scenario-generation)       | Automatic scenario generation |
| Barrier Management  | [docs/safety/barrier_management.md](safety/barrier_management)                         | Evidence-linked PSF/SCE barrier register and safety-analysis handoffs |
| Automated HAZOP     | [docs/safety/automated_hazop_from_stid.md](safety/automated_hazop_from_stid)           | STID/P&ID, plant data, NeqSim process simulation, HAZOP worksheet, barrier handoff, and report workflow |

### Chapter 34b: Consequence Analysis & QRA

| Document                   | Path                                                                                | Description                                                                       |
| -------------------------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| Depressurization (API 521) | [docs/safety/depressurization_per_API_521.md](safety/depressurization_per_API_521) | Transient blowdown via VU-flash, BDV sizing, fire heat input                      |
| MDMT Assessment            | [docs/safety/mdmt_assessment.md](safety/mdmt_assessment)                           | UCS-66 / API 579 / EN 13445 minimum design metal temperature                      |
| Dispersion & Consequence   | [docs/safety/dispersion_and_consequence.md](safety/dispersion_and_consequence)     | Gaussian/heavy-gas dispersion, jet/pool/VCE/BLEVE, probit, IRPA roll-up           |
| HAZOP Worksheet            | [docs/safety/HAZOP.md](safety/HAZOP)                                               | IEC 61882 guidewords, process parameters, deviation rows, and text reports        |
| FMEA / FMECA               | [docs/safety/FMEA.md](safety/FMEA)                                                 | IEC 60812 RPN = S·O·D, criticality threshold filtering                            |
| Event & Fault Trees        | [docs/safety/event_fault_trees.md](safety/event_fault_trees)                       | IEC 61025 / 62502 ETA + FTA with β-factor common-cause and k-of-N voting gates    |

### Chapter 35: Risk Simulation Framework

Comprehensive operational risk simulation framework for equipment failure analysis, production impact assessment, and degraded operation optimization. Includes Monte Carlo simulation, 5×5 risk matrix, process topology analysis, STID tagging per ISO 14224/NORSOK, and dependency analysis with cross-installation support.

| Document                       | Path                                                                               | Description                                                                               |
| ------------------------------ | ---------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| **Risk Framework Index**       | [docs/risk/index.md](risk/index)                                                   | **START HERE**: Quick start guide, architecture overview, package structure               |
| **Framework Overview**         | [docs/risk/overview.md](risk/overview)                                             | Core concepts, capabilities, industry standards compliance (ISO 14224, OREDA, NORSOK)     |
| **Equipment Failure Modeling** | [docs/risk/equipment-failure.md](risk/equipment-failure)                           | FailureType enum, capacity factors, OREDA reliability data, λ/R(t)/MTTF formulas          |
| **Risk Matrix**                | [docs/risk/risk-matrix.md](risk/risk-matrix)                                       | 5×5 probability/consequence matrix, risk scoring, cost calculations                       |
| **Monte Carlo Simulation**     | [docs/risk/monte-carlo.md](risk/monte-carlo)                                       | OperationalRiskSimulator, exponential sampling, P10/P50/P90 statistics, convergence       |
| **Production Impact Analysis** | [docs/risk/production-impact.md](risk/production-impact)                           | Loss calculations, criticality index, cascade analysis, economic impact                   |
| **Degraded Operation**         | [docs/risk/degraded-operation.md](risk/degraded-operation)                         | DegradedOperationOptimizer, recovery planning, operating modes                            |
| **Process Topology**           | [docs/risk/topology.md](risk/topology)                                             | ProcessTopologyAnalyzer, graph extraction, topological ordering, DOT/JSON export          |
| **STID Tagging**               | [docs/risk/stid-tagging.md](risk/stid-tagging)                                     | FunctionalLocation class, PPPP-TT-NNNNN[S] format, installation/equipment codes           |
| **Dependency Analysis**        | [docs/risk/dependency-analysis.md](risk/dependency-analysis)                       | DependencyAnalyzer, cascade failure trees, cross-installation effects                     |
| **Mathematical Reference**     | [docs/risk/mathematical-reference.md](risk/mathematical-reference)                 | Complete formulas: reliability, system availability, Monte Carlo, risk calculations       |
| **API Reference**              | [docs/risk/api-reference.md](risk/api-reference)                                   | Full API documentation for all risk simulation classes                                    |
| **Reliability Data Guide**     | [docs/risk/RELIABILITY_DATA_GUIDE.md](risk/RELIABILITY_DATA_GUIDE)                 | OREDA-based reliability data, failure rate sources, equipment categories                  |
| **Physics-Based Integration**  | [docs/risk/PHYSICS_BASED_RISK_INTEGRATION.md](risk/PHYSICS_BASED_RISK_INTEGRATION) | **Integration of physics-based models with risk simulation for dynamic failure analysis** |

### Chapter 35a: Advanced Risk Framework (**NEW**)

Extended risk analysis capabilities implementing P1-P7 priority improvements for oil & gas industry applications.

| Document                            | Path                                                                                                | Description                                                   |
| ----------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| **Advanced Framework Overview**     | [docs/risk/README.md](risk/)                                                                        | **START HERE**: Overview of all 7 priority packages           |
| **P1: Dynamic Simulation**          | [docs/risk/dynamic-simulation.md](risk/dynamic-simulation)                                          | Monte Carlo with transient effects, shutdown/startup modeling |
| **P2: SIS/SIF Integration**         | [docs/risk/sis-integration.md](risk/sis-integration)                                                | IEC 61508/61511, LOPA analysis, SIL verification              |
| **P4: Bow-Tie Analysis**            | [docs/risk/bowtie-analysis.md](risk/bowtie-analysis)                                                | Barrier analysis, threat/consequence visualization            |
| **P6: Condition-Based Reliability** | [docs/risk/condition-based.md](risk/condition-based)                                                | Health monitoring, RUL estimation, predictive maintenance     |
| **Tutorial Notebook**               | [docs/examples/AdvancedRiskFramework_Tutorial.ipynb](examples/AdvancedRiskFramework_Tutorial.ipynb) | Comprehensive Jupyter tutorial                                |

#### Advanced Risk Framework Packages

| Package                         | Purpose                  | Key Classes                                                            |
| ------------------------------- | ------------------------ | ---------------------------------------------------------------------- |
| `process.safety.risk.dynamic`   | P1: Transient simulation | `DynamicRiskSimulator`, `ProductionProfile`, `TransientLossStatistics` |
| `process.safety.risk.sis`       | P2: Safety systems       | `SafetyInstrumentedFunction`, `SISIntegratedRiskModel`, `LOPAResult`   |
| `process.safety.risk.realtime`  | P3: Digital twin         | `RealTimeRiskMonitor`, `RealTimeRiskAssessment`                        |
| `process.safety.risk.bowtie`    | P4: Barrier analysis     | `BowTieAnalyzer`, `BowTieModel`                                        |
| `process.safety.risk.portfolio` | P5: Portfolio risk       | `PortfolioRiskAnalyzer`, `PortfolioRiskResult`                         |
| `process.safety.risk.condition` | P6: CBR                  | `ConditionBasedReliability`                                            |
| `process.safety.risk.ml`        | P7: ML integration       | `RiskMLInterface`, `MLPrediction`                                      |
| `process.safety.risk.examples`  | Quick-start examples     | `RiskFrameworkQuickStart`                                              |

#### Key Classes in Risk Framework

| Class                        | Package                     | Purpose                                  |
| ---------------------------- | --------------------------- | ---------------------------------------- |
| `EquipmentFailureMode`       | `process.equipment.failure` | Failure mode definitions with OREDA data |
| `ReliabilityDataSource`      | `process.equipment.failure` | OREDA-based reliability data access      |
| `ProductionImpactAnalyzer`   | `process.safety.risk`       | Production loss analysis                 |
| `DegradedOperationOptimizer` | `process.safety.risk`       | Degraded mode optimization               |
| `OperationalRiskSimulator`   | `process.safety.risk`       | Monte Carlo simulation engine            |
| `RiskMatrix`                 | `process.safety.risk`       | 5×5 risk assessment matrix               |
| `ProcessTopologyAnalyzer`    | `process.util.topology`     | Process graph extraction                 |
| `FunctionalLocation`         | `process.util.topology`     | STID tag parsing (ISO 14224)             |
| `DependencyAnalyzer`         | `process.util.topology`     | Equipment dependency analysis            |

---

## Part VI: PVT & Flow Assurance

### Chapter 35: PVT Simulation

| Document                                | Path                                                                                                       | Description                                                                                                      |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| **Phase Envelope Guide**                | [docs/pvtsimulation/phase_envelope_guide.md](pvtsimulation/phase_envelope_guide)                           | **Cricondenbar, cricondentherm, HCDP, bubble/dew points**                                                        |
| **PVT Lab Tests**                       | [docs/pvtsimulation/pvt_lab_tests.md](pvtsimulation/pvt_lab_tests)                                         | **CCE, CVD, DL, separator test, swelling test simulations**                                                      |
| PVT Overview                            | [docs/pvtsimulation/README.md](pvtsimulation/)                                                             | PVT simulation module                                                                                            |
| PVT Workflows                           | [docs/wiki/pvt_simulation_workflows.md](wiki/pvt_simulation_workflows)                                     | PVT simulation workflows                                                                                         |
| PVT Workflow Module                     | [docs/pvtsimulation/pvt_workflow.md](pvtsimulation/pvt_workflow)                                           | PVT workflow module                                                                                              |
| Property Flash                          | [docs/wiki/property_flash_workflows.md](wiki/property_flash_workflows)                                     | Property flash workflows                                                                                         |
| **Relative Permeability**               | [docs/pvtsimulation/relative_permeability.md](pvtsimulation/relative_permeability)                         | **Corey and LET relative permeability models, Eclipse table export (SWOF/SGOF/SOF3)**                            |
| **Gas Pseudopressure & Pseudocritical** | [docs/pvtsimulation/gas_pseudopressure_pseudocritical.md](pvtsimulation/gas_pseudopressure_pseudocritical) | **Real gas pseudopressure integral, Standing/Sutton/Piper pseudocritical correlations, Wichert-Aziz correction** |
| Whitson Reader                          | [docs/pvtsimulation/whitson_pvt_reader.md](pvtsimulation/whitson_pvt_reader)                               | Whitson PVT reader                                                                                               |
| **Eclipse E300 Fluid Import**           | [docs/pvtsimulation/eclipse_e300_fluid_import.md](pvtsimulation/eclipse_e300_fluid_import)                 | **E300 file format reference, reading/writing Eclipse fluids, PVTsim integration, reservoir coupling workflows** |
| **JSON Fluid Format**                   | [docs/pvtsimulation/json_fluid_format.md](pvtsimulation/json_fluid_format)                                 | **JSON fluid file format reference, reading/writing/converting fluids, E300 interconversion, API integration**   |
| Solution Gas-Water Ratio                | [docs/pvtsimulation/SolutionGasWaterRatio.md](pvtsimulation/SolutionGasWaterRatio)                         | Rsw calculation methods (McCain, Søreide-Whitson, Electrolyte CPA)                                               |

### Chapter 36: Black Oil Models

| Document           | Path                                                                           | Description                                                                                    |
| ------------------ | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Black Oil Overview | [docs/blackoil/README.md](blackoil/)                                           | Black oil module                                                                               |
| Flash Playbook     | [docs/wiki/black_oil_flash_playbook.md](wiki/black_oil_flash_playbook)         | Black oil flash playbook                                                                       |
| Black Oil Export   | [docs/pvtsimulation/blackoil_pvt_export.md](pvtsimulation/blackoil_pvt_export) | Black oil PVT export, E300 compositional export, and E300 import with automatic water addition |

### Chapter 37: Flow Assurance

| Document                           | Path                                                                                                                             | Description                                                                                        |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Flow Assurance                     | [docs/pvtsimulation/flowassurance/README.md](pvtsimulation/flowassurance/)                                                       | Flow assurance module                                                                              |
| Asphaltene Modeling                | [docs/pvtsimulation/flowassurance/asphaltene_modeling.md](pvtsimulation/flowassurance/asphaltene_modeling)                       | Asphaltene modeling                                                                                |
| Asphaltene CPA                     | [docs/pvtsimulation/flowassurance/asphaltene_cpa_calculations.md](pvtsimulation/flowassurance/asphaltene_cpa_calculations)       | CPA calculations                                                                                   |
| De Boer Screening                  | [docs/pvtsimulation/flowassurance/asphaltene_deboer_screening.md](pvtsimulation/flowassurance/asphaltene_deboer_screening)       | De Boer screening                                                                                  |
| Method Comparison                  | [docs/pvtsimulation/flowassurance/asphaltene_method_comparison.md](pvtsimulation/flowassurance/asphaltene_method_comparison)     | Method comparison                                                                                  |
| Parameter Fitting                  | [docs/pvtsimulation/flowassurance/asphaltene_parameter_fitting.md](pvtsimulation/flowassurance/asphaltene_parameter_fitting)     | Parameter fitting                                                                                  |
| Validation                         | [docs/pvtsimulation/flowassurance/asphaltene_validation.md](pvtsimulation/flowassurance/asphaltene_validation)                   | Validation                                                                                         |
| **Asphaltene Stability Notebook**  | [examples/notebooks/AsphalteneStabilityAnalysis.ipynb](../examples/notebooks/AsphalteneStabilityAnalysis.ipynb)                  | **Jupyter notebook: 6-method asphaltene prediction (De Boer, FH, CPA, Pedersen, RI, benchmark), literature validation (7 cases), parity plot** |
| **Flow Assurance Screening Tools** | [docs/pvtsimulation/flowassurance/flow_assurance_screening_tools.md](pvtsimulation/flowassurance/flow_assurance_screening_tools) | **Pipeline cooldown, CO2 corrosion (de Waard-Milliams), scale prediction, wax curve monotonicity** |
| **Erosion Prediction**             | [docs/pvtsimulation/flowassurance/erosion_prediction.md](pvtsimulation/flowassurance/erosion_prediction)                         | **API RP 14E erosional velocity, DNV RP O501 sand erosion, sand load defaults by completion type, corrosion-limited velocity, risk assessment** |
| **Emulsion Viscosity**             | [docs/pvtsimulation/flowassurance/emulsion_viscosity_calculator.md](pvtsimulation/flowassurance/emulsion_viscosity_calculator)   | **Einstein, Taylor, Brinkman, Pal-Rhodes, Woelflin, Richardson models, phase inversion**           |


### Chapter 38: Gas Quality

| Document              | Path                                                                                   | Description            |
| --------------------- | -------------------------------------------------------------------------------------- | ---------------------- |
| Gas Quality Standards | [docs/wiki/gas_quality_standards_from_tests.md](wiki/gas_quality_standards_from_tests) | Gas quality standards  |
| Humid Air             | [docs/wiki/humid_air_math.md](wiki/humid_air_math)                                     | Humid air calculations |

---

## Part VII: Standards & Quality

### Chapter 39: ISO Standards

| Document                  | Path                                                                             | Description                                        |
| ------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------- |
| Standards Overview        | [docs/standards/README.md](standards/)                                           | Standards module                                   |
| ISO 6976                  | [docs/standards/iso6976_calorific_values.md](standards/iso6976_calorific_values) | ISO 6976 calorific values                          |
| ISO 6578                  | [docs/standards/iso6578_lng_density.md](standards/iso6578_lng_density)           | ISO 6578 LNG density                               |
| ISO 15403                 | [docs/standards/iso15403_cng_quality.md](standards/iso15403_cng_quality)         | ISO 15403 CNG quality                              |
| Dew Point                 | [docs/standards/dew_point_standards.md](standards/dew_point_standards)           | Dew point standards                                |
| ASTM D6377                | [docs/standards/astm_d6377_rvp.md](standards/astm_d6377_rvp)                     | ASTM D6377 RVP                                     |
| **Oil Quality Standards** | [docs/standards/oil_quality_standards.md](standards/oil_quality_standards)       | **ASTM D86, D445, D4052, D4294, D2500, D97, BS&W** |
| Sales Contracts           | [docs/standards/sales_contracts.md](standards/sales_contracts)                   | Sales contracts                                    |
| **IEC 81346 Reference Designations** | [docs/standards/iec81346-reference-designations.md](standards/iec81346-reference-designations) | **IEC 81346 structured equipment identification — letter codes, automatic designation generation, hierarchical/flat function numbering, ProcessModel integration, lifecycle state persistence, engineering deliverables, ISA-5.1 cross-reference, DEXPI export, ProcessAutomation integration** |

---

## Part VIII: Advanced Topics

### Chapter 40: Future Infrastructure

| Document              | Path                                                                   | Description                   |
| --------------------- | ---------------------------------------------------------------------- | ----------------------------- |
| Future Infrastructure | [docs/process/future-infrastructure.md](process/future-infrastructure) | Future infrastructure classes |
| API Reference         | [docs/process/future-api-reference.md](process/future-api-reference)   | Future API reference          |

### Chapter 41: Digital Twins

| Document     | Path                                                                         | Description              |
| ------------ | ---------------------------------------------------------------------------- | ------------------------ |
| Digital Twin | [docs/process/digital-twin-integration.md](process/digital-twin-integration) | Digital twin integration |
| Plant Data & Tagreader | [docs/process/plant-data-tagreader.md](process/plant-data-tagreader) | Connecting NeqSim to plant historians (PI/IP.21) via tagreader |
| Lifecycle    | [docs/process/lifecycle/README.md](process/lifecycle/)                       | Lifecycle management     |

### Chapter 42: AI/ML Integration

| Document             | Path                                                                                     | Description             |
| -------------------- | ---------------------------------------------------------------------------------------- | ----------------------- |
| AI Platform          | [docs/integration/ai_platform_integration.md](integration/ai_platform_integration)       | AI platform integration |
| AI Validation        | [docs/integration/ai_validation_framework.md](integration/ai_validation_framework)       | AI validation framework |
| AI Validation PR     | [docs/integration/PR_AI_VALIDATION_FRAMEWORK.md](integration/PR_AI_VALIDATION_FRAMEWORK) | AI validation PR docs   |
| ML Integration       | [docs/integration/ml_integration.md](integration/ml_integration)                         | ML integration guide    |
| ML Surrogate         | [docs/process/ml/README.md](process/ml/)                                                 | ML surrogate models     |
| Integration Overview | [docs/integration/README.md](integration/)                                               | Integration module      |
| **Agentic Engineering Introduction** | [docs/integration/ai_agentic_programming_intro.md](integration/ai_agentic_programming_intro) | **NEW: Comprehensive introduction to AI agent-assisted engineering with NeqSim** |
| **Agents & Skills Reference** | [docs/integration/ai_agents_reference.md](integration/ai_agents_reference) | **Complete catalog of all 16 agents and 14 skills with commands and examples** |
| **Agentic Workflow Examples** | [docs/integration/ai_workflow_examples.md](integration/ai_workflow_examples) | **NEW: Step-by-step walkthroughs of agent-driven engineering workflows** |
| **Agentic Java Classes** | [docs/integration/ai_agentic_classes.md](integration/ai_agentic_classes) | **TaskResultValidator, SimulationQualityGate, AgentSession, AgentFeedbackCollector — Java infrastructure for AI-driven simulation QA** |
| **Skills Guide** | [docs/integration/skills_guide.md](integration/skills_guide) | **Creating, using, and managing skills — core, community, and local private skills with STID and plant data examples** |

### Chapter 43: Sustainability & Emissions

| Document                                  | Path                                                                                                 | Description                                                                                                                  |
| ----------------------------------------- | ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Emissions Tracking                        | [docs/process/sustainability/README.md](process/sustainability/)                                     | Emissions tracking overview                                                                                                  |
| **Offshore Emission Reporting Guide**     | [docs/emissions/OFFSHORE_EMISSION_REPORTING.md](emissions/OFFSHORE_EMISSION_REPORTING)               | **Comprehensive guide for offshore platform GHG emission reporting with regulatory references**                              |
| **Produced Water Emissions Tutorial**     | [docs/examples/ProducedWaterEmissions_Tutorial.md](examples/ProducedWaterEmissions_Tutorial)         | **Comprehensive tutorial for produced water degassing emissions calculation**                                                |
| **Norwegian Emission Methods Comparison** | [docs/examples/NorwegianEmissionMethods_Comparison.md](examples/NorwegianEmissionMethods_Comparison) | **NeqSim vs Norwegian handbook method: validation, uncertainty, regulatory compliance**                                      |
| **GFMW 2023 Reference**                   | *External publication*                                                                               | **"Virtual Measurement of Emissions from Produced Water Using an Online Process Simulator" - Kristiansen et al., GFMW 2023** |

### Chapter 44: Optimization

| Document                                                | Path                                                                                                                                                                   | Description                                                                                                             |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Optimization Overview                                   | [docs/process/optimization/README.md](process/optimization/)                                                                                                           | Optimization module                                                                                                     |
| **Process Researcher**                                  | [docs/process/optimization/process-researcher.md](process/optimization/process-researcher)                                                                              | **Candidate flowsheet generation and ranking from feed/product specifications, including reaction-route candidates**     |
| **Flow Rate Optimization**                              | [docs/process/optimization/flow-rate-optimization.md](process/optimization/flow-rate-optimization)                                                                     | **Comprehensive flow rate optimizer with lift curve generation for Eclipse reservoir simulation**                       |
| **Multi-Objective Optimization**                        | [docs/process/optimization/multi-objective-optimization.md](process/optimization/multi-objective-optimization)                                                         | **Pareto front generation for competing objectives (throughput vs energy)**                                             |
| **Constraint Framework**                                | [docs/process/optimization/constraint-framework.md](process/optimization/constraint-framework)                                                                         | **Unified ProcessConstraint interface bridging equipment, internal, and external optimizer constraints**                |
| **Data Reconciliation and Steady-State Detection**      | [docs/process/optimization/data-reconciliation.md](process/optimization/data-reconciliation)                                                                           | **R-statistic steady-state detection, WLS data reconciliation, gross error detection, SSD-to-reconciliation bridge**    |
| Batch Studies                                           | [docs/process/optimization/batch-studies.md](process/optimization/batch-studies)                                                                                       | Batch studies                                                                                                           |
| Bottleneck Analysis                                     | [docs/wiki/bottleneck_analysis.md](wiki/bottleneck_analysis)                                                                                                           | Bottleneck analysis and ProductionOptimizer                                                                             |
| **Multi-Variable Optimization**                         | [docs/wiki/bottleneck_analysis.md#multi-variable-optimization-with-manipulatedvariable](wiki/bottleneck_analysis#multi-variable-optimization-with-manipulatedvariable) | **ManipulatedVariable for split factors, dual feeds, pressure setpoints**                                               |
| Calibration                                             | [docs/process/calibration/README.md](process/calibration/)                                                                                                             | Model calibration                                                                                                       |
| **Data Reconciliation → Parameter Estimation Workflow** | [docs/calibration/data_reconciliation_parameter_estimation.md](calibration/data_reconciliation_parameter_estimation)                                                   | **End-to-end plant-data-to-model-tuning: DataReconciliationEngine, BatchParameterEstimator (L-M), EnKF, Python helper** |
| Advisory                                                | [docs/process/advisory/README.md](process/advisory/)                                                                                                                   | Advisory systems                                                                                                        |

### Chapter 45: Real-Time Integration

| Document        | Path                                                                                                   | Description           |
| --------------- | ------------------------------------------------------------------------------------------------------ | --------------------- |
| Real-Time Guide | [docs/integration/REAL_TIME_INTEGRATION_GUIDE.md](integration/REAL_TIME_INTEGRATION_GUIDE)             | Real-time integration |
| MPC Integration | [docs/integration/mpc_integration.md](integration/mpc_integration)                                     | MPC integration       |
| Industrial MPC  | [docs/integration/neqsim_industrial_mpc_integration.md](integration/neqsim_industrial_mpc_integration) | Industrial MPC        |

### Chapter 46: External Integrations

| Document        | Path                                                                           | Description     |
| --------------- | ------------------------------------------------------------------------------ | --------------- |
| DEXPI P&ID Import, Export & Visualization | [docs/integration/dexpi-reader.md](integration/dexpi-reader) | DEXPI import/export/round-trip, ISO 10628 shapes, auto-layout, instruments, SIL markers, fail-position, mechanical design, stream table, drawing border, symbol legend, configurable layout, topology, equipment factory, simulation builder |
| QRA Integration | [docs/integration/QRA_INTEGRATION_GUIDE.md](integration/QRA_INTEGRATION_GUIDE) | QRA integration |

### Chapter 47: Process Logic Framework

| Document              | Path                                                                                                       | Description               |
| --------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------- |
| Simulation Overview   | [docs/simulation/README.md](simulation/)                                                                   | Simulation module         |
| Process Logic         | [docs/simulation/process_logic_framework.md](simulation/process_logic_framework)                           | Process logic framework   |
| Advanced Logic        | [docs/simulation/advanced_process_logic.md](simulation/advanced_process_logic)                             | Advanced process logic    |
| Implementation        | [docs/simulation/process_logic_implementation_summary.md](simulation/process_logic_implementation_summary) | Implementation summary    |
| Enhancements          | [docs/simulation/ProcessLogicEnhancements.md](simulation/ProcessLogicEnhancements)                         | Logic enhancements        |
| Runtime Flexibility   | [docs/simulation/RuntimeLogicFlexibility.md](simulation/RuntimeLogicFlexibility)                           | Runtime flexibility       |
| Graph-Based           | [docs/simulation/graph_based_process_simulation.md](simulation/graph_based_process_simulation)             | Graph-based simulation    |
| Parallel Simulation   | [docs/simulation/parallel_process_simulation.md](simulation/parallel_process_simulation)                   | Parallel simulation       |
| Recycle Acceleration  | [docs/simulation/recycle_acceleration_guide.md](simulation/recycle_acceleration_guide)                     | Recycle acceleration      |
| Process Calculator    | [docs/simulation/process_calculator.md](simulation/process_calculator)                                     | Process calculator        |
| Integrated Workflow   | [docs/simulation/INTEGRATED_WORKFLOW_GUIDE.md](simulation/INTEGRATED_WORKFLOW_GUIDE)                       | Integrated workflow       |
| Differentiable Thermo | [docs/simulation/differentiable_thermodynamics.md](simulation/differentiable_thermodynamics)               | Auto-differentiation      |
| Derivatives           | [docs/simulation/derivatives_and_gradients.md](simulation/derivatives_and_gradients)                       | Derivatives and gradients |
| Equipment Factory     | [docs/simulation/equipment_factory.md](simulation/equipment_factory)                                       | Equipment factory         |

### Chapter 48: Field Development

| Document                          | Path                                                                                                                       | Description                                                             |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| Field Development Overview        | [docs/fielddevelopment/README.md](fielddevelopment/)                                                                       | Field development module overview                                       |
| **Digital Field Twin**            | [docs/fielddevelopment/DIGITAL_FIELD_TWIN.md](fielddevelopment/DIGITAL_FIELD_TWIN)                                         | **NEW** Comprehensive architecture for lifecycle consistency            |
| **Mathematical Reference**        | [docs/fielddevelopment/MATHEMATICAL_REFERENCE.md](fielddevelopment/MATHEMATICAL_REFERENCE)                                 | **NEW** Mathematical foundations for all calculations                   |
| **API Guide**                     | [docs/fielddevelopment/API_GUIDE.md](fielddevelopment/API_GUIDE)                                                           | **NEW** Detailed usage examples for all components                      |
| **Integrated Framework**          | [docs/fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK.md](fielddevelopment/INTEGRATED_FIELD_DEVELOPMENT_FRAMEWORK) | PVT→Reservoir→Well→Process integration guide                            |
| **Decision Engine Workflows**      | [docs/fielddevelopment/DECISION_ENGINE_WORKFLOWS.md](fielddevelopment/DECISION_ENGINE_WORKFLOWS)                           | Tieback, greenfield, portfolio, process, reservoir, and reporting workflows |
| **Host Tie-In Capacity**          | [docs/fielddevelopment/HOST_TIE_IN_CAPACITY.md](fielddevelopment/HOST_TIE_IN_CAPACITY)                                     | Base-host and satellite production capacity, holdback, process bottlenecks, and debottleneck decisions |
| **Strategy**                      | [docs/fielddevelopment/FIELD_DEVELOPMENT_STRATEGY.md](fielddevelopment/FIELD_DEVELOPMENT_STRATEGY)                         | Field development strategy and roadmap                                  |
| **Late-Life Operations**          | [docs/fielddevelopment/LATE_LIFE_OPERATIONS.md](fielddevelopment/LATE_LIFE_OPERATIONS)                                     | **Turndown, debottlenecking, and decommissioning timing analysis**      |
| **Multi-Scenario VFP Generation** | [docs/fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION.md](fielddevelopment/MULTI_SCENARIO_PRODUCTION_OPTIMIZATION) | VFP tables with varying GOR/water cut for reservoir simulation coupling |
| Field Planning                    | [docs/wiki/field_development_planning.md](wiki/field_development_planning)                                                 | Field development planning                                              |
| Field Engine                      | [docs/simulation/field_development_engine.md](simulation/field_development_engine)                                         | Field development engine                                                |
| **Economics**                     | [docs/process/economics/README.md](process/economics/)                                                                     | Economics module: NPV, IRR, tax models, decline curves                  |
| **Subsea Systems**                | [docs/process/equipment/subsea_systems.md](process/equipment/subsea_systems)                                               | Subsea production systems, tieback analysis                             |

#### Digital Field Twin Lifecycle Components

| Lifecycle Phase     | Documentation               | Key Capabilities                                               |
| ------------------- | --------------------------- | -------------------------------------------------------------- |
| Screening (DG0-DG1) | DIGITAL_FIELD_TWIN §3       | Concept comparison, flow assurance screening, tieback analysis |
| Selection (DG2)     | MATHEMATICAL_REFERENCE §4-5 | NPV calculation, MCDA ranking, Norwegian tax model             |
| Definition (DG3)    | API_GUIDE §5-6              | Process system auto-generation, network modeling               |
| Execution (DG4)     | DIGITAL_FIELD_TWIN §4       | VFP table export, reservoir coupling                           |
| Operations          | DIGITAL_FIELD_TWIN §5       | Real-time optimization, Monte Carlo uncertainty                |
| Late-Life           | LATE_LIFE_OPERATIONS        | Turndown, debottlenecking, decommissioning timing              |

#### New Classes in This PR

| Class                       | Package      | Purpose                                  |
| --------------------------- | ------------ | ---------------------------------------- |
| `CashFlowEngine`            | `economics`  | Full-lifecycle NPV with tax models       |
| `NorwegianTaxModel`         | `economics`  | NCS fiscal regime (22% + 56%)            |
| `SURFCostEstimator`         | `subsea`     | SURF CAPEX with regional factors         |
| `PortfolioOptimizer`        | `economics`  | Multi-project investment optimization    |
| `DevelopmentOptionRanker`   | `evaluation` | MCDA-based concept ranking               |
| `MonteCarloRunner`          | `evaluation` | Probabilistic uncertainty analysis       |
| `ConceptToProcessLinker`    | `facility`   | Auto-generate ProcessSystem from concept |
| `MultiphaseFlowIntegrator`  | `network`    | Pipeline hydraulics integration          |
| `ReservoirCouplingExporter` | `reservoir`  | VFP tables and ECLIPSE keywords          |
| `TiebackAnalyzer`           | `tieback`    | Tieback feasibility screening            |

---

## Part IX: Developer Guide

### Chapter 49: Contributing

| Document                           | Path                                                                                             | Description                                                                           |
| ---------------------------------- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| Development Overview               | [docs/development/README.md](development/)                                                       | Development overview                                                                  |
| Contributing Structure             | [docs/development/contributing-structure.md](development/contributing-structure)                 | Contributing guidelines                                                               |
| Developer Setup                    | [docs/development/DEVELOPER_SETUP.md](development/DEVELOPER_SETUP)                               | Developer setup                                                                       |
| **Extending Process Equipment**    | [docs/development/extending_process_equipment.md](development/extending_process_equipment)       | **Add custom equipment, stream introspection, MultiPortEquipment base class**         |
| **Extending Physical Properties**  | [docs/development/extending_physical_properties.md](development/extending_physical_properties)   | **NEW: Add viscosity, conductivity, diffusivity models**                              |
| **Extending Thermodynamic Models** | [docs/development/extending_thermodynamic_models.md](development/extending_thermodynamic_models) | **NEW: Add custom equations of state**                                                |
| **Python Extension Patterns**      | [docs/development/python_extension_patterns.md](development/python_extension_patterns)           | **NEW: Python integration, wrappers, JPype interfaces**                               |
| **Jupyter Development Workflow**   | [docs/development/jupyter_development_workflow.md](development/jupyter_development_workflow)     | **Live Java development from Jupyter notebooks with auto-compile and kernel restart** |
| **Task Solving Guide**             | [docs/development/TASK_SOLVING_GUIDE.md](development/TASK_SOLVING_GUIDE)                         | **Step-by-step workflow for solving engineering tasks, including study_config.yaml, intake pauses, document inputs, NeqSim Runner execution, and detailed multi-notebook studies** |
| **Code Patterns**                  | [docs/development/CODE_PATTERNS.md](development/CODE_PATTERNS)                                   | **Copy-paste starters for common coding tasks**                                       |
| **Task Log**                       | [docs/development/TASK_LOG.md](development/TASK_LOG)                                             | **Searchable log of all solved tasks**                                                |
| **Lessons Learned**                | [docs/development/LESSONS_LEARNED.md](development/LESSONS_LEARNED)                               | **Practical lessons from 45+ solved tasks (EOS, convergence, API gotchas)**           |
| Build Guide                        | [docs/development/BUILD.md](development/BUILD)                                                   | Build system, Maven profiles, packaging                                               |
| Getting Started (Developer)        | [docs/development/GETTING_STARTED_DEVELOPER.md](development/GETTING_STARTED_DEVELOPER)           | Quick-start guide for new contributors                                                |
| Project Structure Recommendations  | [docs/development/PROJECT_STRUCTURE_RECOMMENDATIONS.md](development/PROJECT_STRUCTURE_RECOMMENDATIONS) | Recommended package layout and module boundaries                                 |

### Chapter 50: Testing

| Document      | Path                                                                                   | Description          |
| ------------- | -------------------------------------------------------------------------------------- | -------------------- |
| Test Overview | [docs/wiki/test-overview.md](wiki/test-overview)                                       | Test overview        |
| Flash Tests   | [docs/wiki/flash_equations_and_tests.md](wiki/flash_equations_and_tests)               | Flash equation tests |
| Safety Tests  | [docs/safety/integration_safety_chain_tests.md](safety/integration_safety_chain_tests) | Safety chain tests   |

### Chapter 51: Notebooks & Examples

| Document                                  | Path                                                                                                           | Description                                                                                                        |
| ----------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| **Reading Fluid Properties**              | [docs/examples/ReadingFluidProperties.md](examples/ReadingFluidProperties)                                     | **NEW: Comprehensive guide to reading thermodynamic and physical properties**                                      |
| Colab Notebooks                           | [docs/wiki/java_simulation_from_colab_notebooks.md](wiki/java_simulation_from_colab_notebooks)                 | Colab notebooks                                                                                                    |
| Transient Slug Example                    | [docs/examples/transient_slug_separator_control_example.md](examples/transient_slug_separator_control_example) | Transient slug example                                                                                             |
| Selective Logic                           | [docs/examples/selective-logic-execution.md](examples/selective-logic-execution)                               | Selective logic execution                                                                                          |
| Comparison Quickstart                     | [docs/examples/comparesimulations_quickstart.md](examples/comparesimulations_quickstart)                       | Simulation comparison                                                                                              |
| **PVT Simulation & Tuning**               | [docs/examples/PVT_Simulation_and_Tuning.md](examples/PVT_Simulation_and_Tuning)                               | **PVT simulation setup, EoS tuning, and characterization examples**                                                |
| **TVP/RVP Study**                         | [docs/examples/TVP_RVP_Study.md](examples/TVP_RVP_Study)                                                       | **True Vapor Pressure and Reid Vapor Pressure calculations**                                                       |
| **ESP Pump Tutorial**                     | [docs/examples/ESP_Pump_Tutorial.md](examples/ESP_Pump_Tutorial)                                               | **Electric Submersible Pump simulation and sizing**                                                                |
| **Graph-Based Simulation**                | [docs/examples/GraphBasedProcessSimulation.md](examples/GraphBasedProcessSimulation)                           | **Graph-based process simulation tutorial**                                                                        |
| **Field Development Workflow**            | [docs/examples/FieldDevelopmentWorkflow.md](examples/FieldDevelopmentWorkflow)                                 | **End-to-end field development workflow example**                                                                  |
| **Field Development Decision Engine**     | [examples/notebooks/field_development_decision_engine.ipynb](../examples/notebooks/field_development_decision_engine) | **Concept templates, lifecycle emissions, MCDA ranking, portfolio optimization, and report-ready tables** |
| **Field Development Process Coupling**    | [examples/notebooks/field_development_process_reservoir_coupling.ipynb](../examples/notebooks/field_development_process_reservoir_coupling) | **Tieback route networks, gathering allocation, process utility generation, and VFP/schedule export** |
| **Host Tie-In Capacity and Holdback**     | [examples/notebooks/host_tie_in_capacity_and_holdback.ipynb](../examples/notebooks/host_tie_in_capacity_and_holdback) | **Brownfield host capacity, satellite holdback, process-equipment bottlenecks, and debottleneck value** |
| **Multi-Scenario VFP Tutorial**           | [docs/examples/MultiScenarioVFP_Tutorial.ipynb](examples/MultiScenarioVFP_Tutorial)                            | **VFP generation with varying GOR/water cut scenarios**                                                            |
| **Production System Bottleneck Analysis** | [docs/examples/ProductionSystem_BottleneckAnalysis.ipynb](examples/ProductionSystem_BottleneckAnalysis)        | **Multi-well system optimization, bottleneck identification, and well prioritization**                             |
| **Integrated Production & Risk Analysis** | [docs/examples/IntegratedProductionRiskAnalysis.ipynb](examples/IntegratedProductionRiskAnalysis)              | **Complete operational workflow combining bottleneck analysis with risk simulation, Monte Carlo, and risk matrix** |
| **LNG Heat Exchanger Demo**               | [examples/notebooks/LNGHeatExchanger_ComprehensiveDemo.ipynb](../examples/notebooks/LNGHeatExchanger_ComprehensiveDemo) | **Comprehensive BAHX demo: composite curves, exergy, adaptive zones, Manglik-Bergles, transient cool-down, core sizing, freeze-out, maldistribution, mechanical design, cost estimation, SMR cycle** |
| **LNG Ageing Basics**                     | [examples/notebooks/lng_ageing_basics.ipynb](../examples/notebooks/lng_ageing_basics)                                   | **Single-tank LNG ageing simulation: composition evolution, BOG, Wobbe index, voyage profiles** |
| **LNG Ageing Advanced**                   | [examples/notebooks/lng_ageing_advanced.ipynb](../examples/notebooks/lng_ageing_advanced)                               | **Advanced LNG ageing: tank geometry, sloshing, methane number, rollover detection, multi-zone heat transfer** |
| **LNG Ship Voyage**                       | [examples/notebooks/lng_ship_voyage.ipynb](../examples/notebooks/lng_ship_voyage)                                       | **Multi-tank Q-Max carrier voyage: per-tank evolution, shared BOG handling, ship-level KPIs** |
| **MPC Integration Tutorial**              | [docs/examples/MPC_Integration_Tutorial.md](examples/MPC_Integration_Tutorial)                                 | **Model Predictive Control integration example**                                                                   |
| **AI Platform Integration**               | [docs/examples/AIPlatformIntegration.md](examples/AIPlatformIntegration)                                       | **AI platform integration tutorial**                                                                               |
| **Beer Brewing Bio-Process**              | [docs/examples/BeerBrewing_BioProcess_Simulation.md](examples/BeerBrewing_BioProcess_Simulation)               | **Bio-process simulation for brewing applications**                                                                |
| **H2S Distribution Modeling**             | [docs/examples/H2S_Distribution_Modeling.md](examples/H2S_Distribution_Modeling)                               | **H2S distribution and partitioning across phases**                                                                |
| **Multiphase Flow Pipeline Riser**        | [docs/examples/MultiphaseFlowPipelineRiser_Interactive.md](examples/MultiphaseFlowPipelineRiser_Interactive)   | **Interactive multiphase pipeline-riser simulation**                                                               |
| **Looped Pipeline Network**               | [docs/examples/LoopedPipelineNetworkExample.md](examples/LoopedPipelineNetworkExample)                         | **Looped pipeline network simulation example**                                                                     |
| **Advanced Risk Framework**               | [docs/examples/AdvancedRiskFramework_Tutorial.md](examples/AdvancedRiskFramework_Tutorial)                     | **Advanced risk framework tutorial**                                                                               |
| **Two-Fluid Pipe Tutorial**               | [docs/examples/TwoFluidPipe_Tutorial.md](examples/TwoFluidPipe_Tutorial)                                       | **Two-fluid pipe model tutorial**                                                                                  |
| Examples Index                            | [docs/examples/index.md](examples/index)                                                                       | Examples documentation index                                                                                       |

### Chapter 52: Documentation Infrastructure

| Document               | Path                                                                            | Description                                          |
| ---------------------- | ------------------------------------------------------------------------------- | ---------------------------------------------------- |
| **GitHub Pages Setup** | [docs/GITHUB_PAGES_SETUP.md](GITHUB_PAGES_SETUP)                                | **NEW** Enable GitHub Pages for hosted documentation |
| Reference Manual       | [docs/manual/neqsim_reference_manual.html](manual/neqsim_reference_manual.html) | Interactive reference manual                         |
| Documentation Index    | [docs/index.md](index)                                                          | GitHub Pages home page                               |

---

## Appendices

### Appendix A: Chemical Reactions

| Document                   | Path                                                                                                       | Description                                                 |
| -------------------------- | ---------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| Chemical Reactions         | [docs/chemicalreactions/README.md](chemicalreactions/)                                                     | Chemical reactions module                                   |
| Sulfur Deposition Analysis | [docs/chemicalreactions/sulfur_deposition_analysis.md](chemicalreactions/sulfur_deposition_analysis)       | Sulfur formation, solubility, deposition, and FeS corrosion |
| Chemistry & Integrity      | [docs/chemistry/index.md](chemistry/)                                                                      | Open standards-traceable chemical integrity stack (scale, corrosion, scavengers, RCA, MCP) |
| Electrolyte Scale (Davies) | [docs/chemistry/electrolyte_scale.md](chemistry/electrolyte_scale)                                         | Davies SI math, ion conventions, North-Sea worked example   |
| Mechanistic CO2 Corrosion  | [docs/chemistry/mechanistic_corrosion.md](chemistry/mechanistic_corrosion)                                 | NORSOK + Nesic + Langmuir inhibitor with worked example     |
| Packed-bed Scavenger       | [docs/chemistry/packed_bed_scavenger.md](chemistry/packed_bed_scavenger)                                   | 1D PFR PDE for H2S scavenger sizing and breakthrough        |
| Closed-loop Deposition     | [docs/chemistry/closed_loop_deposition.md](chemistry/closed_loop_deposition)                               | Coupling pipe hydraulics with `ScaleDepositionAccumulator`  |
| MCP `runChemistry` tool    | [docs/chemistry/mcp.md](chemistry/mcp)                                                                     | JSON schema for the chemistry MCP tool                      |

### Appendix B: Statistics

| Document          | Path                                                                           | Description            |
| ----------------- | ------------------------------------------------------------------------------ | ---------------------- |
| Statistics        | [docs/statistics/README.md](statistics/)                                       | Statistics module      |
| Parameter Fitting | [docs/statistics/parameter_fitting.md](statistics/parameter_fitting)           | Detailed parameter fitting guide with legacy API, CSV/YAML data files, specs, robust objectives, validation, and reports      |
| Monte Carlo       | [docs/statistics/monte_carlo_simulation.md](statistics/monte_carlo_simulation) | Monte Carlo simulation |
| Data Analysis     | [docs/statistics/data_analysis.md](statistics/data_analysis)                   | Data analysis          |

### Appendix C: Mathematical Library

| Document     | Path                               | Description          |
| ------------ | ---------------------------------- | -------------------- |
| Math Library | [docs/mathlib/README.md](mathlib/) | Mathematical library |

### Appendix D: Utilities

| Document                    | Path                                                                         | Description                                                                          |
| --------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Utilities                   | [docs/util/README.md](util/)                                                 | Utility functions                                                                    |
| Unit Conversion             | [docs/util/unit_conversion.md](util/unit_conversion)                         | Unit conversion guide                                                                |
| **Unit Conversion Recipes** | [docs/cookbook/unit-conversion-recipes.md](cookbook/unit-conversion-recipes) | **NEW** Quick reference for all supported unit strings                               |
| **Optimizer Guide**         | [docs/util/optimizer_guide.md](util/optimizer_guide)                         | **NEW** Comprehensive optimization framework with BFGS, Pareto, sensitivity analysis |

### Appendix F: Process Design Templates

| Document            | Path                                                                     | Description                                                                 |
| ------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| **Templates Guide** | [docs/process/design/templates_guide.md](process/design/templates_guide) | **NEW** Pre-built process templates (compression, dehydration, CO2 capture) |

### Appendix G: Mechanical Design Standards

| Document                | Path                                                                                                   | Description                                                 |
| ----------------------- | ------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------- |
| **TEMA Standard Guide** | [docs/process/mechanical_design/tema_standard_guide.md](process/mechanical_design/tema_standard_guide) | **NEW** TEMA shell and tube heat exchanger design standards |
| **Thermal-Hydraulic Design** | [docs/process/mechanical_design/thermal_hydraulic_design.md](process/mechanical_design/thermal_hydraulic_design) | **NEW** Tube/shell HTC (Gnielinski, Kern, Bell-Delaware), overall U, pressure drops, LMTD correction, vibration screening, rating mode |
| **Two-Phase Heat Transfer** | [docs/process/mechanical_design/two_phase_heat_transfer.md](process/mechanical_design/two_phase_heat_transfer) | **NEW** Shah condensation, Chen/Gungor-Winterton boiling, Friedel/MSH two-phase pressure drop, Ebert-Panchal fouling, incremental zone analysis, tube inserts |

### Appendix H: Cookbook (Quick Recipes)

| Document               | Path                                                                       | Description                                         |
| ---------------------- | -------------------------------------------------------------------------- | --------------------------------------------------- |
| **Cookbook Index**     | [docs/cookbook/index.md](cookbook/index)                                   | **NEW** Quick copy-paste recipes for common tasks   |
| Thermodynamics Recipes | [docs/cookbook/thermodynamics-recipes.md](cookbook/thermodynamics-recipes) | Fluids, flash, properties, phase envelopes          |
| Process Recipes        | [docs/cookbook/process-recipes.md](cookbook/process-recipes)               | Separators, compressors, heat exchangers            |
| Pipeline Recipes       | [docs/cookbook/pipeline-recipes.md](cookbook/pipeline-recipes)             | Pressure drop, multiphase flow                      |
| **Adsorption Recipes** | [docs/cookbook/adsorption-recipes.md](cookbook/adsorption-recipes)         | **Adsorption bed simulation recipes and workflows** |

### Appendix I: Troubleshooting

| Document                  | Path                                                   | Description                          |
| ------------------------- | ------------------------------------------------------ | ------------------------------------ |
| **Troubleshooting Guide** | [docs/troubleshooting/index.md](troubleshooting/index) | **NEW** Solutions to common problems |

### Appendix E: Wiki Reference

| Document      | Path                         | Description        |
| ------------- | ---------------------------- | ------------------ |
| Wiki Overview | [docs/wiki/README.md](wiki/) | Wiki documentation |

---

## Document Statistics

| Category               | Count   |
| ---------------------- | ------- |
| Wiki/Tutorials         | 60      |
| Thermodynamics         | 26      |
| Process Simulation     | 47      |
| Safety Systems         | 18      |
| **Risk Simulation**    | **13**  |
| Field Development      | 11      |
| Integration/AI         | 12      |
| Pipeline/Flow          | 17      |
| PVT/Reservoir          | 15      |
| Standards              | 6       |
| Development            | 11      |
| Statistics             | 4       |
| Examples               | 19      |
| **Optimization**       | **5**   |
| **Templates & Design** | **2**   |
| **Quickstart Guides**  | **4**   |
| **Cookbook**           | **6**   |
| **Tutorials/Learning** | **2**   |
| **Troubleshooting**    | **1**   |
| Other                  | 24      |
| **Total**              | **303** |

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
