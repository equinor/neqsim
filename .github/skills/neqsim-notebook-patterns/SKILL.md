---
name: neqsim-notebook-patterns
description: "Jupyter notebook patterns for NeqSim. USE WHEN: creating or reviewing Jupyter notebooks that use NeqSim for process simulation, thermodynamics, or PVT analysis. Covers dual-boot setup cell, class imports (devtools vs pip), notebook structure, visualization requirements, and results.json schema."
last_verified: "2026-07-04"
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

# Multi-area plant management
jneqsim.process.processmodel.ProcessModel    # Named collection of ProcessSystems
jneqsim.process.processmodel.ProcessModule   # Legacy — prefer ProcessModel

# Load additional classes via JClass (devtools) or full path (pip)
# ns.DistillationColumn = ns.JClass("neqsim.process.equipment.distillation.DistillationColumn")
```

### Loading Custom / NIP Classes via jpype.JClass

Some newer classes (CO2 injection well analysis, impurity monitoring, transient
wellbore) are not exposed through the `jneqsim` gateway. Load them with `jpype.JClass()`:

```python
import jpype

# CO2 injection well analysis classes
CO2InjectionWellAnalyzer = jpype.JClass("neqsim.process.equipment.pipeline.CO2InjectionWellAnalyzer")
TransientWellbore = jpype.JClass("neqsim.process.equipment.pipeline.TransientWellbore")
CO2FlowCorrections = jpype.JClass("neqsim.process.equipment.pipeline.CO2FlowCorrections")
ImpurityMonitor = jpype.JClass("neqsim.process.measurementdevice.ImpurityMonitor")
```

In **devtools mode**, use `ns.JClass()` instead of `jpype.JClass()`.

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

### Including Extracted PDF Figures in Notebooks

When reference documents (papers, standards, data sheets) are available as PDFs,
extract their pages as PNG images for inclusion in the analysis:

```python
# In a notebook cell — extract specific pages from a reference PDF
import subprocess
subprocess.run([
    "python", "../../devtools/pdf_to_figures.py",
    "../step1_scope_and_research/references/compressor_datasheet.pdf",
    "--pages", "3", "5",
    "--outdir", "../figures/"
], check=True)
```

Or use the Python API directly:
```python
from devtools.pdf_to_figures import pdf_to_pngs
pngs = pdf_to_pngs(
    "../step1_scope_and_research/references/compressor_datasheet.pdf",
    outdir="../figures/", pages=[3, 5]
)
```

This is useful for:
- Embedding reference diagrams alongside simulation results for comparison
- Digitizing data from charts to validate NeqSim predictions
- Including vendor performance curves in design feasibility notebooks
- Documenting the source drawings/P&IDs that drove the simulation setup

## Multi-Area Plant Architecture (ProcessModel)

For large plants (platforms, gas plants, refineries), split into separate `ProcessSystem`
objects per process area and combine with `ProcessModel`. This is the pattern used in
production models for large platforms.

### Pattern: Functions Returning ProcessSystem

```python
ProcessModel = jneqsim.process.processmodel.ProcessModel

def create_well_feed_model(inp):
    """Each area is a function returning a ProcessSystem."""
    well_process = jneqsim.process.processmodel.ProcessSystem()
    feed = Stream("feed", fluid)
    feed.setFlowRate(inp.flow_rate, "kg/hr")
    well_process.add(feed)
    manifold = Splitter("manifold", feed)
    manifold.setSplitFactors([0.5, 0.5])
    well_process.add(manifold)
    return well_process

def create_separation_process(inp, feed_stream):
    sep_process = jneqsim.process.processmodel.ProcessSystem()
    separator = ThreePhaseSeparator("1st stage", feed_stream)
    sep_process.add(separator)
    # ... more equipment
    return sep_process

# Build each area (run individually to populate outlet streams)
well_model = create_well_feed_model(params)
well_model.run()

# Cross-system streams: outlet of one system feeds constructor of next
sep_train_A = create_separation_process(params,
    well_model.getUnit("manifold").getSplitStream(0))
sep_train_A.run()

sep_train_B = create_separation_process(params,
    well_model.getUnit("manifold").getSplitStream(1))
sep_train_B.run()

# Combine all into ProcessModel with named entries
plant = ProcessModel()
plant.add("well process", well_model)
plant.add("separation train A", sep_train_A)
plant.add("separation train B", sep_train_B)
plant.run()  # Iterates all systems until convergence

