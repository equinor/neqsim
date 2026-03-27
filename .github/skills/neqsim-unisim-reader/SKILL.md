---
name: neqsim-unisim-reader
description: "Reads Honeywell UniSim Design / Aspen HYSYS .usc files via COM automation and converts them to NeqSim ProcessSystem / ProcessModule structures. USE WHEN: a user has UniSim/HYSYS simulation files and wants to recreate or compare the model in NeqSim. Covers COM API navigation, component mapping, EOS mapping, operation type mapping, topology reconstruction, sub-flowsheet handling, and result verification."
---

# UniSim Design / HYSYS → NeqSim Conversion Skill

Convert Honeywell UniSim Design (.usc) files into NeqSim ProcessSystem or
ProcessModule structures using Windows COM automation.

## Prerequisites

- **Windows only** — UniSim Design must be installed (COM server)
- **Python packages**: `pywin32` (`pip install pywin32`)
- **UniSim Design R510+** (R460+ should also work)
- COM ProgID: `UnisimDesign.Application`

## Core Module

The `devtools/unisim_reader.py` module provides three main classes:

| Class | Purpose |
|-------|---------|
| `UniSimReader` | Opens .usc files via COM, extracts all data |
| `UniSimToNeqSim` | Converts extracted model to NeqSim JSON builder format or standalone Python code |
| `UniSimComparator` | Compares UniSim vs NeqSim results for verification |

---

## 1. UniSim COM Object Model

UniSim's COM automation exposes the following hierarchy:

```
Application
├── SimulationCases
│   └── Case
│       ├── Solver (CanSolve, Converge)
│       ├── BasisManager
│       │   └── FluidPackages[]
│       │       ├── name, PropertyPackageName
│       │       └── Components[]
│       │           └── name
│       └── Flowsheet (main)
│           ├── MaterialStreams[]
│           │   ├── name
│           │   ├── Temperature.GetValue("C")
│           │   ├── Pressure.GetValue("bar")
│           │   ├── MassFlow.GetValue("kg/h")
│           │   ├── MolarFlow.GetValue("kgmole/h")
│           │   ├── MassDensity.GetValue("kg/m3")
│           │   ├── MolecularWeight.GetValue()
│           │   ├── VapourFraction.GetValue()
│           │   ├── MassEnthalpy.GetValue("kJ/kg")
│           │   ├── ComponentMolarFraction.GetValues()
│           │   └── ComponentMolarFraction.SetValues([...])
│           ├── EnergyStreams[]
│           │   ├── name
│           │   └── HeatFlow.GetValue("kW")
│           ├── Operations[]
│           │   ├── name, TypeName
│           │   ├── Feeds[] (multi-feed ops: mixers, separators)
│           │   ├── Products[] (multi-product ops: tee/splitter)
│           │   ├── FeedStream / ProductStream (single-stream ops)
│           │   ├── Product (singular, for mixers)
│           │   ├── VapourProduct, LiquidProduct, WaterProduct (separators)
│           │   ├── EnergyFeeds[], EnergyProducts[]
│           │   └── Type-specific: DutyValue, AdiabaticEfficiency,
│           │       PolytropicEfficiency, PressureDrop, Length, Diameter
│           └── Flowsheets[] (sub-flowsheets, recursive)
```

### CRITICAL: Operation Connectivity Patterns

UniSim COM uses **different** property names for different operation types.
You MUST check multiple patterns to extract feed/product connections:

| Operation Type | Feed Source | Product Source |
|---|---|---|
| **compressor** | `FeedStream` (single) | `ProductStream` (single) |
| **coolerop / heaterop** | `FeedStream` (single) | `ProductStream` (single) |
| **valveop** | `FeedStream` (single) | `ProductStream` (single) |
| **recycle** | `FeedStream` (single) | `ProductStream` (single) |
| **pumpop / expandop** | `FeedStream` (single) | `ProductStream` (single) |
| **mixerop** | `Feeds[]` (array) | `Product` (singular!) |
| **teeop** | `FeedStream` (single) | `Products[]` (array) |
| **flashtank** | `Feeds[]` (array) | `VapourProduct`, `LiquidProduct` |
| **sep3op** | `Feeds[]` (array) | `VapourProduct`, `LiquidProduct`, `WaterProduct` |
| **heatexop** | Has shell-side / tube-side sub-objects | |

