---
title: "UniSim Design to NeqSim Conversion Guide"
description: "Complete guide for converting Honeywell UniSim Design (.usc) process models to NeqSim ProcessSystem simulations and exporting NeqSim models back to UniSim. Covers COM automation, component mapping, EOS mapping, topology reconstruction, verification, and troubleshooting."
keywords: "UniSim, HYSYS, Aspen HYSYS, converter, conversion, COM, usc file, process model import, process model export, UniSim to NeqSim, NeqSim to UniSim, model migration"
---

# UniSim Design to NeqSim Conversion Guide

This guide covers the complete workflow for converting process simulation models between **Honeywell UniSim Design** (or Aspen HYSYS) and **NeqSim**. It includes:

- Reading `.usc` files and extracting process data via Windows COM automation
- Converting UniSim models to NeqSim `ProcessSystem` simulations
- Multiple output formats: JSON, Python scripts, Jupyter notebooks, EOT simulators
- Exporting NeqSim models back to UniSim `.usc` files
- Verifying converted models by comparing stream properties
- Troubleshooting common conversion issues

---

## Prerequisites

| Requirement | Details |
|-------------|---------|
| **Operating System** | Windows (COM automation requires Windows) |
| **UniSim Design** | R460+ installed and licensed (R510+ recommended) |
| **Python** | 3.8+ with `pywin32` (`pip install pywin32`) |
| **NeqSim** | `pip install neqsim` (or local dev build) |

Install the required packages:

```bash
pip install pywin32 neqsim
```

For local development with the NeqSim repository:

```bash
# From the neqsim repo root
pip install -e devtools/
```

---

## Architecture Overview

The conversion system consists of three main classes in `devtools/unisim_reader.py` and one class in `devtools/unisim_writer.py`:

| Class | Module | Purpose |
|-------|--------|---------|
| `UniSimReader` | `unisim_reader.py` | Opens `.usc` files via COM, extracts all process data |
| `UniSimToNeqSim` | `unisim_reader.py` | Converts extracted model to NeqSim JSON, Python, or notebook |
| `UniSimComparator` | `unisim_reader.py` | Compares UniSim vs NeqSim results for verification |
| `UniSimWriter` | `unisim_writer.py` | Creates UniSim `.usc` files from NeqSim JSON exports |

### Data Flow

```
UniSim .usc file
       │
       ▼  (COM automation)
   UniSimReader  ──────►  UniSimModel (Python dataclasses)
                               │
                               ▼
                        UniSimToNeqSim
                       ┌───────┼───────────────┐
                       ▼       ▼               ▼
                    to_json()  to_python()  to_notebook()
                       │       │               │
                       ▼       ▼               ▼
                  NeqSim JSON  Python script   Jupyter notebook
                       │
                       ▼
            ProcessSystem.fromJsonAndRun()
                       │
                       ▼
              Running NeqSim simulation
                       │
                       ▼  (reverse direction)
              ProcessSystem.toJson()
                       │
                       ▼
                  UniSimWriter  ──────►  New .usc file
```

---

## Part 1: UniSim to NeqSim (Reading .usc Files)

### Quick Start

```python
from devtools.unisim_reader import UniSimReader, UniSimToNeqSim

# Step 1: Read the UniSim file
with UniSimReader(visible=False) as reader:
    model = reader.read(r"C:\path\to\model.usc")

# Step 2: Inspect what was extracted
print(model.summary())

# Step 3: Convert and run in NeqSim
converter = UniSimToNeqSim(model)
process = converter.build_and_run()
```

### Step-by-Step Walkthrough

#### 1. Reading the UniSim File

The `UniSimReader` opens UniSim Design via COM automation, pauses the solver, and extracts all process data:

```python
from devtools.unisim_reader import UniSimReader

# Create reader (visible=True shows the UniSim GUI for debugging)
reader = UniSimReader(visible=False)

# Read a single file
model = reader.read(r"C:\Models\GasPlant.usc")

# Or read multiple files efficiently (one UniSim session)
models = reader.read_multiple([
    r"C:\Models\GasPlant.usc",
    r"C:\Models\OilProcess.usc",
])

# Always close when done (or use context manager)
reader.close()
```

