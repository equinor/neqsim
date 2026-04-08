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

### 1.1 Extracting Binary Interaction Parameters (BIPs / kij)

UniSim stores the full BIP (kij) matrix on the PropertyPackage object. The
correct COM access pattern is:

```python
fp = case.Flowsheet.FluidPackage
pp = fp.PropertyPackage

kij_obj = pp.Kij          # Returns CDispatch (RealFlexVariable)
raw = kij_obj.Values      # Returns tuple-of-tuples (n×n matrix)

# IMPORTANT: The .Values property returns the full symmetric matrix.
# Diagonal values are -32767.0 (sentinel for "self-interaction").
# Replace with 0.0 when parsing.
n = fp.Components.Count
comp_names = [fp.Components.Item(i).Name for i in range(n)]

bic = []
for i in range(n):
    row = []
    for j in range(n):
        val = float(raw[i][j])
        if abs(val + 32767.0) < 1.0:  # diagonal sentinel
            val = 0.0
        row.append(val)
    bic.append(row)
```

**Key discoveries:**
- `pp.Kij` returns a `CDispatch` (UniSim RealFlexVariable), NOT a Python-iterable
- `kij_obj.Values` is the correct access pattern — returns tuple-of-tuples
- `kij_obj.GetValues()` fails with "Invalid number of parameters"
- `kij_obj.__call__(i,j)` fails with "Does not support a collection"
- `pp.GetInteractionParameter(i,j)` returns 0.0 for PR-LK (correlation-based
  BIPs are stored internally, not as user-defined parameters)
- The matrix is symmetric: `kij[i][j] == kij[j][i]`
- For PR-LK, BIPs are generated from the Lee-Kesler correlation — they are
  non-zero even if never explicitly tuned by the user

**Common BIP patterns (PR-LK, hydrocarbon system):**
- H2O–HC: +0.48 to +0.50 (strong positive interaction)
- H2O–N2: -2.24 (strong negative)
- H2O–CO2: -0.56
- CO2–C1: +0.105
- N2–C1: +0.025
- HC–HC (light–heavy): small values, typically < 0.01

### 1.2 Extracting Component Thermodynamic Properties

For pseudo-components and library components, extract critical properties
and other thermodynamic data:

```python
fp = case.Flowsheet.FluidPackage
pp = fp.PropertyPackage

n = fp.Components.Count
for i in range(n):
    comp = fp.Components.Item(i)
    name = comp.Name
    mw = comp.MolecularWeight.GetValue()
    tc = comp.CriticalTemperature.GetValue("C")   # °C
    pc = comp.CriticalPressure.GetValue("bar")     # bar
    nbp = comp.NormalBoilingPt.GetValue("C")       # °C
    vc = comp.CriticalVolume.GetValue("m3/kgmole") # m3/kmol
    # Acentric factor — not directly available via .AcentricFactor
    # Use Edmister correlation: w = (3/7) * log10(Pc/1.01325) / (Tc/Tbp - 1) - 1
```

**Notes:**
- Acentric factor is NOT directly accessible via COM for all property packages.
  `comp.AcentricFactor` may not exist. Use the Edmister correlation as fallback.
- Parachor can sometimes be read via `pp.Parachor.Values` (same pattern as Kij).
- Volume shift: `pp.VolumShift.Values` (note the UniSim spelling: "VolumShift",
  not "VolumeShift").

### 1.3 Generating E300 Fluid Files from UniSim Data

To create an Eclipse E300-format fluid file from UniSim-extracted properties
for loading into NeqSim via `EclipseFluidReadWrite.read()`:

**Required E300 sections** (NeqSim reader will crash without these):
- `CNAMES` — component names
- `TCRIT` — critical temperatures (K)
- `PCRIT` — critical pressures (atm)
- `ACF` — acentric factors
- `MW` — molecular weights (g/mol)
- `TBOIL` — normal boiling points (K)
- `VCRIT` — critical volumes (m3/kg-mol)
- `PARACHOR` — parachor values (if unknown: `4.0 * MW^0.77`)
- `SSHIFT` — volume shift parameters (can be all zeros)
- `BIC` — binary interaction coefficients (lower triangular)
- `ZI` — mole fractions

**Optional E300 sections** (NeqSim supports these):
- `BICS` — volume-corrected BICs at surface conditions (parsed but same format as BIC)
- `OMEGAA` — per-component OmegaA override values (one value per line + `/` terminator)
- `OMEGAB` — per-component OmegaB override values (same format)
- `SSHIFTS` — volume shift at surface conditions (same format as SSHIFT)
- `PEDERSEN` — keyword (no values) → activates Pedersen viscosity correlation

