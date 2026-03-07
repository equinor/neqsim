"""
new_task.py - Create a new task-solving workspace and task folders.

This script lives in devtools/ (tracked in git) and is always available
after cloning. It auto-creates the task_solve/ folder structure on first run.

Usage:
    python devtools/new_task.py "JT cooling for rich gas"
    python devtools/new_task.py "TEG dehydration sizing" --type B
    python devtools/new_task.py "hydrate formation temperature" --type A --author "Your Name"
    python devtools/new_task.py --setup              # just create task_solve/ without a task
    python devtools/new_task.py --list               # list existing tasks
"""
import os
import shutil
import sys
from datetime import date


# ── Paths ────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
TASK_SOLVE_DIR = os.path.join(PROJECT_ROOT, "task_solve")
TEMPLATE_DIR = os.path.join(TASK_SOLVE_DIR, "TASK_TEMPLATE")

TASK_TYPES = {
    "A": "Property",
    "B": "Process",
    "C": "PVT",
    "D": "Standards",
    "E": "Feature",
    "F": "Design",
    "G": "Workflow",
}


# ══════════════════════════════════════════════════════════
# Embedded templates — these are the "source of truth" so
# new users get them even though task_solve/ is gitignored.
# ══════════════════════════════════════════════════════════

WORKSPACE_README = r"""# AI-Supported Task Solving While Developing

This folder is a **local working area** for solving engineering tasks using the
3-step AI-assisted workflow. It is in `.gitignore` — nothing here is committed.
Each task gets its own subfolder with scope & research notes, simulation results,
figures, and reports.

## Quick Start

Open VS Code Copilot Chat and type:

```
@solve.task JT cooling for rich gas at 100 bara
```

That's it. The agent creates the folder, researches the topic, builds and runs
a simulation, validates the results, and generates reports (Word + HTML) — all
in one session. You solve advanced engineering tasks while simultaneously
improving the NeqSim toolbox.

> **Alternative:** If you prefer a manual step-by-step approach, run
> `python devtools/new_task.py "your task"` and follow the prompts in the
> generated README.

---

## Prerequisites

| Requirement | Install / Setup | Used In |
|-------------|----------------|---------|
| Python 3.8+ | [python.org](https://python.org) | All steps |
| Java JDK 8+ | Bundled via `devtools/` setup | Step 2 simulations |
| NeqSim dev tools | `pip install -e devtools/` (from repo root) | Step 2 — boots the JVM and gives you `neqsim_dev_setup` |
| VS Code + GitHub Copilot Chat | VS Code marketplace | All steps |
| python-docx | `pip install python-docx matplotlib` | Step 3 reports |
| Google NotebookLM (optional) | [notebooklm.google.com](https://notebooklm.google.com) | Step 1 research (or use Copilot — see below) |

> **Important:** For the task-solving workflow use `pip install -e devtools/` — this
> installs the `neqsim_dev_setup` helper that boots the JVM from your local build.
> Do **not** use `pip install neqsim` here (that installs the released package, not
> your working copy).

**Quick setup (one-time):**

```powershell
cd path/to/neqsim
pip install -e devtools/          # installs neqsim_dev_setup for Jupyter
pip install python-docx matplotlib # for Word reports and plots
```

---

## Who Is This For?

### Path A: Process Engineer (I just want answers)

You have an engineering question — "What's the hydrate temperature for this
gas?" or "Size a 3-stage compressor train." You don't want to learn Java or git.

1. Open VS Code Copilot Chat
2. Type: `@solve.task hydrate temperature for wet gas at 100 bara`
3. The agent creates a folder, runs the simulation, and gives you results
4. Find reports in `task_solve/.../step3_report/`

### Path B: Developer (I want to extend NeqSim)

You're solving a task AND improving the NeqSim codebase. When the API is
missing something, you add it mid-task — new methods, equipment, or models.

1. Type: `@solve.task add JT coefficient method` (or run `python devtools/new_task.py` for manual control)
2. Work through all 3 steps — the agent flags API gaps as it goes
3. Add the missing Java code, rebuild, and the notebook picks it up immediately
4. Promote reusable code back into `src/main/`, `src/test/`, or `examples/`

### Path C: Researcher (I need a technical assessment)

You're producing a deliverable — a report, technology screening, or design
study. The 3 steps map directly to a professional workflow.

1. Type: `@solve.task field development concept selection for deepwater gas`
2. Review and refine the scope and research notes the agent produces
3. Iterate on analysis — the agent refines until results validate
4. Run report generation to produce Word + HTML deliverables

### Path D: Other AI Tools (OpenAI Codex, Claude Code, Cursor, etc.)

The workflow is **not tied to VS Code Copilot Chat**. The script, folder
structure, templates, and report generator work from any terminal. Any AI
coding agent that can read files and run commands can drive the workflow.

**How to start a task from OpenAI Codex (or any AI agent):**

1. Run the setup: `python devtools/new_task.py "your task" --type B`
2. Point the agent to the guide:
   ```
   Read docs/development/TASK_SOLVING_GUIDE.md for the full workflow.
   Read the task README at task_solve/YYYY-MM-DD_your_task/README.md.
   Follow the 3-step workflow: fill task_spec.md, create a notebook
   in step2_analysis/, then run step3_report/generate_report.py.
   ```
3. The AI agent reads the templates, fills in the task spec, creates notebooks,
   and runs the report generator — same output, different tool.

**What works everywhere** (no VS Code required):
- `python devtools/new_task.py` — creates task folders
- `task_spec.md` — scope document (plain markdown)
- Jupyter notebooks — work in any Python environment
- `python step3_report/generate_report.py` — generates Word + HTML
- `git` + `gh pr create` — contributing back via PR

**What's VS Code Copilot-specific** (optional convenience):
- `@solve.task` agent — automates the full workflow (the agent reads
  `.github/agents/solve.task.agent.md` for its instructions)
- Specialist agents (`@thermo.fluid`, `@solve.process`, etc.)

---

## Common Task Examples

| Type | Example Tasks |
|------|--------------|
| **A - Property** | Density of CO2 at 200 bar; viscosity of MEG-water; JT coefficient for rich gas |
| **B - Process** | TEG dehydration unit; 3-stage compression; HP/LP separation train |
| **C - PVT** | CME test for reservoir oil; CCE at 100C; swelling test with CO2 injection |
| **D - Standards** | Wobbe index per ISO 6976; hydrocarbon dew point; AGA flow measurement |
| **E - Feature** | Add anti-surge to compressor; fix CPA solver for CO2-water; new property method |
| **F - Design** | Pipeline wall thickness per DNV; separator mechanical design; PSV sizing |
| **G - Workflow** | Field development concept selection; technology screening; design basis |

---

## Adaptive Complexity — One Workflow, Any Scale

The framework adapts automatically. You don't need to configure anything — the
agent scales its depth based on what you ask for:

| Scale | Example | What Happens |
|-------|---------|-------------|
| **Quick** | "density of CO2 at 200 bar" | Minimal task_spec, one notebook cell, brief summary — done in minutes |
| **Standard** | "TEG dehydration for 50 MMSCFD" | Full task_spec, complete notebook, Word + HTML reports |
| **Comprehensive** | "field development concept selection per NORSOK" | Detailed task_spec with all standards, multiple notebooks per discipline, full HTML report with navigation |

**The same `@solve.task` command handles all of these.** The agent reads your
request and decides how deep to go. Specify standards ("per DNV-OS-F101") and
deliverables ("with sensitivity analysis and cost estimate") to guide depth.

### Guiding the Analysis

You control the scope through your request — the more you specify, the deeper
the analysis. Compare:

- **Simple:** `@solve.task hydrate temperature for wet gas at 100 bara`
  → Quick calculation, one-page result

- **Medium:** `@solve.task hydrate temperature for wet gas at 100 bara, per NORSOK P-001, with inhibitor dosing curve`
  → Standard analysis with standards compliance and sensitivity plot

- **Full study:** `@solve.task field development flow assurance assessment per NORSOK P-001 and DNV-RP-F109, covering hydrate, wax, corrosion, and slugging for 50 km subsea tieback, deliver phase envelopes, inhibitor curves, pipeline profiles, and design basis document`
  → Multi-notebook comprehensive study with full deliverable set

---

## The 3-Step Workflow

```
 STEP 1                    STEP 2                    STEP 3
 Scope & Research          Analysis & Evaluation     Report

 Define standards,         Build simulation,         Word + HTML
 methods, deliverables     run, validate, iterate    deliverables

 Google NotebookLM         NeqSim API +              python-docx
  or Copilot               GitHub Copilot            + HTML template
 + open sources
                           Iteration is implicit:
 Build knowledge base      refine until validated
```

### Step 1: Scope & Research

This step has two parts: **define the scope** (what to do) and **research** (gather background).

#### Part A: Task Specification (scope)

Before any analysis, define what governs the work:

- **Applicable standards**: Which codes and standards apply? (e.g., NORSOK P-001,
  ISO 6976, DNV-OS-F101, API 520, ASME B31.3, company TR documents)
- **Calculation methods/models**: Which EOS, correlations, or pipe flow models
  to use? (e.g., SRK-CPA for polar systems, Beggs & Brill for multiphase flow,
  OLGA-style thermal-hydraulic)
- **Required deliverables**: What must the final output include? (e.g., phase
  envelopes, pressure profiles, sizing calculations, sensitivity plots, VFP tables)
- **Acceptance criteria**: Tolerances, design factors, safety margins, convergence
  targets (e.g., mass balance < 0.1%, design factor per DNV = 0.72)
- **Operating envelope**: Range of conditions to cover (pressures, temperatures,
  flow rates, compositions)

Fill in `step1_scope_and_research/task_spec.md` — this is the "brief" that
guides everything in Step 2.

#### Part B: Research (background knowledge)

Use **either** Google NotebookLM or Copilot — or both:

**Option A: Google NotebookLM** (best for deep literature review)
- Upload PDFs, standards documents, and technical papers
- Ask questions across all your sources at once
- Get cited answers with references back to source pages

**Option B: GitHub Copilot in VS Code** (best for code-adjacent research)
- Open Copilot Chat and ask research questions directly
- Copilot can search the web, read repo docs, and summarise findings

**Copilot research workflow:**

1. Open VS Code Copilot Chat (Ctrl+Shift+I)
2. Paste this prompt:
   ```
   I'm researching [TOPIC] for a NeqSim task.
   Search the web and this repository for:
   1. Key physical principles and governing equations
   2. Typical operating ranges and design rules of thumb
   3. Relevant industry standards (API, ASME, ISO, NORSOK, DNV)
   4. What NeqSim classes/methods already exist for this
   Write the findings to step1_scope_and_research/notes.md in my task folder.
   ```
3. Review and refine — ask follow-up questions
4. Save the final notes in `step1_scope_and_research/`

### Step 2: Analysis & Evaluation

This step combines building, running, and validating the simulation in one
iterative flow. You don't need to separate "analysis" from "evaluation" — it's
a natural loop:

1. Build the simulation (notebook or Java test)
2. Run it and inspect results
3. Check physics (mass/energy balance, reasonable ranges)
4. Compare against reference data from Step 1
5. If results are off → adjust and rerun (iteration is implicit)
6. When satisfied → save final results and figures

All simulation code, results, and validation notes go to `step2_analysis/`.

### Step 3: Report (Word + HTML)

The deliverables are a **Word report** (`.docx`) and optionally an **HTML report**.

- Word report: formal document for sharing/review, generated via `generate_report.py`
- HTML report: interactive, navigable document — ideal for large workflows with
  many sections, embedded plots, and linked references
- Run: `python step3_report/generate_report.py`
- Both formats embed all figures from `figures/` directory

---

## VS Code Agent Quick Reference

| Agent | Best For | Example |
|-------|----------|---------|
| `@solve.task` | **Full 3-step workflow** (does everything) | "JT cooling for rich gas at 100 bara" |
| `@thermo.fluid` | Fluid setup, EOS, flash, properties | "Density of CO2-methane mix at 200 bar" |
| `@solve.process` | Complete simulation -> notebook | "TEG dehydration for 50 MMSCFD" |
| `@pvt.simulation` | PVT lab experiments | "CME test at 100C for this oil" |
| `@gas.quality` | Gas standards (ISO, GPA) | "Wobbe index per ISO 6976" |
| `@mechanical.design` | Wall thickness, sizing | "20-inch pipe per DNV-OS-F101" |
| `@flow.assurance` | Hydrates, wax, corrosion | "Hydrate curve for wet gas at 100 bara" |
| `@safety.depressuring` | Blowdown, PSV, fire | "Fire-case blowdown for HP separator" |

---

## Contributing Back

### Minimum (everyone should do this)
Add a task log entry to `docs/development/TASK_LOG.md`.

### Medium (if you wrote a useful notebook)
Copy your notebook to `examples/notebooks/`.

### Full (if you extended the API)
Write a test in `src/test/java/neqsim/` and run `mvnw.cmd test`.

### Create a Pull Request (if you want to contribute code or examples)

When your task produces reusable outputs — new methods, tests, notebooks, or
documentation — create a PR directly from the task:

```powershell
# 1. Create a branch from your current branch
git checkout -b task/your-task-name

# 2. Stage the files you want to contribute
git add src/test/java/neqsim/...             # tests
git add examples/notebooks/your_notebook.ipynb # notebook
git add docs/development/TASK_LOG.md          # task log entry

# 3. Commit and push
git commit -m "Add [description] from task solving workflow"
git push -u origin task/your-task-name

# 4. Create the PR (requires GitHub CLI: https://cli.github.com/)
gh pr create --title "Add [description]" --body "From task: [task title]"
```

> **Tip:** The `@solve.task` agent can do this for you — just ask
> "create a PR with the reusable outputs from this task".

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `python` not found | Use `python3` or install Python from python.org |
| Copilot Chat doesn't know NeqSim | Start prompt with "Read CONTEXT.md for orientation" |
| Simulation gives wrong numbers | Check EOS choice, mixing rule, units (Kelvin vs Celsius!) |
| Want to share task with colleague | Zip the task folder and send it - it's self-contained |

---

## Large Workflows (e.g., Field Development, Class A Studies)

For complex tasks that span multiple engineering disciplines (field development
concept selection, design basis studies, technology screening, Class A/B
estimates), the framework scales naturally:

1. **Step 1 (Scope)** becomes critical — define ALL standards, methods, and
   deliverables upfront in `task_spec.md`. For Class A studies, this may
   reference 10+ standards and produce a detailed work breakdown.
2. **Step 2 (Analysis)** can contain multiple notebooks, each covering a
   sub-analysis (e.g., `01_reservoir_fluid.ipynb`, `02_pipeline_sizing.ipynb`,
   `03_process_train.ipynb`, `04_flow_assurance.ipynb`, `05_cost_estimation.ipynb`)
3. **Step 3 (Report)** produces a comprehensive HTML document with navigation
   sidebar, linking all sub-analyses — plus a Word summary for formal distribution

The task type **G (Workflow)** is intended for these multi-discipline studies.

### Example: Field Development Concept Selection

```
@solve.task field development concept selection for 200 MMSCFD deepwater gas,
  per NORSOK P-001, Z-013, L-001, and DNV-OS-F101.
  Evaluate subsea tieback vs. FPSO vs. fixed platform.
  Deliver: reservoir fluid characterization, process train sizing,
  pipeline hydraulics, flow assurance assessment (hydrate + wax + corrosion),
  mechanical design summary, CAPEX/OPEX ranking, and recommendation report.
```

This generates 5-8 notebooks, a full task_spec.md referencing all standards,
and a navigable HTML report — a complete engineering study.
"""

