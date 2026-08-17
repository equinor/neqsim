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
- [Package Structure](#package-structure)
- [ProcessSystem](#processsystem)
- [Equipment Categories](#equipment-categories)
- [Controllers and Logic](#controllers-and-logic)
- [Safety Systems](#safety-systems)
- [Basic Usage](#basic-usage)

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

## Package Structure

```
process/
├── SimulationBaseClass.java         # Base class for simulations
├── SimulationInterface.java         # Simulation interface
│
├── equipment/                        # Process equipment
│   ├── ProcessEquipmentBaseClass.java
│   ├── ProcessEquipmentInterface.java
│   ├── TwoPortEquipment.java         # Equipment with inlet/outlet
│   ├── EquipmentFactory.java         # Factory for creating equipment
│   │
│   ├── stream/                       # Streams
│   │   ├── Stream.java
│   │   ├── StreamInterface.java
│   │   ├── EnergyStream.java
│   │   └── VirtualStream.java
│   │
│   ├── separator/                    # Separators
│   │   ├── Separator.java
│   │   ├── ThreePhaseSeparator.java
│   │   ├── GasScrubber.java
│   │   └── SeparatorInterface.java
│   │
│   ├── heatexchanger/               # Heat transfer
│   │   ├── Heater.java
│   │   ├── Cooler.java
│   │   ├── HeatExchanger.java
│   │   ├── NeqHeater.java
│   │   └── Condenser.java
│   │
│   ├── compressor/                  # Compression
│   │   ├── Compressor.java
│   │   ├── CompressorInterface.java
│   │   └── CompressorChartInterface.java
│   │
│   ├── pump/                        # Pumps
│   │   ├── Pump.java
│   │   └── PumpInterface.java
│   │
│   ├── expander/                    # Expanders
│   │   ├── Expander.java
│   │   └── ExpanderInterface.java
│   │
│   ├── valve/                       # Valves
│   │   ├── ThrottlingValve.java
│   │   ├── ValveInterface.java
│   │   └── SafetyValve.java
│   │
│   ├── mixer/                       # Mixers
│   │   ├── Mixer.java
│   │   ├── StaticMixer.java
│   │   └── MixerInterface.java
│   │
│   ├── splitter/                    # Splitters
│   │   ├── Splitter.java
│   │   └── SplitterInterface.java
│   │
│   ├── distillation/                # Distillation
│   │   ├── DistillationColumn.java
│   │   ├── SimpleTray.java
│   │   ├── Condenser.java
│   │   └── Reboiler.java
│   │
│   ├── reactor/                     # Reactors
│   │   ├── Reactor.java
│   │   └── PFReactor.java
│   │
│   ├── absorber/                    # Absorption
│   │   ├── Absorber.java
│   │   └── SimpleTEGAbsorber.java
│   │
│   ├── pipeline/                    # Pipelines
│   │   ├── Pipeline.java
│   │   └── PipelineInterface.java
│   │
│   ├── well/                        # Wells
│   │   ├── SimpleWell.java
│   │   └── WellFlow.java
│   │
│   ├── tank/                        # Tanks and vessels
│   │   ├── Tank.java
│   │   └── ProcessVessel.java
│   │
│   ├── filter/                      # Filters
│   │   └── Filter.java
│   │
│   ├── membrane/                    # Membranes
│   │   └── Membrane.java
│   │
│   ├── ejector/                     # Ejectors
│   │   └── Ejector.java
│   │
│   ├── electrolyzer/                # Electrolyzers
│   │   └── PEM_Electrolyzer.java
│   │
│   └── util/                        # Utility equipment
│       ├── Adjuster.java
│       ├── Recycle.java
│       ├── Calculator.java
│       ├── Setter.java
│       └── MoleFractionSetter.java
│
├── processmodel/                    # Process system
│   ├── ProcessSystem.java
│   ├── ProcessModule.java
│   └── graph/                       # Graph-based execution
│       ├── ProcessGraph.java
│       └── ProcessGraphBuilder.java
│
├── controllerdevice/                # Controllers
│   ├── ControllerDevice.java
│   └── PIDController.java
│
├── measurementdevice/               # Measurements
│   ├── MeasurementDevice.java
│   ├── TemperatureMeasurement.java
│   ├── PressureMeasurement.java
│   └── FlowMeasurement.java
│
├── logic/                           # Process logic
│   ├── ProcessLogicController.java
│   └── ConditionalLogic.java
│
├── alarm/                           # Alarm system
│   └── ProcessAlarmManager.java
│
├── safety/                          # Safety systems
│   ├── PSV/
│   ├── ESD/
│   └── Blowdown/
│
├── calibration/                     # Equipment calibration
├── conditionmonitor/                # Condition monitoring
├── costestimation/                  # Cost estimation
├── mechanicaldesign/                # Mechanical design calculations
│   ├── separator/                   # Separator vessel design
│   ├── compressor/                  # Compressor design (API 617)
│   ├── valve/                       # Valve body, sizing, actuator
│   └── designstandards/             # ASME, API, IEC standards
├── mpc/                             # Model predictive control
├── ml/                              # Machine learning
└── streaming/                       # Data streaming
```

### Mechanical Design Documentation

| Equipment | Documentation | Standards |
|-----------|--------------|-----------|
| Separators | See SeparatorMechanicalDesign | ASME, BS 5500 |
| Compressors | [CompressorMechanicalDesign.md](CompressorMechanicalDesign.md) | API 617, API 672 |
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

## Equipment Categories

### Streams

```java
// Material stream
Stream gas = new Stream("Natural Gas", fluid);
gas.setFlowRate(5000.0, "Sm3/hr");
gas.setTemperature(25.0, "C");
gas.setPressure(100.0, "bara");
gas.run();

// Energy stream
EnergyStream heat = new EnergyStream("Heating Duty");
heat.setEnergyFlow(1000.0, "kW");
```

### Separators

```java
// Two-phase separator
Separator sep2p = new Separator("V-100", inletStream);
sep2p.run();
Stream gas = sep2p.getGasOutStream();
Stream liquid = sep2p.getLiquidOutStream();

// Three-phase separator
ThreePhaseSeparator sep3p = new ThreePhaseSeparator("V-200", inletStream);
sep3p.run();
Stream gas = sep3p.getGasOutStream();
Stream oil = sep3p.getOilOutStream();
Stream water = sep3p.getWaterOutStream();
```

### Heat Exchangers

```java
// Heater (duty specified)
Heater heater = new Heater("E-100", inletStream);
heater.setOutTemperature(80.0, "C");
heater.run();
System.out.println("Duty: " + heater.getDuty() + " W");

// Cooler
Cooler cooler = new Cooler("E-200", inletStream);
cooler.setOutTemperature(30.0, "C");
cooler.run();

// Shell-tube heat exchanger
HeatExchanger hx = new HeatExchanger("E-300", hotStream, coldStream);
hx.setUAvalue(5000.0);  // W/K
hx.run();
```

### Compressors

```java
// Compressor with polytropic efficiency
Compressor comp = new Compressor("K-100", inletStream);
comp.setOutletPressure(80.0, "bara");
comp.setPolytropicEfficiency(0.75);
comp.setUsePolytropicCalc(true);
comp.run();

System.out.println("Power: " + comp.getPower("kW") + " kW");
System.out.println("Outlet T: " + comp.getOutletStream().getTemperature("C") + " °C");
```

### Valves

```java
// Throttling valve (Joule-Thomson)
ThrottlingValve valve = new ThrottlingValve("FV-100", inletStream);
valve.setOutletPressure(50.0, "bara");
valve.run();

// Valve with Cv
valve.setCv(100.0, "US");
valve.setPercentValveOpening(50.0);
```

### Distillation

```java
// Simple distillation column
DistillationColumn column = new DistillationColumn("T-100", 10, true, true);
column.addFeedStream(feedStream, 5);
column.setCondenserTemperature(40.0, "C");
column.setReboilerTemperature(120.0, "C");
column.run();

Stream overhead = column.getGasOutStream();
Stream bottoms = column.getLiquidOutStream();
```

---

## Controllers and Logic

### Adjusters

Adjust a parameter to meet a specification.

```java
// Adjust heater duty to achieve target temperature
Adjuster tempAdjuster = new Adjuster("TC-100");
tempAdjuster.setAdjustedVariable(heater, "duty");
tempAdjuster.setTargetVariable(heater.getOutletStream(), "temperature", 80.0, "C");
process.add(tempAdjuster);
```

### Recycles

Handle recycle loops in the process.

```java
Recycle recycle = new Recycle("Recycle");
recycle.addStream(recycleStream);
recycle.setOutletStream(recycleInletStream);
recycle.setTolerance(1e-6);
process.add(recycle);
```

### Calculators

Use an explicit Java callback; `Calculator` does not parse expression strings. Register the
equipment objects themselves so optimized process execution can derive graph dependencies.

```java
Calculator calc = new Calculator("heater target calculator");
calc.addInputVariable(stream);
calc.setOutputVariable(heater);
calc.setCalculationMethod((inputs, output) -> {
    Stream feed = (Stream) inputs.get(0);
    Heater target = (Heater) output;
    target.setOutTemperature(feed.getTemperature("K") + 10.0, "K");
});
process.add(calc);
```

See [Calculators and setters](equipment/util/calculators.md) for a complete executable example,
preset calculations, recycle-coupling behavior, supported setter targets, and validation
boundaries.

---

## Safety Systems

### Pressure Safety Valves

```java
SafetyValve psv = new SafetyValve("PSV-100", vessel);
psv.setSetPressure(120.0, "bara");
psv.setBlowdownPressure(0.1);  // 10% blowdown
process.add(psv);
```

### Blowdown Systems

See [Safety Simulation Roadmap](../safety/SAFETY_SIMULATION_ROADMAP.md) for detailed safety system documentation.

---

## Dynamic Simulation

```java
// Run transient simulation
double simulationTime = 3600.0;  // 1 hour
double timeStep = 1.0;           // 1 second

process.setTimeStep(timeStep);
for (double t = 0; t < simulationTime; t += timeStep) {
    process.runTransient();

    // Log data
    System.out.println(t + ", " +
        separator.getPressure() + ", " +
        separator.getGasOutStream().getFlowRate("kg/hr"));
}
```

---

## Process Reports

After a successful run, use `getReport_json()` for the structured process report and
`getStreamSummaryTable()` for a formatted stream summary. The legacy `reportResults()` aggregator
assumes that every equipment type supplies a non-null row array and is not safe as a general
flowsheet reporting contract. The complete quick start executes both supported APIs. Treat
`display()` as an interactive console helper rather than a machine-readable result contract.

---

---

## Best Practices

1. **Use unique names** for all equipment
2. **Set flow rate and conditions** before running
3. **Add equipment in flow order** for clarity
4. **Use Recycle** for recycle loops
5. **Check mass balance** after simulation
6. **Clone streams** before branching to avoid shared state

---

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