**EOS Selection via E300:**
- `EOS\nSRK /` → `SystemSrkEos`
- `EOS\nPR /\nPRCORR` → `SystemPrEos1978` (PR1978 correction)
- `EOS\nPR /\nPRLKCORR` → **`SystemPrLeeKeslerEos`** (PR-LK, PR76 alpha for all ω)
- `EOS\nPR /` → `SystemPrEos` (base PR)

**CRITICAL**: If the BIC section is omitted, NeqSim's `EclipseFluidReadWrite`
will crash with a NullPointerException. Always include BIC, even if all zeros.

**Loading with a forced EOS (ignores EOS keyword in file):**
```python
from neqsim import jneqsim
EclipseFluidReadWrite = jneqsim.thermo.util.readwrite.EclipseFluidReadWrite
SystemPrLeeKeslerEos = jneqsim.thermo.system.SystemPrLeeKeslerEos

# Force PR-LK regardless of EOS in file
fluid = SystemPrLeeKeslerEos(288.15, 1.01325)
fluid = EclipseFluidReadWrite.read(e300_path, fluid)
```

**⚠️ CRITICAL WARNING — Water BIPs and OmegaA:**

When water is present, the water–hydrocarbon BIPs (kij) interact dangerously
with OmegaA. **NEVER mix BIP conventions with OmegaA modifications:**

- Standard E300 BIPs for water–HC are typically **+0.48 to +0.50**
- PR-LK correlation BIPs for H2O–N2 are typically **−2.24** (negative!)
- H2O–CO2 is typically **−0.557**, H2O–H2S is typically **−0.390**

These negative BIPs **intentionally** increase cross-attraction and keep water
in the liquid phase. If you simultaneously set `OMEGAA` for water to a
non-standard value (e.g., 0.42748), the phase behavior will be wrong —
HP Separator vapour fraction can jump from 0.41 to 0.81 (catastrophic).

**Rule**: Only use `OMEGAA` for water together with its matched BIP set.
Default (no OMEGAA section) is safer unless you have a PVTsim-generated file
that was specifically fitted with both OMEGAA and BICs together.

**NeqSim E300 component name mapping:**
| E300 Name | NeqSim Maps To |
|-----------|---------------|
| `C1` | `methane` |
| `C2` | `ethane` |
| `C3` | `propane` |
| `iC4` | `i-butane` |
| `C4` | `n-butane` |
| `iC5` | `i-pentane` |
| `C5` | `n-pentane` |
| `C6` | `n-hexane` |
| `N2` | `nitrogen` |
| `CO2` | `CO2` |
| `H2O` | `water` |
| All others | TBP pseudo-fraction (via `addTBPfraction()`) |

**Note:** Aromatics (Benzene, Toluene, E-Benzene, m-Xylene, etc.) are NOT in
NeqSim's E300 recognized name map — they will be treated as TBP pseudo-fractions
with estimated density.

---

## 2. Operation Type Mapping

UniSim internal operation type names (from `op.TypeName`) mapped to NeqSim types:

### Core Process Equipment

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `valveop` | `ThrottlingValve` | Pressure letdown valve, choke |
| `sep3op` | `ThreePhaseSeparator` | Three-phase separator |
| `flashtank` | `Separator` / `GasScrubber` | Two-phase separator. Auto-promoted to `ThreePhaseSeparator` if `WaterProduct` connected. Vertical orientation → `GasScrubber`. |
| `mixerop` | `Mixer` | Stream mixer/junction |
| `teeop` | `Splitter` | Stream splitter/tee |
| `compressor` | `Compressor` | Gas compressor |
| `coolerop` | `Cooler` | Cooler/aftercooler |
| `heaterop` | `Heater` | Heater/pre-heater |
| `pumpop` | `Pump` | Liquid pump |
| `expandop` | `Expander` | Turboexpander |
| `heatexop` | `HeatExchanger` | Shell-and-tube / plate HX |
| `pipeseg` | `AdiabaticPipe` | Pipe segment |
| `recycle` | `Recycle` | Recycle convergence block |
| `adjust` | `Adjuster` | Process variable adjuster |
| `setop` | `SetPoint` | Set variable/propagation |
| `saturateop` | `StreamSaturatorUtil` | Stream saturator |
| `spreadsheetop` | `Spreadsheet` | Spreadsheet calculator (skipped in code gen) |
| `templateop` | `SubFlowsheet` | Sub-flowsheet template |

### Columns & Absorbers

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `fractop` | `DistillationColumn` | Fractionation column |
| `distillation` | `DistillationColumn` | Distillation column |
| `columnop` | `DistillationColumn` | Generic column |
| `reboiledabsorber` | `DistillationColumn` | Reboiled absorber |
| `absorberop` | `Absorber` | Absorption column (see glycol note below) |
| `absorber` | `Absorber` | Absorber (see glycol note below) |

