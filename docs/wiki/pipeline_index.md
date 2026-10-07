---
title: "Pipeline Modeling Documentation"
description: "Task-oriented navigation for NeqSim pipeline hydraulics, transient flow, networks, transport models, and mechanical design."
---

Use this hub to choose the maintained pipeline guide that matches the engineering question. The
linked guides own their API examples, units, assumptions, validation evidence, and limitations;
this page does not duplicate those details.

## Choose a workflow

| Engineering task | Start here | Scope |
| --- | --- | --- |
| Build or compare steady pipeline equipment | [Pipeline simulation guide](../process/equipment/pipeline_simulation.md) | Equipment selection, geometry, pressure drop, elevation, and heat transfer |
| Run a complete calculation | [Pipeline recipes](../cookbook/pipeline-recipes.md) | Maintained, test-backed Java examples |
| Select a model for an operating case | [Model recommendations](pipeline_model_recommendations.md) | Decision guidance and model boundaries |
| Review pressure-drop equations | [Pipeline flow equations](pipeline_flow_equations.md) | Governing equations, friction, elevation, and units |
| Compare multiphase correlations | [Multiphase-flow correlations](../process/equipment/multiphase_flow_correlations.md) | Beggs-Brill, Hagedorn-Brown, Mukherjee-Brill, and Gray routes |
| Study detailed transient multiphase flow | [Multiphase transient model](multiphase_transient_model.md) | `TwoFluidPipe` model structure, closures, and diagnostics |
| Configure transient numerics | [Transient multiphase pipe](transient_multiphase_pipe.md) | Sections, time integration, reporting, and engineering limits |
| Assess rapid pressure surges | [Water-hammer implementation](water_hammer_implementation.md) | `WaterHammerPipe`, method of characteristics, and surge boundaries |
| Model low-level heat and mass transfer | [Fluid-mechanics documentation](../fluidmechanics/README.md) | Flow systems, regimes, interphase transport, and detailed profiles |
| Optimize connected lines and branches | [Pipeline-network optimization](../process/pipeline_network_optimization.md) | Network topology, hydraulic coupling, constraints, and optimization |
| Connect wells, templates, pipelines, and risers | [SURF network integration](../fielddevelopment/SURF_NETWORK_INTEGRATION.md) | Production and injection system topology |
| Screen wall thickness and integrity | [Pipeline mechanical design](../process/pipeline_mechanical_design.md) | Design-code workflows kept separate from hydraulic simulation |

## Model and documentation boundaries

| Capability | Primary NeqSim route | Documentation boundary |
| --- | --- | --- |
| Correlation-based steady hydraulics | `PipeBeggsAndBrills` and related pipeline equipment | Use the [pipeline simulation guide](../process/equipment/pipeline_simulation.md) and [correlation guide](../process/equipment/multiphase_flow_correlations.md) |
| Detailed transient two-fluid hydraulics | `TwoFluidPipe` | Use the [multiphase transient model](multiphase_transient_model.md); do not treat it as a drift-flux alias |
| Fast liquid-pressure transients | `WaterHammerPipe` | Use the [water-hammer guide](water_hammer_implementation.md) and its stated assumptions |
| Low-level non-equilibrium transport | `TwoPhasePipeFlowSystem` | Use the [two-phase flow-system guide](../fluidmechanics/TwoPhasePipeFlowModel.md) |
| Field and gathering topology | Network and SURF APIs | Use the [network optimization](../process/pipeline_network_optimization.md) and [SURF integration](../fielddevelopment/SURF_NETWORK_INTEGRATION.md) guides |
| Mechanical capacity and integrity | Pipeline mechanical-design APIs | Use the [mechanical-design guide](../process/pipeline_mechanical_design.md); hydraulic results alone are not a design-code check |

## Supporting indexes

- [Process-equipment documentation](../process/equipment/README.md)
- [Fluid-mechanics index](../fluidmechanics/index.md)
- [Wiki learning path](index.md)
- [Reference manual index](../REFERENCE_MANUAL_INDEX.md)

For API signatures, verify the linked guide against the current Java source and
[JavaDoc](https://equinor.github.io/neqsim/javadoc/index.html). For defects or missing coverage,
use the [NeqSim issue tracker](https://github.com/equinor/neqsim/issues).
