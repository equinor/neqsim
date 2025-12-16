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

### Heat Transfer Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Heat Exchangers | [heat_exchangers.md](heat_exchangers.md) | Heaters, coolers, condensers, reboilers |

### Rotating Equipment

| Equipment | File | Description |
|-----------|------|-------------|
| Compressors | [compressors.md](compressors.md) | Gas compression |
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

### Well/Reservoir

| Equipment | File | Description |
|-----------|------|-------------|
| Wells | [wells.md](wells.md) | Production wells, chokes |

### Pipeline

| Equipment | File | Description |
|-----------|------|-------------|
| Pipelines | [pipelines.md](pipelines.md) | Pipe flow, pressure drop |

### Storage

| Equipment | File | Description |
|-----------|------|-------------|
| Tanks | [tanks.md](tanks.md) | Storage tanks, LNG boil-off |

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

---

## Related Documentation

- [Process Package](../README.md) - Package overview
- [ProcessSystem](../process_system.md) - Process system guide
- [Controllers](../controllers.md) - Control equipment