> **Glycol/TEG Contactor Rule**: When an Absorber operation has a name
> containing "glyc", "teg", or "dehydrat" (case-insensitive), the code
> generator produces a `ComponentSplitter` instead of a `DistillationColumn`.
> This removes water from the gas stream using the Oseberg pattern:
> `setSplitFactors([1.0] * (N-1) + [0.0])` where water is the last component.
> Stream 0 = dry gas, stream 1 = removed water. Port resolution uses
> `split0`/`split1` instead of `gasOut`/`liquidOut`. Non-glycol absorbers
> still use `DistillationColumn`.

### Column Internals (Sub-parts, Not Standalone)

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `partialcondenser` | `ColumnInternals` | Partial condenser (skipped) |
| `totalcondenser` | `ColumnInternals` | Total condenser (skipped) |
| `condenser3op` | `ColumnInternals` | Three-outlet condenser (skipped) |
| `traysection` | `ColumnInternals` | Tray section (skipped) |
| `bpreboiler` | `ColumnInternals` | Reboiler (skipped) |

### Reactors

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `reactorop` | `GibbsReactor` | Generic reactor → Gibbs |
| `gibbsreactorop` | `GibbsReactor` | Gibbs reactor |
| `eqreactorop` | `GibbsReactor` | Equilibrium reactor → Gibbs |
| `equilibriumreactorop` | `GibbsReactor` | Equilibrium reactor (alternate) |
| `convreactorop` | `GibbsReactor` | Conversion reactor → Gibbs |
| `conversionreactorop` | `GibbsReactor` | Conversion reactor (alternate) |
| `pfreactorop` | `PlugFlowReactor` | Plug flow reactor |
| `kineticreactorop` | `PlugFlowReactor` | Kinetic reactor → PFR |
| `cstrop` | `StirredTankReactor` | CSTR |

### Controllers & Logic

| UniSim TypeName | NeqSim Type | Description |
|-----------------|-------------|-------------|
| `pidfbcontrolop` | `PIDController` | PID feedback controller |
| `surgecontroller` | `SurgeController` | Surge controller (skipped) |
| `balanceop` | `BalanceOp` | Balance utility |
| `logicalop` | `LogicalOp` | Logic operation (skipped) |
| `selectop` | `LogicalOp` | Selector (skipped) |

### Operations Always Skipped in Code Generation

The following `SKIPPED_NEQSIM_TYPES` are recognized but produce only
comment lines — they have no standalone NeqSim representation:
- `SurgeController` — Compressor surge control logic
- `ColumnInternals` — Sub-parts of column operations (condenser, reboiler, tray sections)

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
| `SO2` | `SO2` |
| `NH3` / `Ammonia` | `ammonia` |
| `Ethylene` / `Ethene` | `ethylene` |
| `Propylene` / `Propene` | `propene` |
| `1-Butene` | `1-butene` |
| `cis-2-Butene` | `c2-butene` |
| `trans-2-Butene` | `t2-butene` |
| `Isobutene` | `isobutene` |
| `Cyclohexane` | `cyclohexane` |
| `CO` / `CarbonMonoxide` | `CO` |
| `DEAmine` | `DEA` |
| `MEAmine` | `MEA` |
| `MDEAmine` | `MDEA` |
| `AceticAcid` | `acetic acid` |
| `Ethanol` | `ethanol` |
| `c-Hexane` | `c-hexane` |

**Alternate aliases**: The map also includes short-form aliases like `C1`→methane, `C2`→ethane, `N2`→nitrogen, `H2`→hydrogen, `O2`→oxygen, `Ar`→argon, `He`→helium, `iC4`→i-butane, `nC4`→n-butane, `iC5`→i-pentane, `nC5`→n-pentane, `nC6`→n-hexane, etc.

**Unmapped components**: `12C3Oxide` (propylene oxide) maps to `None` — it is not in the NeqSim database and will be skipped with a warning.

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

The code variable is `PROPERTY_PACKAGE_MAP` (not EOS_MAP). It maps UniSim
property package names (including common spelling variants) to NeqSim EOS
model strings:

### Primary Mappings

| UniSim Property Package | NeqSim EOS Model | Mixing Rule | Notes |
|-------------------------|------------------|-------------|-------|
| `Peng-Robinson` / `PengRobinson` / `Peng Robinson` | `PR` | `classic` | |
| `Peng-Robinson - LK` / `Peng Robinson - LK` | `PR` | `classic` | |
| `SRK` / `Soave-Redlich-Kwong` | `SRK` | `classic` | |
| `CPA` / `CPA-SRK` | `CPA` | `10` | For polar systems (water, glycols, amines) |
| `Glycol Package` | `CPA` | `10` | Maps to CPA for MEG/TEG |
| `GERG 2008` | `GERG2008` | (built-in) | Natural gas |
| `Sour PR` / `SourPR` | `PR` | `classic` | H2S/CO2 systems |
| `Sour SRK` | `SRK` | `classic` | H2S/CO2 systems |