**WARNING**: `op.Products` does NOT exist on mixers and separators — it throws
`AttributeError`. You must use `op.Product` (singular) for mixers and
`op.VapourProduct` / `op.LiquidProduct` for separators.

The recommended extraction order (as implemented in `unisim_reader.py`):
1. Try `Feeds[]` array first (multi-feed ops)
2. Fall back to `FeedStream` (single-stream ops)
3. Try `Products[]` array first (multi-product ops)
4. Try `VapourProduct` / `LiquidProduct` / `WaterProduct` (separators)
5. Try `Product` singular (mixers)
6. Fall back to `ProductStream` (single-stream ops)
```

### Key COM Patterns

```python
import win32com.client
import time

# Start UniSim
app = win32com.client.dynamic.Dispatch('UnisimDesign.Application')
app.Visible = True  # or False for headless

# Open a case
case = app.SimulationCases.Open(r'C:\path\to\file.usc')
time.sleep(3)  # Wait for loading

# Pause solver during extraction
solver = case.Solver
solver.CanSolve = False

# Access data
fs = case.Flowsheet
stream = fs.MaterialStreams.Item(0)
temp_C = stream.Temperature.GetValue('C')
pres_bar = stream.Pressure.GetValue('bar')
flow_kgh = stream.MassFlow.GetValue('kg/h')
comp_fracs = stream.ComponentMolarFraction.GetValues()

# Unit operations
op = fs.Operations.Item(0)
op_type = op.TypeName  # e.g. "compressor", "valveop", "sep3op"
op_name = op.name

# Feed/product streams — varies by operation type!
# For single-stream ops (compressor, valve, cooler, pump, heater):
feed_name = op.FeedStream.name
prod_name = op.ProductStream.name

# For mixers: Feeds[] array + Product singular
for i in range(op.Feeds.Count):
    feed_name = op.Feeds.Item(i).name
prod_name = op.Product.name  # singular!

# For separators: Feeds[] + VapourProduct / LiquidProduct
for i in range(op.Feeds.Count):
    feed_name = op.Feeds.Item(i).name
vap_name = op.VapourProduct.name
liq_name = op.LiquidProduct.name

# For tee/splitter: FeedStream + Products[]
feed_name = op.FeedStream.name
for i in range(op.Products.Count):
    prod_name = op.Products.Item(i).name