**Extracted data includes:**

- **Fluid packages**: EOS type, component list, property package name
- **Material streams**: Temperature, pressure, flow rate, composition, density, molecular weight, vapour fraction, enthalpy
- **Energy streams**: Heat flow
- **Unit operations**: Type, feed/product connections, type-specific properties (efficiency, outlet pressure, duty, etc.)
- **Sub-flowsheets**: Recursively extracted with full connectivity

#### 2. Inspecting the Extracted Model

```python
# Human-readable summary
print(model.summary())
```

Example output:

```
UniSim Model: GasPlant.usc
  Fluid Packages: 1
    'Basis-1': Peng-Robinson (7 components)
  Main Flowsheet: 'Main'
    Streams: 13
    Operations: 11
    Sub-Flowsheets: 2
      'Compression': 4 ops, 5 streams
      'Dehydration': 3 ops, 4 streams
    Operation types:
      compressor: 3
      coolerop: 2
      sep3op: 2
      valveop: 2
      mixerop: 1
      heatexop: 1
```

Access individual elements:

```python
# All operations (including sub-flowsheets)
for op in model.all_operations():
    print(f"{op.name}: {op.type_name} | feeds={op.feeds} | products={op.products}")

# All streams
for s in model.all_streams():
    print(f"{s.name}: T={s.temperature_C}°C, P={s.pressure_bara} bara, "
          f"flow={s.mass_flow_kgh} kg/h")

# Fluid package details
fp = model.fluid_packages[0]
print(f"EOS: {fp.property_package}")
print(f"Components: {fp.component_names}")

# Stream composition
for s in model.flowsheet.material_streams:
    if s.composition:
        print(f"\n{s.name}:")
        for comp, frac in s.composition.items():
            print(f"  {comp}: {frac:.4f}")
```

#### 3. Converting to NeqSim

The `UniSimToNeqSim` converter offers multiple output formats:

##### Option A: JSON Builder Format (for ProcessSystem.fromJsonAndRun)

```python
import json
from devtools.unisim_reader import UniSimToNeqSim

converter = UniSimToNeqSim(model)
neqsim_json = converter.to_json()

# Save for inspection
with open("process.json", "w") as f:
    json.dump(neqsim_json, f, indent=2)

# Check for warnings
for w in converter.warnings:
    print(f"WARNING: {w}")

# Build and run directly
from neqsim import jneqsim
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
result = ProcessSystem.fromJsonAndRun(json.dumps(neqsim_json))

if not result.isError():
    process = result.getProcessSystem()
    print(f"Units built: {process.size()}")
```

##### Option B: Standalone Python Script

Generates a complete, runnable Python file with explicit `jneqsim` API calls:

```python
converter = UniSimToNeqSim(model)
python_code = converter.to_python(include_subflowsheets=True)

# Save to file
with open("gas_plant_process.py", "w") as f:
    f.write(python_code)
print(f"Generated {len(python_code.splitlines())} lines of Python")
```

The generated script includes:

1. All `jneqsim` imports
2. Fluid/EOS definition with mapped composition and mixing rule
3. Feed streams with temperature, pressure, and flow rate from UniSim
4. All equipment in **topological order** (upstream before downstream)
5. Equipment properties (efficiency, outlet pressure, etc.)
6. Stream wiring through outlet stream references
7. Auto-generated recycle blocks for forward-referenced streams
8. `process.run()` call

##### Option C: Jupyter Notebook

Interactive notebook with markdown explanations between code cells:

```python
converter = UniSimToNeqSim(model)
converter.save_notebook("gas_plant.ipynb")
```

The notebook contains:

1. Title and overview table (components, operations, EOS)
2. Setup cell with imports
3. Fluid creation with composition table
4. Feed stream creation with properties table
5. One markdown + code cell per equipment unit
6. Process run cell
7. Results summary cell
8. Conversion warnings cell

##### Option D: EOT / ProcessPilot Simulator

For reinforcement learning and optimization frameworks:

```python
converter = UniSimToNeqSim(model)
converter.save_eot_simulator("my_simulator.py", class_name="GasPlantSimulator")

# Also generate a demo notebook for the EOT simulator
nb = converter.to_eot_notebook(class_name="GasPlantSimulator")
with open("eot_demo.ipynb", "w") as f:
    json.dump(nb, f, indent=1)
```

#### 4. Command-Line Interface

```bash
# Print model summary
python devtools/unisim_reader.py C:\Models\file.usc --summary

# Output NeqSim JSON
python devtools/unisim_reader.py C:\Models\file.usc --json

# Generate Python script
python devtools/unisim_reader.py C:\Models\file.usc --python process.py

# Generate Jupyter notebook
python devtools/unisim_reader.py C:\Models\file.usc --notebook process.ipynb

# Generate EOT simulator + demo notebook
python devtools/unisim_reader.py C:\Models\file.usc --eot sim.py --eot-notebook demo.ipynb

# Fast extraction (topology only, no stream properties)
python devtools/unisim_reader.py C:\Models\file.usc --no-streams --summary

# Show UniSim GUI during extraction (debugging)
python devtools/unisim_reader.py C:\Models\file.usc --visible --summary

# Combined: multiple outputs in one command
python devtools/unisim_reader.py C:\Models\file.usc --python p.py --notebook n.ipynb --eot s.py
```

---

## Part 2: NeqSim to UniSim (Writing .usc Files)

The `UniSimWriter` in `devtools/unisim_writer.py` creates UniSim Design simulations from NeqSim JSON exports.

### Quick Start

```python
from devtools.unisim_writer import UniSimWriter

# From a NeqSim JSON string (e.g., ProcessSystem.toJson())
writer = UniSimWriter(visible=True)
writer.build_from_json(neqsim_json_str, save_path="output.usc")
writer.close()
```

### From a Running NeqSim ProcessSystem

```python
from neqsim import jneqsim

# ... build and run your NeqSim process ...
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
# process = ProcessSystem()
# ... add equipment ...
# process.run()

# Export to JSON
json_str = str(process.toJson())

# Create UniSim file
from devtools.unisim_writer import UniSimWriter
writer = UniSimWriter(visible=True)
writer.build_from_json(json_str, save_path="my_process.usc")
writer.close()
```

### From a Multi-Area ProcessModel

```python
from neqsim import jneqsim

# Multi-area process
ProcessModel = jneqsim.process.processmodel.ProcessModel
# plant = ProcessModel("Platform")
# plant.add("Separation", sep_process)
# plant.add("Compression", comp_process)
# plant.run()

json_str = str(plant.toJson())

writer = UniSimWriter(visible=True)
writer.build_from_json(json_str, save_path="platform.usc")
writer.close()
```

### Using a Template File

For best results, open an existing licensed-saved `.usc` file as a template:

```python
writer = UniSimWriter(visible=True, template_path=r"C:\Templates\blank.usc")
writer.build_from_json(neqsim_json_str, save_path="output.usc")
writer.close()
```

### Reverse Mapping Tables

The writer uses reverse mappings to translate NeqSim names back to UniSim:

**Component names** (NeqSim to UniSim):

| NeqSim | UniSim |
|--------|--------|
| `nitrogen` | `Nitrogen` |
| `CO2` | `CO2` |
| `methane` | `Methane` |
| `ethane` | `Ethane` |
| `propane` | `Propane` |
| `i-butane` | `i-Butane` |
| `n-butane` | `n-Butane` |
| `water` | `H2O` |
| `MEG` | `EGlycol` |
| `TEG` | `TEGlycol` |
| `hydrogen` | `Hydrogen` |
| `H2S` | `H2S` |

**EOS mapping** (NeqSim to UniSim):

| NeqSim Model | UniSim Property Package |
|--------------|------------------------|
| `SRK` | `SRK` |
| `PR` | `Peng-Robinson` |
| `CPA` | `CPA` |
| `GERG2008` | `GERG 2008` |

**Equipment mapping** (NeqSim to UniSim):

