---
title: Learning Paths
description: Structured learning paths for PVT engineers, process engineers, and developers to master NeqSim efficiently.
---

# Learning Paths

Choose the learning path that matches your role and goals.

## 🎯 Choose Your Path

| Path | For | Time | Focus |
|------|-----|------|-------|
| [PVT Engineer](#pvt-engineer-path) | Reservoir/PVT engineers | 4-6 hours | Fluids, flash, characterization |
| [Process Engineer](#process-engineer-path) | Process/facilities engineers | 6-8 hours | Equipment, flowsheets, optimization |
| [Developer](#developer-path) | Software developers | 8-10 hours | API, architecture, extensions |

---

## 🔬 PVT Engineer Path

**Goal**: Master fluid modeling, flash calculations, and PVT characterization.

### Level 1: Fundamentals (1 hour)

1. **[Python Quickstart](../quickstart/python-quickstart)** - Get NeqSim running
2. **[Reading Fluid Properties](../thermo/reading_fluid_properties)** - Understanding init levels
3. **Run**: [Reading Fluid Properties Notebook](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb)

### Level 2: Thermodynamic Models (1.5 hours)

1. **[Thermodynamic Models Guide](../thermo/thermodynamic_models)** - All EoS options
2. **[Mixing Rules Guide](../thermo/mixing_rules_guide)** - BIPs and mixing rules
3. **Reference**: [Which EoS should I use?](../cookbook/thermodynamics-recipes.md#which-eos-should-i-use)

| Fluid Type | Recommended EoS |
|------------|-----------------|
| Dry gas | SRK, PR |
| Gas condensate | PR, SRK-Peneloux |
| Black oil | PR, SRK |
| Heavy oil | PR, CPA |
| CO₂ systems | SRK-CPA, GERG-2008 |
| Aqueous | CPA, Electrolyte-CPA |

### Level 3: Flash Calculations (1 hour)

1. **[Flash Calculations Guide](../thermo/flash_calculations_guide)** - All flash types
2. **[Flash Equations](../wiki/flash_equations_and_tests)** - Mathematical details
3. **Practice**: Run different flash types on same fluid

| Flash Type | Specify | Calculate |
|------------|---------|-----------|
| TPflash | T, P | Phase amounts, compositions |
| PHflash | P, H | T, phase amounts |
| PSflash | P, S | T, phase amounts |
| TVflash | T, V | P, phase amounts |

### Level 4: Fluid Characterization (1.5 hours)

1. **[PVT Fluid Characterization](../thermo/pvt_fluid_characterization)** - Plus fraction handling
2. **[Fluid Characterization Math](../pvtsimulation/fluid_characterization_mathematics)** - Lumping details
3. **Run**: [PVT Simulation and Tuning Notebook](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb)

### Level 5: Advanced Topics (1 hour)

1. **[Hydrate Models](../thermo/hydrate_models)** - Hydrate equilibrium
2. **[Wax Characterization](../thermo/characterization/wax_characterization)** - Wax modeling
3. **[Asphaltene Modeling](../pvtsimulation/flowassurance/asphaltene_modeling)** - Asphaltene precipitation

### 📚 PVT Reference Materials

- [JavaDoc: SystemInterface](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/thermo/system/SystemInterface.html)
- [JavaDoc: ThermodynamicOperations](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/thermodynamicoperations/ThermodynamicOperations.html)
- [Component Database Guide](../thermo/component_database_guide)

---

## 🏭 Process Engineer Path

**Goal**: Design and simulate process flowsheets with equipment models.

### Level 1: Fundamentals (1.5 hours)

1. **[Java Quickstart](../quickstart/java-quickstart)** - Process simulation basics
2. **[Process System Guide](../process/processmodel/process_system)** - Building flowsheets
3. **[Streams Documentation](../process/equipment/streams)** - Material streams

### Level 2: Core Equipment (2 hours)

1. **[Separators](../process/equipment/separators)** - 2-phase and 3-phase
2. **[Compressors](../process/equipment/compressors)** - Centrifugal, reciprocating
3. **[Heat Exchangers](../process/equipment/heat_exchangers)** - Heaters, coolers, exchangers
4. **[Valves](../process/equipment/valves)** - Control valves, chokes

### Level 3: Process Flowsheets (2 hours)

1. **[Mixers and Splitters](../process/equipment/mixers_splitters)** - Stream combining/splitting
2. **[Recycles](../process/equipment/util/recycles)** - Handling recycle streams
3. **[Adjusters](../process/equipment/util/adjusters)** - Specification adjustments
4. **Run**: [Network Solver Tutorial](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb)

### Level 4: Advanced Equipment (1.5 hours)

1. **[Distillation](../process/equipment/distillation)** - Column simulation
2. **[Pipelines](../process/equipment/pipelines)** - Multiphase flow
3. **[Pumps](../process/equipment/pumps)** - Pump modeling
4. **[Wells](../process/equipment/wells)** - Well modeling

### Level 5: Optimization & Control (1 hour)

1. **[Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW)** - Process optimization
2. **[Controllers](../process/controllers)** - Process control
3. **Run**: [Production Optimizer Tutorial](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb)

### 📚 Process Reference Materials

- [JavaDoc: ProcessSystem](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/process/processmodel/ProcessSystem.html)
- [JavaDoc: ProcessEquipmentInterface](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/process/equipment/ProcessEquipmentInterface.html)
- [Equipment Index](../process/equipment/README)

---

## 💻 Developer Path

**Goal**: Understand NeqSim architecture, extend functionality, contribute code.

### Level 1: Setup & Architecture (2 hours)

1. **[Developer Setup](../development/DEVELOPER_SETUP)** - Build from source
2. **[Modules Overview](../modules)** - Package architecture
3. **[Contributing Guide](../development/README)** - Code standards

### Level 2: Core APIs (2 hours)

1. **[SystemInterface JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/thermo/system/SystemInterface.html)** - Fluid API
2. **[ProcessEquipmentInterface JavaDoc](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/neqsim/process/equipment/ProcessEquipmentInterface.html)** - Equipment API
3. **[Test Overview](../wiki/test-overview)** - Testing patterns

### Level 3: Thermodynamic Implementation (2 hours)

1. **[Mathematical Models](../thermo/mathematical_models)** - EoS implementation
2. **[Phase Package](../thermo/phase/README)** - Phase calculations
3. **[Component Package](../thermo/component/README)** - Component properties

### Level 4: Process Implementation (2 hours)

1. **[Equipment Base Classes](../process/README)** - Equipment patterns
2. **[Mechanical Design](../process/mechanical_design)** - Design calculations
3. **[Graph Simulation](../process/processmodel/graph_simulation)** - Topology analysis

### Level 5: Advanced Development (2 hours)

1. **[AI Integration](../integration/ai_platform_integration)** - ML/AI patterns
2. **[MPC Integration](../integration/mpc_integration)** - Control system integration
3. **[Serialization](../simulation/process_serialization)** - Save/load processes

### 📚 Developer Reference Materials

- [Full JavaDoc API](https://equinor.github.io/neqsimhome/javadoc/site/apidocs/index.html)
- [Reference Manual Index](../REFERENCE_MANUAL_INDEX)
- [GitHub Repository](https://github.com/equinor/neqsim)

---

## 📊 Progress Checklist

Use this to track your progress:

### PVT Engineer
- [ ] Can create fluids with different EoS
- [ ] Understand init levels (init(1), init(2), initProperties)
- [ ] Can run different flash types
- [ ] Can characterize plus fractions
- [ ] Can calculate phase envelopes
- [ ] Can model hydrates/wax

### Process Engineer
- [ ] Can build a simple process flowsheet
- [ ] Can use separators, compressors, heat exchangers
- [ ] Can handle recycle streams
- [ ] Can use adjusters for specifications
- [ ] Can model pipelines
- [ ] Can optimize processes

### Developer
- [ ] Can build NeqSim from source
- [ ] Understand package architecture
- [ ] Can write unit tests
- [ ] Can add new components
- [ ] Can create new equipment
- [ ] Can integrate with external systems