### Fallback Mappings (Approximated as SRK)

These UniSim packages have no direct NeqSim equivalent and fall back to `SRK`:

| UniSim Property Package | NeqSim Fallback | Notes |
|-------------------------|-----------------|-------|
| `ASME Steam` | `SRK` | Water only |
| `MBWR` | `SRK` | NeqSim has BWRS but limited components |
| `Lee-Kesler-Plocker` | `SRK` | No LKP model |
| `NRTL` / `UNIQUAC` / `UNIQUAC - Ideal` / `Wilson` | `SRK` | Activity models |
| `Zudkevitch Joffee` / `Kabadi Danner` | `SRK` | Specialized EOS |
| `Antoine` / `Chao Seader` / `Grayson Streed` | `SRK` | Legacy correlations |
| `DBR Amine Package` | `SRK` | DBR proprietary |
| `OLI` | `SRK` | Electrolyte package |
| `COMPropertyPkg` | `SRK` | COM extension package |

A warning is logged when a fallback mapping is used.

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

### Generate Jupyter Notebook (Interactive Version)

The notebook uses the **exact same shared code generators** as `to_python()` —
so the resulting process logic is always identical — but wraps equipment in
separate cells with markdown explanations (descriptions, feed tables, model
overview):

```python
converter = UniSimToNeqSim(model)
converter.save_notebook("process.ipynb")
```

Or get the raw dict (nbformat v4):
```python
nb_dict = converter.to_notebook(include_subflowsheets=True)
```

The notebook contains:
1. **Title & overview table** — components, operations, feed count, EOS
2. **Setup cell** — `from neqsim import jneqsim` + class aliases
3. **Fluid markdown + code** — composition table and fluid creation
4. **Feed streams markdown + code** — T/P/flow table and stream creation
5. **Equipment cells** — one markdown description + one code cell per unit
6. **Run cell** — `process.run()`
7. **Results cell** — prints T, P for every unit operation
8. **Warnings cell** — any conversion assumptions or skipped items

### Generate EOT / ProcessPilot Simulator

Generate a `BaseSimulator` subclass for the ProcessPilot-NeqSimInterface
framework (reinforcement learning / optimization):

```python
converter = UniSimToNeqSim(model)
converter.save_eot_simulator("my_simulator.py", class_name="MySimulator")
```

The generated code:
- Subclasses `eot.simulators.base_simulator.BaseSimulator`
- Uses `eot.components` factory functions (`get_stream`, `get_compressor`, etc.)
- Implements `build_process()` with all equipment
- Reports `name` property from the UniSim file name
- Falls back to raw `jneqsim` calls for equipment not covered by EOT factories

**EOT demo notebook** — shows how to instantiate, run, and step the simulator:
```python
nb = converter.to_eot_notebook(class_name="MySimulator")
import json
with open("eot_demo.ipynb", "w") as f:
    json.dump(nb, f, indent=1)
```

### Code Sharing Architecture

The `to_python()`, `to_notebook()`, and `to_eot_simulator()` methods all share
the same internal code generators:

| Shared Method | Purpose |
|--------------|---------|
| `_prepare_topology()` | Topological sort, variable naming, stream resolution |
| `_gen_fluid_lines()` | Fluid creation code (EOS, components, mixing rule) |
| `_gen_feed_lines()` | Feed stream setup (T, P, flow, `process.add()`) |
| `_gen_equipment_lines()` | Per-equipment code (properties, wiring, `process.add()`) |
| `_to_pyvar()` | Convert arbitrary name to valid Python identifier |
| `_unique_var()` | Assign unique variable name avoiding collisions |
| `_outlet_ref()` | Resolve dot-notation outlet references |

This ensures that **Python scripts, notebooks, and EOT simulators always
produce the same process logic** — only the wrapping differs.

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

1. **EOS differences**: UniSim PR-LK uses PR76 alpha (`m = 0.37464 + 1.54226ω − 0.26992ω²`)
   for ALL components, including those with ω > 0.49 (where PR78 uses a different cubic).
   **Fix**: Use `SystemPrLeeKeslerEos` in NeqSim — add `PRLKCORR` line after `EOS PR` in E300 file.
   This typically reduces vapour-fraction bias from −1.6% to < 0.3%.
2. **BIP (binary interaction parameters)**: UniSim PR-LK correlation BIPs are non-zero even for
   pure-prediction (never user-tuned). Extract via `pp.Kij.Values` (Section 1.1) and include in
   E300 BIC section. Without correct BIPs, vapour fraction can differ by 5%+ and oil MW by 100%+.
3. **Water BIPs**: PR-LK BIPs for H2O–N2 (≈−2.24), H2O–CO2 (≈−0.557), H2O–H2S (≈−0.390)
   are **negative** (increase cross-attraction → keep water in liquid). If a file has both
   water BIPs AND OMEGAA overrides, they must be used as a matched set. Using standard BIPs
   with modified OmegaA causes catastrophic phase split errors.
