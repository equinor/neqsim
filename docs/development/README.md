---
title: Development Documentation
description: Guides for developers contributing to NeqSim, extending models, and creating custom components.
---

# Development Documentation

Guides for developers contributing to NeqSim, extending the library with new models, and integrating with Python.

---

## Overview

This folder contains documentation for:
- Setting up development environments
- Contributing to the NeqSim project
- Extending NeqSim with custom models and equipment
- Python integration patterns

---

## Getting Started

| Document | Description |
|----------|-------------|
| [DEVELOPER_SETUP.md](DEVELOPER_SETUP) | Development environment setup |
| [contributing-structure.md](contributing-structure) | Contributing guidelines and code structure |

---

## Extending NeqSim

These guides explain how to add new functionality to NeqSim:

| Guide | Description |
|-------|-------------|
| [Extending Process Equipment](extending_process_equipment) | Add custom separators, reactors, and other unit operations |
| [Extending Physical Properties](extending_physical_properties) | Add custom viscosity, conductivity, and diffusivity models |
| [Extending Thermodynamic Models](extending_thermodynamic_models) | Add custom equations of state and activity models |
| [Python Extension Patterns](python_extension_patterns) | Use NeqSim from Python, create wrappers, implement interfaces |

### Extension Quick Reference

**Process Equipment**: Extend `ProcessEquipmentBaseClass`, implement `run()` method
```java
public class MyEquipment extends ProcessEquipmentBaseClass {
    @Override
    public void run(UUID id) {
        // Get inlet, calculate, set outlet
    }
}
```

**Physical Properties**: Extend `Viscosity`/`Conductivity`/`Diffusivity`, implement `calcViscosity()` etc.
```java
public class MyViscosityModel extends Viscosity {
    @Override
    public double calcViscosity() {
        // Your correlation here
    }
}
```

**Thermodynamic Models**: Extend `SystemEos`, create matching `PhaseXxxEos` and `ComponentXxx`
```java
public class SystemMyEos extends SystemEos {
    public SystemMyEos(double T, double P) {
        // Initialize phases with PhaseMyEos
    }
}
```

**Python Integration**: Use `jneqsim` gateway or implement Java interfaces with `@JImplements`
```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(300.0, 50.0)
```

---

## Architecture References

For deeper understanding of NeqSim's architecture:

| Topic | Location |
|-------|----------|
| Thermodynamic Systems | [../thermo/](../thermo/) |
| Process Equipment | [../process/](../process/) |
| Physical Properties | [../physical_properties/](../physical_properties/) |
| Validation Framework | [../integration/ai_validation_framework.md](../integration/ai_validation_framework) |

---

## Quick Links

- [NeqSim GitHub Repository](https://github.com/equinor/neqsim)
- [NeqSim Python Package](https://github.com/equinor/neqsim-python)
- [Main Documentation](../README)
- [Reference Manual](../REFERENCE_MANUAL_INDEX)