TASK_README = r"""# Task: [Title]

**Date:** YYYY-MM-DD
**Type:** A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design) | G (Workflow)
**Status:** In Progress | Complete

## Problem Statement

[Describe the engineering question or task]

---

## Step 1: Scope & Research

### Part A: Task Specification

Fill in `step1_scope_and_research/task_spec.md` before starting analysis.

- [ ] Applicable standards defined
- [ ] Calculation methods/models specified
- [ ] Required deliverables listed
- [ ] Acceptance criteria set
- [ ] Operating envelope defined

### Part B: Research

- [ ] Literature search completed
- [ ] Reference data collected
- [ ] Key sources documented in `step1_scope_and_research/notes.md`

**Option A — Google NotebookLM** (upload PDFs, get cited answers):

```
I need to understand [TOPIC] for oil & gas process engineering.
Give me:
1. Key physical principles and governing equations
2. Typical operating ranges and design rules of thumb
3. Relevant industry standards (API, ASME, ISO, NORSOK, DNV)
4. Common correlations used in practice
5. Known limitations or edge cases
```

**Option B — VS Code Copilot Chat** (Ctrl+Shift+I, web search + repo context):

```
I'm researching [TOPIC] for a NeqSim task.
Read the task specification in step1_scope_and_research/task_spec.md first.
Search the web and this repository for:
1. Key physical principles and governing equations
2. Requirements from the specified standards
3. What NeqSim classes/methods already exist for this
Write the findings to step1_scope_and_research/notes.md in my task folder.
```

---

## Step 2: Analysis & Evaluation

- [ ] NeqSim simulation written (notebook or test)
- [ ] Results extracted and saved
- [ ] Results validated against references and acceptance criteria
- [ ] Physics checks passed (mass/energy balance, ranges)
- [ ] API gaps identified (if any)

The analysis and evaluation happen in one iterative loop — build, run, validate,
refine until results meet the acceptance criteria from Step 1.

**AI prompt - paste into VS Code Copilot Chat:**

```
I'm working on a task in task_solve/[THIS_FOLDER]/.
Read the task specification in step1_scope_and_research/task_spec.md.
Read the research notes in step1_scope_and_research/notes.md.

Task: [DESCRIBE YOUR TASK]

Create a Jupyter notebook in step2_analysis/ that:
1. Sets up the fluid system with appropriate EOS (per task spec)
2. Builds the process flowsheet
3. Runs the simulation
4. Validates results against acceptance criteria
5. Extracts key results and saves figures to figures/
```

**Which VS Code agent to use:**

| Task Type | Agent | Example prompt |
|-----------|-------|----------------|
| Fluid properties | `@thermo.fluid` | "Create a CPA fluid for gas with 5% MEG" |
| Process simulation | `@solve.process` | "3-stage compression from 5 to 150 bara" |
| PVT study | `@pvt.simulation` | "CME test for reservoir fluid at 100C" |
| Gas quality | `@gas.quality` | "Wobbe index per ISO 6976 for this gas" |
| Mechanical design | `@mechanical.design` | "Wall thickness for 20-inch pipe per DNV" |
| Flow assurance | `@flow.assurance` | "Hydrate formation curve for wet gas" |
| Safety | `@safety.depressuring` | "Fire-case blowdown for HP separator" |

---

## Step 3: Report (Word + HTML)

The deliverables are a **Word report** (`.docx`) and optionally an **HTML report**.

- [ ] Figures saved to `figures/`
- [ ] `generate_report.py` customized and runs end-to-end
- [ ] Report(s) generated in `step3_report/`
- [ ] All required deliverables from task spec produced
- [ ] Task logged in `docs/development/TASK_LOG.md`

**To generate reports:**

```powershell
pip install python-docx matplotlib    # one-time setup
python step3_report/generate_report.py
```

**AI prompt - paste into VS Code Copilot Chat:**

```
Customize step3_report/generate_report.py for this task.
Read the task spec in step1_scope_and_research/task_spec.md for required deliverables.
Fill in the report sections with actual results from step2_analysis/.
Embed all figures from figures/.
Generate both Word (.docx) and HTML output.
```

---

## Key Results

[Summary of findings - fill in when complete]

---

## Contribute Back - Reusable Outputs

When your task is done, promote valuable work back into the repo so others
benefit. Check what applies:

- [ ] **Test** - Copy simulation to `src/test/java/neqsim/` (proves it keeps working)
- [ ] **Notebook** - Copy notebook to `examples/notebooks/` (others can rerun it)
- [ ] **API extension** - New methods added to `src/main/java/neqsim/`
- [ ] **Documentation** - Guide or recipe added to `docs/`
- [ ] **Task log** - Entry added to `docs/development/TASK_LOG.md`

Don't worry if you can't do all of these. Even just the task log entry helps
the next person (or AI session) find your solution.

### Create a Pull Request

If this task produced reusable code, tests, or notebooks, create a PR to
contribute them back:

```powershell
# Create a feature branch
git checkout -b task/[SHORT_NAME]

# Stage reusable outputs (pick what applies)
git add src/test/java/neqsim/...              # new tests
git add examples/notebooks/...                 # example notebooks
git add docs/...                               # documentation
git add docs/development/TASK_LOG.md           # task log entry

# Commit and push
git commit -m "Add [description] from task: [TITLE]"
git push -u origin task/[SHORT_NAME]

# Create PR (requires GitHub CLI)
gh pr create --title "Add [description]" \\
  --body "From task-solving workflow: [TITLE]"
```

> **Tip:** Ask the `@solve.task` agent to do this:
> "create a PR with the test and notebook from this task"
"""