4. **Water distribution (multi-stage)**: In UniSim with recycles, water may split across
   multiple separators. NeqSim forward-flow models capture water mainly at the first stage.
   +70% water flow error at secondary stages is structurally expected if recycles are missing.
5. **Hypothetical components**: Pseudo-component property estimation differs between simulators.
   Critical properties and acentric factor estimation methods vary.
6. **Compressor efficiency**: UniSim COM sometimes returns `None` for `AdiabaticEfficiency`.
   Always check extracted efficiency values; default to 75% isentropic with a warning.
7. **Mixing rules**: UniSim may use advanced mixing rules not available in NeqSim.
8. **Transport properties**: Different viscosity/thermal conductivity correlations.
9. **Convergence**: Different solver algorithms, tolerance settings, and recycle initialization.

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

### Forward Reference Handling (Recycle Loops)

When the topological sort detects cycles (equipment B referenced before it is
defined because B depends on equipment A which depends on B), the converter
creates **forward reference placeholders** — temporary Stream objects that
stand in for the not-yet-created equipment outlets.

#### How It Works

1. **Cycle detection** (`_prepare_topology`): Operations in a cycle are
   identified. The back-edge producers become forward references stored in
   `topo['fwd_ref_placeholders']`.

2. **Placeholder registration** (`_register_fwd_placeholders`): For each
   forward-referenced producer, a `_fwd_XXX` variable is created. For
   multi-outlet equipment (Separator, ThreePhaseSeparator), **port-specific
   placeholders** are also created:

   | Equipment | Placeholders Created |
   |-----------|---------------------|
   | `V-100` (Separator) | `_fwd_V_100` (generic), `_fwd_V_100_gasOut`, `_fwd_V_100_liquidOut` |
   | `S-200` (ThreePhaseSeparator) | `_fwd_S_200` (generic), `_fwd_S_200_gasOut`, `_fwd_S_200_oilOut`, `_fwd_S_200_waterOut` |
   | `K-100` (Compressor) | `_fwd_K_100` (generic only) |

3. **Placeholder stream creation** (in `to_python` / `to_notebook` /
   `to_eot_simulator`): Each placeholder is created as a `Stream` with
   temperature, pressure, and flow rate from the UniSim stream data for
   that product outlet. Port-specific placeholders use the stream data for
   each individual outlet.

4. **Outlet resolution** (`_outlet_ref`): When downstream equipment
   references a forward-referenced separator's liquid outlet (e.g.
   `"V-100.liquidOut"`), the resolver first checks for a port-specific key
   (`V-100.liquidOut`) in `fwd_ref_vars` before falling back to the
   generic placeholder.

5. **Auto-Recycle wiring** (`_gen_equipment_lines`): After the actual
   separator is created and added to the process, Recycle objects are
   automatically generated to wire each actual outlet back to its
   forward reference placeholder:

   ```python
   # Auto-recycle: wire V-100 gasOut back to forward ref placeholder
   _rcy_V_100_gasOut = Recycle("V-100_gasOut_loop")
   _rcy_V_100_gasOut.addStream(V_100.getGasOutStream())
   _rcy_V_100_gasOut.setOutletStream(_fwd_V_100_gasOut)
   process.add(_rcy_V_100_gasOut)
   ```

#### Why Port-Specific Placeholders Matter

Without port-specific placeholders, a valve downstream of a separator's
liquid outlet would incorrectly receive the combined feed placeholder
(with gas + liquid flow). This caused 500%+ flow deviations in early
versions. Port-specific placeholders ensure each downstream unit gets
the correct phase with approximately correct T, P, and flow.

#### HeatExchanger Outlet Port Resolution

HeatExchanger has two feed/product sides indexed 0 (Shell) and 1 (Tube).
The converter maps downstream references to `getOutStream(int(0))` or
`getOutStream(int(1))` based on the product's position in the products
list (set by `_extract_heatexchanger`: ShellSideProduct = index 0,
TubeSideProduct = index 1).

Port naming convention: `hx0` and `hx1` (analogous to `gasOut`/`liquidOut`
for separators). Forward-reference placeholders are created per-side when
a HeatExchanger is in a recycle loop.

**Generated code example:**

```python
E_100 = HeatExchanger("E-100")
E_100.setFeedStream(0, shell_feed)   # Shell side
E_100.setFeedStream(1, tube_feed)    # Tube side
process.add(E_100)

# Downstream: Cooler on shell product (index 0)
C_100 = Cooler("C-100", E_100.getOutStream(int(0)))

# Downstream: Valve on tube product (index 1)
VLV_100 = ThrottlingValve("VLV-100", E_100.getOutStream(int(1)))
```

