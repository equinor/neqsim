---
title: "Usage Examples"
description: "A curated catalog of maintained NeqSim examples for thermodynamics, PVT, process simulation, pipelines, dynamics, standards, and troubleshooting."
---

This catalog points to maintained, task-oriented examples instead of repeating short Java
fragments that can drift away from the public API. Start with the
[canonical quick start](getting_started.md#run-the-canonical-java-quick-start), then choose the
workflow closest to your engineering question.

## Thermodynamics and physical properties

| Task | Maintained example | What to verify |
| --- | --- | --- |
| Create a fluid and run TP, PH, and dew-point flashes | [Thermodynamics recipes](../cookbook/thermodynamics-recipes.md) | Model, mixing rule, state units, phase result |
| Understand flash algorithms and validation cases | [Flash equations and tests](flash_equations_and_tests.md) | Convergence, phase stability, material balance |
| Read density, viscosity, conductivity, and interfacial properties | [Property-flash workflows](property_flash_workflows.md) | Required initialization and property units |
| Select an equation of state | [Thermodynamics guide](thermodynamics_guide.md) | Fluid range, association, electrolyte limitations |

## Fluid characterization and PVT

| Task | Maintained example | What to verify |
| --- | --- | --- |
| Add TBP and plus fractions | [Fluid characterization](fluid_characterization.md) | Molar-mass and density basis, characterization model |
| Run CCE, CVD, separator, and viscosity studies | [PVT simulation workflows](pvt_simulation_workflows.md) | Laboratory conditions, basis, observed-data comparison |
| Screen crude-oil quality | [Oil-quality standards](../standards/oil_quality_standards.md) | Method boundary, sample basis, units, expert review |

## Process simulation and equipment

| Task | Maintained example | What to verify |
| --- | --- | --- |
| Connect streams, separators, valves, heaters, and compressors | [Process recipes](../cookbook/process-recipes.md) | Feed initialization, equipment units, balances |
| Select execution and inspect process results | [ProcessSystem guide](../process/processmodel/process_system.md) | Topology, convergence, calculation identity, reporting |
| Model tray absorbers and strippers | [Absorber and stripper guide](../process/equipment/absorbers.md) | Solver status, tray setup, transfer and balance checks |
| Explore broader process patterns | [Process simulation guide](process_simulation.md) | Model ownership, recycles, operating assumptions |

## Pipelines and dynamics

| Task | Maintained example | What to verify |
| --- | --- | --- |
| Choose a steady-state or transient pipeline model | [Pipeline documentation index](pipeline_index.md) | Geometry, elevation sign, roughness, heat transfer, flow regime |
| Build a dynamic process case | [Process transient simulation guide](process_transient_simulation_guide.md) | Initial steady state, time step, controller topology, inventories |

## Tutorials and notebooks

The [examples index](../examples/index.md) links to complete tutorials and source notebooks. A
published notebook should have been executed from a clean runtime and saved with its calculated
tables, plots, diagnostics, and validation checks. Interactive outputs need a stored static
fallback when they cannot render reliably outside their original environment.

Use the [Java getting-started guide](../java-getting-started.md) for a complete application layout.
For short copyable recipes, prefer the maintained cookbook pages over isolated fragments copied
from old issues or tests.

## Validation checklist

Before adapting an example:

1. Pin the NeqSim version or record the tested `master` commit.
2. Define composition basis, thermodynamic model, mixing rule, temperature, pressure, and flow
   units.
3. Run flashes and property initialization in the order required by the API.
4. Check phase availability before reading a named phase.
5. Verify material, component, and energy balances appropriate to the workflow.
6. Compare important results with a nearby operating point and an independent reference or
   defensible physical bound.
7. Preserve convergence diagnostics and warnings that affect validity.

For common setup, convergence, units, and dependency problems, use the
[troubleshooting guide](../troubleshooting/index.md).

## Engineering boundary

Examples demonstrate reproducible NeqSim calculations. They do not replace fluid-model
qualification, equipment design, relief or safety verification, standards interpretation, vendor
review, or accountable engineering approval.