STEP1_NOTES = """# Step 1: Research Notes

## Sources

| # | Source | Type | Key Finding |
|---|--------|------|-------------|
| 1 | | | |

## Background

[Summary of the engineering context]

## Key Data / Correlations

[Reference values, correlations, experimental data]

## Open Questions

- [ ]
"""

TASK_SPEC = """# Task Specification

This file defines the scope and requirements that guide the analysis in Step 2.
Fill this in before starting any simulation work.

## Applicable Standards

List the codes, standards, and company requirements that govern this task.

| Standard | Scope | Key Requirements |
|----------|-------|-----------------|
| | | |

Examples: NORSOK P-001, ISO 6976, DNV-OS-F101, API 520, ASME B31.3, Equinor TR1414

## Calculation Methods & Models

Specify which methods, equations of state, and correlations to use.

- **Equation of State:** [e.g., SRK, PR, SRK-CPA for polar systems]
- **Pipe flow model:** [e.g., Beggs & Brill, OLGA-style, single-phase]
- **Heat transfer:** [e.g., adiabatic, U-value based, ambient loss]
- **Other correlations:** [e.g., API 520 for PSV sizing, NORSOK M-001 for materials]

## Required Deliverables

What must the final output include? Check all that apply and add specifics.

- [ ] Phase envelope / phase diagram
- [ ] Pressure-temperature profiles
- [ ] Equipment sizing calculations
- [ ] Sensitivity analysis (specify parameters)
- [ ] Comparison with reference/experimental data
- [ ] VFP tables
- [ ] Material selection
- [ ] Cost estimation
- [ ] Other: [specify]

## Acceptance Criteria

Define what \"good enough\" means for this task.

- **Mass balance tolerance:** [e.g., < 0.1%]
- **Energy balance tolerance:** [e.g., < 1%]
- **Design factor:** [e.g., 0.72 per DNV]
- **Safety margin:** [e.g., 10% on design pressure]
- **Convergence:** [e.g., solver residual < 1e-6]
- **Other:** [specify]

## Operating Envelope

Define the range of conditions to be covered.

| Parameter | Min | Design | Max | Unit |
|-----------|-----|--------|-----|------|
| Pressure | | | | bara |
| Temperature | | | | C |
| Flow rate | | | | kg/hr |
| Composition | | | | mol% |

## Input Data

Reference any input data files, lab reports, or composition tables.

- [ ] Fluid composition: [source]
- [ ] Operating conditions: [source]
- [ ] Equipment data: [source]
- [ ] Other: [specify]
"""

