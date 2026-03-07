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

### Step-by-step

1. **Create the task folder:**
   ```bash
   python devtools/new_task.py "your task title" --type B --author "Name"
   ```
   Types: A=Property, B=Process, C=PVT, D=Standards, E=Feature, F=Design, G=Workflow

2. **Read the generated README** at `task_solve/YYYY-MM-DD_task_slug/README.md`

3. **Follow the 3-step workflow:**

   **Step 1 — Scope & Research**
   - Fill `step1_scope_and_research/task_spec.md` (standards, methods, deliverables, acceptance criteria)
   - Write research notes to `step1_scope_and_research/notes.md`
   - Place literature papers, standards PDFs, and lab reports in `step1_scope_and_research/references/`
   - Summarise each document's key contributions in `notes.md` under "Literature & Reference Documents"

   **Step 2 — Analysis & Evaluation**
   - Create a Jupyter notebook in `step2_analysis/` using NeqSim
   - Use the dual-boot setup cell (see below)
   - Run all cells, validate results against acceptance criteria
   - Save plots to `figures/` as PNG
   - **Save results.json** in the task root (see pattern below)

   **Step 3 — Report**
   - `generate_report.py` auto-reads `task_spec.md` and `results.json`
   - Run `python step3_report/generate_report.py` to produce Word + HTML

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
}
with open(str(TASK_DIR / "results.json"), "w") as f:
    json.dump(results, f, indent=2)
```

The report generator auto-reads this file to populate Results and Validation sections.
- **key_results**: Rendered as styled table with auto-detected units (use suffixes like `_C`, `_bar`, `_kg`, `_hours`)
- **validation**: Rendered as pass/fail table with color coding
- **equations**: KaTeX in HTML, PNG images in Word
- **figures**: Numbered captions from `figure_captions`
- **tables**: Custom tables rendered in both HTML and Word with headers/rows
- **references**: Numbered reference list rendered in the References section of the report

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
fluid.init(3);
double density = fluid.getDensity("kg/m3");
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

### Python (Jupyter) fluid

```python
from neqsim import jneqsim
fluid = jneqsim.thermo.system.SystemSrkEos(273.15 + 25.0, 60.0)
fluid.addComponent("methane", 0.85)
fluid.setMixingRule("classic")
```

## Key Paths

| Path | Purpose |
|------|---------|
| `src/main/java/neqsim/` | Main source (thermo, process, pvt, standards) |
| `src/test/java/neqsim/` | JUnit 5 tests (mirrors src structure) |
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

## Documentation

All classes and methods need complete JavaDoc (class description, `@param`,
`@return`, `@throws`). HTML5-compatible: use `<caption>` in tables, no
`summary` attribute, no `@see` with plain text. Run `./mvnw javadoc:javadoc`
to verify.