# Close
case.Close()
app.Quit()
```

### Important Notes

- Always call `solver.CanSolve = False` before reading to prevent recalculation
- UniSim uses -32767 for empty/unset values — filter these out
- Use `time.sleep()` between COM calls if stability issues arise
- Property values accessed via `.GetValue(unit_string)`
- Composition accessed via `.GetValues()` returning a sequence

---

## 2. Operation Type Mapping

UniSim internal operation type names (from `op.TypeName`) mapped to NeqSim types:

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `valveop` | `ThrottlingValve` | Pressure letdown valve, choke |
| `sep3op` | `ThreePhaseSeparator` | Three-phase separator |
| `flashtank` | `Separator` | Two-phase flash drum/separator |
| `mixerop` | `Mixer` | Stream mixer/junction |
| `teeop` | `Splitter` | Stream splitter/tee |
| `compressor` | `Compressor` | Gas compressor |
| `coolerop` | `Cooler` | Cooler/aftercooler |
| `heaterop` | `Heater` | Heater/pre-heater |
| `pumpop` | `Pump` | Liquid pump |
| `expandop` | `Expander` | Turboexpander |
| `heatexop` | `HeatExchanger` | Shell-and-tube / plate HX |
| `recycle` | `Recycle` | Recycle convergence block |
| `adjust` | `Adjuster` | Adjust/controller (skipped in NeqSim) |
| `setop` | `Set` | Set variable (skipped in NeqSim) |
| `pipeseg` | `AdiabaticPipe` | Pipe segment |
| `fractop` | `DistillationColumn` | Fractionation column |
| `saturateop` | `StreamSaturatorUtil` | Stream saturator |
| `spreadsheetop` | — (skipped) | Spreadsheet calculator |
| `templateop` | — (sub-flowsheet) | Sub-flowsheet template |
| `absorberop` | `Absorber` | Absorption column |
| `reactorop` | — (skipped) | Generic reactor |
| `pfreactorop` | — (skipped) | Plug flow reactor |
| `cstrop` | — (skipped) | Continuous stirred tank reactor |
| `convreactorop` | — (skipped) | Conversion reactor |
| `eqreactorop` | — (skipped) | Equilibrium reactor |
| `gibbsreactorop` | — (skipped) | Gibbs reactor |
| `pidfbcontrolop` | — (skipped) | PID feedback controller |
| `surgecontroller` | — (skipped) | Surge controller |

### Operations Skipped in Conversion

These UniSim operation types have no direct NeqSim equivalent and are skipped:
- `adjust` — NeqSim uses `Adjuster` but wiring differs
- `setop` — Variable set/propagation
- `spreadsheetop` — Calculation spreadsheets
- `templateop` — Mapped to sub-flowsheets, handled separately
- `reactorop` / `pfreactorop` / `cstrop` / `convreactorop` / `eqreactorop` / `gibbsreactorop` — Reactor types (no generic JSON mapping)
- `pidfbcontrolop` / `surgecontroller` — Control logic (not modeled in steady-state)

---

## 3. Component Name Mapping

UniSim component names to NeqSim database names:

| UniSim Name | NeqSim Name |
|-------------|-------------|
| `Nitrogen` | `nitrogen` |
| `CO2` | `CO2` |
| `Methane` | `methane` |
| `Ethane` | `ethane` |
| `Propane` | `propane` |
| `i-Butane` | `i-butane` |
| `n-Butane` | `n-butane` |
| `i-Pentane` | `i-pentane` |
| `n-Pentane` | `n-pentane` |
| `n-Hexane` | `n-hexane` |
| `n-Heptane` | `n-heptane` |
| `n-Octane` | `n-octane` |
| `n-Nonane` | `n-nonane` |
| `n-Decane` | `nC10` |
| `H2O` | `water` |
| `EGlycol` | `MEG` |
| `TEGlycol` | `TEG` |
| `DEGlycol` | `DEG` |
| `MeOH` | `methanol` |
| `Hydrogen` | `hydrogen` |
| `H2S` | `H2S` |
| `Oxygen` | `oxygen` |
| `Argon` | `argon` |
| `Helium` | `helium` |
| `nC11`–`nC24` | `nC11`–`nC24` |
| `Benzene` | `benzene` |
| `Toluene` | `toluene` |
| `E-Benzene` | `ethylbenzene` |
| `m-Xylene` | `m-Xylene` |
| `o-Xylene` | `o-Xylene` |
| `p-Xylene` | `p-Xylene` |
| `COS` | `COS` |
| `Ethylene` | `ethylene` |
| `Propylene` | `propylene` |
| `1-Butene` | `1-butene` |
| `c-Hexane` | `c-hexane` |

### Hypothetical Components

UniSim components ending with `*` are hypothetical (pseudo-components), e.g.:
- `C6 GRAND*`, `C7 GRAND*`, ..., `C55-C80 GRAND*`
- `251116-01*`, `251115-01*` (numbered hypos)

These require C7+ characterization in NeqSim. Strategies:
1. **Skip**: Remove hypos from composition, re-normalize known components
2. **Approximate**: Map to nearest real component by molecular weight
3. **Characterize**: Use NeqSim's `characterisePlusFraction()` with MW and density data

---

## 4. Property Package Mapping

| UniSim Property Package | NeqSim EOS Model | Mixing Rule |
|-------------------------|------------------|-------------|
| `Peng-Robinson` | `PR` | `classic` |
| `Peng-Robinson - LK` | `PR` | `classic` |
| `SRK` | `SRK` | `classic` |
| `Soave-Redlich-Kwong` | `SRK` | `classic` |
| `CPA` / `CPA-SRK` | `CPA` | `10` |
| `GERG 2008` | `GERG2008` | (built-in) |
| `ASME Steam` | `SRK` | `classic` (water only) |
| `Lee-Kesler-Plocker` | `SRK` | `classic` (approx) |
| `NRTL` | `SRK` | `classic` (approx) |
| `MBWR` | `BWRS` | (built-in) |

---

## 5. Workflow: From .usc File to Running NeqSim Model

### Quick Usage (Python)

```python
from devtools.unisim_reader import UniSimReader, UniSimToNeqSim, UniSimComparator

# Step 1: Read the UniSim file
with UniSimReader(visible=False) as reader:
    model = reader.read(r"path\to\file.usc")

# Step 2: Inspect what was extracted
print(model.summary())

