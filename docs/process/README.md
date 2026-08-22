---
title: Process Simulation Package
description: The `process` package provides process equipment, unit operations, controllers, and process system management for building complete flowsheets.
---

The `process` package provides process equipment, unit operations, controllers, and process system management for building complete flowsheets.

For controlled design cases, equipment and discipline sizing, safety verification, engineering deliverables, and
handover, use the separate [Engineering documentation](../engineering/).

## Table of Contents

- [Overview](#overview)
- [Documentation Structure](#documentation-structure)
- [Package Architecture](#package-architecture)
- [ProcessSystem](#processsystem)
- [Choosing Equipment](#choosing-equipment)
- [Specifications, Recycles, and Calculators](#specifications-recycles-and-calculators)
- [Transient and Safety Boundaries](#transient-and-safety-boundaries)
- [Result Handling](#result-handling)
- [Best Practices](#best-practices)


---

## Overview

**Location:** `neqsim.process`

**Purpose:**
- Model process equipment (separators, heat exchangers, compressors, etc.)
- Build process flowsheets with `ProcessSystem`
- Implement control logic and adjusters
- Simulate dynamic and steady-state processes
- Safety system modeling (PSV, ESD, blowdown)

---

## Documentation Structure

This documentation is organized into the following sections:

| Section | Description |
|---------|-------------|
| [equipment/](equipment/) | Equipment documentation (separators, compressors, etc.) |
| [equipment/adsorption_bed.md](equipment/adsorption_bed.md) | **Adsorption bed** — transient simulation, LDF mass transfer, PSA/TSA cycles |
| [lng_liquefaction.md](lng_liquefaction.md) | **LNG liquefaction** — closed-loop SMR, C3MR, DMR, and nitrogen-expander templates with common KPIs and literature screening |
| [mercury_removal.md](mercury_removal.md) | **Mercury removal guard beds** — chemisorption (PuraSpec), bed loading, breakthrough, degradation, mechanical design, cost |
| [bioprocessing.md](bioprocessing.md) | **Bio-processing** — reactors, fermenters, solid-liquid separators, LLE, evaporators, dryers, crystallizers |
| [neqsim-studio.md](neqsim-studio.md) | **NeqSim Studio (Python)** — newcomer-friendly process builder: natural language, templates, guided wizard, edit-by-chat, recipe gallery |
| [processmodel/](processmodel/) | ProcessSystem and flowsheet management |
| [energy_streams.md](energy_streams.md) | **Energy streams** — typed heat, shaft-work, and electrical ports, unit-aware duties, graph ordering, and energy-driven equipment |
| [process_json_export_and_e300_fluids.md](process_json_export_and_e300_fluids.md) | **Process JSON export** — self-contained ProcessSystem/ProcessModel JSON for MCP, including E300-equivalent component properties and volume correction |
| [simulation-hooks-and-events.md](simulation-hooks-and-events.md) | **Lifecycle hooks, event bus, auto-validation** for ProcessSystem and ProcessModel |
| [model-change-events.md](model-change-events.md) | **Governed model revisions** — versioned change events, idempotent publication, fingerprints, and durable replay |
| [model-impact-analysis.md](model-impact-analysis.md) | **Cross-model impact analysis** — configurable propagation rules, recalculation order, cycle detection, and reapproval work |
| [safety/](safety/) | Safety systems (PSV, ESD, blowdown) |
| [controllers.md](controllers.md) | Process controllers and logic |
| [unisim-to-neqsim-conversion.md](unisim-to-neqsim-conversion.md) | **UniSim/HYSYS conversion** — convert `.usc` models to NeqSim with E300 full-fluid transfer and export back to UniSim |
| [piping_route_builder.md](piping_route_builder.md) | **STID/E3D line-list piping route builder** — convert route tables into serial Beggs-and-Brill hydraulic models and water-hammer screening handoffs |
| [water_hammer_implementation.md](../wiki/water_hammer_implementation.md) | **Water hammer/liquid hammer screening** — fast valve closure, pump trip, STID route, tagreader event, and MCP runWaterHammer workflow |
| [operational_evidence_package.md](operational_evidence_package.md) | **Operational evidence package** — combine P&ID/STID references, tagreader values, scenario actions, and bottleneck detection |
| [exergy-analysis.md](exergy-analysis.md) | **Exergy analysis** — plant-wide destruction hotspots for ProcessSystem and ProcessModel |
| [production-allocation.md](production-allocation.md) | **Production allocation** — back-allocate metered production to wells/sources via a linear recovery-factor proxy network (handles recycle/reflux) |
| [k-value-fast-simulation.md](k-value-fast-simulation.md) | **Cached K-value fast simulation** — run one rigorous base-case process, freeze separator K-values and fallback splits, then execute fast source-rate scenarios without repeated EOS flashes |
| [screening_calculators.md](screening_calculators.md) | **Screening and sizing calculators** — flare radiation (API 521), line sizing/erosion LOF (API RP 14E), flow-induced vibration (AVIFF), pump NPSH, control-valve sizing/noise (IEC 60534), thermowell strength (ASME PTC 19.3), pipeline overpressure protection, orifice metering (GPSA), and crude desalting; with optional process-object bridges |

### Process Design Guide

| Document | Description |
|----------|-------------|
| [process_design_guide.md](process_design_guide.md) | **Complete guide to process design workflow using NeqSim** |

### Design Framework (NEW) ✨

| Document | Description |
|----------|-------------|
| [DESIGN_FRAMEWORK.md](DESIGN_FRAMEWORK.md) | **Automated equipment sizing and optimization framework** |

**Key Features:**
- `AutoSizeable` interface - Equipment auto-sizing from flow requirements
- `DesignSpecification` - Builder pattern for equipment configuration
- `ProcessTemplate` - Reusable process configurations
- `DesignOptimizer` - Design-to-optimization workflow
- Integration with MechanicalDesign and company TR documents

### Optimization and Constraints Framework (NEW) ✨

| Document | Description |
|----------|-------------|
| [optimization/OPTIMIZATION_AND_CONSTRAINTS.md](optimization/OPTIMIZATION_AND_CONSTRAINTS.md) | **COMPREHENSIVE: Complete guide to optimization algorithms, constraint types, bottleneck analysis** |
| [optimization/OPTIMIZATION_OVERVIEW.md](optimization/OPTIMIZATION_OVERVIEW.md) | When to use which optimizer |
| [optimization/process-researcher.md](optimization/process-researcher.md) | **Process researcher** - generate and rank candidate flowsheets from feed/product targets, including reaction routes |
| [CAPACITY_CONSTRAINT_FRAMEWORK.md](CAPACITY_CONSTRAINT_FRAMEWORK.md) | Equipment capacity limits and utilization tracking |

**Key Features:**
- Five search algorithms: Binary, Golden-Section, Nelder-Mead, Particle Swarm, Gradient Descent
- Multi-constraint support per equipment (speed, power, surge, K-factor, etc.)
- Constraint types: HARD (trip/damage), SOFT (efficiency loss), DESIGN (normal envelope)
- Constraint severity: CRITICAL, HARD, SOFT, ADVISORY
- Automated bottleneck detection with `ProcessSystem.findBottleneck()`
- Multi-objective Pareto optimization
- External optimizer integration (Python/SciPy via `ProcessSimulationEvaluator`)

### Corrosion Analysis ✨

| Document | Description |
|----------|-------------|
| [corrosion/](corrosion/) | **Corrosion analysis module overview** — NORSOK M-506 CO2 corrosion rate + NORSOK M-001 material selection |
| [corrosion/norsok_m506_corrosion_rate.md](corrosion/norsok_m506_corrosion_rate.md) | **NORSOK M-506 API** — CO2 corrosion rate prediction with fugacity, pH, correction factors |
| [corrosion/norsok_m001_material_selection.md](corrosion/norsok_m001_material_selection.md) | **NORSOK M-001 API** — Material grade recommendation, sour service, chloride SCC |
| [corrosion/pipeline_corrosion_integration.md](corrosion/pipeline_corrosion_integration.md) | **Pipeline integration** — Automated corrosion analysis from process simulation |

### Electrical Design Documentation

| Document | Description |
|----------|-------------|
| [electrical-design.md](electrical-design.md) | **Electrical design framework — motor sizing, VFD, cable, transformer, switchgear, hazardous area, equipment-specific designs, plant-wide load analysis** |

### Dynamic Simulation

| Document | Description |
|----------|-------------|
| [dynamic-simulation.md](dynamic-simulation.md) | **Dynamic simulation helper — auto-instruments a sized steady-state process with transmitters and PID controllers for transient simulation** |
| [agent-rca-dynamic-fault-benchmark.md](agent-rca-dynamic-fault-benchmark.md) | **AgentRCA dynamic fault benchmark — normal-only evidence and ranked diagnoses for controlled sensor bias, gas leaks, blockage, and imposed multiphase slugging excitation** |

**Key Features:**
- `DynamicProcessHelper` — one-call conversion from steady-state to dynamic
- Auto-creates PT, LT, TT transmitters per equipment type
- Auto-wires PID controllers (PC, LC, WLC) to downstream valves
- Configurable PID tuning with sensible defaults
- Convenience methods for flow and temperature control loops

### Instrument Design Documentation

| Document | Description |
|----------|-------------|
| [instrument-design.md](instrument-design.md) | **Instrument design framework — ISA-5.1 identification, SIL-rated safety instruments, I/O counting, DCS/SIS cabinet sizing, cost estimation, equipment-specific designs (separator, compressor, heat exchanger, pipeline, valve), plant-wide SystemInstrumentDesign** |

**Key Features:**
- Equipment-specific designs: Compressor, Pump, Separator, Heater/Cooler, Pipeline
- `SystemElectricalDesign` for plant-wide load aggregation, transformer and generator sizing
- IEC standards: 60034 (motors), 60502 (cables), 60076 (transformers), 61439 (switchgear)
- `ProcessSystem.getSystemElectricalDesign()` for one-call plant electrical summary

### Mechanical Design Documentation

| Document | Description |
|----------|-------------|
| [EQUIPMENT_DESIGN_PARAMETERS.md](EQUIPMENT_DESIGN_PARAMETERS.md) | **Equipment design parameters, autoSize vs MechanicalDesign guide** |
| [mechanical_design_standards.md](mechanical_design_standards.md) | Design standards (NORSOK, ASME, API, DNV, etc.) |
| [process_design_standards_program.md](process_design_standards_program.md) | Standards priorities, evidence gates, requirement coverage, and change control |
| [standard_design_kernel_migration.md](standard_design_kernel_migration.md) | Migrate global editions, metadata factories, mutable calculators, and legacy case execution to typed kernels |
| [mechanical_design_database.md](mechanical_design_database.md) | Data sources, database schemas, and CSV configuration |
| [pipeline_mechanical_design.md](pipeline_mechanical_design.md) | Pipeline mechanical design (wall thickness, stress, buckling, corrosion) |
| [dnv_rp_f109_on_bottom_stability.md](dnv_rp_f109_on_bottom_stability.md) | **DNV-RP-F109 on-bottom stability screening** — typed vertical, transparent absolute-static lateral, and external-response displacement checks with fail-closed readiness |
| [dnv_st_f101_pipeline_screening.md](dnv_st_f101_pipeline_screening.md) | **Fail-closed DNV-ST-F101:2021 pipeline limit-state screening with explicit review boundary** |
| [topside_piping_design.md](topside_piping_design.md) | **Topside piping design (velocity, support, vibration per ASME B31.3)** |
| [riser_mechanical_design.md](riser_mechanical_design.md) | Riser design (catenary, VIV, fatigue per DNV-OS-F201) |
| [well_mechanical_design.md](well_mechanical_design.md) | **Well casing/tubing design, barrier verification, cost estimation per NORSOK D-010, API 5CT** |
| [torg_integration.md](torg_integration.md) | Technical Requirements Documents (TORG) integration |
| [field_development_orchestration.md](field_development_orchestration.md) | Complete design workflow orchestration |
| [mechanical_design/two_phase_heat_transfer.md](mechanical_design/two_phase_heat_transfer.md) | **Two-phase heat transfer — Shah condensation, Chen/Gungor-Winterton boiling, Friedel/MSH pressure drop, Ebert-Panchal fouling, incremental zone analysis, tube inserts** |

### Cost Estimation Framework (NEW) ✨

| Document | Description |
|----------|-------------|
| [COST_ESTIMATION_FRAMEWORK.md](COST_ESTIMATION_FRAMEWORK.md) | **Comprehensive capital and operating cost estimation, including scope-safe result reconciliation** |
| [COST_ESTIMATION_API_REFERENCE.md](COST_ESTIMATION_API_REFERENCE.md) | **Detailed API reference for cost estimation classes and CostEstimateResult output maps** |

**Key Features:**
- Equipment cost estimation using Turton et al. correlations
- Support for 14+ equipment types (separators, compressors, heat exchangers, tanks, expanders, ejectors, absorbers, etc.)
- Topsides, SURF, subsea, well, and process-level CAPEX rollups
- Multi-currency support (USD, EUR, NOK, GBP, CNY, JPY)
- Location factors for 11 global regions
- Operating cost (OPEX) calculation with utility costs
- Financial metrics (payback period, ROI, NPV)
- Process-level cost aggregation with located/base equipment rows and report-ready `CostEstimateResult` JSON export

### Equipment Categories

| Category | Documentation | Classes |
|----------|--------------|---------|
| Streams | [streams.md](equipment/streams.md) | Stream, EnergyStream, VirtualStream |
| Separators | [separators.md](equipment/separators.md) | Separator, ThreePhaseSeparator, GasScrubber |
| Heat Exchangers | [heat_exchangers.md](equipment/heat_exchangers.md) | Heater, Cooler, HeatExchanger |
| Compressors | [compressors.md](equipment/compressors.md) | Compressor, CompressorChart, CompressorWashing |
| Compressor Deposit / Degradation | [compressor_deposit_degradation.md](compressor_deposit_degradation.md) | CompressorDeposit, DepositMechanism, DepositSource, CompressorDepositProfile, WashFluid, CompressorDepositWash |
| Compressor Anti-Surge Control | [compressor_antisurge_control.md](equipment/compressor_antisurge_control.md) | AntiSurgeController, CompressorMonitor, ThrottlingValve, Recycle |
| Pumps | [pumps.md](equipment/pumps.md) | Pump, PumpChart |
| Expanders | [expanders.md](equipment/expanders.md) | Expander, TurboExpanderCompressor |
| Valves | [valves.md](equipment/valves.md) | ThrottlingValve, SafetyValve, BlowdownValve |
| **Choke Collapse Analysis** | [choke-collapse.md](choke-collapse.md) | ChokeCollapseAnalyzer — critical pressure ratio, flashing, cavitation |
| **Inadvertent Valve Operation** | [inadvertent-valve-operation.md](inadvertent-valve-operation.md) | InadvertentValveOperationAnalyzer — API 521 §4.4.13 / NORSOK P-002 IVO screening |
| **Well Chokes** | [well_choke_implementation.md](well_choke_implementation.md) | Sachdeva, Gilbert choke models, ThrottlingValve integration |
| Distillation | [distillation.md](equipment/distillation.md) | DistillationColumn, SimpleTray |
| Absorbers | [absorbers.md](equipment/absorbers.md) | SimpleAbsorber, SimpleTEGAbsorber |
| Ejectors | [ejectors.md](equipment/ejectors.md) | Ejector |
| Membranes | [membranes.md](equipment/membranes.md) | MembraneSeparator |
| Flares | [flares.md](equipment/flares.md) | Flare, FlareStack |
| Electrolyzers | [electrolyzers.md](equipment/electrolyzers.md) | Electrolyzer, CO2Electrolyzer |
| Filters | [filters.md](equipment/filters.md) | Particle, coalescing, strainer, and media filters with dynamic loading and mechanical design |
| H2S Scavengers | [H2S_scavenger_guide.md](H2S_scavenger_guide.md) | H2S chemical scavenging (triazine, glyoxal, iron sponge) |
| **Sulfur Recovery** | [sulfur_recovery.md](sulfur_recovery.md) | Integrated Claus furnace, WHB, converters, sulfur condensers, TGTU recycle, incineration, and KPIs |
| Reactors | [reactors.md](equipment/reactors.md) | GibbsReactor |
| Pipelines | [pipelines.md](equipment/pipelines.md) | Pipeline, AdiabaticPipe, TopsidePiping, Riser |
| **Water Hammer Screening** | [water_hammer_implementation.md](../wiki/water_hammer_implementation.md) | WaterHammerPipe, WaterHammerStudy, MCP runWaterHammer |
| **Piping Route Builder** | [piping_route_builder.md](piping_route_builder.md) | PipingRouteBuilder for STID/E3D line-list route hydraulics |
| **CO2 Well Analysis** | [co2_injection_well_analysis.md](co2_injection_well_analysis.md) | CO2InjectionWellAnalyzer, ImpurityMonitor, TransientWellbore, CO2FlowCorrections |
| **LNG Liquefaction** | [lng_liquefaction.md](lng_liquefaction.md) | LNGProcessBuilder, LNGProcessModel, LNGProcessBenchmark, LNGHeatExchanger |
| **Hydrogen Production** | [hydrogen_production.md](hydrogen_production.md) | SMR/ATR/POX route templates, ReformerFurnace, CatalyticTubeReformer, AutothermalReformer, PartialOxidationReactor, PSACascade, Electrolyzer |
| Looped Networks | [looped_networks.md](equipment/looped_networks.md) | LoopedPipeNetwork, Hardy Cross solver |
| Gas Network Operations | [gas_network_operations.md](gas_network_operations.md) | Conservative mixing, coupled hydraulics, quality, optimization, and linepack |
| Oil Network Operations | [oil_network_operations.md](oil_network_operations.md) | Pumps, assays, tanks, parcels, blends, and cargo scheduling |
| Tanks | [tanks.md](equipment/tanks.md) | Tank, VesselDepressurization |
| Wells | [wells.md](equipment/wells.md) | Well equipment |
| Subsea Trees | [subsea_trees.md](equipment/subsea_trees.md) | SubseaTree, valve control |
| Subsea Manifolds | [subsea_manifolds.md](equipment/subsea_manifolds.md) | SubseaManifold |
| Subsea Boosters | [subsea_boosters.md](equipment/subsea_boosters.md) | SubseaBooster, multiphase pumps |
| Umbilicals | [umbilicals.md](equipment/umbilicals.md) | Umbilical systems |
| Battery Storage | [battery_storage.md](equipment/battery_storage.md) | BatteryStorage |
| **Heat Integration** | [heat_integration.md](equipment/heat_integration.md) | PinchAnalysis, HeatStream |
| **Power Generation** | [power_generation.md](equipment/power_generation.md) | GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem, FuelCell, WindTurbine, SolarPanel |
| Failure Modes | [failure_modes.md](equipment/failure_modes.md) | EquipmentFailureMode, reliability |
| Mixers/Splitters | [mixers_splitters.md](equipment/mixers_splitters.md) | Mixer, Splitter |
| Utility | [util/](equipment/util/) | Adjuster, Recycle, Calculator |

---

## Package Architecture

The process package separates common simulation behavior from equipment-specific stream topology.
Use the narrowest API that represents the operation being modeled.

| Responsibility | Current API | Boundary |
|---|---|---|
| Simulation lifecycle | `SimulationInterface`, `SimulationBaseClass` | Execution identity, run state, and common simulation behavior |
| Equipment contract | `ProcessEquipmentInterface` | Reporting, mechanical design, validation, fluid access, capacity, and common operating properties |
| Shared equipment implementation | `ProcessEquipmentBaseClass` | Common state and default implementations; it does not imply one inlet and one outlet |
| One-inlet/one-outlet equipment | `TwoPortInterface`, `TwoPortEquipment` | Single-stream inlet/outlet methods used by heaters, compressors, pumps, and throttling valves |
| Multiport equipment | Equipment-specific classes | Separators, mixers, splitters, and columns expose topology-specific stream methods |
| Flowsheet orchestration | `ProcessSystem` | Named units, topology, execution strategy, convergence, reporting, and transient time |
| Controls and measurements | `controllerdevice`, `measurementdevice`, `logic` | Controllers, sensors, alarms, and process logic |

See the [equipment index](equipment/README.md) for the current category map and
[ProcessSystem guide](processmodel/process_system.md) for orchestration details.
API 672 |
| Valves | [ValveMechanicalDesign.md](ValveMechanicalDesign.md) | IEC 60534, ANSI/ISA-75, ASME B16.34 |

---


## ProcessSystem

The `ProcessSystem` class is the container for building and running process flowsheets.

### Basic Usage

The following complete Java 8 program builds and runs a valve-and-separator flowsheet. `run()` is
the normal entry point; with the default settings it delegates to the topology-aware optimized
dispatcher and falls back conservatively when parallel execution is not suitable.

```java
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public final class ProcessSystemQuickStart {
  private ProcessSystemQuickStart() {}

  public static void main(String[] args) {
    SystemInterface fluid = new SystemSrkEos(300.0, 80.0);
    fluid.addComponent("methane", 0.85);
    fluid.addComponent("ethane", 0.08);
    fluid.addComponent("propane", 0.05);
    fluid.addComponent("n-butane", 0.02);
    fluid.setMixingRule("classic");

    Stream feed = new Stream("feed", fluid);
    feed.setFlowRate(1000.0, "kg/hr");

    ThrottlingValve valve = new ThrottlingValve("inlet valve", feed);
    valve.setOutletPressure(40.0, "bara");

    Separator separator = new Separator("HP separator", valve.getOutletStream());

    ProcessSystem process = new ProcessSystem("gas processing plant");
    process.add(feed);
    process.add(valve);
    process.add(separator);
    process.run();

    double inletMassFlowKgPerHr = feed.getFlowRate("kg/hr");
    double gasMassFlowKgPerHr = separator.getGasOutStream().getFlowRate("kg/hr");
    double liquidMassFlowKgPerHr =
        separator.getLiquidOutStream().getFlowRate("kg/hr");
    double relativeMassBalanceError =
        Math.abs(
                inletMassFlowKgPerHr
                    - gasMassFlowKgPerHr
                    - liquidMassFlowKgPerHr)
            / inletMassFlowKgPerHr;

    if (!(gasMassFlowKgPerHr > 0.0)
        || relativeMassBalanceError > 1.0e-6) {
      throw new IllegalStateException("Invalid separator result");
    }

    if (process.hasRecycleLoops()
        || process.getExecutionPartitionInfo().isEmpty()
        || process.getReport_json().isEmpty()
        || process.getStreamSummaryTable().isEmpty()) {
      throw new IllegalStateException("Incomplete process diagnostics");
    }
  }
}
```

### Execution Strategies

Use `run()` for normal simulations. By default, it delegates to `runOptimized()`, which selects a
strategy from the current topology:

- adjusters force sequential execution because their feedback is not represented by the graph;
- recycle systems use the hybrid iterative path;
- feed-forward systems use parallel or dataflow execution when their topology makes that safe;
- unsupported or interrupted optimized paths fall back to sequential execution.

`runParallel()` is a lower-level API, throws `InterruptedException`, and should be called directly
only when the caller deliberately owns interruption handling. The public hybrid entry point is
`runHybrid(UUID)`, not a no-argument method. Do not assume a fixed speedup: execution time depends
on topology, unit-operation cost, recycle behavior, runtime, and hardware. Benchmark representative
cases and confirm that results match the sequential baseline.

### Analyze Process Topology

Use `hasRecycleLoops()` to detect cycles and `getExecutionPartitionInfo()` to inspect the
topology-derived execution plan. The complete quick start above executes both calls.

### Key ProcessSystem Methods

| Method | Description |
|--------|-------------|
| `add(equipment)` | Add equipment to the process |
| `run()` | Run using the configured default; optimized dispatch is enabled by default |
| `runOptimized()` | Select a conservative strategy from the current topology |
| `runParallel()` | Run independent graph levels in parallel; throws `InterruptedException` |
| `runHybrid(UUID)` | Low-level hybrid execution for recycle-containing systems |
| `runTransient()` | Advance one transient step using the configured time step |
| `getUnit(name)` | Get equipment by name |
| `hasRecycleLoops()` | Check whether the process graph contains cycles |
| `getExecutionPartitionInfo()` | Describe the current execution partition |
| `copy()` | Deep-copy the process system |
| `getReport_json()` | Generate the structured JSON report |
| `getStreamSummaryTable()` | Return a formatted stream-property table |

---

## Choosing Equipment

The [equipment index](equipment/README.md) is the authoritative navigation page for individual
unit-operation guides. The complete quick start above is the canonical package example; individual
guides add equipment-specific constructors, specifications, units, and validation.

Stream topology determines which accessor is valid:

| Topology | Examples | Access pattern |
|---|---|---|
| One inlet, one outlet | Heater, compressor, pump, throttling valve | `TwoPortEquipment.getInletStream()` and `getOutletStream()` |
| Multiple outlets | Separator, splitter | Equipment-specific getters such as `Separator.getGasOutStream()` and `getLiquidOutStream()` |
| Multiple inlets | Mixer, separator | Equipment-specific `addStream(...)` or inlet-list APIs |
| Staged equipment | Distillation column, absorber | Stage/feed APIs plus equipment-specific product streams |
| Utility graph node | Recycle, adjuster, calculator | Declared input/output dependencies rather than a universal material outlet |

Do not cast the result of `ProcessSystem.getUnit(name)` until the expected equipment type has
been established. Prefer retaining a typed reference when constructing the flowsheet.

## Specifications, Recycles, and Calculators

- Use [adjusters](equipment/util/adjusters.md) for a bounded variable-to-target solve.
- Use [recycles](equipment/util/recycles.md) to close material loops and document the convergence
  variable, tolerance, and maximum iterations.
- Use [calculators and setters](equipment/util/calculators.md) for explicit Java callbacks and
  supported setter targets. `Calculator` does not parse expression strings.
- Add each utility object to the same `ProcessSystem` as its dependencies so topology and
  convergence logic can account for it.

## Transient and Safety Boundaries

A steady-state flowsheet is not automatically a valid dynamic model. Before advancing time:

1. converge and validate the steady-state case;
2. confirm that each participating unit implements the required transient state and holdup;
3. choose a time step appropriate for the fastest modeled response;
4. configure controllers, events, and boundary conditions explicitly;
5. call `runTransient(double, UUID)` when the caller needs explicit step duration and calculation
   identity, or the configured no-argument helper when that behavior is intentional;
6. capture results through reports, monitors, or callbacks rather than console printing.

See [dynamic simulation](dynamic-simulation.md) for instrumentation and controller setup.

A `SafetyValve` is constructed from a `StreamInterface`, not a vessel object, and its current
set-pressure method is `setPressureSpec(double)`; there is no `setSetPressure(...)` API.
Pressure-relief simulation is not design certification. Use the [valve guide](equipment/valves.md)
and [safety roadmap](../safety/SAFETY_SIMULATION_ROADMAP.md), record units and scenarios explicitly,
and obtain the required engineering review.

## Result Handling

After a successful run:

- use `getReport_json()` for a structured process report;
- use `getStreamSummaryTable()` for a formatted stream-property table;
- use equipment-specific getters for engineering quantities and always supply units where the API
  accepts them;
- check conservation and operating constraints before interpreting results.

The legacy `reportResults()` aggregator assumes every equipment type supplies a non-null row
array and is not a general flowsheet reporting contract. Treat interactive display helpers as
diagnostic UI, not serialized evidence.

## Best Practices

1. Give every stream, equipment object, controller, and measurement a stable unique name.
2. Set feed composition, flow basis, temperature, and pressure with explicit units.
3. Retain typed object references and add units to the `ProcessSystem` in readable flow order.
4. Use graph-aware recycle, adjuster, and calculator objects instead of manual outer loops.
5. Validate mass and energy balances, convergence, phase behavior, and operating constraints.
6. Clone a stream before intentional branching when downstream cases must not share mutable state.
7. Preserve the `ProcessSystem`, named streams, calculation identity, and structured reports when
   the model will be serialized, restarted, or embedded in a larger workflow.


## Future Infrastructure

NeqSim includes foundational infrastructure to support the future of process simulation:

| Capability | Documentation | Description |
|------------|---------------|-------------|
| **Lifecycle Management** | [lifecycle/](lifecycle/) | Model versioning, state export/import, lifecycle tracking |
| **Emissions Tracking** | [sustainability/](sustainability/) | CO2e accounting, regulatory reporting |
| **Advisory Systems** | [advisory/](advisory/) | Look-ahead predictions with uncertainty |
| **ML Integration** | [ml/](ml/) | Surrogate models, physics constraint validation |
| **Safety Scenarios** | [safety/scenario-generation.md](safety/scenario-generation.md) | Automatic failure scenario generation |
| **Batch Studies** | [optimization/batch-studies.md](optimization/batch-studies.md) | Parallel parameter studies |

See [Future Infrastructure Overview](future-infrastructure.md) for complete documentation.

---

## Related Documentation

- [Equipment Documentation](equipment/) - Detailed equipment guides
- [Process Logic Framework](../simulation/process_logic_framework.md) - Logic controllers
- [Safety Systems](../safety/SAFETY_SIMULATION_ROADMAP.md) - Safety simulation
- [Alarm System](../safety/alarm_system_guide.md) - Process alarms
- [Future Infrastructure](future-infrastructure.md) - Digital twin, AI integration, sustainability