STEP2_NOTES = """# Step 2: Analysis & Validation Notes

## Analysis Log

### Run 1 - YYYY-MM-DD

**Setup:**

**Results:**

**Validation against acceptance criteria:**

**Status:** Pass / Needs refinement

---

## Validation Checklist

- [ ] Mass balance closes (in = out +/- tolerance from task spec)
- [ ] Energy balance closes
- [ ] Temperatures in reasonable range
- [ ] Pressures positive
- [ ] Densities in expected range
- [ ] Results consistent with literature/correlations
- [ ] Results meet acceptance criteria from task spec
- [ ] Sensitivity to key parameters checked
- [ ] All required deliverables from task spec produced
"""

GENERATE_REPORT = '''"""
generate_report.py - Generate Word and HTML reports for this task.

Usage:
    pip install python-docx matplotlib   (one-time setup)
    python step3_report/generate_report.py

This script runs headless (no Jupyter kernel needed). It:
1. Collects results from step2_analysis/
2. Embeds figures from figures/
3. Produces a .docx Word report in step3_report/
4. Produces an .html report in step3_report/ (for large workflows)

Customize the sections below for your specific task.
"""
import os
import sys
import glob
import base64
from datetime import date

try:
    from docx import Document
    from docx.shared import Inches, Pt
    from docx.enum.text import WD_ALIGN_PARAGRAPH
except ImportError:
    print("ERROR: python-docx not installed. Run: pip install python-docx")
    sys.exit(1)

# Paths
TASK_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIG_DIR = os.path.join(TASK_DIR, "figures")
REPORT_DIR = os.path.dirname(os.path.abspath(__file__))
DOCX_FILE = os.path.join(REPORT_DIR, "Report.docx")
HTML_FILE = os.path.join(REPORT_DIR, "Report.html")

# Configuration (edit these)
TITLE = "Task Report"           # <-- Change to your task title
AUTHOR = ""                     # <-- Your name
TASK_DATE = date.today().isoformat()

# Report sections — edit content for your specific task
SECTIONS = [
    {
        "heading": "1. Executive Summary",
        "content": "[Replace this with a 3-5 sentence summary of the task, approach, "
                   "and key findings.]",
    },
    {
        "heading": "2. Problem Description",
        "content": "[Describe the engineering question or task that was solved.]",
    },
    {
        "heading": "3. Scope and Standards",
        "content": "[List applicable standards, calculation methods, and acceptance "
                   "criteria from the task specification.]",
    },
    {
        "heading": "4. Approach",
        "content": "[Describe the methodology: EOS used, process configuration, "
                   "simulation setup, key assumptions.]",
    },
    {
        "heading": "5. Results",
        "content": "[Present key numerical results. Add tables and figures below.]",
    },
    {
        "heading": "6. Conclusions and Recommendations",
        "content": "[Summarize key findings and provide recommendations.]",
    },
    {
        "heading": "7. References",
        "content": "[List references from step1_scope_and_research/notes.md.]",
    },
]


def get_figures():
    """Collect all PNG figures from the figures/ directory."""
    return sorted(glob.glob(os.path.join(FIG_DIR, "*.png")))


def build_word_report():
    """Build the Word document."""
    doc = Document()

    # Title page
    doc.add_heading(TITLE, level=0)
    doc.add_paragraph("")
    doc.add_paragraph("Author: {}".format(AUTHOR or "(not specified)"))
    doc.add_paragraph("Date: {}".format(TASK_DATE))
    doc.add_page_break()

    # Add all sections
    for section in SECTIONS:
        doc.add_heading(section["heading"], level=1)
        doc.add_paragraph(section["content"])

        # Embed figures after Results section
        if "Results" in section["heading"]:
            figures = get_figures()
            if figures:
                for fig_path in figures:
                    fig_name = os.path.basename(fig_path)
                    doc.add_picture(fig_path, width=Inches(6.0))
                    last_para = doc.paragraphs[-1]
                    last_para.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    caption = doc.add_paragraph(
                        "Figure: {}".format(
                            fig_name.replace("_", " ").replace(".png", "")
                        )
                    )
                    caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    caption.runs[0].font.size = Pt(9)
                    caption.runs[0].font.italic = True
                    doc.add_paragraph("")
            else:
                doc.add_paragraph(
                    "[No figures found in figures/ directory. "
                    "Save plots as PNG files there and re-run this script.]"
                )

    # Save
    doc.save(DOCX_FILE)
    print("Word report saved: {}".format(DOCX_FILE))


def build_html_report():
    """Build an HTML report with embedded figures and navigation sidebar."""
    figures = get_figures()

    # Build figure HTML with base64-embedded images
    figure_html = ""
    if figures:
        for fig_path in figures:
            fig_name = os.path.basename(fig_path)
            caption = fig_name.replace("_", " ").replace(".png", "")
            with open(fig_path, "rb") as f:
                img_data = base64.b64encode(f.read()).decode("utf-8")
            figure_html += """
            <div class="figure">
                <img src="data:image/png;base64,{}" alt="{}">
                <p class="caption">Figure: {}</p>
            </div>
            """.format(img_data, caption, caption)
    else:
        figure_html = "<p><em>No figures found in figures/ directory.</em></p>"

    # Build section HTML and navigation
    nav_items = ""
    section_html = ""
    for section in SECTIONS:
        section_id = section["heading"].lower().replace(" ", "-").replace(".", "")
        nav_items += \'    <li><a href="#{}">{}</a></li>\\n\'.format(
            section_id, section["heading"]
        )
        content = section["content"]
        # Insert figures after Results section
        if "Results" in section["heading"]:
            content += figure_html
        section_html += """
        <section id="{}">
            <h2>{}</h2>
            <p>{}</p>
        </section>
        """.format(section_id, section["heading"], content)

    html = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{title}</title>
    <style>
        * {{ margin: 0; padding: 0; box-sizing: border-box; }}
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
               display: flex; line-height: 1.6; color: #333; }}
        nav {{ width: 260px; min-height: 100vh; background: #f5f5f5; padding: 1.5rem;
              position: fixed; overflow-y: auto; border-right: 1px solid #ddd; }}
        nav h3 {{ margin-bottom: 1rem; color: #555; font-size: 0.9rem;
                  text-transform: uppercase; letter-spacing: 0.05em; }}
        nav ul {{ list-style: none; }}
        nav li {{ margin-bottom: 0.5rem; }}
        nav a {{ color: #0366d6; text-decoration: none; font-size: 0.9rem; }}
        nav a:hover {{ text-decoration: underline; }}
        main {{ margin-left: 260px; max-width: 900px; padding: 2rem 3rem; }}
        h1 {{ margin-bottom: 0.5rem; color: #1a1a1a; }}
        h2 {{ margin-top: 2rem; margin-bottom: 1rem; color: #1a1a1a;
             border-bottom: 1px solid #eee; padding-bottom: 0.3rem; }}
        .meta {{ color: #666; margin-bottom: 2rem; }}
        section {{ margin-bottom: 2rem; }}
        .figure {{ text-align: center; margin: 1.5rem 0; }}
        .figure img {{ max-width: 100%; border: 1px solid #ddd; border-radius: 4px; }}
        .caption {{ font-size: 0.85rem; color: #666; font-style: italic;
                    margin-top: 0.3rem; }}
        table {{ border-collapse: collapse; width: 100%; margin: 1rem 0; }}
        th, td {{ border: 1px solid #ddd; padding: 0.5rem 0.75rem; text-align: left; }}
        th {{ background: #f5f5f5; }}
        @media (max-width: 768px) {{
            nav {{ position: static; width: 100%; min-height: auto; }}
            main {{ margin-left: 0; padding: 1rem; }}
        }}
    </style>
</head>
<body>
    <nav>
        <h3>Contents</h3>
        <ul>
{nav}
        </ul>
        <hr style="margin: 1rem 0;">
        <p style="font-size: 0.8rem; color: #999;">Generated {date}</p>
    </nav>
    <main>
        <h1>{title}</h1>
        <p class="meta">Author: {author} | Date: {date}</p>
{sections}
    </main>
</body>
</html>""".format(
        title=TITLE,
        author=AUTHOR or "(not specified)",
        date=TASK_DATE,
        nav=nav_items,
        sections=section_html,
    )

    with open(HTML_FILE, "w", encoding="utf-8") as f:
        f.write(html)
    print("HTML report saved: {}".format(HTML_FILE))


if __name__ == "__main__":
    build_word_report()
    build_html_report()
    print("")
    print("Both reports generated. Open Report.html in a browser for")
    print("navigable view, or Report.docx for formal distribution.")
'''


