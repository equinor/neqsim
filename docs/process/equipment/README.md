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
| Pumps | [pumps.md](pumps.md) | Liquid pumping |
| Expanders | [expanders.md](expanders.md) | Power recovery, turboexpanders |

### Flow Control

| Equipment | File | Description |
|-----------|------|-------------|
| Valves | [valves.md](valves.md) | Throttling valves, chokes, safety valves |

### Reactors

| Equipment | File | Description |
|-----------|------|-------------|
| Reactors | [reactors.md](reactors.md) | CSTR, PFR, equilibrium reactors |
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

### Pipeline/Network

| Equipment | File | Description |
|-----------|------|-------------|
| Pipelines | [pipelines.md](pipelines.md) | Pipe flow, pressure drop |
| **Risers** | [pipelines.md#risers](pipelines.md#risers) | **SCR, TTR, Flexible, Lazy-Wave risers** |
| Networks | [networks.md](networks.md) | Pipeline network modeling |
| Manifolds | [manifolds.md](manifolds.md) | Multi-stream routing |

### Flow Measurement

| Equipment | File | Description |
|-----------|------|-------------|
| Differential Pressure | [differential_pressure.md](differential_pressure.md) | Orifice plates, flow measurement |

### Storage

| Equipment | File | Description |
|-----------|------|-------------|
| Tanks | [tanks.md](tanks.md) | Storage tanks, LNG boil-off |

### Gas Treatment

| Equipment | File | Description |
|-----------|------|-------------|
| Adsorbers | [adsorbers.md](adsorbers.md) | CO₂ and gas adsorption |

### Power Generation

| Equipment | File | Description |
|-----------|------|-------------|
| Power Equipment | [power_generation.md](power_generation.md) | Gas turbines, fuel cells, renewables |

### Utility Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Adjusters | [util/adjusters.md](util/adjusters.md) | Variable adjustment to meet specs |
| Recycles | [util/recycles.md](util/recycles.md) | Recycle stream handling |
| Calculators | [util/calculators.md](util/calculators.md) | Custom calculations and setters |

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

- [Process Package](../README.md) - Package overview
- [ProcessSystem](../processmodel/process_system.md) - Process system guide
- [ProcessModule](../processmodel/process_module.md) - Modular process units
- [Controllers](../controllers.md) - Control equipment
- [Safety Systems](../safety/README.md) - Safety equipment

