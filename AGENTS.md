# NeqSim — Agent Instructions

> This file is read automatically by OpenAI Codex, Claude Code, and other AI
> coding agents. For VS Code Copilot, see `.github/copilot-instructions.md`.

## Quick Orientation

Read `CONTEXT.md` for a 60-second overview of the codebase (repo map, build
commands, code patterns).

## Critical Constraint: Java 8

**All code MUST compile with Java 8.** Never use:
- `var`, `List.of()`, `Map.of()`, `Set.of()`, `String.repeat()`,
  `str.isBlank()`, `str.strip()`, text blocks (`"""`), records, pattern
  matching `instanceof`, `Optional.isEmpty()`.
- Use explicit types, `Arrays.asList()`, `StringUtils.repeat()`, `str.trim().isEmpty()`.

## Build & Test

```bash
./mvnw install                            # full build (Linux/Mac)
mvnw.cmd install                          # Windows
./mvnw test -Dtest=SeparatorTest          # single test class
./mvnw test -Dtest=SeparatorTest#testTwo  # single method
./mvnw checkstyle:check spotbugs:check pmd:check  # static analysis
```

## Solving Engineering Tasks (Primary Workflow)

NeqSim supports an AI-driven task-solving workflow. When asked to solve an
engineering task (hydrate prediction, pipeline sizing, compressor design, etc.):

### ⚠️ MANDATORY: All output goes to `task_solve/` folder

**Every task MUST create a folder under `task_solve/` FIRST.** All deliverables
(task_spec.md, notebooks, notes.md, results.json, figures/) are placed inside
this folder. Never write task analysis files to `examples/`, docs, or the
workspace root.

### ⚠️ MANDATORY: All downloaded documents go INSIDE the task folder

**All documents retrieved during a task — STID drawings, PI historian exports,
vendor datasheets, P&IDs, literature PDFs — MUST be saved to
`step1_scope_and_research/references/` within the task folder.** Never save
task-related files to workspace-level directories like `output/` or `figures/`.
Converted PNGs go to the task's `figures/` directory. This ensures tasks are
self-contained and portable.

### Step-by-step

1. **Create the task folder (DO THIS FIRST — non-negotiable):**
   ```bash
   neqsim new-task "your task title" --type B --author "Name"
   ```
   Types: A=Property, B=Process, C=PVT, D=Standards, E=Feature, F=Design, G=Workflow

2. **Read the generated README** at `task_solve/YYYY-MM-DD_task_slug/README.md`

3. **Follow the 3-step workflow:**

   **Step 1 — Scope & Research**
   - Fill `step1_scope_and_research/task_spec.md` (standards, methods, deliverables, acceptance criteria)
   - **Run `@capability.scout` and write the result to** `step1_scope_and_research/capability_assessment.md` (mandatory for Standard/Comprehensive). The validator will warn if this artifact is missing or unfilled.
   - **Discover the right skills** via semantic search:
     ```bash
     python devtools/skill_search.py "<your task title>" --top 5
     ```
     Load the top-3 skills before starting analysis.
   - **Pull literature and internal docs** via `@literature.scout`. PDFs land in `step1_scope_and_research/references/`; manifest in `references/manifest.json`; summaries appended to `notes.md`.
   - Write **substantive** research notes to `step1_scope_and_research/notes.md` (no empty template sections)
   - Place literature papers, standards PDFs, and lab reports in `step1_scope_and_research/references/`
   - **Extract figures from PDFs** using `devtools/pdf_to_figures.py`:
     ```bash
     python devtools/pdf_to_figures.py step1_scope_and_research/references/ --outdir figures/
     ```
     Then view extracted pages with `view_image` to read diagrams, P&IDs, charts, and tables.
   - Summarise each document's key contributions in `notes.md` under "Literature & Reference Documents"

   **Step 1.5 — Deep Analysis & Solution Design (MANDATORY for Standard/Comprehensive)**
   - Write `step1_scope_and_research/analysis.md` with physics deep-dive, alternative
     approaches, NeqSim capability assessment, solution architecture, and engineering
     insight questions (5-10 questions the analysis must answer)
   - Write `step1_scope_and_research/neqsim_improvements.md` with NIPs for every NeqSim
     gap found — propose concrete Java classes with method signatures and test cases
   - Compute order-of-magnitude estimates BEFORE running simulations

   **Step 2 — Analysis & Evaluation**
   - Create a Jupyter notebook in `step2_analysis/` using NeqSim
  - Use the devtools setup cell (see below) so workspace Java classes load from `target/classes`
   - Run all cells, validate results against acceptance criteria
   - **MANDATORY: Include detailed results table** with all key outputs and units
   - **MANDATORY: Include at least 2-3 matplotlib figures** (profiles, sensitivities, comparisons) with axis labels, units, titles, legends, and grids
   - **MANDATORY: After EVERY figure, add a discussion markdown cell** with:
     observation (what the figure shows with numbers), physical mechanism (why),
     engineering implication (what it means for design), and recommendation
     (specific action). Populate `figure_discussion` in results.json.
   - Save all figures to `figures/` as PNG (dpi=150, bbox_inches="tight")
   - **MANDATORY: Create a separate benchmark validation notebook** (`XX_benchmark_validation.ipynb`) comparing NeqSim results against independent reference data (NIST, textbook examples, published cases, industry benchmarks). Include at least 3 data points, a parity/deviation plot, and save `benchmark_validation` results to `results.json`
   - **MANDATORY: Create a separate uncertainty & risk notebook** (`XX_uncertainty_risk_analysis.ipynb`) that:
     - Identifies key uncertain input parameters and assigns realistic ranges (low/base/high or distribution)
     - **MUST use full NeqSim process simulations inside the Monte Carlo loop** — do NOT
       use simplified Python correlations when NeqSim classes exist for the calculation
       (e.g., use `SimpleReservoir` + `PipeBeggsAndBrills` for production profiles, not
       a Python exponential decline). Simplified models are only acceptable when NeqSim
       has no equivalent class.
     - **Resource/reserve estimates MUST be uncertain parameters** — always include
       GIP or STOIIP as a triangular/lognormal input. Report P10/P50/P90 for GIP,
       recovery factor, and total production alongside the main output.
     - Runs Monte Carlo simulation (N≥200 with NeqSim, N≥1000 for simplified models)
       to produce P10/P50/P90 estimates of the main output
     - **Performance optimisation pattern**: Cache expensive NeqSim results that don't
       change between iterations (e.g., compute base SURF cost once, scale by multiplier).
       In tornado sensitivity, classify parameters as "technical" (require NeqSim re-run)
       vs "economic" (reuse base production profile, recalculate cash flow only).
     - Generates a tornado diagram showing input sensitivity ranking
     - Includes a risk register with 6-10 risks across categories (Market, Technical,
       Cost, Schedule, HSE, Regulatory), using ISO 31000 5×5 matrix
     - Saves `uncertainty` and `risk_evaluation` results to `results.json`
   - **Save results.json** in the task root (see pattern below)

   **Step 2.5 — Consistency Check (MANDATORY before report)**
   - Run `python devtools/consistency_checker.py task_solve/YYYY-MM-DD_slug/`
   - The tool extracts numerical values from all notebooks and results.json
   - Detects inconsistencies: numerical mismatches, scope mismatches (e.g., volumetric vs mass-based), contradictory claims
   - Produces `consistency_report.json` in the task folder
   - **Fix any CRITICAL issues before generating the report**
   - Common issues: external study data measuring different quantities than notebook calculations

   **Step 3 — Report**
   - `generate_report.py` auto-reads `task_spec.md` and `results.json`
   - Run `python step3_report/generate_report.py` to produce a professional
     engineering report (Report.docx + Report.html)
   - Scientific papers (`--paper`) are only generated when explicitly requested
   - **Important:** The template now has built-in styled formatting for
     Benchmark Validation, Uncertainty Analysis, and Risk Evaluation sections
     (color-coded risk badges, P10/P50/P90 tables, tornado tables, benchmark
     PASS/FAIL tables). These render automatically when the corresponding keys
     exist in `results.json`. Ensure `figure_captions` in results.json covers
     figures from ALL notebooks, not just the main one. When design parameters
     change, update hardcoded numbers in MANUAL_SECTIONS.