# ══════════════════════════════════════════════════════════
# Functions
# ══════════════════════════════════════════════════════════

def slugify(title):
    """Convert a title to a folder-safe slug."""
    slug = title.lower().strip()
    for ch in ",:;!?()[]{}'\"/\\.":
        slug = slug.replace(ch, "")
    slug = slug.replace(" ", "_").replace("-", "_")
    while "__" in slug:
        slug = slug.replace("__", "_")
    return slug.strip("_")


def _write_file(path, content):
    """Write content to a file, creating parent dirs as needed."""
    parent = os.path.dirname(path)
    if parent and not os.path.exists(parent):
        os.makedirs(parent)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def setup_workspace():
    """
    Create the task_solve/ folder with README and TASK_TEMPLATE.

    Safe to call multiple times — skips files that already exist.
    Returns True if anything was created.
    """
    created = False

    # Main README
    readme = os.path.join(TASK_SOLVE_DIR, "README.md")
    if not os.path.exists(readme):
        _write_file(readme, WORKSPACE_README)
        created = True

    # Template structure
    template_files = {
        os.path.join(TEMPLATE_DIR, "README.md"): TASK_README,
        os.path.join(TEMPLATE_DIR, "step1_scope_and_research", "task_spec.md"): TASK_SPEC,
        os.path.join(TEMPLATE_DIR, "step1_scope_and_research", "notes.md"): STEP1_NOTES,
        os.path.join(TEMPLATE_DIR, "step2_analysis", "notes.md"): STEP2_NOTES,
        os.path.join(TEMPLATE_DIR, "step2_analysis", ".gitkeep"): "",
        os.path.join(TEMPLATE_DIR, "step3_report", "generate_report.py"): GENERATE_REPORT,
        os.path.join(TEMPLATE_DIR, "figures", ".gitkeep"): "",
    }

    for path, content in template_files.items():
        if not os.path.exists(path):
            _write_file(path, content)
            created = True

    return created


