---
name: neqsim-notebook-patterns
description: "Jupyter notebook patterns for NeqSim. USE WHEN: creating or reviewing Jupyter notebooks that use NeqSim for process simulation, thermodynamics, or PVT analysis. Covers dual-boot setup cell, class imports (devtools vs pip), notebook structure, visualization requirements, and results.json schema."
---

# Jupyter Notebook Patterns for NeqSim

Standard patterns for creating Jupyter notebooks that use NeqSim via the Python gateway.

## Dual-Boot Setup Cell (Use in Every Notebook)

This cell works both locally (with devtools compiled classes) and in Google Colab:

```python
import importlib, subprocess, sys

try:
    from neqsim_dev_setup import neqsim_init, neqsim_classes
    ns = neqsim_init(recompile=False)
    ns = neqsim_classes(ns)
    NEQSIM_MODE = "devtools"
    print("NeqSim loaded via devtools (local dev mode)")
except ImportError:
    try:
        import neqsim
    except ImportError:
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-q", "neqsim"])
    from neqsim import jneqsim
    NEQSIM_MODE = "pip"
    print("NeqSim loaded via pip package")
```

## Class Import Cell

```python
if NEQSIM_MODE == "devtools":
    # Classes already on ns.* from neqsim_classes()
    pass
else:
    ns = type('ns', (), {})()  # simple namespace
    ns.SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
    ns.ProcessSystem = jneqsim.process.processmodel.ProcessSystem
    ns.Stream = jneqsim.process.equipment.stream.Stream
    ns.Separator = jneqsim.process.equipment.separator.Separator
    ns.Compressor = jneqsim.process.equipment.compressor.Compressor
    ns.Cooler = jneqsim.process.equipment.heatexchanger.Cooler
    ns.Heater = jneqsim.process.equipment.heatexchanger.Heater
    ns.ThrottlingValve = jneqsim.process.equipment.valve.ThrottlingValve
    ns.Mixer = jneqsim.process.equipment.mixer.Mixer
    ns.Splitter = jneqsim.process.equipment.splitter.Splitter
    # ... add only classes used in this notebook
```

### Alternative: Simple `jneqsim` Import (Non-devtools Notebooks)

```python
from neqsim import jneqsim

SystemSrkEos = jneqsim.thermo.system.SystemSrkEos
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
Stream = jneqsim.process.equipment.stream.Stream
# ... add only classes used
```

NEVER use raw `jpype` imports or `jpype.startJVM()` for new notebooks.

## Common Class Paths

```python
# Thermo systems
jneqsim.thermo.system.SystemSrkEos
jneqsim.thermo.system.SystemPrEos
jneqsim.thermo.system.SystemSrkCPAstatoil
jneqsim.thermo.system.SystemGERG2008Eos
jneqsim.thermodynamicoperations.ThermodynamicOperations

# Process equipment
jneqsim.process.processmodel.ProcessSystem
jneqsim.process.equipment.stream.Stream
jneqsim.process.equipment.separator.Separator
jneqsim.process.equipment.separator.ThreePhaseSeparator
jneqsim.process.equipment.compressor.Compressor
jneqsim.process.equipment.heatexchanger.Heater
jneqsim.process.equipment.heatexchanger.Cooler
jneqsim.process.equipment.heatexchanger.HeatExchanger
jneqsim.process.equipment.valve.ThrottlingValve
jneqsim.process.equipment.mixer.Mixer
jneqsim.process.equipment.splitter.Splitter
jneqsim.process.equipment.pipeline.AdiabaticPipe
jneqsim.process.equipment.pipeline.PipeBeggsAndBrills
jneqsim.process.equipment.distillation.DistillationColumn
jneqsim.process.equipment.expander.Expander
jneqsim.process.equipment.util.Recycle
jneqsim.process.equipment.util.Adjuster

# Load additional classes via JClass (devtools) or full path (pip)
# ns.DistillationColumn = ns.JClass("neqsim.process.equipment.distillation.DistillationColumn")
```

## Notebook Structure (Follow This Order)

1. **Title + Introduction** (Markdown) — What the notebook demonstrates. ASCII flow diagram if process simulation. Colab badge.
2. **Setup and Imports** (Code) — Dual-boot cell + class imports
3. **Fluid Creation** (Code) — Temperature in Kelvin, set mixing rule
4. **Process Building** (Code + Markdown) — Build flowsheet step by step with explanatory markdown between cells
5. **Run Simulation** (Code) — Single `process.run()` call
6. **Results Extraction** (Code) — Key results in formatted table with units (pandas DataFrame or f-strings)
7. **Visualization** (Code) — **MANDATORY: at least 2-3 matplotlib figures**
8. **Summary & Next Steps** (Markdown) — Key takeaways

### Colab Badge

```markdown
[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/equinor/neqsim/blob/master/examples/notebooks/FILENAME.ipynb)
```

## Visualization Requirements (MANDATORY)

Every notebook MUST include at least 2-3 matplotlib figures:

- **Axis labels with units** — always include (e.g., "Temperature (°C)", "Pressure (bara)")
- **Title, legend, grid** — for readability
- **Save as PNG** — `plt.savefig("../figures/name.png", dpi=150, bbox_inches="tight")`
- **Discussion cell after EVERY figure** — observation, mechanism, implication, recommendation