4. **Create a PR** with reusable outputs:
   ```bash
   git checkout -b task/task-slug
   # Copy reusable files to proper locations (NEVER commit task_solve/ contents)
   cp task_solve/.../notebook.ipynb examples/notebooks/
   cp task_solve/.../SomeTest.java src/test/java/neqsim/...
   git add examples/notebooks/ src/test/java/ docs/development/TASK_LOG.md
   git commit -m "Add [description] from task: [title]"
   git push -u origin task/task-slug
   gh pr create --title "Add [description]" --body "From task-solving workflow"
   ```

5. **Fix and improve documentation** encountered during the task:
   - If you find **errors** in existing docs (wrong API signatures, outdated
     patterns, incorrect examples), fix them and include the fixes in the PR.
   - If you discover **missing documentation** (undocumented classes, missing
     cookbook recipes, gaps in guides), add it and include in the PR.
   - If you identify **improvements** (clearer explanations, better examples,
     additional warnings), make the changes and include in the PR.
   - Update the relevant index files (`REFERENCE_MANUAL_INDEX.md`, section
     `index.md`) when adding new doc pages.
   - Documentation fixes go in the **same PR** as the task outputs so
     reviewers see the full context of what was learned.

### Devtools notebook cell (use in every task notebook)

Task notebooks and runner workflows must use `neqsim_dev_setup.py` so Java
classes come from the workspace (`target/classes`) instead of the installed
Python `neqsim` package. This makes new Java classes available without copying
a packaged JAR into `site-packages/neqsim/lib/java11/`.

```python
import os
import sys
from pathlib import Path


def find_neqsim_project_root():
  env_root = os.environ.get("NEQSIM_PROJECT_ROOT")
  candidates = []
  if env_root:
    candidates.append(Path(env_root).resolve())
  cwd = Path.cwd().resolve()
  candidates.extend([cwd] + list(cwd.parents))
  for candidate in candidates:
    if (candidate / "pom.xml").exists() and (candidate / "devtools" / "neqsim_dev_setup.py").exists():
      return candidate
  raise RuntimeError("Could not find NeqSim project root. Set NEQSIM_PROJECT_ROOT.")


PROJECT_ROOT = find_neqsim_project_root()
sys.path.insert(0, str(PROJECT_ROOT / "devtools"))

from neqsim_dev_setup import neqsim_init, neqsim_classes

ns = neqsim_init(project_root=PROJECT_ROOT, recompile=False, verbose=True)
ns = neqsim_classes(ns)
NEQSIM_MODE = "devtools"
```

### Follow-up Questions (ASK BEFORE STARTING)

Before beginning any Standard or Comprehensive task, ask the user these scoping
questions to avoid rework and produce better results:

1. **Fluid / resource**: What is the reservoir fluid composition? If unavailable,
   what type (lean gas, rich gas, oil, condensate)? What is the estimated
   resource volume (GIP/STOIIP) and its uncertainty range?
2. **Operating envelope**: What are the design pressure, temperature, and flow
   rate ranges? Any constraints (backpressure limit, arrival temperature)?
3. **Standards & jurisdiction**: Which design codes apply (NORSOK, DNV, API,
   ASME)? Which fiscal/tax regime (Norwegian NCS, UK, generic)?
4. **Economics**: What gas/oil price range and currency? What discount rate?
   Are cost estimates needed (CAPEX breakdown, OPEX)?
5. **Uncertainty scope**: Which parameters are most uncertain? Should Monte
   Carlo use full NeqSim process simulations (slower, more accurate) or
   simplified correlations (faster)?
6. **Deliverables**: What output format — quick answer, notebook only, or full
   Word + HTML report? Are benchmarks against published data required?
7. **Risk categories**: Which risk categories matter most (market, technical,
   HSE, regulatory, schedule)?

For Quick-scale tasks, skip questions and proceed directly.

### Adaptive scale

- **Quick** — single property/question → minimal task_spec, few cells, brief summary
- **Standard** — process sim, PVT study → full task_spec, complete notebook, Word + HTML
- **Comprehensive** — multi-discipline, Class A study → detailed task_spec, multiple notebooks, full HTML with navigation

### Save results.json (end of every notebook)

