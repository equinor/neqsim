---
title: Process Equipment Documentation
description: Source-safe navigation and API ownership for process equipment in NeqSim.
---

Use this index to choose an equipment-specific guide. For an executable, end-to-end flowsheet,
start with the [process-package quick start](../README.md); it is compiled and run by the
documentation regression suite.

## Equipment Categories

### Flow Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Streams | [streams.md](streams.md) | Material and energy streams |
| Mixers & Splitters | [mixers_splitters.md](mixers_splitters.md) | Stream mixing and splitting |

### Separation Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Separators | [separators.md](separators.md) | 2-phase and 3-phase separators, scrubbers |
| Distillation | [distillation.md](distillation.md) | Distillation columns |
| Absorbers | [absorbers.md](absorbers.md) | Absorption/stripping columns |
| Membranes | [membranes.md](membranes.md) | Membrane separation units |
| Filters | [filters.md](filters.md) | Particulate and charcoal filters |

### Heat Transfer Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Heat Exchangers | [heat_exchangers.md](heat_exchangers.md) | Heaters, coolers, condensers, reboilers |

### Rotating Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Compressors | [compressors.md](compressors.md) | Gas compression, mechanical losses, seal gas |
| Compressor Anti-Surge Control | [compressor_antisurge_control.md](compressor_antisurge_control.md) | Dynamic anti-surge recycle, speed/load control, and coordinated pressure-speed-recycle control philosophy |
| Pumps | [pumps.md](pumps.md) | Liquid pumping |
| Expanders | [expanders.md](expanders.md) | Power recovery, turboexpanders |

### Flow Control

| Equipment | File | Description |
|-----------|------|-------------|
| Valves | [valves.md](valves.md) | Throttling valves, chokes, safety valves |

### Reactors

| Equipment | File | Description |
|-----------|------|-------------|
| Reactors (Overview) | [reactors.md](reactors.md) | All reactor types: PFR, CSTR, Gibbs, stoichiometric, ammonia, sulfur, bio-processing |
| Iron-Sulfide Wall Source | [iron_sulfide_wall_source.md](iron_sulfide_wall_source.md) | Stateful FeS/FeCO3 scale, oxygen ingress, S8 generation, and compressor deposition coupling |
| Plug Flow Reactor | [plug_flow_reactor.md](plug_flow_reactor.md) | Kinetic PFR with power-law/LHHW/reversible kinetics, catalyst bed, Ergun ΔP, energy modes |
| Electrolyzers | [electrolyzers.md](electrolyzers.md) | Water and CO₂ electrolysis |

### Ejectors

| Equipment | File | Description |
|-----------|------|-------------|
| Ejectors | [ejectors.md](ejectors.md) | Steam and gas ejectors |

### Safety Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Flares | [flares.md](flares.md) | Flare systems and combustion |

### Well/Reservoir

| Equipment | File | Description |
|-----------|------|-------------|
| Wells | [wells.md](wells.md) | Production wells, chokes |
| Reservoirs | [reservoirs.md](reservoirs.md) | Material balance reservoir modeling |
| Subsea Systems | [subsea_systems.md](subsea_systems.md) | Subsea wells and flowlines |

### Subsea Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Subsea Trees | [subsea_trees.md](subsea_trees.md) | Christmas trees, valve control |
| Subsea Manifolds | [subsea_manifolds.md](subsea_manifolds.md) | Multi-slot production manifolds |
| Subsea Boosters | [subsea_boosters.md](subsea_boosters.md) | Multiphase pumps, compressors |
| Umbilicals | [umbilicals.md](umbilicals.md) | Hydraulic, chemical, electrical supply |

### Pipeline/Network