| NeqSim Type | UniSim TypeName |
|-------------|-----------------|
| `ThrottlingValve` | `valveop` |
| `Separator` | `flashtank` |
| `ThreePhaseSeparator` | `sep3op` |
| `Mixer` | `mixerop` |
| `Splitter` | `teeop` |
| `Compressor` | `compressor` |
| `Cooler` | `coolerop` |
| `Heater` | `heaterop` |
| `Pump` | `pumpop` |
| `Expander` | `expandop` |
| `HeatExchanger` | `heatexop` |
| `Recycle` | `recycle` |
| `AdiabaticPipe` | `pipeseg` |

---

## Mapping Reference

### Component Name Mapping (UniSim to NeqSim)

The reader automatically maps UniSim component names to NeqSim database names:

| UniSim Name | NeqSim Name | Notes |
|-------------|-------------|-------|
| `Nitrogen` / `N2` | `nitrogen` | |
| `CO2` / `CarbonDioxide` | `CO2` | |
| `Methane` / `C1` | `methane` | |
| `Ethane` / `C2` | `ethane` | |
| `Propane` / `C3` | `propane` | |
| `i-Butane` / `iC4` | `i-butane` | |
| `n-Butane` / `nC4` | `n-butane` | |
| `i-Pentane` / `iC5` | `i-pentane` | |
| `n-Pentane` / `nC5` | `n-pentane` | |
| `n-Hexane` / `nC6` | `n-hexane` | |
| `n-Heptane` / `nC7` | `n-heptane` | |
| `n-Octane` / `nC8` | `n-octane` | |
| `n-Nonane` / `nC9` | `n-nonane` | |
| `n-Decane` / `nC10` | `nC10` | |
| `nC11` - `nC20` | `nC11` - `nC20` | Direct mapping |
| `H2O` / `Water` | `water` | |
| `EGlycol` | `MEG` | Mono-ethylene glycol |
| `TEGlycol` | `TEG` | Triethylene glycol |
| `DEGlycol` | `DEG` | Diethylene glycol |
| `MeOH` / `Methanol` | `methanol` | |
| `Hydrogen` / `H2` | `hydrogen` | |
| `H2S` | `H2S` | |
| `Oxygen` / `O2` | `oxygen` | |
| `Argon` / `Ar` | `argon` | |
| `Helium` / `He` | `helium` | |
| `Benzene` | `benzene` | |
| `Toluene` | `toluene` | |
| `CO` / `CarbonMonoxide` | `CO` | |
| `NH3` / `Ammonia` | `ammonia` | |
| `Ethylene` / `Ethene` | `ethylene` | |
| `Propylene` / `Propene` | `propene` | |
| `DEAmine` | `DEA` | |
| `MEAmine` | `MEA` | |
| `MDEAmine` | `MDEA` | |
| `12C3Oxide` | *Not mapped* | Propylene oxide not in NeqSim |

**Hypothetical components** (names ending with `*`, e.g., `C7 GRAND*`) are not mapped and require manual C7+ characterization in NeqSim.

### Property Package / EOS Mapping (UniSim to NeqSim)

| UniSim Property Package | NeqSim EOS | Mixing Rule | Notes |
|-------------------------|------------|-------------|-------|
| `Peng-Robinson` | `PR` | `classic` | Most common for oil and gas |
| `SRK` / `Soave-Redlich-Kwong` | `SRK` | `classic` | |
| `CPA` / `CPA-SRK` | `CPA` | `10` | Polar systems (water, glycols) |
| `Glycol Package` | `CPA` | `10` | MEG/TEG systems |
| `GERG 2008` | `GERG2008` | (built-in) | Natural gas metering |
| `Sour PR` / `Sour SRK` | `PR` / `SRK` | `classic` | H2S/CO2 systems |
| `ASME Steam` | `SRK` | `classic` | *Fallback* — water only |
| `MBWR` | `SRK` | `classic` | *Fallback* — no LKP in NeqSim |
| `NRTL` / `UNIQUAC` / `Wilson` | `SRK` | `classic` | *Fallback* — activity models |
| `DBR Amine Package` | `SRK` | `classic` | *Fallback* |
| `OLI` | `SRK` | `classic` | *Fallback* — electrolyte |

A warning is logged when a fallback mapping is used.

### Operation Type Mapping (UniSim to NeqSim)