```python
import json, os, pathlib

# Resolve task directory from notebook location (NOT os.getcwd — unreliable in VS Code)
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
    "figure_captions": {
        # "plot.png": "Description of the figure"
    },
    "figure_discussion": [
        # {"figure": "plot.png", "title": "Plot Title",
        #  "observation": "What the figure shows", "mechanism": "Why it happens",
        #  "implication": "What it means for design", "recommendation": "Action to take",
        #  "linked_results": ["key_result_name"], "insight_question_ref": "Q1"}
    ],
    "equations": [
        # {"label": "Energy Balance", "latex": "Q = m C_p \\Delta T"}
    ],
    "tables": [
        # {"title": "Sensitivity Analysis",
        #  "headers": ["Parameter", "Base Case", "Low", "High"],
        #  "rows": [["Pressure (bar)", 60.0, 40.0, 80.0],
        #           ["Temperature (C)", 25.0, 15.0, 35.0]]}
    ],
    "references": [
        # {"id": "Smith2019", "text": "Smith, J. (2019). CNG Tank Thermal Analysis. J. Energy Storage, 25, 100-115."},
        # {"id": "API521", "text": "API 521, 7th Edition (2020). Pressure-Relieving and Depressuring Systems."},
        # {"id": "DNV-ST-F101", "text": "DNV-ST-F101 (2021). Submarine Pipeline Systems."}
    ],
    "uncertainty": {
        # "method": "Monte Carlo with full NeqSim process simulation",
        # "n_simulations": 200,
        # "simulation_engine": "NeqSim (SRK EOS, SimpleReservoir, PipeBeggsAndBrills)",
        # "input_parameters": [
        #     {"name": "GIP Volume", "unit": "m3", "low": 0.65e9, "base": 1.0e9, "high": 1.45e9, "distribution": "triangular"},
        #     {"name": "Gas Price", "unit": "NOK/Sm3", "low": 0.8, "base": 1.5, "high": 2.5, "distribution": "triangular"},
        #     {"name": "CAPEX Multiplier", "unit": "-", "low": 0.85, "base": 1.0, "high": 1.4, "distribution": "triangular"},
        # ],
        # "output_parameter": "NPV after tax (MNOK)",
        # "p10": -22.0,
        # "p50": 3352.0,
        # "p90": 7086.0,
        # "mean": 3471.0,
        # "std": 2617.0,
        # "prob_negative_pct": 10.5,
        # "resource_estimate": {
        #     "gip_GSm3_p10": 105.0, "gip_GSm3_p50": 135.0, "gip_GSm3_p90": 169.0,
        #     "recovery_factor_pct_p10": 45.0, "recovery_factor_pct_p50": 57.0, "recovery_factor_pct_p90": 66.0,
        #     "total_production_GSm3_p10": 67.0, "total_production_GSm3_p50": 77.0, "total_production_GSm3_p90": 86.0
        # },
        # "capex_mnok": {"p10": 13100.0, "p50": 14700.0, "p90": 17500.0},
        # "tornado": [
        #     {"parameter": "Gas Price (0.8-2.5 NOK/Sm3)", "npv_low": -688, "npv_high": 10302, "swing": 10990},
        # ]
    },
    "risk_evaluation": {
        # "risks": [
        #     {"id": "R1", "description": "Gas price below breakeven", "category": "Market",
        #      "likelihood": "Possible", "consequence": "Major", "risk_level": "High",
        #      "mitigation": "Long-term sales contracts, hedging"},
        # ],
        # "overall_risk_level": "Medium",
        # "risk_matrix_used": "5x5 (ISO 31000)"
    },
}
with open(str(TASK_DIR / "results.json"), "w") as f:
    json.dump(results, f, indent=2)

# ── Programmatic quality gate: validate results.json ──
TaskResultValidator = ns.JClass("neqsim.util.agentic.TaskResultValidator")

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

### Iterative Updates to results.json

When working iteratively with continuous updates:

1. **Load before Modifying** — Always read existing results.json before adding new data:
   ```python
   results_path = TASK_DIR / "results.json"
   if results_path.exists():
       with open(results_path, "r") as f:
           results = json.load(f)
   else:
       results = {}
   ```

2. **Use dict.update() for New Data** — Merge new results without losing existing:
   ```python
   results["key_results"] = {**results.get("key_results", {}), "new_result": 42.5}
   results["figure_captions"] = {**results.get("figure_captions", {}), "new_plot.png": "Caption"}
   ```

3. **Append to Lists** — For discussion, tables, equations:
   ```python
   results.setdefault("figure_discussion", []).append(new_discussion)
   results.setdefault("tables", []).append(new_table)
   ```

4. **Run Consistency Check** before report generation:
   ```bash
   python devtools/consistency_checker.py task_solve/YYYY-MM-DD_slug/
   ```

5. **Regenerate Report** — The report generator dynamically includes sections based on
   what's present in results.json. Adding `uncertainty` or `risk_evaluation` automatically
   creates those sections in the report.

The report generator auto-reads this file to populate Results and Validation sections.
- **key_results**: Rendered as styled table with auto-detected units (use suffixes like `_C`, `_bar`, `_kg`, `_hours`)
- **validation**: Rendered as pass/fail table with color coding
- **equations**: KaTeX in HTML, PNG images in Word
- **figures**: Numbered captions from `figure_captions`
- **figure_discussion**: Discussion blocks with observation, mechanism, implication, recommendation — rendered as a "Discussion" section (report) or inline in "Results and Discussion" (paper). Links figures to conclusions via traceability chain.
- **tables**: Custom tables rendered in both HTML and Word with headers/rows
- **references**: Numbered reference list rendered in the References section of the report
- **uncertainty**: Monte Carlo results (P10/P50/P90, tornado data, probability of negative outcome) rendered as styled tables in the Uncertainty Analysis section
- **risk_evaluation**: Risk register with color-coded risk levels (High=red, Medium=orange, Low=green), summary badges, and mitigation table in the Risk Evaluation section
- **benchmark_validation**: Benchmark tests rendered as a table with PASS/FAIL color coding and detail columns

## Code Patterns

### Create a fluid

```java
SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 60.0);
fluid.addComponent("methane", 0.85);
fluid.addComponent("ethane", 0.10);
fluid.addComponent("propane", 0.05);
fluid.setMixingRule("classic"); // NEVER skip
```

### Run a flash

```java
ThermodynamicOperations ops = new ThermodynamicOperations(fluid);
ops.TPflash();
fluid.initProperties();  // MANDATORY: initializes both thermodynamic AND transport properties
// NOTE: init(3) alone does NOT initialize transport properties (viscosity, thermal conductivity)
double density = fluid.getDensity("kg/m3");
double viscosity = fluid.getPhase("gas").getViscosity("kg/msec");
double thermalCond = fluid.getPhase("gas").getThermalConductivity("W/mK");
```

### Phase envelope (CRITICAL: branch labels are swapped)

When using `calcPTphaseEnvelope(true, 1.0)` (bubblePointFirst=true), the getter
method names are SWAPPED — `getBubblePointTemperatures()` returns physically DEW
curve data and vice versa. **Always classify branches by physical reasoning:**

```python
branch_A_T = np.array(envelope.getBubblePointTemperatures())
branch_B_T = np.array(envelope.getDewPointTemperatures())
# The DEW curve always has the higher max temperature (contains cricondentherm)
if branch_A_T.max() > branch_B_T.max():
    dew_T = branch_A_T   # "bubble" getter returns dew data (swapped!)
    bub_T = branch_B_T
else:
    dew_T = branch_B_T
    bub_T = branch_A_T
```

### Process simulation

```java
Stream feed = new Stream("feed", fluid);
feed.setFlowRate(100.0, "kg/hr");
Separator sep = new Separator("HP sep", feed);
ProcessSystem process = new ProcessSystem();
process.add(feed);
process.add(sep);
process.run();
```

### Separator mechanical design (physical configuration)

Physical dimensions, internals, and design parameters are configured through
`SeparatorMechanicalDesign` — NOT directly on `Separator`. This follows the
same pattern used for wells, pipelines, compressors, and heat exchangers.
Bridge methods delegate to the Separator's performance calculator:

```java
// After process.run():
sep.initMechanicalDesign();
SeparatorMechanicalDesign design =
    (SeparatorMechanicalDesign) sep.getMechanicalDesign();
