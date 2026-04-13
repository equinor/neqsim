# Add physics-based separator entrainment modeling framework

## Summary

Adds a comprehensive **physics-based entrainment calculation framework** for separator performance evaluation. The framework models the complete separation chain — inlet device, gravity settling, mist elimination — using first-principles correlations from industry standards (Shell DEP, GPSA, Souders-Brown). Replaces empirical K-factor-only approaches with stage-by-stage droplet tracking.

**25 files changed, 8,342 insertions, 25 deletions.**

## Commit Message

```
feat: add physics-based separator entrainment modeling framework

Add 8 new classes in neqsim.process.equipment.separator.entrainment
for detailed separator performance calculation:

- SeparatorPerformanceCalculator: 7-stage pipeline orchestrator
- DropletSizeDistribution: Rosin-Rammler & log-normal DSDs
- DropletSettlingCalculator: Schiller-Naumann terminal velocity
- GradeEfficiencyCurve: S-curve grade efficiency for wire mesh,
  vane pack, axial cyclone, and plate pack internals
- InletDeviceModel: 7 inlet device types with bulk efficiency,
  momentum flux, pressure drop, and downstream DSD modification
- MultiphaseFlowRegime: Taitel-Dukler flow regime prediction for
  horizontal and vertical pipe orientations (7 regimes)
- SeparatorGeometryCalculator: vessel geometry, residence times,
  liquid levels, and gas/liquid area calculations
- SeparatorInternalsDatabase: CSV-backed singleton for mist
  eliminator and inlet device specifications

Integrate into Separator and ThreePhaseSeparator via
setEnhancedEntrainmentCalculation(true) with convenience methods
for inlet device type, pipe diameter, and surface tension.

Add 2 CSV databases (SeparatorInternals.csv, SeparatorInletDevices.csv),
9 JUnit 5 test classes (67 tests), documentation guide, and a Jupyter
notebook example covering horizontal 2-phase, vertical scrubber, and
horizontal 3-phase separator cases.
```

## What's New

### 1. Entrainment Calculation Package (`separator.entrainment`) — 8 NEW classes

#### SeparatorPerformanceCalculator
Orchestrates the full 7-stage enhanced calculation pipeline:
1. Generate inlet droplet size distribution (Rosin-Rammler, d₀ from Hinze correlation)
2. Predict multiphase flow regime (Taitel-Dukler)
3. Calculate inlet device bulk separation + downstream DSD modification
4. Calculate vessel geometry (gas/liquid areas, residence times)
5. Calculate gravity section efficiency (Souders-Brown / Stokes settling)
6. Calculate mist eliminator efficiency (grade efficiency curve integration)
7. Combine into overall gas-liquid and liquid-liquid efficiencies

Key outputs: K-factor, K-factor utilization, gravity cut diameter, oil-in-gas fraction, water-in-gas fraction, mist eliminator flooding status, JSON report.

#### DropletSizeDistribution
- Factory methods: `rosinRammler(d0, n)`, `logNormal(dMedian, sigma)`
- Properties: D50, D32 (Sauter mean), discrete volume-weighted classes
- Used as input/output for inlet device and efficiency calculations

#### DropletSettlingCalculator
- `calcTerminalVelocity(diameter, rhoGas, rhoLiq, muGas)` — Schiller-Naumann correlation
- Stokes regime for small droplets, drag correction for intermediate Re

#### GradeEfficiencyCurve
- S-shaped efficiency curves parameterized by cut diameter (d₅₀) and steepness
- Factory methods: `wireMeshDefault()`, `vanePackDefault()`, `axialCycloneDefault()`, `platePack(d50, maxEff)`
- `calcOverallEfficiency(DSD)` — integrates grade curve against a full DSD

#### InletDeviceModel
- 7 device types: NONE, DEFLECTOR_PLATE, HALF_PIPE, INLET_VANE, SCHOEPENTOETER, IMPINGEMENT_PLATE, INLET_CYCLONE
- Calculates: bulk separation efficiency, momentum flux, pressure drop, downstream DSD

#### MultiphaseFlowRegime
- Taitel-Dukler flow regime prediction
- 7 regimes: STRATIFIED_SMOOTH, STRATIFIED_WAVY, ANNULAR, PLUG, SLUG, ANNULAR_MIST, DISPERSED_BUBBLE
- Supports horizontal and vertical pipe orientations

#### SeparatorGeometryCalculator
- Gas/liquid area from vessel diameter + liquid level fraction
- Gas and liquid residence times
- Effective settling height for horizontal/vertical vessels

#### SeparatorInternalsDatabase
- CSV-backed singleton loading from `designdata/SeparatorInternals.csv` (17 records) and `SeparatorInletDevices.csv` (13 records)
- Query by type, K-factor range, application suitability

### 2. Separator & ThreePhaseSeparator Integration

New convenience methods on both `Separator` and `ThreePhaseSeparator`:
- `setEnhancedEntrainmentCalculation(true)` — enables detailed calculation
- `setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE)`
- `setInletPipeDiameter(0.254)` — feed pipe diameter in meters
- `setGasLiquidSurfaceTension(0.015)` — in N/m
- `getKFactor()`, `getKFactorUtilization()`, `getInletFlowRegime()`, `isMistEliminatorFlooded()`
- `getPerformanceCalculator()` — access the full calculator for advanced configuration

### 3. CSV Design Data

| File | Records | Content |
|------|---------|---------|
| `SeparatorInternals.csv` | 17 | Wire mesh, vane pack, cyclone specs with K-factor ranges |
| `SeparatorInletDevices.csv` | 13 | Inlet device specs with efficiency and pressure drop data |

### 4. Tests — 9 test classes, 67 tests

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `SeparatorPerformanceCalculatorTest` | 8 | Full pipeline, JSON output, K-factor |
| `EnhancedPerformanceCalculatorTest` | 10 | Integration with Separator/ThreePhaseSep |
| `DropletSizeDistributionTest` | 7 | RR, log-normal, D50, D32, edge cases |
| `DropletSettlingCalculatorTest` | 6 | Terminal velocity, Stokes, intermediate Re |
| `GradeEfficiencyCurveTest` | 8 | Wire mesh, vane, cyclone, overall efficiency |
| `InletDeviceModelTest` | 8 | All 7 device types, DSD modification |
| `MultiphaseFlowRegimeTest` | 8 | All 7 regimes, horizontal + vertical |
| `SeparatorGeometryCalculatorTest` | 8 | Areas, residence times, liquid levels |
| `SeparatorInternalsDatabaseTest` | 4 | CSV loading, queries, record access |

### 5. Documentation

- **New guide**: `docs/process/equipment/separator-entrainment-modeling.md` — ~950 lines covering physics, API, examples
- **Updated**: `docs/process/equipment/separators.md` — callout linking to enhanced guide
- **Updated**: `docs/REFERENCE_MANUAL_INDEX.md` — new entry

### 6. Jupyter Notebook Example

`examples/notebooks/separator_entrainment_modeling.ipynb` — fully executed notebook with:
- Standalone tool demos (DSD, grade efficiency, settling velocity)
- Case 1: Horizontal two-phase separator (2.4m × 8m, 70 bara)
- Case 2: Vertical gas scrubber (1.8m × 5m, 85 bara)
- Case 3: Horizontal three-phase separator (3.2m × 12m, 25 bara)
- Comparison table, sensitivity study, inlet device comparison, flow regime map
- 8 matplotlib figures