#### Core Process Equipment

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `valveop` | `ThrottlingValve` | Pressure letdown valve |
| `sep3op` | `ThreePhaseSeparator` | Three-phase separator |
| `flashtank` | `Separator` | Two-phase flash drum |
| `mixerop` | `Mixer` | Stream mixer |
| `teeop` | `Splitter` | Stream splitter/tee |
| `compressor` | `Compressor` | Gas compressor |
| `coolerop` | `Cooler` | Cooler |
| `heaterop` | `Heater` | Heater |
| `pumpop` | `Pump` | Liquid pump |
| `expandop` | `Expander` | Turboexpander |
| `heatexop` | `HeatExchanger` | Shell-and-tube / plate HX |
| `pipeseg` | `AdiabaticPipe` | Pipe segment |
| `recycle` | `Recycle` | Recycle convergence block |
| `adjust` | `Adjuster` | Process variable adjuster |
| `saturateop` | `StreamSaturatorUtil` | Stream saturator |

#### Columns and Absorbers

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `fractop` / `distillation` / `columnop` | `DistillationColumn` | Distillation column |
| `reboiledabsorber` | `DistillationColumn` | Reboiled absorber |
| `absorberop` / `absorber` | `Absorber` or `ComponentSplitter` | See glycol contactor rule below |

**Glycol/TEG contactor rule**: When an absorber has a name containing "glyc", "teg", or "dehydrat" (case-insensitive), the converter produces a `ComponentSplitter` instead of a `DistillationColumn`. This models water removal using split factors: `[1.0, 1.0, ..., 0.0]` where water (last component) is fully removed.

#### Reactors

| UniSim TypeName | NeqSim Type |
|-----------------|-------------|
| `reactorop` / `gibbsreactorop` / `eqreactorop` | `GibbsReactor` |
| `convreactorop` / `conversionreactorop` | `GibbsReactor` |
| `pfreactorop` / `kineticreactorop` | `PlugFlowReactor` |
| `cstrop` | `StirredTankReactor` |

#### Skipped Operations

These operations are recognized but produce comment lines only:

| UniSim TypeName | Reason |
|-----------------|--------|
| `spreadsheetop` | Calculation-only, no process equivalent |
| `pidfbcontrolop` | Control logic, produces TODO comment |
| `surgecontroller` | Surge control, no standalone NeqSim equivalent |
| `partialcondenser` / `totalcondenser` / `traysection` / `bpreboiler` | Column internals (part of `DistillationColumn`) |
| `logicalop` / `selectop` / `balanceop` | Logic operations |

---

## Topology Reconstruction

The converter automatically reconstructs the process topology from UniSim's stream-based connectivity:

### How It Works

1. **Identify external feeds**: Streams not produced by any operation become feed `Stream` objects
2. **Build producer map**: Maps each stream name to (operation, port)
3. **Topological sort**: Kahn's algorithm orders operations so upstream equipment is defined before downstream consumers
4. **Handle recycle loops**: Forward reference placeholders break dependency cycles

### Stream Wiring (Dot-Notation)

The converter uses dot-notation to reference equipment outlet streams:

| Producer Type | Stream | NeqSim Reference | API Call |
|--------------|--------|------------------|----------|
| Separator (gas) | 1st product | `"Sep.gasOut"` | `sep.getGasOutStream()` |
| Separator (liquid) | 2nd product | `"Sep.liquidOut"` | `sep.getLiquidOutStream()` |
| 3-Phase Sep (gas) | 1st product | `"Sep.gasOut"` | `sep.getGasOutStream()` |
| 3-Phase Sep (oil) | 2nd product | `"Sep.oilOut"` | `sep.getOilOutStream()` |
| 3-Phase Sep (water) | 3rd product | `"Sep.waterOut"` | `sep.getWaterOutStream()` |
| Compressor | output | `"Comp.outlet"` | `comp.getOutletStream()` |
| Cooler/Heater | output | `"Cooler.outlet"` | `cooler.getOutletStream()` |
| Valve | output | `"Valve.outlet"` | `valve.getOutletStream()` |
| Mixer | output | `"Mixer.outlet"` | `mixer.getOutletStream()` |
| Splitter | nth output | `"Split.split0"` | `split.getSplitStream(int(0))` |
| HeatExchanger | shell out | `"HX.hx0"` | `hx.getOutStream(int(0))` |
| HeatExchanger | tube out | `"HX.hx1"` | `hx.getOutStream(int(1))` |