design.setMaxOperationPressure(85.0);
design.setGasLoadFactor(0.107);       // K-factor [m/s]
design.setRetentionTime(120.0);       // Liquid retention [s]
design.setInletNozzleID(0.254);       // 10-inch inlet nozzle [m]
design.setDemisterType("wire_mesh");

// Bridge methods — inlet pipe, inlet device, sections
design.setInletPipeDiameter(0.254);   // Inlet pipe ID for DSD [m]
design.setInletDeviceType(InletDeviceModel.InletDeviceType.INLET_VANE);
design.addSeparatorSection("Demister", "meshpad");

// Bridge methods — dynamic internals
design.setWeirHeightAbsolute(0.30);   // Weir height [m]
design.setWeirLength(1.5);            // Weir crest length [m]
design.setBootVolume(2.0);            // Boot/sump volume [m3]
design.setMistEliminatorDpCoeff(150.0);  // Euler number for dP
design.setMistEliminatorThickness(0.15); // Demister thickness [m]

design.readDesignSpecifications();
design.calcDesign();
String json = design.toJson();
```

**Internals classes** (`mechanicaldesign.separator.internals`):
- `DemistingInternal` — Eu-number pressure drop, Souders-Brown max velocity,
  carry-over model for wire mesh / vane pack / cyclone demisting devices
- `DemistingInternalWithDrainage` — adds drainage section efficiency

**Primary separation** (`mechanicaldesign.separator.primaryseparation`):
- `PrimarySeparation` — inlet momentum, bulk separation, carry-over
- `InletVane` (6000 Pa, 85%), `InletVaneWithMeshpad` (92%+mesh),
  `InletCyclones` (8000 Pa, 95%)

### Stream introspection

Every `ProcessEquipmentInterface` exposes its connected streams:

```java
List<StreamInterface> inlets = sep.getInletStreams();   // [feed]
List<StreamInterface> outlets = sep.getOutletStreams();  // [gasOut, liquidOut]
```

### Named controllers

Attach multiple controllers to any equipment by tag name:

```java
valve.addController("LC-100", levelController);
valve.addController("PC-200", pressureController);
ControllerDeviceInterface lc = valve.getController("LC-100");
Map<String, ControllerDeviceInterface> all = valve.getControllers();
```

### Explicit connections

Record typed connection metadata on a `ProcessSystem`:

```java
process.connect(feed, sep,
    ProcessConnection.ConnectionType.MATERIAL, "Feed");
List<ProcessConnection> conns = process.getConnections();
```

### Unified element query

`ProcessElementInterface` is the common supertype for equipment, controllers,
and measurement devices. Query all elements at once:

```java
List<ProcessElementInterface> all = process.getAllElements();
```

### Automation API (PREFERRED for agents)

String-addressable variable access without navigating Java internals.
Use `ProcessAutomation` for reading/writing simulation variables:

```java
// Get the automation facade (convenience method on ProcessSystem)
ProcessAutomation auto = process.getAutomation();

// Discover units and variables
List<String> units = auto.getUnitList();                    // ["Feed Gas", "HP Sep", ...]
List<SimulationVariable> vars = auto.getVariableList("HP Sep"); // all variables with type/unit/description
String eqType = auto.getEquipmentType("HP Sep");            // "Separator"

// Read values with unit conversion
double t = auto.getVariableValue("HP Sep.gasOutStream.temperature", "C");
double p = auto.getVariableValue("HP Sep.pressure", "bara");
double flow = auto.getVariableValue("HP Sep.gasOutStream.flowRate", "kg/hr");

// Write inputs (only INPUT-type variables) and re-run
auto.setVariableValue("Compressor.outletPressure", 150.0, "bara");
process.run();  // propagate changes
```

For multi-area `ProcessModel`:

```java
ProcessAutomation plantAuto = plant.getAutomation();
List<String> areas = plantAuto.getAreaList();  // ["Separation", "Compression"]
// Area-qualified addresses
double t = plantAuto.getVariableValue("Separation::HP Sep.gasOutStream.temperature", "C");
plantAuto.setVariableValue("Compression::Compressor.outletPressure", 170.0, "bara");
plant.run();
```

### Self-healing automation (PREFERRED for agents)

The automation API includes self-diagnosis and auto-correction. When an address
is wrong, use the safe accessors for automatic fuzzy matching and recovery:

```java
ProcessAutomation auto = process.getAutomation();

// Safe get — returns JSON with value on success, or diagnostics on failure
String result = auto.getVariableValueSafe("hp separator.temperature", "C");
// Returns: {"status":"auto_corrected","originalAddress":"hp separator.temperature",
//           "correctedAddress":"HP Sep.temperature","value":25.0,"unit":"C",...}

// Safe set — validates physical bounds + fuzzy address matching
String setResult = auto.setVariableValueSafe("Compressor.outletPressure", 150.0, "bara");

// Access diagnostics for learning and insights
AutomationDiagnostics diag = auto.getDiagnostics();
String report = diag.getLearningReport();  // operation stats, error patterns, corrections
```

Key capabilities:
- **Fuzzy name matching** — finds closest unit/property when exact match fails
- **Auto-correction** — fixes case, whitespace, partial names, typos (edit distance ≤ 2)
- **Learned corrections** — remembers past corrections for instant reuse
- **Physical bounds** — validates temperature, pressure, efficiency ranges before setting
- **Operation tracking** — tracks success/failure rates and generates recommendations

### Lifecycle state: save, restore, compare

Portable, Git-diffable JSON snapshots for reproducibility and version tracking:

```java
// Save a ProcessSystem snapshot
ProcessSystemState state = ProcessSystemState.fromProcessSystem(process);
state.setName("Gas Processing");
state.setVersion("1.0.0");
state.saveToFile("model_v1.json");                    // human-readable JSON
state.saveToCompressedFile("model_v1.json.gz");       // smaller for archival

// Load and validate
ProcessSystemState loaded = ProcessSystemState.loadFromFile("model_v1.json");
ProcessSystemState.ValidationResult result = loaded.validate();
assert result.isValid();

// Multi-area ProcessModel state
ProcessModelState modelState = ProcessModelState.fromProcessModel(plant);
modelState.setVersion("1.0.0");
modelState.saveToFile("plant_v1.json");

// Version comparison (design reviews, change tracking)
ProcessModelState v2 = ProcessModelState.fromProcessModel(plant);
v2.setVersion("2.0");
ProcessModelState.ModelDiff diff = ProcessModelState.compare(v1, v2);
assert diff.hasChanges();
// diff.getModifiedParameters(), diff.getAddedEquipment(), diff.getRemovedEquipment()