# Access results by area name
print(plant.getConvergenceSummary())
print(plant.getMassBalanceReport())
sep_A = plant.get("separation train A")
print(sep_A.getUnit("1st stage").getGasOutStream().getFlowRate("MSm3/day"))
```

### Key Rules for Multi-Area Models

- **Order matters**: `add()` upstream systems first — they run in insertion order
- **Cross-system streams**: Pass outlet stream of System A as constructor arg to System B
- **Run each area first**: Call `.run()` on each ProcessSystem before combining (populates streams)
- **NEVER** add ProcessModule/ProcessModel to a ProcessSystem — it will throw TypeError
- **Use `getUnit("name")`** to access equipment across systems
- **ProcessModel.run()** iterates all systems and checks convergence

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

## Automation API (String-Addressable Variables)

Use `ProcessAutomation` for agent-friendly access to simulation variables — avoids
navigating Java class hierarchies. Preferred when programmatically exploring or
modifying process simulations in notebooks.

```python
# Get automation facade
auto = process.getAutomation()

# Discover equipment
units = list(auto.getUnitList())         # ["Feed Gas", "HP Sep", "Compressor", ...]
eq_type = auto.getEquipmentType("HP Sep")  # "Separator"

# List variables for an equipment unit
vars_list = list(auto.getVariableList("HP Sep"))
for v in vars_list:
    print(f"{v.getAddress()} [{v.getType()}] ({v.getDefaultUnit()}) — {v.getDescription()}")

# Read values with unit conversion (dot-notation address)
temp = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C")
flow = auto.getVariableValue("HP Sep.gasOutStream.flowRate", "kg/hr")

# Write INPUT variables and re-run
auto.setVariableValue("Compressor.outletPressure", 150.0, "bara")
process.run()  # propagate changes

# Multi-area plant
plant_auto = plant.getAutomation()
areas = list(plant_auto.getAreaList())  # ["Separation", "Compression"]
t = plant_auto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C")
```

## Lifecycle State (Save / Restore / Compare)

JSON snapshots for reproducibility and version comparison in notebooks:

```python
import jpype
ProcessSystemState = jpype.JClass("neqsim.process.processmodel.lifecycle.ProcessSystemState")
ProcessModelState = jpype.JClass("neqsim.process.processmodel.lifecycle.ProcessModelState")

# Save state
state = ProcessSystemState.fromProcessSystem(process)
state.setName("Gas Processing")
state.setVersion("1.0.0")
state.saveToFile("model_v1.json")

# Load and validate
loaded = ProcessSystemState.loadFromFile("model_v1.json")
result = loaded.validate()
print(f"Valid: {result.isValid()}")

# Multi-area model state
model_state = ProcessModelState.fromProcessModel(plant)
model_state.setVersion("1.0.0")
model_state.saveToFile("plant_v1.json")

# Compare two versions
v1 = ProcessModelState.fromProcessModel(plant)  # before changes
# ... make changes ...
v2 = ProcessModelState.fromProcessModel(plant)  # after changes
diff = ProcessModelState.compare(v1, v2)
if diff.hasChanges():
    print("Modified parameters:", list(diff.getModifiedParameters()))
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

## Validate results.json (MANDATORY for Task-Solving Notebooks)

After saving results.json, run the Java `TaskResultValidator` to catch schema errors
before report generation. This is a programmatic quality gate — do NOT proceed to
Step 3 if validation fails.

```python
# ── Programmatic quality gate ──
import jpype
TaskResultValidator = jpype.JClass("neqsim.util.agentic.TaskResultValidator")

with open(str(TASK_DIR / "results.json"), "r") as f:
    json_str = f.read()

report = TaskResultValidator.validate(json_str)
print(f"Valid: {report.isValid()}  |  Errors: {report.getErrorCount()}  |  Warnings: {report.getWarningCount()}")

if not report.isValid():
    print("\n❌ ERRORS (must fix before proceeding to report):")
    for err in report.getErrors():
        print(f"  [{err.field}] {err.message}")

if report.getWarningCount() > 0:
    print("\n⚠️ WARNINGS (fix for Standard/Comprehensive tasks):")
    for warn in report.getWarnings():
        print(f"  [{warn.field}] {warn.message}")

assert report.isValid(), "results.json failed validation — fix errors above"
```

The validator checks:
- **Required keys**: `key_results`, `validation`, `approach`, `conclusions`
- **Recommended keys**: `figure_captions`, `figure_discussion`, `equations`, `tables`, `references`, `uncertainty`, `risk_evaluation`, `standards_applied`
- **Uncertainty section**: Monte Carlo N ≥ 200, P10/P50/P90 present
- **Risk evaluation**: Risk register with id, description, risk_level
- **Standards applied**: Array entries with code, scope, status (PASS/FAIL/INFO/N/A)

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