### Forward Reference Handling (Recycle Loops)

When the topological sort detects cycles, the converter creates **forward reference placeholders** — temporary `Stream` objects that stand in for not-yet-created equipment outlets:

1. **Cycle detection**: Back-edge producers become forward references
2. **Placeholder creation**: `_fwd_XXX` streams are created with T, P, flow from UniSim data
3. **Port-specific placeholders**: Multi-outlet equipment (separators) gets per-port placeholders (e.g., `_fwd_V100_gasOut`, `_fwd_V100_liquidOut`)
4. **Auto-recycle wiring**: After the actual equipment is created, `Recycle` objects wire each real outlet back to its placeholder

**Why port-specific placeholders matter**: Without them, a valve downstream of a separator's liquid outlet would incorrectly receive the combined feed placeholder, causing 500%+ flow deviations.

Example generated code for a recycle loop:

```python
# Forward reference placeholder for separator V-100
_fwd_V_100_gasOut = Stream("V-100_gasOut_fwdref", fluid.clone())
_fwd_V_100_gasOut.setTemperature(15.0, "C")
_fwd_V_100_gasOut.setPressure(84.0, "bara")
_fwd_V_100_gasOut.setFlowRate(45000.0, "kg/hr")
process.add(_fwd_V_100_gasOut)

# ... downstream equipment uses _fwd_V_100_gasOut ...

# Actual separator (created later in topological order)
V_100 = Separator("V-100", cooled_feed)
process.add(V_100)

# Auto-recycle: wire actual outlet back to placeholder
_rcy_V_100_gasOut = Recycle("V-100_gasOut_loop")
_rcy_V_100_gasOut.addStream(V_100.getGasOutStream())
_rcy_V_100_gasOut.setOutletStream(_fwd_V_100_gasOut)
process.add(_rcy_V_100_gasOut)
```

---

## Handling Sub-Flowsheets

UniSim uses sub-flowsheets (template operations) for modular process sections. The converter supports two strategies:

| Model Complexity | Strategy | When to Use |
|-----------------|----------|-------------|
| Main + 1-2 small sub-flowsheets | Flatten into single `ProcessSystem` | Simple models |
| Main + 3+ sub-flowsheets | Separate `ProcessSystem` per area in `ProcessModel` | Platform/plant models |
| Sub-flowsheet has own fluid package | Must be separate `ProcessSystem` | Different thermodynamic bases |

### Flattened Approach (Default)

By default, `to_python(include_subflowsheets=True)` flattens all sub-flowsheet operations into the main process:

```python
converter = UniSimToNeqSim(model)
code = converter.to_python(include_subflowsheets=True)
```

### ProcessModel Approach (Large Models)

For complex platform models (e.g., Grane with 6 sub-flowsheets):

```python
from neqsim import jneqsim
ProcessModel = jneqsim.process.processmodel.ProcessModel

# Build each area as a separate ProcessSystem
plant = ProcessModel("Grane")
plant.add("Main", main_process)
plant.add("Breidablikk", breidablikk_process)
plant.add("LP_Inlet", lp_process)
plant.add("HP_Inlet", hp_process)
plant.add("DPC_Unit", dpc_process)
plant.run()
```

---

## Verification

Always verify the converted model by comparing UniSim and NeqSim stream results.

### Using the Comparator

```python
from devtools.unisim_reader import UniSimComparator

comparator = UniSimComparator(model, neqsim_process)
comparisons = comparator.compare_streams()
comparator.print_report(comparisons)
```

### Using Comparison Points

```python
converter = UniSimToNeqSim(model)
comparison_points = converter.get_comparison_points()

for pt in comparison_points:
    print(f"{pt['stream']}: T={pt['temperature_C']:.1f}°C, "
          f"P={pt['pressure_bara']:.1f} bara")
```

