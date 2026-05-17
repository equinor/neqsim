---
title: Utility Equipment
description: This folder contains documentation for utility and control equipment in NeqSim.
---

# Utility Equipment

This folder contains documentation for utility and control equipment in NeqSim.

## Equipment Categories

### Process Control

| Equipment | File | Description |
|-----------|------|-------------|
| Adjusters | [adjusters.md](adjusters) | Parameter adjustment to meet specifications |
| Recycles | [recycles.md](recycles) | Recycle stream handling |
| Calculators | [calculators.md](calculators) | Custom calculations |

### Stream Utilities

| Equipment | File | Description |
|-----------|------|-------------|
| Saturators | [saturators.md](saturators) | Water saturation for wet gas simulation |
| Stream Fitters | [stream_fitters.md](stream_fitters) | Stream property fitting |

### Facility Systems

| Equipment | File | Description |
|-----------|------|-------------|
| Fuel Gas System | [fuel_gas_system.md](fuel_gas_system) | Fuel gas conditioning for turbines, heaters, pilots |
| Utility Air System | [utility_air_system.md](utility_air_system) | Instrument/plant air per ISO 8573-1 |
| Produced Water Degassing | [produced_water_degassing.md](produced_water_degassing) | Multi-stage degassing with emissions |

---

## Quick Reference

### Adjuster Pattern

```java
Adjuster adjuster = new Adjuster("Controller");
adjuster.setAdjustedVariable(equipment, "parameter");
adjuster.setTargetVariable(stream, "property", targetValue, unit);
process.add(adjuster);
```

### Recycle Pattern

```java
Recycle recycle = new Recycle("RecycleName");
recycle.addStream(recycleStream);
recycle.setOutletStream(targetMixer);
recycle.setTolerance(1e-6);
process.add(recycle);
```

---

## Related Documentation

- [Process Controllers](../../controllers) - Controller documentation
- [Equipment Overview](../) - All equipment