# Step 3: Convert to NeqSim JSON
converter = UniSimToNeqSim(model)
neqsim_json = converter.to_json()

# Step 4: View warnings and assumptions
for w in converter.warnings:
    print(f"WARNING: {w}")

# Step 5: Build and run in NeqSim
import json
from neqsim import jneqsim
ProcessSystem = jneqsim.process.processmodel.ProcessSystem
result = ProcessSystem.fromJsonAndRun(json.dumps(neqsim_json))

# Check for partial success (tolerant error handling)
if result.hasWarnings():
    print(f"Warnings: {len(list(result.getWarnings()))}")
    for w in result.getWarnings():
        print(f"  [{w.getCode()}] {w.getMessage()}")

if not result.isError():
    process = result.getProcessSystem()
    print(f"Units built: {process.size()}")
```

### Generate Python Code (Human-Readable Alternative)

Instead of JSON, generate a standalone Python script with explicit `jneqsim` API calls:

```python
converter = UniSimToNeqSim(model)
python_code = converter.to_python(include_subflowsheets=True)

# Save to file
with open("process.py", "w") as f:
    f.write(python_code)
print(f"Generated {len(python_code.splitlines())} lines of Python")
```

The generated script is a **complete, runnable Python file** that includes:
1. All `jneqsim` imports (thermo systems, equipment classes)
2. Fluid/EOS definition with mapped composition and mixing rule
3. Feed streams created with temperature, pressure, and flow rate from UniSim
4. All equipment in **topological order** (upstream before downstream)
5. Equipment properties set via `jneqsim` API calls (efficiency, outlet pressure, etc.)
6. Stream wiring through outlet stream references (e.g., `separator.getGasOutStream()`)
7. Sub-flowsheet operations (if `include_subflowsheets=True`)
8. `process.run()` call at the end

The generated code uses **direct NeqSim Java API calls** — no JSON intermediate.
Equipment that cannot be mapped (reactors, controllers, spreadsheets) is commented
out with a skip reason. This output is ideal for:
- **Code review** — every connection is visible and auditable
- **Manual editing** — users can modify equipment parameters, add controllers
- **Learning** — shows the exact NeqSim API mapping for each UniSim operation

**Example for the Grane platform model:** `to_python()` generates ~850 lines
covering ~180 operations including Splitters, ThreePhaseSeparators, Compressors,
Coolers, Mixers, ThrottlingValves, and sub-flowsheet equipment.

### For Complex Models (Sub-Flowsheets → ProcessModule)

Large UniSim models with sub-flowsheets should be decomposed:

```python
# The converter produces sub_flowsheets as separate process sections
neqsim_json = converter.to_json(include_subflowsheets=True)

# Main process
main_process = neqsim_json['process']

# Sub-flowsheets (each is a separate ProcessSystem)
for sf_name, sf_process in neqsim_json.get('sub_flowsheets', {}).items():
    print(f"Sub-flowsheet '{sf_name}': {len(sf_process)} operations")
