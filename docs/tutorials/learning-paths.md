---
title: Learning Paths
description: Structured learning paths for PVT engineers, process engineers, and developers to master NeqSim efficiently.
---

Choose the learning path that matches your role and goals.

Notebook labels below mirror the [examples catalog](../examples/index.md). **Executed** means the committed notebook contains stored execution counts and outputs; **Source only** means it contains source cells but no stored execution. Rerun either kind against the current `master` branch before treating its results as current validation evidence.

## Choose your path

| Path | For | Time | Focus |
|------|-----|------|-------|
| [PVT Engineer](#pvt-engineer-path) | Reservoir/PVT engineers | 4-6 hours | Fluids, flash, characterization |
| [Process Engineer](#process-engineer-path) | Process/facilities engineers | 6-8 hours | Equipment, flowsheets, optimization |
| [Developer](#developer-path) | Software developers | 8-10 hours | API, architecture, extensions |

---

## PVT engineer path

**Goal**: Master fluid modeling, flash calculations, and PVT characterization.

### Level 1: Fundamentals (1 hour)

1. **[Python Quickstart](../quickstart/python-quickstart.md)** - Get NeqSim running
2. **[Reading Fluid Properties](../thermo/reading_fluid_properties.md)** - Understanding init levels
3. **Run — Executed**: [Reading Fluid Properties Notebook](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ReadingFluidProperties.ipynb) — stored outputs are available; rerun all cells for current-master evidence

### Level 2: Thermodynamic Models (1.5 hours)

1. **[Thermodynamic Models Guide](../thermo/thermodynamic_models.md)** - All EoS options
2. **[Mixing Rules Guide](../thermo/mixing_rules_guide.md)** - BIPs and mixing rules
3. **Reference**: [Which EoS should I use?](../cookbook/thermodynamics-recipes.md#which-eos-should-i-use)

| Fluid or task | Starting model | Important qualification |
|---|---|---|
| Dry natural gas | SRK or PR | Validate density and calorific properties for the composition |
| Gas condensate | PR or SRK | Tune heavy-end characterization to PVT data |
| Black or volatile oil | PR or SRK | Characterize and tune C7+ before process studies |
| Heavy oil | PR/SRK family; consider `SystemPrDanesh` | Validate heavy-end characterization, density, and PVT response |
| Dry CO₂-rich fluid | PR, SRK, or a validated multiparameter model | Check impurities and phase-boundary range |
| CO₂ with water | CPA | Validate water content, mutual solubility, and BIPs |
| Natural-gas reference properties | GERG-2008 | Use only supported components and validity ranges; it is not a general multiphase-VLE default |
| Electrolytes and brines | Electrolyte-CPA | Define ions, salinity basis, and precipitation scope |

### Level 3: Flash Calculations (1 hour)

1. **[Flash Calculations Guide](../thermo/flash_calculations_guide.md)** - All flash types
2. **[Flash Equations](../wiki/flash_equations_and_tests.md)** - Mathematical details
3. **Practice**: Run different flash types on same fluid

| Flash Type | Specify | Calculate |
|------------|---------|-----------|
| TPflash | T, P | Phase amounts, compositions |
| PHflash | P, H | T, phase amounts |
| PSflash | P, S | T, phase amounts |
| TVflash | T, V | P, phase amounts |

### Level 4: Fluid Characterization (1.5 hours)

1. **[PVT Fluid Characterization](../thermo/pvt_fluid_characterization.md)** - Plus fraction handling
2. **[Fluid Characterization Math](../pvtsimulation/fluid_characterization_mathematics.md)** - Lumping details
3. **Inspect or run — Source only**: [PVT Simulation and Tuning Notebook](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/PVT_Simulation_and_Tuning.ipynb) — no stored execution; run all cells before relying on results

### Level 5: Advanced Topics (1 hour)

1. **[Hydrate Models](../thermo/hydrate_models.md)** - Hydrate equilibrium
2. **[Wax Characterization](../thermo/characterization/wax_characterization.md)** - Wax modeling
3. **[Asphaltene Modeling](../pvtsimulation/flowassurance/asphaltene_modeling.md)** - Asphaltene precipitation

### Level 6: AI-Assisted Studies (30 min)

1. **[Solve an Engineering Task](solve-engineering-task)** — Let AI agents handle the workflow
2. **[Task Solving Guide](../development/TASK_SOLVING_GUIDE.md)** — How the multi-agent system works
3. **Try it**: Type `@solve.task hydrate formation temperature for rich gas at 100 bara` in VS Code

The AI workflow automates the process around PVT calculations — scoping, running simulations, validating against benchmarks, and generating Word/HTML reports — so you can focus on interpreting results.

### PVT reference materials

- [JavaDoc: SystemInterface](https://equinor.github.io/neqsim/javadoc/neqsim/thermo/system/SystemInterface.html)
- [JavaDoc: ThermodynamicOperations](https://equinor.github.io/neqsim/javadoc/neqsim/thermodynamicoperations/ThermodynamicOperations.html)
- [Component Database Guide](../thermo/component_database_guide.md)

---

## Process engineer path

**Goal**: Design and simulate process flowsheets with equipment models.

### Level 1: Fundamentals (1.5 hours)

1. **[Java Quickstart](../quickstart/java-quickstart.md)** - Process simulation basics
2. **[Process System Guide](../process/processmodel/process_system.md)** - Building flowsheets
3. **[Streams Documentation](../process/equipment/streams.md)** - Material streams

### Level 2: Core Equipment (2 hours)

1. **[Separators](../process/equipment/separators.md)** - 2-phase and 3-phase
2. **[Compressors](../process/equipment/compressors.md)** - Centrifugal, reciprocating
3. **[Heat Exchangers](../process/equipment/heat_exchangers.md)** - Heaters, coolers, exchangers
4. **[Valves](../process/equipment/valves.md)** - Control valves, chokes

### Level 3: Process Flowsheets (2 hours)

1. **[Mixers and Splitters](../process/equipment/mixers_splitters.md)** - Stream combining/splitting
2. **[Recycles](../process/equipment/util/recycles.md)** - Handling recycle streams
3. **[Adjusters](../process/equipment/util/adjusters.md)** - Specification adjustments
4. **Inspect or run — Source only**: [Network Solver Tutorial](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/NetworkSolverTutorial.ipynb) — no stored execution; run all cells before relying on results

### Level 4: Advanced Equipment (1.5 hours)

1. **[Distillation](../process/equipment/distillation.md)** - Column simulation
2. **[Pipelines](../process/equipment/pipelines.md)** - Multiphase flow
3. **[Pumps](../process/equipment/pumps.md)** - Pump modeling
4. **[Wells](../process/equipment/wells.md)** - Well modeling

### Level 5: Optimization & Control (1 hour)

1. **[Optimization Overview](../process/optimization/OPTIMIZATION_OVERVIEW.md)** - Process optimization
2. **[Controllers](../process/controllers.md)** - Process control
3. **Inspect or run — Source only**: [Production Optimizer Tutorial](https://colab.research.google.com/github/equinor/neqsim/blob/master/docs/examples/ProductionOptimizer_Tutorial.ipynb) — no stored execution; run all cells before relying on results

### Level 6: AI-Assisted Studies (30 min)

1. **[Solve an Engineering Task](solve-engineering-task)** — Let AI agents handle the workflow
2. **[Task Solving Guide](../development/TASK_SOLVING_GUIDE.md)** — How the multi-agent system works
3. **Try it**: Type `@solve.task TEG dehydration sizing for 50 MMSCFD wet gas` in VS Code

The AI workflow automates the work around process simulation — scoping, literature review, building flowsheets, validating against benchmarks, uncertainty analysis, and generating engineering reports — so you can focus on design decisions.

### Process reference materials

- [JavaDoc: ProcessSystem](https://equinor.github.io/neqsim/javadoc/neqsim/process/processmodel/ProcessSystem.html)
- [JavaDoc: ProcessEquipmentInterface](https://equinor.github.io/neqsim/javadoc/neqsim/process/equipment/ProcessEquipmentInterface.html)
- [Equipment Index](../process/equipment/README.md)

---

## Developer path

**Goal**: Understand NeqSim architecture, extend functionality, contribute code.

### Level 1: Setup & Architecture (2 hours)

1. **[Developer Setup](../development/DEVELOPER_SETUP.md)** - Build from source
2. **[Modules Overview](../modules.md)** - Package architecture
3. **[Contributing Guide](../development/README.md)** - Code standards

### Level 2: Core APIs (2 hours)

1. **[SystemInterface JavaDoc](https://equinor.github.io/neqsim/javadoc/neqsim/thermo/system/SystemInterface.html)** - Fluid API
2. **[ProcessEquipmentInterface JavaDoc](https://equinor.github.io/neqsim/javadoc/neqsim/process/equipment/ProcessEquipmentInterface.html)** - Equipment API
3. **[Test Overview](../wiki/test-overview.md)** - Testing patterns

### Level 3: Thermodynamic Implementation (2 hours)

1. **[Mathematical Models](../thermo/mathematical_models.md)** - EoS implementation
2. **[Phase Package](../thermo/phase/README.md)** - Phase calculations
3. **[Component Package](../thermo/component/README.md)** - Component properties

### Level 4: Process Implementation (2 hours)

1. **[Equipment Base Classes](../process/README.md)** - Equipment patterns
2. **[Mechanical Design](../process/mechanical_design.md)** - Design calculations
3. **[Graph Simulation](../process/processmodel/graph_simulation.md)** - Topology analysis

### Level 5: Advanced Development (2 hours)

1. **[AI Integration](../integration/ai_platform_integration.md)** - ML/AI patterns
2. **[MPC Integration](../integration/mpc_integration.md)** - Control system integration
3. **[Serialization](../simulation/process_serialization.md)** - Save/load processes

### Developer reference materials

- [Full JavaDoc API](https://equinor.github.io/neqsim/javadoc/index.html)
- [Reference Manual Index](../REFERENCE_MANUAL_INDEX.md)
- [GitHub Repository](https://github.com/equinor/neqsim)

---

## Progress checklist

Use this to track your progress:

### PVT Engineer
- [ ] Can create fluids with different EoS
- [ ] Understand init levels (init(1), init(2), initProperties)
- [ ] Can run different flash types
- [ ] Can characterize plus fractions
- [ ] Can calculate phase envelopes
- [ ] Can model hydrates/wax
- [ ] Can use `@solve.task` to run a PVT study end-to-end

### Process Engineer
- [ ] Can build a simple process flowsheet
- [ ] Can use separators, compressors, heat exchangers
- [ ] Can handle recycle streams
- [ ] Can use adjusters for specifications
- [ ] Can model pipelines
- [ ] Can optimize processes
- [ ] Can use `@solve.task` to produce a validated engineering report

### Developer
- [ ] Can build NeqSim from source
- [ ] Understand package architecture
- [ ] Can write unit tests
- [ ] Can add new components
- [ ] Can create new equipment
- [ ] Can integrate with external systems