// Compressed bytes for network/API transfer (no disk I/O)
byte[] bytes = modelState.toCompressedBytes();
ProcessModelState restored = ProcessModelState.fromCompressedBytes(bytes);
```

### Python (Jupyter) fluid in task notebooks

```python
fluid = ns.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.setMixingRule("classic")
```

### Headless execution (no kernel restarts)

For long-running or fragile simulations, use the neqsim_runner to run each job
in an isolated subprocess with automatic retry:

```python
from neqsim_runner.agent_bridge import AgentBridge

bridge = AgentBridge(task_dir="task_solve/2026-04-08_my_task")

# Submit a notebook (default: mode="execute" produces executed .ipynb with outputs)
job_ids = [bridge.submit_notebook("step2_analysis/notebook.ipynb", max_retries=3)]

# Alternative: use mode="script" to convert to .py (lighter, no .ipynb output)
# job_ids = [bridge.submit_notebook("step2_analysis/notebook.ipynb", mode="script")]

# Alternative: submit a standalone script
# job_ids = [bridge.submit_script("run_sim.py", args={"pressure": 60.0})]

# Alternative: submit a parametric sweep (each case = own subprocess + JVM)
# cases = [{"pressure": p} for p in [30, 60, 90, 120]]
# job_ids = bridge.submit_parametric_sweep("run_case.py", cases)

# Run all (supervisor handles retry/recovery)
bridge.run_all(max_parallel=1)

summary = bridge.summary()
if summary["failed"] or summary["pending"]:
    raise RuntimeError("NeqSim Runner jobs did not all complete successfully")

# Read results
results = bridge.get_results(job_ids[0])
bridge.merge_results_to_task(job_ids)

# Get the executed notebook (with cell outputs, plots, etc.)
executed_nb = bridge.get_executed_notebook(job_ids[0])
```

CLI equivalent: `python -m neqsim_runner go my_sim.py --args '{"pressure": 60}'`

### Task progress checkpoints (survives context exhaustion)

Long-running tasks often exhaust the agent's context window. The progress
tracker writes a `progress.json` to the task folder after each milestone.
A fresh agent reads it and resumes where the previous one left off:

```python
from neqsim_runner.progress import TaskProgress

# Start or resume
progress = TaskProgress("task_solve/2026-04-08_my_task")
if progress.is_resuming():
    print(progress.resume_summary())  # prints what's done + next action

# Checkpoint after each milestone
progress.complete_milestone("step1_research_done",
    summary="Research complete. SRK EOS, 3-stage compression.",
    outputs=["step1_scope_and_research/task_spec.md"],
    decisions={"eos": "SRK", "scale": "Standard"})
progress.store_context("feed_composition", {"methane": 0.85, "ethane": 0.07})
progress.set_next_action("Create notebook: 01_compression.ipynb")
```

### Build process from JSON

```java
// Static convenience methods on ProcessSystem
SimulationResult result = ProcessSystem.fromJsonAndRun(jsonString);
if (result.isSuccess()) {
    ProcessSystem process = result.getProcessSystem();
    // Access equipment by name:
    process.getUnit("HP Separator");
    // Access streams by dot-notation:
    process.resolveStreamReference("HP Separator.gasOut");
}
// Tolerant: wiring failures become warnings, not errors
for (ErrorDetail w : result.getWarnings()) {
    System.out.println(w.getCode() + ": " + w.getMessage());
}
```

```python
# Python equivalent inside task notebooks/runner jobs
import json
ProcessSystem = ns.ProcessSystem
result = ProcessSystem.fromJsonAndRun(json.dumps(neqsim_json))
if not result.isError():
    process = result.getProcessSystem()
```

### Subsea well design (mechanical design + cost)

```java
SubseaWell well = new SubseaWell("Producer-1", stream);
well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
well.setMeasuredDepth(3800.0);
well.setWaterDepth(350.0);
well.setMaxWellheadPressure(345.0);
well.setReservoirPressure(400.0);
well.setProductionCasingOD(9.625);
well.setProductionCasingDepth(3800.0);
well.setTubingOD(5.5); well.setTubingWeight(23.0); well.setTubingGrade("L80");
well.setHasDHSV(true);
well.setPrimaryBarrierElements(3);
well.setSecondaryBarrierElements(3);
well.setDrillingDays(45.0);
well.setCompletionDays(25.0);

well.initMechanicalDesign();
WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
design.calcDesign();           // API 5C3 burst/collapse/tension + NORSOK D-010
design.calculateCostEstimate(); // drilling, completion, wellhead, logging
String json = design.toJson();
```

**Standards:** API 5CT/ISO 11960 (casing grades), API Bull 5C3 (burst/collapse/tension),
NORSOK D-010 (design factors, barriers), API RP 90 (annular pressure).

### Equipment design feasibility reports

After running compressors or heat exchangers in a process simulation, generate
a feasibility report to check if equipment is realistic to build and operate:

```java
// Compressor feasibility
CompressorDesignFeasibilityReport report =
    new CompressorDesignFeasibilityReport(compressor);
report.setDriverType("gas-turbine");
report.setCompressorType("centrifugal");
report.setAnnualOperatingHours(8000);
report.generateReport();

String verdict = report.getVerdict();  // FEASIBLE / FEASIBLE_WITH_WARNINGS / NOT_FEASIBLE
String json = report.toJson();         // Full JSON: mech design, cost, suppliers, curves
report.applyChartToCompressor();       // Apply generated performance curves

// Heat exchanger feasibility
HeatExchangerDesignFeasibilityReport hxReport =
    new HeatExchangerDesignFeasibilityReport(heatExchanger);
hxReport.setExchangerType("shell-and-tube");
hxReport.setDesignStandard("TEMA-R");
hxReport.generateReport();
String hxVerdict = hxReport.getVerdict();
```

Reports include: mechanical design, cost estimation (CAPEX + OPEX + lifecycle),
supplier matching (15 compressor OEMs, 14 HX suppliers), feasibility issues
with severity (BLOCKER/WARNING/INFO), and compressor curve generation.

### CO2 injection well analysis

Full-stack safety analysis for CO2 injection wells — steady-state flow, phase
boundary mapping, impurity enrichment, shutdown transients, and flow corrections:

```java
// High-level analyzer
CO2InjectionWellAnalyzer analyzer = new CO2InjectionWellAnalyzer("InjWell-1");
analyzer.setFluid(co2Fluid);
analyzer.setWellGeometry(1300.0, 0.1571, 5e-5);
analyzer.setOperatingConditions(90.0, 25.0, 150000.0);
analyzer.setFormationTemperature(4.0, 43.0);
analyzer.addTrackedComponent("hydrogen", 0.10);
analyzer.runFullAnalysis();
boolean safe = analyzer.isSafeToOperate();

// Impurity monitoring
ImpurityMonitor monitor = new ImpurityMonitor("H2-Mon", stream);
monitor.addTrackedComponent("hydrogen", 0.10);
double enrichment = monitor.getEnrichmentFactor("hydrogen");