**Important:** Do NOT use `getOutletStream()` for HeatExchanger — it only
returns one side. Always use `getOutStream(int(index))` for explicit
side selection.

#### Compressor Efficiency Defaults

When compressor efficiency is not available from the UniSim COM extraction
(returns `None`), the code applies these rules:

1. If the extracted value is > 1.0, it's treated as a percentage and
   converted to a fraction (e.g., 75 → 0.75)
2. If adiabatic efficiency is available (0 < eff ≤ 1), it's set via
   `setIsentropicEfficiency()`
3. If only polytropic efficiency is available, it's set via
   `setPolytropicEfficiency()` + `setUsePolytropicCalc(True)`
4. If **neither** efficiency is available, a 75% isentropic default is
   applied with a warning comment in the generated code

This matters because NeqSim defaults to 100% isentropic efficiency,
which produces unrealistically low outlet temperatures.

#### Separator Phase Detection and Entrainment Extraction

The reader now detects 2-phase vs 3-phase separators by two mechanisms:

1. **UniSim TypeName**: `flashtank` → `Separator` (2-phase),
   `sep3op` → `ThreePhaseSeparator` (3-phase)
2. **WaterProduct heuristic**: If a `flashtank` has a `WaterProduct`
   connected, it is automatically promoted to `ThreePhaseSeparator`

#### Orientation Detection (Vertical → GasScrubber)

The reader extracts separator orientation from UniSim COM attributes
(`Orientation`, `VesselOrientation`, `SeparatorOrientation`). When a
`flashtank` is detected as **vertical**, the NeqSim type is mapped to
`GasScrubber` instead of `Separator`.

| UniSim Type | Orientation | NeqSim Type |
|---|---|---|
| `flashtank` | horizontal (default) | `Separator` |
| `flashtank` | vertical | `GasScrubber` |
| `flashtank` + WaterProduct | any | `ThreePhaseSeparator` |
| `sep3op` | any | `ThreePhaseSeparator` |

`GasScrubber` extends `Separator` in NeqSim — it is a vertical vessel
optimised for removing liquid droplets from a gas stream, with K-value
sizing constraints and a default 10% liquid level.

**Entrainment** is extracted from UniSim COM and mapped to NeqSim
`setEntrainment()` calls. The reader tries multiple COM attribute names
for each entrainment direction:

| Entrainment Direction | UniSim COM Attributes (tried in order) | NeqSim `setEntrainment` Args |
|---|---|---|
| Liquid in gas (oil carryover) | `LiqCarryOverMolFrac`, `LiqCarryOverFrac`, `LiquidInVapourFraction`, `LiqInVap`, `LiquidCarryover` | `(val, "volume", "product", "oil", "gas")` |
| Gas in liquid (gas carry-under) | `VapCarryUnderMolFrac`, `VapCarryUnderFrac`, `VapourInLiquidFraction`, `VapInLiq`, `VapourCarryunder` | `(val, "volume", "product", "gas", "liquid")` |
| Water in oil (3-phase) | `WaterInOilFraction`, `WaterInOil`, `AqInOil`, `AqueousInOilFraction` | `(val, "volume", "product", "aqueous", "oil")` |
| Oil in water (3-phase) | `OilInWaterFraction`, `OilInWater`, `OilInAq`, `OilInAqueousFraction` | `(val, "volume", "product", "oil", "aqueous")` |

Generated Python code example:
```python
# Three-phase separator with entrainment from UniSim
mp_sep = ThreePhaseSeparator("20VA102", heater_mp.getOutletStream())
mp_sep.setEntrainment(0.084, "volume", "product", "aqueous", "oil")  # water in oil
mp_sep.setEntrainment(0.002, "volume", "product", "oil", "aqueous")  # oil in water
process.add(mp_sep)
```

**Note:** If the UniSim COM does not expose entrainment attributes (some
model versions or configurations may not), the extraction silently skips
them — the separator will use NeqSim defaults (zero entrainment).

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

# Generate standalone Python script
python devtools/unisim_reader.py path/to/file.usc --python process.py

# Generate Jupyter notebook
python devtools/unisim_reader.py path/to/file.usc --notebook process.ipynb

# Generate EOT / ProcessPilot simulator
python devtools/unisim_reader.py path/to/file.usc --eot my_sim.py --eot-class MySimulator

# Generate EOT demo notebook
python devtools/unisim_reader.py path/to/file.usc --eot-notebook eot_demo.ipynb

# Save full extracted model
python devtools/unisim_reader.py path/to/file.usc --save extracted.json

# Fast extraction (topology only, no stream properties)
python devtools/unisim_reader.py path/to/file.usc --no-streams --summary

# Combined: Python + notebook + EOT in one command
python devtools/unisim_reader.py path/to/file.usc --python p.py --notebook n.ipynb --eot s.py

