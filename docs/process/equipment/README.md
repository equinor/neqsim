---
title: Process Equipment Documentation
description: Source-safe navigation and API ownership for process equipment in NeqSim.
---

Use this index to choose an equipment-specific guide. For an executable, end-to-end flowsheet,
start with the [process-package quick start](../README); it is compiled and run by the
documentation regression suite.

The [complete source-backed equipment catalog](equipment_catalog) lists all 234 concrete
`ProcessEquipmentInterface` implementations on the current source tree. Its generated inventory
is checked in CI, so a newly added equipment class cannot remain absent from this documentation.

## Equipment Categories

### Flow Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Streams | [streams.md](streams) | Material and energy streams |
| Mixers & Splitters | [mixers_splitters.md](mixers_splitters) | Stream mixing and splitting |
| Manifolds | [manifolds.md](manifolds) | Multi-inlet routing and production manifolds |

### Separation Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Separators | [separators.md](separators) | 2-phase and 3-phase separators, scrubbers |
| Separator Entrainment | [separator-entrainment-modeling.md](separator-entrainment-modeling) | Droplet distributions, internals, grade efficiency, and performance calculation |
| Distillation | [distillation.md](distillation) | Distillation columns |
| Absorbers | [absorbers.md](absorbers) | Absorption/stripping columns |
| Adsorbers | [adsorbers.md](adsorbers) | Simplified adsorption equipment |
| Adsorption Beds | [adsorption_bed.md](adsorption_bed) | Dynamic beds, breakthrough, mercury removal, and PSA |
| Membranes | [membranes.md](membranes) | Membrane separation units |
| Filters | [filters.md](filters) | Particulate and charcoal filters |
| Water Treatment | [water_treatment.md](water_treatment) | Hydrocyclones, flotation, produced-water treatment, and compliance |
| Black-Oil Separator | [black_oil_separator.md](black_oil_separator) | Pressure-temperature separation of `SystemBlackOil` fluids |
| Solid Handling | [solid_handling.md](solid_handling) | Feedstock preparation, dewatering, filtration, drying, and crystallization |

### Heat Transfer Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Heat Exchangers | [heat_exchangers.md](heat_exchangers) | Heaters, coolers, condensers, reboilers |
| Multi-Stream Heat Exchangers | [multistream_heat_exchanger.md](multistream_heat_exchanger) | Composite curves, pinch constraints, and multi-stream matching |
| LNG Heat Exchanger | [LNGHeatExchanger.md](LNGHeatExchanger) | Brazed-aluminium exchanger model for cryogenic service |
| Heat Integration | [heat_integration.md](heat_integration) | Heat streams and pinch analysis |
| Water Cooler and Reboiler | [water_cooler_reboiler.md](water_cooler_reboiler) | Cooling-water and reboiler utilities |

### Rotating Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Compressors | [compressors.md](compressors) | Gas compression, mechanical losses, seal gas |
| Compressor Curves | [compressor_curves.md](compressor_curves) | Performance maps, correction, interpolation, and envelopes |
| Compressor Shaft | [compressor_shaft.md](compressor_shaft) | Multiple compressor bodies on a common-speed shaft |
| Compressor Anti-Surge Control | [compressor_antisurge_control.md](compressor_antisurge_control) | Dynamic anti-surge recycle, speed/load control, and coordinated pressure-speed-recycle control philosophy |
| Pumps | [pumps.md](pumps) | Liquid pumping |
| Expanders | [expanders.md](expanders) | Power recovery, turboexpanders |

### Flow Control

| Equipment | File | Description |
|-----------|------|-------------|
| Valves | [valves.md](valves) | Throttling valves, chokes, safety valves |
| Control Valves | [control_valves.md](control_valves) | Specialized pressure and level control valves |

### Reactors

| Equipment | File | Description |
|-----------|------|-------------|
| Reactors (Overview) | [reactors.md](reactors) | All reactor types: PFR, CSTR, Gibbs, stoichiometric, ammonia, sulfur, bio-processing |
| Iron-Sulfide Wall Source | [iron_sulfide_wall_source.md](iron_sulfide_wall_source) | Stateful FeS/FeCO3 scale, oxygen ingress, S8 generation, and compressor deposition coupling |
| Plug Flow Reactor | [plug_flow_reactor.md](plug_flow_reactor) | Kinetic PFR with power-law/LHHW/reversible kinetics, catalyst bed, Ergun ΔP, energy modes |
| Electrolyzers | [electrolyzers.md](electrolyzers) | Water and CO₂ electrolysis |

### Ejectors

| Equipment | File | Description |
|-----------|------|-------------|
| Ejectors | [ejectors.md](ejectors) | Steam and gas ejectors |

### Safety Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Flares | [flares.md](flares) | Flare systems and combustion |
| Vessel Depressurization | [vessel_depressurization.md](vessel_depressurization) | Blowdown, filling, fire exposure, and transient vessel inventory |

### Well/Reservoir