```

---

## 6. Result Verification

Always compare UniSim and NeqSim results after conversion:

```python
# After building NeqSim process
comparator = UniSimComparator(model, neqsim_process)
comparisons = comparator.compare_streams()
comparator.print_report(comparisons)
```

### Expected Deviations

| Property | Typical Deviation | Acceptable | Notes |
|----------|------------------|------------|-------|
| Temperature | < 1 °C | < 3 °C | Flash calculation differences |
| Pressure | 0% | 0% | Should match exactly (input) |
| Mass flow | < 0.1% | < 1% | Mass balance differences |
| Density | < 2% | < 5% | EOS differences (PR vs SRK) |
| Vapour fraction | < 0.02 | < 0.05 | Phase split sensitivity |
| Compressor power | < 5% | < 10% | Efficiency model differences |
| Heat duty | < 5% | < 10% | Enthalpy model differences |

### Factors Causing Deviations

1. **EOS differences**: UniSim PR-LK vs NeqSim PR — different alpha functions
2. **BIP (binary interaction parameters)**: UniSim may use tuned BIPs
3. **Hypothetical components**: Pseudo-component property estimation differs
4. **Mixing rules**: UniSim may use advanced mixing rules not available in NeqSim
5. **Transport properties**: Different correlations for viscosity, thermal conductivity
6. **Convergence**: Different solver algorithms, tolerance settings

---

## 7. Stream Topology Reconstruction

UniSim operations reference streams by name via `.Feeds[]` and `.Products[]`.
The converter reconstructs the process topology:

1. **Identify external feeds**: Streams not produced by any operation
2. **Build producer map**: `stream_name → (operation_name, port)`
3. **Topological sort**: Kahn's algorithm to order operations by dependency
4. **Handle cycles**: Recycle blocks break dependency loops

### Dot-notation for NeqSim wiring

| Producer Type | Stream | NeqSim Reference |
|--------------|--------|------------------|
| Separator (gas) | 1st product | `"Sep.gasOut"` |
| Separator (liquid) | 2nd product | `"Sep.liquidOut"` |
| 3-Phase Sep (gas) | 1st product | `"Sep.gasOut"` |
| 3-Phase Sep (oil) | 2nd product | `"Sep.oilOut"` |
| 3-Phase Sep (water) | 3rd product | `"Sep.waterOut"` |
| Compressor | output | `"Comp.outlet"` |
| Cooler/Heater | output | `"Cooler.outlet"` |
| Valve | output | `"Valve.outlet"` |
| Mixer | output | `"Mixer.outlet"` |

---

## 8. Handling Sub-Flowsheets

UniSim uses sub-flowsheets (template operations) for modular process sections.
In NeqSim, these map to either:

1. **Separate ProcessSystem objects** composed in a `ProcessModule`
2. **Flattened into the main ProcessSystem** (simpler but may not handle inter-area recycles)

### Architecture Decision

| Model Complexity | Strategy |
|-----------------|----------|
| Main + 1-2 small sub-flowsheets | Flatten into single ProcessSystem |
| Main + 3+ sub-flowsheets | ProcessModule with separate ProcessSystems |
| Sub-flowsheet has own fluid package | Must be separate ProcessSystem |

### Example: Grane Model Structure

```
Main Flowsheet (146 operations)
├── Breidablikk (18 operations) — well stream preparation
├── Grane_LP (16 operations) — low pressure inlet
├── Grane_HP (6 operations) — high pressure inlet
├── TPL1 (6 operations) — test separator
├── DPC_UNIT (20 operations) — dew point control
└── HM (41 operations) — heating medium system
```

This would become:
```python
from neqsim import jneqsim
ProcessModule = jneqsim.process.processmodel.ProcessModule

module = ProcessModule("Grane")
module.add(main_process)         # Main separation & compression
module.add(breidablikk_process)  # Breidablikk wells
module.add(grane_lp_process)     # LP inlet
module.add(grane_hp_process)     # HP inlet
module.add(dpc_process)          # Dew point control
# HM (heating medium) typically not modeled in NeqSim
module.run()
```

---

## 9. Command-Line Interface

```bash
# Print model summary
python devtools/unisim_reader.py path/to/file.usc --summary

# Output NeqSim JSON
python devtools/unisim_reader.py path/to/file.usc --json

# Save full extracted model
python devtools/unisim_reader.py path/to/file.usc --save extracted.json

# Fast extraction (topology only, no stream properties)
python devtools/unisim_reader.py path/to/file.usc --no-streams --summary
```

---

## 10. Known Limitations

1. **Windows only** — COM automation requires Windows + UniSim installed
2. **Hypothetical components** — pseudo-components need manual C7+ characterization
3. **Tuned BIPs** — UniSim's tuned binary interaction parameters not extracted
4. **Column internals** — distillation column tray/packing details not fully mapped
5. **Dynamic models** — only steady-state data extracted
6. **Control logic** — PID controllers, logic blocks not converted
7. **Custom correlations** — UniSim's user-defined correlations not transferred
8. **Performance curves** — compressor/pump performance maps not extracted
9. **Multiple fluid packages** — only the first fluid package used for composition

---

## 11. Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `pywintypes.com_error` | UniSim not installed or wrong version | Verify UniSim installation |
| Empty stream values | Stream not solved in UniSim | Open file in UniSim GUI, run solver first |
| `-32767` values | UniSim empty marker | Filter with `val > -30000` check |
| COM timeout | Large model loading | Increase `time.sleep()` after `.Open()` |
| Wrong component count | Multiple fluid packages | Check which FP the stream uses |
| Missing operations | Sub-flowsheet not recursed | Use `model.all_operations()` |
| Composition doesn't sum to 1 | Hypothetical components excluded | Re-normalize after filtering |
