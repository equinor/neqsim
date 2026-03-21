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

### Step-by-step

1. **Create the task folder (DO THIS FIRST — non-negotiable):**
   ```bash
   python devtools/new_task.py "your task title" --type B --author "Name"
   ```
   Types: A=Property, B=Process, C=PVT, D=Standards, E=Feature, F=Design, G=Workflow

2. **Read the generated README** at `task_solve/YYYY-MM-DD_task_slug/README.md`

3. **Follow the 3-step workflow:**

   **Step 1 — Scope & Research**
   - Fill `step1_scope_and_research/task_spec.md` (standards, methods, deliverables, acceptance criteria)
   - Write **substantive** research notes to `step1_scope_and_research/notes.md` (no empty template sections)
   - Place literature papers, standards PDFs, and lab reports in `step1_scope_and_research/references/`
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
   - Use the dual-boot setup cell (see below)
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

   **Step 3 — Report**
   - `generate_report.py` auto-reads `task_spec.md` and `results.json`
   - Run `python step3_report/generate_report.py` to produce Word + HTML
   - Add `--paper` to also generate a scientific paper: `Paper.docx` + `Paper.html`
   - Use `--paper-only` to generate only the paper (skips technical report)
   - For the paper, configure `PAPER_TITLE`, `PAPER_AUTHORS`, `PAPER_KEYWORDS`,
     and `PAPER_SECTIONS` in `generate_report.py`
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

### Dual-boot notebook cell (use in every notebook)

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
```

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

### Python (Jupyter) fluid

```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.setMixingRule("classic")
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

## Key Paths

| Path | Purpose |
|------|---------|
| `src/main/java/neqsim/` | Main source (thermo, process, pvt, standards) |
| `src/test/java/neqsim/` | JUnit 5 tests (mirrors src structure) |
| `src/main/java/neqsim/process/equipment/` | ProcessEquipmentInterface, MultiPortEquipment, stream introspection |
| `src/main/java/neqsim/process/processmodel/` | ProcessSystem, ProcessConnection, ProcessElementInterface |
| `src/main/java/neqsim/process/mechanicaldesign/subsea/` | Well & SURF design, cost estimation |
| `src/main/java/neqsim/process/equipment/subsea/` | SubseaWell, SubseaTree equipment |
| `examples/notebooks/` | Jupyter notebook examples |
| `devtools/new_task.py` | Task-solving script |
| `docs/development/TASK_SOLVING_GUIDE.md` | Full workflow guide |
| `docs/development/CODE_PATTERNS.md` | Copy-paste code starters |
| `docs/development/TASK_LOG.md` | Past solved tasks (search before starting) |
| `.github/agents/solve.task.agent.md` | Detailed agent instructions |

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
3. **Common bugs caught by this process**:
   - Plus fraction names with `+` character (use `"C20"` not `"C20+"`)
   - Calling `characterisePlusFraction()` before `setMixingRule()`
   - Wrong method names (`getUnitOperation()` vs `getUnit()`)
   - Wrong parameter types (`int` given where `double` expected)
   - Risk threshold descriptions inconsistent with source logic

This policy applies to ALL agents that produce code for documentation.

## Documentation

All classes and methods need complete JavaDoc (class description, `@param`,
`@return`, `@throws`). HTML5-compatible: use `<caption>` in tables, no
`summary` attribute, no `@see` with plain text. Run `./mvnw javadoc:javadoc`
to verify.