## Usage Example

```java
// Enable enhanced entrainment on any Separator
Separator sep = new Separator("HP Sep", feed);
sep.setInternalDiameter(2.4);
sep.setSeparatorLength(8.0);
sep.setOrientation("horizontal");
sep.setEnhancedEntrainmentCalculation(true);
sep.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE);
sep.setInletPipeDiameter(0.254);
sep.setGasLiquidSurfaceTension(0.015);
sep.getPerformanceCalculator().setMistEliminatorCurve(
    GradeEfficiencyCurve.wireMeshDefault());

process.add(sep);
process.run();

// Read results
double kFactor = sep.getKFactor();
double efficiency = sep.getPerformanceCalculator().getOverallGasLiquidEfficiency();
boolean flooded = sep.isMistEliminatorFlooded();
String json = sep.getPerformanceCalculator().toJson();
```

## Breaking Changes

None. The enhanced calculation is opt-in via `setEnhancedEntrainmentCalculation(true)`. Default separator behavior is unchanged.

### 2. ProcessSystem.toJson() / fromJson() — NEW

Convenience methods on `ProcessSystem`:

```java
// Export to JSON
String json = process.toJson();

// Build from JSON (tolerant — missing refs become warnings)
SimulationResult result = ProcessSystem.fromJson(json);

// Build and run in one call
SimulationResult result = ProcessSystem.fromJsonAndRun(json);
```

### 3. ProcessModel.toJson() / fromJson() — NEW

Multi-area plant export/import for `ProcessModel` (named collection of `ProcessSystem` objects):

```java
// Export multi-area plant
ProcessModel plant = new ProcessModel();
plant.add("separation", separationProcess);
plant.add("compression", compressionProcess);
String json = plant.toJson();

// Round-trip
ProcessModel rebuilt = ProcessModel.fromJson(json);
rebuilt.run();

// Or build-and-run in one call
ProcessModel rebuilt = ProcessModel.fromJsonAndRun(json);
```

**JSON schema:**
```json
{
  "areas": {
    "separation": { "fluid": {...}, "process": [...] },
    "compression": { "fluid": {...}, "process": [...] }
  }
}
```

### 4. UniSim Reader (`devtools/unisim_reader.py`) — MAJOR UPDATE

Reads Honeywell UniSim Design `.usc` files via COM automation and generates executable NeqSim Python code:

- **45+ operation type mappings** (separators, compressors, heat exchangers, reactors, columns, valves, mixers, splitters, etc.)
- **Component name mapping** (UniSim ↔ NeqSim name resolution)
- **Property package mapping** (Peng-Robinson, SRK, CPA, etc.)
- **Topology reconstruction** with port-specific forward reference placeholders for recycle loops
- **Sub-flowsheet handling** (nested operations within flowsheets)
- **Multiple output formats:** Python script, Jupyter notebook, JSON, EOT simulator

### 5. Grane Platform Notebook (`examples/notebooks/grane_platform_process.ipynb`) — NEW

A 300-cell production notebook demonstrating the full UniSim → NeqSim workflow on the **Grane GEE 2030** platform model:

- 14 components, 149 operations, 205 streams, 5 sub-flowsheets
- 4 named process areas via `ProcessModel` (Main, Grane LP, Grane HP, DPC UNIT)
- Recycle convergence with Wegstein acceleration
- Comprehensive stream-by-stream comparison against UniSim reference data
- `toJson()` export of the full multi-area plant

### 6. Reactor Equipment — NEW

| Class | Description |
|-------|-------------|
| `PlugFlowReactor` | Steady-state tubular reactor with axial temperature/conversion profiles |
| `CatalystBed` | Fixed-bed catalyst layer with pressure drop and deactivation models |
| `KineticReaction` | First-order/second-order kinetic rate law with Arrhenius temperature dependence |
| `ReactorAxialProfile` | Records axial position, temperature, conversion, pressure along reactor length |

### 7. DistillationColumn Enhancements

- New convergence diagnostics: `getLastIterationCount()`, `getLastMassResidual()`, `getLastEnergyResidual()`
- Column-specific heat duty tracking per tray
- Builder pattern for convenient column creation

### 8. Test Coverage

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `JsonProcessExporterTest` | 9 | Export, round-trip, splitter, cooler, ProcessModel |
| `PlugFlowReactorTest` | 8 | Adiabatic, isothermal, catalyst, profiles |
| `CatalystBedTest` | 6 | Pressure drop, deactivation, multi-bed |
| `KineticReactionTest` | 5 | First/second order, Arrhenius, reversible |
| `DistillationColumnTest` | 2 | Inside-out solver, convergence metrics |
| `test_unisim_outputs.py` | 14 | UniSim converter all output modes |
| `test_unisim_writer.py` | 23 | UniSim writer parsing, mappings, validation |

## Architecture

```
┌──────────────────────────────────┐
│         ProcessModel             │  plant.toJson()  ──────┐
│  ┌──────────┐  ┌──────────┐     │                        │
│  │ ProcessA  │  │ ProcessB │     │                        ▼
│  │ .toJson() │  │ .toJson()│     │          ┌─────────────────────┐
│  └──────────┘  └──────────┘     │          │  JSON                │
│          │            │          │          │  {"areas": {         │
│          ▼            ▼          │          │    "A": {fluid,proc} │
│  JsonProcessExporter             │          │    "B": {fluid,proc} │
└──────────────────────────────────┘          │  }}                  │
                                              └─────────────────────┘
                                                        │
                                              ProcessModel.fromJson()
                                                        │
                                                        ▼
┌──────────────────────────────────┐          ┌─────────────────────┐
│     JsonProcessBuilder           │◄─────────│  Per-area build     │
│  (2-pass: create → wire)         │          │  via JsonProcessBuilder
│  resolveStreamReference()        │          └─────────────────────┘
│  "HP Sep.gasOut" → stream obj    │
└──────────────────────────────────┘

UniSim .usc ──COM──▶ unisim_reader.py ──▶ Python notebook ──▶ ProcessModel
                                            ▲                      │
                                            │                      │ .toJson()
                                            │                      ▼
                                         fromJson() ◄──── JSON file
                                                            │
                                               unisim_writer.py ──COM──▶ UniSim .usc
```

## Files Changed

### New Java Classes
- `src/main/java/neqsim/process/processmodel/JsonProcessExporter.java`
- `src/main/java/neqsim/process/equipment/reactor/PlugFlowReactor.java`
- `src/main/java/neqsim/process/equipment/reactor/CatalystBed.java`
- `src/main/java/neqsim/process/equipment/reactor/KineticReaction.java`
- `src/main/java/neqsim/process/equipment/reactor/ReactorAxialProfile.java`

### Modified Java Classes
- `ProcessSystem.java` — added `toJson()`
- `ProcessModel.java` — added `toJson()`, `fromJson()`, `fromJsonAndRun()`
- `JsonProcessBuilder.java` — improved wiring with iterative forward-reference resolution
- `DistillationColumn.java` — convergence diagnostics, builder pattern
- `HeatExchanger.java` — additional outlet stream accessors
- `Adjuster.java` — improved error reporting

