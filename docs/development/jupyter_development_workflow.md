---
title: "Jupyter Development Workflow"
description: "How to use Jupyter notebooks for live NeqSim Java development with automatic compilation, JVM management, and kernel restart."
---

# Jupyter Development Workflow

Use Jupyter notebooks to interactively test NeqSim Java code as you write it.
Edit a Java file, re-run a cell, and see results immediately — no JAR rebuild needed.

## Overview

The `neqsim_dev_setup` module (in `devtools/`) provides three functions:

| Function | Purpose |
|----------|---------|
| `neqsim_init()` | Start the JVM with the project classpath |
| `neqsim_classes()` | Import commonly used Java classes into a namespace |
| `neqsim_compile()` | Compile and optionally restart the kernel |

## Prerequisites

1. **Java JDK** installed and on PATH (or `JAVA_HOME` set)
2. **Python venv** with JPype installed (`pip install jpype1`)
3. **NeqSim compiled** at least once (`mvnw.cmd compile` or `mvnw.cmd package -DskipTests`)

## One-time setup

From the project root with your venv active:

```bash
pip install -e devtools/
```

This installs `neqsim_dev_setup` as an **editable** package — the module
points at the actual file in `devtools/`, so any edits you make to it are
picked up immediately (after a kernel restart).

## Quick start

Create a new notebook anywhere and add two cells:

```python
# Cell 1 — JVM lifecycle
from neqsim_dev_setup import neqsim_init, neqsim_classes
ns = neqsim_init(recompile=True)
```

```python
# Cell 2 — class imports
ns = neqsim_classes(ns)
```

Then use Java classes via the `ns` namespace:

```python
fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.9)
fluid.addComponent("ethane", 0.1)
fluid.setMixingRule("classic")
```

## How it works in detail

### The classpath

`neqsim_init()` starts a JVM with this classpath (in priority order):

1. **`target/classes/`** — your latest compiled `.class` files
2. **`src/main/resources/`** — database files, CSV data, component tables
3. **`target/neqsim-*-shaded.jar`** — all third-party dependencies (EJML, Commons Math, etc.)

Because `target/classes/` comes first, any class you recompile immediately
shadows the version inside the shaded JAR.

### The JVM lifecycle

JPype (the Python-Java bridge) can only start the JVM **once** per Python
process. Once classes are loaded, they cannot be reloaded. This means:

- **Fresh kernel**: `neqsim_init()` compiles (if `recompile=True`), starts
  the JVM, and returns a namespace object.
- **JVM already running** (re-run the cell): `neqsim_init()` detects this,
  compiles (if requested), then **restarts the Jupyter kernel** via
  `IPython.Application.instance().kernel.do_shutdown(restart=True)`.
  After restart, the cell runs again on a fresh kernel with updated classes.

This is why Cell 1 is self-contained — it always does the right thing
regardless of kernel state.

### The namespace object

`neqsim_init()` returns a `types.SimpleNamespace` with two attributes:

- `ns.PROJECT_ROOT` — `Path` to the project root
- `ns.JClass` — shortcut for `jpype.JClass()` to import any Java class

`neqsim_classes(ns)` then populates this namespace with commonly used classes:

| Attribute | Java class |
|-----------|-----------|
| `ns.SystemSrkEos` | `neqsim.thermo.system.SystemSrkEos` |
| `ns.SystemPrEos` | `neqsim.thermo.system.SystemPrEos` |
| `ns.SystemSrkCPAstatoil` | `neqsim.thermo.system.SystemSrkCPAstatoil` |
| `ns.ThermodynamicOperations` | `neqsim.thermodynamicoperations.ThermodynamicOperations` |
| `ns.ProcessSystem` | `neqsim.process.processmodel.ProcessSystem` |
| `ns.Stream` | `neqsim.process.equipment.stream.Stream` |
| `ns.Separator` | `neqsim.process.equipment.separator.Separator` |
| `ns.ThreePhaseSeparator` | `neqsim.process.equipment.separator.ThreePhaseSeparator` |
| `ns.Compressor` | `neqsim.process.equipment.compressor.Compressor` |
| `ns.Cooler` | `neqsim.process.equipment.heatexchanger.Cooler` |
| `ns.Heater` | `neqsim.process.equipment.heatexchanger.Heater` |
| `ns.HeatExchanger` | `neqsim.process.equipment.heatexchanger.HeatExchanger` |
| `ns.Mixer` | `neqsim.process.equipment.mixer.Mixer` |
| `ns.Splitter` | `neqsim.process.equipment.splitter.Splitter` |
| `ns.ThrottlingValve` | `neqsim.process.equipment.valve.ThrottlingValve` |
| `ns.AdiabaticPipe` | `neqsim.process.equipment.pipeline.AdiabaticPipe` |
| `ns.PipeBeggsAndBrills` | `neqsim.process.equipment.pipeline.PipeBeggsAndBrills` |
| `ns.Pump` | `neqsim.process.equipment.pump.Pump` |
| `ns.Manifold` | `neqsim.process.equipment.manifold.Manifold` |
| `ns.StreamSaturatorUtil` | `neqsim.process.equipment.util.StreamSaturatorUtil` |
| `ns.Recycle` | `neqsim.process.equipment.util.Recycle` |
| `ns.Adjuster` | `neqsim.process.equipment.util.Adjuster` |