### Expected Deviations

| Property | Typical | Acceptable | Notes |
|----------|---------|------------|-------|
| Temperature | less than 1°C | less than 3°C | Flash calculation differences |
| Pressure | 0% | 0% | Should match exactly (input) |
| Mass flow | less than 0.1% | less than 1% | Mass balance differences |
| Density | less than 2% | less than 5% | EOS differences (PR vs SRK) |
| Vapour fraction | less than 0.02 | less than 0.05 | Phase split sensitivity |
| Compressor power | less than 5% | less than 10% | Efficiency model differences |
| Heat duty | less than 5% | less than 10% | Enthalpy model differences |

### Factors Causing Deviations

1. **EOS alpha functions**: UniSim PR-LK uses different alpha functions than NeqSim PR
2. **Binary interaction parameters**: UniSim may have tuned BIPs that are not extracted
3. **Hypothetical components**: Pseudo-component property estimation differs between tools
4. **Mixing rules**: UniSim may use advanced mixing rules not available in NeqSim
5. **Transport properties**: Different correlations for viscosity and thermal conductivity
6. **HeatExchanger pressure drops**: NeqSim HeatExchanger does not model pressure drops; UniSim typically includes 0.5-1.0 bar per side
7. **HeatExchanger UA tuning**: The `setUAvalue()` parameter must be tuned case-by-case to match UniSim's heat duty

---

## UniSim COM Object Model Reference

For advanced users or debugging, here is the UniSim COM hierarchy:

```
Application
  SimulationCases
    Case
      Solver (CanSolve, Converge)
      BasisManager
        FluidPackages[]
          name, PropertyPackageName
          Components[]
            name
      Flowsheet (main)
        MaterialStreams[]
          name
          Temperature.GetValue("C")
          Pressure.GetValue("bar")
          MassFlow.GetValue("kg/h")
          MolarFlow.GetValue("kgmole/h")
          ComponentMolarFraction.GetValues()
        EnergyStreams[]
          name
          HeatFlow.GetValue("kW")
        Operations[]
          name, TypeName
          (connectivity varies by type — see below)
        Flowsheets[] (sub-flowsheets, recursive)
```

### COM Connectivity Patterns by Operation Type

Different UniSim operation types expose different COM properties for feed/product streams:

| Operation Type | Feed Property | Product Property |
|----------------|---------------|------------------|
| compressor, coolerop, heaterop, valveop, pumpop, expandop, recycle | `FeedStream` | `ProductStream` |
| mixerop | `Feeds[]` (array) | `Product` (singular!) |
| teeop | `FeedStream` | `Products[]` (array) |
| flashtank, sep3op | `Feeds[]` (array) | `VapourProduct`, `LiquidProduct`, `WaterProduct` |
| heatexop | `ShellSideFeed`/`TubeSideFeed` or `Feeds[]` | `ShellSideProduct`/`TubeSideProduct` or `Products[]` |

**Warning**: Using `op.Products` on a mixer throws `AttributeError`. Use `op.Product` (singular) for mixers and `op.VapourProduct`/`op.LiquidProduct` for separators.

### Key COM Patterns

```python
import win32com.client
import time

# Start UniSim
app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
app.Visible = True

# Open case and pause solver
case = app.SimulationCases.Open(r'C:\path\to\file.usc')
time.sleep(3)
solver = case.Solver
solver.CanSolve = False

# Read stream data
fs = case.Flowsheet
stream = fs.MaterialStreams.Item(0)
temp_C = stream.Temperature.GetValue('C')
pres_bar = stream.Pressure.GetValue('bar')
flow_kgh = stream.MassFlow.GetValue('kg/h')
comp_fracs = stream.ComponentMolarFraction.GetValues()

# Read operation data
op = fs.Operations.Item(0)
print(f"Type: {op.TypeName}, Name: {op.name}")

# Clean up
case.Close()
app.Quit()
```

---

## Exploring an Unknown UniSim File

Use the diagnostic tool `devtools/explore_unisim_com.py` to dump the complete COM object model from any `.usc` file:

```bash
python devtools/explore_unisim_com.py C:\Models\unknown_model.usc
```