# Show UniSim GUI window during extraction (useful for debugging)
python devtools/unisim_reader.py path/to/file.usc --visible --summary
```

---

## 10. Known Limitations

1. **Windows only** — COM automation requires Windows + UniSim installed
2. **Hypothetical components** — pseudo-components need manual C7+ characterization
3. **Tuned BIPs** — Extractable via `pp.Kij.Values` (see Section 1.1). The
   `unisim_reader.py` does not yet automate this, but manual extraction is
   straightforward. The returned matrix uses -32767.0 as a diagonal sentinel.
4. **Column internals** — distillation column tray/packing details not fully mapped
5. **Dynamic models** — only steady-state data extracted
6. **Control logic** — PID controllers produce TODO comments, not functional controllers
7. **Custom correlations** — UniSim's user-defined correlations not transferred
8. **Performance curves** — compressor/pump performance maps not extracted
9. **Multiple fluid packages** — only the first fluid package used for composition
10. **Absorber columns** — single-feed only; multi-feed absorbers show a TODO.
    Glycol/TEG contactors (name contains "glyc", "teg", or "dehydrat") are
    modeled as `ComponentSplitter` for water removal instead of `DistillationColumn`
11. **SetPoint / Adjuster wiring** — generates skeleton code but wiring is often incomplete
12. **Recycle convergence** — heavily circular models (5+ forward references) may
    not converge with placeholder initial values; multiple `process.run()` calls
    or manual tuning may be needed
13. **Compressor efficiency** — COM extraction sometimes returns `None` even when
    the UniSim model has efficiency data; defaults to 75% isentropic
14. **Spreadsheet operations** — produce a skip comment; no calculation logic transferred
15. **Logical / balance operations** — produce skip comments
16. **DistillationColumn solver divergence** — NeqSim's sequential-substitution and
    inside-out column solvers diverge for C3/C4-rich (NGL-range) feeds at low
    pressure. Feeds with < 30% methane and significant C3+ fractions will not
    converge. Lighter feeds (e.g. deethanizer with 51% CH4) converge reliably.
    Build the column **outside** the `ProcessSystem` to avoid re-run divergence.
    See the TUTOR1 notebook for a worked example.
17. **HeatExchanger pressure drops** — NeqSim `HeatExchanger` does not model
    pressure drops; outlet pressures equal inlet pressures. UniSim models
    typically include 0.5–1.0 bar pressure drop per side. This causes small
    temperature deviations in downstream equipment.
18. **HeatExchanger UA tuning** — The `HeatExchanger.setUAvalue()` parameter
    must be tuned to match UniSim's heat duty. Counter-current heat balance
    differences between UniSim and NeqSim typically produce 1–2°C deviation
    on outlet temperatures.

---

## 11. Data Class Reference

The extracted UniSim model uses these Python dataclasses:

### UniSimComponent

```python
@dataclass
class UniSimComponent:
    name: str             # e.g. "Methane", "CO2", "C7 GRAND*"
    index: int            # Position in fluid package component list
    is_hypothetical: bool # True if name ends with '*'
```

### UniSimFluidPackage

```python
@dataclass
class UniSimFluidPackage:
    name: str              # e.g. "Basis-1"
    property_package: str  # e.g. "Peng-Robinson", "SRK", "CPA"
    components: List[UniSimComponent]

    @property
    def component_names(self) -> List[str]: ...
```

### UniSimStreamData

```python
@dataclass
class UniSimStreamData:
    name: str
    temperature_C: Optional[float]      # Celsius
    pressure_bara: Optional[float]       # bara
    mass_flow_kgh: Optional[float]       # kg/h
    molar_flow_kgmolh: Optional[float]  # kgmol/h
    vapour_fraction: Optional[float]     # 0-1
    mass_density_kgm3: Optional[float]   # kg/m3
    molecular_weight: Optional[float]    # g/mol
    enthalpy_kJkg: Optional[float]       # kJ/kg
    composition: Optional[Dict[str, float]]  # component_name -> mole fraction
    n_phases: Optional[int]
    viscosity_cP: Optional[float]
    thermal_conductivity: Optional[float]
    specific_heat_kJkgC: Optional[float]
```

### UniSimEnergyStream

```python
@dataclass
class UniSimEnergyStream:
    name: str
    heat_flow_kW: Optional[float]  # kW
```

### UniSimOperation

```python
@dataclass
class UniSimOperation:
    name: str                              # Equipment name e.g. "K-100"
    type_name: str                         # UniSim internal type e.g. "compressor"
    feeds: List[str]                       # Feed stream names
    products: List[str]                    # Product stream names
    energy_feeds: List[str]                # Energy feed stream names
    energy_products: List[str]             # Energy product stream names
    properties: Dict[str, Any]             # Type-specific extracted properties