// Formation temperature gradient on PipeBeggsAndBrills
PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("Wellbore", feed);
pipe.setFormationTemperatureGradient(4.0, -0.03, "C");

// Shutdown transient
TransientWellbore wellbore = new TransientWellbore("Shutdown", stream);
wellbore.setWellDepth(1300.0);
wellbore.setFormationTemperature(277.15, 316.15);
wellbore.setShutdownCoolingRate(6.0);
wellbore.runShutdownSimulation(48.0, 1.0);

// CO2 flow corrections (static utility)
boolean dense = CO2FlowCorrections.isDensePhase(system);
double holdupCorr = CO2FlowCorrections.getLiquidHoldupCorrectionFactor(system);
```

**Classes:** `CO2InjectionWellAnalyzer`, `TransientWellbore`, `CO2FlowCorrections`
in `process.equipment.pipeline`; `ImpurityMonitor` in `process.measurementdevice`.

### Python (Jupyter) — CO2 well analysis

```python
CO2InjectionWellAnalyzer = ns.JClass("neqsim.process.equipment.pipeline.CO2InjectionWellAnalyzer")
TransientWellbore = ns.JClass("neqsim.process.equipment.pipeline.TransientWellbore")
CO2FlowCorrections = ns.JClass("neqsim.process.equipment.pipeline.CO2FlowCorrections")
ImpurityMonitor = ns.JClass("neqsim.process.measurementdevice.ImpurityMonitor")
```

## Key Paths

| Path                                                                    | Purpose                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/main/java/neqsim/`                                                 | Main source (thermo, process, pvt, standards)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `src/test/java/neqsim/`                                                 | JUnit 5 tests (mirrors src structure)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `src/main/java/neqsim/process/equipment/`                               | ProcessEquipmentInterface, MultiPortEquipment, stream introspection                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `src/main/java/neqsim/process/processmodel/`                            | ProcessSystem, ProcessConnection, ProcessElementInterface, JsonProcessBuilder, SimulationResult                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `src/main/java/neqsim/process/automation/`                              | ProcessAutomation (string-addressable variable API), AutomationDiagnostics (fuzzy matching, auto-correction, physical validation, learning), SimulationVariable (INPUT/OUTPUT descriptor)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `src/main/java/neqsim/process/processmodel/lifecycle/`                  | ProcessSystemState, ProcessModelState — JSON lifecycle snapshots, version comparison, compressed transfer                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `devtools/unisim_reader.py`                                             | UniSim COM reader → NeqSim Python/notebook/EOT/JSON (UniSimReader, UniSimToNeqSim, UniSimComparator). 45+ typed operation handlers via `UniSimOperationHandler`, port-specific forward refs, auto-recycle wiring. **Default E300 fluid export**: `read(export_e300=True)` extracts Tc, Pc, omega, MW, BIPs from COM and writes E300 files for all fluid packages. `build_and_run()` auto-loads E300 fluids via `EclipseFluidReadWrite.read()` and `ProcessSystem.fromJsonAndRun(json, fluid)`. Generated JSON includes `_unisim_operation_mapping` for native/adapter/reference/control/internal/skip traceability. **Full mode default**: `full_mode=True` (all 4 methods) auto-classifies sub-flowsheets as process/utility, includes only process SFs in ProcessModel. Verified with TUTOR1.usc (11/13 streams match) and R510 SG Condensation (31 comp, 250 ops, 8 SFs: 78% isolated match, 71% connected). |
| `devtools/test_unisim_outputs.py`                                       | Pure-Python tests for UniSim converter output modes, E300 fluid export, operation handler registry strategy, and JSON mapping summaries (no COM needed — synthetic models)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `examples/notebooks/tutor1_gas_processing.ipynb`                        | End-to-end UniSim→NeqSim verification: TUTOR1 gas processing (7 comp, PR EOS, 13 ops). Reference for conversion workflows.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `src/main/java/neqsim/process/mechanicaldesign/subsea/`                 | Well & SURF design, cost estimation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `src/main/java/neqsim/process/mechanicaldesign/`                        | Engineering deliverables (StudyClass, InstrumentScheduleGenerator, etc.)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `src/main/java/neqsim/process/mechanicaldesign/heatexchanger/`          | HX thermal-hydraulic design (ThermalDesignCalculator, BellDelawareMethod, VibrationAnalysis)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `src/main/java/neqsim/process/equipment/subsea/`                        | SubseaWell, SubseaTree equipment                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `src/main/java/neqsim/process/equipment/pipeline/`                      | Pipe flow, TwoFluidPipe, CO2InjectionWellAnalyzer, TransientWellbore                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `src/main/java/neqsim/process/measurementdevice/`                       | Transmitters (PT, TT, LT, FT), AlarmConfig, ImpurityMonitor                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `examples/notebooks/`                                                   | Jupyter notebook examples                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `devtools/new_task.py`                                                  | Task-solving script                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `devtools/neqsim_runner/`                                               | Supervised simulation runner — isolated subprocess per job, auto-retry, checkpoint/resume, SQLite state. Use `AgentBridge` for task-solving integration.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `devtools/pdf_to_figures.py`                                            | Convert PDF pages to PNG images for AI analysis. Use `pdf_to_pngs()` for single files, `pdf_folder_to_pngs()` for batch. Requires `pymupdf`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `devtools/skill_search.py`                                              | Semantic skill retrieval — TF-IDF + cosine over every SKILL.md `description`. Run `python devtools/skill_search.py "<task title>" --top 5` at the start of a task to load the right skills. Falls back to Jaccard tokens if scikit-learn is missing.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `devtools/validate_task_results.py`                                     | CI gate that mirrors `TaskResultValidator` rules in pure Python. Modes: positional, `--all`, `--changed` (reads `CHANGED_FILES`). Also warns when `step1_scope_and_research/capability_assessment.md` is missing or unfilled.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `.github/workflows/task_quality_gate.yml`                               | PR gate: runs `validate_task_results.py` + `consistency_checker.py` on changed task folders only.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `.github/workflows/task_nip_issues.yml`                                 | On push to master, opens one labelled GitHub issue per newly added `neqsim_improvements.md` (deduped by title).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `step1_scope_and_research/capability_assessment.md` (per task)          | Mandatory artifact for Standard/Comprehensive tasks: capability requirements matrix, NeqSim coverage check, gap implementation plan, skills to load. Auto-scaffolded into every new task by `new_task.py`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `.github/agents/literature.scout.agent.md`                              | Literature & internal-database scout — pulls papers, standards, and STID/vendor docs into `step1_scope_and_research/references/`, writes `references/manifest.json`, summarises into `notes.md`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `.github/agents/review.agent.md`                                        | Review agent — grades a task folder before PR (schema, consistency, capability_assessment, notebook execution, figure traceability, repo-memory hits). Read-only.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `devtools/verify_skills_agents.py`                                      | CI lint for `.github/skills/` and `.github/agents/` — enforces YAML front-matter, validates `skill-index.json` references, flags orphan skills.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `devtools/generate_agent_skill_map.py`                                  | Auto-generates `docs/development/AGENT_SKILL_MAP.md` from `Loaded skills:` lines in agent files. CI fails if the map is stale.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `.github/workflows/skills_agents_lint.yml`                              | PR/push gate that runs the verifier and the map generator; ensures skill↔agent linkage stays accurate.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `docs/development/TASK_SOLVING_GUIDE.md`                                | Full workflow guide                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `docs/development/CODE_PATTERNS.md`                                     | Copy-paste code starters                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `docs/development/TASK_LOG.md`                                          | Past solved tasks (search before starting)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `.github/agents/solve.task.agent.md`                                    | Detailed agent instructions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `.github/agents/router.agent.md`                                        | Request routing and multi-agent composition                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `.github/agents/capability.scout.agent.md`                              | Capability assessment, gap analysis, implementation planning                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `.github/agents/field.development.agent.md`                             | Field development studies, concept selection, economics                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `.github/agents/engineering.deliverables.agent.md`                      | Engineering deliverables (PFD, instruments, fire, noise, etc.)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `.github/agents/extract.process.agent.md`                               | Extract process info from documents → NeqSim JSON / ProcessModule → simulation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `src/main/java/neqsim/process/fielddevelopment/`                        | Field development workflows, economics, screening                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `src/main/java/neqsim/process/util/fielddevelopment/`                   | Production profiles, scheduling, DCF calculator                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `docs/fielddevelopment/`                                                | Field development documentation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `CHANGELOG_AGENT_NOTES.md`                                              | API changes agents need to know about                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `src/main/java/neqsim/process/equipment/heatexchanger/heatintegration/` | Pinch analysis (PinchAnalysis, HeatStream) for heat integration                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `src/main/java/neqsim/process/equipment/powergeneration/`               | Power generation (GasTurbine, SteamTurbine, HRSG, CombinedCycleSystem)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `src/main/java/neqsim/util/agentic/`                                    | Agentic infrastructure (TaskResultValidator, SimulationQualityGate, AgentSession)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `.github/agents/reaction.engineering.agent.md`                          | Reaction engineering systems design                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `.github/agents/control.system.agent.md`                                | Control system and instrumentation design                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `.github/agents/emissions.environmental.agent.md`                       | Emissions calculation and environmental compliance                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `.github/agents/ccs.hydrogen.agent.md`                                  | CCS value chain and hydrogen systems (CO2 transport, injection, H2 blending)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `.github/agents/technical.reader.agent.md`                              | Read technical documents (PDF, Word, Excel) and engineering images (P&IDs, mechanical drawings, vendor datasheets, performance maps, phase envelopes) — extract equipment data, compositions, requirements, stream tables, piping topology, dimensions, and operating conditions                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