Common plot types:
- Process profiles (T, P, flow vs equipment/stage)
- Composition charts (bar/stacked)
- Sensitivity curves (parametric sweeps)
- Phase envelopes (T vs P with boundaries)
- Cost breakdowns (bar charts)
- Tornado diagrams (sensitivity ranking)

## Getting Results from NeqSim

```python
# Stream properties
stream.getTemperature() - 273.15   # °C (returns Kelvin!)
stream.getPressure()                # bara
stream.getFlowRate("kg/hr")         # mass flow with unit

# Equipment outputs
comp.getPower("kW")                 # compressor power
cooler.getDuty()                    # heat duty in Watts

# Fluid properties (MUST call initProperties() first for standalone flash)
fluid = stream.getFluid()
fluid.initProperties()              # MANDATORY before transport properties
fluid.getDensity("kg/m3")
fluid.getPhase("gas").getViscosity("kg/msec")
fluid.getPhase("gas").getThermalConductivity("W/mK")
```

## results.json Template (for Task-Solving Notebooks)

```python
import json, os, pathlib

NOTEBOOK_DIR = pathlib.Path(globals().get(
    "__vsc_ipynb_file__", os.path.abspath("step2_analysis/notebook.ipynb")
)).resolve().parent
TASK_DIR = NOTEBOOK_DIR.parent
FIGURES_DIR = TASK_DIR / "figures"
FIGURES_DIR.mkdir(exist_ok=True)

results = {
    "key_results": {"outlet_temperature_C": -18.5, "pressure_drop_bar": 3.2},
    "validation": {"mass_balance_error_pct": 0.01, "acceptance_criteria_met": True},
    "approach": "Used SRK EOS with classic mixing rule...",
    "conclusions": "The analysis shows...",
    "figure_captions": {},
    "figure_discussion": [],
    "equations": [],
    "tables": [],
    "references": [],
    "uncertainty": {},
    "risk_evaluation": {}
}
with open(str(TASK_DIR / "results.json"), "w") as f:
    json.dump(results, f, indent=2)
```

## Type Conversion Tips

```python
# Explicit float for numeric Java parameters
comp.setOutletPressure(float(pressure))

# Python bool works for Java boolean
fluid.setMultiPhaseCheck(True)

# String parameters usually work as-is
stream.setFlowRate(50000.0, "kg/hr")
```

## Performance Estimation and Optimization

### Simulation Time Estimates

Use these estimates to warn users about long-running operations and to decide
whether Monte Carlo should use full NeqSim simulations or simplified models:

| Operation | Typical Time | Notes |
|-----------|-------------|-------|
| Single TPflash (3-5 components) | < 0.01 s | Negligible |
| Single TPflash (15+ components, CPA) | 0.05-0.2 s | CPA adds association iterations |
| ProcessSystem with 5-10 units | 0.5-5 s | Depends on recycles and adjusters |
| ProcessSystem with 20+ units + recycles | 5-30 s | Complex flowsheets need multiple iterations |
| Phase envelope calculation | 2-10 s | Many flash points along the boundary |
| Hydrate equilibrium temperature | 1-3 s | Iterative solid-fluid equilibrium |
| Distillation column (10 stages) | 5-20 s | Inside-out solver faster than standard |
| Pipeline (PipeBeggsAndBrills, 50 km) | 2-10 s | Segmented calculation along length |
| Monte Carlo N=200 with process sim | 5-15 min | 200 x 2-5 s per run |
| Monte Carlo N=1000 with process sim | 30-60 min | Consider caching or simplified model |
| Monte Carlo N=1000 with TPflash only | 1-5 min | Fast if no process equipment |

### When to Warn the User

If estimated total simulation time exceeds 10 minutes, inform the user:
```python
estimated_time_min = N_simulations * time_per_sim_s / 60.0
if estimated_time_min > 10:
    print(f"⚠️ Estimated run time: {estimated_time_min:.0f} minutes ({N_simulations} simulations)")
    print("Consider reducing N or caching intermediate results.")
```

### Optimization Strategies for Monte Carlo

1. **Cache expensive NeqSim results** that don't change between iterations:
   ```python
   # Compute base process simulation once
   process.run()
   base_power_kW = comp.getPower("kW")
   base_flow = stream.getFlowRate("kg/hr")

   # In Monte Carlo loop, only re-run what changes
   for i in range(N):
       gas_price = np.random.triangular(0.8, 1.5, 2.5)
       npv = calculate_npv(base_flow, gas_price, capex_multiplier)
   ```

2. **Classify parameters for tornado analysis**:
   - **Economic parameters** (gas price, discount rate, CAPEX multiplier): reuse base production profile, recalculate cash flow only — instant
   - **Technical parameters** (composition, pressure, temperature): require NeqSim re-run — slow

3. **Reduce component count** for screening-level Monte Carlo:
   ```python
   # Instead of 15-component fluid, use 5-component lumped version
   fluid = SystemSrkEos(273.15 + 25.0, 60.0)
   fluid.addComponent("methane", 0.85)
   fluid.addComponent("ethane", 0.10)
   fluid.addComponent("propane", 0.05)
   # Skip C4+, N2, CO2 for screening
   ```

4. **Use parallel-safe patterns** (when running outside Jupyter):
   Each NeqSim fluid/process should be independent — clone fluids before branching.

## Notebook Placement

- General examples: `examples/notebooks/`
- Task-solving: `task_solve/YYYY-MM-DD_slug/step2_analysis/`
- After creating: update `docs/examples/index.md` if documenting