```

Common `properties` keys by operation type:

| Operation | Property Keys |
|-----------|--------------|
| Compressor | `outlet_pressure_bara`, `adiabatic_efficiency`, `polytropic_efficiency` |
| Valve | `outlet_pressure_bara` |
| Cooler / Heater | `outlet_temperature_C`, `duty_kW` |
| HeatExchanger | `UA`, `duty_kW` |
| Pipe | `length_m`, `diameter_m`, `roughness_m` |
| Pump | `outlet_pressure_bara`, `efficiency` |

### UniSimFlowsheet

```python
@dataclass
class UniSimFlowsheet:
    name: str
    material_streams: List[UniSimStreamData]
    energy_streams: List[UniSimEnergyStream]
    operations: List[UniSimOperation]
    sub_flowsheets: List['UniSimFlowsheet']  # Recursive
```

### UniSimModel

```python
@dataclass
class UniSimModel:
    file_path: str
    file_name: str
    fluid_packages: List[UniSimFluidPackage]
    flowsheet: Optional[UniSimFlowsheet]

    def summary(self) -> str: ...          # Human-readable overview
    def all_operations(self) -> List[UniSimOperation]: ...  # Flat list incl. sub-FS
    def all_streams(self) -> List[UniSimStreamData]: ...    # Flat list incl. sub-FS
    def to_dict(self) -> Dict: ...         # JSON-serializable
```

---

## 12. Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| `pywintypes.com_error` | UniSim not installed or wrong version | Verify UniSim installation |
| Empty stream values | Stream not solved in UniSim | Open file in UniSim GUI, run solver first |
| `-32767` values | UniSim empty marker | Filter with `val > -30000` check |
| COM timeout | Large model loading | Increase `time.sleep()` after `.Open()` |
| Wrong component count | Multiple fluid packages | Check which FP the stream uses |
| Missing operations | Sub-flowsheet not recursed | Use `model.all_operations()` |
| Composition doesn't sum to 1 | Hypothetical components excluded | Re-normalize after filtering |
| Compressor outlet T too low | Efficiency = 100% (ideal) | Check for missing efficiency; defaults to 75% |
| Valve flow deviation 500%+ | Wrong forward ref placeholder | Port-specific placeholders now fix this |
| Separator downstream wrong phase | Generic fwd ref placeholder used | Ensure `_register_fwd_placeholders` ran |
| Recycle not converging | Too many forward references | Try multiple `process.run()` calls or tune placeholders |
| `AttributeError` on COM property | Operation type doesn't have that property | Wrap in try/except or check `TypeName` |
| Column diverges / mass runaway | C3/C4-rich feed at low P | Known solver limitation; build column outside ProcessSystem; use flash separation as fallback |
| HX outlet T differs 1–2°C | UA mismatch or no ΔP modeled | Tune UA value; add note that NeqSim HX has no pressure drop |
| SalesGas T off by 5°C | Propagated HX deviation | CoolGas deviation amplified through counter-current HX; adjust UA |

---

## 13. Verified Reference Cases

| Case | File | Components | Operations | Converged | Notes |
|------|------|-----------|------------|-----------|-------|
| TUTOR1 | `TUTOR1.usc` | 7 (N₂, CO₂, C₁–nC₄) | 13 | 11/13 streams | DePropanizer column diverges; upstream matches within 1°C. Notebook: `examples/notebooks/tutor1_gas_processing.ipynb` |

### TUTOR1 Lessons Learned

The TUTOR1 gas processing tutorial was the first end-to-end verified conversion.
Key findings that apply to any UniSim conversion:

1. **Recycle loops converge well**: The Gas/Gas HX ↔ LTS recycle loop converged
   in 3 iterations using placeholder streams seeded from UniSim data.

2. **HeatExchanger UA tuning**: Setting `UA = 35000 W/K` produced CoolGas ≈ 5.5°C
   vs UniSim's 6.6°C. The deviation propagates to SalesGas (15°C vs 10°C). The
   UA value needs case-by-case tuning against UniSim's heat duty.

3. **Column solver limitation**: The DePropanizer (7 components, 24% CH4,
   27% C₂, 24% C₃, 24% C₄ at 14 bara) diverged with all three solver types
   (DIRECT_SUBSTITUTION, INSIDE_OUT, DAMPED_SUBSTITUTION) and multiple
   configurations (1–5 trays). This is a known NeqSim limitation for NGL-range
   feeds. **Workaround**: Build the column outside the ProcessSystem to avoid
   re-run divergence affecting upstream convergence.

4. **Pressure drops not modeled**: UniSim's Gas/Gas HX has ~0.7 bar ΔP per side;
   NeqSim passes pressure through unchanged. This is cosmetic for most
   comparisons but compounds through multi-stage processes.

5. **DewPoint and Balance operations**: UniSim's DewPoint (balance op) and
   ADJ-1 (adjuster) are not needed for mass balance; safe to skip.

6. **Heating Value spreadsheet**: Property-only calculations; safe to skip.