### New Test Classes
- `JsonProcessExporterTest.java` (9 tests)
- `PlugFlowReactorTest.java` (8 tests)
- `CatalystBedTest.java` (6 tests)
- `KineticReactionTest.java` (5 tests)

### New Notebooks
- `examples/notebooks/grane_platform_process.ipynb` — Grane platform UniSim import
- `examples/notebooks/neqsim_json_roundtrip.ipynb` — JSON export/import demo

### 9. UniSim Writer (`devtools/unisim_writer.py`) — NEW

The **reverse** of `unisim_reader.py` — takes NeqSim JSON and creates a UniSim Design `.usc` file via COM automation:

```python
from devtools.unisim_writer import UniSimWriter

# From a JSON string (ProcessSystem or ProcessModel)
writer = UniSimWriter(visible=True)
writer.build_from_json(json_str, save_path="output.usc")
writer.close()

# From a live NeqSim ProcessSystem
writer.build_from_process_system(process, save_path="output.usc")

# Validate without UniSim installed
from devtools.unisim_writer import validate_json_for_unisim
result = validate_json_for_unisim(json_str)
print(result['valid'], result['warnings'])
```

**Features:**
- Parses both single-area (`ProcessSystem.toJson()`) and multi-area (`ProcessModel.toJson()`) JSON
- Reverse mapping dictionaries: NeqSim components → UniSim names, EOS → property packages, equipment types → UniSim operation types
- Topology resolution: dot-notation inlet references resolved to internal stream names
- Equipment wiring: feeds/products connected using type-specific COM patterns (Feeds[], FeedStream, VapourProduct, etc.)
- Equipment properties: outlet pressure, temperature, efficiency, split factors
- Standalone validation function (no COM needed) to pre-check JSON compatibility
- CLI: `python unisim_writer.py input.json -o output.usc --validate-only`
- 23 unit tests covering parsing, mappings, connectivity, and validation

### Python / DevTools
- `devtools/unisim_reader.py` — UniSim COM reader (+2582 lines)
- `devtools/unisim_writer.py` — UniSim COM writer (NeqSim JSON → .usc) — **NEW**
- `devtools/test_unisim_outputs.py` — 14 converter tests (reader)
- `devtools/test_unisim_writer.py` — 23 writer tests — **NEW**

### Documentation
- `docs/process/equipment/plug_flow_reactor.md` — PlugFlowReactor guide
- `docs/process/equipment/reactors.md` — updated reactor overview
- Updated `AGENTS.md`, `REFERENCE_MANUAL_INDEX.md`, skill files
- "TEG dehydration for 50 MMSCFD per NORSOK P-001" → Standard
- "field development concept selection per NORSOK, DNV-OS-F101, with cost ranking" → Comprehensive

### PR Generation — Contributing Back

Tasks that produce reusable outputs (tests, notebooks, docs, API extensions) can be contributed back via Pull Request. The workflow now includes:
- PR instructions in every task README
- Agent can create a feature branch, copy outputs to proper locations, and create a PR via `gh pr create`
- Safety rules: never commit `task_solve/` contents, copy first, ask before pushing

### Three User Paths — All Can Improve NeqSim

| Path | Target User | What They Get | How NeqSim Grows |
|------|-------------|---------------|------------------|
| **A: Process Engineer** | "I just want answers" | Paste a prompt, get results | Surfaces API gaps; notebooks become examples |
| **B: Developer** | "I want to extend NeqSim" | Full 3-step + promote code back | New methods, equipment, tests added mid-task |
| **C: Researcher** | "I need a report" | Literature → simulation → Word + HTML | Validates models; identifies missing correlations |
| **D: Other AI Tools** | Using Codex, Claude Code, Cursor | Same workflow, any AI agent | Same flywheel, tool-agnostic |

### Works With Any AI Tool (Not Just VS Code Copilot)

The workflow is tool-agnostic at its core. The `@solve.task` agent is a VS Code convenience, but the script, templates, and report generator work from any terminal:

- **OpenAI Codex**: Reads `AGENTS.md` automatically. The `.openapi/codex.yaml` installs Java 8, Maven, Python, and `gh` CLI. Full end-to-end including PR creation.
- **Claude Code**: Same approach — give it the workflow prompt and task folder path
- **Cursor**: Paste agent instructions from `.github/agents/solve.task.agent.md` into Cursor rules
- **Google Colab**: The dual-boot notebook cell auto-detects `pip install neqsim` vs devtools

### Full Codex Support — Solve + PR in One Shot

Codex (Cloud or CLI) can now run the **entire workflow autonomously**:

1. `AGENTS.md` (repo root) gives Codex project instructions (Java 8 rules, build commands, workflow steps, code patterns)
2. `.openapi/codex.yaml` sets up the sandbox with JDK 8, Maven, Python, `gh` CLI
3. One-shot prompt creates the task, fills scope, runs simulation, generates reports, and creates a PR

**Key limitation:** Codex Cloud uses the released `neqsim` package (`pip install neqsim`), so it can solve tasks using the existing API but cannot extend the Java source code mid-task. For Type E (Feature) tasks that need new Java methods, use Codex CLI (local) or VS Code Copilot.

See "End-to-End with OpenAI Codex" in `TASK_SOLVING_GUIDE.md` for the copy-paste prompt.

### Task Type G — Large Workflows

For complex multi-discipline studies (field development, design basis, technology screening):
- Step 1 (Scope) becomes critical — define ALL standards, methods, and deliverables upfront
- Step 2 (Analysis) supports **multiple notebooks**, numbered sequentially (e.g., `01_reservoir_fluid.ipynb`, `02_pipeline_sizing.ipynb`, `03_flow_assurance.ipynb`)
- Step 3 (Report) produces a navigable **HTML document** with sidebar navigation linking all sub-analyses — plus a Word summary

### Dual Report Output (Word + HTML)

Step 3 produces **both** formats:
- **Word (.docx)** — formal document for sharing/review via `python-docx`
- **HTML** — interactive, navigable report with sidebar navigation, embedded base64 images, and responsive layout — ideal for large workflows with many sections

### Step 1 Research Options

Step 1 research supports **two alternatives**:
- **Google NotebookLM** — best for deep literature review (upload PDFs, cited answers)
- **GitHub Copilot Chat** — best for code-adjacent research (web search + repo context). Includes a ready-to-paste Copilot prompt

### Prerequisites Section

Clear table listing all requirements with install commands. Explicitly states:
> Use `pip install -e devtools/` for the task workflow — do **not** use `pip install neqsim` (that installs the released package, not your working copy).

### Copy-Paste AI Prompts

Every task template includes ready-to-use prompts for each step — users paste them directly into NotebookLM, Copilot Chat, or Claude. No prompt engineering required.

## Files Changed