## Skills Reference

Skills are reusable knowledge packages loaded automatically by agents:

| Skill                                              | Purpose                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `neqsim-api-patterns`                              | EOS selection, fluid creation, flash, equipment patterns                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `neqsim-java8-rules`                               | Forbidden Java 9+ features, replacement patterns                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `neqsim-notebook-patterns`                         | Jupyter notebook structure, visualization, performance estimation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `neqsim-optimization-and-doe`                      | Process flowsheet optimization & DoE — decision tree across the 30 NeqSim optimizer classes (ProcessOptimizationEngine, ProductionOptimizer, SQPoptimizer, MultiObjectiveOptimizer, MonteCarloSimulator, BatchStudy, ProcessSimulationEvaluator, DesignOptimizer), SciPy/Pyomo/BoTorch bridging, sensitivity, Pareto, uncertainty                                                                                                                                                                                                                                                                                                                                       |
| `neqsim-pdf-ocr`                                   | OCR text extraction from scanned PDFs and P&IDs — OCRmyPDF + Tesseract + pytesseract, tag-pattern post-filtering, P&ID-tuned settings (400 DPI, sparse PSM)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `neqsim-troubleshooting`                           | Recovery strategies for convergence failures, zero values, phase issues                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `neqsim-input-validation`                          | Pre-simulation checks (T, P, composition, component names)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `neqsim-regression-baselines`                      | Baseline management for preventing accuracy drift                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `neqsim-standards-lookup`                          | Industry standards lookup — equipment-to-standards mapping, CSV database queries, compliance tracking in results.json                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `neqsim-agent-handoff`                             | Structured schemas for multi-agent result passing (includes lifecycle state handoff)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `neqsim-physics-explanations`                      | Plain-language explanations of engineering phenomena                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `neqsim-capability-map`                            | Structured inventory of NeqSim capabilities by discipline                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `neqsim-model-calibration-and-data-reconciliation` | Digital twin model calibration and data reconciliation — bounded parameter tuning, steady-state windowing, residual diagnostics, train/validation reporting                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `neqsim-field-development`                         | Field development workflows, concept selection, lifecycle management                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `neqsim-field-economics`                           | NPV, IRR, cash flow, tax regimes (Norwegian NCS, UK), cost estimation                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `neqsim-subsea-and-wells`                          | Subsea systems, well design, SURF cost, tieback analysis                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `neqsim-production-optimization`                   | Decline curves, bottleneck analysis, gas lift, network optimization                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `neqsim-process-extraction`                        | Extract process data from text/tables/PFDs into NeqSim JSON builder format                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `neqsim-unisim-reader`                             | UniSim COM reader — component/EOS/operation-handler registry mapping, topology reconstruction, forward refs, verification. **Default E300 fluid export** for lossless transfer of critical properties (Tc, Pc, omega, MW, BIPs) including hypothetical/pseudo components. Uses `UniSimOperationHandler` strategies (`native`, `adapter`, `reference`, `control`, `column_internal`, `skip`) and `_unisim_operation_mapping` JSON summaries; balance/virtual/template placeholders use `UnisimCalculator` adapters. Includes TUTOR1 verified reference case, DistillationColumn solver limitations for NGL-rich feeds, HeatExchanger UA tuning notes, separator 2-phase/3-phase auto-detection (flashtank with WaterProduct promoted to ThreePhaseSeparator), orientation detection (vertical → GasScrubber, horizontal → Separator), and entrainment extraction (liquid carryover, gas carry-under, water-in-oil, oil-in-water). |
| `neqsim-eos-regression`                            | EOS parameter regression — kij tuning, PVT matching (CME, CVD), C7+ characterization, scipy optimization                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `neqsim-reaction-engineering`                      | Reactor patterns — GibbsReactor, PlugFlowReactor, StirredTankReactor, KineticReaction, CatalystBed, **AnaerobicDigester, FermentationReactor, BiogasUpgrader, biorefinery modules**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `neqsim-dynamic-simulation`                        | Dynamic simulation — runTransient, PID controllers, transmitters, tuning, depressurization                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `neqsim-distillation-design`                       | Distillation column design — solver selection, feed tray rules, convergence, internals sizing                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `neqsim-electrolyte-systems`                       | Electrolyte/brine chemistry — SystemElectrolyteCPAstatoil, ions, scale risk, MEG injection                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `neqsim-flow-assurance`                            | Flow assurance — hydrate, wax, asphaltene, corrosion, pipeline hydraulics, inhibitor dosing                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| `neqsim-water-hammer`                              | Water/liquid hammer screening — `WaterHammerPipe`, `WaterHammerStudy`, MCP `runWaterHammer`, STID route geometry, tagreader event windows, valve closure, pump trip, pressure envelopes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `neqsim-ccs-hydrogen`                              | CCS and hydrogen — CO2 phase behavior with impurities, dense phase transport, injection wells, H2 blending                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `neqsim-power-generation`                          | Power generation — gas turbines, steam turbines, HRSG, combined cycle, heat integration                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `neqsim-platform-modeling`                         | Production platform process modeling — multi-stage separation, recompression with compressor curves and anti-surge, export/injection compression, scrubber liquid recycles, Cv valve flow, iteration strategies, structured result extraction. Derived from 15+ NCS platform models                                                                                                                                                                                                                                                                                                                                                                                    |
| `neqsim-technical-document-reading`                | Read technical documents and engineering images — PDF/Word/Excel extraction, P&ID topology, vendor datasheet parsing, image analysis with view_image, performance map digitization, figure discussion generation                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `neqsim-trapped-liquid-fire-rupture`               | Blocked-in liquid fire rupture workflow — evidence retrieval, trapped inventory, API 521 fire exposure, material/flange derating, PFP demand, and source-term handoff                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `neqsim-stid-retriever`                            | Retrieve engineering documents (compressor curves, mechanical drawings, data sheets) for tasks. Supports local dirs, manual upload, pluggable retrieval backends (configured via gitignored `devtools/doc_retrieval_config.yaml`). Includes relevance filtering by task type and retrieval manifests for traceability                                                                                                                                                                                                                                                                                                                                                  |
| `neqsim-process-safety`                            | HAZOP guidewords, LOPA worksheets via `LOPAResult`, SIL determination via `SafetyInstrumentedFunction` (IEC 61508/61511), bow-tie via `BowTieModel`/`BowTieAnalyzer`, 5×5 risk matrix via `RiskMatrix` (NORSOK Z-013, CCPS, API 754) |
| `neqsim-heat-integration`                          | Pinch analysis with `PinchAnalysis` — composite & grand composite curves, ΔTmin selection, MER targeting, retrofit diagnostics, auto-extract via `PinchAnalysis.fromProcessSystem(process, dTmin)` |
| `neqsim-equipment-cost-estimation`                 | Equipment-level CAPEX via `CostEstimationCalculator` — Turton/Peters/Ulrich correlations, CEPCI escalation (2019→2025), material/pressure factors, AACE class 1–5, Cp→Cbm→Ctm→Cgr stackup |
| `neqsim-relief-flare-network`                      | PSV sizing per API 520 (gas/liquid/two-phase) via `ReliefValveSizing`, API 521 fire heat input, flare radiation API 537 via `Flare.estimateRadiationHeatFlux`, header back-pressure & Mach checks |
| `neqsim-controllability-operability`               | Operating envelope mapping, turndown analysis, control valve sizing per ISA-75/IEC 60534 via `ThrottlingValve`, startup/shutdown sequences, recycle stability diagnostics |
| `neqsim-utilities-specification`                   | Steam levels (HP/MP/LP), cooling water (ΔT 10–15 °C), instrument air (≤ −40 °C dew point), fuel gas (Wobbe Index), N₂, demin water, refrigeration; per NORSOK U-001, ISA-7.0.01 |
| `neqsim-professional-reporting`                    | Deliverable quality — `results.json` master schema, figure→discussion→linked_results traceability, KaTeX math, citations, AACE class declaration, uncertainty disclosure (P10/P50/P90), risk register, benchmark validation |