| Equipment | File | Description |
|-----------|------|-------------|
| Pipelines | [pipelines.md](pipelines.md) | Pipe flow, pressure drop |
| **Risers** | [pipelines.md#risers](pipelines.md#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Networks | [networks.md](networks.md) | Pipeline network modeling |
| Looped Networks | [looped_networks.md](looped_networks.md) | Hardy Cross solver for loops |
| Gas Network Operations | [../gas_network_operations.md](../gas_network_operations.md) | Coupled gas quality, optimization, and linepack |
| Oil Network Operations | [../oil_network_operations.md](../oil_network_operations.md) | Oil pumps, terminal tanks, parcels, and cargo schedules |
| Manifolds | [manifolds.md](manifolds.md) | Multi-stream routing |

### Flow Measurement

| Equipment | File | Description |
|-----------|------|-------------|
| Differential Pressure | [differential_pressure.md](differential_pressure.md) | Orifice plates, flow measurement |

### Storage

| Equipment | File | Description |
|-----------|------|-------------|
| Tanks | [tanks.md](tanks.md) | Storage tanks, LNG boil-off |
| Vessel Depressurization | [vessel_depressurization.md](vessel_depressurization.md) | Filling, blowdown, fire cases, CNG/H2 tanks, heat transfer models |

### Gas Treatment

| Equipment | File | Description |
|-----------|------|-------------|
| Adsorbers | [adsorbers.md](adsorbers.md) | CO₂ and gas adsorption |

### Power Generation

| Equipment | File | Description |
|-----------|------|-------------|
| Power Equipment | [power_generation.md](power_generation.md) | Gas turbines, fuel cells, renewables |
| Battery Storage | [battery_storage.md](battery_storage.md) | Energy storage systems |

### Utility Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Adjusters | [util/adjusters.md](util/adjusters.md) | Variable adjustment to meet specs |
| Recycles | [util/recycles.md](util/recycles.md) | Recycle stream handling |
| Calculators | [util/calculators.md](util/calculators.md) | Custom calculations and setters |

### Reliability & Failure

| Equipment | File | Description |
|-----------|------|-------------|
| Failure Modes | [failure_modes.md](failure_modes.md) | Equipment failure modeling for risk analysis |


## API Ownership

All process equipment implements `ProcessEquipmentInterface`, normally through
`ProcessEquipmentBaseClass`. That common contract covers lifecycle, reporting, validation,
mechanical design, capacity, fluid access, and common operating properties. It does **not** give
every unit a single material inlet and outlet.

| API owner | Use it for | Representative equipment |
|---|---|---|
| `ProcessEquipmentInterface` | Common execution, reporting, design, validation, capacity, and fluid access | All process equipment |
| `TwoPortInterface` / `TwoPortEquipment` | One material inlet and one material outlet | Heater, cooler, compressor, pump, expander, throttling valve |
| Equipment-specific multiport API | Multiple feeds, products, or stages | Separator, mixer, splitter, distillation column, absorber |
| Utility equipment API | Graph dependencies, targets, and convergence | Recycle, adjuster, calculator |

For example, `TwoPortEquipment` owns `getInletStream()` and `getOutletStream()`.
`Separator` instead exposes gas and liquid product streams and can accept multiple feeds.
A mixer, splitter, or staged column likewise needs its own guide; do not assume the two-port
constructor or accessors.

## Constructing and Running Equipment

There is no universal `EquipmentType(name, inletStream)` constructor or `setParameter(value)`
method. Follow the selected guide and current Java source for that equipment's:

1. constructor and stream topology;
2. specification methods and units;
3. required thermodynamic or physical-property initialization;
4. steady-state or transient execution boundary;
5. equipment-specific results and validation.

Add streams and equipment to one `ProcessSystem`, retain typed references, run the process, and
check conservation plus equipment constraints. The
[canonical process-package example](../README.md#processsystem) demonstrates this sequence with a
feed, throttling valve, separator, mass-balance assertion, topology diagnostics, and structured
reports.

`ProcessSystem.getUnit(name)` returns the common equipment interface. Cast only after verifying
the expected type, or retain the typed reference created while building the process.

## Equipment Hierarchy

```text
ProcessEquipmentInterface
    └── ProcessEquipmentBaseClass
        ├── TwoPortEquipment implements TwoPortInterface
        │   ├── Heater / Cooler
        │   ├── Compressor / Pump / Expander
        │   └── ThrottlingValve
        ├── Separator and ThreePhaseSeparator (multi-inlet, multi-outlet)
        ├── Mixer (multi-inlet)
        ├── Splitter (multi-outlet)
        └── DistillationColumn and absorbers (staged, equipment-specific ports)
```

The diagram shows API ownership, not every concrete subtype.

## Related Documentation

- [Process package](../README.md) — architecture, execution, reports, and the executable quick start
- [ProcessSystem](../processmodel/process_system.md) — flowsheet orchestration and execution
- [ProcessModule](../processmodel/process_module.md) — modular process units
- [Controllers](../controllers.md) — control equipment
- [Safety systems](../safety/README.md) — safety simulation
- [Engineering documentation](../../engineering/) — controlled design cases and discipline workflows