| File | Change |
|------|--------|
| `devtools/new_task.py` | **Updated** — 3-step workflow, task specification, HTML report, Type G, auto-reading generate_report.py, results.json bridge, reference fluid compositions, structured validation |
| `.github/agents/solve.task.agent.md` | **Updated** — 3-step workflow with task specification, Type G, quality gates between steps, results.json mandate |
| `AGENTS.md` | **New** — Project-level instructions for OpenAI Codex and other AI agents, with results.json pattern |
| `.openapi/codex.yaml` | **Updated** — Installs Java 8, Maven, Python, gh CLI for full task-solving in Codex sandbox |
| `.gitignore` | Added `task_solve/`, `__pycache__/`, `*.pyc` |
| `README.md` | Added "Solve Engineering Tasks with AI" section |
| `CONTEXT.md` | Added "Solve a Task (Start Here)" section at top |
| `devtools/README.md` | Added `new_task.py` to files table + task-solving section |
| `docs/development/TASK_SOLVING_GUIDE.md` | Added workflow section, engineering rigor section (data flow, quality gates, structured validation), Codex end-to-end guide |
| `devtools/__pycache__/*.pyc` | **Removed** from tracking (was accidentally committed) |

### Engineering Rigor Improvements (Latest)

These changes make the workflow production-grade for real engineering deliverables:

1. **Data bridge (results.json)** — Notebooks save structured results to `results.json`; the report generator auto-reads it. The three steps are no longer disconnected islands — data flows from task_spec → notebook → report automatically.

2. **Auto-populating reports** — `generate_report.py` now reads `task_spec.md` (for Scope & Standards) and `results.json` (for Results & Validation). Reports show real data instead of placeholder text.

3. **Quality gates** — The agent must verify specific conditions before moving from Step 2 to Step 3: results.json exists, all acceptance criteria checked, all deliverables produced, figures saved.

4. **Structured validation** — `step2_analysis/notes.md` now includes a validation summary table (check / status / value), a sensitivity analysis table, and a reference data comparison table. These map directly to `results.json` fields.

5. **Reference fluid compositions** — `task_spec.md` includes 4 pre-defined fluid compositions (lean gas, rich gas, wet gas, CO2 stream) as pick-and-use starting points.

6. **Color-coded HTML validation** — The HTML report shows validation results with green PASS / red FAIL styling.

## Why This Enables Advanced Tasks

Traditional workflows separate "using a tool" from "developing a tool". This workflow merges them:

1. **Mid-task development** — When a simulation needs a method that doesn't exist yet (e.g., JT coefficient for a CPA fluid), the user adds it to `src/main/java/neqsim/` right there, reruns the simulation, and continues. The task isn't blocked.
2. **Immediate validation** — New code is tested against a real engineering problem, not synthetic unit tests. The task *is* the acceptance test.
3. **Knowledge capture** — Every task logs what worked, what was missing, and what was added. The next person (or AI session) finds it via `TASK_LOG.md`.
4. **Progressive complexity** — Simple tasks (Path A) reveal gaps. Those gaps become development tasks (Path B). Development tasks produce reusable tools that unlock even harder tasks.

## Design Decisions

1. **`task_solve/` is gitignored** — it's a local working area, never committed. Templates are embedded in `devtools/new_task.py` so they're always available after `git clone`.
2. **Script in `devtools/`** — tracked in git, importable, no external dependencies (stdlib only).
3. **`pip install -e devtools/`** enforced for the task workflow — ensures users work against their local build, not the released package. When you add a new Java method and rebuild, it's immediately available in your notebook.
4. **Templates are string constants** — `setup_workspace()` is idempotent and safe to call multiple times.

## How to Test

```powershell
# Default: Copilot does everything (in VS Code Copilot Chat)
# Type: @solve.task JT cooling for rich gas at 100 bara

# Quick scale (simple question):
# Type: @solve.task density of CO2 at 200 bar

# Standard scale (with standards):
# Type: @solve.task pipeline wall thickness per DNV-OS-F101 and NORSOK L-001

# Comprehensive scale (large workflow):
# Type: @solve.task field development concept selection for deepwater gas

# PR generation (after completing a task):
# Type: @solve.task create a PR with the test and notebook from this task

# Alternative: Script creates folder, user follows prompts
python devtools/new_task.py --setup
python devtools/new_task.py "test task" --type A --author "Test"
python devtools/new_task.py "field dev study" --type G --author "Test"
python devtools/new_task.py --list
ls task_solve/20*
```

## New Java Code — Field Development & Subsea Design

The task-solving workflow was used to solve a field development NPV calculation. During that work, missing capabilities were identified and added directly to NeqSim:

### New: `SURFCostEstimator` (Subsea CAPEX)

**File:** `src/main/java/neqsim/process/mechanicaldesign/subsea/SURFCostEstimator.java`

A comprehensive SURF (Subsea, Umbilicals, Risers, Flowlines) cost estimator with:
- **S** — Subsea trees (pressure rating, bore size, horizontal/dual bore), manifolds, PLETs, jumpers
- **U** — Umbilicals (static/dynamic, length-based costing)
- **R** — Risers (flexible/rigid, diameter-based, water depth adjustment)
- **F** — Flowlines (infield + export, diameter/length/material-driven, installation method)
- **Regional cost factors** via `SubseaCostEstimator.Region` (NORWAY, UK, GOM, BRAZIL, WEST_AFRICA)
- **JSON/Map output** with category breakdown, line items, and vessel day estimates
- **Currency conversion** via exchange rate parameter

```java
SURFCostEstimator surf = new SURFCostEstimator(6, 300.0, SubseaCostEstimator.Region.NORWAY);
surf.setExportPipelineLengthKm(80.0);
surf.setExportPipelineDiameterInches(24.0);
surf.setNumberOfPLETs(2);
surf.setNumberOfJumpers(6);
double totalUSD = surf.calculate();
```

---

## New: Heat Exchanger Thermal-Hydraulic Design Toolkit

Five new classes in `neqsim.process.mechanicaldesign.heatexchanger` provide TEMA-level
shell-and-tube heat exchanger design with rigorous thermal-hydraulic calculations:

### `ThermalDesignCalculator`

Central calculator for tube-side and shell-side heat transfer coefficients, overall U,
pressure drops, and zone-by-zone analysis. Supports Gnielinski (tube-side) and Kern or
Bell-Delaware (shell-side) methods.

```java
ThermalDesignCalculator calc = new ThermalDesignCalculator();
calc.setTubeODm(0.01905);
calc.setTubeLengthm(6.0);
calc.setTubeCount(200);
calc.setTubeSideFluid(995.0, 0.0008, 4180.0, 0.62, 5.0, true);
calc.setShellSideFluid(820.0, 0.003, 2200.0, 0.13, 8.0);
calc.setShellSideMethod(ThermalDesignCalculator.ShellSideMethod.BELL_DELAWARE);
calc.calculate();
String json = calc.toJson();
```

### `BellDelawareMethod`

Industry-standard Bell-Delaware method for shell-side HTC and pressure drop with
J-factor correction factors (Jc, Jl, Jb, Js, Jr) and Zhukauskas correlation for tube banks:
```
h_shell = h_ideal × Jc × Jl × Jb × Js × Jr
```

### `LMTDcorrectionFactor`

LMTD correction factor (F_t) for multi-pass configurations using Bowman-Mueller-Nagle (1940).
Supports 1-N shell passes, calculates R and P, recommends minimum shell passes needed.

### `VibrationAnalysis`

Flow-induced vibration screening per TEMA RCB-4.6 evaluating:
- Vortex shedding (Von Karman, Strouhal = 0.22)
- Fluid-elastic instability (Connors criterion, safety factor 0.5)
- Acoustic resonance (shell mode frequencies)

