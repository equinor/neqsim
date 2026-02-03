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
| Adjusters | [adjusters.md](adjusters.md) | Parameter adjustment to meet specifications |
| Recycles | [recycles.md](recycles.md) | Recycle stream handling |
| Calculators | [calculators.md](calculators.md) | Custom calculations |

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

- [Process Controllers](../../controllers.md) - Controller documentation
- [Equipment Overview](../README.md) - All equipment