## API Verification (Mandatory)

Before using any NeqSim class in examples or notebooks:
1. Search for the class to confirm it exists
2. Read constructor and method signatures
3. Use only methods that actually exist with correct parameter types
4. Do NOT assume convenience overloads — check first

## Documentation Code Verification (Mandatory)

Every code example in documentation, tutorials, or cookbooks MUST be verified by a runnable test:

1. **Write a JUnit test** that calls every API method shown in the doc
   - Append to `src/test/java/neqsim/DocExamplesCompilationTest.java`
   - Or create a dedicated test in the appropriate package
2. **Run the test** and confirm it passes before finalizing the doc
   - Use `./mvnw test -Dtest=DocExamplesCompilationTest` (or the specific test class)
   - **If the test fails, fix the documentation code — do NOT finalize with broken examples**
   - This step is NON-NEGOTIABLE — never skip it, even for "simple" examples
3. **Common bugs caught by this process**:
   - Plus fraction names with `+` character (use `"C20"` not `"C20+"`)
   - Calling `characterisePlusFraction()` before `setMixingRule()`
   - Wrong method names (`getUnitOperation()` vs `getUnit()`)
   - Wrong parameter types (`int` given where `double` expected)
   - Risk threshold descriptions inconsistent with source logic
   - Methods requiring unit strings (e.g., `setDesignAmbientTemperature(15.0, "C")` not `setDesignAmbientTemperature(15.0)`)
   - Getter methods requiring arguments (e.g., `getFanStaticPressure(flow)` not `getFanStaticPressure()`)

This policy applies to ALL agents that produce code for documentation.

## Notebook Execution Verification (Mandatory)

**Every Jupyter notebook MUST be executed after creation and all cells must pass.**
Notebooks that have not been run are NOT considered complete.

Workflow:
1. **Compile latest workspace classes** before running notebooks that use new/modified classes:
   ```bash
  ./mvnw compile  # Linux/Mac
  mvnw.cmd compile  # Windows
   ```
2. **Use the devtools setup cell** (`neqsim_dev_setup.py`, `ns.*`) in the first code cell
3. **Run every code cell in order** — use NeqSim Runner by default for task notebooks
4. **If any cell fails**, fix the code in that cell and re-run before continuing
5. **Common runtime errors**:
   - `AttributeError` — method doesn't exist; read the Java source for correct name
   - `TypeError: No matching overloads` — wrong arguments; check Java method signature
   - Inherited methods (e.g., `getInternalDiameter()` not `getColumnDiameter()`)
   - Getters requiring arguments (e.g., `getFanStaticPressure(double)`)
6. **A notebook is NOT complete until all cells execute without errors**

This policy applies to ALL agents that produce Jupyter notebooks.

## Documentation

All classes and methods need complete JavaDoc (class description, `@param`,
`@return`, `@throws`). HTML5-compatible: use `<caption>` in tables, no
`summary` attribute, no `@see` with plain text. Run `./mvnw javadoc:javadoc`
to verify.