Returns `VibrationResult` with pass/fail and diagnostic ratios.

### `ShellAndTubeDesignCalculator` (Major Expansion)

Now includes ASME VIII Div.1 pressure design (UHX-13 tubesheet, UG-27 MAWP, UG-37
nozzle reinforcement, UG-99 hydro test), NACE MR0175/ISO 15156 sour service assessment,
thermal-hydraulic integration, weight/cost estimation, and Bill of Materials.

### `HeatExchangerMechanicalDesign` (Enhanced)

Auto-selects exchanger type (shell-and-tube, plate, air-cooled) based on configurable
criteria (`MIN_AREA`, `MIN_WEIGHT`, `MIN_PRESSURE_DROP`). Full TEMA class (R/C/B),
shell types (E-X), fouling, velocity limits, materials, and NACE support.

**Standards:** TEMA R/C/B, ASME VIII Div.1, NACE MR0175/ISO 15156, Bell-Delaware, Gnielinski, Connors.

---

## New: TwoFluidPipe Enhancements

### Boundary Condition API

New public setters for inlet/outlet boundary conditions during transient simulation:

```java
pipe.setInletBoundaryCondition(BoundaryCondition.CONSTANT_FLOW);
pipe.setOutletBoundaryCondition(BoundaryCondition.CLOSED);  // shut-in scenario
pipe.closeOutlet();   // convenience
pipe.openOutlet(30.0, "bara");
```

Types: `STREAM_CONNECTED` (default inlet), `CONSTANT_FLOW`, `CONSTANT_PRESSURE`, `CLOSED`.

### Three-Phase Conservation Equations

`TwoFluidConservationEquations` extended to 7 equations for gas-oil-water with separate
oil and water momentum using AUSM+ flux scheme and MUSCL reconstruction.

### `InterfacialFriction` (New Closure Class)

Flow regime-dependent interfacial friction correlations:
- Stratified smooth: Taitel-Dukler (1976)
- Stratified wavy: Andritsos-Hanratty (1987)
- Annular: Wallis (1969)
- Slug: Oliemans (1986)

### Additional TwoFluidPipe Features

- Elevation profile: `setElevationProfile(double[])`
- Temperature profile output: `getTemperatureProfile("C")` or `getTemperatureProfile()` (K)
- Liquid inventory: `getLiquidInventory("m3")` or `getLiquidInventory("kg")`
- Cooldown time: `calculateCooldownTime(targetTemp, "C")`

---

## New: Pump Enhancements

- **Pump curve support** with affinity law scaling for different speeds
- **Cavitation detection** (NPSH available vs required)
- **Operating status monitoring** (surge, stonewall, efficiency)
- **Outlet temperature mode** for plant data matching

---

## Engineering Deliverables Updates

### `InstrumentScheduleGenerator` (New)

ISA-5.1 tagged instrument schedule generator bridging engineering deliverables and
dynamic simulation. Tags: PT-100+, TT-200+, LT-300+, FT-400+. Creates real
`MeasurementDeviceInterface` objects with `AlarmConfig` (HH/H/L/LL) and SIL ratings.

### `StudyClass` (Updated)

Added `INSTRUMENT_SCHEDULE` to `DeliverableType` enum:
- CLASS_A: 7 deliverables (was 6)
- CLASS_B: 4 deliverables (was 3)

---

## Documentation & Agent Updates

| File | Change |
|------|--------|
| `docs/process/mechanical_design/thermal_hydraulic_design.md` | **New** — 400+ line guide for HX thermal-hydraulic design |
| `docs/wiki/two_fluid_model.md` | **Updated** — Boundary conditions, three-phase, InterfacialFriction, AUSM+ |
| `docs/cookbook/pipeline-recipes.md` | **New** — 770+ line cookbook with TwoFluidPipe recipes |
| `docs/process/equipment/heat_exchangers.md` | **Updated** — Added thermal design section with class table |
| `docs/wiki/heat_exchanger_mechanical_design.md` | **Updated** — Cross-reference to thermal design guide |
| `docs/wiki/pump_theory_and_implementation.md` | **Updated** — Pump curve and cavitation theory |
| `.github/skills/neqsim-capability-map/SKILL.md` | **Updated** — Added 11 new classes, updated gaps |
| `.github/skills/neqsim-api-patterns/SKILL.md` | **Updated** — HX thermal design + engineering deliverables patterns |
| `.github/agents/engineering.deliverables.agent.md` | **Updated** — Instrument schedule as 7th deliverable |
| `.github/agents/field.development.agent.md` | **Updated** — Item 17, updated class map |
| `CHANGELOG_AGENT_NOTES.md` | **Updated** — Two new entries (HX thermal design + instrument schedule) |
| `CONTEXT.md` | **Updated** — HX thermal design in repo map + key locations |
| `AGENTS.md` | **Updated** — HX thermal design + TwoFluidPipe in key paths |
| `docs/REFERENCE_MANUAL_INDEX.md` | **Updated** — Added thermal_hydraulic_design.md entry |

## Test Coverage