def create_task(title, task_type="B", author=""):
    """Create a new task folder from the template."""
    # Ensure workspace exists
    if not os.path.exists(TEMPLATE_DIR):
        print("Setting up task_solve/ workspace for the first time...")
        setup_workspace()
        print("")

    today = date.today().isoformat()
    folder_name = "{}_{}".format(today, slugify(title))
    task_dir = os.path.join(TASK_SOLVE_DIR, folder_name)

    if os.path.exists(task_dir):
        print("ERROR: Folder already exists: {}".format(task_dir))
        sys.exit(1)

    # Copy template
    shutil.copytree(TEMPLATE_DIR, task_dir)

    # Fill in the README
    readme_path = os.path.join(task_dir, "README.md")
    with open(readme_path, "r", encoding="utf-8") as f:
        content = f.read()

    type_label = "{} ({})".format(task_type, TASK_TYPES.get(task_type, ""))
    content = content.replace("[Title]", title)
    content = content.replace("YYYY-MM-DD", today)
    content = content.replace(
        "A (Property) | B (Process) | C (PVT) | D (Standards) | E (Feature) | F (Design) | G (Workflow)",
        type_label,
    )
    content = content.replace("[THIS_FOLDER]", folder_name)
    if author:
        content = content.replace(
            "**Status:**",
            "**Author:** {}\n**Status:**".format(author),
        )

    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(content)

    # Fill in the step1 notes
    notes_path = os.path.join(task_dir, "step1_scope_and_research", "notes.md")
    with open(notes_path, "r", encoding="utf-8") as f:
        notes = f.read()
    notes = notes.replace(
        "[Summary of the engineering context]",
        "Task: {}".format(title),
    )
    with open(notes_path, "w", encoding="utf-8") as f:
        f.write(notes)

    # Fill in generate_report.py
    report_path = os.path.join(task_dir, "step3_report", "generate_report.py")
    with open(report_path, "r", encoding="utf-8") as f:
        report = f.read()
    report = report.replace('TITLE = "Task Report"', 'TITLE = "{}"'.format(title))
    if author:
        report = report.replace('AUTHOR = ""', 'AUTHOR = "{}"'.format(author))
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report)

    print("Created: task_solve/{}".format(folder_name))
    print("")
    print("Next steps (pick one):")
    print("")
    print("  Recommended - Let Copilot do everything:")
    print("    Open VS Code Copilot Chat and type:")
    print("")
    print("    @solve.task {}".format(title))
    print("")
    print("  Alternative - Follow prompts manually:")
    print("    Open task_solve/{}/README.md".format(folder_name))
    print("")
    return task_dir


