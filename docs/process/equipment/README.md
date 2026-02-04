---
title: Process Equipment Documentation
description: This folder contains detailed documentation for all process equipment in NeqSim.
---

# Process Equipment Documentation

This folder contains detailed documentation for all process equipment in NeqSim.

## Equipment Categories

### Flow Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Streams | [streams.md](streams) | Material and energy streams |
| Mixers & Splitters | [mixers_splitters.md](mixers_splitters) | Stream mixing and splitting |

### Separation Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Separators | [separators.md](separators) | 2-phase and 3-phase separators, scrubbers |
| Distillation | [distillation.md](distillation) | Distillation columns |
| Absorbers | [absorbers.md](absorbers) | Absorption/stripping columns |
| Membranes | [membranes.md](membranes) | Membrane separation units |
| Filters | [filters.md](filters) | Particulate and charcoal filters |

### Heat Transfer Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Heat Exchangers | [heat_exchangers.md](heat_exchangers) | Heaters, coolers, condensers, reboilers |

### Rotating Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Compressors | [compressors.md](compressors) | Gas compression, mechanical losses, seal gas |
| Pumps | [pumps.md](pumps) | Liquid pumping |
| Expanders | [expanders.md](expanders) | Power recovery, turboexpanders |

### Flow Control

| Equipment | File | Description |
|-----------|------|-------------|
| Valves | [valves.md](valves) | Throttling valves, chokes, safety valves |

### Reactors

| Equipment | File | Description |
|-----------|------|-------------|
| Reactors | [reactors.md](reactors) | CSTR, PFR, equilibrium reactors |
| Electrolyzers | [electrolyzers.md](electrolyzers) | Water and CO₂ electrolysis |

### Ejectors

| Equipment | File | Description |
|-----------|------|-------------|
| Ejectors | [ejectors.md](ejectors) | Steam and gas ejectors |

### Safety Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Flares | [flares.md](flares) | Flare systems and combustion |

### Well/Reservoir

| Equipment | File | Description |
|-----------|------|-------------|
| Wells | [wells.md](wells) | Production wells, chokes |
| Reservoirs | [reservoirs.md](reservoirs) | Material balance reservoir modeling |
| Subsea Systems | [subsea_systems.md](subsea_systems) | Subsea wells and flowlines |

### Subsea Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Subsea Trees | [subsea_trees.md](subsea_trees) | Christmas trees, valve control |
| Subsea Manifolds | [subsea_manifolds.md](subsea_manifolds) | Multi-slot production manifolds |
| Subsea Boosters | [subsea_boosters.md](subsea_boosters) | Multiphase pumps, compressors |
| Umbilicals | [umbilicals.md](umbilicals) | Hydraulic, chemical, electrical supply |

### Pipeline/Network

| Equipment | File | Description |
|-----------|------|-------------|
| Pipelines | [pipelines.md](pipelines) | Pipe flow, pressure drop |
| **Risers** | [pipelines.md#risers](pipelines#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Networks | [networks.md](networks) | Pipeline network modeling |
| Looped Networks | [looped_networks.md](looped_networks) | Hardy Cross solver for loops |
| Manifolds | [manifolds.md](manifolds) | Multi-stream routing |

### Flow Measurement

| Equipment | File | Description |
|-----------|------|-------------|
| Differential Pressure | [differential_pressure.md](differential_pressure) | Orifice plates, flow measurement |

### Storage

| Equipment | File | Description |
|-----------|------|-------------|
| Tanks | [tanks.md](tanks) | Storage tanks, LNG boil-off |

### Gas Treatment

| Equipment | File | Description |
|-----------|------|-------------|
| Adsorbers | [adsorbers.md](adsorbers) | CO₂ and gas adsorption |

### Power Generation

| Equipment | File | Description |
|-----------|------|-------------|
| Power Equipment | [power_generation.md](power_generation) | Gas turbines, fuel cells, renewables |
| Battery Storage | [battery_storage.md](battery_storage) | Energy storage systems |

### Utility Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Adjusters | [util/adjusters.md](util/adjusters) | Variable adjustment to meet specs |
| Recycles | [util/recycles.md](util/recycles) | Recycle stream handling |
| Calculators | [util/calculators.md](util/calculators) | Custom calculations and setters |

### Reliability & Failure

| Equipment | File | Description |
|-----------|------|-------------|
| Failure Modes | [failure_modes.md](failure_modes) | Equipment failure modeling for risk analysis |

---

## Quick Reference

### Creating Equipment

```java
// All equipment follows similar pattern
EquipmentType equipment = new EquipmentType("Name", inletStream);
equipment.setParameter(value);
equipment.run();
Stream outlet = equipment.getOutletStream();
```

### Adding to ProcessSystem

```java
ProcessSystem process = new ProcessSystem();
process.add(stream);
process.add(equipment1);
process.add(equipment2);
process.run();
```

### Getting Equipment by Name

```java
Compressor comp = (Compressor) process.getUnit("K-100");
```

---

## Common Methods

All equipment inherits from `ProcessEquipmentBaseClass`:

| Method | Description |
|--------|-------------|
| `run()` | Execute calculation |
| `runTransient()` | Execute transient step |
| `getName()` | Get equipment name |
| `getInletStream()` | Get inlet stream |
| `getOutletStream()` | Get outlet stream |
| `getPressure()` | Get operating pressure |
| `getTemperature()` | Get operating temperature |
| `getMechanicalDesign()` | Get mechanical design object |
| `needRecalculation()` | Check if recalculation needed |

### Compressor-Specific Methods

| Method | Description |
|--------|-------------|
| `initMechanicalLosses(shaftDiameter)` | Initialize seal gas and bearing loss model |
| `getSealGasConsumption()` | Get total seal gas consumption (Nm³/hr) |
| `getBearingLoss()` | Get total bearing power loss (kW) |
| `getMechanicalEfficiency()` | Get mechanical efficiency (0-1) |

---

## Equipment Inheritance

```
ProcessEquipmentInterface
    │
    └── ProcessEquipmentBaseClass
            │
            ├── TwoPortEquipment (inlet/outlet pattern)
            │       ├── Heater, Cooler
            │       ├── Compressor, Pump, Expander
            │       ├── ThrottlingValve
            │       └── ...
            │
            ├── Separator (multi-outlet)
            │       ├── ThreePhaseSeparator
            │       ├── GasScrubber
            │       └── ...
            │
            ├── Mixer (multi-inlet)
            ├── Splitter (multi-outlet)
            │
            └── DistillationColumn
```

---

## Related Documentation

- [Process Package](../) - Package overview
- [ProcessSystem](../processmodel/process_system) - Process system guide
- [ProcessModule](../processmodel/process_module) - Modular process units
- [Controllers](../controllers) - Control equipment
- [Safety Systems](../safety/) - Safety equipment