| Test File | Tests | Status |
|-----------|-------|--------|
| `ThermalDesignCalculatorTest` | Bell-Delaware, LMTD, vibration, zone analysis | New |
| `TwoFluidPipeBenchmarkTest` | Beggs-Brill, Taitel-Dukler, Mukherjee-Brill | New (@Disabled — needs optimization) |
| `TwoFluidPipeBoundaryConditionTest` | BC types, shut-in, pressure buildup | New (@Disabled) |
| `TwoFluidPipeLiteratureValidationTest` | Literature validation against correlations | New (@Disabled) |
| `DocExamplesCompilationTest` | Documentation code examples compile and run | New |
| `PumpTest` | Pump curve, cavitation, outlet temperature mode | New |
| `InstrumentScheduleGeneratorTest` | 31 tests — ISA tags, live devices, SIL, JSON | Previously added |
| `EngineeringDeliverablesPackageTest` | Updated counts for CLASS_A (7), CLASS_B (4) | Updated |
```

**Tests:** 6 tests in `SURFCostEstimatorTest.java` — NCS tieback, JSON output, cost breakdown, region adjustment, currency conversion, recalculate.

### Enhanced: `WellDesignCalculator` — VME + Temperature Derating

**File:** `src/main/java/neqsim/process/mechanicaldesign/subsea/WellDesignCalculator.java`

Added two critical features per NORSOK D-010 and API 5CT:

1. **Von Mises Equivalent (triaxial) check** — NORSOK D-010 Table 18 requires VME design factor >= 1.25. Combines hoop, axial, and radial stresses:

$$\sigma_{VME} = \sqrt{\frac{1}{2}\left[(\sigma_h - \sigma_a)^2 + (\sigma_a - \sigma_r)^2 + (\sigma_r - \sigma_h)^2\right]}$$

2. **API 5CT temperature derating** — Per API TR 5C3 Table D.1, yield strength is derated at elevated temperatures (>100°C). Supports interpolation from 100°C to 300°C.

3. **`getCasingGradeSMTS()`** — New public method returning Specified Minimum Tensile Strength per API 5CT/ISO 11960 for grades H40 through 25Cr.

4. **`getCasingGradeSMYS()` made public** — Previously private, now public for external access.

### New: `FieldDevelopmentNPVTest` — Integration Test

**File:** `src/test/java/neqsim/process/fielddevelopment/economics/FieldDevelopmentNPVTest.java`

Complete end-to-end test replicating a field development NPV notebook in Java:
- **Full reservoir simulation** using `SimpleReservoir` + `WellFlow` + `PipeBeggsAndBrills`
- **25-year production profile** with plateau and decline phases (bisection search for sustainable rate)
- **Norwegian tax model** via `CashFlowEngine("NO")` — 22% corporate + 56% petroleum
- **Breakeven gas price** calculation
- **3 test methods:** full simulation NPV, simplified fixed-profile NPV, direct spreadsheet-style NPV

## Agent Improvements

The agent instructions were updated based on lessons learned from the field development task:

### Follow-up Questions (New)

Before starting Standard or Comprehensive tasks, agents now ask 7 scoping questions:
1. Fluid/resource composition and resource volume uncertainty
2. Operating envelope (pressure, temperature, flow rate)
3. Standards and jurisdiction (NORSOK, DNV, API, ASME)
4. Economics (prices, discount rate, CAPEX breakdown)
5. Uncertainty scope (full NeqSim MC vs simplified)
6. Deliverables (quick answer, notebook, full report)
7. Risk categories (market, technical, HSE, regulatory)

### Mandatory Uncertainty & Risk Analysis

- Monte Carlo simulations must use **full NeqSim process simulations** in the loop (not simplified Python correlations) when NeqSim classes exist
- Resource/reserve estimates (GIP/STOIIP) must be **uncertain parameters** with P10/P50/P90 reporting
- Performance optimization: cache expensive NeqSim results, classify tornado parameters as technical vs economic
- Risk register with ISO 31000 5×5 matrix is mandatory

### Updated Files

| File | Change |
|------|--------|
| `AGENTS.md` | Added follow-up questions, NeqSim MC mandate, results.json schema |
| `.github/copilot-instructions.md` | Updated items 12-13 with NeqSim MC requirements, resource estimate mandate |
| `.github/agents/solve.task.agent.md` | Added follow-up questions, uncertainty/risk section with code examples, expanded quality gate |
| `.github/agents/notebook.example.agent.md` | Minor updates |
| `.github/agents/solve.process.agent.md` | Minor updates |

## Complete Files Changed

| File | Status | Description |
|------|--------|-------------|
| **Java Source** | | |
| `src/main/java/.../SURFCostEstimator.java` | **New** | SURF CAPEX estimator (~1050 lines) |
| `src/main/java/.../WellDesignCalculator.java` | **Modified** | Added VME check, temperature derating, SMTS method |
| `src/main/java/.../WellMechanicalDesign.java` | **Modified** | Wire VME results to design output |
| **Tests** | | |
| `src/test/java/.../SURFCostEstimatorTest.java` | **New** | 6 tests for SURF cost estimation |
| `src/test/java/.../WellMechanicalDesignTest.java` | **Modified** | Added VME and derating tests |
| `src/test/java/.../FieldDevelopmentNPVTest.java` | **New** | 4 tests — full reservoir sim NPV |
| **Agent Instructions** | | |
| `AGENTS.md` | **Modified** | Follow-up questions, MC patterns |
| `.github/copilot-instructions.md` | **Modified** | NeqSim MC mandate, resource estimates |
| `.github/agents/solve.task.agent.md` | **Modified** | Uncertainty/risk section, quality gates |
| `.github/agents/solve.process.agent.md` | **Modified** | Minor updates |
| `.github/agents/notebook.example.agent.md` | **Modified** | Minor updates |
| **Documentation** | | |
| `docs/development/CODE_PATTERNS.md` | **Modified** | CashFlowEngine pattern, fixed SURF API |
| `docs/development/TASK_LOG.md` | **Modified** | New task entries |
| `docs/development/TASK_SOLVING_GUIDE.md` | **Modified** | MC workflow, resource estimates |
| `docs/REFERENCE_MANUAL_INDEX.md` | **Modified** | Added CashFlowEngine, NorwegianTaxModel, SURFCostEstimator |
| `CONTEXT.md` | **Modified** | Updated repo map |
| **Workflow** | | |
| `devtools/new_task.py` | **Modified** | Minor improvements |

## Test Results

All new and existing tests pass:

```
SURFCostEstimatorTest:       6 passed, 0 failed
WellMechanicalDesignTest:   27 passed, 0 failed
FieldDevelopmentNPVTest:     4 passed, 0 failed
```

## Standards Compliance

| Standard | Implementation |
|----------|---------------|
| NORSOK D-010 Table 18 | VME design factor >= 1.25 in `WellDesignCalculator` |
| API 5CT / ISO 11960 | Casing grade SMYS and SMTS values |
| API TR 5C3 Table D.1 | Temperature derating factors (100-300°C) |
| Norwegian NCS fiscal regime | 22% corporate + 56% petroleum tax in `CashFlowEngine` |

---

## New: Distillation & Column Internals Toolkit

Seven new classes providing rigorous distillation column design — from shortcut methods through tray/packing hydraulics to automated internals sizing.

### `ShortcutDistillationColumn` — Fenske-Underwood-Gilliland Method

Rapid conceptual design using the classic FUG equations:
- **Fenske:** Minimum stages from relative volatility and key component recoveries
- **Underwood:** Minimum reflux ratio from root-finding on the Underwood equation
- **Gilliland:** Actual stages from reflux ratio multiplier (Molokanov correlation)
- **Kirkbride:** Optimal feed tray location

```java
ShortcutDistillationColumn shortcut = new ShortcutDistillationColumn("Depropanizer", feed);
shortcut.setLightKey("propane");
shortcut.setHeavyKey("n-butane");
shortcut.setLightKeyRecoveryDistillate(0.98);
shortcut.setHeavyKeyRecoveryDistillate(0.02);
shortcut.setRefluxRatioMultiplier(1.3);
shortcut.setCondenserPressure(15.0, "bara");
shortcut.setReboilerPressure(16.0, "bara");
shortcut.run();
int nMin = shortcut.getMinimumNumberOfStages();
double rMin = shortcut.getMinimumRefluxRatio();
int nActual = shortcut.getActualNumberOfStages();
String json = shortcut.getResultsJson();
```

### `PackedColumn` — Packed Absorption/Distillation Column

Extends `DistillationColumn` to model packed columns (absorbers, strippers, contactors). Adds packing-specific functionality:
- HETP calculation from packed bed height to determine theoretical stages
- Packing hydraulics via `PackingHydraulicsCalculator` (flooding, pressure drop, mass transfer)
- Built-in packing presets (Pall Ring, Mellapak, IMTP, etc.)

```java
PackedColumn absorber = new PackedColumn("CO2 Absorber", 10, feed);
absorber.setPackedHeight(15.0);
absorber.setPackingType("Mellapak 250Y");
absorber.setStructuredPacking(true);
absorber.addSolventStream(leanAmine, 1);
absorber.run();
double hetp = absorber.getHETP();
double floodPct = absorber.getPercentFlood();
```

### `ColumnSpecification` — Column Degree-of-Freedom Framework

Typed specification for distillation column DOFs with outer secant/bisection adjustment loop:
- `SpecificationType` enum: `PRODUCT_PURITY`, `REFLUX_RATIO`, `COMPONENT_RECOVERY`, `PRODUCT_FLOW_RATE`, `DUTY`
- `ProductLocation` enum: `TOP`, `BOTTOM`

```java
ColumnSpecification topSpec = new ColumnSpecification(
    ColumnSpecification.SpecificationType.PRODUCT_PURITY,
    ColumnSpecification.ProductLocation.TOP,
    0.95, "methane");