### Importing additional classes

You don't need to modify `neqsim_dev_setup.py` to use more classes.
In your notebook, use either approach:

```python
# Add to ns namespace (consistent prefix)
ns.DistillationColumn = ns.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn"
)

# Or import into a local variable (no prefix)
import jpype
DistillationColumn = jpype.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn"
)
```

Inner classes and enums use `$` notation:

```python
SolverType = ns.JClass(
    "neqsim.process.equipment.distillation.DistillationColumn$SolverType"
)
```

Java standard library classes work too:

```python
ArrayList = ns.JClass("java.util.ArrayList")
HashMap = ns.JClass("java.util.HashMap")
```

## Compilation

### Automatic (recommended)

Pass `recompile=True` to `neqsim_init()`:

```python
ns = neqsim_init(recompile=True)
```

This runs `mvnw.cmd compile -q` before starting the JVM. Compilation
typically takes 30-60 seconds.

### Manual

Compile from a terminal and then run the init cell without recompile:

```bash
mvnw.cmd compile -q
```

```python
ns = neqsim_init(recompile=False)
```

### Standalone compile + restart

Use `neqsim_compile()` to compile and restart from any cell:

```python
from neqsim_dev_setup import neqsim_compile
neqsim_compile()  # compiles + restarts kernel
```

## The edit-compile-test cycle

```
1. Edit Java code in VS Code
2. Re-run Cell 1 (neqsim_init with recompile=True)
   → Compiles your changes
   → Restarts kernel (if JVM was running)
   → Cell 1 re-executes on fresh kernel
3. Run Cell 2 (neqsim_classes)
   → Imports classes from freshly compiled code
4. Run your test cells
   → Uses the updated Java classes
```

## Why not use the neqsim Python package directly?

The [neqsim-python](https://github.com/equinor/neqsim-python) package
(`pip install neqsim`) bundles a **released** JAR. It's great for end users
but has two limitations for development:

1. **Stale code** — it uses the published JAR, not your local changes
2. **Namespace conflict** — `from neqsim.thermo... import` hits the Python
   package, not the Java package. That's why we use `jpype.JClass()` with
   fully qualified class names instead.

The `devtools/` approach loads classes directly from `target/classes/`,
giving you instant access to any Java change you compile.

## Project root detection

The module auto-detects the project root relative to its own location:

```python
_PROJECT_ROOT = Path(__file__).resolve().parent.parent
# devtools/neqsim_dev_setup.py → parent = devtools/ → parent = project root
```

You can override this per notebook:

```python
ns = neqsim_init(project_root="/path/to/other/neqsim")
```

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| `ImportError: cannot import name 'neqsim_classes'` | Stale `__pycache__` or old module cached | Restart kernel; delete `__pycache__` dirs |
| `ModuleNotFoundError: No module named 'neqsim_dev_setup'` | Package not installed | Run `pip install -e devtools/` |
| `FileNotFoundError: No shaded JAR found` | Never built the full project | Run `mvnw.cmd package -DskipTests` |
| `RuntimeError: Maven compile failed` | Java compilation error | Check the error output; fix the Java code |
| Kernel shows "crashed" after re-run | Expected — `do_shutdown(restart=True)` triggers this | Normal behavior; kernel restarts and re-runs |
| `JVMNotFoundException` | No JDK found | Install JDK and set `JAVA_HOME` |

## File locations

| File | Path | Purpose |
|------|------|---------|
| Module | `devtools/neqsim_dev_setup.py` | JVM bootstrap + class imports |
| Package config | `devtools/pyproject.toml` | Makes it pip-installable |
| Example notebook | `examples/notebooks/neqsim_dev.ipynb` | Working demo |
| This guide | `docs/development/jupyter_development_workflow.md` | Detailed docs |