| Equipment | File | Description |
|-----------|------|-------------|
| Wells | [wells.md](wells) | Production wells, chokes |
| Reservoirs | [reservoirs.md](reservoirs) | Material balance reservoir modeling |
| Production Well Networks | [production_well_networks.md](production_well_networks) | Coupled wells, flowlines, and network constraints |
| Well Allocation | [well_allocation.md](well_allocation) | Production allocation across wells |
| Subsea Systems | [subsea_systems.md](subsea_systems) | Subsea wells and flowlines |

### Subsea Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Subsea Equipment Overview | [subsea_equipment.md](subsea_equipment) | Complete SURF and subsea equipment selection |
| Subsea Trees | [subsea_trees.md](subsea_trees) | Christmas trees, valve control |
| Subsea Manifolds | [subsea_manifolds.md](subsea_manifolds) | Multi-slot production manifolds |
| Manifold Design | [manifold_design.md](manifold_design) | Mechanical design for topside, onshore, and subsea manifolds |
| Subsea Boosters | [subsea_boosters.md](subsea_boosters) | Multiphase pumps, compressors |
| Umbilicals | [umbilicals.md](umbilicals) | Hydraulic, chemical, electrical supply |

### Pipeline/Network

| Equipment | File | Description |
|-----------|------|-------------|
| Pipelines | [pipelines.md](pipelines) | Pipe flow, pressure drop |
| Pipeline Simulation | [pipeline_simulation.md](pipeline_simulation) | Model selection, configuration, and execution |
| Multiphase Correlations | [multiphase_flow_correlations.md](multiphase_flow_correlations) | Pressure-drop, holdup, and flow-regime correlations |
| **Risers** | [pipelines.md#risers](pipelines#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Networks | [networks.md](networks) | Pipeline network modeling |
| Looped Networks | [looped_networks.md](looped_networks) | Hardy Cross solver for loops |
| Gas Network Operations | [../gas_network_operations.md](../gas_network_operations) | Coupled gas quality, optimization, and linepack |
| Oil Network Operations | [../oil_network_operations.md](../oil_network_operations) | Oil pumps, terminal tanks, parcels, and cargo schedules |

### Flow Measurement

| Equipment | File | Description |
|-----------|------|-------------|
| Differential Pressure | [differential_pressure.md](differential_pressure) | Orifice plates, flow measurement |
| Measurement Devices | [measurement_devices.md](measurement_devices) | Flow, pressure, composition, and condition measurements |

### Storage

| Equipment | File | Description |
|-----------|------|-------------|
| Tanks | [tanks.md](tanks) | Storage tanks, LNG boil-off |
| LNG Cargo Ageing | [../lng-ageing.md](../lng-ageing) | Stratification, boil-off gas, rollover, voyage, and cargo-quality evolution |

### Power Generation

| Equipment | File | Description |
|-----------|------|-------------|
| Power Equipment | [power_generation.md](power_generation) | Gas turbines, fuel cells, renewables |
| Battery Storage | [battery_storage.md](battery_storage) | Energy storage systems |
| Energy Conversion | [energy_conversion.md](energy_conversion) | Motors, generators, converters, thermal utilities, and energy-network solvers |

### Utility Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Utility Index | [util/](util/) | Complete utility-equipment navigation |
| Adjusters | [util/adjusters.md](util/adjusters) | Variable adjustment to meet specs |
| Recycles | [util/recycles.md](util/recycles) | Recycle stream handling |
| Calculators | [util/calculators.md](util/calculators) | Custom calculations and setters |
| Fuel Gas System | [util/fuel_gas_system.md](util/fuel_gas_system) | Fuel-gas supply and balancing |
| Produced-Water Degassing | [util/produced_water_degassing.md](util/produced_water_degassing) | Degassing-system utility model |
| Saturators | [util/saturators.md](util/saturators) | Water and component saturation utilities |
| Stream Fitters | [util/stream_fitters.md](util/stream_fitters) | GOR, MPFM, and production-rate fitting |
| Utility Air System | [util/utility_air_system.md](util/utility_air_system) | Instrument and plant-air utility modeling |

### Reliability & Failure

| Equipment | File | Description |
|-----------|------|-------------|
| Failure Modes | [failure_modes.md](failure_modes) | Equipment failure modeling for risk analysis |

### Complete Source Inventory

| Catalog | Coverage | Description |
| --- | --- | --- |
| [Complete equipment catalog](equipment_catalog) | 234 concrete classes in 33 source packages | Generated from the Java inheritance tree and checked against source in CI |


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
[canonical process-package example](../README#processsystem) demonstrates this sequence with a
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

- [Process package](../README) — architecture, execution, reports, and the executable quick start
- [ProcessSystem](../processmodel/process_system) — flowsheet orchestration and execution
- [ProcessModule](../processmodel/process_module) — modular process units
- [Controllers](../controllers) — control equipment
- [Safety systems](../safety/README) — safety simulation
- [Engineering documentation](../../engineering/) — controlled design cases and discipline workflows