column.setTopSpecification(topSpec);
column.setBottomSpecification(new ColumnSpecification(
    ColumnSpecification.SpecificationType.REFLUX_RATIO,
    ColumnSpecification.ProductLocation.TOP, 3.0));
column.run();
```

### `TrayHydraulicsCalculator` — Tray Hydraulics Engine

Per-tray hydraulic evaluation for sieve, valve, and bubble-cap trays:

| Check | Correlation/Reference |
|-------|----------------------|
| Flooding | Fair (Souders-Brown + FLV correction) |
| Weeping | Sinnott (minimum hole velocity) |
| Entrainment | Fair entrainment correlation |
| Downcomer backup | Francis weir formula |
| Pressure drop | Dry tray (orifice) + liquid head + residual head |
| Tray efficiency | O'Connell correlation (α × μ) |
| Turndown | Min/design vapor ratio |

**References:** Kister (1992), Ludwig (2001), Fair (1961), Sinnott (2005).

### `PackingHydraulicsCalculator` — Packing Hydraulics Engine

Industry-standard correlations for packed columns:

| Correlation | Purpose |
|-------------|---------|
| Eckert GPDC | Flooding velocity |
| Leva | Wet packing pressure drop |
| Onda (1968) | Gas/liquid mass transfer coefficients, wetted area |
| HTU/HETP | From two-resistance model |

Built-in presets for 10 random packings (Pall Ring, Raschig Ring, IMTP, Berl Saddle) and
7 structured packings (Mellapak 125Y–500Y, Flexipac 1Y–3Y). Auto-sizes column diameter
to standard vessel sizes.

### `ColumnInternalsDesigner` — Internals Sizing Facade

Evaluates hydraulic performance on every tray of a converged `DistillationColumn`:
- Identifies controlling tray (highest loading), sizes column diameter
- Supports both tray and packed modes
- Produces comprehensive JSON reports with per-tray profiles

```java
DistillationColumn column = new DistillationColumn("Depropanizer", 25, true, true);
column.addFeedStream(feed, 12);
column.run();
ColumnInternalsDesigner designer = new ColumnInternalsDesigner(column);
designer.setTrayType("sieve");
designer.setTraySpacing(0.61);
designer.calculate();
double diameter = designer.getRequiredDiameter();
boolean ok = designer.isDesignOk();
String json = designer.toJson();
```

---

## New: Air Cooler — Full API 661 Thermal Design

Complete rewrite of `AirCooler` from a simple air flow calculator to a **full API 661 thermal design model** (~960 lines):

- **Briggs-Young** fin-tube correlation for air-side HTC
- **Schmidt** annular fin efficiency calculation
- **Robinson-Briggs** air-side pressure drop
- **LMTD** with F-correction for cross-flow (Bowman-Mueller-Nagle)
- **Fan model** with cubic polynomial fan curve (dP vs Q)
- **Ambient temperature correction** (ITD ratio method)
- **Bundle sizing** (tubes per row, total tubes, face area, fin area)
- Default geometry per API 661 (25.4 mm tube OD, 15.875 mm fin height, 2.5 mm fin pitch, 4 rows, 12 m tubes)

```java
AirCooler cooler = new AirCooler("Process Cooler", hotStream);
cooler.setOutTemperature(40.0, "C");
cooler.setDesignAmbientTemperature(15.0, "C");
cooler.setNumberOfTubeRows(4);
cooler.setTubeLength(12.0);
cooler.run();
double duty = cooler.getDuty();
double fanPower = cooler.getFanPower("kW");
double uOverall = cooler.getOverallU();
String json = cooler.toJson();
```

---

## New: PVF Flash — Pressure-Vapor Fraction Flash

New flash specification: given pressure + target vapor fraction → find temperature. Uses Illinois method (accelerated regula falsi) for robustness.

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.PVFflash(0.5);  // Find T where β = 0.5
// β=0.0 → bubble point temperature
// β=1.0 → dew point temperature
```

---

## New: Amine System Framework

### `AmineSystem` — Convenience Wrapper for Amine Thermodynamics

Simplified API for creating rigorous electrolyte-CPA amine systems. Replaces the simplistic Kent-Eisenberg approach:

| Amine | Neutral | Protonated | Carbamate | Max Loading |
|-------|---------|------------|-----------|-------------|
| MEA | MEA | MEA+ | MEACOO- | 0.5 |
| DEA | DEA | DEA+ | DEACOO- | 0.5 |
| MDEA | MDEA | MDEA+ | (none) | 1.0 |
| aMDEA | MDEA, Piperazine | MDEA+, Piperazine+ | PZCOO- | 1.0 |

```java
AmineSystem amineSystem = new AmineSystem(
    AmineSystem.AmineType.MEA,
    273.15 + 40.0, 1.0,  // T [K], P [bara]
    0.30,                 // 30 wt% amine
    0.40);                // CO2 loading (mol CO2 / mol amine)
SystemInterface fluid = amineSystem.getSystem();
```

### `AmineViscosity` — Loaded Amine Solution Viscosity

Correlations for CO₂-loaded amine solutions:
- **Weiland et al. (1998)** for MEA, DEA, aMDEA
- **Teng et al. (1994)** for MDEA
- Auto-detects amine type from fluid composition

### New Database Entries

| Component | ID | Description |
|-----------|----|-------------|
| MEA+ | 1259 | Protonated monoethanolamine ion (charge=+1) |
| MEACOO- | 1260 | MEA carbamate ion (charge=-1, with CPA parameters) |

Updated `REACTIONDATA.csv` and `STOCCOEFDATA.csv` with MEA/DEA equilibrium reactions (Austgen 1989).

---

## New: ProcessSystem Stream Summary

Three new methods on `ProcessSystem` — brings UniSim/HYSYS-style "Workbook" functionality to NeqSim:

```java
ProcessSystem process = new ProcessSystem();
// ... add equipment ...
process.run();

// Formatted text table (like UniSim Workbook)
System.out.println(process.getStreamSummaryTable());

// JSON output for programmatic access
String json = process.getStreamSummaryJson();

// Get all streams
List<StreamInterface> streams = process.getAllStreams();
```

Output includes: T(°C), P(bara), total flow (mol/hr, kg/hr), vapor fraction, molar mass, and component mole fractions for all streams in the flowsheet.

---

## New: NeqSim vs UniSim Comparison Document

Feature-by-feature comparison of NeqSim vs Honeywell UniSim Design across 12 dimensions (thermodynamics, distillation, dynamics, safety, cost estimation, etc.) — see `docs/development/NEQSIM_VS_UNISIM_COMPARISON.md`.

---

## New: Process Simulation Enhancements Guide

