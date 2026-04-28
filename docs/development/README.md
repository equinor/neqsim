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
| [jupyter_development_workflow.md](jupyter_development_workflow) | Jupyter notebooks for live Java development |

---

## AI-Assisted Task Solving

| Document | Description |
|----------|-------------|
| [TASK_SOLVING_GUIDE.md](TASK_SOLVING_GUIDE) | Complete workflow for solving tasks with AI while developing the physics engine, including `study_config.yaml` for deep multi-notebook studies |
| [Solve an Engineering Task](../tutorials/solve-engineering-task) | Hands-on tutorial: from blank screen to validated report |
| [CODE_PATTERNS.md](CODE_PATTERNS) | Copy-paste code starters for every common task type |
| [TASK_LOG.md](TASK_LOG) | Persistent memory — searchable log of all solved tasks |
| [LESSONS_LEARNED.md](LESSONS_LEARNED) | Practical lessons from 45+ solved tasks (EOS, convergence, API gotchas) |
| [WORKFLOW_REVIEW.md](WORKFLOW_REVIEW) | Comparative review of the task solver vs commercial tools and AI frameworks |

Start with `CONTEXT.md` in the repo root for a 60-second orientation.

For detailed engineering studies, create tasks with explicit depth controls:

```powershell
neqsim new-task "field development study" --type G --scale comprehensive --report-depth detailed --notebooks 5
```

The generated `study_config.yaml` controls notebook count and names, report
sections, benchmark validation, uncertainty/risk requirements, figure minimums,
document inputs, and consistency-check gates. Task input can also be provided
as markdown prompt files or engineering documents in
`step1_scope_and_research/references/`. See [TASK_SOLVING_GUIDE.md](TASK_SOLVING_GUIDE)
for the full configuration schema and examples.

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
- [Main Documentation](../)
- [Reference Manual](../REFERENCE_MANUAL_INDEX)