This prints all fluid packages, components, streams, operations, and their properties — useful for understanding a new model before conversion.

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `pywintypes.com_error` | UniSim not installed or wrong version | Verify UniSim installation, check COM ProgID |
| Empty stream values | Stream not solved in UniSim | Open in UniSim GUI, run solver first |
| `-32767` values | UniSim empty marker | Already filtered by reader (`val > -30000`) |
| COM timeout | Large model loading | Increase `time.sleep()` after `.Open()` |
| Wrong component count | Multiple fluid packages | Check which fluid package the stream uses |
| Missing operations | Sub-flowsheet not recursed | Use `model.all_operations()` |
| Composition doesn't sum to 1.0 | Hypothetical components excluded | Re-normalize after filtering |
| Compressor outlet T too low | Efficiency defaulted to 100% | Reader defaults to 75% if COM returns None |
| Valve flow 500%+ deviation | Wrong forward reference placeholder | Ensure port-specific placeholders (fixed in current version) |
| Recycle not converging | Many forward references | Try multiple `process.run()` calls or tune placeholder values |
| Column diverges | C3/C4-rich feed at low pressure | Known NeqSim solver limitation; build column outside ProcessSystem |
| HX outlet T off by 1-2°C | UA mismatch or no pressure drop | Tune UA value; NeqSim HX has no pressure drop model |
| `AttributeError` on COM property | Wrong property for operation type | Check `TypeName` and use correct COM pattern |
| COM E_ACCESSDENIED | Stale type-library cache | Clear `%LOCALAPPDATA%\Temp\gen_py` folder |

---

## Complete Example: Gas Processing Plant

This end-to-end example reads a UniSim gas plant model and creates a verified NeqSim simulation:

```python
import json
from devtools.unisim_reader import UniSimReader, UniSimToNeqSim, UniSimComparator

# 1. Read the UniSim file
with UniSimReader(visible=False) as reader:
    model = reader.read(r"C:\Models\GasPlant.usc")

print(model.summary())

# 2. Convert to NeqSim
converter = UniSimToNeqSim(model)

# Check warnings
for w in converter.warnings:
    print(f"  WARNING: {w}")
for a in converter.assumptions:
    print(f"  ASSUMPTION: {a}")

# 3. Generate a standalone Python script (for code review)
python_code = converter.to_python(include_subflowsheets=True)
with open("gas_plant.py", "w") as f:
    f.write(python_code)

# 4. Generate a Jupyter notebook (for interactive exploration)
converter.save_notebook("gas_plant.ipynb")

# 5. Build and run the NeqSim model via JSON
neqsim_json = converter.to_json()
from neqsim import jneqsim
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
result = ProcessSystem.fromJsonAndRun(json.dumps(neqsim_json))

if not result.isError():
    process = result.getProcessSystem()
    print(f"\nNeqSim model built: {process.size()} units")

    # 6. Verify results
    comparator = UniSimComparator(model, process)
    comparisons = comparator.compare_streams()
    comparator.print_report(comparisons)
```

---

## Related Documentation

- [Process Simulation Package](README.md) — Overview of NeqSim process simulation
- [ProcessSystem and Flowsheet Management](processmodel/) — How ProcessSystem and ProcessModel work
- [Jupyter Development Workflow](../development/jupyter_development_workflow.md) — Using notebooks for NeqSim development
- [UniSim Conversion Notebook](../../examples/notebooks/unisim_to_neqsim_conversion.ipynb) — Interactive example notebook

---

## File Reference

| File | Purpose |
|------|---------|
| `devtools/unisim_reader.py` | Core module: `UniSimReader`, `UniSimToNeqSim`, `UniSimComparator` |
| `devtools/unisim_writer.py` | Reverse direction: `UniSimWriter` — NeqSim JSON to `.usc` |
| `devtools/explore_unisim_com.py` | Diagnostic tool: dump COM object model from any `.usc` file |
| `devtools/test_unisim_outputs.py` | 14 pytest tests for all output modes (no COM needed) |
| `examples/notebooks/unisim_to_neqsim_conversion.ipynb` | Example notebook with test cases |