Comprehensive user guide for all new capabilities: Air Cooler, Packed Column, Tray Hydraulics, Packing Hydraulics, Column Internals Designer, Shortcut Distillation, PVF Flash + Stream Summary — see `docs/process/process-simulation-enhancements.md`.

---

## New: Jupyter Notebook — Air Cooler & Packed Column

`examples/notebooks/air_cooler_and_packed_column.ipynb` — demonstrates:
- Part 1: Air Cooler setup, thermal design results, fan power
- Part 2: Packed Column (absorber/contactor) with HETP-based staging and packing hydraulics

---

## Updated Test Coverage

| Test File | Tests | Status |
|-----------|-------|--------|
| `PackedColumnTest` | 4 tests (basic absorber, setters/getters, condenser/reboiler, JSON) | New |
| `ShortcutDistillationColumnTest` | 3 tests (deethanizer, depropanizer, JSON) | New |
| `ColumnInternalsDesignerTest` | 4 tests (sieve tray, convenience, packed, structured) | New |
| `PackingHydraulicsCalculatorTest` | 6 tests (Pall Ring, structured, diameter, presets, mass transfer, dP) | New |
| `TrayHydraulicsCalculatorTest` | 6 tests (sieve, diameter, valve, liquid rate, weeping, O'Connell) | New |
| `ProcessSystemStreamSummaryTest` | 3 tests (text table, JSON, getAllStreams) | New |
| `PVFflashTest` | 4 tests (mid-fraction, bubble point, dew point, consistency) | New |
| `AirCoolerTest` | 14 new tests (LMTD, U, fin efficiency, fan, bundle, ITD, JSON) | Expanded |
| `ColumnSpecificationTest` | Column spec purity/recovery/flow rate tests | New |
| `DocExamplesCompilationTest` | 3 new tests (thermal design JSON, interfacial friction) | Expanded |

## Updated Files Summary

| File | Status | Description |
|------|--------|-------------|
| **Java Source — New Classes** | | |
| `.../distillation/PackedColumn.java` | **New** | Packed absorption/distillation column (~436 lines) |
| `.../distillation/ShortcutDistillationColumn.java` | **New** | FUG shortcut method (~774 lines) |
| `.../distillation/ColumnSpecification.java` | **New** | Column DOF specification framework |
| `.../distillation/internals/ColumnInternalsDesigner.java` | **New** | Internals sizing facade (~719 lines) |
| `.../distillation/internals/PackingHydraulicsCalculator.java` | **New** | Packing hydraulics engine (~1032 lines) |
| `.../distillation/internals/TrayHydraulicsCalculator.java` | **New** | Tray hydraulics engine (~1057 lines) |
| `.../flashops/PVFflash.java` | **New** | PVF flash calculation (~231 lines) |
| **Java Source — Major Updates** | | |
| `.../heatexchanger/AirCooler.java` | **Major rewrite** | Full API 661 thermal design (~960 lines added) |
| `.../distillation/DistillationColumn.java` | **Modified** | Column specification framework (+531 lines) |
| `.../processmodel/ProcessSystem.java` | **Modified** | Stream summary methods (+187 lines) |
| `.../amines/AmineSystem.java` | **New/Modified** | Amine system convenience wrapper (+327 lines) |
| `.../viscosity/AmineViscosity.java` | **Modified** | Loaded amine viscosity correlations |
| `.../heatexchanger/ThermalDesignCalculator.java` | **Modified** | Added `toJson()` method |
| `.../ThermodynamicOperations.java` | **Modified** | Added `PVFflash()` entry point |
| **Tests** | | |
| `.../distillation/PackedColumnTest.java` | **New** | 4 tests |
| `.../distillation/ShortcutDistillationColumnTest.java` | **New** | 3 tests |
| `.../distillation/ColumnSpecificationTest.java` | **New** | Column spec tests |
| `.../internals/ColumnInternalsDesignerTest.java` | **New** | 4 tests |
| `.../internals/PackingHydraulicsCalculatorTest.java` | **New** | 6 tests |
| `.../internals/TrayHydraulicsCalculatorTest.java` | **New** | 6 tests |
| `.../processmodel/ProcessSystemStreamSummaryTest.java` | **New** | 3 tests |
| `.../flashops/PVFflashTest.java` | **New** | 4 tests |
| `.../heatexchanger/AirCoolerTest.java` | **Expanded** | +14 new tests |
| `.../DocExamplesCompilationTest.java` | **Expanded** | +3 new tests |
| **Resources** | | |
| `src/main/resources/data/COMP.csv` | **Modified** | Added MEA+, MEACOO- ionic species |
| `src/main/resources/data/REACTIONDATA.csv` | **Modified** | Added MEA/DEA equilibrium reactions |
| `src/main/resources/data/STOCCOEFDATA.csv` | **Modified** | Updated stoichiometric coefficients |
| **Documentation** | | |
| `docs/development/NEQSIM_VS_UNISIM_COMPARISON.md` | **New** | NeqSim vs UniSim comparison (~553 lines) |
| `docs/process/process-simulation-enhancements.md` | **New** | Enhancements user guide (~507 lines) |
| `docs/REFERENCE_MANUAL_INDEX.md` | **Modified** | Added new entries |
| `docs/cookbook/pipeline-recipes.md` | **Modified** | Minor updates |
| `docs/wiki/pump_theory_and_implementation.md` | **Modified** | Minor updates |
| `docs/wiki/two_fluid_model.md` | **Modified** | Minor updates |
| **Examples** | | |
| `examples/notebooks/air_cooler_and_packed_column.ipynb` | **New** | Air cooler + packed column notebook |
| **Agent Instructions** | | |
| `.github/agents/documentation.agent.md` | **Modified** | Minor updates |
| `.github/agents/notebook.example.agent.md` | **Modified** | Minor updates |
| `.github/copilot-instructions.md` | **Modified** | Added amine system patterns |
| `AGENTS.md` | **Modified** | Added amine system + column internals references |

## Updated Standards Compliance

| Standard | Implementation |
|----------|---------------|
| NORSOK D-010 Table 18 | VME design factor >= 1.25 in `WellDesignCalculator` |
| API 5CT / ISO 11960 | Casing grade SMYS and SMTS values |
| API TR 5C3 Table D.1 | Temperature derating factors (100-300°C) |
| Norwegian NCS fiscal regime | 22% corporate + 56% petroleum tax in `CashFlowEngine` |
| API 661 | Air cooler thermal design (Briggs-Young, Robinson-Briggs, Schmidt fin efficiency) |
| TEMA RCB-4.6 | Vibration screening (existing — VibrationAnalysis) |
| Fair (1961) | Tray flooding (Souders-Brown) and entrainment correlations |
| Kister (1992) | Tray hydraulics reference design practices |
| Sinnott (2005) | Weeping check, tray pressure drop |
| O'Connell (1946) | Tray efficiency correlation |
| Eckert GPDC | Packed column flooding and capacity limits |
| Onda (1968) | Gas/liquid mass transfer coefficients for packed columns |
| Leva | Wet packing pressure drop |
| Fenske-Underwood-Gilliland | Shortcut distillation column design |
| Weiland et al. (1998) | Loaded amine solution viscosity (MEA, DEA, aMDEA) |
| Teng et al. (1994) | MDEA solution viscosity |
| Austgen (1989) | MEA/DEA reaction equilibrium constants |