def list_tasks():
    """List existing task folders."""
    if not os.path.exists(TASK_SOLVE_DIR):
        print("No task_solve/ folder yet. Run: python devtools/new_task.py --setup")
        return

    entries = sorted(os.listdir(TASK_SOLVE_DIR))
    tasks = [
        e for e in entries
        if os.path.isdir(os.path.join(TASK_SOLVE_DIR, e))
        and e != "TASK_TEMPLATE"
    ]

    if not tasks:
        print("No tasks yet. Create one with: python devtools/new_task.py \"your task\"")
    else:
        print("Tasks in task_solve/:")
        for t in tasks:
            readme = os.path.join(TASK_SOLVE_DIR, t, "README.md")
            status = ""
            if os.path.exists(readme):
                with open(readme, "r", encoding="utf-8") as f:
                    for line in f:
                        if "**Status:**" in line:
                            status = line.split("**Status:**")[-1].strip()
                            break
            print("  {} {}".format(t, "[{}]".format(status) if status else ""))


def main():
    if len(sys.argv) < 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)

    if sys.argv[1] == "--setup":
        if setup_workspace():
            print("Created task_solve/ workspace with README and template.")
        else:
            print("task_solve/ workspace already exists.")
        print("\nCreate a task: python devtools/new_task.py \"your task title\"")
        return

    if sys.argv[1] == "--list":
        list_tasks()
        return

    title = sys.argv[1]
    task_type = "B"
    author = ""

    i = 2
    while i < len(sys.argv):
        if sys.argv[i] == "--type" and i + 1 < len(sys.argv):
            task_type = sys.argv[i + 1].upper()
            i += 2
        elif sys.argv[i] == "--author" and i + 1 < len(sys.argv):
            author = sys.argv[i + 1]
            i += 2
        else:
            i += 1

    if task_type not in TASK_TYPES:
        print("WARNING: Unknown task type '{}'. Valid: {}".format(
            task_type, ", ".join(sorted(TASK_TYPES.keys()))))
        print("Using type B (Process) as default.")
        task_type = "B"

    create_task(title, task_type, author)


if __name__ == "__main__":
    main()
